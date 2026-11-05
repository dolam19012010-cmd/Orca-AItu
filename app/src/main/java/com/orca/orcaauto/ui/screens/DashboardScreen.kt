package com.orca.orcaauto.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.orca.orcaauto.R
import com.orca.orcaauto.data.local.AppDatabase
import com.orca.orcaauto.data.preferences.UserPreferences
import com.orca.orcaauto.service.OemSurvivalHelper
import com.orca.orcaauto.ui.theme.AccentCyan
import com.orca.orcaauto.ui.theme.AccentEmerald
import com.orca.orcaauto.ui.theme.AccentRose
import com.orca.orcaauto.ui.theme.BackgroundDark
import com.orca.orcaauto.ui.theme.CardBorder
import com.orca.orcaauto.ui.theme.GlassBorder
import com.orca.orcaauto.ui.theme.GlassSurfaceDark
import com.orca.orcaauto.ui.theme.GlassSurfaceInner
import com.orca.orcaauto.ui.theme.PrimaryIndigo
import com.orca.orcaauto.ui.theme.SurfaceDark
import com.orca.orcaauto.ui.theme.SurfaceVariantDark
import com.orca.orcaauto.ui.theme.TextMutedDark
import com.orca.orcaauto.ui.theme.TextPrimaryDark
import com.orca.orcaauto.ui.theme.TextSecondaryDark
import com.orca.orcaauto.worker.WorkScheduler
import kotlinx.coroutines.launch

@Composable
fun DashboardScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val database = remember { AppDatabase.getDatabase(context) }
    val preferences = remember { UserPreferences.getInstance(context) }

    var isTestingAi by remember { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current
    var isNotificationAccessGranted by remember { mutableStateOf(OemSurvivalHelper.isNotificationAccessGranted(context)) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isNotificationAccessGranted = OemSurvivalHelper.isNotificationAccessGranted(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val scheduledHours by preferences.scheduledHoursFlow.collectAsState()
    val wallpaperPath by preferences.wallpaperPathFlow.collectAsState()

    // Reactive Flow for unprocessed notifications (Zero polling)
    val unprocessedCount by remember(database) {
        try {
            database.notificationDao().getUnprocessedCountFlow()
        } catch (e: Throwable) {
            kotlinx.coroutines.flow.flowOf(0)
        }
    }.collectAsState(initial = 0)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // App Header
        DashboardHeader()

        // Quick Command Actions
        ExecutiveQuickActionsCard(
            isTestingAi = isTestingAi,
            onTestAiClick = {
                val activeKeys = preferences.getKeyPool().filter { it.isEnabled && it.rawKey.isNotBlank() }
                if (activeKeys.isEmpty()) {
                    Toast.makeText(context, "Vui lòng thêm ít nhất 1 API Key trong tab Cấu hình trước!", Toast.LENGTH_LONG).show()
                    return@ExecutiveQuickActionsCard
                }
                isTestingAi = true
                WorkScheduler.triggerImmediateSummary(context)
                Toast.makeText(context, "Đã gửi lệnh tóm tắt AI. Đang xử lý ngầm qua WorkManager...", Toast.LENGTH_SHORT).show()
                scope.launch {
                    kotlinx.coroutines.delay(2000)
                    isTestingAi = false
                }
            },
            onOpenBubbleClick = {
                scope.launch {
                    val selectedId = preferences.getSelectedSessionId()
                    val sessionRecord = database.summaryDao().getLatestSummaryForSession(selectedId)
                    val activeSession = preferences.getSelectedSession()
                    com.orca.orcaauto.service.FloatingBubbleService.show(
                        context = context,
                        summaryContent = sessionRecord?.summaryContent.orEmpty(),
                        modelInfo = sessionRecord?.modelInfo ?: "${activeSession.iconEmoji} ${activeSession.name}",
                        sessionId = selectedId
                    )
                    Toast.makeText(context, "Đã mở bong bóng chat (${activeSession.name})!", Toast.LENGTH_SHORT).show()
                }
            }
        )

        // Unified System Status Cockpit
        SystemStatusCockpitCard(
            isNotificationGranted = isNotificationAccessGranted,
            onOpenNotificationSettings = {
                try {
                    context.startActivity(OemSurvivalHelper.getNotificationAccessIntent(context))
                } catch (e: Exception) {
                    Toast.makeText(context, "Không thể mở cài đặt thông báo: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            },
            unprocessedCount = unprocessedCount,
            scheduledHours = scheduledHours
        )
    }
}

@Composable
private fun DashboardHeader() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFF27272A), Color(0xFF09090B))
                    ),
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
        Column {
            Text(
                text = "AItu Bảng Điều Khiển",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimaryDark,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Trạng thái vận hành & Điều phối thông báo",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondaryDark
            )
        }
    }
}

