# CameraSuperpowers Improvements Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ten improvements to the Camera2 tab: layout polish, live HUD with confirmed values and lens stats, shutter haptic+animation feedback, camera reconnection retry, OIS API fix, noise reduction pill, INF focus shortcut, and screen keep-on.

**Architecture:** Changes are spread across the model layer (`CameraSettings`), the controller (`Camera2Controller`), and the two fragment files. `Camera2Controller` gains a new `onLiveStatsUpdate` callback and retry logic; `Camera2Fragment` wires both callbacks to HUD views and gains new pills. `CameraXFragment` only receives the screen keep-on change.

**Tech Stack:** Kotlin, Android Camera2 API, ViewBinding, ViewPropertyAnimator, HapticFeedbackConstants

---

## File Map

| File | What changes |
|---|---|
| `app/src/main/res/drawable/slider_panel_bg.xml` | **Create** — dark shape drawable with rounded top corners for the slider panel |
| `app/src/main/res/layout/fragment_camera2.xml` | Pill margin 56→80dp, slider panel inset + new bg, add `tvIso`/`tvShutter`/`tvLiveStats` HUD labels |
| `app/src/main/java/.../model/CameraSettings.kt` | Add `noiseReduction: Int`, remove `focusAuto: Boolean` |
| `app/src/test/java/.../model/CameraSettingsTest.kt` | Remove `focusAuto` assertion, add `noiseReduction` default test |
| `app/src/main/java/.../camera2/Camera2Controller.kt` | Fix OIS, add `onLiveStatsUpdate` callback + extraction, reconnection retry |
| `app/src/main/java/.../camera2/Camera2Fragment.kt` | Wire HUD callbacks, shutter feedback, NR pill, INF pill, screen keep-on |
| `app/src/main/java/.../camerax/CameraXFragment.kt` | Screen keep-on, remove `focusAuto` reference from `showFocusSlider` |

---

## Task 1: Update CameraSettings — add noiseReduction, remove focusAuto

Focus is always manual from this point on. `focusAuto` is removed and its callsites updated. `noiseReduction` is added as a plain `Int` (avoids pulling Android imports into the model layer; the value `1` equals `CaptureRequest.NOISE_REDUCTION_MODE_FAST`).

**Files:**
- Modify: `app/src/main/java/com/example/superpowerscameraresearch/model/CameraSettings.kt`
- Modify: `app/src/test/java/com/example/superpowerscameraresearch/model/CameraSettingsTest.kt`

- [ ] **Step 1: Update CameraSettings data class**

Replace the entire file content:

```kotlin
package com.example.superpowerscameraresearch.model

data class CameraSettings(
    val isoAuto: Boolean = true,
    val iso: Int = 800,
    val shutterAuto: Boolean = true,
    val shutterNs: Long = 33_333_333L,   // ~1/30s in nanoseconds
    val wbAuto: Boolean = true,
    val whiteBalanceK: Int = 4000,
    val focusDistance: Float = 0f,       // 0 = infinity (Camera2 dioptre units)
    val zoom: Float = 1.0f,
    val oisEnabled: Boolean = true,
    val noiseReduction: Int = 1          // CaptureRequest.NOISE_REDUCTION_MODE_FAST
) {
    companion object {
        fun shutterNsToDisplay(ns: Long): String {
            val seconds = ns / 1_000_000_000.0
            return when {
                seconds >= 1.0 -> "${seconds.toLong()}s"
                else -> {
                    val denom = Math.round(1.0 / seconds)
                    "1/${denom}s"
                }
            }
        }

        fun focusDistanceToDisplay(dioptre: Float): String =
            if (dioptre == 0f) "∞" else "${"%.2f".format(dioptre)} D"
    }
}
```

- [ ] **Step 2: Update CameraSettingsTest**

Replace the `default settings use auto modes` test and add a `noiseReduction` default test:

