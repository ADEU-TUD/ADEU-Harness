package io.github.thepytales.llmhub

import io.github.thepytales.llmhub.docs.CellStyle
import io.github.thepytales.llmhub.docs.ChartSpec
import io.github.thepytales.llmhub.docs.CondRule
import io.github.thepytales.llmhub.docs.OfficeImport
import io.github.thepytales.llmhub.docs.SheetChart
import io.github.thepytales.llmhub.docs.SheetEngine
import io.github.thepytales.llmhub.docs.SheetMeta
import io.github.thepytales.llmhub.docs.SheetTemplates
import io.github.thepytales.llmhub.docs.SheetTools
import io.github.thepytales.llmhub.docs.XlsxWriter
import io.github.thepytales.llmhub.docs.toJson
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Table editor extras: dates, dynamic arrays, other sheets, formats, filling, clipboard, charts and templates. */
class SheetExtrasTest {
    private fun eval(formula: String, rows: List<List<String>> = emptyList()) =
        SheetEngine.Sheet(rows + listOf(listOf(formula))).display(rows.size, 0)

    @Test
    fun datesAndDateArithmetic() {
        assertEquals(46115.0, SheetEngine.parseDate("03.04.2026"))
        assertEquals(46115.0, SheetEngine.parseDate("2026-04-03"))
        assertEquals(1.0, SheetEngine.serial(java.time.LocalDate.of(1900, 1, 1)))
        assertEquals(61.0, SheetEngine.serial(java.time.LocalDate.of(1900, 3, 1)))
        assertEquals("03.04.2026", SheetEngine.dateText(46115.0))
        val rows = listOf(listOf("Start", "Ende"), listOf("03.04.2026", "17.04.2026"))
        fun at(f: String) = SheetEngine.Sheet(rows + listOf(listOf(f))).display(2, 0)
        assertEquals("17.04.2026", at("=A2+14"))
        assertEquals("14", at("=B2-A2"))
        assertEquals("03.04.2026", at("=DATUM(2026;4;3)"))
        assertEquals("01.01.2027", at("=DATUM(2026;13;1)"))
        assertEquals("3", at("=TAG(A2)"))
        assertEquals("4", at("=MONAT(A2)"))
        assertEquals("2026", at("=JAHR(A2)"))
        // 03.04.2026 is a Friday.
        assertEquals("6", at("=WOCHENTAG(A2)"))
        assertEquals("5", at("=WOCHENTAG(A2;2)"))
        assertEquals("14", at("=DATEDIF(A2;B2;\"D\")"))
        assertEquals("0", at("=DATEDIF(A2;B2;\"M\")"))
        assertEquals("11", at("=NETTOARBEITSTAGE(A2;B2)"))
        assertEquals("10", at("=NETTOARBEITSTAGE(A2;B2;DATUM(2026;4;6))"))
        assertEquals("03.10.2026", at("=EDATUM(A2;6)"))
        assertEquals("10.04.2026", at("=ARBEITSTAG(A2;5)"))
        assertEquals("30.04.2026", at("=MONATSENDE(A2;0)"))
        assertEquals("14", at("=KALENDERWOCHE(A2)"))
        assertEquals("53", SheetEngine.Sheet(listOf(listOf("01.01.2027", "=KALENDERWOCHE(A1)"))).display(0, 1))
        assertEquals("1", SheetEngine.Sheet(listOf(listOf("01.01.2027", "=KALENDERWOCHE(A1;1)"))).display(0, 1))
        assertEquals("WAHR", at("=A2<B2"))
        assertEquals("1", at("=ZÄHLENWENN(A2:B2;\">10.04.2026\")"))
        assertEquals("03.04.2026", at("=TEXT(A2;\"TT.MM.JJJJ\")"))
        assertEquals("Freitag, 3. April 2026", at("=TEXT(A2;\"TTTT, T. MMMM JJJJ\")"))
        assertTrue(Regex("\\d\\d\\.\\d\\d\\.\\d{4}").matches(eval("=HEUTE()")))
        // Excel stores the formulas with English names; KALENDERWOCHE counts ISO weeks there too.
        assertEquals("WEEKNUM(A2,21)", SheetEngine.toExcel("=KALENDERWOCHE(A2)"))
        assertEquals("NETWORKDAYS(A2,B2)", SheetEngine.toExcel("=NETTOARBEITSTAGE(A2;B2)"))
        assertEquals("WEEKNUM(A2;1)", OfficeImport.fromExcel("WEEKNUM(A2)"))
    }

