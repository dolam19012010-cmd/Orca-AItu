package com.orca.orcaauto.domain.filter

import com.orca.orcaauto.data.preferences.AppFilterRule
import com.orca.orcaauto.data.preferences.ChatFilterMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Extreme, adversarial, and edge-case unit test suite for Personalized Chat Filtering.
 * Tests Asterisk (*) Wildcards, Exact Matching, SubText extraction, Unicode NFC/NFD variations,
 * and boundary conditions.
 */
class PersonalizedChatFilterExtremeTest {

    // =========================================================================
    // 1. EXACT MATCHING WITHOUT ASTERISK (Quy tắc không có dấu sao: Khớp chính xác tuyệt đối)
    // =========================================================================

    @Test
    fun testExactMatchingWithoutAsteriskDoesNotMatchCompoundWords() {
        // Block contact exactly named "Chi"
        val rule = AppFilterRule(
            appPackage = "com.zing.zalo",
            mode = ChatFilterMode.NORMAL_WITH_BLOCK,
            chatNames = setOf("Chi")
        )

        // Exact name "Chi" -> MUST BE BLOCKED
        assertFalse("Tên chính xác 'Chi' phải bị chặn",
            NotificationFilter.evaluateRule(rule, conversationTitle = "", subText = "", title = "Chi")
        )

        // Compound chat "Chi phí công ty" -> MUST NOT BE BLOCKED!
        assertTrue("Nhóm 'Chi phí công ty' không được phép bị chặn nhầm",
            NotificationFilter.evaluateRule(rule, conversationTitle = "Chi phí công ty", subText = "", title = "Kế toán")
        )

        // Name "Nguyễn Kim Chi" -> MUST NOT BE BLOCKED (because user only typed "Chi", not "*Chi*")
        assertTrue("Người tên 'Nguyễn Kim Chi' không bị chặn khi chỉ định 'Chi'",
            NotificationFilter.evaluateRule(rule, conversationTitle = "", subText = "", title = "Nguyễn Kim Chi")
        )
    }

    // =========================================================================
    // 2. ASTERISK WILDCARD MATCHING (*Chi*, *Dự Án*, Sếp*, *Tuấn)
    // =========================================================================

    @Test
    fun testWrappedAsteriskMatchesAnythingContainingPhraseRegardlessOfEmojis() {
        val rule = AppFilterRule(
            appPackage = "com.zing.zalo",
            mode = ChatFilterMode.NORMAL_WITH_BLOCK,
            chatNames = setOf("*Dự Án Orca*")
        )

        // Group decorated with emojis at both ends
        val fancyGroup = "🔥 [DEV TEAM] Dự Án Orca 2026 🚀"
        assertFalse("Dấu sao * ở 2 đầu phải bắt trọn nhóm có icon lửa và tên lửa",
            NotificationFilter.evaluateRule(rule, conversationTitle = fancyGroup, subText = "", title = "Admin")
        )

        // Clean group
        assertFalse("Chuỗi thuần cũng phải khớp",
            NotificationFilter.evaluateRule(rule, conversationTitle = "Dự Án Orca", subText = "", title = "Admin")
        )

        // Unrelated group
        assertTrue("Nhóm khác không được dính",
            NotificationFilter.evaluateRule(rule, conversationTitle = "Dự Án Khác", subText = "", title = "Admin")
        )
    }

    @Test
    fun testPrefixAndSuffixAsteriskWildcards() {
        // Rule: Sếp* (Bắt đầu bằng chữ Sếp)
        val prefixRule = AppFilterRule(
            appPackage = "com.zing.zalo",
            mode = ChatFilterMode.STRICT_WHITELIST,
            chatNames = setOf("Sếp*")
        )
        assertTrue(NotificationFilter.evaluateRule(prefixRule, "", "", "Sếp Tuấn"))
        assertTrue(NotificationFilter.evaluateRule(prefixRule, "", "", "Sếp Tổng"))
        assertFalse(NotificationFilter.evaluateRule(prefixRule, "", "", "Trợ lý của Sếp"))

        // Rule: *Tuấn (Kết thúc bằng chữ Tuấn)
        val suffixRule = AppFilterRule(
            appPackage = "com.zing.zalo",
            mode = ChatFilterMode.STRICT_WHITELIST,
            chatNames = setOf("*Tuấn")
        )
        assertTrue(NotificationFilter.evaluateRule(suffixRule, "", "", "Nguyễn Văn Tuấn"))
        assertTrue(NotificationFilter.evaluateRule(suffixRule, "", "", "Tuấn"))
        assertFalse(NotificationFilter.evaluateRule(suffixRule, "", "", "Tuấn Anh"))
    }

    // =========================================================================
    // 3. SUBTEXT GROUP EXTRACTION (Hỗ trợ Zalo/Messenger đẩy tên nhóm vào EXTRA_SUB_TEXT)
    // =========================================================================