```kotlin
@Test
fun `default settings use auto modes`() {
    val settings = CameraSettings()
    assertTrue(settings.isoAuto)
    assertTrue(settings.shutterAuto)
    assertTrue(settings.wbAuto)
}

@Test
fun `default noiseReduction is FAST (1)`() {
    assertEquals(1, CameraSettings().noiseReduction)
}
```

- [ ] **Step 3: Run the model tests**

```bash
./gradlew testDebugUnitTest --tests "*.CameraSettingsTest" 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL` with all tests passing.

- [ ] **Step 4: Fix CameraXFragment — remove focusAuto from showFocusSlider**

In `app/src/main/java/com/example/superpowerscameraresearch/camerax/CameraXFragment.kt`, find the `showFocusSlider` method. Change:

```kotlin
settings = settings.copy(focusDistance = d, focusAuto = false)
```

to:

```kotlin
settings = settings.copy(focusDistance = d)
```

- [ ] **Step 5: Verify the project compiles**

```bash
./gradlew assembleDebug 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL`. If `focusAuto` is referenced anywhere else, fix those callsites now.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/example/superpowerscameraresearch/model/CameraSettings.kt \
        app/src/test/java/com/example/superpowerscameraresearch/model/CameraSettingsTest.kt \
        app/src/main/java/com/example/superpowerscameraresearch/camerax/CameraXFragment.kt
git commit -m "Remove focusAuto from CameraSettings, add noiseReduction field"
```

---

## Task 2: New drawable — slider panel background

**Files:**
- Create: `app/src/main/res/drawable/slider_panel_bg.xml`

- [ ] **Step 1: Create the drawable**

```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <solid android:color="#EE000000" />
    <corners
        android:topLeftRadius="8dp"
        android:topRightRadius="8dp" />
</shape>
```

- [ ] **Step 2: Verify it compiles**

```bash
./gradlew assembleDebug 2>&1 | tail -5
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/res/drawable/slider_panel_bg.xml
git commit -m "Add slider_panel_bg drawable with rounded top corners"
```

---

## Task 3: Layout changes — fragment_camera2.xml

Three independent changes: (a) push pills up, (b) inset and round the slider panel, (c) add HUD label views.

**Files:**
- Modify: `app/src/main/res/layout/fragment_camera2.xml`

- [ ] **Step 1: Increase pillsContainer bottom margin**

Find the `pillsContainer` LinearLayout. Change:

```xml
android:layout_marginBottom="56dp"
```

to:

```xml
android:layout_marginBottom="80dp"
```

- [ ] **Step 2: Inset the slider panel**

Find the `sliderPanel` LinearLayout. Make these three changes:

1. Replace `android:background="#EE000000"` with `android:background="@drawable/slider_panel_bg"`
2. Add `android:layout_marginHorizontal="16dp"`
3. Find the `SeekBar` inside `sliderPanel` and add `android:paddingHorizontal="8dp"`

The sliderPanel opening tag should end up looking like:

```xml
<LinearLayout
    android:id="@+id/sliderPanel"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_gravity="bottom"
    android:layout_marginHorizontal="16dp"
    android:background="@drawable/slider_panel_bg"
    android:orientation="vertical"
    android:padding="12dp"
    android:visibility="gone">
```

And the SeekBar:

```xml
<SeekBar
    android:id="@+id/seekBar"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:paddingHorizontal="8dp" />
```

- [ ] **Step 3: Add HUD label views**

The existing top HUD has `tvCameraSelector` (top|start), `tvHardwareLevel` (top|center), and `tvFps` (top|end). Add `tvIso` and `tvShutter` in a second row, and `tvLiveStats` in a third row. Insert these three new TextViews after the existing `tvFps`:

```xml
<TextView
    android:id="@+id/tvIso"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:layout_gravity="top|start"
    android:layout_marginTop="32dp"
    android:layout_marginStart="8dp"
    android:background="@drawable/hud_label_bg"
    android:padding="4dp"
    android:textColor="#FFCCCCAA"
    android:textSize="11sp"
    android:fontFamily="monospace"
    android:text="ISO --" />

