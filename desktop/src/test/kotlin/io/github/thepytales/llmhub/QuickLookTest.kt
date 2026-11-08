package io.github.thepytales.llmhub

import io.github.thepytales.llmhub.files.FileDetails
import java.io.File
import java.nio.file.Files
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Schnellansicht und Details-Spalte in Dateien (AP-66): what the details say and how ←/→ go through the files. */
class QuickLookTest {
    private val base = Files.createTempDirectory("schnellansicht").toFile()

    @AfterTest
    fun cleanUp() { base.deleteRecursively() }

    @Test
    fun detailsOfAFileInTheLoraFolder() {
        val root = File(base, "Lora")
        val dir = File(root, "Erstellt/Briefe").apply { mkdirs() }
        val f = File(dir, "Einladung.txt").apply { writeText("Liebe Kolleginnen und Kollegen, herzliche Einladung zum Kolloquium am 12.11.") }
        f.setLastModified(1_800_000_000_000L)
        val dates = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY)
        val rows = FileDetails.rows(f, "Text", root, extra = listOf(FileDetails.Row("Wörter", "${FileDetails.words(f.readText())}"), FileDetails.Row("Leer", "")), tags = listOf("Lehre"), dates = dates)
        val map = rows.associate { it.label to it.value }
        assertEquals("Text", map["Art"])
        assertEquals("${f.length()} B", map["Größe"])
        assertEquals(dates.format(java.util.Date(1_800_000_000_000L)), map["Geändert"])
        assertEquals("10", map["Wörter"], "das Datum ist ein Wort")
        assertEquals("#Lehre", map["Schlagwörter"])
        assertEquals("Dateien › Erstellt › Briefe", map["Ort"])
        assertTrue("Leer" !in map, "leere Angaben fallen weg")
        // Outside the Lora-Ordner: the real folder.
        val other = File(base, "anderswo.txt").apply { writeText("x") }
        assertEquals(base.absoluteFile.normalize().path, FileDetails.rows(other, "Text", root).first { it.label == "Ort" }.value)
        // A folder counts its entries.
        val folder = FileDetails.rows(dir, "Ordner", root).associate { it.label to it.value }
        assertEquals("1 Einträge", folder["Inhalt"])
    }

    @Test
    fun sizesWordsAndSteps() {
        assertEquals("512 B", FileDetails.size(512))
        assertEquals("2 KB", FileDetails.size(2048))
        assertEquals("1,5 MB", FileDetails.size(1_572_864))
        assertEquals(5, FileDetails.words("Das ist's – ein E-Mail-Test 2026"))
        val keys = listOf("a", "b", "c")
        assertEquals("b", FileDetails.step(keys, "a", 1))
        assertEquals("c", FileDetails.step(keys, "a", -1), "←/→ gehen im Kreis")
        assertEquals("a", FileDetails.step(keys, "c", 1))
        assertEquals("a", FileDetails.step(keys, "x", 1), "unbekannt: die erste")
        assertNull(FileDetails.step(emptyList<String>(), "a", 1))
    }
}
