package io.github.thepytales.llmhub

import android.content.Context
import io.github.thepytales.llmhub.data.LoraDrive
import io.github.thepytales.llmhub.desktop.ExplorerMenu
import io.github.thepytales.llmhub.desktop.FileListTransferable
import io.github.thepytales.llmhub.desktop.OfficeHandoff
import io.github.thepytales.llmhub.desktop.ShellArgs
import io.github.thepytales.llmhub.ui.ChatHandoff
import io.github.thepytales.llmhub.ui.ExternalOffice
import io.github.thepytales.llmhub.ui.PdfHandoff
import io.github.thepytales.llmhub.ui.handleShellRequest
import io.github.thepytales.llmhub.ui.shellTarget
import kotlinx.coroutines.runBlocking
import java.awt.datatransfer.DataFlavor
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Windows-Integration (AP-59): context menu, starts with a file, files out of the app, Windows-Papierkorb, Office and back. */
class WindowsIntegrationTest {
    private val base = Files.createTempDirectory("windows-integration").toFile()
    private val c by lazy {
        if (System.getProperty("adeu.data").isNullOrBlank()) System.setProperty("adeu.data", File(base, "daten").absolutePath)
        AppContainer(Context(File(base, "files").apply { mkdirs() }, File(base, "cache").apply { mkdirs() }))
    }

    @AfterTest
    fun cleanUp() {
        LoraDrive.systemTrash = null
        OfficeHandoff.clear()
        base.deleteRecursively()
    }

    @Test
    fun contextMenuEntriesPerUserWithoutAdminRights() {
        val exe = File("C:/Users/anna/AppData/Local/ADEU LLM/ADEU LLM.exe")
        val keys = ExplorerMenu.keys(exe)
        // Only HKEY_CURRENT_USER\Software\Classes: no admin rights needed.
        assertTrue(keys.all { it.path.startsWith("Software\\Classes\\") }, keys.toString())
        val open = keys.first { it.path.endsWith("*\\shell\\Lora.Open\\command") }.values.getValue("")
        assertEquals("\"${exe.absolutePath}\" --open \"%1\"", open)
        assertEquals("Lora fragen", keys.first { it.path.endsWith("*\\shell\\Lora.Ask") }.values.getValue(""))
        assertTrue(keys.any { it.path.endsWith("Directory\\shell\\Lora.Ask\\command") && "--ask-folder" in it.values.getValue("") })
    }

    @Test
    fun aStartWithAFileReachesTheRunningApp() {
        val r = assertNotNull(ShellArgs.parse(listOf("--ask", "C:\\Daten\\Bericht.docx")))
        assertEquals(ShellArgs.Kind.ASK, r.kind)
        assertEquals(r, ShellArgs.fromLine(ShellArgs.line(r)))
        assertEquals(ShellArgs.Kind.ASK_FOLDER, ShellArgs.parse(listOf("--minimized", "--ask-folder", "\"D:\\Projekt\""))?.kind)
        assertNull(ShellArgs.parse(listOf("--open")))
        assertNull(ShellArgs.fromLine("show"))
        // Where „Mit Lora öffnen“ takes a file.
        assertEquals(listOf("pdf", "doc", "doc", "video", "chat", "chat"), listOf("a.pdf", "b.docx", "c.xlsx", "d.mp4", "e.mp3", "f.zip").map { shellTarget(File(it)) })
    }

    @Test
    fun filesLeaveTheAppAsFiles() {
        val f = File(base, "Antrag.pdf").apply { writeText("x") }
        val t = FileListTransferable(listOf(f))
        assertTrue(t.isDataFlavorSupported(DataFlavor.javaFileListFlavor))
        assertEquals(listOf(f.absoluteFile), t.getTransferData(DataFlavor.javaFileListFlavor))
        assertEquals(f.absolutePath, t.getTransferData(DataFlavor.stringFlavor))
    }

