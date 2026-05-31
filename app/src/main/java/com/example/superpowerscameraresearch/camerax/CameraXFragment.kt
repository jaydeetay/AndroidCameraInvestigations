package com.example.superpowerscameraresearch.camerax

import android.content.Context
import android.graphics.Color
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.superpowerscameraresearch.R
import com.example.superpowerscameraresearch.camera2.Camera2Characteristics
import com.example.superpowerscameraresearch.databinding.FragmentCameraxBinding
import com.example.superpowerscameraresearch.model.CameraCapabilities
import com.example.superpowerscameraresearch.model.CameraSettings
import com.example.superpowerscameraresearch.overlay.HistogramView
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlin.math.exp
import kotlin.math.ln

class CameraXFragment : Fragment() {

    private var _binding: FragmentCameraxBinding? = null
    private val binding get() = _binding!!

    private lateinit var controller: CameraXController
    private lateinit var allCapabilities: List<CameraCapabilities>
    private lateinit var currentCapabilities: CameraCapabilities
    private var settings = CameraSettings()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCameraxBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        allCapabilities = Camera2Characteristics.readAll(requireContext())
        val defaultCam = allCapabilities.firstOrNull {
            it.facing == CameraCharacteristics.LENS_FACING_BACK
        } ?: allCapabilities.first()
        currentCapabilities = defaultCam

        controller = CameraXController(
            context = requireContext(),
            previewView = binding.previewView,
            lifecycleOwner = viewLifecycleOwner,
            onFpsUpdate = { fps ->
                activity?.runOnUiThread { _binding?.tvFps?.text = "${"%.1f".format(fps)} fps" }
            },
            onHistogramReady = { hist, clipping ->
                activity?.runOnUiThread { _binding?.histogramView?.update(hist, clipping) }
            }
        )

        setupPills()
        setupSlider()
        setupCameraSelector()
        setupCapture()
        setupInfo()
        updateHardwareLevelBadge()

