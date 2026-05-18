package com.example.camerainvestigations.overlay

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
