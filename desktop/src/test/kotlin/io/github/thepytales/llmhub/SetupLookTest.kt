package io.github.thepytales.llmhub

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import io.github.thepytales.llmhub.data.ApiProvider
import io.github.thepytales.llmhub.data.DataPlace
import io.github.thepytales.llmhub.data.ProviderKind
import io.github.thepytales.llmhub.ui.DataPlaceChip
import io.github.thepytales.llmhub.ui.LlmHubTheme
import io.github.thepytales.llmhub.ui.SetupPage
import io.github.thepytales.llmhub.ui.SetupWay
import io.github.thepytales.llmhub.ui.SetupWizard
import io.github.thepytales.llmhub.ui.currentWay
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Lora einrichten Schritt für Schritt (AP-120) and „lokal / TU-Server / Cloud“ (AP-119) as pictures
 * (build/screenshots/einrichten-*.png), at the width of a Windows window and of a phone.
 */
@OptIn(ExperimentalTestApi::class)
class SetupLookTest {
    private val base = java.nio.file.Files.createTempDirectory("adeu-einrichten").toFile().also {
        if (System.getProperty("adeu.data").isNullOrBlank()) System.setProperty("adeu.data", File(it, "daten").absolutePath)
    }
    private val c by lazy { AppContainer(android.content.Context(File(base, "files").apply { mkdirs() }, File(base, "cache").apply { mkdirs() })) }

    private val owner = object : androidx.lifecycle.LifecycleOwner {
        val registry = androidx.lifecycle.LifecycleRegistry.createUnsafe(this).apply { currentState = androidx.lifecycle.Lifecycle.State.RESUMED }
        override val lifecycle: androidx.lifecycle.Lifecycle get() = registry
    }

    @AfterTest
    fun cleanUp() { base.deleteRecursively() }

    private fun ui(block: SkikoComposeUiTest.() -> Unit) =
        runSkikoComposeUiTest(Size(1000 * 2f, 800 * 2f), Density(2f)) {
            mainClock.autoAdvance = false
            block()
        }

    private fun SkikoComposeUiTest.shot(name: String, w: Dp, h: Dp, content: @androidx.compose.runtime.Composable () -> Unit) {
        setContent {
            androidx.compose.runtime.CompositionLocalProvider(androidx.lifecycle.compose.LocalLifecycleOwner provides owner) {
                LlmHubTheme(AppSettings()) {
                    Box(Modifier.size(w, h).background(MaterialTheme.colorScheme.background).testTag("shot")) { Surface { content() } }
                }
            }
        }
        repeat(6) { Thread.sleep(150); mainClock.advanceTimeBy(300) }
        waitForIdle()
        File("build/screenshots").mkdirs()
        ImageIO.write(onNodeWithTag("shot").captureToImage().toAwtImage(), "png", File("build/screenshots/$name"))
    }

    private fun SkikoComposeUiTest.has(text: String) = onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()

    private fun SkikoComposeUiTest.step(p: SetupPage, w: Dp = 900.dp, h: Dp = 640.dp, prefix: String = "windows") {
        c.settings.update { it.copy(setupStep = p.ordinal + 1) }
        shot("einrichten-$prefix-${p.ordinal + 1}-${p.name.lowercase()}.png", w, h) { SetupWizard(c, onClose = {}, onNavigate = {}) }
    }

