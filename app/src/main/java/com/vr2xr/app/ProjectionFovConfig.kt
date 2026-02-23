package com.vr2xr.app

object ProjectionFovConfig {
    const val DEFAULT_DEGREES = 95f
    const val MIN_DEGREES = 60f
    const val MAX_DEGREES = 145f
    const val STEP_DEGREES = 1f
    const val PREFERENCES_FILE = "projection_settings"
    const val PREFERENCE_KEY_FOV_DEGREES = "fov_degrees"

    fun clampDegrees(value: Float): Float {
        return value.coerceIn(MIN_DEGREES, MAX_DEGREES)
    }

    fun normalizeDegrees(value: Float): Float {
        if (!value.isFinite()) {
            return DEFAULT_DEGREES
        }
        return clampDegrees(value)
    }
}
