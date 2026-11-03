package com.orca.orcaauto.domain.filter

import android.app.Notification
import android.service.notification.StatusBarNotification
import com.orca.orcaauto.data.preferences.UserPreferences

object NotificationFilter {

    // Regex to detect OTP codes
    private val OTP_PATTERNS = listOf(
        Regex("(?i)(mã\\s*xác\\s*thực|mã\\s*otp|mã\\s*xác\\s*nhận|otp\\s*code|verification\\s*code|security\\s*code|mã\\s*giao\\s*dịch|passcode).*?(\\b\\d{4,8}\\b)"),
        Regex("(?i)(\\b\\d{4,8}\\b).*?(là\\s*mã|is\\s*your\\s*code|để\\s*xác\\s*nhận|for\\s*verification)"),
        Regex("(?i)\\b(otp|xác\\s*nhận|xác\\s*thực)\\b.*?\\b\\d{4,8}\\b")
    )

    // Keywords indicating system / junk / promotional messages
    private val SPAM_KEYWORDS = listOf(
        "khuyến mãi", "ưu đãi đặc biệt", "nhận ngay voucher", "giảm giá tới",
        "nạp thẻ cào", "vay tiêu dùng", "quảng cáo", "livestream đang diễn ra",
        "tham gia minigame", "săn deal", "voucher sốc", "đang đồng bộ tin nhắn",
        "đang tải", "checking for messages", "syncing"
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

        // 2. Extract title & text
        val extras = notification.extras ?: return false
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim().orEmpty()

        val fullContent = if (bigText.isNotBlank()) bigText else text

        // Must have non-empty text content
        if (fullContent.isBlank()) {
            return false
        }

        // 3. Filter out system/app progress messages (e.g. "Đang tải ảnh...", "Cuộc gọi nhỡ")
        if (isSpamOrSystem(title, fullContent)) {
            return false
        }

        // 4. Filter out OTP codes
        if (isOtpMessage(fullContent) || isOtpMessage(title)) {
            return false
        }

        return true
    }

    private fun isOtpMessage(content: String): Boolean {
        for (pattern in OTP_PATTERNS) {
            if (pattern.containsMatchIn(content)) {
                return true
            }
        }
        return false
    }

    private fun isSpamOrSystem(title: String, content: String): Boolean {
        val combined = "$title $content".lowercase()
        return SPAM_KEYWORDS.any { combined.contains(it) }
    }
}
