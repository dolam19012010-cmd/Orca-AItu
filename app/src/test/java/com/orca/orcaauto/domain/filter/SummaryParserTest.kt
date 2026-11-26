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

    @Test
    fun testParseEnglishAiOutput() {
        val englishAiOutput = """
            === PART 1: GENTLE REMINDERS & THOUGHTFUL NOTES ===
            1. [Gentle Reminder]: Please review the pull request before our standup at 10:00 AM.
            2. [Key Observation]: Client approved the mockups without requested changes.

            === PART 2: TASK DISPATCH BOARD ===
            ### 🔴 TO-DO TASKS
            - [ ] Review PR #42 | Due: 10:00 | Source: GitHub
            - [ ] Submit weekly report | Deadline: 17:00 | Source: Slack

            ### 🟢 COMPLETED TASKS
            - [x] Merge sprint release branch | Source: Jira

            ### 🟡 PLANNED TASKS
            - [ ] Sprint retro meeting | Time: Friday | Source: Calendar
        """.trimIndent()

        val parsed = SummaryParser.parse(englishAiOutput)

        // Advice
        assertTrue(parsed.conciseAdvice.contains("Please review the pull request"))
        assertTrue(parsed.conciseAdvice.contains("Client approved the mockups"))
        assertTrue(parsed.criticalQuestions.isEmpty())

        // To-Do
        assertEquals(2, parsed.tasksToDo.size)
        assertEquals("Review PR #42", parsed.tasksToDo[0].title)
        assertEquals("10:00", parsed.tasksToDo[0].deadline)
        assertEquals("GitHub", parsed.tasksToDo[0].source)
        assertEquals(TaskStatus.TODO, parsed.tasksToDo[0].status)

        assertEquals("Submit weekly report", parsed.tasksToDo[1].title)
        assertEquals("17:00", parsed.tasksToDo[1].deadline)
        assertEquals("Slack", parsed.tasksToDo[1].source)

        // Done
        assertEquals(1, parsed.tasksDone.size)
        assertEquals("Merge sprint release branch", parsed.tasksDone[0].title)
        assertEquals("Jira", parsed.tasksDone[0].source)
        assertEquals(TaskStatus.DONE, parsed.tasksDone[0].status)

        // Planned
        assertEquals(1, parsed.tasksPlanned.size)
        assertEquals("Sprint retro meeting", parsed.tasksPlanned[0].title)
        assertEquals("Friday", parsed.tasksPlanned[0].deadline)
        assertEquals("Calendar", parsed.tasksPlanned[0].source)
        assertEquals(TaskStatus.PLANNED, parsed.tasksPlanned[0].status)

        assertEquals(4, parsed.totalTaskCount)
    }

    @Test
    fun testTaskStatusGetDisplayName() {
        assertEquals("Phải làm", TaskStatus.TODO.getDisplayName(com.orca.orcaauto.ui.locale.AppLanguage.VIETNAMESE))
        assertEquals("To-Do", TaskStatus.TODO.getDisplayName(com.orca.orcaauto.ui.locale.AppLanguage.ENGLISH))
        assertEquals("Đã làm", TaskStatus.DONE.getDisplayName(com.orca.orcaauto.ui.locale.AppLanguage.VIETNAMESE))
        assertEquals("Done", TaskStatus.DONE.getDisplayName(com.orca.orcaauto.ui.locale.AppLanguage.ENGLISH))
        assertEquals("Kế hoạch", TaskStatus.PLANNED.getDisplayName(com.orca.orcaauto.ui.locale.AppLanguage.VIETNAMESE))
        assertEquals("Planned", TaskStatus.PLANNED.getDisplayName(com.orca.orcaauto.ui.locale.AppLanguage.ENGLISH))
    }

    @Test
    fun testParseTaskWithChatRefIdExtractedAndStrippedFromSource() {
        val sampleAiOutput = """
            PHẦN 1: LỜI KHUYÊN & LƯU Ý
            - Lời khuyên: Tập trung xử lý các việc gấp chiều nay.

            PHẦN 2: BẢNG NHIỆM VỤ ĐIỀU PHỐI
            Nhiệm vụ phải làm (To-Do):
            - [ ] Gửi báo cáo quý | Nguồn: Sếp Hùng [#z4k9m] | Hạn: 17:00
            - [ ] Kiểm tra lỗi server | Nguồn: Team DevOps [#t8a2c] | Hạn: 18:00
            - [ ] Phản hồi khách hàng | Nguồn: Lan Anh [#m1p7x] | Hạn: 19:00
        """.trimIndent()

        val parsed = SummaryParser.parse(sampleAiOutput)

        assertEquals(3, parsed.tasksToDo.size)

        // Item 1: Zalo
        val task1 = parsed.tasksToDo[0]
        assertEquals("Gửi báo cáo quý", task1.title)
        assertEquals("Sếp Hùng", task1.source)
        assertEquals("17:00", task1.deadline)
        assertEquals("z4k9m", task1.chatRefId)
        assertFalse("Source không được chứa raw tag", task1.source.contains("[#z4k9m]"))
        assertFalse("Source không được rò rỉ #z4k9m", task1.source.contains("#z4k9m"))

        // Item 2: Telegram
        val task2 = parsed.tasksToDo[1]
        assertEquals("Kiểm tra lỗi server", task2.title)
        assertEquals("Team DevOps", task2.source)
        assertEquals("t8a2c", task2.chatRefId)

        // Item 3: Messenger
        val task3 = parsed.tasksToDo[2]
        assertEquals("Phản hồi khách hàng", task3.title)
        assertEquals("Lan Anh", task3.source)
        assertEquals("m1p7x", task3.chatRefId)
    }

    @Test
    fun testParseTaskWithoutChatRefIdDefaultsToNull() {
        val sample = """
            PHẦN 1: LỜI KHUYÊN
            - Lời khuyên: Giữ sức khỏe.

            PHẦN 2: BẢNG NHIỆM VỤ ĐIỀU PHỐI
            Nhiệm vụ phải làm (To-Do):
            - [ ] Việc tự do cá nhân | Nguồn: Người dùng | Hạn: 20:00
        """.trimIndent()

        val parsed = SummaryParser.parse(sample)
        assertEquals(1, parsed.tasksToDo.size)
        val task = parsed.tasksToDo[0]
        assertEquals("Việc tự do cá nhân", task.title)
        assertEquals("Người dùng", task.source)
        assertEquals(null, task.chatRefId)
    }

    @Test
    fun testCleanMarkdownRemovesChatRefId() {
        val rawSource = "Nguồn: Sếp Hùng [#z4k9m]"
        val cleaned = SummaryParser.cleanMarkdown(rawSource)
        assertEquals("Nguồn: Sếp Hùng", cleaned)

        val rawAdvice = "Lời khuyên: [#t8a2c] cần gửi hợp đồng sớm"
        val cleanedAdvice = SummaryParser.cleanMarkdown(rawAdvice)
        assertEquals("Lời khuyên: cần gửi hợp đồng sớm", cleanedAdvice)
    }

    @Test
    fun testParseContinuationSubLineWithChatRefId() {
        val sampleWithSublines = """
            PHẦN 1: LỜI KHUYÊN
            - Lời khuyên: Đọc kỹ tài liệu.

            PHẦN 2: BẢNG NHIỆM VỤ ĐIỀU PHỐI
            Nhiệm vụ phải làm (To-Do):
            - [ ] Xem lại tài liệu kỹ thuật
            Nguồn: Trưởng phòng [#m1p7x]
            Hạn: 14:00
        """.trimIndent()

        val parsed = SummaryParser.parse(sampleWithSublines)
        assertEquals(1, parsed.tasksToDo.size)
        val task = parsed.tasksToDo[0]
        assertEquals("Xem lại tài liệu kỹ thuật", task.title)
        assertEquals("Trưởng phòng", task.source)
        assertEquals("14:00", task.deadline)
        assertEquals("m1p7x", task.chatRefId)
    }

    @Test
    fun testExtractLiveAdvicePreview_stripsScratchpadAndStopsBeforePart2() {
        val unclosedThinking = "<scratchpad>Đang suy luận nội bộ..."
        assertEquals("", SummaryParser.extractLiveAdvicePreview(unclosedThinking))

        val part1Only = """
            <scratchpad>Đã lọc 5 tin nhắn</scratchpad>
            === PHẦN 1: LỜI KHUYÊN & LƯU Ý ===
            - [Lời khuyên]: Nên phản hồi khách hàng trước 15:00.
        """.trimIndent()
        val livePart1 = SummaryParser.extractLiveAdvicePreview(part1Only)
        assertFalse(livePart1.contains("<scratchpad>"))
        assertFalse(livePart1.contains("PHẦN 1"))
        assertTrue(livePart1.contains("Nên phản hồi khách hàng trước 15:00."))

        val midPart2Stream = """
            <scratchpad>OK</scratchpad>
            === PHẦN 1: GÓC NHÌN PHẢN BIỆN & LỜI KHUYÊN ===
            Chú ý lịch họp chiều nay lúc 14:00.

            === PHẦN 2: BẢNG ĐIỀU PHỐI NHIỆM VỤ ===
            ### 🔴 NHIỆM VỤ PHẢI LÀM
            - [ ] Gửi báo cáo | Nguồn: Sếp Hùng [#z1a2b] | Hạn: 15:00
        """.trimIndent()
        val livePart2Vi = SummaryParser.extractLiveAdvicePreview(midPart2Stream, isEnglish = false)
        assertTrue(livePart2Vi.contains("Chú ý lịch họp chiều nay lúc 14:00."))
        assertTrue(livePart2Vi.contains("⏳ Đang trích xuất danh sách nhiệm vụ..."))
        assertFalse(livePart2Vi.contains("PHẦN 2"))
        assertFalse(livePart2Vi.contains("[#z1a2b]"))
        assertFalse(livePart2Vi.contains("- [ ]"))

        val livePart2En = SummaryParser.extractLiveAdvicePreview(midPart2Stream, isEnglish = true)
        assertTrue(livePart2En.contains("⏳ Extracting tasks..."))
        assertFalse(livePart2En.contains("[#z1a2b]"))
    }

    @Test
    fun testFormatForClipboard_producesCleanReadableOutputWithoutRawTags() {
        val raw = """
            <scratchpad>Suy luận nội bộ cần ẩn</scratchpad>
            === PHẦN 1: GÓC NHÌN PHẢN BIỆN & LỜI KHUYÊN ===
            - [Nhẹ Nhàng]: Hôm nay ưu tiên chốt hợp đồng và kiểm tra tiến độ server.

            === PHẦN 2: BẢNG ĐIỀU PHỐI NHIỆM VỤ ===
            Nhiệm vụ phải làm (To-Do):
            - [ ] Ký hợp đồng khách hàng A | Nguồn: Sếp Tuấn [#z4k9m] | Hạn: 16:00
            Kế hoạch dự kiến:
            - [ ] Họp tổng kết tuần | Nguồn: Nhóm Dev [#t8a2c] | Hạn: Thứ 6
            Nhiệm vụ đã làm (Done):
            - [x] Gửi bảng báo giá | Nguồn: Lan Anh [#m1p7x]
        """.trimIndent()
        val parsed = SummaryParser.parse(raw)

        val clipVi = SummaryParser.formatForClipboard(parsed, isEnglish = false)
        assertTrue(clipVi.contains("Hôm nay ưu tiên chốt hợp đồng và kiểm tra tiến độ server."))
        assertTrue(clipVi.contains("📌 Việc cần làm"))
        assertTrue(clipVi.contains("- Ký hợp đồng khách hàng A (⏰ 16:00) — Sếp Tuấn"))
        assertTrue(clipVi.contains("🗓 Kế hoạch"))
        assertTrue(clipVi.contains("- Họp tổng kết tuần (⏰ Thứ 6) — Nhóm Dev"))
        assertTrue(clipVi.contains("✅ Đã xong"))
        assertTrue(clipVi.contains("- Gửi bảng báo giá — Lan Anh"))
        assertFalse(clipVi.contains("PHẦN"))
        assertFalse(clipVi.contains("<scratchpad>"))
        assertFalse(clipVi.contains("[#z4k9m]"))
        assertFalse(clipVi.contains("[#t8a2c]"))
        assertFalse(clipVi.contains("[#m1p7x]"))

        val clipEn = SummaryParser.formatForClipboard(parsed, isEnglish = true)
        assertTrue(clipEn.contains("📌 To-Do"))
        assertTrue(clipEn.contains("🗓 Planned"))
        assertTrue(clipEn.contains("✅ Done"))
    }

    @Test
    fun testHasStructuredHeaders_detectsStructuredVsPlainChat() {
        assertFalse(SummaryParser.hasStructuredHeaders("Chào bạn, hôm nay có 2 tin nhắn mới từ Sếp Hùng."))
        assertTrue(
            SummaryParser.hasStructuredHeaders(
                """
                    === PHẦN 1: LỜI KHUYÊN ===
                    Cần kiểm tra email.
                """.trimIndent()
            )
        )
        assertTrue(
            SummaryParser.hasStructuredHeaders(
                """
                    ### TO-DO TASKS
                    - [ ] Reply client | Due: 17:00
                """.trimIndent()
            )
        )
    }
}
