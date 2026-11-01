package io.github.thepytales.llmhub

import android.content.Context
import io.github.thepytales.llmhub.data.FileKind
import io.github.thepytales.llmhub.data.FileLibrary
import io.github.thepytales.llmhub.data.FileSource
import io.github.thepytales.llmhub.docs.DocKind
import io.github.thepytales.llmhub.docs.LiveDoc
import io.github.thepytales.llmhub.docs.MarkdownDocx
import io.github.thepytales.llmhub.docs.MarkdownPdf
import io.github.thepytales.llmhub.docs.TextDiff
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Dateiverwaltung (AP-18): one catalog of documents, storage locations and knowledge; folders, tags, full-text search. */
class FileLibraryTest {
    private val base = Files.createTempDirectory("adeu-dateien").toFile()
    private val place = File(base, "Ablage").apply { mkdirs() }
    private val c by lazy {
        if (System.getProperty("adeu.data").isNullOrBlank()) System.setProperty("adeu.data", File(base, "daten").absolutePath)
        AppContainer(Context(File(base, "files").apply { mkdirs() }, File(base, "cache").apply { mkdirs() }))
    }

    @AfterTest
    fun cleanUp() {
        base.deleteRecursively()
    }

    private fun setUp(): LiveDoc {
        c.docs.addVault("Lehre", place.absolutePath, makeDefault = false)
        MarkdownPdf.write(File(place, "Ausschreibung.pdf"), "Ausschreibung", "# Ausschreibung\n\nDie Förderung für Lehrinnovation beträgt bis zu 25000 Euro.")
        File(place, "Vertraege").mkdirs()
        MarkdownDocx.write(File(place, "Vertraege/Kooperationsvertrag.docx"), "Kooperationsvertrag", "# Kooperationsvertrag\n\nZwischen der SLUB und der Professur für Erziehungswissenschaft.")
        File(place, "notiz.txt").writeText("Rückruf bei Frau Beispiel wegen Raumplanung.")
        File(place, "foto.png").writeBytes(byteArrayOf(-119, 80, 78, 71))
        File(place, ".versteckt.txt").writeText("geheim")
        val doc = LiveDoc.create("Seminarplan Didaktik", "# Seminarplan\n\nSitzung 1: Einführung in die Didaktik.", "Sie", kind = DocKind.DOC)
        c.docs.save(doc)
        c.knowledge.add("Modulhandbuch", "Modul EW-1: Prüfungsleistung ist eine Hausarbeit.", "")
        return doc
    }

    @Test
    fun everythingIsInOneCatalogWithPlacesAndFolders() {
        val doc = setUp()
        val items = c.fileLibrary.items()
        val names = items.map { it.name }
        assertTrue(listOf("Ausschreibung.pdf", "Kooperationsvertrag.docx", "notiz.txt", "foto.png", "Seminarplan Didaktik", "Modulhandbuch").all { it in names }, names.toString())
        assertTrue(".versteckt.txt" !in names, "keine versteckten Dateien")
        val contract = items.first { it.name == "Kooperationsvertrag.docx" }
        assertEquals(FileSource.DISK, contract.source)
        assertEquals("Lehre", contract.place)
        assertEquals("Vertraege", contract.folder)
        assertEquals(FileKind.WORD, contract.kind)
        val seminar = items.first { it.docId == doc.id }
        // AP-16: documents are files in the Lora-Ordner, their folder is the real one.
        assertEquals(FileLibrary.LORA, seminar.place)
        assertEquals("", seminar.folder)
        assertEquals(FileKind.KNOWLEDGE, items.first { it.name == "Modulhandbuch" }.kind)
        // Schlagwörter of documents stay in the app.
        c.fileLibrary.setTags(seminar.key, listOf("#Lehre", "lehre", " Didaktik "))
        val again = c.fileLibrary.items().first { it.docId == doc.id }
        assertEquals(listOf("Lehre", "Didaktik"), again.tags)
        assertEquals(listOf("Lehre", "Didaktik"), c.fileLibrary.allTags(c.fileLibrary.items()))
        // „Zuletzt bearbeitet“: newest first.
        File(place, "notiz.txt").setLastModified(System.currentTimeMillis() + 60_000)
        assertEquals("notiz.txt", c.fileLibrary.items().first().name)
    }

