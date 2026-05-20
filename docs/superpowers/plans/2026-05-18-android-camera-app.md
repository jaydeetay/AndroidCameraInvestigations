# Android Camera Investigation App — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build an Android test app (API 28+, Kotlin) with Camera2 and CameraX tabs that expose full manual camera controls, a live histogram, RAW/DNG capture, and a camera selector — optimised for astrophotography investigation.

**Architecture:** `MainActivity` hosts a `ViewPager2` with two fragments (`Camera2Fragment`, `CameraXFragment`). Each fragment owns its camera lifecycle independently (open in `onResume`, close in `onPause`). Overlay views (`ReticleView`, `HistogramView`) are transparent `View`s layered over the preview. Pure logic (histogram computation) lives in `HistogramComputer` and is unit-tested independently of Android.

**Tech Stack:** Kotlin, Camera2 API (framework), CameraX 1.3.4, ViewPager2, Material Components, JUnit 4

---

## File Structure

```
AndroidCameraInvestigations/
├── .gitignore
├── settings.gradle.kts
├── build.gradle.kts
├── gradle/
│   ├── libs.versions.toml
│   └── wrapper/gradle-wrapper.properties
├── app/
│   ├── build.gradle.kts
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/com/example/camerainvestigations/
│       │   │   ├── MainActivity.kt
│       │   │   ├── model/
│       │   │   │   ├── CameraSettings.kt
│       │   │   │   └── CameraCapabilities.kt
│       │   │   ├── overlay/
│       │   │   │   ├── HistogramComputer.kt
│       │   │   │   ├── HistogramView.kt
│       │   │   │   └── ReticleView.kt
│       │   │   ├── camera2/
│       │   │   │   ├── Camera2Characteristics.kt
│       │   │   │   ├── Camera2Controller.kt
│       │   │   │   └── Camera2Fragment.kt
│       │   │   └── camerax/
│       │   │       ├── CameraXController.kt
│       │   │       └── CameraXFragment.kt
│       │   └── res/
│       │       ├── layout/
│       │       │   ├── activity_main.xml
│       │       │   ├── fragment_camera2.xml
│       │       │   ├── fragment_camerax.xml
│       │       │   ├── bottom_sheet_camera_selector.xml
│       │       │   └── bottom_sheet_capabilities.xml
│       │       ├── drawable/
│       │       │   ├── hud_label_bg.xml
│       │       │   └── capture_button_bg.xml
│       │       └── values/
│       │           ├── strings.xml
│       │           └── themes.xml
│       └── test/java/com/example/camerainvestigations/
│           ├── overlay/HistogramComputerTest.kt
│           └── model/CameraSettingsTest.kt
```

---

## Task 1: Gradle Project Scaffolding

**Files:**
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts`
- Create: `gradle/libs.versions.toml`
- Create: `gradle/wrapper/gradle-wrapper.properties`
- Create: `app/build.gradle.kts`
- Create: `.gitignore`

- [ ] **Step 1: Create `.gitignore`**

```
*.iml
.gradle
/local.properties
/.idea
.DS_Store
/build
/captures
.externalNativeBuild
.cxx
local.properties
.superpowers/
```

- [ ] **Step 2: Create `settings.gradle.kts`**

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "AndroidCameraInvestigations"
include(":app")
```

- [ ] **Step 3: Create `build.gradle.kts` (root)**

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
}
```

- [ ] **Step 4: Create `gradle/libs.versions.toml`**

```toml
[versions]
agp = "8.2.2"
kotlin = "1.9.22"
camerax = "1.3.4"
coroutines = "1.7.3"

[libraries]
androidx-camera-core      = { module = "androidx.camera:camera-core",      version.ref = "camerax" }
androidx-camera-camera2   = { module = "androidx.camera:camera-camera2",   version.ref = "camerax" }
androidx-camera-lifecycle = { module = "androidx.camera:camera-lifecycle",  version.ref = "camerax" }
androidx-camera-view      = { module = "androidx.camera:camera-view",       version.ref = "camerax" }
androidx-viewpager2       = { module = "androidx.viewpager2:viewpager2",    version = "1.0.0" }
material                  = { module = "com.google.android.material:material", version = "1.12.0" }
kotlinx-coroutines-android = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-android", version.ref = "coroutines" }
junit                     = { module = "junit:junit",                        version = "4.13.2" }
androidx-test-ext-junit   = { module = "androidx.test.ext:junit",           version = "1.1.5" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android      = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
```

- [ ] **Step 5: Create `gradle/wrapper/gradle-wrapper.properties`**

```properties
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-8.4-bin.zip
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
```

- [ ] **Step 6: Create `app/build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "superpowerscameraresearchearch"
    compileSdk = 34

    defaultConfig {
        applicationId = "superpowerscameraresearchearch"
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions { jvmTarget = "1.8" }
    buildFeatures { viewBinding = true }
}

dependencies {
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.viewpager2)
    implementation(libs.material)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
}
```

- [ ] **Step 7: Generate Gradle wrapper scripts**

If Android Studio is available: open the project and let it sync.

If using the command line (requires Gradle 8.4 installed):
```bash
gradle wrapper --gradle-version 8.4
```

Verify:
```bash
./gradlew tasks
```
Expected: task list printed without error.

- [ ] **Step 8: Commit**

```bash
git add settings.gradle.kts build.gradle.kts gradle/ app/build.gradle.kts .gitignore
git commit -m "feat: gradle project scaffolding"
```

---

## Task 2: App Shell — Manifest, Theme, Empty MainActivity

**Files:**
- Create: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/res/values/themes.xml`
- Create: `app/src/main/res/values/strings.xml`
- Create: `app/src/main/res/layout/activity_main.xml`
- Create: `app/src/main/java/com/example/camerainvestigations/MainActivity.kt`

- [ ] **Step 1: Create `AndroidManifest.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.CAMERA" />
    <uses-feature android:name="android.hardware.camera.any" android:required="true" />

    <application
        android:allowBackup="false"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:theme="@style/Theme.CameraInvestigations">
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:screenOrientation="portrait"
            android:windowSoftInputMode="adjustNothing">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>

</manifest>
```

- [ ] **Step 2: Create `res/values/themes.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="Theme.CameraInvestigations" parent="Theme.MaterialComponents.DayNight.NoActionBar">
        <item name="colorPrimary">#FF441111</item>
        <item name="colorPrimaryVariant">#FF220808</item>
        <item name="colorOnPrimary">#FFFFFFFF</item>
        <item name="android:windowBackground">@android:color/black</item>
        <item name="android:statusBarColor">@android:color/black</item>
        <item name="android:navigationBarColor">@android:color/black</item>
    </style>
</resources>
```

- [ ] **Step 3: Create `res/values/strings.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">Camera Investigations</string>
    <string name="tab_camera2">CAMERA2</string>
    <string name="tab_camerax">CAMERAX</string>
    <string name="permission_rationale">Camera permission is required to preview and capture images.</string>
    <string name="raw_unsupported">RAW unsupported</string>
</resources>
```

- [ ] **Step 4: Create `res/layout/activity_main.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:background="@android:color/black">

    <com.google.android.material.tabs.TabLayout
        android:id="@+id/tabLayout"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:background="@android:color/black"
        style="@style/Widget.MaterialComponents.TabLayout" />

    <androidx.viewpager2.widget.ViewPager2
        android:id="@+id/viewPager"
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1" />

</LinearLayout>
```

- [ ] **Step 5: Create `MainActivity.kt`**

```kotlin
package superpowerscameraresearch

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.viewpager2.widget.ViewPager2
import superpowerscameraresearch.camera2.Camera2Fragment
import superpowerscameraresearch.camerax.CameraXFragment
import superpowerscameraresearch.databinding.ActivityMainBinding
import com.google.android.material.tabs.TabLayoutMediator

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            Toast.makeText(this, getString(R.string.permission_rationale), Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
            return
        }

        setupViewPager()
    }

    private fun setupViewPager() {
        val fragments = listOf(Camera2Fragment(), CameraXFragment())
        val titles = listOf(getString(R.string.tab_camera2), getString(R.string.tab_camerax))

        binding.viewPager.adapter = object : androidx.fragment.app.FragmentStateAdapter(this) {
            override fun getItemCount() = fragments.size
            override fun createFragment(position: Int) = fragments[position]
        }
        binding.viewPager.offscreenPageLimit = ViewPager2.OFFSCREEN_PAGE_LIMIT_DEFAULT

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = titles[position]
        }.attach()
    }
}
```

