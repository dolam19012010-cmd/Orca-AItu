package com.orca.orcaauto.domain.assistant

import androidx.compose.runtime.Immutable
import com.orca.orcaauto.data.local.NotificationEntity
import com.orca.orcaauto.domain.filter.PiiSanitizer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Atomic message item after coalescing and optional ephemeral ticket ID assignment.
 */
@Immutable
data class CoalescedMessageItem(
    val id: Long,               // Real Room DB ID
    val ticketId: Int? = null,  // Ephemeral ticket ID [1], [2], [3]... (used in Round 1 for spam filtering)
    val content: String,        // Message content (PII sanitized / cleaned)
    val timestamp: Long         // Timestamp in milliseconds
)

/**
 * A continuous speaking turn (utterance block) from a single speaker in a conversation.
 * Consecutive messages within an idle silence gap <= 3 minutes (180,000 ms) are coalesced.
 */
@Immutable
data class SpeakerTurn(
    val speaker: String,
    val startTimestamp: Long,   // Timestamp of the first message in this turn
    val messages: List<CoalescedMessageItem>
) {
    val lastTimestamp: Long
        get() = messages.lastOrNull()?.timestamp ?: startTimestamp
}

/**
 * Coalesced conversation belonging to an application.
 */
@Immutable
data class CoalescedConversation(
    val appName: String,
    val appPackage: String,
    val conversationTitle: String,
    val turns: List<SpeakerTurn>
)

/**
 * Aggregated coalescing result containing conversations, ephemeral ticket ID mapping, and total message count.
 */
@Immutable
data class CoalesceResult(
    val conversations: List<CoalescedConversation>,
    val ticketMap: Map<Int, Long>, // ticketId (1, 2, 3...) -> real NotificationEntity.id
    val totalMessageCount: Int
)

/**
 * MessageCoalescer: State machine engine that coalesces fragmented notification streams
 * into coherent conversations and speaker turns using a 3-minute window (180,000 ms).
 */
object MessageCoalescer {

    const val COALESCE_WINDOW_MS = 180_000L // 3 minutes window (180,000 ms)
    const val MAX_TURN_DURATION_MS = 15 * 60 * 1000L // 15 minutes window (900,000 ms)

    private val SENDER_PAREN_GROUP_REGEX = Regex("""^(.+?)\s*\((.+?)\)$""")
    private val GROUP_ARROW_SENDER_REGEX = Regex("""^(.+?)\s*>\s*(.+)$""")
    private val GROUP_CONTENT_PREFIX_REGEX = Regex("""^((?=.*\p{L})[\p{L}0-9_ ]{2,25}):\s+(.*)""", RegexOption.DOT_MATCHES_ALL)

    /**
     * Resolves conversation title from notification entity.
     * Supports formats like "Speaker (Group Name)" or "Group Name > Speaker".
     */
    fun resolveConversationTitle(notif: NotificationEntity): String {
        val sender = notif.sender.trim()

        // 1. "Speaker (Group Name)" -> Conversation Title is "Group Name"
        val parenMatch = SENDER_PAREN_GROUP_REGEX.find(sender)
        if (parenMatch != null) {
            val group = parenMatch.groupValues[2].trim()
            if (group.isNotBlank()) return group
        }

        // 2. "Group Name > Speaker" -> Conversation Title is "Group Name"
        val arrowMatch = GROUP_ARROW_SENDER_REGEX.find(sender)
        if (arrowMatch != null) {
            val group = arrowMatch.groupValues[1].trim()
            if (group.isNotBlank()) return group
        }

        // 3. Fallback to sender or default
        return sender.ifBlank { "Hội thoại không tên" }
    }

