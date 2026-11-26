package com.orca.orcaauto.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FloatingBubbleImeLayoutHelperTest {

    @Test
    fun calculateCardLayout_whenImeClosed_keepsBaseCardCentered() {
        val spec = FloatingBubbleImeLayoutHelper.calculateCardLayout(
            rootWidth = 1080,
            rootHeight = 2400,
            rootTopOnScreen = 0,
            baseCardWidth = 1015,
            baseCardHeight = 1776,
            sysTopInset = 108,
            sysNavBottomInset = 72,
            insetsImeBottom = 0,
            insetsImeVisible = false,
            visibleFrameTopInRoot = 108,
            visibleFrameBottomInsetInRoot = 72,
            hasVisibleFrame = true,
            statusBarFallbackPx = 72,
            edgeMarginPx = 24,
            minImeFallbackJumpPx = 192,
            compactHideSessionsThresholdPx = 720,
            minCardHeightPx = 360
        )

        assertFalse(spec.isImeVisible)
        assertEquals(FloatingBubbleImeLayoutHelper.VerticalPlacement.CENTER, spec.placement)
        assertEquals(1776, spec.height)
        assertEquals(0, spec.topMargin)
        assertEquals(0, spec.bottomMargin)
        assertFalse(spec.hideSessionStripForCompactIme)
    }

    @Test
    fun calculateCardLayout_whenImeOpenViaWindowInsets_shrinksAndLiftsCardAboveKeyboard() {
        val imeHeight = 900
        val spec = FloatingBubbleImeLayoutHelper.calculateCardLayout(
            rootWidth = 1080,
            rootHeight = 2400,
            rootTopOnScreen = 0,
            baseCardWidth = 1015,
            baseCardHeight = 1776,
            sysTopInset = 108,
            sysNavBottomInset = 72,
            insetsImeBottom = imeHeight,
            insetsImeVisible = true,
            visibleFrameTopInRoot = 108,
            visibleFrameBottomInsetInRoot = imeHeight,
            hasVisibleFrame = true,
            statusBarFallbackPx = 72,
            edgeMarginPx = 24,
            minImeFallbackJumpPx = 192,
            compactHideSessionsThresholdPx = 720,
            minCardHeightPx = 360
        )

        assertTrue(spec.isImeVisible)
        assertEquals(FloatingBubbleImeLayoutHelper.VerticalPlacement.ABOVE_IME, spec.placement)
        assertEquals(132, spec.topMargin)
        assertEquals(924, spec.bottomMargin)
        // Bottom edge = 2400 - 924 = 1476, strictly above keyboard top (1500)
        assertEquals(1344, spec.height)
        val cardTop = 2400 - spec.bottomMargin - spec.height
        assertTrue("Card top ($cardTop) must stay below status bar + margin (${spec.topMargin})", cardTop >= spec.topMargin)
        assertFalse(spec.hideSessionStripForCompactIme)
    }

    @Test
    fun calculateCardLayout_whenOemSkipsTypeImeButShrinksVisibleFrame_usesVisibleFrameFallback() {
        val oemKeyboardHeight = 960
        val spec = FloatingBubbleImeLayoutHelper.calculateCardLayout(
            rootWidth = 1080,
            rootHeight = 2400,
            rootTopOnScreen = 0,
            baseCardWidth = 1015,
            baseCardHeight = 1776,
            sysTopInset = 108,
            sysNavBottomInset = 144, // 3-button nav bar
            insetsImeBottom = 0, // Buggy OEM overlay omits Type.ime().bottom
            insetsImeVisible = false,
            visibleFrameTopInRoot = 108,
            visibleFrameBottomInsetInRoot = oemKeyboardHeight,
            hasVisibleFrame = true,
            statusBarFallbackPx = 72,
            edgeMarginPx = 24,
            minImeFallbackJumpPx = 192,
            compactHideSessionsThresholdPx = 720,
            minCardHeightPx = 360
        )

        assertTrue(spec.isImeVisible)
        assertEquals(FloatingBubbleImeLayoutHelper.VerticalPlacement.ABOVE_IME, spec.placement)
        assertEquals(oemKeyboardHeight + 24, spec.bottomMargin)
        assertEquals(1284, spec.height)
    }

    @Test
    fun calculateCardLayout_inTightLandscapeWithImeOpen_hidesSessionStripSoInputRemainsVisible() {
        val spec = FloatingBubbleImeLayoutHelper.calculateCardLayout(
            rootWidth = 2400,
            rootHeight = 1080,
            rootTopOnScreen = 0,
            baseCardWidth = 1500,
            baseCardHeight = 799,
            sysTopInset = 72,
            sysNavBottomInset = 0,
            insetsImeBottom = 560,
            insetsImeVisible = true,
            visibleFrameTopInRoot = 72,
            visibleFrameBottomInsetInRoot = 560,
            hasVisibleFrame = true,
            statusBarFallbackPx = 72,
            edgeMarginPx = 24,
            minImeFallbackJumpPx = 192,
            compactHideSessionsThresholdPx = 720,
            minCardHeightPx = 360
        )

        assertTrue(spec.isImeVisible)
        assertTrue(spec.hideSessionStripForCompactIme)
        assertEquals(400, spec.height)
        assertEquals(584, spec.bottomMargin)
    }

    @Test
    fun calculateCardLayout_whenLegacyAdjustResizeAlreadyShrankRootWindow_clampsHeightAndPreservesTopSafe() {
        // Legacy API 26-29: WMS moved window top below status bar (rootTopOnScreen = 96) and shrank rootHeight above IME
        val spec = FloatingBubbleImeLayoutHelper.calculateCardLayout(
            rootWidth = 1080,
            rootHeight = 1404,
            rootTopOnScreen = 96,
            baseCardWidth = 1015,
            baseCardHeight = 1776,
            sysTopInset = 0,
            sysNavBottomInset = 0,
            insetsImeBottom = 0,
            insetsImeVisible = false,
            visibleFrameTopInRoot = 0,
            visibleFrameBottomInsetInRoot = 0,
            hasVisibleFrame = true,
            statusBarFallbackPx = 96,
            edgeMarginPx = 24,
            minImeFallbackJumpPx = 192,
            compactHideSessionsThresholdPx = 720,
            minCardHeightPx = 360
        )

        assertEquals(24, spec.topMargin)
        assertEquals(24, spec.bottomMargin)
        assertEquals(1356, spec.height)
    }

    @Test
    fun calculateCardLayout_whenApi26To29BleedsImeIntoNavBottomAndVisibleFrame_detectsImeAndLiftsCard() {
        val imeHeight = 900
        val spec = FloatingBubbleImeLayoutHelper.calculateCardLayout(
            rootWidth = 1080,
            rootHeight = 2400,
            rootTopOnScreen = 0,
            baseCardWidth = 1015,
            baseCardHeight = 1776,
            sysTopInset = 108,
            sysNavBottomInset = imeHeight, // Legacy API 26-29 WindowInsetsCompat puts IME inside nav/systemBottom
            insetsImeBottom = 0,
            insetsImeVisible = false,
            visibleFrameTopInRoot = 108,
            visibleFrameBottomInsetInRoot = imeHeight,
            hasVisibleFrame = true,
            statusBarFallbackPx = 72,
            edgeMarginPx = 24,
            minImeFallbackJumpPx = 192,
            compactHideSessionsThresholdPx = 720,
            minCardHeightPx = 360
        )

        assertTrue(spec.isImeVisible)
        assertEquals(FloatingBubbleImeLayoutHelper.VerticalPlacement.ABOVE_IME, spec.placement)
        assertEquals(924, spec.bottomMargin)
        assertEquals(1344, spec.height)
    }
}
