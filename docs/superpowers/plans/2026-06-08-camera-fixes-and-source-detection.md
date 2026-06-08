# Camera Fixes & Source Detection Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix three camera bugs (zoom pill lockout, Camera2 zoom not working, CameraX night freeze), add a settings text stamp to all saved photos, and add a live source-detection overlay that draws red circles around stars and the moon with adjustable sensitivity.

**Architecture:** Camera2 is the primary path; CameraX tasks are included but lower priority. New code is isolated in three new files (`ConnectedComponentDetector`, `PhotoStamper`, `SourceDetectionOverlay`) with clean interfaces so each can be tested and swapped independently. Existing fragments only grow new pill/slider wiring; all algorithm and drawing logic stays out of them.

**Tech Stack:** Kotlin, Android Camera2 API, CameraX (reference), JUnit 4 unit tests (`./gradlew test`), no new dependencies.

---

## File Map

| File | Action | Purpose |
|---|---|---|
| `camera2/Camera2Fragment.kt` | Modify | Remove ZOOM from lockout; add DETECT pill + slider |
| `camerax/CameraXFragment.kt` | Modify | Remove ZOOM from lockout; add DETECT pill + slider |
| `camera2/Camera2Controller.kt` | Modify | Fix zoom gating; separate histogramReader guard; JPEG reader; PhotoStamper; dual-save |
| `camerax/CameraXController.kt` | Modify | Drop analysis in night; store Camera ref; apply zoom via cameraControl; PhotoStamper |
| `model/CameraSettings.kt` | Modify | Add `sourceDetectionSensitivity: Int = 0` |
| `overlay/SourceDetector.kt` | Create | `SourceDetector` interface + `DetectedSource` data class |
| `overlay/ConnectedComponentDetector.kt` | Create | First `SourceDetector` implementation |
| `overlay/SourceDetectionOverlay.kt` | Create | Transparent `View` drawing circles; `drawOnto()` static helper |
| `overlay/PhotoStamper.kt` | Create | Stamps settings text onto a `Bitmap` |
| `res/layout/fragment_camera2.xml` | Modify | Add `SourceDetectionOverlay` above `ReticleView` |
| `res/layout/fragment_camerax.xml` | Modify | Add `SourceDetectionOverlay` above `ReticleView` |
| `test/.../ConnectedComponentDetectorTest.kt` | Create | Unit tests for algorithm |
| `test/.../PhotoStamperTest.kt` | Create | Unit tests for stamp string formatting |

All paths relative to `app/src/main/java/com/example/superpowerscameraresearch/`.

---

## Task 1: BUG-1 — Remove ZOOM from night-mode pill lockout

**Files:**
- Modify: `app/src/main/java/com/example/superpowerscameraresearch/camera2/Camera2Fragment.kt:227`
- Modify: `app/src/main/java/com/example/superpowerscameraresearch/camerax/CameraXFragment.kt:185`

- [ ] **Step 1: Fix Camera2Fragment**

In `Camera2Fragment.kt`, find `setManualPillsEnabled` and remove `"ZOOM"` from the list:

```kotlin
private fun setManualPillsEnabled(enabled: Boolean) {
    listOf("ISO", "SS", "WB", "FOCUS", "INF").forEach { tag ->
        binding.pillsContainer.findViewWithTag<TextView>(tag)?.apply {
            alpha = if (enabled) 1f else 0.4f
            isClickable = enabled
        }
    }
}
```

- [ ] **Step 2: Fix CameraXFragment**

In `CameraXFragment.kt`, find `setManualPillsEnabled` and remove `"ZOOM"`:

```kotlin
private fun setManualPillsEnabled(enabled: Boolean) {
    listOf("ISO", "SS", "WB", "FOCUS", "OIS").forEach { tag ->
        binding.pillsContainer.findViewWithTag<TextView>(tag)?.apply {
            alpha = if (enabled) 1f else 0.4f
            isClickable = enabled
        }
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/superpowerscameraresearch/camera2/Camera2Fragment.kt
git add app/src/main/java/com/example/superpowerscameraresearch/camerax/CameraXFragment.kt
git commit -m "fix: zoom pill no longer locked out in night mode"
```

---

## Task 2: BUG-2 — Fix Camera2 zoom

**Files:**
- Modify: `app/src/main/java/com/example/superpowerscameraresearch/camera2/Camera2Controller.kt`

The two problems are: (a) `CONTROL_ZOOM_RATIO` is set unconditionally on API 30+ even when the device doesn't support it (check `CONTROL_ZOOM_RATIO_RANGE`); (b) the `histogramReader?.surface ?: return` guard in `applySettings(CameraSettings)` silently suppresses all camera settings if the histogram reader has no surface.

- [ ] **Step 1: Fix zoom gating in `applySettings(builder, s)`**

Find the zoom block (~line 232) and replace:

```kotlin
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
```

With:

```kotlin
// CONTROL_ZOOM_RATIO requires API 30 AND explicit device support.
// Fall back to SCALER_CROP_REGION on devices that don't advertise a zoom range.
val zoomRatioRange = if (Build.VERSION.SDK_INT >= 30) {
    characteristics.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE)
} else null

if (zoomRatioRange != null) {
    builder[CaptureRequest.CONTROL_ZOOM_RATIO] = s.zoom.coerceIn(zoomRatioRange.lower, zoomRatioRange.upper)
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
```

- [ ] **Step 2: Separate histogramReader guard from settings update in `applySettings(CameraSettings)`**

Find `applySettings(settings: CameraSettings)` (~line 249) and replace:

```kotlin
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
```

With:

```kotlin
fun applySettings(settings: CameraSettings) {
    currentSettings = settings
    val previewSurface = previewSurface ?: return
    val builder = cameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW) ?: return
    builder.addTarget(previewSurface)
    // Histogram surface is optional — its absence must not suppress other settings.
    histogramReader?.surface?.let { builder.addTarget(it) }
    applySettings(builder, settings)
    captureSession?.setRepeatingRequest(builder.build(), captureCallback, cameraHandler)
}
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/superpowerscameraresearch/camera2/Camera2Controller.kt
git commit -m "fix: Camera2 zoom now works — gate CONTROL_ZOOM_RATIO on device support, decouple histogramReader guard"
```

