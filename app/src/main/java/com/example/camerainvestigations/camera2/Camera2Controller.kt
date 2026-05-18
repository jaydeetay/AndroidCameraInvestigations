package com.example.camerainvestigations.camera2

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.hardware.camera2.params.RggbChannelVector
import android.media.ImageReader
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import android.view.TextureView
import com.example.camerainvestigations.model.CameraSettings
import com.example.camerainvestigations.overlay.HistogramComputer

class Camera2Controller(
    private val context: Context,
    private val textureView: TextureView,
    private var cameraId: String,
    private val onSettingsConfirmed: (CameraSettings) -> Unit,
    private val onFpsUpdate: (Float) -> Unit,
    private val onHistogramReady: (IntArray, Boolean) -> Unit
) {
    private val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null

    private val cameraThread = HandlerThread("Camera2Worker").also { it.start() }
    private val cameraHandler = Handler(cameraThread.looper)

    private var histogramReader: ImageReader? = null
    private var histogramFrameCount = 0

    private var lastFrameTimestamp = 0L
    var currentSettings = CameraSettings()
        private set

    val characteristics: CameraCharacteristics
        get() = manager.getCameraCharacteristics(cameraId)

    @SuppressLint("MissingPermission")
    fun openCamera() {
        val st = textureView.surfaceTexture ?: return
        st.setDefaultBufferSize(1920, 1080)

        histogramReader = ImageReader.newInstance(640, 360, ImageFormat.YUV_420_888, 2).apply {
            setOnImageAvailableListener({ reader ->
                if (++histogramFrameCount % 3 != 0) {
                    reader.acquireLatestImage()?.close()
                    return@setOnImageAvailableListener
                }
                val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
                val plane = image.planes[0]
                val bytes = ByteArray(plane.buffer.remaining())
                plane.buffer.get(bytes)
                val histogram = HistogramComputer.compute(
                    bytes, stride = plane.rowStride,
                    width = image.width, height = image.height
                )
                val clipping = HistogramComputer.isClipping(histogram)
                onHistogramReady(histogram, clipping)
                image.close()
            }, cameraHandler)
        }

        manager.openCamera(cameraId, object : CameraDevice.StateCallback() {
            override fun onOpened(device: CameraDevice) {
                cameraDevice = device
                startPreviewSession(Surface(st))
            }
            override fun onDisconnected(device: CameraDevice) { device.close(); cameraDevice = null }
            override fun onError(device: CameraDevice, error: Int) { device.close(); cameraDevice = null }
        }, cameraHandler)
    }

    private fun startPreviewSession(previewSurface: Surface) {
        val histSurface = histogramReader!!.surface
        cameraDevice!!.createCaptureSession(
            listOf(previewSurface, histSurface),
            object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    captureSession = session
                    issueRepeatingRequest(previewSurface, histSurface)
                }
                override fun onConfigureFailed(session: CameraCaptureSession) {}
            },
            cameraHandler
        )
    }

    private fun issueRepeatingRequest(previewSurface: Surface, histSurface: Surface) {
        val builder = cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
            addTarget(previewSurface)
            addTarget(histSurface)
            applySettings(this, currentSettings)
        }
        captureSession?.setRepeatingRequest(builder.build(), captureCallback, cameraHandler)
    }

    private fun applySettings(builder: CaptureRequest.Builder, s: CameraSettings) {
        if (s.isoAuto && s.shutterAuto) {
            builder[CaptureRequest.CONTROL_AE_MODE] = CaptureRequest.CONTROL_AE_MODE_ON
        } else {
            builder[CaptureRequest.CONTROL_AE_MODE] = CaptureRequest.CONTROL_AE_MODE_OFF
            if (!s.isoAuto) builder[CaptureRequest.SENSOR_SENSITIVITY] = s.iso
            if (!s.shutterAuto) builder[CaptureRequest.SENSOR_EXPOSURE_TIME] = s.shutterNs
        }

        if (s.wbAuto) {
            builder[CaptureRequest.CONTROL_AWB_MODE] = CaptureRequest.CONTROL_AWB_MODE_AUTO
        } else {
            builder[CaptureRequest.CONTROL_AWB_MODE] = CaptureRequest.CONTROL_AWB_MODE_OFF
            builder[CaptureRequest.COLOR_CORRECTION_MODE] = CaptureRequest.COLOR_CORRECTION_MODE_TRANSFORM_MATRIX
            builder[CaptureRequest.COLOR_CORRECTION_GAINS] = colorTemperatureToGains(s.whiteBalanceK)
        }

        if (s.focusAuto) {
            builder[CaptureRequest.CONTROL_AF_MODE] = CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
        } else {
            builder[CaptureRequest.CONTROL_AF_MODE] = CaptureRequest.CONTROL_AF_MODE_OFF
            builder[CaptureRequest.LENS_FOCUS_DISTANCE] = s.focusDistance
        }

        val oisModes = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION_MODES) ?: intArrayOf()
        if (oisModes.contains(CameraMetadata.LENS_OPTICAL_STABILIZATION_MODE_ON)) {
            builder[CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE] =
                if (s.oisEnabled) CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_ON
                else CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_OFF
        }

        if (Build.VERSION.SDK_INT >= 30) {
            builder[CaptureRequest.CONTROL_ZOOM_RATIO] = s.zoom
        } else {
            val activeArray = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
            if (activeArray != null) {
                val cropW = (activeArray.width() / s.zoom).toInt()
                val cropH = (activeArray.height() / s.zoom).toInt()
                val left = (activeArray.width() - cropW) / 2
                val top = (activeArray.height() - cropH) / 2
                builder[CaptureRequest.SCALER_CROP_REGION] =
                    android.graphics.Rect(left, top, left + cropW, top + cropH)
            }
        }
    }

    fun applySettings(settings: CameraSettings) {
        currentSettings = settings
        val st = textureView.surfaceTexture ?: return
        val previewSurface = Surface(st)
        val histSurface = histogramReader?.surface ?: return
        val builder = cameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW) ?: return
        builder.addTarget(previewSurface)
        builder.addTarget(histSurface)
        applySettings(builder, settings)
        captureSession?.setRepeatingRequest(builder.build(), captureCallback, cameraHandler)
    }

    private val captureCallback = object : CameraCaptureSession.CaptureCallback() {
        override fun onCaptureCompleted(
            session: CameraCaptureSession,
            request: CaptureRequest,
            result: TotalCaptureResult
        ) {
            val ts = result.get(CaptureResult.SENSOR_TIMESTAMP) ?: return
            if (lastFrameTimestamp != 0L) {
                val dtNs = ts - lastFrameTimestamp
                if (dtNs > 0) onFpsUpdate(1_000_000_000f / dtNs)
            }
            lastFrameTimestamp = ts

            val confirmedIso = result.get(CaptureResult.SENSOR_SENSITIVITY)
            val confirmedSs  = result.get(CaptureResult.SENSOR_EXPOSURE_TIME)
            if (confirmedIso != null && confirmedSs != null) {
                onSettingsConfirmed(currentSettings.copy(iso = confirmedIso, shutterNs = confirmedSs))
            }
        }
    }

    fun switchCamera(newCameraId: String) {
        closeCamera()
        cameraId = newCameraId
        openCamera()
    }

    fun closeCamera() {
        captureSession?.close(); captureSession = null
        cameraDevice?.close(); cameraDevice = null
        histogramReader?.close(); histogramReader = null
    }

    fun destroy() {
        closeCamera()
        cameraThread.quitSafely()
    }

    /** Stub — full implementation added in Task 8. */
    fun captureRaw(context: Context) { /* implemented in Task 8 */ }

    private fun colorTemperatureToGains(kelvin: Int): RggbChannelVector {
        val t = kelvin.coerceIn(2000, 8000).toFloat()
        val warm = (t - 2000f) / 6000f
        val rGain = 2.4f - 1.6f * warm
        val bGain = 0.5f + 1.7f * warm
        return RggbChannelVector(rGain, 1.0f, 1.0f, bGain)
    }
}