    @Test
    fun modernFunctionsAndSpill() {
        val rows = listOf(
            listOf("Name", "Klasse", "Note", "", ""),
            listOf("Anna", "7b", "2", "", ""),
            listOf("Ben", "7a", "5", "", ""),
            listOf("Cleo", "7b", "1", "", ""),
            listOf("Dana", "7a", "6", "", ""),
            listOf("Emil", "7b", "3", "", ""),
        )
        fun sheet(d: String, e: String = "") = SheetEngine.Sheet(rows.mapIndexed { i, r -> if (i == 1) r.take(3) + listOf(d, e) else r })
        val unique = sheet("=EINDEUTIG(B2:B6)")
        assertEquals(listOf("7b", "7a", ""), (1..3).map { unique.display(it, 3) })
        val filter = sheet("=FILTER(A2:A6;C2:C6>=5)")
        assertEquals(listOf("Ben", "Dana", ""), (1..3).map { filter.display(it, 3) })
        assertEquals("keine", SheetEngine.Sheet(rows + listOf(listOf("=FILTER(A2:A6;C2:C6>6;\"keine\")"))).display(6, 0))
        val sorted = sheet("=SORTIEREN(A2:C6;3;-1)")
        assertEquals(listOf("Dana", "Ben", "Emil", "Anna", "Cleo"), (1..5).map { sorted.display(it, 3) })
        assertEquals("6", sorted.display(1, 5))
        assertEquals(listOf("1", "2", "3"), sheet("=SEQUENZ(3)").let { s -> (1..3).map { s.display(it, 3) } })
        // A spill range that is not empty gives #ÜBERLAUF!.
        val blocked = SheetEngine.Sheet(rows.mapIndexed { i, r -> if (i == 1) r.take(3) + listOf("=EINDEUTIG(B2:B6)", "") else if (i == 2) r.take(3) + listOf("x", "") else r })
        assertEquals("#ÜBERLAUF!", blocked.display(1, 3))
        // References to the spill range and to spilled cells.
        val ref = SheetEngine.Sheet(rows.mapIndexed { i, r -> if (i == 1) r.take(3) + listOf("=SORTIEREN(C2:C6)", "=SUMME(D2#)") else if (i == 2) r.take(3) + listOf("", "=D3*10") else r })
        assertEquals("17", ref.display(1, 4))
        assertEquals("20", ref.display(2, 4))
        assertEquals("2", eval("=ZEILE()", listOf(listOf("x"))))
        assertEquals("5", eval("=ZEILE(B5)"))
        assertEquals("3", eval("=SPALTE(C9)"))
        assertEquals("3", eval("=ZEILEN(A1:B3)"))
        assertEquals("7,5", eval("=VRUNDEN(7,4;0,5)"))
        assertEquals("10", eval("=OBERGRENZE(7,2;5)"))
        assertEquals("5", eval("=UNTERGRENZE(7,2;5)"))
        assertEquals("1.234,50", eval("=TEXT(1234,5;\"#.##0,00\")"))
        assertEquals("3,14", eval("=TEXT(3,14159;\"0,00\")"))
        assertEquals("25 %", eval("=TEXT(0,25;\"0 %\")"))
        assertEquals("12,50 €", eval("=TEXT(12,5;\"0,00 €\")"))
        assertEquals("2", eval("=SUMMENPRODUKT((B2:B6=\"7b\")*(C2:C6<3))", rows))
        assertEquals("_xlfn.UNIQUE(B2:B6)", SheetEngine.toExcel("=EINDEUTIG(B2:B6)"))
        assertEquals("_xlfn._xlws.FILTER(A2:A6,C2:C6>=5)", SheetEngine.toExcel("=FILTER(A2:A6;C2:C6>=5)"))
        assertEquals("_xlfn._xlws.SORT(A2:C6,3,-1)", SheetEngine.toExcel("=SORTIEREN(A2:C6;3;-1)"))
        assertEquals("MROUND(A1,0.5)", SheetEngine.toExcel("=VRUNDEN(A1;0,5)"))
        assertEquals("VLOOKUP(A2,E2:F9,2,FALSE)", SheetEngine.toExcel("=SVERWEIS(A2;E2:F9;2;FALSCH)"))
        assertEquals("UNIQUE(B2:B6)", OfficeImport.fromExcel("_xlfn.UNIQUE(B2:B6)"))
        assertEquals("2", SheetEngine.Sheet(listOf(listOf("7b", "=ANZAHL2(_xlfn.UNIQUE(A1:A3))"), listOf("7a"), listOf("7b"))).display(0, 1))
    }

