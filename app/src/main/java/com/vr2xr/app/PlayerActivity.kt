package com.vr2xr.app

import android.os.Build
import android.os.Bundle
import android.opengl.GLSurfaceView
import android.view.MotionEvent
import android.view.Surface
import androidx.appcompat.app.AppCompatActivity
import com.vr2xr.databinding.ActivityPlayerBinding
import com.vr2xr.diag.DiagnosticsOverlay
import com.vr2xr.diag.DiagnosticsState
import com.vr2xr.display.ExternalDisplayController
import com.vr2xr.display.ExternalGlPresentation
import com.vr2xr.player.CodecCapabilityProbe
import com.vr2xr.player.VrPlayerEngine
import com.vr2xr.render.RenderMode
import com.vr2xr.render.VrSbsRenderer
import com.vr2xr.source.SourceDescriptor
import com.vr2xr.tracking.PoseState
import kotlin.math.cos
import kotlin.math.sin

class PlayerActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPlayerBinding
    private lateinit var player: VrPlayerEngine
    private lateinit var diagnosticsOverlay: DiagnosticsOverlay
    private lateinit var displayController: ExternalDisplayController
    private lateinit var internalRenderer: VrSbsRenderer
    private var decoderSummary: String = "unknown"
    private var mediaPrepared = false
    private var activeSurface: Surface? = null
    private var internalSurface: Surface? = null
    private var externalSurface: Surface? = null
    private var externalPresentation: ExternalGlPresentation? = null
    private var latestPose: PoseState = PoseState()
    private var trackingSummary: String = "manual touch controls"
    private var manualYawRad: Float = 0f
    private var manualPitchRad: Float = 0f
    private var lastTouchX: Float = 0f
    private var lastTouchY: Float = 0f
    private val renderMode = RenderMode()
    private val errors by lazy { ErrorUiController(this) }

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

        displayController = ExternalDisplayController(
            context = this,
            listener = object : ExternalDisplayController.Listener {
                override fun onModeChanged(mode: com.vr2xr.display.PhysicalDisplayMode?) {
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

        updateDiagnostics()

        binding.recenterButton.setOnClickListener {
            recenterView()
        }
    }

    override fun onStart() {
        super.onStart()
        displayController.start()
        binding.videoSurface.onResume()
        syncExternalPresentation()
        val pose = buildManualPose()
        latestPose = pose
        applyPoseToRenderers(pose)
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
        if (display == null) {
            dismissExternalPresentation()
            bindPreferredSurface(source)
            return
        }
        val activePresentation = externalPresentation
        if (activePresentation != null && activePresentation.display.displayId == display.displayId) {
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
                it.setRenderMode(renderMode)
                it.setPose(latestPose)
                it.show()
            }
        }.onFailure {
            errors.show("Failed to start external presentation: ${it.message ?: "unknown error"}")
            externalPresentation = null
            externalSurface = null
        }
    }

    private fun dismissExternalPresentation() {
        externalPresentation?.dismiss()
        externalPresentation = null
        externalSurface = null
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
        manualYawRad = 0f
        manualPitchRad = 0f
        val pose = buildManualPose()
        latestPose = pose
        applyPoseToRenderers(pose)
        updateDiagnostics()
    }

    private fun handleLookTouch(event: MotionEvent): Boolean {
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

private const val TOUCH_RADIANS_PER_PIXEL = 0.0035f
private const val MAX_MANUAL_YAW_RAD = 3.1415927f
private const val MAX_MANUAL_PITCH_RAD = 1.2f
