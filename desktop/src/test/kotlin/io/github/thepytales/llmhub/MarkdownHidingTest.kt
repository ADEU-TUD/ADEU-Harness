package io.github.thepytales.llmhub

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import io.github.thepytales.llmhub.docs.SlideDeck
import io.github.thepytales.llmhub.ui.MarkdownHiding
import io.github.thepytales.llmhub.ui.continueList
import io.github.thepytales.llmhub.ui.smartEdit
import io.github.thepytales.llmhub.ui.toggleBullets
import io.github.thepytales.llmhub.ui.toggleMarks
import kotlin.test.Test
import kotlin.test.assertEquals

/** Editing documents and slides without visible Markdown marks. */
class MarkdownHidingTest {
    private fun shown(md: String) = MarkdownHiding.transform(md, Color.Blue, Color.Gray).text.text

    @Test
    fun hidesMarksAndShowsBullets() {
        assertEquals("Überschrift", shown("## Überschrift"))
        assertEquals("Ein fetter Satz mit Link.", shown("Ein **fetter** Satz mit [Link](https://tu-dresden.de)."))
        assertEquals("• Punkt eins\n  • eingerückt", shown("- Punkt eins\n  - eingerückt"))
        assertEquals("☐ offen\n☑ erledigt", shown("- [ ] offen\n- [x] erledigt"))
        assertEquals("1. Erstens", shown("1. Erstens"))
        assertEquals("Zitat", shown("> Zitat"))
        assertEquals("Hinweis", shown("> [!NOTE]\n> Hinweis"))
        assertEquals("val x = 1", shown("```kotlin\nval x = 1\n```"))
    }

    @Test
    fun offsetsMapBothWays() {
        val t = MarkdownHiding.transform("## Titel **fett**", Color.Blue, Color.Gray)
        assertEquals("Titel fett", t.text.text)
        // The cursor before "T" is after the hidden "## ".
        assertEquals(3, t.offsetMapping.transformedToOriginal(0))
        assertEquals(0, t.offsetMapping.originalToTransformed(3))
        assertEquals(6, t.offsetMapping.originalToTransformed(11)) // "f" of fett
        assertEquals(17, t.offsetMapping.transformedToOriginal(10)) // end
    }

    @Test
    fun backspaceAtStartOfHeadingRemovesTheFormatting() {
        val old = TextFieldValue("## Titel", TextRange(3))
        // Backspace removes the hidden space before "T".
        val next = smartEdit(old, TextFieldValue("##Titel", TextRange(2)))
        assertEquals("Titel", next.text)
        assertEquals(0, next.selection.start)
    }

    @Test
    fun backspaceInListJoinsWithItemAbove() {
        val old = TextFieldValue("- eins\n- zwei", TextRange(9))
        val next = smartEdit(old, TextFieldValue("- eins\n-zwei", TextRange(8)))
        assertEquals("- eins zwei", next.text)
    }

    @Test
    fun backspaceNextToHiddenBoldMarksDeletesTheVisibleLetter() {
        // Cursor after the closing "**" of "**fett**": Backspace would only hit a hidden "*".
        val old = TextFieldValue("**fett** x", TextRange(8))
        val next = smartEdit(old, TextFieldValue("**fett* x", TextRange(7)))
        assertEquals("**fet** x", next.text)
    }

    @Test
    fun enterContinuesAndEndsLists() {
        val v = TextFieldValue("- eins", TextRange(6))
        assertEquals("- eins\n- ", smartEdit(v, TextFieldValue("- eins\n", TextRange(7))).text)
        assertEquals("1. a\n2. ", continueList(TextFieldValue("1. a", TextRange(4)))!!.text)
        assertEquals("- [x] a\n- [ ] ", continueList(TextFieldValue("- [x] a", TextRange(7)))!!.text)
        // Enter on an empty item ends the list.
        assertEquals("- eins\n", continueList(TextFieldValue("- eins\n- ", TextRange(9)))!!.text)
        assertEquals(null, continueList(TextFieldValue("Absatz", TextRange(6))))
    }

    @Test
    fun formattingButtons() {
        assertEquals("ein **Wort** hier", toggleMarks(TextFieldValue("ein Wort hier", TextRange(5)), "**").text)
        assertEquals("ein Wort hier", toggleMarks(TextFieldValue("ein **Wort** hier", TextRange(6, 10)), "**").text)
        assertEquals("- a\n- b", toggleBullets(TextFieldValue("a\nb", TextRange(0, 3))).text)
        assertEquals("a\nb", toggleBullets(TextFieldValue("- a\n- b", TextRange(0, 7))).text)
    }

    @Test
    fun titleSlideKeepsSubtitleAndDetails() {
        val md = SlideDeck.markdown(listOf(SlideDeck.Slide("Bienen", "Eine Einführung\nVortrag · Oktober 2026", layout = SlideDeck.Layout.TITLE)))
        assertEquals("# Bienen\n\n## Eine Einführung\n\nVortrag · Oktober 2026\n", md)
        val back = SlideDeck.parse(md).single()
        assertEquals(listOf("Eine Einführung", "Vortrag · Oktober 2026"), back.body.lines().filter { it.isNotBlank() })
    }
}
