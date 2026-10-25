package io.github.thepytales.llmhub.research

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The citation styles of the reference list. Every style sorts alphabetically by the first author (as the
 * user asked for, also IEEE, which then numbers in that order). Titles of books and journals are set in
 * *italics* (Markdown), so the list can go straight into a document.
 */
enum class CitationStyle(
    val label: String,
    val description: String,
    /** Author-year styles mark two works of the same authors and year with a/b. */
    val authorYear: Boolean = true,
    /** Whether the place of publication belongs into the reference (APA 7 and MLA leave it out). */
    val usesPlace: Boolean = true,
    val numbered: Boolean = false,
) {
    APA7_DE("APA 7 (deutsch, DGPs)", "Psychologie, Erziehungswissenschaft – Richtlinien der DGPs", usesPlace = false),
    APA7_EN("APA 7 (englisch)", "Internationale Zeitschriften, englische Arbeiten", usesPlace = false),
    ZFPAED("Erziehungswissenschaft (Beltz/ZfPäd)", "Zeitschrift für Pädagogik, Beltz Juventa, viele Sammelbände der Bildungsforschung"),
    DIN_ISO_690("DIN ISO 690 (Autor-Jahr)", "Deutsche Norm, häufig in Abschlussarbeiten"),
    HARVARD("Harvard (Cite Them Right)", "Britische Hochschulen, Wirtschaft"),
    CHICAGO("Chicago (Autor-Jahr)", "Geschichte, Sozialwissenschaften"),
    MLA9("MLA 9", "Sprach- und Literaturwissenschaften", authorYear = false, usesPlace = false),
    IEEE("IEEE (nummeriert)", "Technik, Informatik – hier alphabetisch nummeriert", authorYear = false, numbered = true),
    ;

    val german: Boolean get() = this == APA7_DE || this == ZFPAED || this == DIN_ISO_690

    companion object {
        fun parse(name: String?): CitationStyle? {
            val n = name?.trim()?.lowercase()?.replace(Regex("[^a-z0-9]"), "") ?: return null
            if (n.isEmpty()) return null
            return entries.firstOrNull { it.name.lowercase().replace("_", "") == n } ?: when {
                n.startsWith("apa") && ("en" in n || "engl" in n) -> APA7_EN
                n.startsWith("apa") || n.startsWith("dgps") -> APA7_DE
                n.startsWith("din") || n.startsWith("iso") -> DIN_ISO_690
                n.startsWith("harvard") -> HARVARD
                n.startsWith("chicago") -> CHICAGO
                n.startsWith("mla") -> MLA9
                n.startsWith("ieee") -> IEEE
                n.startsWith("zfp") || n.startsWith("beltz") || "paed" in n || "päd" in name.lowercase() || n.startsWith("erziehung") -> ZFPAED
                else -> null
            }
        }
    }
}

/** Formats one reference in a style; [suffix] is the a/b after the year, [number] the position for IEEE. */
object CitationFormatter {
    private const val DASH = "–"

    fun format(r: Reference, style: CitationStyle, suffix: String = "", number: Int = 0): String {
        if (r.unparsed) return r.raw.trim()
        val text = when (style) {
            CitationStyle.APA7_DE, CitationStyle.APA7_EN -> apa(r, style == CitationStyle.APA7_DE, suffix)
            CitationStyle.ZFPAED -> zfpaed(r, suffix)
            CitationStyle.DIN_ISO_690 -> din(r, suffix)
            CitationStyle.HARVARD -> harvard(r, suffix)
            CitationStyle.CHICAGO -> chicago(r, suffix)
            CitationStyle.MLA9 -> mla(r)
            CitationStyle.IEEE -> "[$number] " + ieee(r)
        }
        return tidy(text)
    }

    /** Removes doubled punctuation and spaces that empty fields leave behind. */
    private fun tidy(s: String) = s.replace(Regex("\\s+"), " ")
        .replace(Regex("\\.\\s*\\."), ".").replace(Regex(",\\s*\\."), ".").replace(Regex("\\s+([.,;:])"), "$1")
        .replace("?.", "?").replace("!.", "!").replace("*.*", "").replace("**", "").replace(" ,", ",").trim()

    /** Title ends with ? or ! – no full stop after it. */
    private fun end(title: String) = if (title.endsWith("?") || title.endsWith("!") || title.endsWith(".")) title else "$title."

