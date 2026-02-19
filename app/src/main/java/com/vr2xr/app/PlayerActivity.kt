package com.vr2xr.app

import android.os.Build
import android.os.Bundle
import android.opengl.GLSurfaceView
import android.view.MotionEvent
import android.view.Surface
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.vr2xr.R
import com.vr2xr.databinding.ActivityPlayerBinding
import com.vr2xr.diag.DiagnosticsOverlay
import com.vr2xr.diag.DiagnosticsState
import com.vr2xr.display.ExternalDisplayController
import com.vr2xr.display.ExternalGlPresentation
import com.vr2xr.display.PhysicalDisplayMode
import com.vr2xr.player.CodecCapabilityProbe
import com.vr2xr.player.VrPlayerEngine
import com.vr2xr.render.RenderMode
import com.vr2xr.render.VrSbsRenderer
import com.vr2xr.source.SourceDescriptor
import com.vr2xr.tracking.OneProTrackingSessionManager
import com.vr2xr.tracking.PoseState
import io.onepro.xr.XrBiasState
import io.onepro.xr.XrSessionState
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class PlayerActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPlayerBinding
    private lateinit var player: VrPlayerEngine
    private lateinit var diagnosticsOverlay: DiagnosticsOverlay
    private lateinit var displayController: ExternalDisplayController
    private lateinit var internalRenderer: VrSbsRenderer
    private lateinit var trackingManager: OneProTrackingSessionManager
    private var decoderSummary: String = "unknown"
    private var mediaPrepared = false
    private var activeSurface: Surface? = null
    private var internalSurface: Surface? = null
    private var externalSurface: Surface? = null
    private var externalPresentation: ExternalGlPresentation? = null
    private var externalPresentationModeSignature: DisplayModeSignature? = null
    private var latestPose: PoseState = PoseState()
    private var trackingSummary: String = "manual touch controls"
    private var manualYawRad: Float = 0f
    private var manualPitchRad: Float = 0f
    private var lastTouchX: Float = 0f
    private var lastTouchY: Float = 0f
    private val renderMode = RenderMode()
    private val errors by lazy { ErrorUiController(this) }
    private var latestSessionState: XrSessionState = XrSessionState.Idle
    private var latestBiasState: XrBiasState = XrBiasState.Inactive
    private var wasStreaming = false
    private var trackingSessionStateJob: Job? = null
    private var trackingBiasStateJob: Job? = null
    private var trackingPoseJob: Job? = null

    private val source: SourceDescriptor? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_SOURCE, SourceDescriptor::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_SOURCE)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        trackingManager = (application as Vr2xrApplication).trackingSessionManager
        displayController = ExternalDisplayController(
            context = this,
            listener = object : ExternalDisplayController.Listener {
                override fun onModeChanged(mode: PhysicalDisplayMode?) {
                    runOnUiThread {
                        syncExternalPresentation()
                        updateDiagnostics()
                    }
                }
            }
        )
        diagnosticsOverlay = DiagnosticsOverlay(this)
        binding.diagnosticsContainer.addView(diagnosticsOverlay)

        player = VrPlayerEngine(this)

        val resolvedSource = source
        if (resolvedSource == null) {
            finish()
            return
        }

        binding.sourceText.text = resolvedSource.normalized
        decoderSummary = CodecCapabilityProbe().findHevcDecoderSummary()

        internalRenderer = VrSbsRenderer(
            listener = object : VrSbsRenderer.Listener {
                override fun onVideoSurfaceReady(surface: Surface) {
                    runOnUiThread { onInternalSurfaceReady(resolvedSource, surface) }
                }

                override fun onVideoSurfaceDestroyed() {
                    runOnUiThread { onInternalSurfaceDestroyed() }
                }
            }
        )
        binding.videoSurface.setEGLContextClientVersion(2)
        binding.videoSurface.setRenderer(internalRenderer)
        binding.videoSurface.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        internalRenderer.setRenderMode(renderMode)
        internalRenderer.setPose(latestPose)
        binding.videoSurface.setOnTouchListener { _, event -> handleLookTouch(event) }

        binding.recenterButton.setOnClickListener { recenterView() }
        binding.calibrateButton.setOnClickListener { runCalibration() }
        binding.zeroViewButton.setOnClickListener { runZeroView() }
        updateTrackingSummary()
        updateTrackingControls()
        updateDiagnostics()
    }

    override fun onStart() {
        super.onStart()
        displayController.start()
        binding.videoSurface.onResume()
        syncExternalPresentation()
        val pose = buildManualPose()
        latestPose = pose
        applyPoseToRenderers(pose)
        attachTrackingCollectors()
        updateDiagnostics()
    }

    override fun onStop() {
        super.onStop()
        dismissExternalPresentation()
        if (::internalRenderer.isInitialized) {
            binding.videoSurface.onPause()
        }
        displayController.stop()
        if (::player.isInitialized) {
            player.pause()
        }
        trackingSessionStateJob?.cancel()
        trackingSessionStateJob = null
        trackingBiasStateJob?.cancel()
        trackingBiasStateJob = null
        trackingPoseJob?.cancel()
        trackingPoseJob = null
    }

    override fun onDestroy() {
        dismissExternalPresentation()
        if (::internalRenderer.isInitialized) {
            internalRenderer.release()
        }
        if (::player.isInitialized) {
            player.release()
        }
        super.onDestroy()
    }

    companion object {
        const val EXTRA_SOURCE = "extra_source"
    }

    private fun attachTrackingCollectors() {
        trackingSessionStateJob?.cancel()
        trackingSessionStateJob = lifecycleScope.launch {
            trackingManager.sessionState.collect { state ->
                handleTrackingSessionState(state)
            }
        }
        trackingBiasStateJob?.cancel()
        trackingBiasStateJob = lifecycleScope.launch {
            trackingManager.biasState.collect { state ->
                latestBiasState = state
                updateTrackingSummary()
                updateDiagnostics()
            }
        }
        trackingPoseJob?.cancel()
        trackingPoseJob = lifecycleScope.launch {
            trackingManager.pose.collect { pose ->
                if (pose == null || !pose.trackingAvailable) {
                    return@collect
                }
                latestPose = pose
                applyPoseToRenderers(pose)
            }
        }
    }

    private fun handleTrackingSessionState(state: XrSessionState) {
        latestSessionState = state
        val streaming = state is XrSessionState.Streaming
        if (!streaming && wasStreaming) {
            manualYawRad = latestPose.yaw
            manualPitchRad = latestPose.pitch
            val manualPose = buildManualPose()
            latestPose = manualPose
            applyPoseToRenderers(manualPose)
        }
        wasStreaming = streaming
        updateTrackingControls()
        updateTrackingSummary()
        updateDiagnostics()
    }

    private fun updateTrackingControls() {
        binding.zeroViewButton.isEnabled = latestSessionState is XrSessionState.Streaming
    }

    private fun updateTrackingSummary() {
        trackingSummary = when (val state = latestSessionState) {
            XrSessionState.Idle,
            XrSessionState.Stopped -> getString(R.string.tracking_state_manual)

            XrSessionState.Connecting -> getString(R.string.tracking_state_connecting)

            is XrSessionState.Calibrating -> getString(
                R.string.tracking_state_calibrating,
                state.calibrationSampleCount,
                state.calibrationTarget
            )

            is XrSessionState.Streaming -> getString(R.string.tracking_state_streaming)

            is XrSessionState.Error -> getString(R.string.tracking_state_error)
        }
        if (latestBiasState is XrBiasState.Error && latestSessionState !is XrSessionState.Streaming) {
            trackingSummary = getString(R.string.tracking_state_error)
        }
    }

    private fun runCalibration() {
        lifecycleScope.launch {
            when (latestSessionState) {
                XrSessionState.Connecting,
                is XrSessionState.Calibrating -> Unit

                is XrSessionState.Streaming -> {
                    trackingManager.recalibrate()
                        .onFailure { error ->
                            errors.show(getString(R.string.tracking_action_failed, error.message ?: "recalibration failed"))
                        }
                }

                else -> {
                    trackingManager.runCalibration()
                        .onFailure { error ->
                            errors.show(getString(R.string.tracking_action_failed, error.message ?: "calibration failed"))
                        }
                }
            }
        }
    }

    private fun runZeroView() {
        lifecycleScope.launch {
            trackingManager.zeroView()
                .onFailure { error ->
                    errors.show(getString(R.string.tracking_action_failed, error.message ?: "zero view failed"))
                }
        }
    }

    private fun updateDiagnostics() {
        val mode = displayController.currentPhysicalMode()
        val route = when (activeSurface) {
            externalSurface -> "external"
            internalSurface -> "internal"
            else -> "none"
        }
        diagnosticsOverlay.update(
            DiagnosticsState(
                displaySummary = mode?.let { "${it.width}x${it.height}@${it.refreshRateHz} [$route]" }
                    ?: "internal-only [$route]",
                trackingSummary = trackingSummary,
                decoderSummary = decoderSummary,
                droppedFrames = 0
            )
        )
    }

    private fun syncExternalPresentation() {
        val display = displayController.currentPresentationDisplay()
        val mode = displayController.currentPhysicalMode()
        if (display == null || mode == null) {
            dismissExternalPresentation()
            bindPreferredSurface(source)
            return
        }
        val currentSignature = mode.toSignature()
        val activePresentation = externalPresentation
        if (
            activePresentation != null &&
            activePresentation.display.displayId == display.displayId &&
            externalPresentationModeSignature == currentSignature
        ) {
            bindPreferredSurface(source)
            return
        }

        dismissExternalPresentation()
        runCatching {
            ExternalGlPresentation(
                context = this,
                display = display,
                listener = object : ExternalGlPresentation.Listener {
                    override fun onVideoSurfaceReady(surface: Surface) {
                        runOnUiThread {
                            externalSurface = surface
                            bindPreferredSurface(source)
                        }
                    }

                    override fun onVideoSurfaceDestroyed() {
                        runOnUiThread {
                            externalSurface = null
                            bindPreferredSurface(source)
                        }
                    }
                }
            ).also {
                externalPresentation = it
                externalPresentationModeSignature = currentSignature
                it.setRenderMode(renderMode)
                it.setPose(latestPose)
                it.show()
            }
        }.onFailure {
            errors.show("Failed to start external presentation: ${it.message ?: "unknown error"}")
            externalPresentation = null
            externalSurface = null
            externalPresentationModeSignature = null
        }
    }

    private fun dismissExternalPresentation() {
        externalPresentation?.dismiss()
        externalPresentation = null
        externalSurface = null
        externalPresentationModeSignature = null
    }

    private fun onInternalSurfaceReady(source: SourceDescriptor, surface: Surface) {
        internalSurface = surface
        bindPreferredSurface(source)
    }

    private fun onInternalSurfaceDestroyed() {
        internalSurface = null
        bindPreferredSurface(source)
    }

    private fun bindPreferredSurface(source: SourceDescriptor?) {
        if (source == null) return
        val preferredSurface = externalSurface ?: internalSurface
        if (preferredSurface == null) {
            activeSurface = null
            player.pause()
            updateDiagnostics()
            return
        }

        if (!mediaPrepared) {
            player.prepare(source, preferredSurface)
            mediaPrepared = true
        } else if (activeSurface !== preferredSurface) {
            player.bindVideoSurface(preferredSurface)
        }

        activeSurface = preferredSurface
        player.play()
        updateDiagnostics()
    }

    private fun applyPoseToRenderers(pose: PoseState) {
        if (::internalRenderer.isInitialized) {
            internalRenderer.setPose(pose)
        }
        externalPresentation?.setPose(pose)
    }

    private fun recenterView() {
        if (latestSessionState is XrSessionState.Streaming) {
            runZeroView()
            return
        }
        manualYawRad = 0f
        manualPitchRad = 0f
        val pose = buildManualPose()
        latestPose = pose
        applyPoseToRenderers(pose)
        updateDiagnostics()
    }

    private fun handleLookTouch(event: MotionEvent): Boolean {
        if (latestSessionState is XrSessionState.Streaming) {
            return false
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = event.x
                lastTouchY = event.y
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - lastTouchX
                val dy = event.y - lastTouchY
                lastTouchX = event.x
                lastTouchY = event.y

                manualYawRad = (manualYawRad - (dx * TOUCH_RADIANS_PER_PIXEL))
                    .coerceIn(-MAX_MANUAL_YAW_RAD, MAX_MANUAL_YAW_RAD)
                manualPitchRad = (manualPitchRad - (dy * TOUCH_RADIANS_PER_PIXEL))
                    .coerceIn(-MAX_MANUAL_PITCH_RAD, MAX_MANUAL_PITCH_RAD)

                val pose = buildManualPose()
                latestPose = pose
                applyPoseToRenderers(pose)
                updateDiagnostics()
                return true
            }
        }
        return false
    }

    private fun buildManualPose(): PoseState {
        val q = eulerToQuaternion(manualYawRad, manualPitchRad, 0f)
        return PoseState(
            yaw = manualYawRad,
            pitch = manualPitchRad,
            roll = 0f,
            qx = q.first,
            qy = q.second,
            qz = q.third,
            qw = q.fourth,
            trackingAvailable = false
        )
    }

    private fun eulerToQuaternion(yaw: Float, pitch: Float, roll: Float): Quaternion {
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
        return Quaternion(qx, qy, qz, qw)
    }
}

private data class Quaternion(
    val first: Float,
    val second: Float,
    val third: Float,
    val fourth: Float
)

private data class DisplayModeSignature(
    val displayId: Int,
    val modeId: Int,
    val width: Int,
    val height: Int
)

private fun PhysicalDisplayMode.toSignature(): DisplayModeSignature {
    return DisplayModeSignature(
        displayId = displayId,
        modeId = modeId,
        width = width,
        height = height
    )
}

private const val TOUCH_RADIANS_PER_PIXEL = 0.0035f
private const val MAX_MANUAL_YAW_RAD = 3.1415927f
private const val MAX_MANUAL_PITCH_RAD = 1.2f
