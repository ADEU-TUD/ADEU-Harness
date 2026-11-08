package io.github.thepytales.llmhub.docs

import kotlin.math.abs

/**
 * Lora baut Folien aus einem Paper (AP-51), auf dem Gerät: a deck in the TUD design from the text of a PDF – title
 * slide with the authors, „Worum es geht“ from the abstract, Gliederung, one slide per section (Einleitung, Methode,
 * Ergebnisse, Diskussion …; subsections get their own slide), figures as picture slides, tables as table slides,
 * „Fazit“ and „Vielen Dank!“; the long text of each section goes into the speaker notes. Points appear one by one.
 *
 * The structure comes from the lines of the PDF with their font size ([PdfTools.textRuns]): the largest text on page 1
 * is the title, the lines under it the authors, headings are numbered („2.1 Datenerhebung“), the usual section names
 * (Abstract, Introduction, Methode …) or larger than the body text. Running heads, page numbers, references and the
 * appendix are left out. Without sizes (plain text) the headings come from numbers and names alone.
 */
object PaperDeck {
    /** A line of the paper: page, top and left in points, font height (0: unknown) and its cells (text split at wide gaps). */
    data class Line(val page: Int, val y: Float, val x: Float, val size: Float, val cells: List<String>) {
        val text: String get() = cells.joinToString(" ").trim()
    }

    /** A picture of the paper: page, Markdown path of the stored file, and its size in pixels. */
    data class Figure(val page: Int, val path: String, val width: Int, val height: Int)

    /** A section of the paper with its level (1 = section, 2 = subsection) and text. */
    data class Section(val title: String, val level: Int, val page: Int, val text: String)

    data class Paper(
        val title: String, val authors: List<String>, val abstract: String, val sections: List<Section>,
        val figures: List<Pair<Int, String>>, val tables: List<Triple<Int, String, List<List<String>>>>,
    )

    /** The lines of the PDF from its text runs: runs of a page at the same height are one line, left to right. */
    fun lines(runs: List<PdfTextRun>): List<Line> = runs.groupBy { it.page }.toSortedMap().flatMap { (page, list) ->
        val sorted = list.filter { it.text.isNotBlank() }.sortedWith(compareBy({ it.y }, { it.x }))
        val out = mutableListOf<MutableList<PdfTextRun>>()
        for (r in sorted) {
            val last = out.lastOrNull()
            if (last != null && abs(last.first().y + last.first().h / 2 - (r.y + r.h / 2)) < maxOf(last.first().h, r.h) * 0.5f) last += r else out += mutableListOf(r)
        }
        out.map { l -> val s = l.sortedBy { it.x }; Line(page, s.first().y, s.first().x, s.maxOf { it.h }, s.map { it.text.trim() }) }
    }

    /** Lines of plain text (no sizes): one per text line, pages split at form feeds. */
    fun lines(text: String): List<Line> {
        var page = 0
        var y = 0f
        return text.replace("\r\n", "\n").lines().mapNotNull { l ->
            if ('\u000C' in l) { page++; y = 0f }
            y += 12f
            l.replace("\u000C", "").trim().takeIf { it.isNotEmpty() }?.let { Line(page, y, 0f, 0f, listOf(it)) }
        }
    }

