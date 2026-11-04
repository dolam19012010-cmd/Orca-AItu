package com.orca.orcaauto.domain.prompt

import com.orca.orcaauto.data.local.NotificationEntity
import com.orca.orcaauto.data.local.SmartAlarmEntity
import com.orca.orcaauto.domain.filter.PiiSanitizer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object GeminiPromptBuilder {

    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

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
        contentBuilder.append("=== NGUYÊN TẮC VĂN PHONG ===\n")
        contentBuilder.append(persona.promptInstructions).append("\n\n")

        // 2. Real-time Anchor & Time Estimation
        contentBuilder.append("=== THỜI GIAN THỰC TẾ & MỐC HẸN ===\n")
        contentBuilder.append("⏰ Thời điểm hiện tại: $nowFormatted (Múi giờ: ${tz.id}, GMT$offsetStr).\n")
        contentBuilder.append("• Đối chiếu mốc thời gian trong tin nhắn với thời điểm hiện tại để tính đúng ngày giờ và ước tính mốc báo thức đề xuất hợp lý.\n")
        contentBuilder.append("• Nếu việc cần làm chưa có giờ cụ thể, hãy ghi 'Hạn: Hôm nay (Chờ chốt giờ)' hoặc giờ ước tính kèm ngày.\n")
        contentBuilder.append("• Đối với những việc chưa xong mà đã quá hạn hoặc hoàn thành thụ động theo thời gian: Vẫn giữ lại theo dõi thêm 1 đến 2 phiên sau khi quá hạn, tránh vội vàng xóa bỏ làm trôi việc của người dùng.\n\n")

        // 3. Active Alarms Sync (Chống tạo lặp)
        if (activeAlarms.isNotEmpty()) {
            contentBuilder.append("=== DANH SÁCH BÁO THỨC ĐANG BẬT (TRÁNH TẠO TRÙNG) ===\n")
            for (alarm in activeAlarms.take(15)) {
                contentBuilder.append("- ${alarm.taskTitle} | Hạn: ${alarm.targetDateLabel}\n")
            }
            contentBuilder.append("⚡ Các việc trên ĐÃ BẬT BÁO THỨC. Nếu tin nhắn mới không có thay đổi về giờ giấc thì không cần liệt kê lại.\n\n")
        }

        // 5. Cancelled Tasks Blacklist
        val distinctCancelled = cancelledTasks.map { it.taskTitle.trim() }.filter { it.isNotBlank() }.distinct().take(20)
        if (distinctCancelled.isNotEmpty()) {
            contentBuilder.append("=== VIỆC NGƯỜI DÙNG ĐÃ HỦY (CẤM TẠO LẠI) ===\n")
            for (title in distinctCancelled) {
                contentBuilder.append("- $title\n")
            }
            contentBuilder.append("⚡ Người dùng đã chủ động hủy các việc này, không đưa lại vào bảng nhiệm vụ.\n\n")
        }

        // 6. Completed Tasks
        val distinctCompleted = completedTasks.map { it.taskTitle.trim() }.filter { it.isNotBlank() }.distinct().take(15)
        if (distinctCompleted.isNotEmpty()) {
            contentBuilder.append("=== VIỆC ĐÃ HOÀN THÀNH ===\n")
            for (title in distinctCompleted) {
                contentBuilder.append("- $title\n")
            }
            contentBuilder.append("⚡ Các việc này đã xong, không đưa vào mục 'PHẢI LÀM'.\n\n")
        }

        // 7. Task Notes
        if (taskNotes.isNotEmpty()) {
            contentBuilder.append("=== LỜI DẶN RIÊNG CỦA NGƯỜI DÙNG CHO CÁC NHIỆM VỤ ===\n")
            for ((tTitle, tNote) in taskNotes) {
                if (tTitle.isNotBlank() && tNote.isNotBlank()) {
                    contentBuilder.append("- Việc: \"$tTitle\" ➜ Dặn: \"$tNote\"\n")
                }
            }
            contentBuilder.append("⚡ Bắt buộc tuân thủ lời dặn này để điều chỉnh mức ưu tiên hoặc thời gian phù hợp.\n\n")
        }

        // 8. Custom User Context
        if (customFixedContext.isNotBlank()) {
            contentBuilder.append("=== BỐI CẢNH CỦA NGƯỜI DÙNG ===\n")
            contentBuilder.append(customFixedContext.trim())
            contentBuilder.append("\n\n")
        }

        // 9. Previous Summary Context
        if (previousSummary.isNotBlank()) {
            val compactPrev = previousSummary.trim().take(3000)
            contentBuilder.append("=== BÁO CÁO CA TRƯỚC (THAM CHIẾU TIẾN ĐỘ) ===\n")
            contentBuilder.append(compactPrev)
            contentBuilder.append("\n⚡ Nối tiếp tiến độ từ ca trước; những việc chưa hoàn thành hoặc quá hạn vẫn duy trì trong danh sách thêm 1-2 phiên theo quy tắc trên.\n\n")
        }

        // 10. Live User Query
        if (userQueryOrInstruction.isNotBlank()) {
            contentBuilder.append("=== YÊU CẦU / CÂU HỎI TRỰC TIẾP TỪ NGƯỜI DÙNG ===\n")
            contentBuilder.append("💬 \"${userQueryOrInstruction.trim()}\"\n")
            contentBuilder.append("👉 Hãy trả lời trực tiếp trong phần [${persona.adviceSectionTitle}] và điều phối bảng nhiệm vụ cho phù hợp.\n\n")
        }

        // 11. Notifications Data
        contentBuilder.append("=== DỮ LIỆU TIN NHẮN (${notifications.size} tin nhắn) ===\n")

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

        // 12. BẮT BUỘC TUÂN THỦ 100% CẤU TRÚC ĐỊNH DẠNG ĐẦU RA (LÀM CĂNG Ở ĐỊNH DẠNG)
        contentBuilder.append("\n=== QUY TẮC ĐỊNH DẠNG ĐẦU RA BẮT BUỘC (TUÂN THỦ TUYỆT ĐỐI) ===\n\n")
        contentBuilder.append("BẮT BUỘC xuất ra đúng cấu trúc 2 PHẦN như sau, không tự ý đổi tiêu đề hay thêm bớt phần khác:\n\n")

        contentBuilder.append("=== PHẦN 1: ${persona.adviceSectionTitle.uppercase()} & ${persona.noteSectionTitle.uppercase()} ===\n")
        contentBuilder.append("1. [${persona.adviceSectionTitle}]: ${persona.adviceDescription}\n")
        contentBuilder.append("2. [${persona.noteSectionTitle}]: ${persona.noteDescription}\n\n")

        contentBuilder.append("=== PHẦN 2: BẢNG ĐIỀU PHỐI NHIỆM VỤ ===\n")
        contentBuilder.append("QUY TẮC CÚ PHÁP TỪNG DÒNG NHIỆM VỤ (BẮT BUỘC CHUẨN XÁC ĐỂ HỆ THỐNG PARSE MÁY):\n")
        contentBuilder.append("• Mỗi nhiệm vụ trên ĐÚNG 1 DÒNG DUY NHẤT theo cú pháp: [Tên nhiệm vụ ngắn gọn] | Nguồn: [Tên người gửi ngắn] | Hạn: [Thời gian/Giờ đề xuất]\n")
        contentBuilder.append("• TUYỆT ĐỐI KHÔNG dùng dấu sao in đậm (**) trong tên nhiệm vụ.\n")
        contentBuilder.append("• TUYỆT ĐỐI KHÔNG tự ý ngắt dòng, xuống dòng giữa chừng trong một nhiệm vụ.\n")
        contentBuilder.append("• Bắt buộc dùng đúng 3 nhóm tiêu đề chuẩn sau:\n\n")

        contentBuilder.append("### 🔴 NHIỆM VỤ PHẢI LÀM (TO-DO)\n")
        contentBuilder.append("(Bao gồm việc mới và các việc quá hạn/hoàn thành thụ động được giữ lại theo dõi thêm 1-2 phiên)\n")
        contentBuilder.append("- [Tên nhiệm vụ cần làm] | Nguồn: [Người gửi] | Hạn: [Giờ/Ngày đề xuất]\n\n")

        contentBuilder.append("### 🟢 NHIỆM VỤ ĐÃ LÀM (DONE)\n")
        contentBuilder.append("(Chỉ giữ tối đa 1-2 việc quan trọng vừa hoàn tất gần đây có mốc giờ rõ; không liệt kê việc lặt vặt đã xong)\n")
        contentBuilder.append("- [Tên việc quan trọng đã xong] | Nguồn: [Người gửi] | Thời gian: [Đã xong lúc HH:mm]\n\n")

        contentBuilder.append("### 🟡 KẾ HOẠCH DỰ KIẾN (PLANNED)\n")
        contentBuilder.append("- [Tên lịch hẹn/kế hoạch tương lai] | Nguồn: [Người gửi] | Mục tiêu: [Ngày/Giờ]\n")

        return contentBuilder.toString()
    }
}
