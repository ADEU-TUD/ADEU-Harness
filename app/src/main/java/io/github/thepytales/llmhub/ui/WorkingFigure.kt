package io.github.thepytales.llmhub.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** What the little figure does while the agent works; it changes every few seconds. */
enum class FigureActivity { WALK, READ, TYPE, JUGGLE, SEARCH, DANCE, THINK }

/** Order of the activities: every activity once, starting with walking, then a fixed but varied sequence. */
internal val FIGURE_SEQUENCE = listOf(
    FigureActivity.WALK, FigureActivity.READ, FigureActivity.JUGGLE, FigureActivity.TYPE,
    FigureActivity.DANCE, FigureActivity.SEARCH, FigureActivity.THINK,
)

/** Milliseconds per activity. */
internal const val FIGURE_PERIOD = 4200L

/** The activity at [timeMs] since the figure appeared and the phase (0..1) within it. */
internal fun figureActivity(timeMs: Long): Pair<FigureActivity, Float> {
    val n = (timeMs / FIGURE_PERIOD).toInt()
    return FIGURE_SEQUENCE[n % FIGURE_SEQUENCE.size] to (timeMs % FIGURE_PERIOD) / FIGURE_PERIOD.toFloat()
}

/**
 * Shown instead of the thinking and the tool steps while the agent works (Einstellungen › Chat & Werkzeuge ›
 * Arbeitsschritte einklappen): a small figure that walks, reads, types, juggles, searches, dances or thinks, the
 * sentence „Der Agent arbeitet an einer Antwort, bitte warte kurz.“, the current step and a button for the details.
 */
