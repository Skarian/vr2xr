package com.vr2xr.app

import org.junit.Assert.assertEquals
import org.junit.Test

class ProjectionFovConfigTest {
    @Test
    fun clampDegreesLimitsToConfiguredRange() {
        assertEquals(ProjectionFovConfig.MIN_DEGREES, ProjectionFovConfig.clampDegrees(-100f), EPSILON)
        assertEquals(ProjectionFovConfig.MAX_DEGREES, ProjectionFovConfig.clampDegrees(1000f), EPSILON)
    }

    @Test
    fun normalizeDegreesFallsBackToDefaultForNonFiniteInputs() {
        assertEquals(
            ProjectionFovConfig.DEFAULT_DEGREES,
            ProjectionFovConfig.normalizeDegrees(Float.NaN),
            EPSILON
        )
        assertEquals(
            ProjectionFovConfig.DEFAULT_DEGREES,
            ProjectionFovConfig.normalizeDegrees(Float.POSITIVE_INFINITY),
            EPSILON
        )
        assertEquals(
            ProjectionFovConfig.DEFAULT_DEGREES,
            ProjectionFovConfig.normalizeDegrees(Float.NEGATIVE_INFINITY),
            EPSILON
        )
    }

    @Test
    fun normalizeDegreesClampsFiniteValues() {
        assertEquals(ProjectionFovConfig.MIN_DEGREES, ProjectionFovConfig.normalizeDegrees(0f), EPSILON)
        assertEquals(ProjectionFovConfig.MAX_DEGREES, ProjectionFovConfig.normalizeDegrees(1000f), EPSILON)
        assertEquals(99f, ProjectionFovConfig.normalizeDegrees(99f), EPSILON)
    }
}

private const val EPSILON = 1e-6f
