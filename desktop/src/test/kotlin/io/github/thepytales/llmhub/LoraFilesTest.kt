package io.github.thepytales.llmhub

import android.content.Context
import io.github.thepytales.llmhub.data.ChatFilter
import io.github.thepytales.llmhub.data.ChatMessage
import io.github.thepytales.llmhub.data.Conversation
import io.github.thepytales.llmhub.data.FileAttachment
import io.github.thepytales.llmhub.data.LoraDrive
import io.github.thepytales.llmhub.docs.DocBlocks
import io.github.thepytales.llmhub.docs.DocComment
import io.github.thepytales.llmhub.docs.DocKind
import io.github.thepytales.llmhub.docs.DocMarkup
import io.github.thepytales.llmhub.docs.DocTab
import io.github.thepytales.llmhub.docs.DocxExtras
import io.github.thepytales.llmhub.docs.LatexOmml
import io.github.thepytales.llmhub.docs.LiveDoc
import io.github.thepytales.llmhub.docs.MarkdownDocx
import io.github.thepytales.llmhub.docs.OfficeImport
import io.github.thepytales.llmhub.docs.PptxWriter
import io.github.thepytales.llmhub.docs.SlideDeck
import io.github.thepytales.llmhub.docs.SlideElement
import io.github.thepytales.llmhub.docs.SlideMaster
import io.github.thepytales.llmhub.docs.Tracking
import io.github.thepytales.llmhub.files.OfficeEdit
import io.github.thepytales.llmhub.ui.DriveItem
import io.github.thepytales.llmhub.ui.sortItems
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipFile
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** AP-16: der Lora-Ordner (Dateien wie Google Drive), echte Office-Dateien, Änderungen, Kommentare, Formeln, freie Folien. */
class LoraFilesTest {
    private val base = Files.createTempDirectory("lora-ordner").toFile()
    private val c by lazy {
        if (System.getProperty("adeu.data").isNullOrBlank()) System.setProperty("adeu.data", File(base, "daten").absolutePath)
        AppContainer(Context(File(base, "files").apply { mkdirs() }, File(base, "cache").apply { mkdirs() }))
    }

    @AfterTest
    fun cleanUp() {
        base.deleteRecursively()
    }

    private fun zipText(f: File, entry: String): String = ZipFile(f).use { z -> z.getEntry(entry)?.let { z.getInputStream(it).readBytes().toString(Charsets.UTF_8) }.orEmpty() }
    private fun zipText(b: ByteArray, entry: String): String = File(base, "tmp-${System.nanoTime()}.zip").also { it.writeBytes(b) }.let { zipText(it, entry) }

