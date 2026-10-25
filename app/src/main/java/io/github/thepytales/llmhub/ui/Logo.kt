package io.github.thepytales.llmhub.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/** Colors of the logo (TU Dresden blue), the same in every look. */
private object LogoColors {
    val top = Color(0xFF0A0AA0)
    val bottom = Color(0xFF00006E)
    val light = Color(0xFF97C6FF)
}

private fun phase(t: Float, from: Float, to: Float) = ((t - from) / (to - from)).coerceIn(0f, 1f)

/** Ease out with a little overshoot, for the points that "pop" into place. */
private fun pop(x: Float): Float {
    if (x <= 0f) return 0f
    if (x >= 1f) return 1f
    val c = 1.70158f
    val y = x - 1f
    return 1f + (c + 1f) * y * y * y + c * y * y
}

/**
 * The logo: the didactic triangle (teacher, learners, content) on a TU-blue tile, with a sound wave in the middle –
 * the conversation that becomes text. [reveal] 0 → 1 builds it up (start animation); 1 is the finished logo.
 */
@Composable
fun AppLogo(modifier: Modifier = Modifier, reveal: Float = 1f) {
    Canvas(modifier) { drawLogo(reveal) }
}

private fun DrawScope.drawLogo(t: Float) {
    val u = size.minDimension / 108f
    fun p(x: Float, y: Float) = Offset(x * u, y * u)
    // Tile
    drawRoundRect(
        brush = Brush.linearGradient(listOf(LogoColors.top, LogoColors.bottom), Offset.Zero, Offset(size.width, size.height)),
        cornerRadius = CornerRadius(24 * u, 24 * u),
        size = Size(108 * u, 108 * u),
    )
    val a = p(54f, 28f)
    val b = p(80f, 73f)
    val c = p(28f, 73f)
    // Edges drawn one after the other.
    val edges = listOf(a to b, b to c, c to a)
    val edgeT = phase(t, 0.08f, 0.55f) * 3f
    edges.forEachIndexed { i, (s, e) ->
        val f = (edgeT - i).coerceIn(0f, 1f)
        if (f > 0f) drawLine(Color.White, s, s + (e - s) * f, strokeWidth = 3.6f * u, cap = StrokeCap.Round)
    }
    // Sound wave grows from the middle.
    val bars = listOf(46f to 2f, 50f to 5.5f, 54f to 8.5f, 58f to 5.5f, 62f to 2f)
    bars.forEachIndexed { i, (x, h) ->
        val f = pop(phase(t, 0.55f + 0.05f * kotlin.math.abs(i - 2), 0.85f + 0.05f * kotlin.math.abs(i - 2)))
        if (f > 0f) {
            val hh = h * f
            drawLine(LogoColors.light, p(x, 59f - hh), p(x, 59f + hh), strokeWidth = 3.2f * u, cap = StrokeCap.Round)
        }
    }
    // The three points pop up (top, left, right).
    listOf(a to Color.White, c to Color.White, b to LogoColors.light).forEachIndexed { i, (center, color) ->
        val f = pop(phase(t, 0.12f + 0.14f * i, 0.42f + 0.14f * i))
        if (f > 0f) drawCircle(color, radius = 8.5f * u * f, center = center)
    }
}

/** Shown once per start of the app. */
private object StartAnimationState {
    var shown = false
}

/**
 * Short start animation (about 1.4 s) above the app, which is already loading underneath: the logo builds up, the
 * name fades in, then everything fades out. Off when [enabled] is false (Einstellungen › Darstellung).
 */
@Composable
fun StartAnimation(enabled: Boolean, content: @Composable () -> Unit) {
    var visible by remember { mutableStateOf(enabled && !StartAnimationState.shown) }
    Box(Modifier.fillMaxSize()) {
        content()
        if (visible) {
            val reveal = remember { Animatable(0f) }
            val text = remember { Animatable(0f) }
            val out = remember { Animatable(1f) }
            val tile = remember { Animatable(0.82f) }
            LaunchedEffect(Unit) {
                StartAnimationState.shown = true
                launch { tile.animateTo(1f, tween(520, easing = FastOutSlowInEasing)) }
                reveal.animateTo(1f, tween(1050, easing = LinearEasing))
                text.animateTo(1f, tween(260))
                kotlinx.coroutines.delay(160)
                out.animateTo(0f, tween(320))
                visible = false
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .alpha(out.value)
                    .background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    AppLogo(Modifier.size(112.dp).scale(tile.value).alpha(phase(reveal.value, 0f, 0.12f).coerceAtLeast(0.001f) * 1f), reveal = reveal.value)
                    Spacer(Modifier.height(22.dp))
                    Text(
                        "ADEU LLM",
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
                        modifier = Modifier.alpha(text.value),
                    )
                    Text(
                        "Allgemeine Didaktik und Empirische Unterrichtsforschung",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp, vertical = 4.dp).alpha(text.value),
                    )
                }
            }
        }
    }
}
