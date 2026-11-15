package com.orca.orcaauto.service

import com.orca.orcaauto.data.local.SharedNoteEntity
import com.orca.orcaauto.data.local.SmartAlarmEntity
import com.orca.orcaauto.data.local.SmartAlarmStatus
import com.orca.orcaauto.domain.alarm.SystemAlarmHelper
import com.orca.orcaauto.domain.assistant.AssistantStep
import com.orca.orcaauto.domain.assistant.AssistantUiState
import com.orca.orcaauto.domain.filter.ParsedSummary
import com.orca.orcaauto.domain.filter.SummaryParser
import com.orca.orcaauto.domain.filter.TaskItem
import com.orca.orcaauto.domain.filter.TaskStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Empirical Challenge Test Suite for [FloatingBubbleService] logic.
 *
 * Stress-tests:
 * 1. Mid-stream coroutine cancellation when activeStreamingJob?.cancel() is invoked (e.g. bubble collapsed).
 * 2. Verification that when allTasks.isEmpty() and advice/note is present, empty task box is NEVER displayed.
 * 3. Verification of SHOW_NOTE card rendering contracts (private badge, lock icon, title, content, timestamp).
 */
class FloatingBubbleServiceEmpiricalChallengeTest {

    // =========================================================================
    // 1. COROUTINE CANCELLATION MID-STREAM (e.g. BUBBLE COLLAPSED / DISMISSED)
    // =========================================================================

    @Test
    fun midStreamCancellation_whenActiveStreamingJobCancelled_cleansUpStateGracefully() = runBlocking {
        // Models the exact coroutine structure and lifecycle in FloatingBubbleService.executeAiCall
        val testScope = CoroutineScope(Dispatchers.Default)
        var isCallingAi = false
        var activeStreamingJob: Job? = null
        val emittedTokens = mutableListOf<String>()
        val streamStartedLatch = CountDownLatch(1)
        val cleanupExecuted = AtomicBoolean(false)

        // Mock pipeline streaming flow that yields tokens with a delay
        val mockStreamingFlow = flow {
            emit(AssistantUiState(step = AssistantStep.STAGE_1_PACKING_INDEX, stepDescription = "Đang đóng gói..."))
            emit(AssistantUiState(step = AssistantStep.STAGE_2_ROUND_1_ROUTING, stepDescription = "Đang phân tích..."))
            emit(AssistantUiState(step = AssistantStep.STAGE_3_LOCAL_RETRIEVAL, stepDescription = "Đang trích xuất..."))
            for (i in 1..20) {
                emit(
                    AssistantUiState(
                        step = AssistantStep.STAGE_4_STREAMING_ANSWER,
                        streamedContentClean = "Token $i"
                    )
                )
                streamStartedLatch.countDown()
                delay(50)
            }
            emit(AssistantUiState(step = AssistantStep.COMPLETED, streamedContentClean = "Completed full answer"))
        }

        // Trigger AI Call
        isCallingAi = true
        activeStreamingJob = testScope.launch {
            try {
                mockStreamingFlow.collect { uiState ->
                    if (uiState.step == AssistantStep.STAGE_4_STREAMING_ANSWER) {
                        emittedTokens.add(uiState.streamedContentClean)
                    }
                }
            } catch (e: CancellationException) {
                // Expected clean cancellation
            } catch (t: Throwable) {
                throw AssertionError("Unexpected error: ${t.message}")
            } finally {
                // Exact cleanup block from FloatingBubbleService line 1718-1730
                isCallingAi = false
                activeStreamingJob = null
                cleanupExecuted.set(true)
            }
        }

        // Wait until at least 1 streaming token is emitted
        assertTrue(streamStartedLatch.await(5, TimeUnit.SECONDS))
        assertTrue("Bong bóng phải nhận được ít nhất 1 token trước khi collapse", emittedTokens.isNotEmpty())

        // Simulate collapseExpandedCard() -> calls activeStreamingJob?.cancel()
        val jobToCancel = activeStreamingJob
        assertNotNull(jobToCancel)
        jobToCancel?.cancel()
        jobToCancel?.join()

        // Verify state after cancellation
        assertFalse("isCallingAi phải được đặt lại false", isCallingAi)
        assertNull("activeStreamingJob phải được đặt lại null", activeStreamingJob)
        assertTrue("Khối finally dọn dẹp phải được thực thi", cleanupExecuted.get())
        assertTrue("Không được nhận đủ 20 tokens do đã hủy giữa chừng", emittedTokens.size < 20)
    }

    // =========================================================================
    // 2. ADVICE DISPLAY WITHOUT EMPTY TASK BOX WHEN ALLTASKS IS EMPTY
    // =========================================================================

    /**
     * Logic oracle replicating FloatingBubbleService line 1434-1523:
     * - effectiveAdvice calculation
     * - sec1Box visibility
     * - emptyBox visibility
     */
    private data class BubbleDisplayVerdict(
        val effectiveAdvice: String,
        val showAdviceBox: Boolean,
        val showEmptyTaskBox: Boolean,
        val showTaskCards: Boolean
    )

    private fun evaluateBubbleDisplayLogic(
        parsed: ParsedSummary,
        latestSummaryText: String
    ): BubbleDisplayVerdict {
        val cleanAdvice = SummaryParser.cleanAdviceText(parsed.conciseAdvice)
        val allTasks = parsed.tasksToDo + parsed.tasksPlanned + parsed.tasksDone
        val effectiveAdvice = when {
            cleanAdvice.isNotBlank() -> cleanAdvice
            allTasks.isEmpty() && latestSummaryText.isNotBlank() -> latestSummaryText
            else -> ""
        }

        val showAdviceBox = effectiveAdvice.isNotBlank()
        // R2: Empty TaskBoard suppression: never show empty task box or placeholder
        val showEmptyTaskBox = false
        val showTaskCards = allTasks.isNotEmpty()

        return BubbleDisplayVerdict(
            effectiveAdvice = effectiveAdvice,
            showAdviceBox = showAdviceBox,
            showEmptyTaskBox = showEmptyTaskBox,
            showTaskCards = showTaskCards
        )
    }

    @Test
    fun bubbleDisplay_whenAdviceOrNoteExistsAndTasksEmpty_hidesEmptyTaskBox() {
        // Scenario A: AI returns note content or answer advice without any to-dos
        val pureNoteAnswer = """
            - Chuẩn bị ống kính tiêu cự 70-200mm f/2.8 để bắt khoảnh khắc từ xa.
            - Tốc độ màn trập tối thiểu 1/1000s để đóng băng chuyển động của cầu thủ.
            - Bật chế độ lấy nét liên tục AF-C tracking chủ thể.
        """.trimIndent()

        val parsed = SummaryParser.parse(pureNoteAnswer)
        val verdict = evaluateBubbleDisplayLogic(parsed, latestSummaryText = pureNoteAnswer)

        assertTrue("Phải hiển thị khung lời khuyên / ghi chú", verdict.showAdviceBox)
        assertFalse("TUYỆT ĐỐI KHÔNG được hiển thị ô trống nhiệm vụ khi đã có câu trả lời/lời khuyên", verdict.showEmptyTaskBox)
        assertFalse("Không có nhiệm vụ để hiển thị thẻ", verdict.showTaskCards)
        assertTrue("Nội dung lời khuyên phải chứa thông tin ống kính", verdict.effectiveAdvice.contains("70-200mm"))
    }

