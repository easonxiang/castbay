package com.weenas.castbay.debug

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.SystemClock
import android.view.Surface
import com.weenas.castbay.service.AirPlayConnectionState
import com.weenas.castbay.service.AirPlayManager
import com.weenas.castbay.service.SenderReport
import com.weenas.castbay.service.StreamInfo
import com.weenas.castbay.util.Log
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * Plays the part of an AirPlay sender in debug builds, so the app can be tested without a
 * phone: it calls the protocol layer's callbacks ([AirPlayManager.nativeBridge]) in the order
 * and at the pace a phone's session produces them. Everything above the protocol runs for
 * real: admission, decoding, timed music playback, the screens, the menus and the stats.
 * Only the network, pairing and encryption are skipped.
 *
 * - Mirroring: a test picture (a clock, a frame counter and a moving block) encoded to H.264
 *   on the device, with the sender's once-a-second stats report.
 * - Music: a generated tune with title, artist, album and cover, sent two seconds ahead of
 *   when it is to be heard, as an iPhone does; pause and resume.
 * - Video: a URL for the TV to play, as apps' AirPlay video does.
 *
 * One session at a time; a new command replaces the running one, and the TV ending the cast
 * (Back twice) stops it, as it disconnects a phone.
 */
class SimulatedSender private constructor(context: Context) {

    data class Sender(val deviceId: String, val name: String, val model: String)

    private val manager = AirPlayManager.getInstance(context)
    private val bridge get() = manager.nativeBridge

    /** The running session; replaced (and so stopped) by the next one. */
    @Volatile private var session: Session? = null

    init {
        manager.registerStateCallback { state, stream, _ -> session?.onState(state, stream) }
    }

    fun mirror(sender: Sender, seconds: Int, width: Int, height: Int, fps: Int) =
        begin(sender) { MirrorSession(sender, seconds, width, height, fps) }

    fun music(sender: Sender, seconds: Int, title: String, artist: String, album: String) =
        begin(sender) { MusicSession(sender, seconds, title, artist, album) }

    fun video(sender: Sender, url: String) = begin(sender) { VideoSession(sender, url) }

    fun pauseMusic() = (session as? MusicSession)?.setPaused(true) ?: Log.w(TAG, "No music playing")
    fun resumeMusic() = (session as? MusicSession)?.setPaused(false) ?: Log.w(TAG, "No music playing")
    /** The network holds the music up for [ms], then delivers what was held, late. */
    fun stallMusic(ms: Long) = (session as? MusicSession)?.stall(ms) ?: Log.w(TAG, "No music playing")

    /** The sender stops casting (as a phone that disconnects). */
    fun stop() {
        session?.finish(senderEnded = true) ?: Log.i(TAG, "Nothing to stop")
    }

    private fun begin(sender: Sender, create: () -> Session) {
        session?.finish(senderEnded = true)
        // A session starts as a phone's does: a connection, then the sender asks to be let in.
        bridge.onConnectionStarted()
        if (!bridge.onClient(sender.deviceId, sender.name, sender.model)) {
            Log.i(TAG, "Refused by the TV: ${sender.name} (as a phone would be: it tries again later)")
            return
        }
        create().also { session = it }.start()
    }

    private abstract inner class Session(val sender: Sender) {
        @Volatile var running = true
            private set
        private var streamed = false
        private val sending = Any()

        abstract fun start()

        /**
         * Sends [data] unless the session has ended: nothing reaches the app after the sender
         * said it has gone, as with a phone, whose connection is closed by then.
         */
        fun send(data: () -> Unit) = synchronized(sending) { if (running) data() }

        /** Stops sending; [senderEnded] tells the app the sender went (else the TV ended it). */
        open fun finish(senderEnded: Boolean) {
            synchronized(sending) {
                if (!running) return
                running = false
            }
            if (session === this) session = null
            if (senderEnded) {
                Log.i(TAG, "${javaClass.simpleName} ended by the sender")
                bridge.onSessionEnd()
            } else {
                Log.i(TAG, "${javaClass.simpleName} ended by the TV")
            }
        }

        /** The TV ending the cast leaves the Streaming state: stop, as a disconnected phone does. */
        fun onState(state: AirPlayConnectionState, stream: StreamInfo) {
            if (state == AirPlayConnectionState.Streaming) streamed = true
            else if (streamed && running && state != AirPlayConnectionState.Connecting) finish(senderEnded = false)
        }

        /** Mirroring and music senders beat every two seconds, even while paused. */
        fun heartbeats() = thread(name = "sim-heartbeat") {
            while (running) {
                send { bridge.onFeedback() }
                sleepMs(2000)
            }
        }
    }

