package com.orca.orcaauto.domain.filter

object PiiSanitizer {

    // Regex for credit cards / bank card patterns
    private val CARD_REGEX = Regex("\\b(?:\\d[ -]*?){13,19}\\b")

    // Regex for Vietnamese phone numbers
    private val PHONE_REGEX = Regex("(?i)\\b(0|\\+84)[35789]\\d{8}\\b")

    // Regex for banking account mention
    private val BANK_ACCOUNT_REGEX = Regex("(?i)(stk|số\\s*tài\\s*khoản|tài\\s*khoản)[:\\s]*([0-9]{6,16})")

    /**
     * Sanitizes sensitive PII (Personally Identifiable Information)
     * before persisting or transmitting to external LLM APIs.
     */
    fun sanitize(rawText: String): String {
        var result = rawText

        // Mask bank card numbers
        result = CARD_REGEX.replace(result) { match ->
            val digits = match.value.replace(Regex("[^0-9]"), "")
            if (digits.length >= 13) {
                "[THẺ NGÂN HÀNG: ***${digits.takeLast(4)}]"
            } else {
                match.value
            }
        }

        // Mask bank accounts
        result = BANK_ACCOUNT_REGEX.replace(result) { match ->
            "${match.groupValues[1]}: [STK ĐÃ CHE]"
        }

        // Mask phone numbers
        result = PHONE_REGEX.replace(result) { match ->
            val phone = match.value
            "${phone.take(3)}****${phone.takeLast(3)}"
        }

        return result
    }
}