- [ ] **Step 6: Verify the shell builds**

```bash
./gradlew :app:assembleDebug
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 7: Commit**

```bash
git add app/src/
git commit -m "feat: app shell with ViewPager2, dark theme, camera permission"
```

---

## Task 3: Data Models + Unit Tests

**Files:**
- Create: `app/src/main/java/com/example/camerainvestigations/model/CameraSettings.kt`
- Create: `app/src/main/java/com/example/camerainvestigations/model/CameraCapabilities.kt`
- Create: `app/src/test/java/com/example/camerainvestigations/model/CameraSettingsTest.kt`

- [ ] **Step 1: Write failing test**

Create `app/src/test/java/com/example/camerainvestigations/model/CameraSettingsTest.kt`:

```kotlin
package superpowerscameraresearch.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraSettingsTest {

    @Test
    fun `default settings use auto modes`() {
        val settings = CameraSettings()
        assertTrue(settings.isoAuto)
        assertTrue(settings.shutterAuto)
        assertTrue(settings.wbAuto)
        assertTrue(settings.focusAuto)
    }

    @Test
    fun `shutterNs converts to display string`() {
        assertEquals("1/30s", CameraSettings.shutterNsToDisplay(33_333_333L))
        assertEquals("1/1000s", CameraSettings.shutterNsToDisplay(1_000_000L))
        assertEquals("2s", CameraSettings.shutterNsToDisplay(2_000_000_000L))
        assertEquals("30s", CameraSettings.shutterNsToDisplay(30_000_000_000L))
    }

    @Test
    fun `focus display returns infinity for zero distance`() {
        assertEquals("∞", CameraSettings.focusDistanceToDisplay(0f))
    }

    @Test
    fun `focus display returns dioptre value for non-zero`() {
        assertEquals("0.50 D", CameraSettings.focusDistanceToDisplay(0.5f))
    }
}
```

- [ ] **Step 2: Run test to confirm it fails**

```bash
./gradlew :app:test --tests "superpowerscameraresearch.model.CameraSettingsTest"
```
Expected: FAIL — `CameraSettings` not found.

- [ ] **Step 3: Create `CameraSettings.kt`**

```kotlin
package superpowerscameraresearch.model

data class CameraSettings(
    val isoAuto: Boolean = true,
    val iso: Int = 800,
    val shutterAuto: Boolean = true,
    val shutterNs: Long = 33_333_333L,   // ~1/30s in nanoseconds
    val wbAuto: Boolean = true,
    val whiteBalanceK: Int = 4000,
    val focusAuto: Boolean = true,
    val focusDistance: Float = 0f,       // 0 = infinity (Camera2 dioptre units)
    val zoom: Float = 1.0f,
    val oisEnabled: Boolean = true
) {
    companion object {
        fun shutterNsToDisplay(ns: Long): String {
            val seconds = ns / 1_000_000_000.0
            return when {
                seconds >= 1.0 -> "${seconds.toLong()}s"
                else -> {
                    val denom = (1.0 / seconds).toLong()
                    "1/${denom}s"
                }
            }
        }

        fun focusDistanceToDisplay(dioptre: Float): String =
            if (dioptre == 0f) "∞" else "${"%.2f".format(dioptre)} D"
    }
}
```

- [ ] **Step 4: Create `CameraCapabilities.kt`**

```kotlin
package superpowerscameraresearch.model

import android.graphics.SizeF
import android.hardware.camera2.CameraCharacteristics
import android.util.Range

data class PhysicalCamera(
    val id: String,
    val focalLength: Float,
    val aperture: Float
)

data class CameraCapabilities(
    val cameraId: String,
    val facing: Int,                        // CameraCharacteristics.LENS_FACING_*
    val focalLengths: FloatArray,
    val apertures: FloatArray,
    val isoRange: Range<Int>?,
    val exposureTimeRange: Range<Long>?,    // nanoseconds
    val hardwareLevel: Int,                 // CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_*
    val supportsRaw: Boolean,
    val supportsOis: Boolean,
    val isLogicalCamera: Boolean,
    val physicalCameras: List<PhysicalCamera>,
    val sensorSizeM: SizeF?,               // physical sensor size in millimetres
    val zoomRatioRange: Range<Float>?      // null on API < 30
) {
    val hardwareLevelName: String get() = when (hardwareLevel) {
        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY  -> "LEGACY"
        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED -> "LIMITED"
        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL    -> "FULL"
        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3       -> "LEVEL_3"
        else -> "UNKNOWN"
    }

    val facingName: String get() = when (facing) {
        CameraCharacteristics.LENS_FACING_BACK  -> "BACK"
        CameraCharacteristics.LENS_FACING_FRONT -> "FRONT"
        else -> "EXTERNAL"
    }

    val primaryFocalLength: Float get() = focalLengths.firstOrNull() ?: 0f
    val primaryAperture: Float get() = apertures.firstOrNull() ?: 0f
    val hasVariableAperture: Boolean get() = apertures.size > 1
}
```

- [ ] **Step 5: Run tests — expect pass**

```bash
./gradlew :app:test --tests "superpowerscameraresearch.model.CameraSettingsTest"
```
Expected: PASS (4 tests)

- [ ] **Step 6: Commit**

```bash
git add app/src/
git commit -m "feat: CameraSettings and CameraCapabilities models with unit tests"
```

---

## Task 4: HistogramComputer + Unit Tests

**Files:**
- Create: `app/src/main/java/com/example/camerainvestigations/overlay/HistogramComputer.kt`
- Create: `app/src/test/java/com/example/camerainvestigations/overlay/HistogramComputerTest.kt`

- [ ] **Step 1: Write failing tests**

```kotlin
package superpowerscameraresearch.overlay

import org.junit.Assert.*
import org.junit.Test

class HistogramComputerTest {

    @Test
    fun `uniform grey image produces single-bin histogram`() {
        val pixels = ByteArray(400) { 128.toByte() }
        val histogram = HistogramComputer.compute(pixels, stride = 20, width = 20, height = 20)
        assertEquals(256, histogram.size)
        assertTrue("bin 128 must be non-zero", histogram[128] > 0)
        histogram.forEachIndexed { bin, count ->
            if (bin != 128) assertEquals("bin $bin should be zero", 0, count)
        }
    }

    @Test
    fun `all-black image fills bin zero`() {
        val pixels = ByteArray(100) { 0 }
        val histogram = HistogramComputer.compute(pixels, stride = 10, width = 10, height = 10)
        assertTrue(histogram[0] > 0)
        histogram.drop(1).forEach { assertEquals(0, it) }
    }

    @Test
    fun `isClipping returns true when top bin exceeds threshold`() {
        val histogram = IntArray(256).also { it[255] = 100 }
        assertTrue(HistogramComputer.isClipping(histogram, total = 100, threshold = 0.005f))
    }

    @Test
    fun `isClipping returns false when top bin is within threshold`() {
        val histogram = IntArray(256).also { it[255] = 1 }
        assertFalse(HistogramComputer.isClipping(histogram, total = 10_000, threshold = 0.005f))
    }

    @Test
    fun `sampling step skips pixels without crashing on large image`() {
        val pixels = ByteArray(1920 * 1080) { (it % 256).toByte() }
        val histogram = HistogramComputer.compute(pixels, stride = 1920, width = 1920, height = 1080)
        val total = histogram.sum()
        assertTrue("sampled pixel count should be > 0", total > 0)
    }
}
```

- [ ] **Step 2: Run to confirm failure**

```bash
./gradlew :app:test --tests "superpowerscameraresearch.overlay.HistogramComputerTest"
```
Expected: FAIL — `HistogramComputer` not found.

- [ ] **Step 3: Create `HistogramComputer.kt`**

```kotlin
package superpowerscameraresearch.overlay

object HistogramComputer {

    private const val SAMPLE_STEP = 4   // sample every 4th pixel in X and Y

    /**
     * Computes a 256-bucket luminance histogram from a YUV_420_888 Y plane.
     * @param yPlane  raw Y plane bytes (unsigned, 0–255)
     * @param stride  row stride in bytes (may be wider than width)
     * @param width   frame width in pixels
     * @param height  frame height in pixels
     * @return IntArray(256) of pixel counts
     */
    fun compute(yPlane: ByteArray, stride: Int, width: Int, height: Int): IntArray {
        val histogram = IntArray(256)
        var row = 0
        while (row < height) {
            var col = 0
            while (col < width) {
                val index = row * stride + col
                if (index < yPlane.size) {
                    histogram[yPlane[index].toInt() and 0xFF]++
                }
                col += SAMPLE_STEP
            }
            row += SAMPLE_STEP
        }
        return histogram
    }

