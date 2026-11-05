package com.orca.orcaauto.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.orca.orcaauto.data.preferences.ChatFilterMode
import com.orca.orcaauto.data.preferences.SummarySessionConfig
import com.orca.orcaauto.data.remote.ApiKeyConfig
import com.orca.orcaauto.ui.theme.AccentCyan
import com.orca.orcaauto.ui.theme.AccentEmerald
import com.orca.orcaauto.ui.theme.AccentRose
import com.orca.orcaauto.ui.theme.CardBorder
import com.orca.orcaauto.ui.theme.GlassBorder
import com.orca.orcaauto.ui.theme.GlassBorderSubtle
import com.orca.orcaauto.ui.theme.GlassSurfaceCard
import com.orca.orcaauto.ui.theme.GlassSurfaceDark
import com.orca.orcaauto.ui.theme.GlassSurfaceInner
import com.orca.orcaauto.ui.theme.PrimaryIndigo
import com.orca.orcaauto.ui.theme.SurfaceDark
import com.orca.orcaauto.ui.theme.TextMutedDark
import com.orca.orcaauto.ui.theme.TextPrimaryDark
import com.orca.orcaauto.ui.theme.TextSecondaryDark

@Composable
fun MultiSessionManagementSection(
    sessions: List<SummarySessionConfig>,
    keyPool: List<ApiKeyConfig>,
    recentSenders: List<String>,
    onSaveSession: (SummarySessionConfig) -> Unit,
    onDeleteSession: (String) -> Unit
) {
    var editingSession by remember { mutableStateOf<SummarySessionConfig?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Guidance Note
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = GlassSurfaceInner),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "💡 Hệ thống Đa Phiên Tuần Tự (FIFO):",
                    style = MaterialTheme.typography.bodySmall,
                    color = AccentCyan,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Mặc định ứng dụng chạy 'Phiên chính'. Bạn có thể tạo thêm phiên riêng (Dự án, Sếp, Gia đình...) với Whitelist riêng và thứ tự ưu tiên Key riêng. Hệ thống gọi AI tuần tự, phiên nào yêu cầu trước chạy trước, tuyệt đối không chạy đè lên nhau.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondaryDark,
                    lineHeight = 18.sp
                )
            }
        }

        // Sessions List
        sessions.forEach { session ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = GlassSurfaceDark),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (session.isMain) AccentCyan.copy(alpha = 0.5f) else GlassBorderSubtle
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = session.iconEmoji, fontSize = 18.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = session.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimaryDark
                            )
                            if (session.isMain) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    color = PrimaryIndigo.copy(alpha = 0.3f),
                                    shape = RoundedCornerShape(6.dp),
                                    border = androidx.compose.foundation.BorderStroke(0.8.dp, PrimaryIndigo)
                                ) {
                                    Text(
                                        text = "Chính",
                                        color = AccentCyan,
                                        fontSize = 10.sp,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // Subtitle descriptions
                        val whitelistDesc = when {
                            session.isMain && session.whitelist.isEmpty() -> "Toàn bộ tin nhắn"
                            session.whitelist.isEmpty() -> "Chưa chọn người/nhóm"
                            else -> "${session.whitelist.size} người/nhóm theo dõi"
                        }

                        val keyPriorityDesc = if (session.keyPriorityIds.isEmpty()) {
                            "Dùng Key Pool mặc định"
                        } else {
                            val count = session.keyPriorityIds.size
                            "$count Key ưu tiên riêng"
                        }

                        Text(
                            text = "• $whitelistDesc\n• $keyPriorityDesc",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondaryDark,
                            fontSize = 11.sp,
                            lineHeight = 16.sp
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { editingSession = session }) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Sửa phiên",
                                tint = AccentCyan,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        if (!session.isMain) {
                            IconButton(onClick = { onDeleteSession(session.id) }) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Xóa phiên",
                                    tint = AccentRose,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Add Session Button
        OutlinedButton(
            onClick = { showCreateDialog = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentCyan),
            border = androidx.compose.foundation.BorderStroke(1.dp, AccentCyan.copy(alpha = 0.5f))
        ) {
            Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = "Tạo Phiên Mới", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        }
    }

    // Dialog for creating a new session
    if (showCreateDialog) {
        SessionConfigDialog(
            session = SummarySessionConfig(
                name = "",
                iconEmoji = "💼",
                filterMode = ChatFilterMode.STRICT_WHITELIST
            ),
            isNew = true,
            keyPool = keyPool,
            recentSenders = recentSenders,
            onDismiss = { showCreateDialog = false },
            onSave = { newSession ->
                onSaveSession(newSession)
                showCreateDialog = false
            }
        )
    }

    // Dialog for editing an existing session
    editingSession?.let { session ->
        SessionConfigDialog(
            session = session,
            isNew = false,
            keyPool = keyPool,
            recentSenders = recentSenders,
            onDismiss = { editingSession = null },
            onSave = { updatedSession ->
                onSaveSession(updatedSession)
                editingSession = null
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SessionConfigDialog(
    session: SummarySessionConfig,
    isNew: Boolean,
    keyPool: List<ApiKeyConfig>,
    recentSenders: List<String>,
    onDismiss: () -> Unit,
    onSave: (SummarySessionConfig) -> Unit
) {
    var name by remember { mutableStateOf(session.name) }
    var selectedEmoji by remember { mutableStateOf(session.iconEmoji) }
    var filterMode by remember { mutableStateOf(session.filterMode) }
    val whitelist = remember { mutableStateListOf<String>().apply { addAll(session.whitelist) } }
    val prioritizedKeyIds = remember { mutableStateListOf<String>().apply { addAll(session.keyPriorityIds) } }
    var customInput by remember { mutableStateOf("") }

    val availableEmojis = listOf("⚡", "💼", "🚀", "👥", "🏠", "🎯", "📌", "🔥", "💬", "⭐")

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceDark,
        title = {
            Text(
                text = if (isNew) "Tạo Phiên Mới" else "Cấu Hình: ${session.name}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimaryDark
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 1. Emoji & Name Row
                Text(
                    text = "1. Tên Phiên & Biểu Tượng:",
                    style = MaterialTheme.typography.bodySmall,
                    color = AccentCyan,
                    fontWeight = FontWeight.Bold
                )

                // Emoji selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    availableEmojis.take(6).forEach { emoji ->
                        Surface(
                            onClick = { selectedEmoji = emoji },
                            shape = CircleShape,
                            color = if (selectedEmoji == emoji) PrimaryIndigo else GlassSurfaceDark,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (selectedEmoji == emoji) AccentCyan else GlassBorderSubtle
                            ),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(text = emoji, fontSize = 16.sp)
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Tên phiên (vd: Dự Án Alpha, Sếp Tuấn)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimaryDark,
                        unfocusedTextColor = TextPrimaryDark,
                        focusedBorderColor = AccentCyan,
                        unfocusedBorderColor = CardBorder
                    )
                )

                HorizontalDivider(color = GlassBorderSubtle)

                // 2. Whitelist contacts
                Text(
                    text = "2. Người / Nhóm Chat Được Gom (Whitelist):",
                    style = MaterialTheme.typography.bodySmall,
                    color = AccentCyan,
                    fontWeight = FontWeight.Bold
                )

                // Quick tap suggestions from recent senders
                if (recentSenders.isNotEmpty()) {
                    Text(
                        text = "Chạm nhanh người gửi gần đây để thêm:",
                        fontSize = 11.sp,
                        color = TextMutedDark
                    )
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        recentSenders.take(8).forEach { sender ->
                            val isAdded = whitelist.any { it.equals(sender, ignoreCase = true) }
                            Surface(
                                onClick = {
                                    if (isAdded) {
                                        whitelist.removeAll { it.equals(sender, ignoreCase = true) }
                                    } else {
                                        whitelist.add(sender)
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                color = if (isAdded) AccentEmerald.copy(alpha = 0.25f) else GlassSurfaceDark,
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isAdded) AccentEmerald else GlassBorderSubtle
                                ),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (isAdded) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = AccentEmerald,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                    }
                                    Text(
                                        text = sender,
                                        fontSize = 11.sp,
                                        color = if (isAdded) Color.White else TextSecondaryDark
                                    )
                                }
                            }
                        }
                    }
                }

                // Manual add with wildcard support
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = customInput,
                        onValueChange = { customInput = it },
                        placeholder = { Text("Nhập tên hoặc dùng * (vd: *Dự Án*)", fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimaryDark,
                            unfocusedTextColor = TextPrimaryDark,
                            focusedBorderColor = AccentCyan,
                            unfocusedBorderColor = CardBorder
                        )
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Button(
                        onClick = {
                            if (customInput.isNotBlank() && !whitelist.contains(customInput.trim())) {
                                whitelist.add(customInput.trim())
                                customInput = ""
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryIndigo),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Thêm", fontSize = 11.sp)
                    }
                }

                // Current whitelist chips
                if (whitelist.isNotEmpty()) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        whitelist.forEach { item ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = PrimaryIndigo.copy(alpha = 0.35f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, PrimaryIndigo)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(text = item, color = Color.White, fontSize = 11.sp)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Xóa",
                                        tint = AccentRose,
                                        modifier = Modifier
                                            .size(14.dp)
                                            .clickable { whitelist.remove(item) }
                                    )
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = GlassBorderSubtle)

                // 3. API Key Priority Reordering
                Text(
                    text = "3. Thứ Tự Ưu Tiên API Key (Sequential Queue):",
                    style = MaterialTheme.typography.bodySmall,
                    color = AccentCyan,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "Hệ thống sẽ gọi Key Ưu tiên 1 trước. Nếu Key này lỗi, tự động chuyển sang Key tiếp theo.",
                    fontSize = 11.sp,
                    color = TextMutedDark
                )

                if (keyPool.isEmpty()) {
                    Text(
                        text = "⚠️ Chưa có API Key nào trong Key Pool. Hãy vào mục 'Universal AI Key' để thêm.",
                        fontSize = 11.sp,
                        color = AccentRose
                    )
                } else {
                    // Display all keys in keyPool with order controls
                    keyPool.forEachIndexed { _, keyConfig ->
                        val priorityIndex = prioritizedKeyIds.indexOf(keyConfig.id)
                        val isPrioritized = priorityIndex >= 0

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isPrioritized) PrimaryIndigo.copy(alpha = 0.2f) else GlassSurfaceDark
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                0.8.dp,
                                if (isPrioritized) AccentCyan.copy(alpha = 0.6f) else GlassBorderSubtle
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        color = if (isPrioritized) PrimaryIndigo else Color.DarkGray,
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = if (isPrioritized) "#${priorityIndex + 1}" else "--",
                                            color = Color.White,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = "${keyConfig.provider.displayName} • ${keyConfig.modelName}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = TextPrimaryDark,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = keyConfig.maskedKey,
                                            fontSize = 10.sp,
                                            color = TextMutedDark
                                        )
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (isPrioritized) {
                                        // Up button
                                        IconButton(
                                            onClick = {
                                                if (priorityIndex > 0) {
                                                    val temp = prioritizedKeyIds[priorityIndex]
                                                    prioritizedKeyIds[priorityIndex] = prioritizedKeyIds[priorityIndex - 1]
                                                    prioritizedKeyIds[priorityIndex - 1] = temp
                                                }
                                            },
                                            enabled = priorityIndex > 0,
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ArrowUpward,
                                                contentDescription = "Tăng ưu tiên",
                                                tint = if (priorityIndex > 0) AccentCyan else Color.Gray,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }

                                        // Down button
                                        IconButton(
                                            onClick = {
                                                if (priorityIndex < prioritizedKeyIds.size - 1) {
                                                    val temp = prioritizedKeyIds[priorityIndex]
                                                    prioritizedKeyIds[priorityIndex] = prioritizedKeyIds[priorityIndex + 1]
                                                    prioritizedKeyIds[priorityIndex + 1] = temp
                                                }
                                            },
                                            enabled = priorityIndex < prioritizedKeyIds.size - 1,
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ArrowDownward,
                                                contentDescription = "Giảm ưu tiên",
                                                tint = if (priorityIndex < prioritizedKeyIds.size - 1) AccentCyan else Color.Gray,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }

                                        // Remove from priority
                                        IconButton(
                                            onClick = { prioritizedKeyIds.remove(keyConfig.id) },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Bỏ ưu tiên",
                                                tint = AccentRose,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    } else {
                                        // Add to priority button
                                        TextButton(
                                            onClick = { prioritizedKeyIds.add(keyConfig.id) }
                                        ) {
                                            Text("+ Ưu tiên", fontSize = 11.sp, color = AccentCyan)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalName = name.trim().ifBlank { if (isNew) "Phiên mới" else session.name }
                    val updated = session.copy(
                        name = finalName,
                        iconEmoji = selectedEmoji,
                        filterMode = if (whitelist.isNotEmpty()) ChatFilterMode.STRICT_WHITELIST else session.filterMode,
                        whitelist = whitelist.toSet(),
                        keyPriorityIds = prioritizedKeyIds.toList()
                    )
                    onSave(updated)
                },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryIndigo)
            ) {
                Text("Lưu Phiên")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Hủy", color = TextSecondaryDark)
            }
        }
    )
}
