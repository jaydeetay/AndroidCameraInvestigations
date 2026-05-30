# CameraSuperpowers — Improvements Design

**Date:** 2026-05-29

## Overview

Seven improvements to the CameraSuperpowers app, addressing weaknesses identified in the comparative review and usability issues found during hands-on use. Goal is a more reliable, more usable field tool for night-sky camera experimentation.

---

## 1. Layout: pill controls — more space above shutter

**Problem:** The pill controls (ISO, SS, WB, FOCUS, ZOOM, OIS) sit only ~4dp above the shutter button, making accidental taps likely.

**Change:** In `fragment_camera2.xml`, increase `pillsContainer` `android:layout_marginBottom` from `56dp` to `80dp`. No logic changes.

---

## 2. Layout: slider panel — inset from screen edges

**Problem:** The seekbar thumb reaches the screen edge, triggering Android's back gesture when dragging from the left or right.

**Change:** In `fragment_camera2.xml`:
- Add `android:layout_marginHorizontal="16dp"` to `sliderPanel`
- Add `android:paddingHorizontal="8dp"` to the `SeekBar`
- Replace the inline `android:background="#EE000000"` color with a new drawable `slider_panel_bg.xml` — a shape with `solid` color `#EE000000` and `topLeftRadius`/`topRightRadius` of 8dp (no bottom rounding since it sits at screen bottom).

Keeps the thumb ~24dp from screen edge.

---

## 3. Confirmed-values HUD

**Problem:** `onSettingsConfirmed` in `Camera2Fragment` is a stub (`/* will update HUD in a future task */`). Confirmed ISO and shutter speed are computed in `captureCallback` but discarded. In auto-AE the user cannot see what the camera is actually doing.

**Change:**

- Add two TextViews to `fragment_camera2.xml`: `tvIso` and `tvShutter`, in the top HUD strip alongside `tvFps`. Style: monospace 11sp, `hud_label_bg` background, same colour family as existing labels.
- Wire `onSettingsConfirmed` in `Camera2Fragment.onViewCreated` to update both views on the UI thread:
  ```kotlin
  onSettingsConfirmed = { confirmed ->
      activity?.runOnUiThread {
          _binding?.tvIso?.text = "ISO ${confirmed.iso}"
          _binding?.tvShutter?.text = CameraSettings.shutterNsToDisplay(confirmed.shutterNs)
      }
  }
  ```
- Labels show confirmed (actual) values only — no "requested vs. actual" distinction.

---

## 4. Shutter press feedback

**Problem:** Tapping the capture button gives no feedback. It's unclear whether the capture was triggered.

**Change:** In `Camera2Fragment.setupCapture()`:
1. `binding.captureButton.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)` — fires immediately on tap.
2. `ViewPropertyAnimator` scale pulse: scale to 1.3× over 120ms, back to 1.0× over 120ms.

No toast, no screen flash.

---

## 5. Reconnection handling

**Problem:** If the camera disconnects (another app steals it, brief interrupt), the app shows a dead preview. The user must kill and relaunch.

**Change:** In `Camera2Controller`:
- Add `private var retryCount = 0` and `private const val MAX_RETRIES = 3`.
- In `onDisconnected` and `onError` callbacks: call `device.close()`, set `cameraDevice = null`, set `isOpening = false`.
- If `retryCount < MAX_RETRIES`: increment `retryCount`, post a 500ms-delayed `openCamera()` on `cameraHandler`.
- In `onOpened`: reset `retryCount = 0`.
- On retry exhaustion: log the failure and stop (silent — no crash, no UI change).

---

## 6. Code quality: OIS detection

**Problem:** OIS support is detected via a sketchy reflection lookup on key name (`characteristics.keys.firstOrNull { it.name == "android.lens.info.availableOpticalStabilization" }`). The constant `CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION` exists and should be used directly.

**Change:** In `Camera2Controller.applySettings(builder, s)`, replace the reflection block with:
```kotlin
val oisModes = characteristics.get(
    CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION
) ?: intArrayOf()
```

---

## 7. Screen keep-on

**Problem:** The display times out during a session, requiring a wake-and-unlock before continuing.

**Change:** In both `Camera2Fragment` and `CameraXFragment`:
- `onResume`: `activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)`
- `onPause`: `activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)`

---

## Files changed

| File | Changes |
|---|---|
| `res/drawable/slider_panel_bg.xml` | New shape drawable — dark background with rounded top corners |
| `fragment_camera2.xml` | Pill margin, slider panel inset + new bg, add tvIso + tvShutter HUD labels |
| `Camera2Fragment.kt` | Wire onSettingsConfirmed, shutter animation + haptics, screen keep-on |
| `CameraXFragment.kt` | Screen keep-on |
| `Camera2Controller.kt` | Reconnection retry, OIS fix |

## Out of scope

- CameraX tab improvements (separate pass)
- Live stats panel (like SpecKit's `SettingRow` model) — separate task
- Histogram improvements