<TextView
    android:id="@+id/tvShutter"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:layout_gravity="top|start"
    android:layout_marginTop="32dp"
    android:layout_marginStart="80dp"
    android:background="@drawable/hud_label_bg"
    android:padding="4dp"
    android:textColor="#FFAACCAA"
    android:textSize="11sp"
    android:fontFamily="monospace"
    android:text="-- s" />

<TextView
    android:id="@+id/tvLiveStats"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:layout_gravity="top|start"
    android:layout_marginTop="56dp"
    android:layout_marginStart="8dp"
    android:background="@drawable/hud_label_bg"
    android:padding="4dp"
    android:textColor="#FF88AACC"
    android:textSize="10sp"
    android:fontFamily="monospace"
    android:text="f/?  ?mm  ?D  AE:--" />
```

- [ ] **Step 4: Verify the layout compiles**

```bash
./gradlew assembleDebug 2>&1 | tail -5
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/res/layout/fragment_camera2.xml
git commit -m "Layout: pill gap, slider inset, add ISO/shutter/live-stats HUD labels"
```

---

## Task 4: Camera2Controller — OIS fix + onLiveStatsUpdate callback

Two changes in `Camera2Controller.kt`: replace the OIS reflection hack with the correct API, and add the `onLiveStatsUpdate` callback to deliver live lens/AE stats to the fragment.

**Files:**
- Modify: `app/src/main/java/com/example/superpowerscameraresearch/camera2/Camera2Controller.kt`

- [ ] **Step 1: Add onLiveStatsUpdate to the constructor**

Add the new parameter to the `Camera2Controller` constructor (after `onHistogramReady`):

```kotlin
class Camera2Controller(
    private val context: Context,
    private val textureView: TextureView,
    private var cameraId: String,
    private val onSettingsConfirmed: (CameraSettings) -> Unit,
    private val onFpsUpdate: (Float) -> Unit,
    private val onHistogramReady: (IntArray, Boolean) -> Unit,
    private val onLiveStatsUpdate: (aperture: Float?, focalLength: Float?, focusDistance: Float?, aeState: String) -> Unit
)
```

- [ ] **Step 2: Add aeStateToString to the companion object**

Replace the existing companion object:

```kotlin
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
```

- [ ] **Step 3: Extend captureCallback to extract and deliver live stats**

In `captureCallback.onCaptureCompleted`, after the existing FPS and `onSettingsConfirmed` code, add:

```kotlin
val aperture     = result.get(CaptureResult.LENS_APERTURE)
val focalLength  = result.get(CaptureResult.LENS_FOCAL_LENGTH)
val focusDistance = result.get(CaptureResult.LENS_FOCUS_DISTANCE)
val aeState      = aeStateToString(result.get(CaptureResult.CONTROL_AE_STATE))
onLiveStatsUpdate(aperture, focalLength, focusDistance, aeState)
```

The full `onCaptureCompleted` override should look like:

```kotlin
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
```

- [ ] **Step 4: Fix OIS detection**

In `applySettings(builder, s)`, replace the entire reflection block:

```kotlin
@Suppress("UNCHECKED_CAST")
val oisModes = characteristics.keys
    .firstOrNull { it.name == "android.lens.info.availableOpticalStabilization" }
    ?.let { characteristics.get(it as CameraCharacteristics.Key<IntArray>) }
    ?: intArrayOf()
