package com.lens035.app.media

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Rasterises the "'98 11 04" style date of point-and-shoot cameras for the date effect. */
object DateStamp {
    const val WIDTH = 500
    const val HEIGHT = 100

    fun text(epochMillis: Long?): String =
        SimpleDateFormat("''yy MM dd", Locale.US).format(Date(epochMillis ?: System.currentTimeMillis()))

    /** White glyphs on a transparent background; the shader colours and glows them. */
    fun bitmap(text: String): Bitmap {
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textSize = 72f
            letterSpacing = 0.06f
        }
        val width = paint.measureText(text)
        val scale = if (width > WIDTH - 16) (WIDTH - 16) / width else 1f
        paint.textSize = 72f * scale
        val metrics = paint.fontMetrics
        val baseline = HEIGHT / 2f - (metrics.ascent + metrics.descent) / 2f
        canvas.drawText(text, 8f, baseline, paint)
        return bitmap
    }
}
