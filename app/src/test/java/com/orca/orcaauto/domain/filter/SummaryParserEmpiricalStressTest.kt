package com.orca.orcaauto.domain.filter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Empirical Adversarial & Stress Tests for [SummaryParser]
 * Focus:
 * 1. Multi-line advice with nested bullets, numbered lists (1., 2., 3., 10.), various symbols (*, -, •).
 * 2. Long Vietnamese text preservation.
 * 3. Invariant: criticalQuestions is ALWAYS empty under all circumstances.
 * 4. Empirical verification of bugs in character regex range and section hijacking.
 */
class SummaryParserEmpiricalStressTest {

    @Test
    fun testCriticalQuestionsIsAlwaysEmptyAcrossDiverseInputs() {
        val testInputs = listOf(
            """
            PHẦN 1: GÓC NHÌN PHẢN BIỆN & LỜI KHUYÊN
            - Có nên kiểm tra lại hợp đồng trước 17h?
            - Tại sao đối tác chưa gửi phụ lục hợp đồng?
            ? Liệu tiến độ có bị chậm không?
            * Làm thế nào để giải quyết vấn đề thanh toán?
            • Ai sẽ chịu trách nhiệm chính?
            1. Bạn có đồng ý với điều khoản mới không?
            
            PHẦN 2: BẢNG NHIỆM VỤ ĐIỀU PHỐI
            Nhiệm vụ phải làm (To-Do):
            - [ ] Rà soát hợp đồng | Hạn: 16:00
            """.trimIndent(),

            """
            1. [Lời Nhắn Nhẹ Nhàng]: Bạn đã kiểm tra tài khoản chưa?
            2. [Lưu Ý Tinh Tế]: Liệu chúng ta có nên gọi điện cho đối tác ngay bây giờ?
            """.trimIndent(),

            """
            - Hỏi 1?
            - Hỏi 2?
            - Hỏi 3?
            """.trimIndent(),

            """
            ??? Câu hỏi dồn dập ???
            - Câu 1: Tại sao?
            - Câu 2: Khi nào?
            """.trimIndent()
        )

        for ((index, input) in testInputs.withIndex()) {
            val parsed = SummaryParser.parse(input)
            assertTrue(
                "Input #$index must have empty criticalQuestions, but had: ${parsed.criticalQuestions}",
                parsed.criticalQuestions.isEmpty()
            )
        }
    }

    @Test
    fun testMultiLineAdviceWithVariousBulletSymbolsAndNumberedLists() {
        val rawAi = """
            PHẦN 1: LỜI KHUYÊN & LƯU Ý
            - Lưu ý đầu tiên: Chuẩn bị đầy đủ 2 máy ảnh thân máy full-frame và ít nhất 3 ống kính.
            * Lưu ý thứ hai: Sử dụng thẻ nhớ tốc độ cao chuẩn UHS-II để chụp liên tục 12 fps.
            • Lưu ý thứ ba: Đeo áo bib phóng viên và đứng đúng khu vực quy định ban tổ chức.
            1. Bước 1: Khởi động máy và định dạng lại thẻ nhớ trước trận đấu 30 phút.
            2. Bước 2: Đo sáng vào khuôn mặt cầu thủ trong điều kiện ánh sáng dàn đèn sân.
            3. Bước 3: Đặt chế độ chụp liên tục tốc độ cao và bám nét theo chủ thể di động.
            10. Bước 10: Sao lưu ngay toàn bộ ảnh vào ổ cứng di động ngay sau khi tiếng còi mãn cuộc vang lên.
            
            PHẦN 2: BẢNG NHIỆM VỤ ĐIỀU PHỐI
            Nhiệm vụ phải làm (To-Do):
            - [ ] Mua thêm 2 cục pin chính hãng | Hạn: 10:00 | Nguồn: Ghi chú
        """.trimIndent()

        val parsed = SummaryParser.parse(rawAi)

        // 1. Invariant: criticalQuestions must be empty
        assertTrue(parsed.criticalQuestions.isEmpty())

        // 2. Advice must contain all bullet markers and items completely
        val advice = parsed.conciseAdvice
        assertTrue("Must contain first bullet item", advice.contains("Chuẩn bị đầy đủ 2 máy ảnh"))
        assertTrue("Must contain second bullet item (*)", advice.contains("thẻ nhớ tốc độ cao chuẩn UHS-II"))
        assertTrue("Must contain third bullet item (•)", advice.contains("Đeo áo bib phóng viên"))
        assertTrue("Must contain step 1", advice.contains("1. Bước 1: Khởi động máy"))
        assertTrue("Must contain step 2", advice.contains("2. Bước 2: Đo sáng"))
        assertTrue("Must contain step 3", advice.contains("3. Bước 3: Đặt chế độ chụp liên tục"))
        assertTrue("Must contain step 10", advice.contains("10. Bước 10: Sao lưu ngay toàn bộ"))

        // 3. Task section correctly separated
        assertEquals(1, parsed.tasksToDo.size)
        assertEquals("Mua thêm 2 cục pin chính hãng", parsed.tasksToDo[0].title)
    }

