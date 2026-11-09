package com.orca.orcaauto.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Swipe
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.orca.orcaauto.R
import com.orca.orcaauto.ui.theme.AccentAmber
import com.orca.orcaauto.ui.theme.AccentCyan
import com.orca.orcaauto.ui.theme.AccentEmerald
import com.orca.orcaauto.ui.theme.AccentRose
import com.orca.orcaauto.ui.theme.BackgroundDark
import com.orca.orcaauto.ui.theme.CardBorder
import com.orca.orcaauto.ui.theme.GlassBorderSubtle
import com.orca.orcaauto.ui.theme.PrimaryIndigo
import com.orca.orcaauto.ui.theme.SurfaceDark
import com.orca.orcaauto.ui.theme.SurfaceVariantDark
import com.orca.orcaauto.ui.theme.TextMutedDark
import com.orca.orcaauto.ui.theme.TextPrimaryDark
import com.orca.orcaauto.ui.theme.TextSecondaryDark

/**
 * Detailed, categorized User Guide formatted like Android System Settings (Menu Item + Icon + Expandable Accordion).
 * Collapsed by default so users can tap specifically into the topic they want without being overwhelmed by a wall of text.
 */
@Composable
fun OnboardingGuideDialog(
    initialDontShowAgain: Boolean = false,
    onDismiss: (dontShowAgain: Boolean) -> Unit
) {
    var dontShowAgain by remember { mutableStateOf(initialDontShowAgain) }
    // Keeps track of the currently expanded section index (null = all collapsed)
    var expandedSectionIndex by remember { mutableStateOf<Int?>(0) } // Default expand API Guide

    AlertDialog(
        onDismissRequest = { onDismiss(dontShowAgain) },
        shape = RoundedCornerShape(20.dp),
        containerColor = SurfaceDark,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, CardBorder, RoundedCornerShape(20.dp)),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(
                            androidx.compose.ui.graphics.Brush.linearGradient(
                                listOf(Color(0xFF27272A), Color(0xFF09090B))
                            ),
                            CircleShape
                        )
                        .border(1.dp, Color(0x33FFFFFF), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_orca_logo),
                        contentDescription = "AItu Logo",
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Hướng Dẫn Chi Tiết AItu",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimaryDark
                    )
                    Text(
                        text = "Chọn mục bên dưới để xem hướng dẫn từng bước",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondaryDark
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 500.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Scrollable List of Settings-like Menu Sections
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Item 1: Cách lấy API Key miễn phí (Google, Groq, OpenRouter)
                    ExpandableGuideSection(
                        index = 0,
                        isExpanded = expandedSectionIndex == 0,
                        onToggle = { expandedSectionIndex = if (expandedSectionIndex == 0) null else 0 },
                        icon = Icons.Default.Key,
                        iconBgColor = Color(0xFF0284C7),
                        title = "Cách lấy API Key miễn phí",
                        subtitle = "Hướng dẫn từng bước lấy Key Google Gemini, Groq & OpenRouter"
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            GuideStepCard(
                                title = "1. Google Gemini API (Khuyên dùng - 100% Miễn phí)",
                                steps = listOf(
                                    "Truy cập trang web: aistudio.google.com (trên điện thoại hoặc PC).",
                                    "Đăng nhập bằng tài khoản Google (Gmail) của bạn.",
                                    "Bấm nút 'Get API key' ở góc trên bên trái.",
                                    "Bấm 'Create API key' ➔ Chọn 'Create key in new project'.",
                                    "Bấm 'Copy' đoạn mã bắt đầu bằng: AIzaSy...",
                                    "Mở AItu ➔ Cài đặt ➔ Dán Key vào ô ➔ Bấm 'Thêm Key'!",
                                    "⚡ Mẹo: Google cấp miễn phí 15 lượt gọi/phút, hoàn toàn không tốn tiền. Model khuyên dùng: gemini-2.5-flash."
                                )
                            )

                            GuideStepCard(
                                title = "2. Groq Cloud API (Phản hồi siêu tốc ~500 tokens/s)",
                                steps = listOf(
                                    "Truy cập: console.groq.com/keys và đăng nhập.",
                                    "Bấm 'Create API Key' ➔ Đặt tên bất kỳ ➔ Bấm Submit.",
                                    "Copy đoạn mã bắt đầu bằng: gsk_...",
                                    "Dán vào AItu để nhận phân tích tức thì sau 1 giây!"
                                )
                            )

                            GuideStepCard(
                                title = "3. OpenRouter API (Dùng DeepSeek-V3, Claude, GPT)",
                                steps = listOf(
                                    "Truy cập: openrouter.ai/keys và đăng nhập.",
                                    "Bấm tạo Key mới ➔ Copy key dạng: sk-or-...",
                                    "Dán vào AItu để trải nghiệm các mô hình mã nguồn mở hàng đầu."
                                )
                            )

                            GuideStepCard(
                                title = "💡 Mẹo đổi mô hình linh hoạt",
                                steps = listOf(
                                    "Tại danh sách Key trong Cài đặt, bạn chỉ cần nhấn giữ vào Key bất kỳ để đổi sang model khác (ví dụ: gemini-2.5-flash, deepseek-chat) mà không cần xóa đi nhập lại!"
                                )
                            )
                        }
                    }

                    // Item 2: Bảo mật & Cấp quyền hệ thống (Tương tự mục Bảo mật trong ảnh)
                    ExpandableGuideSection(
                        index = 1,
                        isExpanded = expandedSectionIndex == 1,
                        onToggle = { expandedSectionIndex = if (expandedSectionIndex == 1) null else 1 },
                        icon = Icons.Default.Security,
                        iconBgColor = Color(0xFFEC4899), // Pink icon like in user's screenshot
                        title = "Bảo mật & Cấp quyền hệ thống",
                        subtitle = "Quyền đọc thông báo, tối ưu pin, cửa sổ nổi & tự khởi chạy"
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            GuideStepCard(
                                title = "🔒 Cam kết quyền riêng tư & Bảo mật PII",
                                steps = listOf(
                                    "AItu chạy hoàn toàn cục bộ trên máy bạn (Local-First).",
                                    "Bộ lọc PII Sanitizer tự động che giấu số điện thoại, email, địa chỉ trước khi gửi đến AI.",
                                    "Tin nhắn thô không lưu trên bất kỳ máy chủ bên ngoài nào."
                                )
                            )

                            GuideStepCard(
                                title = "⚡ Các quyền cần cấp để app chạy ngầm ổn định",
                                steps = listOf(
                                    "1. Quyền đọc thông báo: Cho phép app lắng nghe tin nhắn từ Zalo, Telegram, Messenger.",
                                    "2. Bỏ qua tối ưu hóa pin: Rất quan trọng! Giúp Android không tắt tiến trình ngầm, báo thức và tóm tắt đúng giờ.",
                                    "3. Cửa sổ nổi: Cho phép bong bóng chat hiển thị trên các ứng dụng khác.",
                                    "4. Tự khởi chạy (Autostart - Xiaomi HyperOS, Samsung, Oppo, Vivo): Mở Cài đặt hệ thống ➔ Ứng dụng ➔ AItu ➔ Bật Tự khởi chạy."
                                )
                            )
                        }
                    }

                    // Item 3: Cử chỉ & Bảng điều phối nhiệm vụ
                    ExpandableGuideSection(
                        index = 2,
                        isExpanded = expandedSectionIndex == 2,
                        onToggle = { expandedSectionIndex = if (expandedSectionIndex == 2) null else 2 },
                        icon = Icons.Default.Swipe,
                        iconBgColor = AccentEmerald,
                        title = "Cử chỉ điều phối nhiệm vụ",
                        subtitle = "Vuốt phải hoàn thành, vuốt trái hủy, bấm nhanh đặt báo thức"
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            GuideStepCard(
                                title = "👉 Thao tác cử chỉ thông minh trên từng thẻ",
                                steps = listOf(
                                    "👉 Vuốt sang PHẢI ➔ Hoàn thành (✓): Thẻ chuyển màu xanh, việc chuyển vào lịch sử đã xong.",
                                    "👈 Vuốt sang TRÁI ➔ Hủy việc (✕): AI tự động ghi nhớ và cấm tiệt việc đề xuất lại việc này trong các ca sau.",
                                    "👆 Chạm nhanh (Single Tap) ➔ Bật/tắt báo thức tức thì theo giờ AI đề xuất (ví dụ: 'Đặt 14:00').",
                                    "⏱️ Nhấn giữ (Long Press) ➔ Xem chi tiết hạn chót, người gửi và hướng dẫn cử chỉ."
                                )
                            )
                        }
                    }

                    // Item 4: Báo thức thông minh & Lịch
                    ExpandableGuideSection(
                        index = 3,
                        isExpanded = expandedSectionIndex == 3,
                        onToggle = { expandedSectionIndex = if (expandedSectionIndex == 3) null else 3 },
                        icon = Icons.Default.Alarm,
                        iconBgColor = AccentAmber,
                        title = "Báo thức thông minh & Lịch",
                        subtitle = "Chuông dồn x1/x2/x3, tự hủy báo thức quá hạn & Google Lịch"
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            GuideStepCard(
                                title = "⏰ Cơ chế báo thức không gây phiền toái",
                                steps = listOf(
                                    "Hẹn giờ trực tiếp qua hệ thống báo thức chuẩn Android, không phụ thuộc vào ứng dụng thứ ba.",
                                    "Chế độ chuông dồn x2, x3 reo liên tiếp cách nhau 5 - 15 phút chống ngủ quên.",
                                    "Báo thức đã qua hoặc bị bạn hủy sẽ tự động được dọn dẹp sạch sẽ, không làm rác ứng dụng Đồng hồ của máy.",
                                    "Các sự kiện dài hạn (>24h) hỗ trợ thêm nhanh vào Google Calendar chỉ với 1 chạm."
                                )
                            )
                        }
                    }

                    // Item 5: Bong bóng chat & Lệnh trực tiếp
                    ExpandableGuideSection(
                        index = 4,
                        isExpanded = expandedSectionIndex == 4,
                        onToggle = { expandedSectionIndex = if (expandedSectionIndex == 4) null else 4 },
                        icon = Icons.Default.ChatBubbleOutline,
                        iconBgColor = AccentCyan,
                        title = "Bong bóng chat & Lệnh trực tiếp",
                        subtitle = "Thanh nhập lệnh sát cạnh dưới, hỏi đáp AI tức thì"
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            GuideStepCard(
                                title = "💬 Tương tác tiện lợi mọi lúc mọi nơi",
                                steps = listOf(
                                    "Bong bóng chat thu gọn vào góc màn hình khi không dùng.",
                                    "Thanh nhập lệnh đặt sát cạnh dưới cùng màn hình giúp ngón cái gõ phím cực kỳ thuận tiện.",
                                    "Hỏi đáp hoặc ra lệnh trực tiếp: 'Tóm tắt tin nhắn gấp nhất', 'Hôm nay tôi còn việc gì?'",
                                    "Nút 'Mở app ↗' ở góc trên giúp truy cập ngay màn hình ứng dụng đầy đủ."
                                )
                            )
                            GuideStepCard(
                                title = "✨ Dành riêng cho Samsung One UI (Gộp cụm Zalo & MacroDroid)",
                                steps = listOf(
                                    "Vào Cài đặt máy > Thông báo > Cài đặt nâng cao > Thông báo nổi.",
                                    "Tích chọn 'Xem dạng pop-up thông minh' (Smart pop-up view).",
                                    "Nhấn 'Ứng dụng được bao gồm' và bật AItu.",
                                    "Khi có báo cáo AI, AItu sẽ tự động gom chung hàng bóng tròn với Zalo, MacroDroid (có nút +) cực kỳ tiện lợi mà không cần cài thêm gì!"
                                )
                            )
                        }
                    }

                    // Item 6: Hình nền tùy chỉnh & Hiệu ứng 3D Parallax
                    ExpandableGuideSection(
                        index = 5,
                        isExpanded = expandedSectionIndex == 5,
                        onToggle = { expandedSectionIndex = if (expandedSectionIndex == 5) null else 5 },
                        icon = Icons.Default.Wallpaper,
                        iconBgColor = Color(0xFFF59E0B),
                        title = "Hình nền cá nhân & 3D Parallax",
                        subtitle = "Trong suốt, mờ Frosted Glass và hiệu ứng con quay 3D"
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            GuideStepCard(
                                title = "🎨 3 Chế độ hiển thị hình nền",
                                steps = listOf(
                                    "1. Trong suốt (Clear): Hiển thị hình nền sắc nét kèm lớp phủ tối dịu mắt.",
                                    "2. Mờ (Blurred): Hiệu ứng kính mờ Frosted Glass sang trọng và tăng độ tương phản chữ.",
                                    "3. Động (Dynamic 3D): Tận dụng cảm biến con quay hồi chuyển, hình nền nghiêng 3D sống động theo tay cầm và tự động quy hồi tâm không lo kịch góc."
                                )
                            )
                        }
                    }

                    // Item 7: Múi giờ, Sao lưu & Khôi phục
                    ExpandableGuideSection(
                        index = 6,
                        isExpanded = expandedSectionIndex == 6,
                        onToggle = { expandedSectionIndex = if (expandedSectionIndex == 6) null else 6 },
                        icon = Icons.Default.Settings,
                        iconBgColor = Color(0xFF38BDF8),
                        title = "Múi giờ & Sao lưu dữ liệu",
                        subtitle = "Cấu hình múi giờ chuẩn xác, xuất file JSON đồng bộ máy khác"
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            GuideStepCard(
                                title = "⚙️ Thiết lập chuyên sâu",
                                steps = listOf(
                                    "Múi giờ: Thiết lập múi giờ (mặc định Asia/Ho_Chi_Minh GMT+7) giúp AI tính đúng hạn chót.",
                                    "Sao lưu: Bấm 'Sao lưu ngay' để xuất toàn bộ danh sách API Key, khung giờ và bối cảnh thành file JSON.",
                                    "Khôi phục: Dán mã JSON đã sao lưu vào điện thoại mới để phục hồi toàn bộ cấu hình chỉ trong 1 giây."
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                // Checkbox: Không hiển thị lại
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { dontShowAgain = !dontShowAgain },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = dontShowAgain,
                        onCheckedChange = { dontShowAgain = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = PrimaryIndigo,
                            checkmarkColor = BackgroundDark,
                            uncheckedColor = TextMutedDark
                        )
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Không tự động hiện lại khi mở ứng dụng",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextPrimaryDark,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onDismiss(dontShowAgain) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = PrimaryIndigo,
                    contentColor = BackgroundDark
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Đã Hiểu & Đóng", fontWeight = FontWeight.Bold, color = BackgroundDark)
            }
        }
    )
}

