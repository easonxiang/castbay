package com.weenas.castbay.util

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.os.SystemClock
import android.provider.Settings
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A short history of what happened to the app, kept on the device and shown on the
 * Diagnostics screen (About → Diagnostics), for devices without adb such as car head units:
 * when the process started (and whether the device had just booted), boot broadcasts, the
 * receiver service starting and stopping, keys and media-session commands, taps, and audio
 * focus. Nothing leaves the device; the person reads it on screen (or photographs it).
 */
object Diagnostics {
    data class Event(val atMs: Long, val kind: String, val text: String)

    private const val PREFS = "diagnostics"
    private const val KEY_EVENTS = "events"
    private const val KEY_LAST_EXIT = "last_exit"
    private const val MAX_EVENTS = 120
    private val timeFormat = SimpleDateFormat("MM-dd HH:mm:ss", Locale.US)

    @Volatile private var appContext: Context? = null
    private val events = ArrayDeque<Event>()
    private var loaded = false

    /** Remembers the context (any) and notes this process's start, with how long the device has been up. */
    fun init(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        Log.init(context)
        CrashReports.install(context)
        UsageStats.init(context)
        val uptimeMin = SystemClock.elapsedRealtime() / 60_000
        record("process", "Started (pid ${Process.myPid()}), device up $uptimeMin min, boot #${bootCount() ?: "?"}")
        recordLastExit(context)
        recordUnfinishedRun(context)
    }

    /**
     * Whether the previous process ended while its receiver ran, in this boot, without
     * CastBay stopping it: a crash, or Android ending it. Android 6 to 10 say no more than
     * that (Android 11+ say why, [recordLastExit]); a problem report then adds that run's
     * last log lines ([Log.previousRun]).
     */
    @Volatile var previousRunUnfinished = false
        private set

    private fun runMarker(context: Context) = java.io.File(context.filesDir, "receiver-running")

    private fun recordUnfinishedRun(context: Context) {
        val marker = runMarker(context)
        val bootOfRun = runCatching { marker.takeIf { it.exists() }?.readText()?.trim() }.getOrNull() ?: return
        marker.delete()
        // Switched off or restarted: the receiver didn't fail.
        if (bootOfRun != bootId()) return
        previousRunUnfinished = true
        record("process", "The previous run ended while receiving, without CastBay stopping it: a crash, or Android ended it")
    }

    /** The receiver is running (service created) or stopped on purpose (destroyed). */
    fun receiverRunning(running: Boolean) {
        val context = appContext ?: return
        val marker = runMarker(context)
        if (running) runCatching { marker.writeText(bootId()) } else marker.delete()
    }

    /**
     * This boot's ID, from the kernel; where it can't be read, when it began (to the minute:
     * the clock may be set after starting).
     */
    private fun bootId(): String =
        runCatching { java.io.File("/proc/sys/kernel/random/boot_id").readText().trim() }.getOrNull()?.takeIf { it.isNotEmpty() }
            ?: ((System.currentTimeMillis() - SystemClock.elapsedRealtime()) / 600_000).toString()

