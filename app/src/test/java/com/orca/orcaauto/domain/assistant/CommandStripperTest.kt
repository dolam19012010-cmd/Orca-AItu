package com.orca.orcaauto.domain.assistant

import com.orca.orcaauto.domain.notes.NoteCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Comprehensive Unit Test Suite for [CommandStripper].
 *
 * Verifies:
 * 1. Live stripping removes all completed commands while streaming.
 * 2. Suppression of incomplete trailing command tags to prevent UI flicker.
 * 3. Final text cleaning and excessive newline normalization.
 * 4. Command extraction with ':' and '|' delimiters, privacy flags, and embedded colons.
 * 5. Adversarial inputs: Vietnamese diacritics, non-command bracketed text, malformed tags.
 */
class CommandStripperTest {

    // =========================================================================
    // 1. REAL-TIME LIVE CLEANING & TRAILING TAG SUPPRESSION
    // =========================================================================

    @Test
    fun cleanLive_removesCompletedCommands() {
        val input = "Dưới đây là thông tin [SHOW_NOTE:12] mà bạn đã yêu cầu."
        val cleaned = CommandStripper.cleanLive(input)
        assertEquals("Dưới đây là thông tin  mà bạn đã yêu cầu.", cleaned)
    }

    @Test
    fun cleanLive_suppressesIncompleteTrailingCommandPrefixes() {
        // As tokens stream in, unclosed command prefixes must be hidden from user
        val tokenSequence = listOf(
            "Tôi sẽ hiển thị ghi chú cho bạn: ",
            "Tôi sẽ hiển thị ghi chú cho bạn: [",
            "Tôi sẽ hiển thị ghi chú cho bạn: [SHOW",
            "Tôi sẽ hiển thị ghi chú cho bạn: [SHOW_NOTE",
            "Tôi sẽ hiển thị ghi chú cho bạn: [SHOW_NOTE:",
            "Tôi sẽ hiển thị ghi chú cho bạn: [SHOW_NOTE:42",
            "Tôi sẽ hiển thị ghi chú cho bạn: [SHOW_NOTE:42]"
        )

        val expected = "Tôi sẽ hiển thị ghi chú cho bạn:"

        // All intermediate states with pending tag must hide the tag
        for (i in 2 until tokenSequence.size - 1) {
            val state = tokenSequence[i]
            val cleaned = CommandStripper.cleanLive(state)
            assertEquals("State '$state' must suppress pending command prefix", expected, cleaned)
        }

        // Once the tag is closed with ']', it is stripped cleanly
        val finalState = tokenSequence.last()
        assertEquals(expected, CommandStripper.cleanLive(finalState))
    }

    @Test
    fun cleanLive_suppressesIncompleteTrailingCreateAndAppend() {
        assertEquals("Đang tạo ghi chú:", CommandStripper.cleanLive("Đang tạo ghi chú: [CREA"))
        assertEquals("Đang tạo ghi chú:", CommandStripper.cleanLive("Đang tạo ghi chú: [CREATE:Tiêu đề mới"))
        assertEquals("Đang nối thêm:", CommandStripper.cleanLive("Đang nối thêm: [APP"))
        assertEquals("Đang nối thêm:", CommandStripper.cleanLive("Đang nối thêm: [APPEND:5:Nội dung dở dang"))
        assertEquals("Đang cập nhật:", CommandStripper.cleanLive("Đang cập nhật: [UPD"))
        assertEquals("Đang xóa:", CommandStripper.cleanLive("Đang xóa: [DEL"))
    }

    @Test
    fun cleanLive_preservesNormalBracketedText() {
        // Non-command brackets like citations or numbers should not be stripped
        val normalText = "Tham khảo [1] và kết quả [Báo cáo Q4]."
        val cleaned = CommandStripper.cleanLive(normalText)
        assertEquals(normalText, cleaned)
    }

    @Test
    fun cleanLive_emptyAndBlankStrings_returnEmpty() {
        assertEquals("", CommandStripper.cleanLive(""))
        assertEquals("", CommandStripper.cleanLive("   "))
        assertEquals("", CommandStripper.cleanLive("[SHOW_NOTE:1]"))
    }

    // =========================================================================
    // 2. FINAL CLEANING & WHITESPACE NORMALIZATION
    // =========================================================================

    @Test
    fun cleanFinal_normalizesExcessiveNewlines() {
        val input = """
            Dòng 1
            [SHOW_NOTE:1]
            
            
            
            Dòng 2
        """.trimIndent()

        val cleaned = CommandStripper.cleanFinal(input)
        assertFalse(cleaned.contains("[SHOW_NOTE:1]"))
        assertFalse(cleaned.contains("\n\n\n"))
        assertTrue(cleaned.contains("Dòng 1\n\nDòng 2"))
    }

    // =========================================================================
    // 3. COMMAND EXTRACTION
    // =========================================================================

    @Test
    fun extractCommands_showNote_extractsId() {
        val raw = "Xem ghi chú [SHOW_NOTE:42] và [SHOW_NOTE: 100 ]"
        val commands = CommandStripper.extractCommands(raw)

        assertEquals(2, commands.size)
        assertTrue(commands[0] is NoteCommand.Show)
        assertEquals(42L, (commands[0] as NoteCommand.Show).noteId)
        assertTrue(commands[1] is NoteCommand.Show)
        assertEquals(100L, (commands[1] as NoteCommand.Show).noteId)
    }