    // ── Lora-Ordner ───────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun fixedFoldersUploadsOnceAndTheTresorStaysOut() {
        val drive = c.drive
        assertTrue(drive.uploads.isDirectory && drive.created.isDirectory, "Hochgeladen und Erstellt gibt es von Anfang an")
        assertTrue(drive.isFixed(drive.uploads) && drive.isFixed(drive.created))
        val bytes = "Hallo Welt".toByteArray()
        val staged = drive.stage("Notiz.txt", bytes)
        assertTrue(drive.isStaged(staged.path), "erst beiseitegelegt, bis die Nachricht gesendet ist")
        assertTrue(drive.list(drive.uploads).isEmpty())
        val kept = assertNotNull(drive.adopt(staged.path, "chat1"))
        assertEquals(File(drive.uploads, "Notiz.txt").absoluteFile, kept.absoluteFile)
        // The same file again is not kept twice.
        val again = assertNotNull(drive.adopt(drive.stage("Kopie.txt", bytes).path, "chat2"))
        assertEquals(kept.absoluteFile, again.absoluteFile)
        assertEquals(1, drive.list(drive.uploads).size)
        // The attachment remembers its copy.
        val att = io.github.thepytales.llmhub.data.UploadSink.stored("x.txt", bytes, FileAttachment("x.txt", "text/plain", "x"))
        assertNotNull(att)
    }

    @Test
    fun foldersMoveRenameTrashAndRestoreKeepTheChat() {
        val drive = c.drive
        val made = File(drive.created, "Bericht.pdf").apply { writeText("pdf") }
        val dir = drive.mkdir(drive.root, "Lehre")
        assertEquals("Lehre", dir.name)
        assertEquals("Lehre (2)", drive.mkdir(drive.root, "Lehre").name, "vorhandene Namen bekommen eine Nummer")
        val moved = drive.move(listOf(made), dir).single()
        assertEquals(File(dir, "Bericht.pdf").absoluteFile, moved.absoluteFile)
        // A chat still pointing at the old place finds the file.
        assertEquals(moved.absoluteFile, drive.resolve(made.absolutePath).absoluteFile)
        val renamed = drive.rename(moved, "Abschlussbericht")
        assertEquals("Abschlussbericht.pdf", renamed.name, "die Endung bleibt")
        assertEquals(renamed.absoluteFile, drive.resolve(made.absolutePath).absoluteFile)
        // Stars move along.
        drive.setStarred(renamed, true)
        val final = drive.move(listOf(renamed), drive.root).single()
        assertTrue(drive.starred(final))
        // The fixed folders cannot be renamed, moved or deleted.
        assertTrue(runCatching { drive.rename(drive.uploads, "X") }.isFailure)
        assertTrue(drive.move(listOf(drive.created), dir).isEmpty())
        assertTrue(drive.delete(listOf(drive.uploads)).isEmpty())
        // Papierkorb.
        val entry = drive.delete(listOf(final)).single()
        assertFalse(final.exists())
        assertEquals(1, drive.trash.current.size)
        val back = assertNotNull(drive.restore(entry))
        assertTrue(back.isFile)
        assertTrue(drive.trash.current.isEmpty())
        // Old entries go after 30 days.
        drive.delete(listOf(back))
        drive.purgeTrash(System.currentTimeMillis() + 31L * 24 * 60 * 60 * 1000)
        assertTrue(drive.trash.current.isEmpty())
        // Not into itself.
        assertTrue(drive.move(listOf(dir), File(dir, "Unterordner").apply { mkdirs() }).isEmpty())
    }

    @Test
    fun foldersComeFirstAndTheFixedOnesOnTop() {
        val drive = c.drive
        val a = File(drive.root, "a.txt").apply { writeText("1") }
        val z = drive.mkdir(drive.root, "Zebra")
        val items = drive.list(drive.root).map { f -> DriveItem(f, f.isDirectory, fixed = drive.isFixed(f)) }
        val sorted = sortItems(items, "name", false)
        assertEquals(listOf(LoraDrive.CREATED, LoraDrive.UPLOADS, z.name, a.name), sorted.map { it.name })
    }

    @Test
    fun editorDocumentsAreRealFilesThatFollowTheTitle() {
        val doc = LiveDoc.create("Seminarplan", "# Seminarplan\n\nSitzung 1: Einführung.", "Sie", kind = DocKind.DOC)
        c.docs.save(doc)
        val saved = assertNotNull(c.docs.get(doc.id))
        assertEquals("Seminarplan.docx", saved.path)
        val file = assertNotNull(c.docs.realFile(saved))
        c.docs.files!!.write(saved)
        assertTrue(file.isFile)
        assertTrue("Einführung" in OfficeImport.docx(file.readBytes()))
        // Made in a chat: into „Erstellt“.
        val fromChat = LiveDoc.create("Protokoll", "# Protokoll\n\nText", "KI", chatId = "c1", kind = DocKind.SHEET)
        c.docs.save(fromChat)
        assertEquals("Erstellt/Protokoll.xlsx", c.docs.get(fromChat.id)!!.path)
        // A new title renames the file, like in Google Drive.
        c.docs.save(saved.copy(title = "Seminarplan WiSe"))
        assertEquals("Seminarplan WiSe.docx", c.docs.get(doc.id)!!.path)
        // Moved in the Dateien tab: the document follows and takes the new name.
        val now = c.docs.realFile(c.docs.get(doc.id)!!)!!
        c.docs.files!!.write(c.docs.get(doc.id)!!)
        val moved = c.drive.move(listOf(now), c.drive.mkdir(c.drive.root, "Lehre")).single()
        assertEquals("Lehre/Seminarplan WiSe.docx", c.docs.get(doc.id)!!.path)
        c.drive.rename(moved, "Plan")
        assertEquals("Plan", c.docs.get(doc.id)!!.title)
        // In den Papierkorb und zurück – mit dem Dokument.
        c.docs.delete(doc.id)
        assertNull(c.docs.get(doc.id))
        c.drive.restore(c.drive.trash.current.single())
        assertNotNull(c.docs.get(doc.id), "das Dokument kommt mit seiner Datei zurück")
    }

    @Test
    fun changesInTheEditorGoIntoTheOriginalFormattingStays() {
        val file = File(c.drive.root, "Antrag.docx")
        MarkdownDocx.write(file, "Antrag", "# Antrag\n\nDie Förderung beträgt 1000 Euro.\n\n- erster Punkt\n- zweiter Punkt")
        // Something the editor does not know (a style of Word) must survive.
        val marked = file.readBytes().let { b -> OfficeEdit.replace("docx", b, "erster Punkt", "erster Punkt").bytes }
        file.writeBytes(marked)
        val imported = assertNotNull(OfficeImport.read(file.name, file.readBytes()))
        val doc = c.docs.openReal(file, imported)
        val tab = doc.tabs.first()
        val changed = doc.copy(tabs = listOf(tab.copy(markdown = tab.markdown.replace("1000 Euro", "2500 Euro"))))
        assertTrue(c.docs.files!!.write(changed), "in das Original geschrieben")
        assertTrue("2500 Euro" in OfficeImport.docx(file.readBytes()))
        // A structural change is merged into the original too (Word-Treue, AP-45): the unchanged paragraphs stay as they were.
        val before = file.readBytes()
        val now = changed.tabs.first()
        val restructured = changed.copy(tabs = listOf(now.copy(markdown = now.markdown + "\n\n## Neuer Abschnitt\n\nText")))
        assertTrue(c.docs.files!!.write(restructured), "ins Original zusammengeführt")
        assertTrue("Neuer Abschnitt" in OfficeImport.docx(file.readBytes()))
        val oldXml = java.util.zip.ZipInputStream(before.inputStream()).use { z -> generateSequence { z.nextEntry }.first { it.name == "word/document.xml" }; z.readBytes().toString(Charsets.UTF_8) }
        val newXml = java.util.zip.ZipInputStream(file.readBytes().inputStream()).use { z -> generateSequence { z.nextEntry }.first { it.name == "word/document.xml" }; z.readBytes().toString(Charsets.UTF_8) }
        val para = Regex("<w:p>(?:(?!</w:p>).)*2500 Euro.*?</w:p>").find(oldXml)!!.value
        assertTrue(para in newXml, "Absatz unverändert übernommen")
    }

    @Test
    fun textChangesAreFoundBlockByBlock() {
        val old = "# T\n\nErster Satz.\n\n| A | B |\n| --- | --- |\n| 1 | 2 |"
        val new = "# T\n\nErster Satz geändert.\n\n| A | B |\n| --- | --- |\n| 1 | 3 |"
        val pairs = assertNotNull(io.github.thepytales.llmhub.docs.DocFiles.textChanges(old, new))
        assertEquals(listOf("Erster Satz." to "Erster Satz geändert.", "2" to "3"), pairs)
        assertNull(io.github.thepytales.llmhub.docs.DocFiles.textChanges(old, "$old\n\nNeu"), "neuer Block: Struktur geändert")
        assertNull(io.github.thepytales.llmhub.docs.DocFiles.textChanges("Text", "**Text**"), "Formatierung geändert")
    }

    // ── Stufe 1: Änderungen nachverfolgen und Kommentare ──────────────────────────────────────────────────

    @Test
    fun trackedChangesAreAcceptedOneByOne() {
        val base = "# T\n\nEins.\n\nZwei.\n\nDrei."
        val now = "# T\n\nEins geändert.\n\nDrei.\n\nVier."
        val r = Tracking.review(base, now)
        assertEquals(2, r.changes.size)
        assertEquals(1, r.removed.size)
        val first = r.changes.first { it.before == "Eins." }
        val next = Tracking.baseAfter(base, now, accepted = first)
        val left = Tracking.review(next, now)
        assertEquals(1, left.changes.size, "nur die angenommene Änderung ist weg")
        assertEquals(1, left.removed.size)
        val dropped = Tracking.baseAfter(next, now, dropped = left.removed.single())
        assertEquals(0, Tracking.review(dropped, now).removed.size)
    }

    @Test
    fun wordGetsRevisionsAndCommentsAndBringsThemBack() {
        val tab = DocTab(markdown = "# Bericht\n\nDer Antrag wurde bewilligt.")
        val doc = LiveDoc.create("Bericht", tab.markdown, "Sie").let { d ->
            val t = d.tabs.first()
            d.copy(
                trackBase = mapOf(t.id to "# Bericht\n\nDer Antrag wurde gestellt."),
                comments = listOf(DocComment(tabId = t.id, quote = "Antrag", text = "Bitte Datum ergänzen", author = "Anna Beispiel")),
            )
        }
        val f = File(base, "bericht.docx")
        val ids = DocxExtras.write(f, doc, io.github.thepytales.llmhub.docs.DocTheme(), null)
        assertEquals(1, ids.size)
        val xml = zipText(f, "word/document.xml")
        assertTrue("<w:ins " in xml && "<w:del " in xml, "Änderungen als Überarbeitungen")
        assertTrue("commentReference" in xml)
        assertTrue("Bitte Datum ergänzen" in zipText(f, "word/comments.xml"))
        assertTrue("comments.xml" in zipText(f, "word/_rels/document.xml.rels"))
        // Reading it back: comment and suggestion.
        val read = DocxExtras.readComments(f.readBytes()).single()
        assertEquals("Bitte Datum ergänzen", read.text)
        assertEquals("Anna Beispiel", read.author)
        val plain = OfficeImport.toLiveDoc(OfficeImport.read(f.name, f.readBytes())!!)
        val adopted = DocxExtras.adopt(plain, f.readBytes())
        assertEquals(1, adopted.comments.size)
        val baseText = assertNotNull(adopted.trackBase?.values?.single())
        assertTrue("gestellt" in baseText, baseText)
        assertTrue("bewilligt" in adopted.tabs.first().markdown)
    }

    // ── Stufe 2: Seite, Inhaltsverzeichnis, Formeln ───────────────────────────────────────────────────────

    @Test
    fun pageSetupHasFooterAndMargins() {
        val setup = DocMarkup.PageSetup(footer = "Entwurf", margins = "schmal")
        val md = DocMarkup.withPageSetup("# T\n\nText", setup)
        assertEquals(setup, DocMarkup.pageSetup(md))
        val f = File(base, "seite.docx")
        MarkdownDocx.write(f, "T", md)
        assertTrue("Entwurf" in zipText(f, "word/footer1.xml"))
        assertTrue("w:top=\"720\"" in zipText(f, "word/document.xml"))
    }

    @Test
    fun tableOfContentsListsTheHeadings() {
        val md = "# Titel\n\n${DocMarkup.TOC}\n\n## Einleitung\n\nText\n\n### Fragestellung\n\n## Methode"
        assertEquals(listOf(2 to "Einleitung", 3 to "Fragestellung", 2 to "Methode"), DocMarkup.tocEntries(md))
        val f = File(base, "toc.docx")
        MarkdownDocx.write(f, "Titel", md)
        val xml = zipText(f, "word/document.xml")
        assertTrue("TOC \\o" in xml && "Fragestellung" in xml)
        assertTrue(DocBlocks.split(OfficeImport.docx(f.readBytes())).contains(DocMarkup.TOC), "kommt als Markierung zurück")
    }

    @Test
    fun formulasBecomeWordFormulasAndComeBack() {
        val omml = LatexOmml.convert("\\frac{a}{b} + x^{2} - \\sqrt{n}")
        assertTrue("<m:f>" in omml && "<m:sSup>" in omml && "<m:rad>" in omml, omml)
        assertEquals(listOf("Preis " to false, "x^2" to true, " und 5 $ Rest" to false), LatexOmml.split("Preis \$x^2\$ und 5 $ Rest"))
        val f = File(base, "formel.docx")
        MarkdownDocx.write(f, "F", "# F\n\nMittelwert \$\\bar{x} = \\frac{1}{n}\\sum_{i=1}^{n} x_i\$.\n\n$$\\sqrt{a^2+b^2}$$")
        val xml = zipText(f, "word/document.xml")
        assertTrue("<m:oMath>" in xml && "<m:oMathPara>" in xml)
        val back = OfficeImport.docx(f.readBytes())
        assertTrue("\\frac{1}{n}" in back && "\\sqrt{" in back, back)
    }

    // ── Stufe 3: Folien frei gestalten ────────────────────────────────────────────────────────────────────

    @Test
    fun freeElementsAndTheMasterGoIntoPowerPoint() {
        val slides = listOf(
            SlideDeck.Slide("Titel", "Untertitel", layout = SlideDeck.Layout.TITLE, master = SlideMaster(background = "#F5F7FA", accent = "#1B7F3B", footer = "Seminar")),
            SlideDeck.Slide(
                "", "", layout = SlideDeck.Layout.FREE,
                elements = listOf(
                    SlideElement(kind = "text", x = 2.0, y = 3.0, w = 10.0, h = 2.0, text = "Frei --> platziert", size = 28, bold = true),
                    SlideElement(kind = "form", shape = "ellipse", x = 15.0, y = 5.0, w = 6.0, h = 6.0, text = "Kreis"),
                ),
            ),
        )
        val md = SlideDeck.markdown(slides)
        val parsed = SlideDeck.parse(md)
        assertEquals(2, parsed[1].elements.size)
        assertEquals("Frei --> platziert", parsed[1].elements[0].text, "auch mit „-->“ im Text")
        assertEquals("#1B7F3B", parsed[0].master?.accent)
        assertEquals(SlideDeck.Layout.FREE, parsed[1].layout)
        assertTrue(parsed[1].body.isBlank(), "die Elementzeilen sind kein Folientext")
        val f = File(base, "frei.pptx")
        PptxWriter.write(f, "Titel", md, "Professur")
        val slide2 = zipText(f, "ppt/slides/slide2.xml")
        assertTrue("Frei --&gt; platziert" in slide2 && "prst=\"ellipse\"" in slide2, slide2.takeLast(800))
        assertTrue("F5F7FA" in slide2, "Hintergrund der Masterfolie")
        assertTrue("1B7F3B" in slide2, "Akzentfarbe für Formen")
        assertTrue("element:" !in slide2, "keine Markierungszeilen auf der Folie")
    }

    // ── Umzug der Daten ───────────────────────────────────────────────────────────────────────────────────

    @Test
    fun dataOfEarlierVersionsMovesIntoDotLora() {
        // A chosen data folder of before AP-16: the app's data lay directly in it, next to other files of the user.
        val root = File(base, "Cloud/Lora").apply { mkdirs() }
        File(root, "einstellungen.json").writeText("{}")
        File(root, "conversations").mkdirs()
        File(root, "conversations/a.json").writeText("{}")
        File(root, "Urlaubsfotos").mkdirs()
        File(root, "Lebenslauf.docx").writeText("x")
        val data = File(root, io.github.thepytales.llmhub.desktop.LoraLayout.DATA)
        io.github.thepytales.llmhub.desktop.LoraLayout.moveLegacy(root, data)
        assertTrue(File(data, "einstellungen.json").isFile && File(data, "conversations/a.json").isFile)
        assertFalse(File(root, "einstellungen.json").exists())
        assertTrue(File(root, "Urlaubsfotos").isDirectory && File(root, "Lebenslauf.docx").isFile, "Dateien des Nutzers bleiben, wo sie sind")
        // %APPDATA%: everything moves except the files of this PC.
        val standard = File(base, "AppData/ADEU LLM").apply { mkdirs() }
        File(standard, "einstellungen.json").writeText("{}")
        File(standard, "secure_settings.dat").writeText("geheim")
        File(standard, "livedocs").mkdirs()
        val target = File(base, "Dokumente/Lora/.lora")
        io.github.thepytales.llmhub.desktop.LoraLayout.moveStandard(standard, target, setOf("secure_settings.dat"))
        assertTrue(File(target, "einstellungen.json").isFile && File(target, "livedocs").isDirectory)
        assertTrue(File(standard, "secure_settings.dat").isFile, "Schlüssel bleiben am PC")
        assertFalse(File(target, "secure_settings.dat").exists())
    }

    // ── Chatsuche ─────────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun chatSearchFilters() {
        val now = System.currentTimeMillis()
        val day = 24L * 60 * 60 * 1000
        val a = Conversation(title = "Antrag DFG", model = "deepseek", folderId = "p1", updatedAt = now - day,
            messages = listOf(ChatMessage(role = "user", content = "Hilf beim Antrag", files = listOf(FileAttachment("Ausschreibung.pdf", "application/pdf", "x")))))
        val b = Conversation(title = "Mail an Prüfungsamt", model = "glm", updatedAt = now - 40 * day,
            messages = listOf(ChatMessage(role = "tool", content = "ok", file = "/tmp/Mail.eml")))
        val v = Conversation(title = "Tresor", model = "glm", vault = true, updatedAt = now)
        val all = listOf(a, b, v)
        assertTrue(ChatFilter.search(all, "", ChatFilter()).isEmpty(), "ohne Suchwort und Filter nichts")
        assertEquals(listOf(a), ChatFilter.search(all, "", ChatFilter(days = 7)).map { it.first })
        assertEquals(listOf(a), ChatFilter.search(all, "", ChatFilter(project = "p1")).map { it.first })
        assertEquals(listOf(b), ChatFilter.search(all, "", ChatFilter(project = "")).map { it.first })
        assertEquals(listOf(a), ChatFilter.search(all, "", ChatFilter(withAttachments = true)).map { it.first })
        assertEquals(listOf(b), ChatFilter.search(all, "", ChatFilter(withFiles = true)).map { it.first })
        assertEquals(listOf(b), ChatFilter.search(all, "", ChatFilter(model = "glm")).map { it.first }, "Tresor-Chats nie")
        assertEquals(listOf(a), ChatFilter.search(all, "Ausschreibung", ChatFilter()).map { it.first }, "auch Namen von Anhängen")
    }
}
