package com.vr2xr.app

import android.content.ComponentName
import android.content.Intent
import android.opengl.GLSurfaceView
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.MotionEvent
import android.view.Surface
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.slider.Slider
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.common.util.concurrent.ListenableFuture
import com.vr2xr.R
import com.vr2xr.databinding.ActivityPlayerBinding
import com.vr2xr.databinding.DialogGlassesSettingsBinding
import com.vr2xr.databinding.DialogProjectionSettingsBinding
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
import com.vr2xr.tracking.computeTouchpadAutoDragDelta
import com.vr2xr.tracking.OneXrTrackingSessionManager
import com.vr2xr.tracking.PoseState
import com.vr2xr.tracking.RuntimePoseController
import com.vr2xr.tracking.TOUCHPAD_DRAG_FULL_TRAVEL_RADIANS
import io.onexr.XrBiasState
import io.onexr.XrSessionState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

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
    private val runtimePoseController = RuntimePoseController(initialSensitivity = DEFAULT_IMU_SENSITIVITY)
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
    private var mediaController: MediaController? = null
    private var playbackProgressJob: Job? = null
    private var touchpadAutoDragJob: Job? = null
    private var isTimelineScrubbing = false
    private var pendingTimelinePositionMs = 0L
    private var selectedProjectionIndex = 0
    private var lastTouchpadX = 0f
    private var lastTouchpadY = 0f
    private var touchpadNormalizedX = 0f
    private var touchpadNormalizedY = 0f
    private var launchRequest = PlayerLaunchRequest(
        source = null,
        resumeExisting = false
    )

    private val mediaControllerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            updatePlayPauseButton()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            refreshTimelineUi()
            updatePlayPauseButton()
        }

        override fun onEvents(player: Player, events: Player.Events) {
            if (
                events.contains(Player.EVENT_POSITION_DISCONTINUITY) ||
                events.contains(Player.EVENT_TIMELINE_CHANGED) ||
                events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)
            ) {
                refreshTimelineUi()
            }
        }
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

        launchRequest = parseLaunchRequest(intent)
        val resolvedSource = launchRequest.source
        if (resolvedSource == null) {
            finish()
            return
        }

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
        applyRuntimePose(runtimePoseController.currentPose())

        setupControls()
        connectMediaController()

        val currentSessionSource = playbackSession.state.value.source
        val shouldForceReset = shouldForceSourceReset(
            hasSavedInstanceState = savedInstanceState != null,
            resumeExisting = launchRequest.resumeExisting,
            currentSourceNormalized = currentSessionSource?.normalized,
            requestedSourceNormalized = resolvedSource.normalized
        )
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
        stopTouchpadAutoDrag()
        stopPlaybackProgressUpdates()
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
        stopTouchpadAutoDrag()
        clearActiveSurface(force = true)
        dismissExternalPresentation()
        releaseMediaController()
        if (::internalRenderer.isInitialized) {
            internalRenderer.release()
        }
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        launchRequest = parseLaunchRequest(intent)
        val resolvedSource = launchRequest.source ?: return
        if (!::playbackSession.isInitialized) {
            return
        }
        val currentSessionSource = playbackSession.state.value.source
        val shouldForceReset = shouldForceSourceReset(
            hasSavedInstanceState = false,
            resumeExisting = launchRequest.resumeExisting,
            currentSourceNormalized = currentSessionSource?.normalized,
            requestedSourceNormalized = resolvedSource.normalized
        )
        playbackSession.attachSource(resolvedSource, forceReset = shouldForceReset)
    }

    companion object {
        const val EXTRA_SOURCE = "extra_source"
        const val EXTRA_RESUME_EXISTING = "extra_resume_existing"
    }

    private fun parseLaunchRequest(intent: Intent?): PlayerLaunchRequest {
        if (intent == null) {
            return PlayerLaunchRequest(source = null, resumeExisting = false)
        }
        val source = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_SOURCE, SourceDescriptor::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_SOURCE)
        }
        return PlayerLaunchRequest(
            source = source,
            resumeExisting = intent.getBooleanExtra(EXTRA_RESUME_EXISTING, false)
        )
    }

    private fun connectMediaController() {
        if (mediaControllerFuture != null) {
            return
        }
        val token = SessionToken(this, ComponentName(this, VrPlaybackService::class.java))
        val connectionHints = Bundle().apply {
            putBoolean(VrPlaybackService.CONTROLLER_HINT_ALLOW_APP_PLAYBACK, true)
        }
        val future = MediaController.Builder(this, token)
            .setConnectionHints(connectionHints)
            .buildAsync()
        mediaControllerFuture = future
        future.addListener(
            {
                if (mediaControllerFuture !== future) {
                    MediaController.releaseFuture(future)
                    return@addListener
                }
                runCatching { future.get() }
                    .onSuccess { controller ->
                        mediaController?.removeListener(mediaControllerListener)
                        mediaController = controller
                        controller.addListener(mediaControllerListener)
                        refreshTimelineUi()
                        updatePlayPauseButton()
                        startPlaybackProgressUpdates()
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
        mediaController?.removeListener(mediaControllerListener)
        mediaController = null
        stopPlaybackProgressUpdates()
        val future = mediaControllerFuture ?: return
        mediaControllerFuture = null
        MediaController.releaseFuture(future)
    }

    private fun setupControls() {
        binding.glassesSettingsButton.setOnClickListener { showGlassesSettingsPopup() }
        binding.projectionSettingsButton.setOnClickListener { showProjectionSettingsPopup() }
        binding.seekBackButton.setOnClickListener { seekBy(-SEEK_INTERVAL_MS) }
        binding.playPauseButton.setOnClickListener { togglePlayPause() }
        binding.seekForwardButton.setOnClickListener { seekBy(SEEK_INTERVAL_MS) }
        binding.recenterButton.setOnClickListener { runRecenter() }
        setupTimeline()
        setupTouchpad()
        refreshTimelineUi()
        updatePlayPauseButton()
    }

    private fun setupTimeline() {
        binding.timelineSlider.clearOnChangeListeners()
        binding.timelineSlider.clearOnSliderTouchListeners()
        binding.timelineSlider.addOnChangeListener { _, value, fromUser ->
            if (!fromUser) {
                return@addOnChangeListener
            }
            pendingTimelinePositionMs = value.toLong()
            binding.currentTimeText.text = formatPlaybackTime(pendingTimelinePositionMs)
        }
        binding.timelineSlider.addOnSliderTouchListener(
            object : com.google.android.material.slider.Slider.OnSliderTouchListener {
                override fun onStartTrackingTouch(slider: com.google.android.material.slider.Slider) {
                    isTimelineScrubbing = true
                    pendingTimelinePositionMs = slider.value.toLong()
                    binding.currentTimeText.text = formatPlaybackTime(pendingTimelinePositionMs)
                }

                override fun onStopTrackingTouch(slider: com.google.android.material.slider.Slider) {
                    pendingTimelinePositionMs = slider.value.toLong()
                    isTimelineScrubbing = false
                    seekToTimelinePosition()
                }
            }
        )
    }

    private fun setupTouchpad() {
        binding.touchpadArea.post {
            touchpadNormalizedX = 0f
            touchpadNormalizedY = 0f
            resetTouchpadIndicator(animate = false)
        }
        binding.touchpadArea.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    lastTouchpadX = event.x
                    lastTouchpadY = event.y
                    moveTouchpadIndicator(event.x, event.y)
                    startTouchpadAutoDrag()
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    applyTouchpadDragDelta(
                        deltaX = event.x - lastTouchpadX,
                        deltaY = event.y - lastTouchpadY
                    )
                    lastTouchpadX = event.x
                    lastTouchpadY = event.y
                    moveTouchpadIndicator(event.x, event.y)
                    true
                }

                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {
                    stopTouchpadAutoDrag()
                    applyRuntimePose(runtimePoseController.commitTouchpadBias())
                    touchpadNormalizedX = 0f
                    touchpadNormalizedY = 0f
                    resetTouchpadIndicator(animate = true)
                    true
                }

                else -> false
            }
        }
    }

    private fun moveTouchpadIndicator(rawX: Float, rawY: Float) {
        val area = binding.touchpadArea
        val indicator = binding.touchpadIndicator
        val maxOffsetX = ((area.width - indicator.width) / 2f).coerceAtLeast(0f)
        val maxOffsetY = ((area.height - indicator.height) / 2f).coerceAtLeast(0f)
        val centeredX = (rawX - area.width / 2f).coerceIn(-maxOffsetX, maxOffsetX)
        val centeredY = (rawY - area.height / 2f).coerceIn(-maxOffsetY, maxOffsetY)
        indicator.animate().cancel()
        indicator.translationX = centeredX
        indicator.translationY = centeredY
        indicator.alpha = 1f
        touchpadNormalizedX = if (maxOffsetX > 0f) centeredX / maxOffsetX else 0f
        touchpadNormalizedY = if (maxOffsetY > 0f) centeredY / maxOffsetY else 0f
    }

    private fun applyTouchpadDragDelta(deltaX: Float, deltaY: Float) {
        val area = binding.touchpadArea
        val indicator = binding.touchpadIndicator
        val maxOffsetX = ((area.width - indicator.width) / 2f).coerceAtLeast(0f)
        val maxOffsetY = ((area.height - indicator.height) / 2f).coerceAtLeast(0f)
        if (maxOffsetX <= 0f || maxOffsetY <= 0f) {
            return
        }
        val yawDelta = (deltaX / maxOffsetX) * TOUCHPAD_DRAG_FULL_TRAVEL_RADIANS
        val pitchDelta = -(deltaY / maxOffsetY) * TOUCHPAD_DRAG_FULL_TRAVEL_RADIANS
        applyRuntimePose(runtimePoseController.applyTouchpadBiasDelta(yawDelta, pitchDelta))
    }

    private fun startTouchpadAutoDrag() {
        if (touchpadAutoDragJob?.isActive == true) {
            return
        }
        touchpadAutoDragJob = lifecycleScope.launch {
            while (isActive) {
                applyTouchpadAutoDragStep()
                delay(TOUCHPAD_AUTO_DRAG_INTERVAL_MS)
            }
        }
    }

    private fun stopTouchpadAutoDrag() {
        touchpadAutoDragJob?.cancel()
        touchpadAutoDragJob = null
    }

    private fun applyTouchpadAutoDragStep() {
        val delta = computeTouchpadAutoDragDelta(
            normalizedX = touchpadNormalizedX,
            normalizedY = touchpadNormalizedY,
            intervalMs = TOUCHPAD_AUTO_DRAG_INTERVAL_MS,
            edgeThreshold = TOUCHPAD_AUTO_DRAG_EDGE_THRESHOLD,
            radiansPerSecond = TOUCHPAD_AUTO_DRAG_RADIANS_PER_SECOND
        )
        if (delta.yawDeltaRad == 0f && delta.pitchDeltaRad == 0f) {
            return
        }
        applyRuntimePose(
            runtimePoseController.applyTouchpadBiasDelta(
                yawDeltaRad = delta.yawDeltaRad,
                pitchDeltaRad = delta.pitchDeltaRad
            )
        )
    }

    private fun resetTouchpadIndicator(animate: Boolean) {
        val indicator = binding.touchpadIndicator
        indicator.animate().cancel()
        if (animate) {
            indicator.animate()
                .translationX(0f)
                .translationY(0f)
                .alpha(0.78f)
                .setDuration(120L)
                .start()
            return
        }
        indicator.translationX = 0f
        indicator.translationY = 0f
        indicator.alpha = 0.78f
    }

    private fun seekBy(deltaMs: Long) {
        val controller = mediaController ?: return
        val durationMs = controller.duration.takeIf { it != C.TIME_UNSET && it > 0L }
        val target = (controller.currentPosition + deltaMs).coerceAtLeast(0L)
        val boundedTarget = if (durationMs != null) target.coerceAtMost(durationMs) else target
        controller.seekTo(boundedTarget)
        refreshTimelineUi()
    }

    private fun togglePlayPause() {
        val controller = mediaController ?: return
        if (controller.isPlaying) {
            controller.pause()
        } else {
            controller.play()
        }
        updatePlayPauseButton()
    }

    private fun seekToTimelinePosition() {
        val controller = mediaController ?: return
        val durationMs = controller.duration.takeIf { it != C.TIME_UNSET && it > 0L }
        val target = if (durationMs != null) {
            pendingTimelinePositionMs.coerceIn(0L, durationMs)
        } else {
            pendingTimelinePositionMs.coerceAtLeast(0L)
        }
        controller.seekTo(target)
        refreshTimelineUi()
    }

    private fun refreshTimelineUi() {
        val controller = mediaController
        if (controller == null) {
            binding.timelineSlider.isEnabled = false
            if (binding.timelineSlider.valueTo != 1f) {
                binding.timelineSlider.valueTo = 1f
            }
            if (!isTimelineScrubbing) {
                binding.timelineSlider.value = 0f
            }
            binding.currentTimeText.text = getString(R.string.player_default_time)
            binding.durationTimeText.text = getString(R.string.player_default_time)
            return
        }

        val durationMs = controller.duration.takeIf { it != C.TIME_UNSET && it > 0L } ?: 0L
        val safeDurationMs = durationMs.coerceAtLeast(1L)
        val currentPositionMsRaw = controller.currentPosition.coerceAtLeast(0L)
        val currentPositionMs = if (durationMs > 0L) {
            currentPositionMsRaw.coerceAtMost(durationMs)
        } else {
            currentPositionMsRaw
        }
        if (binding.timelineSlider.valueTo != safeDurationMs.toFloat()) {
            binding.timelineSlider.valueTo = safeDurationMs.toFloat()
        }
        binding.timelineSlider.isEnabled = durationMs > 0L
        if (!isTimelineScrubbing) {
            pendingTimelinePositionMs = currentPositionMs
            val sliderValue = currentPositionMs.coerceIn(0L, safeDurationMs).toFloat()
            if (binding.timelineSlider.value != sliderValue) {
                binding.timelineSlider.value = sliderValue
            }
        }
        val displayedPositionMs = if (isTimelineScrubbing) pendingTimelinePositionMs else currentPositionMs
        binding.currentTimeText.text = formatPlaybackTime(displayedPositionMs)
        binding.durationTimeText.text = formatPlaybackTime(durationMs)
    }

    private fun updatePlayPauseButton() {
        val isPlaying = mediaController?.isPlaying == true
        val iconRes = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        val contentDescription = if (isPlaying) {
            getString(R.string.player_pause_content_description)
        } else {
            getString(R.string.player_play_content_description)
        }
        binding.playPauseButton.setIconResource(iconRes)
        binding.playPauseButton.contentDescription = contentDescription
    }

    private fun startPlaybackProgressUpdates() {
        if (playbackProgressJob?.isActive == true) {
            return
        }
        playbackProgressJob = lifecycleScope.launch {
            while (isActive) {
                refreshTimelineUi()
                delay(PLAYBACK_PROGRESS_UPDATE_MS)
            }
        }
    }

    private fun stopPlaybackProgressUpdates() {
        playbackProgressJob?.cancel()
        playbackProgressJob = null
    }

    private fun showGlassesSettingsPopup() {
        val dialogBinding = DialogGlassesSettingsBinding.inflate(layoutInflater)
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.glasses_settings_title)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.player_settings_close, null)
            .create()

        bindImuSensitivitySlider(
            slider = dialogBinding.imuSensitivitySlider,
            valueLabel = dialogBinding.imuSensitivityValueText
        )
        bindImuTrackingToggle(
            toggle = dialogBinding.imuTrackingSwitch,
            row = dialogBinding.imuTrackingRow
        )

        dialogBinding.runCalibrationButton.setOnClickListener {
            runCalibration()
            dialog.dismiss()
        }

        dialog.show()
        applyPlayerSettingsDialogStyle(dialog)
    }

    private fun bindImuSensitivitySlider(slider: Slider, valueLabel: TextView) {
        slider.clearOnChangeListeners()
        slider.clearOnSliderTouchListeners()
        slider.value = runtimePoseController.imuSensitivity()
        valueLabel.text = formatImuSensitivityValue(runtimePoseController.imuSensitivity())
        slider.addOnChangeListener { _, value, fromUser ->
            valueLabel.text = formatImuSensitivityValue(value)
            if (!fromUser) {
                return@addOnChangeListener
            }
            applyRuntimePose(runtimePoseController.setImuSensitivity(value))
        }
    }

    private fun bindImuTrackingToggle(
        toggle: SwitchMaterial,
        row: View
    ) {
        toggle.setOnCheckedChangeListener(null)
        toggle.isChecked = runtimePoseController.isImuTrackingEnabled()
        row.setOnClickListener { toggle.toggle() }
        toggle.setOnCheckedChangeListener { _, isChecked ->
            applyRuntimePose(runtimePoseController.setImuTrackingEnabled(isChecked))
        }
    }

    private fun showProjectionSettingsPopup() {
        val dialogBinding = DialogProjectionSettingsBinding.inflate(layoutInflater)
        dialogBinding.projectionHalfEquirectangularOption.isChecked = selectedProjectionIndex == 0
        dialogBinding.projectionOptionsGroup.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId == R.id.projectionHalfEquirectangularOption) {
                selectedProjectionIndex = 0
            }
        }

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.projection_settings_title)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.player_settings_close, null)
            .create()
        dialog.show()
        applyPlayerSettingsDialogStyle(dialog)
    }

    private fun applyPlayerSettingsDialogStyle(dialog: AlertDialog) {
        dialog.window?.setBackgroundDrawable(
            ContextCompat.getDrawable(this, R.drawable.bg_player_settings_dialog)
        )
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.let { closeButton ->
            closeButton.isAllCaps = false
            closeButton.setTextColor(
                ContextCompat.getColor(this, R.color.player_dialog_close_text)
            )
        }
    }

    private fun formatImuSensitivityValue(value: Float): String {
        return getString(R.string.imu_sensitivity_value_format, value)
    }

    private fun formatPlaybackTime(positionMs: Long): String {
        val totalSeconds = (positionMs.coerceAtLeast(0L) / 1000L).toInt()
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
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
                applyRuntimePose(runtimePoseController.onTrackingPoseUpdated(pose))
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

    private fun applyRuntimePose(pose: PoseState) {
        latestPose = pose
        applyPoseToRenderers(pose)
    }

    private fun runRecenter() {
        if (latestSessionState !is XrSessionState.Streaming) {
            errors.show(getString(R.string.tracking_recenter_requires_streaming))
            return
        }
        applyRuntimePose(runtimePoseController.resetTouchpadBias())
        resetTouchpadIndicator(animate = false)
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

internal data class PlayerLaunchRequest(
    val source: SourceDescriptor?,
    val resumeExisting: Boolean
)

internal fun shouldForceSourceReset(
    hasSavedInstanceState: Boolean,
    resumeExisting: Boolean,
    currentSourceNormalized: String?,
    requestedSourceNormalized: String
): Boolean {
    return !hasSavedInstanceState &&
        !(resumeExisting && currentSourceNormalized == requestedSourceNormalized)
}

private const val EXTERNAL_RECONNECT_WATCHDOG_MS = 500L
private const val PAUSED_FRAME_REFRESH_RETRY_MS = 250L
private const val PAUSED_FRAME_VISIBILITY_TIMEOUT_MS = 2000L
private const val SEEK_INTERVAL_MS = 15_000L
private const val PLAYBACK_PROGRESS_UPDATE_MS = 300L
private const val DEFAULT_IMU_SENSITIVITY = 0.9f
private const val TOUCHPAD_AUTO_DRAG_INTERVAL_MS = 16L
private const val TOUCHPAD_AUTO_DRAG_EDGE_THRESHOLD = 0.9f
private const val TOUCHPAD_AUTO_DRAG_RADIANS_PER_SECOND = 0.24f
