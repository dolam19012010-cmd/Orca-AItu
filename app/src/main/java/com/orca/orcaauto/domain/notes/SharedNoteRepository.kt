package com.orca.orcaauto.domain.notes

import com.orca.orcaauto.data.local.SharedNoteEntity
import kotlinx.coroutines.flow.Flow

/**
 * Lightweight pointer for AI Catalog (Phase 1).
 * Never carries note content (0 byte egress).
 */
data class NoteCatalogItem(
    val id: Long,
    val title: String,
    val isPrivate: Boolean,
    val updatedAt: Long
)

/**
 * Sealed hierarchy for Phase 3 Local Retrieval:
 * - PublicNote carries full content.
 * - SealedPrivatePointer physically omits content, guaranteeing zero leak to network.
 */
sealed class RetrievedNote {
    abstract val id: Long
    abstract val title: String
    abstract val isPrivate: Boolean

    data class PublicNote(
        override val id: Long,
        override val title: String,
        val content: String,
        val updatedAt: Long
    ) : RetrievedNote() {
        override val isPrivate: Boolean = false
    }

    data class SealedPrivatePointer(
        override val id: Long,
        override val title: String,
        val updatedAt: Long,
        val pointerNotice: String = "[GHI CHÚ RIÊNG TƯ: Nội dung được bảo mật cục bộ. AI chỉ được phép gọi SHOW_NOTE hoặc APPEND]"
    ) : RetrievedNote() {
        override val isPrivate: Boolean = true
    }
}

/**
 * Command models for Phase 5 Post-Processing.
 */
sealed class NoteCommand {
    data class Show(val noteId: Long) : NoteCommand()
    data class Create(val title: String, val content: String, val isPrivate: Boolean = false) : NoteCommand()
    data class Update(val noteId: Long, val newContent: String) : NoteCommand()
    data class Append(val noteId: Long, val textToAppend: String) : NoteCommand()
    data class Delete(val noteId: Long) : NoteCommand()
}

/**
 * Execution result for AI-invoked note commands.
 */
sealed class CommandResult {
    data class Success(
        val message: String,
        val affectedNoteId: Long? = null,
        val payload: SharedNoteEntity? = null
    ) : CommandResult()

    data class Failure(
        val errorMessage: String
    ) : CommandResult()

    data class SecurityViolation(
        val violationReason: String
    ) : CommandResult()
}

/**
 * Repository interface governing both human UI operations and strictly guarded AI operations.
 */
interface SharedNoteRepository {
    // --- User UI Operations ---
    fun getActiveNotesFlow(): Flow<List<SharedNoteEntity>>
    fun getTrashNotesFlow(): Flow<List<SharedNoteEntity>>
    fun searchNotesFlow(query: String): Flow<List<SharedNoteEntity>>
    suspend fun getNoteById(id: Long): SharedNoteEntity?
    suspend fun createNote(title: String, content: String, isPrivate: Boolean, isPinned: Boolean = false): Long
    suspend fun updateNote(id: Long, title: String, content: String, isPrivate: Boolean, isPinned: Boolean = false): Boolean
    suspend fun softDeleteNote(id: Long): Boolean
    suspend fun restoreNote(id: Long): Boolean
    suspend fun permanentDeleteNote(id: Long): Boolean
    suspend fun togglePinNote(id: Long): Boolean
    suspend fun clearTrash(): Int

    // --- AI Assistant Operations (Sealed Pointer Enforced) ---
    suspend fun getCatalogForAi(): List<NoteCatalogItem>
    suspend fun retrieveNotesForAi(noteIds: List<Long>): List<RetrievedNote>
    suspend fun executeAiCommand(command: NoteCommand): CommandResult
}