        controller.start(defaultCam.cameraId)
    }

    override fun onResume() {
        super.onResume()
        binding.root.keepScreenOn = true
    }

    override fun onPause() {
        super.onPause()
        binding.root.keepScreenOn = false
    }

    override fun onDestroyView() {
        super.onDestroyView()
        controller.stop()
        _binding = null
    }

    private fun setupPills() {
        binding.pillsContainer.removeAllViews()
        val params = mutableListOf(
            Triple("ISO", "#FFE88888") { showIsoSlider() },
            Triple("SS",  "#FF88E888") { showShutterSlider() },
            Triple("WB",  "#FF8888E8") { showWbSlider() },
            Triple("FOCUS","#FFEAA888") { showFocusSlider() },
            Triple("ZOOM","#FFAA88E8") { showZoomSlider() }
        )
        if (currentCapabilities.supportsOis) {
            params += Triple("OIS", "#FF88E8E8") { toggleOis() }
        }
        params.forEach { (label, colorHex, action) ->
            val pill = TextView(requireContext()).apply {
                text = label
                tag = label
                setTextColor(Color.parseColor(colorHex))
                background = ContextCompat.getDrawable(requireContext(), R.drawable.hud_label_bg)
                setPadding(16, 8, 16, 8)
                textSize = 11f
                typeface = android.graphics.Typeface.MONOSPACE
                setOnClickListener { action() }
            }
            binding.pillsContainer.addView(pill)
            val lp = pill.layoutParams as LinearLayout.LayoutParams
            lp.marginEnd = 6
            pill.layoutParams = lp
        }
    }

    private fun toggleOis() {
        settings = settings.copy(oisEnabled = !settings.oisEnabled)
        controller.applySettings(settings)
        binding.pillsContainer.findViewWithTag<TextView>("OIS")?.text =
            if (settings.oisEnabled) "OIS ON" else "OIS OFF"
    }

    private fun showIsoSlider() {
        val range = currentCapabilities.isoRange ?: return
        binding.tvParamName.text = "ISO"
        binding.tvParamRange.text = "${range.lower} – ${range.upper}"
        binding.sliderPanel.visibility = View.VISIBLE
        binding.seekBar.max = 1000
        binding.seekBar.progress = ((settings.iso - range.lower).toFloat() /
                (range.upper - range.lower) * 1000).toInt()
        binding.tvParamValue.text = settings.iso.toString()
        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                if (!fromUser) return
                val iso = range.lower + ((range.upper - range.lower).toLong() * p / 1000).toInt()
                settings = settings.copy(iso = iso, isoAuto = false)
                binding.tvParamValue.text = iso.toString()
                controller.applySettings(settings)
            }
            override fun onStartTrackingTouch(sb: SeekBar) = Unit
            override fun onStopTrackingTouch(sb: SeekBar) = Unit
        })
    }

    private fun showShutterSlider() {
        val range = currentCapabilities.exposureTimeRange ?: return
        val logMin = ln(range.lower.toDouble())
        val logMax = ln(range.upper.toDouble())
        binding.tvParamName.text = "SHUTTER"
        binding.tvParamRange.text = "${CameraSettings.shutterNsToDisplay(range.lower)} – ${CameraSettings.shutterNsToDisplay(range.upper)}"
        binding.sliderPanel.visibility = View.VISIBLE
        binding.seekBar.max = 1000
        binding.seekBar.progress = ((ln(settings.shutterNs.toDouble()) - logMin) / (logMax - logMin) * 1000).toInt()
        binding.tvParamValue.text = CameraSettings.shutterNsToDisplay(settings.shutterNs)
        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                if (!fromUser) return
                val ns = exp(logMin + (logMax - logMin) * p / 1000).toLong()
                settings = settings.copy(shutterNs = ns, shutterAuto = false)
                binding.tvParamValue.text = CameraSettings.shutterNsToDisplay(ns)
                controller.applySettings(settings)
            }
            override fun onStartTrackingTouch(sb: SeekBar) = Unit
            override fun onStopTrackingTouch(sb: SeekBar) = Unit
        })
    }

    private fun showWbSlider() {
        binding.tvParamName.text = "WHITE BALANCE"
        binding.tvParamRange.text = "2000K – 8000K"
        binding.sliderPanel.visibility = View.VISIBLE
        binding.seekBar.max = 1000
        binding.seekBar.progress = ((settings.whiteBalanceK - 2000).toFloat() / 6000 * 1000).toInt()
        binding.tvParamValue.text = "${settings.whiteBalanceK}K"
        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                if (!fromUser) return
                val k = 2000 + (6000 * p / 1000)
                settings = settings.copy(whiteBalanceK = k, wbAuto = false)
                binding.tvParamValue.text = "${k}K"
                controller.applySettings(settings)
            }
            override fun onStartTrackingTouch(sb: SeekBar) = Unit
            override fun onStopTrackingTouch(sb: SeekBar) = Unit
        })
    }

    private fun showFocusSlider() {
        binding.tvParamName.text = "FOCUS DISTANCE"
        binding.tvParamRange.text = "∞ – 10D"
        binding.sliderPanel.visibility = View.VISIBLE
        binding.seekBar.max = 1000
        binding.seekBar.progress = (settings.focusDistance / 10f * 1000).toInt()
        binding.tvParamValue.text = CameraSettings.focusDistanceToDisplay(settings.focusDistance)
        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                if (!fromUser) return
                val d = p / 1000f * 10f
                settings = settings.copy(focusDistance = d)
                binding.tvParamValue.text = CameraSettings.focusDistanceToDisplay(d)
                controller.applySettings(settings)
            }
            override fun onStartTrackingTouch(sb: SeekBar) = Unit
            override fun onStopTrackingTouch(sb: SeekBar) = Unit
        })
    }

    private fun showZoomSlider() {
        val maxZoom = currentCapabilities.zoomRatioRange?.upper ?: 10f
        binding.tvParamName.text = "ZOOM"
        binding.tvParamRange.text = "1.0× – ${"%.1f".format(maxZoom)}×"
        binding.sliderPanel.visibility = View.VISIBLE
        binding.seekBar.max = 1000
        binding.seekBar.progress = ((settings.zoom - 1f) / (maxZoom - 1f) * 1000).toInt()
        binding.tvParamValue.text = "${"%.1f".format(settings.zoom)}×"
        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                if (!fromUser) return
                val z = 1f + (maxZoom - 1f) * p / 1000f
                settings = settings.copy(zoom = z)
                binding.tvParamValue.text = "${"%.1f".format(z)}×"
                controller.applySettings(settings)
            }
            override fun onStartTrackingTouch(sb: SeekBar) = Unit
            override fun onStopTrackingTouch(sb: SeekBar) = Unit
        })
    }

    private fun setupSlider() {
        binding.root.setOnClickListener { binding.sliderPanel.visibility = View.GONE }
    }

    private fun setupCameraSelector() {
        binding.tvCameraSelector.setOnClickListener { showCameraSelectorSheet() }
        binding.tvCameraSelector.text = "${"%.0f".format(currentCapabilities.primaryFocalLength)}mm ▾"
    }

    private fun showCameraSelectorSheet() {
        val sheet = BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(R.layout.bottom_sheet_camera_selector, null)
        val container = view.findViewById<ViewGroup>(R.id.cameraListContainer)

        allCapabilities.forEach { cap ->
            val row = TextView(requireContext()).apply {
                text = "${cap.facingName} — ${"%.0f".format(cap.primaryFocalLength)}mm  " +
                        "f/${"%.1f".format(cap.primaryAperture)}  ${cap.hardwareLevelName}  RAW:${cap.supportsRaw}"
                setTextColor(0xFFCCCCCC.toInt())
                textSize = 13f
                typeface = android.graphics.Typeface.MONOSPACE
                setPadding(24, 16, 24, 16)
                setOnClickListener {
                    currentCapabilities = cap
                    controller.switchCamera(cap.cameraId)
                    binding.tvCameraSelector.text = "${"%.0f".format(cap.primaryFocalLength)}mm ▾"
                    updateHardwareLevelBadge()
                    setupPills()
                    setupCapture()
                    sheet.dismiss()
                }
            }
            container.addView(row)
        }
        sheet.setContentView(view)
        sheet.show()
    }

    private fun updateHardwareLevelBadge() {
        binding.tvHardwareLevel.text = currentCapabilities.hardwareLevelName
    }

    private fun setupCapture() {
        val supported = currentCapabilities.supportsRaw
        binding.captureButton.alpha = if (supported) 1f else 0.4f
        binding.captureButton.isEnabled = supported
        binding.tvRawUnsupported.visibility = if (supported) View.GONE else View.VISIBLE
        binding.captureButton.setOnClickListener {
            if (supported) controller.captureRaw()
        }
    }

    private fun setupInfo() {
        binding.btnInfo.setOnClickListener { showCapabilitiesSheet() }
    }

    private fun showCapabilitiesSheet() {
        val manager = requireContext().getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val entries = Camera2Characteristics.dumpAll(manager, currentCapabilities.cameraId)
        val sheet = BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(R.layout.bottom_sheet_capabilities, null)
        val container = view.findViewById<ViewGroup>(R.id.capabilitiesContainer)
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
        sheet.setContentView(view)
        sheet.show()
    }
}
