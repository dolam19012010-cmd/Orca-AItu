package com.orca.orcaauto.domain.assistant

import android.content.Context
import android.util.Log
import com.orca.orcaauto.data.local.AppDatabase
import com.orca.orcaauto.data.local.NotificationDao
import com.orca.orcaauto.data.local.SharedNoteEntity
import com.orca.orcaauto.data.preferences.AssistantThinkingLevel
import com.orca.orcaauto.data.preferences.UserPreferences
import com.orca.orcaauto.data.remote.ApiKeyConfig
import com.orca.orcaauto.data.remote.StreamUiEvent
import com.orca.orcaauto.data.remote.StreamingAiClient
import com.orca.orcaauto.data.remote.UniversalAiClient
import com.orca.orcaauto.domain.notes.CommandResult
import com.orca.orcaauto.domain.notes.NoteCommand
import com.orca.orcaauto.domain.notes.SharedNoteRepository
import com.orca.orcaauto.domain.notes.SharedNoteRepositoryImpl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * 5-Stage Orchestrator for Assistant Interactions ("Trợ lý của trợ lý").
 *
 * Runs completely independent of SequentialAiQueue.executionMutex to guarantee
 * that user-facing chat interactions are never blocked by background notification summaries.
 *
 * STAGES:
 * 1. ContextPacker: Lightweight index packing (id, title, isPrivate, updatedAt + 24h message overview).
 *    Zero bytes of private note content are accessed or emitted.
 * 2. Round1Router: Sub-ground lightweight model call completely hidden from the UI.
 *    Parses needed noteIds and sender filters with intelligent keyword fallback.
 * 3. LocalRetriever: Retrieves local Room DB data, strictly enforcing Sealed Pointer
 *    (0 byte egress for private notes).
 * 4. StreamingAiClient: Round 2 model streams answer directly to UI with real-time command tag stripping.
 * 5. Post-Processing: Background execution of note commands (SHOW_NOTE, CREATE, APPEND, UPDATE, DELETE).
 *    Strictly rejects UPDATE/DELETE on private notes via repository security checks.
 */