    @Test
    fun testLongVietnameseAdviceTextWithSpecialDiacriticsAndFormatting() {
        val longText = """
            PHẦN 1: GÓC NHÌN CHUYÊN GIA VÀ LỜI KHUYÊN SÂU SẮC
            Khi tác nghiệp quay chụp bóng đá trên sân cỏ nhân tạo hoặc sân vận động quốc gia:
            - Điều kiện thời tiết: Trời mưa phùn gió bấc ở miền Bắc rất dễ làm đọng sương trên kính lọc filter. Bạn phải bọc túi chống mưa chuyên dụng cho ống tele 70-200mm f/2.8.
            - Kỹ thuật bắt nét: Sử dụng hệ thống lấy nét 3D-tracking hoặc Animal/Human Eye AF để bám theo chuyển động rê dắt bóng tốc độ cao của tiền đạo cánh.
            - Tinh thần đồng đội: Luôn phối hợp nhịp nhàng với bình luận viên và đạo diễn hình ảnh để không bỏ lỡ các pha bóng tranh chấp nhạy cảm trong vòng cấm địa.
            - Hậu cần & An toàn: Giữ gìn tư trang, ba lô máy ảnh cẩn thận và uống đủ nước điện giải suốt 90 phút thi đấu căng thẳng.
            
            PHẦN 2: BẢNG NHIỆM VỤ
            Nhiệm vụ phải làm (To-Do):
            - Sạc đầy pin máy ảnh | Hạn: 07:00
            - Lau cảm biến máy ảnh | Hạn: 07:30
        """.trimIndent()

        val parsed = SummaryParser.parse(longText)

        assertTrue(parsed.criticalQuestions.isEmpty())
        val advice = parsed.conciseAdvice

        assertTrue(advice.contains("Khi tác nghiệp quay chụp bóng đá"))
        assertTrue(advice.contains("Trời mưa phùn gió bấc"))
        assertTrue(advice.contains("túi chống mưa chuyên dụng cho ống tele 70-200mm f/2.8"))
        assertTrue(advice.contains("lấy nét 3D-tracking hoặc Animal/Human Eye AF"))
        assertTrue(advice.contains("bình luận viên và đạo diễn hình ảnh"))
        assertTrue(advice.contains("uống đủ nước điện giải suốt 90 phút thi đấu"))

        assertEquals(2, parsed.tasksToDo.size)
    }

    /**
     * EMPIRICAL FINDING 1:
     * Due to `[\\-•*]` in `Regex("""^([\\-•*]\s*|\d+\.\s*)""")`, the pattern forms a character
     * range from ASCII 92 (`\`) to Unicode 8226 (`•`).
     * Consequently, any advice line beginning with a lowercase letter (e.g. 'b' in "bạn nên...")
     * has its first character mistakenly matched as a bullet prefix, causing the first word
     * to be corrupted (e.g. "bạn" -> "b ạn").
     */
    @Test
    fun testEmpiricalBug_LeadingLowercaseLetterChoppedDueToUnescapedRegexRange() {
        val line = "bạn nên kiểm tra lại thiết bị trước khi đi làm."
        val cleaned = SummaryParser.cleanAdviceLine(line)

        // The bug causes "bạn" to become "b ạn"
        assertEquals(
            "EMPIRICAL PROOF OF BUG: 'bạn' is corrupted into 'b ạn'",
            "b ạn nên kiểm tra lại thiết bị trước khi đi làm.",
            cleaned
        )
        // Confirm that it fails to preserve the clean original line
        assertNotEquals(line, cleaned)
    }

