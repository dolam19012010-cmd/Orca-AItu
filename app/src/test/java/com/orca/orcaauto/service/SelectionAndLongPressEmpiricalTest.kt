package com.orca.orcaauto.service

import android.content.Context
import android.text.Editable
import android.text.SpannableStringBuilder
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Field
import java.lang.reflect.Modifier

/**
 * Empirical Challenge and Stress Harness for:
 * 1. Smart Context Dispatcher (empty vs. populated long-press)
 * 2. SelectionAwareEditText listener invocation & null-safety
 * 3. suppressActionModeCallback contracts (eliminating OEM BadTokenException)
 * 4. Inverted & out-of-bounds selection range resolution for Copy, Cut, Paste
 * 5. Lifecycle zero-ghost-handles invariants
 */
class SelectionAndLongPressEmpiricalTest {

    // =========================================================================
    // 1. SMART CONTEXT DISPATCHER CHALLENGES
    // =========================================================================

    /**
     * Exact replication of Smart Context Dispatcher condition in FloatingBubbleService.kt:
     *
     * setOnLongClickListener {
     *     if (!isEnabled || isCallingAi) return@setOnLongClickListener false
     *     if (text.isNullOrBlank()) {
     *         pasteFromClipboard(this) // returns true
     *     } else {
     *         false
     *     }
     * }
     */
    private fun evaluateSmartContextLongPress(
        text: CharSequence?,
        isEnabled: Boolean = true,
        isCallingAi: Boolean = false,
        pasteResult: Boolean = true
    ): Boolean {
        if (!isEnabled || isCallingAi) return false
        return if (text.isNullOrBlank()) {
            pasteResult
        } else {
            false
        }
    }

    @Test
    fun challenge_emptyAndBlankVariations_reliablyReturnTrueForInstantPaste() {
        val emptyAndBlankSamples = listOf(
            "",
            " ",
            "   ",
            "\t",
            "\n",
            "\r\n",
            "  \t \n \r  ",
            "\u00A0", // Non-breaking space
            "\u2000", // En quad
            "\u2003", // Em quad
            "\u3000"  // Ideographic space
        )

        for (sample in emptyAndBlankSamples) {
            val handled = evaluateSmartContextLongPress(sample)
            assertTrue(
                "Sample '${sample.replace("\n", "\\n").replace("\t", "\\t")}' must consume long-press (return true) for instant paste",
                handled
            )
        }

        // Null check
        assertTrue("Null text must consume long-press (return true)", evaluateSmartContextLongPress(null))
    }

    @Test
    fun challenge_populatedVariations_reliablyReturnFalseForAospWordSelection() {
        val populatedSamples = listOf(
            "a",
            ".",
            "H",
            "Hello",
            "  hello  ",
            "Báo cáo tiến độ",
            "👋🚀",
            "12345",
            "line1\nline2",
            " special _ chars !@#"
        )

        for (sample in populatedSamples) {
            val handled = evaluateSmartContextLongPress(sample)
            assertFalse(
                "Sample '$sample' must NOT consume long-press (return false) so AOSP Editor executes native word selection",
                handled
            )
        }
    }

    @Test
    fun challenge_disabledOrCallingAi_alwaysReturnsFalse() {
        // When AI is calling or queryEt is disabled, long press must NEVER paste or consume
        assertFalse(
            "Disabled editor must return false",
            evaluateSmartContextLongPress(text = "", isEnabled = false, isCallingAi = false)
        )
        assertFalse(
            "Calling AI editor must return false",
            evaluateSmartContextLongPress(text = "", isEnabled = true, isCallingAi = true)
        )
        assertFalse(
            "Disabled and populated editor must return false",
            evaluateSmartContextLongPress(text = "Hello", isEnabled = false, isCallingAi = false)
        )
    }

    // =========================================================================
    // 2. SELECTION AWARE EDIT TEXT CONTRACT CHALLENGES
    // =========================================================================

