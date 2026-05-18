package com.example.camerainvestigations.overlay

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
        // Corner brackets
        val bo = radius * 1.6f
        for ((sx, sy) in listOf(Pair(-1f, -1f), Pair(1f, -1f), Pair(-1f, 1f), Pair(1f, 1f))) {
            val bx = cx + sx * bo
            val by = cy + sy * bo
            canvas.drawLine(bx, by, bx + sx * bracket, by, paint)
            canvas.drawLine(bx, by, bx, by + sy * bracket, paint)
        }
    }
}