    /**
     * Returns true if more than [threshold] fraction of sampled pixels are at maximum brightness.
     */
    fun isClipping(histogram: IntArray, total: Int, threshold: Float = 0.005f): Boolean {
        if (total == 0) return false
        return histogram[255].toFloat() / total > threshold
    }
}
```

- [ ] **Step 4: Run tests — expect pass**

```bash
./gradlew :app:test --tests "superpowerscameraresearch.overlay.HistogramComputerTest"
```
Expected: PASS (5 tests)

- [ ] **Step 5: Commit**

```bash
git add app/src/
git commit -m "feat: HistogramComputer with unit tests"
```

---

## Task 5: Overlay Views (ReticleView + HistogramView) + Drawables

**Files:**
- Create: `app/src/main/java/com/example/camerainvestigations/overlay/ReticleView.kt`
- Create: `app/src/main/java/com/example/camerainvestigations/overlay/HistogramView.kt`
- Create: `app/src/main/res/drawable/hud_label_bg.xml`
- Create: `app/src/main/res/drawable/capture_button_bg.xml`

- [ ] **Step 1: Create `ReticleView.kt`**

```kotlin
package superpowerscameraresearch.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class ReticleView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#CC441111")
        strokeWidth = 2f
        style = Paint.Style.STROKE
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val radius = minOf(width, height) * 0.08f
        val arm = radius * 0.7f
        val bracket = radius * 0.4f

        // Circle
        canvas.drawCircle(cx, cy, radius, paint)
        // Crosshair lines
        canvas.drawLine(cx, cy - arm, cx, cy + arm, paint)
        canvas.drawLine(cx - arm, cy, cx + arm, cy, paint)
        // Corner brackets (top-left)
        val bo = radius * 1.6f
        for ((sx, sy) in listOf(Pair(-1f, -1f), Pair(1f, -1f), Pair(-1f, 1f), Pair(1f, 1f))) {
            val bx = cx + sx * bo
            val by = cy + sy * bo
            canvas.drawLine(bx, by, bx + sx * bracket, by, paint)
            canvas.drawLine(bx, by, bx, by + sy * bracket, paint)
        }
    }
}
```

- [ ] **Step 2: Create `HistogramView.kt`**

```kotlin
package superpowerscameraresearch.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class HistogramView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private var histogram: IntArray = IntArray(256)
    private var clipping: Boolean = false
    private var maxCount: Int = 1

    private val barPaint = Paint().apply { color = Color.parseColor("#AAFFFFFF") }
    private val clipPaint = Paint().apply { color = Color.parseColor("#FFFF4444") }
    private val bgPaint   = Paint().apply { color = Color.parseColor("#99000000") }

    fun update(histogram: IntArray, isClipping: Boolean) {
        this.histogram = histogram
        this.clipping = isClipping
        this.maxCount = histogram.max().coerceAtLeast(1)
        postInvalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        canvas.drawRect(0f, 0f, w, h, bgPaint)

        val barW = w / 256f
        for (i in 0 until 256) {
            val barH = (histogram[i].toFloat() / maxCount) * h
            val paint = if (i == 255 && clipping) clipPaint else barPaint
            canvas.drawRect(i * barW, h - barH, (i + 1) * barW, h, paint)
        }
    }
}
```

- [ ] **Step 3: Create `res/drawable/hud_label_bg.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <solid android:color="#99000000" />
    <corners android:radius="3dp" />
</shape>
```

- [ ] **Step 4: Create `res/drawable/capture_button_bg.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="oval">
    <solid android:color="#44FFFFFF" />
    <stroke android:width="2dp" android:color="#99FFFFFF" />
</shape>
```

- [ ] **Step 5: Build to verify views compile**

```bash
./gradlew :app:assembleDebug
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: Commit**

```bash
git add app/src/
git commit -m "feat: ReticleView, HistogramView, HUD drawables"
```

---

## Task 6: Camera2 Characteristics Reader

**Files:**
- Create: `app/src/main/java/com/example/camerainvestigations/camera2/Camera2Characteristics.kt`

- [ ] **Step 1: Create `Camera2Characteristics.kt`**

```kotlin
package superpowerscameraresearch.camera2

import android.content.Context
import android.graphics.SizeF
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.os.Build
import android.util.Range
import superpowerscameraresearch.model.CameraCapabilities
import superpowerscameraresearch.model.PhysicalCamera

object Camera2Characteristics {

    fun readAll(context: Context): List<CameraCapabilities> {
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        return manager.cameraIdList.mapNotNull { id ->
            runCatching { read(manager, id) }.getOrNull()
        }
    }

    fun read(manager: CameraManager, cameraId: String): CameraCapabilities {
        val c = manager.getCameraCharacteristics(cameraId)

        val capabilities = c.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: intArrayOf()
        val isLogical = capabilities.contains(CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA)
        val supportsRaw = capabilities.contains(CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_RAW)

        val oisModes = c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION_MODES) ?: intArrayOf()
        val supportsOis = oisModes.contains(CameraMetadata.LENS_OPTICAL_STABILIZATION_MODE_ON)

        val physicalIds = if (isLogical) c.physicalCameraIds else emptySet()
        val physicalCameras = physicalIds.mapNotNull { pid ->
            runCatching {
                val pc = manager.getCameraCharacteristics(pid)
                PhysicalCamera(
                    id = pid,
                    focalLength = pc.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.firstOrNull() ?: 0f,
                    aperture = pc.get(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES)?.firstOrNull() ?: 0f
                )
            }.getOrNull()
        }

        val sensorSize = c.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
        val zoomRange: Range<Float>? = if (Build.VERSION.SDK_INT >= 30) {
            c.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE)
        } else null

        return CameraCapabilities(
            cameraId = cameraId,
            facing = c.get(CameraCharacteristics.LENS_FACING) ?: CameraCharacteristics.LENS_FACING_BACK,
            focalLengths = c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS) ?: floatArrayOf(),
            apertures = c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES) ?: floatArrayOf(),
            isoRange = c.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE),
            exposureTimeRange = c.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE),
            hardwareLevel = c.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)
                ?: CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY,
            supportsRaw = supportsRaw,
            supportsOis = supportsOis,
            isLogicalCamera = isLogical,
            physicalCameras = physicalCameras,
            sensorSizeM = sensorSize?.let { SizeF(it.width, it.height) },
            zoomRatioRange = zoomRange
        )
    }

    /**
     * Returns a human-readable dump of all CameraCharacteristics keys for display
     * in the capabilities bottom sheet.
     */
    fun dumpAll(manager: CameraManager, cameraId: String): List<Pair<String, String>> {
        val c = manager.getCameraCharacteristics(cameraId)
        val result = mutableListOf<Pair<String, String>>()

        fun add(key: String, value: Any?) {
            if (value != null) result += key to value.toString()
        }

        add("Camera ID", cameraId)
        add("Facing", when (c.get(CameraCharacteristics.LENS_FACING)) {
            CameraCharacteristics.LENS_FACING_BACK -> "BACK"
            CameraCharacteristics.LENS_FACING_FRONT -> "FRONT"
            else -> "EXTERNAL"
        })
        add("Hardware Level", when (c.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)) {
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY -> "LEGACY"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED -> "LIMITED"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL -> "FULL"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3 -> "LEVEL_3"
            else -> "UNKNOWN"
        })
        add("Focal Lengths (mm)", c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.contentToString())
        add("Apertures (f/)", c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES)?.contentToString())
        add("ISO Range", c.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE))
        add("Exposure Time Range (ns)", c.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE))
        add("Sensor Size (mm)", c.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE))
        add("Sensor Pixel Array", c.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE))
        add("RAW Support", c.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
            ?.contains(CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_RAW))
        add("OIS Modes", c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION_MODES)
            ?.map { if (it == 1) "OIS_ON" else "OIS_OFF" })
        add("Noise Reduction Modes", c.get(CameraCharacteristics.NOISE_REDUCTION_AVAILABLE_NOISE_REDUCTION_MODES)?.contentToString())
        add("Max Analog Sensitivity", c.get(CameraCharacteristics.SENSOR_MAX_ANALOG_SENSITIVITY))
        add("Flash Available", c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE))
        add("Logical Multi-Camera", c.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
            ?.contains(CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA))
        if (Build.VERSION.SDK_INT >= 30) {
            add("Zoom Ratio Range", c.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE))
        }
        add("Active Array Size", c.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE))
        add("AE Modes", c.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_MODES)?.contentToString())
        add("AWB Modes", c.get(CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES)?.contentToString())
        add("AF Modes", c.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES)?.contentToString())

        return result
    }
}
```

