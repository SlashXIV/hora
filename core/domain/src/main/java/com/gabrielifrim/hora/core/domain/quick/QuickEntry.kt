package com.gabrielifrim.hora.core.domain.quick

import com.gabrielifrim.hora.core.domain.model.Reminder
import java.text.Normalizer
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.MonthDay
import java.time.YearMonth

/**
 * Ce qu'une phrase de saisie rapide a compris. Rien n'est enregistré : l'éditeur
 * s'ouvre pré-rempli et on relit. Un champ null n'a pas été dit.
 */
data class QuickEntry(
    val title: String,
    val date: LocalDate? = null,
    val start: LocalTime? = null,
    val end: LocalTime? = null,
    val duration: Duration? = null,
    val location: String = "",
    val reminders: List<Reminder> = emptyList(),
) {
    companion object {
        /**
         * « Dentiste jeudi 14h30 1h @cabinet Dupont rappel 1h avant ». Comprend : aujourd'hui,
         * demain, après-demain, un jour de la semaine (le prochain, aujourd'hui compris si
         * l'heure n'est pas passée), « le 12 », « 12/11 », « 12 novembre » ; « 14h », « 14h30 »,
         * « 14:30 », « midi » ; une fin (« de 14h à 16h », « 14h-16h ») ou une durée (« 1h »,
         * « 30 min », « pendant 2h ») ; « rappel 1h avant » ; un lieu après « @ ».
         * « rappel la veille » (à 20:00, ou « à 18h »). Le reste fait le titre.
         */
        fun parse(text: String, now: LocalDateTime): QuickEntry = Parser(WORD.findAll(text).map { it.value }.toList(), now).run()

        /** Ce que chaque mot de [text] est devenu, en plages de caractères : pour colorer la saisie. */
        fun parts(text: String, now: LocalDateTime): List<Pair<IntRange, Part>> {
            val found = WORD.findAll(text).toList()
            val parser = Parser(found.map { it.value }, now).also { it.run() }
            return found.mapIndexed { i, m -> m.range to parser.kinds[i] }
        }

        private val WORD = Regex("\\S+")
    }

    enum class Part { TITLE, WHEN, REMINDER, PLACE }
}

private class Parser(private val words: List<String>, private val now: LocalDateTime) {
    private val keys = words.map(::normalize)
    private val used = BooleanArray(words.size)
    val kinds = Array(words.size) { QuickEntry.Part.TITLE }
    private var date: LocalDate? = null
    private var weekday: DayOfWeek? = null
    private var start: LocalTime? = null
    private var end: LocalTime? = null
    private var duration: Duration? = null
    private val reminders = mutableListOf<Reminder>()

    fun run(): QuickEntry {
        var i = 0
        while (i < words.size) i = if (keys[i].startsWith("@")) i + 1 else match(i) ?: (i + 1)
        val today = now.toLocalDate()
        val resolved = date ?: weekday?.let { d ->
            val ahead = (d.value - today.dayOfWeek.value + 7) % 7
            val sameDayPast = ahead == 0 && start != null && start!! <= now.toLocalTime()
            today.plusDays(if (sameDayPast) 7L else ahead.toLong())
        }
        val at = keys.indexOfFirst { it.startsWith("@") }
        val free = words.indices.filter { !used[it] }
        if (at >= 0) free.filter { it >= at }.forEach { kinds[it] = QuickEntry.Part.PLACE }
        fun text(range: List<Int>) = range.joinToString(" ") { words[it] }.removePrefix("@").trim()
        return QuickEntry(
            title = text(free.filter { at < 0 || it < at }),
            date = resolved,
            start = start,
            end = end,
            duration = duration,
            location = if (at < 0) "" else text(free.filter { it >= at }),
            reminders = reminders,
        )
    }

