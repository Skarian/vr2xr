package com.vr2xr.player

import android.view.Surface
import androidx.media3.common.Player
import com.vr2xr.source.SourceDescriptor
import kotlinx.coroutines.flow.StateFlow

data class PlaybackSessionState(
    val source: SourceDescriptor? = null,
    val hasBoundSurface: Boolean = false,
    val expectedPlayWhenReady: Boolean = false,
    val playWhenReady: Boolean = false,
    val playbackState: Int = Player.STATE_IDLE,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L
)

interface PlaybackSessionOwner {
    val state: StateFlow<PlaybackSessionState>
    val player: Player

    fun attachSource(source: SourceDescriptor, forceReset: Boolean)
    fun bindSurface(surface: Surface)
    fun clearSurface(surface: Surface)
    fun clearSurface()
    fun play()
    fun pause()
    fun pauseForInterruption()
    fun resumeIfExpected()
    fun showPausedFrameIfExpected()
    fun seekTo(positionMs: Long)
}