- [ ] **Step 2: Build to verify it compiles**

```bash
./gradlew :app:assembleDebug
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/superpowerscameraresearch/camera2/Camera2Characteristics.kt
git commit -m "feat: Camera2Characteristics reader and dump utility"
```

---

## Task 7: Camera2 Controller + Fragment

**Files:**
- Create: `app/src/main/java/com/example/camerainvestigations/camera2/Camera2Controller.kt`
- Create: `app/src/main/res/layout/fragment_camera2.xml`
- Create: `app/src/main/java/com/example/camerainvestigations/camera2/Camera2Fragment.kt`

- [ ] **Step 1: Create `fragment_camera2.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@android:color/black">

    <TextureView
        android:id="@+id/textureView"
        android:layout_width="match_parent"
        android:layout_height="match_parent" />

    <superpowerscameraresearch.overlay.ReticleView
        android:id="@+id/reticleView"
        android:layout_width="match_parent"
        android:layout_height="match_parent" />

    <superpowerscameraresearch.overlay.HistogramView
        android:id="@+id/histogramView"
        android:layout_width="88dp"
        android:layout_height="44dp"
        android:layout_gravity="bottom|start"
        android:layout_marginStart="8dp"
        android:layout_marginBottom="100dp" />

    <TextView
        android:id="@+id/tvFps"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_gravity="top|end"
        android:layout_marginTop="8dp"
        android:layout_marginEnd="8dp"
        android:background="@drawable/hud_label_bg"
        android:padding="4dp"
        android:textColor="#FF88EE88"
        android:textSize="11sp"
        android:fontFamily="monospace"
        android:text="-- fps" />

    <TextView
        android:id="@+id/tvCameraSelector"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_gravity="top|start"
        android:layout_marginTop="8dp"
        android:layout_marginStart="8dp"
        android:background="@drawable/hud_label_bg"
        android:padding="4dp"
        android:textColor="#FFCCAAAA"
        android:textSize="11sp"
        android:fontFamily="monospace"
        android:text="MAIN ▾" />

    <TextView
        android:id="@+id/tvHardwareLevel"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_gravity="top|center_horizontal"
        android:layout_marginTop="8dp"
        android:background="@drawable/hud_label_bg"
        android:padding="4dp"
        android:textColor="#FFAAAACC"
        android:textSize="11sp"
        android:fontFamily="monospace"
        android:text="" />

    <!-- Parameter pills container -->
    <LinearLayout
        android:id="@+id/pillsContainer"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_gravity="bottom"
        android:layout_marginBottom="56dp"
        android:orientation="horizontal"
        android:gravity="center"
        android:paddingHorizontal="4dp" />

    <!-- Active slider panel -->
    <LinearLayout
        android:id="@+id/sliderPanel"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_gravity="bottom"
        android:background="#EE000000"
        android:orientation="vertical"
        android:padding="12dp"
        android:visibility="gone">

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="horizontal"
            android:gravity="center_vertical"
            android:layout_marginBottom="8dp">

            <TextView
                android:id="@+id/tvParamName"
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                android:textColor="#FFE88888"
                android:textSize="11sp"
                android:fontFamily="monospace" />

            <TextView
                android:id="@+id/tvParamValue"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:textColor="#FFFFFFFF"
                android:textSize="20sp"
                android:fontFamily="monospace"
                android:layout_marginHorizontal="12dp" />

            <TextView
                android:id="@+id/tvParamRange"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:textColor="#FF666666"
                android:textSize="9sp"
                android:fontFamily="monospace" />
        </LinearLayout>

        <SeekBar
            android:id="@+id/seekBar"
            android:layout_width="match_parent"
            android:layout_height="wrap_content" />

    </LinearLayout>

    <!-- Capture button -->
    <FrameLayout
        android:id="@+id/captureButton"
        android:layout_width="48dp"
        android:layout_height="48dp"
        android:layout_gravity="bottom|center_horizontal"
        android:layout_marginBottom="4dp"
        android:background="@drawable/capture_button_bg" />

    <!-- Info / capabilities button -->
    <TextView
        android:id="@+id/btnInfo"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_gravity="bottom|end"
        android:layout_marginEnd="8dp"
        android:layout_marginBottom="8dp"
        android:text="ⓘ"
        android:textColor="#88FFFFFF"
        android:textSize="22sp" />

</FrameLayout>
```

- [ ] **Step 2: Create `Camera2Controller.kt`**

```kotlin
package superpowerscameraresearch.camera2

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.hardware.camera2.params.RggbChannelVector
import android.media.ImageReader
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import android.view.TextureView
import superpowerscameraresearch.model.CameraSettings
import superpowerscameraresearch.overlay.HistogramComputer

class Camera2Controller(
    private val context: Context,
    private val textureView: TextureView,
    private var cameraId: String,
    private val onSettingsConfirmed: (CameraSettings) -> Unit,
    private val onFpsUpdate: (Float) -> Unit,
    private val onHistogramReady: (IntArray, Boolean) -> Unit
) {
    private val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var previewRequest: CaptureRequest? = null

    private val cameraThread = HandlerThread("Camera2Worker").also { it.start() }
    private val cameraHandler = Handler(cameraThread.looper)

    private var histogramReader: ImageReader? = null
    private var histogramFrameCount = 0

    private var lastFrameTimestamp = 0L
    private var currentSettings = CameraSettings()

    val characteristics: CameraCharacteristics
        get() = manager.getCameraCharacteristics(cameraId)

    @SuppressLint("MissingPermission")
    fun openCamera() {
        val st = textureView.surfaceTexture ?: return
        st.setDefaultBufferSize(1920, 1080)

        histogramReader = ImageReader.newInstance(640, 360, ImageFormat.YUV_420_888, 2).apply {
            setOnImageAvailableListener({ reader ->
                if (++histogramFrameCount % 3 != 0) {
                    reader.acquireLatestImage()?.close()
                    return@setOnImageAvailableListener
                }
                val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
                val plane = image.planes[0]
                val histogram = HistogramComputer.compute(
                    plane.buffer.let { buf ->
                        ByteArray(buf.remaining()).also { buf.get(it) }
                    },
                    stride = plane.rowStride,
                    width = image.width,
                    height = image.height
                )
                val total = histogram.sum()
                val clipping = HistogramComputer.isClipping(histogram, total)
                onHistogramReady(histogram, clipping)
                image.close()
            }, cameraHandler)
        }

        manager.openCamera(cameraId, object : CameraDevice.StateCallback() {
            override fun onOpened(device: CameraDevice) {
                cameraDevice = device
                startPreviewSession(Surface(st))
            }
            override fun onDisconnected(device: CameraDevice) { device.close(); cameraDevice = null }
            override fun onError(device: CameraDevice, error: Int) { device.close(); cameraDevice = null }
        }, cameraHandler)
    }

    private fun startPreviewSession(previewSurface: Surface) {
        val histSurface = histogramReader!!.surface
        cameraDevice!!.createCaptureSession(
            listOf(previewSurface, histSurface),
            object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    captureSession = session
                    issueRepeatingRequest(previewSurface, histSurface)
                }
                override fun onConfigureFailed(session: CameraCaptureSession) {}
            },
            cameraHandler
        )
    }

    private fun issueRepeatingRequest(previewSurface: Surface, histSurface: Surface) {
        val s = currentSettings
        val builder = cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
            addTarget(previewSurface)
            addTarget(histSurface)
            applySettings(this, s)
        }
        previewRequest = builder.build()
        captureSession?.setRepeatingRequest(previewRequest!!, captureCallback, cameraHandler)
    }

    private fun applySettings(builder: CaptureRequest.Builder, s: CameraSettings) {
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

        if (s.focusAuto) {
            builder[CaptureRequest.CONTROL_AF_MODE] = CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
        } else {
            builder[CaptureRequest.CONTROL_AF_MODE] = CaptureRequest.CONTROL_AF_MODE_OFF
            builder[CaptureRequest.LENS_FOCUS_DISTANCE] = s.focusDistance
        }

        val caps = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION_MODES) ?: intArrayOf()
        if (caps.contains(CameraMetadata.LENS_OPTICAL_STABILIZATION_MODE_ON)) {
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
    }

    fun applySettings(settings: CameraSettings) {
        currentSettings = settings
        val st = textureView.surfaceTexture ?: return
        val previewSurface = Surface(st)
        val histSurface = histogramReader?.surface ?: return
        val builder = cameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW) ?: return
        builder.addTarget(previewSurface)
        builder.addTarget(histSurface)
        applySettings(builder, settings)
        captureSession?.setRepeatingRequest(builder.build(), captureCallback, cameraHandler)
    }

    private val captureCallback = object : CameraCaptureSession.CaptureCallback() {
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
        }
    }

    fun switchCamera(newCameraId: String) {
        closeCamera()
        cameraId = newCameraId
        openCamera()
    }

    fun closeCamera() {
        captureSession?.close(); captureSession = null
        cameraDevice?.close(); cameraDevice = null
        histogramReader?.close(); histogramReader = null
    }

    fun destroy() {
        closeCamera()
        cameraThread.quitSafely()
    }

    private fun colorTemperatureToGains(kelvin: Int): RggbChannelVector {
        val t = kelvin.coerceIn(2000, 8000).toFloat()
        val warm = (t - 2000f) / 6000f  // 0 = warmest, 1 = coolest
        val rGain = 2.4f - 1.6f * warm
        val bGain = 0.5f + 1.7f * warm
        return RggbChannelVector(rGain, 1.0f, 1.0f, bGain)
    }
}
```

