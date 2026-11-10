package io.github.thepytales.llmhub

import io.github.thepytales.llmhub.agent.ActionPlan
import io.github.thepytales.llmhub.agent.ActionResult
import io.github.thepytales.llmhub.agent.DayJournal
import io.github.thepytales.llmhub.agent.DryRun
import kotlinx.coroutines.runBlocking
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Probelauf und „Alles rückgängig“ (AP-77). */
class DryRunTest {
    @Test
    fun recognizesTheQuestionAndKeepsTheWish() {
        assertEquals("alle PDFs nach Archiv/2025 verschieben", DryRun.strip("Was würde passieren, wenn ich alle PDFs nach Archiv/2025 verschieben würde?"))
        assertEquals("Ordner Hochgeladen aufräumen", DryRun.strip("Probelauf: Ordner Hochgeladen aufräumen"))
        assertEquals("Duplikate löschen", DryRun.strip("Was passiert, wenn du Duplikate löschen würdest?"))
        assertEquals("move all files to Archive", DryRun.strip("What would happen if I move all files to Archive?"))
        assertNull(DryRun.strip("Verschiebe alle PDFs nach Archiv"))
        assertNull(DryRun.strip("Was ist ein Probelauf?"))
    }

    @Test
    fun undoDayIsAskedInEverydayWords() {
        assertTrue(DryRun.asksUndoDay("Mach alles von heute rückgängig"))
        assertTrue(DryRun.asksUndoDay("alles rückgängig seit heute morgen"))
        assertTrue(DryRun.asksUndoDay("Was hast du heute gemacht?"))
        assertFalse(DryRun.asksUndoDay("Rückgängig"))
        assertFalse(DryRun.asksUndoDay("Mach den Ordner sauber"))
    }

    @Test
    fun thePreviewSaysWhatCannotBeTakenBackAndAdvises() {
        val many = ActionPlan("files.duplicates", "Legt 214 doppelte Dateien in den Papierkorb …", true, (1..214).map { "f$it.pdf" to "Papierkorb" }) { ActionResult("") }
        val notes = DryRun.notes(many)
        assertTrue(notes.any { "214" in it && "Papierkorb" in it && "30 Tage" in it }, notes.toString())
        assertTrue(notes.any { it == "Das betrifft 214 Dateien – erst an einem Ordner ausprobieren?" }, notes.toString())
        val inTool = ActionPlan("pdf.delete_pages", "Löscht Seite 3 …", true) { ActionResult("") }
        assertTrue(DryRun.notes(inTool).any { "Strg+Z" in it })
        val own = ActionPlan("x.y", "…", true, irreversible = "Wird sofort gesendet.") { ActionResult("") }
        assertTrue("Wird sofort gesendet." in DryRun.notes(own))
        assertNull(DryRun.advice(3))
    }

    @Test
    fun todaysActionsCanBeTakenBackTogether() = runBlocking {
        DayJournal.clearForTest()
        var restored = 0
        val now = LocalDateTime.of(2026, 11, 10, 15, 0)
        DayJournal.record("files.move", ActionResult("3 Dateien verschoben.", undo = { restored++; "zurück" }), now.minusHours(2))
        DayJournal.record("files.tag", ActionResult("Schlagwörter vergeben.", undo = { restored++; "weg" }), now.minusHours(1))
        DayJournal.record("files.move", ActionResult("Gestern verschoben.", undo = { restored++; "x" }), now.minusDays(1))
        val protocol = listOf("2026-11-10 08:05:00  files.tidy ausgeführt: 12 Dateien in 3 Ordner sortiert.", "2026-11-10 08:04:00  files.tidy geplant: …")
        val today = DayJournal.today(LocalTime.MIDNIGHT, now, protocol)
        assertEquals(listOf("Schlagwörter vergeben.", "3 Dateien verschoben.", "12 Dateien in 3 Ordner sortiert."), today.map { it.text })
        assertFalse(today.last().canUndo, "vor dem Neustart: nicht mehr zurücknehmbar")
        val (done, rest) = DayJournal.undoAll(today)
        assertEquals(2, done)
        assertEquals(2, restored)
        assertEquals(listOf("files.tidy"), rest.map { it.actionId })
        assertTrue(today.take(2).all { !it.canUndo })
    }
}
