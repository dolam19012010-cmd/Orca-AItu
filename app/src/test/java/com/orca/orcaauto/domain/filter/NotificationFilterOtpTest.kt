package com.orca.orcaauto.domain.filter

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Comprehensive empirical tests for [NotificationFilter.isOtpMessage].
 * Verifies strict 3-tier OTP detection:
 * 1. Technical keyword (OTP, smart OTP, verification code, mã xác thực, etc.)
 * 2. 4-8 digit isolated code
 * 3. Short-term validity or secrecy/confidentiality context
 *
 * Ensures zero false positives for ordinary Vietnamese and conversational messages,
 * while reliably intercepting genuine banking and service OTP codes.
 */
class NotificationFilterOtpTest {

    // =========================================================================
    // FALSE POSITIVE PREVENTION (Must NOT be flagged as OTP -> returns false)
    // =========================================================================

    @Test
    fun testOrdinaryConfirmationWithNumbers_notOtp() {
        assertFalse(
            "Xác nhận danh sách bạn bè không phải OTP",
            NotificationFilter.isOtpMessage("xác nhận danh sách 45 bạn tham gia dã ngoại ngày mai")
        )
        assertFalse(
            "Xác nhận nộp học phí không phải OTP",
            NotificationFilter.isOtpMessage("xác nhận nộp tiền 1250000đ tiền học phí kỳ 1")
        )
        assertFalse(
            "Xác nhận phòng thi không phải OTP",
            NotificationFilter.isOtpMessage("xác nhận phòng thi 1024 ca thi sáng thứ 7")
        )
        assertFalse(
            "Xác nhận đặt cọc không phải OTP",
            NotificationFilter.isOtpMessage("Chuyển 500000 để xác nhận cọc phòng trọ nhé")
        )
    }

    @Test
    fun testBankTransactionReceipts_notOtp() {
        // Bank balance changes or transaction receipts must NOT be dropped as OTP
        val receipt = "Giao dịch thành công 500000 VND lúc 14:30. Mã giao dịch: 98765432. Số dư: 15200000 VND."
        assertFalse("Biên lai giao dịch ngân hàng không phải OTP", NotificationFilter.isOtpMessage(receipt))

        val vcbNotif = "VCB: Tai khoan 0123456789 biến động -250000 VND. Ref: FT23192849201. Cảm ơn quý khách."
        assertFalse("Biến động số dư không phải OTP", NotificationFilter.isOtpMessage(vcbNotif))
    }

    @Test
    fun testShippingAndOrderTracking_notOtp() {
        assertFalse(
            "Mã đơn hàng vận chuyển không phải OTP",
            NotificationFilter.isOtpMessage("Đơn hàng 849201 đang được shipper giao tới bạn, vui lòng chú ý điện thoại.")
        )
        assertFalse(
            "Mã vận đơn không phải OTP",
            NotificationFilter.isOtpMessage("Mã vận đơn VNPOST 49201842 đã đến bưu cục quận 1.")
        )
    }

    @Test
    fun testConversationalChatWithNumbers_notOtp() {
        assertFalse(
            "Hẹn giờ gặp mặt không phải OTP",
            NotificationFilter.isOtpMessage("Hôm nay gặp nhau lúc 1530 ở quán cà phê nhé.")
        )
        assertFalse(
            "Số điện thoại thông thường không phải OTP",
            NotificationFilter.isOtpMessage("Số điện thoại liên hệ là 0912345678, gọi trước khi qua nha.")
        )
        assertFalse(
            "Tin nhắn rỗng hoặc chỉ có dấu cách",
            NotificationFilter.isOtpMessage("   ")
        )
    }

    // =========================================================================
    // TRUE POSITIVE DETECTION (Genuine OTPs -> MUST return true)
    // =========================================================================

    @Test
    fun testVietnameseOtp_withDiacritics_isDetected() {
        val msg1 = "Mã xác thực của bạn là 192834. Hết hạn trong 5 phút. Vui lòng không chia sẻ mã này cho bất kỳ ai."
        assertTrue("Mã xác thực có hạn dùng và cảnh báo bí mật phải là OTP", NotificationFilter.isOtpMessage(msg1))

        val msg2 = "Mã OTP giao dịch tại Tiki là 582910. Hiệu lực trong 3 phút. Tuyệt đối không cung cấp mã cho người khác."
        assertTrue("Mã OTP có hiệu lực và cảnh báo bảo mật phải là OTP", NotificationFilter.isOtpMessage(msg2))

        val msg3 = "Mã xác minh tài khoản Zalo của bạn là 8492. Mã có hiệu lực trong 120 giây. Bảo mật thông tin này."
        assertTrue("Mã xác minh 4 số với thời hạn và bảo mật phải là OTP", NotificationFilter.isOtpMessage(msg3))
    }

    @Test
    fun testVietnameseOtp_nonDiacriticSms_isDetected() {
        val sms1 = "Ma OTP cua ban la 682910. Hieu luc trong 3 phut. Khong chia se ma nay voi bat ky ai."
        assertTrue("SMS không dấu OTP viễn thông phải được nhận diện", NotificationFilter.isOtpMessage(sms1))

        val sms2 = "Smart OTP: 456789 la ma xac thuc giao dich. Han dung 2 phut. Tuyet doi khong chia se."
        assertTrue("Smart OTP không dấu phải được nhận diện", NotificationFilter.isOtpMessage(sms2))
    }

    @Test
    fun testEnglishOtpMessages_isDetected() {
        val en1 = "Your verification code is 839201. It expires in 10 minutes. Do not share this code with anyone."
        assertTrue("English verification code must be detected", NotificationFilter.isOtpMessage(en1))

        val en2 = "Your one-time password is 748291. Valid for 5 minutes. Confidential."
        assertTrue("One-time password must be detected", NotificationFilter.isOtpMessage(en2))

        val en3 = "Security code: 994821. Never share your OTP with anyone. Expires soon."
        assertTrue("Security code with OTP must be detected", NotificationFilter.isOtpMessage(en3))
    }

    @Test
    fun testBoundaryDigits_4To8Digits() {
        // 4 digits code
        val fourDigits = "Mã OTP của bạn là 1234. Hết hạn trong 3 phút. Không chia sẻ."
        assertTrue("4 chữ số OTP hợp lệ", NotificationFilter.isOtpMessage(fourDigits))

        // 8 digits code
        val eightDigits = "Mã xác thực là 12345678. Hiệu lực 5 phút. Bảo mật mã này."
        assertTrue("8 chữ số OTP hợp lệ", NotificationFilter.isOtpMessage(eightDigits))

        // 3 digits (less than 4) should NOT match
        val threeDigits = "Mã OTP của bạn là 123. Hết hạn trong 3 phút. Không chia sẻ."
        assertFalse("3 chữ số không đủ điều kiện OTP", NotificationFilter.isOtpMessage(threeDigits))

        // 9 digits (more than 8) should NOT match
        val nineDigits = "Mã OTP của bạn là 123456789. Hết hạn trong 3 phút. Không chia sẻ."
        assertFalse("9 chữ số vượt quá độ dài OTP", NotificationFilter.isOtpMessage(nineDigits))
    }
}
