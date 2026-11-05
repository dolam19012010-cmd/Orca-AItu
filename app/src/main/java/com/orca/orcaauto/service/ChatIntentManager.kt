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

data class CachedChatIntent(
    val packageName: String,
    val senderTitle: String,
    val pendingIntent: PendingIntent?,
    val timestamp: Long = System.currentTimeMillis(),
    val extractedPhoneOrUsername: String? = null
)

/**
 * Quản lý bộ nhớ đệm Intent và điều hướng 3 tầng:
 * Tầng 1: PendingIntent (sbn.notification.contentIntent) kèm BAL Opt-in cho Android 14/15
 * Tầng 2: Deep Link (tg://, t.me, m.me, zalo.me)
 * Tầng 3: Fallback Native App Launch (getLaunchIntentForPackage)
 */
object ChatIntentManager {
    private const val TAG = "ChatIntentManager"
    const val CACHE_EXPIRY_MS = 45 * 60 * 1000L // 45 phút

    // Key: "$packageName:$cleanSenderTitle"
    private val intentCache = ConcurrentHashMap<String, CachedChatIntent>()

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

    /**
     * Tìm kiếm Intent phù hợp nhất dựa trên task source và task title.
     */
    fun findBestMatchingIntent(sourceText: String, taskTitle: String): CachedChatIntent? {
        cleanExpiredCache()
        val cleanSource = normalizeText(sourceText)
        val cleanTitle = normalizeText(taskTitle)

        // 1. Tìm khớp chính xác hoặc khớp chứa trong senderTitle
        for ((_, cached) in intentCache) {
            val cachedSender = normalizeText(cached.senderTitle)
            if (cleanSource.isNotBlank() && (cleanSource.contains(cachedSender) || cachedSender.contains(cleanSource))) {
                return cached
            }
            if (cleanTitle.contains(cachedSender)) {
                return cached
            }
        }

        // 2. Tìm theo tên app trong sourceText (Zalo, Telegram, Messenger)
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

        // 3. Fallback: lấy Intent mới nhất còn hiệu lực trong cache
        return intentCache.values.maxByOrNull { it.timestamp }
    }

    /**
     * Xác định package của app tương ứng với nhiệm vụ (Zalo, Telegram, Messenger...)
     */
    fun resolveTargetPackage(sourceText: String, taskTitle: String): String {
        val cleanSource = normalizeText(sourceText)
        val cleanTitle = normalizeText(taskTitle)

        // 1. Phân tích trực tiếp từ sourceText
        when {
            cleanSource.contains("zalo") -> return "com.zing.zalo"
            cleanSource.contains("telegram") || cleanSource.contains("tele") -> return "org.telegram.messenger"
            cleanSource.contains("messenger") || cleanSource.contains("facebook") || cleanSource.contains("mess") -> return "com.facebook.orca"
        }

        // 2. Tra cứu trong bộ nhớ đệm người gửi gần đây (sender -> package)
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

        // 3. Phân tích từ taskTitle nếu có nhắc tên app
        when {
            cleanTitle.contains("zalo") -> return "com.zing.zalo"
            cleanTitle.contains("telegram") || cleanTitle.contains("tele") -> return "org.telegram.messenger"
            cleanTitle.contains("messenger") || cleanTitle.contains("mess") -> return "com.facebook.orca"
        }

        // 4. Fallback: package của tin nhắn gần nhất trong cache
        val latest = intentCache.values.maxByOrNull { it.timestamp }
        if (latest != null) {
            return latest.packageName
        }

        // Mặc định Zalo
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

    /**
     * Mở trực tiếp ứng dụng tương ứng với nhiệm vụ (Zalo, Telegram, Messenger)
     */
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

    fun jumpToChat(
        context: Context,
        sourceText: String,
        taskTitle: String,
        autoCopySmartReply: Boolean = false
    ): Boolean {
        return openAppForTask(context, sourceText, taskTitle)
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

    private fun launchFallbackApp(context: Context, packageName: String, senderName: String) {
        try {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (launchIntent != null) {
                context.startActivity(launchIntent)
                showToast(context, "Đã mở app. Hãy chọn chat với: $senderName", Toast.LENGTH_LONG)
            } else {
                showToast(context, "Không tìm thấy ứng dụng trên thiết bị", Toast.LENGTH_SHORT)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi mở app fallback: ${e.message}")
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
        intentCache.clear()
    }

    fun getCacheSize(): Int = intentCache.size

    private fun guessPackageFromSource(sourceText: String): String? {
        val lower = sourceText.lowercase()
        return when {
            lower.contains("zalo") -> "com.zing.zalo"
            lower.contains("telegram") -> "org.telegram.messenger"
            lower.contains("messenger") || lower.contains("facebook") -> "com.facebook.orca"
            else -> null
        }
    }

    private fun buildKey(packageName: String, cleanSender: String): String = "$packageName:$cleanSender"

    private fun normalizeText(text: String): String {
        return Normalizer.normalize(text, Normalizer.Form.NFC)
            .trim()
            .lowercase()
    }
}
