package com.orca.orcaauto.domain

import com.orca.orcaauto.data.local.NotificationEntity
import com.orca.orcaauto.data.local.SharedNoteEntity
import com.orca.orcaauto.data.local.SmartAlarmEntity
import com.orca.orcaauto.data.local.SmartAlarmStatus
import com.orca.orcaauto.data.remote.ApiKeyConfig
import com.orca.orcaauto.data.remote.AiProviderType
import com.orca.orcaauto.domain.assistant.CommandStripper
import com.orca.orcaauto.domain.notes.NoteCommand
import com.orca.orcaauto.domain.prompt.GeminiPromptBuilder
import com.orca.orcaauto.domain.prompt.PromptPersona
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying the Unified 2-Round Pipeline contract in [LiveAiChatHelper],
 * [SessionSummaryResult], and [GeminiPromptBuilder] integration with note snippets.
 */
class LiveAiChatHelperUnifiedPipelineTest {

    private val testApiKey = ApiKeyConfig(
        provider = AiProviderType.GEMINI,
        rawKey = "AIzaSyTestKey_12345",
        modelName = "gemini-2.0-flash",
        endpointUrl = "",
        isEnabled = true
    )

    @Test
    fun testSessionSummaryResult_supportsTwoAndThreeComponentDestructuring() {
        val sampleNote = SharedNoteEntity(
            id = 42L,
            title = "Kế hoạch tuần tra",
            content = "Tuần tra các tầng từ 1 đến 15",
            isPrivate = false,
            updatedAt = System.currentTimeMillis()
        )

        val result = SessionSummaryResult(
            summaryContent = "=== PHẦN 1 ===\nLời khuyên\n=== PHẦN 2 ===\nBảng nhiệm vụ",
            usedKey = testApiKey,
            shownCards = listOf(sampleNote)
        )

        // 2-component destructuring (as used in SummaryWorker)
        val (summary, key) = result
        assertEquals("summaryContent khớp", result.summaryContent, summary)
        assertEquals("usedKey khớp", testApiKey, key)

        // 3-component destructuring (as used in FloatingBubbleService)
        val (summary3, key3, cards) = result
        assertEquals("summary3 khớp", result.summaryContent, summary3)
        assertEquals("key3 khớp", testApiKey, key3)
        assertEquals("cards size là 1", 1, cards.size)
        assertEquals("note id là 42", 42L, cards[0].id)

        // Triple conversion
        val triple = result.toTriple()
        assertEquals(3, Triple::class.java.declaredFields.size.coerceAtLeast(3))
        assertEquals(result.summaryContent, triple.first)
        assertEquals(testApiKey, triple.second)
        assertEquals(1, triple.third.size)
    }

    @Test
    fun testTagCancelledTasksInSummary() {
        val summary = """
            === PHẦN 1: LỜI NHẮN NHẸ NHÀNG ===
            Chào bạn, ca trực hôm nay cần chú ý thiết bị PCCC.

            === PHẦN 2: BẢNG ĐIỀU PHỐI NHIỆM VỤ ===
            ### 🔴 NHIỆM VỤ PHẢI LÀM (TO-DO)
            - [ ] Kiểm tra hệ thống PCCC | Nguồn: BQL | Hạn: 10:00
            - [ ] Họp giao ban đầu ca | Nguồn: Đội trưởng | Hạn: 08:30
        """.trimIndent()

        val cancelledAlarm = SmartAlarmEntity(
            alarmId = "ALM_pccc",
            taskTitle = "Kiểm tra hệ thống PCCC",
            targetTimeMillis = System.currentTimeMillis(),
            targetDateLabel = "10:00",
            status = SmartAlarmStatus.CANCELLED
        )

        val tagged = LiveAiChatHelper.tagCancelledTasksInSummary(summary, listOf(cancelledAlarm))

        assertTrue("Nhiệm vụ PCCC phải được gắn nhãn [ĐÃ HỦY]", tagged.contains("Kiểm tra hệ thống PCCC | Nguồn: BQL | Hạn: 10:00 [ĐÃ HỦY]"))
        assertFalse("Họp giao ban không bị gắn nhãn [ĐÃ HỦY]", tagged.contains("Họp giao ban đầu ca | Nguồn: Đội trưởng | Hạn: 08:30 [ĐÃ HỦY]"))
    }

