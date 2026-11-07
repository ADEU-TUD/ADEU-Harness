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
import kotlin.test.Test
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
