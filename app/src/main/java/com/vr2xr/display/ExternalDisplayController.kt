package com.vr2xr.display

import android.content.Context
import android.hardware.display.DisplayManager
import android.view.Display

class ExternalDisplayController(
    context: Context,
    private val listener: Listener? = null
) {
    private val displayManager = context.getSystemService(DisplayManager::class.java)
    private var started = false
    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) {
            listener?.onModeChanged(currentPhysicalMode())
        }

        override fun onDisplayRemoved(displayId: Int) {
            listener?.onModeChanged(currentPhysicalMode())
        }

        override fun onDisplayChanged(displayId: Int) {
            listener?.onModeChanged(currentPhysicalMode())
        }
    }

    fun start() {
        if (started) return
        started = true
        displayManager.registerDisplayListener(displayListener, null)
        listener?.onModeChanged(currentPhysicalMode())
    }

    fun stop() {
        if (!started) return
        started = false
        displayManager.unregisterDisplayListener(displayListener)
    }

    fun currentPhysicalMode(): PhysicalDisplayMode? {
        val target: Display = currentPresentationDisplay() ?: return null
        val mode = target.mode
        return PhysicalDisplayMode(
            displayId = target.displayId,
            modeId = mode.modeId,
            width = mode.physicalWidth,
            height = mode.physicalHeight,
            refreshRateHz = mode.refreshRate
        )
    }

    fun currentPresentationDisplay(): Display? {
        return displayManager
            .getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
            .filter { it.state != Display.STATE_OFF }
            .maxWithOrNull(displayRankingComparator)
    }

    interface Listener {
        fun onModeChanged(mode: PhysicalDisplayMode?)
    }
}

private val displayRankingComparator =
    compareBy<Display> { it.mode.physicalWidth.toLong() * it.mode.physicalHeight.toLong() }
        .thenBy { it.mode.refreshRate }

data class PhysicalDisplayMode(
    val displayId: Int,
    val modeId: Int,
    val width: Int,
    val height: Int,
    val refreshRateHz: Float
)