    @Test
    fun fullTextSearchOverAllFormats() {
        setUp()
        val lib = c.fileLibrary
        val items = lib.items()
        // Before the texts were read, the chat's search only finds names.
        assertTrue(lib.search("Lehrinnovation", items, extract = false).isEmpty())
        lib.index(items)
        assertNull(lib.indexing.value)
        val pdf = lib.search("Lehrinnovation 25000", items).single()
        assertEquals("Ausschreibung.pdf", pdf.item.name)
        assertEquals(1, pdf.page)
        assertTrue("Lehrinnovation" in pdf.snippet, pdf.snippet)
        // Accents and case do not matter; Word, text, editor documents and knowledge are found too.
        assertEquals("Ausschreibung.pdf", lib.search("forderung", items).first().item.name)
        assertEquals("Kooperationsvertrag.docx", lib.search("SLUB Professur", items).single().item.name)
        assertEquals("notiz.txt", lib.search("raumplanung", items).single().item.name)
        assertEquals("Seminarplan Didaktik", lib.search("Einführung Didaktik", items).first().item.name)
        assertEquals("Modulhandbuch", lib.search("Hausarbeit", items).single().item.name)
        assertTrue(lib.search("Hausarbeit Lehrinnovation", items).isEmpty(), "alle Wörter müssen vorkommen")
        // A name counts more than the text; after reading, the chat's search finds the content without waiting.
        assertEquals("Ausschreibung.pdf", lib.search("Ausschreibung", items).first().item.name)
        assertEquals(1, lib.search("Lehrinnovation", items, extract = false).size)
        // Schlagwörter are searched too.
        lib.setTags(items.first { it.name == "notiz.txt" }.key, listOf("Telefonat"))
        assertEquals("notiz.txt", lib.search("telefonat", lib.items()).single().item.name)
        // The text cache notices changes.
        File(place, "notiz.txt").apply { writeText("Neuer Inhalt: Exkursion nach Meißen."); setLastModified(lastModified() + 5_000) }
        assertEquals("notiz.txt", lib.search("Meißen", lib.items()).single().item.name)
        // For the model.
        val text = FileLibrary.describe(lib.search("SLUB", lib.items()))
        assertTrue("Kooperationsvertrag.docx" in text && "Lehre › Vertraege" in text && "Pfad:" in text, text)
        assertEquals("Keine passende Datei gefunden.", FileLibrary.describe(emptyList()))
    }

    @Test
    fun versionsAndFilesCanBeCompared() {
        val doc = setUp()
        val changed = doc.change("KI", "überarbeitet") { d -> d.copy(tabs = listOf(d.tabs[0].copy(markdown = "# Seminarplan\n\nSitzung 1: Einführung in die Fachdidaktik.\n\nSitzung 2: Methoden."))) }
        c.docs.save(changed)
        val versions = c.docs.get(doc.id)!!.versions
        assertTrue(versions.size >= 2)
        val diff = TextDiff.words(versions.first().tabs.first().markdown, versions.last().tabs.first().markdown)
        assertTrue(diff.any { it.op == TextDiff.Op.ADDED && "Methoden" in it.text } && diff.any { it.op == TextDiff.Op.REMOVED && "Didaktik" in it.text })
        val items = c.fileLibrary.items()
        assertEquals(versions.size, items.first { it.docId == doc.id }.versions)
        // Two files of different formats: their texts.
        assertNotNull(c.fileLibrary.text(items.first { it.name == "Kooperationsvertrag.docx" }))
        assertNull(c.fileLibrary.text(items.first { it.name == "foto.png" }), "Bilder haben keinen Text")
    }

    @Test
    fun foldingKeepsPositions() {
        val s = "Größe ÄRGER Straße"
        assertEquals(s.length, FileLibrary.fold(s).length)
        assertEquals("grose arger strase", FileLibrary.fold(s))
        val passage = FileLibrary.passage("a ".repeat(200) + "[Seite 3]\nDas Wichtige steht hier." + " b".repeat(200), 410)
        assertTrue(passage.startsWith("… ") && passage.endsWith(" …") && "[Seite" !in passage, passage)
    }
}
