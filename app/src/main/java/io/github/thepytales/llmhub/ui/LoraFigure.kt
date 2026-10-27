package io.github.thepytales.llmhub.ui

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * Lora, the mascot of the app (named after Low-Rank Adaptation): a small round robot in the brand color that keeps
 * the user company while the agent works and hangs around on the start page. Drawn on a canvas (no pictures, no
 * emoji) in a space of 80 × 56 units, so it fits any size with that aspect ratio.
 */

/** What Lora does; [periodMs] is how long one round of it lasts. */
enum class LoraAction(val periodMs: Long) {
    // While the agent works.
    WALK(4200), READ(4200), TYPE(4200), JUGGLE(4200), SEARCH(4200), DANCE(4200), THINK(4200),
    MICROSCOPE(5000), FLASK(4600), GLOBE(4600), TELESCOPE(5000), NOTES(4600), DIG(5600), EUREKA(3200),
    BOOKS(4600), CHART(5000), COFFEE(4600),

    // On the start page.
    WAVE(2800), LOOK(5200), SLEEP(6000),
}

/** While the agent works: every research activity in turn, starting with walking. */
internal val WORKING_SEQUENCE = listOf(
    LoraAction.WALK, LoraAction.READ, LoraAction.MICROSCOPE, LoraAction.TYPE, LoraAction.GLOBE, LoraAction.JUGGLE,
    LoraAction.FLASK, LoraAction.SEARCH, LoraAction.NOTES, LoraAction.TELESCOPE, LoraAction.DANCE, LoraAction.DIG,
    LoraAction.THINK, LoraAction.BOOKS, LoraAction.CHART, LoraAction.EUREKA, LoraAction.COFFEE,
)

/** On the start page: calmer, with a look around between the activities. */
internal val HOME_SEQUENCE = listOf(
    LoraAction.LOOK, LoraAction.COFFEE, LoraAction.LOOK, LoraAction.READ, LoraAction.GLOBE, LoraAction.LOOK,
    LoraAction.MICROSCOPE, LoraAction.LOOK, LoraAction.TELESCOPE, LoraAction.LOOK, LoraAction.CHART,
)

/** Width / height of Lora's drawing space; canvases should keep this ratio. */
const val LORA_ASPECT = 80f / 56f

/** How long Lora takes to move from one activity into the next (pose and props blend). */
internal const val LORA_BLEND_MS = 500L

/**
 * One moment of Lora: the [action] at [phase] (0..1) of its round, [elapsedMs] since it began; while [blend] < 1 Lora
 * still moves out of [previous] (which may be the same action when it repeats).
 */
internal data class LoraFrame(val action: LoraAction, val phase: Float, val elapsedMs: Long, val previous: LoraAction?, val blend: Float)

/**
 * Decides what Lora does: the [sequence] in turn, an action that fits what is going on ([hint], e.g. the web search
 * of the agent or the user typing) now and then, and [play] at once (a tap, the greeting).
 */
class LoraDirector internal constructor(
    private val sequence: List<LoraAction>,
    start: LoraAction,
    /** An action must run at least this long before a new hint replaces it. */
    private val minDwellMs: Long,
) {
    private var current = start
    private var previous: LoraAction? = null
    private var startedAt = 0L
    private var now = 0L
    private var seqIndex = sequence.indexOf(start)
    private var slots = 0
    private var hint: List<LoraAction> = emptyList()
    private var hintEvery = 3
    private var hintFresh = false
    private var hintPick = 0

    /** Actions that fit what is going on; one of them every [every] rounds (1: only these), the first one soon. */
    fun hint(actions: List<LoraAction>, every: Int = 3) {
        if (actions != hint) {
            hint = actions
            hintFresh = actions.isNotEmpty()
            hintPick = 0
        }
        hintEvery = every.coerceAtLeast(1)
    }

    /** Starts [action] at once. */
    fun play(action: LoraAction) = switch(action, now)

    internal fun frame(timeMs: Long): LoraFrame {
        now = timeMs
        var elapsed = timeMs - startedAt
        val early = hintFresh && current !in hint && elapsed >= minDwellMs
        if (elapsed >= current.periodMs || early) {
            switch(next(), timeMs)
            elapsed = 0
        }
        val blend = if (previous == null) 1f else (elapsed / LORA_BLEND_MS.toFloat()).coerceIn(0f, 1f)
        return LoraFrame(current, (elapsed.toFloat() / current.periodMs).coerceIn(0f, 1f), elapsed, previous.takeIf { blend < 1f }, blend)
    }

    private fun next(): LoraAction {
        slots++
        val useHint = hint.isNotEmpty() && (hintFresh || slots % hintEvery == 0)
        hintFresh = false
        if (useHint) {
            repeat(hint.size) {
                val a = hint[hintPick++ % hint.size]
                if (a != current || hint.size == 1) return a
            }
        }
        repeat(sequence.size) {
            seqIndex = (seqIndex + 1) % sequence.size
            if (sequence[seqIndex] != current) return sequence[seqIndex]
        }
        return current
    }

    private fun switch(action: LoraAction, timeMs: Long) {
        previous = current
        current = action
        startedAt = timeMs
    }
}

/** What Lora acts out for the current step of the agent ([tool] is the name of the running tool). */
internal fun loraHint(tool: String?, thinking: Boolean): List<LoraAction> = when {
    tool == null -> if (thinking) listOf(LoraAction.THINK, LoraAction.EUREKA) else emptyList()
    tool == "web_search" || tool == "news_search" -> listOf(LoraAction.SEARCH, LoraAction.TELESCOPE)
    "literature" in tool || tool.startsWith("zotero") || tool == "format_bibliography" || tool == "journal_new_articles" ->
        listOf(LoraAction.BOOKS, LoraAction.READ)
    tool == "check_references" || tool == "check_accessibility" -> listOf(LoraAction.NOTES, LoraAction.MICROSCOPE)
    tool == "run_javascript" -> listOf(LoraAction.FLASK, LoraAction.CHART)
    tool == "create_chart" -> listOf(LoraAction.CHART)
    tool.startsWith("read_") || tool == "fetch_url" -> listOf(LoraAction.READ, LoraAction.MICROSCOPE)
    tool.startsWith("search_") || tool.startsWith("list_") -> listOf(LoraAction.DIG, LoraAction.SEARCH)
    tool == "transcribe_local_file" || tool == "remember" -> listOf(LoraAction.NOTES)
    tool.startsWith("create_") || tool.startsWith("edit_") || tool.startsWith("write_") || tool == "fill_pdf" ||
        tool == "set_excel_cells" || tool == "reply_to_comment" -> listOf(LoraAction.TYPE, LoraAction.NOTES)
    else -> emptyList()
}

internal data class LoraColors(
    val body: Color,
    val visor: Color,
    val eye: Color,
    val line: Color,
    val prop: Color,
    /** The little star on the antenna, the light bulb. */
    val spark: Color,
    val blush: Color,
    val shadow: Color,
)

@Composable
internal fun loraColors(): LoraColors {
    val scheme = MaterialTheme.colorScheme
    return LoraColors(
        body = LocalChatLook.current.brand,
        visor = scheme.surface,
        eye = scheme.onSurface,
        line = scheme.onSurfaceVariant,
        prop = scheme.tertiary,
        spark = Color(0xFFFFB300),
        blush = Color(0xFFFF8FA3),
        shadow = scheme.onSurface.copy(alpha = 0.12f),
    )
}

@Composable
fun rememberLoraDirector(
    sequence: List<LoraAction> = WORKING_SEQUENCE,
    start: LoraAction = sequence.first(),
    minDwellMs: Long = 1500,
): LoraDirector = remember { LoraDirector(sequence, start, minDwellMs) }

