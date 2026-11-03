package com.weenas.castbay.service

/** Time-synced lyrics: one line per timestamp, sorted by time. */
data class Lyrics(val lines: List<Line>) {
    data class Line(val timeSec: Double, val text: String)

    /** The index of the line being sung at [positionSec], or -1 before the first one. */
    fun indexAt(positionSec: Double): Int {
        var low = 0
        var high = lines.size - 1
        var found = -1
        while (low <= high) {
            val mid = (low + high) / 2
            if (lines[mid].timeSec <= positionSec) {
                found = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return found
    }

    companion object {
        private val TIMESTAMP = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")
        private val OFFSET = Regex("""\[offset:\s*([+-]?\d+)]""", RegexOption.IGNORE_CASE)

        /**
         * Parses LRC text: "[01:02.34]words", several timestamps per line allowed, and an
         * optional "[offset:+/-ms]" (positive shows lines earlier). Metadata tags and empty
         * lines between verses are dropped. Null when nothing is timed.
         */
        fun parseLrc(text: String): Lyrics? {
            val offsetSec = OFFSET.find(text)?.groupValues?.get(1)?.toIntOrNull()?.div(1000.0) ?: 0.0
            val lines = text.lineSequence().flatMap { raw ->
                val stamps = TIMESTAMP.findAll(raw).toList()
                if (stamps.isEmpty()) return@flatMap emptySequence()
                val words = raw.substring(stamps.last().range.last + 1).trim()
                stamps.asSequence().map { stamp ->
                    val (minutes, seconds, fraction) = stamp.destructured
                    val fractionSec = if (fraction.isEmpty()) 0.0 else fraction.toInt() / Math.pow(10.0, fraction.length.toDouble())
                    Line((minutes.toInt() * 60 + seconds.toInt() + fractionSec - offsetSec).coerceAtLeast(0.0), words)
                }
            }.sortedBy { it.timeSec }.toList()
            // Keep blank lines (instrumental gaps) only between sung ones.
            val sung = lines.dropWhile { it.text.isEmpty() }.dropLastWhile { it.text.isEmpty() }
            return sung.takeIf { lines -> lines.any { it.text.isNotEmpty() } }?.let(::Lyrics)
        }

        // Words in a title that say the track has no singing. Kept to clear ones: "钢琴版",
        // "吉他版" or "BGM" are often still sung.
        private val INSTRUMENTAL_TITLE = Regex(
            """纯音乐|純音樂|伴奏|instrumental|off[\s-]?vocal|karaoke|backing track|[(\[（【\-]\s*inst\.?\s*[)\]）】]?\s*$""",
            RegexOption.IGNORE_CASE
        )

        /** Whether [title] says the track is instrumental ("Song (Instrumental)", "xx 纯音乐", "Song (Inst.)"). */
        fun isInstrumentalTitle(title: String): Boolean = INSTRUMENTAL_TITLE.containsMatchIn(title)

        /**
         * A title without the decorations that keep lyric sites from matching, e.g.
         * "Song (Live)", "Song - Remastered 2011", "Song【官方MV】". Null when nothing changes.
         */
        fun simplifyTitle(title: String): String? {
            val simpler = title
                .replace(Regex("""\s*[(\[（【][^)\]）】]*[)\]）】]\s*"""), " ")
                .replace(Regex("""\s+-\s+.*$"""), "")
                .replace(Regex("""\s+(feat\.?|ft\.)\s+.*$""", RegexOption.IGNORE_CASE), "")
                .trim()
            return simpler.takeIf { it.isNotEmpty() && it != title.trim() }
        }
    }
}

/** A lyrics site's search result, reduced to what matching needs. */
data class LyricsCandidate(val durationSec: Double, val syncedLrc: String?, val instrumental: Boolean = false)

object LyricsMatcher {
    /** How far a result's duration may be from the song's (different edits are longer or shorter). */
    const val MAX_DURATION_DIFF_SEC = 3.0

    /**
     * The synced result closest in length to [durationSec] (any synced one when it is unknown).
     * Failing that, an instrumental one when every result of about that length is marked
     * instrumental (a sung version among them means the site just lacks its lyrics).
     */
    fun best(candidates: List<LyricsCandidate>, durationSec: Double): LyricsCandidate? {
        val fitting = if (durationSec <= 0) candidates else candidates.filter {
            it.durationSec <= 0 || kotlin.math.abs(it.durationSec - durationSec) <= MAX_DURATION_DIFF_SEC
        }
        val synced = fitting.filter { !it.instrumental && !it.syncedLrc.isNullOrBlank() }
        if (synced.isNotEmpty()) {
            if (durationSec <= 0) return synced.first()
            return synced.minByOrNull { if (it.durationSec <= 0) MAX_DURATION_DIFF_SEC else kotlin.math.abs(it.durationSec - durationSec) }
        }
        return fitting.takeIf { it.isNotEmpty() && it.all(LyricsCandidate::instrumental) }?.first()
    }
}