class AssistantPipeline(
    private val context: Context? = null,
    private val noteRepository: SharedNoteRepository,
    private val streamingAiClient: StreamingAiClient = StreamingAiClient(),
    private val universalAiClient: UniversalAiClient = UniversalAiClient(),
    private val contextPacker: ContextPacker,
    private val round1Router: Round1Router = Round1Router(universalAiClient),
    private val localRetriever: LocalRetriever,
    private val notificationDao: NotificationDao? = null,
    private val keyPoolProvider: () -> List<ApiKeyConfig>,
    private val thinkingLevelProvider: (() -> AssistantThinkingLevel)? = null
) {

    private val activeNotificationDao: NotificationDao?
        get() = notificationDao ?: localRetriever.notificationDao

    companion object {
        private const val TAG = "AssistantPipeline"

        /**
         * Convenience factory using Android Context and Room DB singleton.
         */
        fun create(context: Context): AssistantPipeline {
            val db = AppDatabase.getDatabase(context)
            val noteRepo = SharedNoteRepositoryImpl(db.sharedNoteDao())
            val notifDao = db.notificationDao()
            val userPrefs = UserPreferences.getInstance(context)

            val universalClient = UniversalAiClient()
            val streamingClient = StreamingAiClient()

            val packer = ContextPacker(noteRepo, notifDao)
            val router = Round1Router(universalClient)
            val retriever = LocalRetriever(noteRepo, notifDao)

            return AssistantPipeline(
                context = context,
                noteRepository = noteRepo,
                streamingAiClient = streamingClient,
                universalAiClient = universalClient,
                contextPacker = packer,
                round1Router = router,
                localRetriever = retriever,
                notificationDao = notifDao,
                keyPoolProvider = { userPrefs.getKeyPool() },
                thinkingLevelProvider = { userPrefs.getAssistantThinkingLevel() }
            )
        }

        /**
         * Static helper matching PROJECT.md interface contract:
         * AssistantPipeline.askStreaming(userPrompt: String, context: Context): Flow<AssistantUiState>
         */
        fun askStreaming(userPrompt: String, context: Context): Flow<AssistantUiState> {
            return create(context).askStreaming(userPrompt)
        }
    }

    /**
     * Executes the 5-stage pipeline as an asynchronous Flow.
     * Guarantees clean cancellation when the user dismisses the chat sheet.
     */
    fun askStreaming(userPrompt: String): Flow<AssistantUiState> = flow {
        if (userPrompt.isBlank()) {
            emit(
                AssistantUiState(
                    step = AssistantStep.ERROR,
                    errorMessage = "Yêu cầu của bạn không được để trống."
                )
            )
            return@flow
        }

        val keyPool = keyPoolProvider().filter { it.isEnabled && it.rawKey.isNotBlank() }
        if (keyPool.isEmpty()) {
            emit(
                AssistantUiState(
                    step = AssistantStep.ERROR,
                    errorMessage = "Chưa cấu hình API Key nào! Vui lòng vào Cài đặt để thêm API Key."
                )
            )
            return@flow
        }

        try {
            // =========================================================================
            // GIAI ĐOẠN 1: Đóng gói mục lục nhẹ (ContextPacker)
            // =========================================================================
            emit(
                AssistantUiState(
                    step = AssistantStep.STAGE_1_PACKING_INDEX,
                    stepDescription = "Đang tìm thông tin liên quan..."
                )
            )
            val packagedContext = contextPacker.pack(userPrompt)

            // =========================================================================
            // GIAI ĐOẠN 2: Điều hướng ngầm Vòng 1 (Round1Router)
            // =========================================================================
            emit(
                AssistantUiState(
                    step = AssistantStep.STAGE_2_ROUND_1_ROUTING,
                    stepDescription = "Đang lọc ghi chú và tin nhắn..."
                )
            )
            val routerDecision = round1Router.route(userPrompt, packagedContext, keyPool)

            // =========================================================================
            // Xử lý xóa tin nhắn rác (Spam Deletion) phát hiện từ Vòng 1
            // =========================================================================
            val deleteSpamTickets = routerDecision.deleteSpamTicketIds
            val realIdsToDelete = if (deleteSpamTickets.isNotEmpty()) {
                deleteSpamTickets.mapNotNull { packagedContext.ticketMap[it] }.distinct()
            } else {
                emptyList()
            }

            if (realIdsToDelete.isNotEmpty()) {
                try {
                    activeNotificationDao?.deleteByIds(realIdsToDelete)
                    Log.i(TAG, "Đã xóa ${realIdsToDelete.size} tin nhắn rác khỏi Room DB: $realIdsToDelete")
                } catch (e: Exception) {
                    Log.w(TAG, "Lỗi khi xóa tin nhắn rác $realIdsToDelete: ${e.message}")
                }
            }

            // =========================================================================
            // GIAI ĐOẠN 3: Truy xuất Room DB & Thực thi Sealed Pointer (LocalRetriever)
            // =========================================================================
            emit(
                AssistantUiState(
                    step = AssistantStep.STAGE_3_LOCAL_RETRIEVAL,
                    stepDescription = "Đang mở dữ liệu trên máy..."
                )
            )
            val retrievedContext = localRetriever.retrieve(
                userPrompt = userPrompt,
                decision = routerDecision,
                excludedNotificationIds = realIdsToDelete.toSet()
            )

            // =========================================================================
            // GIAI ĐOẠN 4: Vòng 2 Stream qua SSE ra UI (StreamingAiClient)
            // =========================================================================
            emit(
                AssistantUiState(
                    step = AssistantStep.STAGE_4_STREAMING_ANSWER,
                    stepDescription = "Đang tạo câu trả lời..."
                )
            )

            val rawContentAcc = StringBuilder()
            val rawThoughtAcc = StringBuilder()
            var activeUsedKey: ApiKeyConfig? = null

            val thinkingLevel = thinkingLevelProvider?.invoke()
                ?: context?.let {
                    try {
                        UserPreferences.getInstance(it).getAssistantThinkingLevel()
                    } catch (_: Throwable) {
                        AssistantThinkingLevel.BALANCED
                    }
                }
                ?: AssistantThinkingLevel.BALANCED

            val streamFlow = streamingAiClient.streamWithSpikeRacing(
                keyPool = keyPool,
                prompt = retrievedContext.assembledRound2Prompt,
                systemInstruction = retrievedContext.systemInstruction,
                thinkingLevel = thinkingLevel
            )

            streamFlow.collect { event ->
                when (event) {
                    is StreamUiEvent.Started -> {
                        emit(
                            AssistantUiState(
                                step = AssistantStep.STAGE_4_STREAMING_ANSWER,
                                stepDescription = "Đang kết nối trợ lý...",
                                streamedThought = rawThoughtAcc.toString(),
                                streamedContentClean = CommandStripper.cleanLive(rawContentAcc.toString()),
                                activeUsedKey = activeUsedKey
                            )
                        )
                    }

                    is StreamUiEvent.ThoughtDelta -> {
                        rawThoughtAcc.append(event.text)
                        emit(
                            AssistantUiState(
                                step = AssistantStep.STAGE_4_STREAMING_ANSWER,
                                stepDescription = "Đang suy nghĩ...",
                                streamedThought = rawThoughtAcc.toString(),
                                streamedContentClean = CommandStripper.cleanLive(rawContentAcc.toString()),
                                activeUsedKey = activeUsedKey
                            )
                        )
                    }

                    is StreamUiEvent.ContentDelta -> {
                        rawContentAcc.append(event.text)
                        val cleanDisplay = CommandStripper.cleanLive(rawContentAcc.toString())
                        emit(
                            AssistantUiState(
                                step = AssistantStep.STAGE_4_STREAMING_ANSWER,
                                stepDescription = "Đang trả lời...",
                                streamedThought = rawThoughtAcc.toString(),
                                streamedContentClean = cleanDisplay,
                                activeUsedKey = activeUsedKey
                            )
                        )
                    }

                    is StreamUiEvent.SpikeDetected -> {
                        if (event.isInFlightReset) {
                            rawContentAcc.setLength(0)
                            rawThoughtAcc.setLength(0)
                        }
                        emit(
                            AssistantUiState(
                                step = AssistantStep.STAGE_4_STREAMING_ANSWER,
                                stepDescription = if (event.isInFlightReset) {
                                    "Kênh chính bị gián đoạn, đang chuyển sang kênh dự phòng..."
                                } else {
                                    "Mạng hơi chậm, đang chuyển sang kênh dự phòng..."
                                },
                                streamedThought = rawThoughtAcc.toString(),
                                streamedContentClean = CommandStripper.cleanLive(rawContentAcc.toString()),
                                activeUsedKey = activeUsedKey
                            )
                        )
                    }

                    is StreamUiEvent.Complete -> {
                        activeUsedKey = event.usedKey
                    }

                    is StreamUiEvent.Error -> {
                        throw event.throwable
                    }
                }
            }

            // Final clean text without any trailing partial command syntax
            val finalCleanContent = CommandStripper.cleanFinal(rawContentAcc.toString())

            // =========================================================================
            // GIAI ĐOẠN 5: Hậu kỳ thực thi lệnh ghi chú (Post-processing)
            // =========================================================================
            emit(
                AssistantUiState(
                    step = AssistantStep.STAGE_5_EXECUTING_ACTIONS,
                    stepDescription = "Đang hoàn tất cập nhật ghi chú...",
                    streamedThought = rawThoughtAcc.toString(),
                    streamedContentClean = finalCleanContent,
                    activeUsedKey = activeUsedKey
                )
            )

            val commands = CommandStripper.extractCommands(rawContentAcc.toString())
            val executedResults = mutableListOf<NoteCommandResult>()
            val shownCards = mutableListOf<SharedNoteEntity>()

            // Execute each command exactly once in background
            for (cmd in commands) {
                try {
                    val result = noteRepository.executeAiCommand(cmd)
                    when (result) {
                        is CommandResult.Success -> {
                            executedResults.add(
                                NoteCommandResult(
                                    command = cmd,
                                    isSuccess = true,
                                    message = result.message,
                                    targetNoteId = result.affectedNoteId,
                                    payload = result.payload
                                )
                            )
                            if (result.payload != null) {
                                // Add to cards if SHOW_NOTE or CREATE
                                val existingIndex = shownCards.indexOfFirst { it.id == result.payload.id }
                                if (existingIndex >= 0) {
                                    shownCards[existingIndex] = result.payload
                                } else {
                                    shownCards.add(result.payload)
                                }
                            }
                        }

                        is CommandResult.Failure -> {
                            executedResults.add(
                                NoteCommandResult(
                                    command = cmd,
                                    isSuccess = false,
                                    message = result.errorMessage,
                                    targetNoteId = when (cmd) {
                                        is NoteCommand.Show -> cmd.noteId
                                        is NoteCommand.Append -> cmd.noteId
                                        is NoteCommand.Update -> cmd.noteId
                                        is NoteCommand.Delete -> cmd.noteId
                                        is NoteCommand.Create -> null
                                    }
                                )
                            )
                        }

                        is CommandResult.SecurityViolation -> {
                            Log.w(TAG, "Security violation during command execution: ${result.violationReason}")
                            executedResults.add(
                                NoteCommandResult(
                                    command = cmd,
                                    isSuccess = false,
                                    message = result.violationReason,
                                    targetNoteId = when (cmd) {
                                        is NoteCommand.Show -> cmd.noteId
                                        is NoteCommand.Append -> cmd.noteId
                                        is NoteCommand.Update -> cmd.noteId
                                        is NoteCommand.Delete -> cmd.noteId
                                        is NoteCommand.Create -> null
                                    }
                                )
                            )
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (t: Throwable) {
                    Log.e(TAG, "Error executing command $cmd: ${t.message}", t)
                    executedResults.add(
                        NoteCommandResult(
                            command = cmd,
                            isSuccess = false,
                            message = t.message ?: "Lỗi thực thi lệnh"
                        )
                    )
                }
            }

            // =========================================================================
            // HOÀN TẤT
            // =========================================================================
            emit(
                AssistantUiState(
                    step = AssistantStep.COMPLETED,
                    stepDescription = "Hoàn tất",
                    streamedThought = rawThoughtAcc.toString(),
                    streamedContentClean = finalCleanContent,
                    activeUsedKey = activeUsedKey,
                    shownNoteCards = shownCards.distinctBy { it.id },
                    executedActions = executedResults,
                    errorMessage = null
                )
            )

        } catch (e: CancellationException) {
            Log.i(TAG, "AssistantPipeline coroutine cancelled cleanly.")
            throw e
        } catch (t: Throwable) {
            Log.e(TAG, "AssistantPipeline execution failed: ${t.message}", t)
            emit(
                AssistantUiState(
                    step = AssistantStep.ERROR,
                    stepDescription = "Đã xảy ra lỗi",
                    errorMessage = t.message ?: "Đã xảy ra sự cố trong quá trình xử lý câu hỏi."
                )
            )
        }
    }
}

/**
 * CommandStripper:
 * Responsible for live real-time command stripping, final text cleaning,
 * and robust extraction of note command tags.
 */
object CommandStripper {

    // Regex for fully closed commands: [SHOW_NOTE:1], [CREATE:t:c], [APPEND:1:t], [UPDATE:1:t], [DELETE:1], [DELETE_MSG:1]
    private val completedCommandRegex = Regex(
        """\[(SHOW_NOTE|CREATE|APPEND|UPDATE|DELETE|DELETE_MSG)\s*[:|](.*?)\]""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    // Regex for pending/partial command tag or dangling '[' at the tail of a streaming string
    private val pendingTailCommandRegex = Regex(
        """\[\s*(?:$|(?:SHOW_NOTE|CREATE|APPEND|UPDATE|DELETE|DELETE_MSG|SHOW|CREA|APP|UPD|DEL|[SCAUD])[^\]]*$)""",
        RegexOption.IGNORE_CASE
    )

    /**
     * Strips fully completed command tags AND temporarily suppresses unclosed trailing command tags
     * so that raw syntax does not flicker on the UI while being streamed.
     */
    fun cleanLive(raw: String): String {
        if (raw.isBlank()) return ""
        // 1. Strip completed command tags
        val stripped = completedCommandRegex.replace(raw, "")
        // 2. Suppress unclosed trailing command prefix (including dangling '[')
        return pendingTailCommandRegex.replace(stripped, "").trimEnd()
    }

    /**
     * Strips all completed command tags from final completed text, normalizing excessive whitespace.
     * Also cleans any dangling incomplete command tags if stream was cut off.
     */
    fun cleanFinal(raw: String): String {
        if (raw.isBlank()) return ""
        val stripped = completedCommandRegex.replace(raw, "")
        val cleanTail = pendingTailCommandRegex.replace(stripped, "")
        return cleanTail.replace(Regex("""\n{3,}"""), "\n\n").trim()
    }

    /**
     * Extracts structured [NoteCommand] list from raw model output.
     */
    fun extractCommands(raw: String): List<NoteCommand> {
        if (raw.isBlank()) return emptyList()
        val commands = mutableListOf<NoteCommand>()

        for (match in completedCommandRegex.findAll(raw)) {
            val verb = match.groupValues[1].uppercase().trim()
            val body = match.groupValues[2].trim()

            when (verb) {
                "SHOW_NOTE" -> {
                    val id = body.toLongOrNull() ?: extractFirstLong(body)
                    if (id != null) {
                        commands.add(NoteCommand.Show(id))
                    }
                }

                "CREATE" -> {
                    val parsed = parseCreateParameters(body)
                    if (parsed != null) {
                        commands.add(parsed)
                    }
                }

                "APPEND" -> {
                    val parsed = parseAppendParameters(body)
                    if (parsed != null) {
                        commands.add(parsed)
                    }
                }

                "UPDATE" -> {
                    val parsed = parseUpdateParameters(body)
                    if (parsed != null) {
                        commands.add(parsed)
                    }
                }

                "DELETE" -> {
                    val id = body.toLongOrNull() ?: extractFirstLong(body)
                    if (id != null) {
                        commands.add(NoteCommand.Delete(id))
                    }
                }
            }
        }

        return commands
    }

    private fun extractFirstLong(text: String): Long? {
        val match = Regex("""\d+""").find(text)
        return match?.value?.toLongOrNull()
    }

    private fun parseCreateParameters(body: String): NoteCommand.Create? {
        if (body.isBlank()) return null

        // 1. Prioritize '|' delimiter with limit = 3
        if (body.contains('|')) {
            val parts = body.split('|', limit = 3)
            if (parts.size < 2) return null

            val title = parts[0].trim()
            if (title.isBlank()) return null

            var content: String
            var isPrivate = false

            if (parts.size == 3) {
                val potentialPrivacy = parts[2].trim().lowercase()
                when (potentialPrivacy) {
                    "true", "private", "riêng tư", "rieng tu", "bí mật", "bi mat" -> {
                        isPrivate = true
                        content = parts[1].trim()
                    }
                    "false", "public", "thường", "thuong", "công khai", "cong khai" -> {
                        isPrivate = false
                        content = parts[1].trim()
                    }
                    else -> {
                        isPrivate = false
                        content = "${parts[1]}|${parts[2]}".trim()
                    }
                }
            } else {
                content = parts[1].trim()
                isPrivate = false
            }

            return NoteCommand.Create(
                title = title,
                content = content,
                isPrivate = isPrivate
            )
        }

        // 2. Backward compatibility fallback for ':' delimiter
        val firstSplitIndex = body.indexOf(':')
        if (firstSplitIndex == -1) return null

        val title = body.substring(0, firstSplitIndex).trim()
        val remainder = body.substring(firstSplitIndex + 1).trim()
        if (title.isBlank()) return null

        val secondSplitIndex = remainder.lastIndexOf(':')
        var content = remainder
        var isPrivate = false

        if (secondSplitIndex != -1) {
            val potentialPrivacy = remainder.substring(secondSplitIndex + 1).trim().lowercase()
            if (potentialPrivacy in setOf("true", "private", "riêng tư", "rieng tu", "bí mật", "bi mat")) {
                isPrivate = true
                content = remainder.substring(0, secondSplitIndex).trim()
            } else if (potentialPrivacy in setOf("false", "public", "thường", "thuong", "công khai", "cong khai")) {
                isPrivate = false
                content = remainder.substring(0, secondSplitIndex).trim()
            }
        }

        return NoteCommand.Create(
            title = title,
            content = content,
            isPrivate = isPrivate
        )
    }

    private fun parseAppendParameters(body: String): NoteCommand.Append? {
        if (body.isBlank()) return null

        // 1. Prioritize '|' delimiter with limit = 2
        if (body.contains('|')) {
            val parts = body.split('|', limit = 2)
            if (parts.size < 2) return null
            val idStr = parts[0].trim()
            val textToAppend = parts[1].trim()
            val id = idStr.toLongOrNull() ?: extractFirstLong(idStr) ?: return null
            if (textToAppend.isBlank()) return null
            return NoteCommand.Append(id, textToAppend)
        }

        // 2. Backward compatibility fallback for ':' delimiter
        val splitIndex = body.indexOf(':')
        if (splitIndex == -1) return null

        val idStr = body.substring(0, splitIndex).trim()
        val textToAppend = body.substring(splitIndex + 1).trim()
        val id = idStr.toLongOrNull() ?: extractFirstLong(idStr) ?: return null

        if (textToAppend.isBlank()) return null
        return NoteCommand.Append(id, textToAppend)
    }

    private fun parseUpdateParameters(body: String): NoteCommand.Update? {
        if (body.isBlank()) return null

        // 1. Prioritize '|' delimiter with limit = 2
        if (body.contains('|')) {
            val parts = body.split('|', limit = 2)
            if (parts.size < 2) return null
            val idStr = parts[0].trim()
            val newContent = parts[1].trim()
            val id = idStr.toLongOrNull() ?: extractFirstLong(idStr) ?: return null
            return NoteCommand.Update(id, newContent)
        }

        // 2. Backward compatibility fallback for ':' delimiter
        val splitIndex = body.indexOf(':')
        if (splitIndex == -1) return null

        val idStr = body.substring(0, splitIndex).trim()
        val newContent = body.substring(splitIndex + 1).trim()
        val id = idStr.toLongOrNull() ?: extractFirstLong(idStr) ?: return null

        return NoteCommand.Update(id, newContent)
    }
}
