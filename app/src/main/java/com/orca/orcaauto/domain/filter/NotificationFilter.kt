package com.orca.orcaauto.domain.filter

import android.app.Notification
import android.service.notification.StatusBarNotification
import com.orca.orcaauto.data.preferences.AppFilterRule
import com.orca.orcaauto.data.preferences.ChatFilterMode
import com.orca.orcaauto.data.preferences.UserPreferences
import java.text.Normalizer

object NotificationFilter {

    // Regex to detect OTP codes
    private val OTP_PATTERNS = listOf(
        Regex("(?i)(mã\\s*xác\\s*thực|mã\\s*otp|mã\\s*xác\\s*nhận|otp\\s*code|verification\\s*code|security\\s*code|mã\\s*giao\\s*dịch|passcode).*?(\\b\\d{4,8}\\b)"),
        Regex("(?i)(\\b\\d{4,8}\\b).*?(là\\s*mã|is\\s*your\\s*code|để\\s*xác\\s*nhận|for\\s*verification)"),
        Regex("(?i)\\b(otp|xác\\s*nhận|xác\\s*thực)\\b.*?\\b\\d{4,8}\\b")
    )

    private val WHITESPACE_REGEX = Regex("\\s+")

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

        // 2. Extract title, conversationTitle & text
        val extras = notification.extras ?: return false
        val conversationTitle = extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString()?.trim().orEmpty()
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

        // 4. Personalized Chat Filter (Blacklist vs Whitelist)
        if (!isChatAllowed(packageName, conversationTitle, title, preferences)) {
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
        title: String,
        preferences: UserPreferences
    ): Boolean {
        val rule = preferences.getAppFilterRule(packageName)

        // Record sender/group to recent list if non-empty
        val candidateName = conversationTitle.ifBlank { title }
        if (candidateName.isNotBlank() && candidateName != "Người gửi ẩn danh") {
            preferences.addRecentSender(packageName, candidateName)
        }

        return evaluateRule(rule, conversationTitle, title)
    }

    /**
     * Pure evaluation logic for AppFilterRule.
     * Guaranteed crash-proof against extreme inputs, regex characters, unicode NFC/NFD variations,
     * and whitespace anomalies.
     */
    fun evaluateRule(
        rule: AppFilterRule,
        conversationTitle: String,
        title: String
    ): Boolean {
        val chatNames = rule.chatNames

        if (chatNames.isEmpty()) {
            return when (rule.mode) {
                ChatFilterMode.NORMAL_WITH_BLOCK -> true  // No blocklist rules -> allow all
                ChatFilterMode.STRICT_WHITELIST -> false  // Empty whitelist -> block all until configured
            }
        }

        val normalizedConvTitle = normalize(conversationTitle)
        val normalizedTitle = normalize(title)

        // Check if either conversationTitle or title matches any configured rule name/keyword
        val isMatched = chatNames.any { ruleName ->
            val normalizedRule = normalize(ruleName)
            if (normalizedRule.isBlank()) false
            else {
                (normalizedConvTitle.isNotBlank() && (normalizedConvTitle == normalizedRule || normalizedConvTitle.contains(normalizedRule))) ||
                (normalizedTitle.isNotBlank() && (normalizedTitle == normalizedRule || normalizedTitle.contains(normalizedRule)))
            }
        }

        return when (rule.mode) {
            ChatFilterMode.NORMAL_WITH_BLOCK -> !isMatched
            ChatFilterMode.STRICT_WHITELIST -> isMatched
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

    private fun isOtpMessage(content: String): Boolean {
        for (pattern in OTP_PATTERNS) {
            if (pattern.containsMatchIn(content)) {
                return true
            }
        }
        return false
    }
}
