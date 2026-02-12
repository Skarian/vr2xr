package com.vr2xr.player

import android.media.MediaCodecList

class CodecCapabilityProbe {
    fun findHevcDecoderSummary(): String {
        val codecs = MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos
            .asSequence()
            .filter { !it.isEncoder }
            .filter { info -> info.supportedTypes.any { it.equals("video/hevc", ignoreCase = true) } }
            .toList()

        if (codecs.isEmpty()) {
            return "No HEVC decoder advertised"
        }

        val first = codecs.first()
        val caps = first.getCapabilitiesForType("video/hevc").videoCapabilities
        val width = caps.supportedWidths.upper
        val height = caps.supportedHeights.upper
        return "${first.name} max=${width}x${height}"
    }
}
