package io.github.thepytales.llmhub.data

import io.github.thepytales.llmhub.docs.CalendarEvent
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID
import io.github.thepytales.llmhub.ui.trLater

/** A point of the checklist from the call („Lebenslauf beifügen“, „Englisch, höchstens 15 Seiten“). */
@Serializable
data class ProposalCheck(val id: String = UUID.randomUUID().toString(), val text: String, val done: Boolean = false)

/** A deadline (date „2027-02-15“, optionally with time „2027-02-15 14:00“). */
@Serializable
data class ProposalDeadline(val id: String = UUID.randomUUID().toString(), val title: String, val date: String, val note: String = "")

/** A part of the proposal with the funder's limit; its text is written here or in the chat. */
@Serializable
data class ProposalSection(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val guidance: String = "",
    val limitChars: Int? = null,
    val limitWords: Int? = null,
    val limitPages: Double? = null,
    val text: String = "",
)

/** An Arbeitspaket: from month [start] (1 = first month) for [months] months. */
@Serializable
data class WorkPackage(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val lead: String = "",
    val start: Int = 1,
    val months: Int = 6,
    val deliverables: String = "",
)

/** A line of the budget: amounts in euros per project year. */
@Serializable
data class BudgetRow(
    val id: String = UUID.randomUUID().toString(),
    val category: String,
    val item: String,
    val amounts: List<Double> = emptyList(),
    val note: String = "",
) {
    val total: Double get() = amounts.sum()
}

/** A trial review (Probelauf mit Gutachter-Rolle) and where it happened. */
@Serializable
data class ProposalReview(val at: Long = System.currentTimeMillis(), val chatId: String? = null, val summary: String = "")

/**
 * Antrags-Werkstatt (AP-21): one proposal from the call to the submission – checklist and deadlines from the call, the
 * outline after the funder's rules with limits, Arbeitspakete (Gantt), budget, DMP, ethics, trial reviews.
 */
@Serializable
data class Proposal(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val funder: String = Funders.DFG.id,
    /** Programme or call, e.g. „Sachbeihilfe“, „Horizon Europe RIA“, „Richtlinie … vom …“. */
    val program: String = "",
    /** What the call asks for, in short (from reading it). */
    val callSummary: String = "",
    /** Where the call came from: file name or URL. */
    val callSource: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val chatId: String? = null,
    val checklist: List<ProposalCheck> = emptyList(),
    val deadlines: List<ProposalDeadline> = emptyList(),
    val sections: List<ProposalSection> = emptyList(),
    val durationMonths: Int = 36,
    val workPackages: List<WorkPackage> = emptyList(),
    val budget: List<BudgetRow> = emptyList(),
    /** Flat rate on top of the direct costs in percent (DFG Programmpauschale, EU indirect costs …); null = none. */
    val overheadPercent: Double? = null,
    /** For the page watcher: characters (with spaces) per page; 3,000 ≈ 11 pt, single spacing. */
    val charsPerPage: Int = 3000,
    /** Limit of the whole text in pages (if the funder sets one). */
    val pageLimit: Double? = null,
    val dmp: String = "",
    val ethics: String = "",
    val reviews: List<ProposalReview> = emptyList(),
) {
    val years: Int get() = ((durationMonths + 11) / 12).coerceAtLeast(1)
    val directCosts: Double get() = budget.sumOf { it.total }
    val overhead: Double get() = overheadPercent?.let { directCosts * it / 100 } ?: 0.0
    val funderInfo: Funder get() = Funders.byId(funder)
}

/** What a funder expects: outline, checklist, budget categories, flat rate. All values are starting points. */
data class Funder(
    val id: String,
    val name: String,
    val program: String,
    val outline: List<ProposalSection>,
    val checklist: List<String>,
    val budgetCategories: List<String>,
    val overheadPercent: Double? = null,
    val overheadLabel: String = "Pauschale",
    val pageLimit: Double? = null,
    val note: String = "",
)

/**
 * Built-in funders. The outlines follow the usual templates of the funders (state of 2026) and are only a start: the call
 * read by Lora wins, and the checklist says to check the current template.
 */
object Funders {
    private fun s(title: String, guidance: String = "", pages: Double? = null) = ProposalSection(title = title, guidance = guidance, limitPages = pages)

