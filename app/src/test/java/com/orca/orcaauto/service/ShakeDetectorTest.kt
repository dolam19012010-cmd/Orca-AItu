package com.orca.orcaauto.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ShakeDetectorTest {

    private var shakeTriggered = false
    private lateinit var detector: ShakeDetector

    @Before
    fun setUp() {
        shakeTriggered = false
        detector = ShakeDetector {
            shakeTriggered = true
        }
    }

    @Test
    fun testPrePeakThresholdConstant() {
        // Pre-peak threshold must be between gentle movements (2.5 m/s²) and full shake (9.5 m/s²)
        assertTrue(ShakeDetector.PRE_PEAK_THRESHOLD in 4.0f..5.0f)
        assertEquals(2, ShakeDetector.MIN_REVERSALS)
    }

    @Test
    fun testNaturalTwoBeatShakeTriggersSuccessfully() {
        val t0 = 10_000_000_000L // 10s baseline in ns

        // Beat 1: Initial forward flick (X = 12.0 m/s²)
        val r1 = detector.processAcceleration(12.0f, 1.5f, 0.5f, t0)
        assertFalse("Beat 1 must register first peak but not trigger yet", r1)

        // Beat 2: Reverse flick 180ms later (X = -11.5 m/s²)
        val t1 = t0 + 180_000_000L // +180ms
        val r2 = detector.processAcceleration(-11.5f, -1.0f, -0.5f, t1)
        assertTrue("Beat 2 reverse flick must satisfy 2-beat shake and trigger", r2)
    }

    @Test
    fun testWristTiltDuringReversalStillTriggersWith3dDotProduct() {
        val t0 = 10_000_000_000L

        // Beat 1: Wrist flicks right predominantly on X
        val r1 = detector.processAcceleration(11.0f, 2.0f, 1.0f, t0)
        assertFalse(r1)

        // Beat 2: Wrist flicks back with 3D rotation (X is negative, but Z tilted positive)
        // With old 1D dominant axis logic this would fail, but with 3D dot product it succeeds!
        val t1 = t0 + 200_000_000L // +200ms
        val r2 = detector.processAcceleration(-10.0f, -1.5f, 4.0f, t1)
        assertTrue("3D Dot product must detect true physical reversal despite wrist tilt", r2)
    }

    @Test
    fun testWalkingOrSubtlePhoneMovementDoesNotTrigger() {
        val t0 = 10_000_000_000L

        // Normal walking/typing acceleration (< 4.5 m/s²)
        var now = t0
        for (i in 0 until 10) {
            val r = detector.processAcceleration(3.5f, 2.0f, -1.5f, now)
            assertFalse("Walking acceleration under threshold must not trigger", r)
            now += 200_000_000L
        }
    }

    @Test
    fun testHighFrequencyEngineVibrationFilteredOut() {
        val t0 = 10_000_000_000L

        // Mechanical high frequency vibration (< 50ms interval, e.g. 30ms / 33Hz)
        val r1 = detector.processAcceleration(14.0f, 0f, 0f, t0)
        assertFalse(r1)

        val t1 = t0 + 25_000_000L // only 25ms later (< MIN_INTERVAL_NS 50ms)
        val r2 = detector.processAcceleration(-14.0f, 0f, 0f, t1)
        assertFalse("High frequency mechanical vibration must be dropped as noise", r2)
    }

    @Test
    fun testCooldownPreventsAccidentalImmediateRetrigger() {
        val t0 = 10_000_000_000L

        // 1st valid shake
        detector.processAcceleration(12.0f, 0f, 0f, t0)
        val triggered = detector.processAcceleration(-12.0f, 0f, 0f, t0 + 160_000_000L)
        assertTrue(triggered)

        // Immediate subsequent shake 300ms later during 1.2s cooldown
        val tCooldown = t0 + 460_000_000L
        detector.processAcceleration(13.0f, 0f, 0f, tCooldown)
        val retriggered = detector.processAcceleration(-13.0f, 0f, 0f, tCooldown + 160_000_000L)
        assertFalse("Cooldown period must suppress rapid repeated wakeups", retriggered)
    }
}
