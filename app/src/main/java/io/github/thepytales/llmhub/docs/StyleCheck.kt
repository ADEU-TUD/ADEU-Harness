package io.github.thepytales.llmhub.docs

import io.github.thepytales.llmhub.data.HouseRule
import io.github.thepytales.llmhub.ui.tr

/**
 * Hausstil-Prüfung und Barrierefreiheits-Check (AP-79) für Dokumente und Folien (Markdown des offenen Editors) und für
 * Text aus Dateien: jede Fundstelle mit Ort („Absatz 4“, „Folie 2“) und, wo es automatisch geht, einem „Beheben“.
 *
 * **Hausstil**: die eigenen Regeln der Professur (Einstellungen › Dokumente & Logos › Hausstil, auch aus geteilten
 * Lora-Paketen) und die Schreibweise der TU Dresden („TU Dresden“ ohne Bindestrich) sowie Abkürzungen mit schmalem
 * Abstand nach DIN 5008 („z. B.“). **Barrierefreiheit**: Überschriftenstruktur (eine Hauptüberschrift, keine
 * übersprungenen Ebenen, fett gesetzte Zeilen statt echter Überschriften), Alternativtexte von Bildern, verständliche
 * Linktexte, nackte Adressen, Tabellen mit Kopfzeile, Folien mit Titel, lange Zeilen in Großbuchstaben.
 */
object StyleCheck {
    enum class Kind { HOUSE, ACCESS }

    /** A replacement in the Markdown: [range] of the text becomes [replacement]. */
    data class Fix(val range: IntRange, val replacement: String)

    data class Issue(val kind: Kind, val text: String, val where: String, val excerpt: String, val fix: Fix? = null, val advice: String = "")

    /** Checks [markdown]; [slides]: a deck („---“ between slides, each with a title). */
    fun check(markdown: String, rules: List<HouseRule> = emptyList(), slides: Boolean = false): List<Issue> {
        val blocks = blocks(markdown)
        return house(markdown, blocks, rules, slides) + access(markdown, blocks, slides)
    }

    // ── Blocks: paragraphs with their place in the text, for „Absatz 4“ and „Folie 2“ ────────────────────────

    data class Block(val start: Int, val text: String, val paragraph: Int, val slide: Int)

    internal fun blocks(md: String): List<Block> {
        val out = mutableListOf<Block>()
        var pos = 0
        var paragraph = 0
        var slide = 1
        var inCode = false
        for (line in md.split('\n')) {
            val t = line.trim()
            if (t.startsWith("```")) inCode = !inCode
            if (!inCode) {
                if (t == "---" || t == "***") slide++
                else if (t.isNotEmpty()) out += Block(pos, line, ++paragraph, slide)
            }
            pos += line.length + 1
        }
        return out
    }

    private fun where(b: Block, slides: Boolean) = if (slides) tr("Folie {0}", b.slide) else tr("Absatz {0}", b.paragraph)

    private fun excerpt(text: String, at: Int, len: Int): String {
        val from = (at - 30).coerceAtLeast(0)
        val to = (at + len + 30).coerceAtMost(text.length)
        return (if (from > 0) "…" else "") + text.substring(from, to).replace('\n', ' ').trim() + (if (to < text.length) "…" else "")
    }

    // ── Hausstil ───────────────────────────────────────────────────────────────────────────────────────────

    /** Built-in spellings: the name of the university and abbreviations with a space (DIN 5008). */
    private val BUILT_IN = listOf(
        HouseRule("TU-Dresden", "TU Dresden", "Schreibweise der TU Dresden: ohne Bindestrich."),
        HouseRule("Technische Universität-Dresden", "Technische Universität Dresden", "Schreibweise der TU Dresden: ohne Bindestrich."),
        HouseRule("z.B.", "z. B.", "Abkürzung mit Leerzeichen (DIN 5008)."),
        HouseRule("d.h.", "d. h.", "Abkürzung mit Leerzeichen (DIN 5008)."),
        HouseRule("u.a.", "u. a.", "Abkürzung mit Leerzeichen (DIN 5008)."),
        HouseRule("z.T.", "z. T.", "Abkürzung mit Leerzeichen (DIN 5008)."),
        HouseRule("o.ä.", "o. Ä.", "Abkürzung mit Leerzeichen (DIN 5008)."),
        HouseRule("s.o.", "s. o.", "Abkürzung mit Leerzeichen (DIN 5008)."),
        HouseRule("s.u.", "s. u.", "Abkürzung mit Leerzeichen (DIN 5008)."),
    )

    private fun pattern(r: HouseRule): Regex {
        val core = Regex.escape(r.avoid)
        // Abbreviations end with a dot: no word boundary after it.
        val end = if (r.avoid.last().isLetterOrDigit()) "(?![\\p{L}\\p{N}])" else ""
        val start = if (r.avoid.first().isLetterOrDigit()) "(?<![\\p{L}\\p{N}])" else ""
        return if (r.wholeWord) Regex(start + core + end) else Regex(core, RegexOption.IGNORE_CASE)
    }

