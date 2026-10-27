package io.github.thepytales.llmhub

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.thepytales.llmhub.data.AppSettings
import io.github.thepytales.llmhub.data.ModelCatalog
import io.github.thepytales.llmhub.data.TemplateLibrary
import io.github.thepytales.llmhub.ui.Composer
import io.github.thepytales.llmhub.ui.ContextChip
import io.github.thepytales.llmhub.ui.EmptyChat
import io.github.thepytales.llmhub.ui.LlmHubTheme
import io.github.thepytales.llmhub.ui.WorkingCard
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Lora in the chat and on the start page: the card while the agent works keeps its height whatever the step says,
 * and pictures of both (build/lora-*.png) for a look at the layout on phones.
 */
@OptIn(ExperimentalTestApi::class)
class LoraUiTest {
    private fun ui(width: Int, height: Int, block: SkikoComposeUiTest.() -> Unit) =
        runSkikoComposeUiTest(Size(width * 2f, height * 2f), Density(2f)) {
            // Lora moves on every frame; the clock only moves when the test says so.
            mainClock.autoAdvance = false
            block()
        }

    @Test
    fun workingCardKeepsItsHeight() = ui(500, 300) {
        val width = mutableStateOf(360.dp)
        val step = mutableStateOf<String?>(null)
        val tool = mutableStateOf<String?>(null)
        val count = mutableIntStateOf(0)
        val open = mutableStateOf(false)
        setContent {
            LlmHubTheme(AppSettings()) {
                Box(Modifier.width(width.value).testTag("card")) {
                    WorkingCard(step.value, tool.value, step.value == "Denkt nach …", count.intValue, open.value) { open.value = !open.value }
                }
            }
        }
        val heights = mutableMapOf<String, Int>()
        for (w in listOf(360.dp, 412.dp)) for (s in listOf(null, "Denkt nach …", "Websuche …", "Ordner durchsuchen – ein sehr langer Schritt, der nie in eine Zeile passt …"))
            for (n in listOf(0, 1, 71)) for (o in listOf(false, true)) {
                width.value = w
                step.value = s
                tool.value = if (s == "Websuche …") "web_search" else null
                count.intValue = n
                open.value = o
                repeat(20) { mainClock.advanceTimeByFrame() }
                heights["$w/$s/$n/$o"] = onNodeWithTag("card").fetchSemanticsNode().size.height
            }
        assertEquals(1, heights.values.toSet().size, "Karte immer gleich hoch: $heights")
    }

    @Test
    fun pictures() {
        // Start page on a large and a small phone, while typing, with the keyboard open; the card in a chat.
        listOf(412 to 800, 360 to 640).forEach { (w, h) -> home(w, h, typing = false, name = "lora-home-$w.png", advance = 1300) }
        home(412, 800, typing = true, name = "lora-home-typing.png", advance = 2500)
        home(412, 430, typing = false, name = "lora-home-keyboard.png", advance = 900)
        ui(412, 330) {
            setContent {
                LlmHubTheme(AppSettings()) {
                    Column(
                        Modifier.width(412.dp).background(MaterialTheme.colorScheme.background).padding(14.dp).testTag("shot"),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        WorkingCard(null, null, false, 0, false, null)
                        WorkingCard("Websuche …", "web_search", false, 71, false) {}
                        WorkingCard("Denkt nach …", null, true, 3, true) {}
                    }
                }
            }
            mainClock.advanceTimeBy(1600)
            save("lora-card.png")
        }
    }

    private fun home(w: Int, h: Int, typing: Boolean, name: String, advance: Long) = ui(w, h) {
        setContent { LlmHubTheme(AppSettings()) { Phone(w.dp, h.dp, typing) } }
        mainClock.advanceTimeBy(advance)
        save(name)
    }

    private fun SkikoComposeUiTest.save(name: String) {
        File("build").mkdirs()
        ImageIO.write(onNodeWithTag("shot").captureToImage().toAwtImage(), "png", File("build/$name"))
    }

    /** The start of a new chat as on the phone: top bar, the start page, the chips and the input. */
    @Composable
    private fun Phone(width: Dp, height: Dp, typing: Boolean) {
        Column(Modifier.size(width, height).background(MaterialTheme.colorScheme.background).testTag("shot")) {
            Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 12.dp)) {
                Icon(Icons.Outlined.Menu, null, Modifier.padding(top = 16.dp))
                Spacer(Modifier.weight(1f))
                Icon(Icons.Outlined.VisibilityOff, null, Modifier.padding(top = 16.dp, end = 20.dp))
                Icon(Icons.Outlined.MoreVert, null, Modifier.padding(top = 16.dp))
            }
            Box(Modifier.weight(1f)) {
                EmptyChat(
                    hasApiKey = true,
                    userName = "Dominik",
                    model = ModelCatalog.info(ModelCatalog.DEFAULT_CHAT),
                    onModel = {},
                    padding = PaddingValues(0.dp),
                    suggestions = TemplateLibrary.suggestions(emptyList(), emptyList()),
                    onTemplate = {},
                    onApiKey = {},
                    onAllTemplates = {},
                    onOpen = {},
                    typing = typing,
                )
            }
            Composer(
                input = if (typing) "Finde Literatur zu Schulgeografie" else "",
                onInput = {},
                placeholder = "Wie kann ich helfen?",
                modelLabel = "Automatisch · DeepSeek V4.1 Flash",
                onModel = {},
                images = emptyList(),
                files = emptyList(),
                loadingFiles = 0,
                onRemoveImage = {},
                onRemoveFile = {},
                running = false,
                recording = false,
                transcribing = false,
                onPlus = {},
                onMic = {},
                onSend = {},
                onStop = {},
            ) {
                ContextChip(Icons.Outlined.Code, "GitHub · Repo wählen", {}, highlighted = false)
                ContextChip(Icons.Outlined.Tune, "Standard", {}, highlighted = false)
                ContextChip(Icons.Outlined.Psychology, "Gedächtnis", {}, highlighted = false)
            }
            Text("", style = MaterialTheme.typography.labelSmall)
        }
    }
}
