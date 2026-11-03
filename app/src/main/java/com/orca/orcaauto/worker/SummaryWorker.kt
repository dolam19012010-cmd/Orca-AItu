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

        if (keyPool.isEmpty()) {
            Log.e(TAG, "Chưa thiết lập bất kỳ API Key nào trong Key Pool! Sẽ thử lại sau.")
            return Result.retry()
        }

        return try {
            // 1. Purge notifications older than 24h and fetch unprocessed within 24h (cap at 60)
            val cutoff24h = System.currentTimeMillis() - (24 * 60 * 60 * 1000L)
            database.notificationDao().deleteOldNotifications(cutoff24h)
            val unprocessedNotifications = database.notificationDao().getUnprocessedWithin24Hours(limit = 100)

            if (unprocessedNotifications.isEmpty()) {
                Log.i(TAG, "Không có tin nhắn mới nào cần xử lý trong 24 giờ qua. Hoàn tất chu kỳ.")
                return Result.success()
            }

            Log.i(TAG, "Tìm thấy ${unprocessedNotifications.size} tin nhắn chưa xử lý trong 24h qua. Chuẩn bị prompt...")

            // 2. Build structured prompt with real-time timestamp, fixed user context, and previous summary reference
            val fixedContext = preferences.getCustomFixedContext()
            val persona = preferences.getPromptPersona()
            val latestSummaryRecord = database.summaryDao().getLatestSummary()
            val previousSummary = if (preferences.isRecentSummaryContextEnabled()) latestSummaryRecord?.summaryContent.orEmpty() else ""
            val activeAlarms = if (preferences.isLongTermTasksContextEnabled()) database.smartAlarmDao().getActiveAlarms() else emptyList()
            val cancelledTasks = if (preferences.isLongTermTasksContextEnabled()) database.smartAlarmDao().getCancelledAlarms() else emptyList()
            val completedTasks = if (preferences.isLongTermTasksContextEnabled()) database.smartAlarmDao().getCompletedAlarms() else emptyList()
            val taskNotes = preferences.getTaskNotes()
            val prompt = GeminiPromptBuilder.buildSummaryPrompt(
                notifications = unprocessedNotifications,
                customFixedContext = fixedContext,
                persona = persona,
                previousSummary = previousSummary,
                activeAlarms = activeAlarms,
                cancelledTasks = cancelledTasks,
                completedTasks = completedTasks,
                taskNotes = taskNotes,
                timezoneId = preferences.getUserTimezone()
            )

            // 3. Call Universal AI Client with Multi-Key Failover
            val client = UniversalAiClient()
            val result = client.executeWithFailover(keyPool, prompt)

            if (result.isSuccess) {
                val (cleanSummary, usedKeyConfig) = result.getOrThrow()
                val currentTime = SimpleDateFormat("HH:mm - dd/MM/yyyy", Locale.getDefault()).format(Date())
                val timeSlotWithProvider = "$currentTime [${usedKeyConfig.provider.displayName}]"
                val modelInfo = "${usedKeyConfig.provider.displayName} • ${usedKeyConfig.modelName}"

                // 4. Save summary record into history with modelInfo
                val summaryRecord = SummaryHistoryEntity(
                    timestamp = System.currentTimeMillis(),
                    targetTimeSlot = timeSlotWithProvider,
                    messageCount = unprocessedNotifications.size,
                    summaryContent = cleanSummary,
                    status = "SUCCESS",
                    modelInfo = modelInfo
                )
                database.summaryDao().insert(summaryRecord)

                // 5. Mark messages as processed in Room DB
                val processedIds = unprocessedNotifications.map { it.id }
                database.notificationDao().markAsProcessed(processedIds)

                // 6. Only trigger Status Bar Notification if requested or if Floating Bubble is not available
                val isOverlayActive = com.orca.orcaauto.service.FloatingBubbleService.isOverlayPermissionGranted(applicationContext)
                val userPrefs = com.orca.orcaauto.data.preferences.UserPreferences.getInstance(applicationContext)
                val shouldShowStatusBarNotif = userPrefs.isSummaryStatusBarNotificationEnabled() || !isOverlayActive

                if (shouldShowStatusBarNotif) {
                    try {
                        val parsed = com.orca.orcaauto.domain.filter.SummaryParser.parse(cleanSummary)
                        val timeShort = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                        com.orca.orcaauto.service.SummaryNotificationHelper.showSummaryReportNotification(
                            context = applicationContext,
                            timeSlot = timeShort,
                            messageCount = unprocessedNotifications.size,
                            parsed = parsed,
                            rawSummary = cleanSummary,
                            modelInfo = modelInfo
                        )
                    } catch (e: Exception) {
                        Log.w(TAG, "Không thể gửi Heads-up Notification: ${e.message}")
                    }
                }

                // 7. Pop up Floating Chat Bubble (Bong bóng nổi) if permission is granted
                try {
                    if (isOverlayActive) {
                        com.orca.orcaauto.service.FloatingBubbleService.show(applicationContext, cleanSummary, modelInfo)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Không thể hiển thị Bong bóng nổi: ${e.message}")
                }

                Log.i(TAG, "Tóm tắt AI thành công bằng [${usedKeyConfig.provider.displayName}]! Đã xử lý ${processedIds.size} tin nhắn.")
                Result.success()
            } else {
                val error = result.exceptionOrNull()
                Log.w(TAG, "Toàn bộ các Key trong Key Pool đều thất bại: ${error?.message}. Dữ liệu được bảo toàn trong Room DB. Tự động thử lại sau 15 phút.")
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
