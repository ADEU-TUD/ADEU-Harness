package io.github.thepytales.llmhub

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.thepytales.llmhub.agent.LoraAgent
import io.github.thepytales.llmhub.agent.LoraReply
import io.github.thepytales.llmhub.agent.LoraStep
import io.github.thepytales.llmhub.data.AppMap
import io.github.thepytales.llmhub.data.AppSettings
import io.github.thepytales.llmhub.data.AppTool
import io.github.thepytales.llmhub.data.ChatMessage
import io.github.thepytales.llmhub.ui.LlmHubTheme
import io.github.thepytales.llmhub.ui.LoraFace
import io.github.thepytales.llmhub.ui.LoraFaceStill
import io.github.thepytales.llmhub.ui.LoraHelperButton
import io.github.thepytales.llmhub.ui.LoraTopBarButton
import io.github.thepytales.llmhub.ui.LoraAction
import io.github.thepytales.llmhub.ui.LoraHandoffCard
import io.github.thepytales.llmhub.ui.LoraHelper
import io.github.thepytales.llmhub.ui.LoraNextSteps
import io.github.thepytales.llmhub.ui.LoraSheet
import io.github.thepytales.llmhub.ui.LoraSidePanel
import io.github.thepytales.llmhub.ui.LoraTurn
import java.io.File
import java.time.LocalDate
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Pictures of Lora's panel and her parts in the chat (AP-33–AP-35), for the pull request and by eye:
 * build/lora-panel-*.png, build/lora-chat-parts.png.
 */
@OptIn(ExperimentalTestApi::class)
class LoraPanelLookTest {
    private val base = java.nio.file.Files.createTempDirectory("adeu-lora-look").toFile().also {
        if (System.getProperty("adeu.data").isNullOrBlank()) System.setProperty("adeu.data", File(it, "daten").absolutePath)
    }
    private val c by lazy { AppContainer(android.content.Context(File(base, "files").apply { mkdirs() }, File(base, "cache").apply { mkdirs() })) }

    private val owner = object : androidx.lifecycle.LifecycleOwner {
        val registry = androidx.lifecycle.LifecycleRegistry.createUnsafe(this).apply { currentState = androidx.lifecycle.Lifecycle.State.RESUMED }
        override val lifecycle: androidx.lifecycle.Lifecycle get() = registry
    }

    /** The app's theme with a lifecycle (Lora's parts collect the settings with it). */
    @androidx.compose.runtime.Composable
    private fun Themed(settings: AppSettings = AppSettings(), content: @androidx.compose.runtime.Composable () -> Unit) {
        androidx.compose.runtime.CompositionLocalProvider(androidx.lifecycle.compose.LocalLifecycleOwner provides owner) {
            LlmHubTheme(settings) { content() }
        }
    }

    private fun ui(width: Int, height: Int, block: SkikoComposeUiTest.() -> Unit) =
        runSkikoComposeUiTest(Size(width * 2f, height * 2f), Density(2f)) {
            mainClock.autoAdvance = false
            block()
        }

    private fun SkikoComposeUiTest.save(name: String) {
        File("build").mkdirs()
        val img = onNodeWithTag("shot").captureToImage().toAwtImage()
        ImageIO.write(img, "png", File("build/$name"))
        assertTrue(img.width > 100 && img.height > 100)
    }

    private fun conversation() {
        LoraHelper.reset()
        val targets = AppMap.build(android = false)
        val theme = targets.first { it.id == "option.appearance.theme" }
        LoraHelper.turns += LoraTurn("Dunkles Design").apply {
            reply = LoraReply("Hier ist es: Einstellungen › Darstellung › Helligkeit.", listOf(LoraStep.Navigate(theme)))
            state[0] = "done"
        }
        val setting = LoraAgent.SETTINGS.first { it.id == "text_size" }
        LoraHelper.turns += LoraTurn("Mach die Schrift größer").apply {
            reply = LoraReply("Soll ich die **Schriftgröße** umstellen?", listOf(LoraStep.ChangeSetting(setting, "1.15")))
        }
        LoraHelper.turns += LoraTurn("Ich will PDFs zusammenfügen").apply {
            reply = LoraReply("Dafür gibt es den PDF-Werkzeugkasten – einschalten?", listOf(LoraStep.EnableTool(AppTool.PDF)))
        }
        LoraHelper.turns += LoraTurn("Trag mir die Skizzenfrist ein").apply {
            reply = LoraReply("Soll ich das eintragen?", listOf(LoraStep.Reminder("Skizzenfrist BMFTR", LocalDate.of(2026, 11, 30))))
        }
        LoraHelper.turns += LoraTurn("Wo hatte ich das mit der Wübben-Stiftung?").apply {
            reply = LoraReply("Das könnte er sein:", listOf(LoraStep.OpenChat("1", "Ausschreibungen Geistes- und Sozialwissenschaften", System.currentTimeMillis())))
        }
        LoraHelper.route.value = "settings/appearance"
        LoraHelper.isOpen.value = true
    }

