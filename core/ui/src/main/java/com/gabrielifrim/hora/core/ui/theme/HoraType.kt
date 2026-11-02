package com.gabrielifrim.hora.core.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.gabrielifrim.hora.core.ui.R

// Trois polices OFL embarquées, variables : l'appli reste hors ligne et ne dépend
// d'aucune police du téléphone. Licences dans design/fonts/.

private fun variable(res: Int, weight: Int, vararg extra: FontVariation.Setting) = Font(
    resId = res,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight), *extra),
)

// Taille optique de Fraunces fixée pour l'affiche : à 144, les empattements
// s'affinent comme une gravure ; à 30, ils tiennent dans un titre de 20 sp.
private val PosterOptical = FontVariation.Setting("opsz", 120f)
private val HeadingOptical = FontVariation.Setting("opsz", 30f)

/** Affiche : les dates et les titres, comme les chiffres gravés d'un cadran. */
val PosterFamily = FontFamily(variable(R.font.fraunces, 560, PosterOptical))
private val HeadingFamily = FontFamily(variable(R.font.fraunces, 600, HeadingOptical))

/** Texte courant, dessiné pour la lisibilité (I, l et 1 se distinguent). */
val TextFamily = FontFamily(
    variable(R.font.atkinson_hyperlegible_next, 400),
    variable(R.font.atkinson_hyperlegible_next, 500),
    variable(R.font.atkinson_hyperlegible_next, 650),
)

/** Heures, comptes à rebours : à chasse fixe, un 9 qui devient 10 ne décale rien. */
val HourFamily = FontFamily(
    variable(R.font.spline_sans_mono, 450),
    variable(R.font.spline_sans_mono, 500),
)

private val Trimmed = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both)

/** Styles nommés par usage, pas par taille. */
@Immutable
data class HoraType(
    /** Grande date d'écran (« 2 novembre »). */
    val poster: TextStyle = TextStyle(
        fontFamily = PosterFamily, fontWeight = FontWeight(560), fontSize = 40.sp,
        lineHeight = 1.0.em, letterSpacing = (-0.015).em, lineHeightStyle = Trimmed,
    ),
    /** Titre de section ou d'événement. */
    val heading: TextStyle = TextStyle(
        fontFamily = HeadingFamily, fontWeight = FontWeight(600), fontSize = 20.sp, lineHeight = 1.2.em,
    ),
    /** Petite ligne en capitales au-dessus d'un titre : jour, semaine, contexte. */
    val eyebrow: TextStyle = TextStyle(
        fontFamily = TextFamily, fontWeight = FontWeight(650), fontSize = 11.sp, letterSpacing = 0.12.em,
    ),
    val body: TextStyle = TextStyle(
        fontFamily = TextFamily, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 1.45.em,
    ),
    val bodySmall: TextStyle = TextStyle(
        fontFamily = TextFamily, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 1.4.em,
    ),
    /** Heures gravées, horaires. */
    val hour: TextStyle = TextStyle(
        fontFamily = HourFamily, fontWeight = FontWeight(450), fontSize = 12.sp, fontFeatureSettings = "tnum",
    ),
    /** Grand chiffre : compte à rebours, total d'import. */
    val figure: TextStyle = TextStyle(
        fontFamily = HourFamily, fontWeight = FontWeight(500), fontSize = 34.sp,
        fontFeatureSettings = "tnum", lineHeightStyle = Trimmed,
    ),
    /** Libellé de bouton. */
    val action: TextStyle = TextStyle(
        fontFamily = TextFamily, fontWeight = FontWeight(650), fontSize = 14.sp, lineHeight = 1.2.em,
    ),
    /** Libellé de champ, en capitales. */
    val label: TextStyle = TextStyle(
        fontFamily = TextFamily, fontWeight = FontWeight(650), fontSize = 11.sp, letterSpacing = 0.1.em,
    ),
)
