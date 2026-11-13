package com.gabrielifrim.hora.core.data.repository

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.database.Cursor
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import android.provider.CalendarContract.Instances
import android.provider.CalendarContract.Reminders
import com.gabrielifrim.hora.core.data.R
import com.gabrielifrim.hora.core.data.database.EventDao
import com.gabrielifrim.hora.core.domain.model.Agenda
import com.gabrielifrim.hora.core.domain.model.Event
import com.gabrielifrim.hora.core.domain.model.Reminder
import com.gabrielifrim.hora.core.domain.model.Timing
import com.gabrielifrim.hora.core.domain.repository.DeviceCalendarRepository
import com.gabrielifrim.hora.core.domain.repository.deviceAllDay
import com.gabrielifrim.hora.core.domain.repository.deviceAgendaId
import com.gabrielifrim.hora.core.domain.repository.deviceCalendarOf
import com.gabrielifrim.hora.core.domain.repository.deviceEventId
import com.gabrielifrim.hora.core.domain.repository.deviceEventOf
import com.gabrielifrim.hora.core.domain.repository.nearestColorKey
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/**
 * Lecture des agendas d'Android par `CalendarContract`. Les choix (pont activé, agendas
 * masqués, couleurs) restent en préférences : propres au téléphone, comme ses agendas,
 * ils ne voyagent pas avec la sauvegarde de la base.
 */
