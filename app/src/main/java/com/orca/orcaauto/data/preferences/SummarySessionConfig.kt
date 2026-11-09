package com.orca.orcaauto.data.preferences

import androidx.compose.runtime.Immutable
import java.util.UUID

@Immutable
data class SummarySessionConfig(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val iconEmoji: String = "\u26A1",
    val isMain: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val filterMode: ChatFilterMode = ChatFilterMode.NORMAL_WITH_BLOCK,
    val whitelist: Set<String> = emptySet(),
    val banlist: Set<String> = emptySet(),
    val keyPriorityIds: List<String> = emptyList(),
    val isScheduled: Boolean = true,
    val scheduledHours: List<String> = listOf("12:00", "18:00"),
    val customInstruction: String = "",
    val zaloEnabled: Boolean = true,
    val telegramEnabled: Boolean = true,
    val messengerEnabled: Boolean = true,
    val promptPersonaId: String = "gentle_assistant",
    val customFixedContext: String = "",
    val autoCompleteOverdue: Boolean = false,
    val aiLanguage: String = "auto"
) {
    companion object {
        const val MAIN_SESSION_ID = "session_main_default"

        fun createMainSession(): SummarySessionConfig {
            return SummarySessionConfig(
                id = MAIN_SESSION_ID,
                name = "Phi\u00EAn ch\u00EDnh",
                iconEmoji = "\u26A1",
                isMain = true,
                createdAt = 0L, // Created first
                filterMode = ChatFilterMode.NORMAL_WITH_BLOCK,
                whitelist = emptySet(),
                banlist = emptySet(),
                keyPriorityIds = emptyList(), // Uses default key pool
                isScheduled = true,
                scheduledHours = listOf("12:00", "18:00"),
                customInstruction = "",
                zaloEnabled = true,
                telegramEnabled = true,
                messengerEnabled = true,
                promptPersonaId = "gentle_assistant",
                customFixedContext = "",
                autoCompleteOverdue = false,
                aiLanguage = "auto"
            )
        }
    }
}