    @Test
    fun extractCommands_create_withColonAndPipeDelimiters() {
        // Standard colon delimiter
        val rawColon = "[CREATE:Kế hoạch tuần:Nội dung công việc 1, 2, 3]"
        val commands1 = CommandStripper.extractCommands(rawColon)
        assertEquals(1, commands1.size)
        val cmd1 = commands1[0] as NoteCommand.Create
        assertEquals("Kế hoạch tuần", cmd1.title)
        assertEquals("Nội dung công việc 1, 2, 3", cmd1.content)
        assertFalse(cmd1.isPrivate)

        // Pipe delimiter
        val rawPipe = "[CREATE:Mua sắm|Sữa tươi, bánh mì, trái cây]"
        val commands2 = CommandStripper.extractCommands(rawPipe)
        assertEquals(1, commands2.size)
        val cmd2 = commands2[0] as NoteCommand.Create
        assertEquals("Mua sắm", cmd2.title)
        assertEquals("Sữa tươi, bánh mì, trái cây", cmd2.content)
        assertFalse(cmd2.isPrivate)
    }

    @Test
    fun extractCommands_create_withPrivacyFlags() {
        val rawPrivate1 = "[CREATE:Mật khẩu ngân hàng:Pass 123456:true]"
        val cmd1 = CommandStripper.extractCommands(rawPrivate1).first() as NoteCommand.Create
        assertEquals("Mật khẩu ngân hàng", cmd1.title)
        assertEquals("Pass 123456", cmd1.content)
        assertTrue(cmd1.isPrivate)

        val rawPrivate2 = "[CREATE:Ghi chú bí mật|Nội dung nhạy cảm|riêng tư]"
        val cmd2 = CommandStripper.extractCommands(rawPrivate2).first() as NoteCommand.Create
        assertEquals("Ghi chú bí mật", cmd2.title)
        assertEquals("Nội dung nhạy cảm", cmd2.content)
        assertTrue(cmd2.isPrivate)

        val rawPublic = "[CREATE:Ghi chú thường:Nội dung:false]"
        val cmd3 = CommandStripper.extractCommands(rawPublic).first() as NoteCommand.Create
        assertFalse(cmd3.isPrivate)
    }

    @Test
    fun extractCommands_create_handlesColonsInsideBody() {
        // Title has no colon, but content body has multiple colons
        val raw = "[CREATE:Thời khóa biểu:Tiết 1: 07:00 - 08:30; Tiết 2: 08:45 - 10:15]"
        val commands = CommandStripper.extractCommands(raw)
        assertEquals(1, commands.size)
        val cmd = commands[0] as NoteCommand.Create
        assertEquals("Thời khóa biểu", cmd.title)
        assertEquals("Tiết 1: 07:00 - 08:30; Tiết 2: 08:45 - 10:15", cmd.content)
    }

    @Test
    fun extractCommands_append_colonAndPipe() {
        val raw = "Tôi đã ghi thêm: [APPEND:5:Nội dung mới cần nối] [APPEND:6|Nội dung nối bằng pipe]"
        val commands = CommandStripper.extractCommands(raw)
        assertEquals(2, commands.size)

        val append1 = commands[0] as NoteCommand.Append
        assertEquals(5L, append1.noteId)
        assertEquals("Nội dung mới cần nối", append1.textToAppend)

        val append2 = commands[1] as NoteCommand.Append
        assertEquals(6L, append2.noteId)
        assertEquals("Nội dung nối bằng pipe", append2.textToAppend)
    }

    @Test
    fun extractCommands_update_and_delete() {
        val raw = "[UPDATE:7:Nội dung mới toanh] [DELETE:9]"
        val commands = CommandStripper.extractCommands(raw)
        assertEquals(2, commands.size)

        val updateCmd = commands[0] as NoteCommand.Update
        assertEquals(7L, updateCmd.noteId)
        assertEquals("Nội dung mới toanh", updateCmd.newContent)

        val deleteCmd = commands[1] as NoteCommand.Delete
        assertEquals(9L, deleteCmd.noteId)
    }

    @Test
    fun extractCommands_caseInsensitive() {
        val raw = "[show_note:1] [create:t:c] [append:2:a] [update:3:u] [delete:4]"
        val commands = CommandStripper.extractCommands(raw)
        assertEquals(5, commands.size)
        assertTrue(commands[0] is NoteCommand.Show)
        assertTrue(commands[1] is NoteCommand.Create)
        assertTrue(commands[2] is NoteCommand.Append)
        assertTrue(commands[3] is NoteCommand.Update)
        assertTrue(commands[4] is NoteCommand.Delete)
    }

    @Test
    fun extractCommands_emptyOrNoCommands_returnsEmptyList() {
        assertTrue(CommandStripper.extractCommands("").isEmpty())
        assertTrue(CommandStripper.extractCommands("Không có lệnh nào ở đây cả.").isEmpty())
        assertTrue(CommandStripper.extractCommands("[INVALID_COMMAND:123]").isEmpty())
    }
}
