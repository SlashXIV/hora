package com.gabrielifrim.hora.feature.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.text.TextPaint
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.createBitmap
import com.gabrielifrim.hora.core.ui.R as UiR

/** Une case du widget Prières : nom, heure de début, et son état. */
internal data class PrayerCell(val name: String, val time: String, val current: Boolean, val past: Boolean)

/**
 * Les cinq cases dessinées d'un seul tenant, comme sur la planche : un lanceur ne sait ni
 * charger les polices de l'app ni teinter un simple filet. L'heure rapetisse plutôt que de
 * déborder de sa case (« 11:50 AM » à 200 %).
 */
internal fun prayerRow(context: Context, cells: List<PrayerCell>, widthPx: Int, ink: Int, ink2: Int, faded: Int, rule: Int, accent: Int): Bitmap {
    val d = context.resources.displayMetrics.density
    val scale = context.resources.configuration.fontScale
    val gap = 4 * d
    val cellW = (widthPx - gap * (cells.size - 1)) / cells.size
    val name = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = ResourcesCompat.getFont(context, UiR.font.atkinson_hyperlegible_next)
        fontVariationSettings = "'wght' 650"
        letterSpacing = 0.04f
        textSize = 10.5f * d * scale
        textAlign = Paint.Align.CENTER
    }
    val time = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = ResourcesCompat.getFont(context, UiR.font.spline_sans_mono)
        fontFeatureSettings = "tnum"
        textAlign = Paint.Align.CENTER
    }
    // Une seule taille pour les cinq heures : la plus longue décide, les chiffres restent alignés.
    val widest = cells.maxOf { it.time.length }.let { n -> cells.first { it.time.length == n }.time }
    var timeSize = 13.5f * d * scale
    time.textSize = timeSize
    while (time.measureText(widest) > cellW - 12 * d && timeSize > 7 * d) {
        timeSize -= 0.5f * d
        time.textSize = timeSize
    }
    val pad = 6 * d
    val nameH = name.fontMetrics.let { it.descent - it.ascent }
    val timeH = time.fontMetrics.let { it.descent - it.ascent }
    val height = (pad + nameH + 2 * d + timeH + pad).toInt()
    val bitmap = createBitmap(widthPx, height)
    val canvas = Canvas(bitmap)
    val border = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = d }
    cells.forEachIndexed { i, c ->
        val left = i * (cellW + gap)
        border.color = if (c.current) accent else rule
        canvas.drawRoundRect(RectF(left + d / 2, d / 2, left + cellW - d / 2, height - d / 2), 8 * d, 8 * d, border)
        val cx = left + cellW / 2
        name.color = if (c.past) faded else ink2
        canvas.drawText(c.name.uppercase(), cx, pad - name.fontMetrics.ascent, name)
        time.color = when {
            c.current -> accent
            c.past -> faded
            else -> ink
        }
        time.fontVariationSettings = if (c.current) "'wght' 600" else "'wght' 450"
        canvas.drawText(c.time, cx, pad + nameH + 2 * d - time.fontMetrics.ascent, time)
    }
    return bitmap
}