    @Test
    fun referencesToOtherSheets() {
        val book = SheetEngine.Workbook(
            listOf(
                "Übersicht" to listOf(listOf("Klasse", "Schnitt"), listOf("7b", "=MITTELWERT('Noten 7b'!B2:B4)"), listOf("Erste", "=Tabelle2!A1&\"!\""), listOf("Fehlt", "=Gibtsnicht!A1")),
                "Noten 7b" to listOf(listOf("Name", "Note"), listOf("Anna", "2"), listOf("Ben", "3"), listOf("Cleo", "=Übersicht!B1")),
                "Tabelle2" to listOf(listOf("Hallo")),
            ),
        )
        val s = book.sheet("Übersicht")!!
        assertEquals("2,5", s.display(1, 1))
        assertEquals("Hallo!", s.display(2, 1))
        assertEquals("#BEZUG!", s.display(3, 1))
        assertEquals("AVERAGE('Noten 7b'!B2:B4)", SheetEngine.toExcel("=MITTELWERT('Noten 7b'!B2:B4)"))
        assertEquals("SUM(Tabelle2!A1:A3)", SheetEngine.toExcel("=SUMME(Tabelle2!A1:A3)"))
        assertEquals("'Tabelle 2'!A1", SheetEngine.toExcel("=Tab!A1") { if (it == "Tab") "Tabelle 2" else it })
        assertEquals("'7b'!A1", SheetEngine.toExcel("='7b'!A1"))
        assertEquals("'Q1 2026'", SheetEngine.quoteSheet("Q1 2026"))
        assertEquals("'A1'", SheetEngine.quoteSheet("A1"))
        // Cross-sheet references in the .xlsx file.
        val file = Files.createTempFile("blaetter", ".xlsx").toFile()
        XlsxWriter.writeTabs(file, listOf("Übersicht" to SheetEngine.markdown(listOf(listOf("A", "B"), listOf("x", "=SUMME('Noten 7b'!B2:B3)"))), "Noten 7b" to SheetEngine.markdown(listOf(listOf("Name", "Note"), listOf("Anna", "2"), listOf("Ben", "3")))))
        ZipFile(file).use { zip ->
            val xml = zip.getInputStream(zip.getEntry("xl/worksheets/sheet1.xml")).readBytes().toString(Charsets.UTF_8)
            assertTrue("<f>SUM('Noten 7b'!B2:B3)</f><v>5</v>" in xml, xml)
        }
    }

