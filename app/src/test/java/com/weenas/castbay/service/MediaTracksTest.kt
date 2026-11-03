package com.weenas.castbay.service

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class MediaTracksTest {
    private fun label(language: String?, name: String?, position: Int = 0) =
        MediaTracks.label(language, name, position, Locale.ENGLISH) { "Track $it" }

    @Test
    fun namesTracksByLanguageAndLabel() {
        assertEquals("English", label("en", null))
        assertEquals("Chinese", label("zh", "Chinese"))
        assertEquals("Chinese · Commentary", label("zh", "Commentary"))
        assertEquals("Director's cut", label(null, "Director's cut"))
        assertEquals("Track 3", label("und", null, position = 2))
        assertEquals("音轨 3", MediaTracks.label("und", null, 2, Locale.ENGLISH) { "音轨 $it" })
    }

    @Test
    fun hidesUndeclaredClosedCaptions() {
        assertEquals(true, MediaTracks.isPlaceholderCaption("application/cea-608", null))
        assertEquals(true, MediaTracks.isPlaceholderCaption("application/cea-708", "und"))
        assertEquals(false, MediaTracks.isPlaceholderCaption("application/cea-608", "en"))
        assertEquals(false, MediaTracks.isPlaceholderCaption("text/vtt", null))
    }

    @Test
    fun nextWrapsAround() {
        val off = TrackChoice("Off", null, 0, selected = false)
        val first = TrackChoice("English", null, 0, selected = false)
        val second = TrackChoice("Chinese", null, 1, selected = true)
        assertEquals(off, MediaTracks.next(listOf(off, first, second)))
        assertEquals(first, MediaTracks.next(listOf(off.copy(selected = true), first, second.copy(selected = false))))
        assertEquals(null, MediaTracks.next(emptyList()))
    }
}
