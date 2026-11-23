package com.orca.orcaauto.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShakeDetectorTest {

    @Test
    fun testMagnitudeSquaredThreshold_belowOrEqual144IsIgnored() {
        var callbackTriggered = false
        val detector = ShakeDetector { callbackTriggered = true }

        val t0 = 1_000_000_000L

        // magSq = 12^2 = 144.0f <= 144.0f -> ignored
        assertFalse(detector.processAcceleration(12.0f, 0f, 0f, t0))
        assertFalse(detector.processAcceleration(-12.0f, 0f, 0f, t0 + 100_000_000L))
        assertFalse(detector.processAcceleration(12.0f, 0f, 0f, t0 + 200_000_000L))
        assertFalse(callbackTriggered)

        // Weak acceleration: magSq = 8^2 + 8^2 = 128.0f <= 144.0f -> ignored
        assertFalse(detector.processAcceleration(8.0f, 8.0f, 0f, t0 + 300_000_000L))
        assertFalse(detector.processAcceleration(-8.0f, -8.0f, 0f, t0 + 400_000_000L))
        assertFalse(detector.processAcceleration(8.0f, 8.0f, 0f, t0 + 500_000_000L))
        assertFalse(callbackTriggered)
    }

    @Test
    fun testMagnitudeSquaredThreshold_above144TriggersCadence() {
        var callbackTriggered = false
        val detector = ShakeDetector { callbackTriggered = true }

        val t0 = 1_000_000_000L

        // magSq = 12.01^2 = 144.24f > 144.0f -> triggers cadence
        assertFalse(detector.processAcceleration(12.01f, 0f, 0f, t0))
        assertFalse(detector.processAcceleration(-12.01f, 0f, 0f, t0 + 150_000_000L))
        val triggered = detector.processAcceleration(12.01f, 0f, 0f, t0 + 300_000_000L)

        assertTrue("Should trigger shake when magSq > 144.0f on all 3 alternating reversals", triggered)
        assertTrue("Callback should be invoked", callbackTriggered)
    }

    @Test
    fun testThreeAlternatingPulsesWithin750ms_triggersShake() {
        var shakeCount = 0
        val detector = ShakeDetector { shakeCount++ }

        val baseTime = 10_000_000_000L

        // Pulse 1: Positive peak (+X)
        val p1 = detector.processAcceleration(14.0f, 0f, 0f, baseTime)
        assertFalse("Pulse 1 (reversal 1) should not trigger shake yet", p1)
        assertEquals(0, shakeCount)

        // Pulse 2: Negative peak (-X) after 150ms
        val p2 = detector.processAcceleration(-14.0f, 0f, 0f, baseTime + 150_000_000L)
        assertFalse("Pulse 2 (reversal 2) should not trigger shake yet", p2)
        assertEquals(0, shakeCount)

        // Pulse 3: Positive peak (+X) after another 150ms (total elapsed 300ms <= 750ms)
        val p3 = detector.processAcceleration(14.0f, 0f, 0f, baseTime + 300_000_000L)
        assertTrue("Pulse 3 (reversal 3) should trigger shake", p3)
        assertEquals(1, shakeCount)
    }

    @Test
    fun testNonAlternatingPulses_doNotTriggerShake() {
        var shakeCount = 0
        val detector = ShakeDetector { shakeCount++ }

        val baseTime = 10_000_000_000L

        // Same direction (+X) repeated 3 times (e.g. running or moving phone in 1 direction)
        detector.processAcceleration(14.0f, 0f, 0f, baseTime)
        detector.processAcceleration(14.0f, 0f, 0f, baseTime + 150_000_000L)
        val triggered = detector.processAcceleration(14.0f, 0f, 0f, baseTime + 300_000_000L)

        assertFalse("Same direction acceleration pulses must not trigger shake", triggered)
        assertEquals(0, shakeCount)
    }

    @Test
    fun testCadenceInterval_tooFastBelow70ms_isDiscarded() {
        var shakeCount = 0
        val detector = ShakeDetector { shakeCount++ }

        val baseTime = 10_000_000_000L

        // Pulse 1: Positive peak (+X)
        detector.processAcceleration(15.0f, 0f, 0f, baseTime)

        // Pulse 2: Negative peak (-X) arriving only 40ms later (< 70ms mechanical noise filter)
        val p2 = detector.processAcceleration(-15.0f, 0f, 0f, baseTime + 40_000_000L)
        assertFalse("High frequency vibration (< 70ms) should be discarded", p2)

        // Pulse 3: Positive peak arriving 120ms from Pulse 1 (80ms from Pulse 2, but Pulse 2 wasn't accepted as reversal)
        // Since last accepted sign was +X and current is +X, it's not a reversal
        val p3 = detector.processAcceleration(15.0f, 0f, 0f, baseTime + 120_000_000L)
        assertFalse(p3)
        assertEquals(0, shakeCount)
    }

    @Test
    fun testCadenceInterval_tooSlowAbove420ms_resetsCadence() {
        var shakeCount = 0
        val detector = ShakeDetector { shakeCount++ }

        val baseTime = 10_000_000_000L

        // Pulse 1: Positive peak (+X)
        detector.processAcceleration(15.0f, 0f, 0f, baseTime)

        // Pulse 2: Arriving 500ms later (> 420ms MAX_INTERVAL_NS) -> resets cadence to count 1
        detector.processAcceleration(-15.0f, 0f, 0f, baseTime + 500_000_000L)

        // Pulse 3: Arriving 500ms later (> 420ms) -> resets cadence to count 1 again
        val p3 = detector.processAcceleration(15.0f, 0f, 0f, baseTime + 1_000_000_000L)

        assertFalse("Slow intervals (> 420ms) must reset cadence and never trigger shake", p3)
        assertEquals(0, shakeCount)
    }

    @Test
    fun testCooldown_preventsSubsequentTriggersWithin1_2Seconds() {
        var shakeCount = 0
        val detector = ShakeDetector { shakeCount++ }

        val baseTime = 10_000_000_000L

        // 1. Initial valid shake
        detector.processAcceleration(15.0f, 0f, 0f, baseTime)
        detector.processAcceleration(-15.0f, 0f, 0f, baseTime + 100_000_000L)
        val initialTrigger = detector.processAcceleration(15.0f, 0f, 0f, baseTime + 200_000_000L)
        assertTrue("Initial shake gesture should trigger", initialTrigger)
        assertEquals(1, shakeCount)

        // 2. Attempt immediately inside cooldown (e.g., 500ms after last shake at 200ms -> t = baseTime + 700ms, dt = 500ms < 1.2s)
        val cdPulse1 = detector.processAcceleration(15.0f, 0f, 0f, baseTime + 700_000_000L)
        val cdPulse2 = detector.processAcceleration(-15.0f, 0f, 0f, baseTime + 800_000_000L)
        val cdPulse3 = detector.processAcceleration(15.0f, 0f, 0f, baseTime + 900_000_000L)
        assertFalse(cdPulse1)
        assertFalse(cdPulse2)
        assertFalse(cdPulse3)
        assertEquals("No second callback during cooldown", 1, shakeCount)

        // 3. Attempt at t = baseTime + 1_350_000_000L (dt = 1.15s < 1.2s) -> still in cooldown
        val edgeCooldown = detector.processAcceleration(15.0f, 0f, 0f, baseTime + 1_350_000_000L)
        assertFalse("At 1.15s (< 1.2s) cooldown is still active", edgeCooldown)

        // 4. Attempt after cooldown expires (1.3s after last shake at 200ms -> t = baseTime + 1_550_000_000L, dt = 1.35s > 1.2s)
        val afterCdTime = baseTime + 1_550_000_000L
        detector.processAcceleration(15.0f, 0f, 0f, afterCdTime)
        detector.processAcceleration(-15.0f, 0f, 0f, afterCdTime + 100_000_000L)
        val secondTrigger = detector.processAcceleration(15.0f, 0f, 0f, afterCdTime + 200_000_000L)

        assertTrue("After 1.2s cooldown expires, shake should trigger again", secondTrigger)
        assertEquals(2, shakeCount)
    }

    @Test
    fun testMultiAxisSupport_yAxisAndZAxisAlternatingShake() {
        // Y-axis shake
        var yShakeCount = 0
        val yDetector = ShakeDetector { yShakeCount++ }
        val t0 = 20_000_000_000L

        yDetector.processAcceleration(0f, 16.0f, 0f, t0)
        yDetector.processAcceleration(0f, -16.0f, 0f, t0 + 120_000_000L)
        val yTrigger = yDetector.processAcceleration(0f, 16.0f, 0f, t0 + 240_000_000L)
        assertTrue("Y-axis shake should trigger", yTrigger)
        assertEquals(1, yShakeCount)

        // Z-axis shake
        var zShakeCount = 0
        val zDetector = ShakeDetector { zShakeCount++ }
        val t1 = 30_000_000_000L

        zDetector.processAcceleration(0f, 0f, 16.0f, t1)
        zDetector.processAcceleration(0f, 0f, -16.0f, t1 + 120_000_000L)
        val zTrigger = zDetector.processAcceleration(0f, 0f, 16.0f, t1 + 240_000_000L)
        assertTrue("Z-axis shake should trigger", zTrigger)
        assertEquals(1, zShakeCount)
    }

    @Test
    fun testTimeWindowStationaryReset_resetsCadence() {
        var shakeCount = 0
        val detector = ShakeDetector { shakeCount++ }
        val t0 = 40_000_000_000L

        // Two alternating pulses
        detector.processAcceleration(15.0f, 0f, 0f, t0)
        detector.processAcceleration(-15.0f, 0f, 0f, t0 + 150_000_000L)

        // Stationary event after 800ms (> TIME_WINDOW_NS = 750ms)
        detector.processAcceleration(0f, 0f, 0f, t0 + 950_000_000L)

        // Next pulse arrives later -> should start a fresh cadence, NOT complete the previous one
        val nextPulse = detector.processAcceleration(15.0f, 0f, 0f, t0 + 1_050_000_000L)
        assertFalse("Cadence should have reset after stationary interval > 750ms", nextPulse)
        assertEquals(0, shakeCount)
    }

    @Test
    fun testInitialState_notInCooldownAtEarlyTimestamps() {
        var shakeCount = 0
        val detector = ShakeDetector { shakeCount++ }

        // Timestamps < 1.2s from epoch 0 (e.g. early system boot)
        val earlyTime = 200_000_000L // 200ms
        detector.processAcceleration(14.0f, 0f, 0f, earlyTime)
        detector.processAcceleration(-14.0f, 0f, 0f, earlyTime + 100_000_000L)
        val triggered = detector.processAcceleration(14.0f, 0f, 0f, earlyTime + 200_000_000L)

        assertTrue("Should not be blocked by cooldown on initial gesture at early boot timestamps", triggered)
        assertEquals(1, shakeCount)
    }
}
