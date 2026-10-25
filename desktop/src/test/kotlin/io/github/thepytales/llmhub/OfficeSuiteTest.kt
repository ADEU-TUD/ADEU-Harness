package io.github.thepytales.llmhub

import io.github.thepytales.llmhub.agent.LocalFolderTools
import io.github.thepytales.llmhub.docs.DocBlocks
import io.github.thepytales.llmhub.docs.DocKind
import io.github.thepytales.llmhub.docs.DocsStore
import io.github.thepytales.llmhub.docs.LiveDoc
import io.github.thepytales.llmhub.docs.MarkdownDocx
import io.github.thepytales.llmhub.docs.OfficeImport
import io.github.thepytales.llmhub.docs.PptxWriter
import io.github.thepytales.llmhub.docs.SheetEngine
import io.github.thepytales.llmhub.docs.SlideDeck
import io.github.thepytales.llmhub.docs.XlsxWriter
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** The office suite of the editor: tables with formulas, slides, Office import and storage folders. */
class OfficeSuiteTest {
    private val grades = listOf(
        listOf("Name", "Punkte", "Note"),
        listOf("Anna", "92", "=WENN(B2>=90;1;2)"),
        listOf("Ben", "71,5", "=WENN(B3>=90;1;2)"),
        listOf("Summe", "=SUMME(B2:B3)", "=MITTELWERT(C2:C3)"),
        listOf("Runden", "=RUNDEN(B4*1,19;1)", "=ANZAHL(B2:B3)&\" Werte\""),
    )

    @Test
    fun formulasLikeGermanExcel() {
        val sheet = SheetEngine.Sheet(grades)
        assertEquals("1", sheet.display(1, 2))
        assertEquals("2", sheet.display(2, 2))
        assertEquals("163,5", sheet.display(3, 1))
        assertEquals("1,5", sheet.display(3, 2))
        assertEquals("194,6", sheet.display(4, 1))
        assertEquals("2 Werte", sheet.display(4, 2))
        assertEquals("#DIV/0!", SheetEngine.Sheet(listOf(listOf("=1/0"))).display(0, 0))
        assertEquals("#ZIRKEL!", SheetEngine.Sheet(listOf(listOf("=B1", "=A1"))).display(0, 0))
        assertEquals("#NAME?", SheetEngine.Sheet(listOf(listOf("=GIBTSNICHT(1)"))).display(0, 0))
        assertEquals("3", SheetEngine.Sheet(listOf(listOf("ja", "nein", "ja", "=ZÄHLENWENN(A1:C1;\"ja\")+1"))).display(0, 3))
        assertEquals("SUM(B2:B3)", SheetEngine.toExcel("=SUMME(B2:B3)"))
        assertEquals("ROUND(B4*1.19,1)", SheetEngine.toExcel("=RUNDEN(B4*1,19;1)"))
        assertEquals("IF(B2>=90,\"a;b\",2)", SheetEngine.toExcel("=WENN(B2>=90;\"a;b\";2)"))
        assertEquals("RUNDEN(B4*1,19;1)", OfficeImport.fromExcel("RUNDEN(B4*1.19,1)"))
        assertEquals(1 to 27, SheetEngine.parseRef("AB2"))
        assertEquals("AB2", SheetEngine.ref(1, 27))
    }

    @Test
    fun sheetMarkdownAndXlsxRoundTrip() {
        val md = SheetEngine.markdown(grades)
        assertEquals(grades, SheetEngine.grid(md))
        val file = Files.createTempFile("tabelle", ".xlsx").toFile()
        XlsxWriter.writeSheets(file, listOf(XlsxWriter.Sheet("Noten", grades)))
        ZipFile(file).use { zip ->
            val xml = zip.getInputStream(zip.getEntry("xl/worksheets/sheet1.xml")).readBytes().toString(Charsets.UTF_8)
            assertTrue("<f>SUM(B2:B3)</f><v>163.5</v>" in xml, xml)
            assertTrue("<f>IF(B2&gt;=90,1,2)</f>" in xml, xml)
        }
        val back = OfficeImport.read("noten.xlsx", file.readBytes())
        assertNotNull(back)
        assertEquals(DocKind.SHEET, back.kind)
        val rows = SheetEngine.grid(back.tabs.single().second)
        assertEquals("Noten", back.tabs.single().first)
        assertEquals("=SUM(B2:B3)", rows[3][1])
        assertEquals("163,5", SheetEngine.Sheet(rows).display(3, 1))
        assertEquals("71,5", rows[2][1])
    }

    @Test
    fun slidesParseAndWriteBack() {
        val deck = listOf(
            SlideDeck.Slide("Formatives Assessment", "Seminar · 2026", layout = SlideDeck.Layout.TITLE),
            SlideDeck.Slide("Ablauf", "- Einstieg\n- Input\n- Übung", notes = "Zeit im Blick behalten", layout = SlideDeck.Layout.CONTENT),
            SlideDeck.Slide("Vergleich", "- vorher", "- nachher", layout = SlideDeck.Layout.TWO_COLUMNS),
            SlideDeck.Slide("Zitat", "> „Lernen sichtbar machen“", layout = SlideDeck.Layout.QUOTE),
        )
        val md = SlideDeck.markdown(deck)
        assertEquals(deck, SlideDeck.parse(md))
        // The same Markdown becomes the PowerPoint file, and the PowerPoint file comes back as slides.
        val file = Files.createTempFile("folien", ".pptx").toFile()
        PptxWriter.write(file, "Formatives Assessment", md, "TU Dresden")
        val back = OfficeImport.read("folien.pptx", file.readBytes())
        assertNotNull(back)
        assertEquals(DocKind.SLIDES, back.kind)
        val slides = SlideDeck.parse(back.tabs.single().second)
        assertEquals(4, slides.size, back.tabs.single().second)
        assertEquals("Formatives Assessment", slides[0].title)
        assertEquals("Ablauf", slides[1].title)
        assertTrue("Einstieg" in slides[1].body)
        assertEquals("Zeit im Blick behalten", slides[1].notes)
    }

