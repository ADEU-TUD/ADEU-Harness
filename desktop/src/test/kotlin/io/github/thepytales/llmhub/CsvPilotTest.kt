package io.github.thepytales.llmhub

import io.github.thepytales.llmhub.docs.CsvPilot
import io.github.thepytales.llmhub.docs.OfficeImport
import io.github.thepytales.llmhub.docs.SheetEngine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * CSV-Lotse (AP-93): typical problem files arrive in columns, with numbers as numbers and dates as dates, without
 * handwork – Excel export (Windows-1252, semicolon, „1.234,56“), LimeSurvey export (UTF-8 with BOM, comma, quotes,
 * line breaks in answers, ISO dates), a file with broken umlauts („Ã¤“), an Excel „Unicode-Text“ (UTF-16, tabs), a
 * Selma/OPAL-like export with title lines above the table and an English file („1,234.56“, 12/31/2026).
 */
class CsvPilotTest {
    private fun cells(bytes: ByteArray, name: String = "x.csv"): List<List<String>> {
        val d = CsvPilot.detect(bytes, name)
        return CsvPilot.rows(bytes, d.settings)
    }

    private fun value(cell: String) = SheetEngine.literal(cell)

    @Test
    fun excelExportInWindows1252() {
        val text = "Name;Betrag;Datum;PLZ\r\nMüller;1.234,56;03.04.2026;01069\r\nGrößer;12,5;31.12.2026;01187\r\nÄrger;-0,75;01.01.2027;01307\r\n"
        val bytes = text.toByteArray(charset("windows-1252"))
        val d = CsvPilot.detect(bytes)
        assertEquals(CsvPilot.Encoding.WINDOWS_1252, d.settings.encoding)
        assertEquals(';', d.settings.separator)
        assertEquals(',', d.settings.decimal)
        assertEquals('.', d.settings.thousands)
        val rows = CsvPilot.rows(bytes, d.settings)
        assertEquals(listOf("Name", "Betrag", "Datum", "PLZ"), rows[0])
        assertEquals("Müller", rows[1][0])
        assertEquals("Größer", rows[2][0])
        assertEquals(SheetEngine.Value.Num(1234.56), value(rows[1][1]))
        assertEquals(SheetEngine.Value.Num(-0.75), value(rows[3][1]))
        assertTrue((value(rows[1][2]) as SheetEngine.Value.Num).date)
        // Postcodes keep their leading zero.
        assertEquals("01069", rows[1][3])
    }

    @Test
    fun limeSurveyExport() {
        val text = "﻿\"id\",\"submitdate\",\"G01Q01\",\"G01Q02\",\"Note\"\n" +
            "\"1\",\"2026-11-10 14:32:05\",\"Ja, gern\",\"Sehr gut\nwirklich\",\"1.7\"\n" +
            "\"2\",\"2026-11-11 09:05:00\",\"Nein\",\"Geht so; naja\",\"2.3\"\n" +
            "\"3\",\"2026-11-12 10:00:00\",\"Vielleicht\",\"\",\"3.0\"\n"
        val bytes = text.toByteArray(Charsets.UTF_8)
        val d = CsvPilot.detect(bytes)
        assertEquals(CsvPilot.Encoding.UTF8, d.settings.encoding)
        assertEquals(',', d.settings.separator)
        assertEquals('.', d.settings.decimal)
        val rows = CsvPilot.rows(bytes, d.settings)
        assertEquals(4, rows.size)
        assertEquals("id", rows[0][0])
        assertEquals("Ja, gern", rows[1][2])
        assertEquals("Sehr gut\nwirklich", rows[1][3])
        assertEquals("Geht so; naja", rows[2][3])
        assertEquals(SheetEngine.Value.Num(1.7), value(rows[1][4]))
        // With seconds the date stays ISO (the sheet reads it) and keeps them.
        assertEquals("2026-11-10 14:32:05", rows[1][1])
        assertTrue((value(rows[2][1]) as SheetEngine.Value.Num).date)
        assertEquals("09.11.2026", SheetEngine.dateText(SheetEngine.parseDate("2026-11-09")!!))
    }

