package com.vr2xr.tracking

import kotlin.math.abs

data class TouchpadAutoDragDelta(
    val yawDeltaRad: Float = 0f,
    val pitchDeltaRad: Float = 0f
)

fun computeTouchpadAutoDragDelta(
    normalizedX: Float,
    normalizedY: Float,
    intervalMs: Long,
    edgeThreshold: Float,
    radiansPerSecond: Float
): TouchpadAutoDragDelta {
    val yawStrength = edgeAutoDragStrength(normalizedX, edgeThreshold)
    val pitchStrength = edgeAutoDragStrength(normalizedY, edgeThreshold)
    if (yawStrength == 0f && pitchStrength == 0f) {
        return TouchpadAutoDragDelta()
    }
    val stepSeconds = intervalMs / 1000f
    return TouchpadAutoDragDelta(
        yawDeltaRad = yawStrength * radiansPerSecond * stepSeconds,
        pitchDeltaRad = -pitchStrength * radiansPerSecond * stepSeconds
    )
}

fun edgeAutoDragStrength(normalizedAxis: Float, edgeThreshold: Float): Float {
    val safeEdgeThreshold = edgeThreshold.coerceIn(0f, MAX_EDGE_THRESHOLD)
    val magnitude = abs(normalizedAxis)
    if (magnitude <= safeEdgeThreshold) {
        return 0f
    }
    val scaledMagnitude = ((magnitude - safeEdgeThreshold) /
        (1f - safeEdgeThreshold)).coerceIn(0f, 1f)
    return if (normalizedAxis >= 0f) scaledMagnitude else -scaledMagnitude
}

private const val MAX_EDGE_THRESHOLD = 0.999f
