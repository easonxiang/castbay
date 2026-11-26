package com.weenas.castbay.util

import android.content.Context
import android.os.Process
import com.weenas.castbay.protocol.AirPlayNative
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * A problem report the person chooses to send (About → Diagnostics → Upload): this device,
 * the Diagnostics events and the app's recent log, with personal details taken out on the
 * device ([scrub]). The website keeps it for 90 days under a short ID ("CB-7K3F9Q") that the
 * person passes on with their problem report; nothing else is sent, and nothing is sent
 * without them asking.
 */
object LogReport {
    private const val TAG = "CastBayReport"
    // The website takes up to 512 KB.
    private const val MAX_CHARS = 400_000
    private const val LOGCAT_LINES = 3000
    private const val PREVIOUS_LINES = 2000
    private const val CONNECT_TIMEOUT_MS = 20_000
    // Some networks upload slowly; the answer comes once the whole report is in.
    private const val READ_TIMEOUT_MS = 60_000
    private const val ATTEMPTS = 2

    /**
     * The report's text, already scrubbed: what [upload] sends. With [crash] (from
     * [CrashReports]), that comes first. Blocking (reads logcat).
     */
    fun build(context: Context, crash: String? = null): String {
        val utc = SimpleDateFormat("yyyy-MM-dd HH:mm:ss 'UTC'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        val head = buildString {
            appendLine("CastBay problem report, ${utc.format(Date())}")
            Diagnostics.deviceInfo(context).forEach { (label, value) -> appendLine("$label: $value") }
            appendLine()
            if (crash != null) {
                appendLine("== The crash ==")
                appendLine(crash.trimEnd())
                appendLine()
            }
            appendLine("== Diagnostics events (oldest first) ==")
            Diagnostics.events().asReversed().forEach { appendLine(Diagnostics.format(it)) }
            appendLine()
        }
        // Three logs, as no one of them is enough everywhere: the app's own lines and the
        // protocol code's (kept in memory: some devices, TCL TVs and BYD car displays among
        // them, keep no app logs in logcat), then logcat, for the player and the system.
        val total = (MAX_CHARS - head.length).coerceAtLeast(0)
        // What led up to an unfinished previous run, when there was one: in memory it was lost.
        val previous = if (Diagnostics.previousRunUnfinished) newest(Log.previousRun(PREVIOUS_LINES), total / 4) else ""
        val budget = total - previous.length
        val app = newest(Log.recent(), budget * 4 / 10)
        val native = newest(runCatching { AirPlayNative.recentLog() }.getOrDefault(emptyList()), budget * 4 / 10)
        val system = newest(logcat(), budget - app.length - native.length)
        return scrub(buildString {
            append(head)
            if (previous.isNotEmpty()) {
                appendLine("== The previous run, which ended without CastBay stopping it (its last lines) ==")
                appendLine(previous)
                appendLine()
            }
            appendLine("== App log ==")
            appendLine(app.ifEmpty { "(none)" })
            appendLine()
            appendLine("== AirPlay protocol log ==")
            appendLine(native.ifEmpty { "(none)" })
            appendLine()
            appendLine("== System log (logcat, this app's process) ==")
            append(system.ifEmpty { "(none: this device keeps no app logs)" })
        })
    }

    /** The newest of [lines] that fit in [budget] characters, oldest first. */
    private fun newest(lines: List<String>, budget: Int): String {
        val kept = ArrayDeque<String>()
        var size = 0
        for (line in lines.asReversed()) {
            if (size + line.length + 1 > budget) break
            kept.addFirst(line)
            size += line.length + 1
        }
        return kept.joinToString("\n")
    }

    /**
     * Sends [text], trying again once if the network failed (not if the website refused it):
     * the report's ID, or why it failed ([reason] says it briefly). Blocking; call off the main
     * thread. A failure is recorded in Diagnostics, with its reason.
     */
    fun upload(context: Context, text: String, appVersion: String, crash: Boolean = false): Result<String> {
        // A phone is the sender, not the receiver: a report from one is not sent. This
        // also covers crash reports, which go through here ([CrashReports.send]).
        if (DeviceType.isPhone(context)) {
            Diagnostics.record("report", "Not uploaded: this is a phone, not a TV")
            return Result.failure(PhoneNotSupported())
        }
        var result: Result<String> = Result.failure(java.io.IOException("not sent"))
        for (attempt in 1..ATTEMPTS) {
            result = runCatching { send(context, text, appVersion, crash) }
            val error = result.exceptionOrNull() ?: return result
            Log.w(TAG, "Report upload failed (attempt $attempt): ${reason(error)}", error)
            if (error is Refused || attempt == ATTEMPTS) break
            Thread.sleep(2_000)
        }
        result.exceptionOrNull()?.let { Diagnostics.record("report", "Upload failed: ${reason(it)}") }
        return result
    }

    /** A short reason for an upload failure, for the screen and Diagnostics. */
    fun reason(error: Throwable): String = when (error) {
        is PhoneNotSupported -> "this device is a phone, not a TV"
        is TooManyReports -> "too many reports"
        is Refused -> "HTTP ${error.code}"
        is java.net.SocketTimeoutException -> "timed out"
        is java.net.UnknownHostException -> "address not found (DNS)"
        is java.net.ConnectException -> "could not connect"
        is javax.net.ssl.SSLException -> "secure connection failed (${error.javaClass.simpleName})"
        is java.net.SocketException -> "connection broken (${error.message?.take(40)})"
        else -> "${error.javaClass.simpleName}: ${error.message?.take(60)}"
    }

