package com.vr2xr.player

import androidx.media3.common.Player
import com.vr2xr.source.SourceDescriptor
import com.vr2xr.source.SourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackCoordinatorPausedFramePolicyTest {
    @Test
    fun doesNotRefreshWhenExpectedToPlay() {
        val plan = buildPausedFrameRefreshPlan(
            expectedPlayWhenReady = true,
            hasBoundSurface = true,
            hasPlayerError = false,
            playbackState = Player.STATE_READY,
            currentPositionMs = 1_000L,
            durationMs = 10_000L
        )

        assertFalse(plan.shouldRefresh)
        assertEquals(PausedFrameRefreshResult.SKIPPED_EXPECTED_PLAYING, plan.result)
    }

    @Test
    fun doesNotRefreshWithoutBoundSurface() {
        val plan = buildPausedFrameRefreshPlan(
            expectedPlayWhenReady = false,
            hasBoundSurface = false,
            hasPlayerError = false,
            playbackState = Player.STATE_READY,
            currentPositionMs = 1_000L,
            durationMs = 10_000L
        )

        assertFalse(plan.shouldRefresh)
        assertEquals(PausedFrameRefreshResult.SKIPPED_NO_SURFACE, plan.result)
    }

    @Test
    fun nudgesForwardAndRestoresPositionWhenPaused() {
        val plan = buildPausedFrameRefreshPlan(
            expectedPlayWhenReady = false,
            hasBoundSurface = true,
            hasPlayerError = false,
            playbackState = Player.STATE_READY,
            currentPositionMs = 1_000L,
            durationMs = 10_000L
        )

        assertTrue(plan.shouldRefresh)
        assertEquals(PausedFrameRefreshResult.REFRESHED_NUDGE, plan.result)
        assertEquals(1_001L, plan.refreshPositionMs)
        assertEquals(1_000L, plan.restorePositionMs)
    }

    @Test
    fun nudgesBackwardAtEndOfMedia() {
        val plan = buildPausedFrameRefreshPlan(
            expectedPlayWhenReady = false,
            hasBoundSurface = true,
            hasPlayerError = false,
            playbackState = Player.STATE_READY,
            currentPositionMs = 9_999L,
            durationMs = 10_000L
        )

        assertTrue(plan.shouldRefresh)
        assertEquals(PausedFrameRefreshResult.REFRESHED_NUDGE, plan.result)
        assertEquals(9_998L, plan.refreshPositionMs)
        assertEquals(9_999L, plan.restorePositionMs)
    }

    @Test
    fun expectsPausedFrameOnlyForPausedNonIdleBoundState() {
        assertTrue(
            shouldExpectPausedFrameVisible(
                expectedPlayWhenReady = false,
                hasBoundSurface = true,
                hasSource = true,
                hasPlayerError = false,
                playbackState = Player.STATE_READY
            )
        )
        assertFalse(
            shouldExpectPausedFrameVisible(
                expectedPlayWhenReady = true,
                hasBoundSurface = true,
                hasSource = true,
                hasPlayerError = false,
                playbackState = Player.STATE_READY
            )
        )
        assertFalse(
            shouldExpectPausedFrameVisible(
                expectedPlayWhenReady = false,
                hasBoundSurface = true,
                hasSource = true,
                hasPlayerError = false,
                playbackState = Player.STATE_IDLE
            )
        )
        assertFalse(
            shouldExpectPausedFrameVisible(
                expectedPlayWhenReady = false,
                hasBoundSurface = true,
                hasSource = true,
                hasPlayerError = true,
                playbackState = Player.STATE_READY
            )
        )
    }

    @Test
    fun doesNotRefreshWhenPlayerHasError() {
        val plan = buildPausedFrameRefreshPlan(
            expectedPlayWhenReady = false,
            hasBoundSurface = true,
            hasPlayerError = true,
            playbackState = Player.STATE_READY,
            currentPositionMs = 1_000L,
            durationMs = 10_000L
        )

        assertFalse(plan.shouldRefresh)
        assertEquals(PausedFrameRefreshResult.SKIPPED_PLAYER_ERROR, plan.result)
    }

    @Test
    fun appBackgroundPauseClearsExpectedPlayIntent() {
        val coordinator = PlaybackCoordinator()
        coordinator.attachSource(sampleSource(), forceReset = true)

        assertTrue(coordinator.state.value.expectedPlayWhenReady)
        coordinator.pauseForAppBackground()

        assertFalse(coordinator.state.value.expectedPlayWhenReady)
        assertEquals(PauseReason.APP_BACKGROUND, coordinator.state.value.pauseReason)
    }

    @Test
    fun interruptionPauseWithoutPlayerPreservesExpectedPlayIntent() {
        val coordinator = PlaybackCoordinator()
        coordinator.attachSource(sampleSource(), forceReset = true)

        coordinator.pauseForInterruption()

        assertTrue(coordinator.state.value.expectedPlayWhenReady)
    }

    private fun sampleSource(): SourceDescriptor {
        return SourceDescriptor(
            original = "https://example.com/video.mp4",
            normalized = "https://example.com/video.mp4",
            type = SourceType.HTTP_URL
        )
    }
}
