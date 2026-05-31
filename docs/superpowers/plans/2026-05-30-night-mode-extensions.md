# Night Mode Extensions Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add night-mode controls to both camera tabs — `ExtensionMode.NIGHT` in CameraX (with extension enumeration in the capabilities sheet) and `CONTROL_SCENE_MODE_NIGHT` in Camera2 — locking out manual controls when night mode is active.

**Architecture:** `CameraSettings` carries two new boolean flags (`nightMode`, `nightSceneMode`). Each controller branches on the relevant flag during its settings-apply path. Fragments conditionally add a NIGHT pill based on runtime capability checks and lock out ISO/SS/WB/FOCUS/ZOOM pills while night mode is active. `ExtensionsManager` availability is queried asynchronously in `CameraXController.start()` and fed back to the fragment via a new callback.

**Tech Stack:** CameraX 1.3.4 + `androidx.camera:camera-extensions:1.3.4`, Camera2 API (minSdk 28), existing pill UI pattern.

---

## File Map

| File | Action | What changes |
|------|--------|--------------|
| `gradle/libs.versions.toml` | Modify | Add `androidx-camera-extensions` alias |
| `app/build.gradle.kts` | Modify | Add `camera-extensions` dependency |
| `app/src/main/java/…/model/CameraSettings.kt` | Modify | Add `nightMode`, `nightSceneMode` fields |
| `app/src/main/java/…/model/CameraCapabilities.kt` | Modify | Add `availableSceneModes: List<Int>` field |
| `app/src/test/java/…/model/CameraSettingsTest.kt` | Modify | Add tests for new fields |
| `app/src/main/java/…/camera2/Camera2Characteristics.kt` | Modify | Populate `availableSceneModes`; add scene modes to `dumpAll` |
| `app/src/main/java/…/camera2/Camera2Controller.kt` | Modify | Branch on `nightSceneMode` in `applySettings` |
| `app/src/main/java/…/camera2/Camera2Fragment.kt` | Modify | NIGHT pill + manual-pill lockout |
| `app/src/main/java/…/camerax/CameraXController.kt` | Modify | `ExtensionsManager`, night bind path, `onExtensionsAvailability` callback |
| `app/src/main/java/…/camerax/CameraXFragment.kt` | Modify | NIGHT pill + lockout + extension section in capabilities sheet |

All paths under `app/src/main/java/com/example/superpowerscameraresearch/`.

---

### Task 1: Add camera-extensions dependency

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`

- [ ] **Step 1: Add the library alias to the version catalog**

In `gradle/libs.versions.toml`, add one line after `androidx-camera-view`:

```toml
androidx-camera-extensions = { module = "androidx.camera:camera-extensions", version.ref = "camerax" }
```

The `camerax` version is already `"1.3.4"` — no new version entry needed.

- [ ] **Step 2: Add the dependency to build.gradle.kts**

In `app/build.gradle.kts`, add after `implementation(libs.androidx.camera.view)`:

```kotlin
implementation(libs.androidx.camera.extensions)
```

- [ ] **Step 3: Sync and verify the build resolves**

Run: `./gradlew :app:dependencies --configuration releaseRuntimeClasspath | grep camera-extensions`

Expected output contains: `androidx.camera:camera-extensions:1.3.4`

- [ ] **Step 4: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts
git commit -m "feat: add camera-extensions dependency"
```

---

### Task 2: Extend CameraSettings and CameraCapabilities

**Files:**
- Modify: `app/src/main/java/…/model/CameraSettings.kt`
- Modify: `app/src/main/java/…/model/CameraCapabilities.kt`
- Modify: `app/src/test/java/…/model/CameraSettingsTest.kt`

- [ ] **Step 1: Write failing tests**

Open `app/src/test/java/com/example/superpowerscameraresearch/model/CameraSettingsTest.kt` and add:

```kotlin
@Test
fun `default nightMode is false`() {
    assertFalse(CameraSettings().nightMode)
}

@Test
fun `default nightSceneMode is false`() {
    assertFalse(CameraSettings().nightSceneMode)
}

@Test
fun `copy preserves nightMode`() {
    val s = CameraSettings(nightMode = true)
    assertTrue(s.copy(iso = 400).nightMode)
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:test --tests "*.CameraSettingsTest" 2>&1 | tail -20`

