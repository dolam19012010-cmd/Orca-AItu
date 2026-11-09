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
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.orca.orcaauto.R
import com.orca.orcaauto.data.preferences.SummarySessionConfig
import com.orca.orcaauto.data.preferences.UserPreferences
import com.orca.orcaauto.data.remote.ApiKeyConfig
import com.orca.orcaauto.data.remote.ApiDebugLogManager
import com.orca.orcaauto.data.remote.KeyTestReport
import com.orca.orcaauto.data.remote.UniversalAiClient
import com.orca.orcaauto.domain.prompt.PromptPersona
import com.orca.orcaauto.service.FloatingBubbleService
import com.orca.orcaauto.service.OemSurvivalHelper
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
                if (isExpanded) {
                    if (iconBgColor == Color(0xFF27272A) || iconBgColor == SurfaceVariantDark || iconBgColor == BackgroundDark) AccentCyan.copy(alpha = 0.5f)
                    else iconBgColor.copy(alpha = 0.5f)
                } else GlassBorder,
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
                            tint = if (iconBgColor == PrimaryIndigo || iconBgColor == Color.White) BackgroundDark else iconTint,
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
                        tint = if (isExpanded) {
                            if (iconBgColor == Color(0xFF27272A) || iconBgColor == SurfaceVariantDark || iconBgColor == BackgroundDark) AccentCyan
                            else iconBgColor
                        } else TextMutedDark,
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
fun SettingsScreen(initialExpandedCategory: Int? = null) {
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
    val selectedSessionId by preferences.selectedSessionIdFlow.collectAsState()
    val currentSession = sessions.find { it.id == selectedSessionId } ?: sessions.firstOrNull() ?: SummarySessionConfig.createMainSession()

    var showCreateSessionDialog by remember { mutableStateOf(false) }
    var newSessionNameInput by remember { mutableStateOf("") }
    var newSessionEmojiInput by remember { mutableStateOf("\u26A1") }
    val allRecentSenders = remember(filterRules) {
        listOf(
            UserPreferences.PACKAGE_ZALO,
            UserPreferences.PACKAGE_TELEGRAM,
            UserPreferences.PACKAGE_MESSENGER
        ).flatMap { preferences.getRecentSenders(it) }.distinct()
    }
    var appForFilterDialog by remember { mutableStateOf<String?>(null) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
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
                        preferences.setWallpaperMode(WallpaperMode.CLEAR)
                        Toast.makeText(context, "\u0110\u00E3 \u00E1p d\u1EE5ng \u1EA3nh n\u1EC1n! (M\u1EB7c \u0111\u1ECBnh: Trong su\u1ED1t)", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Throwable) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "L\u1ED7i l\u01B0u \u1EA3nh: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
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

        // Top-level Session Switcher Bar (App-Cloning Độc Lập)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = GlassSurfaceDark),
            border = BorderStroke(1.dp, GlassBorder)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "\uD83C\uDFAD Phi\u00EAn C\u1EA5u H\u00ECnh",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimaryDark,
                            fontSize = 14.5.sp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .background(AccentCyan.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                                .border(0.5.dp, AccentCyan.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "App-Cloning",
                                color = AccentCyan,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Text(
                        text = "${sessions.size} phi\u00EAn",
                        fontSize = 11.5.sp,
                        color = TextSecondaryDark
                    )
                }

                Text(
                    text = "M\u1ED7i phi\u00EAn l\u00E0 m\u1ED9t b\u1EA3n c\u1EA5u h\u00ECnh ri\u00EAng bi\u1EC7t (App qu\u00E9t, Ca t\u00F3m t\u1EAFt, V\u0103n phong, B\u1ED1i c\u1EA3nh). Chuy\u1EC3n phi\u00EAn \u0111\u1EC3 xem v\u00E0 s\u1EEDa c\u00E0i \u0111\u1EB7t:",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondaryDark,
                    fontSize = 11.5.sp
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    sessions.forEach { session ->
                        val isSelected = session.id == currentSession.id
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (isSelected) PrimaryIndigo else SurfaceVariantDark.copy(alpha = 0.6f)
                                )
                                .border(
                                    1.dp,
                                    if (isSelected) AccentCyan else CardBorder,
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable {
                                    preferences.setSelectedSessionId(session.id)
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = session.iconEmoji.ifBlank { "\u26A1" },
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = session.name,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) BackgroundDark else TextPrimaryDark,
                                    maxLines = 1,
                                    softWrap = false
                                )
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = BackgroundDark,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(SurfaceVariantDark.copy(alpha = 0.3f))
                            .border(BorderStroke(1.dp, AccentCyan.copy(alpha = 0.6f)), RoundedCornerShape(10.dp))
                            .clickable {
                                newSessionNameInput = "Phi\u00EAn ${sessions.size + 1}"
                                newSessionEmojiInput = "\u26A1"
                                showCreateSessionDialog = true
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                tint = AccentCyan,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "T\u1EA1o Phi\u00EAn M\u1EDBi",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = AccentCyan,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }
        }

        var expandedSectionIndex by remember { mutableStateOf<Int?>(initialExpandedCategory) }

        androidx.compose.runtime.LaunchedEffect(initialExpandedCategory) {
            if (initialExpandedCategory != null) {
                expandedSectionIndex = initialExpandedCategory
            }
        }

        // Category 0: Ứng dụng quét thông báo
        SettingsCategoryCard(
            isExpanded = expandedSectionIndex == 0,
            onToggle = { expandedSectionIndex = if (expandedSectionIndex == 0) null else 0 },
            icon = Icons.Default.Notifications,
            iconBgColor = Color(0xFF3B82F6),
            title = "Ứng Dụng Quét Thông Báo",
            subtitle = "${currentSession.name}: Zalo, Telegram, Facebook Messenger",
            badgeText = "${listOf(zaloEnabled, telegramEnabled, messengerEnabled).count { it }}/3 Bật"
        ) {
            AppMonitoredSection(
                zaloEnabled = currentSession.zaloEnabled,
                telegramEnabled = currentSession.telegramEnabled,
                messengerEnabled = currentSession.messengerEnabled,
                filterRules = filterRules,
                onToggleZalo = {
                    val updated = currentSession.copy(zaloEnabled = it)
                    preferences.saveSession(updated)
                    if (currentSession.isMain) preferences.setZaloEnabled(it)
                },
                onToggleTelegram = {
                    val updated = currentSession.copy(telegramEnabled = it)
                    preferences.saveSession(updated)
                    if (currentSession.isMain) preferences.setTelegramEnabled(it)
                },
                onToggleMessenger = {
                    val updated = currentSession.copy(messengerEnabled = it)
                    preferences.saveSession(updated)
                    if (currentSession.isMain) preferences.setMessengerEnabled(it)
                },
                onOpenFilterDialog = { pkg -> appForFilterDialog = pkg }
            )
        }

        // Category 1: Khung giờ tóm tắt tự động
        SettingsCategoryCard(
            isExpanded = expandedSectionIndex == 1,
            onToggle = { expandedSectionIndex = if (expandedSectionIndex == 1) null else 1 },
            icon = Icons.Default.Schedule,
            iconBgColor = Color(0xFF10B981),
            title = "Khung Gi\u1EDD T\u00F3m T\u1EAFt T\u1EF1 \u0110\u1ED9ng",
            subtitle = "L\u00EAn l\u1ECBch AI qu\u00E9t th\u00F4ng b\u00E1o cho [${currentSession.name}]",
            badgeText = "${currentSession.scheduledHours.size} m\u1ED1c (${currentSession.name})"
        ) {
            ScheduledHoursSection(
                scheduledHours = currentSession.scheduledHours,
                onAddHourClick = {
                    newHourInput = ""
                    showAddHourDialog = true
                },
                onRemoveHour = { hourStr ->
                    val updatedHours = currentSession.scheduledHours.filterNot { it == hourStr }
                    val updated = currentSession.copy(scheduledHours = updatedHours)
                    preferences.saveSession(updated)
                    if (currentSession.isMain) {
                        preferences.removeScheduledHour(hourStr)
                        WorkScheduler.cancelScheduledHour(context, hourStr)
                    }
                    WorkScheduler.scheduleDailySummaries(context)
                    Toast.makeText(context, "\u0110\u00E3 x\u00F3a m\u1ED1c gi\u1EDD $hourStr c\u1EE7a [${currentSession.name}]", Toast.LENGTH_SHORT).show()
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

        // Category Quáº£n LÃ½ Äa PhiÃªn (App Cloning)
        SettingsCategoryCard(
            isExpanded = expandedSectionIndex == 99,
            onToggle = { expandedSectionIndex = if (expandedSectionIndex == 99) null else 99 },
            icon = Icons.Default.FilterAlt,
            iconBgColor = Color(0xFF8B5CF6),
            title = "Qu\u1EA3n L\u00FD \u0110a Phi\u00EAn (App Cloning)",
            subtitle = "Nh\u00E2n b\u1EA3n c\u1EA5u h\u00ECnh \u0111\u1ED9c l\u1EADp, B\u1ED9 l\u1ECDc ng\u01B0\u1EDDi g\u1EEDi & \u01AFu ti\u00EAn Key",
            badgeText = "${sessions.size} phi\u00EAn"
        ) {
            MultiSessionManagementSection(
                sessions = sessions,
                keyPool = keyPool,
                recentSenders = allRecentSenders,
                onSaveSession = { session ->
                    preferences.saveSession(session)
                    Toast.makeText(context, "\u0110\u00E3 l\u01B0u phi\u00EAn '${session.name}'!", Toast.LENGTH_SHORT).show()
                },
                onDeleteSession = { sessionId ->
                    scope.launch {
                        try {
                            com.orca.orcaauto.data.local.AppDatabase.getDatabase(context).summaryDao().deleteBySessionId(sessionId)
                        } catch (e: Exception) {
                            android.util.Log.w("SettingsScreen", "Lỗi xóa dữ liệu phiên: ${e.message}")
                        }
                    }
                    val deleted = preferences.deleteSession(sessionId)
                    if (deleted) {
                        Toast.makeText(context, "\u0110\u00E3 x\u00F3a phi\u00EAn th\u00E0nh c\u00F4ng!", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }

        // Category 3: Phong cách văn phong trợ lý AI
        val currentSessionPersona = PromptPersona.fromId(currentSession.promptPersonaId) ?: PromptPersona.GENTLE_ASSISTANT
        SettingsCategoryCard(
            isExpanded = expandedSectionIndex == 3,
            onToggle = { expandedSectionIndex = if (expandedSectionIndex == 3) null else 3 },
            icon = Icons.Default.Psychology,
            iconBgColor = Color(0xFF8B5CF6),
            title = "Phong C\u00E1ch V\u0103n Phong AI",
            subtitle = "Chu \u0111\u00E1o, Nghi\u00EAm t\u00FAc, T\u1ED1i gi\u1EA3n, H\u00F3m h\u1EC9nh...",
            badgeText = "${currentSessionPersona.title} (${currentSession.name})"
        ) {
            PersonaStyleSection(
                currentPersona = currentSessionPersona,
                onSelectPersona = { persona ->
                    val updated = currentSession.copy(promptPersonaId = persona.id)
                    preferences.saveSession(updated)
                    if (currentSession.isMain) preferences.setPromptPersona(persona)
                    Toast.makeText(context, "\u0110\u00E3 ch\u1ECDn: ${persona.title} cho [${currentSession.name}]", Toast.LENGTH_SHORT).show()
                }
            )
        }

        // Category 4: Trí nhớ ngữ cảnh AI & Bối cảnh
        SettingsCategoryCard(
            isExpanded = expandedSectionIndex == 4,
            onToggle = { expandedSectionIndex = if (expandedSectionIndex == 4) null else 4 },
            icon = Icons.Default.Visibility,
            iconBgColor = Color(0xFF06B6D4),
            title = "Tr\u00ED Nh\u1EDB Ng\u1EEF C\u1EA3nh AI",
            subtitle = "B\u1ED1i c\u1EA3nh ri\u00EAng [${currentSession.name}], ca g\u1EA7n nh\u1EA5t & vi\u1EC7c qu\u00E1 h\u1EA1n"
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "T\u00F9y ch\u1ECDn ng\u1EEF c\u1EA3nh truy\u1EC1n v\u00E0o m\u1ED7i l\u1EA7n AI ph\u00E2n t\u00EDch cho phi\u00EAn [${currentSession.name}].",
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
                            checkedTrackColor = PrimaryIndigo,
                            uncheckedThumbColor = TextMutedDark,
                            uncheckedTrackColor = SurfaceDark,
                            uncheckedBorderColor = CardBorder
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
                            checkedTrackColor = PrimaryIndigo,
                            uncheckedThumbColor = TextMutedDark,
                            uncheckedTrackColor = SurfaceDark,
                            uncheckedBorderColor = CardBorder
                        )
                    )
                }

                // Toggle 3: Auto-complete overdue tasks
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
                            text = "T\u1EF1 \u0111\u1ED9ng ho\u00E0n th\u00E0nh vi\u1EC7c qu\u00E1 h\u1EA1n",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimaryDark
                        )
                        Text(
                            text = "Vi\u1EC7c c\u00F3 h\u1EA1n tr\u01B0\u1EDBc hi\u1EC7n t\u1EA1i s\u1EBD t\u1EF1 \u0111\u01B0\u1EE3c x\u1EBFp v\u00E0o \u0110\u00C3 L\u00C0M (DONE)",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondaryDark,
                            fontSize = 11.sp
                        )
                    }
                    Switch(
                        checked = currentSession.autoCompleteOverdue,
                        onCheckedChange = { isChecked ->
                            val updated = currentSession.copy(autoCompleteOverdue = isChecked)
                            preferences.saveSession(updated)
                            Toast.makeText(context, if (isChecked) "\u0110\u00E3 b\u1EADt t\u1EF1 ho\u00E0n th\u00E0nh qu\u00E1 h\u1EA1n cho [${currentSession.name}]" else "\u0110\u00E3 t\u1EAFt t\u1EF1 ho\u00E0n th\u00E0nh qu\u00E1 h\u1EA1n cho [${currentSession.name}]", Toast.LENGTH_SHORT).show()
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = BackgroundDark,
                            checkedTrackColor = PrimaryIndigo,
                            uncheckedThumbColor = TextMutedDark,
                            uncheckedTrackColor = SurfaceDark,
                            uncheckedBorderColor = CardBorder
                        )
                    )
                }

                FixedContextSection(
                    customFixedContext = currentSession.customFixedContext,
                    onSaveContext = { newContext ->
                        val updated = currentSession.copy(customFixedContext = newContext)
                        preferences.saveSession(updated)
                        if (currentSession.isMain) {
                            preferences.setCustomFixedContext(newContext)
                        }
                        Toast.makeText(context, "\u0110\u00E3 l\u01B0u b\u1ED1i c\u1EA3nh cho [${currentSession.name}]!", Toast.LENGTH_SHORT).show()
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
                    preferences.setWallpaperMode(targetMode)
                    Toast.makeText(context, "\u0110\u00E3 chuy\u1EC3n sang ch\u1EBF \u0111\u1ED9: ${targetMode.title}", Toast.LENGTH_SHORT).show()
                }
            )
        }

        // Category 6: Bong bóng chat AI thông minh AItu
        SettingsCategoryCard(
            isExpanded = expandedSectionIndex == 6,
            onToggle = { expandedSectionIndex = if (expandedSectionIndex == 6) null else 6 },
            icon = Icons.Default.Visibility,
            iconBgColor = AccentCyan,
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
            iconBgColor = Color(0xFF38BDF8),
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
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimaryDark),
                    border = BorderStroke(1.dp, GlassBorder)
                ) {
                    Icon(imageVector = Icons.Default.Info, contentDescription = null, modifier = Modifier.size(16.dp), tint = AccentCyan)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("\uD83D\uDCD6 M\u1EDF C\u1EA9m Nang H\u01B0\u1EDBng D\u1EABn Chi Ti\u1EBFt", fontSize = 12.5.sp, color = TextPrimaryDark)
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
                        placeholder = { Text("08:00", color = TextMutedDark) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PrimaryIndigo,
                            unfocusedBorderColor = CardBorder,
                            focusedTextColor = TextPrimaryDark,
                            unfocusedTextColor = TextPrimaryDark,
                            focusedPlaceholderColor = TextMutedDark,
                            unfocusedPlaceholderColor = TextMutedDark,
                            cursorColor = AccentCyan
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val formatted = newHourInput.trim()
                        if (formatted.matches(Regex("^([01]?[0-9]|2[0-3]):[0-5][0-9]$"))) {
                            val normalized = if (formatted.length == 4) "0$formatted" else formatted
                            val updatedHours = (currentSession.scheduledHours + normalized).distinct().sorted()
                            val updated = currentSession.copy(scheduledHours = updatedHours)
                            preferences.saveSession(updated)
                            if (currentSession.isMain) {
                                preferences.addScheduledHour(normalized)
                            }
                            WorkScheduler.scheduleDailySummaries(context)
                            Toast.makeText(context, "\u0110\u00E3 th\u00EAm khung gi\u1EDD $normalized cho [${currentSession.name}]", Toast.LENGTH_SHORT).show()
                            showAddHourDialog = false
                        } else {
                            Toast.makeText(context, "\u0110\u1ECBnh d\u1EA1ng kh\u00F4ng h\u1EE3p l\u1EC7 (HH:mm)!", Toast.LENGTH_LONG).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrimaryIndigo,
                        contentColor = BackgroundDark
                    )
                ) {
                    Text("X\u00E1c Nh\u1EADn", color = BackgroundDark, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddHourDialog = false }) {
                    Text("H\u1EE7y", color = TextSecondaryDark)
                }
            },
            containerColor = SurfaceDark
        )
    }

    // Modal Dialog: Create New Cloned Session
    if (showCreateSessionDialog) {
        val emojis = listOf("\u26A1", "\uD83D\uDCBC", "\uD83D\uDCDA", "\uD83C\uDFE0", "\uD83C\uDFAF", "\uD83D\uDCAC", "\uD83E\uDD16", "\uD83D\uDD25")
        AlertDialog(
            onDismissRequest = { showCreateSessionDialog = false },
            title = {
                Text("T\u1EA1o Phi\u00EAn M\u1EDBi (App-Cloning)", fontWeight = FontWeight.Bold, color = TextPrimaryDark)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Phi\u00EAn m\u1EDBi l\u00E0 m\u1ED9t b\u1EA3n c\u1EA5u h\u00ECnh ho\u00E0n to\u00E0n \u0111\u1ED9c l\u1EADp: b\u1ED1i c\u1EA3nh ri\u00EAng, c\u00E0i \u0111\u1EB7t ri\u00EAng, kh\u00F4ng b\u1ECB tr\u00F9ng l\u1EB7p.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondaryDark
                    )

                    OutlinedTextField(
                        value = newSessionNameInput,
                        onValueChange = { newSessionNameInput = it },
                        singleLine = true,
                        label = { Text("T\u00EAn phi\u00EAn (v\u00ED d\u1EE5: C\u00F4ng Vi\u1EC7c, H\u1ECDc T\u1EADp)") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PrimaryIndigo,
                            unfocusedBorderColor = CardBorder,
                            focusedTextColor = TextPrimaryDark,
                            unfocusedTextColor = TextPrimaryDark,
                            focusedLabelColor = AccentCyan,
                            unfocusedLabelColor = TextSecondaryDark,
                            cursorColor = AccentCyan
                        )
                    )

                    Text(
                        text = "Bi\u1EC3u t\u01B0\u1EE3ng:",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondaryDark
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        emojis.forEach { emoji ->
                            val isSelected = newSessionEmojiInput == emoji
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) PrimaryIndigo else SurfaceVariantDark.copy(alpha = 0.5f))
                                    .border(1.dp, if (isSelected) AccentCyan else CardBorder, RoundedCornerShape(8.dp))
                                    .clickable { newSessionEmojiInput = emoji },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(emoji, fontSize = 16.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val name = newSessionNameInput.trim().ifBlank { "Phi\u00EAn ${sessions.size + 1}" }
                        val newSession = SummarySessionConfig(
                            id = java.util.UUID.randomUUID().toString(),
                            name = name,
                            iconEmoji = newSessionEmojiInput.trim().ifBlank { "\u26A1" },
                            isMain = false,
                            zaloEnabled = false,
                            telegramEnabled = false,
                            messengerEnabled = false,
                            scheduledHours = listOf("12:00", "18:00"),
                            customFixedContext = "",
                            promptPersonaId = "gentle_assistant",
                            autoCompleteOverdue = false
                        )
                        preferences.saveSession(newSession)
                        preferences.setSelectedSessionId(newSession.id)
                        showCreateSessionDialog = false
                        Toast.makeText(context, "\u0110\u00E3 t\u1EA1o v\u00E0 chuy\u1EC3n sang [${newSession.name}]!", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrimaryIndigo,
                        contentColor = BackgroundDark
                    )
                ) {
                    Text("T\u1EA1o & K\u00EDch Ho\u1EA1t", color = BackgroundDark, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateSessionDialog = false }) {
                    Text("H\u1EE7y", color = TextSecondaryDark)
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
                                Toast.makeText(context, "\u0110\u00E3 d\u00E1n t\u1EEB b\u1ED9 nh\u1EDB t\u1EA1m!", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "B\u1ED9 nh\u1EDB t\u1EA1m \u0111ang tr\u1ED1ng!", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentCyan),
                        border = BorderStroke(1.dp, AccentCyan.copy(alpha = 0.6f))
                    ) {
                        Text("\uD83D\uDCCB D\u00E1n T\u1EEB Clipboard", fontSize = 12.sp, color = AccentCyan, fontWeight = FontWeight.Bold)
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
                            unfocusedTextColor = TextPrimaryDark,
                            focusedPlaceholderColor = TextMutedDark,
                            unfocusedPlaceholderColor = TextMutedDark,
                            cursorColor = AccentCyan
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
                placeholder = { Text("AIzaSy... hoặc gsk_... hoặc sk-...", color = TextMutedDark) },
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
                    unfocusedTextColor = TextPrimaryDark,
                    focusedLabelColor = AccentCyan,
                    unfocusedLabelColor = TextSecondaryDark,
                    focusedPlaceholderColor = TextMutedDark,
                    unfocusedPlaceholderColor = TextMutedDark,
                    cursorColor = AccentCyan
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
                            onDismissRequest = { isModelMenuExpanded = false },
                            modifier = Modifier
                                .background(SurfaceDark)
                                .border(1.dp, CardBorder, RoundedCornerShape(8.dp))
                        ) {
                            detectedConfig.provider.supportedModels.forEach { modelOption ->
                                DropdownMenuItem(
                                    text = { Text(modelOption, color = TextPrimaryDark) },
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
                    Text("Th\u00EAm Key", color = BackgroundDark, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = onTestAllKeys,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    enabled = !isCheckingKeys,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentCyan),
                    border = BorderStroke(1.dp, AccentCyan.copy(alpha = 0.6f))
                ) {
                    if (isCheckingKeys) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = AccentCyan)
                    } else {
                        Icon(imageVector = Icons.AutoMirrored.Filled.Send, contentDescription = null, tint = AccentCyan)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Ki\u1EC3m Tra T\u1EA5t C\u1EA3", color = AccentCyan, fontWeight = FontWeight.Bold)
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
                                        tint = BackgroundDark,
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
                    unfocusedTextColor = TextPrimaryDark,
                    focusedPlaceholderColor = TextMutedDark,
                    unfocusedPlaceholderColor = TextMutedDark,
                    cursorColor = AccentCyan
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
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentCyan),
                border = BorderStroke(1.dp, AccentCyan.copy(alpha = 0.6f))
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp), tint = AccentCyan)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Th\u00EAm", fontSize = 12.sp, color = AccentCyan, fontWeight = FontWeight.Bold, maxLines = 1)
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
                                modifier = Modifier.height(32.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentRose),
                                border = BorderStroke(1.dp, AccentRose.copy(alpha = 0.6f))
                            ) {
                                Text("G\u1EE1 B\u1ECF", fontSize = 11.5.sp, color = AccentRose, fontWeight = FontWeight.Bold)
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
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimaryDark),
            border = BorderStroke(1.dp, GlassBorder)
        ) {
            Text("\uD83D\uDE80 C\u00E0i \u0110\u1EB7t T\u1EF1 Kh\u1EDFi Ch\u1EA1y C\u00F9ng M\u00E1y (Autostart)", fontSize = 12.sp, color = TextPrimaryDark)
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
                        modifier = Modifier.height(28.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentCyan),
                        border = BorderStroke(1.dp, AccentCyan.copy(alpha = 0.6f))
                    ) {
                        Text("T\u00F9y Ch\u1EC9nh Kh\u00E1c", fontSize = 11.sp, color = AccentCyan)
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
                            color = if (isSelected) BackgroundDark else TextPrimaryDark,
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
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimaryDark),
                    border = BorderStroke(1.dp, CardBorder)
                ) {
                    Text("Ph\u1EE5c H\u1ED3i (Restore)", fontSize = 12.sp, color = TextPrimaryDark)
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
                            unfocusedTextColor = TextPrimaryDark,
                            focusedLabelColor = AccentCyan,
                            unfocusedLabelColor = TextSecondaryDark,
                            cursorColor = AccentCyan
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
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentCyan),
                border = BorderStroke(1.dp, AccentCyan.copy(alpha = 0.6f))
            ) {
                Text("C\u1EA5p quy\u1EC1n", fontSize = 10.sp, color = AccentCyan)
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
                    checkedTrackColor = PrimaryIndigo,
                    uncheckedThumbColor = TextMutedDark,
                    uncheckedTrackColor = SurfaceDark,
                    uncheckedBorderColor = CardBorder
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
                    placeholder = { Text("Ví dụ: gemini-2.5-flash", color = TextMutedDark) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryIndigo,
                        unfocusedBorderColor = CardBorder,
                        focusedTextColor = TextPrimaryDark,
                        unfocusedTextColor = TextPrimaryDark,
                        focusedPlaceholderColor = TextMutedDark,
                        unfocusedPlaceholderColor = TextMutedDark,
                        cursorColor = AccentCyan
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
                                color = if (isSelected) BackgroundDark else TextPrimaryDark,
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
                uncheckedThumbColor = TextMutedDark,
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
                        colors = RadioButtonDefaults.colors(selectedColor = PrimaryIndigo, unselectedColor = TextMutedDark)
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
                        colors = RadioButtonDefaults.colors(selectedColor = AccentEmerald, unselectedColor = TextMutedDark)
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
                            unfocusedContainerColor = SurfaceDark,
                            focusedPlaceholderColor = TextMutedDark,
                            unfocusedPlaceholderColor = TextMutedDark,
                            cursorColor = AccentCyan
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
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PrimaryIndigo,
                            contentColor = BackgroundDark
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("+ Th\u00EAm", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = BackgroundDark)
                    }
                }

                Text(
                    text = "💡 Mẹo: Dùng dấu * ở 2 đầu (vd: *Dự Án*) để tìm bao quát bất kể icon. Gõ không có dấu * (vd: Chi) để chặn đích danh 1 người.",
                    color = TextMutedDark,
                    fontSize = 10.5.sp
                )

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
                                        containerColor = Color(0xFF131B2A),
                                        labelColor = AccentCyan
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
                colors = ButtonDefaults.buttonColors(
                    containerColor = PrimaryIndigo,
                    contentColor = BackgroundDark
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("L\u01B0u C\u1EA5u H\u00ECnh", fontWeight = FontWeight.Bold, color = BackgroundDark)
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
                    uncheckedThumbColor = TextMutedDark,
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
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentCyan),
                border = BorderStroke(1.dp, AccentCyan.copy(alpha = 0.6f))
            ) {
                Text("\uD83D\uDE80 Kh\u1EDFi \u0110\u1ED9ng / M\u1EDF Bong B\u00F3ng Chat Ngay", fontSize = 12.5.sp, color = AccentCyan, fontWeight = FontWeight.SemiBold)
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
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
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
                        modifier = Modifier.weight(1.3f),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
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
                            Text("⚡ Ping Test", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = BackgroundDark, maxLines = 1, softWrap = false)
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            val text = ApiDebugLogManager.exportFormattedText()
                            clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(text))
                            Toast.makeText(context, "Đã sao chép toàn bộ nhật ký debug vào Clipboard!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, GlassBorder)
                    ) {
                        Text("📋 Chép", fontSize = 11.sp, color = TextPrimaryDark, maxLines = 1, softWrap = false)
                    }

                    OutlinedButton(
                        onClick = {
                            ApiDebugLogManager.clear()
                            Toast.makeText(context, "Đã xóa sạch nhật ký debug!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(0.8f),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
                    ) {
                        Text("🗑️ Xóa", fontSize = 11.sp, color = AccentRose, maxLines = 1, softWrap = false)
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
                                    Row(
                                        modifier = Modifier.weight(1f, fill = false),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
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
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "${log.provider} • ${log.model}",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = TextPrimaryDark,
                                            fontSize = 11.5.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "${log.durationMs}ms | ${log.formattedTime}",
                                        fontSize = 10.sp,
                                        color = TextSecondaryDark,
                                        maxLines = 1,
                                        softWrap = false
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