    /**
     * EMPIRICAL FINDING 2:
     * In `SummaryParser.parse()`, line 180:
     * `lower.contains("nhiệm vụ phải làm") || (lower.contains("to-do") && !isBullet)`
     * and line 188:
     * `lower.contains("kế hoạch dự kiến") || (lower.contains("planned") && !isBullet)`
     * DO NOT check `!isBullet` for the Vietnamese phrases!
     * If an advice bullet point in Section 1 mentions "nhiệm vụ phải làm" or "kế hoạch dự kiến",
     * it prematurely triggers section transition, drops the advice line, and hijacks all
     * remaining advice into tasks!
     */
    @Test
    fun testEmpiricalBug_AdviceBulletsHijackedIntoTasksWhenContainingSectionKeywords() {
        val rawAi = """
            PHẦN 1: LỜI KHUYÊN CHIẾN LƯỢC
            - Kế hoạch dự kiến cần linh hoạt thay đổi nếu thời tiết có mưa lớn đột ngột.
            - Hãy luôn nhớ kiểm tra góc nhìn trước trận đấu.
            
            PHẦN 2: BẢNG NHIỆM VỤ ĐIỀU PHỐI
            Nhiệm vụ phải làm (To-Do):
            - [ ] Mang theo áo mưa chuyên dụng | Hạn: 13:00 | Nguồn: Nhóm trưởng
        """.trimIndent()

        val parsed = SummaryParser.parse(rawAi)

        // PROOF OF BUG:
        // The bullet "- Kế hoạch dự kiến cần linh hoạt..." triggered section 4 (PLANNED tasks)
        // causing conciseAdvice to LOSE that bullet and subsequent advice!
        assertFalse(
            "EMPIRICAL PROOF OF BUG: conciseAdvice lost the advice bullet because it was hijacked into Section 4",
            parsed.conciseAdvice.contains("Kế hoạch dự kiến cần linh hoạt")
        )
        // And the subsequent bullet "- Hãy luôn nhớ kiểm tra góc nhìn trước trận đấu" was converted into a planned task!
        assertTrue(
            "EMPIRICAL PROOF OF BUG: Advice bullet was misclassified as a planned task",
            parsed.tasksPlanned.any { it.title.contains("Hãy luôn nhớ kiểm tra góc nhìn") }
        )
    }

    @Test
    fun testNestedAndIndentedBulletsInAdvice() {
        val rawAi = """
            PHẦN 1: HƯỚNG DẪN CẤU HÌNH
              - Cấu hình máy ảnh chính:
                * Tốc độ: 1/1250s
                * Khẩu độ: f/2.8
                * ISO: Auto (giới hạn 3200)
              - Cấu hình góc máy phụ sau khung thành:
                • Sử dụng chân monopod vững chắc
                • Ống kính góc rộng 16-35mm
        """.trimIndent()

        val parsed = SummaryParser.parse(rawAi)

        assertTrue(parsed.criticalQuestions.isEmpty())
        val advice = parsed.conciseAdvice
        assertTrue(advice.contains("Cấu hình máy ảnh chính"))
        assertTrue(advice.contains("Tốc độ: 1/1250s"))
        assertTrue(advice.contains("Khẩu độ: f/2.8"))
        assertTrue(advice.contains("ISO: Auto"))
        assertTrue(advice.contains("Cấu hình góc máy phụ"))
        assertTrue(advice.contains("chân monopod vững chắc"))
        assertTrue(advice.contains("16-35mm"))
    }

    @Test
    fun testCleanAdviceTextWithHeaderPrefixesAndPersonaTags() {
        val input = """
            [Lời Nhắn Nhẹ Nhàng]: Bạn hãy nghỉ ngơi một chút nhé.
            [Cố Vấn]: Luôn giữ vững tiến độ hợp đồng.
            **Lời khuyên**: Hãy xem xét kỹ các điều khoản thanh toán.
            Lời nhắn: Ngày mai sẽ có cuộc họp quan trọng.
            - Chú ý: Không chia sẻ mật khẩu cho người lạ.
        """.trimIndent()

        val cleaned = SummaryParser.cleanAdviceText(input)

        assertFalse(cleaned.contains("[Lời Nhắn Nhẹ Nhàng]:"))
        assertFalse(cleaned.contains("[Cố Vấn]:"))
        assertFalse(cleaned.contains("**Lời khuyên**:"))
        assertFalse(cleaned.contains("Lời nhắn:"))
        assertTrue(cleaned.contains("Bạn hãy nghỉ ngơi một chút nhé."))
        assertTrue(cleaned.contains("Luôn giữ vững tiến độ hợp đồng."))
        assertTrue(cleaned.contains("Hãy xem xét kỹ các điều khoản thanh toán."))
        assertTrue(cleaned.contains("Ngày mai sẽ có cuộc họp quan trọng."))
        assertTrue(cleaned.contains("Không chia sẻ mật khẩu cho người lạ."))
    }

    @Test
    fun testFreeformAdviceWithQuestionsRemainsContinuous() {
        val input = """
            Lời khuyên chân thành:
            1. Bạn có muốn đổi lịch sang ngày mai không? Nếu có, hãy báo sớm cho đối tác.
            2. Kiểm tra lại địa điểm hẹn ở quán Cà phê Highland.
            3. Liệu xe có kịp bảo dưỡng trước chuyến đi? Hãy gọi cho gara vào đầu giờ sáng.
        """.trimIndent()

        val parsed = SummaryParser.parse(input)

        assertTrue(parsed.criticalQuestions.isEmpty())
        assertTrue(parsed.conciseAdvice.contains("Bạn có muốn đổi lịch sang ngày mai không?"))
        assertTrue(parsed.conciseAdvice.contains("Kiểm tra lại địa điểm hẹn"))
        assertTrue(parsed.conciseAdvice.contains("Liệu xe có kịp bảo dưỡng trước chuyến đi?"))
        assertEquals(0, parsed.totalTaskCount)
    }
}
