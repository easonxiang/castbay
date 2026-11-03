package com.weenas.castbay.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper

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

        /** Media keys the system routed here (a steering wheel's, a remote's), for Diagnostics. */
        private fun command(name: String, command: DacpClient.Command) {
            com.weenas.castbay.util.Diagnostics.record("media", "Media session: $name")
            onCommand(command)
        }
    }

    /** Shows [nowPlaying], or releases the session when null. Callable from any thread. */
    fun update(nowPlaying: NowPlaying?) {
        main.post {
            if (nowPlaying == null) {
                release()
                return@post
            }
            val active = session ?: MediaSession(appContext, "CastBay").also {
                it.setCallback(callback, main)
                session = it
            }
            active.setMetadata(metadata(nowPlaying))
            active.setPlaybackState(playbackState(nowPlaying))
            active.isActive = true
        }
    }

    private fun release() {
        session?.let {
            it.isActive = false
            it.release()
        }
        session = null
        lastCover = null
    }

    private fun metadata(nowPlaying: NowPlaying): MediaMetadata {
        val builder = MediaMetadata.Builder()
            .putString(
                MediaMetadata.METADATA_KEY_TITLE,
                // The system shows it (e.g. the TV's now playing card), in the app's language.
                nowPlaying.title ?: AppLanguage.wrap(appContext).getString(com.weenas.castbay.R.string.now_playing_untitled)
            )
            .putString(MediaMetadata.METADATA_KEY_ARTIST, nowPlaying.artist)
            .putString(MediaMetadata.METADATA_KEY_ALBUM, nowPlaying.album)
            .putLong(MediaMetadata.METADATA_KEY_DURATION, (nowPlaying.durationSec * 1000).toLong())
        coverBitmap(nowPlaying.coverArt)?.let { builder.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, it) }
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
    }
}
