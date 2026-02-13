package com.vr2xr.source

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build

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
                val streamUri = intent.firstSharedUri()
                if (streamUri != null) {
                    resolver.resolveUri(context, streamUri, persistPermission = false)
                } else {
                    val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                    if (text.isNullOrBlank()) {
                        Result.failure(IllegalArgumentException("ACTION_SEND missing stream/text"))
                    } else {
                        resolver.resolveSharedText(text)
                    }
                }
            }

            Intent.ACTION_SEND_MULTIPLE -> {
                val streamUri = intent.firstSharedUriFromMultiple()
                if (streamUri != null) {
                    resolver.resolveUri(context, streamUri, persistPermission = false)
                } else {
                    Result.failure(IllegalArgumentException("ACTION_SEND_MULTIPLE missing stream"))
                }
            }

            else -> null
        }
    }

    private fun Intent.firstSharedUri(): Uri? {
        val extraStream = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            getParcelableExtra(Intent.EXTRA_STREAM)
        }
        return extraStream ?: firstClipDataUri()
    }

    private fun Intent.firstSharedUriFromMultiple(): Uri? {
        val firstFromExtras = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)?.firstOrNull()
        } else {
            @Suppress("DEPRECATION")
            getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)?.firstOrNull()
        }
        return firstFromExtras ?: firstClipDataUri()
    }

    private fun Intent.firstClipDataUri(): Uri? {
        val clip = clipData ?: return null
        if (clip.itemCount == 0) return null
        return clip.getItemAt(0).uri
    }
}
