package com.example.superpowerscameraresearch.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class HistogramView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private data class HistogramState(
        val histogram: IntArray,
        val clipping: Boolean,
        val maxCount: Int
    )

    @Volatile
    private var state = HistogramState(IntArray(256), false, 1)

    private val barPaint = Paint().apply { color = Color.parseColor("#AAFFFFFF") }
    private val clipPaint = Paint().apply { color = Color.parseColor("#FFFF4444") }
    private val bgPaint   = Paint().apply { color = Color.parseColor("#99000000") }

    fun update(histogram: IntArray, isClipping: Boolean) {
        state = HistogramState(histogram, isClipping, histogram.max().coerceAtLeast(1))
        postInvalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val (histogram, clipping, maxCount) = state
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