    /**
     * Resolves the actual speaker from notification entity and conversation context.
     */
    fun resolveSpeaker(notif: NotificationEntity, conversationTitle: String): String {
        val sender = notif.sender.trim()

        // 1. "Speaker (Group Name)" -> Speaker is "Speaker"
        val parenMatch = SENDER_PAREN_GROUP_REGEX.find(sender)
        if (parenMatch != null) {
            val speaker = parenMatch.groupValues[1].trim()
            if (speaker.isNotBlank()) return speaker
        }

        // 2. "Group Name > Speaker" -> Speaker is "Speaker"
        val arrowMatch = GROUP_ARROW_SENDER_REGEX.find(sender)
        if (arrowMatch != null) {
            val speaker = arrowMatch.groupValues[2].trim()
            if (speaker.isNotBlank()) return speaker
        }

        // 3. If group hint in conversation title, check content for "Speaker: Message"
        if (isGroupHint(conversationTitle)) {
            val prefixMatch = GROUP_CONTENT_PREFIX_REGEX.find(notif.content.trim())
            if (prefixMatch != null) {
                val candidate = prefixMatch.groupValues[1].trim()
                if (candidate.isNotBlank()) return candidate
            }
        }

        return sender.ifBlank { "Người gửi ẩn danh" }
    }

    /**
     * Strips leading speaker prefix from message content if it matches the resolved speaker.
     */
    fun cleanContent(notif: NotificationEntity, resolvedSpeaker: String): String {
        val content = notif.content.trim()
        val prefixMatch = GROUP_CONTENT_PREFIX_REGEX.find(content)
        if (prefixMatch != null) {
            val extractedSpeaker = prefixMatch.groupValues[1].trim()
            if (extractedSpeaker.equals(resolvedSpeaker, ignoreCase = true)) {
                return prefixMatch.groupValues[2].trim()
            }
        }
        return content
    }

    private fun isGroupHint(conversationTitle: String): Boolean {
        val lower = conversationTitle.lowercase(Locale.ROOT)
        return lower.contains("nhóm") ||
               lower.contains("group") ||
               lower.contains("team") ||
               lower.contains("dự án")
    }

    /**
     * Coalesces notification list into structured conversations and speaker turns.
     *
     * @param notifications Input notifications
     * @param assignTicketIds When true, assigns ephemeral sequential ticket IDs [1], [2], [3]... in RAM
     * @param sanitizePii When true, applies PII sanitization (masking card numbers, bank accounts, phone numbers)
     * @return CoalesceResult containing structured conversations, ticket ID to Room DB ID map, and total message count
     */
    fun coalesce(
        notifications: List<NotificationEntity>,
        assignTicketIds: Boolean = false,
        sanitizePii: Boolean = true
    ): CoalesceResult {
        if (notifications.isEmpty()) {
            return CoalesceResult(
                conversations = emptyList(),
                ticketMap = emptyMap(),
                totalMessageCount = 0
            )
        }

        // Step 1: Sort chronologically ascending by timestamp, then by id
        val sorted = notifications.sortedWith(
            compareBy<NotificationEntity> { it.timestamp }.thenBy { it.id }
        )

        // Step 2: Group by App (preserving chronological appearance order)
        val groupedByApp = sorted.groupBy { notif ->
            notif.appName.ifBlank { notif.appPackage.ifBlank { "Ứng dụng khác" } }
        }

        var currentTicketIndex = 1
        val ticketMap = mutableMapOf<Int, Long>()
        val resultConversations = mutableListOf<CoalescedConversation>()

        for ((appName, appList) in groupedByApp) {
            // Step 3: Group by Conversation within App
            val groupedByConv = appList.groupBy { resolveConversationTitle(it) }

            for ((convTitle, convList) in groupedByConv) {
                val turns = mutableListOf<SpeakerTurn>()
                var activeTurnSpeaker: String? = null
                var activeTurnStartTime: Long = 0L
                var activeTurnLastTime: Long = 0L
                val activeTurnMessages = mutableListOf<CoalescedMessageItem>()

                for (notif in convList) {
                    val speaker = resolveSpeaker(notif, convTitle)
                    val rawCleanText = cleanContent(notif, speaker)
                    val finalContent = if (sanitizePii) PiiSanitizer.sanitize(rawCleanText) else rawCleanText

                    val ticketId = if (assignTicketIds) {
                        val tid = currentTicketIndex++
                        ticketMap[tid] = notif.id
                        tid
                    } else {
                        null
                    }

                    val msgItem = CoalescedMessageItem(
                        id = notif.id,
                        ticketId = ticketId,
                        content = finalContent,
                        timestamp = notif.timestamp
                    )

                    if (activeTurnSpeaker == null) {
                        // Start first turn
                        activeTurnSpeaker = speaker
                        activeTurnStartTime = notif.timestamp
                        activeTurnLastTime = notif.timestamp
                        activeTurnMessages.add(msgItem)
                    } else {
                        val sameSpeaker = activeTurnSpeaker.equals(speaker, ignoreCase = true)
                        val silenceGap = notif.timestamp - activeTurnLastTime
                        val turnDuration = notif.timestamp - activeTurnStartTime

                        // Coalescing condition:
                        // 1. Same speaker
                        // 2. Silence gap <= COALESCE_WINDOW_MS (180,000 ms = 3 minutes)
                        // 3. Turn duration <= MAX_TURN_DURATION_MS (900,000 ms = 15 minutes)
                        if (sameSpeaker && silenceGap in 0..COALESCE_WINDOW_MS && turnDuration <= MAX_TURN_DURATION_MS) {
                            activeTurnMessages.add(msgItem)
                            activeTurnLastTime = notif.timestamp
                        } else {
                            // Turn break: close previous turn, start new turn
                            turns.add(
                                SpeakerTurn(
                                    speaker = activeTurnSpeaker,
                                    startTimestamp = activeTurnStartTime,
                                    messages = activeTurnMessages.toList()
                                )
                            )
                            activeTurnMessages.clear()

                            activeTurnSpeaker = speaker
                            activeTurnStartTime = notif.timestamp
                            activeTurnLastTime = notif.timestamp
                            activeTurnMessages.add(msgItem)
                        }
                    }
                }

                // Flush final active turn
                if (activeTurnSpeaker != null && activeTurnMessages.isNotEmpty()) {
                    turns.add(
                        SpeakerTurn(
                            speaker = activeTurnSpeaker,
                            startTimestamp = activeTurnStartTime,
                            messages = activeTurnMessages.toList()
                        )
                    )
                }

                val firstPkg = convList.firstOrNull()?.appPackage.orEmpty()
                resultConversations.add(
                    CoalescedConversation(
                        appName = appName,
                        appPackage = firstPkg,
                        conversationTitle = convTitle,
                        turns = turns
                    )
                )
            }
        }

        return CoalesceResult(
            conversations = resultConversations,
            ticketMap = ticketMap,
            totalMessageCount = sorted.size
        )
    }

