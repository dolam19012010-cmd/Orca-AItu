package com.orca.orcaauto.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Comprehensive unit tests for [FloatingBubbleVoiceHelper].
 * Verifies all decision logic, boundary conditions, slide-to-cancel math,
 * text aggregation, and thinking mode chips auto-collapse rules.
 */
class FloatingBubbleVoiceHelperTest {

    // =========================================================================
    // 0. TIMING & THRESHOLD CONSTANTS
    // =========================================================================

    @Test
    fun testHoldThresholdConstant_isOneSecond() {
        assertEquals(
            "Hold threshold for voice recording must be exactly 1.0 second (1000ms)",
            1000L,
            FloatingBubbleVoiceHelper.HOLD_THRESHOLD_MS
        )
        assertEquals(50f, FloatingBubbleVoiceHelper.CANCEL_THRESHOLD_DP, 0.001f)
    }

    // =========================================================================
    // 1. TOUCH ACTION RESOLUTION
    // =========================================================================

    @Test
    fun testResolveTouchAction_whenHoldNotTriggered_withoutSlop_alwaysResolvesToTap() {
        assertEquals(
            FloatingBubbleVoiceHelper.TouchAction.TAP,
            FloatingBubbleVoiceHelper.resolveTouchAction(isHoldTriggered = false, isSlideCancelled = false)
        )
        // Even if slide flag was somehow set, if hold never fired and slop not exceeded, it remains a TAP
        assertEquals(
            FloatingBubbleVoiceHelper.TouchAction.TAP,
            FloatingBubbleVoiceHelper.resolveTouchAction(isHoldTriggered = false, isSlideCancelled = true)
        )
        // Explicit false for isSlopExceeded
        assertEquals(
            FloatingBubbleVoiceHelper.TouchAction.TAP,
            FloatingBubbleVoiceHelper.resolveTouchAction(isHoldTriggered = false, isSlideCancelled = false, isSlopExceeded = false)
        )
    }

    @Test
    fun testResolveTouchAction_whenHoldNotTriggered_andSlopExceeded_resolvesToIgnore() {
        // Dragging away before 1000ms aborts touch gesture -> IGNORE (do not send AI query)
        assertEquals(
            FloatingBubbleVoiceHelper.TouchAction.IGNORE,
            FloatingBubbleVoiceHelper.resolveTouchAction(
                isHoldTriggered = false,
                isSlideCancelled = false,
                isSlopExceeded = true
            )
        )
        assertEquals(
            FloatingBubbleVoiceHelper.TouchAction.IGNORE,
            FloatingBubbleVoiceHelper.resolveTouchAction(
                isHoldTriggered = false,
                isSlideCancelled = true,
                isSlopExceeded = true
            )
        )
    }

    @Test
    fun testResolveTouchAction_whenHoldTriggeredAndNotCancelled_resolvesToCommit() {
        assertEquals(
            FloatingBubbleVoiceHelper.TouchAction.HOLD_RELEASE_COMMIT,
            FloatingBubbleVoiceHelper.resolveTouchAction(isHoldTriggered = true, isSlideCancelled = false)
        )
    }

    @Test
    fun testResolveTouchAction_whenHoldTriggeredAndCancelled_resolvesToCancel() {
        assertEquals(
            FloatingBubbleVoiceHelper.TouchAction.HOLD_RELEASE_CANCEL,
            FloatingBubbleVoiceHelper.resolveTouchAction(isHoldTriggered = true, isSlideCancelled = true)
        )
    }

    // =========================================================================
    // 2. SLIDE TO CANCEL PROGRESS MATH
    // =========================================================================

    @Test
    fun testCalculateCancelProgress_exactCalculationsAndClamping() {
        val threshold = 100f

        // Stationary
        assertEquals(0f, FloatingBubbleVoiceHelper.calculateCancelProgress(200f, 200f, threshold), 0.0001f)

        // Dragging right (negative progress clamped to 0)
        assertEquals(0f, FloatingBubbleVoiceHelper.calculateCancelProgress(200f, 250f, threshold), 0.0001f)

        // Halfway drag left (50px out of 100px)
        assertEquals(0.5f, FloatingBubbleVoiceHelper.calculateCancelProgress(200f, 150f, threshold), 0.0001f)

        // Exactly at threshold (100px out of 100px)
        assertEquals(1.0f, FloatingBubbleVoiceHelper.calculateCancelProgress(200f, 100f, threshold), 0.0001f)

        // Dragged beyond threshold (150px out of 100px, clamped to 1.0f)
        assertEquals(1.0f, FloatingBubbleVoiceHelper.calculateCancelProgress(200f, 50f, threshold), 0.0001f)
    }

    @Test
    fun testCalculateCancelProgress_degenerateThresholdReturnsZero() {
        assertEquals(0f, FloatingBubbleVoiceHelper.calculateCancelProgress(200f, 100f, 0f), 0.0001f)
        assertEquals(0f, FloatingBubbleVoiceHelper.calculateCancelProgress(200f, 100f, -50f), 0.0001f)
    }

    // =========================================================================
    // 3. CANCEL THRESHOLD BOUNDARIES
    // =========================================================================

