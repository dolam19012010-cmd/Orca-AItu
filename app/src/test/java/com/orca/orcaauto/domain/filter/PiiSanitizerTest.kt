package com.orca.orcaauto.domain.filter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PiiSanitizerTest {

    @Test
    fun masksEmailWithoutTouchingMentions() {
        val input = "Gửi báo cáo qua email nguyen.van.a@company.vn cho @nam trước 17h"
        val out = PiiSanitizer.sanitize(input)
        assertFalse(out.contains("nguyen.van.a@company.vn"))
        assertTrue(out.contains("[EMAIL ĐÃ CHE]"))
        assertTrue("Giữ nguyên @mention thường", out.contains("@nam"))
    }

    @Test
    fun masksCccdOnlyWhenExplicitKeywordPresent() {
        val sensitive = "Số CCCD của em là 079123456789 và CMND cũ: 123456789 nhé"
        val masked = PiiSanitizer.sanitize(sensitive)
        assertFalse(masked.contains("079123456789"))
        assertFalse(masked.contains("123456789"))
        assertTrue(masked.contains("[CCCD/CMND ĐÃ CHE]"))

        val ordinary = "Mã giao dịch FT23192849201 hoặc mã vận đơn 492018421 đã xong"
        assertEquals("Không ẩn nhầm mã giao dịch/vận đơn 9-12 số", ordinary, PiiSanitizer.sanitize(ordinary))
    }

    @Test
    fun masksOtpAndPinStrictlyWithKeywordAndDigits() {
        val otpMsg = "Mã OTP của bạn là 582910, smart otp: A19283, mã PIN là 4590"
        val masked = PiiSanitizer.sanitize(otpMsg)
        assertFalse(masked.contains("582910"))
        assertFalse(masked.contains("A19283"))
        assertFalse(masked.contains("4590"))
        assertTrue(masked.contains("[MÃ BẢO MẬT ĐÃ CHE]"))

        val reverseOtp = "839201 là mã xác thực giao dịch"
        val maskedReverse = PiiSanitizer.sanitize(reverseOtp)
        assertFalse(maskedReverse.contains("839201"))
        assertTrue(maskedReverse.contains("[MÃ BẢO MẬT ĐÃ CHE]"))

        // False positive guards
        val orderCode = "Mã đơn hàng 849201 đang giao, hẹn họp phòng 1024 lúc 15:30"
        assertEquals(orderCode, PiiSanitizer.sanitize(orderCode))

        val normalPhrase = "Đã mã xác minh xong rồi nhé"
        assertEquals(normalPhrase, PiiSanitizer.sanitize(normalPhrase))
    }

    @Test
    fun masksExplicitPasswordAndPreservesAddressAndQuestions() {
        val pwdMsg = "Mật khẩu wifi: Orca@2026, pass wifi là Secret99 nhé"
        val masked = PiiSanitizer.sanitize(pwdMsg)
        assertFalse(masked.contains("Orca@2026"))
        assertFalse(masked.contains("Secret99"))
        assertTrue(masked.contains("[MẬT KHẨU ĐÃ CHE]"))

        val question = "Quên mật khẩu là gì rồi, họp tại số 25 Lê Lợi lúc 14h"
        assertEquals("Không ẩn câu hỏi mật khẩu hay địa chỉ họp", question, PiiSanitizer.sanitize(question))
    }

    @Test
    fun retainsPhoneAndBankAccountSanitization() {
        val input = "Gọi 0912345678 hoặc CK STK: 19034567890123"
        val out = PiiSanitizer.sanitize(input)
        assertFalse(out.contains("0912345678"))
        assertTrue(out.contains("091****678"))
        assertTrue(out.contains("[STK ĐÃ CHE]"))
    }
}