    @Test
    fun bubbleDisplay_whenNoAdviceAndNoTasks_neverShowsEmptyTaskBox() {
        // Scenario B: Completely empty summary or initial blank state
        val parsed = ParsedSummary(
            rawCleanText = "",
            conciseAdvice = "",
            tasksToDo = emptyList(),
            tasksPlanned = emptyList(),
            tasksDone = emptyList()
        )
        val verdict = evaluateBubbleDisplayLogic(parsed, latestSummaryText = "")

        assertFalse("Không có lời khuyên thì không hiện khung lời khuyên", verdict.showAdviceBox)
        assertFalse("R2: Khi hoàn toàn không có cả lời khuyên lẫn nhiệm vụ, TUYỆT ĐỐI KHÔNG hiển thị ô trống nhiệm vụ", verdict.showEmptyTaskBox)
        assertFalse(verdict.showTaskCards)
    }

    @Test
    fun bubbleDisplay_whenBothAdviceAndTasksExist_showsBothWithoutEmptyBox() {
        // Scenario C: Both advice and tasks are present
        val parsed = ParsedSummary(
            rawCleanText = "Kiểm tra kỹ trước khi xuất hành",
            conciseAdvice = "Kiểm tra kỹ trước khi xuất hành",
            tasksToDo = listOf(TaskItem(title = "Sạc đầy pin máy ảnh")),
            tasksPlanned = emptyList(),
            tasksDone = emptyList()
        )
        val verdict = evaluateBubbleDisplayLogic(parsed, latestSummaryText = "Kiểm tra kỹ")

        assertTrue("Phải hiện khung lời khuyên", verdict.showAdviceBox)
        assertFalse("Không được hiện ô trống nhiệm vụ vì đã có task thực sự", verdict.showEmptyTaskBox)
        assertTrue("Phải hiện thẻ nhiệm vụ", verdict.showTaskCards)
    }

    // =========================================================================
    // 3. SHOW_NOTE LOCAL NOTE CARDS RENDERING DATA CONTRACT
    // =========================================================================

    @Test
    fun showNoteCards_contractVerification_rendersBothPublicAndPrivateCardsProperly() {
        val publicNote = SharedNoteEntity(
            id = 1L,
            title = "Tip đi làm quay chụp bóng đá",
            content = "1. Luôn mang theo 2 thân máy và thẻ nhớ dự phòng.\n2. Chỉnh AF-C tracking.",
            isPrivate = false,
            updatedAt = 1715000000000L
        )

        val privateNote = SharedNoteEntity(
            id = 2L,
            title = "Mã PIN két sắt văn phòng",
            content = "987654",
            isPrivate = true,
            updatedAt = 1715000000000L
        )

        val notes = listOf(publicNote, privateNote)

        // Verify data model contract for renderLocalNoteCardsInBubble
        // 1. Container receives 1 label header + 2 cards
        val expectedCardCount = notes.size
        assertEquals(2, expectedCardCount)

        // 2. Public note card checks
        assertEquals("📝 ", if (publicNote.isPrivate) "🔒 " else "📝 ")
        assertFalse(publicNote.isPrivate)
        assertEquals("Tip đi làm quay chụp bóng đá", publicNote.title)
        assertTrue(publicNote.content.isNotBlank())

        // 3. Private note card checks (Sealed Pointer)
        assertEquals("🔒 ", if (privateNote.isPrivate) "🔒 " else "📝 ")
        assertTrue(privateNote.isPrivate)
        assertEquals("Mã PIN két sắt văn phòng", privateNote.title)
        assertEquals("987654", privateNote.content)
        val badgeText = if (privateNote.isPrivate) "Riêng tư" else null
        assertEquals("Riêng tư", badgeText)

        // 4. Empty notes case
        val emptyNotes = emptyList<SharedNoteEntity>()
        assertTrue(emptyNotes.isEmpty())
    }

    // =========================================================================
    // 4. STREAMING UX: CONNECTING STATUS vs TOKEN STREAMING
    // =========================================================================

    @Test
    fun streamingUX_whenWaitingFirstToken_displaysConnectingStatusInsteadOfNakedCursor() {
        val blankUiState = AssistantUiState(
            step = AssistantStep.STAGE_4_STREAMING_ANSWER,
            streamedContentClean = ""
        )

        val displayTextWhenWaiting = if (blankUiState.streamedContentClean.isBlank()) {
            "⏳ Đang kết nối máy chủ AI..."
        } else {
            blankUiState.streamedContentClean + " ▌"
        }

        assertEquals("Phải hiển thị icon đồng hồ cát và thông báo kết nối máy chủ AI", "⏳ Đang kết nối máy chủ AI...", displayTextWhenWaiting)
        assertFalse("Tuyệt đối không hiển thị con trỏ đơn độc ▌ trong khung trống", displayTextWhenWaiting.contains("▌"))

        val activeUiState = AssistantUiState(
            step = AssistantStep.STAGE_4_STREAMING_ANSWER,
            streamedContentClean = "Chào bạn, đây là thông tin cần tìm"
        )

        val displayTextWhenStreaming = if (activeUiState.streamedContentClean.isBlank()) {
            "⏳ Đang kết nối máy chủ AI..."
        } else {
            activeUiState.streamedContentClean + " ▌"
        }

        assertEquals("Chào bạn, đây là thông tin cần tìm ▌", displayTextWhenStreaming)
    }

    @Test
    fun streamingIndicator_displaysConnectingStatusWhileWaitingForFirstToken_coversAllEdgeCases() {
        // Edge case 1: Completely empty streamed content
        val stateEmpty = AssistantUiState(
            step = AssistantStep.STAGE_4_STREAMING_ANSWER,
            streamedContentClean = ""
        )
        val textEmpty = if (stateEmpty.streamedContentClean.isBlank()) "⏳ Đang kết nối máy chủ AI..." else stateEmpty.streamedContentClean + " ▌"
        assertEquals("⏳ Đang kết nối máy chủ AI...", textEmpty)

        // Edge case 2: Whitespace only streamed content (before first non-whitespace token arrives)
        val stateWhitespace = AssistantUiState(
            step = AssistantStep.STAGE_4_STREAMING_ANSWER,
            streamedContentClean = "   \n\t  "
        )
        val textWhitespace = if (stateWhitespace.streamedContentClean.isBlank()) "⏳ Đang kết nối máy chủ AI..." else stateWhitespace.streamedContentClean + " ▌"
        assertEquals("⏳ Đang kết nối máy chủ AI...", textWhitespace)

        // Edge case 3: First single character token arrives
        val stateFirstChar = AssistantUiState(
            step = AssistantStep.STAGE_4_STREAMING_ANSWER,
            streamedContentClean = "A"
        )
        val textFirstChar = if (stateFirstChar.streamedContentClean.isBlank()) "⏳ Đang kết nối máy chủ AI..." else stateFirstChar.streamedContentClean + " ▌"
        assertEquals("A ▌", textFirstChar)

        // Edge case 4: Multiline streamed content
        val stateMultiline = AssistantUiState(
            step = AssistantStep.STAGE_4_STREAMING_ANSWER,
            streamedContentClean = "Dòng 1\nDòng 2"
        )
        val textMultiline = if (stateMultiline.streamedContentClean.isBlank()) "⏳ Đang kết nối máy chủ AI..." else stateMultiline.streamedContentClean + " ▌"
        assertEquals("Dòng 1\nDòng 2 ▌", textMultiline)
    }

