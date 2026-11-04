package com.orca.orcaauto.domain.filter

import android.app.Notification
import android.service.notification.StatusBarNotification
import com.orca.orcaauto.data.preferences.ChatFilterMode
import com.orca.orcaauto.data.preferences.UserPreferences

object NotificationFilter {

    // Regex to detect OTP codes
    private val OTP_PATTERNS = listOf(
        Regex("(?i)(mã\\s*xác\\s*thực|mã\\s*otp|mã\\s*xác\\s*nhận|otp\\s*code|verification\\s*code|security\\s*code|mã\\s*giao\\s*dịch|passcode).*?(\\b\\d{4,8}\\b)"),
        Regex("(?i)(\\b\\d{4,8}\\b).*?(là\\s*mã|is\\s*your\\s*code|để\\s*xác\\s*nhận|for\\s*verification)"),
        Regex("(?i)\\b(otp|xác\\s*nhận|xác\\s*thực)\\b.*?\\b\\d{4,8}\\b")
    )

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
        val chatNames = rule.chatNames

        // Record sender/group to recent list if non-empty
        val candidateName = conversationTitle.ifBlank { title }
        if (candidateName.isNotBlank() && candidateName != "Người gửi ẩn danh") {
            preferences.addRecentSender(packageName, candidateName)
        }

        if (chatNames.isEmpty()) {
            // When no rules configured:
            // NORMAL_WITH_BLOCK: allow all
            // STRICT_WHITELIST: allow all until user adds at least one rule
            return true
        }

        val normalizedConvTitle = conversationTitle.trim().lowercase()
        val normalizedTitle = title.trim().lowercase()

        // Check if either conversationTitle or title matches any configured rule name/keyword
        val isMatched = chatNames.any { ruleName ->
            val normalizedRule = ruleName.trim().lowercase()
            if (normalizedRule.isBlank()) false
            else {
                (normalizedConvTitle.isNotBlank() && (normalizedConvTitle == normalizedRule || normalizedConvTitle.contains(normalizedRule))) ||
                (normalizedTitle.isNotBlank() && (normalizedTitle == normalizedRule || normalizedTitle.contains(normalizedRule)))
            }
        }

        return when (rule.mode) {
            ChatFilterMode.NORMAL_WITH_BLOCK -> {
                // If matched in block mode, drop message
                !isMatched
            }
            ChatFilterMode.STRICT_WHITELIST -> {
                // If matched in whitelist mode, permit message
                isMatched
            }
        }
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
