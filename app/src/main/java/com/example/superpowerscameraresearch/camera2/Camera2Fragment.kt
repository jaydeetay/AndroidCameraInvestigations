package com.example.superpowerscameraresearch.camera2

import android.content.Context
import android.graphics.Color
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.fragment.app.Fragment
import com.example.superpowerscameraresearch.databinding.FragmentCamera2Binding
import com.example.superpowerscameraresearch.model.CameraCapabilities
import com.example.superpowerscameraresearch.model.CameraSettings
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlin.math.exp
import kotlin.math.ln

class Camera2Fragment : Fragment() {

    private var _binding: FragmentCamera2Binding? = null
    private val binding get() = _binding!!

    private lateinit var controller: Camera2Controller
    private lateinit var allCapabilities: List<CameraCapabilities>
    private lateinit var currentCapabilities: CameraCapabilities
    private var settings = CameraSettings()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCamera2Binding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val manager = requireContext().getSystemService(Context.CAMERA_SERVICE) as CameraManager
        allCapabilities = Camera2Characteristics.readAll(requireContext())
        currentCapabilities = allCapabilities.firstOrNull {
            it.facing == CameraCharacteristics.LENS_FACING_BACK
        } ?: allCapabilities.first()

        controller = Camera2Controller(
            context = requireContext(),
            textureView = binding.textureView,
            cameraId = currentCapabilities.cameraId,
            onSettingsConfirmed = { confirmed ->
                activity?.runOnUiThread {
                    _binding?.tvIso?.text = "ISO ${confirmed.iso}"
                    _binding?.tvShutter?.text = CameraSettings.shutterNsToDisplay(confirmed.shutterNs)
                }
            },
            onFpsUpdate = { fps ->
                activity?.runOnUiThread {
                    _binding?.tvFps?.text = "${"%.1f".format(fps)} fps"
                }
            },
            onHistogramReady = { hist, clipping ->
                activity?.runOnUiThread { _binding?.histogramView?.update(hist, clipping) }
            },
            onLiveStatsUpdate = { aperture, focalLength, focusDistance, aeState ->
                activity?.runOnUiThread {
                    val ap = aperture?.let { "f/${"%.1f".format(it)}" } ?: "f/?"
                    val fl = focalLength?.let { "${"%.0f".format(it)}mm" } ?: "?mm"
                    val fd = focusDistance?.let { CameraSettings.focusDistanceToDisplay(it) } ?: "?D"
                    _binding?.tvLiveStats?.text = "$ap  $fl  $fd  AE:$aeState"
                }
            }
        )

        setupPills()
        setupSlider()
        setupCameraSelector()
        setupCapture()
        setupInfo()
        updateHardwareLevelBadge()

