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
)

/**
 * Un événement. [start] et [end] changent d'unité selon [allDay] : millisecondes
 * depuis l'époque (heure fixe, avec [timeZone]), ou jour depuis l'époque, dernier
 * jour inclus (toute la journée, date flottante, [timeZone] nul).
 */
@Entity(
    tableName = "event",
    foreignKeys = [ForeignKey(entity = AgendaEntity::class, parentColumns = ["id"], childColumns = ["agenda_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("agenda_id"), Index(value = ["uid"], unique = true), Index(value = ["all_day", "start", "end"])],
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
        WHERE agenda.hidden = 0 AND (
            (event.all_day = 0 AND event.start < :toMs AND (event.`end` > :fromMs OR (event.`end` = event.start AND event.start >= :fromMs)))
            OR (event.all_day = 1 AND event.start <= :lastDay AND event.`end` >= :firstDay)
        )
        """,
    )
    fun observeBetween(fromMs: Long, toMs: Long, firstDay: Long, lastDay: Long): Flow<List<EventEntity>>

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
}
