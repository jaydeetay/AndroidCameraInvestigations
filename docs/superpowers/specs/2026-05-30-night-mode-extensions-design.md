# Night Mode Extensions Design

**Date:** 2026-05-30  
**Status:** Approved

## Overview

Add three night-mode features to the camera research app:

1. `ExtensionMode.NIGHT` toggle in the CameraX tab (with manual-control lockout)
2. CameraX extension availability enumeration shown in the capabilities sheet
3. `CONTROL_SCENE_MODE_NIGHT` toggle in the Camera2 tab

A fourth option — native Camera2 `CameraExtensionSession` (API 31+) — is recorded as future work.

---

## 1. Data Model & Dependency

### CameraSettings additions

```kotlin
data class CameraSettings(
    // ... existing fields ...
    val nightMode: Boolean = false,       // CameraX ExtensionMode.NIGHT
    val nightSceneMode: Boolean = false,  // Camera2 CONTROL_SCENE_MODE_NIGHT
)
```

### New Gradle dependency

```kotlin
implementation(libs.androidx.camera.extensions)
```

Version is aligned with existing camera-core/camera2/lifecycle artifacts via the libs version catalog. The `androidx.camera.extensions` alias must also be added to `gradle/libs.versions.toml` under the same `camerax` version used by the other camera artifacts.

---

## 2. CameraX Tab

### Controller changes

`CameraXController` gains one new constructor parameter:

```kotlin
val onExtensionsAvailability: (Map<Int, Boolean>) -> Unit
```

This matches the existing callback pattern (`onFpsUpdate`, `onHistogramReady`).

During `start()`, the controller calls `ExtensionsManager.getInstanceAsync()` and caches the result. On resolution it fires `onExtensionsAvailability` with a map of `ExtensionMode` constant → `Boolean`. Relevant keys: `NIGHT`, `HDR`, `BOKEH`, `BEAUTY`, `AUTO`.

In `bindUseCases()`, when `settings.nightMode = true`:
- Obtains `extensionsManager.getExtensionEnabledCameraSelector(selector, ExtensionMode.NIGHT)`
- Uses that selector instead of the normal one; all other bind logic is identical

When `settings.nightMode = false`, the normal selector is used and `Camera2Interop` overrides are applied as before.

### Fragment changes

`setupPills()` reads from a `extensionsAvailability: Map<Int, Boolean>` field on the fragment. The NIGHT pill is only added when `extensionsAvailability[ExtensionMode.NIGHT] == true`.

When `onExtensionsAvailability` fires (asynchronously after `onViewCreated`), the fragment stores the map and calls `setupPills()` again on the main thread.

**Pill lockout:** When `settings.nightMode = true`:
- NIGHT pill label → `"NIGHT ON"`
- ISO, SS, WB, FOCUS, ZOOM pills → `alpha = 0.4f`, `isClickable = false`

Deactivating NIGHT restores all pills to normal.

### Extension enumeration in capabilities sheet

`showCapabilitiesSheet()` appends a "CameraX Extensions" section after the existing characteristics dump. For each of the five `ExtensionMode` constants it shows:

```
NIGHT     ✓
HDR       ✗
BOKEH     ✗
BEAUTY    ✗
AUTO      ✗
```

Drawn from the cached `extensionsAvailability` map (already resolved by the time the user opens the sheet).

---

## 3. Camera2 Tab

### CameraCapabilities addition

```kotlin
data class CameraCapabilities(
    // ... existing fields ...
    val availableSceneModes: List<Int> = emptyList(),
)
```

Populated in `Camera2Characteristics.readAll()` from `CameraCharacteristics.CONTROL_AVAILABLE_SCENE_MODES`. No new camera open/close required.

### Controller changes

In `Camera2Controller.applySettings()`, when `settings.nightSceneMode = true`:

```kotlin
setCaptureRequestOption(CONTROL_MODE, CONTROL_MODE_USE_SCENE_MODE)
setCaptureRequestOption(CONTROL_SCENE_MODE, CONTROL_SCENE_MODE_NIGHT)
```

When false:

```kotlin
setCaptureRequestOption(CONTROL_MODE, CONTROL_MODE_AUTO)
// CONTROL_SCENE_MODE not set (defaults to DISABLED)
```

Note: under `USE_SCENE_MODE`, the HAL may silently ignore manual ISO/SS values. The pill lockout makes this explicit to the user.

### Fragment changes

NIGHT pill added conditionally in `setupPills()` only if `CONTROL_SCENE_MODE_NIGHT` (value 4) is in `currentCapabilities.availableSceneModes`. Same conditional pattern as OIS.

**Pill lockout:** identical to CameraX — when NIGHT is active, ISO/SS/WB/FOCUS/ZOOM go to `alpha = 0.4f` / non-clickable.

---

## 4. Future Work: Camera2 `CameraExtensionSession` (API 31+)

The native Camera2 equivalent of CameraX Extensions. Accessed via `CameraExtensionCharacteristics.getSupportedExtensions()` and a dedicated `CameraExtensionSession` (separate from the normal `CameraCaptureSession`). Supports the same five extension types. Not implemented here due to significantly higher integration cost — requires a parallel session-management path in `Camera2Controller`. Record for a future investigation branch.

---

## Files Affected

| File | Change |
|------|--------|
| `gradle/libs.versions.toml` | Add `androidx.camera.extensions` alias |
| `app/build.gradle.kts` | Add `camera-extensions` dependency |
| `model/CameraSettings.kt` | Add `nightMode`, `nightSceneMode` fields |
| `model/CameraCapabilities.kt` | Add `availableSceneModes` field |
| `camera2/Camera2Characteristics.kt` | Populate `availableSceneModes` |
| `camerax/CameraXController.kt` | Add `ExtensionsManager`, night bind path, `onExtensionsAvailability` callback |
| `camerax/CameraXFragment.kt` | Add NIGHT pill, lockout logic, capabilities sheet extension section |
| `camera2/Camera2Controller.kt` | Apply `USE_SCENE_MODE` / `SCENE_MODE_NIGHT` |
| `camera2/Camera2Fragment.kt` | Add NIGHT pill, lockout logic |
