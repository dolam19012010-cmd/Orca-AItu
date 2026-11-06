package com.orca.orcaauto.ui.components

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import com.orca.orcaauto.ui.theme.GlassBorderSubtle
import com.orca.orcaauto.ui.theme.GlassSurfaceDark
import com.orca.orcaauto.ui.theme.GlassSurfaceInner
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
                    text = "\uD83D\uDCA1 H\u1EC7 th\u1ED1ng \u0110a Phi\u00EAn Tu\u1EA7n T\u1EF1 (FIFO):",
                    style = MaterialTheme.typography.bodySmall,
                    color = AccentCyan,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "M\u1EB7c \u0111\u1ECBnh \u1EE9ng d\u1EE5ng ch\u1EA1y 'Phi\u00EAn ch\u00EDnh'. B\u1EA1n c\u00F3 th\u1EC3 t\u1EA1o th\u00EAm phi\u00EAn ri\u00EAng (D\u1EF1 \u00E1n, S\u1EBFp, Gia \u0111\u00ECnh...) v\u1EDBi Whitelist ri\u00EAng v\u00E0 th\u1EE9 t\u1EF1 \u01B0u ti\u00EAn Key ri\u00EAng. H\u1EC7 th\u1ED1ng g\u1ECDi AI tu\u1EA7n t\u1EF1, phi\u00EAn n\u00E0o y\u00EAu c\u1EA7u tr\u01B0\u1EDBc ch\u1EA1y tr\u01B0\u1EDBc, tuy\u1EC7t \u0111\u1ED1i kh\u00F4ng ch\u1EA1y \u0111\u00E8 l\u00EAn nhau.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFCBD5E1),
                    lineHeight = 18.sp
                )
            }
        }

        // Sessions List
        sessions.forEach { session ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = GlassSurfaceDark),
                border = BorderStroke(
                    1.dp,
                    if (session.isMain) AccentCyan.copy(alpha = 0.6f) else GlassBorderSubtle
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
                                color = Color.White
                            )
                            if (session.isMain) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    color = Color(0xFF2563EB).copy(alpha = 0.25f),
                                    shape = RoundedCornerShape(6.dp),
                                    border = BorderStroke(0.8.dp, Color(0xFF3B82F6))
                                ) {
                                    Text(
                                        text = "Ch\u00EDnh",
                                        color = Color(0xFF93C5FD),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        val whitelistDesc = when {
                            session.isMain && session.whitelist.isEmpty() -> "To\u00E0n b\u1ED9 tin nh\u1EAFn"
                            session.whitelist.isEmpty() -> "Ch\u01B0a ch\u1ECDn ng\u01B0\u1EDDi/nh\u00F3m"
                            else -> "${session.whitelist.size} ng\u01B0\u1EDDi/nh\u00F3m theo d\u00F5i"
                        }

                        val keyPriorityDesc = if (session.keyPriorityIds.isEmpty()) {
                            "D\u00F9ng Key Pool m\u1EB7c \u0111\u1ECBnh"
                        } else {
                            val count = session.keyPriorityIds.size
                            "$count Key \u01B0u ti\u00EAn ri\u00EAng"
                        }

                        val instructionDesc = if (session.customInstruction.isNotBlank()) "\n\u2022 Ch\u1EC9 th\u1ECB AI: ${session.customInstruction.take(30)}..." else ""
                        val scheduledDesc = if (!session.isMain && !session.isScheduled) "\n\u2022 Ca \u0111\u1ECBnh k\u1EF3: T\u1EAFt (Ch\u1EC9 t\u00F3m t\u1EAFt th\u1EE7 c\u00F4ng)" else ""

                        Text(
                            text = "\u2022 $whitelistDesc\n\u2022 $keyPriorityDesc$instructionDesc$scheduledDesc",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFA1A1AA),
                            fontSize = 11.sp,
                            lineHeight = 16.sp
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { editingSession = session }) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "S\u1EEDa phi\u00EAn",
                                tint = AccentCyan,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        if (!session.isMain) {
                            IconButton(onClick = { onDeleteSession(session.id) }) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "X\u00F3a phi\u00EAn",
                                    tint = AccentRose,
                                    modifier = Modifier.size(20.dp)
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
            border = BorderStroke(1.dp, AccentCyan.copy(alpha = 0.6f))
        ) {
            Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = "T\u1EA1o Phi\u00EAn M\u1EDBi", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        }
    }

    if (showCreateDialog) {
        SessionConfigDialog(
            session = SummarySessionConfig(
                name = "",
                iconEmoji = "\uD83D\uDCBC",
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
    val whitelist = remember { mutableStateListOf<String>().apply { addAll(session.whitelist) } }
    val prioritizedKeyIds = remember { mutableStateListOf<String>().apply { addAll(session.keyPriorityIds) } }
    var customInput by remember { mutableStateOf("") }
    var customInstruction by remember { mutableStateOf(session.customInstruction) }
    var isScheduled by remember { mutableStateOf(session.isScheduled) }

    val availableEmojis = listOf("\u26A1", "\uD83D\uDCBC", "\uD83D\uDE80", "\uD83D\uDC65", "\uD83C\uDFE0", "\uD83C\uDFAF", "\uD83D\uDCCC", "\uD83D\uDD25", "\uD83D\uDCAC", "\u2B50")

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceDark,
        title = {
            Text(
                text = if (isNew) "T\u1EA1o Phi\u00EAn M\u1EDBi" else "C\u1EA5u H\u00ECnh: ${session.name}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
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
                    text = "1. T\u00EAn Phi\u00EAn & Bi\u1EC3u T\u01B0\u1EE3ng:",
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
                        val isSelectedEmoji = selectedEmoji == emoji
                        Surface(
                            onClick = { selectedEmoji = emoji },
                            shape = CircleShape,
                            color = if (isSelectedEmoji) Color(0xFF2563EB) else GlassSurfaceDark,
                            border = BorderStroke(
                                1.dp,
                                if (isSelectedEmoji) Color(0xFF93C5FD) else GlassBorderSubtle
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
                    label = { Text("T\u00EAn phi\u00EAn (vd: D\u1EF1 \u00C1n Alpha, S\u1EBFp Tu\u1EA5n)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedLabelColor = AccentCyan,
                        unfocusedLabelColor = Color(0xFFA1A1AA),
                        focusedBorderColor = AccentCyan,
                        unfocusedBorderColor = CardBorder
                    )
                )

                HorizontalDivider(color = GlassBorderSubtle)

                // 2. Whitelist contacts
                Text(
                    text = "2. Ng\u01B0\u1EDDi / Nh\u00F3m Chat \u0110\u01B0\u1EE3c Gom (Whitelist):",
                    style = MaterialTheme.typography.bodySmall,
                    color = AccentCyan,
                    fontWeight = FontWeight.Bold
                )

                if (recentSenders.isNotEmpty()) {
                    Text(
                        text = "Ch\u1EA1m nhanh ng\u01B0\u1EDDi g\u1EEDi g\u1EA7n \u0111\u00E2y \u0111\u1EC3 th\u00EAm:",
                        fontSize = 11.sp,
                        color = Color(0xFFA1A1AA)
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
                                color = if (isAdded) Color(0xFF10B981).copy(alpha = 0.25f) else GlassSurfaceDark,
                                border = BorderStroke(
                                    1.dp,
                                    if (isAdded) Color(0xFF10B981) else GlassBorderSubtle
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
                                            tint = Color(0xFF10B981),
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                    }
                                    Text(
                                        text = sender,
                                        fontSize = 11.sp,
                                        color = if (isAdded) Color.White else Color(0xFFCBD5E1)
                                    )
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = customInput,
                        onValueChange = { customInput = it },
                        placeholder = { Text("Nh\u1EADp t\u00EAn ho\u1EB7c d\u00F9ng * (vd: *D\u1EF1 \u00C1n*)", fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
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
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF2563EB),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Th\u00EAm", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }

                if (whitelist.isNotEmpty()) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        whitelist.forEach { item ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFF2563EB).copy(alpha = 0.25f),
                                border = BorderStroke(1.dp, Color(0xFF3B82F6))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(text = item, color = Color.White, fontSize = 11.sp)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "X\u00F3a",
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
                    text = "3. Th\u1EE9 T\u1EF1 \u01AFu Ti\u00EAn API Key (Sequential Queue):",
                    style = MaterialTheme.typography.bodySmall,
                    color = AccentCyan,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "H\u1EC7 th\u1ED1ng s\u1EBD g\u1ECDi Key \u01AFu ti\u00EAn 1 tr\u01B0\u1EDBc. N\u1EBFu Key n\u00E0y l\u1ED7i, t\u1EF1 \u0111\u1ED9ng chuy\u1EC3n sang Key ti\u1EBFp theo.",
                    fontSize = 11.sp,
                    color = Color(0xFFA1A1AA)
                )

                if (keyPool.isEmpty()) {
                    Text(
                        text = "\u26A0\uFE0F Ch\u01B0a c\u00F3 API Key n\u00E0o trong Key Pool. H\u00E3y v\u00E0o m\u1EE5c 'Universal AI Key' \u0111\u1EC3 th\u00EAm.",
                        fontSize = 11.sp,
                        color = AccentRose
                    )
                } else {
                    keyPool.forEachIndexed { _, keyConfig ->
                        val priorityIndex = prioritizedKeyIds.indexOf(keyConfig.id)
                        val isPrioritized = priorityIndex >= 0

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isPrioritized) Color(0xFF2563EB).copy(alpha = 0.2f) else GlassSurfaceDark
                            ),
                            border = BorderStroke(
                                0.8.dp,
                                if (isPrioritized) Color(0xFF60A5FA) else GlassBorderSubtle
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
                                        color = if (isPrioritized) Color(0xFF2563EB) else Color(0xFF3F3F46),
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
                                            text = "${keyConfig.provider.displayName} \u2022 ${keyConfig.modelName}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.White,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = keyConfig.maskedKey,
                                            fontSize = 10.sp,
                                            color = Color(0xFFA1A1AA)
                                        )
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (isPrioritized) {
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
                                                contentDescription = "T\u0103ng \u01B0u ti\u00EAn",
                                                tint = if (priorityIndex > 0) AccentCyan else Color.Gray,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }

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
                                                contentDescription = "Gi\u1EA3m \u01B0u ti\u00EAn",
                                                tint = if (priorityIndex < prioritizedKeyIds.size - 1) AccentCyan else Color.Gray,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }

                                        IconButton(
                                            onClick = { prioritizedKeyIds.remove(keyConfig.id) },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "B\u1ECF \u01B0u ti\u00EAn",
                                                tint = AccentRose,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    } else {
                                        TextButton(
                                            onClick = { prioritizedKeyIds.add(keyConfig.id) }
                                        ) {
                                            Text("+ \u01AFu ti\u00EAn", fontSize = 11.sp, color = AccentCyan)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                HorizontalDivider(color = GlassBorderSubtle)

                // 4. Custom Permanent Instruction (Prompt Riêng Của Bản Nhân Bản)
                Text(
                    text = "4. L\u1EDDi D\u1EB7n C\u1ED1 \u0110\u1ECBnh Cho AI C\u1EE7a Phi\u00EAn N\u00E0y (Prompt Ri\u00EAng):",
                    style = MaterialTheme.typography.bodySmall,
                    color = AccentCyan,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "Gi\u1ED1ng nh\u01B0 m\u1ED9t b\u1EA3n app nh\u00E2n b\u1EA3n \u0111\u1ED9c l\u1EADp, b\u1EA1n c\u00F3 th\u1EC3 d\u1EB7n d\u00F2 ri\u00EAng cho phi\u00EAn n\u00E0y (vd: 'Ch\u1EC9 l\u1ECDc deadline v\u00E0 vi\u1EC7c g\u1EA5p, b\u1ECF qua x\u00E3 giao').",
                    fontSize = 11.sp,
                    color = Color(0xFFA1A1AA)
                )

                OutlinedTextField(
                    value = customInstruction,
                    onValueChange = { customInstruction = it },
                    label = { Text("Ch\u1EC9 th\u1ECB ri\u00EAng cho AI phi\u00EAn n\u00E0y (T\u00F9y ch\u1ECDn)") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedLabelColor = AccentCyan,
                        unfocusedLabelColor = Color(0xFFA1A1AA),
                        focusedBorderColor = AccentCyan,
                        unfocusedBorderColor = GlassBorderSubtle
                    )
                )

                HorizontalDivider(color = GlassBorderSubtle)

                // 5. Automatic Shift Summary Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "5. T\u1EF1 \u0110\u1ED9ng T\u00F3m T\u1EAFt Theo Ca:",
                            style = MaterialTheme.typography.bodySmall,
                            color = AccentCyan,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "T\u1EF1 \u0111\u1ED9ng t\u00F3m t\u1EAFt theo c\u00E1c m\u1ED1c gi\u1EDD ca (12:00, 18:00) n\u1EBFu phi\u00EAn c\u00F3 tin nh\u1EAFn m\u1EDBi.",
                            fontSize = 11.sp,
                            color = Color(0xFFA1A1AA)
                        )
                    }
                    Switch(
                        checked = isScheduled,
                        onCheckedChange = { isScheduled = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF2563EB)
                        )
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalName = name.trim().ifBlank { if (isNew) "Phi\u00EAn m\u1EDBi" else session.name }
                    val updated = session.copy(
                        name = finalName,
                        iconEmoji = selectedEmoji,
                        filterMode = if (whitelist.isNotEmpty()) ChatFilterMode.STRICT_WHITELIST else session.filterMode,
                        whitelist = whitelist.toSet(),
                        keyPriorityIds = prioritizedKeyIds.toList(),
                        customInstruction = customInstruction.trim(),
                        isScheduled = isScheduled
                    )
                    onSave(updated)
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF2563EB),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("L\u01B0u Phi\u00EAn", fontWeight = FontWeight.Bold, color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("H\u1EE7y", color = Color(0xFFA1A1AA))
            }
        }
    )
}
