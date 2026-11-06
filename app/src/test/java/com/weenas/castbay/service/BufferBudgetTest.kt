package com.weenas.castbay.service

import androidx.media3.common.C
import org.junit.Assert.assertEquals
import org.junit.Test

class BufferBudgetTest {
    private val mb = 1L shl 20

    @Test
    fun aSmallHeapBuffersAQuarterOfIt() {
        assertEquals((12 * mb).toInt(), bufferBudgetBytes(48 * mb))
        assertEquals((24 * mb).toInt(), bufferBudgetBytes(96 * mb))
    }

    @Test
    fun aTinyHeapStillBuffersSomething() {
        assertEquals((8 * mb).toInt(), bufferBudgetBytes(24 * mb))
    }

    @Test
    fun aLargeHeapKeepsExoPlayersDefault() {
        assertEquals(C.LENGTH_UNSET, bufferBudgetBytes(128 * mb))
        assertEquals(C.LENGTH_UNSET, bufferBudgetBytes(192 * mb))
    }
}
