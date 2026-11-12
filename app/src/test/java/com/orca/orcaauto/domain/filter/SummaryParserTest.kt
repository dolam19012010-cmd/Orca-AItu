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
        assertTrue(parsed.conciseAdvice.contains("5%"))
        assertTrue(parsed.conciseAdvice.contains("vận chuyển"))
        assertTrue(parsed.criticalQuestions.isEmpty())

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

    @Test
    fun testStripScratchpadAndExtractReasoning() {
        val inputWithScratchpad = """
            <scratchpad>
            1. Neo thời gian: Hiện tại là 20:30 ngày 04/11/2026.
            2. Lọc nhiễu: Tin nhắn từ mẹ bảo mua sữa lúc 21h là việc thật.
            3. Đề xuất báo thức: 20:45.
            </scratchpad>
            === PHẦN 1: LỜI NHẮN NHẸ NHÀNG & LƯU Ý TINH TẾ ===
            1. [Lời Nhắn Nhẹ Nhàng]: Nhớ ghé siêu thị mua sữa cho mẹ nhé.
            2. [Lưu Ý Tinh Tế]: Siêu thị đóng cửa lúc 21:30.

            === PHẦN 2: BẢNG ĐIỀU PHỐI NHIỆM VỤ ===
            ### 🔴 NHIỆM VỤ PHẢI LÀM (TO-DO)
            - Mua sữa cho mẹ | Nguồn: Mẹ | Hạn: 21:00
        """.trimIndent()

        val parsed = SummaryParser.parse(inputWithScratchpad)
        assertFalse(parsed.rawCleanText.contains("<scratchpad>"))
        assertFalse(parsed.rawCleanText.contains("</scratchpad>"))
        assertFalse(parsed.rawCleanText.contains("Neo thời gian"))
        assertTrue(parsed.conciseAdvice.contains("mua sữa cho mẹ"))
        assertEquals(1, parsed.tasksToDo.size)
        assertEquals("Mua sữa cho mẹ", parsed.tasksToDo[0].title)
        assertEquals("21:00", parsed.tasksToDo[0].deadline)
        assertEquals("Mẹ", parsed.tasksToDo[0].source)
        // Verify reasoning was extracted into reasoningNotes
        assertTrue(parsed.reasoningNotes?.contains("Neo thời gian") == true)
        assertTrue(parsed.reasoningNotes?.contains("Hiện tại là 20:30") == true)
    }

    @Test
    fun testBulletPointsInAdviceRemainInConciseAdviceAndNotCriticalQuestions() {
        val sampleAiOutput = """
            === PHẦN 1: LỜI KHUYÊN & LƯU Ý ===
            - Cần chuẩn bị trang thiết bị đầy đủ trước khi xuất phát.
            * Kiểm tra kỹ thẻ nhớ và pin dự phòng.
            • Giữ liên lạc liên tục với đội ngũ hậu cần.

            === PHẦN 2: BẢNG ĐIỀU PHỐI NHIỆM VỤ ===
            ### 🔴 NHIỆM VỤ PHẢI LÀM (TO-DO)
            - Mua thêm pin dự phòng | Hạn: 14:00 | Nguồn: Trưởng nhóm
        """.trimIndent()

        val parsed = SummaryParser.parse(sampleAiOutput)

        assertTrue(parsed.conciseAdvice.contains("chuẩn bị trang thiết bị"))
        assertTrue(parsed.conciseAdvice.contains("thẻ nhớ và pin dự phòng"))
        assertTrue(parsed.conciseAdvice.contains("đội ngũ hậu cần"))
        assertTrue(parsed.criticalQuestions.isEmpty())
        assertEquals(1, parsed.tasksToDo.size)
        assertEquals("Mua thêm pin dự phòng", parsed.tasksToDo[0].title)
    }

    @Test
    fun testNumberedListInAdviceRemainInConciseAdvice() {
        val sampleAiOutput = """
            PHẦN 1: HƯỚNG DẪN CHI TIẾT
            1. Bước 1: Khởi động máy ảnh và cài đặt ISO 800.
            2. Bước 2: Chọn tiêu cự phù hợp từ 70mm đến 200mm.
            3. Bước 3: Đảm bảo tốc độ màn trập trên 1/1000s.

            PHẦN 2: BẢNG NHIỆM VỤ
            Nhiệm vụ phải làm (To-Do):
            - Chuẩn bị ống kính tele | Hạn: 08:00 | Nguồn: Ghi chú
        """.trimIndent()

        val parsed = SummaryParser.parse(sampleAiOutput)

        assertTrue(parsed.conciseAdvice.contains("1. Bước 1: Khởi động máy ảnh"))
        assertTrue(parsed.conciseAdvice.contains("2. Bước 2: Chọn tiêu cự"))
        assertTrue(parsed.conciseAdvice.contains("3. Bước 3: Đảm bảo tốc độ"))
        assertTrue(parsed.criticalQuestions.isEmpty())
        assertEquals(1, parsed.tasksToDo.size)
    }

    @Test
    fun testFreeformAnswerWithoutTaskSection() {
        val freeformAiOutput = """
            Kinh nghiệm thực tế khi đi quay chụp bóng đá:
            - Chọn vị trí chụp dọc đường biên ngang khung thành.
            - Luôn di chuyển máy theo hướng bóng lăn.
            - Chú ý những pha va chạm hoặc cảm xúc cầu thủ.
        """.trimIndent()

        val parsed = SummaryParser.parse(freeformAiOutput)

        assertTrue(parsed.conciseAdvice.contains("Kinh nghiệm thực tế"))
        assertTrue(parsed.conciseAdvice.contains("đường biên ngang khung thành"))
        assertTrue(parsed.conciseAdvice.contains("theo hướng bóng lăn"))
        assertTrue(parsed.criticalQuestions.isEmpty())
        assertEquals(0, parsed.totalTaskCount)
        assertTrue(parsed.tasksToDo.isEmpty())
        assertTrue(parsed.tasksDone.isEmpty())
        assertTrue(parsed.tasksPlanned.isEmpty())
    }
}
