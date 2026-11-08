package io.github.thepytales.llmhub

import io.github.thepytales.llmhub.docs.OfficeImport
import io.github.thepytales.llmhub.docs.PptxWriter
import io.github.thepytales.llmhub.docs.SlideDeck
import java.nio.file.Files
import java.util.zip.ZipFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** AP-51 Folien wie PowerPoint: transitions, points one by one, PowerPoint round trip and decks from documents. */
class SlidesLikePowerPointTest {
    private val deck = listOf(
        SlideDeck.Slide("Vortrag", "Untertitel", layout = SlideDeck.Layout.TITLE),
        SlideDeck.Slide("Ergebnisse", "Einleitung\n\n- Erster Punkt\n  - Unterpunkt\n- Zweiter Punkt\n- Dritter Punkt", transition = "fade", build = true),
        SlideDeck.Slide("Ausblick", "- Später", transition = "push"),
    )

    @Test
    fun transitionsAndBuildStayWithTheSlide() {
        val md = SlideDeck.markdown(deck)
        assertTrue("<!-- animation: " in md, md)
        val back = SlideDeck.parse(md)
        assertEquals(listOf("", "fade", "push"), back.map { it.transition })
        assertEquals(listOf(false, true, false), back.map { it.build })
        // The animation line is no text of the slide.
        assertEquals(deck[1].body, back[1].body)
        // Points appear one per click; sub-points come with their point.
        assertEquals(3, SlideDeck.buildCount(back[1]))
        assertEquals(0, SlideDeck.buildCount(back[2]))
        assertEquals("Einleitung", SlideDeck.buildStep(back[1], 0).body)
        assertEquals("Einleitung\n\n- Erster Punkt\n  - Unterpunkt", SlideDeck.buildStep(back[1], 1).body)
        assertEquals(back[1], SlideDeck.buildStep(back[1], 3))
    }

    @Test
    fun powerPointWritesAndReadsAnimations() {
        val file = Files.createTempFile("ap51", ".pptx").toFile()
        PptxWriter.write(file, "Vortrag", SlideDeck.markdown(deck), "Professur")
        ZipFile(file).use { zip ->
            val s2 = zip.getInputStream(zip.getEntry("ppt/slides/slide2.xml")).readBytes().toString(Charsets.UTF_8)
            val s3 = zip.getInputStream(zip.getEntry("ppt/slides/slide3.xml")).readBytes().toString(Charsets.UTF_8)
            assertTrue("<p:transition spd=\"med\"><p:fade/></p:transition>" in s2)
            assertTrue("<p:timing>" in s2 && "build=\"p\"" in s2 && "<p:pRg st=\"0\" end=\"0\"/>" in s2, s2.takeLast(600))
            // transition and timing follow clrMapOvr, as the schema wants it.
            assertTrue(s2.indexOf("</p:clrMapOvr>") < s2.indexOf("<p:transition") && s2.indexOf("<p:transition") < s2.indexOf("<p:timing>"))
            assertTrue("<p:push dir=\"l\"/>" in s3 && "<p:timing>" !in s3)
            // Every paragraph of the body gets its click (the build targets an existing text box).
            val spid = Regex("<p:bldP spid=\"(\\d+)\"").find(s2)!!.groupValues[1]
            assertTrue("<p:cNvPr id=\"$spid\"" in s2)
        }
        val back = SlideDeck.parse(OfficeImport.read("vortrag.pptx", file.readBytes())!!.tabs.single().second)
        assertEquals(listOf("", "fade", "push"), back.map { it.transition })
        assertEquals(listOf(false, true, false), back.map { it.build })
        file.delete()
    }

    @Test
    fun deckFromDocumentWithAnimations() {
        val doc = "# Studie\n\n## Methode\n\n- Fragebogen\n- 120 Personen\n\n## Ergebnisse\n\nDie Wirkung war deutlich und hielt über das Semester an.\n\n- Effekt mittel\n- stabil"
        val slides = SlideDeck.fromDocument(doc, "Studie").mapIndexed { i, s -> if (i > 0 && s.layout == SlideDeck.Layout.CONTENT) s.copy(build = true, transition = "fade") else s }
        assertTrue(slides.size >= 4)
        assertEquals(SlideDeck.Layout.TITLE, slides.first().layout)
        assertTrue(slides.any { it.build && it.title == "Methode" })
        assertTrue(slides.first { it.title == "Ergebnisse" }.notes.contains("Wirkung"))
        assertEquals(slides.map { it.transition }, SlideDeck.parse(SlideDeck.markdown(slides)).map { it.transition })
    }