    val DFG = Funder(
        "dfg", "DFG", "Sachbeihilfe",
        outline = listOf(
            s("1 Ausgangslage und eigene Vorarbeiten", "Stand der Forschung, eigene Vorarbeiten mit Publikationen, Bedeutung der Fragestellung"),
            s("2 Ziele und Arbeitsprogramm", "Ziele, Arbeitsprogramm mit Methoden und Zeitplan, Umgang mit Forschungsdaten, Relevanz von Geschlecht und Vielfältigkeit"),
            s("3 Projekt- und Finanzierungsrahmen", "Beantragte Module und Mittel mit Begründung"),
            s("4 Projekt- und Rahmenbedingungen", "Beteiligte, Zusammenarbeit, Ausstattung, Eigenleistungen der Hochschule"),
            s("5 Erläuterungen zu den beantragten Modulen und Mitteln"),
            s("Literaturverzeichnis"),
        ),
        checklist = listOf(
            "Aktuellen Leitfaden und Vordruck (Projektbeschreibung) der DFG herunterladen und Seitenvorgabe prüfen",
            "Antrag im elan-Portal anlegen",
            "Lebenslauf und Publikationsverzeichnis nach DFG-Vorgabe",
            "Umgang mit Forschungsdaten beschreiben (Forschungsdatenmanagement)",
            "Ethische und rechtliche Aspekte (Ethikvotum, Datenschutz) klären",
            "Personalmittel nach den aktuellen DFG-Personalmittelsätzen berechnen",
            "Unterschrift bzw. Zustimmung der Hochschulleitung/Verwaltung (Drittmittelanzeige an der TU Dresden)",
        ),
        budgetCategories = listOf("Personal", "Sachmittel", "Reisen", "Investitionen", "Publikationen", "Sonstiges"),
        overheadPercent = 22.0, overheadLabel = "Programmpauschale (22 %, zahlt die DFG zusätzlich)",
        note = trLater("Seitenzahlen und Gliederung immer mit dem aktuellen DFG-Vordruck abgleichen."),
    )

    val BMBF = Funder(
        "bmbf", "BMBF", "Projektskizze",
        outline = listOf(
            s("Gesamtziel des Vorhabens", "Bezug zur Förderrichtlinie"),
            s("Stand der Wissenschaft und Technik, eigene Vorarbeiten"),
            s("Arbeitsplan", "Arbeitspakete, Meilensteine, Zeitplan"),
            s("Verwertungsplan", "Nutzen, Transfer, Anschlussfähigkeit"),
            s("Projektpartner und Zusammenarbeit"),
            s("Grobe Finanzplanung"),
        ),
        checklist = listOf(
            "Förderrichtlinie (Bundesanzeiger) lesen: Fristen, Umfang der Skizze, Bewertungskriterien",
            "Skizze im Portal easy-Online einreichen",
            "Projektpauschale und Eigenanteil mit der Drittmittelverwaltung klären",
            "Verwertungsplan nicht vergessen",
        ),
        budgetCategories = listOf("Personal", "Sachmittel", "Reisen", "Aufträge an Dritte", "Investitionen", "Sonstiges"),
        overheadPercent = 20.0, overheadLabel = "Projektpauschale (20 % für Hochschulen)",
    )

    val EU = Funder(
        "eu", "EU", "Horizon Europe (RIA/IA)",
        outline = listOf(
            s("1 Excellence", "1.1 Objectives and ambition · 1.2 Methodology"),
            s("2 Impact", "2.1 Pathways towards impact · 2.2 Measures to maximise impact (dissemination, exploitation, communication) · 2.3 Summary"),
            s("3 Quality and efficiency of the implementation", "3.1 Work plan and resources · 3.2 Capacity of participants and consortium as a whole"),
        ),
        checklist = listOf(
            "Call und Work Programme im Funding & Tenders Portal lesen (Topic, Budget, Frist)",
            "Seitenlimit von Part B prüfen (RIA/IA meist 45 Seiten für Teil 1–3)",
            "Part A im Portal ausfüllen (Partner, Budget, Ethik-Selbstbewertung)",
            "Gender Equality Plan der Einrichtung vorhanden",
            "Datenmanagementplan (Open Science) einplanen",
            "Ethics Self-Assessment",
            "Konsortialvereinbarung vorbereiten",
        ),
        budgetCategories = listOf("Personnel", "Subcontracting", "Travel", "Equipment", "Other goods and services", "Other"),
        overheadPercent = 25.0, overheadLabel = "Indirect costs (25 % flat rate)", pageLimit = 45.0,
        note = trLater("Englisch; Bewertung nach Excellence, Impact, Implementation."),
    )

