package com.vr2xr.app

import android.content.Context
import android.widget.Toast

class ErrorUiController(
    private val context: Context
) {
    fun show(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }
}
