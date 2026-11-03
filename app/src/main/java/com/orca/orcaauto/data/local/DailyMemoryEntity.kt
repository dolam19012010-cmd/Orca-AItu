package com.orca.orcaauto.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "daily_memories",
    indices = [
        Index(value = ["dateString"], unique = true),
        Index(value = ["timestamp"])
    ]
)
data class DailyMemoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val dateString: String, // e.g. "28/10/2026"
    val timestamp: Long = System.currentTimeMillis(),
    val totalMessagesProcessed: Int,
    val shiftCount: Int, // Number of shifts aggregated (e.g. 2: lunch + evening)
    val condensedMemory: String // Super concise 5-7 bullets for long term retrieval
)
