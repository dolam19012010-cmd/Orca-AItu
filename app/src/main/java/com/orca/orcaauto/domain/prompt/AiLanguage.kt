package com.orca.orcaauto.domain.prompt

import com.orca.orcaauto.ui.locale.AppLanguage

enum class AiLanguage(
    val code: String,
    val displayNameVi: String,
    val displayNameEn: String
) {
    AUTO(
        code = "auto",
        displayNameVi = "Đồng bộ theo ngôn ngữ giao diện",
        displayNameEn = "Match UI Language"
    ),
    VIETNAMESE(
        code = "vi",
        displayNameVi = "Tiếng Việt (Vietnamese)",
        displayNameEn = "Vietnamese (Tiếng Việt)"
    ),
    ENGLISH(
        code = "en",
        displayNameVi = "Tiếng Anh (English)",
        displayNameEn = "English (Tiếng Anh)"
    );

    fun resolve(appLanguage: AppLanguage): AppLanguage = when (this) {
        AUTO -> appLanguage
        VIETNAMESE -> AppLanguage.VIETNAMESE
        ENGLISH -> AppLanguage.ENGLISH
    }

    fun getDisplayName(appLanguage: AppLanguage): String = when (appLanguage) {
        AppLanguage.VIETNAMESE -> displayNameVi
        AppLanguage.ENGLISH -> displayNameEn
    }

    companion object {
        fun fromCode(code: String?): AiLanguage {
            return entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: AUTO
        }
    }
}