    private fun send(context: Context, text: String, appVersion: String, crash: Boolean): String {
        return Servers.call(context, "/api/reports") { url -> send(url, text, appVersion, crash) }
    }

    private fun send(url: URL, text: String, appVersion: String, crash: Boolean): String {
        val connection = url.openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "text/plain; charset=utf-8")
            connection.setRequestProperty("X-CastBay-Version", appVersion)
            connection.setRequestProperty("X-CastBay-Report", if (crash) "crash" else "manual")
            val bytes = text.toByteArray()
            connection.setFixedLengthStreamingMode(bytes.size)
            connection.outputStream.use { it.write(bytes) }
            val code = connection.responseCode
            if (code == 429) throw TooManyReports()
            if (code != HttpURLConnection.HTTP_CREATED) throw Refused(code)
            val id = JSONObject(connection.inputStream.bufferedReader().use { it.readText() }).getString("id")
            Log.i(TAG, "Report uploaded as $id (${bytes.size} bytes)")
            Diagnostics.record("report", "Uploaded as $id")
            id
        } finally {
            connection.disconnect()
        }
    }

    /** The website answered, but not with a report ID (sending again wouldn't help). */
    open class Refused(code: Int) : Servers.HttpError(code)

    /** This device is a phone: CastBay is a TV receiver, so a report from one is not sent. */
    class PhoneNotSupported : Exception("a phone, not a TV")

    /** The website accepts a few reports a minute from one place. */
    class TooManyReports : Refused(429)

    /** This process's lines in logcat, oldest first; empty if logcat can't be read. */
    private fun logcat(): List<String> = runCatching {
        val pid = Process.myPid()
        // logcat's --pid is Android 7+: Android 6's printed its usage instead. There, every
        // process's recent lines are read and this one's kept.
        val byPid = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N
        val command = mutableListOf("logcat", "-d", "-v", "threadtime", "-t", (if (byPid) LOGCAT_LINES else LOGCAT_LINES * 5).toString())
        if (byPid) command += listOf("--pid", pid.toString())
        val process = ProcessBuilder(command).redirectErrorStream(true).start()
        val lines = process.inputStream.bufferedReader().use { it.readLines() }
        process.destroy()
        processLines(lines, pid)
    }.getOrDefault(emptyList())

    /**
     * The lines of [pid] in threadtime format ("10-04 15:21:53.785  6213  6256 I Tag: …"); anything
     * else, such as logcat's usage or another process's lines, is left out.
     */
    internal fun processLines(lines: List<String>, pid: Int): List<String> {
        val own = pid.toString()
        return lines.filter { line ->
            val fields = line.trim().split(Regex("\\s+"), limit = 4)
            fields.size == 4 && fields[0].length == 5 && fields[0][2] == '-' && fields[2] == own
        }
    }

    // UxPlay logs the PIN a sender must enter, and the like.
    private val secret = Regex("""(?i)\b(pin|password|passwd)(\s*[=:]\s*)"[^"]*"""")
    private val personal = Regex("${Log.PERSONAL_START}[^${Log.PERSONAL_END}\\n]*${Log.PERSONAL_END}")
    private val link = Regex("""\b[a-zA-Z][a-zA-Z0-9+.-]*://[^\s"'<>]+""")
    private val email = Regex("""[\w.+-]+@[\w-]+(\.[\w-]+)+""")
    private val mac = Regex("""\b[0-9A-Fa-f]{2}([:-][0-9A-Fa-f]{2}){5}\b""")
    private val ipv4 = Regex("""\b(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})\b""")
    // Global IPv6 addresses (2000::/3); link-local fe80:: ones say nothing about where one is.
    private val ipv6 = Regex("""\b[23][0-9a-fA-F]{3}(:[0-9a-fA-F]{0,4}){2,7}\b""")

    /**
     * Takes out what could identify the person or what they played: values logged as
     * [Log.personal] (titles, names), PINs and passwords, links, email and hardware addresses, and internet
     * addresses (local network ones, which help find network problems, stay).
     */
    internal fun scrub(text: String): String = text
        .replace(personal, "${Log.PERSONAL_START}…${Log.PERSONAL_END}")
        .replace(secret) { "${it.groupValues[1]}${it.groupValues[2]}\"…\"" }
        .replace(link, "<link>")
        .replace(email, "<email>")
        .replace(mac, "<mac>")
        .replace(ipv6, "<ipv6>")
        .replace(ipv4) { if (isLocal(it.groupValues.drop(1).map(String::toInt))) it.value else "<ip>" }

    private fun isLocal(parts: List<Int>): Boolean {
        val (a, b) = parts
        return parts.any { it > 255 } || // not an address (a version number or the like)
            a == 10 || a == 127 || a == 0 ||
            (a == 172 && b in 16..31) ||
            (a == 192 && b == 168) ||
            (a == 169 && b == 254) ||
            (a == 100 && b in 64..127) // carrier-grade NAT
    }
}
