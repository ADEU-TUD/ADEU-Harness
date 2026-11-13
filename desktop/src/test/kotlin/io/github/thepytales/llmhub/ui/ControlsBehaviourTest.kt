package io.github.thepytales.llmhub.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runSkikoComposeUiTest
import androidx.compose.ui.test.withKeyDown
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.thepytales.llmhub.data.AppSettings
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Bedienelemente aus einem Guss (AP-123): what the keyboard and the mouse do in dialogs and on symbol buttons, and that
 * the screens use the app's parts instead of Material's.
 */
@OptIn(ExperimentalTestApi::class)
class ControlsBehaviourTest {
    private fun ui(block: SkikoComposeUiTest.() -> Unit) = runSkikoComposeUiTest(Size(640f, 480f), Density(1f)) {
        mainClock.autoAdvance = false
        block()
    }

    private fun SkikoComposeUiTest.settle() {
        repeat(6) {
            Thread.sleep(30)
            mainClock.advanceTimeBy(100)
        }
        waitForIdle()
    }

    private fun SkikoComposeUiTest.show(content: @Composable () -> Unit) {
        setContent {
            LlmHubTheme(AppSettings()) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { content() }
            }
        }
        settle()
    }

    private fun SkikoComposeUiTest.press(key: Key, ctrl: Boolean = false) {
        onNode(isDialog()).performKeyInput { if (ctrl) withKeyDown(Key.CtrlLeft) { pressKey(key) } else pressKey(key) }
        settle()
    }

    private fun SkikoComposeUiTest.has(text: String) = onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun renameSelectsTheNameAndEnterSaves() = ui {
        var saved: String? = null
        var open by mutableStateOf(true)
        show { if (open) TextInputDialog("Umbenennen", "Bericht.docx", "Name", onConfirm = { saved = it }, onDismiss = { open = false }) }
        // The cursor is in the field, the name without its ending is selected: typing replaces it.
        onNode(isFocused()).performTextInput("Notizen")
        settle()
        press(Key.Enter)
        assertEquals("Notizen.docx", saved)
        assertFalse(open)
    }

    @Test
    fun selectionOfANameLeavesTheEnding() {
        assertEquals(TextRange(0, 7), nameSelection("Bericht.docx"))
        assertEquals(TextRange(0, 11), nameSelection("Kapitel 1.2"))
        assertEquals(TextRange(0, 6), nameSelection("Ordner"))
        assertEquals(TextRange(0, 9), nameSelection(".gitignore".drop(1)))
    }

    @Test
    fun enterStartsTheMainActionAndEscCloses() = ui {
        var done = 0
        var closed = 0
        var open by mutableStateOf(true)
        show {
            if (open) AlertDialog(
                onDismissRequest = { closed++; open = false },
                title = { Text("Exportieren") },
                text = { Text("Als Word-Datei speichern?") },
                confirmButton = { TextButton(onClick = { done++ }) { Text("Exportieren") } },
                dismissButton = { TextButton(onClick = { closed++ }) { Text("Abbrechen") } },
            )
        }
        press(Key.Enter)
        assertEquals(1, done, "Enter startet die Hauptaktion")
        press(Key.Escape)
        assertEquals(1, closed, "Esc schließt")
    }

    @Test
    fun enterNeverDeletes() = ui {
        var deleted = false
        var open by mutableStateOf(true)
        show { if (open) ConfirmDialog("Chat löschen?", "Kommt in den Papierkorb.", "Löschen", onConfirm = { deleted = true }, onDismiss = { open = false }) }
        press(Key.Enter)
        assertFalse(deleted, "Enter löscht nicht")
        assertTrue(open)
        assertTrue(isDangerLabel("Endgültig löschen") && isDangerLabel("Delete") && isDangerLabel("Zurücksetzen"))
        assertFalse(isDangerLabel("Speichern") || isDangerLabel("Exportieren"))
    }

    @Test
    fun enterDoesNotChooseBetweenTwoMainButtons() = ui {
        var a = 0
        var b = 0
        show {
            AlertDialog(
                onDismissRequest = {},
                title = { Text("Exportiert") },
                confirmButton = {
                    Row {
                        TextButton(onClick = { a++ }) { Text("Öffnen") }
                        TextButton(onClick = { b++ }) { Text("Speichern unter") }
                    }
                },
            )
        }
        press(Key.Enter)
        assertEquals(0, a + b)
    }

    @Test
    fun ctrlEnterSendsFromALongerText() = ui {
        var sent: String? = null
        show {
            var text by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = {},
                title = { Text("Notiz") },
                text = { OutlinedTextField(text, { text = it }, minLines = 3, modifier = Modifier.testTag("feld")) },
                confirmButton = { TextButton(onClick = { sent = text }) { Text("Speichern") } },
            )
        }
        onNode(isFocused()).performTextInput("Zeile eins")
        settle()
        press(Key.Enter)
        assertEquals(null, sent, "Enter macht im längeren Text eine neue Zeile")
        press(Key.Enter, ctrl = true)
        assertTrue(sent?.startsWith("Zeile eins") == true, "Strg+Enter speichert: $sent")
    }

    @Test
    fun symbolButtonShowsItsDescriptionAsTooltip() = ui {
        show {
            Box(Modifier.padding(top = 120.dp, start = 120.dp)) {
                IconButton(onClick = {}, modifier = Modifier.testTag("suche")) { Icon(Icons.Outlined.Search, contentDescription = "Suchen (Strg+F)") }
            }
        }
        assertFalse(has("Suchen (Strg+F)"))
        onNodeWithTag("suche").performMouseInput { moveTo(center) }
        mainClock.advanceTimeBy(TIP_DELAY_MS + 200)
        settle()
        assertTrue(has("Suchen (Strg+F)"), "Tooltip erscheint")
        onNodeWithTag("suche").performMouseInput { moveTo(androidx.compose.ui.geometry.Offset(-200f, -200f)) }
        settle()
        assertFalse(has("Suchen (Strg+F)"), "Tooltip verschwindet")
    }

    /** Hinweis-Banner: an error, a deletion with „Rückgängig“ and a result – picture in build/bedienung/hinweise.png. */
    @Test
    fun noticeBanners() = runSkikoComposeUiTest(Size(640 * 2f, 280 * 2f), Density(2f)) {
        mainClock.autoAdvance = false
        class Data(override val visuals: androidx.compose.material3.SnackbarVisuals) : androidx.compose.material3.SnackbarData {
            override fun performAction() {}
            override fun dismiss() {}
        }
        setContent {
            LlmHubTheme(AppSettings()) {
                androidx.compose.foundation.layout.Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(vertical = 8.dp).testTag("shot")) {
                    NoticeBar(Data(Notices.Notice("Der Uni-Server antwortet gerade nicht.", action = "Erneut versuchen", error = true).visuals()))
                    NoticeBar(Data(Notices.Notice("Chat gelöscht", action = "Rückgängig").visuals()))
                    NoticeBar(Data(NoticeVisuals("Geplante Aufgabe: Wochenbericht ist fertig.", actionLabel = "Ansehen", tone = NoticeTone.DONE)))
                }
            }
        }
        settle()
        File("build/bedienung").mkdirs()
        javax.imageio.ImageIO.write(
            onNodeWithTag("shot").captureToImage().toAwtImage(), "png", File("build/bedienung/hinweise.png"),
        )
        assertTrue(has("Erneut versuchen") && has("Rückgängig") && has("Ansehen"))
    }

    /** Long pages and lists show a scrollbar on the PC; dragging it scrolls, a page that fits has none. */
    @Test
    fun scrollbarShowsAndDrags() = ui {
        val page = androidx.compose.foundation.ScrollState(0)
        lateinit var list: androidx.compose.foundation.lazy.LazyListState
        show {
            list = androidx.compose.foundation.lazy.rememberLazyListState()
            Row(Modifier.fillMaxSize()) {
                androidx.compose.foundation.layout.Column(Modifier.weight(1f).fillMaxSize().testTag("seite").verticalScroll(page)) {
                    repeat(60) { Text("Zeile $it", Modifier.padding(8.dp)) }
                }
                LazyColumn(Modifier.weight(1f).fillMaxSize().testTag("liste"), state = list) {
                    items(200) { Text("Eintrag $it", Modifier.padding(8.dp)) }
                }
                androidx.compose.foundation.layout.Column(Modifier.weight(1f).fillMaxSize().testTag("kurz").verticalScroll(androidx.compose.foundation.ScrollState(0))) {
                    Text("Passt")
                }
            }
        }
        val img = onNodeWithTag("seite").captureToImage().toAwtImage()
        File("build/bedienung").mkdirs()
        javax.imageio.ImageIO.write(img, "png", File("build/bedienung/bildlaufleiste.png"))
        // The bar sits at the right edge, near the top (the page starts at its beginning).
        fun dark(x: Int, y: Int) = (img.getRGB(x, y) and 0xff) < 200
        assertTrue((4 until 40).any { dark(img.width - 5, it) }, "Leiste oben rechts sichtbar")
        val short = onNodeWithTag("kurz").captureToImage().toAwtImage()
        assertFalse((0 until short.height).any { (short.getRGB(short.width - 5, it) and 0xff) < 200 }, "keine Leiste, wenn alles passt")
        // Dragging the bar scrolls the page and the list.
        for (tag in listOf("seite", "liste")) {
            val w = onNodeWithTag(tag).fetchSemanticsNode().size.width.toFloat()
            onNodeWithTag(tag).performMouseInput {
                moveTo(androidx.compose.ui.geometry.Offset(w - 5f, 20f)); press()
                moveTo(androidx.compose.ui.geometry.Offset(w - 5f, 220f)); release()
            }
            settle()
        }
        assertTrue(page.value > page.maxValue / 3, "Seite gescrollt: ${page.value} von ${page.maxValue}")
        assertTrue(list.firstVisibleItemIndex > 40, "Liste gescrollt: ${list.firstVisibleItemIndex}")
    }

    /** Screens use the app's parts (ui/Controls.kt, ui/Dialogs.kt): Material's directly would lose looks and keys. */
    @Test
    fun screensUseTheAppsControls() {
        val names = "Button|OutlinedButton|TextButton|FilledTonalButton|IconButton|Icon|AlertDialog|OutlinedTextField|Switch|DropdownMenu"
        val direct = Regex(
            """^import androidx\.compose\.material3\.($names)$|androidx\.compose\.material3\.($names)\(|""" +
                """^import androidx\.compose\.foundation\.(verticalScroll|lazy\.LazyColumn)$""",
        )
        val own = setOf("Controls.kt", "Dialogs.kt", "Scrollbars.kt")
        val found = listOf(File("../app/src/main/java"), File("src/main/kotlin")).flatMap { root ->
            root.walkTopDown().filter { it.isFile && it.extension == "kt" && it.name !in own }
                .filter { it.readText().contains("\npackage io.github.thepytales.llmhub.ui\n") || it.readText().startsWith("package io.github.thepytales.llmhub.ui\n") }
                .flatMap { f -> f.readLines().withIndex().filter { direct.containsMatchIn(it.value) }.map { "${f.path}:${it.index + 1}" }.toList() }
                .toList()
        }
        assertTrue(found.isEmpty(), "Bausteine aus ui/Controls.kt, ui/Dialogs.kt und ui/Scrollbars.kt nehmen (Import von androidx.compose entfernen):\n" + found.joinToString("\n"))
    }
}
