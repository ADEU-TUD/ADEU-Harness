package io.github.thepytales.llmhub

import io.github.thepytales.llmhub.docs.DocBlocks
import io.github.thepytales.llmhub.docs.text.Align
import io.github.thepytales.llmhub.docs.text.CharFormat
import io.github.thepytales.llmhub.docs.text.ParaKind
import io.github.thepytales.llmhub.docs.text.RichText
import io.github.thepytales.llmhub.ui.inlineSegments
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Fließtext-Editor (AP-44) and Lora im Dokument (AP-47): the running text and its Markdown. */
class FlowEditorTest {
    private fun rich(md: String) = RichText.fromBlocks(DocBlocks.split(md).filter(RichText::isFlowBlock))
    private fun md(r: RichText) = DocBlocks.join(RichText.toBlocks(r))

    @Test
    fun markdownRoundTrip() {
        val src = "# Titel\n\nEin **fetter** und *kursiver* Satz mit [Link](https://tu-dresden.de).\n\n## Abschnitt\n\n- eins\n- zwei\n  - tiefer\n\n1. erstens\n2. zweitens\n\n- [ ] offen\n- [x] erledigt\n\n> Ein Zitat\n"
        val r = rich(src)
        assertEquals(ParaKind.H1, r.paras[0].kind)
        assertEquals("Titel", r.paraText(0))
        assertTrue(r.paras.any { it.kind == ParaKind.BULLET && it.indent == 1 })
        assertTrue(r.paras.any { it.kind == ParaKind.CHECK && it.checked })
        assertEquals(src, md(r))
    }

    @Test
    fun underlineColourHighlightAndAlignmentSurvive() {
        var r = rich("Hallo Welt\n")
        r = r.format(0 until 5) { it.copy(underline = true, color = "C00000") }
        r = r.format(6 until 10) { it.copy(highlight = "FFF59D", bold = true) }
        r = r.formatParas(0, 0) { it.copy(align = Align.CENTER) }
        val out = md(r)
        assertTrue("<u>" in out && "color:#C00000" in out && "background:#FFF59D" in out && "<!--a:c-->" in out, out)
        val back = rich(out)
        assertEquals(Align.CENTER, back.paras[0].align)
        assertEquals("Hallo Welt", back.text)
        assertTrue(back.chars[0].underline && back.chars[0].color == "C00000")
        assertTrue(back.chars[7].bold && back.chars[7].highlight == "FFF59D")
        // The exports read the same marks.
        val segs = inlineSegments(out.trim())
        assertTrue(segs.any { it.underline && it.color == "C00000" })
        assertTrue(segs.none { "<" in it.text }, segs.toString())
    }

    @Test
    fun typingKeepsFormatsAndSplitsParagraphs() {
        var r = rich("## Kopf\n\nText\n")
        r = r.format(r.starts[1] until r.text.length) { it.copy(bold = true) }
        // Typing at the end of the bold text stays bold.
        r = r.edited(r.text + "e", null)
        assertTrue(r.chars.last().bold)
        // Enter after a heading starts normal text.
        r = r.edited("Kopf\nX\nTexte", null)
        assertEquals(listOf(ParaKind.H2, ParaKind.NORMAL, ParaKind.NORMAL), r.paras.map { it.kind })
        // Enter in a list continues the list.
        var l = rich("- eins\n")
        l = l.edited("eins\nzwei", null)
        assertEquals(listOf(ParaKind.BULLET, ParaKind.BULLET), l.paras.map { it.kind })
        assertEquals("- eins\n- zwei\n", md(l))
    }

    @Test
    fun softLinesStayOneParagraph() {
        val src = "Max Muster  \nHauptstraße 1  \n01069 Dresden\n"
        val r = rich(src)
        assertEquals(3, r.paras.size)
        assertEquals(src, md(r))
    }

    @Test
    fun suggestionsAreAcceptedOneByOne() {
        val old = rich("Der Hund läuft schnell über die Wiese.\n")
        val s = RichText.suggest(old, "Der Hund rennt schnell über die grüne Wiese.", 1)
        assertEquals(2, s.suggestions.size)
        // Until accepted, the stored Markdown is the old text.
        assertEquals("Der Hund läuft schnell über die Wiese.\n", md(s))
        val first = s.settle(s.suggestions[0], accept = true)
        val both = first.settle(first.suggestions[0], accept = false)
        assertEquals("Der Hund rennt schnell über die Wiese.", both.text)
        assertTrue(both.suggestions.isEmpty())
    }

    @Test
    fun loraWritesAtTheCursorAndCanBeDiscarded() {
        val base = rich("Erster Absatz.\n")
        val piece = RichText.ofMarkdown("## Einleitung\n\nNeuer Text.") { it.copy(ai = true) }
        val withAi = base.insert(base.text.length, RichText("\n", listOf(CharFormat(ai = true)), listOf(base.paras[0], base.paras[0])).insert(1, piece))
        assertTrue(withAi.paras.any { it.kind == ParaKind.H2 })
        val kept = withAi.keepAi()
        assertFalse(kept.chars.any { it.ai })
        assertTrue("## Einleitung" in md(kept), md(kept))
        val dropped = withAi.removeWhere { withAi.chars[it].ai }
        assertEquals("Erster Absatz.", dropped.text)
    }

    @Test
    fun blocksOfTheirOwnStayOutside() {
        assertFalse(RichText.isFlowBlock("| a | b |\n| --- | --- |\n| 1 | 2 |"))
        assertFalse(RichText.isFlowBlock("\\newpage"))
        assertFalse(RichText.isFlowBlock("![Bild](Bilder/a.png)"))
        assertFalse(RichText.isFlowBlock("```\ncode\n```"))
        assertTrue(RichText.isFlowBlock("<u>unterstrichen</u> und mehr"))
        assertTrue(RichText.isFlowBlock("<!--a:c-->Zentriert"))
    }
}
