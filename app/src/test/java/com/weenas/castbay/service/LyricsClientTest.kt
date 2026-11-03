package com.weenas.castbay.service

import com.sun.net.httpserver.HttpServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetSocketAddress
import java.net.URLDecoder

class LyricsClientTest {
    private val requests = mutableListOf<String>()
    private val sleeps = mutableListOf<Long>()
    /** Answers each request: (status, body) from the path and query. */
    private var answer: (String) -> Pair<Int, String> = { 404 to "" }
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/api") { exchange ->
            val request = exchange.requestURI.path.removePrefix("/api/") + "?" +
                URLDecoder.decode(exchange.requestURI.rawQuery.orEmpty(), "UTF-8")
            synchronized(requests) { requests += request }
            val (status, body) = answer(request)
            if (status == 503) exchange.responseHeaders.add("Retry-After", "1")
            val bytes = body.toByteArray()
            exchange.sendResponseHeaders(status, if (bytes.isEmpty()) -1 else bytes.size.toLong())
            if (bytes.isNotEmpty()) exchange.responseBody.use { it.write(bytes) }
            exchange.close()
        }
        start()
    }
    private val client = LyricsClient("test", "http://127.0.0.1:${server.address.port}/api") { sleeps += it }

    @After
    fun stop() = server.stop(0)

    private fun found(text: String) = """[{"duration":258.0,"syncedLyrics":"[00:01.00]$text"}]"""

    @Test
    fun retriesWhileTheSiteIsBusy() {
        var busy = 2
        answer = { if (busy-- > 0) 503 to """{"name":"ServerOverloaded"}""" else 200 to found("泡沫") }
        val result = client.find("泡沫", "邓紫棋", null, 258.0)
        assertEquals("泡沫", (result as LyricsResult.Found).lyrics.lines.single().text)
        assertEquals(listOf(1000L, 2000L), sleeps)
    }

    @Test
    fun aBusyStepDoesNotStopTheSimplerTitle() {
        // The exact match and the full title stay busy; the title without brackets answers.
        answer = { request ->
            if (request.startsWith("search") && request.contains("track_name=光辉岁月&")) 200 to found("光辉岁月") else 503 to ""
        }
        val result = client.find("光辉岁月 (Glorious Years)", "Beyond", "光辉岁月", 258.0)
        assertTrue(result is LyricsResult.Found)
        assertEquals(listOf(1000L, 2000L, 4000L, 1000L, 2000L, 4000L), sleeps)
    }

    @Test
    fun aLookupThatFailedIsAskedAgainLater() {
        answer = { 503 to "" }
        assertEquals(LyricsResult.Failed, client.find("囚鸟", "彭羚", null, 260.0))
        answer = { 200 to found("囚鸟") }
        assertTrue(client.find("囚鸟", "彭羚", null, 260.0) is LyricsResult.Found)
    }

    @Test
    fun noneIsRemembered() {
        answer = { 200 to "[]" }
        assertEquals(LyricsResult.None, client.find("凹凸", null, null, 0.0))
        val asked = requests.size
        assertEquals(LyricsResult.None, client.find("凹凸", null, null, 0.0))
        assertEquals(asked, requests.size)
        assertTrue(sleeps.isEmpty())
    }

    @Test
    fun waitsAsAskedWithinLimits() {
        assertEquals(1000L, LyricsClient.retryWaitMs("1", 1))
        assertEquals(2000L, LyricsClient.retryWaitMs("1", 2))
        assertEquals(3000L, LyricsClient.retryWaitMs("3", 1))
        assertEquals(5000L, LyricsClient.retryWaitMs("120", 1))
        assertEquals(4000L, LyricsClient.retryWaitMs(null, 3))
        assertTrue(LyricsClient.isBusy(503) && LyricsClient.isBusy(429) && !LyricsClient.isBusy(500))
    }

    @Test
    fun anInstrumentalTitleIsNotLookedUp() {
        assertEquals(LyricsResult.Instrumental, client.find("River Flows in You (Instrumental)", "Yiruma", null, 211.0))
        assertTrue(requests.isEmpty())
    }

    @Test
    fun theSiteMarksInstrumentalTracks() {
        answer = { request ->
            if (request.startsWith("get")) 200 to """{"duration":211.0,"instrumental":true,"syncedLyrics":null}""" else 404 to ""
        }
        assertEquals(LyricsResult.Instrumental, client.find("River Flows in You", "Yiruma", "First Love", 211.0))
        assertEquals(1, requests.size)
        answer = { 200 to """[{"duration":211.0,"instrumental":true,"syncedLyrics":null}]""" }
        assertEquals(LyricsResult.Instrumental, client.find("Kiss the Rain", "Yiruma", null, 211.0))
    }
}
