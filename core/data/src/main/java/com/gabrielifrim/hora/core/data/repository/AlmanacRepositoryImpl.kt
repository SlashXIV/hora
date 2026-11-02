package com.gabrielifrim.hora.core.data.repository

import android.content.Context
import com.gabrielifrim.hora.core.data.R
import com.gabrielifrim.hora.core.domain.almanac.Almanac
import com.gabrielifrim.hora.core.domain.almanac.AlmanacEntry
import com.gabrielifrim.hora.core.domain.almanac.AlmanacGroup
import com.gabrielifrim.hora.core.domain.almanac.AlmanacKind
import com.gabrielifrim.hora.core.domain.model.Agenda
import com.gabrielifrim.hora.core.domain.model.Event
import com.gabrielifrim.hora.core.domain.model.Timing
import com.gabrielifrim.hora.core.domain.repository.AlmanacRepository
import com.gabrielifrim.hora.core.domain.repository.SettingsRepository
import com.gabrielifrim.hora.core.domain.repository.almanacAgendaId
import com.gabrielifrim.hora.core.domain.repository.almanacEntryOf
import com.gabrielifrim.hora.core.domain.repository.almanacEventId
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class AlmanacRepositoryImpl @Inject constructor(
    private val settings: SettingsRepository,
    @param:ApplicationContext private val context: Context,
) : AlmanacRepository {

    override val agendas: Flow<List<Agenda>> = settings.settings.map { s ->
        AlmanacGroup.entries.map { group ->
            Agenda(
                id = almanacAgendaId(group),
                name = context.getString(group.label()),
                colorKey = group.colorKey(),
                hidden = group in s.hiddenAlmanac,
                sortOrder = Int.MAX_VALUE,
            )
        }
    }

    override fun eventsBetween(first: LocalDate, last: LocalDate, zone: ZoneId): Flow<List<Event>> = settings.settings.map { s ->
        Almanac.entries(first, last, zone, s.almanac, AlmanacGroup.entries.toSet() - s.hiddenAlmanac).map { it.toEvent(zone) }
    }

    override fun event(id: Long): Flow<Event?> = settings.settings.map { s ->
        val (kind, date) = almanacEntryOf(id) ?: return@map null
        val zone = ZoneId.systemDefault()
        Almanac.entries(date, date, zone, s.almanac, setOf(kind.group)).firstOrNull { it.kind == kind }?.toEvent(zone)
    }

    override suspend fun setHidden(group: AlmanacGroup, hidden: Boolean) =
        settings.update { it.copy(hiddenAlmanac = if (hidden) it.hiddenAlmanac + group else it.hiddenAlmanac - group) }

    private fun AlmanacEntry.toEvent(zone: ZoneId): Event {
        val id = almanacEventId(kind, date)
        return Event(
            id = id,
            uid = "almanac$id", // texte-technique : jamais montré ni exporté
            agendaId = almanacAgendaId(kind.group),
            title = context.getString(kind.label()),
            timing = at?.let { Timing.Timed(it, it, zone) } ?: Timing.AllDay(date, date),
        )
    }
}

private fun AlmanacGroup.label(): Int = when (this) {
    AlmanacGroup.HOLIDAYS -> R.string.almanac_holidays
    AlmanacGroup.FEASTS -> R.string.almanac_feasts
    AlmanacGroup.MOON -> R.string.almanac_moon
    AlmanacGroup.CLOCK -> R.string.almanac_clock
}

/** Couleurs fixes des agendas calculés (noms du registre `AgendaColorKey`). */
private fun AlmanacGroup.colorKey(): String = when (this) {
    AlmanacGroup.HOLIDAYS -> "BRICK"
    AlmanacGroup.FEASTS -> "RASPBERRY"
    AlmanacGroup.MOON -> "SLATE"
    AlmanacGroup.CLOCK -> "OCHRE"
}

private fun AlmanacKind.label(): Int = when (this) {
    AlmanacKind.NEW_YEAR -> R.string.almanac_new_year
    AlmanacKind.GOOD_FRIDAY -> R.string.almanac_good_friday
    AlmanacKind.EASTER_MONDAY -> R.string.almanac_easter_monday
    AlmanacKind.LABOUR_DAY -> R.string.almanac_labour_day
    AlmanacKind.VICTORY_1945 -> R.string.almanac_victory_1945
    AlmanacKind.ASCENSION -> R.string.almanac_ascension
    AlmanacKind.WHIT_MONDAY -> R.string.almanac_whit_monday
    AlmanacKind.BASTILLE_DAY -> R.string.almanac_bastille_day
    AlmanacKind.ASSUMPTION -> R.string.almanac_assumption
    AlmanacKind.ALL_SAINTS -> R.string.almanac_all_saints
    AlmanacKind.ARMISTICE -> R.string.almanac_armistice
    AlmanacKind.CHRISTMAS -> R.string.almanac_christmas
    AlmanacKind.SAINT_STEPHEN -> R.string.almanac_saint_stephen
    AlmanacKind.BRIDGE -> R.string.almanac_bridge
    AlmanacKind.MOTHERS_DAY -> R.string.almanac_mothers_day
    AlmanacKind.FATHERS_DAY -> R.string.almanac_fathers_day
    AlmanacKind.CANDLEMAS -> R.string.almanac_candlemas
    AlmanacKind.VALENTINE -> R.string.almanac_valentine
    AlmanacKind.GRANDMOTHERS_DAY -> R.string.almanac_grandmothers_day
    AlmanacKind.EASTER -> R.string.almanac_easter
    AlmanacKind.PENTECOST -> R.string.almanac_pentecost
    AlmanacKind.MUSIC_DAY -> R.string.almanac_music_day
    AlmanacKind.HALLOWEEN -> R.string.almanac_halloween
    AlmanacKind.NEW_MOON -> R.string.almanac_new_moon
    AlmanacKind.FIRST_QUARTER -> R.string.almanac_first_quarter
    AlmanacKind.FULL_MOON -> R.string.almanac_full_moon
    AlmanacKind.LAST_QUARTER -> R.string.almanac_last_quarter
    AlmanacKind.SUMMER_TIME -> R.string.almanac_summer_time
    AlmanacKind.WINTER_TIME -> R.string.almanac_winter_time
}
