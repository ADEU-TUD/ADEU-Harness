package io.github.thepytales.llmhub

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import io.github.thepytales.llmhub.data.AppSettings
import io.github.thepytales.llmhub.ui.DocPane
import io.github.thepytales.llmhub.ui.DocsHandoff
import io.github.thepytales.llmhub.ui.LlmHubTheme
import io.github.thepytales.llmhub.ui.LoraFileBrowser
import io.github.thepytales.llmhub.ui.LoraFileDialog
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO
import io.github.thepytales.llmhub.ui.deleteSpan
import io.github.thepytales.llmhub.ui.joinWithPrevious
import io.github.thepytales.llmhub.ui.selectAllPieces
import io.github.thepytales.llmhub.ui.spanParts
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The Fließtext-Editor (AP-44) and the Lora-Dateidialog (AP-42) render and react (pictures in build/flow-look). */
@OptIn(ExperimentalTestApi::class)
class FlowEditorUiTest {
    private val out = File("build/flow-look").apply { mkdirs() }

    private val owner = object : androidx.lifecycle.LifecycleOwner {
        val registry = androidx.lifecycle.LifecycleRegistry.createUnsafe(this).apply { currentState = androidx.lifecycle.Lifecycle.State.RESUMED }
        override val lifecycle: androidx.lifecycle.Lifecycle get() = registry
    }

