package com.example.camerainvestigations.model

import android.hardware.camera2.CameraCharacteristics
import android.util.Range
import android.util.SizeF

data class PhysicalCamera(
    val id: String,
    val focalLength: Float,
    val aperture: Float
)

data class CameraCapabilities(
    val cameraId: String,
    val facing: Int,                        // CameraCharacteristics.LENS_FACING_*
    val focalLengths: List<Float>,
    val apertures: List<Float>,
    val isoRange: Range<Int>?,
    val exposureTimeRange: Range<Long>?,    // nanoseconds
    val hardwareLevel: Int,                 // CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_*
    val supportsRaw: Boolean,
    val supportsOis: Boolean,
    val isLogicalCamera: Boolean,
    val physicalCameras: List<PhysicalCamera>,
    val sensorSizeMm: SizeF?,              // physical sensor size in millimetres
    val zoomRatioRange: Range<Float>?      // null on API < 30
) {
    val hardwareLevelName: String get() = when (hardwareLevel) {
        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY   -> "LEGACY"
        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED  -> "LIMITED"
        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL     -> "FULL"
        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3        -> "LEVEL_3"
        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL -> "EXTERNAL"
        else -> "UNKNOWN($hardwareLevel)"
    }

    val facingName: String get() = when (facing) {
        CameraCharacteristics.LENS_FACING_BACK  -> "BACK"
        CameraCharacteristics.LENS_FACING_FRONT -> "FRONT"
        else -> "EXTERNAL"
    }

    val primaryFocalLength: Float get() = focalLengths.firstOrNull() ?: 0f
    val primaryAperture: Float get() = apertures.firstOrNull() ?: 0f
    val hasVariableAperture: Boolean get() = apertures.size > 1
}
