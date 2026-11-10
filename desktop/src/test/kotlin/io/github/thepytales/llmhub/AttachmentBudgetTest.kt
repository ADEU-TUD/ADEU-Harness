package io.github.thepytales.llmhub

import io.github.thepytales.llmhub.data.ChatMessage
import io.github.thepytales.llmhub.data.FileAttachment
import io.github.thepytales.llmhub.data.ModelCatalog
import io.github.thepytales.llmhub.files.AttachmentBudget
import io.github.thepytales.llmhub.files.AttachmentReader
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Große Anhänge vollständig nutzen (AP-85). */
class AttachmentBudgetTest {
    /** 300 pages of about 3 000 characters, with page marks; page 217 holds the answer. */
    private val book: String = (1..300).joinToString("\n\n") { p ->
        "[Seite $p]\n" + (if (p == 217) "Die Prüfungsfrist für die Hausarbeit endet am 15. März; danach gilt ein Nachtermin. " else "") +
            (1..40).joinToString(" ") { "Abschnitt $p Satz $it über Didaktik und Unterricht in der Grundschule mit Beispielen." }
    }
    private val pdf = FileAttachment("Handbuch.pdf", "application/pdf", book, pages = 300)

    private fun request(file: FileAttachment, question: String) = listOf(
        ChatMessage(role = "system", content = "System"),
        ChatMessage(role = "user", content = question, files = listOf(file)),
    )

    @Test
    fun aThreeHundredPagePdfArrivesWholeWithALargeModel() {
        assertTrue(book.length > 900_000, "über 900 000 Zeichen – früher nach 120 000 abgeschnitten")
        val out = AttachmentBudget.prepare(request(pdf, "Wann endet die Frist?"), ModelCatalog.FAST_CHAT)
        assertEquals(book, out[1].files.single().text)
        assertTrue(AttachmentBudget.needsChoice(listOf(pdf), ModelCatalog.FAST_CHAT).isEmpty())
        assertEquals("geht ganz mit", AttachmentBudget.shown(listOf(pdf), ModelCatalog.FAST_CHAT).single().text)
    }

    @Test
    fun smallerModelsAskAndNeverCutSilently() {
        val small = "openai/gpt-oss-120b"
        assertTrue(AttachmentBudget.budgetChars(small) < book.length)
        assertEquals(listOf(pdf), AttachmentBudget.needsChoice(listOf(pdf), small), "die App fragt")
        assertEquals(ModelCatalog.FAST_CHAT, AttachmentBudget.suggestModel(book.length), "Vorschlag: GLM-5.3 Flash")
        // Not chosen yet, or „passende Stellen“: the passages for the question, marked as such, with the page.
        val sent = AttachmentBudget.prepare(request(pdf, "Wann endet die Prüfungsfrist für die Hausarbeit?"), small)[1].files.single().text
        assertTrue(sent.startsWith("[Nur die zur Frage passenden Stellen"), sent.take(200))
        assertTrue("Die Prüfungsfrist für die Hausarbeit endet am 15. März" in sent)
        assertTrue("[Seite 217]" in sent)
        assertTrue(sent.length <= AttachmentBudget.budgetChars(small) + 200)
        assertTrue("[…]" in sent)
        // „Ganz“ stays whole (the user switched to a model that takes it).
        val whole = pdf.copy(send = AttachmentBudget.Mode.FULL.id)
        assertEquals(book, AttachmentBudget.prepare(request(whole, "Frage"), small)[1].files.single().text)
        // The summary goes instead of the text once it is there.
        val digest = pdf.copy(send = AttachmentBudget.Mode.SUMMARY.id, digest = "## Abschnitt 1 von 3\n\nKurz gefasst.")
        val s = AttachmentBudget.prepare(request(digest, "Worum geht es?"), small)[1].files.single().text
        assertTrue(s.startsWith("[Abschnittsweise Zusammenfassung") && "Kurz gefasst." in s, s)
        assertTrue(AttachmentBudget.shown(listOf(pdf), small).single().text.startsWith("nur passende Stellen"))
    }

    @Test
    fun localModelsGetThePassagesByThemselves() {
        val local = "local/qwen3.5-2b"
        assertTrue(AttachmentBudget.needsChoice(listOf(pdf), local).isEmpty(), "kein Dialog – kleine lokale Modelle nehmen die Stellen")
        val sent = AttachmentBudget.prepare(request(pdf, "Prüfungsfrist Hausarbeit"), local)[1].files.single().text
        assertTrue("15. März" in sent && sent.length < 40_000, sent.length.toString())
        // Attachments do not count against the history (no summary of the chat because of a big file).
        assertEquals(book.length / 3.5, AttachmentBudget.tokens(request(pdf, "x")).toDouble(), 2.0)
    }

    @Test
    fun theReaderNoLongerCutsAt120000Characters() = runBlocking {
        val dir = Files.createTempDirectory("big").toFile()
        try {
            val f = File(dir, "lang.txt").apply { writeText(book) }
            val r = AttachmentReader.read(f)
            assertEquals(book.trim().length, r.file!!.text.length)
            assertFalse(r.file!!.truncated)
            val src = File("../app/src/main/java/io/github/thepytales/llmhub/files/AttachmentReader.kt").readText()
            assertFalse("nur die ersten" in src)
        } finally {
            dir.deleteRecursively()
        }
    }
}
