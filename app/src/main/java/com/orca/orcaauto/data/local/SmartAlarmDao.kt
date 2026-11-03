package com.orca.orcaauto.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SmartAlarmDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(alarm: SmartAlarmEntity)

    @Query("SELECT * FROM smart_alarms ORDER BY targetTimeMillis ASC")
    fun getAllAlarmsFlow(): Flow<List<SmartAlarmEntity>>

    @Query("SELECT * FROM smart_alarms")
    suspend fun getAllAlarms(): List<SmartAlarmEntity>

    @Query("SELECT * FROM smart_alarms WHERE status = 'ACTIVE' ORDER BY targetTimeMillis ASC")
    fun getActiveAlarmsFlow(): Flow<List<SmartAlarmEntity>>

    @Query("SELECT * FROM smart_alarms WHERE status = 'ACTIVE' ORDER BY targetTimeMillis ASC")
    suspend fun getActiveAlarms(): List<SmartAlarmEntity>

    @Query("SELECT * FROM smart_alarms WHERE status = 'CANCELLED' ORDER BY updatedAt DESC LIMIT 20")
    suspend fun getCancelledAlarms(): List<SmartAlarmEntity>

    @Query("SELECT * FROM smart_alarms WHERE status = 'COMPLETED' ORDER BY updatedAt DESC LIMIT 20")
    suspend fun getCompletedAlarms(): List<SmartAlarmEntity>

    @Query("SELECT * FROM smart_alarms WHERE alarmId = :alarmId LIMIT 1")
    suspend fun getAlarmById(alarmId: String): SmartAlarmEntity?

    @Query("SELECT * FROM smart_alarms WHERE alarmId = :alarmId LIMIT 1")
    fun getAlarmByIdFlow(alarmId: String): Flow<SmartAlarmEntity?>

    @Query("UPDATE smart_alarms SET status = :status, updatedAt = :updatedAt WHERE alarmId = :alarmId")
    suspend fun updateStatus(alarmId: String, status: SmartAlarmStatus, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE smart_alarms SET status = :status, updatedAt = :updatedAt WHERE LOWER(TRIM(taskTitle)) = LOWER(TRIM(:title))")
    suspend fun updateStatusByTitle(title: String, status: SmartAlarmStatus, updatedAt: Long = System.currentTimeMillis())

    @Query("SELECT * FROM smart_alarms WHERE LOWER(TRIM(taskTitle)) = LOWER(TRIM(:title)) LIMIT 1")
    suspend fun getAlarmByTitle(title: String): SmartAlarmEntity?

    @Query("UPDATE smart_alarms SET isClockAppStaged = :staged, updatedAt = :updatedAt WHERE alarmId = :alarmId")
    suspend fun updateClockAppStaged(alarmId: String, staged: Boolean, updatedAt: Long = System.currentTimeMillis())

    @Query("SELECT * FROM smart_alarms WHERE status = 'ACTIVE' AND isClockAppStaged = 0 AND clockAppStageTimeMillis <= :nowMillis")
    suspend fun getUnstagedAlarmsReadyForClockApp(nowMillis: Long): List<SmartAlarmEntity>

    @Query("UPDATE smart_alarms SET status = 'EXPIRED', updatedAt = :nowMillis WHERE status = 'PROPOSED' AND targetTimeMillis < :nowMillis")
    suspend fun markExpiredAlarms(nowMillis: Long)

    @Query("UPDATE smart_alarms SET status = 'COMPLETED', updatedAt = :nowMillis WHERE status = 'ACTIVE' AND targetTimeMillis < :nowMillis - 3600000")
    suspend fun autoMarkPastActiveAlarms(nowMillis: Long)

    @Query("DELETE FROM smart_alarms WHERE status IN ('COMPLETED', 'CANCELLED', 'EXPIRED') AND targetTimeMillis < :cutoffMillis")
    suspend fun deleteOldAlarms(cutoffMillis: Long)

    @Query("DELETE FROM smart_alarms WHERE alarmId = :alarmId")
    suspend fun deleteAlarmById(alarmId: String)
}
