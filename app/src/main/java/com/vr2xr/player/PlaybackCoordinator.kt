package com.vr2xr.player

import android.view.Surface
import androidx.media3.common.Player
import com.vr2xr.source.SourceDescriptor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PlaybackCoordinator {
    private val _state = MutableStateFlow(PlaybackSessionState())

    private var engine: VrPlayerEngine? = null
    private var activeSource: SourceDescriptor? = null
    private var activeSurface: Surface? = null
    private var expectedPlayWhenReady = false
    private var pauseReason = PauseReason.NONE
    private var interruptionPauseInFlight = false
    private var pausedFrameRefreshCount = 0L
    private var lastPausedFrameRefreshResult = PausedFrameRefreshResult.NONE
    private var awaitingFirstFrameAfterSurfaceBind = false
    private var renderedFirstFrameCount = 0L

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            publishState()
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (interruptionPauseInFlight && !playWhenReady) {
                interruptionPauseInFlight = false
                pauseReason = PauseReason.INTERRUPTION
            } else {
                expectedPlayWhenReady = playWhenReady
                pauseReason = if (playWhenReady) PauseReason.NONE else PauseReason.USER_INTENT
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

        override fun onRenderedFirstFrame() {
            awaitingFirstFrameAfterSurfaceBind = false
            renderedFirstFrameCount += 1L
            publishState()
        }
    }

    val state: StateFlow<PlaybackSessionState> = _state.asStateFlow()

    fun currentPlayer(): Player? = engine?.player

    fun attachEngine(engine: VrPlayerEngine) {
        if (this.engine === engine) {
            publishState()
            return
        }
        val expectedBeforeAttach = expectedPlayWhenReady
        val restorePositionMs = state.value.positionMs.coerceAtLeast(0L)
        val shouldResume = expectedBeforeAttach && activeSurface != null && activeSource != null
        this.engine?.player?.removeListener(playerListener)
        this.engine = engine
        engine.player.addListener(playerListener)
        activeSource?.let { source ->
            engine.loadSource(source, autoPlay = false, forceReset = true)
            if (restorePositionMs > 0L) {
                engine.seekTo(restorePositionMs)
            }
        }
        expectedPlayWhenReady = expectedBeforeAttach
        activeSurface?.let { surface ->
            awaitingFirstFrameAfterSurfaceBind = true
            engine.bindVideoSurface(surface)
        }
        if (shouldResume) {
            engine.play()
        }
        publishState()
    }

    fun detachEngine(engine: VrPlayerEngine) {
        if (this.engine !== engine) {
            return
        }
        engine.player.removeListener(playerListener)
        this.engine = null
        publishState()
    }

    fun attachSource(source: SourceDescriptor, forceReset: Boolean) {
        val sourceChanged = activeSource?.normalized != source.normalized
        if (!sourceChanged && !forceReset) {
            publishState()
            return
        }
        activeSource = source
        expectedPlayWhenReady = true
        pauseReason = PauseReason.NONE
        engine?.loadSource(source, autoPlay = false, forceReset = sourceChanged || forceReset)
        activeSurface?.let { surface ->
            engine?.bindVideoSurface(surface)
        }
        publishState()
    }

    fun bindSurface(surface: Surface) {
        if (activeSurface === surface) {
            publishState()
            return
        }
        val previousSurface = activeSurface
        if (previousSurface != null && previousSurface !== surface) {
            engine?.clearVideoSurface(previousSurface)
        }
        activeSurface = surface
        awaitingFirstFrameAfterSurfaceBind = true
        engine?.bindVideoSurface(surface)
        publishState()
    }

    fun clearSurface(surface: Surface) {
        if (activeSurface !== surface) {
            return
        }
        engine?.clearVideoSurface(surface)
        activeSurface = null
        awaitingFirstFrameAfterSurfaceBind = false
        publishState()
    }

    fun clearSurface() {
        engine?.clearVideoSurface()
        activeSurface = null
        awaitingFirstFrameAfterSurfaceBind = false
        publishState()
    }

    fun play() {
        expectedPlayWhenReady = true
        pauseReason = PauseReason.NONE
        engine?.play()
        publishState()
    }

    fun pause() {
        expectedPlayWhenReady = false
        pauseReason = PauseReason.USER_INTENT
        engine?.pause()
        publishState()
    }

    fun pauseForInterruption() {
        val player = engine?.player
        if (player == null || !player.playWhenReady) {
            publishState()
            return
        }
        interruptionPauseInFlight = true
        pauseReason = PauseReason.INTERRUPTION
        engine?.pause()
        publishState()
    }

    fun pauseForAppBackground() {
        expectedPlayWhenReady = false
        pauseReason = PauseReason.APP_BACKGROUND
        interruptionPauseInFlight = false
        val player = engine?.player
        if (player?.playWhenReady == true) {
            engine?.pause()
        }
        publishState()
    }

    fun resumeIfExpected() {
        if (!expectedPlayWhenReady) {
            publishState()
            return
        }
        engine?.play()
        publishState()
    }

    fun showPausedFrameIfExpected() {
        val player = engine?.player
        if (player == null) {
            lastPausedFrameRefreshResult = PausedFrameRefreshResult.SKIPPED_NO_PLAYER
            publishState()
            return
        }
        val refreshPlan = buildPausedFrameRefreshPlan(
            expectedPlayWhenReady = expectedPlayWhenReady,
            hasBoundSurface = activeSurface != null,
            hasPlayerError = player.playerError != null,
            playbackState = player.playbackState,
            currentPositionMs = player.currentPosition,
            durationMs = player.duration
        )
        lastPausedFrameRefreshResult = refreshPlan.result
        if (!refreshPlan.shouldRefresh) {
            publishState()
            return
        }
        pausedFrameRefreshCount += 1L
        engine?.seekTo(refreshPlan.refreshPositionMs)
        refreshPlan.restorePositionMs?.let { restorePositionMs ->
            engine?.seekTo(restorePositionMs)
        }
        engine?.pause()
        publishState()
    }

    fun seekTo(positionMs: Long) {
        engine?.seekTo(positionMs)
        publishState()
    }

    private fun publishState() {
        val player = engine?.player
        val rawDuration = player?.duration ?: 0L
        val durationMs = if (rawDuration < 0L) 0L else rawDuration
        _state.value = PlaybackSessionState(
            source = activeSource,
            hasBoundSurface = activeSurface != null,
            expectedPlayWhenReady = expectedPlayWhenReady,
            playWhenReady = player?.playWhenReady == true,
            pauseReason = pauseReason,
            playbackState = player?.playbackState ?: Player.STATE_IDLE,
            positionMs = player?.currentPosition?.coerceAtLeast(0L) ?: 0L,
            durationMs = durationMs,
            pausedFrameVisibleExpected = shouldExpectPausedFrameVisible(
                expectedPlayWhenReady = expectedPlayWhenReady,
                hasBoundSurface = activeSurface != null,
                hasSource = activeSource != null,
                hasPlayerError = player?.playerError != null,
                playbackState = player?.playbackState ?: Player.STATE_IDLE
            ),
            pausedFrameRefreshCount = pausedFrameRefreshCount,
            lastPausedFrameRefreshResult = lastPausedFrameRefreshResult,
            awaitingFirstFrameAfterSurfaceBind = awaitingFirstFrameAfterSurfaceBind,
            renderedFirstFrameCount = renderedFirstFrameCount
        )
    }
}

