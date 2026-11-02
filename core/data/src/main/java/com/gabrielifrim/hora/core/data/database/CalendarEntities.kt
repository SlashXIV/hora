package com.gabrielifrim.hora.core.data.database

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "agenda")
data class AgendaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** Nom d'une couleur du registre (`AgendaColorKey`), stocké par nom. */
    @ColumnInfo(name = "color_key") val colorKey: String,
    val hidden: Boolean,
    @ColumnInfo(name = "sort_order") val sortOrder: Int,
    /** Rappels copiés sur chaque nouvel événement (`ReminderCodec`). */
    @ColumnInfo(name = "default_reminders", defaultValue = "") val defaultReminders: String = "",
)

/**
 * Un événement. [start] et [end] changent d'unité selon [allDay] : millisecondes
 * depuis l'époque (heure fixe, avec [timeZone]), ou jour depuis l'époque, dernier
 * jour inclus (toute la journée, date flottante, [timeZone] nul).
 */
@Entity(
    tableName = "event",
    foreignKeys = [ForeignKey(entity = AgendaEntity::class, parentColumns = ["id"], childColumns = ["agenda_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("agenda_id"), Index(value = ["uid"], unique = true), Index(value = ["all_day", "start", "end"]), Index("parent_id")],
)
data class EventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uid: String,
    @ColumnInfo(name = "agenda_id") val agendaId: Long,
    val title: String,
    val location: String,
    val notes: String,
    @ColumnInfo(name = "color_key") val colorKey: String?,
    @ColumnInfo(name = "all_day") val allDay: Boolean,
    val start: Long,
    val end: Long,
    @ColumnInfo(name = "time_zone") val timeZone: String?,
    /** Rappels (`ReminderCodec`), vide s'il n'y en a pas. */
    @ColumnInfo(defaultValue = "") val reminders: String = "",
    /** RRULE de la série, sans « RRULE: » ; null pour un événement simple. */
    val rrule: String? = null,
    /** Clés des occurrences supprimées, séparées par des virgules. */
    @ColumnInfo(defaultValue = "") val exdates: String = "",
    /** Occurrence modifiée seule : la série d'origine et la clé remplacée. */
    @ColumnInfo(name = "parent_id") val parentId: Long? = null,
    @ColumnInfo(name = "recurrence_id") val recurrenceId: Long? = null,
)

/** Un jeu de rappels réutilisable (« Rendez-vous du matin »). */
@Entity(tableName = "reminder_set")
data class ReminderSetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val spec: String,
)

/** Rappel déjà présenté : jamais deux fois, et ce qui manque se rattrape au redémarrage. */
@Entity(tableName = "fired_reminder", primaryKeys = ["event_id", "occurrence", "reminder"])
data class FiredReminderEntity(
    @ColumnInfo(name = "event_id") val eventId: Long,
    val occurrence: Long,
    val reminder: String,
    @ColumnInfo(name = "fired_at") val firedAt: Long,
)

/** Rappel reporté : sonne de nouveau à [triggerAt]. */
@Entity(tableName = "snooze")
data class SnoozeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "event_id") val eventId: Long,
    val occurrence: Long,
    @ColumnInfo(name = "trigger_at") val triggerAt: Long,
    val alarm: Boolean,
)

@Dao
interface AgendaDao {
    @Query("SELECT * FROM agenda ORDER BY sort_order, id")
    fun observeAll(): Flow<List<AgendaEntity>>

    @Query("SELECT COUNT(*) FROM agenda")
    suspend fun count(): Int

    @Upsert
    suspend fun upsert(agenda: AgendaEntity): Long

