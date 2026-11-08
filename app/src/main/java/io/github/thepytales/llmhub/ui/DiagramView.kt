package io.github.thepytales.llmhub.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.thepytales.llmhub.docs.Diagram
import io.github.thepytales.llmhub.docs.Diagrams
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

internal fun diagramColor(hex: String): Color = runCatching { Color(0xFF000000 or hex.removePrefix("#").toLong(16)) }.getOrDefault(Color.Gray)

/**
 * Draws the [shapes] of a diagram (AP-61) at scale [k] with [origin] (diagram units) at the top left – the same layout
 * as the SVG of the PDF. [selected]: the id of a node drawn with a frame.
 */
internal fun DrawScope.drawDiagram(shapes: List<Diagrams.Shape>, measurer: TextMeasurer, k: Float, origin: Offset, selected: String? = null) {
    fun p(x: Float, y: Float) = Offset((x - origin.x) * k, (y - origin.y) * k)
    val blue = diagramColor(Diagrams.TUD_BLUE)
    for (s in shapes) when (s) {
        is Diagrams.Title -> drawText(measurer, s.text, p(s.x, s.y - 16f), TextStyle(color = blue, fontSize = (15f * k).sp, fontWeight = FontWeight.Bold))
        is Diagrams.Axis -> drawLine(blue, p(s.x1, s.y), p(s.x2, s.y), strokeWidth = 3f * k)
        is Diagrams.Edge -> {
            val a = p(s.x1, s.y1); val b = p(s.x2, s.y2)
            drawLine(blue, a, b, strokeWidth = 1.6f * k, pathEffect = if (s.dashed) PathEffect.dashPathEffect(floatArrayOf(5f * k, 4f * k)) else null)
            if (s.arrow) {
                val ang = atan2(b.y - a.y, b.x - a.x)
                val len = 9f * k
                val path = Path().apply {
                    moveTo(b.x, b.y)
                    lineTo(b.x - len * cos(ang - 0.42f), b.y - len * sin(ang - 0.42f))
                    lineTo(b.x - len * cos(ang + 0.42f), b.y - len * sin(ang + 0.42f))
                    close()
                }
                drawPath(path, blue)
            }
            if (s.label.isNotBlank()) {
                val layout = measurer.measure(s.label, TextStyle(color = Color(0xFF333333), fontSize = (12f * k).sp))
                val mid = Offset((a.x + b.x) / 2, (a.y + b.y) / 2)
                val tl = Offset(mid.x - layout.size.width / 2f, mid.y - layout.size.height / 2f)
                drawRect(Color.White, tl - Offset(3f, 1f), Size(layout.size.width + 6f, layout.size.height + 2f))
                drawText(layout, topLeft = tl)
            }
        }
        is Diagrams.Node -> {
            val tl = p(s.x, s.y)
            val size = Size(s.w * k, s.h * k)
            val fill = diagramColor(s.fill)
            val stroke = diagramColor(s.stroke)
            val line = Stroke(width = (if (s.id == selected) 3f else 1.4f) * k)
            val frame = if (s.id == selected) Color(0xFFF5A200) else stroke
            when (s.shape) {
                "diamond" -> {
                    val path = Path().apply { moveTo(tl.x + size.width / 2, tl.y); lineTo(tl.x + size.width, tl.y + size.height / 2); lineTo(tl.x + size.width / 2, tl.y + size.height); lineTo(tl.x, tl.y + size.height / 2); close() }
                    drawPath(path, fill); drawPath(path, frame, style = line)
                }
                "circle" -> { drawOval(fill, tl, size); drawOval(frame, tl, size, style = line) }
                "round", "stadium" -> {
                    val r = if (s.shape == "stadium") size.height / 2 else 12f * k
                    drawRoundRect(fill, tl, size, CornerRadius(r)); drawRoundRect(frame, tl, size, CornerRadius(r), style = line)
                }
                "note" -> {
                    drawRect(Color(0x22000000), tl + Offset(3f * k, 3f * k), size)
                    drawRect(fill, tl, size); drawRect(frame, tl, size, style = line)
                }
                else -> { drawRoundRect(fill, tl, size, CornerRadius(3f * k)); drawRoundRect(frame, tl, size, CornerRadius(3f * k), style = line) }
            }
            val text = s.lines.joinToString("\n")
            if (text.isNotBlank()) {
                val layout = measurer.measure(text, TextStyle(color = diagramColor(s.textColor), fontSize = (13f * k).sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center, lineHeight = (17f * k).sp))
                drawText(layout, topLeft = Offset(tl.x + (size.width - layout.size.width) / 2, tl.y + (size.height - layout.size.height) / 2))
            }
        }
        is Diagrams.Stroke -> {
            val pts = s.points.chunked(2).filter { it.size == 2 }.map { p(it[0], it[1]) }
            if (pts.size > 1) {
                val path = Path().apply { moveTo(pts[0].x, pts[0].y); pts.drop(1).forEach { lineTo(it.x, it.y) } }
                drawPath(path, diagramColor(s.color), style = Stroke(width = s.width * k, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}

/** A diagram as a picture whose height follows the width (documents, chat, slides). */
@Composable
fun DiagramCanvas(diagram: Diagram, modifier: Modifier = Modifier) {
    val shapes = remember(diagram) { Diagrams.layout(diagram) }
    val bounds = remember(shapes) { Diagrams.bounds(shapes) }
    val measurer = rememberTextMeasurer()
    Canvas(modifier.aspectRatio((bounds.w / bounds.h).coerceIn(0.3f, 6f)).semantics { contentDescription = Diagrams.altText(diagram) }) {
        val k = minOf(size.width / bounds.w, size.height / bounds.h)
        drawDiagram(shapes, measurer, k, Offset(bounds.minX, bounds.minY))
    }
}

/** A diagram block in a document or a chat answer: white ground, at most 720 dp wide. */
@Composable
fun DiagramView(diagram: Diagram, modifier: Modifier = Modifier) {
    val shapes = remember(diagram) { Diagrams.layout(diagram) }
    val b = remember(shapes) { Diagrams.bounds(shapes) }
    Surface(color = Color.White, shape = MaterialTheme.shapes.medium, modifier = modifier.fillMaxWidth().widthIn(max = (b.w * 0.9f).coerceIn(240f, 720f).dp)) {
        DiagramCanvas(diagram, Modifier.fillMaxWidth())
    }
}
