package com.orca.orcaauto.worker

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.Calendar
import java.util.concurrent.TimeUnit

object WorkScheduler {

    private const val WORK_12PM_NAME = "OrcaAuto_Summary_12PM"
    private const val WORK_18PM_NAME = "OrcaAuto_Summary_18PM"
    private const val WORK_LIFECYCLE_NAME = "OrcaAuto_Data_Lifecycle"
    const val MANUAL_WORK_NAME = "OrcaAuto_Manual_Summary"

    /**
     * Schedules daily summary tasks dynamically based on user-configured hours (UserPreferences),
     * and daily data lifecycle worker (cleanup + daily memory rollup) at 23:50.
     */
    fun scheduleDailySummaries(context: Context) {
        val workManager = WorkManager.getInstance(context)
        val preferences = com.orca.orcaauto.data.preferences.UserPreferences.getInstance(context)
        val hoursList = (preferences.getScheduledHours() + preferences.loadSessions().filter { it.isScheduled }.flatMap { it.scheduledHours }).distinct().sorted()

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        // 1. Dynamically schedule for each user-defined hour
        for (timeStr in hoursList) {
            val parts = timeStr.split(":")
            if (parts.size == 2) {
                val hour = parts[0].toIntOrNull() ?: continue
                val minute = parts[1].toIntOrNull() ?: continue
                val workName = "OrcaAuto_Summary_${hour}h${minute}"

                val initialDelay = calculateInitialDelay(hour, minute)
                val request = PeriodicWorkRequestBuilder<SummaryWorker>(24, TimeUnit.HOURS)
                    .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
                    .setConstraints(constraints)
                    .setBackoffCriteria(
                        BackoffPolicy.EXPONENTIAL,
                        15,
                        TimeUnit.MINUTES
                    )
                    .build()

                workManager.enqueueUniquePeriodicWork(
                    workName,
                    ExistingPeriodicWorkPolicy.UPDATE,
                    request
                )
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
     * Cancels a specific scheduled summary worker when removed by the user.
     */
    fun cancelScheduledHour(context: Context, hourStr: String) {
        val parts = hourStr.split(":")
        if (parts.size == 2) {
            val hour = parts[0].toIntOrNull() ?: return
            val minute = parts[1].toIntOrNull() ?: return
            WorkManager.getInstance(context).cancelUniqueWork("OrcaAuto_Summary_${hour}h${minute}")
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

    private fun calculateInitialDelay(targetHour: Int, targetMinute: Int): Long {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, targetHour)
            set(Calendar.MINUTE, targetMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        if (target.before(now)) {
            target.add(Calendar.DAY_OF_YEAR, 1)
        }

        return target.timeInMillis - now.timeInMillis
    }
}
