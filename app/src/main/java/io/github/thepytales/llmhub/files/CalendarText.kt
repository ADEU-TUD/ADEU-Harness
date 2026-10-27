package io.github.thepytales.llmhub.files

import java.nio.charset.Charset
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Calendar files (.ics) and contacts (.vcf) as a readable summary. */
object CalendarText {
    private const val MAX_ITEMS = 500
    private val DAY = DateTimeFormatter.ofPattern("EEE, dd.MM.yyyy", Locale.GERMANY)
    private val TIME = DateTimeFormatter.ofPattern("EEE, dd.MM.yyyy HH:mm", Locale.GERMANY)

    private data class Prop(val name: String, val params: Map<String, String>, val value: String)

    /** Content lines with continuation lines joined (RFC 5545 folding, vCard 2.1 quoted-printable soft breaks). */
    private fun props(text: String): List<Prop> {
        val lines = mutableListOf<String>()
        for (line in text.replace("\r\n", "\n").split('\n')) {
            if ((line.startsWith(" ") || line.startsWith("\t")) && lines.isNotEmpty()) lines[lines.lastIndex] += line.substring(1)
            else if (lines.isNotEmpty() && lines.last().endsWith("=") && lines.last().contains("QUOTED-PRINTABLE", true)) lines[lines.lastIndex] = lines.last().dropLast(1) + line
            else lines += line
        }
        return lines.filter { ':' in it }.map { l ->
            // The value starts at the first colon outside quotes.
            var quoted = false
            var colon = -1
            for ((i, ch) in l.withIndex()) {
                if (ch == '"') quoted = !quoted
                if (ch == ':' && !quoted) { colon = i; break }
            }
            val head = l.substring(0, colon)
            val parts = head.split(';')
            val params = parts.drop(1).associate { p -> p.substringBefore('=').uppercase() to p.substringAfter('=', "").trim('"') }
            var value = l.substring(colon + 1)
            if (params["ENCODING"].equals("QUOTED-PRINTABLE", true)) {
                val cs = params["CHARSET"]?.let { runCatching { Charset.forName(it) }.getOrNull() } ?: Charsets.UTF_8
                value = String(MailText.quotedPrintable(value, underscore = false), cs)
            }
            Prop(parts[0].substringAfterLast('.').uppercase(), params, value)
        }
    }

    private fun unescape(s: String) = s.replace("\\n", "\n").replace("\\N", "\n").replace("\\,", ",").replace("\\;", ";").replace("\\\\", "\\").trim()

