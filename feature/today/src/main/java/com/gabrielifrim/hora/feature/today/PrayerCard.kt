package com.gabrielifrim.hora.feature.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gabrielifrim.hora.core.domain.almanac.Place
import com.gabrielifrim.hora.core.domain.almanac.PrayerSettings
import com.gabrielifrim.hora.core.domain.almanac.Prayers
import com.gabrielifrim.hora.core.reminders.PrayerAlarms
import com.gabrielifrim.hora.core.ui.component.Eyebrow
import com.gabrielifrim.hora.core.ui.component.HoraText
import com.gabrielifrim.hora.core.ui.text.HoraFormats
import com.gabrielifrim.hora.core.ui.theme.HoraTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Les cinq prières du jour (avis mashhour, école malikite) : début et fin du temps
 * préférable (« ≈ » quand elle est estimée). La fin du temps de nécessité ne paraît
 * que pour la prière en cours dont le préférable est passé : là où elle sert, sans
 * se lire comme une heure limite ordinaire. En tête, la date de l'hégire.
 */
@Composable
internal fun PrayerCard(today: LocalDate, now: Instant, place: Place, settings: PrayerSettings, zone: ZoneId, formats: HoraFormats) {
    val windows = remember(today, place, settings) { Prayers.day(today, place, settings) } ?: return
    val (hDay, hMonth, hYear) = remember(today, settings.hijriAdjust) { Prayers.hijri(today, settings.hijriAdjust) }
    val months = stringArrayResource(R.array.hijri_months)
    fun time(t: Instant) = formats.time(t.atZone(zone).toLocalTime())
    fun spoken(t: Instant) = formats.spoken(t.atZone(zone).toLocalTime())

    // La prière en cours : la dernière commencée (le temps de nécessité de Maghrib court aussi pendant 'Isha).
    val current = windows.lastOrNull { !now.isBefore(it.start) && now.isBefore(it.necessaryEnd) }?.prayer
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Eyebrow(stringResource(R.string.prayers_eyebrow, hDay, months[hMonth - 1], hYear))
        // Chiffres à chasse fixe : la plus longue heure (« 11:50 AM ») donne sa largeur à toutes.
        val widestStart = windows.map { time(it.start) }.maxBy { it.length }
        windows.forEach { w ->
            val isCurrent = w.prayer == current
            val late = isCurrent && !now.isBefore(w.preferredEnd)
            val name = stringResource(PrayerAlarms.prayerName(w.prayer, settings.fajrName))
            val preferred = if (w.estimated) "≈ " + time(w.preferredEnd) else time(w.preferredEnd) // texte-technique : signe « environ »
            val necessity = stringResource(R.string.prayer_necessity, time(w.necessaryEnd))
            val spokenText = stringResource(
                if (w.estimated) R.string.prayer_spoken_estimated else R.string.prayer_spoken,
                name, spoken(w.start), spoken(w.preferredEnd),
            ) + if (late) " ; " + stringResource(R.string.prayer_necessity, spoken(w.necessaryEnd)) else ""
            Row(
                Modifier.fillMaxWidth().heightIn(min = 40.dp).clearAndSetSemantics { contentDescription = spokenText },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                HoraText(
                    name,
                    style = HoraTheme.type.body.copy(fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal),
                    color = if (isCurrent) HoraTheme.colors.accent else HoraTheme.colors.ink,
                    modifier = Modifier.width(88.dp),
                )
                val hourStyle = HoraTheme.type.hour.copy(fontSize = HoraTheme.type.body.fontSize)
                Box {
                    HoraText(widestStart, style = hourStyle, modifier = Modifier.alpha(0f))
                    HoraText(time(w.start), style = hourStyle)
                }
                Column(Modifier.weight(1f).padding(start = 4.dp)) {
                    HoraText(stringResource(R.string.prayer_until, preferred), style = HoraTheme.type.bodySmall, color = HoraTheme.colors.ink2)
                    if (late) HoraText(necessity, style = HoraTheme.type.bodySmall, color = HoraTheme.colors.ink2)
                }
            }
        }
        HoraText(stringResource(R.string.prayers_legend), style = HoraTheme.type.bodySmall, color = HoraTheme.colors.ink2)
    }
}
