package io.github.thepytales.llmhub.ui

import androidx.compose.ui.geometry.RoundRect
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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
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
    WALK(4200), READ(4800), TYPE(4200), JUGGLE(4200), SEARCH(4200), DANCE(4200), THINK(4200),
    MICROSCOPE(5000), FLASK(4600), GLOBE(4600), TELESCOPE(5000), NOTES(4800), DIG(5600), EUREKA(3200),
    BOOKS(4600), CHART(5000), COFFEE(4600), RADAR(4600), BINOCULARS(5000), CARDS(5000), SHELF(5600), NET(4600),
    PUZZLE(5000), PLANE(4800), HEADPHONES(4800), ABACUS(4600), TWIRL(2600),

    // On the start page.
    WAVE(2800), LOOK(5200), SLEEP(6000), STRETCH(4200), WATER(5400),
}

/** While the agent works: every research activity in turn, starting with walking. */
internal val WORKING_SEQUENCE = listOf(
    LoraAction.WALK, LoraAction.READ, LoraAction.RADAR, LoraAction.MICROSCOPE, LoraAction.TYPE, LoraAction.CARDS,
    LoraAction.GLOBE, LoraAction.JUGGLE, LoraAction.BINOCULARS, LoraAction.FLASK, LoraAction.PUZZLE, LoraAction.SEARCH,
    LoraAction.NOTES, LoraAction.NET, LoraAction.TELESCOPE, LoraAction.DANCE, LoraAction.SHELF, LoraAction.DIG,
    LoraAction.THINK, LoraAction.ABACUS, LoraAction.BOOKS, LoraAction.PLANE, LoraAction.CHART, LoraAction.HEADPHONES,
    LoraAction.EUREKA, LoraAction.TWIRL, LoraAction.COFFEE,
)

/** On the start page: calmer, with a look around between the activities. */
internal val HOME_SEQUENCE = listOf(
    LoraAction.LOOK, LoraAction.COFFEE, LoraAction.LOOK, LoraAction.STRETCH, LoraAction.READ, LoraAction.LOOK,
    LoraAction.WATER, LoraAction.GLOBE, LoraAction.LOOK, LoraAction.HEADPHONES, LoraAction.MICROSCOPE, LoraAction.LOOK,
    LoraAction.PLANE, LoraAction.TELESCOPE, LoraAction.LOOK, LoraAction.TWIRL, LoraAction.CHART, LoraAction.LOOK,
    LoraAction.PUZZLE,
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
    fun play(action: LoraAction) {
        if (LoraMotion.allows(action)) switch(action, now)
    }

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
    tool == null -> if (thinking) listOf(LoraAction.THINK, LoraAction.PUZZLE, LoraAction.EUREKA) else emptyList()
    tool == "web_search" || tool == "news_search" -> listOf(LoraAction.SEARCH, LoraAction.RADAR, LoraAction.TELESCOPE, LoraAction.BINOCULARS)
    "literature" in tool || tool.startsWith("zotero") || tool == "format_bibliography" || tool == "journal_new_articles" ->
        listOf(LoraAction.BOOKS, LoraAction.CARDS, LoraAction.SHELF, LoraAction.READ)
    tool == "check_references" || tool == "check_accessibility" -> listOf(LoraAction.NOTES, LoraAction.MICROSCOPE)
    tool == "run_javascript" -> listOf(LoraAction.FLASK, LoraAction.ABACUS, LoraAction.CHART)
    tool == "create_chart" -> listOf(LoraAction.CHART)
    tool == "create_email_draft" -> listOf(LoraAction.PLANE, LoraAction.TYPE)
    tool.startsWith("read_") || tool == "fetch_url" -> listOf(LoraAction.READ, LoraAction.MICROSCOPE)
    tool.startsWith("search_") || tool.startsWith("list_") -> listOf(LoraAction.DIG, LoraAction.NET, LoraAction.SEARCH)
    tool == "transcribe_local_file" -> listOf(LoraAction.HEADPHONES, LoraAction.NOTES)
    tool == "remember" -> listOf(LoraAction.NOTES)
    tool.startsWith("create_") || tool.startsWith("edit_") || tool.startsWith("write_") || tool == "fill_pdf" ||
        tool == "set_excel_cells" || tool == "reply_to_comment" -> listOf(LoraAction.TYPE, LoraAction.NOTES)
    else -> emptyList()
}

/** What Lora does when tapped while working: in turn a wave, a pirouette and a flash of inspiration. */
internal val LORA_TAP_REACTIONS = listOf(LoraAction.WAVE, LoraAction.TWIRL, LoraAction.EUREKA)

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

/** Einstellungen › Lora › Ruhigere Bewegungen (AP-07): no walking, dancing or spinning. */
object LoraMotion {
    @Volatile
    var calm: Boolean = false

    private val LIVELY = setOf(LoraAction.WALK, LoraAction.DANCE, LoraAction.TWIRL, LoraAction.JUGGLE, LoraAction.PLANE, LoraAction.DIG, LoraAction.NET)

    /** [sequence] without the lively actions when calm. */
    fun filter(sequence: List<LoraAction>): List<LoraAction> = if (!calm) sequence else sequence.filter { it !in LIVELY }.ifEmpty { listOf(LoraAction.READ) }

    fun allows(action: LoraAction) = !calm || action !in LIVELY
}

@Composable
fun rememberLoraDirector(
    sequence: List<LoraAction> = WORKING_SEQUENCE,
    start: LoraAction = sequence.first(),
    minDwellMs: Long = 1500,
): LoraDirector = remember {
    val seq = LoraMotion.filter(sequence)
    LoraDirector(seq, start.takeIf { LoraMotion.allows(it) } ?: seq.first(), minDwellMs)
}

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
 * [squash] of the body height, [glow] of the antenna star, [turn] of the head and body in degrees (0 = facing the
 * viewer, positive = towards the viewer's right, 180 = the back).
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
    val turn: Float = 0f,
    val eyes: LoraEyes = LoraEyes.OPEN,
    val mouth: LoraMouth = LoraMouth.SMILE,
)

private fun ease(x: Float): Float = (0.5f - 0.5f * cos(PI * x.coerceIn(0f, 1f))).toFloat()
private fun wave(t: Float, periods: Float) = sin(2 * PI * t * periods).toFloat()
private fun mix(a: Float, b: Float, t: Float) = a + (b - a) * t

/** Eases to 1 with a small overshoot, so arms swing a little past their new place and settle. */
private fun overshoot(x: Float): Float {
    val t = x.coerceIn(0f, 1f) - 1
    return 1 + 2.4f * t * t * t + 1.4f * t * t
}

/** 0 before [a], eased up to 1 at [b]. */
private fun ramp(p: Float, a: Float, b: Float) = ease((p - a) / (b - a))

/** Up from [a] to [b], down from [c] to [d]. */
private fun bump(p: Float, a: Float, b: Float, c: Float, d: Float) = ramp(p, a, b) * (1 - ramp(p, c, d))

/** A short arc 0 → 1 → 0 between [a] and [b]. */
private fun arc(p: Float, a: Float, b: Float) = if (p <= a || p >= b) 0f else sin(PI * (p - a) / (b - a)).toFloat()

/** [t] moves the pose, [tArms] the arms and the tilt (with a little overshoot). */
private fun lerp(a: LoraPose, b: LoraPose, t: Float, tArms: Float = t) = LoraPose(
    dx = mix(a.dx, b.dx, t),
    hop = mix(a.hop, b.hop, t),
    tilt = mix(a.tilt, b.tilt, tArms),
    lookX = mix(a.lookX, b.lookX, t),
    lookY = mix(a.lookY, b.lookY, t),
    legs = mix(a.legs, b.legs, t),
    armL = mix(a.armL, b.armL, tArms),
    armR = mix(a.armR, b.armR, tArms),
    squash = mix(a.squash, b.squash, t),
    glow = mix(a.glow, b.glow, t),
    turn = mix(a.turn, b.turn, t),
    eyes = if (t < 0.5f) a.eyes else b.eyes,
    mouth = if (t < 0.5f) a.mouth else b.mouth,
)

/** How far the clipboard of [LoraAction.NOTES] shows its front (1) or its back (0): turned to write, turned back to show. */
private fun notesShow(p: Float) = 1 - ramp(p, 0f, 0.08f) + ramp(p, 0.76f, 0.86f)

/** Where the paper plane of [LoraAction.PLANE] flies at [k] (0..1): up from the hand, a loop, then off to the right. */
private fun planePath(k: Float): Offset {
    fun quad(a: Offset, c: Offset, b: Offset, t: Float) = Offset(
        (1 - t) * (1 - t) * a.x + 2 * (1 - t) * t * c.x + t * t * b.x,
        (1 - t) * (1 - t) * a.y + 2 * (1 - t) * t * c.y + t * t * b.y,
    )
    val bottom = Offset(22f, 19.5f)
    return when {
        k < 0.25f -> quad(Offset(17f, 36f), Offset(17f, 19.5f), bottom, k / 0.25f)
        k < 0.7f -> {
            val th = PI / 2 - 2 * PI * (k - 0.25f) / 0.45f
            Offset(22f + 6.5f * cos(th).toFloat(), 13f + 6.5f * sin(th).toFloat())
        }
        else -> quad(bottom, Offset(30f, 19.5f), Offset(35f, 15f), (k - 0.7f) / 0.3f)
    }
}

/** How far the paper plane has flown at phase [p] (outside 0..1: not in the air). */
private fun planeK(p: Float) = (p - 0.46f) / 0.5f

