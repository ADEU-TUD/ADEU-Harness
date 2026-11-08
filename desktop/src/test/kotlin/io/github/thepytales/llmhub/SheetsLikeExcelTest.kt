package io.github.thepytales.llmhub

import io.github.thepytales.llmhub.docs.CellCheck
import io.github.thepytales.llmhub.docs.CellStyle
import io.github.thepytales.llmhub.docs.CondRule
import io.github.thepytales.llmhub.docs.OfficeImport
import io.github.thepytales.llmhub.docs.SheetEngine
import io.github.thepytales.llmhub.docs.SheetHelp
import io.github.thepytales.llmhub.docs.SheetMeta
import io.github.thepytales.llmhub.docs.SheetTools
import io.github.thepytales.llmhub.docs.XlsxReader
import io.github.thepytales.llmhub.docs.XlsxWriter
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** AP-48 Tabellen wie Excel: more functions, whole columns, wildcards, help, merged cells, choice lists, xlsx and large data. */
class SheetsLikeExcelTest {
    private val people = listOf(
        listOf("Name", "Klasse", "Note", "Punkte"),
        listOf("Anna", "7b", "2", "81"),
        listOf("Ben", "7a", "5", "35"),
        listOf("Bert", "7b", "1", "95"),
        listOf("Dana", "7a", "6", "12"),
        listOf("Emil*", "7b", "3", "66"),
    )

    private fun at(formula: String, rows: List<List<String>> = people): String =
        SheetEngine.Sheet(rows + listOf(listOf(formula))).display(rows.size, 0)

    @Test
    fun wholeColumnsAndWildcards() {
        assertEquals("289", at("=SUMME(D:D)"))
        assertEquals("35", at("=SVERWEIS(\"Ben\";A:D;4;FALSCH)"))
        assertEquals("7a", at("=XVERWEIS(\"Dana\";A:A;B:B)"))
        val wb = SheetEngine.Workbook(listOf("Noten" to people, "Liste" to listOf(listOf("Wer", "Note"), listOf("Bert", "=SVERWEIS(A2;Noten!A:C;3;FALSCH)"))))
        assertEquals("1", wb.sheet("Liste")!!.display(1, 1))
        // Excel's wildcards in criteria and exact lookups; ~ escapes them.
        assertEquals("2", at("=ZÄHLENWENN(A2:A6;\"B*\")"))
        assertEquals("3", at("=ZÄHLENWENN(A2:A6;\"<>B*\")"))
        assertEquals("1", at("=ZÄHLENWENN(A2:A6;\"?en\")"))
        assertEquals("1", at("=ZÄHLENWENN(A2:A6;\"Emil~*\")"))
        assertEquals("130", at("=SUMMEWENNS(D2:D6;A2:A6;\"B*\")"))
        assertEquals("95", at("=SVERWEIS(\"Be?t\";A2:D6;4;FALSCH)"))
        assertEquals("4", at("=VERGLEICH(\"D*\";A2:A6;0)"))
    }

    @Test
    fun lookupModesAndMissingArguments() {
        // Exact or the next smaller / larger value, wildcards, search from the end, a left-out argument.
        assertEquals("Emil*", at("=XVERWEIS(70;D2:D6;A2:A6;;-1)"))
        assertEquals("Anna", at("=XVERWEIS(70;D2:D6;A2:A6;;1)"))
        assertEquals("keiner", at("=XVERWEIS(70;D2:D6;A2:A6;\"keiner\")"))
        assertEquals("Bert", at("=XVERWEIS(\"B*\";A2:A6;A2:A6;;2;-1)"))
        assertEquals("3", at("=XVERGLEICH(\"Bert\";A2:A6)"))
        assertEquals("2", at("=XVERGLEICH(\"7a\";B2:B6;0)"))
        // Grade scale with VERWEIS (sorted thresholds).
        val scale = listOf(listOf("ab", "Note"), listOf("0", "5"), listOf("50", "4"), listOf("65", "3"), listOf("80", "2"), listOf("90", "1"))
        assertEquals("3", at("=VERWEIS(66;A2:A6;B2:B6)", scale))
        assertEquals("0", at("=WENN(1>2;\"x\";)"))
    }

