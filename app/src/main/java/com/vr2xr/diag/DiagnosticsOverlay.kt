package com.vr2xr.diag

import android.content.Context
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.vr2xr.R

class DiagnosticsOverlay(context: Context) : FrameLayout(context) {
    private val textView = TextView(context).apply {
        setBackgroundColor(ContextCompat.getColor(context, R.color.overlay_background))
        setTextColor(ContextCompat.getColor(context, R.color.overlay_text_primary))
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
            append("Playback: ${state.playbackSummary}")
        }
    }
}

data class DiagnosticsState(
    val displaySummary: String = "none",
    val trackingSummary: String = "unavailable",
    val decoderSummary: String = "unknown",
    val playbackSummary: String = "unavailable"
)
