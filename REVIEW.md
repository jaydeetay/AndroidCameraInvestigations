# Camera Investigation Suite — Review

Three Android apps were built from the same prompt, each using a different Claude Code generation approach. This document reviews how well each fulfilled the requirements and assesses code quality.

## The prompt

> I want to build a test Android app to help me understand the capabilities of the Android camera. Ultimately, I will use this knowledge to build a 'live view' into Sky Map where users can see the map overlaid over a view through the camera. We will possibly also use the images to do autocalibration, if the light sources (moon, bright stars) are detectible. To do this I need to experiment with the camera capabilities and that's where this app comes in. It should use the camera APIs that will be available to me in Sky Map. It should have onscreen controls where I can experiment with the various exposure and quality controls that the API makes available. It should show a simple overlay over the camera view - a targetting reticle like you might have in a first person shooter game will do. It should also display any settings that the camera API exposes (e.g. aperture, ISO, frames per second, shutter speed - whatever you can find). The app needs to be functional rather than pretty. Call out any areas where different phones might have different capabilities. Remember that this is mostly for testing at night looking at the sky. The app should have two tabs, one demonstrating the Camera2 API and one demonstrating CameraX, that we can flip between. Minimum API 28. Include a button to save RAW images. Include a histogram of the light levels in the UI as an overlay. Ensure we can select between the different cameras.

Key requirements: Camera2 + CameraX tabs · ISO/shutter/WB/focus/zoom controls · targeting reticle overlay · live camera stats display · camera selection · RAW save · histogram overlay · per-phone capability callouts · functional over pretty · min API 28 · night sky use case.

---

## The apps

