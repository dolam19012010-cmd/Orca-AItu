package com.orca.orcaauto.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface NotificationDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(notification: NotificationEntity): Long

    @Query("SELECT * FROM notifications WHERE isProcessed = 0 ORDER BY timestamp ASC")
    suspend fun getUnprocessedNotifications(): List<NotificationEntity>

    @Query("SELECT * FROM notifications WHERE isProcessed = 0 AND timestamp >= :sinceTimestamp ORDER BY timestamp ASC")
    suspend fun getUnprocessedNotificationsSince(sinceTimestamp: Long): List<NotificationEntity>

    @Query("SELECT * FROM notifications WHERE timestamp >= :sinceTimestamp ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getNotificationsSinceWithLimit(sinceTimestamp: Long, limit: Int): List<NotificationEntity>

    /**
     * Lấy toàn bộ tin nhắn trong vòng 24 giờ qua (không loại trừ tin đã đọc/xử lý, chỉ loại trừ tin quá 24h)
     */
    suspend fun getNotificationsWithin24Hours(limit: Int = 100): List<NotificationEntity> {
        val cutoff24h = System.currentTimeMillis() - 24 * 3600 * 1000L
        return getNotificationsSinceWithLimit(cutoff24h, limit).reversed()
    }

    /**
     * Tương thích với các lời gọi cũ: Không loại trừ tin đã đọc, chỉ lọc bỏ tin quá 24h theo lệnh người dùng
     */
    suspend fun getUnprocessedWithin24Hours(limit: Int = 100): List<NotificationEntity> {
        return getNotificationsWithin24Hours(limit)
    }

    @Query("SELECT COUNT(*) FROM notifications")
    suspend fun getTotalNotificationCount(): Int

    @Query("SELECT id FROM notifications WHERE isProcessed = 1 ORDER BY timestamp ASC LIMIT :count")
    suspend fun getOldestProcessedIds(count: Int): List<Long>

    @Query("SELECT id FROM notifications ORDER BY timestamp ASC LIMIT :count")
    suspend fun getOldestNotificationIds(count: Int): List<Long>

    @Query("DELETE FROM notifications WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>): Int

    suspend fun enforceMaxCapacity(maxCapacity: Int = 200) {
        val total = getTotalNotificationCount()
        if (total > maxCapacity) {
            val toRemove = total - maxCapacity
            // 1. Prioritize deleting already processed notifications (isProcessed = 1) to protect pending messages
            val processedIds = getOldestProcessedIds(toRemove)
            if (processedIds.isNotEmpty()) {
                deleteByIds(processedIds)
            }
            // 2. Only if still exceeding maxCapacity (e.g. massive unprocessed burst > 150), trim oldest
            val remainingTotal = getTotalNotificationCount()
            if (remainingTotal > maxCapacity) {
                val excess = remainingTotal - maxCapacity
                val oldestIds = getOldestNotificationIds(excess)
                if (oldestIds.isNotEmpty()) {
                    deleteByIds(oldestIds)
                }
            }
        }
    }

    @Query("SELECT * FROM notifications WHERE timestamp >= :sinceTimestamp ORDER BY timestamp DESC")
    suspend fun getNotificationsSince(sinceTimestamp: Long): List<NotificationEntity>

    @Query("SELECT COUNT(*) FROM notifications WHERE isProcessed = 0")
    fun getUnprocessedCountFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM notifications WHERE isProcessed = 0")
    suspend fun getUnprocessedCount(): Int

    @Query("SELECT COUNT(*) FROM notifications WHERE appPackage = :appPackage AND sender = :sender AND content = :content AND timestamp >= :afterTimestamp")
    suspend fun countRecentDuplicates(appPackage: String, sender: String, content: String, afterTimestamp: Long): Int

    @Query("UPDATE notifications SET isProcessed = 1 WHERE id IN (:ids)")
    suspend fun markAsProcessed(ids: List<Long>)

    @Query("SELECT * FROM notifications ORDER BY timestamp DESC LIMIT 100")
    fun getRecentNotificationsFlow(): Flow<List<NotificationEntity>>

    @Query("SELECT * FROM notifications ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentNotifications(limit: Int = 50): List<NotificationEntity>

    @Query("DELETE FROM notifications WHERE timestamp < :beforeTimestamp")
    suspend fun deleteOldNotifications(beforeTimestamp: Long): Int

    @Query("DELETE FROM notifications WHERE isProcessed = 1 AND timestamp < :beforeTimestamp")
    suspend fun deleteOldProcessed(beforeTimestamp: Long)

    @Query("DELETE FROM notifications")
    suspend fun clearAllNotifications()
}
