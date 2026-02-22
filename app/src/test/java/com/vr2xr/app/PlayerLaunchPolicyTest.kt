package com.vr2xr.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerLaunchPolicyTest {
    @Test
    fun forcesResetWhenNotResumingExistingSession() {
        assertTrue(
            shouldForceSourceReset(
                hasSavedInstanceState = false,
                resumeExisting = false,
                currentSourceNormalized = "https://example.com/a.mp4",
                requestedSourceNormalized = "https://example.com/a.mp4"
            )
        )
    }

    @Test
    fun keepsExistingSessionWhenResumingSameSource() {
        assertFalse(
            shouldForceSourceReset(
                hasSavedInstanceState = false,
                resumeExisting = true,
                currentSourceNormalized = "https://example.com/a.mp4",
                requestedSourceNormalized = "https://example.com/a.mp4"
            )
        )
    }

    @Test
    fun resetsWhenResumingDifferentSource() {
        assertTrue(
            shouldForceSourceReset(
                hasSavedInstanceState = false,
                resumeExisting = true,
                currentSourceNormalized = "https://example.com/a.mp4",
                requestedSourceNormalized = "https://example.com/b.mp4"
            )
        )
    }

    @Test
    fun doesNotResetDuringConfigurationRecreation() {
        assertFalse(
            shouldForceSourceReset(
                hasSavedInstanceState = true,
                resumeExisting = false,
                currentSourceNormalized = "https://example.com/a.mp4",
                requestedSourceNormalized = "https://example.com/b.mp4"
            )
        )
    }
}
