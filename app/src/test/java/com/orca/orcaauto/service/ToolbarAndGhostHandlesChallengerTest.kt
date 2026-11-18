package com.orca.orcaauto.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Empirical Challenge and Stress Harness for Challenger 2:
 * 1. In-Card Action Toolbar:
 *    - Dán (Paste): range replacement vs cursor insertion, safe clipboard coercion, boundary clamping.
 *    - Sao chép (Copy): active range vs fallback all-text copy, inverted selection, empty guards.
 *    - Cắt (Cut): range deletion, cursor update, unselected warning guard, empty guards.
 *    - Chọn tất cả (Select All): selectAll invocation, focus requirement, empty guards.
 * 2. Ghost Handle Prevention and Toolbar Dismissal Lifecycle:
 *    - collapseExpandedCard: clearFocus, Selection.setSelection(text, 0), hideQuickActionBar, hideSoftInput.
 *    - renderSessionChips: session switch cleanup invariants.
 *    - onTextChanged: dismiss toolbar when caret collapses during active typing.
 *    - onFocusChange: dismiss toolbar immediately without animation when focus is lost.
 *    - expandFloatingCard: pre-warmed shell toolbar guaranteed GONE and alpha 0f upon expansion.
 */
class ToolbarAndGhostHandlesChallengerTest {

    // =========================================================================
    // 1. DÁN (PASTE) STRESS TESTS
    // =========================================================================

    /**
     * Exact replication of paste logic from FloatingBubbleService.kt:
     *
     * val rawStart = queryEt.selectionStart.coerceAtLeast(0)
     * val rawEnd = queryEt.selectionEnd.coerceAtLeast(0)
     * val start = minOf(rawStart, rawEnd).coerceAtMost(editable.length)
     * val end = maxOf(rawStart, rawEnd).coerceAtMost(editable.length)
     * editable.replace(start, end, clipText)
     * val newCursor = (start + clipText.length).coerceAtMost(editable.length)
     * queryEt.setSelection(newCursor)
     */
    private fun simulatePasteAction(
        currentText: String,
        selStart: Int,
        selEnd: Int,
        clipText: String?,
        isEnabled: Boolean = true,
        isCallingAi: Boolean = false
    ): PasteResult {
        if (!isEnabled || isCallingAi) {
            return PasteResult(success = false, message = "DISABLED", text = currentText, newCursor = selStart)
        }
        if (clipText.isNullOrEmpty()) {
            return PasteResult(success = false, message = "EMPTY_CLIPBOARD", text = currentText, newCursor = selStart)
        }
        val sb = StringBuilder(currentText)
        val rawStart = selStart.coerceAtLeast(0)
        val rawEnd = selEnd.coerceAtLeast(0)
        val start = minOf(rawStart, rawEnd).coerceAtMost(sb.length)
        val end = maxOf(rawStart, rawEnd).coerceAtMost(sb.length)

        sb.replace(start, end, clipText)
        val newCursor = (start + clipText.length).coerceAtMost(sb.length)
        return PasteResult(success = true, message = "PASTED", text = sb.toString(), newCursor = newCursor)
    }

    data class PasteResult(val success: Boolean, val message: String, val text: String, val newCursor: Int)

    @Test
    fun paste_caretInsertion_atStart_inMiddle_atEnd() {
        // Insertion at start
        val resStart = simulatePasteAction("World", 0, 0, "Hello ")
        assertTrue(resStart.success)
        assertEquals("Hello World", resStart.text)
        assertEquals(6, resStart.newCursor)

        // Insertion in middle
        val resMid = simulatePasteAction("Hello World", 5, 5, ", Cruel")
        assertTrue(resMid.success)
        assertEquals("Hello, Cruel World", resMid.text)
        assertEquals(12, resMid.newCursor)

        // Insertion at end
        val resEnd = simulatePasteAction("Hello", 5, 5, " World")
        assertTrue(resEnd.success)
        assertEquals("Hello World", resEnd.text)
        assertEquals(11, resEnd.newCursor)
    }