@Singleton
internal class DeviceCalendarRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    eventDao: EventDao,
) : DeviceCalendarRepository {
    private val prefs = context.getSharedPreferences("device_calendars", Context.MODE_PRIVATE)
    private val resolver get() = context.contentResolver

    /** Préférences ou agendas d'Android changés ; un premier signal au départ. */
    private val changes: Flow<Unit> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { trySend(Unit) }
        }
        // Observer le fournisseur exige la permission (SecurityException sinon) : on retente à chaque changement de réglage.
        var observing = false
        fun observe() {
            if (observing || context.checkSelfPermission(Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) return
            observing = runCatching { resolver.registerContentObserver(Uri.parse("content://com.android.calendar"), true, observer) }.isSuccess
        }
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> observe(); trySend(Unit) }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        observe()
        trySend(Unit)
        awaitClose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
            if (observing) resolver.unregisterContentObserver(observer)
        }
    }.conflate()

    private val uids: Flow<Set<String>> = eventDao.observeUids().map { it.toSet() }

    private fun readable(): Boolean = prefs.getBoolean(ENABLED, false) &&
        context.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    private fun hidden(): Set<Long> = prefs.getStringSet(HIDDEN, emptySet()).orEmpty().mapNotNull { it.toLongOrNull() }.toSet()

    override val enabled: Flow<Boolean> = changes.map { readable() }

    override suspend fun setEnabled(on: Boolean) = prefs.edit().putBoolean(ENABLED, on).apply()

    override val agendas: Flow<List<Agenda>> = changes.map { if (readable()) calendars() else emptyList() }.flowOn(Dispatchers.IO)

    override fun eventsBetween(first: LocalDate, last: LocalDate, zone: ZoneId): Flow<List<Event>> = combine(changes, uids) { _, horaUids ->
        if (!readable()) return@combine emptyList()
        // Les journées entières sont rangées en UTC : un jour de marge de chaque côté, les vues trient au jour près.
        val from = first.minusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val to = last.plusDays(2).atStartOfDay(zone).toInstant().toEpochMilli()
        instances(from, to, null).filter { it.uid !in horaUids }
    }.flowOn(Dispatchers.IO)

    override fun event(id: Long, begin: Long?): Flow<Event?> = changes.map {
        val eventId = deviceEventOf(id) ?: return@map null
        if (!readable()) return@map null
        val day = Duration.ofDays(1).toMillis()
        val found = if (begin != null) {
            instances(begin - day, begin + day, eventId).firstOrNull { it.occurrence == begin }
        } else {
            // shortcut: sans occurrence, la prochaine dans l'année ; passer l'occurrence si un appel en manque.
            val now = System.currentTimeMillis()
            instances(now - 400 * day, now + 400 * day, eventId).let { list -> list.firstOrNull { it.occurrence!! >= now } ?: list.lastOrNull() }
        }
        found?.copy(reminders = reminders(eventId))
    }.flowOn(Dispatchers.IO)

    override fun search(query: String): Flow<List<Event>> = combine(changes, uids) { _, horaUids ->
        val q = query.trim()
        if (!readable() || q.isEmpty()) return@combine emptyList()
        // shortcut: on cherche dans l'année autour d'aujourd'hui, première occurrence de chaque événement.
        val now = System.currentTimeMillis()
        val day = Duration.ofDays(1).toMillis()
        instances(now - 365 * day, now + 365 * day, null)
            .filter { it.uid !in horaUids && listOf(it.title, it.location, it.notes).any { s -> s.contains(q, ignoreCase = true) } }
            // La prochaine occurrence d'une série plutôt que celle de l'an dernier.
            .sortedBy { it.occurrence!! < now }
            .distinctBy { it.id }
    }.flowOn(Dispatchers.IO)

    override val duplicates: Flow<Int> = combine(changes, uids) { _, horaUids ->
        if (!readable()) return@combine 0
        val hidden = hidden()
        query(Events.CONTENT_URI, arrayOf(Events.CALENDAR_ID, Events.UID_2445), "${Events.DELETED} = 0", null) { c ->
            c.getLong(0).takeIf { it !in hidden }?.let { c.getString(1) }
        }.count { it in horaUids }
    }.flowOn(Dispatchers.IO)

    override suspend fun setHidden(agendaId: Long, hidden: Boolean) {
        val id = deviceCalendarOf(agendaId) ?: return
        val set = hidden().let { if (hidden) it + id else it - id }
        prefs.edit().putStringSet(HIDDEN, set.map { it.toString() }.toSet()).apply()
    }

    override suspend fun setColor(agendaId: Long, colorKey: String) {
        val id = deviceCalendarOf(agendaId) ?: return
        prefs.edit().putString(COLOR + id, colorKey).apply()
    }

    private fun calendars(): List<Agenda> {
        val hidden = hidden()
        return query(
            Calendars.CONTENT_URI,
            arrayOf(Calendars._ID, Calendars.CALENDAR_DISPLAY_NAME, Calendars.ACCOUNT_NAME, Calendars.ACCOUNT_TYPE, Calendars.CALENDAR_COLOR),
            null, null,
        ) { c ->
            val id = c.getLong(0)
            Agenda(
                id = deviceAgendaId(id),
                name = c.getString(1).orEmpty(),
                colorKey = prefs.getString(COLOR + id, null) ?: nearestColorKey(c.getInt(4)),
                hidden = id in hidden,
                sortOrder = Int.MAX_VALUE - 1,
                account = if (c.getString(3) == CalendarContract.ACCOUNT_TYPE_LOCAL) context.getString(R.string.device_local) else c.getString(2).orEmpty(),
            )
        }.sortedWith(compareBy({ it.account }, { it.name }))
    }

    /** Occurrences entre [from] et [to] (millisecondes) des agendas visibles, ou du seul événement [eventId]. */
    private fun instances(from: Long, to: Long, eventId: Long?): List<Event> {
        val hidden = hidden()
        val uri = Instances.CONTENT_URI.buildUpon().also { ContentUris.appendId(it, from); ContentUris.appendId(it, to) }.build()
        val selection = "${Instances.STATUS} IS NULL OR ${Instances.STATUS} != ${Events.STATUS_CANCELED}"
        return query(
            uri,
            arrayOf(
                Instances.EVENT_ID, Instances.BEGIN, Instances.END, Instances.TITLE, Instances.EVENT_LOCATION, Instances.DESCRIPTION,
                Instances.CALENDAR_ID, Instances.ALL_DAY, Instances.AVAILABILITY, Instances.EVENT_TIMEZONE, Instances.UID_2445,
            ),
            if (eventId == null) selection else "($selection) AND ${Instances.EVENT_ID} = ?",
            eventId?.let { arrayOf(it.toString()) },
        ) { c ->
            val calendar = c.getLong(6)
            if (calendar in hidden) return@query null
            val id = c.getLong(0)
            val begin = c.getLong(1)
            val end = maxOf(begin, c.getLong(2))
            val timing = if (c.getInt(7) == 1) {
                deviceAllDay(begin, end)
            } else {
                val zone = c.getString(9)?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: ZoneId.systemDefault()
                Timing.Timed(Instant.ofEpochMilli(begin), Instant.ofEpochMilli(end), zone)
            }
            Event(
                id = deviceEventId(id),
                uid = c.getString(10) ?: "device$id", // texte-technique : jamais montré ni exporté
                agendaId = deviceAgendaId(calendar),
                title = c.getString(3).orEmpty(),
                timing = timing,
                location = c.getString(4).orEmpty(),
                notes = c.getString(5).orEmpty(),
                busy = c.getInt(8) != Events.AVAILABILITY_FREE,
                // L'instant de début : la fiche retrouve cette occurrence-là.
                occurrence = begin,
            )
        }.sortedBy { it.occurrence }
    }

    /** Les rappels d'Android, pour mémoire : Hora ne les fait pas sonner. */
    private fun reminders(eventId: Long): List<Reminder> = query(
        Reminders.CONTENT_URI, arrayOf(Reminders.MINUTES), "${Reminders.EVENT_ID} = ?", arrayOf(eventId.toString()),
    ) { c -> c.getInt(0).takeIf { it >= 0 }?.let { Reminder.Before(it) } }.distinct()

    private fun <T : Any> query(uri: Uri, projection: Array<String>, selection: String?, args: Array<String>?, row: (Cursor) -> T?): List<T> =
        // Permission retirée entre deux lectures, fournisseur absent : on montre rien plutôt que de planter.
        runCatching {
            resolver.query(uri, projection, selection, args, null)?.use { c -> buildList { while (c.moveToNext()) row(c)?.let(::add) } }
        }.getOrNull().orEmpty()

    private companion object {
        const val ENABLED = "enabled"
        const val HIDDEN = "hidden"
        const val COLOR = "color_"
    }
}