    @Test
    fun moreFunctions() {
        assertEquals("mittel", at("=WAHL(2;\"gut\";\"mittel\";\"schlecht\")"))
        assertEquals("Neuna", at("=ERSETZEN(A2;1;2;\"Neu\")"))
        assertEquals("FALSCH", at("=IDENTISCH(\"anna\";A2)"))
        assertEquals("Max", at("=TEXTVOR(\"Max Muster\";\" \")"))
        assertEquals("tu-dresden.de", at("=TEXTNACH(\"m.muster@tu-dresden.de\";\"@\")"))
        assertEquals("14:30", at("=ZEIT(14;30;0)"))
        assertEquals("14", at("=STUNDE(ZEIT(14;30;0))"))
        assertEquals("3,14", at("=KÜRZEN(PI();2)"))
        assertEquals("-1", at("=VORZEICHEN(-4)"))
        assertEquals("3", at("=LOG(1000)"))
        assertEquals("289", at("=TEILERGEBNIS(9;D2:D6)"))
        assertEquals("95", at("=MAXWENNS(D2:D6;B2:B6;\"7b\")"))
        assertEquals("12", at("=MINWENNS(D2:D6;B2:B6;\"7a\")"))
        assertEquals("35", at("=QUARTILE(D2:D6;1)"))
        assertEquals("WAHR", at("=ISTNV(SVERWEIS(\"Zoe\";A2:D6;2;FALSCH))"))
        assertEquals("7", at("=FEHLER.TYP(SVERWEIS(\"Zoe\";A2:D6;2;FALSCH))"))
        assertEquals("2", at("=FEHLER.TYP(1/0)"))
        assertEquals("Ben", at("=INDIREKT(\"A\"&3)"))
        assertEquals("\$C\$2", at("=ADRESSE(2;3)"))
        assertEquals("03.04.2026", at("=DATWERT(\"03.04.2026\")"))
        assertEquals("WAHR", at("=ISTGERADE(4)"))
        assertEquals("WAHR", at("=XODER(WAHR;FALSCH)"))
        assertEquals("1,5", at("=RANG.MITTELW(5;{5;5;3})".replace("{5;5;3}", "E1:E3"), listOf(listOf("", "", "", "", "5"), listOf("", "", "", "", "5"), listOf("", "", "", "", "3"))))
        // Spilling: text split, frequency of grades, transpose, stacking, columns.
        val split = SheetEngine.Sheet(listOf(listOf("=TEXTTEILEN(\"a;b;c\";\";\")", "", "")))
        assertEquals(listOf("a", "b", "c"), (0..2).map { split.display(0, it) })
        val gradeRows = people.mapIndexed { r, row -> row + listOf("", listOf("Klasse", "2", "4").getOrElse(r) { "" }) } + listOf(listOf("", "", "", "", "=HÄUFIGKEIT(C2:C6;F2:F3)", ""))
        val freq = SheetEngine.Sheet(gradeRows)
        assertEquals(listOf("2", "1", "2"), (6..8).map { freq.display(it, 4) })
        val tr = SheetEngine.Sheet(listOf(listOf("1", "2", "=MTRANS(A1:B1)"), listOf("", "", "")))
        assertEquals(listOf("1", "2"), (0..1).map { tr.display(it, 2) })
        val stack = SheetEngine.Sheet(listOf(listOf("a", "=VSTAPELN(A1:A2;A3)"), listOf("b", ""), listOf("c", "")))
        assertEquals(listOf("a", "b", "c"), (0..2).map { stack.display(it, 1) })
        val cols = SheetEngine.Sheet(people + listOf(listOf("=SPALTENWAHL(A1:D2;1;-1)", "")))
        assertEquals(listOf("Name", "Punkte"), (0..1).map { cols.display(people.size, it) })
        // Excel names with the prefixes of newer functions.
        assertEquals("_xlfn.MAXIFS(D2:D6,B2:B6,\"7b\")", SheetEngine.toExcel("=MAXWENNS(D2:D6;B2:B6;\"7b\")"))
        assertEquals("_xlfn.XLOOKUP(70,D2:D6,A2:A6,,-1)", SheetEngine.toExcel("=XVERWEIS(70;D2:D6;A2:A6;;-1)"))
        assertEquals("=MAXIFS(D2:D6;B2:B6;\"7b\")", "=" + OfficeImport.fromExcel("_xlfn.MAXIFS(D2:D6,B2:B6,\"7b\")"))
    }

