package com.orca.orcaauto.domain

import android.content.Context
import android.util.Log
import com.orca.orcaauto.data.local.AppDatabase
import com.orca.orcaauto.data.local.SharedNoteEntity
import com.orca.orcaauto.data.local.SummaryHistoryEntity
import com.orca.orcaauto.data.local.getBestSummaryForSession
import com.orca.orcaauto.data.preferences.SummarySessionConfig
import com.orca.orcaauto.data.preferences.UserPreferences
import com.orca.orcaauto.data.remote.ApiKeyConfig
import com.orca.orcaauto.data.remote.UniversalAiClient
import com.orca.orcaauto.domain.ai.SequentialAiQueue
import com.orca.orcaauto.domain.alarm.SystemAlarmHelper
import com.orca.orcaauto.domain.assistant.CommandStripper
import com.orca.orcaauto.domain.assistant.ContextPacker
import com.orca.orcaauto.domain.assistant.LocalRetriever
import com.orca.orcaauto.domain.assistant.Round1Router
import com.orca.orcaauto.domain.assistant.SessionSummaryIntentDetector
import com.orca.orcaauto.domain.filter.SessionMessageFilter
import com.orca.orcaauto.domain.notes.CommandResult
import com.orca.orcaauto.domain.notes.NoteCommand
import com.orca.orcaauto.domain.notes.SharedNoteRepositoryImpl
import com.orca.orcaauto.domain.prompt.GeminiPromptBuilder
import com.orca.orcaauto.domain.prompt.PromptPersona
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Result data class for session summarization containing:
 * - summaryContent: clean 2-part summary text
 * - usedKey: API key used by the LLM
 * - shownCards: list of note cards to display on UI (from SHOW_NOTE / CREATE / referenced notes)
 */
data class SessionSummaryResult(
    val summaryContent: String,
    val usedKey: ApiKeyConfig,
    val shownCards: List<SharedNoteEntity> = emptyList()
) {
    fun toTriple(): Triple<String, ApiKeyConfig, List<SharedNoteEntity>> =
        Triple(summaryContent, usedKey, shownCards)
}

object LiveAiChatHelper {

    private const val TAG = "LiveAiChatHelper"

    /**
     * Executes an instant on-demand AI call from the Chat Bubble for the active session.
     * Combines user's direct question/instruction with latest message context and persona.
     * Supports optional [onTokenDelta] streaming callback for real-time word-by-word token delivery.
     */
    suspend fun sendUserPrompt(
        context: Context,
        userQuery: String,
        onTokenDelta: ((String) -> Unit)? = null
    ): Result<SessionSummaryResult> = withContext(Dispatchers.IO) {
        val prefs = UserPreferences.getInstance(context)
        val session = prefs.getSelectedSession()
        summarizeSession(context, session, userQuery, onTokenDelta)
    }