    @Test
    fun shiftFormulaAndFillSeries() {
        assertEquals("=B3+\$C\$1", SheetEngine.shiftFormula("=B2+\$C\$1", 1, 0))
        assertEquals("=SUMME('Q1 2026'!C2:C9)", SheetEngine.shiftFormula("=SUMME('Q1 2026'!B2:B9)", 0, 1))
        assertEquals("=Q1!B3", SheetEngine.shiftFormula("=Q1!B2", 1, 0))
        assertEquals("=A\$2*B3", SheetEngine.shiftFormula("=A\$2*B2", 1, 0))
        assertEquals(listOf("2", "3", "4"), SheetTools.fillSeries(listOf("1"), 3))
        assertEquals(listOf("15", "20"), SheetTools.fillSeries(listOf("5", "10"), 2))
        assertEquals(listOf("0,3", "0,4"), SheetTools.fillSeries(listOf("0,1", "0,2"), 2))
        assertEquals(listOf("04.04.2026", "05.04.2026"), SheetTools.fillSeries(listOf("03.04.2026"), 2))
        assertEquals(listOf("10.04.2026", "17.04.2026"), SheetTools.fillSeries(listOf("27.03.2026", "03.04.2026"), 2))
        assertEquals(listOf("01.03.2026", "01.04.2026"), SheetTools.fillSeries(listOf("01.01.2026", "01.02.2026"), 2))
        assertEquals(listOf("Dienstag", "Mittwoch"), SheetTools.fillSeries(listOf("Montag"), 2))
        assertEquals(listOf("Sa", "So", "Mo"), SheetTools.fillSeries(listOf("Fr"), 3))
        assertEquals(listOf("Februar"), SheetTools.fillSeries(listOf("Januar"), 1))
        assertEquals(listOf("Woche 2", "Woche 3"), SheetTools.fillSeries(listOf("Woche 1"), 2))
        assertEquals(listOf("ja", "ja"), SheetTools.fillSeries(listOf("ja"), 2))
        val rows = listOf(listOf("Nr", "Preis", "Brutto"), listOf("1", "10", "=B2*\$E\$1"), listOf("", "20", ""), listOf("", "30", ""))
        val filled = SheetTools.fill(rows, 1, 0, 3, 2, down = true)
        assertEquals(listOf("1", "2", "3"), filled.drop(1).map { it[0] })
        assertEquals(listOf("10", "20", "30"), filled.drop(1).map { it[1] })
        assertEquals(listOf("=B2*\$E\$1", "=B3*\$E\$1", "=B4*\$E\$1"), filled.drop(1).map { it[2] })
        val right = SheetTools.fill(listOf(listOf("Mo", "", "", "")), 0, 0, 0, 3, down = false)
        assertEquals(listOf("Mo", "Di", "Mi", "Do"), right[0])
    }

    @Test
    fun formattingRoundTrip() {
        val rows = listOf(listOf("Posten", "Betrag"), listOf("Bücher", "12,5"), listOf("Anteil", "0,25"))
        val meta = SheetMeta(
            cells = mapOf("B2" to CellStyle(b = true, nf = "eur", color = "#C00000"), "B3" to CellStyle(i = true, nf = "0%", fill = "#FFF2CC", align = "c")),
            rules = listOf(CondRule("B2:B3", "gt", "10", fill = "#FFC7CE", color = "#9C0006"), CondRule("A2:A3", "contains", "-->")),
            charts = listOf(SheetChart("bar", "A1:B3", "Ausgaben")),
            freeze = false,
        )
        val md = SheetMeta.compose(rows, meta)
        assertTrue("<!-- sheet:" in md && "-->" !in md.substringAfter("<!-- sheet:").substringBeforeLast("-->"), md)
        assertEquals(rows, SheetEngine.grid(md))
        assertEquals(meta, SheetMeta.parse(md))
        assertEquals(SheetEngine.markdown(rows), SheetMeta.compose(rows, SheetMeta()))
        val sheet = SheetEngine.Sheet(rows)
        assertEquals("12,50 €", SheetMeta.show(sheet, 1, 1, meta.style(1, 1)))
        assertEquals("25 %", SheetMeta.show(sheet, 2, 1, meta.style(2, 1)))
        assertEquals("1.234,57", SheetMeta.formatValue(SheetEngine.Value.Num(1234.567), "#,##0.00"))
        assertEquals("03.04.2026", SheetMeta.formatValue(SheetEngine.Value.Num(46115.0), "date"))
        // Formats move with inserted rows; a deleted row takes its format along.
        val moved = meta.remapRows(listOf(0, null, 1, 2))
        assertEquals(CellStyle(b = true, nf = "eur", color = "#C00000"), moved.cells["B3"])
        assertEquals("B3:B4", moved.rules[0].range)
        assertEquals("A1:B4", moved.charts[0].range)
        val deleted = meta.remapRows(listOf(0, 2))
        assertEquals(setOf("B2"), deleted.cells.keys)
        assertEquals(CellStyle(i = true, nf = "0%", fill = "#FFF2CC", align = "c"), deleted.cells["B2"])
        // Conditional formatting and the chart of the range.
        val paint = SheetTools.conditional(meta.rules, sheet)
        assertEquals("#FFC7CE", paint[1 to 1]?.fill)
        assertEquals(null, paint[2 to 1])
        val grades = SheetEngine.Sheet(listOf(listOf("Note"), listOf("1"), listOf("5"), listOf("6"), listOf("")))
        val red = SheetTools.conditional(listOf(CondRule("A2:A5", "between", "5", "6")), grades)
        assertEquals(setOf(2 to 0, 3 to 0), red.keys)
        val scale = SheetTools.conditional(listOf(CondRule("A2:A4", "scale", fill = "#FFFFFF", fill2 = "#000000")), grades)
        assertEquals("#FFFFFF", scale[1 to 0]?.fill)
        assertEquals("#000000", scale[3 to 0]?.fill)
        val spec = SheetTools.chartSpec(meta.charts[0], sheet)
        assertNotNull(spec)
        assertEquals(ChartSpec.Type.BAR, spec.type)
        assertEquals(listOf("Bücher", "Anteil"), spec.labels)
        assertEquals("Betrag", spec.series.single().name)
        assertEquals(listOf(12.5, 0.25), spec.series.single().values)
        assertEquals(spec, ChartSpec.parse(spec.toJson()))
        val export = SheetTools.exportMarkdown(md)
        assertTrue("12,50 €" in export && "```chart" in export && "<!--" !in export, export)
    }