    val SAB = Funder(
        "sab", "SAB", "Förderung Freistaat Sachsen",
        outline = listOf(
            s("Vorhabenbeschreibung"), s("Ziele und erwartete Ergebnisse"), s("Maßnahmen und Arbeitsplan"), s("Zeitplan"),
            s("Kosten- und Finanzierungsplan"), s("Nachhaltigkeit und Verwertung"),
        ),
        checklist = listOf(
            "Förderrichtlinie und Merkblatt der SAB lesen",
            "Antrag im Förderportal der SAB stellen",
            "Kosten- und Finanzierungsplan nach SAB-Vorlage",
            "Eigenanteil und Kofinanzierung klären",
        ),
        budgetCategories = listOf("Personal", "Sachausgaben", "Reisen", "Investitionen", "Sonstiges"),
    )

    val TUD = Funder(
        "tud", trLater("TUD-intern"), "Interne Förderung der TU Dresden",
        outline = listOf(
            s("Ausgangslage und Bedarf"), s("Ziele"), s("Maßnahmen und Zeitplan"), s("Beitrag zu Lehre bzw. Forschung an der TU Dresden"),
            s("Kosten"), s("Nachhaltigkeit"),
        ),
        checklist = listOf("Ausschreibung und Formular der TU Dresden lesen", "Zustimmung der Leitung der Professur/Fakultät", "Kosten mit dem Sachgebiet Drittmittel abstimmen"),
        budgetCategories = listOf("Personal (SHK/WHK)", "Sachmittel", "Reisen", "Sonstiges"),
    )

    val OTHER = Funder(
        "other", trLater("Andere"), "",
        outline = listOf(s("Zusammenfassung"), s("Ausgangslage"), s("Ziele"), s("Arbeitsplan"), s("Ergebnisse und Verwertung"), s("Finanzierung")),
        checklist = listOf("Ausschreibung vollständig lesen: Fristen, Umfang, Formalien, Bewertungskriterien"),
        budgetCategories = listOf("Personal", "Sachmittel", "Reisen", "Sonstiges"),
    )

    val all = listOf(DFG, BMBF, EU, SAB, TUD, OTHER)

    fun byId(id: String?): Funder = all.firstOrNull { it.id.equals(id, ignoreCase = true) || it.name.equals(id, ignoreCase = true) } ?: OTHER

    /** A new proposal with the funder's outline, checklist and flat rate. */
    fun newProposal(title: String, funder: Funder, chatId: String? = null) = Proposal(
        title = title, funder = funder.id, program = funder.program, chatId = chatId,
        sections = funder.outline.map { it.copy(id = UUID.randomUUID().toString()) },
        checklist = funder.checklist.map { ProposalCheck(text = it) },
        overheadPercent = funder.overheadPercent, pageLimit = funder.pageLimit,
    )
}

/** Zeichen-/Seitenwächter and the Abgabe-Check of a proposal (pure functions, tested). */
object ProposalChecks {
    data class Count(val chars: Int, val words: Int, val pages: Double)

    data class Finding(val level: Level, val text: String)
    enum class Level { OK, WARN, ERROR }

    fun count(text: String, charsPerPage: Int): Count {
        val t = text.trim()
        val words = if (t.isEmpty()) 0 else t.split(Regex("\\s+")).size
        return Count(t.length, words, if (charsPerPage > 0) t.length.toDouble() / charsPerPage else 0.0)
    }

    /** Over the limit of [s]: a text like „3.400 von 3.000 Zeichen“, else null. */
    fun overLimit(s: ProposalSection, charsPerPage: Int): String? {
        val c = count(s.text, charsPerPage)
        return when {
            s.limitChars != null && c.chars > s.limitChars -> "${num(c.chars)} von ${num(s.limitChars)} Zeichen"
            s.limitWords != null && c.words > s.limitWords -> "${num(c.words)} von ${num(s.limitWords)} Wörtern"
            s.limitPages != null && c.pages > s.limitPages + 0.001 -> "${pages(c.pages)} von ${pages(s.limitPages)} Seiten"
            else -> null
        }
    }

    /** Share of the limit used (0…), null without a limit – for the bar under a section. */
    fun usage(s: ProposalSection, charsPerPage: Int): Float? {
        val c = count(s.text, charsPerPage)
        return when {
            s.limitChars != null && s.limitChars > 0 -> c.chars.toFloat() / s.limitChars
            s.limitWords != null && s.limitWords > 0 -> c.words.toFloat() / s.limitWords
            s.limitPages != null && s.limitPages > 0 -> (c.pages / s.limitPages).toFloat()
            else -> null
        }
    }