---

## Task 3: BUG-3 — Fix CameraX night mode freeze and zoom

**Files:**
- Modify: `app/src/main/java/com/example/superpowerscameraresearch/camerax/CameraXController.kt`

Two problems: (a) `ImageAnalysis` is bound alongside `preview + imageCapture` with the Extensions selector — most vendors don't support it and silently fail after `unbindAll()`, freezing the preview; (b) zoom is never applied in night mode because `applyCamera2Interop` is skipped and the `Camera` ref from `bindToLifecycle` is discarded.

- [ ] **Step 1: Add `camera` field and fix `bindUseCases()`**

Add a class-level field near the other private vars:

```kotlin
private var boundCamera: androidx.camera.core.Camera? = null
```

Find `bindUseCases()` and replace the `prov.unbindAll()` + `runCatching` block:

```kotlin
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
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/example/superpowerscameraresearch/camerax/CameraXController.kt
git commit -m "fix: CameraX night mode no longer freezes; zoom applied via cameraControl"
```

---

## Task 4: Add `sourceDetectionSensitivity` to `CameraSettings`

**Files:**
- Modify: `app/src/main/java/com/example/superpowerscameraresearch/model/CameraSettings.kt`

- [ ] **Step 1: Add field**

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
    val nightSceneMode: Boolean = false,
    val sourceDetectionSensitivity: Int = 0,  // 0 = off, 1–100
) { /* unchanged */ }
```

- [ ] **Step 2: Verify existing tests still pass**

```
./gradlew test
```

Expected: all tests green.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/superpowerscameraresearch/model/CameraSettings.kt
git commit -m "feat: add sourceDetectionSensitivity to CameraSettings"
```

---

## Task 5: `SourceDetector` interface and `DetectedSource`

**Files:**
- Create: `app/src/main/java/com/example/superpowerscameraresearch/overlay/SourceDetector.kt`

- [ ] **Step 1: Create the file**

```kotlin
package com.example.superpowerscameraresearch.overlay

/**
 * Strategy interface for detecting bright astronomical sources in a luma plane.
 * Swap implementations by passing a different SourceDetector to the overlay.
 *
 * cx/cy are the primary scientific output (source centroids in image coordinates).
 * radius is used only for drawing circles and may not be physically meaningful.
 */
interface SourceDetector {
    fun detect(
        luma: ByteArray,
        stride: Int,
        width: Int,
        height: Int,
        sensitivity: Int  // 1–100; callers must not call with 0
    ): List<DetectedSource>
}

data class DetectedSource(
    val cx: Float,     // centroid x, in image pixel coordinates
    val cy: Float,     // centroid y, in image pixel coordinates
    val radius: Float  // bounding radius in image pixels, for circle rendering
)
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/example/superpowerscameraresearch/overlay/SourceDetector.kt
git commit -m "feat: SourceDetector interface and DetectedSource"
```

---

## Task 6: `ConnectedComponentDetector` with tests

**Files:**
- Create: `app/src/main/java/com/example/superpowerscameraresearch/overlay/ConnectedComponentDetector.kt`
- Create: `app/src/test/java/com/example/superpowerscameraresearch/overlay/ConnectedComponentDetectorTest.kt`

- [ ] **Step 1: Write failing tests**

```kotlin
// app/src/test/java/com/example/superpowerscameraresearch/overlay/ConnectedComponentDetectorTest.kt
package com.example.superpowerscameraresearch.overlay

import org.junit.Assert.*
import org.junit.Test

class ConnectedComponentDetectorTest {

    private val detector = ConnectedComponentDetector()

    // Helper: create a luma ByteArray of given size, filled with 0.
    private fun blackFrame(w: Int, h: Int) = ByteArray(w * h) { 0 }

    @Test
    fun `all-black frame returns no sources`() {
        val result = detector.detect(blackFrame(64, 64), stride = 64, width = 64, height = 64, sensitivity = 50)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `single bright pixel is filtered as noise`() {
        val frame = blackFrame(64, 64)
        frame[32 * 64 + 32] = 255.toByte()  // one hot pixel at (32,32)
        val result = detector.detect(frame, stride = 64, width = 64, height = 64, sensitivity = 50)
        assertTrue("single hot pixel should be filtered", result.isEmpty())
    }

    @Test
    fun `small bright cluster is detected as a source`() {
        val frame = blackFrame(64, 64)
        // 2x2 bright patch centred around (32,32)
        frame[31 * 64 + 31] = 255.toByte()
        frame[31 * 64 + 32] = 255.toByte()
        frame[32 * 64 + 31] = 255.toByte()
        frame[32 * 64 + 32] = 255.toByte()
        val result = detector.detect(frame, stride = 64, width = 64, height = 64, sensitivity = 50)
        assertEquals(1, result.size)
        assertEquals(31.5f, result[0].cx, 0.5f)
        assertEquals(31.5f, result[0].cy, 0.5f)
    }

    @Test
    fun `two separate clusters produce two sources`() {
        val frame = blackFrame(64, 64)
        // Star 1 near top-left
        frame[5 * 64 + 5] = 255.toByte()
        frame[5 * 64 + 6] = 255.toByte()
        frame[6 * 64 + 5] = 255.toByte()
        // Star 2 near bottom-right
        frame[55 * 64 + 55] = 255.toByte()
        frame[55 * 64 + 56] = 255.toByte()
        frame[56 * 64 + 55] = 255.toByte()
        val result = detector.detect(frame, stride = 64, width = 64, height = 64, sensitivity = 50)
        assertEquals(2, result.size)
    }

    @Test
    fun `large blob radius is clamped to frameWidth over 4`() {
        // Fill top half entirely — simulates bright sky, should be discarded (>30% frame)
        val w = 64; val h = 64
        val frame = ByteArray(w * h) { if (it / w < h / 2) 255.toByte() else 0 }
        val result = detector.detect(frame, stride = w, width = w, height = h, sensitivity = 100)
        // The half-frame blob covers 50% > MAX_BLOB_AREA_FRACTION (30%), so it's filtered out
        assertTrue("sky-filling blob should be discarded", result.isEmpty())
    }

    @Test
    fun `higher sensitivity detects dimmer sources`() {
        val frame = blackFrame(64, 64)
        // Dim 2x2 patch: value 80 (below 50% threshold but above 20% threshold)
        frame[10 * 64 + 10] = 80.toByte()
        frame[10 * 64 + 11] = 80.toByte()
        frame[11 * 64 + 10] = 80.toByte()
        frame[11 * 64 + 11] = 80.toByte()
        val lowSens = detector.detect(frame, stride = 64, width = 64, height = 64, sensitivity = 50)
        val highSens = detector.detect(frame, stride = 64, width = 64, height = 64, sensitivity = 80)
        assertTrue("dim source invisible at low sensitivity", lowSens.isEmpty())
        assertEquals("dim source visible at high sensitivity", 1, highSens.size)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

```
./gradlew test --tests "*.ConnectedComponentDetectorTest"
```

Expected: compilation error (class not yet defined).

- [ ] **Step 3: Implement `ConnectedComponentDetector`**

```kotlin
// app/src/main/java/com/example/superpowerscameraresearch/overlay/ConnectedComponentDetector.kt
package com.example.superpowerscameraresearch.overlay

