package com.orca.orcaauto.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SummaryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(summary: SummaryHistoryEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(summaries: List<SummaryHistoryEntity>): List<Long>

    @Query("SELECT * FROM summary_history ORDER BY timestamp DESC")
    suspend fun getAllSummaries(): List<SummaryHistoryEntity>

    @Query("SELECT * FROM summary_history ORDER BY timestamp DESC")
    fun getAllSummariesFlow(): Flow<List<SummaryHistoryEntity>>

    @Query("SELECT * FROM summary_history ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestSummary(): SummaryHistoryEntity?

    @Query("SELECT * FROM summary_history ORDER BY timestamp DESC LIMIT 1")
    fun getLatestSummaryFlow(): Flow<SummaryHistoryEntity?>

    @Query("SELECT * FROM summary_history WHERE sessionId = :sessionId OR (:sessionId = 'session_main_default' AND (sessionId = '' OR sessionId IS NULL)) ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestSummaryForSession(sessionId: String): SummaryHistoryEntity?

    @Query("SELECT * FROM summary_history WHERE sessionId = :sessionId OR (:sessionId = 'session_main_default' AND (sessionId = '' OR sessionId IS NULL)) ORDER BY timestamp DESC LIMIT 1")
    fun getLatestSummaryForSessionFlow(sessionId: String): Flow<SummaryHistoryEntity?>

    @Query("SELECT * FROM summary_history WHERE sessionId = :sessionId OR (:sessionId = 'session_main_default' AND (sessionId = '' OR sessionId IS NULL)) ORDER BY timestamp DESC")
    fun getSummariesForSessionFlow(sessionId: String): Flow<List<SummaryHistoryEntity>>

    @Query("SELECT * FROM summary_history WHERE timestamp >= :fromTimestamp AND timestamp <= :toTimestamp ORDER BY timestamp ASC")
    suspend fun getSummariesInRange(fromTimestamp: Long, toTimestamp: Long): List<SummaryHistoryEntity>

    @Query("DELETE FROM summary_history WHERE timestamp < :beforeTimestamp")
    suspend fun deleteOldSummaries(beforeTimestamp: Long)

    @Query("DELETE FROM summary_history WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM summary_history WHERE sessionId = :sessionId")
    suspend fun deleteBySessionId(sessionId: String): Int

    @Query("DELETE FROM summary_history WHERE TRIM(summaryContent) LIKE 'Hỏi:%' OR TRIM(summaryContent) LIKE 'hỏi:%' OR TRIM(summaryContent) LIKE 'Hoi:%' OR TRIM(summaryContent) LIKE 'hoi:%' OR summaryContent LIKE '%\nHỏi:%' OR summaryContent LIKE '%\nhỏi:%' OR summaryContent LIKE '%\nHoi:%' OR summaryContent LIKE '%\nhoi:%'")
    suspend fun deleteLegacyChatRecords(): Int

    @Query("SELECT * FROM summary_history WHERE (sessionId = :sessionId OR (:sessionId = 'session_main_default' AND (sessionId = '' OR sessionId IS NULL))) AND TRIM(summaryContent) NOT LIKE 'Hỏi:%' AND TRIM(summaryContent) NOT LIKE 'hỏi:%' AND TRIM(summaryContent) NOT LIKE 'Hoi:%' AND TRIM(summaryContent) NOT LIKE 'hoi:%' AND summaryContent NOT LIKE '%\nHỏi:%' AND summaryContent NOT LIKE '%\nhỏi:%' AND summaryContent NOT LIKE '%\nHoi:%' AND summaryContent NOT LIKE '%\nhoi:%' ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentSummariesForSession(sessionId: String, limit: Int = 10): List<SummaryHistoryEntity>
}

/**
 * Returns the best authoritative summary record for the given session.
 * Prioritizes recent valid summaries with tasks (totalTaskCount > 0),
 * safely falling back to recent non-chat summaries without tasks,
 * and completely filtering out legacy ad-hoc chat records.
 */
suspend fun SummaryDao.getBestSummaryForSession(sessionId: String): SummaryHistoryEntity? {
    val recents = getRecentSummariesForSession(sessionId, limit = 10)
    val validList = recents.filter { it.summaryContent.isNotBlank() && !com.orca.orcaauto.domain.filter.SummaryParser.isLegacyChatRecord(it.summaryContent) }
    return validList.firstOrNull { com.orca.orcaauto.domain.filter.SummaryParser.parse(it.summaryContent).totalTaskCount > 0 }
        ?: validList.firstOrNull()
}