    @Test
    fun panelOnWideScreens() = ui(960, 820) {
        conversation()
        setContent {
            Themed {
                Row(Modifier.size(960.dp, 820.dp).background(MaterialTheme.colorScheme.background).testTag("shot")) {
                    Box(Modifier.weight(1f).fillMaxSize().padding(24.dp)) { Text("App", style = MaterialTheme.typography.headlineMedium) }
                    LoraSidePanel(c)
                }
            }
        }
        mainClock.advanceTimeBy(1500)
        save("lora-panel-wide.png")
    }

    @Test
    fun sheetOnPhones() = ui(412, 860) {
        LoraHelper.reset()
        LoraHelper.route.value = "chat"
        LoraHelper.chatHasMessages = true
        LoraHelper.isOpen.value = true
        setContent {
            Themed(AppSettings(userName = "Dominik")) {
                Box(Modifier.size(412.dp, 860.dp).background(MaterialTheme.colorScheme.background).testTag("shot")) {
                    Text("Chat", Modifier.padding(24.dp), style = MaterialTheme.typography.headlineMedium)
                    LoraSheet(c)
                }
            }
        }
        mainClock.advanceTimeBy(1500)
        save("lora-panel-phone.png")
        LoraHelper.isOpen.value = false
    }

    @Test
    fun partsInTheChat() = ui(520, 520) {
        val handed = ChatMessage(
            role = "user", content = "Suche im Web nach Ausschreibungen für Forschung und Lehre in den Geistes- und Sozialwissenschaften mit Frist in den nächsten 4 Wochen.",
            instructions = "Kontext aus dem Gespräch mit Lora, der Helferin der App (nicht vom Nutzer getippt, nicht wörtlich wiederholen): Fachrichtung Geistes-/Sozialwissenschaften.",
            handoff = "Qwen3.8 27B · kann im Web suchen",
        )
        val answer = "| Fördergeber | Programm | Frist |\n|---|---|---|\n| BMFTR | Generative KI | 30.11.2026 |\n| Wübben Stiftung | Sandpit | 15.12.2026 |\n\nMehr unter doi:10.1007/s11618-023-01170-3."
        setContent {
            Themed {
                Column(
                    Modifier.width(520.dp).background(MaterialTheme.colorScheme.background).padding(16.dp).testTag("shot"),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    LoraHandoffCard(handed)
                    LoraNextSteps(c, answer, null)
                }
            }
        }
        mainClock.advanceTimeBy(2500)
        save("lora-chat-parts.png")
    }

    /**
     * Lora's entries as close-ups (the whole figure would be tiny there): „Frag Lora“ in the top bar (wide and compact),
     * the sleeping Lora in the corner and the face in a few moods – build/lora-entries.png.
     */
    @Test
    fun entriesShowHerFace() = ui(520, 260) {
        LoraHelper.isOpen.value = false
        setContent {
            Themed {
                Column(
                    Modifier.width(520.dp).background(MaterialTheme.colorScheme.background).padding(16.dp).testTag("shot"),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        LoraTopBarButton(c)
                        LoraTopBarButton(c, compact = true)
                        LoraHelperButton(c)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        listOf(LoraAction.IDLE, LoraAction.WAVE, LoraAction.SLEEP, LoraAction.EUREKA, LoraAction.CHEER, LoraAction.THINK).forEach { a ->
                            LoraFaceStill(Modifier.size(56.dp), action = a)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        listOf(24, 32, 40).forEach { d -> LoraFace(Modifier.size(d.dp), label = "Lora") }
                    }
                }
            }
        }
        mainClock.advanceTimeBy(1200)
        save("lora-entries.png")
    }
}