class ConnectedComponentDetector : SourceDetector {

    companion object {
        // Blobs smaller than this pixel count are treated as hot-pixel noise and discarded.
        private const val MIN_BLOB_PIXELS = 2

        // Blobs covering more than this fraction of the frame are treated as sky/fog and discarded.
        private const val MAX_BLOB_AREA_FRACTION = 0.30f
    }

    override fun detect(
        luma: ByteArray,
        stride: Int,
        width: Int,
        height: Int,
        sensitivity: Int
    ): List<DetectedSource> {
        // threshold: sensitivity=100 → T≈0 (detect everything); sensitivity=1 → T≈252 (only very bright)
        val threshold = ((1f - sensitivity / 100f) * 255f).toInt()
        val maxBlobPixels = (width * height * MAX_BLOB_AREA_FRACTION).toInt()

        // Union-Find parent array indexed by pixel index (y*stride + x, but we use y*width + x for
        // the label array since stride may include padding we don't care about).
        val labels = IntArray(width * height) { -1 }  // -1 = below threshold
        val parent = IntArray(width * height) { it }

        fun root(i: Int): Int {
            var r = i
            while (parent[r] != r) r = parent[r]
            // Path compression
            var j = i
            while (parent[j] != r) { val next = parent[j]; parent[j] = r; j = next }
            return r
        }

        fun union(a: Int, b: Int) {
            val ra = root(a); val rb = root(b)
            if (ra != rb) parent[ra] = rb
        }

        // Single-pass labeling (4-connectivity: left and above neighbours)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val lumaVal = luma[y * stride + x].toInt() and 0xFF
                if (lumaVal < threshold) continue
                val idx = y * width + x
                labels[idx] = idx  // mark as above-threshold; parent already = idx
                if (x > 0 && labels[idx - 1] >= 0) union(idx, idx - 1)
                if (y > 0 && labels[(y - 1) * width + x] >= 0) union(idx, (y - 1) * width + x)
            }
        }

        // Accumulate per-root statistics
        data class BlobStats(
            var count: Int = 0,
            var sumX: Long = 0, var sumY: Long = 0,
            var minX: Int = Int.MAX_VALUE, var maxX: Int = Int.MIN_VALUE,
            var minY: Int = Int.MAX_VALUE, var maxY: Int = Int.MIN_VALUE
        )
        val stats = HashMap<Int, BlobStats>()

        for (y in 0 until height) {
            for (x in 0 until width) {
                val idx = y * width + x
                if (labels[idx] < 0) continue
                val r = root(idx)
                val s = stats.getOrPut(r) { BlobStats() }
                s.count++
                s.sumX += x; s.sumY += y
                if (x < s.minX) s.minX = x; if (x > s.maxX) s.maxX = x
                if (y < s.minY) s.minY = y; if (y > s.maxY) s.maxY = y
            }
        }

        val result = mutableListOf<DetectedSource>()
        val maxRadius = width / 4f  // upper clamp: prevents full-sky fog blobs

        for ((_, s) in stats) {
            if (s.count < MIN_BLOB_PIXELS) continue   // noise filter
            if (s.count > maxBlobPixels) continue      // sky/fog filter
            val cx = s.sumX.toFloat() / s.count
            val cy = s.sumY.toFloat() / s.count
            val radius = (maxOf(s.maxX - s.minX, s.maxY - s.minY) / 2f).coerceIn(2f, maxRadius)
            result += DetectedSource(cx, cy, radius)
        }

        return result
    }
}
```

- [ ] **Step 4: Run tests**

```
./gradlew test --tests "*.ConnectedComponentDetectorTest"
```

Expected: all 6 tests pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/superpowerscameraresearch/overlay/ConnectedComponentDetector.kt
git add app/src/test/java/com/example/superpowerscameraresearch/overlay/ConnectedComponentDetectorTest.kt
git commit -m "feat: ConnectedComponentDetector — blob-based source detection with tests"
```

---

## Task 7: `PhotoStamper` with tests

**Files:**
- Create: `app/src/main/java/com/example/superpowerscameraresearch/overlay/PhotoStamper.kt`
- Create: `app/src/test/java/com/example/superpowerscameraresearch/overlay/PhotoStamperTest.kt`

The Bitmap drawing cannot be unit-tested on JVM, so tests cover only the stamp string formatting logic, extracted as an internal `buildLines()` function.

- [ ] **Step 1: Write failing tests**