Expected: compilation error — `nightMode` unresolved.

- [ ] **Step 3: Add fields to CameraSettings**

Replace the `data class CameraSettings(` block in `model/CameraSettings.kt` with:

```kotlin
data class CameraSettings(
    val isoAuto: Boolean = true,
    val iso: Int = 800,
    val shutterAuto: Boolean = true,
    val shutterNs: Long = 33_333_333L,
    val wbAuto: Boolean = true,
    val whiteBalanceK: Int = 4000,
    val focusDistance: Float = 0f,
    val zoom: Float = 1.0f,
    val oisEnabled: Boolean = true,
    val noiseReduction: Int = 1,
    val nightMode: Boolean = false,
    val nightSceneMode: Boolean = false
) {
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :app:test --tests "*.CameraSettingsTest" 2>&1 | tail -20`

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Add availableSceneModes to CameraCapabilities**

In `model/CameraCapabilities.kt`, add the field at the end of the data class constructor (before the closing `)`):

```kotlin
    val availableSceneModes: List<Int> = emptyList()
```

The full constructor now ends:

```kotlin
data class CameraCapabilities(
    val cameraId: String,
    val facing: Int,
    val focalLengths: List<Float>,
    val apertures: List<Float>,
    val isoRange: Range<Int>?,
    val exposureTimeRange: Range<Long>?,
    val hardwareLevel: Int,
    val supportsRaw: Boolean,
    val supportsOis: Boolean,
    val isLogicalCamera: Boolean,
    val physicalCameras: List<PhysicalCamera>,
    val sensorSizeMm: SizeF?,
    val zoomRatioRange: Range<Float>?,
    val availableSceneModes: List<Int> = emptyList()
) {
```

- [ ] **Step 6: Verify the project still compiles**

Run: `./gradlew :app:compileDebugKotlin 2>&1 | tail -20`

Expected: `BUILD SUCCESSFUL` (existing callers of `CameraCapabilities(...)` use named parameters, so the default value is safe)

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/example/superpowerscameraresearch/model/CameraSettings.kt \
        app/src/main/java/com/example/superpowerscameraresearch/model/CameraCapabilities.kt \
        app/src/test/java/com/example/superpowerscameraresearch/model/CameraSettingsTest.kt
git commit -m "feat: add nightMode fields to CameraSettings and CameraCapabilities"
```

---

### Task 3: Populate availableSceneModes in Camera2Characteristics

**Files:**
- Modify: `app/src/main/java/…/camera2/Camera2Characteristics.kt`

- [ ] **Step 1: Populate availableSceneModes in `read()`**

In `Camera2Characteristics.read()`, add a scene modes read before the `return CameraCapabilities(...)` call. Locate the line `val zoomRange: Range<Float>? = ...` and add immediately after it:

```kotlin
val sceneModes = c.get(CameraCharacteristics.CONTROL_AVAILABLE_SCENE_MODES)?.toList() ?: emptyList()
```

Then add `availableSceneModes = sceneModes` to the `CameraCapabilities(...)` constructor call in the same function:

```kotlin
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
    zoomRatioRange = zoomRange,
    availableSceneModes = sceneModes
)
```

- [ ] **Step 2: Add scene modes to `dumpAll()`**

In `Camera2Characteristics.dumpAll()`, add after the existing `add("AWB Modes", ...)` line at the bottom (before `return result`):

```kotlin
val sceneModesRaw = c.get(CameraCharacteristics.CONTROL_AVAILABLE_SCENE_MODES)
if (sceneModesRaw != null) {
    val names = sceneModesRaw.map { mode ->
        when (mode) {
            CameraMetadata.CONTROL_SCENE_MODE_DISABLED       -> "DISABLED"
            CameraMetadata.CONTROL_SCENE_MODE_FACE_PRIORITY  -> "FACE_PRIORITY"
            CameraMetadata.CONTROL_SCENE_MODE_ACTION         -> "ACTION"
            CameraMetadata.CONTROL_SCENE_MODE_PORTRAIT       -> "PORTRAIT"
            CameraMetadata.CONTROL_SCENE_MODE_LANDSCAPE      -> "LANDSCAPE"
            CameraMetadata.CONTROL_SCENE_MODE_NIGHT          -> "NIGHT"
            CameraMetadata.CONTROL_SCENE_MODE_NIGHT_PORTRAIT -> "NIGHT_PORTRAIT"
            CameraMetadata.CONTROL_SCENE_MODE_THEATRE        -> "THEATRE"
            CameraMetadata.CONTROL_SCENE_MODE_BEACH          -> "BEACH"
            CameraMetadata.CONTROL_SCENE_MODE_SNOW           -> "SNOW"
            CameraMetadata.CONTROL_SCENE_MODE_SUNSET         -> "SUNSET"
            CameraMetadata.CONTROL_SCENE_MODE_STEADYPHOTO    -> "STEADYPHOTO"
            CameraMetadata.CONTROL_SCENE_MODE_FIREWORKS      -> "FIREWORKS"
            CameraMetadata.CONTROL_SCENE_MODE_SPORTS         -> "SPORTS"
            CameraMetadata.CONTROL_SCENE_MODE_PARTY          -> "PARTY"
            CameraMetadata.CONTROL_SCENE_MODE_CANDLELIGHT    -> "CANDLELIGHT"
            CameraMetadata.CONTROL_SCENE_MODE_BARCODE        -> "BARCODE"
            else -> "UNKNOWN($mode)"
        }
    }
    add("Scene Modes", names.joinToString(", "))
}
```

- [ ] **Step 3: Verify compilation**

Run: `./gradlew :app:compileDebugKotlin 2>&1 | tail -20`

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/example/superpowerscameraresearch/camera2/Camera2Characteristics.kt
git commit -m "feat: populate availableSceneModes and add scene modes to dumpAll"
```

