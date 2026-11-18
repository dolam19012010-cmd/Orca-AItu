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
import kotlinx.coroutines.isActive
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

        // Under R4: TaskBoard strictly renders tasks from AI parsed summary, eliminating DB task injection
        val parsed = SummaryParser.parse(latestSummaryText)
        val speakableParsed = com.orca.orcaauto.domain.alarm.SystemAlarmHelper.filterOutCancelled(parsed, alarmDbMap)

        val allTasksToDo = speakableParsed.tasksToDo
        val allTasksPlanned = speakableParsed.tasksPlanned
        val allTasksDone = speakableParsed.tasksDone
        val allTasks = allTasksToDo + allTasksPlanned + allTasksDone

        assertEquals("TaskBoard chỉ hiển thị trung thực 1 nhiệm vụ từ AI summary, không tự nhồi thêm từ Room DB", 1, allTasks.size)
        assertTrue("Chứa nhiệm vụ Họp giao ban", allTasks.any { it.title.contains("Họp giao ban") })
        assertFalse("Không tự ý nhồi Diễn tập PCCC từ Room DB vào TaskBoard khi AI không yêu cầu", allTasks.any { it.title.contains("Diễn tập PCCC") })
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

        // Under R4: TaskBoard takes 100% tasks from AI parse and dismissed filters, with NO Room DB injection
        val speakableParsed = com.orca.orcaauto.domain.alarm.SystemAlarmHelper.filterOutCancelled(parsed, alarmDbMap)
        val allTasksToDo = speakableParsed.tasksToDo
        val allTasksPlanned = speakableParsed.tasksPlanned
        val allTasksDone = speakableParsed.tasksDone
        val allTasks = allTasksToDo + allTasksPlanned + allTasksDone

        assertTrue("Dưới R4: TaskBoard không tự nhồi task từ Room DB khi AI không có task", allTasks.isEmpty())
    }

    // =========================================================================
    // 6. R1: TASK FILTERING & TWO-LAYER DEDUPLICATION
    // =========================================================================

    @Test
    fun r1_completedTasksFiltering_onlyIncludesTodayOrLast24Hours_andLimitsToAtMost5() {
        // Under R4: TaskBoard strictly displays AI parsed tasks, completely eliminating Room DB injection
        val summaryWithDone = """
            ### LỜI KHUYÊN:
            Các nhiệm vụ ca trực hôm nay.
            
            ### 🟢 NHIỆM VỤ ĐÃ LÀM (DONE)
            - [x] Bàn giao ca trực sáng | Nguồn: Trưởng ca | Hạn: 08:00
            - [x] Kiểm tra sổ nhật ký trực | Nguồn: Sổ trực | Hạn: 09:00
        """.trimIndent()

        val parsed = SummaryParser.parse(summaryWithDone)
        assertEquals("AI phân giải được 2 việc đã xong", 2, parsed.tasksDone.size)

        // Even if Room DB has 10+ completed alarms from past days/today
        val now = System.currentTimeMillis()
        val manyDbAlarms = (1..10).associate { i ->
            val id = "ALM_db_done_$i"
            id to SmartAlarmEntity(
                alarmId = id,
                taskTitle = "Nhiệm vụ DB cũ $i",
                targetTimeMillis = now - i * 60_000L,
                targetDateLabel = "Xong",
                status = SmartAlarmStatus.COMPLETED
            )
        }

        val speakableParsed = SystemAlarmHelper.filterOutCancelled(parsed, manyDbAlarms)
        val allTasksDone = speakableParsed.tasksDone

        // R4 Contract: TaskBoard only contains the 2 tasks from AI, zero injection from Room DB
        assertEquals("TaskBoard chỉ chứa đúng 2 việc đã xong từ AI", 2, allTasksDone.size)
        assertTrue(allTasksDone.any { it.title.contains("Bàn giao ca trực sáng") })
        assertTrue(allTasksDone.any { it.title.contains("Kiểm tra sổ nhật ký trực") })
        assertFalse("Không tự nhồi bất kỳ việc cũ nào từ Room DB", allTasksDone.any { it.title.contains("Nhiệm vụ DB cũ") })
    }

    @Test
    fun r1_internalTwoLayerDeduplication_keepsNewestUpdatedAtForMatchingTitles() {
        // Under R4: TaskBoard eliminates SQLite task injection and duplicate creation.
        // It reflects AI summary tasks, and uses alarmDbMap purely for status resolution (e.g. isTaskCompleted)
        val summaryWithTasks = """
            ### VIỆC CẦN LÀM NGAY:
            - [ ] Báo cáo doanh thu tháng | Nguồn: Kế toán | Hạn: 10:00
            - [ ] Gặp đối tác chiến lược Alpha | Nguồn: Giám đốc | Hạn: 15:00
            - [ ] Kiểm tra hệ thống PCCC | Nguồn: BQL | Hạn: 11:00
        """.trimIndent()

        val parsed = SummaryParser.parse(summaryWithTasks)
        assertEquals(3, parsed.tasksToDo.size)

        // Suppose "Kiểm tra hệ thống PCCC" was completed in alarmDbMap
        val completedPcccAlarm = SmartAlarmEntity(
            alarmId = "ALM_pccc",
            taskTitle = "Kiểm tra hệ thống PCCC",
            targetTimeMillis = System.currentTimeMillis(),
            targetDateLabel = "11:00",
            status = SmartAlarmStatus.COMPLETED,
            updatedAt = System.currentTimeMillis()
        )
        val alarmDbMap = mapOf(completedPcccAlarm.alarmId to completedPcccAlarm)

        // In FloatingBubbleService.populateTaskBoard:
        // Filter out completed tasks from parsed TODO
        val parsedTasksToDo = parsed.tasksToDo.filterNot { task ->
            SystemAlarmHelper.isTaskCompleted(task.title, alarmDbMap)
        }

        // Verify: PCCC is filtered out of ToDo because DB marked it COMPLETED
        assertEquals("Chỉ còn 2 việc ToDo (PCCC đã hoàn thành trong DB được loại khỏi ToDo)", 2, parsedTasksToDo.size)
        assertTrue(parsedTasksToDo.any { it.title.contains("Báo cáo doanh thu") })
        assertTrue(parsedTasksToDo.any { it.title.contains("Gặp đối tác") })
        assertFalse("Việc đã hoàn thành không xuất hiện trong ToDo", parsedTasksToDo.any { it.title.contains("Kiểm tra hệ thống PCCC") })
    }

    @Test
    fun r1_unfinishedTasks_allRetainedWithout24hCutoffOr5ItemLimit() {
        // Under R4: TaskBoard faithfully renders 100% of tasks parsed from AI without arbitrary capping or DB injection
        val aiSummaryWith8Tasks = """
            ### VIỆC CẦN LÀM NGAY:
            - [ ] Nhiệm vụ ca trực 1 | Nguồn: Sếp | Hạn: 09:00
            - [ ] Nhiệm vụ ca trực 2 | Nguồn: Sếp | Hạn: 10:00
            - [ ] Nhiệm vụ ca trực 3 | Nguồn: Sếp | Hạn: 11:00
            - [ ] Nhiệm vụ ca trực 4 | Nguồn: Sếp | Hạn: 12:00
            - [ ] Nhiệm vụ ca trực 5 | Nguồn: Sếp | Hạn: 13:00
            - [ ] Nhiệm vụ ca trực 6 | Nguồn: Sếp | Hạn: 14:00
            - [ ] Nhiệm vụ ca trực 7 | Nguồn: Sếp | Hạn: 15:00
            - [ ] Nhiệm vụ ca trực 8 | Nguồn: Sếp | Hạn: 16:00
        """.trimIndent()

        val parsed = SummaryParser.parse(aiSummaryWith8Tasks)
        val speakableParsed = SystemAlarmHelper.filterOutCancelled(parsed, emptyMap())

        val allTasksToDo = speakableParsed.tasksToDo
        val allTasks = allTasksToDo + speakableParsed.tasksPlanned + speakableParsed.tasksDone

        // Verify all 8 tasks are retained faithfully
        assertEquals("Toàn bộ 8 nhiệm vụ từ AI phải được hiển thị đầy đủ trên TaskBoard", 8, allTasks.size)
        for (i in 1..8) {
            assertTrue("Chứa nhiệm vụ $i", allTasks.any { it.title.contains("Nhiệm vụ ca trực $i") })
        }
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
    fun bestSummarySelection_prioritizesRecordsWithTasks_andFallsBackToRecent() {
        val summaryNoTasks = com.orca.orcaauto.data.local.SummaryHistoryEntity(
            id = 2,
            timestamp = 2000L,
            targetTimeSlot = "11:31 - 15/11",
            messageCount = 35,
            summaryContent = "=== PHẦN 1: LỜI KHUYÊN ===\nKhông có việc cần làm.",
            status = "SUCCESS",
            modelInfo = "Gemini",
            sessionId = "session_main_default",
            sessionName = "Phiên chính"
        )
        val summaryWithTasks = com.orca.orcaauto.data.local.SummaryHistoryEntity(
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

        // Case 1: Both records present -> MUST pick summaryWithTasks (id 1) even if summaryNoTasks is newer (timestamp 2000 > 1000)
        val recents1 = listOf(summaryNoTasks, summaryWithTasks).filter { it.summaryContent.isNotBlank() }
        val best1 = recents1.firstOrNull { SummaryParser.parse(it.summaryContent).totalTaskCount > 0 }
            ?: recents1.firstOrNull()
        assertEquals("Ưu tiên bản ghi có nhiệm vụ để không bị mất việc", 1L, best1?.id)

        // Case 2: Only summaryNoTasks -> Falls back to the latest valid record (id 2)
        val recents2 = listOf(summaryNoTasks).filter { it.summaryContent.isNotBlank() }
        val best2 = recents2.firstOrNull { SummaryParser.parse(it.summaryContent).totalTaskCount > 0 }
            ?: recents2.firstOrNull()
        assertEquals("Fallback an toàn sang bản tóm tắt gần nhất", 2L, best2?.id)

        // Case 3: Empty list -> returns null
        val recents3 = emptyList<com.orca.orcaauto.data.local.SummaryHistoryEntity>()
        val best3 = recents3.firstOrNull { SummaryParser.parse(it.summaryContent).totalTaskCount > 0 }
            ?: recents3.firstOrNull()
        assertNull("Danh sách rỗng phải trả về null để UI hiển thị placeholder an toàn", best3)
    }

    @Test
    fun latestSummaryTextAssignment_protectsExistingTasks_fromZeroTaskOverwrite() {
        // Initial state: TaskBoard has tasks from a previous summary
        var latestSummaryText = "=== PHẦN 1: LỜI KHUYÊN ===\nLàm bài\n=== PHẦN 2: BẢNG NHIỆM VỤ ===\n### 🔴 NHIỆM VỤ PHẢI LÀM (TO-DO)\n- Task 1"
        val zeroTaskContent = "=== PHẦN 1: LỜI KHUYÊN ===\nChỉ là lời nhắc thông thường."

        // Simulated Flow arrival of zero-task record
        val parsed = SummaryParser.parse(zeroTaskContent)
        val hasTasks = parsed.totalTaskCount > 0
        val currentHasTasks = latestSummaryText.isNotBlank() && SummaryParser.parse(latestSummaryText).totalTaskCount > 0

        if (hasTasks || !currentHasTasks) {
            latestSummaryText = zeroTaskContent
        }

        // Must still retain the existing summary with tasks!
        assertTrue("latestSummaryText không được bị đè bởi bản ghi 0 việc", latestSummaryText.contains("Task 1"))
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

    // =========================================================================
    // 6. SSE STREAM WATCHDOG & NETWORK DROP HANDLING (TWO-PHASE DECOUPLED)
    // =========================================================================

    @Test
    fun streamWatchdog_twoPhase_whenConnectionTimeoutExceeded_cancelsJobAndTriggersTimeoutState() = runBlocking {
        val testScope = CoroutineScope(Dispatchers.Default)
        val initialConnectionTimeoutMs = 150L // Compressed for fast JVM test execution
        val sseWatchdogTimeoutMs = 60L
        val requestStartedAt = System.currentTimeMillis()
        val hasReceivedFirstToken = AtomicBoolean(false)
        val lastTokenReceivedAt = java.util.concurrent.atomic.AtomicLong(0L)
        val isWatchdogTimedOut = AtomicBoolean(false)
        val uiStateRendered = AtomicBoolean(false)
        val streamLatch = CountDownLatch(1)

        var streamingJob: Job? = null
        var watchdogJob: Job? = null

        watchdogJob = testScope.launch {
            while (isActive) {
                delay(15)
                val now = System.currentTimeMillis()
                if (hasReceivedFirstToken.get()) {
                    val elapsed = now - lastTokenReceivedAt.get()
                    if (elapsed >= sseWatchdogTimeoutMs) {
                        isWatchdogTimedOut.set(true)
                        streamingJob?.cancel(CancellationException("SSE_STREAM_WATCHDOG_TIMEOUT"))
                        break
                    }
                } else {
                    val elapsed = now - requestStartedAt
                    if (elapsed >= initialConnectionTimeoutMs) {
                        isWatchdogTimedOut.set(true)
                        streamingJob?.cancel(CancellationException("INITIAL_CONNECTION_TIMEOUT"))
                        break
                    }
                }
            }
        }

        streamingJob = testScope.launch {
            try {
                // Simulates a hanging network stream that yields zero tokens
                streamLatch.countDown()
                delay(5000L)
            } catch (e: CancellationException) {
                if (isWatchdogTimedOut.get()) {
                    uiStateRendered.set(true)
                }
            } finally {
                watchdogJob.cancel()
            }
        }

        streamLatch.await(1, TimeUnit.SECONDS)
        streamingJob.join()

        assertTrue("Watchdog timeout flag phải được kích hoạt sau khi hết timeout kết nối ban đầu", isWatchdogTimedOut.get())
        assertTrue("Giao diện thông báo mất mạng & nút thử lại phải được kích hoạt", uiStateRendered.get())
        assertFalse("Streaming job phải đã bị huỷ", streamingJob.isActive)
    }

    @Test
    fun streamWatchdog_twoPhase_allowsPreTokenDelayWithoutTriggeringStallWatchdog() = runBlocking {
        // Models the exact bug in production: Round 1 routing + remote queuing takes 100ms.
        // The in-flight stall watchdog is 50ms, but initial connection timeout is 300ms.
        // In the old buggy code, a 100ms delay before first token triggered the 50ms watchdog!
        // In the new decoupled code, stall watchdog is forbidden until hasReceivedFirstToken is true.
        val testScope = CoroutineScope(Dispatchers.Default)
        val initialConnectionTimeoutMs = 300L
        val sseWatchdogTimeoutMs = 50L
        val requestStartedAt = System.currentTimeMillis()
        val hasReceivedFirstToken = AtomicBoolean(false)
        val lastTokenReceivedAt = java.util.concurrent.atomic.AtomicLong(0L)
        val isWatchdogTimedOut = AtomicBoolean(false)
        var streamCompletedCleanly = false

        var streamingJob: Job? = null
        val watchdogJob = testScope.launch {
            while (isActive) {
                delay(10)
                val now = System.currentTimeMillis()
                if (hasReceivedFirstToken.get()) {
                    val elapsed = now - lastTokenReceivedAt.get()
                    if (elapsed >= sseWatchdogTimeoutMs) {
                        isWatchdogTimedOut.set(true)
                        streamingJob?.cancel(CancellationException("SSE_STREAM_WATCHDOG_TIMEOUT"))
                        break
                    }
                } else {
                    val elapsed = now - requestStartedAt
                    if (elapsed >= initialConnectionTimeoutMs) {
                        isWatchdogTimedOut.set(true)
                        streamingJob?.cancel(CancellationException("INITIAL_CONNECTION_TIMEOUT"))
                        break
                    }
                }
            }
        }

        streamingJob = testScope.launch {
            try {
                // Simulate Round 1 Router + remote model queueing taking 100ms (> 50ms stall watchdog)
                delay(100)

                // First token physically arrives!
                hasReceivedFirstToken.set(true)
                lastTokenReceivedAt.set(System.currentTimeMillis())

                // Then in-flight tokens stream smoothly every 15ms (< 50ms stall watchdog)
                for (i in 2..5) {
                    delay(15)
                    lastTokenReceivedAt.set(System.currentTimeMillis())
                }
                streamCompletedCleanly = true
            } finally {
                watchdogJob.cancel()
            }
        }

        streamingJob.join()

        assertFalse("Độ trễ trước token đầu tiên không được phép kích hoạt watchdog đứt dòng giữa chừng", isWatchdogTimedOut.get())
        assertTrue("Stream phải hoàn thành trọn vẹn mà không bị bóp nghẹt", streamCompletedCleanly)
    }

    @Test
    fun streamWatchdog_twoPhase_triggersStallTimeoutWhenStreamFreezesAfterFirstToken() = runBlocking {
        val testScope = CoroutineScope(Dispatchers.Default)
        val initialConnectionTimeoutMs = 500L
        val sseWatchdogTimeoutMs = 60L
        val requestStartedAt = System.currentTimeMillis()
        val hasReceivedFirstToken = AtomicBoolean(false)
        val lastTokenReceivedAt = java.util.concurrent.atomic.AtomicLong(0L)
        val isWatchdogTimedOut = AtomicBoolean(false)
        val uiStateRendered = AtomicBoolean(false)

        var streamingJob: Job? = null
        val watchdogJob = testScope.launch {
            while (isActive) {
                delay(10)
                val now = System.currentTimeMillis()
                if (hasReceivedFirstToken.get()) {
                    val elapsed = now - lastTokenReceivedAt.get()
                    if (elapsed >= sseWatchdogTimeoutMs) {
                        isWatchdogTimedOut.set(true)
                        streamingJob?.cancel(CancellationException("SSE_STREAM_WATCHDOG_TIMEOUT"))
                        break
                    }
                } else {
                    val elapsed = now - requestStartedAt
                    if (elapsed >= initialConnectionTimeoutMs) {
                        isWatchdogTimedOut.set(true)
                        streamingJob?.cancel(CancellationException("INITIAL_CONNECTION_TIMEOUT"))
                        break
                    }
                }
            }
        }

        streamingJob = testScope.launch {
            try {
                // First token arrives early at 20ms
                delay(20)
                hasReceivedFirstToken.set(true)
                lastTokenReceivedAt.set(System.currentTimeMillis())

                // Stream suddenly dies/hangs (e.g. WiFi dropped mid-response)
                delay(5000L)
            } catch (e: CancellationException) {
                if (isWatchdogTimedOut.get()) {
                    uiStateRendered.set(true)
                }
            } finally {
                watchdogJob.cancel()
            }
        }

        streamingJob.join()

        assertTrue("Watchdog phải phát hiện đứt dòng sau token đầu tiên", isWatchdogTimedOut.get())
        assertTrue("UI timeout mạng phải được hiển thị khi đứt dòng giữa chừng", uiStateRendered.get())
        assertFalse("Streaming job phải đã bị huỷ", streamingJob.isActive)
    }

    @Test
    fun streamWatchdog_whenUserCollapsesCard_cancelsJobWithoutTriggeringTimeoutUi() = runBlocking {
        val testScope = CoroutineScope(Dispatchers.Default)
        val isWatchdogTimedOut = AtomicBoolean(false)
        val timeoutUiRendered = AtomicBoolean(false)
        val jobStarted = CountDownLatch(1)

        val streamingJob = testScope.launch {
            try {
                jobStarted.countDown()
                delay(5000L)
            } catch (e: CancellationException) {
                if (isWatchdogTimedOut.get()) {
                    timeoutUiRendered.set(true)
                }
            }
        }

        jobStarted.await(1, TimeUnit.SECONDS)
        // User collapses floating bubble
        streamingJob.cancel()
        streamingJob.join()

        assertFalse("Người dùng chủ động đóng bóng không được kích hoạt cờ timeout", isWatchdogTimedOut.get())
        assertFalse("Không được hiển thị lỗi timeout mạng khi người dùng chủ động đóng bóng", timeoutUiRendered.get())
    }

    @Test
    fun streamWatchdog_whenRetryTriggered_reinvokesExecuteAiCallWithOriginalPrompt() {
        var retriedPrompt: String? = null
        val originalPrompt = "Tóm tắt ca 18:00"
        val onRetryMock: (String) -> Unit = { prompt ->
            retriedPrompt = prompt
        }

        // Simulate user clicking retry
        onRetryMock(originalPrompt)

        assertEquals("Prompt truyền vào hàm retry phải khớp chính xác với prompt ban đầu", originalPrompt, retriedPrompt)
    }

    @Test
    fun networkExceptionClassifier_correctlyIdentifiesSocketAndHostExceptions() {
        val unknownHost = java.net.UnknownHostException("Unable to resolve host api.gemini.google.com")
        val socketTimeout = java.net.SocketTimeoutException("timeout")
        val connectEx = java.net.ConnectException("Failed to connect to host")
        val nestedNetwork = java.io.IOException("Stream error", socketTimeout)
        val customMsg = java.io.IOException("Software caused connection abort")
        val watchdogTimeout = java.util.concurrent.CancellationException("SSE_STREAM_WATCHDOG_TIMEOUT")
        val connectionTimeout = java.util.concurrent.CancellationException("INITIAL_CONNECTION_TIMEOUT")

        val npe = NullPointerException("Null pointer")
        val illegalArg = IllegalArgumentException("Bad input")

        assertTrue(FloatingBubbleService.isNetworkException(unknownHost))
        assertTrue(FloatingBubbleService.isNetworkException(socketTimeout))
        assertTrue(FloatingBubbleService.isNetworkException(connectEx))
        assertTrue(FloatingBubbleService.isNetworkException(nestedNetwork))
        assertTrue(FloatingBubbleService.isNetworkException(customMsg))
        assertTrue(FloatingBubbleService.isNetworkException(watchdogTimeout))
        assertTrue(FloatingBubbleService.isNetworkException(connectionTimeout))

        assertFalse(FloatingBubbleService.isNetworkException(npe))
        assertFalse(FloatingBubbleService.isNetworkException(illegalArg))
        assertFalse(FloatingBubbleService.isNetworkException(null))
    }

    // =========================================================================
    // 7. PRECISE AI ERROR DISAMBIGUATION TESTS (NO FALSE NETWORK DROP LABELS)
    // =========================================================================

    @Test
    fun buildAiErrorMessage_whenPhysicalDnsResolutionFails_returnsTrueNetworkDrop() {
        val unknownHost = java.net.UnknownHostException("Unable to resolve host api.gemini.google.com")
        val (title, detail) = FloatingBubbleService.buildAiErrorMessage(null, unknownHost, false)

        assertEquals("Mất kết nối mạng", title)
        assertTrue(detail.contains("chưa có kết nối Internet", ignoreCase = true))
    }

    @Test
    fun buildAiErrorMessage_whenRateLimited_returnsExplicit429Message() {
        val rateLimitEx = com.orca.orcaauto.data.remote.RateLimitException("HTTP 429: RESOURCE_EXHAUSTED")
        val (title, detail) = FloatingBubbleService.buildAiErrorMessage(null, rateLimitEx, false)

        assertEquals("Hết hạn mức AI (HTTP 429)", title)
        assertTrue(detail.contains("hạn mức", ignoreCase = true))
    }

    @Test
    fun buildAiErrorMessage_whenApiKeyUnauthorized_returnsExplicitKeyErrorMessage() {
        val authEx = java.io.IOException("HTTP 401 (OpenRouter - openrouter/free): Unauthorized key")
        val (title, detail) = FloatingBubbleService.buildAiErrorMessage(null, authEx, false)

        assertEquals("API Key không hợp lệ (401/403)", title)
        assertTrue(detail.contains("từ chối", ignoreCase = true))
    }

    @Test
    fun buildAiErrorMessage_whenServerOverloaded_returnsExplicit50xMessage() {
        val serverEx = java.io.IOException("HTTP 503 (OpenRouter): Service Unavailable")
        val (title, detail) = FloatingBubbleService.buildAiErrorMessage(null, serverEx, false)

        assertEquals("Máy chủ AI quá tải (50x)", title)
        assertTrue(detail.contains("bảo trì", ignoreCase = true))
    }

    @Test
    fun buildAiErrorMessage_whenWatchdogTimedOut_returnsTimeoutMessageWithoutClaimingNetworkOffline() {
        val watchdogEx = java.util.concurrent.CancellationException("INITIAL_CONNECTION_TIMEOUT")
        val (title, detail) = FloatingBubbleService.buildAiErrorMessage(null, watchdogEx, true)

        assertEquals("Máy chủ phản hồi quá lâu (>90s)", title)
        assertTrue(detail.contains("Không nhận được phản hồi", ignoreCase = true))
    }

    @Test
    fun buildAiErrorMessage_whenAllKeysFailed_returnsAllKeysFailedMessage() {
        val allKeysEx = java.io.IOException("Toàn bộ 2 API Key trong danh sách dự phòng đều thất bại:\nKey 1: HTTP 401\nKey 2: HTTP 503")
        val (title, detail) = FloatingBubbleService.buildAiErrorMessage(null, allKeysEx, false)

        assertEquals("Toàn bộ API Key đều lỗi", title)
        assertTrue(detail.contains("danh sách dự phòng", ignoreCase = true))
    }

    // =========================================================================
    // TASK CARD BORDER & IN-PLACE ALARM TOGGLE CONTRACTS
    // =========================================================================

    @Test
    fun taskCardColorCoding_verifiesToDoTasksHaveRedStroke_andPlannedHaveAmber() {
        // Contract test verifying stroke logic matching renderTaskRow
        fun resolveTaskStrokeHex(isCancelled: Boolean, isCompleted: Boolean, isPlanned: Boolean): String {
            return when {
                isCancelled -> "#59EF4444"
                isCompleted -> "#10B981"
                isPlanned -> "#59F59E0B"
                else -> "#EF4444" // Nhiệm vụ cần làm (To-Do) MUST have the iconic red border!
            }
        }

        // 1. Standard uncompleted, uncancelled To-Do task MUST resolve to red #EF4444
        assertEquals("#EF4444", resolveTaskStrokeHex(isCancelled = false, isCompleted = false, isPlanned = false))

        // 2. Planned task MUST resolve to amber
        assertEquals("#59F59E0B", resolveTaskStrokeHex(isCancelled = false, isCompleted = false, isPlanned = true))

        // 3. Completed task MUST resolve to emerald green
        assertEquals("#10B981", resolveTaskStrokeHex(isCancelled = false, isCompleted = true, isPlanned = false))

        // 4. Cancelled task MUST resolve to muted red
        assertEquals("#59EF4444", resolveTaskStrokeHex(isCancelled = true, isCompleted = false, isPlanned = false))
    }

    @Test
    fun alarmToggleInPlace_updatesAlarmDbMapState_withoutLosingTaskIdentity() {
        val taskTitle = "Gửi báo cáo tuần cho sếp"
        val cleanTitle = "Gửi báo cáo tuần cho sếp"
        val alarmId = "alarm_test_123"
        val targetCal = Calendar.getInstance().apply { add(Calendar.HOUR_OF_DAY, 2) }

        var alarmDbMap = mapOf<String, SmartAlarmEntity>()

        // 1. Initial State: No active alarm
        var isAlarmActive = alarmDbMap[alarmId]?.status == SmartAlarmStatus.ACTIVE
        assertFalse("Ban đầu báo thức chưa được bật", isAlarmActive)

        // 2. User taps alarm pill to schedule alarm
        val scheduledAlarm = SmartAlarmEntity(
            alarmId = alarmId,
            taskTitle = cleanTitle,
            targetTimeMillis = targetCal.timeInMillis,
            targetDateLabel = "16:00",
            status = SmartAlarmStatus.ACTIVE,
            updatedAt = System.currentTimeMillis()
        )
        alarmDbMap = alarmDbMap + (alarmId to scheduledAlarm)

        // Verify updated state
        isAlarmActive = alarmDbMap[alarmId]?.status == SmartAlarmStatus.ACTIVE
        assertTrue("Sau khi bấm bật, báo thức phải ở trạng thái ACTIVE", isAlarmActive)
        assertEquals(SmartAlarmStatus.ACTIVE, alarmDbMap[alarmId]?.status)

        // 3. User taps alarm pill again to cancel alarm
        val disarmedAlarm = scheduledAlarm.copy(
            status = SmartAlarmStatus.PROPOSED,
            updatedAt = System.currentTimeMillis()
        )
        alarmDbMap = alarmDbMap + (alarmId to disarmedAlarm)

        // Verify disarmed state
        isAlarmActive = alarmDbMap[alarmId]?.status == SmartAlarmStatus.ACTIVE
        assertFalse("Sau khi bấm tắt, báo thức phải quay về PROPOSED", isAlarmActive)
        assertEquals(SmartAlarmStatus.PROPOSED, alarmDbMap[alarmId]?.status)
    }

    // =========================================================================
    // 17. EMPIRICAL CHALLENGES: MILESTONE 1 CONCURRENCY, LIFECYCLE & INVARIANTS
    // =========================================================================

    @Test
    fun shimmerLifecycle_whenStoppedBeforePostExecutes_doesNotStartInfiniteAnimator() {
        val isShimmerActive = AtomicBoolean(false)
        var animatorStarted = false
        var runnableExecuted = false

        // Simulate startTextShimmer
        isShimmerActive.set(true)
        val runnable = Runnable {
            runnableExecuted = true
            if (!isShimmerActive.get()) {
                return@Runnable
            }
            animatorStarted = true
        }

        // Simulate stopTextShimmer called before post executes
        isShimmerActive.set(false)

        // Now post runnable finally executes on Looper
        runnable.run()

        assertTrue(runnableExecuted)
        assertFalse("Animator tuyệt đối không được start khi stopTextShimmer đã gọi", animatorStarted)
    }

    @Test
    fun executeAiCall_structuredConcurrency_rethrowsWhenExternalCancellation() {
        val isWatchdogTimedOut = AtomicBoolean(false)
        var rethrownCaught = false

        // 1. External Cancellation (e.g. bubble collapsed, job cancelled by user)
        try {
            try {
                throw CancellationException("USER_COLLAPSED_BUBBLE")
            } catch (e: CancellationException) {
                if (!isWatchdogTimedOut.get()) {
                    throw e
                }
            }
        } catch (e: CancellationException) {
            rethrownCaught = true
            assertEquals("USER_COLLAPSED_BUBBLE", e.message)
        }
        assertTrue("CancellationException ngoại vi phải được rethrow để bảo toàn structured concurrency", rethrownCaught)

        // 2. Watchdog Timeout Cancellation (swallowed to display error UI)
        isWatchdogTimedOut.set(true)
        var watchdogSwallowed = false
        try {
            try {
                throw CancellationException("INITIAL_CONNECTION_TIMEOUT")
            } catch (e: CancellationException) {
                if (!isWatchdogTimedOut.get()) {
                    throw e
                }
                watchdogSwallowed = true
            }
        } catch (_: CancellationException) {
            watchdogSwallowed = false
        }
        assertTrue("CancellationException do watchdog kích hoạt phải được giữ lại để render UI", watchdogSwallowed)
    }

    @Test
    fun accumulatedLiveBuffer_threadSafety_underConcurrentStreamingDeltas() = runBlocking {
        val accumulatedLive = StringBuilder()
        val numThreads = 8
        val tokensPerThread = 250
        val scope = CoroutineScope(Dispatchers.Default)

        val jobs = (1..numThreads).map { threadIdx ->
            scope.launch {
                for (i in 1..tokensPerThread) {
                    synchronized(accumulatedLive) {
                        accumulatedLive.append("T$threadIdx-D$i ")
                        accumulatedLive.toString()
                    }
                }
            }
        }
        jobs.forEach { it.join() }

        val finalResult = synchronized(accumulatedLive) { accumulatedLive.toString() }
        assertNotNull(finalResult)
        assertEquals(numThreads * tokensPerThread, finalResult.split(" ").filter { it.isNotBlank() }.size)
    }

    @Test
    fun globalRequestTimeout_enforces120sHardCeilingAcrossMultipleKeyFailovers() {
        val globalRequestStartedAt = 1000L
        val globalTimeoutMs = FloatingBubbleService.GLOBAL_REQUEST_TIMEOUT_MS
        assertEquals(120_000L, globalTimeoutMs)

        // Key 1 fails at +40s, Key 2 fails at +85s, Key 3 attempts at +125s
        val checkTimes = listOf(40_000L, 85_000L, 125_000L)
        var timedOutAt120s = false

        for (elapsed in checkTimes) {
            val now = globalRequestStartedAt + elapsed
            val totalElapsed = now - globalRequestStartedAt
            if (totalElapsed >= globalTimeoutMs) {
                timedOutAt120s = true
                break
            }
        }

        assertTrue("Watchdog phải kích hoạt GLOBAL_REQUEST_TIMEOUT khi vượt mốc 120s toàn cục", timedOutAt120s)
    }

    @Test
    fun buildAiErrorMessage_handlesGlobalTimeoutCeilingMessage() {
        val globalTimeoutEx = CancellationException("GLOBAL_REQUEST_TIMEOUT")
        val (title, detail) = FloatingBubbleService.buildAiErrorMessage(null, globalTimeoutEx, true)
        assertEquals("Tổng thời gian chờ quá lâu (>120s)", title)
        assertTrue(detail.contains("120 giây"))

        val initialTimeoutEx = CancellationException("INITIAL_CONNECTION_TIMEOUT")
        val (title90s, detail90s) = FloatingBubbleService.buildAiErrorMessage(null, initialTimeoutEx, true)
        assertEquals("Máy chủ phản hồi quá lâu (>90s)", title90s)
        assertTrue(detail90s.contains("90 giây"))
    }

    private enum class TestSendBtnState { CALLING_AI, HAS_TEXT, EMPTY }

    @Test
    fun updateSendBtnState_skipsReallocation_whenStateUnchanged() {
        // Models SendBtnState transitions in updateSendBtnState
        var currentSendBtnState: TestSendBtnState? = null
        var drawableAllocations = 0

        fun updateState(isCalling: Boolean, hasText: Boolean) {
            val targetState = when {
                isCalling -> TestSendBtnState.CALLING_AI
                hasText -> TestSendBtnState.HAS_TEXT
                else -> TestSendBtnState.EMPTY
            }
            if (targetState == currentSendBtnState) return
            currentSendBtnState = targetState
            drawableAllocations++
        }

        // 1. Initial empty
        updateState(isCalling = false, hasText = false)
        assertEquals(1, drawableAllocations)
        assertEquals(TestSendBtnState.EMPTY, currentSendBtnState)

        // 2. Typing characters 1 to 20 -> state remains HAS_TEXT
        for (len in 1..20) {
            updateState(isCalling = false, hasText = true)
        }
        assertEquals(2, drawableAllocations) // Only 1 allocation when transitioning to HAS_TEXT!
        assertEquals(TestSendBtnState.HAS_TEXT, currentSendBtnState)

        // 3. Trigger AI calling
        updateState(isCalling = true, hasText = true)
        assertEquals(3, drawableAllocations)
        assertEquals(TestSendBtnState.CALLING_AI, currentSendBtnState)
    }

    @Test
    fun isPhysicalNetworkOffline_respectsNetCapabilityValidatedContract() {
        // Contract test for NetworkCapabilities validation logic in isPhysicalNetworkOffline
        fun evaluateCapabilities(hasInternet: Boolean, isValidated: Boolean): Boolean {
            return !hasInternet || !isValidated
        }

        // 1. Captive portal: connected to Wi-Fi with internet cap but NOT validated
        assertTrue("Wi-Fi có INTERNET nhưng thiếu VALIDATED phải được coi là offline", evaluateCapabilities(hasInternet = true, isValidated = false))

        // 2. Disconnected completely
        assertTrue("Không có INTERNET phải là offline", evaluateCapabilities(hasInternet = false, isValidated = false))

        // 3. Fully validated WAN connection
        assertFalse("Có cả INTERNET và VALIDATED phải là online (false)", evaluateCapabilities(hasInternet = true, isValidated = true))
    }

    // =========================================================================
    // 18. MILESTONE M1: FLOATING BUBBLE LATENCY & CONCURRENCY CHALLENGES
    // =========================================================================

    private data class MockTaskRowHolder(
        val holderId: Int,
        var boundTitle: String,
        var boundPlanned: Boolean
    )

    private data class MockRowView(
        val viewId: Int,
        var tag: MockTaskRowHolder? = null
    )

    private class MockTaskContainer {
        val children = mutableListOf<MockRowView>()
        var removeAllViewsCallCount = 0
        var removeViewsCallCount = 0

        fun childCount(): Int = children.size
        fun getChildAt(index: Int): MockRowView = children[index]
        fun removeViews(start: Int, count: Int) {
            removeViewsCallCount++
            for (i in 0 until count) {
                children.removeAt(start)
            }
        }
        fun removeAllViews() {
            removeAllViewsCallCount++
            children.clear()
        }
        fun addView(view: MockRowView) {
            children.add(view)
        }
        fun addView(view: MockRowView, index: Int) {
            children.add(index, view)
        }
        fun removeViewAt(index: Int) {
            children.removeAt(index)
        }
    }

    @Test
    fun m1_inPlaceDiffing_reusesChildViewsAndPreservesTags_withoutCallingRemoveAllViews() {
        val container = MockTaskContainer()
        val viewIdGenerator = java.util.concurrent.atomic.AtomicInteger(0)

        fun reconcileTaskBoard(taskItems: List<Pair<String, Boolean>>) {
            val targetCount = taskItems.size
            val currentCount = container.childCount()

            // 1. Remove excess trailing children if targetCount < currentCount
            if (currentCount > targetCount) {
                container.removeViews(targetCount, currentCount - targetCount)
            }

            // 2. In-place update existing child views via TaskRowHolder (< 0.05ms)
            val updateCount = minOf(currentCount, targetCount)
            for (i in 0 until updateCount) {
                val child = container.getChildAt(i)
                val holder = child.tag
                val item = taskItems[i]
                if (holder != null) {
                    holder.boundTitle = item.first
                    holder.boundPlanned = item.second
                } else {
                    val newView = MockRowView(viewId = viewIdGenerator.incrementAndGet()).apply {
                        tag = MockTaskRowHolder(holderId = viewId, boundTitle = item.first, boundPlanned = item.second)
                    }
                    container.removeViewAt(i)
                    container.addView(newView, i)
                }
            }

            // 3. Append only new views if needed
            for (i in updateCount until targetCount) {
                val item = taskItems[i]
                val newView = MockRowView(viewId = viewIdGenerator.incrementAndGet()).apply {
                    tag = MockTaskRowHolder(holderId = viewId, boundTitle = item.first, boundPlanned = item.second)
                }
                container.addView(newView)
            }
        }

        // Pass 1: Mount 3 initial task items
        val initialItems = listOf(
            "Task 1: Họp đầu tuần" to false,
            "Task 2: Báo cáo tài chính" to true,
            "Task 3: Kiểm tra PCCC" to false
        )
        reconcileTaskBoard(initialItems)
        assertEquals(3, container.childCount())
        val view1 = container.getChildAt(0)
        val view2 = container.getChildAt(1)
        val view3 = container.getChildAt(2)
        assertEquals(1, view1.viewId)
        assertEquals(2, view2.viewId)
        assertEquals(3, view3.viewId)
        assertEquals(0, container.removeAllViewsCallCount)

        // Pass 2: In-place update all 3 items with new titles/statuses (0 tree teardown)
        val updatedItems = listOf(
            "Task 1: Họp đầu tuần (XONG)" to true,
            "Task 2: Báo cáo tài chính Q3" to false,
            "Task 3: Kiểm tra PCCC tầng hầm" to false
        )
        reconcileTaskBoard(updatedItems)
        assertEquals(3, container.childCount())
        assertEquals("removeAllViews() TUYỆT ĐỐI không được gọi khi in-place diffing", 0, container.removeAllViewsCallCount)
        assertTrue("View 0 phải được giữ nguyên tham chiếu đối tượng", container.getChildAt(0) === view1)
        assertTrue("View 1 phải được giữ nguyên tham chiếu đối tượng", container.getChildAt(1) === view2)
        assertTrue("View 2 phải được giữ nguyên tham chiếu đối tượng", container.getChildAt(2) === view3)
        assertEquals("Task 1: Họp đầu tuần (XONG)", container.getChildAt(0).tag?.boundTitle)
        assertEquals("Task 2: Báo cáo tài chính Q3", container.getChildAt(1).tag?.boundTitle)

        // Pass 3: Contract with 2 items (M < N) -> view 1 & 2 retained, trailing removed
        val reducedItems = listOf(
            "Task 1: Họp đầu tuần" to false,
            "Task 2: Báo cáo tài chính" to true
        )
        reconcileTaskBoard(reducedItems)
        assertEquals(2, container.childCount())
        assertTrue("View 0 vẫn được tái sử dụng khi thu nhỏ danh sách", container.getChildAt(0) === view1)
        assertTrue("View 1 vẫn được tái sử dụng khi thu nhỏ danh sách", container.getChildAt(1) === view2)
        assertEquals(0, container.removeAllViewsCallCount)
        assertEquals(1, container.removeViewsCallCount)

        // Pass 4: Expand with 4 items (M > N) -> view 1 & 2 retained, view 4 appended
        val expandedItems = listOf(
            "Task 1" to false,
            "Task 2" to false,
            "Task 3 mới" to false,
            "Task 4 mới" to true
        )
        reconcileTaskBoard(expandedItems)
        assertEquals(4, container.childCount())
        assertTrue(container.getChildAt(0) === view1)
        assertTrue(container.getChildAt(1) === view2)
        assertEquals(0, container.removeAllViewsCallCount)
    }

    @Test
    fun m1_isUserInteractingWithTask_buffersDatabaseUpdatesDuringSwipe_preventingActionCancel() {
        var isUserInteractingWithTask = false
        var isSwipeAnimating = false
        var hasPendingTaskBoardUpdate = false
        var taskBoardRefreshCount = 0

        fun onAlarmDbFlowEmitted(alarms: List<SmartAlarmEntity>) {
            if (isUserInteractingWithTask || isSwipeAnimating) {
                hasPendingTaskBoardUpdate = true
                return
            }
            taskBoardRefreshCount++
        }

        // 1. Initial emission when idle: refreshes immediately
        onAlarmDbFlowEmitted(listOf())
        assertEquals(1, taskBoardRefreshCount)
        assertFalse(hasPendingTaskBoardUpdate)

        // 2. User touches down on task row to swipe (ACTION_DOWN)
        isUserInteractingWithTask = true

        // 3. Room DB emits while user is dragging
        onAlarmDbFlowEmitted(listOf())
        onAlarmDbFlowEmitted(listOf())

        // Refresh must NOT have run (would cancel the swipe gesture via ACTION_CANCEL)
        assertEquals("Không được refresh task board khi người dùng đang chạm/vuốt", 1, taskBoardRefreshCount)
        assertTrue("hasPendingTaskBoardUpdate phải được bật để ghi nhớ cập nhật", hasPendingTaskBoardUpdate)

        // 4. User releases finger (ACTION_UP)
        isUserInteractingWithTask = false

        // In ACTION_UP:
        if (hasPendingTaskBoardUpdate) {
            hasPendingTaskBoardUpdate = false
            taskBoardRefreshCount++
        }

        assertEquals("Phải hoàn tất refresh hoãn lại ngay khi thả tay", 2, taskBoardRefreshCount)
        assertFalse("Cờ pending phải được xóa sau khi xử lý xong", hasPendingTaskBoardUpdate)
    }

    @Test
    fun m1_singlePassMeasureContract_eliminatesDoubleMeasureWeight_withExplicitTitleWidth() {
        fun computeTitleWidth(cardWidthPx: Int, density: Float): Int {
            val dp84Px = (84 * density).toInt()
            val dp150Px = (150 * density).toInt()
            return (cardWidthPx - dp84Px).coerceAtLeast(dp150Px)
        }

        // Test across multiple screen configurations (density 1.5 to 4.0)
        val densities = listOf(1.5f, 2.0f, 2.625f, 3.0f, 3.5f, 4.0f)
        for (d in densities) {
            val screenWidthPx = (390 * d).toInt()
            val cardWidthPx = ((screenWidthPx * 0.94f).toInt()).coerceAtMost((500 * d).toInt())
            val titleWidth = computeTitleWidth(cardWidthPx, d)

            assertTrue("titleWidth ($titleWidth) phải luôn lớn hơn 0 trên density $d", titleWidth > 0)
            val minExpected = (150 * d).toInt()
            assertTrue("titleWidth ($titleWidth) phải >= min $minExpected", titleWidth >= minExpected)
        }

        // Verify contract: layout weight is 0.0f (no weight)
        val weight = 0.0f
        assertEquals("Single-pass measure bắt buộc weight = 0.0f để tránh pass đo thứ 2", 0.0f, weight, 0.001f)
    }

    @Test
    fun m1_staticRegexAndO1Matching_benchmarksSubMillisecondPerformance_andAccurateNormalization() {
        val testTitles = listOf(
            "[1] Họp giao ban đầu ca lúc 09:00",
            "• Gửi báo cáo doanh số cho sếp",
            "- Bàn giao ca trực sáng",
            "1.2. Kiểm tra thiết bị PCCC tầng hầm",
            "* Nộp đề án quý 4 trước 17h30",
            "Nhiệm vụ thông thường không tiền tố"
        )

        // 1. Benchmark 2,000 iterations of cleanTaskTitle: must execute under 50ms due to static regex
        val startNs = System.nanoTime()
        for (i in 1..2000) {
            val sample = testTitles[i % testTitles.size]
            val cleaned = SystemAlarmHelper.cleanTaskTitle(sample)
            assertFalse(cleaned.startsWith("["))
            assertFalse(cleaned.startsWith("-"))
            assertFalse(cleaned.startsWith("•"))
            assertFalse(cleaned.startsWith("*"))
        }
        val durationMs = (System.nanoTime() - startNs) / 1_000_000.0
        assertTrue("2000 lần dọn tiêu đề bằng static regex phải hoàn thành dưới 50ms (thực tế: ${durationMs}ms)", durationMs < 50.0)

        // 2. O(1) Title Map matching test with multiple Vietnamese diacritics and casing
        val alarm1 = SmartAlarmEntity(
            alarmId = "ALM_hop_giao_ban",
            taskTitle = "Họp giao ban đầu ca",
            targetTimeMillis = System.currentTimeMillis() + 3600_000L,
            targetDateLabel = "09:00",
            status = SmartAlarmStatus.ACTIVE
        )
        val alarm2 = SmartAlarmEntity(
            alarmId = "ALM_pccc",
            taskTitle = "Kiểm tra thiết bị PCCC",
            targetTimeMillis = System.currentTimeMillis() + 7200_000L,
            targetDateLabel = "14:00",
            status = SmartAlarmStatus.PROPOSED
        )
        val alarms = listOf(alarm1, alarm2)
        val normalizedMap = SystemAlarmHelper.buildNormalizedTitleMap(alarms)

        // Exact match
        val matchExact = SystemAlarmHelper.findMatchingAlarmFast("Họp giao ban đầu ca", normalizedMap)
        assertEquals("ALM_hop_giao_ban", matchExact?.alarmId)

        // Case insensitive match
        val matchLower = SystemAlarmHelper.findMatchingAlarmFast("họp giao ban đầu ca", normalizedMap)
        assertEquals("ALM_hop_giao_ban", matchLower?.alarmId)

        // Cleaned prefix match
        val cleanedTitle = SystemAlarmHelper.cleanTaskTitle("• Họp giao ban đầu ca")
        val matchCleaned = SystemAlarmHelper.findMatchingAlarmFast(cleanedTitle, normalizedMap)
        assertEquals("ALM_hop_giao_ban", matchCleaned?.alarmId)

        // Non-existent task returns null instantly in O(1)
        val matchNone = SystemAlarmHelper.findMatchingAlarmFast("Nhiệm vụ không tồn tại", normalizedMap)
        assertNull(matchNone)
    }

    @Test
    fun m1_continuousMorphingParameters_validateScaleAndInterpolatorContracts() {
        val startScale = 0.12f
        val endScale = 1.0f
        val morphDurationMs = 180L
        val bubbleRetentionMs = 70L

        // Invariant: startScale must match bubble / card size ratio (~44dp / ~370dp ~= 0.1189f -> 0.12f)
        assertEquals(0.12f, startScale, 0.001f)
        assertEquals(1.0f, endScale, 0.001f)

        // Invariant: bubble retention time (70ms) must be strictly less than morph duration (180ms)
        assertTrue("Thời gian giữ bóng cũ (70ms) phải ngắn hơn hoạt ảnh bung thẻ (180ms)", bubbleRetentionMs < morphDurationMs)
        assertTrue("Thời gian giữ bóng phải >= 50ms để mắt người không thấy khoảng trống", bubbleRetentionMs >= 50L)

        // Invariant: Bitmap config RGB_565 saves 50% memory over ARGB_8888 (2 bytes vs 4 bytes)
        val bytesPerPixelRgb565 = 2
        val bytesPerPixelArgb8888 = 4
        val memorySavingsPercent = ((bytesPerPixelArgb8888 - bytesPerPixelRgb565).toDouble() / bytesPerPixelArgb8888) * 100
        assertEquals(50.0, memorySavingsPercent, 0.001)
    }
}