/**
 * Expandable Settings-like list row: Icon in colored circle, title, subtitle, and rotating chevron.
 * Expands inline only when tapped.
 */
@Composable
private fun ExpandableGuideSection(
    index: Int,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    icon: ImageVector,
    iconBgColor: Color,
    title: String,
    subtitle: String,
    content: @Composable () -> Unit
) {
    val rotationAngle by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        label = "ChevronRotation"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceVariantDark.copy(alpha = 0.40f), RoundedCornerShape(12.dp))
            .border(
                1.dp,
                if (isExpanded) {
                    if (iconBgColor == Color(0xFF27272A) || iconBgColor == SurfaceVariantDark) AccentCyan.copy(alpha = 0.5f) else iconBgColor.copy(alpha = 0.45f)
                } else GlassBorderSubtle,
                RoundedCornerShape(12.dp)
            )
    ) {
        // Menu item row (clickable header)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggle() }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Circular icon with colored background (exact match with Android Settings screenshot style)
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .background(iconBgColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (iconBgColor == PrimaryIndigo || iconBgColor == Color.White) BackgroundDark else Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isExpanded) {
                        if (iconBgColor == Color(0xFF27272A) || iconBgColor == SurfaceVariantDark || iconBgColor == PrimaryIndigo || iconBgColor == Color.White) AccentCyan else iconBgColor
                    } else TextPrimaryDark
                )
                Spacer(modifier = Modifier.height(1.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondaryDark,
                    fontSize = 11.sp,
                    maxLines = if (isExpanded) 2 else 1
                )
            }

            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = if (isExpanded) "Thu gọn" else "Mở rộng",
                tint = if (isExpanded) {
                    if (iconBgColor == Color(0xFF27272A) || iconBgColor == SurfaceVariantDark || iconBgColor == PrimaryIndigo || iconBgColor == Color.White) AccentCyan else iconBgColor
                } else TextMutedDark,
                modifier = Modifier
                    .size(20.dp)
                    .rotate(rotationAngle)
            )
        }

        // Expanded content section
        AnimatedVisibility(
            visible = isExpanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, bottom = 12.dp, top = 2.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                content()
            }
        }
    }
}

@Composable
private fun GuideStepCard(
    title: String,
    steps: List<String>
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceDark.copy(alpha = 0.65f), RoundedCornerShape(10.dp))
            .border(1.dp, GlassBorderSubtle, RoundedCornerShape(10.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = AccentCyan
        )
        steps.forEach { step ->
            Text(
                text = "• $step",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondaryDark,
                lineHeight = 16.sp
            )
        }
    }
}
