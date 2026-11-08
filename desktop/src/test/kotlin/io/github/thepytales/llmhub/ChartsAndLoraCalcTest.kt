package io.github.thepytales.llmhub

import io.github.thepytales.llmhub.docs.ChartLayout
import io.github.thepytales.llmhub.docs.ChartLinks
import io.github.thepytales.llmhub.docs.ChartSpec
import io.github.thepytales.llmhub.docs.DocKind
import io.github.thepytales.llmhub.docs.DocTab
import io.github.thepytales.llmhub.docs.LiveDoc
import io.github.thepytales.llmhub.docs.SheetCalc
import io.github.thepytales.llmhub.docs.SheetChart
import io.github.thepytales.llmhub.docs.SheetEngine
import io.github.thepytales.llmhub.docs.SheetMeta
import io.github.thepytales.llmhub.docs.SheetTools
import io.github.thepytales.llmhub.docs.SlideDeck
import io.github.thepytales.llmhub.docs.toJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** AP-49 Diagramme und Lora rechnet: chart types from ranges, linked charts in documents, distribution, groups, explanations. */
class ChartsAndLoraCalcTest {
    private val grades = listOf(
        listOf("Name", "Gruppe", "Punkte", "Note"),
        listOf("Anna", "A", "81", "2"),
        listOf("Ben", "B", "35", "5"),
        listOf("Cleo", "A", "95", "1"),
        listOf("Dana", "B", "66", "3"),
        listOf("Emil", "A", "70", "2"),
        listOf("Finn", "B", "88", "2"),
    )

    @Test
    fun chartTypesFromRanges() {
        val sheet = SheetEngine.Sheet(grades)
        val bar = assertNotNull(SheetTools.chartSpec(SheetChart("bar", "A1:A7", "Punkte").copy(range = "A1:C7"), sheet))
        assertEquals(ChartSpec.Type.BAR, bar.type)
        assertEquals(ChartLayout.TUD, bar.colors)
        assertEquals(listOf("Anna", "Ben", "Cleo", "Dana", "Emil", "Finn"), bar.labels)
        val hbar = assertNotNull(SheetTools.chartSpec(SheetChart("hbar", "A1:C7"), sheet))
        assertEquals(ChartSpec.Type.HBAR, hbar.type)
        val scatter = assertNotNull(SheetTools.chartSpec(SheetChart("scatter", "C1:D7", "Punkte und Note"), sheet))
        assertEquals(ChartSpec.Type.SCATTER, scatter.type)
        assertEquals(listOf(81.0, 35.0, 95.0, 66.0, 70.0, 88.0), scatter.series.single().x)
        assertEquals("Punkte", scatter.xLabel)
        assertEquals("Note", scatter.yLabel)
        val hist = assertNotNull(SheetTools.chartSpec(SheetChart("histogram", "C1:C7"), sheet))
        assertEquals(ChartSpec.Type.HISTOGRAM, hist.type)
        assertEquals(6, hist.series.single().values.size)
        val box = assertNotNull(SheetTools.chartSpec(SheetChart("box", "C1:D7", xLabel = "Spalte", yLabel = "Wert"), sheet))
        assertEquals(listOf("Punkte", "Note"), box.labels)
        assertEquals("Wert", box.yLabel)
        // The SVG uses the colors of the TUD design.
        assertTrue(ChartLayout.svg(bar).contains("#00008C"))
        assertEquals(bar.colors, ChartSpec.parse(bar.toJson())!!.colors)
    }

    @Test
    fun distributionGroupsAndExplanation() {
        val sheet = SheetEngine.Sheet(grades)
        val dist = assertNotNull(SheetCalc.distribution(grades, 3, sheet))
        assertEquals(5, dist.col)
        val rows = SheetCalc.apply(grades, dist)
        val meta = dist.meta(SheetMeta())
        val after = SheetEngine.Sheet(rows)
        // Grades 1, 2, 3, 5 with counts 1, 3, 1, 1 and shares.
        assertEquals(listOf("1", "2", "3", "5"), (1..4).map { after.display(it, 5) })
        assertEquals(listOf("1", "3", "1", "1"), (1..4).map { after.display(it, 6) })
        assertEquals("50 %", SheetMeta.show(after, 2, 7, meta.style(2, 7)))
        val chart = meta.charts.single()
        assertEquals("F1:G5", chart.range)
        assertEquals(listOf(1.0, 3.0, 1.0, 1.0), SheetTools.chartSpec(chart, after)!!.series.single().values)
        // Many different values: classes of equal width.
        val points = assertNotNull(SheetCalc.distribution(grades, 2, sheet, maxValues = 3, bins = 4))
        val p = SheetEngine.Sheet(SheetCalc.apply(grades, points))
        assertEquals(6, (1..4).sumOf { p.display(it, 6).toInt() })
        // Mean per group like a pivot table.
        val group = assertNotNull(SheetCalc.byGroup(grades, 1, 2, sheet))
        val g = SheetEngine.Sheet(SheetCalc.apply(grades, group))
        assertEquals(listOf("A", "82", "3", "70", "95"), (5..9).map { g.display(1, it) })
        assertEquals(listOf("B", "63", "3", "35", "88"), (5..9).map { g.display(2, it) })
        val text = SheetCalc.explain("=SVERWEIS(A2;\$A\$2:\$D\$7;4;FALSCH)", grades)
        assertTrue(text.startsWith("SVERWEIS: Nachschlagen"), text)
        assertTrue("A2 = Name (Zeile 2)" in text && "A2:D7 = Name (Zeilen 2–7)" in text && "Mit $ bleibt" in text, text)
        assertTrue("ganze Spalte Punkte" in SheetCalc.explain("=SUMME(C:C)", grades))
    }

