package com.vr2xr.source

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri

class SourceResolver {
    companion object {
        private val HTTP_URL_REGEX = Regex("""https?://[^\s<>"']+""", RegexOption.IGNORE_CASE)
    }

    fun resolve(intent: Intent?): SourceDescriptor? {
        val uri = intent?.data ?: return null
        val scheme = uri.scheme?.lowercase() ?: return null
        return when (scheme) {
            "http", "https" -> SourceDescriptor(
                original = uri.toString(),
                normalized = uri.toString(),
                type = SourceType.HTTP_URL,
                displayName = uri.lastPathSegment
            )

            ContentResolver.SCHEME_CONTENT, ContentResolver.SCHEME_FILE -> SourceDescriptor(
                original = uri.toString(),
                normalized = uri.toString(),
                type = SourceType.LOCAL_URI,
                displayName = uri.lastPathSegment
            )

            else -> null
        }
    }

    fun resolve(context: Context, intent: Intent?): Result<SourceDescriptor> {
        if (intent == null) {
            return Result.failure(IllegalArgumentException("No intent provided"))
        }
        return IntentIngestor(this).ingest(context, intent)
            ?: Result.failure(IllegalArgumentException("Unsupported launch intent"))
    }

    fun resolveUrl(raw: String): Result<SourceDescriptor> {
        val normalizedRaw = raw.trim()
        if (normalizedRaw.isBlank()) {
            return Result.failure(IllegalArgumentException("URL is empty"))
        }

        val parsed = Uri.parse(normalizedRaw)
        val scheme = parsed.scheme?.lowercase()
        return if (scheme == "http" || scheme == "https") {
            Result.success(
                SourceDescriptor(
                    original = normalizedRaw,
                    normalized = parsed.toString(),
                    type = SourceType.HTTP_URL,
                    displayName = parsed.lastPathSegment
                )
            )
        } else {
            Result.failure(IllegalArgumentException("Only http(s) URLs are supported"))
        }
    }

    fun resolveSharedText(raw: String): Result<SourceDescriptor> {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) {
            return Result.failure(IllegalArgumentException("Shared text is empty"))
        }

        val candidate = HTTP_URL_REGEX.find(trimmed)?.value ?: trimmed
        return resolveUrl(candidate)
    }

    fun resolveUri(context: Context, uri: Uri, persistPermission: Boolean): Result<SourceDescriptor> {
        val scheme = uri.scheme?.lowercase()
        if (persistPermission) {
            val persistFailure = takePersistablePermission(context, uri, scheme)
            if (persistFailure != null) {
                return Result.failure(persistFailure)
            }
        }
        return when (scheme) {
            ContentResolver.SCHEME_CONTENT, ContentResolver.SCHEME_FILE -> Result.success(
                SourceDescriptor(
                    original = uri.toString(),
                    normalized = uri.toString(),
                    type = SourceType.LOCAL_URI,
                    displayName = uri.lastPathSegment
                )
            )

            "http", "https" -> resolveUrl(uri.toString())
            else -> Result.failure(IllegalArgumentException("Unsupported URI scheme: $scheme"))
        }
    }

    private fun takePersistablePermission(
        context: Context,
        uri: Uri,
        scheme: String?
    ): Throwable? {
        if (scheme != ContentResolver.SCHEME_CONTENT) {
            return null
        }
        return runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            null
        }.getOrElse { error ->
            PersistablePermissionException(uri = uri, cause = error)
        }
    }
}

class PersistablePermissionException(
    uri: Uri,
    cause: Throwable
) : IllegalStateException("Unable to persist permission for selected source: $uri", cause)
