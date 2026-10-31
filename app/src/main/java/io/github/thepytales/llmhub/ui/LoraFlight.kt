package io.github.thepytales.llmhub.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * The way from a wish to the place (AP-36): when Lora leads somewhere, she flies from her panel along an arc to the
 * target – the chat's input field, an entry of the side menu or the option that then glows – leaves a trail of small
 * stars and lands with a soft ring. All positions are in root coordinates; [LoraFlightLayer] lies over the whole app.
 */
object LoraFlight {
    enum class Landing { CHAT, PLACE }

    data class Trip(val id: Long, val from: Offset?, val landing: Landing, val spot: String?)

    val trip = MutableStateFlow<Trip?>(null)

    /** Lora in the panel's header (the start of the flight). */
    @Volatile
    var origin: Offset? = null

    /** The chat's input field (where a handed-over question lands). */
    @Volatile
    var composer: Offset? = null

    fun start(landing: Landing, spot: String? = null) {
        trip.value = Trip(System.nanoTime(), origin.takeIf { LoraHelper.isOpen.value }, landing, spot)
    }
}

/** Where the pointer (or the last touch) is, so Lora's eyes can follow it (AP-36). */
object LoraGaze {
    @Volatile
    var pointer: Offset? = null

    @Volatile
    var movedAt: Long = 0L

    /** The direction from [center] to the pointer, each axis -1..1, or null when the pointer has been still for a while. */
    fun direction(center: Offset, reach: Float): Offset? {
        val p = pointer ?: return null
        if (System.currentTimeMillis() - movedAt > 6_000) return null
        val d = p - center
        return Offset((d.x / reach).coerceIn(-1f, 1f), (d.y / reach).coerceIn(-1f, 1f))
    }
}

/** Notes where the pointer moves, without taking the events away from the app. */
fun Modifier.trackLoraGaze(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            val e = awaitPointerEvent(PointerEventPass.Initial)
            e.changes.firstOrNull()?.let {
                LoraGaze.pointer = it.position
                LoraGaze.movedAt = System.currentTimeMillis()
            }
        }
    }
}

private val FLIGHT_EASING = CubicBezierEasing(0.45f, 0f, 0.25f, 1f)

/** Lies over the app and shows Lora's flight while there is one. */
@Composable
fun LoraFlightLayer() {
    val trip by LoraFlight.trip.collectAsStateWithLifecycle()
    val t = trip ?: return
    val progress = remember(t.id) { Animatable(0f) }
    val calm = LoraMotion.calm
    LaunchedEffect(t.id) {
        progress.animateTo(1f, tween(if (calm) 480 else 950, easing = FLIGHT_EASING))
        delay(if (calm) 200 else 460)
        if (LoraFlight.trip.value?.id == t.id) LoraFlight.trip.value = null
    }
    val time by produceState(0L, t.id) {
        val start = withFrameMillis { it }
        while (true) withFrameMillis { value = it - start }
    }
    val colors = loraColors()
    Canvas(Modifier.fillMaxSize()) {
        drawFlight(t, progress.value, time, calm, colors)
    }
}

/** A point on the quadratic curve from [a] over [c] to [b]. */
internal fun bezier(a: Offset, c: Offset, b: Offset, q: Float): Offset {
    val u = 1 - q
    return Offset(u * u * a.x + 2 * u * q * c.x + q * q * b.x, u * u * a.y + 2 * u * q * c.y + q * q * b.y)
}

/** The target of [t] on a canvas of this size. */
private fun DrawScope.targetOf(t: LoraFlight.Trip): Offset = when (t.landing) {
    LoraFlight.Landing.CHAT -> LoraFlight.composer ?: Offset(size.width * 0.5f, size.height - 72.dp.toPx())
    LoraFlight.Landing.PLACE -> t.spot?.let { Spotlight.bounds[it]?.takeIf { r -> r.width > 0f && r.inside(size.width, size.height) }?.center }
        ?: Offset(size.width * 0.5f, size.height * 0.36f)
}