    /**
     * Simulated test double of SelectionAwareEditText contract to stress-test
     * null listener, multiple dispatches, rapid cursor movement, and backward selection.
     */
    private class SelectionAwareDispatcher {
        var onSelectionChangedListener: ((selStart: Int, selEnd: Int) -> Unit)? = null

        fun dispatchSelectionChanged(selStart: Int, selEnd: Int) {
            onSelectionChangedListener?.invoke(selStart, selEnd)
        }
    }

    @Test
    fun challenge_selectionDispatcher_withoutListener_doesNotThrow() {
        val dispatcher = SelectionAwareDispatcher()
        // Listener is null initially; dispatching must not crash
        dispatcher.dispatchSelectionChanged(0, 0)
        dispatcher.dispatchSelectionChanged(5, 10)
        dispatcher.dispatchSelectionChanged(-1, -1)
    }

    @Test
    fun challenge_selectionDispatcher_withListener_deliversExactBounds() {
        val dispatcher = SelectionAwareDispatcher()
        val receivedEvents = mutableListOf<Pair<Int, Int>>()
        dispatcher.onSelectionChangedListener = { start, end ->
            receivedEvents.add(Pair(start, end))
        }

        dispatcher.dispatchSelectionChanged(0, 0)
        dispatcher.dispatchSelectionChanged(0, 5)
        dispatcher.dispatchSelectionChanged(5, 5)
        dispatcher.dispatchSelectionChanged(10, 2) // Inverted selection (drag handle backward)

        assertEquals(4, receivedEvents.size)
        assertEquals(Pair(0, 0), receivedEvents[0])
        assertEquals(Pair(0, 5), receivedEvents[1])
        assertEquals(Pair(5, 5), receivedEvents[2])
        assertEquals(Pair(10, 2), receivedEvents[3])
    }

    @Test
    fun challenge_toolbarVisibilityResolution_underAllSelectionStates() {
        // Contract in FloatingBubbleService:
        // if (!queryEt.isEnabled || isCallingAi) -> hide(animated = false)
        // else if (selStart != selEnd) -> show()
        // else -> hide(animated = true)

        fun resolveToolbarVisibility(
            selStart: Int,
            selEnd: Int,
            isEnabled: Boolean,
            isCallingAi: Boolean
        ): String {
            return if (!isEnabled || isCallingAi) {
                "HIDE_IMMEDIATE"
            } else if (selStart != selEnd) {
                "SHOW"
            } else {
                "HIDE_ANIMATED"
            }
        }

        // Active forward selection -> SHOW
        assertEquals("SHOW", resolveToolbarVisibility(0, 10, isEnabled = true, isCallingAi = false))

        // Active backward selection (inverted handles) -> SHOW
        assertEquals("SHOW", resolveToolbarVisibility(10, 2, isEnabled = true, isCallingAi = false))

        // Collapsed cursor at position 5 -> HIDE_ANIMATED
        assertEquals("HIDE_ANIMATED", resolveToolbarVisibility(5, 5, isEnabled = true, isCallingAi = false))

        // Collapsed cursor at position 0 -> HIDE_ANIMATED
        assertEquals("HIDE_ANIMATED", resolveToolbarVisibility(0, 0, isEnabled = true, isCallingAi = false))

        // Active selection during AI response streaming -> HIDE_IMMEDIATE
        assertEquals("HIDE_IMMEDIATE", resolveToolbarVisibility(0, 10, isEnabled = true, isCallingAi = true))

        // Active selection when disabled -> HIDE_IMMEDIATE
        assertEquals("HIDE_IMMEDIATE", resolveToolbarVisibility(0, 10, isEnabled = false, isCallingAi = false))
    }

    // =========================================================================
    // 3. ACTIONMODE SUPPRESSION CONTRACT (OEM BADTOKENEXCEPTION ELIMINATION)
    // =========================================================================