    @Test
    fun testSubTextGroupIdentification() {
        val rule = AppFilterRule(
            appPackage = "com.zing.zalo",
            mode = ChatFilterMode.NORMAL_WITH_BLOCK,
            chatNames = setOf("*Hội Đồng Quản Trị*")
        )

        // On Samsung/Xiaomi, conversationTitle is blank, but group name is in subText
        val isAllowed = NotificationFilter.evaluateRule(
            rule = rule,
            conversationTitle = "",
            subText = "Hội Đồng Quản Trị",
            title = "Nguyễn Văn A"
        )
        assertFalse("Phải phát hiện và chặn nhóm khi tên nhóm nằm trong subText", isAllowed)
    }

    // =========================================================================
    // 4. EMPTY & BLANK RULES (Cực đoan: Rỗng, khoảng trắng, tab, xuống dòng)
    // =========================================================================

    @Test
    fun testEmptyWhitelistBlocksEverything() {
        val rule = AppFilterRule(
            appPackage = "com.zing.zalo",
            mode = ChatFilterMode.STRICT_WHITELIST,
            chatNames = emptySet()
        )
        assertFalse(NotificationFilter.evaluateRule(rule, "Nhóm Dự Án", "", "Sếp Tuấn"))
        assertFalse(NotificationFilter.evaluateRule(rule, "", "", "Nguyễn Văn A"))
    }

    @Test
    fun testEmptyBlacklistAllowsEverything() {
        val rule = AppFilterRule(
            appPackage = "com.zing.zalo",
            mode = ChatFilterMode.NORMAL_WITH_BLOCK,
            chatNames = emptySet()
        )
        assertTrue(NotificationFilter.evaluateRule(rule, "Nhóm Dự Án", "", "Sếp Tuấn"))
        assertTrue(NotificationFilter.evaluateRule(rule, "", "", "Spam Quảng Cáo"))
    }

    @Test
    fun testBlankAndWhitespaceOnlyRulesAreIgnored() {
        val rule = AppFilterRule(
            appPackage = "com.zing.zalo",
            mode = ChatFilterMode.NORMAL_WITH_BLOCK,
            chatNames = setOf("   ", "\t", "\n\r  \n", "")
        )
        assertTrue(NotificationFilter.evaluateRule(rule, "Bất Kỳ Đoạn Chat Nào", "", "Bất Kỳ Ai"))
    }

    // =========================================================================
    // 5. VIETNAMESE ACCENTS & UNICODE NFC vs NFD DECOMPOSED FORMS
    // =========================================================================

    @Test
    fun testVietnameseAccentsDecomposedNfdVsNfcEquivalence() {
        val nfcText = "Sếp Tuấn"
        val nfdText = "Se\u0302\u0301p Tua\u0302\u0301n"

        val rule = AppFilterRule(
            appPackage = "com.zing.zalo",
            mode = ChatFilterMode.NORMAL_WITH_BLOCK,
            chatNames = setOf(nfcText)
        )

        val isAllowed = NotificationFilter.evaluateRule(rule, conversationTitle = nfdText, subText = "", title = "Thành viên")
        assertFalse("Decomposed NFD input must match NFC rule seamlessly", isAllowed)
    }

    @Test
    fun testVietnameseCaseInsensitivity() {
        val rule = AppFilterRule(
            appPackage = "com.zing.zalo",
            mode = ChatFilterMode.STRICT_WHITELIST,
            chatNames = setOf("sếp tuấn")
        )

        assertTrue(NotificationFilter.evaluateRule(rule, "", "", "SẾP TUẤN"))
        assertTrue(NotificationFilter.evaluateRule(rule, "", "", "Sếp Tuấn"))
        assertTrue(NotificationFilter.evaluateRule(rule, "", "", "sẾp TuẤn"))
    }

    // =========================================================================
    // 6. ADVERSARIAL & REGEX INJECTION (Ký tự phá hoại)
    // =========================================================================

    @Test
    fun testSpecialRegexCharactersAreHandledSafely() {
        val rule = AppFilterRule(
            appPackage = "com.zing.zalo",
            mode = ChatFilterMode.NORMAL_WITH_BLOCK,
            chatNames = setOf("[VIP]*")
        )

        // Matches "[VIP] Nhóm A"
        assertFalse(NotificationFilter.evaluateRule(rule, "[VIP] Nhóm A", "", "User"))

        // Unrelated does not match
        assertTrue(NotificationFilter.evaluateRule(rule, "Nhóm A", "", "User"))
    }

    @Test
    fun testExtremeLengthStringsHandleGracefullyWithoutTimeout() {
        val giantString = "A".repeat(10_000) + "Sếp Tuấn" + "B".repeat(10_000)

        val rule = AppFilterRule(
            appPackage = "com.zing.zalo",
            mode = ChatFilterMode.NORMAL_WITH_BLOCK,
            chatNames = setOf("*Sếp Tuấn*")
        )

        val start = System.currentTimeMillis()
        val isAllowed = NotificationFilter.evaluateRule(rule, giantString, "", "Sender")
        val duration = System.currentTimeMillis() - start

        assertFalse("Must find wildcard match in huge string", isAllowed)
        assertTrue("Evaluation must take under 50ms even for 20k characters", duration < 50)
    }
}
