package io.github.thepytales.llmhub.creative

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.thepytales.llmhub.data.DesignElement
import io.github.thepytales.llmhub.data.DesignFormat
import io.github.thepytales.llmhub.data.ElementKind
import io.github.thepytales.llmhub.docs.TudBrand

/**
 * Elements of the shared canvas (AP-50) whose drawing lives in the renderer of Gestalten: the TUD-Shape, symbols, QR
 * codes and tables. Android draws the TUD-Shape and tables itself and shows symbols and QR codes as a placeholder (it
 * has no QR encoder and not the TUD icon sets); Windows has its own version of this file that uses the renderer of
 * Gestalten, so an element looks the same on a slide and in a design.
 */
object CanvasArt {
    /** Whether symbols and QR codes can be inserted and drawn here (Windows). */
    const val FULL = false

    /** PNG of [e] for PowerPoint (symbols and QR codes on slides); null where the platform cannot draw it. */
    @Suppress("UNUSED_PARAMETER")
    fun png(e: DesignElement, pxPerMm: Float): ByteArray? = null
}

/** [e] (in its own box, without position and rotation) filling [modifier]. */
@Composable
fun CanvasArtView(e: DesignElement, modifier: Modifier = Modifier) {
    when (e.kind) {
        ElementKind.TUD_SHAPE -> Canvas(modifier.fillMaxSize()) {
            val c = colorOf(e.fill.ifBlank { e.color }) ?: Color(0xFF00008C)
            val side = minOf(size.width, size.height)
            val ox = (size.width - side) / 2
            val oy = (size.height - side) / 2
            val p = Path()
            TudBrand.SHAPE.forEach { poly ->
                p.moveTo(ox + poly[0] * side / 600f, oy + poly[1] * side / 600f)
                for (i in 2 until poly.size step 2) p.lineTo(ox + poly[i] * side / 600f, oy + poly[i + 1] * side / 600f)
                p.close()
            }
            scale(if (e.flipX) -1f else 1f, 1f, Offset(size.width / 2, size.height / 2)) { drawPath(p, c) }
        }
        ElementKind.TABLE -> CanvasTableView(e, modifier)
        else -> Box(modifier.fillMaxSize().background(Color(0x1A00008C)), contentAlignment = Alignment.Center) {
            Text(if (e.kind == ElementKind.QR) "QR" else "★", color = Color(0xFF00008C))
        }
    }
}

/** A table of the canvas as Compose layout (Android; Windows draws it with the renderer of Gestalten). */
@Composable
internal fun CanvasTableView(e: DesignElement, modifier: Modifier) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val pxPerMm = with(density) { maxWidth.toPx() } / e.w.coerceAtLeast(0.1f)
        val rows = CanvasTable.normalized(e.rows)
        val line = colorOf(CanvasTable.LINE) ?: Color.LightGray
        Column(Modifier.fillMaxSize().border(0.5.dp, line)) {
            rows.forEachIndexed { r, row ->
                Row(Modifier.fillMaxWidth().weight(1f).background(colorOf(CanvasTable.fill(e, r)) ?: Color.Transparent)) {
                    row.forEach { cell ->
                        Box(Modifier.weight(1f).fillMaxHeight().border(0.25.dp, line).padding(with(density) { (CanvasTable.PAD * pxPerMm).toDp() }), contentAlignment = Alignment.CenterStart) {
                            Text(
                                FieldRich.annotated(cell, rich = true), maxLines = 3, overflow = TextOverflow.Ellipsis,
                                style = TextStyle(
                                    color = colorOf(CanvasTable.textColor(e, r)) ?: Color.Black,
                                    fontSize = with(density) { (e.fontSize * DesignFormat.MM_PER_PT * pxPerMm).toSp() },
                                    fontWeight = if (CanvasTable.bold(e, r)) FontWeight.Bold else FontWeight.Normal,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Picker for the symbols of Gestalten (Material symbols and TUD icons); Android has none ([CanvasArt.FULL]). */
@Composable
@Suppress("UNUSED_PARAMETER")
fun CanvasSymbolPicker(onDismiss: () -> Unit, onPick: (String) -> Unit) {
    androidx.compose.runtime.LaunchedEffect(Unit) { onDismiss() }
}

private fun colorOf(hex: String): Color? = hex.trim().removePrefix("#").takeIf { it.length == 6 }?.toLongOrNull(16)?.let { Color(0xFF000000 or it) }
