package com.orca.orcaauto.domain.assistant

import com.orca.orcaauto.data.local.NotificationDao
import com.orca.orcaauto.data.local.NotificationEntity
import com.orca.orcaauto.domain.notes.NoteCatalogItem
import com.orca.orcaauto.domain.notes.SharedNoteRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

    private val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    suspend fun pack(userPrompt: String): PackagedContext {
        val catalogItems = try {
            noteRepository.getCatalogForAi()
        } catch (e: Exception) {
            emptyList()
        }

        val catalogText = buildCatalogSnippet(catalogItems)
        val notificationSummaryText = buildNotificationSummarySnippet()

        val fullPrompt = buildString {
            appendLine("=== DANH MỤC KHO GHI CHÚ (MỤC LỤC NHẸ - 0 BYTE NỘI DUNG RIÊNG TƯ) ===")
            appendLine(catalogText)
            appendLine()
            appendLine("=== TỔNG QUAN TIN NHẮN 24H GẦN ĐÂY ===")
            appendLine(notificationSummaryText)
            appendLine()
            appendLine("=== YÊU CẦU CỦA NGƯỜI DÙNG ===")
            appendLine(userPrompt.trim())
        }

        return PackagedContext(
            userPrompt = userPrompt,
            catalogItems = catalogItems,
            catalogPromptSnippet = catalogText,
            notificationSummarySnippet = notificationSummaryText,
            fullOverviewPrompt = fullPrompt
        )
    }

    private fun buildCatalogSnippet(items: List<NoteCatalogItem>): String {
        if (items.isEmpty()) {
            return "(Kho ghi chú hiện tại đang trống)"
        }

        return items.joinToString("\n") { item ->
            val privacyTag = if (item.isPrivate) "[🔒 RIÊNG TƯ - CHỈ POINTER CỤC BỘ]" else "[Thường]"
            val dateStr = try {
                dateFormat.format(Date(item.updatedAt))
            } catch (e: Exception) {
                "${item.updatedAt}"
            }
            "- [ID: ${item.id}] \"${item.title}\" $privacyTag | Cập nhật: $dateStr"
        }
    }

    private suspend fun buildNotificationSummarySnippet(): String {
        return try {
            val notifications = notificationDao.getNotificationsWithin24Hours(limit = 100)
            if (notifications.isEmpty()) {
                return "(Không có thông báo/tin nhắn mới trong 24 giờ qua)"
            }

            // Group by appName/package and sender
            val grouped = notifications.groupBy { notification ->
                val app = notification.appName.ifBlank { notification.appPackage }
                val sender = notification.sender.ifBlank { "Không rõ người gửi" }
                "$app: $sender"
            }

            grouped.entries.joinToString("\n") { (key, list) ->
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
        } catch (e: Exception) {
            "(Không thể tải tổng quan tin nhắn 24h: ${e.message})"
        }
    }
}
