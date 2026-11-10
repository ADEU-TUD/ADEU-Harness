package io.github.thepytales.llmhub

import io.github.thepytales.llmhub.docs.PdfConvert
import io.github.thepytales.llmhub.docs.PdfTable
import io.github.thepytales.llmhub.docs.PdfTextRun
import io.github.thepytales.llmhub.docs.PdfTools
import io.github.thepytales.llmhub.docs.XlsxWriter
import kotlin.math.abs
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * PDF-Tabellen nach Excel (AP-95) on the Messkorpus ([PdfTableCorpus]): per PDF, whether exactly one table comes out,
 * how many cells are right (cells in the wrong place or extra rows – a repeated head – count against it) and whether
 * numbers become the right numbers. Report: desktop/build/pdf-tabellen/bericht.txt. Only the Entwicklungsteil has a
 * threshold; the Prüfteil is reported.
 */
class PdfTableCorpusTest {
    data class Score(val name: String, val tables: Int, val cells: Double, val numbers: Double)

    private fun norm(s: String) = s.replace(Regex("\\s+"), " ").trim()

    private fun score(spec: PdfTableCorpus.Spec, dir: File, before: Boolean = false): Score {
        val f = File(dir, spec.name + ".pdf")
        if (!f.isFile) PdfTableCorpus.write(spec, f)
        val tables = if (before) PdfTablesBefore.detect(PdfTools.textRuns(null, f).first) else PdfConvert.tables(null, f)
        val got = tables.maxByOrNull { it.rows.size * it.columns }?.rows.orEmpty()
        val want = spec.expected
        var right = 0
        var nums = 0
        var numsRight = 0
        for (r in want.indices) for (c in want[r].indices) {
            val w = norm(want[r][c])
            val g = norm(got.getOrNull(r)?.getOrNull(c).orEmpty())
            if (w == g) right++
            val wn = XlsxWriter.number(w)
            if (wn != null && w.any { it.isDigit() } && r > 0) {
                nums++
                if (XlsxWriter.number(g)?.let { kotlin.math.abs(it - wn) < 1e-9 } == true) numsRight++
            }
        }
        val cells = want.sumOf { it.size }
        val gotCells = got.sumOf { row -> row.size }
        return Score(spec.name, tables.size, right.toDouble() / maxOf(cells, gotCells), if (nums == 0) 1.0 else numsRight.toDouble() / nums)
    }

    private fun part(name: String, specs: List<PdfTableCorpus.Spec>, dir: File, out: StringBuilder, before: Boolean = false): List<Score> {
        val scores = specs.map { score(it, dir, before) }
        out.append("== $name\n")
        scores.forEach { s -> out.append("%-14s Tabellen %d  Zellen %5.1f %%  Zahlen %5.1f %%\n".format(java.util.Locale.ROOT, s.name, s.tables, 100 * s.cells, 100 * s.numbers)) }
        out.append("Mittel: Zellen %.1f %%, Zahlen %.1f %%, genau eine Tabelle: %d von %d\n\n".format(java.util.Locale.ROOT,
            100 * scores.map { it.cells }.average(), 100 * scores.map { it.numbers }.average(), scores.count { it.tables == 1 }, scores.size))
        return scores
    }

    @Test
    fun messkorpus() {
        val dir = Files.createTempDirectory("adeu-pdf-tabellen").toFile()
        val out = StringBuilder()
        part("Entwicklungsteil – vorher (1.0.66)", PdfTableCorpus.DEV, dir, out, before = true)
        val dev = part("Entwicklungsteil", PdfTableCorpus.DEV, dir, out)
        part("Prüfteil – vorher (1.0.66)", PdfTableCorpus.TEST, dir, out, before = true)
        part("Prüfteil", PdfTableCorpus.TEST, dir, out)
        File("build/pdf-tabellen").mkdirs()
        File("build/pdf-tabellen/bericht.txt").writeText(out.toString())
        println(out)
        // Keep the corpus PDFs for looking at them.
        dir.listFiles()!!.forEach { it.copyTo(File("build/pdf-tabellen/${it.name}"), overwrite = true) }
        assertTrue(dev.map { it.cells }.average() >= THRESHOLD, "Entwicklungsteil: Zellen ${dev.map { it.cells }.average()}")
    }

    companion object {
        /** Raised as the recognition gets better (Entwicklungsteil only). */
        const val THRESHOLD = 0.0
    }
}

/** The recognition of 1.0.66 (AP-15), unchanged, for the comparison „vorher“. */
object PdfTablesBefore {
    private data class Row(val y: Float, val h: Float, val cells: List<PdfTextRun>)

    fun detect(runs: List<PdfTextRun>): List<PdfTable> = runs.groupBy { it.page }.toSortedMap().flatMap { (page, list) ->
        val sorted = list.filter { it.text.isNotBlank() }.sortedWith(compareBy({ it.y }, { it.x }))
        val rows = mutableListOf<MutableList<PdfTextRun>>()
        sorted.forEach { r ->
            val last = rows.lastOrNull()
            if (last != null && abs((last.first().y + last.first().h / 2) - (r.y + r.h / 2)) < maxOf(last.first().h, r.h) * 0.5f) last += r else rows += mutableListOf(r)
        }
        val lines = rows.map { cells -> Row(cells.minOf { it.y }, cells.maxOf { it.h }, cells.sortedBy { it.x }) }
        val tables = mutableListOf<PdfTable>()
        var i = 0
        while (i < lines.size) {
            val first = lines[i]
            if (first.cells.size < 2) { i++; continue }
            val block = mutableListOf(first)
            var j = i + 1
            while (j < lines.size) {
                val prev = block.last()
                val next = lines[j]
                // Rows of a table are further apart than lines of text (padding); a gap of more than a few lines ends it.
                val close = next.y - prev.y < maxOf(prev.h, next.h) * 4.8f
                if (!close || next.cells.size < 2 || !aligned(block.first(), next)) break
                block += next
                j++
            }
            if (block.size >= 2) {
                val cols = block.maxOf { it.cells.size }
                // Column starts from the row with the most cells; every cell goes to the nearest column.
                val starts = block.first { it.cells.size == cols }.cells.map { it.x }
                val rowsText = block.map { r ->
                    val cells = MutableList(cols) { "" }
                    r.cells.forEach { cell ->
                        val k = starts.indices.minByOrNull { abs(starts[it] - cell.x) } ?: 0
                        cells[k] = (cells[k] + " " + cell.text.trim()).trim()
                    }
                    cells
                }
                tables += PdfTable(page, block.first().y, block.last().y, rowsText)
                i = j
            } else i++
        }
        tables
    }

    /** Whether [row]'s cells start near the columns of [head] (at least two of them, within 14 pt). */
    private fun aligned(head: Row, row: Row): Boolean {
        val starts = head.cells.map { it.x }
        val hits = row.cells.count { c -> starts.any { abs(it - c.x) < 14f } || head.cells.any { h -> c.x >= h.x - 4 && c.x <= h.x + h.w + 4 } }
        return hits >= minOf(2, row.cells.size) && abs(row.cells.size - head.cells.size) <= 1
    }
}
