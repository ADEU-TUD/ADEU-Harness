package io.github.thepytales.llmhub.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.rightClick
import androidx.compose.ui.test.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.data.AppSettings
import io.github.thepytales.llmhub.docs.LiveDoc
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * APD-15 (Design-Arbeitsplan): pictures of the document editor for the comparison „vorher/nachher“ – a small laptop
 * (1366 × 768 at 150 %), Full HD at 125 % and a large monitor (2560 × 1440 at 125 %); ADEU light and dark, Hoher
 * Kontrast; with the menu *Datei* open. Pictures in build/apd15, and in messung.txt where the text of the page starts
 * (the room the header leaves for the page). The same file runs on the old code for the pictures „vorher“.
 */
@OptIn(ExperimentalTestApi::class)
class DocEditorLookTest {
    private val out = File("build/apd15").apply { mkdirs() }
    private val base = java.nio.file.Files.createTempDirectory("adeu-apd15").toFile().also {
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
        repeat(rounds) {
            Thread.sleep(80)
            mainClock.advanceTimeBy(160)
        }
        waitForIdle()
    }

    private val sample = """
        # Antrag Lehrinnovation

        ## 1. Ausgangslage

        Die Vorlesung *Einführung in die Statistik* hat jedes Semester rund 400 Teilnehmende. Die Übungen sind überlaufen, und viele Studierende brechen das Modul im ersten Semester ab. Gleichzeitig fehlt es an Material, mit dem sie sich selbstständig und mit sofortiger Rückmeldung vorbereiten können.

        Mit dem Vorhaben sollen **Selbstlernmodule** entstehen, die an die Vorlesung anschließen und in OPAL bereitstehen.

        ## 2. Ziele

        - Selbstlernmodule mit sofortiger Rückmeldung
        - Weniger Abbrüche im ersten Semester (Ziel: −15 %)
        - Wiederverwendbare Materialien unter offener Lizenz

        ## 3. Zeitplan

        | Phase | Zeitraum | Ergebnis |
        |---|---|---|
        | Konzeption | 01–03/2027 | Lernziele, Storyboard |
        | Umsetzung | 04–09/2027 | 12 Module in OPAL |
        | Evaluation | 10–12/2027 | Bericht, Kennzahlen |

        ## 4. Nachhaltigkeit

        Die Module werden nach Projektende von der Professur weitergeführt und jedes Semester aktualisiert.
    """.trimIndent()

    private fun shoot(name: String, widthDp: Int, heightDp: Int, density: Float, style: String = "ADEU", mode: String = "LIGHT", openFile: Boolean = false, rightClick: Boolean = false) {
        c.settings.update {
            AppSettings().copy(
                appStyle = style, themeMode = mode, startAnimation = false, loraIntroDone = true, modelsIntroDone = true,
                pseudonymModelAsk = false, commandModelAsk = false, loraTours = false, memoryIntroDone = true, autoModelAsk = false,
                aiUseHint = false, userName = "Alex",
            )
        }
        val doc = LiveDoc.create("Antrag Lehrinnovation", sample, "Alex")
        c.docs.save(doc)
        runSkikoComposeUiTest(Size(widthDp * density, heightDp * density), Density(density)) {
            mainClock.autoAdvance = false
            setContent {
                androidx.compose.runtime.CompositionLocalProvider(androidx.lifecycle.compose.LocalLifecycleOwner provides owner) {
                    val s by c.settings.settings.collectAsState()
                    LlmHubTheme(s) {
                        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { DocScreen(c, doc.id, onBack = {}, onOpenChat = {}) }
                    }
                }
            }
            settle(16)
            val top = runCatching { onAllNodesWithContentDescription("Dokumenttext").onFirst().getBoundsInRoot().top.value }.getOrNull()
            File(out, "messung.txt").appendText("$name: Text der Seite beginnt bei ${top?.let { "%.0f dp".format(it) } ?: "?"} von $heightDp dp\n")
            if (openFile) {
                onAllNodesWithText("Datei").onFirst().performClick()
                settle(6)
            }
            // Rechtsklick-Menü (AP-125), on a word of the first paragraph.
            if (rightClick) {
                onAllNodesWithContentDescription("Dokumenttext").onFirst().performMouseInput { rightClick(androidx.compose.ui.geometry.Offset(width * 0.3f, height * 0.42f)) }
                settle(6)
            }
            ImageIO.write(captureToImage().toAwtImage(), "png", File(out, "$name.png"))
        }
        assertTrue(File(out, "$name.png").isFile)
    }

    @Test fun laptop() = shoot("laptop-hell", 911, 512, 1.5f)
    @Test fun laptopDark() = shoot("laptop-dunkel", 911, 512, 1.5f, mode = "DARK")
    @Test fun laptopContrast() = shoot("laptop-kontrast", 911, 512, 1.5f, style = "CONTRAST")
    @Test fun laptopFileMenu() = shoot("laptop-datei", 911, 512, 1.5f, openFile = true)
    @Test fun fullHd() = shoot("fullhd-hell", 1536, 864, 1.25f)
    @Test fun laptopRightClick() = shoot("laptop-rechtsklick", 911, 512, 1.5f, rightClick = true)
    @Test fun large() = shoot("gross-hell", 2048, 1152, 1.25f)
}
