package com.orca.orcaauto.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PendingActions
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.orca.orcaauto.domain.voice.VoiceBriefingManager
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.orca.orcaauto.data.local.AppDatabase
import com.orca.orcaauto.data.local.DailyMemoryEntity
import com.orca.orcaauto.data.local.NotificationEntity
import com.orca.orcaauto.data.local.SmartAlarmEntity
import com.orca.orcaauto.data.local.SmartAlarmStatus
import com.orca.orcaauto.data.local.SummaryHistoryEntity
import com.orca.orcaauto.data.preferences.UserPreferences
import com.orca.orcaauto.domain.alarm.AlarmCascadeMode
import com.orca.orcaauto.domain.alarm.SystemAlarmHelper
import com.orca.orcaauto.domain.filter.SummaryParser
import com.orca.orcaauto.domain.filter.TaskItem
import com.orca.orcaauto.domain.filter.TaskStatus
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import com.orca.orcaauto.ui.theme.AccentCyan
import com.orca.orcaauto.ui.theme.AccentEmerald
import com.orca.orcaauto.ui.theme.AccentRose
import com.orca.orcaauto.ui.theme.BackgroundDark
import com.orca.orcaauto.ui.theme.CardBorder
import com.orca.orcaauto.ui.theme.GlassBorder
import com.orca.orcaauto.ui.theme.GlassBorderSubtle
import com.orca.orcaauto.ui.theme.GlassSurfaceCard
import com.orca.orcaauto.ui.theme.GlassSurfaceDark
import com.orca.orcaauto.ui.theme.GlassSurfaceInner
import com.orca.orcaauto.ui.theme.PrimaryIndigo
import com.orca.orcaauto.ui.theme.SurfaceDark
import com.orca.orcaauto.ui.theme.SurfaceVariantDark
import com.orca.orcaauto.ui.theme.TextMutedDark
import com.orca.orcaauto.ui.theme.TextPrimaryDark
import com.orca.orcaauto.ui.theme.TextSecondaryDark

