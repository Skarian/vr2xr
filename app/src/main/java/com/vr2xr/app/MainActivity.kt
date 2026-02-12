package com.vr2xr.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.vr2xr.databinding.ActivityMainBinding
import com.vr2xr.source.IntentIngestor
import com.vr2xr.source.SourceDescriptor
import com.vr2xr.source.SourceResolver

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val resolver = SourceResolver()
    private val errors by lazy { ErrorUiController(this) }

    private val openDocument = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) {
            binding.statusText.text = "File picker canceled"
            return@registerForActivityResult
        }
        resolver.resolveUri(this, uri, persistPermission = true)
            .onSuccess(::launchPlayer)
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
                .onSuccess(::launchPlayer)
                .onFailure { errors.show(it.message ?: "Invalid URL") }
        }

        maybeHandleInboundIntent(intent)
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
            .onSuccess(::launchPlayer)
            .onFailure { errors.show(it.message ?: "Unsupported launch intent") }
    }

    private fun launchPlayer(source: SourceDescriptor) {
        binding.statusText.text = "Launching: ${source.normalized}"
        val intent = Intent(this, PlayerActivity::class.java)
            .putExtra(PlayerActivity.EXTRA_SOURCE, source)
        startActivity(intent)
    }
}
