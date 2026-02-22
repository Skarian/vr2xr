package com.vr2xr.tracking

import org.junit.Assert.assertEquals
import org.junit.Test

class TouchpadAutoDragTest {
    @Test
    fun edgeStrengthIsZeroBelowOrAtThreshold() {
        assertEquals(0f, edgeAutoDragStrength(0.89f, EDGE_THRESHOLD), EPSILON)
        assertEquals(0f, edgeAutoDragStrength(0.9f, EDGE_THRESHOLD), EPSILON)
        assertEquals(0f, edgeAutoDragStrength(-0.9f, EDGE_THRESHOLD), EPSILON)
    }

    @Test
    fun edgeStrengthRampsBetweenThresholdAndEdge() {
        val positive = edgeAutoDragStrength(0.95f, EDGE_THRESHOLD)
        val negative = edgeAutoDragStrength(-0.95f, EDGE_THRESHOLD)

        assertEquals(0.5f, positive, EPSILON)
        assertEquals(-0.5f, negative, EPSILON)
    }

    @Test
    fun edgeStrengthClampsAtFullScaleNearBoundary() {
        assertEquals(1f, edgeAutoDragStrength(1f, EDGE_THRESHOLD), EPSILON)
        assertEquals(-1f, edgeAutoDragStrength(-1f, EDGE_THRESHOLD), EPSILON)
    }

    @Test
    fun autoDragDeltaReturnsZeroWhenInsideThreshold() {
        val delta = computeTouchpadAutoDragDelta(
            normalizedX = 0.4f,
            normalizedY = -0.2f,
            intervalMs = INTERVAL_MS,
            edgeThreshold = EDGE_THRESHOLD,
            radiansPerSecond = RADIANS_PER_SECOND
        )

        assertEquals(0f, delta.yawDeltaRad, EPSILON)
        assertEquals(0f, delta.pitchDeltaRad, EPSILON)
    }

    @Test
    fun autoDragDeltaUsesExpectedSignsAndMagnitude() {
        val delta = computeTouchpadAutoDragDelta(
            normalizedX = 1f,
            normalizedY = -1f,
            intervalMs = INTERVAL_MS,
            edgeThreshold = EDGE_THRESHOLD,
            radiansPerSecond = RADIANS_PER_SECOND
        )

        val expectedPerStep = RADIANS_PER_SECOND * (INTERVAL_MS / 1000f)
        assertEquals(expectedPerStep, delta.yawDeltaRad, EPSILON)
        assertEquals(expectedPerStep, delta.pitchDeltaRad, EPSILON)
    }

    @Test
    fun edgeThresholdIsSafelyHandledNearOne() {
        val delta = computeTouchpadAutoDragDelta(
            normalizedX = 1f,
            normalizedY = 0f,
            intervalMs = INTERVAL_MS,
            edgeThreshold = 1f,
            radiansPerSecond = RADIANS_PER_SECOND
        )

        val expectedPerStep = RADIANS_PER_SECOND * (INTERVAL_MS / 1000f)
        assertEquals(expectedPerStep, delta.yawDeltaRad, EPSILON)
        assertEquals(0f, delta.pitchDeltaRad, EPSILON)
    }
}

private const val EPSILON = 1e-6f
private const val EDGE_THRESHOLD = 0.9f
private const val RADIANS_PER_SECOND = 0.24f
private const val INTERVAL_MS = 16L