    /** A small paper as PDF: title, authors, abstract, numbered sections with a subsection, a table, a figure, references. */
    private fun paperPdf(): java.io.File {
        val file = Files.createTempFile("paper", ".pdf").toFile()
        org.apache.pdfbox.pdmodel.PDDocument().use { doc ->
            val regular = org.apache.pdfbox.pdmodel.font.PDType1Font(org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName.HELVETICA)
            val bold = org.apache.pdfbox.pdmodel.font.PDType1Font(org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName.HELVETICA_BOLD)
            val pages = listOf(
                listOf(
                    Triple(20f, bold, "Lernen mit Feedback im Hörsaal"), Triple(0f, regular, ""),
                    Triple(11f, regular, "Anna Muster, Ben Beispiel"), Triple(9f, regular, "Technische Universität Dresden, Institut für Lehre"), Triple(0f, regular, ""),
                    Triple(13f, bold, "Zusammenfassung"),
                    Triple(10f, regular, "Wir untersuchen, wie kurzes Feedback in großen Vorlesungen das Lernen verändert."),
                    Triple(10f, regular, "In zwei Gruppen mit 78 Studierenden verbesserte Feedback die Noten deutlich um 0,7."),
                    Triple(10f, regular, "Die Ergebnisse sprechen für regelmäßige Rückmeldungen in jeder Sitzung."), Triple(0f, regular, ""),
                    Triple(13f, bold, "1 Einleitung"),
                    Triple(10f, regular, "Große Vorlesungen lassen wenig Raum für Rückmeldungen an einzelne Studierende und"),
                    Triple(10f, regular, "40 Studierende pro Übung sind keine Seltenheit"),
                    Triple(10f, regular, "an deutschen Hochschulen, wie viele Fakultäten berichten."),
                    Triple(10f, regular, "Frühere Studien zeigen, dass Feedback zu den wirksamsten Faktoren des Lernens gehört."),
                ),
                listOf(
                    Triple(13f, bold, "2 Methode"),
                    Triple(10f, regular, "Wir verglichen zwei Kurse über ein Semester mit identischen Prüfungen am Ende."), Triple(0f, regular, ""),
                    Triple(11f, bold, "2.1 Teilnehmende"),
                    Triple(10f, regular, "Insgesamt nahmen 78 Studierende des Lehramts teil, 40 davon in der Feedbackgruppe."),
                    Triple(10f, regular, "Die Teilnahme war freiwillig und wurde nicht vergütet."), Triple(0f, regular, ""),
                    Triple(-1f, regular, "FIGURE"),
                    Triple(9f, regular, "Abbildung 1: Ablauf der Studie mit zwei Gruppen"),
                ),
                listOf(
                    Triple(13f, bold, "3 Ergebnisse"),
                    Triple(10f, regular, "Die Feedbackgruppe erreichte im Mittel die Note 2,1, die Kontrollgruppe 2,8."),
                    Triple(10f, regular, "Tabelle 1: Noten am Ende des Semesters"),
                    Triple(-2f, regular, "Gruppe|Mittelwert|n"), Triple(-2f, regular, "Feedback|2,1|40"), Triple(-2f, regular, "Kontrolle|2,8|38"), Triple(0f, regular, ""),
                    Triple(13f, bold, "4 Diskussion"),
                    Triple(10f, regular, "Kurzes Feedback wirkt auch in großen Gruppen, kostet aber Zeit in der Vorbereitung."),
                    Triple(10f, regular, "Künftige Studien sollten weitere Fächer und längere Zeiträume untersuchen."), Triple(0f, regular, ""),
                    Triple(13f, bold, "Literatur"),
                    Triple(9f, regular, "Hattie, J. (2009). Visible Learning. Routledge."),
                ),
            )
            pages.forEachIndexed { n, lines ->
                val page = org.apache.pdfbox.pdmodel.PDPage(org.apache.pdfbox.pdmodel.common.PDRectangle.A4)
                doc.addPage(page)
                org.apache.pdfbox.pdmodel.PDPageContentStream(doc, page).use { cs ->
                    fun text(x: Float, y: Float, size: Float, font: org.apache.pdfbox.pdmodel.font.PDFont, s: String) {
                        cs.beginText(); cs.setFont(font, size); cs.newLineAtOffset(x, y); cs.showText(s); cs.endText()
                    }
                    // Running head and page number: on every page, not part of the talk.
                    text(72f, 810f, 8f, regular, "Muster und Beispiel – Lernen mit Feedback")
                    text(290f, 30f, 8f, regular, "${n + 1}")
                    var y = 760f
                    for ((size, font, s) in lines) {
                        when {
                            size == 0f -> y -= 10f
                            size == -1f -> {
                                val img = java.awt.image.BufferedImage(400, 240, java.awt.image.BufferedImage.TYPE_INT_RGB)
                                img.createGraphics().apply { color = java.awt.Color(0, 0, 140); fillRect(40, 40, 320, 160); dispose() }
                                cs.drawImage(org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory.createFromImage(doc, img), 72f, y - 180f, 300f, 180f)
                                y -= 195f
                            }
                            size == -2f -> { s.split('|').forEachIndexed { k, cell -> text(72f + k * 170f, y, 10f, regular, cell) }; y -= 14f }
                            else -> { text(72f, y, size, font, s); y -= size * 1.45f }
                        }
                    }
                }
            }
            doc.save(file)
        }
        return file
    }

