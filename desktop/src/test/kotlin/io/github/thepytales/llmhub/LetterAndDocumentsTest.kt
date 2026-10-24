package io.github.thepytales.llmhub

import io.github.thepytales.llmhub.agent.AssistMode
import io.github.thepytales.llmhub.agent.FieldAssist
import io.github.thepytales.llmhub.data.TemplateLibrary
import io.github.thepytales.llmhub.docs.DocStyle
import io.github.thepytales.llmhub.docs.DocTheme
import io.github.thepytales.llmhub.docs.DocumentSources
import io.github.thepytales.llmhub.docs.DocumentWriter
import io.github.thepytales.llmhub.docs.LetterBuilder
import io.github.thepytales.llmhub.docs.Placeholders
import io.github.thepytales.llmhub.docs.TudBrand
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LetterAndDocumentsTest {
    private val out = File("build/test-output").apply { mkdirs() }

    private val body = "vielen Dank für Ihre Anfrage zum Haushalt 2027.\n\nWir reichen die Vorschläge bis zum 30. November ein. " +
        "Die Bilder aus dem Flur werden nicht verkauft."

    @Test
    fun letterKeepsTheUsersTextWord_for_word() {
        val r = LetterBuilder.build(
            LetterBuilder.Input(
                sender = "Dominik Töpper\nWissenschaftlicher Mitarbeiter\n+49 351 463-12345\ndominik.toepper@tu-dresden.de\nRaum WEB 123",
                recipient = "Frau Prof. Dr. Anna Müller, Dezernat Finanzen, Mommsenstr. 7, 01069 Dresden",
                subject = "Haushaltsvorschläge 2027",
                body = body,
            )
        )
        assertTrue(body in r.content, "Brieftext muss wörtlich übernommen werden")
        assertTrue(r.content.startsWith("Sehr geehrte Frau Prof. Dr. Müller,"), r.content)
        assertTrue("Mit freundlichen Grüßen\n\nDominik Töpper  \nWissenschaftlicher Mitarbeiter" in r.content, r.content)
        assertEquals("Haushaltsvorschläge 2027", r.title)
        assertEquals(
            listOf("Frau Prof. Dr. Anna Müller", "Dezernat Finanzen", "Mommsenstr. 7", "01069 Dresden"),
            r.letter.recipient.lines(),
        )
        assertEquals(
            listOf(
                "Dominik Töpper", "Wissenschaftlicher Mitarbeiter", "Telefon: +49 351 463-12345",
                "E-Mail: dominik.toepper@tu-dresden.de", "Raum: WEB 123",
            ),
            r.letter.sender.lines(),
        )
        assertTrue(Placeholders.find(listOf(r.content, r.letter.sender, r.letter.recipient)).isEmpty())
    }

    @Test
    fun salutationsFollowTheRecipient() {
        assertEquals("Sehr geehrter Herr Schmidt,", LetterBuilder.salutationFor("Herrn Max Schmidt\nHauptstr. 1"))
        assertEquals("Sehr geehrte Frau Dr. Weber,", LetterBuilder.salutationFor("Frau Dr. phil. Eva Weber"))
        assertEquals("Sehr geehrte Damen und Herren,", LetterBuilder.salutationFor("Test Person"))
        assertEquals("Sehr geehrte Damen und Herren,", LetterBuilder.salutationFor("Studierendensekretariat\nTU Dresden"))
    }

    @Test
    fun ownSalutationAndClosingAreNotDoubled() {
        val r = LetterBuilder.build(
            LetterBuilder.Input("Erika Muster", "Test Person", "Test", body = "Liebe Frau Test,\n\ndanke!\n\nViele Grüße\nErika"),
        )
        assertEquals(1, Regex("Liebe Frau Test").findAll(r.content).count())
        assertFalse("Mit freundlichen Grüßen" in r.content)
    }

    @Test
    fun letterHtmlKeepsClosingAndSignatureTogether() {
        val r = LetterBuilder.build(LetterBuilder.Input("Dominik Töpper\nFunktion X", "Test Person", "Haushalt", body = body))
        val theme = DocTheme(DocStyle.TUD, TudBrand.BLUE, TudBrand.logoBlue, "", "Fakultät\nInstitut\nProfessur")
        val html = DocumentWriter.toHtml(r.title, r.content, theme, print = true, letter = r.letter)
        File(out, "brief.html").writeText(html)
        assertTrue("<div class=\"closing\">\n<p>Mit freundlichen Grüßen</p>" in html, html.takeLast(600))
        assertTrue("Wir reichen die Vorschläge bis zum 30. November ein." in html)
        assertFalse("[Funktion]" in html || "[Telefon]" in html)
    }

    @Test
    fun letterTemplateIsDirectAndExplainsItsFields() {
        val t = TemplateLibrary.builtIn.first { it.id == TemplateLibrary.BUILT_IN + "tud_letter" }
        assertEquals("letter", t.direct)
        assertEquals(listOf("Absender", "Empfänger", "Betreff", "Bezug", "Anrede", "Brieftext"), t.fields.map { it.label })
        assertTrue(t.fields.all { it.hint.isNotBlank() })
        assertTrue(t.fields.first { it.label == "Brieftext" }.multiline)
        assertTrue(t.fields.first { it.label == "Empfänger" }.isAddress)
    }

    @Test
    fun placeholdersAreFoundAndFilled() {
        val text = "Am [Datum] um [Uhrzeit] – siehe [Link](https://x.de), [ ] offen, [x] erledigt, > [!NOTE] Hinweis, Quelle [1]."
        assertEquals(listOf("Datum", "Uhrzeit"), Placeholders.find(text))
        assertEquals("Am 3. Mai um [Uhrzeit] – siehe", Placeholders.fill(text, mapOf("Datum" to "3. Mai", "Uhrzeit" to " ")).substringBefore(" [Link]"))
    }

    @Test
    fun documentSourcesRoundTrip() {
        val file = File(out, "quelle-test.pdf").apply { writeText("x") }
        val args = buildJsonObject { put("title", "T [Datum]"); put("content", "Hallo [Name]"); put("style", "tud") }
        DocumentSources.save(file, args)
        val loaded = DocumentSources.load(file)!!
        assertEquals(args, loaded)
        val filled = DocumentSources.mapTexts(loaded) { Placeholders.fill(it, mapOf("Name" to "Anna")) }
        assertEquals(JsonPrimitive("Hallo Anna"), filled["content"])
        assertEquals(JsonPrimitive("tud"), filled["style"])
    }

    @Test
    fun fieldAssistAsksForTheFieldOnly() {
        val t = TemplateLibrary.builtIn.first { it.direct == "letter" }
        val field = t.fields.first { it.label == "Brieftext" }
        val messages = FieldAssist.prompt(t, field, mapOf("Betreff" to "Haushalt", "Brieftext" to "Haushalt"), AssistMode.EXPAND)
        assertTrue("ohne Anrede, ohne Grußformel" in messages[0].content)
        assertTrue("Betreff: Haushalt" in messages[1].content && "Bisheriger Inhalt" in messages[1].content)
        assertEquals("Text", FieldAssist.clean("<think>x</think>\n„Text“"))
        assertEquals(listOf(AssistMode.TIDY, AssistMode.CUSTOM), AssistMode.of(t.fields.first { it.label == "Empfänger" }))
    }
}