    /**
     * Why the previous process ended (Android 11+): killed by the system, stopped by the
     * person or a cleaner app ("user stopped": no broadcasts or jobs reach the app until it is
     * opened again), low memory, a crash… Each exit is recorded once.
     */
    private fun recordLastExit(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val am = context.getSystemService(android.app.ActivityManager::class.java) ?: return
        val last = runCatching { am.getHistoricalProcessExitReasons(context.packageName, 0, 1).firstOrNull() }.getOrNull() ?: return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getLong(KEY_LAST_EXIT, 0) == last.timestamp) return
        prefs.edit().putLong(KEY_LAST_EXIT, last.timestamp).apply()
        val reason = when (last.reason) {
            android.app.ApplicationExitInfo.REASON_USER_STOPPED -> "stopped (force stop)"
            android.app.ApplicationExitInfo.REASON_USER_REQUESTED -> "removed by the person"
            android.app.ApplicationExitInfo.REASON_LOW_MEMORY -> "low memory"
            android.app.ApplicationExitInfo.REASON_SIGNALED -> "killed (signal ${last.status})"
            android.app.ApplicationExitInfo.REASON_CRASH, android.app.ApplicationExitInfo.REASON_CRASH_NATIVE -> "crashed"
            android.app.ApplicationExitInfo.REASON_ANR -> "not responding"
            android.app.ApplicationExitInfo.REASON_EXIT_SELF -> "exited by itself"
            android.app.ApplicationExitInfo.REASON_OTHER -> "other"
            else -> "reason ${last.reason}"
        }
        CrashReports.noteExit(context, last)
        val at = timeFormat.format(Date(last.timestamp))
        record("process", "Previous one ended $at: $reason${last.description?.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: ""}")
    }

    /** Adds an event; [kind] groups them ("boot", "service", "key", "touch", "focus", …). Any thread. */
    @Synchronized
    fun record(kind: String, text: String) {
        loadLocked()
        events.addLast(Event(System.currentTimeMillis(), kind, text))
        while (events.size > MAX_EVENTS) events.removeFirst()
        saveLocked()
    }

    /** Newest first. */
    @Synchronized
    fun events(): List<Event> {
        loadLocked()
        return events.reversed()
    }

    @Synchronized
    fun clear() {
        events.clear()
        loaded = true
        saveLocked()
    }

    fun format(event: Event): String = "${timeFormat.format(Date(event.atMs))}  ${event.kind}  ${event.text}"

    /** What the device is: for the Diagnostics screen and the compatibility list. */
    fun deviceInfo(context: Context): List<Pair<String, String>> {
        val metrics = context.resources.displayMetrics
        val config = context.resources.configuration
        val touch = context.packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)
        return listOf(
            "Device" to "${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})",
            "Android" to "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), ${Build.DISPLAY}",
            "Screen" to "${metrics.widthPixels}×${metrics.heightPixels} px, ${config.screenWidthDp}×${config.screenHeightDp} dp, " +
                "density ${metrics.density}" + if (touch) ", touch screen" else "",
            "Memory" to memoryInfo(context),
            "App" to "${AppVersion.name(context)} (${context.packageName})",
            "Up for" to "${SystemClock.elapsedRealtime() / 60_000} min, boot #${bootCount() ?: "?"}",
        )
    }

    /** The device's RAM and how much of it this app may use, to read out-of-memory crashes by. */
    private fun memoryInfo(context: Context): String {
        val activities = context.getSystemService(ActivityManager::class.java)
        val total = ActivityManager.MemoryInfo().also { activities.getMemoryInfo(it) }.totalMem
        val heap = Runtime.getRuntime().maxMemory()
        return "RAM ${"%.1f".format(Locale.US, total / 1e9)} GB" +
            (if (activities.isLowRamDevice) " (low-RAM device)" else "") + ", app heap ${heap shr 20} MB"
    }

    private fun bootCount(): Int? = appContext?.let {
        runCatching { Settings.Global.getInt(it.contentResolver, Settings.Global.BOOT_COUNT) }.getOrNull()
    }

    private fun loadLocked() {
        if (loaded) return
        val context = appContext ?: return
        loaded = true
        val stored = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_EVENTS, null) ?: return
        runCatching {
            val array = JSONArray(stored)
            val older = (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                Event(o.getLong("t"), o.getString("k"), o.getString("x"))
            }
            // Events recorded before the context was known come after the stored ones.
            val newer = events.toList()
            events.clear()
            events.addAll(older)
            events.addAll(newer)
            while (events.size > MAX_EVENTS) events.removeFirst()
        }
    }

    private fun saveLocked() {
        val context = appContext ?: return
        if (!loaded) return
        val array = JSONArray()
        events.forEach { array.put(JSONObject().put("t", it.atMs).put("k", it.kind).put("x", it.text)) }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_EVENTS, array.toString()).apply()
    }
}
