package com.weenas.castbay.util

import org.junit.Assert.assertEquals
import org.junit.Test

class SessionSummaryTest {
    @Test
    fun saysHowAShortSessionWent() {
        assertEquals(
            "Mirroring ended after 4.6 s: H.265 994x1920 on c2.amlogic.hevc.decoder; 118 frames received (912 KB), 117 shown, 0 dropped; 1 key frames",
            SessionSummary.video(4.6, "H.265", 994, 1920, "c2.amlogic.hevc.decoder", 118, 912L * 1024, 117, 0, 1, 0),
        )
    }

    @Test
    fun givesTheKeyFrameGapAndMegabytesForALongOne() {
        assertEquals(
            "Mirroring ended after 125.0 s: H.264 1920x1080 on OMX.MTK.VIDEO.DECODER.AVC; 3750 frames received (25.0 MB), 3748 shown, 2 dropped; 3 key frames, longest gap 1800 frames",
            SessionSummary.video(125.0, "H.264", 1920, 1080, "OMX.MTK.VIDEO.DECODER.AVC", 3750, 25L * 1048576, 3748, 2, 3, 1800),
        )
    }

    @Test
    fun aSessionWithoutADecoderOrSizeStillReads() {
        assertEquals(
            "Mirroring ended after 0.4 s: H.264 on no decoder; 5 frames received (0 KB), 0 shown, 5 dropped; 0 key frames",
            SessionSummary.video(0.4, "H.264", 0, 0, null, 5, 100, 0, 5, 0, 0),
        )
    }
}
