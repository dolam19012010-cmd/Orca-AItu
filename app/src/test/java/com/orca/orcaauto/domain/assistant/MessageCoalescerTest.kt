package com.orca.orcaauto.domain.assistant

import com.orca.orcaauto.data.local.NotificationEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageCoalescerTest {

    // 1. Single message creates single turn and single conversation
    @Test
    fun testSingleMessage_createsSingleTurnAndConversation() {
        val notif = NotificationEntity(
            id = 100L,
            appPackage = "com.zing.zalo",
            appName = "Zalo",
            sender = "Sếp Tuấn",
            content = "Alo em",
            timestamp = 1700000000000L
        )

        val result = MessageCoalescer.coalesce(listOf(notif))

        assertEquals(1, result.conversations.size)
        assertEquals(1, result.totalMessageCount)
        val conv = result.conversations.first()
        assertEquals("Zalo", conv.appName)
        assertEquals("com.zing.zalo", conv.appPackage)
        assertEquals("Sếp Tuấn", conv.conversationTitle)
        assertEquals(1, conv.turns.size)

        val turn = conv.turns.first()
        assertEquals("Sếp Tuấn", turn.speaker)
        assertEquals(1700000000000L, turn.startTimestamp)
        assertEquals(1, turn.messages.size)
        assertEquals("Alo em", turn.messages.first().content)
        assertEquals(100L, turn.messages.first().id)
    }

    // 2. Rapid messages from same sender under 3 minutes coalesce into single turn
    @Test
    fun testRapidMessagesSameSender_under3Minutes_coalescesIntoSingleTurn() {
        val t0 = 1700000000000L
        val notifications = listOf(
            NotificationEntity(id = 1L, appPackage = "com.zing.zalo", appName = "Zalo", sender = "Sếp Tuấn", content = "Alo em", timestamp = t0),
            NotificationEntity(id = 2L, appPackage = "com.zing.zalo", appName = "Zalo", sender = "Sếp Tuấn", content = "Chiều nay 15h họp nhé", timestamp = t0 + 45_000L),
            NotificationEntity(id = 3L, appPackage = "com.zing.zalo", appName = "Zalo", sender = "Sếp Tuấn", content = "Nhớ mang theo hợp đồng", timestamp = t0 + 120_000L)
        )

        val result = MessageCoalescer.coalesce(notifications)

        val conv = result.conversations.first()
        assertEquals(1, conv.turns.size)
        val turn = conv.turns.first()
        assertEquals("Sếp Tuấn", turn.speaker)
        assertEquals(3, turn.messages.size)
        assertEquals(t0, turn.startTimestamp)
        assertEquals(t0 + 120_000L, turn.lastTimestamp)

        val prompt = MessageCoalescer.formatForPrompt(result)
        // Timestamp is shown on turn header, not repeated for each message
        assertTrue(prompt.contains("• ["))
        assertTrue(prompt.contains("Sếp Tuấn:"))
        assertTrue(prompt.contains("  - Alo em"))
        assertTrue(prompt.contains("  - Chiều nay 15h họp nhé"))
        assertTrue(prompt.contains("  - Nhớ mang theo hợp đồng"))

        // Count how many times the timestamp prefix pattern "[HH:mm]" appears
        val timestampMatches = Regex("""\[\d{2}:\d{2}\]""").findAll(prompt).count()
        assertEquals(1, timestampMatches)
    }

    // 3. Silence gap greater than 3 minutes breaks into new turn
    @Test
    fun testSilenceGapGreaterThan3Minutes_breaksIntoNewTurn() {
        val t0 = 1700000000000L
        val notifications = listOf(
            NotificationEntity(id = 1L, appPackage = "com.zing.zalo", appName = "Zalo", sender = "Sếp Tuấn", content = "Tin 1", timestamp = t0),
            NotificationEntity(id = 2L, appPackage = "com.zing.zalo", appName = "Zalo", sender = "Sếp Tuấn", content = "Tin 2 sau hơn 3 phút", timestamp = t0 + 180_001L)
        )

        val result = MessageCoalescer.coalesce(notifications)

        val conv = result.conversations.first()
        assertEquals(2, conv.turns.size)

        val turn1 = conv.turns[0]
        assertEquals(t0, turn1.startTimestamp)
        assertEquals(1, turn1.messages.size)
        assertEquals("Tin 1", turn1.messages[0].content)

        val turn2 = conv.turns[1]
        assertEquals(t0 + 180_001L, turn2.startTimestamp)
        assertEquals(1, turn2.messages.size)
        assertEquals("Tin 2 sau hơn 3 phút", turn2.messages[0].content)
    }

    // 4. Exact 3-minute boundary: 180.000 ms coalesces, 180.001 ms breaks
    @Test
    fun testExact3MinuteBoundary() {
        val t0 = 1700000000000L
        val notifications = listOf(
            NotificationEntity(id = 1L, appPackage = "com.zing.zalo", appName = "Zalo", sender = "A", content = "Msg 1", timestamp = t0),
            // Exactly 180,000 ms later -> should coalesce into turn 1
            NotificationEntity(id = 2L, appPackage = "com.zing.zalo", appName = "Zalo", sender = "A", content = "Msg 2 at 180.000ms", timestamp = t0 + 180_000L),
            // 180,001 ms after Msg 2 -> should break into turn 2
            NotificationEntity(id = 3L, appPackage = "com.zing.zalo", appName = "Zalo", sender = "A", content = "Msg 3 at 180.001ms gap", timestamp = t0 + 180_000L + 180_001L)
        )

        val result = MessageCoalescer.coalesce(notifications)

        val conv = result.conversations.first()
        assertEquals(2, conv.turns.size)
        assertEquals(2, conv.turns[0].messages.size)
        assertEquals("Msg 1", conv.turns[0].messages[0].content)
        assertEquals("Msg 2 at 180.000ms", conv.turns[0].messages[1].content)

        assertEquals(1, conv.turns[1].messages.size)
        assertEquals("Msg 3 at 180.001ms gap", conv.turns[1].messages[0].content)
    }

    // 5. Sender change breaks turn immediately regardless of time gap
    @Test
    fun testSenderChange_breaksTurnImmediatelyRegardlessOfTime() {
        val t0 = 1700000000000L
        val notifications = listOf(
            NotificationEntity(id = 1L, appPackage = "com.zing.zalo", appName = "Zalo", sender = "Sếp Tuấn (Nhóm Họp)", content = "Alo em", timestamp = t0),
            NotificationEntity(id = 2L, appPackage = "com.zing.zalo", appName = "Zalo", sender = "Lan Trợ Lý (Nhóm Họp)", content = "Dạ em nghe", timestamp = t0 + 5_000L)
        )

        val result = MessageCoalescer.coalesce(notifications)

        assertEquals(1, result.conversations.size)
        val conv = result.conversations.first()
        assertEquals("Nhóm Họp", conv.conversationTitle)
        assertEquals(2, conv.turns.size)
        assertEquals("Sếp Tuấn", conv.turns[0].speaker)
        assertEquals("Lan Trợ Lý", conv.turns[1].speaker)
    }

    // 6. Multi-party dialogue flow preserves chronological order
    @Test
    fun testMultiPartyDialogueFlow() {
        val t0 = 1700000000000L
        val notifications = listOf(
            NotificationEntity(id = 1L, appPackage = "com.zing.zalo", appName = "Zalo", sender = "Nhóm Ăn Trưa > Tuấn", content = "Trưa nay ăn gì?", timestamp = t0),
            NotificationEntity(id = 2L, appPackage = "com.zing.zalo", appName = "Zalo", sender = "Nhóm Ăn Trưa > Lan", content = "Ăn cơm tấm nhé", timestamp = t0 + 10_000L),
            NotificationEntity(id = 3L, appPackage = "com.zing.zalo", appName = "Zalo", sender = "Nhóm Ăn Trưa > Tuấn", content = "Ok chốt 11h45", timestamp = t0 + 20_000L)
        )

        val result = MessageCoalescer.coalesce(notifications)

        assertEquals(1, result.conversations.size)
        val conv = result.conversations.first()
        assertEquals("Nhóm Ăn Trưa", conv.conversationTitle)
        assertEquals(3, conv.turns.size)
        assertEquals("Tuấn", conv.turns[0].speaker)
        assertEquals("Trưa nay ăn gì?", conv.turns[0].messages[0].content)

        assertEquals("Lan", conv.turns[1].speaker)
        assertEquals("Ăn cơm tấm nhé", conv.turns[1].messages[0].content)

        assertEquals("Tuấn", conv.turns[2].speaker)
        assertEquals("Ok chốt 11h45", conv.turns[2].messages[0].content)
    }

    // 7. Grouping across multiple applications
    @Test
    fun testMultipleAppsGrouping() {
        val notifications = listOf(
            NotificationEntity(id = 1L, appPackage = "com.zing.zalo", appName = "Zalo", sender = "A", content = "Zalo msg", timestamp = 1000L),
            NotificationEntity(id = 2L, appPackage = "org.telegram.messenger", appName = "Telegram", sender = "B", content = "Telegram msg", timestamp = 2000L),
            NotificationEntity(id = 3L, appPackage = "com.facebook.orca", appName = "Messenger", sender = "C", content = "Messenger msg", timestamp = 3000L)
        )

        val result = MessageCoalescer.coalesce(notifications)

        assertEquals(3, result.conversations.size)
        val prompt = MessageCoalescer.formatForPrompt(result)
        assertTrue(prompt.contains("【 Ứng dụng: Zalo 】"))
        assertTrue(prompt.contains("【 Ứng dụng: Telegram 】"))
        assertTrue(prompt.contains("【 Ứng dụng: Messenger 】"))
    }

    // 8. Multiple conversations within the same application
    @Test
    fun testMultipleConversationsWithinSameApp() {
        val notifications = listOf(
            NotificationEntity(id = 1L, appPackage = "com.zing.zalo", appName = "Zalo", sender = "Sếp Tuấn", content = "Báo cáo đâu em?", timestamp = 1000L),
            NotificationEntity(id = 2L, appPackage = "com.zing.zalo", appName = "Zalo", sender = "Lan (Kế toán)", content = "Đã gửi hóa đơn nhé", timestamp = 2000L)
        )

        val result = MessageCoalescer.coalesce(notifications)

        assertEquals(2, result.conversations.size)
        assertEquals("Sếp Tuấn", result.conversations[0].conversationTitle)
        assertEquals("Kế toán", result.conversations[1].conversationTitle)
        assertEquals("Lan", result.conversations[1].turns[0].speaker)

        val prompt = MessageCoalescer.formatForPrompt(result)
        assertTrue(prompt.contains("💬 Hội thoại: Sếp Tuấn"))
        assertTrue(prompt.contains("💬 Hội thoại: Kế toán"))
    }

    // 9. Sequential ephemeral ticket ID assignment
    @Test
    fun testTicketIdAssignmentSequential() {
        val notifications = listOf(
            NotificationEntity(id = 101L, appPackage = "com.zing.zalo", appName = "Zalo", sender = "User1", content = "Msg 1", timestamp = 1000L),
            NotificationEntity(id = 102L, appPackage = "com.zing.zalo", appName = "Zalo", sender = "User1", content = "Msg 2", timestamp = 2000L),
            NotificationEntity(id = 103L, appPackage = "org.telegram.messenger", appName = "Telegram", sender = "User2", content = "Msg 3", timestamp = 3000L),
            NotificationEntity(id = 104L, appPackage = "org.telegram.messenger", appName = "Telegram", sender = "User2", content = "Msg 4", timestamp = 4000L)
        )

        val result = MessageCoalescer.coalesce(notifications, assignTicketIds = true)

        assertEquals(4, result.ticketMap.size)
        assertEquals(101L, result.ticketMap[1])
        assertEquals(102L, result.ticketMap[2])
        assertEquals(103L, result.ticketMap[3])
        assertEquals(104L, result.ticketMap[4])

        val firstConv = result.conversations.first()
        assertEquals(1, firstConv.turns[0].messages[0].ticketId)
        assertEquals(2, firstConv.turns[0].messages[1].ticketId)
    }

    // 10. formatForPrompt with ticket IDs displays [1], [2]...
    @Test
    fun testFormatPromptWithTicketIds() {
        val notifications = listOf(
            NotificationEntity(id = 10L, appPackage = "com.zing.zalo", appName = "Zalo", sender = "Tuấn", content = "Tin nhắn test 1", timestamp = 1000L),
            NotificationEntity(id = 11L, appPackage = "com.zing.zalo", appName = "Zalo", sender = "Tuấn", content = "Tin nhắn test 2", timestamp = 2000L)
        )

        val result = MessageCoalescer.coalesce(notifications, assignTicketIds = true)
        val prompt = MessageCoalescer.formatForPrompt(result, withTicketIds = true)

        assertTrue(prompt.contains("  - [1] Tin nhắn test 1"))
        assertTrue(prompt.contains("  - [2] Tin nhắn test 2"))
    }

    // 11. formatForPrompt without ticket IDs is clean
    @Test
    fun testFormatPromptWithoutTicketIds() {
        val notifications = listOf(
            NotificationEntity(id = 10L, appPackage = "com.zing.zalo", appName = "Zalo", sender = "Tuấn", content = "Tin nhắn test 1", timestamp = 1000L),
            NotificationEntity(id = 11L, appPackage = "com.zing.zalo", appName = "Zalo", sender = "Tuấn", content = "Tin nhắn test 2", timestamp = 2000L)
        )

        val result = MessageCoalescer.coalesce(notifications, assignTicketIds = true)
        val prompt = MessageCoalescer.formatForPrompt(result, withTicketIds = false)

        assertTrue(prompt.contains("  - Tin nhắn test 1"))
        assertTrue(prompt.contains("  - Tin nhắn test 2"))
        assertFalse(prompt.contains("[1]"))
        assertFalse(prompt.contains("[2]"))
    }

    // 12. PII masking applied when sanitizePii is true, skipped when false
    @Test
    fun testPiiMaskingApplied() {
        val sensitiveContent = "Chuyển tiền vào STK 1234567890 thẻ 4111 2222 3333 4444 SĐT 0912345678 nhé"
        val notifications = listOf(
            NotificationEntity(id = 1L, appPackage = "com.zing.zalo", appName = "Zalo", sender = "Tuấn", content = sensitiveContent, timestamp = 1000L)
        )

        // With sanitizePii = true
        val resultSanitized = MessageCoalescer.coalesce(notifications, sanitizePii = true)
        val sanitizedContent = resultSanitized.conversations.first().turns.first().messages.first().content
        assertTrue(sanitizedContent.contains("[STK ĐÃ CHE]"))
        assertTrue(sanitizedContent.contains("[THẺ NGÂN HÀNG: ***4444]"))
        assertTrue(sanitizedContent.contains("091****678"))
        assertFalse(sanitizedContent.contains("1234567890"))

        // With sanitizePii = false
        val resultRaw = MessageCoalescer.coalesce(notifications, sanitizePii = false)
        val rawContent = resultRaw.conversations.first().turns.first().messages.first().content
        assertEquals(sensitiveContent, rawContent)
    }

    // 13. Multi-line message indentation maintains bullet hierarchy
    @Test
    fun testMultiLineMessageIndentation() {
        val multiLineContent = "Kế hoạch tuần này:\nPhần 1: Khảo sát thị trường\nPhần 2: Báo cáo kết quả"
        val notifications = listOf(
            NotificationEntity(id = 1L, appPackage = "com.zing.zalo", appName = "Zalo", sender = "Sếp", content = multiLineContent, timestamp = 1000L)
        )

        val result = MessageCoalescer.coalesce(notifications, assignTicketIds = true)
        val prompt = MessageCoalescer.formatForPrompt(result, withTicketIds = true)

        assertTrue(prompt.contains("  - [1] Kế hoạch tuần này:"))
        assertTrue(prompt.contains("    Phần 1: Khảo sát thị trường"))
        assertTrue(prompt.contains("    Phần 2: Báo cáo kết quả"))
    }

    // 14. Outdated/unordered timestamps are automatically sorted chronologically
    @Test
    fun testOutdatedTimestampsAutoSorted() {
        val notifications = listOf(
            NotificationEntity(id = 3L, appPackage = "com.zing.zalo", appName = "Zalo", sender = "Tuấn", content = "Third", timestamp = 3000L),
            NotificationEntity(id = 1L, appPackage = "com.zing.zalo", appName = "Zalo", sender = "Tuấn", content = "First", timestamp = 1000L),
            NotificationEntity(id = 2L, appPackage = "com.zing.zalo", appName = "Zalo", sender = "Tuấn", content = "Second", timestamp = 2000L)
        )

        val result = MessageCoalescer.coalesce(notifications)

        val messages = result.conversations.first().turns.first().messages
        assertEquals(3, messages.size)
        assertEquals("First", messages[0].content)
        assertEquals("Second", messages[1].content)
        assertEquals("Third", messages[2].content)
    }

    // 15. Empty list and blank sender / appName handling
    @Test
    fun testEmptyAndBlankSenderHandling() {
        // Empty list
        val emptyResult = MessageCoalescer.coalesce(emptyList())
        assertTrue(emptyResult.conversations.isEmpty())
        assertTrue(emptyResult.ticketMap.isEmpty())
        assertEquals(0, emptyResult.totalMessageCount)
        assertEquals("(Không có tin nhắn liên quan nào)", MessageCoalescer.formatForPrompt(emptyResult))

        // Blank sender and appName fallback
        val blankNotif = NotificationEntity(
            id = 1L,
            appPackage = "com.unknown.app",
            appName = "",
            sender = "  ",
            content = "Thông báo hệ thống",
            timestamp = 1000L
        )

        val result = MessageCoalescer.coalesce(listOf(blankNotif))
        assertEquals(1, result.conversations.size)
        val conv = result.conversations.first()
        assertEquals("com.unknown.app", conv.appName)
        assertEquals("Hội thoại không tên", conv.conversationTitle)
        assertEquals("Người gửi ẩn danh", conv.turns.first().speaker)
    }

    // Extra: Group chat formats like "Group > Speaker" and group content prefix "Nam: message"
    @Test
    fun testGroupChatPrefixAndArrowFormats() {
        // Format: "Nhóm Dự Án > Hùng"
        val arrowNotif = NotificationEntity(
            id = 1L,
            appPackage = "com.zing.zalo",
            appName = "Zalo",
            sender = "Nhóm Dự Án > Hùng",
            content = "Tiến độ đạt 80% rồi",
            timestamp = 1000L
        )
        val arrowResult = MessageCoalescer.coalesce(listOf(arrowNotif))
        assertEquals("Nhóm Dự Án", arrowResult.conversations.first().conversationTitle)
        assertEquals("Hùng", arrowResult.conversations.first().turns.first().speaker)
        assertEquals("Tiến độ đạt 80% rồi", arrowResult.conversations.first().turns.first().messages.first().content)

        // Format: Group name in sender, content starts with "Nam: ..."
        val groupContentNotif = NotificationEntity(
            id = 2L,
            appPackage = "com.zing.zalo",
            appName = "Zalo",
            sender = "Nhóm Bạn Thân",
            content = "Nam: Chiều nay ai rảnh đi cafe không?",
            timestamp = 2000L
        )
        val groupResult = MessageCoalescer.coalesce(listOf(groupContentNotif))
        assertEquals("Nhóm Bạn Thân", groupResult.conversations.first().conversationTitle)
        assertEquals("Nam", groupResult.conversations.first().turns.first().speaker)
        assertEquals("Chiều nay ai rảnh đi cafe không?", groupResult.conversations.first().turns.first().messages.first().content)
    }

    // =========================================================================
    // 16. TURN DURATION CEILING (MAX_TURN_DURATION_MS = 15 MINUTES) TESTS (R6)
    // =========================================================================

    @Test
    fun turnDurationUnder15Minutes_coalescesIntoSingleTurn() {
        val t0 = 1700000000000L
        val min = 60_000L
        // Messages at T0, T0+2.5m, T0+5m, T0+7.5m, T0+10m, T0+12.5m, T0+15m
        // Silence gap is 2.5 minutes (<= COALESCE_WINDOW_MS 3m)
        // Total duration is exactly 15 minutes (<= MAX_TURN_DURATION_MS 15m)
        val notifications = listOf(
            NotificationEntity(id = 1L, appPackage = "com.zing.zalo", appName = "Zalo", sender = "Sếp Tuấn", content = "Tin 0m", timestamp = t0),
            NotificationEntity(id = 2L, appPackage = "com.zing.zalo", appName = "Zalo", sender = "Sếp Tuấn", content = "Tin 2.5m", timestamp = t0 + (2.5 * min).toLong()),
            NotificationEntity(id = 3L, appPackage = "com.zing.zalo", appName = "Zalo", sender = "Sếp Tuấn", content = "Tin 5m", timestamp = t0 + 5 * min),
            NotificationEntity(id = 4L, appPackage = "com.zing.zalo", appName = "Zalo", sender = "Sếp Tuấn", content = "Tin 7.5m", timestamp = t0 + (7.5 * min).toLong()),
            NotificationEntity(id = 5L, appPackage = "com.zing.zalo", appName = "Zalo", sender = "Sếp Tuấn", content = "Tin 10m", timestamp = t0 + 10 * min),
            NotificationEntity(id = 6L, appPackage = "com.zing.zalo", appName = "Zalo", sender = "Sếp Tuấn", content = "Tin 12.5m", timestamp = t0 + (12.5 * min).toLong()),
            NotificationEntity(id = 7L, appPackage = "com.zing.zalo", appName = "Zalo", sender = "Sếp Tuấn", content = "Tin 15m", timestamp = t0 + 15 * min)
        )

        val result = MessageCoalescer.coalesce(notifications)

        assertEquals(1, result.conversations.size)
        val conv = result.conversations.first()
        assertEquals(1, conv.turns.size)

        val turn = conv.turns.first()
        assertEquals("Sếp Tuấn", turn.speaker)
        assertEquals(7, turn.messages.size)
        assertEquals(t0, turn.startTimestamp)
        assertEquals(t0 + 15 * min, turn.lastTimestamp)
    }

    @Test
    fun turnDurationExceeds15Minutes_splitsIntoNewTurnAndPrintsTimestamp() {
        val t0 = 1700000000000L
        val min = 60_000L
        // Messages 2 minutes apart: 0m, 2m, 4m, 6m, 8m, 10m, 12m, 14m (each gap <= 3m)
        // Then next message at 15m + 1s (gap from 14m is 1m + 1s <= 3m, but duration from start is > 15m)
        val notifications = mutableListOf<NotificationEntity>()
        var idCounter = 1L
        for (m in 0..14 step 2) {
            notifications.add(
                NotificationEntity(
                    id = idCounter++,
                    appPackage = "com.zing.zalo",
                    appName = "Zalo",
                    sender = "Sếp Tuấn",
                    content = "Tin ${m}m",
                    timestamp = t0 + m * min
                )
            )
        }
        notifications.add(
            NotificationEntity(
                id = idCounter++,
                appPackage = "com.zing.zalo",
                appName = "Zalo",
                sender = "Sếp Tuấn",
                content = "Tin 15m01s (vượt trần 15 phút)",
                timestamp = t0 + 15 * min + 1_000L
            )
        )

        val result = MessageCoalescer.coalesce(notifications)

        val conv = result.conversations.first()
        assertEquals("Phải tách thành 2 turns khi thời lượng độc thoại vượt quá 15 phút", 2, conv.turns.size)

        val turn1 = conv.turns[0]
        assertEquals(8, turn1.messages.size)
        assertEquals(t0, turn1.startTimestamp)

        val turn2 = conv.turns[1]
        assertEquals(1, turn2.messages.size)
        assertEquals(t0 + 15 * min + 1_000L, turn2.startTimestamp)
        assertEquals("Tin 15m01s (vượt trần 15 phút)", turn2.messages[0].content)

        // Verify formatForPrompt prints separate • [HH:mm] headers for both turns
        val prompt = MessageCoalescer.formatForPrompt(result)
        val headerMatches = Regex("""• \[\d{2}:\d{2}\] Sếp Tuấn:""").findAll(prompt).count()
        assertEquals(2, headerMatches)
    }

    @Test
    fun monologueExceeding30Minutes_splitsIntoThreeTurns() {
        val t0 = 1700000000000L
        val min = 60_000L
        // Continuous monologue with messages 2 minutes apart spanning 32 minutes:
        // Window 1: 0m, 2m, ..., 14m (Turn 1, duration 14m <= 15m)
        // Window 2: 16m, 18m, ..., 30m (Turn 2, starts at 16m, duration at 30m is 14m <= 15m)
        // Window 3: 32m (Turn 3, starts at 32m)
        val notifs = mutableListOf<NotificationEntity>()
        var idCounter = 1L
        for (m in 0..32 step 2) {
            notifs.add(
                NotificationEntity(
                    id = idCounter++,
                    appPackage = "com.zing.zalo",
                    appName = "Zalo",
                    sender = "Giảng viên",
                    content = "Nội dung bài giảng phút thứ $m",
                    timestamp = t0 + m * min
                )
            )
        }

        val result = MessageCoalescer.coalesce(notifs)

        val conv = result.conversations.first()
        assertEquals("Độc thoại 32 phút phải được chia thành 3 turns liên tiếp", 3, conv.turns.size)

        val turn1 = conv.turns[0]
        assertEquals(8, turn1.messages.size) // 0, 2, 4, 6, 8, 10, 12, 14
        assertEquals(t0, turn1.startTimestamp)

        val turn2 = conv.turns[1]
        assertEquals(8, turn2.messages.size) // 16, 18, 20, 22, 24, 26, 28, 30
        assertEquals(t0 + 16 * min, turn2.startTimestamp)

        val turn3 = conv.turns[2]
        assertEquals(1, turn3.messages.size) // 32
        assertEquals(t0 + 32 * min, turn3.startTimestamp)
    }

    @Test
    fun ticketIdsRemainSequentialAcrossTurnSplits() {
        val t0 = 1700000000000L
        val min = 60_000L
        val notifs = listOf(
            NotificationEntity(id = 101L, appPackage = "com.zing.zalo", appName = "Zalo", sender = "Tuấn", content = "Tin 1", timestamp = t0),
            NotificationEntity(id = 102L, appPackage = "com.zing.zalo", appName = "Zalo", sender = "Tuấn", content = "Tin 2", timestamp = t0 + 2 * min),
            NotificationEntity(id = 103L, appPackage = "com.zing.zalo", appName = "Zalo", sender = "Tuấn", content = "Tin 3 tách turn", timestamp = t0 + 16 * min),
            NotificationEntity(id = 104L, appPackage = "com.zing.zalo", appName = "Zalo", sender = "Tuấn", content = "Tin 4", timestamp = t0 + 18 * min)
        )

        val result = MessageCoalescer.coalesce(notifs, assignTicketIds = true)

        assertEquals(2, result.conversations.first().turns.size)

        // Ticket IDs must be [1], [2], [3], [4] sequentially mapped to real IDs
        assertEquals(4, result.ticketMap.size)
        assertEquals(101L, result.ticketMap[1])
        assertEquals(102L, result.ticketMap[2])
        assertEquals(103L, result.ticketMap[3])
        assertEquals(104L, result.ticketMap[4])

        val prompt = MessageCoalescer.formatForPrompt(result, withTicketIds = true)
        assertTrue(prompt.contains("  - [1] Tin 1"))
        assertTrue(prompt.contains("  - [2] Tin 2"))
        assertTrue(prompt.contains("  - [3] Tin 3 tách turn"))
        assertTrue(prompt.contains("  - [4] Tin 4"))
    }
}