```

with:

```kotlin
val oisModes = characteristics.get(
    CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION
) ?: intArrayOf()
```

- [ ] **Step 5: Fix focus mode in applySettings**

`focusAuto` no longer exists. Replace the entire focus block:

```kotlin
if (s.focusAuto) {
    builder[CaptureRequest.CONTROL_AF_MODE] = CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
} else {
    builder[CaptureRequest.CONTROL_AF_MODE] = CaptureRequest.CONTROL_AF_MODE_OFF
    builder[CaptureRequest.LENS_FOCUS_DISTANCE] = s.focusDistance
}
```

with:

```kotlin
builder[CaptureRequest.CONTROL_AF_MODE] = CaptureRequest.CONTROL_AF_MODE_OFF
builder[CaptureRequest.LENS_FOCUS_DISTANCE] = s.focusDistance
```

- [ ] **Step 6: Add noiseReduction to applySettings**

At the end of `applySettings(builder, s)`, before the closing brace, add:

```kotlin
builder[CaptureRequest.NOISE_REDUCTION_MODE] = s.noiseReduction
```

- [ ] **Step 7: Fix Camera2Fragment — add no-op onLiveStatsUpdate to the controller constructor call**

In `Camera2Fragment.onViewCreated`, the `Camera2Controller(...)` constructor call now requires the new parameter. Add a no-op lambda as a placeholder (it will be replaced in Task 5):

```kotlin
controller = Camera2Controller(
    context = requireContext(),
    textureView = binding.textureView,
    cameraId = currentCapabilities.cameraId,
    onSettingsConfirmed = { /* wired in Task 5 */ },
    onFpsUpdate = { fps ->
        activity?.runOnUiThread {
            _binding?.tvFps?.text = "${"%.1f".format(fps)} fps"
        }
    },
    onHistogramReady = { hist, clipping ->
        activity?.runOnUiThread { _binding?.histogramView?.update(hist, clipping) }
    },
    onLiveStatsUpdate = { _, _, _, _ -> }
)
```

- [ ] **Step 8: Verify the project compiles**

```bash
./gradlew assembleDebug 2>&1 | tail -5
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/example/superpowerscameraresearch/camera2/Camera2Controller.kt \
        app/src/main/java/com/example/superpowerscameraresearch/camera2/Camera2Fragment.kt
git commit -m "Camera2Controller: fix OIS API, add live stats callback, add NR setting"
```

---

## Task 5: Camera2Fragment — wire HUD callbacks

Replace the no-op lambdas from Task 4 with real HUD updates.

**Files:**
- Modify: `app/src/main/java/com/example/superpowerscameraresearch/camera2/Camera2Fragment.kt`

- [ ] **Step 1: Wire onSettingsConfirmed**

Replace `onSettingsConfirmed = { /* wired in Task 5 */ }` with:

```kotlin
onSettingsConfirmed = { confirmed ->
    activity?.runOnUiThread {
        _binding?.tvIso?.text = "ISO ${confirmed.iso}"
        _binding?.tvShutter?.text = CameraSettings.shutterNsToDisplay(confirmed.shutterNs)
    }
},
```

- [ ] **Step 2: Wire onLiveStatsUpdate**

Replace `onLiveStatsUpdate = { _, _, _, _ -> }` with:

```kotlin
onLiveStatsUpdate = { aperture, focalLength, focusDistance, aeState ->
    activity?.runOnUiThread {
        val ap = aperture?.let { "f/${"%.1f".format(it)}" } ?: "f/?"
        val fl = focalLength?.let { "${"%.0f".format(it)}mm" } ?: "?mm"
        val fd = focusDistance?.let { CameraSettings.focusDistanceToDisplay(it) } ?: "?D"
        _binding?.tvLiveStats?.text = "$ap  $fl  $fd  AE:$aeState"
    }
},
```

- [ ] **Step 3: Verify compiles**

```bash
./gradlew assembleDebug 2>&1 | tail -5
```

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/example/superpowerscameraresearch/camera2/Camera2Fragment.kt
git commit -m "Wire confirmed ISO/shutter and live lens stats to HUD"
```

---

## Task 6: Camera2Controller — reconnection retry

**Files:**
- Modify: `app/src/main/java/com/example/superpowerscameraresearch/camera2/Camera2Controller.kt`

- [ ] **Step 1: Add retryCount field**

After the existing `@Volatile private var isOpening = false` line, add:

```kotlin
private var retryCount = 0
```

- [ ] **Step 2: Update onDisconnected and onError in the openCamera callback**

Find the `manager.openCamera(...)` call in `openCamera()`. Replace the `onDisconnected` and `onError` overrides:

```kotlin
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
```