    @Test
    fun formulaHelpWhileTyping() {
        val call = assertNotNull(SheetHelp.at("=WENN(SVERWEIS(A2;"))
        assertEquals("SVERWEIS", call.name)
        assertEquals(1, call.argument)
        assertEquals("Matrix", call.parts[call.argument])
        assertEquals("WENN", SheetHelp.at("=WENN(SVERWEIS(A2;B:C;2;FALSCH);")!!.name)
        assertEquals("SUMME", SheetHelp.at("=SUM(B2:B9")!!.name)
        assertNull(SheetHelp.at("=\"SUMME(\""))
        assertNull(SheetHelp.at("Text"))
        assertTrue(SheetHelp.suggestions("=SV").any { it.first == "SVERWEIS" })
        assertTrue(SheetHelp.suggestions("=B2").isEmpty())
        assertEquals("=SVERWEIS(" to 10, SheetHelp.complete("=SV", 3, "SVERWEIS"))
    }

    @Test
    fun mergedCellsChecksWidthsAndFreezing() {
        var meta = SheetMeta().merge(0, 0, 0, 2).merge(3, 1, 4, 1)
        assertEquals(listOf("A1:C1", "B4:B5"), meta.merges)
        assertNotNull(meta.mergeAt(0, 1))
        assertNull(meta.mergeAt(1, 1))
        meta = meta.merge(0, 1, 1, 3)
        assertEquals(listOf("B4:B5", "B1:D2"), meta.merges)
        assertEquals(listOf("B4:B5"), meta.unmerge(0, 0, 0, 0).let { it.unmerge(1, 2, 1, 2) }.merges)
        // Rows inserted above move merged cells, checks and widths along; column order changes widths.
        meta = SheetMeta(merges = listOf("A2:B2"), checks = listOf(CellCheck("C2:C9", listOf("ja", "nein"))), widths = mapOf("C" to 200), frozenRows = 2, frozenCols = 1)
        val moved = meta.remapRows(listOf(0, null, 1, 2, 3, 4, 5, 6, 7, 8))
        assertEquals(listOf("A3:B3"), moved.merges)
        assertEquals("C3:C10", moved.checks.single().range)
        assertEquals(mapOf("D" to 200), meta.remapCols(listOf(0, null, 1, 2)).widths)
        assertEquals(2, meta.topRows)
        assertEquals(200, meta.width(2))
        assertEquals(118, meta.width(0))
        val md = SheetMeta.compose(people, meta)
        assertEquals(meta, SheetMeta.parse(md))
        assertEquals(people, SheetEngine.grid(md))
        assertEquals(SheetEngine.markdown(people), SheetMeta.strip(md))
        assertEquals("ja", SheetMeta.parse(md).checkAt(4, 2)!!.list.first())
    }

    @Test
    fun findReplaceAndChoiceLists() {
        val rows = listOf(listOf("Name", "Note"), listOf("Anna", "=SUMME(B3:B4)"), listOf("anna maria", "2"), listOf("Ben", "3"))
        val sheet = SheetEngine.Sheet(rows)
        assertEquals(listOf(1 to 0, 2 to 0), SheetTools.find(rows, SheetTools.Find("anna")))
        assertEquals(listOf(1 to 0), SheetTools.find(rows, SheetTools.Find("anna", wholeCell = true)))
        assertEquals(listOf(1 to 0), SheetTools.find(rows, SheetTools.Find("Anna", matchCase = true)))
        assertEquals(listOf(1 to 1), SheetTools.find(rows, SheetTools.Find("SUMME")))
        assertEquals(listOf(1 to 1), SheetTools.find(rows, SheetTools.Find("5", inFormulas = false)) { r, c -> sheet.display(r, c) })
        val (next, n) = SheetTools.replaceAll(rows, SheetTools.Find("anna"), "Hanna")
        assertEquals(2, n)
        assertEquals(listOf("Hanna", "Hanna maria"), listOf(next[1][0], next[2][0]))
        assertEquals("=SUMME(C3:C4)", SheetTools.replaceAll(rows, SheetTools.Find("B3:B4"), "C3:C4").first[1][1])
        // Choice lists from typed values or from a range of another sheet.
        val wb = SheetEngine.Workbook(listOf("Noten" to rows, "Listen" to listOf(listOf("Status"), listOf("bestanden"), listOf("nicht bestanden"), listOf(""))))
        val list = CellCheck("C2:C9", SheetTools.parseList("ja; nein, vielleicht"))
        assertEquals(listOf("ja", "nein", "vielleicht"), SheetTools.options(list, wb.sheet(0)))
        val ref = CellCheck("C2:C9", source = "Listen!\$A\$2:\$A\$4")
        assertEquals(listOf("bestanden", "nicht bestanden"), SheetTools.options(ref, wb.sheet(0)))
        assertTrue(SheetTools.allowed(ref, "Bestanden", wb.sheet(0)))
        assertTrue(!SheetTools.allowed(ref, "durchgefallen", wb.sheet(0)))
        assertTrue(SheetTools.allowed(ref, "", wb.sheet(0)))
    }