    private fun it(s: String) = if (s.isBlank()) "" else "*${s.trim()}*"

    private fun pages(p: String) = p.replace(Regex("\\s*[-–—]+\\s*"), DASH)

    private fun ordinalEdition(e: String, german: Boolean): String {
        val n = e.filter { it.isDigit() }.toIntOrNull() ?: return e
        if (n <= 1) return ""
        if (german) return "$n. Aufl."
        val suffix = when {
            n % 100 in 11..13 -> "th"
            n % 10 == 1 -> "st"
            n % 10 == 2 -> "nd"
            n % 10 == 3 -> "rd"
            else -> "th"
        }
        return "$n$suffix ed."
    }

    private fun doiOrUrl(r: Reference) = when {
        r.doi.isNotBlank() -> "https://doi.org/${r.doi}"
        r.url.isNotBlank() -> r.url
        else -> ""
    }

    private fun accessedDate(r: Reference, pattern: String, locale: Locale): String =
        runCatching { LocalDate.parse(r.accessed.take(10)).format(DateTimeFormatter.ofPattern(pattern, locale)) }.getOrDefault(r.accessed)

    // ── APA 7 ────────────────────────────────────────────────────────────────────────────────────────────────

    private fun apaName(p: Person) = if (p.literal || p.given.isBlank()) p.family else "${p.family}, ${p.initials()}"

    private fun apaNames(list: List<Person>): String = when {
        list.isEmpty() -> ""
        list.size == 1 -> apaName(list[0])
        list.size <= 20 -> list.dropLast(1).joinToString(", ") { apaName(it) } + ", & " + apaName(list.last())
        else -> list.take(19).joinToString(", ") { apaName(it) } + ", … " + apaName(list.last())
    }

    /** Editors inside a chapter reference: initials first ("A. Müller & B. Schmidt"). */
    private fun apaEditorsInline(list: List<Person>): String {
        val names = list.map { p -> if (p.literal || p.given.isBlank()) p.family else "${p.initials()} ${p.family}" }
        return when (names.size) {
            0 -> ""
            1 -> names[0]
            2 -> names[0] + " & " + names[1]
            else -> names.dropLast(1).joinToString(", ") + ", & " + names.last()
        }
    }

    private fun apa(r: Reference, de: Boolean, suffix: String): String {
        val year = "(" + (r.year.ifBlank { if (de) "o. J." else "n.d." }) + (if (r.year.isNotBlank() || suffix.isEmpty()) suffix else "-$suffix") + ")."
        val ed = if (r.editors.size > 1) (if (de) "(Hrsg.)" else "(Eds.)") else (if (de) "(Hrsg.)" else "(Ed.)")
        val edition = ordinalEdition(r.edition, de)
        val link = doiOrUrl(r)
        val who = when {
            r.authors.isNotEmpty() -> apaNames(r.authors)
            r.type == RefType.EDITED_BOOK && r.editors.isNotEmpty() -> apaNames(r.editors) + " " + ed
            else -> ""
        }
        val pp = if (de) "S." else "pp."
        val body = when (r.type) {
            RefType.ARTICLE -> {
                val vol = if (r.volume.isNotBlank()) ", " + it(r.volume) + (if (r.issue.isNotBlank()) "(${r.issue})" else "") else ""
                val p = if (r.pages.isNotBlank()) ", " + pages(r.pages) else ""
                listOf(end(r.title), it(r.container) + vol + p + ".", link)
            }
            RefType.BOOK, RefType.EDITED_BOOK -> {
                val extra = edition.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()
                listOf(end(it(r.title) + extra), r.publisher.ifBlank { r.institution }.let { if (it.isBlank()) "" else "$it." }, link)
            }
            RefType.CHAPTER, RefType.CONFERENCE -> {
                val inPart = buildString {
                    append(if (de) "In " else "In ")
                    if (r.editors.isNotEmpty()) append(apaEditorsInline(r.editors)).append(" ").append(ed).append(", ")
                    append(it(r.container))
                    val details = listOfNotNull(edition.takeIf { it.isNotBlank() }, r.pages.takeIf { it.isNotBlank() }?.let { "$pp ${pages(it)}" })
                    if (details.isNotEmpty()) append(" (").append(details.joinToString(", ")).append(")")
                    append(".")
                }
                listOf(end(r.title), inPart, r.publisher.let { if (it.isBlank()) "" else "$it." }, link)
            }
            RefType.THESIS -> {
                val kind = r.genre.ifBlank { if (de) "Dissertation" else "Doctoral dissertation" }
                listOf(it(r.title) + " [" + listOf(kind, r.institution).filter { it.isNotBlank() }.joinToString(", ") + "].", link)
            }
            RefType.REPORT -> {
                val number = r.genre.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()
                val issuer = r.publisher.ifBlank { r.institution }.takeIf { it.isNotBlank() && who != it }
                listOf(end(it(r.title) + number), issuer?.let { "$it." }.orEmpty(), link)
            }
            RefType.WEB -> {
                val site = r.container.takeIf { it.isNotBlank() && it != who }?.let { "$it." }.orEmpty()
                val retrieved = if (r.accessed.isNotBlank() && r.doi.isBlank()) {
                    if (de) "Abgerufen am ${accessedDate(r, "d. MMMM yyyy", Locale.GERMANY)}, von ${r.url}"
                    else "Retrieved ${accessedDate(r, "MMMM d, yyyy", Locale.US)}, from ${r.url}"
                } else link
                listOf(end(it(r.title)), site, retrieved)
            }
            RefType.OTHER -> listOf(end(if (r.container.isBlank()) it(r.title) else r.title), it(r.container).let { if (it.isBlank()) "" else "$it." }, r.publisher.let { if (it.isBlank()) "" else "$it." }, link)
        }
        // Without author the title moves to the front.
        return if (who.isBlank()) {
            val t = body.first()
            listOf(t, year).plus(body.drop(1)).filter { it.isNotBlank() }.joinToString(" ")
        } else {
            (listOf("$who $year") + body).filter { it.isNotBlank() }.joinToString(" ")
        }
    }

