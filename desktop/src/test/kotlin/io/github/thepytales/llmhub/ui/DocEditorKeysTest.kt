package io.github.thepytales.llmhub.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.click
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runSkikoComposeUiTest
import androidx.compose.ui.test.withKeyDown
import androidx.compose.ui.unit.Density
import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.data.AppSettings
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tasten und Markieren wie Word (AP-125 c), with real key presses and clicks in the editor: Strg+Pfeile, Strg+Pos1,
 * Umschalt+F3, Alt+Umschalt+↑/↓, Strg+Umschalt+>, Strg+Enter, Strg+Rück, Format kopieren/einfügen, Doppel- und
 * Dreifachklick, Strg+Klick.
 */
@OptIn(ExperimentalTestApi::class)
class DocEditorKeysTest {
    private val base = Files.createTempDirectory("adeu-ap125c-keys").toFile().also {
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
        settle(12)
        return doc.id
    }

    private fun md(id: String) = c.docs.get(id)!!.tabs.first().markdown

    @Test
    fun wordKeysMoveMarkAndChange() = runSkikoComposeUiTest(Size(1300f, 900f), Density(1f)) {
        val id = open("Erster Absatz.\n\nzweiter absatz hier.\n\nDritter Absatz.\n")
        val field = onAllNodesWithContentDescription("Dokumenttext").onFirst()
        field.performMouseInput { click(Offset(4f, 8f)) }
        settle(2)
        // Strg+↓ to the start of the second paragraph, Strg+Umschalt+→ twice marks „zweiter absatz “, Umschalt+F3 makes
        // the first letters capitals.
        field.performKeyInput { withKeyDown(Key.CtrlLeft) { pressKey(Key.DirectionDown) } }
        field.performKeyInput { withKeyDown(Key.CtrlLeft) { withKeyDown(Key.ShiftLeft) { pressKey(Key.DirectionRight); pressKey(Key.DirectionRight) } } }
        field.performKeyInput { withKeyDown(Key.ShiftLeft) { pressKey(Key.F3) } }
        settle(10)
        assertTrue("Zweiter Absatz hier." in md(id), md(id))
        // Strg+Umschalt+> (here the key with „.“, as on an English keyboard) makes the marked words bigger.
        field.performKeyInput { withKeyDown(Key.CtrlLeft) { withKeyDown(Key.ShiftLeft) { pressKey(Key.Period) } } }
        settle(10)
        assertTrue("font-size:12pt" in md(id), md(id))
        // Strg+< on a German keyboard (the key beside Umschalt): smaller again – the size of the Formatvorlage, no own size.
        field.performKeyInput { withKeyDown(Key.CtrlLeft) { pressKey(Key(java.awt.event.KeyEvent.VK_LESS)) } }
        settle(10)
        assertFalse("font-size" in md(id), md(id))
        field.performKeyInput { withKeyDown(Key.CtrlLeft) { withKeyDown(Key.ShiftLeft) { pressKey(Key(java.awt.event.KeyEvent.VK_LESS)) } } }
        settle(10)
        assertTrue("font-size:12pt" in md(id), md(id))
        // Alt+Umschalt+↓ moves the paragraph below the third one.
        field.performKeyInput { withKeyDown(Key.AltLeft) { withKeyDown(Key.ShiftLeft) { pressKey(Key.DirectionDown) } } }
        settle(10)
        assertTrue(md(id).indexOf("Dritter Absatz.") < md(id).indexOf("hier."), md(id))
        // Strg+Pos1, then Strg+Rück at the end of the first word deletes it.
        field.performKeyInput { withKeyDown(Key.CtrlLeft) { pressKey(Key.MoveHome); pressKey(Key.DirectionRight) } }
        field.performKeyInput { withKeyDown(Key.CtrlLeft) { pressKey(Key.Backspace) } }
        settle(10)
        assertTrue(md(id).startsWith("Absatz."), md(id))
        // Strg+Enter: a page break at the cursor, and typing goes on in the text after it.
        field.performKeyInput { withKeyDown(Key.CtrlLeft) { pressKey(Key.Enter) } }
        settle(10)
        assertTrue("\\newpage" in md(id), md(id))
        onAllNodesWithContentDescription("Dokumenttext")[1].performTextInput("Nach dem Umbruch ")
        settle(10)
        assertTrue("\\newpage\n\nNach dem Umbruch Absatz." in md(id), md(id))
    }

    @Test
    fun formatCopyAndPasteLikeWord() = runSkikoComposeUiTest(Size(1300f, 900f), Density(1f)) {
        val id = open("**Fett** und normal.\n")
        val field = onAllNodesWithContentDescription("Dokumenttext").onFirst()
        field.performMouseInput { click(Offset(4f, 8f)) }
        settle(2)
        // Strg+Umschalt+C in the bold word, then mark „normal“ (Strg+Ende, Strg+Umschalt+← twice) and Strg+Alt+V.
        field.performKeyInput { withKeyDown(Key.CtrlLeft) { pressKey(Key.DirectionRight) } }
        field.performKeyInput { withKeyDown(Key.CtrlLeft) { pressKey(Key.DirectionLeft) } }
        field.performKeyInput { withKeyDown(Key.CtrlLeft) { withKeyDown(Key.ShiftLeft) { pressKey(Key.DirectionRight) } } }
        field.performKeyInput { withKeyDown(Key.CtrlLeft) { withKeyDown(Key.ShiftLeft) { pressKey(Key.C) } } }
        field.performKeyInput { withKeyDown(Key.CtrlLeft) { pressKey(Key.MoveEnd) } }
        field.performKeyInput { withKeyDown(Key.CtrlLeft) { withKeyDown(Key.ShiftLeft) { pressKey(Key.DirectionLeft); pressKey(Key.DirectionLeft) } } }
        field.performKeyInput { withKeyDown(Key.CtrlLeft) { withKeyDown(Key.AltLeft) { pressKey(Key.V) } } }
        settle(10)
        assertTrue("**normal.**" in md(id) || "**normal**" in md(id), md(id))
    }

    @Test
    fun clicksMarkWordSentenceAndParagraph() = runSkikoComposeUiTest(Size(1300f, 900f), Density(1f)) {
        val id = open("Alpha Beta. Gamma Delta.\n\nZweiter Absatz.\n")
        val field = onAllNodesWithContentDescription("Dokumenttext").onFirst()
        val at = Offset(6f, 8f)
        // Doppelklick: the word.
        field.performMouseInput { doubleClick(at) }
        settle(2)
        field.performTextInput("Eins")
        settle(10)
        assertTrue(md(id).startsWith("Eins Beta."), md(id))
        // Strg+Klick: the sentence (with the space after it).
        field.performKeyInput { keyDown(Key.CtrlLeft) }
        field.performMouseInput { click(at) }
        field.performKeyInput { keyUp(Key.CtrlLeft) }
        settle(2)
        field.performTextInput("Neu. ")
        settle(10)
        assertTrue(md(id).startsWith("Neu. Gamma Delta."), md(id))
        // Dreifachklick: the paragraph.
        field.performMouseInput { click(at); click(at); click(at) }
        settle(2)
        field.performTextInput("Ganz neu.")
        settle(10)
        assertTrue(md(id).startsWith("Ganz neu."), md(id))
        assertFalse("Gamma" in md(id), md(id))
        assertTrue("Zweiter Absatz." in md(id), md(id))
    }
}