@Composable
fun WorkingCard(step: String?, stepsCount: Int, open: Boolean, onToggle: (() -> Unit)?) {
    Surface(
        color = LocalChatLook.current.card,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(start = 8.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            WorkingFigure(Modifier.size(width = 76.dp, height = 52.dp))
            Spacer(Modifier.size(8.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(tr("Der Agent arbeitet an einer Antwort, bitte warte kurz."), style = MaterialTheme.typography.bodyMedium)
                if (step != null) {
                    Text(
                        step,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (onToggle != null && stepsCount > 0) {
                TextButton(onClick = onToggle) {
                    Text(if (open) tr("Ausblenden") else tr("Schritte") + " ($stepsCount)", style = MaterialTheme.typography.labelMedium)
                    Icon(if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, contentDescription = null, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

/** A finished turn with folded steps: one quiet line „Gedankengang und 4 Arbeitsschritte“ that opens them. */
@Composable
fun StepsLine(thinking: Boolean, tools: Int, open: Boolean, onToggle: () -> Unit) {
    val parts = buildList {
        if (thinking) add(tr("Gedankengang"))
        if (tools == 1) add(tr("1 Arbeitsschritt")) else if (tools > 1) add("$tools " + tr("Arbeitsschritte"))
    }
    if (parts.isEmpty()) return
    TextButton(onClick = onToggle, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 0.dp)) {
        Text(
            parts.joinToString(" " + tr("und") + " "),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Icon(
            if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, contentDescription = if (open) tr("Einklappen") else tr("Anzeigen"),
            modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The figure: a small rounded robot in the brand color with a light visor and two eyes that blink. Drawn on a canvas
 * (no pictures, no emoji), calm motion with eased curves, so it stays sober in every style.
 */
@Composable
fun WorkingFigure(modifier: Modifier = Modifier) {
    val time by produceState(0L) {
        val start = withFrameMillis { it }
        while (true) withFrameMillis { value = it - start }
    }
    val look = LocalChatLook.current
    val colors = FigureColors(
        body = look.brand,
        visor = MaterialTheme.colorScheme.surface,
        eye = MaterialTheme.colorScheme.onSurface,
        line = MaterialTheme.colorScheme.onSurfaceVariant,
        prop = MaterialTheme.colorScheme.tertiary,
        shadow = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
    )
    val label = tr("Animierte Figur: der Agent arbeitet")
    Canvas(modifier.semantics { contentDescription = label }) { drawFigure(time, colors) }
}

internal data class FigureColors(val body: Color, val visor: Color, val eye: Color, val line: Color, val prop: Color, val shadow: Color)

private fun ease(x: Float): Float = (0.5f - 0.5f * cos(PI * x.coerceIn(0f, 1f))).toFloat()
private fun wave(t: Float, periods: Float) = sin(2 * PI * t * periods).toFloat()

/** Draws the figure at [timeMs]; split out so the drawing can run without an animation clock. */
internal fun DrawScope.drawFigure(timeMs: Long, c: FigureColors) {
    val (activity, p) = figureActivity(timeMs)
    val u = size.height / 52f
    val cx0 = size.width / 2
    val ground = size.height - 4 * u
    // Props fade in and out at the start and end of each activity.
    val fade = ease((p / 0.12f).coerceAtMost(1f)) * ease(((1 - p) / 0.12f).coerceAtMost(1f))
    val secs = timeMs / 1000f

    var dx = 0f
    var hop = 0f
    var tilt = 0f
    var look = 0f
    var legSwing = 0f
    // Arm angles in degrees from hanging down (positive = raised to the side).
    var armL = 12f
    var armR = 12f
    when (activity) {
        FigureActivity.WALK -> {
            dx = wave(p, 1f) * size.width * 0.28f
            val dir = cos(2 * PI * p).toFloat()
            look = dir.coerceIn(-1f, 1f)
            legSwing = wave(secs, 2.2f)
            hop = abs(wave(secs, 2.2f)) * 1.6f * u
            armL = 12f + 22f * legSwing
            armR = 12f - 22f * legSwing
        }
        FigureActivity.READ -> {
            look = -0.6f + 1.2f * ((secs * 0.8f) % 1f)
            armL = -28f; armR = -28f
        }
        FigureActivity.TYPE -> {
            look = 0.1f * wave(secs, 0.5f)
            armL = -38f + 9f * wave(secs, 4f)
            armR = -38f - 9f * wave(secs, 4f)
        }
        FigureActivity.JUGGLE -> {
            armL = 60f + 25f * wave(secs, 1.6f)
            armR = 60f - 25f * wave(secs, 1.6f)
            look = 0f
        }
        FigureActivity.SEARCH -> {
            look = wave(secs, 0.4f)
            armR = 95f + 15f * wave(secs, 0.8f)
            armL = 14f
            dx = wave(p, 1f) * size.width * 0.08f
        }
        FigureActivity.DANCE -> {
            tilt = 9f * wave(secs, 1.2f)
            hop = abs(wave(secs, 1.2f)) * 3f * u
            armL = 120f + 35f * wave(secs, 1.2f)
            armR = 120f - 35f * wave(secs, 1.2f)
            legSwing = 0.6f * wave(secs, 1.2f)
        }
        FigureActivity.THINK -> {
            look = 0.5f
            armR = -62f + 4f * wave(secs, 0.7f)
            armL = 14f
            tilt = -4f
        }
    }
    val cx = cx0 + dx
    val bodyW = 22 * u
    val bodyH = 19 * u
    val legLen = 6 * u
    val bodyTop = ground - legLen - bodyH - hop
    val bodyBottom = bodyTop + bodyH

    // Shadow on the ground, smaller while hopping.
    val shadowW = bodyW * (1f - hop / (12 * u))
    drawOval(c.shadow, Offset(cx - shadowW / 2, ground - 1.2f * u), Size(shadowW, 2.4f * u))

    // Legs.
    val legStroke = 2.6f * u
    for (side in listOf(-1, 1)) {
        val hipX = cx + side * 5 * u
        val swing = if (side < 0) legSwing else -legSwing
        drawLine(c.body, Offset(hipX, bodyBottom - u), Offset(hipX + swing * 3 * u, bodyBottom + legLen - u), legStroke, StrokeCap.Round)
    }

    rotate(tilt, Offset(cx, bodyBottom)) {
        // Antenna with a small light that pulses.
        val antennaTop = Offset(cx, bodyTop - 5 * u)
        drawLine(c.line, Offset(cx, bodyTop), antennaTop, 1.4f * u, StrokeCap.Round)
        val glow = 0.55f + 0.45f * (0.5f + 0.5f * wave(secs, 0.9f))
        drawCircle(c.prop.copy(alpha = glow), 2.1f * u, antennaTop)

        // Body and visor.
        drawRoundRect(c.body, Offset(cx - bodyW / 2, bodyTop), Size(bodyW, bodyH), CornerRadius(7 * u))
        val visorW = 16 * u
        val visorH = 9 * u
        val visorTop = bodyTop + 3.5f * u
        drawRoundRect(c.visor, Offset(cx - visorW / 2, visorTop), Size(visorW, visorH), CornerRadius(4.5f * u))

        // Eyes: look sideways, blink every few seconds.
        val blinkPhase = (secs % 3.7f)
        val open = if (blinkPhase < 0.14f) abs(blinkPhase - 0.07f) / 0.07f else 1f
        val eyeH = (3.6f * u * open).coerceAtLeast(0.6f * u)
        val eyeY = visorTop + visorH / 2 + if (activity == FigureActivity.READ || activity == FigureActivity.TYPE) 1.2f * u else 0f
        for (side in listOf(-1, 1)) {
            val ex = cx + side * 3.6f * u + look * 1.8f * u
            drawRoundRect(c.eye, Offset(ex - 1.3f * u, eyeY - eyeH / 2), Size(2.6f * u, eyeH), CornerRadius(1.3f * u))
        }
        // Small smile while dancing and juggling.
        if (activity == FigureActivity.DANCE || activity == FigureActivity.JUGGLE) {
            val smile = Path().apply {
                moveTo(cx - 2 * u, visorTop + visorH - 2.4f * u)
                quadraticTo(cx, visorTop + visorH - 0.9f * u, cx + 2 * u, visorTop + visorH - 2.4f * u)
            }
            drawPath(smile, c.eye.copy(alpha = fade), style = Stroke(0.9f * u, cap = StrokeCap.Round))
        }

        // Arms in front of the body, behind the props; a darker edge keeps them visible on the body.
        val shoulderY = bodyTop + 9 * u
        fun arm(side: Int, degrees: Float): Offset {
            val a = Math.toRadians(degrees.toDouble())
            val start = Offset(cx + side * bodyW / 2 - side * 0.5f * u, shoulderY)
            val end = Offset(start.x + side * (sin(a) * 8 * u).toFloat(), start.y + (cos(a) * 8 * u).toFloat())
            drawLine(c.visor.copy(alpha = 0.5f), start, end, 3.6f * u, StrokeCap.Round)
            drawLine(c.body, start, end, 2.6f * u, StrokeCap.Round)
            return end
        }
        val handL = arm(-1, armL)
        val handR = arm(1, armR)

        // Props of the activity.
        when (activity) {
            FigureActivity.READ -> {
                // An open book held in front; a page turns now and then.
                val bookY = bodyBottom - 6 * u
                val w = 9 * u
                val h = 7 * u
                val col = c.prop.copy(alpha = fade)
                val left = Path().apply { moveTo(cx, bookY + u); lineTo(cx - w, bookY); lineTo(cx - w, bookY + h); lineTo(cx, bookY + h + u); close() }
                val right = Path().apply { moveTo(cx, bookY + u); lineTo(cx + w, bookY); lineTo(cx + w, bookY + h); lineTo(cx, bookY + h + u); close() }
                drawPath(left, col)
                drawPath(right, col.copy(alpha = fade * 0.85f))
                for (i in 1..3) {
                    val y = bookY + i * 1.6f * u
                    drawLine(c.visor.copy(alpha = fade * 0.8f), Offset(cx - w + 1.5f * u, y), Offset(cx - 1.5f * u, y + 0.4f * u), 0.6f * u)
                    drawLine(c.visor.copy(alpha = fade * 0.8f), Offset(cx + 1.5f * u, y + 0.4f * u), Offset(cx + w - 1.5f * u, y), 0.6f * u)
                }
                val turn = (secs % 1.6f) / 1.6f
                if (turn < 0.35f) {
                    val k = turn / 0.35f
                    val x = cx + w * (1 - 2 * k)
                    drawLine(col, Offset(cx, bookY + u), Offset(x, bookY - 1.5f * u * sin(PI * k).toFloat()), 0.9f * u, StrokeCap.Round)
                }
            }
            FigureActivity.TYPE -> {
                // A small laptop; tiny dots rise from the screen.
                val baseY = bodyBottom + 0.5f * u
                val col = c.prop.copy(alpha = fade)
                drawRoundRect(col, Offset(cx - 10 * u, baseY), Size(20 * u, 2.2f * u), CornerRadius(u))
                val screen = Path().apply {
                    moveTo(cx - 8 * u, baseY); lineTo(cx - 7 * u, baseY - 5.5f * u); lineTo(cx + 7 * u, baseY - 5.5f * u); lineTo(cx + 8 * u, baseY); close()
                }
                drawPath(screen, col.copy(alpha = fade * 0.9f))
                for (i in 0..2) {
                    val k = ((secs * 0.9f + i / 3f) % 1f)
                    drawCircle(c.prop.copy(alpha = fade * (1 - k)), 0.9f * u, Offset(cx + (i - 1) * 4 * u + (bodyW * 0.75f), baseY - 6 * u - k * 10 * u))
                }
            }
            FigureActivity.JUGGLE -> {
                // Three balls on one loop between the hands.
                val top = bodyTop - 9 * u
                for (i in 0..2) {
                    val k = ((secs * 0.7f + i / 3f) % 1f)
                    val x = handL.x + (handR.x - handL.x) * k
                    val y = handL.y + (handR.y - handL.y) * k - sin(PI * k).toFloat() * (handL.y - top)
                    drawCircle(c.prop.copy(alpha = fade), 1.8f * u, Offset(x, y))
                }
            }
            FigureActivity.SEARCH -> {
                // A magnifying glass in the raised hand.
                val col = c.line.copy(alpha = fade)
                val lens = Offset(handR.x + 2.5f * u, handR.y - 2.5f * u)
                drawLine(col, handR, lens, 1.4f * u, StrokeCap.Round)
                drawCircle(c.visor.copy(alpha = fade * 0.6f), 3.6f * u, lens)
                drawCircle(col, 3.6f * u, lens, style = Stroke(1.3f * u))
            }
            FigureActivity.DANCE -> {
                // Two small sparkles that come and go.
                for (i in 0..1) {
                    val k = ((secs * 0.8f + i * 0.5f) % 1f)
                    val center = Offset(cx + (if (i == 0) -1 else 1) * (bodyW / 2 + 6 * u), bodyTop + 2 * u - k * 6 * u)
                    val r = 1.8f * u * sin(PI * k).toFloat()
                    val col = c.prop.copy(alpha = fade)
                    drawLine(col, Offset(center.x - r, center.y), Offset(center.x + r, center.y), 0.8f * u, StrokeCap.Round)
                    drawLine(col, Offset(center.x, center.y - r), Offset(center.x, center.y + r), 0.8f * u, StrokeCap.Round)
                }
            }
            FigureActivity.THINK -> {
                // Thought dots appear one after the other above the head.
                val shown = ((secs * 1.4f) % 4f).toInt()
                for (i in 0 until shown.coerceAtMost(3)) {
                    drawCircle(c.line.copy(alpha = fade), (1.0f + i * 0.5f) * u, Offset(cx + (5 + i * 3.5f) * u, bodyTop - (2 + i * 3.2f) * u))
                }
            }
            FigureActivity.WALK -> Unit
        }
    }
}
