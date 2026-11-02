package com.gabrielifrim.hora.core.domain.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Un agenda (Perso, Taff, Famille…). [colorKey] est le nom d'une couleur du
 * registre de l'interface ; le domaine ne la connaît que par son nom.
 */
data class Agenda(
    val id: Long = 0,
    val name: String,
    val colorKey: String,
    val hidden: Boolean = false,
    val sortOrder: Int = 0,
)

/** Quand a lieu un événement. */
sealed interface Timing {
    /** À heure fixe, dans un fuseau : 14:30 à Paris reste 14:30 à Paris si l'on voyage. */
    data class Timed(val start: Instant, val end: Instant, val zone: ZoneId) : Timing {
        init {
            require(!end.isBefore(start)) { "fin avant le début" }
        }
    }

    /** Toute la journée, du premier au dernier jour inclus, sans fuseau (date « flottante »). */
    data class AllDay(val first: LocalDate, val last: LocalDate) : Timing {
        init {
            require(!last.isBefore(first)) { "dernier jour avant le premier" }
        }
    }
}

data class Event(
    val id: Long = 0,
    /** Identifiant stable (RFC 5545) : il sert au dédoublonnage à l'import. */
    val uid: String,
    val agendaId: Long,
    val title: String,
    val timing: Timing,
    val location: String = "",
    val notes: String = "",
    /** Couleur propre à l'événement ; null : celle de son agenda. */
    val colorKey: String? = null,
)
