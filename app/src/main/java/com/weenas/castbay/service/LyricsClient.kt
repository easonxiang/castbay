package com.weenas.castbay.service

import com.weenas.castbay.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Looks up time-synced lyrics on LRCLIB (lrclib.net: free, no account, community lyrics) by
 * the title, artist and length AirPlay senders report. Only used when the user turns lyrics
 * on, since it sends what is playing to that site. Blocking; call off the main thread.
 */
/** [appVersion] identifies the app to LRCLIB, as it asks; [baseUrl] and [sleep] are for tests. */
class LyricsClient(
    private val appVersion: String,
    private val baseUrl: String = BASE_URL,
    private val sleep: (Long) -> Unit = Thread::sleep
) {
    // The last songs looked up (plain JVM, so the client runs in unit tests).
    private val cache = object : LinkedHashMap<String, LyricsResult>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, LyricsResult>) = size > CACHE_SIZE
    }

    /**
     * The lyrics for a song, [LyricsResult.Instrumental] when it has no words (by its title, without
     * asking the site, or as the site marks it), [LyricsResult.None] when the site has none, or
     * [LyricsResult.Failed] when it could not be asked (busy or unreachable), worth trying again later.
     */
    fun find(title: String, artist: String?, album: String?, durationSec: Double): LyricsResult {
        if (Lyrics.isInstrumentalTitle(title)) return LyricsResult.Instrumental
        val key = listOf(title, artist.orEmpty(), durationSec.toInt().toString()).joinToString("\u0000")
        synchronized(cache) { cache[key] }?.let { return it }
        val result = try {
            lookUp(title, artist, album, durationSec)
        } catch (error: Exception) {
            // Not cached, so a later try asks again.
            Log.w(TAG, "Lyrics lookup failed for ${Log.personal(title)}: ${error.message}")
            return LyricsResult.Failed
        }
        synchronized(cache) { cache[key] = result }
        val outcome = when (result) {
            is LyricsResult.Found -> "${result.lyrics.lines.size} lines"
            LyricsResult.Instrumental -> "instrumental"
            else -> "none"
        }
        Log.i(TAG, "Lyrics for ${Log.personal(title)} / ${Log.personal(artist.orEmpty())}: $outcome")
        return result
    }

    /**
     * Tries the exact match, a search, then a search with the title simplified, until one finds
     * lyrics or that the song is instrumental. A step that fails does not stop the next ones; if
     * none found anything and any failed, this throws, so the song is not remembered as having none.
     */
    private fun lookUp(title: String, artist: String?, album: String?, durationSec: Double): LyricsResult {
        val steps = mutableListOf<() -> LyricsResult?>()
        // The exact match needs all four fields; search is looser.
        if (!artist.isNullOrBlank() && !album.isNullOrBlank() && durationSec > 0) {
            steps += { exact(title, artist, album, durationSec) }
        }
        steps += { search(title, artist, durationSec) }
        Lyrics.simplifyTitle(title)?.let { simpler -> steps += { search(simpler, artist, durationSec) } }
        var failure: Exception? = null
        for (step in steps) {
            try {
                step()?.let { return it }
            } catch (error: Exception) {
                failure = error
            }
        }
        failure?.let { throw it }
        return LyricsResult.None
    }

    private fun exact(title: String, artist: String, album: String, durationSec: Double): LyricsResult? {
        val body = get(
            "get",
            "track_name" to title, "artist_name" to artist, "album_name" to album,
            "duration" to durationSec.toInt().toString()
        ) ?: return null
        return candidate(JSONObject(body)).toResult()
    }

    private fun search(title: String, artist: String?, durationSec: Double): LyricsResult? {
        val params = mutableListOf("track_name" to title)
        if (!artist.isNullOrBlank()) params += "artist_name" to artist
        val body = get("search", *params.toTypedArray()) ?: return null
        val results = JSONArray(body)
        val candidates = (0 until results.length()).map { i -> candidate(results.getJSONObject(i)) }
        return LyricsMatcher.best(candidates, durationSec)?.toResult()
    }

    private fun candidate(item: JSONObject) = LyricsCandidate(
        item.optDouble("duration", 0.0).takeUnless { it.isNaN() } ?: 0.0,
        item.optString("syncedLyrics").takeIf { it.isNotBlank() && it != "null" },
        item.optBoolean("instrumental", false)
    )

    /** Lyrics, instrumental, or null (nothing usable: untimed text only). */
    private fun LyricsCandidate.toResult(): LyricsResult? = when {
        instrumental -> LyricsResult.Instrumental
        else -> syncedLrc?.let(Lyrics::parseLrc)?.let(LyricsResult::Found)
    }

    /**
     * The response body, or null for 404 (not found). When the site says it is busy (503, or
     * 429), waits as long as it asks (Retry-After, at most [MAX_RETRY_WAIT_MS]) and tries again.
     */
    private fun get(path: String, vararg params: Pair<String, String>): String? {
        val query = params.joinToString("&") { (name, value) -> "$name=${URLEncoder.encode(value, "UTF-8")}" }
        var attempt = 0
        while (true) {
            val response = fetch("$baseUrl/$path?$query")
            when (response.code) {
                HttpURLConnection.HTTP_OK -> return response.body
                HttpURLConnection.HTTP_NOT_FOUND -> return null
            }
            if (!isBusy(response.code) || attempt >= BUSY_RETRIES) throw java.io.IOException("HTTP ${response.code}")
            attempt++
            sleep(retryWaitMs(response.retryAfter, attempt))
        }
    }

    private class Response(val code: Int, val body: String?, val retryAfter: String?)

    private fun fetch(url: String): Response {
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            // LRCLIB asks clients to identify themselves.
            connection.setRequestProperty("User-Agent", "CastBay/$appVersion (https://github.com/weenas/castbay)")
            val code = connection.responseCode
            val body = if (code == HttpURLConnection.HTTP_OK) connection.inputStream.bufferedReader().use { it.readText() } else null
            Response(code, body, connection.getHeaderField("Retry-After"))
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val TAG = "CastBayLyrics"
        private const val CACHE_SIZE = 64
        private const val BASE_URL = "https://lrclib.net/api"
        // LRCLIB can take several seconds to answer from China.
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val READ_TIMEOUT_MS = 20_000
        // LRCLIB sheds load in bursts ("ServerOverloaded", Retry-After: 1); a few seconds later it answers.
        private const val BUSY_RETRIES = 3
        private const val MAX_RETRY_WAIT_MS = 5_000L

        internal fun isBusy(code: Int) = code == 503 || code == 429

        /** Retry-After in seconds when given, else 1, 2, 4 s for attempts 1, 2, 3; at most 5 s. */
        internal fun retryWaitMs(retryAfter: String?, attempt: Int): Long {
            val asked = retryAfter?.trim()?.toLongOrNull()?.takeIf { it >= 0 }?.times(1000)
            val backoff = 1000L shl (attempt - 1).coerceIn(0, 4)
            return maxOf(asked ?: 0L, backoff).coerceAtMost(MAX_RETRY_WAIT_MS)
        }
    }
}

/** What a lookup came to. */
sealed interface LyricsResult {
    data class Found(val lyrics: Lyrics) : LyricsResult
    /** The site has none for this song. */
    data object None : LyricsResult
    /** The song has no words (its title says so, or the site marks it). */
    data object Instrumental : LyricsResult
    /** The site was busy or unreachable; asking again later may find them. */
    data object Failed : LyricsResult
}
