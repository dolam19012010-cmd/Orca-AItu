package com.orca.orcaauto.worker

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

object WorkScheduler {

    private const val TAG = "WorkScheduler"
    private const val WORK_12PM_NAME = "OrcaAuto_Summary_12PM"
    private const val WORK_18PM_NAME = "OrcaAuto_Summary_18PM"
    private const val WORK_LIFECYCLE_NAME = "OrcaAuto_Data_Lifecycle"
    const val MANUAL_WORK_NAME = "OrcaAuto_Manual_Summary"

    const val KEY_TARGET_HOUR = "target_hour"
    const val KEY_TARGET_MINUTE = "target_minute"
    const val KEY_NOMINAL_SHIFT_TAG = "nominal_shift_tag"
    const val KEY_NOMINAL_TIME = "nominal_time"

    fun formatShiftTag(hour: Int, minute: Int): String = "Ca: %02d:%02d".format(Locale.US, hour, minute)
    fun formatShiftTime(hour: Int, minute: Int): String = "%02d:%02d".format(Locale.US, hour, minute)
    fun getWorkName(hour: Int, minute: Int): String = "OrcaAuto_Summary_${hour}h${minute}"
    fun getTag(hour: Int, minute: Int): String = "OrcaAuto_Summary_Tag_${hour}h${minute}"

    /**
     * Calculates delay in milliseconds from [nowMillis] to the next occurrence of [targetHour]:[targetMinute]:00.000.
     * Guaranteed to anchor to exact wall-clock seconds, eliminating cumulative drift.
     */
    fun calculateInitialDelay(targetHour: Int, targetMinute: Int, nowMillis: Long = System.currentTimeMillis()): Long {
        val now = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val target = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, targetHour)
            set(Calendar.MINUTE, targetMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        if (target.timeInMillis <= now.timeInMillis) {
            target.add(Calendar.DAY_OF_YEAR, 1)
        }

        return target.timeInMillis - now.timeInMillis
    }

    /**
     * Calculates delay in milliseconds for the subsequent occurrence of [targetHour]:[targetMinute]:00.000.
     */
    fun calculateNextOccurrenceDelay(targetHour: Int, targetMinute: Int, nowMillis: Long = System.currentTimeMillis()): Long {
        return calculateInitialDelay(targetHour, targetMinute, nowMillis)
    }

    /**
     * Schedules daily summary tasks dynamically based on user-configured hours (UserPreferences),
     * using wall-clock anchored OneTimeWorkRequests to eliminate cumulative drift.
     * Also schedules daily data lifecycle worker (cleanup + daily memory rollup) at 23:50.
     */
    fun scheduleDailySummaries(context: Context) {
        val workManager = WorkManager.getInstance(context)
        val preferences = com.orca.orcaauto.data.preferences.UserPreferences.getInstance(context)
        val hoursList = (preferences.getScheduledHours() + preferences.loadSessions().filter { it.isScheduled }.flatMap { it.scheduledHours }).distinct().sorted()

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        // 1. Dynamically schedule for each user-defined hour using wall-clock anchored OneTimeWorkRequest
        for (timeStr in hoursList) {
            val parts = timeStr.split(":")
            if (parts.size == 2) {
                val hour = parts[0].toIntOrNull() ?: continue
                val minute = parts[1].toIntOrNull() ?: continue
                val workName = getWorkName(hour, minute)

                // Cancel legacy periodic work with this name to ensure clean migration
                try {
                    workManager.cancelUniqueWork(workName)
                } catch (_: Exception) {}

                val initialDelay = calculateInitialDelay(hour, minute)
                val inputData = Data.Builder()
                    .putInt(KEY_TARGET_HOUR, hour)
                    .putInt(KEY_TARGET_MINUTE, minute)
                    .putString(KEY_NOMINAL_SHIFT_TAG, formatShiftTag(hour, minute))
                    .putString(KEY_NOMINAL_TIME, formatShiftTime(hour, minute))
                    .build()

                val request = OneTimeWorkRequestBuilder<SummaryWorker>()
                    .setInputData(inputData)
                    .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
                    .setConstraints(constraints)
                    .setBackoffCriteria(
                        BackoffPolicy.EXPONENTIAL,
                        15,
                        TimeUnit.MINUTES
                    )
                    .addTag(getTag(hour, minute))
                    .build()

                workManager.enqueueUniqueWork(
                    workName,
                    ExistingWorkPolicy.REPLACE,
                    request
                )
                Log.i(TAG, "Đã lập lịch OneTimeWork cho ca ${formatShiftTag(hour, minute)} với delay ${initialDelay}ms")
            }
        }

        // 2. Schedule 23:50 Data Lifecycle & Rollup job
        val initialDelay2350 = calculateInitialDelay(23, 50)
        val lifecycleRequest = PeriodicWorkRequestBuilder<DataLifecycleWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(initialDelay2350, TimeUnit.MILLISECONDS)
            .build()

        workManager.enqueueUniquePeriodicWork(
            WORK_LIFECYCLE_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            lifecycleRequest
        )
    }