---

### Task 4: Camera2Controller — night scene mode support

**Files:**
- Modify: `app/src/main/java/…/camera2/Camera2Controller.kt`

The private `applySettings(builder, s)` function currently applies AE/AWB/AF/OIS/Zoom/NR. When `nightSceneMode = true` we need `USE_SCENE_MODE` + `SCENE_MODE_NIGHT` instead of the AE/AWB block. The AF, OIS, zoom, and NR lines can stay (they are valid under either mode).

- [ ] **Step 1: Replace the AE/AWB block in `applySettings(builder, s)`**

Locate `private fun applySettings(builder: CaptureRequest.Builder, s: CameraSettings)` in `Camera2Controller.kt`. Replace the entire function body with:

```kotlin
private fun applySettings(builder: CaptureRequest.Builder, s: CameraSettings) {
    if (s.nightSceneMode) {
        builder[CaptureRequest.CONTROL_MODE] = CaptureRequest.CONTROL_MODE_USE_SCENE_MODE
        builder[CaptureRequest.CONTROL_SCENE_MODE] = CaptureRequest.CONTROL_SCENE_MODE_NIGHT
    } else {
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
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew :app:compileDebugKotlin 2>&1 | tail -20`

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/superpowerscameraresearch/camera2/Camera2Controller.kt
git commit -m "feat: Camera2Controller supports CONTROL_SCENE_MODE_NIGHT"
```

---

### Task 5: Camera2Fragment — NIGHT pill and manual-pill lockout

**Files:**
- Modify: `app/src/main/java/…/camera2/Camera2Fragment.kt`

`CONTROL_SCENE_MODE_NIGHT` has integer value `4`. The pill appears only when `4` is in `currentCapabilities.availableSceneModes`.

- [ ] **Step 1: Add the NIGHT pill entry to `setupPills()`**

In `Camera2Fragment.setupPills()`, find the `params` list. Add a NIGHT entry after the NR entry:

```kotlin
val params = mutableListOf(
    Triple("ISO",   "#FFE88888") { showIsoSlider() },
    Triple("SS",    "#FF88E888") { showShutterSlider() },
    Triple("WB",    "#FF8888E8") { showWbSlider() },
    Triple("FOCUS", "#FFEAA888") { showFocusSlider() },
    Triple("INF",   "#FFEAA888") { tapInfinity() },
    Triple("ZOOM",  "#FFAA88E8") { showZoomSlider() },
    Triple("NR",    "#FFE8CC88") { cycleNoiseReduction() }
)
if (currentCapabilities.supportsOis) {
    params += Triple("OIS", "#FF88E8E8") { toggleOis() }
}
if (currentCapabilities.availableSceneModes.contains(CameraMetadata.CONTROL_SCENE_MODE_NIGHT)) {
    params += Triple("NIGHT", "#FF9999FF") { toggleNightSceneMode() }
}
```

Also update the pill label resolution in the `params.forEach` block — find `"OIS" -> if (settings.oisEnabled) "OIS ON" else "OIS OFF"` and add a `"NIGHT"` case:

```kotlin
text = when (label) {
    "NR"    -> nrLabel(settings.noiseReduction)
    "OIS"   -> if (settings.oisEnabled) "OIS ON" else "OIS OFF"
    "NIGHT" -> if (settings.nightSceneMode) "NIGHT ON" else "NIGHT"
    else    -> label
}
```

- [ ] **Step 2: Add `toggleNightSceneMode()` function**

Add after `toggleOis()`:

```kotlin
private fun toggleNightSceneMode() {
    settings = settings.copy(nightSceneMode = !settings.nightSceneMode)
    controller.applySettings(settings)
    binding.pillsContainer.findViewWithTag<TextView>("NIGHT")?.text =
        if (settings.nightSceneMode) "NIGHT ON" else "NIGHT"
    setManualPillsEnabled(!settings.nightSceneMode)
}
```

- [ ] **Step 3: Add `setManualPillsEnabled()` helper**

Add after `toggleNightSceneMode()`:

```kotlin
private fun setManualPillsEnabled(enabled: Boolean) {
    listOf("ISO", "SS", "WB", "FOCUS", "ZOOM").forEach { tag ->
        binding.pillsContainer.findViewWithTag<TextView>(tag)?.apply {
            alpha = if (enabled) 1f else 0.4f
            isClickable = enabled
        }
    }
}
```

- [ ] **Step 4: Reset night mode on camera switch**

In `showCameraSelectorSheet()`, locate the `setOnClickListener` for each camera row. Before `controller.switchCamera(cap.cameraId)`, add:

```kotlin
if (settings.nightSceneMode) {
    settings = settings.copy(nightSceneMode = false)
}
```

So the full click handler becomes:

```kotlin
setOnClickListener {
    if (settings.nightSceneMode) {
        settings = settings.copy(nightSceneMode = false)
    }
    currentCapabilities = cap
    controller.switchCamera(cap.cameraId)
    updateCameraSelectorLabel()
    updateHardwareLevelBadge()
    setupPills()
    setupCapture()
    sheet.dismiss()
}
```

- [ ] **Step 5: Add the missing import**

At the top of `Camera2Fragment.kt`, add if not already present:

```kotlin
import android.hardware.camera2.CameraMetadata
```

- [ ] **Step 6: Verify compilation**

Run: `./gradlew :app:compileDebugKotlin 2>&1 | tail -20`

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/example/superpowerscameraresearch/camera2/Camera2Fragment.kt
git commit -m "feat: Camera2Fragment NIGHT scene mode pill with manual-control lockout"
```

