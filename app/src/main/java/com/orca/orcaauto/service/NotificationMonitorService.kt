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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

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

        // Tải số lượng tin nhắn chưa xử lý ban đầu lên RAM để các lần ghi sau không cần truy vấn COUNT(*)
        serviceScope.launch {
            try {
                val initialCount = database.notificationDao().getUnprocessedCount()
                inMemoryUnprocessedCount.set(initialCount)
            } catch (_: Exception) {}
        }

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
        val timestamp = sbn.postTime.takeIf { it > 0 } ?: System.currentTimeMillis()

        // 1. In-memory rapid deduplication (8-second sliding window)
        // Drops Zalo/Messenger/Telegram reposts synchronously without launching coroutines or hitting disk
        if (isDuplicateNotification(packageName, title, content, timestamp)) {
            Log.d(TAG, "Khử trùng lặp (in-memory 8s): Bỏ qua thông báo lặp từ [$appName] $title: ${content.take(40)}")
            return
        }

        // 1.1. Cache PendingIntent for direct jump to chat (ignoring Group Summary to avoid jumping to Home)
        val isGroupSummary = (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0
        val contentIntent = sbn.notification.contentIntent
        if (!isGroupSummary && contentIntent != null) {
            ChatIntentManager.cacheChatIntent(
                packageName = packageName,
                senderTitle = title,
                pendingIntent = contentIntent
            )
        }

        serviceScope.launch {
            try {
                // 2. Ghi trực tiếp vào Room DB (Khử trùng lặp 8s trên RAM ở bước 1 đã bảo vệ toàn diện)
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

                // Cập nhật bộ đếm trên RAM mà không cần truy vấn SQL COUNT(*)
                val currentUnprocessed = inMemoryUnprocessedCount.incrementAndGet()
                val now = System.currentTimeMillis()

                // Periodic maintenance (throttled to once every 15 mins to save battery and flash I/O)
                if (now - lastMaintenanceTime >= MAINTENANCE_INTERVAL_MS) {
                    lastMaintenanceTime = now
                    // 1. Maintain 24-hour rolling retention window: purge older notifications
                    val cutoff24h = now - (24 * 60 * 60 * 1000L)
                    database.notificationDao().deleteOldNotifications(cutoff24h)

                    // 2. Enforce FIFO Sliding Window (Cap 150 messages, protecting unprocessed items)
                    database.notificationDao().enforceMaxCapacity(maxCapacity = 150)

                    // 3. Đồng bộ lại bộ đếm RAM định kỳ với DB thực tế
                    try {
                        val syncedCount = database.notificationDao().getUnprocessedCount()
                        inMemoryUnprocessedCount.set(syncedCount)
                    } catch (_: Exception) {}
                }

                // 3. Auto-Spillover Trigger: if unread messages accumulate to >= 80 between shifts
                if (currentUnprocessed >= BURST_UNPROCESSED_THRESHOLD && (now - lastBurstTriggerTime) >= BURST_COOLDOWN_MS) {
                    lastBurstTriggerTime = now
                    inMemoryUnprocessedCount.set(0)
                    Log.i(TAG, "Phát hiện dồn ứ $currentUnprocessed tin nhắn giữa 2 ca! Tự động xả đệm & tóm tắt khẩn cấp...")
                    com.orca.orcaauto.worker.WorkScheduler.triggerImmediateSummary(applicationContext)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Lỗi khi lưu thông báo vào Room DB: ${e.message}", e)
            }
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        isConnected = true
        Log.i(TAG, "NotificationListenerService connected.")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        isConnected = false
        Log.w(TAG, "NotificationListenerService disconnected! Triggering auto-rebind...")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            requestRebind(ComponentName(this, NotificationMonitorService::class.java))
        }
        // Thử lại bằng Component Toggle nếu sau 1.5s chưa kết nối lại
        serviceScope.launch {
            kotlinx.coroutines.delay(1500)
            if (!isConnected) {
                OemSurvivalHelper.forceRebindNotificationListener(applicationContext)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isConnected = false
        serviceScope.cancel()
        Log.i(TAG, "NotificationMonitorService destroyed.")
    }

    companion object {
        private const val TAG = "NotificationMonitor"
        @Volatile
        var isConnected: Boolean = false
            private set
        const val DEDUP_WINDOW_MS = 8_000L
        private const val BURST_UNPROCESSED_THRESHOLD = 80 // When unread messages hit 80 between shifts (max 100)
        private const val BURST_COOLDOWN_MS = 15 * 60 * 1000L // 15-minute cooldown between auto-burst summaries
        private const val MAINTENANCE_INTERVAL_MS = 15 * 60 * 1000L // Run DB purge at most once every 15 mins to save battery
        private var lastBurstTriggerTime = 0L
        private var lastMaintenanceTime = 0L
        private val inMemoryUnprocessedCount = AtomicInteger(0)

        // In-memory sliding window cache for rapid deduplication (sub-millisecond O(1) checks)
        // Key: packageName|sender|hash(content) -> timestamp
        private val recentNotificationsMap = ConcurrentHashMap<String, Long>()

        /**
         * Checks whether an incoming notification is a duplicate within the 8-second window.
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

            // Automatically purge old entries (> 30s) if the map grows to keep memory near 0 bytes
            if (recentNotificationsMap.size > 100) {
                val cutoff = now - 30_000L
                recentNotificationsMap.entries.removeIf { it.value < cutoff }
            }

            val lastSeen = recentNotificationsMap[key]
            if (lastSeen != null && (now - lastSeen) < DEDUP_WINDOW_MS) {
                return true
            }

            recentNotificationsMap[key] = now
            return false
        }
    }
}
