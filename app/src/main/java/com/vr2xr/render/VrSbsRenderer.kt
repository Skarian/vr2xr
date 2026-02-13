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
import kotlin.math.tan
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
    private var projectionModeHandle: Int = -1
    private var headYawHandle: Int = -1
    private var headPitchHandle: Int = -1
    private var eyeYawOffsetHandle: Int = -1
    private var tanHalfFovXHandle: Int = -1
    private var tanHalfFovYHandle: Int = -1

    private var outputWidth: Int = 1
    private var outputHeight: Int = 1
    private var externalTextureId: Int = 0
    private var surfaceTexture: SurfaceTexture? = null
    private var surface: Surface? = null
    @Volatile
    private var latestPose: PoseState = PoseState()
    @Volatile
    private var latestRenderMode: RenderMode = RenderMode()

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
        projectionModeHandle = GLES20.glGetUniformLocation(program, "uProjectionMode")
        headYawHandle = GLES20.glGetUniformLocation(program, "uHeadYawRad")
        headPitchHandle = GLES20.glGetUniformLocation(program, "uHeadPitchRad")
        eyeYawOffsetHandle = GLES20.glGetUniformLocation(program, "uEyeYawOffsetRad")
        tanHalfFovXHandle = GLES20.glGetUniformLocation(program, "uTanHalfFovX")
        tanHalfFovYHandle = GLES20.glGetUniformLocation(program, "uTanHalfFovY")

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
        renderFrame(externalTextureId, latestPose, latestRenderMode)
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
        GLES20.glUniform1i(projectionModeHandle, mode.projectionMode.toShaderValue())
        GLES20.glUniform1f(headYawHandle, pose.yaw)
        GLES20.glUniform1f(headPitchHandle, pose.pitch)

        val halfWidth = outputWidth / 2
        val leftEyeWidth = halfWidth.coerceAtLeast(1)
        val rightEyeWidth = (outputWidth - halfWidth).coerceAtLeast(1)
        val safeHeight = outputHeight.coerceAtLeast(1)
        val halfConvergenceRadians = Math.toRadians(mode.convergenceDegrees.toDouble()).toFloat() * 0.5f
        val tanHalfFovY = tan(Math.toRadians((mode.perEyeFovDegrees * 0.5f).toDouble())).toFloat()

        // Left eye from left half of source texture.
        GLES20.glViewport(0, 0, halfWidth, outputHeight)
        GLES20.glUniform1f(texScaleHandle, 0.5f)
        GLES20.glUniform1f(texOffsetHandle, if (mode.swapEyes) 0.5f else 0f)
        GLES20.glUniform1f(eyeYawOffsetHandle, halfConvergenceRadians)
        GLES20.glUniform1f(tanHalfFovYHandle, tanHalfFovY)
        GLES20.glUniform1f(tanHalfFovXHandle, tanHalfFovY * (leftEyeWidth.toFloat() / safeHeight.toFloat()))
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        // Right eye from right half of source texture.
        GLES20.glViewport(halfWidth, 0, outputWidth - halfWidth, outputHeight)
        GLES20.glUniform1f(texScaleHandle, 0.5f)
        GLES20.glUniform1f(texOffsetHandle, if (mode.swapEyes) 0f else 0.5f)
        GLES20.glUniform1f(eyeYawOffsetHandle, -halfConvergenceRadians)
        GLES20.glUniform1f(tanHalfFovYHandle, tanHalfFovY)
        GLES20.glUniform1f(tanHalfFovXHandle, tanHalfFovY * (rightEyeWidth.toFloat() / safeHeight.toFloat()))
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(texCoordHandle)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0)
    }

    override fun onFrameAvailable(surfaceTexture: SurfaceTexture?) {
        frameAvailable.set(true)
    }

    fun setPose(pose: PoseState) {
        latestPose = pose
    }

    fun setRenderMode(mode: RenderMode) {
        latestRenderMode = mode
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
    val swapEyes: Boolean = false,
    val convergenceDegrees: Float = 0f,
    val perEyeFovDegrees: Float = 95f
)