    /**
     * Executes AI summarization for a specific session via the Unified 2-Round Pipeline:
     * Stage 1: ContextPacker packs lightweight note catalog & 24h notification overview with ticket IDs.
     * Round 1: Round1Router uses lightweight model to select neededNoteIds, senderFilter, deleteSpamTicketIds.
     * Spam Deletion: Automatically deletes spam tickets from Room DB.
     * Intermediate Stage: LocalRetriever loads full note contents (Sealed Pointer for private notes).
     * Round 2: GeminiPromptBuilder builds clean 2-part prompt with internal reasoning guidelines.
     * Execution: SequentialAiQueue executes call sequentially (FIFO with session key prioritization).
     * Post-processing & persistence: CommandStripper extracts note commands, executes them via noteRepo,
     * collects shownCards, persists clean record to summary_history, and marks notifications processed.
     * Supports optional [onTokenDelta] for real-time token streaming.
     */
    suspend fun summarizeSession(
        context: Context,
        session: SummarySessionConfig,
        userQueryOrInstruction: String? = null,
        onTokenDelta: ((String) -> Unit)? = null,
        nominalShiftTag: String? = null,
        onStatusUpdate: ((String) -> Unit)? = null
    ): Result<SessionSummaryResult> = withContext(Dispatchers.IO) {
        val db = AppDatabase.getDatabase(context)
        val prefs = UserPreferences.getInstance(context)
        val noteRepo = SharedNoteRepositoryImpl(db.sharedNoteDao())
        val notifDao = db.notificationDao()
        val universalClient = UniversalAiClient()
        val keyPool = prefs.getKeyPool().filter { it.isEnabled && it.rawKey.isNotBlank() }
        val resolvedKeys = SequentialAiQueue.resolveKeysForSession(session, keyPool)
        if (resolvedKeys.isEmpty()) {
            return@withContext Result.failure(
                IllegalArgumentException("Chưa cấu hình API Key nào! Vui lòng vào Cài đặt để thêm API Key.")
            )
        }

        // 1. Purge old notifications (>24h) and fetch recent messages within 24h
        val cutoff24h = System.currentTimeMillis() - (24 * 60 * 60 * 1000L)
        notifDao.deleteOldNotifications(cutoff24h)
        val allNotifications = notifDao.getNotificationsWithin24Hours(limit = 100)

        // 2. Filter notifications according to the session rules
        val sessionNotifications = SessionMessageFilter.filter(allNotifications, session)

        val isPureSummaryRequest = userQueryOrInstruction.isNullOrBlank() ||
                userQueryOrInstruction.trim() == "Tóm tắt các tin nhắn quan trọng gần đây" ||
                SessionSummaryIntentDetector.detect(userQueryOrInstruction, listOf(session), session).isSummaryRequest

        if (sessionNotifications.isEmpty() && isPureSummaryRequest) {
            val detail = if (allNotifications.isEmpty()) {
                "Chưa có tin nhắn nào được ghi nhận trong 24 giờ qua. Vui lòng kiểm tra quyền Đọc Thông Báo trong Cài đặt."
            } else {
                "Có ${allNotifications.size} tin nhắn trong 24h nhưng chưa có tin nào khớp bộ lọc/Whitelist của phiên '${session.name}'. Hãy kiểm tra lại Whitelist phiên trong Cài đặt."
            }
            return@withContext Result.failure(IllegalStateException(detail))
        }

        // Prepare effective instruction
        val instructionParts = mutableListOf<String>()
        if (session.customInstruction.isNotBlank()) {
            instructionParts.add("【CHỈ THỊ RIÊNG CỦA PHIÊN '${session.name}'】:\n${session.customInstruction.trim()}")
        }
        if (!userQueryOrInstruction.isNullOrBlank()) {
            instructionParts.add(userQueryOrInstruction.trim())
        }
        val effectiveInstruction = if (instructionParts.isNotEmpty()) {
            instructionParts.joinToString("\n\n")
        } else {
            "Tóm tắt các tin nhắn quan trọng gần đây trong phiên '${session.name}'"
        }

        // =========================================================================
        // Stage 1: ContextPacker
        // =========================================================================
        onStatusUpdate?.invoke("🔍 Đang phân tích bối cảnh phiên '${session.name}'...")
        val packer = ContextPacker(noteRepo, notifDao)
        val packagedContext = packer.pack(effectiveInstruction, preloadedNotifications = sessionNotifications)

        // =========================================================================
        // Round 1: Round1Router
        // =========================================================================
        val router = Round1Router(universalClient)
        val routerDecision = router.route(effectiveInstruction, packagedContext, resolvedKeys)

        // =========================================================================
        // Delete Spam Tickets detected in Round 1
        // =========================================================================
        val deleteSpamTickets = routerDecision.deleteSpamTicketIds
        val realIdsToDelete = if (deleteSpamTickets.isNotEmpty()) {
            deleteSpamTickets.mapNotNull { packagedContext.ticketMap[it] }.distinct()
        } else {
            emptyList()
        }
        if (realIdsToDelete.isNotEmpty()) {
            try {
                notifDao.deleteByIds(realIdsToDelete)
                Log.i(TAG, "Đã xóa ${realIdsToDelete.size} tin nhắn rác khỏi Room DB: $realIdsToDelete")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Lỗi khi xóa tin nhắn rác: ${e.message}")
            }
        }

        // =========================================================================
        // Intermediate Stage: LocalRetriever (Sealed Pointer for private notes)
        // =========================================================================
        val retriever = LocalRetriever(noteRepo, notifDao, db.smartAlarmDao())
        val retrievedNotes = try {
            noteRepo.retrieveNotesForAi(routerDecision.neededNoteIds)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Không thể truy xuất ghi chú: ${e.message}")
            emptyList()
        }
        val notesSnippet = if (retrievedNotes.isNotEmpty()) {
            retriever.buildNotesSnippet(retrievedNotes)
        } else {
            ""
        }

        val filteredSessionNotifications = if (realIdsToDelete.isNotEmpty()) {
            sessionNotifications.filterNot { realIdsToDelete.contains(it.id) }
        } else {
            sessionNotifications
        }

        // =========================================================================
        // Round 2 Prompt: GeminiPromptBuilder (clean 2-part format, internal reasoning)
        // =========================================================================
        val fixedContext = if (session.customFixedContext.isNotBlank()) session.customFixedContext else prefs.getCustomFixedContext()
        val persona = if (session.promptPersonaId.isNotBlank()) {
            PromptPersona.fromId(session.promptPersonaId)
        } else {
            prefs.getPromptPersona()
        }
        val latestSummaryRecord = db.summaryDao().getBestSummaryForSession(session.id)
        val rawPreviousSummary = if (prefs.isRecentSummaryContextEnabled()) latestSummaryRecord?.summaryContent.orEmpty() else ""
        val activeAlarms = if (prefs.isLongTermTasksContextEnabled()) db.smartAlarmDao().getActiveAlarms() else emptyList()
        val cancelledTasks = SystemAlarmHelper.filterRecentCancelled(db.smartAlarmDao().getCancelledAlarms())
        val completedTasks = if (prefs.isLongTermTasksContextEnabled()) db.smartAlarmDao().getCompletedAlarms() else emptyList()

        val previousSummary = if (cancelledTasks.isNotEmpty() && rawPreviousSummary.isNotBlank()) {
            tagCancelledTasksInSummary(rawPreviousSummary, cancelledTasks)
        } else {
            rawPreviousSummary
        }

        val effectiveAiLang = when (session.safeAiLanguage) {
            "vi" -> com.orca.orcaauto.ui.locale.AppLanguage.VIETNAMESE
            "en" -> com.orca.orcaauto.ui.locale.AppLanguage.ENGLISH
            else -> prefs.getEffectiveAiLanguage()
        }

        val prompt = GeminiPromptBuilder.buildSummaryPrompt(
            notifications = filteredSessionNotifications,
            customFixedContext = fixedContext,
            persona = persona,
            userQueryOrInstruction = effectiveInstruction,
            previousSummary = previousSummary,
            activeAlarms = activeAlarms,
            cancelledTasks = cancelledTasks,
            completedTasks = completedTasks,
            timezoneId = prefs.getUserTimezone(),
            autoCompleteOverdue = session.autoCompleteOverdue,
            language = effectiveAiLang,
            notesSnippet = notesSnippet
        )

        // =========================================================================
        // Execution: SequentialAiQueue (FIFO with session key priorities)
        // =========================================================================
        onStatusUpdate?.invoke("⚡ Đang tổng hợp phiên '${session.name}' (${filteredSessionNotifications.size} tin)...")
        val result = SequentialAiQueue.execute(context, session, prompt, onTokenDelta, onStatusUpdate)

        if (result.isSuccess) {
            val (rawAiOutput, usedKey) = result.getOrThrow()

            // =====================================================================
            // Post-processing & persistence
            // =====================================================================
            val commands = CommandStripper.extractCommands(rawAiOutput)
            val shownCards = mutableListOf<SharedNoteEntity>()

            // Execute each command via noteRepo.executeAiCommand(...)
            for (cmd in commands) {
                try {
                    val cmdRes = noteRepo.executeAiCommand(cmd)
                    if (cmdRes is CommandResult.Success && cmdRes.payload != null) {
                        val existingIdx = shownCards.indexOfFirst { it.id == cmdRes.payload.id }
                        if (existingIdx >= 0) {
                            shownCards[existingIdx] = cmdRes.payload
                        } else {
                            shownCards.add(cmdRes.payload)
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (t: Throwable) {
                    Log.w(TAG, "Lỗi khi thực thi lệnh ghi chú $cmd: ${t.message}")
                }
            }

            // Also collect shownCards for any [SHOW_NOTE:...] or referenced notes
            for (cmd in commands) {
                if (cmd is NoteCommand.Show) {
                    try {
                        val noteEntity = noteRepo.getNoteById(cmd.noteId)
                        if (noteEntity != null && shownCards.none { it.id == noteEntity.id }) {
                            shownCards.add(noteEntity)
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {}
                }
            }

            // If user asked about notes and router identified public notes, add them if not present
            for (noteId in routerDecision.neededNoteIds) {
                if (shownCards.none { it.id == noteId }) {
                    try {
                        val entity = noteRepo.getNoteById(noteId)
                        if (entity != null && (!entity.isPrivate || commands.any { it is NoteCommand.Show && it.noteId == noteId })) {
                            shownCards.add(entity)
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {}
                }
            }

            val cleanSummary = CommandStripper.cleanFinal(rawAiOutput)
            val modelInfo = "${usedKey.provider.displayName} • ${usedKey.modelName}"

            // Save clean record into Room DB summary_history
            val dateStr = SimpleDateFormat("dd/MM", Locale.getDefault()).format(Date())
            val timeSlot = if (!nominalShiftTag.isNullOrBlank()) {
                "$nominalShiftTag - $dateStr [${session.name}]"
            } else {
                SimpleDateFormat("HH:mm - dd/MM", Locale.getDefault()).format(Date()) + " [${session.name}]"
            }
            val summaryRecord = SummaryHistoryEntity(
                timestamp = System.currentTimeMillis(),
                targetTimeSlot = timeSlot,
                messageCount = filteredSessionNotifications.size,
                summaryContent = cleanSummary,
                status = "SUCCESS",
                modelInfo = modelInfo,
                sessionId = session.id,
                sessionName = session.name
            )
            db.summaryDao().insert(summaryRecord)

            // Mark notifications as processed
            if (filteredSessionNotifications.isNotEmpty()) {
                notifDao.markAsProcessed(filteredSessionNotifications.map { it.id })
            }

            Result.success(SessionSummaryResult(cleanSummary, usedKey, shownCards))
        } else {
            Result.failure(result.exceptionOrNull() ?: Exception("Lỗi gọi AI"))
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
