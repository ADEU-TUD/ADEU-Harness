package io.github.thepytales.llmhub

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import java.io.File
import kotlin.random.Random

/**
 * Messkorpus of AP-95 (PDF-Tabellen nach Excel): invented PDFs drawn with PDFBox whose tables are known cell by cell –
 * statistics reports over several pages (the head row repeated on every page), budget plans with a head cell spanning
 * two columns and right-aligned amounts, small tables of papers. Two parts with their own seeds, contents and measures:
 * [DEV] to improve the recognition, [TEST] only for the figures at the end.
 */
object PdfTableCorpus {
    enum class Align { LEFT, RIGHT, CENTER }

    data class Col(val width: Float, val align: Align)

    /** A head cell spanning [span] columns from [from] (a row above the head row). */
    data class Group(val from: Int, val span: Int, val text: String)

    data class Spec(
        val name: String,
        val cols: List<Col>,
        val head: List<String>,
        val rows: List<List<String>>,
        val groups: List<Group> = emptyList(),
        val fontSize: Float = 9f,
        val rowGap: Float = 5f,
        val lines: Boolean = false,
        val before: String = "",
        val after: String = "",
        /** Rows per page; the head is repeated on every page. */
        val perPage: Int = Int.MAX_VALUE,
    ) {
        /** What Excel should get: head (and group row) once, then all rows. */
        val expected: List<List<String>>
            get() {
                val group = if (groups.isEmpty()) emptyList() else listOf(List(cols.size) { c -> groups.firstOrNull { it.from == c }?.text ?: "" })
                return group + listOf(head) + rows
            }
    }

