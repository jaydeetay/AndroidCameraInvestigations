package com.example.superpowerscameraresearch.overlay

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

class SourceDetectionOverlay @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private var sources: List<DetectedSource> = emptyList()
    private var analysisWidth: Int = 640
    private var analysisHeight: Int = 360
    private var sensorOrientation: Int = 0

    private val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF4444")
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    fun setSources(
        sources: List<DetectedSource>,
        analysisWidth: Int = 640,
        analysisHeight: Int = 360,
        sensorOrientation: Int = 0
    ) {
        this.sources = sources
        this.analysisWidth = analysisWidth
        this.analysisHeight = analysisHeight
        this.sensorOrientation = sensorOrientation
        post { invalidate() }
    }

    fun clear() {
        sources = emptyList()
        post { invalidate() }
    }

    override fun onDraw(canvas: Canvas) {
        if (sources.isEmpty()) return

        circlePaint.strokeWidth = 1.5f * resources.displayMetrics.density

        val W = analysisWidth.toFloat()
        val H = analysisHeight.toFloat()
        val vw = width.toFloat()
        val vh = height.toFloat()

        for (src in sources) {
            // Map sensor-space (cx, cy) to view-space accounting for sensor rotation.
            // Sensor orientation is degrees CW needed to rotate sensor image to upright portrait.
            val (vx, vy, vr) = when (sensorOrientation) {
                90 -> {
                    // 90° CW: sensor landscape → portrait. (sx,sy) → ((1-sy/H)*vw, (sx/W)*vh)
                    val x = (1f - src.cy / H) * vw
                    val y = (src.cx / W) * vh
                    val r = src.radius * maxOf(vw / H, vh / W)
                    Triple(x, y, r)
                }
                270 -> {
                    // 270° CW (90° CCW): (sx,sy) → ((sy/H)*vw, (1-sx/W)*vh)
                    val x = (src.cy / H) * vw
                    val y = (1f - src.cx / W) * vh
                    val r = src.radius * maxOf(vw / H, vh / W)
                    Triple(x, y, r)
                }
                180 -> {
                    val x = (1f - src.cx / W) * vw
                    val y = (1f - src.cy / H) * vh
                    val r = src.radius * maxOf(vw / W, vh / H)
                    Triple(x, y, r)
                }
                else -> {
                    // 0°: no rotation
                    val x = (src.cx / W) * vw
                    val y = (src.cy / H) * vh
                    val r = src.radius * maxOf(vw / W, vh / H)
                    Triple(x, y, r)
                }
            }
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
                strokeWidth = out.width * 0.002f
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