    @Test
    fun everyStepAtTheWidthOfAWindow() = ui {
        c.settings.update { it.copy(apiKey = "", providers = emptyList(), roles = "", loraIntroDone = false) }
        step(SetupPage.WELCOME)
        assertTrue(has("Hallo, ich bin Lora!") && has("Los geht’s") && has("Später"))
        step(SetupPage.ROLES)
        assertTrue(has("Lehre") && has("Verwaltung"))
        step(SetupPage.PLACE)
        // The recommendation first, „alles lokal“ clearly as the special case, the own provider folded away.
        assertTrue(has("Uni-Server der TU Dresden (empfohlen)") && has("TU-Server") && has("lokal"))
        assertTrue(has("Nur sinnvoll für sehr sensible Daten"))
        assertTrue(!has("Arbeitsspeicher"), "Uni-Server vorgewählt, keine Geräteeinschätzung")
        assertTrue(has("Für Fortgeschrittene: eigener Anbieter") && !has("Eigener Anbieter (z. B. Google Gemini)"))
        step(SetupPage.ACCESS)
        assertTrue(has("Self-Service-Portal öffnen") && has("Schlüssel prüfen"))
        step(SetupPage.PRIVACY)
        assertTrue(has("Namen vor dem Senden ersetzen (empfohlen)"))
        step(SetupPage.TOOLS)
        assertTrue(has("PDF-Werkzeugkasten") && has("Für Fortgeschrittene") && !has("Server-Terminal"), "Server-Terminal eingeklappt")
        step(SetupPage.LOOK)
        assertTrue(has("Schriftgröße") && has("Ruhigere Bewegungen"))
        step(SetupPage.DONE)
        assertTrue(has("Fast geschafft!") && has("Ändern"))
    }

    @Test
    fun narrowLikeAPhone() = ui {
        c.settings.update { it.copy(apiKey = "sk-test", loraPlace = "MIXED") }
        step(SetupPage.PLACE, 400.dp, 760.dp, "android")
        assertTrue(has("Schritt 3 von 8"))
    }

    @Test
    fun theWayFollowsTheSettings() {
        // First start without a key: the recommendation, not „alles lokal“.
        c.settings.update { it.copy(apiKey = "", providers = emptyList(), loraPlace = "LOCAL", loraIntroDone = false) }
        assertEquals(SetupWay.TU, currentWay(c))
        c.settings.update { it.copy(loraIntroDone = true) }
        assertEquals(SetupWay.LOCAL, currentWay(c))
        c.settings.update { it.copy(providers = listOf(ApiProvider(kind = ProviderKind.GEMINI.name, apiKey = "x"))) }
        assertEquals(SetupWay.OWN, currentWay(c))
        c.settings.update { it.copy(apiKey = "sk-x") }
        assertEquals(SetupWay.TU, currentWay(c))
    }

    @Test
    fun whereTheDataGoes() = ui {
        // Models: on this device, Uni server, a further provider.
        assertEquals(DataPlace.LOCAL, DataPlace.ofModel(c, "local/qwen3.5-4b"))
        assertEquals(DataPlace.TU, DataPlace.ofModel(c, "deepseek-ai/DeepSeek-V4.1-Flash"))
        c.settings.update { it.copy(providers = listOf(ApiProvider(kind = ProviderKind.GEMINI.name, apiKey = "x"))) }
        assertEquals(DataPlace.CLOUD, DataPlace.ofModel(c, "gemini-3-flash"))
        // Servers by address: this computer, the TU, elsewhere.
        assertEquals(DataPlace.LOCAL, DataPlace.ofUrl("http://localhost:11434/v1"))
        assertEquals(DataPlace.LOCAL, DataPlace.ofUrl("http://127.0.0.1:8080"))
        assertEquals(DataPlace.TU, DataPlace.ofUrl("https://llm.scads.ai/v1"))
        assertEquals(DataPlace.TU, DataPlace.ofUrl("https://kiserver.zih.tu-dresden.de/api"))
        assertEquals(DataPlace.CLOUD, DataPlace.ofUrl("https://api.openai.com/v1"))
        assertEquals(DataPlace.CLOUD, DataPlace.ofUrl("https://tu-dresden.de.example.com/v1"))
        assertEquals(DataPlace.LOCAL, DataPlace.ofServer(null, "npx -y server-filesystem"))
        shot("einrichten-datenort.png", 420.dp, 60.dp) {
            Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) { DataPlace.entries.forEach { DataPlaceChip(it) } }
        }
        assertTrue(has("lokal") && has("TU-Server") && has("Cloud"))
    }
}
