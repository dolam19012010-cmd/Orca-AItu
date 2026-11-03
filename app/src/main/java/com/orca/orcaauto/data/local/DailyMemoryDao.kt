package com.orca.orcaauto.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyMemoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(dailyMemory: DailyMemoryEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(list: List<DailyMemoryEntity>): List<Long>

    @Query("SELECT * FROM daily_memories ORDER BY timestamp DESC")
    suspend fun getAllDailyMemories(): List<DailyMemoryEntity>

    @Query("SELECT * FROM daily_memories ORDER BY timestamp DESC")
    fun getAllDailyMemoriesFlow(): Flow<List<DailyMemoryEntity>>

    @Query("SELECT * FROM daily_memories WHERE dateString = :dateString LIMIT 1")
    suspend fun getMemoryByDate(dateString: String): DailyMemoryEntity?

    @Query("DELETE FROM daily_memories WHERE id = :id")
    suspend fun deleteById(id: Long)
}
