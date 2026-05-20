# Android Camera Investigation App — Design Spec

**Date:** 2026-05-18  
**Purpose:** Investigate Android camera capabilities for use in Sky Map — specifically to understand manual exposure controls, image quality at night, and whether light sources (moon, bright stars) are detectable for autocalibration.

---

## Goals

- Experiment with Camera2 and CameraX APIs side by side
- Expose and control every available manual camera parameter
- Capture RAW/DNG images for offline quality analysis
- Display live histogram to assess exposure and star detectability
- Surface all device-reported camera capabilities so differences between phones are visible
- Note where capabilities vary by device

Out of scope: frame stacking (noted as a future feature), any Sky Map integration, image processing / star detection algorithms.

---

## Architecture

### Top-level structure

```
MainActivity
└── ViewPager2
    ├── Camera2Fragment        (tab 1)
    └── CameraXFragment        (tab 2)
```

`MainActivity` is minimal — it hosts the `ViewPager2` and a `TabLayout`. All camera logic lives in the fragments. ViewPager2 keeps only the active fragment in the `RESUMED` state, so camera resources are released when switching tabs (`onPause` closes the session, `onResume` opens it).

### Package layout

```
superpowerscameraresearch/
  MainActivity.kt
  camera2/
    Camera2Fragment.kt           — UI, lifecycle, wires views to controller
    Camera2Controller.kt         — opens CameraDevice, builds CaptureRequests, handles RAW capture
    Camera2Characteristics.kt    — reads CameraCharacteristics, produces CameraCapabilities model
  camerax/
    CameraXFragment.kt
    CameraXController.kt         — binds CameraX use cases, applies Camera2Interop for manual keys
  overlay/
    ReticleView.kt               — crosshair + circle overlay drawn on Canvas
    HistogramView.kt             — 256-bucket luminance histogram drawn on Canvas
  model/
    CameraSettings.kt            — ISO, shutterSpeed, whiteBalanceK, focusDistance, zoomRatio, oisEnabled
    CameraCapabilities.kt        — ranges, supported features, physical camera list
```

No shared base class between Camera2Fragment and CameraXFragment — the intentional duplication makes the API differences visible.

---

## UI Layout

### Fullscreen HUD

The camera preview fills the entire screen. Controls are overlaid as a HUD to minimise UI brightness (important for night use and dark adaptation).

**Idle state (no slider active):**
- Preview fills screen
- Tab bar (CAMERA2 / CAMERAX) at top
- Camera selector button top-left (below tabs) — shows current camera focal length
- FPS counter top-right
- Histogram bottom-left (semi-transparent, ~80×40dp)
- Reticle centred on preview
- Parameter pills row above capture button: ISO · SS · WB · FOCUS · ZOOM
- Capture button centred at bottom

**Active slider state (parameter tapped):**
- Slider panel slides up from bottom (~100dp tall)
- Shows: parameter name, current value, device range
- Other parameter pills remain visible above the slider (dimmed) — tap to switch
- Capture button moves to bottom-right corner

**Capabilities panel:**
- Accessed via ⓘ button; opens a bottom sheet
- Scrollable dump of all `CameraCharacteristics` values for the selected camera
- Formatted as a key/value list with human-readable labels

### Reticle

`ReticleView` is a transparent `View` in a `FrameLayout` over the preview. Draws outer circle, crosshair lines, and four corner brackets via `Canvas`. Colour: `#CC441111` (dim red) to preserve night vision. Scales with view size — no bitmaps.

### Histogram

`HistogramView` is a custom `View` overlaid bottom-left. Draws 256-bucket bar chart. Right edge highlighted red if >0.5% of pixels are at bin 255 (clipping warning — useful for not blowing out the moon). Updated via `postInvalidate()` from background thread.

---

## Controls

All controls apply in manual mode. Each parameter shows the device-reported range from `CameraCharacteristics`.

| Control | Camera2 key | Notes |
|---|---|---|
| ISO | `SENSOR_SENSITIVITY` | Range from `SENSOR_INFO_SENSITIVITY_RANGE` |
| Shutter speed | `SENSOR_EXPOSURE_TIME` | Logarithmic slider; range from `SENSOR_INFO_EXPOSURE_TIME_RANGE` |
| White balance | `COLOR_CORRECTION_MODE` + `COLOR_CORRECTION_GAINS` | Kelvin value; Auto toggle |
| Focus distance | `LENS_FOCUS_DISTANCE` | Dioptre value; ∞ button snaps to `0.0` (infinity) |
| Zoom | `CONTROL_ZOOM_RATIO` (API 30+) or active array crop (API 28–29) | API version noted in UI |
| OIS | `LENS_OPTICAL_STABILIZATION_MODE` | Toggle only shown if `LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION_MODES` includes OIS ⚠️ |
| Aperture | `LENS_APERTURE` | Control only shown if `LENS_INFO_AVAILABLE_APERTURES` has >1 value ⚠️ |

**Read-only HUD stats (from `CaptureResult` each frame):**
- Live FPS (computed from frame timestamps)
- Actual ISO applied (may differ from requested if device clamped it)
- Actual shutter speed applied
- Aperture value
- Focal length
- Hardware level badge: LEGACY / LIMITED / FULL / LEVEL_3

The HUD always shows what the camera *actually applied*, not what was requested — devices silently clamp values on LEGACY and LIMITED hardware.

---

## Camera Selection

