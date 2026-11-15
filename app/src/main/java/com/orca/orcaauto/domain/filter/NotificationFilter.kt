package com.orca.orcaauto.domain.filter

import android.app.Notification
import android.service.notification.StatusBarNotification
import com.orca.orcaauto.data.preferences.AppFilterRule
import com.orca.orcaauto.data.preferences.ChatFilterMode
import com.orca.orcaauto.data.preferences.UserPreferences
import java.text.Normalizer
import java.util.concurrent.ConcurrentHashMap

object NotificationFilter {

    // In-memory per-package debounce cache to avoid redundant cross-component calls
    private val _recentSendersCache = ConcurrentHashMap<String, String>()

    // Strict 3-tier regex to detect genuine technical OTP messages:
    // Tier 1: Explicit technical OTP / verification keywords
    private val OTP_KEYWORD_REGEX = Regex(
        "(?i)\\b(m[aã]\\s*otp|smart\\s*otp|otp\\s*code|otp|m[aã]\\s*x[aá]c\\s*th[uự]c|m[aã]\\s*x[aá]c\\s*minh|verification\\s*code|security\\s*code|one[- ]time\\s*password)\\b"
    )
    // Tier 2: Isolated numeric code (4 to 8 digits)
    private val OTP_CODE_REGEX = Regex("\\b\\d{4,8}\\b")
    // Tier 3: Short-term validity or secrecy / confidentiality context
    private val OTP_CONTEXT_REGEX = Regex(
        "(?i)(hi[eệ]u\\s*l[uự]c|h[eế]t\\s*h[aạ]n|h[aạ]n\\s*d[uù]ng|kh[oô]ng\\s*(chia\\s*s[eẻ]|cung\\s*c[aấ]p)|tuy[eệ]t\\s*[dđ][oố]i\\s*kh[oô]ng|b[aả]o\\s*m[aậ]t|m[aã]\\s*b[ií]\\s*m[aậ]t|ch[iỉ]\\s*d[uù]ng\\s*(1|m[oộ]t)\\s*l[aầ]n|valid\\s*for|expires?|do\\s*not\\s*share|never\\s*share|confidential)"
    )

    // Regex to detect system command syntax & prompt injection attempts
    private val COMMAND_INJECTION_PATTERNS = listOf(
        // Assistant note command syntax: [SHOW_NOTE:...], [CREATE:...], [APPEND:...], [UPDATE:...], [DELETE:...], [DELETE_MSG:...], [TICKET:...]
        Regex("""(?i)\[(SHOW_NOTE|CREATE|APPEND|UPDATE|DELETE|DELETE_MSG|TICKET)\s*[:|]"""),
        // Thought / reasoning / system prompt injection tags
        Regex("""(?i)<\/?(scratchpad|think|system|prompt_injection|instruction)>"""),
        Regex("""(?i)\[(SYSTEM|PROMPT_INJECTION)\]""")
    )

    private val WHITESPACE_REGEX = Regex("\\s+")

    /**
     * Checks if a given text contains system command syntax or prompt injection attempts.
     */
    fun containsSystemCommandInjection(text: String): Boolean {
        if (text.isBlank()) return false
        return COMMAND_INJECTION_PATTERNS.any { it.containsMatchIn(text) }
    }

    /**
     * Determine if a StatusBarNotification should be recorded.
     * Returns true if it is a genuine, actionable human text message.
     */
    fun shouldRecord(sbn: StatusBarNotification, preferences: UserPreferences): Boolean {
        val packageName = sbn.packageName ?: return false

        // Check if package is monitored and enabled by user
        if (!preferences.isAppMonitored(packageName)) {
            return false
        }

        val notification = sbn.notification ?: return false

        // 1. Ignore Ongoing notifications (downloads, active calls, media players, background sync)
        val flags = notification.flags
        if ((flags and Notification.FLAG_ONGOING_EVENT) != 0 ||
            (flags and Notification.FLAG_FOREGROUND_SERVICE) != 0 ||
            (flags and Notification.FLAG_LOCAL_ONLY) != 0
        ) {
            return false
        }

        // 2. Extract conversationTitle, subText, title & text
        val extras = notification.extras ?: return false
        val conversationTitle = extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString()?.trim().orEmpty()
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()?.trim().orEmpty()
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim().orEmpty()

        val fullContent = if (bigText.isNotBlank()) bigText else text

        // Must have non-empty text content
        if (fullContent.isBlank()) {
            return false
        }

        // 3. Filter out OTP codes
        if (isOtpMessage(fullContent) || isOtpMessage(title)) {
            return false
        }

        // 3.1. Defense mechanism: Drop incoming notification immediately if it contains command calling syntax or injection tags
        if (containsSystemCommandInjection(fullContent) || containsSystemCommandInjection(title)) {
            return false
        }

        // 4. Personalized Chat Filter (Blacklist vs Whitelist with Asterisk Wildcard support)
        if (!isChatAllowed(packageName, conversationTitle, subText, title, preferences)) {
            return false
        }

        return true
    }

