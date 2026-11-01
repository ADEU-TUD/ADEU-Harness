package io.github.thepytales.llmhub.agent

import io.github.thepytales.llmhub.data.AppMap
import io.github.thepytales.llmhub.data.AppTarget
import io.github.thepytales.llmhub.data.AppTool
import io.github.thepytales.llmhub.data.MapKind
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import io.github.thepytales.llmhub.ui.tr

/**
 * What Lora notices on the device, without any request (AP-34, AP-35): where the user is and what fits there, next
 * steps under an answer (a deadline for the calendar, a table for Excel …), a matching place of the app while typing,
 * and a readable line for the step the agent is working on. Pure functions, so they are easy to test.
 */
object LoraSense {

    /**
     * A wish to tap in Lora's panel: [target] (an id of the AppMap) opens that place right away; [inChat] sends [wish]
     * as a message into the open chat; otherwise [wish] goes to Lora like a typed one.
     */
    data class Suggestion(val label: String, val wish: String, val target: String? = null, val inChat: Boolean = false)

    /** The area the user is in, from the route of the screen ("settings/appearance", "chat?id=…", "pdf" …). */
    fun place(route: String?): String {
        val r = route.orEmpty().substringBefore('?')
        val section = r.substringAfter("settings/", "").substringBefore('/')
        return when {
            r.isEmpty() || r == "chat" -> trLater("Chat")
            r == "settings" || r.startsWith("settings/{") -> trLater("Einstellungen")
            section.isNotEmpty() -> trLater("Einstellungen › ") + (AppMap.byId("settings.$section")?.title ?: section)
            r == "templates" || r == "creative" -> trLater("Erstellen")
            r.startsWith("design/") -> trLater("Design")
            r == "literature" -> trLater("Literatur")
            r == "transcripts" || r.startsWith("transcript") -> trLater("Transkription")
            r == "docs" || r == "files" -> trLater("Dateien")
            r.startsWith("doc/") -> trLater("Dokument")
            r == "images" -> trLater("Bilder erzeugen")
            r == "tasks" -> trLater("Geplante Aufgaben")
            r == "pdf" -> trLater("PDF-Werkzeugkasten")
            r == "vault" -> trLater("Tresor")
            r == "models" -> trLater("Modellübersicht")
            r == "compare" -> trLater("Modelle vergleichen")
            else -> AppMap.build().firstOrNull { it.route == r }?.title ?: trLater("App")
        }
    }

