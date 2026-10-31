package io.github.thepytales.llmhub

import android.content.Context
import io.github.thepytales.llmhub.agent.ChatModes
import io.github.thepytales.llmhub.agent.CitationStatus
import io.github.thepytales.llmhub.agent.Evidence
import io.github.thepytales.llmhub.agent.EvidenceSource
import io.github.thepytales.llmhub.agent.ExcerptTools
import io.github.thepytales.llmhub.agent.RepoContext
import io.github.thepytales.llmhub.data.ChatMessage
import io.github.thepytales.llmhub.data.FileAttachment
import io.github.thepytales.llmhub.data.ToolCall
import io.github.thepytales.llmhub.research.ExcerptSchema
import io.github.thepytales.llmhub.research.Excerpts
import io.github.thepytales.llmhub.research.Reference
import io.github.thepytales.llmhub.ui.excerptRequest
import io.github.thepytales.llmhub.ui.excerptTable
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Belegpflicht und Quellenmodus (AP-08) and the Exzerpt-Agent (AP-09). */
class EvidenceAndExcerptTest {
    private val pdfText = "[Seite 1]\nFeedback im Unterricht\nEinleitung zum Thema.\n\n" +
        "[Seite 2]\nDie Studie zeigt, dass formatives Feedback die Leistung deutlich verbessert, besonders bei schwächeren Lernenden.\n\n" +
        "[Seite 3]\nAls Methode wurde eine Längsschnittstudie mit 412 Schülerinnen und Schülern der Klassen 3 und 4 durch-\ngeführt."
    private val source = EvidenceSource("Feedback.pdf", pdfText, EvidenceSource.Kind.FILE)

    private val answer = """
        Formatives Feedback verbessert die Leistung deutlich, vor allem bei schwächeren Lernenden [1].
        Die Untersuchung war eine Längsschnittstudie mit 412 Kindern [2].
        Außerdem wirkt Feedback in allen Fächern gleich stark und hält jahrelang an.

        ## Belege
        [1] Feedback.pdf, S. 2 – „formatives Feedback die Leistung deutlich verbessert, besonders bei schwächeren Lernenden“
        [2] Feedback.pdf, S. 2 – „Längsschnittstudie mit 412 Schülerinnen und Schülern der Klassen 3 und 4 durchgeführt“
    """.trimIndent()

    @Test
    fun sourcesModeIsAMode() {
        assertEquals(Evidence.PROMPT, ChatModes.SOURCES.prompt)
        assertTrue(ChatModes.SOURCES in ChatModes.matching("quellen"))
        assertTrue(ChatModes.EXCERPT in ChatModes.matching("exzerpt"))
    }

    @Test
    fun parsesTheListOfEvidence() {
        val (body, citations) = Evidence.parse(answer)
        assertTrue("## Belege" !in body)
        assertEquals(2, citations.size)
        val first = citations[0]
        assertEquals(1, first.n)
        assertEquals("Feedback.pdf", first.source)
        assertEquals(2, first.page)
        assertTrue(first.quote!!.startsWith("formatives Feedback"))
        val web = Evidence.parseEntry(3, "OECD-Bericht – https://www.oecd.org/bericht – „Lehrkräfte geben selten schriftliches Feedback“")
        assertEquals("https://www.oecd.org/bericht", web.url)
        val doi = Evidence.parseEntry(4, "Hattie & Timperley (2007), S. 81, doi:10.3102/003465430298487 – „Feedback is one of the most powerful influences“")
        assertEquals("10.3102/003465430298487", doi.doi)
        assertEquals(81, doi.page)
        val time = Evidence.parseEntry(5, "Interview_A.txt, 00:12:30 – „wir haben das dann immer so gemacht“")
        assertEquals("00:12:30", time.time)
    }

    @Test
    fun checksQuotesAndPagesAndMarksUnsupportedStatements() {
        val report = Evidence.check(answer, listOf(source))
        assertEquals(CitationStatus.VERIFIED, report.citations[0].status)
        // The second quote stands on page 3 (with hyphenation at the line end), not on page 2.
        assertEquals(CitationStatus.PAGE_MISMATCH, report.citations[1].status)
        assertEquals(listOf(3), report.citations[1].foundPages)
        assertEquals(1, report.unsupported.size)
        assertTrue(report.unsupported.single().startsWith("Außerdem wirkt Feedback"))
        assertTrue(report.summary().contains("1 falsch"), report.summary())
        val md = report.markdown()
        assertTrue(md.contains("| 2 |") && md.contains("S. 3"), md)
        assertTrue(Evidence.mark(answer, report).contains("⚠"))
    }

