package com.vr2xr.display

import android.app.Presentation
import android.content.Context
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.view.Display
import android.view.Surface
import android.widget.FrameLayout
import com.vr2xr.render.RenderMode
import com.vr2xr.render.VrSbsRenderer
import com.vr2xr.tracking.PoseState

class ExternalGlPresentation(
    context: Context,
    display: Display,
    private val listener: Listener
) : Presentation(context, display) {
    private lateinit var surfaceView: GLSurfaceView
    private lateinit var renderer: VrSbsRenderer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        surfaceView = GLSurfaceView(context).apply {
            setEGLContextClientVersion(2)
        }
        renderer = VrSbsRenderer(
            listener = object : VrSbsRenderer.Listener {
                override fun onVideoSurfaceReady(surface: Surface) {
                    listener.onVideoSurfaceReady(surface)
                }

                override fun onVideoSurfaceDestroyed() {
                    listener.onVideoSurfaceDestroyed()
                }
            }
        )
        surfaceView.setRenderer(renderer)
        surfaceView.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY

        setContentView(
            FrameLayout(context).apply {
                addView(
                    surfaceView,
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                )
            }
        )
    }

    override fun onStart() {
        super.onStart()
        surfaceView.onResume()
    }

    override fun onStop() {
        surfaceView.onPause()
        renderer.release()
        super.onStop()
    }

    fun setPose(pose: PoseState) {
        if (!::renderer.isInitialized) return
        renderer.setPose(pose)
    }

    fun setRenderMode(mode: RenderMode) {
        if (!::renderer.isInitialized) return
        renderer.setRenderMode(mode)
    }

    interface Listener {
        fun onVideoSurfaceReady(surface: Surface)
        fun onVideoSurfaceDestroyed()
    }
}
