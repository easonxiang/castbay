package com.weenas.castbay.ui.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VinylSpinTest {
    private fun VinylSpin.run(seconds: Float, spinning: Boolean) {
        var t = 0f
        while (t < seconds) { step(0.033f, spinning); t += 0.033f }
    }

    @Test
    fun speedsUpToFullSpeedAndTurnsOnceInAboutFifteenSeconds() {
        val spin = VinylSpin()
        spin.run(3f, spinning = true)
        assertEquals(VinylSpin.FULL_SPEED, spin.speed, 0.5f)
        val before = spin.angle
        spin.run(1f, spinning = true)
        assertEquals(VinylSpin.FULL_SPEED, (spin.angle - before + 360f) % 360f, 1f)
    }

    @Test
    fun coastsToAStopAndKeepsItsAngleWhenPaused() {
        val spin = VinylSpin()
        spin.run(2f, spinning = true)
        spin.run(6f, spinning = false)
        assertEquals(0f, spin.speed, 0f)
        val stoppedAt = spin.angle
        assertFalse(spin.step(0.033f, spinning = false))
        assertEquals(stoppedAt, spin.angle, 0f)
        // Playing again goes on from that angle.
        spin.run(0.5f, spinning = true)
        assertTrue(spin.angle != stoppedAt)
    }

    @Test
    fun theAngleWrapsAroundAFullTurn() {
        val spin = VinylSpin()
        spin.run(40f, spinning = true)
        assertTrue(spin.angle in 0f..360f)
    }

    @Test
    fun aRecordThatIsNotMeantToTurnStaysStill() {
        val spin = VinylSpin()
        assertFalse(spin.step(0.033f, spinning = false))
        assertEquals(0f, spin.angle, 0f)
    }
}
