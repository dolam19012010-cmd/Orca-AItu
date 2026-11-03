package com.orca.orcaauto.domain.filter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SummaryParserTest {

    @Test
    fun testCleanMarkdownRemovesSpecialFormatting() {
        val input = "**Quan trọng**: `fix code` __ngay__ và ? kiểm tra [Zalo]"
        val cleaned = SummaryParser.cleanMarkdown(input)
        assertEquals("Quan trọng: fix code ngay và kiểm tra Zalo", cleaned)
    }

    @Test
    fun testParseStandardTwoPartResponse() {
        val sampleAiOutput = """
            PHẦN 1: GÓC NHÌN PHẢN BIỆN & LỜI KHUYÊN
            - Lời khuyên: Cần chốt ngay hợp đồng trước 17:00 chiều nay để tránh trễ hạn giao xe.
            - Điểm hoài nghi: Khách hàng yêu cầu giảm thêm 5% nhưng chưa cam kết thanh toán đợt 1.
            - Mâu thuẫn: Bên vận chuyển báo xe có thể trễ trong khi nhân viên kinh doanh hứa giao trong ngày.
            
            PHẦN 2: BẢNG NHIỆM VỤ ĐIỀU PHỐI
            Nhiệm vụ phải làm (To-Do):
            - [ ] Gửi báo giá cập nhật cho anh Nam | Hạn: 15:30 | Nguồn: Zalo
            - [ ] Xác nhận lịch đăng kiểm xe với đại lý | Hạn: 16:00 | Nguồn: Telegram
            
            Nhiệm vụ đã làm (Done):
            - [x] Đã gửi biên lai đặt cọc cho chị Lan | Nguồn: Messenger
            
            Kế hoạch dự kiến (Planned):
            - [ ] Lên kế hoạch giao lưu cuối tuần với đối tác | Hạn: Thứ 6 | Nguồn: Zalo
        """.trimIndent()

        val parsed = SummaryParser.parse(sampleAiOutput)

        // Verify Section 1
        assertTrue(parsed.conciseAdvice.contains("Cần chốt ngay hợp đồng"))
        assertEquals(2, parsed.criticalQuestions.size)
        assertTrue(parsed.criticalQuestions[0].contains("5%"))
        assertTrue(parsed.criticalQuestions[1].contains("vận chuyển"))

        // Verify Section 2 Tasks
        assertEquals(2, parsed.tasksToDo.size)
        assertEquals("Gửi báo giá cập nhật cho anh Nam", parsed.tasksToDo[0].title)
        assertEquals("15:30", parsed.tasksToDo[0].deadline)
        assertEquals("Zalo", parsed.tasksToDo[0].source)
        assertEquals(TaskStatus.TODO, parsed.tasksToDo[0].status)

        assertEquals("Xác nhận lịch đăng kiểm xe với đại lý", parsed.tasksToDo[1].title)
        assertEquals("16:00", parsed.tasksToDo[1].deadline)
        assertEquals("Telegram", parsed.tasksToDo[1].source)

        assertEquals(1, parsed.tasksDone.size)
        assertEquals("Đã gửi biên lai đặt cọc cho chị Lan", parsed.tasksDone[0].title)
        assertEquals("Messenger", parsed.tasksDone[0].source)
        assertEquals(TaskStatus.DONE, parsed.tasksDone[0].status)

        assertEquals(1, parsed.tasksPlanned.size)
        assertEquals("Lên kế hoạch giao lưu cuối tuần với đối tác", parsed.tasksPlanned[0].title)
        assertEquals("Thứ 6", parsed.tasksPlanned[0].deadline)
        assertEquals(TaskStatus.PLANNED, parsed.tasksPlanned[0].status)

        assertEquals(4, parsed.totalTaskCount)
    }

    @Test
    fun testParseUnstructuredFallbackText() {
        val unformattedOutput = """
            Hôm nay tiến độ dự án có một số vấn đề cần lưu ý.
            Khách hàng phản hồi chất lượng tốt.
            Cần rà soát lại hợp đồng vào ngày mai.
        """.trimIndent()

        val parsed = SummaryParser.parse(unformattedOutput)

        // When headers are missing, advice should contain the text
        assertTrue(parsed.conciseAdvice.isNotBlank())
        assertTrue(parsed.conciseAdvice.contains("tiến độ dự án"))
    }

    @Test
    fun testStripReasoningAndAnswerTags() {
        val inputWithThinkAndAnswer = """
            <think>
            Tôi cần phân tích các tin nhắn và lập bảng việc.
            Người dùng muốn nộp bài tập.
            </think>
            <answer>
            PHẦN 1: GÓC NHÌN PHẢN BIỆN & LỜI KHUYÊN
            - Lời khuyên: Hãy nộp bài sớm trước 17:00?
            
            PHẦN 2: BẢNG NHIỆM VỤ ĐIỀU PHỐI
            Nhiệm vụ phải làm (To-Do):
            - [ ] Nộp bài tập về nhà buổi 19 | Hạn: 17:00 | Nguồn: Thầy giáo
            - [ ] Nộp bài tập về nhà buổi 19 | Hạn: 17:00 | Nguồn: Thầy giáo
            </answer>
        """.trimIndent()

        val parsed = SummaryParser.parse(inputWithThinkAndAnswer)
        assertFalse(parsed.rawCleanText.contains("<think>"))
        assertFalse(parsed.rawCleanText.contains("</think>"))
        assertFalse(parsed.rawCleanText.contains("<answer>"))
        assertFalse(parsed.rawCleanText.contains("</answer>"))
        assertTrue(parsed.conciseAdvice.contains("nộp bài sớm"))
        // Deduplicated
        assertEquals(1, parsed.tasksToDo.size)
        assertEquals("Nộp bài tập về nhà buổi 19", parsed.tasksToDo[0].title)
    }
}
