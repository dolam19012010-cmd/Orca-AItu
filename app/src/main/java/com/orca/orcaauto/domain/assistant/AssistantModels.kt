package com.orca.orcaauto.domain.assistant

import androidx.compose.runtime.Immutable
import com.orca.orcaauto.data.local.SharedNoteEntity
import com.orca.orcaauto.data.remote.ApiKeyConfig
import com.orca.orcaauto.domain.notes.NoteCatalogItem
import com.orca.orcaauto.domain.notes.NoteCommand
import com.orca.orcaauto.domain.notes.RetrievedNote

/**
 * 5-Stage Orchestration Steps for Assistant Pipeline.
 */
@Immutable
enum class AssistantStep {
    IDLE,
    STAGE_1_PACKING_INDEX,     // Đang đóng gói danh mục ghi chú nhẹ & tổng quan tin nhắn 24h
    STAGE_2_ROUND_1_ROUTING,   // Vòng 1: AI nhẹ đang phân tích ngầm lựa chọn noteIds & bộ lọc tin nhắn
    STAGE_3_LOCAL_RETRIEVAL,   // Giai đoạn 3: Truy xuất Room DB cục bộ & áp dụng Sealed Pointer
    STAGE_4_STREAMING_ANSWER,  // Vòng 2: AI mạnh đang stream câu trả lời trực tiếp ra UI
    STAGE_5_EXECUTING_ACTIONS, // Hậu kỳ: Thực thi các lệnh ghi chú (SHOW_NOTE, CREATE, APPEND, UPDATE, DELETE)
    COMPLETED,
    ERROR
}

/**
 * Result of executing an AI note command during Stage 5 post-processing.
 */
@Immutable
data class NoteCommandResult(
    val command: NoteCommand,
    val isSuccess: Boolean,
    val message: String,
    val targetNoteId: Long? = null,
    val payload: SharedNoteEntity? = null
)

/**
 * UI State exposed to Jetpack Compose UI (AssistantChatSheet).
 */
@Immutable
data class AssistantUiState(
    val step: AssistantStep = AssistantStep.IDLE,
    val stepDescription: String = "",
    val streamedThought: String = "",
    val streamedContentClean: String = "", // Đã lọc sạch các thẻ lệnh kỹ thuật theo thời gian thực
    val activeUsedKey: ApiKeyConfig? = null,
    val shownNoteCards: List<SharedNoteEntity> = emptyList(), // Các thẻ ghi chú hiển thị cục bộ dưới câu trả lời
    val executedActions: List<NoteCommandResult> = emptyList(),
    val errorMessage: String? = null
)

/**
 * Stage 1 Output: Lightweight packaged context.
 * Guaranteed: ZERO BYTES of private note content egress.
 */
@Immutable
data class PackagedContext(
    val userPrompt: String,
    val catalogItems: List<NoteCatalogItem>,
    val catalogPromptSnippet: String,
    val notificationSummarySnippet: String,
    val fullOverviewPrompt: String,
    val ticketMap: Map<Int, Long> = emptyMap()
)

/**
 * Stage 2 Output: Routing decision from Round 1 sub-ground call.
 */
@Immutable
data class Round1Decision(
    val neededNoteIds: List<Long> = emptyList(),
    val senderFilter: List<String> = emptyList(),
    val reasoning: String = "",
    val rawAiResponse: String = "",
    val deleteSpamTicketIds: List<Int> = emptyList()
)

/**
 * Stage 3 Output: Assembled local context with Sealed Pointer enforcement.
 */
@Immutable
data class RetrievedContext(
    val retrievedNotes: List<RetrievedNote> = emptyList(),
    val relevantNotificationsSnippet: String = "",
    val assembledRound2Prompt: String = "",
    val systemInstruction: String = ""
)
