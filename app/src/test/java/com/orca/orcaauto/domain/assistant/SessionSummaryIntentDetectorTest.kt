package com.orca.orcaauto.domain.assistant

import com.orca.orcaauto.data.preferences.SummarySessionConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionSummaryIntentDetectorTest {

    private val mainSession = SummarySessionConfig.createMainSession()
    private val workSession = SummarySessionConfig(
        id = "session_work",
        name = "Công việc"
    )
    private val alphaProjectSession = SummarySessionConfig(
        id = "session_work_alpha",
        name = "Công việc Dự Án Alpha"
    )
    private val sessions = listOf(mainSession, workSession, alphaProjectSession)

    @Test
    fun detect_genericSummaryPrompts_returnsCurrentSession() {
        val prompts = listOf(
            "tóm tắt đi",
            "tổng hợp tin nhắn hôm nay",
            "báo cáo ca",
            "summarize",
            "recap tin nhắn vừa rồi"
        )

        for (prompt in prompts) {
            val result = SessionSummaryIntentDetector.detect(prompt, sessions, workSession)
            assertTrue("Expected summary intent for '$prompt'", result.isSummaryRequest)
            assertEquals("Expected active fallback session for '$prompt'", workSession.id, result.targetSession?.id)
            assertFalse("Did not expect explicitlyNamedSession for '$prompt'", result.explicitlyNamedSession)
        }
    }

    @Test
    fun detect_explicitSessionName_accentInsensitiveAndLongestNameWins() {
        val longestMatch = SessionSummaryIntentDetector.detect(
            "tóm tắt cong viec du an alpha giúp tôi",
            sessions,
            mainSession
        )
        assertTrue(longestMatch.isSummaryRequest)
        assertEquals(alphaProjectSession.id, longestMatch.targetSession?.id)
        assertTrue(longestMatch.explicitlyNamedSession)

        val shorterMatch = SessionSummaryIntentDetector.detect(
            "tổng hợp Công việc hôm nay",
            sessions,
            mainSession
        )
        assertTrue(shorterMatch.isSummaryRequest)
        assertEquals(workSession.id, shorterMatch.targetSession?.id)
        assertTrue(shorterMatch.explicitlyNamedSession)
    }

    @Test
    fun detect_explicitMainSessionAliases_matchMainSession() {
        val viMain = SessionSummaryIntentDetector.detect(
            "tóm tắt lại phiên chính giúp tôi",
            sessions,
            workSession
        )
        assertTrue(viMain.isSummaryRequest)
        assertEquals(mainSession.id, viMain.targetSession?.id)
        assertTrue(viMain.explicitlyNamedSession)

        val enMain = SessionSummaryIntentDetector.detect(
            "summary main session",
            sessions,
            workSession
        )
        assertTrue(enMain.isSummaryRequest)
        assertEquals(mainSession.id, enMain.targetSession?.id)
        assertTrue(enMain.explicitlyNamedSession)
    }

    @Test
    fun detect_negativeGuards_rejectNoteAndLongArticleSummariesWithoutSessionContext() {
        val viNote = SessionSummaryIntentDetector.detect(
            "tóm tắt ghi chú cá nhân",
            sessions,
            workSession
        )
        assertFalse(viNote.isSummaryRequest)

        val enNote = SessionSummaryIntentDetector.detect(
            "summarize this note",
            sessions,
            workSession
        )
        assertFalse(enNote.isSummaryRequest)

        val longArticlePrompt = "tóm tắt đoạn văn sau đây giúp tôi nhé: " + "a ".repeat(110)
        val longResult = SessionSummaryIntentDetector.detect(longArticlePrompt, sessions, workSession)
        assertFalse(longResult.isSummaryRequest)
    }

    @Test
    fun detect_noteWordAlongsideSessionOrChatContext_allowsSummaryRequest() {
        val withSessionName = SessionSummaryIntentDetector.detect(
            "tóm tắt phiên Công việc và ghi chú liên quan",
            sessions,
            mainSession
        )
        assertTrue(withSessionName.isSummaryRequest)
        assertEquals(workSession.id, withSessionName.targetSession?.id)
        assertTrue(withSessionName.explicitlyNamedSession)

        val withChatContext = SessionSummaryIntentDetector.detect(
            "tóm tắt tin nhắn zalo và ghi chú",
            sessions,
            workSession
        )
        assertTrue(withChatContext.isSummaryRequest)
        assertEquals(workSession.id, withChatContext.targetSession?.id)
        assertFalse(withChatContext.explicitlyNamedSession)
    }

    @Test
    fun resolveTargetSession_returnsMatchedOrFallback() {
        val resolved = SessionSummaryIntentDetector.resolveTargetSession(
            "kiểm tra giúp Công việc",
            sessions,
            mainSession
        )
        assertEquals(workSession.id, resolved.id)

        val fallback = SessionSummaryIntentDetector.resolveTargetSession(
            "hôm nay có gì mới",
            sessions,
            workSession
        )
        assertEquals(workSession.id, fallback.id)
    }
}
