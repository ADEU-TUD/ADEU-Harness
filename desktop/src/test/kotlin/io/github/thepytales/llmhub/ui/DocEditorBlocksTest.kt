package io.github.thepytales.llmhub.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.rightClick
import androidx.compose.ui.test.runSkikoComposeUiTest
import androidx.compose.ui.test.withKeyDown
import androidx.compose.ui.unit.Density
import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.data.AppSettings
import io.github.thepytales.llmhub.docs.DocBlocks
import io.github.thepytales.llmhub.docs.DocMarkup
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Bilder und Tabellen anfassen (AP-125 d) with real clicks, drags and keys: a picture is selected by a click, its
 * handles change the width, its bar the place and caption, Entf removes it; a table cell takes text, Tab goes on (in the
 * last cell a new row), the Rechtsklick-Menü adds rows, a column border changes the widths.
 */
@OptIn(ExperimentalTestApi::class)
class DocEditorBlocksTest {
    private val base = Files.createTempDirectory("adeu-ap125d-ui").toFile().also {
        if (System.getProperty("adeu.data").isNullOrBlank()) System.setProperty("adeu.data", File(it, "daten").absolutePath)
    }
    private val c by lazy { AppContainer(android.content.Context(File(base, "files").apply { mkdirs() }, File(base, "cache").apply { mkdirs() })) }
    private val owner = object : androidx.lifecycle.LifecycleOwner {
        val registry = androidx.lifecycle.LifecycleRegistry.createUnsafe(this).apply { currentState = androidx.lifecycle.Lifecycle.State.RESUMED }
        override val lifecycle: androidx.lifecycle.Lifecycle get() = registry
    }

    @AfterTest
    fun cleanUp() { base.deleteRecursively() }

    private fun SkikoComposeUiTest.settle(rounds: Int = 8) {
        repeat(rounds) { Thread.sleep(80); mainClock.advanceTimeBy(200) }
        waitForIdle()
    }

    private fun SkikoComposeUiTest.open(markdown: String): String {
        mainClock.autoAdvance = false
        val doc = DocsHandoff.open(c, "Antrag", markdown)
        setContent {
            androidx.compose.runtime.CompositionLocalProvider(androidx.lifecycle.compose.LocalLifecycleOwner provides owner) {
                LlmHubTheme(AppSettings()) { androidx.compose.material3.Surface { DocPane(c, doc.id, onClose = {}) } }
            }
        }
        settle(14)
        return doc.id
    }

    private fun md(id: String) = c.docs.get(id)!!.tabs.first().markdown
    private fun SkikoComposeUiTest.has(text: String) = onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    private fun picture(): File = File(base, "bild.png").also { f ->
        val bi = java.awt.image.BufferedImage(400, 200, java.awt.image.BufferedImage.TYPE_INT_RGB)
        val g = bi.createGraphics()
        g.paint = java.awt.GradientPaint(0f, 0f, java.awt.Color(0, 48, 93), 400f, 200f, java.awt.Color(0, 168, 220))
        g.fillRect(0, 0, 400, 200)
        g.dispose()
        javax.imageio.ImageIO.write(bi, "png", f)
    }

    @Test
    fun pictureWithHandlesBarAndCaption() = runSkikoComposeUiTest(Size(1300f, 900f), Density(1f)) {
        val src = picture().absolutePath
        val id = open("Erster Absatz.\n\n![Hörsaal](${src}){width=50%}\n\nLetzter Absatz.\n")
        // A click selects the picture: handles and the bar appear.
        onAllNodesWithContentDescription("Hörsaal").onFirst().performClick()
        settle()
        assertTrue(onAllNodesWithContentDescription("Größe ändern").fetchSemanticsNodes().size == 4, "vier Griffe")
        assertTrue(has("Bildunterschrift") && has("Alternativtext") && has("Ersetzen"), "Leiste am Bild")
        // Dragging the lower right handle 130 px to the right: a centred picture grows on both sides (+260 px of 1300·…).
        val before = DocMarkup.image(md(id).lines().first { it.startsWith("![") })!!.width!!
        onAllNodesWithContentDescription("Größe ändern")[3].performMouseInput {
            moveTo(center); press(); repeat(13) { moveBy(Offset(10f, 0f)) }; release()
        }
        settle(10)
        val after = DocMarkup.image(md(id).lines().first { it.startsWith("![") })!!.width
        assertNotNull(after)
        assertTrue(after > before + 10, "breiter: $before → $after")
        // Rechts in the bar.
        onAllNodesWithContentDescription("Bild rechts").onFirst().performClick()
        settle(10)
        assertTrue("align=right" in md(id), md(id))
        // The caption under the picture, edited in place.
        onAllNodesWithContentDescription("Bildunterschrift").onFirst().performClick()
        onAllNodesWithContentDescription("Bildunterschrift").onFirst().performKeyInput { pressKey(Key.MoveEnd) }
        onAllNodesWithContentDescription("Bildunterschrift").onFirst().performTextInput(" im Sommer")
        onAllNodesWithContentDescription("Bildunterschrift").onFirst().performKeyInput { pressKey(Key.Enter) }
        settle(10)
        assertTrue("![Hörsaal im Sommer]" in md(id), md(id))
        // Rechtsklick: the menu of the picture.
        onAllNodesWithContentDescription("Hörsaal im Sommer").onFirst().performMouseInput { rightClick(center) }
        settle()
        listOf("Bild ersetzen …", "Alternativtext …", "Breite 50 %", "Nach oben verschieben", "Bild löschen").forEach { assertTrue(has(it), "Menü: $it") }
        onAllNodesWithText("Bild löschen").onFirst().performClick()
        settle(10)
        assertFalse("![" in md(id), md(id))
        assertTrue("Erster Absatz." in md(id) && "Letzter Absatz." in md(id), md(id))
    }

