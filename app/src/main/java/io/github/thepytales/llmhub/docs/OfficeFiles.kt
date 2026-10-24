package io.github.thepytales.llmhub.docs

import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Base64
import java.util.UUID

/** An e-mail the user sends from their own mail app; stored as .eml marked as draft (Outlook opens it for editing). */
data class MailDraft(val to: String, val cc: String, val subject: String, val body: String)

object MailFiles {
    private fun encodeWord(s: String) =
        if (s.all { it.code < 128 }) s else "=?UTF-8?B?" + Base64.getEncoder().encodeToString(s.toByteArray()) + "?="

    fun write(file: File, d: MailDraft) {
        val text = buildString {
            append("X-Unsent: 1\r\n")
            if (d.to.isNotBlank()) append("To: ").append(d.to.trim()).append("\r\n")
            if (d.cc.isNotBlank()) append("Cc: ").append(d.cc.trim()).append("\r\n")
            append("Subject: ").append(encodeWord(d.subject.trim())).append("\r\n")
            append("MIME-Version: 1.0\r\n")
            append("Content-Type: text/plain; charset=UTF-8\r\n")
            append("Content-Transfer-Encoding: base64\r\n\r\n")
            Base64.getMimeEncoder().encodeToString(d.body.replace("\r\n", "\n").replace("\n", "\r\n").toByteArray()).let { append(it).append("\r\n") }
        }
        file.writeText(text)
    }

    fun read(file: File): MailDraft? = runCatching {
        val raw = file.readText()
        val head = raw.substringBefore("\r\n\r\n")
        val body = raw.substringAfter("\r\n\r\n").trim()
        fun header(name: String) = head.lines().firstOrNull { it.startsWith("$name:", ignoreCase = true) }?.substringAfter(':')?.trim().orEmpty()
        val subject = header("Subject").let { s ->
            if (s.startsWith("=?UTF-8?B?")) String(Base64.getDecoder().decode(s.removePrefix("=?UTF-8?B?").removeSuffix("?="))) else s
        }
        MailDraft(header("To"), header("Cc"), subject, String(Base64.getMimeDecoder().decode(body)).replace("\r\n", "\n"))
    }.getOrNull()
}

/** A calendar entry as .ics (opens in Outlook, Apple and Google Calendar; Android adds it via the calendar app). */
data class CalendarEvent(
    val title: String,
    val start: LocalDateTime,
    val end: LocalDateTime,
    val allDay: Boolean = false,
    val location: String = "",
    val description: String = "",
    val reminderMinutes: Int? = null,
)

object CalendarFiles {
    private val ZONE: ZoneId = ZoneId.of("Europe/Berlin")
    private val UTC = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
    private val DAY = DateTimeFormatter.ofPattern("yyyyMMdd")

    /** "2026-11-03 14:00", "2026-11-03T14:00", "03.11.2026 14:00", "3.11.2026" (date only = all day). */
    fun parse(text: String): Pair<LocalDateTime, Boolean>? {
        val t = text.trim().replace('T', ' ').removeSuffix("Z")
        val iso = Regex("^(\\d{4})-(\\d{1,2})-(\\d{1,2})(?:\\s+(\\d{1,2}):(\\d{2}))?").find(t)
        val de = Regex("^(\\d{1,2})\\.(\\d{1,2})\\.(\\d{4})(?:,?\\s+(\\d{1,2})[:.](\\d{2}))?").find(t)
        val (y, mo, d, h, mi) = when {
            iso != null -> iso.groupValues.drop(1)
            de != null -> de.groupValues.drop(1).let { listOf(it[2], it[1], it[0], it[3], it[4]) }
            else -> return null
        }
        val date = runCatching { LocalDate.of(y.toInt(), mo.toInt(), d.toInt()) }.getOrNull() ?: return null
        return if (h.isEmpty()) date.atStartOfDay() to true else date.atTime(h.toInt(), mi.toInt()) to false
    }

    private fun esc(s: String) = s.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace("\n", "\\n")

    /** Lines longer than 75 octets are folded as RFC 5545 requires. */
    private fun fold(line: String): String {
        if (line.toByteArray().size <= 75) return line
        val out = StringBuilder()
        var count = 0
        for (ch in line) {
            val n = ch.toString().toByteArray().size
            if (count + n > 74) { out.append("\r\n "); count = 1 }
            out.append(ch); count += n
        }
        return out.toString()
    }

    fun write(file: File, e: CalendarEvent) {
        fun utc(t: LocalDateTime) = ZonedDateTime.of(t, ZONE).withZoneSameInstant(ZoneId.of("UTC")).format(UTC)
        val lines = buildList {
            add("BEGIN:VCALENDAR"); add("VERSION:2.0"); add("PRODID:-//ADEU LLM//DE"); add("CALSCALE:GREGORIAN"); add("METHOD:PUBLISH")
            add("BEGIN:VEVENT")
            add("UID:${UUID.randomUUID()}@adeu-llm")
            add("DTSTAMP:${utc(LocalDateTime.now())}")
            if (e.allDay) {
                add("DTSTART;VALUE=DATE:${e.start.toLocalDate().format(DAY)}")
                add("DTEND;VALUE=DATE:${e.end.toLocalDate().plusDays(if (e.end.toLocalDate() == e.start.toLocalDate()) 1 else 0).format(DAY)}")
            } else {
                add("DTSTART:${utc(e.start)}")
                add("DTEND:${utc(e.end)}")
            }
            add("SUMMARY:${esc(e.title)}")
            if (e.location.isNotBlank()) add("LOCATION:${esc(e.location)}")
            if (e.description.isNotBlank()) add("DESCRIPTION:${esc(e.description)}")
            e.reminderMinutes?.let { m ->
                add("BEGIN:VALARM"); add("ACTION:DISPLAY"); add("DESCRIPTION:${esc(e.title)}"); add("TRIGGER:-PT${m}M"); add("END:VALARM")
            }
            add("END:VEVENT"); add("END:VCALENDAR")
        }
        file.writeText(lines.joinToString("\r\n") { fold(it) } + "\r\n")
    }

    /** Reads what this app wrote (for "Zum Kalender hinzufügen" on Android). */
    fun read(file: File): CalendarEvent? = runCatching {
        // Only the event itself, not the description of its reminder (VALARM).
        val text = file.readText().replace("\r\n ", "").substringBefore("BEGIN:VALARM")
        fun prop(name: String) = text.lines().firstOrNull { it.startsWith("$name:") || it.startsWith("$name;") }?.substringAfter(':')?.trim()
        fun un(s: String?) = s.orEmpty().replace("\\n", "\n").replace("\\,", ",").replace("\\;", ";").replace("\\\\", "\\")
        val allDay = text.contains("DTSTART;VALUE=DATE")
        fun time(v: String?): LocalDateTime = if (allDay) LocalDate.parse(v!!, DAY).atStartOfDay()
        else ZonedDateTime.of(LocalDateTime.parse(v!!, UTC.withZone(ZoneId.of("UTC"))), ZoneId.of("UTC")).withZoneSameInstant(ZONE).toLocalDateTime()
        CalendarEvent(un(prop("SUMMARY")), time(prop("DTSTART")), time(prop("DTEND")), allDay, un(prop("LOCATION")), un(prop("DESCRIPTION")))
    }.getOrNull()

    fun millis(t: LocalDateTime) = ZonedDateTime.of(t, ZONE).toInstant().toEpochMilli()
}