    private inner class MirrorSession(
        sender: Sender,
        private val seconds: Int,
        private val width: Int,
        private val height: Int,
        private val fps: Int
    ) : Session(sender) {
        @Volatile private var sentFrames = 0
        @Volatile private var sentBytes = 0L

        override fun start() {
            Log.i(TAG, "Mirroring ${width}x$height at $fps fps from ${sender.name}" + if (seconds > 0) " for $seconds s" else "")
            val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, width * height * 2)
                setInteger(MediaFormat.KEY_FRAME_RATE, fps)
                // Mirroring senders send key frames rarely.
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 10)
            }
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            val input = encoder.createInputSurface()
            encoder.start()
            heartbeats()
            thread(name = "sim-mirror-draw") { draw(input, encoder) }
            thread(name = "sim-mirror-send") { send(encoder) }
            thread(name = "sim-mirror-report") { report() }
        }

        private fun draw(surface: Surface, encoder: MediaCodec) {
            val picture = TestPicture(width, height, sender.name)
            val frameMs = 1000.0 / fps
            val startedAt = SystemClock.elapsedRealtime()
            var frame = 0
            try {
                while (running && (seconds <= 0 || SystemClock.elapsedRealtime() - startedAt < seconds * 1000L)) {
                    val canvas = surface.lockHardwareCanvas()
                    try {
                        picture.draw(canvas, frame)
                    } finally {
                        surface.unlockCanvasAndPost(canvas)
                    }
                    frame++
                    val due = startedAt + (frame * frameMs).toLong()
                    sleepMs(due - SystemClock.elapsedRealtime())
                }
            } catch (error: Exception) {
                Log.e(TAG, "Test picture failed", error)
            }
            if (running) finish(senderEnded = true)
            runCatching { encoder.signalEndOfInputStream() }
        }

        private fun send(encoder: MediaCodec) {
            val info = MediaCodec.BufferInfo()
            try {
                while (running) {
                    val index = encoder.dequeueOutputBuffer(info, 50_000)
                    if (index < 0) continue
                    val buffer = encoder.getOutputBuffer(index)
                    if (buffer != null && info.size > 0) {
                        val data = ByteArray(info.size)
                        buffer.position(info.offset)
                        buffer.get(data)
                        val config = info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                        // As the protocol delivers them: parameter sets once, then access units.
                        send { bridge.onVideoData(data, if (config) 0 else info.presentationTimeUs, false) }
                        if (!config) sentFrames++
                        sentBytes += data.size
                    }
                    encoder.releaseOutputBuffer(index, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                }
            } catch (error: IllegalStateException) {
                Log.w(TAG, "Encoder stopped", error)
            } finally {
                runCatching { encoder.stop() }
                encoder.release()
            }
        }

        /** The sender's stats, once a second, as a phone reports them. */
        private fun report() {
            var lastFrames = 0
            var lastBytes = 0L
            while (running) {
                sleepMs(1000)
                val frames = sentFrames - lastFrames
                val bits = (sentBytes - lastBytes) * 8.0
                lastFrames = sentFrames
                lastBytes = sentBytes
                val values = mapOf(
                    "sentFramesAvg" to frames.toDouble(), "encoderCurrentFPS" to fps.toDouble(),
                    "submitSurfaceFPS" to frames.toDouble(), "encoderDropFPS" to 0.0,
                    "encoderQueueDropFPS" to 0.0, "sinkOverflowDropFPS" to 0.0, "rttAvg" to 1.0,
                    "lossAvg" to 0.0, "txUsageAvg" to bits, "txCapacityAvg" to 100e6
                )
                SenderReport.parse(SenderReport.KEYS.map { values[it] ?: -1.0 }.toDoubleArray())
                    ?.let { report -> send { bridge.onSenderReport(report) } }
            }
        }
    }

    private inner class MusicSession(
        sender: Sender,
        private val seconds: Int,
        private val title: String,
        private val artist: String,
        private val album: String
    ) : Session(sender) {
        @Volatile private var paused = false
        /** Nothing is sent until then (elapsedRealtime), as when the network holds packets up. */
        @Volatile private var stalledUntilMs = 0L
        /** Samples sent so far: the position in the tune. */
        @Volatile private var position = 0L
        private val lock = Object()

        override fun start() {
            Log.i(TAG, "Music from ${sender.name}: $title – $artist, $seconds s")
            bridge.audioInfo.onMetadata(dmap(title, artist, album))
            bridge.audioInfo.onCoverArt(cover(title))
            bridge.audioInfo.onProgress(0.0, seconds.toDouble())
            heartbeats()
            thread(name = "sim-music") { play() }
        }

        fun stall(ms: Long) {
            Log.i(TAG, "Music held up for $ms ms")
            stalledUntilMs = SystemClock.elapsedRealtime() + ms
        }

        fun setPaused(value: Boolean) {
            if (paused == value) return
            Log.i(TAG, if (value) "Music paused" else "Music resumed")
            if (value) {
                paused = true
                // An iPhone pausing tells the receiver to drop what it has queued, and sends the
                // cover again (the same picture, as new bytes).
                bridge.onAudioFlush()
                bridge.audioInfo.onCoverArt(cover(title))
            } else {
                bridge.audioInfo.onProgress(position.toDouble() / RATE, seconds.toDouble())
                synchronized(lock) {
                    paused = false
                    lock.notifyAll()
                }
            }
        }

        private fun play() {
            val total = seconds.toLong() * RATE
            while (running && position < total) {
                // Each run (from the start, or a resume) is sent two seconds ahead of when it
                // is to be heard, at the pace it plays, as an iPhone does.
                val runStartedAt = SystemClock.elapsedRealtime()
                val runPlaysAtUs = System.currentTimeMillis() * 1000 + LEAD_US
                var sent = 0L
                while (running && !paused && position < total) {
                    val heldMs = stalledUntilMs - SystemClock.elapsedRealtime()
                    if (heldMs > 0) sleepMs(heldMs)
                    val frame = tune(position, FRAME_SAMPLES)
                    val playAtUs = runPlaysAtUs + sent * 1_000_000 / RATE
                    send { bridge.onPcmData(frame, playAtUs, frame.size / 2) }
                    position += FRAME_SAMPLES
                    sent += FRAME_SAMPLES
                    sleepMs(runStartedAt + sent * 1000 / RATE - SystemClock.elapsedRealtime())
                }
                synchronized(lock) {
                    while (running && paused) lock.wait(500)
                }
            }
            // The phone stays connected until the tune has been heard, then stops.
            sleepMs(LEAD_US / 1000)
            if (running) finish(senderEnded = true)
        }
    }

    private inner class VideoSession(sender: Sender, private val url: String) : Session(sender) {
        override fun start() {
            Log.i(TAG, "Video from ${sender.name}: $url")
            bridge.videoPlayback.onPlay(url, 0f)
        }

        override fun finish(senderEnded: Boolean) {
            val stopping = running && senderEnded
            super.finish(senderEnded)
            // After the session is marked over, so the screen leaving isn't taken for the TV's doing.
            if (stopping) bridge.videoPlayback.onStop()
        }
    }

    /** A picture that shows at a glance whether frames arrive, in order and smoothly. */
    private class TestPicture(private val width: Int, private val height: Int, private val sender: String) {
        private val scale = height / 1080f
        private val background = Paint().apply {
            shader = LinearGradient(0f, 0f, width.toFloat(), height.toFloat(),
                Color.rgb(0x2E, 0x27, 0x45), Color.rgb(0x1B, 0x18, 0x26), Shader.TileMode.CLAMP)
        }
        private val big = text(160f, Color.WHITE, Typeface.MONOSPACE)
        private val small = text(40f, Color.argb(200, 255, 255, 255), Typeface.DEFAULT)
        private val block = Paint().apply { color = Color.rgb(0xA4, 0x8B, 0xF5) }
        private val clock = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

        private fun text(size: Float, color: Int, face: Typeface) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size * scale
            this.color = color
            typeface = face
            textAlign = Paint.Align.CENTER
        }

        fun draw(canvas: Canvas, frame: Int) {
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), background)
            val cx = width / 2f
            canvas.drawText("CastBay simulated mirroring · $sender", cx, height * 0.2f, small)
            canvas.drawText(clock.format(Date()), cx, height * 0.5f, big)
            canvas.drawText("frame $frame", cx, height * 0.62f, small)
            // Crosses the screen every two seconds (at 30 fps): stutter shows as jumps.
            val size = 80 * scale
            val x = (frame % 60) / 60f * (width - size)
            canvas.drawRect(x, height * 0.8f, x + size, height * 0.8f + size, block)
        }
    }

    companion object {
        const val TAG = "CastBaySim"
        /** Apple's public HLS sample. */
        const val SAMPLE_VIDEO = "https://devstreaming-cdn.apple.com/videos/streaming/examples/bipbop_16x9/bipbop_16x9_variant.m3u8"
        private const val RATE = 44100
        /** Samples in one of AirPlay's ALAC frames. */
        private const val FRAME_SAMPLES = 352
        /** How far ahead of being heard an iPhone sends music. */
        private const val LEAD_US = 2_000_000L

        @Volatile private var instance: SimulatedSender? = null

        fun get(context: Context): SimulatedSender = instance ?: synchronized(this) {
            instance ?: SimulatedSender(context.applicationContext).also { instance = it }
        }

        private fun sleepMs(ms: Long) {
            if (ms > 0) Thread.sleep(ms)
        }

        /** 16-bit stereo PCM of a gentle arpeggio, from sample [from]: gaps and stutter are easy to hear. */
        private fun tune(from: Long, samples: Int): ByteArray {
            val notes = doubleArrayOf(261.63, 329.63, 392.00, 523.25, 392.00, 329.63)
            val noteLength = 0.4
            val out = ByteArray(samples * 4)
            for (i in 0 until samples) {
                val t = (from + i).toDouble() / RATE
                val into = (t % noteLength) / noteLength
                val envelope = min(1.0, into * 20) * (1 - into).pow(0.5)
                val value = (10000 * envelope * sin(2 * PI * notes[(t / noteLength).toInt() % notes.size] * t)).toInt()
                val lo = (value and 0xFF).toByte()
                val hi = (value shr 8 and 0xFF).toByte()
                out[i * 4] = lo; out[i * 4 + 1] = hi; out[i * 4 + 2] = lo; out[i * 4 + 3] = hi
            }
            return out
        }

        /** The DMAP listing item AirPlay senders describe a track with. */
        private fun dmap(title: String, artist: String, album: String): ByteArray {
            fun item(tag: String, value: ByteArray) = ByteArrayOutputStream().apply {
                write(tag.toByteArray())
                write(byteArrayOf((value.size shr 24).toByte(), (value.size shr 16).toByte(), (value.size shr 8).toByte(), value.size.toByte()))
                write(value)
            }.toByteArray()
            val body = item("minm", title.toByteArray()) + item("asar", artist.toByteArray()) + item("asal", album.toByteArray())
            return item("mlit", body)
        }

        /** A cover: the app's purple, with the title on it. */
        private fun cover(title: String): ByteArray {
            val size = 600
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), Paint().apply {
                shader = LinearGradient(0f, 0f, size.toFloat(), size.toFloat(),
                    Color.rgb(0x7B, 0x5C, 0xE0), Color.rgb(0x2B, 0x9E, 0xB3), Shader.TileMode.CLAMP)
            })
            canvas.drawText(title, size / 2f, size / 2f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 56f
                textAlign = Paint.Align.CENTER
                typeface = Typeface.DEFAULT_BOLD
            })
            return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }.toByteArray()
        }
    }
}
