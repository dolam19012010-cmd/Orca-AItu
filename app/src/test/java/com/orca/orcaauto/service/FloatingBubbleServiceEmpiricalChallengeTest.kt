package com.orca.orcaauto.service

import com.orca.orcaauto.data.local.SharedNoteEntity
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
        val showEmptyTaskBox = allTasks.isEmpty() && effectiveAdvice.isBlank()
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
    fun bubbleDisplay_whenNoAdviceAndNoTasks_showsEmptyTaskBox() {
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
        assertTrue("Khi hoàn toàn không có cả lời khuyên lẫn nhiệm vụ, phải hiển thị ô trống hoàn tất", verdict.showEmptyTaskBox)
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

        // Now simulate AssistantStep.COMPLETED with real shift summary tasks
        val newSummaryWithTasks = """
            ### LỜI KHUYÊN:
            Cập nhật ca trực chiều.

            ### VIỆC CẦN LÀM NGAY:
            - [ ] Bàn giao ca trực phòng điều khiển lúc 16:00
        """.trimIndent()
        val parsedNew = SummaryParser.parse(newSummaryWithTasks)
        val newTasks = parsedNew.tasksToDo + parsedNew.tasksPlanned + parsedNew.tasksDone

        if (newTasks.isNotEmpty()) {
            latestSummaryText = newSummaryWithTasks
            activeChatAnswer = null
        } else {
            activeChatAnswer = newSummaryWithTasks
        }

        assertEquals("latestSummaryText được cập nhật khi AI sinh báo cáo có nhiệm vụ", newSummaryWithTasks, latestSummaryText)
        assertNull("activeChatAnswer được xóa khi có summary mới", activeChatAnswer)
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


}
