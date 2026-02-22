package com.vr2xr.app

object PlaybackResumeContract {
    const val ACTION_RESUME_PLAYBACK = "com.vr2xr.action.RESUME_PLAYBACK"
    const val EXTRA_RESUME_REQUESTED = "com.vr2xr.extra.RESUME_REQUESTED"

    fun isResumeRequest(
        action: String?,
        resumeRequested: Boolean
    ): Boolean {
        return action == ACTION_RESUME_PLAYBACK && resumeRequested
    }
}