    @Test
    fun deletedFilesGoIntoTheWindowsPapierkorbWhenWanted() {
        val drive = c.drive
        val f = File(drive.root, "Alt.txt").apply { writeText("weg") }
        val trashed = mutableListOf<File>()
        LoraDrive.systemTrash = { file -> trashed += file; file.delete() }
        // Off (the default): the Lora-Papierkorb as before.
        val inApp = drive.delete(listOf(f))
        assertEquals(1, inApp.size)
        assertTrue(trashed.isEmpty())
        drive.restore(inApp.single())
        // On: Windows takes it; nothing in the Lora-Papierkorb.
        c.settings.update { it.copy(systemTrash = true) }
        drive.systemTrashed = emptyList()
        val none = drive.delete(listOf(File(drive.root, "Alt.txt")))
        assertTrue(none.isEmpty())
        assertEquals(listOf("Alt.txt"), drive.systemTrashed)
        assertEquals(listOf("Alt.txt"), trashed.map { it.name })
        assertTrue(!File(drive.root, "Alt.txt").exists())
        assertTrue(drive.trash.current.none { it.name == "Alt.txt" && it.from.endsWith("Alt.txt") && File(it.from).exists() })
        c.settings.update { it.copy(systemTrash = false) }
    }

    @Test
    fun openInWordAndBack() {
        val doc = File(base, "Bericht.docx").apply { writeText("v1") }
        val saved = mutableListOf<File>()
        OfficeHandoff.onSaved = { saved += it }
        OfficeHandoff.watch(doc)
        // Shown as open at once (Word takes a moment to start).
        assertTrue(ExternalOffice.isOpen(doc))
        // Word holds its owner file; long names have „~$“ in place of the first two letters.
        val lock = File(base, "~\$richt.docx").apply { writeText("") }
        assertTrue(OfficeHandoff.isOpenInOffice(doc))
        OfficeHandoff.tick()
        assertTrue(ExternalOffice.isOpen(doc))
        // A save in Word comes back.
        doc.writeText("v2"); doc.setLastModified(System.currentTimeMillis() + 5000)
        OfficeHandoff.tick()
        assertEquals(listOf(doc), saved)
        // Word closed: no longer open.
        lock.delete()
        OfficeHandoff.tick()
        assertTrue(!ExternalOffice.isOpen(doc))
        assertEquals("Word", ExternalOffice.program(doc))
        assertEquals("PowerPoint", ExternalOffice.program(File("a.pptx")))
        OfficeHandoff.onSaved = null
    }

    /** Fertig, wenn: Eine Datei aus dem Explorer lässt sich per Kontextmenü in Lora fragen und per Ziehen in den Lora-Ordner legen. */
    @Test
    fun fileFromTheExplorerIsAskedAndDroppedIntoTheLoraOrdner(): Unit = runBlocking {
        val outside = File(base, "Explorer").apply { mkdirs() }
        val note = File(outside, "Protokoll.txt").apply { writeText("Sitzung am 3. November: Budget für 2027 beschlossen.") }
        // Rechtsklick › „Lora fragen“: a new chat with the file in the input field.
        ChatHandoff.pending.value = null
        handleShellRequest(c, assertNotNull(ShellArgs.parse(listOf("--ask", note.absolutePath))))
        val content = assertNotNull(ChatHandoff.pending.value)
        assertEquals("Protokoll.txt", content.files.single().name)
        assertTrue("Budget für 2027" in content.files.single().text)
        assertEquals("", ChatHandoff.open.value)
        ChatHandoff.pending.value = null; ChatHandoff.open.value = null
        // „Mit Lora öffnen“ on a PDF: the PDF-Werkzeugkasten.
        val pdf = File(outside, "Antrag.pdf").apply { writeBytes(org.apache.pdfbox.pdmodel.PDDocument().use { d -> d.addPage(org.apache.pdfbox.pdmodel.PDPage()); java.io.ByteArrayOutputStream().also { d.save(it) }.toByteArray() }) }
        handleShellRequest(c, ShellArgs.Request(ShellArgs.Kind.OPEN, pdf))
        assertEquals(pdf.absolutePath, PdfHandoff.request.value?.path)
        PdfHandoff.request.value = null
        // Dragged from the Explorer into the open folder of Dateien: a copy lands in the Lora-Ordner.
        val target = File(c.drive.root, "Sitzungen").apply { mkdirs() }
        val copied = c.drive.copy(listOf(note), target)
        assertEquals(listOf("Protokoll.txt"), copied.map { it.name })
        assertTrue(File(target, "Protokoll.txt").readText().startsWith("Sitzung"))
        assertTrue(note.exists(), "Das Original bleibt im Explorer")
    }
}
