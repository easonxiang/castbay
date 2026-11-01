package com.weenas.castbay.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import com.weenas.castbay.MainActivity
import com.weenas.castbay.receiver.CarMediaButtonReceiver
import com.weenas.castbay.util.Diagnostics

/**
 * A media session for audio streaming, so the TV remote's media keys control the sender
 * (via [onCommand], e.g. DACP) and the system can show what is playing.
 */
class NowPlayingSession(
    context: Context,
    private val onCommand: (DacpClient.Command) -> Unit
) {
    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())

    /** Main thread only. */
    private var session: MediaSession? = null
    private var lastCover: ByteArray? = null

    private val callback = object : MediaSession.Callback() {
        override fun onPlay() = command("play", DacpClient.Command.PLAY)
        override fun onPause() = command("pause", DacpClient.Command.PAUSE)
        override fun onStop() = command("stop", DacpClient.Command.PAUSE)
        override fun onSkipToNext() = command("next", DacpClient.Command.NEXT)
        override fun onSkipToPrevious() = command("previous", DacpClient.Command.PREVIOUS)

        override fun onPlayFromMediaId(mediaId: String?, extras: android.os.Bundle?) {
            Diagnostics.record("media", "Media session: play from media id $mediaId (a media resume)")
            com.weenas.castbay.receiver.ReceiverStarter.start(appContext, "media resume")
        }

        override fun onMediaButtonEvent(mediaButtonIntent: Intent): Boolean {
            val key = mediaButtonIntent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT)
            Diagnostics.record("media", "Media session: button ${key?.let { KeyEvent.keyCodeToString(it.keyCode) + " action " + it.action }}")
            return super.onMediaButtonEvent(mediaButtonIntent)
        }

        /** Media keys the system routed here (a steering wheel's, a remote's), for Diagnostics. */
        private fun command(name: String, command: DacpClient.Command) {
            com.weenas.castbay.util.Diagnostics.record("media", "Media session: $name")
            onCommand(command)
        }
    }

    /**
     * The session, created on first use and kept for the life of the process: the media browser
     * service ([CarMediaService]) hands its token out before any music plays. Main thread only.
     */
    private fun ensureSession(): MediaSession = session ?: MediaSession(appContext, "CastBay").also {
        it.setCallback(callback, main)
        // What a media card's tap opens, and where a media button goes while no session is
        // active or the process is gone (a car, a steering wheel's key after it woke up).
        it.setSessionActivity(PendingIntent.getActivity(appContext, 0, Intent(appContext, MainActivity::class.java), IMMUTABLE))
        it.setMediaButtonReceiver(PendingIntent.getBroadcast(
            appContext, 0,
            Intent(Intent.ACTION_MEDIA_BUTTON).setComponent(ComponentName(appContext, CarMediaButtonReceiver::class.java)),
            // The system adds the key to the intent it sends, which Android 12+ allows only if mutable.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        ))
        it.setPlaybackState(PlaybackState.Builder().setActions(PlaybackState.ACTION_PLAY).setState(PlaybackState.STATE_NONE, 0, 0f).build())
        session = it
        Diagnostics.record("media", "Media session created")
    }

    /** For [CarMediaService]; main thread only. */
    fun token(): MediaSession.Token = ensureSession().sessionToken

    /** A key a media notification's button or [CarMediaButtonReceiver] received. */
    fun handleKey(keyCode: Int) {
        Diagnostics.record("media", "Media key ${KeyEvent.keyCodeToString(keyCode)}")
        when (keyCode) {
            KeyEvent.KEYCODE_MEDIA_PLAY -> callback.onPlay()
            KeyEvent.KEYCODE_MEDIA_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_HEADSETHOOK -> callback.onPause()
            KeyEvent.KEYCODE_MEDIA_STOP -> callback.onStop()
            KeyEvent.KEYCODE_MEDIA_NEXT -> callback.onSkipToNext()
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> callback.onSkipToPrevious()
        }
    }

    /** Shows [nowPlaying], or hides it when null. Callable from any thread. */
    fun update(nowPlaying: NowPlaying?) {
        main.post {
            if (nowPlaying == null) {
                hide()
                return@post
            }
            val active = ensureSession()
            active.setMetadata(metadata(nowPlaying))
            active.setPlaybackState(playbackState(nowPlaying))
            latest = nowPlaying
            val wasActive = active.isActive
            active.isActive = true
            notify(nowPlaying, active)
            broadcastLegacy(nowPlaying)
            if (!wasActive) {
                // A display that attaches as the session becomes active may read before the metadata
                // lands: send it again a moment later, and say so in Diagnostics.
                Diagnostics.record("media", "Media session active: ${nowPlaying.title} / ${nowPlaying.artist}, cover ${lastBitmap != null}")
                for (delay in REPUBLISH_MS) main.postDelayed({
                    val current = latest
                    if (active.isActive && current != null) active.setMetadata(metadata(current))
                }, delay)
            }
        }
    }

    private fun hide() {
        latest = null
        session?.let {
            it.isActive = false
            it.setPlaybackState(PlaybackState.Builder().setActions(PlaybackState.ACTION_PLAY).setState(PlaybackState.STATE_NONE, 0, 0f).build())
        }
        lastCover = null
        lastBitmap = null
        if (notified) {
            notified = false
            AirPlayService.restoreNotification(appContext)
            appContext.sendBroadcast(Intent(LEGACY_PLAYSTATE).putExtra("playing", false))
        }
    }

    private var notified = false

    /** The last thing shown, for the delayed metadata sends. Main thread only. */
    private var latest: NowPlaying? = null

    /**
     * The service's foreground notification, as a media notification tied to the session: what
     * Android 11+ media controls, and car and cluster displays that read notifications, show.
     */
    private fun notify(nowPlaying: NowPlaying, active: MediaSession) {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            Notification.Builder(appContext, AirPlayService.CHANNEL_ID)
        else @Suppress("DEPRECATION") Notification.Builder(appContext)
        val playPause = if (nowPlaying.playing) KeyEvent.KEYCODE_MEDIA_PAUSE else KeyEvent.KEYCODE_MEDIA_PLAY
        builder
            .setContentTitle(nowPlaying.title ?: "AirPlay audio")
            .setContentText(nowPlaying.artist)
            .setSubText(nowPlaying.album)
            .setSmallIcon(com.weenas.castbay.R.drawable.ic_notification)
            .setLargeIcon(lastBitmap)
            .setContentIntent(PendingIntent.getActivity(appContext, 0, Intent(appContext, MainActivity::class.java), IMMUTABLE))
            .setOngoing(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .addAction(action(android.R.drawable.ic_media_previous, "Previous", KeyEvent.KEYCODE_MEDIA_PREVIOUS))
            .addAction(action(
                if (nowPlaying.playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (nowPlaying.playing) "Pause" else "Play", playPause
            ))
            .addAction(action(android.R.drawable.ic_media_next, "Next", KeyEvent.KEYCODE_MEDIA_NEXT))
            .setStyle(Notification.MediaStyle().setMediaSession(active.sessionToken).setShowActionsInCompactView(0, 1, 2))
        appContext.getSystemService(NotificationManager::class.java).notify(AirPlayService.NOTIFICATION_ID, builder.build())
        notified = true
    }

    private fun action(icon: Int, title: String, keyCode: Int): Notification.Action {
        val intent = Intent(Intent.ACTION_MEDIA_BUTTON)
            .setComponent(ComponentName(appContext, CarMediaButtonReceiver::class.java))
            .putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        return Notification.Action.Builder(
            Icon.createWithResource(appContext, icon), title,
            PendingIntent.getBroadcast(appContext, keyCode, intent, IMMUTABLE)
        ).build()
    }

    /** The broadcasts old music players sent; some car and widget displays still listen for them. */
    private fun broadcastLegacy(nowPlaying: NowPlaying) {
        val extras = { intent: Intent ->
            intent.putExtra("id", 1L)
                .putExtra("artist", nowPlaying.artist)
                .putExtra("album", nowPlaying.album)
                .putExtra("track", nowPlaying.title)
                .putExtra("playing", nowPlaying.playing)
                .putExtra("duration", (nowPlaying.durationSec * 1000).toLong())
                .putExtra("position", (nowPlaying.currentPositionSec() * 1000).toLong())
        }
        if (nowPlaying.title != lastLegacyTitle) {
            lastLegacyTitle = nowPlaying.title
            appContext.sendBroadcast(extras(Intent(LEGACY_META)))
            Diagnostics.record("media", "Sent the legacy track broadcast")
        }
        appContext.sendBroadcast(extras(Intent(LEGACY_PLAYSTATE)))
    }

    private var lastLegacyTitle: String? = null

    private fun metadata(nowPlaying: NowPlaying): MediaMetadata {
        val title = nowPlaying.title ?: "AirPlay audio"
        val builder = MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_MEDIA_ID, "castbay-now-playing")
            .putString(MediaMetadata.METADATA_KEY_TITLE, title)
            .putString(MediaMetadata.METADATA_KEY_ARTIST, nowPlaying.artist)
            .putString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST, nowPlaying.artist)
            .putString(MediaMetadata.METADATA_KEY_ALBUM, nowPlaying.album)
            // What media UIs show for an item (the car's displays may read these, not the plain ones).
            .putString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE, title)
            .putString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE, nowPlaying.artist)
            .putString(MediaMetadata.METADATA_KEY_DISPLAY_DESCRIPTION, nowPlaying.album)
            .putLong(MediaMetadata.METADATA_KEY_DURATION, (nowPlaying.durationSec * 1000).toLong())
        coverBitmap(nowPlaying.coverArt)?.let {
            builder.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, it)
                .putBitmap(MediaMetadata.METADATA_KEY_ART, it)
                .putBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON, it)
        }
        return builder.build()
    }

    private var lastBitmap: Bitmap? = null

    /** Decodes (and caches) the cover, downscaled: metadata crosses a binder transaction. */
    private fun coverBitmap(cover: ByteArray?): Bitmap? {
        if (cover == null) return null
        if (cover.contentEquals(lastCover)) return lastBitmap
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(cover, 0, cover.size, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_ART_SIZE) sample *= 2
        lastBitmap = BitmapFactory.decodeByteArray(cover, 0, cover.size,
            BitmapFactory.Options().apply { inSampleSize = sample })
        lastCover = cover
        return lastBitmap
    }

    private fun playbackState(nowPlaying: NowPlaying): PlaybackState =
        PlaybackState.Builder()
            .setActions(
                PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or
                    PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_STOP or
                    PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS
            )
            .setState(
                if (nowPlaying.playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
                (nowPlaying.positionSec * 1000).toLong(),
                if (nowPlaying.playing) 1f else 0f,
                nowPlaying.positionAtMs
            )
            .build()

    private companion object {
        const val MAX_ART_SIZE = 512
        val REPUBLISH_MS = listOf(1500L, 5000L)
        const val LEGACY_META = "com.android.music.metachanged"
        const val LEGACY_PLAYSTATE = "com.android.music.playstatechanged"
        const val IMMUTABLE = PendingIntent.FLAG_IMMUTABLE
    }
}
