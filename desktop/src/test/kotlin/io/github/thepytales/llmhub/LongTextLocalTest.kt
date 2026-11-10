package io.github.thepytales.llmhub

import io.github.thepytales.llmhub.data.ChatMessage
import io.github.thepytales.llmhub.data.FileAttachment
import io.github.thepytales.llmhub.files.AttachmentBudget
import io.github.thepytales.llmhub.local.Hardware
import io.github.thepytales.llmhub.local.LocalCatalog
import io.github.thepytales.llmhub.local.LongText
import io.github.thepytales.llmhub.local.SpeedForecast
import io.github.thepytales.llmhub.ui.AttachmentDigest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Lange Texte lokal (AP-108): context length after the memory, what goes along by itself, the honest time before the
 * start, and the pieces of the section summary with their pages. An invented 80-page document (no real texts).
 * Whether the times are right within 30 % is measured on the device (Office-PC with 32 GB).
 */
class LongTextLocalTest {
    private val office32 = Hardware(phone = false, ramGb = 32.0, cores = 8, memBandwidthGbs = 51.2, cpuFeatures = setOf("avx2"))

    /** 80 pages of about 3000 characters, with page marks as the PDF reader writes them. */
    private val book: String = (1..80).joinToString("\n\n") { p ->
        "[Seite $p]\n" + "Abschnitt über Leseförderung an Grundschulen, Teil $p. ".repeat(50) + if (p == 57) "Die Prüfungsfrist für die Hausarbeit endet am 15. März." else ""
    }

    @AfterTest
    fun reset() { LocalCatalog.hardware = null; LocalCatalog.forecaster = null }

    @Test
    fun theContextFollowsTheMemory() {
        val m4 = LocalCatalog.get("qwen3.5-4b")!!
        assertEquals(8192, m4.context)
        val c32 = LocalCatalog.contextFor(m4, office32)
        assertEquals(m4.maxContext, c32, "32 GB: das ganze 80-Seiten-Dokument passt")
        assertTrue(LocalCatalog.contextFor(m4, office32.copy(ramGb = 8.0)) < c32)
        // Never below the catalog value, never above the model's limit, in steps of 4096.
        val tight = LocalCatalog.contextFor(LocalCatalog.get("qwen3.5-9b")!!, office32.copy(ramGb = 14.0))
        assertTrue(tight >= 16384 && tight % 4096 == 0, tight.toString())
        assertEquals(32_768, LocalCatalog.contextFor(LocalCatalog.get("qwen2.5-coder-7b")!!, office32), "Qwen 2.5 Coder: höchstens 32 k")
    }

    @Test
    fun localModelsReadAboutAMinuteAndAHalfByThemselvesAndTheWholeTextOnRequest() {
        LocalCatalog.hardware = office32
        val model = "local/qwen3.5-4b"
        val f = SpeedForecast.forecast(LocalCatalog.get(model)!!, office32)
        val quick = AttachmentBudget.budgetChars(model)
        assertEquals((f.readTokS * LocalCatalog.QUICK_READ_S * LongText.CHARS_PER_TOKEN).toInt(), quick)
        assertTrue(book.length > quick && book.length <= AttachmentBudget.wholeChars(model), "${book.length} / $quick / ${AttachmentBudget.wholeChars(model)}")
        val pdf = FileAttachment(name = "Bericht.pdf", mime = "application/pdf", text = book, pages = 80)
        fun ask(file: FileAttachment) = AttachmentBudget.prepare(listOf(ChatMessage(role = "user", content = "Wann endet die Prüfungsfrist der Hausarbeit?", files = listOf(file))), model)
            .single().files.single().text
        val passages = ask(pdf)
        assertTrue("15. März" in passages && "[Seite 57]" in passages, "Belegstelle mit Seite")
        assertTrue(passages.length <= quick + 300)
        // „Ganz lesen“ chosen: the whole text goes along, it fits the context of this device.
        assertEquals(book, ask(pdf.copy(send = AttachmentBudget.Mode.FULL.id)))
    }

    @Test
    fun theTimeIsSaidBeforeTheStart() {
        val m4 = LocalCatalog.get("qwen3.5-4b")!!
        val f = SpeedForecast.forecast(m4, office32)
        val whole = LongText.seconds(LongText.whole(book.length), f)
        val passages = LongText.seconds(LongText.passages(book.length, (f.readTokS * 90 * 3.5).toInt()), f)
        val sections = LongText.seconds(LongText.sections(book.length, 12_000, 131_072), f)
        assertTrue(passages < 150, "Stellen: $passages s")
        assertTrue(whole > passages * 5, "ganz: $whole s")
        assertTrue(sections > passages, "abschnittsweise: $sections s")
        assertEquals(LongText.duration(90.0), "≈ 2 Min.")
        assertEquals(LongText.duration(42.0), "≈ 45 s")
        assertEquals(LongText.duration(4000.0), "≈ 1 Std. 7 Min.")
        // A small context needs a joining step in groups.
        val few = LongText.sections(book.length, 12_000, 131_072)
        val tight = LongText.sections(book.length, 12_000, 4_096)
        assertTrue(tight.requests > few.requests, "$tight / $few")
        // Progress: from the pace of the done sections.
        val p = LongText.Progress(done = 2, total = 10, startedAt = 0, estimateS = 999.0)
        assertEquals(320.0, p.remainingS(now = 80_000), 1e-6)
        assertTrue(p.text().startsWith("Abschnitt 3 von 10 · noch"))
    }

    @Test
    fun everyPieceStartsWithItsPage() {
        val pieces = AttachmentDigest.pieces(book, 12_000)
        assertTrue(pieces.size >= 15, pieces.size.toString())
        assertTrue(pieces.all { it.trimStart().startsWith("[Seite ") }, pieces.map { it.take(12) }.toString())
        // Nothing is lost: each piece ends with its part of the text.
        book.chunked(12_000).forEachIndexed { i, part -> assertTrue(pieces[i].endsWith(part)) }
    }
}
