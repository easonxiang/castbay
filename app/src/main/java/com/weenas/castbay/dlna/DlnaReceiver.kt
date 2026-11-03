package com.weenas.castbay.dlna

import android.content.Context
import com.weenas.castbay.util.Log
import java.util.UUID

/**
 * CastBay as a DLNA media renderer, next to AirPlay: video apps' own "cast" buttons (Bilibili,
 * iQiyi, Youku, ...) find it and hand it a media URL to play.
 */
class DlnaReceiver(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("dlna_identity", Context.MODE_PRIVATE)
    private var http: DlnaHttpServer? = null
    private var events: DlnaEvents? = null
    private var ssdp: SsdpServer? = null

    /** Stable across restarts, so control points keep recognising the TV. */
    private val uuid: String by lazy {
        preferences.getString(KEY_UUID, null) ?: UUID.randomUUID().toString().also {
            preferences.edit().putString(KEY_UUID, it).apply()
        }
    }

    @Synchronized
    fun start(name: String, target: DlnaRenderer.Target) {
        stop()
        val friendlyName = name.trim().ifBlank { "CastBay" }
        val renderer = DlnaRenderer(target)
        val eventing = DlnaEvents(renderer)
        val server = DlnaHttpServer(renderer, eventing) { UpnpDescriptions.device(friendlyName, uuid) }
        try {
            // The same port as last time, so control points' cached descriptions stay valid.
            val port = server.start(preferences.getInt(KEY_PORT, 0))
            preferences.edit().putInt(KEY_PORT, port).apply()
            http = server
            events = eventing.also { it.start() }
            ssdp = SsdpServer(uuid) { server.port }.also { it.start() }
            Log.i(TAG, "DLNA renderer \"$friendlyName\" on port $port (uuid $uuid)")
        } catch (error: Exception) {
            Log.e(TAG, "Could not start the DLNA renderer", error)
            stop()
        }
    }

    @Synchronized
    fun stop() {
        ssdp?.stop()
        http?.stop()
        events?.stop()
        ssdp = null
        http = null
        events = null
    }

    private companion object {
        const val TAG = "CastBayDlna"
        const val KEY_UUID = "uuid"
        const val KEY_PORT = "http_port"
    }
}
