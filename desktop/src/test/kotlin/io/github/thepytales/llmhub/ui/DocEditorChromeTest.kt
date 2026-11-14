package io.github.thepytales.llmhub.ui

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.rightClick
import androidx.compose.ui.test.performKeyInput
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
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * APD-15: the document editor's header like Google Docs – menus under the title, the Formatleiste across the width with
 * „⋯“ instead of cut-off buttons, *Datei* with what „⋮“ held, the tabs row only with several tabs, no floating KI field –
 * and Strg+Z undoing what was just typed (it used to reach the window's undo of saved states first).
 */
@OptIn(ExperimentalTestApi::class)
class DocEditorChromeTest {
    private val base = Files.createTempDirectory("adeu-apd15-chrome").toFile().also {
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

    /** The editor in a laptop window (1366 × 768 at 150 %). */
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

    private fun SkikoComposeUiTest.has(text: String) = onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun headerLikeGoogleDocs() = runSkikoComposeUiTest(Size(911 * 1.5f, 512 * 1.5f), Density(1.5f)) {
        open("# Antrag\n\nErster Absatz.\n")
        // Menus under the title, in the header (above the Formatleiste).
        listOf("Datei", "Bearbeiten", "Ansicht", "Einfügen", "Format", "Extras").forEach { assertTrue(has(it), "Menü $it") }
        val fileTop = onAllNodesWithText("Datei").onFirst().getBoundsInRoot().top
        val boldTop = onAllNodesWithContentDescription("Fett (Strg+B)").onFirst().getBoundsInRoot().top
        assertTrue(fileTop < boldTop, "Menüs über der Formatleiste")
        // Nothing cut off in a narrow window: the rest of the Formatleiste opens from „⋯“.
        assertTrue(onAllNodesWithContentDescription("Weitere Werkzeuge").fetchSemanticsNodes().isNotEmpty(), "„⋯“ in der Formatleiste")
        // One tab: no tabs row; no floating KI field over the text.
        assertFalse(has("Tab 1"), "keine Reiterzeile bei einem Tab")
        assertFalse(has("Mit KI schreiben …"), "kein schwebendes KI-Feld")
        // The page gets the room: its text starts in the upper half of the window.
        val textTop = onAllNodesWithContentDescription("Dokumenttext").onFirst().getBoundsInRoot().top
        assertTrue(textTop.value < 256f, "Text beginnt bei $textTop")
        // Statusleiste.
        assertTrue(has("Seite 1 von 1"), "Statusleiste")
        // Datei holds what „⋮“ held.
        onAllNodesWithText("Datei").onFirst().performClick()
        settle()
        listOf("Umbenennen …", "Kopie anlegen", "Exportieren …", "Versionsverlauf", "Löschen").forEach { assertTrue(has(it), "Datei › $it") }
    }

    @Test
    fun undoTakesBackWhatWasJustTyped() = runSkikoComposeUiTest(Size(1300f, 900f), Density(1f)) {
        val id = open("# Antrag\n\nErster Absatz.\n")
        val field = onAllNodesWithContentDescription("Dokumenttext").onFirst()
        field.performClick()
        field.performTextInput("Neu ")
        settle(2)
        assertTrue(has("Neu ", substring = true), "getippt")
        // Right after typing (nothing saved yet) Strg+Z takes it back – it used to do nothing until the next save.
        field.performKeyInput { withKeyDown(Key.CtrlLeft) { pressKey(Key.Z) } }
        settle(10)
        val md = c.docs.get(id)!!.tabs.first().markdown
        assertFalse("Neu " in md, md)
        assertEquals(true, "Erster Absatz." in md, md)
    }

    @Test
    fun menusHideLikeGoogleDocs() = runSkikoComposeUiTest(Size(1300f, 900f), Density(1f)) {
        open("# Antrag\n\nErster Absatz.\n")
        val field = onAllNodesWithContentDescription("Dokumenttext").onFirst()
        field.performClick()
        // Strg+Umschalt+F hides title and menus; the Formatleiste stays, its arrow brings them back.
        field.performKeyInput { withKeyDown(Key.CtrlLeft) { withKeyDown(Key.ShiftLeft) { pressKey(Key.F) } } }
        settle()
        assertFalse(has("Datei"), "Menüs ausgeblendet")
        assertTrue(onAllNodesWithContentDescription("Fett (Strg+B)").fetchSemanticsNodes().isNotEmpty(), "Formatleiste bleibt")
        onAllNodesWithContentDescription("Menüs einblenden (Strg+Umschalt+F)").onFirst().performClick()
        settle()
        assertTrue(has("Datei"), "Menüs wieder da")
    }

    /** Ausrichtung with keys no app-wide shortcut uses (Strg+E stays the KI): Strg+Umschalt+E, Strg+R, Strg+Umschalt+J. */
    @Test
    fun alignmentKeysDoNotOverlapTheApp() = runSkikoComposeUiTest(Size(1300f, 900f), Density(1f)) {
        val id = open("# Antrag\n\nErster Absatz.\n")
        val field = onAllNodesWithContentDescription("Dokumenttext").onFirst()
        field.performClick()
        field.performKeyInput { withKeyDown(Key.CtrlLeft) { withKeyDown(Key.ShiftLeft) { pressKey(Key.E) } } }
        settle(10)
        assertTrue("<!--a:c" in c.docs.get(id)!!.tabs.first().markdown, c.docs.get(id)!!.tabs.first().markdown)
        field.performKeyInput { withKeyDown(Key.CtrlLeft) { pressKey(Key.R) } }
        settle(10)
        assertTrue("<!--a:r" in c.docs.get(id)!!.tabs.first().markdown, c.docs.get(id)!!.tabs.first().markdown)
    }

    /**
     * Rechtsklick-Menü (AP-125): the app's menu (not Compose's English one) with Ausschneiden … Alles auswählen; with a
     * display also Einfügen with formatting and Nur Text einfügen through the real clipboard.
     */
    @Test
    fun rightClickMenuLikeWord() = runSkikoComposeUiTest(Size(1300f, 900f), Density(1f)) {
        val id = open("# Antrag\n\nErster Absatz.\n")
        val field = onAllNodesWithContentDescription("Dokumenttext").onFirst()
        field.performMouseInput { rightClick(center) }
        settle()
        listOf("Ausschneiden", "Kopieren", "Einfügen", "Nur Text einfügen", "Kommentar", "Formatierung entfernen", "Alles auswählen")
            .forEach { assertTrue(has(it), "Menü: $it") }
        assertFalse(has("Paste") || has("Select All"), "kein zweites Menü von Compose")
        onAllNodesWithText("Kopieren").onFirst().assertIsNotEnabled()
        // Alles auswählen, and Kopieren is possible.
        onAllNodesWithText("Alles auswählen").onFirst().performClick()
        settle()
        field.performMouseInput { rightClick(center) }
        settle()
        onAllNodesWithText("Kopieren").onFirst().assertIsEnabled()
        onAllNodesWithText("Formatierung entfernen").onFirst().performClick()
        settle()
        if (java.awt.GraphicsEnvironment.isHeadless()) return@runSkikoComposeUiTest
        // Through the real clipboard: Nur Text einfügen drops the formatting, Einfügen keeps it.
        field.performClick()
        assertTrue(RichClipboard.copy(c, "<b>Fett</b>", "Fett"))
        field.performMouseInput { rightClick(center) }
        settle()
        onAllNodesWithText("Nur Text einfügen").onFirst().performClick()
        settle(10)
        val plain = c.docs.get(id)!!.tabs.first().markdown
        assertTrue("Fett" in plain && "**Fett**" !in plain, plain)
        field.performMouseInput { rightClick(center) }
        settle()
        // The entry of the menu (with its key), not the menu *Einfügen* of the header.
        onAllNodes(hasText("Einfügen") and hasText("Strg+V")).onFirst().performClick()
        settle(10)
        val rich = c.docs.get(id)!!.tabs.first().markdown
        assertTrue("**Fett**" in rich, rich)
    }

    private fun SkikoComposeUiTest.has(text: String, substring: Boolean) =
        onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty()
}