    // ── Erziehungswissenschaft (Beltz / Zeitschrift für Pädagogik) ───────────────────────────────────────────

    private fun zfpName(p: Person) = if (p.literal || p.given.isBlank()) p.family else "${p.family}, ${p.initials()}"

    private fun zfpaed(r: Reference, suffix: String): String {
        val year = "(" + r.year.ifBlank { "o. J." } + suffix + "):"
        val names = r.creators.joinToString("/") { zfpName(it) }
        val ed = if (r.authors.isEmpty() && r.editors.isNotEmpty()) " (Hrsg.)" else ""
        val who = if (names.isBlank()) "" else "$names$ed "
        val placePub = listOf(r.place, r.publisher).filter { it.isNotBlank() }.joinToString(": ")
        val edition = ordinalEdition(r.edition, true)
        val link = doiOrUrl(r)
        val body = when (r.type) {
            RefType.ARTICLE -> {
                val vol = listOf(
                    r.volume.takeIf { it.isNotBlank() },
                    r.issue.takeIf { it.isNotBlank() }?.let { "H. $it" },
                    r.pages.takeIf { it.isNotBlank() }?.let { "S. ${pages(it)}" },
                ).filterNotNull().joinToString(", ")
                "${end(r.title)} In: ${r.container}${if (vol.isNotBlank()) " $vol" else ""}."
            }
            RefType.CHAPTER, RefType.CONFERENCE -> {
                val eds = r.editors.joinToString("/") { zfpName(it) }
                val inPart = "In: " + (if (eds.isNotBlank()) "$eds (Hrsg.): " else "") + end(r.container)
                listOf(end(r.title), inPart, edition.takeIf { it.isNotBlank() }?.let { "$it." }.orEmpty(),
                    (placePub + (if (r.pages.isNotBlank()) ", S. ${pages(r.pages)}" else "")).let { if (it.isBlank()) "" else "$it." })
                    .filter { it.isNotBlank() }.joinToString(" ")
            }
            RefType.THESIS -> listOf(end(r.title), listOf(r.genre.ifBlank { "Dissertation" }, r.institution).filter { it.isNotBlank() }.joinToString(", ") + ".").joinToString(" ")
            RefType.WEB -> listOf(end(r.title), r.container.takeIf { it.isNotBlank() }?.let { "$it." }.orEmpty(), r.url,
                if (r.accessed.isNotBlank()) "[Zugriff: ${accessedDate(r, "dd.MM.yyyy", Locale.GERMANY)}]." else "").filter { it.isNotBlank() }.joinToString(" ")
            else -> listOf(end(r.title), edition.takeIf { it.isNotBlank() }?.let { "$it." }.orEmpty(), placePub.ifBlank { r.institution }.let { if (it.isBlank()) "" else "$it." })
                .filter { it.isNotBlank() }.joinToString(" ")
        }
        val tail = if (r.type != RefType.WEB && link.isNotBlank()) " $link" else ""
        return if (who.isBlank()) "${end(r.title)} ${year.dropLast(1)}. ${body.removePrefix(end(r.title)).trim()}$tail" else "$who$year $body$tail"
    }

