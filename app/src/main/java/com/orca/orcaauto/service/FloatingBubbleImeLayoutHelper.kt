package com.orca.orcaauto.service

/**
 * Pure layout geometry calculator for positioning and resizing the expanded floating card
 * cleanly above the soft keyboard (IME) across Android API 26–35 and OEM overlay behaviors.
 *
 * Fully decoupled from Android View instances for deterministic JVM testing.
 */
internal object FloatingBubbleImeLayoutHelper {

    enum class VerticalPlacement {
        CENTER,
        ABOVE_IME
    }

    data class CardLayoutSpec(
        val width: Int,
        val height: Int,
        val placement: VerticalPlacement,
        val topMargin: Int,
        val bottomMargin: Int,
        val isImeVisible: Boolean,
        val hideSessionStripForCompactIme: Boolean
    )

    fun calculateCardLayout(
        rootWidth: Int,
        rootHeight: Int,
        rootTopOnScreen: Int,
        baseCardWidth: Int,
        baseCardHeight: Int,
        sysTopInset: Int,
        sysNavBottomInset: Int,
        insetsImeBottom: Int,
        insetsImeVisible: Boolean,
        visibleFrameTopInRoot: Int,
        visibleFrameBottomInsetInRoot: Int,
        hasVisibleFrame: Boolean,
        statusBarFallbackPx: Int,
        edgeMarginPx: Int,
        minImeFallbackJumpPx: Int,
        compactHideSessionsThresholdPx: Int,
        minCardHeightPx: Int
    ): CardLayoutSpec {
        val safeRootW = rootWidth.coerceAtLeast(baseCardWidth)
        val clampedWidth = baseCardWidth.coerceAtMost((safeRootW - edgeMarginPx * 2).coerceAtLeast(1))

        if (rootHeight <= 0) {
            return CardLayoutSpec(
                width = clampedWidth,
                height = baseCardHeight,
                placement = VerticalPlacement.CENTER,
                topMargin = 0,
                bottomMargin = 0,
                isImeVisible = false,
                hideSessionStripForCompactIme = false
            )
        }

        val visTop = if (hasVisibleFrame) visibleFrameTopInRoot.coerceAtLeast(0) else 0
        val topFallback = if (rootTopOnScreen <= 0 && sysTopInset <= 0 && visTop <= 0) {
            statusBarFallbackPx.coerceAtLeast(0)
        } else {
            0
        }
        val effectiveTopInset = maxOf(sysTopInset.coerceAtLeast(0), visTop, topFallback)
        val topSafeMargin = effectiveTopInset + edgeMarginPx

        val rawImeBottom = insetsImeBottom.coerceAtLeast(0)
        val safeNavBottom = sysNavBottomInset.coerceAtLeast(0)
        val visBottom = if (hasVisibleFrame) visibleFrameBottomInsetInRoot.coerceAtLeast(0) else 0

        // OEM / API 26-29 fallback: legacy WindowInsetsCompat may omit Type.ime() or report IME height inside nav/visibleFrame
        val boundedNavBottom = safeNavBottom.coerceAtMost((rootHeight * 0.12f).toInt())
        val fallbackThreshold = maxOf(boundedNavBottom + minImeFallbackJumpPx, (rootHeight * 0.16f).toInt())
        val fallbackImeBottom = if (rawImeBottom == 0 && visBottom > fallbackThreshold) visBottom else 0
        val effectiveImeBottom = maxOf(rawImeBottom, fallbackImeBottom)
        val isImeVisible = insetsImeVisible || effectiveImeBottom > 0

        if (!isImeVisible) {
            val closedBottomMargin = maxOf(safeNavBottom, if (visBottom <= fallbackThreshold) visBottom else 0) + edgeMarginPx
            val maxAvailableClosed = (rootHeight - topSafeMargin - closedBottomMargin).coerceAtLeast(minCardHeightPx)
            return if (baseCardHeight <= maxAvailableClosed) {
                CardLayoutSpec(
                    width = clampedWidth,
                    height = baseCardHeight,
                    placement = VerticalPlacement.CENTER,
                    topMargin = 0,
                    bottomMargin = 0,
                    isImeVisible = false,
                    hideSessionStripForCompactIme = false
                )
            } else {
                CardLayoutSpec(
                    width = clampedWidth,
                    height = maxAvailableClosed,
                    placement = VerticalPlacement.CENTER,
                    topMargin = topSafeMargin,
                    bottomMargin = closedBottomMargin,
                    isImeVisible = false,
                    hideSessionStripForCompactIme = false
                )
            }
        }

        val bottomObstacle = maxOf(safeNavBottom, effectiveImeBottom, if (rawImeBottom == 0) visBottom else 0)
        val bottomSafeMargin = bottomObstacle + edgeMarginPx
        val availableHeight = (rootHeight - topSafeMargin - bottomSafeMargin).coerceAtLeast(minCardHeightPx)
        val targetHeight = baseCardHeight.coerceAtMost(availableHeight)
        val hideSessions = availableHeight < compactHideSessionsThresholdPx

        return CardLayoutSpec(
            width = clampedWidth,
            height = targetHeight,
            placement = VerticalPlacement.ABOVE_IME,
            topMargin = topSafeMargin,
            bottomMargin = bottomSafeMargin,
            isImeVisible = true,
            hideSessionStripForCompactIme = hideSessions
        )
    }
}
