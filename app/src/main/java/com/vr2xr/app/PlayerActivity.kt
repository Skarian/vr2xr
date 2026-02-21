package com.vr2xr.app

import android.content.ComponentName
import android.opengl.GLSurfaceView
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.Surface
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.vr2xr.R
import com.vr2xr.databinding.ActivityPlayerBinding
import com.vr2xr.diag.DiagnosticsOverlay
import com.vr2xr.diag.DiagnosticsState
import com.vr2xr.diag.PlaybackDiagnosticsConfig
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
import com.vr2xr.player.VrPlaybackService
import com.vr2xr.render.RenderMode
import com.vr2xr.render.VrSbsRenderer
import com.vr2xr.source.SourceDescriptor
import com.vr2xr.tracking.OneXrTrackingSessionManager
import com.vr2xr.tracking.PoseState
import io.onexr.XrBiasState
import io.onexr.XrSessionState
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
    private lateinit var trackingManager: OneXrTrackingSessionManager
    private lateinit var routeBinding: PlayerRouteBinding

    private val routeStateMachine = DisplayRouteStateMachine()
    private var routeState: DisplayRouteState = DisplayRouteState.NoOutput

    private var decoderSummary: String = "unknown"
    private var internalSurface: Surface? = null
    private var externalSurface: Surface? = null
    private var externalPresentation: ExternalGlPresentation? = null
    private var externalPresentationModeSignature: DisplayModeSignature? = null
    private var externalPresentationToken: Long = 0L
    private var latestPose: PoseState = PoseState()
    private var trackingSummary: String = ""

    private val renderMode = RenderMode()
    private val errors by lazy { ErrorUiController(this) }

    private var latestSessionState: XrSessionState = XrSessionState.Idle
    private var latestBiasState: XrBiasState = XrBiasState.Inactive

    private var trackingSessionStateJob: Job? = null
    private var trackingBiasStateJob: Job? = null
    private var trackingPoseJob: Job? = null
    private var playbackSessionStateJob: Job? = null
    private var externalReconnectWatchdogJob: Job? = null
    private var pausedFrameContinuityJob: Job? = null
    private var mediaControllerFuture: ListenableFuture<MediaController>? = null

    private val source: SourceDescriptor? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_SOURCE, SourceDescriptor::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_SOURCE)
        }
    }
    private val resumeExisting: Boolean by lazy {
        intent.getBooleanExtra(EXTRA_RESUME_EXISTING, false)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val app = application as Vr2xrApplication
        trackingManager = app.trackingSessionManager
        playbackSession = app.playbackSessionOwner
        routeBinding = PlayerRouteBinding(playbackSession)

        displayController = ExternalDisplayController(
            context = this,
            listener = object : ExternalDisplayController.Listener {
                override fun onModeChanged(mode: PhysicalDisplayMode?) {
                    runOnUiThread {
                        if (!canProcessRouting("display-mode-changed")) {
                            return@runOnUiThread
                        }
                        syncExternalPresentation()
                        updateRouting("display-mode-changed")
                    }
                }
            }
        )

        diagnosticsOverlay = DiagnosticsOverlay(this)
        binding.diagnosticsContainer.addView(diagnosticsOverlay)
        binding.diagnosticsContainer.visibility = View.GONE

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

        binding.transportControls.player = null
        binding.transportControls.show()
        connectMediaController()

        binding.recenterButton.setOnClickListener { recenterView() }
        binding.calibrateButton.setOnClickListener { runCalibration() }
        binding.zeroViewButton.setOnClickListener { runZeroView() }

        val currentSessionSource = playbackSession.state.value.source
        val shouldForceReset = savedInstanceState == null &&
            !(resumeExisting && currentSessionSource?.normalized == resolvedSource.normalized)
        playbackSession.attachSource(resolvedSource, forceReset = shouldForceReset)
        updateTrackingSummary()
        updateTrackingControls()
        renderRouteStatus()
        updateDiagnostics()
    }

    override fun onStart() {
        super.onStart()
        routeBinding.onStart()
        connectMediaController()
        displayController.start()
        binding.videoSurface.onResume()
        syncExternalPresentation()
        applyPoseToRenderers(latestPose)
        attachTrackingCollectors()
        attachPlaybackSessionCollector()
        updateRouting("activity-start")
        updateDiagnostics()
        startExternalReconnectWatchdog()
    }

    override fun onPause() {
        if (!isChangingConfigurations) {
            logRouting("onPause -> pauseForAppBackground")
            playbackSession.pauseForAppBackground()
        }
        super.onPause()
    }

    override fun onStop() {
        routeBinding.onTeardownBegin()
        displayController.stop()
        externalReconnectWatchdogJob?.cancel()
        externalReconnectWatchdogJob = null
        pausedFrameContinuityJob?.cancel()
        pausedFrameContinuityJob = null
        playbackSessionStateJob?.cancel()
        playbackSessionStateJob = null
        logRouting("onStop teardown -> clear/dismiss")
        clearActiveSurface(force = true)
        dismissExternalPresentation()
        if (::internalRenderer.isInitialized) {
            binding.videoSurface.onPause()
        }
        super.onStop()
        if (!isChangingConfigurations) {
            releaseMediaController()
        }
        trackingSessionStateJob?.cancel()
        trackingSessionStateJob = null
        trackingBiasStateJob?.cancel()
        trackingBiasStateJob = null
        trackingPoseJob?.cancel()
        trackingPoseJob = null
    }

    override fun onDestroy() {
        routeBinding.onTeardownBegin()
        clearActiveSurface(force = true)
        dismissExternalPresentation()
        releaseMediaController()
        if (::internalRenderer.isInitialized) {
            internalRenderer.release()
        }
        super.onDestroy()
    }

    companion object {
        const val EXTRA_SOURCE = "extra_source"
        const val EXTRA_RESUME_EXISTING = "extra_resume_existing"
    }

    private fun connectMediaController() {
        if (mediaControllerFuture != null) {
            return
        }
        val token = SessionToken(this, ComponentName(this, VrPlaybackService::class.java))
        val future = MediaController.Builder(this, token).buildAsync()
        mediaControllerFuture = future
        future.addListener(
            {
                if (mediaControllerFuture !== future) {
                    MediaController.releaseFuture(future)
                    return@addListener
                }
                runCatching { future.get() }
                    .onSuccess { controller ->
                        binding.transportControls.player = controller
                        binding.transportControls.show()
                    }
                    .onFailure { error ->
                        val detail = error.message ?: getString(R.string.error_unknown)
                        errors.show(getString(R.string.error_connect_playback_service, detail))
                    }
            },
            ContextCompat.getMainExecutor(this)
        )
    }

    private fun releaseMediaController() {
        binding.transportControls.player = null
        val future = mediaControllerFuture ?: return
        mediaControllerFuture = null
        MediaController.releaseFuture(future)
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
                if (!canProcessRouting("reconnect-watchdog-loop")) {
                    delay(EXTERNAL_RECONNECT_WATCHDOG_MS)
                    continue
                }
                if (routeState !is DisplayRouteState.ExternalActive) {
                    syncExternalPresentation()
                    updateRouting("reconnect-watchdog")
                }
                delay(EXTERNAL_RECONNECT_WATCHDOG_MS)
            }
        }
    }

    private fun attachPlaybackSessionCollector() {
        playbackSessionStateJob?.cancel()
        playbackSessionStateJob = lifecycleScope.launch {
            playbackSession.state.collect {
                maybeEnsurePausedFrameVisibility()
                updateDiagnostics()
            }
        }
    }

    private fun handleTrackingSessionState(state: XrSessionState) {
        latestSessionState = state
        updateTrackingControls()
        updateTrackingSummary()
        updateDiagnostics()
    }

    private fun updateTrackingControls() {
        val isStreaming = latestSessionState is XrSessionState.Streaming
        binding.zeroViewButton.isEnabled = isStreaming
        binding.recenterButton.isEnabled = isStreaming
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
        if (!canProcessRouting("sync-external-presentation")) {
            return
        }
        val display = displayController.currentPresentationDisplay()
        if (display == null) {
            if (externalPresentation?.isShowing != true) {
                dismissExternalPresentation()
            }
            updateRouting("no-external-display")
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
                            if (token != externalPresentationToken ||
                                !canProcessRouting("external-surface-ready")
                            ) {
                                return@runOnUiThread
                            }
                            externalSurface = surface
                            updateRouting("external-surface-ready")
                        }
                    }

                    override fun onVideoSurfaceDestroyed() {
                        runOnUiThread {
                            if (token != externalPresentationToken ||
                                !canProcessRouting("external-surface-destroyed")
                            ) {
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
            val detail = it.message ?: getString(R.string.error_unknown)
            errors.show(getString(R.string.error_start_external_presentation, detail))
            externalPresentation = null
            externalSurface = null
            externalPresentationModeSignature = null
            updateRouting("external-presentation-failure")
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
        if (!canProcessRouting("internal-surface-ready")) {
            return
        }
        updateRouting("internal-surface-ready")
    }

    private fun onInternalSurfaceDestroyed() {
        val destroyedSurface = internalSurface
        internalSurface = null
        routeBinding.onSurfaceDestroyed(
            surface = destroyedSurface,
            clearSessionSurface = canProcessRouting("internal-surface-destroyed"),
            log = ::logRouting
        )
        updateRouting("internal-surface-destroyed")
    }

    private fun onExternalSurfaceDestroyed() {
        val destroyedSurface = externalSurface
        externalSurface = null
        routeBinding.onSurfaceDestroyed(
            surface = destroyedSurface,
            clearSessionSurface = canProcessRouting("external-surface-destroyed"),
            log = ::logRouting
        )
        updateRouting("external-surface-destroyed")
    }

    private fun updateRouting(trigger: String) {
        if (!canProcessRouting("update-routing:$trigger")) {
            return
        }
        val snapshot = DisplayRouteSnapshot(
            externalDisplayId = displayController.currentPresentationDisplay()?.displayId
                ?: externalPresentation?.display?.displayId,
            externalSurfaceReady = externalSurface != null,
            activeRoute = routeBinding.activeRoute,
            activeSurfaceBound = routeBinding.activeSurface != null
        )
        val decision = routeStateMachine.decide(snapshot)
        logRouting(
            "updateRouting trigger=$trigger target=${decision.target} state=${decision.state.asDebugLabel()} " +
                "activeRoute=${routeBinding.activeRoute} activeSurface=${routeBinding.activeSurface != null} " +
                "externalSurface=${externalSurface != null}"
        )
        routeState = decision.state
        var externalSurfaceRebound = false

        when (decision.target) {
            RouteTarget.EXTERNAL -> {
                externalSurfaceRebound = bindSurface(externalSurface, ActiveRoute.EXTERNAL)
            }
            RouteTarget.HOLD_CURRENT -> Unit
            RouteTarget.NONE -> {
                playbackSession.pauseForInterruption()
                clearActiveSurface()
            }
        }
        if (routeState is DisplayRouteState.ExternalActive) {
            if (externalSurfaceRebound) {
                playbackSession.showPausedFrameIfExpected()
            }
            playbackSession.resumeIfExpected()
        }

        maybeEnsurePausedFrameVisibility()
        renderRouteStatus()
        updateDiagnostics()
    }

    private fun bindSurface(surface: Surface?, route: ActiveRoute): Boolean {
        return routeBinding.bindSurface(surface, route, ::logRouting)
    }

    private fun clearActiveSurface(force: Boolean = false) {
        routeBinding.clearActiveSurface(force = force, log = ::logRouting)
    }

    private fun maybeEnsurePausedFrameVisibility() {
        if (!canProcessRouting("paused-frame-ensure")) {
            pausedFrameContinuityJob?.cancel()
            pausedFrameContinuityJob = null
            return
        }
        val sessionState = playbackSession.state.value
        val shouldEnsure = routeState is DisplayRouteState.ExternalActive &&
            routeBinding.activeRoute == ActiveRoute.EXTERNAL &&
            routeBinding.activeSurface != null &&
            sessionState.pausedFrameVisibleExpected &&
            sessionState.awaitingFirstFrameAfterSurfaceBind
        if (!shouldEnsure) {
            pausedFrameContinuityJob?.cancel()
            pausedFrameContinuityJob = null
            return
        }
        if (pausedFrameContinuityJob?.isActive == true) {
            return
        }
        pausedFrameContinuityJob = lifecycleScope.launch {
            val deadlineMs = SystemClock.elapsedRealtime() + PAUSED_FRAME_VISIBILITY_TIMEOUT_MS
            while (isActive && SystemClock.elapsedRealtime() < deadlineMs) {
                val latestState = playbackSession.state.value
                val stillShouldEnsure = routeState is DisplayRouteState.ExternalActive &&
                    routeBinding.activeRoute == ActiveRoute.EXTERNAL &&
                    routeBinding.activeSurface != null &&
                    latestState.pausedFrameVisibleExpected &&
                    latestState.awaitingFirstFrameAfterSurfaceBind
                if (!stillShouldEnsure) {
                    break
                }
                playbackSession.showPausedFrameIfExpected()
                delay(PAUSED_FRAME_REFRESH_RETRY_MS)
            }
        }
    }

    private fun canProcessRouting(trigger: String): Boolean {
        return routeBinding.canProcess(trigger, ::logRouting)
    }

    private fun logRouting(message: String) {
        if (!PlaybackDiagnosticsConfig.isEnabled()) {
            return
        }
        Log.i(PlaybackDiagnosticsConfig.ROUTING_LOG_TAG, message)
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
        val diagnosticsEnabled = PlaybackDiagnosticsConfig.isEnabled()
        binding.diagnosticsContainer.visibility = if (diagnosticsEnabled) View.VISIBLE else View.GONE
        if (!diagnosticsEnabled) {
            return
        }
        val mode = displayController.currentPhysicalMode()
        val playbackState = playbackSession.state.value
        val displaySummary = if (mode == null) {
            "internal-only [${routeState.asDebugLabel()}]"
        } else {
            "${mode.width}x${mode.height}@${mode.refreshRateHz} [${routeState.asDebugLabel()}]"
        }
        val playbackSummary = buildString {
            append("expected=")
            append(playbackState.expectedPlayWhenReady)
            append(", actual=")
            append(playbackState.playWhenReady)
            append(", pause=")
            append(playbackState.pauseReason)
            append(", surface=")
            append(playbackState.hasBoundSurface)
            append(", pausedFrameExpected=")
            append(playbackState.pausedFrameVisibleExpected)
            append(", pausedFrameRefresh=")
            append(playbackState.lastPausedFrameRefreshResult)
            append("#")
            append(playbackState.pausedFrameRefreshCount)
            append(", firstFrameWaiting=")
            append(playbackState.awaitingFirstFrameAfterSurfaceBind)
            append(", firstFrameCount=")
            append(playbackState.renderedFirstFrameCount)
        }
        diagnosticsOverlay.update(
            DiagnosticsState(
                displaySummary = displaySummary,
                trackingSummary = trackingSummary,
                decoderSummary = decoderSummary,
                playbackSummary = playbackSummary
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
        if (latestSessionState !is XrSessionState.Streaming) {
            errors.show(getString(R.string.tracking_recenter_requires_streaming))
            return
        }
        runZeroView()
    }
}

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

private const val EXTERNAL_RECONNECT_WATCHDOG_MS = 500L
private const val PAUSED_FRAME_REFRESH_RETRY_MS = 250L
private const val PAUSED_FRAME_VISIBILITY_TIMEOUT_MS = 2000L
