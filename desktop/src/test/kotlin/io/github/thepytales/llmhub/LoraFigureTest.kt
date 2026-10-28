package io.github.thepytales.llmhub

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import io.github.thepytales.llmhub.ui.LORA_BLEND_MS
import io.github.thepytales.llmhub.ui.LoraAction
import io.github.thepytales.llmhub.ui.LoraColors
import io.github.thepytales.llmhub.ui.LoraDirector
import io.github.thepytales.llmhub.ui.LoraFrame
import io.github.thepytales.llmhub.ui.WORKING_SEQUENCE
import io.github.thepytales.llmhub.ui.HOME_SEQUENCE
import io.github.thepytales.llmhub.ui.drawLora
import io.github.thepytales.llmhub.ui.loraHint
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Lora, the mascot: the director plays every activity, follows hints, and every moment draws inside the canvas. */
class LoraFigureTest {
    private val light = LoraColors(
        body = Color(0xFF5B4FE9), visor = Color.White, eye = Color(0xFF1B1B1F), line = Color(0xFF5F6368),
        prop = Color(0xFF0B7FAB), spark = Color(0xFFFFB300), blush = Color(0xFFFF8FA3), shadow = Color(0x1F000000),
    )
    private val dark = LoraColors(
        body = Color(0xFF7B70FF), visor = Color(0xFF1C1B1F), eye = Color(0xFFE6E1E5), line = Color(0xFFC4C7C5),
        prop = Color(0xFF7FD0F0), spark = Color(0xFFFFB300), blush = Color(0xFFFF8FA3), shadow = Color(0x33FFFFFF),
    )

    private fun director(sequence: List<LoraAction> = WORKING_SEQUENCE, start: LoraAction = sequence.first(), dwell: Long = 1500) =
        LoraDirector(sequence, start, dwell)

    /** Runs the director frame by frame (every 16 ms) and returns the action at each step. */
    private fun LoraDirector.run(fromMs: Long, toMs: Long): List<Pair<Long, LoraFrame>> =
        (fromMs..toMs step 16).map { it to frame(it) }

    @Test
    fun cyclesThroughAllWorkingActions() {
        val d = director()
        assertEquals(LoraAction.WALK, d.frame(0).action, "beginnt mit dem Laufen")
        val total = WORKING_SEQUENCE.sumOf { it.periodMs }
        val seen = d.run(0, total + 100).map { it.second.action }
        assertEquals(WORKING_SEQUENCE.toSet(), seen.toSet(), "jede Aktion kommt vor")
        // In the order of the sequence, each once per round.
        assertEquals(WORKING_SEQUENCE, seen.distinctConsecutive().take(WORKING_SEQUENCE.size), "Reihenfolge")
        // The home sequence starts with the greeting.
        assertEquals(LoraAction.WAVE, director(HOME_SEQUENCE, LoraAction.WAVE).frame(0).action, "Startseite winkt zuerst")
    }

    @Test
    fun freshHintSwitchesAfterDwellThenComesPeriodically() {
        val d = director(dwell = 1500)
        d.frame(0)
        d.frame(500)
        d.hint(listOf(LoraAction.SEARCH, LoraAction.TELESCOPE), every = 3)
        assertEquals(LoraAction.WALK, d.frame(1400).action, "vor der Mindestdauer bleibt die Aktion")
        assertEquals(LoraAction.SEARCH, d.frame(1520).action, "danach kommt der Hinweis sofort")
        // Then every third round one of the hinted actions, the two in turn.
        val rounds = d.run(1520, 1520 + 40_000).map { it.second.action }.distinctConsecutive()
        val hinted = rounds.withIndex().filter { it.value == LoraAction.SEARCH || it.value == LoraAction.TELESCOPE }
        assertTrue(hinted.size >= 3, "Hinweis kommt wieder: $rounds")
        assertTrue(rounds.any { it == LoraAction.TELESCOPE }, "beide Hinweise abwechselnd: $rounds")
        assertTrue(rounds.any { it !in setOf(LoraAction.SEARCH, LoraAction.TELESCOPE) }, "dazwischen die übrigen Aktionen")
        hinted.zipWithNext().forEach { (a, b) -> assertTrue(b.index - a.index <= 3, "höchstens drei Runden Abstand: $rounds") }
        // A hint that matches the current action does not cut it short.
        val e = director(dwell = 100)
        e.frame(0)
        e.hint(listOf(LoraAction.WALK))
        assertEquals(LoraAction.WALK, e.frame(2000).action, "passende Aktion läuft weiter")
        assertEquals(2000L, e.frame(2000).elapsedMs, "nicht neu gestartet")
    }

    @Test
    fun playSwitchesAtOnceAndBlends() {
        val d = director(HOME_SEQUENCE, LoraAction.LOOK)
        d.frame(0)
        d.frame(1000)
        d.play(LoraAction.EUREKA)
        val first = d.frame(1000)
        assertEquals(LoraAction.EUREKA, first.action, "sofort umgeschaltet")
        assertEquals(LoraAction.LOOK, first.previous, "blendet aus der vorigen Aktion")
        assertEquals(0f, first.blend, "Überblendung beginnt")
        val mid = d.frame(1000 + LORA_BLEND_MS / 2)
        assertTrue(mid.blend in 0.4f..0.6f, "halb übergeblendet: ${mid.blend}")
        val done = d.frame(1000 + LORA_BLEND_MS)
        assertEquals(1f, done.blend, "fertig übergeblendet")
        assertNull(done.previous, "keine vorige Aktion mehr")
        // After the action, the sequence goes on.
        val after = d.frame(1000 + LoraAction.EUREKA.periodMs + 20)
        assertTrue(after.action != LoraAction.EUREKA, "danach geht der Ablauf weiter")
    }

