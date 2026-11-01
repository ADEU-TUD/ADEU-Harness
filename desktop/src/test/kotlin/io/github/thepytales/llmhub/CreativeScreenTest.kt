package io.github.thepytales.llmhub

import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.runSkikoComposeUiTest
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import io.github.thepytales.llmhub.data.AppSettings
import io.github.thepytales.llmhub.data.DesignTemplates
import io.github.thepytales.llmhub.ui.DesignEditor
import io.github.thepytales.llmhub.ui.TemplatesScreen
import io.github.thepytales.llmhub.ui.LlmHubTheme
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlin.test.Test
import kotlin.test.assertTrue

/** „Erstellen“ (AP-24) shows Lora's field, the design templates and the TU Vorlagen; a design opens in the editor (pictures in build/creative-look). */
@OptIn(ExperimentalTestApi::class)
class CreativeScreenTest {
    @Test
    fun galleryAndEditor() = runSkikoComposeUiTest(Size(1500f, 950f), Density(1f)) {
        mainClock.autoAdvance = false
        val base = Files.createTempDirectory("adeu-creative-ui").toFile()
        val c = AppContainer(android.content.Context(File(base, "files").apply { mkdirs() }, File(base, "cache").apply { mkdirs() }))
        val poster = c.designs.save(DesignTemplates.POSTER.create("Poster Feedback"))
        c.designs.save(DesignTemplates.POST.create())
        val owner = object : androidx.lifecycle.LifecycleOwner {
            val registry = androidx.lifecycle.LifecycleRegistry.createUnsafe(this).apply { currentState = androidx.lifecycle.Lifecycle.State.RESUMED }
            override val lifecycle: androidx.lifecycle.Lifecycle get() = registry
        }
        var editing by androidx.compose.runtime.mutableStateOf<String?>(null)
        setContent {
            androidx.compose.runtime.CompositionLocalProvider(androidx.lifecycle.compose.LocalLifecycleOwner provides owner) {
                LlmHubTheme(AppSettings()) {
                    androidx.compose.material3.Surface(color = androidx.compose.material3.MaterialTheme.colorScheme.background) {
                        val id = editing
                        if (id == null) TemplatesScreen(c, onBack = {}, onUse = {}) else DesignEditor(c, id, onClose = { editing = null })
                    }
                }
            }
        }
        val out = File("build/creative-look").apply { mkdirs() }
        fun settle() {
            repeat(8) {
                Thread.sleep(150)
                mainClock.advanceTimeBy(300)
            }
            waitForIdle()
        }
        fun shot(name: String) {
            settle()
            ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(out, "$name.png"))
        }
        assertTrue(onAllNodesWithText("Tagungsposter").fetchSemanticsNodes().isNotEmpty(), "Gestaltungsvorlagen")
        assertTrue(onAllNodesWithText("Was möchtest du erstellen?").fetchSemanticsNodes().isNotEmpty(), "Lora-Feld")
        assertTrue(onAllNodesWithText("TU Vorlagen").fetchSemanticsNodes().isNotEmpty(), "TU Vorlagen darunter")
        shot("bildschirm-erstellen")
        editing = poster.id
        settle()
        assertTrue(onAllNodesWithText("Exportieren").fetchSemanticsNodes().isNotEmpty(), "Editor offen")
        shot("bildschirm-editor")
        // Select the title through the API of the canvas and look at the properties.
        onNodeWithText("Text").performClick()
        settle()
        onNodeWithText("Überschrift").performClick()
        shot("bildschirm-editor-text")
        base.deleteRecursively()
    }
}
