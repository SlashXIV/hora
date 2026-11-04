package com.gabrielifrim.hora.feature.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.text.TextPaint
import android.text.TextUtils
import androidx.annotation.FontRes
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.createBitmap
import com.gabrielifrim.hora.core.ui.R as UiR
import kotlin.math.ceil

/**
 * Un texte dessiné en image dans la vraie police de l'app : un lanceur ne charge pas les
 * polices d'une application. La taille suit la police du téléphone (200 % compris) au moment
 * du dessin ; une ligne trop longue finit par « … ».
 */
internal class WidgetText(private val context: Context) {
    enum class Style(@FontRes val font: Int, val sp: Float, val variation: String, val features: String? = null) {
        POSTER(UiR.font.fraunces, 30f, "'wght' 560, 'opsz' 120"),
        HEADING(UiR.font.fraunces, 16f, "'wght' 600, 'opsz' 30"),
        TIME(UiR.font.spline_sans_mono, 11.5f, "'wght' 450", "tnum"),
    }

    fun draw(text: String, style: Style, color: Int, maxWidthPx: Int): Bitmap? {
        if (text.isEmpty()) return null
        val metrics = context.resources.displayMetrics
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = ResourcesCompat.getFont(context, style.font)
            fontVariationSettings = style.variation
            style.features?.let { fontFeatureSettings = it }
            textSize = style.sp * metrics.density * context.resources.configuration.fontScale
            this.color = color
        }
        val line = TextUtils.ellipsize(text, paint, maxWidthPx.toFloat(), TextUtils.TruncateAt.END).toString()
        val fm = paint.fontMetrics
        val width = ceil(paint.measureText(line)).toInt().coerceAtLeast(1)
        val height = ceil(fm.descent - fm.ascent).toInt().coerceAtLeast(1)
        return createBitmap(width, height).also { Canvas(it).drawText(line, 0f, -fm.ascent, paint) }
    }
}