/** Lora, animated; [hint] and [hintEvery] go to [LoraDirector.hint]. */
@Composable
fun LoraFigure(
    modifier: Modifier = Modifier,
    label: String,
    director: LoraDirector = rememberLoraDirector(),
    hint: List<LoraAction> = emptyList(),
    hintEvery: Int = 3,
) {
    val time by produceState(0L) {
        val start = withFrameMillis { it }
        while (true) withFrameMillis { value = it - start }
    }
    SideEffect { director.hint(hint, hintEvery) }
    val colors = loraColors()
    Canvas(modifier.semantics { contentDescription = label }) {
        val t = time
        drawLora(director.frame(t), t, colors)
    }
}

/** How Lora's eyes look. */
internal enum class LoraEyes { OPEN, HAPPY, WIDE, CLOSED, FOCUS }

/** How Lora's mouth looks. */
internal enum class LoraMouth { SMILE, GRIN, OPEN, FLAT, NONE }

/**
 * Lora's posture in units: [dx] sideways, [hop] up, [tilt] in degrees around the feet, eyes [lookX]/[lookY] (-1..1),
 * [legs] swing (-1..1), arm angles in degrees from hanging down (positive = raised to the side, negative = in front),
 * [squash] of the body height, [glow] of the antenna star.
 */
internal data class LoraPose(
    val dx: Float = 0f,
    val hop: Float = 0f,
    val tilt: Float = 0f,
    val lookX: Float = 0f,
    val lookY: Float = 0f,
    val legs: Float = 0f,
    val armL: Float = 12f,
    val armR: Float = 12f,
    val squash: Float = 1f,
    val glow: Float = 1f,
    val eyes: LoraEyes = LoraEyes.OPEN,
    val mouth: LoraMouth = LoraMouth.SMILE,
)

private fun ease(x: Float): Float = (0.5f - 0.5f * cos(PI * x.coerceIn(0f, 1f))).toFloat()
private fun wave(t: Float, periods: Float) = sin(2 * PI * t * periods).toFloat()
private fun mix(a: Float, b: Float, t: Float) = a + (b - a) * t

/** 0 before [a], eased up to 1 at [b]. */
private fun ramp(p: Float, a: Float, b: Float) = ease((p - a) / (b - a))

/** Up from [a] to [b], down from [c] to [d]. */
private fun bump(p: Float, a: Float, b: Float, c: Float, d: Float) = ramp(p, a, b) * (1 - ramp(p, c, d))

/** A short arc 0 → 1 → 0 between [a] and [b]. */
private fun arc(p: Float, a: Float, b: Float) = if (p <= a || p >= b) 0f else sin(PI * (p - a) / (b - a)).toFloat()

private fun lerp(a: LoraPose, b: LoraPose, t: Float) = LoraPose(
    dx = mix(a.dx, b.dx, t),
    hop = mix(a.hop, b.hop, t),
    tilt = mix(a.tilt, b.tilt, t),
    lookX = mix(a.lookX, b.lookX, t),
    lookY = mix(a.lookY, b.lookY, t),
    legs = mix(a.legs, b.legs, t),
    armL = mix(a.armL, b.armL, t),
    armR = mix(a.armR, b.armR, t),
    squash = mix(a.squash, b.squash, t),
    glow = mix(a.glow, b.glow, t),
    eyes = if (t < 0.5f) a.eyes else b.eyes,
    mouth = if (t < 0.5f) a.mouth else b.mouth,
)

