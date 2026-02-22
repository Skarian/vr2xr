package com.vr2xr.player

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.Player
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.vr2xr.app.MainActivity
import com.vr2xr.app.PlaybackResumeContract
import com.vr2xr.app.Vr2xrApplication

class VrPlaybackService : MediaSessionService() {
    companion object {
        const val CONTROLLER_HINT_ALLOW_APP_PLAYBACK = "com.vr2xr.player.allow_app_playback_controls"
    }

    private lateinit var playbackCoordinator: PlaybackCoordinator
    private lateinit var engine: VrPlayerEngine
    private lateinit var mediaSession: MediaSession
    private val mediaSessionCallback = object : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val defaultResult = super.onConnect(session, controller)
            if (!defaultResult.isAccepted) {
                return defaultResult
            }
            val allowAppPlaybackCommands = controller.packageName == packageName &&
                controller.connectionHints.getBoolean(CONTROLLER_HINT_ALLOW_APP_PLAYBACK, false)
            val availablePlayerCommands = if (allowAppPlaybackCommands) {
                defaultResult.availablePlayerCommands
            } else {
                defaultResult.availablePlayerCommands
                    .buildUpon()
                    .removeAll(Player.COMMAND_PLAY_PAUSE, Player.COMMAND_PREPARE)
                    .build()
            }
            val acceptedResultBuilder = MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(defaultResult.availableSessionCommands)
                .setAvailablePlayerCommands(availablePlayerCommands)
                .setCustomLayout(defaultResult.customLayout)
                .setSessionActivity(buildSessionActivity())
            defaultResult.sessionExtras?.let(acceptedResultBuilder::setSessionExtras)
            return acceptedResultBuilder.build()
        }
    }

    override fun onCreate() {
        super.onCreate()
        playbackCoordinator = (application as Vr2xrApplication).playbackCoordinator
        engine = VrPlayerEngine(this)
        playbackCoordinator.attachEngine(engine)
        mediaSession = MediaSession.Builder(this, engine.player)
            .setCallback(mediaSessionCallback)
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
        val intent = Intent(this, MainActivity::class.java).apply {
            action = PlaybackResumeContract.ACTION_RESUME_PLAYBACK
            putExtra(PlaybackResumeContract.EXTRA_RESUME_REQUESTED, true)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        return PendingIntent.getActivity(
            this,
            1000,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
