package com.orca.orcaauto.domain

import android.content.Context
import com.orca.orcaauto.data.local.AppDatabase
import com.orca.orcaauto.data.local.SummaryHistoryEntity
import com.orca.orcaauto.data.preferences.SummarySessionConfig
import com.orca.orcaauto.data.preferences.UserPreferences
import com.orca.orcaauto.data.remote.ApiKeyConfig
import com.orca.orcaauto.domain.ai.SequentialAiQueue
import com.orca.orcaauto.domain.filter.SessionMessageFilter
import com.orca.orcaauto.domain.prompt.GeminiPromptBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object LiveAiChatHelper {

    /**
     * Executes an instant on-demand AI call from the Chat Bubble for the active session.
     * Combines user's direct question/instruction with latest message context and persona.
     */
    suspend fun sendUserPrompt(
        context: Context,
        userQuery: String
    ): Result<Pair<String, ApiKeyConfig>> = withContext(Dispatchers.IO) {
        val prefs = UserPreferences.getInstance(context)
        val session = prefs.getSelectedSession()
        summarizeSession(context, session, userQuery)
    }

    /**
     * Executes AI summarization for a specific session strictly via SequentialAiQueue (FIFO).
     * Filters messages by the session's whitelist/banlist and resolves the session's priority keys.
     */
    suspend fun summarizeSession(
        context: Context,
        session: SummarySessionConfig,
        userQueryOrInstruction: String? = null
    ): Result<Pair<String, ApiKeyConfig>> = withContext(Dispatchers.IO) {
        val db = AppDatabase.getDatabase(context)
        val prefs = UserPreferences.getInstance(context)

        // 1. Purge old notifications (>24h) and fetch recent messages within 24h
        val cutoff24h = System.currentTimeMillis() - (24 * 60 * 60 * 1000L)
        db.notificationDao().deleteOldNotifications(cutoff24h)
        val allNotifications = db.notificationDao().getNotificationsWithin24Hours(limit = 100)

        // 2. Filter notifications according to the session rules
        val sessionNotifications = SessionMessageFilter.filter(allNotifications, session)

        if (sessionNotifications.isEmpty()) {
            return@withContext Result.failure(
                IllegalStateException("Không có tin nhắn nào thuộc phiên '${session.name}' trong 24 giờ qua.")
            )
        }

        // 3. Build tailored prompt with user's supplemental instruction and previous summary reference
        val fixedContext = prefs.getCustomFixedContext()
        val persona = prefs.getPromptPersona()
        val latestSummaryRecord = db.summaryDao().getLatestSummaryForSession(session.id)
        val previousSummary = if (prefs.isRecentSummaryContextEnabled()) latestSummaryRecord?.summaryContent.orEmpty() else ""
        val activeAlarms = if (prefs.isLongTermTasksContextEnabled()) db.smartAlarmDao().getActiveAlarms() else emptyList()
        val cancelledTasks = if (prefs.isLongTermTasksContextEnabled()) db.smartAlarmDao().getCancelledAlarms() else emptyList()
        val completedTasks = if (prefs.isLongTermTasksContextEnabled()) db.smartAlarmDao().getCompletedAlarms() else emptyList()
        val taskNotes = prefs.getTaskNotes()

        val instructionParts = mutableListOf<String>()
        if (session.customInstruction.isNotBlank()) {
            instructionParts.add("【CHỈ THỊ RIÊNG CỦA PHIÊN '${session.name}'】:\n${session.customInstruction.trim()}")
        }
        if (!userQueryOrInstruction.isNullOrBlank()) {
            instructionParts.add(userQueryOrInstruction.trim())
        }
        val effectiveInstruction = instructionParts.joinToString("\n\n")

        val prompt = GeminiPromptBuilder.buildSummaryPrompt(
            notifications = sessionNotifications,
            customFixedContext = fixedContext,
            persona = persona,
            userQueryOrInstruction = effectiveInstruction,
            previousSummary = previousSummary,
            activeAlarms = activeAlarms,
            cancelledTasks = cancelledTasks,
            completedTasks = completedTasks,
            taskNotes = taskNotes,
            timezoneId = prefs.getUserTimezone()
        )

        // 4. Execute strictly sequentially through SequentialAiQueue (FIFO with session key priorities)
        val result = SequentialAiQueue.execute(context, session, prompt)

        if (result.isSuccess) {
            val (cleanSummary, usedKey) = result.getOrThrow()
            val modelInfo = "${usedKey.provider.displayName} • ${usedKey.modelName}"

            // 5. Save to history with session metadata
            val timeSlot = SimpleDateFormat("HH:mm - dd/MM", Locale.getDefault()).format(Date()) + " [${session.name}]"
            val summaryRecord = SummaryHistoryEntity(
                timestamp = System.currentTimeMillis(),
                targetTimeSlot = timeSlot,
                messageCount = sessionNotifications.size,
                summaryContent = cleanSummary,
                status = "SUCCESS",
                modelInfo = modelInfo,
                sessionId = session.id,
                sessionName = session.name
            )
            db.summaryDao().insert(summaryRecord)

            // 6. Mark messages as processed
            if (sessionNotifications.isNotEmpty()) {
                db.notificationDao().markAsProcessed(sessionNotifications.map { it.id })
            }

            Result.success(Pair(cleanSummary, usedKey))
        } else {
            result
        }
    }
}
