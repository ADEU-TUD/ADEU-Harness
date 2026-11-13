package io.github.thepytales.llmhub

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import io.github.thepytales.llmhub.ui.LORA_BLEND_MS
import io.github.thepytales.llmhub.ui.LoraAction
import io.github.thepytales.llmhub.ui.LoraColors
import io.github.thepytales.llmhub.ui.LoraDirector
import io.github.thepytales.llmhub.ui.LoraFrame
import io.github.thepytales.llmhub.ui.WORKING_SEQUENCE
import io.github.thepytales.llmhub.ui.drawLora
import io.github.thepytales.llmhub.ui.drawLoraFace
import io.github.thepytales.llmhub.ui.homeSequence
import java.io.File
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.Test

/**
 * Films of Lora to look at before a release (no check): every action as a loop, the start page and the work as the app
 * plays them (with the moves from one action into the next), the close-up and the small size of the chat. It only runs
 * when LORA_PREVIEW_DIR names a folder and needs ffmpeg on the PATH:
 *
 *   LORA_PREVIEW_DIR=/tmp/lora ./gradlew -p desktop test --tests '*LoraPreviewTest*'
 *
 * LORA_PREVIEW_ONLY=WAVE,IDLE limits it to these actions (and leaves out the long films), LORA_PREVIEW_FPS sets the
 * frames per second (60). The films are lossless (.mkv), one folder per look of the app (light, dark).
 *
 * LORA_STRIP_DIR=/tmp/strips writes picture strips instead (no ffmpeg needed, a few seconds): some moments of every
 * action side by side, several actions per picture – to look through all actions quickly. LORA_PREVIEW_ONLY limits the
 * actions, LORA_STRIP_N sets the moments per action (6), LORA_STRIP_W the width of one moment in pixels (260),
 * LORA_STRIP_FROM and LORA_STRIP_TO the part of the round (0 to 1), LORA_STRIP_LOOK=dark the dark look.
 */
class LoraPreviewTest {
    private class Look(val name: String, val colors: LoraColors, val card: Color, val page: Color)

    /** The ADEU look of the app, light and dark (ui/Theme.kt). */
    private val looks = listOf(
        Look(
            "light",
            LoraColors(
                body = Color(0xFF5B4FE9), visor = Color(0xFFF6F6F9), eye = Color(0xFF16161D), line = Color(0xFF53536A),
                prop = Color(0xFF8B5CF6), spark = Color(0xFFFFB300), blush = Color(0xFFFF8FA3), shadow = Color(0x1F16161D),
            ),
            card = Color.White, page = Color(0xFFF6F6F9),
        ),
        Look(
            "dark",
            LoraColors(
                body = Color(0xFF7B70FF), visor = Color(0xFF111115), eye = Color(0xFFECECF2), line = Color(0xFFA6A6B6),
                prop = Color(0xFFA77BFF), spark = Color(0xFFFFB300), blush = Color(0xFFFF8FA3), shadow = Color(0x1FECECF2),
            ),
            card = Color(0xFF1A1A20), page = Color(0xFF111115),
        ),
    )

    @Test
    fun films() {
        val dir = System.getenv("LORA_PREVIEW_DIR")?.takeIf { it.isNotBlank() }?.let(::File) ?: return
        val fps = System.getenv("LORA_PREVIEW_FPS")?.toIntOrNull() ?: 60
        val only = System.getenv("LORA_PREVIEW_ONLY")?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
        for (look in looks) {
            val out = File(dir, look.name).apply { mkdirs() }
            for (action in LoraAction.entries) {
                if (only.isNotEmpty() && action.name !in only) continue
                // As a loop: the end of the round moves into its beginning, as when the app repeats an action.
                film(File(out, "${action.name}.mkv"), 560, 392, fps, action.periodMs, look.card) { t ->
                    drawLora(LoraFrame(action, t / action.periodMs.toFloat(), t, action, (t / LORA_BLEND_MS.toFloat()).coerceAtMost(1f)), 20_000 + t, look.colors)
                }
            }
            if (only.isNotEmpty()) continue
            // The start page (it greets first) and the work, as the app plays them.
            val home = LoraDirector(homeSequence(12), LoraAction.WAVE, 1500)
            film(File(out, "reel-home.mkv"), 560, 392, fps, 60_000, look.page) { t -> drawLora(home.frame(t), t, look.colors) }
            val work = LoraDirector(WORKING_SEQUENCE, LoraAction.WALK, 1500)
            film(File(out, "reel-work.mkv"), 560, 392, fps, 75_000, look.card) { t -> drawLora(work.frame(t), t, look.colors) }
            // The close-up of small places (the button in the top bar, the sleeping Lora in a corner).
            val face = LoraDirector(listOf(LoraAction.IDLE, LoraAction.WAVE, LoraAction.LOOK, LoraAction.NOD, LoraAction.SLEEP), LoraAction.IDLE, 1500)
            film(File(out, "face.mkv"), 320, 320, fps, 22_400, look.card) { t -> drawLoraFace(face.frame(t), t, look.colors) }
            // The whole figure as small as under an answer of the chat (30 × 21 dp, here at four times the pixels).
            val small = LoraDirector(listOf(LoraAction.CHEER, LoraAction.IDLE, LoraAction.NOD, LoraAction.WAVE), LoraAction.CHEER, 1500)
            film(File(out, "small.mkv"), 120, 84, fps, 13_800, look.card) { t -> drawLora(small.frame(t), t, look.colors) }
        }
    }