    @Test
    fun deckFromPaperWithFiguresTablesAndAnimations() {
        val pdf = paperPdf()
        val lines = io.github.thepytales.llmhub.docs.PaperDeck.lines(io.github.thepytales.llmhub.docs.PdfTools.textRuns(null, pdf).first)
        val paper = io.github.thepytales.llmhub.docs.PaperDeck.read(lines, "paper")
        assertEquals("Lernen mit Feedback im Hörsaal", paper.title)
        assertEquals(listOf("Anna Muster, Ben Beispiel"), paper.authors)
        assertTrue("78 Studierenden" in paper.abstract, paper.abstract)
        // Sections and the subsection; the line „40 Studierende …“ inside a paragraph is no heading, the references go.
        assertEquals(listOf("Einleitung" to 1, "Methode" to 1, "Teilnehmende" to 2, "Ergebnisse" to 1, "Diskussion" to 1), paper.sections.map { it.title to it.level })
        assertTrue(paper.sections.none { "Hattie" in it.text } && paper.sections.none { "Muster und Beispiel" in it.text })
        assertEquals(listOf(listOf("Gruppe", "Mittelwert", "n"), listOf("Feedback", "2,1", "40"), listOf("Kontrolle", "2,8", "38")), paper.tables.single().third)

        val base = Files.createTempDirectory("paper-deck").toFile()
        val figures = io.github.thepytales.llmhub.agent.SlidesLora.figures(pdf, base)
        assertEquals(1, figures.size)
        assertEquals(1, figures.single().page)
        assertTrue(java.io.File(base, figures.single().path).isFile)
        val slides = io.github.thepytales.llmhub.docs.PaperDeck.slides(paper, figures)
        assertEquals(SlideDeck.Layout.TITLE, slides.first().layout)
        assertEquals("Anna Muster, Ben Beispiel", slides.first().body)
        assertEquals("Worum es geht", slides[1].title)
        assertEquals(SlideDeck.Layout.AGENDA, slides[2].layout)
        assertEquals("- Einleitung\n- Methode\n- Ergebnisse\n- Diskussion", slides[2].body)
        assertEquals(SlideDeck.Layout.SECTION, slides.first { it.title == "Methode" }.layout)
        val part = slides.first { it.title == "Teilnehmende" }
        assertTrue(part.build && part.transition == "fade" && part.notes.isNotBlank() && "78 Studierende" in part.body, part.toString())
        val figure = slides.first { it.layout == SlideDeck.Layout.IMAGE }
        assertEquals("Abbildung 1", figure.title)
        assertTrue("Ablauf der Studie" in figure.imageAlt)
        // The figure follows its section, the table its own.
        assertTrue(slides.indexOf(figure) < slides.indexOfFirst { it.title == "Ergebnisse" })
        val table = slides.first { it.layout == SlideDeck.Layout.TABLE }
        assertEquals("Tabelle 1", table.title)
        assertTrue("| Feedback | 2,1 | 40 |" in table.body, table.body)
        assertEquals(SlideDeck.Layout.END, slides.last().layout)

        // Presented with animations, also in PowerPoint.
        val md = SlideDeck.markdown(slides.map { if (it.image.isNotBlank()) it.copy(image = java.io.File(base, it.image).absolutePath) else it })
        val file = Files.createTempFile("paper", ".pptx").toFile()
        PptxWriter.write(file, paper.title, md, "Professur")
        ZipFile(file).use { zip ->
            val n = slides.indexOf(part) + 1
            val xml = zip.getInputStream(zip.getEntry("ppt/slides/slide$n.xml")).readBytes().toString(Charsets.UTF_8)
            assertTrue("<p:fade/>" in xml && "<p:timing>" in xml)
        }
        assertEquals(slides.size, SlideDeck.parse(OfficeImport.read("paper.pptx", file.readBytes())!!.tabs.single().second).size)
        file.delete()
        pdf.delete()
        base.deleteRecursively()
    }