    @Test
    fun singleHintEveryRoundRepeats() {
        val d = director(HOME_SEQUENCE, LoraAction.LOOK, dwell = 600)
        d.frame(0)
        d.hint(listOf(LoraAction.SLEEP), every = 1)
        val actions = d.run(0, 40_000).filter { it.first >= 700 }.map { it.second.action }.toSet()
        assertEquals(setOf(LoraAction.SLEEP), actions, "schläft durch")
        // Waking up: without the hint the sequence comes back.
        d.hint(emptyList())
        val later = d.run(40_016, 60_000).map { it.second.action }.toSet()
        assertTrue(later.any { it != LoraAction.SLEEP }, "wacht wieder auf")
    }

    @Test
    fun hintsForTools() {
        assertEquals(listOf(LoraAction.SEARCH, LoraAction.TELESCOPE), loraHint("web_search", false))
        assertEquals(listOf(LoraAction.BOOKS, LoraAction.READ), loraHint("search_literature", false))
        assertEquals(listOf(LoraAction.READ, LoraAction.MICROSCOPE), loraHint("read_document", false))
        assertEquals(listOf(LoraAction.FLASK, LoraAction.CHART), loraHint("run_javascript", false))
        assertEquals(listOf(LoraAction.TYPE, LoraAction.NOTES), loraHint("create_document", false))
        assertEquals(listOf(LoraAction.CHART), loraHint("create_chart", false))
        assertEquals(listOf(LoraAction.THINK, LoraAction.EUREKA), loraHint(null, true))
        assertEquals(emptyList(), loraHint(null, false))
        assertEquals(emptyList(), loraHint("unbekanntes_werkzeug", false))
    }

    @Test
    fun drawsInsideTheCanvasAtEveryMoment() {
        // Card size (110 × 77 dp at density 3) and the size in the voice mode.
        for ((w, h) in listOf(330 to 231, 570 to 399)) {
            for (action in LoraAction.entries) {
                var t = 0L
                while (t <= action.periodMs) {
                    val frame = LoraFrame(action, t / action.periodMs.toFloat(), t, null, 1f)
                    check(render(frame, 7_000 + t, w, h, light), "$action bei $t ms ($w×$h)")
                    t += 90
                }
                // Moving in from every other action (half blended), early and late in the round.
                for (other in LoraAction.entries) {
                    for (phase in listOf(0.05f, 0.5f)) {
                        val frame = LoraFrame(action, phase, (phase * action.periodMs).toLong(), other, 0.5f)
                        check(render(frame, 3_300, w, h, light), "$other → $action ($w×$h)")
                    }
                }
            }
        }
        sheet(light, "build/lora.png")
        sheet(dark, "build/lora-dark.png")
    }

    /** Some of Lora is painted, nothing touches the border of the canvas. */
    private fun check(img: BufferedImage, what: String) {
        var painted = 0
        for (x in 0 until img.width step 3) for (y in 0 until img.height step 3) if ((img.getRGB(x, y) ushr 24) != 0) painted++
        assertTrue(painted > 100, "Lora gezeichnet: $what")
        for (x in 0 until img.width) for (y in listOf(0, img.height - 1)) assertTrue((img.getRGB(x, y) ushr 24) == 0, "bleibt im Bild (oben/unten): $what")
        for (y in 0 until img.height) for (x in listOf(0, img.width - 1)) assertTrue((img.getRGB(x, y) ushr 24) == 0, "bleibt im Bild (links/rechts): $what")
    }

    /** A contact sheet to look at: each action a row, five moments of its round (plus a blend at the start). */
    private fun sheet(colors: LoraColors, path: String) {
        val w = 330
        val h = 231
        val cols = 6
        val actions = LoraAction.entries
        val img = BufferedImage(w * cols, h * actions.size, BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        g.color = if (colors == dark) java.awt.Color(0x1C, 0x1B, 0x1F) else java.awt.Color.WHITE
        g.fillRect(0, 0, img.width, img.height)
        actions.forEachIndexed { row, action ->
            val prev = actions[(row + actions.size - 1) % actions.size]
            g.drawImage(render(LoraFrame(action, 0.03f, 150, prev, 0.4f), 2_150, w, h, colors), 0, row * h, null)
            listOf(0.12f, 0.35f, 0.58f, 0.78f, 0.94f).forEachIndexed { i, phase ->
                val t = (phase * action.periodMs).toLong()
                g.drawImage(render(LoraFrame(action, phase, t, null, 1f), 2_000 + t, w, h, colors), (i + 1) * w, row * h, null)
            }
            g.color = java.awt.Color.GRAY
            g.drawString(action.name, 6, row * h + 14)
        }
        g.dispose()
        File(path).parentFile?.mkdirs()
        ImageIO.write(img, "png", File(path))
    }

    private fun render(frame: LoraFrame, timeMs: Long, w: Int, h: Int, colors: LoraColors = light): BufferedImage {
        val bitmap = ImageBitmap(w, h)
        CanvasDrawScope().draw(Density(3f), LayoutDirection.Ltr, Canvas(bitmap), Size(w.toFloat(), h.toFloat())) { drawLora(frame, timeMs, colors) }
        return bitmap.toAwtImage()
    }

    private fun <T> List<T>.distinctConsecutive(): List<T> = fold(mutableListOf()) { out, x -> if (out.lastOrNull() != x) out.add(x); out }
}
