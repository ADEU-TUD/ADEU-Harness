package io.github.thepytales.llmhub.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
// A star import on purpose: the app's own Button, AlertDialog … of this package win over it, so the same test shows
// Material's parts on the old code and the app's on the new one (pictures before/after, AP-123).
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runSkikoComposeUiTest
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.thepytales.llmhub.data.AppSettings
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test

/**
 * Bedienelemente aus einem Guss (AP-123) as pictures in build/bedienung: buttons, fields, switches and symbol buttons
 * in the main styles, a delete question, a rename dialog, a dialog with a list and a menu.
 */
@OptIn(ExperimentalTestApi::class)
class ControlsLookTest {
    private val out = File("build/bedienung").apply { mkdirs() }

    private fun SkikoComposeUiTest.settle() {
        repeat(8) {
            Thread.sleep(60)
            mainClock.advanceTimeBy(120)
        }
        waitForIdle()
    }

    private fun SkikoComposeUiTest.save(name: String, node: androidx.compose.ui.test.SemanticsNodeInteraction) {
        ImageIO.write(node.captureToImage().toAwtImage(), "png", File(out, name))
    }

    @Composable
    private fun Gallery(label: String) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = {}) { Text("Speichern") }
                OutlinedButton(onClick = {}) { Text("Teilen") }
                FilledTonalButton(onClick = {}) { Text("Vorschlag") }
                TextButton(onClick = {}) { Text("Mehr") }
                OutlinedButton(onClick = {}, enabled = false) { Text("Nicht verfügbar") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField("Projektbericht", {}, label = { Text("Titel") }, singleLine = true, modifier = Modifier.width(230.dp))
                OutlinedTextField(
                    "geheim", {}, label = { Text("Passwort") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), modifier = Modifier.width(230.dp),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = true, onCheckedChange = {})
                Switch(checked = false, onCheckedChange = {})
                Checkbox(checked = true, onCheckedChange = {})
                IconButton(onClick = {}) { Icon(Icons.Outlined.Search, contentDescription = "Suchen") }
                IconButton(onClick = {}) { Icon(Icons.Outlined.ContentCopy, contentDescription = "Kopieren") }
                IconButton(onClick = {}) { Icon(Icons.Outlined.Settings, contentDescription = "Einstellungen") }
            }
        }
    }

    /** Buttons, fields and switches in ADEU (light and dark), TU Dresden, Office, Hoher Kontrast and Claude. */
    @Test
    fun galleryOfControls() = runSkikoComposeUiTest(Size(700 * 2f, 1500 * 2f), Density(2f)) {
        mainClock.autoAdvance = false
        val looks = listOf("ADEU" to "LIGHT", "ADEU" to "DARK", "TUD" to "LIGHT", "OFFICE" to "LIGHT", "CONTRAST" to "LIGHT", "CLAUDE" to "LIGHT")
        setContent {
            Column(Modifier.testTag("shot")) {
                looks.forEach { (style, mode) ->
                    LlmHubTheme(AppSettings(appStyle = style, themeMode = mode)) {
                        Surface(Modifier.width(700.dp), color = MaterialTheme.colorScheme.background) {
                            Gallery(AppStyle.valueOf(style).label + if (mode == "DARK") " · dunkel" else "")
                        }
                    }
                }
            }
        }
        settle()
        save("bedienelemente.png", onNodeWithTag("shot"))
    }

    private fun SkikoComposeUiTest.dialogShot(name: String, mode: String = "LIGHT", dialog: @Composable () -> Unit) {
        setContent {
            LlmHubTheme(AppSettings(themeMode = mode)) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp)) {
                    Text("Lora", style = MaterialTheme.typography.headlineSmall)
                }
                dialog()
            }
        }
        settle()
        save(name, onNode(isDialog()))
    }

    @Test
    fun deleteQuestion() = runSkikoComposeUiTest(Size(640 * 2f, 440 * 2f), Density(2f)) {
        mainClock.autoAdvance = false
        dialogShot("dialog-loeschen.png") {
            ConfirmDialog("Chat löschen?", "„Antrag DFG 2027“ kommt in den Papierkorb (30 Tage, Einstellungen › Daten).", "Löschen", onConfirm = {}, onDismiss = {})
        }
    }

    @Test
    fun deleteQuestionDark() = runSkikoComposeUiTest(Size(640 * 2f, 440 * 2f), Density(2f)) {
        mainClock.autoAdvance = false
        dialogShot("dialog-loeschen-dunkel.png", "DARK") {
            ConfirmDialog("Chat löschen?", "„Antrag DFG 2027“ kommt in den Papierkorb (30 Tage, Einstellungen › Daten).", "Löschen", onConfirm = {}, onDismiss = {})
        }
    }

    @Test
    fun renameDialog() = runSkikoComposeUiTest(Size(640 * 2f, 440 * 2f), Density(2f)) {
        mainClock.autoAdvance = false
        dialogShot("dialog-umbenennen.png") {
            TextInputDialog("Umbenennen", "Bericht Zwischenstand.docx", "Name", onConfirm = {}, onDismiss = {})
        }
    }

    /** A dialog as most of the app's are written: a list in the text, text buttons for confirm and dismiss. */
    @Test
    fun listDialog() = runSkikoComposeUiTest(Size(640 * 2f, 520 * 2f), Density(2f)) {
        mainClock.autoAdvance = false
        dialogShot("dialog-liste.png") {
            AlertDialog(
                onDismissRequest = {},
                icon = { Icon(Icons.Outlined.DriveFileRenameOutline, contentDescription = null) },
                title = { Text("Exportieren als") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Word (.docx)", "PDF", "Markdown").forEachIndexed { i, f ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = i == 0, onClick = {})
                                Text(f)
                            }
                        }
                        OutlinedTextField("Bericht", {}, label = { Text("Dateiname") }, singleLine = true)
                    }
                },
                confirmButton = { TextButton(onClick = {}) { Text("Exportieren") } },
                dismissButton = { TextButton(onClick = {}) { Text("Abbrechen") } },
            )
        }
    }

    @Test
    fun menu() = runSkikoComposeUiTest(Size(420 * 2f, 360 * 2f), Density(2f)) {
        mainClock.autoAdvance = false
        setContent {
            LlmHubTheme(AppSettings()) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp)) {
                    Box {
                        Text("Chat ⋮")
                        DropdownMenu(expanded = true, onDismissRequest = {}) {
                            DropdownMenuItem(text = { Text("Umbenennen") }, leadingIcon = { Icon(Icons.Outlined.DriveFileRenameOutline, null) }, onClick = {})
                            DropdownMenuItem(text = { Text("Kopieren") }, leadingIcon = { Icon(Icons.Outlined.ContentCopy, null) }, onClick = {})
                            DropdownMenuItem(colors = dangerMenuColors(), text = { Text("Löschen") }, leadingIcon = { Icon(Icons.Outlined.Delete, null) }, onClick = {})
                        }
                    }
                }
            }
        }
        settle()
        save("menue.png", onNode(isPopup()))
    }
}
