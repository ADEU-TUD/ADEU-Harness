package io.github.thepytales.llmhub

import io.github.thepytales.llmhub.agent.ActionArea
import io.github.thepytales.llmhub.agent.ToolActions
import io.github.thepytales.llmhub.desktop.MacClickFix
import java.awt.Canvas
import java.awt.event.InputEvent
import java.awt.event.MouseEvent
import java.io.File
import java.nio.file.Files
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Lora handelt in Werkzeugen (AP-38) and the click fix for macOS. */
class LoraActionsTest {
    @Test
    fun areasAndTheirActions() {
        assertEquals(ActionArea.FILES, ActionArea.of("files"))
        assertEquals(ActionArea.PDF, ActionArea.of("pdf"))
        assertEquals(ActionArea.DOC, ActionArea.of("doc/abc"))
        assertEquals(null, ActionArea.of("settings"))
        val files = ToolActions.forArea(ActionArea.FILES, android = false).map { it.id }
        assertTrue("files.tidy" in files && "files.duplicates" in files && "pdf.open" in files && "pdf.merge" in files, files.toString())
        assertTrue("pdf.delete_pages" !in files, "Seiten löschen nur im Werkzeugkasten")
        val pdf = ToolActions.forArea(ActionArea.PDF, android = true).map { it.id }
        assertTrue("pdf.compress" !in pdf && "pdf.redact" !in pdf, "Nur Windows: $pdf")
        // Every action is a tool the model can call.
        ToolActions.all.forEach { a -> assertTrue(a.toolName.matches(Regex("^act_[a-z_]+$")), a.toolName); assertEquals(a, ToolActions.byToolName(a.toolName)) }
    }

    @Test
    fun semestersAndTidyFolders() {
        assertEquals("WiSe 2026-27", ToolActions.semester(LocalDate.of(2026, 11, 3)))
        assertEquals("WiSe 2025-26", ToolActions.semester(LocalDate.of(2026, 2, 1)))
        assertEquals("SoSe 2026", ToolActions.semester(LocalDate.of(2026, 5, 1)))
        val dir = Files.createTempDirectory("tidy").toFile()
        val pdf = File(dir, "a.pdf").apply { writeText("x") }
        val pic = File(dir, "b.JPG").apply { writeText("y") }
        assertEquals("PDF", ToolActions.tidyFolder(pdf, "typ"))
        assertEquals("Bilder", ToolActions.tidyFolder(pic, "typ"))
        assertTrue(ToolActions.tidyFolder(pdf, "monat").matches(Regex("\\d{4}-\\d{2}")))
        dir.deleteRecursively()
    }

    @Test
    fun duplicatesHaveTheSameContent() {
        val dir = Files.createTempDirectory("dup").toFile()
        val a = File(dir, "a.txt").apply { writeText("gleich") }
        val b = File(dir, "b.txt").apply { writeText("gleich") }
        val c = File(dir, "c.txt").apply { writeText("anders") }
        val d = File(dir, "d.txt").apply { writeText("gleicH") }
        val groups = ToolActions.duplicates(listOf(a, b, c, d))
        assertEquals(1, groups.size)
        assertEquals(setOf(a, b), groups[0].toSet())
        dir.deleteRecursively()
    }

    @Test
    fun macClicksGetTheirButtonState() {
        val src = Canvas()
        // A press without the button in the modifiers (tap to click) gets it …
        val press = MouseEvent(src, MouseEvent.MOUSE_PRESSED, 0L, 0, 5, 5, 1, false, MouseEvent.BUTTON1)
        val fixed = MacClickFix.normalize(press) as MouseEvent
        assertTrue(fixed.modifiersEx and InputEvent.BUTTON1_DOWN_MASK != 0)
        // … a release that still carries it loses it …
        val release = MouseEvent(src, MouseEvent.MOUSE_RELEASED, 0L, InputEvent.BUTTON1_DOWN_MASK, 5, 5, 1, false, MouseEvent.BUTTON1)
        assertEquals(0, (MacClickFix.normalize(release) as MouseEvent).modifiersEx and InputEvent.BUTTON1_DOWN_MASK)
        // … and correct events pass unchanged.
        val ok = MouseEvent(src, MouseEvent.MOUSE_PRESSED, 0L, InputEvent.BUTTON1_DOWN_MASK, 5, 5, 1, false, MouseEvent.BUTTON1)
        assertSame(ok, MacClickFix.normalize(ok))
        val move = MouseEvent(src, MouseEvent.MOUSE_MOVED, 0L, 0, 5, 5, 0, false, MouseEvent.NOBUTTON)
        assertSame(move, MacClickFix.normalize(move))
    }
}
