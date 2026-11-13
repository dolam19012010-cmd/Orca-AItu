package com.orca.orcaauto.domain.assistant

import com.orca.orcaauto.data.local.NotificationDao
import com.orca.orcaauto.data.local.NotificationEntity
import com.orca.orcaauto.domain.filter.NotificationFilter
import com.orca.orcaauto.domain.notes.NoteCatalogItem
import com.orca.orcaauto.domain.notes.SharedNoteRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Stage 1: ContextPacker
 *
 * Packs a lightweight index containing note metadata (ID, title, isPrivate, updatedAt)
 * and a 24-hour notification sender/app overview.
 *
 * CRITICAL SECURITY INVARIANT:
 * Zero bytes of private note content are accessed or emitted.
 */
class ContextPacker(
    private val noteRepository: SharedNoteRepository,
    private val notificationDao: NotificationDao
) {

    private val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).apply {
        timeZone = vnTimeZone
    }
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault()).apply {
        timeZone = vnTimeZone
    }

    fun getRealTimeAnchorLine(timestamp: Long = System.currentTimeMillis()): String =
        Companion.getRealTimeAnchorLine(timestamp)

    suspend fun pack(userPrompt: String): PackagedContext {
        val catalogItems = try {
            noteRepository.getCatalogForAi()
        } catch (e: Exception) {
            emptyList()
        }

        val notifications = try {
            notificationDao.getNotificationsWithin24Hours(limit = 100)
        } catch (e: Exception) {
            emptyList()
        }.filterNot { NotificationFilter.containsSystemCommandInjection(it.content) || NotificationFilter.containsSystemCommandInjection(it.sender) }

        val catalogText = buildCatalogSnippet(catalogItems)
        val notificationSummaryText = buildNotificationSummarySnippet(notifications)

        // Conditional Adaptive Message Compression (Round 1 only)
        val totalCount = notifications.size
        val processedCount = notifications.count { it.isProcessed }
        val isCompressionActive = shouldCompress(totalCount, processedCount)

        val notificationsForCoalesce = if (isCompressionActive) {
            notifications.map { notif ->
                if (notif.isProcessed) {
                    notif.copy(content = compressMessageContent(notif.content))
                } else {
                    notif // Unprocessed messages: keep 100% verbatim
                }
            }
        } else {
            notifications // Keep 100% verbatim
        }

        val coalesceResult = MessageCoalescer.coalesce(
            notifications = notificationsForCoalesce,
            assignTicketIds = true,
            sanitizePii = false
        )
        val ticketedDetail = MessageCoalescer.formatForPrompt(coalesceResult, withTicketIds = true)

        val fullPrompt = buildString {
            appendLine(getRealTimeAnchorLine())
            appendLine()
            appendLine("=== DANH MỤC KHO GHI CHÚ (MỤC LỤC NHẸ - 0 BYTE NỘI DUNG RIÊNG TƯ) ===")
            appendLine(catalogText)
            appendLine()
            appendLine("=== TỔNG QUAN TIN NHẮN 24H GẦN ĐÂY ===")
            appendLine(notificationSummaryText)
            if (notifications.isNotEmpty() && ticketedDetail.isNotBlank() && ticketedDetail != "(Không có tin nhắn liên quan nào)") {
                appendLine()
                appendLine("=== CHI TIẾT TIN NHẮN 24H KÈM VÉ ID (ĐỂ NHẬN DIỆN VÀ XÓA SPAM) ===")
                appendLine(ticketedDetail)
            }
            appendLine()
            appendLine("=== YÊU CẦU CỦA NGƯỜI DÙNG ===")
            appendLine(userPrompt.trim())
        }

        return PackagedContext(
            userPrompt = userPrompt,
            catalogItems = catalogItems,
            catalogPromptSnippet = catalogText,
            notificationSummarySnippet = notificationSummaryText,
            fullOverviewPrompt = fullPrompt,
            ticketMap = coalesceResult.ticketMap
        )
    }

    private fun buildCatalogSnippet(items: List<NoteCatalogItem>): String {
        if (items.isEmpty()) {
            return "(Kho ghi chú hiện tại đang trống)"
        }

        return items.joinToString("\n") { item ->
            val privacyTag = if (item.isPrivate) "[🔒 RIÊNG TƯ - CHỈ POINTER CỤC BỘ]" else "[Thường]"
            val createdStr = try {
                dateFormat.format(Date(item.createdAt))
            } catch (e: Exception) {
                "${item.createdAt}"
            }
            val updatedStr = try {
                dateFormat.format(Date(item.updatedAt))
            } catch (e: Exception) {
                "${item.updatedAt}"
            }
            "- [ID: ${item.id}] ${if (item.isPinned) "[📌 ĐÃ GHIM] " else ""}\"${item.title}\" $privacyTag | Tạo: $createdStr | Cập nhật: $updatedStr"
        }
    }

    private fun buildNotificationSummarySnippet(notifications: List<NotificationEntity>): String {
        if (notifications.isEmpty()) {
            return "(Không có thông báo/tin nhắn mới trong 24 giờ qua)"
        }

        // Group by appName/package and sender
        val grouped = notifications.groupBy { notification ->
            val app = notification.appName.ifBlank { notification.appPackage }
            val sender = notification.sender.ifBlank { "Không rõ người gửi" }
            "$app: $sender"
        }

        return grouped.entries.joinToString("\n") { (key, list) ->
            val latest = list.maxByOrNull { it.timestamp }
            val latestTime = latest?.let {
                try {
                    timeFormat.format(Date(it.timestamp))
                } catch (e: Exception) {
                    ""
                }
            }.orEmpty()
            val timeSuffix = if (latestTime.isNotBlank()) " (Gần nhất lúc $latestTime)" else ""
            "- $key: ${list.size} tin nhắn$timeSuffix"
        }
    }

    companion object {
        val vnLocale = Locale("vi", "VN")
        val vnTimeZone: TimeZone = TimeZone.getTimeZone("Asia/Ho_Chi_Minh")

        fun getRealTimeAnchorLine(timestamp: Long = System.currentTimeMillis()): String {
            val date = Date(timestamp)
            val dayFormat = SimpleDateFormat("EEEE", vnLocale).apply { timeZone = vnTimeZone }
            val restFormat = SimpleDateFormat("'ngày' dd/MM/yyyy 'lúc' HH:mm", vnLocale).apply { timeZone = vnTimeZone }

            val rawDay = dayFormat.format(date)
            val capitalizedDay = rawDay.split(" ").joinToString(" ") { word ->
                word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(vnLocale) else it.toString() }
            }
            val rest = restFormat.format(date)
            return "⏰ Thời điểm hiện tại: $capitalizedDay, $rest (Múi giờ: Asia/Ho_Chi_Minh)"
        }

        fun shouldCompress(totalCount: Int, processedCount: Int): Boolean {
            return totalCount >= 20 && processedCount >= 10 && (processedCount.toDouble() / totalCount) >= 0.6
        }

        fun compressMessageContent(raw: String): String {
            val trimmed = raw.trim()
            if (trimmed.length <= 25) return trimmed

            var text = trimmed

            // Strip conversational greeting prefixes
            val greetingPrefixRegex = Regex(
                """^(?:dạ\s+|vâng\s+|alo\s+|xin\s+chào\s+|chào\s+(?:bạn|anh|chị|em|cả\s+nhà|mọi\s+người|mn)\s*[,.-]?\s*)+""",
                RegexOption.IGNORE_CASE
            )
            text = greetingPrefixRegex.replace(text, "")

            // Strip conversational filler question lead-ins
            val leadInRegex = Regex(
                """^(?:cho\s+em\s+hỏi\s+(?:là\s+)?|cho\s+mình\s+hỏi\s+(?:là\s+)?|bạn\s+cho\s+mình\s+hỏi\s+|em\s+báo\s+(?:với\s+)?(?:anh|chị)\s+(?:là\s+)?|anh\s+ơi\s+|chị\s+ơi\s+|em\s+ơi\s+)""",
                RegexOption.IGNORE_CASE
            )
            text = leadInRegex.replace(text, "")

            // Strip polite conversational endings and particles
            val closingParticleRegex = Regex(
                """\s+(?:nhé|nha|nhá|nhe|ạ|với\s+ạ|giúp\s+em\s+với\s+ạ|giúp\s+em\s+nhé|đó\s+ạ|nhe\s+bạn)\s*([.!?]*)$""",
                RegexOption.IGNORE_CASE
            )
            text = closingParticleRegex.replace(text, "$1")

            // Normalize multiple spaces and multiple newlines
            text = text.replace(Regex("""[ \t]+"""), " ")
                .replace(Regex("""\n{2,}"""), "\n")
                .trim()

            return if (text.isNotBlank()) text else trimmed
        }
    }
}
