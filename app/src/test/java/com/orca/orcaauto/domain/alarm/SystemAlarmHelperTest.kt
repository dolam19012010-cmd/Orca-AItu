package com.orca.orcaauto.domain.alarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemAlarmHelperTest {

    @Test
    fun testParseStandardColonFormats() {
        val t1 = SystemAlarmHelper.parseTimeFromText("Hạn: 15:30 chiều nay")
        assertNotNull(t1)
        assertEquals(15, t1?.first)
        assertEquals(30, t1?.second)

        val t2 = SystemAlarmHelper.parseTimeFromText("09:05 sáng")
        assertNotNull(t2)
        assertEquals(9, t2?.first)
        assertEquals(5, t2?.second)
    }

    @Test
    fun testParseHNotationAndAmPm() {
        val t1 = SystemAlarmHelper.parseTimeFromText("Lịch hẹn 16h")
        assertNotNull(t1)
        assertEquals(16, t1?.first)
        assertEquals(0, t1?.second)

        val t2 = SystemAlarmHelper.parseTimeFromText("Họp lúc 16h45")
        assertNotNull(t2)
        assertEquals(16, t2?.first)
        assertEquals(45, t2?.second)

        val t3 = SystemAlarmHelper.parseTimeFromText("Giao hàng 3h chiều")
        assertNotNull(t3)
        assertEquals(15, t3?.first)
        assertEquals(0, t3?.second)

        val t4 = SystemAlarmHelper.parseTimeFromText("Check mail 8h sáng")
        assertNotNull(t4)
        assertEquals(8, t4?.first)
        assertEquals(0, t4?.second)
    }

    @Test
    fun testParseNaturalLanguageShortcuts() {
        val t1 = SystemAlarmHelper.parseTimeFromText("Cần gửi xong chiều nay")
        assertNotNull(t1)
        assertEquals(17, t1?.first)
        assertEquals(0, t1?.second)

        val t2 = SystemAlarmHelper.parseTimeFromText("Nhắc xử lý sáng mai")
        assertNotNull(t2)
        assertEquals(9, t2?.first)
        assertEquals(0, t2?.second)

        val t3 = SystemAlarmHelper.parseTimeFromText("Không có giờ nào")
        assertNull(t3)
    }

    @Test
    fun testCalculateAlarmSchedulesCascadeX1() {
        val schedules = SystemAlarmHelper.calculateAlarmSchedules(
            taskTitle = "Gửi hợp đồng cho anh Tuấn",
            targetHour = 15,
            targetMinute = 30,
            mode = AlarmCascadeMode.X1
        )
        assertEquals(1, schedules.size)
        assertEquals(15, schedules[0].hour)
        assertEquals(30, schedules[0].minute)
        assertTrue(schedules[0].isPrimary)
        assertTrue(schedules[0].label.contains("ĐẾN HẠN"))
    }

    @Test
    fun testCalculateAlarmSchedulesCascadeX2() {
        val schedules = SystemAlarmHelper.calculateAlarmSchedules(
            taskTitle = "Đăng kiểm xe ô tô",
            targetHour = 15,
            targetMinute = 30,
            mode = AlarmCascadeMode.X2
        )
        assertEquals(2, schedules.size)
        // 1st alarm: 15:15 (15 mins prior)
        assertEquals(15, schedules[0].hour)
        assertEquals(15, schedules[0].minute)
        assertEquals(15, schedules[0].offsetMinutes)
        assertTrue(schedules[0].label.contains("Chuẩn bị (15p)"))

        // 2nd alarm: 15:30 (on time)
        assertEquals(15, schedules[1].hour)
        assertEquals(30, schedules[1].minute)
        assertEquals(0, schedules[1].offsetMinutes)
        assertTrue(schedules[1].isPrimary)
        assertTrue(schedules[1].label.contains("ĐẾN HẠN"))
    }

    @Test
    fun testCalculateAlarmSchedulesCascadeX3() {
        val schedules = SystemAlarmHelper.calculateAlarmSchedules(
            taskTitle = "Chốt cọc căn hộ",
            targetHour = 14,
            targetMinute = 0,
            mode = AlarmCascadeMode.X3
        )
        assertEquals(3, schedules.size)
        // 1st alarm: 13:30 (30 mins prior)
        assertEquals(13, schedules[0].hour)
        assertEquals(30, schedules[0].minute)

        // 2nd alarm: 13:45 (15 mins prior)
        assertEquals(13, schedules[1].hour)
        assertEquals(45, schedules[1].minute)

        // 3rd alarm: 14:00 (primary)
        assertEquals(14, schedules[2].hour)
        assertEquals(0, schedules[2].minute)
        assertTrue(schedules[2].isPrimary)
    }

    @Test
    fun testMidnightWrapAroundCalculation() {
        // Task at 00:10 midnight, with X2 (15 mins prior -> 23:55 previous day)
        val schedules = SystemAlarmHelper.calculateAlarmSchedules(
            taskTitle = "Gửi báo cáo ca đêm",
            targetHour = 0,
            targetMinute = 10,
            mode = AlarmCascadeMode.X2
        )
        assertEquals(2, schedules.size)
        // Earliest ring: 23:55
        assertEquals(23, schedules[0].hour)
        assertEquals(55, schedules[0].minute)
        assertEquals(15, schedules[0].offsetMinutes)

        // Primary ring: 00:10
        assertEquals(0, schedules[1].hour)
        assertEquals(10, schedules[1].minute)
        assertEquals(0, schedules[1].offsetMinutes)
    }

    @Test
    fun testParseDateTimeRelativeTomorrowAndDayAfterTomorrow() {
        val base = java.util.Calendar.getInstance().apply {
            set(2026, java.util.Calendar.OCTOBER, 30, 10, 0, 0)
        }

        // 1. Ngày mai
        val dtTomorrow = SystemAlarmHelper.parseDateTimeFromText("15:30 ngày mai", base)
        assertNotNull(dtTomorrow)
        assertEquals(15, dtTomorrow?.calendar?.get(java.util.Calendar.HOUR_OF_DAY))
        assertEquals(30, dtTomorrow?.calendar?.get(java.util.Calendar.MINUTE))
        assertEquals(31, dtTomorrow?.calendar?.get(java.util.Calendar.DAY_OF_MONTH))
        assertEquals(java.util.Calendar.OCTOBER, dtTomorrow?.calendar?.get(java.util.Calendar.MONTH))

        // 2. Ngày kia (> 28h => isFarFuture = true)
        val dtDayAfter = SystemAlarmHelper.parseDateTimeFromText("Hạn 10h ngày kia", base)
        assertNotNull(dtDayAfter)
        assertEquals(10, dtDayAfter?.calendar?.get(java.util.Calendar.HOUR_OF_DAY))
        assertEquals(0, dtDayAfter?.calendar?.get(java.util.Calendar.MINUTE))
        assertEquals(1, dtDayAfter?.calendar?.get(java.util.Calendar.DAY_OF_MONTH))
        assertEquals(java.util.Calendar.NOVEMBER, dtDayAfter?.calendar?.get(java.util.Calendar.MONTH))
        assertTrue(dtDayAfter?.isFarFuture == true)
        assertTrue(dtDayAfter?.dateLabel?.contains("Ngày kia") == true)
    }

    @Test
    fun testParseDateTimeSpecificDateAndNextMonth() {
        val base = java.util.Calendar.getInstance().apply {
            set(2026, java.util.Calendar.OCTOBER, 30, 10, 0, 0)
        }

        // Specific date: 15/11
        val dtSpecific = SystemAlarmHelper.parseDateTimeFromText("Giao hàng lúc 14:00 ngày 15/11", base)
        assertNotNull(dtSpecific)
        assertEquals(14, dtSpecific?.calendar?.get(java.util.Calendar.HOUR_OF_DAY))
        assertEquals(0, dtSpecific?.calendar?.get(java.util.Calendar.MINUTE))
        assertEquals(15, dtSpecific?.calendar?.get(java.util.Calendar.DAY_OF_MONTH))
        assertEquals(java.util.Calendar.NOVEMBER, dtSpecific?.calendar?.get(java.util.Calendar.MONTH))
        assertTrue(dtSpecific?.isFarFuture == true)

        // Tháng sau
        val dtNextMonth = SystemAlarmHelper.parseDateTimeFromText("Họp tổng kết tháng sau lúc 9h sáng", base)
        assertNotNull(dtNextMonth)
        assertEquals(9, dtNextMonth?.calendar?.get(java.util.Calendar.HOUR_OF_DAY))
        assertEquals(java.util.Calendar.NOVEMBER, dtNextMonth?.calendar?.get(java.util.Calendar.MONTH))
        assertTrue(dtNextMonth?.isFarFuture == true)
    }

    @Test
    fun testGenerateDeterministicAlarmId() {
        val cal1 = java.util.Calendar.getInstance().apply {
            set(2026, java.util.Calendar.OCTOBER, 31, 8, 30, 0)
        }
        val cal2 = java.util.Calendar.getInstance().apply {
            set(2026, java.util.Calendar.OCTOBER, 31, 8, 30, 0)
        }

        val id1 = SystemAlarmHelper.generateAlarmId("Họp giao ban sáng mai", cal1)
        val id2 = SystemAlarmHelper.generateAlarmId("  HỌP GIAO BAN SÁNG MAI!  ", cal2)

        // Must start with ALM_ and produce the exact same ID despite case/punctuation differences
        assertTrue(id1.startsWith("ALM_"))
        assertEquals(id1, id2)

        // Different time must produce different ID
        val cal3 = java.util.Calendar.getInstance().apply {
            set(2026, java.util.Calendar.OCTOBER, 31, 9, 30, 0)
        }
        val id3 = SystemAlarmHelper.generateAlarmId("Họp giao ban sáng mai", cal3)
        assertTrue(id1 != id3)
    }
}