- [ ] **Step 3: Create `Camera2Fragment.kt`**

```kotlin
package superpowerscameraresearch.camera2

import android.content.Context
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import superpowerscameraresearch.R
import superpowerscameraresearch.databinding.FragmentCamera2Binding
import superpowerscameraresearch.model.CameraCapabilities
import superpowerscameraresearch.model.CameraSettings
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

    private sealed class ActiveParam {
        object None : ActiveParam()
        object Iso : ActiveParam()
        object Shutter : ActiveParam()
        object WhiteBalance : ActiveParam()
        object Focus : ActiveParam()
        object Zoom : ActiveParam()
    }
    private var activeParam: ActiveParam = ActiveParam.None

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCamera2Binding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val manager = requireContext().getSystemService(Context.CAMERA_SERVICE) as CameraManager
        allCapabilities = Camera2Characteristics.readAll(requireContext())
        val defaultCam = allCapabilities.firstOrNull {
            it.facing == CameraCharacteristics.LENS_FACING_BACK
        } ?: allCapabilities.first()
        currentCapabilities = defaultCam

        controller = Camera2Controller(
            context = requireContext(),
            textureView = binding.textureView,
            cameraId = defaultCam.cameraId,
            onSettingsConfirmed = { confirmed ->
                activity?.runOnUiThread { updateHudValues(confirmed) }
            },
            onFpsUpdate = { fps ->
                activity?.runOnUiThread { binding.tvFps.text = "${"%.1f".format(fps)} fps" }
            },
            onHistogramReady = { hist, clipping ->
                activity?.runOnUiThread { binding.histogramView.update(hist, clipping) }
            }
        )

        setupPills()
        setupSlider()
        setupCameraSelector()
        setupCapture()
        setupInfo()
        updateHardwareLevelBadge()

        binding.textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) = controller.openCamera()
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

    private fun setupPills() {
        val params = mutableListOf(
            Triple("ISO", "#FFE88888") { showIsoSlider() },
            Triple("SS",  "#FF88E888") { showShutterSlider() },
            Triple("WB",  "#FF8888E8") { showWbSlider() },
            Triple("FOCUS","#FFEAA888") { showFocusSlider() },
            Triple("ZOOM","#FFAA88E8") { showZoomSlider() }
        )
        // OIS toggle only shown if device supports it
        if (currentCapabilities.supportsOis) {
            params += Triple("OIS", "#FF88E8E8") { toggleOis() }
        }
        params.forEach { (label, colorHex, action) ->
            val pill = layoutInflater.inflate(R.layout.pill_parameter,
                binding.pillsContainer, false) as TextView
            pill.text = label
            pill.setTextColor(android.graphics.Color.parseColor(colorHex))
            pill.tag = label
            pill.setOnClickListener { action() }
            binding.pillsContainer.addView(pill)
        }
    }

    private fun toggleOis() {
        settings = settings.copy(oisEnabled = !settings.oisEnabled)
        controller.applySettings(settings)
        // Update OIS pill text to show state
        binding.pillsContainer.findViewWithTag<TextView>("OIS")?.text =
            if (settings.oisEnabled) "OIS ON" else "OIS OFF"
    }

    private fun showIsoSlider() {
        activeParam = ActiveParam.Iso
        val range = currentCapabilities.isoRange ?: return
        binding.tvParamName.text = "ISO"
        binding.tvParamRange.text = "${range.lower} – ${range.upper}"
        binding.sliderPanel.visibility = View.VISIBLE
        binding.seekBar.max = 1000
        binding.seekBar.progress = ((settings.iso - range.lower).toFloat() /
                (range.upper - range.lower) * 1000).toInt()
        updateIsoDisplay()
        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                if (!fromUser) return
                val iso = range.lower + ((range.upper - range.lower).toLong() * p / 1000).toInt()
                settings = settings.copy(iso = iso, isoAuto = false)
                updateIsoDisplay()
                controller.applySettings(settings)
            }
            override fun onStartTrackingTouch(sb: SeekBar) = Unit
            override fun onStopTrackingTouch(sb: SeekBar) = Unit
        })
    }

    private fun updateIsoDisplay() { binding.tvParamValue.text = settings.iso.toString() }

    private fun showShutterSlider() {
        activeParam = ActiveParam.Shutter
        val range = currentCapabilities.exposureTimeRange ?: return
        binding.tvParamName.text = "SHUTTER"
        binding.tvParamRange.text = "${CameraSettings.shutterNsToDisplay(range.lower)} – ${CameraSettings.shutterNsToDisplay(range.upper)}"
        binding.sliderPanel.visibility = View.VISIBLE
        binding.seekBar.max = 1000
        val logMin = ln(range.lower.toDouble())
        val logMax = ln(range.upper.toDouble())
        val logVal = ln(settings.shutterNs.toDouble())
        binding.seekBar.progress = ((logVal - logMin) / (logMax - logMin) * 1000).toInt()
        updateShutterDisplay()
        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                if (!fromUser) return
                val ns = exp(logMin + (logMax - logMin) * p / 1000).toLong()
                settings = settings.copy(shutterNs = ns, shutterAuto = false)
                updateShutterDisplay()
                controller.applySettings(settings)
            }
            override fun onStartTrackingTouch(sb: SeekBar) = Unit
            override fun onStopTrackingTouch(sb: SeekBar) = Unit
        })
    }

    private fun updateShutterDisplay() {
        binding.tvParamValue.text = CameraSettings.shutterNsToDisplay(settings.shutterNs)
    }

    private fun showWbSlider() {
        activeParam = ActiveParam.WhiteBalance
        binding.tvParamName.text = "WHITE BALANCE"
        binding.tvParamRange.text = "2000K – 8000K"
        binding.sliderPanel.visibility = View.VISIBLE
        binding.seekBar.max = 1000
        binding.seekBar.progress = ((settings.whiteBalanceK - 2000).toFloat() / 6000 * 1000).toInt()
        updateWbDisplay()
        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                if (!fromUser) return
                val k = 2000 + (6000 * p / 1000)
                settings = settings.copy(whiteBalanceK = k, wbAuto = false)
                updateWbDisplay()
                controller.applySettings(settings)
            }
            override fun onStartTrackingTouch(sb: SeekBar) = Unit
            override fun onStopTrackingTouch(sb: SeekBar) = Unit
        })
    }

    private fun updateWbDisplay() { binding.tvParamValue.text = "${settings.whiteBalanceK}K" }

    private fun showFocusSlider() {
        activeParam = ActiveParam.Focus
        binding.tvParamName.text = "FOCUS DISTANCE"
        binding.tvParamRange.text = "∞ – 10D"
        binding.sliderPanel.visibility = View.VISIBLE
        binding.seekBar.max = 1000
        binding.seekBar.progress = (settings.focusDistance / 10f * 1000).toInt()
        updateFocusDisplay()
        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                if (!fromUser) return
                val d = p / 1000f * 10f
                settings = settings.copy(focusDistance = d, focusAuto = false)
                updateFocusDisplay()
                controller.applySettings(settings)
            }
            override fun onStartTrackingTouch(sb: SeekBar) = Unit
            override fun onStopTrackingTouch(sb: SeekBar) = Unit
        })
    }

    private fun updateFocusDisplay() {
        binding.tvParamValue.text = CameraSettings.focusDistanceToDisplay(settings.focusDistance)
    }

    private fun showZoomSlider() {
        activeParam = ActiveParam.Zoom
        val maxZoom = currentCapabilities.zoomRatioRange?.upper ?: 10f
        binding.tvParamName.text = "ZOOM"
        binding.tvParamRange.text = "1.0× – ${"%.1f".format(maxZoom)}×"
        binding.sliderPanel.visibility = View.VISIBLE
        binding.seekBar.max = 1000
        binding.seekBar.progress = ((settings.zoom - 1f) / (maxZoom - 1f) * 1000).toInt()
        updateZoomDisplay()
        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                if (!fromUser) return
                val z = 1f + (maxZoom - 1f) * p / 1000f
                settings = settings.copy(zoom = z)
                updateZoomDisplay()
                controller.applySettings(settings)
            }
            override fun onStartTrackingTouch(sb: SeekBar) = Unit
            override fun onStopTrackingTouch(sb: SeekBar) = Unit
        })
    }

    private fun updateZoomDisplay() { binding.tvParamValue.text = "${"%.1f".format(settings.zoom)}×" }

    private fun setupSlider() {
        binding.sliderPanel.setOnClickListener { /* absorb touches */ }
        binding.root.setOnClickListener {
            if (activeParam !is ActiveParam.None) {
                activeParam = ActiveParam.None
                binding.sliderPanel.visibility = View.GONE
            }
        }
    }

    private fun setupCameraSelector() {
        binding.tvCameraSelector.setOnClickListener {
            showCameraSelectorSheet()
        }
        updateCameraSelectorLabel()
    }

    private fun updateCameraSelectorLabel() {
        val fl = currentCapabilities.primaryFocalLength
        binding.tvCameraSelector.text = "${"%.0f".format(fl)}mm ▾"
    }

    private fun showCameraSelectorSheet() {
        val sheet = BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(R.layout.bottom_sheet_camera_selector, null)
        val container = view.findViewById<ViewGroup>(R.id.cameraListContainer)

        allCapabilities.forEach { cap ->
            val row = layoutInflater.inflate(R.layout.item_camera_info, container, false)
            row.findViewById<TextView>(R.id.tvCameraId).text =
                "${cap.facingName} — ${"%.0f".format(cap.primaryFocalLength)}mm  f/${"%.1f".format(cap.primaryAperture)}"
            row.findViewById<TextView>(R.id.tvCameraDetails).text =
                "${cap.hardwareLevelName}  RAW:${cap.supportsRaw}  OIS:${cap.supportsOis}"
            row.setOnClickListener {
                currentCapabilities = cap
                controller.switchCamera(cap.cameraId)
                updateCameraSelectorLabel()
                updateHardwareLevelBadge()
                sheet.dismiss()
            }
            container.addView(row)
        }

        sheet.setContentView(view)
        sheet.show()
    }

    private fun updateHardwareLevelBadge() {
        binding.tvHardwareLevel.text = currentCapabilities.hardwareLevelName
    }

    private fun updateHudValues(confirmed: CameraSettings) {
        // Pill labels already show current settings; confirmed values update the readback
    }

    private fun setupCapture() {
        if (!currentCapabilities.supportsRaw) {
            binding.captureButton.alpha = 0.4f
            binding.captureButton.isEnabled = false
        }
        binding.captureButton.setOnClickListener {
            if (currentCapabilities.supportsRaw) {
                controller.captureRaw(requireContext())
            }
        }
    }

    private fun setupInfo() {
        binding.btnInfo.setOnClickListener {
            showCapabilitiesSheet()
        }
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
```