A camera selector button (top-left HUD) cycles through available cameras. On tap it opens a bottom sheet listing each camera with:
- Facing (front / back)
- Focal length (identifies wide / main / telephoto)
- Aperture
- Hardware level
- RAW supported: yes / no

Selecting a camera tears down the current session and opens the new camera ID. The selector is independent per tab — Camera2Fragment and CameraXFragment each have their own selection.

**Logical vs physical cameras:** If a camera ID reports `LOGICAL_MULTI_CAMERA` capability, its physical sub-camera IDs are listed beneath it in the selector sheet with their individual characteristics.

---

## Camera Pipelines

### Camera2

```
CameraManager.getCameraIdList()
  → user selects camera ID
  → CameraCharacteristics → CameraCapabilities model → populate UI
  → CameraDevice.open()
    → CaptureRequest.Builder (TEMPLATE_PREVIEW)
      → apply CameraSettings
      → Surface targets:
          1. TextureView surface          → live preview
          2. ImageReader (YUV_420_888)    → histogram (every 3rd frame)
          3. ImageReader (RAW_SENSOR)     → DNG capture (on demand)
      → CameraCaptureSession.setRepeatingRequest()
      → CaptureCallback reads CaptureResult → updates HUD stats
```

Settings change → `CameraSettings` updated → Controller rebuilds and re-issues repeating request.

### CameraX

```
ProcessCameraProvider.getInstance()
  → user selects camera (CameraSelector + physical ID via Camera2CameraInfo)
  → Preview use case            → PreviewView
  → ImageAnalysis use case      → histogram (YUV, every 3rd frame, background Executor)
  → ImageCapture use case       → RAW DNG on demand
  → Camera2Interop.Extender applied to each use case
      → injects manual CaptureRequest keys from CameraSettings
```

`Camera2Interop` is used to inject the same manual keys as the Camera2 tab, making the comparison direct.

---

## RAW/DNG Capture

**Camera2:** `ImageReader` with `RAW_SENSOR` format. On capture: acquire `Image` and matching `TotalCaptureResult`, pass both to `DngCreator`. `DngCreator` embeds full lens/sensor metadata (focal length, aperture, ISO, shutter speed, white balance, noise model). Write to `MediaStore` via `ContentResolver` (no storage permission needed, API 29+).

**CameraX:** `ImageCapture` with `OUTPUT_FORMAT_RAW` (CameraX 1.3+). `Camera2Interop` injects manual capture keys so the DNG metadata is correct.

**If RAW unsupported:** `REQUEST_AVAILABLE_CAPABILITIES` must include `RAW`. If not present for the selected camera, the capture button is disabled and labelled "RAW unsupported". Common on ultrawide and front cameras. ⚠️

---

## Histogram Computation

- Source: Y plane of `YUV_420_888` frame (luminance only)
- Sampling: every 4th pixel to keep CPU load low
- Frequency: every 3rd frame
- Thread: dedicated `HandlerThread` (Camera2) or background `Executor` (CameraX) — never main thread
- Output: `IntArray(256)` passed to `HistogramView` via `postInvalidate()`
- Clipping warning: if >0.5% of sampled pixels land in bin 255, right edge of histogram rendered red

---

## Device Variability

Areas where phones differ significantly, called out in the UI:

| Feature | Note |
|---|---|
| Hardware level | LEGACY devices may silently ignore manual controls; shown as badge in HUD |
| RAW capture | Not supported on all cameras (ultrawide, front often unsupported) |
| OIS | Only shown if `LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION_MODES` includes OIS mode |
| Variable aperture | Only shown if `LENS_INFO_AVAILABLE_APERTURES` has >1 value (rare — Samsung Galaxy S9/S10 generation) |
| Zoom API | `CONTROL_ZOOM_RATIO` requires API 30+; app falls back to crop region on API 28–29, notes which is in use |
| ISO range | Varies widely; some phones cap at 3200, others reach 6400+ |
| Max shutter speed | Most phones support up to 30s; some are capped lower |
| Logical multi-camera | Available on many modern phones; physical sub-cameras listed separately |

---

## Project Setup

| Item | Value |
|---|---|
| Language | Kotlin |
| Min SDK | 28 (Android 9) |
| Target SDK | 34 |
| Build | Gradle with `libs.versions.toml` |

**Dependencies:**
```toml
[versions]
camerax = "1.3.4"

[libraries]
androidx-camera-core       = { module = "androidx.camera:camera-core",       version.ref = "camerax" }
androidx-camera-camera2    = { module = "androidx.camera:camera-camera2",    version.ref = "camerax" }
androidx-camera-lifecycle  = { module = "androidx.camera:camera-lifecycle",  version.ref = "camerax" }
androidx-camera-view       = { module = "androidx.camera:camera-view",       version.ref = "camerax" }
androidx-viewpager2        = { module = "androidx.viewpager2:viewpager2",    version = "1.0.0" }
material                   = { module = "com.google.android.material:material", version = "1.12.0" }
kotlinx-coroutines-android = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-android", version = "1.7.3" }
```

**Permissions:**
```xml
<uses-permission android:name="android.permission.CAMERA" />
```
Runtime permission requested on first launch. No internet or location permissions.

---

## Future Features (Out of Scope)

- **Frame stacking:** Capture N frames, average pixel values on background thread to reduce noise. The RAW `ImageReader` pipeline is the foundation this would build on.
- **Star detection / autocalibration:** Analyse captured DNG frames for point light sources. Feeds into Sky Map autocalibration.
- **Sky Map overlay:** Replace reticle with the actual Sky Map rendering composited over the camera preview.
