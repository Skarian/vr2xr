package com.vr2xr.diag

import android.util.Log
import com.vr2xr.BuildConfig

object PlaybackDiagnosticsConfig {
    const val ROUTING_LOG_TAG = "PlayerRouting"

    fun isEnabled(): Boolean {
        return shouldEnable(
            buildFlagEnabled = BuildConfig.PLAYBACK_DIAGNOSTICS_ENABLED,
            routingLoggable = Log.isLoggable(ROUTING_LOG_TAG, Log.DEBUG)
        )
    }
}

internal fun shouldEnable(buildFlagEnabled: Boolean, routingLoggable: Boolean): Boolean {
    return buildFlagEnabled || routingLoggable
}
