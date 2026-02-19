package com.vr2xr.player

import android.content.Context
import android.view.Surface
import androidx.media3.common.Player
import com.vr2xr.source.SourceDescriptor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AppPlaybackSessionOwner(
    context: Context
) : PlaybackSessionOwner {
    private val engine = VrPlayerEngine(context.applicationContext)
    private val _state = MutableStateFlow(PlaybackSessionState())

    private var activeSource: SourceDescriptor? = null
    private var activeSurface: Surface? = null
    private var expectedPlayWhenReady = false
    private var interruptionPauseInFlight = false

    override val state: StateFlow<PlaybackSessionState> = _state.asStateFlow()
    override val player: Player = engine.player

    init {
        player.addListener(
            object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    publishState()
                }

                override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                    if (interruptionPauseInFlight && !playWhenReady) {
                        interruptionPauseInFlight = false
                    } else {
                        expectedPlayWhenReady = playWhenReady
                    }
                    publishState()
                }

                override fun onPositionDiscontinuity(
                    oldPosition: Player.PositionInfo,
                    newPosition: Player.PositionInfo,
                    reason: Int
                ) {
                    publishState()
                }
            }
        )
        publishState()
    }

    override fun attachSource(source: SourceDescriptor, forceReset: Boolean) {
        val sourceChanged = activeSource?.normalized != source.normalized
        if (!sourceChanged && !forceReset) {
            publishState()
            return
        }
        activeSource = source
        expectedPlayWhenReady = true
        engine.loadSource(source, autoPlay = false, forceReset = sourceChanged || forceReset)
        activeSurface?.let { engine.bindVideoSurface(it) }
        publishState()
    }

    override fun bindSurface(surface: Surface) {
        if (activeSurface === surface) {
            publishState()
            return
        }
        activeSurface = surface
        engine.bindVideoSurface(surface)
        publishState()
    }

    override fun clearSurface(surface: Surface) {
        if (activeSurface !== surface) {
            return
        }
        engine.clearVideoSurface(surface)
        activeSurface = null
        publishState()
    }

    override fun clearSurface() {
        engine.clearVideoSurface()
        activeSurface = null
        publishState()
    }

    override fun play() {
        expectedPlayWhenReady = true
        engine.play()
        publishState()
    }

    override fun pause() {
        expectedPlayWhenReady = false
        engine.pause()
        publishState()
    }

    override fun pauseForInterruption() {
        if (!player.playWhenReady) {
            interruptionPauseInFlight = false
            publishState()
            return
        }
        interruptionPauseInFlight = true
        engine.pause()
        publishState()
    }

    override fun resumeIfExpected() {
        if (!expectedPlayWhenReady) {
            publishState()
            return
        }
        engine.play()
        publishState()
    }

    override fun showPausedFrameIfExpected() {
        if (expectedPlayWhenReady || player.playbackState == Player.STATE_IDLE) {
            publishState()
            return
        }
        val currentPositionMs = player.currentPosition.coerceAtLeast(0L)
        engine.seekTo(currentPositionMs)
        engine.pause()
        publishState()
    }

    override fun seekTo(positionMs: Long) {
        engine.seekTo(positionMs)
        publishState()
    }

    private fun publishState() {
        val rawDuration = player.duration
        val durationMs = if (rawDuration < 0L) 0L else rawDuration
        _state.value = PlaybackSessionState(
            source = activeSource,
            hasBoundSurface = activeSurface != null,
            expectedPlayWhenReady = expectedPlayWhenReady,
            playWhenReady = player.playWhenReady,
            playbackState = player.playbackState,
            positionMs = player.currentPosition.coerceAtLeast(0L),
            durationMs = durationMs
        )
    }
}
