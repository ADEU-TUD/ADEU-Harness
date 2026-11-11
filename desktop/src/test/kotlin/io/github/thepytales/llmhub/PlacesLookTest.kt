package io.github.thepytales.llmhub

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.thepytales.llmhub.data.AppSettings
import io.github.thepytales.llmhub.data.Places
import io.github.thepytales.llmhub.local.Hardware
import io.github.thepytales.llmhub.local.LocalCatalog
import io.github.thepytales.llmhub.ui.IntroModelChoice
import io.github.thepytales.llmhub.ui.IntroModels
import io.github.thepytales.llmhub.ui.LlmHubTheme
import io.github.thepytales.llmhub.ui.LocalSetupScreen
import io.github.thepytales.llmhub.ui.PlacesSettings
import io.github.thepytales.llmhub.ui.WelcomeCard
import java.io.File
import java.io.RandomAccessFile
import javax.imageio.ImageIO
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Wo Lora arbeitet (AP-112) as pictures (build/orte-*.png): the page at the width of a Windows window and of an Android
 * phone, the setup step by step, the first start without a key. Rendered with the Compose interface of the desktop
 * version – the Android app uses the same screens.
 */
@OptIn(ExperimentalTestApi::class)
class PlacesLookTest {
    private val base = java.nio.file.Files.createTempDirectory("adeu-orte").toFile().also {
        if (System.getProperty("adeu.data").isNullOrBlank()) System.setProperty("adeu.data", File(it, "daten").absolutePath)
    }
    private val c by lazy { AppContainer(android.content.Context(File(base, "files").apply { mkdirs() }, File(base, "cache").apply { mkdirs() })) }
    private var fake: File? = null

    private val owner = object : androidx.lifecycle.LifecycleOwner {
        val registry = androidx.lifecycle.LifecycleRegistry.createUnsafe(this).apply { currentState = androidx.lifecycle.Lifecycle.State.RESUMED }
        override val lifecycle: androidx.lifecycle.Lifecycle get() = registry
    }

    @AfterTest
    fun cleanUp() {
        fake?.delete()
        c.localLlm.refresh()
        base.deleteRecursively()
    }

    private val officePc = Hardware(phone = false, ramGb = 16.0, cores = 6, cpuName = "Intel Core i5", cpuFeatures = setOf("avx2"), memBandwidthGbs = 51.2, memDetail = "DDR4-3200 · 128 Bit")
    private val phone = Hardware(phone = true, ramGb = 12.0, cores = 8, cpuName = "Snapdragon", cpuFeatures = setOf("neon", "dotprod", "i8mm"))

    /** A model „on the device“ (a sparse file of the right size), so the page shows what runs here. */
    private fun withModel(id: String) {
        val m = LocalCatalog.get(id)!!
        val f = c.localLlm.file(m)
        f.parentFile.mkdirs()
        RandomAccessFile(f, "rw").use { it.setLength(m.size) }
        fake = f
        c.localLlm.refresh()
    }

    private fun ui(width: Dp, block: SkikoComposeUiTest.() -> Unit) =
        runSkikoComposeUiTest(Size(width.value * 2f + 80, 3400 * 2f), Density(2f)) {
            mainClock.autoAdvance = false
            block()
        }

    private fun SkikoComposeUiTest.settle() {
        repeat(6) {
            Thread.sleep(200)
            mainClock.advanceTimeBy(300)
        }
        waitForIdle()
    }

    private fun SkikoComposeUiTest.shot(name: String, width: Dp, content: @androidx.compose.runtime.Composable () -> Unit): Int {
        setContent {
            androidx.compose.runtime.CompositionLocalProvider(androidx.lifecycle.compose.LocalLifecycleOwner provides owner) {
                LlmHubTheme(AppSettings()) {
                    Box(Modifier.background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.TopStart) {
                        Column(
                            Modifier.width(width).wrapContentHeight().background(MaterialTheme.colorScheme.background).padding(16.dp).testTag("shot"),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) { content() }
                    }
                }
            }
        }
        settle()
        val img = onNodeWithTag("shot").captureToImage().toAwtImage()
        File("build/screenshots").mkdirs()
        ImageIO.write(img, "png", File("build/screenshots/$name"))
        return img.height / 2
    }

    private fun SkikoComposeUiTest.has(text: String) = onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun pageAtTheWidthOfAWindowsWindow() = ui(1000.dp) {
        c.settings.update { it.copy(apiKey = "sk-test", places = "", loraPlace = "MIXED", ttsEngine = "SERVER") }
        c.localLlm.pretend(officePc)
        withModel("qwen3.5-4b")
        Places.apply(c, Places.Profile.MIXED)
        shot("orte-windows-gemischt.png", 1000.dp) { PlacesSettings(c) {} }
        assertTrue(has("Alles lokal") && has("Gemischt (empfohlen)") && has("Alles über API"))
        assertTrue(has("nur über API"), "Bilder erzeugen wird gezeigt, nicht versteckt")
        assertTrue(has("braucht Internet"))
        assertTrue(has("Ins Postfach darf nur ein Modell auf diesem Gerät"), "ausgegraut mit Grund")
        Places.apply(c, Places.Profile.ALL_LOCAL)
        shot("orte-windows-alles-lokal.png", 1000.dp) { PlacesSettings(c) {} }
        assertTrue(has("läuft auf diesem Gerät"))
    }

    @Test
    fun pageAtTheWidthOfAnAndroidPhone() = ui(400.dp) {
        c.settings.update { it.copy(apiKey = "", providers = emptyList(), places = "", loraPlace = "MIXED", ttsEngine = "SERVER") }
        c.localLlm.pretend(phone)
        withModel("qwen3.5-2b")
        shot("orte-android-ohne-schluessel.png", 400.dp) { PlacesSettings(c) {} }
        assertTrue(has("Kein API-Schlüssel hinterlegt"))
        assertTrue(has("Kein API-Schlüssel – darum auf diesem Gerät."))
    }

    @Test
    fun setupStepByStep() = ui(760.dp) {
        c.settings.update { it.copy(apiKey = "", places = "", localSetupStep = 3) }
        c.localLlm.pretend(officePc)
        shot("einrichtung-windows-sprachmodell.png", 760.dp) { LocalSetupScreen(c, onBack = {}, onNavigate = {}) }
        assertTrue(has("Schritt 3 von 9"))
        assertTrue(has("Wörter/s"), "Tempo je Modell")
        assertTrue(has("Später") && has("Überspringen"))
        c.settings.update { it.copy(localSetupStep = 9) }
        withModel("qwen3.5-4b")
        shot("einrichtung-windows-uebersicht.png", 760.dp) { LocalSetupScreen(c, onBack = {}, onNavigate = {}) }
        assertTrue(has("Alles auf diesem Gerät nutzen"))
        c.settings.update { it.copy(localSetupStep = 0) }
    }

    @Test
    fun firstStartWithoutAKey() = ui(520.dp) {
        c.settings.update { it.copy(apiKey = "", providers = emptyList(), places = "", loraPlace = "MIXED") }
        c.localLlm.pretend(phone)
        shot("erster-start-android-ohne-schluessel.png", 480.dp) {
            WelcomeCard(onApiKey = {}, onNavigate = {})
            IntroModels(c, androidx.compose.runtime.remember { IntroModelChoice(c) })
        }
        assertTrue(has("Alles auf diesem Gerät einrichten"))
        assertTrue(has("Alles auf diesem Gerät"))
        assertTrue(has("Noch kein API-Schlüssel"))
    }
}