---

### Task 6: CameraXController — ExtensionsManager and night bind path

**Files:**
- Modify: `app/src/main/java/…/camerax/CameraXController.kt`

`ExtensionsManager` is obtained async after the `ProcessCameraProvider` is ready. Its instance is cached; per-camera availability is re-queried on `switchCamera`.

- [ ] **Step 1: Add imports**

Add to the existing imports in `CameraXController.kt`:

```kotlin
import androidx.camera.extensions.ExtensionMode
import androidx.camera.extensions.ExtensionsManager
```

- [ ] **Step 2: Add fields and callback parameter**

Replace the class declaration and field block. The constructor gains one new parameter (`onExtensionsAvailability`) and two new fields (`extensionsManager`, `extensionsAvailability`):

```kotlin
class CameraXController(
    private val context: Context,
    private val previewView: PreviewView,
    private val lifecycleOwner: LifecycleOwner,
    private val onFpsUpdate: (Float) -> Unit,
    private val onHistogramReady: (IntArray, Boolean) -> Unit,
    private val onExtensionsAvailability: (Map<Int, Boolean>) -> Unit = {}
) {
    private var provider: ProcessCameraProvider? = null
    private var extensionsManager: ExtensionsManager? = null
    private var imageCapture: ImageCapture? = null
    private var currentCameraId: String = ""
    private var currentSettings = CameraSettings()
    private val analysisExecutor = Executors.newSingleThreadExecutor()
    @Volatile private var lastTimestamp = 0L
```