    /** Essaie chaque forme à la position [i] ; rend la position suivante, ou null. */
    private fun match(i: Int): Int? {
        val w = keys[i]
        // Mots de liaison : consommés seulement s'ils introduisent ce qui suit.
        if (w in CONNECTORS && i + 1 < words.size) {
            val next = match(i + 1) ?: return null
            used[i] = true
            kinds[i] = kinds[i + 1]
            return next
        }
        if (w == "rappel" || w == "rappeler") {
            if (keys.getOrNull(i + 1) == "la" && keys.getOrNull(i + 2) == "veille") {
                // « la veille à 18h » ; sans heure, 20:00 comme sur la planche.
                val at = if (keys.getOrNull(i + 3) == "a") timeAt(i + 4) else null
                reminders += Reminder.Anchored(1, at?.first ?: LocalTime.of(20, 0))
                return consume(i, 3 + (at?.let { 1 + it.second } ?: 0), QuickEntry.Part.REMINDER)
            }
            val (d, n) = durationAt(i + 1) ?: return null
            reminders += Reminder.Before(d.toMinutes().toInt())
            val after = if (keys.getOrNull(i + 1 + n) == "avant") n + 1 else n
            return consume(i, 1 + after, QuickEntry.Part.REMINDER)
        }
        if (w == "pendant") {
            val (d, n) = durationAt(i + 1) ?: return null
            duration = d
            return consume(i, 1 + n)
        }
        RELATIVE[w]?.let { if (date == null) { date = now.toLocalDate().plusDays(it); return consume(i, 1) } }
        DAYS[w]?.let { d ->
            if (date != null || weekday != null) return null
            weekday = d
            return consume(i, if (keys.getOrNull(i + 1) == "prochain") 2 else 1)
        }
        dateAt(i)?.let { (d, n) -> if (date == null) { date = d; return consume(i, n) } }
        val time = timeAt(i)
        if (time != null) {
            val (t, n) = time
            if (start == null) {
                start = t
                // « 14h-16h », « 14h à 16h », « 14h jusqu'à 16h »
                val link = keys.getOrNull(i + n)
                if (link in RANGE_LINKS) timeAt(i + n + 1)?.let { (e, m) -> if (e > t) { end = e; return consume(i, n + 1 + m) } }
                return consume(i, n)
            }
            // Une deuxième heure après « à » est une fin ; nue, c'est une durée (« 14h30 1h »).
        }
        if (duration == null && end == null) {
            durationAt(i)?.let { (d, n) -> duration = d; return consume(i, n) }
        }
        return null
    }

    private fun consume(i: Int, n: Int, kind: QuickEntry.Part = QuickEntry.Part.WHEN): Int {
        for (k in i until i + n) { used[k] = true; kinds[k] = kind }
        return i + n
    }

    /** « 14h », « 14h30 », « 14:30 », « 14 h », « midi », « 14h-16h » (début seulement ici). */
    private fun timeAt(i: Int): Pair<LocalTime, Int>? {
        val w = keys.getOrNull(i) ?: return null
        if (w == "midi") return LocalTime.NOON to 1
        Regex("^(\\d{1,2})(?:h|:)(\\d{2})?$").matchEntire(w.substringBefore('-'))?.let { m ->
            val t = clock(m.groupValues[1], m.groupValues[2]) ?: return null
            // « 14h-16h » en un mot : la fin suit le tiret.
            if (w.contains('-') && start == null) {
                Regex("^(\\d{1,2})(?:h|:)(\\d{2})?$").matchEntire(w.substringAfter('-'))?.let { e ->
                    clock(e.groupValues[1], e.groupValues[2])?.takeIf { it > t }?.let { end = it }
                }
            }
            return t to 1
        }
        if (w.matches(Regex("^\\d{1,2}$")) && keys.getOrNull(i + 1) == "h") return clock(w, "")?.let { it to 2 }
        return null
    }

    private fun clock(h: String, m: String): LocalTime? {
        val hour = h.toInt()
        val minute = m.ifEmpty { "0" }.toInt()
        return if (hour in 0..23 && minute in 0..59) LocalTime.of(hour, minute) else null
    }