/** Lora's posture during [a] at phase [p] (0..1) and [s] seconds. */
internal fun loraPose(a: LoraAction, p: Float, s: Float): LoraPose = when (a) {
    LoraAction.WALK -> {
        val legs = wave(s, 2.2f)
        LoraPose(
            dx = wave(p, 1f) * 20, hop = abs(legs) * 1.4f, lookX = cos(2 * PI * p).toFloat(), legs = legs,
            armL = 12 + 22 * legs, armR = 12 - 22 * legs,
        )
    }
    LoraAction.READ -> LoraPose(
        tilt = 2 * wave(s, 0.25f), lookX = -0.6f + 1.2f * ((s * 0.8f) % 1f), lookY = 0.7f, armL = -30f, armR = -30f,
    )
    LoraAction.TYPE -> LoraPose(
        lookX = 0.15f * wave(s, 0.5f), lookY = 0.8f, armL = -40 + 9 * wave(s, 4f), armR = -40 - 9 * wave(s, 4f),
        mouth = LoraMouth.FLAT,
    )
    LoraAction.JUGGLE -> LoraPose(
        hop = abs(wave(s, 1.6f)) * 0.6f, lookX = 0.5f * wave(s, 0.35f), lookY = -0.7f,
        armL = 60 + 25 * wave(s, 1.6f), armR = 60 - 25 * wave(s, 1.6f), mouth = LoraMouth.GRIN,
    )
    LoraAction.SEARCH -> {
        val found = ramp(p, 0.78f, 0.86f)
        LoraPose(
            dx = wave(p, 1f) * 6, hop = found * abs(wave(s, 2f)) * 1.5f, tilt = 3 * wave(s, 0.4f), lookX = wave(s, 0.4f),
            armL = 14f, armR = 95 + 15 * wave(s, 0.8f),
            eyes = if (found > 0.5f) LoraEyes.WIDE else LoraEyes.OPEN, mouth = if (found > 0.5f) LoraMouth.OPEN else LoraMouth.SMILE,
        )
    }
    LoraAction.DANCE -> {
        val beat = wave(s, 1.2f)
        LoraPose(
            hop = abs(beat) * 3, tilt = 9 * beat, legs = 0.6f * beat, armL = 120 + 35 * beat, armR = 120 - 35 * beat,
            squash = 0.94f + 0.06f * abs(beat), eyes = LoraEyes.HAPPY, mouth = LoraMouth.GRIN,
        )
    }
    LoraAction.THINK -> LoraPose(
        tilt = -4f, lookX = 0.5f, lookY = -0.7f, legs = 0.25f * maxOf(0f, wave(s, 1.5f)) * ramp(p, 0.4f, 0.5f),
        armL = 14f, armR = -100 + 4 * wave(s, 0.7f), mouth = LoraMouth.FLAT,
    )
    LoraAction.MICROSCOPE -> {
        val lean = ramp(p, 0.04f, 0.16f) * (1 - ramp(p, 0.66f, 0.74f))
        val amazed = ramp(p, 0.70f, 0.78f)
        LoraPose(
            dx = 2.5f * lean, hop = amazed * abs(wave(s, 1.6f)) * 2.2f, tilt = 12 * lean - 4 * amazed * (1 - ramp(p, 0.9f, 1f)),
            lookX = 0.9f * lean, lookY = 0.4f * lean - 0.5f * amazed,
            armL = mix(14f, 145 - 15 * wave(s, 2f), amazed),
            armR = mix(mix(14f, 38 + 8 * wave(s, 1.5f), lean), 150 + 15 * wave(s, 2f), amazed),
            eyes = when { lean > 0.5f -> LoraEyes.FOCUS; amazed > 0.5f -> LoraEyes.WIDE; else -> LoraEyes.OPEN },
            mouth = if (amazed > 0.5f) LoraMouth.OPEN else LoraMouth.SMILE,
        )
    }
    LoraAction.FLASK -> {
        val startle = bump(p, 0.52f, 0.57f, 0.66f, 0.74f)
        val happy = ramp(p, 0.72f, 0.8f)
        LoraPose(
            hop = startle * 1.5f, tilt = -6 * startle, lookX = 0.8f, lookY = -0.3f,
            armL = 14 + 6 * wave(s, 0.8f) + 40 * startle, armR = 70 + 8 * wave(s, 1.6f) * (1 - startle),
            eyes = when { startle > 0.5f -> LoraEyes.WIDE; happy > 0.5f -> LoraEyes.HAPPY; else -> LoraEyes.OPEN },
            mouth = when { startle > 0.5f -> LoraMouth.OPEN; happy > 0.5f -> LoraMouth.GRIN; else -> LoraMouth.SMILE },
        )
    }
    LoraAction.GLOBE -> {
        val happy = p > 0.5f && p < 0.85f
        LoraPose(
            tilt = -4f, lookX = -0.9f, lookY = 0.1f, armL = 58 + 14 * maxOf(0f, wave(s, 0.7f)), armR = 12 + 4 * wave(s, 0.5f),
            eyes = if (happy) LoraEyes.HAPPY else LoraEyes.OPEN, mouth = if (happy) LoraMouth.GRIN else LoraMouth.SMILE,
        )
    }
    LoraAction.TELESCOPE -> {
        val lean = ramp(p, 0.04f, 0.14f) * (1 - ramp(p, 0.6f, 0.68f))
        val point = ramp(p, 0.62f, 0.7f)
        LoraPose(
            dx = 2 * lean, hop = point * abs(wave(s, 1.2f)) * 1.2f, tilt = 10 * lean,
            lookX = mix(0.9f * lean, 0.6f, point), lookY = mix(-0.3f * lean, -1f, point),
            armL = 14f, armR = mix(mix(14f, 48f, lean), 158 + 6 * wave(s, 1f), point),
            eyes = when { lean > 0.5f -> LoraEyes.FOCUS; point > 0.5f -> LoraEyes.HAPPY; else -> LoraEyes.OPEN },
            mouth = if (point > 0.5f) LoraMouth.GRIN else LoraMouth.SMILE,
        )
    }
    LoraAction.NOTES -> {
        val done = ramp(p, 0.8f, 0.86f)
        LoraPose(
            hop = done * abs(wave(s, 1.5f)) * 0.8f, lookX = -0.25f + 0.15f * wave(s, 0.5f), lookY = 0.7f * (1 - done),
            armL = -38f, armR = -48 + 7 * wave(s, 3f) * (1 - done),
            eyes = if (done > 0.5f) LoraEyes.HAPPY else LoraEyes.OPEN, mouth = if (done > 0.5f) LoraMouth.GRIN else LoraMouth.SMILE,
        )
    }
    LoraAction.DIG -> {
        val crouch = ramp(p, 0f, 0.08f) * (1 - ramp(p, 0.6f, 0.7f))
        val lift = ramp(p, 0.66f, 0.76f)
        LoraPose(
            hop = lift * abs(wave(s, 1.4f)) * 2, tilt = 8 * crouch, lookX = 0.9f * crouch, lookY = 0.9f * crouch - 0.7f * lift,
            armL = mix(mix(14f, 20f, crouch), 165f, lift), armR = mix(mix(14f, 60 + 18 * wave(s, 2.4f), crouch), 165f, lift),
            squash = mix(1f, 0.86f, crouch),
            eyes = if (lift > 0.5f) LoraEyes.HAPPY else LoraEyes.OPEN, mouth = if (lift > 0.5f) LoraMouth.GRIN else LoraMouth.SMILE,
        )
    }
    LoraAction.EUREKA -> {
        val jump = arc(p, 0.2f, 0.45f)
        val raise = ramp(p, 0.12f, 0.22f)
        LoraPose(
            hop = jump * 5, lookX = 0.2f, lookY = -1f + 0.5f * ramp(p, 0.5f, 0.7f),
            armL = mix(14f, 50f, raise), armR = mix(14f, 165f, raise),
            squash = 1 - 0.1f * arc(p, 0.12f, 0.22f) + 0.04f * jump, glow = 1.4f,
            eyes = if (p < 0.45f) LoraEyes.WIDE else LoraEyes.HAPPY, mouth = if (p < 0.45f) LoraMouth.OPEN else LoraMouth.GRIN,
        )
    }
    LoraAction.BOOKS -> {
        val legs = 0.7f * wave(s, 1.8f)
        LoraPose(
            dx = wave(p, 1f) * 8, hop = abs(legs) * 1.1f, tilt = 3 * wave(s, 0.9f),
            lookX = 0.35f * cos(2 * PI * p).toFloat(), lookY = -0.6f, legs = legs, armL = -15f, armR = -15f,
        )
    }
    LoraAction.CHART -> {
        val done = ramp(p, 0.74f, 0.8f)
        LoraPose(
            lookX = mix(-1f, 0.3f, done), lookY = -0.2f * (1 - done), armL = 96 + 10 * wave(s, 0.6f), armR = 12 + 4 * wave(s, 0.5f),
            hop = done * abs(wave(s, 1.4f)) * 0.8f,
            eyes = if (done > 0.5f) LoraEyes.HAPPY else LoraEyes.OPEN, mouth = if (done > 0.5f) LoraMouth.GRIN else LoraMouth.SMILE,
        )
    }
    LoraAction.COFFEE -> {
        val sip = bump(p, 0.25f, 0.38f, 0.58f, 0.68f)
        val ahh = bump(p, 0.62f, 0.68f, 0.86f, 0.94f)
        LoraPose(
            tilt = -3 * sip, lookX = 0.3f * (1 - sip), lookY = 0.2f - 0.3f * sip,
            armL = 12 + 3 * wave(s, 0.5f), armR = mix(-55f, -115f, sip) + 2 * wave(s, 0.8f),
            eyes = when { sip > 0.5f -> LoraEyes.CLOSED; ahh > 0.5f -> LoraEyes.HAPPY; else -> LoraEyes.OPEN },
            mouth = when { sip > 0.5f -> LoraMouth.NONE; ahh > 0.5f -> LoraMouth.GRIN; else -> LoraMouth.SMILE },
        )
    }
    LoraAction.WAVE -> {
        val hello = arc(p, 0f, 0.22f)
        LoraPose(
            hop = hello * 3, tilt = 4 * wave(s, 0.9f), lookX = 0.2f, lookY = -0.1f,
            armL = 12 + 6 * hello, armR = 150 + 25 * wave(s, 1.8f), eyes = LoraEyes.HAPPY, mouth = LoraMouth.GRIN,
        )
    }
    LoraAction.LOOK -> {
        val look = -0.8f * bump(p, 0.06f, 0.18f, 0.32f, 0.42f) + 0.8f * bump(p, 0.38f, 0.5f, 0.62f, 0.72f)
        val tap = bump(p, 0.76f, 0.78f, 0.92f, 0.94f)
        LoraPose(
            hop = arc(p, 0.5f, 0.6f) * 1.6f, tilt = 4 * look, lookX = look, lookY = -0.2f * tap,
            legs = tap * 0.35f * maxOf(0f, wave(s, 3f)), armL = 12 + 3 * wave(s, 0.5f), armR = 12 - 3 * wave(s, 0.5f),
        )
    }
    LoraAction.SLEEP -> LoraPose(
        tilt = -7f, lookY = 0.4f, armL = 6f, armR = 6f, squash = 0.93f + 0.03f * wave(s, 0.25f), glow = 0.3f,
        eyes = LoraEyes.CLOSED, mouth = LoraMouth.NONE,
    )
}

/** Where things are in one frame (pixels; [u] is one unit of the 80 × 56 space). */
private class LoraGeo(
    val u: Float,
    /** Center of the canvas and top of the 80 × 56 space; [ground] is where the feet stand. */
    val cx0: Float,
    val oy: Float,
    val ground: Float,
    /** Lora's body: center (with the sideways step), top, bottom and size. */
    val cx: Float,
    val top: Float,
    val bottom: Float,
    val bw: Float,
    val bh: Float,
)

private fun Color.fade(a: Float) = copy(alpha = alpha * a.coerceIn(0f, 1f))

/** Fixed colors of a few props that should read the same in every style. */
private val Ocean = Color(0xFF6EC1E4)
private val Land = Color(0xFF7BC67E)
private val Potion = Color(0xFF2EC4B6)
private val Ivory = Color(0xFFF4EBD9)
private val Tick = Color(0xFF34A853)