    @Query("DELETE FROM agenda WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface EventDao {
    /**
     * Événements des agendas visibles qui touchent la période : à heure fixe, ceux
     * qui commencent avant [toMs] et finissent après [fromMs] (ou qui, de durée
     * nulle, commencent dedans) ; sur la journée, ceux dont les jours la croisent.
     */
    @Query(
        """
        SELECT event.* FROM event JOIN agenda ON agenda.id = event.agenda_id
        WHERE agenda.hidden = 0 AND event.rrule IS NULL AND (
            (event.all_day = 0 AND event.start < :toMs AND (event.`end` > :fromMs OR (event.`end` = event.start AND event.start >= :fromMs)))
            OR (event.all_day = 1 AND event.start <= :lastDay AND event.`end` >= :firstDay)
        )
        """,
    )
    fun observeBetween(fromMs: Long, toMs: Long, firstDay: Long, lastDay: Long): Flow<List<EventEntity>>

    /** Séries visibles qui commencent avant la fin de la fenêtre : elles sont développées ensuite. */
    @Query(
        """
        SELECT event.* FROM event JOIN agenda ON agenda.id = event.agenda_id
        WHERE agenda.hidden = 0 AND event.rrule IS NOT NULL
            AND ((event.all_day = 0 AND event.start < :toMs) OR (event.all_day = 1 AND event.start <= :lastDay))
        """,
    )
    fun observeSeries(toMs: Long, lastDay: Long): Flow<List<EventEntity>>

    /** Clés des occurrences modifiées à part, par série : elles sortent du développement. */
    @Query("SELECT parent_id, recurrence_id FROM event WHERE parent_id IS NOT NULL AND recurrence_id IS NOT NULL")
    fun observeOverrides(): Flow<List<OverrideRow>>

    @Query("SELECT parent_id, recurrence_id FROM event WHERE parent_id IS NOT NULL AND recurrence_id IS NOT NULL")
    suspend fun overrideRows(): List<OverrideRow>

    @Query("SELECT * FROM event WHERE parent_id = :seriesId")
    suspend fun children(seriesId: Long): List<EventEntity>

    @Query("SELECT * FROM event WHERE id = :id")
    suspend fun get(id: Long): EventEntity?

    /** Tout ce qui a des rappels, agendas masqués compris : un agenda masqué sonne quand même. */
    @Query("SELECT * FROM event WHERE reminders != ''")
    suspend fun withReminders(): List<EventEntity>

    @Query("SELECT * FROM event WHERE id = :id")
    fun observe(id: Long): Flow<EventEntity?>

    @Query(
        """
        SELECT event.* FROM event JOIN agenda ON agenda.id = event.agenda_id
        WHERE agenda.hidden = 0 AND (title LIKE :pattern ESCAPE '\' OR location LIKE :pattern ESCAPE '\' OR notes LIKE :pattern ESCAPE '\')
        LIMIT 200
        """,
    )
    fun search(pattern: String): Flow<List<EventEntity>>

    @Upsert
    suspend fun upsert(event: EventEntity): Long

    @Query("DELETE FROM event WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM event WHERE parent_id = :seriesId")
    suspend fun deleteChildren(seriesId: Long)
}

data class OverrideRow(
    @ColumnInfo(name = "parent_id") val parentId: Long,
    @ColumnInfo(name = "recurrence_id") val recurrenceId: Long,
)

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminder_set ORDER BY name")
    fun observeSets(): Flow<List<ReminderSetEntity>>

    @Upsert
    suspend fun upsertSet(set: ReminderSetEntity): Long

    @Query("DELETE FROM reminder_set WHERE id = :id")
    suspend fun deleteSet(id: Long)

    @Query("SELECT * FROM fired_reminder WHERE fired_at >= :since")
    suspend fun firedSince(since: Long): List<FiredReminderEntity>

    @androidx.room.Insert(onConflict = androidx.room.OnConflictStrategy.IGNORE)
    suspend fun markFired(fired: FiredReminderEntity)

    @Query("DELETE FROM fired_reminder WHERE fired_at < :before")
    suspend fun forgetFiredBefore(before: Long)

    @Query("SELECT * FROM snooze")
    suspend fun snoozes(): List<SnoozeEntity>

    @androidx.room.Insert
    suspend fun snooze(snooze: SnoozeEntity)

    @Query("DELETE FROM snooze WHERE id = :id")
    suspend fun deleteSnooze(id: Long)
}