    @Test
    fun inventedQuotesAreNotFound() {
        val fake = "Feedback schadet [1].\n\n## Belege\n[1] Feedback.pdf, S. 2 – „Feedback schadet der Motivation aller Lernenden erheblich“"
        val report = Evidence.check(fake, listOf(source))
        assertEquals(CitationStatus.NOT_FOUND, report.citations.single().status)
        // Without any source in the chat a DOI cannot be checked.
        val book = "Feedback wirkt [1].\n\n## Belege\n[1] Hattie (2009), S. 173, doi:10.4324/9780203887332 – „feedback is among the most powerful influences on achievement“"
        assertEquals(CitationStatus.UNCHECKABLE, Evidence.check(book, emptyList()).citations.single().status)
    }

    @Test
    fun sourcesComeFromAttachmentsAndReadingTools() {
        val thread = listOf(
            ChatMessage(role = "user", content = "Was steht drin?", files = listOf(FileAttachment("Feedback.pdf", "application/pdf", pdfText, path = "/tmp/x.pdf"))),
            ChatMessage(role = "assistant", toolCalls = listOf(ToolCall("t1", "fetch_url", """{"url":"https://example.org/a"}"""))),
            ChatMessage(role = "tool", content = "Seiteninhalt der Webseite", toolCallId = "t1", toolName = "fetch_url"),
        )
        val sources = Evidence.sourcesOf(thread)
        assertEquals(2, sources.size)
        assertEquals("/tmp/x.pdf", sources[0].path)
        assertEquals("https://example.org/a", sources[1].url)
    }

    @Test
    fun excerptPagesAreChecked() {
        val good = "## Wörtliche Zitate\n> „formatives Feedback die Leistung deutlich verbessert“ (S. 2)\n> „Längsschnittstudie mit 412 Schülerinnen und Schülern“ (S. 3)"
        val ok = Excerpts.check(good, listOf(source))
        assertTrue(ok.ok)
        assertEquals(2, ok.verified)
        val bad = "> „Längsschnittstudie mit 412 Schülerinnen und Schülern“ (S. 1)"
        val wrong = Excerpts.check(bad, listOf(source))
        assertEquals(1, wrong.problems.size)
        assertTrue(wrong.problems.single().contains("S. 3"))
        assertEquals(ExcerptSchema.REVIEW, ExcerptSchema.parse("review"))
        assertTrue(ExcerptSchema.STANDARD.containsAll(listOf("Fragestellung", "Methode", "Stichprobe", "Zentrale Befunde", "Anschlussfragen")))
    }

    @Test
    fun excerptAttachesToTheLiteratureEntry() = runBlocking {
        val base = Files.createTempDirectory("adeu-exzerpt").toFile()
        if (System.getProperty("adeu.data").isNullOrBlank()) System.setProperty("adeu.data", File(base, "daten").absolutePath)
        try {
            val c = AppContainer(Context(File(base, "files").apply { mkdirs() }, File(base, "cache").apply { mkdirs() }))
            val ref = c.literature.addAll(listOf(Reference(title = "Feedback im Unterricht", doi = "10.1000/xyz"))).first()
            val tools = ExcerptTools(c)
            val ctx = RepoContext(mutableListOf(), files = listOf(FileAttachment("Feedback.pdf", "application/pdf", pdfText)))
            fun args(md: String, force: Boolean = false) = JsonObject(
                mapOf("title" to JsonPrimitive("Feedback im Unterricht"), "markdown" to JsonPrimitive(md), "doi" to JsonPrimitive("10.1000/xyz"), "schema" to JsonPrimitive("empirisch")) +
                    (if (force) mapOf("force" to JsonPrimitive(true)) else emptyMap()),
            )
            val intro = "# Exzerpt\n\n## Bibliografie\nMuster (2024). Feedback im Unterricht.\n\n## Zentrale Befunde\nFeedback hilft.\n\n## Wörtliche Zitate\n"
            val wrong = tools.run(ExcerptTools.NAME, args(intro + "> „Längsschnittstudie mit 412 Schülerinnen und Schülern“ (S. 1)"), ctx)
            assertTrue(wrong.text.startsWith("FEHLER"), wrong.text)
            assertTrue(c.literature.current.first { it.id == ref.id }.excerpt.isBlank(), "nichts gespeichert, solange Seiten falsch sind")
            val right = tools.run(ExcerptTools.NAME, args(intro + "> „Längsschnittstudie mit 412 Schülerinnen und Schülern“ (S. 3)"), ctx)
            assertTrue(right.text.startsWith("Exzerpt gespeichert"), right.text)
            val stored = c.literature.current.first { it.id == ref.id }
            assertTrue(stored.excerpt.contains("Seitenangaben geprüft"))
            assertNotNull(stored.excerptDocId)
            assertTrue(c.docs.items.value.any { it.id == stored.excerptDocId })
            assertTrue(excerptTable(listOf(stored)).contains("Feedback hilft."))
            assertTrue(excerptRequest(stored).contains("Literatur-ID: ${stored.id}"))
        } finally {
            base.deleteRecursively()
        }
    }
}
