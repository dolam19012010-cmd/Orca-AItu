package com.orca.orcaauto.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.orca.orcaauto.data.local.SharedNoteEntity
import com.orca.orcaauto.data.preferences.AssistantThinkingLevel
import com.orca.orcaauto.data.preferences.UserPreferences
import com.orca.orcaauto.domain.assistant.AssistantPipeline
import com.orca.orcaauto.domain.assistant.AssistantStep
import com.orca.orcaauto.domain.assistant.AssistantUiState
import com.orca.orcaauto.domain.assistant.NoteCommandResult
import com.orca.orcaauto.ui.theme.AccentAmber
import com.orca.orcaauto.ui.theme.AccentCyan
import com.orca.orcaauto.ui.theme.AccentEmerald
import com.orca.orcaauto.ui.theme.AccentRose
import com.orca.orcaauto.ui.theme.BackgroundDark
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * In-memory representation of an Assistant chat bubble during the session.
 */
data class AssistantChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val isUser: Boolean,
    val text: String,
    val thought: String = "",
    val state: AssistantUiState? = null,
    val isStreaming: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * AssistantChatSheet:
 * 2-Round Chat UX with real-time SSE Streaming and Sealed Pointer Room DB integration.
 *
 * KEY INVARIANTS & USER DIRECTIVES:
 * 1. Round 1 runs completely invisible in background ("Đang phân tích bối cảnh...").
 * 2. Round 2 streams text smoothly token-by-token with blinking cursor.
 * 3. Command stripping in real-time prevents raw tag leaks and flickering.
 * 4. LocalNoteCards are rendered directly under the answer after stream completion.
 * 5. UI State & IME padding: Virtual keyboard never conceals the input box.
 * 6. Smooth auto-scroll keeps the streaming response in view.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantChatSheet(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val messages = remember { mutableStateListOf<AssistantChatMessage>() }
    var inputText by remember { mutableStateOf("") }
    var isProcessing by remember { mutableStateOf(false) }
    var activeJob by remember { mutableStateOf<Job?>(null) }

    val userPrefs = remember { UserPreferences.getInstance(context) }
    val thinkingLevel by userPrefs.assistantThinkingLevelFlow.collectAsState()

    val listState = rememberLazyListState()

    // Clean cancellation when dismissed or disposed
    DisposableEffect(Unit) {
        onDispose {
            activeJob?.cancel()
        }
    }

    // Auto-scroll to bottom whenever the latest message text or thought updates
    LaunchedEffect(messages.size, messages.lastOrNull()?.text, messages.lastOrNull()?.thought) {
        if (messages.isNotEmpty()) {
            val isStillStreaming = messages.lastOrNull()?.isStreaming == true
            if (isStillStreaming) {
                listState.scrollToItem(messages.size - 1)
            } else {
                listState.animateScrollToItem(messages.size - 1)
            }
        }
    }

    // Execution dispatcher
    val sendMessage: (String) -> Unit = { query ->
        val trimmed = query.trim()
        if (trimmed.isNotBlank() && !isProcessing) {
            inputText = ""
            isProcessing = true

            // Add user message
            messages.add(
                AssistantChatMessage(
                    isUser = true,
                    text = trimmed
                )
            )

            // Add placeholder assistant message
            val assistantMsgId = UUID.randomUUID().toString()
            val initialAssistantMsg = AssistantChatMessage(
                id = assistantMsgId,
                isUser = false,
                text = "",
                isStreaming = true,
                state = AssistantUiState(
                    step = AssistantStep.STAGE_1_PACKING_INDEX,
                    stepDescription = "Đang phân tích bối cảnh..."
                )
            )
            messages.add(initialAssistantMsg)

            activeJob = scope.launch {
                try {
                    val pipeline = AssistantPipeline.create(context)
                    pipeline.askStreaming(trimmed).collect { uiState ->
                        val index = messages.indexOfFirst { it.id == assistantMsgId }
                        if (index != -1) {
                            val isStillStreaming = uiState.step != AssistantStep.COMPLETED &&
                                uiState.step != AssistantStep.ERROR

                            messages[index] = messages[index].copy(
                                text = uiState.streamedContentClean,
                                thought = uiState.streamedThought,
                                state = uiState,
                                isStreaming = isStillStreaming
                            )
                        }
                    }
                } catch (t: Throwable) {
                    val index = messages.indexOfFirst { it.id == assistantMsgId }
                    if (index != -1) {
                        messages[index] = messages[index].copy(
                            isStreaming = false,
                            state = AssistantUiState(
                                step = AssistantStep.ERROR,
                                errorMessage = t.message ?: "Lỗi kết nối mạng"
                            )
                        )
                    }
                } finally {
                    isProcessing = false
                    activeJob = null
                }
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = {
            activeJob?.cancel()
            onDismiss()
        },
        sheetState = sheetState,
        containerColor = SurfaceDark,
        dragHandle = {
            BottomSheetDefaults.DragHandle(
                color = GlassBorderSubtle
            )
        },
        windowInsets = WindowInsets.ime,
        modifier = Modifier.fillMaxHeight(0.94f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
        ) {
            // Header Bar
            AssistantSheetHeader(
                isProcessing = isProcessing,
                onClose = {
                    activeJob?.cancel()
                    onDismiss()
                }
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Chat Messages / Suggestions View
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (messages.isEmpty()) {
                    EmptyConversationView(
                        onSelectSuggestion = { suggestion ->
                            sendMessage(suggestion)
                        }
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp)
                    ) {
                        items(
                            items = messages,
                            key = { it.id }
                        ) { message ->
                            if (message.isUser) {
                                UserChatBubble(message = message)
                            } else {
                                AssistantChatBubble(
                                    message = message,
                                    onRetry = {
                                        val lastUserMsg = messages.lastOrNull { it.isUser }?.text
                                        if (!lastUserMsg.isNullOrBlank()) {
                                            sendMessage(lastUserMsg)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Input Bar with IME Padding and Thinking Level Chips
            AssistantInputBar(
                inputText = inputText,
                onInputChanged = { inputText = it },
                isProcessing = isProcessing,
                thinkingLevel = thinkingLevel,
                onThinkingLevelChanged = { newLevel ->
                    userPrefs.setAssistantThinkingLevel(newLevel)
                },
                onSend = { sendMessage(inputText) },
                onCancel = {
                    activeJob?.cancel()
                    isProcessing = false
                }
            )
        }
    }
}

@Composable
private fun AssistantSheetHeader(
    isProcessing: Boolean,
    onClose: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(AccentCyan.copy(alpha = 0.16f))
                    .border(1.dp, AccentCyan.copy(alpha = 0.4f), CircleShape),
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
                    text = "Trợ lý Orca",
                    color = TextPrimaryDark,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (isProcessing) "Đang chuẩn bị câu trả lời..." else "Sẵn sàng hỗ trợ ghi chú & tin nhắn",
                    color = if (isProcessing) AccentCyan else TextMutedDark,
                    fontSize = 11.5.sp
                )
            }
        }

        IconButton(onClick = onClose) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Đóng",
                tint = TextSecondaryDark
            )
        }
    }
}

@Composable
private fun UserChatBubble(message: AssistantChatMessage) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .clip(
                    RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = 16.dp,
                        bottomEnd = 4.dp
                    )
                )
                .background(AccentCyan.copy(alpha = 0.16f))
                .border(
                    1.dp,
                    AccentCyan.copy(alpha = 0.35f),
                    RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = 16.dp,
                        bottomEnd = 4.dp
                    )
                )
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(
                text = message.text,
                color = TextPrimaryDark,
                fontSize = 14.5.sp,
                lineHeight = 20.sp
            )
        }
    }
}

