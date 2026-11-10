package com.orca.orcaauto.data.preferences

import androidx.compose.runtime.Immutable
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import java.lang.reflect.Type
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
    val aiLanguage: String? = "auto"
) {
    val safeAiLanguage: String
        get() = if (aiLanguage.isNullOrBlank()) "auto" else aiLanguage

    companion object {
        const val MAIN_SESSION_ID = "session_main_default"

        fun createMainSession(): SummarySessionConfig {
            return SummarySessionConfig(
                id = MAIN_SESSION_ID,
                name = "Phiên chính",
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

class SummarySessionConfigDeserializer : JsonDeserializer<SummarySessionConfig> {
    override fun deserialize(
        json: JsonElement?,
        typeOfT: Type?,
        context: JsonDeserializationContext?
    ): SummarySessionConfig {
        if (json == null || !json.isJsonObject) {
            return SummarySessionConfig.createMainSession()
        }
        val obj = json.asJsonObject

        val id = obj.get("id")?.takeIf { !it.isJsonNull }?.asString?.takeIf { it.isNotBlank() }
            ?: UUID.randomUUID().toString()
        val name = obj.get("name")?.takeIf { !it.isJsonNull }?.asString?.takeIf { it.isNotBlank() } ?: "Phiên chính"
        val iconEmoji = obj.get("iconEmoji")?.takeIf { !it.isJsonNull }?.asString?.takeIf { it.isNotBlank() && !it.contains("?") && it.length <= 4 } ?: "\u26A1"
        val isMain = obj.get("isMain")?.takeIf { !it.isJsonNull }?.asBoolean ?: false
        val createdAt = obj.get("createdAt")?.takeIf { !it.isJsonNull }?.asLong ?: System.currentTimeMillis()

        val filterModeStr = obj.get("filterMode")?.takeIf { !it.isJsonNull }?.asString
        val filterMode = try {
            if (filterModeStr != null) ChatFilterMode.valueOf(filterModeStr) else ChatFilterMode.NORMAL_WITH_BLOCK
        } catch (_: Throwable) {
            ChatFilterMode.NORMAL_WITH_BLOCK
        }

        val whitelist = try {
            obj.get("whitelist")?.takeIf { !it.isJsonNull }?.asJsonArray?.mapNotNull { it.asString }?.toSet() ?: emptySet()
        } catch (_: Throwable) {
            emptySet()
        }

        val banlist = try {
            obj.get("banlist")?.takeIf { !it.isJsonNull }?.asJsonArray?.mapNotNull { it.asString }?.toSet() ?: emptySet()
        } catch (_: Throwable) {
            emptySet()
        }

        val keyPriorityIds = try {
            obj.get("keyPriorityIds")?.takeIf { !it.isJsonNull }?.asJsonArray?.mapNotNull { it.asString } ?: emptyList()
        } catch (_: Throwable) {
            emptyList()
        }

        val isScheduled = obj.get("isScheduled")?.takeIf { !it.isJsonNull }?.asBoolean ?: true

        val scheduledHours = try {
            obj.get("scheduledHours")?.takeIf { !it.isJsonNull }?.asJsonArray?.mapNotNull { it.asString }?.takeIf { it.isNotEmpty() }
                ?: listOf("12:00", "18:00")
        } catch (_: Throwable) {
            listOf("12:00", "18:00")
        }

        val customInstruction = obj.get("customInstruction")?.takeIf { !it.isJsonNull }?.asString ?: ""
        val zaloEnabled = obj.get("zaloEnabled")?.takeIf { !it.isJsonNull }?.asBoolean ?: true
        val telegramEnabled = obj.get("telegramEnabled")?.takeIf { !it.isJsonNull }?.asBoolean ?: true
        val messengerEnabled = obj.get("messengerEnabled")?.takeIf { !it.isJsonNull }?.asBoolean ?: true
        val promptPersonaId = obj.get("promptPersonaId")?.takeIf { !it.isJsonNull }?.asString?.takeIf { it.isNotBlank() } ?: "gentle_assistant"
        val customFixedContext = obj.get("customFixedContext")?.takeIf { !it.isJsonNull }?.asString ?: ""
        val autoCompleteOverdue = obj.get("autoCompleteOverdue")?.takeIf { !it.isJsonNull }?.asBoolean ?: false
        val aiLanguage = obj.get("aiLanguage")?.takeIf { !it.isJsonNull }?.asString?.takeIf { it.isNotBlank() } ?: "auto"

        return SummarySessionConfig(
            id = id,
            name = name,
            iconEmoji = iconEmoji,
            isMain = isMain,
            createdAt = createdAt,
            filterMode = filterMode,
            whitelist = whitelist,
            banlist = banlist,
            keyPriorityIds = keyPriorityIds,
            isScheduled = isScheduled,
            scheduledHours = scheduledHours,
            customInstruction = customInstruction,
            zaloEnabled = zaloEnabled,
            telegramEnabled = telegramEnabled,
            messengerEnabled = messengerEnabled,
            promptPersonaId = promptPersonaId,
            customFixedContext = customFixedContext,
            autoCompleteOverdue = autoCompleteOverdue,
            aiLanguage = aiLanguage
        )
    }
}