    private val KNOWN = Regex(
        "^(?:(?:\\d+(?:\\.\\d+)*|[IVX]+)\\.?\\s+)?(abstract|zusammenfassung|kurzfassung|summary|introduction|einleitung|einführung|motivation|related work|background|hintergrund|" +
            "forschungsstand|stand der forschung|theory|theoretical background|theoretischer hintergrund|theorie|methods?|methodology|methodik|methoden?|materials? and methods|" +
            "material und methoden?|study design|studiendesign|design|data|daten|datenerhebung|participants|stichprobe|procedure|vorgehen|experiments?|experimental setup|" +
            "results?|ergebnisse|befunde|evaluation|analysis|analyse|auswertung|discussion|diskussion|limitations?|limitationen|grenzen|conclusions?|fazit|schluss|" +
            "schlussfolgerungen?|ausblick|outlook|future work|implications|implikationen|references|literatur|literaturverzeichnis|quellen|bibliography|" +
            "acknowledge?ments?|danksagung|appendix|anhang|funding|förderung|conflicts? of interest|author contributions|data availability)\\s*:?$",
        RegexOption.IGNORE_CASE,
    )
    private val NUMBERED = Regex("^((?:\\d{1,2})(?:\\.\\d{1,2}){0,3})\\.?\\s+(\\p{Lu}[^.!?]{1,90})$")
    private val ROMAN = Regex("^([IVX]{1,5})\\.\\s+(\\p{Lu}.{1,90})$")
    private val SKIP = Regex("^(references|literatur|literaturverzeichnis|quellen|bibliography|acknowledge?ments?|danksagung|appendix|anhang|funding|förderung|conflicts? of interest|author contributions|data availability)", RegexOption.IGNORE_CASE)
    private val ABSTRACT = Regex("^(abstract|zusammenfassung|kurzfassung|summary)\\b", RegexOption.IGNORE_CASE)
    private val FIGURE = Regex("^(fig\\.?|figure|abb\\.?|abbildung)\\s*(\\d+)\\s*[.:|]?\\s*(.*)$", RegexOption.IGNORE_CASE)
    private val TABLE = Regex("^(tab\\.?|table|tabelle)\\s*(\\d+)\\s*[.:|]?\\s*(.*)$", RegexOption.IGNORE_CASE)