internal data class PausedFrameRefreshPlan(
    val shouldRefresh: Boolean,
    val refreshPositionMs: Long,
    val restorePositionMs: Long?,
    val result: PausedFrameRefreshResult
)

internal fun shouldExpectPausedFrameVisible(
    expectedPlayWhenReady: Boolean,
    hasBoundSurface: Boolean,
    hasSource: Boolean,
    hasPlayerError: Boolean,
    playbackState: Int
): Boolean {
    return !expectedPlayWhenReady &&
        hasBoundSurface &&
        hasSource &&
        !hasPlayerError &&
        playbackState != Player.STATE_IDLE
}

internal fun buildPausedFrameRefreshPlan(
    expectedPlayWhenReady: Boolean,
    hasBoundSurface: Boolean,
    hasPlayerError: Boolean,
    playbackState: Int,
    currentPositionMs: Long,
    durationMs: Long
): PausedFrameRefreshPlan {
    if (expectedPlayWhenReady) {
        return PausedFrameRefreshPlan(
            shouldRefresh = false,
            refreshPositionMs = 0L,
            restorePositionMs = null,
            result = PausedFrameRefreshResult.SKIPPED_EXPECTED_PLAYING
        )
    }
    if (!hasBoundSurface) {
        return PausedFrameRefreshPlan(
            shouldRefresh = false,
            refreshPositionMs = 0L,
            restorePositionMs = null,
            result = PausedFrameRefreshResult.SKIPPED_NO_SURFACE
        )
    }
    if (hasPlayerError) {
        return PausedFrameRefreshPlan(
            shouldRefresh = false,
            refreshPositionMs = 0L,
            restorePositionMs = null,
            result = PausedFrameRefreshResult.SKIPPED_PLAYER_ERROR
        )
    }
    if (playbackState == Player.STATE_IDLE) {
        return PausedFrameRefreshPlan(
            shouldRefresh = false,
            refreshPositionMs = 0L,
            restorePositionMs = null,
            result = PausedFrameRefreshResult.SKIPPED_IDLE
        )
    }
    val clampedPositionMs = currentPositionMs.coerceAtLeast(0L)
    val cappedDurationMs = durationMs.coerceAtLeast(0L)
    val nudgeCandidateMs = when {
        cappedDurationMs <= 1L -> clampedPositionMs
        clampedPositionMs + 1L < cappedDurationMs -> clampedPositionMs + 1L
        clampedPositionMs > 0L -> clampedPositionMs - 1L
        else -> clampedPositionMs
    }
    if (nudgeCandidateMs == clampedPositionMs) {
        return PausedFrameRefreshPlan(
            shouldRefresh = true,
            refreshPositionMs = clampedPositionMs,
            restorePositionMs = null,
            result = PausedFrameRefreshResult.REFRESHED_SAME_POSITION
        )
    }
    return PausedFrameRefreshPlan(
        shouldRefresh = true,
        refreshPositionMs = nudgeCandidateMs,
        restorePositionMs = clampedPositionMs,
        result = PausedFrameRefreshResult.REFRESHED_NUDGE
    )
}