```kotlin
// app/src/test/java/com/example/superpowerscameraresearch/overlay/PhotoStamperTest.kt
package com.example.superpowerscameraresearch.overlay

import com.example.superpowerscameraresearch.model.CameraSettings
import org.junit.Assert.*
import org.junit.Test

class PhotoStamperTest {

    @Test
    fun `line1 contains ISO when not auto`() {
        val s = CameraSettings(isoAuto = false, iso = 1600)
        val (line1, _) = PhotoStamper.buildLines(s, aperture = null, focalLengthMm = null,
            zoom = 1.0f, timestampMs = 0L, stack = "Camera2")
        assertTrue(line1.contains("ISO 1600"))
    }

    @Test
    fun `line1 contains ISO AUTO when auto`() {
        val s = CameraSettings(isoAuto = true)
        val (line1, _) = PhotoStamper.buildLines(s, aperture = null, focalLengthMm = null,
            zoom = 1.0f, timestampMs = 0L, stack = "Camera2")
        assertTrue(line1.contains("ISO AUTO"))
    }

    @Test
    fun `line1 contains DETECT OFF when sensitivity is zero`() {
        val s = CameraSettings(sourceDetectionSensitivity = 0)
        val (line1, _) = PhotoStamper.buildLines(s, aperture = null, focalLengthMm = null,
            zoom = 1.0f, timestampMs = 0L, stack = "Camera2")
        assertTrue(line1.contains("DETECT OFF"))
    }

    @Test
    fun `line1 contains DETECT percentage when sensitivity nonzero`() {
        val s = CameraSettings(sourceDetectionSensitivity = 65)
        val (line1, _) = PhotoStamper.buildLines(s, aperture = null, focalLengthMm = null,
            zoom = 1.0f, timestampMs = 0L, stack = "Camera2")
        assertTrue(line1.contains("DETECT 65%"))
    }

    @Test
    fun `line2 contains stack name`() {
        val s = CameraSettings()
        val (_, line2) = PhotoStamper.buildLines(s, aperture = null, focalLengthMm = null,
            zoom = 1.0f, timestampMs = 1_000_000L, stack = "Camera2")
        assertTrue(line2.contains("Camera2"))
    }

    @Test
    fun `line1 includes focal length and aperture when provided`() {
        val s = CameraSettings()
        val (line1, _) = PhotoStamper.buildLines(s, aperture = 1.8f, focalLengthMm = 24f,
            zoom = 2.0f, timestampMs = 0L, stack = "Camera2")
        assertTrue(line1.contains("f/1.8"))
        assertTrue(line1.contains("24mm"))
        assertTrue(line1.contains("2.0×"))
    }
}
```

- [ ] **Step 2: Run to verify failure**

```
./gradlew test --tests "*.PhotoStamperTest"
```

Expected: compilation error.

- [ ] **Step 3: Implement `PhotoStamper`**

```kotlin
// app/src/main/java/com/example/superpowerscameraresearch/overlay/PhotoStamper.kt
package com.example.superpowerscameraresearch.overlay

import android.graphics.*
import com.example.superpowerscameraresearch.model.CameraSettings
import java.text.SimpleDateFormat
import java.util.*

object PhotoStamper {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    /**
     * Returns a new Bitmap with two lines of settings text burned into the bottom.
     * Does not mutate the input bitmap.
     */
    fun stamp(
        source: Bitmap,
        settings: CameraSettings,
        aperture: Float?,
        focalLengthMm: Float?,
        stack: String,
        timestampMs: Long = System.currentTimeMillis()
    ): Bitmap {
        val out = source.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(out)
        val (line1, line2) = buildLines(settings, aperture, focalLengthMm, settings.zoom, timestampMs, stack)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = out.height * 0.013f  // ~1.3% of image height
            typeface = Typeface.MONOSPACE
        }

        val lineHeight = textPaint.textSize * 1.4f
        val stripHeight = lineHeight * 2 + textPaint.textSize * 0.6f
        val stripTop = out.height - stripHeight

        // Semi-transparent black gradient strip
        val gradientPaint = Paint().apply {
            shader = LinearGradient(
                0f, stripTop, 0f, out.height.toFloat(),
                intArrayOf(Color.TRANSPARENT, 0xCC000000.toInt()),
                floatArrayOf(0f, 0.4f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, stripTop, out.width.toFloat(), out.height.toFloat(), gradientPaint)

        val xPad = out.width * 0.01f
        canvas.drawText(line1, xPad, out.height - lineHeight - textPaint.textSize * 0.3f, textPaint)
        canvas.drawText(line2, xPad, out.height - textPaint.textSize * 0.3f, textPaint)

        return out
    }

    /** Exposed for unit testing — builds the two stamp lines without touching Bitmap APIs. */
    internal fun buildLines(
        s: CameraSettings,
        aperture: Float?,
        focalLengthMm: Float?,
        zoom: Float,
        timestampMs: Long,
        stack: String
    ): Pair<String, String> {
        val isoStr = if (s.isoAuto) "ISO AUTO" else "ISO ${s.iso}"
        val ssStr = if (s.shutterAuto) "SS AUTO" else CameraSettings.shutterNsToDisplay(s.shutterNs)
        val wbStr = if (s.wbAuto) "WB AUTO" else "WB ${s.whiteBalanceK}K"
        val nrStr = when (s.noiseReduction) {
            0 -> "NR OFF"
            1 -> "NR FAST"
            else -> "NR HQ"
        }
        val detectStr = if (s.sourceDetectionSensitivity == 0) "DETECT OFF"
                        else "DETECT ${s.sourceDetectionSensitivity}%"

        val parts = mutableListOf(isoStr, ssStr)
        if (aperture != null) parts += "f/${"%.1f".format(aperture)}"
        if (focalLengthMm != null) parts += "${"%.0f".format(focalLengthMm)}mm"
        parts += "${"%.1f".format(zoom)}×"
        parts += wbStr
        parts += nrStr
        parts += detectStr

        val line1 = parts.joinToString(" · ")
        val line2 = "${dateFormat.format(Date(timestampMs))} · $stack"
        return Pair(line1, line2)
    }
}
```