    /**
     * Two or three wishes that fit the area of [route]; [inChat] = a chat with messages is open. Most of them Lora
     * answers on the device (a place, the help), so a tap is instant.
     */
    fun suggestions(route: String?, inChat: Boolean, enabledTools: Set<String>, android: Boolean): List<Suggestion> {
        val r = route.orEmpty().substringBefore('?')
        val pdfOn = AppTool.PDF.id in enabledTools
        val list = when {
            r.isEmpty() || r == "chat" -> if (inChat) listOf(
                Suggestion(trLater("Chat zusammenfassen"), "Fasse diesen Chat in wenigen Stichpunkten zusammen.", inChat = true),
                Suggestion(trLater("Als Word-Dokument"), "Mach aus deiner letzten Antwort ein Word-Dokument (docx).", inChat = true),
                Suggestion(trLater("Standardmodell"), "Standardmodell", "option.chat.defaultModel"),
            ) else listOf(
                Suggestion(trLater("Literatur finden"), "Literatursuche", "mode.literature"),
                Suggestion(if (pdfOn) trLater("PDFs bearbeiten") else trLater("PDF-Werkzeugkasten"), "PDF zusammenfügen", if (pdfOn) "tool.pdf" else null),
                Suggestion(trLater("Brief schreiben"), "Brief", "template.b_tud_letter"),
            )
            r.startsWith("settings") -> listOf(
                Suggestion(trLater("Hell oder dunkel"), "Dunkles Design", "option.appearance.theme"),
                Suggestion(trLater("Schriftgröße"), "Schriftgröße", "option.appearance.textsize"),
                Suggestion(trLater("Namen schützen"), "Wie funktioniert die Pseudonymisierung?"),
            )
            r == "templates" || r == "creative" -> listOfNotNull(
                // Erstellen (AP-24): on Windows Lora also designs.
                Suggestion(trLater("Poster gestalten lassen"), "Gestalten", "mode.design").takeIf { !android && ChatModes.visible().any { it.id == ChatModes.DESIGN.id } },
                Suggestion(trLater("Antwort an Studierende"), "Mail an Studierende", "template.b_mail_student"),
                Suggestion(trLater("Vorlage Schritt für Schritt"), "Wie geht eine Vorlage mit Lora?"),
                Suggestion(trLater("Eigene Vorlage"), "Wie lege ich eine eigene Vorlage an?").takeIf { android || ChatModes.visible().none { it.id == ChatModes.DESIGN.id } },
            )
            r == "literature" -> listOf(
                Suggestion(trLater("Exzerpt erstellen"), "Exzerpt", "mode.excerpt"),
                Suggestion(trLater("Zitierstil ändern"), "Zitierstil", "settings.literature"),
                Suggestion(trLater("Literatur suchen"), "Literatursuche", "mode.literature"),
            )
            r == "transcripts" || r.startsWith("transcript") -> listOf(
                Suggestion(trLater("Interview transkribieren"), "Wie transkribiere ich ein Interview?"),
                Suggestion(trLater("Spracherkennung"), "Transkription", "settings.transcription"),
                Suggestion(trLater("Namen schützen"), "Pseudonymisierung", "option.privacy.pseudonymize"),
            )
            r == "pdf" -> listOf(
                Suggestion(trLater("Wie geht zusammenfügen?"), "Wie füge ich PDFs zusammen?"),
                Suggestion("PDF zusammenfassen", "Erklär mir, wie ich ein PDF im Chat zusammenfassen lasse"),
                Suggestion(trLater("Formular ausfüllen"), "Wie fülle ich ein PDF-Formular aus?"),
            )
            r == "vault" -> listOf(
                Suggestion(trLater("Wie sicher ist der Tresor?"), "Wie funktioniert der Tresor?"),
                Suggestion(trLater("Lokale Modelle"), "Modelle auf diesem Gerät", "area.local"),
            )
            r == "docs" || r == "files" -> listOf(
                Suggestion(trLater("Wo liegen meine Dateien?"), "Erklär mir den Lora-Ordner und wie ich ihn verschiebe"),
                Suggestion(trLater("Datei finden"), "Wie finde ich eine Datei über ihren Inhalt?"),
            )
            r.startsWith("doc/") -> listOf(
                Suggestion(trLater("Änderungen nachverfolgen"), "Wie verfolge ich Änderungen im Dokument nach?"),
                Suggestion(trLater("Dokumente & Logos"), "Dokumente & Logos", "settings.documents"),
                Suggestion(trLater("Absender ändern"), "Absender", "option.documents.sender"),
            )
            r == "images" -> listOf(
                Suggestion(trLater("Bild für eine Folie"), "Erklär mir, wie ich ein gutes Bild für eine Folie erzeuge"),
                Suggestion(trLater("Eigene Logos"), "Logos", "option.documents.logo"),
            )
            r == "tasks" -> listOf(
                Suggestion(trLater("Wie geht eine Aufgabe?"), "Wie lege ich eine geplante Aufgabe an?"),
                Suggestion(trLater("Benachrichtigungen"), "Wie geht Benachrichtigungen?"),
            )
            else -> emptyList()
        }
        return (list + Suggestion(trLater("Was kann ich hier tun?"), "Wie geht ${place(route)}?")).distinctBy { it.wish }.take(4)
    }

    // ── Next steps under an answer (AP-35) ──────────────────────────────────────────────────────────────────

    /** A deadline found in an answer: what it is for and the day. */
    data class Deadline(val title: String, val date: LocalDate)

    /** What Lora offers under an answer. */
    sealed interface NextStep {
        data class Calendar(val deadlines: List<Deadline>) : NextStep
        data class Excel(val table: String) : NextStep
        data object Word : NextStep
        data class Literature(val dois: List<String>) : NextStep
    }