    @Test
    fun editorShowsFormattedRunningText() = runSkikoComposeUiTest(Size(1300f, 900f), Density(1f)) {
        mainClock.autoAdvance = false
        val base = Files.createTempDirectory("adeu-flow-ui").toFile()
        val c = AppContainer(android.content.Context(File(base, "files").apply { mkdirs() }, File(base, "cache").apply { mkdirs() }))
        val doc = DocsHandoff.open(
            c, "Bericht",
            "# Bericht\n\nEin **fetter**, <u>unterstrichener</u> und <span style=\"color:#C00000\">roter</span> Satz.\n\n" +
                "<!--a:c-->Zentrierter Absatz.\n\n## Liste\n\n- eins\n- zwei\n  - tiefer\n\n1. erstens\n2. zweitens\n\n- [ ] offen\n- [x] erledigt\n\n" +
                "| A | B |\n| --- | --- |\n| 1 | 2 |\n\n> Ein Zitat\n",
        )
        setContent {
            androidx.compose.runtime.CompositionLocalProvider(androidx.lifecycle.compose.LocalLifecycleOwner provides owner) {
                LlmHubTheme(AppSettings()) {
                    androidx.compose.material3.Surface { DocPane(c, doc.id, onClose = {}) }
                }
            }
        }
        fun settle() { repeat(6) { Thread.sleep(100); mainClock.advanceTimeBy(300) }; waitForIdle() }
        settle()
        ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(out, "editor.png"))
        assertTrue(onAllNodesWithText("Einfügen").fetchSemanticsNodes().isNotEmpty(), "Menüs wie Docs")
        val fields = onAllNodesWithContentDescription("Dokumenttext")
        assertTrue(fields.fetchSemanticsNodes().size >= 2, "Fließtext vor und nach der Tabelle")
        // Typing goes into the running text and is saved as Markdown.
        fields[0].performClick()
        fields[0].performTextInput("Neu: ")
        repeat(10) { Thread.sleep(100); mainClock.advanceTimeBy(300) }
        waitForIdle()
        ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(out, "editor-typed.png"))
        val md = c.docs.get(doc.id)!!.tabs.first().markdown
        assertTrue("Neu: " in md, md)
        assertTrue("<u>unterstrichener</u>" in md && "<!--a:c-->" in md && "- [x] erledigt" in md, md)
    }

    /**
     * Seitenansicht of a document longer than one page at Windows scale 100 %: the label „Seite 2“ sits in the right
     * margin (20 mm = 75.6 px, wider than the 60 px it used to be measured into) – that crashed with
     * „maxWidth(-15) must be >= than minWidth(0)“ while Lora wrote a long document.
     */
    /**
     * Gliederung, Formatvorlagen-Satz, Kommentare am Rand, Suchen und Ersetzen und ein Seitenumbruch (AP-43 bis AP-46):
     * everything shows, and a search marks the hits.
     */
    @Test
    fun outlineStylesCommentsAndSearch() = runSkikoComposeUiTest(Size(1900f, 1000f), Density(1f)) {
        mainClock.autoAdvance = false
        val base = Files.createTempDirectory("adeu-flow-extras").toFile()
        val c = AppContainer(android.content.Context(File(base, "files").apply { mkdirs() }, File(base, "cache").apply { mkdirs() }))
        val src = "# Hausarbeit\n\n<!--a:c;s=subtitle-->Ein Untertitel\n\n## Einleitung\n\nDie Einleitung nennt die Frage.\n\n\\newpage\n\n" +
            "## Methode\n\n<!--a:j;z=1.5-->Die Methode ist eine Umfrage mit Frage und Antwort.\n\n<!--a:;s=lit-->Müller, A. (2020): Buch. Dresden.\n\n" +
            "<!-- vorlagen: wissenschaftlich -->\n"
        val doc = DocsHandoff.open(c, "Hausarbeit", src)
        val tab = c.docs.get(doc.id)!!.tabs.first().id
        c.docs.update(doc.id) { d -> d.copy(comments = listOf(io.github.thepytales.llmhub.docs.DocComment(tabId = tab, quote = "Umfrage", text = "Wie viele Personen?"))) }
        setContent {
            androidx.compose.runtime.CompositionLocalProvider(androidx.lifecycle.compose.LocalLifecycleOwner provides owner) {
                LlmHubTheme(AppSettings()) {
                    androidx.compose.material3.Surface { DocPane(c, doc.id, onClose = {}) }
                }
            }
        }
        fun settle() { repeat(6) { Thread.sleep(100); mainClock.advanceTimeBy(300) }; waitForIdle() }
        settle()
        ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(out, "editor-outline.png"))
        assertTrue(onAllNodesWithText("Gliederung").fetchSemanticsNodes().isNotEmpty(), "Gliederung links")
        assertTrue(onAllNodesWithText("Methode").fetchSemanticsNodes().isNotEmpty(), "Überschrift in der Gliederung")
        assertTrue(onAllNodesWithText("Wie viele Personen?").fetchSemanticsNodes().isNotEmpty(), "Kommentar am Rand")
        assertTrue(onAllNodesWithContentDescription("Löschen").fetchSemanticsNodes().isNotEmpty(), "Seitenumbruch mit Löschen")
        // Suchen und Ersetzen über das Menü.
        onAllNodesWithText("Bearbeiten")[0].performClick()
        settle()
        onAllNodesWithText("Suchen und ersetzen")[0].performClick()
        settle()
        onAllNodesWithText("Suchen")[0].performTextInput("Frage")
        settle()
        ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(out, "editor-search.png"))
        assertTrue(onAllNodesWithText("1 von 3").fetchSemanticsNodes().isNotEmpty(), "drei Treffer (auch in „Umfrage“)")
        // The setting line of the style set stays in the document and is not drawn.
        assertTrue(onAllNodesWithText("vorlagen", substring = true).fetchSemanticsNodes().isEmpty())
        assertTrue("<!-- vorlagen: wissenschaftlich -->" in c.docs.get(doc.id)!!.tabs.first().markdown)
    }

    @Test
    fun pageBreakLabelInWideMarginDoesNotCrash() = runSkikoComposeUiTest(Size(1300f, 1400f), Density(1f)) {
        mainClock.autoAdvance = false
        val base = Files.createTempDirectory("adeu-flow-pages").toFile()
        val c = AppContainer(android.content.Context(File(base, "files").apply { mkdirs() }, File(base, "cache").apply { mkdirs() }))
        val long = (1..60).joinToString("\n\n") { "Absatz $it: " + "Ein Satz, der die Seite füllt. ".repeat(6) }
        val doc = DocsHandoff.open(c, "Lang", "# Lang\n\n$long\n")
        setContent {
            androidx.compose.runtime.CompositionLocalProvider(androidx.lifecycle.compose.LocalLifecycleOwner provides owner) {
                LlmHubTheme(AppSettings()) {
                    androidx.compose.material3.Surface { DocPane(c, doc.id, onClose = {}) }
                }
            }
        }
        repeat(6) { Thread.sleep(100); mainClock.advanceTimeBy(300) }
        waitForIdle()
        ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(out, "editor-pages.png"))
        assertTrue(onAllNodesWithContentDescription("Dokumenttext").fetchSemanticsNodes().isNotEmpty(), "Editor da")
    }

    /**
     * Echte Seiten (AP-43): a long document in the Seitenansicht breaks into sheets with header, footer and page numbers;
     * no line is cut by a page end (picture in build/flow-look/editor-real-pages.png).
     */
    @Test
    fun realPagesWithHeaderAndFooter() = runSkikoComposeUiTest(Size(1300f, 2600f), Density(1f)) {
        mainClock.autoAdvance = false
        val base = Files.createTempDirectory("adeu-flow-real-pages").toFile()
        val c = AppContainer(android.content.Context(File(base, "files").apply { mkdirs() }, File(base, "cache").apply { mkdirs() }))
        val long = (1..40).joinToString("\n\n") { k -> (if (k % 7 == 0) "## Abschnitt $k\n\n" else "") + "Absatz $k: " + "Ein Satz, der die Seite füllt und umbricht. ".repeat(5) }
        val md = io.github.thepytales.llmhub.docs.DocMarkup.withPageSetup(
            "# Lang\n\n$long\n\n\\newpage\n\nNach dem Umbruch.",
            io.github.thepytales.llmhub.docs.DocMarkup.PageSetup(header = "Hausarbeit", footer = "TU Dresden", titlePage = true),
        )
        val doc = DocsHandoff.open(c, "Lang", md)
        setContent {
            androidx.compose.runtime.CompositionLocalProvider(androidx.lifecycle.compose.LocalLifecycleOwner provides owner) {
                LlmHubTheme(AppSettings()) {
                    androidx.compose.material3.Surface { DocPane(c, doc.id, onClose = {}) }
                }
            }
        }
        repeat(14) { Thread.sleep(100); mainClock.advanceTimeBy(300) }
        waitForIdle()
        ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(out, "editor-real-pages.png"))
        // No line of the running text crosses the end of a page's text area.
        val state = io.github.thepytales.llmhub.ui.FlowState.shown?.get()!!
        assertTrue(state.pageCount >= 5, "Seiten: ${state.pageCount}")
        val stride = state.pageText + state.pageBetween
        var checked = 0
        for (seg in state.segs.filterIsInstance<io.github.thepytales.llmhub.ui.FlowSeg.Text>()) {
            val layout = seg.layout!!
            val top = state.segTop[seg.id]!!
            val shown = layout.layoutInput.text.text
            for (l in 0 until layout.lineCount) {
                val start = layout.getLineStart(l)
                if (shown.getOrNull(start) == '\u2060' || shown.getOrNull(start) == '\n' || layout.getLineEnd(l) - start <= 1) continue
                val a = top + layout.getLineTop(l)
                val b = top + layout.getLineBottom(l)
                val page = (a / stride).toInt()
                val inPage = a - page * stride
                assertTrue(inPage >= -1f && inPage + (b - a) <= state.pageText + 1f, "Zeile ${shown.substring(start, minOf(start + 20, shown.length))} über dem Seitenende: y=$inPage")
                checked++
            }
        }
        assertTrue(checked > 100, "geprüfte Zeilen: $checked")
        assertTrue(onAllNodesWithContentDescription("Dokumenttext").fetchSemanticsNodes().isNotEmpty(), "Editor da")
    }

    /** Tabellen über Seiten und Abschnitte (AP-43/45): a long table breaks row by row, its header repeats; sections. */
    @Test
    fun tablesBreakOverPagesAndSectionsHaveTheirHeader() = runSkikoComposeUiTest(Size(1300f, 3200f), Density(1f)) {
        mainClock.autoAdvance = false
        val base = Files.createTempDirectory("adeu-flow-table-pages").toFile()
        val c = AppContainer(android.content.Context(File(base, "files").apply { mkdirs() }, File(base, "cache").apply { mkdirs() }))
        val rows = listOf(listOf("Nr.", "Messung", "Wert")) + (1..45).map { listOf("$it", "Probe $it", "${it * 3} mg") }
        val intro = (1..6).joinToString("\n\n") { "Absatz $it: " + "Text vor der Tabelle, der etwas Platz braucht. ".repeat(4) }
        val md = io.github.thepytales.llmhub.docs.DocMarkup.withPageSetup(
            "# Messreihe\n\n$intro\n\n" + io.github.thepytales.llmhub.docs.DocBlocks.table(rows) + "\n\nNach der Tabelle.\n\n" +
                io.github.thepytales.llmhub.docs.DocMarkup.sectionMarker(io.github.thepytales.llmhub.docs.DocMarkup.Section(header = "Anhang")) + "\n\nAnhang A.",
            io.github.thepytales.llmhub.docs.DocMarkup.PageSetup(header = "Bericht"),
        )
        val doc = DocsHandoff.open(c, "Messreihe", md)
        setContent {
            androidx.compose.runtime.CompositionLocalProvider(androidx.lifecycle.compose.LocalLifecycleOwner provides owner) {
                LlmHubTheme(AppSettings()) {
                    androidx.compose.material3.Surface { DocPane(c, doc.id, onClose = {}) }
                }
            }
        }
        repeat(16) { Thread.sleep(100); mainClock.advanceTimeBy(300) }
        waitForIdle()
        ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(out, "editor-table-pages.png"))
        val state = io.github.thepytales.llmhub.ui.FlowState.shown?.get()!!
        val table = state.segs.filterIsInstance<io.github.thepytales.llmhub.ui.FlowSeg.Raw>().first { it.isTable }
        assertEquals(rows.size, table.rowHeights.size, "alle Zeilen gemessen")
        assertTrue(table.rowPush.isNotEmpty(), "die Tabelle bricht zwischen Zeilen um: ${table.rowPush}")
        assertTrue(table.repeatAt.isNotEmpty() && table.repeatAt.all { it in table.rowPush.keys }, "Kopfzeile wiederholt: ${table.repeatAt}")
        assertTrue(onAllNodesWithContentDescription("Wiederholte Kopfzeile").fetchSemanticsNodes().isNotEmpty())
        // The section starts on its own page and shows its header there.
        assertEquals(1, state.sectionPages.size, "Abschnitt")
        assertEquals("Anhang", state.sectionPages[0].second.header)
        assertTrue(state.sectionPages[0].first in 2..state.pageCount, "Seite ${state.sectionPages[0].first} von ${state.pageCount}")
    }

    /** Tabstopps (AP-44): a tab reaches its stop – left ones start there, right ones end there. */
    @Test
    fun tabsReachTheirStops() = runSkikoComposeUiTest(Size(1900f, 900f), Density(1f)) {
        mainClock.autoAdvance = false
        val base = Files.createTempDirectory("adeu-flow-tabs").toFile()
        val c = AppContainer(android.content.Context(File(base, "files").apply { mkdirs() }, File(base, "cache").apply { mkdirs() }))
        val md = "# Tabs\n\n<!--a:;t=40l120r-->Name\tVorname\tDresden\n\nA\tB"
        val doc = DocsHandoff.open(c, "Tabs", md)
        setContent {
            androidx.compose.runtime.CompositionLocalProvider(androidx.lifecycle.compose.LocalLifecycleOwner provides owner) {
                LlmHubTheme(AppSettings()) {
                    androidx.compose.material3.Surface { DocPane(c, doc.id, onClose = {}) }
                }
            }
        }
        repeat(10) { Thread.sleep(100); mainClock.advanceTimeBy(300) }
        waitForIdle()
        ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(out, "editor-tabs.png"))
        val state = io.github.thepytales.llmhub.ui.FlowState.shown?.get()!!
        val seg = state.segs.filterIsInstance<io.github.thepytales.llmhub.ui.FlowSeg.Text>().first { "Vorname" in it.rich.text }
        val layout = seg.layout!!
        val t = seg.rich.text
        val mm = 3.7795f
        fun x(o: Int) = layout.getHorizontalPosition(seg.shown(o), true)
        assertEquals(40f * mm, x(t.indexOf("Vorname")), 3f, "linker Tabstopp bei 40 mm")
        assertEquals(120f * mm, x(t.indexOf("Dresden") + "Dresden".length), 3f, "rechter Tabstopp bei 120 mm")
        // Without own stops: the next multiple of 12,5 mm.
        assertEquals(12.5f * mm, x(t.indexOf("B")), 3f, "Standard-Tabstopp")
        assertTrue("Name\tVorname\tDresden" in state.markdown(), state.markdown())
    }

    /** Große Dokumente (AP-43 d): 200+ pages open, page and take typing without long waits (times in build/flow-look). */
    @Test
    fun bigDocumentStaysResponsive() = runSkikoComposeUiTest(Size(1300f, 1000f), Density(1f)) {
        mainClock.autoAdvance = false
        val base = Files.createTempDirectory("adeu-flow-big").toFile()
        val c = AppContainer(android.content.Context(File(base, "files").apply { mkdirs() }, File(base, "cache").apply { mkdirs() }))
        val para = "Ein Satz in einem langen Dokument, der sich wiederholt und die Seiten füllt. "
        val md = "# Großes Dokument\n\n" + (1..175).joinToString("\n\n") { k ->
            "## Kapitel $k\n\n" + (1..9).joinToString("\n\n") { j -> "Absatz $k.$j: " + para.repeat(4) }
        }
        val doc = DocsHandoff.open(c, "Groß", md)
        val t0 = System.nanoTime()
        setContent {
            androidx.compose.runtime.CompositionLocalProvider(androidx.lifecycle.compose.LocalLifecycleOwner provides owner) {
                LlmHubTheme(AppSettings()) {
                    androidx.compose.material3.Surface { DocPane(c, doc.id, onClose = {}) }
                }
            }
        }
        val state = io.github.thepytales.llmhub.ui.FlowState.shown?.get()
        var rounds = 0
        while (rounds++ < 80) {
            Thread.sleep(50); mainClock.advanceTimeBy(200); waitForIdle()
            val s = io.github.thepytales.llmhub.ui.FlowState.shown?.get() ?: continue
            if (s.pageCount >= 200) break
        }
        val open = (System.nanoTime() - t0) / 1_000_000
        val st = io.github.thepytales.llmhub.ui.FlowState.shown?.get()!!
        assertTrue(st.pageCount >= 200, "Seiten: ${st.pageCount}")
        // Typing in the middle of the document: one character at a time, each laid out and paged again.
        val fields = st.segs.filterIsInstance<io.github.thepytales.llmhub.ui.FlowSeg.Text>()
        val target = fields[fields.size / 2]
        val at = target.rich.text.indexOf("Absatz", target.rich.text.length / 2).coerceAtLeast(0)
        runOnIdle { st.active = target; target.selection = androidx.compose.ui.text.TextRange(at) }
        val times = mutableListOf<Long>()
        val phases = StringBuilder()
        repeat(8) {
            val k0 = System.nanoTime()
            runOnIdle { st.insertText("x") }
            val k1 = System.nanoTime()
            mainClock.advanceTimeBy(50)
            val k2 = System.nanoTime()
            waitForIdle()
            val k3 = System.nanoTime()
            phases.append("${(k1 - k0) / 1_000_000}/${(k2 - k1) / 1_000_000}/${(k3 - k2) / 1_000_000} ")
            times += (System.nanoTime() - k0) / 1_000_000
        }
        val typing = times.drop(2).average()
        File(out, "big-document.txt").writeText(
            "Seiten: ${st.pageCount}\nTextfelder: ${fields.size}\nZeichen: ${fields.sumOf { it.rich.text.length }}\n" +
                "Öffnen bis Seitenplan: $open ms\nTippen je Zeichen: ${"%.0f".format(typing)} ms (${times.joinToString()})\nPhasen (Einfügen/Uhr/Leerlauf): $phases\n",
        )
        println(File(out, "big-document.txt").readText())
        // Measured 1.0.57: about 80 ms per key (before the pieces and the resting page plan: 2,7–3,5 s).
        assertTrue(typing < 600, "Tippen je Zeichen: $typing ms")
        assertTrue(fields.size > 20, "in Stücke geteilt: ${fields.size}")
    }

    /** Markieren über Tabellen hinweg (AP-44): one selection over text, a table and text; format, copy, delete it. */
    @Test
    fun selectionReachesOverTablesAndPieces() = runSkikoComposeUiTest(Size(1300f, 1200f), Density(1f)) {
        mainClock.autoAdvance = false
        val base = Files.createTempDirectory("adeu-flow-span").toFile()
        val c = AppContainer(android.content.Context(File(base, "files").apply { mkdirs() }, File(base, "cache").apply { mkdirs() }))
        val md = "# Bericht\n\nVor der Tabelle steht Text.\n\n| A | B |\n| --- | --- |\n| 1 | 2 |\n\nNach der Tabelle geht es weiter.\n\nLetzter Absatz."
        val doc = DocsHandoff.open(c, "Bericht", md)
        setContent {
            androidx.compose.runtime.CompositionLocalProvider(androidx.lifecycle.compose.LocalLifecycleOwner provides owner) {
                LlmHubTheme(AppSettings()) {
                    androidx.compose.material3.Surface { DocPane(c, doc.id, onClose = {}) }
                }
            }
        }
        repeat(8) { Thread.sleep(100); mainClock.advanceTimeBy(300) }
        waitForIdle()
        val st = io.github.thepytales.llmhub.ui.FlowState.shown?.get()!!
        val texts = st.segs.filterIsInstance<io.github.thepytales.llmhub.ui.FlowSeg.Text>()
        assertEquals(2, texts.size)
        val (a, b) = texts
        // Strg+A over both fields: bold for all text, the table inside the selection.
        runOnIdle { st.active = a; assertTrue(st.selectAllPieces()) }
        waitForIdle()
        val parts = st.spanParts()!!
        assertEquals(3, parts.size, "Text, Tabelle, Text")
        assertTrue(parts[1].first is io.github.thepytales.llmhub.ui.FlowSeg.Raw && parts[1].second == null)
        runOnIdle { st.toggle({ it.bold }) { f, on -> f.copy(bold = on) } }
        assertTrue(a.rich.chars.withIndex().all { (i, f) -> a.rich.text[i] == '\n' || f.bold } && b.rich.chars.withIndex().all { (i, f) -> b.rich.text[i] == '\n' || f.bold }, "alles fett")
        repeat(3) { Thread.sleep(50); mainClock.advanceTimeBy(200) }
        waitForIdle()
        ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(out, "editor-span.png"))
        // From „Text.“ in the first field to „Nach der“ in the second: deleting takes the table with it.
        runOnIdle {
            st.spanAnchor = a to a.rich.text.indexOf("steht")
            st.active = b
            b.selection = androidx.compose.ui.text.TextRange(0, b.rich.text.indexOf("Tabelle geht"))
            assertTrue(st.deleteSpan())
        }
        waitForIdle()
        assertTrue(st.segs.none { it is io.github.thepytales.llmhub.ui.FlowSeg.Raw && it.isTable }, "Tabelle weg")
        val joined = st.segs.filterIsInstance<io.github.thepytales.llmhub.ui.FlowSeg.Text>().single()
        assertTrue(joined.rich.text.contains("Vor der Tabelle Tabelle geht es weiter."), joined.rich.text)
        assertEquals(null, st.spanAnchor)
        // Rückgängig brings the table back.
        runOnIdle { st.undo() }
        waitForIdle()
        assertTrue(st.segs.any { it is io.github.thepytales.llmhub.ui.FlowSeg.Raw && it.isTable })
    }

    /** Pieces of a long text (AP-43 d): Backspace at the start of a piece joins it with the one before. */
    @Test
    fun piecesOfALongTextJoinAndSplitInvisibly() {
        val long = (1..30).joinToString("\n\n") { k -> "## Kapitel $k\n\n" + "Text des Kapitels $k. ".repeat(80) }
        val st = io.github.thepytales.llmhub.ui.FlowState("# Lang\n\n$long")
        val texts = st.segs.filterIsInstance<io.github.thepytales.llmhub.ui.FlowSeg.Text>()
        assertTrue(texts.size > 5, "Stücke: ${texts.size}")
        // Every piece but the first starts at a chapter; the Markdown is the same as before.
        assertTrue(texts.drop(1).all { it.rich.paras.first().kind.heading }, "an Kapiteln geteilt")
        assertEquals(io.github.thepytales.llmhub.docs.DocBlocks.join(io.github.thepytales.llmhub.docs.DocBlocks.split("# Lang\n\n$long")), st.markdown())
        val second = texts[1]
        val before = texts[0].rich.text.length
        assertTrue(st.joinWithPrevious(second))
        assertEquals(texts.size - 1, st.segs.size)
        assertTrue(st.segs[0].let { it as io.github.thepytales.llmhub.ui.FlowSeg.Text }.rich.text.length > before)
        assertEquals(before, st.active!!.selection.start, "Cursor an der Naht")
        // A short document stays one field.
        assertEquals(1, io.github.thepytales.llmhub.ui.FlowState("# Kurz\n\nText.").segs.size)
    }

    /** Änderungen nachverfolgen im Fließtext (AP-44): inserted green, removed struck through, the bar to accept them. */
    @Test
    fun trackedChangesShowInTheRunningText() = runSkikoComposeUiTest(Size(1300f, 900f), Density(1f)) {
        mainClock.autoAdvance = false
        val base = Files.createTempDirectory("adeu-flow-track").toFile()
        val c = AppContainer(android.content.Context(File(base, "files").apply { mkdirs() }, File(base, "cache").apply { mkdirs() }))
        val doc = DocsHandoff.open(c, "Bericht", "# Bericht\n\nDer Antrag wurde heute gestellt und genehmigt.\n\nEin zweiter Absatz mit neuem Satz.\n")
        c.docs.update(doc.id) { d -> d.copy(trackBase = mapOf(d.tabs.first().id to "# Bericht\n\nDer Antrag wurde gestern gestellt.\n\nEin zweiter Absatz.\n")) }
        setContent {
            androidx.compose.runtime.CompositionLocalProvider(androidx.lifecycle.compose.LocalLifecycleOwner provides owner) {
                LlmHubTheme(AppSettings()) {
                    androidx.compose.material3.Surface { DocPane(c, doc.id, onClose = {}) }
                }
            }
        }
        repeat(8) { Thread.sleep(100); mainClock.advanceTimeBy(300) }
        waitForIdle()
        ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(out, "editor-tracking.png"))
        assertTrue(onAllNodesWithContentDescription("Dokumenttext").fetchSemanticsNodes().isNotEmpty(), "Fließtext statt Blockansicht")
        assertTrue(onAllNodesWithText("Änderung 1 von", substring = true).fetchSemanticsNodes().isNotEmpty(), "Leiste der Änderungen")
        onAllNodesWithText("Annehmen")[0].performClick()
        repeat(6) { Thread.sleep(100); mainClock.advanceTimeBy(300) }
        waitForIdle()
        val tracked = c.docs.get(doc.id)!!.trackBase!!.values.single()
        assertTrue("heute" in tracked && "gestern" !in tracked, tracked)
    }

    @Test
    fun fileDialogShowsPlaces() = runSkikoComposeUiTest(Size(940f, 620f), Density(1f)) {
        mainClock.autoAdvance = false
        val base = Files.createTempDirectory("adeu-dialog-ui").toFile()
        val c = AppContainer(android.content.Context(File(base, "files").apply { mkdirs() }, File(base, "cache").apply { mkdirs() }))
        val dir = File(base, "Ordner").apply { mkdirs() }
        File(dir, "Bericht.pdf").writeText("x")
        File(dir, "Notizen.txt").writeText("y")
        var picked: List<File>? = null
        // No remembered places from the real data folder in the test.
        LoraFileDialog.appLoaded = false
        setContent {
            LlmHubTheme(AppSettings()) {
                androidx.compose.material3.Surface {
                    LoraFileBrowser(c, LoraFileDialog.Request(LoraFileDialog.Mode.OPEN, "Datei öffnen", start = dir)) { picked = it }
                }
            }
        }
        repeat(6) { Thread.sleep(100); mainClock.advanceTimeBy(300) }
        waitForIdle()
        ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(out, "dialog.png"))
        assertTrue(onAllNodesWithText("Lora-Ordner").fetchSemanticsNodes().isNotEmpty(), "Seitenspalte")
        assertTrue(onAllNodesWithText("Dieser PC").fetchSemanticsNodes().isNotEmpty(), "Dieser PC")
        onAllNodesWithText("Bericht.pdf")[0].performClick()
        onAllNodesWithText("Öffnen")[0].performClick()
        repeat(4) { Thread.sleep(100); mainClock.advanceTimeBy(300) }
        waitForIdle()
        LoraFileDialog.appLoaded = true
        assertTrue(picked?.singleOrNull()?.name == "Bericht.pdf", picked.toString())
    }
}
