package io.github.thepytales.llmhub

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import io.github.thepytales.llmhub.ui.FIGURE_PERIOD
import io.github.thepytales.llmhub.ui.FIGURE_SEQUENCE
import io.github.thepytales.llmhub.ui.FigureActivity
import io.github.thepytales.llmhub.ui.FigureColors
import io.github.thepytales.llmhub.ui.drawFigure
import io.github.thepytales.llmhub.ui.figureActivity
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The working figure: every activity comes in turn and draws at every moment (also mid-transition). */
class WorkingFigureTest {
    private val colors = FigureColors(
        body = Color(0xFF00305D), visor = Color.White, eye = Color(0xFF1B1B1F), line = Color(0xFF5F6368),
        prop = Color(0xFF0B7FAB), shadow = Color(0x1F000000),
    )

    @Test
    fun cyclesThroughAllActivities() {
        val seen = (0 until FIGURE_SEQUENCE.size).map { figureActivity(it * FIGURE_PERIOD + 100).first }.toSet()
        assertEquals(FigureActivity.entries.toSet(), seen)
        assertEquals(FigureActivity.WALK, figureActivity(0).first)
    }

    @Test
    fun drawsAtEveryMoment() {
        val w = 76 * 3
        val h = 52 * 3
        // Drawn every 70 ms over two full cycles; each frame must paint the figure (not stay empty).
        var t = 0L
        while (t < FIGURE_PERIOD * FIGURE_SEQUENCE.size * 2) {
            val img = render(t, w, h)
            var painted = 0
            for (x in 0 until w step 3) for (y in 0 until h step 3) if ((img.getRGB(x, y) ushr 24) != 0) painted++
            assertTrue(painted > 100, "Figur gezeichnet bei $t ms")
            t += 70
        }
        // A contact sheet for a look at the figure (build/figure.png): each activity at four moments.
        val sheet = java.awt.image.BufferedImage(w * 4, h * FIGURE_SEQUENCE.size, java.awt.image.BufferedImage.TYPE_INT_ARGB)
        val g = sheet.createGraphics()
        g.color = java.awt.Color.WHITE
        g.fillRect(0, 0, sheet.width, sheet.height)
        FIGURE_SEQUENCE.indices.forEach { row ->
            (0 until 4).forEach { col -> g.drawImage(render(row * FIGURE_PERIOD + 400 + col * 900L, w, h), col * w, row * h, null) }
        }
        g.dispose()
        ImageIO.write(sheet, "png", File("build/figure.png"))
    }

    private fun render(t: Long, w: Int, h: Int): java.awt.image.BufferedImage {
        val bitmap = ImageBitmap(w, h)
        CanvasDrawScope().draw(Density(3f), LayoutDirection.Ltr, Canvas(bitmap), Size(w.toFloat(), h.toFloat())) { drawFigure(t, colors) }
        return bitmap.toAwtImage()
    }
}
