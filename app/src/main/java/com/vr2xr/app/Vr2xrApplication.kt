package com.vr2xr.app

import android.app.Application
import com.vr2xr.player.AppPlaybackSessionOwner
import com.vr2xr.player.PlaybackSessionOwner
import com.vr2xr.tracking.OneProTrackingSessionManager

class Vr2xrApplication : Application() {
    val trackingSessionManager: OneProTrackingSessionManager by lazy {
        OneProTrackingSessionManager(this)
    }

    val playbackSessionOwner: PlaybackSessionOwner by lazy {
        AppPlaybackSessionOwner(this)
    }
}