/** A four-pointed sparkle like the one on Lora's antenna. */
private fun DrawScope.star(center: Offset, r: Float, color: Color) {
    if (r <= 0f) return
    val k = 0.22f * r
    val path = Path().apply {
        moveTo(center.x, center.y - r)
        quadraticTo(center.x + k, center.y - k, center.x + r, center.y)
        quadraticTo(center.x + k, center.y + k, center.x, center.y + r)
        quadraticTo(center.x - k, center.y + k, center.x - r, center.y)
        quadraticTo(center.x - k, center.y - k, center.x, center.y - r)
        close()
    }
    drawPath(path, color)
}

/** A small fossil bone, [len] long, horizontal. */
private fun DrawScope.bone(center: Offset, len: Float, u: Float, line: Color, a: Float) {
    if (a <= 0f) return
    val half = len / 2 - 0.9f * u
    for ((color, extra) in listOf(line.fade(a) to 0.35f * u, Ivory.fade(a) to 0f)) {
        drawLine(color, Offset(center.x - half, center.y), Offset(center.x + half, center.y), 1.6f * u + 2 * extra, StrokeCap.Round)
        for (sx in listOf(-1, 1)) for (sy in listOf(-1, 1)) {
            drawCircle(color, 0.95f * u + extra, Offset(center.x + sx * half, center.y + sy * 0.75f * u))
        }
    }
}

