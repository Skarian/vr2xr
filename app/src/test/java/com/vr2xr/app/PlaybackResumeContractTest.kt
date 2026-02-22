package com.vr2xr.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackResumeContractTest {
    @Test
    fun resumeRequestRequiresMatchingActionAndExtra() {
        assertTrue(
            PlaybackResumeContract.isResumeRequest(
                action = PlaybackResumeContract.ACTION_RESUME_PLAYBACK,
                resumeRequested = true
            )
        )
    }

    @Test
    fun resumeRequestRejectsMissingExtra() {
        assertFalse(
            PlaybackResumeContract.isResumeRequest(
                action = PlaybackResumeContract.ACTION_RESUME_PLAYBACK,
                resumeRequested = false
            )
        )
    }

    @Test
    fun resumeRequestRejectsUnexpectedAction() {
        assertFalse(
            PlaybackResumeContract.isResumeRequest(
                action = "android.intent.action.MAIN",
                resumeRequested = true
            )
        )
    }
}