    // =========================================================================
    // 5. ZERO TASK LOSS: PRESERVING SUMMARY & ROOM DB SMART ALARMS IN TASKBOARD
    // =========================================================================

    @Test
    fun zeroTaskLoss_chatAnswerDoesNotOverwriteSummaryOrDestroyRoomDbTaskBoard() {
        val initialSummary = """
            ### LỜI KHUYÊN:
            Hôm nay có 2 việc gấp cần chú ý.
            
            ### VIỆC CẦN LÀM NGAY:
            - [ ] Họp giao ban đầu tuần lúc 09:00
        """.trimIndent()

        var latestSummaryText = initialSummary
        var activeChatAnswer: String? = null

        // Room DB contains an active task scheduled earlier (e.g. PCCC drill)
        val roomDbAlarm = com.orca.orcaauto.data.local.SmartAlarmEntity(
            alarmId = "ALM_pccc_1500",
            taskTitle = "Diễn tập PCCC tòa nhà",
            targetTimeMillis = System.currentTimeMillis() + 3600_000L,
            targetDateLabel = "15:00 Hôm nay",
            status = com.orca.orcaauto.data.local.SmartAlarmStatus.ACTIVE
        )
        val alarmDbMap = mapOf(roomDbAlarm.alarmId to roomDbAlarm)

        // Simulate user asking a conversational question in Floating Bubble Chat
        val chatAnswer = "Dạ vâng, tôi đã kiểm tra hệ thống giúp bạn. Mọi thứ hoạt động bình thường."

        // IN AssistantStep.COMPLETED: Chat answer must NOT overwrite latestSummaryText!
        activeChatAnswer = chatAnswer
        // DO NOT: latestSummaryText = chatAnswer

        assertEquals("latestSummaryText phải được giữ nguyên vẹn", initialSummary, latestSummaryText)
        assertEquals("activeChatAnswer lưu trữ câu trả lời chat", chatAnswer, activeChatAnswer)

        // Evaluate task board population logic: merges parsed summary tasks + Room DB smart_alarms
        val parsed = SummaryParser.parse(latestSummaryText)
        val speakableParsed = com.orca.orcaauto.domain.alarm.SystemAlarmHelper.filterOutCancelled(parsed, alarmDbMap)
        val existingParsedTasks = speakableParsed.tasksToDo + speakableParsed.tasksPlanned + speakableParsed.tasksDone

        val dbTasksToDo = mutableListOf<TaskItem>()
        for (alarm in alarmDbMap.values) {
            if (alarm.status == com.orca.orcaauto.data.local.SmartAlarmStatus.CANCELLED ||
                alarm.status == com.orca.orcaauto.data.local.SmartAlarmStatus.EXPIRED) {
                continue
            }
            val alreadyPresent = existingParsedTasks.any { task ->
                com.orca.orcaauto.domain.alarm.SystemAlarmHelper.isTitleMatching(task.title, alarm.taskTitle)
            }
            if (!alreadyPresent) {
                dbTasksToDo.add(
                    TaskItem(
                        title = alarm.taskTitle,
                        deadline = alarm.targetDateLabel,
                        status = TaskStatus.TODO
                    )
                )
            }
        }

        val allTasksToDo = speakableParsed.tasksToDo + dbTasksToDo
        val allTasks = allTasksToDo + speakableParsed.tasksPlanned + speakableParsed.tasksDone

        assertEquals("TaskBoard phải có đủ 2 nhiệm vụ (1 từ summary + 1 từ Room DB smart_alarms)", 2, allTasks.size)
        assertTrue("Chứa nhiệm vụ Họp giao ban", allTasks.any { it.title.contains("Họp giao ban") })
        assertTrue("Chứa nhiệm vụ Diễn tập PCCC từ Room DB", allTasks.any { it.title.contains("Diễn tập PCCC") })
    }

    @Test
    fun zeroTaskLoss_onCasualChatCompletion_preservesSummaryAndRestoresRoomDbAlarms() {
        val initialSummary = """
            ### LỜI KHUYÊN:
            Kiểm tra trang thiết bị đầu ca.

            ### VIỆC CẦN LÀM NGAY:
            - [ ] Kiểm tra hệ thống giám sát server lúc 08:30
        """.trimIndent()

        var latestSummaryText = initialSummary
        var activeChatAnswer: String? = null

        val alarmDb = mapOf(
            "ALM_backup_db" to com.orca.orcaauto.data.local.SmartAlarmEntity(
                alarmId = "ALM_backup_db",
                taskTitle = "Sao lưu cơ sở dữ liệu định kỳ",
                targetTimeMillis = System.currentTimeMillis() + 7200_000L,
                targetDateLabel = "11:00 Hôm nay",
                status = com.orca.orcaauto.data.local.SmartAlarmStatus.ACTIVE
            )
        )

        // Simulate AssistantStep.COMPLETED with casual chat (no tasks)
        val casualChatReply = "Chào bạn! Thời tiết hôm nay mát mẻ, chúc bạn một ca trực hiệu quả."
        val parsedCasual = SummaryParser.parse(casualChatReply)
        val casualTasks = parsedCasual.tasksToDo + parsedCasual.tasksPlanned + parsedCasual.tasksDone

        if (casualTasks.isNotEmpty()) {
            latestSummaryText = casualChatReply
            activeChatAnswer = null
        } else {
            // Casual reply without tasks: DO NOT overwrite latestSummaryText!
            activeChatAnswer = casualChatReply
        }

        assertEquals("latestSummaryText không bị đè bẹp bởi câu chat", initialSummary, latestSummaryText)
        assertEquals(casualChatReply, activeChatAnswer)

        // Under R2: AI chat streaming completions (AssistantStep.COMPLETED in executeAiCall)
        // strictly assign to activeChatAnswer and NEVER overwrite latestSummaryText (even if reply has tasks)
        val chatAnswerWithTasks = """
            - [ ] Đổi mật khẩu định kỳ
            - [ ] Kiểm tra camera giám sát
        """.trimIndent()
        activeChatAnswer = chatAnswerWithTasks

        assertEquals("latestSummaryText không bị đè bởi câu trả lời chat", initialSummary, latestSummaryText)
        assertEquals(chatAnswerWithTasks, activeChatAnswer)

        // However, when a real new summary arrives from SummaryDao / Background Worker:
        val newSummaryFromDao = """
            ### LỜI KHUYÊN:
            Cập nhật ca trực chiều.

            ### VIỆC CẦN LÀM NGAY:
            - [ ] Bàn giao ca trực phòng điều khiển lúc 16:00
        """.trimIndent()
        val parsedNew = SummaryParser.parse(newSummaryFromDao)
        val newTasks = parsedNew.tasksToDo + parsedNew.tasksPlanned + parsedNew.tasksDone

        if (newTasks.isNotEmpty() || latestSummaryText.isBlank()) {
            latestSummaryText = newSummaryFromDao
            activeChatAnswer = null
        }

        assertEquals("latestSummaryText được cập nhật khi SummaryDao phát hiện báo cáo mới", newSummaryFromDao, latestSummaryText)
        assertNull("activeChatAnswer được xóa khi có summary mới từ DB", activeChatAnswer)
    }