    private fun parse(bytes: ByteArray) = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder().parse(ByteArrayInputStream(bytes))

    @Test
    fun xlsxWithStylesRulesFilterAndFreeze() {
        val rows = listOf(
            listOf("Name", "Datum", "Note", "Klasse", "Liste"),
            listOf("Anna", "03.04.2026", "5", "7b", "=EINDEUTIG(D2:D4)"),
            listOf("Ben", "=B2+7", "2", "7a", ""),
            listOf("Cleo", "", "6", "7b", ""),
        )
        val meta = SheetMeta(
            cells = mapOf("A2" to CellStyle(b = true, fill = "#FFF2CC"), "C2" to CellStyle(nf = "0.00", align = "r"), "C3" to CellStyle(nf = "eur", i = true, color = "#1F4E79")),
            rules = listOf(
                CondRule("C2:C4", "between", "5", "6"), CondRule("C2:C4", "lt", "2,5", fill = "#C6EFCE", color = "#006100"),
                CondRule("A2:A4", "contains", "an"), CondRule("C2:C4", "scale", fill = "#F8696B", fill2 = "#63BE7B"), CondRule("D2:D4", "eq", "7b"),
            ),
        )
        val file = Files.createTempFile("formate", ".xlsx").toFile()
        XlsxWriter.writeTabs(file, listOf("Noten 7b" to SheetMeta.compose(rows, meta)))
        ZipFile(file).use { zip ->
            // Every part is well-formed XML.
            zip.entries().toList().forEach { e -> parse(zip.getInputStream(e).readBytes()) }
            val sheet = zip.getInputStream(zip.getEntry("xl/worksheets/sheet1.xml")).readBytes().toString(Charsets.UTF_8)
            val styles = zip.getInputStream(zip.getEntry("xl/styles.xml")).readBytes().toString(Charsets.UTF_8)
            val book = zip.getInputStream(zip.getEntry("xl/workbook.xml")).readBytes().toString(Charsets.UTF_8)
            val types = zip.getInputStream(zip.getEntry("[Content_Types].xml")).readBytes().toString(Charsets.UTF_8)
            assertTrue("state=\"frozen\"" in sheet && "<autoFilter ref=\"A1:E4\"/>" in sheet, sheet)
            assertTrue("<conditionalFormatting sqref=\"C2:C4\"><cfRule type=\"cellIs\"" in sheet && "operator=\"between\"><formula>5</formula><formula>6</formula>" in sheet, sheet)
            assertTrue("operator=\"lessThan\"><formula>2.5</formula>" in sheet && "type=\"containsText\"" in sheet && "SEARCH(&quot;an&quot;,A2)" in sheet, sheet)
            assertTrue("<colorScale>" in sheet && "operator=\"equal\"><formula>&quot;7b&quot;</formula>" in sheet, sheet)
            // The date is a date number with a date format; the formula result is a date too.
            assertTrue(Regex("<c r=\"B2\" s=\"(\\d+)\"><v>46115</v></c>").containsMatchIn(sheet), sheet)
            assertTrue(Regex("<c r=\"B3\" s=\"\\d+\"><f>B2\\+7</f><v>46122</v></c>").containsMatchIn(sheet), sheet)
            // The dynamic array spills in Excel too.
            assertTrue("<c r=\"E2\" t=\"str\" cm=\"1\"><f t=\"array\" ref=\"E2:E3\">_xlfn.UNIQUE(D2:D4)</f><v>7b</v></c>" in sheet, sheet)
            assertTrue("<c r=\"E3\" t=\"inlineStr\"><is><t xml:space=\"preserve\">7a</t></is></c>" in sheet, sheet)
            assertNotNull(zip.getEntry("xl/metadata.xml"))
            assertTrue("sheetMetadata" in types)
            assertTrue("formatCode=\"dd.mm.yyyy\"" in styles && "formatCode=\"#,##0.00\\ &quot;€&quot;\"" in styles, styles)
            assertTrue("<b/>" in styles && "<i/>" in styles && "FFFFF2CC" in styles && "FF1F4E79" in styles && "horizontal=\"right\"" in styles, styles)
            assertTrue("<dxfs count=\"2\">" in styles && "<bgColor rgb=\"FFFFC7CE\"/>" in styles, styles)
            assertTrue("_xlnm._FilterDatabase" in book && "'Noten 7b'!\$A\$1:\$E\$4" in book && "fullCalcOnLoad" in book, book)
        }
        // Back in: bold, fill, number formats and dates.
        val back = OfficeImport.read("formate.xlsx", file.readBytes())!!
        val (name, md) = back.tabs.single()
        assertEquals("Noten 7b", name)
        val grid = SheetEngine.grid(md)
        assertEquals("03.04.2026", grid[1][1])
        assertEquals("=B2+7", grid[2][1])
        assertEquals("=UNIQUE(D2:D4)", grid[1][4])
        assertEquals("", grid[2][4])
        val m = SheetMeta.parse(md)
        assertEquals(CellStyle(b = true, fill = "#FFF2CC"), m.cells["A2"])
        assertEquals(CellStyle(nf = "0.00", align = "r"), m.cells["C2"])
        assertEquals(CellStyle(i = true, color = "#1F4E79", nf = "eur"), m.cells["C3"])
        assertEquals("date", m.cells["B3"]?.nf)
        assertEquals(null, m.cells["B2"])
        assertEquals(null, m.cells["A1"])
        assertEquals("7a", SheetEngine.Sheet(grid).display(2, 4))
    }

