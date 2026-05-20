package com.example.superpowerscameraresearch.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraSettingsTest {

    @Test
    fun `default settings use auto modes`() {
        val settings = CameraSettings()
        assertTrue(settings.isoAuto)
        assertTrue(settings.shutterAuto)
        assertTrue(settings.wbAuto)
        assertTrue(settings.focusAuto)
    }

    @Test
    fun `shutterNs converts to display string`() {
        assertEquals("1/30s", CameraSettings.shutterNsToDisplay(33_333_333L))
        assertEquals("1/1000s", CameraSettings.shutterNsToDisplay(1_000_000L))
        assertEquals("2s", CameraSettings.shutterNsToDisplay(2_000_000_000L))
        assertEquals("30s", CameraSettings.shutterNsToDisplay(30_000_000_000L))
    }

    @Test
    fun `focus display returns infinity for zero distance`() {
        assertEquals("∞", CameraSettings.focusDistanceToDisplay(0f))
    }

    @Test
    fun `focus display returns dioptre value for non-zero`() {
        assertEquals("0.50 D", CameraSettings.focusDistanceToDisplay(0.5f))
    }

    @Test
    fun `shutterNs rounds correctly for common speeds`() {
        assertEquals("1/60s", CameraSettings.shutterNsToDisplay(16_666_667L))
        assertEquals("1/125s", CameraSettings.shutterNsToDisplay(8_000_000L))
    }
}