    @Test
    fun cleaningUnifiesDates() {
        val rows = listOf(
            listOf("Name", "Abgabe", "Note"),
            listOf(" Anna ", "2026-04-03", "2"),
            listOf("Ben", "3.4.26", "5"),
            listOf("Cleo", "03.04.2026", "1"),
            listOf("Dana", "", "3"),
        )
        val (clean, said) = io.github.thepytales.llmhub.agent.SheetLora.clean(rows)
        assertEquals(listOf("03.04.2026", "03.04.2026", "03.04.2026", ""), clean.drop(1).map { it[1] })
        // Grades are no dates; the head row stays.
        assertEquals(listOf("2", "5", "1", "3"), clean.drop(1).map { it[2] })
        assertEquals("Abgabe", clean[0][1])
        assertTrue(said.any { "Datumsangaben" in it } && said.any { "Leerzeichen" in it }, said.toString())
    }

    @Test
    fun linkedChartUpdatesWhenGradesChange() {
        // Fertig, wenn: a distribution with chart from a grade table stands linked in a document and follows changed grades.
        val dist = SheetCalc.distribution(grades, 3, SheetEngine.Sheet(grades))!!
        val sheetMd = SheetMeta.compose(SheetCalc.apply(grades, dist), dist.meta(SheetMeta()))
        val table = LiveDoc.create("Noten 7b", sheetMd, "Sie", kind = DocKind.SHEET)
        val tab = table.tabs.single()
        val chart = SheetMeta.parse(tab.markdown).charts.single()
        val spec = SheetTools.chartSpec(chart, SheetTools.workbook(table.tabs).sheet(0))!!
        val block = ChartLinks.block(spec, ChartLinks.linkTo(table.id, tab.id, chart, 0))
        assertEquals(ChartLinks.Link(table.id, tab.id, chart.id), ChartLinks.links(block).single())
        val report = LiveDoc.create("Bericht", "# Bericht\n\nDie Noten:\n\n$block\n", "Sie")
        var docs = mapOf(table.id to table, report.id to report)
        assertNull(ChartLinks.refreshDoc(report) { docs[it] })
        // Ben gets a 2 instead of a 5: the chart in the report follows.
        val changed = SheetMeta.compose(SheetCalc.apply(grades, dist).mapIndexed { r, row -> if (r == 2) row.toMutableList().apply { set(3, "2") } else row }, SheetMeta.parse(tab.markdown))
        docs = docs + (table.id to table.copy(tabs = listOf(tab.copy(markdown = changed))))
        val fresh = assertNotNull(ChartLinks.refreshDoc(report) { docs[it] })
        val shown = SlideDeck.chartOf(fresh.tabs.single().markdown)!!
        assertEquals(listOf(1.0, 4.0, 1.0, 0.0), shown.series.single().values)
        assertTrue(fresh.tabs.single().markdown.startsWith("# Bericht\n\nDie Noten:"))
        // The same in a presentation: the chart slide stays a chart slide.
        val deck = LiveDoc.create("Vortrag", SlideDeck.markdown(listOf(SlideDeck.Slide("Titel", layout = SlideDeck.Layout.TITLE), SlideDeck.Slide("Noten", block, layout = SlideDeck.Layout.CHART))), "Sie", kind = DocKind.SLIDES)
        val freshDeck = assertNotNull(ChartLinks.refreshDoc(deck) { docs[it] })
        val slide = SlideDeck.parse(freshDeck.tabs.single().markdown)[1]
        assertEquals(SlideDeck.Layout.CHART, slide.layout)
        assertEquals(4.0, SlideDeck.chartOf(slide.body)!!.series.single().values[1])
        // A link to a sheet that is gone leaves the chart as it is.
        assertNull(ChartLinks.refreshDoc(report.copy(tabs = listOf(DocTab(title = "x", markdown = block)))) { null })
    }
}
