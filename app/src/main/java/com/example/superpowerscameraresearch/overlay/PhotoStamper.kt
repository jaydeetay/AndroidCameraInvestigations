package com.example.superpowerscameraresearch.overlay

import android.graphics.*
import com.example.superpowerscameraresearch.model.CameraSettings
import java.text.SimpleDateFormat
import java.util.*

object PhotoStamper {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    /**
     * Returns a new Bitmap with two lines of settings text burned into the bottom.
     * Does not mutate the input bitmap.
     */
    fun stamp(
        source: Bitmap,
        settings: CameraSettings,
        aperture: Float?,
        focalLengthMm: Float?,
        stack: String,
        timestampMs: Long = System.currentTimeMillis()
    ): Bitmap {
        val out = source.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(out)
        val (line1, line2) = buildLines(settings, aperture, focalLengthMm, settings.zoom, timestampMs, stack)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = out.height * 0.013f
            typeface = Typeface.MONOSPACE
        }

        val lineHeight = textPaint.textSize * 1.4f
        val stripHeight = lineHeight * 2 + textPaint.textSize * 0.6f
        val stripTop = out.height - stripHeight

        val gradientPaint = Paint().apply {
            shader = LinearGradient(
                0f, stripTop, 0f, out.height.toFloat(),
                intArrayOf(Color.TRANSPARENT, 0xCC000000.toInt()),
                floatArrayOf(0f, 0.4f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, stripTop, out.width.toFloat(), out.height.toFloat(), gradientPaint)

        val xPad = out.width * 0.01f
        canvas.drawText(line1, xPad, out.height - lineHeight - textPaint.textSize * 0.3f, textPaint)
        canvas.drawText(line2, xPad, out.height - textPaint.textSize * 0.3f, textPaint)

        return out
    }

    /** Exposed for unit testing — builds the two stamp lines without touching Bitmap APIs. */
    internal fun buildLines(
        s: CameraSettings,
        aperture: Float?,
        focalLengthMm: Float?,
        zoom: Float,
        timestampMs: Long,
        stack: String
    ): Pair<String, String> {
        val isoStr = if (s.isoAuto) "ISO AUTO" else "ISO ${s.iso}"
        val ssStr = if (s.shutterAuto) "SS AUTO" else CameraSettings.shutterNsToDisplay(s.shutterNs)
        val wbStr = if (s.wbAuto) "WB AUTO" else "WB ${s.whiteBalanceK}K"
        val nrStr = when (s.noiseReduction) {
            0 -> "NR OFF"
            1 -> "NR FAST"
            else -> "NR HQ"
        }
        val detectStr = if (s.sourceDetectionSensitivity == 0) "DETECT OFF"
                        else "DETECT ${s.sourceDetectionSensitivity}%"

        val parts = mutableListOf(isoStr, ssStr)
        if (aperture != null) parts += "f/${"%.1f".format(aperture)}"
        if (focalLengthMm != null) parts += "${"%.0f".format(focalLengthMm)}mm"
        parts += "${"%.1f".format(zoom)}×"
        parts += wbStr
        parts += nrStr
        parts += detectStr

        val line1 = parts.joinToString(" · ")
        val line2 = "${dateFormat.format(Date(timestampMs))} · $stack"
        return Pair(line1, line2)
    }
}
