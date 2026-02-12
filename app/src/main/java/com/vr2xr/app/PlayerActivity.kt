package com.vr2xr.app

import android.os.Bundle
import android.opengl.GLSurfaceView
import android.view.Surface
import androidx.appcompat.app.AppCompatActivity
import com.vr2xr.databinding.ActivityPlayerBinding
import com.vr2xr.diag.DiagnosticsOverlay
import com.vr2xr.diag.DiagnosticsState
import com.vr2xr.display.ExternalDisplayController
import com.vr2xr.player.CodecCapabilityProbe
import com.vr2xr.player.VrPlayerEngine
import com.vr2xr.render.VrSbsRenderer
import com.vr2xr.source.SourceDescriptor

class PlayerActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPlayerBinding
    private lateinit var player: VrPlayerEngine
    private lateinit var diagnosticsOverlay: DiagnosticsOverlay
    private lateinit var displayController: ExternalDisplayController
    private lateinit var renderer: VrSbsRenderer
    private var decoderSummary: String = "unknown"
    private var prepared = false

    private val source: SourceDescriptor? by lazy {
        intent.getParcelableExtra(EXTRA_SOURCE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        displayController = ExternalDisplayController(
            context = this,
            listener = object : ExternalDisplayController.Listener {
                override fun onModeChanged(mode: com.vr2xr.display.PhysicalDisplayMode?) {
                    updateDiagnostics()
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

        renderer = VrSbsRenderer(
            listener = object : VrSbsRenderer.Listener {
                override fun onVideoSurfaceReady(surface: Surface) {
                    runOnUiThread {
                        if (!prepared) {
                            player.prepare(resolvedSource, surface)
                            prepared = true
                        }
                        player.play()
                        updateDiagnostics()
                    }
                }

                override fun onVideoSurfaceDestroyed() {
                    runOnUiThread {
                        prepared = false
                        player.pause()
                    }
                }
            }
        )
        binding.videoSurface.setEGLContextClientVersion(2)
        binding.videoSurface.setRenderer(renderer)
        binding.videoSurface.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY

        updateDiagnostics()

        binding.recenterButton.setOnClickListener {
            // Tracking recenter hook added in Phase 5.
        }
    }

    override fun onStart() {
        super.onStart()
        displayController.start()
        binding.videoSurface.onResume()
    }

    override fun onStop() {
        super.onStop()
        binding.videoSurface.onPause()
        displayController.stop()
        player.pause()
    }

    override fun onDestroy() {
        renderer.release()
        player.release()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_SOURCE = "extra_source"
    }

    private fun updateDiagnostics() {
        val mode = displayController.currentPhysicalMode()
        diagnosticsOverlay.update(
            DiagnosticsState(
                displaySummary = mode?.let { "${it.width}x${it.height}@${it.refreshRateHz}" } ?: "internal-only",
                trackingSummary = "not connected",
                decoderSummary = decoderSummary,
                droppedFrames = 0
            )
        )
    }
}