    @Test
    fun brokenUmlautsAreRepaired() {
        // UTF-8 read as Windows-1252 and saved again as UTF-8: „Ã¤“ for „ä“.
        val original = "Ort;Anzahl\nDöbeln;3\nGörlitz;5\nZittau – Süd;7\nStraße;2\n"
        val broken = String(original.toByteArray(Charsets.UTF_8), charset("windows-1252"))
        assertTrue("Ã¶" in broken)
        val bytes = broken.toByteArray(Charsets.UTF_8)
        val d = CsvPilot.detect(bytes)
        assertTrue(d.settings.repair, d.notes.toString())
        val rows = CsvPilot.rows(bytes, d.settings)
        assertEquals(listOf("Döbeln", "Görlitz", "Zittau – Süd", "Straße"), rows.drop(1).map { it[0] })
    }

    @Test
    fun unicodeTextOfExcelWithTabs() {
        val text = "Fach\tStunden\tAnteil\nMathe\t4\t12,5 %\nDeutsch\t5\t15,0 %\n"
        val bytes = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + text.toByteArray(Charsets.UTF_16LE)
        val d = CsvPilot.detect(bytes, "export.txt")
        assertEquals(CsvPilot.Encoding.UTF16LE, d.settings.encoding)
        assertEquals('\t', d.settings.separator)
        val rows = CsvPilot.rows(bytes, d.settings)
        assertEquals("Mathe", rows[1][0])
        assertEquals("12,5 %", rows[1][2])
        assertEquals(SheetEngine.Value.Num(0.125), value(rows[1][2]))
    }

    @Test
    fun titleLinesAboveTheTableAreSkipped() {
        val text = "Teilnehmerliste Vorlesung Statik\nExport vom 10.11.2026\n\nNachname;Vorname;Matrikel;Punkte\nMeier;Anna;4711001;17,5\nSchulz;Ben;4711002;12\nLange;Cem;4711003;9,5\n"
        val bytes = text.toByteArray(charset("windows-1252"))
        val d = CsvPilot.detect(bytes)
        assertEquals(2, d.settings.skip, d.notes.toString())
        val rows = CsvPilot.rows(bytes, d.settings)
        assertEquals(listOf("Nachname", "Vorname", "Matrikel", "Punkte"), rows[0])
        assertEquals("4711001", rows[1][2])
        assertEquals(SheetEngine.Value.Num(17.5), value(rows[1][3]))
    }

    @Test
    fun englishNumbersAndUsDates() {
        val text = "item,amount,date\nPaper,\"1,234.56\",12/31/2026\nPens,3.5,01/15/2027\nInk,1.234,02/28/2027\n"
        val bytes = text.toByteArray(Charsets.UTF_8)
        val d = CsvPilot.detect(bytes)
        assertEquals(',', d.settings.separator)
        assertEquals('.', d.settings.decimal)
        assertEquals(CsvPilot.DateStyle.MDY, d.settings.dates)
        val rows = CsvPilot.rows(bytes, d.settings)
        assertEquals(SheetEngine.Value.Num(1234.56), value(rows[1][1]))
        // In an English file „1.234“ is one point two three four, not one thousand.
        assertEquals(SheetEngine.Value.Num(1.234), value(rows[3][1]))
        assertEquals("31.12.2026", rows[1][2])
        assertEquals("15.01.2027", rows[2][2])
    }

    @Test
    fun openingGoesThroughThePilot() {
        val bytes = "Name;Wert\nÄpfel;1.000,5\n".toByteArray(charset("windows-1252"))
        val md = OfficeImport.read("liste.csv", bytes)!!.tabs.first().second
        val grid = SheetEngine.grid(md)
        assertEquals("Äpfel", grid[1][0])
        assertEquals(SheetEngine.Value.Num(1000.5), value(grid[1][1]))
    }

    @Test
    fun savingInBothFlavours() {
        val rows = listOf(listOf("Name", "Betrag"), listOf("Müller; Söhne", "1.234,5"), listOf("Lee", "12"))
        val excel = CsvPilot.write(rows, CsvPilot.Flavour.EXCEL)
        assertTrue(excel.startsWith("﻿"))
        assertEquals("﻿Name;Betrag\r\n\"Müller; Söhne\";1.234,5\r\nLee;12\r\n", excel)
        val standard = CsvPilot.write(rows, CsvPilot.Flavour.STANDARD)
        assertEquals("Name,Betrag\r\nMüller; Söhne,1234.5\r\nLee,12\r\n", standard)
        // Both read back to the same values.
        for (out in listOf(excel, standard)) {
            val back = cells(out.toByteArray(Charsets.UTF_8))
            assertEquals("Müller; Söhne", back[1][0])
            assertEquals(SheetEngine.Value.Num(1234.5), value(back[1][1]))
        }
    }
}