@Composable
private fun ExecutiveQuickActionsCard(
    isTestingAi: Boolean,
    onTestAiClick: () -> Unit,
    onOpenBubbleClick: () -> Unit
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
            Text(
                text = "Thao Tác Nhanh",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimaryDark
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Button 1: AI Instant Summary
                Button(
                    onClick = onTestAiClick,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrimaryIndigo,
                        contentColor = BackgroundDark
                    ),
                    shape = RoundedCornerShape(10.dp),
                    enabled = !isTestingAi
                ) {
                    if (isTestingAi) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = BackgroundDark,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Đang xử lý...", fontSize = 12.sp, color = BackgroundDark, fontWeight = FontWeight.Bold)
                    } else {
                        Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp), tint = BackgroundDark)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Tóm Tắt AI Ngay", fontSize = 12.sp, color = BackgroundDark, fontWeight = FontWeight.Bold)
                    }
                }

                // Button 2: Open Floating Bubble
                OutlinedButton(
                    onClick = onOpenBubbleClick,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(imageVector = Icons.Default.ChatBubbleOutline, contentDescription = null, modifier = Modifier.size(18.dp), tint = AccentCyan)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Mở Bong Bóng", fontSize = 12.sp, color = TextPrimaryDark)
                }
            }
        }
    }
}

@Composable
private fun SystemStatusCockpitCard(
    isNotificationGranted: Boolean,
    onOpenNotificationSettings: () -> Unit,
    unprocessedCount: Int,
    scheduledHours: List<String>
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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Trạng Thái Hệ Thống",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimaryDark
            )

            // Row 1: Notification Listener Engine
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(if (isNotificationGranted) AccentEmerald else AccentRose, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Tiến trình theo dõi thông báo",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = TextPrimaryDark
                        )
                        Text(
                            text = if (isNotificationGranted) "Đang hoạt động tự động (Không tốn pin)" else "Chưa cấp quyền truy cập",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isNotificationGranted) AccentEmerald else AccentRose
                        )
                    }
                }

                OutlinedButton(
                    onClick = onOpenNotificationSettings,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(if (isNotificationGranted) "Kiểm tra" else "Cấp quyền", fontSize = 11.sp, color = AccentCyan)
                }
            }

            // Row 2: Room Queue
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Notifications, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Tin nhắn chờ xử lý:", style = MaterialTheme.typography.bodySmall, color = TextSecondaryDark)
                }
                Text(
                    text = "$unprocessedCount tin chưa tóm tắt",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = AccentCyan
                )
            }

            // Row 3: Scheduled hours
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Schedule, contentDescription = null, tint = AccentEmerald, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Khung giờ tóm tắt:", style = MaterialTheme.typography.bodySmall, color = TextSecondaryDark)
                }
                Text(
                    text = if (scheduledHours.isNotEmpty()) scheduledHours.joinToString(", ") else "Chưa đặt giờ",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimaryDark
                )
            }

            // Tip note
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(GlassSurfaceInner, RoundedCornerShape(8.dp))
                    .border(1.dp, GlassBorder, RoundedCornerShape(8.dp))
                    .padding(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Info, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Xem chi tiết tin nhắn tại 'Lịch Sử AI'. Cấp quyền & sao lưu tại 'Cấu Hình'.",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondaryDark,
                        lineHeight = 15.sp
                    )
                }
            }
        }
    }
}
