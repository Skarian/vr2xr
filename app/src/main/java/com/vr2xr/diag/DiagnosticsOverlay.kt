package com.vr2xr.diag

import android.content.Context
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.TextView

class DiagnosticsOverlay(context: Context) : FrameLayout(context) {
    private val textView = TextView(context).apply {
        setBackgroundColor(0x88000000.toInt())
        setTextColor(0xFFFFFFFF.toInt())
        textSize = 11f
        setPadding(12, 12, 12, 12)
    }

    init {
        val params = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        params.gravity = Gravity.TOP or Gravity.START
        addView(textView, params)
    }

    fun update(state: DiagnosticsState) {
        textView.text = buildString {
            appendLine("Display: ${state.displaySummary}")
            appendLine("Tracking: ${state.trackingSummary}")
            appendLine("Decoder: ${state.decoderSummary}")
            append("Dropped: ${state.droppedFrames}")
        }
    }
}

data class DiagnosticsState(
    val displaySummary: String = "none",
    val trackingSummary: String = "unavailable",
    val decoderSummary: String = "unknown",
    val droppedFrames: Long = 0
)