    @Test
    fun xlsxImportOfForeignStyles() {
        // A minimal workbook as Excel writes it: shared strings, bold font, built-in date and percent formats.
        val files = mapOf(
            "[Content_Types].xml" to "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"/>",
            "xl/workbook.xml" to "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets><sheet name=\"Liste\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>",
            "xl/_rels/workbook.xml.rels" to "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"x\" Target=\"worksheets/sheet1.xml\"/></Relationships>",
            "xl/sharedStrings.xml" to "<sst xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><si><t>Termin</t></si><si><t>Anteil</t></si></sst>",
            "xl/styles.xml" to "<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><fonts><font><sz val=\"11\"/></font><font><b/><sz val=\"11\"/></font></fonts>" +
                "<fills><fill><patternFill patternType=\"none\"/></fill><fill><patternFill patternType=\"gray125\"/></fill><fill><patternFill patternType=\"solid\"><fgColor rgb=\"FFFFFF00\"/></patternFill></fill></fills>" +
                "<cellXfs><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\"/><xf numFmtId=\"14\" fontId=\"0\" fillId=\"0\"/><xf numFmtId=\"9\" fontId=\"1\" fillId=\"2\"/></cellXfs></styleSheet>",
            "xl/worksheets/sheet1.xml" to "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>" +
                "<row r=\"1\"><c r=\"A1\" t=\"s\"><v>0</v></c><c r=\"B1\" t=\"s\"><v>1</v></c></row>" +
                "<row r=\"2\"><c r=\"A2\" s=\"1\"><v>46115</v></c><c r=\"B2\" s=\"2\"><v>0.25</v></c></row></sheetData></worksheet>",
        )
        val bytes = ByteArrayOutputStream().also { out -> ZipOutputStream(out).use { z -> files.forEach { (n, t) -> z.putNextEntry(ZipEntry(n)); z.write(t.toByteArray()); z.closeEntry() } } }.toByteArray()
        val md = OfficeImport.xlsx(bytes).single().second
        val grid = SheetEngine.grid(md)
        assertEquals(listOf(listOf("Termin", "Anteil"), listOf("03.04.2026", "0,25")), grid)
        assertEquals(CellStyle(b = true, fill = "#FFFF00", nf = "0%"), SheetMeta.parse(md).cells["B2"])
        assertEquals("25 %", SheetMeta.show(SheetEngine.Sheet(grid), 1, 1, SheetMeta.parse(md).style(1, 1)))
    }

