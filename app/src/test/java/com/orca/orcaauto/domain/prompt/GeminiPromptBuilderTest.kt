package com.orca.orcaauto.domain.prompt

import com.orca.orcaauto.data.local.NotificationEntity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiPromptBuilderTest {

    @Test
    fun testBuildSummaryPromptDoesNotContainScratchpadTagsAndHasInternalReasoningGuidelines() {
        val prompt = GeminiPromptBuilder.buildSummaryPrompt(
            notifications = emptyList(),
            customFixedContext = "Bối cảnh kiểm thử",
            persona = PromptPersona.GENTLE_ASSISTANT
        )

        // Khẳng định prompt Vòng 2 hoàn toàn không chứa thẻ nháp
        assertFalse("Prompt không được chứa thẻ mở <scratchpad>", prompt.contains("<scratchpad>"))
        assertFalse("Prompt không được chứa thẻ đóng </scratchpad>", prompt.contains("</scratchpad>"))

        // Khẳng định có chỉ thị tư duy nội bộ và yêu cầu xuất trực tiếp 2 phần
        assertTrue("Prompt phải có Chỉ thị tư duy nội bộ", prompt.contains("CHỈ THỊ TƯ DUY NỘI BỘ (SYSTEM REASONING GUIDELINES)"))
        assertTrue("Prompt phải yêu cầu xuất trực tiếp 2 phần", prompt.contains("TRỰC TIẾP XUẤT 2 PHẦN"))
    }

    @Test
    fun testBuildSummaryPromptInjectsCustomContextAndPersona() {
        val notifications = listOf(
            NotificationEntity(
                id = 1,
                appPackage = "com.zing.zalo",
                appName = "Zalo",
                sender = "Nguyễn Văn A",
                content = "Giao hợp đồng trước 17h nhé"
            )
        )

        val prompt = GeminiPromptBuilder.buildSummaryPrompt(
            notifications = notifications,
            customFixedContext = "Tôi là Giám đốc kinh doanh công ty X",
            persona = PromptPersona.GENTLE_ASSISTANT,
            userQueryOrInstruction = "Tập trung vào đơn hàng lớn",
            previousSummary = "Ca trước đã chốt đơn thành công"
        )

        // Verify that custom context is injected
        assertTrue(prompt.contains("Tôi là Giám đốc kinh doanh công ty X"))

        // Verify persona instructions
        assertTrue(prompt.contains(PromptPersona.GENTLE_ASSISTANT.systemRole))

        // Verify user interactive query
        assertTrue(prompt.contains("Tập trung vào đơn hàng lớn"))

        // Verify reference to previous summary and overdue task retention
        assertTrue(prompt.contains("Ca trước đã chốt đơn thành công"))
        assertTrue(prompt.contains("thêm 1 đến 2 phiên sau khi quá hạn"))

        // Verify notification content
        assertTrue(prompt.contains("Nguyễn Văn A"))
        assertTrue(prompt.contains("Giao hợp đồng trước 17h"))
    }
}
