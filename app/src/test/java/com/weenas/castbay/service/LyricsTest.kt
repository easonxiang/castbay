package com.weenas.castbay.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test

class LyricsTest {
    @Test
    fun parsesTimedLinesAndSkipsTags() {
        val lyrics = Lyrics.parseLrc(
            """
            [ti:晴天]
            [ar:周杰伦]
            [00:12.50]故事的小黄花
            [00:16.2]从出生那年就飘着
            [00:20]
            [01:02.345][02:10.00]刮风这天
            """.trimIndent()
        )!!
        assertEquals(
            listOf(
                Lyrics.Line(12.5, "故事的小黄花"),
                Lyrics.Line(16.2, "从出生那年就飘着"),
                Lyrics.Line(20.0, ""),
                Lyrics.Line(62.345, "刮风这天"),
                Lyrics.Line(130.0, "刮风这天")
            ),
            lyrics.lines
        )
    }

    @Test
    fun appliesTheOffsetAndRejectsUntimedText() {
        val lyrics = Lyrics.parseLrc("[offset:+500]\n[00:10.00]line")!!
        assertEquals(9.5, lyrics.lines.single().timeSec, 0.001)
        assertNull(Lyrics.parseLrc("just plain lyrics\nno timestamps"))
        assertNull(Lyrics.parseLrc("[00:01.00]\n[00:02.00]"))
    }

    @Test
    fun findsTheLineAtAPosition() {
        val lyrics = Lyrics(listOf(Lyrics.Line(10.0, "a"), Lyrics.Line(20.0, "b"), Lyrics.Line(30.0, "c")))
        assertEquals(-1, lyrics.indexAt(5.0))
        assertEquals(0, lyrics.indexAt(10.0))
        assertEquals(1, lyrics.indexAt(29.9))
        assertEquals(2, lyrics.indexAt(300.0))
    }

    @Test
    fun simplifiesDecoratedTitles() {
        assertEquals("Yesterday", Lyrics.simplifyTitle("Yesterday (Remastered 2009)"))
        assertEquals("Hey Jude", Lyrics.simplifyTitle("Hey Jude - Remastered 2015"))
        assertEquals("晴天", Lyrics.simplifyTitle("晴天【官方MV】"))
        assertEquals("Stay", Lyrics.simplifyTitle("Stay feat. Justin Bieber"))
        assertNull(Lyrics.simplifyTitle("晴天"))
    }

    @Test
    fun picksTheSyncedResultClosestInLength() {
        val short = LyricsCandidate(200.0, "[00:01.00]a")
        val close = LyricsCandidate(270.0, "[00:01.00]b")
        val unsynced = LyricsCandidate(269.0, null)
        val far = LyricsCandidate(300.0, "[00:01.00]c")
        assertEquals(close, LyricsMatcher.best(listOf(short, unsynced, far, close), 268.5))
        assertNull(LyricsMatcher.best(listOf(short, far), 268.5))
        assertEquals(short, LyricsMatcher.best(listOf(unsynced, short), 0.0))
    }

    @Test
    fun recognisesInstrumentalTitles() {
        for (title in listOf("River Flows in You (Instrumental)", "晴天 (伴奏)", "菊次郎的夏天 纯音乐", "千本桜 (Off Vocal)", "Song - Inst.", "Song (Inst.)", "Song [Karaoke Version]")) {
            assertTrue(title, Lyrics.isInstrumentalTitle(title))
        }
        for (title in listOf("爱上你是一个错 (伤感吉他版)", "晴天 钢琴版", "Instant Crush", "Institution", "抖音BGM", "光辉岁月 (Glorious Years)")) {
            assertFalse(title, Lyrics.isInstrumentalTitle(title))
        }
    }

    @Test
    fun instrumentalOnlyWhenEveryFittingResultIsMarked() {
        val sung = LyricsCandidate(211.0, "[00:01.00]a")
        val instrumental = LyricsCandidate(211.0, null, instrumental = true)
        val plain = LyricsCandidate(212.0, null)
        val otherLength = LyricsCandidate(150.0, "[00:01.00]b")
        assertEquals(instrumental, LyricsMatcher.best(listOf(instrumental, otherLength), 211.0))
        assertEquals(sung, LyricsMatcher.best(listOf(instrumental, sung), 211.0))
        assertNull(LyricsMatcher.best(listOf(instrumental, plain), 211.0))
        assertEquals(instrumental, LyricsMatcher.best(listOf(instrumental, instrumental), 0.0))
    }
}
