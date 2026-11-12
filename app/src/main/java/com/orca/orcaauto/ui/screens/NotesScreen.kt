package com.orca.orcaauto.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.orca.orcaauto.data.local.SharedNoteEntity
import com.orca.orcaauto.domain.notes.SharedNoteRepositoryImpl
import com.orca.orcaauto.ui.components.AssistantChatSheet
import com.orca.orcaauto.ui.theme.AccentAmber
import com.orca.orcaauto.ui.theme.AccentCyan
import com.orca.orcaauto.ui.theme.AccentEmerald
import com.orca.orcaauto.ui.theme.AccentRose
import com.orca.orcaauto.ui.theme.BackgroundDark
import com.orca.orcaauto.ui.theme.CardBorder
import com.orca.orcaauto.ui.theme.GlassBorder
import com.orca.orcaauto.ui.theme.GlassBorderHighlight
import com.orca.orcaauto.ui.theme.GlassBorderSubtle
import com.orca.orcaauto.ui.theme.GlassSurfaceCard
import com.orca.orcaauto.ui.theme.GlassSurfaceDark
import com.orca.orcaauto.ui.theme.GlassSurfaceInner
import com.orca.orcaauto.ui.theme.PrimaryIndigo
import com.orca.orcaauto.ui.theme.PrimaryWhite
import com.orca.orcaauto.ui.theme.SurfaceDark
import com.orca.orcaauto.ui.theme.SurfaceVariantDark
import com.orca.orcaauto.ui.theme.TextMutedDark
import com.orca.orcaauto.ui.theme.TextPrimaryDark
import com.orca.orcaauto.ui.theme.TextSecondaryDark
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class NoteFilterMode {
    ALL,
    PRIVATE,
    NORMAL,
    TRASH
}

