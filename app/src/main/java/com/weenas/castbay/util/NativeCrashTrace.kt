package com.weenas.castbay.util

/**
 * A short, readable summary of a native crash from the tombstone Android keeps for it
 * (ApplicationExitInfo.getTraceInputStream, a protobuf on Android 11+): the signal, the abort
 * message and the crashing thread's frames (library, offset, symbol if the library has one, build
 * id). Everything else in a tombstone (memory dumps, the log, open files, the command line) is
 * left out. Only the few fields needed are read, by hand, so nothing depends on a protobuf library.
 *
 * Field numbers are those of AOSP's debuggerd/proto/tombstone.proto.
 */
internal object NativeCrashTrace {
    private const val MAX_FRAMES = 40

    private class Field(val number: Int, val wire: Int, val value: Long, val start: Int, val end: Int)

    private class Reader(private val b: ByteArray, private var i: Int, private val end: Int) {
        fun fields(): Sequence<Field> = sequence {
            while (i < end) {
                val tag = varint()
                val number = (tag ushr 3).toInt()
                when (val wire = (tag and 7).toInt()) {
                    0 -> yield(Field(number, wire, varint(), i, i))
                    1 -> { yield(Field(number, wire, fixed(8), i - 8, i)) }
                    2 -> {
                        val length = varint().toInt()
                        if (length < 0 || i + length > end) return@sequence
                        yield(Field(number, wire, 0, i, i + length))
                        i += length
                    }
                    5 -> { yield(Field(number, wire, fixed(4), i - 4, i)) }
                    else -> return@sequence
                }
            }
        }

        private fun varint(): Long {
            var result = 0L
            var shift = 0
            while (i < end && shift < 64) {
                val byte = b[i++].toInt()
                result = result or ((byte and 0x7f).toLong() shl shift)
                if (byte and 0x80 == 0) return result
                shift += 7
            }
            i = end
            return result
        }

        private fun fixed(size: Int): Long {
            var result = 0L
            for (k in 0 until size) if (i + k < end) result = result or ((b[i + k].toLong() and 0xff) shl (8 * k))
            i = minOf(end, i + size)
            return result
        }
    }

    private fun fields(b: ByteArray, start: Int = 0, end: Int = b.size) = Reader(b, start, end).fields()
    private fun text(b: ByteArray, f: Field) = String(b, f.start, f.end - f.start, Charsets.UTF_8)

    /** The summary, or null if [tombstone] is not one we can read. */
    fun summarize(tombstone: ByteArray): String? = runCatching { read(tombstone) }.getOrNull()?.takeIf { it.isNotBlank() }

    private fun read(b: ByteArray): String {
        var tid = -1L
        var signal: Field? = null
        var abortMessage: String? = null
        val threads = mutableListOf<Field>()
        for (f in fields(b)) when {
            f.number == 6 && f.wire == 0 -> tid = f.value
            f.number == 10 && f.wire == 2 -> signal = f
            f.number == 14 && f.wire == 2 -> abortMessage = text(b, f)
            f.number == 16 && f.wire == 2 -> threads += f
        }
        return buildString {
            signal?.let { appendLine(signalLine(b, it)) }
            abortMessage?.let { appendLine("Abort message: ${it.take(300)}") }
            // threads is a map<uint32, Thread>: each entry has the thread id (1) and the Thread (2).
            for (entry in threads) {
                var id = -1L
                var thread: Field? = null
                for (f in fields(b, entry.start, entry.end)) when {
                    f.number == 1 && f.wire == 0 -> id = f.value
                    f.number == 2 && f.wire == 2 -> thread = f
                }
                if (id == tid && thread != null) appendThread(b, thread, id)
            }
        }
    }

    private fun signalLine(b: ByteArray, signal: Field): String {
        var number = 0L; var name = ""; var code = 0L; var codeName = ""; var address: Long? = null
        for (f in fields(b, signal.start, signal.end)) when {
            f.number == 1 && f.wire == 0 -> number = f.value
            f.number == 2 && f.wire == 2 -> name = text(b, f)
            f.number == 3 && f.wire == 0 -> code = f.value
            f.number == 4 && f.wire == 2 -> codeName = text(b, f)
            f.number == 9 && f.wire == 0 -> address = f.value
        }
        return "Signal $number ($name), code $code ($codeName)" + (address?.let { ", fault address 0x${java.lang.Long.toHexString(it)}" } ?: "")
    }

    private fun StringBuilder.appendThread(b: ByteArray, thread: Field, id: Long) {
        var name = ""
        val frames = mutableListOf<Field>()
        for (f in fields(b, thread.start, thread.end)) when {
            f.number == 2 && f.wire == 2 -> name = text(b, f)
            f.number == 4 && f.wire == 2 -> frames += f
        }
        appendLine("Crashing thread $id \"$name\":")
        frames.take(MAX_FRAMES).forEachIndexed { index, frame ->
            var relPc = 0L; var function = ""; var offset = 0L; var file = ""; var buildId = ""
            for (f in fields(b, frame.start, frame.end)) when {
                f.number == 1 && f.wire == 0 -> relPc = f.value
                f.number == 4 && f.wire == 2 -> function = text(b, f)
                f.number == 5 && f.wire == 0 -> offset = f.value
                f.number == 6 && f.wire == 2 -> file = text(b, f)
                f.number == 8 && f.wire == 2 -> buildId = text(b, f)
            }
            // Only the library's name: the path of an installed app holds a random folder.
            append("  #%02d pc %016x  %s".format(index, relPc, file.substringAfterLast('/').ifEmpty { "?" }))
            if (function.isNotEmpty()) append(" ($function+$offset)")
            if (buildId.isNotEmpty()) append(" [$buildId]")
            appendLine()
        }
        if (frames.size > MAX_FRAMES) appendLine("  … ${frames.size - MAX_FRAMES} more frames")
    }
}
