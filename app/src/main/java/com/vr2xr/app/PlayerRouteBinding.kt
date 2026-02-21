package com.vr2xr.app

import android.os.SystemClock
import android.view.Surface
import com.vr2xr.display.ActiveRoute
import com.vr2xr.player.PlaybackSessionOwner

class PlayerRouteBinding(
    private val playbackSession: PlaybackSessionOwner
) {
    var activeRoute: ActiveRoute = ActiveRoute.NONE
        private set
    var activeSurface: Surface? = null
        private set

    private var routingActive = false
    private var teardownInProgress = false

    fun onStart() {
        routingActive = true
        teardownInProgress = false
    }

    fun onTeardownBegin() {
        routingActive = false
        teardownInProgress = true
    }

    fun canProcess(trigger: String, log: (String) -> Unit): Boolean {
        val enabled = routingActive && !teardownInProgress
        if (!enabled) {
            log("skip trigger=$trigger routingActive=$routingActive teardown=$teardownInProgress")
        }
        return enabled
    }

    fun bindSurface(
        surface: Surface?,
        route: ActiveRoute,
        log: (String) -> Unit
    ): Boolean {
        if (teardownInProgress) {
            log("skip bind route=$route teardown=true")
            return false
        }
        if (surface == null) {
            clearActiveSurface(force = false, log = log)
            return false
        }
        if (activeSurface === surface && activeRoute == route) {
            return false
        }
        val previousSurface = activeSurface
        if (previousSurface != null && previousSurface !== surface) {
            log("clear previous surface id=${System.identityHashCode(previousSurface)} before bind")
            playbackSession.clearSurface(previousSurface)
        }
        log("bind surface id=${System.identityHashCode(surface)} route=$route")
        playbackSession.bindSurface(surface)
        activeSurface = surface
        activeRoute = route
        return true
    }

    fun clearActiveSurface(force: Boolean, log: (String) -> Unit) {
        if (teardownInProgress && !force) {
            log("skip clear surface teardown=true force=false")
            return
        }
        val previousSurface = activeSurface
        if (previousSurface != null) {
            log("clear active surface id=${System.identityHashCode(previousSurface)}")
            playbackSession.clearSurface(previousSurface)
        }
        activeSurface = null
        activeRoute = ActiveRoute.NONE
    }

    fun onSurfaceDestroyed(surface: Surface?, clearSessionSurface: Boolean, log: (String) -> Unit) {
        if (surface == null || activeSurface !== surface) {
            return
        }
        if (clearSessionSurface) {
            playbackSession.clearSurface(surface)
        } else {
            log("surface destroyed while routing disabled id=${System.identityHashCode(surface)}")
        }
        activeSurface = null
        activeRoute = ActiveRoute.NONE
    }
}
