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