- [ ] **Step 4: Run tests**

```
./gradlew test --tests "*.PhotoStamperTest"
```

Expected: all 6 tests pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/superpowerscameraresearch/overlay/PhotoStamper.kt
git add app/src/test/java/com/example/superpowerscameraresearch/overlay/PhotoStamperTest.kt
git commit -m "feat: PhotoStamper burns settings text onto a Bitmap, with tests"
```

---

## Task 8: `SourceDetectionOverlay` view + layout wiring

**Files:**
- Create: `app/src/main/java/com/example/superpowerscameraresearch/overlay/SourceDetectionOverlay.kt`
- Modify: `app/src/main/res/layout/fragment_camera2.xml`
- Modify: `app/src/main/res/layout/fragment_camerax.xml`

- [ ] **Step 1: Create `SourceDetectionOverlay`**

```kotlin
// app/src/main/java/com/example/superpowerscameraresearch/overlay/SourceDetectionOverlay.kt
package com.example.superpowerscameraresearch.overlay

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

class SourceDetectionOverlay @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private var sources: List<DetectedSource> = emptyList()
    // Dimensions of the analysis frame the sources were detected in.
    private var analysisWidth: Int = 640
    private var analysisHeight: Int = 360

    private val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF4444")
        style = Paint.Style.STROKE
        strokeWidth = 3f  // overridden in onDraw to 1.5dp
    }

    fun setSources(sources: List<DetectedSource>, analysisWidth: Int = 640, analysisHeight: Int = 360) {
        this.sources = sources
        this.analysisWidth = analysisWidth
        this.analysisHeight = analysisHeight
        // Called from analysis thread; post to ensure we draw on the UI thread.
        post { invalidate() }
    }

    fun clear() {
        sources = emptyList()
        post { invalidate() }
    }

    override fun onDraw(canvas: Canvas) {
        if (sources.isEmpty()) return

        // Scale from analysis coords to view coords, preserving aspect fill.
        val scaleX = width.toFloat() / analysisWidth
        val scaleY = height.toFloat() / analysisHeight

        circlePaint.strokeWidth = 1.5f * resources.displayMetrics.density

        for (src in sources) {
            val vx = src.cx * scaleX
            val vy = src.cy * scaleY
            val vr = src.radius * maxOf(scaleX, scaleY)
            canvas.drawCircle(vx, vy, vr, circlePaint)
        }
    }

    companion object {
        /**
         * Draws detection circles onto a full-resolution Bitmap for saving.
         * Scales source coordinates from [analysisWidth × analysisHeight] to the bitmap dimensions.
         */
        fun drawOnto(
            bitmap: Bitmap,
            sources: List<DetectedSource>,
            analysisWidth: Int,
            analysisHeight: Int
        ): Bitmap {
            if (sources.isEmpty()) return bitmap
            val out = bitmap.copy(Bitmap.Config.ARGB_8888, true)
            val canvas = Canvas(out)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#FF4444")
                style = Paint.Style.STROKE
                strokeWidth = out.width * 0.002f  // ~0.2% of image width
            }
            val scaleX = out.width.toFloat() / analysisWidth
            val scaleY = out.height.toFloat() / analysisHeight
            for (src in sources) {
                canvas.drawCircle(
                    src.cx * scaleX,
                    src.cy * scaleY,
                    src.radius * maxOf(scaleX, scaleY),
                    paint
                )
            }
            return out
        }
    }
}
```

- [ ] **Step 2: Add overlay to `fragment_camera2.xml`**

Add after the `ReticleView` entry (and before `HistogramView`):

```xml
    <com.example.superpowerscameraresearch.overlay.SourceDetectionOverlay
        android:id="@+id/sourceDetectionOverlay"
        android:layout_width="match_parent"
        android:layout_height="match_parent" />
```

- [ ] **Step 3: Add overlay to `fragment_camerax.xml`**

Same addition in the same position (after `ReticleView`):

```xml
    <com.example.superpowerscameraresearch.overlay.SourceDetectionOverlay
        android:id="@+id/sourceDetectionOverlay"
        android:layout_width="match_parent"
        android:layout_height="match_parent" />
```

- [ ] **Step 4: Build to check for errors**

```
./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL (no code references the new view yet, but layout inflation must not fail).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/superpowerscameraresearch/overlay/SourceDetectionOverlay.kt
git add app/src/main/res/layout/fragment_camera2.xml
git add app/src/main/res/layout/fragment_camerax.xml
git commit -m "feat: SourceDetectionOverlay view — live circle drawing and drawOnto() for saved photos"
```

---

## Task 9: Camera2Fragment — DETECT pill and slider

**Files:**
- Modify: `app/src/main/java/com/example/superpowerscameraresearch/camera2/Camera2Fragment.kt`

The DETECT pill follows the exact same pattern as the existing ZOOM pill. Sensitivity 0 = grey "DETECT OFF", 1–100 = red "DETECT xx%". Night mode does NOT lock out the DETECT pill.

- [ ] **Step 1: Add DETECT pill to `setupPills()`**

In `setupPills()`, find where the existing pill list is built (the `params` list around line 155–170 in Camera2Fragment). Add a DETECT entry:

```kotlin
params += Triple("DETECT", "#FFFF6666") { showDetectSlider() }
```

In the `pill.text` assignment block (the `when` on tag), add:

```kotlin
"DETECT" -> if (settings.sourceDetectionSensitivity == 0) "DETECT OFF"
             else "DETECT ${settings.sourceDetectionSensitivity}%"
