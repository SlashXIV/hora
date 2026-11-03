package com.gabrielifrim.hora.core.domain.model

import java.time.Duration

/**
 * Un modèle d'événement (« Dentiste », 45 min, agenda Santé, rappel 1 h avant) : tout
 * sauf la date. [minutes] ne sert qu'à heure fixe ; un modèle sur la journée en dure une.
 */
data class EventTemplate(
    val id: Long = 0,
    val title: String,
    /** null : le premier agenda visible au moment de s'en servir. */
    val agendaId: Long?,
    val colorKey: String? = null,
    val location: String = "",
    val notes: String = "",
    val allDay: Boolean = false,
    val minutes: Int = 60,
    val busy: Boolean = true,
    val reminders: List<Reminder> = emptyList(),
) {
    companion object {
        fun of(event: Event): EventTemplate = EventTemplate(
            title = event.title,
            agendaId = event.agendaId,
            colorKey = event.colorKey,
            location = event.location,
            notes = event.notes,
            allDay = event.timing is Timing.AllDay,
            minutes = (event.timing as? Timing.Timed)?.let { Duration.between(it.start, it.end).toMinutes().toInt() } ?: 60,
            busy = event.busy,
            reminders = event.reminders,
        )
    }
}
