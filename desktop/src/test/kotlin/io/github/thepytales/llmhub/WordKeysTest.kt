package io.github.thepytales.llmhub

import androidx.compose.ui.text.TextRange
import io.github.thepytales.llmhub.docs.text.WordKeys
import io.github.thepytales.llmhub.ui.FlowSeg
import io.github.thepytales.llmhub.ui.FlowState
import io.github.thepytales.llmhub.ui.cycleCase
import io.github.thepytales.llmhub.ui.moveParagraphs
import io.github.thepytales.llmhub.ui.moveToDocumentEdge
import io.github.thepytales.llmhub.ui.moveByWord
import io.github.thepytales.llmhub.ui.spanParts
import io.github.thepytales.llmhub.ui.stepFontSize
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Tasten und Markieren wie Word (AP-125 c): word steps, sentences, Groß-/Kleinschreibung, font sizes, moving paragraphs. */
class WordKeysTest {
    @Test
    fun wordStepsLikeWordOnWindows() {
        val t = "Das ist, sagt’s Lora, gut.\nZweiter Absatz"
        // Strg+→: to the start of the next word; punctuation is a word of its own; the apostrophe stays in the word.
        val stops = generateSequence(0) { i -> WordKeys.nextWord(t, i).takeIf { it != i } }.toList()
        assertEquals(listOf("Das ", "ist", ", ", "sagt’s ", "Lora", ", ", "gut", ".", "\n", "Zweiter ", "Absatz"), stops.zipWithNext { a, b -> t.substring(a, b) })
        // Strg+←: the same stops backwards.
        val back = generateSequence(t.length) { i -> WordKeys.prevWord(t, i).takeIf { it != i } }.toList()
        assertEquals(stops.reversed(), back)
    }

    @Test
    fun sentenceAndWordAtTheCursor() {
        val t = "Erster Satz. Zweiter Satz! Dritter?\nNeu"
        assertEquals("Zweiter Satz! ", t.substring(WordKeys.sentenceAt(t, 16)))
        assertEquals("Erster Satz. ", t.substring(WordKeys.sentenceAt(t, 0)))
        assertEquals("Dritter?", t.substring(WordKeys.sentenceAt(t, 30)))
        assertEquals("Neu", t.substring(WordKeys.sentenceAt(t, t.length)))
        assertEquals("Zweiter", t.substring(WordKeys.wordAt(t, 15)))
    }

    @Test
    fun caseCyclesLikeShiftF3() {
        assertEquals("Hallo Welt", WordKeys.cycleCase("hallo welt"))
        assertEquals("HALLO WELT", WordKeys.cycleCase("Hallo Welt"))
        assertEquals("hallo welt", WordKeys.cycleCase("HALLO WELT"))
        assertEquals("STRAßE", WordKeys.cycleCase("Straße"))
        assertEquals("straße", WordKeys.cycleCase("STRAßE"))
        assertEquals("123", WordKeys.cycleCase("123"))
    }

    @Test
    fun fontSizeStepsLikeWord() {
        assertEquals(12, WordKeys.stepSize(11f, up = true))
        assertEquals(10, WordKeys.stepSize(11f, up = false))
        assertEquals(11, WordKeys.stepSize(10.5f, up = true))
        assertEquals(36, WordKeys.stepSize(28f, up = true))
        assertEquals(80, WordKeys.stepSize(72f, up = true))
        assertEquals(7, WordKeys.stepSize(8f, up = false))
        assertEquals(1, WordKeys.stepSize(1f, up = false))
    }

    private fun texts(s: FlowState) = s.segs.filterIsInstance<FlowSeg.Text>()

    @Test
    fun paragraphsMoveUpAndDownAndOverATable() {
        val s = FlowState("Eins\n\nZwei\n\nDrei\n\n| A | B |\n| --- | --- |\n| 1 | 2 |\n\nVier\n")
        val first = texts(s).first()
        s.active = first
        first.selection = TextRange(first.rich.text.indexOf("Drei") + 1)
        s.moveParagraphs(first, up = true)
        assertEquals("Eins\n\nDrei\n\nZwei", s.markdown().substringBefore("\n\n|"))
        assertEquals("Drei", first.rich.text.substring(first.selection.start - 1, first.selection.start + 3))
        // Down twice: past „Zwei“, then over the table into the text after it.
        s.moveParagraphs(first, up = false)
        s.moveParagraphs(first, up = false)
        val md = s.markdown()
        assertTrue(md.startsWith("Eins\n\nZwei\n\n| A"), md)
        assertTrue(md.trimEnd().endsWith("| 1 | 2 |\n\nDrei\n\nVier"), md)
        assertTrue(s.active!!.rich.text.startsWith("Drei"))
        // Rückgängig takes back one move at a time.
        s.undo()
        assertTrue(s.markdown().startsWith("Eins\n\nZwei\n\nDrei\n\n| A"), s.markdown())
    }

    @Test
    fun documentEdgeMarksOverAllFieldsAndCaseAndSize() {
        val s = FlowState("hallo welt\n\n| A | B |\n| --- | --- |\n| 1 | 2 |\n\nzweites stück\n")
        val (a, b) = texts(s)
        s.active = b
        b.selection = TextRange(3)
        // Strg+Umschalt+Pos1 in the last field: the selection reaches to the start of the document over the table.
        s.moveToDocumentEdge(b, end = false, extend = true)
        val parts = s.spanParts()!!
        assertEquals(3, parts.size, "Text, Tabelle, Text")
        // Umschalt+F3 over all of it: Erster Buchstabe groß.
        s.cycleCase(s.active!!)
        assertTrue(s.markdown().startsWith("Hallo Welt"), s.markdown())
        assertTrue("Zweites stück" in s.markdown(), s.markdown())
        // Strg+→ in a field without a selection moves by words.
        s.spanAnchor = null
        s.active = a
        a.selection = TextRange(0)
        s.moveByWord(a, forward = true, extend = true)
        assertEquals(TextRange(0, 6), a.selection)
        // Strg+Umschalt+>: the marked word one step bigger (from the size of the Formatvorlage, 11 pt → 12 pt).
        s.stepFontSize(a, up = true)
        assertTrue("font-size:12pt" in s.markdown(), s.markdown())
    }
}