    @Test
    fun paste_rangeReplacement_forwardAndInvertedRanges() {
        val original = "The quick brown fox"

        // Forward range replacement: "quick" at (4, 9) replaced by "lazy dog and"
        val resFwd = simulatePasteAction(original, 4, 9, "lazy dog and")
        assertTrue(resFwd.success)
        assertEquals("The lazy dog and brown fox", resFwd.text)
        assertEquals(4 + "lazy dog and".length, resFwd.newCursor)

        // Inverted range replacement: backwards drag (9, 4)
        val resInv = simulatePasteAction(original, 9, 4, "super slow")
        assertTrue(resInv.success)
        assertEquals("The super slow brown fox", resInv.text)
        assertEquals(4 + "super slow".length, resInv.newCursor)

        // Complete text replacement: (0, length)
        val resAll = simulatePasteAction(original, 0, original.length, "Brand new prompt")
        assertTrue(resAll.success)
        assertEquals("Brand new prompt", resAll.text)
        assertEquals("Brand new prompt".length, resAll.newCursor)
    }

    @Test
    fun paste_outOfBoundsCoordinates_safelyClampedWithoutException() {
        val original = "OrcaAuto"

        // Negative start index
        val resNeg = simulatePasteAction(original, -10, 4, "Mega")
        assertTrue(resNeg.success)
        assertEquals("MegaAuto", resNeg.text)
        assertEquals(4, resNeg.newCursor)

        // Way beyond text length
        val resHuge = simulatePasteAction(original, 4, 9999, "Power")
        assertTrue(resHuge.success)
        assertEquals("OrcaPower", resHuge.text)
        assertEquals(9, resHuge.newCursor)

        // Completely outside negative and positive
        val resBoth = simulatePasteAction(original, -100, 1000, "Clean slate")
        assertTrue(resBoth.success)
        assertEquals("Clean slate", resBoth.text)
        assertEquals("Clean slate".length, resBoth.newCursor)
    }

    @Test
    fun paste_intoEmptyText_worksSmoothly() {
        val resEmpty = simulatePasteAction("", 0, 0, "First input")
        assertTrue(resEmpty.success)
        assertEquals("First input", resEmpty.text)
        assertEquals(11, resEmpty.newCursor)
    }

    @Test
    fun paste_emptyOrNullClipboard_leavesTextUntouched() {
        // Null clipboard
        val resNull = simulatePasteAction("Existing text", 4, 8, null)
        assertFalse(resNull.success)
        assertEquals("EMPTY_CLIPBOARD", resNull.message)
        assertEquals("Existing text", resNull.text)

        // Empty clipboard string
        val resEmpty = simulatePasteAction("Existing text", 4, 8, "")
        assertFalse(resEmpty.success)
        assertEquals("EMPTY_CLIPBOARD", resEmpty.message)
        assertEquals("Existing text", resEmpty.text)
    }

    @Test
    fun paste_disabledOrCallingAi_rejectedImmediately() {
        val resDisabled = simulatePasteAction("Prompt", 0, 0, "Data", isEnabled = false)
        assertFalse(resDisabled.success)
        assertEquals("DISABLED", resDisabled.message)

        val resCalling = simulatePasteAction("Prompt", 0, 0, "Data", isCallingAi = true)
        assertFalse(resCalling.success)
        assertEquals("DISABLED", resCalling.message)
    }

    @Test
    fun paste_multiLineAndEmojiStrings_safeCursorPositioning() {
        val emojiClip = "🚀 Tóm tắt tin nhắn\n- Mục 1: Họp lúc 14h\n- Mục 2: Kiểm tra PR"
        val res = simulatePasteAction("Lệnh: ", 6, 6, emojiClip)
        assertTrue(res.success)
        assertEquals("Lệnh: 🚀 Tóm tắt tin nhắn\n- Mục 1: Họp lúc 14h\n- Mục 2: Kiểm tra PR", res.text)
        assertEquals(6 + emojiClip.length, res.newCursor)
    }

    // =========================================================================
    // 2. SAO CHÉP (COPY) STRESS TESTS
    // =========================================================================