    private val MONTHS = listOf("januar", "februar", "marz", "april", "mai", "juni", "juli", "august", "september", "oktober", "november", "dezember")
    private val DEADLINE_WORDS = Regex(
        "(frist|deadline|einreich|abgabe|stichtag|bewerbungsschluss|antragsschluss|einsendeschluss|bis zum|bis spätestens|bis spaetestens|endet|läuft bis|laeuft bis|termin|skizze)",
        RegexOption.IGNORE_CASE,
    )
    private val NUMERIC = Regex("(?<![\\d.])(\\d{1,2})\\.(\\d{1,2})\\.(\\d{4}|\\d{2})?(?![\\d])")
    private val WORDED = Regex(
        "(?<!\\d)(\\d{1,2})\\.\\s*(Januar|Jan\\.?|Februar|Feb\\.?|März|Maerz|Mär\\.?|April|Apr\\.?|Mai|Juni|Jun\\.?|Juli|Jul\\.?|August|Aug\\.?|September|Sept?\\.?|Oktober|Okt\\.?|November|Nov\\.?|Dezember|Dez\\.?)(\\s+(\\d{4}))?",
        RegexOption.IGNORE_CASE,
    )
    private val ISO = Regex("(?<!\\d)(\\d{4})-(\\d{2})-(\\d{2})(?!\\d)")
    private val NOT_A_DAY = Regex("(abschnitt|kapitel|kap\\.|nr\\.|version|seite|s\\.|§|abs\\.|punkt|ziffer|tabelle|abbildung|abb\\.|tab\\.)\\s*$")
    private val DOI = Regex("\\b10\\.\\d{4,9}/[^\\s\"<>)\\]]+", RegexOption.IGNORE_CASE)

    /** The days in [text] (numeric, written out, ISO), each from [today] on and at most two years ahead. */
    internal fun dates(text: String, today: LocalDate): List<LocalDate> {
        val out = mutableListOf<LocalDate>()
        fun add(y: Int?, m: Int, d: Int) {
            val date = if (y != null) runCatching { LocalDate.of(if (y < 100) 2000 + y else y, m, d) }.getOrNull()
            else runCatching { LocalDate.of(today.year, m, d) }.getOrNull()?.let { if (it.isBefore(today)) it.plusYears(1) else it }
            if (date != null && !date.isBefore(today) && date.isBefore(today.plusYears(2))) out += date
        }
        ISO.findAll(text).forEach { add(it.groupValues[1].toInt(), it.groupValues[2].toInt(), it.groupValues[3].toInt()) }
        NUMERIC.findAll(text).forEach { m ->
            val d = m.groupValues[1].toInt()
            val mo = m.groupValues[2].toInt()
            val year = m.groupValues[3].takeIf { it.isNotEmpty() }?.toInt()
            // „Abschnitt 3.2.“ or „Kap. 4.1.“ is no date.
            val before = text.substring(maxOf(0, m.range.first - 14), m.range.first).lowercase()
            if (year == null && NOT_A_DAY.containsMatchIn(before)) return@forEach
            if (d in 1..31 && mo in 1..12) add(year, mo, d)
        }
        WORDED.findAll(text).forEach { m ->
            val name = AppMap.norm(m.groupValues[2]).removeSuffix(" ").take(3)
            val mo = MONTHS.indexOfFirst { it.startsWith(name) } + 1
            if (mo > 0) add(m.groupValues[4].takeIf { it.isNotEmpty() }?.toInt(), mo, m.groupValues[1].toInt())
        }
        return out.distinct()
    }

    private fun plain(s: String) = s.replace(Regex("[*_`#>|]"), " ").replace(Regex("\\[(.*?)]\\(.*?\\)"), "$1")
        .replace(Regex("\\s+"), " ").trim()

    /** A short title for a deadline: the line without the date, markdown and filler. */
    private fun titleOf(line: String): String {
        var t = plain(line)
        t = t.replace(ISO, "").replace(NUMERIC, "").replace(WORDED, "")
        t = t.replace(Regex("(?i)\\b(frist|deadline|stichtag|bis zum|bis spätestens|einreichung|abgabe)\\b\\s*:?"), "")
        t = t.replace(Regex("^[\\s:–—,;.-]+|[\\s:–—,;(.-]+$"), "").replace(Regex("\\(\\s*\\)"), "").replace(Regex("\\s+"), " ").trim()
        return t.take(70).trimEnd().ifEmpty { "Frist" }
    }