class PresentationTest {
    private val out = File("build/test-output").apply { mkdirs() }

    val sample = """
        # Classroom Management in der Sekundarstufe
        ## Seminarsitzung 4 · Allgemeine Didaktik
        Dominik Töpper · 24. Oktober 2026

        ---
        # Ablauf der Sitzung
        1. Einstieg: Fallvignette (10 min)
        2. Input: Dimensionen nach Kounin
        3. Gruppenarbeit mit **Videoanalyse**
        4. Sicherung und Ausblick
        Notizen: Vignette vorlesen, dann Murmelgruppen.

        ---
        # Teil 2: Empirische Befunde

        ---
        # Dimensionen der Klassenführung
        - Allgegenwärtigkeit (*withitness*)
          - Lehrkraft signalisiert Überblick
        - Überlappung
        - Reibungslosigkeit und Schwung
        |||
        - Gruppenmobilisierung
        - Abwechslung und Herausforderung

        ---
        # Befunde im Überblick
        Effektstärken aus Metaanalysen:

        | Merkmal | d | Quelle |
        | --- | --- | --- |
        | Klassenführung | 0,52 | Hattie 2009 |
        | Regeln & Routinen | 0,76 | Marzano 2003 |

        ---
        > Gute Klassenführung ist die Voraussetzung, nicht der Inhalt guten Unterrichts.
        > — nach Helmke
    """.trimIndent()

    @Test
    fun slidesAreRecognised() {
        val slides = io.github.thepytales.llmhub.docs.PptxWriter.parse(sample)
        assertEquals(6, slides.size)
        assertEquals(io.github.thepytales.llmhub.docs.PptxWriter.Kind.TITLE, slides[0].kind)
        assertEquals("Seminarsitzung 4 · Allgemeine Didaktik", slides[0].subtitle)
        assertEquals("Vignette vorlesen, dann Murmelgruppen.", slides[1].notes)
        assertEquals(io.github.thepytales.llmhub.docs.PptxWriter.Kind.SECTION, slides[2].kind)
        assertTrue(slides[3].right.isNotEmpty())
        assertEquals(io.github.thepytales.llmhub.docs.PptxWriter.Kind.QUOTE, slides[5].kind)
    }

    @Test
    fun writesAValidPackage() {
        val file = File(out, "praesentation.pptx")
        io.github.thepytales.llmhub.docs.PptxWriter.write(file, "Test", sample, "Fakultät Erziehungswissenschaften\nInstitut für Erziehungswissenschaft\nProfessur für Allgemeine Didaktik und Empirische Unterrichtsforschung")
        val zip = java.util.zip.ZipFile(file)
        val names = zip.entries().toList().map { it.name }
        assertTrue("ppt/slides/slide6.xml" in names && "ppt/notesSlides/notesSlide2.xml" in names && "ppt/media/logo-weiss.png" in names)
        // Every XML part must be well-formed.
        val parser = javax.xml.parsers.DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder()
        names.filter { it.endsWith(".xml") || it.endsWith(".rels") }.forEach { n -> zip.getInputStream(zip.getEntry(n)).use { parser.parse(it) } }
        zip.close()
    }
}
