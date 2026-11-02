package com.gabrielifrim.hora.core.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Couleurs de l'interface, métaphore du cadran solaire : un fond, une encre, un
 * accent « bleu cadran », et l'ombre portée qui marque l'heure présente.
 *
 * Pas de couleurs dynamiques : chaque couple texte / fond est vérifié par
 * `PaletteContrastTest`.
 */
@Immutable
data class HoraColors(
    /** Identifiant persisté : ne se renomme jamais. */
    val id: String,
    val isDark: Boolean,
    val background: Color,
    /** Zones en retrait : champ de saisie rapide, feuilles. */
    val surface: Color,
    /** Texte et filets principaux. */
    val ink: Color,
    /** Texte secondaire : heures gravées, aides. */
    val ink2: Color,
    /** Filets doux. Décoratif, jamais du texte. */
    val rule: Color,
    /** Accent lisible en texte : ligne « maintenant », action principale. */
    val accent: Color,
    /** Texte posé sur l'accent. */
    val onAccent: Color,
    /** Texte posé sur un aplat d'encre (choix segmenté sélectionné). */
    val onInk: Color,
    /** L'ombre du cadran : décalage dur sous un bloc, ombre de la ligne « maintenant ». */
    val shadow: Color,
    /** Plages récurrentes, en fond des vues jour et semaine. */
    val band: Color,
    /** Nuit (avant le lever, après le coucher) dans la vue jour. */
    val night: Color,
    val success: Color,
    val warning: Color,
    val error: Color,
    /** Voile sous une feuille ou un dialogue. */
    val scrim: Color,
    val agenda: AgendaColors,
)

/**
 * Les huit couleurs d'agenda d'une palette. Elles codent une donnée : le test
 * exige 3:1 sur le fond et un écart ΔE ≥ 8 entre deux couleurs, même en
 * protanopie et deutéranopie simulées. Chacune est doublée d'une forme
 * ([AgendaShape]) : la couleur n'est jamais seule.
 */
@Immutable
data class AgendaColors(
    val blue: Color,
    val fir: Color,
    val plum: Color,
    val brick: Color,
    val ochre: Color,
    val teal: Color,
    val raspberry: Color,
    val slate: Color,
) {
    operator fun get(key: AgendaColorKey): Color = when (key) {
        AgendaColorKey.BLUE -> blue
        AgendaColorKey.FIR -> fir
        AgendaColorKey.PLUM -> plum
        AgendaColorKey.BRICK -> brick
        AgendaColorKey.OCHRE -> ochre
        AgendaColorKey.TEAL -> teal
        AgendaColorKey.RASPBERRY -> raspberry
        AgendaColorKey.SLATE -> slate
    }

    val all: List<Color> get() = AgendaColorKey.entries.map(::get)
}

/** Clé d'une couleur d'agenda, stockée par nom en base ; sa forme associée. */
enum class AgendaColorKey(val shape: AgendaShape) {
    BLUE(AgendaShape.DOT),
    FIR(AgendaShape.SQUARE),
    PLUM(AgendaShape.DIAMOND),
    BRICK(AgendaShape.TRIANGLE),
    OCHRE(AgendaShape.RING),
    TEAL(AgendaShape.CROSS),
    RASPBERRY(AgendaShape.TRIANGLE_DOWN),
    SLATE(AgendaShape.BAR),
}

enum class AgendaShape { DOT, SQUARE, DIAMOND, TRIANGLE, RING, CROSS, TRIANGLE_DOWN, BAR }

/** Registre des palettes : en ajouter une = ajouter une entrée. */
object HoraPalettes {
    val Midi = HoraColors(
        id = "midi",
        isDark = false,
        background = Color(0xFFF3F5F7),
        surface = Color(0xFFE7EBF0),
        ink = Color(0xFF18202B),
        ink2 = Color(0xFF4A5568),
        rule = Color(0xFFC9D1DB),
        accent = Color(0xFF2448A8),
        onAccent = Color(0xFFFFFFFF),
        onInk = Color(0xFFF3F5F7),
        shadow = Color(0x2918202B),
        band = Color(0x0E18202B),
        night = Color(0x1218202B),
        success = Color(0xFF186A41),
        warning = Color(0xFF8A5A00),
        error = Color(0xFFB3261E),
        scrim = Color(0x59050814),
        agenda = AgendaColors(
            blue = Color(0xFF0F6EC1),
            fir = Color(0xFF499953),
            plum = Color(0xFF5E2284),
            brick = Color(0xFF761C06),
            ochre = Color(0xFF7A5E05),
            teal = Color(0xFF3494A5),
            raspberry = Color(0xFF5A0A3F),
            slate = Color(0xFF57616E),
        ),
    )

    val Nocturne = HoraColors(
        id = "nocturne",
        isDark = true,
        background = Color(0xFF0F1530),
        surface = Color(0xFF18203F),
        ink = Color(0xFFEEE9DC),
        ink2 = Color(0xFFA9B1CC),
        rule = Color(0xFF2B355E),
        accent = Color(0xFF8FB8FF),
        onAccent = Color(0xFF0F1530),
        onInk = Color(0xFF0F1530),
        shadow = Color(0x8C030612),
        band = Color(0x0DEEE9DC),
        night = Color(0x59030612),
        success = Color(0xFF6FD39C),
        warning = Color(0xFFF2C46B),
        error = Color(0xFFFF8A80),
        scrim = Color(0x99000000),
        agenda = AgendaColors(
            blue = Color(0xFF6276FC),
            fir = Color(0xFF64C07E),
            plum = Color(0xFFC992FB),
            brick = Color(0xFFDB572B),
            ochre = Color(0xFFFDD25B),
            teal = Color(0xFFA2FBF3),
            raspberry = Color(0xFFCF5794),
            slate = Color(0xFFB7C4D4),
        ),
    )

    val all = listOf(Midi, Nocturne)
}
