package com.orca.orcaauto.data.preferences

import androidx.compose.runtime.Immutable

@Immutable
enum class ChatFilterMode(val code: String, val title: String, val description: String) {
    NORMAL_WITH_BLOCK(
        code = "normal_block",
        title = "Nghe bình thường (có kèm chặn)",
        description = "Đọc mọi tin nhắn, trừ các đoạn chat nằm trong danh sách chặn."
    ),
    STRICT_WHITELIST(
        code = "whitelist",
        title = "Chỉ định riêng (Whitelist)",
        description = "Chỉ đọc DUY NHẤT các đoạn chat được chỉ định, bỏ qua tất cả còn lại."
    );

    companion object {
        fun fromCode(code: String?): ChatFilterMode {
            return entries.find { it.code.equals(code, ignoreCase = true) } ?: NORMAL_WITH_BLOCK
        }
    }
}

@Immutable
data class AppFilterRule(
    val appPackage: String,
    val mode: ChatFilterMode = ChatFilterMode.NORMAL_WITH_BLOCK,
    val chatNames: Set<String> = emptySet()
)