private fun Rect.inside(w: Float, h: Float) = center.x in 0f..w && center.y in 0f..h

internal fun DrawScope.drawFlight(t: LoraFlight.Trip, p: Float, timeMs: Long, calm: Boolean, colors: LoraColors) {
    val from = t.from ?: Offset(size.width - 72.dp.toPx(), size.height - 120.dp.toPx())
    val to = targetOf(t)
    // An arc above the straight line; calm: nearly straight.
    val lift = if (calm) 0.06f else 0.22f
    val ctrl = Offset((from.x + to.x) / 2, min(from.y, to.y) - lift * size.height)
    val pos = bezier(from, ctrl, to, p)
    val spark = colors.spark
    val star = 4.dp.toPx()
    // The trail: small stars where Lora just was.
    if (!calm) {
        for (k in 1..7) {
            val q = p - k * 0.04f
            if (q <= 0f) continue
            val fade = (1f - k / 8f) * (1f - smooth(p, 0.85f, 1f))
            drawStar(bezier(from, ctrl, to, q), star * (1f - k / 10f), spark.copy(alpha = 0.75f * fade))
        }
    }
    // Lora herself: turned a little into the direction of flight, smaller towards the target.
    if (p < 0.98f) {
        val ahead = bezier(from, ctrl, to, (p + 0.03f).coerceAtMost(1f))
        val angle = Math.toDegrees(atan2((ahead.y - pos.y).toDouble(), (ahead.x - pos.x).toDouble())).toFloat()
        val lean = (if (ahead.x >= pos.x) angle else angle - 180f).let { a -> ((a + 540f) % 360f) - 180f }.coerceIn(-28f, 28f)
        val scale = 1f - 0.45f * smooth(p, 0.55f, 1f)
        val w = 72.dp.toPx() * scale
        val h = w / LORA_ASPECT
        val alpha = 1f - smooth(p, 0.9f, 0.98f)
        val period = LoraAction.FLY.periodMs
        inset(pos.x - w / 2, pos.y - h / 2, size.width - (pos.x + w / 2), size.height - (pos.y + h / 2)) {
            rotate(lean) {
                drawLora(LoraFrame(LoraAction.FLY, (timeMs % period) / period.toFloat(), timeMs, null, 1f), timeMs, colors.faded(alpha))
            }
        }
    }
    // Landing: a soft ring and a few sparks spreading out.
    val land = smooth(p, 0.86f, 1f)
    if (land > 0f) {
        val ring = 8.dp.toPx() + 30.dp.toPx() * land
        drawCircle(colors.body.copy(alpha = 0.45f * (1f - land)), ring, to, style = Stroke(2.dp.toPx()))
        if (!calm) for (k in 0 until 6) {
            val a = Math.toRadians((k * 60 + 15).toDouble())
            val r = ring * 0.9f
            drawStar(Offset(to.x + (cos(a) * r).toFloat(), to.y + (sin(a) * r).toFloat()), star * (1f - land * 0.6f), spark.copy(alpha = 0.9f * (1f - land)))
        }
    }
}

private fun smooth(x: Float, a: Float, b: Float): Float {
    val t = ((x - a) / (b - a)).coerceIn(0f, 1f)
    return t * t * (3 - 2 * t)
}

/** A small four-pointed sparkle. */
private fun DrawScope.drawStar(center: Offset, r: Float, color: androidx.compose.ui.graphics.Color) {
    if (r <= 0f || color.alpha <= 0f) return
    val path = androidx.compose.ui.graphics.Path().apply {
        moveTo(center.x, center.y - r)
        quadraticTo(center.x, center.y, center.x + r, center.y)
        quadraticTo(center.x, center.y, center.x, center.y + r)
        quadraticTo(center.x, center.y, center.x - r, center.y)
        quadraticTo(center.x, center.y, center.x, center.y - r)
        close()
    }
    drawPath(path, color)
}
