package com.orca.orcaauto.domain.notes

import android.content.Context
import com.orca.orcaauto.data.local.AppDatabase
import com.orca.orcaauto.data.local.SharedNoteDao
import com.orca.orcaauto.data.local.SharedNoteEntity
import kotlinx.coroutines.flow.Flow

class SharedNoteRepositoryImpl(
    private val dao: SharedNoteDao
) : SharedNoteRepository {

    // --- User UI Operations ---

    override fun getActiveNotesFlow(): Flow<List<SharedNoteEntity>> {
        return dao.getActiveNotesFlow()
    }

    override fun getTrashNotesFlow(): Flow<List<SharedNoteEntity>> {
        return dao.getTrashNotesFlow()
    }

    override fun searchNotesFlow(query: String): Flow<List<SharedNoteEntity>> {
        val trimmed = query.trim()
        return if (trimmed.isBlank()) {
            dao.getActiveNotesFlow()
        } else {
            dao.searchNotesFlow(trimmed)
        }
    }

    override suspend fun getNoteById(id: Long): SharedNoteEntity? {
        return dao.getNoteById(id)
    }

    override suspend fun createNote(
        title: String,
        content: String,
        isPrivate: Boolean,
        isPinned: Boolean
    ): Long {
        val now = System.currentTimeMillis()
        val cleanTitle = title.trim().ifBlank { "Ghi chú không tên" }
        val entity = SharedNoteEntity(
            title = cleanTitle,
            content = content.trim(),
            isPrivate = isPrivate,
            isPinned = isPinned,
            isDeleted = false,
            createdAt = now,
            updatedAt = now
        )
        return dao.insert(entity)
    }

    override suspend fun updateNote(
        id: Long,
        title: String,
        content: String,
        isPrivate: Boolean,
        isPinned: Boolean
    ): Boolean {
        val existing = dao.getNoteById(id) ?: return false
        val cleanTitle = title.trim().ifBlank { existing.title }
        val updated = existing.copy(
            title = cleanTitle,
            content = content.trim(),
            isPrivate = isPrivate,
            isPinned = isPinned,
            updatedAt = System.currentTimeMillis()
        )
        return dao.update(updated) > 0
    }

    override suspend fun softDeleteNote(id: Long): Boolean {
        return dao.softDelete(id) > 0
    }

    override suspend fun restoreNote(id: Long): Boolean {
        return dao.restore(id) > 0
    }

    override suspend fun permanentDeleteNote(id: Long): Boolean {
        return dao.permanentDelete(id) > 0
    }

    override suspend fun togglePinNote(id: Long): Boolean {
        val existing = dao.getNoteById(id) ?: return false
        return dao.updatePinStatus(id, !existing.isPinned) > 0
    }

    override suspend fun clearTrash(): Int {
        return dao.clearTrash()
    }

    // --- AI Assistant Operations (Sealed Pointer Enforced) ---

    /**
     * Phase 1 Catalog Index:
     * Only retrieves lightweight metadata (id, title, isPrivate, updatedAt).
     * Note content is never selected or transmitted (0 byte egress).
     */
    override suspend fun getCatalogForAi(): List<NoteCatalogItem> {
        return dao.getCatalogNotes().map { tuple ->
            NoteCatalogItem(
                id = tuple.id,
                title = tuple.title,
                isPrivate = tuple.isPrivate,
                updatedAt = tuple.updatedAt,
                isPinned = tuple.isPinned,
                createdAt = tuple.createdAt
            )
        }
    }

    /**
     * Phase 3 Local Retrieval:
     * - Public notes (isPrivate = false) return [RetrievedNote.PublicNote] with full content.
     * - Private notes (isPrivate = true) return [RetrievedNote.SealedPrivatePointer] where content
     *   is physically excluded, guaranteeing 0 bytes of private note content ever reach the network.
     */
    override suspend fun retrieveNotesForAi(noteIds: List<Long>): List<RetrievedNote> {
        if (noteIds.isEmpty()) return emptyList()
        val entities = dao.getNotesByIds(noteIds)
        val noteMap = entities.associateBy { it.id }

        return noteIds.mapNotNull { id ->
            val note = noteMap[id] ?: return@mapNotNull null
            if (note.isPrivate) {
                RetrievedNote.SealedPrivatePointer(
                    id = note.id,
                    title = note.title,
                    updatedAt = note.updatedAt
                )
            } else {
                RetrievedNote.PublicNote(
                    id = note.id,
                    title = note.title,
                    content = note.content,
                    updatedAt = note.updatedAt
                )
            }
        }
    }

    /**
     * Phase 5 Post-Processing Command Execution:
     * - SHOW_NOTE(id): App resolves note locally from Room DB for UI card rendering.
     * - CREATE(title, content, isPrivate): Creates new note in Room DB.
     * - APPEND(id, text): Permitted for BOTH public and private notes.
     * - UPDATE(id, text): STRICTLY REJECTED for private notes (SecurityViolation).
     * - DELETE(id): STRICTLY REJECTED for private notes (SecurityViolation).
     */
    override suspend fun executeAiCommand(command: NoteCommand): CommandResult {
        return when (command) {
            is NoteCommand.Show -> {
                val note = dao.getNoteById(command.noteId)
                if (note == null || note.isDeleted) {
                    CommandResult.Failure("Ghi chú #${command.noteId} không tồn tại hoặc đã bị xóa.")
                } else {
                    CommandResult.Success(
                        message = "Hiển thị ghi chú #${note.id}",
                        affectedNoteId = note.id,
                        payload = note
                    )
                }
            }

            is NoteCommand.Create -> {
                val cleanTitle = command.title.trim().ifBlank { "Ghi chú từ AI" }
                val now = System.currentTimeMillis()
                val entity = SharedNoteEntity(
                    title = cleanTitle,
                    content = command.content.trim(),
                    isPrivate = command.isPrivate,
                    createdAt = now,
                    updatedAt = now
                )
                val newId = dao.insert(entity)
                CommandResult.Success(
                    message = "Đã tạo ghi chú #${newId}",
                    affectedNoteId = newId,
                    payload = entity.copy(id = newId)
                )
            }

            is NoteCommand.Append -> {
                val note = dao.getNoteById(command.noteId)
                if (note == null || note.isDeleted) {
                    return CommandResult.Failure("Ghi chú #${command.noteId} không tồn tại hoặc đã bị xóa.")
                }
                val textToAppend = command.textToAppend.trim()
                if (textToAppend.isBlank()) {
                    return CommandResult.Failure("Nội dung nối thêm không được để trống.")
                }
                // APPEND is explicitly permitted for both private and public notes
                val newContent = if (note.content.isBlank()) {
                    textToAppend
                } else {
                    "${note.content}\n\n$textToAppend"
                }
                val updated = note.copy(
                    content = newContent,
                    updatedAt = System.currentTimeMillis()
                )
                dao.update(updated)
                CommandResult.Success(
                    message = "Đã nối thêm nội dung vào ghi chú #${note.id}",
                    affectedNoteId = note.id,
                    payload = updated
                )
            }

            is NoteCommand.Update -> {
                val note = dao.getNoteById(command.noteId)
                if (note == null || note.isDeleted) {
                    return CommandResult.Failure("Ghi chú #${command.noteId} không tồn tại hoặc đã bị xóa.")
                }
                // CRITICAL SECURITY ENFORCEMENT:
                if (note.isPrivate) {
                    return CommandResult.SecurityViolation(
                        "VIOLATION: AI không được phép ghi đè nội dung ghi chú riêng tư (#${note.id}). Chỉ cho phép thao tác APPEND."
                    )
                }
                val updated = note.copy(
                    content = command.newContent.trim(),
                    updatedAt = System.currentTimeMillis()
                )
                dao.update(updated)
                CommandResult.Success(
                    message = "Đã cập nhật nội dung ghi chú #${note.id}",
                    affectedNoteId = note.id,
                    payload = updated
                )
            }

            is NoteCommand.Delete -> {
                val note = dao.getNoteById(command.noteId)
                if (note == null || note.isDeleted) {
                    return CommandResult.Failure("Ghi chú #${command.noteId} không tồn tại hoặc đã bị xóa.")
                }
                // CRITICAL SECURITY ENFORCEMENT:
                if (note.isPrivate) {
                    return CommandResult.SecurityViolation(
                        "VIOLATION: AI không được phép xóa ghi chú riêng tư (#${note.id})."
                    )
                }
                dao.softDelete(note.id)
                CommandResult.Success(
                    message = "Đã chuyển ghi chú #${note.id} vào thùng rác",
                    affectedNoteId = note.id
                )
            }
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: SharedNoteRepository? = null

        fun getInstance(context: Context): SharedNoteRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SharedNoteRepositoryImpl(
                    AppDatabase.getDatabase(context).sharedNoteDao()
                ).also { INSTANCE = it }
            }
        }

        fun setInstanceForTesting(repository: SharedNoteRepository?) {
            INSTANCE = repository
        }
    }
}