        binding.textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) =
                controller.openCamera()
            override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) = Unit
            override fun onSurfaceTextureDestroyed(st: SurfaceTexture) = true
            override fun onSurfaceTextureUpdated(st: SurfaceTexture) = Unit
        }
    }

    override fun onResume() {
        super.onResume()
        if (binding.textureView.isAvailable) controller.openCamera()
    }

    override fun onPause() {
        super.onPause()
        controller.closeCamera()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        controller.destroy()
        _binding = null
    }

    // ── Parameter pills ──────────────────────────────────────────────────────

    private fun setupPills() {
        binding.pillsContainer.removeAllViews()

        val params = mutableListOf(
            Triple("ISO",   "#FFE88888") { showIsoSlider() },
            Triple("SS",    "#FF88E888") { showShutterSlider() },
            Triple("WB",    "#FF8888E8") { showWbSlider() },
            Triple("FOCUS", "#FFEAA888") { showFocusSlider() },
            Triple("ZOOM",  "#FFAA88E8") { showZoomSlider() }
        )
        if (currentCapabilities.supportsOis) {
            params += Triple("OIS", "#FF88E8E8") { toggleOis() }
        }
        params.forEach { (label, colorHex, action) ->
            val pill = TextView(requireContext()).apply {
                text = label
                setTextColor(Color.parseColor(colorHex))
                background = androidx.core.content.ContextCompat.getDrawable(requireContext(), com.example.superpowerscameraresearch.R.drawable.hud_label_bg)
                setPadding(16, 8, 16, 8)
                textSize = 11f
                typeface = android.graphics.Typeface.MONOSPACE
                tag = label
                setOnClickListener { action() }
            }
            binding.pillsContainer.addView(pill)
            (pill.layoutParams as LinearLayout.LayoutParams).marginEnd = 6
        }
    }

    private fun toggleOis() {
        settings = settings.copy(oisEnabled = !settings.oisEnabled)
        controller.applySettings(settings)
        binding.pillsContainer.findViewWithTag<TextView>("OIS")?.text =
            if (settings.oisEnabled) "OIS ON" else "OIS OFF"
    }

    // ── Slider helpers ───────────────────────────────────────────────────────

    private fun showIsoSlider() {
        val range = currentCapabilities.isoRange ?: return
        showSlider("ISO", "${range.lower} – ${range.upper}") { progress ->
            val iso = (range.lower + (range.upper - range.lower).toLong() * progress / 1000).toInt()
            settings = settings.copy(iso = iso, isoAuto = false)
            binding.tvParamValue.text = iso.toString()
            controller.applySettings(settings)
        }
        binding.seekBar.progress = ((settings.iso - range.lower).toFloat() /
                (range.upper - range.lower) * 1000).toInt()
        binding.tvParamValue.text = settings.iso.toString()
    }

    private fun showShutterSlider() {
        val range = currentCapabilities.exposureTimeRange ?: return
        val logMin = ln(range.lower.toDouble())
        val logMax = ln(range.upper.toDouble())
        showSlider(
            "SHUTTER",
            "${CameraSettings.shutterNsToDisplay(range.lower)} – ${CameraSettings.shutterNsToDisplay(range.upper)}"
        ) { progress ->
            val ns = exp(logMin + (logMax - logMin) * progress / 1000).toLong()
            settings = settings.copy(shutterNs = ns, shutterAuto = false)
            binding.tvParamValue.text = CameraSettings.shutterNsToDisplay(ns)
            controller.applySettings(settings)
        }
        val logVal = ln(settings.shutterNs.toDouble())
        binding.seekBar.progress = ((logVal - logMin) / (logMax - logMin) * 1000).toInt()
        binding.tvParamValue.text = CameraSettings.shutterNsToDisplay(settings.shutterNs)
    }

    private fun showWbSlider() {
        showSlider("WHITE BALANCE", "2000K – 8000K") { progress ->
            val k = 2000 + 6000 * progress / 1000
            settings = settings.copy(whiteBalanceK = k, wbAuto = false)
            binding.tvParamValue.text = "${k}K"
            controller.applySettings(settings)
        }
        binding.seekBar.progress = ((settings.whiteBalanceK - 2000).toFloat() / 6000 * 1000).toInt()
        binding.tvParamValue.text = "${settings.whiteBalanceK}K"
    }

    private fun showFocusSlider() {
        showSlider("FOCUS DISTANCE", "∞ – 10D") { progress ->
            val d = progress / 1000f * 10f
            settings = settings.copy(focusDistance = d)
            binding.tvParamValue.text = CameraSettings.focusDistanceToDisplay(d)
            controller.applySettings(settings)
        }
        binding.seekBar.progress = (settings.focusDistance / 10f * 1000).toInt()
        binding.tvParamValue.text = CameraSettings.focusDistanceToDisplay(settings.focusDistance)
    }

    private fun showZoomSlider() {
        val maxZoom = currentCapabilities.zoomRatioRange?.upper ?: 10f
        showSlider("ZOOM", "1.0× – ${"%.1f".format(maxZoom)}×") { progress ->
            val z = 1f + (maxZoom - 1f) * progress / 1000f
            settings = settings.copy(zoom = z)
            binding.tvParamValue.text = "${"%.1f".format(z)}×"
            controller.applySettings(settings)
        }
        binding.seekBar.progress = ((settings.zoom - 1f) / (maxZoom - 1f) * 1000).toInt()
        binding.tvParamValue.text = "${"%.1f".format(settings.zoom)}×"
    }

    private fun showSlider(name: String, range: String, onProgress: (Int) -> Unit) {
        binding.tvParamName.text = name
        binding.tvParamRange.text = range
        binding.sliderPanel.visibility = View.VISIBLE
        binding.seekBar.max = 1000
        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                if (fromUser) onProgress(p)
            }
            override fun onStartTrackingTouch(sb: SeekBar) = Unit
            override fun onStopTrackingTouch(sb: SeekBar) = Unit
        })
    }

    private fun setupSlider() {
        binding.root.setOnClickListener {
            binding.sliderPanel.visibility = View.GONE
        }
        binding.sliderPanel.setOnClickListener { /* absorb — don't dismiss */ }
    }

    // ── Camera selector ──────────────────────────────────────────────────────

    private fun setupCameraSelector() {
        binding.tvCameraSelector.setOnClickListener { showCameraSelectorSheet() }
        updateCameraSelectorLabel()
    }

    private fun updateCameraSelectorLabel() {
        binding.tvCameraSelector.text = "${"%.0f".format(currentCapabilities.primaryFocalLength)}mm ▾"
    }

    private fun showCameraSelectorSheet() {
        val sheet = BottomSheetDialog(requireContext())
        val scrollView = android.widget.ScrollView(requireContext())
        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, 24)
        }
        scrollView.addView(container)

        allCapabilities.forEach { cap ->
            val row = TextView(requireContext()).apply {
                text = "${cap.facingName}  ${"%.0f".format(cap.primaryFocalLength)}mm  " +
                        "f/${"%.1f".format(cap.primaryAperture)}  ${cap.hardwareLevelName}  " +
                        "RAW:${cap.supportsRaw}  OIS:${cap.supportsOis}"
                setTextColor(0xFFCCCCCC.toInt())
                textSize = 12f
                typeface = android.graphics.Typeface.MONOSPACE
                setPadding(32, 20, 32, 20)
                setBackgroundColor(if (cap.cameraId == currentCapabilities.cameraId) 0x22FFFFFF else 0x00000000)
                setOnClickListener {
                    currentCapabilities = cap
                    controller.switchCamera(cap.cameraId)
                    updateCameraSelectorLabel()
                    updateHardwareLevelBadge()
                    setupPills()  // re-create pills (OIS may change)
                    setupCapture()
                    sheet.dismiss()
                }
            }
            container.addView(row)
        }

        sheet.setContentView(scrollView)
        sheet.show()
    }

    private fun updateHardwareLevelBadge() {
        binding.tvHardwareLevel.text = currentCapabilities.hardwareLevelName
    }

    // ── RAW capture ──────────────────────────────────────────────────────────

    private fun setupCapture() {
        val supported = currentCapabilities.supportsRaw
        binding.captureButton.alpha = if (supported) 1f else 0.4f
        binding.captureButton.isEnabled = supported
        binding.tvRawUnsupported.visibility = if (supported) android.view.View.GONE else android.view.View.VISIBLE
        binding.captureButton.setOnClickListener {
            if (supported) {
                it.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                it.animate()
                    .scaleX(1.3f).scaleY(1.3f)
                    .setDuration(120)
                    .withEndAction {
                        it.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
                    }
                    .start()
                controller.captureRaw()
            }
        }
    }

    // ── Capabilities info ────────────────────────────────────────────────────

    private fun setupInfo() {
        binding.btnInfo.setOnClickListener { showCapabilitiesSheet() }
    }

    private fun showCapabilitiesSheet() {
        val manager = requireContext().getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val entries = Camera2Characteristics.dumpAll(manager, currentCapabilities.cameraId)

        val sheet = BottomSheetDialog(requireContext())
        val scrollView = android.widget.ScrollView(requireContext())
        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 16, 32, 32)
        }
        scrollView.addView(container)

        entries.forEach { (key, value) ->
            val row = TextView(requireContext()).apply {
                text = "$key\n$value"
                setTextColor(0xFFCCCCCC.toInt())
                textSize = 11f
                typeface = android.graphics.Typeface.MONOSPACE
                setPadding(0, 8, 0, 8)
            }
            container.addView(row)
        }

        sheet.setContentView(scrollView)
        sheet.show()
    }
}