    @Test
    fun suggestionsToTapAndNotesWithoutModel() {
        val numbers = SlideDeck.Slide("Anteile", "- Gruppe A: 42 %\n- Gruppe B: 35 %\n- Gruppe C: 23 %")
        val s = io.github.thepytales.llmhub.docs.SlideLook.suggest(numbers)
        val chart = s.first { it.label == "Als Diagramm" }.slides.single()
        assertEquals(SlideDeck.Layout.CHART, chart.layout)
        assertEquals(listOf(42.0, 35.0, 23.0), SlideDeck.chartOf(chart.body)!!.series.single().values)
        assertEquals(listOf("Gruppe A", "Gruppe B", "Gruppe C"), SlideDeck.chartOf(chart.body)!!.labels)
        assertTrue(s.any { it.label == "Als Tabelle" && it.slides.single().layout == SlideDeck.Layout.TABLE })
        assertTrue(s.any { it.label == "Punkte nacheinander" && it.slides.single().build })
        val sides = io.github.thepytales.llmhub.docs.SlideLook.suggest(SlideDeck.Slide("Umstellung", "- Vorher: Papier\n- Ordner im Schrank\n- Nachher: digital\n- Suche in Sekunden"))
        val cmp = sides.first { it.label == "Als Vergleich" }.slides.single()
        assertEquals(SlideDeck.Layout.COMPARISON, cmp.layout)
        assertEquals("Vorher" to "- Papier\n- Ordner im Schrank", SlideDeck.columnHead(cmp.body))
        assertEquals("Nachher" to "- digital\n- Suche in Sekunden", SlideDeck.columnHead(cmp.right))
        val many = SlideDeck.Slide("Viel", (1..8).joinToString("\n") { "- Punkt $it" })
        assertEquals(2, io.github.thepytales.llmhub.docs.SlideLook.suggest(many).first { it.label == "Auf zwei Folien" }.slides.size)
        assertTrue(io.github.thepytales.llmhub.docs.SlideLook.suggest(deck[0]).isEmpty())

        val notes = io.github.thepytales.llmhub.docs.SlideLook.notes(deck, 1)
        assertTrue("„Ergebnisse“" in notes && "Zuerst: Erster Punkt." in notes && "Schließlich: Dritter Punkt." in notes && "Als Nächstes: „Ausblick“." in notes, notes)
    }

    @Test
    fun secondScreenPutsInkOnTheSameSpot() {
        val from = androidx.compose.ui.unit.IntSize(1600, 900)
        val to = androidx.compose.ui.unit.IntSize(1920, 1080)
        assertEquals(androidx.compose.ui.geometry.Offset(960f, 540f), io.github.thepytales.llmhub.ui.audiencePoint(androidx.compose.ui.geometry.Offset(800f, 450f), from, to))
        // A presenter area taller than 16:9: the slide is centred there, its top left corner stays the corner.
        val tall = androidx.compose.ui.unit.IntSize(1000, 900)
        assertEquals(androidx.compose.ui.geometry.Offset(0f, 0f), io.github.thepytales.llmhub.ui.audiencePoint(androidx.compose.ui.geometry.Offset(0f, 168.75f), tall, to))
    }
}