@Composable
private fun AssistantChatBubble(
    message: AssistantChatMessage,
    onRetry: () -> Unit
) {
    val state = message.state
    val step = state?.step ?: AssistantStep.IDLE
    val isStillStreaming = message.isStreaming

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(
                    RoundedCornerShape(
                        topStart = 4.dp,
                        topEnd = 16.dp,
                        bottomStart = 16.dp,
                        bottomEnd = 16.dp
                    )
                )
                .background(GlassSurfaceCard)
                .border(
                    1.dp,
                    GlassBorderSubtle,
                    RoundedCornerShape(
                        topStart = 4.dp,
                        topEnd = 16.dp,
                        bottomStart = 16.dp,
                        bottomEnd = 16.dp
                    )
                )
                .padding(14.dp)
        ) {
            // Header row with Icon and status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = AccentCyan,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Trợ lý AI",
                        color = AccentCyan,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Model name tag if completed
                val modelName = state?.activeUsedKey?.modelName
                if (!modelName.isNullOrBlank()) {
                    Text(
                        text = modelName,
                        color = TextMutedDark,
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Sub-ground Round 1 Status Indicator
            if (step == AssistantStep.STAGE_1_PACKING_INDEX ||
                step == AssistantStep.STAGE_2_ROUND_1_ROUTING ||
                step == AssistantStep.STAGE_3_LOCAL_RETRIEVAL
            ) {
                Round1ThinkingIndicator(step = step, description = state?.stepDescription.orEmpty())
            }

            // Reasoning / Thinking Accordion (Stage 4)
            if (message.thought.isNotBlank()) {
                ReasoningAccordion(thoughtText = message.thought)
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Streamed Answer Content
            if (message.text.isNotBlank()) {
                SelectionContainer {
                    Row {
                        Text(
                            text = message.text,
                            color = TextPrimaryDark,
                            fontSize = 14.5.sp,
                            lineHeight = 21.sp
                        )
                        if (isStillStreaming) {
                            StreamingCursor()
                        }
                    }
                }
            } else if (isStillStreaming &&
                (step == AssistantStep.STAGE_4_STREAMING_ANSWER || step == AssistantStep.STAGE_5_EXECUTING_ACTIONS)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = AccentCyan,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Đang nhả chữ câu trả lời...",
                        color = TextSecondaryDark,
                        fontSize = 13.sp
                    )
                }
            }

            // Pending action indicators during streaming or execution
            if (step == AssistantStep.STAGE_5_EXECUTING_ACTIONS) {
                Spacer(modifier = Modifier.height(10.dp))
                ActionPendingIndicator(description = state?.stepDescription.orEmpty())
            }

            // Executed actions summary badges
            if (state != null && state.executedActions.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                ExecutedActionsSummary(actions = state.executedActions)
            }

            // Render Local Note Cards from SHOW_NOTE (Retrieved directly from Room DB)
            if (state != null && state.shownNoteCards.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Ghi chú liên quan",
                    color = AccentCyan,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.shownNoteCards.forEach { localNote ->
                        LocalNoteCard(note = localNote)
                    }
                }
            }

            // Error display
            if (step == AssistantStep.ERROR || !state?.errorMessage.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                ErrorMessageCard(
                    errorMessage = state?.errorMessage ?: "Đã xảy ra lỗi không xác định",
                    onRetry = onRetry
                )
            }
        }
    }
}

