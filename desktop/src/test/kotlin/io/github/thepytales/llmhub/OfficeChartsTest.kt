package io.github.thepytales.llmhub

import io.github.thepytales.llmhub.docs.CellRich
import io.github.thepytales.llmhub.docs.ChartSpec
import io.github.thepytales.llmhub.docs.MarkdownDocx
import io.github.thepytales.llmhub.docs.OfficeImport
import io.github.thepytales.llmhub.docs.PptxWriter
import io.github.thepytales.llmhub.docs.OfficeCharts
import io.github.thepytales.llmhub.docs.SheetChart
import io.github.thepytales.llmhub.docs.SheetEngine
import io.github.thepytales.llmhub.docs.SheetMeta
import io.github.thepytales.llmhub.docs.XlsxReader
import io.github.thepytales.llmhub.docs.XlsxWriter
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.util.zip.ZipInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** AP-48/AP-49: formatting inside cells and real Office charts (Excel, Word, PowerPoint). */
class OfficeChartsTest {
    private fun zip(bytes: ByteArray): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { z ->
            var e = z.nextEntry
            while (e != null) {
                out[e.name] = if (e.name.endsWith(".xlsx")) "(xlsx ${z.readBytes().size})" else z.readBytes().toString(Charsets.UTF_8)
                e = z.nextEntry
            }
        }
        return out
    }

    @Test
    fun formattingInsideACell() {
        val raw = "Note **sehr** gut und <span style=\"color:#C00000\">rot</span>"
        assertTrue(CellRich.isRich(raw))
        assertEquals("Note sehr gut und rot", CellRich.plain(raw))
        assertFalse(CellRich.isRich("2*3*4"), "a product is no italic text")
        assertFalse(CellRich.isRich("=A1*2*B1"), "formulas stay as typed")
        // Formulas see the text without marks.
        val sheet = SheetEngine.Sheet(listOf(listOf("Text", "Länge"), listOf(raw, "=LÄNGE(A2)"), listOf("**12**", "=A3*2")))
        assertEquals("21", sheet.display(1, 1))
        assertEquals("24", sheet.display(2, 1), "a bold number still counts as number")
        // Italic for „gut“ (plain 10 until 13), then off again.
        val italic = CellRich.toggle(raw, 10, 13, CellRich.Mark.ITALIC)
        assertEquals("Note sehr gut und rot", CellRich.plain(italic))
        assertTrue(CellRich.has(italic, 10, 13, CellRich.Mark.ITALIC))
        assertTrue(CellRich.has(italic, 5, 9, CellRich.Mark.BOLD))
        assertFalse(CellRich.has(CellRich.toggle(italic, 10, 13, CellRich.Mark.ITALIC), 10, 13, CellRich.Mark.ITALIC))
        val colored = CellRich.color("Nur ein Wort", 4, 7, "#00A000")
        assertEquals("Nur <span style=\"color:#00A000\">ein</span> Wort", colored)
        // Mapping between what the formula bar shows and the stored text.
        val p = CellRich.parse(raw)
        assertEquals(5, p.plainOffset(raw.indexOf("sehr")))
        assertEquals(raw.indexOf("sehr"), p.rawOffset(5))
    }

    @Test
    fun richCellsSurviveExcel() {
        val rows = listOf(
            listOf("Name", "Bemerkung"),
            listOf("Anna", "Abgabe **pünktlich**, <u>sehr</u> gut"),
            listOf("Ben", "<span style=\"color:#C00000\">fehlt</span>"),
        )
        val file = Files.createTempFile("rich", ".xlsx").toFile()
        XlsxWriter.writeTabs(file, listOf("Noten" to SheetMeta.compose(rows, SheetMeta())))
        val sheet = zip(file.readBytes())["xl/worksheets/sheet1.xml"]!!
        assertTrue("<r><rPr><b/><sz val=\"11\"/><rFont val=\"Calibri\"/></rPr><t xml:space=\"preserve\">pünktlich</t></r>" in sheet, sheet)
        assertTrue("<u/>" in sheet && "FFC00000" in sheet)
        val back = SheetEngine.grid(XlsxReader.read(file.readBytes()).single().second)
        assertEquals(rows, back)
        file.delete()
    }

    private val grades = listOf(
        listOf("Note", "Anzahl", "Anteil"),
        listOf("1", "4", "0,2"),
        listOf("2", "7", "0,35"),
        listOf("3", "6", "0,3"),
        listOf("4", "3", "0,15"),
    )

    @Test
    fun chartsAreRealExcelChartsAndComeBack() {
        val raw = listOf(listOf("Punkte")) + listOf(12, 35, 48, 51, 55, 61, 64, 70, 72, 77, 81, 90).map { listOf("$it") }
        val meta = SheetMeta(
            charts = listOf(
                SheetChart("bar", "A1:B5", "Notenverteilung", "Note", "Anzahl", id = "vert1", labels = true),
                SheetChart("pie", "A1:B5", "Anteile", id = "kreis2", labels = true),
                SheetChart("line", "A1:C5", "Verlauf", id = "linie3", labels = true),
            ),
        )
        val other = SheetMeta(charts = listOf(SheetChart("histogram", "A1:A13", "Punkte", id = "hist4"), SheetChart("box", "A1:A13", "Streuung", id = "box5")))
        val file = Files.createTempFile("charts", ".xlsx").toFile()
        XlsxWriter.writeTabs(file, listOf("Noten 2026" to SheetMeta.compose(grades, meta), "Rohwerte" to SheetMeta.compose(raw, other)))
        val parts = zip(file.readBytes())
        val c1 = parts["xl/charts/chart1.xml"]!!
        assertTrue("<c:barChart><c:barDir val=\"col\"/><c:grouping val=\"clustered\"/>" in c1, c1)
        assertTrue("<c:f>'Noten 2026'!\$B\$2:\$B\$5</c:f>" in c1, "values point at the cells: $c1")
        assertTrue("<c:f>'Noten 2026'!\$A\$2:\$A\$5</c:f>" in c1 && "<c:f>'Noten 2026'!\$B\$1</c:f>" in c1)
        assertTrue("<c:pieChart>" in parts["xl/charts/chart2.xml"]!!)
        assertTrue("<c:lineChart>" in parts["xl/charts/chart3.xml"]!! && "\$C\$2:\$C\$5" in parts["xl/charts/chart3.xml"]!!)
        assertTrue("<c:numLit>" in parts["xl/charts/chart4.xml"]!!, "a histogram's classes are values")
        assertTrue("<drawing r:id=\"rIdDr\"/>" in parts["xl/worksheets/sheet1.xml"]!!)
        assertTrue("Lora-Diagramm vert1" in parts["xl/drawings/drawing1.xml"]!!)
        assertTrue("Histogramm von A1:A13" in parts["xl/drawings/drawing2.xml"]!!)
        assertTrue("drawingml.chart+xml" in parts["[Content_Types].xml"]!!)

        val tabs = XlsxReader.read(file.readBytes())
        val first = SheetMeta.parse(tabs[0].second).charts
        assertEquals(listOf("bar", "pie", "line"), first.map { it.type })
        assertEquals(listOf("A1:B5", "A1:B5", "A1:C5"), first.map { it.range })
        assertEquals(listOf("vert1", "kreis2", "linie3"), first.map { it.id })
        assertEquals("Notenverteilung", first[0].title)
        assertEquals("Note", first[0].xLabel)
        assertEquals("Anzahl", first[0].yLabel)
        val second = SheetMeta.parse(tabs[1].second).charts
        assertEquals(listOf("histogram" to "A1:A13", "box" to "A1:A13"), second.map { it.type to it.range })
        file.delete()
    }

    @Test
    fun foreignExcelChartBecomesAnEditorChart() {
        // As Excel writes it: the chart on the sheet, its data on another one, no Lora names.
        val xml = OfficeCharts.xml(
            ChartSpec(ChartSpec.Type.HBAR, "Umsatz", labels = listOf("A", "B"), series = listOf(ChartSpec.Series("2026", listOf(3.0, 4.0)))),
            listOf(OfficeCharts.Refs("Daten!\$C\$1", "Daten!\$A\$2:\$A\$3", "Daten!\$C\$2:\$C\$3")),
        )
        val read = assertNotNull(OfficeCharts.read(xml.toByteArray()))
        assertEquals("hbar", read.type)
        assertEquals("Umsatz", read.title)
        val (sheet, chart) = assertNotNull(read.sheetChart("x"))
        assertEquals("Daten", sheet)
        assertEquals("A1:C3", chart.range)
        val spec = assertNotNull(read.spec())
        assertEquals(listOf("A", "B"), spec.labels)
        assertEquals(listOf(3.0, 4.0), spec.series.single().values)
    }

    @Test
    fun wordAndPowerPointGetRealCharts() {
        val block = "```chart\n{\"type\":\"bar\",\"title\":\"Notenverteilung\",\"x_label\":\"Note\",\"labels\":[\"1\",\"2\",\"3\"],\"series\":[{\"name\":\"Anzahl\",\"values\":[4,7,6]}]}\n```"
        val docx = Files.createTempFile("bericht", ".docx").toFile()
        MarkdownDocx.write(docx, "Bericht", "# Bericht\n\nDie Verteilung:\n\n$block\n\nEnde.\n\n```chart\n{\"type\":\"box\",\"title\":\"Streuung\",\"series\":[{\"name\":\"a\",\"values\":[1,2,3,4]}]}\n```\n")
        val w = zip(docx.readBytes())
        assertTrue("<c:barChart>" in w["word/charts/chart1.xml"]!! && "Notenverteilung" in w["word/charts/chart1.xml"]!!)
        assertTrue("charts/chart1.xml" in w["word/_rels/document.xml.rels"]!!)
        assertTrue("word/embeddings/Microsoft_Excel_Worksheet1.xlsx" in w)
        assertTrue("/word/charts/chart1.xml" in w["[Content_Types].xml"]!! && "Extension=\"xlsx\"" in w["[Content_Types].xml"]!!)
        assertTrue("word/charts/chart2.xml" !in w, "a box plot keeps its data table")
        assertTrue("Streuung" in w["word/document.xml"]!!)
        // Back from Word: the chart is a chart block again, with its values.
        val md = OfficeImport.docx(docx.readBytes())
        val spec = assertNotNull(ChartSpec.parse(Regex("```chart\\n([\\s\\S]*?)\\n```").find(md)!!.groupValues[1]))
        assertEquals("Notenverteilung", spec.title)
        assertEquals(listOf(4.0, 7.0, 6.0), spec.series.single().values)
        assertEquals(listOf("1", "2", "3"), spec.labels)
        docx.delete()

        val pptx = Files.createTempFile("folien", ".pptx").toFile()
        PptxWriter.write(pptx, "Vortrag", "# Vortrag\n\n---\n\n<!-- layout: diagramm -->\n## Verteilung\n\n$block\n", "Professur")
        val p = zip(pptx.readBytes())
        val chartPart = p.keys.single { it.startsWith("ppt/charts/chart") && it.endsWith(".xml") }
        assertTrue("<c:barChart>" in p[chartPart]!! && "<c:externalData r:id=\"rIdData\">" in p[chartPart]!!)
        assertTrue(p.keys.any { it.startsWith("ppt/embeddings/") })
        // Back from PowerPoint (PptxReader, AP-51): the chart slide is a chart slide with the values again.
        val deck = io.github.thepytales.llmhub.docs.SlideDeck.parse(OfficeImport.pptx(pptx.readBytes()))
        val chartSlide = deck.first { it.layout == io.github.thepytales.llmhub.docs.SlideDeck.Layout.CHART }
        val back = assertNotNull(io.github.thepytales.llmhub.docs.SlideDeck.chartOf(chartSlide.body))
        assertEquals(listOf(4.0, 7.0, 6.0), back.series.single().values)
        assertEquals("Notenverteilung", back.title)
        pptx.delete()
    }

    @Test
    fun embeddedChartCarriesItsData() {
        val spec = ChartSpec(
            ChartSpec.Type.BAR, "Noten", "Note", "Anzahl", labels = listOf("1", "2", "3"),
            series = listOf(ChartSpec.Series("7a", listOf(2.0, 5.0, 1.0)), ChartSpec.Series("7b", listOf(3.0, 4.0, 2.0))),
        )
        val e = assertNotNull(OfficeCharts.embedded(spec, "../embeddings/Microsoft_Excel_Worksheet1.xlsx"))
        assertTrue("<c:externalData r:id=\"rIdData\">" in e.chartXml)
        assertTrue("<c:f>Tabelle1!\$C\$2:\$C\$4</c:f>" in e.chartXml, e.chartXml)
        assertTrue("Microsoft_Excel_Worksheet1.xlsx" in e.relsXml)
        val data = SheetEngine.grid(XlsxReader.read(e.workbook).single().second)
        assertEquals(listOf("Note", "7a", "7b"), data[0])
        assertEquals(listOf("3", "1", "2"), data[3])
        val back = assertNotNull(OfficeCharts.read(e.chartXml.toByteArray())?.spec())
        assertEquals(spec.series, back.series)
        assertEquals(spec.labels, back.labels)
    }
}
