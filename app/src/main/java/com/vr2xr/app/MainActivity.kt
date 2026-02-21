package com.vr2xr.app

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.lifecycle.lifecycleScope
import com.vr2xr.R
import com.vr2xr.databinding.ActivityMainBinding
import com.vr2xr.source.IntentIngestor
import com.vr2xr.source.SourceDescriptor
import com.vr2xr.source.SourceResolver
import com.vr2xr.tracking.OneXrConnectionProbe
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private enum class ConnectionStatusUi(
        val labelResId: Int,
        val indicatorColorResId: Int
    ) {
        CHECKING(R.string.xreal_status_checking, R.color.xreal_status_checking),
        CONNECTED(R.string.xreal_status_connected, R.color.xreal_status_connected),
        NOT_CONNECTED(R.string.xreal_status_not_connected, R.color.xreal_status_not_connected),
    }

    private lateinit var binding: ActivityMainBinding
    private val resolver = SourceResolver()
    private val errors by lazy { ErrorUiController(this) }
    private val trackingManager by lazy { (application as Vr2xrApplication).trackingSessionManager }
    private val playbackSession by lazy { (application as Vr2xrApplication).playbackSessionOwner }
    private var connectionProbeJob: Job? = null
    private var pendingLauncherResumeCheck = false
    private var currentConnectionStatus = ConnectionStatusUi.CHECKING

    private val openDocument = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) {
            setRuntimeStatus(getString(R.string.status_file_picker_canceled))
            return@registerForActivityResult
        }
        resolver.resolveUri(this, uri, persistPermission = true)
            .onSuccess(::handleResolvedSource)
            .onFailure { errors.show(it.message ?: getString(R.string.error_open_selected_file)) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.openFileButton.setOnClickListener {
            openDocument.launch(arrayOf("video/*"))
        }

        binding.openUrlButton.setOnClickListener {
            val raw = binding.urlInput.text?.toString().orEmpty()
            resolver.resolveUrl(raw)
                .onSuccess(::handleResolvedSource)
                .onFailure { errors.show(it.message ?: getString(R.string.error_invalid_url)) }
        }
        binding.requirementsHelpButton.setOnClickListener {
            showRequirementsModal()
        }

        renderConnectionStatusUi(ConnectionStatusUi.CHECKING)
        setRuntimeStatus(null)
        maybeHandleInboundIntent(intent)
        pendingLauncherResumeCheck = isLauncherIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        startConnectionProbeLoop()
        maybeResumeActivePlaybackSession()
    }

    override fun onStop() {
        super.onStop()
        connectionProbeJob?.cancel()
        connectionProbeJob = null
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        maybeHandleInboundIntent(intent)
        pendingLauncherResumeCheck = isLauncherIntent(intent)
    }

    private fun maybeHandleInboundIntent(intent: Intent?) {
        if (intent == null) return
        val ingestorResult = IntentIngestor(resolver).ingest(this, intent) ?: return
        ingestorResult
            .onSuccess(::handleResolvedSource)
            .onFailure { errors.show(it.message ?: getString(R.string.error_unsupported_launch_intent)) }
    }

    private fun handleResolvedSource(source: SourceDescriptor) {
        lifecycleScope.launch {
            setRuntimeStatus(getString(R.string.status_preparing_source, source.normalized))
            val probe = trackingManager.probeConnection()
            renderConnectionStatus(probe)
            if (probe.connected) {
                launchTrackingSetup(source)
            } else {
                setRuntimeStatus(getString(R.string.status_glasses_required))
                Toast.makeText(this@MainActivity, R.string.toast_glasses_required, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun launchPlayer(source: SourceDescriptor, resumeExisting: Boolean = false) {
        setRuntimeStatus(getString(R.string.status_launching_source, source.normalized))
        val intent = Intent(this, PlayerActivity::class.java)
            .putExtra(PlayerActivity.EXTRA_SOURCE, source)
            .putExtra(PlayerActivity.EXTRA_RESUME_EXISTING, resumeExisting)
        startActivity(intent)
    }

    private fun launchTrackingSetup(source: SourceDescriptor) {
        val intent = Intent(this, TrackingSetupActivity::class.java)
            .putExtra(PlayerActivity.EXTRA_SOURCE, source)
        startActivity(intent)
    }

    private fun startConnectionProbeLoop() {
        connectionProbeJob?.cancel()
        connectionProbeJob = lifecycleScope.launch {
            while (isActive) {
                val probe = trackingManager.probeConnection()
                renderConnectionStatus(probe)
                delay(2000L)
            }
        }
    }

    private fun maybeResumeActivePlaybackSession() {
        if (!pendingLauncherResumeCheck) {
            return
        }
        pendingLauncherResumeCheck = false
        val activeSource = playbackSession.state.value.source ?: return
        launchPlayer(activeSource, resumeExisting = true)
        finish()
    }

    private fun isLauncherIntent(intent: Intent?): Boolean {
        if (intent == null || intent.action != Intent.ACTION_MAIN) {
            return false
        }
        return intent.categories?.contains(Intent.CATEGORY_LAUNCHER) == true
    }

    private fun setRuntimeStatus(message: String?) {
        if (message.isNullOrBlank()) {
            binding.statusText.text = ""
            binding.statusText.visibility = View.GONE
            return
        }
        binding.statusText.text = message
        binding.statusText.visibility = View.VISIBLE
    }

    private fun showRequirementsModal() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.requirements_modal_title)
            .setMessage(R.string.requirements_modal_message)
            .setPositiveButton(R.string.requirements_modal_action, null)
            .show()
    }

    private fun renderConnectionStatusUi(nextStatus: ConnectionStatusUi) {
        val animateConnectedTransition =
            currentConnectionStatus == ConnectionStatusUi.NOT_CONNECTED &&
                nextStatus == ConnectionStatusUi.CONNECTED

        currentConnectionStatus = nextStatus
        binding.xrealStatusText.text = getString(nextStatus.labelResId)
        binding.xrealStatusDot.backgroundTintList = ColorStateList.valueOf(
            ContextCompat.getColor(this, nextStatus.indicatorColorResId)
        )

        if (animateConnectedTransition) {
            playConnectedMicroAnimation()
            return
        }
        resetStatusAnimations()
    }

    private fun resetStatusAnimations() {
        binding.xrealStatusDot.scaleX = 1f
        binding.xrealStatusDot.scaleY = 1f
        binding.xrealStatusDot.alpha = 1f
        binding.xrealStatusIcon.translationY = 0f
        binding.xrealStatusIcon.scaleX = 1f
        binding.xrealStatusIcon.scaleY = 1f
        binding.xrealStatusText.alpha = 1f
    }

    private fun playConnectedMicroAnimation() {
        val iconNudge = resources.getDimension(R.dimen.launcher_icon_nudge_distance)
        val dotScaleX = ObjectAnimator.ofFloat(binding.xrealStatusDot, View.SCALE_X, 1f, 1.65f, 1f)
        val dotScaleY = ObjectAnimator.ofFloat(binding.xrealStatusDot, View.SCALE_Y, 1f, 1.65f, 1f)
        val dotFade = ObjectAnimator.ofFloat(binding.xrealStatusDot, View.ALPHA, 0.5f, 1f)
        val textFade = ObjectAnimator.ofFloat(binding.xrealStatusText, View.ALPHA, 0.2f, 1f)
        val iconNudgeAnimator = ObjectAnimator.ofFloat(binding.xrealStatusIcon, View.TRANSLATION_Y, 0f, -iconNudge, 0f)
        val iconScaleX = ObjectAnimator.ofFloat(binding.xrealStatusIcon, View.SCALE_X, 1f, 1.1f, 1f)
        val iconScaleY = ObjectAnimator.ofFloat(binding.xrealStatusIcon, View.SCALE_Y, 1f, 1.1f, 1f)
        AnimatorSet().apply {
            duration = 420L
            interpolator = FastOutSlowInInterpolator()
            playTogether(dotScaleX, dotScaleY, dotFade, textFade, iconNudgeAnimator, iconScaleX, iconScaleY)
            start()
        }
    }

    private fun renderConnectionStatus(probe: OneXrConnectionProbe) {
        val nextStatus = if (probe.connected) {
            ConnectionStatusUi.CONNECTED
        } else {
            ConnectionStatusUi.NOT_CONNECTED
        }
        renderConnectionStatusUi(nextStatus)
    }
}
