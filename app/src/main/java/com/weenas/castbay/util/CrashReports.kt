package com.weenas.castbay.util

import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * What went wrong when CastBay last stopped unexpectedly, kept on the device until it is sent
 * as a problem report ([LogReport]) or dismissed: a Java crash's stack trace with the log lines
 * before it (caught as it happens), or, on Android 11+, a "not responding" or native crash from
 * Android's record of the exit (with its trace, for not responding). Nothing is sent from here:
 * the person either turned on Send error reports, or is asked on the home screen.
 */
object CrashReports {
    private const val TAG = "CastBayCrash"
    private const val FILE = "crash-pending.txt"
    // The log lines kept with a crash; the report adds no more than LogReport's limit anyway.
    private const val LOG_LINES = 800
    private const val MAX_TRACE_CHARS = 60_000
    private const val MAX_TOMBSTONE_BYTES = 4 * 1024 * 1024
    private val timeFormat = SimpleDateFormat("MM-dd HH:mm:ss", Locale.US)
    private val installed = AtomicBoolean(false)
    private val sending = AtomicBoolean(false)

    /** Catches uncaught exceptions from now on (once per process), then lets Android end it. */
    fun install(context: Context) {
        if (!installed.compareAndSet(false, true)) return
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { save(appContext, crashText(thread, error)) }
            previous?.uncaughtException(thread, error)
        }
    }

    /**
     * Notes a "not responding" or native crash that Android recorded for [exit] (Android 11+),
     * unless a Java crash was already caught for it.
     */
    fun noteExit(context: Context, exit: ApplicationExitInfo) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val kind = when (exit.reason) {
            ApplicationExitInfo.REASON_ANR -> "Not responding"
            ApplicationExitInfo.REASON_CRASH_NATIVE -> "Native crash"
            else -> return
        }
        if (pending(context) != null) return
        val trace = when (exit.reason) {
            ApplicationExitInfo.REASON_ANR ->
                runCatching { exit.traceInputStream?.bufferedReader()?.use { it.readText() } }.getOrNull()
            // The tombstone (a protobuf): only its signal and the crashing thread's frames are kept.
            else -> runCatching { exit.traceInputStream?.use { readUpTo(it, MAX_TOMBSTONE_BYTES) } }.getOrNull()
                ?.let(NativeCrashTrace::summarize)
        }
        save(context, buildString {
            appendLine("$kind at ${timeFormat.format(Date(exit.timestamp))}: ${exit.description.orEmpty()}")
            if (trace != null) {
                appendLine()
                append(trace.take(MAX_TRACE_CHARS))
            }
        })
    }

    /** The unsent crash, if any. */
    fun pending(context: Context): String? =
        file(context).takeIf { it.exists() }?.let { runCatching { it.readText() }.getOrNull() }

    fun clear(context: Context) {
        file(context).delete()
    }

    /**
     * Sends the pending crash as a problem report (off the main thread), once per process:
     * the report's ID, or why not. It is kept for a later try if sending fails.
     */
    fun send(context: Context, appVersion: String): Result<String>? {
        val crash = pending(context) ?: return null
        if (!sending.compareAndSet(false, true)) return null
        return try {
            LogReport.upload(context, LogReport.build(context, crash), appVersion, crash = true)
                .onSuccess {
                    clear(context)
                    Diagnostics.record("report", "Crash report sent as $it")
                }
        } finally {
            sending.set(false)
        }
    }

    private fun readUpTo(input: java.io.InputStream, limit: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (out.size() < limit) {
            val n = input.read(buffer, 0, minOf(buffer.size, limit - out.size()))
            if (n < 0) break
            out.write(buffer, 0, n)
        }
        return out.toByteArray()
    }

    private fun crashText(thread: Thread, error: Throwable): String = buildString {
        appendLine("Crash at ${timeFormat.format(Date())} in thread \"${thread.name}\": ${error.javaClass.name}")
        appendLine(StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString().take(MAX_TRACE_CHARS))
        appendLine("== Log before the crash ==")
        Log.recent().takeLast(LOG_LINES).forEach { appendLine(it) }
    }

    private fun save(context: Context, text: String) {
        file(context).writeText(text)
        Diagnostics.record("process", "Stopped unexpectedly: ${text.lineSequence().first().take(120)}")
        Log.e(TAG, "Saved a crash for the next start")
    }

    private fun file(context: Context) = File(context.filesDir, FILE)
}