    fun limitLabel(s: ProposalSection): String? = when {
        s.limitChars != null -> "max. ${num(s.limitChars)} Zeichen"
        s.limitWords != null -> "max. ${num(s.limitWords)} Wörter"
        s.limitPages != null -> "max. ${pages(s.limitPages)} Seiten"
        else -> null
    }

    fun num(n: Int): String = "%,d".format(n).replace(',', '.')
    fun pages(p: Double): String = if (p % 1.0 == 0.0) p.toLong().toString() else String.format(java.util.Locale.GERMANY, "%.1f", p)
    fun euro(d: Double): String = String.format(java.util.Locale.GERMANY, "%,.0f €", d)

    /** Days until [date] (negative: past), null when it is not a date. */
    fun daysUntil(date: String, today: LocalDate = LocalDate.now()): Long? =
        io.github.thepytales.llmhub.docs.CalendarFiles.parse(date)?.first?.toLocalDate()?.let { ChronoUnit.DAYS.between(today, it) }

    /** The Abgabe-Check: everything that is still missing or too long, then what is fine. */
    fun check(p: Proposal, today: LocalDate = LocalDate.now()): List<Finding> {
        val out = mutableListOf<Finding>()
        val open = p.checklist.filter { !it.done }
        out += if (open.isEmpty()) Finding(Level.OK, "Checkliste erledigt (${p.checklist.size} Punkte).")
        else Finding(Level.ERROR, "Checkliste: ${open.size} von ${p.checklist.size} Punkten offen – " + open.take(3).joinToString("; ") { it.text } + if (open.size > 3) " …" else "")
        val dated = p.deadlines.mapNotNull { d -> daysUntil(d.date, today)?.let { d to it } }
        dated.filter { it.second in 0..13 }.forEach { (d, n) -> out += Finding(Level.WARN, "Frist „${d.title}“ in $n Tag(en) (${d.date}).") }
        dated.filter { it.second < 0 }.forEach { (d, _) -> out += Finding(Level.WARN, "Frist „${d.title}“ ist vorbei (${d.date}).") }
        if (p.deadlines.isEmpty()) out += Finding(Level.WARN, "Keine Frist eingetragen.")
        p.deadlines.filter { daysUntil(it.date, today) == null }.forEach { out += Finding(Level.WARN, "Frist „${it.title}“ hat kein lesbares Datum („${it.date}“).") }
        val empty = p.sections.filter { it.text.isBlank() }
        if (empty.isNotEmpty()) out += Finding(Level.ERROR, "Noch leer: " + empty.joinToString(", ") { it.title })
        p.sections.forEach { s -> overLimit(s, p.charsPerPage)?.let { out += Finding(Level.ERROR, "„${s.title}“ ist zu lang: $it.") } }
        val total = p.sections.sumOf { count(it.text, p.charsPerPage).pages }
        p.pageLimit?.let { limit -> if (total > limit) out += Finding(Level.ERROR, "Insgesamt etwa ${pages(total)} von ${pages(limit)} Seiten.") }
        if (p.sections.isNotEmpty() && empty.isEmpty() && p.sections.none { overLimit(it, p.charsPerPage) != null }) out += Finding(Level.OK, "Alle Abschnitte geschrieben und im Umfang (etwa ${pages(total)} Seiten).")
        if (p.workPackages.isEmpty()) out += Finding(Level.WARN, "Keine Arbeitspakete angelegt.")
        p.workPackages.filter { it.start < 1 || it.start + it.months - 1 > p.durationMonths }.forEach {
            out += Finding(Level.ERROR, "Arbeitspaket „${it.title}“ liegt außerhalb der Laufzeit (${p.durationMonths} Monate).")
        }
        if (p.workPackages.isNotEmpty()) {
            val covered = (1..p.durationMonths).filter { m -> p.workPackages.any { m >= it.start && m < it.start + it.months } }
            if (covered.size < p.durationMonths) out += Finding(Level.WARN, "In ${p.durationMonths - covered.size} Monat(en) der Laufzeit läuft kein Arbeitspaket.")
        }
        if (p.budget.isEmpty() || p.directCosts <= 0) out += Finding(Level.WARN, "Noch kein Budget.")
        else {
            out += Finding(Level.OK, "Budget: ${euro(p.directCosts)} direkte Kosten" + (if (p.overhead > 0) " + ${euro(p.overhead)} Pauschale" else "") + ".")
            p.budget.filter { it.amounts.size > p.years }.forEach { out += Finding(Level.WARN, "Budgetzeile „${it.item}“ hat mehr Jahre als die Laufzeit.") }
        }
        if (p.dmp.isBlank()) out += Finding(Level.WARN, "Forschungsdatenmanagement (DMP) noch nicht beschrieben – die TU Vorlage „Datenmanagementplan“ hilft.")
        if (p.ethics.isBlank()) out += Finding(Level.WARN, "Ethik/Datenschutz noch nicht geklärt.")
        if (p.reviews.isEmpty()) out += Finding(Level.WARN, "Noch kein Probelauf mit einer Gutachter-Rolle.")
        return out.sortedBy { it.level.ordinal * -1 }
    }

