package com.orca.orcaauto.data.preferences

import androidx.compose.runtime.Immutable

/**
 * 3-Level Thinking Configuration for Round 2 Streaming AI.
 *
 * FAST: thinkingBudget = 0, includeThoughts = false, openRouterEffort = "low"
 * BALANCED: thinkingBudget = 1024, includeThoughts = true, openRouterEffort = "medium"
 * DEEP: thinkingBudget = 4096, includeThoughts = true, openRouterEffort = "high"
 */
@Immutable
enum class AssistantThinkingLevel(
    val id: String,
    val title: String,
    val description: String,
    val geminiBudget: Int,
    val openRouterEffort: String
) {
    FAST(
        id = "fast",
        title = "Nhanh",
        description = "Phản hồi siêu tốc, tắt suy nghĩ nội tâm",
        geminiBudget = 0,
        openRouterEffort = "low"
    ),
    BALANCED(
        id = "balanced",
        title = "Cân bằng",
        description = "Cân bằng giữa tốc độ và chiều sâu suy nghĩ (1024 tokens)",
        geminiBudget = 1024,
        openRouterEffort = "medium"
    ),
    DEEP(
        id = "deep",
        title = "Sâu sắc",
        description = "Suy luận toàn diện cho tác vụ phức tạp (4096 tokens)",
        geminiBudget = 4096,
        openRouterEffort = "high"
    );

    companion object {
        fun fromId(id: String?): AssistantThinkingLevel {
            return entries.firstOrNull {
                it.id.equals(id, ignoreCase = true) || it.name.equals(id, ignoreCase = true)
            } ?: BALANCED
        }
    }
}
