package com.vr2xr.tracking

import kotlin.math.cos
import kotlin.math.sin

data class TouchpadBias(
    val yawRad: Float = 0f,
    val pitchRad: Float = 0f
)

class RuntimePoseController(
    initialSensitivity: Float,
    private val minSensitivity: Float = MIN_IMU_SENSITIVITY,
    private val maxSensitivity: Float = MAX_IMU_SENSITIVITY
) {
    private var imuSensitivity = initialSensitivity.coerceIn(minSensitivity, maxSensitivity)
    private var imuTrackingEnabled = true
    private var latestTrackingPose = PoseState()
    private var latestScaledImuPose = scalePose(latestTrackingPose)
    private var frozenImuPose = latestScaledImuPose
    private var accumulatedTouchpadBias = TouchpadBias()
    private var touchpadGestureBias = TouchpadBias()
    private var appliedPose = composePose()

    fun imuSensitivity(): Float = imuSensitivity

    fun isImuTrackingEnabled(): Boolean = imuTrackingEnabled

    fun currentPose(): PoseState = appliedPose

    fun onTrackingPoseUpdated(pose: PoseState): PoseState {
        latestTrackingPose = pose
        latestScaledImuPose = scalePose(pose)
        if (!imuTrackingEnabled) {
            return appliedPose
        }
        appliedPose = composePose()
        return appliedPose
    }

    fun setImuSensitivity(value: Float): PoseState {
        imuSensitivity = value.coerceIn(minSensitivity, maxSensitivity)
        latestScaledImuPose = scalePose(latestTrackingPose)
        if (!imuTrackingEnabled) {
            return appliedPose
        }
        appliedPose = composePose()
        return appliedPose
    }

    fun setImuTrackingEnabled(enabled: Boolean): PoseState {
        if (imuTrackingEnabled == enabled) {
            return appliedPose
        }
        if (!enabled) {
            frozenImuPose = latestScaledImuPose
        }
        imuTrackingEnabled = enabled
        appliedPose = composePose()
        return appliedPose
    }

    fun applyTouchpadBiasDelta(yawDeltaRad: Float, pitchDeltaRad: Float): PoseState {
        touchpadGestureBias = normalizeTouchpadBias(
            TouchpadBias(
                yawRad = touchpadGestureBias.yawRad + yawDeltaRad,
                pitchRad = touchpadGestureBias.pitchRad + pitchDeltaRad
            )
        )
        appliedPose = composePose()
        return appliedPose
    }

    fun commitTouchpadBias(): PoseState {
        accumulatedTouchpadBias = normalizeTouchpadBias(combinedTouchpadBias())
        touchpadGestureBias = TouchpadBias()
        appliedPose = composePose()
        return appliedPose
    }

    fun resetTouchpadBias(): PoseState {
        accumulatedTouchpadBias = TouchpadBias()
        touchpadGestureBias = TouchpadBias()
        appliedPose = composePose()
        return appliedPose
    }

    private fun composePose(): PoseState {
        val basePose = if (imuTrackingEnabled) latestScaledImuPose else frozenImuPose
        val bias = combinedTouchpadBias()
        val yaw = basePose.yaw + bias.yawRad
        val pitch = basePose.pitch + bias.pitchRad
        val roll = basePose.roll
        val quaternion = toPoseQuaternion(yaw, pitch, roll)
        return basePose.copy(
            yaw = yaw,
            pitch = pitch,
            roll = roll,
            qx = quaternion.x,
            qy = quaternion.y,
            qz = quaternion.z,
            qw = quaternion.w
        )
    }

    private fun combinedTouchpadBias(): TouchpadBias {
        return normalizeTouchpadBias(
            TouchpadBias(
                yawRad = accumulatedTouchpadBias.yawRad + touchpadGestureBias.yawRad,
                pitchRad = accumulatedTouchpadBias.pitchRad + touchpadGestureBias.pitchRad
            )
        )
    }

    private fun scalePose(pose: PoseState): PoseState {
        val yaw = pose.yaw * imuSensitivity
        val pitch = pose.pitch * imuSensitivity
        val roll = pose.roll * imuSensitivity
        val quaternion = toPoseQuaternion(yaw, pitch, roll)
        return pose.copy(
            yaw = yaw,
            pitch = pitch,
            roll = roll,
            qx = quaternion.x,
            qy = quaternion.y,
            qz = quaternion.z,
            qw = quaternion.w
        )
    }

    private fun normalizeTouchpadBias(bias: TouchpadBias): TouchpadBias {
        return TouchpadBias(
            yawRad = normalizeRadians(bias.yawRad),
            pitchRad = normalizeRadians(bias.pitchRad)
        )
    }
}

private fun normalizeRadians(value: Float): Float {
    var normalized = value % TWO_PI_RADIANS
    if (normalized > PI_RADIANS) {
        normalized -= TWO_PI_RADIANS
    } else if (normalized < -PI_RADIANS) {
        normalized += TWO_PI_RADIANS
    }
    return normalized
}

private fun toPoseQuaternion(yaw: Float, pitch: Float, roll: Float): PoseQuaternion {
    val cy = cos(yaw * 0.5f)
    val sy = sin(yaw * 0.5f)
    val cp = cos(pitch * 0.5f)
    val sp = sin(pitch * 0.5f)
    val cr = cos(roll * 0.5f)
    val sr = sin(roll * 0.5f)
    val qw = (cr * cp * cy) + (sr * sp * sy)
    val qx = (sr * cp * cy) - (cr * sp * sy)
    val qy = (cr * sp * cy) + (sr * cp * sy)
    val qz = (cr * cp * sy) - (sr * sp * cy)
    return PoseQuaternion(qx, qy, qz, qw)
}

private data class PoseQuaternion(
    val x: Float,
    val y: Float,
    val z: Float,
    val w: Float
)

const val MIN_IMU_SENSITIVITY = 0.1f
const val MAX_IMU_SENSITIVITY = 0.9f
const val TOUCHPAD_DRAG_FULL_TRAVEL_RADIANS = 0.30f
private const val PI_RADIANS = 3.1415927f
private const val TWO_PI_RADIANS = PI_RADIANS * 2f