    private fun house(md: String, blocks: List<Block>, rules: List<HouseRule>, slides: Boolean): List<Issue> {
        val out = mutableListOf<Issue>()
        for (r in (rules + BUILT_IN).distinctBy { it.avoid }) {
            val re = pattern(r)
            for (b in blocks) for (m in re.findAll(b.text)) {
                val at = b.start + m.range.first
                val fix = r.preferred.takeIf { it.isNotEmpty() }?.let { Fix(at..at + m.value.length - 1, it) }
                val text = if (r.preferred.isNotEmpty()) tr("„{0}“ → „{1}“", m.value, r.preferred) else tr("„{0}“ vermeiden", m.value)
                out += Issue(Kind.HOUSE, text + (r.note.takeIf { it.isNotBlank() }?.let { " – $it" } ?: ""), where(b, slides), excerpt(b.text, m.range.first, m.value.length), fix)
            }
        }
        // Two spaces in a row inside a paragraph.
        for (b in blocks) for (m in Regex("(?<=\\S)  +(?=\\S)").findAll(b.text)) {
            val at = b.start + m.range.first
            out += Issue(Kind.HOUSE, tr("Doppeltes Leerzeichen"), where(b, slides), excerpt(b.text, m.range.first, m.value.length), Fix(at..at + m.value.length - 1, " "))
        }
        return out.sortedBy { it.fix?.range?.first ?: Int.MAX_VALUE }
    }

    // ── Barrierefreiheit ─────────────────────────────────────────────────────────────────────────────────

    private val HEADING = Regex("^(#{1,6})\\s+(.*)$")
    private val FAKE_HEADING = Regex("^\\*\\*([^*]{2,80})\\*\\*\\s*$")
    private val IMAGE = Regex("!\\[([^\\]]*)]\\(([^)]+)\\)")
    private val LINK = Regex("(?<!!)\\[([^\\]]+)]\\(([^)]+)\\)")
    private val BARE_URL = Regex("(?<![(<\\[])\\bhttps?://\\S+")
    private val VAGUE = setOf("hier", "link", "klicken sie hier", "hier klicken", "mehr", "weiter", "weiterlesen", "click here", "here", "more", "dieser link", "diesen link")

    private fun access(md: String, blocks: List<Block>, slides: Boolean): List<Issue> {
        val out = mutableListOf<Issue>()
        var last = 0
        var h1 = 0
        val headingsPerSlide = HashMap<Int, Boolean>()
        for (b in blocks) {
            val t = b.text.trim()
            val w = where(b, slides)
            HEADING.find(t)?.let { m ->
                val level = m.groupValues[1].length
                val title = m.groupValues[2].trim()
                headingsPerSlide[b.slide] = true
                if (title.isEmpty()) out += Issue(Kind.ACCESS, tr("Leere Überschrift"), w, t, Fix(b.start..b.start + b.text.length - 1, ""), tr("Screenreader lesen eine leere Überschrift vor."))
                if (level == 1) h1++
                if (!slides && last > 0 && level > last + 1) {
                    val hashes = "#".repeat(last + 1)
                    val lead = b.text.length - b.text.trimStart().length
                    out += Issue(
                        Kind.ACCESS, tr("Überschrift springt von Ebene {0} auf Ebene {1}", last, level), w, title,
                        Fix(b.start + lead..b.start + lead + m.groupValues[1].length - 1, hashes),
                        tr("Ebenen nicht überspringen – Screenreader-Nutzende navigieren über sie."),
                    )
                    last += 1
                } else last = level
            }
            FAKE_HEADING.find(t)?.takeIf { !slides }?.let { m ->
                val level = (last + 1).coerceIn(2, 6).let { if (last == 0) 2 else it }
                out += Issue(
                    Kind.ACCESS, tr("Fett gesetzte Zeile statt Überschrift"), w, m.groupValues[1],
                    Fix(b.start..b.start + b.text.length - 1, "#".repeat(level) + " " + m.groupValues[1].trim()),
                    tr("Als echte Überschrift erscheint sie in der Gliederung und für Screenreader."),
                )
            }
            for (m in IMAGE.findAll(b.text)) if (m.groupValues[1].isBlank()) {
                out += Issue(Kind.ACCESS, tr("Bild ohne Alternativtext"), w, m.groupValues[2].substringAfterLast('/').take(60), advice = tr("Kurz beschreiben, was das Bild zeigt (Bild antippen › Alternativtext)."))
            }
            for (m in LINK.findAll(b.text)) if (m.groupValues[1].trim().lowercase().trimEnd('.', '!', ':') in VAGUE) {
                out += Issue(Kind.ACCESS, tr("Linktext „{0}“ sagt nicht, wohin er führt", m.groupValues[1]), w, excerpt(b.text, m.range.first, m.value.length), advice = tr("Das Ziel nennen, z. B. „Prüfungsordnung (PDF)“."))
            }
            for (m in BARE_URL.findAll(b.text)) if (!b.text.contains("](" + m.value)) {
                val url = m.value.trimEnd('.', ',', ';', ')')
                val at = b.start + m.range.first
                val label = url.removePrefix("https://").removePrefix("http://").removePrefix("www.").substringBefore('/')
                out += Issue(Kind.ACCESS, tr("Nackte Adresse als Link"), w, url, Fix(at..at + url.length - 1, "[$label]($url)"), tr("Screenreader lesen sonst die ganze Adresse Zeichen für Zeichen vor."))
            }
            if (t.length >= 25 && t.none { it.isLowerCase() } && t.count { it.isLetter() } >= 15 && !t.startsWith("|")) {
                out += Issue(Kind.ACCESS, tr("Ganze Zeile in Großbuchstaben"), w, t.take(60), advice = tr("Großbuchstaben sind schwer zu lesen und werden teils buchstabiert – lieber fett oder als Überschrift."))
            }
        }
        // Tables: the first row is the head; an empty head row is no head.
        for (b in blocks) {
            val t = b.text.trim()
            if (t.startsWith("|") && Regex("^\\|?\\s*:?-{3,}").containsMatchIn(t)) {
                val head = blocks.getOrNull(blocks.indexOf(b) - 1)?.text.orEmpty()
                if (head.split('|').map { it.trim() }.filter { it.isNotEmpty() }.isEmpty()) {
                    out += Issue(Kind.ACCESS, tr("Tabelle ohne Kopfzeile"), where(b, slides), "", advice = tr("Die erste Zeile mit Spaltenüberschriften füllen."))
                }
            }
        }
        if (!slides) {
            if (h1 > 1) out += Issue(Kind.ACCESS, tr("{0} Hauptüberschriften (Ebene 1)", h1), tr("Dokument"), "", advice = tr("Nur eine Hauptüberschrift (Titel), darunter Ebene 2."))
            if (blocks.size >= 12 && blocks.none { HEADING.containsMatchIn(it.text.trim()) }) {
                out += Issue(Kind.ACCESS, tr("Langes Dokument ohne Überschriften"), tr("Dokument"), "", advice = tr("Abschnitte mit Überschriften gliedern – sie ergeben die Navigation und die Lesezeichen im PDF."))
            }
        } else {
            val slidesTotal = blocks.maxOfOrNull { it.slide } ?: 0
            for (s in 1..slidesTotal) if (headingsPerSlide[s] != true && blocks.any { it.slide == s }) {
                out += Issue(Kind.ACCESS, tr("Folie ohne Titel"), tr("Folie {0}", s), blocks.first { it.slide == s }.text.trim().take(60), advice = tr("Jede Folie braucht einen eindeutigen Titel – er ist die Navigation für Screenreader."))
            }
        }
        return out
    }