    @Test
    fun testBuildSummaryPromptWithNotesSnippet() {
        val notifications = listOf(
            NotificationEntity(
                id = 1L,
                appPackage = "com.zing.zalo",
                appName = "Zalo",
                sender = "Sếp",
                content = "Nhớ kiểm tra báo cáo tháng nhé."
            )
        )

        val notesSnippet = """
            === GHI CHÚ #10: "Mật khẩu wifi văn phòng" ===
            SSID: Office_5G, Pass: Secur!ty2026
        """.trimIndent()

        val prompt = GeminiPromptBuilder.buildSummaryPrompt(
            notifications = notifications,
            customFixedContext = "Bối cảnh kiểm thử",
            persona = PromptPersona.GENTLE_ASSISTANT,
            userQueryOrInstruction = "Tìm mật khẩu wifi giúp tôi",
            notesSnippet = notesSnippet
        )

        // Must NOT contain <scratchpad>
        assertFalse("Prompt không được chứa thẻ <scratchpad>", prompt.contains("<scratchpad>"))
        assertFalse("Prompt không được chứa thẻ </scratchpad>", prompt.contains("</scratchpad>"))

        // Must contain notes snippet
        assertTrue("Prompt phải chứa phần kho ghi chú", prompt.contains("=== KHO GHI CHÚ LIÊN QUAN (TRÍ NHỚ DÀI HẠN) ==="))
        assertTrue("Prompt phải chứa nội dung ghi chú", prompt.contains("Mật khẩu wifi văn phòng"))
        assertTrue("Prompt phải chứa lệnh [SHOW_NOTE:id]", prompt.contains("[SHOW_NOTE:id]"))

        // Must contain internal reasoning guidelines
        assertTrue("Prompt phải chứa chỉ thị tư duy nội bộ", prompt.contains("CHỈ THỊ TƯ DUY NỘI BỘ (SYSTEM REASONING GUIDELINES)"))
    }

    @Test
    fun testCommandStripperWithShowNoteCommand() {
        val rawAiOutput = """
            Chào bạn, tôi đã tìm thấy thông tin bạn cần trong kho ghi chú.
            [SHOW_NOTE:10]
            Hãy tham khảo thẻ ghi chú bên dưới nhé.

            === PHẦN 2: BẢNG ĐIỀU PHỐI NHIỆM VỤ ===
            ### 🔴 NHIỆM VỤ PHẢI LÀM (TO-DO)
            - [ ] Đổi mật khẩu định kỳ | Nguồn: IT | Hạn: 15:00
        """.trimIndent()

        val commands = CommandStripper.extractCommands(rawAiOutput)
        assertEquals("Phải bóc tách được 1 lệnh", 1, commands.size)
        assertTrue("Lệnh phải là NoteCommand.Show", commands[0] is NoteCommand.Show)
        assertEquals(10L, (commands[0] as NoteCommand.Show).noteId)

        val clean = CommandStripper.cleanFinal(rawAiOutput)
        assertFalse("Clean output không chứa thẻ [SHOW_NOTE:10]", clean.contains("[SHOW_NOTE:10]"))
        assertTrue("Clean output giữ nguyên văn bản trả lời", clean.contains("Chào bạn, tôi đã tìm thấy thông tin"))
    }

    @Test
    fun testLiveAiChatHelper_streamingCallbackContract() {
        val capturedTokens = mutableListOf<String>()
        val streamingCallback: (String) -> Unit = { token ->
            capturedTokens.add(token)
        }

        // Simulate streaming token arrival
        streamingCallback("Xin ")
        streamingCallback("chào ")
        streamingCallback("ngài!")

        assertEquals(3, capturedTokens.size)
        assertEquals("Xin chào ngài!", capturedTokens.joinToString(""))
    }
}
