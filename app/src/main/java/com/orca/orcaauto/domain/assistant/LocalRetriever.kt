package com.orca.orcaauto.domain.assistant

import com.orca.orcaauto.data.local.NotificationDao
import com.orca.orcaauto.data.local.NotificationEntity
import com.orca.orcaauto.data.local.SharedNoteEntity
import com.orca.orcaauto.data.local.SmartAlarmDao
import com.orca.orcaauto.data.local.SmartAlarmEntity
import com.orca.orcaauto.domain.filter.NotificationFilter
import com.orca.orcaauto.domain.notes.RetrievedNote
import com.orca.orcaauto.domain.notes.SharedNoteRepository
import com.orca.orcaauto.domain.prompt.PromptPersona
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Stage 3: LocalRetriever
 *
 * Retrieves local notes, active alarms/tasks, previous session summary, and relevant notifications
 * based on Round 1 routing decisions and unified session engine.
 *
 * STRICT SECURITY INVARIANT (SEALED POINTER):
 * If a note has isPrivate = true, it is represented as a [RetrievedNote.SealedPrivatePointer].
 * Zero bytes of private note content are loaded into memory for network egress.
 * AI receives only a local sealed pointer notice instructing it to use [SHOW_NOTE:id] or [APPEND:id:...].
 */
