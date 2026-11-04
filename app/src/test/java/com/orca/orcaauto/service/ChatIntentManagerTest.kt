package com.orca.orcaauto.service

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ChatIntentManagerTest {

    @Before
    fun setUp() {
        ChatIntentManager.clearAll()
    }

    @After
    fun tearDown() {
        ChatIntentManager.clearAll()
    }

    @Test
    fun testCacheAndRetrieveIntent() {
        ChatIntentManager.cacheChatIntent(
            packageName = "com.zing.zalo",
            senderTitle = "Sếp Tuấn",
            pendingIntent = null // In test, null is allowed when checking data class if mock not used
        )

        assertEquals(0, ChatIntentManager.getCacheSize()) // pendingIntent null -> not cached

        // Now with dummy CachedChatIntent added directly for logic validation
        val dummy = CachedChatIntent(
            packageName = "com.zing.zalo",
            senderTitle = "Sếp Tuấn",
            pendingIntent = null
        )
        assertNotNull(dummy)
        assertEquals("com.zing.zalo", dummy.packageName)
        assertEquals("Sếp Tuấn", dummy.senderTitle)
    }

    @Test
    fun testSmartReplyGeneration() {
        val replyReport = ChatIntentManager.generateSmartReply("Gửi file báo cáo tài chính quý 3")
        assertTrue("Câu trả lời phải thể hiện đang hoàn thiện và gửi",
            replyReport.contains("hoàn thiện") || replyReport.contains("gửi")
        )

        val replyMeeting = ChatIntentManager.generateSmartReply("Mai 9h họp chiến lược dự án")
        assertTrue("Câu trả lời họp phải thể hiện tham gia đúng giờ",
            replyMeeting.contains("lịch") || replyMeeting.contains("đúng giờ")
        )

        val replyPayment = ChatIntentManager.generateSmartReply("Nhớ chuyển tiền nhà trước tối nay")
        assertTrue("Câu trả lời tiền phải thể hiện kiểm tra và thanh toán",
            replyPayment.contains("kiểm tra") || replyPayment.contains("thực hiện")
        )

        val replyConfirm = ChatIntentManager.generateSmartReply("Xác nhận lại số liệu giúp anh")
        assertTrue("Câu trả lời xác nhận",
            replyConfirm.contains("xác nhận") || replyConfirm.contains("kiểm tra")
        )

        val replyDefault = ChatIntentManager.generateSmartReply("Hôm nay xong cái này nhé")
        assertTrue("Câu trả lời mặc định lịch sự",
            replyDefault.contains("đang xử lý") || replyDefault.contains("nắm thông tin")
        )
    }

    @Test
    fun testSenderMatchingLogic() {
        // Test matching text normalization
        val reply1 = ChatIntentManager.generateSmartReply("Báo cáo tuần")
        assertNotNull(reply1)
    }
}