- [ ] **Step 4: Build to verify it compiles**

```bash
./gradlew :app:assembleDebug
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add app/src/
git commit -m "feat: Camera2Controller and Camera2Fragment with HUD controls"
```

---

## Task 8: Camera2 RAW/DNG Capture

**Files:**
- Modify: `app/src/main/java/com/example/camerainvestigations/camera2/Camera2Controller.kt` (add `captureRaw`)

- [ ] **Step 1: Add RAW ImageReader and `captureRaw` to `Camera2Controller`**

In `Camera2Controller`, add these fields at the top of the class (after `histogramReader`):

```kotlin
private var rawReader: ImageReader? = null
private var pendingCaptureResult: TotalCaptureResult? = null
private var pendingRawImage: android.media.Image? = null
private val rawLock = Object()
```

Replace `startPreviewSession` to also include the RAW surface when supported:

```kotlin
private fun startPreviewSession(previewSurface: Surface) {
    val caps = characteristics.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: intArrayOf()
    val supportsRaw = caps.contains(CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_RAW)

    val surfaces = mutableListOf(previewSurface, histogramReader!!.surface)

    if (supportsRaw) {
        val size = characteristics.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)!!
        rawReader = ImageReader.newInstance(size.width, size.height, ImageFormat.RAW_SENSOR, 2).apply {
            setOnImageAvailableListener({ reader ->
                val image = reader.acquireNextImage()
                synchronized(rawLock) {
                    pendingRawImage = image
                    tryWriteDng()
                }
            }, cameraHandler)
        }
        surfaces += rawReader!!.surface
    }

    cameraDevice!!.createCaptureSession(
        surfaces,
        object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(session: CameraCaptureSession) {
                captureSession = session
                issueRepeatingRequest(previewSurface, histogramReader!!.surface)
            }
            override fun onConfigureFailed(session: CameraCaptureSession) {}
        },
        cameraHandler
    )
}
```

Add `captureRaw` method and `tryWriteDng` helper:

```kotlin
fun captureRaw(context: Context) {
    val rawSurface = rawReader?.surface ?: return
    val st = textureView.surfaceTexture ?: return
    val previewSurface = Surface(st)

    val builder = cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
        addTarget(previewSurface)
        addTarget(rawSurface)
        applySettings(this, currentSettings)
        this[CaptureRequest.CONTROL_CAPTURE_INTENT] = CaptureRequest.CONTROL_CAPTURE_INTENT_STILL_CAPTURE
    }

    captureSession?.capture(builder.build(), object : CameraCaptureSession.CaptureCallback() {
        override fun onCaptureCompleted(
            session: CameraCaptureSession,
            request: CaptureRequest,
            result: TotalCaptureResult
        ) {
            synchronized(rawLock) {
                pendingCaptureResult = result
                pendingRawImage?.let { tryWriteDng(context) }
            }
        }
    }, cameraHandler)
}

private fun tryWriteDng(ctx: Context) {
    val image = pendingRawImage ?: return
    val result = pendingCaptureResult ?: return
    val context = ctx ?: return
    pendingRawImage = null
    pendingCaptureResult = null

    runCatching {
        val dng = android.hardware.camera2.DngCreator(characteristics, result)
        val values = android.content.ContentValues().apply {
            put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME,
                "IMG_${System.currentTimeMillis()}.dng")
            put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "image/x-adobe-dng")
            put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, "DCIM/CameraInvestigations")
        }
        val uri = context.contentResolver.insert(
            android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)!!
        context.contentResolver.openOutputStream(uri)!!.use { stream ->
            dng.writeImage(stream, image)
        }
        dng.close()
    }
    image.close()
}
```

- [ ] **Step 2: Build to verify it compiles**

```bash
./gradlew :app:assembleDebug
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/superpowerscameraresearch/camera2/Camera2Controller.kt
git commit -m "feat: Camera2 RAW/DNG capture via DngCreator + MediaStore"
```

---

## Task 9: CameraX Implementation

**Files:**
- Create: `app/src/main/java/com/example/camerainvestigations/camerax/CameraXController.kt`
- Create: `app/src/main/res/layout/fragment_camerax.xml`
- Create: `app/src/main/java/com/example/camerainvestigations/camerax/CameraXFragment.kt`