/** Lora's posture during [a] at phase [p] (0..1) and [s] seconds. */
internal fun loraPose(a: LoraAction, p: Float, s: Float): LoraPose = when (a) {
    LoraAction.WALK -> {
        val legs = wave(s, 2.2f)
        val facing = cos(2 * PI * p).toFloat()
        LoraPose(
            dx = wave(p, 1f) * 20, hop = abs(legs) * 1.4f, lookX = facing, legs = legs, turn = 30 * facing,
            armL = 12 + 22 * legs, armR = 12 - 22 * legs,
        )
    }
    LoraAction.READ -> {
        // Reads line by line (from Lora's left to right, so from the viewer's right to left), peeks over the book once.
        val peek = bump(p, 0.5f, 0.58f, 0.7f, 0.78f)
        LoraPose(
            tilt = 2 * wave(s, 0.25f) * (1 - peek), hop = arc(p, 0.56f, 0.64f) * 1.2f,
            lookX = mix(0.5f - ((s * 0.7f) % 1f), 0f, peek), lookY = mix(0.8f, -0.2f, peek),
            armL = -20 + 2 * wave(s, 0.25f), armR = -20 - 2 * wave(s, 0.25f),
            eyes = if (peek > 0.5f) LoraEyes.HAPPY else LoraEyes.OPEN, mouth = if (peek > 0.5f) LoraMouth.GRIN else LoraMouth.SMILE,
        )
    }
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
            turn = 18 * wave(s, 0.4f) * (1 - found), armL = 14f, armR = 95 + 15 * wave(s, 0.8f),
            eyes = if (found > 0.5f) LoraEyes.WIDE else LoraEyes.OPEN, mouth = if (found > 0.5f) LoraMouth.OPEN else LoraMouth.SMILE,
        )
    }
    LoraAction.DANCE -> {
        val beat = wave(s, 1.2f)
        LoraPose(
            hop = abs(beat) * 3, tilt = 9 * beat, legs = 0.6f * beat, armL = 120 + 35 * beat, armR = 120 - 35 * beat,
            squash = 0.94f + 0.06f * abs(beat), turn = 15 * beat, eyes = LoraEyes.HAPPY, mouth = LoraMouth.GRIN,
        )
    }
    LoraAction.THINK -> LoraPose(
        tilt = -4f, lookX = 0.5f, lookY = -0.7f, legs = 0.25f * maxOf(0f, wave(s, 1.5f)) * ramp(p, 0.4f, 0.5f),
        armL = 14f, armR = -92 + 4 * wave(s, 0.7f), turn = 10f, mouth = LoraMouth.FLAT,
    )
    LoraAction.MICROSCOPE -> {
        val lean = ramp(p, 0.04f, 0.16f) * (1 - ramp(p, 0.66f, 0.74f))
        val amazed = ramp(p, 0.70f, 0.78f)
        LoraPose(
            dx = 3 * lean, hop = amazed * abs(wave(s, 1.6f)) * 2.2f, tilt = 16 * lean - 4 * amazed * (1 - ramp(p, 0.9f, 1f)),
            lookX = 0.9f * lean, lookY = 0.4f * lean - 0.5f * amazed, turn = 30 * lean,
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
            hop = startle * 1.5f, tilt = -6 * startle, lookX = 0.8f, lookY = -0.3f, turn = 12 * (1 - startle),
            armL = 14 + 6 * wave(s, 0.8f) + 40 * startle, armR = 70 + 8 * wave(s, 1.6f) * (1 - startle),
            eyes = when { startle > 0.5f -> LoraEyes.WIDE; happy > 0.5f -> LoraEyes.HAPPY; else -> LoraEyes.OPEN },
            mouth = when { startle > 0.5f -> LoraMouth.OPEN; happy > 0.5f -> LoraMouth.GRIN; else -> LoraMouth.SMILE },
        )
    }
    LoraAction.GLOBE -> {
        val happy = p > 0.5f && p < 0.85f
        LoraPose(
            dx = -1.5f, tilt = -4f, lookX = -0.9f, lookY = 0.1f, turn = -24f,
            armL = 62 + 14 * maxOf(0f, wave(s, 0.7f)), armR = 12 + 4 * wave(s, 0.5f),
            eyes = if (happy) LoraEyes.HAPPY else LoraEyes.OPEN, mouth = if (happy) LoraMouth.GRIN else LoraMouth.SMILE,
        )
    }
    LoraAction.TELESCOPE -> {
        val lean = ramp(p, 0.04f, 0.14f) * (1 - ramp(p, 0.6f, 0.68f))
        val point = ramp(p, 0.62f, 0.7f)
        LoraPose(
            dx = 2 * lean, hop = point * abs(wave(s, 1.2f)) * 1.2f, tilt = 10 * lean,
            lookX = mix(0.9f * lean, 0.6f, point), lookY = mix(-0.3f * lean, -1f, point), turn = 26 * lean + 10 * point,
            armL = 14f, armR = mix(mix(14f, 48f, lean), 158 + 6 * wave(s, 1f), point),
            eyes = when { lean > 0.5f -> LoraEyes.FOCUS; point > 0.5f -> LoraEyes.HAPPY; else -> LoraEyes.OPEN },
            mouth = if (point > 0.5f) LoraMouth.GRIN else LoraMouth.SMILE,
        )
    }
    LoraAction.NOTES -> {
        // Writes on the clipboard (its back towards the viewer), then turns it round to show the note.
        val show = notesShow(p)
        val writing = bump(p, 0.08f, 0.14f, 0.7f, 0.76f)
        val done = ramp(p, 0.84f, 0.9f)
        LoraPose(
            hop = done * abs(wave(s, 1.5f)) * 0.8f, lookX = 0.3f * writing + 0.1f * wave(s, 0.5f) * writing,
            lookY = 0.8f * (1 - show) - 0.1f * show,
            armL = -36f, armR = mix(-38f, -92 + 6 * wave(s, 3f), writing),
            eyes = if (done > 0.5f) LoraEyes.HAPPY else LoraEyes.OPEN, mouth = if (done > 0.5f) LoraMouth.GRIN else LoraMouth.SMILE,
        )
    }
    LoraAction.DIG -> {
        val crouch = ramp(p, 0f, 0.08f) * (1 - ramp(p, 0.6f, 0.7f))
        val lift = ramp(p, 0.66f, 0.76f)
        LoraPose(
            hop = lift * abs(wave(s, 1.4f)) * 2, tilt = 8 * crouch, lookX = 0.9f * crouch, lookY = 0.9f * crouch - 0.7f * lift,
            armL = mix(mix(14f, 20f, crouch), 140 - 15 * wave(s, 2f), lift), armR = mix(mix(14f, 60 + 18 * wave(s, 2.4f), crouch), 150 + 6 * wave(s, 2f), lift),
            squash = mix(1f, 0.86f, crouch), turn = 22 * crouch,
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
            turn = 20 * cos(2 * PI * p).toFloat(),
        )
    }
    LoraAction.CHART -> {
        val done = ramp(p, 0.74f, 0.8f)
        LoraPose(
            lookX = mix(-1f, 0.3f, done), lookY = -0.2f * (1 - done), armL = 96 + 10 * wave(s, 0.6f), armR = 12 + 4 * wave(s, 0.5f),
            hop = done * abs(wave(s, 1.4f)) * 0.8f, turn = -28 * (1 - done),
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
    LoraAction.RADAR -> {
        // Sends signals from the antenna and turns the head to listen, then a ping in the sky: there!
        val ping = ramp(p, 0.64f, 0.72f)
        val scan = wave(s, 0.4f) * (1 - ping)
        LoraPose(
            hop = ping * abs(wave(s, 1.5f)) * 1.2f, tilt = 3 * scan, lookX = mix(scan, 0.8f, ping), lookY = -0.5f - 0.4f * ping,
            turn = mix(28 * scan, 18f, ping), armL = mix(28 + 6 * wave(s, 0.8f), 14f, ping), armR = mix(28 - 6 * wave(s, 0.8f), 152f, ping),
            eyes = if (ping > 0.5f) LoraEyes.WIDE else LoraEyes.OPEN, mouth = if (ping > 0.5f) LoraMouth.OPEN else LoraMouth.FLAT,
        )
    }
    LoraAction.BINOCULARS -> {
        val up = ramp(p, 0.04f, 0.14f) * (1 - ramp(p, 0.64f, 0.72f))
        val seen = ramp(p, 0.68f, 0.76f)
        val sweep = wave(s, 0.4f)
        LoraPose(
            dx = 2.5f * sweep * up, tilt = 3 * sweep * up, turn = 12 * sweep * up, lookY = -0.3f * seen,
            hop = seen * abs(wave(s, 1.5f)) * 1.4f,
            armL = mix(mix(14f, -128f, up), 140 + 10 * wave(s, 1.6f), seen), armR = mix(mix(-30f, -128f, up), -60f, seen),
            eyes = if (seen > 0.5f) LoraEyes.HAPPY else LoraEyes.OPEN,
            mouth = when { seen > 0.5f -> LoraMouth.GRIN; up > 0.5f -> LoraMouth.OPEN; else -> LoraMouth.SMILE },
        )
    }
    LoraAction.CARDS -> {
        // Flips through the index cards in the box, pulls one out and holds it up.
        val lean = ramp(p, 0.04f, 0.14f) * (1 - ramp(p, 0.56f, 0.64f))
        val found = ramp(p, 0.58f, 0.68f)
        LoraPose(
            dx = 2.5f * lean, tilt = 9 * lean, turn = 30 * lean, lookX = 0.9f * lean, lookY = 0.7f * lean - 0.8f * found,
            hop = found * abs(wave(s, 1.4f)) * 1.5f,
            armL = mix(14f, 22f, lean) + 3 * wave(s, 0.6f), armR = mix(mix(14f, 75 + 10 * wave(s, 2.6f), lean), 162 + 5 * wave(s, 1.4f), found),
            eyes = when { found > 0.5f -> LoraEyes.HAPPY; lean > 0.5f -> LoraEyes.FOCUS; else -> LoraEyes.OPEN },
            mouth = if (found > 0.5f) LoraMouth.GRIN else LoraMouth.SMILE,
        )
    }
    LoraAction.SHELF -> {
        // Turns to the shelf, runs a hand along the books, pulls one out and hugs it.
        val toShelf = ramp(p, 0.02f, 0.12f) * (1 - ramp(p, 0.56f, 0.66f))
        val pull = ramp(p, 0.42f, 0.5f)
        val hug = ramp(p, 0.6f, 0.7f)
        LoraPose(
            dx = -3.5f * toShelf, tilt = -5 * toShelf + 3 * hug * wave(s, 0.7f), turn = -32 * toShelf,
            lookX = -0.9f * toShelf, lookY = -0.7f * toShelf + 0.3f * hug, hop = hug * abs(wave(s, 1.2f)) * 0.8f,
            armL = mix(mix(14f, 132 + 8 * wave(s, 1.1f) * (1 - pull), toShelf), -62f, hug), armR = mix(14f, -62f, hug),
            eyes = when { hug > 0.5f -> LoraEyes.HAPPY; toShelf > 0.5f -> LoraEyes.FOCUS; else -> LoraEyes.OPEN },
            mouth = if (hug > 0.5f) LoraMouth.GRIN else LoraMouth.SMILE,
        )
    }
    LoraAction.NET -> {
        // Pages flutter by; Lora swings the net and catches one.
        val swing = ramp(p, 0.38f, 0.5f)
        val caught = ramp(p, 0.5f, 0.58f)
        val follow = ramp(p, 0.05f, 0.45f)
        LoraPose(
            tilt = -4 * (1 - swing) + 8 * arc(p, 0.38f, 0.56f), hop = caught * abs(wave(s, 1.5f)) * 1.2f,
            lookX = mix(mix(-0.9f, 0.9f, follow), 0.8f, caught), lookY = mix(-0.6f, 0.3f, caught), turn = mix(-14f, 20f, follow),
            armL = 14 + 120 * bump(p, 0.58f, 0.64f, 0.8f, 0.86f), armR = mix(150 + 5 * wave(s, 0.9f), 75 + 4 * wave(s, 0.8f), swing),
            eyes = when { caught > 0.5f -> LoraEyes.HAPPY; swing > 0.1f -> LoraEyes.WIDE; else -> LoraEyes.OPEN },
            mouth = when { caught > 0.5f -> LoraMouth.GRIN; swing > 0.1f -> LoraMouth.OPEN; else -> LoraMouth.SMILE },
        )
    }
    LoraAction.PUZZLE -> {
        // Turns the last piece in the hand (the other hand at the chin), fits it in and cheers.
        val place = ramp(p, 0.5f, 0.62f)
        val done = ramp(p, 0.62f, 0.7f)
        LoraPose(
            turn = 22 * (1 - done), tilt = 3 * wave(s, 0.3f) * (1 - done), lookX = mix(0.8f, 0.2f, done), lookY = mix(-0.2f, -0.4f, done),
            hop = done * abs(wave(s, 1.4f)) * 1.4f,
            armL = mix(-92 + 4 * wave(s, 0.7f), 140 + 10 * wave(s, 1.4f), done),
            armR = mix(mix(55 + 8 * wave(s, 0.7f), 100f, place), 140 - 10 * wave(s, 1.4f), done),
            eyes = if (done > 0.5f) LoraEyes.HAPPY else LoraEyes.OPEN, mouth = if (done > 0.5f) LoraMouth.GRIN else LoraMouth.FLAT,
        )
    }
    LoraAction.PLANE -> {
        // Folds a paper plane, winds up, throws it and watches it loop.
        val folding = bump(p, 0.04f, 0.08f, 0.3f, 0.36f)
        val windUp = ramp(p, 0.36f, 0.44f)
        val thrown = ramp(p, 0.44f, 0.5f)
        val k = planeK(p)
        val plane = planePath(k.coerceIn(0f, 1f))
        val watching = k in 0f..1f
        LoraPose(
            tilt = -7 * windUp * (1 - thrown) + 6 * arc(p, 0.44f, 0.6f), hop = arc(p, 0.44f, 0.56f) * 1.2f,
            armL = mix(-42 + 5 * wave(s, 1.2f) * folding, 25f, windUp),
            armR = mix(mix(-42 - 5 * wave(s, 1.2f) * folding, 160f, windUp), 75f, thrown),
            lookX = if (watching) (plane.x / 20f).coerceIn(-1f, 1f) else 0.5f * windUp,
            lookY = if (watching) ((plane.y - 24f) / 12f).coerceIn(-1f, 0.6f) else 0.7f * (1 - windUp) - 0.4f * windUp,
            turn = if (watching) (plane.x * 0.8f).coerceIn(-20f, 20f) else 0f,
            eyes = if (p > 0.9f) LoraEyes.HAPPY else LoraEyes.OPEN, mouth = if (p > 0.46f) LoraMouth.GRIN else LoraMouth.SMILE,
        )
    }
    LoraAction.HEADPHONES -> {
        // Listens with the eyes closed, bobbing to the beat, then grooves.
        val beat = wave(s, 1.1f)
        val groove = bump(p, 0.45f, 0.52f, 0.8f, 0.86f)
        LoraPose(
            dx = 3 * groove * wave(s, 0.55f), hop = abs(beat) * (0.6f + 1.2f * groove), tilt = 5 * beat * (0.6f + 0.6f * groove),
            legs = 0.3f * maxOf(0f, beat), squash = 0.98f + 0.02f * abs(beat), turn = 14 * groove * wave(s, 0.55f),
            armL = mix(14 + 4 * beat, 120 + 25 * beat, groove), armR = mix(168f, 120 - 25 * beat, groove),
            eyes = if (groove > 0.5f) LoraEyes.HAPPY else LoraEyes.CLOSED, mouth = if (groove > 0.5f) LoraMouth.GRIN else LoraMouth.SMILE,
        )
    }
    LoraAction.ABACUS -> {
        val done = ramp(p, 0.8f, 0.86f)
        LoraPose(
            lookX = 0.2f * wave(s, 0.8f) * (1 - done), lookY = 0.8f * (1 - done) - 0.2f * done, hop = done * abs(wave(s, 1.4f)),
            armL = -12f, armR = mix(-48 + 14 * wave(s, 1.6f), 130f, done),
            eyes = if (done > 0.5f) LoraEyes.HAPPY else LoraEyes.OPEN, mouth = if (done > 0.5f) LoraMouth.GRIN else LoraMouth.FLAT,
        )
    }
    LoraAction.TWIRL -> {
        // Crouches, jumps, spins once in the air and lands with a squash.
        val raise = ramp(p, 0.12f, 0.22f) * (1 - ramp(p, 0.7f, 0.86f))
        LoraPose(
            hop = arc(p, 0.18f, 0.62f) * 6, squash = 1 - 0.1f * arc(p, 0.04f, 0.2f) - 0.1f * arc(p, 0.6f, 0.74f) + 0.05f * arc(p, 0.2f, 0.6f),
            armL = mix(14f, 155f, raise), armR = mix(14f, 155f, raise), turn = (360 * ramp(p, 0.22f, 0.58f)) % 360f,
            lookY = -0.3f, eyes = LoraEyes.HAPPY, mouth = LoraMouth.GRIN,
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
            hop = arc(p, 0.5f, 0.6f) * 1.6f, tilt = 4 * look, lookX = look, lookY = -0.2f * tap, turn = 32 * look,
            legs = tap * 0.35f * maxOf(0f, wave(s, 3f)), armL = 12 + 3 * wave(s, 0.5f), armR = 12 - 3 * wave(s, 0.5f),
        )
    }
    LoraAction.SLEEP -> LoraPose(
        tilt = -7f, lookY = 0.4f, armL = 6f, armR = 6f, squash = 0.93f + 0.03f * wave(s, 0.25f), glow = 0.3f,
        eyes = LoraEyes.CLOSED, mouth = LoraMouth.NONE,
    )
    LoraAction.STRETCH -> {
        // Reaches up on tiptoes with a yawn, then shakes it off.
        val up = bump(p, 0.06f, 0.24f, 0.5f, 0.62f)
        val yawn = bump(p, 0.12f, 0.22f, 0.42f, 0.5f)
        val shake = bump(p, 0.62f, 0.66f, 0.8f, 0.86f)
        LoraPose(
            squash = 1 + 0.08f * up, hop = 0.8f * up, tilt = 3 * up * wave(s, 0.5f) + 7 * shake * wave(s, 3f), lookY = -0.6f * up,
            armL = mix(14f, 172f, up) + 12 * shake * wave(s, 3f), armR = mix(14f, 172f, up) - 12 * shake * wave(s, 3f),
            eyes = when { yawn > 0.3f || up > 0.6f -> LoraEyes.CLOSED; shake > 0.5f -> LoraEyes.HAPPY; else -> LoraEyes.OPEN },
            mouth = when { yawn > 0.3f -> LoraMouth.OPEN; shake > 0.5f -> LoraMouth.GRIN; else -> LoraMouth.SMILE },
        )
    }
    LoraAction.WATER -> {
        // Waters the little plant, which grows and blooms.
        val reach = ramp(p, 0.04f, 0.12f) * (1 - ramp(p, 0.56f, 0.64f))
        val pour = bump(p, 0.12f, 0.18f, 0.5f, 0.56f)
        val admire = ramp(p, 0.68f, 0.76f)
        LoraPose(
            turn = 25f, lookX = 0.8f, lookY = 0.4f * pour + 0.1f, tilt = 4 * pour, hop = admire * arc(p, 0.76f, 0.9f) * 1.6f,
            armL = 14 + 3 * wave(s, 0.5f) + 120 * bump(p, 0.76f, 0.82f, 0.9f, 0.96f), armR = mix(45f, 105f, reach),
            eyes = if (admire > 0.5f) LoraEyes.HAPPY else LoraEyes.OPEN, mouth = if (admire > 0.5f) LoraMouth.GRIN else LoraMouth.SMILE,
        )
    }
}

/** Lora's pose in [frame] at [s] seconds: blended out of the previous action, with a small hop into the new one. */
private fun posed(frame: LoraFrame, s: Float): LoraPose {
    val now = loraPose(frame.action, frame.phase, s)
    val prev = frame.previous ?: return now
    val mixed = lerp(loraPose(prev, 1f, s), now, ease(frame.blend), overshoot(frame.blend))
    return if (prev == frame.action) mixed else mixed.copy(hop = mixed.hop + 1.8f * sin(PI * frame.blend).toFloat())
}

/** How far back Lora looks to feel the motion (leaning into it, stretching, the antenna lagging). */
private const val MOTION_MS = 60L

/** [frame] [ms] earlier. */
private fun earlier(frame: LoraFrame, ms: Long) = frame.copy(
    phase = (frame.phase - ms.toFloat() / frame.action.periodMs).coerceAtLeast(0f),
    blend = (frame.blend - ms.toFloat() / LORA_BLEND_MS).coerceAtLeast(0f),
)

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
    /** How far the face moved sideways with the turn of the head. */
    val faceDx: Float,
) {
    /** A point given in units from the top center of the 80 × 56 space. */
    fun at(x: Float, y: Float) = Offset(cx0 + x * u, oy + y * u)
}

