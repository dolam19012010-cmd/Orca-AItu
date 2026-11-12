package com.orca.orcaauto.domain

import android.content.Context
import com.orca.orcaauto.data.local.AppDatabase
import com.orca.orcaauto.data.local.SummaryHistoryEntity
import com.orca.orcaauto.data.preferences.SummarySessionConfig
import com.orca.orcaauto.data.preferences.UserPreferences
import com.orca.orcaauto.data.remote.ApiKeyConfig
import com.orca.orcaauto.domain.ai.SequentialAiQueue
import com.orca.orcaauto.domain.alarm.SystemAlarmHelper
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

        val isExplicitUserQuery = !userQueryOrInstruction.isNullOrBlank() &&
                userQueryOrInstruction.trim() != "T\u00F3m t\u1EAFt c\u00E1c tin nh\u1EAFn quan tr\u1ECDng g\u1EA7n \u0111\u00E2y"

        if (sessionNotifications.isEmpty() && !isExplicitUserQuery) {
            return@withContext Result.failure(
                IllegalStateException("Kh\u00F4ng c\u00F3 tin nh\u1EAFn n\u00E0o thu\u1ED9c phi\u00EAn '${session.name}' trong 24 gi\u1EDD qua.")
            )
        }

        // 3. Build tailored prompt with user's supplemental instruction and previous summary reference
        val fixedContext = if (session.customFixedContext.isNotBlank()) session.customFixedContext else prefs.getCustomFixedContext()
        val persona = if (session.promptPersonaId.isNotBlank()) {
            com.orca.orcaauto.domain.prompt.PromptPersona.fromId(session.promptPersonaId)
        } else {
            prefs.getPromptPersona()
        }
        val latestSummaryRecord = db.summaryDao().getLatestSummaryForSession(session.id)
        val rawPreviousSummary = if (prefs.isRecentSummaryContextEnabled()) latestSummaryRecord?.summaryContent.orEmpty() else ""
        val activeAlarms = if (prefs.isLongTermTasksContextEnabled()) db.smartAlarmDao().getActiveAlarms() else emptyList()
        // Luôn nạp danh sách việc đã hủy trong vòng 48 giờ để chặn tái sinh qua nhiều phiên
        val cancelledTasks = SystemAlarmHelper.filterRecentCancelled(db.smartAlarmDao().getCancelledAlarms())
        val completedTasks = if (prefs.isLongTermTasksContextEnabled()) db.smartAlarmDao().getCompletedAlarms() else emptyList()
        val taskNotes = prefs.getTaskNotes()

        // Gắn nhãn [ĐÃ HỦY] cho các việc đã bị hủy trong ca trước để AI tự biết và không nối tiếp
        val previousSummary = if (cancelledTasks.isNotEmpty() && rawPreviousSummary.isNotBlank()) {
            tagCancelledTasksInSummary(rawPreviousSummary, cancelledTasks)
        } else {
            rawPreviousSummary
        }

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
            timezoneId = prefs.getUserTimezone(),
            autoCompleteOverdue = session.autoCompleteOverdue
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

    /**
     * Tags cancelled tasks in previous summary with [ĐÃ HỦY] so the AI sees the explicit cancelled label.
     */
    fun tagCancelledTasksInSummary(
        summaryContent: String,
        cancelledTasks: List<com.orca.orcaauto.data.local.SmartAlarmEntity>
    ): String {
        if (cancelledTasks.isEmpty() || summaryContent.isBlank()) return summaryContent
        var inTaskSection = false
        val taggedLines = summaryContent.lines().map { line ->
            val trimmed = line.trim()
            val lower = trimmed.lowercase()
            val isBullet = trimmed.startsWith("-") || trimmed.startsWith("•") ||
                trimmed.startsWith("*") || trimmed.startsWith("[") || trimmed.matches(Regex("""^\d+\..*"""))

            if (!isBullet) {
                when {
                    lower.contains("nhiệm vụ") || lower.contains("kế hoạch") ||
                        lower.contains("phần 2") || lower.contains("to-do") ||
                        lower.contains("planned") || lower.contains("done") -> inTaskSection = true
                    lower.contains("phần 1") || lower.contains("góc nhìn") || lower.contains("lời khuyên") -> inTaskSection = false
                }
                return@map line
            }

            if (!inTaskSection || trimmed.contains("[ĐÃ HỦY]")) {
                return@map line
            }

            val rawTitleSegment = trimmed.substringBefore("|")
            val cleanLineTitle = rawTitleSegment
                .replace(Regex("""^[\-*•\d.]+\s*"""), "")
                .replace(Regex("""^\[[ xX]?\]\s*"""), "")
                .replace(Regex("""[*_`~]"""), "")
                .replace(Regex("""\s*\((?:[Hh]ạn|[Mm]ục tiêu|[Tt]hời gian):[^)]+\)"""), "")
                .trim()

            val isCancelled = cleanLineTitle.isNotBlank() &&
                cancelledTasks.any { SystemAlarmHelper.isTitleMatching(it.taskTitle, cleanLineTitle) }

            if (isCancelled) "$line [ĐÃ HỦY]" else line
        }
        return taggedLines.joinToString("\n")
    }
}