@Composable
fun HistoryScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val database = remember { AppDatabase.getDatabase(context) }
    val preferences = remember { com.orca.orcaauto.data.preferences.UserPreferences.getInstance(context) }
    val wallpaperPath by preferences.wallpaperPathFlow.collectAsState()
    val voiceManager = remember { VoiceBriefingManager.getInstance(context) }
    var selectedTab by remember { mutableIntStateOf(0) }

    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose {
            voiceManager.stop()
        }
    }

    val shiftSummaries by database.summaryDao().getAllSummariesFlow().collectAsState(initial = emptyList())
    val recentNotifications by database.notificationDao().getRecentNotificationsFlow().collectAsState(initial = emptyList())
    val dailyMemories by database.dailyMemoryDao().getAllDailyMemoriesFlow().collectAsState(initial = emptyList())
    val smartAlarms by database.smartAlarmDao().getAllAlarmsFlow().collectAsState(initial = emptyList())
    val allAlarmMap = remember(smartAlarms) {
        smartAlarms.associateBy { it.alarmId }
    }

    val sessions by preferences.sessionsFlow.collectAsState()
    val selectedSessionId by preferences.selectedSessionIdFlow.collectAsState()
    val displayedSummaries = remember(shiftSummaries, selectedSessionId) {
        shiftSummaries.filter { item ->
            if (selectedSessionId == com.orca.orcaauto.data.preferences.SummarySessionConfig.MAIN_SESSION_ID) {
                item.sessionId == com.orca.orcaauto.data.preferences.SummarySessionConfig.MAIN_SESSION_ID || item.sessionId.isBlank()
            } else {
                item.sessionId == selectedSessionId
            }
        }
    }

    var showClearConfirmDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .padding(16.dp)
    ) {
        // Title Header: Clean & Modern
        Text(
            text = "Trí nhớ AI",
            style = MaterialTheme.typography.titleLarge,
            color = TextPrimaryDark,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Báo cáo ca & Nhiệm vụ tự động",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondaryDark
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Switch Tabs: Shift Summaries vs Raw Notifications (Only 2 clean tabs!)
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = GlassSurfaceDark,
            contentColor = AccentCyan,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                    color = PrimaryIndigo
                )
            },
            modifier = Modifier.border(1.dp, GlassBorder, RoundedCornerShape(12.dp))
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Tóm Tắt Ca (${shiftSummaries.size})", fontWeight = FontWeight.Medium, fontSize = 13.sp) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Tin Đã Ghi (${recentNotifications.size})", fontWeight = FontWeight.Medium, fontSize = 13.sp) }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Horizontal Session Tabs Bar
        if (selectedTab == 0 && sessions.isNotEmpty()) {
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(sessions, key = { it.id }) { session ->
                    val isSelected = session.id == selectedSessionId
                    Surface(
                        onClick = { preferences.setSelectedSessionId(session.id) },
                        shape = RoundedCornerShape(16.dp),
                        color = if (isSelected) Color(0xFF2563EB) else GlassSurfaceDark,
                        border = BorderStroke(1.dp, if (isSelected) Color(0xFF60A5FA) else GlassBorderSubtle),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(text = session.iconEmoji, fontSize = 12.sp)
                            Text(
                                text = session.name,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) Color.White else Color(0xFFCBD5E1)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        AnimatedContent(
            targetState = selectedTab,
            transitionSpec = {
                fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(180))
            },
            label = "HistoryTabTransition"
        ) { currentTab ->
            when (currentTab) {
                0 -> {
                    // Tab 1: Shift Summaries with 2-Part Section View
                    if (displayedSummaries.isEmpty()) {
                        val activeSession = sessions.find { it.id == selectedSessionId }
                        val emptyTitle = if (activeSession != null && !activeSession.isMain) {
                            "Chưa có tóm tắt cho phiên '${activeSession.name}'"
                        } else {
                            "Chưa có bản tóm tắt ca nào"
                        }
                        val emptyDesc = if (activeSession != null && !activeSession.isMain) {
                            "Các tin nhắn thuộc Whitelist của phiên này sẽ được hiển thị tại đây khi có tóm tắt mới."
                        } else {
                            "Báo cáo ca sẽ tự động gửi ra màn hình khóa vào 12:00 & 18:00\nhoặc khi bạn bấm 'Tóm tắt ngay' ở trang chủ."
                        }
                        EmptyStateView(
                            title = emptyTitle,
                            desc = emptyDesc
                        )
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            contentPadding = PaddingValues(bottom = 80.dp)
                        ) {
                            items(displayedSummaries, key = { it.id }) { item ->
                                TwoPartSummaryCard(
                                    item = item,
                                    allAlarmMap = allAlarmMap,
                                    onCopy = { copyToClipboard(context, item.summaryContent) }
                                )
                            }
                        }
                    }
                }
                else -> {
                    // Tab 2: Raw Logged Notifications (Last 24h, bounded capacity 200, 100 unprocessed)
                    if (recentNotifications.isEmpty()) {
                        EmptyStateView(
                            title = "Chưa có thông báo nào được ghi lại",
                            desc = "Hệ thống sẽ tự động lưu lại các tin nhắn từ Zalo, Telegram, Messenger tại đây khi có thông báo mới."
                        )
                    } else {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Sub-header bar with count and clear button
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(SurfaceDark, RoundedCornerShape(10.dp))
                                    .border(1.dp, CardBorder, RoundedCornerShape(10.dp))
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "Đang lưu trữ ${recentNotifications.size} tin gần nhất",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = TextPrimaryDark
                                    )
                                    Text(
                                        text = "Lưu trữ tối đa 200 tin trong 24h • Tối đa 100 tin chưa đọc",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextMutedDark,
                                        fontSize = 11.sp
                                    )
                                }

                                OutlinedButton(
                                    onClick = { showClearConfirmDialog = true },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentRose),
                                    border = BorderStroke(1.dp, AccentRose.copy(alpha = 0.5f))
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "X\u00F3a",
                                        tint = AccentRose,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("X\u00F3a s\u1EA1ch", color = AccentRose, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                contentPadding = PaddingValues(bottom = 80.dp)
                            ) {
                                items(recentNotifications, key = { it.id }) { notification ->
                                    RawNotificationCard(
                                        notification = notification,
                                        onCopy = { copyToClipboard(context, notification.content) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showClearConfirmDialog) {
        var deleteCountdown by remember { mutableIntStateOf(3) }
        var confirmChecked by remember { mutableStateOf(false) }

        androidx.compose.runtime.LaunchedEffect(showClearConfirmDialog) {
            deleteCountdown = 3
            confirmChecked = false
            while (deleteCountdown > 0) {
                kotlinx.coroutines.delay(1000L)
                deleteCountdown--
            }
        }

        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            title = { Text("Xác nhận xóa sạch tin nhắn?", fontWeight = FontWeight.Bold, color = TextPrimaryDark) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Thao tác này sẽ xóa vĩnh viễn toàn bộ tin nhắn đã ghi nhận khỏi bộ nhớ tạm. Các bản tóm tắt ca đã tạo sẽ vẫn được giữ nguyên an toàn.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondaryDark
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { confirmChecked = !confirmChecked }
                            .padding(vertical = 4.dp)
                    ) {
                        androidx.compose.material3.Checkbox(
                            checked = confirmChecked,
                            onCheckedChange = { confirmChecked = it },
                            colors = androidx.compose.material3.CheckboxDefaults.colors(
                                checkedColor = AccentRose,
                                uncheckedColor = TextMutedDark
                            )
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Tôi chắc chắn muốn xóa toàn bộ tin nhắn",
                            color = TextPrimaryDark,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = deleteCountdown == 0 && confirmChecked,
                    onClick = {
                        scope.launch {
                            database.notificationDao().clearAllNotifications()
                            Toast.makeText(context, "Đã xóa sạch bộ nhớ tạm tin nhắn!", Toast.LENGTH_SHORT).show()
                            showClearConfirmDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentRose,
                        disabledContainerColor = SurfaceVariantDark
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    val btnText = if (deleteCountdown > 0) "Chờ (${deleteCountdown}s)..." else "Xác nhận xóa ngay"
                    Text(
                        btnText,
                        color = if (deleteCountdown == 0 && confirmChecked) Color.White else TextMutedDark,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmDialog = false }) {
                    Text("H\u1EE7y", color = TextSecondaryDark)
                }
            },
            containerColor = SurfaceDark
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TwoPartSummaryCard(
    item: SummaryHistoryEntity,
    allAlarmMap: Map<String, SmartAlarmEntity>,
    onCopy: () -> Unit
) {
    val context = LocalContext.current
    val parsed = remember(item.summaryContent) { SummaryParser.parse(item.summaryContent) }
    var showRawText by remember { mutableStateOf(false) }
    var selectedTaskForAlarm by remember { mutableStateOf<TaskItem?>(null) }
    var taskToConfirmCancel by remember { mutableStateOf<Pair<TaskItem, String>?>(null) }
    var taskToConfirmRestore by remember { mutableStateOf<Pair<TaskItem, String>?>(null) }
    var taskToConfirmUncomplete by remember { mutableStateOf<Pair<TaskItem, String>?>(null) }

    val voiceManager = remember { VoiceBriefingManager.getInstance(context) }
    val isSpeaking by voiceManager.isSpeakingFlow.collectAsState()
    val currentSpeakingId by voiceManager.currentSummaryIdFlow.collectAsState()
    val isThisCardSpeaking = isSpeaking && currentSpeakingId == item.id
    val haptic = LocalHapticFeedback.current

    fun getAlarmInfoForTask(task: TaskItem): Pair<String, SmartAlarmEntity?> {
        val parsedDt = SystemAlarmHelper.parseDateTimeFromText(task.deadline)
        val taskCal = parsedDt?.calendar ?: Calendar.getInstance().apply { add(Calendar.HOUR_OF_DAY, 1) }
        val generatedAlarmId = SystemAlarmHelper.generateAlarmId(task.title, taskCal)
        val matchingAlarm = allAlarmMap[generatedAlarmId] ?: SystemAlarmHelper.findMatchingAlarm(task.title, allAlarmMap)
        val finalAlarmId = matchingAlarm?.alarmId ?: generatedAlarmId
        return Pair(finalAlarmId, matchingAlarm)
    }

    fun handleQuickAlarmToggle(task: TaskItem, alarmId: String, existingAlarm: SmartAlarmEntity?) {
        val isCurrentlyActive = existingAlarm != null && existingAlarm.status == SmartAlarmStatus.ACTIVE
        if (isCurrentlyActive) {
            SystemAlarmHelper.cancelSmartAlarms(context, task.title, alarmId)
            Toast.makeText(context, "Đã tắt chuông báo: ${task.title.take(20)}...", Toast.LENGTH_SHORT).show()
        } else {
            val parsedDt = SystemAlarmHelper.parseDateTimeFromText(task.deadline)
            val taskCal = parsedDt?.calendar ?: Calendar.getInstance().apply { add(Calendar.HOUR_OF_DAY, 1) }
            val result = SystemAlarmHelper.scheduleSmartAlarms(
                context = context,
                taskTitle = task.title,
                targetCalendar = taskCal,
                mode = AlarmCascadeMode.X1
            )
            result.onSuccess {
                val timeText = parsedDt?.timeLabel ?: String.format(Locale.getDefault(), "%02d:%02d", taskCal.get(Calendar.HOUR_OF_DAY), taskCal.get(Calendar.MINUTE))
                Toast.makeText(context, "Đã đặt chuông báo $timeText: ${task.title.take(20)}...", Toast.LENGTH_SHORT).show()
            }.onFailure { err ->
                Toast.makeText(context, "Không thể đặt báo thức: ${err.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, GlassBorder, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = GlassSurfaceDark)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Card Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .background(PrimaryIndigo.copy(alpha = 0.2f), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = AccentCyan,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = item.targetTimeSlot,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimaryDark
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Message,
                                contentDescription = null,
                                tint = TextMutedDark,
                                modifier = Modifier.size(11.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Phân tích ${item.messageCount} tin nhắn • ${parsed.totalTaskCount} việc",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondaryDark
                            )
                        }
                    }
                }

                Row {
                    IconButton(onClick = onCopy) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Sao chép",
                            tint = AccentCyan
                        )
                    }
                }
            }

            if (showRawText) {
                // Raw View
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(GlassSurfaceInner, RoundedCornerShape(10.dp))
                        .border(1.dp, GlassBorderSubtle, RoundedCornerShape(10.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        text = parsed.rawCleanText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimaryDark,
                        lineHeight = 20.sp
                    )
                }
            } else {
                // ================= PHẦN 1: GÓC NHÌN PHẢN BIỆN & LỜI KHUYÊN (Nhấn giữ để nghe giọng đọc) =================
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = 1.dp,
                            color = if (isThisCardSpeaking) AccentCyan else PrimaryIndigo.copy(alpha = 0.35f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .combinedClickable(
                            onClick = {
                                if (isThisCardSpeaking) {
                                    voiceManager.stop()
                                }
                            },
                            onLongClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                voiceManager.speakSummary(item.id, parsed)
                            }
                        ),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isThisCardSpeaking) GlassSurfaceInner else GlassSurfaceCard
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Lightbulb,
                                    contentDescription = null,
                                    tint = AccentCyan,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "💡 Lời khuyên & Lưu ý",
                                    color = AccentCyan,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            if (isThisCardSpeaking) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .background(AccentCyan.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                        contentDescription = "Đang đọc",
                                        tint = AccentCyan,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Đang đọc (Chạm dừng)",
                                        color = AccentCyan,
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            } else {
                                Text(
                                    text = "Nhấn giữ để nghe 🎧",
                                    color = TextMutedDark,
                                    fontSize = 10.5.sp
                                )
                            }
                        }

                        if (parsed.conciseAdvice.isNotBlank()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(PrimaryIndigo.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                                    .border(1.dp, PrimaryIndigo.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                                    .padding(8.dp)
                            ) {
                                Text(
                                    text = parsed.conciseAdvice,
                                    color = TextPrimaryDark,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        if (parsed.criticalQuestions.isNotEmpty()) {
                            parsed.criticalQuestions.forEach { question ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                                        contentDescription = null,
                                        tint = AccentRose,
                                        modifier = Modifier.size(13.dp).padding(top = 2.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = question,
                                        color = TextPrimaryDark,
                                        style = MaterialTheme.typography.bodySmall,
                                        lineHeight = 17.sp
                                    )
                                }
                            }
                        }
                    }
                }

                // ================= PHẦN 2: BẢNG NHIỆM VỤ =================
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, GlassBorderSubtle, RoundedCornerShape(12.dp)),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = GlassSurfaceCard)
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.PendingActions,
                                contentDescription = null,
                                tint = AccentEmerald,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "📋 Nhiệm vụ & Kế hoạch",
                                color = AccentEmerald,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Unified Task List: Color-coded (Red = To-Do, Yellow = Planned, Green = Done), zero redundant section headers!
                        parsed.tasksToDo.forEach { task ->
                            val (alarmId, alarmEntity) = getAlarmInfoForTask(task)
                            SwipeableTaskItemRow(
                                task = task,
                                badgeColor = AccentRose,
                                alarmEntity = alarmEntity,
                                onQuickAlarmToggle = { handleQuickAlarmToggle(task, alarmId, alarmEntity) },
                                onOpenAlarmDetail = { selectedTaskForAlarm = task },
                                onComplete = {
                                    SystemAlarmHelper.markTaskCompleted(context, task.title, alarmId)
                                    Toast.makeText(context, "✓ Đã hoàn thành: ${task.title.take(20)}...", Toast.LENGTH_SHORT).show()
                                },
                                onRequestCancel = {
                                    taskToConfirmCancel = Pair(task, alarmId)
                                },
                                onRequestRestore = {
                                    taskToConfirmRestore = Pair(task, alarmId)
                                },
                                onRequestUncomplete = {
                                    taskToConfirmUncomplete = Pair(task, alarmId)
                                }
                            )
                        }

                        parsed.tasksPlanned.forEach { task ->
                            val (alarmId, alarmEntity) = getAlarmInfoForTask(task)
                            SwipeableTaskItemRow(
                                task = task,
                                badgeColor = Color(0xFFF59E0B),
                                alarmEntity = alarmEntity,
                                onQuickAlarmToggle = { handleQuickAlarmToggle(task, alarmId, alarmEntity) },
                                onOpenAlarmDetail = { selectedTaskForAlarm = task },
                                onComplete = {
                                    SystemAlarmHelper.markTaskCompleted(context, task.title, alarmId)
                                    Toast.makeText(context, "✓ Đã hoàn thành: ${task.title.take(20)}...", Toast.LENGTH_SHORT).show()
                                },
                                onRequestCancel = {
                                    taskToConfirmCancel = Pair(task, alarmId)
                                },
                                onRequestRestore = {
                                    taskToConfirmRestore = Pair(task, alarmId)
                                },
                                onRequestUncomplete = {
                                    taskToConfirmUncomplete = Pair(task, alarmId)
                                }
                            )
                        }

                        parsed.tasksDone.forEach { task ->
                            val (alarmId, alarmEntity) = getAlarmInfoForTask(task)
                            SwipeableTaskItemRow(
                                task = task,
                                badgeColor = AccentEmerald,
                                alarmEntity = alarmEntity,
                                onQuickAlarmToggle = { handleQuickAlarmToggle(task, alarmId, alarmEntity) },
                                onOpenAlarmDetail = { selectedTaskForAlarm = task },
                                onComplete = {
                                    SystemAlarmHelper.markTaskCompleted(context, task.title, alarmId)
                                    Toast.makeText(context, "✓ Đã hoàn thành: ${task.title.take(20)}...", Toast.LENGTH_SHORT).show()
                                },
                                onRequestCancel = {
                                    taskToConfirmCancel = Pair(task, alarmId)
                                },
                                onRequestRestore = {
                                    taskToConfirmRestore = Pair(task, alarmId)
                                },
                                onRequestUncomplete = {
                                    taskToConfirmUncomplete = Pair(task, alarmId)
                                }
                            )
                        }
                    }
                }
            }

            // Toggle raw markdown view
            Text(
                text = if (showRawText) "← Quay lại dạng bảng phân tích" else "Xem văn bản gốc đầy đủ →",
                color = AccentCyan,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.clickable { showRawText = !showRawText }
            )
        }
    }

    if (selectedTaskForAlarm != null) {
        val task = selectedTaskForAlarm!!
        val (alarmId, existingAlarm) = getAlarmInfoForTask(task)
        val stateText = existingAlarm?.let { "✓ ${it.targetDateLabel}" }
        TaskAlarmConfigDialog(
            task = task,
            currentAlarmState = stateText,
            currentAlarmId = alarmId,
            onDismiss = { selectedTaskForAlarm = null },
            onAlarmSet = { _ ->
                selectedTaskForAlarm = null
            },
            onAlarmCancelled = {
                selectedTaskForAlarm = null
            }
        )
    }

    if (taskToConfirmCancel != null) {
        val (cancellingTask, cancellingAlarmId) = taskToConfirmCancel!!
        AlertDialog(
            onDismissRequest = { taskToConfirmCancel = null },
            title = {
                Text("Hủy nhiệm vụ này?", fontWeight = FontWeight.Bold, color = TextPrimaryDark)
            },
            text = {
                Text(
                    "Nhiệm vụ \"${cancellingTask.title}\" sẽ được hủy và AI sẽ ghi nhớ để không đưa lại vào các ca sau.",
                    color = TextSecondaryDark,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        SystemAlarmHelper.markTaskCancelled(context, cancellingTask.title, cancellingAlarmId)
                        Toast.makeText(context, "Đã hủy nhiệm vụ. AI sẽ không đề xuất lại.", Toast.LENGTH_SHORT).show()
                        taskToConfirmCancel = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentRose)
                ) {
                    Text("Xác nhận hủy", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { taskToConfirmCancel = null }) {
                    Text("Giữ lại", color = TextSecondaryDark)
                }
            },
            containerColor = SurfaceDark
        )
    }

    if (taskToConfirmRestore != null) {
        val (restoringTask, restoringAlarmId) = taskToConfirmRestore!!
        AlertDialog(
            onDismissRequest = { taskToConfirmRestore = null },
            title = {
                Text("Khôi phục nhiệm vụ?", fontWeight = FontWeight.Bold, color = TextPrimaryDark)
            },
            text = {
                Text(
                    "Bạn có muốn khôi phục lại nhiệm vụ \"${restoringTask.title}\" về danh sách việc cần làm không?",
                    color = TextSecondaryDark,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        SystemAlarmHelper.restoreCancelledTask(context, restoringTask.title, restoringAlarmId)
                        Toast.makeText(context, "Đã khôi phục nhiệm vụ thành công!", Toast.LENGTH_SHORT).show()
                        taskToConfirmRestore = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentEmerald)
                ) {
                    Text("Khôi phục", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { taskToConfirmRestore = null }) {
                    Text("Hủy bỏ", color = TextSecondaryDark)
                }
            },
            containerColor = SurfaceDark
        )
    }

    if (taskToConfirmUncomplete != null) {
        val (uncompletingTask, uncompletingAlarmId) = taskToConfirmUncomplete!!
        AlertDialog(
            onDismissRequest = { taskToConfirmUncomplete = null },
            title = {
                Text("Đưa về chưa hoàn thành?", fontWeight = FontWeight.Bold, color = TextPrimaryDark)
            },
            text = {
                Text(
                    "Bạn có muốn đưa nhiệm vụ \"${uncompletingTask.title}\" về lại danh sách việc cần làm (chưa hoàn thành) để tiếp tục theo dõi không?",
                    color = TextSecondaryDark,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        SystemAlarmHelper.uncompleteTask(context, uncompletingTask.title, uncompletingAlarmId)
                        Toast.makeText(context, "Đã đưa về trạng thái chưa hoàn thành!", Toast.LENGTH_SHORT).show()
                        taskToConfirmUncomplete = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrimaryIndigo,
                        contentColor = BackgroundDark
                    )
                ) {
                    Text("Xác nhận", color = BackgroundDark, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { taskToConfirmUncomplete = null }) {
                    Text("Hủy bỏ", color = TextSecondaryDark)
                }
            },
            containerColor = SurfaceDark
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun SwipeableTaskItemRow(
    task: TaskItem,
    badgeColor: Color,
    alarmEntity: SmartAlarmEntity?,
    onQuickAlarmToggle: () -> Unit,
    onOpenAlarmDetail: () -> Unit,
    onComplete: () -> Unit,
    onRequestCancel: () -> Unit,
    onRequestRestore: () -> Unit = {},
    onRequestUncomplete: () -> Unit = {}
) {
    val isCompleted = alarmEntity?.status == SmartAlarmStatus.COMPLETED || task.status == TaskStatus.DONE
    val isCancelled = alarmEntity?.status == SmartAlarmStatus.CANCELLED
    val isAlarmActive = alarmEntity?.status == SmartAlarmStatus.ACTIVE
    val context = LocalContext.current

    val parsedDt = remember(task.deadline) { SystemAlarmHelper.parseDateTimeFromText(task.deadline) }
    val proposedTimeLabel = parsedDt?.timeLabel ?: "Hẹn giờ"

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { dismissValue ->
            when (dismissValue) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    onComplete()
                    false
                }
                SwipeToDismissBoxValue.EndToStart -> {
                    onRequestCancel()
                    false
                }
                SwipeToDismissBoxValue.Settled -> false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = !isCompleted && !isCancelled,
        enableDismissFromEndToStart = !isCancelled,
        backgroundContent = {
            val direction = dismissState.dismissDirection
            val color = when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> AccentEmerald.copy(alpha = 0.85f)
                SwipeToDismissBoxValue.EndToStart -> AccentRose.copy(alpha = 0.85f)
                else -> Color.Transparent
            }
            val alignment = when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
                SwipeToDismissBoxValue.EndToStart -> Alignment.CenterEnd
                else -> Alignment.Center
            }
            val icon = when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> Icons.Default.CheckCircle
                SwipeToDismissBoxValue.EndToStart -> Icons.Default.Delete
                else -> null
            }
            val text = when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> "Hoàn thành ✓"
                SwipeToDismissBoxValue.EndToStart -> "Hủy việc ✕"
                else -> ""
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(color, RoundedCornerShape(10.dp))
                    .padding(horizontal = 16.dp),
                contentAlignment = alignment
            ) {
                if (icon != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(GlassSurfaceDark, RoundedCornerShape(10.dp))
                .border(
                    1.dp,
                    if (isCompleted) AccentEmerald.copy(alpha = 0.5f) else if (isCancelled) AccentRose.copy(alpha = 0.35f) else badgeColor.copy(alpha = 0.45f),
                    RoundedCornerShape(10.dp)
                )
                .combinedClickable(
                    onClick = {
                        if (!isCompleted && !isCancelled) {
                            onQuickAlarmToggle()
                        } else if (isCancelled) {
                            Toast.makeText(context, "Nhấn giữ để khôi phục nhiệm vụ đã hủy", Toast.LENGTH_SHORT).show()
                        } else if (isCompleted) {
                            Toast.makeText(context, "Nhấn giữ để đưa về trạng thái chưa hoàn thành", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onLongClick = {
                        if (!isCompleted && !isCancelled) {
                            onOpenAlarmDetail()
                        } else if (isCancelled) {
                            onRequestRestore()
                        } else if (isCompleted) {
                            onRequestUncomplete()
                        }
                    }
                )
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Task Title Row (Pure text, zero bullet dots, color expressed via border & badge tags)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isCompleted) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Đã xong",
                        tint = AccentEmerald,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text(
                    text = task.title,
                    color = if (isCompleted || isCancelled) TextMutedDark else TextPrimaryDark,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    textDecoration = if (isCompleted || isCancelled) androidx.compose.ui.text.style.TextDecoration.LineThrough else null,
                    modifier = Modifier.weight(1f)
                )
                if (isCancelled) {
                    Box(
                        modifier = Modifier
                            .background(AccentRose.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text("Đã hủy", color = AccentRose, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // FlowRow for Badges & Quick Alarm Pill: Always wraps gracefully, zero squished layouts!
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (task.deadline.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .background(badgeColor.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 7.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "⏰ ${task.deadline}",
                            color = badgeColor,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp
                        )
                    }
                }

                if (task.source.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 7.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "💬 ${task.source}",
                            color = TextSecondaryDark,
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 11.sp
                        )
                    }
                }

                // Quick Alarm Pill Button
                if (!isCompleted && !isCancelled) {
                    val pillBg = if (isAlarmActive) AccentEmerald.copy(alpha = 0.18f) else AccentCyan.copy(alpha = 0.12f)
                    val pillBorder = if (isAlarmActive) AccentEmerald.copy(alpha = 0.6f) else AccentCyan.copy(alpha = 0.35f)
                    val pillColor = if (isAlarmActive) AccentEmerald else AccentCyan

                    Box(
                        modifier = Modifier
                            .background(pillBg, RoundedCornerShape(6.dp))
                            .border(1.dp, pillBorder, RoundedCornerShape(6.dp))
                            .clickable { onQuickAlarmToggle() }
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isAlarmActive) Icons.Default.CheckCircle else Icons.Default.Alarm,
                                contentDescription = null,
                                tint = pillColor,
                                modifier = Modifier.size(11.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isAlarmActive) (alarmEntity?.targetDateLabel?.let { "✓ $it" } ?: "✓ Đã đặt $proposedTimeLabel") else "🔔 Đặt $proposedTimeLabel",
                                color = pillColor,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TaskAlarmConfigDialog(
    task: TaskItem,
    currentAlarmState: String? = null,
    currentAlarmId: String? = null,
    onDismiss: () -> Unit,
    onAlarmSet: (String) -> Unit,
    onAlarmCancelled: () -> Unit = {}
) {
    val context = LocalContext.current
    val prefs = remember { UserPreferences.getInstance(context) }
    val cleanTitle = remember(task.title) {
        task.title.replace(Regex("""^[\[\(\-•*\d.]+\s*"""), "").trim()
    }
    var taskNote by remember { mutableStateOf(prefs.getTaskNote(cleanTitle).orEmpty()) }
    var isAlarmEnabled by remember {
        mutableStateOf(currentAlarmState != null || task.deadline.isNotBlank())
    }

    val parsedDateTime = remember(task.deadline) {
        SystemAlarmHelper.parseDateTimeFromText(task.deadline)
    }

    var hour by remember { mutableIntStateOf(parsedDateTime?.calendar?.get(Calendar.HOUR_OF_DAY) ?: 9) }
    var minute by remember { mutableIntStateOf(parsedDateTime?.calendar?.get(Calendar.MINUTE) ?: 0) }
    var cascadeMode by remember { mutableStateOf(AlarmCascadeMode.X1) }
    var cascadeIntervalMinutes by remember { mutableIntStateOf(15) }

    val targetCalendar = remember(hour, minute, parsedDateTime) {
        val cal = (parsedDateTime?.calendar?.clone() as? Calendar) ?: Calendar.getInstance().apply {
            add(Calendar.HOUR_OF_DAY, 1)
        }
        cal.set(Calendar.HOUR_OF_DAY, hour)
        cal.set(Calendar.MINUTE, minute)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        cal
    }

    fun adjustMinutes(delta: Int) {
        val total = (hour * 60 + minute + delta + 1440) % 1440
        hour = total / 60
        minute = total % 60
    }

    val previewSchedules = remember(task.title, targetCalendar, cascadeMode, cascadeIntervalMinutes) {
        SystemAlarmHelper.calculateAlarmSchedules(task.title, targetCalendar, cascadeMode, cascadeIntervalMinutes)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceDark,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = null,
                    tint = AccentCyan,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Ghi Chú & Báo Thức Nhiệm Vụ",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimaryDark
                    )
                    Text(
                        text = task.title.take(35) + if (task.title.length > 35) "..." else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondaryDark,
                        maxLines = 1
                    )
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
                // Section 1: Ghi chú cho phiên sau
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "📝 GHI CHÚ / LỜI DẶN CHO PHIÊN SAU",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = AccentCyan
                    )
                    OutlinedTextField(
                        value = taskNote,
                        onValueChange = { taskNote = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = {
                            Text(
                                "Nhập lời dặn AI cho các ca tiếp theo (tùy chọn)...",
                                fontSize = 12.sp,
                                color = TextMutedDark
                            )
                        },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = TextPrimaryDark, fontSize = 13.sp),
                        maxLines = 3,
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimaryDark,
                            unfocusedTextColor = TextPrimaryDark,
                            cursorColor = AccentCyan,
                            focusedBorderColor = AccentCyan,
                            unfocusedBorderColor = CardBorder,
                            focusedPlaceholderColor = TextMutedDark,
                            unfocusedPlaceholderColor = TextMutedDark,
                            focusedContainerColor = SurfaceVariantDark.copy(alpha = 0.5f),
                            unfocusedContainerColor = SurfaceVariantDark.copy(alpha = 0.3f)
                        )
                    )
                    Text(
                        text = "💡 Lời dặn này sẽ được nạp trực tiếp vào AI trong các phiên tóm tắt kế tiếp để AI ưu tiên xử lý.",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMutedDark,
                        fontSize = 10.sp
                    )
                }

                // Section 2: Checkbox Báo Thức
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SurfaceVariantDark.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                        .clickable { isAlarmEnabled = !isAlarmEnabled }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = isAlarmEnabled,
                        onCheckedChange = { isAlarmEnabled = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = AccentCyan,
                            checkmarkColor = BackgroundDark,
                            uncheckedColor = TextMutedDark
                        )
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Text(
                            text = "Đặt báo thức nhắc nhở",
                            fontWeight = FontWeight.Bold,
                            color = if (isAlarmEnabled) TextPrimaryDark else TextSecondaryDark,
                            fontSize = 13.sp
                        )
                        Text(
                            text = if (isAlarmEnabled) "Bật chuông báo hệ thống khi đến hạn" else "Không đặt chuông báo cho việc này",
                            color = TextMutedDark,
                            fontSize = 10.5.sp
                        )
                    }
                }

                // If alarm is enabled: show alarm controls
                AnimatedVisibility(visible = isAlarmEnabled) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        // Large Time Display & Date Label
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(SurfaceVariantDark, RoundedCornerShape(12.dp))
                                .padding(vertical = 12.dp, horizontal = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = String.format(Locale.getDefault(), "%02d:%02d", hour, minute),
                                    fontSize = 36.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = AccentCyan
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "📅 ${parsedDateTime?.dateLabel ?: "Hôm nay"} • ${if (task.deadline.isNotBlank()) task.deadline else "Gợi ý tự động"}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (parsedDateTime?.isFarFuture == true) Color(0xFFF59E0B) else TextMutedDark,
                                    fontWeight = if (parsedDateTime?.isFarFuture == true) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }

                        // Far future tip
                        if (parsedDateTime?.isFarFuture == true) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF451A03).copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                    .border(1.dp, Color(0xFFB45309), RoundedCornerShape(8.dp))
                                    .padding(8.dp)
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = "💡 Lịch hẹn tương lai (> 24 giờ):",
                                        color = Color(0xFFFBBF24),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                    Text(
                                        text = "Hệ thống sẽ dùng AItu Exact Alarm để đánh thức và đổ chuông chính xác vào đúng ngày ${parsedDateTime.dateLabel}.",
                                        color = TextPrimaryDark,
                                        fontSize = 10.5.sp
                                    )
                                    OutlinedButton(
                                        onClick = {
                                            SystemAlarmHelper.addToGoogleCalendar(context, task.title, targetCalendar)
                                        },
                                        shape = RoundedCornerShape(6.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        modifier = Modifier.height(28.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentCyan),
                                        border = BorderStroke(1.dp, AccentCyan.copy(alpha = 0.6f))
                                    ) {
                                        Text("\uD83D\uDCC5 M\u1EDF th\u00EAm v\u00E0o L\u1ECBch Google / M\u00E1y", fontSize = 10.5.sp, color = AccentCyan)
                                    }
                                }
                            }
                        }

                        // Quick delta adjustment chips
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            listOf(-15, -5, 5, 15).forEach { delta ->
                                OutlinedButton(
                                    onClick = { adjustMinutes(delta) },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.height(32.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimaryDark),
                                    border = BorderStroke(1.dp, CardBorder)
                                ) {
                                    Text(
                                        text = if (delta > 0) "+${delta}p" else "${delta}p",
                                        fontSize = 11.sp,
                                        color = TextPrimaryDark,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        // Cascade mode selection (x1, x2, x3)
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "CHẾ ĐỘ CHUÔNG BÁO LIÊN TIẾP",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = AccentEmerald
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                AlarmCascadeMode.entries.forEach { mode ->
                                    val isSelected = (mode == cascadeMode)
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .border(
                                                width = if (isSelected) 1.5.dp else 1.dp,
                                                color = if (isSelected) AccentCyan else CardBorder,
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                            .background(
                                                color = if (isSelected) PrimaryIndigo.copy(alpha = 0.25f) else SurfaceVariantDark.copy(alpha = 0.4f),
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                            .clickable { cascadeMode = mode }
                                            .padding(vertical = 8.dp, horizontal = 4.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(
                                                text = mode.displayName,
                                                color = if (isSelected) AccentCyan else TextPrimaryDark,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp
                                            )
                                            Text(
                                                text = mode.subtitle,
                                                color = if (isSelected) TextPrimaryDark else TextMutedDark,
                                                fontSize = 9.sp,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            }

                            // Interval spacing adjustment row: Only shown for x2 and x3
                            if (cascadeMode != AlarmCascadeMode.X1) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Khoảng cách giữa các lượt chuông:",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TextSecondaryDark,
                                            fontSize = 11.sp
                                        )
                                        Text(
                                            text = "${cascadeIntervalMinutes} phút",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = AccentCyan,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        listOf(5, 10, 15, 20, 30).forEach { interval ->
                                            val isSelected = (cascadeIntervalMinutes == interval)
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .border(
                                                        width = if (isSelected) 1.5.dp else 1.dp,
                                                        color = if (isSelected) AccentCyan else CardBorder,
                                                        shape = RoundedCornerShape(8.dp)
                                                    )
                                                    .background(
                                                        color = if (isSelected) PrimaryIndigo.copy(alpha = 0.25f) else SurfaceVariantDark.copy(alpha = 0.4f),
                                                        shape = RoundedCornerShape(8.dp)
                                                    )
                                                    .clickable { cascadeIntervalMinutes = interval }
                                                    .padding(vertical = 6.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = "${interval}p",
                                                    color = if (isSelected) AccentCyan else TextPrimaryDark,
                                                    fontSize = 11.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Live Preview
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.Black.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                                .padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Danh sách mốc chuông sẽ kích hoạt (${parsedDateTime?.dateLabel ?: "Hôm nay"}):",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMutedDark,
                                fontSize = 10.sp
                            )
                            previewSchedules.forEach { s ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "🔔 ${s.formattedTime}",
                                        color = if (s.isPrimary) AccentRose else AccentCyan,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                    Text(
                                        text = "• ${s.label}",
                                        color = TextSecondaryDark,
                                        fontSize = 10.sp,
                                        maxLines = 1
                                    )
                                }
                            }
                        }

                        // If alarm already placed: show instant Delete/Cancel button
                        if (currentAlarmState != null) {
                            Button(
                                onClick = {
                                    SystemAlarmHelper.cancelSmartAlarms(context, task.title, currentAlarmId)
                                    isAlarmEnabled = false
                                    onAlarmCancelled()
                                    Toast.makeText(context, "Đã hủy chuông báo cho: ${task.title.take(20)}...", Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = AccentRose.copy(alpha = 0.15f)),
                                border = androidx.compose.foundation.BorderStroke(1.dp, AccentRose.copy(alpha = 0.6f)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth().height(36.dp)
                            ) {
                                Text("🗑 Hủy bỏ chuông báo này ngay lập tức", color = AccentRose, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    // 1. Save or clear task note
                    prefs.setTaskNote(cleanTitle, taskNote)

                    // 2. Handle alarm scheduling or cancellation
                    if (isAlarmEnabled) {
                        if (currentAlarmState != null && !currentAlarmId.isNullOrBlank()) {
                            SystemAlarmHelper.cancelSmartAlarms(context, task.title, currentAlarmId)
                        }
                        val result = SystemAlarmHelper.scheduleSmartAlarms(
                            context = context,
                            taskTitle = task.title,
                            targetCalendar = targetCalendar,
                            mode = cascadeMode,
                            intervalMinutes = cascadeIntervalMinutes
                        )
                        result.onSuccess { schedules ->
                            val summaryText = String.format(Locale.getDefault(), "%02d:%02d (%s)", hour, minute, cascadeMode.code)
                            val fullSummary = if (parsedDateTime?.dateLabel != null && parsedDateTime.dateLabel != "Hôm nay") {
                                "$summaryText ${parsedDateTime.dateLabel}"
                            } else summaryText

                            val noteMsg = if (taskNote.isNotBlank()) " và lưu ghi chú" else ""
                            Toast.makeText(
                                context,
                                "Đã lên lịch ${schedules.size} báo thức$noteMsg (${parsedDateTime?.dateLabel ?: "Hôm nay"})!",
                                Toast.LENGTH_LONG
                            ).show()
                            onAlarmSet(fullSummary)
                        }.onFailure { err ->
                            Toast.makeText(
                                context,
                                "Không thể đặt báo thức: ${err.message}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    } else {
                        // Alarm unchecked
                        if (currentAlarmState != null) {
                            SystemAlarmHelper.cancelSmartAlarms(context, task.title, currentAlarmId)
                            onAlarmCancelled()
                        }
                        val toastMsg = if (taskNote.isNotBlank()) "Đã lưu ghi chú cho phiên sau!" else "Đã lưu thay đổi!"
                        Toast.makeText(context, toastMsg, Toast.LENGTH_SHORT).show()
                        onDismiss()
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = PrimaryIndigo,
                    contentColor = BackgroundDark
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = BackgroundDark
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Lưu thiết lập",
                    fontWeight = FontWeight.Bold,
                    color = BackgroundDark
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("\u0110\u00F3ng", color = TextSecondaryDark)
            }
        }
    )
}

@Composable
fun RawNotificationCard(
    notification: NotificationEntity,
    onCopy: () -> Unit
) {
    val timeFormatted = remember(notification.timestamp) {
        val sdf = SimpleDateFormat("HH:mm • dd/MM", Locale.getDefault())
        sdf.format(Date(notification.timestamp))
    }

    var isExpanded by remember { mutableStateOf(false) }
    val isLongContent = notification.content.length > 100

    val (appTitle, appColor) = remember(notification.appPackage) {
        when {
            notification.appPackage.equals("com.orca.orcaauto", ignoreCase = true) -> "Orca AItu" to Color.White
            notification.appPackage.contains("zalo", ignoreCase = true) -> "Zalo" to Color(0xFF0068FF)
            notification.appPackage.contains("telegram", ignoreCase = true) -> "Telegram" to Color(0xFF229ED9)
            notification.appPackage.contains("orca", ignoreCase = true) || notification.appPackage.contains("messenger", ignoreCase = true) -> "Messenger" to Color(0xFF0084FF)
            else -> notification.appName to AccentCyan
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, GlassBorder, RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = GlassSurfaceDark)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header Row: App Badge, Sender Name, Status Pill & Timestamp
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    // App tag
                    Box(
                        modifier = Modifier
                            .background(appColor.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                            .border(1.dp, appColor.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = appTitle,
                            color = appColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = notification.sender,
                        color = TextPrimaryDark,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Status Pill
                    Box(
                        modifier = Modifier
                            .background(
                                if (notification.isProcessed) AccentEmerald.copy(alpha = 0.15f) else AccentCyan.copy(alpha = 0.15f),
                                RoundedCornerShape(6.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (notification.isProcessed) "Đã tóm tắt" else "Chờ tóm tắt",
                            color = if (notification.isProcessed) AccentEmerald else AccentCyan,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    Text(
                        text = timeFormatted,
                        color = TextMutedDark,
                        fontSize = 10.sp
                    )

                    IconButton(
                        onClick = onCopy,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Sao chép",
                            tint = TextMutedDark,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            // Message Content Box with smooth expansion animation
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(GlassSurfaceInner, RoundedCornerShape(8.dp))
                    .border(1.dp, GlassBorderSubtle, RoundedCornerShape(8.dp))
                    .animateContentSize()
                    .clickable(enabled = isLongContent) { isExpanded = !isExpanded }
                    .padding(10.dp)
            ) {
                Column {
                    Text(
                        text = notification.content,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondaryDark,
                        lineHeight = 18.sp,
                        maxLines = if (isExpanded || !isLongContent) Int.MAX_VALUE else 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (isLongContent) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (isExpanded) "Thu gọn ▲" else "Xem thêm ▼",
                            color = AccentCyan,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DailyMemoryCard(
    memory: DailyMemoryEntity,
    onCopy: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, AccentEmerald.copy(alpha = 0.5f), RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(AccentEmerald.copy(alpha = 0.2f), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bookmark,
                            contentDescription = null,
                            tint = AccentEmerald,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Bản Nhớ Ngày: ${memory.dateString}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = AccentEmerald
                        )
                        Text(
                            text = "Tổng hợp từ ${memory.shiftCount} ca (${memory.totalMessagesProcessed} tin) • Lưu vĩnh viễn",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondaryDark
                        )
                    }
                }

                IconButton(onClick = onCopy) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Sao chép",
                        tint = AccentEmerald
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceVariantDark.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                    .padding(12.dp)
            ) {
                Text(
                    text = memory.condensedMemory,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimaryDark,
                    lineHeight = 20.sp
                )
            }
        }
    }
}

@Composable
private fun EmptyStateView(title: String, desc: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 60.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(SurfaceDark, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = null,
                    tint = TextMutedDark,
                    modifier = Modifier.size(36.dp)
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimaryDark
            )
            Text(
                text = desc,
                style = MaterialTheme.typography.bodyMedium,
                color = TextMutedDark,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("AItu Summary", text)
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, "Đã sao chép nội dung vào bộ nhớ tạm!", Toast.LENGTH_SHORT).show()
}