    /**
     * Checks if the specific chat or sender is permitted by the app's filter rules.
     * Also saves recent senders/groups to preferences for quick setup in Settings UI.
     */
    fun isChatAllowed(
        packageName: String,
        conversationTitle: String,
        subText: String,
        title: String,
        preferences: UserPreferences
    ): Boolean {
        val rule = preferences.getAppFilterRule(packageName)

        // Record sender/group to recent list if non-empty (prioritize conversationTitle -> subText -> title)
        val candidateName = conversationTitle.ifBlank { subText }.ifBlank { title }
        if (candidateName.isNotBlank() && candidateName != "Người gửi ẩn danh") {
            val lastSender = _recentSendersCache[packageName]
            if (!candidateName.equals(lastSender, ignoreCase = true)) {
                _recentSendersCache[packageName] = candidateName
                preferences.addRecentSender(packageName, candidateName)
            }
        }

        return evaluateRule(rule, conversationTitle, subText, title)
    }

    /**
     * Backward-compatible overload for 2-title calls.
     */
    fun isChatAllowed(
        packageName: String,
        conversationTitle: String,
        title: String,
        preferences: UserPreferences
    ): Boolean {
        return isChatAllowed(packageName, conversationTitle, "", title, preferences)
    }

    /**
     * Pure evaluation logic for AppFilterRule supporting Asterisk (*) wildcards and exact matching.
     * Guaranteed crash-proof against extreme inputs, regex characters, unicode NFC/NFD variations,
     * and whitespace anomalies.
     */
    fun evaluateRule(
        rule: AppFilterRule,
        conversationTitle: String,
        subText: String = "",
        title: String = ""
    ): Boolean {
        val chatNames = rule.chatNames

        if (chatNames.isEmpty()) {
            return when (rule.mode) {
                ChatFilterMode.NORMAL_WITH_BLOCK -> true  // No blocklist rules -> allow all
                ChatFilterMode.STRICT_WHITELIST -> false  // Empty whitelist -> block all until configured
            }
        }

        val normalizedConv = normalize(conversationTitle)
        val normalizedSub = normalize(subText)
        val normalizedTitle = normalize(title)

        // Check if ANY of conversationTitle, subText, or title matches ANY configured rule
        val isMatched = chatNames.any { ruleName ->
            val normalizedRule = normalize(ruleName)
            if (normalizedRule.isBlank()) false
            else {
                (normalizedConv.isNotBlank() && matchesGlobPattern(normalizedConv, normalizedRule)) ||
                (normalizedSub.isNotBlank() && matchesGlobPattern(normalizedSub, normalizedRule)) ||
                (normalizedTitle.isNotBlank() && matchesGlobPattern(normalizedTitle, normalizedRule))
            }
        }

        return when (rule.mode) {
            ChatFilterMode.NORMAL_WITH_BLOCK -> !isMatched
            ChatFilterMode.STRICT_WHITELIST -> isMatched
        }
    }

    /**
     * Matches text against pattern using Asterisk (*) wildcard rules:
     * - Pattern with NO '*' (e.g. "chi"): EXACT MATCH (`text == pattern`).
     * - Pattern wrapped in '*' (e.g. "*dự án orca*"): CONTAINS (`text.contains("dự án orca")`).
     * - Pattern ending with '*' (e.g. "sếp*"): STARTS WITH (`text.startsWith("sếp")`).
     * - Pattern starting with '*' (e.g. "*tuấn"): ENDS WITH (`text.endsWith("tuấn")`).
     * - Arbitrary glob (e.g. "dự*orca"): Translates '*' to '.*' regex safely.
     */
    fun matchesGlobPattern(text: String, pattern: String): Boolean {
        if (text == pattern) return true
        if (!pattern.contains('*')) {
            return text == pattern
        }

        val rawClean = pattern.replace("*", "").trim()
        if (rawClean.isBlank()) return false // Pattern was just "*" or "***"

        return try {
            val parts = pattern.split('*')
            val regexPattern = parts.joinToString(separator = ".*") { Regex.escape(it) }
            Regex(regexPattern, setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).matches(text)
        } catch (_: Throwable) {
            text.contains(rawClean, ignoreCase = true)
        }
    }

    /**
     * Normalizes text to NFC form, collapses arbitrary whitespace, and converts to lowercase.
     */
    fun normalize(text: String): String {
        if (text.isBlank()) return ""
        return Normalizer.normalize(text.trim(), Normalizer.Form.NFC)
            .lowercase()
            .replace(WHITESPACE_REGEX, " ")
    }

    internal fun isOtpMessage(content: String): Boolean {
        if (content.isBlank()) return false
        return OTP_KEYWORD_REGEX.containsMatchIn(content) &&
                OTP_CODE_REGEX.containsMatchIn(content) &&
                OTP_CONTEXT_REGEX.containsMatchIn(content)
    }
}
