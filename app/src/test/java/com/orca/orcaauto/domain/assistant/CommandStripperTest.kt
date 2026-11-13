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
        // As tokens stream in, unclosed command prefixes (including dangling '[') must be hidden from user
        val tokenSequence = listOf(
            "Tôi sẽ hiển thị ghi chú cho bạn: ",
            "Tôi sẽ hiển thị ghi chú cho bạn: [",
            "Tôi sẽ hiển thị ghi chú cho bạn: [S",
            "Tôi sẽ hiển thị ghi chú cho bạn: [SH",
            "Tôi sẽ hiển thị ghi chú cho bạn: [SHO",
            "Tôi sẽ hiển thị ghi chú cho bạn: [SHOW",
            "Tôi sẽ hiển thị ghi chú cho bạn: [SHOW_NOTE",
            "Tôi sẽ hiển thị ghi chú cho bạn: [SHOW_NOTE:",
            "Tôi sẽ hiển thị ghi chú cho bạn: [SHOW_NOTE:42",
            "Tôi sẽ hiển thị ghi chú cho bạn: [SHOW_NOTE:42]"
        )

        val expected = "Tôi sẽ hiển thị ghi chú cho bạn:"

        // All intermediate states from index 1 (dangling '[') until before closing ']' must hide the tag
        for (i in 1 until tokenSequence.size - 1) {
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
        assertEquals("Đang tạo ghi chú:", CommandStripper.cleanLive("Đang tạo ghi chú: ["))
        assertEquals("Đang tạo ghi chú:", CommandStripper.cleanLive("Đang tạo ghi chú: [C"))
        assertEquals("Đang tạo ghi chú:", CommandStripper.cleanLive("Đang tạo ghi chú: [CREA"))
        assertEquals("Đang tạo ghi chú:", CommandStripper.cleanLive("Đang tạo ghi chú: [CREATE:Tiêu đề mới"))
        assertEquals("Đang tạo ghi chú:", CommandStripper.cleanLive("Đang tạo ghi chú: [CREATE: Lịch họp 14:30 | Phòng 201"))
        assertEquals("Đang nối thêm:", CommandStripper.cleanLive("Đang nối thêm: [A"))
        assertEquals("Đang nối thêm:", CommandStripper.cleanLive("Đang nối thêm: [APP"))
        assertEquals("Đang nối thêm:", CommandStripper.cleanLive("Đang nối thêm: [APPEND:5:Nội dung dở dang"))
        assertEquals("Đang cập nhật:", CommandStripper.cleanLive("Đang cập nhật: [U"))
        assertEquals("Đang cập nhật:", CommandStripper.cleanLive("Đang cập nhật: [UPD"))
        assertEquals("Đang xóa:", CommandStripper.cleanLive("Đang xóa: [D"))
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

    @Test
    fun cleanFinal_stripsDanglingIncompleteCommandTags() {
        val input = "Dưới đây là thông tin bạn cần: [CREATE: Lịch họp 14:30 | Phòng 201"
        val cleaned = CommandStripper.cleanFinal(input)
        assertEquals("Dưới đây là thông tin bạn cần:", cleaned)

        val danglingBracket = "Xem kết quả tại [SHOW"
        assertEquals("Xem kết quả tại", CommandStripper.cleanFinal(danglingBracket))

        val singleBracket = "Xem kết quả tại ["
        assertEquals("Xem kết quả tại", CommandStripper.cleanFinal(singleBracket))
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
    fun extractCommands_create_pipeDelimiterWithColonsInTitleAndContent() {
        // [CREATE: Lịch họp 14:30 | Phòng 201 lúc 15:00 | private]
        val raw = "[CREATE: Lịch họp 14:30 | Phòng 201 lúc 15:00 | private]"
        val commands = CommandStripper.extractCommands(raw)
        assertEquals(1, commands.size)
        val cmd = commands[0] as NoteCommand.Create
        assertEquals("Lịch họp 14:30", cmd.title)
        assertEquals("Phòng 201 lúc 15:00", cmd.content)
        assertTrue(cmd.isPrivate)

        // Public flag with timestamps in title
        val rawPublic = "[CREATE: Lịch sinh hoạt 08:00 : Họp sáng | Nội dung chi tiết: mục 1 | public]"
        val cmdPublic = CommandStripper.extractCommands(rawPublic).first() as NoteCommand.Create
        assertEquals("Lịch sinh hoạt 08:00 : Họp sáng", cmdPublic.title)
        assertEquals("Nội dung chi tiết: mục 1", cmdPublic.content)
        assertFalse(cmdPublic.isPrivate)

        // Multiple pipes without privacy flag (merged content preserved)
        val rawTable = "[CREATE: Bảng dữ liệu | Cột 1 | Cột 2]"
        val cmdTable = CommandStripper.extractCommands(rawTable).first() as NoteCommand.Create
        assertEquals("Bảng dữ liệu", cmdTable.title)
        assertEquals("Cột 1 | Cột 2", cmdTable.content)
        assertFalse(cmdTable.isPrivate)
    }

    @Test
    fun extractCommands_append_pipeDelimiterWithColons() {
        // [APPEND: 1 | Điểm danh 14:30 : Đủ người]
        val raw = "[APPEND: 1 | Điểm danh 14:30 : Đủ người]"
        val commands = CommandStripper.extractCommands(raw)
        assertEquals(1, commands.size)
        val appendCmd = commands[0] as NoteCommand.Append
        assertEquals(1L, appendCmd.noteId)
        assertEquals("Điểm danh 14:30 : Đủ người", appendCmd.textToAppend)
    }

    @Test
    fun extractCommands_update_pipeDelimiterWithColons() {
        val raw = "[UPDATE: 2 | Danh sách cập nhật mới lúc 09:00: Mục 1, Mục 2]"
        val commands = CommandStripper.extractCommands(raw)
        assertEquals(1, commands.size)
        val updateCmd = commands[0] as NoteCommand.Update
        assertEquals(2L, updateCmd.noteId)
        assertEquals("Danh sách cập nhật mới lúc 09:00: Mục 1, Mục 2", updateCmd.newContent)
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
