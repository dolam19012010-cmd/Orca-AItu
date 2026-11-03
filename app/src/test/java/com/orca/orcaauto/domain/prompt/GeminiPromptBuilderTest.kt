package com.orca.orcaauto.domain.prompt

import com.orca.orcaauto.data.local.NotificationEntity
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiPromptBuilderTest {

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

        // Verify reference to previous summary
        assertTrue(prompt.contains("Ca trước đã chốt đơn thành công"))

        // Verify notification content
        assertTrue(prompt.contains("Nguyễn Văn A"))
        assertTrue(prompt.contains("Giao hợp đồng trước 17h"))
    }
}
