package com.vr2xr.tracking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class RuntimePoseControllerTest {
    @Test
    fun scalesPoseWithImuSensitivity() {
        val controller = RuntimePoseController(initialSensitivity = 0.5f)
        val updated = controller.onTrackingPoseUpdated(
            pose(yaw = 1.2f, pitch = -0.6f, roll = 0.4f)
        )

        assertEquals(0.6f, updated.yaw, EPSILON)
        assertEquals(-0.3f, updated.pitch, EPSILON)
        assertEquals(0.2f, updated.roll, EPSILON)
    }

    @Test
    fun holdsLastAppliedPoseWhenImuIsDisabled() {
        val controller = RuntimePoseController(initialSensitivity = 0.5f)
        controller.onTrackingPoseUpdated(pose(yaw = 1.0f, pitch = 0.2f, roll = 0f))
        controller.applyTouchpadBiasDelta(yawDeltaRad = 0.1f, pitchDeltaRad = -0.05f)
        val frozen = controller.setImuTrackingEnabled(false)

        val afterTrackingUpdate = controller.onTrackingPoseUpdated(
            pose(yaw = 4.0f, pitch = 2.0f, roll = 0.3f)
        )

        assertEquals(frozen.yaw, afterTrackingUpdate.yaw, EPSILON)
        assertEquals(frozen.pitch, afterTrackingUpdate.pitch, EPSILON)
        assertEquals(frozen.roll, afterTrackingUpdate.roll, EPSILON)
    }

    @Test
    fun commitsTouchpadBiasOverFrozenBasePoseAndKeepsItDurable() {
        val controller = RuntimePoseController(initialSensitivity = 0.5f)
        controller.onTrackingPoseUpdated(pose(yaw = 2.0f, pitch = 0f, roll = 0f))
        controller.setImuTrackingEnabled(false)

        val biased = controller.applyTouchpadBiasDelta(yawDeltaRad = 0.1f, pitchDeltaRad = -0.1f)
        val committed = controller.commitTouchpadBias()
        val centeredGesture = controller.applyTouchpadBiasDelta(yawDeltaRad = 0f, pitchDeltaRad = 0f)

        assertEquals(1.1f, biased.yaw, EPSILON)
        assertEquals(-0.1f, biased.pitch, EPSILON)
        assertEquals(1.1f, committed.yaw, EPSILON)
        assertEquals(-0.1f, committed.pitch, EPSILON)
        assertEquals(1.1f, centeredGesture.yaw, EPSILON)
        assertEquals(-0.1f, centeredGesture.pitch, EPSILON)
    }

    @Test
    fun touchpadDeltaAccumulatesWithoutLegacyClamp() {
        val controller = RuntimePoseController(initialSensitivity = 0.5f)
        controller.onTrackingPoseUpdated(pose(yaw = 0f, pitch = 0f, roll = 0f))

        controller.applyTouchpadBiasDelta(yawDeltaRad = 0.2f, pitchDeltaRad = -0.1f)
        controller.applyTouchpadBiasDelta(yawDeltaRad = 0.25f, pitchDeltaRad = -0.22f)
        val committed = controller.commitTouchpadBias()
        val centeredGesture = controller.applyTouchpadBiasDelta(yawDeltaRad = 0f, pitchDeltaRad = 0f)

        assertEquals(0.45f, committed.yaw, EPSILON)
        assertEquals(-0.32f, committed.pitch, EPSILON)
        assertEquals(0.45f, centeredGesture.yaw, EPSILON)
        assertEquals(-0.32f, centeredGesture.pitch, EPSILON)
    }

    @Test
    fun liveTouchpadDeltaLayersOnTopOfCommittedBias() {
        val controller = RuntimePoseController(initialSensitivity = 0.5f)
        controller.onTrackingPoseUpdated(pose(yaw = 2.0f, pitch = 0f, roll = 0f))
        controller.setImuTrackingEnabled(false)
        controller.applyTouchpadBiasDelta(yawDeltaRad = 0.1f, pitchDeltaRad = 0f)
        controller.commitTouchpadBias()

        val liveGesture = controller.applyTouchpadBiasDelta(yawDeltaRad = 0.08f, pitchDeltaRad = 0f)
        val committed = controller.commitTouchpadBias()
        val centeredGesture = controller.applyTouchpadBiasDelta(yawDeltaRad = 0f, pitchDeltaRad = 0f)

        assertEquals(1.18f, liveGesture.yaw, EPSILON)
        assertEquals(1.18f, committed.yaw, EPSILON)
        assertEquals(1.18f, centeredGesture.yaw, EPSILON)
        assertEquals(0f, centeredGesture.pitch, EPSILON)
    }

    @Test
    fun resetTouchpadBiasClearsCommittedAndGestureOffsets() {
        val controller = RuntimePoseController(initialSensitivity = 0.5f)
        controller.onTrackingPoseUpdated(pose(yaw = 2.0f, pitch = 0f, roll = 0f))
        controller.applyTouchpadBiasDelta(yawDeltaRad = 0.1f, pitchDeltaRad = 0f)
        controller.commitTouchpadBias()
        controller.applyTouchpadBiasDelta(yawDeltaRad = 0.08f, pitchDeltaRad = -0.05f)

        val reset = controller.resetTouchpadBias()

        assertEquals(1.0f, reset.yaw, EPSILON)
        assertEquals(0f, reset.pitch, EPSILON)
    }

    @Test
    fun resumesLatestLivePoseWhenImuIsReenabled() {
        val controller = RuntimePoseController(initialSensitivity = 0.5f)
        controller.onTrackingPoseUpdated(pose(yaw = 1.0f, pitch = 0f, roll = 0f))
        controller.setImuTrackingEnabled(false)
        controller.onTrackingPoseUpdated(pose(yaw = 3.0f, pitch = 1.0f, roll = 0f))

        val reenabled = controller.setImuTrackingEnabled(true)

        assertEquals(1.5f, reenabled.yaw, EPSILON)
        assertEquals(0.5f, reenabled.pitch, EPSILON)
    }

    @Test
    fun clampsSensitivityToSupportedRange() {
        val controller = RuntimePoseController(initialSensitivity = 0.5f)
        controller.setImuSensitivity(5f)
        val high = controller.onTrackingPoseUpdated(pose(yaw = 1.0f, pitch = 0f, roll = 0f))
        controller.setImuSensitivity(-5f)
        val low = controller.onTrackingPoseUpdated(pose(yaw = 1.0f, pitch = 0f, roll = 0f))

        assertEquals(MAX_IMU_SENSITIVITY, high.yaw, EPSILON)
        assertEquals(MIN_IMU_SENSITIVITY, low.yaw, EPSILON)
    }

    @Test
    fun normalizesLargeTouchpadBiasAnglesWithoutReintroducingCap() {
        val controller = RuntimePoseController(initialSensitivity = 0.5f)
        controller.onTrackingPoseUpdated(pose(yaw = 0f, pitch = 0f, roll = 0f))

        controller.applyTouchpadBiasDelta(yawDeltaRad = 7f, pitchDeltaRad = -7f)
        val committed = controller.commitTouchpadBias()

        assertTrue(abs(committed.yaw) <= PI_RADIANS)
        assertTrue(abs(committed.pitch) <= PI_RADIANS)
    }
}

private fun pose(yaw: Float, pitch: Float, roll: Float): PoseState {
    return PoseState(
        yaw = yaw,
        pitch = pitch,
        roll = roll,
        trackingAvailable = true
    )
}

private const val EPSILON = 1e-6f
private const val PI_RADIANS = 3.1415927f