    /**
     * Reschedules the next occurrence of a shift after completion of the current worker run.
     * Verifies that the shift is still configured before enqueuing.
     */
    fun scheduleNextOccurrence(context: Context, hour: Int, minute: Int) {
        val preferences = com.orca.orcaauto.data.preferences.UserPreferences.getInstance(context)
        val hoursList = (preferences.getScheduledHours() + preferences.loadSessions().filter { it.isScheduled }.flatMap { it.scheduledHours }).distinct()
        val timeFormatted = formatShiftTime(hour, minute)
        val exists = hoursList.any { it.trim() == timeFormatted || it.trim() == "$hour:$minute" }

        val workName = getWorkName(hour, minute)
        val workManager = WorkManager.getInstance(context)

        if (!exists) {
            Log.i(TAG, "Ca $timeFormatted không còn tồn tại trong cấu hình. Huỷ bỏ tái lập lịch.")
            workManager.cancelUniqueWork(workName)
            return
        }

        val nextDelay = calculateNextOccurrenceDelay(hour, minute)
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val inputData = Data.Builder()
            .putInt(KEY_TARGET_HOUR, hour)
            .putInt(KEY_TARGET_MINUTE, minute)
            .putString(KEY_NOMINAL_SHIFT_TAG, formatShiftTag(hour, minute))
            .putString(KEY_NOMINAL_TIME, formatShiftTime(hour, minute))
            .build()

        val request = OneTimeWorkRequestBuilder<SummaryWorker>()
            .setInputData(inputData)
            .setInitialDelay(nextDelay, TimeUnit.MILLISECONDS)
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                15,
                TimeUnit.MINUTES
            )
            .addTag(getTag(hour, minute))
            .build()

        workManager.enqueueUniqueWork(
            workName,
            ExistingWorkPolicy.REPLACE,
            request
        )
        Log.i(TAG, "Đã tự động tái lập lịch ca tiếp theo [${formatShiftTag(hour, minute)}] sau ${nextDelay}ms")
    }

    /**
     * Cancels a specific scheduled summary worker when removed by the user.
     */
    fun cancelScheduledHour(context: Context, hourStr: String) {
        val parts = hourStr.split(":")
        if (parts.size == 2) {
            val hour = parts[0].toIntOrNull() ?: return
            val minute = parts[1].toIntOrNull() ?: return
            WorkManager.getInstance(context).cancelUniqueWork(getWorkName(hour, minute))
        }
    }

    /**
     * Triggers an immediate one-time summary work request
     * for manual testing and instant report generation.
     */
    fun triggerImmediateSummary(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<SummaryWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                15,
                TimeUnit.MINUTES
            )
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            MANUAL_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }
}