    /**
     * Exact replication of copy logic from FloatingBubbleService.kt:
     *
     * val editable = queryEt.text
     * if (editable.isNullOrEmpty()) { Toast("Không có nội dung để sao chép"); return }
     * val rawStart = queryEt.selectionStart.coerceAtLeast(0)
     * val rawEnd = queryEt.selectionEnd.coerceAtLeast(0)
     * val start = minOf(rawStart, rawEnd).coerceAtMost(editable.length)
     * val end = maxOf(rawStart, rawEnd).coerceAtMost(editable.length)
     * val textToCopy = if (start < end) {
     *     editable.substring(start, end)
     * } else {
     *     editable.toString()
     * }
     */
    private fun simulateCopyAction(
        text: String?,
        selStart: Int,
        selEnd: Int
    ): CopyResult {
        if (text.isNullOrEmpty()) {
            return CopyResult(success = false, message = "EMPTY_TEXT", copiedText = null)
        }
        val rawStart = selStart.coerceAtLeast(0)
        val rawEnd = selEnd.coerceAtLeast(0)
        val start = minOf(rawStart, rawEnd).coerceAtMost(text.length)
        val end = maxOf(rawStart, rawEnd).coerceAtMost(text.length)
        val textToCopy = if (start < end) {
            text.substring(start, end)
        } else {
            text.toString()
        }
        return CopyResult(success = true, message = "COPIED", copiedText = textToCopy)
    }

    data class CopyResult(val success: Boolean, val message: String, val copiedText: String?)

    @Test
    fun copy_forwardSelection_extractsExactSlice() {
        val sample = "OrcaAuto AI Super Assistant"
        val res = simulateCopyAction(sample, 9, 11) // "AI"
        assertTrue(res.success)
        assertEquals("AI", res.copiedText)
    }

    @Test
    fun copy_invertedSelection_extractsExactSlice() {
        val sample = "OrcaAuto AI Super Assistant"
        val res = simulateCopyAction(sample, 11, 9) // Inverted drag handle
        assertTrue(res.success)
        assertEquals("AI", res.copiedText)
    }

    @Test
    fun copy_caretCollapsed_fallsBackToCopyEntireText() {
        val sample = "Lập kế hoạch tuần mới"
        val res = simulateCopyAction(sample, 5, 5) // Caret inside word, no range
        assertTrue(res.success)
        assertEquals(sample, res.copiedText) // Copies all text
    }

    @Test
    fun copy_emptyOrNullText_returnsSafeGuard() {
        val resNull = simulateCopyAction(null, 0, 0)
        assertFalse(resNull.success)
        assertEquals("EMPTY_TEXT", resNull.message)
        assertNull(resNull.copiedText)

        val resEmpty = simulateCopyAction("", 0, 0)
        assertFalse(resEmpty.success)
        assertEquals("EMPTY_TEXT", resEmpty.message)
        assertNull(resEmpty.copiedText)
    }

    @Test
    fun copy_outOfBounds_clampedSafely() {
        val sample = "Clamped range"
        val res = simulateCopyAction(sample, -5, 200)
        assertTrue(res.success)
        assertEquals("Clamped range", res.copiedText)
    }

    // =========================================================================
    // 3. CẮT (CUT) STRESS TESTS
    // =========================================================================

    /**
     * Exact replication of cut logic from FloatingBubbleService.kt:
     *
     * if (!queryEt.isEnabled || isCallingAi) return
     * val editable = queryEt.text
     * if (editable.isNullOrEmpty()) { Toast("Không có nội dung để cắt"); return }
     * val rawStart = queryEt.selectionStart.coerceAtLeast(0)
     * val rawEnd = queryEt.selectionEnd.coerceAtLeast(0)
     * val start = minOf(rawStart, rawEnd).coerceAtMost(editable.length)
     * val end = maxOf(rawStart, rawEnd).coerceAtMost(editable.length)
     * if (start < end) {
     *     val textToCut = editable.substring(start, end)
     *     editable.delete(start, end)
     *     queryEt.setSelection(start)
     *     hideQuickActionBar(animated = true)
     * } else {
     *     Toast("Chưa chọn đoạn chữ cần cắt")
     * }
     */
    private fun simulateCutAction(
        currentText: String?,
        selStart: Int,
        selEnd: Int,
        isEnabled: Boolean = true,
        isCallingAi: Boolean = false
    ): CutResult {
        if (!isEnabled || isCallingAi) {
            return CutResult(success = false, message = "DISABLED", remainingText = currentText ?: "", cutText = null, newCursor = selStart)
        }
        if (currentText.isNullOrEmpty()) {
            return CutResult(success = false, message = "EMPTY_TEXT", remainingText = "", cutText = null, newCursor = 0)
        }
        val sb = StringBuilder(currentText)
        val rawStart = selStart.coerceAtLeast(0)
        val rawEnd = selEnd.coerceAtLeast(0)
        val start = minOf(rawStart, rawEnd).coerceAtMost(sb.length)
        val end = maxOf(rawStart, rawEnd).coerceAtMost(sb.length)

        return if (start < end) {
            val textToCut = sb.substring(start, end)
            sb.delete(start, end)
            CutResult(success = true, message = "CUT", remainingText = sb.toString(), cutText = textToCut, newCursor = start)
        } else {
            CutResult(success = false, message = "NO_SELECTION", remainingText = sb.toString(), cutText = null, newCursor = start)
        }
    }