    @Test
    fun strips() {
        val dir = System.getenv("LORA_STRIP_DIR")?.takeIf { it.isNotBlank() }?.let(::File) ?: return
        fun number(name: String, default: Float) = System.getenv(name)?.toFloatOrNull() ?: default
        val only = System.getenv("LORA_PREVIEW_ONLY")?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
        val n = number("LORA_STRIP_N", 6f).toInt()
        val w = number("LORA_STRIP_W", 260f).toInt()
        val h = w * 7 / 10
        val from = number("LORA_STRIP_FROM", 0f)
        val to = number("LORA_STRIP_TO", 1f)
        val look = looks.first { it.name == (System.getenv("LORA_STRIP_LOOK") ?: "light") }
        val cols = minOf(n, 1560 / w)
        val rowsOfAction = (n + cols - 1) / cols
        val actions = LoraAction.entries.filter { only.isEmpty() || it.name in only }
        dir.mkdirs()
        actions.chunked(maxOf(1, 4 / rowsOfAction)).forEachIndexed { index, chunk ->
            val img = java.awt.image.BufferedImage(w * cols, h * rowsOfAction * chunk.size, java.awt.image.BufferedImage.TYPE_INT_RGB)
            val g = img.createGraphics()
            chunk.forEachIndexed { row, action ->
                for (i in 0 until n) {
                    val phase = from + (to - from) * (i + 0.5f) / n
                    val t = (phase * action.periodMs).toLong()
                    val bitmap = ImageBitmap(w, h)
                    CanvasDrawScope().draw(Density(w / 280f), LayoutDirection.Ltr, Canvas(bitmap), Size(w.toFloat(), h.toFloat())) {
                        drawRect(look.card)
                        drawLora(LoraFrame(action, phase, t, action, (t / LORA_BLEND_MS.toFloat()).coerceAtMost(1f)), 20_000 + t, look.colors)
                    }
                    val x = (i % cols) * w
                    val y = (row * rowsOfAction + i / cols) * h
                    g.drawImage(bitmap.toAwtImage(), x, y, null)
                    g.color = java.awt.Color.GRAY
                    g.drawString(if (i == 0) "${action.name} %.2f".format(phase) else "%.2f".format(phase), x + 4, y + 12)
                }
            }
            g.dispose()
            javax.imageio.ImageIO.write(img, "png", File(dir, "strip-${look.name}-%02d.png".format(index)))
        }
    }

    /** A film of [durationMs] at [fps]: [draw] paints the moment [t] (ms) on [background]. */
    private fun film(file: File, w: Int, h: Int, fps: Int, durationMs: Long, background: Color, draw: DrawScope.(t: Long) -> Unit) {
        val process = ProcessBuilder(
            "ffmpeg", "-loglevel", "error", "-y", "-f", "rawvideo", "-pix_fmt", "bgra", "-s", "${w}x$h", "-r", "$fps", "-i", "-",
            "-c:v", "libx264rgb", "-qp", "0", "-preset", "veryfast", file.path,
        ).redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.INHERIT).start()
        val bitmap = ImageBitmap(w, h)
        val canvas = Canvas(bitmap)
        val scope = CanvasDrawScope()
        val pixels = IntArray(w * h)
        val bytes = ByteBuffer.allocate(w * h * 4).order(ByteOrder.LITTLE_ENDIAN)
        process.outputStream.buffered(1 shl 20).use { pipe: OutputStream ->
            val frames = (durationMs * fps / 1000).toInt()
            for (i in 0 until frames) {
                val t = i * 1000L / fps
                scope.draw(Density(2f), LayoutDirection.Ltr, canvas, Size(w.toFloat(), h.toFloat())) {
                    drawRect(background)
                    draw(t)
                }
                bitmap.readPixels(pixels)
                bytes.asIntBuffer().put(pixels)
                pipe.write(bytes.array())
            }
        }
        check(process.waitFor() == 0) { "ffmpeg: ${file.name}" }
    }
}