    /**
     * Formats coalesced messages into a prompt-friendly hierarchical string.
     * Displays timestamp once at the start of each speaker turn, hiding it on subsequent messages.
     *
     * @param result Coalesced result
     * @param withTicketIds When true, includes ticket IDs [1], [2] in message bullets
     */
    fun formatForPrompt(
        result: CoalesceResult,
        withTicketIds: Boolean = false
    ): String {
        if (result.conversations.isEmpty()) {
            return "(Không có tin nhắn liên quan nào)"
        }

        val sb = StringBuilder()
        val groupedByApp = result.conversations.groupBy { it.appName }

        for ((appName, convList) in groupedByApp) {
            sb.appendLine("【 Ứng dụng: $appName 】")

            for (conv in convList) {
                sb.appendLine("💬 Hội thoại: ${conv.conversationTitle}")

                for (turn in conv.turns) {
                    val timeStr = formatTimestamp(turn.startTimestamp)
                    val timePrefix = if (timeStr.isNotBlank()) "[$timeStr] " else ""
                    sb.appendLine("• $timePrefix${turn.speaker}:")

                    for (msg in turn.messages) {
                        val ticketTag = if (withTicketIds && msg.ticketId != null) "[${msg.ticketId}] " else ""
                        val lines = msg.content.lines()
                        if (lines.size <= 1) {
                            sb.appendLine("  - $ticketTag${msg.content}")
                        } else {
                            sb.appendLine("  - $ticketTag${lines.first()}")
                            for (extraLine in lines.drop(1)) {
                                sb.appendLine("    $extraLine")
                            }
                        }
                    }
                }
                sb.appendLine()
            }
        }

        return sb.toString().trimEnd()
    }

    private fun formatTimestamp(timestamp: Long): String {
        return try {
            val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
            sdf.format(Date(timestamp))
        } catch (e: Exception) {
            ""
        }
    }
}
