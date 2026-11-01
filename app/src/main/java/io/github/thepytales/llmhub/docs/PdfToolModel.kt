package io.github.thepytales.llmhub.docs

import kotlinx.serialization.Serializable
import io.github.thepytales.llmhub.ui.trLater

/**
 * Shared types of the PDF-Werkzeugkasten (AP-11). Positions are fractions of the page (0..1, from the top left
 * corner), so the screen can draw them at any zoom and PdfTools converts them to PDF points.
 */
@Serializable
data class PdfBox(val x: Float, val y: Float, val w: Float, val h: Float) {
    val right get() = x + w
    val bottom get() = y + h
}

@Serializable
data class PdfPoint(val x: Float, val y: Float)

enum class PdfMarkKind(val label: String) {
    HIGHLIGHT(trLater("Hervorheben")), UNDERLINE(trLater("Unterstreichen")), STRIKE(trLater("Durchstreichen")), INK(trLater("Freihand")), NOTE(trLater("Notiz")), STAMP(trLater("Stempel"))
}

/** An annotation to add: highlight/underline/strike over [boxes], freehand [strokes], a note or stamp at [boxes].first. */
@Serializable
data class PdfMark(
    val page: Int,
    val kind: PdfMarkKind,
    val boxes: List<PdfBox> = emptyList(),
    val strokes: List<List<PdfPoint>> = emptyList(),
    val text: String = "",
    /** RGB color 0xRRGGBB. */
    val color: Int = when (kind) {
        PdfMarkKind.HIGHLIGHT -> 0xFFE066
        PdfMarkKind.STAMP, PdfMarkKind.STRIKE -> 0xC62828
        PdfMarkKind.NOTE -> 0xFFC107
        else -> 0x1565C0
    },
)

/** A comment found in a PDF (for the list and its export). */
data class PdfComment(val page: Int, val kind: String, val text: String, val author: String = "")

/** An entry of the outline (bookmarks). */
data class PdfOutlineEntry(val title: String, val page: Int, val level: Int)

/** A search hit: page (1-based) and the text around it. */
data class PdfSearchHit(val page: Int, val snippet: String)

enum class PdfFieldType(val label: String) { TEXT(trLater("Textfeld")), MULTILINE(trLater("Mehrzeilig")), CHECKBOX(trLater("Ankreuzfeld")), DATE(trLater("Datum")), SIGNATURE(trLater("Unterschrift")) }

/** A form field to create (or one that exists), with its place on the page. */
@Serializable
data class PdfFieldSpec(
    val name: String,
    val page: Int,
    val box: PdfBox,
    val type: PdfFieldType = PdfFieldType.TEXT,
    /** The printed text it belongs to („Name:“), as tooltip. */
    val label: String = "",
)

/** A run of text with its position in points from the top left corner of its page. */
data class PdfTextRun(val page: Int, val x: Float, val y: Float, val w: Float, val h: Float, val text: String)

/**
 * Formulare ausfüllbar machen (AP-11): finds where entries belong on a page that only has printed text – lines of
 * underscores („Name: ________“), a label with a colon and empty space up to the margin, boxes to tick (☐, □, [ ]) and
 * labels such as „Datum“ or „Unterschrift“. The result are fields the user can still move, resize or retype.
 */
object PdfFormDetect {
    private val UNDERSCORES = Regex("_{4,}|\\.{8,}|…{3,}")
    private val BOX = Regex("[☐□▢❏]|\\[\\s?]")
    private val LABEL_END = Regex("([\\p{L}][\\p{L}\\d .,/()-]{1,40}):\\s*$")
    private val DATE_WORDS = Regex("(?i)\\b(datum|date|geburtsdatum|ort, datum)\\b")
    private val SIGN_WORDS = Regex("(?i)\\b(unterschrift|signature|unterzeichnet)\\b")
    private val MULTI_WORDS = Regex("(?i)\\b(begründung|bemerkung|anmerkung|kommentar|beschreibung|anlass|zweck)\\b")