- [ ] **Step 3: Replace `start()` to chain ExtensionsManager init**

Replace the existing `start()` function:

```kotlin
fun start(cameraId: String) {
    currentCameraId = cameraId
    val providerFuture = ProcessCameraProvider.getInstance(context)
    providerFuture.addListener({
        val prov = providerFuture.get()
        provider = prov
        val extFuture = ExtensionsManager.getInstanceAsync(context, prov)
        extFuture.addListener({
            extensionsManager = extFuture.get()
            onExtensionsAvailability(queryAvailability(cameraId))
            bindUseCases()
        }, ContextCompat.getMainExecutor(context))
    }, ContextCompat.getMainExecutor(context))
}
```

- [ ] **Step 4: Add `queryAvailability()` helper**

Add as a private function after `start()`:

```kotlin
private fun queryAvailability(cameraId: String): Map<Int, Boolean> {
    val mgr = extensionsManager ?: return emptyMap()
    val selector = buildSelectorForId(cameraId)
    return mapOf(
        ExtensionMode.NIGHT  to mgr.isExtensionAvailable(selector, ExtensionMode.NIGHT),
        ExtensionMode.HDR    to mgr.isExtensionAvailable(selector, ExtensionMode.HDR),
        ExtensionMode.BOKEH  to mgr.isExtensionAvailable(selector, ExtensionMode.BOKEH),
        ExtensionMode.BEAUTY to mgr.isExtensionAvailable(selector, ExtensionMode.BEAUTY),
        ExtensionMode.AUTO   to mgr.isExtensionAvailable(selector, ExtensionMode.AUTO)
    )
}

private fun buildSelectorForId(cameraId: String): CameraSelector =
    CameraSelector.Builder()
        .addCameraFilter { cameras ->
            cameras.filter { Camera2CameraInfo.from(it).cameraId == cameraId }.ifEmpty { cameras }
        }.build()
```

- [ ] **Step 5: Update `switchCamera()` to re-query availability**

Replace the existing `switchCamera()`:

```kotlin
fun switchCamera(cameraId: String) {
    currentCameraId = cameraId
    if (extensionsManager != null) {
        onExtensionsAvailability(queryAvailability(cameraId))
    }
    bindUseCases()
}
```

- [ ] **Step 6: Update `bindUseCases()` to use night selector when appropriate**

Replace the entire `bindUseCases()` function:

```kotlin
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
                val histogram = HistogramComputer.compute(
                    plane.buffer.let { buf -> ByteArray(buf.remaining()).also { buf.get(it) } },
                    stride = plane.rowStride,
                    width = imageProxy.width,
                    height = imageProxy.height
                )
                onHistogramReady(histogram, HistogramComputer.isClipping(histogram))
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
        prov.bindToLifecycle(lifecycleOwner, finalSelector, preview, analysis, imageCapture!!)
    }.onFailure {
        Log.e(TAG, "Failed to bind CameraX use cases", it)
    }
}
```

- [ ] **Step 7: Verify compilation**