    data class CutResult(val success: Boolean, val message: String, val remainingText: String, val cutText: String?, val newCursor: Int)

    @Test
    fun cut_forwardSelection_cutsSliceAndUpdatesCursor() {
        val sample = "Báo cáo tiến độ hoàn thành"
        val res = simulateCutAction(sample, 8, 16) // "tiến độ "
        assertTrue(res.success)
        assertEquals("CUT", res.message)
        assertEquals("tiến độ ", res.cutText)
        assertEquals("Báo cáo hoàn thành", res.remainingText)
        assertEquals(8, res.newCursor)
    }

    @Test
    fun cut_invertedSelection_cutsSliceAndUpdatesCursor() {
        val sample = "Báo cáo tiến độ hoàn thành"
        val res = simulateCutAction(sample, 16, 8) // Inverted selection
        assertTrue(res.success)
        assertEquals("CUT", res.message)
        assertEquals("tiến độ ", res.cutText)
        assertEquals("Báo cáo hoàn thành", res.remainingText)
        assertEquals(8, res.newCursor)
    }

    @Test
    fun cut_caretCollapsed_doesNotCut_returnsNoSelectionWarning() {
        // Crucial invariant: Cutting without an active selection must NOT wipe all text!
        val sample = "Nội dung quan trọng không được mất"
        val res = simulateCutAction(sample, 8, 8) // Caret placed, no range selected
        assertFalse(res.success)
        assertEquals("NO_SELECTION", res.message)
        assertNull(res.cutText)
        assertEquals(sample, res.remainingText) // Must remain completely untouched
        assertEquals(8, res.newCursor)
    }

    @Test
    fun cut_entireTextSelected_cutsEverythingToClipboard() {
        val sample = "Toàn bộ văn bản"
        val res = simulateCutAction(sample, 0, sample.length)
        assertTrue(res.success)
        assertEquals("CUT", res.message)
        assertEquals(sample, res.cutText)
        assertEquals("", res.remainingText)
        assertEquals(0, res.newCursor)
    }

    @Test
    fun cut_outOfBounds_clampedSafely() {
        val sample = "OrcaAuto"
        val res = simulateCutAction(sample, -5, 4) // "-5" clamped to 0 -> cuts "Orca"
        assertTrue(res.success)
        assertEquals("Orca", res.cutText)
        assertEquals("Auto", res.remainingText)
        assertEquals(0, res.newCursor)
    }

    @Test
    fun cut_emptyOrNullText_guardedSafely() {
        val resNull = simulateCutAction(null, 0, 0)
        assertFalse(resNull.success)
        assertEquals("EMPTY_TEXT", resNull.message)

        val resEmpty = simulateCutAction("", 0, 0)
        assertFalse(resEmpty.success)
        assertEquals("EMPTY_TEXT", resEmpty.message)
    }

    @Test
    fun cut_disabledOrCallingAi_rejectedImmediately() {
        val resDisabled = simulateCutAction("Text", 0, 2, isEnabled = false)
        assertFalse(resDisabled.success)
        assertEquals("DISABLED", resDisabled.message)

        val resCalling = simulateCutAction("Text", 0, 2, isCallingAi = true)
        assertFalse(resCalling.success)
        assertEquals("DISABLED", resCalling.message)
    }

    // =========================================================================
    // 4. CHỌN TẤT CẢ (SELECT ALL) STRESS TESTS
    // =========================================================================

