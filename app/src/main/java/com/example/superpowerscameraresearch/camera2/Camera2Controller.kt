package com.example.superpowerscameraresearch.camera2

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.hardware.camera2.params.RggbChannelVector
import android.media.ImageReader
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.provider.MediaStore
import android.util.Log
import android.view.Surface
import android.view.TextureView
import com.example.superpowerscameraresearch.model.CameraSettings
import com.example.superpowerscameraresearch.overlay.HistogramComputer

class Camera2Controller(
    private val context: Context,
    private val textureView: TextureView,
    private var cameraId: String,
    private val onSettingsConfirmed: (CameraSettings) -> Unit,
    private val onFpsUpdate: (Float) -> Unit,
    private val onHistogramReady: (IntArray, Boolean) -> Unit,
    private val onLiveStatsUpdate: (aperture: Float?, focalLength: Float?, focusDistance: Float?, aeState: String) -> Unit
) {
    private val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null

    private val cameraThread = HandlerThread("Camera2Worker").also { it.start() }
    private val cameraHandler = Handler(cameraThread.looper)

    private var histogramReader: ImageReader? = null
    private var histogramFrameCount = 0

    private var rawReader: ImageReader? = null
    private var pendingCaptureResult: TotalCaptureResult? = null
    private var pendingRawImage: android.media.Image? = null
    private val rawLock = Object()

    private var previewSurface: Surface? = null

    @Volatile private var isOpening = false
    @Volatile private var retryCount = 0

    private var cachedCharacteristics: CameraCharacteristics? = null

    private var lastFrameTimestamp = 0L
    var currentSettings = CameraSettings()
        private set

    val characteristics: CameraCharacteristics
        get() = cachedCharacteristics ?: manager.getCameraCharacteristics(cameraId).also {
            cachedCharacteristics = it
        }

    @SuppressLint("MissingPermission")
    fun openCamera() {
        if (isOpening) return
        isOpening = true
        val st = textureView.surfaceTexture ?: run { isOpening = false; return }
        st.setDefaultBufferSize(1920, 1080)
        previewSurface?.release()
        previewSurface = Surface(st)

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
                isOpening = false
                retryCount = 0
                cameraDevice = device
                startPreviewSession(previewSurface!!)
            }
            override fun onDisconnected(device: CameraDevice) {
                isOpening = false
                device.close()
                cameraDevice = null
                if (retryCount < MAX_RETRIES) {
                    retryCount++
                    cameraHandler.postDelayed({ openCamera() }, 500)
                } else {
                    Log.e(TAG, "Camera disconnected, max retries ($MAX_RETRIES) exhausted")
                }
            }
            override fun onError(device: CameraDevice, error: Int) {
                isOpening = false
                device.close()
                cameraDevice = null
                if (retryCount < MAX_RETRIES) {
                    retryCount++
                    cameraHandler.postDelayed({ openCamera() }, 500)
                } else {
                    Log.e(TAG, "Camera error $error, max retries ($MAX_RETRIES) exhausted")
                }
            }
        }, cameraHandler)
    }

    private fun startPreviewSession(previewSurface: Surface) {
        val histSurface = histogramReader!!.surface
        val caps = characteristics.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: intArrayOf()
        val rawSupported = caps.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW)

        if (rawSupported) {
            val pixelArraySize = characteristics.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)!!
            rawReader = ImageReader.newInstance(
                pixelArraySize.width, pixelArraySize.height,
                ImageFormat.RAW_SENSOR, 2
            ).apply {
                setOnImageAvailableListener({ reader ->
                    val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
                    synchronized(rawLock) {
                        pendingRawImage = image
                        tryWriteDng()
                    }
                }, cameraHandler)
            }
        }

        val surfaces = buildList {
            add(previewSurface)
            add(histSurface)
            rawReader?.surface?.let { add(it) }
        }

        cameraDevice!!.createCaptureSession(
            surfaces,
            object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    captureSession = session
                    issueRepeatingRequest(previewSurface, histSurface)
                }
                override fun onConfigureFailed(session: CameraCaptureSession) {
                    isOpening = false
                    Log.e(TAG, "CameraCaptureSession configuration failed for camera $cameraId")
                }
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

        builder[CaptureRequest.CONTROL_AF_MODE] = CaptureRequest.CONTROL_AF_MODE_OFF
        builder[CaptureRequest.LENS_FOCUS_DISTANCE] = s.focusDistance

        val oisModes = characteristics.get(
            CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION
        ) ?: intArrayOf()
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

        builder[CaptureRequest.NOISE_REDUCTION_MODE] = s.noiseReduction
    }

    fun applySettings(settings: CameraSettings) {
        currentSettings = settings
        val previewSurface = previewSurface ?: return
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

            val aperture      = result.get(CaptureResult.LENS_APERTURE)
            val focalLength   = result.get(CaptureResult.LENS_FOCAL_LENGTH)
            val focusDistance = result.get(CaptureResult.LENS_FOCUS_DISTANCE)
            val aeState       = aeStateToString(result.get(CaptureResult.CONTROL_AE_STATE))
            onLiveStatsUpdate(aperture, focalLength, focusDistance, aeState)
        }
    }

    fun switchCamera(newCameraId: String) {
        closeCamera()
        cameraId = newCameraId
        cachedCharacteristics = null
        retryCount = 0
        openCamera()
    }

    fun closeCamera() {
        captureSession?.close(); captureSession = null
        cameraDevice?.close(); cameraDevice = null
        histogramReader?.setOnImageAvailableListener(null, null)
        histogramReader?.close(); histogramReader = null
        rawReader?.setOnImageAvailableListener(null, null)
        rawReader?.close(); rawReader = null
        previewSurface?.release(); previewSurface = null
        histogramFrameCount = 0
    }

    fun destroy() {
        closeCamera()
        cameraThread.quitSafely()
    }

    fun captureRaw() {
        val rawReaderLocal = rawReader ?: return
        val surface = previewSurface ?: return

        val builder = cameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE) ?: return
        builder.addTarget(surface)
        builder.addTarget(rawReaderLocal.surface)
        applySettings(builder, currentSettings)
        builder[CaptureRequest.CONTROL_CAPTURE_INTENT] = CaptureRequest.CONTROL_CAPTURE_INTENT_STILL_CAPTURE

        captureSession?.capture(builder.build(), object : CameraCaptureSession.CaptureCallback() {
            override fun onCaptureCompleted(
                session: CameraCaptureSession,
                request: CaptureRequest,
                result: TotalCaptureResult
            ) {
                synchronized(rawLock) {
                    pendingCaptureResult = result
                    tryWriteDng()
                }
            }
        }, cameraHandler)
    }

    private fun tryWriteDng() {
        // Must be called while holding rawLock
        val image = pendingRawImage ?: return
        val result = pendingCaptureResult ?: return

        // Consume both to avoid double-write
        pendingRawImage = null
        pendingCaptureResult = null

        try {
            runCatching {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, "IMG_${System.currentTimeMillis()}.dng")
                    put(MediaStore.Images.Media.MIME_TYPE, "image/x-adobe-dng")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "DCIM/CameraInvestigations")
                }
                val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)!!
                DngCreator(characteristics, result).use { dng ->
                    context.contentResolver.openOutputStream(uri)!!.use { dng.writeImage(it, image) }
                }
            }.onFailure {
                Log.e(TAG, "Failed to write DNG", it)
            }
        } finally {
            image.close()
        }
    }

    private fun colorTemperatureToGains(kelvin: Int): RggbChannelVector {
        val t = kelvin.coerceIn(2000, 8000).toFloat()
        val warm = (t - 2000f) / 6000f
        val rGain = 2.4f - 1.6f * warm
        val bGain = 0.5f + 1.7f * warm
        return RggbChannelVector(rGain, 1.0f, 1.0f, bGain)
    }

    companion object {
        private const val TAG = "Camera2Controller"
        private const val MAX_RETRIES = 3

        fun aeStateToString(state: Int?): String = when (state) {
            CaptureResult.CONTROL_AE_STATE_SEARCHING -> "SEARCHING"
            CaptureResult.CONTROL_AE_STATE_CONVERGED -> "CONVERGED"
            CaptureResult.CONTROL_AE_STATE_LOCKED -> "LOCKED"
            CaptureResult.CONTROL_AE_STATE_FLASH_REQUIRED -> "FLASH_REQ"
            CaptureResult.CONTROL_AE_STATE_PRECAPTURE -> "PRECAPTURE"
            else -> "INACTIVE"
        }
    }
}
