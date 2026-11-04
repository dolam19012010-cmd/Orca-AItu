package com.orca.orcaauto.domain.prompt

import com.orca.orcaauto.data.local.NotificationEntity
import com.orca.orcaauto.data.local.SmartAlarmEntity
import com.orca.orcaauto.domain.filter.PiiSanitizer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object GeminiPromptBuilder {

    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val fullDateFormat = SimpleDateFormat("EEEE, 'ngày' dd/MM/yyyy 'lúc' HH:mm", Locale("vi", "VN"))

    /**
     * Builds a structured AI prompt supporting dynamic PromptPersonas (Gentle Assistant, Executive Secretary, etc.)
     * Includes real-time timestamp, custom fixed user context, and optional live interactive user instructions.
     */
    fun buildSummaryPrompt(
        notifications: List<NotificationEntity>,
        customFixedContext: String = "",
        persona: PromptPersona = PromptPersona.GENTLE_ASSISTANT,
        userQueryOrInstruction: String = "",
        previousSummary: String = "",
        activeAlarms: List<SmartAlarmEntity> = emptyList(),
        cancelledTasks: List<SmartAlarmEntity> = emptyList(),
        completedTasks: List<SmartAlarmEntity> = emptyList(),
        taskNotes: Map<String, String> = emptyMap(),
        timezoneId: String = "Asia/Ho_Chi_Minh"
    ): String {
        val groupedByApp = notifications.groupBy { it.appName }

        val tz = try { java.util.TimeZone.getTimeZone(timezoneId) } catch (_: Exception) { java.util.TimeZone.getDefault() }
        val localizedDateFormat = SimpleDateFormat("EEEE, 'ngày' dd/MM/yyyy 'lúc' HH:mm", Locale("vi", "VN")).apply {
            timeZone = tz
        }
        val rawDateStr = localizedDateFormat.format(Date())
        val nowFormatted = rawDateStr.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale("vi", "VN")) else it.toString() }
        val offsetHours = tz.rawOffset / (3600 * 1000)
        val offsetStr = if (offsetHours >= 0) "+$offsetHours" else "$offsetHours"

        val contentBuilder = StringBuilder()

        // 1. Role & Tone instruction based on Persona
        contentBuilder.append(persona.systemRole).append("\n\n")
        contentBuilder.append("=== NGUYÊN TẮC VĂN PHONG & CÁCH NHẮC NHỞ ===\n")
        contentBuilder.append(persona.promptInstructions).append("\n\n")

        contentBuilder.append("=== THỜI GIAN THỰC HIỆN BÁO CÁO ===\n")
        contentBuilder.append("⏰ Thời điểm hiện tại: $nowFormatted (Múi giờ: ${tz.id}, GMT$offsetStr).\n")
        contentBuilder.append("⚡ QUY TẮC THỜI GIAN & ƯỚC TÍNH BÁO THỨC ĐỀ XUẤT HỢP LÝ:\n")
        contentBuilder.append("1. ĐỐI CHIẾU THỜI GIAN THỰC: Luôn đối chiếu các mốc trong tin nhắn ('chiều nay', 'sáng mai', 'ngày kia', 'thứ 6 tuần sau', 'tháng sau'...) với mốc hiện tại ở trên để tính toán ngày giờ chuẩn xác.\n")
        contentBuilder.append("2. NHÌN NGÀY VÀ HẠN, ƯỚC TÍNH BÁO THỨC HỢP LÝ: Hãy nhìn kỹ ngày giờ diễn ra và bản chất công việc, ước tính các yếu tố thực tế (thời gian chuẩn bị, di chuyển, mức độ gấp, vùng đệm an toàn) để chọn ra mốc thời gian báo thức/nhắc nhở đề xuất thiết thực nhất. Ví dụ:\n")
        contentBuilder.append("   • Cuộc họp hoặc lịch hẹn lúc 09:00 sáng mai ➜ Đề xuất báo thức khoảng 08:15 hoặc 08:30 để kịp chuẩn bị (Hạn: 08:30 ngày mai).\n")
        contentBuilder.append("   • Hạn chót hoàn thành việc lúc 17:00 chiều nay ➜ Đề xuất báo thức lúc 16:00 hoặc 16:30 để kịp hoàn thiện trước hạn (Hạn: 16:30 hôm nay).\n")
        contentBuilder.append("   • Sự kiện cần chuẩn bị vào buổi sáng ➜ Đề xuất báo thức đầu giờ sáng (Hạn: 07:30 ngày mai).\n")
        contentBuilder.append("3. GHI ĐỦ NGÀY VÀ GIỜ: Trong phần 'Hạn: [Thời gian]' của mỗi nhiệm vụ, hãy ghi rõ cả giờ đề xuất và ngày (ví dụ: 'Hạn: 16:30 hôm nay', 'Hạn: 08:30 ngày mai 05/11', 'Hạn: 15h ngày 15/11'). Điều này giúp hệ thống tự động đặt chuông báo thức thông minh chuẩn xác!\n\n")

        if (activeAlarms.isNotEmpty()) {
            contentBuilder.append("=== DANH SÁCH BÁO THỨC & NHIỆM VỤ ĐÃ ĐƯỢC ĐẶT (TRÁNH TẠO TRÙNG LẶP) ===\n")
            for (alarm in activeAlarms.take(15)) {
                contentBuilder.append("- [Mã: ${alarm.alarmId}] ${alarm.taskTitle} | Hạn: ${alarm.targetDateLabel} | Trạng thái: ĐÃ BẬT BÁO THỨC\n")
            }
            contentBuilder.append("⚡ QUY TẮC ĐỒNG BỘ: Các nhiệm vụ trên ĐÃ ĐƯỢC người dùng tích chọn và kích hoạt chuông báo thức. Trong bảng nhiệm vụ mới bên dưới, hãy giữ nguyên trạng thái hoặc tiến độ cho nhiệm vụ này, TUYỆT ĐỐI KHÔNG sinh thêm nhiệm vụ trùng lặp hay đề xuất báo thức lại!\n\n")
        }

        val distinctCancelled = cancelledTasks.map { it.taskTitle.trim() }.filter { it.isNotBlank() }.distinct().take(30)
        if (distinctCancelled.isNotEmpty()) {
            contentBuilder.append("=== DANH SÁCH NHIỆM VỤ NGƯỜI DÙNG ĐÃ CHỦ ĐỘNG HỦY (TUYỆT ĐỐI CẤM TẠO LẠI) ===\n")
            for (title in distinctCancelled) {
                contentBuilder.append("- [ĐÃ HỦY] $title\n")
            }
            contentBuilder.append("⚡ QUY TẮC BẮT BUỘC: Người dùng đã chủ động hủy bỏ các nhiệm vụ trên (kể cả từ các ngày trước). TUYỆT ĐỐI CẤM đề xuất lại hoặc đưa bất kỳ việc nào ở trên vào bảng nhiệm vụ 'PHẢI LÀM' hay 'KẾ HOẠCH DỰ KIẾN', ngay cả khi tin nhắn cũ vẫn còn chứa thông tin này!\n\n")
        }

        val distinctCompleted = completedTasks.map { it.taskTitle.trim() }.filter { it.isNotBlank() }.distinct().take(20)
        if (distinctCompleted.isNotEmpty()) {
            contentBuilder.append("=== DANH SÁCH NHIỆM VỤ ĐÃ HOÀN THÀNH ===\n")
            for (title in distinctCompleted) {
                contentBuilder.append("- [ĐÃ XONG] $title\n")
            }
            contentBuilder.append("⚡ QUY TẮC: Các nhiệm vụ này đã hoàn tất, TUYỆT ĐỐI KHÔNG đưa lại vào mục 'PHẢI LÀM'!\n\n")
        }

        if (taskNotes.isNotEmpty()) {
            contentBuilder.append("=== LƯU Ý & GHI CHÚ ĐẶC BIỆT CỦA NGƯỜI DÙNG CHO CÁC NHIỆM VỤ (DẶN DÒ CHO PHIÊN NÀY) ===\n")
            for ((tTitle, tNote) in taskNotes) {
                if (tTitle.isNotBlank() && tNote.isNotBlank()) {
                    contentBuilder.append("- Nhiệm vụ: \"$tTitle\" ➜ Lời dặn của người dùng: \"$tNote\"\n")
                }
            }
            contentBuilder.append("⚡ QUY TẮC: Bạn BẮT BUỘC phải đối chiếu các lời dặn này để điều chỉnh mức độ ưu tiên, cập nhật lời nhắn hoặc thực hiện đúng chỉ dẫn của người dùng cho nhiệm vụ đó!\n\n")
        }

        if (customFixedContext.isNotBlank()) {
            contentBuilder.append("=== BỐI CẢNH & THÔNG TIN CỐ ĐỊNH CỦA NGƯỜI DÙNG ===\n")
            contentBuilder.append(customFixedContext.trim())
            contentBuilder.append("\n(Hãy dùng thông tin bối cảnh trên để hiểu vai trò, cách xưng hô và ưu tiên đúng các mối quan hệ công việc cốt lõi của người dùng).\n\n")
        }

        if (previousSummary.isNotBlank()) {
            val compactPrev = previousSummary.trim().take(1200)
            contentBuilder.append("=== BÁO CÁO & TIẾN ĐỘ CA GẦN NHẤT (CONTEXT LIÊN TỤC & THAM CHIẾU TIẾN ĐỘ) ===\n")
            contentBuilder.append(compactPrev)
            contentBuilder.append("\n\n⚡ QUY TẮC THAM CHIẾU BÁO CÁO CŨ:\n")
            contentBuilder.append("1. TIẾN ĐỘ: Dùng báo cáo trước để biết các việc ca trước đang chờ xử lý. Nếu tin nhắn mới cho thấy việc đã xong -> chuyển sang ĐÃ XONG hoặc TỰ ĐỘNG BỎ ĐI nếu là việc vặt.\n")
            contentBuilder.append("2. KHÔNG LẶP LẠI VẸT: Tuyệt đối KHÔNG chép lại y nguyên các việc cũ nếu trong loạt tin nhắn mới này không có thêm thông tin hay diễn biến mới.\n")
            contentBuilder.append("3. VĂN PHONG & TRÌNH BÀY: Học hỏi cách trình bày ngắn gọn, súc tích và đúng chuẩn của ca trước.\n\n")
        }

        if (userQueryOrInstruction.isNotBlank()) {
            contentBuilder.append("=== LỆNH BỔ SUNG / CÂU HỎI TRỰC TIẾP CỦA NGƯỜI DÙNG ===\n")
            contentBuilder.append("💬 \"${userQueryOrInstruction.trim()}\"\n")
            contentBuilder.append("👉 YÊU CẦU: Hãy trực tiếp trả lời câu hỏi hoặc xử lý yêu cầu trên của người dùng trong phần [${persona.adviceSectionTitle}], đồng thời điều phối bảng nhiệm vụ bên dưới cho phù hợp nhất!\n\n")
        }

        contentBuilder.append("=== DỮ LIỆU TIN NHẮN THU THẬP ĐƯỢC (${notifications.size} tin nhắn) ===\n")

        if (notifications.isEmpty()) {
            contentBuilder.append("(Không có tin nhắn mới nào trong ca. Hãy trả lời trực tiếp câu hỏi/yêu cầu của người dùng ở trên).\n")
        } else {
            for ((appName, appNotifications) in groupedByApp) {
                contentBuilder.append("\n【 Ứng dụng: $appName 】\n")
                val groupedBySender = appNotifications.groupBy { it.sender }

                for ((sender, senderMsgs) in groupedBySender) {
                    contentBuilder.append("- Người gửi: **$sender**\n")
                    for (msg in senderMsgs) {
                        val timeStr = timeFormat.format(Date(msg.timestamp))
                        val sanitizedContent = PiiSanitizer.sanitize(msg.content)
                        contentBuilder.append("  • [$timeStr] $sanitizedContent\n")
                    }
                }
            }
        }

        contentBuilder.append("\n=== CẤU TRÚC ĐẦU RA BẮT BUỘC (2 PHẦN) ===\n\n")

        contentBuilder.append("=== PHẦN 1: ${persona.adviceSectionTitle.uppercase()} & ${persona.noteSectionTitle.uppercase()} ===\n")
        contentBuilder.append("1. [${persona.adviceSectionTitle}]: ${persona.adviceDescription}\n")
        contentBuilder.append("2. [${persona.noteSectionTitle}]: ${persona.noteDescription}\n\n")

        contentBuilder.append("=== PHẦN 2: BẢNG ĐIỀU PHỐI NHIỆM VỤ ===\n")
        contentBuilder.append("QUY TẮC ĐỊNH DẠNG: Mỗi mục trên 1 dòng duy nhất theo cú pháp: [Tên nhiệm vụ ngắn gọn] | Nguồn: [Tên người gửi ngắn] | Hạn: [Giờ đề xuất/Hạn chót]\n")
        contentBuilder.append("TUYỆT ĐỐI KHÔNG dùng dấu ** in đậm hay ký tự thừa trong tên nhiệm vụ. Giữ tên và hạn chót cực kỳ súc tích, không cách dòng hay xuống dòng thừa.\n\n")
        contentBuilder.append("### 🔴 NHIỆM VỤ PHẢI LÀM (TO-DO)\n")
        contentBuilder.append("- [Nhiệm vụ cần làm] | Nguồn: [Người gửi] | Hạn: [Giờ đề xuất cụ thể]\n\n")
        contentBuilder.append("### 🟢 NHIỆM VỤ ĐÃ LÀM (DONE)\n")
        contentBuilder.append("⚡ QUY TẮC BỎ VIỆC ĐÃ HOÀN THÀNH: Chỉ giữ lại tối đa 1-2 việc QUAN TRỌNG NHẤT vừa hoàn tất gần đây (có mốc thời gian rõ: 'Đã xong 10:30'). Các việc vặt, tiểu tiết đã xong thì TỰ ĐỘNG BỎ ĐI, KHÔNG liệt kê để tránh rác màn hình.\n")
        contentBuilder.append("- [Nhiệm vụ quan trọng đã xong] | Nguồn: [Người gửi] | Thời gian: [Đã xong lúc HH:mm]\n\n")
        contentBuilder.append("### 🟡 KẾ HOẠCH DỰ KIẾN (PLANNED)\n")
        contentBuilder.append("- [Lịch hẹn/kế hoạch tương lai] | Nguồn: [Người gửi] | Mục tiêu: [Ngày/Giờ]\n")

        return contentBuilder.toString()
    }
}
