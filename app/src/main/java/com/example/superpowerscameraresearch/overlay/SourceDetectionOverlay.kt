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

    private val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF4444")
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    fun setSources(sources: List<DetectedSource>, analysisWidth: Int = 640, analysisHeight: Int = 360) {
        this.sources = sources
        this.analysisWidth = analysisWidth
        this.analysisHeight = analysisHeight
        post { invalidate() }
    }

    fun clear() {
        sources = emptyList()
        post { invalidate() }
    }

    override fun onDraw(canvas: Canvas) {
        if (sources.isEmpty()) return

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
