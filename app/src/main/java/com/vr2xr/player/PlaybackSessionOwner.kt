package com.vr2xr.player

import android.view.Surface
import com.vr2xr.source.SourceDescriptor
import kotlinx.coroutines.flow.StateFlow

data class PlaybackSessionState(
    val source: SourceDescriptor? = null,
    val hasBoundSurface: Boolean = false,
    val expectedPlayWhenReady: Boolean = false,
    val playWhenReady: Boolean = false,
    val pauseReason: PauseReason = PauseReason.NONE,
    val playbackState: Int = androidx.media3.common.Player.STATE_IDLE,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val pausedFrameVisibleExpected: Boolean = false,
    val pausedFrameRefreshCount: Long = 0L,
    val lastPausedFrameRefreshResult: PausedFrameRefreshResult = PausedFrameRefreshResult.NONE,
    val awaitingFirstFrameAfterSurfaceBind: Boolean = false,
    val renderedFirstFrameCount: Long = 0L
)

enum class PauseReason {
    NONE,
    USER_INTENT,
    INTERRUPTION,
    APP_BACKGROUND
}

enum class PausedFrameRefreshResult {
    NONE,
    SKIPPED_NO_PLAYER,
    SKIPPED_EXPECTED_PLAYING,
    SKIPPED_NO_SURFACE,
    SKIPPED_PLAYER_ERROR,
    SKIPPED_IDLE,
    REFRESHED_SAME_POSITION,
    REFRESHED_NUDGE
}

interface PlaybackSessionOwner {
    val state: StateFlow<PlaybackSessionState>

    fun attachSource(source: SourceDescriptor, forceReset: Boolean)
    fun bindSurface(surface: Surface)
    fun clearSurface(surface: Surface)
    fun clearSurface()
    fun play()
    fun pause()
    fun pauseForInterruption()
    fun pauseForAppBackground()
    fun resumeIfExpected()
    fun showPausedFrameIfExpected()
    fun seekTo(positionMs: Long)
}
