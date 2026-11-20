package com.orca.orcaauto.service

/**
 * Pure decision and math helper for voice-to-text dictation, touch timing,
 * slide-to-cancel progress and thresholds, speech text aggregation,
 * and thinking mode chips auto-collapse conditions.
 *
 * Fully decoupled from Android UI view hierarchies to enable empirical JVM testing.
 */
internal object FloatingBubbleVoiceHelper {
    const val HOLD_THRESHOLD_MS = 1000L
    const val CANCEL_THRESHOLD_DP = 50f
    const val OUTSIDE_HOLD_ABORT_THRESHOLD_MS = 450L

    enum class TouchAction {
        TAP,
        HOLD_START,
        HOLD_RELEASE_COMMIT,
        HOLD_RELEASE_CANCEL,
        IGNORE
    }

    /**
     * Resolves the touch action on button release or touch completion.
     */
    fun resolveTouchAction(
        isHoldTriggered: Boolean,
        isSlideCancelled: Boolean,
        isSlopExceeded: Boolean = false
    ): TouchAction {
        return when {
            !isHoldTriggered && isSlopExceeded -> TouchAction.IGNORE
            !isHoldTriggered -> TouchAction.TAP
            isSlideCancelled -> TouchAction.HOLD_RELEASE_CANCEL
            else -> TouchAction.HOLD_RELEASE_COMMIT
        }
    }

    /**
     * Determines whether releasing an outside touch should collapse the card.
     * Returns true ONLY if:
     * 1. Touch started outside the card bounds.
     * 2. Release occurred outside the card bounds.
     * 3. Touch duration was less than [OUTSIDE_HOLD_ABORT_THRESHOLD_MS] (not held to abort).
     * 4. Hold-abort flag was not triggered.
     */
    fun shouldCollapseOnOutsideRelease(
        downStartedOutside: Boolean,
        isReleaseOutside: Boolean,
        pressDurationMs: Long,
        isHoldAborted: Boolean
    ): Boolean {
        if (!downStartedOutside || !isReleaseOutside) return false
        if (isHoldAborted) return false
        if (pressDurationMs >= OUTSIDE_HOLD_ABORT_THRESHOLD_MS) return false
        return true
    }

    /**
     * Calculates the slide-to-cancel progress ratio in [0f, 1f].
     * (downX - currentX) represents dragging left.
     */
    fun calculateCancelProgress(downX: Float, currentX: Float, thresholdPx: Float): Float {
        if (thresholdPx <= 0f) return 0f
        val dragLeft = downX - currentX
        if (dragLeft <= 0f) return 0f
        return (dragLeft / thresholdPx).coerceIn(0f, 1f)
    }

    /**
     * True if drag left is greater than or equal to thresholdPx.
     */
    fun isCancelThresholdExceeded(downX: Float, currentX: Float, thresholdPx: Float): Boolean {
        if (thresholdPx <= 0f) return false
        return (downX - currentX) >= thresholdPx
    }

    /**
     * Detects transition when crossing the cancellation threshold (for haptic tick and bubble pop).
     */
    fun didCrossCancelThreshold(wasExceeded: Boolean, isNowExceeded: Boolean): Boolean {
        return !wasExceeded && isNowExceeded
    }

    /**
     * Detects transition when dragging back below cancellation threshold (reverting bubble pop and cancel warning).
     */
    fun didRevertFromCancel(wasExceeded: Boolean, isNowExceeded: Boolean): Boolean {
        return wasExceeded && !isNowExceeded
    }

    /**
     * Concatenates speech delta onto initial text cleanly with a single space separator if both are non-blank.
     */
    fun combineTranscribedText(initialText: String, speechDelta: String): String {
        val trimmedDelta = speechDelta.trim()
        if (trimmedDelta.isEmpty()) return initialText
        return if (initialText.isBlank()) trimmedDelta else "${initialText.trim()} $trimmedDelta"
    }

    /**
     * Thinking mode chips should be visible ONLY if input field has focus OR non-blank text.
     */
    fun shouldShowThinkingChips(hasFocus: Boolean, text: CharSequence?): Boolean {
        return hasFocus || !text.isNullOrBlank()
    }
}
