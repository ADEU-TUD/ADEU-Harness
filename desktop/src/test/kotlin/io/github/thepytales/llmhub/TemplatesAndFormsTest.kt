package io.github.thepytales.llmhub

import android.content.Context
import io.github.thepytales.llmhub.agent.ApprovalMode
import io.github.thepytales.llmhub.agent.Risk
import io.github.thepytales.llmhub.data.TemplateFields
import io.github.thepytales.llmhub.data.TemplateKind
import io.github.thepytales.llmhub.data.TemplateLibrary
import io.github.thepytales.llmhub.docs.DocStyle
import io.github.thepytales.llmhub.docs.DocTheme
import io.github.thepytales.llmhub.docs.DocumentWriter
import io.github.thepytales.llmhub.docs.Letter
import io.github.thepytales.llmhub.docs.PdfForms
import io.github.thepytales.llmhub.docs.PdfText
import io.github.thepytales.llmhub.docs.TudBrand
import io.github.thepytales.llmhub.docs.describe
import org.apache.pdfbox.Loader
import org.apache.pdfbox.cos.COSName
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.PDResources
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm
import org.apache.pdfbox.pdmodel.interactive.form.PDTextField
import org.apache.pdfbox.text.PDFTextStripper
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TemplatesAndFormsTest {
    private val out = File("build/test-output").apply { mkdirs() }
    private val context = Context(out, out)

    @Test
    fun placeholdersAreFilledAndEmptyLinesDropped() {
        val text = "Absender: {{Absender|Name}}\nBetreff: {{Betreff}}\nFester Text\nNotiz: {{Notiz|optional}}"
        assertEquals(listOf("Absender", "Betreff", "Notiz"), TemplateFields.of(text).map { it.label })
        val filled = TemplateFields.fill(text, mapOf("Absender" to "Dr. Erika Musterfrau", "Betreff" to " Antrag "))
        assertEquals("Absender: Dr. Erika Musterfrau\nBetreff: Antrag\nFester Text", filled)
    }

    @Test
    fun builtInLibraryIsConsistent() {
        val all = TemplateLibrary.builtIn
        assertEquals(all.size, all.map { it.id }.toSet().size, "doppelte ids")
        assertTrue(all.all { it.builtIn })
        all.filter { it.kind == TemplateKind.PROMPT }.forEach { t ->
            assertTrue(t.instructions.isNotBlank(), "${t.name}: keine Anweisungen")
            assertTrue(t.fields.isNotEmpty(), "${t.name}: keine Felder")
            assertFalse("{{" in TemplateFields.fill(t.content, t.fields.associate { it.label to "x" }), "${t.name}: Platzhalter übrig")
        }
        assertTrue(TemplateLibrary.suggestions(emptyList(), emptyList()).size >= 4)
    }

    @Test
    fun ownTemplatesSurviveExportAndImport() {
        val own = TemplateLibrary.copyOf(TemplateLibrary.builtIn.first())
        val imported = TemplateLibrary.import("Hallo,\nhier meine Vorlage:\n" + TemplateLibrary.export(listOf(own)))
        assertEquals(1, imported.size)
        assertEquals(own.name, imported[0].name)
        assertEquals(own.instructions, imported[0].instructions)
        assertFalse(imported[0].builtIn)
    }

    @Test
    fun approvalModes() {
        assertTrue(ApprovalMode.ASK.asks(Risk.EDIT))
        assertFalse(ApprovalMode.EDITS.asks(Risk.EDIT))
        assertTrue(ApprovalMode.EDITS.asks(Risk.PUBLIC))
        assertFalse(ApprovalMode.ALL.asks(Risk.PUBLIC))
        assertEquals(ApprovalMode.ASK, ApprovalMode.of("unbekannt"))
    }

    @Test
    fun pdfFormIsReadAndFilled() {
        val src = File(out, "formular.pdf")
        PDDocument().use { doc ->
            val page = PDPage(PDRectangle.A4)
            doc.addPage(page)
            val font = PDType1Font(Standard14Fonts.FontName.HELVETICA)
            val form = PDAcroForm(doc)
            doc.documentCatalog.acroForm = form
            form.defaultResources = PDResources().apply { put(COSName.getPDFName("Helv"), font) }
            form.defaultAppearance = "/Helv 10 Tf 0 g"
            val field = PDTextField(form)
            field.partialName = "txt_name"
            field.defaultAppearance = "/Helv 10 Tf 0 g"
            form.fields.add(field)
            val widget = field.widgets[0]
            widget.rectangle = PDRectangle(150f, 700f, 200f, 18f)
            widget.page = page
            page.annotations.add(widget)
            PDPageContentStream(doc, page).use { cs ->
                cs.beginText(); cs.setFont(font, 10f); cs.newLineAtOffset(60f, 705f); cs.showText("Name, Vorname:"); cs.endText()
            }
            doc.save(src)
        }
        val info = PdfForms.inspect(context, src)
        assertEquals(1, info.fields.size)
        assertEquals("Name, Vorname:", info.fields[0].label)
        assertTrue("txt_name" in info.describe("formular.pdf"))

        val filled = File(out, "formular-ausgefuellt.pdf")
        val report = PdfForms.fill(context, src, filled, mapOf("txt_name" to "Musterfrau, Erika", "gibtsnicht" to "x"), emptyList(), flatten = false)
        assertEquals(listOf("txt_name"), report.filled)
        assertTrue(report.problems.any { "gibtsnicht" in it })
        Loader.loadPDF(filled).use { assertEquals("Musterfrau, Erika", it.documentCatalog.acroForm.getField("txt_name").valueAsString) }

        // Without fields: text is printed at a position, umlauts included.
        val flat = File(out, "ohne-felder.pdf")
        PdfForms.fill(context, filled, flat, emptyMap(), listOf(PdfText(1, 60f, 40f, "Grüße aus Dresden – „TUD“")), flatten = true)
        Loader.loadPDF(flat).use { assertTrue("Grüße aus Dresden" in PDFTextStripper().getText(it)) }
    }

    @Test
    fun tudDocumentsUseTheCorporateDesign() {
        val theme = TudBrand.apply(DocTheme(style = DocStyle.TUD, unit = "Fakultät Erziehungswissenschaften\nProfessur für Allgemeine Didaktik und Empirische Unterrichtsforschung"))
        assertTrue(TudBrand.logoBlue.size > 1000 && TudBrand.fontFaces.contains("font/woff2"))
        val doc = DocumentWriter.toHtml("Protokoll der Teamsitzung", SAMPLE, theme, print = true)
        val letter = DocumentWriter.toHtml(
            "Ihr Antrag vom 1. Oktober", "Sehr geehrte Frau Muster,\n\nvielen Dank.\n\nMit freundlichen Grüßen",
            theme, print = true, letter = Letter("Dr. Erika Musterfrau\nWissenschaftliche Mitarbeiterin\nTelefon: +49 351 463-00000\nE-Mail: erika.musterfrau@tu-dresden.de", "Frau\nMaria Muster\nMusterstraße 1\n01069 Dresden"),
        )
        val cert = DocumentWriter.toHtml(
            "Teilnahmebescheinigung",
            "## Dr. Erika Musterfrau\n\nhat erfolgreich am Workshop\n\n## Unterrichtsqualität beobachten\n\n" +
                "an der Professur für Allgemeine Didaktik und Empirische Unterrichtsforschung der Technischen Universität Dresden " +
                "am 5. März 2026 (8 Unterrichtseinheiten) teilgenommen.\n\n> Inhalte: Beobachtungsinstrumente, Videoanalyse.\n\n" +
                "| Dr. Frank Beier | Prof. Dr. Axel Gehrmann |\n| --- | --- |\n| Lehrstuhlvertretung | Professor |",
            theme, print = true, certificate = true,
        )
        assertTrue("cert-sign" in cert && "cert-big" in cert && "Dresden, " in cert)
        assertTrue("letter-row" in letter && "Professur für Allgemeine Didaktik" in letter)
        File(out, "tud-dokument.html").writeText(doc)
        File(out, "tud-brief.html").writeText(letter)
        File(out, "tud-urkunde.html").writeText(cert)
    }

    private companion object {
        const val SAMPLE = "| Angabe | Eintrag |\n| --- | --- |\n| Datum | 24. Oktober 2026 |\n| Ort | MS1/405 |\n\n" +
            "## Tagesordnung\n\n1. Begrüßung\n2. Lehrplanung Sommersemester\n\n## TOP 1 – Begrüßung\n\nDie Sitzung wurde eröffnet.\n\n" +
            "> [!IMPORTANT]\n> Beschluss: Das Kolloquium findet monatlich statt (einstimmig).\n\n## Aufgaben\n\n" +
            "| Nr. | Aufgabe | Verantwortlich | Termin |\n| --- | --- | --- | --- |\n| 1 | Raum buchen | D. Töpper | 31.10. |\n\n" +
            "- [ ] Teilnahme bestätigt\n- [x] Unterlagen versendet\n\n| Name | |\n| --- | --- |\n| Unterschrift | |"
    }
}
