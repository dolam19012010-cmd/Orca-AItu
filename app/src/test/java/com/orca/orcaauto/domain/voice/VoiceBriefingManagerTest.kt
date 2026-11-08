package com.orca.orcaauto.domain.voice

import com.orca.orcaauto.domain.filter.ParsedSummary
import com.orca.orcaauto.domain.filter.TaskItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceBriefingManagerTest {

    @Test
    fun testBuildSpokenScript_withAdviceAndTasks() {
        val parsed = ParsedSummary(
            rawCleanText = "Nội dung tóm tắt gốc",
            conciseAdvice = "Nhớ kiểm tra báo cáo cuối ngày.",
            tasksToDo = listOf(
                TaskItem(title = "Gửi email cho sếp", deadline = "17:00 hôm nay")
            ),
            tasksDone = emptyList(),
            tasksPlanned = listOf(
                TaskItem(title = "Họp tuần với team", deadline = "09:00 sáng mai")
            ),
            criticalQuestions = listOf("Đã chốt số liệu ngân sách chưa?")
        )

        val script = VoiceBriefingManager.buildSpokenScript(parsed)

        assertTrue(script.contains("Lời khuyên dành cho bạn: Nhớ kiểm tra báo cáo cuối ngày."))
        assertTrue(script.contains("Các nhiệm vụ cần thực hiện."))
        assertTrue(script.contains("Nhiệm vụ 1: Gửi email cho sếp. Hạn chót là 17:00 hôm nay."))
        assertTrue(script.contains("Nhiệm vụ 2: Họp tuần với team. Hạn chót là 09:00 sáng mai."))
        assertTrue(script.contains("Lưu ý quan trọng: Đã chốt số liệu ngân sách chưa?"))
    }

    @Test
    fun testCleanMarkdown() {
        val raw = "**Lưu ý** [ ] `quan trọng`: *xong việc* trước 12h."
        val cleaned = VoiceBriefingManager.cleanMarkdown(raw)
        assertEquals("Lưu ý quan trọng: xong việc trước 12h.", cleaned)
    }
}
