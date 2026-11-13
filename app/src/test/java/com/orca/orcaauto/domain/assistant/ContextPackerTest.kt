package com.orca.orcaauto.domain.assistant

import com.orca.orcaauto.data.local.NotificationEntity
import com.orca.orcaauto.data.local.SharedNoteEntity
import com.orca.orcaauto.domain.notes.FakeSharedNoteDao
import com.orca.orcaauto.domain.notes.SharedNoteRepository
import com.orca.orcaauto.domain.notes.SharedNoteRepositoryImpl
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Comprehensive Unit Test Suite for [ContextPacker] covering:
 * - R1: Real-time anchor line with Asia/Ho_Chi_Minh time zone and Note metadata formatting ([📌 ĐÃ GHIM], Tạo/Cập nhật).
 * - R4: PII relaxation (sanitizePii = false retains phone numbers, account numbers, order codes).
 * - R5: Conditional Adaptive Message Compression (unprocessed always 100% verbatim, processed compressed only when total >= 20 and majority >= 60%).
 */
class ContextPackerTest {

    private lateinit var fakeNoteDao: FakeSharedNoteDao
    private lateinit var fakeNotificationDao: FakeNotificationDao
    private lateinit var noteRepository: SharedNoteRepository
    private lateinit var contextPacker: ContextPacker

    @Before
    fun setUp() {
        fakeNoteDao = FakeSharedNoteDao()
        fakeNotificationDao = FakeNotificationDao()
        noteRepository = SharedNoteRepositoryImpl(fakeNoteDao)
        contextPacker = ContextPacker(noteRepository, fakeNotificationDao)
    }

    // =========================================================================
    // 1. REAL-TIME ANCHOR (R1)
    // =========================================================================

    @Test
    fun realTimeAnchor_presentAtTopOfPromptWithAsiaHoChiMinh() = runTest {
        // 1. Verify getRealTimeAnchorLine format matches specification:
        // "⏰ Thời điểm hiện tại: Thứ..., ngày dd/MM/yyyy lúc HH:mm (Múi giờ: Asia/Ho_Chi_Minh)"
        val testTimestamp = 1715587200000L // 2024-05-13 15:00:00 (UTC+7)
        val anchorLine = ContextPacker.getRealTimeAnchorLine(testTimestamp)

        val anchorPattern = Regex("""^⏰ Thời điểm hiện tại: (Thứ Hai|Thứ Ba|Thứ Tư|Thứ Năm|Thứ Sáu|Thứ Bảy|Chủ Nhật), ngày \d{2}/\d{2}/\d{4} lúc \d{2}:\d{2} \(Múi giờ: Asia/Ho_Chi_Minh\)$""")
        assertTrue("Real-time anchor phải chuẩn tiếng Việt và đúng múi giờ Asia/Ho_Chi_Minh", anchorPattern.matches(anchorLine))
        assertTrue(anchorLine.contains("(Múi giờ: Asia/Ho_Chi_Minh)"))

        // 2. Verify anchor line is present at the very first line of fullOverviewPrompt
        val packaged = contextPacker.pack("Kiểm tra thời gian")
        val lines = packaged.fullOverviewPrompt.lines()
        val firstLine = lines.firstOrNull().orEmpty()

        assertTrue("Dòng đầu tiên của prompt phải là Real-Time Anchor", firstLine.startsWith("⏰ Thời điểm hiện tại:"))
        assertTrue("Dòng đầu tiên phải chứa nhãn múi giờ Asia/Ho_Chi_Minh", firstLine.endsWith("(Múi giờ: Asia/Ho_Chi_Minh)"))
        assertTrue("Dòng đầu tiên phải khớp regex đầy đủ", anchorPattern.matches(firstLine))
    }

    // =========================================================================
    // 2. CATALOG METADATA FORMATTING (R1)
    // =========================================================================

