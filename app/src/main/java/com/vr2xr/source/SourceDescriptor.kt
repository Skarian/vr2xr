package com.vr2xr.source

import android.net.Uri
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class SourceDescriptor(
    val original: String,
    val normalized: String,
    val type: SourceType,
    val displayName: String? = null
) : Parcelable {
    fun toUri(): Uri = Uri.parse(normalized)
}

enum class SourceType {
    LOCAL_URI,
    HTTP_URL
}