    @Test
    fun clipboardTsvCsvAndHtml() {
        val cells = listOf(listOf("Name", "Notiz"), listOf("Anna", "zwei\nZeilen"), listOf("Ben", "sagt \"hallo\""))
        val tsv = SheetTools.toTsv(cells)
        assertEquals("Name\tNotiz\r\nAnna\t\"zwei\nZeilen\"\r\nBen\t\"sagt \"\"hallo\"\"\"", tsv)
        assertEquals(cells, SheetTools.parseClipboard(tsv))
        assertEquals(listOf(listOf("1", "2"), listOf("3", "")), SheetTools.parseClipboard("1\t2\n3\t\n"))
        assertEquals(listOf(listOf("a", "1,5"), listOf("b", "2")), SheetTools.parseClipboard("a;1,5\nb;2"))
        assertEquals(listOf(listOf("Name", "Note"), listOf("Anna", "2")), SheetTools.parseClipboard("| Name | Note |\n| --- | --- |\n| Anna | 2 |"))
        val html = "<html><body><table><tr><th>Name</th><th colspan=\"2\">Noten</th></tr><tr><td><b>Anna</b></td><td>1</td><td>2&nbsp;</td></tr></table></body></html>"
        assertEquals(listOf(listOf("Name", "Noten", ""), listOf("Anna", "1", "2")), SheetTools.parseClipboard(html))
        assertEquals(listOf(listOf("nur ein Wert")), SheetTools.parseClipboard("nur ein Wert"))
        val pasted = SheetTools.pasteAt(listOf(listOf("A", "B"), listOf("1", "2")), 1, 1, listOf(listOf("x", "y"), listOf("z", "w")))
        assertEquals(listOf(listOf("A", "B", ""), listOf("1", "x", "y"), listOf("", "z", "w")), pasted)
    }

    @Test
    fun templatesEvaluateWithoutErrors() {
        SheetTemplates.ALL.forEach { (name, md) ->
            val rows = SheetEngine.grid(md)
            val sheet = SheetEngine.Sheet(rows)
            rows.indices.forEach { r -> rows[r].indices.forEach { c -> assertTrue(!sheet.display(r, c).startsWith("#"), "$name ${SheetEngine.ref(r, c)}: ${rows[r][c]} → ${sheet.display(r, c)}") } }
            val meta = SheetMeta.parse(md)
            assertTrue(meta.rules.isNotEmpty(), name)
            val file = Files.createTempFile("vorlage", ".xlsx").toFile()
            XlsxWriter.writeTabs(file, listOf(name to md))
            ZipFile(file).use { zip -> zip.entries().toList().forEach { e -> parse(zip.getInputStream(e).readBytes()) } }
        }
        val grades = SheetEngine.Sheet(SheetEngine.grid(SheetTemplates.GRADES))
        // Anna: 46 of 50 points → 1, oral 2 → 0,6 · 1 + 0,4 · 2 = 1,4 → 1.
        assertEquals("1", grades.display(1, 2))
        assertEquals("1,4", grades.display(1, 4))
        assertEquals("1", grades.display(1, 5))
        assertEquals("5", grades.display(3, 2))
        val attendance = SheetEngine.Sheet(SheetEngine.grid(SheetTemplates.ATTENDANCE))
        assertEquals("03.11.2026", attendance.display(0, 2))
        // 06.11.2026 is a Friday; the next working day is Monday.
        assertEquals("09.11.2026", attendance.display(0, 6))
        val budget = SheetEngine.Sheet(SheetEngine.grid(SheetTemplates.BUDGET))
        assertEquals("293,6", budget.display(11, 5))
        assertEquals("12,50 €", SheetMeta.formatValue(SheetEngine.Value.Num(12.5), "eur"))
    }
}
