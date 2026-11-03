package com.orca.orcaauto.data.local

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Immutable
enum class SmartAlarmStatus {
    PROPOSED,   // AI suggested in chat, user hasn't ticked yet
    ACTIVE,     // User ticked or confirmed -> scheduled in system
    EXPIRED,    // Target time passed while still PROPOSED
    CANCELLED,  // User or AI explicitly unticked / cancelled
    COMPLETED   // Alarm finished ringing
}

@Immutable
@Entity(
    tableName = "smart_alarms",
    indices = [
        Index(value = ["alarmId"], unique = true),
        Index(value = ["status", "targetTimeMillis"]),
        Index(value = ["isClockAppStaged", "targetTimeMillis"])
    ]
)
data class SmartAlarmEntity(
    @PrimaryKey
    val alarmId: String,                    // Deterministic ID (e.g. "ALM_hash")
    val taskTitle: String,                  // Clean title of the task
    val targetTimeMillis: Long,             // Exact timestamp in ms
    val targetDateLabel: String,            // Human readable: e.g. "15:30 Hôm nay", "10:00 Ngày mai (31/10)"
    val cascadeMode: String = "x2",         // "x1", "x2", "x3"
    val status: SmartAlarmStatus = SmartAlarmStatus.ACTIVE,
    val isClockAppStaged: Boolean = false,  // true if already injected into Native Phone Clock App
    val clockAppStageTimeMillis: Long = 0L, // when it enters < 24h window (target - 23.5 hours)
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