@Composable
private fun Round1ThinkingIndicator(step: AssistantStep, description: String) {
    val infiniteTransition = rememberInfiniteTransition(label = "Pulsing")
    val alphaAnim by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(700),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Alpha"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(AccentCyan.copy(alpha = 0.08f))
            .border(1.dp, AccentCyan.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
            .padding(10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.alpha(alphaAnim)
        ) {
            Icon(
                imageVector = Icons.Default.Psychology,
                contentDescription = null,
                tint = AccentCyan,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (description.isNotBlank()) description else "Đang tìm thông tin liên quan...",
                color = AccentCyan,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun ActionPendingIndicator(description: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(AccentAmber.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(14.dp),
            color = AccentAmber,
            strokeWidth = 2.dp
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = description.ifBlank { "Đang cập nhật ghi chú..." },
            color = AccentAmber,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun ReasoningAccordion(thoughtText: String) {
    var isExpanded by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = GlassSurfaceInner),
        border = BorderStroke(1.dp, GlassBorderSubtle),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Lightbulb,
                        contentDescription = null,
                        tint = AccentAmber,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Quá trình suy luận",
                        color = AccentAmber,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = TextSecondaryDark,
                    modifier = Modifier.size(18.dp)
                )
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    Text(
                        text = thoughtText.trim(),
                        color = TextSecondaryDark,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@Composable
private fun StreamingCursor() {
    val infiniteTransition = rememberInfiniteTransition(label = "Cursor")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(400),
            repeatMode = RepeatMode.Reverse
        ),
        label = "CursorAlpha"
    )
    Text(
        text = " ▌",
        color = AccentCyan,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.alpha(alpha)
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExecutedActionsSummary(actions: List<NoteCommandResult>) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        actions.forEach { action ->
            val bgCol = if (action.isSuccess) AccentEmerald.copy(alpha = 0.14f) else AccentRose.copy(alpha = 0.14f)
            val borderCol = if (action.isSuccess) AccentEmerald.copy(alpha = 0.4f) else AccentRose.copy(alpha = 0.4f)
            val textCol = if (action.isSuccess) AccentEmerald else AccentRose
            val icon = if (action.isSuccess) Icons.Default.CheckCircle else Icons.Default.Warning

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(bgCol)
                    .border(1.dp, borderCol, RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = textCol,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = action.message.removePrefix("VIOLATION: "),
                        color = textCol,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

/**
 * Local Room DB Card rendered directly beneath assistant chat response.
 * Completely local, zero byte egress for private notes.
 */
@Composable
private fun LocalNoteCard(note: SharedNoteEntity) {
    val context = LocalContext.current
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }
    val formattedDate = remember(note.updatedAt) {
        try {
            dateFormat.format(Date(note.updatedAt))
        } catch (e: Exception) {
            "${note.updatedAt}"
        }
    }

    val cardBorder = if (note.isPrivate) {
        BorderStroke(1.dp, AccentCyan.copy(alpha = 0.45f))
    } else {
        BorderStroke(1.dp, GlassBorderSubtle)
    }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = GlassSurfaceInner),
        border = cardBorder,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // Header: Category label & Copy button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = null,
                        tint = AccentCyan,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
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
                    } else {
                        Text(
                            text = "GHI CHÚ #${note.id}",
                            color = TextSecondaryDark,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                IconButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Note Content", "${note.title}\n\n${note.content}")
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "Đã sao chép nội dung ghi chú", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Sao chép",
                        tint = TextSecondaryDark,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Title
            Text(
                text = note.title,
                color = TextPrimaryDark,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Full content
            SelectionContainer {
                Text(
                    text = note.content.ifBlank { "(Ghi chú chưa có nội dung)" },
                    color = if (note.content.isBlank()) TextMutedDark else TextSecondaryDark,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Cập nhật lúc: $formattedDate",
                color = TextMutedDark,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun ErrorMessageCard(errorMessage: String, onRetry: () -> Unit) {
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = AccentRose.copy(alpha = 0.12f)),
        border = BorderStroke(1.dp, AccentRose.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Error,
                    contentDescription = null,
                    tint = AccentRose,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = errorMessage,
                    color = AccentRose,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = onRetry,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        tint = AccentCyan,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Thử lại", color = AccentCyan, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun EmptyConversationView(onSelectSuggestion: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(AccentCyan.copy(alpha = 0.14f))
                .border(1.dp, AccentCyan.copy(alpha = 0.35f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = AccentCyan,
                modifier = Modifier.size(26.dp)
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "Trợ lý Ghi chú & Tin nhắn",
            color = TextPrimaryDark,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Hỏi nhanh về tin nhắn gần đây, tìm lại hoặc cập nhật ghi chú cá nhân — nội dung riêng tư luôn được giữ an toàn trên máy.",
            color = TextSecondaryDark,
            fontSize = 13.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            lineHeight = 18.sp
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Suggested Queries
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Gợi ý câu hỏi:",
                color = TextMutedDark,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )

            val suggestions = listOf(
                "Tóm tắt các tin nhắn quan trọng trong 24 giờ qua",
                "Xem lại ghi chú riêng tư của tôi",
                "Tạo ghi chú mới: Lịch công tác tuần này",
                "Tổng quan danh mục kho ghi chú hiện có"
            )

            suggestions.forEach { prompt ->
                Surface(
                    onClick = { onSelectSuggestion(prompt) },
                    shape = RoundedCornerShape(12.dp),
                    color = GlassSurfaceInner,
                    border = BorderStroke(1.dp, GlassBorderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lightbulb,
                            contentDescription = null,
                            tint = AccentCyan,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = prompt,
                            color = TextPrimaryDark,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AssistantInputBar(
    inputText: String,
    onInputChanged: (String) -> Unit,
    isProcessing: Boolean,
    thinkingLevel: AssistantThinkingLevel,
    onThinkingLevelChanged: (AssistantThinkingLevel) -> Unit,
    onSend: () -> Unit,
    onCancel: () -> Unit
) {
    Surface(
        color = GlassSurfaceDark,
        modifier = Modifier
            .fillMaxWidth()
            .imePadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            // Thinking level chip selector row (⚡ Nhanh, ⚖ Cân bằng, 🧠 Sâu sắc)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ThinkingLevelChip(
                    label = "⚡ Nhanh",
                    selected = thinkingLevel == AssistantThinkingLevel.FAST,
                    onClick = { onThinkingLevelChanged(AssistantThinkingLevel.FAST) },
                    modifier = Modifier.weight(1f)
                )
                ThinkingLevelChip(
                    label = "⚖ Cân bằng",
                    selected = thinkingLevel == AssistantThinkingLevel.BALANCED,
                    onClick = { onThinkingLevelChanged(AssistantThinkingLevel.BALANCED) },
                    modifier = Modifier.weight(1f)
                )
                ThinkingLevelChip(
                    label = "🧠 Sâu sắc",
                    selected = thinkingLevel == AssistantThinkingLevel.DEEP,
                    onClick = { onThinkingLevelChanged(AssistantThinkingLevel.DEEP) },
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = onInputChanged,
                    placeholder = {
                        Text(
                            text = "Hỏi trợ lý về tin nhắn, ghi chú...",
                            color = TextMutedDark,
                            fontSize = 13.5.sp
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimaryDark,
                        unfocusedTextColor = TextPrimaryDark,
                        focusedContainerColor = GlassSurfaceInner,
                        unfocusedContainerColor = GlassSurfaceInner,
                        focusedBorderColor = AccentCyan,
                        unfocusedBorderColor = GlassBorderSubtle
                    ),
                    shape = RoundedCornerShape(22.dp),
                    maxLines = 4,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                )

                if (isProcessing) {
                    IconButton(
                        onClick = onCancel,
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(AccentRose)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = "Hủy phản hồi",
                            tint = PrimaryWhite,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                } else {
                    IconButton(
                        onClick = onSend,
                        enabled = inputText.isNotBlank(),
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(if (inputText.isNotBlank()) AccentCyan else GlassSurfaceInner)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Gửi",
                            tint = if (inputText.isNotBlank()) BackgroundDark else TextMutedDark,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ThinkingLevelChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (selected) AccentCyan.copy(alpha = 0.18f) else GlassSurfaceInner.copy(alpha = 0.6f)
    val borderColor = if (selected) AccentCyan else GlassBorderSubtle
    val textColor = if (selected) AccentCyan else TextSecondaryDark
    val fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .border(BorderStroke(1.dp, borderColor), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 5.dp, horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 11.5.sp,
            fontWeight = fontWeight,
            maxLines = 1
        )
    }
}
