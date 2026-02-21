package com.vr2xr.app

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import com.vr2xr.R
import com.vr2xr.databinding.ActivityTrackingSetupBinding
import com.vr2xr.source.SourceDescriptor
import io.onexr.XrSessionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class TrackingSetupActivity : AppCompatActivity() {
    private lateinit var binding: ActivityTrackingSetupBinding
    private val uiScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val errors by lazy { ErrorUiController(this) }
    private val trackingManager by lazy { (application as Vr2xrApplication).trackingSessionManager }

    private var currentSessionState: XrSessionState = XrSessionState.Idle
    private var calibrationStartedByUser = false
    private var calibrationProgressObserved = false
    private var completionSequenceStarted = false
    private var readyPageLaunched = false
    private var sessionStateJob: Job? = null
    private var actionJob: Job? = null
    private var completionJob: Job? = null

    private val source: SourceDescriptor? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(PlayerActivity.EXTRA_SOURCE, SourceDescriptor::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(PlayerActivity.EXTRA_SOURCE)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTrackingSetupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (source == null) {
            finish()
            return
        }

        binding.runCalibrationButton.setOnClickListener { runCalibration() }
        renderSessionState(XrSessionState.Idle)
    }

    override fun onStart() {
        super.onStart()
        attachCollectors()
    }

    override fun onStop() {
        super.onStop()
        sessionStateJob?.cancel()
        sessionStateJob = null
        actionJob?.cancel()
        actionJob = null
        completionJob?.cancel()
        completionJob = null
        if (!readyPageLaunched) {
            completionSequenceStarted = false
        }
    }

    override fun onDestroy() {
        uiScope.cancel()
        super.onDestroy()
    }

    private fun attachCollectors() {
        sessionStateJob?.cancel()
        sessionStateJob = uiScope.launch {
            trackingManager.sessionState.collect { state ->
                currentSessionState = state
                renderSessionState(state)
            }
        }
    }

    private fun runCalibration() {
        if (
            currentSessionState is XrSessionState.Connecting ||
            currentSessionState is XrSessionState.Calibrating ||
            completionSequenceStarted
        ) {
            return
        }
        calibrationStartedByUser = true
        calibrationProgressObserved = false
        completionSequenceStarted = false
        readyPageLaunched = false
        setCalibrationButtonCalibrating()
        actionJob?.cancel()
        actionJob = uiScope.launch {
            if (currentSessionState is XrSessionState.Streaming) {
                trackingManager.recalibrate()
                    .onFailure { error ->
                        calibrationStartedByUser = false
                        calibrationProgressObserved = false
                        errors.show(getString(R.string.tracking_action_failed, error.message ?: "recalibration failed"))
                        renderSessionState(currentSessionState)
                    }
            } else {
                trackingManager.runCalibration()
                    .onFailure { error ->
                        calibrationStartedByUser = false
                        calibrationProgressObserved = false
                        errors.show(getString(R.string.tracking_action_failed, error.message ?: "calibration failed"))
                        renderSessionState(currentSessionState)
                    }
            }
        }
    }

    private fun launchReadyStep() {
        val resolvedSource = source ?: return
        if (readyPageLaunched) {
            return
        }
        readyPageLaunched = true
        startActivity(
            Intent(this, TrackingReadyActivity::class.java)
                .putExtra(PlayerActivity.EXTRA_SOURCE, resolvedSource)
        )
        finish()
    }

    private fun renderSessionState(state: XrSessionState) {
        currentSessionState = state
        when (state) {
            XrSessionState.Idle,
            XrSessionState.Stopped -> {
                binding.instructionText.text = getString(R.string.tracking_setup_step1)
                binding.statusText.text = getString(R.string.tracking_setup_idle)
                setCalibrationButtonReady(enabled = true)
            }

            XrSessionState.Connecting -> {
                binding.instructionText.text = getString(R.string.tracking_setup_step1)
                binding.statusText.text = getString(R.string.tracking_setup_idle)
                if (calibrationStartedByUser) {
                    calibrationProgressObserved = true
                }
                setCalibrationButtonCalibrating()
            }

            is XrSessionState.Calibrating -> {
                binding.instructionText.text = getString(R.string.tracking_setup_step1)
                binding.statusText.text = getString(R.string.tracking_setup_idle)
                if (calibrationStartedByUser) {
                    calibrationProgressObserved = true
                }
                setCalibrationButtonCalibrating()
            }

            is XrSessionState.Streaming -> {
                binding.instructionText.text = getString(R.string.tracking_setup_step1)
                binding.statusText.text = getString(R.string.tracking_setup_idle)
                if (calibrationStartedByUser && calibrationProgressObserved) {
                    setCalibrationButtonComplete(animated = false)
                    startCompletionSequence()
                } else if (calibrationStartedByUser) {
                    setCalibrationButtonCalibrating()
                } else {
                    setCalibrationButtonReady(enabled = true)
                }
            }

            is XrSessionState.Error -> {
                binding.instructionText.text = getString(R.string.tracking_setup_step1)
                binding.statusText.text = getString(R.string.tracking_setup_error, state.message)
                calibrationStartedByUser = false
                calibrationProgressObserved = false
                completionSequenceStarted = false
                completionJob?.cancel()
                completionJob = null
                setCalibrationButtonReady(enabled = true)
            }
        }
    }

    private fun setCalibrationButtonReady(enabled: Boolean) {
        binding.runCalibrationButton.isEnabled = enabled
        binding.runCalibrationButton.text = getString(R.string.run_calibration)
        binding.calibrationProgressIndicator.visibility = View.GONE
        binding.calibrationCheckMark.visibility = View.GONE
    }

    private fun setCalibrationButtonCalibrating() {
        binding.runCalibrationButton.isEnabled = false
        binding.runCalibrationButton.text = getString(R.string.tracking_setup_calibrating_simple)
        binding.calibrationProgressIndicator.visibility = View.VISIBLE
        binding.calibrationCheckMark.visibility = View.GONE
    }

    private fun setCalibrationButtonComplete(animated: Boolean) {
        binding.runCalibrationButton.isEnabled = false
        binding.runCalibrationButton.text = getString(R.string.tracking_setup_calibration_complete_button)
        binding.calibrationProgressIndicator.visibility = View.GONE
        binding.calibrationCheckMark.visibility = View.VISIBLE
        if (animated) {
            animateCheckMark()
        } else {
            binding.calibrationCheckMark.alpha = 1f
            binding.calibrationCheckMark.scaleX = 1f
            binding.calibrationCheckMark.scaleY = 1f
        }
    }

    private fun animateCheckMark() {
        binding.calibrationCheckMark.alpha = 0f
        binding.calibrationCheckMark.scaleX = 0.4f
        binding.calibrationCheckMark.scaleY = 0.4f
        AnimatorSet().apply {
            duration = 420L
            interpolator = FastOutSlowInInterpolator()
            playTogether(
                ObjectAnimator.ofFloat(binding.calibrationCheckMark, View.ALPHA, 0f, 1f),
                ObjectAnimator.ofFloat(binding.calibrationCheckMark, View.SCALE_X, 0.4f, 1.15f, 1f),
                ObjectAnimator.ofFloat(binding.calibrationCheckMark, View.SCALE_Y, 0.4f, 1.15f, 1f)
            )
            start()
        }
    }

    private fun playIconCompletionAnimation() {
        val nudgeDistance = resources.getDimension(R.dimen.launcher_icon_nudge_distance)
        AnimatorSet().apply {
            duration = 460L
            interpolator = FastOutSlowInInterpolator()
            playTogether(
                ObjectAnimator.ofFloat(binding.setupGlassesIcon, View.TRANSLATION_Y, 0f, -nudgeDistance, 0f),
                ObjectAnimator.ofFloat(binding.setupGlassesIcon, View.SCALE_X, 1f, 1.14f, 1f),
                ObjectAnimator.ofFloat(binding.setupGlassesIcon, View.SCALE_Y, 1f, 1.14f, 1f)
            )
            start()
        }
    }

    private fun startCompletionSequence() {
        if (completionSequenceStarted || readyPageLaunched) {
            return
        }
        completionSequenceStarted = true
        setCalibrationButtonComplete(animated = true)
        playIconCompletionAnimation()
        completionJob?.cancel()
        completionJob = uiScope.launch {
            delay(1000L)
            if (
                calibrationStartedByUser &&
                calibrationProgressObserved &&
                currentSessionState is XrSessionState.Streaming
            ) {
                launchReadyStep()
            } else {
                completionSequenceStarted = false
                renderSessionState(currentSessionState)
            }
        }
    }
}