    // ── DIN ISO 690 (author-year) ───────────────────────────────────────────────────────────────────────────

    private fun dinNames(list: List<Person>): String {
        if (list.isEmpty()) return ""
        fun first(p: Person) = if (p.literal || p.given.isBlank()) p.family.uppercase() else "${p.family.uppercase()}, ${p.given}"
        fun other(p: Person) = if (p.literal || p.given.isBlank()) p.family.uppercase() else "${p.given} ${p.family.uppercase()}"
        return when {
            list.size == 1 -> first(list[0])
            list.size <= 3 -> first(list[0]) + list.drop(1).dropLast(1).joinToString("") { ", " + other(it) } + " und " + other(list.last())
            else -> first(list[0]) + " et al."
        }
    }

    private fun din(r: Reference, suffix: String): String {
        val names = dinNames(r.creators)
        val ed = if (r.authors.isEmpty() && r.editors.isNotEmpty()) ", Hrsg." else ""
        val year = r.year.ifBlank { "o. J." } + suffix
        val head = if (names.isBlank()) "" else "$names$ed, $year."
        val placePub = listOf(r.place, r.publisher).filter { it.isNotBlank() }.joinToString(": ")
        val edition = ordinalEdition(r.edition, true).replace("Aufl.", "Auflage")
        val link = when {
            r.doi.isNotBlank() -> "Verfügbar unter: https://doi.org/${r.doi}"
            r.url.isNotBlank() -> "Verfügbar unter: ${r.url}"
            else -> ""
        }
        val body = when (r.type) {
            RefType.ARTICLE -> {
                val vol = listOf(
                    r.volume.takeIf { it.isNotBlank() }?.let { "Jg. $it" },
                    r.issue.takeIf { it.isNotBlank() }?.let { "Nr. $it" },
                    r.pages.takeIf { it.isNotBlank() }?.let { "S. ${pages(it)}" },
                ).filterNotNull().joinToString(", ")
                listOf(end(r.title), it(r.container) + ".", if (vol.isNotBlank()) "$vol." else "", r.issn.takeIf { it.isNotBlank() }?.let { "ISSN $it." }.orEmpty(), link)
            }
            RefType.CHAPTER, RefType.CONFERENCE -> {
                val eds = dinNames(r.editors).let { if (it.isBlank()) "" else "$it, Hrsg. " }
                listOf(end(r.title), "In: $eds${it(r.container)}.", edition.takeIf { it.isNotBlank() }?.let { "$it." }.orEmpty(),
                    (placePub + (if (r.pages.isNotBlank()) ", S. ${pages(r.pages)}" else "")).let { if (it.isBlank()) "" else "$it." },
                    r.isbn.takeIf { it.isNotBlank() }?.let { "ISBN $it." }.orEmpty(), link)
            }
            RefType.THESIS -> listOf(end(it(r.title)), listOf(r.genre.ifBlank { "Dissertation" }, r.institution).filter { it.isNotBlank() }.joinToString(". ") + ".", link)
            RefType.WEB -> listOf(it(r.title) + " [online].", r.container.takeIf { it.isNotBlank() }?.let { "$it." }.orEmpty(),
                if (r.accessed.isNotBlank()) "[Zugriff am: ${accessedDate(r, "d. MMMM yyyy", Locale.GERMANY)}]." else "", link)
            else -> listOf(end(it(r.title)), edition.takeIf { it.isNotBlank() }?.let { "$it." }.orEmpty(),
                placePub.ifBlank { r.institution }.let { if (it.isBlank()) "" else "$it." }, r.isbn.takeIf { it.isNotBlank() }?.let { "ISBN $it." }.orEmpty(), link)
        }
        return if (head.isBlank()) (listOf(body.first(), "$year.") + body.drop(1)).filter { it.isNotBlank() }.joinToString(" ")
        else (listOf(head) + body).filter { it.isNotBlank() }.joinToString(" ")
    }

