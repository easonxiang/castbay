package com.weenas.castbay.util

import java.util.Locale

/**
 * One line that says how a screen-mirroring session went, written when it ends: reports of
 * "the phone stops mirroring after a few seconds" otherwise say only "sender disconnected"
 * (CB-928HM8), without telling a decoder that never got going from a sender that left early.
 */
object SessionSummary {
    fun video(
        seconds: Double,
        codec: String,
        width: Int,
        height: Int,
        decoder: String?,
        received: Long,
        bytes: Long,
        rendered: Long,
        dropped: Long,
        keyFrames: Int,
        longestKeyGap: Int,
    ): String = buildString {
        append(String.format(Locale.US, "Mirroring ended after %.1f s: %s", seconds, codec))
        if (width > 0 && height > 0) append(" ${width}x$height")
        append(" on ${decoder ?: "no decoder"}; $received frames received (${size(bytes)}), $rendered shown, $dropped dropped")
        append("; $keyFrames key frames")
        if (keyFrames > 1) append(", longest gap $longestKeyGap frames")
    }

    private fun size(bytes: Long): String =
        if (bytes >= 1 shl 20) String.format(Locale.US, "%.1f MB", bytes / 1048576.0) else "${bytes / 1024} KB"
}
