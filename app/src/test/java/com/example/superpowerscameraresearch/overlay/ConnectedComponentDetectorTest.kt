package com.example.superpowerscameraresearch.overlay

import org.junit.Assert.*
import org.junit.Test

class ConnectedComponentDetectorTest {

    private val detector = ConnectedComponentDetector()

    // Helper: create a luma ByteArray of given size, filled with 0.
    private fun blackFrame(w: Int, h: Int) = ByteArray(w * h) { 0 }

    @Test
    fun `all-black frame returns no sources`() {
        val result = detector.detect(blackFrame(64, 64), stride = 64, width = 64, height = 64, sensitivity = 50)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `single bright pixel is filtered as noise`() {
        val frame = blackFrame(64, 64)
        frame[32 * 64 + 32] = 255.toByte()  // one hot pixel at (32,32)
        val result = detector.detect(frame, stride = 64, width = 64, height = 64, sensitivity = 50)
        assertTrue("single hot pixel should be filtered", result.isEmpty())
    }

    @Test
    fun `small bright cluster is detected as a source`() {
        val frame = blackFrame(64, 64)
        // 2x2 bright patch centred around (32,32)
        frame[31 * 64 + 31] = 255.toByte()
        frame[31 * 64 + 32] = 255.toByte()
        frame[32 * 64 + 31] = 255.toByte()
        frame[32 * 64 + 32] = 255.toByte()
        val result = detector.detect(frame, stride = 64, width = 64, height = 64, sensitivity = 50)
        assertEquals(1, result.size)
        assertEquals(31.5f, result[0].cx, 0.5f)
        assertEquals(31.5f, result[0].cy, 0.5f)
    }

    @Test
    fun `two separate clusters produce two sources`() {
        val frame = blackFrame(64, 64)
        // Star 1 near top-left
        frame[5 * 64 + 5] = 255.toByte()
        frame[5 * 64 + 6] = 255.toByte()
        frame[6 * 64 + 5] = 255.toByte()
        // Star 2 near bottom-right
        frame[55 * 64 + 55] = 255.toByte()
        frame[55 * 64 + 56] = 255.toByte()
        frame[56 * 64 + 55] = 255.toByte()
        val result = detector.detect(frame, stride = 64, width = 64, height = 64, sensitivity = 50)
        assertEquals(2, result.size)
    }

    @Test
    fun `large blob radius is clamped to frameWidth over 4`() {
        // Fill top half entirely — simulates bright sky, should be discarded (>30% frame)
        val w = 64; val h = 64
        val frame = ByteArray(w * h) { if (it / w < h / 2) 255.toByte() else 0 }
        val result = detector.detect(frame, stride = w, width = w, height = h, sensitivity = 100)
        // The half-frame blob covers 50% > MAX_BLOB_AREA_FRACTION (30%), so it's filtered out
        assertTrue("sky-filling blob should be discarded", result.isEmpty())
    }

    @Test
    fun `higher sensitivity detects dimmer sources`() {
        val frame = blackFrame(64, 64)
        // Dim 2x2 patch: value 80 (below 50% threshold but above 20% threshold)
        frame[10 * 64 + 10] = 80.toByte()
        frame[10 * 64 + 11] = 80.toByte()
        frame[11 * 64 + 10] = 80.toByte()
        frame[11 * 64 + 11] = 80.toByte()
        val lowSens = detector.detect(frame, stride = 64, width = 64, height = 64, sensitivity = 50)
        val highSens = detector.detect(frame, stride = 64, width = 64, height = 64, sensitivity = 80)
        assertTrue("dim source invisible at low sensitivity", lowSens.isEmpty())
        assertEquals("dim source visible at high sensitivity", 1, highSens.size)
    }
}
