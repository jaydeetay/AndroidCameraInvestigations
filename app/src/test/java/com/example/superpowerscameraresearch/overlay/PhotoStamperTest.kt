package com.example.superpowerscameraresearch.overlay

import com.example.superpowerscameraresearch.model.CameraSettings
import org.junit.Assert.*
import org.junit.Test

class PhotoStamperTest {

    @Test
    fun `line1 contains ISO when not auto`() {
        val s = CameraSettings(isoAuto = false, iso = 1600)
        val (line1, _) = PhotoStamper.buildLines(s, aperture = null, focalLengthMm = null,
            zoom = 1.0f, timestampMs = 0L, stack = "Camera2")
        assertTrue(line1.contains("ISO 1600"))
    }

    @Test
    fun `line1 contains ISO AUTO when auto`() {
        val s = CameraSettings(isoAuto = true)
        val (line1, _) = PhotoStamper.buildLines(s, aperture = null, focalLengthMm = null,
            zoom = 1.0f, timestampMs = 0L, stack = "Camera2")
        assertTrue(line1.contains("ISO AUTO"))
    }

    @Test
    fun `line1 contains DETECT OFF when sensitivity is zero`() {
        val s = CameraSettings(sourceDetectionSensitivity = 0)
        val (line1, _) = PhotoStamper.buildLines(s, aperture = null, focalLengthMm = null,
            zoom = 1.0f, timestampMs = 0L, stack = "Camera2")
        assertTrue(line1.contains("DETECT OFF"))
    }

    @Test
    fun `line1 contains DETECT percentage when sensitivity nonzero`() {
        val s = CameraSettings(sourceDetectionSensitivity = 65)
        val (line1, _) = PhotoStamper.buildLines(s, aperture = null, focalLengthMm = null,
            zoom = 1.0f, timestampMs = 0L, stack = "Camera2")
        assertTrue(line1.contains("DETECT 65%"))
    }

    @Test
    fun `line2 contains stack name`() {
        val s = CameraSettings()
        val (_, line2) = PhotoStamper.buildLines(s, aperture = null, focalLengthMm = null,
            zoom = 1.0f, timestampMs = 1_000_000L, stack = "Camera2")
        assertTrue(line2.contains("Camera2"))
    }

    @Test
    fun `line1 includes focal length and aperture when provided`() {
        val s = CameraSettings()
        val (line1, _) = PhotoStamper.buildLines(s, aperture = 1.8f, focalLengthMm = 24f,
            zoom = 2.0f, timestampMs = 0L, stack = "Camera2")
        assertTrue(line1.contains("f/1.8"))
        assertTrue(line1.contains("24mm"))
        assertTrue(line1.contains("2.0×"))
    }
}