    @Test
    fun catalogSnippet_formatsPinnedBadgeAndCreatedUpdatedDates() = runTest {
        val createdTimePinned = 1715500000000L
        val updatedTimePinned = 1715586400000L

        fakeNoteDao.insert(
            SharedNoteEntity(
                id = 10L,
                title = "Kế hoạch quan trọng",
                content = "Nội dung quan trọng",
                isPrivate = false,
                isPinned = true,
                createdAt = createdTimePinned,
                updatedAt = updatedTimePinned
            )
        )

        val createdTimeUnpinned = 1715400000000L
        val updatedTimeUnpinned = 1715480000000L

        fakeNoteDao.insert(
            SharedNoteEntity(
                id = 20L,
                title = "Ghi chú thường",
                content = "Nội dung thường",
                isPrivate = true,
                isPinned = false,
                createdAt = createdTimeUnpinned,
                updatedAt = updatedTimeUnpinned
            )
        )

        val packaged = contextPacker.pack("Tra cứu danh mục")
        val snippet = packaged.catalogPromptSnippet

        val df = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).apply {
            timeZone = TimeZone.getTimeZone("Asia/Ho_Chi_Minh")
        }

        // 1. Verify Pinned note formatting has [📌 ĐÃ GHIM] and both timestamps
        val expectedCreatedStr10 = df.format(Date(createdTimePinned))
        val expectedUpdatedStr10 = df.format(Date(updatedTimePinned))
        assertTrue("Ghi chú ghim phải có huy hiệu [📌 ĐÃ GHIM]", snippet.contains("- [ID: 10] [📌 ĐÃ GHIM] \"Kế hoạch quan trọng\" [Thường]"))
        assertTrue("Phải hiển thị ngày tạo", snippet.contains("Tạo: $expectedCreatedStr10"))
        assertTrue("Phải hiển thị ngày cập nhật", snippet.contains("Cập nhật: $expectedUpdatedStr10"))

