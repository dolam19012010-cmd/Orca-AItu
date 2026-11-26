package com.orca.orcaauto.domain.filter

object PiiSanitizer {

    // Email address with explicit @domain.tld
    private val EMAIL_REGEX = Regex("""\b[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}\b""")

    // Regex for credit cards / bank card patterns (13-19 digits)
    private val CARD_REGEX = Regex("\\b(?:\\d[ -]*?){13,19}\\b")

    // Regex for banking account mention (explicit stk / tài khoản label)
    private val BANK_ACCOUNT_REGEX = Regex("(?i)(stk|số\\s*tài\\s*khoản|tài\\s*khoản)[:\\s]*([0-9]{6,16})")

    // CCCD / CMND: STRICTLY requires explicit ID keyword before 9 or 12 digits
    private val CCCD_CMND_REGEX = Regex(
        """(?i)(?<![\p{L}\p{N}])(cccd|cmnd|c[aă]n\s*c[uư][oớ]c(?:\s*c[oô]ng\s*d[aâ]n)?|ch[uứ]ng\s*minh\s*(?:nh[aâ]n\s*d[aâ]n|th[uư])|s[oố]\s*[đd][iị]nh\s*danh(?:\s*c[aá]\s*nh[aâ]n)?)(?![\p{L}\p{N}])(?:\s*(?:s[oố]|l[aà]|c[uũ]|m[oớ]i|c[uủ]a\s*(?:em|anh|ch[iị]|m[iì]nh|t[oô]i|kh[aá]ch)|g[aắ]n\s*chip))*\s*[:=\-]?\s*(\d{9}|\d{12})(?![\p{L}\p{N}])"""
    )

    // OTP / PIN: STRICTLY requires explicit OTP/PIN/verification keyword AND code containing >= 1 digit
    private val OTP_PIN_FORWARD_REGEX = Regex(
        """(?i)(?<![\p{L}\p{N}])(smart\s*otp|m[aã]\s*otp|otp|m[aã]\s*x[aá]c\s*(?:th[uự]c|minh)|verification\s*code|security\s*code|m[aã]\s*pin|pin\s*code|passcode)(?![\p{L}\p{N}])(?:\s*(?:c[uủ]a\s*(?:b[aạ]n|anh|ch[iị]|em|t[oô]i)|giao\s*d[iị]ch|t[aà]i\s*kho[aả]n))?\s*(?:l[aà]\s*|[:=\-]\s*|\s+)((?=[A-Za-z0-9]*\d)[A-Za-z0-9]{4,8})(?![\p{L}\p{N}])"""
    )
    private val OTP_PIN_REVERSE_REGEX = Regex(
        """(?i)(?<![\p{L}\p{N}])(\d{4,8})\s+l[aà]\s+(m[aã]\s*otp|smart\s*otp|otp|m[aã]\s*x[aá]c\s*(?:th[uự]c|minh))(?![\p{L}\p{N}])"""
    )

    // Password: STRICTLY requires explicit delimiter (':', '=', 'là')
    private val PASSWORD_REGEX = Regex(
        """(?i)(?<![\p{L}\p{N}])(m[aậ]t\s*kh[aẩ]u(?:\s*(?:wifi|app|t[aà]i\s*kho[aả]n|[đd][aă]ng\s*nh[aậ]p))?|password|pass\s*wifi)(?![\p{L}\p{N}])\s*(?::|=|(?<![\p{L}\p{N}])l[aà](?![\p{L}\p{N}]))\s*([^\s,;.!?]{4,32})"""
    )
    private val NON_SECRET_PASSWORD_WORDS = setOf(
        "gì", "gi", "nào", "nao", "đâu", "dau", "đây", "day", "vậy", "vay",
        "chưa", "chua", "rồi", "roi", "sai", "đúng", "dung", "mới", "moi"
    )

    // Regex for Vietnamese phone numbers
    private val PHONE_REGEX = Regex("(?i)\\b(0|\\+84)[35789]\\d{8}\\b")

    /**
     * Sanitizes sensitive PII (Personally Identifiable Information)
     * before persisting or transmitting to external LLM APIs, using strict
     * contextual markers so ordinary numbers, order IDs, and times are preserved.
     */
    fun sanitize(rawText: String): String {
        var result = rawText

        // 1. Mask email addresses
        result = EMAIL_REGEX.replace(result) { "[EMAIL ĐÃ CHE]" }

        // 2. Mask explicit banking accounts BEFORE bank cards so 13-16 digit STK isn't mislabeled as a card
        result = BANK_ACCOUNT_REGEX.replace(result) { match ->
            "${match.groupValues[1]}: [STK ĐÃ CHE]"
        }

        // 3. Mask bank card numbers
        result = CARD_REGEX.replace(result) { match ->
            val digits = match.value.replace(Regex("[^0-9]"), "")
            if (digits.length >= 13) {
                "[THẺ NGÂN HÀNG: ***${digits.takeLast(4)}]"
            } else {
                match.value
            }
        }

        // 4. Mask CCCD / CMND (strictly keyword-anchored)
        result = CCCD_CMND_REGEX.replace(result) { match ->
            "${match.groupValues[1]}: [CCCD/CMND ĐÃ CHE]"
        }

        // 5. Mask OTP / PIN codes (strictly keyword-anchored with digit check)
        result = OTP_PIN_FORWARD_REGEX.replace(result) { match ->
            "${match.groupValues[1]}: [MÃ BẢO MẬT ĐÃ CHE]"
        }
        result = OTP_PIN_REVERSE_REGEX.replace(result) { match ->
            "[MÃ BẢO MẬT ĐÃ CHE] là ${match.groupValues[2]}"
        }

        // 6. Mask explicit password / pass wifi assignments
        result = PASSWORD_REGEX.replace(result) { match ->
            val candidate = match.groupValues[2]
            if (candidate.lowercase() in NON_SECRET_PASSWORD_WORDS) {
                match.value
            } else {
                "${match.groupValues[1]}: [MẬT KHẨU ĐÃ CHE]"
            }
        }

        // 7. Mask phone numbers
        result = PHONE_REGEX.replace(result) { match ->
            val phone = match.value
            "${phone.take(3)}****${phone.takeLast(3)}"
        }

        return result
    }
}