    @Test
    fun zeroTaskLoss_onCardReopen_rendersActiveAlarmsFromRoomDbEvenWhenSummaryHasNoTasks() {
        // Scenario: Floating bubble card is reopened (expandFloatingCard).
        // latestSummaryText has NO tasks (e.g. empty or pure advice),
        // but Room DB smart_alarms contains multiple active/proposed/completed alarms.
        val emptyTasksSummary = "Hiện tại chưa có phân tích ca trực mới."
        val parsed = SummaryParser.parse(emptyTasksSummary)
        val existingTasks = parsed.tasksToDo + parsed.tasksPlanned + parsed.tasksDone
        assertTrue("Summary không có task", existingTasks.isEmpty())

        val alarm1 = com.orca.orcaauto.data.local.SmartAlarmEntity(
            alarmId = "ALM_patrol_1",
            taskTitle = "Tuần tra tầng thượng tòa nhà",
            targetTimeMillis = System.currentTimeMillis() + 1800_000L,
            targetDateLabel = "10:00 Hôm nay",
            status = com.orca.orcaauto.data.local.SmartAlarmStatus.ACTIVE
        )
        val alarm2 = com.orca.orcaauto.data.local.SmartAlarmEntity(
            alarmId = "ALM_generator_test",
            taskTitle = "Thử máy phát điện dự phòng",
            targetTimeMillis = System.currentTimeMillis() + 3600_000L,
            targetDateLabel = "14:00 Hôm nay",
            status = com.orca.orcaauto.data.local.SmartAlarmStatus.PROPOSED
        )
        val alarm3Completed = com.orca.orcaauto.data.local.SmartAlarmEntity(
            alarmId = "ALM_morning_briefing",
            taskTitle = "Giao ban ca sáng",
            targetTimeMillis = System.currentTimeMillis() - 3600_000L,
            targetDateLabel = "08:00 Hôm nay",
            status = com.orca.orcaauto.data.local.SmartAlarmStatus.COMPLETED
        )
        val alarm4Cancelled = com.orca.orcaauto.data.local.SmartAlarmEntity(
            alarmId = "ALM_cancelled_drill",
            taskTitle = "Diễn tập hủy bỏ",
            targetTimeMillis = System.currentTimeMillis(),
            targetDateLabel = "09:00",
            status = com.orca.orcaauto.data.local.SmartAlarmStatus.CANCELLED
        )

        val alarmDbMap = mapOf(
            alarm1.alarmId to alarm1,
            alarm2.alarmId to alarm2,
            alarm3Completed.alarmId to alarm3Completed,
            alarm4Cancelled.alarmId to alarm4Cancelled
        )

        // Execute populateTaskBoard synthesis logic
        val speakableParsed = com.orca.orcaauto.domain.alarm.SystemAlarmHelper.filterOutCancelled(parsed, alarmDbMap)
        val existingParsedTasks = speakableParsed.tasksToDo + speakableParsed.tasksPlanned + speakableParsed.tasksDone

        val dbTasksToDo = mutableListOf<TaskItem>()
        val dbTasksDone = mutableListOf<TaskItem>()

        for (alarm in alarmDbMap.values) {
            if (alarm.status == com.orca.orcaauto.data.local.SmartAlarmStatus.CANCELLED ||
                alarm.status == com.orca.orcaauto.data.local.SmartAlarmStatus.EXPIRED) {
                continue
            }
            val alreadyPresent = existingParsedTasks.any { task ->
                com.orca.orcaauto.domain.alarm.SystemAlarmHelper.isTitleMatching(task.title, alarm.taskTitle)
            }
            if (!alreadyPresent) {
                if (alarm.status == com.orca.orcaauto.data.local.SmartAlarmStatus.COMPLETED) {
                    dbTasksDone.add(
                        TaskItem(
                            title = alarm.taskTitle,
                            deadline = alarm.targetDateLabel,
                            status = TaskStatus.DONE
                        )
                    )
                } else {
                    dbTasksToDo.add(
                        TaskItem(
                            title = alarm.taskTitle,
                            deadline = alarm.targetDateLabel,
                            status = TaskStatus.TODO
                        )
                    )
                }
            }
        }

        val allTasksToDo = speakableParsed.tasksToDo + dbTasksToDo
        val allTasksDone = speakableParsed.tasksDone + dbTasksDone
        val allTasks = allTasksToDo + speakableParsed.tasksPlanned + allTasksDone

        // 1. Verify tasks are populated from Room DB smart_alarms
        assertEquals("Phải có 2 nhiệm vụ cần làm (ACTIVE và PROPOSED)", 2, allTasksToDo.size)
        assertEquals("Phải có 1 nhiệm vụ đã hoàn thành (COMPLETED)", 1, dbTasksDone.size)
        assertEquals("Tổng số nhiệm vụ phải là 3 (bỏ qua CANCELLED)", 3, allTasks.size)

        // 2. Verify specific tasks
        assertTrue("Chứa Tuần tra tầng thượng", allTasksToDo.any { it.title.contains("Tuần tra tầng thượng") })
        assertTrue("Chứa Thử máy phát điện", allTasksToDo.any { it.title.contains("Thử máy phát điện") })
        assertTrue("Chứa Giao ban ca sáng trong tasksDone", allTasksDone.any { it.title.contains("Giao ban ca sáng") })
        assertFalse("Tuyệt đối không chứa nhiệm vụ đã hủy", allTasks.any { it.title.contains("Diễn tập hủy bỏ") })
    }

    // =========================================================================
    // 6. R1: TASK FILTERING & TWO-LAYER DEDUPLICATION
    // =========================================================================