        // 2. Verify Unpinned note does NOT have [📌 ĐÃ GHIM] but has both timestamps and privacy tag
        val expectedCreatedStr20 = df.format(Date(createdTimeUnpinned))
        val expectedUpdatedStr20 = df.format(Date(updatedTimeUnpinned))
        assertTrue("Ghi chú không ghim không được có huy hiệu [📌 ĐÃ GHIM]", snippet.contains("- [ID: 20] \"Ghi chú thường\" [🔒 RIÊNG TƯ - CHỈ POINTER CỤC BỘ]"))
        assertFalse(snippet.contains("[📌 ĐÃ GHIM] \"Ghi chú thường\""))
        assertTrue("Phải hiển thị ngày tạo của ghi chú thường", snippet.contains("Tạo: $expectedCreatedStr20"))
        assertTrue("Phải hiển thị ngày cập nhật của ghi chú thường", snippet.contains("Cập nhật: $expectedUpdatedStr20"))
    }

    // =========================================================================
    // 3. PII SANITIZATION RELAXATION (R4)
    // =========================================================================

    @Test
    fun piiRelaxation_retainsRawPhoneAndBankAccountsInPrompt() = runTest {
        val rawMessage = "Khách đặt hàng gửi về SĐT 0912345678, STK Techcombank 19036789123456, mã đơn hàng ORD-8899 nhé"
        val now = System.currentTimeMillis()

        fakeNotificationDao.insert(
            NotificationEntity(
                id = 1L,
                appPackage = "com.zing.zalo",
                appName = "Zalo",
                sender = "Khách Hàng VIP",
                content = rawMessage,
                timestamp = now - 5000L,
                isProcessed = false
            )
        )

        val packaged = contextPacker.pack("Kiểm tra đơn hàng mới")

        // 1. PII relaxation: Raw phone number and bank account number must NOT be masked
        assertTrue("SĐT 0912345678 phải được giữ nguyên vẹn trong prompt", packaged.fullOverviewPrompt.contains("0912345678"))
        assertTrue("STK 19036789123456 phải được giữ nguyên vẹn trong prompt", packaged.fullOverviewPrompt.contains("19036789123456"))
        assertTrue("Mã đơn hàng ORD-8899 phải được giữ nguyên vẹn trong prompt", packaged.fullOverviewPrompt.contains("ORD-8899"))

        // 2. Must NOT contain PII masking placeholders
        assertFalse("Không được che STK khi sanitizePii = false", packaged.fullOverviewPrompt.contains("[STK ĐÃ CHE]"))
        assertFalse("Không được che SĐT khi sanitizePii = false", packaged.fullOverviewPrompt.contains("091****678"))
    }

    // =========================================================================
    // 4. CONDITIONAL ADAPTIVE MESSAGE COMPRESSION (R5)
    // =========================================================================

    @Test
    fun adaptiveCompression_unprocessedAlwaysVerbatim_processedCompressedIfMajority() = runTest {
        val now = System.currentTimeMillis()

        // ---------------------------------------------------------------------
        // Case A: Total notifications < 20 (e.g., 15 total, 12 processed = 80%)
        // Under threshold: 100% verbatim for ALL messages
        // ---------------------------------------------------------------------
        fakeNotificationDao.clearAllNotifications()
        val verboseProcessed = "Dạ em chào anh Tuấn, em xin phép gửi file báo cáo tài chính tháng 11 ạ!"
        val verboseUnprocessed = "Dạ vâng xin chào anh, bên em vừa tiếp nhận yêu cầu hỗ trợ mới nhé!"

        for (i in 1..12) {
            fakeNotificationDao.insert(
                NotificationEntity(
                    id = i.toLong(),
                    appPackage = "com.zing.zalo",
                    appName = "Zalo",
                    sender = "Nhân viên $i",
                    content = verboseProcessed,
                    timestamp = now - (15 - i) * 60_000L,
                    isProcessed = true
                )
            )
        }
        for (i in 13..15) {
            fakeNotificationDao.insert(
                NotificationEntity(
                    id = i.toLong(),
                    appPackage = "com.zing.zalo",
                    appName = "Zalo",
                    sender = "Khách $i",
                    content = verboseUnprocessed,
                    timestamp = now - (15 - i) * 60_000L,
                    isProcessed = false
                )
            )
        }

        assertFalse(
            "Tổng < 20 thì không được nén dù tỷ lệ đã xử lý cao",
            ContextPacker.shouldCompress(totalCount = 15, processedCount = 12)
        )

        val packagedCaseA = contextPacker.pack("Xem báo cáo")
        assertTrue(
            "Tin đã xử lý vẫn phải giữ 100% nguyên văn khi tổng < 20",
            packagedCaseA.fullOverviewPrompt.contains(verboseProcessed)
        )
        assertTrue(
            "Tin chưa xử lý phải giữ 100% nguyên văn",
            packagedCaseA.fullOverviewPrompt.contains(verboseUnprocessed)
        )

        // ---------------------------------------------------------------------
        // Case B: Total >= 20, but processed < 10 or ratio < 0.6
        // E.g., 25 total, 8 processed (ratio 8/25 = 32% < 60%)
        // ---------------------------------------------------------------------
        fakeNotificationDao.clearAllNotifications()
        for (i in 1..8) {
            fakeNotificationDao.insert(
                NotificationEntity(
                    id = i.toLong(),
                    appPackage = "com.zing.zalo",
                    appName = "Zalo",
                    sender = "Nhân viên $i",
                    content = verboseProcessed,
                    timestamp = now - (30 - i) * 60_000L,
                    isProcessed = true
                )
            )
        }
        for (i in 9..25) {
            fakeNotificationDao.insert(
                NotificationEntity(
                    id = i.toLong(),
                    appPackage = "com.zing.zalo",
                    appName = "Zalo",
                    sender = "Khách $i",
                    content = verboseUnprocessed,
                    timestamp = now - (30 - i) * 60_000L,
                    isProcessed = false
                )
            )
        }

        assertFalse(
            "Tổng >= 20 nhưng tin đã xử lý chiếm dưới 60% thì không được nén",
            ContextPacker.shouldCompress(totalCount = 25, processedCount = 8)
        )

        val packagedCaseB = contextPacker.pack("Xem tin nhắn")
        assertTrue(
            "Tin đã xử lý vẫn giữ 100% nguyên văn khi chưa đủ tỷ lệ đa số",
            packagedCaseB.fullOverviewPrompt.contains(verboseProcessed)
        )
        assertTrue(
            "Tin chưa xử lý luôn giữ 100% nguyên văn",
            packagedCaseB.fullOverviewPrompt.contains(verboseUnprocessed)
        )

        // ---------------------------------------------------------------------
        // Case C: Total >= 20, processed >= 10, ratio >= 0.6
        // E.g., 25 total, 20 processed (20/25 = 80%), 5 unprocessed
        // ---------------------------------------------------------------------
        fakeNotificationDao.clearAllNotifications()
        val processedWithKeywords = "Dạ em chào anh Tuấn, đơn hàng ORD-7766 đã bàn giao bưu tá Viettel Post SĐT 0988776655 thành công nhé ạ!"
        val unprocessedCritical = "Dạ vâng xin chào anh, khách hàng báo lỗi khẩn cấp mã ERR-404 trên máy chủ nhé!"

        for (i in 1..20) {
            fakeNotificationDao.insert(
                NotificationEntity(
                    id = i.toLong(),
                    appPackage = "com.zing.zalo",
                    appName = "Zalo",
                    sender = "Nhân viên CSKH $i",
                    content = processedWithKeywords,
                    timestamp = now - (30 - i) * 60_000L,
                    isProcessed = true
                )
            )
        }
        for (i in 21..25) {
            fakeNotificationDao.insert(
                NotificationEntity(
                    id = i.toLong(),
                    appPackage = "com.zing.zalo",
                    appName = "Zalo",
                    sender = "Kỹ Thuật $i",
                    content = unprocessedCritical,
                    timestamp = now - (30 - i) * 60_000L,
                    isProcessed = false
                )
            )
        }

        assertTrue(
            "Tổng >= 20 và tin đã xử lý >= 60% thì kích hoạt nén thích ứng",
            ContextPacker.shouldCompress(totalCount = 25, processedCount = 20)
        )

        val packagedCaseC = contextPacker.pack("Tổng hợp đơn và lỗi")

        // 1. Unprocessed messages MUST remain 100% verbatim
        assertTrue(
            "Tin chưa xử lý (isProcessed = false) BẮT BUỘC giữ 100% nguyên văn không bị cắt xén",
            packagedCaseC.fullOverviewPrompt.contains(unprocessedCritical)
        )

        // 2. Processed messages must be compressed (greetings/filler removed) but crucial codes/entities preserved
        assertFalse(
            "Tin đã xử lý phải được nén, loại bỏ câu chào thừa 'Dạ em chào anh Tuấn,'",
            packagedCaseC.fullOverviewPrompt.contains("Dạ em chào anh Tuấn,")
        )
        // Strictly preserves Sender, Group/App, Timestamp, and Key Keywords/Codes
        assertTrue("Phải giữ lại người gửi", packagedCaseC.fullOverviewPrompt.contains("Nhân viên CSKH"))
        assertTrue("Phải giữ lại tên app", packagedCaseC.fullOverviewPrompt.contains("Zalo"))
        assertTrue("Phải giữ lại mã đơn hàng ORD-7766", packagedCaseC.fullOverviewPrompt.contains("ORD-7766"))
        assertTrue("Phải giữ lại đơn vị vận chuyển Viettel Post", packagedCaseC.fullOverviewPrompt.contains("Viettel Post"))
        assertTrue("Phải giữ lại SĐT 0988776655", packagedCaseC.fullOverviewPrompt.contains("0988776655"))
    }

    @Test
    fun helper_compressMessageContent_boundaryAndEdgeCases() {
        // 1. Short text (<= 25 chars) untouched
        assertEquals("Xin chào bạn", ContextPacker.compressMessageContent("Xin chào bạn"))
        assertEquals("OK anh", ContextPacker.compressMessageContent("OK anh"))

        // 2. Strips greetings prefix and polite endings
        val raw = "Dạ em chào anh, cuộc họp chiều nay dời sang 16h30 tại phòng 302 nhé ạ!"
        val compressed = ContextPacker.compressMessageContent(raw)
        assertFalse(compressed.contains("Dạ em chào anh"))
        assertTrue(compressed.contains("cuộc họp chiều nay dời sang 16h30 tại phòng 302"))

        // 3. Strips filler lead-in
        val leadInRaw = "Cho em hỏi là đơn hàng 12345 đã giao chưa bạn ơi?"
        val leadInCompressed = ContextPacker.compressMessageContent(leadInRaw)
        assertFalse(leadInCompressed.contains("Cho em hỏi là"))
        assertTrue(leadInCompressed.contains("đơn hàng 12345 đã giao chưa"))
    }
}
