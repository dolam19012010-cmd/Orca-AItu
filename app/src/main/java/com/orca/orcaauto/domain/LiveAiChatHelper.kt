package com.orca.orcaauto.domain

import android.content.Context
import com.orca.orcaauto.data.local.AppDatabase
import com.orca.orcaauto.data.local.SummaryHistoryEntity
import com.orca.orcaauto.data.preferences.UserPreferences
import com.orca.orcaauto.data.remote.ApiKeyConfig
import com.orca.orcaauto.data.remote.UniversalAiClient
import com.orca.orcaauto.domain.prompt.GeminiPromptBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object LiveAiChatHelper {

    /**
     * Executes an instant on-demand AI call from the Chat Bubble.
     * Combines user's direct question/instruction with latest message context and persona.
     * Saves the resulting report into Room DB summary history.
     */
    suspend fun sendUserPrompt(
        context: Context,
        userQuery: String
    ): Result<Pair<String, ApiKeyConfig>> = withContext(Dispatchers.IO) {
        val db = AppDatabase.getDatabase(context)
        val prefs = UserPreferences.getInstance(context)
        val keyPool = prefs.getKeyPool()

        if (keyPool.none { it.isEnabled && it.rawKey.isNotBlank() }) {
            return@withContext Result.failure(
                IllegalArgumentException("Chưa có API Key nào được kích hoạt. Hãy vào Cài đặt để thêm Key!")
            )
        }

        // 1. Purge old notifications (>24h) and fetch messages within 24h (cap 80)
        val cutoff24h = System.currentTimeMillis() - (24 * 60 * 60 * 1000L)
        db.notificationDao().deleteOldNotifications(cutoff24h)
        var notifications = db.notificationDao().getUnprocessedWithin24Hours(limit = 100)
        val hadUnprocessed = notifications.isNotEmpty()
        if (notifications.isEmpty()) {
            notifications = db.notificationDao().getNotificationsWithin24Hours(cutoff24h).take(50)
        }

        // 2. Build tailored prompt with user's supplemental instruction and previous summary reference
        val fixedContext = prefs.getCustomFixedContext()
        val persona = prefs.getPromptPersona()
        val latestSummaryRecord = db.summaryDao().getLatestSummary()
        val previousSummary = if (prefs.isRecentSummaryContextEnabled()) latestSummaryRecord?.summaryContent.orEmpty() else ""
        val activeAlarms = if (prefs.isLongTermTasksContextEnabled()) db.smartAlarmDao().getActiveAlarms() else emptyList()
        val cancelledTasks = if (prefs.isLongTermTasksContextEnabled()) db.smartAlarmDao().getCancelledAlarms() else emptyList()
        val completedTasks = if (prefs.isLongTermTasksContextEnabled()) db.smartAlarmDao().getCompletedAlarms() else emptyList()
        val taskNotes = prefs.getTaskNotes()
        val prompt = GeminiPromptBuilder.buildSummaryPrompt(
            notifications = notifications,
            customFixedContext = fixedContext,
            persona = persona,
            userQueryOrInstruction = userQuery,
            previousSummary = previousSummary,
            activeAlarms = activeAlarms,
            cancelledTasks = cancelledTasks,
            completedTasks = completedTasks,
            taskNotes = taskNotes,
            timezoneId = prefs.getUserTimezone()
        )

        // 3. Execute failover through configured AI keys
        val client = UniversalAiClient()
        val result = client.executeWithFailover(keyPool, prompt)

        if (result.isSuccess) {
            val (cleanSummary, usedKey) = result.getOrThrow()
            val modelInfo = "${usedKey.provider.displayName} • ${usedKey.modelName}"

            // 4. Save to history with modelInfo
            val timeSlot = SimpleDateFormat("HH:mm - dd/MM", Locale.getDefault()).format(Date()) + " [Lệnh Trực Tiếp]"
            val summaryRecord = SummaryHistoryEntity(
                timestamp = System.currentTimeMillis(),
                targetTimeSlot = timeSlot,
                messageCount = notifications.size,
                summaryContent = cleanSummary,
                status = "SUCCESS",
                modelInfo = modelInfo
            )
            db.summaryDao().insert(summaryRecord)

            // 5. Mark unprocessed messages as processed
            if (hadUnprocessed) {
                db.notificationDao().markAsProcessed(notifications.map { it.id })
            }

            Result.success(Pair(cleanSummary, usedKey))
        } else {
            result
        }
    }
}