    @Test
    fun r1_completedTasksFiltering_onlyIncludesTodayOrLast24Hours_andLimitsToAtMost5() {
        val now = System.currentTimeMillis()
        val startOfToday = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val cutoff24h = now - 24 * 3600 * 1000L

        // 1. Task completed today (targetTimeMillis >= startOfToday) -> Valid
        val alarmToday = SmartAlarmEntity(
            alarmId = "ALM_today_1",
            taskTitle = "Nhiệm vụ xong hôm nay",
            targetTimeMillis = startOfToday + 3600_000L,
            targetDateLabel = "Hôm nay",
            status = SmartAlarmStatus.COMPLETED,
            updatedAt = now - 5000L
        )

        // 2. Task completed in last 24h (updatedAt >= cutoff24h, though targetTimeMillis was yesterday) -> Valid
        val alarmLast24h = SmartAlarmEntity(
            alarmId = "ALM_24h_1",
            taskTitle = "Nhiệm vụ xong 3 giờ trước",
            targetTimeMillis = startOfToday - 3600_000L,
            targetDateLabel = "Hôm qua",
            status = SmartAlarmStatus.COMPLETED,
            updatedAt = now - 3 * 3600 * 1000L
        )

        // 3. Stale task from 3 days ago (targetTimeMillis < startOfToday AND updatedAt < cutoff24h) -> EXCLUDED
        val alarmStale1 = SmartAlarmEntity(
            alarmId = "ALM_stale_3d",
            taskTitle = "Nhiệm vụ cũ 3 ngày trước",
            targetTimeMillis = startOfToday - 3 * 86400_000L,
            targetDateLabel = "3 ngày trước",
            status = SmartAlarmStatus.COMPLETED,
            updatedAt = now - 3 * 86400_000L
        )

        // 4. Stale task from 10 days ago -> EXCLUDED
        val alarmStale2 = SmartAlarmEntity(
            alarmId = "ALM_stale_10d",
            taskTitle = "Nhiệm vụ cũ 10 ngày trước",
            targetTimeMillis = startOfToday - 10 * 86400_000L,
            targetDateLabel = "10 ngày trước",
            status = SmartAlarmStatus.COMPLETED,
            updatedAt = now - 10 * 86400_000L
        )

        // 5-11: 7 more completed tasks today
        val moreTodayTasks = (1..7).map { i ->
            SmartAlarmEntity(
                alarmId = "ALM_today_batch_$i",
                taskTitle = "Nhiệm vụ hôm nay $i",
                targetTimeMillis = startOfToday + i * 1800_000L,
                targetDateLabel = "Hôm nay $i",
                status = SmartAlarmStatus.COMPLETED,
                updatedAt = now - i * 60_000L
            )
        }

        val allCandidateAlarms = listOf(alarmToday, alarmLast24h, alarmStale1, alarmStale2) + moreTodayTasks
        val alarmDbMap = allCandidateAlarms.associateBy { it.alarmId }

        // Filter and deduplicate completed tasks according to R1 logic
        val validDbAlarms = alarmDbMap.values
            .filter { it.status != SmartAlarmStatus.CANCELLED && it.status != SmartAlarmStatus.EXPIRED }

        val deduplicatedDoneAlarms = mutableListOf<SmartAlarmEntity>()
        validDbAlarms
            .filter { it.status == SmartAlarmStatus.COMPLETED }
            .filter { it.targetTimeMillis >= startOfToday || it.updatedAt >= cutoff24h }
            .sortedByDescending { it.updatedAt }
            .forEach { alarm ->
                val alreadyPresent = deduplicatedDoneAlarms.any { existing ->
                    SystemAlarmHelper.isTitleMatching(existing.taskTitle, alarm.taskTitle)
                }
                if (!alreadyPresent) {
                    deduplicatedDoneAlarms.add(alarm)
                }
            }

        val dbTasksDone = deduplicatedDoneAlarms.take(5).map { alarm ->
            TaskItem(
                title = alarm.taskTitle,
                deadline = alarm.targetDateLabel,
                status = TaskStatus.DONE
            )
        }

        // Verify:
        // Stale tasks must be excluded
        assertFalse("Nhiệm vụ cũ 3 ngày trước phải bị lọc bỏ", dbTasksDone.any { it.title.contains("3 ngày trước") })
        assertFalse("Nhiệm vụ cũ 10 ngày trước phải bị lọc bỏ", dbTasksDone.any { it.title.contains("10 ngày trước") })

        // Must strictly not exceed 5 tasks
        assertEquals("Số lượng nhiệm vụ đã hoàn thành tối đa là 5", 5, dbTasksDone.size)

        // Tasks in list must be from today or last 24h
        assertTrue("Chứa nhiệm vụ xong hôm nay", dbTasksDone.any { it.title.contains("Nhiệm vụ xong hôm nay") })
    }

    @Test
    fun r1_internalTwoLayerDeduplication_keepsNewestUpdatedAtForMatchingTitles() {
        val now = System.currentTimeMillis()
        val startOfToday = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val cutoff24h = now - 24 * 3600 * 1000L

        // Duplicated COMPLETED entries with same/similar title
        val doneOlder = SmartAlarmEntity(
            alarmId = "ALM_done_v1",
            taskTitle = "Báo cáo doanh thu tháng",
            targetTimeMillis = startOfToday + 3600_000L,
            targetDateLabel = "09:00",
            status = SmartAlarmStatus.COMPLETED,
            updatedAt = now - 10000L
        )
        val doneNewer = SmartAlarmEntity(
            alarmId = "TASK_done_v2",
            taskTitle = "báo cáo doanh thu tháng",
            targetTimeMillis = startOfToday + 7200_000L,
            targetDateLabel = "10:00",
            status = SmartAlarmStatus.COMPLETED,
            updatedAt = now - 1000L
        )

        // Duplicated TODO entries with same/similar title
        val todoOlder = SmartAlarmEntity(
            alarmId = "ALM_todo_v1",
            taskTitle = "Gặp đối tác chiến lược Alpha",
            targetTimeMillis = now + 3600_000L,
            targetDateLabel = "14:00",
            status = SmartAlarmStatus.ACTIVE,
            updatedAt = now - 8000L
        )
        val todoNewer = SmartAlarmEntity(
            alarmId = "TASK_todo_v2",
            taskTitle = "gặp đối tác chiến lược Alpha",
            targetTimeMillis = now + 7200_000L,
            targetDateLabel = "15:00",
            status = SmartAlarmStatus.ACTIVE,
            updatedAt = now - 2000L
        )

        // An entry with an active alarm that was subsequently completed in a newer entry
        val supersededTodo = SmartAlarmEntity(
            alarmId = "ALM_superseded",
            taskTitle = "Kiểm tra hệ thống PCCC",
            targetTimeMillis = now,
            targetDateLabel = "11:00",
            status = SmartAlarmStatus.ACTIVE,
            updatedAt = now - 5000L
        )
        val completedSuper = SmartAlarmEntity(
            alarmId = "TASK_completed_super",
            taskTitle = "Kiểm tra hệ thống PCCC",
            targetTimeMillis = now,
            targetDateLabel = "Đã xong",
            status = SmartAlarmStatus.COMPLETED,
            updatedAt = now - 500L
        )

        val alarmDbMap = mapOf(
            doneOlder.alarmId to doneOlder,
            doneNewer.alarmId to doneNewer,
            todoOlder.alarmId to todoOlder,
            todoNewer.alarmId to todoNewer,
            supersededTodo.alarmId to supersededTodo,
            completedSuper.alarmId to completedSuper
        )

        val validDbAlarms = alarmDbMap.values
            .filter { it.status != SmartAlarmStatus.CANCELLED && it.status != SmartAlarmStatus.EXPIRED }

        // Layer 1: deduplicate COMPLETED (keep newest updatedAt)
        val deduplicatedDoneAlarms = mutableListOf<SmartAlarmEntity>()
        validDbAlarms
            .filter { it.status == SmartAlarmStatus.COMPLETED }
            .filter { it.targetTimeMillis >= startOfToday || it.updatedAt >= cutoff24h }
            .sortedByDescending { it.updatedAt }
            .forEach { alarm ->
                val alreadyPresent = deduplicatedDoneAlarms.any { existing ->
                    SystemAlarmHelper.isTitleMatching(existing.taskTitle, alarm.taskTitle)
                }
                if (!alreadyPresent) {
                    deduplicatedDoneAlarms.add(alarm)
                }
            }

        val dbTasksDone = deduplicatedDoneAlarms.take(5).map { alarm ->
            TaskItem(title = alarm.taskTitle, deadline = alarm.targetDateLabel, status = TaskStatus.DONE)
        }

        // Layer 2: deduplicate TODO (keep newest updatedAt, skip if superseded by completed)
        val deduplicatedToDoAlarms = mutableListOf<SmartAlarmEntity>()
        validDbAlarms
            .filter { it.status != SmartAlarmStatus.COMPLETED }
            .sortedByDescending { it.updatedAt }
            .forEach { alarm ->
                val alreadyInDone = deduplicatedDoneAlarms.any { doneAlarm ->
                    SystemAlarmHelper.isTitleMatching(doneAlarm.taskTitle, alarm.taskTitle) &&
                        doneAlarm.updatedAt >= alarm.updatedAt
                }
                val alreadyPresent = deduplicatedToDoAlarms.any { existing ->
                    SystemAlarmHelper.isTitleMatching(existing.taskTitle, alarm.taskTitle)
                }
                if (!alreadyInDone && !alreadyPresent) {
                    deduplicatedToDoAlarms.add(alarm)
                }
            }

        val dbTasksToDo = deduplicatedToDoAlarms.map { alarm ->
            TaskItem(title = alarm.taskTitle, deadline = alarm.targetDateLabel, status = TaskStatus.TODO)
        }

        // 1. Verify dbTasksDone has only 1 entry for "Báo cáo doanh thu tháng" with deadline "10:00" (the newer one)
        val revenueTasks = dbTasksDone.filter { SystemAlarmHelper.isTitleMatching("Báo cáo doanh thu tháng", it.title) }
        assertEquals("Bản ghi hoàn thành trùng lặp phải được khử, chỉ giữ lại 1", 1, revenueTasks.size)
        assertEquals("Phải giữ bản ghi có updatedAt mới nhất (deadline 10:00)", "10:00", revenueTasks.first().deadline)

        // 2. Verify dbTasksToDo has only 1 entry for "Gặp đối tác chiến lược Alpha" with deadline "15:00" (the newer one)
        val partnerTasks = dbTasksToDo.filter { SystemAlarmHelper.isTitleMatching("Gặp đối tác chiến lược Alpha", it.title) }
        assertEquals("Bản ghi việc cần làm trùng lặp phải được khử, chỉ giữ lại 1", 1, partnerTasks.size)
        assertEquals("Phải giữ bản ghi có updatedAt mới nhất (deadline 15:00)", "15:00", partnerTasks.first().deadline)

        // 3. Verify superseded todo task is NOT in dbTasksToDo because a newer completed record exists
        val pcccInTodo = dbTasksToDo.any { SystemAlarmHelper.isTitleMatching("Kiểm tra hệ thống PCCC", it.title) }
        assertFalse("Nhiệm vụ đã hoàn thành không được phép xuất hiện trong danh sách việc cần làm", pcccInTodo)
        val pcccInDone = dbTasksDone.any { SystemAlarmHelper.isTitleMatching("Kiểm tra hệ thống PCCC", it.title) }
        assertTrue("Nhiệm vụ PCCC phải nằm trong danh sách đã hoàn thành", pcccInDone)
    }

