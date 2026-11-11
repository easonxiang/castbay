package com.weenas.castbay.util

import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeCrashTraceTest {
    // A tiny protobuf writer, enough to build what a tombstone holds.
    private class Msg {
        val out = ByteArrayOutputStream()
        private fun varint(value: Long) { var v = value; while (v and 0x7f.inv().toLong() != 0L) { out.write(((v and 0x7f) or 0x80).toInt()); v = v ushr 7 }; out.write(v.toInt()) }
        fun num(field: Int, value: Long) = apply { varint((field.toLong() shl 3) or 0); varint(value) }
        fun bytes(field: Int, data: ByteArray) = apply { varint((field.toLong() shl 3) or 2); varint(data.size.toLong()); out.write(data) }
        fun text(field: Int, value: String) = bytes(field, value.toByteArray())
        fun msg(field: Int, m: Msg) = bytes(field, m.out.toByteArray())
        fun build(): ByteArray = out.toByteArray()
    }

    private fun frame(relPc: Long, function: String?, offset: Long, file: String, buildId: String) = Msg().apply {
        num(1, relPc); num(2, 0x7000_0000L + relPc)
        if (function != null) { text(4, function); num(5, offset) }
        text(6, file); text(8, buildId)
    }

    private fun tombstone(): ByteArray {
        val crashing = Msg().num(1, 2383).text(2, "UxPlay-raop").apply {
            msg(4, frame(0x4a1c, "raop_rtp_start_audio", 36, "/data/app/~~abc/com.weenas.castbay-xyz/lib/arm64/libairplay.so", "ab12"))
            msg(4, frame(0x1f00, null, 0, "/apex/com.android.runtime/lib64/bionic/libc.so", "cd34"))
            msg(5, Msg().text(1, "memory dump that must not appear"))
        }
        val other = Msg().num(1, 2316).text(2, "main").msg(4, frame(0x10, "main", 1, "/system/bin/app_process64", "ef56"))
        return Msg().num(5, 2316).num(6, 2383)
            .msg(10, Msg().num(1, 11).text(2, "SIGSEGV").num(3, 1).text(4, "SEGV_MAPERR").num(9, 0x18))
            .text(14, "assertion failed")
            .text(9, "/data/data/private/command-line")
            .msg(16, Msg().num(1, 2316).msg(2, other))
            .msg(16, Msg().num(1, 2383).msg(2, crashing))
            .build()
    }

    @Test
    fun summarizesTheSignalAndTheCrashingThread() {
        val text = NativeCrashTrace.summarize(tombstone())!!
        assertEquals(
            """
            Signal 11 (SIGSEGV), code 1 (SEGV_MAPERR), fault address 0x18
            Abort message: assertion failed
            Crashing thread 2383 "UxPlay-raop":
              #00 pc 0000000000004a1c  libairplay.so (raop_rtp_start_audio+36) [ab12]
              #01 pc 0000000000001f00  libc.so [cd34]
            """.trimIndent() + "\n",
            text,
        )
    }

    @Test
    fun leavesOutWhatIsNotNeeded() {
        val text = NativeCrashTrace.summarize(tombstone())!!
        assertTrue("memory dump" !in text)
        assertTrue("command-line" !in text)
        assertTrue("~~abc" !in text)
        assertTrue("main" !in text)
    }

    @Test
    fun anUnreadableTombstoneGivesNothing() {
        assertNull(NativeCrashTrace.summarize(ByteArray(0)))
        assertNull(NativeCrashTrace.summarize("*** *** *** text tombstone".toByteArray()))
        assertNull(NativeCrashTrace.summarize(byteArrayOf(0x12, 0x7f, 1, 2)))
    }
}