    @Test
    fun testIsCancelThresholdExceeded_boundaryChecks() {
        val threshold = 50f
        val downX = 100f

        // 49.9f: Just below threshold -> false
        assertFalse(FloatingBubbleVoiceHelper.isCancelThresholdExceeded(downX, downX - 49.9f, threshold))

        // 50.0f: Exactly on threshold -> true
        assertTrue(FloatingBubbleVoiceHelper.isCancelThresholdExceeded(downX, downX - 50.0f, threshold))

        // 50.1f: Beyond threshold -> true
        assertTrue(FloatingBubbleVoiceHelper.isCancelThresholdExceeded(downX, downX - 50.1f, threshold))

        // Dragging right -> false
        assertFalse(FloatingBubbleVoiceHelper.isCancelThresholdExceeded(downX, downX + 20f, threshold))

        // Degenerate threshold -> false
        assertFalse(FloatingBubbleVoiceHelper.isCancelThresholdExceeded(downX, downX - 100f, 0f))
        assertFalse(FloatingBubbleVoiceHelper.isCancelThresholdExceeded(downX, downX - 100f, -10f))
    }

    // =========================================================================
    // 4. THRESHOLD CROSSING & REVERSIBLE TRANSITIONS
    // =========================================================================

    @Test
    fun testDidCrossCancelThreshold_detectsLeadingEdgeOnly() {
        // Crossing from not exceeded to exceeded -> true
        assertTrue(FloatingBubbleVoiceHelper.didCrossCancelThreshold(wasExceeded = false, isNowExceeded = true))

        // Sustained exceeded state -> false
        assertFalse(FloatingBubbleVoiceHelper.didCrossCancelThreshold(wasExceeded = true, isNowExceeded = true))

        // Dragging back -> false
        assertFalse(FloatingBubbleVoiceHelper.didCrossCancelThreshold(wasExceeded = true, isNowExceeded = false))

        // Sustained normal state -> false
        assertFalse(FloatingBubbleVoiceHelper.didCrossCancelThreshold(wasExceeded = false, isNowExceeded = false))
    }

    @Test
    fun testDidRevertFromCancel_detectsTrailingEdgeOnly() {
        // Dragging back from exceeded to normal -> true
        assertTrue(FloatingBubbleVoiceHelper.didRevertFromCancel(wasExceeded = true, isNowExceeded = false))

        // Sustained normal state -> false
        assertFalse(FloatingBubbleVoiceHelper.didRevertFromCancel(wasExceeded = false, isNowExceeded = false))

        // Crossing into cancel -> false
        assertFalse(FloatingBubbleVoiceHelper.didRevertFromCancel(wasExceeded = false, isNowExceeded = true))

        // Sustained exceeded state -> false
        assertFalse(FloatingBubbleVoiceHelper.didRevertFromCancel(wasExceeded = true, isNowExceeded = true))
    }

    // =========================================================================
    // 5. TRANSCRIBED TEXT COMBINATION
    // =========================================================================

    @Test
    fun testCombineTranscribedText_variousCombinations() {
        // Empty initial
        assertEquals("Xin chào", FloatingBubbleVoiceHelper.combineTranscribedText("", "Xin chào"))
        assertEquals("Xin chào", FloatingBubbleVoiceHelper.combineTranscribedText("   ", "Xin chào"))

        // Empty delta returns initial unchanged
        assertEquals("Đã có", FloatingBubbleVoiceHelper.combineTranscribedText("Đã có", ""))
        assertEquals("Đã có", FloatingBubbleVoiceHelper.combineTranscribedText("Đã có", "   "))

        // Both non-blank: joined with clean single space
        assertEquals(
            "Hôm nay trời đẹp tôi đi dạo",
            FloatingBubbleVoiceHelper.combineTranscribedText("Hôm nay trời đẹp", "tôi đi dạo")
        )

        // Trailing and leading whitespaces trimmed and joined cleanly
        assertEquals(
            "Câu lệnh kiểm tra",
            FloatingBubbleVoiceHelper.combineTranscribedText("Câu lệnh   ", "   kiểm tra   ")
        )
    }

    // =========================================================================
    // 6. THINKING CHIPS AUTO-COLLAPSE CONDITIONS
    // =========================================================================

    @Test
    fun testShouldShowThinkingChips_focusAndTextRules() {
        // Has focus: always true regardless of text
        assertTrue(FloatingBubbleVoiceHelper.shouldShowThinkingChips(hasFocus = true, text = null))
        assertTrue(FloatingBubbleVoiceHelper.shouldShowThinkingChips(hasFocus = true, text = ""))
        assertTrue(FloatingBubbleVoiceHelper.shouldShowThinkingChips(hasFocus = true, text = "   "))
        assertTrue(FloatingBubbleVoiceHelper.shouldShowThinkingChips(hasFocus = true, text = "Tóm tắt email"))

        // No focus, but has populated text: true
        assertTrue(FloatingBubbleVoiceHelper.shouldShowThinkingChips(hasFocus = false, text = "Tóm tắt email"))
        assertTrue(FloatingBubbleVoiceHelper.shouldShowThinkingChips(hasFocus = false, text = "  Nội dung  "))

        // No focus and empty/blank: false (auto-collapsed)
        assertFalse(FloatingBubbleVoiceHelper.shouldShowThinkingChips(hasFocus = false, text = null))
        assertFalse(FloatingBubbleVoiceHelper.shouldShowThinkingChips(hasFocus = false, text = ""))
        assertFalse(FloatingBubbleVoiceHelper.shouldShowThinkingChips(hasFocus = false, text = "   "))
        assertFalse(FloatingBubbleVoiceHelper.shouldShowThinkingChips(hasFocus = false, text = "\n\t"))
    }
}