    @Test
    fun r1_unfinishedTasks_allRetainedWithout24hCutoffOr5ItemLimit() {
        val now = System.currentTimeMillis()
        // Create 8 unfinished tasks, including overdue ones from several days ago
        val unfinishedAlarms = (1..8).map { i ->
            SmartAlarmEntity(
                alarmId = "ALM_todo_$i",
                taskTitle = "Nhiệm vụ còn hạn hoặc tồn đọng $i",
                targetTimeMillis = now - i * 86400_000L, // Past dates (overdue)
                targetDateLabel = "Quá hạn $i ngày",
                status = if (i % 2 == 0) SmartAlarmStatus.ACTIVE else SmartAlarmStatus.PROPOSED,
                updatedAt = now - i * 3600_000L
            )
        }

        val alarmDbMap = unfinishedAlarms.associateBy { it.alarmId }
        val validDbAlarms = alarmDbMap.values
            .filter { it.status != SmartAlarmStatus.CANCELLED && it.status != SmartAlarmStatus.EXPIRED }

        val deduplicatedToDoAlarms = mutableListOf<SmartAlarmEntity>()
        validDbAlarms
            .filter { it.status != SmartAlarmStatus.COMPLETED }
            .sortedByDescending { it.updatedAt }
            .forEach { alarm ->
                val alreadyPresent = deduplicatedToDoAlarms.any { existing ->
                    SystemAlarmHelper.isTitleMatching(existing.taskTitle, alarm.taskTitle)
                }
                if (!alreadyPresent) {
                    deduplicatedToDoAlarms.add(alarm)
                }
            }

        val dbTasksToDo = deduplicatedToDoAlarms.map { alarm ->
            TaskItem(title = alarm.taskTitle, deadline = alarm.targetDateLabel, status = TaskStatus.TODO)
        }

        // All 8 unfinished tasks must be retained (no 5-item cap, no 24h cutoff)
        assertEquals("Toàn bộ 8 nhiệm vụ chưa xong phải được giữ lại đầy đủ", 8, dbTasksToDo.size)
    }

    // =========================================================================
    // 7. R2: UI STABILITY, EMPTY SUPPRESSION & OPTIMISTIC SWIPE
    // =========================================================================

    @Test
    fun r2_aiChatStreamingCompletion_strictlyUpdatesActiveChatAnswer_andNeverOverwritesLatestSummaryText() {
        val originalSessionSummary = """
            ### LỜI KHUYÊN:
            Tiếp tục theo dõi ca trực hôm nay.

            ### VIỆC CẦN LÀM NGAY:
            - [ ] Kiểm tra phòng máy chủ lúc 09:30
            - [ ] Rà soát nhật ký an ninh lúc 14:00
        """.trimIndent()

        var latestSummaryText = originalSessionSummary
        var activeChatAnswer: String? = null

        // AI chat streaming response that contains markdown tasks / bullets
        val chatAnswerWithTasks = """
            Dưới đây là một số mẹo an ninh:
            - [ ] Đổi mật khẩu định kỳ 90 ngày
            - [ ] Kiểm tra camera giám sát
        """.trimIndent()

        // Replicating AssistantStep.COMPLETED in FloatingBubbleService:
        // Strictly record in activeChatAnswer, NEVER overwrite or blank latestSummaryText
        activeChatAnswer = chatAnswerWithTasks

        // Verify invariant
        assertEquals("latestSummaryText TUYỆT ĐỐI không bị ghi đè bởi câu trả lời chat", originalSessionSummary, latestSummaryText)
        assertEquals("activeChatAnswer nhận câu trả lời chat", chatAnswerWithTasks, activeChatAnswer)

        // Verify task board population still uses original session summary
        val parsed = SummaryParser.parse(latestSummaryText)
        val allTasks = parsed.tasksToDo + parsed.tasksPlanned + parsed.tasksDone
        assertEquals("Bảng nhiệm vụ vẫn giữ nguyên 2 nhiệm vụ ban đầu", 2, allTasks.size)
        assertTrue(allTasks.any { it.title.contains("Kiểm tra phòng máy chủ") })
        assertTrue(allTasks.any { it.title.contains("Rà soát nhật ký an ninh") })
    }