    private fun androidx.compose.ui.test.SemanticsNodeInteraction.textLayout(): androidx.compose.ui.text.TextLayoutResult {
        val out = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult].action!!.invoke(out)
        return out.first()
    }

    /**
     * Textumbruch (AP-125 e): with „Text daneben“ the text after the picture runs on beside it – its lines next to the
     * picture end before it, below it they have the whole width; a click beside the picture lands in the text.
     */
    @Test
    fun textRunsBesideAWrappedPicture() = runSkikoComposeUiTest(Size(1300f, 900f), Density(1f)) {
        val src = picture().absolutePath
        val long = List(30) { "Ein Satz neben dem Bild, der weiterläuft." }.joinToString(" ")
        val id = open("![Hörsaal](${src}){width=40%}\n\n$long\n\nDanach.\n")
        onAllNodesWithContentDescription("Hörsaal").onFirst().performClick()
        settle()
        onAllNodesWithText("Text daneben").onFirst().performClick()
        settle(12)
        assertTrue("wrap=right" in md(id), md(id))
        val pic = onAllNodesWithContentDescription("Hörsaal").onFirst().getBoundsInRoot()
        // The text after the picture (the first field is the empty line before it).
        val field = onAllNodesWithContentDescription("Dokumenttext")[1]
        val box = field.getBoundsInRoot()
        val layout = field.textLayout()
        // The first line ends before the picture, a line below it reaches further.
        assertTrue((box.left.value + layout.getLineRight(0)) <= pic.left.value + 1f, "erste Zeile endet vor dem Bild: ${layout.getLineRight(0)} / ${pic.left}")
        val below = (0 until layout.lineCount).filter { box.top.value + layout.getLineTop(it) > pic.bottom.value + 20f }
        assertTrue(below.isNotEmpty(), "Zeilen unter dem Bild")
        assertTrue(below.maxOf { box.left.value + layout.getLineRight(it) } > pic.left.value + 20f, "unter dem Bild volle Breite")
        // A click into the text beside the picture puts the cursor there (the picture does not take the click).
        field.performMouseInput { click(androidx.compose.ui.geometry.Offset(4f, 6f)) }
        settle(2)
        field.performTextInput("Neu: ")
        settle(10)
        assertTrue("Neu: Ein Satz" in md(id), md(id))
        // Left: the lines beside it start after the picture.
        onAllNodesWithContentDescription("Hörsaal").onFirst().performClick()
        settle()
        onAllNodesWithContentDescription("Bild links").onFirst().performClick()
        settle(12)
        assertTrue("wrap=left" in md(id), md(id))
        val l2 = onAllNodesWithContentDescription("Dokumenttext")[1].textLayout()
        val pic2 = onAllNodesWithContentDescription("Hörsaal").onFirst().getBoundsInRoot()
        assertTrue(box.left.value + l2.getLineLeft(0) >= pic2.right.value - 1f, "erste Zeile beginnt nach dem Bild")
    }

    @Test
    fun tableAsAGridWithTabMenuAndColumnWidths() = runSkikoComposeUiTest(Size(1300f, 900f), Density(1f)) {
        val id = open("Vorher.\n\n| Raum | Plätze |\n| --- | --- |\n| HSZ 02 | 400 |\n\nNachher.\n")
        // Typing in a cell, Tab to the next one, Tab in the last cell adds a row.
        val cell = onAllNodesWithContentDescription("Zelle 2, 2").onFirst()
        cell.performClick()
        cell.performKeyInput { pressKey(Key.MoveEnd) }
        cell.performTextInput("0")
        settle(4)
        cell.performKeyInput { pressKey(Key.Tab) }
        settle(6)
        onAllNodesWithContentDescription("Zelle 3, 1").onFirst().performTextInput("POT 81")
        settle(6)
        assertTrue("| HSZ 02 | 4000 |" in md(id), md(id))
        assertTrue("| POT 81 |  |" in md(id) || "| POT 81 | |" in md(id), md(id))
        // Rechtsklick in a cell: rows and columns.
        onAllNodesWithContentDescription("Zelle 1, 1").onFirst().performMouseInput { rightClick(center) }
        settle()
        listOf("Zeile unterhalb einfügen", "Spalte rechts einfügen", "Spalte löschen", "Tabelle löschen").forEach { assertTrue(has(it), "Menü: $it") }
        onAllNodesWithText("Spalte rechts einfügen").onFirst().performClick()
        settle(10)
        assertEquals(3, DocBlocks.tableRows(md(id).substringAfter("Vorher.").substringBefore("Nachher.")).first().size, md(id))
        // Dragging the first column border to the left: the first column gets narrower.
        onAllNodesWithContentDescription("Spaltenbreite ändern").onFirst().performMouseInput {
            moveTo(center); press(); repeat(10) { moveBy(Offset(-10f, 0f)) }; release()
        }
        settle(10)
        val w = DocBlocks.tableWidths(md(id).substringAfter("Vorher.").substringBefore("Nachher."))
        assertNotNull(w, md(id))
        assertTrue(w[0] < w[1], "$w")
        // Strg+B in a cell makes the word bold.
        val raum = onAllNodesWithContentDescription("Zelle 1, 1").onFirst()
        raum.performClick()
        raum.performKeyInput { withKeyDown(Key.CtrlLeft) { pressKey(Key.B) } }
        settle(10)
        assertTrue("**Raum**" in md(id), md(id))
    }
}