/** Things that stand on the ground next to Lora (behind the body, not tilted with it). */
private fun DrawScope.drawScene(action: LoraAction, p: Float, s: Float, a: Float, g: LoraGeo, c: LoraColors) {
    if (a <= 0f) return
    val u = g.u
    val ground = g.ground
    val line = c.line.fade(a)
    when (action) {
        LoraAction.MICROSCOPE -> {
            val mx = g.cx0 + 18.5f * u
            drawRoundRect(line, Offset(mx - 6 * u, ground - 2.2f * u), Size(11 * u, 2.2f * u), CornerRadius(1.1f * u))
            drawLine(line, Offset(mx + 3 * u, ground - 2 * u), Offset(mx + 3 * u, ground - 12.5f * u), 2.2f * u, StrokeCap.Round)
            val arm = Path().apply {
                moveTo(mx + 3 * u, ground - 12.5f * u)
                quadraticTo(mx + 3.2f * u, ground - 17 * u, mx - 1.5f * u, ground - 15.5f * u)
            }
            drawPath(arm, line, style = Stroke(2 * u, cap = StrokeCap.Round))
            drawLine(line, Offset(mx - 4.5f * u, ground - 7.5f * u), Offset(mx + 3 * u, ground - 7.5f * u), 1.4f * u, StrokeCap.Round)
            drawCircle(c.spark.fade(a * (0.45f + 0.45f * (0.5f + 0.5f * wave(s, 1.2f)))), 1.0f * u, Offset(mx - 1.2f * u, ground - 8.5f * u))
            drawLine(c.prop.fade(a), Offset(mx - 1.2f * u, ground - 10 * u), Offset(mx - 4.6f * u, ground - 19 * u), 3.2f * u, StrokeCap.Round)
            drawLine(line, Offset(mx - 1.2f * u, ground - 10 * u), Offset(mx - 0.8f * u, ground - 8.9f * u), 1.8f * u, StrokeCap.Round)
            drawLine(line, Offset(mx - 4.6f * u, ground - 19 * u), Offset(mx - 5.2f * u, ground - 20.8f * u), 3.8f * u, StrokeCap.Round)
            drawCircle(c.prop.fade(a), 1.3f * u, Offset(mx + 3 * u, ground - 10.5f * u))
            drawCircle(c.visor.fade(a), 0.5f * u, Offset(mx + 3 * u, ground - 10.5f * u))
        }
        LoraAction.GLOBE -> {
            val gx = g.cx0 - 18 * u
            val gy = ground - 12 * u
            val r = 6 * u
            drawOval(line, Offset(gx - 4.5f * u, ground - 1.8f * u), Size(9 * u, 1.8f * u))
            drawLine(line, Offset(gx, ground - 1.2f * u), Offset(gx, gy + r + 1.2f * u), 1.4f * u, StrokeCap.Round)
            drawArc(
                line, 90f, 180f, useCenter = false, topLeft = Offset(gx - r - 1.2f * u, gy - r - 1.2f * u),
                size = Size(2 * r + 2.4f * u, 2 * r + 2.4f * u), style = Stroke(1.1f * u, cap = StrokeCap.Round),
            )
            drawCircle(Ocean.fade(a), r, Offset(gx, gy))
            val rot = s * 1.4f
            val clip = Path().apply { addOval(Rect(Offset(gx, gy), r)) }
            clipPath(clip) {
                // Continents: (longitude, latitude, width, height); they turn with the globe and narrow at the edge.
                listOf(
                    floatArrayOf(0f, -0.25f, 4.4f, 3.2f), floatArrayOf(1.9f, 0.25f, 3.2f, 4.4f),
                    floatArrayOf(3.6f, -0.15f, 4.8f, 2.8f), floatArrayOf(5.0f, 0.5f, 2.6f, 2.2f),
                ).forEach { (lon, lat, w, h) ->
                    val angle = lon + rot
                    val depth = cos(angle)
                    if (depth > 0f) {
                        val x = gx + r * sin(angle)
                        val ww = w * u * (0.35f + 0.65f * depth)
                        drawOval(Land.fade(a), Offset(x - ww / 2, gy + lat * r - h * u / 2), Size(ww, h * u))
                    }
                }
            }
            drawCircle(Color.White.fade(a * 0.4f), 1.3f * u, Offset(gx - 2.3f * u, gy - 2.7f * u))
            drawCircle(line, r, Offset(gx, gy), style = Stroke(0.7f * u))
        }
        LoraAction.TELESCOPE -> {
            val tx = g.cx0 + 19 * u
            val mount = Offset(tx, ground - 9.5f * u)
            for (foot in listOf(-4.5f, 4.5f, 0.8f)) drawLine(line, mount, Offset(tx + foot * u, ground), 1.1f * u, StrokeCap.Round)
            drawLine(line, mount, Offset(tx, ground - 19 * u), 1.4f * u, StrokeCap.Round)
            drawLine(c.prop.fade(a), Offset(tx - 6 * u, ground - 14.5f * u), Offset(tx + 8 * u, ground - 25 * u), 3.4f * u, StrokeCap.Round)
            drawLine(line, Offset(tx + 6.4f * u, ground - 23.8f * u), Offset(tx + 8.2f * u, ground - 25.15f * u), 4.6f * u, StrokeCap.Round)
            drawLine(line, Offset(tx - 6 * u, ground - 14.5f * u), Offset(tx - 7.4f * u, ground - 13.5f * u), 2 * u, StrokeCap.Round)
            drawCircle(line, 1.4f * u, mount)
            // Stars that twinkle, and a shooting star when Lora looks up.
            listOf(Offset(23f, 7f), Offset(32f, 14f), Offset(13f, 4.5f), Offset(35f, 4f)).forEachIndexed { i, (x, y) ->
                val tw = 0.5f + 0.5f * wave(s * (0.9f + i * 0.2f) + i * 0.3f, 1f)
                star(Offset(g.cx0 + x * u, g.oy + y * u), 1.4f * u * (0.45f + 0.55f * tw), c.spark.fade(a))
            }
            val k = (p - 0.66f) / 0.24f
            if (k in 0f..1f) {
                val head = Offset(g.cx0 + (4 + k * 26) * u, g.oy + (3 + k * 8) * u)
                val f = a * sin(PI * k).toFloat()
                drawLine(c.spark.fade(f * 0.6f), Offset(head.x - 6 * u, head.y - 1.85f * u), head, 0.6f * u, StrokeCap.Round)
                star(head, 1.3f * u, c.spark.fade(f))
            }
        }
        LoraAction.DIG -> {
            val mx = g.cx0 + 16 * u
            drawArc(c.line.fade(a * 0.35f), 180f, 180f, useCenter = true, topLeft = Offset(mx - 7 * u, ground - 4.2f * u), size = Size(14 * u, 8.4f * u))
            drawCircle(c.line.fade(a * 0.5f), 0.6f * u, Offset(mx - 4.5f * u, ground - 0.8f * u))
            drawCircle(c.line.fade(a * 0.5f), 0.45f * u, Offset(mx + 5 * u, ground - 0.6f * u))
            val lift = ramp(p, 0.66f, 0.76f)
            val reveal = ramp(p, 0.08f, 0.5f)
            bone(Offset(mx, ground - 1.9f * u), 6.5f * u, u, c.line, a * (0.25f + 0.75f * reveal) * (1 - lift))
            if (p < 0.62f) {
                val crouch = ramp(p, 0f, 0.08f)
                for (i in 0 until 5) {
                    val k = (s * 1.8f + i / 5f) % 1f
                    val dir = if (i % 2 == 0) -1 else 1
                    drawCircle(
                        c.line.fade(a * (1 - k) * crouch), 0.55f * u,
                        Offset(mx - 2.5f * u + dir * k * 5 * u + i * 0.6f * u, ground - 2.5f * u - sin(PI * k).toFloat() * 4 * u),
                    )
                }
            }
        }
        LoraAction.CHART -> {
            val bx0 = g.cx0 - 31 * u
            val bx1 = g.cx0 - 13 * u
            val by0 = ground - 22 * u
            val by1 = ground - 8 * u
            drawLine(line, Offset(bx0 + 3 * u, by1), Offset(bx0 + 1.2f * u, ground), 1.2f * u, StrokeCap.Round)
            drawLine(line, Offset(bx1 - 3 * u, by1), Offset(bx1 - 1.2f * u, ground), 1.2f * u, StrokeCap.Round)
            drawRoundRect(c.visor.fade(a), Offset(bx0, by0), Size(bx1 - bx0, by1 - by0), CornerRadius(1.2f * u))
            drawRoundRect(line, Offset(bx0, by0), Size(bx1 - bx0, by1 - by0), CornerRadius(1.2f * u), style = Stroke(0.8f * u))
            val axis = Path().apply {
                moveTo(bx0 + 1.8f * u, by0 + 1.8f * u); lineTo(bx0 + 1.8f * u, by1 - 1.6f * u); lineTo(bx1 - 1.6f * u, by1 - 1.6f * u)
            }
            drawPath(axis, line, style = Stroke(0.6f * u, cap = StrokeCap.Round))
            val heights = floatArrayOf(3.5f, 6f, 5f, 9f)
            val tops = heights.indices.map { i ->
                val h = heights[i] * u * ramp(p, 0.08f + i * 0.15f, 0.22f + i * 0.15f)
                val x = bx0 + 3 * u + i * 3.6f * u
                if (h > 0.2f * u) {
                    drawRoundRect(
                        (if (i == heights.lastIndex) c.spark else c.prop).fade(a), Offset(x, by1 - 1.6f * u - h), Size(2.6f * u, h), CornerRadius(0.5f * u),
                    )
                }
                Offset(x + 1.3f * u, by1 - 2.8f * u - heights[i] * u)
            }
            val trend = ramp(p, 0.72f, 0.8f)
            if (trend > 0f) {
                val path = Path().apply { moveTo(tops[0].x, tops[0].y); tops.drop(1).forEach { lineTo(it.x, it.y) } }
                drawPath(path, c.eye.fade(a * trend * 0.7f), style = Stroke(0.55f * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
                star(Offset(tops.last().x, tops.last().y - 1.6f * u), 1.3f * u * trend, c.spark.fade(a * trend))
            }
        }
        else -> Unit
    }
}

/**
 * Things Lora holds or that float around Lora (tilted with the body). [front] is the second pass after the hands:
 * the lid of the laptop is in front of them.
 */
private fun DrawScope.drawHeld(
    action: LoraAction, p: Float, s: Float, a: Float, g: LoraGeo, c: LoraColors,
    shoulderL: Offset, handL: Offset, handR: Offset, front: Boolean,
) {
    if (a <= 0f) return
    val u = g.u
    val cx = g.cx
    val top = g.top
    val bottom = g.bottom
    if (front) {
        if (action == LoraAction.TYPE) {
            val lid = Path().apply {
                moveTo(cx - 8 * u, bottom + 0.3f * u); lineTo(cx - 7.2f * u, bottom - 6.2f * u)
                lineTo(cx + 7.2f * u, bottom - 6.2f * u); lineTo(cx + 8 * u, bottom + 0.3f * u); close()
            }
            drawPath(lid, c.prop.fade(a))
            star(Offset(cx, bottom - 3 * u), 1.4f * u, Color.White.fade(a * 0.85f))
        }
        return
    }
    when (action) {
        LoraAction.READ -> {
            val bookY = bottom - 6.2f * u
            val w = 8.5f * u
            val h = 6.5f * u
            drawRoundRect(c.prop.fade(a), Offset(cx - w - 0.8f * u, bookY - 0.3f * u), Size(2 * w + 1.6f * u, h + 1.9f * u), CornerRadius(0.9f * u))
            for (side in listOf(-1, 1)) {
                val page = Path().apply {
                    moveTo(cx, bookY + 0.8f * u); lineTo(cx + side * w, bookY); lineTo(cx + side * w, bookY + h); lineTo(cx, bookY + h + 0.8f * u); close()
                }
                drawPath(page, c.visor.fade(a))
                for (i in 1..3) {
                    val y = bookY + i * 1.6f * u
                    drawLine(c.line.fade(a * 0.55f), Offset(cx + side * 1.4f * u, y + 0.5f * u), Offset(cx + side * (w - 1.4f * u), y), 0.45f * u, StrokeCap.Round)
                }
            }
            val turn = (s % 1.6f) / 1.6f
            if (turn < 0.35f) {
                val k = turn / 0.35f
                val x = cx + w * (1 - 2 * k)
                val lift = 1.6f * u * sin(PI * k).toFloat()
                val page = Path().apply {
                    moveTo(cx, bookY + 0.8f * u); lineTo(x, bookY - lift); lineTo(x, bookY + h - lift); lineTo(cx, bookY + h + 0.8f * u); close()
                }
                drawPath(page, c.visor.fade(a))
                drawPath(page, c.line.fade(a * 0.5f), style = Stroke(0.35f * u))
            }
        }
        LoraAction.TYPE -> {
            drawRoundRect(c.line.fade(a), Offset(cx - 10 * u, bottom + 0.3f * u), Size(20 * u, 2 * u), CornerRadius(0.9f * u))
            for (i in 0..2) {
                val k = (s * 0.9f + i / 3f) % 1f
                drawRoundRect(
                    c.prop.fade(a * (1 - k)), Offset(cx + (i - 1) * 5 * u - 0.6f * u, bottom - 7.5f * u - k * 9 * u), Size(1.2f * u, 1.2f * u), CornerRadius(0.3f * u),
                )
            }
        }
        LoraAction.JUGGLE -> {
            val peak = top - 9 * u
            val colors = listOf(c.prop, c.spark, c.blush)
            for (i in 0..2) {
                val k = (s * 0.7f + i / 3f) % 1f
                val x = handL.x + (handR.x - handL.x) * k
                val y = handL.y + (handR.y - handL.y) * k - sin(PI * k).toFloat() * (handL.y - peak)
                drawCircle(colors[i].fade(a), 1.8f * u, Offset(x, y))
                drawCircle(Color.White.fade(a * 0.5f), 0.55f * u, Offset(x - 0.6f * u, y - 0.6f * u))
            }
        }
        LoraAction.SEARCH -> {
            val lens = Offset(handR.x + 2.4f * u, handR.y - 2.6f * u)
            val r = 3.8f * u
            drawLine(c.line.fade(a), handR, Offset(lens.x - 0.6f * r, lens.y + 0.7f * r), 1.5f * u, StrokeCap.Round)
            drawCircle(c.visor.fade(a * 0.55f), r, lens)
            drawCircle(c.prop.fade(a), r, lens, style = Stroke(1.3f * u))
            drawArc(
                Color.White.fade(a * 0.8f), 200f, 60f, useCenter = false, topLeft = Offset(lens.x - r * 0.6f, lens.y - r * 0.6f),
                size = Size(r * 1.2f, r * 1.2f), style = Stroke(0.5f * u, cap = StrokeCap.Round),
            )
            if (p > 0.8f) star(Offset(lens.x + r + 1.5f * u, lens.y - r), 1.3f * u * ramp(p, 0.8f, 0.86f), c.spark.fade(a))
        }
        LoraAction.DANCE -> {
            for (i in 0..1) {
                val k = (s * 0.8f + i * 0.5f) % 1f
                val side = if (i == 0) -1 else 1
                val head = Offset(cx + side * (g.bw / 2 + 6 * u), top + 4 * u - k * 7 * u)
                val col = (if (i == 0) c.prop else c.spark).fade(a * sin(PI * k).toFloat())
                drawOval(col, Offset(head.x - 1.1f * u, head.y - 0.85f * u), Size(2.2f * u, 1.7f * u))
                val stem = Offset(head.x + 0.95f * u, head.y)
                drawLine(col, stem, Offset(stem.x, stem.y - 3.6f * u), 0.5f * u, StrokeCap.Round)
                drawLine(col, Offset(stem.x, stem.y - 3.6f * u), Offset(stem.x + 1.6f * u, stem.y - 2.2f * u), 0.6f * u, StrokeCap.Round)
            }
        }
        LoraAction.THINK -> {
            val shown = ((s * 1.4f) % 4f).toInt().coerceAtMost(3)
            for (i in 0 until shown) {
                val center = Offset(cx + (5 + i * 3.5f) * u, top - (2 + i * 3.2f) * u)
                val r = (0.9f + i * 0.6f) * u
                drawCircle(c.visor.fade(a), r, center)
                drawCircle(c.line.fade(a), r, center, style = Stroke(0.45f * u))
            }
        }
        LoraAction.MICROSCOPE -> {
            val amazed = ramp(p, 0.72f, 0.8f)
            if (amazed > 0f) {
                for (side in listOf(-1, 1)) {
                    val k = 0.75f + 0.25f * wave(s, 2f + side * 0.3f)
                    star(Offset(cx + side * 14 * u, top + 1.5f * u), 1.8f * u * amazed * k, c.spark.fade(a))
                }
            }
        }
        LoraAction.FLASK -> {
            val bx = handR.x
            val by = handR.y - 0.9f * u
            val glass = Path().apply {
                moveTo(bx - 3.5f * u, by); lineTo(bx + 3.5f * u, by); lineTo(bx + 1.2f * u, by - 6 * u)
                lineTo(bx + 1.2f * u, by - 8.5f * u); lineTo(bx - 1.2f * u, by - 8.5f * u); lineTo(bx - 1.2f * u, by - 6 * u); close()
            }
            drawPath(glass, c.visor.fade(a * 0.9f))
            val slosh = 0.45f * u * wave(s, 1.6f)
            val liquid = Path().apply {
                moveTo(bx - 3.1f * u, by - 0.3f * u); lineTo(bx + 3.1f * u, by - 0.3f * u)
                lineTo(bx + 2.1f * u, by - 3.2f * u + slosh); lineTo(bx - 2.1f * u, by - 3.2f * u - slosh); close()
            }
            drawPath(liquid, Potion.fade(a))
            drawPath(glass, c.line.fade(a), style = Stroke(0.6f * u, join = StrokeJoin.Round))
            for (i in 0..2) {
                val k = (s * 1.1f + i / 3f) % 1f
                val center = Offset(bx + sin(k * 6 + i) * 0.7f * u, by - 3 * u - k * 10 * u)
                val r = (0.45f + 0.45f * k) * u
                drawCircle(c.visor.fade(a * (1 - k)), r, center)
                drawCircle(Potion.fade(a * (1 - k)), r, center, style = Stroke(0.3f * u))
            }
            val grow = ramp(p, 0.52f, 0.6f)
            val gone = 1 - ramp(p, 0.72f, 0.84f)
            if (grow > 0f && gone > 0f) {
                val cloud = Offset(bx, by - 11.5f * u - grow * 1.5f * u)
                listOf(Triple(-2f, 0.4f, 1.8f), Triple(0f, -0.6f, 2.4f), Triple(2f, 0.4f, 1.8f)).forEach { (ox, oy, r) ->
                    drawCircle(c.line.fade(a * 0.4f * gone), r * u * grow, Offset(cloud.x + ox * u, cloud.y + oy * u))
                }
                star(Offset(cloud.x - 4.2f * u, cloud.y - 1.5f * u), 1.1f * u * grow * gone, c.spark.fade(a))
                star(Offset(cloud.x + 4.3f * u, cloud.y - 2.2f * u), 0.9f * u * grow * gone, c.spark.fade(a))
            }
        }
        LoraAction.NOTES -> {
            val x0 = cx - 7.2f * u
            val y0 = top + 14.8f * u
            drawRoundRect(c.line.fade(a * 0.75f), Offset(x0, y0), Size(10 * u, 10.2f * u), CornerRadius(0.9f * u))
            drawRoundRect(c.visor.fade(a), Offset(x0 + 0.8f * u, y0 + 0.9f * u), Size(8.4f * u, 8.5f * u), CornerRadius(0.4f * u))
            drawRoundRect(c.eye.fade(a * 0.8f), Offset(cx - 4.2f * u, y0 - 0.8f * u), Size(4 * u, 1.6f * u), CornerRadius(0.5f * u))
            for (i in 0..3) {
                val lp = ((p - 0.08f - i * 0.17f) / 0.17f).coerceIn(0f, 1f)
                if (lp > 0f) {
                    val y = y0 + 2.6f * u + i * 1.9f * u
                    drawLine(c.line.fade(a), Offset(x0 + 1.8f * u, y), Offset(x0 + 1.8f * u + 6 * u * lp, y), 0.55f * u, StrokeCap.Round)
                }
            }
            val tick = ramp(p, 0.8f, 0.88f)
            if (tick > 0f) {
                val path = Path().apply {
                    moveTo(x0 + 2.6f * u, y0 + 6.4f * u); lineTo(x0 + 4.2f * u, y0 + 8 * u); lineTo(x0 + 7.6f * u, y0 + 3.6f * u)
                }
                drawPath(path, Tick.fade(a * tick), style = Stroke(1.0f * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            // The pencil in the other hand.
            val tip = Offset(handR.x - 3.2f * u, handR.y + 1.4f * u)
            drawLine(c.blush.fade(a), handR, Offset(handR.x + 1 * u, handR.y - 0.45f * u), 1.1f * u, StrokeCap.Round)
            drawLine(c.spark.fade(a), handR, Offset(tip.x + 0.75f * u, tip.y - 0.33f * u), 1.1f * u, StrokeCap.Round)
            drawLine(c.eye.fade(a), Offset(tip.x + 0.75f * u, tip.y - 0.33f * u), tip, 0.6f * u, StrokeCap.Round)
        }
        LoraAction.DIG -> {
            val lift = ramp(p, 0.66f, 0.76f)
            if (p < 0.68f) {
                val f = a * (1 - lift)
                drawLine(c.prop.fade(f), handR, Offset(handR.x + 2 * u, handR.y + 1.4f * u), 0.9f * u, StrokeCap.Round)
                drawLine(c.spark.fade(f), Offset(handR.x + 2 * u, handR.y + 1.4f * u), Offset(handR.x + 2.9f * u, handR.y + 2.3f * u), 1.8f * u, StrokeCap.Round)
            }
            if (lift > 0f) {
                val center = Offset((handL.x + handR.x) / 2, (handL.y + handR.y) / 2 - 1.2f * u)
                bone(center, 8 * u, u, c.line, a * lift)
                val shine = ramp(p, 0.76f, 0.82f)
                for (side in listOf(-1, 1)) {
                    star(Offset(center.x + side * 6.5f * u, center.y - 1.6f * u), 1.5f * u * shine * (0.75f + 0.25f * wave(s, 2f)), c.spark.fade(a))
                }
            }
        }
        LoraAction.EUREKA -> {
            val appear = ramp(p, 0.08f, 0.2f)
            if (appear > 0f) {
                val flicker = if (p < 0.2f && ((p * 60).toInt() % 2 == 0)) 0.35f else 1f
                val bulb = Offset(cx, top - 12.5f * u)
                val lit = a * appear * flicker
                drawCircle(c.spark.fade(lit * 0.22f), 5 * u, bulb)
                for (i in 0 until 8) {
                    val ang = i * PI / 4 + s * 0.6
                    val len = (1.2f + 0.4f * wave(s, 1.5f)) * u
                    val r0 = 5.2f * u
                    drawLine(
                        c.spark.fade(lit), Offset(bulb.x + (cos(ang) * r0).toFloat(), bulb.y + (sin(ang) * r0).toFloat()),
                        Offset(bulb.x + (cos(ang) * (r0 + len)).toFloat(), bulb.y + (sin(ang) * (r0 + len)).toFloat()), 0.6f * u, StrokeCap.Round,
                    )
                }
                drawRoundRect(c.line.fade(a * appear), Offset(bulb.x - 1.4f * u, bulb.y + 2.6f * u), Size(2.8f * u, 2 * u), CornerRadius(0.5f * u))
                drawCircle(c.spark.fade(lit), 3.3f * u, bulb)
                val filament = Path().apply {
                    moveTo(bulb.x - 1.1f * u, bulb.y + 1.4f * u); lineTo(bulb.x - 0.5f * u, bulb.y - 0.2f * u)
                    lineTo(bulb.x + 0.5f * u, bulb.y - 0.2f * u); lineTo(bulb.x + 1.1f * u, bulb.y + 1.4f * u)
                }
                drawPath(filament, Color.White.fade(lit * 0.9f), style = Stroke(0.45f * u, cap = StrokeCap.Round))
            }
        }
        LoraAction.BOOKS -> {
            val colors = listOf(c.prop, c.spark, Ocean, c.blush)
            for (i in 0..3) {
                val w = (15 - (i % 2) * 2.2f) * u
                val h = 2.6f * u
                val x = cx + sin(s * 3 + i * 0.9f) * 0.35f * u * i
                val yBottom = bottom + 1.5f * u - i * 2.8f * u
                drawRoundRect(colors[i].fade(a), Offset(x - w / 2, yBottom - h), Size(w, h), CornerRadius(0.5f * u))
                drawLine(Color.White.fade(a * 0.6f), Offset(x - w / 2 + 1.2f * u, yBottom - h / 2), Offset(x + w / 2 - 1.2f * u, yBottom - h / 2), 0.35f * u)
            }
        }
        LoraAction.CHART -> {
            val dirX = handL.x - shoulderL.x
            val dirY = handL.y - shoulderL.y
            val len = sqrt(dirX * dirX + dirY * dirY).coerceAtLeast(0.01f)
            val tip = Offset(handL.x + dirX / len * 6 * u, handL.y + dirY / len * 6 * u)
            drawLine(c.line.fade(a), handL, tip, 0.8f * u, StrokeCap.Round)
            drawCircle(c.spark.fade(a), 0.65f * u, tip)
        }
        LoraAction.COFFEE -> {
            val sip = bump(p, 0.25f, 0.38f, 0.58f, 0.68f)
            val mug = Offset(handR.x - 2.6f * u, handR.y - 0.4f * u)
            val w = 4.4f * u
            val h = 5 * u
            drawArc(
                c.prop.fade(a), -90f, 180f, useCenter = false, topLeft = Offset(mug.x + w / 2 - 1.3f * u, mug.y - 1.5f * u),
                size = Size(2.8f * u, 3 * u), style = Stroke(0.8f * u),
            )
            drawRoundRect(c.prop.fade(a), Offset(mug.x - w / 2, mug.y - h / 2), Size(w, h), CornerRadius(0.9f * u))
            drawLine(Color.White.fade(a * 0.6f), Offset(mug.x - w / 2 + 0.8f * u, mug.y), Offset(mug.x + w / 2 - 0.8f * u, mug.y), 0.45f * u)
            val steam = a * (1 - sip) * 0.7f
            if (steam > 0f) {
                for (i in 0..1) {
                    val x0 = mug.x + (i - 0.5f) * 1.6f * u
                    val y0 = mug.y - h / 2 - 0.6f * u
                    val path = Path().apply {
                        moveTo(x0, y0)
                        for (k in 1..8) lineTo(x0 + sin(k * 0.8f + s * 3 + i * 2f) * 0.6f * u, y0 - k * 0.5f * u)
                    }
                    drawPath(path, c.line.fade(steam), style = Stroke(0.45f * u, cap = StrokeCap.Round))
                }
            }
        }
        LoraAction.WAVE -> {
            for (i in 0..1) {
                val r = (3 + i * 1.4f) * u
                drawArc(
                    c.line.fade(a * 0.6f), -60f, 70f, useCenter = false, topLeft = Offset(handR.x - r, handR.y - r),
                    size = Size(2 * r, 2 * r), style = Stroke(0.55f * u, cap = StrokeCap.Round),
                )
            }
        }
        LoraAction.SLEEP -> {
            for (i in 0..2) {
                val k = (s * 0.35f + i / 3f) % 1f
                val z = (0.8f + k * 0.9f) * u
                val center = Offset(cx + 7 * u + k * 7 * u, top - 1 * u - k * 9 * u)
                val path = Path().apply {
                    moveTo(center.x - z, center.y - z); lineTo(center.x + z, center.y - z)
                    lineTo(center.x - z, center.y + z); lineTo(center.x + z, center.y + z)
                }
                drawPath(path, c.line.fade(a * sin(PI * k).toFloat()), style = Stroke(0.55f * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
        else -> Unit
    }
}

/** Draws Lora at [timeMs] (seconds drive the small motions, [frame] the activity); runs without an animation clock. */
internal fun DrawScope.drawLora(frame: LoraFrame, timeMs: Long, c: LoraColors) {
    val s = timeMs / 1000f
    val prev = frame.previous
    val same = prev == frame.action
    val b = ease(frame.blend)
    val now = loraPose(frame.action, frame.phase, s)
    val pose = if (prev == null) now else lerp(loraPose(prev, 1f, s), now, b)
    // Props of the previous action fade out while the new ones fade in; a repeated action keeps its props.
    val inA = if (prev == null || same) 1f else b
    val outA = if (prev == null || same) 0f else 1 - b

    val u = min(size.height / 56f, size.width / 80f)
    val cx0 = size.width / 2
    val oy = (size.height - 56 * u) / 2
    val ground = oy + 52.5f * u
    val hop = pose.hop * u
    val bw = 24 * u * (1 + (1 - pose.squash) * 0.5f)
    val bh = 21 * u * pose.squash
    val cx = cx0 + pose.dx * u
    val bottom = ground - 5 * u - hop
    val top = bottom - bh
    val g = LoraGeo(u, cx0, oy, ground, cx, top, bottom, bw, bh)

    if (prev != null && outA > 0f) drawScene(prev, 1f, s, outA, g, c)
    drawScene(frame.action, frame.phase, s, inA, g, c)

    // Shadow on the ground, smaller while hopping.
    val shadowW = bw * (1f - hop / (14 * u)).coerceAtLeast(0.4f)
    drawOval(c.shadow, Offset(cx - shadowW / 2, ground - 0.9f * u), Size(shadowW, 1.8f * u))

    // Legs with round feet.
    for (side in listOf(-1, 1)) {
        val hipX = cx + side * 5.5f * u
        val swing = if (side < 0) pose.legs else -pose.legs
        val foot = Offset(hipX + swing * 2.6f * u, bottom + 3.8f * u)
        drawLine(c.body, Offset(hipX, bottom - u), Offset(foot.x, foot.y - 0.6f * u), 2.8f * u, StrokeCap.Round)
        drawOval(c.body, Offset(foot.x + side * 0.5f * u - 2.3f * u, foot.y - 1.2f * u), Size(4.6f * u, 2.4f * u))
    }

    rotate(pose.tilt, Offset(cx, bottom)) {
        // Antenna with a small star that pulses (dim while sleeping, bright with an idea).
        val pulse = 0.5f + 0.5f * wave(s, 0.9f)
        val tip = Offset(cx + 0.7f * u * wave(s, 0.6f), top - 5.2f * u)
        drawLine(c.line, Offset(cx, top + 0.6f * u), tip, 1.3f * u, StrokeCap.Round)
        if (pose.glow > 1f) drawCircle(c.spark.fade(0.2f * (pose.glow - 1f) / 0.4f), 4.2f * u, tip)
        star(tip, 2.4f * u * (0.88f + 0.12f * pulse) * (if (pose.glow > 1f) 1.15f else 1f), c.spark.fade((0.75f + 0.25f * pulse) * min(1f, pose.glow)))

        // Body with a glossy highlight and the little star on the chest.
        drawRoundRect(c.body, Offset(cx - bw / 2, top), Size(bw, bh), CornerRadius(min(9 * u, bh / 2)))
        drawRoundRect(Color.White.copy(alpha = 0.22f), Offset(cx - bw / 2 + 2.6f * u, top + 1.1f * u), Size(5.5f * u, 1.5f * u), CornerRadius(0.75f * u))
        star(Offset(cx, top + bh * 0.84f), 1.5f * u, Color.White.copy(alpha = 0.35f))

        // Visor with eyes, cheeks and mouth.
        val visorTop = top + 3.2f * u * pose.squash
        drawRoundRect(c.visor, Offset(cx - 9 * u, visorTop), Size(18 * u, 11 * u), CornerRadius(5.5f * u))
        val blink = s % 3.7f
        val open = if (blink < 0.14f) abs(blink - 0.07f) / 0.07f else 1f
        val eyeY = visorTop + 5 * u + pose.lookY * 1.1f * u
        for (side in listOf(-1, 1)) {
            val ex = cx + side * 3.9f * u + pose.lookX * 2 * u
            when (pose.eyes) {
                LoraEyes.OPEN, LoraEyes.WIDE -> {
                    val wide = pose.eyes == LoraEyes.WIDE
                    val w = (if (wide) 3.3f else 2.8f) * u
                    val h = ((if (wide) 4.4f else 3.6f) * u * open).coerceAtLeast(0.5f * u)
                    drawRoundRect(c.eye, Offset(ex - w / 2, eyeY - h / 2), Size(w, h), CornerRadius(w / 2))
                    if (open > 0.6f) drawCircle(c.visor, (if (wide) 0.7f else 0.55f) * u, Offset(ex - 0.45f * u, eyeY - 0.8f * u))
                }
                LoraEyes.FOCUS -> drawRoundRect(c.eye, Offset(ex - 1.5f * u, eyeY - 0.75f * u), Size(3 * u, 1.5f * u), CornerRadius(0.75f * u))
                LoraEyes.HAPPY -> {
                    val path = Path().apply { moveTo(ex - 1.5f * u, eyeY + 0.7f * u); quadraticTo(ex, eyeY - 1.6f * u, ex + 1.5f * u, eyeY + 0.7f * u) }
                    drawPath(path, c.eye, style = Stroke(1f * u, cap = StrokeCap.Round))
                }
                LoraEyes.CLOSED -> {
                    val path = Path().apply { moveTo(ex - 1.5f * u, eyeY); quadraticTo(ex, eyeY + 1.3f * u, ex + 1.5f * u, eyeY) }
                    drawPath(path, c.eye, style = Stroke(0.9f * u, cap = StrokeCap.Round))
                }
            }
            val cheek = Offset(cx + side * 5.9f * u + pose.lookX * 0.8f * u, visorTop + (8 + pose.lookY * 0.3f) * u)
            drawOval(c.blush.copy(alpha = 0.55f), Offset(cheek.x - 1.4f * u, cheek.y - 0.75f * u), Size(2.8f * u, 1.5f * u))
        }
        val mx = cx + pose.lookX * 1.4f * u
        val my = visorTop + 8.3f * u + pose.lookY * 0.6f * u
        when (pose.mouth) {
            LoraMouth.SMILE -> {
                val path = Path().apply { moveTo(mx - 1.3f * u, my - 0.3f * u); quadraticTo(mx, my + 1f * u, mx + 1.3f * u, my - 0.3f * u) }
                drawPath(path, c.eye, style = Stroke(0.8f * u, cap = StrokeCap.Round))
            }
            LoraMouth.GRIN -> {
                val path = Path().apply { moveTo(mx - 1.6f * u, my - 0.5f * u); quadraticTo(mx, my + 2f * u, mx + 1.6f * u, my - 0.5f * u); close() }
                drawPath(path, c.eye)
            }
            LoraMouth.OPEN -> drawOval(c.eye, Offset(mx - 0.8f * u, my - 0.75f * u), Size(1.6f * u, 1.9f * u))
            LoraMouth.FLAT -> drawLine(c.eye, Offset(mx - 1 * u, my + 0.2f * u), Offset(mx + 1 * u, my + 0.2f * u), 0.8f * u, StrokeCap.Round)
            LoraMouth.NONE -> Unit
        }

        // Arms in front of the body; a light edge keeps them visible on it. Hands come after the held things.
        val shoulderY = top + bh * 0.69f
        fun shoulder(side: Int) = Offset(cx + side * (bw / 2 - 0.9f * u), shoulderY)
        fun arm(side: Int, degrees: Float): Offset {
            val rad = Math.toRadians(degrees.toDouble())
            val start = shoulder(side)
            val end = Offset(start.x + side * (sin(rad) * 8 * u).toFloat(), start.y + (cos(rad) * 8 * u).toFloat())
            drawLine(c.visor.copy(alpha = 0.55f), start, end, 3.8f * u, StrokeCap.Round)
            drawLine(c.body, start, end, 2.8f * u, StrokeCap.Round)
            return end
        }
        val handL = arm(-1, pose.armL)
        val handR = arm(1, pose.armR)
        val shoulderL = shoulder(-1)

        if (prev != null && outA > 0f) drawHeld(prev, 1f, s, outA, g, c, shoulderL, handL, handR, front = false)
        drawHeld(frame.action, frame.phase, s, inA, g, c, shoulderL, handL, handR, front = false)
        for (hand in listOf(handL, handR)) {
            drawCircle(c.visor.copy(alpha = 0.55f), 2.05f * u, hand)
            drawCircle(c.body, 1.6f * u, hand)
        }
        if (prev != null && outA > 0f) drawHeld(prev, 1f, s, outA, g, c, shoulderL, handL, handR, front = true)
        drawHeld(frame.action, frame.phase, s, inA, g, c, shoulderL, handL, handR, front = true)
    }
}
