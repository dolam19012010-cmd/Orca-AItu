package com.orca.orcaauto.domain.ai

import com.orca.orcaauto.data.local.NotificationEntity
import com.orca.orcaauto.data.preferences.ChatFilterMode
import com.orca.orcaauto.data.preferences.SummarySessionConfig
import com.orca.orcaauto.data.remote.ApiKeyConfig
import com.orca.orcaauto.data.remote.AiProviderType
import com.orca.orcaauto.domain.filter.SessionMessageFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SequentialAiQueueAndSessionFilterTest {

    @Test
    fun testSessionMessageFilter_MainSession_AllowsAll() {
        val mainSession = SummarySessionConfig.createMainSession()
        val notifs = listOf(
            NotificationEntity(id = 1, appPackage = "com.zing.zalo", appName = "Zalo", sender = "Sếp Tuấn", content = "Nộp báo cáo"),
            NotificationEntity(id = 2, appPackage = "org.telegram.messenger", appName = "Telegram", sender = "Nhóm Bạn", content = "Đi ăn trưa"),
            NotificationEntity(id = 3, appPackage = "com.facebook.orca", appName = "Messenger", sender = "Khách hàng", content = "Gửi báo giá")
        )

        val filtered = SessionMessageFilter.filter(notifs, mainSession)
        assertEquals(3, filtered.size)
    }

    @Test
    fun testSessionMessageFilter_StrictWhitelist_WildcardMatching() {
        val projectSession = SummarySessionConfig(
            id = "session_proj_1",
            name = "Dự Án Alpha",
            isMain = false,
            filterMode = ChatFilterMode.STRICT_WHITELIST,
            whitelist = setOf("Sếp*", "*Dự Án Alpha*")
        )

        val notifs = listOf(
            NotificationEntity(id = 1, appPackage = "com.zing.zalo", appName = "Zalo", sender = "Sếp Tuấn", content = "Giao việc"),
            NotificationEntity(id = 2, appPackage = "com.zing.zalo", appName = "Zalo", sender = "Sếp Tổng", content = "Họp gấp"),
            NotificationEntity(id = 3, appPackage = "org.telegram.messenger", appName = "Telegram", sender = "Nhóm Dự Án Alpha 2026", content = "Bug fix"),
            NotificationEntity(id = 4, appPackage = "com.facebook.orca", appName = "Messenger", sender = "Bạn Thân", content = "Cà phê không")
        )

        val filtered = SessionMessageFilter.filter(notifs, projectSession)
        assertEquals(3, filtered.size)
        assertTrue(filtered.any { it.sender == "Sếp Tuấn" })
        assertTrue(filtered.any { it.sender == "Sếp Tổng" })
        assertTrue(filtered.any { it.sender == "Nhóm Dự Án Alpha 2026" })
        assertFalse(filtered.any { it.sender == "Bạn Thân" })
    }

    @Test
    fun testSessionMessageFilter_BanlistTakesPrecedence() {
        val workSession = SummarySessionConfig(
            id = "session_work",
            name = "Công Việc",
            isMain = false,
            filterMode = ChatFilterMode.NORMAL_WITH_BLOCK,
            banlist = setOf("*Spam*", "Quảng Cáo*")
        )

        val notifs = listOf(
            NotificationEntity(id = 1, appPackage = "com.zing.zalo", appName = "Zalo", sender = "Sếp Tuấn", content = "Họp"),
            NotificationEntity(id = 2, appPackage = "com.zing.zalo", appName = "Zalo", sender = "Nhóm Spam Chợ", content = "Bán hàng"),
            NotificationEntity(id = 3, appPackage = "com.zing.zalo", appName = "Zalo", sender = "Quảng Cáo Shopee", content = "Mã giảm giá")
        )

        val filtered = SessionMessageFilter.filter(notifs, workSession)
        assertEquals(1, filtered.size)
        assertEquals("Sếp Tuấn", filtered[0].sender)
    }

    @Test
    fun testResolveKeysForSession_PriorityOrderingAndFallbacks() {
        val key1 = ApiKeyConfig(id = "key_gemini", rawKey = "AIzaSy_1", provider = AiProviderType.GEMINI, modelName = "gemini-3.8-flash", endpointUrl = "http://gemini")
        val key2 = ApiKeyConfig(id = "key_nvidia", rawKey = "nvapi_2", provider = AiProviderType.NVIDIA, modelName = "llama-3.1-nemotron-70b", endpointUrl = "http://nvidia")
        val key3 = ApiKeyConfig(id = "key_groq", rawKey = "gsk_3", provider = AiProviderType.GROQ, modelName = "llama-3.3-70b", endpointUrl = "http://groq")
        val globalPool = listOf(key1, key2, key3)

        // Session prioritizes NVIDIA (Key 2) first, then Gemini (Key 1)
        val sessionWithCustomKeys = SummarySessionConfig(
            id = "sess_1",
            name = "Test",
            keyPriorityIds = listOf("key_nvidia", "key_gemini")
        )

        val resolved = SequentialAiQueue.resolveKeysForSession(sessionWithCustomKeys, globalPool)
        assertEquals(3, resolved.size)
        assertEquals("key_nvidia", resolved[0].id)
        assertEquals("key_gemini", resolved[1].id)
        assertEquals("key_groq", resolved[2].id) // Fallback backup
    }

    @Test
    fun testOrderSessionsForBatch_MainFirstThenCreatedAtFifo() {
        val mainSession = SummarySessionConfig.createMainSession()
        val custom1 = SummarySessionConfig(id = "c1", name = "Sếp", isMain = false, createdAt = 1000L)
        val custom2 = SummarySessionConfig(id = "c2", name = "Dự Án", isMain = false, createdAt = 500L)
        val custom3 = SummarySessionConfig(id = "c3", name = "Gia Đình", isMain = false, createdAt = 1500L)

        val unordered = listOf(custom1, custom3, mainSession, custom2)
        val ordered = SequentialAiQueue.orderSessionsForBatch(unordered)

        // Main session first, then custom2 (500L), then custom1 (1000L), then custom3 (1500L)
        assertEquals(mainSession.id, ordered[0].id)
        assertEquals("c2", ordered[1].id)
        assertEquals("c1", ordered[2].id)
        assertEquals("c3", ordered[3].id)
    }
}