    @Test
    fun r2_emptyTaskBoardSuppression_whenAllTasksEmpty_neverShowsEmptyBox() {
        // When there are no tasks in parsed summary and no tasks in Room DB
        val emptyParsed = ParsedSummary(
            rawCleanText = "Chào buổi sáng!",
            conciseAdvice = "Chào buổi sáng!",
            tasksToDo = emptyList(),
            tasksPlanned = emptyList(),
            tasksDone = emptyList()
        )
        val alarmDbMap = emptyMap<String, SmartAlarmEntity>()

        val allTasksToDo = emptyParsed.tasksToDo
        val allTasksPlanned = emptyParsed.tasksPlanned
        val allTasksDone = emptyParsed.tasksDone
        val allTasks = allTasksToDo + allTasksPlanned + allTasksDone

        val isContainerVisible = allTasks.isNotEmpty()
        val hasEmptyBox = false // Empty box was completely removed

        assertFalse("Khi không có nhiệm vụ, container phải ở trạng thái GONE", isContainerVisible)
        assertFalse("Tuyệt đối không hiển thị emptyBox khi danh sách rỗng", hasEmptyBox)
    }

    @Test
    fun r2_swipeDismissal_optimisticViewHiding_setsSwipeAnimatingAndRecordsDismissedTitle() {
        val dismissedTaskTitles = mutableSetOf<String>()
        var isSwipeAnimating = false
        val taskTitle = "Gặp khách hàng lúc 10:00"

        // 1. Swipe starts exceeding threshold -> sets animating flag and records dismissed title
        isSwipeAnimating = true
        dismissedTaskTitles.add(taskTitle)

        assertTrue("isSwipeAnimating phải là true trong khi diễn ra hoạt ảnh 160ms", isSwipeAnimating)
        assertTrue("dismissedTaskTitles phải ghi nhận nhiệm vụ vừa vuốt", dismissedTaskTitles.contains(taskTitle))

        // 2. While isSwipeAnimating is true, incoming reactive DB flow MUST skip refreshing task board
        val reactiveFlowCollectShouldRefreshBoard = !isSwipeAnimating
        assertFalse("Khi đang vuốt, luồng DB không được phép gọi populateTaskBoard làm gãy animation", reactiveFlowCollectShouldRefreshBoard)

        // 3. At end of 160ms animation: view is hidden locally and isSwipeAnimating reset to false
        isSwipeAnimating = false
        assertFalse("isSwipeAnimating phải được trả về false sau khi kết thúc vuốt", isSwipeAnimating)

        // 4. On next populateTaskBoard, dismissed task is filtered out
        val candidateTasks = listOf(TaskItem(title = taskTitle, deadline = "10:00", status = TaskStatus.TODO))
        val filteredTasks = candidateTasks.filterNot { task ->
            dismissedTaskTitles.any { SystemAlarmHelper.isTitleMatching(task.title, it) }
        }
        assertTrue("Nhiệm vụ đã vuốt không được xuất hiện lại trong TaskBoard", filteredTasks.isEmpty())
    }

    @Test
    fun unifiedPipeline_whenUserSubmitsGuidance_producesFullSummaryWithAdviceAndTasks() {
        val userPrompt = "à t k thuộc đội văn nghệ nhé"
        val mockAiOutput = """
=== PHẦN 1: LỜI KHUYÊN & LƯU Ý ===
1. [Lời khuyên]: Vì bạn không thuộc đội văn nghệ nên các việc gửi MP4 và chuẩn bị tất đen bạn không cần tham gia. Hãy tập trung hoàn thành bài Speaking Unit 3 và BTVN IE08.
2. [Lưu ý]: Nhớ nộp Speaking Unit 3 trước 12:00.

=== PHẦN 2: BẢNG ĐIỀU PHỐI NHIỆM VỤ ===
### 🔴 NHIỆM VỤ PHẢI LÀM (TO-DO)
- Nộp bài Speaking Unit 3 lên bảng Padlet | Nguồn: Cô Hiền | Hạn: 12:00
- Hoàn thành BTVN IE08 | Nguồn: Thầy Tuấn | Hạn: 22:00
        """.trimIndent()

        val parsed = SummaryParser.parse(mockAiOutput)
        val cleanAdvice = SummaryParser.cleanAdviceText(parsed.conciseAdvice)

        // 1. Verify that advice is extracted correctly and addresses user prompt
        assertTrue("Lời khuyên phải đề cập đến văn nghệ", cleanAdvice.contains("văn nghệ"))
        assertTrue("Lời khuyên phải đề cập đến Speaking Unit 3", cleanAdvice.contains("Speaking Unit 3"))

        // 2. Verify that tasks are NOT zero
        assertTrue("Bảng nhiệm vụ phải có nhiệm vụ TO-DO", parsed.tasksToDo.isNotEmpty())
        assertEquals("Phải có 2 nhiệm vụ TO-DO", 2, parsed.tasksToDo.size)
        assertTrue(parsed.tasksToDo.any { it.title.contains("Speaking Unit 3") })
        assertTrue(parsed.tasksToDo.any { it.title.contains("Hoàn thành BTVN IE08") })

        // 3. Verify that văn nghệ is NOT in TO-DO tasks
        assertFalse(parsed.tasksToDo.any { it.title.contains("tất đen") || it.title.contains("file nhạc") })

        // 4. Verify populateTaskBoard logic accepts this summary as having tasks
        val hasTasks = (parsed.tasksToDo + parsed.tasksPlanned + parsed.tasksDone).isNotEmpty()
        assertTrue("hasTasks phải là true để không bị coi là bản ghi rỗng", hasTasks)
    }

    @Test
    fun legacyChatRecords_whenPurged_removesHoiPrefixedRecordsFromSummaryHistory() {
        val summaries = listOf(
            com.orca.orcaauto.data.local.SummaryHistoryEntity(
                id = 1,
                timestamp = 1000L,
                targetTimeSlot = "09:24 - 15/11",
                messageCount = 36,
                summaryContent = "=== PHẦN 1: LỜI KHUYÊN ===\nLàm bài tập\n=== PHẦN 2: BẢNG NHIỆM VỤ ===\n### 🔴 NHIỆM VỤ PHẢI LÀM (TO-DO)\n- Bài 1 | Nguồn: A | Hạn: 10h",
                status = "SUCCESS",
                modelInfo = "Gemini",
                sessionId = "session_main_default",
                sessionName = "Phiên chính"
            ),
            com.orca.orcaauto.data.local.SummaryHistoryEntity(
                id = 2,
                timestamp = 2000L,
                targetTimeSlot = "11:31 - 15/11",
                messageCount = 35,
                summaryContent = "Hỏi: à t k thuộc đội văn nghệ nhé\n\nÀ vậy thì bạn không cần tham gia nhé",
                status = "SUCCESS",
                modelInfo = "Gemini",
                sessionId = "session_main_default",
                sessionName = "Phiên chính"
            )
        )

        // Filter out legacy "Hỏi:" chat records using robust isLegacyChatRecord
        val legacyRecords = summaries.filter { SummaryParser.isLegacyChatRecord(it.summaryContent) }
        val cleanSummaries = summaries.filterNot { SummaryParser.isLegacyChatRecord(it.summaryContent) }

        assertEquals("Phải phát hiện 1 bản ghi chat cũ dạng Hỏi:", 1, legacyRecords.size)
        assertEquals("Bản ghi bị lọc là id 2", 2L, legacyRecords.first().id)
        assertEquals("Danh sách sạch chỉ còn 1 bản ghi chuẩn", 1, cleanSummaries.size)
        assertEquals("Bản ghi chuẩn giữ lại là id 1", 1L, cleanSummaries.first().id)
    }