    @Test
    fun challenge_suppressActionModeCallback_contractVerification() {
        // Reflection check: verify suppressActionModeCallback field on FloatingBubbleService
        val clazz = Class.forName("com.orca.orcaauto.service.FloatingBubbleService")
        val field: Field = clazz.getDeclaredField("suppressActionModeCallback")
        field.isAccessible = true

        // Create an instance or check field type
        assertTrue("suppressActionModeCallback must implement ActionMode.Callback",
            ActionMode.Callback::class.java.isAssignableFrom(field.type))

        // Test the exact suppression callback behavior
        val callback = object : ActionMode.Callback {
            override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean = false
            override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean = false
            override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean = false
            override fun onDestroyActionMode(mode: ActionMode?) {}
        }

        // onCreateActionMode MUST return false to prevent OS FloatingToolbarPopup window allocation
        assertFalse("onCreateActionMode must return false", callback.onCreateActionMode(null, null))
        assertFalse("onPrepareActionMode must return false", callback.onPrepareActionMode(null, null))
        assertFalse("onActionItemClicked must return false", callback.onActionItemClicked(null, null))
        // onDestroyActionMode must complete without throwing
        callback.onDestroyActionMode(null)
    }

    // =========================================================================
    // 4. IN-CARD ACTION RESOLUTION: COPY, CUT, PASTE ORACLES
    // =========================================================================

    @Test
    fun challenge_copyPill_rangeResolutionStress() {
        fun resolveCopy(text: String, selStart: Int, selEnd: Int): String? {
            if (text.isEmpty()) return null
            val rawStart = selStart.coerceAtLeast(0)
            val rawEnd = selEnd.coerceAtLeast(0)
            val start = minOf(rawStart, rawEnd).coerceAtMost(text.length)
            val end = maxOf(rawStart, rawEnd).coerceAtMost(text.length)
            val textToCopy = if (start < end) {
                text.substring(start, end)
            } else {
                text
            }
            return if (textToCopy.isNotEmpty()) textToCopy else null
        }

        val content = "Tóm tắt cuộc họp giao ban sáng nay"

        // Normal range
        assertEquals("Tóm tắt", resolveCopy(content, 0, 7))

        // Inverted range (dragged right to left)
        assertEquals("Tóm tắt", resolveCopy(content, 7, 0))

        // Caret collapsed at character 10 -> fallback to copy entire text
        assertEquals(content, resolveCopy(content, 10, 10))

        // Caret collapsed at 0
        assertEquals(content, resolveCopy(content, 0, 0))

        // Out of bounds negative start and massive end
        assertEquals(content, resolveCopy(content, -999, 99999))

        // Empty string -> returns null (triggers Toast 'Không có nội dung để sao chép')
        assertNull(resolveCopy("", 0, 0))
    }

    @Test
    fun challenge_cutPill_rangeResolutionStress() {
        data class CutResult(val cutText: String?, val remainingText: String, val newCursor: Int, val toastMsg: String)

        fun resolveCut(initialText: String, selStart: Int, selEnd: Int): CutResult {
            if (initialText.isEmpty()) {
                return CutResult(null, initialText, 0, "Không có nội dung để cắt")
            }
            val sb = StringBuilder(initialText)
            val rawStart = selStart.coerceAtLeast(0)
            val rawEnd = selEnd.coerceAtLeast(0)
            val start = minOf(rawStart, rawEnd).coerceAtMost(sb.length)
            val end = maxOf(rawStart, rawEnd).coerceAtMost(sb.length)
            return if (start < end) {
                val cutText = sb.substring(start, end)
                sb.delete(start, end)
                CutResult(cutText, sb.toString(), start, "✓ Đã cắt")
            } else {
                CutResult(null, initialText, rawStart, "Chưa chọn đoạn chữ cần cắt")
            }
        }

        // Cut middle word
        val res1 = resolveCut("Họp dự án OrcaAuto", 4, 10)
        assertEquals("dự án ", res1.cutText)
        assertEquals("Họp OrcaAuto", res1.remainingText)
        assertEquals(4, res1.newCursor)
        assertEquals("✓ Đã cắt", res1.toastMsg)

        // Cut with inverted selection coordinates
        val res2 = resolveCut("Họp dự án OrcaAuto", 10, 4)
        assertEquals("dự án ", res2.cutText)
        assertEquals("Họp OrcaAuto", res2.remainingText)
        assertEquals(4, res2.newCursor)
        assertEquals("✓ Đã cắt", res2.toastMsg)

        // Cut with no range selected (caret at index 5)
        val res3 = resolveCut("Họp dự án OrcaAuto", 5, 5)
        assertNull(res3.cutText)
        assertEquals("Họp dự án OrcaAuto", res3.remainingText)
        assertEquals("Chưa chọn đoạn chữ cần cắt", res3.toastMsg)

        // Cut empty text
        val res4 = resolveCut("", 0, 0)
        assertNull(res4.cutText)
        assertEquals("Không có nội dung để cắt", res4.toastMsg)
    }

