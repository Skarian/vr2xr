package com.vr2xr.app

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.vr2xr.R
import com.vr2xr.databinding.ActivityTrackingSetupBinding
import com.vr2xr.source.SourceDescriptor
import io.onepro.xr.XrSessionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class TrackingSetupActivity : AppCompatActivity() {
    private lateinit var binding: ActivityTrackingSetupBinding
    private val uiScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val errors by lazy { ErrorUiController(this) }
    private val trackingManager by lazy { (application as Vr2xrApplication).trackingSessionManager }

    private var currentSessionState: XrSessionState = XrSessionState.Idle
    private var zeroViewApplied = false
    private var sessionStateJob: Job? = null
    private var actionJob: Job? = null

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
        binding.zeroViewButton.setOnClickListener { runZeroView() }
        binding.continueButton.setOnClickListener { continueToPlayer() }
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
        if (currentSessionState is XrSessionState.Connecting || currentSessionState is XrSessionState.Calibrating) {
            return
        }
        actionJob?.cancel()
        actionJob = uiScope.launch {
            if (currentSessionState is XrSessionState.Streaming) {
                trackingManager.recalibrate()
                    .onFailure { error ->
                        errors.show(getString(R.string.tracking_action_failed, error.message ?: "recalibration failed"))
                    }
            } else {
                trackingManager.runCalibration()
                    .onFailure { error ->
                        errors.show(getString(R.string.tracking_action_failed, error.message ?: "calibration failed"))
                    }
            }
        }
    }

    private fun runZeroView() {
        actionJob?.cancel()
        actionJob = uiScope.launch {
            trackingManager.zeroView()
                .onSuccess {
                    zeroViewApplied = true
                    binding.statusText.text = getString(R.string.tracking_setup_zero_done)
                    binding.continueButton.isEnabled = true
                }
                .onFailure { error ->
                    errors.show(getString(R.string.tracking_action_failed, error.message ?: "zero view failed"))
                }
        }
    }

    private fun continueToPlayer() {
        val resolvedSource = source ?: return
        startActivity(
            Intent(this, PlayerActivity::class.java)
                .putExtra(PlayerActivity.EXTRA_SOURCE, resolvedSource)
        )
        finish()
    }

    private fun renderSessionState(state: XrSessionState) {
        when (state) {
            XrSessionState.Idle,
            XrSessionState.Stopped -> {
                binding.instructionText.text = getString(R.string.tracking_setup_step1)
                binding.statusText.text = getString(R.string.tracking_setup_idle)
                binding.runCalibrationButton.isEnabled = true
                binding.zeroViewButton.isEnabled = false
                binding.continueButton.isEnabled = false
                zeroViewApplied = false
            }

            XrSessionState.Connecting -> {
                binding.instructionText.text = getString(R.string.tracking_setup_step1)
                binding.statusText.text = getString(R.string.tracking_setup_connecting)
                binding.runCalibrationButton.isEnabled = false
                binding.zeroViewButton.isEnabled = false
                binding.continueButton.isEnabled = false
            }

            is XrSessionState.Calibrating -> {
                val target = state.calibrationTarget.coerceAtLeast(1)
                binding.instructionText.text = getString(R.string.tracking_setup_step1)
                binding.statusText.text = getString(
                    R.string.tracking_setup_calibrating,
                    state.calibrationSampleCount,
                    target
                )
                binding.runCalibrationButton.isEnabled = false
                binding.zeroViewButton.isEnabled = false
                binding.continueButton.isEnabled = false
            }

            is XrSessionState.Streaming -> {
                binding.instructionText.text = getString(R.string.tracking_setup_step2)
                binding.statusText.text = getString(R.string.tracking_setup_streaming)
                binding.runCalibrationButton.isEnabled = true
                binding.zeroViewButton.isEnabled = true
                binding.continueButton.isEnabled = zeroViewApplied
            }

            is XrSessionState.Error -> {
                binding.instructionText.text = getString(R.string.tracking_setup_step1)
                binding.statusText.text = getString(R.string.tracking_setup_error, state.message)
                binding.runCalibrationButton.isEnabled = true
                binding.zeroViewButton.isEnabled = false
                binding.continueButton.isEnabled = false
                zeroViewApplied = false
            }
        }
    }
}
