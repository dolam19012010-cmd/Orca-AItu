package com.orca.orcaauto.service

import android.app.Notification
import android.content.ComponentName
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.orca.orcaauto.data.local.AppDatabase
import com.orca.orcaauto.data.local.NotificationEntity
import com.orca.orcaauto.data.preferences.UserPreferences
import com.orca.orcaauto.domain.filter.NotificationFilter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import android.util.LruCache

class NotificationMonitorService : NotificationListenerService() {

    private var serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var preferences: UserPreferences
    private lateinit var database: AppDatabase

    override fun onCreate() {
        super.onCreate()
        if (!serviceScope.isActive) {
            serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        }
        preferences = UserPreferences.getInstance(applicationContext)
        database = AppDatabase.getDatabase(applicationContext)
        Log.i(TAG, "NotificationMonitorService created and ready.")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        sbn ?: return

        // Check if the notification passes filter criteria
        if (!NotificationFilter.shouldRecord(sbn, preferences)) {
            return
        }

        val packageName = sbn.packageName ?: return
        val extras = sbn.notification?.extras ?: return

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim() ?: "Người gửi ẩn danh"
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim().orEmpty()
        val content = if (bigText.isNotBlank()) bigText else text

        if (content.isBlank()) return

        val appName = preferences.getTargetAppName(packageName)

        // Phòng thủ an toàn: Loại bỏ ngay lập tức nếu tin nhắn chứa cú pháp lệnh hệ thống hoặc injection
        if (NotificationFilter.containsSystemCommandInjection(content) || NotificationFilter.containsSystemCommandInjection(title)) {
            Log.w(TAG, "⛔ [PHÒNG THỦ LỆNH] Phát hiện và loại bỏ ngay tin nhắn chứa cú pháp lệnh hệ thống hoặc injection: [$appName] $title: ${content.take(40)}")
            return
        }

        val timestamp = sbn.postTime.takeIf { it > 0 } ?: System.currentTimeMillis()

        // 1. In-memory rapid deduplication (sliding window)
        // Drops Zalo/Messenger/Telegram reposts synchronously without launching coroutines or hitting disk
        if (isDuplicateNotification(packageName, title, content, timestamp)) {
            Log.d(TAG, "Khử trùng lặp (in-memory LRU): Bỏ qua thông báo lặp từ [$appName] $title: ${content.take(40)}")
            return
        }

        // 1.1. Cache PendingIntent for direct jump to chat (ignoring Group Summary to avoid jumping to Home)
        val isGroupSummary = (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0
        val contentIntent = sbn.notification.contentIntent
        if (!isGroupSummary && contentIntent != null) {
            ChatIntentManager.cacheChatTarget(
                packageName = packageName,
                rawTitle = title,
                pendingIntent = contentIntent
            )
        }

        serviceScope.launch {
            try {
                // 2. Database level fallback deduplication (prevents duplicates across service restarts)
                val eightSecondsAgo = timestamp - DEDUP_WINDOW_MS
                val duplicates = database.notificationDao().countRecentDuplicates(
                    appPackage = packageName,
                    sender = title,
                    content = content,
                    afterTimestamp = eightSecondsAgo
                )
                if (duplicates > 0) {
                    Log.d(TAG, "Khử trùng lặp (DB level): Bỏ qua thông báo trùng lặp trong 8s: [$appName] $title")
                    return@launch
                }

                val entity = NotificationEntity(
                    appPackage = packageName,
                    appName = appName,
                    sender = title,
                    content = content,
                    timestamp = timestamp,
                    isProcessed = false
                )
                val id = database.notificationDao().insert(entity)
                Log.d(TAG, "Lưu thông báo thành công: [$appName] $title (id=$id)")

                val now = System.currentTimeMillis()

                // Periodic maintenance (throttled to once every 15 mins to save battery and flash I/O)
                if (now - lastMaintenanceTime >= MAINTENANCE_INTERVAL_MS) {
                    lastMaintenanceTime = now
                    // 1. Maintain 24-hour rolling retention window: purge older notifications & injection attempts
                    val cutoff24h = now - (24 * 60 * 60 * 1000L)
                    database.notificationDao().deleteOldNotifications(cutoff24h)
                    database.notificationDao().purgeCommandInjectionNotifications()

                    // 2. Enforce FIFO Sliding Window (Cap 150 messages, protecting unprocessed items)
                    database.notificationDao().enforceMaxCapacity(maxCapacity = 150)
                }

                // 3. Auto-Spillover Trigger: if unread messages accumulate to >= 80 between shifts
                val unprocessed = database.notificationDao().getUnprocessedCount()
                if (unprocessed >= BURST_UNPROCESSED_THRESHOLD && (now - lastBurstTriggerTime) >= BURST_COOLDOWN_MS) {
                    lastBurstTriggerTime = now
                    Log.i(TAG, "Phát hiện dồn ứ $unprocessed tin nhắn giữa 2 ca! Tự động xả đệm & tóm tắt khẩn cấp...")
                    com.orca.orcaauto.worker.WorkScheduler.triggerImmediateSummary(applicationContext)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Lỗi khi lưu thông báo vào Room DB: ${e.message}", e)
            }
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.i(TAG, "NotificationListenerService connected.")
        repopulateActiveChatIntents()
    }

    private fun repopulateActiveChatIntents() {
        try {
            val activeList = activeNotifications ?: return
            for (sbn in activeList) {
                val notif = sbn.notification ?: continue
                val isGroupSummary = (notif.flags and Notification.FLAG_GROUP_SUMMARY) != 0
                val contentIntent = notif.contentIntent
                if (!isGroupSummary && contentIntent != null) {
                    val extras = notif.extras
                    val rawTitle = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString()
                        ?: extras?.getString("android.title")
                        ?: ""
                    if (rawTitle.isNotBlank()) {
                        ChatIntentManager.cacheChatTarget(
                            packageName = sbn.packageName,
                            rawTitle = rawTitle,
                            pendingIntent = contentIntent
                        )
                    }
                }
            }
            Log.i(TAG, "Đã khôi phục cache chat target từ active notifications: ${ChatIntentManager.getCacheSize()} items")
        } catch (e: Exception) {
            Log.w(TAG, "Lỗi khi khôi phục active notifications cache: ${e.message}")
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.w(TAG, "NotificationListenerService disconnected! Triggering auto-rebind...")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            requestRebind(ComponentName(this, NotificationMonitorService::class.java))
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        Log.i(TAG, "NotificationMonitorService destroyed.")
    }

    companion object {
        private const val TAG = "NotificationMonitor"
        const val DEDUP_WINDOW_MS = 6_000L
        private const val BURST_UNPROCESSED_THRESHOLD = 80 // When unread messages hit 80 between shifts (max 100)
        private const val BURST_COOLDOWN_MS = 15 * 60 * 1000L // 15-minute cooldown between auto-burst summaries
        private const val MAINTENANCE_INTERVAL_MS = 15 * 60 * 1000L // Run DB purge at most once every 15 mins to save battery
        private var lastBurstTriggerTime = 0L
        private var lastMaintenanceTime = 0L

        // In-memory O(1) LRU cache for rapid deduplication (sub-millisecond O(1) checks)
        // Key: packageName|sender|hash(content) -> timestamp
        private val recentNotificationsLruCache = LruCache<String, Long>(128)

        /**
         * Checks whether an incoming notification is a duplicate within the deduplication window.
         * Returns true if duplicate (should be dropped), false otherwise.
         */
        fun isDuplicateNotification(
            packageName: String,
            sender: String,
            content: String,
            timestamp: Long
        ): Boolean {
            val now = if (timestamp > 0) timestamp else System.currentTimeMillis()
            val key = "$packageName\u0000$sender\u0000${content.hashCode()}"

            synchronized(recentNotificationsLruCache) {
                val lastSeen = recentNotificationsLruCache.get(key)
                if (lastSeen != null && (now - lastSeen) < DEDUP_WINDOW_MS) {
                    return true
                }
                recentNotificationsLruCache.put(key, now)
                return false
            }
        }

        fun clearDeduplicationCacheForTesting() {
            synchronized(recentNotificationsLruCache) { recentNotificationsLruCache.evictAll() }
        }
    }
}
