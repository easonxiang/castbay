package com.weenas.castbay.service

import org.junit.Assert.assertEquals
import org.junit.Test

class BufferBudgetTest {
    private val mb = 1L shl 20

    @Test
    fun aQuarterOfTheHeap() {
        assertEquals((48 * mb).toInt(), bufferBudgetBytes(192 * mb))
    }

    @Test
    fun aSmallHeapStillBuffersSomething() {
        assertEquals((8 * mb).toInt(), bufferBudgetBytes(24 * mb))
    }

    @Test
    fun aLargeHeapIsCapped() {
        assertEquals((128 * mb).toInt(), bufferBudgetBytes(1024 * mb))
    }
}
