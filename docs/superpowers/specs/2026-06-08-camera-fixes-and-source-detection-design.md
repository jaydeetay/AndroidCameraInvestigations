# Camera Fixes & Source Detection Design
**Date:** 2026-06-08

---

## Scope

Three bug fixes and two new features for both the Camera2 and CameraX camera stacks.

---

## Bug Fixes

### BUG-1 — Zoom pill locked out in night mode

**Files:** `Camera2Fragment.kt`, `CameraXFragment.kt`

Both fragments' `setManualPillsEnabled()` include `"ZOOM"` in the lockout list applied when night mode activates. Zoom is a hardware capability independent of scene mode or Extensions, so it should remain interactive.

**Fix:** Remove `"ZOOM"` from the tag list in both `setManualPillsEnabled()` calls.

---

### BUG-2 — Camera2 zoom has no effect

**Files:** `Camera2Controller.kt`

`CONTROL_ZOOM_RATIO` (API 30+) is applied unconditionally on qualifying API versions, but many devices don't actually support it — support must be checked via `CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE`. When the key is set on an unsupporting device it is silently ignored.

Additionally, `applySettings(CameraSettings)` contains an early-return guard on `histogramReader?.surface` that will silently no-op the entire settings update (including zoom) if the histogram reader is in an unexpected state.

**Fix:**
1. Gate `CONTROL_ZOOM_RATIO` on `characteristics.get(CONTROL_ZOOM_RATIO_RANGE) != null`. Fall back to `SCALER_CROP_REGION` on all other devices (existing path).
2. Separate `histogramReader` null-guard from the zoom/settings update path so that a missing histogram surface doesn't suppress camera settings.

---

### BUG-3 — CameraX night mode freezes the app

**File:** `CameraXController.kt`

`bindUseCases()` binds `preview + analysis + imageCapture` together with the Extensions camera selector. CameraX Extensions do not support `ImageAnalysis` as a simultaneous use case on most devices. `prov.unbindAll()` fires before the bind attempt; when the bind fails the exception is swallowed by `runCatching`, leaving the camera unbound with a frozen preview.

Additionally, zoom is not applied to the CameraX camera in night mode because `applyCamera2Interop` (which sets `CONTROL_ZOOM_RATIO`) is intentionally skipped for the Extensions path. The `Camera` object returned by `bindToLifecycle` is not stored, so `cameraControl.setZoomRatio()` is never called.

**Fix:**
1. When `useNight = true`, bind only `preview + imageCapture` (drop `analysis`). This means no histogram/FPS readout in night mode, which is acceptable.
2. Store the `Camera` result from `prov.bindToLifecycle(...)` in a field. After binding, call `camera.cameraControl.setZoomRatio(currentSettings.zoom)` when `useNight` is true so zoom is respected.

---

## Feature: Settings Stamp

### Overview

Every saved photo gets a single-line monospace text strip burned into the bottom of the JPEG, recording the capture settings. Target readers are the user reviewing their own shots later.

### Implementation

A new `PhotoStamper` utility object with a single method:

```kotlin
object PhotoStamper {
    fun stamp(bitmap: Bitmap, line1: String, line2: String): Bitmap
}
```

Draws two lines of white monospace text (12sp) over a semi-transparent black gradient at the bottom of the bitmap. Returns a new bitmap (does not mutate the input). The gradient ensures readability on the mostly-black night sky images that are the primary use case.

**Stamp content:**

- Line 1: `ISO xxx · 1/xs · f/x.x · xxmm · x.x× · WB xxxxK · NR FAST · DETECT xx%`
- Line 2: `YYYY-MM-DD HH:MM:SS · Camera2` (or `CameraX`)

`PhotoStamper` is called from both `Camera2Controller` and `CameraXController` in their respective capture callbacks, before the image is written to `MediaStore`.

