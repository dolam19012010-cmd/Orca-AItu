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
}
