package io.github.thepytales.llmhub.docs

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters

/** Weekly sessions of a course between two dates, without public holidays in Saxony and lecture-free periods. */
object SemesterPlan {
    /** Easter Sunday (Gauss/Meeus algorithm). */
    fun easter(year: Int): LocalDate {
        val a = year % 19
        val b = year / 100
        val c = year % 100
        val d = b / 4
        val e = b % 4
        val f = (b + 8) / 25
        val g = (b - f + 1) / 3
        val h = (19 * a + b - d - g + 15) % 30
        val i = c / 4
        val k = c % 4
        val l = (32 + 2 * e + 2 * i - h - k) % 7
        val m = (a + 11 * h + 22 * l) / 451
        val month = (h + l - 7 * m + 114) / 31
        val day = (h + l - 7 * m + 114) % 31 + 1
        return LocalDate.of(year, month, day)
    }

    /** Public holidays in Saxony (Buß- und Bettag included; Fronleichnam only in some communities, so left out). */
    fun holidays(year: Int): Map<LocalDate, String> {
        val e = easter(year)
        // Buß- und Bettag: the Wednesday before 23 November.
        val repentance = LocalDate.of(year, 11, 22).with(TemporalAdjusters.previousOrSame(DayOfWeek.WEDNESDAY))
        return mapOf(
            LocalDate.of(year, 1, 1) to "Neujahr",
            e.minusDays(2) to "Karfreitag",
            e.plusDays(1) to "Ostermontag",
            LocalDate.of(year, 5, 1) to "Tag der Arbeit",
            e.plusDays(39) to "Christi Himmelfahrt",
            e.plusDays(50) to "Pfingstmontag",
            LocalDate.of(year, 10, 3) to "Tag der Deutschen Einheit",
            LocalDate.of(year, 10, 31) to "Reformationstag",
            repentance to "Buß- und Bettag",
            LocalDate.of(year, 12, 25) to "1. Weihnachtsfeiertag",
            LocalDate.of(year, 12, 26) to "2. Weihnachtsfeiertag",
        )
    }

    fun weekday(text: String): DayOfWeek? = when (text.trim().lowercase().take(2)) {
        "mo" -> DayOfWeek.MONDAY
        "di", "tu" -> DayOfWeek.TUESDAY
        "mi", "we" -> DayOfWeek.WEDNESDAY
        "do", "th" -> DayOfWeek.THURSDAY
        "fr" -> DayOfWeek.FRIDAY
        "sa" -> DayOfWeek.SATURDAY
        "so", "su" -> DayOfWeek.SUNDAY
        else -> null
    }

    /** "21.12.2026–03.01.2027; 2026-11-18" → date ranges. */
    fun ranges(text: String): List<ClosedRange<LocalDate>> = text.split(';', '\n', ',').mapNotNull { part ->
        val dates = Regex("\\d{4}-\\d{1,2}-\\d{1,2}|\\d{1,2}\\.\\d{1,2}\\.\\d{4}").findAll(part).mapNotNull { CalendarFiles.parse(it.value)?.first?.toLocalDate() }.toList()
        when (dates.size) {
            0 -> null
            1 -> dates[0]..dates[0]
            else -> dates[0]..dates[1]
        }
    }

    data class Session(val date: LocalDate, val number: Int, val topic: String)
    data class Skipped(val date: LocalDate, val reason: String)

    fun plan(day: DayOfWeek, first: LocalDate, last: LocalDate, breaks: List<ClosedRange<LocalDate>>, topics: List<String>): Pair<List<Session>, List<Skipped>> {
        val sessions = mutableListOf<Session>()
        val skipped = mutableListOf<Skipped>()
        var d = first.with(TemporalAdjusters.nextOrSame(day))
        while (!d.isAfter(last)) {
            val holiday = holidays(d.year)[d]
            when {
                holiday != null -> skipped += Skipped(d, holiday)
                breaks.any { d in it } -> skipped += Skipped(d, "vorlesungsfrei")
                else -> sessions += Session(d, sessions.size + 1, topics.getOrElse(sessions.size) { "" })
            }
            d = d.plusWeeks(1)
        }
        return sessions to skipped
    }

    /** "13:00", "13.00", "4. DS" (Doppelstunden der TU Dresden). */
    fun time(text: String): Pair<LocalTime, LocalTime>? {
        val ds = Regex("(\\d)\\.?\\s*DS", RegexOption.IGNORE_CASE).find(text)?.groupValues?.get(1)?.toIntOrNull()
        if (ds != null && ds in 1..8) {
            val start = listOf("07:30", "09:20", "11:10", "13:00", "14:50", "16:40", "18:30", "20:20")[ds - 1]
            val t = LocalTime.parse(start)
            return t to t.plusMinutes(90)
        }
        val times = Regex("(\\d{1,2})[:.](\\d{2})").findAll(text).map { LocalTime.of(it.groupValues[1].toInt(), it.groupValues[2].toInt()) }.toList()
        return when (times.size) {
            0 -> null
            1 -> times[0] to times[0].plusMinutes(90)
            else -> times[0] to times[1]
        }
    }
}
