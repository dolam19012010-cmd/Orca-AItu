package com.orca.orcaauto.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.orca.orcaauto.data.local.AppDatabase
import com.orca.orcaauto.data.local.SummaryHistoryEntity
import com.orca.orcaauto.data.preferences.UserPreferences
import com.orca.orcaauto.data.remote.UniversalAiClient
import com.orca.orcaauto.domain.prompt.GeminiPromptBuilder
import com.orca.orcaauto.service.NotificationMonitorService
import com.orca.orcaauto.service.OemSurvivalHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SummaryWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val database = AppDatabase.getDatabase(applicationContext)
        val preferences = UserPreferences.getInstance(applicationContext)
        val keyPool = preferences.getKeyPool().filter { it.isEnabled && it.rawKey.isNotBlank() }

        Log.i(TAG, "SummaryWorker bắt đầu thực thi tác vụ tóm tắt...")

        // OEM Survivability Watchdog: Phục hồi kết nối NotificationListenerService nếu bị OEM ngắt ngầm
        try {
            if (OemSurvivalHelper.isNotificationAccessGranted(applicationContext) && !NotificationMonitorService.isConnected) {
                Log.w(TAG, "Watchdog phát hiện NotificationListenerService bị ngắt kết nối ngầm. Đang kích hoạt Component Toggle...")
                OemSurvivalHelper.forceRebindNotificationListener(applicationContext)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi khi chạy Watchdog khôi phục listener trong SummaryWorker: ${e.message}", e)
        }

        if (keyPool.isEmpty()) {
            Log.e(TAG, "Chưa thiết lập bất kỳ API Key nào trong Key Pool! Sẽ thử lại sau.")
            return Result.retry()
        }

        return try {
            // 1. Purge notifications older than 24h and fetch messages within 24h (cap at 100)
            val cutoff24h = System.currentTimeMillis() - (24 * 60 * 60 * 1000L)
            database.notificationDao().deleteOldNotifications(cutoff24h)
            val notificationsIn24h = database.notificationDao().getNotificationsWithin24Hours(limit = 100)

            if (notificationsIn24h.isEmpty()) {
                Log.i(TAG, "Không có tin nhắn nào trong 24 giờ qua. Hoàn tất chu kỳ.")
                return Result.success()
            }

            Log.i(TAG, "Tìm thấy ${notificationsIn24h.size} tin nhắn trong 24h qua. Tiến hành tóm tắt tuần tự cho các phiên...")

            // 2. Load all sessions and order strictly for batch execution (Main session first, then by creation date)
            val allSessions = preferences.loadSessions()
            val orderedSessions = com.orca.orcaauto.domain.ai.SequentialAiQueue.orderSessionsForBatch(allSessions)
            val activeSessionId = preferences.getSelectedSessionId()

            var anySessionSuccess = false
            var displaySummaryContent: String? = null
            var displayModelInfo: String? = null
            var displayMessageCount = 0
            var displaySessionId: String = activeSessionId

            for (session in orderedSessions) {
                // If custom session has auto-summary disabled, skip scheduled run
                if (!session.isMain && !session.isScheduled) {
                    Log.i(TAG, "Phiên '${session.name}' đã tắt tự động tóm tắt theo ca. Bỏ qua.")
                    continue
                }

                // Check if session has matching messages
                val sessionMsgs = com.orca.orcaauto.domain.filter.SessionMessageFilter.filter(notificationsIn24h, session)
                if (sessionMsgs.isEmpty()) {
                    Log.i(TAG, "Phiên '${session.name}' không có tin nhắn phù hợp trong 24h qua. Bỏ qua.")
                    continue
                }

                Log.i(TAG, "Bắt đầu tóm tắt tuần tự cho phiên '${session.name}' (${sessionMsgs.size} tin nhắn)...")
                val result = com.orca.orcaauto.domain.LiveAiChatHelper.summarizeSession(applicationContext, session)

                if (result.isSuccess) {
                    anySessionSuccess = true
                    val (cleanSummary, usedKeyConfig) = result.getOrThrow()
                    val modelInfo = "${usedKeyConfig.provider.displayName} • ${usedKeyConfig.modelName}"

                    // Prioritize display for the currently active session, or fallback to main session
                    if (session.id == activeSessionId || (displaySummaryContent == null && session.isMain)) {
                        displaySummaryContent = cleanSummary
                        displayModelInfo = modelInfo
                        displayMessageCount = sessionMsgs.size
                        displaySessionId = session.id
                    }
                    Log.i(TAG, "Tóm tắt phiên '${session.name}' thành công bằng [${usedKeyConfig.provider.displayName}]!")
                } else {
                    val err = result.exceptionOrNull()?.message
                    Log.w(TAG, "Tóm tắt phiên '${session.name}' thất bại: $err")
                }
            }

            if (anySessionSuccess && displaySummaryContent != null && displayModelInfo != null) {
                // 3. Trigger Status Bar Notification if requested or if Floating Bubble is not available
                val isOverlayActive = com.orca.orcaauto.service.FloatingBubbleService.isOverlayPermissionGranted(applicationContext)
                val shouldShowStatusBarNotif = preferences.isSummaryStatusBarNotificationEnabled() || !isOverlayActive

                if (shouldShowStatusBarNotif) {
                    try {
                        val parsed = com.orca.orcaauto.domain.filter.SummaryParser.parse(displaySummaryContent)
                        val timeShort = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                        com.orca.orcaauto.service.SummaryNotificationHelper.showSummaryReportNotification(
                            context = applicationContext,
                            timeSlot = timeShort,
                            messageCount = displayMessageCount,
                            parsed = parsed,
                            rawSummary = displaySummaryContent,
                            modelInfo = displayModelInfo,
                            sessionId = displaySessionId
                        )
                    } catch (e: Exception) {
                        Log.w(TAG, "Không thể gửi Heads-up Notification: ${e.message}")
                    }
                }

                // 4. Pop up Floating Chat Bubble (Bong bóng nổi) if permission is granted
                try {
                    preferences.setHasUnreadSummary(true)
                    if (isOverlayActive) {
                        com.orca.orcaauto.service.FloatingBubbleService.show(
                            context = applicationContext,
                            summaryContent = displaySummaryContent,
                            modelInfo = displayModelInfo,
                            sessionId = displaySessionId,
                            wakeToState1 = true
                        )
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Không thể hiển thị Bong bóng nổi: ${e.message}")
                }

                Result.success()
            } else if (anySessionSuccess) {
                Result.success()
            } else {
                Log.w(TAG, "Toàn bộ các phiên đều thất bại. Tự động thử lại sau 15 phút.")
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi ngoại lệ trong SummaryWorker: ${e.message}. Tự động thử lại sau 15 phút.", e)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "SummaryWorker"
    }
}
