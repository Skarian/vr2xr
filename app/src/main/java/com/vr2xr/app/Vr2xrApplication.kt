package com.vr2xr.app

import android.app.Application
import com.vr2xr.player.AppPlaybackSessionOwner
import com.vr2xr.player.PlaybackCoordinator
import com.vr2xr.player.PlaybackSessionOwner
import com.vr2xr.tracking.OneXrTrackingSessionManager

class Vr2xrApplication : Application() {
    val trackingSessionManager: OneXrTrackingSessionManager by lazy {
        OneXrTrackingSessionManager(this)
    }

    val playbackCoordinator: PlaybackCoordinator by lazy {
        PlaybackCoordinator()
    }

    val playbackSessionOwner: PlaybackSessionOwner by lazy {
        AppPlaybackSessionOwner(playbackCoordinator)
    }
}
