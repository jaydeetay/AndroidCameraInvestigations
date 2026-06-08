package com.example.superpowerscameraresearch.camerax

import android.content.ContentValues
import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.RggbChannelVector
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.core.*
import androidx.camera.extensions.ExtensionMode
import androidx.camera.extensions.ExtensionsManager
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.example.superpowerscameraresearch.model.CameraSettings
import com.example.superpowerscameraresearch.overlay.HistogramComputer
import java.util.concurrent.Executors

class CameraXController(
    private val context: Context,
    private val previewView: PreviewView,
    private val lifecycleOwner: LifecycleOwner,
    private val onFpsUpdate: (Float) -> Unit,
    private val onHistogramReady: (IntArray, Boolean) -> Unit,
    private val onExtensionsAvailability: (Map<Int, Boolean>) -> Unit = {},
    private val onFrameAvailable: ((luma: ByteArray, stride: Int, width: Int, height: Int) -> Unit)? = null
) {
    private var provider: ProcessCameraProvider? = null
    private var extensionsManager: ExtensionsManager? = null
    private var imageCapture: ImageCapture? = null
    private var currentCameraId: String = ""
    private var currentSettings = CameraSettings()
    private val analysisExecutor = Executors.newSingleThreadExecutor()
    @Volatile private var lastTimestamp = 0L
    private var boundCamera: androidx.camera.core.Camera? = null

    fun start(cameraId: String) {
        currentCameraId = cameraId
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            val prov = providerFuture.get()
            provider = prov
            val extFuture = ExtensionsManager.getInstanceAsync(context, prov)
            extFuture.addListener({
                extensionsManager = runCatching { extFuture.get() }
                    .onFailure { Log.e(TAG, "ExtensionsManager init failed", it) }
                    .getOrNull()
                onExtensionsAvailability(queryAvailability(cameraId))
                bindUseCases()
            }, ContextCompat.getMainExecutor(context))
        }, ContextCompat.getMainExecutor(context))
    }

    private fun queryAvailability(cameraId: String): Map<Int, Boolean> {
        val mgr = extensionsManager ?: return emptyMap()
        val selector = buildSelectorForId(cameraId)
        return mapOf(
            ExtensionMode.NIGHT        to mgr.isExtensionAvailable(selector, ExtensionMode.NIGHT),
            ExtensionMode.HDR          to mgr.isExtensionAvailable(selector, ExtensionMode.HDR),
            ExtensionMode.BOKEH        to mgr.isExtensionAvailable(selector, ExtensionMode.BOKEH),
            ExtensionMode.FACE_RETOUCH to mgr.isExtensionAvailable(selector, ExtensionMode.FACE_RETOUCH),
            ExtensionMode.AUTO         to mgr.isExtensionAvailable(selector, ExtensionMode.AUTO)
        )
    }

    private fun buildSelectorForId(cameraId: String): CameraSelector =
        CameraSelector.Builder()
            .addCameraFilter { cameras ->
                cameras.filter { Camera2CameraInfo.from(it).cameraId == cameraId }.ifEmpty { cameras }
            }.build()

    private fun bindUseCases() {
        val prov = provider ?: return

        val previewBuilder = Preview.Builder()
        val analysisBuilder = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
        val captureBuilder = ImageCapture.Builder()

        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val characteristics = runCatching { cameraManager.getCameraCharacteristics(currentCameraId) }.getOrNull()

        @Suppress("UNCHECKED_CAST")
        val oisModes = characteristics?.keys
            ?.firstOrNull { it.name == "android.lens.info.availableOpticalStabilization" }
            ?.let { characteristics.get(it as CameraCharacteristics.Key<IntArray>) }
            ?: intArrayOf()
        val supportsOis = oisModes.contains(CameraMetadata.LENS_OPTICAL_STABILIZATION_MODE_ON)

        val baseSelector = buildSelectorForId(currentCameraId)

        val useNight = currentSettings.nightMode &&
                extensionsManager?.isExtensionAvailable(baseSelector, ExtensionMode.NIGHT) == true

        if (!useNight) {
            applyCamera2Interop(previewBuilder, analysisBuilder, captureBuilder, currentSettings, supportsOis)
        }

        val preview = previewBuilder.build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }

        val analysis = analysisBuilder.build().also { ia ->
            var frameCount = 0
            ia.setAnalyzer(analysisExecutor) { imageProxy ->
                val ts = imageProxy.imageInfo.timestamp
                if (lastTimestamp != 0L) {
                    val fps = 1_000_000_000f / (ts - lastTimestamp)
                    onFpsUpdate(fps)
                }
                lastTimestamp = ts

                if (++frameCount % 3 == 0) {
                    val plane = imageProxy.planes[0]
                    val bytes = plane.buffer.let { buf -> ByteArray(buf.remaining()).also { buf.get(it) } }
                    val histogram = HistogramComputer.compute(
                        bytes,
                        stride = plane.rowStride,
                        width = imageProxy.width,
                        height = imageProxy.height
                    )
                    onHistogramReady(histogram, HistogramComputer.isClipping(histogram))
                    onFrameAvailable?.invoke(bytes, plane.rowStride, imageProxy.width, imageProxy.height)
                }
                imageProxy.close()
            }
        }

        imageCapture = captureBuilder.build()

        val finalSelector = if (useNight) {
            extensionsManager!!.getExtensionEnabledCameraSelector(baseSelector, ExtensionMode.NIGHT)
        } else {
            baseSelector
        }

        prov.unbindAll()
        runCatching {
            // ImageAnalysis is incompatible with most CameraX extension modes.
            // Drop it when using night extension; histogram/FPS are suspended in that mode.
            val useCases = if (useNight) {
                arrayOf(preview, imageCapture!!)
            } else {
                arrayOf(preview, analysis, imageCapture!!)
            }
            boundCamera = prov.bindToLifecycle(lifecycleOwner, finalSelector, *useCases)
            if (useNight) {
                boundCamera?.cameraControl?.setZoomRatio(currentSettings.zoom)
            }
        }.onFailure {
            Log.e(TAG, "Failed to bind CameraX use cases", it)
        }
    }

    private fun applyCamera2Interop(
        previewBuilder: Preview.Builder,
        analysisBuilder: ImageAnalysis.Builder,
        captureBuilder: ImageCapture.Builder,
        s: CameraSettings,
        supportsOis: Boolean = false
    ) {
        listOf(
            Camera2Interop.Extender(previewBuilder),
            Camera2Interop.Extender(analysisBuilder),
            Camera2Interop.Extender(captureBuilder)
        ).forEach { ext ->
            if (s.isoAuto && s.shutterAuto) {
                ext.setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
            } else {
                ext.setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
                if (!s.isoAuto) ext.setCaptureRequestOption(CaptureRequest.SENSOR_SENSITIVITY, s.iso)
                if (!s.shutterAuto) ext.setCaptureRequestOption(CaptureRequest.SENSOR_EXPOSURE_TIME, s.shutterNs)
            }

            if (s.wbAuto) {
                ext.setCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO)
            } else {
                ext.setCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_OFF)
                ext.setCaptureRequestOption(CaptureRequest.COLOR_CORRECTION_MODE, CaptureRequest.COLOR_CORRECTION_MODE_TRANSFORM_MATRIX)
                ext.setCaptureRequestOption(CaptureRequest.COLOR_CORRECTION_GAINS, colorTemperatureToGains(s.whiteBalanceK))
            }

            ext.setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)
            ext.setCaptureRequestOption(CaptureRequest.LENS_FOCUS_DISTANCE, s.focusDistance)

            if (Build.VERSION.SDK_INT >= 30) {
                ext.setCaptureRequestOption(CaptureRequest.CONTROL_ZOOM_RATIO, s.zoom)
            }

            if (supportsOis) {
                ext.setCaptureRequestOption(
                    CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE,
                    if (s.oisEnabled) CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_ON
                    else CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_OFF
                )
            }
        }
    }

    fun applySettings(settings: CameraSettings) {
        currentSettings = settings
        bindUseCases()
    }

    fun switchCamera(cameraId: String) {
        currentCameraId = cameraId
        if (extensionsManager != null) {
            onExtensionsAvailability(queryAvailability(cameraId))
        }
        bindUseCases()
    }

    fun captureRaw() {
        val capture = imageCapture ?: return
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "IMG_${System.currentTimeMillis()}.jpg")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "DCIM/CameraInvestigations")
        }
        val options = ImageCapture.OutputFileOptions.Builder(
            context.contentResolver,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            values
        ).build()
        capture.takePicture(options, ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {}
                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "Capture failed", exc)
                }
            })
    }

    fun stop() {
        provider?.unbindAll()
        analysisExecutor.shutdown()
    }

    private fun colorTemperatureToGains(kelvin: Int): RggbChannelVector {
        val t = kelvin.coerceIn(2000, 8000).toFloat()
        val warm = (t - 2000f) / 6000f
        return RggbChannelVector(2.4f - 1.6f * warm, 1.0f, 1.0f, 0.5f + 1.7f * warm)
    }

    companion object {
        private const val TAG = "CameraXController"
    }
}
