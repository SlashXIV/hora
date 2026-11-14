package com.gabrielifrim.hora.core.data.repository

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.database.Cursor
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.provider.CalendarContract
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import android.provider.CalendarContract.Instances
import android.provider.CalendarContract.Reminders
import com.gabrielifrim.hora.core.data.R
import com.gabrielifrim.hora.core.data.database.EventDao
import com.gabrielifrim.hora.core.domain.model.Agenda
import com.gabrielifrim.hora.core.domain.model.Event
import com.gabrielifrim.hora.core.domain.model.Recurrence
import com.gabrielifrim.hora.core.domain.model.Reminder
import com.gabrielifrim.hora.core.domain.model.Timing
import com.gabrielifrim.hora.core.domain.recurrence.EditScope
import com.gabrielifrim.hora.core.domain.recurrence.SeriesEdits
import com.gabrielifrim.hora.core.domain.repository.DeviceCalendarRepository
import com.gabrielifrim.hora.core.domain.repository.deviceMinutes
import com.gabrielifrim.hora.core.domain.repository.deviceTimes
import com.gabrielifrim.hora.core.domain.repository.copyKey
import com.gabrielifrim.hora.core.domain.repository.deviceAllDay
import com.gabrielifrim.hora.core.domain.repository.deviceStart
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
import kotlinx.coroutines.withContext

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

    /** Ce qu'Hora a déjà : un événement d'Android au même UID, ou copie au même titre et au même début, est un doublon. */
    private class Known(val uids: Set<String>, val keys: Set<String>) {
        fun has(uid: String?, allDay: Boolean, dtstart: Long, title: String?) =
            (uid != null && uid in uids) || copyKey(allDay, deviceStart(allDay, dtstart), title.orEmpty()) in keys
    }

    private val known: Flow<Known> = combine(eventDao.observeUids(), eventDao.observeCopies()) { uids, copies ->
        Known(uids.toSet(), copies.mapTo(HashSet()) { copyKey(it.allDay, it.start, it.title) })
    }

    private fun readable(): Boolean = prefs.getBoolean(ENABLED, false) &&
        context.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    private fun canWrite(): Boolean = readable() && prefs.getBoolean(WRITE, false) &&
        context.checkSelfPermission(Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED

    private fun ids(key: String): Set<Long> = prefs.getStringSet(key, emptySet()).orEmpty().mapNotNull { it.toLongOrNull() }.toSet()

    /**
     * Masqué dans Hora : le choix fait dans Hora, sinon celui d'Android (un agenda décoché dans
     * Google Agenda, comme les fériés de chaque compte, reste masqué).
     */
    private fun hiddenTest(): (Long, Boolean) -> Boolean {
        val hidden = ids(HIDDEN)
        val shown = ids(SHOWN)
        return { id, androidVisible -> id in hidden || (!androidVisible && id !in shown) }
    }

    override val enabled: Flow<Boolean> = changes.map { readable() }

    // Arrêter la lecture arrête aussi l'écriture.
    override suspend fun setEnabled(on: Boolean) = prefs.edit().putBoolean(ENABLED, on).apply { if (!on) putBoolean(WRITE, false) }.apply()

    override val writable: Flow<Boolean> = changes.map { canWrite() }

    override suspend fun setWritable(on: Boolean) = prefs.edit().putBoolean(WRITE, on).apply()

    override val agendas: Flow<List<Agenda>> = changes.map { if (readable()) calendars() else emptyList() }.flowOn(Dispatchers.IO)

    override fun eventsBetween(first: LocalDate, last: LocalDate, zone: ZoneId): Flow<List<Event>> = combine(changes, known) { _, hora ->
        if (!readable()) return@combine emptyList()
        // Les journées entières sont rangées en UTC : un jour de marge de chaque côté, les vues trient au jour près.
        val from = first.minusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val to = last.plusDays(2).atStartOfDay(zone).toInstant().toEpochMilli()
        instances(from, to, null, hora)
    }.flowOn(Dispatchers.IO)

    override fun event(id: Long, begin: Long?): Flow<Event?> = changes.map {
        val eventId = deviceEventOf(id) ?: return@map null
        if (!readable()) return@map null
        val day = Duration.ofDays(1).toMillis()
        // Sans occurrence, ou si elle a bougé (série décalée), la prochaine.
        val found = begin?.let { b -> instances(b - day, b + day, eventId).firstOrNull { it.occurrence == b } } ?: run {
            // shortcut: on cherche dans l'année autour d'aujourd'hui ; au-delà, la fiche se ferme.
            val now = System.currentTimeMillis()
            instances(now - 400 * day, now + 400 * day, eventId).let { list -> list.firstOrNull { it.occurrence!! >= now } ?: list.lastOrNull() }
        }
        val rule = query(ContentUris.withAppendedId(Events.CONTENT_URI, eventId), arrayOf(Events.RRULE), null, null) { c -> c.getString(0) }.firstOrNull()
        found?.copy(reminders = reminders(eventId), recurrence = rule?.let { Recurrence(it) })
    }.flowOn(Dispatchers.IO)

    override fun search(query: String): Flow<List<Event>> = combine(changes, known) { _, hora ->
        val q = query.trim()
        if (!readable() || q.isEmpty()) return@combine emptyList()
        // shortcut: on cherche dans l'année autour d'aujourd'hui, première occurrence de chaque événement.
        val now = System.currentTimeMillis()
        val day = Duration.ofDays(1).toMillis()
        instances(now - 365 * day, now + 365 * day, null, hora)
            .filter { listOf(it.title, it.location, it.notes).any { s -> s.contains(q, ignoreCase = true) } }
            // La prochaine occurrence d'une série plutôt que celle de l'an dernier.
            .sortedBy { it.occurrence!! < now }
            .distinctBy { it.id }
    }.flowOn(Dispatchers.IO)

    override val duplicates: Flow<Int> = combine(changes, known) { _, hora ->
        if (!readable()) return@combine 0
        val hidden = hiddenTest()
        val columns = arrayOf(Events.CALENDAR_ID, Events.UID_2445, Events.VISIBLE, Events.ALL_DAY, Events.DTSTART, Events.TITLE)
        query(Events.CONTENT_URI, columns, "${Events.DELETED} = 0", null) { c ->
            true.takeIf { !hidden(c.getLong(0), c.getInt(2) == 1) && hora.has(c.getString(1), c.getInt(3) == 1, c.getLong(4), c.getString(5)) }
        }.size
    }.flowOn(Dispatchers.IO)

    override suspend fun setHidden(agendaId: Long, hidden: Boolean) {
        val id = deviceCalendarOf(agendaId) ?: return
        fun put(key: String, add: Boolean) = (ids(key).let { if (add) it + id else it - id }).map { it.toString() }.toSet()
        prefs.edit().putStringSet(HIDDEN, put(HIDDEN, hidden)).putStringSet(SHOWN, put(SHOWN, !hidden)).apply()
    }

    override suspend fun setColor(agendaId: Long, colorKey: String) {
        val id = deviceCalendarOf(agendaId) ?: return
        prefs.edit().putString(COLOR + id, colorKey).apply()
    }

    private fun calendars(): List<Agenda> {
        val hidden = hiddenTest()
        val write = canWrite()
        return query(
            Calendars.CONTENT_URI,
            arrayOf(Calendars._ID, Calendars.CALENDAR_DISPLAY_NAME, Calendars.ACCOUNT_NAME, Calendars.ACCOUNT_TYPE, Calendars.CALENDAR_COLOR, Calendars.CALENDAR_ACCESS_LEVEL, Calendars.VISIBLE),
            null, null,
        ) { c ->
            val id = c.getLong(0)
            Agenda(
                id = deviceAgendaId(id),
                // Un nom resté clé de traduction (« calendar_displayname_local » de l'agenda Xiaomi) n'est pas montrable.
                name = c.getString(1)?.takeUnless { RESOURCE_KEY.matches(it) } ?: context.getString(R.string.device_local_name),
                colorKey = prefs.getString(COLOR + id, null) ?: nearestColorKey(c.getInt(4)),
                hidden = hidden(id, c.getInt(6) == 1),
                sortOrder = Int.MAX_VALUE - 1,
                account = if (c.getString(3) == CalendarContract.ACCOUNT_TYPE_LOCAL) context.getString(R.string.device_local) else c.getString(2).orEmpty(),
                readOnly = !write || c.getInt(5) < Calendars.CAL_ACCESS_CONTRIBUTOR,
            )
        }.sortedWith(compareBy({ it.account }, { it.name }))
    }

    /** Occurrences entre [from] et [to] (millisecondes) des agendas visibles, ou du seul événement [eventId]. */
    private fun instances(from: Long, to: Long, eventId: Long?, hora: Known? = null): List<Event> {
        val hidden = hiddenTest()
        val uri = Instances.CONTENT_URI.buildUpon().also { ContentUris.appendId(it, from); ContentUris.appendId(it, to) }.build()
        val selection = "${Instances.STATUS} IS NULL OR ${Instances.STATUS} != ${Events.STATUS_CANCELED}"
        return query(
            uri,
            arrayOf(
                Instances.EVENT_ID, Instances.BEGIN, Instances.END, Instances.TITLE, Instances.EVENT_LOCATION, Instances.DESCRIPTION,
                Instances.CALENDAR_ID, Instances.ALL_DAY, Instances.AVAILABILITY, Instances.EVENT_TIMEZONE, Instances.UID_2445,
                Instances.VISIBLE, Instances.DTSTART,
            ),
            if (eventId == null) selection else "($selection) AND ${Instances.EVENT_ID} = ?",
            eventId?.let { arrayOf(it.toString()) },
        ) { c ->
            val calendar = c.getLong(6)
            if (hidden(calendar, c.getInt(11) == 1)) return@query null
            if (hora?.has(c.getString(10), c.getInt(7) == 1, c.getLong(12), c.getString(3)) == true) return@query null
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

    override suspend fun save(edited: Event, original: Event?, scope: EditScope): Long? = withContext(Dispatchers.IO) {
        if (!canWrite()) return@withContext null
        runCatching {
            val id = when {
                original == null -> {
                    val calendar = deviceCalendarOf(edited.agendaId) ?: return@runCatching null
                    val values = values(edited, edited.recurrence?.rule).apply { put(Events.CALENDAR_ID, calendar) }
                    resolver.insert(Events.CONTENT_URI, values)?.let(ContentUris::parseId)
                }
                original.recurrence != null && scope == EditScope.THIS -> {
                    val series = deviceEventOf(original.id) ?: return@runCatching null
                    exception(series, original.occurrence ?: return@runCatching null, exceptionValues(edited))
                }
                else -> {
                    val eventId = deviceEventOf(original.id) ?: return@runCatching null
                    // Toute la série : le décalage fait sur l'occurrence s'applique depuis son premier jour.
                    val timing = if (original.recurrence != null) {
                        SeriesEdits.shiftSeries(seriesStart(eventId) ?: return@runCatching null, original.timing, edited.timing)
                    } else {
                        edited.timing
                    }
                    val values = values(edited.copy(timing = timing), edited.recurrence?.rule)
                    eventId.takeIf { resolver.update(ContentUris.withAppendedId(Events.CONTENT_URI, it), values, null, null) > 0 }
                }
            } ?: return@runCatching null
            resolver.delete(Reminders.CONTENT_URI, "${Reminders.EVENT_ID} = ?", arrayOf(id.toString()))
            edited.reminders.map { deviceMinutes(it, edited.timing, ZoneId.systemDefault()) }.distinct().forEach { minutes ->
                resolver.insert(Reminders.CONTENT_URI, ContentValues().apply {
                    put(Reminders.EVENT_ID, id)
                    put(Reminders.MINUTES, minutes)
                    put(Reminders.METHOD, Reminders.METHOD_ALERT)
                })
            }
            deviceEventId(id)
        }.onFailure { Log.w(TAG, "Android refuse l'écriture", it) }.getOrNull()
    }

    override suspend fun delete(shown: Event, scope: EditScope): Boolean = withContext(Dispatchers.IO) {
        val eventId = deviceEventOf(shown.id)
        if (!canWrite() || eventId == null) return@withContext false
        runCatching {
            val uri = ContentUris.withAppendedId(Events.CONTENT_URI, eventId)
            val canceled = ContentValues().apply { put(Events.STATUS, Events.STATUS_CANCELED) }
            val isException = query(uri, arrayOf(Events.ORIGINAL_ID), null, null) { c -> c.getString(0) }.isNotEmpty()
            when {
                // Une occurrence d'une série : une exception annulée, comme le font les apps d'agenda.
                shown.recurrence != null && scope == EditScope.THIS -> exception(eventId, shown.occurrence ?: return@runCatching false, canceled) != null
                // Supprimer une occurrence déjà modifiée ferait revenir celle d'origine : on l'annule.
                isException -> resolver.update(uri, canceled, null, null) > 0
                else -> resolver.delete(uri, null, null) > 0
            }
        }.onFailure { Log.w(TAG, "Android refuse la suppression", it) }.getOrDefault(false)
    }

    override suspend fun canEditOccurrence(id: Long): Boolean = withContext(Dispatchers.IO) {
        val eventId = deviceEventOf(id) ?: return@withContext false
        query(ContentUris.withAppendedId(Events.CONTENT_URI, eventId), arrayOf(Events._SYNC_ID), null, null) { c -> c.getString(0) }.isNotEmpty()
    }

    /** Remplace l'occurrence [begin] de la série [seriesId] ; l'id de l'exception créée. */
    private fun exception(seriesId: Long, begin: Long, values: ContentValues): Long? {
        values.put(Events.ORIGINAL_INSTANCE_TIME, begin)
        return resolver.insert(ContentUris.withAppendedId(Events.CONTENT_EXCEPTION_URI, seriesId), values)?.let(ContentUris::parseId)
    }

    /** Le début de la série, seul utile pour la décaler. */
    private fun seriesStart(eventId: Long): Timing? = query(
        ContentUris.withAppendedId(Events.CONTENT_URI, eventId), arrayOf(Events.DTSTART, Events.ALL_DAY, Events.EVENT_TIMEZONE), null, null,
    ) { c ->
        val start = c.getLong(0)
        if (c.getInt(1) == 1) {
            deviceAllDay(start, start)
        } else {
            val zone = c.getString(2)?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: ZoneId.systemDefault()
            Timing.Timed(Instant.ofEpochMilli(start), Instant.ofEpochMilli(start), zone)
        }
    }.firstOrNull()

    /** Une occurrence modifiée : Android lui refuse fin et règle, elle garde une durée. */
    private fun exceptionValues(e: Event) = values(e, "").apply { remove(Events.RRULE); remove(Events.DTEND) }

    /** Ce qu'Hora écrit d'un événement ; couleur propre, trajet et épingle restent à Hora. */
    private fun values(e: Event, rule: String?) = ContentValues().apply {
        val t = deviceTimes(e.timing, rule != null)
        put(Events.TITLE, e.title)
        put(Events.EVENT_LOCATION, e.location)
        put(Events.DESCRIPTION, e.notes)
        put(Events.AVAILABILITY, if (e.busy) Events.AVAILABILITY_BUSY else Events.AVAILABILITY_FREE)
        put(Events.DTSTART, t.start)
        put(Events.DTEND, t.end)
        put(Events.DURATION, t.duration)
        put(Events.ALL_DAY, if (t.allDay) 1 else 0)
        put(Events.EVENT_TIMEZONE, t.zone)
        put(Events.RRULE, rule)
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
        const val SHOWN = "shown"
        val RESOURCE_KEY = Regex("[a-z]+(_[a-z]+)+")
        const val WRITE = "write"
        const val TAG = "Hora"
        const val COLOR = "color_"
    }
}