class LocalRetriever(
    private val noteRepository: SharedNoteRepository,
    val notificationDao: NotificationDao,
    val smartAlarmDao: SmartAlarmDao? = null
) {

    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    fun getRealTimeAnchorLine(timestamp: Long = System.currentTimeMillis()): String =
        ContextPacker.getRealTimeAnchorLine(timestamp)

    companion object {
        fun getRealTimeAnchorLine(timestamp: Long = System.currentTimeMillis()): String =
            ContextPacker.getRealTimeAnchorLine(timestamp)
    }

    suspend fun retrieve(
        userPrompt: String,
        decision: Round1Decision,
        excludedNotificationIds: Set<Long> = emptySet(),
        persona: PromptPersona = PromptPersona.GENTLE_ASSISTANT,
        activeAlarms: List<SmartAlarmEntity> = emptyList(),
        previousSummary: String? = null,
        sessionInfo: String? = null,
        notesCatalog: List<SharedNoteEntity> = emptyList()
    ): RetrievedContext {
        // 1. Retrieve notes from Room DB
        val retrievedNotes = try {
            noteRepository.retrieveNotesForAi(decision.neededNoteIds)
        } catch (e: Exception) {
            emptyList()
        }

        // 2. Retrieve relevant notifications if sender filters were determined
        val relevantNotifications = retrieveRelevantNotifications(decision.senderFilter, excludedNotificationIds)

        // 3. Resolve active smart alarms
        val resolvedAlarms = if (activeAlarms.isNotEmpty()) {
            activeAlarms
        } else {
            try {
                smartAlarmDao?.getActiveAlarms() ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
        }

        // 4. Resolve notes catalog (preserving 0-byte private protection)
        val catalogSnippet = buildCatalogSnippet(notesCatalog)

        // 5. Assemble notes snippet (strictly enforcing Sealed Pointer)
        val notesSnippet = buildNotesSnippet(retrievedNotes)

        // 6. Assemble notifications snippet
        val notificationsSnippet = buildNotificationsSnippet(relevantNotifications)

        // 7. Build system instructions and Round 2 prompt
        val systemInstruction = buildSystemInstruction(persona)
        val assembledPrompt = buildRound2Prompt(
            userPrompt = userPrompt,
            notesSnippet = notesSnippet,
            notificationsSnippet = notificationsSnippet,
            activeAlarms = resolvedAlarms,
            previousSummary = previousSummary,
            sessionInfo = sessionInfo,
            catalogSnippet = catalogSnippet
        )

        return RetrievedContext(
            retrievedNotes = retrievedNotes,
            relevantNotificationsSnippet = notificationsSnippet,
            assembledRound2Prompt = assembledPrompt,
            systemInstruction = systemInstruction
        )
    }

    private suspend fun buildCatalogSnippet(notesCatalog: List<SharedNoteEntity>): String {
        return if (notesCatalog.isNotEmpty()) {
            notesCatalog.joinToString("\n") { note ->
                val pinTag = if (note.isPinned) "[📌 ĐÃ GHIM] " else ""
                if (note.isPrivate) {
                    "- [ID: ${note.id}] $pinTag\"${note.title}\" [🔒 RIÊNG TƯ - CHỈ POINTER CỤC BỘ] | (Bảo mật cục bộ - 0 byte nội dung)"
                } else {
                    "- [ID: ${note.id}] $pinTag\"${note.title}\" [Công khai]"
                }
            }
        } else {
            try {
                val repoCatalog = noteRepository.getCatalogForAi()
                if (repoCatalog.isEmpty()) {
                    "(Kho ghi chú hiện tại đang trống)"
                } else {
                    repoCatalog.joinToString("\n") { item ->
                        val pinTag = if (item.isPinned) "[📌 ĐÃ GHIM] " else ""
                        if (item.isPrivate) {
                            "- [ID: ${item.id}] $pinTag\"${item.title}\" [🔒 RIÊNG TƯ - CHỈ POINTER CỤC BỘ] | (Bảo mật cục bộ - 0 byte nội dung)"
                        } else {
                            "- [ID: ${item.id}] $pinTag\"${item.title}\" [Công khai]"
                        }
                    }
                }
            } catch (e: Exception) {
                "(Kho ghi chú hiện tại đang trống)"
            }
        }
    }

    private fun buildNotesSnippet(notes: List<RetrievedNote>): String {
        if (notes.isEmpty()) {
            return "(Không có ghi chú nào được trích xuất cho yêu cầu này)"
        }

        return notes.joinToString("\n\n") { note ->
            when (note) {
                is RetrievedNote.PublicNote -> {
                    """
                    === GHI CHÚ #${note.id}: "${note.title}" ===
                    ${note.content.ifBlank { "(Ghi chú chưa có nội dung)" }}
                    """.trimIndent()
                }
                is RetrievedNote.SealedPrivatePointer -> {
                    """
                    === GHI CHÚ RIÊNG TƯ #${note.id}: "${note.title}" (BẢO MẬT CỤC BỘ) ===
                    ${note.pointerNotice}
                    [LƯU Ý DÀNH CHO AI]:
                    - Nội dung ghi chú này được mã hóa bảo mật cục bộ trên thiết bị của người dùng.
                    - AI KHÔNG thể đọc nội dung cũ và KHÔNG ĐƯỢC bịa đặt nội dung.
                    - Để người dùng xem nội dung ghi chú này, hãy đưa lệnh [SHOW_NOTE:${note.id}] vào câu trả lời của bạn.
                    - Bạn có thể ghi thêm nội dung vào ghi chú này bằng lệnh [APPEND: ${note.id} | nội dung cần thêm].
                    - Tuyệt đối KHÔNG sử dụng [UPDATE: ${note.id} | ...] hoặc [DELETE: ${note.id}] vì hệ thống sẽ từ chối ngay lập tức.
                    """.trimIndent()
                }
            }
        }
    }

    private suspend fun retrieveRelevantNotifications(
        senderFilters: List<String>,
        excludedNotificationIds: Set<Long> = emptySet()
    ): List<NotificationEntity> {
        val all24h = try {
            notificationDao.getNotificationsWithin24Hours(limit = 100)
        } catch (e: Exception) {
            emptyList()
        }.filter { it.id !in excludedNotificationIds }
         .filterNot { NotificationFilter.containsSystemCommandInjection(it.content) || NotificationFilter.containsSystemCommandInjection(it.sender) }

        if (senderFilters.isEmpty()) {
            return all24h.takeLast(20) // Default to latest 20 messages if no specific sender filter
        }

        val lowerFilters = senderFilters.map { it.lowercase().trim() }
        val filtered = all24h.filter { notif ->
            val senderLower = notif.sender.lowercase()
            val appLower = notif.appName.lowercase()
            val contentLower = notif.content.lowercase()
            lowerFilters.any { filter ->
                senderLower.contains(filter) || appLower.contains(filter) || contentLower.contains(filter)
            }
        }

        return if (filtered.isNotEmpty()) filtered else all24h.takeLast(10)
    }

    private fun buildNotificationsSnippet(notifications: List<NotificationEntity>): String {
        if (notifications.isEmpty()) {
            return "(Không có tin nhắn liên quan nào)"
        }
        val coalesced = MessageCoalescer.coalesce(
            notifications = notifications,
            assignTicketIds = false,
            sanitizePii = false
        )
        return MessageCoalescer.formatForPrompt(coalesced, withTicketIds = false)
    }

    private fun buildSystemInstruction(persona: PromptPersona = PromptPersona.GENTLE_ASSISTANT): String {
        return """
        ${persona.systemRole}
        Nhiệm vụ của bạn là trả lời câu hỏi của người dùng một cách chính xác, tự nhiên, lịch sự bằng tiếng Việt.
        
        PHONG CÁCH VÀ THÁI ĐỘ:
        ${persona.promptInstructions}
        - Hãy trả lời một cách tự nhiên, mạch lạc, trực tiếp vào trọng tâm câu hỏi của người dùng.
        - TUYỆT ĐỐI KHÔNG tự động chia tiêu đề cứng như 'Lời nhắn nhẹ nhàng' hay 'Lưu ý tinh tế' trừ khi người dùng yêu cầu tóm tắt phiên làm việc.
        
        QUY TẮC BẮT BUỘC PHÂN BIỆT RÕ RÀNG GIỮA NHIỆM VỤ / BÁO THỨC (TASKS / SMART ALARMS) VÀ GHI CHÚ (NOTES):
        1. Phân định bản chất và nguồn dữ liệu:
           - Nhiệm vụ / Báo thức (Bảng Nhiệm Vụ Ca / TaskBoard / Smart Alarms): Là các công việc, sự kiện, ca trực, lịch hẹn có mốc thời gian cụ thể (ví dụ: 'tập huấn PCCC', 'họp giao ban 15h', 'uống thuốc 20h', v.v.). Chúng xuất hiện trong mục 'DANH SÁCH BÁO THỨC & NHIỆM VỤ ĐANG HOẠT ĐỘNG (TASKBOARD / SMART ALARMS)'.
           - Ghi chú (Kho Ghi Chú / Smart Notes): Là các văn bản, tài liệu, danh sách lưu trữ dài hạn trong kho ghi chú cá nhân (có ID dạng số như #1, #2...). Chúng xuất hiện trong mục 'MỤC LỤC KHO GHI CHÚ (SMART NOTES CATALOG)' hoặc 'THÔNG TIN GHI CHÚ TRÍCH XUẤT CỤC BỘ'.
        2. Quy tắc hành vi khi người dùng yêu cầu thao tác với Nhiệm vụ / Báo thức:
           - Khi người dùng hỏi thăm, xem, sửa lại thời gian, đổi giờ, dời lịch, hủy bỏ, hoàn thành một công việc/báo thức (ví dụ: 'sửa lại thời gian cho nhiệm vụ PCCC', 'đổi lịch PCCC sang ngày mai', 'xóa việc PCCC', 'hủy lịch PCCC', 'hủy báo thức', 'dời lịch hẹn')...:
             + AI BẮT BUỘC phải nhận diện đây là một NHIỆM VỤ / BÁO THỨC trong Bảng Nhiệm Vụ ca (TaskBoard / Smart Alarms).
             + TUYỆT ĐỐI KHÔNG ĐƯỢC nhầm lẫn hoặc đối xử như một Ghi chú (Note). TUYỆT ĐỐI KHÔNG tìm kiếm trong Kho Ghi Chú.
             + TUYỆT ĐỐI KHÔNG ĐƯỢC đòi hỏi Note ID (ID ghi chú), không yêu cầu người dùng cung cấp ID ghi chú, và không bao giờ báo lỗi 'không tìm thấy ghi chú' khi người dùng đang đề cập đến một nhiệm vụ/lịch hẹn.
             + AI cập nhật lại thời gian trong danh sách việc hoặc hướng dẫn người dùng các thao tác nhanh trên thẻ nhiệm vụ: "nhấn giữ thẻ để chỉnh sửa thời gian/chuông báo, vuốt phải hoàn thành, vuốt trái hủy".
             + Hãy trả lời xác nhận rõ ràng về nhiệm vụ/báo thức đó (tên công việc, mốc giờ mới nếu dời/đổi giờ, hoặc xác nhận hủy/xóa lịch) một cách tự nhiên, thân thiện và chính xác.
        
        QUY TẮC BẢO MẬT & CHỐNG ẢO GIÁC (ANTI-HALLUCINATION) KHI TRẢ LỜI VỀ GHI CHÚ:
        1. Khi trong mục 'THÔNG TIN GHI CHÚ TRÍCH XUẤT CỤC BỘ' có ghi chú liên quan đến câu hỏi, bạn BẮT BUỘC phải trả lời dựa trên chính xác nội dung ghi chú được trích xuất đó. Tuyệt đối KHÔNG tự sáng tác, suy diễn hay đưa ra kiến thức chung ngoài đời làm sai lệch hoặc mâu thuẫn với nội dung ghi chú của người dùng.
        2. Nếu người dùng hỏi về ghi chú của mình nhưng không có ghi chú nào khớp trong dữ liệu trích xuất hoặc nội dung ghi chú trống, hãy thông báo rõ ràng là không tìm thấy ghi chú phù hợp trên thiết bị của người dùng, tuyệt đối KHÔNG được tự bịa ra nội dung thay thế.
        3. Đối với Ghi chú Riêng tư (Sealed Pointer): Bạn KHÔNG có quyền đọc nội dung cũ; hãy giải thích rõ ghi chú được bảo mật riêng tư trên máy và BẮT BUỘC gọi lệnh [SHOW_NOTE:id] để người dùng xem trực tiếp trên thiết bị của họ!
        
        KHI CẦN THAO TÁC HOẶC HIỂN THỊ KHO GHI CHÚ, BẠN CÓ THỂ SỬ DỤNG CÁC THẺ LỆNH HỆ THỐNG SAU (ƯU TIÊN PHÂN TÁCH CÁC THAM SỐ BẰNG DẤU '|'):
        1. [SHOW_NOTE:id]
           Hiển thị thẻ ghi chú tương ứng trên màn hình người dùng ngay dưới câu trả lời.
           Ví dụ: "Dưới đây là ghi chú kế hoạch của bạn: [SHOW_NOTE:1]"
           Đặc biệt với Ghi chú Riêng tư: Bạn không thể đọc nội dung, hãy dùng [SHOW_NOTE:id] để người dùng xem trực tiếp trên máy của họ!
        
        2. [CREATE: Tiêu đề | Nội dung | private] hoặc [CREATE: Tiêu đề | Nội dung | public]
           Tạo một ghi chú mới vào kho ghi chú của người dùng. Nếu không chỉ định cờ thứ 3, mặc định là public.
           LƯU Ý QUAN TRỌNG: Chỉ sử dụng lệnh này khi thật sự cần thiết hoặc khi được người dùng yêu cầu rõ ràng (ví dụ: bảo lưu lại, ghi nhớ vào máy, tạo ghi chú). Tuyệt đối không tự tiện tạo ghi chú cho các câu hỏi đáp thông thường, các cuộc trò chuyện chào hỏi, hoặc các đầu việc ngắn hạn hàng ngày để tránh làm rác kho ghi chú cá nhân của người dùng.
           DÙNG DẤU '|' ĐỂ PHÂN TÁCH, cho phép tiêu đề và nội dung chứa mốc giờ (ví dụ 14:30) hoặc dấu hai chấm ':' mà không bị lỗi.
           Ví dụ: [CREATE: Lịch họp 14:30 | Gặp đối tác tại phòng 201 lúc 15:00 | public]
           Ví dụ ghi chú riêng tư: [CREATE: Mật khẩu két sắt | Mã số 8899 lúc 10:00 | private]
        
        3. [APPEND: id | nội dung thêm]
           Thêm nội dung vào cuối một ghi chú đã có (áp dụng cho cả ghi chú thường và ghi chú riêng tư).
           Dùng dấu '|' để nội dung thêm thoải mái chứa mốc giờ hoặc dấu ':'.
           Ví dụ: [APPEND: 4 | \n- Đã thanh toán tiền điện thoại tháng 11 lúc 14:30]
        
        4. [UPDATE: id | nội dung mới]
           Cập nhật toàn bộ nội dung của ghi chú thường (CHỈ áp dụng cho ghi chú thường, CẤM dùng trên ghi chú riêng tư).
           Ví dụ: [UPDATE: 2 | Danh sách cập nhật mới lúc 09:00: Mục 1, Mục 2]
        
        5. [DELETE: id]
           Xóa ghi chú thường vào thùng rác (CẤM dùng trên ghi chú riêng tư).
           Ví dụ: [DELETE: 5]
        
        QUY TẮC HIỂN THỊ:
        - Ứng dụng sẽ tự động ẩn các thẻ lệnh trên khỏi văn bản mà người dùng nhìn thấy, đồng thời chuyển chúng thành các hành động thực tế (hiển thị thẻ ghi chú, thêm ghi chú, v.v.).
        - Hãy trả lời một cách tự nhiên, mạch lạc, giải thích rõ bạn đã thực hiện hoặc hiển thị điều gì cho người dùng.
        """.trimIndent()
    }

    private fun buildRound2Prompt(
        userPrompt: String,
        notesSnippet: String,
        notificationsSnippet: String,
        activeAlarms: List<SmartAlarmEntity> = emptyList(),
        previousSummary: String? = null,
        sessionInfo: String? = null,
        catalogSnippet: String = ""
    ): String {
        return buildString {
            appendLine(getRealTimeAnchorLine())
            appendLine()

            if (!sessionInfo.isNullOrBlank()) {
                appendLine("=== THÔNG TIN PHIÊN LÀM VIỆC HIỆN TẠI ===")
                appendLine(sessionInfo.trim())
                appendLine()
            }

            if (!previousSummary.isNullOrBlank()) {
                appendLine("=== BỐI CẢNH TÓM TẮT PHIÊN TRƯỚC (PREVIOUS SESSION SUMMARY) ===")
                appendLine(previousSummary.trim())
                appendLine("LƯU Ý: Đây là tóm tắt phiên làm việc gần nhất để bạn nắm được bối cảnh liên tục của người dùng.")
                appendLine()
            }

            appendLine("=== DANH SÁCH BÁO THỨC & NHIỆM VỤ ĐANG HOẠT ĐỘNG (TASKBOARD / SMART ALARMS) ===")
            if (activeAlarms.isEmpty()) {
                appendLine("(Hiện không có báo thức hay nhiệm vụ nào đang chạy)")
            } else {
                activeAlarms.forEach { alarm ->
                    appendLine("• [Mã: ${alarm.alarmId}] \"${alarm.taskTitle}\" | Hạn: ${alarm.targetDateLabel} (Trạng thái: ${alarm.status})")
                }
            }
            appendLine("LƯU Ý ĐẶC BIỆT: Đây là các NHIỆM VỤ / BÁO THỨC trên bảng công việc (TaskBoard). Khi người dùng hỏi, yêu cầu sửa, đổi giờ, dời lịch hoặc hủy các đầu việc này, hãy đối chiếu trực tiếp danh sách trên. TUYỆT ĐỐI KHÔNG coi đây là Ghi chú (Notes) và KHÔNG ĐƯỢC đòi Note ID!")
            appendLine()

            if (catalogSnippet.isNotBlank()) {
                appendLine("=== MỤC LỤC KHO GHI CHÚ (SMART NOTES CATALOG) ===")
                appendLine(catalogSnippet.trim())
                appendLine("LƯU Ý: Đây là mục lục tiêu đề các ghi chú trong máy người dùng (nội dung riêng tư được bảo mật 0-byte).")
                appendLine()
            }

            appendLine("=== THÔNG TIN GHI CHÚ TRÍCH XUẤT CỤC BỘ ===")
            appendLine(notesSnippet)
            appendLine("LƯU Ý: Nếu thông tin trên có ghi chú liên quan đến câu hỏi, hãy ưu tiên và bám sát tuyệt đối nội dung này để trả lời trung thực, không tự bịa đặt kiến thức ngoài lề.")
            appendLine()

            appendLine("=== TIN NHẮN VÀ THÔNG BÁO 24H LIÊN QUAN ===")
            appendLine(notificationsSnippet)
            appendLine()

            appendLine("=== YÊU CẦU CỦA NGƯỜI DÙNG ===")
            appendLine(userPrompt.trim())
        }
    }
}
