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

        // Verify reference to previous summary and overdue task retention
        assertTrue(prompt.contains("Ca trước đã chốt đơn thành công"))
        assertTrue(prompt.contains("thêm 1 đến 2 phiên sau khi quá hạn"))

        // Verify notification content
        assertTrue(prompt.contains("Nguyễn Văn A"))
        assertTrue(prompt.contains("Giao hợp đồng trước 17h"))
    }

    @Test
    fun testBuildEnglishSummaryPrompt() {
        val notifications = listOf(
            NotificationEntity(
                id = 1,
                appPackage = "org.telegram.messenger",
                appName = "Telegram",
                sender = "Alice Smith",
                content = "Please finalize the Q4 roadmap by 3pm"
            )
        )

        val prompt = GeminiPromptBuilder.buildSummaryPrompt(
            notifications = notifications,
            customFixedContext = "I am a VP of Engineering",
            persona = PromptPersona.EXECUTIVE_SECRETARY,
            userQueryOrInstruction = "Highlight release risks",
            previousSummary = "Previous sprint was on track",
            language = com.orca.orcaauto.ui.locale.AppLanguage.ENGLISH
        )

        // Verify English headers & system role
        assertTrue(prompt.contains(PromptPersona.EXECUTIVE_SECRETARY.systemRoleEn))
        assertTrue(prompt.contains("=== TONE & STYLE PRINCIPLES ==="))
        assertTrue(prompt.contains("=== REAL-TIME TIME ANCHOR & SCHEDULING ==="))
        assertTrue(prompt.contains("=== USER BACKGROUND & CONTEXT ==="))
        assertTrue(prompt.contains("I am a VP of Engineering"))
        assertTrue(prompt.contains("=== DIRECT USER REQUEST / INSTRUCTION ==="))
        assertTrue(prompt.contains("Highlight release risks"))
        assertTrue(prompt.contains("=== STEP-BY-STEP REASONING GUIDELINES (MANDATORY <scratchpad> TAG) ==="))
        assertTrue(prompt.contains("1. Time Anchor"))
        assertTrue(prompt.contains("=== MANDATORY OUTPUT FORMAT RULES (AFTER CLOSING </scratchpad>) ==="))
        assertTrue(prompt.contains("### 🔴 TO-DO TASKS"))
        assertTrue(prompt.contains("### 🟢 COMPLETED TASKS (DONE)"))
        assertTrue(prompt.contains("### 🟡 PLANNED TASKS"))
        assertTrue(prompt.contains("Alice Smith"))
        assertTrue(prompt.contains("Please finalize the Q4 roadmap"))
    }
}
