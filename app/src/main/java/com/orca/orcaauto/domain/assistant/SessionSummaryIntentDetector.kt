package com.orca.orcaauto.domain.assistant

import com.orca.orcaauto.data.preferences.SummarySessionConfig

data class SessionSummaryIntent(
    val isSummaryRequest: Boolean,
    val targetSession: SummarySessionConfig? = null,
    val explicitlyNamedSession: Boolean = false
)

/**
 * Pure intent detector for direct user prompts requesting a session/shift summary
 * or targeting an explicitly named session.
 */
object SessionSummaryIntentDetector {

    private val NON_ALNUM_REGEX = Regex("""[^\p{L}\p{N}\s]""")
    private val MULTI_SPACE_REGEX = Regex("""\s+""")
    private val CA_SHIFT_WORD_REGEX = Regex("""\bca(?!\s+nhan\b)\b""")

    private val SUMMARY_TRIGGER_PHRASES = listOf(
        "tom tat",
        "tong hop",
        "bao cao ca",
        "bao cao phien",
        "summarize",
        "summary",
        "recap",
        "diem tin"
    )

    private val NOTE_KEYWORDS = listOf("ghi chu", "note", "tai lieu")

    private val SESSION_OR_CHAT_CONTEXT_KEYWORDS = listOf(
        "phien",
        "tin nhan",
        "zalo",
        "telegram",
        "messenger"
    )

    fun detect(
        userPrompt: String,
        sessions: List<SummarySessionConfig>,
        currentSession: SummarySessionConfig
    ): SessionSummaryIntent {
        val cleanPrompt = normalizeClean(userPrompt)
        if (cleanPrompt.isBlank()) {
            return SessionSummaryIntent(isSummaryRequest = false)
        }

        val padded = " $cleanPrompt "
        val hasSummaryTrigger = SUMMARY_TRIGGER_PHRASES.any { phrase ->
            padded.contains(" $phrase ")
        }
        if (!hasSummaryTrigger) {
            return SessionSummaryIntent(isSummaryRequest = false)
        }

        val hasSessionOrChatKeyword = SESSION_OR_CHAT_CONTEXT_KEYWORDS.any { kw ->
            padded.contains(" $kw ")
        } || CA_SHIFT_WORD_REGEX.containsMatchIn(cleanPrompt)

        val matchedSession = findExplicitSessionFromNormalized(cleanPrompt, sessions)

        val hasNoteKeyword = NOTE_KEYWORDS.any { kw -> padded.contains(" $kw ") }
        if (hasNoteKeyword && !hasSessionOrChatKeyword && matchedSession == null) {
            return SessionSummaryIntent(isSummaryRequest = false)
        }

        if (userPrompt.trim().length > 180 && !hasSessionOrChatKeyword && matchedSession == null) {
            return SessionSummaryIntent(isSummaryRequest = false)
        }

        return SessionSummaryIntent(
            isSummaryRequest = true,
            targetSession = matchedSession ?: currentSession,
            explicitlyNamedSession = matchedSession != null
        )
    }

    fun resolveTargetSession(
        userPrompt: String,
        sessions: List<SummarySessionConfig>,
        fallbackSession: SummarySessionConfig
    ): SummarySessionConfig {
        val cleanPrompt = normalizeClean(userPrompt)
        if (cleanPrompt.isBlank()) return fallbackSession
        return findExplicitSessionFromNormalized(cleanPrompt, sessions) ?: fallbackSession
    }

    private fun findExplicitSessionFromNormalized(
        cleanPrompt: String,
        sessions: List<SummarySessionConfig>
    ): SummarySessionConfig? {
        if (sessions.isEmpty()) return null
        val paddedPrompt = " $cleanPrompt "

        val sortedSessions = sessions.sortedByDescending { it.name.trim().length }
        for (session in sortedSessions) {
            val cleanName = normalizeClean(session.name)
            if (cleanName.length >= 2 && paddedPrompt.contains(" $cleanName ")) {
                return session
            }
        }

        if (paddedPrompt.contains(" phien chinh ") || paddedPrompt.contains(" main session ")) {
            return sessions.firstOrNull { it.isMain }
        }

        return null
    }

    private fun normalizeClean(text: String): String {
        val noAccent = Round1Router.normalizeVietnamese(text)
        return noAccent.replace(NON_ALNUM_REGEX, " ")
            .replace(MULTI_SPACE_REGEX, " ")
            .trim()
    }
}