    // ── Harvard (Cite Them Right) ───────────────────────────────────────────────────────────────────────────

    private fun harvardName(p: Person) = if (p.literal || p.given.isBlank()) p.family else "${p.family}, ${p.initials(withSpace = false)}"

    private fun harvardNames(list: List<Person>) = when {
        list.isEmpty() -> ""
        list.size == 1 -> harvardName(list[0])
        list.size <= 3 -> list.dropLast(1).joinToString(", ") { harvardName(it) } + " and " + harvardName(list.last())
        else -> harvardName(list[0]) + " et al."
    }

    private fun harvard(r: Reference, suffix: String): String {
        val names = harvardNames(r.creators)
        val ed = if (r.authors.isEmpty() && r.editors.isNotEmpty()) (if (r.editors.size > 1) " (eds.)" else " (ed.)") else ""
        val year = "(" + r.year.ifBlank { "no date" } + suffix + ")"
        val placePub = listOf(r.place, r.publisher).filter { it.isNotBlank() }.joinToString(": ")
        val edition = ordinalEdition(r.edition, false).replace(" ed.", " edn")
        val available = when {
            r.doi.isNotBlank() -> "Available at: https://doi.org/${r.doi}"
            r.url.isNotBlank() -> "Available at: ${r.url}" + (if (r.accessed.isNotBlank()) " (Accessed: ${accessedDate(r, "d MMMM yyyy", Locale.UK)})" else "")
            else -> ""
        }
        val body = when (r.type) {
            RefType.ARTICLE -> {
                val vol = r.volume + (if (r.issue.isNotBlank()) "(${r.issue})" else "")
                "‘${r.title}’, " + it(r.container) + (if (vol.isNotBlank()) ", $vol" else "") + (if (r.pages.isNotBlank()) ", pp. ${pages(r.pages)}" else "") + "."
            }
            RefType.CHAPTER, RefType.CONFERENCE -> {
                val eds = harvardNames(r.editors).let { if (it.isBlank()) "" else "$it (${if (r.editors.size > 1) "eds." else "ed."}) " }
                "‘${r.title}’, in $eds${it(r.container)}." + (if (edition.isNotBlank()) " $edition." else "") +
                    (if (placePub.isNotBlank()) " $placePub" else "") + (if (r.pages.isNotBlank()) ", pp. ${pages(r.pages)}" else "") + "."
            }
            RefType.THESIS -> end(it(r.title)) + " " + listOf(r.genre.ifBlank { "PhD thesis" }, r.institution).filter { it.isNotBlank() }.joinToString(". ") + "."
            RefType.WEB -> end(it(r.title))
            else -> end(it(r.title)) + (if (edition.isNotBlank()) " $edition." else "") + placePub.ifBlank { r.institution }.let { if (it.isBlank()) "" else " $it." }
        }
        val who = if (names.isBlank()) "" else "$names$ed $year"
        val text = if (who.isBlank()) "${body.trimEnd('.')} $year." else "$who $body"
        return listOf(text, if (available.isNotBlank()) "$available." else "").filter { it.isNotBlank() }.joinToString(" ")
    }

    // ── Chicago author-date ─────────────────────────────────────────────────────────────────────────────────

    private fun chicagoNames(list: List<Person>): String {
        if (list.isEmpty()) return ""
        fun first(p: Person) = if (p.literal || p.given.isBlank()) p.family else "${p.family}, ${p.given}"
        fun other(p: Person) = p.display
        val shown = if (list.size > 10) list.take(7) else list
        val joined = when (shown.size) {
            1 -> first(shown[0])
            2 -> first(shown[0]) + ", and " + other(shown[1])
            else -> first(shown[0]) + shown.drop(1).dropLast(1).joinToString("") { ", " + other(it) } + ", and " + other(shown.last())
        }.replace(", and ", if (shown.size == 2) " and " else ", and ")
        return if (list.size > 10) "$joined, et al." else joined
    }

    private fun chicagoEditorsInline(list: List<Person>) = when (list.size) {
        0 -> ""
        1 -> list[0].display
        2 -> list[0].display + " and " + list[1].display
        else -> list.dropLast(1).joinToString(", ") { it.display } + ", and " + list.last().display
    }