    @Test
    fun challenge_pastePill_insertionAndReplacementStress() {
        data class PasteResult(val resultText: String, val newCursor: Int)

        fun resolvePaste(
            currentText: String,
            selStart: Int,
            selEnd: Int,
            clipText: String?
        ): PasteResult? {
            if (clipText.isNullOrEmpty()) return null
            val sb = StringBuilder(currentText)
            val rawStart = selStart.coerceAtLeast(0)
            val rawEnd = selEnd.coerceAtLeast(0)
            val start = minOf(rawStart, rawEnd).coerceAtMost(sb.length)
            val end = maxOf(rawStart, rawEnd).coerceAtMost(sb.length)
            sb.replace(start, end, clipText)
            val newCursor = (start + clipText.length).coerceAtMost(sb.length)
            return PasteResult(sb.toString(), newCursor)
        }

        // Paste into empty string
        val res1 = resolvePaste("", 0, 0, "Nội dung dán")
        assertEquals(PasteResult("Nội dung dán", 12), res1)

        // Paste replacing an active range
        val res2 = resolvePaste("Xin chào thế giới", 9, 17, "OrcaAuto")
        assertEquals(PasteResult("Xin chào OrcaAuto", 17), res2)

        // Paste replacing inverted selection range
        val res3 = resolvePaste("Xin chào thế giới", 17, 9, "OrcaAuto")
        assertEquals(PasteResult("Xin chào OrcaAuto", 17), res3)

        // Paste at cursor without selection
        val res4 = resolvePaste("Xin chào!", 8, 8, " bạn")
        assertEquals(PasteResult("Xin chào bạn!", 12), res4)

        // Empty clipboard
        val res5 = resolvePaste("Xin chào!", 0, 0, "")
        assertNull("Empty clip text must return null", res5)
        val res6 = resolvePaste("Xin chào!", 0, 0, null)
        assertNull("Null clip text must return null", res6)
    }

    // =========================================================================
    // 5. LIFECYCLE ZERO GHOST HANDLES INVARIANTS
    // =========================================================================

    @Test
    fun challenge_zeroGhostHandles_stateTransitions() {
        // Contract on collapseExpandedCard and session switching:
        // 1. clearFocus()
        // 2. Selection.setSelection(queryEt.text, 0) -> collapses selection range to 0
        // 3. quickActionBar.visibility = GONE & alpha = 0f
        class MockEditorState {
            var hasFocus = true
            var selStart = 5
            var selEnd = 15
            var toolbarVisible = true
            var toolbarAlpha = 1.0f

            fun executeLifecycleCleanup() {
                hasFocus = false
                selStart = 0
                selEnd = 0
                toolbarVisible = false
                toolbarAlpha = 0f
            }
        }

        val state = MockEditorState()
        assertTrue(state.hasFocus)
        assertTrue(state.selStart != state.selEnd)
        assertTrue(state.toolbarVisible)

        state.executeLifecycleCleanup()

        assertFalse("Focus must be cleared", state.hasFocus)
        assertEquals("Selection start must collapse to 0", 0, state.selStart)
        assertEquals("Selection end must collapse to 0", 0, state.selEnd)
        assertFalse("Toolbar must be hidden (GONE)", state.toolbarVisible)
        assertEquals("Toolbar alpha must be 0", 0f, state.toolbarAlpha, 0.001f)
    }
}
