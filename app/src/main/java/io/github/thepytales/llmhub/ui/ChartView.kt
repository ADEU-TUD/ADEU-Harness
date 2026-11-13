package io.github.thepytales.llmhub.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.thepytales.llmhub.docs.ChartLayout
import io.github.thepytales.llmhub.docs.ChartSpec

/** Draws a chart (the same layout as in PDF documents); the data table can be shown below it. */
@Composable
fun ChartView(spec: ChartSpec, modifier: Modifier = Modifier) {
    var table by remember { mutableStateOf(false) }
    Surface(color = Color.White, shape = MaterialTheme.shapes.medium, modifier = modifier.fillMaxWidth().widthIn(max = 640.dp)) {
        Column {
            ChartCanvas(spec, Modifier.fillMaxWidth().padding(4.dp))
            TextButton(onClick = { table = !table }) { Text(if (table) tr("Daten ausblenden") else tr("Daten als Tabelle"), color = Color(0xFF1E40AF)) }
            if (table) MarkdownText(spec.table(), Modifier.padding(horizontal = 12.dp, vertical = 4.dp), color = Color(0xFF1A1A1A))
        }
    }
}

/** The chart drawing alone (chat, documents, slides); its height follows the width. */
@Composable
fun ChartCanvas(spec: ChartSpec, modifier: Modifier = Modifier) {
    val shapes = remember(spec) { ChartLayout.layout(spec) }
    val measurer = rememberTextMeasurer()
    Canvas(
        modifier.aspectRatio(ChartLayout.W / ChartLayout.H)
            .semantics { contentDescription = tr("Diagramm: {0}. Daten: ", spec.title.ifBlank { spec.type.name }) + spec.table().replace("|", " ").take(600) },
    ) {
        val k = size.width / ChartLayout.W
        fun c(hex: String) = runCatching { Color(0xFF000000 or hex.removePrefix("#").toLong(16)) }.getOrDefault(Color.Gray)
        shapes.forEach { s ->
            when (s) {
                is ChartLayout.Rect -> drawRect(c(s.color).copy(alpha = s.opacity), Offset(s.x * k, s.y * k), Size(maxOf(s.w, 0f) * k, maxOf(s.h, 0f) * k))
                is ChartLayout.Line -> drawLine(c(s.color), Offset(s.x1 * k, s.y1 * k), Offset(s.x2 * k, s.y2 * k), s.width * k)
                is ChartLayout.Poly -> {
                    val p = Path()
                    s.points.forEachIndexed { i, (x, y) -> if (i == 0) p.moveTo(x * k, y * k) else p.lineTo(x * k, y * k) }
                    drawPath(p, c(s.color), style = Stroke(s.width * k))
                }
                is ChartLayout.Dot -> drawCircle(c(s.color), s.r * k, Offset(s.x * k, s.y * k))
                is ChartLayout.Wedge -> drawArc(c(s.color), s.start, s.sweep, true, Offset((s.cx - s.r) * k, (s.cy - s.r) * k), Size(2 * s.r * k, 2 * s.r * k))
                is ChartLayout.Label -> {
                    val layout = measurer.measure(s.text, TextStyle(fontSize = (s.size * k / (density * fontScale)).sp, color = c(s.color), fontWeight = if (s.bold) FontWeight.Bold else null))
                    val w = layout.size.width.toFloat()
                    val h = layout.size.height.toFloat()
                    val x = when (s.anchor) { "start" -> s.x * k; "end" -> s.x * k - w; else -> s.x * k - w / 2 }
                    val y = s.y * k - h * 0.78f
                    if (s.vertical) rotate(-90f, Offset(s.x * k, s.y * k)) { drawText(layout, topLeft = Offset(s.x * k - w / 2, s.y * k - h / 2)) }
                    else drawText(layout, topLeft = Offset(x, y))
                }
            }
        }
    }
}