```

- [ ] **Step 2: Add `showDetectSlider()` function**

Add alongside the other `show*Slider` functions:

```kotlin
private fun showDetectSlider() {
    showSlider("DETECT", "OFF ←→ 100%") { progress ->
        settings = settings.copy(sourceDetectionSensitivity = progress)
        binding.tvParamValue.text = if (progress == 0) "OFF" else "$progress%"
        updateDetectPill()
        controller.applySettings(settings)
        if (progress == 0) binding.sourceDetectionOverlay.clear()
    }
    binding.seekBar.progress = settings.sourceDetectionSensitivity
    binding.tvParamValue.text = if (settings.sourceDetectionSensitivity == 0) "OFF"
                                 else "${settings.sourceDetectionSensitivity}%"
}
```

- [ ] **Step 3: Add `updateDetectPill()` helper**

```kotlin
private fun updateDetectPill() {
    val pill = binding.pillsContainer.findViewWithTag<TextView>("DETECT") ?: return
    if (settings.sourceDetectionSensitivity == 0) {
        pill.text = "DETECT OFF"
        pill.alpha = 0.4f
    } else {
        pill.text = "DETECT ${settings.sourceDetectionSensitivity}%"
        pill.alpha = 1f
    }
}
```

- [ ] **Step 4: Add `onFrameAvailable` parameter to `Camera2Controller`**

`Camera2Fragment` doesn't see raw luma frames — they're consumed inside the histogram reader. Add a callback so the fragment can run detection on the same bytes.

In `Camera2Controller.kt`, add `onFrameAvailable` as the last constructor parameter (with default `null` so existing callers don't break):

```kotlin
class Camera2Controller(
    private val context: Context,
    private val textureView: TextureView,
    private var cameraId: String,
    private val onSettingsConfirmed: (CameraSettings) -> Unit,
    private val onFpsUpdate: (Float) -> Unit,
    private val onHistogramReady: (IntArray, Boolean) -> Unit,
    private val onLiveStatsUpdate: (aperture: Float?, focalLength: Float?, focusDistance: Float?, aeState: String) -> Unit,
    private val onFrameAvailable: ((luma: ByteArray, stride: Int, width: Int, height: Int) -> Unit)? = null
)
```

In `openCamera()`, in the histogram reader's `setOnImageAvailableListener`, invoke it right after `onHistogramReady` (the `bytes` variable is already in scope at that point):

```kotlin
onHistogramReady(histogram, clipping)
onFrameAvailable?.invoke(bytes, plane.rowStride, image.width, image.height)
```

- [ ] **Step 5: Wire overlay in `Camera2Fragment`**

Add fields near the top of `Camera2Fragment`:

```kotlin
private val sourceDetector: SourceDetector = ConnectedComponentDetector()
private var lastSources: List<DetectedSource> = emptyList()
```

Add the required imports:

```kotlin
import com.example.superpowerscameraresearch.overlay.ConnectedComponentDetector
import com.example.superpowerscameraresearch.overlay.DetectedSource
import com.example.superpowerscameraresearch.overlay.SourceDetector
```

In the `Camera2Controller` construction call in `onViewCreated`, add the `onFrameAvailable` named argument:

```kotlin
onFrameAvailable = { luma, stride, w, h ->
    val sens = settings.sourceDetectionSensitivity
    if (sens > 0) {
        val sources = sourceDetector.detect(luma, stride, w, h, sens)
        lastSources = sources
        binding.sourceDetectionOverlay.setSources(sources)
    } else {
        lastSources = emptyList()
    }
},
```

- [ ] **Step 6: Build and verify**

```
./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/example/superpowerscameraresearch/camera2/Camera2Fragment.kt
git add app/src/main/java/com/example/superpowerscameraresearch/camera2/Camera2Controller.kt
git commit -m "feat: Camera2 DETECT pill — live source detection with SourceDetectionOverlay"
```

---

## Task 10: Camera2Controller — JPEG capture, PhotoStamper, dual-save

**Files:**
- Modify: `app/src/main/java/com/example/superpowerscameraresearch/camera2/Camera2Controller.kt`
- Modify: `app/src/main/java/com/example/superpowerscameraresearch/camera2/Camera2Fragment.kt`

Currently Camera2Controller saves only a DNG. This task adds a JPEG `ImageReader` to the session, stamps it with `PhotoStamper`, and saves an additional `_detect.jpg` when detection is active.

- [ ] **Step 1: Add lens info setters and stamp snapshot to `Camera2Controller`**

Add fields near the top of `Camera2Controller`:

```kotlin
private var stampAperture: Float? = null
private var stampFocalLengthMm: Float? = null

