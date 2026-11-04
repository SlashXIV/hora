package com.gabrielifrim.hora.feature.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Build
import android.util.SizeF
import android.text.format.DateFormat
import android.widget.RemoteViews
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.gabrielifrim.hora.core.domain.model.Event
import com.gabrielifrim.hora.core.domain.model.Timing
import com.gabrielifrim.hora.core.domain.repository.AgendaRepository
import com.gabrielifrim.hora.core.domain.repository.AlmanacRepository
import com.gabrielifrim.hora.core.domain.repository.EventRepository
import com.gabrielifrim.hora.core.domain.repository.SettingsRepository
import com.gabrielifrim.hora.core.reminders.ReminderLauncher
import com.gabrielifrim.hora.core.ui.component.drawAgendaShape
import com.gabrielifrim.hora.core.ui.text.HoraFormats
import com.gabrielifrim.hora.core.ui.text.colorKeyOf
import com.gabrielifrim.hora.core.ui.text.formatsFor
import com.gabrielifrim.hora.core.ui.theme.AgendaShape
import com.gabrielifrim.hora.core.ui.theme.HoraColors
import com.gabrielifrim.hora.core.ui.theme.paletteFor
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import javax.inject.Inject
import com.gabrielifrim.hora.core.ui.R as UiR
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Le widget Aujourd'hui : redessiné à chaque changement d'événement ou de réglage (depuis
 * l'application), et à l'instant où son contenu change (une alarme approchée, sans réveil).
 */
@Singleton
class TodayWidgets @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val events: EventRepository,
    private val almanac: AlmanacRepository,
    private val agendaRepository: AgendaRepository,
    private val settings: SettingsRepository,
    private val launcher: ReminderLauncher,
) {
    suspend fun update(now: Instant = Instant.now()) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, TodayWidgetProvider::class.java))
        if (ids.isEmpty()) return arm(null)
        val zone = ZoneId.systemDefault()
        val today = now.atZone(zone).toLocalDate()
        val user = settings.settings.first()
        // La langue de l'app, pas celle du téléphone (« mercredi », semaine qui commence lundi).
        val locale = Locale.forLanguageTag(context.getString(UiR.string.app_locale))
        val formats = formatsFor(user, locale, DateFormat.is24HourFormat(context))
        val night = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        // shortcut: en « Système », la palette est lue au redessin ; passer en sombre ne redessine qu'au prochain changement. Couleurs « night » des ressources si ça gêne.
        val colors = paletteFor(user.theme, night)
        val agendas = combine(agendaRepository.agendas, almanac.agendas) { a, b -> (a + b).associateBy { it.id } }.first()
        val list = combine(events.eventsBetween(today, today.plusDays(31), zone), almanac.eventsBetween(today, today, zone)) { a, b -> a + b }.first()
        val content = TodayContent.of(list, now, zone)
        for (id in ids) {
            // En portrait, la largeur est la plus petite annoncée et la hauteur la plus grande.
            val options = manager.getAppWidgetOptions(id)
            val widthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH).takeIf { it > 0 } ?: 280
            val views = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Une variante par nombre de lignes : le lanceur prend la plus haute qui tient dans sa vraie taille.
                RemoteViews((0..TodayContent.MAX_ROWS).associate { rows -> SizeF(widthDp.toFloat(), heightFor(rows)) to render(content, agendas, colors, formats, zone, now, widthDp, rows) })
            } else {
                val heightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT).takeIf { it > 0 } ?: 140
                render(content, agendas, colors, formats, zone, now, widthDp, rowsFor(heightDp))
            }
            manager.updateAppWidget(id, views)
        }
        arm(content.changesAt)
    }

    private fun render(
        content: TodayContent,
        agendas: Map<Long, com.gabrielifrim.hora.core.domain.model.Agenda>,
        colors: HoraColors,
        formats: HoraFormats,
        zone: ZoneId,
        now: Instant,
        widthDp: Int,
        rows: Int,
    ): RemoteViews {
        val today = now.atZone(zone).toLocalDate()
        val density = context.resources.displayMetrics.density
        val textWidth = ((widthDp - 32) * density).toInt()
        val text = WidgetText(context)
        val ink = colors.ink.toArgb()
        val ink2 = colors.ink2.toArgb()
        val views = RemoteViews(context.packageName, R.layout.widget_today)
        views.setInt(R.id.widget_bg, "setColorFilter", colors.background.toArgb())
        views.setInt(R.id.widget_rule, "setColorFilter", colors.rule.toArgb())
        views.setTextViewText(R.id.widget_eyebrow, context.getString(R.string.widget_eyebrow, formats.weekday(today), formats.week(today)))
        views.setTextColor(R.id.widget_eyebrow, ink2)
        views.setTextColor(R.id.widget_clock, ink2)
        val date = formats.dayMonth(today)
        views.setImageViewBitmap(R.id.widget_date, text.draw(date, WidgetText.Style.POSTER, ink, textWidth))
        views.setContentDescription(R.id.widget_date, date)
        views.setOnClickPendingIntent(R.id.widget_root, PendingIntent.getActivity(context, 0, launcher.openApp(context), PendingIntent.FLAG_IMMUTABLE))

        val next = content.next
        views.setTextColor(R.id.widget_next_label, ink2)
        if (next == null) {
            views.setTextViewText(R.id.widget_next_label, context.getString(R.string.widget_nothing_next))
            views.setImageViewBitmap(R.id.widget_next_title, null)
            views.setImageViewBitmap(R.id.widget_next_time, null)
            views.setImageViewBitmap(R.id.widget_next_mark, null)
        } else {
            val start = (next.timing as Timing.Timed).start.atZone(zone)
            val time = formats.time(start.toLocalTime())
            // L'heure est à droite ; l'étiquette ne dit le jour que s'il n'est pas aujourd'hui.
            val label = if (start.toLocalDate() == today) context.getString(R.string.widget_next)
            else context.getString(R.string.widget_next_at, formats.dayShort(start.toLocalDate()))
            val timeBitmap = text.draw(time, WidgetText.Style.TIME, ink2, textWidth / 3)
            views.setTextViewText(R.id.widget_next_label, label)
            views.setImageViewBitmap(R.id.widget_next_title, text.draw(next.title, WidgetText.Style.HEADING, ink, textWidth - (19 * density).toInt() - (timeBitmap?.width ?: 0) - (8 * density).toInt()))
            views.setImageViewBitmap(R.id.widget_next_time, timeBitmap)
            views.setImageViewBitmap(R.id.widget_next_mark, mark(next, agendas, colors))
            views.setContentDescription(R.id.widget_next, "$label, ${next.title}, ${formats.spoken(start.toLocalTime())}")
            views.setOnClickPendingIntent(R.id.widget_next, openEvent(next))
        }

        views.removeAllViews(R.id.widget_rows)
        val timeWidth = text.draw(formats.time(java.time.LocalTime.of(22, 22)), WidgetText.Style.TIME, ink2, textWidth)?.width ?: 0
        content.rest.take(rows).forEach { e ->
            val row = RemoteViews(context.packageName, R.layout.widget_today_row)
            val t = e.timing
            val time = when {
                t is Timing.AllDay -> context.getString(R.string.widget_all_day)
                t is Timing.Timed -> formats.time(t.start.atZone(zone).toLocalTime())
                else -> ""
            }
            row.setImageViewBitmap(R.id.row_time, text.draw(time, WidgetText.Style.TIME, ink2, textWidth / 3))
            row.setInt(R.id.row_time, "setMinimumWidth", timeWidth)
            val spokenTime = if (t is Timing.Timed) formats.spoken(t.start.atZone(zone).toLocalTime()) else time
            row.setTextViewText(R.id.row_title, e.title)
            row.setTextColor(R.id.row_title, ink)
            row.setImageViewBitmap(R.id.row_mark, mark(e, agendas, colors))
            row.setContentDescription(R.id.row_root, "$spokenTime, ${e.title}")
            if (e.id > 0) row.setOnClickPendingIntent(R.id.row_root, openEvent(e))
            views.addView(R.id.widget_rows, row)
        }
        val nothingLeft = content.rest.isEmpty() && next != null && !TodayContent.isToday(next, today, zone)
        views.setViewVisibility(R.id.widget_empty, if (nothingLeft) android.view.View.VISIBLE else android.view.View.GONE)
        views.setTextViewText(R.id.widget_empty, context.getString(R.string.widget_nothing_left))
        views.setTextColor(R.id.widget_empty, ink2)
        return views
    }

    private fun openEvent(e: Event): PendingIntent = PendingIntent.getActivity(
        context, (e.id * 31 + (e.occurrence ?: 0)).toInt(),
        launcher.openEvent(context, e.id, e.occurrence ?: -1),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** Le repère d'agenda dessiné par le même code que dans l'app (forme + couleur). */
    private fun mark(e: Event, agendas: Map<Long, com.gabrielifrim.hora.core.domain.model.Agenda>, colors: HoraColors): Bitmap {
        val key = colorKeyOf(e, agendas)
        return shapeBitmap(key.shape, colors.agenda[key], (11 * context.resources.displayMetrics.density).toInt().coerceAtLeast(8))
    }

    /** Hauteur, en dp, qu'il faut pour [rows] lignes sous l'en-tête et le prochain (environ 124 dp à 100 %, mesuré sur Pixel). */
    private fun heightFor(rows: Int): Float = context.resources.configuration.fontScale * (HEADER_DP + ROW_DP * rows)

    private fun rowsFor(heightDp: Int): Int = (0..TodayContent.MAX_ROWS).lastOrNull { heightFor(it) <= heightDp } ?: 0

    /** Une alarme approchée et sans réveil : l'écran d'accueil n'est pas regardé écran éteint. */
    private fun arm(at: Instant?) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        val pending = PendingIntent.getBroadcast(context, 0, Intent(context, WidgetTickReceiver::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        if (at == null) return manager.cancel(pending)
        manager.set(AlarmManager.RTC, at.toEpochMilli(), pending)
    }
}

private const val HEADER_DP = 124f
private const val ROW_DP = 32f

internal fun shapeBitmap(shape: AgendaShape, color: Color, px: Int): Bitmap {
    val image = ImageBitmap(px, px)
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(image), Size(px.toFloat(), px.toFloat())) { drawAgendaShape(shape, color) }
    return image.asAndroidBitmap()
}

private fun BroadcastReceiver.updateAsync(widgets: TodayWidgets) {
    val pending = goAsync()
    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
        try {
            widgets.update()
        } finally {
            pending.finish()
        }
    }
}

@AndroidEntryPoint
class TodayWidgetProvider : AppWidgetProvider() {
    @Inject lateinit var widgets: TodayWidgets

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = updateAsync(widgets)

    // Redimensionné : le nombre de lignes et la largeur des textes changent.
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: android.os.Bundle) = updateAsync(widgets)
}

@AndroidEntryPoint
class WidgetTickReceiver : BroadcastReceiver() {
    @Inject lateinit var widgets: TodayWidgets

    override fun onReceive(context: Context, intent: Intent) = updateAsync(widgets)
}