    private val font = PDType1Font(Standard14Fonts.FontName.HELVETICA)
    private val bold = PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD)

    fun write(spec: Spec, file: File) {
        PDDocument().use { doc ->
            val chunks = spec.rows.chunked(spec.perPage.coerceAtMost(spec.rows.size.coerceAtLeast(1)))
            chunks.forEachIndexed { pageNo, chunk ->
                val page = PDPage(PDRectangle.A4)
                doc.addPage(page)
                PDPageContentStream(doc, page).use { cs ->
                    var y = page.mediaBox.height - 70f
                    val left = 60f
                    fun text(t: String, x: Float, yy: Float, f: PDType1Font = font, size: Float = spec.fontSize) {
                        cs.beginText(); cs.setFont(f, size); cs.newLineAtOffset(x, yy); cs.showText(t); cs.endText()
                    }
                    fun cell(t: String, c: Int, yy: Float, f: PDType1Font = font) {
                        val x0 = left + spec.cols.take(c).sumOf { it.width.toDouble() }.toFloat()
                        val w = f.getStringWidth(t) / 1000 * spec.fontSize
                        val x = when (spec.cols[c].align) {
                            Align.LEFT -> x0 + 2
                            Align.RIGHT -> x0 + spec.cols[c].width - w - 4
                            Align.CENTER -> x0 + (spec.cols[c].width - w) / 2
                        }
                        text(t, x, yy, f)
                    }
                    if (pageNo == 0 && spec.before.isNotBlank()) {
                        spec.before.chunked(95).forEach { line -> text(line, left, y, size = 10f); y -= 14f }
                        y -= 10f
                    }
                    if (pageNo > 0) { text("(Fortsetzung)", left, y, size = 8f); y -= 18f }
                    val step = spec.fontSize + spec.rowGap
                    val total = spec.cols.sumOf { it.width.toDouble() }.toFloat()
                    fun rule(yy: Float) { if (spec.lines) { cs.moveTo(left, yy); cs.lineTo(left + total, yy); cs.stroke() } }
                    rule(y + spec.fontSize + 2)
                    if (spec.groups.isNotEmpty()) {
                        spec.groups.forEach { g ->
                            val x0 = left + spec.cols.take(g.from).sumOf { it.width.toDouble() }.toFloat()
                            val w = spec.cols.drop(g.from).take(g.span).sumOf { it.width.toDouble() }.toFloat()
                            val tw = bold.getStringWidth(g.text) / 1000 * spec.fontSize
                            text(g.text, x0 + (w - tw) / 2, y, bold)
                        }
                        y -= step
                    }
                    spec.head.forEachIndexed { c, t -> cell(t, c, y, bold) }
                    rule(y - spec.rowGap / 2)
                    y -= step
                    chunk.forEach { row ->
                        row.forEachIndexed { c, t -> cell(t, c, y) }
                        y -= step
                    }
                    rule(y + step - spec.rowGap / 2 - 1)
                    if (pageNo == chunks.lastIndex && spec.after.isNotBlank()) {
                        y -= 12f
                        spec.after.chunked(95).forEach { line -> text(line, left, y, size = 10f); y -= 14f }
                    }
                    text("Seite ${pageNo + 1}", page.mediaBox.width / 2 - 15, 40f, size = 8f)
                }
            }
            doc.save(file)
        }
    }

    // ── Contents (all invented) ──────────────────────────────────────────────────────────────────────

    private fun de(n: Double, decimals: Int): String {
        val s = String.format(java.util.Locale.GERMANY, "%,.${decimals}f", n)
        return s
    }

    private val DISTRICTS = listOf("Altstadt", "Neustadt", "Pieschen", "Klotzsche", "Loschwitz", "Blasewitz", "Leuben", "Prohlis",
        "Plauen", "Cotta", "Gorbitz", "Löbtau", "Striesen", "Johannstadt", "Kaditz", "Mickten", "Trachau", "Weißig", "Langebrück",
        "Schönfeld", "Cossebaude", "Mobschatz", "Oberwartha", "Gompitz", "Altfranken", "Schönborn", "Hellerau", "Wilschdorf")

    private val ITEMS = listOf("Geschäftsbedarf", "Bücher und Zeitschriften", "Post- und Fernmeldegebühren", "Dienstreisen",
        "Hilfskräfte", "Geräte unter 800 Euro", "Software und Lizenzen", "Veranstaltungen", "Druckkosten", "Gästebetreuung",
        "Wartung von Geräten", "Verbrauchsmaterial Labor", "Fortbildung", "Exkursionen", "Publikationsgebühren")

    private fun statistics(r: Random, name: String, n: Int, perPage: Int, fontSize: Float, lines: Boolean) = Spec(
        name,
        listOf(Col(110f, Align.LEFT), Col(70f, Align.RIGHT), Col(70f, Align.RIGHT), Col(60f, Align.RIGHT), Col(70f, Align.RIGHT)),
        listOf("Stadtteil", "Einwohner", "Haushalte", "Anteil", "Fläche (km²)"),
        DISTRICTS.shuffled(r).take(n).map { d ->
            listOf(d, de(r.nextInt(2_000, 60_000).toDouble(), 0), de(r.nextInt(900, 30_000).toDouble(), 0),
                de(r.nextDouble(0.5, 12.0), 1) + " %", de(r.nextDouble(1.0, 40.0), 2))
        },
        fontSize = fontSize, lines = lines, perPage = perPage,
        before = "Tabelle 3: Bevölkerung nach Stadtteilen am 31.12. (erfundene Werte für den Messkorpus).",
        after = "Quelle: eigene Berechnungen; Rundungsdifferenzen möglich.",
    )

    private fun budget(r: Random, name: String, n: Int, fontSize: Float, lines: Boolean) = Spec(
        name,
        listOf(Col(50f, Align.LEFT), Col(170f, Align.LEFT), Col(80f, Align.RIGHT), Col(80f, Align.RIGHT), Col(70f, Align.RIGHT)),
        listOf("Titel", "Bezeichnung", "2026", "2027", "Änderung"),
        ITEMS.shuffled(r).take(n).mapIndexed { i, item ->
            val a = r.nextInt(500, 90_000).toDouble()
            val b = a * r.nextDouble(0.7, 1.4)
            listOf("5${11 + i}${r.nextInt(10, 99)}", item, de(a, 2), de(b, 2), de(b - a, 2))
        },
        groups = listOf(Group(2, 2, "Ansatz in Euro")),
        fontSize = fontSize, lines = lines,
        before = "Haushaltsplan der Professur, Kapitel 0815 (Entwurf, erfunden).",
    )

    private fun paper(r: Random, name: String, fontSize: Float) = Spec(
        name,
        listOf(Col(120f, Align.LEFT), Col(60f, Align.CENTER), Col(60f, Align.CENTER), Col(60f, Align.CENTER), Col(50f, Align.RIGHT)),
        listOf("Model", "Precision", "Recall", "F1", "n"),
        listOf("Baseline", "Rules only", "Small encoder", "Large encoder", "Ensemble", "Human").shuffled(r).take(4 + r.nextInt(3)).map { m ->
            val p = r.nextDouble(0.6, 0.98); val rc = r.nextDouble(0.6, 0.98)
            listOf(m, "%.2f".format(java.util.Locale.ROOT, p), "%.2f".format(java.util.Locale.ROOT, rc), "%.2f".format(java.util.Locale.ROOT, 2 * p * rc / (p + rc)), r.nextInt(80, 400).toString())
        },
        fontSize = fontSize, lines = true,
        before = "Table 2. Results on the held-out set (invented numbers).",
    )

    /** Entwicklungsteil: improve the recognition only with these. */
    val DEV: List<Spec> by lazy {
        val r = Random(95)
        listOf(
            statistics(r, "statistik-a", 24, 12, 9f, false),
            statistics(r, "statistik-b", 9, 99, 10f, true),
            budget(r, "haushalt-a", 10, 9f, false),
            budget(r, "haushalt-b", 6, 10f, true),
            paper(r, "paper-a", 9f),
        )
    }

    /** Prüfteil: other seeds, sizes and lengths; never used to change the recognition. */
    val TEST: List<Spec> by lazy {
        val r = Random(1995)
        listOf(
            statistics(r, "statistik-p1", 27, 10, 8.5f, true),
            statistics(r, "statistik-p2", 14, 99, 9.5f, false),
            statistics(r, "statistik-p3", 20, 8, 10f, false),
            budget(r, "haushalt-p1", 12, 8.5f, true),
            budget(r, "haushalt-p2", 8, 9.5f, false),
            budget(r, "haushalt-p3", 15, 10f, true),
            paper(r, "paper-p1", 8.5f),
            paper(r, "paper-p2", 10f),
        )
    }
}
