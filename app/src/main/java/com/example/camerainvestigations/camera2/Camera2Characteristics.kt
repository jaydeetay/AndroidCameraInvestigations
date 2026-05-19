package com.example.camerainvestigations.camera2

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.os.Build
import android.util.Range
import android.util.SizeF
import com.example.camerainvestigations.model.CameraCapabilities
import com.example.camerainvestigations.model.PhysicalCamera

object Camera2Characteristics {

    fun readAll(context: Context): List<CameraCapabilities> {
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        return manager.cameraIdList.mapNotNull { id ->
            runCatching { read(manager, id) }
                .onFailure { android.util.Log.e("Camera2Char", "Failed to read camera $id", it) }
                .getOrNull()
        }
    }

    fun read(manager: CameraManager, cameraId: String): CameraCapabilities {
        val c = manager.getCameraCharacteristics(cameraId)

        val capabilities = c.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: intArrayOf()
        val isLogical = capabilities.contains(CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA)
        val supportsRaw = capabilities.contains(CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_RAW)

        @Suppress("UNCHECKED_CAST")
        val oisModes = c.keys
            .firstOrNull { it.name == "android.lens.info.availableOpticalStabilization" }
            ?.let { c.get(it as CameraCharacteristics.Key<IntArray>) }
            ?: intArrayOf()
        val supportsOis = oisModes.contains(CameraMetadata.LENS_OPTICAL_STABILIZATION_MODE_ON)

        val physicalIds = if (isLogical) c.physicalCameraIds else emptySet()
        val physicalCameras = physicalIds.mapNotNull { pid ->
            runCatching {
                val pc = manager.getCameraCharacteristics(pid)
                PhysicalCamera(
                    id = pid,
                    focalLength = pc.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.firstOrNull() ?: 0f,
                    aperture = pc.get(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES)?.firstOrNull() ?: 0f
                )
            }
            .onFailure { android.util.Log.e("Camera2Char", "Failed to read physical camera $pid", it) }
            .getOrNull()
        }

        val sensorSize = c.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
        val zoomRange: Range<Float>? = if (Build.VERSION.SDK_INT >= 30) {
            c.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE)
        } else null

        return CameraCapabilities(
            cameraId = cameraId,
            facing = c.get(CameraCharacteristics.LENS_FACING) ?: CameraCharacteristics.LENS_FACING_BACK,
            focalLengths = c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.toList() ?: emptyList(),
            apertures = c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES)?.toList() ?: emptyList(),
            isoRange = c.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE),
            exposureTimeRange = c.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE),
            hardwareLevel = c.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)
                ?: CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY,
            supportsRaw = supportsRaw,
            supportsOis = supportsOis,
            isLogicalCamera = isLogical,
            physicalCameras = physicalCameras,
            sensorSizeMm = sensorSize?.let { SizeF(it.width, it.height) },
            zoomRatioRange = zoomRange
        )
    }

    /**
     * Returns a human-readable dump of all CameraCharacteristics keys for display
     * in the capabilities bottom sheet.
     */
    fun dumpAll(manager: CameraManager, cameraId: String): List<Pair<String, String>> {
        val c = manager.getCameraCharacteristics(cameraId)
        val result = mutableListOf<Pair<String, String>>()

        fun add(key: String, value: Any?) {
            if (value != null) result += key to value.toString()
        }

        add("Camera ID", cameraId)
        add("Facing", c.get(CameraCharacteristics.LENS_FACING)?.let {
            when (it) {
                CameraCharacteristics.LENS_FACING_BACK  -> "BACK"
                CameraCharacteristics.LENS_FACING_FRONT -> "FRONT"
                CameraCharacteristics.LENS_FACING_EXTERNAL -> "EXTERNAL"
                else -> "UNKNOWN($it)"
            }
        })
        add("Hardware Level", when (c.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)) {
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY -> "LEGACY"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED -> "LIMITED"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL -> "FULL"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3 -> "LEVEL_3"
            else -> "UNKNOWN"
        })
        add("Focal Lengths (mm)", c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.contentToString())
        add("Apertures (f/)", c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES)?.contentToString())
        add("ISO Range", c.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE))
        add("Exposure Time Range (ns)", c.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE))
        add("Sensor Size (mm)", c.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE))
        add("Sensor Pixel Array", c.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE))
        add("RAW Support", c.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
            ?.contains(CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_RAW))
        
        @Suppress("UNCHECKED_CAST")
        val oisModes = c.keys
            .firstOrNull { it.name == "android.lens.info.availableOpticalStabilization" }
            ?.let { c.get(it as CameraCharacteristics.Key<IntArray>) }
        if (oisModes != null) {
            val modeStrings = oisModes.toList().map { mode ->
                when (mode) {
                    CameraMetadata.LENS_OPTICAL_STABILIZATION_MODE_ON  -> "OIS_ON"
                    CameraMetadata.LENS_OPTICAL_STABILIZATION_MODE_OFF -> "OIS_OFF"
                    else -> "OIS_UNKNOWN($mode)"
                }
            }
            add("OIS Modes", modeStrings)
        }

        add("Noise Reduction Modes", c.get(CameraCharacteristics.NOISE_REDUCTION_AVAILABLE_NOISE_REDUCTION_MODES)?.contentToString())
        add("Max Analog Sensitivity", c.get(CameraCharacteristics.SENSOR_MAX_ANALOG_SENSITIVITY))
        add("Flash Available", c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE))
        add("Logical Multi-Camera", c.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
            ?.contains(CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA))
        if (Build.VERSION.SDK_INT >= 30) {
            add("Zoom Ratio Range", c.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE))
        }
        add("Active Array Size", c.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE))
        add("AE Modes", c.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_MODES)?.contentToString())
        add("AWB Modes", c.get(CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES)?.contentToString())
        add("AF Modes", c.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES)?.contentToString())

        return result
    }
}
