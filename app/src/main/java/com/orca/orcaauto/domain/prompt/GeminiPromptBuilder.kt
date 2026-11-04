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
        contentBuilder.append("=== NGUYÊN TẮC VĂN PHONG & CÁCH NHẮC NHỞ ===\n")
        contentBuilder.append(persona.promptInstructions).append("\n\n")

        // 2. Real-time Anchor & Smart Alarm Estimation
        contentBuilder.append("=== THỜI GIAN HIỆN TẠI & QUY TẮC ĐỀ XUẤT BÁO THỨC ===\n")
        contentBuilder.append("⏰ Thời điểm hiện tại: $nowFormatted (Múi giờ: ${tz.id}, GMT$offsetStr).\n")
        contentBuilder.append("1. ĐỐI CHIẾU THỜI GIAN: Đối chiếu các mốc trong tin nhắn ('chiều nay', 'sáng mai', 'ngày kia', 'thứ 6'...) với mốc hiện tại ở trên để tính đúng ngày giờ.\n")
        contentBuilder.append("2. NHÌN NGÀY VÀ HẠN, ƯỚC TÍNH BÁO THỨC HỢP LÝ: Nhìn kỹ ngày, giờ và bản chất công việc; ước tính thời gian chuẩn bị, di chuyển hoặc độ gấp để đề xuất giờ báo thức thiết thực (Ví dụ: Họp 09:00 sáng mai ➜ Đề xuất báo 08:30; Hạn nộp 17:00 chiều nay ➜ Đề xuất báo 16:30).\n")
        contentBuilder.append("3. GHI ĐỦ NGÀY VÀ GIỜ: Trong phần 'Hạn: [Thời gian]', luôn ghi cả giờ đề xuất và ngày (Ví dụ: 'Hạn: 16:30 hôm nay', 'Hạn: 08:30 ngày mai 05/11'). Nếu việc quan trọng chưa chốt giờ cụ thể, hãy ghi 'Hạn: Hôm nay (Chờ chốt giờ)' hoặc ước tính giờ kèm ngày.\n\n")

        // 3. Separation of Tasks vs Casual Notes
        contentBuilder.append("=== PHÂN ĐỊNH: BẢNG NHIỆM VỤ VS LỜI NHẮN/LƯU Ý ===\n")
        contentBuilder.append("• BẢNG NHIỆM VỤ (PHẦN 2): Đưa vào những việc có hành động cụ thể cần làm. ĐẶC BIỆT: Việc QUAN TRỌNG thì DÙ CHƯA CHỐT GIỜ VẪN BẮT BUỘC ĐƯA VÀO (ghi 'Hạn: Hôm nay (Chờ chốt giờ)' hoặc giờ ước tính). Tuyệt đối không bỏ sót việc quan trọng chỉ vì đối phương chưa chốt giờ.\n")
        contentBuilder.append("• LỜI NHẮN / LƯU Ý (PHẦN 1): Dành cho những việc lặt vặt, tiểu tiết không quan trọng, hoặc nhờ vả vu vơ (mua đồ vặt, tán gẫu...). Những việc vặt này chỉ nhắc nhẹ ở đây, KHÔNG đưa vào Bảng Nhiệm Vụ (TO-DO) để tránh làm rác danh sách checklist.\n\n")

        // 4. Active Alarms Sync (Chống tạo lặp)
        if (activeAlarms.isNotEmpty()) {
            contentBuilder.append("=== DANH SÁCH BÁO THỨC ĐANG BẬT (TRÁNH TẠO TRÙNG) ===\n")
            for (alarm in activeAlarms.take(15)) {
                contentBuilder.append("- ${alarm.taskTitle} | Hạn: ${alarm.targetDateLabel}\n")
            }
            contentBuilder.append("⚡ Các việc trên ĐÃ BẬT BÁO THỨC. Nếu tin nhắn mới không có thay đổi gì về giờ giấc hay tiến độ thì không cần liệt kê lại để tránh trùng lặp.\n\n")
        }

        // 5. Cancelled Tasks Blacklist
        val distinctCancelled = cancelledTasks.map { it.taskTitle.trim() }.filter { it.isNotBlank() }.distinct().take(20)
        if (distinctCancelled.isNotEmpty()) {
            contentBuilder.append("=== VIỆC NGƯỜI DÙNG ĐÃ HỦY (CẤM TẠO LẠI) ===\n")
            for (title in distinctCancelled) {
                contentBuilder.append("- $title\n")
            }
            contentBuilder.append("⚡ Người dùng đã chủ động hủy các việc này. Cấm đưa lại vào bảng nhiệm vụ, kể cả khi tin nhắn còn chứa thông tin.\n\n")
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
            contentBuilder.append("\n(Dùng bối cảnh trên để hiểu vai trò và ưu tiên đúng các mối quan hệ công việc cốt lõi).\n\n")
        }

        // 9. Previous Summary Context
        if (previousSummary.isNotBlank()) {
            val compactPrev = previousSummary.trim().take(1000)
            contentBuilder.append("=== BÁO CÁO CA TRƯỚC (THAM CHIẾU TIẾN ĐỘ) ===\n")
            contentBuilder.append(compactPrev)
            contentBuilder.append("\n⚡ Dùng để nối tiếp tiến độ, không chép lại y nguyên nếu tin nhắn mới không có diễn biến mới.\n\n")
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

        // 12. Output Format (2 Parts)
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
        contentBuilder.append("⚡ Chỉ giữ lại tối đa 1-2 việc QUAN TRỌNG NHẤT vừa hoàn tất gần đây (có mốc thời gian rõ: 'Đã xong lúc HH:mm'). Tuyệt đối không liệt kê việc vặt đã xong.\n")
        contentBuilder.append("- [Nhiệm vụ quan trọng đã xong] | Nguồn: [Người gửi] | Thời gian: [Đã xong lúc HH:mm]\n\n")
        contentBuilder.append("### 🟡 KẾ HOẠCH DỰ KIẾN (PLANNED)\n")
        contentBuilder.append("- [Lịch hẹn/kế hoạch tương lai] | Nguồn: [Người gửi] | Mục tiêu: [Ngày/Giờ]\n")

        return contentBuilder.toString()
    }
}