    /** The deadlines as calendar events (all day, reminder a week before). */
    fun events(p: Proposal): List<CalendarEvent> = p.deadlines.mapNotNull { d ->
        val (start, allDay) = io.github.thepytales.llmhub.docs.CalendarFiles.parse(d.date) ?: return@mapNotNull null
        CalendarEvent(
            title = "${p.title}: ${d.title}", start = start, end = if (allDay) start else start.plusHours(1), allDay = allDay,
            description = listOf(p.funderInfo.name + " " + p.program, d.note).filter { it.isNotBlank() }.joinToString("\n"),
            reminderMinutes = 7 * 24 * 60,
        )
    }

    /** The whole proposal as Markdown (for Word): sections, Arbeitspakete with a month bar, budget. */
    fun markdown(p: Proposal): String = buildString {
        append("# ").append(p.title).append("\n\n")
        append("**Förderer:** ").append(p.funderInfo.name).append(if (p.program.isNotBlank()) " – ${p.program}" else "").append("  \n")
        append("**Laufzeit:** ").append(p.durationMonths).append(" Monate\n\n")
        for (s in p.sections) {
            append("## ").append(s.title).append("\n\n")
            append(s.text.trim().ifEmpty { "_(noch nicht geschrieben)_" }).append("\n\n")
        }
        if (p.workPackages.isNotEmpty()) {
            append("## Arbeitspakete und Zeitplan\n\n| AP | Titel | Leitung | Monate | Ergebnisse |\n| --- | --- | --- | --- | --- |\n")
            p.workPackages.forEachIndexed { i, w ->
                append("| AP ${i + 1} | ${cell(w.title)} | ${cell(w.lead)} | ${w.start}–${w.start + w.months - 1} | ${cell(w.deliverables)} |\n")
            }
            append("\n")
            val quarters = (p.durationMonths + 2) / 3
            append("| AP | ").append((1..quarters).joinToString(" | ") { "Q$it" }).append(" |\n| --- | ").append((1..quarters).joinToString(" | ") { "---" }).append(" |\n")
            p.workPackages.forEachIndexed { i, w ->
                append("| AP ${i + 1} | ").append((1..quarters).joinToString(" | ") { q ->
                    val from = (q - 1) * 3 + 1
                    val to = q * 3
                    if (w.start <= to && w.start + w.months - 1 >= from) "■" else ""
                }).append(" |\n")
            }
            append("\n")
        }
        if (p.budget.isNotEmpty()) {
            append("## Finanzplan\n\n| Kategorie | Posten | ").append((1..p.years).joinToString(" | ") { "Jahr $it" }).append(" | Summe |\n")
            append("| --- | --- | ").append((1..p.years).joinToString(" | ") { "---" }).append(" | --- |\n")
            for (r in p.budget) {
                append("| ${cell(r.category)} | ${cell(r.item)} | ").append((0 until p.years).joinToString(" | ") { y -> r.amounts.getOrNull(y)?.let(::euro).orEmpty() }).append(" | ${euro(r.total)} |\n")
            }
            append("| **Direkte Kosten** | | ").append((0 until p.years).joinToString(" | ") { y -> euro(p.budget.sumOf { it.amounts.getOrElse(y) { 0.0 } }) }).append(" | **${euro(p.directCosts)}** |\n")
            if (p.overhead > 0) append("| ${cell(p.funderInfo.overheadLabel)} | | ").append((0 until p.years).joinToString(" | ") { "" }).append(" | ${euro(p.overhead)} |\n")
            append("\n")
        }
        if (p.dmp.isNotBlank()) append("## Umgang mit Forschungsdaten\n\n").append(p.dmp.trim()).append("\n\n")
        if (p.ethics.isNotBlank()) append("## Ethische und rechtliche Aspekte\n\n").append(p.ethics.trim()).append("\n\n")
    }

    private fun cell(s: String) = s.replace("|", "/").replace("\n", " ")
}
