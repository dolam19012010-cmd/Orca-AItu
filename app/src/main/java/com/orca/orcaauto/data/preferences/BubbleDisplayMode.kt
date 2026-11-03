package com.orca.orcaauto.data.preferences

enum class BubbleDisplayMode(val code: String, val title: String, val description: String) {
    OPTION_A(
        code = "OPTION_A",
        title = "Phương án A: Ẩn thông minh & Chạm mép gọi",
        description = "Bóng tự mờ sau 1.5s, tự ẩn sau 5s có vạch chỉ báo mép. Chạm bất kỳ đâu ở mép màn hình để gọi trợ lý ngay lập tức. Không bao giờ che màn hình khi chơi game, xem phim."
    ),
    OPTION_B(
        code = "OPTION_B",
        title = "Phương án B: Ghim cố định ở mép viền (Messenger)",
        description = "Bóng luôn hiển thị nép ở mép viền, mờ nhẹ 28% khi nghỉ nhưng KHÔNG BAO GIỜ TỰ ẨN BIẾN MẤT. Bạn có thể nhìn thấy và chạm trực tiếp vào bóng bất kỳ lúc nào."
    );

    companion object {
        fun fromCode(code: String?): BubbleDisplayMode {
            return entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: OPTION_A
        }
    }
}