    /** "20261103T140000Z" etc. → German date; all-day dates without time. */
    private fun date(p: Prop?): Pair<String, LocalDateTime?> {
        val v = p?.value?.trim() ?: return "" to null
        return runCatching {
            when {
                p.params["VALUE"] == "DATE" || Regex("^\\d{8}$").matches(v) -> {
                    val d = LocalDate.parse(v.take(8), DateTimeFormatter.BASIC_ISO_DATE)
                    d.format(DAY) + " (ganztägig)" to d.atStartOfDay()
                }
                v.endsWith("Z") -> {
                    val t = LocalDateTime.parse(v.dropLast(1), DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss"))
                    val local = t.atOffset(ZoneOffset.UTC).atZoneSameInstant(ZoneId.of("Europe/Berlin")).toLocalDateTime()
                    local.format(TIME) to local
                }
                else -> {
                    val t = LocalDateTime.parse(v.take(15), DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss"))
                    val zone = p.params["TZID"]?.takeIf { !it.contains("Berlin") && !it.contains("W. Europe", true) }
                    t.format(TIME) + (zone?.let { " ($it)" } ?: "") to t
                }
            }
        }.getOrDefault(v to null)
    }

    fun ics(text: String): String {
        val items = mutableListOf<Pair<LocalDateTime?, String>>()
        var current: MutableList<Prop>? = null
        var kind = ""
        var calendarName: String? = null
        var depth = 0
        for (p in props(text)) {
            when {
                p.name == "BEGIN" && p.value.uppercase() in setOf("VEVENT", "VTODO") && current == null -> { current = mutableListOf(); kind = p.value.uppercase(); depth = 0 }
                p.name == "BEGIN" && current != null -> depth++
                p.name == "END" && current != null && depth > 0 -> depth--
                p.name == "END" && current != null && p.value.uppercase() == kind -> {
                    if (items.size < MAX_ITEMS) items += event(current, kind)
                    current = null
                }
                current != null && depth == 0 -> current += p
                p.name == "X-WR-CALNAME" -> calendarName = unescape(p.value)
            }
        }
        if (items.isEmpty()) return "Die Kalenderdatei enthält keine Termine."
        return buildString {
            append(calendarName?.let { "Kalender „$it“" } ?: "Kalender").append(": ").append(items.size).append(if (items.size == 1) " Eintrag" else " Einträge").append("\n\n")
            items.sortedWith(compareBy(nullsLast()) { it.first }).forEach { append(it.second).append("\n\n") }
        }.trim()
    }

    private fun event(props: List<Prop>, kind: String): Pair<LocalDateTime?, String> {
        fun get(name: String) = props.firstOrNull { it.name == name }
        val (start, at) = date(get("DTSTART"))
        val end = date(get("DTEND")).first
        val due = date(get("DUE")).first
        return at to buildString {
            append("## ").append(get("SUMMARY")?.let { unescape(it.value) }?.ifBlank { null } ?: if (kind == "VTODO") "Aufgabe" else "Termin").append('\n')
            if (kind == "VTODO") append("- Aufgabe").append(get("STATUS")?.let { " (${it.value.lowercase()})" }.orEmpty()).append('\n')
            if (start.isNotEmpty()) append("- Beginn: ").append(start).append('\n')
            if (end.isNotEmpty()) append("- Ende: ").append(end).append('\n')
            if (due.isNotEmpty()) append("- Fällig: ").append(due).append('\n')
            get("LOCATION")?.let { unescape(it.value) }?.takeIf { it.isNotBlank() }?.let { append("- Ort: ").append(it).append('\n') }
            get("RRULE")?.let { append("- Wiederholung: ").append(it.value).append('\n') }
            get("ORGANIZER")?.let { o -> append("- Organisation: ").append(o.params["CN"] ?: o.value.removePrefix("mailto:").removePrefix("MAILTO:")).append('\n') }
            props.filter { it.name == "ATTENDEE" }.takeIf { it.isNotEmpty() }?.let { a ->
                append("- Teilnehmende: ").append(a.take(30).joinToString(", ") { it.params["CN"] ?: it.value.substringAfter(':') }).append(if (a.size > 30) ", …" else "").append('\n')
            }
            get("URL")?.let { append("- Link: ").append(it.value).append('\n') }
            get("DESCRIPTION")?.let { unescape(it.value) }?.takeIf { it.isNotBlank() }?.let { d ->
                append("- Beschreibung: ").append(if (d.length > 600) d.take(600) + " …" else d).append('\n')
            }
        }.trimEnd()
    }

    fun vcf(text: String): String {
        val cards = mutableListOf<List<Prop>>()
        var current: MutableList<Prop>? = null
        for (p in props(text)) {
            when {
                p.name == "BEGIN" && p.value.equals("VCARD", true) -> current = mutableListOf()
                p.name == "END" && p.value.equals("VCARD", true) -> { current?.let { if (cards.size < MAX_ITEMS) cards += it }; current = null }
                else -> current?.add(p)
            }
        }
        if (cards.isEmpty()) return "Die Datei enthält keine Kontakte."
        return buildString {
            append(if (cards.size == 1) "1 Kontakt" else "${cards.size} Kontakte").append("\n\n")
            for (c in cards) {
                fun all(name: String) = c.filter { it.name == name }
                val n = all("N").firstOrNull()?.value?.split(';')?.map { unescape(it) }
                val name = all("FN").firstOrNull()?.let { unescape(it.value) }?.ifBlank { null }
                    ?: n?.let { listOfNotNull(it.getOrNull(3), it.getOrNull(1), it.getOrNull(2), it.getOrNull(0), it.getOrNull(4)).filter { s -> s.isNotBlank() }.joinToString(" ") }
                    ?: "Ohne Namen"
                append("## ").append(name).append('\n')
                all("ORG").firstOrNull()?.let { append("- Organisation: ").append(it.value.split(';').map(::unescape).filter { s -> s.isNotBlank() }.joinToString(", ")).append('\n') }
                all("TITLE").firstOrNull()?.let { append("- Funktion: ").append(unescape(it.value)).append('\n') }
                fun typed(p: Prop): String {
                    val type = (p.params["TYPE"] ?: p.params.keys.firstOrNull { it in setOf("WORK", "HOME", "CELL", "VOICE", "FAX", "INTERNET") }).orEmpty()
                        .lowercase().split(',').mapNotNull { TYPES[it] }.distinct().joinToString(", ")
                    return if (type.isEmpty()) "" else " ($type)"
                }
                all("EMAIL").forEach { append("- E-Mail: ").append(it.value.trim()).append(typed(it)).append('\n') }
                all("TEL").forEach { append("- Telefon: ").append(it.value.removePrefix("tel:").trim()).append(typed(it)).append('\n') }
                all("ADR").forEach { a ->
                    val parts = a.value.split(';').map(::unescape).filter { it.isNotBlank() }
                    if (parts.isNotEmpty()) append("- Adresse: ").append(parts.joinToString(", ")).append(typed(a)).append('\n')
                }
                all("BDAY").firstOrNull()?.let { append("- Geburtstag: ").append(it.value).append('\n') }
                all("URL").forEach { append("- Web: ").append(it.value).append('\n') }
                all("NOTE").firstOrNull()?.let { append("- Notiz: ").append(unescape(it.value).take(400)).append('\n') }
                append('\n')
            }
        }.trim()
    }

    private val TYPES = mapOf("work" to "dienstlich", "home" to "privat", "cell" to "mobil", "fax" to "Fax", "pref" to "bevorzugt")
}
