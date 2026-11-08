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
}