    /** « 1h », « 1h30 », « 30 min », « 30min », « 2 h », « 2 heures ». */
    private fun durationAt(i: Int): Pair<Duration, Int>? {
        val w = keys.getOrNull(i) ?: return null
        Regex("^(\\d{1,2})h(\\d{2})?$").matchEntire(w)?.let { m ->
            return Duration.ofHours(m.groupValues[1].toLong()).plusMinutes(m.groupValues[2].ifEmpty { "0" }.toLong()) to 1
        }
        Regex("^(\\d{1,3})(min|mn|minutes?)$").matchEntire(w)?.let { m -> return Duration.ofMinutes(m.groupValues[1].toLong()) to 1 }
        if (w.matches(Regex("^\\d{1,3}$"))) {
            val n = w.toLong()
            return when (keys.getOrNull(i + 1)) {
                "min", "mn", "minute", "minutes" -> Duration.ofMinutes(n) to 2
                "h", "heure", "heures" -> Duration.ofHours(n) to 2
                else -> null
            }
        }
        return null
    }

    /** « 12/11 », « 12/11/2026 », « le 12 », « 12 novembre », « 1er novembre 2027 ». */
    private fun dateAt(i: Int): Pair<LocalDate, Int>? {
        val w = keys.getOrNull(i) ?: return null
        val today = now.toLocalDate()
        Regex("^(\\d{1,2})/(\\d{1,2})(?:/(\\d{2}|\\d{4}))?$").matchEntire(w)?.let { m ->
            val (d, mo, y) = m.destructured
            val md = runCatching { MonthDay.of(mo.toInt(), d.toInt()) }.getOrNull() ?: return null
            return yearOf(md, y)?.let { it to 1 }
        }
        val day = Regex("^(\\d{1,2})(?:er)?$").matchEntire(w)?.groupValues?.get(1)?.toInt() ?: return null
        MONTHS[keys.getOrNull(i + 1)]?.let { month ->
            val md = runCatching { MonthDay.of(month, day) }.getOrNull() ?: return null
            val year = keys.getOrNull(i + 2)?.takeIf { it.matches(Regex("^\\d{4}$")) }
            return yearOf(md, year.orEmpty())?.let { it to if (year != null) 3 else 2 }
        }
        // « le 12 » : ce mois-ci, ou le suivant si le jour est passé.
        if (i > 0 && keys[i - 1] == "le" && keys.getOrNull(i + 1) != "h") {
            var month = YearMonth.from(today)
            if (day < today.dayOfMonth) month = month.plusMonths(1)
            if (!month.isValidDay(day)) return null
            return month.atDay(day) to 1
        }
        return null
    }

    /** Année dite, sinon celle qui place la date aujourd'hui ou après. */
    private fun yearOf(md: MonthDay, year: String): LocalDate? {
        val today = now.toLocalDate()
        if (year.isNotEmpty()) {
            val y = year.toInt().let { if (it < 100) 2000 + it else it }
            return runCatching { md.atYear(y) }.getOrNull()
        }
        val thisYear = md.atYear(today.year)
        return if (thisYear < today) md.atYear(today.year + 1) else thisYear
    }

    private companion object {
        val CONNECTORS = setOf("a", "le", "de", "des", "vers", "jusqu'a", "jusqua")
        val RANGE_LINKS = setOf("-", "a", "jusqu'a", "jusqua")
        val RELATIVE = mapOf("aujourd'hui" to 0L, "aujourdhui" to 0L, "demain" to 1L, "apres-demain" to 2L)
        val DAYS = mapOf(
            "lundi" to DayOfWeek.MONDAY, "mardi" to DayOfWeek.TUESDAY, "mercredi" to DayOfWeek.WEDNESDAY,
            "jeudi" to DayOfWeek.THURSDAY, "vendredi" to DayOfWeek.FRIDAY, "samedi" to DayOfWeek.SATURDAY, "dimanche" to DayOfWeek.SUNDAY,
        )
        val MONTHS = listOf("janvier", "fevrier", "mars", "avril", "mai", "juin", "juillet", "aout", "septembre", "octobre", "novembre", "decembre")
            .mapIndexed { i, m -> m to i + 1 }.toMap() +
            mapOf("janv" to 1, "fev" to 2, "fevr" to 2, "avr" to 4, "juil" to 7, "sept" to 9, "oct" to 10, "nov" to 11, "dec" to 12)
    }
}

/** Minuscules sans accents, apostrophe droite, ponctuation finale retirée : « Après-demain, » → « apres-demain ». */
private fun normalize(word: String): String =
    Normalizer.normalize(word.lowercase().replace('’', '\''), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}"), "")
        .trimEnd(',', '.', ';', '!', '?')