| App | Repo | Approach |
|---|---|---|
| **CameraSuperpowers** (this repo) | [jaydeetay/AndroidCameraInvestigations](https://github.com/jaydeetay/AndroidCameraInvestigations) | Claude Code with Superpowers skills |
| **CameraSpecKit** | [jaydeetay/android-camera-investigations-speckit](https://github.com/jaydeetay/android-camera-investigations-speckit) | Claude Code with SpecKit |
| **CameraOneShot** | [jaydeetay/CameraInvestigationOneShot](https://github.com/jaydeetay/CameraInvestigationOneShot) | One-shotted by Claude |

---

## CameraSuperpowers

![CameraSuperpowers main screen](screenshot.png)

### Prompt fulfillment: ★★★★★

The strongest implementation of the feature set. The pill-based control system (ISO, SS, WB, FOCUS, ZOOM, OIS) is exactly the right UX for quick in-field adjustments — tapping a pill slides up a labelled seekbar. White balance goes all the way to a colour-temperature model (2000K–8000K) with a proper `RggbChannelVector` gain calculation, which no other app attempts. Zoom correctly uses `CONTROL_ZOOM_RATIO` on API 30+ and falls back to `SCALER_CROP_REGION` crop for older devices. The camera selector bottom sheet shows focal length, aperture, hardware level, RAW and OIS support for every lens — highly relevant for the night-sky use case. The histogram runs on a dedicated thread and samples every 4th pixel to keep it cheap. The reticle (circle + crosshair + corner brackets) looks right for the FPS analogy in the prompt.

One incomplete piece: `onSettingsConfirmed` in `Camera2Fragment` has a `/* will update HUD in a future task */` comment — confirmed ISO/shutter values from the capture result are computed but never displayed. So the HUD doesn't update to show what the camera actually used in auto-AE.

### Code quality: ★★★★☆

Well-structured: `Camera2Controller` handles all session lifecycle, `CameraCapabilities`/`CameraSettings` are clean data models, `HistogramComputer` is a separate stateless object. The OIS detection uses a sketchy unchecked cast via key name reflection (`characteristics.keys.firstOrNull { it.name == "android.lens.info.availableOpticalStabilization" }`) — this should just use `CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION` directly. 1532 lines, 11 files.

---

## CameraSpecKit

![CameraSpecKit main screen](https://raw.githubusercontent.com/jaydeetay/android-camera-investigations-speckit/001-camera-investigation-app/screenshot.png)

### Prompt fulfillment: ★★★☆☆

Works correctly — but partially implements the spec. ISO and shutter controls are present and functional. WB, focus mode, and zoom controls are absent. The reticle is a gap-style crosshair (no enclosing circle), which is more minimal than the FPS-style the prompt described. The histogram is present and throttled to 2Hz.

The standout feature is the live settings panel, which surfaces both static camera characteristics and live capture-result values (ISO, shutter, FPS range, AF state) using a `SettingRow` model that distinguishes live vs. static entries — the most principled implementation of the "display camera settings" requirement. The reconnection logic with a `SessionState` sealed class is the only app to handle `onDisconnected` properly, which matters for a device expected to be used in the field at night.

The 16KB ELF alignment issue (`libimage_processing_util_jni.so`) causes a dialog on every cold start — an unresolved dependency problem. `observeSettings` in the Camera2 fragment is a stub with a comment and no body.

### Code quality: ★★★★★

The best-engineered codebase. `StateFlow` + coroutines for session state, histogram data, and capture results is the right architecture. `Camera2SessionManager` and `RawCaptureHelper` are properly separated. `CameraEnumerator`, `CharacteristicsFormatter`, `MediaStoreHelper`, and `CameraLogger` are utility classes that belong in production code. `CaptureSettings` is a clean immutable data class. 1564 lines across 18 files with tight single-responsibility separation.

---

## CameraOneShot

![CameraOneShot main screen](https://raw.githubusercontent.com/jaydeetay/CameraInvestigationOneShot/main/screenshot.png)

### Prompt fulfillment: ★★★★☆

The most feature-complete on paper. The live info overlay (ISO, shutter, FPS, aperture, focal length, focus distance, AE state, AF state) reading directly from capture results is the richest of the three — values visibly change in real time as the camera adjusts. Has spinners for camera selection, AE mode (auto/manual), white balance, noise reduction, and focus mode. The capabilities panel at the bottom of the screen is always visible without a tap. Amber reticle (0xCCFF4400) with tick marks on the circle looks good. RAW capture (DNG) works on the Camera2 tab.

Two notable failures: the app crashes when switching between API tabs (acknowledged in the README); and the CameraX "Capture RAW" button saves a JPEG with a `.jpg` extension while displaying a toast explaining it's not real RAW — functional but misleading. CameraX manual ISO/shutter ranges are read via Camera2 interop but no sliders were implemented for them on that tab.

### Code quality: ★★☆☆☆

Structurally the weakest. `Camera2Fragment.kt` is 854 lines — a God class containing threading, session management, ImageReader lifecycle, capture callbacks, UI binding, and file I/O all in one place. The CameraX histogram converts YUV to RGBA bitmap and then calls `Bitmap.createScaledBitmap` + `getPixels` — significantly heavier than reading the Y plane directly as the other two apps do. No reconnection logic. 1603 lines across just 6 files.

---

## Summary

| | Superpowers | SpecKit | OneShot |
|---|---|---|---|
| Camera2 + CameraX tabs | ✅ | ✅ | ✅ (crashes on switch) |
| ISO / shutter controls | ✅ | ✅ | ✅ |
| WB / focus / zoom controls | ✅ | ❌ | ✅ (partial on CameraX) |
| Reticle overlay | ✅ | ✅ (minimal) | ✅ |
| Live stats HUD | Partial | ✅ | ✅ (best) |
| Histogram | ✅ | ✅ | ✅ |
| Camera selector | ✅ | ✅ | ✅ |
| RAW save | ✅ | ✅ | ✅ (CameraX tab: JPEG only) |
| Phone variability callouts | ✅ | ✅ | ✅ |
| Reconnection handling | ❌ | ✅ | ❌ |
| Code structure | Good | Excellent | Poor (God class) |

**Overall:** SpecKit has the best code. Superpowers has the best feature set. OneShot has the richest live data display but the worst architecture and a known crash.