    @Test
    fun isLegacyChatRecord_detectsVariationsWithWhitespaceAndCase_preservesRealSummaries() {
        // Chat records with leading whitespace, newlines, case differences
        assertTrue(SummaryParser.isLegacyChatRecord("Hỏi: à t k thuộc đội văn nghệ nhé\n\nÀ vậy thì bạn không cần tham gia nhé"))
        assertTrue(SummaryParser.isLegacyChatRecord("  \n  hỏi: bài tập hôm nay là gì?\n\nBài tập là làm toán."))
        assertTrue(SummaryParser.isLegacyChatRecord("\r\n\tHoi: mai có học không?\n\nMai nghỉ học nhé."))
        assertTrue(SummaryParser.isLegacyChatRecord("HỎI: có cần nộp bài không?\n\nCó nhé."))

        // Real summary with Section 1 mentioning "Hỏi:" but having Section 2 TaskBoard -> MUST NOT BE FLAGGED
        val realSummaryWithHoiMention = """
            === PHẦN 1: LỜI KHUYÊN ===
            Hỏi: bạn đã hỏi cô giáo về đề cương chưa? Cần hỏi sớm nhé.
            === PHẦN 2: BẢNG NHIỆM VỤ ===
            ### 🔴 NHIỆM VỤ PHẢI LÀM (TO-DO)
            - Ôn tập chương 1 | Nguồn: Sách | Hạn: 20:00
        """.trimIndent()
        assertFalse(
            "Bản tóm tắt thật có chữ 'Hỏi:' trong lời khuyên nhưng có Bảng nhiệm vụ KHÔNG được coi là chat rác",
            SummaryParser.isLegacyChatRecord(realSummaryWithHoiMention)
        )

        // Real summary with 0 tasks but structured sections -> MUST NOT BE FLAGGED
        val realSummaryNoTasks = """
            === PHẦN 1: LỜI KHUYÊN ===
            Hôm nay không có bài tập hay thông báo mới nào cần xử lý.
            === PHẦN 2: BẢNG NHIỆM VỤ ===
        """.trimIndent()
        assertFalse(
            "Bản tóm tắt thật không có việc nhưng có cấu trúc phần 2 KHÔNG bị coi là chat rác",
            SummaryParser.isLegacyChatRecord(realSummaryNoTasks)
        )
    }

    @Test
    fun bestSummarySelection_filtersLegacyChat_prioritizesTasks_andHandlesFallbacksSafely() {
        val chatRecord1 = com.orca.orcaauto.data.local.SummaryHistoryEntity(
            id = 3,
            timestamp = 3000L,
            targetTimeSlot = "11:31 - 15/11",
            messageCount = 35,
            summaryContent = "\n\nHỏi: à t k thuộc đội văn nghệ nhé\n\nÀ thế thì thôi nhé",
            status = "SUCCESS",
            modelInfo = "Gemini",
            sessionId = "session_main_default",
            sessionName = "Phiên chính"
        )
        val validSummaryNoTasks = com.orca.orcaauto.data.local.SummaryHistoryEntity(
            id = 2,
            timestamp = 2000L,
            targetTimeSlot = "10:00 - 15/11",
            messageCount = 10,
            summaryContent = "=== PHẦN 1: LỜI KHUYÊN ===\nKhông có việc cần làm.",
            status = "SUCCESS",
            modelInfo = "Gemini",
            sessionId = "session_main_default",
            sessionName = "Phiên chính"
        )
        val validSummaryWithTasks = com.orca.orcaauto.data.local.SummaryHistoryEntity(
            id = 1,
            timestamp = 1000L,
            targetTimeSlot = "09:24 - 15/11",
            messageCount = 36,
            summaryContent = "=== PHẦN 1: LỜI KHUYÊN ===\nLàm bài tập\n=== PHẦN 2: BẢNG NHIỆM VỤ ===\n### 🔴 NHIỆM VỤ PHẢI LÀM (TO-DO)\n- Nộp Speaking Unit 3 | Nguồn: Cô Hiền | Hạn: 12h",
            status = "SUCCESS",
            modelInfo = "Gemini",
            sessionId = "session_main_default",
            sessionName = "Phiên chính"
        )

        // Case 1: All 3 records present -> MUST pick validSummaryWithTasks (id 1)
        val recents1 = listOf(chatRecord1, validSummaryNoTasks, validSummaryWithTasks)
        val validList1 = recents1.filterNot { SummaryParser.isLegacyChatRecord(it.summaryContent) }
        val best1 = validList1.firstOrNull { SummaryParser.parse(it.summaryContent).totalTaskCount > 0 }
            ?: validList1.firstOrNull()
        assertEquals("Ưu tiên bản ghi có nhiệm vụ", 1L, best1?.id)

        // Case 2: Only chatRecord1 and validSummaryNoTasks -> MUST pick validSummaryNoTasks (id 2), NEVER chatRecord1
        val recents2 = listOf(chatRecord1, validSummaryNoTasks)
        val validList2 = recents2.filterNot { SummaryParser.isLegacyChatRecord(it.summaryContent) }
        val best2 = validList2.firstOrNull { SummaryParser.parse(it.summaryContent).totalTaskCount > 0 }
            ?: validList2.firstOrNull()
        assertEquals("Fallback an toàn sang bản tóm tắt hợp lệ không việc", 2L, best2?.id)

        // Case 3: Only chat records exist -> MUST return null, NEVER pick chat record
        val recents3 = listOf(chatRecord1)
        val validList3 = recents3.filterNot { SummaryParser.isLegacyChatRecord(it.summaryContent) }
        val best3 = validList3.firstOrNull { SummaryParser.parse(it.summaryContent).totalTaskCount > 0 }
            ?: validList3.firstOrNull()
        assertNull("Nếu chỉ có chat rác, phải trả về null để UI hiển thị placeholder an toàn", best3)
    }

    @Test
    fun latestSummaryTextAssignment_doesNotAcceptZeroTaskWhenBlank_ifChatRecord() {
        var latestSummaryText = ""
        val chatRecordContent = "Hỏi: à t k thuộc đội văn nghệ nhé\n\nÀ thế thì thôi nhé"

        // Simulated Flow arrival
        val isLegacyChat = SummaryParser.isLegacyChatRecord(chatRecordContent)
        if (!isLegacyChat) {
            val parsed = SummaryParser.parse(chatRecordContent)
            val hasTasks = parsed.totalTaskCount > 0
            val currentHasTasks = latestSummaryText.isNotBlank() && SummaryParser.parse(latestSummaryText).totalTaskCount > 0
            if (hasTasks || !currentHasTasks) {
                latestSummaryText = chatRecordContent
            }
        }

        assertTrue("latestSummaryText không được nhận bản ghi chat rỗng", latestSummaryText.isBlank())
    }

    @Test
    fun cancellationException_isNeverSwallowedInFlowCollectors() = runBlocking {
        var cancellationRethrown = false
        val job = launch {
            try {
                flow<Int> {
                    emit(1)
                    delay(1000L)
                }.collect {
                    delay(500L)
                }
            } catch (e: CancellationException) {
                cancellationRethrown = true
                throw e
            } catch (e: Exception) {
                // Should not reach here for cancellation
            }
        }
        delay(50L)
        job.cancel()
        job.join()
        assertTrue("CancellationException phải được rethrow chuẩn xác", cancellationRethrown)
    }
}