**Camera2:** currently saves RAW/DNG via `DngCreator`. The stamp is applied by adding a JPEG surface to the capture session in addition to the RAW surface, saving the RAW as-is (no stamp — DNG is a standard format and shouldn't be modified), and stamping the companion JPEG. The JPEG surface is new: add a second `ImageReader` (JPEG format, same resolution as preview, e.g. 1920×1080) to the capture session surfaces list.

**CameraX:** saves JPEG natively via `ImageCapture`. Intercept in `onImageSaved` by re-reading the saved file as a `Bitmap`, stamping it, and overwriting the file.

---

## Feature: Source Detection

### Overview

Detect bright point sources (stars) and extended bright objects (moon, planets) in the camera preview. Draw red circles over detected sources live on the preview and on saved photos. Sensitivity is adjustable via a pill-launched slider; setting it to 0 disables detection entirely.

### Architecture

```
SourceDetector (interface)
    └── ConnectedComponentDetector   ← first implementation
            (swappable via strategy pattern)

SourceDetectionOverlay (View)
    └── sits above PreviewView/SurfaceView in both fragment layouts

CameraSettings
    └── sourceDetectionSensitivity: Int = 0   (0 = off, 1–100)
```

### `SourceDetector` Interface

```kotlin
interface SourceDetector {
    /**
     * Detect bright sources in a grayscale luma plane.
     * Returns centroids + bounding radii in image pixel coordinates.
     */
    fun detect(luma: ByteArray, stride: Int, width: Int, height: Int, sensitivity: Int): List<DetectedSource>
}

data class DetectedSource(
    val cx: Float,   // centroid x in image coords
    val cy: Float,   // centroid y in image coords
    val radius: Float  // bounding radius in image pixels
)
```

The interface is intentionally minimal. Future implementations (Gaussian centroiding, PSF fitting) replace only the class, not the call sites. The centroid values `cx/cy` are the primary scientific output; the radius is used only for visualisation.

### `ConnectedComponentDetector`

Algorithm (all tunable constants are named and doc-commented):

1. **Threshold** — `T = (1f - sensitivity / 100f) * 255f`. High sensitivity → low T → more sources.
2. **Labeling** — single-pass union-find over all pixels where `luma[i] ≥ T`. Adjacent 4-connected pixels share a label.
3. **Per-label statistics** — accumulate pixel count, sum of x/y (for centroid), bounding box (for radius).
4. **Centroid** — `cx = sumX / count`, `cy = sumY / count`.
5. **Radius** — `max(boundingWidth, boundingHeight) / 2`, clamped to `[2px, frameWidth / 4]`. The upper clamp discards whole-sky fog blobs.
6. **Noise filter** — discard blobs with `count < MIN_BLOB_PIXELS` (default 2) to reject single hot pixels.

Runs at analysis resolution (640 × 360). Expected runtime < 5 ms per frame on mid-range hardware. Detection is skipped entirely when `sensitivity == 0`.

### `SourceDetectionOverlay`

A transparent `View` placed above the camera preview surface in both fragment layouts. Holds a `List<DetectedSource>` in image coordinates. On `onDraw`, scales each source to view coordinates using the preview's known aspect-fill transform (same approach as `ReticleView`). Draws a `1.5dp` circle in `#FF4444` with no fill.

Updated from the image analysis thread via `post { invalidate() }` after each detection pass. When detection is off, the list is cleared and the view draws nothing (no invalidation cost).

### Pill and Slider

New `"DETECT"` pill added to both fragments' pill rows, colour `#FF6666`. Pill text:
- Sensitivity 0: `"DETECT OFF"` in grey (same disabled style as locked-out pills)
- Sensitivity 1–100: `"DETECT xx%"` in red

Tapping opens the existing `showSlider()` mechanism, range 0–100.

Night mode does **not** lock out the DETECT pill. Source detection runs on the image analysis pipeline, which is independent of capture mode.

### Dual-Save on Capture

When a photo is taken:

1. **Clean file** — `IMG_<timestamp>.jpg` — settings stamp only, no circles.
2. **Annotated file** — `IMG_<timestamp>_detect.jpg` — stamp + red circles drawn at full image resolution, scaled from the most recent `detectedSources` list. Only written when `sensitivity > 0`.

Both saved to `DCIM/CameraInvestigations`.

The annotated bitmap is produced by `SourceDetectionOverlay.drawOnto(bitmap, sources, imageWidth, imageHeight)` — a separate static helper that reuses the same drawing logic as the live overlay but at the saved image's resolution.

---

## `CameraSettings` Changes

```kotlin
data class CameraSettings(
    // ... existing fields ...
    val sourceDetectionSensitivity: Int = 0,   // 0 = off
)
```

---

## Files Affected

| File | Change |
|---|---|
| `Camera2Fragment.kt` | Remove ZOOM from lockout list; add DETECT pill + slider |
| `CameraXFragment.kt` | Remove ZOOM from lockout list; add DETECT pill + slider |
| `Camera2Controller.kt` | Fix CONTROL_ZOOM_RATIO gating; fix histogramReader guard; call PhotoStamper on capture |
| `CameraXController.kt` | Fix night bind (drop analysis); store Camera ref; apply zoom via cameraControl; call PhotoStamper |
| `CameraSettings.kt` | Add `sourceDetectionSensitivity` |
| `SourceDetector.kt` | New — interface + DetectedSource |
| `ConnectedComponentDetector.kt` | New — first implementation |
| `SourceDetectionOverlay.kt` | New — transparent overlay view |
| `PhotoStamper.kt` | New — bitmap text stamp utility |
| `fragment_camera2.xml` | Add SourceDetectionOverlay above preview surface |
| `fragment_camerax.xml` | Add SourceDetectionOverlay above PreviewView |
