package com.orca.orcaauto.domain.prompt

import com.orca.orcaauto.data.local.NotificationEntity
import com.orca.orcaauto.data.local.SmartAlarmEntity
import com.orca.orcaauto.domain.filter.PiiSanitizer
import com.orca.orcaauto.ui.locale.AppLanguage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object GeminiPromptBuilder {

    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    /**
     * Builds a structured AI prompt supporting dynamic PromptPersonas (Gentle Assistant, Executive Secretary, etc.)
     * Includes real-time timestamp, custom fixed user context, and optional live interactive user instructions.
     * Supports both VIETNAMESE and ENGLISH prompt generation and output language enforcement.
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
        timezoneId: String = "Asia/Ho_Chi_Minh",
        autoCompleteOverdue: Boolean = false,
        language: AppLanguage = AppLanguage.VIETNAMESE
    ): String {
        return if (language == AppLanguage.ENGLISH) {
            buildEnglishPrompt(
                notifications = notifications,
                customFixedContext = customFixedContext,
                persona = persona,
                userQueryOrInstruction = userQueryOrInstruction,
                previousSummary = previousSummary,
                activeAlarms = activeAlarms,
                cancelledTasks = cancelledTasks,
                completedTasks = completedTasks,
                taskNotes = taskNotes,
                timezoneId = timezoneId,
                autoCompleteOverdue = autoCompleteOverdue
            )
        } else {
            buildVietnamesePrompt(
                notifications = notifications,
                customFixedContext = customFixedContext,
                persona = persona,
                userQueryOrInstruction = userQueryOrInstruction,
                previousSummary = previousSummary,
                activeAlarms = activeAlarms,
                cancelledTasks = cancelledTasks,
                completedTasks = completedTasks,
                taskNotes = taskNotes,
                timezoneId = timezoneId,
                autoCompleteOverdue = autoCompleteOverdue
            )
        }
    }

    private fun buildVietnamesePrompt(
        notifications: List<NotificationEntity>,
        customFixedContext: String,
        persona: PromptPersona,
        userQueryOrInstruction: String,
        previousSummary: String,
        activeAlarms: List<SmartAlarmEntity>,
        cancelledTasks: List<SmartAlarmEntity>,
        completedTasks: List<SmartAlarmEntity>,
        taskNotes: Map<String, String>,
        timezoneId: String,
        autoCompleteOverdue: Boolean
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
        if (autoCompleteOverdue) {
            contentBuilder.append("• [TỰ ĐỘNG HOÀN THÀNH KHI QUÁ HẠN]: Bất kỳ việc nào đã quá hạn so với thời điểm hiện tại thì BẮT BUỘC tự động xếp vào mục [### 🟢 NHIỆM VỤ ĐÃ LÀM (DONE)] với ghi chú '(Tự động hết hạn)'. Tuyệt đối không giữ lại ở mục PHẢI LÀM.\n\n")
        } else {
            contentBuilder.append("• Đối với những việc chưa xong mà đã quá hạn hoặc hoàn thành thụ động theo thời gian: Vẫn giữ lại theo dõi thêm 1 đến 2 phiên sau khi quá hạn, tránh vội vàng xóa bỏ làm trôi việc của người dùng.\n\n")
        }

        // 3. Ngữ cảnh nhiệm vụ dài hạn (Long-term Context & Trạng thái 2 ngày gần nhất)
        val distinctCancelled = cancelledTasks.map { it.taskTitle.trim() }.filter { it.isNotBlank() }.distinct().take(35)
        val distinctCompleted = completedTasks.map { it.taskTitle.trim() }.filter { it.isNotBlank() }.distinct().take(15)
        val hasLongTermData = activeAlarms.isNotEmpty() || distinctCancelled.isNotEmpty() || distinctCompleted.isNotEmpty()

        if (hasLongTermData) {
            contentBuilder.append("=== BỐI CẢNH DÀI HẠN & TRẠNG THÁI NHIỆM VỤ (2 NGÀY GẦN NHẤT) ===\n")
            if (activeAlarms.isNotEmpty()) {
                contentBuilder.append("• Việc đang theo dõi / hẹn giờ:\n")
                for (alarm in activeAlarms.take(15)) {
                    contentBuilder.append("  - ${alarm.taskTitle} | Hạn: ${alarm.targetDateLabel} [ĐANG THEO DÕI]\n")
                }
            }
            if (distinctCancelled.isNotEmpty()) {
                contentBuilder.append("• Việc đã bị người dùng HỦY (trong vòng 2 ngày qua):\n")
                for (title in distinctCancelled) {
                    contentBuilder.append("  - $title [ĐÃ HỦY] -> Người dùng đã hủy, tuyệt đối không tạo lại\n")
                }
            }
            if (distinctCompleted.isNotEmpty()) {
                contentBuilder.append("• Việc đã giải quyết xong:\n")
                for (title in distinctCompleted) {
                    contentBuilder.append("  - $title [ĐÃ HOÀN THÀNH]\n")
                }
            }
            contentBuilder.append("⚡ Bắt buộc tôn trọng các nhãn trên: Việc có nhãn [ĐÃ HỦY] hoặc [ĐÃ HOÀN THÀNH] thì tuyệt đối không xếp vào mục cần làm (To-Do/Planned).\n\n")
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

        // 12. HƯỚNG DẪN SUY LUẬN TỪNG BƯỚC QUA THẺ NHÁP <scratchpad>
        contentBuilder.append("\n=== NGUYÊN TẮC SUY LUẬN TỪNG BƯỚC (BẮT BUỘC DÙNG THẺ NHÁP <scratchpad>) ===\n")
        contentBuilder.append("Trước khi xuất bản nội dung báo cáo chính thức, bạn BẮT BUỘC phải thực hiện phân tích logic bên trong thẻ <scratchpad>...</scratchpad> theo đúng 5 bước:\n")
        contentBuilder.append("<scratchpad>\n")
        contentBuilder.append("1. Neo thời gian: Xác định NOW là thời điểm nào (dd/MM/yyyy HH:mm). Liệt kê các mốc thời gian tương đối trong tin nhắn (\"mai\", \"chiều nay\", \"15h\") và quy đổi sang mốc thời gian cụ thể.\n")
        contentBuilder.append("2. Lọc ý định & cam kết: Loại bỏ tin nhắn quảng cáo, mã OTP, tán gẫu không hành động. Chỉ giữ lại các việc có người giao việc hoặc cam kết rõ ràng.\n")
        contentBuilder.append("3. Phân giải mâu thuẫn: Tìm các tin nhắn dời lịch/hủy việc/đính chính; đối chiếu với danh sách việc đã làm/đã hủy để không tạo lặp.\n")
        contentBuilder.append("4. Kiểm toán quá hạn: So sánh hạn của từng việc với NOW. Nếu việc chưa hoàn thành mà đã quá hạn, đánh dấu và giữ lại theo dõi thêm 1-2 phiên theo lệnh người dùng.\n")
        contentBuilder.append("5. Đề xuất báo thức: Tính toán giờ kích hoạt chuông hợp lý (họp/hẹn báo trước 15-30p; việc trong ngày chưa có giờ cụ thể thì ước tính 09:30 hoặc 14:30).\n")
        contentBuilder.append("</scratchpad>\n\n")

        contentBuilder.append("=== QUY TẮC ĐỊNH DẠNG ĐẦU RA BẮT BUỘC (SAU KHI ĐÓNG THẺ </scratchpad>) ===\n\n")
        contentBuilder.append("Sau khi đóng thẻ </scratchpad>, bạn BẮT BUỘC xuất ra đúng cấu trúc 2 PHẦN như sau (Tuyệt đối không mang nội dung nháp ra ngoài):\n\n")

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

    private fun buildEnglishPrompt(
        notifications: List<NotificationEntity>,
        customFixedContext: String,
        persona: PromptPersona,
        userQueryOrInstruction: String,
        previousSummary: String,
        activeAlarms: List<SmartAlarmEntity>,
        cancelledTasks: List<SmartAlarmEntity>,
        completedTasks: List<SmartAlarmEntity>,
        taskNotes: Map<String, String>,
        timezoneId: String,
        autoCompleteOverdue: Boolean
    ): String {
        val groupedByApp = notifications.groupBy { it.appName }

        val tz = try { java.util.TimeZone.getTimeZone(timezoneId) } catch (_: Exception) { java.util.TimeZone.getDefault() }
        val localizedDateFormat = SimpleDateFormat("EEEE, MMMM dd, yyyy 'at' HH:mm", Locale.US).apply {
            timeZone = tz
        }
        val rawDateStr = localizedDateFormat.format(Date())
        val nowFormatted = rawDateStr.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }
        val offsetHours = tz.rawOffset / (3600 * 1000)
        val offsetStr = if (offsetHours >= 0) "+$offsetHours" else "$offsetHours"

        val contentBuilder = StringBuilder()

        // 1. Role & Tone instruction based on Persona
        contentBuilder.append(persona.getSystemRole(AppLanguage.ENGLISH)).append("\n\n")
        contentBuilder.append("=== TONE & STYLE PRINCIPLES ===\n")
        contentBuilder.append(persona.getPromptInstructions(AppLanguage.ENGLISH)).append("\n\n")

        // 2. Real-time Anchor & Time Estimation
        contentBuilder.append("=== REAL-TIME TIME ANCHOR & SCHEDULING ===\n")
        contentBuilder.append("⏰ Current Time: $nowFormatted (Timezone: ${tz.id}, GMT$offsetStr).\n")
        contentBuilder.append("• Cross-check message timestamps against the current time to compute accurate dates and propose sensible smart alarm triggers.\n")
        contentBuilder.append("• If a task lacks a specific hour, state 'Due: Today (Pending exact time)' or specify an estimated time with date.\n")
        if (autoCompleteOverdue) {
            contentBuilder.append("• [AUTO-COMPLETE OVERDUE TASKS]: Any task whose deadline has passed compared to the current time MUST automatically be placed in [### 🟢 COMPLETED TASKS (DONE)] with the note '(Auto-expired)'. Never keep expired tasks in the TO-DO section.\n\n")
        } else {
            contentBuilder.append("• For unfinished tasks that are overdue or passively resolved over time: Keep tracking them for 1 to 2 subsequent sessions past their deadline to avoid prematurely dropping tasks.\n\n")
        }

        // 3. Long-term Context & Task Status (Past 2 Days)
        val distinctCancelled = cancelledTasks.map { it.taskTitle.trim() }.filter { it.isNotBlank() }.distinct().take(35)
        val distinctCompleted = completedTasks.map { it.taskTitle.trim() }.filter { it.isNotBlank() }.distinct().take(15)
        val hasLongTermData = activeAlarms.isNotEmpty() || distinctCancelled.isNotEmpty() || distinctCompleted.isNotEmpty()

        if (hasLongTermData) {
            contentBuilder.append("=== LONG-TERM CONTEXT & TASK STATUS (PAST 2 DAYS) ===\n")
            if (activeAlarms.isNotEmpty()) {
                contentBuilder.append("• Tasks currently tracked / scheduled alarms:\n")
                for (alarm in activeAlarms.take(15)) {
                    contentBuilder.append("  - ${alarm.taskTitle} | Due: ${alarm.targetDateLabel} [TRACKING]\n")
                }
            }
            if (distinctCancelled.isNotEmpty()) {
                contentBuilder.append("• Tasks explicitly CANCELLED by user (within the past 2 days):\n")
                for (title in distinctCancelled) {
                    contentBuilder.append("  - $title [CANCELLED] -> User explicitly cancelled this task; NEVER recreate or re-add it\n")
                }
            }
            if (distinctCompleted.isNotEmpty()) {
                contentBuilder.append("• Tasks already resolved:\n")
                for (title in distinctCompleted) {
                    contentBuilder.append("  - $title [COMPLETED]\n")
                }
            }
            contentBuilder.append("⚡ Mandatory adherence: Tasks marked [CANCELLED] or [COMPLETED] must NEVER be placed in To-Do or Planned.\n\n")
        }

        // 7. Task Notes
        if (taskNotes.isNotEmpty()) {
            contentBuilder.append("=== USER'S SPECIAL INSTRUCTIONS FOR SPECIFIC TASKS ===\n")
            for ((tTitle, tNote) in taskNotes) {
                if (tTitle.isNotBlank() && tNote.isNotBlank()) {
                    contentBuilder.append("- Task: \"$tTitle\" ➜ Note: \"$tNote\"\n")
                }
            }
            contentBuilder.append("⚡ Must strictly obey these instructions to adjust priority or timing accordingly.\n\n")
        }

        // 8. Custom User Context
        if (customFixedContext.isNotBlank()) {
            contentBuilder.append("=== USER BACKGROUND & CONTEXT ===\n")
            contentBuilder.append(customFixedContext.trim())
            contentBuilder.append("\n\n")
        }

        // 9. Previous Summary Context
        if (previousSummary.isNotBlank()) {
            val compactPrev = previousSummary.trim().take(3000)
            contentBuilder.append("=== PREVIOUS SESSION REPORT (PROGRESS CONTINUITY) ===\n")
            contentBuilder.append(compactPrev)
            contentBuilder.append("\n⚡ Continue progress from the previous session; unfinished or overdue tasks stay tracked for 1-2 sessions according to the rules above.\n\n")
        }

        // 10. Live User Query
        if (userQueryOrInstruction.isNotBlank()) {
            contentBuilder.append("=== DIRECT USER REQUEST / INSTRUCTION ===\n")
            contentBuilder.append("💬 \"${userQueryOrInstruction.trim()}\"\n")
            contentBuilder.append("👉 Address this directly in [${persona.getAdviceSectionTitle(AppLanguage.ENGLISH)}] and coordinate the task board accordingly.\n\n")
        }

        // 11. Notifications Data
        contentBuilder.append("=== MESSAGE DATA (${notifications.size} messages) ===\n")

        if (notifications.isEmpty()) {
            contentBuilder.append("(No new messages in this shift. Please answer the user's direct query/request above in English).\n")
        } else {
            for ((appName, appNotifications) in groupedByApp) {
                contentBuilder.append("\n【 Application: $appName 】\n")
                val groupedBySender = appNotifications.groupBy { it.sender }

                for ((sender, senderMsgs) in groupedBySender) {
                    contentBuilder.append("- Sender: **$sender**\n")
                    for (msg in senderMsgs) {
                        val timeStr = timeFormat.format(Date(msg.timestamp))
                        val sanitizedContent = PiiSanitizer.sanitize(msg.content)
                        contentBuilder.append("  • [$timeStr] $sanitizedContent\n")
                    }
                }
            }
        }

        // 12. HƯỚNG DẪN SUY LUẬN TỪNG BƯỚC QUA THẺ NHÁP <scratchpad>
        contentBuilder.append("\n=== STEP-BY-STEP REASONING GUIDELINES (MANDATORY <scratchpad> TAG) ===\n")
        contentBuilder.append("Before outputting the official report, you MUST perform logical analysis inside <scratchpad>...</scratchpad> following these 5 steps in English:\n")
        contentBuilder.append("<scratchpad>\n")
        contentBuilder.append("1. Time Anchor: Determine current time NOW (dd/MM/yyyy HH:mm). Convert relative time expressions (\"tomorrow\", \"this afternoon\", \"3pm\") into concrete dates/times.\n")
        contentBuilder.append("2. Filter Intent & Commitment: Discard spam, OTP codes, social chitchat without action items. Retain only tasks with clear assigners or commitments.\n")
        contentBuilder.append("3. Conflict Resolution: Identify reschedule, cancellation, and correction messages; cross-check with completed and cancelled tasks to prevent duplicate creation.\n")
        contentBuilder.append("4. Overdue Audit: Compare each task's deadline with NOW. If overdue and unfinished, mark and retain for 1-2 sessions or auto-expire per user policy.\n")
        contentBuilder.append("5. Smart Alarm Proposal: Calculate optimal alert triggers (15-30m prior for meetings; estimate 09:30 or 14:30 for open tasks).\n")
        contentBuilder.append("</scratchpad>\n\n")

        contentBuilder.append("=== MANDATORY OUTPUT FORMAT RULES (AFTER CLOSING </scratchpad>) ===\n\n")
        contentBuilder.append("After closing </scratchpad>, you MUST output strictly in the following 2-PART structure (All content must be in ENGLISH, no scratchpad leaks):\n\n")

        val adviceTitle = persona.getAdviceSectionTitle(AppLanguage.ENGLISH)
        val noteTitle = persona.getNoteSectionTitle(AppLanguage.ENGLISH)
        contentBuilder.append("=== PART 1: ${adviceTitle.uppercase()} & ${noteTitle.uppercase()} ===\n")
        contentBuilder.append("1. [$adviceTitle]: ${persona.getAdviceDescription(AppLanguage.ENGLISH)}\n")
        contentBuilder.append("2. [$noteTitle]: ${persona.getNoteDescription(AppLanguage.ENGLISH)}\n\n")

        contentBuilder.append("=== PART 2: TASK DISPATCH BOARD ===\n")
        contentBuilder.append("LINE-BY-LINE SYNTAX RULES (CRITICAL FOR PARSER COMPATIBILITY):\n")
        contentBuilder.append("• Each task MUST be on EXACTLY ONE SINGLE LINE using syntax: [Concise task title] | Source: [Sender name] | Due: [Proposed time/date]\n")
        contentBuilder.append("• NEVER use bold markdown (**) within the task title.\n")
        contentBuilder.append("• NEVER insert line breaks within a single task line.\n")
        contentBuilder.append("• You MUST use these exact 3 section headings:\n\n")

        contentBuilder.append("### 🔴 TO-DO TASKS\n")
        contentBuilder.append("(Includes new actionable tasks and retained overdue/ongoing items)\n")
        contentBuilder.append("- [Task to do] | Source: [Sender] | Due: [Proposed time/date]\n\n")

        contentBuilder.append("### 🟢 COMPLETED TASKS (DONE)\n")
        contentBuilder.append("(Keep at most 1-2 important recently finished tasks with timestamps; do not list trivial finished items)\n")
        contentBuilder.append("- [Important finished task] | Source: [Sender] | Time: [Completed at HH:mm]\n\n")

        contentBuilder.append("### 🟡 PLANNED TASKS\n")
        contentBuilder.append("- [Upcoming event/future plan] | Source: [Sender] | Target: [Date/Time]\n")

        return contentBuilder.toString()
    }
}
