package com.orca.orcaauto.data.local

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Immutable
@Entity(
    tableName = "shared_notes",
    indices = [
        Index(value = ["isDeleted", "updatedAt"]),
        Index(value = ["isPrivate"]),
        Index(value = ["updatedAt"]),
        Index(value = ["isPinned"])
    ]
)
data class SharedNoteEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val content: String = "",
    val isPrivate: Boolean = false,
    val isPinned: Boolean = false,
    val isDeleted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
