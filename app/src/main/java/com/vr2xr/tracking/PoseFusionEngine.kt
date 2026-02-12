package com.vr2xr.tracking

class PoseFusionEngine {
    private var yawOffset: Float = 0f

    fun update(sample: ImuSample): PoseState {
        val yaw = sample.gz - yawOffset
        val pitch = sample.gy
        val roll = sample.gx
        return PoseState(
            yaw = yaw,
            pitch = pitch,
            roll = roll,
            trackingAvailable = true
        )
    }

    fun recenter() {
        yawOffset = 0f
    }
}