    private fun chicago(r: Reference, suffix: String): String {
        val names = chicagoNames(r.creators)
        val ed = if (r.authors.isEmpty() && r.editors.isNotEmpty()) (if (r.editors.size > 1) ", eds." else ", ed.") else ""
        val year = r.year.ifBlank { "n.d." } + suffix
        val placePub = listOf(r.place, r.publisher).filter { it.isNotBlank() }.joinToString(": ")
        val edition = ordinalEdition(r.edition, false)
        val link = doiOrUrl(r)
        val body = when (r.type) {
            RefType.ARTICLE -> "“${end(r.title)}” " + it(r.container) + (if (r.volume.isNotBlank()) " ${r.volume}" else "") +
                (if (r.issue.isNotBlank()) " (${r.issue})" else "") + (if (r.pages.isNotBlank()) ": ${pages(r.pages)}" else "") + "."
            RefType.CHAPTER, RefType.CONFERENCE -> "“${end(r.title)}” In " + it(r.container) +
                (if (r.editors.isNotEmpty()) ", edited by ${chicagoEditorsInline(r.editors)}" else "") +
                (if (r.pages.isNotBlank()) ", ${pages(r.pages)}" else "") + "." + (if (placePub.isNotBlank()) " $placePub." else "")
            RefType.THESIS -> "“${end(r.title)}” " + listOf(r.genre.ifBlank { "PhD diss." }, r.institution).filter { it.isNotBlank() }.joinToString(", ") + "."
            RefType.WEB -> "“${end(r.title)}” " + r.container.let { if (it.isBlank()) "" else "$it." } +
                (if (r.accessed.isNotBlank()) " Accessed ${accessedDate(r, "MMMM d, yyyy", Locale.US)}." else "")
            else -> end(it(r.title)) + (if (edition.isNotBlank()) " $edition" else "") + placePub.ifBlank { r.institution }.let { if (it.isBlank()) "" else " $it." }
        }
        val head = if (names.isBlank()) "" else "$names$ed. $year."
        val text = if (head.isBlank()) "${body.trimEnd('.')}. $year." else "$head $body"
        return listOf(text, if (link.isNotBlank()) "$link." else "").filter { it.isNotBlank() }.joinToString(" ")
    }

    // ── MLA 9 ───────────────────────────────────────────────────────────────────────────────────────────────

    private fun mlaNames(list: List<Person>): String = when {
        list.isEmpty() -> ""
        list.size == 1 -> list[0].let { if (it.literal || it.given.isBlank()) it.family else "${it.family}, ${it.given}" }
        list.size == 2 -> list[0].let { if (it.literal || it.given.isBlank()) it.family else "${it.family}, ${it.given}" } + ", and " + list[1].display
        else -> list[0].let { if (it.literal || it.given.isBlank()) it.family else "${it.family}, ${it.given}" } + ", et al"
    }

    private fun mla(r: Reference): String {
        val names = mlaNames(r.creators)
        val ed = if (r.authors.isEmpty() && r.editors.isNotEmpty()) (if (r.editors.size > 1) ", editors" else ", editor") else ""
        val link = when {
            r.doi.isNotBlank() -> "https://doi.org/${r.doi}"
            r.url.isNotBlank() -> r.url.removePrefix("https://").removePrefix("http://")
            else -> ""
        }
        val edition = ordinalEdition(r.edition, false)
        val parts = when (r.type) {
            RefType.ARTICLE -> listOf(
                "“${end(r.title)}”",
                listOf(
                    it(r.container), r.volume.takeIf { it.isNotBlank() }?.let { "vol. $it" }, r.issue.takeIf { it.isNotBlank() }?.let { "no. $it" },
                    r.year.takeIf { it.isNotBlank() }, r.pages.takeIf { it.isNotBlank() }?.let { "pp. ${pages(it)}" }, link.takeIf { it.isNotBlank() },
                ).filter { !it.isNullOrBlank() }.joinToString(", ") + ".",
            )
            RefType.CHAPTER, RefType.CONFERENCE -> listOf(
                "“${end(r.title)}”",
                listOf(
                    it(r.container), r.editors.takeIf { it.isNotEmpty() }?.let { "edited by " + chicagoEditorsInline(it) },
                    r.publisher.takeIf { it.isNotBlank() }, r.year.takeIf { it.isNotBlank() },
                    r.pages.takeIf { it.isNotBlank() }?.let { "pp. ${pages(it)}" }, link.takeIf { it.isNotBlank() },
                ).filter { !it.isNullOrBlank() }.joinToString(", ") + ".",
            )
            RefType.THESIS -> listOf(end(it(r.title)), r.year.let { if (it.isBlank()) "" else "$it." },
                listOf(r.institution, r.genre.ifBlank { "PhD dissertation" }).filter { it.isNotBlank() }.joinToString(", ") + ".")
            RefType.WEB -> listOf("“${end(r.title)}”", listOf(it(r.container), r.year, link).filter { it.isNotBlank() }.joinToString(", ") + ".",
                if (r.accessed.isNotBlank()) "Accessed ${accessedDate(r, "d MMM. yyyy", Locale.US)}." else "")
            else -> listOf(end(it(r.title)), listOf(edition, r.publisher.ifBlank { r.institution }, r.year, link).filter { it.isNotBlank() }.joinToString(", ") + ".")
        }
        val head = if (names.isBlank()) "" else "$names$ed."
        return (listOf(head) + parts).filter { it.isNotBlank() && it != "." }.joinToString(" ")
    }

