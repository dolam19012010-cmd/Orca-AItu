package com.orca.orcaauto.domain.assistant

import com.orca.orcaauto.data.local.NotificationDao
import com.orca.orcaauto.data.local.NotificationEntity
import com.orca.orcaauto.domain.notes.RetrievedNote
import com.orca.orcaauto.domain.notes.SharedNoteRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Stage 3: LocalRetriever
 *
 * Retrieves local notes and relevant notifications based on Round 1 routing decisions.
 *
 * STRICT SECURITY INVARIANT (SEALED POINTER):
 * If a note has isPrivate = true, it is represented as a [RetrievedNote.SealedPrivatePointer].
 * Zero bytes of private note content are loaded into memory for network egress.
 * AI receives only a local sealed pointer notice instructing it to use [SHOW_NOTE:id] or [APPEND:id:...].
 */
class LocalRetriever(
    private val noteRepository: SharedNoteRepository,
    val notificationDao: NotificationDao
) {

    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    suspend fun retrieve(
        userPrompt: String,
        decision: Round1Decision,
        excludedNotificationIds: Set<Long> = emptySet()
    ): RetrievedContext {
        // 1. Retrieve notes from Room DB
        val retrievedNotes = try {
            noteRepository.retrieveNotesForAi(decision.neededNoteIds)
        } catch (e: Exception) {
            emptyList()
        }

        // 2. Retrieve relevant notifications if sender filters were determined
        val relevantNotifications = retrieveRelevantNotifications(decision.senderFilter, excludedNotificationIds)

        // 3. Assemble notes snippet (strictly enforcing Sealed Pointer)
        val notesSnippet = buildNotesSnippet(retrievedNotes)

        // 4. Assemble notifications snippet
        val notificationsSnippet = buildNotificationsSnippet(relevantNotifications)

        // 5. Build system instructions and Round 2 prompt
        val systemInstruction = buildSystemInstruction()
        val assembledPrompt = buildRound2Prompt(userPrompt, notesSnippet, notificationsSnippet)

        return RetrievedContext(
            retrievedNotes = retrievedNotes,
            relevantNotificationsSnippet = notificationsSnippet,
            assembledRound2Prompt = assembledPrompt,
            systemInstruction = systemInstruction
        )
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
            sanitizePii = true
        )
        return MessageCoalescer.formatForPrompt(coalesced, withTicketIds = false)
    }

    private fun buildSystemInstruction(): String {
        return """
        Bạn là Trợ lý AI thông minh tích hợp trên thiết bị của người dùng (OrcaAuto).
        Nhiệm vụ của bạn là trả lời câu hỏi của người dùng một cách chính xác, tự nhiên, lịch sự bằng tiếng Việt.
        
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
        notificationsSnippet: String
    ): String {
        return buildString {
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