    /**
     * Fields for the runs of one page of [pageW] × [pageH] points. [runs] are lines (or parts of lines) of text with
     * their positions; characters of a line must be in reading order.
     */
    fun detect(runs: List<PdfTextRun>, pageW: Float, pageH: Float): List<PdfFieldSpec> {
        val out = mutableListOf<PdfFieldSpec>()
        val margin = pageW * 0.08f
        val used = HashSet<String>()
        fun name(label: String, page: Int): String {
            val base = label.trim().trimEnd(':').replace(Regex("\\s+"), " ").take(40).ifBlank { "Feld" }
            var n = base
            var i = 2
            while (!used.add("$page/$n")) n = "$base ${i++}"
            return n
        }
        fun typeFor(label: String, width: Float): PdfFieldType = when {
            SIGN_WORDS.containsMatchIn(label) -> PdfFieldType.SIGNATURE
            DATE_WORDS.containsMatchIn(label) && width < pageW * 0.35f -> PdfFieldType.DATE
            MULTI_WORDS.containsMatchIn(label) -> PdfFieldType.MULTILINE
            else -> PdfFieldType.TEXT
        }
        for (run in runs) {
            if (run.text.isBlank() || run.w <= 0f) continue
            val charW = run.w / run.text.length.coerceAtLeast(1)
            val h = run.h.coerceAtLeast(8f)
            // 1. Lines of underscores or dots: the field sits on the line.
            UNDERSCORES.findAll(run.text).forEach { m ->
                val x = run.x + m.range.first * charW
                val w = (m.value.length * charW).coerceAtLeast(30f)
                val before = run.text.substring(0, m.range.first).trim()
                val label = before.substringAfterLast("  ").ifBlank { before }.ifBlank { nearestLabel(runs, run) }
                val type = typeFor(label, w)
                val fh = if (type == PdfFieldType.MULTILINE) h * 2.4f else h * 1.25f
                out += spec(name(label.ifBlank { "Feld" }, run.page), run.page, x, run.y + run.h - fh + h * 0.2f, w, fh, type, label, pageW, pageH)
            }
            // 2. Boxes to tick.
            BOX.findAll(run.text).forEach { m ->
                val x = run.x + m.range.first * charW
                val after = run.text.substring(m.range.last + 1).trim().split(Regex("\\s{2,}|[☐□▢❏]|\\[\\s?]")).firstOrNull().orEmpty().trim()
                val size = h.coerceIn(8f, 14f)
                out += spec(name(after.ifBlank { "Ankreuzen" }, run.page), run.page, x, run.y + (run.h - size) / 2, size, size, PdfFieldType.CHECKBOX, after, pageW, pageH)
            }
            // 3. „Label:“ at the end of the text with room up to the margin.
            if (!UNDERSCORES.containsMatchIn(run.text) && !BOX.containsMatchIn(run.text)) {
                val m = LABEL_END.find(run.text.trimEnd()) ?: continue
                val label = m.groupValues[1].trim()
                if (label.split(' ').size > 5) continue
                val start = run.x + run.w + charW
                val end = rightNeighbour(runs, run)?.x?.minus(charW) ?: (pageW - margin)
                val w = end - start
                if (w < pageW * 0.15f) continue
                val type = typeFor(label, w)
                val fh = if (type == PdfFieldType.MULTILINE) h * 2.4f else h * 1.25f
                out += spec(name(label, run.page), run.page, start, run.y - h * 0.1f, w, fh, type, label, pageW, pageH)
            }
        }
        return out
    }

    /** Text right of [run] on the same line. */
    private fun rightNeighbour(runs: List<PdfTextRun>, run: PdfTextRun): PdfTextRun? =
        runs.filter { it !== run && it.page == run.page && kotlin.math.abs(it.y - run.y) < run.h * 0.6f && it.x > run.x + run.w }.minByOrNull { it.x }

    /** A short text just above or left of [run] that names the line („Name“ above a line of underscores). */
    private fun nearestLabel(runs: List<PdfTextRun>, run: PdfTextRun): String =
        runs.filter { it !== run && it.page == run.page && it.text.isNotBlank() && !UNDERSCORES.matches(it.text.trim()) }
            .filter { (kotlin.math.abs(it.y - run.y) < run.h * 0.6f && it.x + it.w <= run.x + 2) || (it.y < run.y && run.y - it.y < run.h * 2.2f && it.x < run.x + run.w && it.x + it.w > run.x) }
            .minByOrNull { kotlin.math.abs(it.y - run.y) + kotlin.math.abs(it.x - run.x) * 0.1f }
            ?.text?.trim()?.trimEnd(':')?.take(40).orEmpty()

    private fun spec(name: String, page: Int, x: Float, y: Float, w: Float, h: Float, type: PdfFieldType, label: String, pw: Float, ph: Float) =
        PdfFieldSpec(name, page, PdfBox((x / pw).coerceIn(0f, 1f), (y / ph).coerceIn(0f, 1f), (w / pw).coerceIn(0.005f, 1f), (h / ph).coerceIn(0.005f, 1f)), type, label.trim().trimEnd(':'))
}

/** Page numbers to write: format with {n} (page) and {N} (pages), position and first number. */
data class PdfNumbering(val format: String = "Seite {n} von {N}", val position: Position = Position.BOTTOM_CENTER, val start: Int = 1, val size: Float = 10f) {
    enum class Position(val label: String) { BOTTOM_CENTER(trLater("Unten Mitte")), BOTTOM_RIGHT(trLater("Unten rechts")), TOP_RIGHT(trLater("Oben rechts")) }

    fun text(n: Int, total: Int) = format.replace("{n}", (n + start - 1).toString()).replace("{N}", (total + start - 1).toString())
}

/** Page ranges like „1-3, 5, 8-“ (1-based) → page indexes (0-based) of a document with [pages] pages. */
fun parsePageRanges(text: String, pages: Int): List<Int> {
    val out = mutableListOf<Int>()
    text.split(',', ';').map { it.trim() }.filter { it.isNotEmpty() }.forEach { part ->
        val bounds = part.split('-', '–').map { it.trim() }
        val a = bounds[0].toIntOrNull() ?: if (bounds[0].isEmpty()) 1 else return@forEach
        val b = if (bounds.size == 1) a else bounds[1].toIntOrNull() ?: pages
        for (p in minOf(a, b)..maxOf(a, b)) if (p in 1..pages && (p - 1) !in out) out += p - 1
    }
    return out
}