Run: `./gradlew :app:compileDebugKotlin 2>&1 | tail -20`

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/example/superpowerscameraresearch/camerax/CameraXController.kt
git commit -m "feat: CameraXController integrates ExtensionsManager with night bind path"
```

---

### Task 7: CameraXFragment — NIGHT pill, lockout, extension enumeration

**Files:**
- Modify: `app/src/main/java/…/camerax/CameraXFragment.kt`

- [ ] **Step 1: Add imports**

Add to the imports in `CameraXFragment.kt`:

```kotlin
import androidx.camera.extensions.ExtensionMode
```

- [ ] **Step 2: Add the extensions availability field**

Add after `private var settings = CameraSettings()`:

```kotlin
private var extensionsAvailability: Map<Int, Boolean> = emptyMap()
```

- [ ] **Step 3: Pass the callback when constructing CameraXController**

In `onViewCreated`, replace the `CameraXController(...)` constructor call with:

```kotlin
controller = CameraXController(
    context = requireContext(),
    previewView = binding.previewView,
    lifecycleOwner = viewLifecycleOwner,
    onFpsUpdate = { fps ->
        activity?.runOnUiThread { _binding?.tvFps?.text = "${"%.1f".format(fps)} fps" }
    },
    onHistogramReady = { hist, clipping ->
        activity?.runOnUiThread { _binding?.histogramView?.update(hist, clipping) }
    },
    onExtensionsAvailability = { avail ->
        activity?.runOnUiThread {
            extensionsAvailability = avail
            setupPills()
        }
    }
)
```

- [ ] **Step 4: Add NIGHT pill entry to `setupPills()`**

In `setupPills()`, after the OIS conditional block, add:

```kotlin
if (extensionsAvailability[ExtensionMode.NIGHT] == true) {
    params += Triple("NIGHT", "#FF9999FF") { toggleNightMode() }
}
```

Also add a `"NIGHT"` label case in the `params.forEach` block. Find:

```kotlin
text = label
```

Replace that entire `text = ...` assignment with:

```kotlin
text = when (label) {
    "OIS"   -> if (settings.oisEnabled) "OIS ON" else "OIS OFF"
    "NIGHT" -> if (settings.nightMode) "NIGHT ON" else "NIGHT"
    else    -> label
}
```

- [ ] **Step 5: Add `toggleNightMode()` function**

Add after `toggleOis()`:

```kotlin
private fun toggleNightMode() {
    settings = settings.copy(nightMode = !settings.nightMode)
    controller.applySettings(settings)
    binding.pillsContainer.findViewWithTag<TextView>("NIGHT")?.text =
        if (settings.nightMode) "NIGHT ON" else "NIGHT"
    setManualPillsEnabled(!settings.nightMode)
}

private fun setManualPillsEnabled(enabled: Boolean) {
    listOf("ISO", "SS", "WB", "FOCUS", "ZOOM").forEach { tag ->
        binding.pillsContainer.findViewWithTag<TextView>(tag)?.apply {
            alpha = if (enabled) 1f else 0.4f
            isClickable = enabled
        }
    }
}
```

- [ ] **Step 6: Reset night mode on camera switch**

In `showCameraSelectorSheet()`, find the `setOnClickListener` for each camera row. Before `controller.switchCamera(cap.cameraId)`, add:

```kotlin
if (settings.nightMode) {
    settings = settings.copy(nightMode = false)
    setManualPillsEnabled(true)
}
```

- [ ] **Step 7: Add extension enumeration to the capabilities sheet**

In `showCapabilitiesSheet()`, after adding all characteristic rows but before `sheet.setContentView(view)`, add:

```kotlin
if (extensionsAvailability.isNotEmpty()) {
    val header = TextView(requireContext()).apply {
        text = "\nCameraX Extensions"
        setTextColor(0xFFFFFFFF.toInt())
        textSize = 12f
        typeface = android.graphics.Typeface.MONOSPACE
        setPadding(0, 16, 0, 4)
    }
    container.addView(header)

    val extNames = mapOf(
        ExtensionMode.NIGHT  to "NIGHT",
        ExtensionMode.HDR    to "HDR",
        ExtensionMode.BOKEH  to "BOKEH",
        ExtensionMode.BEAUTY to "BEAUTY",
        ExtensionMode.AUTO   to "AUTO"
    )
    extNames.forEach { (mode, name) ->
        val supported = extensionsAvailability[mode] == true
        val row = TextView(requireContext()).apply {
            text = "${"%-8s".format(name)}  ${if (supported) "✓" else "✗"}"
            setTextColor(if (supported) 0xFF88FF88.toInt() else 0xFF888888.toInt())
            textSize = 11f
            typeface = android.graphics.Typeface.MONOSPACE
            setPadding(0, 4, 0, 4)
        }
        container.addView(row)
    }
}
```

The variable `container` in `showCapabilitiesSheet()` is obtained via `view.findViewById<ViewGroup>(R.id.capabilitiesContainer)` — this name is already used earlier in the same function.

- [ ] **Step 8: Verify compilation**

Run: `./gradlew :app:compileDebugKotlin 2>&1 | tail -20`

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 9: Run all unit tests**

Run: `./gradlew :app:test 2>&1 | tail -20`

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 10: Commit**

```bash
git add app/src/main/java/com/example/superpowerscameraresearch/camerax/CameraXFragment.kt
git commit -m "feat: CameraXFragment NIGHT extension pill, manual lockout, extension enumeration in capabilities sheet"
```
