package io.github.thepytales.llmhub.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.rightClick
import androidx.compose.ui.test.runSkikoComposeUiTest
import androidx.compose.ui.test.withKeyDown
import androidx.compose.ui.unit.Density
import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.data.AppSettings
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.AfterTest
import kotlin.test.Test

/**
 * AP-125 (c, d): pictures for the comparison „vorher/nachher“ – a document with picture and table on Full HD at 125 %:
 * as it is, the picture clicked, a right click in the table, the Tastenübersicht (Strg+/ lives in the window, here the
 * menu *Format*). Pictures in build/ap125; the same file runs on the old code for the pictures „vorher“ (steps the old
 * code cannot do are left out there).
 */
@OptIn(ExperimentalTestApi::class)
class DocBlocksLookTest {
    private val out = File("build/ap125").apply { mkdirs() }
    private val base = java.nio.file.Files.createTempDirectory("adeu-ap125-look").toFile().also {
        if (System.getProperty("adeu.data").isNullOrBlank()) System.setProperty("adeu.data", File(it, "daten").absolutePath)
    }
    private val c by lazy { AppContainer(android.content.Context(File(base, "files").apply { mkdirs() }, File(base, "cache").apply { mkdirs() })) }
    private val owner = object : androidx.lifecycle.LifecycleOwner {
        val registry = androidx.lifecycle.LifecycleRegistry.createUnsafe(this).apply { currentState = androidx.lifecycle.Lifecycle.State.RESUMED }
        override val lifecycle: androidx.lifecycle.Lifecycle get() = registry
    }

    @AfterTest
    fun cleanUp() { base.deleteRecursively() }

    private fun SkikoComposeUiTest.settle(rounds: Int = 10) {
        repeat(rounds) { Thread.sleep(80); mainClock.advanceTimeBy(160) }
        waitForIdle()
    }

    private fun SkikoComposeUiTest.shot(name: String) =
        ImageIO.write(onAllNodes(isRoot()).onFirst().captureToImage().toAwtImage(), "png", File(out, "$name.png"))

