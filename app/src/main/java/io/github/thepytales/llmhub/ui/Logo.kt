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
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/** Colors of the logo (light lavender tile, Lora as in the app: violet with a white face), the same in every look. */
private object LogoColors {
    val top = Color(0xFFF6F4FF)
    val bottom = Color(0xFFDCD6FF)
    val light = Color.White

    /** Lora as she looks in the ADEU style: violet body, white visor with dark eyes, a warm orange book. */
    val lora = LoraColors(
        body = Color(0xFF5B4FE9),
        visor = Color.White,
        eye = Color(0xFF1B1B2F),
        line = Color(0xFF6A6290),
        prop = Color(0xFFFF8A3D),
        spark = Color(0xFFFFB300),
        blush = Color(0xFFFF8FA3),
        shadow = Color(0xFF2B1FA8).copy(alpha = 0.16f),
    )
}

private fun phase(t: Float, from: Float, to: Float) = ((t - from) / (to - from)).coerceIn(0f, 1f)

/** Ease out with a little overshoot, for Lora hopping into place. */
private fun pop(x: Float): Float {
    if (x <= 0f) return 0f
    if (x >= 1f) return 1f
    val c = 1.70158f
    val y = x - 1f
    return 1f + (c + 1f) * y * y * y + c * y * y
}

/** Moment of Lora's reading round shown in the finished logo: eyes open, no page in the air. */
internal const val LOGO_TIME_MS = 1000L

/**
 * The logo: Lora, the mascot, reading a book on a light tile – the research assistant of the professorship.
 * [reveal] 0 → 1 builds it up (start animation: the tile appears, Lora hops in and turns a page); 1 is the finished
 * logo, always in the same pose.
 */
@Composable
fun AppLogo(modifier: Modifier = Modifier, reveal: Float = 1f) {
    Canvas(modifier) { drawLogo(reveal) }
}

/**
 * Draws the logo at stage [t] (1 = finished). [tile] false leaves the blue tile and the light out (foreground of the
 * Android icon, which brings its own background); [scale] shrinks Lora around the center (safe zone of that icon).
 */
internal fun DrawScope.drawLogo(t: Float, tile: Boolean = true, scale: Float = 1f, colors: LoraColors = LogoColors.lora) {
    val k = size.minDimension / 108f
    val shape = Path().apply { addRoundRect(RoundRect(0f, 0f, 108 * k, 108 * k, CornerRadius(24 * k, 24 * k))) }
    if (tile) {
        drawPath(shape, Brush.linearGradient(listOf(LogoColors.top, LogoColors.bottom), Offset.Zero, Offset(108 * k, 108 * k)))
        // A soft light behind Lora, like a reading lamp.
        val glow = phase(t, 0.15f, 0.6f)
        if (glow > 0f) {
            drawCircle(
                Brush.radialGradient(listOf(LogoColors.light.copy(alpha = 0.75f * glow), Color.Transparent), Offset(54 * k, 50 * k), 46 * k),
                radius = 46 * k, center = Offset(54 * k, 50 * k),
            )
        }
    }
    // Lora hops in from below and lands on the ground line; then reads (a page turns while the animation runs).
    val enter = pop(phase(t, 0.05f, 0.62f))
    if (enter <= 0f) return
    val u = 2.15f * k * scale
    val rise = (1f - enter) * 30 * k
    val timeMs = if (t >= 1f) LOGO_TIME_MS else (t * 1800).toLong().coerceAtLeast(1)
    // drawLora fills an 80 × 56 space; it is placed so that Lora (about 36 units high) stands centered on the tile.
    val feet = 54 * k + 20 * u
    val left = 54 * k - 40 * u
    val top = feet - 52.5f * u + rise
    val right = size.width - (left + 80 * u)
    val bottom = size.height - (top + 56 * u)
    val draw: DrawScope.() -> Unit = {
        inset(left, top, right, bottom) {
            drawLora(LoraFrame(LoraAction.READ, phase = 0.18f, elapsedMs = timeMs, previous = null, blend = 1f), timeMs, colors)
        }
    }
    if (tile) clipPath(shape) { draw() } else draw()
}

/** Shown once per start of the app. */
private object StartAnimationState {
    var shown = false
}

/**
 * Short start animation (about 1.7 s) above the app, which is already loading underneath: the tile appears, Lora
 * hops in and turns a page, the name fades in, then everything fades out. Off when [enabled] is false (Einstellungen › Darstellung).
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
                reveal.animateTo(1f, tween(1300, easing = LinearEasing))
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
                    AppLogo(Modifier.size(120.dp).scale(tile.value).alpha(phase(reveal.value, 0f, 0.12f).coerceAtLeast(0.001f)), reveal = reveal.value)
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
