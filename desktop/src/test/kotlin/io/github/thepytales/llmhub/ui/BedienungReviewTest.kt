package io.github.thepytales.llmhub.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.Search
// Star import on purpose (see ControlsLookTest): the old code shows Material's parts, the new one the app's.
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.data.AppSettings
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.AfterTest
import kotlin.test.Test

/**
 * Pictures of real screens for the review of AP-123 (build/bedienung): a settings page, the export dialog with several
 * actions, „Transkription abbrechen?“, a tooltip and the banners as they were. The same file runs on the old code for
 * the pictures „vorher“.
 */
@OptIn(ExperimentalTestApi::class)
class BedienungReviewTest {
    private val out = File("build/bedienung").apply { mkdirs() }
    private val base = java.nio.file.Files.createTempDirectory("adeu-bedienung").toFile().also {
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
        repeat(rounds) {
            Thread.sleep(80)
            mainClock.advanceTimeBy(150)
        }
        waitForIdle()
    }

    /** The whole window, dialogs and tooltips included. */
    private fun SkikoComposeUiTest.window(name: String) = ImageIO.write(captureToImage().toAwtImage(), "png", File(out, name))

    private fun SkikoComposeUiTest.app(mode: String = "LIGHT", content: @Composable () -> Unit) = setContent {
        androidx.compose.runtime.CompositionLocalProvider(androidx.lifecycle.compose.LocalLifecycleOwner provides owner) {
            LlmHubTheme(AppSettings(themeMode = mode)) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { content() }
            }
        }
    }

    /** Einstellungen › Chat: cards, a secondary button, switches – and the scrollbar of the page. */
    @Test
    fun settingsPage() = runSkikoComposeUiTest(Size(820 * 2f, 560 * 2f), Density(2f)) {
        mainClock.autoAdvance = false
        app { SettingsScreen(c, "chat", onBack = {}, onNavigate = {}) }
        settle()
        window("seite-einstellungen.png")
    }

    @Test
    fun settingsPageDark() = runSkikoComposeUiTest(Size(820 * 2f, 560 * 2f), Density(2f)) {
        mainClock.autoAdvance = false
        app("DARK") { SettingsScreen(c, "chat", onBack = {}, onNavigate = {}) }
        settle()
        window("seite-einstellungen-dunkel.png")
    }

    /** The export dialog: open, share and save as. */
    @Test
    fun exportDialog() = runSkikoComposeUiTest(Size(760 * 2f, 860 * 2f), Density(2f)) {
        mainClock.autoAdvance = false
        app { ExportDialog(c, "Projektbericht", "# Projektbericht\n\nZwischenstand des Projekts.", onDismiss = {}) }
        settle()
        window("dialog-export.png")
    }

    /** Before: two buttons called „Abbrechen“. */
    @Test
    fun transcriptionCancel() = runSkikoComposeUiTest(Size(640 * 2f, 440 * 2f), Density(2f)) {
        mainClock.autoAdvance = false
        app {
            ConfirmDialog(
                "Transkription abbrechen?", "„Interview 3“ ist zu 64 % fertig. Der bisherige Fortschritt geht verloren.",
                "Transkription abbrechen", onConfirm = {}, onDismiss = {}, dismiss = "Weiterlaufen lassen", danger = true, /*NEU*/
            )
        }
        settle()
        window("dialog-transkription.png")
    }

    /** A toolbar of symbol buttons with the mouse resting on one. */
    @Test
    fun tooltip() = runSkikoComposeUiTest(Size(420 * 2f, 170 * 2f), Density(2f)) {
        mainClock.autoAdvance = false
        app {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Projektbericht.docx", style = MaterialTheme.typography.titleMedium)
                Box(Modifier.padding(top = 40.dp)) {
                    Row {
                        IconButton(onClick = {}) { Icon(Icons.Outlined.Search, contentDescription = "Suchen (Strg+F)") }
                        IconButton(onClick = {}, modifier = Modifier.testTag("teilen")) { Icon(Icons.Outlined.IosShare, contentDescription = "Teilen") }
                        IconButton(onClick = {}) { Icon(Icons.Outlined.ContentCopy, contentDescription = "Kopieren") }
                        IconButton(onClick = {}) { Icon(Icons.Outlined.Edit, contentDescription = "Umbenennen (F2)") }
                    }
                }
            }
        }
        settle()
        onNodeWithTag("teilen").performMouseInput { moveTo(center) }
        mainClock.advanceTimeBy(900)
        settle()
        window("tooltip.png")
    }

    /** The banners as the old code showed them: the result banner at the top, errors and „Rückgängig“ below. */
    @Test
    fun bannersAsTheyWere() = runSkikoComposeUiTest(Size(640 * 2f, 300 * 2f), Density(2f)) {
        mainClock.autoAdvance = false
        class Data(override val visuals: SnackbarVisuals) : SnackbarData {
            override fun performAction() {}
            override fun dismiss() {}
        }
        class Visuals(override val message: String, override val actionLabel: String?) : SnackbarVisuals {
            override val withDismissAction = true
            override val duration = SnackbarDuration.Long
        }
        app {
            Column(Modifier.padding(12.dp).width(616.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Snackbar(Data(Visuals("Der Uni-Server antwortet gerade nicht.", "Erneut versuchen")), shape = RoundedCornerShape(14.dp))
                Snackbar(Data(Visuals("Chat gelöscht", "Rückgängig")), shape = RoundedCornerShape(14.dp))
                Snackbar(
                    Data(Visuals("Geplante Aufgabe: Wochenbericht ist fertig.", "Ansehen")),
                    shape = RoundedCornerShape(16.dp),
                    containerColor = LocalChatLook.current.card,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    actionColor = MaterialTheme.colorScheme.primary,
                    dismissActionContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        settle()
        window("hinweise-vorher.png")
    }
}