    // ── IEEE ────────────────────────────────────────────────────────────────────────────────────────────────

    private fun ieeeName(p: Person) = if (p.literal || p.given.isBlank()) p.family else "${p.initials()} ${p.family}"

    private fun ieeeNames(list: List<Person>) = when {
        list.isEmpty() -> ""
        list.size > 6 -> ieeeName(list[0]) + " et al."
        list.size == 1 -> ieeeName(list[0])
        list.size == 2 -> ieeeName(list[0]) + " and " + ieeeName(list[1])
        else -> list.dropLast(1).joinToString(", ") { ieeeName(it) } + ", and " + ieeeName(list.last())
    }

    private fun ieee(r: Reference): String {
        val names = ieeeNames(r.creators) + if (r.authors.isEmpty() && r.editors.isNotEmpty()) (if (r.editors.size > 1) ", Eds." else ", Ed.") else ""
        val placePub = listOf(r.place, r.publisher).filter { it.isNotBlank() }.joinToString(": ")
        val edition = ordinalEdition(r.edition, false)
        val year = r.year.ifBlank { "n.d." }
        val doi = if (r.doi.isNotBlank()) "doi: ${r.doi}." else ""
        val text = when (r.type) {
            RefType.ARTICLE -> "$names, “${r.title},” " + listOf(
                it(r.container), r.volume.takeIf { it.isNotBlank() }?.let { "vol. $it" }, r.issue.takeIf { it.isNotBlank() }?.let { "no. $it" },
                r.pages.takeIf { it.isNotBlank() }?.let { "pp. ${pages(it)}" }, year,
            ).filter { !it.isNullOrBlank() }.joinToString(", ") + ", $doi"
            RefType.CHAPTER, RefType.CONFERENCE -> "$names, “${r.title},” in " + it(r.container) +
                (if (r.editors.isNotEmpty()) ", " + ieeeNames(r.editors) + (if (r.editors.size > 1) ", Eds." else ", Ed.") else "") +
                (if (placePub.isNotBlank()) " $placePub" else "") + ", $year" + (if (r.pages.isNotBlank()) ", pp. ${pages(r.pages)}" else "") + ". $doi"
            RefType.THESIS -> "$names, “${r.title},” " + listOf(r.genre.ifBlank { "Ph.D. dissertation" }, r.institution, r.place, year).filter { it.isNotBlank() }.joinToString(", ") + "."
            RefType.WEB -> "$names, “${r.title},” " + (if (r.container.isNotBlank()) "${r.container}. " else "") +
                (if (r.accessed.isNotBlank()) "Accessed: ${accessedDate(r, "MMM. d, yyyy", Locale.US)}. " else "") + "[Online]. Available: ${r.url.ifBlank { r.doiUrl }}"
            else -> "$names, " + it(r.title) + (if (edition.isNotBlank()) ", $edition" else "") + ". " +
                (if (placePub.isNotBlank()) "$placePub, " else "") + "$year. $doi"
        }
        return text.removePrefix(", ").trim()
    }
}