    private fun picture(): File = File(base, "hoersaal.png").also { f ->
        val bi = java.awt.image.BufferedImage(800, 450, java.awt.image.BufferedImage.TYPE_INT_RGB)
        val g = bi.createGraphics()
        g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON)
        g.paint = java.awt.GradientPaint(0f, 0f, java.awt.Color(0, 48, 93), 800f, 450f, java.awt.Color(0, 168, 220))
        g.fillRect(0, 0, 800, 450)
        g.color = java.awt.Color(255, 255, 255, 150)
        for (row in 0 until 6) for (seat in 0 until 14) g.fillRoundRect(60 + seat * 50 + row * 6, 120 + row * 48, 34, 26, 8, 8)
        g.color = java.awt.Color(255, 255, 255, 220)
        g.fillRect(250, 40, 300, 56)
        g.dispose()
        ImageIO.write(bi, "png", f)
    }

    private fun sample(src: String) = """
        Die Vorlesung *Einführung in die Statistik* hat jedes Semester rund 400 Teilnehmende. Mit dem Vorhaben sollen Selbstlernmodule entstehen, die an die Vorlesung anschließen.

        ![Abb. 1: Der Hörsaal im HSZ](${src}){width=60%}

        Die Module werden in drei Phasen entwickelt und in OPAL bereitgestellt.

        | Phase | Zeitraum | Ergebnis |
        | --- | --- | --- |
        | Konzept | 04/2027 – 06/2027 | Lernziele und Aufbau |
        | Umsetzung | 07/2027 – 12/2027 | Zwölf Module in OPAL |
        | Erprobung | 01/2028 – 03/2028 | Auswertung mit Studierenden |

        Die Evaluation erfolgt gemeinsam mit dem Zentrum für interdisziplinäres Lernen und Lehren.
    """.trimIndent() + "\n"

    @Test
    fun picturesForTheComparison() = runSkikoComposeUiTest(Size(1920f, 1080f), Density(1.25f)) {
        mainClock.autoAdvance = false
        val doc = DocsHandoff.open(c, "Antrag Lehrinnovation", sample(picture().absolutePath))
        setContent {
            androidx.compose.runtime.CompositionLocalProvider(androidx.lifecycle.compose.LocalLifecycleOwner provides owner) {
                LlmHubTheme(AppSettings()) { androidx.compose.material3.Surface { DocPane(c, doc.id, onClose = {}) } }
            }
        }
        settle(16)
        shot("1-dokument")
        // The picture clicked (old code: a Markdown dialog; new: handles and the bar).
        onAllNodesWithContentDescription("Abb. 1: Der Hörsaal im HSZ").onFirst().performClick()
        settle()
        shot("2-bild-angeklickt")
        // Dragging a handle (only the new code has them): the width shown while dragging.
        val handles = onAllNodesWithContentDescription("Größe ändern")
        if (handles.fetchSemanticsNodes().size == 4) {
            handles[3].performMouseInput { moveTo(center); press(); repeat(8) { moveBy(Offset(-10f, 0f)) } }
            settle(4)
            shot("3-bild-ziehen")
            handles[3].performMouseInput { release() }
            settle()
            onAllNodesWithContentDescription("Abb. 1: Der Hörsaal im HSZ").onFirst().performMouseInput { rightClick(center) }
            settle()
            shot("4-bild-rechtsklick")
            onAllNodesWithContentDescription("Abb. 1: Der Hörsaal im HSZ").onFirst().performKeyInput { pressKey(Key.Escape) }
            settle()
        } else {
            // Old code: close the dialog again.
            onAllNodesWithText("Abbrechen").fetchSemanticsNodes().takeIf { it.isNotEmpty() }?.let { onAllNodesWithText("Abbrechen").onFirst().performClick() }
            settle()
        }
        // A right click into the table.
        onAllNodesWithText("Umsetzung").onFirst().performMouseInput { rightClick(center) }
        settle()
        shot("5-tabelle-rechtsklick")
        onAllNodesWithText("Umsetzung").onFirst().performKeyInput { pressKey(Key.Escape) }
        settle()
        // The table while a cell is in use.
        onAllNodesWithText("Konzept").onFirst().performClick()
        settle()
        shot("6-tabelle-zelle")
        // Menu Format (new: Schrift größer/kleiner, Groß-/Kleinschreibung with their keys).
        onAllNodesWithContentDescription("Dokumenttext").onFirst().performClick()
        onAllNodesWithContentDescription("Dokumenttext").onFirst().performKeyInput { withKeyDown(Key.CtrlLeft) { pressKey(Key.MoveHome) } }
        settle()
        onAllNodesWithText("Format").onFirst().performClick()
        settle()
        shot("7-menue-format")
    }

    /** Textumbruch (AP-125 e): a picture at the right edge with text beside it, and selected with its bar. */
    @Test
    fun wrapForTheComparison() = runSkikoComposeUiTest(Size(1920f, 1080f), Density(1.25f)) {
        mainClock.autoAdvance = false
        val text = """
            Die Vorlesung *Einführung in die Statistik* hat jedes Semester rund 400 Teilnehmende. Die Übungen sind überlaufen, und viele Studierende brechen das Modul im ersten Semester ab.

            ![Abb. 1: Der Hörsaal im HSZ](${picture().absolutePath}){width=40% wrap=right}

            Mit dem Vorhaben sollen Selbstlernmodule entstehen, die an die Vorlesung anschließen und in OPAL bereitstehen. Jedes Modul behandelt ein Thema der Vorlesung, erklärt es an Beispielen aus der Forschung der TU Dresden und endet mit Aufgaben, die sofort ausgewertet werden. So können sich die Studierenden im eigenen Tempo vorbereiten und sehen, wo sie noch Lücken haben.

            Die Module werden in drei Phasen entwickelt: Konzept, Umsetzung und Erprobung mit Studierenden. Die Evaluation erfolgt gemeinsam mit dem Zentrum für interdisziplinäres Lernen und Lehren, die Ergebnisse fließen in die zweite Fassung ein.

            Die Materialien stehen unter offener Lizenz und können von anderen Lehrenden übernommen werden.
        """.trimIndent() + "\n"
        val doc = DocsHandoff.open(c, "Antrag Lehrinnovation", text)
        setContent {
            androidx.compose.runtime.CompositionLocalProvider(androidx.lifecycle.compose.LocalLifecycleOwner provides owner) {
                LlmHubTheme(AppSettings()) { androidx.compose.material3.Surface { DocPane(c, doc.id, onClose = {}) } }
            }
        }
        settle(16)
        shot("10-textumbruch")
        onAllNodesWithContentDescription("Abb. 1: Der Hörsaal im HSZ").onFirst().performClick()
        settle()
        shot("11-textumbruch-gewaehlt")
    }

    /** The same document as Word file and PDF (built-in renderer) and as HTML for the browser's print (Edge/Chrome). */
    @Test
    fun exportsForTheComparison() {
        val md = """
            Die Vorlesung *Einführung in die Statistik* hat jedes Semester rund 400 Teilnehmende. Die Übungen sind überlaufen, und viele Studierende brechen das Modul im ersten Semester ab.

            ![Abb. 1: Der Hörsaal im HSZ](${picture().absolutePath}){width=40% wrap=right}

            Mit dem Vorhaben sollen Selbstlernmodule entstehen, die an die Vorlesung anschließen und in OPAL bereitstehen. Jedes Modul behandelt ein Thema der Vorlesung, erklärt es an Beispielen aus der Forschung der TU Dresden und endet mit Aufgaben, die sofort ausgewertet werden. So können sich die Studierenden im eigenen Tempo vorbereiten und sehen, wo sie noch Lücken haben.

            | Phase | Zeitraum | Ergebnis |
            | ---------- | ---------- | ------------------------------ |
            | Konzept | 04/2027 – 06/2027 | Lernziele und Aufbau |
            | Umsetzung | 07/2027 – 12/2027 | Zwölf Module in OPAL |

            Die Materialien stehen unter offener Lizenz und können von anderen Lehrenden übernommen werden.
        """.trimIndent() + "\n"
        io.github.thepytales.llmhub.docs.MarkdownDocx.write(File(out, "export.docx"), "Antrag", md)
        val html = io.github.thepytales.llmhub.docs.DocumentWriter.toHtml("Antrag", md, print = true)
        File(out, "export.html").writeText(html)
        runCatching { io.github.thepytales.llmhub.docs.HtmlPdf.renderBuiltIn(html, File(out, "export.pdf")) }
    }

    /** Blocksatz (AP-125 e): the same with justified paragraphs, in the editor, as Word file and as HTML for the PDF. */
    @Test
    fun justifiedWrapForTheComparison() = runSkikoComposeUiTest(Size(1920f, 1080f), Density(1.25f)) {
        mainClock.autoAdvance = false
        val text = """
            <!--a:j-->Die Vorlesung *Einführung in die Statistik* hat jedes Semester rund 400 Teilnehmende. Die Übungen sind überlaufen, und viele Studierende brechen das Modul im ersten Semester ab.

            ![Abb. 1: Der Hörsaal im HSZ](${picture().absolutePath}){width=40% wrap=right}

            <!--a:j-->Mit dem Vorhaben sollen Selbstlernmodule entstehen, die an die Vorlesung anschließen und in OPAL bereitstehen. Jedes Modul behandelt ein Thema der Vorlesung, erklärt es an Beispielen aus der Forschung der TU Dresden und endet mit Aufgaben, die sofort ausgewertet werden. So können sich die Studierenden im eigenen Tempo vorbereiten und sehen, wo sie noch Lücken haben.

            <!--a:j-->Die Module werden in drei Phasen entwickelt: Konzept, Umsetzung und Erprobung mit Studierenden. Die Evaluation erfolgt gemeinsam mit dem Zentrum für interdisziplinäres Lernen und Lehren, die Ergebnisse fließen in die zweite Fassung ein.
        """.trimIndent() + "\n"
        io.github.thepytales.llmhub.docs.MarkdownDocx.write(File(out, "blocksatz.docx"), "Antrag", text)
        File(out, "blocksatz.html").writeText(io.github.thepytales.llmhub.docs.DocumentWriter.toHtml("Antrag", text, print = true))
        val doc = DocsHandoff.open(c, "Antrag Lehrinnovation", text)
        setContent {
            androidx.compose.runtime.CompositionLocalProvider(androidx.lifecycle.compose.LocalLifecycleOwner provides owner) {
                LlmHubTheme(AppSettings()) { androidx.compose.material3.Surface { DocPane(c, doc.id, onClose = {}) } }
            }
        }
        settle(16)
        shot("12-blocksatz")
    }

    /** Strg+/ (the Tastenübersicht of the window) with the new part „Im Dokument (wie in Word)“. */
    @Test
    fun shortcutsOverview() = runSkikoComposeUiTest(Size(1920f, 1080f), Density(1.25f)) {
        setContent {
            LlmHubTheme(AppSettings()) {
                androidx.compose.material3.Surface(androidx.compose.ui.Modifier.fillMaxSize()) { ShortcutsDialog(onDismiss = {}) }
            }
        }
        waitForIdle()
        shot("8-tastenuebersicht")
        if (onAllNodesWithText("Im Dokument (wie in Word)").fetchSemanticsNodes().isNotEmpty()) {
            onAllNodesWithText("Strg+Klick").onFirst().performScrollTo()
            waitForIdle()
            shot("9-tastenuebersicht-dokument")
        }
    }
}
