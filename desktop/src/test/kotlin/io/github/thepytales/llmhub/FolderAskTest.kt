package io.github.thepytales.llmhub

import io.github.thepytales.llmhub.agent.CitationStatus
import io.github.thepytales.llmhub.agent.Evidence
import io.github.thepytales.llmhub.agent.FolderAsk
import io.github.thepytales.llmhub.data.PrivacyLevel
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** AP-58 Frag diesen Ordner: files of a folder, passages with file and page, sources for the Belegprüfung. */
class FolderAskTest {
    /** 50 „PDFs“ as their cached texts: pages with [Seite N], a few files about feedback. */
    private fun folder(root: File): Map<File, String> = (1..50).associate { i ->
        val f = File(root, if (i % 10 == 0) "Unterordner/Studie $i.pdf" else "Studie $i.pdf")
        val pages = (1..6).map { p ->
            val topic = when {
                i == 7 && p == 3 -> "Formatives Feedback steigert die Lernleistung deutlich, besonders bei schwächeren Schülerinnen und Schülern."
                i == 23 && p == 5 -> "Lehrkräfte geben im Mittel nur zwei Minuten Feedback je Stunde; schriftliches Feedback wirkt nachhaltiger."
                i == 41 && p == 2 -> "Peer-Feedback braucht klare Kriterien, sonst bleibt es oberflächlich."
                else -> "Die Stichprobe umfasste $i Klassen in Sachsen. Seite $p beschreibt Methode und Rahmenbedingungen der Erhebung."
            }
            "[Seite $p]\n$topic\nWeitere Ausführungen zu Unterricht, Schule und Organisation."
        }
        f to pages.joinToString("\n\n")
    }

    @Test
    fun filesOfAFolder() {
        val root = Files.createTempDirectory("ordner").toFile()
        File(root, "a.pdf").writeText("x")
        File(root, "Notizen.md").writeText("x")
        File(root, "bild.png").writeText("x")
        File(root, ".versteckt").mkdirs(); File(root, ".versteckt/b.pdf").writeText("x")
        File(root, "~\$offen.docx").writeText("x")
        File(root, "Unter").mkdirs(); File(root, "Unter/c.docx").writeText("x")
        assertEquals(listOf("a.pdf", "Notizen.md", "Unter/c.docx"), FolderAsk.files(root).map { it.relativeTo(root).path.replace('\\', '/') })
        assertNull(FolderAsk.suggestedPrivacy(root, FolderAsk.files(root)))
        File(root, "Gutachten Müller.pdf").writeText("x")
        assertEquals(PrivacyLevel.TU, FolderAsk.suggestedPrivacy(root, FolderAsk.files(root)))
        root.deleteRecursively()
    }

    @Test
    fun passagesFromSeveralFilesWithPages() {
        val root = File("/daten/Literatur")
        val texts = folder(root)
        val hits = FolderAsk.rank(texts, "Was sagen die Studien über Feedback?", k = 6, root = root)
        // The three files about feedback come first, with their page.
        assertEquals(setOf("Studie 7.pdf" to 3, "Studie 23.pdf" to 5, "Studie 41.pdf" to 2), hits.take(3).map { it.file to it.page }.toSet())
        assertTrue(FolderAsk.terms("Was sagen die Studien über Feedback?").contains("feedback"))
        assertEquals(6, FolderAsk.pages(texts.values.first()).size)
        // Text without pages comes in pieces (page 0).
        assertTrue(FolderAsk.pages("a".repeat(4000)).all { it.first == 0 } && FolderAsk.pages("a".repeat(4000)).size >= 3)
        val out = FolderAsk.describe(hits, "Literatur")
        assertTrue(out.startsWith("### Datei: "))
        // Fertig, wenn: an answer with evidence from several files is checked, and each source knows its file to open.
        val sources = FolderAsk.sources(out)
        assertTrue(sources.any { it.label == "Studie 7.pdf" && it.path!!.endsWith("Studie 7.pdf") })
        val answer = """
            Formatives Feedback wirkt [1], wird aber selten gegeben [2]. Peer-Feedback braucht Kriterien [3].

            ## Belege
            [1] Studie 7.pdf, S. 3 – „Formatives Feedback steigert die Lernleistung deutlich“
            [2] Studie 23.pdf, S. 5 – „Lehrkräfte geben im Mittel nur zwei Minuten Feedback je Stunde“
            [3] Studie 41.pdf, S. 2 – „Peer-Feedback braucht klare Kriterien“
        """.trimIndent()
        val report = Evidence.check(answer, sources)
        assertEquals(listOf(CitationStatus.VERIFIED, CitationStatus.VERIFIED, CitationStatus.VERIFIED), report.citations.map { it.status })
        assertEquals(listOf("Studie 7.pdf", "Studie 23.pdf", "Studie 41.pdf"), report.citations.map { it.source!!.label })
        // A wrong page is noticed (one page later counts: a quote may run over the page break).
        val wrong = Evidence.check(answer.replace("Studie 7.pdf, S. 3", "Studie 7.pdf, S. 5"), sources)
        assertEquals(CitationStatus.PAGE_MISMATCH, wrong.citations.first().status)
        assertTrue(FolderAsk.describe(emptyList(), "Literatur").contains("nichts"))
        assertTrue(FolderAsk.instructions(root, texts.keys.toList()).contains("search_folder"))
    }
}
