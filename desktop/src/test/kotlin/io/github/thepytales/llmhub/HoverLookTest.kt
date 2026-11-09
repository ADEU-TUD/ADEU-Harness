package io.github.thepytales.llmhub

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.thepytales.llmhub.data.AppSettings
import io.github.thepytales.llmhub.ui.AssistChip
import io.github.thepytales.llmhub.ui.FilterChip
import io.github.thepytales.llmhub.ui.LlmHubTheme
import io.github.thepytales.llmhub.ui.SuggestionChip
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * AP-70 b: hovering a chip or button with the mouse only tints it faintly – no grey box, no shadow edge across the
 * label (Material 3 lifted the transparent chips by 1 dp, and the shadow showed through). The clear keyboard focus of
 * AP-30 stays. Pictures (Material 3 above, the app's chips below) in build/hover.
 */
@OptIn(ExperimentalTestApi::class)
class HoverLookTest {
    private val out = File("build/hover").apply { mkdirs() }
    private val scale = 2f

    private fun SkikoComposeUiTest.settle() {
        repeat(6) {
            Thread.sleep(40)
            mainClock.advanceTimeBy(100)
        }
        waitForIdle()
    }

    private fun SkikoComposeUiTest.shot(name: String): BufferedImage {
        val img = onNodeWithTag("shot").captureToImage().toAwtImage()
        ImageIO.write(img, "png", File(out, name))
        return img
    }

    /** Pixel bounds of the element [tag] in the picture. */
    private fun SkikoComposeUiTest.box(tag: String): java.awt.Rectangle {
        val b = onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
        return java.awt.Rectangle(b.left.toInt(), b.top.toInt(), (b.right - b.left).toInt(), (b.bottom - b.top).toInt())
    }

    private fun diff(a: Int, b: Int) = maxOf(abs((a shr 16 and 0xff) - (b shr 16 and 0xff)), abs((a shr 8 and 0xff) - (b shr 8 and 0xff)), abs((a and 0xff) - (b and 0xff)))
    private fun luma(c: Int) = ((c shr 16 and 0xff) * 299 + (c shr 8 and 0xff) * 587 + (c and 0xff) * 114) / 1000

    /** What hovering (or focusing) did to one element, measured against the same element at rest. */
    private class Change(val halo: Int, val tint: Double, val labelContrast: Int)

    private fun change(rest: BufferedImage, now: BufferedImage, r: java.awt.Rectangle): Change {
        val bg = rest.getRGB(r.x + 1, r.y + 1)
        val cx = r.x + r.width / 2
        val cy = r.y + r.height / 2
        // The outline of the chip or button: first and last pixels on the middle lines that differ from the background.
        val top = (r.y until r.y + r.height).first { diff(rest.getRGB(cx, it), bg) > 20 }
        val bottom = (r.y until r.y + r.height).last { diff(rest.getRGB(cx, it), bg) > 20 }
        val left = (r.x until r.x + r.width).first { diff(rest.getRGB(it, cy), bg) > 20 }
        val right = (r.x until r.x + r.width).last { diff(rest.getRGB(it, cy), bg) > 20 }
        // Shadow halo: changes just outside the outline (2 to 7 px).
        var halo = 0
        for (x in left..right) for (d in 2..7) {
            listOf(top - d, bottom + d).filter { it in 0 until rest.height }.forEach { y -> halo = maxOf(halo, diff(rest.getRGB(x, y), now.getRGB(x, y))) }
        }
        for (y in top + 10..bottom - 10) for (d in 2..7) {
            listOf(left - d, right + d).filter { it in 0 until rest.width }.forEach { x -> halo = maxOf(halo, diff(rest.getRGB(x, y), now.getRGB(x, y))) }
        }
        // Tint: change of the fill inside the outline (the median, so the label does not count).
        val inner = mutableListOf<Int>()
        val fills = mutableListOf<Int>()
        for (y in top + 4..bottom - 4) for (x in left + 6..right - 6) {
            inner += diff(rest.getRGB(x, y), now.getRGB(x, y))
            fills += luma(now.getRGB(x, y))
        }
        inner.sort(); fills.sort()
        val fill = fills[fills.size / 2]
        // The label stays readable: its darkest (or, in the dark theme, brightest) stroke against the fill.
        val contrast = maxOf(fill - fills.first(), fills.last() - fill)
        return Change(halo, inner[inner.size / 2].toDouble(), contrast)
    }

    @Composable
    private fun ChipRow(prefix: String, material: Boolean) {
        var picked by remember { mutableStateOf(false) }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (material) "Material 3" else "App", Modifier.size(90.dp, 24.dp))
            if (material) {
                androidx.compose.material3.FilterChip(selected = picked, onClick = { picked = !picked }, label = { Text("Klinge (B)") }, modifier = Modifier.testTag("$prefix-filter"))
                androidx.compose.material3.AssistChip(onClick = {}, label = { Text("Hinzufügen") }, modifier = Modifier.testTag("$prefix-assist"))
                androidx.compose.material3.SuggestionChip(onClick = {}, label = { Text("Vorschlag") }, modifier = Modifier.testTag("$prefix-suggest"))
            } else {
                FilterChip(selected = picked, onClick = { picked = !picked }, label = { Text("Klinge (B)") }, modifier = Modifier.testTag("$prefix-filter"))
                AssistChip(onClick = {}, label = { Text("Hinzufügen") }, modifier = Modifier.testTag("$prefix-assist"))
                SuggestionChip(onClick = {}, label = { Text("Vorschlag") }, modifier = Modifier.testTag("$prefix-suggest"))
            }
            OutlinedButton(onClick = {}, modifier = Modifier.testTag("$prefix-button")) { Text("Teilen (S)") }
        }
    }

    private fun check(theme: String) = runSkikoComposeUiTest(Size(720f * scale, 150f * scale), Density(scale)) {
        mainClock.autoAdvance = false
        setContent {
            LlmHubTheme(AppSettings(themeMode = theme)) {
                // As in the app: on a surface, which gives the chips their content colour (and so the colour of the tint).
                Surface(Modifier.size(720.dp, 150.dp).testTag("shot"), color = MaterialTheme.colorScheme.background) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        ChipRow("m", material = true)
                        ChipRow("a", material = false)
                    }
                }
            }
        }
        settle()
        val name = theme.lowercase()
        val rest = shot("$name-0-ruhe.png")
        val kinds = listOf("filter", "assist", "suggest", "button")
        val report = StringBuilder()
        for (k in kinds) {
            for (prefix in listOf("m", "a")) {
                onNodeWithTag("$prefix-$k").performMouseInput { moveTo(center) }
                settle()
                val hovered = shot("$name-hover-$prefix-$k.png")
                val c = change(rest, hovered, box("$prefix-$k"))
                report.append("$theme $prefix-$k: Schatten ${c.halo}, Tönung ${c.tint}, Kontrast ${c.labelContrast}\n")
                if (prefix == "a") {
                    assertTrue(c.halo <= 4, "$theme $k: kein Schatten um das Element beim Überfahren (${c.halo})")
                    assertTrue(c.tint in 1.0..16.0, "$theme $k: Überfahren tönt dezent (${c.tint})")
                    assertTrue(c.labelContrast >= 90, "$theme $k: Beschriftung bleibt lesbar (${c.labelContrast})")
                }
            }
        }
        println(report)
        // A click leaves no box behind once the mouse has gone.
        onNodeWithTag("a-assist").performMouseInput { moveTo(center); press(); release() }
        settle()
        onNodeWithTag("shot").performMouseInput { moveTo(Offset(4f, 4f)) }
        settle()
        val after = shot("$name-nach-klick.png")
        val clicked = change(rest, after, box("a-assist"))
        assertTrue(clicked.halo <= 4 && clicked.tint <= 3.0, "$theme: nach dem Klick bleibt nichts stehen (${clicked.halo}, ${clicked.tint})")
        // The keyboard focus (Tab) stays clearly visible (AP-30).
        onNodeWithTag("a-assist").performKeyInput { pressKey(Key.Tab) }
        settle()
        val focused = shot("$name-tab.png")
        val moved = listOf("a-suggest", "a-button").map { change(rest, focused, box(it)).tint }
        assertTrue(moved.any { it >= 25.0 }, "$theme: der Tastaturfokus ist deutlich sichtbar ($moved)")
    }

    @Test
    fun hoverLight() = check("LIGHT")

    @Test
    fun hoverDark() = check("DARK")

    /** Screens use the app's chips (ui/Chips.kt): the ones of Material 3 directly would bring the grey box back. */
    @Test
    fun screensUseTheFlatChips() {
        val direct = Regex("""import androidx\.compose\.material3\.(FilterChip|AssistChip|SuggestionChip|InputChip)\b|androidx\.compose\.material3\.(FilterChip|AssistChip|SuggestionChip|InputChip)\(""")
        val found = listOf(File("../app/src"), File("src/main/kotlin")).flatMap { root ->
            root.walkTopDown().filter { it.isFile && it.extension == "kt" && it.name != "Chips.kt" }.flatMap { f ->
                f.readLines().withIndex().filter { direct.containsMatchIn(it.value) }.map { "${f.path}:${it.index + 1}" }.toList()
            }.toList()
        }
        assertTrue(found.isEmpty(), "Chips aus ui/Chips.kt nehmen (Import von androidx.compose.material3 entfernen):\n" + found.joinToString("\n"))
    }
}
