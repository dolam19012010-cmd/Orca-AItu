package com.orca.orcaauto.worker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/**
 * Unit tests verifying wall-clock anchoring and cumulative drift recovery in [WorkScheduler].
 */
class WorkSchedulerTimeCalculationTest {

    @Test
    fun calculateInitialDelay_whenBeforeTargetTime_calculatesExactDelayOnSameDay() {
        val calendar = Calendar.getInstance().apply {
            set(2026, Calendar.MAY, 10, 9, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val nowMillis = calendar.timeInMillis

        // Target: 12:00 PM same day -> exactly 3 hours delay
        val delay = WorkScheduler.calculateInitialDelay(12, 0, nowMillis)
        val expectedDelay = 3 * 3600 * 1000L

        assertEquals(expectedDelay, delay)

        val triggerTime = Calendar.getInstance().apply { timeInMillis = nowMillis + delay }
        assertEquals(2026, triggerTime.get(Calendar.YEAR))
        assertEquals(Calendar.MAY, triggerTime.get(Calendar.MONTH))
        assertEquals(10, triggerTime.get(Calendar.DAY_OF_MONTH))
        assertEquals(12, triggerTime.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, triggerTime.get(Calendar.MINUTE))
        assertEquals(0, triggerTime.get(Calendar.SECOND))
        assertEquals(0, triggerTime.get(Calendar.MILLISECOND))
    }

    @Test
    fun calculateInitialDelay_whenExactlyAtTargetTime_schedulesForNextDay() {
        val calendar = Calendar.getInstance().apply {
            set(2026, Calendar.MAY, 10, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val nowMillis = calendar.timeInMillis

        // At exactly 12:00:00.000, next occurrence is tomorrow at 12:00:00.000 (24h)
        val delay = WorkScheduler.calculateInitialDelay(12, 0, nowMillis)
        val expectedDelay = 24 * 3600 * 1000L

        assertEquals(expectedDelay, delay)

        val triggerTime = Calendar.getInstance().apply { timeInMillis = nowMillis + delay }
        assertEquals(11, triggerTime.get(Calendar.DAY_OF_MONTH))
        assertEquals(12, triggerTime.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, triggerTime.get(Calendar.MINUTE))
        assertEquals(0, triggerTime.get(Calendar.SECOND))
    }

    @Test
    fun calculateInitialDelay_whenAfterTargetTime_schedulesForTomorrow() {
        val calendar = Calendar.getInstance().apply {
            set(2026, Calendar.MAY, 10, 14, 30, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val nowMillis = calendar.timeInMillis

        // 14:30 to next day 12:00 -> 9.5 hours to midnight + 12 hours = 21.5 hours
        val delay = WorkScheduler.calculateInitialDelay(12, 0, nowMillis)
        val expectedDelay = (21 * 3600 + 30 * 60) * 1000L

        assertEquals(expectedDelay, delay)

        val triggerTime = Calendar.getInstance().apply { timeInMillis = nowMillis + delay }
        assertEquals(11, triggerTime.get(Calendar.DAY_OF_MONTH))
        assertEquals(12, triggerTime.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, triggerTime.get(Calendar.MINUTE))
    }

    @Test
    fun driftRecovery_whenDelayedByDozeMode_anchorsExactlyToNominalTargetNextDay() {
        // Nominal shift was 12:00, but Doze Mode delayed execution until 12:47:35.890
        val actualExecutionCal = Calendar.getInstance().apply {
            set(2026, Calendar.JUNE, 15, 12, 47, 35)
            set(Calendar.MILLISECOND, 890)
        }
        val actualExecutionMillis = actualExecutionCal.timeInMillis

        val nextDelay = WorkScheduler.calculateNextOccurrenceDelay(12, 0, actualExecutionMillis)
        val scheduledTriggerTime = actualExecutionMillis + nextDelay

        val targetCal = Calendar.getInstance().apply { timeInMillis = scheduledTriggerTime }
        assertEquals("Target day must be the following day", 16, targetCal.get(Calendar.DAY_OF_MONTH))
        assertEquals("Target hour must remain 12:00", 12, targetCal.get(Calendar.HOUR_OF_DAY))
        assertEquals("Target minute must remain 00", 0, targetCal.get(Calendar.MINUTE))
        assertEquals("Target second must be 00", 0, targetCal.get(Calendar.SECOND))
        assertEquals("Target millisecond must be 000", 0, targetCal.get(Calendar.MILLISECOND))

        // Cumulative drift error must be exactly 0
        val nominalNextDayCal = Calendar.getInstance().apply {
            set(2026, Calendar.JUNE, 16, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        assertEquals(nominalNextDayCal.timeInMillis, scheduledTriggerTime)
    }

    @Test
    fun calculateInitialDelay_acrossMonthAndYearBoundary_anchorsCorrectly() {
        // New Year's Eve: 2026-12-31 at 23:30:00.000
        val calendar = Calendar.getInstance().apply {
            set(2026, Calendar.DECEMBER, 31, 23, 30, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val nowMillis = calendar.timeInMillis

        // Next 12:00 must be on 2027-01-01 at 12:00:00.000 (12 hours 30 mins)
        val delay = WorkScheduler.calculateInitialDelay(12, 0, nowMillis)
        val expectedDelay = (12 * 3600 + 30 * 60) * 1000L

        assertEquals(expectedDelay, delay)

        val triggerTime = Calendar.getInstance().apply { timeInMillis = nowMillis + delay }
        assertEquals(2027, triggerTime.get(Calendar.YEAR))
        assertEquals(Calendar.JANUARY, triggerTime.get(Calendar.MONTH))
        assertEquals(1, triggerTime.get(Calendar.DAY_OF_MONTH))
        assertEquals(12, triggerTime.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, triggerTime.get(Calendar.MINUTE))
        assertEquals(0, triggerTime.get(Calendar.SECOND))
    }

    @Test
    fun shiftTagAndNamingFormatting_formatsStandardPaddedStrings() {
        assertEquals("Ca: 12:00", WorkScheduler.formatShiftTag(12, 0))
        assertEquals("Ca: 08:05", WorkScheduler.formatShiftTag(8, 5))
        assertEquals("12:00", WorkScheduler.formatShiftTime(12, 0))
        assertEquals("08:05", WorkScheduler.formatShiftTime(8, 5))
        assertEquals("OrcaAuto_Summary_12h0", WorkScheduler.getWorkName(12, 0))
        assertEquals("OrcaAuto_Summary_Tag_8h5", WorkScheduler.getTag(8, 5))
    }

    @Test
    fun invarianceUnderVaryingExecutionTimes_anchorsToTargetSecond() {
        val baseCal = Calendar.getInstance().apply {
            set(2026, Calendar.AUGUST, 20, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }

        // Test varying completion times from 12:00:01 to 23:59:59 (e.g. heavy doze / delays)
        for (minuteOffset in listOf(1, 15, 47, 120, 300, 600)) {
            val executionTime = baseCal.timeInMillis + (minuteOffset * 60 * 1000L)
            val delay = WorkScheduler.calculateNextOccurrenceDelay(12, 0, executionTime)
            val trigger = Calendar.getInstance().apply { timeInMillis = executionTime + delay }

            assertEquals(21, trigger.get(Calendar.DAY_OF_MONTH))
            assertEquals(12, trigger.get(Calendar.HOUR_OF_DAY))
            assertEquals(0, trigger.get(Calendar.MINUTE))
            assertEquals(0, trigger.get(Calendar.SECOND))
            assertEquals(0, trigger.get(Calendar.MILLISECOND))
        }
    }
}