private fun Color.fade(a: Float) = copy(alpha = alpha * a.coerceIn(0f, 1f))

/** Fixed colors of a few props that should read the same in every style. */
private val Ocean = Color(0xFF5DADE2)
private val Land = Color(0xFF4CAF50)
private val Potion = Color(0xFF2EC4B6)
private val Ivory = Color(0xFFF4EBD9)
private val Tick = Color(0xFF34A853)
private val Clay = Color(0xFFD9825B)

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

/** A small heart, about 2 [r] wide. */
private fun DrawScope.heart(center: Offset, r: Float, color: Color) {
    if (r <= 0f) return
    drawCircle(color, 0.55f * r, Offset(center.x - 0.5f * r, center.y - 0.3f * r))
    drawCircle(color, 0.55f * r, Offset(center.x + 0.5f * r, center.y - 0.3f * r))
    drawPath(
        Path().apply {
            moveTo(center.x - 1.02f * r, center.y - 0.1f * r); lineTo(center.x + 1.02f * r, center.y - 0.1f * r); lineTo(center.x, center.y + 1.05f * r); close()
        },
        color,
    )
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

/** A written page, turned by [degrees]. */
private fun DrawScope.page(center: Offset, degrees: Float, u: Float, c: LoraColors, a: Float) {
    if (a <= 0f) return
    rotate(degrees, center) {
        val tl = Offset(center.x - 1.7f * u, center.y - 2.2f * u)
        drawRoundRect(Ivory.fade(a), tl, Size(3.4f * u, 4.4f * u), CornerRadius(0.3f * u))
        drawRoundRect(c.line.fade(a * 0.6f), tl, Size(3.4f * u, 4.4f * u), CornerRadius(0.3f * u), style = Stroke(0.25f * u))
        for (i in 0..2) {
            val y = tl.y + (1.1f + i * 1.1f) * u
            drawLine(c.line.fade(a * 0.5f), Offset(tl.x + 0.6f * u, y), Offset(tl.x + (2.8f - (i % 2) * 0.8f) * u, y), 0.3f * u, StrokeCap.Round)
        }
    }
}

/** A paper plane pointing along [heading] (degrees, 0 = to the right). */
private fun DrawScope.paperPlane(at: Offset, heading: Float, u: Float, c: LoraColors, a: Float) {
    if (a <= 0f) return
    rotate(heading, at) {
        val plane = Path().apply {
            moveTo(at.x + 2.2f * u, at.y); lineTo(at.x - 1.7f * u, at.y - 1.6f * u); lineTo(at.x - 0.8f * u, at.y)
            lineTo(at.x - 1.7f * u, at.y + 1.6f * u); close()
        }
        drawPath(plane, c.visor.fade(a))
        drawPath(plane, c.line.fade(a), style = Stroke(0.3f * u, join = StrokeJoin.Round))
        drawLine(c.line.fade(a * 0.7f), Offset(at.x + 2.2f * u, at.y), Offset(at.x - 0.8f * u, at.y), 0.25f * u)
    }
}

/** A jigsaw piece filling a cell at [o] of [w] × [h], with a knob to the [right] and one [down] into its neighbours. */
private fun DrawScope.piece(o: Offset, w: Float, h: Float, u: Float, color: Color, right: Boolean, down: Boolean) {
    val gap = 0.2f * u
    drawRoundRect(color, Offset(o.x + gap, o.y + gap), Size(w - 2 * gap, h - 2 * gap), CornerRadius(0.5f * u))
    if (right) drawCircle(color, 0.95f * u, Offset(o.x + w, o.y + h / 2))
    if (down) drawCircle(color, 0.95f * u, Offset(o.x + w / 2, o.y + h))
    drawCircle(Color.White.copy(alpha = color.alpha * 0.35f), 0.5f * u, Offset(o.x + 1.4f * u, o.y + 1.3f * u))
}

/** The puzzle board of [LoraAction.PUZZLE]: corner, cell size and the colors of its four pieces. */
private class PuzzleBoard(g: LoraGeo, c: LoraColors) {
    val x0 = g.cx0 + 14 * g.u
    val y0 = g.ground - 22 * g.u
    val cw = 7.5f * g.u
    val ch = 5.5f * g.u
    val colors = listOf(Ocean, c.prop, c.spark, c.blush)
    private val margin = 1.5f * g.u
    fun cell(i: Int) = Offset(x0 + margin + (i % 2) * cw, y0 + margin + (i / 2) * ch)
}

/** Things that stand on the ground next to Lora or fly around (behind the body, not tilted with it). */
private fun DrawScope.drawScene(action: LoraAction, p: Float, s: Float, a: Float, g: LoraGeo, c: LoraColors) {
    if (a <= 0f) return
    val u = g.u
    val ground = g.ground
    val line = c.line.fade(a)
    when (action) {
        LoraAction.MICROSCOPE -> {
            val mx = g.cx0 + 23 * u
            drawRoundRect(line, Offset(mx - 6 * u, ground - 2.2f * u), Size(11 * u, 2.2f * u), CornerRadius(1.1f * u))
            drawLine(line, Offset(mx + 3 * u, ground - 2 * u), Offset(mx + 3 * u, ground - 12.5f * u), 2.2f * u, StrokeCap.Round)
            val arm = Path().apply {
                moveTo(mx + 3 * u, ground - 12.5f * u)
                quadraticTo(mx + 3.2f * u, ground - 18 * u, mx - 4.2f * u, ground - 15 * u)
            }
            drawPath(arm, line, style = Stroke(2 * u, cap = StrokeCap.Round))
            drawLine(line, Offset(mx - 4.5f * u, ground - 7.5f * u), Offset(mx + 3 * u, ground - 7.5f * u), 1.4f * u, StrokeCap.Round)
            drawCircle(c.spark.fade(a * (0.45f + 0.45f * (0.5f + 0.5f * wave(s, 1.2f)))), 1.0f * u, Offset(mx - 1.2f * u, ground - 8.5f * u))
            drawLine(c.prop.fade(a), Offset(mx - 1.2f * u, ground - 10 * u), Offset(mx - 6.8f * u, ground - 18.6f * u), 3.2f * u, StrokeCap.Round)
            drawLine(line, Offset(mx - 1.2f * u, ground - 10 * u), Offset(mx - 0.8f * u, ground - 8.9f * u), 1.8f * u, StrokeCap.Round)
            drawLine(line, Offset(mx - 6.8f * u, ground - 18.6f * u), Offset(mx - 7.6f * u, ground - 20.2f * u), 3.8f * u, StrokeCap.Round)
            drawCircle(c.prop.fade(a), 1.3f * u, Offset(mx + 3 * u, ground - 10.5f * u))
            drawCircle(c.visor.fade(a), 0.5f * u, Offset(mx + 3 * u, ground - 10.5f * u))
        }
        LoraAction.GLOBE -> {
            val gx = g.cx0 - 21 * u
            val gy = ground - 12.5f * u
            val r = 6.5f * u
            drawOval(line, Offset(gx - 4.5f * u, ground - 1.8f * u), Size(9 * u, 1.8f * u))
            drawLine(line, Offset(gx, ground - 1.2f * u), Offset(gx, gy + r + 1.2f * u), 1.4f * u, StrokeCap.Round)
            drawArc(
                c.line.fade(a * 0.7f), 90f, 180f, useCenter = false, topLeft = Offset(gx - r - 1.2f * u, gy - r - 1.2f * u),
                size = Size(2 * r + 2.4f * u, 2 * r + 2.4f * u), style = Stroke(0.8f * u, cap = StrokeCap.Round),
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
                star(g.at(x, y), 1.4f * u * (0.45f + 0.55f * tw), c.spark.fade(a))
            }
            val k = (p - 0.66f) / 0.24f
            if (k in 0f..1f) {
                val head = g.at(4 + k * 26, 3 + k * 8)
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
        LoraAction.RADAR -> {
            val ping = ramp(p, 0.64f, 0.7f)
            if (ping > 0f) {
                val blip = g.at(26f, 11f)
                val k = ((p - 0.64f) * 3f) % 1f
                drawCircle(c.prop.fade(a * (1 - k) * 0.8f), (1.2f + 5 * k) * u, blip, style = Stroke(0.5f * u))
                drawCircle(c.spark.fade(a * ping), 1.2f * u, blip)
                star(Offset(blip.x + 2.6f * u, blip.y - 2.4f * u), 1f * u * ping, c.spark.fade(a))
            }
        }
        LoraAction.CARDS -> {
            val bx = g.cx0 + 21 * u
            val boxTop = ground - 7 * u
            val found = ramp(p, 0.58f, 0.66f)
            val tabs = listOf(c.spark, c.blush, Ocean, Land)
            // Index cards, back to front; the one under Lora's fingers pops up, the found one is gone.
            val up = if (p in 0.14f..0.58f) (((p - 0.14f) / 0.44f) * 10).toInt() % 6 else -1
            for (i in 0 until 6) {
                if (i == 3 && found > 0f) continue
                val y = boxTop - 2.4f * u + i * 0.42f * u - (if (i == up) 1.4f * u else 0f)
                drawRoundRect(Ivory.fade(a), Offset(bx - 5 * u, y), Size(10 * u, 3 * u), CornerRadius(0.4f * u))
                drawRoundRect(c.line.fade(a * 0.5f), Offset(bx - 5 * u, y), Size(10 * u, 3 * u), CornerRadius(0.4f * u), style = Stroke(0.25f * u))
                drawRoundRect(tabs[i % 4].fade(a), Offset(bx - 4.4f * u + (i % 3) * 3.2f * u, y - 0.9f * u), Size(2.4f * u, 1.2f * u), CornerRadius(0.3f * u))
            }
            drawRoundRect(c.prop.fade(a), Offset(bx - 6 * u, boxTop), Size(12 * u, 7 * u), CornerRadius(0.8f * u))
            drawRoundRect(Ivory.fade(a), Offset(bx - 2.6f * u, boxTop + 1.4f * u), Size(5.2f * u, 2.2f * u), CornerRadius(0.3f * u))
            drawLine(c.line.fade(a * 0.6f), Offset(bx - 1.6f * u, boxTop + 2.5f * u), Offset(bx + 1.6f * u, boxTop + 2.5f * u), 0.35f * u, StrokeCap.Round)
            drawArc(line, 0f, 180f, useCenter = false, topLeft = Offset(bx - 1.2f * u, boxTop + 4.3f * u), size = Size(2.4f * u, 1.8f * u), style = Stroke(0.45f * u))
        }
        LoraAction.SHELF -> {
            val x0 = g.cx0 - 34 * u
            val x1 = g.cx0 - 17 * u
            val topY = ground - 25 * u
            val middle = ground - 12.5f * u
            drawRect(c.line.fade(a * 0.12f), Offset(x0, topY), Size(x1 - x0, ground - topY))
            val colors = listOf(c.prop, c.spark, Land, c.blush, Ocean, Potion)
            // Books on the upper and the lower board: (width, height); the fifth one above is the one Lora takes.
            val pull = ramp(p, 0.42f, 0.5f)
            var x = x0 + 0.9f * u
            listOf(2.4f to 9f, 2f to 10f, 2.8f to 8.5f, 2.2f to 9.5f, 2.6f to 10.2f, 2.4f to 9.2f).forEachIndexed { i, (w, h) ->
                val taken = i == 4
                if (!(taken && p >= 0.5f)) {
                    val left = x
                    withTransform({
                        if (taken) {
                            translate(top = -1.5f * u * pull)
                            rotate(18 * pull, Offset(left + w * u, middle))
                        }
                    }) {
                        drawRoundRect(colors[i].fade(a), Offset(left, middle - h * u), Size(w * u, h * u), CornerRadius(0.4f * u))
                        drawLine(Color.White.fade(a * 0.55f), Offset(left + 0.4f * u, middle - h * u + 1.6f * u), Offset(left + (w - 0.4f) * u, middle - h * u + 1.6f * u), 0.35f * u)
                    }
                }
                x += (w + 0.25f) * u
            }
            x = x0 + 0.9f * u
            listOf(2.4f to 8.5f, 2f to 10f, 2.6f to 9f, 2.2f to 7.5f, 2f to 9.5f).forEachIndexed { i, (w, h) ->
                drawRoundRect(colors[(i + 3) % colors.size].fade(a), Offset(x, ground - 0.6f * u - h * u), Size(w * u, h * u), CornerRadius(0.4f * u))
                drawLine(Color.White.fade(a * 0.55f), Offset(x + 0.4f * u, ground - (h - 1.2f) * u), Offset(x + (w - 0.4f) * u, ground - (h - 1.2f) * u), 0.35f * u)
                x += (w + 0.25f) * u
            }
            // A book leaning on its neighbour.
            rotate(-16f, Offset(x + 2.4f * u, ground - 0.6f * u)) {
                drawRoundRect(Ocean.fade(a), Offset(x + 0.2f * u, ground - 8.4f * u), Size(2.2f * u, 7.8f * u), CornerRadius(0.4f * u))
            }
            for (bx in listOf(x0, x1)) drawLine(line, Offset(bx, topY), Offset(bx, ground), 1.1f * u, StrokeCap.Round)
            for (by in listOf(topY, middle, ground - 0.6f * u)) drawLine(line, Offset(x0, by), Offset(x1, by), 1.1f * u, StrokeCap.Round)
        }
        LoraAction.NET -> {
            // The page that ends up in the net, and one that flutters past.
            val k1 = (p - 0.02f) / 0.42f
            if (k1 in 0f..1f) {
                page(
                    g.at(-34 + 61 * k1, 8 + 27 * k1 + 2.5f * sin(4 * PI * k1).toFloat()), 25 * sin(6 * PI * k1).toFloat(), u, c,
                    a * ramp(k1, 0f, 0.08f) * (1 - ramp(k1, 0.9f, 1f)),
                )
            }
            val k2 = (p - 0.12f) / 0.62f
            if (k2 in 0f..1f) {
                page(g.at(-36 + 66 * k2, 5 + 3 * k2 + 2 * sin(5 * PI * k2).toFloat()), -30 * sin(4 * PI * k2).toFloat(), u, c, a * sin(PI * k2).toFloat())
            }
        }
        LoraAction.PUZZLE -> {
            val board = PuzzleBoard(g, c)
            val x1 = board.x0 + 18 * u
            val y1 = ground - 8 * u
            drawLine(line, Offset(board.x0 + 3 * u, y1), Offset(board.x0 + 1.2f * u, ground), 1.2f * u, StrokeCap.Round)
            drawLine(line, Offset(x1 - 3 * u, y1), Offset(x1 - 1.2f * u, ground), 1.2f * u, StrokeCap.Round)
            drawRoundRect(c.visor.fade(a), Offset(board.x0, board.y0), Size(x1 - board.x0, y1 - board.y0), CornerRadius(1.2f * u))
            drawRoundRect(line, Offset(board.x0, board.y0), Size(x1 - board.x0, y1 - board.y0), CornerRadius(1.2f * u), style = Stroke(0.8f * u))
            val placed = p >= 0.62f
            for (i in listOf(3, 2, 1, 0)) {
                val o = board.cell(i)
                if (i == 0 && !placed) {
                    drawRoundRect(
                        c.line.fade(a * 0.5f), Offset(o.x + 0.3f * u, o.y + 0.3f * u), Size(board.cw - 0.6f * u, board.ch - 0.6f * u), CornerRadius(0.5f * u),
                        style = Stroke(0.35f * u, pathEffect = PathEffect.dashPathEffect(floatArrayOf(0.9f * u, 0.7f * u))),
                    )
                } else {
                    piece(o, board.cw, board.ch, u, board.colors[i].fade(a), right = i % 2 == 0, down = i < 2)
                }
            }
            val shine = ramp(p, 0.62f, 0.68f) * (1 - ramp(p, 0.9f, 1f))
            if (shine > 0f) {
                val center = Offset(board.x0 + 9 * u, board.y0 + 7 * u)
                for ((dx, dy) in listOf(-10.5f to -6f, 10.5f to -5f, 0f to -9f)) {
                    star(Offset(center.x + dx * u, center.y + dy * u), 1.4f * u * shine * (0.75f + 0.25f * wave(s, 2f + dx * 0.02f)), c.spark.fade(a))
                }
            }
        }
        LoraAction.PLANE -> {
            val k = planeK(p)
            if (k in 0f..1f) {
                val f = a * ramp(k, 0f, 0.04f) * (1 - ramp(k, 0.86f, 1f))
                for (i in 1..5) {
                    val kk = k - i * 0.022f
                    if (kk > 0f) drawCircle(c.line.fade(f * (0.4f - i * 0.07f)), 0.35f * u, planePath(kk).let { g.at(it.x, it.y) })
                }
                val pos = planePath(k)
                val behind = planePath((k - 0.01f).coerceAtLeast(0f))
                val heading = if (k < 0.01f) -70f else Math.toDegrees(atan2((pos.y - behind.y).toDouble(), (pos.x - behind.x).toDouble())).toFloat()
                paperPlane(g.at(pos.x, pos.y), heading, u, c, f)
            }
        }
        LoraAction.TWIRL -> {
            val land = arc(p, 0.6f, 0.86f)
            if (land > 0f) {
                for ((dx, dy) in listOf(-11f to -3f, 11f to -4f, -7f to -9f, 8f to -10f)) {
                    star(Offset(g.cx0 + dx * u, ground + dy * u), 1.3f * u * land, c.spark.fade(a))
                }
            }
        }
        LoraAction.WATER -> {
            val px = g.cx0 + 25 * u
            val soil = ground - 4.5f * u
            val grow = ramp(p, 0.3f, 0.72f)
            val bloom = ramp(p, 0.7f, 0.8f)
            val stemTop = soil - (3 + 8 * grow) * u
            val sway = 0.5f * u * wave(s, 0.4f)
            drawPath(
                Path().apply { moveTo(px, soil); quadraticTo(px - 0.8f * u, (soil + stemTop) / 2, px + sway, stemTop) },
                Land.fade(a), style = Stroke(0.8f * u, cap = StrokeCap.Round),
            )
            // Leaves come out as it grows.
            for ((i, at) in listOf(0.35f, 0.62f).withIndex()) {
                val leaf = ramp(grow, at - 0.3f, at + 0.05f)
                if (leaf > 0f) {
                    val y = soil - (3 + 8 * grow) * u * at
                    val side = if (i == 0) -1 else 1
                    rotate(side * 35f, Offset(px, y)) {
                        drawOval(Land.fade(a), Offset(px + (if (side < 0) -2.6f else 0f) * u * leaf, y - 0.65f * u * leaf), Size(2.6f * u * leaf, 1.3f * u * leaf))
                    }
                }
            }
            if (bloom > 0f) {
                val center = Offset(px + sway, stemTop)
                for (i in 0 until 5) {
                    val ang = 2 * PI * i / 5 + 0.3
                    drawCircle(c.blush.fade(a), 1.05f * u * bloom, Offset(center.x + (cos(ang) * 1.3f * u * bloom).toFloat(), center.y + (sin(ang) * 1.3f * u * bloom).toFloat()))
                }
                drawCircle(c.spark.fade(a), 0.8f * u * bloom, center)
                val shine = arc(p, 0.74f, 0.98f)
                star(Offset(center.x + 3.2f * u, center.y - 2.4f * u), 1.1f * u * shine, c.spark.fade(a))
            } else {
                drawCircle(Land.fade(a), 0.7f * u, Offset(px + sway, stemTop))
            }
            // The pot.
            val pot = Path().apply {
                moveTo(px - 3.2f * u, soil); lineTo(px + 3.2f * u, soil); lineTo(px + 2.3f * u, ground); lineTo(px - 2.3f * u, ground); close()
            }
            drawPath(pot, Clay.fade(a))
            drawRoundRect(Clay.fade(a), Offset(px - 3.6f * u, soil - 0.6f * u), Size(7.2f * u, 1.5f * u), CornerRadius(0.4f * u))
            drawRoundRect(Color.Black.fade(a * 0.12f), Offset(px - 3.6f * u, soil + 0.6f * u), Size(7.2f * u, 0.3f * u))
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
    shoulderL: Offset, shoulderR: Offset, handL: Offset, handR: Offset, front: Boolean,
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
            // The open book faces Lora: the viewer sees the covers, the spine in the middle and the top edges of the pages.
            val peek = bump(p, 0.5f, 0.58f, 0.7f, 0.78f)
            val bt = top + 14.8f * u + peek * 2.2f * u
            val bb = bt + 8.6f * u
            val w = 9 * u
            for (side in listOf(-1, 1)) {
                val edge = Path().apply {
                    moveTo(cx, bt + 0.4f * u); lineTo(cx + side * (w - 0.5f * u), bt - 1.1f * u)
                    lineTo(cx + side * (w - 0.5f * u), bt + 1.2f * u); lineTo(cx, bt + 1.6f * u); close()
                }
                drawPath(edge, Ivory.fade(a))
                drawPath(edge, c.line.fade(a * 0.45f), style = Stroke(0.3f * u, join = StrokeJoin.Round))
            }
            // A page turns: it rises over the top edge, from Lora's right (the viewer's left) to the other side.
            val turn = (s % 1.8f) / 1.8f
            if (turn < 0.4f) {
                val k = turn / 0.4f
                val x = cx - w * cos(PI * k).toFloat()
                val rise = 3.2f * u * sin(PI * k).toFloat()
                val sheet = Path().apply {
                    moveTo(cx, bt + 0.4f * u); lineTo(x, bt - 1.1f * u - rise); lineTo(x, bt + 2 * u); lineTo(cx, bt + 2 * u); close()
                }
                drawPath(sheet, Ivory.fade(a))
                drawPath(sheet, c.line.fade(a * 0.5f), style = Stroke(0.3f * u, join = StrokeJoin.Round))
            }
            for (side in listOf(-1, 1)) {
                val cover = Path().apply {
                    moveTo(cx + side * 0.5f * u, bt); lineTo(cx + side * w, bt + 0.3f * u)
                    lineTo(cx + side * w, bb - 0.3f * u); lineTo(cx + side * 0.5f * u, bb); close()
                }
                drawPath(cover, c.prop.fade(a))
            }
            drawRoundRect(c.eye.fade(a * 0.22f), Offset(cx - 0.8f * u, bt - 0.2f * u), Size(1.6f * u, bb - bt + 0.4f * u), CornerRadius(0.6f * u))
            // The title on the front cover, on the viewer's right.
            drawRoundRect(Ivory.fade(a), Offset(cx + 2.4f * u, bt + 1.8f * u), Size(5 * u, 2.6f * u), CornerRadius(0.4f * u))
            for (i in 0..1) {
                val y = bt + (2.6f + i * 1f) * u
                drawLine(c.line.fade(a * 0.6f), Offset(cx + 3.2f * u, y), Offset(cx + (6.6f - i * 1.2f) * u, y), 0.3f * u, StrokeCap.Round)
            }
            star(Offset(cx + 4.9f * u, bt + 6.2f * u), 1.1f * u, c.spark.fade(a))
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
            val peak = top - 12 * u
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
        LoraAction.DANCE -> notes(s, a, cx, top, g, c, 0.8f)
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
            val show = notesShow(p)
            val writing = bump(p, 0.08f, 0.14f, 0.7f, 0.76f)
            val x0 = cx - 6.5f * u
            val y0 = top + 16.2f * u - 1.2f * u * ramp(p, 0.84f, 0.9f)
            val w = 12 * u
            val h = 9 * u
            // The pencil: its tip is behind the board, the top end sticks out over the edge while Lora writes.
            if (writing > 0f) {
                drawLine(c.spark.fade(a * writing), Offset(handR.x - 1.4f * u, handR.y + 2.2f * u), Offset(handR.x + 2.4f * u, handR.y - 2.3f * u), 1.1f * u, StrokeCap.Round)
                drawLine(c.blush.fade(a * writing), Offset(handR.x + 2.4f * u, handR.y - 2.3f * u), Offset(handR.x + 3f * u, handR.y - 3f * u), 1.1f * u, StrokeCap.Round)
            }
            // Turning the board: it narrows to an edge and widens again with the other side.
            val squeeze = abs(cos(PI * (1 - show))).toFloat().coerceAtLeast(0.04f)
            withTransform({ scale(squeeze, 1f, Offset(x0 + w / 2, y0 + h / 2)) }) {
                // An opaque ground, so the board does not let Lora shine through when it fades in.
                drawRoundRect(c.visor.fade(a), Offset(x0, y0), Size(w, h), CornerRadius(1 * u))
                if (show < 0.5f) {
                    drawRoundRect(c.line.fade(a * 0.85f), Offset(x0, y0), Size(w, h), CornerRadius(1 * u))
                    drawRoundRect(Color.White.fade(a * 0.18f), Offset(x0 + 0.8f * u, y0 + 0.8f * u), Size(w - 1.6f * u, h - 1.6f * u), CornerRadius(0.6f * u), style = Stroke(0.3f * u))
                    drawRoundRect(c.eye.fade(a * 0.8f), Offset(x0 + w / 2 - 2.2f * u, y0 - 0.8f * u), Size(4.4f * u, 1.4f * u), CornerRadius(0.5f * u))
                } else {
                    drawRoundRect(c.line.fade(a * 0.75f), Offset(x0, y0), Size(w, h), CornerRadius(1 * u))
                    drawRoundRect(c.visor.fade(a), Offset(x0 + 0.8f * u, y0 + 1 * u), Size(w - 1.6f * u, h - 1.8f * u), CornerRadius(0.4f * u))
                    drawRoundRect(c.eye.fade(a * 0.8f), Offset(x0 + w / 2 - 2.2f * u, y0 - 0.8f * u), Size(4.4f * u, 1.7f * u), CornerRadius(0.5f * u))
                    for (i in 0..2) {
                        val y = y0 + 2.8f * u + i * 2 * u
                        drawLine(c.line.fade(a), Offset(x0 + 2 * u, y), Offset(x0 + (8.6f - (i % 2) * 2) * u, y), 0.6f * u, StrokeCap.Round)
                    }
                    val tick = ramp(p, 0.84f, 0.9f) + 1 - ramp(p, 0f, 0.04f)
                    if (tick > 0f) {
                        val path = Path().apply {
                            moveTo(x0 + 7.4f * u, y0 + 5.6f * u); lineTo(x0 + 8.8f * u, y0 + 7 * u); lineTo(x0 + 11.2f * u, y0 + 3.4f * u)
                        }
                        drawPath(path, Tick.fade(a * tick), style = Stroke(1f * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    }
                }
            }
        }
        LoraAction.DIG -> {
            val lift = ramp(p, 0.66f, 0.76f)
            if (p < 0.68f) {
                val f = a * (1 - lift)
                drawLine(c.prop.fade(f), handR, Offset(handR.x + 2 * u, handR.y + 1.4f * u), 0.9f * u, StrokeCap.Round)
                drawLine(c.spark.fade(f), Offset(handR.x + 2 * u, handR.y + 1.4f * u), Offset(handR.x + 2.9f * u, handR.y + 2.3f * u), 1.8f * u, StrokeCap.Round)
            }
            if (lift > 0f) {
                val center = Offset(handR.x + 0.6f * u, handR.y - 2.6f * u)
                bone(center, 8 * u, u, c.line, a * lift)
                val shine = ramp(p, 0.76f, 0.82f)
                for (side in listOf(-1, 1)) {
                    star(Offset(center.x + side * 5.5f * u, center.y - 2 * u), 1.5f * u * shine * (0.75f + 0.25f * wave(s, 2f + side * 0.4f)), c.spark.fade(a))
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
                c.line.fade(a), -90f, 180f, useCenter = false, topLeft = Offset(mug.x + w / 2 - 1.3f * u, mug.y - 1.5f * u),
                size = Size(2.8f * u, 3 * u), style = Stroke(0.9f * u),
            )
            drawRoundRect(Ivory.fade(a), Offset(mug.x - w / 2, mug.y - h / 2), Size(w, h), CornerRadius(0.9f * u))
            drawRoundRect(c.line.fade(a), Offset(mug.x - w / 2, mug.y - h / 2), Size(w, h), CornerRadius(0.9f * u), style = Stroke(0.4f * u))
            // A little heart on the mug.
            heart(Offset(mug.x, mug.y + 0.3f * u), 1.12f * u, c.blush.fade(a))
            val steam = a * (1 - sip) * 0.85f
            if (steam > 0f) {
                for (i in 0..1) {
                    val x0 = mug.x + (i - 0.5f) * 1.6f * u
                    val y0 = mug.y - h / 2 - 0.6f * u
                    val path = Path().apply {
                        moveTo(x0, y0)
                        for (k in 1..8) lineTo(x0 + sin(k * 0.8f + s * 3 + i * 2f) * 0.6f * u, y0 - k * 0.5f * u)
                    }
                    drawPath(path, c.line.fade(steam), style = Stroke(0.55f * u, cap = StrokeCap.Round))
                }
            }
        }
        LoraAction.RADAR -> {
            // Signals go out from the antenna; quieter once something answered.
            val tip = Offset(cx, top - 5.2f * u)
            val calm = 1 - 0.7f * ramp(p, 0.64f, 0.7f)
            for (i in 0..2) {
                val k = (s * 0.8f + i / 3f) % 1f
                val r = (2.6f + 10 * k) * u
                drawArc(
                    c.prop.fade(a * (1 - k) * calm), 205f, 130f, useCenter = false, topLeft = Offset(tip.x - r, tip.y - r),
                    size = Size(2 * r, 2 * r), style = Stroke(0.6f * u, cap = StrokeCap.Round),
                )
            }
        }
        LoraAction.BINOCULARS -> {
            val up = ramp(p, 0.04f, 0.14f) * (1 - ramp(p, 0.64f, 0.72f))
            val center = Offset(mix(cx + 0.5f * u, cx + g.faceDx, up), mix(top + 18.6f * u, top + 7.6f * u, up))
            if (up < 1f) {
                // The strap round the neck while they hang.
                val strap = Path().apply {
                    moveTo(center.x - 5 * u, center.y - 1.5f * u); quadraticTo(cx, top + 9 * u, center.x + 5 * u, center.y - 1.5f * u)
                }
                drawPath(strap, c.line.fade(a * (1 - up)), style = Stroke(0.45f * u))
            }
            drawRoundRect(c.line.fade(a), Offset(center.x - 1.3f * u, center.y - 1.3f * u), Size(2.6f * u, 1.8f * u), CornerRadius(0.4f * u))
            for (side in listOf(-1, 1)) {
                val barrel = Offset(center.x + side * 3.1f * u, center.y)
                drawRoundRect(c.line.fade(a), Offset(barrel.x - 2.4f * u, barrel.y - 2.7f * u), Size(4.8f * u, 5.6f * u), CornerRadius(1.6f * u))
                val lens = Offset(barrel.x, barrel.y + 0.5f * u)
                drawCircle(Ocean.fade(a), 1.65f * u, lens)
                drawCircle(Color.White.fade(a * 0.7f), 0.5f * u, Offset(lens.x - 0.55f * u, lens.y - 0.55f * u))
            }
            val seen = ramp(p, 0.7f, 0.78f)
            if (seen > 0f) {
                star(Offset(cx - 12 * u, top - 1 * u), 1.6f * u * seen * (0.75f + 0.25f * wave(s, 2f)), c.spark.fade(a))
                star(Offset(cx + 13 * u, top + 2 * u), 1.2f * u * seen * (0.75f + 0.25f * wave(s, 2.4f)), c.spark.fade(a))
            }
        }
        LoraAction.CARDS -> {
            val found = ramp(p, 0.58f, 0.66f)
            if (found > 0f) {
                val card = Offset(handR.x, handR.y - 3.4f * u)
                val tl = Offset(card.x - 3.6f * u, card.y - 2.3f * u)
                drawRoundRect(Ivory.fade(a * found), tl, Size(7.2f * u, 4.6f * u), CornerRadius(0.4f * u))
                drawRoundRect(c.line.fade(a * found * 0.6f), tl, Size(7.2f * u, 4.6f * u), CornerRadius(0.4f * u), style = Stroke(0.3f * u))
                drawRoundRect(Land.fade(a * found), Offset(tl.x + 0.8f * u, tl.y - 0.9f * u), Size(2.4f * u, 1.2f * u), CornerRadius(0.3f * u))
                for (i in 0..1) {
                    val y = tl.y + (1.6f + i * 1.4f) * u
                    drawLine(c.line.fade(a * found * 0.6f), Offset(tl.x + 1 * u, y), Offset(tl.x + (6 - i * 1.6f) * u, y), 0.35f * u, StrokeCap.Round)
                }
                star(Offset(card.x + 4.8f * u, card.y - 3 * u), 1.3f * u * ramp(p, 0.64f, 0.7f), c.spark.fade(a))
            }
        }
        LoraAction.SHELF -> {
            if (p >= 0.5f) {
                // The book: first its spine (as on the shelf), then its cover once Lora holds it to the chest.
                val hug = ramp(p, 0.6f, 0.7f)
                val center = Offset(mix(handL.x + 0.6f * u, cx, hug), mix(handL.y - 3.5f * u, top + 17.4f * u, hug))
                val w = mix(2.6f * u, 7.4f * u, hug)
                val h = mix(10.2f * u, 9.4f * u, hug)
                drawRoundRect(Ocean.fade(a), Offset(center.x - w / 2, center.y - h / 2), Size(w, h), CornerRadius(0.6f * u))
                if (hug > 0.4f) {
                    val f = a * ramp(hug, 0.4f, 1f)
                    drawRoundRect(Ivory.fade(f), Offset(center.x - w * 0.32f, center.y - h * 0.32f), Size(w * 0.64f, h * 0.22f), CornerRadius(0.3f * u))
                    star(Offset(center.x, center.y + h * 0.18f), 1.1f * u, c.spark.fade(f))
                } else {
                    drawLine(Color.White.fade(a * 0.55f), Offset(center.x - w / 2 + 0.4f * u, center.y - h / 2 + 1.6f * u), Offset(center.x + w / 2 - 0.4f * u, center.y - h / 2 + 1.6f * u), 0.35f * u)
                }
                val love = ramp(p, 0.7f, 0.76f) * (1 - ramp(p, 0.92f, 1f))
                if (love > 0f) heart(Offset(cx + 8 * u, top - 2 * u - 4 * u * ((p - 0.7f) / 0.3f)), 1.4f * u * love, c.blush.fade(a))
            }
        }
        LoraAction.NET -> {
            // The handle goes on from the arm, but always upwards (the net never drags on the ground).
            val ax = handR.x - shoulderR.x
            val ay = handR.y - shoulderR.y
            val len = sqrt(ax * ax + ay * ay).coerceAtLeast(0.01f)
            val dy = min(ay / len, -0.45f)
            val dx = sqrt(1 - dy * dy)
            val hoop = Offset(handR.x + dx * 9 * u, handR.y + dy * 9 * u)
            drawLine(c.line.fade(a), handR, Offset(hoop.x - dx * 2.6f * u, hoop.y - dy * 2.6f * u), 0.9f * u, StrokeCap.Round)
            val bag = Path().apply {
                moveTo(hoop.x - 3 * u, hoop.y)
                quadraticTo(hoop.x - 2 * u, hoop.y + 5.5f * u, hoop.x + 0.6f * u, hoop.y + 5.2f * u)
                quadraticTo(hoop.x + 3 * u, hoop.y + 3 * u, hoop.x + 3 * u, hoop.y)
                close()
            }
            drawPath(bag, c.visor.fade(a * 0.4f))
            if (p > 0.46f) page(Offset(hoop.x + 0.2f * u, hoop.y + 2.6f * u), 18f, u * 0.85f, c, a * ramp(p, 0.46f, 0.5f))
            for (i in -1..1) {
                drawLine(c.line.fade(a * 0.35f), Offset(hoop.x + i * 1.6f * u, hoop.y), Offset(hoop.x + i * 0.8f * u + 0.4f * u, hoop.y + 4.8f * u), 0.25f * u)
            }
            drawPath(bag, c.line.fade(a * 0.55f), style = Stroke(0.3f * u))
            drawOval(c.line.fade(a), Offset(hoop.x - 3 * u, hoop.y - 1.1f * u), Size(6 * u, 2.2f * u), style = Stroke(0.7f * u))
            val shine = ramp(p, 0.56f, 0.62f) * (1 - ramp(p, 0.9f, 1f))
            if (shine > 0f) star(Offset(hoop.x + 4.2f * u, hoop.y - 2.4f * u), 1.3f * u * shine, c.spark.fade(a))
        }
        LoraAction.PUZZLE -> {
            val place = ramp(p, 0.5f, 0.62f)
            if (p < 0.62f) {
                val board = PuzzleBoard(g, c)
                val slot = board.cell(0)
                val from = Offset(handR.x - 1.5f * u, handR.y - 5 * u)
                val o = Offset(mix(from.x, slot.x, place), mix(from.y, slot.y, place))
                rotate(30 * wave(s, 0.45f) * (1 - place), Offset(o.x + board.cw / 2, o.y + board.ch / 2)) {
                    piece(o, board.cw, board.ch, u, board.colors[0].fade(a), right = true, down = true)
                }
            }
        }
        LoraAction.PLANE -> {
            val fold = ramp(p, 0.06f, 0.3f)
            if (p < 0.36f) {
                // Folding: the top corners come down to the middle.
                val f = a * (1 - ramp(p, 0.3f, 0.36f))
                val x0 = cx - 4.5f * u
                val x1 = cx + 4.5f * u
                val y0 = top + 14.5f * u
                val y1 = y0 + 7 * u
                val corner = y0 + fold * 3.8f * u
                val sheet = Path().apply { moveTo(x0, y1); lineTo(x1, y1); lineTo(x1, corner); lineTo(cx, y0); lineTo(x0, corner); close() }
                drawPath(sheet, c.visor.fade(f))
                drawPath(sheet, c.line.fade(f), style = Stroke(0.3f * u, join = StrokeJoin.Round))
                if (fold > 0.05f) {
                    for (side in listOf(-1, 1)) {
                        drawLine(c.line.fade(f * 0.6f), Offset(cx, y0), Offset(cx + side * 4.5f * u * (1 - fold * 0.7f), y1), 0.25f * u)
                    }
                }
            }
            if (p in 0.3f..0.47f) {
                paperPlane(Offset(handR.x + 0.8f * u, handR.y - 1.6f * u), -60f, u, c, a * ramp(p, 0.3f, 0.36f) * (1 - ramp(p, 0.45f, 0.47f)))
            }
        }
        LoraAction.HEADPHONES -> {
            val half = g.bw / 2
            drawArc(
                c.line.fade(a), 180f, 180f, useCenter = false, topLeft = Offset(cx - half - 0.2f * u, top - 2.6f * u),
                size = Size(2 * half + 0.4f * u, 22 * u), style = Stroke(1.3f * u, cap = StrokeCap.Round),
            )
            for (side in listOf(-1, 1)) {
                val cup = Offset(cx + side * (half + 0.2f * u), top + 8.4f * u)
                drawRoundRect(c.prop.fade(a), Offset(cup.x - 1.8f * u, cup.y - 3.2f * u), Size(3.6f * u, 6.4f * u), CornerRadius(1.6f * u))
                drawRoundRect(c.line.fade(a * 0.6f), Offset(cup.x - side * 1.2f * u - 0.5f * u, cup.y - 2.4f * u), Size(1 * u, 4.8f * u), CornerRadius(0.5f * u))
            }
            notes(s, a, cx, top, g, c, 0.9f)
        }
        LoraAction.ABACUS -> {
            val x0 = cx - 8 * u
            val x1 = cx + 7 * u
            val y0 = top + 13.8f * u
            val y1 = top + 22.4f * u
            drawRoundRect(Ivory.fade(a), Offset(x0, y0), Size(x1 - x0, y1 - y0), CornerRadius(0.8f * u))
            val colors = listOf(c.prop, c.spark, c.blush)
            // Beads counted over to the right: five on the first rod, then three, then two.
            val tick = (p / 0.78f * 10).coerceIn(0f, 10f)
            val starts = floatArrayOf(0f, 5f, 8f)
            val counts = intArrayOf(5, 3, 2)
            for (r in 0..2) {
                val y = y0 + (2.2f + r * 2.2f) * u
                drawLine(c.line.fade(a * 0.7f), Offset(x0 + 0.8f * u, y), Offset(x1 - 0.8f * u, y), 0.35f * u)
                for (j in 0 until 5) {
                    val n = 4 - j
                    val moved = if (n < counts[r]) ease(tick - starts[r] - n) else 0f
                    val left = x0 + (1.8f + j * 1.9f) * u
                    val right = x1 - (1.8f + (4 - j) * 1.9f) * u
                    drawCircle(colors[r].fade(a), 0.85f * u, Offset(mix(left, right, moved), y))
                }
            }
            drawRoundRect(c.line.fade(a), Offset(x0, y0), Size(x1 - x0, y1 - y0), CornerRadius(0.8f * u), style = Stroke(0.8f * u))
            val done = ramp(p, 0.8f, 0.86f)
            if (done > 0f) star(Offset(x1 + 1.5f * u, y0 - 1.5f * u), 1.5f * u * done * (0.75f + 0.25f * wave(s, 2f)), c.spark.fade(a))
        }
        LoraAction.WATER -> {
            // The watering can hangs from the hand by its handle and tips forward to pour.
            val pour = bump(p, 0.12f, 0.18f, 0.5f, 0.56f)
            val angle = 42 * pour
            rotate(angle, handR) {
                drawRoundRect(Ocean.fade(a), Offset(handR.x - 0.6f * u, handR.y + 0.4f * u), Size(5.2f * u, 3.6f * u), CornerRadius(0.9f * u))
                drawLine(Ocean.fade(a), Offset(handR.x + 4 * u, handR.y + 2.9f * u), Offset(handR.x + 7.2f * u, handR.y - 0.2f * u), 0.9f * u, StrokeCap.Round)
                drawCircle(Ocean.fade(a), 0.75f * u, Offset(handR.x + 7.3f * u, handR.y - 0.3f * u))
                drawArc(c.line.fade(a), 180f, 180f, useCenter = false, topLeft = Offset(handR.x - 0.6f * u, handR.y - 0.9f * u), size = Size(3.6f * u, 2.6f * u), style = Stroke(0.5f * u))
                drawLine(Color.White.fade(a * 0.5f), Offset(handR.x + 0.4f * u, handR.y + 1.6f * u), Offset(handR.x + 3.6f * u, handR.y + 1.6f * u), 0.35f * u, StrokeCap.Round)
            }
            if (pour > 0.3f) {
                val rad = Math.toRadians(angle.toDouble())
                val tx = 7.3f * u
                val ty = -0.3f * u
                val tip = Offset(handR.x + (tx * cos(rad) - ty * sin(rad)).toFloat(), handR.y + (tx * sin(rad) + ty * cos(rad)).toFloat())
                val soil = g.ground - 4.5f * u
                for (i in 0 until 4) {
                    val k = (s * 1.6f + i / 4f) % 1f
                    val y = tip.y + k * (soil - tip.y)
                    drawCircle(Ocean.fade(a * pour * (1 - 0.4f * k)), 0.45f * u, Offset(tip.x + k * 0.8f * u + (i % 2) * 0.5f * u, y))
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

/** Music notes rising on both sides of Lora ([speed] rounds per second). */
private fun DrawScope.notes(s: Float, a: Float, cx: Float, top: Float, g: LoraGeo, c: LoraColors, speed: Float) {
    val u = g.u
    for (i in 0..1) {
        val k = (s * speed + i * 0.5f) % 1f
        val side = if (i == 0) -1 else 1
        val head = Offset(cx + side * (g.bw / 2 + 6 * u), top + 4 * u - k * 7 * u)
        val col = (if (i == 0) c.prop else c.spark).fade(a * sin(PI * k).toFloat())
        drawOval(col, Offset(head.x - 1.1f * u, head.y - 0.85f * u), Size(2.2f * u, 1.7f * u))
        val stem = Offset(head.x + 0.95f * u, head.y)
        drawLine(col, stem, Offset(stem.x, stem.y - 3.6f * u), 0.5f * u, StrokeCap.Round)
        drawLine(col, Offset(stem.x, stem.y - 3.6f * u), Offset(stem.x + 1.6f * u, stem.y - 2.2f * u), 0.6f * u, StrokeCap.Round)
    }
}

/** Draws Lora at [timeMs] (seconds drive the small motions, [frame] the activity); runs without an animation clock. */
internal fun DrawScope.drawLora(frame: LoraFrame, timeMs: Long, c: LoraColors) {
    val s = timeMs / 1000f
    val prev = frame.previous
    val same = prev == frame.action
    val b = ease(frame.blend)
    val base = posed(frame, s)
    // The motion of the last moment: Lora leans into it, stretches when hopping, squashes on landing, the antenna lags.
    val dt = MOTION_MS / 1000f
    val before = posed(earlier(frame, MOTION_MS), s - dt)
    val vx = (base.dx - before.dx) / dt
    val vy = (base.hop - before.hop) / dt
    val vt = (base.tilt - before.tilt) / dt
    val springy = if (base.hop < 0.5f && vy < -4f) 1 - min(0.05f, -vy * 0.003f) else 1 + min(0.04f, abs(vy) * 0.003f)
    val pose = base.copy(
        tilt = base.tilt + (vx * 0.15f).coerceIn(-5f, 5f),
        // And breathes.
        squash = base.squash * springy * (1 + 0.012f * wave(s, 0.3f)),
    )
    val lagX = (-vx * 0.04f - vt * 0.02f).coerceIn(-2.2f, 2.2f)
    val lagY = (vy * 0.05f).coerceIn(-1.2f, 1.5f)
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
    // The turn of the head: the face moves sideways and narrows; past 90 degrees the back shows.
    val turnRad = Math.toRadians(pose.turn.toDouble())
    val facing = cos(turnRad).toFloat()
    val sideways = sin(turnRad).toFloat()
    val spread = 0.6f + 0.4f * abs(facing)
    val g = LoraGeo(u, cx0, oy, ground, cx, top, bottom, bw, bh, sideways * 7 * u)

    if (prev != null && outA > 0f) drawScene(prev, 1f, s, outA, g, c)
    drawScene(frame.action, frame.phase, s, inA, g, c)

    // Shadow on the ground, smaller while hopping.
    val shadowW = bw * (1f - hop / (14 * u)).coerceAtLeast(0.4f)
    drawOval(c.shadow, Offset(cx - shadowW / 2, ground - 0.9f * u), Size(shadowW, 1.8f * u))

    // Legs with round feet; a swinging foot lifts a little.
    for (side in listOf(-1, 1)) {
        val hipX = cx + side * 5.5f * u * spread
        val swing = if (side < 0) pose.legs else -pose.legs
        val foot = Offset(hipX + swing * 2.6f * u, bottom + 3.8f * u - maxOf(0f, swing) * 0.9f * u)
        drawLine(c.body, Offset(hipX, bottom - u), Offset(foot.x, foot.y - 0.6f * u), 2.8f * u, StrokeCap.Round)
        drawOval(c.body, Offset(foot.x + side * 0.5f * u - 2.3f * u, foot.y - 1.2f * u), Size(4.6f * u, 2.4f * u))
    }

    rotate(pose.tilt, Offset(cx, bottom)) {
        // Antenna on a spring with a small star that pulses (dim while sleeping, bright with an idea).
        val pulse = 0.5f + 0.5f * wave(s, 0.9f)
        val tip = Offset(cx + 0.7f * u * wave(s, 0.6f) + lagX * u, top - 5.2f * u + lagY * u)
        val antenna = Path().apply { moveTo(cx, top + 0.6f * u); quadraticTo(cx, top - 2.4f * u, tip.x, tip.y) }
        drawPath(antenna, c.line, style = Stroke(1.3f * u, cap = StrokeCap.Round))
        if (pose.glow > 1f) drawCircle(c.spark.fade(0.2f * (pose.glow - 1f) / 0.4f), 4.2f * u, tip)
        star(tip, 2.4f * u * (0.88f + 0.12f * pulse) * (if (pose.glow > 1f) 1.15f else 1f), c.spark.fade((0.75f + 0.25f * pulse) * min(1f, pose.glow)))

        // Body with a glossy highlight (it slides the other way when Lora turns).
        val bodyShape = Path().apply { addRoundRect(RoundRect(cx - bw / 2, top, cx + bw / 2, top + bh, CornerRadius(min(9 * u, bh / 2)))) }
        drawPath(bodyShape, c.body)
        // The highlight stays on the body (at the rounded corner it would stick out).
        clipPath(bodyShape) {
            drawRoundRect(
                Color.White.copy(alpha = 0.22f), Offset(cx - bw / 2 + 2.6f * u - sideways * 2 * u, top + 1.1f * u), Size(5.5f * u, 1.5f * u), CornerRadius(0.75f * u),
            )
        }

        val visorTop = top + 3.2f * u * pose.squash
        if (facing > 0.04f) {
            // The little star on the chest and the face, both turned with the head.
            withTransform({ translate(left = sideways * 6 * u); scale(facing, 1f, Offset(cx, top + bh * 0.84f)) }) {
                star(Offset(cx, top + bh * 0.84f), 1.5f * u, Color.White.copy(alpha = 0.35f))
            }
            withTransform({ translate(left = g.faceDx); scale(facing, 1f, Offset(cx, visorTop + 5.5f * u)) }) {
                drawFace(pose, s, cx, visorTop, u, c)
            }
        } else if (facing < -0.04f) {
            // The back: a small hatch with two screws.
            withTransform({ translate(left = -g.faceDx); scale(-facing, 1f, Offset(cx, visorTop + 5.5f * u)) }) {
                drawRoundRect(Color.White.copy(alpha = 0.18f), Offset(cx - 5 * u, visorTop + 2 * u), Size(10 * u, 7 * u), CornerRadius(1.6f * u))
                for (side in listOf(-1, 1)) drawCircle(Color.White.copy(alpha = 0.35f), 0.6f * u, Offset(cx + side * 3.4f * u, visorTop + 3.4f * u))
            }
        }

        // Arms in front of the body with a soft elbow; a light edge keeps them visible on it. Hands come after the held things.
        val shoulderY = top + bh * 0.69f
        fun shoulder(side: Int) = Offset(cx + side * (bw / 2 - 0.9f * u) * spread, shoulderY)
        fun arm(side: Int, degrees: Float): Offset {
            val rad = Math.toRadians(degrees.toDouble())
            val start = shoulder(side)
            val ex = side * (sin(rad) * 8 * u).toFloat() * spread
            val ey = (cos(rad) * 8 * u).toFloat()
            val end = Offset(start.x + ex, start.y + ey)
            val len = sqrt(ex * ex + ey * ey).coerceAtLeast(0.01f)
            val bend = 1.6f * u * (1 + 0.2f * wave(s + side * 0.4f, 0.7f))
            val elbow = Offset((start.x + end.x) / 2 + side * ey / len * bend, (start.y + end.y) / 2 - side * ex / len * bend)
            val path = Path().apply { moveTo(start.x, start.y); quadraticTo(elbow.x, elbow.y, end.x, end.y) }
            drawPath(path, c.visor.copy(alpha = 0.55f), style = Stroke(3.8f * u, cap = StrokeCap.Round))
            drawPath(path, c.body, style = Stroke(2.8f * u, cap = StrokeCap.Round))
            return end
        }
        val handL = arm(-1, pose.armL)
        val handR = arm(1, pose.armR)
        val shoulderL = shoulder(-1)
        val shoulderR = shoulder(1)

        if (prev != null && outA > 0f) drawHeld(prev, 1f, s, outA, g, c, shoulderL, shoulderR, handL, handR, front = false)
        drawHeld(frame.action, frame.phase, s, inA, g, c, shoulderL, shoulderR, handL, handR, front = false)
        for (hand in listOf(handL, handR)) {
            drawCircle(c.visor.copy(alpha = 0.55f), 2.05f * u, hand)
            drawCircle(c.body, 1.6f * u, hand)
        }
        if (prev != null && outA > 0f) drawHeld(prev, 1f, s, outA, g, c, shoulderL, shoulderR, handL, handR, front = true)
        drawHeld(frame.action, frame.phase, s, inA, g, c, shoulderL, shoulderR, handL, handR, front = true)
    }
}

/** Lora's visor with eyes, cheeks and mouth; blinks now and then, sometimes twice. */
private fun DrawScope.drawFace(pose: LoraPose, s: Float, cx: Float, visorTop: Float, u: Float, c: LoraColors) {
    drawRoundRect(c.visor, Offset(cx - 9 * u, visorTop), Size(18 * u, 11 * u), CornerRadius(5.5f * u))
    fun lid(t: Float) = if (t < 0.14f) abs(t - 0.07f) / 0.07f else 1f
    val open = min(lid(s % 3.7f), lid((s + 1.9f) % 9.1f))
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
}