- [ ] **Step 3: Reset retryCount on successful open**

In `onOpened`, add `retryCount = 0` before `startPreviewSession`:

```kotlin
override fun onOpened(device: CameraDevice) {
    isOpening = false
    retryCount = 0
    cameraDevice = device
    startPreviewSession(previewSurface!!)
}
```

- [ ] **Step 4: Verify compiles**

```bash
./gradlew assembleDebug 2>&1 | tail -5
```

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/superpowerscameraresearch/camera2/Camera2Controller.kt
git commit -m "Camera2Controller: add reconnection retry (max 3 attempts, 500ms delay)"
```

---

## Task 7: Camera2Fragment — shutter press feedback

**Files:**
- Modify: `app/src/main/java/com/example/superpowerscameraresearch/camera2/Camera2Fragment.kt`

- [ ] **Step 1: Add haptic + animation to setupCapture**

Find `setupCapture()`. The `captureButton.setOnClickListener` currently just calls `controller.captureRaw()`. Wrap it to fire haptic and animation first:

```kotlin
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
```

- [ ] **Step 2: Verify compiles**

```bash
./gradlew assembleDebug 2>&1 | tail -5
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/superpowerscameraresearch/camera2/Camera2Fragment.kt
git commit -m "Shutter button: haptic feedback + scale pulse animation on capture"
```

---

## Task 8: Camera2Fragment — noise reduction pill

**Files:**
- Modify: `app/src/main/java/com/example/superpowerscameraresearch/camera2/Camera2Fragment.kt`

- [ ] **Step 1: Add cycleNoiseReduction helper**

Add this private method to `Camera2Fragment`:

```kotlin
private fun cycleNoiseReduction() {
    val next = when (settings.noiseReduction) {
        android.hardware.camera2.CaptureRequest.NOISE_REDUCTION_MODE_FAST -> 
            android.hardware.camera2.CaptureRequest.NOISE_REDUCTION_MODE_HIGH_QUALITY
        android.hardware.camera2.CaptureRequest.NOISE_REDUCTION_MODE_HIGH_QUALITY -> 
            android.hardware.camera2.CaptureRequest.NOISE_REDUCTION_MODE_OFF
        else -> android.hardware.camera2.CaptureRequest.NOISE_REDUCTION_MODE_FAST
    }
    settings = settings.copy(noiseReduction = next)
    controller.applySettings(settings)
    binding.pillsContainer.findViewWithTag<TextView>("NR")?.text = nrLabel(next)
}

