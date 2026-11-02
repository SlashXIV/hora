package com.gabrielifrim.hora.core.ui.component

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Icônes de Hora, au trait sur une grille de 24. Elles remplacent
 * `material-icons-extended` : quelques icônes dessinées ici plutôt qu'une
 * bibliothèque au style Material. Les cercles sont écrits en deux arcs.
 */
object HoraIcons {
    /** Aujourd'hui : un cadran, son style et deux lignes horaires. */
    val Today = line("today", "M4 17h16", "M12 17V8l7 9", "M6 13l-1.5-1", "M7.5 9.5 6.5 8", "M12 5V3.5")
    val Agenda = line("agenda", "M4 6.5A1.5 1.5 0 0 1 5.5 5h13A1.5 1.5 0 0 1 20 6.5v12a1.5 1.5 0 0 1-1.5 1.5h-13A1.5 1.5 0 0 1 4 18.5z", "M4 10h16", "M9.3 10v10", "M14.6 10v10", "M8 3v4", "M16 3v4")
    val Search = line("search", circle(10.5f, 10.5f, 6f), "M15 15l5 5")
    // Des curseurs plutôt qu'un engrenage : au trait fin, l'engrenage se lisait comme un soleil.
    val Settings = line("settings", "M4 7h9", "M17 7h3", "M4 17h3", "M11 17h9", circle(15f, 7f, 2f), circle(9f, 17f, 2f))
    val Add = line("add", "M12 5v14", "M5 12h14")
    val Back = line("back", "M15 5l-7 7 7 7", "M8 12h12")
    val Previous = line("previous", "M14.5 6l-6 6 6 6")
    val Next = line("next", "M9.5 6l6 6-6 6")
    val Close = line("close", "M6 6l12 12", "M18 6L6 18")
    val Check = line("check", "M5 12.5l4.5 4.5L19 7.5")
    val Bell = line("bell", "M6 16v-5a6 6 0 0 1 12 0v5l1.5 2h-15z", "M10 20.5h4")
}

private fun circle(cx: Float, cy: Float, r: Float) =
    "M${cx - r} ${cy}a$r $r 0 1 0 ${2 * r} 0a$r $r 0 1 0 ${-2 * r} 0"

private fun line(name: String, vararg paths: String): ImageVector =
    ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        .apply {
            paths.forEach {
                addPath(
                    pathData = addPathNodes(it),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = STROKE,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }
        .build()

private const val STROKE = 1.7f
