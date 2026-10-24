package io.github.thepytales.llmhub

import io.github.thepytales.llmhub.agent.ReferenceCheck
import io.github.thepytales.llmhub.agent.ScriptRunner
import io.github.thepytales.llmhub.data.TemplateLibrary
import io.github.thepytales.llmhub.docs.AccessibilityCheck
import io.github.thepytales.llmhub.docs.Contrast
import io.github.thepytales.llmhub.docs.DocTheme
import io.github.thepytales.llmhub.docs.MarkdownDocx
import io.github.thepytales.llmhub.docs.PptxWriter
import io.github.thepytales.llmhub.docs.QtiWriter
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import java.io.File
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TeachingAndResearchTest {
    private val out = File("build/test-output").apply { mkdirs() }

    private val quiz = """
        # Selbsttest Klassenführung
        ## Präventive Klassenführung (1 Punkt)
        Welches Merkmal beschreibt Kounin als „Allgegenwärtigkeit“?
        - [x] Die Lehrkraft signalisiert, dass sie alles im Blick hat.
        - [ ] Die Lehrkraft wechselt häufig die Sozialform.
        - [ ] Die Lehrkraft lobt regelmäßig.
        Feedback: Kounin nennt das „withitness“.

        ## Basisdimensionen (2 Punkte)
        Welche gehören zu den drei Basisdimensionen der Unterrichtsqualität?
        - [x] Klassenführung
        - [x] Kognitive Aktivierung
        - [ ] Hausaufgabenkontrolle

        ## Begriff
        Wie heißt der Zeitraum zwischen Frage und Aufruf?
        Antwort: Wartezeit | Wait time

        ## Reflexion (4 Punkte)
        Erläutern Sie an einem Beispiel, wie Regeln eingeführt werden können.
        Erwartung: Regeln gemeinsam entwickeln, positiv formulieren, sichtbar machen, konsequent anwenden.
    """.trimIndent()

    @Test
    fun quizBecomesAQti21PackageWithAllQuestionTypes() {
        val test = QtiWriter.parse("Test", quiz)
        assertEquals("Selbsttest Klassenführung", test.title)
        assertEquals(listOf(QtiWriter.Kind.SINGLE, QtiWriter.Kind.MULTIPLE, QtiWriter.Kind.TEXT, QtiWriter.Kind.ESSAY), test.questions.map { it.kind })
        assertEquals(listOf(1.0, 2.0, 1.0, 4.0), test.questions.map { it.points })
        assertEquals(listOf("Wartezeit", "Wait time"), test.questions[2].answers)
        val file = File(out, "selbsttest.zip").apply { delete() }
        QtiWriter.write(file, "Test", quiz)
        ZipFile(file).use { zip ->
            val names = zip.entries().asSequence().map { it.name }.toList()
            assertTrue("imsmanifest.xml" in names && "assessmentTest.xml" in names, names.toString())
            assertEquals(4, names.count { it.startsWith("item_") })
            // Every part is well-formed XML.
            val parser = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder()
            names.forEach { n -> zip.getInputStream(zip.getEntry(n)).use { parser.parse(it) } }
            val single = zip.getInputStream(zip.getEntry("item_1.xml")).bufferedReader().readText()
            assertTrue("maxChoices=\"1\"" in single && "<value>choice_1</value>" in single && "modalFeedback" in single, single)
            val multiple = zip.getInputStream(zip.getEntry("item_2.xml")).bufferedReader().readText()
            assertTrue("cardinality=\"multiple\"" in multiple && "mapEntry mapKey=\"choice_3\" mappedValue=\"-1\"" in multiple, multiple)
            val text = zip.getInputStream(zip.getEntry("item_3.xml")).bufferedReader().readText()
            assertTrue("textEntryInteraction" in text && "caseSensitive=\"false\"" in text, text)
            val essay = zip.getInputStream(zip.getEntry("item_4.xml")).bufferedReader().readText()
            assertTrue("extendedTextInteraction" in essay, essay)
            val manifest = zip.getInputStream(zip.getEntry("imsmanifest.xml")).bufferedReader().readText()
            assertTrue("imsqti_test_xmlv2p1" in manifest && manifest.split("imsqti_item_xmlv2p1").size == 5, manifest)
        }
        assertEquals("4 Fragen (1 Einfachauswahl, 1 Mehrfachauswahl, 1 Kurzantwort, 1 Freitext), 8 Punkte", QtiWriter.summary(test))
    }

    @Test
    fun evaluationHelpersCount() {
        val csv = "Item;Antwort\nVerständlich;1\nVerständlich;2\nVerständlich;2\nVerständlich;\n"
        val r = ScriptRunner.run(
            "var d = parseCSV(files['eva.csv']); var a = column(d, 'Antwort'); print(JSON.stringify(describe(a))); " +
                "print(JSON.stringify(freq(a))); print(percent(2, 3))",
            mapOf("eva.csv" to csv),
        )
        assertNull(r.error, r.output)
        assertTrue("\"n\":3" in r.output && "\"M\":1.67" in r.output, r.output)
        assertTrue("\"2\":2" in r.output && "(leer)" in r.output, r.output)
        assertTrue("66.7" in r.output, r.output)
    }

    @Test
    fun referencesAreReadFromAReferenceList() {
        val text = """
            Wie bei Hattie (2009) gezeigt, …

            Literatur
            - Hattie, J. (2009). *Visible learning: A synthesis of over 800 meta-analyses relating to achievement*. Routledge.
            - Kunter, M., & Trautwein, U. (2013). Psychologie des Unterrichts. Schöningh.
            - Praetorius, A.-K., Klieme, E., Herbert, B., & Pinger, P. (2018). Generic dimensions of teaching quality. ZDM, 50(3), 407–426. https://doi.org/10.1007/s11858-018-0918-4.
        """.trimIndent()
        val refs = ReferenceCheck.extract(text)
        assertEquals(3, refs.size, refs.toString())
        val p = ReferenceCheck.parse(refs[2])
        assertEquals("10.1007/s11858-018-0918-4", p.doi)
        assertEquals(2018, p.year)
        assertTrue("Praetorius" in p.surnames && "Klieme" in p.surnames, p.surnames.toString())
        assertEquals("Generic dimensions of teaching quality", p.title)
        val hattie = ReferenceCheck.parse(refs[0])
        assertEquals(listOf("Hattie"), hattie.surnames)
        assertTrue(hattie.title.startsWith("Visible learning"), hattie.title)
    }

    @Test
    fun referencesAreJudgedByTitleAuthorsAndYear() {
        val p = ReferenceCheck.parse("Hattie, J. (2009). Visible learning. Routledge.")
        val right = ReferenceCheck.Work("Visible Learning", listOf("John Hattie"), 2008, "Routledge", "10.4324/9780203887332", null, "Crossref")
        assertEquals(ReferenceCheck.Status.VERIFIED, ReferenceCheck.judge(p, right, viaDoi = false).first)
        val otherYear = right.copy(year = 2015)
        assertEquals(ReferenceCheck.Status.DIFFERS, ReferenceCheck.judge(p, otherYear, viaDoi = false).first)
        val wrong = ReferenceCheck.Work("Teacher stress and burnout", listOf("Anna Muster"), 2009, "", null, null, "Crossref")
        assertEquals(ReferenceCheck.Status.NOT_FOUND, ReferenceCheck.judge(p, wrong, viaDoi = false).first)
        assertEquals(ReferenceCheck.Status.DIFFERS, ReferenceCheck.judge(p, wrong, viaDoi = true).first)
    }

    /** Asks Crossref for real (only with LIVE_WEB=1, the CI has no reliable internet). */
    @Test
    fun referencesAreFoundAtCrossref() {
        if (System.getenv("LIVE_WEB") != "1") return
        val results = runBlocking {
            ReferenceCheck.check(
                OkHttpClient(),
                listOf(
                    "Praetorius, A.-K., Klieme, E., Herbert, B., & Pinger, P. (2018). Generic dimensions of teaching quality: The German framework of Three Basic Dimensions. ZDM, 50(3), 407–426. https://doi.org/10.1007/s11858-018-0918-4",
                    "Hattie, J. (2009). Visible learning. Routledge.",
                    "Zwiebelmeier, Q. (2021). Die didaktische Quantenwolke im Sachunterricht der Antarktis. Zeitschrift für erfundene Didaktik, 3, 1–9.",
                ),
            )
        }
        println(ReferenceCheck.report(results))
        assertEquals(ReferenceCheck.Status.VERIFIED, results[0].status)
        assertTrue(results[1].status in setOf(ReferenceCheck.Status.VERIFIED, ReferenceCheck.Status.DIFFERS))
        assertEquals(ReferenceCheck.Status.NOT_FOUND, results[2].status)
    }

    @Test
    fun generatedOfficeFilesPassTheAccessibilityCheck() {
        val docx = File(out, "barrierefrei.docx")
        MarkdownDocx.write(docx, "Merkblatt", "# Merkblatt\n\nText.\n\n## Termine\n\n| Datum | Thema |\n| --- | --- |\n| 1.11. | Start |\n")
        val docFindings = AccessibilityCheck.docx(docx)
        assertTrue(docFindings.all { it.ok }, docFindings.toString())
        val pptx = File(out, "barrierefrei.pptx")
        PptxWriter.write(pptx, "Vortrag", "# Vortrag\n## Untertitel\n---\n# Inhalt\n- Punkt\n---\n# Abschnitt\n---\n> Zitat\n> — Quelle", "Professur")
        val slideFindings = AccessibilityCheck.pptx(pptx)
        assertTrue(slideFindings.all { it.ok }, slideFindings.toString())
        ZipFile(pptx).use { zip ->
            val slide = zip.getInputStream(zip.getEntry("ppt/slides/slide2.xml")).bufferedReader().readText()
            // Title first in reading order, logo and footer after the content.
            assertTrue(slide.indexOf("ph type=\"title\"") < slide.indexOf("Logo"), slide)
            assertTrue("decorative" in slide)
        }
        val missing = AccessibilityCheck.html("<html><body><img src=x.png><p>Text</p></body></html>")
        assertEquals(4, missing.count { !it.ok }, missing.toString())
    }

    @Test
    fun tooLightAccentsAreDarkenedForContrast() {
        assertTrue(Contrast.ratio("#000000", "#FFFFFF") > 20.9)
        assertEquals("#1E40AF", Contrast.readable(DocTheme.DEFAULT_ACCENT))
        val yellow = Contrast.readable("#FFD400")
        assertTrue(Contrast.ratio(yellow, "#FFFFFF") >= Contrast.MIN, yellow)
    }

    @Test
    fun newTemplatesExistAndUseTheirTools() {
        fun t(id: String) = assertNotNull(TemplateLibrary.builtIn.firstOrNull { it.id == TemplateLibrary.BUILT_IN + id }, id)
        assertTrue("format=qti" in t("teach_quiz").instructions.orEmpty())
        assertTrue("run_javascript" in t("teach_evaluation").instructions.orEmpty())
        assertTrue("check_references" in t("res_refcheck").instructions.orEmpty())
        assertTrue("fill_pdf" in t("travel_expenses").instructions.orEmpty())
        assertTrue("check_accessibility" in t("accessibility").instructions.orEmpty())
        assertFalse(TemplateLibrary.builtIn.groupBy { it.id }.any { it.value.size > 1 }, "doppelte Vorlagen-IDs")
    }
}
