package com.vr2xr.player

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.vr2xr.app.MainActivity
import com.vr2xr.app.Vr2xrApplication

class VrPlaybackService : MediaSessionService() {
    private lateinit var playbackCoordinator: PlaybackCoordinator
    private lateinit var engine: VrPlayerEngine
    private lateinit var mediaSession: MediaSession

    override fun onCreate() {
        super.onCreate()
        playbackCoordinator = (application as Vr2xrApplication).playbackCoordinator
        engine = VrPlayerEngine(this)
        playbackCoordinator.attachEngine(engine)
        mediaSession = MediaSession.Builder(this, engine.player)
            .setSessionActivity(buildSessionActivity())
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession {
        return mediaSession
    }

    override fun onDestroy() {
        playbackCoordinator.detachEngine(engine)
        mediaSession.release()
        engine.release()
        super.onDestroy()
    }

    private fun buildSessionActivity(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(
            this,
            1000,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
