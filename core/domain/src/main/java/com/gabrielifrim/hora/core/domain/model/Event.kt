package com.gabrielifrim.hora.core.domain.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Un agenda (Perso, Taff, Famille…). [colorKey] est le nom d'une couleur du
 * registre de l'interface ; le domaine ne la connaît que par son nom.
 * [defaultReminders] sont copiés sur chaque nouvel événement de l'agenda.
 */
data class Agenda(
    val id: Long = 0,
    val name: String,
    val colorKey: String,
    val hidden: Boolean = false,
    val sortOrder: Int = 0,
    val defaultReminders: List<Reminder> = emptyList(),
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

/**
 * Clé d'une occurrence dans sa série : l'instant de début d'origine en
 * millisecondes (heure fixe), ou le jour depuis l'époque (toute la journée). Elle
 * ne change pas quand on modifie l'occurrence : c'est le RECURRENCE-ID de RFC 5545.
 */
fun Timing.occurrenceKey(): Long = when (this) {
    is Timing.Timed -> start.toEpochMilli()
    is Timing.AllDay -> first.toEpochDay()
}

/** Série : une RRULE (RFC 5545, sans le préfixe « RRULE: ») et les occurrences supprimées. */
data class Recurrence(val rule: String, val exceptions: Set<Long> = emptySet())

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
    /** Occupé (TRANSP:OPAQUE) ; un événement libre ne bloque pas un créneau. */
    val busy: Boolean = true,
    /** Épinglé sur Aujourd'hui : compte à rebours, ou compteur « depuis » une fois passé. */
    val pinned: Boolean = false,
    /** Marge de trajet avant le début, saisie à la main (sans réseau, pas d'itinéraire) ; 0 : aucune. */
    val travelMinutes: Int = 0,
    val reminders: List<Reminder> = emptyList(),
    /** Règle de répétition, sur l'événement d'origine d'une série. */
    val recurrence: Recurrence? = null,
    /** Occurrence modifiée seule : sa série d'origine et la clé de l'occurrence qu'elle remplace. */
    val parentId: Long? = null,
    val recurrenceId: Long? = null,
    /**
     * Rempli seulement sur une occurrence calculée d'une série : sa clé. [timing] est
     * alors celui de l'occurrence, et [id] celui de la série.
     */
    val occurrence: Long? = null,
) {
    val isRecurring: Boolean get() = recurrence != null || parentId != null
}
