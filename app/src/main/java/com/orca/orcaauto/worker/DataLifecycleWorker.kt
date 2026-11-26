package com.orca.orcaauto.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.orca.orcaauto.data.local.AppDatabase
import com.orca.orcaauto.data.local.DailyMemoryEntity
import com.orca.orcaauto.data.preferences.UserPreferences
import com.orca.orcaauto.data.remote.UniversalAiClient
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class DataLifecycleWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val database = AppDatabase.getDatabase(applicationContext)
        val preferences = UserPreferences.getInstance(applicationContext)
        val keyPool = preferences.getKeyPool().filter { it.isEnabled && it.rawKey.isNotBlank() }

        Log.i(TAG, "DataLifecycleWorker bắt đầu chu kỳ dọn dẹp và nén bộ nhớ ngày...")

        val tz = com.orca.orcaauto.domain.assistant.ContextPacker.resolveTimeZone(preferences.getUserTimezone())
        val now = Calendar.getInstance(tz)
        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).apply { timeZone = tz }
        val todayStr = dateFormat.format(now.time)

        // 1. Calculate Start and End timestamps of Today in user's configured timezone
        val startOfDay = Calendar.getInstance(tz).apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val endOfDay = System.currentTimeMillis()

        // 2. Rollup: Create Contextual Memory digest from today's shift summaries (if enabled)
        if (preferences.isContextualMemoryRollupEnabled()) {
            try {
                val todaySummaries = database.summaryDao().getSummariesInRange(startOfDay, endOfDay)
                val existingMemory = database.dailyMemoryDao().getMemoryByDate(todayStr)

                if (todaySummaries.isNotEmpty() && existingMemory == null && keyPool.isNotEmpty()) {
                    val totalMessages = todaySummaries.sumOf { it.messageCount }
                    val combinedText = todaySummaries.joinToString("\n\n---\n\n") {
                        "【Ca: ${it.targetTimeSlot}】\n${it.summaryContent}"
                    }

                    val isEn = preferences.getEffectiveAiLanguage() == com.orca.orcaauto.ui.locale.AppLanguage.ENGLISH
                    val rollupPrompt = if (isEn) {
                        """
                        You are an Executive Chief Secretary. Below are today's ($todayStr) shift summaries:
                        $combinedText
                        
                        REQUIREMENT:
                        Condense everything into 1 minimalist "CONTEXTUAL MEMORY DIGEST" (5-7 concise bullets in English):
                        1. Most important accomplishments today
                        2. Remaining open tasks or urgent deadlines for tomorrow
                        3. Special contextual notes (if any)
                        """.trimIndent()
                    } else {
                        """
                        Bạn là Thư ký Trưởng Cấp cao. Dưới đây là các bản tóm tắt công việc trong ngày $todayStr:
                        $combinedText
                        
                        YÊU CẦU:
                        Hãy cô đọng toàn bộ thành 1 "TRÍ NHỚ NGỮ CẢNH CUỐI NGÀY" tối giản (tối đa 5-7 gạch đầu dòng):
                        1. Việc quan trọng nhất đã giải quyết hôm nay
                        2. Các đầu việc tồn đọng hoặc deadline khẩn cấp cần làm tiếp
                        3. Ghi chú ngữ cảnh đặc biệt cần nhớ (nếu có)
                        Viết thật súc tích, mạch lạc, không rườm rà.
                        """.trimIndent()
                    }

                    val result = com.orca.orcaauto.domain.ai.SequentialAiQueue.execute(
                        context = applicationContext,
                        session = com.orca.orcaauto.data.preferences.SummarySessionConfig.createMainSession(),
                        prompt = rollupPrompt
                    )

                    if (result.isSuccess) {
                        val (condensed, _) = result.getOrThrow()
                        val dailyEntity = DailyMemoryEntity(
                            dateString = todayStr,
                            timestamp = System.currentTimeMillis(),
                            totalMessagesProcessed = totalMessages,
                            shiftCount = todaySummaries.size,
                            condensedMemory = condensed
                        )
                        database.dailyMemoryDao().insert(dailyEntity)
                        Log.i(TAG, "Đã tạo thành công Trí nhớ ngữ cảnh cho $todayStr")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Lỗi khi tạo Trí nhớ ngữ cảnh: ${e.message}", e)
            }
        }

        // 3. Clear Raw Notifications older than 24 hours that are already processed (TTL = 24h)
        try {
            val oneDayAgo = System.currentTimeMillis() - (24 * 60 * 60 * 1000L)
            database.notificationDao().deleteOldProcessed(oneDayAgo)
            Log.i(TAG, "Đã dọn dẹp sạch sẽ các tin nhắn thô đã xử lý cũ hơn 24 giờ.")
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi khi xóa tin nhắn cũ: ${e.message}", e)
        }

        // 4. Clear Shift Summaries older than 30 days (TTL = 30 days)
        try {
            val thirtyDaysAgo = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000L)
            database.summaryDao().deleteOldSummaries(thirtyDaysAgo)
            Log.i(TAG, "Đã dọn dẹp các bản tóm tắt ca cũ hơn 30 ngày.")
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi khi dọn dẹp tóm tắt 30 ngày: ${e.message}", e)
        }

        return Result.success()
    }

    companion object {
        private const val TAG = "DataLifecycleWorker"
    }
}