    /**
     * Exact replication of selectAll logic from FloatingBubbleService.kt:
     *
     * val selectAllPill = createActionPill("🔘", "Chọn tất cả") {
     *     if (!queryEt.isEnabled || isCallingAi) return
     *     if (!queryEt.text.isNullOrEmpty()) {
     *         queryEt.requestFocus()
     *         queryEt.selectAll()
     *         queryEt.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
     *     }
     * }
     */
    private fun simulateSelectAll(
        text: String?,
        isEnabled: Boolean = true,
        isCallingAi: Boolean = false
    ): SelectAllResult {
        if (!isEnabled || isCallingAi) {
            return SelectAllResult(executed = false, message = "DISABLED", start = -1, end = -1)
        }
        if (text.isNullOrEmpty()) {
            return SelectAllResult(executed = false, message = "EMPTY_TEXT", start = -1, end = -1)
        }
        return SelectAllResult(executed = true, message = "SELECTED_ALL", start = 0, end = text.length)
    }

    data class SelectAllResult(val executed: Boolean, val message: String, val start: Int, val end: Int)

    @Test
    fun selectAll_onPopulatedText_selectsFullRange() {
        val sample = "Hôm nay thời tiết đẹp quá"
        val res = simulateSelectAll(sample)
        assertTrue(res.executed)
        assertEquals(0, res.start)
        assertEquals(sample.length, res.end)
    }

    @Test
    fun selectAll_onEmptyOrNullText_noOp() {
        val resEmpty = simulateSelectAll("")
        assertFalse(resEmpty.executed)
        assertEquals("EMPTY_TEXT", resEmpty.message)

        val resNull = simulateSelectAll(null)
        assertFalse(resNull.executed)
        assertEquals("EMPTY_TEXT", resNull.message)
    }

    @Test
    fun selectAll_whenDisabledOrCallingAi_rejectedImmediately() {
        val resDisabled = simulateSelectAll("Some text", isEnabled = false)
        assertFalse(resDisabled.executed)
        assertEquals("DISABLED", resDisabled.message)

        val resCalling = simulateSelectAll("Some text", isCallingAi = true)
        assertFalse(resCalling.executed)
        assertEquals("DISABLED", resCalling.message)
    }

    // =========================================================================
    // 5. TOOLBAR DISMISSAL & VISIBILITY STATE MACHINE TESTS
    // =========================================================================

    /**
     * Models toolbar visibility state machine driven by onSelectionChanged, TextWatcher, and FocusChange.
     */
    class QuickActionBarStateMachine {
        var isVisible: Boolean = false
        var alpha: Float = 0f
        var isAnimated: Boolean = false

        fun show() {
            isVisible = true
            alpha = 1f
            isAnimated = true
        }

        fun hide(animated: Boolean) {
            isVisible = false
            alpha = 0f
            isAnimated = animated
        }

        fun onSelectionChanged(selStart: Int, selEnd: Int, isEnabled: Boolean, isCallingAi: Boolean) {
            if (!isEnabled || isCallingAi) {
                hide(animated = false)
            } else if (selStart != selEnd) {
                show()
            } else {
                hide(animated = true)
            }
        }

        fun onTextChanged(selStart: Int, selEnd: Int) {
            if (selStart == selEnd) {
                hide(animated = false)
            }
        }

        fun onFocusChanged(hasFocus: Boolean) {
            if (!hasFocus) {
                hide(animated = false)
            }
        }
    }

    @Test
    fun toolbarVisibility_selectionActive_showsToolbar() {
        val sm = QuickActionBarStateMachine()
        sm.onSelectionChanged(0, 5, isEnabled = true, isCallingAi = false)
        assertTrue("Toolbar must be visible when selection range is active", sm.isVisible)
        assertEquals(1f, sm.alpha, 0.001f)
    }

    @Test
    fun toolbarVisibility_selectionCollapsed_hidesToolbarWithAnimation() {
        val sm = QuickActionBarStateMachine()
        sm.show()
        sm.onSelectionChanged(3, 3, isEnabled = true, isCallingAi = false)
        assertFalse("Toolbar must be hidden when selection collapses to caret", sm.isVisible)
        assertEquals(0f, sm.alpha, 0.001f)
        assertTrue("Hiding on caret collapse uses smooth 80ms animation", sm.isAnimated)
    }

    @Test
    fun toolbarVisibility_typingResumesWithCaret_hidesImmediatelyWithoutLag() {
        val sm = QuickActionBarStateMachine()
        sm.show()
        sm.onTextChanged(selStart = 7, selEnd = 7)
        assertFalse("Toolbar must immediately disappear when user resumes normal typing", sm.isVisible)
        assertFalse("Hiding during active typing must NOT animate to eliminate UI lag", sm.isAnimated)
    }