    /** The paper's structure from its lines. */
    fun read(all: List<Line>, fileTitle: String = ""): Paper {
        // Running heads and feet (the same text on several pages) and page numbers go.
        val repeated = all.groupBy { it.text.lowercase().replace(Regex("\\d+"), "#") }.filter { (_, l) -> l.map { it.page }.distinct().size >= 3 && l.first().text.length < 120 }.keys
        val lines = all.filter { l -> l.text.lowercase().replace(Regex("\\d+"), "#") !in repeated && !Regex("^\\d{1,3}$").matches(l.text) && !l.text.startsWith("arXiv:", true) }
        val sized = lines.filter { it.size > 0f }
        val body = sized.groupBy { Math.round(it.size * 2) / 2f }.maxByOrNull { (_, l) -> l.sumOf { it.text.length } }?.key ?: 0f
        // The usual distance of two lines of body text: a larger gap before a line is the end of a paragraph.
        val steps = lines.zipWithNext().filter { (a, b) -> a.page == b.page && abs(a.size - body) < 0.6f && abs(b.size - body) < 0.6f && b.y > a.y && b.y - a.y < body * 4 }.map { (a, b) -> b.y - a.y }.sorted()
        val step = steps.getOrNull(steps.size / 2) ?: 0f

        // Title: the largest text in the upper part of the first page; the authors are the short lines under it.
        val first = lines.filter { it.page == lines.firstOrNull()?.page }
        val top = first.filter { it.size > 0f && it.y < (first.maxOfOrNull { l -> l.y } ?: 0f) * 0.6f + 1f }
        val titleSize = top.maxOfOrNull { it.size }?.takeIf { it >= body * 1.2f }
        val titleLines = if (titleSize != null) {
            val at = first.indexOfFirst { abs(it.size - titleSize) < 0.6f }
            first.drop(at).takeWhile { abs(it.size - titleSize) < 0.6f }.take(4)
        } else listOfNotNull(first.firstOrNull { it.text.length in 8..200 && !KNOWN.matches(it.text) })
        val title = titleLines.joinToString(" ") { it.text }.replace(Regex("\\s+"), " ").trim().ifBlank { fileTitle }
        val afterTitle = first.dropWhile { it !in titleLines }.drop(titleLines.size)
        val authors = afterTitle.takeWhile { !isHeading(it, body, null, step) && !ABSTRACT.containsMatchIn(it.text) }.take(6)
            .map { it.text.replace(Regex("[*†‡§¹²³⁴⁵⁶⁷⁸⁹⁰]|(?<=\\p{L})\\d(,\\d)*\\b"), "").replace(Regex("\\s+"), " ").trim() }
            .filter { a -> a.length in 3..160 && '@' !in a && !Regex("universit|institut|department|fakultät|faculty|school|college|lab\\b|gmbh|street|straße|\\d{5}", RegexOption.IGNORE_CASE).containsMatchIn(a) }
            .take(2)

        // Sections between the headings; captions of figures and tables are taken out of the text.
        val sections = mutableListOf<Section>()
        val tables = mutableListOf<Triple<Int, String, List<List<String>>>>()
        val figures = mutableListOf<Pair<Int, String>>()
        var head: Pair<String, Int>? = null
        var headPage = 0
        val text = StringBuilder()
        var abstract = ""
        fun flush() {
            val t = text.toString().replace(Regex("(\\p{Ll})- (\\p{Ll})"), "$1$2").replace(Regex("\\s+"), " ").trim()
            text.clear()
            // Text before the first heading: the abstract, when it is marked („Abstract—…“, „Abstract. …“) or long.
            val h = head ?: run {
                val at = t.indexOf("Abstract", ignoreCase = true).takeIf { it >= 0 } ?: t.indexOf("Zusammenfassung", ignoreCase = true)
                if (abstract.isEmpty()) abstract = if (at >= 0) t.substring(at).replace(Regex("^\\p{L}+\\s*[—–:.-]?\\s*"), "") else if (t.length > 400) t else ""
                return
            }
            if (ABSTRACT.containsMatchIn(h.first)) abstract = t
            else if (!SKIP.containsMatchIn(h.first)) sections += Section(h.first, h.second, headPage, t)
        }
        val start = lines.indexOf(afterTitle.firstOrNull() ?: lines.firstOrNull())
        var k = start.coerceAtLeast(0)
        var skipping = false
        while (k < lines.size) {
            val l = lines[k]
            val fig = FIGURE.find(l.text)
            val tab = TABLE.find(l.text)
            when {
                isHeading(l, body, lines.getOrNull(k - 1), step) -> {
                    flush()
                    val (name, level) = headingOf(l.text)
                    // From the references on nothing belongs into the talk.
                    if (level == 1 && Regex("^(references|literatur|literaturverzeichnis|quellen|bibliography)", RegexOption.IGNORE_CASE).containsMatchIn(name)) { head = null; break }
                    head = name to level
                    headPage = l.page
                    skipping = SKIP.containsMatchIn(name) && level == 1
                }
                skipping -> {}
                fig != null && l.text.length < 400 -> figures += l.page to l.text
                tab != null && l.text.length < 400 -> {
                    // The rows of the table: the lines with several cells right below (or above) the caption.
                    fun rows(range: IntProgression): List<List<String>> = range.asSequence().map { lines.getOrNull(it) }.takeWhile { it != null && it.page == l.page && it.cells.size >= 2 }
                        .map { it!!.cells }.take(15).toList()
                    val below = rows(k + 1 until lines.size)
                    val table = below.ifEmpty { rows(k - 1 downTo 0).reversed() }
                    if (table.size >= 2) {
                        tables += Triple(l.page, l.text, table)
                        if (below.isNotEmpty()) k += below.size
                    }
                }
                else -> text.append(' ').append(l.text)
            }
            k++
        }
        flush()
        return Paper(title, authors, abstract, sections.filter { it.text.isNotBlank() || it.level == 1 }, figures, tables)
    }

