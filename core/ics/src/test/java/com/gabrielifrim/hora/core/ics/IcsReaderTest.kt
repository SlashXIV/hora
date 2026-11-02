package com.gabrielifrim.hora.core.ics

import com.gabrielifrim.hora.core.domain.model.Reminder
import com.gabrielifrim.hora.core.domain.model.Timing
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Test

class IcsReaderTest {
    private val paris = ZoneId.of("Europe/Paris")

    private fun fossify() = IcsReader.read(javaClass.getResource("/fossify-synthetique.ics")!!.readBytes(), paris)
    private fun IcsImport.byUid(uid: String) = entries.single { it.item.event.uid == uid }

    @Test
    fun `export Fossify - tout entre, agendas tirés de CATEGORIES`() {
        val result = fossify()
        assertThat(result.entries).hasSize(7)
        assertThat(result.tasks).isEqualTo(1)
        assertThat(result.skipped).isEqualTo(0)
        assertThat(result.entries.map { it.agenda.name }.toSet())
            .containsExactly("Anniversaires", "Jours fériés", null, "exemple@example.org", "Évènement régulier")
        assertThat(result.byUid("Caldav-1-77").agenda.argb).isEqualTo(-30720)
    }

    @Test
    fun `anniversaire Fossify - DTEND égal à DTSTART donne un jour, la série est gardée`() {
        val alice = fossify().byUid("12").item.event
        assertThat(alice.timing).isEqualTo(Timing.AllDay(LocalDate.of(1970, 3, 2), LocalDate.of(1970, 3, 2)))
        assertThat(alice.recurrence!!.rule).isEqualTo("FREQ=YEARLY;INTERVAL=1;BYMONTH=3")
    }

    @Test
    fun `rappels sur la journée - comptés depuis minuit, ils deviennent ancrés`() {
        val result = fossify()
        assertThat(result.byUid("Caldav-1-77").item.event.reminders).containsExactly(
            Reminder.Anchored(0, LocalTime.of(10, 0)), // P0DT10H : le jour même à 10 h
            Reminder.Anchored(3, LocalTime.of(10, 0)), // -P2DT14H : 3 jours avant à 10 h
        )
        assertThat(result.byUid("Caldav-6-67").item.event.reminders).containsExactly(Reminder.Anchored(1, LocalTime.of(17, 0)))
        assertThat(result.shiftedReminders).isEqualTo(0)
    }

    @Test
    fun `heure UTC - lue en instant exact, rappels relatifs`() {
        val train = fossify().byUid("Caldav-6-68").item.event
        assertThat(train.timing).isEqualTo(Timing.Timed(Instant.parse("2025-06-23T04:10:00Z"), Instant.parse("2025-06-23T05:10:00Z"), paris))
        assertThat(train.reminders).containsExactly(Reminder.Before(30), Reminder.Before(1440))
    }

    @Test
    fun `texte déplié, échappements résolus, durée nulle gardée`() {
        val result = fossify()
        val stay = result.byUid("Caldav-6-67").item.event
        assertThat(stay.location).isEqualTo("Plage, côté sud")
        assertThat(stay.notes).startsWith("Pour afficher des informations détaillées sur les événements créés automatiquement tels")
        assertThat(stay.notes).contains("\n\nCréé à partir d'un e-mail.")
        assertThat(stay.timing).isEqualTo(Timing.AllDay(LocalDate.of(2025, 6, 23), LocalDate.of(2025, 6, 26)))
        val visit = result.byUid("Caldav-1-94").item.event.timing as Timing.Timed
        assertThat(visit.end).isEqualTo(visit.start)
    }

    @Test
    fun `tâche datée - importée en événement sur la journée`() {
        val task = fossify().byUid("00000000000000000000000000000000000000000001").item.event
        assertThat(task.timing).isEqualTo(Timing.AllDay(LocalDate.of(2026, 4, 10), LocalDate.of(2026, 4, 10)))
        assertThat(task.reminders).containsExactly(Reminder.Anchored(1, LocalTime.MIDNIGHT))
    }

    @Test
    fun `pli au milieu d'un caractère UTF-8 - recollé avant décodage`() {
        val bytes = "BEGIN:VCALENDAR\r\nBEGIN:VEVENT\r\nUID:x\r\nDTSTART;VALUE=DATE:20260101\r\nSUMMARY:caf".toByteArray() +
            byteArrayOf(0xC3.toByte()) + "\r\n ".toByteArray() + byteArrayOf(0xA9.toByte()) + "\r\nEND:VEVENT\r\nEND:VCALENDAR\r\n".toByteArray()
        assertThat(IcsReader.read(bytes, paris).entries.single().item.event.title).isEqualTo("café")
    }

