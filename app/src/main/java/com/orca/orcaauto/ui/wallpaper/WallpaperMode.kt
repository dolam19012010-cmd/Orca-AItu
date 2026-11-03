package com.orca.orcaauto.ui.wallpaper

import androidx.compose.runtime.Immutable

@Immutable
enum class WallpaperMode(
    val code: String,
    val title: String,
    val subtitle: String
) {
    CLEAR(
        code = "clear",
        title = "Trong Suốt",
        subtitle = "Hiển thị sắc nét, tương phản tối ưu"
    ),
    BLURRED(
        code = "blurred",
        title = "Mờ Ảo",
        subtitle = "Kính sương mờ sang trọng (Frosted Blur)"
    ),
    DYNAMIC(
        code = "dynamic",
        title = "Động (3D)",
        subtitle = "Con quay hồi chuyển 3D Parallax & Hạt phát sáng"
    );

    companion object {
        fun fromCode(code: String?): WallpaperMode {
            return entries.find { it.code.equals(code, ignoreCase = true) } ?: CLEAR
        }
    }
}
