package io.github.thepytales.llmhub

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runSkikoComposeUiTest
import androidx.compose.ui.test.withKeyDown
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.thepytales.llmhub.data.AppSettings
import io.github.thepytales.llmhub.ui.DocPane
import io.github.thepytales.llmhub.ui.DocsHandoff
import io.github.thepytales.llmhub.ui.LlmHubTheme
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Keine Überschneidungen (AP-125, Hauptnutzer 14.11.2026): the window's keys as Main.kt wires them – [AppShortcuts.early]
 * before the focused element, [AppShortcuts.late] after it. In the document Strg+B makes bold (it used to toggle the
 * Seitenleiste, the window took it first); outside a text Strg+B is the Seitenleiste again; Strg+K is the Befehlssuche
 * everywhere, also in the document.
 */
@OptIn(ExperimentalTestApi::class)
class AppShortcutsTest {
    private val base = Files.createTempDirectory("adeu-shortcuts").toFile().also {
        if (System.getProperty("adeu.data").isNullOrBlank()) System.setProperty("adeu.data", File(it, "daten").absolutePath)
    }
    private val c by lazy { AppContainer(android.content.Context(File(base, "files").apply { mkdirs() }, File(base, "cache").apply { mkdirs() })) }
    private val owner = object : androidx.lifecycle.LifecycleOwner {
        val registry = androidx.lifecycle.LifecycleRegistry.createUnsafe(this).apply { currentState = androidx.lifecycle.Lifecycle.State.RESUMED }
        override val lifecycle: androidx.lifecycle.Lifecycle get() = registry
    }

    @AfterTest
    fun cleanUp() { base.deleteRecursively() }

    @Test
    fun textComesFirstForBoldTheAppKeepsItsKeys() = runSkikoComposeUiTest(Size(1300f, 900f), Density(1f)) {
        mainClock.autoAdvance = false
        var route: String? = null
        val doc = DocsHandoff.open(c, "Antrag", "# Antrag\n\nErster Absatz.\n")
        lateinit var other: FocusRequester
        setContent {
            androidx.compose.runtime.CompositionLocalProvider(androidx.lifecycle.compose.LocalLifecycleOwner provides owner) {
                LlmHubTheme(AppSettings()) {
                    // Like the window in Main.kt: early keys before the focused element, late keys after it.
                    Column(
                        Modifier
                            .onPreviewKeyEvent { e -> AppShortcuts.early(e)?.let { route = it; true } ?: false }
                            .onKeyEvent { e -> AppShortcuts.late(e)?.let { route = it; true } ?: false },
                    ) {
                        other = remember { FocusRequester() }
                        Box(Modifier.size(20.dp).testTag("anderes").focusRequester(other).focusable())
                        androidx.compose.material3.Surface(Modifier.weight(1f)) { DocPane(c, doc.id, onClose = {}) }
                    }
                }
            }
        }
        fun settle(n: Int = 10) { repeat(n) { Thread.sleep(80); mainClock.advanceTimeBy(200) }; waitForIdle() }
        settle(12)
        // In the text: Strg+B makes what follows bold, the Seitenleiste stays as it is.
        val field = onAllNodesWithContentDescription("Dokumenttext").onFirst()
        field.performClick()
        field.performKeyInput { withKeyDown(Key.CtrlLeft) { pressKey(Key.B) } }
        field.performTextInput("Fett")
        settle(12)
        assertNull(route, "Strg+B im Text öffnet nicht die Seitenleiste")
        val md = c.docs.get(doc.id)!!.tabs.first().markdown
        assertTrue("**Fett**" in md, md)
        // Strg+K stays the Befehlssuche, also in the document.
        field.performKeyInput { withKeyDown(Key.CtrlLeft) { pressKey(Key.K) } }
        assertEquals("palette", route)
        // Outside a text Strg+B is the Seitenleiste and Strg+D the Dateien, as before.
        route = null
        runOnIdle { other.requestFocus() }
        settle(2)
        onNodeWithTag("anderes").performKeyInput { withKeyDown(Key.CtrlLeft) { pressKey(Key.B) } }
        assertEquals("drawer", route)
        onNodeWithTag("anderes").performKeyInput { withKeyDown(Key.CtrlLeft) { pressKey(Key.D) } }
        assertEquals("files", route)
    }
}
