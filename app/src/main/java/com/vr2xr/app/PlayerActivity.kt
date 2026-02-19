package com.vr2xr.app

import android.opengl.GLSurfaceView
import android.os.Build
import android.os.Bundle
import android.view.MotionEvent
import android.view.Surface
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.vr2xr.R
import com.vr2xr.databinding.ActivityPlayerBinding
import com.vr2xr.diag.DiagnosticsOverlay
import com.vr2xr.diag.DiagnosticsState
import com.vr2xr.display.ActiveRoute
import com.vr2xr.display.DisplayRouteSnapshot
import com.vr2xr.display.DisplayRouteState
import com.vr2xr.display.DisplayRouteStateMachine
import com.vr2xr.display.ExternalDisplayController
import com.vr2xr.display.ExternalGlPresentation
import com.vr2xr.display.PhysicalDisplayMode
import com.vr2xr.display.RouteTarget
import com.vr2xr.display.asDebugLabel
import com.vr2xr.player.CodecCapabilityProbe
import com.vr2xr.player.PlaybackSessionOwner
import com.vr2xr.render.RenderMode
import com.vr2xr.render.VrSbsRenderer
import com.vr2xr.source.SourceDescriptor
import com.vr2xr.tracking.OneProTrackingSessionManager
import com.vr2xr.tracking.PoseState
import io.onepro.xr.XrBiasState
import io.onepro.xr.XrSessionState
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class PlayerActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPlayerBinding
    private lateinit var playbackSession: PlaybackSessionOwner
    private lateinit var diagnosticsOverlay: DiagnosticsOverlay
    private lateinit var displayController: ExternalDisplayController
    private lateinit var internalRenderer: VrSbsRenderer
    private lateinit var trackingManager: OneProTrackingSessionManager

    private val routeStateMachine = DisplayRouteStateMachine()
    private var routeState: DisplayRouteState = DisplayRouteState.NoOutput
    private var activeRoute: ActiveRoute = ActiveRoute.NONE

    private var decoderSummary: String = "unknown"
    private var activeSurface: Surface? = null
    private var internalSurface: Surface? = null
    private var externalSurface: Surface? = null
    private var externalPresentation: ExternalGlPresentation? = null
    private var externalPresentationModeSignature: DisplayModeSignature? = null
    private var externalPresentationToken: Long = 0L
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
    private var externalReconnectWatchdogJob: Job? = null

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

        val app = application as Vr2xrApplication
        trackingManager = app.trackingSessionManager
        playbackSession = app.playbackSessionOwner

        displayController = ExternalDisplayController(
            context = this,
            listener = object : ExternalDisplayController.Listener {
                override fun onModeChanged(mode: PhysicalDisplayMode?) {
                    runOnUiThread {
                        syncExternalPresentation()
                        updateRouting()
                    }
                }
            }
        )

        diagnosticsOverlay = DiagnosticsOverlay(this)
        binding.diagnosticsContainer.addView(diagnosticsOverlay)

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
                    runOnUiThread { onInternalSurfaceReady(surface) }
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

        binding.transportControls.player = playbackSession.player
        binding.transportControls.show()

        binding.recenterButton.setOnClickListener { recenterView() }
        binding.calibrateButton.setOnClickListener { runCalibration() }
        binding.zeroViewButton.setOnClickListener { runZeroView() }

        playbackSession.attachSource(resolvedSource, forceReset = savedInstanceState == null)
        updateTrackingSummary()
        updateTrackingControls()
        renderRouteStatus()
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
        updateRouting()
        updateDiagnostics()
        startExternalReconnectWatchdog()
    }

    override fun onStop() {
        super.onStop()
        dismissExternalPresentation()
        clearActiveSurface()
        if (::internalRenderer.isInitialized) {
            binding.videoSurface.onPause()
        }
        displayController.stop()
        if (!isChangingConfigurations) {
            playbackSession.pauseForInterruption()
        }
        externalReconnectWatchdogJob?.cancel()
        externalReconnectWatchdogJob = null
        trackingSessionStateJob?.cancel()
        trackingSessionStateJob = null
        trackingBiasStateJob?.cancel()
        trackingBiasStateJob = null
        trackingPoseJob?.cancel()
        trackingPoseJob = null
    }

    override fun onDestroy() {
        dismissExternalPresentation()
        clearActiveSurface()
        if (::internalRenderer.isInitialized) {
            internalRenderer.release()
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

    private fun startExternalReconnectWatchdog() {
        externalReconnectWatchdogJob?.cancel()
        externalReconnectWatchdogJob = lifecycleScope.launch {
            while (isActive) {
                if (routeState !is DisplayRouteState.ExternalActive) {
                    syncExternalPresentation()
                    updateRouting()
                }
                delay(EXTERNAL_RECONNECT_WATCHDOG_MS)
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
        binding.trackingStatusText.text = getString(R.string.tracking_status_template, trackingSummary)
    }

    private fun runCalibration() {
        lifecycleScope.launch {
            when (latestSessionState) {
                XrSessionState.Connecting,
                is XrSessionState.Calibrating -> Unit

                is XrSessionState.Streaming -> {
                    trackingManager.recalibrate()
                        .onFailure { error ->
                            errors.show(
                                getString(
                                    R.string.tracking_action_failed,
                                    error.message ?: "recalibration failed"
                                )
                            )
                        }
                }

                else -> {
                    trackingManager.runCalibration()
                        .onFailure { error ->
                            errors.show(
                                getString(
                                    R.string.tracking_action_failed,
                                    error.message ?: "calibration failed"
                                )
                            )
                        }
                }
            }
        }
    }

    private fun runZeroView() {
        lifecycleScope.launch {
            trackingManager.zeroView()
                .onFailure { error ->
                    errors.show(
                        getString(
                            R.string.tracking_action_failed,
                            error.message ?: "zero view failed"
                        )
                    )
                }
        }
    }

    private fun syncExternalPresentation() {
        val display = displayController.currentPresentationDisplay()
        if (display == null) {
            if (externalPresentation?.isShowing != true) {
                dismissExternalPresentation()
            }
            updateRouting()
            return
        }

        val currentSignature = display.toSignature()
        val activePresentation = externalPresentation
        if (
            activePresentation != null &&
            activePresentation.display.displayId == display.displayId &&
            externalPresentationModeSignature == currentSignature &&
            externalSurface != null
        ) {
            return
        }

        dismissExternalPresentation()
        val token = ++externalPresentationToken
        runCatching {
            ExternalGlPresentation(
                context = this,
                display = display,
                listener = object : ExternalGlPresentation.Listener {
                    override fun onVideoSurfaceReady(surface: Surface) {
                        runOnUiThread {
                            if (token != externalPresentationToken) {
                                return@runOnUiThread
                            }
                            externalSurface = surface
                            updateRouting()
                        }
                    }

                    override fun onVideoSurfaceDestroyed() {
                        runOnUiThread {
                            if (token != externalPresentationToken) {
                                return@runOnUiThread
                            }
                            onExternalSurfaceDestroyed()
                        }
                    }
                }
            ).also {
                if (token != externalPresentationToken) {
                    it.dismiss()
                    return@also
                }
                externalPresentation = it
                externalPresentationModeSignature = currentSignature
                it.setRenderMode(renderMode)
                it.setPose(latestPose)
                it.show()
            }
        }.onFailure {
            if (token != externalPresentationToken) {
                return@onFailure
            }
            errors.show("Failed to start external presentation: ${it.message ?: "unknown error"}")
            externalPresentation = null
            externalSurface = null
            externalPresentationModeSignature = null
            updateRouting()
        }
    }

    private fun dismissExternalPresentation() {
        externalPresentationToken += 1L
        externalPresentation?.dismiss()
        externalPresentation = null
        externalSurface = null
        externalPresentationModeSignature = null
    }

    private fun onInternalSurfaceReady(surface: Surface) {
        internalSurface = surface
        updateRouting()
    }

    private fun onInternalSurfaceDestroyed() {
        val destroyedSurface = internalSurface
        if (destroyedSurface != null && activeSurface === destroyedSurface) {
            playbackSession.clearSurface(destroyedSurface)
            activeSurface = null
            activeRoute = ActiveRoute.NONE
        }
        internalSurface = null
        updateRouting()
    }

    private fun onExternalSurfaceDestroyed() {
        val destroyedSurface = externalSurface
        if (destroyedSurface != null && activeSurface === destroyedSurface) {
            playbackSession.clearSurface(destroyedSurface)
            activeSurface = null
            activeRoute = ActiveRoute.NONE
        }
        externalSurface = null
        updateRouting()
    }

    private fun updateRouting() {
        val snapshot = DisplayRouteSnapshot(
            externalDisplayId = displayController.currentPresentationDisplay()?.displayId
                ?: externalPresentation?.display?.displayId,
            externalSurfaceReady = externalSurface != null,
            activeRoute = activeRoute,
            activeSurfaceBound = activeSurface != null
        )
        val decision = routeStateMachine.decide(snapshot)
        routeState = decision.state
        var externalSurfaceRebound = false

        when (decision.target) {
            RouteTarget.EXTERNAL -> {
                externalSurfaceRebound = bindSurface(externalSurface, ActiveRoute.EXTERNAL)
            }
            RouteTarget.HOLD_CURRENT -> Unit
            RouteTarget.NONE -> {
                clearActiveSurface()
                playbackSession.pauseForInterruption()
            }
        }
        if (routeState is DisplayRouteState.ExternalActive) {
            if (externalSurfaceRebound) {
                playbackSession.showPausedFrameIfExpected()
            }
            playbackSession.resumeIfExpected()
        }

        renderRouteStatus()
        updateDiagnostics()
    }

    private fun bindSurface(surface: Surface?, route: ActiveRoute): Boolean {
        if (surface == null) {
            clearActiveSurface()
            return false
        }
        if (activeSurface === surface && activeRoute == route) {
            return false
        }

        val previousSurface = activeSurface
        if (previousSurface != null && previousSurface !== surface) {
            playbackSession.clearSurface(previousSurface)
        }

        playbackSession.bindSurface(surface)
        activeSurface = surface
        activeRoute = route
        return true
    }

    private fun clearActiveSurface() {
        val previousSurface = activeSurface
        if (previousSurface != null) {
            playbackSession.clearSurface(previousSurface)
        }
        activeSurface = null
        activeRoute = ActiveRoute.NONE
    }

    private fun renderRouteStatus() {
        val summary = when (val current = routeState) {
            DisplayRouteState.NoOutput -> getString(R.string.route_state_no_output)
            is DisplayRouteState.ExternalPending -> getString(
                R.string.route_state_external_pending,
                current.displayId
            )
            is DisplayRouteState.ExternalActive -> getString(
                R.string.route_state_external_active,
                current.displayId
            )
        }
        binding.routeStatusText.text = getString(R.string.route_status_template, summary)
        binding.videoSurface.visibility = View.INVISIBLE
    }

    private fun updateDiagnostics() {
        val mode = displayController.currentPhysicalMode()
        val displaySummary = if (mode == null) {
            "internal-only [${routeState.asDebugLabel()}]"
        } else {
            "${mode.width}x${mode.height}@${mode.refreshRateHz} [${routeState.asDebugLabel()}]"
        }
        diagnosticsOverlay.update(
            DiagnosticsState(
                displaySummary = displaySummary,
                trackingSummary = trackingSummary,
                decoderSummary = decoderSummary,
                droppedFrames = 0
            )
        )
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

private fun android.view.Display.toSignature(): DisplayModeSignature {
    val mode = this.mode
    return DisplayModeSignature(
        displayId = displayId,
        modeId = mode.modeId,
        width = mode.physicalWidth,
        height = mode.physicalHeight
    )
}

private const val TOUCH_RADIANS_PER_PIXEL = 0.0035f
private const val MAX_MANUAL_YAW_RAD = 3.1415927f
private const val MAX_MANUAL_PITCH_RAD = 1.2f
private const val EXTERNAL_RECONNECT_WATCHDOG_MS = 500L