// Snapshot of settings at the moment captureRaw() is called, used by the JPEG listener.
@Volatile private var pendingStampSettings: CameraSettings? = null
// Sources detected at the moment of capture, for the _detect.jpg.
@Volatile private var pendingDetectSources: List<com.example.superpowerscameraresearch.overlay.DetectedSource> = emptyList()
```

Add a public setter (called from the fragment after capabilities are known):

```kotlin
fun setLensInfo(aperture: Float, focalLengthMm: Float) {
    stampAperture = aperture
    stampFocalLengthMm = focalLengthMm
}
```

- [ ] **Step 2: Add JPEG `ImageReader` in `openCamera()`**

After the `histogramReader` init block, add:

```kotlin
val jpegReader = ImageReader.newInstance(1920, 1080, ImageFormat.JPEG, 2).also {
    this.jpegReader = it
    it.setOnImageAvailableListener({ reader ->
        val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
        val stampSettings = pendingStampSettings ?: currentSettings
        val detectSources = pendingDetectSources
        try {
            val bytes = ByteArray(image.planes[0].buffer.remaining())
            image.planes[0].buffer.get(bytes)
            val rawBitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                ?: return@setOnImageAvailableListener
            saveStampedJpeg(rawBitmap, stampSettings, detectSources)
        } finally {
            image.close()
        }
    }, cameraHandler)
}
```

Add the field declaration near `rawReader`:

```kotlin
private var jpegReader: ImageReader? = null
```

- [ ] **Step 3: Add `jpegReader.surface` to the capture session**

In `startPreviewSession`, update the `surfaces` buildList to include the JPEG surface:

```kotlin
val surfaces = buildList {
    add(previewSurface)
    add(histSurface)
    rawReader?.surface?.let { add(it) }
    jpegReader?.surface?.let { add(it) }
}
```

- [ ] **Step 4: Add JPEG surface as capture target in `captureRaw()`**

In `captureRaw()`, update the capture request to also target the JPEG reader:

```kotlin
fun captureRaw(detectedSources: List<com.example.superpowerscameraresearch.overlay.DetectedSource> = emptyList()) {
    val rawReaderLocal = rawReader ?: return
    val jpegReaderLocal = jpegReader ?: return
    val surface = previewSurface ?: return

    pendingStampSettings = currentSettings
    pendingDetectSources = detectedSources

    val builder = cameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE) ?: return
    builder.addTarget(surface)
    builder.addTarget(rawReaderLocal.surface)
    builder.addTarget(jpegReaderLocal.surface)
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
```

- [ ] **Step 5: Add `saveStampedJpeg()` helper**

```kotlin
private fun saveStampedJpeg(
    rawBitmap: android.graphics.Bitmap,
    settings: CameraSettings,
    detectSources: List<com.example.superpowerscameraresearch.overlay.DetectedSource>
) {
    val ts = System.currentTimeMillis()
    val stamped = PhotoStamper.stamp(rawBitmap, settings, stampAperture, stampFocalLengthMm, "Camera2", ts)

    fun saveBitmap(bitmap: android.graphics.Bitmap, name: String) {
        runCatching {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, "DCIM/CameraInvestigations")
            }
            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)!!
            context.contentResolver.openOutputStream(uri)!!.use { out ->
                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 95, out)
            }
        }.onFailure { Log.e(TAG, "Failed to save JPEG $name", it) }
    }

    saveBitmap(stamped, "IMG_$ts.jpg")

    if (settings.sourceDetectionSensitivity > 0 && detectSources.isNotEmpty()) {
        val annotated = SourceDetectionOverlay.drawOnto(stamped, detectSources, 640, 360)
        saveBitmap(annotated, "IMG_${ts}_detect.jpg")
    }
}
```

Add the required imports at the top of `Camera2Controller.kt`:
```kotlin
import com.example.superpowerscameraresearch.overlay.PhotoStamper
import com.example.superpowerscameraresearch.overlay.SourceDetectionOverlay
```

- [ ] **Step 6: Close `jpegReader` in `closeCameraInternal()`**

```kotlin
jpegReader?.setOnImageAvailableListener(null, null)
jpegReader?.close(); jpegReader = null
```

- [ ] **Step 7: Update `Camera2Fragment` to pass detect sources and lens info**

In `Camera2Fragment.onViewCreated`, after controller construction, call:

```kotlin
controller.setLensInfo(currentCapabilities.primaryAperture, currentCapabilities.primaryFocalLength)
```

Update the `captureButton` click handler to pass current sources:

```kotlin
binding.captureButton.setOnClickListener {
    if (supported) {
        val sources = if (settings.sourceDetectionSensitivity > 0)
            (binding.sourceDetectionOverlay.tag as? List<*>)
                ?.filterIsInstance<com.example.superpowerscameraresearch.overlay.DetectedSource>()
                ?: emptyList()
        else emptyList()
        controller.captureRaw(sources)
    }
}
```

Pass the `lastSources` field (defined in Task 9 Step 5) on capture:

```kotlin
controller.captureRaw(lastSources)
```

- [ ] **Step 8: Build**

```
./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/example/superpowerscameraresearch/camera2/Camera2Controller.kt
git add app/src/main/java/com/example/superpowerscameraresearch/camera2/Camera2Fragment.kt
git commit -m "feat: Camera2 stamped JPEG capture + dual-save annotated detect file"
```

---

## Task 11: CameraXFragment — DETECT pill (lower priority)

**Files:**
- Modify: `app/src/main/java/com/example/superpowerscameraresearch/camerax/CameraXFragment.kt`

Same pill/slider pattern as Task 9 but for CameraXFragment. CameraX already pipes frames through the `ImageAnalysis` use case in `CameraXController`.

- [ ] **Step 1: Add `sourceDetector` field and `lastSources` to `CameraXFragment`**

```kotlin
private val sourceDetector: SourceDetector = ConnectedComponentDetector()
private var lastSources: List<DetectedSource> = emptyList()
```

- [ ] **Step 2: Add DETECT pill to `setupPills()` in `CameraXFragment`**

```kotlin
Triple("DETECT", "#FFFF6666") { showDetectSlider() }
```

In the pill text `when` block:
```kotlin
"DETECT" -> if (settings.sourceDetectionSensitivity == 0) "DETECT OFF"
             else "DETECT ${settings.sourceDetectionSensitivity}%"
```

- [ ] **Step 3: Add `showDetectSlider()` to `CameraXFragment`**

```kotlin
private fun showDetectSlider() {
    val maxZoom = currentCapabilities.zoomRatioRange?.upper ?: 10f
    binding.tvParamName.text = "DETECT"
    binding.tvParamRange.text = "OFF ←→ 100%"
    binding.sliderPanel.visibility = View.VISIBLE
    binding.seekBar.max = 100
    binding.seekBar.progress = settings.sourceDetectionSensitivity
    binding.tvParamValue.text = if (settings.sourceDetectionSensitivity == 0) "OFF"
                                 else "${settings.sourceDetectionSensitivity}%"
    binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
            if (!fromUser) return
            settings = settings.copy(sourceDetectionSensitivity = p)
            binding.tvParamValue.text = if (p == 0) "OFF" else "$p%"
            controller.applySettings(settings)
            updateDetectPill()
            if (p == 0) binding.sourceDetectionOverlay.clear()
        }
        override fun onStartTrackingTouch(sb: SeekBar) = Unit
        override fun onStopTrackingTouch(sb: SeekBar) = Unit
    })
}

