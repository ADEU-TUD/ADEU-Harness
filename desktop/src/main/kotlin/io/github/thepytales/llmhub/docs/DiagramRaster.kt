package io.github.thepytales.llmhub.docs

import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.geom.Ellipse2D
import java.awt.geom.Path2D
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Diagramme als Bild (AP-61), Windows: the layout of [Diagrams] drawn with Java2D into a PNG at twice the size – for
 * Word (and PowerPoint), where a diagram goes in as a picture with its connections as alternative text.
 */
object DiagramRaster {
    private fun c(hex: String) = runCatching { Color(hex.removePrefix("#").toInt(16)) }.getOrDefault(Color.GRAY)

    fun png(d: Diagram, scale: Float = 2f): ByteArray {
        val shapes = Diagrams.layout(d)
        val b = Diagrams.bounds(shapes)
        val w = (b.w * scale).toInt().coerceIn(16, 6000)
        val h = (b.h * scale).toInt().coerceIn(16, 6000)
        val img = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g.color = Color.WHITE
        g.fillRect(0, 0, w, h)
        g.scale(scale.toDouble(), scale.toDouble())
        g.translate(-b.minX.toDouble(), -b.minY.toDouble())
        val blue = c(Diagrams.TUD_BLUE)
        val font = Font("SansSerif", Font.PLAIN, 13)
        for (s in shapes) when (s) {
            is Diagrams.Title -> { g.color = blue; g.font = font.deriveFont(Font.BOLD, 15f); g.drawString(s.text, s.x, s.y) }
            is Diagrams.Axis -> { g.color = blue; g.stroke = BasicStroke(3f); g.drawLine(s.x1.toInt(), s.y.toInt(), s.x2.toInt(), s.y.toInt()) }
            is Diagrams.Edge -> {
                g.color = blue
                g.stroke = if (s.dashed) BasicStroke(1.6f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, floatArrayOf(5f, 4f), 0f) else BasicStroke(1.6f)
                g.draw(java.awt.geom.Line2D.Float(s.x1, s.y1, s.x2, s.y2))
                if (s.arrow) {
                    val ang = atan2((s.y2 - s.y1).toDouble(), (s.x2 - s.x1).toDouble())
                    val p = Path2D.Float()
                    p.moveTo(s.x2, s.y2)
                    p.lineTo(s.x2 - 9 * cos(ang - 0.42), s.y2 - 9 * sin(ang - 0.42))
                    p.lineTo(s.x2 - 9 * cos(ang + 0.42), s.y2 - 9 * sin(ang + 0.42))
                    p.closePath()
                    g.fill(p)
                }
                if (s.label.isNotBlank()) {
                    g.font = font.deriveFont(12f)
                    val fm = g.fontMetrics
                    val mx = (s.x1 + s.x2) / 2; val my = (s.y1 + s.y2) / 2
                    val tw = fm.stringWidth(s.label)
                    g.color = Color.WHITE
                    g.fillRect((mx - tw / 2 - 3).toInt(), (my - fm.ascent / 2 - 3).toInt(), tw + 6, fm.ascent + 4)
                    g.color = Color(0x33, 0x33, 0x33)
                    g.drawString(s.label, mx - tw / 2, my + fm.ascent / 2 - 2)
                }
            }
            is Diagrams.Node -> {
                val shape: java.awt.Shape = when (s.shape) {
                    "diamond" -> Path2D.Float().apply { moveTo(s.x + s.w / 2, s.y); lineTo(s.x + s.w, s.y + s.h / 2); lineTo(s.x + s.w / 2, s.y + s.h); lineTo(s.x, s.y + s.h / 2); closePath() }
                    "circle" -> Ellipse2D.Float(s.x, s.y, s.w, s.h)
                    "round" -> RoundRectangle2D.Float(s.x, s.y, s.w, s.h, 24f, 24f)
                    "stadium" -> RoundRectangle2D.Float(s.x, s.y, s.w, s.h, s.h, s.h)
                    else -> RoundRectangle2D.Float(s.x, s.y, s.w, s.h, 6f, 6f)
                }
                g.color = c(s.fill); g.fill(shape)
                g.color = c(s.stroke); g.stroke = BasicStroke(1.4f); g.draw(shape)
                g.font = font
                val fm = g.fontMetrics
                val lineH = 17f
                val start = s.y + s.h / 2 - (s.lines.size - 1) * lineH / 2 + fm.ascent / 2f - 2
                g.color = c(s.textColor)
                s.lines.forEachIndexed { i, line -> g.drawString(line, s.x + s.w / 2 - fm.stringWidth(line) / 2f, start + i * lineH) }
            }
            is Diagrams.Stroke -> {
                val pts = s.points.chunked(2).filter { it.size == 2 }
                if (pts.size > 1) {
                    val p = Path2D.Float().apply { moveTo(pts[0][0], pts[0][1]); pts.drop(1).forEach { lineTo(it[0], it[1]) } }
                    g.color = c(s.color); g.stroke = BasicStroke(s.width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND); g.draw(p)
                }
            }
        }
        g.dispose()
        return ByteArrayOutputStream().also { ImageIO.write(img, "png", it) }.toByteArray()
    }
}