    /**
     * Whether [l] is a heading: one of the usual section names, or – after the end of a paragraph ([prev] ends a
     * sentence, another page or a gap larger than the usual line [step]) – numbered („2.1 Datenerhebung“) or larger than the body text. So a line
     * like „40 Studierende nahmen teil“ inside a paragraph stays text.
     */
    private fun isHeading(l: Line, body: Float, prev: Line?, step: Float): Boolean {
        val t = l.text
        if (t.length > 95 || t.length < 3) return false
        if (KNOWN.matches(t)) return true
        if (body > 0f && l.size > 0f && l.size < body * 0.95f) return false
        val afterBreak = prev == null || prev.page != l.page || prev.text.trimEnd().lastOrNull()?.let { it in ".:!?)" } == true ||
            (step > 0f && l.y - prev.y > step * 1.35f)
        if (!afterBreak) return false
        if (NUMBERED.matches(t) && !t.trimEnd().endsWith(",") && t.split(' ').size <= 12) return !Regex("\\d{3,}").containsMatchIn(t.substringAfter(' ')) || KNOWN.containsMatchIn(t)
        if (ROMAN.matches(t)) return true
        return body > 0f && l.size >= body * 1.18f && t.split(' ').size <= 12 && !t.endsWith(".")
    }

    /** "2.1 Datenerhebung" → ("Datenerhebung", 2); "Methods" → ("Methods", 1). */
    private fun headingOf(t: String): Pair<String, Int> {
        NUMBERED.find(t)?.let { m -> return m.groupValues[2].trim().trimEnd(':') to m.groupValues[1].count { it == '.' } + 1 }
        ROMAN.find(t)?.let { m -> return m.groupValues[2].trim().trimEnd(':') to 1 }
        return t.replace(Regex("^(\\d+(\\.\\d+)*|[IVX]+)\\.?\\s+"), "").trim().trimEnd(':') to 1
    }

    /** Sentences of a text (at . ! ? before a capital letter, abbreviations like „z. B.“, „et al.“ and „Fig. 2“ kept). */
    fun sentences(text: String): List<String> = text.replace(Regex("\\s+"), " ")
        .split(Regex("(?<=[.!?])\\s+(?=[\\p{Lu}\\d(„\"])"))
        .fold(mutableListOf<String>()) { acc, s ->
            if (acc.isNotEmpty() && Regex("(\\b(al|bzw|ca|etc|vgl|z\\.\\s?B|d\\.\\s?h|u\\.\\s?a|Fig|Abb|Tab|Eq|Nr|S|i\\.\\s?e|e\\.\\s?g)\\.|\\b\\p{Lu}\\.)$").containsMatchIn(acc.last())) acc[acc.lastIndex] = acc.last() + " " + s else acc += s
            acc
        }.map { it.trim() }.filter { it.length >= 25 }

    private val KEY = Regex("\\d|%|signifi|significant|effekt|effect|zeig|show|find|found|result|ergeb|propos|schlag|contribut|beitrag|improv|verbesser|outperform|demonstr|novel|neu", RegexOption.IGNORE_CASE)

    /** Up to [max] key sentences of [text] in their order: the first one and those with numbers and findings. */
    fun keyPoints(text: String, max: Int = 5): List<String> {
        val all = sentences(text)
        if (all.isEmpty()) return emptyList()
        val chosen = (listOf(0) + all.indices.drop(1).filter { KEY.containsMatchIn(all[it]) } + all.indices.drop(1)).distinct().take(max).sorted()
        return chosen.map { shorten(all[it]) }
    }

    /** A sentence as a point: at most [max] characters, cut at a comma or word with „…“. */
    fun shorten(s: String, max: Int = 150): String {
        val t = s.trim().removeSuffix(".")
        if (t.length <= max) return t
        val cut = t.take(max)
        val at = maxOf(cut.lastIndexOf(", "), cut.lastIndexOf("; "), cut.lastIndexOf(" – ")).takeIf { it > max / 2 } ?: cut.lastIndexOf(' ').takeIf { it > max / 2 } ?: max
        return cut.take(at).trimEnd(',', ';', ' ') + " …"
    }