@Composable
fun NotesScreen(
    initialOpenAssistant: Boolean = false
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { SharedNoteRepositoryImpl.getInstance(context) }

    val activeNotes by repository.getActiveNotesFlow().collectAsState(initial = emptyList())
    val trashNotes by repository.getTrashNotesFlow().collectAsState(initial = emptyList())

    var filterMode by remember { mutableStateOf(NoteFilterMode.ALL) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchExpanded by remember { mutableStateOf(false) }

    var editingNote by remember { mutableStateOf<SharedNoteEntity?>(null) }
    var showEditDialog by remember { mutableStateOf(false) }

    var permanentDeleteTarget by remember { mutableStateOf<SharedNoteEntity?>(null) }
    var showClearTrashDialog by remember { mutableStateOf(false) }

    var showAssistantSheet by remember { mutableStateOf(initialOpenAssistant) }

    // Filter calculation
    val displayedNotes = remember(filterMode, searchQuery, activeNotes, trashNotes) {
        val baseList = if (filterMode == NoteFilterMode.TRASH) trashNotes else activeNotes
        val categoryFiltered = when (filterMode) {
            NoteFilterMode.ALL -> baseList
            NoteFilterMode.PRIVATE -> baseList.filter { it.isPrivate }
            NoteFilterMode.NORMAL -> baseList.filter { !it.isPrivate }
            NoteFilterMode.TRASH -> baseList
        }
        val query = searchQuery.trim()
        if (query.isBlank()) {
            categoryFiltered
        } else {
            categoryFiltered.filter { note ->
                note.title.contains(query, ignoreCase = true) ||
                    note.content.contains(query, ignoreCase = true)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Header Bar
            NotesHeader(
                activeCount = activeNotes.size,
                trashCount = trashNotes.size,
                isTrashMode = filterMode == NoteFilterMode.TRASH,
                isSearchExpanded = isSearchExpanded,
                onToggleSearch = {
                    isSearchExpanded = !isSearchExpanded
                    if (!isSearchExpanded) searchQuery = ""
                },
                onToggleTrash = {
                    filterMode = if (filterMode == NoteFilterMode.TRASH) {
                        NoteFilterMode.ALL
                    } else {
                        NoteFilterMode.TRASH
                    }
                },
                onOpenAssistant = {
                    showAssistantSheet = true
                }
            )

            // Collapsible Search Bar
            AnimatedVisibility(
                visible = isSearchExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            text = "Tìm kiếm ghi chú theo tiêu đề hoặc nội dung...",
                            color = TextMutedDark,
                            fontSize = 14.sp
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Tìm kiếm",
                            tint = AccentCyan
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Xóa từ khóa",
                                    tint = TextSecondaryDark
                                )
                            }
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimaryDark,
                        unfocusedTextColor = TextPrimaryDark,
                        focusedContainerColor = GlassSurfaceInner,
                        unfocusedContainerColor = GlassSurfaceInner,
                        focusedBorderColor = AccentCyan,
                        unfocusedBorderColor = GlassBorderSubtle
                    ),
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Filter Chips
            NotesFilterChipsRow(
                currentMode = filterMode,
                allCount = activeNotes.size,
                privateCount = activeNotes.count { it.isPrivate },
                normalCount = activeNotes.count { !it.isPrivate },
                trashCount = trashNotes.size,
                onModeSelected = { filterMode = it }
            )

            // Trash Mode Banner
            if (filterMode == NoteFilterMode.TRASH) {
                Spacer(modifier = Modifier.height(10.dp))
                TrashModeBanner(
                    trashCount = trashNotes.size,
                    onClearTrash = { showClearTrashDialog = true }
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Note List
            if (displayedNotes.isEmpty()) {
                EmptyNotesView(
                    filterMode = filterMode,
                    searchQuery = searchQuery,
                    onCreateClick = {
                        editingNote = null
                        showEditDialog = true
                    }
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 88.dp)
                ) {
                    items(
                        items = displayedNotes,
                        key = { it.id }
                    ) { note ->
                        NoteCardItem(
                            note = note,
                            isTrash = filterMode == NoteFilterMode.TRASH,
                            onClick = {
                                if (filterMode != NoteFilterMode.TRASH) {
                                    editingNote = note
                                    showEditDialog = true
                                }
                            },
                            onTogglePin = {
                                scope.launch {
                                    repository.togglePinNote(note.id)
                                }
                            },
                            onSoftDelete = {
                                scope.launch {
                                    repository.softDeleteNote(note.id)
                                    Toast.makeText(context, "Đã chuyển vào thùng rác", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onRestore = {
                                scope.launch {
                                    repository.restoreNote(note.id)
                                    Toast.makeText(context, "Đã khôi phục ghi chú", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onPermanentDelete = {
                                permanentDeleteTarget = note
                            }
                        )
                    }
                }
            }
        }

        // Floating Action Button for creating new note (Only in active mode)
        if (filterMode != NoteFilterMode.TRASH) {
            FloatingActionButton(
                onClick = {
                    editingNote = null
                    showEditDialog = true
                },
                containerColor = AccentCyan,
                contentColor = BackgroundDark,
                shape = CircleShape,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(24.dp)
                    .border(1.dp, GlassBorderHighlight, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Thêm ghi chú mới",
                    tint = BackgroundDark
                )
            }
        }
    }

    // Note Edit Dialog
    if (showEditDialog) {
        NoteEditDialog(
            note = editingNote,
            onDismiss = { showEditDialog = false },
            onSave = { title, content, isPrivate ->
                scope.launch {
                    if (editingNote == null) {
                        repository.createNote(
                            title = title,
                            content = content,
                            isPrivate = isPrivate,
                            isPinned = false
                        )
                        Toast.makeText(context, "Đã tạo ghi chú mới", Toast.LENGTH_SHORT).show()
                    } else {
                        val current = editingNote!!
                        repository.updateNote(
                            id = current.id,
                            title = title,
                            content = content,
                            isPrivate = isPrivate,
                            isPinned = current.isPinned
                        )
                        Toast.makeText(context, "Đã lưu cập nhật ghi chú", Toast.LENGTH_SHORT).show()
                    }
                    showEditDialog = false
                }
            }
        )
    }

    // Permanent Delete Confirmation Dialog
    if (permanentDeleteTarget != null) {
        val target = permanentDeleteTarget!!
        AlertDialog(
            onDismissRequest = { permanentDeleteTarget = null },
            containerColor = SurfaceDark,
            title = {
                Text(
                    text = "Xóa vĩnh viễn ghi chú?",
                    color = AccentRose,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Ghi chú \"${target.title}\" sẽ bị xóa vĩnh viễn khỏi thiết bị và không thể khôi phục lại. Bạn có chắc chắn không?",
                    color = TextSecondaryDark
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            repository.permanentDeleteNote(target.id)
                            permanentDeleteTarget = null
                            Toast.makeText(context, "Đã xóa vĩnh viễn ghi chú", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentRose)
                ) {
                    Text("Xóa vĩnh viễn", color = PrimaryWhite, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { permanentDeleteTarget = null }) {
                    Text("Hủy", color = TextSecondaryDark)
                }
            }
        )
    }

    // Clear Trash Confirmation Dialog
    if (showClearTrashDialog) {
        AlertDialog(
            onDismissRequest = { showClearTrashDialog = false },
            containerColor = SurfaceDark,
            title = {
                Text(
                    text = "Dọn sạch thùng rác?",
                    color = AccentRose,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Tất cả các ghi chú trong thùng rác (${trashNotes.size} ghi chú) sẽ bị xóa vĩnh viễn. Hành động này không thể hoàn tác.",
                    color = TextSecondaryDark
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            repository.clearTrash()
                            showClearTrashDialog = false
                            Toast.makeText(context, "Đã dọn sạch thùng rác", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentRose)
                ) {
                    Text("Dọn sạch", color = PrimaryWhite, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearTrashDialog = false }) {
                    Text("Hủy", color = TextSecondaryDark)
                }
            }
        )
    }

    // Assistant 2-Round Chat Sheet
    if (showAssistantSheet) {
        AssistantChatSheet(
            onDismiss = { showAssistantSheet = false }
        )
    }
}

@Composable
private fun NotesHeader(
    activeCount: Int,
    trashCount: Int,
    isTrashMode: Boolean,
    isSearchExpanded: Boolean,
    onToggleSearch: () -> Unit,
    onToggleTrash: () -> Unit,
    onOpenAssistant: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Kho Ghi Chú",
                    color = TextPrimaryDark,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(AccentCyan.copy(alpha = 0.15f))
                        .border(1.dp, AccentCyan.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "$activeCount",
                        color = AccentCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Text(
                text = "Lưu trữ lâu dài trên thiết bị • Bảo vệ ghi chú riêng tư",
                color = TextMutedDark,
                fontSize = 12.sp
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Search toggle
            IconButton(onClick = onToggleSearch) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Tìm kiếm",
                    tint = if (isSearchExpanded) AccentCyan else TextSecondaryDark
                )
            }

            // Trash toggle
            IconButton(onClick = onToggleTrash) {
                Box {
                    Icon(
                        imageVector = if (isTrashMode) Icons.Default.Restore else Icons.Default.DeleteSweep,
                        contentDescription = "Thùng rác",
                        tint = if (isTrashMode) AccentRose else TextSecondaryDark
                    )
                    if (!isTrashMode && trashCount > 0) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(AccentRose)
                                .align(Alignment.TopEnd)
                        )
                    }
                }
            }

            // AI Assistant button (glowing AccentCyan badge)
            Surface(
                onClick = onOpenAssistant,
                shape = RoundedCornerShape(12.dp),
                color = GlassSurfaceInner,
                border = BorderStroke(1.dp, AccentCyan.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "Trợ lý AI",
                        tint = AccentCyan,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Trợ lý AI",
                        color = AccentCyan,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun NotesFilterChipsRow(
    currentMode: NoteFilterMode,
    allCount: Int,
    privateCount: Int,
    normalCount: Int,
    trashCount: Int,
    onModeSelected: (NoteFilterMode) -> Unit
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            FilterChipItem(
                label = "Tất cả ($allCount)",
                selected = currentMode == NoteFilterMode.ALL,
                onClick = { onModeSelected(NoteFilterMode.ALL) }
            )
        }
        item {
            FilterChipItem(
                label = "🔒 Riêng tư ($privateCount)",
                selected = currentMode == NoteFilterMode.PRIVATE,
                highlightColor = AccentCyan,
                onClick = { onModeSelected(NoteFilterMode.PRIVATE) }
            )
        }
        item {
            FilterChipItem(
                label = "Ghi chú thường ($normalCount)",
                selected = currentMode == NoteFilterMode.NORMAL,
                onClick = { onModeSelected(NoteFilterMode.NORMAL) }
            )
        }
        item {
            FilterChipItem(
                label = "Thùng rác ($trashCount)",
                selected = currentMode == NoteFilterMode.TRASH,
                highlightColor = AccentRose,
                onClick = { onModeSelected(NoteFilterMode.TRASH) }
            )
        }
    }
}

@Composable
private fun FilterChipItem(
    label: String,
    selected: Boolean,
    highlightColor: Color = AccentCyan,
    onClick: () -> Unit
) {
    val containerBg = if (selected) highlightColor.copy(alpha = 0.16f) else GlassSurfaceDark
    val borderCol = if (selected) highlightColor else GlassBorderSubtle
    val textCol = if (selected) highlightColor else TextSecondaryDark

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(containerBg)
            .border(1.dp, borderCol, RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            color = textCol,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun TrashModeBanner(
    trashCount: Int,
    onClearTrash: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = AccentRose.copy(alpha = 0.12f)),
        border = BorderStroke(1.dp, AccentRose.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.DeleteSweep,
                    contentDescription = null,
                    tint = AccentRose,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Đang xem Thùng rác ($trashCount)",
                    color = AccentRose,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            if (trashCount > 0) {
                TextButton(
                    onClick = onClearTrash,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "Dọn sạch",
                        color = AccentRose,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun NoteCardItem(
    note: SharedNoteEntity,
    isTrash: Boolean,
    onClick: () -> Unit,
    onTogglePin: () -> Unit,
    onSoftDelete: () -> Unit,
    onRestore: () -> Unit,
    onPermanentDelete: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }
    val formattedDate = remember(note.updatedAt) {
        try {
            dateFormat.format(Date(note.updatedAt))
        } catch (e: Exception) {
            "${note.updatedAt}"
        }
    }

    val cardBorder = if (note.isPrivate) {
        BorderStroke(1.dp, AccentCyan.copy(alpha = 0.35f))
    } else {
        BorderStroke(1.dp, GlassBorderSubtle)
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = GlassSurfaceCard),
        border = cardBorder,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isTrash) { onClick() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Header: Badges & Pin icon
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (note.isPrivate) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(AccentCyan.copy(alpha = 0.16f))
                                .border(1.dp, AccentCyan.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = AccentCyan,
                                    modifier = Modifier.size(11.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Riêng tư",
                                    color = AccentCyan,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    if (note.isPinned && !isTrash) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(AccentEmerald.copy(alpha = 0.16f))
                                .border(1.dp, AccentEmerald.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "ĐÃ GHIM",
                                color = AccentEmerald,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Top right actions
                if (!isTrash) {
                    IconButton(
                        onClick = onTogglePin,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = if (note.isPinned) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            contentDescription = if (note.isPinned) "Bỏ ghim" else "Ghim lên đầu",
                            tint = if (note.isPinned) AccentEmerald else TextMutedDark,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Note Title
            Text(
                text = note.title,
                color = TextPrimaryDark,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Note Content Snippet
            Text(
                text = note.content.ifBlank { "(Chưa có nội dung)" },
                color = if (note.content.isBlank()) TextMutedDark else TextSecondaryDark,
                fontSize = 13.5.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Footer: Timestamp and actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Cập nhật: $formattedDate",
                    color = TextMutedDark,
                    fontSize = 11.5.sp
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isTrash) {
                        IconButton(
                            onClick = onRestore,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Restore,
                                contentDescription = "Khôi phục",
                                tint = AccentCyan,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(
                            onClick = onPermanentDelete,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteForever,
                                contentDescription = "Xóa vĩnh viễn",
                                tint = AccentRose,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    } else {
                        IconButton(
                            onClick = onClick,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Chỉnh sửa",
                                tint = TextSecondaryDark,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        IconButton(
                            onClick = onSoftDelete,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Chuyển vào thùng rác",
                                tint = TextMutedDark,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyNotesView(
    filterMode: NoteFilterMode,
    searchQuery: String,
    onCreateClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 80.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        val (icon, title, desc) = when {
            searchQuery.isNotBlank() -> Triple(
                Icons.Default.Search,
                "Không tìm thấy kết quả",
                "Không có ghi chú nào khớp với từ khóa \"$searchQuery\"."
            )
            filterMode == NoteFilterMode.TRASH -> Triple(
                Icons.Default.DeleteSweep,
                "Thùng rác trống",
                "Chưa có ghi chú nào bị xóa tạm thời."
            )
            filterMode == NoteFilterMode.PRIVATE -> Triple(
                Icons.Default.Lock,
                "Chưa có ghi chú riêng tư",
                "Tạo ghi chú và bật cờ riêng tư để bảo vệ bằng Sealed Pointer (0 byte gửi ra mạng)."
            )
            else -> Triple(
                Icons.Default.Description,
                "Kho ghi chú đang trống",
                "Tạo ghi chú mới hoặc để Trợ lý AI tự động lưu lại các thông tin quan trọng."
            )
        }

        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(GlassSurfaceInner)
                .border(1.dp, GlassBorderSubtle, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (filterMode == NoteFilterMode.TRASH) AccentRose else AccentCyan,
                modifier = Modifier.size(28.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = title,
            color = TextPrimaryDark,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = desc,
            color = TextSecondaryDark,
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 32.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        if (filterMode != NoteFilterMode.TRASH && searchQuery.isBlank()) {
            Spacer(modifier = Modifier.height(20.dp))
            OutlinedButton(
                onClick = onCreateClick,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, AccentCyan),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentCyan)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Tạo Ghi Chú Mới", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun NoteEditDialog(
    note: SharedNoteEntity?,
    onDismiss: () -> Unit,
    onSave: (title: String, content: String, isPrivate: Boolean) -> Unit
) {
    var title by remember { mutableStateOf(note?.title.orEmpty()) }
    var content by remember { mutableStateOf(note?.content.orEmpty()) }
    var isPrivate by remember { mutableStateOf(note?.isPrivate ?: false) }
    var showExplanation by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceDark,
        shape = RoundedCornerShape(20.dp),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (note == null) "Tạo Ghi Chú Mới" else "Chỉnh Sửa Ghi Chú #${note.id}",
                    color = TextPrimaryDark,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Đóng",
                        tint = TextSecondaryDark,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Title field
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Tiêu đề", color = TextSecondaryDark) },
                    placeholder = { Text("Nhập tiêu đề ghi chú...", color = TextMutedDark) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimaryDark,
                        unfocusedTextColor = TextPrimaryDark,
                        focusedBorderColor = AccentCyan,
                        unfocusedBorderColor = GlassBorderSubtle,
                        focusedContainerColor = GlassSurfaceInner,
                        unfocusedContainerColor = GlassSurfaceInner
                    ),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Content field
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("Nội dung", color = TextSecondaryDark) },
                    placeholder = { Text("Nhập nội dung chi tiết...", color = TextMutedDark) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimaryDark,
                        unfocusedTextColor = TextPrimaryDark,
                        focusedBorderColor = AccentCyan,
                        unfocusedBorderColor = GlassBorderSubtle,
                        focusedContainerColor = GlassSurfaceInner,
                        unfocusedContainerColor = GlassSurfaceInner
                    ),
                    shape = RoundedCornerShape(12.dp),
                    minLines = 5,
                    maxLines = 10,
                    modifier = Modifier.fillMaxWidth()
                )

                // Privacy Switch Row
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isPrivate) AccentCyan.copy(alpha = 0.12f) else GlassSurfaceInner
                    ),
                    border = BorderStroke(
                        1.dp,
                        if (isPrivate) AccentCyan.copy(alpha = 0.4f) else GlassBorderSubtle
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = if (isPrivate) Icons.Default.Lock else Icons.Default.LockOpen,
                                    contentDescription = null,
                                    tint = if (isPrivate) AccentCyan else TextSecondaryDark,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "Ghi chú riêng tư (🔒)",
                                        color = if (isPrivate) AccentCyan else TextPrimaryDark,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = "AI không thể đọc nội dung cũ",
                                        color = TextMutedDark,
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            Switch(
                                checked = isPrivate,
                                onCheckedChange = { isPrivate = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = AccentCyan,
                                    checkedTrackColor = AccentCyan.copy(alpha = 0.3f),
                                    uncheckedThumbColor = TextMutedDark,
                                    uncheckedTrackColor = SurfaceVariantDark
                                )
                            )
                        }

                        // Explanation button / text
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clickable { showExplanation = !showExplanation }
                                .padding(vertical = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                                contentDescription = null,
                                tint = AccentCyan,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (showExplanation) "Thu gọn giải thích" else "Cách bảo vệ ghi chú riêng tư",
                                color = AccentCyan,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        if (showExplanation || isPrivate) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(BackgroundDark.copy(alpha = 0.5f))
                                    .padding(8.dp)
                            ) {
                                Text(
                                    text = "🔒 Bảo vệ quyền riêng tư trên thiết bị:\n" +
                                        "• Nội dung ghi chú riêng tư không bao giờ bị gửi lên AI.\n" +
                                        "• AI chỉ thấy tên ghi chú để mở thẻ cho bạn tự xem hoặc nối thêm ghi chú mới ở cuối.\n" +
                                        "• AI bị chặn hoàn toàn việc sửa đè hoặc xóa ghi chú riêng tư.",
                                    color = TextSecondaryDark,
                                    fontSize = 11.5.sp,
                                    lineHeight = 16.sp
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
                    val finalTitle = title.trim().ifBlank { "Ghi chú không tên" }
                    onSave(finalTitle, content.trim(), isPrivate)
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentCyan,
                    contentColor = BackgroundDark
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "Lưu Ghi Chú",
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Hủy", color = TextSecondaryDark)
            }
        }
    )
}
