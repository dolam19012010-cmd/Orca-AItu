package com.orca.orcaauto.service

import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import java.text.Normalizer
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.CRC32

typealias JumpResult = ChatIntentManager.JumpResult

data class CachedChatTarget(
    val refId: String,
    val packageName: String,
    val normalizedRoomKey: String,
    val pendingIntent: PendingIntent?,
    val cachedAtMs: Long
)

data class CachedChatIntent(
    val packageName: String,
    val senderTitle: String,
    val pendingIntent: PendingIntent?,
    val timestamp: Long = System.currentTimeMillis(),
    val extractedPhoneOrUsername: String? = null
)

/**
 * Thread-safe RAM LRU Cache with dual indexing:
 * Primary: refId -> CachedChatTarget
 * Secondary: "$packageName|$normalizedRoomKey" -> refId
 * Capacity: 50, TTL: 12 hours
 */
class ChatTargetLruCache(
    val capacity: Int = 50,
    val ttlMs: Long = 12 * 3600 * 1000L
) {
    private val lock = Any()

    private val primaryMap = object : LinkedHashMap<String, CachedChatTarget>(capacity, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedChatTarget>?): Boolean {
            return size > capacity
        }
    }

    private val secondaryIndex = HashMap<String, String>()

    fun put(target: CachedChatTarget) = synchronized(lock) {
        purgeExpiredLocked(target.cachedAtMs)
        primaryMap[target.refId] = target
        secondaryIndex["${target.packageName}|${target.normalizedRoomKey}"] = target.refId
        trimSecondaryIndexLocked()
    }

    fun get(refId: String, nowMs: Long = System.currentTimeMillis()): CachedChatTarget? = synchronized(lock) {
        val target = primaryMap[refId] ?: return null
        if (nowMs - target.cachedAtMs > ttlMs) {
            primaryMap.remove(refId)
            secondaryIndex.remove("${target.packageName}|${target.normalizedRoomKey}")
            return null
        }
        target
    }

    fun getBySecondary(packageName: String, normalizedRoomKey: String, nowMs: Long = System.currentTimeMillis()): CachedChatTarget? = synchronized(lock) {
        val refId = secondaryIndex["$packageName|$normalizedRoomKey"] ?: return null
        return get(refId, nowMs)
    }

    fun findBestMatch(packageName: String, normalizedKey: String, nowMs: Long = System.currentTimeMillis()): CachedChatTarget? = synchronized(lock) {
        val exact = getBySecondary(packageName, normalizedKey, nowMs)
        if (exact != null) return exact

        purgeExpiredLocked(nowMs)
        val matched = primaryMap.values.filter { it.packageName == packageName }
            .filter {
                normalizedKey.isNotBlank() && (it.normalizedRoomKey.contains(normalizedKey) || normalizedKey.contains(it.normalizedRoomKey))
            }
            .maxByOrNull { it.cachedAtMs }
        if (matched != null) return matched

        return primaryMap.values.filter { it.packageName == packageName }.maxByOrNull { it.cachedAtMs }
    }

    fun size(): Int = synchronized(lock) {
        purgeExpiredLocked(System.currentTimeMillis())
        primaryMap.size
    }

    fun clear() = synchronized(lock) {
        primaryMap.clear()
        secondaryIndex.clear()
    }

    private fun purgeExpiredLocked(nowMs: Long) {
        val iterator = primaryMap.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (nowMs - entry.value.cachedAtMs > ttlMs) {
                secondaryIndex.remove("${entry.value.packageName}|${entry.value.normalizedRoomKey}")
                iterator.remove()
            }
        }
    }

    private fun trimSecondaryIndexLocked() {
        val validKeys = primaryMap.values.map { "${it.packageName}|${it.normalizedRoomKey}" }.toSet()
        secondaryIndex.entries.removeIf { !validKeys.contains(it.key) }
    }
}

