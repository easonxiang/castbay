package com.weenas.castbay.service

import androidx.media3.common.C
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import java.util.Locale

/** One entry of the quick menu's audio or subtitle choices; [group] is null for "Off". */
data class TrackChoice(val label: String, val group: TrackGroup?, val index: Int, val selected: Boolean)

/**
 * The audio tracks and subtitles an AirPlay video offers (e.g. an HLS stream's languages), and
 * switching between them. Main thread only, like the player.
 */
object MediaTracks {
    /** Supported tracks of [type] ([C.TRACK_TYPE_AUDIO] or [C.TRACK_TYPE_TEXT]); subtitles start with "Off". */
    /** [untitled] names a track the stream gives no name or language, by its number from 1 ("Track 2"). */
    fun choices(player: Player, type: Int, untitled: (Int) -> String = { "Track $it" }): List<TrackChoice> {
        val tracks = player.currentTracks.groups.filter { it.type == type }.flatMap { group ->
            (0 until group.length).filter(group::isTrackSupported).filterNot { index ->
                group.getTrackFormat(index).let { isPlaceholderCaption(it.sampleMimeType, it.language) }
            }.map { index ->
                Triple(group.mediaTrackGroup, index, group.isTrackSelected(index))
            }
        }
        val choices = tracks.mapIndexed { position, (group, index, selected) ->
            group.getFormat(index).let { TrackChoice(label(it.language, it.label, position, untitled = untitled), group, index, selected) }
        }
        if (type != C.TRACK_TYPE_TEXT || choices.isEmpty()) return choices
        val off = TrackChoice("Off", null, 0, selected = choices.none { it.selected })
        return listOf(off) + choices
    }

    fun select(player: Player, type: Int, choice: TrackChoice) {
        val builder = player.trackSelectionParameters.buildUpon()
        val group = choice.group
        if (group == null) {
            builder.setTrackTypeDisabled(type, true)
        } else {
            builder.setTrackTypeDisabled(type, false)
                .setOverrideForType(TrackSelectionOverride(group, choice.index))
        }
        player.trackSelectionParameters = builder.build()
    }

    /**
     * A closed-caption channel inferred from the video stream rather than declared with a
     * language. HLS streams (e.g. iQiyi's) carry these even when empty, and their subtitles are
     * burned into the picture, so offering them as "Subtitles" only confuses.
     */
    fun isPlaceholderCaption(mimeType: String?, language: String?): Boolean =
        (mimeType == MimeTypes.APPLICATION_CEA608 || mimeType == MimeTypes.APPLICATION_CEA708) &&
            (language.isNullOrBlank() || language == C.LANGUAGE_UNDETERMINED)

    /** The choice after the selected one, wrapping around, for OK-to-cycle menu rows. */
    fun next(choices: List<TrackChoice>): TrackChoice? {
        if (choices.isEmpty()) return null
        val current = choices.indexOfFirst { it.selected }
        return choices[(current + 1) % choices.size]
    }

    /** "English", "Chinese · Commentary", or "Track 2" when the stream names nothing. */
    fun label(
        languageTag: String?,
        name: String?,
        position: Int,
        displayLocale: Locale = Locale.getDefault(),
        untitled: (Int) -> String = { "Track $it" }
    ): String {
        val language = languageTag
            ?.takeIf { it.isNotBlank() && it != C.LANGUAGE_UNDETERMINED }
            ?.let { Locale.forLanguageTag(it).getDisplayName(displayLocale) }
            ?.takeIf { it.isNotBlank() }
        val label = name?.takeIf { it.isNotBlank() }
        return when {
            language != null && label != null && !label.equals(language, ignoreCase = true) -> "$language · $label"
            label != null -> label
            language != null -> language
            else -> untitled(position + 1)
        }
    }
}
