package com.orca.orcaauto.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "summary_history",
    indices = [
        Index(value = ["timestamp"])
    ]
)
data class SummaryHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val targetTimeSlot: String, // e.g. "12:00" or "18:00"
    val messageCount: Int,
    val summaryContent: String,
    val status: String = "SUCCESS", // "SUCCESS", "RETRYING", "FAILED"
    val modelInfo: String = "" // e.g. "Google Gemini • gemini-3.8-flash"
)