/**
 * Quản lý bộ nhớ đệm Intent và điều hướng 3 tầng:
 * Tầng 1: PendingIntent (sbn.notification.contentIntent) kèm BAL Opt-in cho Android 14/15
 * Tầng 2: Deep Link (tg://, t.me, m.me, zalo.me)
 * Tầng 3: Fallback Native App Launch (getLaunchIntentForPackage) + Toast cảnh báo mất dấu
 */
object ChatIntentManager {
    private const val TAG = "ChatIntentManager"

    enum class JumpResult {
        OPENED_CONVERSATION,
        OPENED_APP,
        FAILED
    }

    const val CACHE_EXPIRY_MS = 12 * 3600 * 1000L // 12 giờ
    const val CACHE_CAPACITY = 50

    val targetCache = ChatTargetLruCache(capacity = CACHE_CAPACITY, ttlMs = CACHE_EXPIRY_MS)

    // Legacy fallback cache
    private val intentCache = ConcurrentHashMap<String, CachedChatIntent>()

    fun normalizeRoomKey(rawTitle: String): String {
        var title = rawTitle.trim()
        if (title.contains(":")) {
            title = title.substringBefore(":").trim()
        } else if (title.contains(">")) {
            title = title.substringBefore(">").trim()
        }
        title = title.replace(Regex("""\(\d+(\s*(tin nhắn|messages|msgs))?\)""", RegexOption.IGNORE_CASE), "").trim()
        val nfc = Normalizer.normalize(title, Normalizer.Form.NFC)
        return nfc.replace(Regex("""[^\p{L}\p{N}\s]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
            .lowercase()
    }

    fun generateChatRefId(packageName: String, normalizedRoomKey: String): String {
        val lowerPkg = packageName.lowercase()
        val prefix = when {
            lowerPkg.contains("zalo") -> 'z'
            lowerPkg.contains("telegram") || lowerPkg.contains("tele") -> 't'
            lowerPkg.contains("orca") || lowerPkg.contains("messenger") -> 'm'
            else -> 'o'
        }
        val crc = CRC32()
        crc.update("$packageName|$normalizedRoomKey".toByteArray(Charsets.UTF_8))
        val base36Hash = (crc.value % (36 * 36 * 36 * 36)).toString(36).padStart(4, '0')
        return "$prefix$base36Hash"
    }

    fun cacheChatTarget(
        packageName: String,
        rawTitle: String,
        pendingIntent: PendingIntent?
    ) {
        if (pendingIntent == null) return
        val normalizedKey = normalizeRoomKey(rawTitle)
        val refId = generateChatRefId(packageName, normalizedKey)
        val target = CachedChatTarget(
            refId = refId,
            packageName = packageName,
            normalizedRoomKey = normalizedKey,
            pendingIntent = pendingIntent,
            cachedAtMs = System.currentTimeMillis()
        )
        targetCache.put(target)
        cacheChatIntent(packageName, rawTitle, pendingIntent)
    }

    fun cacheChatIntent(
        packageName: String,
        senderTitle: String,
        pendingIntent: PendingIntent?,
        extractedPhoneOrUsername: String? = null
    ) {
        if (pendingIntent == null) return
        val cleanSender = normalizeText(senderTitle)
        val key = buildKey(packageName, cleanSender)
        intentCache[key] = CachedChatIntent(
            packageName = packageName,
            senderTitle = senderTitle,
            pendingIntent = pendingIntent,
            timestamp = System.currentTimeMillis(),
            extractedPhoneOrUsername = extractedPhoneOrUsername
        )
        cleanExpiredCache()
    }

    fun getCachedIntent(packageName: String, senderTitle: String): CachedChatIntent? {
        val cleanSender = normalizeText(senderTitle)
        val key = buildKey(packageName, cleanSender)
        val cached = intentCache[key]
        if (cached != null && (System.currentTimeMillis() - cached.timestamp <= CACHE_EXPIRY_MS)) {
            return cached
        }
        return null
    }

    fun findBestMatchingIntent(sourceText: String, taskTitle: String): CachedChatIntent? {
        cleanExpiredCache()
        val cleanSource = normalizeText(sourceText)
        val cleanTitle = normalizeText(taskTitle)

        for ((_, cached) in intentCache) {
            val cachedSender = normalizeText(cached.senderTitle)
            if (cleanSource.isNotBlank() && (cleanSource.contains(cachedSender) || cachedSender.contains(cleanSource))) {
                return cached
            }
            if (cleanTitle.contains(cachedSender)) {
                return cached
            }
        }

        val targetPackage = when {
            cleanSource.contains("zalo") -> "com.zing.zalo"
            cleanSource.contains("telegram") -> "org.telegram.messenger"
            cleanSource.contains("messenger") || cleanSource.contains("facebook") -> "com.facebook.orca"
            else -> null
        }

        if (targetPackage != null) {
            val matchByPackage = intentCache.values
                .filter { it.packageName == targetPackage }
                .maxByOrNull { it.timestamp }
            if (matchByPackage != null) return matchByPackage
        }

        return intentCache.values.maxByOrNull { it.timestamp }
    }

    fun resolveTargetPackage(sourceText: String, taskTitle: String, chatRefId: String? = null): String {
        if (!chatRefId.isNullOrBlank()) {
            when (chatRefId.first().lowercaseChar()) {
                'z' -> return "com.zing.zalo"
                't' -> return "org.telegram.messenger"
                'm' -> return "com.facebook.orca"
            }
        }

        val cleanSource = normalizeText(sourceText)
        val cleanTitle = normalizeText(taskTitle)

        when {
            cleanSource.contains("zalo") -> return "com.zing.zalo"
            cleanSource.contains("telegram") || cleanSource.contains("tele") -> return "org.telegram.messenger"
            cleanSource.contains("messenger") || cleanSource.contains("facebook") || cleanSource.contains("mess") -> return "com.facebook.orca"
        }

        for ((_, cached) in intentCache) {
            val cachedSender = normalizeText(cached.senderTitle)
            if (cachedSender.isNotBlank()) {
                if (cleanSource.contains(cachedSender) || cachedSender.contains(cleanSource)) {
                    return cached.packageName
                }
                if (cleanTitle.contains(cachedSender)) {
                    return cached.packageName
                }
            }
        }

        when {
            cleanTitle.contains("zalo") -> return "com.zing.zalo"
            cleanTitle.contains("telegram") || cleanTitle.contains("tele") -> return "org.telegram.messenger"
            cleanTitle.contains("messenger") || cleanTitle.contains("mess") -> return "com.facebook.orca"
        }

        val latest = intentCache.values.maxByOrNull { it.timestamp }
        if (latest != null) {
            return latest.packageName
        }

        return "com.zing.zalo"
    }

    fun getAppDisplayName(packageName: String): String {
        return when (packageName) {
            "com.zing.zalo" -> "Zalo"
            "org.telegram.messenger" -> "Telegram"
            "com.facebook.orca" -> "Messenger"
            else -> "Zalo"
        }
    }

    fun openAppForTask(context: Context, sourceText: String, taskTitle: String): Boolean {
        val targetPackage = resolveTargetPackage(sourceText, taskTitle)
        val appName = getAppDisplayName(targetPackage)
        return try {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(targetPackage)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            }
            if (launchIntent != null) {
                context.startActivity(launchIntent)
                showToast(context, "🚀 Đang mở $appName...")
                true
            } else {
                showToast(context, "Không tìm thấy $appName trên thiết bị", Toast.LENGTH_SHORT)
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi mở app $targetPackage: ${e.message}")
            false
        }
    }

    /**
     * Nhảy vào cuộc trò chuyện 3 tầng:
     * - Tầng 1: PendingIntent từ cache (theo chatRefId hoặc soft match normalizedRoomKey)
     * - Tầng 2: Deep Link (tg://, https://m.me/, zalo.me)
     * - Tầng 3: Mở App qua getLaunchIntentForPackage. Nếu PendingIntent đã chết/mất, Toast thông báo "Đã mất dấu phòng chat, đang mở $appName".
     */
    fun jumpToChat(
        context: Context,
        chatRefId: String?,
        sourceText: String,
        taskTitle: String
    ): JumpResult {
        val targetPackage = resolveTargetPackage(sourceText, taskTitle, chatRefId)
        val appName = getAppDisplayName(targetPackage)

        var target: CachedChatTarget? = null
        if (!chatRefId.isNullOrBlank()) {
            target = targetCache.get(chatRefId)
        }
        if (target == null) {
            val normalizedKey = normalizeRoomKey(sourceText.ifBlank { taskTitle })
            target = targetCache.getBySecondary(targetPackage, normalizedKey)
                ?: targetCache.findBestMatch(targetPackage, normalizedKey)
        }

        var hadPendingIntent = false
        if (target?.pendingIntent != null) {
            hadPendingIntent = true
            val launched = launchPendingIntent(context, target.pendingIntent)
            if (launched) {
                return JumpResult.OPENED_CONVERSATION
            }
            Log.w(TAG, "PendingIntent cho chatRefId=$chatRefId không thể khởi chạy (đã bị hủy hoặc chết). Chuyển sang Tier 2/3.")
        }

        val deepLinkOpened = launchDeepLinkForTask(context, targetPackage, sourceText, taskTitle)
        if (deepLinkOpened) {
            return JumpResult.OPENED_CONVERSATION
        }

        val launchIntent = try {
            context.packageManager.getLaunchIntentForPackage(targetPackage)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            }
        } catch (e: Exception) {
            null
        }

        if (launchIntent != null) {
            return try {
                context.startActivity(launchIntent)
                if (hadPendingIntent || !chatRefId.isNullOrBlank()) {
                    showToast(context, "Đã mất dấu phòng chat, đang mở $appName")
                } else {
                    showToast(context, "🚀 Đang mở $appName...")
                }
                JumpResult.OPENED_APP
            } catch (e: Exception) {
                Log.e(TAG, "Lỗi khi startActivity $targetPackage: ${e.message}")
                JumpResult.FAILED
            }
        } else {
            showToast(context, "Không tìm thấy $appName trên thiết bị")
            return JumpResult.FAILED
        }
    }

    /**
     * Backward-compatible overload.
     */
    fun jumpToChat(
        context: Context,
        sourceText: String,
        taskTitle: String,
        autoCopySmartReply: Boolean = false
    ): Boolean {
        val res = jumpToChat(context, null, sourceText, taskTitle)
        return res != JumpResult.FAILED
    }

    private fun launchDeepLinkForTask(
        context: Context,
        targetPackage: String,
        sourceText: String,
        taskTitle: String
    ): Boolean {
        // Try extracting potential identifier or phone from source/title
        val combined = "$sourceText $taskTitle"
        val phoneMatch = Regex("""(\+84|0)\d{9,10}""").find(combined)?.value
        val usernameMatch = Regex("""@([a-zA-Z0-9_]{4,})""").find(combined)?.groupValues?.get(1)
        val identifier = phoneMatch ?: usernameMatch
        if (identifier != null) {
            return launchDeepLink(context, targetPackage, identifier)
        }
        return false
    }

    private fun launchPendingIntent(context: Context, pendingIntent: PendingIntent): Boolean {
        return try {
            val fillInIntent = Intent().apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            val bundle = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                // Android 14+ (API 34) & Android 15 (API 35): Cho phép Background Activity Launch
                ActivityOptions.makeBasic().apply {
                    pendingIntentBackgroundActivityStartMode =
                        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                }.toBundle()
            } else {
                null
            }

            pendingIntent.send(context, 0, fillInIntent, null, null, null, bundle)
            true
        } catch (e: PendingIntent.CanceledException) {
            Log.e(TAG, "PendingIntent đã bị hủy: ${e.message}")
            false
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi khi kích hoạt PendingIntent: ${e.message}")
            false
        }
    }

    private fun launchDeepLink(context: Context, packageName: String, targetIdentifier: String?): Boolean {
        if (targetIdentifier.isNullOrBlank()) return false
        try {
            val uri = when (packageName) {
                "org.telegram.messenger" -> {
                    if (targetIdentifier.startsWith("+") || targetIdentifier.all { it.isDigit() }) {
                        Uri.parse("tg://resolve?phone=$targetIdentifier")
                    } else {
                        Uri.parse("tg://resolve?domain=$targetIdentifier")
                    }
                }
                "com.facebook.orca" -> {
                    if (targetIdentifier.all { it.isDigit() }) {
                        Uri.parse("fb-messenger://user/$targetIdentifier")
                    } else {
                        Uri.parse("https://m.me/$targetIdentifier")
                    }
                }
                "com.zing.zalo" -> {
                    val cleanPhone = targetIdentifier.replace(" ", "").replace("+84", "0")
                    if (cleanPhone.matches(Regex("^[0-9]{10,11}$"))) {
                        Uri.parse("https://zalo.me/$cleanPhone")
                    } else null
                }
                else -> null
            } ?: return false

            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage(packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi khi mở Deep Link [$targetIdentifier]: ${e.message}")
            return false
        }
    }

    fun generateSmartReply(taskTitle: String): String {
        val lower = taskTitle.lowercase()
        return when {
            lower.contains("gửi") || lower.contains("báo cáo") || lower.contains("file") || lower.contains("tài liệu") ->
                "Dạ em đang hoàn thiện và gửi lại anh/chị ngay đây ạ!"
            lower.contains("họp") || lower.contains("gặp") || lower.contains("hẹn") || lower.contains("call") ->
                "Dạ vâng em đã nắm lịch rồi ạ, em sẽ tham gia đúng giờ!"
            lower.contains("chuyển") || lower.contains("tiền") || lower.contains("thanh toán") ->
                "Dạ vâng để em kiểm tra và thực hiện ngay nhé ạ!"
            lower.contains("xác nhận") || lower.contains("duyệt") || lower.contains("check") ->
                "Dạ vâng em đã kiểm tra và xác nhận rồi ạ."
            else ->
                "Dạ vâng em đã nắm thông tin và đang xử lý rồi nhé ạ!"
        }
    }

    fun copyToClipboard(context: Context, text: String) {
        try {
            Handler(Looper.getMainLooper()).post {
                try {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    val clip = ClipData.newPlainText("OrcaAuto Smart Reply", text)
                    clipboard?.setPrimaryClip(clip)
                    Toast.makeText(context, "📋 Đã sao chép câu trả lời: \"$text\"", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Log.w(TAG, "Không thể copy vào clipboard: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Lỗi post copyToClipboard: ${e.message}")
        }
    }

    private fun showToast(context: Context, text: String, duration: Int = Toast.LENGTH_SHORT) {
        try {
            Handler(Looper.getMainLooper()).post {
                try {
                    Toast.makeText(context, text, duration).show()
                } catch (e: Exception) {
                    Log.w(TAG, "Không thể hiển thị Toast: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Lỗi post showToast: ${e.message}")
        }
    }

    fun cleanExpiredCache() {
        val now = System.currentTimeMillis()
        intentCache.entries.removeIf { now - it.value.timestamp > CACHE_EXPIRY_MS }
    }

    fun clearAll() {
        targetCache.clear()
        intentCache.clear()
    }

    fun getCacheSize(): Int = targetCache.size()

    private fun buildKey(packageName: String, cleanSender: String): String = "$packageName:$cleanSender"

    private fun normalizeText(text: String): String {
        return Normalizer.normalize(text, Normalizer.Form.NFC)
            .trim()
            .lowercase()
    }
}
