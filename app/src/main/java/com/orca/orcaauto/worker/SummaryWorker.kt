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
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SummaryWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val targetHour = inputData.getInt(WorkScheduler.KEY_TARGET_HOUR, -1)
        val targetMinute = inputData.getInt(WorkScheduler.KEY_TARGET_MINUTE, -1)
        val isScheduledShift = targetHour in 0..23 && targetMinute in 0..59
        val nominalShiftTag = if (isScheduledShift) {
            inputData.getString(WorkScheduler.KEY_NOMINAL_SHIFT_TAG) ?: WorkScheduler.formatShiftTag(targetHour, targetMinute)
        } else null

        suspend fun rescheduleNextOccurrenceIfNeeded() {
            if (isScheduledShift) {
                withContext(NonCancellable) {
                    try {
                        WorkScheduler.scheduleNextOccurrence(applicationContext, targetHour, targetMinute)
                    } catch (e: Exception) {
                        Log.w(TAG, "Không thể tái lập lịch cho ca $nominalShiftTag: ${e.message}")
                    }
                }
            }
        }

        if (runAttemptCount >= 2) {
            Log.w(TAG, "SummaryWorker đã vượt quá số lần thử lại tối đa ($runAttemptCount). Dừng tác vụ để tiết kiệm pin.")
            rescheduleNextOccurrenceIfNeeded()
            return Result.failure()
        }

        val database = AppDatabase.getDatabase(applicationContext)
        val preferences = UserPreferences.getInstance(applicationContext)
        val keyPool = preferences.getKeyPool().filter { it.isEnabled && it.rawKey.isNotBlank() }

        Log.i(TAG, "SummaryWorker bắt đầu thực thi tác vụ tóm tắt [${nominalShiftTag ?: "Thủ công"}]...")

        if (keyPool.isEmpty()) {
            Log.i(TAG, "Chưa thiết lập bất kỳ API Key nào trong Key Pool. Hoàn tất chu kỳ để tiết kiệm pin.")
            rescheduleNextOccurrenceIfNeeded()
            return Result.success()
        }

        return try {
            // 1. Purge notifications older than 24h and fetch messages within 24h (cap at 300 so quiet VIP sessions aren't starved)
            val cutoff24h = System.currentTimeMillis() - (24 * 60 * 60 * 1000L)
            database.notificationDao().deleteOldNotifications(cutoff24h)
            val notificationsIn24h = database.notificationDao().getNotificationsWithin24Hours(limit = 300)

            if (notificationsIn24h.isEmpty()) {
                Log.i(TAG, "Không có tin nhắn nào trong 24 giờ qua. Hoàn tất chu kỳ.")
                rescheduleNextOccurrenceIfNeeded()
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
                val result = com.orca.orcaauto.domain.LiveAiChatHelper.summarizeSession(
                    context = applicationContext,
                    session = session,
                    nominalShiftTag = nominalShiftTag
                )

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
                // 3. Pop up Floating Chat Bubble first (in-process if alive; otherwise try startService)
                val isOverlayActive = com.orca.orcaauto.service.FloatingBubbleService.isOverlayPermissionGranted(applicationContext)
                var bubbleShown = false
                try {
                    preferences.setHasUnreadSummary(true)
                    if (isOverlayActive) {
                        bubbleShown = com.orca.orcaauto.service.FloatingBubbleService.show(
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

                // 4. Trigger Status Bar Notification if enabled, overlay missing, or Android blocked cold background service start
                val shouldShowStatusBarNotif = preferences.isSummaryStatusBarNotificationEnabled() || !isOverlayActive || !bubbleShown

                if (shouldShowStatusBarNotif) {
                    try {
                        val parsed = com.orca.orcaauto.domain.filter.SummaryParser.parse(displaySummaryContent)
                        val timeShort = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                        com.orca.orcaauto.service.SummaryNotificationHelper.showSummaryReportNotification(
                            context = applicationContext,
                            timeSlot = nominalShiftTag ?: timeShort,
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

                rescheduleNextOccurrenceIfNeeded()
                Result.success()
            } else if (anySessionSuccess) {
                rescheduleNextOccurrenceIfNeeded()
                Result.success()
            } else {
                Log.w(TAG, "Toàn bộ các phiên đều thất bại. Số lần đã thử: $runAttemptCount.")
                if (runAttemptCount >= 2) {
                    rescheduleNextOccurrenceIfNeeded()
                    Result.failure()
                } else {
                    Result.retry()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi ngoại lệ trong SummaryWorker: ${e.message}. Số lần đã thử: $runAttemptCount.", e)
            if (runAttemptCount >= 2) {
                rescheduleNextOccurrenceIfNeeded()
                Result.failure()
            } else {
                Result.retry()
            }
        }
    }

    companion object {
        private const val TAG = "SummaryWorker"
    }
}
