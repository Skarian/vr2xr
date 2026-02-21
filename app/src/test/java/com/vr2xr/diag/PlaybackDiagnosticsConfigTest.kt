package com.vr2xr.diag

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackDiagnosticsConfigTest {
    @Test
    fun diagnosticsDisabledWhenNoEnableSignals() {
        assertFalse(shouldEnable(buildFlagEnabled = false, routingLoggable = false))
    }

    @Test
    fun diagnosticsEnabledWhenBuildFlagOn() {
        assertTrue(shouldEnable(buildFlagEnabled = true, routingLoggable = false))
    }

    @Test
    fun diagnosticsEnabledWhenRoutingLoggable() {
        assertTrue(shouldEnable(buildFlagEnabled = false, routingLoggable = true))
    }
}