private fun nrLabel(mode: Int): String = when (mode) {
    android.hardware.camera2.CaptureRequest.NOISE_REDUCTION_MODE_FAST -> "NR:FAST"
    android.hardware.camera2.CaptureRequest.NOISE_REDUCTION_MODE_HIGH_QUALITY -> "NR:HQ"
    android.hardware.camera2.CaptureRequest.NOISE_REDUCTION_MODE_OFF -> "NR:OFF"
    else -> "NR:?"
}
```

- [ ] **Step 2: Add the NR pill to setupPills**

In `setupPills()`, add the NR pill to the `params` list (after the ZOOM entry, before OIS):

```kotlin
params += Triple("NR", "#FFE8CC88") { cycleNoiseReduction() }
```

After adding it to the list, the pill needs its initial label set correctly. Change the `params.forEach` block: after `binding.pillsContainer.addView(pill)`, add a special-case label update for the NR pill:

```kotlin
params.forEach { (label, colorHex, action) ->
    val pill = TextView(requireContext()).apply {
        text = if (label == "NR") nrLabel(settings.noiseReduction) else label
        // ... rest unchanged
    }
    // ... rest unchanged
}
```

The full updated `params` list inside `setupPills` should be:

```kotlin
val params = mutableListOf(
    Triple("ISO",   "#FFE88888") { showIsoSlider() },
    Triple("SS",    "#FF88E888") { showShutterSlider() },
    Triple("WB",    "#FF8888E8") { showWbSlider() },
    Triple("FOCUS", "#FFEAA888") { showFocusSlider() },
    Triple("ZOOM",  "#FFAA88E8") { showZoomSlider() },
    Triple("NR",    "#FFE8CC88") { cycleNoiseReduction() }
)
if (currentCapabilities.supportsOis) {
    params += Triple("OIS", "#FF88E8E8") { toggleOis() }
}
```

And the `params.forEach` pill creation:

```kotlin
params.forEach { (label, colorHex, action) ->
    val pill = TextView(requireContext()).apply {
        text = if (label == "NR") nrLabel(settings.noiseReduction) else label
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
```

- [ ] **Step 3: Verify compiles**

```bash
./gradlew assembleDebug 2>&1 | tail -5
```

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/example/superpowerscameraresearch/camera2/Camera2Fragment.kt
git commit -m "Add noise reduction pill (FAST → HQ → OFF cycle)"
```

---

## Task 9: Camera2Fragment — INF focus shortcut pill

**Files:**
- Modify: `app/src/main/java/com/example/superpowerscameraresearch/camera2/Camera2Fragment.kt`

- [ ] **Step 1: Add tapInfinity helper**

```kotlin
private fun tapInfinity() {
    settings = settings.copy(focusDistance = 0f)
    controller.applySettings(settings)
    if (binding.sliderPanel.visibility == View.VISIBLE &&
        binding.tvParamName.text == "FOCUS DISTANCE") {
        binding.seekBar.progress = 0
        binding.tvParamValue.text = "∞"
    }
}
```

- [ ] **Step 2: Add INF pill to setupPills**

Add it immediately after the FOCUS entry in the params list:

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
```

- [ ] **Step 3: Verify compiles**

```bash
./gradlew assembleDebug 2>&1 | tail -5
```

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/example/superpowerscameraresearch/camera2/Camera2Fragment.kt
git commit -m "Add INF pill — one-tap infinity focus lock for night sky use"
```

---

## Task 10: Screen keep-on — both fragments

**Files:**
- Modify: `app/src/main/java/com/example/superpowerscameraresearch/camera2/Camera2Fragment.kt`
- Modify: `app/src/main/java/com/example/superpowerscameraresearch/camerax/CameraXFragment.kt`

- [ ] **Step 1: Add keep-on to Camera2Fragment**

`Camera2Fragment` already has `onResume` and `onPause`. Add the flag to each:

```kotlin
override fun onResume() {
    super.onResume()
    activity?.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    if (binding.textureView.isAvailable) controller.openCamera()
}

override fun onPause() {
    super.onPause()
    activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    controller.closeCamera()
}
```

- [ ] **Step 2: Add keep-on to CameraXFragment**

`CameraXFragment` does not currently have `onResume`/`onPause`. Add them:

```kotlin
override fun onResume() {
    super.onResume()
    activity?.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
}

override fun onPause() {
    super.onPause()
    activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
}
```

- [ ] **Step 3: Run all unit tests**

```bash
./gradlew testDebugUnitTest 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 4: Verify final build**

```bash
./gradlew assembleDebug 2>&1 | tail -5
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/superpowerscameraresearch/camera2/Camera2Fragment.kt \
        app/src/main/java/com/example/superpowerscameraresearch/camerax/CameraXFragment.kt
git commit -m "Keep screen on while either camera tab is active"
```

---

## Spec coverage check

| Spec item | Task |
|---|---|
| 1. Pill gap (56→80dp) | Task 3 step 1 |
| 2. Slider inset + rounded corners | Task 3 step 2 |
| 3. Confirmed ISO/shutter HUD | Task 5 step 1 |
| 3b. Extended live HUD (aperture/FL/FD/AE) | Tasks 4+5 |
| 4. Shutter haptic + scale pulse | Task 7 |
| 5. Reconnection retry | Task 6 |
| 6. OIS API fix | Task 4 step 4 |
| 8. Noise reduction pill | Task 8 |
| 9. INF focus shortcut pill | Task 9 |
| 10. Screen keep-on | Task 10 |
