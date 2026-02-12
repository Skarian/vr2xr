package com.vr2xr.render

import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.view.Surface
import com.vr2xr.render.gl.GlUtil
import com.vr2xr.tracking.PoseState
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.atomic.AtomicBoolean
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class VrSbsRenderer(
    private val listener: Listener? = null
) : GLSurfaceView.Renderer, SurfaceTexture.OnFrameAvailableListener {
    private val frameAvailable = AtomicBoolean(false)
    private var program: Int = 0
    private var positionHandle: Int = -1
    private var texCoordHandle: Int = -1
    private var texScaleHandle: Int = -1
    private var texOffsetHandle: Int = -1
    private var samplerHandle: Int = -1

    private var outputWidth: Int = 1
    private var outputHeight: Int = 1
    private var externalTextureId: Int = 0
    private var surfaceTexture: SurfaceTexture? = null
    private var surface: Surface? = null

    private val quadVertices: FloatBuffer = ByteBuffer
        .allocateDirect(4 * 2 * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .apply {
            put(
                floatArrayOf(
                    -1f, -1f,
                    1f, -1f,
                    -1f, 1f,
                    1f, 1f
                )
            )
            position(0)
        }

    private val quadTexCoords: FloatBuffer = ByteBuffer
        .allocateDirect(4 * 2 * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .apply {
            put(
                floatArrayOf(
                    0f, 1f,
                    1f, 1f,
                    0f, 0f,
                    1f, 0f
                )
            )
            position(0)
        }

    override fun onSurfaceCreated(unused: GL10?, config: EGLConfig?) {
        onSurfaceCreated()
    }

    fun onSurfaceCreated() {
        program = GlUtil.createProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        positionHandle = GLES20.glGetAttribLocation(program, "aPosition")
        texCoordHandle = GLES20.glGetAttribLocation(program, "aTexCoord")
        texScaleHandle = GLES20.glGetUniformLocation(program, "uTexScaleX")
        texOffsetHandle = GLES20.glGetUniformLocation(program, "uTexOffsetX")
        samplerHandle = GLES20.glGetUniformLocation(program, "uTexture")

        externalTextureId = GlUtil.createExternalTexture()
        surfaceTexture = SurfaceTexture(externalTextureId).apply {
            setOnFrameAvailableListener(this@VrSbsRenderer)
        }
        surface = Surface(surfaceTexture).also { listener?.onVideoSurfaceReady(it) }

        GLES20.glClearColor(0f, 0f, 0f, 1f)
    }

    override fun onSurfaceChanged(unused: GL10?, width: Int, height: Int) {
        onSurfaceChanged(width, height)
    }

    fun onSurfaceChanged(width: Int, height: Int) {
        outputWidth = width.coerceAtLeast(1)
        outputHeight = height.coerceAtLeast(1)
        GLES20.glViewport(0, 0, outputWidth, outputHeight)
    }

    override fun onDrawFrame(unused: GL10?) {
        renderFrame(externalTextureId, PoseState(), RenderMode())
    }

    fun renderFrame(frameTexId: Int, pose: PoseState, mode: RenderMode) {
        if (frameAvailable.compareAndSet(true, false)) {
            surfaceTexture?.updateTexImage()
        }
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glUseProgram(program)

        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 0, quadVertices)
        GLES20.glEnableVertexAttribArray(texCoordHandle)
        GLES20.glVertexAttribPointer(texCoordHandle, 2, GLES20.GL_FLOAT, false, 0, quadTexCoords)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, frameTexId)
        GLES20.glUniform1i(samplerHandle, 0)

        val halfWidth = outputWidth / 2

        // Left eye from left half of source texture.
        GLES20.glViewport(0, 0, halfWidth, outputHeight)
        GLES20.glUniform1f(texScaleHandle, 0.5f)
        GLES20.glUniform1f(texOffsetHandle, if (mode.swapEyes) 0.5f else 0f)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        // Right eye from right half of source texture.
        GLES20.glViewport(halfWidth, 0, outputWidth - halfWidth, outputHeight)
        GLES20.glUniform1f(texScaleHandle, 0.5f)
        GLES20.glUniform1f(texOffsetHandle, if (mode.swapEyes) 0f else 0.5f)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(texCoordHandle)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0)
    }

    override fun onFrameAvailable(surfaceTexture: SurfaceTexture?) {
        frameAvailable.set(true)
    }

    fun release() {
        listener?.onVideoSurfaceDestroyed()
        surface?.release()
        surface = null
        surfaceTexture?.release()
        surfaceTexture = null
    }

    interface Listener {
        fun onVideoSurfaceReady(surface: Surface)
        fun onVideoSurfaceDestroyed()
    }
}

data class RenderMode(
    val projectionMode: ProjectionMode = ProjectionMode.VR180,
    val swapEyes: Boolean = false
)

private const val VERTEX_SHADER = """
attribute vec2 aPosition;
attribute vec2 aTexCoord;
uniform float uTexScaleX;
uniform float uTexOffsetX;
varying vec2 vTexCoord;

void main() {
    gl_Position = vec4(aPosition, 0.0, 1.0);
    vTexCoord = vec2((aTexCoord.x * uTexScaleX) + uTexOffsetX, aTexCoord.y);
}
"""

private const val FRAGMENT_SHADER = """
#extension GL_OES_EGL_image_external : require
precision mediump float;
varying vec2 vTexCoord;
uniform samplerExternalOES uTexture;

void main() {
    gl_FragColor = texture2D(uTexture, vTexCoord);
}
"""
