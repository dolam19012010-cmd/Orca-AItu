package com.orca.orcaauto.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.orca.orcaauto.data.preferences.SummarySessionConfig

@Entity(
    tableName = "summary_history",
    indices = [
        Index(value = ["timestamp"]),
        Index(value = ["sessionId"])
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
    val modelInfo: String = "", // e.g. "Google Gemini • gemini-3.8-flash"
    val sessionId: String = SummarySessionConfig.MAIN_SESSION_ID,
    val sessionName: String = "Phiên chính"
)
