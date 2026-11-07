package io.github.thepytales.llmhub

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import io.github.thepytales.llmhub.docs.text.TrackDiff
import io.github.thepytales.llmhub.ui.FlowSeg
import io.github.thepytales.llmhub.ui.FlowState
import io.github.thepytales.llmhub.ui.PageBreakAt
import io.github.thepytales.llmhub.ui.PageSpacers
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Änderungen nachverfolgen im Fließtext (AP-44): Wortvergleich, Anzeige im Text, Annehmen und Ablehnen. */
class FlowTrackingTest {
    /** [base] with every change of [hunks] applied (taking the inserted text from [current]). */
    private fun apply(base: String, current: String, hunks: List<TrackDiff.Hunk>): String {
        val sb = StringBuilder()
        var pos = 0
        for (h in hunks) {
            val d = if (h.del.isEmpty()) h.del.first else h.del.first
            sb.append(base, pos, d)
            if (!h.ins.isEmpty()) sb.append(current, h.ins.first, h.ins.last + 1)
            pos = if (h.del.isEmpty()) d else h.del.last + 1
        }
        sb.append(base, pos, base.length)
        return sb.toString()
    }

    @Test
    fun wordDiffFindsTheChanges() {
        val base = "Der Hund läuft schnell über die Wiese."
        val now = "Der Hund rennt schnell über die große Wiese."
        val h = TrackDiff.hunks(base, now)
        assertEquals(2, h.size, "$h")
        assertEquals("läuft", base.substring(h[0].del.first, h[0].del.last + 1))
        assertEquals("rennt", now.substring(h[0].ins.first, h[0].ins.last + 1))
        assertTrue(h[1].del.isEmpty())
        assertEquals("große", now.substring(h[1].ins.first, h[1].ins.last + 1).trim())
        assertEquals(now, apply(base, now, h))
        assertTrue(TrackDiff.hunks(base, base).isEmpty())
        assertEquals(listOf(TrackDiff.Hunk(0 until 0, 0 until 5)), TrackDiff.hunks("", "Neues"))
    }

    @Test
    fun randomEditsRebuildTheText() {
        val rnd = Random(7)
        val words = listOf("alpha ", "beta ", "gamma ", "delta ", "eps ", "\n", "zeta ", "eta, ")
        repeat(200) {
            val a = List(rnd.nextInt(0, 40)) { words[rnd.nextInt(words.size)] }
            val b = a.toMutableList()
            repeat(rnd.nextInt(0, 8)) {
                when (rnd.nextInt(3)) {
                    0 -> if (b.isNotEmpty()) b.removeAt(rnd.nextInt(b.size))
                    1 -> b.add(rnd.nextInt(b.size + 1), words[rnd.nextInt(words.size)])
                    else -> if (b.isNotEmpty()) b[rnd.nextInt(b.size)] = words[rnd.nextInt(words.size)]
                }
            }
            val base = a.joinToString("")
            val now = b.joinToString("")
            val h = TrackDiff.hunks(base, now)
            assertEquals(now, apply(base, now, h), "base=$base now=$now $h")
            if (base == now) assertTrue(h.isEmpty())
        }
        // Long texts with a few changes stay quick (Myers: the cost grows with the changes, not the length).
        val long = (1..20000).joinToString(" ") { "wort$it" }
        val edited = long.replace("wort500 ", "neu ").replace("wort15000 ", "")
        val t0 = System.nanoTime()
        val h = TrackDiff.hunks(long, edited)
        assertTrue((System.nanoTime() - t0) / 1_000_000 < 1500)
        assertEquals(2, h.size)
    }

    @Test
    fun acceptMovesAChangeIntoTheBaseAndRejectTakesItBack() {
        val base = "# Bericht\n\nDer **Hund** läuft schnell.\n\n| A | B |\n| --- | --- |\n| 1 | 2 |\n\nZweiter Absatz bleibt.\n"
        val now = "# Bericht\n\nDer **Hund** rennt schnell.\n\n| A | B |\n| --- | --- |\n| 1 | 2 |\n\nZweiter Absatz bleibt. Neu dazu.\n"
        assertTrue(FlowState.trackable(base, now))
        assertFalse(FlowState.trackable(base, now.replace("| 1 | 2 |", "| 1 | 3 |")), "Tabelle geändert: Blockansicht")
        val state = FlowState(now)
        assertTrue(state.track(base))
        val list = state.tracked()
        assertEquals(2, list.size, "${list.map { it.second }}")
        // The removed word shows in the text (struck through) at its place.
        val first = list[0].first
        assertEquals(listOf(first.rich.text.indexOf("rennt") to "läuft"), first.deleted)
        // Annehmen: the base takes the change, formats stay.
        val newBase = state.acceptTracked(list[0].first, list[0].second)!!
        assertTrue("Der **Hund** rennt schnell." in newBase, newBase)
        assertTrue("Neu dazu" !in newBase, "die andere Änderung bleibt offen")
        assertTrue(state.track(newBase))
        assertEquals(1, state.tracked().size)
        // Ablehnen: the text of then comes back.
        val (t, h) = state.tracked()[0]
        state.rejectTracked(t, h)
        assertTrue(state.tracked().isEmpty())
        assertTrue("Zweiter Absatz bleibt.\n" in state.markdown() && "Neu dazu" !in state.markdown(), state.markdown())
        // Alle ablehnen gives the base back.
        val s2 = FlowState(now)
        s2.track(base)
        s2.rejectAllTracked()
        assertEquals(FlowState(base).markdown(), s2.markdown())
        // Tracking off: nothing to show.
        s2.track(null)
        assertTrue(s2.segs.filterIsInstance<FlowSeg.Text>().all { it.base == null && it.deleted.isEmpty() })
    }

    @Test
    fun removedTextIsShownInsideItsParagraph() {
        val text = "Erster Absatz\nZweiter"
        val src = AnnotatedString(
            text,
            paragraphStyles = listOf(AnnotatedString.Range(ParagraphStyle(), 0, 14), AnnotatedString.Range(ParagraphStyle(), 14, text.length)),
        )
        val deleted = listOf(14 to "Alter ", 7 to "neuer ", text.length to " Ende")
        val t = PageSpacers(listOf(PageBreakAt.at(text, 14, 30f)), 1f, 1f, deleted, SpanStyle()).filter(src)
        val shown = t.text.text
        assertTrue(shown.startsWith("Erster neuer Absatz\n"), shown)
        // The removed text at the start of the second paragraph belongs to it, after the room of the page break.
        val second = t.text.paragraphStyles.last()
        assertTrue(shown.substring(second.start, second.end).startsWith("Alter Zweiter"), shown.substring(second.start, second.end))
        assertTrue(shown.endsWith("Zweiter Ende"))
        // The cursor never lands in removed text.
        for (o in 0..text.length) assertEquals(o, t.offsetMapping.transformedToOriginal(t.offsetMapping.originalToTransformed(o)))
        val ps = t.text.paragraphStyles.sortedBy { it.start }
        for (k in 1 until ps.size) assertTrue(ps[k].start >= ps[k - 1].end)
    }
}
