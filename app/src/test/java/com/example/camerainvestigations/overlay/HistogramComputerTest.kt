package com.example.camerainvestigations.overlay

import org.junit.Assert.*
import org.junit.Test

class HistogramComputerTest {

    @Test
    fun `uniform grey image produces single-bin histogram`() {
        val pixels = ByteArray(400) { 128.toByte() }
        val histogram = HistogramComputer.compute(pixels, stride = 20, width = 20, height = 20)
        assertEquals(256, histogram.size)
        assertTrue("bin 128 must be non-zero", histogram[128] > 0)
        histogram.forEachIndexed { bin, count ->
            if (bin != 128) assertEquals("bin $bin should be zero", 0, count)
        }
    }

    @Test
    fun `all-black image fills bin zero`() {
        val pixels = ByteArray(100) { 0 }
        val histogram = HistogramComputer.compute(pixels, stride = 10, width = 10, height = 10)
        assertTrue(histogram[0] > 0)
        histogram.drop(1).forEach { assertEquals(0, it) }
    }

    @Test
    fun `isClipping returns true when top bin exceeds threshold`() {
        val histogram = IntArray(256).also { it[255] = 100 }
        assertTrue(HistogramComputer.isClipping(histogram, threshold = 0.005f))
    }

    @Test
    fun `isClipping returns false when top bin is within threshold`() {
        val histogram = IntArray(256).also { it[255] = 1; it[0] = 9999 }
        assertFalse(HistogramComputer.isClipping(histogram, threshold = 0.005f))
    }

    @Test
    fun `sampling step skips pixels without crashing on large image`() {
        val pixels = ByteArray(1920 * 1080) { (it % 256).toByte() }
        val histogram = HistogramComputer.compute(pixels, stride = 1920, width = 1920, height = 1080)
        val total = histogram.sum()
        assertTrue("sampled pixel count should be > 0", total > 0)
    }
}
