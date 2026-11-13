package com.orca.orcaauto.domain.notes

import com.orca.orcaauto.data.local.NoteCatalogTuple
import com.orca.orcaauto.data.local.SharedNoteDao
import com.orca.orcaauto.data.local.SharedNoteEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import java.util.concurrent.atomic.AtomicLong

/**
 * In-memory thread-safe Room DAO fake implementing [SharedNoteDao] for JVM unit testing.
 * Accurately replicates Room's reactive query emissions and SQL sorting/filtering behaviors.
 */
class FakeSharedNoteDao : SharedNoteDao {

    private val lock = Any()
    private val nextId = AtomicLong(1L)
    private val _notesFlow = MutableStateFlow<Map<Long, SharedNoteEntity>>(emptyMap())
    val rawNotesFlow: Flow<Map<Long, SharedNoteEntity>> = _notesFlow.asStateFlow()

    override suspend fun insert(note: SharedNoteEntity): Long = synchronized(lock) {
        val id = if (note.id == 0L) {
            nextId.getAndIncrement()
        } else {
            if (note.id >= nextId.get()) {
                nextId.set(note.id + 1)
            }
            note.id
        }
        val entity = note.copy(id = id)
        _notesFlow.value = _notesFlow.value + (id to entity)
        id
    }

    override suspend fun update(note: SharedNoteEntity): Int = synchronized(lock) {
        if (!_notesFlow.value.containsKey(note.id)) return@synchronized 0
        _notesFlow.value = _notesFlow.value + (note.id to note)
        1
    }

    override suspend fun getNoteById(id: Long): SharedNoteEntity? = synchronized(lock) {
        _notesFlow.value[id]
    }

    override fun getNoteByIdFlow(id: Long): Flow<SharedNoteEntity?> {
        return _notesFlow.map { map -> map[id] }
    }

    override fun getActiveNotesFlow(): Flow<List<SharedNoteEntity>> {
        return _notesFlow.map { map ->
            map.values
                .filter { !it.isDeleted }
                .sortedWith(compareByDescending<SharedNoteEntity> { it.isPinned }.thenByDescending { it.updatedAt })
        }
    }

    override suspend fun getActiveNotes(): List<SharedNoteEntity> = synchronized(lock) {
        _notesFlow.value.values
            .filter { !it.isDeleted }
            .sortedWith(compareByDescending<SharedNoteEntity> { it.isPinned }.thenByDescending { it.updatedAt })
    }

    override fun getTrashNotesFlow(): Flow<List<SharedNoteEntity>> {
        return _notesFlow.map { map ->
            map.values
                .filter { it.isDeleted }
                .sortedByDescending { it.updatedAt }
        }
    }

    override suspend fun getCatalogNotes(): List<NoteCatalogTuple> = synchronized(lock) {
        _notesFlow.value.values
            .filter { !it.isDeleted }
            .sortedWith(compareByDescending<SharedNoteEntity> { it.isPinned }.thenByDescending { it.updatedAt })
            .map {
                NoteCatalogTuple(
                    id = it.id,
                    title = it.title,
                    isPrivate = it.isPrivate,
                    isPinned = it.isPinned,
                    createdAt = it.createdAt,
                    updatedAt = it.updatedAt
                )
            }
    }

    override suspend fun getNotesByIds(ids: List<Long>): List<SharedNoteEntity> = synchronized(lock) {
        val targetIds = ids.toSet()
        _notesFlow.value.values
            .filter { targetIds.contains(it.id) && !it.isDeleted }
    }

    override fun searchNotesFlow(query: String): Flow<List<SharedNoteEntity>> {
        val q = query.trim().lowercase()
        return _notesFlow.map { map ->
            map.values
                .filter { !it.isDeleted && (it.title.lowercase().contains(q) || it.content.lowercase().contains(q)) }
                .sortedWith(compareByDescending<SharedNoteEntity> { it.isPinned }.thenByDescending { it.updatedAt })
        }
    }

    override suspend fun softDelete(id: Long, updatedAt: Long): Int = synchronized(lock) {
        val existing = _notesFlow.value[id] ?: return@synchronized 0
        _notesFlow.value = _notesFlow.value + (id to existing.copy(isDeleted = true, updatedAt = updatedAt))
        1
    }

    override suspend fun restore(id: Long, updatedAt: Long): Int = synchronized(lock) {
        val existing = _notesFlow.value[id] ?: return@synchronized 0
        _notesFlow.value = _notesFlow.value + (id to existing.copy(isDeleted = false, updatedAt = updatedAt))
        1
    }

    override suspend fun permanentDelete(id: Long): Int = synchronized(lock) {
        if (!_notesFlow.value.containsKey(id)) return@synchronized 0
        _notesFlow.value = _notesFlow.value - id
        1
    }

    override suspend fun updatePinStatus(id: Long, isPinned: Boolean, updatedAt: Long): Int = synchronized(lock) {
        val existing = _notesFlow.value[id] ?: return@synchronized 0
        _notesFlow.value = _notesFlow.value + (id to existing.copy(isPinned = isPinned, updatedAt = updatedAt))
        1
    }

    override suspend fun clearTrash(): Int = synchronized(lock) {
        val trashIds = _notesFlow.value.values.filter { it.isDeleted }.map { it.id }
        _notesFlow.value = _notesFlow.value.filterKeys { !trashIds.contains(it) }
        trashIds.size
    }

    fun clearAll() = synchronized(lock) {
        _notesFlow.value = emptyMap()
        nextId.set(1L)
    }
}
