package com.weenas.castbay.service

import android.content.Context
import android.media.MediaCodec
import androidx.media3.exoplayer.video.PlaceholderSurface
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.Build
import android.os.Handler
import android.os.SystemClock
import android.os.HandlerThread
import com.weenas.castbay.util.Diagnostics
import com.weenas.castbay.util.Log
import com.weenas.castbay.util.SessionSummary
import android.view.Surface
import java.util.ArrayDeque

/**
 * Receives complete Annex B access units from the protocol layer and renders them on a TV Surface.
 *
 * The decoder runs in asynchronous mode on its own thread. Incoming frames are queued and fed as
 * soon as MediaCodec releases an input buffer, so a momentarily busy decoder no longer causes
 * frames to be dropped (which used to corrupt every following P-frame until the next IDR).
 *
 * RPiPlay delivers SPS/PPS as a separate buffer only once per session, so that codec config is
 * cached and replayed whenever the decoder is (re)created, e.g. when the Surface appears late.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class VideoRenderer(
    context: Context,
    /** Called on the decoder thread with the visible picture size whenever it changes. */
    private val onFrameSizeChanged: (width: Int, height: Int) -> Unit = { _, _ -> }
) {
    private val appContext = context.applicationContext
    /**
     * Decodes into nothing while the app is in the background (its Surface is gone), so the
     * decoder keeps its state and the picture is back at once on return: mirroring senders
     * send key frames rarely, and a new decoder would wait for one.
     */
    private var placeholder: PlaceholderSurface? = null

    private val lock = Any()
    private val handler = Handler(HandlerThread("CastBay-video").apply { start() }.looper)

    private var surface: Surface? = null
    private var codec: MediaCodec? = null
    private var width = 0
    private var height = 0
    private var mimeType = MediaFormat.MIMETYPE_VIDEO_AVC

    /** Latest parameter-set buffer (SPS/PPS, or VPS/SPS/PPS for HEVC). */
    private var codecConfig: ByteArray? = null
    private var awaitingKeyFrame = true
    /** The decoder would not start with the low-latency settings, so they are left out. */
    private var lowLatencyRefused = false
    private val pendingFrames = ArrayDeque<Frame>()
    private val freeInputs = ArrayDeque<Int>()
    private var droppedFrames = 0L
    private var renderedFrames = 0L
    // For the line written when a session ends (SessionSummary).
    private var receivedFrames = 0L
    private var receivedBytes = 0L
    private var firstFrameAtMs = 0L

    // For the stats overlay.
    private val inputRate = RateMeter()
    @Volatile private var decoderName: String? = null
    @Volatile private var frameWidth = 0
    @Volatile private var frameHeight = 0
    /** When each queued frame arrived, by presentation time, to time it through the decoder. */
    private val arrivals = HashMap<Long, Long>()
    @Volatile private var decodeLatencyMs: Double? = null

    private class Frame(val data: ByteArray, val presentationTimeUs: Long, val flags: Int) {
        val isConfig: Boolean get() = flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
    }

    fun setSurface(value: Surface?) {
        synchronized(lock) {
            if (surface === value) return
            surface = value
            val decoder = codec
            val output = if (value != null && value.isValid) value else placeholderLocked()
            if (decoder != null && output != null) {
                // Swap outputs without tearing down the decoder, so no new IDR frame is needed.
                try {
                    decoder.setOutputSurface(output)
                    if (output === value) releasePlaceholderLocked()
                    return
                } catch (error: Exception) {
                    Log.w(TAG, "setOutputSurface failed, recreating decoder", error)
                }
            }
            releaseCodecLocked()
            startCodecIfReadyLocked()
            releasePlaceholderLocked()
        }
    }

    fun configure(width: Int, height: Int, isH265: Boolean = false) {
        require(width > 0 && height > 0) { "Video dimensions must be positive" }
        val requestedMime = if (isH265) MediaFormat.MIMETYPE_VIDEO_HEVC else MediaFormat.MIMETYPE_VIDEO_AVC
        synchronized(lock) {
            if (this.width == width && this.height == height && mimeType == requestedMime) return
            releaseCodecLocked()
            if (mimeType != requestedMime) codecConfig = null
            this.width = width
            this.height = height
            mimeType = requestedMime
            startCodecIfReadyLocked()
        }
    }

    /**
     * Queues one access unit for decoding. Returns false when the frame was discarded
     * (waiting for a key frame, or the backlog was flushed).
     */
    fun render(accessUnit: ByteArray, presentationTimeUs: Long): Boolean {
        if (accessUnit.isEmpty()) return false
        inputRate.record(accessUnit.size)
        synchronized(lock) {
            if (receivedFrames++ == 0L) firstFrameAtMs = SystemClock.elapsedRealtime()
            receivedBytes += accessUnit.size
            val nal = scanNals(accessUnit)

            if (nal.hasParameterSets && !nal.hasSlice) {
                codecConfig = accessUnit
                if (codec != null) enqueueLocked(Frame(accessUnit, 0, MediaCodec.BUFFER_FLAG_CODEC_CONFIG))
                return true
            }

            // No codec yet (the Surface usually appears just after the stream starts): keep
            // buffering from the key frame on, because the sender only sends IDRs at session
            // start and on format changes. feedLocked() is a no-op until the decoder exists.
            if (awaitingKeyFrame && !nal.hasRandomAccess) {
                droppedFrames++
                noteWaiting(accessUnit, nal)
                return false
            }

            if (pendingFrames.size >= MAX_PENDING_FRAMES) {
                // The decoder cannot keep up. Dropping arbitrary P-frames would corrupt the
                // picture, so flush the backlog and resume cleanly from the next key frame.
                Log.w(TAG, "Decoder backlog of ${pendingFrames.size} frames, resyncing on next key frame")
                droppedFrames += pendingFrames.count { !it.isConfig }
                pendingFrames.removeAll { !it.isConfig }
                awaitingKeyFrame = true
                if (!nal.hasRandomAccess) {
                    droppedFrames++
                    return false
                }
            }

            noteKeyFrameInterval(nal.hasRandomAccess)
            if (awaitingKeyFrame) Log.i(TAG, "Key frame accepted after ${droppedFrames} dropped frames (${accessUnit.size} bytes, NAL types ${nalTypes(accessUnit)})")
            awaitingKeyFrame = false
            if (arrivals.size >= MAX_PENDING_FRAMES) arrivals.clear()
            arrivals[presentationTimeUs] = SystemClock.elapsedRealtimeNanos()
            val flags = if (nal.hasRandomAccess) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
            enqueueLocked(Frame(accessUnit, presentationTimeUs, flags))
            return true
        }
    }

    private var lastWaitingLogMs = 0L
    private var framesSinceKey = 0
    private var longestKeyGap = 0
    private var keyFramesSeen = 0

    /** How far apart the sender's key frames are, in frames: a sender that rarely sends one leaves any damage on screen. */
    private fun noteKeyFrameInterval(isKey: Boolean) {
        framesSinceKey++
        if (!isKey) return
        keyFramesSeen++
        if (keyFramesSeen > 1) longestKeyGap = maxOf(longestKeyGap, framesSinceKey)
        framesSinceKey = 0
    }

    /** While frames are dropped for want of a key frame: once every few seconds, what the dropped frame looks like. */
    private fun noteWaiting(accessUnit: ByteArray, nal: NalSummary) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastWaitingLogMs < 3000) return
        lastWaitingLogMs = now
        Log.i(TAG, "Waiting for a key frame: $droppedFrames dropped so far, last ${accessUnit.size} bytes, NAL types ${nalTypes(accessUnit)}, " +
            "codec ${if (codec != null) "running" else "not started"}, surface ${if (surface?.isValid == true) "valid" else "none"}")
    }

    /** The NAL unit types in an access unit, as text ("7 8 5"), for the log. */
    private fun nalTypes(data: ByteArray): String {
        val types = ArrayList<Int>()
        val h265 = mimeType == MediaFormat.MIMETYPE_VIDEO_HEVC
        var i = 0
        while (i + 3 < data.size && types.size < 12) {
            if (data[i] == 0.toByte() && data[i + 1] == 0.toByte() && data[i + 2] == 1.toByte()) {
                val header = data[i + 3].toInt() and 0xFF
                types.add(if (h265) (header ushr 1) and 0x3F else header and 0x1F)
                i += 3
            } else i++
        }
        return types.joinToString(" ")
    }

    /** What is being decoded, for the stats overlay; null when no stream is active. */
    fun stats(): VideoStats? {
        val name = decoderName ?: return null
        return VideoStats(
            codec = StatsFormat.codecName(mimeType),
            width = frameWidth,
            height = frameHeight,
            fps = inputRate.perSecond(),
            bitrateBps = inputRate.bitsPerSecond(),
            decoder = name,
            droppedFrames = synchronized(lock) { droppedFrames },
            decodeLatencyMs = decodeLatencyMs?.toInt()
        )
    }

    fun stop() {
        synchronized(lock) {
            if (receivedFrames > 0) {
                val summary = SessionSummary.video(
                    seconds = (SystemClock.elapsedRealtime() - firstFrameAtMs) / 1000.0,
                    codec = StatsFormat.codecName(mimeType), width = frameWidth, height = frameHeight, decoder = decoderName,
                    received = receivedFrames, bytes = receivedBytes, rendered = renderedFrames, dropped = droppedFrames,
                    keyFrames = keyFramesSeen, longestKeyGap = longestKeyGap,
                )
                Log.i(TAG, summary)
                Diagnostics.record("video", summary)
            }
            receivedFrames = 0
            receivedBytes = 0
            firstFrameAtMs = 0
            releaseCodecLocked()
            releasePlaceholderLocked()
            width = 0
            height = 0
            codecConfig = null
            pendingFrames.clear()
            awaitingKeyFrame = true
            if (droppedFrames > 0) Log.i(TAG, "Session ended, $droppedFrames video frames dropped")
            decoderName = null
            frameWidth = 0
            frameHeight = 0
            decodeLatencyMs = null
            inputRate.reset()
            droppedFrames = 0
            framesSinceKey = 0
            longestKeyGap = 0
            keyFramesSeen = 0
        }
    }

    private fun enqueueLocked(frame: Frame) {
        pendingFrames.addLast(frame)
        feedLocked()
    }

    /** Copies queued frames into the input buffers the decoder has handed us. */
    private fun feedLocked() {
        val decoder = codec ?: return
        while (freeInputs.isNotEmpty() && pendingFrames.isNotEmpty()) {
            val index = freeInputs.removeFirst()
            val frame = pendingFrames.removeFirst()
            try {
                val input = decoder.getInputBuffer(index)
                if (input == null || frame.data.size > input.capacity()) {
                    Log.w(TAG, "Dropping video access unit of ${frame.data.size} bytes")
                    decoder.queueInputBuffer(index, 0, 0, frame.presentationTimeUs, 0)
                    if (!frame.isConfig) {
                        droppedFrames++
                        awaitingKeyFrame = true
                    }
                    continue
                }
                input.clear()
                input.put(frame.data)
                decoder.queueInputBuffer(index, 0, frame.data.size, frame.presentationTimeUs, frame.flags)
            } catch (error: IllegalStateException) {
                Log.e(TAG, "Video decoder rejected input", error)
                restartCodecLocked()
                return
            }
        }
    }

    private fun startCodecIfReadyLocked() {
        val target = surface ?: return
        if (!target.isValid || width <= 0 || height <= 0 || codec != null) return
        // Some decoders refuse the low-latency settings at configure (a Rockchip one does, on Android 11):
        // then the same decoder is tried without them, and they are not offered again.
        for (lowLatency in if (lowLatencyRefused) listOf(false) else listOf(true, false)) {
            val decoder = try {
                MediaCodec.createDecoderByType(mimeType)
            } catch (error: Exception) {
                Log.e(TAG, "Unable to create $mimeType decoder", error)
                return
            }
            try {
                decoder.setCallback(DecoderCallback(decoder), handler)
                decoder.configure(decoderFormat(decoder, lowLatency), target, null, 0)
                codec = decoder
                renderedFrames = 0
                decoder.start()
                codecConfig?.let { pendingFrames.addFirst(Frame(it, 0, MediaCodec.BUFFER_FLAG_CODEC_CONFIG)) }
                decoderName = decoder.name
                return
            } catch (error: Exception) {
                codec = null
                val name = runCatching { decoder.name }.getOrDefault(mimeType)
                decoder.release()
                if (lowLatency) {
                    Log.w(TAG, "$name would not start with the low-latency settings, trying without them", error)
                    lowLatencyRefused = true
                    Diagnostics.record("video", "$name refused the low-latency settings, started without them")
                    continue
                }
                Log.e(TAG, "Unable to start $mimeType decoder", error)
                pendingFrames.clear()
                awaitingKeyFrame = true
            }
        }
    }

    /**
     * Asks the decoder to output each frame as soon as it is decoded ([lowLatency]). Without this,
     * TV decoders (MediaTek's among them) hold several frames back as if for smooth film playback,
     * which mirroring feels as lag.
     */
    private fun decoderFormat(decoder: MediaCodec, lowLatency: Boolean): MediaFormat {
        val format = MediaFormat.createVideoFormat(mimeType, width, height)
        if (!lowLatency) return format
        // Real-time priority: decode frames as they come rather than at a relaxed pace.
        format.setInteger(MediaFormat.KEY_PRIORITY, 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val supported = runCatching {
                decoder.codecInfo.getCapabilitiesForType(mimeType)
                    .isFeatureSupported(MediaCodecInfo.CodecCapabilities.FEATURE_LowLatency)
            }.getOrDefault(false)
            Log.i(TAG, "${decoder.name} low-latency mode ${if (supported) "supported" else "not advertised"}")
            format.setInteger(MediaFormat.KEY_LOW_LATENCY, 1)
        }
        return format
    }

    private fun placeholderLocked(): Surface? = placeholder ?: runCatching {
        PlaceholderSurface.newInstanceV17(appContext, false)
    }.onFailure { Log.w(TAG, "No placeholder surface; the decoder restarts on return", it) }
        .getOrNull()?.also { placeholder = it }

    private fun releasePlaceholderLocked() {
        placeholder?.release()
        placeholder = null
    }

    private fun releaseCodecLocked() {
        freeInputs.clear()
        arrivals.clear()
        // Frames buffered before any decoder existed are still decodable by the next one.
        val decoder = codec ?: return
        awaitingKeyFrame = true
        pendingFrames.clear()
        codec = null
        try {
            decoder.stop()
        } catch (error: IllegalStateException) {
            Log.w(TAG, "Decoder was already stopped", error)
        } finally {
            decoder.release()
        }
    }

    private fun restartCodecLocked() {
        releaseCodecLocked()
        startCodecIfReadyLocked()
    }

    /** Callbacks run on the decoder thread; [owner] guards against events from a released codec. */
    private inner class DecoderCallback(private val owner: MediaCodec) : MediaCodec.Callback() {
        override fun onInputBufferAvailable(mc: MediaCodec, index: Int) {
            synchronized(lock) {
                if (codec !== owner) return
                freeInputs.addLast(index)
                feedLocked()
            }
        }

        override fun onOutputBufferAvailable(mc: MediaCodec, index: Int, info: MediaCodec.BufferInfo) {
            synchronized(lock) {
                if (codec !== owner) return
                try {
                    // Mirroring is live: show every decoded frame immediately.
                    mc.releaseOutputBuffer(index, info.size > 0)
                    arrivals.remove(info.presentationTimeUs)?.let { recordDecodeLatency(it) }
                    if (info.size > 0 && renderedFrames++ == 0L) Log.i(TAG, "First video frame rendered")
                } catch (error: IllegalStateException) {
                    Log.w(TAG, "Could not release output buffer", error)
                }
            }
        }

        override fun onOutputFormatChanged(mc: MediaCodec, format: MediaFormat) {
            Log.i(TAG, "Decoder output format: $format")
            // The buffer is padded (e.g. 1920x1088 for a 448x972 portrait stream); the crop
            // rect is the part the sender actually drew.
            val width = if (format.containsKey(KEY_CROP_LEFT) && format.containsKey(KEY_CROP_RIGHT)) {
                format.getInteger(KEY_CROP_RIGHT) - format.getInteger(KEY_CROP_LEFT) + 1
            } else {
                format.getInteger(MediaFormat.KEY_WIDTH)
            }
            val height = if (format.containsKey(KEY_CROP_TOP) && format.containsKey(KEY_CROP_BOTTOM)) {
                format.getInteger(KEY_CROP_BOTTOM) - format.getInteger(KEY_CROP_TOP) + 1
            } else {
                format.getInteger(MediaFormat.KEY_HEIGHT)
            }
            if (width > 0 && height > 0) {
                frameWidth = width
                frameHeight = height
                onFrameSizeChanged(width, height)
            }
        }

        override fun onError(mc: MediaCodec, error: MediaCodec.CodecException) {
            synchronized(lock) {
                if (codec !== owner) return
                Log.e(TAG, "Video decoder error (transient=${error.isTransient})", error)
                com.weenas.castbay.util.UsageStats.failure("decoder")
                if (!error.isTransient) restartCodecLocked()
            }
        }
    }

    /** A smoothed time from a frame's arrival to its release for display. */
    private fun recordDecodeLatency(arrivedNanos: Long) {
        val ms = (SystemClock.elapsedRealtimeNanos() - arrivedNanos) / 1_000_000.0
        val previous = decodeLatencyMs
        decodeLatencyMs = if (previous == null) ms else previous + (ms - previous) * 0.1
        if (renderedFrames % 300 == 1L) Log.i(TAG, "Decode latency about ${decodeLatencyMs?.toInt()} ms; key frames $keyFramesSeen, longest gap $longestKeyGap frames, ${framesSinceKey} since the last")
    }

    private class NalSummary(
        val hasParameterSets: Boolean,
        val hasSlice: Boolean,
        val hasRandomAccess: Boolean
    )

    /** Walks Annex B NAL units (3- or 4-byte start codes) and classifies their types. */
    private fun scanNals(data: ByteArray): NalSummary {
        val h265 = mimeType == MediaFormat.MIMETYPE_VIDEO_HEVC
        var parameterSets = false
        var slice = false
        var randomAccess = false
        var index = 0
        while (index + 3 < data.size) {
            if (data[index] == 0.toByte() && data[index + 1] == 0.toByte() && data[index + 2] == 1.toByte()) {
                val header = data[index + 3].toInt() and 0xFF
                if (h265) {
                    val type = (header ushr 1) and 0x3F
                    if (type in 32..34) parameterSets = true
                    if (type in 0..31) slice = true
                    if (type in 16..21) randomAccess = true
                } else {
                    val type = header and 0x1F
                    if (type == 7 || type == 8) parameterSets = true
                    if (type in 1..5) slice = true
                    if (type == 5) randomAccess = true
                }
                index += 3
            } else {
                index++
            }
        }
        return NalSummary(parameterSets, slice, randomAccess)
    }

    private companion object {
        const val TAG = "CastBayVideo"
        /** About two seconds at 60 fps before the backlog is flushed. */
        const val MAX_PENDING_FRAMES = 120
        // MediaFormat.KEY_CROP_* are only public from API 33.
        const val KEY_CROP_LEFT = "crop-left"
        const val KEY_CROP_RIGHT = "crop-right"
        const val KEY_CROP_TOP = "crop-top"
        const val KEY_CROP_BOTTOM = "crop-bottom"
    }
}