    /**
     * Deadlines in an answer: lines with a date and a word like „Frist“, „Einreichung“, „bis zum“, and in tables the
     * rows whose column is called „Frist“, „Deadline“, „Datum“ or „Termin“ (the row's first cells are the title).
     */
    fun deadlines(answer: String, today: LocalDate = LocalDate.now()): List<Deadline> {
        val found = mutableListOf<Deadline>()
        val lines = answer.lines()
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            if (line.trimStart().startsWith("|")) {
                // A table: the header decides which column holds the dates.
                val rows = mutableListOf<List<String>>()
                while (i < lines.size && lines[i].trimStart().startsWith("|")) {
                    val cells = lines[i].trim().trim('|').split('|').map { it.trim() }
                    if (cells.none { it.matches(Regex(":?-{2,}:?")) }) rows += cells
                    i++
                }
                val header = rows.firstOrNull() ?: continue
                val col = header.indexOfFirst { DEADLINE_WORDS.containsMatchIn(it) || Regex("(?i)(datum|zeitraum|wann)").containsMatchIn(it) }
                rows.drop(1).forEach { row ->
                    val cell = if (col >= 0) row.getOrNull(col).orEmpty() else row.joinToString(" ")
                    val hasWord = col >= 0 || DEADLINE_WORDS.containsMatchIn(row.joinToString(" "))
                    if (hasWord) dates(cell, today).firstOrNull()?.let { d ->
                        val name = row.filterIndexed { k, _ -> k != col }.take(2).joinToString(" – ") { plain(it) }.take(70)
                        found += Deadline(name.ifBlank { titleOf(cell) }, d)
                    }
                }
                continue
            }
            if (DEADLINE_WORDS.containsMatchIn(line)) {
                dates(line, today).firstOrNull()?.let { d -> found += Deadline(titleOf(line), d) }
            }
            i++
        }
        return found.distinctBy { it.title.lowercase() to it.date }.take(8)
    }

    /** The first markdown table of [answer] with at least a header and two rows, or null. */
    fun table(answer: String): String? {
        val blocks = mutableListOf<MutableList<String>>()
        var current: MutableList<String>? = null
        for (l in answer.lines()) {
            if (l.trimStart().startsWith("|")) {
                if (current == null) current = mutableListOf<String>().also { blocks += it }
                current += l
            } else current = null
        }
        return blocks.firstOrNull { b -> b.size >= 4 && b.any { it.contains("---") } }?.joinToString("\n")
    }

    /** DOIs in [answer], cleaned and without duplicates. */
    fun dois(answer: String): List<String> =
        DOI.findAll(answer).map { it.value.trimEnd('.', ',', ';', ':') }.distinctBy { it.lowercase() }.take(12).toList()

    /** At most three next steps for an answer, the most useful first. */
    fun nextSteps(answer: String, today: LocalDate = LocalDate.now()): List<NextStep> {
        if (answer.isBlank()) return emptyList()
        val steps = mutableListOf<NextStep>()
        deadlines(answer, today).takeIf { it.isNotEmpty() }?.let { steps += NextStep.Calendar(it) }
        table(answer)?.let { steps += NextStep.Excel(it) }
        dois(answer).takeIf { it.isNotEmpty() }?.let { steps += NextStep.Literature(it) }
        if (answer.length > 1200) steps += NextStep.Word
        return steps.take(3)
    }

    /** The label of a next step's chip. */
    fun label(step: NextStep): String = when (step) {
        is NextStep.Calendar -> if (step.deadlines.size == 1) {
            tr("Frist {0} in den Kalender", step.deadlines[0].date.format(DateTimeFormatter.ofPattern("d.M.", Locale.GERMANY)))
        } else tr("{0} Fristen in den Kalender", step.deadlines.size)
        is NextStep.Excel -> tr("Tabelle als Excel")
        NextStep.Word -> tr("Als Word-Dokument")
        is NextStep.Literature -> if (step.dois.size == 1) "In Literatur übernehmen" else "${step.dois.size} Titel in Literatur übernehmen"
    }

    // ── The step the agent works on (AP-35) ─────────────────────────────────────────────────────────────────

    private fun arg(args: String, vararg keys: String): String? = keys.firstNotNullOfOrNull { k ->
        Regex("\"$k\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)").find(args)?.groupValues?.get(1)
            ?.replace("\\\"", "\"")?.replace("\\n", " ")?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun host(url: String) = url.substringAfter("://").substringBefore('/').removePrefix("www.")

    private fun quote(s: String, max: Int = 48) = "„" + (if (s.length > max) s.take(max).trimEnd() + "…" else s) + "“"

    /**
     * A readable line for the step the agent is on, e.g. „Sucht im Web: „Ausschreibungen Geisteswissenschaften““ –
     * from the tool [name] and its (possibly still growing) JSON [args]. [fallback] is the plain tool label.
     */
    fun stepLine(name: String, args: String, fallback: String): String {
        val q = arg(args, "query", "q", "suchbegriff", "search")
        val url = arg(args, "url")
        val title = arg(args, "title", "file_name", "subject", "name")
        val path = arg(args, "path", "file", "name")
        return when (name) {
            "web_search" -> q?.let { tr("Sucht im Web: {0}", quote(it)) } ?: tr("Sucht im Web …")
            "news_search" -> q?.let { tr("Sucht Nachrichten: {0}", quote(it)) } ?: tr("Sucht Nachrichten …")
            "fetch_url" -> url?.let { tr("Liest {0}", host(it)) } ?: tr("Liest eine Webseite …")
            "search_literature" -> q?.let { tr("Sucht Literatur: {0}", quote(it)) } ?: tr("Sucht Literatur …")
            "search_knowledge" -> q?.let { tr("Durchsucht die Wissenssammlung: {0}", quote(it)) } ?: tr("Durchsucht die Wissenssammlung …")
            "search_chats" -> q?.let { tr("Erinnert sich an frühere Chats: {0}", quote(it)) } ?: tr("Durchsucht frühere Chats …")
            "create_document" -> title?.let { tr("Schreibt {0}", quote(it)) } ?: tr("Schreibt ein Dokument …")
            "edit_document" -> tr("Überarbeitet das Dokument …")
            "create_email_draft" -> title?.let { tr("Entwirft die Mail {0}", quote(it)) } ?: tr("Entwirft eine Mail …")
            "create_calendar_event" -> title?.let { tr("Trägt {0} ein", quote(it)) } ?: tr("Legt einen Termin an …")
            "create_chart" -> title?.let { tr("Zeichnet das Diagramm {0}", quote(it)) } ?: tr("Zeichnet ein Diagramm …")
            "run_javascript" -> tr("Rechnet nach …")
            "read_file", "read_local_file", "read_document" -> path?.let { tr("Liest {0}", quote(it.substringAfterLast('/').substringAfterLast('\\'))) } ?: tr("Liest eine Datei …")
            "check_references" -> tr("Prüft die Quellen …")
            "ask_user" -> tr("Hat eine Rückfrage …")
            "remember" -> tr("Merkt sich etwas …")
            else -> "$fallback …"
        }
    }

    // ── While typing (AP-35) ────────────────────────────────────────────────────────────────────────────────

    private val QUESTION = Regex("^(erklar|erklaer|was |wer |warum|wieso|weshalb|schreib|formulier|ubersetz|uebersetz|fass|gib mir|nenne|hilf mir|analysier|vergleich|suche|such |recherchier|finde|erstelle|kannst du|bitte)")

    /**
     * The place of the app a short typed text clearly means („dunkles Design“, „Zitierstil“, „PDF zusammenfügen“), or
     * null. Real questions and longer texts are left alone – the chat answers those.
     */
    fun typingHint(text: String, targets: List<AppTarget>): AppTarget? {
        val n = AppMap.norm(text)
        val words = n.split(' ').filter { it.isNotEmpty() }
        if (words.size !in 1..6 || n.length < 4 || QUESTION.containsMatchIn(n) || text.contains('?') && words.size > 4) return null
        val hits = AppMap.search(text, targets, 2)
        val best = hits.firstOrNull() ?: return null
        val second = hits.getOrNull(1)?.score ?: 0
        if (best.score < 12 || best.score < second + 5) return null
        return best.target.takeIf { it.kind != MapKind.TEMPLATE && it.id != "area.chat" }
    }
}
