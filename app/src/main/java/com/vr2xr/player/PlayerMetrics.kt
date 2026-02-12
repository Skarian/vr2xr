package com.vr2xr.player

data class PlayerMetrics(
    val droppedFrames: Long = 0,
    val videoFormat: String = "unknown",
    val decoderName: String = "unknown",
    val error: String? = null
)
