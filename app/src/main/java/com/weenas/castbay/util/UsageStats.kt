package com.weenas.castbay.util

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import com.weenas.castbay.service.ReceiverSettingsStore
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Anonymous usage statistics, only while the person has turned them on (Settings → General,
 * off by default): counts for the day (casts by protocol and kind, how long they lasted in
 * bands, mirroring resolution and codec, failures), sent once a day as one summary with the
 * device's model, Android version, screen class and the settings in use, under a random ID
 * that is made when the switch is turned on and forgotten (and deleted on the website) when it
 * is turned off. Nothing is counted while it is off. See docs/design/usage-stats-and-reports.md
 * and the privacy policy for every field.
 */
object UsageStats {
    private const val TAG = "CastBayStats"
    private const val PREFS = "usage_stats"
    private const val KEY_ID = "id"
    private const val KEY_DAYS = "days"
    private const val KEEP_DAYS = 7
    private const val TIMEOUT_MS = 20_000
    private val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val sending = AtomicBoolean(false)

    @Volatile private var appContext: Context? = null
    @Volatile private var castStartMs = 0L

    fun init(context: Context) {
        if (appContext == null) appContext = context.applicationContext
    }

    fun isOn(context: Context): Boolean = prefs(context).contains(KEY_ID)

    /** Turning on makes a new ID; turning off forgets it and the counts, and asks the website to delete its rows. */
    fun setOn(context: Context, on: Boolean) {
        val prefs = prefs(context)
        if (on) {
            if (!prefs.contains(KEY_ID)) prefs.edit().putString(KEY_ID, UUID.randomUUID().toString()).remove(KEY_DAYS).apply()
            return
        }
        val id = prefs.getString(KEY_ID, null)
        prefs.edit().clear().apply()
        if (id != null) {
            val app = context.applicationContext
            kotlin.concurrent.thread(name = "CastBay-stats-delete") {
                runCatching { post(app, "/api/stats/delete", JSONObject().put("id", id).toString()) }
                    .onSuccess { Log.i(TAG, "Statistics deleted on the website") }
                    .onFailure { Log.w(TAG, "Deleting statistics failed: ${it.message}") }
            }
        }
    }

    /** A cast started: "airplay"/"dlna" and "mirror"/"music"/"video". */
    fun castStarted(protocol: String, kind: String) {
        castStartMs = System.currentTimeMillis()
        count("cast.$protocol.$kind")
    }

    /** The cast ended: its length, in bands only. */
    fun castEnded() {
        val start = castStartMs.takeIf { it > 0 } ?: return
        castStartMs = 0
        val minutes = (System.currentTimeMillis() - start) / 60_000
        count(when {
            minutes < 1 -> "length.under1m"
            minutes < 10 -> "length.1to10m"
            else -> "length.over10m"
        })
    }

    /** A mirroring session's picture: its height class and codec. */
    fun mirroring(height: Int, h265: Boolean) =
        count("mirror.${heightClass(height)}.${if (h265) "h265" else "h264"}")

    /** Something failed: "receiver", "decoder", "video". */
    fun failure(kind: String) = count("fail.$kind")

    @Synchronized
    private fun count(key: String) {
        val context = appContext ?: return
        val prefs = prefs(context)
        if (!prefs.contains(KEY_ID)) return
        val days = runCatching { JSONObject(prefs.getString(KEY_DAYS, "{}")!!) }.getOrDefault(JSONObject())
        val today = dayFormat.format(Date())
        val counts = days.optJSONObject(today) ?: JSONObject().also { days.put(today, it) }
        counts.put(key, counts.optInt(key) + 1)
        prefs.edit().putString(KEY_DAYS, days.toString()).apply()
    }