- [ ] **Step 1: Create `fragment_camerax.xml`**

Same as `fragment_camera2.xml` but replace `TextureView` with `PreviewView`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@android:color/black">

    <androidx.camera.view.PreviewView
        android:id="@+id/previewView"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        app:scaleType="fillCenter"
        xmlns:app="http://schemas.android.com/apk/res-auto" />

    <superpowerscameraresearch.overlay.ReticleView
        android:id="@+id/reticleView"
        android:layout_width="match_parent"
        android:layout_height="match_parent" />

    <superpowerscameraresearch.overlay.HistogramView
        android:id="@+id/histogramView"
        android:layout_width="88dp"
        android:layout_height="44dp"
        android:layout_gravity="bottom|start"
        android:layout_marginStart="8dp"
        android:layout_marginBottom="100dp" />

    <TextView
        android:id="@+id/tvFps"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_gravity="top|end"
        android:layout_marginTop="8dp"
        android:layout_marginEnd="8dp"
        android:background="@drawable/hud_label_bg"
        android:padding="4dp"
        android:textColor="#FF88EE88"
        android:textSize="11sp"
        android:fontFamily="monospace"
        android:text="-- fps" />

    <TextView
        android:id="@+id/tvCameraSelector"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_gravity="top|start"
        android:layout_marginTop="8dp"
        android:layout_marginStart="8dp"
        android:background="@drawable/hud_label_bg"
        android:padding="4dp"
        android:textColor="#FFCCAAAA"
        android:textSize="11sp"
        android:fontFamily="monospace"
        android:text="MAIN ▾" />

    <TextView
        android:id="@+id/tvHardwareLevel"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_gravity="top|center_horizontal"
        android:layout_marginTop="8dp"
        android:background="@drawable/hud_label_bg"
        android:padding="4dp"
        android:textColor="#FFAAAAEE"
        android:textSize="11sp"
        android:fontFamily="monospace"
        android:text="" />

    <LinearLayout
        android:id="@+id/pillsContainer"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_gravity="bottom"
        android:layout_marginBottom="56dp"
        android:orientation="horizontal"
        android:gravity="center"
        android:paddingHorizontal="4dp" />

    <LinearLayout
        android:id="@+id/sliderPanel"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_gravity="bottom"
        android:background="#EE000000"
        android:orientation="vertical"
        android:padding="12dp"
        android:visibility="gone">

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="horizontal"
            android:gravity="center_vertical"
            android:layout_marginBottom="8dp">

            <TextView
                android:id="@+id/tvParamName"
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                android:textColor="#FFE88888"
                android:textSize="11sp"
                android:fontFamily="monospace" />

            <TextView
                android:id="@+id/tvParamValue"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:textColor="#FFFFFFFF"
                android:textSize="20sp"
                android:fontFamily="monospace"
                android:layout_marginHorizontal="12dp" />

            <TextView
                android:id="@+id/tvParamRange"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:textColor="#FF666666"
                android:textSize="9sp"
                android:fontFamily="monospace" />
        </LinearLayout>

        <SeekBar
            android:id="@+id/seekBar"
            android:layout_width="match_parent"
            android:layout_height="wrap_content" />

    </LinearLayout>

    <FrameLayout
        android:id="@+id/captureButton"
        android:layout_width="48dp"
        android:layout_height="48dp"
        android:layout_gravity="bottom|center_horizontal"
        android:layout_marginBottom="4dp"
        android:background="@drawable/capture_button_bg" />

    <TextView
        android:id="@+id/btnInfo"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_gravity="bottom|end"
        android:layout_marginEnd="8dp"
        android:layout_marginBottom="8dp"
        android:text="ⓘ"
        android:textColor="#88FFFFFF"
        android:textSize="22sp" />

</FrameLayout>
```

- [ ] **Step 2: Create `CameraXController.kt`**

```kotlin
package superpowerscameraresearch.camerax

import android.content.ContentValues
import android.content.Context
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.RggbChannelVector
import android.os.Build
import android.provider.MediaStore
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import superpowerscameraresearch.camera2.Camera2Characteristics
import superpowerscameraresearch.model.CameraCapabilities
import superpowerscameraresearch.model.CameraSettings
import superpowerscameraresearch.overlay.HistogramComputer
import java.util.concurrent.Executors