    @Test
    fun `série Google - EXDATE, occurrence modifiée, occurrence annulée, RDATE`() {
        val ics = """
            BEGIN:VCALENDAR
            PRODID:-//Google Inc//Google Calendar 70.9054//EN
            X-WR-CALNAME:Travail
            BEGIN:VEVENT
            UID:serie@google.com
            DTSTART;TZID=Europe/Paris:20260105T090000
            DTEND;TZID=Europe/Paris:20260105T100000
            RRULE:FREQ=WEEKLY;BYDAY=MO
            EXDATE;TZID=Europe/Paris:20260112T090000
            RDATE;TZID=Europe/Paris:20260107T090000
            SUMMARY:Point
            END:VEVENT
            BEGIN:VEVENT
            UID:serie@google.com
            RECURRENCE-ID;TZID=Europe/Paris:20260119T090000
            DTSTART;TZID=Europe/Paris:20260119T110000
            DTEND;TZID=Europe/Paris:20260119T120000
            SUMMARY:Point décalé
            END:VEVENT
            BEGIN:VEVENT
            UID:serie@google.com
            RECURRENCE-ID;TZID=Europe/Paris:20260126T090000
            DTSTART;TZID=Europe/Paris:20260126T090000
            STATUS:CANCELLED
            SUMMARY:Point
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()
        val result = IcsReader.read(ics.toByteArray(), ZoneId.of("UTC"))
        fun key(day: Int) = java.time.LocalDateTime.of(2026, 1, day, 9, 0).atZone(paris).toInstant().toEpochMilli()

        val series = result.entries.single { it.item.event.uid == "serie@google.com" }
        assertThat(series.agenda.name).isEqualTo("Travail")
        assertThat((series.item.event.timing as Timing.Timed).zone).isEqualTo(paris)
        assertThat(series.item.event.recurrence!!.exceptions).containsExactly(key(12), key(26))

        val moved = result.entries.single { it.item.event.recurrenceId == key(19) }
        assertThat(moved.item.parentUid).isEqualTo("serie@google.com")
        assertThat(moved.item.event.title).isEqualTo("Point décalé")

        val extra = result.entries.single { it.item.event.recurrenceId == key(7) }
        assertThat(extra.item.parentUid).isEqualTo("serie@google.com")
        assertThat(result.entries).hasSize(3)
    }

    @Test
    fun `fuseau Outlook - nom Windows résolu par les décalages du VTIMEZONE`() {
        val ics = """
            BEGIN:VCALENDAR
            PRODID:Microsoft Exchange Server 2010
            BEGIN:VTIMEZONE
            TZID:Romance Standard Time
            BEGIN:STANDARD
            DTSTART:16010101T030000
            TZOFFSETFROM:+0200
            TZOFFSETTO:+0100
            RRULE:FREQ=YEARLY;INTERVAL=1;BYDAY=-1SU;BYMONTH=10
            END:STANDARD
            BEGIN:DAYLIGHT
            DTSTART:16010101T020000
            TZOFFSETFROM:+0100
            TZOFFSETTO:+0200
            RRULE:FREQ=YEARLY;INTERVAL=1;BYDAY=-1SU;BYMONTH=3
            END:DAYLIGHT
            END:VTIMEZONE
            BEGIN:VEVENT
            UID:outlook-1
            DTSTART;TZID=Romance Standard Time:20260710T140000
            DTEND;TZID=Romance Standard Time:20260710T150000
            SUMMARY:Réunion
            END:VEVENT
            BEGIN:VEVENT
            UID:outlook-2
            DTSTART;TZID=Fuseau inconnu:20260710T140000
            SUMMARY:Ailleurs
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()
        val result = IcsReader.read(ics.toByteArray(), paris)
        val meeting = result.entries.single { it.item.event.uid == "outlook-1" }.item.event.timing as Timing.Timed
        assertThat(meeting.zone).isEqualTo(paris)
        assertThat(meeting.start).isEqualTo(Instant.parse("2026-07-10T12:00:00Z"))
        assertThat(result.unknownZones).containsExactly("Fuseau inconnu")
    }

    @Test
    fun `rappel après le début - ramené au début et signalé`() {
        val ics = """
            BEGIN:VCALENDAR
            BEGIN:VEVENT
            UID:apres
            DTSTART:20260710T140000Z
            BEGIN:VALARM
            TRIGGER;RELATED=END:PT5M
            END:VALARM
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()
        val result = IcsReader.read(ics.toByteArray(), paris)
        assertThat(result.entries.single().item.event.reminders).containsExactly(Reminder.Before(0))
        assertThat(result.shiftedReminders).isEqualTo(1)
    }
}
