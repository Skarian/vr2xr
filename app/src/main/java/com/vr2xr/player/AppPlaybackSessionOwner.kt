package com.vr2xr.player

import android.view.Surface
import com.vr2xr.source.SourceDescriptor
import kotlinx.coroutines.flow.StateFlow

class AppPlaybackSessionOwner(
    private val playbackCoordinator: PlaybackCoordinator
) : PlaybackSessionOwner {
    override val state: StateFlow<PlaybackSessionState> = playbackCoordinator.state

    override fun attachSource(source: SourceDescriptor, forceReset: Boolean) {
        playbackCoordinator.attachSource(source, forceReset)
    }

    override fun bindSurface(surface: Surface) {
        playbackCoordinator.bindSurface(surface)
    }

    override fun clearSurface(surface: Surface) {
        playbackCoordinator.clearSurface(surface)
    }

    override fun clearSurface() {
        playbackCoordinator.clearSurface()
    }

    override fun play() {
        playbackCoordinator.play()
    }

    override fun pause() {
        playbackCoordinator.pause()
    }

    override fun pauseForInterruption() {
        playbackCoordinator.pauseForInterruption()
    }

    override fun pauseForAppBackground() {
        playbackCoordinator.pauseForAppBackground()
    }

    override fun resumeIfExpected() {
        playbackCoordinator.resumeIfExpected()
    }

    override fun showPausedFrameIfExpected() {
        playbackCoordinator.showPausedFrameIfExpected()
    }

    override fun seekTo(positionMs: Long) {
        playbackCoordinator.seekTo(positionMs)
    }
}