class CameraXController(
    private val context: Context,
    private val previewView: PreviewView,
    private val lifecycleOwner: LifecycleOwner,
    private val onFpsUpdate: (Float) -> Unit,
    private val onHistogramReady: (IntArray, Boolean) -> Unit
) {
    private var provider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null
    private var currentCameraId: String = ""
    private var currentSettings = CameraSettings()
    private val analysisExecutor = Executors.newSingleThreadExecutor()
    private var lastTimestamp = 0L

    fun start(cameraId: String) {
        currentCameraId = cameraId
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            provider = future.get()
            bindUseCases()
        }, ContextCompat.getMainExecutor(context))
    }

    private fun bindUseCases() {
        val prov = provider ?: return

        val previewBuilder = Preview.Builder()
        val analysisBuilder = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
        val captureBuilder = ImageCapture.Builder()
            .setOutputFormat(ImageCapture.OUTPUT_FORMAT_RAW)

        applyCamera2Interop(previewBuilder, analysisBuilder, captureBuilder, currentSettings)

        val preview = previewBuilder.build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }

        val analysis = analysisBuilder.build().also { ia ->
            var frameCount = 0
            ia.setAnalyzer(analysisExecutor) { imageProxy ->
                if (++frameCount % 3 == 0) {
                    val ts = imageProxy.imageInfo.timestamp
                    if (lastTimestamp != 0L) {
                        val fps = 1_000_000_000f / (ts - lastTimestamp)
                        onFpsUpdate(fps)
                    }
                    lastTimestamp = ts

                    val plane = imageProxy.planes[0]
                    val histogram = HistogramComputer.compute(
                        plane.buffer.let { buf -> ByteArray(buf.remaining()).also { buf.get(it) } },
                        stride = plane.rowStride,
                        width = imageProxy.width,
                        height = imageProxy.height
                    )
                    onHistogramReady(histogram, HistogramComputer.isClipping(histogram, histogram.sum()))
                }
                imageProxy.close()
            }
        }

        imageCapture = captureBuilder.build()

        val selector = CameraSelector.Builder()
            .addCameraFilter { cameras ->
                cameras.filter { Camera2CameraInfo.from(it).cameraId == currentCameraId }
                    .ifEmpty { cameras }
            }.build()

        prov.unbindAll()
        prov.bindToLifecycle(lifecycleOwner, selector, preview, analysis, imageCapture)
    }

    private fun applyCamera2Interop(
        previewBuilder: Preview.Builder,
        analysisBuilder: ImageAnalysis.Builder,
        captureBuilder: ImageCapture.Builder,
        s: CameraSettings
    ) {
        listOf(
            Camera2Interop.Extender(previewBuilder),
            Camera2Interop.Extender(analysisBuilder),
            Camera2Interop.Extender(captureBuilder)
        ).forEach { ext ->
            if (s.isoAuto && s.shutterAuto) {
                ext.setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
            } else {
                ext.setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
                if (!s.isoAuto) ext.setCaptureRequestOption(CaptureRequest.SENSOR_SENSITIVITY, s.iso)
                if (!s.shutterAuto) ext.setCaptureRequestOption(CaptureRequest.SENSOR_EXPOSURE_TIME, s.shutterNs)
            }

            if (s.wbAuto) {
                ext.setCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO)
            } else {
                ext.setCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_OFF)
                ext.setCaptureRequestOption(CaptureRequest.COLOR_CORRECTION_MODE, CaptureRequest.COLOR_CORRECTION_MODE_TRANSFORM_MATRIX)
                ext.setCaptureRequestOption(CaptureRequest.COLOR_CORRECTION_GAINS, colorTemperatureToGains(s.whiteBalanceK))
            }

            if (s.focusAuto) {
                ext.setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
            } else {
                ext.setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)
                ext.setCaptureRequestOption(CaptureRequest.LENS_FOCUS_DISTANCE, s.focusDistance)
            }

            if (Build.VERSION.SDK_INT >= 30) {
                ext.setCaptureRequestOption(CaptureRequest.CONTROL_ZOOM_RATIO, s.zoom)
            }
        }
    }

    fun applySettings(settings: CameraSettings) {
        currentSettings = settings
        bindUseCases()  // rebind with new Camera2Interop settings
    }

    fun switchCamera(cameraId: String) {
        currentCameraId = cameraId
        bindUseCases()
    }

    fun captureRaw() {
        val capture = imageCapture ?: return
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "IMG_${System.currentTimeMillis()}.dng")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/x-adobe-dng")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "DCIM/CameraInvestigations")
        }
        val options = ImageCapture.OutputFileOptions.Builder(
            context.contentResolver,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            values
        ).build()
        capture.takePicture(options, ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {}
                override fun onError(exc: ImageCaptureException) {}
            })
    }

    fun stop() {
        provider?.unbindAll()
        analysisExecutor.shutdown()
    }

    private fun colorTemperatureToGains(kelvin: Int): RggbChannelVector {
        val t = kelvin.coerceIn(2000, 8000).toFloat()
        val warm = (t - 2000f) / 6000f
        return RggbChannelVector(2.4f - 1.6f * warm, 1.0f, 1.0f, 0.5f + 1.7f * warm)
    }
}
```

- [ ] **Step 3: Create `CameraXFragment.kt`**

`CameraXFragment` mirrors `Camera2Fragment` but delegates to `CameraXController`. Replace controller calls and the `textureView` with `previewView`.

```kotlin
package superpowerscameraresearch.camerax

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.fragment.app.Fragment
import superpowerscameraresearch.camera2.Camera2Characteristics
import superpowerscameraresearch.databinding.FragmentCameraxBinding
import superpowerscameraresearch.model.CameraCapabilities
import superpowerscameraresearch.model.CameraSettings
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
                activity?.runOnUiThread { binding.tvFps.text = "${"%.1f".format(fps)} fps" }
            },
            onHistogramReady = { hist, clipping ->
                activity?.runOnUiThread { binding.histogramView.update(hist, clipping) }
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

    override fun onDestroyView() {
        super.onDestroyView()
        controller.stop()
        _binding = null
    }

    // setupPills, showIsoSlider, showShutterSlider, showWbSlider, showFocusSlider, showZoomSlider,
    // setupSlider, setupCameraSelector, setupCapture, setupInfo, showCameraSelectorSheet,
    // showCapabilitiesSheet, updateHardwareLevelBadge — all identical to Camera2Fragment
    // except controller calls use `controller.applySettings(settings)` and `controller.captureRaw()`

    private fun setupPills() {
        val params = listOf(
            Triple("ISO", "#FFE88888") { showIsoSlider() },
            Triple("SS",  "#FF88E888") { showShutterSlider() },
            Triple("WB",  "#FF8888E8") { showWbSlider() },
            Triple("FOCUS","#FFEAA888") { showFocusSlider() },
            Triple("ZOOM","#FFAA88E8") { showZoomSlider() }
        )
        params.forEach { (label, colorHex, action) ->
            val pill = TextView(requireContext()).apply {
                text = label
                setTextColor(android.graphics.Color.parseColor(colorHex))
                background = requireContext().getDrawable(superpowerscameraresearch.R.drawable.hud_label_bg)
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
                settings = settings.copy(focusDistance = d, focusAuto = false)
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
        val view = layoutInflater.inflate(superpowerscameraresearch.R.layout.bottom_sheet_camera_selector, null)
        val container = view.findViewById<ViewGroup>(superpowerscameraresearch.R.id.cameraListContainer)

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
        if (!currentCapabilities.supportsRaw) {
            binding.captureButton.alpha = 0.4f
            binding.captureButton.isEnabled = false
        }
        binding.captureButton.setOnClickListener {
            if (currentCapabilities.supportsRaw) controller.captureRaw()
        }
    }

    private fun setupInfo() {
        binding.btnInfo.setOnClickListener { showCapabilitiesSheet() }
    }

    private fun showCapabilitiesSheet() {
        val manager = requireContext().getSystemService(Context.CAMERA_SERVICE) as android.hardware.camera2.CameraManager
        val entries = Camera2Characteristics.dumpAll(manager, currentCapabilities.cameraId)
        val sheet = BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(superpowerscameraresearch.R.layout.bottom_sheet_capabilities, null)
        val container = view.findViewById<ViewGroup>(superpowerscameraresearch.R.id.capabilitiesContainer)
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
```

- [ ] **Step 4: Build to verify it compiles**

```bash
./gradlew :app:assembleDebug
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add app/src/
git commit -m "feat: CameraXController and CameraXFragment with Camera2Interop"
```

---

## Task 10: Remaining Layout Files

**Files:**
- Create: `app/src/main/res/layout/bottom_sheet_camera_selector.xml`
- Create: `app/src/main/res/layout/bottom_sheet_capabilities.xml`
- Create: `app/src/main/res/layout/item_camera_info.xml`
- Create: `app/src/main/res/layout/pill_parameter.xml`

- [ ] **Step 1: Create `bottom_sheet_camera_selector.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="vertical"
    android:background="#FF111111">

    <TextView
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="SELECT CAMERA"
        android:textColor="#FF888888"
        android:textSize="11sp"
        android:fontFamily="monospace"
        android:padding="16dp" />

    <LinearLayout
        android:id="@+id/cameraListContainer"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical" />

</LinearLayout>
```

- [ ] **Step 2: Create `bottom_sheet_capabilities.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<ScrollView xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:background="#FF111111">

    <LinearLayout
        android:id="@+id/capabilitiesContainer"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:padding="16dp" />

</ScrollView>
```

- [ ] **Step 3: Create `item_camera_info.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="vertical"
    android:padding="16dp">

    <TextView
        android:id="@+id/tvCameraId"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:textColor="#FFDDDDDD"
        android:textSize="13sp"
        android:fontFamily="monospace" />

    <TextView
        android:id="@+id/tvCameraDetails"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:textColor="#FF888888"
        android:textSize="11sp"
        android:fontFamily="monospace"
        android:layout_marginTop="2dp" />

</LinearLayout>
```

- [ ] **Step 4: Create `pill_parameter.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<TextView xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:background="@drawable/hud_label_bg"
    android:paddingHorizontal="10dp"
    android:paddingVertical="6dp"
    android:layout_marginEnd="4dp"
    android:textSize="11sp"
    android:fontFamily="monospace" />
```

- [ ] **Step 5: Final build**

```bash
./gradlew :app:assembleDebug
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: Run unit tests**

```bash
./gradlew :app:test
```
Expected: All tests PASS

- [ ] **Step 7: Commit**

```bash
git add app/src/
git commit -m "feat: layout files for bottom sheets, camera selector, parameter pills"
```

---

## Manual Testing Checklist

Deploy to a physical Android device (API 28+). Emulators have limited Camera2 support.

```bash
./gradlew :app:installDebug
```

- [ ] Camera permission prompt appears on first launch
- [ ] Camera2 tab shows live preview
- [ ] CameraX tab shows live preview
- [ ] Switching tabs closes one camera and opens the other (no "camera in use" error)
- [ ] Histogram updates in real time
- [ ] Tapping ISO pill shows slider; dragging changes the preview visibly
- [ ] Tapping SHUTTER pill; setting to 1s or longer shows motion blur
- [ ] Tapping FOCUS; sliding to ∞ (0.0) sharpens distant subjects
- [ ] Camera selector sheet lists all cameras on the device
- [ ] ⓘ button opens capabilities sheet with key/value list
- [ ] Capture button saves a .dng file to DCIM/CameraInvestigations
- [ ] On a camera without RAW support, capture button is dimmed and inactive
- [ ] Hardware level badge shows LEGACY/LIMITED/FULL correctly
- [ ] Night test: set ISO 3200, shutter 2s, focus ∞ — stars visible in preview?
- [ ] Histogram clipping warning (right edge red) appears when pointing at a bright light

---

## Known Device Variability

Note these during testing and record which phone you're using:

| Capability | Notes |
|---|---|
| Hardware level | LEGACY devices may silently ignore manual ISO/shutter settings |
| RAW support | Ultrawide / front cameras often don't support it |
| Max shutter speed | Spec says up to 30s but some devices cap lower |
| OIS availability | Toggle won't appear on devices without OIS |
| Zoom API | CONTROL_ZOOM_RATIO requires API 30+; API 28-29 falls back to crop region |
| Physical camera IDs | Only exposed on logical multi-camera devices (most modern flagships) |