    /** A workbook as Excel writes it: shared strings, shared formulas, styles, merged cells, rules, a choice list. */
    private fun excelLike(rows: Int): ByteArray {
        val strings = mutableListOf("Matrikel", "Name", "Punkte", "Note", "Ergebnis", "bestanden", "nicht bestanden", "Prüfung WS 2026 &amp; mehr")
        val sheet = StringBuilder()
        sheet.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?><worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">")
        sheet.append("<sheetViews><sheetView workbookViewId=\"0\"><pane xSplit=\"2\" ySplit=\"1\" topLeftCell=\"C2\" state=\"frozen\"/></sheetView></sheetViews>")
        sheet.append("<cols><col min=\"2\" max=\"2\" width=\"25\" customWidth=\"1\"/></cols><sheetData>")
        sheet.append("<row r=\"1\"><c r=\"A1\" t=\"s\" s=\"1\"><v>0</v></c><c r=\"B1\" t=\"s\" s=\"1\"><v>1</v></c><c r=\"C1\" t=\"s\" s=\"1\"><v>2</v></c><c r=\"D1\" t=\"s\" s=\"1\"><v>3</v></c><c r=\"E1\" t=\"s\" s=\"1\"><v>4</v></c></row>")
        for (i in 0 until rows) {
            val r = i + 2
            strings += "Person ${i + 1}"
            val f = if (i == 0) "<f t=\"shared\" ref=\"D2:D${rows + 1}\" si=\"0\">IF(C2&gt;=50,ROUND(5-C2/25,1),5)</f>" else "<f t=\"shared\" si=\"0\"/>"
            val g = if (i == 0) "<f t=\"shared\" ref=\"E2:E${rows + 1}\" si=\"1\">IF(D2&lt;=4,\"bestanden\",\"nicht bestanden\")</f>" else "<f t=\"shared\" si=\"1\"/>"
            sheet.append("<row r=\"$r\"><c r=\"A$r\"><v>${4_000_000 + i}</v></c><c r=\"B$r\" t=\"s\"><v>${strings.size - 1}</v></c><c r=\"C$r\" s=\"2\"><v>${(i * 37) % 101}.5</v></c>")
            sheet.append("<c r=\"D$r\">$f<v>0</v></c><c r=\"E$r\" t=\"str\">$g<v></v></c></row>")
        }
        sheet.append("<row r=\"${rows + 3}\"><c r=\"A${rows + 3}\" t=\"s\"><v>7</v></c></row>")
        sheet.append("</sheetData><mergeCells count=\"1\"><mergeCell ref=\"A${rows + 3}:E${rows + 3}\"/></mergeCells>")
        sheet.append("<conditionalFormatting sqref=\"D2:D${rows + 1}\"><cfRule type=\"cellIs\" dxfId=\"0\" priority=\"1\" operator=\"greaterThan\"><formula>4</formula></cfRule></conditionalFormatting>")
        sheet.append("<dataValidations count=\"1\"><dataValidation type=\"list\" allowBlank=\"1\" showErrorMessage=\"1\" sqref=\"F2:F${rows + 1}\"><formula1>\"ja,nein\"</formula1></dataValidation></dataValidations>")
        sheet.append("</worksheet>")
        val sst = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><sst xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">" +
            strings.joinToString("") { "<si><t>$it</t></si>" } + "</sst>"
        val styles = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">" +
            "<numFmts count=\"1\"><numFmt numFmtId=\"164\" formatCode=\"0.0\"/></numFmts>" +
            "<fonts count=\"2\"><font><sz val=\"11\"/></font><font><b/><sz val=\"11\"/></font></fonts>" +
            "<fills count=\"2\"><fill><patternFill patternType=\"none\"/></fill><fill><patternFill patternType=\"gray125\"/></fill></fills>" +
            "<cellXfs count=\"3\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\"/><xf numFmtId=\"0\" fontId=\"1\" fillId=\"0\"/><xf numFmtId=\"164\" fontId=\"0\" fillId=\"0\" applyNumberFormat=\"1\"/></cellXfs>" +
            "<dxfs count=\"1\"><dxf><font><color rgb=\"FF9C0006\"/></font><fill><patternFill><bgColor rgb=\"FFFFC7CE\"/></patternFill></fill></dxf></dxfs></styleSheet>"
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            fun put(name: String, text: String) { zip.putNextEntry(ZipEntry(name)); zip.write(text.toByteArray()); zip.closeEntry() }
            put("[Content_Types].xml", "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"/>")
            put("xl/workbook.xml", "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets><sheet name=\"OPAL Export\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>")
            put("xl/_rels/workbook.xml.rels", "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"worksheet\" Target=\"worksheets/sheet1.xml\"/></Relationships>")
            put("xl/worksheets/sheet1.xml", sheet.toString())
            put("xl/sharedStrings.xml", sst)
            put("xl/styles.xml", styles)
        }
        return out.toByteArray()
    }

    @Test
    fun excelFidelityRoundTrip() {
        val tabs = XlsxReader.read(excelLike(20))
        val (name, md) = tabs.single()
        assertEquals("OPAL Export", name)
        val rows = SheetEngine.grid(md)
        val meta = SheetMeta.parse(md)
        // Shared formulas: each row gets its own copy with moved references.
        assertEquals("=IF(C2>=50;ROUND(5-C2/25;1);5)", rows[1][3])
        assertEquals("=IF(D7<=4;\"bestanden\";\"nicht bestanden\")", rows[6][4])
        assertEquals("Person 6", rows[6][1])
        assertEquals("Prüfung WS 2026 & mehr", rows[22][0])
        assertEquals(listOf("A23:E23"), meta.merges)
        assertEquals(CondRule("D2:D21", "gt", "4", fill = "#FFC7CE", color = "#9C0006"), meta.rules.single())
        assertEquals(CellCheck("F2:F21", listOf("ja", "nein")), meta.checks.single())
        assertEquals(1, meta.topRows)
        assertEquals(2, meta.frozenCols)
        assertEquals(180, meta.width(1))
        assertEquals(CellStyle(nf = "0.0"), meta.style(1, 2))
        assertEquals("74,5", SheetMeta.show(SheetEngine.Sheet(rows), 3, 2, meta.style(3, 2)))
        // Written again and read back: nothing is lost.
        val file = Files.createTempFile("ap48", ".xlsx").toFile()
        XlsxWriter.writeTabs(file, tabs)
        val again = XlsxReader.read(file.readBytes()).single().second
        assertEquals(rows, SheetEngine.grid(again))
        val meta2 = SheetMeta.parse(again)
        assertEquals(meta.merges, meta2.merges)
        assertEquals(meta.rules, meta2.rules)
        assertEquals(meta.checks, meta2.checks)
        assertEquals(meta.frozenCols, meta2.frozenCols)
        assertEquals(meta.topRows, meta2.topRows)
        assertEquals(meta.width(1), meta2.width(1))
        assertEquals(meta.style(1, 2), meta2.style(1, 2))
        file.delete()
    }

    @Test
    fun opalExportFilterLookupAndSave() {
        // Fertig, wenn: a grade export with several thousand rows is filtered, enriched with SVERWEIS and saved as .xlsx.
        val n = 5_000
        val t0 = System.nanoTime()
        val opal = XlsxReader.read(excelLike(n)).single()
        val rows = SheetEngine.grid(opal.second)
        assertEquals(n + 3, rows.size)
        val tutors = listOf(listOf("Matrikel", "Tutorium")) + (0 until n step 2).map { listOf("${4_000_000 + it}", "Gruppe ${it % 7 + 1}") }
        val enriched = rows.mapIndexed { r, row ->
            row + when {
                r == 0 -> "Tutorium"
                r in 1..n -> "=WENNNV(SVERWEIS(A${r + 1};Tutorien!A:B;2;FALSCH);\"–\")"
                else -> ""
            }
        }
        val tabs = listOf(opal.first to SheetMeta.compose(enriched, SheetMeta.parse(opal.second)), "Tutorien" to SheetEngine.markdown(tutors))
        val wb = SheetTools.workbook(tabs.map { io.github.thepytales.llmhub.docs.DocTab(title = it.first, markdown = it.second) })
        val sheet = wb.sheet(0)
        assertEquals("Gruppe 1", sheet.display(1, 5))
        assertEquals("–", sheet.display(2, 5))
        // Filter like the editor's AutoFilter: passed and in group 3.
        val passed = (1..n).filter { r -> sheet.display(r, 4) == "bestanden" && sheet.display(r, 5) == "Gruppe 3" }
        assertTrue(passed.isNotEmpty())
        val file = Files.createTempFile("opal", ".xlsx").toFile()
        XlsxWriter.writeTabs(file, tabs)
        val back = XlsxReader.read(file.readBytes())
        // Formulas come back in Excel's names (=IFNA(VLOOKUP(…))): the same formula, the same values.
        fun excel(rows: List<List<String>>) = rows.map { r -> r.map { if (it.startsWith("=")) SheetEngine.toExcel(it) else it } }
        assertEquals(excel(enriched), excel(SheetEngine.grid(back[0].second)))
        assertEquals(tutors, SheetEngine.grid(back[1].second))
        assertEquals("Gruppe 1", SheetTools.workbook(back.map { io.github.thepytales.llmhub.docs.DocTab(title = it.first, markdown = it.second) }).sheet(0).display(1, 5))
        file.delete()
        val ms = (System.nanoTime() - t0) / 1_000_000
        println("OPAL $n Zeilen: lesen, SVERWEIS, filtern, speichern, wieder lesen in $ms ms")
        assertTrue(ms < 60_000, "zu langsam: $ms ms")
    }

    @Test
    fun hundredThousandRowsStayFast() {
        val n = 100_000
        val rows = listOf(listOf("Nr", "Name", "Datum", "Punkte", "Gruppe", "Bemerkung")) +
            (1..n).map { listOf("$it", "Person $it", "03.04.2026", "${it % 100}", "G${it % 12}", if (it % 10 == 0) "nachgeholt" else "") }
        var t = System.nanoTime()
        fun lap(): Long = ((System.nanoTime() - t) / 1_000_000).also { t = System.nanoTime() }
        val md = SheetMeta.compose(rows, SheetMeta(widths = mapOf("B" to 160)))
        val compose = lap()
        val parsed = SheetEngine.grid(md)
        val meta = SheetMeta.parse(md)
        val parse = lap()
        assertEquals(rows, parsed)
        assertEquals(160, meta.width(1))
        // Only what is shown is computed: the first screen of a sheet with a formula column.
        val withFormula = parsed.mapIndexed { r, row -> if (r == 0) row + "Doppelt" else row + "=D${r + 1}*2" }
        val sheet = SheetEngine.Sheet(withFormula)
        (0 until 40).forEach { r -> (0 until 7).forEach { c -> sheet.display(r, c) } }
        val screen = lap()
        assertEquals("=SUMME(D:D)".let { SheetEngine.Sheet(withFormula + listOf(listOf("", "", "", "", "", "", "", it))).display(n + 1, 7) }, "4950000")
        val sum = lap()
        val file = Files.createTempFile("big", ".xlsx").toFile()
        XlsxWriter.writeTabs(file, listOf("Groß" to SheetMeta.compose(withFormula, meta)))
        val write = lap()
        val back = XlsxReader.read(file.readBytes()).single().second
        val read = lap()
        assertEquals(n + 1, SheetEngine.grid(back).size)
        file.delete()
        println("100 000 Zeilen: Markdown $compose ms, lesen $parse ms, erster Bildschirm $screen ms, SUMME(D:D) $sum ms, xlsx schreiben $write ms, xlsx lesen $read ms")
        assertTrue(parse < 5_000 && screen < 1_000, "zu langsam: lesen $parse ms, Bildschirm $screen ms")
    }
}
