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
            NotificationEntity(id = 1, appPackage = "com.zing.zalo", appName = "Zalo", sender = "S\u1EBFp Tu\u1EA5n", content = "N\u1ED9p b\u00E1o c\u00E1o"),
            NotificationEntity(id = 2, appPackage = "org.telegram.messenger", appName = "Telegram", sender = "Nh\u00F3m B\u1EA1n", content = "\u0110i \u0103n tr\u01B0a"),
            NotificationEntity(id = 3, appPackage = "com.facebook.orca", appName = "Messenger", sender = "Kh\u00E1ch h\u00E0ng", content = "G\u1EEDi b\u00E1o gi\u00E1")
        )

        val filtered = SessionMessageFilter.filter(notifs, mainSession)
        assertEquals(3, filtered.size)
    }

    @Test
    fun testSessionMessageFilter_StrictWhitelist_WildcardMatching() {
        val projectSession = SummarySessionConfig(
            id = "session_proj_1",
            name = "D\u1EF1 \u00C1n Alpha",
            isMain = false,
            filterMode = ChatFilterMode.STRICT_WHITELIST,
            whitelist = setOf("S\u1EBFp*", "*D\u1EF1 \u00C1n Alpha*")
        )

        val notifs = listOf(
            NotificationEntity(id = 1, appPackage = "com.zing.zalo", appName = "Zalo", sender = "S\u1EBFp Tu\u1EA5n", content = "Giao vi\u1EC7c"),
            NotificationEntity(id = 2, appPackage = "com.zing.zalo", appName = "Zalo", sender = "S\u1EBFp T\u1ED5ng", content = "H\u1ECDp g\u1EA5p"),
            NotificationEntity(id = 3, appPackage = "org.telegram.messenger", appName = "Telegram", sender = "Nh\u00F3m D\u1EF1 \u00C1n Alpha 2026", content = "Bug fix"),
            NotificationEntity(id = 4, appPackage = "com.facebook.orca", appName = "Messenger", sender = "B\u1EA1n Th\u00E2n", content = "C\u00E0 ph\u00EA kh\u00F4ng")
        )

        val filtered = SessionMessageFilter.filter(notifs, projectSession)
        assertEquals(3, filtered.size)
        assertTrue(filtered.any { it.sender == "S\u1EBFp Tu\u1EA5n" })
        assertTrue(filtered.any { it.sender == "S\u1EBFp T\u1ED5ng" })
        assertTrue(filtered.any { it.sender == "Nh\u00F3m D\u1EF1 \u00C1n Alpha 2026" })
        assertFalse(filtered.any { it.sender == "B\u1EA1n Th\u00E2n" })
    }

    @Test
    fun testSessionMessageFilter_BanlistTakesPrecedence() {
        val workSession = SummarySessionConfig(
            id = "session_work",
            name = "C\u00F4ng Vi\u1EC7c",
            isMain = false,
            filterMode = ChatFilterMode.NORMAL_WITH_BLOCK,
            banlist = setOf("*Spam*", "Qu\u1EA3ng C\u00E1o*")
        )

        val notifs = listOf(
            NotificationEntity(id = 1, appPackage = "com.zing.zalo", appName = "Zalo", sender = "S\u1EBFp Tu\u1EA5n", content = "H\u1ECDp"),
            NotificationEntity(id = 2, appPackage = "com.zing.zalo", appName = "Zalo", sender = "Nh\u00F3m Spam Ch\u1EE3", content = "B\u00E1n h\u00E0ng"),
            NotificationEntity(id = 3, appPackage = "com.zing.zalo", appName = "Zalo", sender = "Qu\u1EA3ng C\u00E1o Shopee", content = "M\u00E3 gi\u1EA3m gi\u00E1")
        )

        val filtered = SessionMessageFilter.filter(notifs, workSession)
        assertEquals(1, filtered.size)
        assertEquals("S\u1EBFp Tu\u1EA5n", filtered[0].sender)
    }

    @Test
    fun testResolveKeysForSession_PriorityOrderingAndFallbacks() {
        val key1 = ApiKeyConfig(id = "key_gemini", rawKey = "AIzaSy_1", provider = AiProviderType.GEMINI, modelName = "gemini-3.8-flash", endpointUrl = "http://gemini")
        val key2 = ApiKeyConfig(id = "key_nvidia", rawKey = "nvapi_2", provider = AiProviderType.NVIDIA, modelName = "llama-3.1-nemotron-70b", endpointUrl = "http://nvidia")
        val key3 = ApiKeyConfig(id = "key_groq", rawKey = "gsk_3", provider = AiProviderType.GROQ, modelName = "llama-3.3-70b", endpointUrl = "http://groq")
        val globalPool = listOf(key1, key2, key3)

        val sessionWithCustomKeys = SummarySessionConfig(
            id = "sess_1",
            name = "Test",
            keyPriorityIds = listOf("key_nvidia", "key_gemini")
        )

        val resolved = SequentialAiQueue.resolveKeysForSession(sessionWithCustomKeys, globalPool)
        assertEquals(3, resolved.size)
        assertEquals("key_nvidia", resolved[0].id)
        assertEquals("key_gemini", resolved[1].id)
        assertEquals("key_groq", resolved[2].id)
    }

    @Test
    fun testOrderSessionsForBatch_MainFirstThenCreatedAtFifo() {
        val mainSession = SummarySessionConfig.createMainSession()
        val custom1 = SummarySessionConfig(id = "c1", name = "S\u1EBFp", isMain = false, createdAt = 1000L)
        val custom2 = SummarySessionConfig(id = "c2", name = "D\u1EF1 \u00C1n", isMain = false, createdAt = 500L)
        val custom3 = SummarySessionConfig(id = "c3", name = "Gia \u0110\u00ECnh", isMain = false, createdAt = 1500L)

        val unordered = listOf(custom1, custom3, mainSession, custom2)
        val ordered = SequentialAiQueue.orderSessionsForBatch(unordered)

        assertEquals(mainSession.id, ordered[0].id)
        assertEquals("c2", ordered[1].id)
        assertEquals("c1", ordered[2].id)
        assertEquals("c3", ordered[3].id)
    }

    @Test
    fun testSessionMessageFilter_AppMatching() {
        val telegramSession = SummarySessionConfig(
            id = "sess_tele",
            name = "Telegram Only",
            isMain = false,
            filterMode = ChatFilterMode.STRICT_WHITELIST,
            whitelist = setOf("Telegram")
        )

        val notifs = listOf(
            NotificationEntity(id = 1, appPackage = "com.zing.zalo", appName = "Zalo", sender = "Tu\u1EA5n", content = "Tin 1"),
            NotificationEntity(id = 2, appPackage = "org.telegram.messenger", appName = "Telegram", sender = "Alice", content = "Tin 2"),
            NotificationEntity(id = 3, appPackage = "org.telegram.messenger", appName = "Telegram", sender = "Bob", content = "Tin 3")
        )

        val filtered = SessionMessageFilter.filter(notifs, telegramSession)
        assertEquals(2, filtered.size)
        assertTrue(filtered.all { it.appName == "Telegram" })
    }
}