private fun updateDetectPill() {
    val pill = binding.pillsContainer.findViewWithTag<TextView>("DETECT") ?: return
    if (settings.sourceDetectionSensitivity == 0) {
        pill.text = "DETECT OFF"; pill.alpha = 0.4f
    } else {
        pill.text = "DETECT ${settings.sourceDetectionSensitivity}%"; pill.alpha = 1f
    }
}
```

- [ ] **Step 4: Add `onFrameAvailable` parameter to `CameraXController`**

In `CameraXController.kt`, add `onFrameAvailable` as the last constructor parameter (with default `null`):

```kotlin
class CameraXController(
    private val context: Context,
    private val previewView: PreviewView,
    private val lifecycleOwner: LifecycleOwner,
    private val onFpsUpdate: (Float) -> Unit,
    private val onHistogramReady: (IntArray, Boolean) -> Unit,
    private val onExtensionsAvailability: (Map<Int, Boolean>) -> Unit = {},
    private val onFrameAvailable: ((luma: ByteArray, stride: Int, width: Int, height: Int) -> Unit)? = null
)
```

In `bindUseCases()`, inside the `ia.setAnalyzer` block, invoke the callback right after `onHistogramReady` (the `plane` and `imageProxy` variables are in scope):

```kotlin
onHistogramReady(histogram, HistogramComputer.isClipping(histogram))
if (++frameCount % 3 == 0) {
    // already computed above; re-use bytes
    onFrameAvailable?.invoke(
        plane.buffer.let { buf -> ByteArray(buf.remaining()).also { buf.get(it) } },
        plane.rowStride, imageProxy.width, imageProxy.height
    )
}
```

Note: the luma bytes are re-read from the buffer here; ensure `imageProxy.planes[0]` is accessed before `imageProxy.close()`.

Wire in `CameraXFragment` by adding the `onFrameAvailable` argument when constructing the controller:

```kotlin
onFrameAvailable = { luma, stride, w, h ->
    val sens = settings.sourceDetectionSensitivity
    if (sens > 0) {
        val sources = sourceDetector.detect(luma, stride, w, h, sens)
        lastSources = sources
        binding.sourceDetectionOverlay.setSources(sources)
    } else {
        lastSources = emptyList()
    }
}
```

- [ ] **Step 5: Build**

```
./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/example/superpowerscameraresearch/camerax/CameraXFragment.kt
git add app/src/main/java/com/example/superpowerscameraresearch/camerax/CameraXController.kt
git commit -m "feat: CameraX DETECT pill — live source detection overlay"
```

---

## Task 12: CameraXController — PhotoStamper on capture (lower priority)

**Files:**
- Modify: `app/src/main/java/com/example/superpowerscameraresearch/camerax/CameraXController.kt`
- Modify: `app/src/main/java/com/example/superpowerscameraresearch/camerax/CameraXFragment.kt`

CameraX saves JPEG via `ImageCapture`. Intercept in `onImageSaved`, re-read the file, stamp it, and overwrite. Dual-save the detect file if sensitivity > 0.

- [ ] **Step 1: Add stamp params and detect sources to `CameraXController`**

```kotlin
private var stampAperture: Float? = null
private var stampFocalLengthMm: Float? = null

fun setLensInfo(aperture: Float, focalLengthMm: Float) {
    stampAperture = aperture
    stampFocalLengthMm = focalLengthMm
}

// Called by fragment before captureRaw to pass the latest detect state.
fun captureRaw(detectedSources: List<com.example.superpowerscameraresearch.overlay.DetectedSource> = emptyList()) {
    // (existing captureRaw body, with sources passed through)
}
```

- [ ] **Step 2: Update `captureRaw` to stamp the saved file**

Replace the existing `onImageSaved` callback:

```kotlin
object : ImageCapture.OnImageSavedCallback {
    override fun onImageSaved(output: ImageCapture.OutputFileResults) {
        val uri = output.savedUri ?: return
        val settings = currentSettings
        val sources = pendingDetectSources
        ContextCompat.getMainExecutor(context).execute {
            runCatching {
                val bm = context.contentResolver.openInputStream(uri)!!.use {
                    android.graphics.BitmapFactory.decodeStream(it)
                } ?: return@runCatching

                val stamped = PhotoStamper.stamp(bm, settings, stampAperture, stampFocalLengthMm, "CameraX")

                context.contentResolver.openOutputStream(uri, "wt")!!.use { out ->
                    stamped.compress(android.graphics.Bitmap.CompressFormat.JPEG, 95, out)
                }

                if (settings.sourceDetectionSensitivity > 0 && sources.isNotEmpty()) {
                    val annotated = SourceDetectionOverlay.drawOnto(stamped, sources, 640, 360)
                    val ts = System.currentTimeMillis()
                    val detectValues = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, "IMG_${ts}_detect.jpg")
                        put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                        put(MediaStore.MediaColumns.RELATIVE_PATH, "DCIM/CameraInvestigations")
                    }
                    val detectUri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, detectValues)!!
                    context.contentResolver.openOutputStream(detectUri)!!.use { out ->
                        annotated.compress(android.graphics.Bitmap.CompressFormat.JPEG, 95, out)
                    }
                }
            }.onFailure { Log.e(TAG, "Stamp failed", it) }
        }
    }
    override fun onError(exc: ImageCaptureException) { Log.e(TAG, "Capture failed", exc) }
}
```

Add `@Volatile private var pendingDetectSources: List<com.example.superpowerscameraresearch.overlay.DetectedSource> = emptyList()` near other volatile fields.

- [ ] **Step 3: Update `CameraXFragment` to call `setLensInfo` and pass sources**

```kotlin
// In onViewCreated, after controller start:
controller.setLensInfo(currentCapabilities.primaryAperture, currentCapabilities.primaryFocalLength)

// In captureButton click:
controller.captureRaw(lastSources)
```

- [ ] **Step 4: Final build and full test run**

```
./gradlew assembleDebug test
```

Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/superpowerscameraresearch/camerax/CameraXController.kt
git add app/src/main/java/com/example/superpowerscameraresearch/camerax/CameraXFragment.kt
git commit -m "feat: CameraX stamped JPEG capture + dual-save annotated detect file"
```