    @Test
    fun wordImportKeepsStructure() {
        val md = "# Protokoll\n\n## Ergebnisse\n\nDie Sitzung war **produktiv**.\n\n- Punkt eins\n- Punkt zwei\n\n| A | B |\n| --- | --- |\n| 1 | 2 |\n"
        val file = Files.createTempFile("protokoll", ".docx").toFile()
        MarkdownDocx.write(file, "Protokoll", md)
        val back = OfficeImport.read("protokoll.docx", file.readBytes())
        assertNotNull(back)
        val text = back.tabs.single().second
        val blocks = DocBlocks.split(text)
        assertTrue(blocks.any { DocBlocks.kindOf(it) == DocBlocks.Kind.H1 && "Protokoll" in it }, text)
        assertTrue(blocks.any { DocBlocks.kindOf(it) == DocBlocks.Kind.H2 && "Ergebnisse" in it }, text)
        assertTrue("**produktiv**" in text, text)
        assertTrue(blocks.any { DocBlocks.kindOf(it) == DocBlocks.Kind.BULLETS && "Punkt zwei" in it }, text)
        assertTrue(blocks.any { DocBlocks.kindOf(it) == DocBlocks.Kind.TABLE }, text)
    }

    @Test
    fun checklists() {
        val block = "- [ ] Raum buchen\n- [x] Einladung senden"
        assertEquals(DocBlocks.Kind.TASKS, DocBlocks.kindOf(block))
        assertEquals(listOf("Raum buchen" to false, "Einladung senden" to true), DocBlocks.tasks(block))
        assertEquals("- [x] Raum buchen\n- [x] Einladung senden", DocBlocks.toggleTask(block, 0))
        assertEquals("- [ ] eins\n- [ ] zwei", DocBlocks.convert("- eins\n- zwei", DocBlocks.Kind.TASKS))
        assertEquals("Raum buchen\nEinladung senden", DocBlocks.plainOf(block))
    }

    @Test
    fun storageFolderMirrorsAndPicksUpOutsideChanges() {
        val appDir = Files.createTempDirectory("app").toFile()
        val vaultDir = Files.createTempDirectory("vault").toFile()
        val store = DocsStore(appDir)
        val vault = store.addVault("Drive", vaultDir.path, makeDefault = true)
        assertEquals(vault.id, store.defaultVault)
        val doc = LiveDoc.create("Notizen", "# Notizen\n\nErster Stand\n", "Sie").copy(vault = vault.id)
        store.save(doc)
        val mirror = File(vaultDir, "Notizen.md")
        assertTrue(mirror.isFile)
        assertTrue(File(vaultDir, ".adeu/${doc.id}.json").isFile)
        // Edited outside the app (Obsidian, another computer).
        mirror.writeText("# Notizen\n\nZweiter Stand\n")
        mirror.setLastModified(System.currentTimeMillis() + 5000)
        store.refresh()
        assertEquals("# Notizen\n\nZweiter Stand\n", store.get(doc.id)!!.tabs.single().markdown)
        // A new Markdown file in the folder becomes a document.
        File(vaultDir, "Idee.md").writeText("# Idee\n\nText")
        store.refresh()
        assertTrue(store.current.any { it.title == "Idee" && it.vault == vault.id })
        // A second app with the same folder (other computer) sees both.
        val other = DocsStore(Files.createTempDirectory("app2").toFile())
        other.addVault("Drive", vaultDir.path, makeDefault = false)
        assertEquals(setOf("Notizen", "Idee"), other.current.map { it.title }.toSet())
        // Renaming renames the copy; moving back into the app removes it from the folder.
        store.save(store.get(doc.id)!!.copy(title = "Besprechung"))
        assertTrue(File(vaultDir, "Besprechung.md").isFile)
        assertTrue(!mirror.exists())
        store.move(doc.id, null)
        assertTrue(!File(vaultDir, "Besprechung.md").exists())
        assertEquals(null, store.get(doc.id)!!.vault)
    }

    @Test
    fun localFoldersStayInside() {
        val root = Files.createTempDirectory("freigabe").toFile().canonicalFile
        File(root, "a").mkdirs()
        assertEquals(File(root, "a/b.txt").canonicalFile, LocalFolderTools.resolve(root, "a/b.txt"))
        assertEquals(File(root, "a/b.txt").canonicalFile, LocalFolderTools.resolve(root, "a\\b.txt"))
        assertEquals(root, LocalFolderTools.resolve(root, ""))
        assertFailsWith<IllegalArgumentException> { LocalFolderTools.resolve(root, "../geheim.txt") }
        assertFailsWith<IllegalArgumentException> { LocalFolderTools.resolve(root, "a/../../x") }
        assertEquals("a/b.txt", LocalFolderTools.rel(root, File(root, "a/b.txt")))
    }

    @Test
    fun kindsOfFiles() {
        assertEquals(DocKind.SLIDES, DocKind.forFile("Vortrag.pptx"))
        assertEquals(DocKind.SHEET, DocKind.forFile("Noten.xlsx"))
        assertEquals(DocKind.DOC, DocKind.forFile("Brief.docx"))
        val csv = OfficeImport.read("liste.csv", "Name;Punkte\nAnna;3,5\n\"Ben; Jr.\";4".toByteArray())!!
        assertEquals(listOf(listOf("Name", "Punkte"), listOf("Anna", "3,5"), listOf("Ben; Jr.", "4")), SheetEngine.grid(csv.tabs.single().second))
    }
}