    // ── Beheben ──────────────────────────────────────────────────────────────────────────────────────────

    /** [markdown] with the fixes of [issues] applied (back to front, overlapping ones skipped). */
    fun apply(markdown: String, issues: List<Issue>): String {
        val fixes = issues.mapNotNull { it.fix }.sortedByDescending { it.range.first }
        val sb = StringBuilder(markdown)
        var lowest = Int.MAX_VALUE
        for (f in fixes) {
            if (f.range.last >= lowest || f.range.first < 0 || f.range.last >= sb.length) continue
            sb.replace(f.range.first, f.range.last + 1, f.replacement)
            lowest = f.range.first
        }
        return sb.toString()
    }

    /** One line per issue for Lora's card and the protocol. */
    fun summary(issues: List<Issue>): String {
        if (issues.isEmpty()) return tr("Keine Funde – Hausstil und Barrierefreiheit sehen gut aus.")
        val house = issues.count { it.kind == Kind.HOUSE }
        val access = issues.count { it.kind == Kind.ACCESS }
        val fixable = issues.count { it.fix != null }
        return tr("{0} Funde: {1} zum Hausstil, {2} zur Barrierefreiheit; {3} lassen sich automatisch beheben.", issues.size, house, access, fixable)
    }

    // ── Corporate Design in Word- und PowerPoint-Dateien ──────────────────────────────────────────────────

    /** Fonts outside the house font (Noto Sans) and colours outside the TUD palette in the XML of a docx/pptx. */
    fun corporateDesign(xml: String, palette: Set<String>, houseFont: String): Pair<Set<String>, Set<String>> {
        val fonts = Regex("(?:w:ascii|typeface)=\"([^\"]+)\"").findAll(xml).map { it.groupValues[1] }
            .filter { !it.startsWith("+") && it.isNotBlank() && !it.equals(houseFont, true) && !it.startsWith(houseFont, true) && it !in SYMBOL_FONTS }.toSet()
        val colors = Regex("(?:w:color w:val|srgbClr val)=\"([0-9A-Fa-f]{6})\"").findAll(xml).map { "#" + it.groupValues[1].uppercase() }
            .filter { it !in palette && it != "#000000" && it != "#FFFFFF" }.toSet()
        return fonts to colors
    }

    private val SYMBOL_FONTS = setOf("Symbol", "Wingdings", "Cambria Math", "Courier New", "Consolas")
}
