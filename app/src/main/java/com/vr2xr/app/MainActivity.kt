package com.vr2xr.app

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.vr2xr.R
import com.vr2xr.databinding.ActivityMainBinding
import com.vr2xr.source.IntentIngestor
import com.vr2xr.source.SourceDescriptor
import com.vr2xr.source.SourceResolver
import com.vr2xr.tracking.OneProConnectionProbe
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val resolver = SourceResolver()
    private val errors by lazy { ErrorUiController(this) }
    private val trackingManager by lazy { (application as Vr2xrApplication).trackingSessionManager }
    private var connectionProbeJob: Job? = null

    private val openDocument = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) {
            binding.statusText.text = "File picker canceled"
            return@registerForActivityResult
        }
        resolver.resolveUri(this, uri, persistPermission = true)
            .onSuccess(::handleResolvedSource)
            .onFailure { errors.show(it.message ?: "Unable to open selected file") }
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
                .onFailure { errors.show(it.message ?: "Invalid URL") }
        }

        binding.xrealStatusText.text = getString(R.string.xreal_status_checking)
        maybeHandleInboundIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        startConnectionProbeLoop()
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
    }

    private fun maybeHandleInboundIntent(intent: Intent?) {
        if (intent == null) return
        val ingestorResult = IntentIngestor(resolver).ingest(this, intent) ?: return
        ingestorResult
            .onSuccess(::handleResolvedSource)
            .onFailure { errors.show(it.message ?: "Unsupported launch intent") }
    }

    private fun handleResolvedSource(source: SourceDescriptor) {
        lifecycleScope.launch {
            binding.statusText.text = getString(R.string.status_preparing_source, source.normalized)
            val probe = trackingManager.probeConnection()
            renderConnectionStatus(probe)
            if (probe.connected) {
                launchTrackingSetup(source)
            } else {
                launchPlayer(source)
            }
        }
    }

    private fun launchPlayer(source: SourceDescriptor) {
        binding.statusText.text = "Launching: ${source.normalized}"
        val intent = Intent(this, PlayerActivity::class.java)
            .putExtra(PlayerActivity.EXTRA_SOURCE, source)
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

    private fun renderConnectionStatus(probe: OneProConnectionProbe) {
        if (probe.connected) {
            binding.xrealStatusText.text = getString(R.string.xreal_status_connected)
            binding.xrealStatusText.setTextColor(Color.parseColor("#2E7D32"))
            return
        }

        if (probe.detail.startsWith("error")) {
            binding.xrealStatusText.text = getString(R.string.xreal_status_error)
            binding.xrealStatusText.setTextColor(Color.parseColor("#C62828"))
            return
        }

        binding.xrealStatusText.text = getString(R.string.xreal_status_not_connected)
        binding.xrealStatusText.setTextColor(Color.parseColor("#B26A00"))
    }
}
