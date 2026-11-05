package com.orca.orcaauto.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.ui.draw.rotate
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.runtime.mutableStateListOf
import com.orca.orcaauto.data.preferences.AppFilterRule
import com.orca.orcaauto.data.preferences.ChatFilterMode
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.orca.orcaauto.R
import com.orca.orcaauto.data.preferences.UserPreferences
import com.orca.orcaauto.data.remote.ApiKeyConfig
import com.orca.orcaauto.data.remote.ApiDebugLogManager
import com.orca.orcaauto.data.remote.KeyTestReport
import com.orca.orcaauto.data.remote.UniversalAiClient
import com.orca.orcaauto.domain.prompt.PromptPersona
import com.orca.orcaauto.service.FloatingBubbleService
import com.orca.orcaauto.service.OemSurvivalHelper
import com.orca.orcaauto.ui.components.MockRewardAdDialog
import com.orca.orcaauto.ui.components.MultiSessionManagementSection
import com.orca.orcaauto.ui.components.OnboardingGuideDialog
import com.orca.orcaauto.ui.theme.AccentCyan
import com.orca.orcaauto.ui.theme.AccentEmerald
import com.orca.orcaauto.ui.theme.AccentRose
import com.orca.orcaauto.ui.theme.AccentAmber
import com.orca.orcaauto.ui.theme.BackgroundDark
import com.orca.orcaauto.ui.theme.CardBorder
import com.orca.orcaauto.ui.theme.GlassBorder
import com.orca.orcaauto.ui.theme.GlassBorderSubtle
import com.orca.orcaauto.ui.theme.GlassSurfaceCard
import com.orca.orcaauto.ui.theme.GlassSurfaceDark
import com.orca.orcaauto.ui.theme.GlassSurfaceInner
import com.orca.orcaauto.ui.theme.PrimaryIndigo
import com.orca.orcaauto.ui.theme.PrimaryIndigoLight
import com.orca.orcaauto.ui.theme.SurfaceDark
import com.orca.orcaauto.ui.theme.SurfaceVariantDark
import com.orca.orcaauto.ui.theme.TextMutedDark
import com.orca.orcaauto.ui.theme.TextPrimaryDark
import com.orca.orcaauto.ui.theme.TextSecondaryDark
import com.orca.orcaauto.ui.wallpaper.WallpaperMode
import com.orca.orcaauto.worker.WorkScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
private fun SettingsCategoryCard(
    isExpanded: Boolean,
    onToggle: () -> Unit,
    icon: ImageVector,
    iconBgColor: Color,
    iconTint: Color = Color.White,
    title: String,
    subtitle: String,
    badgeText: String? = null,
    badgeColor: Color = AccentCyan,
    content: @Composable () -> Unit
) {
    val rotationState by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "arrowRotation"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                if (isExpanded) iconBgColor.copy(alpha = 0.5f) else GlassBorder,
                RoundedCornerShape(16.dp)
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = GlassSurfaceDark)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(iconBgColor, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = iconTint,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimaryDark,
                                fontSize = 14.5.sp
                            )
                            if (badgeText != null) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .background(badgeColor.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                                        .border(0.5.dp, badgeColor.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 6.dp, vertical = 1.5.dp)
                                ) {
                                    Text(
                                        text = badgeText,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = badgeColor,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondaryDark,
                            fontSize = 11.5.sp
                        )
                    }
                }

                IconButton(onClick = onToggle, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.ExpandMore,
                        contentDescription = if (isExpanded) "Thu gọn" else "Mở rộng",
                        tint = if (isExpanded) iconBgColor else TextMutedDark,
                        modifier = Modifier.rotate(rotationState)
                    )
                }
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn(tween(250)) + expandVertically(tween(250)),
                exit = fadeOut(tween(200)) + shrinkVertically(tween(200))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    HorizontalDivider(color = GlassBorderSubtle, thickness = 0.8.dp)
                    content()
                }
            }
        }
    }
}

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val preferences = remember { UserPreferences.getInstance(context) }

    val keyPool by preferences.keyPoolFlow.collectAsState()
    val zaloEnabled by preferences.zaloEnabledFlow.collectAsState()
    val telegramEnabled by preferences.telegramEnabledFlow.collectAsState()
    val messengerEnabled by preferences.messengerEnabledFlow.collectAsState()
    val customFixedContext by preferences.customFixedContextFlow.collectAsState()
    val scheduledHours by preferences.scheduledHoursFlow.collectAsState()
    val currentPersona by preferences.promptPersonaFlow.collectAsState()

    val userTimezone by preferences.userTimezoneFlow.collectAsState()
    val isRecentSummaryContextEnabled by preferences.recentSummaryContextEnabledFlow.collectAsState()
    val isLongTermTasksContextEnabled by preferences.longTermTasksContextEnabledFlow.collectAsState()
    val wallpaperPath by preferences.wallpaperPathFlow.collectAsState()
    val wallpaperMode by preferences.wallpaperModeFlow.collectAsState()
    val filterRules by preferences.filterRulesFlow.collectAsState()
    val sessions by preferences.sessionsFlow.collectAsState()
    val allRecentSenders = remember(filterRules) {
        listOf(
            UserPreferences.PACKAGE_ZALO,
            UserPreferences.PACKAGE_TELEGRAM,
            UserPreferences.PACKAGE_MESSENGER
        ).flatMap { preferences.getRecentSenders(it) }.distinct()
    }
    var appForFilterDialog by remember { mutableStateOf<String?>(null) }

    var showRewardAdForImport by remember { mutableStateOf(false) }
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }
    var showRewardAdForDynamic by remember { mutableStateOf(false) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            pendingImportUri = uri
            showRewardAdForImport = true
        }
    }

    var isCheckingKeys by remember { mutableStateOf(false) }
    var testReports by remember { mutableStateOf<List<KeyTestReport>?>(null) }
    var showTestDialog by remember { mutableStateOf(false) }
    var keyToEditModel by remember { mutableStateOf<ApiKeyConfig?>(null) }

    var showAddHourDialog by remember { mutableStateOf(false) }
    var newHourInput by remember { mutableStateOf("") }

    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    var isBackingUp by remember { mutableStateOf(false) }
    var showRestoreDialog by remember { mutableStateOf(false) }
    var restoreJsonInput by remember { mutableStateOf("") }
    var isRestoring by remember { mutableStateOf(false) }

    // System Permissions tracking
    var isNotificationAccessGranted by remember { mutableStateOf(false) }
    var isBatteryOptIgnored by remember { mutableStateOf(false) }
    var isOverlayGranted by remember { mutableStateOf(false) }
    var isBannerDismissed by remember { mutableStateOf(preferences.isPermissionBannerDismissed()) }
    var showIntroDialog by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                try {
                    isNotificationAccessGranted = OemSurvivalHelper.isNotificationAccessGranted(context)
                    isBatteryOptIgnored = OemSurvivalHelper.isBatteryOptimizationIgnored(context)
                    isOverlayGranted = FloatingBubbleService.isOverlayPermissionGranted(context)
                } catch (e: Throwable) {
                    android.util.Log.w("SettingsScreen", "Lỗi kiểm tra quyền: ${e.message}")
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val allPermissionsGranted = isNotificationAccessGranted && isBatteryOptIgnored && isOverlayGranted

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Cấu Hình & Dự Phòng AI",
            style = MaterialTheme.typography.titleLarge,
            color = TextPrimaryDark,
            fontWeight = FontWeight.Bold
        )

        // Banner: Cấu hình đề xuất (Cấp quyền hệ thống) - Tự ẩn mượt mà khi cấp đủ hoặc khi người dùng bấm ẩn
        AnimatedVisibility(
            visible = !allPermissionsGranted && !isBannerDismissed,
            enter = fadeIn(tween(250)) + expandVertically(tween(250)),
            exit = fadeOut(tween(200)) + shrinkVertically(tween(200))
        ) {
            RecommendedPermissionsBanner(
                isNotificationAccessGranted = isNotificationAccessGranted,
                isBatteryOptIgnored = isBatteryOptIgnored,
                isOverlayGranted = isOverlayGranted,
                onDismiss = {
                    preferences.setPermissionBannerDismissed(true)
                    isBannerDismissed = true
                }
            )
        }

        var expandedSectionIndex by remember { mutableStateOf<Int?>(null) }

        // Category 0: Ứng dụng quét thông báo
        SettingsCategoryCard(
            isExpanded = expandedSectionIndex == 0,
            onToggle = { expandedSectionIndex = if (expandedSectionIndex == 0) null else 0 },
            icon = Icons.Default.Notifications,
            iconBgColor = Color(0xFF3B82F6),
            title = "Ứng Dụng Quét Thông Báo",
            subtitle = "Zalo, Telegram, Facebook Messenger",
            badgeText = "${listOf(zaloEnabled, telegramEnabled, messengerEnabled).count { it }}/3 Bật"
        ) {
            AppMonitoredSection(
                zaloEnabled = zaloEnabled,
                telegramEnabled = telegramEnabled,
                messengerEnabled = messengerEnabled,
                filterRules = filterRules,
                onToggleZalo = { preferences.setZaloEnabled(it) },
                onToggleTelegram = { preferences.setTelegramEnabled(it) },
                onToggleMessenger = { preferences.setMessengerEnabled(it) },
                onOpenFilterDialog = { pkg -> appForFilterDialog = pkg }
            )
        }

        // Category 1: Khung giờ tóm tắt tự động
        SettingsCategoryCard(
            isExpanded = expandedSectionIndex == 1,
            onToggle = { expandedSectionIndex = if (expandedSectionIndex == 1) null else 1 },
            icon = Icons.Default.Schedule,
            iconBgColor = Color(0xFF10B981),
            title = "Khung Giờ Tóm Tắt Tự Động",
            subtitle = "Lên lịch AI quét thông báo và gửi báo cáo",
            badgeText = "${scheduledHours.size} mốc giờ"
        ) {
            ScheduledHoursSection(
                scheduledHours = scheduledHours,
                onAddHourClick = {
                    newHourInput = ""
                    showAddHourDialog = true
                },
                onRemoveHour = { hourStr ->
                    preferences.removeScheduledHour(hourStr)
                    WorkScheduler.cancelScheduledHour(context, hourStr)
                    WorkScheduler.scheduleDailySummaries(context)
                    Toast.makeText(context, "Đã xóa mốc giờ $hourStr", Toast.LENGTH_SHORT).show()
                }
            )
        }

        // Category 2: Universal AI Key & Mô hình AI
        SettingsCategoryCard(
            isExpanded = expandedSectionIndex == 2,
            onToggle = { expandedSectionIndex = if (expandedSectionIndex == 2) null else 2 },
            icon = Icons.Default.Key,
            iconBgColor = Color(0xFFF59E0B),
            title = "Universal AI Key & Mô Hình",
            subtitle = "Gemini, Groq, OpenRouter • Nhấn giữ sửa Model",
            badgeText = "${keyPool.size} key"
        ) {
            MultiKeyPoolSection(
                keyPool = keyPool,
                isCheckingKeys = isCheckingKeys,
                onToggleKey = { id, enabled -> preferences.toggleApiKey(id, enabled) },
                onDeleteKey = { id -> preferences.removeApiKey(id) },
                onEditKeyModel = { config -> keyToEditModel = config },
                onAddKey = { rawKey, model ->
                    val added = preferences.addApiKey(rawKey, model)
                    if (added != null) {
                        Toast.makeText(context, "Đã thêm [${added.provider.displayName} - ${added.modelName}]!", Toast.LENGTH_SHORT).show()
                        true
                    } else {
                        Toast.makeText(context, "Key này đã có trong danh sách!", Toast.LENGTH_SHORT).show()
                        false
                    }
                },
                onTestAllKeys = {
                    if (keyPool.isEmpty()) {
                        Toast.makeText(context, "Vui lòng thêm ít nhất 1 Key để kiểm tra", Toast.LENGTH_SHORT).show()
                        return@MultiKeyPoolSection
                    }
                    isCheckingKeys = true
                    scope.launch {
                        val client = UniversalAiClient()
                        val reports = client.testAllKeys(keyPool)
                        isCheckingKeys = false
                        testReports = reports
                        showTestDialog = true
                    }
                }
            )
        }

        // Category NÃ¢ng Cao: Quáº£n LÃ½ Äa PhiÃªn (Sequential FIFO AI Queue)
        SettingsCategoryCard(
            isExpanded = expandedSectionIndex == 99,
            onToggle = { expandedSectionIndex = if (expandedSectionIndex == 99) null else 99 },
            icon = Icons.Default.FilterAlt,
            iconBgColor = Color(0xFF8B5CF6),
            title = "NÃ¢ng Cao: Quáº£n LÃ½ Äa PhiÃªn",
            subtitle = "Táº¡o phiÃªn riÃªng, Whitelist & Thá»© tá»± Æ°u tiÃªn Key",
            badgeText = "${sessions.size} phiÃªn"
        ) {
            MultiSessionManagementSection(
                sessions = sessions,
                keyPool = keyPool,
                recentSenders = allRecentSenders,
                onSaveSession = { session ->
                    preferences.saveSession(session)
                    Toast.makeText(context, "ÄÃ£ lÆ°u phiÃªn '${session.name}'!", Toast.LENGTH_SHORT).show()
                },
                onDeleteSession = { sessionId ->
                    val deleted = preferences.deleteSession(sessionId)
                    if (deleted) {
                        Toast.makeText(context, "ÄÃ£ xÃ³a phiÃªn thÃ nh cÃ´ng!", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }

        // Category 3: Phong cách văn phong trợ lý AI
        SettingsCategoryCard(
            isExpanded = expandedSectionIndex == 3,
            onToggle = { expandedSectionIndex = if (expandedSectionIndex == 3) null else 3 },
            icon = Icons.Default.Psychology,
            iconBgColor = Color(0xFF27272A),
            title = "Phong Cách Văn Phong AI",
            subtitle = "Chu đáo, Nghiêm túc, Tối giản, Hóm hỉnh...",
            badgeText = currentPersona.title
        ) {
            PersonaStyleSection(
                currentPersona = currentPersona,
                onSelectPersona = { persona ->
                    preferences.setPromptPersona(persona)
                    Toast.makeText(context, "Đã chọn: ${persona.title}", Toast.LENGTH_SHORT).show()
                }
            )
        }

        // Category 4: Trí nhớ ngữ cảnh AI & Bối cảnh
        SettingsCategoryCard(
            isExpanded = expandedSectionIndex == 4,
            onToggle = { expandedSectionIndex = if (expandedSectionIndex == 4) null else 4 },
            icon = Icons.Default.Visibility,
            iconBgColor = Color(0xFF06B6D4),
            title = "Trí Nhớ Ngữ Cảnh AI",
            subtitle = "Tóm tắt ca gần nhất, Việc dài hạn & Bối cảnh cố định"
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Tùy chọn ngữ cảnh truyền vào mỗi lần AI phân tích.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondaryDark
                )

                // Toggle 1: Recent summary context
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SurfaceVariantDark.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                        .border(1.dp, CardBorder, RoundedCornerShape(10.dp))
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Nạp tóm tắt ca gần nhất",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimaryDark
                        )
                        Text(
                            text = "AI nhận bối cảnh ca trước để nối mạch câu chuyện mượt mà",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondaryDark,
                            fontSize = 11.sp
                        )
                    }
                    Switch(
                        checked = isRecentSummaryContextEnabled,
                        onCheckedChange = { preferences.setRecentSummaryContextEnabled(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = BackgroundDark,
                            checkedTrackColor = PrimaryIndigo
                        )
                    )
                }

                // Toggle 2: Long term tasks context
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SurfaceVariantDark.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                        .border(1.dp, CardBorder, RoundedCornerShape(10.dp))
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Nạp nhiệm vụ dài hạn & ngày tương lai",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimaryDark
                        )
                        Text(
                            text = "AI luôn theo dõi việc ngày mai, tuần sau và việc chưa xong",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondaryDark,
                            fontSize = 11.sp
                        )
                    }
                    Switch(
                        checked = isLongTermTasksContextEnabled,
                        onCheckedChange = { preferences.setLongTermTasksContextEnabled(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = BackgroundDark,
                            checkedTrackColor = PrimaryIndigo
                        )
                    )
                }

                FixedContextSection(
                    customFixedContext = customFixedContext,
                    onSaveContext = { newContext ->
                        preferences.setCustomFixedContext(newContext)
                        Toast.makeText(context, "Đã lưu bối cảnh cố định thành công!", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }

        // Category 5: Hình nền & 3D Parallax
        SettingsCategoryCard(
            isExpanded = expandedSectionIndex == 5,
            onToggle = { expandedSectionIndex = if (expandedSectionIndex == 5) null else 5 },
            icon = Icons.Default.Wallpaper,
            iconBgColor = Color(0xFFEC4899),
            title = "Hình Nền Cá Nhân & 3D Parallax",
            subtitle = "Trong suốt, Kính mờ (Frosted) và Động con quay 3D",
            badgeText = if (wallpaperPath != null) wallpaperMode.title else "Mặc định"
        ) {
            WallpaperSection(
                wallpaperPath = wallpaperPath,
                wallpaperMode = wallpaperMode,
                onRequestImportWallpaper = { imagePickerLauncher.launch("image/*") },
                onClearWallpaper = {
                    preferences.clearWallpaper(context)
                    Toast.makeText(context, "Đã gỡ bỏ ảnh nền tùy chỉnh", Toast.LENGTH_SHORT).show()
                },
                onRequestSwitchMode = { targetMode: WallpaperMode ->
                    if (targetMode == WallpaperMode.DYNAMIC) {
                        showRewardAdForDynamic = true
                    } else {
                        preferences.setWallpaperMode(targetMode)
                        Toast.makeText(context, "Đã chuyển sang chế độ: ${targetMode.title}", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }

        // Category 6: Bong bóng chat AI thông minh AItu
        SettingsCategoryCard(
            isExpanded = expandedSectionIndex == 6,
            onToggle = { expandedSectionIndex = if (expandedSectionIndex == 6) null else 6 },
            icon = Icons.Default.Visibility,
            iconBgColor = Color(0xFF27272A),
            title = "Bong Bóng Chat Nổi AItu",
            subtitle = "Chu trình 3 trạng thái thông minh & Đánh thức chạm đúp cạnh màn hình",
            badgeText = "3 Trạng thái",
            badgeColor = PrimaryIndigo
        ) {
            BubbleConfigSection(
                isOverlayGranted = isOverlayGranted,
                onRequestOverlayPermission = {
                    try {
                        context.startActivity(FloatingBubbleService.getOverlayPermissionIntent(context))
                    } catch (e: Throwable) {
                        Toast.makeText(context, "Không thể mở cài đặt quyền: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                },
                onRestartBubble = {
                    FloatingBubbleService.show(context, summaryContent = "Bong bóng chat AItu đã sẵn sàng hoạt động!")
                    Toast.makeText(context, "Đã khởi động bong bóng chat AItu", Toast.LENGTH_SHORT).show()
                }
            )
        }

        // Category 7: Quyền hệ thống & Bền bỉ chạy ngầm
        SettingsCategoryCard(
            isExpanded = expandedSectionIndex == 7,
            onToggle = { expandedSectionIndex = if (expandedSectionIndex == 7) null else 7 },
            icon = Icons.Default.Security,
            iconBgColor = Color(0xFFEF4444),
            title = "Quyền Hệ Thống & Chạy Ngầm",
            subtitle = "Đọc thông báo, Tắt tối ưu pin, Cửa sổ nổi & Mẹo Samsung",
            badgeText = if (allPermissionsGranted) "Đủ quyền" else "Cần cấp quyền",
            badgeColor = if (allPermissionsGranted) AccentEmerald else AccentRose
        ) {
            PermissionsSection(
                isNotificationAccessGranted = isNotificationAccessGranted,
                isBatteryOptIgnored = isBatteryOptIgnored,
                isOverlayGranted = isOverlayGranted
            )
        }

        // Category 8: Múi giờ & Sao lưu dữ liệu
        SettingsCategoryCard(
            isExpanded = expandedSectionIndex == 8,
            onToggle = { expandedSectionIndex = if (expandedSectionIndex == 8) null else 8 },
            icon = Icons.Default.Public,
            iconBgColor = Color(0xFF27272A),
            title = "Múi Giờ & Sao Lưu Dữ Liệu",
            subtitle = "Múi giờ hệ thống AI, Sao lưu & Khôi phục JSON",
            badgeText = userTimezone
        ) {
            TimezoneAndBackupSection(
                userTimezone = userTimezone,
                onSetUserTimezone = { tz: String ->
                    preferences.setUserTimezone(tz)
                    Toast.makeText(context, "Đã cập nhật múi giờ: $tz", Toast.LENGTH_SHORT).show()
                },
                isBackingUp = isBackingUp,
                onBackupClick = {
                    isBackingUp = true
                    scope.launch {
                        val filePath = com.orca.orcaauto.data.backup.BackupManager.saveBackupToFile(context)
                        val json = com.orca.orcaauto.data.backup.BackupManager.exportBackupJson(context)
                        clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(json))
                        isBackingUp = false
                        val msg = if (filePath != null) {
                            "Đã lưu file sao lưu vào Downloads và sao chép vào Clipboard!"
                        } else {
                            "Đã sao chép toàn bộ mã sao lưu vào Clipboard!"
                        }
                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                    }
                },
                onRestoreClick = {
                    restoreJsonInput = ""
                    showRestoreDialog = true
                }
            )
        }

        // Category 9: Hướng dẫn sử dụng chi tiết
        SettingsCategoryCard(
            isExpanded = expandedSectionIndex == 9,
            onToggle = { expandedSectionIndex = if (expandedSectionIndex == 9) null else 9 },
            icon = Icons.Default.Info,
            iconBgColor = Color(0xFF14B8A6),
            title = "Hướng Dẫn Sử Dụng Chi Tiết",
            subtitle = "Lấy API key miễn phí, Cử chỉ nhiệm vụ, Báo thức"
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Cẩm nang hướng dẫn lấy API key miễn phí, cấp quyền hệ thống và các thao tác cử chỉ.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondaryDark
                )
                OutlinedButton(
                    onClick = { showIntroDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(imageVector = Icons.Default.Info, contentDescription = null, modifier = Modifier.size(16.dp), tint = AccentCyan)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("📖 Mở Cẩm Nang Hướng Dẫn Chi Tiết", fontSize = 12.5.sp, color = TextPrimaryDark)
                }
            }
        }

        // Category 10: Thông tin ứng dụng
        SettingsCategoryCard(
            isExpanded = expandedSectionIndex == 10,
            onToggle = { expandedSectionIndex = if (expandedSectionIndex == 10) null else 10 },
            icon = Icons.Default.Info,
            iconBgColor = Color(0xFF64748B),
            title = "Thông Tin Ứng Dụng",
            subtitle = "CS Orca • v2.4.0 (Build 2026.11.01-Release)"
        ) {
            AppInfoSection()
        }
    }

    if (showRewardAdForImport && pendingImportUri != null) {
        MockRewardAdDialog(
            actionTitle = "Áp Dụng Hình Nền Mới",
            description = "Xem video ngắn 5 giây để kích hoạt ảnh nền cá nhân của bạn.",
            onDismiss = {
                showRewardAdForImport = false
                pendingImportUri = null
            },
            onRewardGranted = {
                val uri = pendingImportUri
                if (uri != null) {
                    scope.launch(Dispatchers.IO) {
                        try {
                            val inputStream = context.contentResolver.openInputStream(uri)
                            val destFile = java.io.File(context.filesDir, "custom_wallpaper.jpg")
                            destFile.outputStream().use { out ->
                                inputStream?.copyTo(out)
                            }
                            withContext(Dispatchers.Main) {
                                preferences.setWallpaperPath(destFile.absolutePath)
                                // Per requirement: whenever a new image is imported, default to CLEAR ("trong")
                                preferences.setWallpaperMode(WallpaperMode.CLEAR)
                                Toast.makeText(context, "Đã áp dụng ảnh nền! (Mặc định: Trong suốt)", Toast.LENGTH_SHORT).show()
                                showRewardAdForImport = false
                                pendingImportUri = null
                            }
                        } catch (e: Throwable) {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "Lỗi lưu ảnh: ${e.message}", Toast.LENGTH_SHORT).show()
                                showRewardAdForImport = false
                                pendingImportUri = null
                            }
                        }
                    }
                }
            }
        )
    }

    if (showRewardAdForDynamic) {
        MockRewardAdDialog(
            actionTitle = "Mở Khóa Chế Độ Động (3D Parallax)",
            description = "Tính năng đặc biệt: Hiệu ứng con quay hồi chuyển 3D Parallax & Hạt phát sáng.",
            onDismiss = { showRewardAdForDynamic = false },
            onRewardGranted = {
                preferences.setWallpaperMode(WallpaperMode.DYNAMIC)
                Toast.makeText(context, "Đã kích hoạt Chế Độ Động (3D Parallax)!", Toast.LENGTH_SHORT).show()
                showRewardAdForDynamic = false
            }
        )
    }

    if (keyToEditModel != null) {
        EditKeyModelDialog(
            config = keyToEditModel!!,
            onDismiss = { keyToEditModel = null },
            onSaveModel = { newModel ->
                preferences.updateKeyModel(keyToEditModel!!.rawKey, newModel)
                Toast.makeText(context, "Đã cập nhật model: $newModel", Toast.LENGTH_SHORT).show()
                keyToEditModel = null
            }
        )
    }

    if (showIntroDialog) {
        OnboardingGuideDialog(
            initialDontShowAgain = preferences.isIntroDismissedPermanently(),
            onDismiss = { dontShowAgain ->
                preferences.setIntroDismissedPermanently(dontShowAgain)
                showIntroDialog = false
            }
        )
    }

    // Modal Dialog: Add New Scheduled Hour
    if (showAddHourDialog) {
        AlertDialog(
            onDismissRequest = { showAddHourDialog = false },
            title = {
                Text("Thêm Khung Giờ Báo Cáo", fontWeight = FontWeight.Bold, color = TextPrimaryDark)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Nhập giờ theo định dạng 24h (HH:mm), ví dụ: 08:30, 14:00, 21:00", style = MaterialTheme.typography.bodySmall, color = TextSecondaryDark)
                    OutlinedTextField(
                        value = newHourInput,
                        onValueChange = { newHourInput = it },
                        singleLine = true,
                        placeholder = { Text("08:00") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PrimaryIndigo,
                            unfocusedBorderColor = CardBorder,
                            focusedTextColor = TextPrimaryDark,
                            unfocusedTextColor = TextPrimaryDark
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val added = preferences.addScheduledHour(newHourInput)
                        if (added) {
                            WorkScheduler.scheduleDailySummaries(context)
                            Toast.makeText(context, "Đã thêm khung giờ $newHourInput", Toast.LENGTH_SHORT).show()
                            showAddHourDialog = false
                        } else {
                            Toast.makeText(context, "Định dạng không hợp lệ (HH:mm) hoặc giờ đã tồn tại!", Toast.LENGTH_LONG).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrimaryIndigo,
                        contentColor = BackgroundDark
                    )
                ) {
                    Text("Xác Nhận", color = BackgroundDark, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddHourDialog = false }) {
                    Text("Hủy", color = TextSecondaryDark)
                }
            },
            containerColor = SurfaceDark
        )
    }

    // Modal Dialog: Detailed Test Results for All Keys
    if (showTestDialog && testReports != null) {
        val reports = testReports!!
        val successCount = reports.count { it.isSuccess }

        AlertDialog(
            onDismissRequest = { showTestDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Kết Quả Kiểm Tra API Key",
                        fontWeight = FontWeight.Bold,
                        color = TextPrimaryDark,
                        modifier = Modifier.weight(1f)
                    )
                    Box(
                        modifier = Modifier
                            .background(
                                if (successCount > 0) AccentEmerald.copy(alpha = 0.2f) else AccentRose.copy(alpha = 0.2f),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "$successCount/${reports.size} OK",
                            color = if (successCount > 0) AccentEmerald else AccentRose,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            },
            text = {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(reports) { report ->
                        KeyReportItem(report)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showTestDialog = false },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrimaryIndigo,
                        contentColor = BackgroundDark
                    )
                ) {
                    Text("Đóng", color = BackgroundDark, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = SurfaceDark
        )
    }

    // Modal Dialog: Restore Backup
    if (showRestoreDialog) {
        AlertDialog(
            onDismissRequest = { if (!isRestoring) showRestoreDialog = false },
            title = {
                Text("Phục Hồi Dữ Liệu (Restore)", fontWeight = FontWeight.Bold, color = TextPrimaryDark)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Dán đoạn mã sao lưu JSON hoặc bấm nút 'Dán Từ Clipboard' để nạp cấu hình:",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondaryDark
                    )
                    OutlinedButton(
                        onClick = {
                            val clipText = clipboardManager.getText()?.text.orEmpty()
                            if (clipText.isNotBlank()) {
                                restoreJsonInput = clipText
                                Toast.makeText(context, "Đã dán từ bộ nhớ tạm!", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Bộ nhớ tạm đang trống!", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("📋 Dán Từ Clipboard", fontSize = 12.sp)
                    }
                    OutlinedTextField(
                        value = restoreJsonInput,
                        onValueChange = { restoreJsonInput = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 100.dp, max = 200.dp),
                        placeholder = { Text("{\"version\": 1, ...}", color = TextMutedDark, fontSize = 12.sp) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PrimaryIndigo,
                            unfocusedBorderColor = CardBorder,
                            focusedTextColor = TextPrimaryDark,
                            unfocusedTextColor = TextPrimaryDark
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (restoreJsonInput.isBlank()) {
                            Toast.makeText(context, "Vui lòng dán dữ liệu sao lưu trước!", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        isRestoring = true
                        scope.launch {
                            val res = com.orca.orcaauto.data.backup.BackupManager.restoreFromJson(context, restoreJsonInput)
                            isRestoring = false
                            if (res.isSuccess) {
                                Toast.makeText(context, res.getOrThrow(), Toast.LENGTH_LONG).show()
                                showRestoreDialog = false
                            } else {
                                Toast.makeText(context, "Lỗi phục hồi: ${res.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrimaryIndigo,
                        contentColor = BackgroundDark
                    ),
                    enabled = !isRestoring
                ) {
                    if (isRestoring) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = BackgroundDark)
                    } else {
                        Text("Tiến Hành Phục Hồi", color = BackgroundDark, fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreDialog = false }, enabled = !isRestoring) {
                    Text("Hủy", color = TextSecondaryDark)
                }
            },
            containerColor = SurfaceDark
        )
    }

    appForFilterDialog?.let { pkg ->
        AppChatFilterDialog(
            packageName = pkg,
            appName = preferences.getTargetAppName(pkg),
            initialRule = filterRules[pkg] ?: AppFilterRule(appPackage = pkg),
            recentSenders = preferences.getRecentSenders(pkg),
            onDismiss = { appForFilterDialog = null },
            onSaveRule = { updatedRule ->
                preferences.saveAppFilterRule(updatedRule)
                Toast.makeText(context, "Đã lưu bộ lọc cho ${preferences.getTargetAppName(pkg)}", Toast.LENGTH_SHORT).show()
                appForFilterDialog = null
            }
        )
    }
}

@Composable
private fun MultiKeyPoolSection(
    keyPool: List<ApiKeyConfig>,
    isCheckingKeys: Boolean,
    onToggleKey: (String, Boolean) -> Unit,
    onDeleteKey: (String) -> Unit,
    onEditKeyModel: (ApiKeyConfig) -> Unit,
    onAddKey: (String, String) -> Boolean,
    onTestAllKeys: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    var inputKey by remember { mutableStateOf("") }
    var isKeyVisible by remember { mutableStateOf(false) }

    val detectedConfig = remember(inputKey) {
        if (inputKey.isNotBlank()) ApiKeyConfig.fromRawKey(inputKey) else null
    }

    var selectedModel by remember(detectedConfig) {
        mutableStateOf(detectedConfig?.modelName ?: "")
    }
    var isModelMenuExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Tự động nhận diện: Gemini (2.5/2.0/1.5 Flash), Groq Llama 3.3, DeepSeek V3/R1, OpenRouter. Nếu Key 1 quá tải, app lập tức chuyển sang Key tiếp theo.",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondaryDark
        )

            // List of Configured Keys
            if (keyPool.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    keyPool.forEachIndexed { index, config ->
                        KeyItemRow(
                            index = index + 1,
                            config = config,
                            onToggle = { onToggleKey(config.id, it) },
                            onDelete = { onDeleteKey(config.id) },
                            onLongClick = { onEditKeyModel(config) }
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SurfaceVariantDark.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Chưa có Key nào. Dán API Key bên dưới để thêm.",
                        color = TextMutedDark,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Input Field for new Key (Only this section recomposes while typing)
            OutlinedTextField(
                value = inputKey,
                onValueChange = { inputKey = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Dán API Key (Gemini, Groq, DeepSeek, v.v.)") },
                placeholder = { Text("AIzaSy... hoặc gsk_... hoặc sk-...") },
                singleLine = true,
                visualTransformation = if (isKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                trailingIcon = {
                    IconButton(onClick = { isKeyVisible = !isKeyVisible }) {
                        Icon(
                            imageVector = if (isKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = null,
                            tint = TextSecondaryDark
                        )
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PrimaryIndigo,
                    unfocusedBorderColor = CardBorder,
                    focusedTextColor = TextPrimaryDark,
                    unfocusedTextColor = TextPrimaryDark
                )
            )

            // Detected Provider & Model Selector
            if (detectedConfig != null) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Nhà cung cấp: ",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondaryDark
                        )
                        Box(
                            modifier = Modifier
                                .background(PrimaryIndigo.copy(alpha = 0.25f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = detectedConfig.provider.displayName,
                                color = AccentCyan,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Model Selector Dropdown
                    Box {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(SurfaceVariantDark.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                                .border(1.dp, CardBorder, RoundedCornerShape(8.dp))
                                .clickable { isModelMenuExpanded = true }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Model kích hoạt:",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextMutedDark,
                                    fontSize = 10.sp
                                )
                                Text(
                                    text = selectedModel.ifBlank { detectedConfig.modelName },
                                    color = AccentEmerald,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = null,
                                tint = TextSecondaryDark
                            )
                        }

                        DropdownMenu(
                            expanded = isModelMenuExpanded,
                            onDismissRequest = { isModelMenuExpanded = false }
                        ) {
                            detectedConfig.provider.supportedModels.forEach { modelOption ->
                                DropdownMenuItem(
                                    text = { Text(modelOption) },
                                    onClick = {
                                        selectedModel = modelOption
                                        isModelMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        if (inputKey.isBlank()) return@Button
                        val model = if (detectedConfig != null && selectedModel.isNotBlank()) selectedModel else detectedConfig?.modelName ?: ""
                        val success = onAddKey(inputKey, model)
                        if (success) {
                            inputKey = ""
                            focusManager.clearFocus()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrimaryIndigo,
                        contentColor = BackgroundDark
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null, tint = BackgroundDark)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Thêm Key", color = BackgroundDark, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = onTestAllKeys,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    enabled = !isCheckingKeys
                ) {
                    if (isCheckingKeys) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(imageVector = Icons.AutoMirrored.Filled.Send, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Test Tất Cả")
                    }
                }
            }
        }
}

@Composable
private fun PersonaStyleSection(
    currentPersona: PromptPersona,
    onSelectPersona: (PromptPersona) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, GlassBorder, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = GlassSurfaceDark)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .background(AccentEmerald.copy(alpha = 0.2f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("🎭", fontSize = 12.sp)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Phong Cách Văn Phong Trợ Lý AI",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimaryDark
                )
            }

            Text(
                text = "Chọn giọng điệu tóm tắt phù hợp với bạn. Trợ lý sẽ điều chỉnh cách nhắc nhở nhẹ nhàng, không gây áp lực.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondaryDark
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PromptPersona.entries.forEach { persona ->
                    val isSelected = persona == currentPersona
                    val borderColor = if (isSelected) PrimaryIndigo else CardBorder
                    val bgColor = if (isSelected) PrimaryIndigo.copy(alpha = 0.15f) else SurfaceVariantDark.copy(alpha = 0.35f)

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(if (isSelected) 1.5.dp else 1.dp, borderColor, RoundedCornerShape(12.dp))
                            .clickable { onSelectPersona(persona) },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = bgColor)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = persona.icon, fontSize = 20.sp)
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = persona.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) AccentCyan else TextPrimaryDark
                                    )
                                    Text(
                                        text = persona.subtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondaryDark,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .background(PrimaryIndigo, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FixedContextSection(
    customFixedContext: String,
    onSaveContext: (String) -> Unit
) {
    val focusManager = LocalFocusManager.current
    var fixedContextInput by remember(customFixedContext) { mutableStateOf(customFixedContext) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, GlassBorder, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = GlassSurfaceDark)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .background(AccentCyan.copy(alpha = 0.2f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("📌", fontSize = 12.sp)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Bối Cảnh & Thông Tin Cố Định Của Bạn",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimaryDark
                )
            }

            Text(
                text = "Thông tin này được đính kèm tự động vào mỗi lệnh AI (chức danh, phong cách làm việc, khách VIP, quy tắc cá nhân...)",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondaryDark
            )

            OutlinedTextField(
                value = fixedContextInput,
                onValueChange = { fixedContextInput = it },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 6,
                placeholder = {
                    Text(
                        "Ví dụ: Tôi là Giám đốc công ty. Các khách VIP cần chú ý cao nhất là anh Hải (0901...) và chị Mai. Báo cáo cần ngắn gọn, trực diện, không dài dòng.",
                        fontSize = 13.sp,
                        color = TextMutedDark
                    )
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PrimaryIndigo,
                    unfocusedBorderColor = CardBorder,
                    focusedTextColor = TextPrimaryDark,
                    unfocusedTextColor = TextPrimaryDark
                ),
                shape = RoundedCornerShape(10.dp)
            )

            Button(
                onClick = {
                    onSaveContext(fixedContextInput)
                    focusManager.clearFocus()
                },
                modifier = Modifier.align(Alignment.End),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PrimaryIndigo,
                    contentColor = BackgroundDark
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp), tint = BackgroundDark)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Lưu Bối Cảnh", fontSize = 13.sp, color = BackgroundDark, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ScheduledHoursSection(
    scheduledHours: List<String>,
    onAddHourClick: () -> Unit,
    onRemoveHour: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "AI sẽ quét thông báo và hiện bong bóng tổng hợp báo cáo vào đúng các khung giờ này.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondaryDark,
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.width(8.dp))

            OutlinedButton(
                onClick = onAddHourClick,
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Thêm", fontSize = 12.sp, maxLines = 1)
            }
        }

        // List of hour chips
        if (scheduledHours.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                scheduledHours.chunked(2).forEach { rowHours ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowHours.forEach { hourStr ->
                            RemovableHourChip(
                                time = hourStr,
                                modifier = Modifier.weight(1f),
                                onRemove = { onRemoveHour(hourStr) }
                            )
                        }
                        if (rowHours.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        } else {
            Text(
                text = "Chưa có khung giờ nào. Bấm 'Thêm' để thiết lập.",
                style = MaterialTheme.typography.bodySmall,
                color = TextMutedDark
            )
        }
    }
}

@Composable
private fun AppMonitoredSection(
    zaloEnabled: Boolean,
    telegramEnabled: Boolean,
    messengerEnabled: Boolean,
    filterRules: Map<String, AppFilterRule>,
    onToggleZalo: (Boolean) -> Unit,
    onToggleTelegram: (Boolean) -> Unit,
    onToggleMessenger: (Boolean) -> Unit,
    onOpenFilterDialog: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "Chọn các ứng dụng nhận tin nhắn mà AI sẽ theo dõi để phát hiện công việc. Nhấn giữ vào từng ứng dụng để cài đặt Bộ lọc (Chặn hoặc Chỉ định riêng).",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondaryDark
        )

        AppToggleRow(
            appName = "Zalo",
            packageDescription = UserPreferences.PACKAGE_ZALO,
            isChecked = zaloEnabled,
            rule = filterRules[UserPreferences.PACKAGE_ZALO],
            onCheckedChange = onToggleZalo,
            onLongClick = { onOpenFilterDialog(UserPreferences.PACKAGE_ZALO) }
        )

        AppToggleRow(
            appName = "Telegram",
            packageDescription = UserPreferences.PACKAGE_TELEGRAM,
            isChecked = telegramEnabled,
            rule = filterRules[UserPreferences.PACKAGE_TELEGRAM],
            onCheckedChange = onToggleTelegram,
            onLongClick = { onOpenFilterDialog(UserPreferences.PACKAGE_TELEGRAM) }
        )

        AppToggleRow(
            appName = "Facebook Messenger",
            packageDescription = UserPreferences.PACKAGE_MESSENGER,
            isChecked = messengerEnabled,
            rule = filterRules[UserPreferences.PACKAGE_MESSENGER],
            onCheckedChange = onToggleMessenger,
            onLongClick = { onOpenFilterDialog(UserPreferences.PACKAGE_MESSENGER) }
        )
    }
}

@Composable
private fun RecommendedPermissionsBanner(
    isNotificationAccessGranted: Boolean,
    isBatteryOptIgnored: Boolean,
    isOverlayGranted: Boolean,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, PrimaryIndigo.copy(alpha = 0.5f), RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(PrimaryIndigo.copy(alpha = 0.2f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = AccentCyan,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Cấu Hình Đề Xuất (Cấp Quyền)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimaryDark
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Ẩn đề xuất",
                        tint = TextMutedDark,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Text(
                text = "Cấp các quyền cần thiết để AI có thể đọc thông báo và hiện bong bóng điều phối ổn định trong nền.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondaryDark
            )

            // Permissions list
            if (!isNotificationAccessGranted) {
                PermissionActionRow(
                    icon = Icons.Default.Notifications,
                    iconTint = AccentCyan,
                    title = "Quyền đọc thông báo",
                    desc = "Cho phép thu thập tin Zalo, Telegram, Messenger",
                    buttonText = "Cấp quyền",
                    onGrantClick = {
                        val intent = OemSurvivalHelper.getNotificationAccessIntent(context)
                        context.startActivity(intent)
                    }
                )
            }

            if (!isBatteryOptIgnored) {
                PermissionActionRow(
                    icon = Icons.Default.BatteryChargingFull,
                    iconTint = AccentEmerald,
                    title = "Tắt tối ưu hóa pin",
                    desc = "Giữ AI chạy đúng giờ hẹn, không bị hệ thống tắt",
                    buttonText = "Cấp quyền",
                    onGrantClick = {
                        val intent = OemSurvivalHelper.getIgnoreBatteryOptimizationIntent(context)
                        try {
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Mở cài đặt pin thất bại", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }

            if (!isOverlayGranted) {
                PermissionActionRow(
                    icon = Icons.Default.Security,
                    iconTint = Color(0xFFE4E4E7),
                    title = "Quyền bong bóng chat nổi",
                    desc = "Hiển thị bong bóng tóm tắt trên màn hình",
                    buttonText = "Cấp quyền",
                    onGrantClick = {
                        val intent = FloatingBubbleService.getOverlayPermissionIntent(context)
                        try {
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Mở cài đặt quyền hiển thị nổi thất bại", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) {
                    Text("✕ Ẩn thông báo này", color = TextMutedDark, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun PermissionActionRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    desc: String,
    buttonText: String,
    onGrantClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceVariantDark.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
            .padding(10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(iconTint.copy(alpha = 0.15f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimaryDark
                )
                Text(
                    text = desc,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondaryDark,
                    fontSize = 11.sp
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Button(
            onClick = onGrantClick,
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = PrimaryIndigo,
                contentColor = BackgroundDark
            )
        ) {
            Text(buttonText, fontSize = 11.sp, color = BackgroundDark, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun WallpaperSection(
    wallpaperPath: String?,
    wallpaperMode: WallpaperMode,
    onRequestImportWallpaper: () -> Unit,
    onClearWallpaper: () -> Unit,
    onRequestSwitchMode: (WallpaperMode) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "Hỗ trợ 3 chế độ: Trong suốt, Kính mờ và Động con quay hồi chuyển 3D Parallax.",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondaryDark
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfaceVariantDark.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                .border(1.dp, CardBorder, RoundedCornerShape(10.dp))
                .padding(12.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (wallpaperPath != null) "Ảnh nền: Đang áp dụng" else "Ảnh nền: Mặc định tối giản",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = if (wallpaperPath != null) AccentEmerald else TextPrimaryDark
                        )
                        Text(
                            text = if (wallpaperPath != null) "Chế độ hiện tại: ${wallpaperMode.title}" else "Chưa cài đặt ảnh nền tùy chỉnh",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMutedDark,
                            fontSize = 11.sp
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Button(
                            onClick = onRequestImportWallpaper,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PrimaryIndigo,
                                contentColor = BackgroundDark
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text(
                                text = if (wallpaperPath != null) "Đổi Ảnh" else "Chọn Ảnh",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = BackgroundDark
                            )
                        }

                        if (wallpaperPath != null) {
                            OutlinedButton(
                                onClick = onClearWallpaper,
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text("Gỡ Bỏ", fontSize = 11.5.sp, color = AccentRose)
                            }
                        }
                    }
                }

                if (wallpaperPath != null) {
                    Text(
                        text = "CHẾ ĐỘ HIỂN THỊ HÌNH NỀN:",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = AccentCyan,
                        fontSize = 10.5.sp
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        WallpaperMode.entries.forEach { m ->
                            val isSelected = (wallpaperMode == m)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .border(
                                        width = if (isSelected) 1.5.dp else 1.dp,
                                        color = if (isSelected) AccentCyan else CardBorder,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .background(
                                        color = if (isSelected) PrimaryIndigo.copy(alpha = 0.35f) else SurfaceVariantDark.copy(alpha = 0.4f),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable { onRequestSwitchMode(m) }
                                    .padding(vertical = 8.dp, horizontal = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = m.title,
                                        color = if (isSelected) AccentCyan else TextPrimaryDark,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = if (m == WallpaperMode.DYNAMIC) "👑 Thưởng" else "Miễn phí",
                                        color = if (m == WallpaperMode.DYNAMIC) Color(0xFFF59E0B) else TextMutedDark,
                                        fontSize = 9.sp,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionsSection(
    isNotificationAccessGranted: Boolean,
    isBatteryOptIgnored: Boolean,
    isOverlayGranted: Boolean
) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "Trạng thái cấp các quyền hệ thống cốt lõi để trợ lý AI hoạt động bền bỉ trong nền.",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondaryDark
        )

        PermissionStatusRow(
            title = "Quyền đọc thông báo",
            isGranted = isNotificationAccessGranted,
            onGrant = { context.startActivity(OemSurvivalHelper.getNotificationAccessIntent(context)) }
        )

        PermissionStatusRow(
            title = "Bỏ qua tối ưu pin",
            isGranted = isBatteryOptIgnored,
            onGrant = {
                try {
                    context.startActivity(OemSurvivalHelper.getIgnoreBatteryOptimizationIntent(context))
                } catch (e: Exception) {
                    Toast.makeText(context, "Mở cài đặt pin thất bại", Toast.LENGTH_SHORT).show()
                }
            }
        )

        PermissionStatusRow(
            title = "Bong bóng chat nổi",
            isGranted = isOverlayGranted,
            onGrant = {
                try {
                    context.startActivity(FloatingBubbleService.getOverlayPermissionIntent(context))
                } catch (e: Exception) {
                    Toast.makeText(context, "Mở cài đặt overlay thất bại", Toast.LENGTH_SHORT).show()
                }
            }
        )


        OutlinedButton(
            onClick = {
                val intent = OemSurvivalHelper.getOemAutostartIntent(context)
                if (intent != null) {
                    try {
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(context, "Mở cài đặt tự khởi chạy thất bại", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(context, "Thiết bị này không yêu cầu cài đặt autostart riêng.", Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text("🚀 Cài Đặt Tự Khởi Chạy Cùng Máy (Autostart)", fontSize = 12.sp, color = TextPrimaryDark)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TimezoneAndBackupSection(
    userTimezone: String,
    onSetUserTimezone: (String) -> Unit,
    isBackingUp: Boolean,
    onBackupClick: () -> Unit,
    onRestoreClick: () -> Unit
) {
    var showCustomTzDialog by remember { mutableStateOf(false) }
    var customTzInput by remember { mutableStateOf("") }

    val commonTimezones = listOf(
        "Asia/Ho_Chi_Minh" to "Việt Nam (GMT+7)",
        "Asia/Bangkok" to "Thái Lan (GMT+7)",
        "Asia/Tokyo" to "Nhật Bản (GMT+9)",
        "Asia/Seoul" to "Hàn Quốc (GMT+9)",
        "UTC" to "Giờ Quốc Tế (UTC)"
    )

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // --- Phần Múi Giờ ---
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Múi Giờ Hệ Thống & Thời Gian AI",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = AccentCyan
            )
            Text(
                text = "Múi giờ chuẩn để AI đặt báo thức và tổng hợp chính xác.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondaryDark
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceVariantDark.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                    .border(1.dp, CardBorder, RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Múi giờ hiện tại:", fontSize = 11.sp, color = TextMutedDark)
                        Text(userTimezone, fontWeight = FontWeight.Bold, color = AccentEmerald, fontSize = 13.sp)
                    }
                    OutlinedButton(
                        onClick = {
                            customTzInput = userTimezone
                            showCustomTzDialog = true
                        },
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Text("Tùy Chỉnh Khác", fontSize = 11.sp)
                    }
                }
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                commonTimezones.forEach { (tzId, label) ->
                    val isSelected = (userTimezone == tzId)
                    Box(
                        modifier = Modifier
                            .background(
                                if (isSelected) PrimaryIndigo else SurfaceVariantDark.copy(alpha = 0.4f),
                                RoundedCornerShape(6.dp)
                            )
                            .border(
                                1.dp,
                                if (isSelected) AccentCyan else CardBorder,
                                RoundedCornerShape(6.dp)
                            )
                            .clickable { onSetUserTimezone(tzId) }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = label,
                            fontSize = 11.sp,
                            color = if (isSelected) Color.White else TextPrimaryDark,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }

        HorizontalDivider(color = GlassBorderSubtle, thickness = 0.8.dp)

        // --- Phần Sao Lưu & Phục Hồi ---
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Sao Lưu & Phục Hồi Dữ Liệu",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = AccentCyan
            )
            Text(
                text = "Sao lưu toàn bộ cấu hình vào JSON hoặc khôi phục từ bản cũ.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondaryDark
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onBackupClick,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrimaryIndigo,
                        contentColor = BackgroundDark
                    ),
                    shape = RoundedCornerShape(10.dp),
                    enabled = !isBackingUp
                ) {
                    if (isBackingUp) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = BackgroundDark)
                    } else {
                        Text("Sao Lưu (Backup)", fontSize = 12.sp, color = BackgroundDark, fontWeight = FontWeight.Bold)
                    }
                }

                OutlinedButton(
                    onClick = onRestoreClick,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Phục Hồi (Restore)", fontSize = 12.sp)
                }
            }
        }
    }

    if (showCustomTzDialog) {
        AlertDialog(
            onDismissRequest = { showCustomTzDialog = false },
            title = {
                Text("Nhập ID Múi Giờ", fontWeight = FontWeight.Bold, color = TextPrimaryDark)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Nhập mã định danh múi giờ chuẩn Java/IANA (Ví dụ: Asia/Ho_Chi_Minh, Asia/Tokyo, America/New_York, UTC):",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondaryDark
                    )
                    OutlinedTextField(
                        value = customTzInput,
                        onValueChange = { customTzInput = it },
                        singleLine = true,
                        placeholder = { Text("Asia/Ho_Chi_Minh") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PrimaryIndigo,
                            unfocusedBorderColor = CardBorder,
                            focusedTextColor = TextPrimaryDark,
                            unfocusedTextColor = TextPrimaryDark
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = customTzInput.trim()
                        if (trimmed.isNotBlank()) {
                            onSetUserTimezone(trimmed)
                            showCustomTzDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrimaryIndigo,
                        contentColor = BackgroundDark
                    )
                ) {
                    Text("Áp Dụng", color = BackgroundDark, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomTzDialog = false }) {
                    Text("Hủy", color = TextSecondaryDark)
                }
            },
            containerColor = SurfaceDark
        )
    }
}

@Composable
private fun PermissionStatusRow(
    title: String,
    isGranted: Boolean,
    onGrant: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceVariantDark.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(if (isGranted) AccentEmerald else AccentRose, CircleShape)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = TextPrimaryDark
            )
        }

        if (isGranted) {
            Text(
                text = "✓ Đã cấp",
                style = MaterialTheme.typography.labelSmall,
                color = AccentEmerald,
                fontWeight = FontWeight.Bold
            )
        } else {
            OutlinedButton(
                onClick = onGrant,
                shape = RoundedCornerShape(6.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text("Cấp quyền", fontSize = 10.sp, color = AccentCyan)
            }
        }
    }
}

@Composable
private fun KeyReportItem(report: KeyTestReport) {
    val borderColor = if (report.isSuccess) AccentEmerald.copy(alpha = 0.5f) else AccentRose.copy(alpha = 0.5f)
    val bgColor = if (report.isSuccess) AccentEmerald.copy(alpha = 0.08f) else AccentRose.copy(alpha = 0.08f)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, borderColor, RoundedCornerShape(10.dp)),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${report.config.provider.displayName} • ${report.config.modelName}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimaryDark
                )
                Text(
                    text = "${report.latencyMs}ms",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMutedDark
                )
            }

            Text(
                text = "Key: ${report.config.maskedKey}",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondaryDark
            )

            if (report.isSuccess) {
                Text(
                    text = "✅ Phản hồi: ${report.responsePreview.ifBlank { "OK" }}",
                    color = AccentEmerald,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium
                )
            } else {
                Text(
                    text = "❌ Lỗi: ${report.errorMessage}",
                    color = AccentRose,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun RemovableHourChip(
    time: String,
    modifier: Modifier = Modifier,
    onRemove: () -> Unit
) {
    Card(
        modifier = modifier.border(1.dp, CardBorder, RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = time,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = AccentCyan
                )
                Text(
                    text = "Báo cáo tự động",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMutedDark,
                    fontSize = 10.sp
                )
            }
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Xóa giờ",
                    tint = AccentRose.copy(alpha = 0.8f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun KeyItemRow(
    index: Int,
    config: ApiKeyConfig,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onLongClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(GlassSurfaceDark, RoundedCornerShape(10.dp))
            .border(1.dp, GlassBorderSubtle, RoundedCornerShape(10.dp))
            .combinedClickable(
                onClick = onLongClick,
                onLongClick = onLongClick
            )
            .padding(10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .background(PrimaryIndigo, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "$index",
                        color = BackgroundDark,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = config.provider.displayName,
                    color = AccentCyan,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = "${config.modelName} • ${config.maskedKey}",
                color = AccentEmerald,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(
                checked = config.isEnabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = BackgroundDark,
                    checkedTrackColor = PrimaryIndigo
                )
            )
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Xóa Key",
                    tint = AccentRose.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditKeyModelDialog(
    config: ApiKeyConfig,
    onDismiss: () -> Unit,
    onSaveModel: (String) -> Unit
) {
    var modelInput by remember(config) { mutableStateOf(config.modelName) }

    val suggestions = remember(config.provider) {
        config.provider.supportedModels.ifEmpty {
            listOf("gemini-2.5-flash", "gpt-4o-mini", "deepseek-chat")
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Sửa Mô Hình AI (Model)", fontWeight = FontWeight.Bold, color = TextPrimaryDark)
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${config.provider.displayName} • ${config.maskedKey}",
                    fontSize = 11.sp,
                    color = AccentCyan
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Nhập tên mã mô hình AI bạn muốn chạy với API Key này:",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondaryDark
                )

                OutlinedTextField(
                    value = modelInput,
                    onValueChange = { modelInput = it },
                    singleLine = true,
                    placeholder = { Text("Ví dụ: gemini-2.5-flash") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryIndigo,
                        unfocusedBorderColor = CardBorder,
                        focusedTextColor = TextPrimaryDark,
                        unfocusedTextColor = TextPrimaryDark
                    )
                )

                Text(
                    text = "Gợi ý mô hình phổ biến:",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMutedDark,
                    fontWeight = FontWeight.SemiBold
                )

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    suggestions.forEach { sug ->
                        val isSelected = modelInput.trim() == sug
                        Box(
                            modifier = Modifier
                                .background(
                                    if (isSelected) PrimaryIndigo else SurfaceVariantDark,
                                    RoundedCornerShape(6.dp)
                                )
                                .border(
                                    1.dp,
                                    if (isSelected) AccentCyan else CardBorder,
                                    RoundedCornerShape(6.dp)
                                )
                                .clickable { modelInput = sug }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = sug,
                                fontSize = 11.sp,
                                color = if (isSelected) Color.White else TextPrimaryDark,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmed = modelInput.trim()
                    if (trimmed.isNotBlank()) {
                        onSaveModel(trimmed)
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = PrimaryIndigo,
                    contentColor = BackgroundDark
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Lưu Mô Hình", fontWeight = FontWeight.Bold, color = BackgroundDark)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Hủy", color = TextSecondaryDark)
            }
        },
        containerColor = SurfaceDark
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppToggleRow(
    appName: String,
    packageDescription: String,
    isChecked: Boolean,
    rule: AppFilterRule? = null,
    onCheckedChange: (Boolean) -> Unit,
    onLongClick: () -> Unit
) {
    val ruleMode = rule?.mode ?: ChatFilterMode.NORMAL_WITH_BLOCK
    val ruleCount = rule?.chatNames?.size ?: 0
    val statusText = if (ruleCount > 0) {
        when (ruleMode) {
            ChatFilterMode.NORMAL_WITH_BLOCK -> "Nghe thường (Chặn $ruleCount chat)"
            ChatFilterMode.STRICT_WHITELIST -> "Chỉ định riêng ($ruleCount chat)"
        }
    } else {
        when (ruleMode) {
            ChatFilterMode.NORMAL_WITH_BLOCK -> "Nghe thường (chưa chặn ai)"
            ChatFilterMode.STRICT_WHITELIST -> "Chỉ định riêng (chưa thêm)"
        }
    }

    val statusColor = if (ruleCount > 0) {
        if (ruleMode == ChatFilterMode.STRICT_WHITELIST) AccentEmerald else AccentAmber
    } else {
        TextMutedDark
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = { onCheckedChange(!isChecked) },
                onLongClick = onLongClick
            )
            .background(SurfaceDark.copy(alpha = 0.5f))
            .border(0.5.dp, GlassBorderSubtle, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = appName,
                    color = TextPrimaryDark,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.5.sp
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "• Giữ để cài lọc",
                    color = PrimaryIndigoLight,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "$statusText • $packageDescription",
                color = statusColor,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 11.5.sp
            )
        }
        Switch(
            checked = isChecked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = BackgroundDark,
                checkedTrackColor = PrimaryIndigo,
                uncheckedTrackColor = SurfaceDark,
                uncheckedBorderColor = CardBorder
            )
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppChatFilterDialog(
    packageName: String,
    appName: String,
    initialRule: AppFilterRule,
    recentSenders: List<String>,
    onDismiss: () -> Unit,
    onSaveRule: (AppFilterRule) -> Unit
) {
    var selectedMode by remember { mutableStateOf(initialRule.mode) }
    val chatNamesList = remember { mutableStateListOf<String>().apply { addAll(initialRule.chatNames) } }
    var newChatInput by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E1E24),
        title = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(PrimaryIndigo.copy(alpha = 0.2f), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.FilterAlt,
                            contentDescription = null,
                            tint = PrimaryIndigoLight,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Bộ Lọc — $appName",
                        fontWeight = FontWeight.Bold,
                        color = TextPrimaryDark,
                        fontSize = 17.sp
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Gói ứng dụng: $packageName",
                    color = TextMutedDark,
                    fontSize = 11.5.sp
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "CHẾ ĐỘ NHẬN TIN NHẮN:",
                    color = TextPrimaryDark,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )

                // Option 1: NORMAL_WITH_BLOCK
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { selectedMode = ChatFilterMode.NORMAL_WITH_BLOCK }
                        .background(
                            if (selectedMode == ChatFilterMode.NORMAL_WITH_BLOCK) PrimaryIndigo.copy(alpha = 0.15f)
                            else SurfaceDark.copy(alpha = 0.5f)
                        )
                        .border(
                            1.dp,
                            if (selectedMode == ChatFilterMode.NORMAL_WITH_BLOCK) PrimaryIndigo
                            else GlassBorderSubtle,
                            RoundedCornerShape(10.dp)
                        )
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selectedMode == ChatFilterMode.NORMAL_WITH_BLOCK,
                        onClick = { selectedMode = ChatFilterMode.NORMAL_WITH_BLOCK },
                        colors = RadioButtonDefaults.colors(selectedColor = PrimaryIndigo)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "🟢 Nghe thường (Có kèm chặn)",
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimaryDark,
                            fontSize = 13.5.sp
                        )
                        Text(
                            text = "Đọc mọi tin nhắn, TRỪ danh sách bị chặn bên dưới.",
                            color = TextSecondaryDark,
                            fontSize = 11.5.sp
                        )
                    }
                }

                // Option 2: STRICT_WHITELIST
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { selectedMode = ChatFilterMode.STRICT_WHITELIST }
                        .background(
                            if (selectedMode == ChatFilterMode.STRICT_WHITELIST) AccentEmerald.copy(alpha = 0.15f)
                            else SurfaceDark.copy(alpha = 0.5f)
                        )
                        .border(
                            1.dp,
                            if (selectedMode == ChatFilterMode.STRICT_WHITELIST) AccentEmerald
                            else GlassBorderSubtle,
                            RoundedCornerShape(10.dp)
                        )
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selectedMode == ChatFilterMode.STRICT_WHITELIST,
                        onClick = { selectedMode = ChatFilterMode.STRICT_WHITELIST },
                        colors = RadioButtonDefaults.colors(selectedColor = AccentEmerald)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "🎯 Chỉ định riêng (Whitelist)",
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimaryDark,
                            fontSize = 13.5.sp
                        )
                        Text(
                            text = "Chỉ nhận DUY NHẤT danh sách bên dưới, bỏ qua tất cả còn lại.",
                            color = TextSecondaryDark,
                            fontSize = 11.5.sp
                        )
                    }
                }

                HorizontalDivider(color = GlassBorderSubtle, thickness = 0.5.dp)

                val listTitle = if (selectedMode == ChatFilterMode.NORMAL_WITH_BLOCK) {
                    "DANH SÁCH ĐOẠN CHAT BỊ CHẶN (${chatNamesList.size}):"
                } else {
                    "DANH SÁCH ĐOẠN CHAT ĐƯỢC CHỈ ĐỊNH (${chatNamesList.size}):"
                }
                Text(
                    text = listTitle,
                    color = TextPrimaryDark,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )

                if (chatNamesList.isEmpty()) {
                    Text(
                        text = if (selectedMode == ChatFilterMode.NORMAL_WITH_BLOCK)
                            "Chưa có đoạn chat nào bị chặn. Đang nghe bình thường tất cả."
                        else "⚠️ Chú ý: Danh sách đang trống! Ứng dụng sẽ tạm ngưng ghi nhận mọi tin nhắn từ $appName cho đến khi bạn thêm ít nhất 1 đoạn chat.",
                        color = if (selectedMode == ChatFilterMode.NORMAL_WITH_BLOCK) TextMutedDark else AccentAmber,
                        fontSize = 11.5.sp,
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        chatNamesList.forEach { name ->
                            InputChip(
                                selected = false,
                                onClick = {},
                                label = { Text(text = name, fontSize = 12.sp, color = TextPrimaryDark) },
                                trailingIcon = {
                                    IconButton(
                                        onClick = { chatNamesList.remove(name) },
                                        modifier = Modifier.size(16.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Xóa",
                                            tint = AccentRose,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                },
                                colors = InputChipDefaults.inputChipColors(
                                    containerColor = SurfaceDark,
                                    labelColor = TextPrimaryDark
                                ),
                                border = BorderStroke(
                                    1.dp,
                                    if (selectedMode == ChatFilterMode.NORMAL_WITH_BLOCK) AccentRose.copy(alpha = 0.5f) else AccentEmerald.copy(alpha = 0.5f)
                                )
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newChatInput,
                        onValueChange = { newChatInput = it },
                        placeholder = { Text("Chính xác (vd: Chi) hoặc bao quát (vd: *Dự Án*)...", fontSize = 12.sp, color = TextMutedDark) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimaryDark,
                            unfocusedTextColor = TextPrimaryDark,
                            focusedBorderColor = PrimaryIndigo,
                            unfocusedBorderColor = GlassBorderSubtle,
                            focusedContainerColor = SurfaceDark,
                            unfocusedContainerColor = SurfaceDark
                        ),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.5.sp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val trimmed = newChatInput.trim()
                            if (trimmed.isNotBlank() && !chatNamesList.contains(trimmed)) {
                                chatNamesList.add(trimmed)
                                newChatInput = ""
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryIndigo),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("+ Thêm", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                Text(
                    text = "💡 Mẹo: Dùng dấu * ở 2 đầu (vd: *Dự Án*) để tìm bao quát bất kể icon. Gõ không có dấu * (vd: Chi) để chặn đích danh 1 người.",
                    color = TextMutedDark,
                    fontSize = 10.5.sp
                )
                }

                val unaddedRecents = recentSenders.filter { !chatNamesList.contains(it) }
                if (unaddedRecents.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "🕒 Vừa phát hiện gần đây từ $appName (chạm để thêm nhanh):",
                            color = TextSecondaryDark,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            unaddedRecents.take(8).forEach { recentName ->
                                SuggestionChip(
                                    onClick = { chatNamesList.add(recentName) },
                                    label = { Text("+ $recentName", fontSize = 11.5.sp, color = AccentCyan) },
                                    colors = SuggestionChipDefaults.suggestionChipColors(
                                        containerColor = Color(0xFF131B2A)
                                    ),
                                    border = BorderStroke(
                                        1.dp,
                                        AccentCyan.copy(alpha = 0.3f)
                                    )
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val updatedRule = AppFilterRule(
                        appPackage = packageName,
                        mode = selectedMode,
                        chatNames = chatNamesList.toSet()
                    )
                    onSaveRule(updatedRule)
                },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryIndigo),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Lưu Cấu Hình", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Hủy", color = TextSecondaryDark)
            }
        }
    )
}

@Composable
private fun BubbleConfigSection(
    isOverlayGranted: Boolean,
    onRequestOverlayPermission: () -> Unit,
    onRestartBubble: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "Bong bóng chat AItu hoạt động theo chu trình 3 trạng thái công thái học hoàn toàn tự động:",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondaryDark
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, CardBorder, RoundedCornerShape(12.dp)),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceVariantDark.copy(alpha = 0.35f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // State 1
                Row(verticalAlignment = Alignment.Top) {
                    Box(
                        modifier = Modifier
                            .background(PrimaryIndigo.copy(alpha = 0.25f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 7.dp, vertical = 3.dp)
                    ) {
                        Text("1", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AccentCyan)
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Hiện rõ thò ra (Sáng 100%)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimaryDark
                        )
                        Text(
                            text = "Nhô ra ngoài mép 8dp, sáng rõ. Sau 1.5s không thao tác sẽ tự chuyển sang Trạng thái 2.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondaryDark,
                            fontSize = 11.5.sp
                        )
                    }
                }

                HorizontalDivider(color = CardBorder.copy(alpha = 0.5f))

                // State 2
                Row(verticalAlignment = Alignment.Top) {
                    Box(
                        modifier = Modifier
                            .background(AccentEmerald.copy(alpha = 0.25f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 7.dp, vertical = 3.dp)
                    ) {
                        Text("2", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AccentEmerald)
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Mờ sâu & Thụt vào viền (Mờ 20%)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimaryDark
                        )
                        Text(
                            text = "Thụt sâu vào viền chỉ nhô 12dp, độ mờ sâu 20% không che mắt. Sau 4s không chạm sẽ tự động ẩn đi hoàn toàn.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondaryDark,
                            fontSize = 11.5.sp
                        )
                    }
                }

                HorizontalDivider(color = CardBorder.copy(alpha = 0.5f))

                // State 3
                Row(verticalAlignment = Alignment.Top) {
                    Box(
                        modifier = Modifier
                            .background(Color(0xFF27272A), RoundedCornerShape(6.dp))
                            .padding(horizontal = 7.dp, vertical = 3.dp)
                    ) {
                        Text("3", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Tự động ẩn hoàn toàn (Tàng hình 100%)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimaryDark
                        )
                        Text(
                            text = "Ẩn 100% không vướng màn hình. Chỉ cần Lắc nhẹ cổ tay 2 nhịp (Shake to Wake) để mở lại Trạng thái 2. Màn hình thông suốt 100%, không bị che phím nhắn tin hay nút chơi game.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondaryDark,
                            fontSize = 11.5.sp
                        )
                    }
                }
            }
        }

        val context = LocalContext.current
        val prefs = remember { UserPreferences.getInstance(context) }
        var isShakeEnabled by remember { mutableStateOf(prefs.isShakeToWakeEnabled()) }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfaceVariantDark.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                .border(1.dp, CardBorder, RoundedCornerShape(10.dp))
                .padding(10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Lắc tay gọi bóng chat (Shake to Wake)",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimaryDark
                )
                Text(
                    text = "Lắc nhẹ cổ tay 2 nhịp để gọi bóng khi đang ẩn. Giải phóng 100% cảm ứng mép cho Game và Bàn phím.",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondaryDark,
                    fontSize = 11.sp
                )
            }
            Switch(
                checked = isShakeEnabled,
                onCheckedChange = {
                    isShakeEnabled = it
                    prefs.setShakeToWakeEnabled(it)
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = BackgroundDark,
                    checkedTrackColor = PrimaryIndigo,
                    uncheckedTrackColor = SurfaceDark,
                    uncheckedBorderColor = CardBorder
                )
            )
        }

        Spacer(modifier = Modifier.height(2.dp))

        if (!isOverlayGranted) {
            Button(
                onClick = onRequestOverlayPermission,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentRose)
            ) {
                Text("⚠️ Cấp Quyền Cửa Sổ Nổi Để Dùng Bóng Chat", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
            }
        } else {
            OutlinedButton(
                onClick = onRestartBubble,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, PrimaryIndigo)
            ) {
                Text("🚀 Khởi Động / Mở Bong Bóng Chat Ngay", fontSize = 12.5.sp, color = AccentCyan, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun AppInfoSection() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, GlassBorder, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = GlassSurfaceDark)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header: App Logo, Name & Version
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(
                            Brush.linearGradient(listOf(Color(0xFF27272A), Color(0xFF09090B))),
                            RoundedCornerShape(12.dp)
                        )
                        .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_orca_logo),
                        contentDescription = "AItu Logo",
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Orca AItu",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimaryDark
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .background(AccentEmerald.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                                .border(1.dp, AccentEmerald.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "v2.4.0",
                                color = AccentEmerald,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Trợ Lý Tóm Tắt & Điều Phối Đa Mô Hình",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondaryDark,
                        fontSize = 11.sp
                    )
                }
            }

            HorizontalDivider(color = GlassBorderSubtle, thickness = 0.8.dp)

            // Technical Specs Grid
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AppInfoRow(
                    label = "Bản dựng (Build)",
                    value = "2026.11.01-Release"
                )
                AppInfoRow(
                    label = "Nhà phát triển",
                    value = "CS Orca"
                )
                AppInfoRow(
                    label = "Động cơ AI",
                    value = "Universal Multi-LLM (Gemini, Groq, OpenRouter)"
                )
                AppInfoRow(
                    label = "Kiến trúc hệ thống",
                    value = "Local-First • On-Device Room & WorkManager"
                )
                AppInfoRow(
                    label = "Bảo vệ riêng tư",
                    value = "PII Data Masking • 100% Cục bộ"
                )
            }

            HorizontalDivider(color = GlassBorderSubtle, thickness = 0.8.dp)

            // Status Badge & Footer
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(AccentEmerald, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Hệ thống hoạt động ổn định",
                        style = MaterialTheme.typography.labelSmall,
                        color = AccentEmerald,
                        fontWeight = FontWeight.Medium
                    )
                }

                Text(
                    text = "© 2026 Orca AItu • CS Orca",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMutedDark,
                    fontSize = 10.sp
                )
            }

            HorizontalDivider(color = GlassBorderSubtle, thickness = 0.8.dp)

            var showApiDebugDialog by remember { mutableStateOf(false) }

            OutlinedButton(
                onClick = { showApiDebugDialog = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentCyan),
                border = androidx.compose.foundation.BorderStroke(1.dp, PrimaryIndigoLight.copy(alpha = 0.6f))
            ) {
                Text(
                    text = "🛠️ Mở Bảng Debug API & Kiểm Tra Lỗi Hệ Thống",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = AccentCyan
                )
            }

            if (showApiDebugDialog) {
                ApiDebugConsoleDialog(
                    onDismiss = { showApiDebugDialog = false }
                )
            }
        }
    }
}

@Composable
private fun ApiDebugConsoleDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val logs by ApiDebugLogManager.logsFlow.collectAsState()
    val preferences = remember { UserPreferences.getInstance(context) }
    val keyPool by preferences.keyPoolFlow.collectAsState()

    var isRunningPingTest by remember { mutableStateOf(false) }
    var pingTestResult by remember { mutableStateOf<String?>(null) }
    var expandedLogId by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 680.dp),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🛠️", fontSize = 20.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Bảng Debug API Hệ Thống",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimaryDark
                        )
                        Text(
                            text = "Dành cho nhà phát triển kiểm tra lỗi & phản hồi thô",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondaryDark,
                            fontSize = 10.5.sp
                        )
                    }
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Đóng", tint = TextSecondaryDark)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Actions Toolbar: Ping test, Copy All, Clear
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            isRunningPingTest = true
                            pingTestResult = null
                            scope.launch {
                                try {
                                    val client = UniversalAiClient()
                                    val activeKeys = keyPool.filter { it.isEnabled && it.rawKey.isNotBlank() }
                                    if (activeKeys.isEmpty()) {
                                        pingTestResult = "⚠️ Chưa có API Key nào được kích hoạt trong cài đặt!"
                                    } else {
                                        val testRes = client.testSingleKey(activeKeys.first())
                                        pingTestResult = if (testRes.isSuccess) {
                                            "✓ Ping thành công [${testRes.config.provider.displayName} - ${testRes.config.modelName}]: ${testRes.latencyMs}ms\nPhản hồi: ${testRes.responsePreview}"
                                        } else {
                                            "✗ Ping thất bại [${testRes.config.provider.displayName} - ${testRes.config.modelName}]: ${testRes.latencyMs}ms\nLỗi: ${testRes.errorMessage}"
                                        }
                                    }
                                } catch (e: Throwable) {
                                    pingTestResult = "Lỗi khi chạy ping test: ${e.message}"
                                } finally {
                                    isRunningPingTest = false
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !isRunningPingTest,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PrimaryIndigo,
                            contentColor = BackgroundDark
                        )
                    ) {
                        if (isRunningPingTest) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), color = BackgroundDark, strokeWidth = 2.dp)
                        } else {
                            Text("⚡ Ping Test API", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = BackgroundDark)
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            val text = ApiDebugLogManager.exportFormattedText()
                            clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(text))
                            Toast.makeText(context, "Đã sao chép toàn bộ nhật ký debug vào Clipboard!", Toast.LENGTH_SHORT).show()
                        },
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, GlassBorder)
                    ) {
                        Text("📋 Sao chép", fontSize = 11.5.sp, color = TextPrimaryDark)
                    }

                    OutlinedButton(
                        onClick = {
                            ApiDebugLogManager.clear()
                            Toast.makeText(context, "Đã xóa sạch nhật ký debug!", Toast.LENGTH_SHORT).show()
                        },
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
                    ) {
                        Text("🗑️ Xóa", fontSize = 11.5.sp, color = AccentRose)
                    }
                }

                if (pingTestResult != null) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, if (pingTestResult!!.startsWith("✓")) AccentEmerald else AccentRose, RoundedCornerShape(8.dp)),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (pingTestResult!!.startsWith("✓")) AccentEmerald.copy(alpha = 0.12f) else AccentRose.copy(alpha = 0.12f)
                        )
                    ) {
                        Text(
                            text = pingTestResult!!,
                            modifier = Modifier.padding(10.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = TextPrimaryDark,
                            fontSize = 11.sp
                        )
                    }
                }

                // Summary Stats
                val totalCalls = logs.size
                val successCalls = logs.count { it.isSuccess }
                val failedCalls = totalCalls - successCalls
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SurfaceVariantDark.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Tổng gọi: $totalCalls", fontSize = 11.sp, color = TextSecondaryDark)
                    Text("Thành công: $successCalls", fontSize = 11.sp, color = AccentEmerald, fontWeight = FontWeight.Bold)
                    Text("Lỗi / Thất bại: $failedCalls", fontSize = 11.sp, color = if (failedCalls > 0) AccentRose else TextSecondaryDark, fontWeight = FontWeight.Bold)
                }

                // Logs List
                if (logs.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Chưa có lượt gọi API nào được ghi lại.\nHãy bấm '⚡ Ping Test API' ở trên để kiểm tra kết nối ngay!",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondaryDark,
                            fontSize = 11.5.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                } else {
                    logs.forEach { log ->
                        val isExpanded = expandedLogId == log.id
                        val badgeColor = when {
                            log.isSuccess -> AccentEmerald
                            log.httpCode == 429 -> AccentAmber
                            log.httpCode == 401 || log.httpCode == 403 -> AccentRose
                            else -> Color(0xFFEF4444)
                        }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, badgeColor.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                                .clickable { expandedLogId = if (isExpanded) null else log.id },
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = GlassSurfaceInner)
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .background(badgeColor.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                                .border(0.8.dp, badgeColor.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                                                .padding(horizontal = 5.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = log.httpCode?.let { "HTTP $it" } ?: if (log.isSuccess) "OK" else "LỖI",
                                                color = badgeColor,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "${log.provider} • ${log.model}",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = TextPrimaryDark,
                                            fontSize = 11.5.sp
                                        )
                                    }
                                    Text(
                                        text = "${log.durationMs}ms | ${log.formattedTime}",
                                        fontSize = 10.sp,
                                        color = TextSecondaryDark
                                    )
                                }

                                if (!log.isSuccess && !log.errorMessage.isNullOrBlank()) {
                                    Text(
                                        text = log.errorMessage,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = AccentRose,
                                        fontSize = 11.sp,
                                        maxLines = if (isExpanded) Int.MAX_VALUE else 2
                                    )
                                } else if (log.rawResponseSnippet.isNotBlank()) {
                                    Text(
                                        text = log.rawResponseSnippet,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondaryDark,
                                        fontSize = 10.5.sp,
                                        maxLines = if (isExpanded) Int.MAX_VALUE else 2
                                    )
                                }

                                if (isExpanded) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    HorizontalDivider(color = GlassBorderSubtle, thickness = 0.6.dp)
                                    Text(
                                        text = "Endpoint: ${log.endpoint}",
                                        fontSize = 10.sp,
                                        color = AccentCyan
                                    )
                                    if (log.promptPreview.isNotBlank()) {
                                        Text(
                                            text = "Prompt: ${log.promptPreview}",
                                            fontSize = 10.sp,
                                            color = TextSecondaryDark
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Đóng", color = AccentCyan, fontWeight = FontWeight.Bold)
            }
        },
        containerColor = SurfaceDark,
        shape = RoundedCornerShape(18.dp)
    )
}

@Composable
private fun AppInfoRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondaryDark,
            fontSize = 11.5.sp
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall,
            color = TextPrimaryDark,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.5.sp
        )
    }
}
