package com.orca.orcaauto.ui.locale

enum class AppLanguage(
    val code: String,
    val displayName: String,
    val nativeName: String
) {
    VIETNAMESE("vi", "Tiếng Việt", "Tiếng Việt"),
    ENGLISH("en", "English", "English");

    companion object {
        fun fromCode(code: String?): AppLanguage {
            return entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: VIETNAMESE
        }
    }
}