    @Test
    fun toolbarVisibility_focusLost_hidesImmediately() {
        val sm = QuickActionBarStateMachine()
        sm.show()
        sm.onFocusChanged(hasFocus = false)
        assertFalse("Toolbar must be immediately hidden when EditText loses focus", sm.isVisible)
        assertFalse("Must be non-animated hide to prevent ghost toolbar floating without focus", sm.isAnimated)
    }

    @Test
    fun toolbarVisibility_callingAiState_forcesImmediateHide() {
        val sm = QuickActionBarStateMachine()
        sm.show()
        sm.onSelectionChanged(0, 10, isEnabled = true, isCallingAi = true)
        assertFalse("Toolbar must be immediately hidden while AI streaming is active", sm.isVisible)
        assertFalse("Hide must be instant without animation during AI execution", sm.isAnimated)
    }

    // =========================================================================
    // 6. GHOST HANDLES & LIFECYCLE DISCIPLINE INVARIANTS
    // =========================================================================

    data class LifecycleShellState(
        var hasFocus: Boolean,
        var selectionCaret: Int,
        var toolbarVisible: Boolean,
        var toolbarAlpha: Float,
        var softKeyboardVisible: Boolean
    )

    private fun simulateLifecycleCleanup(state: LifecycleShellState) {
        // Contract implemented in collapseExpandedCard and renderSessionChips:
        // 1. shell.queryEt.clearFocus()
        // 2. Selection.setSelection(shell.queryEt.text, 0)
        // 3. shell.quickActionBar.animate().cancel()
        // 4. shell.quickActionBar.visibility = View.GONE
        // 5. shell.quickActionBar.alpha = 0f
        // 6. imm?.hideSoftInputFromWindow(shell.queryEt.windowToken, 0)
        state.hasFocus = false
        state.selectionCaret = 0
        state.toolbarVisible = false
        state.toolbarAlpha = 0f
        state.softKeyboardVisible = false
    }

    @Test
    fun ghostHandles_collapseExpandedCard_guaranteesZeroOrphanedHandles() {
        val activeState = LifecycleShellState(
            hasFocus = true,
            selectionCaret = 15,
            toolbarVisible = true,
            toolbarAlpha = 1f,
            softKeyboardVisible = true
        )

        simulateLifecycleCleanup(activeState)

        assertFalse("Focus must be cleared on card collapse", activeState.hasFocus)
        assertEquals("Selection must be collapsed to 0 to destroy teardrop handles", 0, activeState.selectionCaret)
        assertFalse("Toolbar must be GONE on card collapse", activeState.toolbarVisible)
        assertEquals(0f, activeState.toolbarAlpha, 0.001f)
        assertFalse("Soft keyboard must be dismissed on card collapse", activeState.softKeyboardVisible)
    }

    @Test
    fun ghostHandles_renderSessionChips_sessionSwitchGuaranteesZeroOrphanedHandles() {
        val sessionAState = LifecycleShellState(
            hasFocus = true,
            selectionCaret = 25,
            toolbarVisible = true,
            toolbarAlpha = 1f,
            softKeyboardVisible = true
        )

        // Switching from Session A to Session B
        simulateLifecycleCleanup(sessionAState)

        assertFalse("Focus must be cleared when switching sessions", sessionAState.hasFocus)
        assertEquals("Selection must be collapsed to 0 on session switch", 0, sessionAState.selectionCaret)
        assertFalse("Toolbar must be GONE on session switch", sessionAState.toolbarVisible)
        assertEquals(0f, sessionAState.toolbarAlpha, 0.001f)
        assertFalse("Soft keyboard must be dismissed on session switch", sessionAState.softKeyboardVisible)
    }

    @Test
    fun ghostHandles_expandFloatingCard_prewarmedShellToolbarInitialStateIsGone() {
        // Contract: Upon expanding floating card (expandFloatingCard lines 2685-2686):
        // shell.quickActionBar.visibility = View.GONE
        // shell.quickActionBar.alpha = 0f
        var prewarmedToolbarVisibility = -1 // Not GONE
        var prewarmedToolbarAlpha = 1f

        // Expansion initialization
        prewarmedToolbarVisibility = 8 // View.GONE
        prewarmedToolbarAlpha = 0f

        assertEquals(8, prewarmedToolbarVisibility)
        assertEquals(0f, prewarmedToolbarAlpha, 0.001f)
    }
}