    /** Speaker notes from a section: its first sentences, at most [max] characters. */
    fun notes(text: String, max: Int = 900): String {
        val out = StringBuilder()
        for (s in sentences(text)) {
            if (out.length + s.length > max) break
            out.append(s).append(' ')
        }
        return out.toString().trim().ifBlank { text.take(max) }
    }

    /**
     * The deck: [figures] are the stored pictures of the PDF (by page), placed after the section of their page with
     * the caption found on that page.
     */
    fun slides(paper: Paper, figures: List<Figure> = emptyList()): List<SlideDeck.Slide> {
        val out = mutableListOf(SlideDeck.Slide(paper.title.ifBlank { "Titel" }, paper.authors.joinToString("\n").ifBlank { DocMarkup.today() }, layout = SlideDeck.Layout.TITLE))
        if (paper.abstract.isNotBlank()) {
            out += SlideDeck.Slide("Worum es geht", keyPoints(paper.abstract, 4).joinToString("\n") { "- $it" }, notes = notes(paper.abstract, 1200), layout = SlideDeck.Layout.CONTENT)
        }
        val main = paper.sections.filter { it.level == 1 }
        if (main.size >= 3) out += SlideDeck.Slide("Gliederung", main.joinToString("\n") { "- ${it.title}" }, layout = SlideDeck.Layout.AGENDA)
        val pictures = figures.sortedBy { it.page }.toMutableList()
        val captions = paper.figures.toMutableList()
        val tables = paper.tables.toMutableList()
        fun media(upTo: Int) {
            while (pictures.isNotEmpty() && pictures.first().page <= upTo) {
                val f = pictures.removeAt(0)
                val caption = captions.firstOrNull { it.first == f.page }?.also { captions.remove(it) }?.second.orEmpty()
                val t = FIGURE.find(caption)
                val title = t?.let { "Abbildung ${it.groupValues[2]}" } ?: "Abbildung"
                out += SlideDeck.Slide(title, "", notes = caption, layout = SlideDeck.Layout.IMAGE, image = f.path, imageAlt = shorten(caption.ifBlank { title }, 200))
            }
            while (tables.isNotEmpty() && tables.first().first <= upTo) {
                val (_, caption, rows) = tables.removeAt(0)
                val t = TABLE.find(caption)
                out += SlideDeck.Slide(t?.let { "Tabelle ${it.groupValues[2]}" } ?: "Tabelle", DocBlocks.table(rows.map { r -> r.map { it.replace("|", "\\|") } }), notes = caption, layout = SlideDeck.Layout.TABLE)
            }
        }
        val sections = paper.sections
        sections.forEachIndexed { i, s ->
            val subs = s.level == 1 && sections.getOrNull(i + 1)?.level?.let { it > 1 } == true
            if (subs) out += SlideDeck.Slide(s.title, keyPoints(s.text, 1).firstOrNull().orEmpty(), notes = notes(s.text), layout = SlideDeck.Layout.SECTION)
            else if (s.text.isNotBlank()) {
                val points = keyPoints(s.text, 5)
                if (points.isNotEmpty()) out += SlideDeck.Slide(s.title, points.joinToString("\n") { "- $it" }, notes = notes(s.text), layout = SlideDeck.Layout.CONTENT, transition = "fade", build = true)
            }
            val nextPage = sections.getOrNull(i + 1)?.page ?: Int.MAX_VALUE
            media(if (nextPage == Int.MAX_VALUE) Int.MAX_VALUE else nextPage - 1)
        }
        media(Int.MAX_VALUE)
        out += SlideDeck.Slide("Vielen Dank!", (listOf("Fragen und Diskussion") + paper.authors.take(1)).joinToString("\n"), layout = SlideDeck.Layout.END)
        return out
    }
}
