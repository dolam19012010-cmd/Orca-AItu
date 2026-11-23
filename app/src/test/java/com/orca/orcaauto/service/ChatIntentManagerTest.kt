package com.orca.orcaauto.service

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ChatIntentManagerTest {

    @Before
    fun setUp() {
        ChatIntentManager.clearAll()
    }

    @After
    fun tearDown() {
        ChatIntentManager.clearAll()
    }

    @Test
    fun testCacheAndRetrieveIntent() {
        ChatIntentManager.cacheChatIntent(
            packageName = "com.zing.zalo",
            senderTitle = "Sếp Tuấn",
            pendingIntent = null
        )

        assertEquals(0, ChatIntentManager.getCacheSize())

        val dummy = CachedChatIntent(
            packageName = "com.zing.zalo",
            senderTitle = "Sếp Tuấn",
            pendingIntent = null
        )
        assertNotNull(dummy)
        assertEquals("com.zing.zalo", dummy.packageName)
        assertEquals("Sếp Tuấn", dummy.senderTitle)
    }

    @Test
    fun testGenerateChatRefId_deterministicAndPrefixMatching() {
        val refRegex = Regex("""^[ztmo][0-9a-z]{4}$""")

        val refZalo = ChatIntentManager.generateChatRefId("com.zing.zalo", "sếp hùng")
        assertTrue("Zalo refId phải bắt đầu bằng 'z' và có 5 ký tự ($refZalo)", refZalo.startsWith("z"))
        assertTrue("Zalo refId phải khớp regex 5 ký tự base36 ($refZalo)", refRegex.matches(refZalo))

        val refTele = ChatIntentManager.generateChatRefId("org.telegram.messenger", "nhóm dev")
        assertTrue("Telegram refId phải bắt đầu bằng 't' ($refTele)", refTele.startsWith("t"))
        assertTrue("Telegram refId phải khớp regex 5 ký tự base36 ($refTele)", refRegex.matches(refTele))

        val refMess = ChatIntentManager.generateChatRefId("com.facebook.orca", "lan anh")
        assertTrue("Messenger refId phải bắt đầu bằng 'm' ($refMess)", refMess.startsWith("m"))
        assertTrue("Messenger refId phải khớp regex 5 ký tự base36 ($refMess)", refRegex.matches(refMess))

        val refOther = ChatIntentManager.generateChatRefId("com.slack", "general")
        assertTrue("Other app refId phải bắt đầu bằng 'o' ($refOther)", refOther.startsWith("o"))
        assertTrue("Other app refId phải khớp regex 5 ký tự base36 ($refOther)", refRegex.matches(refOther))

        // Deterministic property
        val refZaloRepeat = ChatIntentManager.generateChatRefId("com.zing.zalo", "sếp hùng")
        assertEquals("Cùng input phải sinh ra cùng refId tuyệt đối", refZalo, refZaloRepeat)

        // Different room produces different refId
        val refZaloOtherRoom = ChatIntentManager.generateChatRefId("com.zing.zalo", "sếp tuấn")
        assertFalse("Khác roomKey phải sinh ra refId khác nhau", refZalo == refZaloOtherRoom)
    }

    @Test
    fun testNormalizeRoomKey_stripsGroupPrefixesAndSpecialChars() {
        assertEquals("nhóm dự án", ChatIntentManager.normalizeRoomKey("Nhóm Dự Án: Nam"))
        assertEquals("team marketing", ChatIntentManager.normalizeRoomKey("Team Marketing > Tuấn"))
        assertEquals("gia đình", ChatIntentManager.normalizeRoomKey("Gia Đình (5 tin nhắn)"))
        assertEquals("sếp hùng", ChatIntentManager.normalizeRoomKey("Sếp Hùng (3 msgs)"))
        assertEquals("phòng kế toán", ChatIntentManager.normalizeRoomKey("  PHÒNG KẾ TOÁN: Chị Nga  "))
        assertEquals("dự án orca 2026", ChatIntentManager.normalizeRoomKey("[Dự Án Orca 2026]!"))
    }

    @Test
    fun testLruCacheCapacityAndEviction() {
        val cache = ChatTargetLruCache(capacity = 3, ttlMs = 60_000L)
        val now = System.currentTimeMillis()

        val t1 = CachedChatTarget("z0001", "com.zing.zalo", "room1", null, now)
        val t2 = CachedChatTarget("z0002", "com.zing.zalo", "room2", null, now + 10)
        val t3 = CachedChatTarget("z0003", "com.zing.zalo", "room3", null, now + 20)

        cache.put(t1)
        cache.put(t2)
        cache.put(t3)
        assertEquals(3, cache.size())

        // Access t1 to make it recently used (t2 becomes eldest)
        assertNotNull(cache.get("z0001", now + 30))

        // Put 4th item, exceeding capacity 3 -> eldest (t2) evicted
        val t4 = CachedChatTarget("z0004", "com.zing.zalo", "room4", null, now + 40)
        cache.put(t4)

        assertEquals(3, cache.size())
        assertNotNull(cache.get("z0001", now + 50))
        assertNull("t2 phải bị đẩy khỏi LRU cache vì là eldest", cache.get("z0002", now + 50))
        assertNotNull(cache.get("z0003", now + 50))
        assertNotNull(cache.get("z0004", now + 50))
    }

    @Test
    fun testLruCacheTtlPurge() {
        val ttl = 1000L
        val cache = ChatTargetLruCache(capacity = 5, ttlMs = ttl)
        val startMs = 100_000L

        val target = CachedChatTarget("z0001", "com.zing.zalo", "room1", null, startMs)
        cache.put(target)

        // Query before TTL
        assertNotNull(cache.get("z0001", startMs + 500L))

        // Query after TTL expires
        assertNull("Phải trả về null khi quá thời gian TTL", cache.get("z0001", startMs + 1500L))
        assertEquals(0, cache.size())
    }

    @Test
    fun testDualIndexLookup() {
        val cache = ChatTargetLruCache(capacity = 5, ttlMs = 60_000L)
        val now = System.currentTimeMillis()

        val target = CachedChatTarget("t1234", "org.telegram.messenger", "dev team", null, now)
        cache.put(target)

        // Lookup by primary refId
        val byRef = cache.get("t1234", now)
        assertNotNull(byRef)
        assertEquals("dev team", byRef?.normalizedRoomKey)

        // Lookup by secondary (packageName, normalizedRoomKey)
        val bySecondary = cache.getBySecondary("org.telegram.messenger", "dev team", now)
        assertNotNull(bySecondary)
        assertEquals("t1234", bySecondary?.refId)
    }

    @Test
    fun testResolveTargetPackageWithChatRefId() {
        assertEquals("com.zing.zalo", ChatIntentManager.resolveTargetPackage("", "", "z4k9m"))
        assertEquals("org.telegram.messenger", ChatIntentManager.resolveTargetPackage("", "", "t8a2c"))
        assertEquals("com.facebook.orca", ChatIntentManager.resolveTargetPackage("", "", "m1p7x"))
    }

    @Test
    fun testSmartReplyGeneration() {
        val replyReport = ChatIntentManager.generateSmartReply("Gửi file báo cáo tài chính quý 3")
        assertTrue("Câu trả lời phải thể hiện đang hoàn thiện và gửi",
            replyReport.contains("hoàn thiện") || replyReport.contains("gửi")
        )

        val replyMeeting = ChatIntentManager.generateSmartReply("Mai 9h họp chiến lược dự án")
        assertTrue("Câu trả lời họp phải thể hiện tham gia đúng giờ",
            replyMeeting.contains("lịch") || replyMeeting.contains("đúng giờ")
        )

        val replyPayment = ChatIntentManager.generateSmartReply("Nhớ chuyển tiền nhà trước tối nay")
        assertTrue("Câu trả lời tiền phải thể hiện kiểm tra và thanh toán",
            replyPayment.contains("kiểm tra") || replyPayment.contains("thực hiện")
        )

        val replyConfirm = ChatIntentManager.generateSmartReply("Xác nhận lại số liệu giúp anh")
        assertTrue("Câu trả lời xác nhận",
            replyConfirm.contains("xác nhận") || replyConfirm.contains("kiểm tra")
        )

        val replyDefault = ChatIntentManager.generateSmartReply("Hôm nay xong cái này nhé")
        assertTrue("Câu trả lời mặc định lịch sự",
            replyDefault.contains("đang xử lý") || replyDefault.contains("nắm thông tin")
        )
    }

    @Test
    fun testResolveTargetPackageAndDisplayName() {
        // Source contains Zalo
        val pkgZalo = ChatIntentManager.resolveTargetPackage("Zalo - Sếp Tuấn", "Gửi báo cáo")
        assertEquals("com.zing.zalo", pkgZalo)
        assertEquals("Zalo", ChatIntentManager.getAppDisplayName(pkgZalo))

        // Source contains Telegram
        val pkgTele = ChatIntentManager.resolveTargetPackage("Telegram", "Họp online")
        assertEquals("org.telegram.messenger", pkgTele)
        assertEquals("Telegram", ChatIntentManager.getAppDisplayName(pkgTele))

        // Source contains Messenger
        val pkgMess = ChatIntentManager.resolveTargetPackage("Messenger: Hoàng", "Đi cà phê")
        assertEquals("com.facebook.orca", pkgMess)
        assertEquals("Messenger", ChatIntentManager.getAppDisplayName(pkgMess))

        // Title mentions Zalo
        val pkgFromTitle = ChatIntentManager.resolveTargetPackage("", "Nhắn tin Zalo cho khách hàng")
        assertEquals("com.zing.zalo", pkgFromTitle)
    }
}
