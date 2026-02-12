package com.vr2xr.source

import android.content.Context
import android.content.Intent
import android.net.Uri

class IntentIngestor(
    private val resolver: SourceResolver
) {
    fun ingest(context: Context, intent: Intent): Result<SourceDescriptor>? {
        return when (intent.action) {
            Intent.ACTION_VIEW -> {
                val uri = intent.data
                if (uri != null) {
                    resolver.resolveUri(context, uri, persistPermission = false)
                } else {
                    Result.failure(IllegalArgumentException("ACTION_VIEW missing data URI"))
                }
            }

            Intent.ACTION_SEND -> {
                val streamUri: Uri? = intent.getParcelableExtra(Intent.EXTRA_STREAM)
                if (streamUri != null) {
                    resolver.resolveUri(context, streamUri, persistPermission = false)
                } else {
                    val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                    if (text.isNullOrBlank()) {
                        Result.failure(IllegalArgumentException("ACTION_SEND missing stream/text"))
                    } else {
                        resolver.resolveUrl(text)
                    }
                }
            }

            else -> null
        }
    }
}
