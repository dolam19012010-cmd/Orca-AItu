package com.orca.orcaauto.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

data class NoteCatalogTuple(
    val id: Long,
    val title: String,
    val isPrivate: Boolean,
    val isPinned: Boolean = false,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)

@Dao
interface SharedNoteDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(note: SharedNoteEntity): Long

    @Update
    suspend fun update(note: SharedNoteEntity): Int

    @Query("SELECT * FROM shared_notes WHERE id = :id LIMIT 1")
    suspend fun getNoteById(id: Long): SharedNoteEntity?

    @Query("SELECT * FROM shared_notes WHERE id = :id LIMIT 1")
    fun getNoteByIdFlow(id: Long): Flow<SharedNoteEntity?>

    @Query("SELECT * FROM shared_notes WHERE isDeleted = 0 ORDER BY isPinned DESC, updatedAt DESC")
    fun getActiveNotesFlow(): Flow<List<SharedNoteEntity>>

    @Query("SELECT * FROM shared_notes WHERE isDeleted = 0 ORDER BY isPinned DESC, updatedAt DESC")
    suspend fun getActiveNotes(): List<SharedNoteEntity>

    @Query("SELECT * FROM shared_notes WHERE isDeleted = 1 ORDER BY updatedAt DESC")
    fun getTrashNotesFlow(): Flow<List<SharedNoteEntity>>

    @Query("SELECT id, title, isPrivate, isPinned, createdAt, updatedAt FROM shared_notes WHERE isDeleted = 0 ORDER BY isPinned DESC, updatedAt DESC")
    suspend fun getCatalogNotes(): List<NoteCatalogTuple>

    @Query("SELECT * FROM shared_notes WHERE id IN (:ids) AND isDeleted = 0")
    suspend fun getNotesByIds(ids: List<Long>): List<SharedNoteEntity>

    @Query("SELECT * FROM shared_notes WHERE isDeleted = 0 AND (title LIKE '%' || :query || '%' OR content LIKE '%' || :query || '%') ORDER BY isPinned DESC, updatedAt DESC")
    fun searchNotesFlow(query: String): Flow<List<SharedNoteEntity>>

    @Query("UPDATE shared_notes SET isDeleted = 1, updatedAt = :updatedAt WHERE id = :id")
    suspend fun softDelete(id: Long, updatedAt: Long = System.currentTimeMillis()): Int

    @Query("UPDATE shared_notes SET isDeleted = 0, updatedAt = :updatedAt WHERE id = :id")
    suspend fun restore(id: Long, updatedAt: Long = System.currentTimeMillis()): Int

    @Query("DELETE FROM shared_notes WHERE id = :id")
    suspend fun permanentDelete(id: Long): Int

    @Query("UPDATE shared_notes SET isPinned = :isPinned, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updatePinStatus(id: Long, isPinned: Boolean, updatedAt: Long = System.currentTimeMillis()): Int

    @Query("DELETE FROM shared_notes WHERE isDeleted = 1")
    suspend fun clearTrash(): Int
}
