package com.orca.orcaauto.service

/**
 * Pure range and decision helper for overlay text selection, smart context long-press dispatch,
 * clipboard operations (copy, cut, paste), and zero-ghost-handle lifecycle cleanup.
 *
 * Fully decoupled from Android UI view hierarchies to enable empirical JVM testing.
 */
internal object FloatingBubbleTextSelectionHelper {

    /**
     * Determines whether a long-click event on the query editor should be consumed for instant paste.
     *
     * Rule:
     * - Returns true if text is null or blank (instant paste from clipboard).
     * - Returns false if text has populated content, letting AOSP Editor handle word selection
     *   and display drag handles (SelectionModifierCursorController).
     */
    fun shouldConsumeLongClickForInstantPaste(text: CharSequence?): Boolean {
        return text.isNullOrBlank()
    }

    /**
     * Resolves the text to copy from the editor given selection coordinates.
     *
     * If a selection range is active (selStart != selEnd), extracts the clamped substring.
     * If the selection is collapsed (selStart == selEnd), falls back to copying all text.
     * Returns an empty string if text is null or empty.
     */
    fun resolveCopyText(text: CharSequence?, selStart: Int, selEnd: Int): String {
        if (text.isNullOrEmpty()) return ""
        val len = text.length
        val rawStart = selStart.coerceAtLeast(0)
        val rawEnd = selEnd.coerceAtLeast(0)
        val start = minOf(rawStart, rawEnd).coerceAtMost(len)
        val end = maxOf(rawStart, rawEnd).coerceAtMost(len)
        return if (start < end) {
            text.substring(start, end)
        } else {
            text.toString()
        }
    }

    /**
     * Resolves the start and end indices of the text to cut.
     *
     * Returns Pair(start, end) if an active selection range exists (start < end).
     * Returns null if unselected, collapsed caret, or text is null/empty.
     */
    fun resolveCutRange(text: CharSequence?, selStart: Int, selEnd: Int): Pair<Int, Int>? {
        if (text.isNullOrEmpty()) return null
        val len = text.length
        val rawStart = selStart.coerceAtLeast(0)
        val rawEnd = selEnd.coerceAtLeast(0)
        val start = minOf(rawStart, rawEnd).coerceAtMost(len)
        val end = maxOf(rawStart, rawEnd).coerceAtMost(len)
        return if (start < end) {
            Pair(start, end)
        } else {
            null
        }
    }

    /**
     * Resolves safe replacement range for clipboard paste operations, clamping inverted selections
     * and boundary coordinates to the valid text length.
     */
    fun resolvePasteRange(textLength: Int, selStart: Int, selEnd: Int): Pair<Int, Int> {
        val safeLen = textLength.coerceAtLeast(0)
        val rawStart = selStart.coerceAtLeast(0)
        val rawEnd = selEnd.coerceAtLeast(0)
        val start = minOf(rawStart, rawEnd).coerceAtMost(safeLen)
        val end = maxOf(rawStart, rawEnd).coerceAtMost(safeLen)
        return Pair(start, end)
    }

    /**
     * Resolves the new cursor position after text insertion or replacement.
     */
    fun resolveNewCursorPosition(start: Int, insertedLength: Int, totalLength: Int): Int {
        val safeTotal = totalLength.coerceAtLeast(0)
        val safeStart = start.coerceIn(0, safeTotal)
        val safeInserted = insertedLength.coerceAtLeast(0)
        return (safeStart + safeInserted).coerceAtMost(safeTotal)
    }

    /**
     * Data contract for lifecycle cleanup state transitions to eliminate ghost handles
     * across card collapsing, session switching, and service destruction.
     */
    data class LifecycleCleanupPlan(
        val clearFocus: Boolean = true,
        val targetSelection: Int? = null,
        val hideToolbar: Boolean = true
    )

    /**
     * Plans the lifecycle cleanup actions needed to guarantee zero ghost handles.
     */
    fun planLifecycleCleanup(hasText: Boolean): LifecycleCleanupPlan {
        return LifecycleCleanupPlan(
            clearFocus = true,
            targetSelection = if (hasText) 0 else null,
            hideToolbar = true
        )
    }
}