    /**
     * Sends each finished day's summary (not today's, still counting), at most the last week's;
     * a day is kept until the website has it. Blocking; call off the main thread.
     */
    fun sendFinishedDays(context: Context, includeToday: Boolean = false) {
        val prefs = prefs(context)
        val id = prefs.getString(KEY_ID, null) ?: return
        if (!sending.compareAndSet(false, true)) return
        try {
            val days = runCatching { JSONObject(prefs.getString(KEY_DAYS, "{}")!!) }.getOrDefault(JSONObject())
            val today = dayFormat.format(Date())
            val oldest = dayFormat.format(Date(System.currentTimeMillis() - KEEP_DAYS * 86_400_000L))
            for (day in days.keys().asSequence().toList().sorted()) {
                if (day > today || (day == today && !includeToday)) continue
                if (day < oldest) {
                    forget(context, day)
                    continue
                }
                val body = summary(context, id, day, days.getJSONObject(day))
                val sent = runCatching { post(context, "/api/stats", body.toString()) }
                    .onFailure { Log.w(TAG, "Statistics for $day not sent: ${it.message}") }
                if (sent.isFailure) break
                forget(context, day)
                Log.i(TAG, "Statistics for $day sent")
            }
        } finally {
            sending.set(false)
        }
    }

    /** For tests (tools/sim stats): whether it is on, and the days kept. */
    fun describe(context: Context): String =
        "on=${isOn(context)}, days=${prefs(context).getString(KEY_DAYS, "{}")}"

    @Synchronized
    private fun forget(context: Context, day: String) {
        val prefs = prefs(context)
        val days = runCatching { JSONObject(prefs.getString(KEY_DAYS, "{}")!!) }.getOrDefault(JSONObject())
        days.remove(day)
        prefs.edit().putString(KEY_DAYS, days.toString()).apply()
    }

    /** Exactly what is sent for [day]; every field is listed in the privacy policy. */
    internal fun summary(context: Context, id: String, day: String, counts: JSONObject): JSONObject {
        val settings = ReceiverSettingsStore(context).load()
        val metrics = context.resources.displayMetrics
        return JSONObject()
            .put("id", id)
            .put("day", day)
            .put("app", AppVersion.name(context))
            .put("android", Build.VERSION.RELEASE)
            .put("sdk", Build.VERSION.SDK_INT)
            .put("maker", Build.MANUFACTURER.take(40))
            .put("model", Build.MODEL.take(40))
            .put("screen", heightClass(minOf(metrics.widthPixels, metrics.heightPixels)))
            .put("device", deviceKind(context))
            .put("touch", context.packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN))
            .put("lang", Locale.getDefault().language.take(8))
            .put("counts", counts)
            .put("settings", JSONObject()
                .put("access", settings.access)
                .put("resolution", settings.resolution)
                .put("codec", settings.videoCodec)
                .put("dlna", settings.dlnaEnabled)
                .put("startOnBoot", settings.startOnBoot)
                .put("lyrics", settings.showLyrics)
                .put("stats", settings.showStats)
                .put("picture", settings.pictureMode)
                .put("language", settings.language)
                .put("updates", settings.checkUpdates)
                .put("errorReports", settings.sendErrorReports))
    }

    private fun heightClass(height: Int): String = when {
        height >= 2000 -> "2160p"
        height >= 1300 -> "1440p"
        height >= 1000 -> "1080p"
        height >= 700 -> "720p"
        else -> "smaller"
    }

    private fun deviceKind(context: Context): String {
        val uiMode = context.resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK
        val pm = context.packageManager
        return when {
            uiMode == Configuration.UI_MODE_TYPE_TELEVISION || pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK) -> "tv"
            uiMode == Configuration.UI_MODE_TYPE_CAR || pm.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE) -> "car"
            else -> "other"
        }
    }

    private fun post(context: Context, path: String, body: String) = Servers.call(context, path) { url -> post(url, body) }

    private fun post(url: URL, body: String) {
        val connection = url.openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            val bytes = body.toByteArray()
            connection.setFixedLengthStreamingMode(bytes.size)
            connection.outputStream.use { it.write(bytes) }
            val code = connection.responseCode
            if (code !in 200..299) throw Servers.HttpError(code)
        } finally {
            connection.disconnect()
        }
    }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