private fun ProjectionMode.toShaderValue(): Int {
    return when (this) {
        ProjectionMode.VR180 -> 0
        ProjectionMode.VR360 -> 1
        ProjectionMode.FLAT -> 2
    }
}

private const val VERTEX_SHADER = """
attribute vec2 aPosition;
attribute vec2 aTexCoord;
varying vec2 vRawCoord;

void main() {
    gl_Position = vec4(aPosition, 0.0, 1.0);
    vRawCoord = aTexCoord;
}
"""

private const val FRAGMENT_SHADER = """
#extension GL_OES_EGL_image_external : require
precision mediump float;
varying vec2 vRawCoord;
uniform float uTexScaleX;
uniform float uTexOffsetX;
uniform int uProjectionMode;
uniform float uHeadYawRad;
uniform float uHeadPitchRad;
uniform float uEyeYawOffsetRad;
uniform float uTanHalfFovX;
uniform float uTanHalfFovY;
uniform samplerExternalOES uTexture;

const float PI = 3.1415926535897932384626433832795;
const float HALF_PI = 1.5707963267948966192313216916398;
const float TWO_PI = 6.283185307179586476925286766559;

vec3 rotateYaw(vec3 v, float angle) {
    float c = cos(angle);
    float s = sin(angle);
    return vec3(
        (c * v.x) + (s * v.z),
        v.y,
        (-s * v.x) + (c * v.z)
    );
}

vec3 rotatePitch(vec3 v, float angle) {
    float c = cos(angle);
    float s = sin(angle);
    return vec3(
        v.x,
        (c * v.y) - (s * v.z),
        (s * v.y) + (c * v.z)
    );
}

vec3 makeViewRay(vec2 eyeUv) {
    float ndcX = (eyeUv.x * 2.0) - 1.0;
    float ndcY = 1.0 - (eyeUv.y * 2.0);
    vec3 ray = normalize(vec3(ndcX * uTanHalfFovX, ndcY * uTanHalfFovY, 1.0));
    ray = rotatePitch(ray, uHeadPitchRad);
    ray = rotateYaw(ray, uHeadYawRad + uEyeYawOffsetRad);
    return ray;
}

vec2 mapVr180(vec3 ray) {
    float yaw = atan(ray.x, ray.z);
    float pitch = asin(clamp(ray.y, -1.0, 1.0));

    if (abs(yaw) > HALF_PI) {
        return vec2(-1.0, -1.0);
    }
    return vec2((yaw / PI) + 0.5, 0.5 - (pitch / PI));
}

vec2 mapVr360(vec3 ray) {
    float yaw = atan(ray.x, ray.z);
    float pitch = asin(clamp(ray.y, -1.0, 1.0));
    float wrappedYaw = mod((yaw / TWO_PI) + 0.5, 1.0);
    if (wrappedYaw < 0.0) wrappedYaw += 1.0;
    return vec2(wrappedYaw, 0.5 - (pitch / PI));
}

void main() {
    vec2 eyeUv = vRawCoord;
    vec3 ray = makeViewRay(vRawCoord);
    if (uProjectionMode == 0) {
        eyeUv = mapVr180(ray);
    } else if (uProjectionMode == 1) {
        eyeUv = mapVr360(ray);
    }

    if (eyeUv.x < 0.0 || eyeUv.y < 0.0 || eyeUv.x > 1.0 || eyeUv.y > 1.0) {
        gl_FragColor = vec4(0.0, 0.0, 0.0, 1.0);
        return;
    }

    eyeUv = clamp(eyeUv, 0.0, 1.0);
    vec2 sampleUv = vec2((eyeUv.x * uTexScaleX) + uTexOffsetX, eyeUv.y);
    gl_FragColor = texture2D(uTexture, sampleUv);
}
"""
