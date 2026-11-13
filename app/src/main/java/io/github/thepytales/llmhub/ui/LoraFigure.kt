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
import androidx.compose.ui.graphics.Brush
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
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
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

    // PDF-Werkzeugkasten (AP-11): cutting, stapling, stamping – stapling and stamping at a side desk (AP-36).
    SCISSORS(4400), STAPLER(4800), STAMP(5000),

    // More tools (AP-36): a letter into its envelope, the calendar, the easel, the padlock, a presentation, a table,
    // translating, the highlighter, the folder.
    MAIL(5000), CALENDAR(5200), PAINT(5600), LOCK(4400), SLIDES(5600), SHEET(5000), TRANSLATE(4400), HIGHLIGHT(4800), FOLDER(4400),

    // On the start page.
    WAVE(2800), LOOK(5200), SLEEP(6000), STRETCH(4200), WATER(5400),

    // Moods and reactions (AP-36): flying to a place, just being there, nodding, cheering, shrugging, scratching the
    // head, pointing the way.
    FLY(900), IDLE(6000), NOD(2400), CHEER(2600), SHRUG(2600), SCRATCH(2800), POINT(2400),
}

/** While the agent works: every research activity in turn, starting with walking. */
internal val WORKING_SEQUENCE = listOf(
    LoraAction.WALK, LoraAction.READ, LoraAction.RADAR, LoraAction.MICROSCOPE, LoraAction.TYPE, LoraAction.CARDS,
    LoraAction.GLOBE, LoraAction.JUGGLE, LoraAction.BINOCULARS, LoraAction.FLASK, LoraAction.PUZZLE, LoraAction.SEARCH,
    LoraAction.NOTES, LoraAction.NET, LoraAction.TELESCOPE, LoraAction.DANCE, LoraAction.SHELF, LoraAction.DIG,
    LoraAction.THINK, LoraAction.ABACUS, LoraAction.BOOKS, LoraAction.PLANE, LoraAction.CHART, LoraAction.HEADPHONES,
    LoraAction.EUREKA, LoraAction.TWIRL, LoraAction.COFFEE, LoraAction.SCISSORS, LoraAction.STAPLER, LoraAction.STAMP,
    LoraAction.MAIL, LoraAction.CALENDAR, LoraAction.SLIDES, LoraAction.SHEET, LoraAction.HIGHLIGHT, LoraAction.FOLDER,
    LoraAction.LOCK, LoraAction.PAINT, LoraAction.TRANSLATE,
)

/** On the start page: calmer, with a look around between the activities. */
internal val HOME_SEQUENCE = listOf(
    LoraAction.LOOK, LoraAction.COFFEE, LoraAction.LOOK, LoraAction.STRETCH, LoraAction.READ, LoraAction.LOOK,
    LoraAction.WATER, LoraAction.GLOBE, LoraAction.LOOK, LoraAction.HEADPHONES, LoraAction.MICROSCOPE, LoraAction.LOOK,
    LoraAction.PLANE, LoraAction.TELESCOPE, LoraAction.LOOK, LoraAction.TWIRL, LoraAction.CHART, LoraAction.LOOK,
    LoraAction.PUZZLE, LoraAction.IDLE, LoraAction.TRANSLATE, LoraAction.LOOK, LoraAction.PAINT, LoraAction.IDLE,
)

/**
 * The start page by the time of day (AP-36): mornings Lora begins with a coffee, late evenings she is calmer and
 * stretches; otherwise [HOME_SEQUENCE].
 */
internal fun homeSequence(hour: Int): List<LoraAction> = when (hour) {
    in 5..9 -> listOf(LoraAction.COFFEE, LoraAction.LOOK) + HOME_SEQUENCE
    in 21..23, in 0..4 -> listOf(LoraAction.STRETCH, LoraAction.IDLE, LoraAction.READ, LoraAction.LOOK, LoraAction.COFFEE, LoraAction.IDLE, LoraAction.HEADPHONES)
    else -> HOME_SEQUENCE
}

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
    tool == "check_references" || tool == "check_accessibility" -> listOf(LoraAction.HIGHLIGHT, LoraAction.MICROSCOPE)
    tool == "run_javascript" -> listOf(LoraAction.FLASK, LoraAction.ABACUS, LoraAction.CHART)
    tool == "create_chart" -> listOf(LoraAction.CHART)
    tool == "make_pdf_fillable" -> listOf(LoraAction.STAMP, LoraAction.SCISSORS)
    tool == "fill_pdf" -> listOf(LoraAction.STAMP, LoraAction.STAPLER)
    tool == "read_pdf_form" -> listOf(LoraAction.HIGHLIGHT, LoraAction.STAMP)
    tool == "create_email_draft" || tool == "create_series" -> listOf(LoraAction.MAIL, LoraAction.PLANE)
    // Mail & Kalender (AP-13): sorting letters into folders, leafing through the mailbox, the calendar.
    tool == "outlook_move_mails" || tool == "outlook_apply_rules" || tool == "outlook_create_folder" || tool == "outlook_save_rule" -> listOf(LoraAction.FOLDER, LoraAction.MAIL)
    tool == "outlook_list_mails" || tool == "outlook_read_mail" || tool == "outlook_list_folders" -> listOf(LoraAction.MAIL, LoraAction.READ)
    tool == "outlook_save_draft" -> listOf(LoraAction.TYPE, LoraAction.MAIL)
    tool.startsWith("outlook_") -> listOf(LoraAction.CALENDAR)
    tool == "create_calendar_event" || tool == "create_semester_plan" -> listOf(LoraAction.CALENDAR)
    tool == "set_excel_cells" || tool == "edit_office_file" -> listOf(LoraAction.SHEET, LoraAction.TYPE)
    tool == "copy_document_to_folder" || tool == "move_local_file" || tool == "add_to_knowledge" -> listOf(LoraAction.FOLDER)
    tool == "save_logo" || tool == "create_design" || tool == "update_design" -> listOf(LoraAction.PAINT)
    tool == "read_document" || tool == "reply_to_comment" -> listOf(LoraAction.HIGHLIGHT, LoraAction.READ)
    tool.startsWith("read_") || tool == "fetch_url" -> listOf(LoraAction.READ, LoraAction.MICROSCOPE)
    tool.startsWith("search_") || tool.startsWith("list_") -> listOf(LoraAction.DIG, LoraAction.NET, LoraAction.SEARCH)
    tool == "transcribe_local_file" -> listOf(LoraAction.HEADPHONES, LoraAction.NOTES)
    tool == "remember" -> listOf(LoraAction.NOTES)
    tool == "create_document" -> listOf(LoraAction.TYPE, LoraAction.NOTES, LoraAction.SLIDES)
    tool.startsWith("create_") || tool.startsWith("edit_") || tool.startsWith("write_") -> listOf(LoraAction.TYPE, LoraAction.NOTES)
    else -> emptyList()
}

/** What Lora does when tapped while working: in turn a wave, a pirouette and a flash of inspiration. */
internal val LORA_TAP_REACTIONS = listOf(LoraAction.WAVE, LoraAction.TWIRL, LoraAction.EUREKA, LoraAction.CHEER, LoraAction.NOD)

/** Moods Lora shows in her panel, under answers and while flying (AP-36). */
/** Actions during which Lora's eyes follow the pointer. */
private val GAZE_ACTIONS = setOf(LoraAction.LOOK, LoraAction.IDLE, LoraAction.WAVE, LoraAction.NOD)

internal val LORA_MOODS = listOf(LoraAction.FLY, LoraAction.IDLE, LoraAction.NOD, LoraAction.CHEER, LoraAction.SHRUG, LoraAction.SCRATCH, LoraAction.POINT)

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
    // Where Lora is on screen, so her eyes can follow the pointer (AP-36).
    val center = remember { floatArrayOf(Float.NaN, Float.NaN) }
    Canvas(
        modifier
            .semantics { contentDescription = label }
            .onGloballyPositioned { val r = it.boundsInRoot(); center[0] = r.center.x; center[1] = r.center.y },
    ) {
        val t = time
        val gaze = if (center[0].isNaN() || LoraMotion.calm) null else LoraGaze.direction(Offset(center[0], center[1]), 260f * density)
        drawLora(director.frame(t), t, colors, gaze)
    }
}

/** How Lora's eyes look: [SOFT] half closed and content, [SPARKLE] wide with a sparkle in them, [WINK] one of them shut. */
internal enum class LoraEyes { OPEN, HAPPY, WIDE, CLOSED, FOCUS, SOFT, SPARKLE, WINK }

/** How Lora's mouth looks: [OOH] a small round one, [TONGUE] a smile with the tip of the tongue out (concentrating). */
internal enum class LoraMouth { SMILE, GRIN, OPEN, FLAT, NONE, OOH, TONGUE }

/**
 * Lora's posture in units: [dx] sideways, [hop] up, [tilt] in degrees around the feet, eyes [lookX]/[lookY] (-1..1),
 * [legs] swing (-1..1), arm angles in degrees from hanging down (positive = raised to the side, negative = in front),
 * [squash] of the body height, [glow] of the antenna star, [turn] of the head and body in degrees (0 = facing the
 * viewer, positive = towards the viewer's right, 180 = the back), [sway] of the body sideways over feet that stay where
 * they are (a shift of her weight; [dx] moves her as a whole and makes her take steps).
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
    val sway: Float = 0f,
    val eyes: LoraEyes = LoraEyes.OPEN,
    val mouth: LoraMouth = LoraMouth.SMILE,
    /**
     * A hand reaching for a point instead of following its arm angle (AP-36), with weight 0..1: in units from the
     * bottom middle of the body (x to the viewer's right, up is negative). Used where a hand must meet a thing –
     * the scissors at the cut, the stamp on the page.
     */
    val reachL: Offset = Offset.Zero,
    val reachLW: Float = 0f,
    val reachR: Offset = Offset.Zero,
    val reachRW: Float = 0f,
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
    sway = mix(a.sway, b.sway, t),
    eyes = if (t < 0.5f) a.eyes else b.eyes,
    mouth = if (t < 0.5f) a.mouth else b.mouth,
    reachL = when { a.reachLW == 0f -> b.reachL; b.reachLW == 0f -> a.reachL; else -> mixO(a.reachL, b.reachL, tArms) },
    reachLW = mix(a.reachLW, b.reachLW, tArms),
    reachR = when { a.reachRW == 0f -> b.reachR; b.reachRW == 0f -> a.reachR; else -> mixO(a.reachR, b.reachR, tArms) },
    reachRW = mix(a.reachRW, b.reachRW, tArms),
)

/** How far the clipboard of [LoraAction.NOTES] shows its front (1) or its back (0): turned to write, turned back to show. */
private fun notesShow(p: Float) = 1 - ramp(p, 0f, 0.08f) + ramp(p, 0.76f, 0.86f)

/** Stapler: three presses, then the stack is picked up. */
private fun staplerPress(p: Float) = maxOf(bump(p, 0.12f, 0.16f, 0.2f, 0.24f), bump(p, 0.28f, 0.32f, 0.36f, 0.4f), bump(p, 0.44f, 0.48f, 0.52f, 0.56f))

/** Stamp: up high, then down onto the page – twice. */
private fun stampLift(p: Float) = maxOf(bump(p, 0.08f, 0.16f, 0.2f, 0.25f), bump(p, 0.32f, 0.38f, 0.42f, 0.47f))
private fun stampHit(p: Float) = maxOf(bump(p, 0.24f, 0.255f, 0.28f, 0.31f), bump(p, 0.46f, 0.475f, 0.5f, 0.53f))

/** How far the scissors have cut (0..1); six snips. */
private fun scissorsCut(p: Float) = ramp(p, 0.12f, 0.64f)

private fun mixO(a: Offset, b: Offset, t: Float) = Offset(mix(a.x, b.x, t), mix(a.y, b.y, t))

private fun quadO(a: Offset, c: Offset, b: Offset, t: Float) = Offset(
    (1 - t) * (1 - t) * a.x + 2 * (1 - t) * t * c.x + t * t * b.x,
    (1 - t) * (1 - t) * a.y + 2 * (1 - t) * t * c.y + t * t * b.y,
)

/*
 * Places of the tool actions in "body units": x from the middle of Lora (to the viewer's right), y from the bottom of
 * her body (up is negative) while she stands; the ground is at y = 5. The scene draws the same places.
 */

/** The day circled on the calendar. */
private val CAL_DAY = Offset(-19.5f, -10.25f)

private val HILL = Triple(Offset(14f, -10f), Offset(19f, -16f), Offset(24.2f, -10.5f))
private val SUN = Offset(16.6f, -17f)
private const val SUN_R = 1.5f

/** Water line of the painting at [k] (0..1). */
private fun water(k: Float) = Offset(14f + 10f * k, -9.2f + 0.5f * sin(2 * PI * k * 2).toFloat())

/** Where the brush paints at [p]: the hill, the sun, the water. */
private fun paintTip(p: Float): Offset = when {
    p < 0.1f -> HILL.first
    p < 0.36f -> quadO(HILL.first, HILL.second, HILL.third, ramp(p, 0.1f, 0.36f))
    p < 0.4f -> mixO(HILL.third, Offset(SUN.x, SUN.y - SUN_R), ramp(p, 0.36f, 0.4f))
    p < 0.56f -> {
        val a = 2 * PI * ramp(p, 0.4f, 0.56f) - PI / 2
        Offset(SUN.x + SUN_R * cos(a).toFloat(), SUN.y + SUN_R * sin(a).toFloat())
    }
    p < 0.6f -> mixO(Offset(SUN.x, SUN.y - SUN_R), water(0f), ramp(p, 0.56f, 0.6f))
    p < 0.74f -> water(ramp(p, 0.6f, 0.74f))
    else -> water(1f)
}

/** The bars of the chart slide and the points of the list slide, where the pointer goes. */
private val SLIDE_BARS = listOf(Offset(-29f, -13f), Offset(-25f, -15.5f), Offset(-21f, -18f))
private val SLIDE_POINTS = listOf(Offset(-30.6f, -17f), Offset(-30.6f, -14.5f), Offset(-30.6f, -12f))

/** Where the pointer touches the slide at [p]. */
private fun slideTip(p: Float): Offset {
    fun along(list: List<Offset>, k: Float): Offset {
        val x = k * (list.size - 1)
        val i = x.toInt().coerceIn(0, list.size - 2)
        return mixO(list[i], list[i + 1], ease(x - i))
    }
    return when {
        p < 0.1f -> SLIDE_BARS.first()
        p < 0.42f -> along(SLIDE_BARS, ramp(p, 0.1f, 0.42f))
        p < 0.58f -> mixO(SLIDE_BARS.last(), SLIDE_POINTS.first(), ramp(p, 0.46f, 0.58f))
        p < 0.9f -> along(SLIDE_POINTS, ramp(p, 0.58f, 0.9f))
        else -> SLIDE_POINTS.last()
    }
}

/** The middle of cell [i] (0..11, row by row) of the table board. */
private fun sheetCell(i: Int): Offset = Offset(15.25f + 3.5f * (i % 4), -(25f - 3f * (i / 4 + 1) - 1.5f - 5f))

/** Where the highlighter is at [p]: along the first line, then the third. */
private fun highlightTip(p: Float): Offset = when {
    p < 0.12f -> Offset(-3.2f, -4.6f)
    p < 0.32f -> Offset(mix(-3.2f, 5f, ramp(p, 0.12f, 0.32f)), -4.6f)
    p < 0.44f -> mixO(Offset(5f, -4.6f), Offset(-3.2f, -0.6f), ramp(p, 0.32f, 0.44f))
    p < 0.64f -> Offset(mix(-3.2f, 5f, ramp(p, 0.44f, 0.64f)), -0.6f)
    else -> Offset(5f, -0.6f)
}

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

/** A talking mouth: open, round and closed in turn. */
private fun talk(s: Float): LoraMouth = when ((s * 6.5f).toInt() % 4) {
    0 -> LoraMouth.OPEN
    1 -> LoraMouth.OOH
    2 -> LoraMouth.SMILE
    else -> LoraMouth.OPEN
}

/** Lora's posture during [a] at phase [p] (0..1) and [s] seconds. */
internal fun loraPose(a: LoraAction, p: Float, s: Float): LoraPose = when (a) {
    LoraAction.WALK -> {
        // Strolls to one side, across to the other and home. Her feet take the steps (drawLora), arms and a small
        // waddle go with them; passing the middle on the way back she smiles at the viewer.
        val x = wave(p, 1f) * 20
        val heading = cos(2 * PI * p).toFloat()
        val step = (2 * PI * x / STRIDE).toFloat()
        val hello = bump(p, 0.42f, 0.48f, 0.56f, 0.62f)
        LoraPose(
            dx = x, lookX = heading * (1 - hello), lookY = -0.15f * hello, turn = 36 * heading * (1 - 0.7f * hello),
            tilt = -1.8f * sin(step - 0.3f) * abs(heading), armL = 12 + 22 * cos(step), armR = 12 - 22 * cos(step),
            eyes = if (hello > 0.5f) LoraEyes.HAPPY else LoraEyes.OPEN, mouth = if (hello > 0.5f) LoraMouth.GRIN else LoraMouth.SMILE,
        )
    }
    LoraAction.READ -> {
        // Reads line by line (from Lora's left to right, so from the viewer's right to left), comes across something
        // remarkable, and peeks over the book once with a wink.
        val peek = bump(p, 0.5f, 0.58f, 0.7f, 0.78f)
        val gasp = bump(p, 0.22f, 0.26f, 0.34f, 0.4f)
        LoraPose(
            tilt = 2 * wave(s, 0.25f) * (1 - peek) - 3 * gasp, hop = arc(p, 0.56f, 0.64f) * 1.2f + arc(p, 0.22f, 0.3f) * 0.7f,
            sway = 0.5f * wave(s, 0.2f) * (1 - peek),
            lookX = mix(0.5f - ((s * 0.7f) % 1f), 0f, peek) * (1 - 0.6f * gasp), lookY = mix(0.8f, -0.2f, peek),
            legs = 0.3f * maxOf(0f, wave(s, 0.9f)) * bump(p, 0.82f, 0.86f, 0.96f, 1f),
            armL = -20 + 2 * wave(s, 0.25f), armR = -20 - 2 * wave(s, 0.25f),
            eyes = when { peek > 0.5f -> LoraEyes.WINK; gasp > 0.5f -> LoraEyes.SPARKLE; else -> LoraEyes.OPEN },
            mouth = when { peek > 0.5f -> LoraMouth.GRIN; gasp > 0.5f -> LoraMouth.OOH; else -> LoraMouth.SMILE },
        )
    }
    LoraAction.TYPE -> {
        // Types in bursts with the tip of her tongue out, stops to think, and ends with a firm last key.
        val burst = 1 - bump(p, 0.4f, 0.44f, 0.54f, 0.58f)
        val last = bump(p, 0.84f, 0.88f, 0.9f, 0.93f)
        val done = ramp(p, 0.9f, 0.94f)
        val keys = wave(s, 4f) * burst * (1 - done)
        LoraPose(
            lookX = 0.25f * wave(s, 0.5f) * burst + 0.5f * (1 - burst), lookY = mix(-0.7f, 0.8f, burst) * (1 - done) - 0.1f * done,
            tilt = 1.2f * wave(s, 0.5f) * burst - 3 * (1 - burst), squash = 1 - 0.012f * abs(keys) - 0.03f * arc(p, 0.89f, 0.93f),
            armL = -40 + 9 * keys, armR = -40 - 9 * keys - 34 * last, hop = done * arc(p, 0.93f, 1f),
            eyes = if (done > 0.5f) LoraEyes.HAPPY else LoraEyes.OPEN,
            mouth = when { done > 0.5f -> LoraMouth.GRIN; burst < 0.5f -> LoraMouth.OOH; else -> LoraMouth.TONGUE },
        )
    }
    LoraAction.SCISSORS -> {
        // Holds a sheet low in front and cuts it across, snip by snip, along the line – the tip of her tongue out; the
        // lower half drops and she holds up the scissors (AP-36).
        val grab = ramp(p, 0f, 0.08f)
        val cut = scissorsCut(p)
        val tip = mix(9.4f, -1.4f, cut)
        val done = ramp(p, 0.68f, 0.76f)
        val snip = if (cut > 0f && cut < 1f) 0.5f - 0.5f * cos(2 * PI * cut * 6).toFloat() else 0f
        LoraPose(
            tilt = 2 * wave(s, 0.4f) * (1 - done), hop = done * abs(wave(s, 1.4f)) * 0.9f, squash = 1 - 0.015f * snip,
            lookX = mix(0.3f, (tip / 9f).coerceIn(-0.5f, 0.9f), grab) * (1 - done), lookY = 0.9f * grab * (1 - done) - 0.2f * done,
            reachL = Offset(-3f, -2.6f), reachLW = grab,
            reachR = Offset(tip + 5.1f, -2.6f), reachRW = grab * (1 - done), armR = mix(14f, 150f, done),
            eyes = when { done > 0.5f -> LoraEyes.HAPPY; grab > 0.5f -> LoraEyes.FOCUS; else -> LoraEyes.OPEN },
            mouth = when { done > 0.5f -> LoraMouth.GRIN; grab > 0.5f -> LoraMouth.TONGUE; else -> LoraMouth.SMILE },
        )
    }
    LoraAction.STAPLER -> {
        // At the side desk: presses the stapler three times, picks up the stack and shows it.
        val at = ramp(p, 0f, 0.08f) * (1 - ramp(p, 0.92f, 1f))
        val press = staplerPress(p)
        val pick = ramp(p, 0.58f, 0.64f)
        val show = ramp(p, 0.64f, 0.72f)
        val hand = when {
            pick <= 0f -> Offset(16.2f, -9.4f + 1.4f * press)
            show <= 0f -> mixO(Offset(16.2f, -9.4f), Offset(15f, -6.2f), pick)
            else -> mixO(Offset(15f, -6.2f), Offset(15f, -13f), show)
        }
        LoraPose(
            turn = 24 * at * (1 - show), squash = 1 - 0.05f * press, hop = show * arc(p, 0.74f, 0.88f) * 1.2f, tilt = 3 * press + 2 * at * (1 - show),
            lookX = mix(0.8f * at, 0.3f, show), lookY = mix(0.7f, -0.1f, show),
            armL = -24f + 6 * wave(s, 0.4f), reachR = hand, reachRW = at,
            eyes = when { press > 0.5f -> LoraEyes.FOCUS; show > 0.5f -> LoraEyes.HAPPY; else -> LoraEyes.OPEN },
            mouth = when { press > 0.5f -> LoraMouth.FLAT; show > 0.5f -> LoraMouth.GRIN; else -> LoraMouth.SMILE },
        )
    }
    LoraAction.STAMP -> {
        // At the side desk: lifts the stamp and brings it down on the page – twice –, puts it down and shows the page.
        val at = ramp(p, 0f, 0.08f) * (1 - ramp(p, 0.92f, 1f))
        val lift = stampLift(p)
        val hit = stampHit(p)
        val pick = ramp(p, 0.58f, 0.66f)
        val show = ramp(p, 0.66f, 0.74f)
        val hand = when {
            pick <= 0f -> Offset(mix(18.4f, 17.2f, lift), mix(-8.6f, -15f, lift) + 1.6f * hit)
            show <= 0f -> mixO(Offset(18.4f, -8.6f), Offset(17.4f, -6.2f), pick)
            else -> mixO(Offset(17.4f, -6.2f), Offset(15f, -13f), show)
        }
        LoraPose(
            turn = 24 * at * (1 - show), tilt = -3 * lift + 3 * hit + 2 * at * (1 - show), squash = 1 - 0.05f * hit + 0.03f * lift,
            lookX = mix(0.8f * at, 0.3f, show), lookY = mix(0.6f - 1.2f * lift, -0.1f, show),
            hop = show * arc(p, 0.74f, 0.88f) * 1.2f,
            armL = -24f + 6 * wave(s, 0.4f), reachR = hand, reachRW = at,
            eyes = when { hit > 0.5f -> LoraEyes.WIDE; show > 0.5f -> LoraEyes.HAPPY; lift > 0.3f -> LoraEyes.FOCUS; else -> LoraEyes.OPEN },
            mouth = when { hit > 0.5f || show > 0.5f -> LoraMouth.GRIN; lift > 0.3f -> LoraMouth.FLAT; else -> LoraMouth.SMILE },
        )
    }
    LoraAction.MAIL -> {
        // Folds a letter, slides it into an envelope, seals it with a heart and holds it up.
        val fold = ramp(p, 0.08f, 0.3f)
        val seal = ramp(p, 0.54f, 0.6f)
        val lick = bump(p, 0.48f, 0.52f, 0.56f, 0.6f)
        val up = ramp(p, 0.66f, 0.74f)
        LoraPose(
            lookY = 0.8f * (1 - up) - 0.3f * up, lookX = 0.15f * wave(s, 0.5f) * (1 - up), hop = up * arc(p, 0.74f, 0.9f) * 1.6f,
            tilt = 2 * wave(s, 0.35f) * (1 - up) + 3 * up, squash = 1 - 0.05f * arc(p, 0.68f, 0.76f),
            reachL = Offset(-4.4f + 0.8f * fold, -3f), reachLW = 1 - up, reachR = Offset(4.4f - 0.8f * fold, -3f), reachRW = 1 - up,
            armL = 14f + 16 * up, armR = mix(14f, 155f, up),
            eyes = when { up > 0.5f -> LoraEyes.HAPPY; seal > 0.5f -> LoraEyes.SOFT; else -> LoraEyes.OPEN },
            mouth = when { lick > 0.5f -> LoraMouth.TONGUE; seal > 0.5f -> LoraMouth.GRIN; else -> LoraMouth.SMILE },
        )
    }
    LoraAction.CALENDAR -> {
        // Turns to the calendar beside her, flips the page and circles a day in red.
        val to = ramp(p, 0f, 0.08f) * (1 - ramp(p, 0.9f, 1f))
        val flip = ramp(p, 0.12f, 0.3f)
        val circle = ramp(p, 0.42f, 0.68f)
        val done = ramp(p, 0.72f, 0.8f)
        val day = CAL_DAY
        val hand = when {
            p < 0.36f -> mixO(Offset(-15.4f, -14f), Offset(-16f, -18f), flip)
            else -> Offset(day.x + 1.5f * cos(2 * PI * circle - PI / 2).toFloat(), day.y + 1.5f * sin(2 * PI * circle - PI / 2).toFloat())
        }
        LoraPose(
            turn = -26 * to * (1 - 0.6f * done), lookX = -0.9f * to * (1 - done), lookY = mix(-0.5f, 0.1f, ramp(p, 0.36f, 0.42f)) * (1 - done),
            tilt = -2 * to * (1 - done), squash = 1 - 0.05f * arc(p, 0.74f, 0.82f),
            hop = done * arc(p, 0.8f, 0.92f) * 1.4f, reachL = hand, reachLW = to * (1 - done), armL = mix(14f, 140f, done),
            armR = 14f + 4 * wave(s, 0.5f),
            eyes = when { done > 0.5f -> LoraEyes.HAPPY; circle > 0f -> LoraEyes.FOCUS; else -> LoraEyes.OPEN },
            mouth = when { done > 0.5f -> LoraMouth.GRIN; circle > 0f -> LoraMouth.TONGUE; else -> LoraMouth.SMILE },
        )
    }
    LoraAction.PAINT -> {
        // At the easel: a hill, the sun, the water – the tip of her tongue out; then a step back to admire it.
        val to = ramp(p, 0f, 0.08f) * (1 - ramp(p, 0.9f, 1f))
        val admire = ramp(p, 0.76f, 0.84f)
        val tip = paintTip(p)
        LoraPose(
            turn = 22 * to * (1 - 0.7f * admire), dx = -2.5f * admire, lookX = 0.9f * to, lookY = -0.4f + 0.2f * admire,
            tilt = -5 * admire * (1 - ramp(p, 0.92f, 1f)),
            hop = admire * arc(p, 0.84f, 0.96f) * 1.2f, reachR = Offset(tip.x - 3.2f, tip.y + 1.8f), reachRW = to * (1 - admire),
            armR = mix(14f, 60f, admire), armL = 38f + 4 * wave(s, 0.4f),
            eyes = when { admire > 0.5f && p < 0.9f -> LoraEyes.SPARKLE; admire > 0.5f -> LoraEyes.HAPPY; else -> LoraEyes.FOCUS },
            mouth = when { admire > 0.5f -> LoraMouth.GRIN; to > 0.5f -> LoraMouth.TONGUE; else -> LoraMouth.SMILE },
        )
    }
    LoraAction.LOCK -> {
        // Holds a padlock, turns the key, the shackle clicks shut – a start – and the lock glows.
        val insert = ramp(p, 0.12f, 0.42f)
        val click = bump(p, 0.6f, 0.62f, 0.68f, 0.72f)
        val done = ramp(p, 0.7f, 0.78f)
        val key = mixO(Offset(8.2f, -2.8f), Offset(1.6f, -2.6f), insert)
        LoraPose(
            lookX = 0.3f * (1 - insert) * (1 - done), lookY = 0.9f * (1 - done) - 0.2f * done, hop = done * arc(p, 0.78f, 0.92f) * 1.2f + 0.8f * arc(p, 0.6f, 0.68f),
            reachL = Offset(-3.6f, -2.2f), reachLW = 1f - 0.4f * done, reachR = Offset(key.x + 2.4f, key.y), reachRW = 1 - done,
            armR = mix(14f, 140f, done), tilt = 2 * wave(s, 0.4f) * done,
            eyes = when { done > 0.5f -> LoraEyes.HAPPY; click > 0.5f -> LoraEyes.WIDE; insert > 0f -> LoraEyes.FOCUS; else -> LoraEyes.OPEN },
            mouth = when { done > 0.5f -> LoraMouth.GRIN; click > 0.5f -> LoraMouth.OOH; insert > 0f -> LoraMouth.TONGUE; else -> LoraMouth.FLAT },
        )
    }
    LoraAction.SLIDES -> {
        // Presents: points at the bars of a chart, the slide changes, then at the points of a list – talking all the while.
        val to = ramp(p, 0f, 0.08f) * (1 - ramp(p, 0.92f, 1f))
        val audience = 0.5f + 0.5f * wave(p, 2f)
        LoraPose(
            turn = -20 * to, lookX = mix(-0.9f, 0.4f, audience) * to, lookY = -0.4f, tilt = -2 * to + 1.5f * wave(s, 0.6f) * to,
            reachL = Offset(-15.2f, -9.6f), reachLW = to, armL = 14f, armR = 20f + 30 * bump(p, 0.44f, 0.5f, 0.56f, 0.62f) + 6 * wave(s, 0.6f),
            mouth = if (to > 0.5f && p !in 0.44f..0.56f) talk(s) else LoraMouth.SMILE,
            eyes = if (audience > 0.85f && to > 0.5f) LoraEyes.HAPPY else LoraEyes.OPEN,
            hop = 0.6f * arc(p, 0.46f, 0.54f),
        )
    }
    LoraAction.SHEET -> {
        // Fills a table cell by cell, the sum lights up green.
        val to = ramp(p, 0f, 0.08f) * (1 - ramp(p, 0.9f, 1f))
        val k = ramp(p, 0.1f, 0.72f)
        val cell = sheetCell((k * 12).toInt().coerceIn(0, 11))
        val done = ramp(p, 0.76f, 0.84f)
        LoraPose(
            turn = 22 * to * (1 - done), lookX = 0.8f * to * (1 - done), lookY = -0.2f, hop = done * arc(p, 0.84f, 0.96f) * 1.2f,
            squash = 1 - 0.05f * arc(p, 0.78f, 0.86f),
            reachR = Offset(cell.x - 0.6f, cell.y + 0.8f + 0.5f * abs(wave(s, 2.4f))), reachRW = to * (1 - done), armR = mix(14f, 150f, done),
            armL = 14f + 4 * wave(s, 0.5f),
            eyes = if (done > 0.5f) LoraEyes.HAPPY else LoraEyes.FOCUS,
            mouth = when { done > 0.5f -> LoraMouth.GRIN; to > 0.5f -> LoraMouth.TONGUE; else -> LoraMouth.SMILE },
        )
    }
    LoraAction.TRANSLATE -> {
        // Says something to one side, then the same to the other – the speech bubbles answer each other.
        val right = ramp(p, 0.46f, 0.56f)
        val speaking = p in 0.08f..0.42f || p in 0.58f..0.9f
        LoraPose(
            lookX = mix(-0.9f, 0.9f, right), lookY = -0.5f, turn = mix(-16f, 16f, right), tilt = mix(-3f, 3f, right), sway = mix(-1f, 1f, right),
            hop = 0.9f * (arc(p, 0.06f, 0.14f) + arc(p, 0.56f, 0.64f)) + (if (speaking) 0.25f * abs(wave(s, 1.6f)) else 0f),
            armL = 14f + 70 * bump(p, 0.06f, 0.14f, 0.38f, 0.46f), armR = 14f + 70 * bump(p, 0.56f, 0.64f, 0.86f, 0.94f),
            mouth = if (speaking) talk(s) else LoraMouth.SMILE,
            eyes = if (p in 0.42f..0.58f || p > 0.9f) LoraEyes.HAPPY else LoraEyes.OPEN,
        )
    }
    LoraAction.HIGHLIGHT -> {
        // Marks two lines of a page with the highlighter.
        val grab = ramp(p, 0f, 0.08f)
        val tip = highlightTip(p)
        val done = ramp(p, 0.7f, 0.78f)
        LoraPose(
            lookX = (tip.x / 8f).coerceIn(-0.6f, 0.8f) * (1 - done), lookY = 0.9f * grab * (1 - done) - 0.2f * done, hop = done * arc(p, 0.78f, 0.92f) * 1.2f,
            squash = 1 - 0.05f * arc(p, 0.72f, 0.8f),
            reachL = Offset(-4.4f, -2.6f), reachLW = grab, reachR = Offset(tip.x + 2.3f, tip.y + 1.1f), reachRW = grab * (1 - done),
            armR = mix(14f, 140f, done), tilt = 2 * wave(s, 0.4f) * done,
            eyes = when { done > 0.5f -> LoraEyes.HAPPY; grab > 0.5f -> LoraEyes.FOCUS; else -> LoraEyes.OPEN },
            mouth = when { done > 0.5f -> LoraMouth.GRIN; grab > 0.5f -> LoraMouth.TONGUE; else -> LoraMouth.SMILE },
        )
    }
    LoraAction.FOLDER -> {
        // A sheet drops into the folder, the cover closes, Lora hugs the folder and rocks it.
        val hug = ramp(p, 0.64f, 0.72f) * (1 - ramp(p, 0.9f, 0.98f))
        LoraPose(
            lookY = mix(-0.3f + 1.1f * ramp(p, 0.1f, 0.3f), -0.1f, hug), hop = hug * abs(wave(s, 1.2f)) * 0.8f, tilt = 4 * hug * wave(s, 0.6f),
            sway = 1.1f * hug * wave(s, 0.6f),
            reachL = mixO(Offset(-5.8f, -1.6f), Offset(-4.4f, -6.8f), hug), reachLW = 1f, reachR = mixO(Offset(5.8f, -1.6f), Offset(4.4f, -6.8f), hug), reachRW = 1f,
            eyes = when { hug > 0.5f -> LoraEyes.HAPPY; p in 0.44f..0.6f -> LoraEyes.SOFT; else -> LoraEyes.OPEN },
            mouth = if (hug > 0.5f) LoraMouth.GRIN else LoraMouth.SMILE,
        )
    }
    LoraAction.FLY -> LoraPose(
        // Flying from her panel to a place of the app (ui/LoraFlight): arms ahead, the antenna bright.
        hop = 1.6f + 0.7f * wave(s, 1.1f), squash = 1.04f, legs = 0.35f * wave(s, 1.1f), armL = 160f + 6 * wave(s, 1.1f), armR = 150f - 6 * wave(s, 1.1f),
        tilt = 1.5f * wave(s, 0.7f), lookY = -0.2f, glow = 1.35f, eyes = LoraEyes.HAPPY, mouth = LoraMouth.GRIN,
    )
    LoraAction.IDLE -> {
        // Just there: breathes, shifts her weight from one foot to the other, looks around and once up at her own star.
        val shift = wave(p, 1f)
        val star = bump(p, 0.62f, 0.68f, 0.8f, 0.86f)
        val rock = arc(p, 0.3f, 0.42f)
        LoraPose(
            sway = 0.9f * shift, tilt = 1.6f * shift + 1.2f * wave(s, 0.2f), lookX = mix(0.45f * wave(s, 0.11f), 0.12f, star),
            lookY = mix(-0.1f + 0.15f * wave(s, 0.07f), -0.95f, star), glow = 1 + 0.3f * star,
            armL = 10 + 3 * wave(s, 0.3f) + 6 * rock, armR = 10 - 3 * wave(s, 0.3f) + 6 * rock, hop = 0.6f * rock,
            eyes = if (star > 0.6f) LoraEyes.HAPPY else LoraEyes.OPEN,
        )
    }
    LoraAction.NOD -> {
        // Two nods with the whole body, smiling.
        val nod = arc(p, 0.08f, 0.28f) + arc(p, 0.34f, 0.54f)
        LoraPose(
            lookY = 0.75f * nod - 0.1f, tilt = 1.5f * nod, squash = 1 - 0.05f * nod, armL = 12f + 4 * nod, armR = 12f + 4 * nod,
            eyes = if (nod > 0.3f) LoraEyes.HAPPY else LoraEyes.OPEN, mouth = if (p in 0.06f..0.62f) LoraMouth.GRIN else LoraMouth.SMILE,
        )
    }
    LoraAction.CHEER -> {
        // Crouches and jumps with both arms up – twice, the second jump smaller –, kicking her legs; confetti falls.
        val jump = arc(p, 0.1f, 0.42f) + 0.6f * arc(p, 0.5f, 0.74f)
        val dip = arc(p, 0f, 0.12f) + 0.6f * arc(p, 0.42f, 0.52f)
        val up = ramp(p, 0.04f, 0.14f) * (1 - ramp(p, 0.84f, 0.96f))
        LoraPose(
            hop = 5 * jump, squash = 1 - 0.1f * dip + 0.04f * jump, lookY = -0.4f * up, glow = 1 + 0.4f * up,
            legs = 0.45f * wave(s, 2.2f) * minOf(1f, jump * 2),
            armL = mix(14f, 158f + 10 * wave(s, 2f), up), armR = mix(14f, 158f - 10 * wave(s, 2f), up),
            eyes = LoraEyes.HAPPY, mouth = LoraMouth.GRIN,
        )
    }
    LoraAction.SHRUG -> {
        // Nothing found: shoulders up – and once more –, palms out, the head tilted; a question mark above.
        val up = bump(p, 0.08f, 0.2f, 0.7f, 0.82f)
        val again = arc(p, 0.36f, 0.5f)
        LoraPose(
            tilt = -6 * up, sway = 0.8f * up, armL = mix(12f, 64f + 8 * again, up), armR = mix(12f, 64f + 8 * again, up),
            squash = 1 + 0.03f * up + 0.02f * again, lookX = -0.3f * up, lookY = -0.3f * up,
            mouth = if (up > 0.5f) LoraMouth.FLAT else LoraMouth.SMILE,
        )
    }
    LoraAction.SCRATCH -> {
        // Something went wrong: scratches her head and shuffles a foot, a drop of sweat.
        val up = bump(p, 0.06f, 0.16f, 0.74f, 0.86f)
        LoraPose(
            tilt = 4 * up, sway = -0.7f * up, reachR = Offset(10.5f, -20f + 0.7f * wave(s, 3f)), reachRW = up, lookX = -0.3f * up, lookY = -0.5f * up,
            legs = 0.28f * maxOf(0f, wave(s, 1.4f)) * up, armL = 12f + 8 * up,
            eyes = if (up > 0.5f) LoraEyes.FOCUS else LoraEyes.OPEN,
            mouth = when { up > 0.5f -> LoraMouth.FLAT; p > 0.86f -> LoraMouth.OOH; else -> LoraMouth.SMILE },
        )
    }
    LoraAction.POINT -> {
        // Points the way – to where she leads –, with a small hop and a nudge of the arm: there!
        val out = ramp(p, 0.06f, 0.18f) * (1 - ramp(p, 0.84f, 0.96f))
        LoraPose(
            armR = mix(12f, 92f + 3 * wave(s, 1.2f) + 7 * arc(p, 0.42f, 0.54f), out), armL = 12f + 10 * out, tilt = 3 * out, sway = 1.2f * out,
            turn = 18 * out, lookX = 0.9f * out, lookY = -0.1f,
            hop = arc(p, 0.06f, 0.22f) * 1.4f, squash = 1 - 0.06f * arc(p, 0f, 0.08f),
            eyes = LoraEyes.HAPPY, mouth = if (out > 0.5f) LoraMouth.GRIN else LoraMouth.SMILE,
        )
    }
    LoraAction.JUGGLE -> {
        // Three balls in the air, her weight going from foot to foot – and one that nearly got away.
        val beat = wave(s, 1.6f)
        val oops = bump(p, 0.56f, 0.6f, 0.66f, 0.72f)
        LoraPose(
            hop = abs(beat) * 0.6f, sway = 1.1f * wave(s, 0.8f), lookX = 0.6f * wave(s, 0.8f), lookY = -0.7f + 0.3f * oops,
            tilt = 1.5f * wave(s, 0.8f) + 4 * oops,
            armL = 60 + 25 * beat, armR = 60 - 25 * beat,
            eyes = if (oops > 0.5f) LoraEyes.WIDE else LoraEyes.OPEN, mouth = if (oops > 0.5f) LoraMouth.OOH else LoraMouth.GRIN,
        )
    }
    LoraAction.SEARCH -> {
        // Goes looking with the magnifying glass, bends down for a closer look – and finds it.
        val found = ramp(p, 0.78f, 0.86f)
        val peer = bump(p, 0.3f, 0.36f, 0.5f, 0.56f)
        LoraPose(
            dx = wave(p, 1f) * 6, hop = found * abs(wave(s, 2f)) * 1.5f, tilt = 3 * wave(s, 0.4f) * (1 - found) + 5 * peer,
            lookX = mix(wave(s, 0.4f), 0.6f, found), lookY = 0.5f * peer - 0.2f * found, squash = 1 - 0.05f * peer,
            turn = 18 * wave(s, 0.4f) * (1 - found), armL = 14f + 40 * found, armR = 95 + 15 * wave(s, 0.8f) * (1 - found),
            eyes = when { found > 0.5f -> LoraEyes.SPARKLE; peer > 0.5f -> LoraEyes.FOCUS; else -> LoraEyes.OPEN },
            mouth = when { p > 0.9f -> LoraMouth.GRIN; found > 0.5f -> LoraMouth.OPEN; peer > 0.5f -> LoraMouth.OOH; else -> LoraMouth.SMILE },
        )
    }
    LoraAction.DANCE -> {
        // Hops from side to side with swinging arms and hips; in the second half one arm up and one down in turn.
        val beat = wave(s, 1.2f)
        val second = ramp(p, 0.46f, 0.54f)
        val turns = wave(s, 0.6f)
        LoraPose(
            hop = abs(beat) * 3, tilt = 9 * beat * (1 - 0.5f * second), sway = 1.6f * beat, legs = 0.6f * beat,
            armL = mix(120 + 35 * beat, 100 + 62 * turns, second), armR = mix(120 - 35 * beat, 100 - 62 * turns, second),
            squash = 0.94f + 0.06f * abs(beat), turn = 15 * beat, eyes = LoraEyes.HAPPY, mouth = LoraMouth.GRIN,
        )
    }
    LoraAction.THINK -> {
        // Hand at the chin, eyes up; the head goes over to the other side, a foot taps – and something dawns.
        val other = ramp(p, 0.46f, 0.56f)
        val tap = ramp(p, 0.4f, 0.5f)
        val dawn = ramp(p, 0.88f, 0.94f)
        LoraPose(
            tilt = mix(-4f, 3f, other), sway = mix(-0.5f, 0.5f, other), lookX = mix(0.55f, -0.45f, other), lookY = -0.7f + 0.12f * wave(s, 0.3f),
            legs = 0.25f * maxOf(0f, wave(s, 1.5f)) * tap * (1 - dawn),
            armL = 14f, armR = -92 + 4 * wave(s, 0.7f) + 5 * maxOf(0f, wave(s, 2f)) * tap * (1 - dawn), turn = mix(10f, -8f, other),
            eyes = if (dawn > 0.5f) LoraEyes.WIDE else LoraEyes.OPEN, mouth = if (dawn > 0.5f) LoraMouth.OOH else LoraMouth.FLAT,
        )
    }
    LoraAction.MICROSCOPE -> {
        // Leans over to the microscope, one eye at the lens, turns the wheel – and is amazed.
        val lean = ramp(p, 0.04f, 0.16f) * (1 - ramp(p, 0.66f, 0.74f))
        val amazed = ramp(p, 0.70f, 0.78f)
        LoraPose(
            sway = 3 * lean, hop = amazed * abs(wave(s, 1.6f)) * 2.2f, tilt = 16 * lean - 4 * amazed * (1 - ramp(p, 0.9f, 1f)),
            lookX = 0.9f * lean, lookY = 0.4f * lean - 0.5f * amazed, turn = 30 * lean,
            armL = mix(14f, 145 - 15 * wave(s, 2f), amazed),
            armR = mix(mix(14f, 38 + 8 * wave(s, 1.5f), lean), 150 + 15 * wave(s, 2f), amazed),
            eyes = when { lean > 0.5f -> LoraEyes.WINK; amazed > 0.5f -> LoraEyes.SPARKLE; else -> LoraEyes.OPEN },
            mouth = when { amazed > 0.5f && p > 0.86f -> LoraMouth.GRIN; amazed > 0.5f -> LoraMouth.OPEN; lean > 0.5f -> LoraMouth.OOH; else -> LoraMouth.SMILE },
        )
    }
    LoraAction.FLASK -> {
        // Swirls the flask and watches it bubble; a puff makes her start, then she is delighted.
        val startle = bump(p, 0.52f, 0.57f, 0.66f, 0.74f)
        val happy = ramp(p, 0.72f, 0.8f)
        LoraPose(
            hop = startle * 1.5f + happy * abs(wave(s, 1.4f)) * 0.7f, tilt = -6 * startle, sway = -1.2f * startle, lookX = 0.8f, lookY = -0.3f + 0.2f * wave(s, 0.6f) * (1 - startle),
            turn = 12 * (1 - startle), squash = 1 + 0.04f * startle,
            armL = 14 + 6 * wave(s, 0.8f) + 40 * startle, armR = 70 + 8 * wave(s, 1.6f) * (1 - startle),
            eyes = when { startle > 0.5f -> LoraEyes.WIDE; happy > 0.5f -> LoraEyes.HAPPY; else -> LoraEyes.OPEN },
            mouth = when { startle > 0.5f -> LoraMouth.OPEN; happy > 0.5f -> LoraMouth.GRIN; else -> LoraMouth.SMILE },
        )
    }
    LoraAction.GLOBE -> {
        // Spins the globe, spots a place and is glad about it.
        val spot = bump(p, 0.36f, 0.42f, 0.48f, 0.54f)
        val happy = p > 0.5f && p < 0.85f
        LoraPose(
            sway = -1.5f, tilt = -4f + 2 * spot, lookX = -0.9f, lookY = 0.1f + 0.2f * wave(s, 0.5f), turn = -24f,
            hop = 0.9f * arc(p, 0.5f, 0.6f),
            armL = 62 + 14 * maxOf(0f, wave(s, 0.7f)), armR = 12 + 4 * wave(s, 0.5f) + 20 * (if (happy) 1f else 0f) * arc(p, 0.5f, 0.85f),
            eyes = when { happy -> LoraEyes.HAPPY; spot > 0.5f -> LoraEyes.WIDE; else -> LoraEyes.OPEN },
            mouth = when { happy -> LoraMouth.GRIN; spot > 0.5f -> LoraMouth.OOH; else -> LoraMouth.SMILE },
        )
    }
    LoraAction.TELESCOPE -> {
        // One eye at the telescope, then she points up at a shooting star.
        val lean = ramp(p, 0.04f, 0.14f) * (1 - ramp(p, 0.6f, 0.68f))
        val point = ramp(p, 0.62f, 0.7f)
        LoraPose(
            sway = 2 * lean, hop = point * abs(wave(s, 1.2f)) * 1.2f, tilt = 10 * lean,
            lookX = mix(0.9f * lean, 0.6f, point), lookY = mix(-0.3f * lean, -1f, point), turn = 26 * lean + 10 * point,
            armL = 14f + 16 * point, armR = mix(mix(14f, 48f, lean), 158 + 6 * wave(s, 1f), point),
            eyes = when { lean > 0.5f -> LoraEyes.WINK; point > 0.5f && p < 0.9f -> LoraEyes.SPARKLE; point > 0.5f -> LoraEyes.HAPPY; else -> LoraEyes.OPEN },
            mouth = when { point > 0.5f && p < 0.82f -> LoraMouth.OPEN; point > 0.5f -> LoraMouth.GRIN; lean > 0.5f -> LoraMouth.OOH; else -> LoraMouth.SMILE },
        )
    }
    LoraAction.NOTES -> {
        // Writes on the clipboard (its back towards the viewer), then turns it round to show the note.
        val show = notesShow(p)
        val writing = bump(p, 0.08f, 0.14f, 0.7f, 0.76f)
        val done = ramp(p, 0.84f, 0.9f)
        LoraPose(
            hop = done * abs(wave(s, 1.5f)) * 0.8f, lookX = 0.3f * writing + 0.1f * wave(s, 0.5f) * writing,
            lookY = 0.8f * (1 - show) - 0.1f * show, tilt = 1.5f * wave(s, 0.4f) * writing,
            armL = -36f, armR = mix(-38f, -92 + 6 * wave(s, 3f), writing),
            eyes = if (done > 0.5f) LoraEyes.HAPPY else LoraEyes.OPEN,
            mouth = when { done > 0.5f -> LoraMouth.GRIN; writing > 0.5f -> LoraMouth.TONGUE; else -> LoraMouth.SMILE },
        )
    }
    LoraAction.DIG -> {
        // Crouches and digs, stroke by stroke – there is something! – and lifts the find.
        val crouch = ramp(p, 0f, 0.08f) * (1 - ramp(p, 0.6f, 0.7f))
        val lift = ramp(p, 0.66f, 0.76f)
        val find = bump(p, 0.52f, 0.56f, 0.64f, 0.68f)
        LoraPose(
            hop = lift * abs(wave(s, 1.4f)) * 2, tilt = 8 * crouch, lookX = 0.9f * crouch, lookY = 0.9f * crouch - 0.7f * lift,
            armL = mix(mix(14f, 20f, crouch), 140 - 15 * wave(s, 2f), lift), armR = mix(mix(14f, 60 + 18 * wave(s, 2.4f), crouch), 150 + 6 * wave(s, 2f), lift),
            squash = mix(1f, 0.86f + 0.02f * wave(s, 2.4f), crouch), turn = 22 * crouch,
            eyes = when { lift > 0.5f -> LoraEyes.HAPPY; find > 0.5f -> LoraEyes.WIDE; crouch > 0.5f -> LoraEyes.FOCUS; else -> LoraEyes.OPEN },
            mouth = when { lift > 0.5f -> LoraMouth.GRIN; find > 0.5f -> LoraMouth.OOH; crouch > 0.5f -> LoraMouth.TONGUE; else -> LoraMouth.SMILE },
        )
    }
    LoraAction.EUREKA -> {
        // An idea: a start, a crouch, a jump with one arm up – the light bulb is on.
        val jump = arc(p, 0.2f, 0.45f)
        val raise = ramp(p, 0.12f, 0.22f)
        LoraPose(
            hop = jump * 5, lookX = 0.2f, lookY = -1f + 0.5f * ramp(p, 0.5f, 0.7f),
            armL = mix(14f, 50f, raise), armR = mix(14f, 165f, raise),
            squash = 1 - 0.1f * arc(p, 0.1f, 0.22f) + 0.04f * jump, glow = 1.4f,
            eyes = when { p < 0.1f -> LoraEyes.WIDE; p < 0.5f -> LoraEyes.SPARKLE; else -> LoraEyes.HAPPY },
            mouth = when { p < 0.12f -> LoraMouth.OOH; p < 0.45f -> LoraMouth.OPEN; else -> LoraMouth.GRIN },
        )
    }
    LoraAction.BOOKS -> {
        // Carries a pile of books to and fro; it sways, and she keeps an eye on it.
        val heading = cos(2 * PI * p).toFloat()
        val wobble = wave(s, 0.9f)
        LoraPose(
            dx = wave(p, 1f) * 8, tilt = 3 * wobble, lookX = 0.35f * heading, lookY = -0.6f, armL = -15f, armR = -15f, turn = 20 * heading,
            eyes = if (abs(wobble) > 0.85f) LoraEyes.WIDE else LoraEyes.OPEN, mouth = if (abs(wobble) > 0.85f) LoraMouth.OOH else LoraMouth.SMILE,
        )
    }
    LoraAction.CHART -> {
        // Shows the bars of a chart as they grow, the pointer rising with them; the trend delights her.
        val done = ramp(p, 0.74f, 0.8f)
        LoraPose(
            lookX = mix(-1f, 0.3f, done), lookY = -0.2f * (1 - done), armL = 88 + 16 * ramp(p, 0.08f, 0.7f) + 5 * wave(s, 0.6f), armR = 12 + 4 * wave(s, 0.5f) + 30 * done,
            hop = done * abs(wave(s, 1.4f)) * 0.8f, turn = -28 * (1 - done), sway = -0.8f * (1 - done), tilt = -2 * (1 - done),
            eyes = when { done > 0.5f && p < 0.9f -> LoraEyes.SPARKLE; done > 0.5f -> LoraEyes.HAPPY; else -> LoraEyes.OPEN },
            mouth = if (done > 0.5f) LoraMouth.GRIN else LoraMouth.SMILE,
        )
    }
    LoraAction.COFFEE -> {
        // Blows on the coffee, takes a sip with her eyes closed, and sighs contentedly.
        val blow = bump(p, 0.06f, 0.1f, 0.18f, 0.22f)
        val sip = bump(p, 0.25f, 0.38f, 0.58f, 0.68f)
        val ahh = bump(p, 0.62f, 0.68f, 0.86f, 0.94f)
        LoraPose(
            tilt = -3 * sip + 1.5f * ahh * wave(s, 0.4f), sway = 0.7f * ahh * wave(s, 0.4f), lookX = 0.3f * (1 - sip) * (1 - blow), lookY = 0.2f - 0.3f * sip + 0.4f * blow,
            squash = 1 - 0.025f * ahh,
            armL = 12 + 3 * wave(s, 0.5f), armR = mix(-55f, -115f, sip) + 2 * wave(s, 0.8f) - 22 * blow,
            eyes = when { sip > 0.5f -> LoraEyes.CLOSED; ahh > 0.5f -> LoraEyes.SOFT; else -> LoraEyes.OPEN },
            mouth = when { sip > 0.5f -> LoraMouth.NONE; ahh > 0.5f -> LoraMouth.GRIN; blow > 0.5f -> LoraMouth.OOH; else -> LoraMouth.SMILE },
        )
    }
    LoraAction.RADAR -> {
        // Sends signals from the antenna and turns the head to listen, eyes half closed; then a ping in the sky: there!
        val ping = ramp(p, 0.64f, 0.72f)
        val scan = wave(s, 0.4f) * (1 - ping)
        LoraPose(
            hop = ping * abs(wave(s, 1.5f)) * 1.2f, tilt = 3 * scan, sway = 0.8f * scan, lookX = mix(scan, 0.8f, ping), lookY = -0.5f - 0.4f * ping,
            turn = mix(28 * scan, 18f, ping), armL = mix(28 + 6 * wave(s, 0.8f), 14f, ping), armR = mix(28 - 6 * wave(s, 0.8f), 152f, ping),
            glow = 1 + 0.25f * (1 - ping) * (0.5f + 0.5f * wave(s, 0.8f)),
            eyes = if (ping > 0.5f) LoraEyes.SPARKLE else LoraEyes.SOFT,
            mouth = when { ping > 0.5f && p > 0.86f -> LoraMouth.GRIN; ping > 0.5f -> LoraMouth.OPEN; else -> LoraMouth.FLAT },
        )
    }
    LoraAction.BINOCULARS -> {
        // Lifts the binoculars, sweeps the horizon, lowers them and waves: seen it!
        val up = ramp(p, 0.04f, 0.14f) * (1 - ramp(p, 0.64f, 0.72f))
        val seen = ramp(p, 0.68f, 0.76f)
        val sweep = wave(s, 0.4f)
        LoraPose(
            sway = 2.5f * sweep * up, tilt = 3 * sweep * up, turn = 12 * sweep * up, lookY = -0.3f * seen,
            hop = seen * abs(wave(s, 1.5f)) * 1.4f,
            armL = mix(mix(14f, -128f, up), 140 + 10 * wave(s, 1.6f), seen), armR = mix(mix(-30f, -128f, up), -60f, seen),
            eyes = if (seen > 0.5f) LoraEyes.HAPPY else LoraEyes.OPEN,
            mouth = when { seen > 0.5f -> LoraMouth.GRIN; up > 0.5f -> LoraMouth.OOH; else -> LoraMouth.SMILE },
        )
    }
    LoraAction.CARDS -> {
        // Flips through the index cards in the box, pulls one out and holds it up.
        val lean = ramp(p, 0.04f, 0.14f) * (1 - ramp(p, 0.56f, 0.64f))
        val found = ramp(p, 0.58f, 0.68f)
        LoraPose(
            sway = 2.5f * lean, tilt = 9 * lean, turn = 30 * lean, lookX = 0.9f * lean, lookY = 0.7f * lean - 0.8f * found,
            hop = found * abs(wave(s, 1.4f)) * 1.5f,
            armL = mix(14f, 22f, lean) + 3 * wave(s, 0.6f), armR = mix(mix(14f, 75 + 10 * wave(s, 2.6f), lean), 162 + 5 * wave(s, 1.4f), found),
            eyes = when { found > 0.5f -> LoraEyes.HAPPY; lean > 0.5f -> LoraEyes.FOCUS; else -> LoraEyes.OPEN },
            mouth = when { found > 0.5f -> LoraMouth.GRIN; lean > 0.5f -> LoraMouth.TONGUE; else -> LoraMouth.SMILE },
        )
    }
    LoraAction.SHELF -> {
        // Steps to the shelf, runs a hand along the books, pulls one out and hugs it.
        val toShelf = ramp(p, 0.02f, 0.12f) * (1 - ramp(p, 0.56f, 0.66f))
        val pull = ramp(p, 0.42f, 0.5f)
        val hug = ramp(p, 0.6f, 0.7f)
        LoraPose(
            dx = -3.5f * toShelf, tilt = -5 * toShelf + 3 * hug * wave(s, 0.7f), turn = -32 * toShelf, sway = 0.9f * hug * wave(s, 0.7f),
            lookX = -0.9f * toShelf, lookY = -0.7f * toShelf + 0.3f * hug, hop = hug * abs(wave(s, 1.2f)) * 0.8f,
            armL = mix(mix(14f, 132 + 8 * wave(s, 1.1f) * (1 - pull), toShelf), -62f, hug), armR = mix(14f, -62f, hug),
            eyes = when { hug > 0.5f -> LoraEyes.HAPPY; toShelf > 0.5f -> LoraEyes.FOCUS; else -> LoraEyes.OPEN },
            mouth = when { hug > 0.5f -> LoraMouth.GRIN; pull > 0.2f && pull < 1f -> LoraMouth.OOH; else -> LoraMouth.SMILE },
        )
    }
    LoraAction.NET -> {
        // Pages flutter by; Lora follows one, crouches, swings the net with a jump and catches it.
        val swing = ramp(p, 0.38f, 0.5f)
        val caught = ramp(p, 0.5f, 0.58f)
        val follow = ramp(p, 0.05f, 0.45f)
        LoraPose(
            tilt = -4 * (1 - swing) + 8 * arc(p, 0.38f, 0.56f), hop = caught * abs(wave(s, 1.5f)) * 1.2f + 1.8f * arc(p, 0.4f, 0.52f),
            squash = 1 - 0.07f * arc(p, 0.3f, 0.42f),
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
            hop = done * abs(wave(s, 1.4f)) * 1.4f, squash = 1 - 0.05f * arc(p, 0.6f, 0.68f),
            armL = mix(-92 + 4 * wave(s, 0.7f), 140 + 10 * wave(s, 1.4f), done),
            armR = mix(mix(55 + 8 * wave(s, 0.7f), 100f, place), 140 - 10 * wave(s, 1.4f), done),
            eyes = when { done > 0.5f && p < 0.82f -> LoraEyes.SPARKLE; done > 0.5f -> LoraEyes.HAPPY; place > 0f -> LoraEyes.FOCUS; else -> LoraEyes.OPEN },
            mouth = when { done > 0.5f -> LoraMouth.GRIN; place > 0f -> LoraMouth.TONGUE; else -> LoraMouth.FLAT },
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
            tilt = -7 * windUp * (1 - thrown) + 6 * arc(p, 0.44f, 0.6f), hop = arc(p, 0.44f, 0.56f) * 1.4f,
            squash = 1 - 0.05f * windUp * (1 - thrown), sway = -1.2f * windUp * (1 - thrown) + 1.2f * arc(p, 0.44f, 0.6f),
            armL = mix(-42 + 5 * wave(s, 1.2f) * folding, 25f, windUp),
            armR = mix(mix(-42 - 5 * wave(s, 1.2f) * folding, 160f, windUp), 75f, thrown),
            lookX = if (watching) (plane.x / 20f).coerceIn(-1f, 1f) else 0.5f * windUp,
            lookY = if (watching) ((plane.y - 24f) / 12f).coerceIn(-1f, 0.6f) else 0.7f * (1 - windUp) - 0.4f * windUp,
            turn = if (watching) (plane.x * 0.8f).coerceIn(-20f, 20f) else 0f,
            eyes = when { p > 0.9f -> LoraEyes.HAPPY; watching && k > 0.2f -> LoraEyes.WIDE; else -> LoraEyes.OPEN },
            mouth = when { p > 0.9f -> LoraMouth.GRIN; watching && k > 0.2f -> LoraMouth.OOH; p > 0.44f -> LoraMouth.GRIN; folding > 0.5f -> LoraMouth.TONGUE; else -> LoraMouth.SMILE },
        )
    }
    LoraAction.HEADPHONES -> {
        // Listens with the eyes closed, bobbing to the beat, then grooves from foot to foot.
        val beat = wave(s, 1.1f)
        val groove = bump(p, 0.45f, 0.52f, 0.8f, 0.86f)
        LoraPose(
            sway = 2.6f * groove * wave(s, 0.55f) + 0.5f * (1 - groove) * wave(s, 0.55f), hop = abs(beat) * (0.6f + 1.2f * groove), tilt = 5 * beat * (0.6f + 0.6f * groove),
            legs = 0.3f * maxOf(0f, beat), squash = 0.98f + 0.02f * abs(beat), turn = 14 * groove * wave(s, 0.55f),
            armL = mix(14 + 4 * beat, 120 + 25 * beat, groove), armR = mix(168f, 120 - 25 * beat, groove),
            eyes = if (groove > 0.5f) LoraEyes.HAPPY else LoraEyes.CLOSED, mouth = if (groove > 0.5f) LoraMouth.GRIN else LoraMouth.SMILE,
        )
    }
    LoraAction.ABACUS -> {
        // Counts on the abacus bead by bead, nodding along; the sum is right.
        val done = ramp(p, 0.8f, 0.86f)
        val count = abs(wave(s, 1.6f)) * (1 - done)
        LoraPose(
            lookX = 0.35f * wave(s, 0.8f) * (1 - done), lookY = 0.8f * (1 - done) - 0.2f * done, hop = done * abs(wave(s, 1.4f)),
            squash = 1 - 0.015f * count - 0.05f * arc(p, 0.78f, 0.86f),
            armL = -12f, armR = mix(-48 + 14 * wave(s, 1.6f), 130f, done),
            eyes = if (done > 0.5f) LoraEyes.HAPPY else LoraEyes.OPEN,
            mouth = if (done > 0.5f) LoraMouth.GRIN else LoraMouth.TONGUE,
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
        // A little crouch, a hop and a big wave, leaning away from the waving arm – and a wink at the end.
        val hello = arc(p, 0.06f, 0.28f)
        val wink = bump(p, 0.6f, 0.64f, 0.8f, 0.86f)
        LoraPose(
            hop = hello * 3, squash = 1 - 0.07f * arc(p, 0f, 0.1f), tilt = 4 * wave(s, 0.9f) - 2f, sway = -0.8f, lookX = 0.2f, lookY = -0.1f,
            armL = 12 + 6 * hello + 4 * wave(s, 0.9f), armR = 150 + 25 * wave(s, 1.8f),
            eyes = if (wink > 0.5f) LoraEyes.WINK else LoraEyes.HAPPY, mouth = LoraMouth.GRIN,
        )
    }
    LoraAction.LOOK -> {
        // Looks to one side and to the other, her weight going with it; something catches her eye – a hop –, then a foot taps.
        val look = -0.8f * bump(p, 0.06f, 0.18f, 0.32f, 0.42f) + 0.8f * bump(p, 0.38f, 0.5f, 0.62f, 0.72f)
        val tap = bump(p, 0.76f, 0.78f, 0.92f, 0.94f)
        val spot = bump(p, 0.5f, 0.53f, 0.6f, 0.66f)
        LoraPose(
            hop = arc(p, 0.5f, 0.6f) * 1.6f, tilt = 5 * look, sway = 1.4f * look, lookX = look, lookY = -0.2f * tap - 0.15f * abs(look), turn = 32 * look,
            legs = tap * 0.35f * maxOf(0f, wave(s, 3f)), armL = 12 + 3 * wave(s, 0.5f) + 10 * maxOf(0f, -look), armR = 12 - 3 * wave(s, 0.5f) + 10 * maxOf(0f, look),
            eyes = if (spot > 0.5f) LoraEyes.WIDE else LoraEyes.OPEN, mouth = if (spot > 0.5f) LoraMouth.OOH else LoraMouth.SMILE,
        )
    }
    LoraAction.SLEEP -> {
        // Asleep on her feet: slow deep breaths, the head sinking with each, a small snore.
        val breath = wave(s, 0.22f)
        LoraPose(
            tilt = -7f + 1.2f * breath, sway = -0.6f, lookY = 0.45f - 0.15f * breath, armL = 6f - 2 * breath, armR = 6f - 2 * breath,
            squash = 0.93f + 0.035f * breath, glow = 0.3f,
            eyes = LoraEyes.CLOSED, mouth = if (breath > 0.55f) LoraMouth.OOH else LoraMouth.NONE,
        )
    }
    LoraAction.STRETCH -> {
        // Reaches up on tiptoes with a yawn, then shakes it off.
        val up = bump(p, 0.06f, 0.24f, 0.5f, 0.62f)
        val yawn = bump(p, 0.12f, 0.22f, 0.42f, 0.5f)
        val shake = bump(p, 0.62f, 0.66f, 0.8f, 0.86f)
        LoraPose(
            squash = 1 + 0.08f * up, hop = 0.8f * up, tilt = 3 * up * wave(s, 0.5f) + 7 * shake * wave(s, 3f), lookY = -0.6f * up,
            sway = 0.8f * up * wave(s, 0.5f),
            armL = mix(14f, 172f, up) + 12 * shake * wave(s, 3f), armR = mix(14f, 172f, up) - 12 * shake * wave(s, 3f),
            eyes = when { yawn > 0.3f || up > 0.6f -> LoraEyes.CLOSED; shake > 0.5f -> LoraEyes.HAPPY; p in 0.5f..0.64f -> LoraEyes.SOFT; else -> LoraEyes.OPEN },
            mouth = when { yawn > 0.3f -> LoraMouth.OPEN; shake > 0.5f -> LoraMouth.GRIN; else -> LoraMouth.SMILE },
        )
    }
    LoraAction.WATER -> {
        // Waters the little plant, which grows and blooms – and is delighted with it.
        val reach = ramp(p, 0.04f, 0.12f) * (1 - ramp(p, 0.56f, 0.64f))
        val pour = bump(p, 0.12f, 0.18f, 0.5f, 0.56f)
        val admire = ramp(p, 0.68f, 0.76f)
        LoraPose(
            turn = 25f, lookX = 0.8f, lookY = 0.4f * pour + 0.1f - 0.3f * admire, tilt = 4 * pour, sway = 1.2f * pour, hop = admire * arc(p, 0.76f, 0.9f) * 1.6f,
            squash = 1 - 0.05f * arc(p, 0.7f, 0.78f),
            armL = 14 + 3 * wave(s, 0.5f) + 120 * bump(p, 0.76f, 0.82f, 0.9f, 0.96f), armR = mix(45f, 105f, reach),
            eyes = when { admire > 0.5f && p < 0.9f -> LoraEyes.SPARKLE; admire > 0.5f -> LoraEyes.HAPPY; pour > 0.5f -> LoraEyes.SOFT; else -> LoraEyes.OPEN },
            mouth = if (admire > 0.5f) LoraMouth.GRIN else LoraMouth.SMILE,
        )
    }
}

/** Lora's pose in [frame] at [s] seconds: blended out of the previous action, with a small hop into the new one. */
private fun posed(frame: LoraFrame, s: Float): LoraPose {
    val now = loraPose(frame.action, frame.phase, s)
    val prev = frame.previous ?: return now
    // The arms start gently, swing a little past their new place and settle.
    val mixed = lerp(loraPose(prev, 1f, s), now, ease(frame.blend), overshoot(ease(frame.blend)))
    return if (prev == frame.action) mixed else mixed.copy(hop = mixed.hop + 1.8f * sin(PI * frame.blend).toFloat())
}

/** How far back Lora looks to feel her motion: [TRAIL] moments, [TRAIL_MS] apart. */
private const val TRAIL = 9
private const val TRAIL_MS = 40L

/** How strongly a jolt [k] moments ago still swings in something soft: a spring of 2.6 swings a second that dies out. */
private val JELLY: FloatArray = run {
    val w = 2 * PI * 2.6
    val damping = 0.28
    val wd = w * sqrt(1 - damping * damping)
    val dt = TRAIL_MS / 1000.0
    FloatArray(TRAIL) { k -> (exp(-damping * w * k * dt) * sin(wd * k * dt) / (wd * dt)).toFloat() }
}

/**
 * How far something soft that rides on Lora (the tip of the antenna, her arms, her own weight) is ahead of her (+) or
 * behind (−): it stays behind when she starts, swings on when she stops and dies out. [value] is where she was
 * [TRAIL_MS] × k ago (0 = now); the answer has its unit. Steady motion gives 0 – only changes of speed swing.
 */
private inline fun jelly(value: (Int) -> Float): Float {
    var z = 0f
    for (k in 1 until TRAIL) z -= JELLY[k] * (value(k - 1) - 2 * value(k) + value(k + 1))
    return z
}

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

    /** A point [x] units from the middle and [h] units above the ground. */
    fun at2(x: Float, h: Float) = Offset(cx0 + x * u, ground - h * u)
}

private fun Color.fade(a: Float) = copy(alpha = alpha * a.coerceIn(0f, 1f))

/** Fixed colors of a few props that should read the same in every style. */
private val Ocean = Color(0xFF5DADE2)
private val Land = Color(0xFF4CAF50)
private val Potion = Color(0xFF2EC4B6)
private val Ivory = Color(0xFFF4EBD9)

/** Red of the stamp (AP-11). */
private val StampRed = Color(0xFFD32F2F)
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
        LoraAction.STAPLER -> {
            sideDesk(g, c, a)
            val press = staplerPress(p)
            val sh = DESK_H
            // The papers under the jaw until they are picked up, the base, and the arm hinged at the back.
            if (p < 0.6f) paperStrip(g.at2(12.4f, sh + 1.45f), 6.6f * u, 0.55f * u, u, c, a)
            drawRoundRect(line, g.at2(14.2f, sh + 0.9f), Size(9.6f * u, 0.9f * u), CornerRadius(0.45f * u))
            val hinge = g.at2(23.2f, sh + 1.9f)
            rotate(-13f * (1 - press), hinge) {
                drawRoundRect(c.prop.fade(a), g.at2(14f, sh + 2.6f), Size(9.8f * u, 1.7f * u), CornerRadius(0.85f * u))
                drawRoundRect(Color.White.fade(a * 0.35f), g.at2(15f, sh + 2.3f), Size(5.5f * u, 0.45f * u), CornerRadius(0.22f * u))
            }
            drawCircle(line, 0.55f * u, hinge)
            if (press > 0.85f) star(g.at2(12.8f, sh + 2.6f), 0.9f * u, c.spark.fade(a))
        }
        LoraAction.STAMP -> {
            sideDesk(g, c, a)
            val sh = DESK_H
            if (p < 0.6f) {
                // The page lies flat on the desk; the red mark stays after the first hit.
                val l = g.at2(15.8f, sh + 0.5f)
                val page = Path().apply {
                    moveTo(l.x + 0.5f * u, l.y); lineTo(l.x + 6.5f * u, l.y); lineTo(l.x + 7.1f * u, l.y + 0.8f * u); lineTo(l.x - 0.1f * u, l.y + 0.8f * u); close()
                }
                drawPath(page, Ivory.fade(a))
                drawPath(page, c.line.fade(a * 0.5f), style = Stroke(0.15f * u))
                if (p > 0.255f) drawOval(StampRed.fade(a * if (p > 0.475f) 0.95f else 0.7f), Offset(l.x + 2.1f * u, l.y + 0.15f * u), Size(2.9f * u, 0.5f * u))
                if (stampHit(p) > 0.5f) for (k in listOf(-1f, 1f)) star(g.at2(18.9f + k * 4.6f, sh + 1.8f), 0.75f * u, c.spark.fade(a))
            }
            // Put down after the second hit.
            if (p >= 0.58f) stampObject(g.at2(21.6f, sh + 2.6f), u, c, a)
        }
        LoraAction.CALENDAR -> {
            val x0 = -27f
            val x1 = -15f
            val top = 23f
            val bot = 10f
            drawLine(line, g.at2(x0 + 2f, bot), g.at2(x0 + 0.8f, 0.4f), 0.8f * u, StrokeCap.Round)
            drawLine(line, g.at2(x1 - 2f, bot), g.at2(x1 - 0.8f, 0.4f), 0.8f * u, StrokeCap.Round)
            val flip = ramp(p, 0.12f, 0.3f)
            // The new month underneath, the old page flips up over the rings.
            calendarPage(g, c, a, x0, x1, top, bot, 1f, StampRed, grid = true)
            if (flip < 1f) calendarPage(g, c, a, x0, x1, top, bot, 1f - flip, c.prop, grid = flip < 0.4f)
            for (k in 0..2) drawCircle(line, 0.5f * u, g.at2(x0 + 3f + k * 3f, top + 0.2f), style = Stroke(0.3f * u))
            val day = g.at2(CAL_DAY.x, 5f - CAL_DAY.y)
            val circle = ramp(p, 0.42f, 0.68f)
            if (circle > 0f) {
                drawArc(
                    StampRed.fade(a), -90f, 360f * circle, useCenter = false, topLeft = Offset(day.x - 1.6f * u, day.y - 1.6f * u),
                    size = Size(3.2f * u, 3.2f * u), style = Stroke(0.4f * u, cap = StrokeCap.Round),
                )
            }
            if (p > 0.72f) star(Offset(day.x + 2.3f * u, day.y - 2.1f * u), 0.9f * u, c.spark.fade(a))
        }
        LoraAction.PAINT -> {
            val l = 13f
            val r = 25f
            drawLine(line, g.at2(l + 1.5f, 13f), g.at2(l + 0.3f, 0.4f), 0.8f * u, StrokeCap.Round)
            drawLine(line, g.at2(r - 1.5f, 13f), g.at2(r - 0.3f, 0.4f), 0.8f * u, StrokeCap.Round)
            drawLine(c.line.fade(a * 0.5f), g.at2((l + r) / 2, 25f), g.at2((l + r) / 2 + 1f, 0.4f), 0.6f * u, StrokeCap.Round)
            drawRect(Ivory.fade(a), g.at2(l, 25f), Size((r - l) * u, 12f * u))
            drawRect(line, g.at2(l, 25f), Size((r - l) * u, 12f * u), style = Stroke(0.35f * u))
            fun pt(o: Offset) = Offset(g.cx0 + o.x * u, g.ground - 5f * u + o.y * u)
            val hillK = ramp(p, 0.1f, 0.36f)
            if (hillK > 0f) {
                val path = Path()
                val n = (24 * hillK).toInt().coerceAtLeast(1)
                for (i in 0..n) {
                    val q = pt(quadO(HILL.first, HILL.second, HILL.third, hillK * i / n))
                    if (i == 0) path.moveTo(q.x, q.y) else path.lineTo(q.x, q.y)
                }
                drawPath(path, Land.fade(a), style = Stroke(1.1f * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            val sunK = ramp(p, 0.4f, 0.56f)
            if (sunK > 0f) {
                val sc = pt(SUN)
                drawArc(c.spark.fade(a), -90f, 360f * sunK, useCenter = true, topLeft = Offset(sc.x - SUN_R * u, sc.y - SUN_R * u), size = Size(2 * SUN_R * u, 2 * SUN_R * u))
            }
            val waterK = ramp(p, 0.6f, 0.74f)
            if (waterK > 0f) {
                val path = Path()
                val n = (20 * waterK).toInt().coerceAtLeast(1)
                for (i in 0..n) {
                    val q = pt(water(waterK * i / n))
                    if (i == 0) path.moveTo(q.x, q.y) else path.lineTo(q.x, q.y)
                }
                drawPath(path, Ocean.fade(a), style = Stroke(0.8f * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            if (p > 0.8f) star(g.at2(r + 1.4f, 26f), 1.0f * u, c.spark.fade(a))
        }
        LoraAction.SLIDES -> {
            val l = -33f
            val r = -14f
            val m = (l + r) / 2
            drawLine(line, g.at2(m, 13f), g.at2(m, 0.6f), 0.9f * u, StrokeCap.Round)
            drawLine(line, g.at2(m - 3f, 0.4f), g.at2(m + 3f, 0.4f), 0.8f * u, StrokeCap.Round)
            drawRoundRect(Ivory.fade(a), g.at2(l, 27f), Size((r - l) * u, 14f * u), CornerRadius(0.6f * u))
            drawRoundRect(line, g.at2(l, 27f), Size((r - l) * u, 14f * u), CornerRadius(0.6f * u), style = Stroke(0.35f * u))
            val b = ramp(p, 0.46f, 0.54f)
            drawLine(c.line.fade(a * 0.7f), g.at2(l + 2f, 25.2f), g.at2(l + 11f, 25.2f), 0.7f * u, StrokeCap.Round)
            // First a chart …
            if (b < 1f) {
                val bars = listOf(Triple(-30f, 18f, c.prop), Triple(-26f, 20.5f, c.body), Triple(-22f, 23f, Ocean))
                bars.forEach { (x, h, col) -> drawRect(col.fade(a * (1 - b)), g.at2(x, h), Size(2f * u, (h - 14.5f) * u)) }
                drawLine(c.line.fade(a * (1 - b) * 0.6f), g.at2(-31f, 14.5f), g.at2(-17f, 14.5f), 0.25f * u)
            }
            // … then a list.
            if (b > 0f) {
                SLIDE_POINTS.forEachIndexed { i, pt ->
                    val h = 5f - pt.y
                    drawCircle(c.body.fade(a * b), 0.45f * u, g.at2(pt.x, h))
                    drawLine(c.line.fade(a * b * 0.6f), g.at2(pt.x + 1.2f, h), g.at2(-17f - i * 2.2f, h), 0.4f * u, StrokeCap.Round)
                }
            }
        }
        LoraAction.SHEET -> {
            val l = 13.5f
            drawLine(line, g.at2(l + 7f, 13f), g.at2(l + 7f, 0.6f), 0.9f * u, StrokeCap.Round)
            drawLine(line, g.at2(l + 4f, 0.4f), g.at2(l + 10f, 0.4f), 0.8f * u, StrokeCap.Round)
            drawRect(Ivory.fade(a), g.at2(l, 25f), Size(14f * u, 12f * u))
            drawRect(c.body.fade(a * 0.25f), g.at2(l, 25f), Size(14f * u, 3f * u))
            for (i in 1..3) drawLine(c.line.fade(a * 0.35f), g.at2(l + i * 3.5f, 25f), g.at2(l + i * 3.5f, 13f), 0.18f * u)
            for (j in 1..3) drawLine(c.line.fade(a * 0.35f), g.at2(l, 25f - j * 3f), g.at2(l + 14f, 25f - j * 3f), 0.18f * u)
            drawRect(line, g.at2(l, 25f), Size(14f * u, 12f * u), style = Stroke(0.3f * u))
            val filled = (ramp(p, 0.1f, 0.72f) * 12).toInt()
            val widths = listOf(0.55f, 0.8f, 0.35f, 0.65f, 0.7f, 0.45f, 0.9f, 0.5f, 0.6f, 0.75f, 0.4f, 0.85f)
            for (i in 0 until filled.coerceAtMost(12)) {
                val cell = sheetCell(i)
                val o = Offset(g.cx0 + (cell.x - 1.4f) * u, g.ground - 5f * u + (cell.y - 0.4f) * u)
                drawRoundRect((if (i >= 8) Tick else c.prop).fade(a * 0.8f), o, Size(2.8f * u * widths[i], 0.8f * u), CornerRadius(0.3f * u))
            }
            if (p > 0.76f) star(g.at2(l + 15.2f, 14.5f), 1.0f * u, Tick.fade(a))
        }
        LoraAction.TRANSLATE -> {
            val k1 = ramp(p, 0.04f, 0.12f) * (1 - ramp(p, 0.9f, 0.98f))
            val k2 = ramp(p, 0.52f, 0.6f) * (1 - ramp(p, 0.9f, 0.98f))
            speechBubble(g, c, a * k1, -17f, pop(k1), german = true)
            speechBubble(g, c, a * k2, 17f, pop(k2), german = false)
            val arrows = bump(p, 0.42f, 0.5f, 0.84f, 0.92f)
            if (arrows > 0f) {
                val col = c.line.fade(a * arrows)
                for ((y, dir) in listOf(35.2f to 1f, 33.6f to -1f)) {
                    val from = g.at2(-4f * dir, y)
                    val to = g.at2(4f * dir, y)
                    drawLine(col, from, to, 0.4f * u, StrokeCap.Round)
                    drawLine(col, to, Offset(to.x - dir * 1f * u, to.y - 0.7f * u), 0.4f * u, StrokeCap.Round)
                    drawLine(col, to, Offset(to.x - dir * 1f * u, to.y + 0.7f * u), 0.4f * u, StrokeCap.Round)
                }
            }
        }
        LoraAction.CHEER -> {
            val show = bump(p, 0.02f, 0.1f, 0.85f, 0.98f)
            val colors = listOf(c.spark, c.prop, c.blush, Ocean, Land, c.body)
            for (i in 0 until 14) {
                val k = (p * 1.15f + i * 0.071f) % 1f
                val x = -30f + ((i * 37) % 61).toFloat() + 2f * sin(2 * PI * (k * 2 + i * 0.3)).toFloat()
                val h = 50f - k * 46f
                val alpha = a * show * (1 - ramp(k, 0.85f, 1f))
                if (alpha <= 0f) continue
                val center = g.at2(x, h)
                rotate(k * 720f + i * 40f, center) {
                    drawRect(colors[i % colors.size].fade(alpha), Offset(center.x - 0.5f * u, center.y - 0.3f * u), Size(1f * u, 0.6f * u))
                }
            }
        }
        LoraAction.SHRUG -> {
            val up = bump(p, 0.1f, 0.22f, 0.7f, 0.82f)
            if (up > 0f) {
                val q = Offset(g.cx + 9f * u, g.top - 5f * u)
                val col = c.line.fade(a * up)
                val path = Path().apply {
                    moveTo(q.x - 1.3f * u, q.y - 1.2f * u)
                    cubicTo(q.x - 1.3f * u, q.y - 3.2f * u, q.x + 1.6f * u, q.y - 3.2f * u, q.x + 1.4f * u, q.y - 1.3f * u)
                    quadraticTo(q.x + 1.2f * u, q.y - 0.2f * u, q.x, q.y + 0.4f * u)
                    lineTo(q.x, q.y + 1.3f * u)
                }
                drawPath(path, col, style = Stroke(0.7f * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
                drawCircle(col, 0.45f * u, Offset(q.x, q.y + 2.7f * u))
            }
        }
        LoraAction.SCRATCH -> {
            val up = bump(p, 0.1f, 0.2f, 0.74f, 0.86f)
            if (up > 0f) {
                val d = Offset(g.cx - 10.6f * u, g.top + 3f * u + 2.4f * u * ((p * 3f) % 1f))
                val drop = Path().apply {
                    moveTo(d.x, d.y - 1.3f * u)
                    quadraticTo(d.x + 1.1f * u, d.y + 0.2f * u, d.x, d.y + 0.7f * u)
                    quadraticTo(d.x - 1.1f * u, d.y + 0.2f * u, d.x, d.y - 1.3f * u)
                    close()
                }
                drawPath(drop, Ocean.fade(a * up * (1 - ramp((p * 3f) % 1f, 0.7f, 1f))))
            }
        }
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
        when (action) {
            LoraAction.TYPE -> {
                val lid = Path().apply {
                    moveTo(cx - 8 * u, bottom + 0.3f * u); lineTo(cx - 7.2f * u, bottom - 6.2f * u)
                    lineTo(cx + 7.2f * u, bottom - 6.2f * u); lineTo(cx + 8 * u, bottom + 0.3f * u); close()
                }
                drawPath(lid, c.prop.fade(a))
                star(Offset(cx, bottom - 3 * u), 1.4f * u, Color.White.fade(a * 0.85f))
            }
            LoraAction.SCISSORS -> {
                // Cutting along the line, snip by snip; afterwards held up in the raised hand.
                val raw = scissorsCut(p)
                val done = ramp(p, 0.68f, 0.76f)
                val open = if (raw > 0f && raw < 1f) 0.2f + 0.8f * (0.5f + 0.5f * cos(2 * PI * raw * 6).toFloat()) else 0.55f
                val cutTip = Offset(cx + mix(9.4f, -1.4f, raw) * u, bottom - 2.6f * u)
                val upTip = Offset(handR.x, handR.y - 5.1f * u)
                scissors(mixO(cutTip, upTip, done), mix(180f, 270f, done), mix(open, 0.2f, done), u, c, a * ramp(p, 0f, 0.06f).coerceAtLeast(done))
            }
            LoraAction.LOCK -> {
                // The key: to the keyhole, then a quarter turn.
                val insert = ramp(p, 0.12f, 0.42f)
                val turn = ramp(p, 0.46f, 0.58f)
                val k = Offset(cx + mix(8.2f, 1.6f, insert) * u, bottom + mix(-2.8f, -2.6f, insert) * u)
                rotate(90f * turn, k) {
                    drawLine(Silver.fade(a), k, Offset(k.x - 2.2f * u * (1 - 0.6f * insert), k.y), 0.55f * u, StrokeCap.Round)
                    drawCircle(Silver.fade(a), 1.0f * u, Offset(k.x + 0.9f * u, k.y))
                    drawCircle(c.line.fade(a * 0.6f), 1.0f * u, Offset(k.x + 0.9f * u, k.y), style = Stroke(0.2f * u))
                    drawCircle(c.visor.fade(a), 0.35f * u, Offset(k.x + 1.2f * u, k.y))
                }
            }
            LoraAction.HIGHLIGHT -> {
                // The highlighter at the end of the mark; afterwards in the raised hand.
                val done = ramp(p, 0.7f, 0.78f)
                val t = highlightTip(p)
                val at = mixO(Offset(cx + t.x * u, bottom + t.y * u), Offset(handR.x - 1.4f * u, handR.y - 1.6f * u), done)
                val grab = ramp(p, 0f, 0.08f)
                drawLine(Marker.fade(a * grab), at, Offset(at.x + 2.6f * u, at.y + 1.2f * u), 1.1f * u, StrokeCap.Round)
                drawLine(c.line.fade(a * grab), Offset(at.x + 2.2f * u, at.y + 1.0f * u), Offset(at.x + 3.1f * u, at.y + 1.4f * u), 1.1f * u, StrokeCap.Round)
            }
            else -> Unit
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
        LoraAction.SCISSORS -> {
            // The sheet held low in front, the cut so far, and the lower half dropping once it is cut through.
            val grab = ramp(p, 0f, 0.08f)
            val raw = scissorsCut(p)
            val tip = mix(9.4f, -1.4f, raw)
            val fall = ramp(p, 0.64f, 0.86f)
            val sa = a * grab
            fun b(x: Float, y: Float) = Offset(cx + x * u, bottom + y * u)
            sheet(b(-3.2f, -6.2f), b(9f, -2.6f), u, c, sa, lines = listOf(-5.1f, -4.1f, -3.3f).map { bottom + it * u })
            // The lower half swings down from the left edge and fades as it drops.
            val gone = ramp(fall, 0f, 0.5f)
            if (gone < 1f) {
                val pivot = b(-3.2f, -2.6f)
                withTransform({ translate(top = fall * 3f * u); rotate(20f * fall, pivot) }) {
                    sheet(b(-3.2f, -2.6f), b(9f, 1.2f), u, c, sa * (1 - gone), lines = listOf(-1.4f, -0.4f).map { bottom + it * u })
                }
            }
            if (raw < 1f) drawLine(c.line.fade(sa * 0.35f), b(tip, -2.6f), b(-3.2f, -2.6f), 0.18f * u, pathEffect = PathEffect.dashPathEffect(floatArrayOf(0.7f * u, 0.5f * u)))
            drawLine(c.line.fade(sa * 0.85f), b(9f, -2.6f), b(tip, -2.6f), 0.22f * u)
        }
        LoraAction.STAPLER -> {
            // After picking it up: the stapled stack, shown to the viewer.
            if (p >= 0.6f) shownSheet(handR, ramp(p, 0.6f, 0.7f), u, c, a, stamped = false)
        }
        LoraAction.STAMP -> {
            if (p < 0.58f) stampObject(handR, u, c, a)
            if (p >= 0.6f) shownSheet(handR, ramp(p, 0.6f, 0.7f), u, c, a, stamped = true)
        }
        LoraAction.MAIL -> {
            val fold = ramp(p, 0.08f, 0.3f)
            val insert = ramp(p, 0.34f, 0.5f)
            val seal = ramp(p, 0.54f, 0.6f)
            val up = ramp(p, 0.66f, 0.74f)
            val center = mixO(Offset(cx, bottom - 3.6f * u), Offset(handR.x + 0.4f * u, handR.y - 2.4f * u), up)
            val w = 7f * u
            val h = 4.4f * u
            val envA = a * ramp(p, 0.26f, 0.34f)
            val tl = Offset(center.x - w / 2, center.y - h / 2)
            if (envA > 0f) {
                drawRoundRect(Envelope.fade(envA), tl, Size(w, h), CornerRadius(0.4f * u))
                drawRoundRect(c.line.fade(envA * 0.6f), tl, Size(w, h), CornerRadius(0.4f * u), style = Stroke(0.2f * u))
            }
            // The letter: folded, then it slides down into the envelope.
            if (insert < 1f) {
                val lh = mix(5.2f, 2.6f, fold) * u
                val lw = 4.8f * u
                val lc = Offset(center.x, center.y - 0.4f * u - (1 - insert) * 2.6f * u + insert * 1.2f * u)
                val la = a * (1 - ramp(insert, 0.85f, 1f))
                drawRoundRect(Ivory.fade(la), Offset(lc.x - lw / 2, lc.y - lh / 2), Size(lw, lh), CornerRadius(0.25f * u))
                drawRoundRect(c.line.fade(la * 0.6f), Offset(lc.x - lw / 2, lc.y - lh / 2), Size(lw, lh), CornerRadius(0.25f * u), style = Stroke(0.18f * u))
                for (k in 0..1) {
                    val y = lc.y - lh / 2 + lh * (0.35f + 0.3f * k)
                    drawLine(c.line.fade(la * 0.45f), Offset(lc.x - lw / 2 + 0.6f * u, y), Offset(lc.x + lw / 2 - (0.6f + k) * u, y), 0.25f * u, StrokeCap.Round)
                }
            }
            if (envA > 0f) {
                // Front pocket over the letter, then the flap: open upwards, closed downwards with the seal.
                val pocket = Path().apply {
                    moveTo(tl.x, tl.y + 0.35f * h); lineTo(center.x, tl.y + 0.68f * h); lineTo(tl.x + w, tl.y + 0.35f * h)
                    lineTo(tl.x + w, tl.y + h); lineTo(tl.x, tl.y + h); close()
                }
                drawPath(pocket, EnvelopeShade.fade(envA))
                drawPath(pocket, c.line.fade(envA * 0.5f), style = Stroke(0.18f * u))
                val apex = tl.y + mix(-0.5f * h, 0.62f * h, seal)
                val flap = Path().apply { moveTo(tl.x, tl.y); lineTo(center.x, apex); lineTo(tl.x + w, tl.y); close() }
                drawPath(flap, (if (seal > 0.5f) EnvelopeShade else Envelope).fade(envA))
                drawPath(flap, c.line.fade(envA * 0.6f), style = Stroke(0.18f * u))
                if (seal > 0f) heart(Offset(center.x, tl.y + 0.6f * h), 1.0f * u * pop(seal), StampRed.fade(a))
            }
        }
        LoraAction.CALENDAR -> {
            // The red pen in the hand that circles the day.
            if (p > 0.34f) {
                val pen = Offset(handL.x - 1.1f * u, handL.y - 1.4f * u)
                drawLine(StampRed.fade(a), handL, pen, 0.7f * u, StrokeCap.Round)
                drawLine(c.line.fade(a), pen, Offset(pen.x - 0.35f * u, pen.y - 0.45f * u), 0.4f * u, StrokeCap.Round)
            }
        }
        LoraAction.PAINT -> {
            // The palette in the left hand, the brush in the right with the current colour.
            val pal = Offset(handL.x - 2.4f * u, handL.y - 1.5f * u)
            drawOval(Palette.fade(a), pal, Size(4.6f * u, 2.6f * u))
            listOf(Land, c.spark, Ocean, StampRed).forEachIndexed { i, col -> drawCircle(col.fade(a), 0.42f * u, Offset(pal.x + (1.0f + i * 0.95f) * u, pal.y + (1.0f + (i % 2) * 0.6f) * u)) }
            val tip = Offset(handR.x + 3.2f * u, handR.y - 1.8f * u)
            drawLine(c.line.fade(a), Offset(handR.x - 0.6f * u, handR.y + 0.35f * u), Offset(tip.x - 0.9f * u, tip.y + 0.5f * u), 0.5f * u, StrokeCap.Round)
            drawLine(Silver.fade(a), Offset(tip.x - 0.9f * u, tip.y + 0.5f * u), Offset(tip.x - 0.3f * u, tip.y + 0.17f * u), 0.7f * u, StrokeCap.Round)
            val colour = when { p < 0.38f -> Land; p < 0.58f -> c.spark; else -> Ocean }
            drawCircle(colour.fade(a), 0.55f * u, tip)
        }
        LoraAction.LOCK -> {
            val close = ramp(p, 0.6f, 0.66f)
            val center = Offset(cx - 0.4f * u, bottom - 2.6f * u)
            // Shackle (open: lifted out on the right), body, keyhole.
            val lift = (1 - close) * 1.6f * u
            val shackle = Path().apply {
                moveTo(center.x - 1.6f * u, center.y - 1.6f * u)
                lineTo(center.x - 1.6f * u, center.y - 3.2f * u)
                cubicTo(center.x - 1.6f * u, center.y - 5.4f * u - lift, center.x + 1.6f * u, center.y - 5.4f * u - lift, center.x + 1.6f * u, center.y - 3.2f * u - lift)
                lineTo(center.x + 1.6f * u, center.y - 1.6f * u - lift)
            }
            drawPath(shackle, Silver.fade(a), style = Stroke(0.8f * u, cap = StrokeCap.Round))
            drawPath(shackle, c.line.fade(a * 0.5f), style = Stroke(0.2f * u))
            drawRoundRect(c.prop.fade(a), Offset(center.x - 2.6f * u, center.y - 2f * u), Size(5.2f * u, 4f * u), CornerRadius(0.8f * u))
            drawRoundRect(Color.White.fade(a * 0.3f), Offset(center.x - 2f * u, center.y - 1.6f * u), Size(2.4f * u, 0.5f * u), CornerRadius(0.25f * u))
            drawCircle(c.eye.fade(a * 0.8f), 0.45f * u, Offset(center.x + 0.2f * u, center.y - 0.2f * u))
            drawLine(c.eye.fade(a * 0.8f), Offset(center.x + 0.2f * u, center.y - 0.2f * u), Offset(center.x + 0.2f * u, center.y + 0.8f * u), 0.35f * u, StrokeCap.Round)
            if (close > 0.5f && p < 0.74f) star(Offset(center.x + 2.6f * u, center.y - 4.2f * u), 0.9f * u, c.spark.fade(a))
            val glow = bump(p, 0.7f, 0.76f, 0.9f, 0.96f)
            if (glow > 0f) {
                drawCircle(Tick.fade(a * glow * 0.35f), 4.4f * u, center, style = Stroke(0.4f * u))
                star(Offset(center.x - 3.4f * u, center.y - 3.8f * u), 0.8f * u * glow, c.spark.fade(a))
            }
        }
        LoraAction.SLIDES -> {
            // The pointer from the left hand to the place on the slide.
            val t = slideTip(p)
            val tip = Offset(cx + t.x * u, bottom + t.y * u)
            val on = ramp(p, 0f, 0.08f) * (1 - ramp(p, 0.92f, 1f))
            drawLine(c.line.fade(a * on), handL, tip, 0.35f * u, StrokeCap.Round)
            drawCircle(StampRed.fade(a * on), 0.4f * u, tip)
        }
        LoraAction.HIGHLIGHT -> {
            val grab = ramp(p, 0f, 0.08f)
            val sa = a * grab
            fun b(x: Float, y: Float) = Offset(cx + x * u, bottom + y * u)
            sheet(b(-4.6f, -6.2f), b(6.4f, 1.2f), u, c, sa, lines = emptyList())
            val lines = listOf(-4.6f, -2.6f, -0.6f)
            lines.forEachIndexed { i, y -> drawLine(c.line.fade(sa * 0.45f), b(-3.2f, y), b(if (i == 1) 3.4f else 5f, y), 0.3f * u, StrokeCap.Round) }
            val k1 = ramp(p, 0.12f, 0.32f)
            val k3 = ramp(p, 0.44f, 0.64f)
            for ((y, k) in listOf(-4.6f to k1, -0.6f to k3)) if (k > 0f) {
                drawRoundRect(Marker.fade(sa * 0.55f), b(-3.4f, y - 0.6f), Size(8.4f * u * k, 1.2f * u), CornerRadius(0.3f * u))
            }
        }
        LoraAction.FOLDER -> {
            val hug = ramp(p, 0.64f, 0.72f) * (1 - ramp(p, 0.9f, 0.98f))
            val drop = ramp(p, 0.1f, 0.4f)
            val close = ramp(p, 0.44f, 0.58f)
            val center = mixO(Offset(cx, bottom - 2.2f * u), Offset(cx, bottom - 6.6f * u), hug)
            val w = 10.4f * u
            val h = 6.6f * u
            val tl = Offset(center.x - w / 2, center.y - h / 2)
            drawRoundRect(c.prop.fade(a * 0.7f), tl, Size(w, h), CornerRadius(0.5f * u))
            if (close < 1f) for (k in 0..2) drawCircle(c.line.fade(a * (1 - close)), 0.4f * u, Offset(tl.x + (2.6f + k * 2.6f) * u, tl.y + 0.8f * u), style = Stroke(0.25f * u))
            // The sheet drops in from above and disappears behind the cover.
            if (drop > 0f && close < 1f) {
                val sc = Offset(center.x, center.y - (1 - drop) * 7f * u - 0.6f * u)
                val sa = a * ramp(p, 0.06f, 0.1f)
                drawRoundRect(Ivory.fade(sa), Offset(sc.x - 3.8f * u, sc.y - 2.6f * u), Size(7.6f * u, 5.2f * u), CornerRadius(0.25f * u))
                drawRoundRect(c.line.fade(sa * 0.6f), Offset(sc.x - 3.8f * u, sc.y - 2.6f * u), Size(7.6f * u, 5.2f * u), CornerRadius(0.25f * u), style = Stroke(0.18f * u))
            }
            val ch = h * mix(0.55f, 1f, close)
            drawRoundRect(c.prop.fade(a), Offset(tl.x, tl.y + h - ch), Size(w, ch), CornerRadius(0.5f * u))
            if (close > 0.5f) drawRoundRect(Ivory.fade(a * close), Offset(center.x - 1.8f * u, tl.y + h - ch + 1.2f * u), Size(3.6f * u, 1.2f * u), CornerRadius(0.2f * u))
            if (p > 0.6f && p < 0.9f) star(Offset(tl.x + w + 0.6f * u, tl.y - 0.4f * u), 0.9f * u, c.spark.fade(a))
        }
        LoraAction.POINT -> {
            val out = ramp(p, 0.04f, 0.16f) * (1 - ramp(p, 0.84f, 0.96f))
            if (out > 0f) star(Offset(handR.x + 2.2f * u, handR.y), (0.8f + 0.25f * wave(s, 1.4f)) * u * out, c.spark.fade(a))
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

/** Lighter ([k] > 0, towards white) or darker ([k] < 0, towards black); the alpha stays. */
private fun Color.shade(k: Float): Color {
    val t = abs(k).coerceAtMost(1f)
    val to = if (k > 0f) 1f else 0f
    return Color(red + (to - red) * t, green + (to - green) * t, blue + (to - blue) * t, alpha)
}

/** This color with a part [k] of [other] mixed in; the alpha stays. */
private fun Color.tint(other: Color, k: Float) = Color(red + (other.red - red) * k, green + (other.green - green) * k, blue + (other.blue - blue) * k, alpha)

/** How bright a color is (0 = black, 1 = white). */
private fun Color.brightness() = 0.299f * red + 0.587f * green + 0.114f * blue

/** Lora's body: a dome at the head, rounder than the seat. */
private fun bodyPath(cx: Float, top: Float, bw: Float, bh: Float, u: Float): Path {
    val l = cx - bw / 2
    val r = cx + bw / 2
    val b = top + bh
    val tx = min(10.5f * u, bw / 2)
    val ty = min(10.5f * u, bh * 0.5f)
    val bx = min(8f * u, bw / 2)
    val by = min(8f * u, bh * 0.42f)
    val k = 0.5523f
    return Path().apply {
        moveTo(l + tx, top)
        lineTo(r - tx, top)
        cubicTo(r - tx + tx * k, top, r, top + ty - ty * k, r, top + ty)
        lineTo(r, b - by)
        cubicTo(r, b - by + by * k, r - bx + bx * k, b, r - bx, b)
        lineTo(l + bx, b)
        cubicTo(l + bx - bx * k, b, l, b - by + by * k, l, b - by)
        lineTo(l, top + ty)
        cubicTo(l, top + ty - ty * k, l + tx - tx * k, top, l + tx, top)
        close()
    }
}

/** The body with its volume: lighter above, darker below, a soft light from the upper left and a gloss on the shoulder. */
private fun DrawScope.drawBody(shape: Path, cx: Float, top: Float, bw: Float, bh: Float, u: Float, c: LoraColors, sideways: Float) {
    drawPath(
        shape,
        Brush.verticalGradient(0f to c.body.shade(0.13f), 0.5f to c.body, 1f to c.body.shade(-0.17f), startY = top, endY = top + bh),
    )
    val light = Offset(cx - bw * 0.2f - sideways * 2.5f * u, top + bh * 0.2f)
    drawPath(shape, Brush.radialGradient(listOf(Color.White.copy(alpha = 0.26f * c.body.alpha), Color.White.copy(alpha = 0f)), light, bw * 0.55f))
    // The gloss follows the round of the head (it slides the other way when Lora turns).
    val r = min(10.5f * u, bh * 0.5f) - 1.5f * u
    if (r > 2 * u) {
        val center = Offset(cx - bw / 2 + 1.5f * u + r - sideways * 2 * u, top + 1.5f * u + r)
        drawArc(
            Color.White.copy(alpha = 0.34f * c.body.alpha), 196f, 46f, useCenter = false, topLeft = Offset(center.x - r, center.y - r),
            size = Size(2 * r, 2 * r), style = Stroke(1.05f * u, cap = StrokeCap.Round),
        )
        val dot = Math.toRadians(254.0)
        drawCircle(Color.White.copy(alpha = 0.34f * c.body.alpha), 0.55f * u, Offset(center.x + (cos(dot) * r).toFloat(), center.y + (sin(dot) * r).toFloat()))
    }
}

/** A soft limb from [a] over the bend [c] to [b], [wa] wide at its start and [wb] at its end, with round ends. */
private fun DrawScope.limb(a: Offset, c: Offset, b: Offset, wa: Float, wb: Float, color: Color) {
    val n = 10
    val xs = FloatArray(2 * (n + 1))
    val ys = FloatArray(2 * (n + 1))
    for (i in 0..n) {
        val t = i / n.toFloat()
        val p = quadO(a, c, b, t)
        var dx = 2 * (1 - t) * (c.x - a.x) + 2 * t * (b.x - c.x)
        var dy = 2 * (1 - t) * (c.y - a.y) + 2 * t * (b.y - c.y)
        val len = sqrt(dx * dx + dy * dy).coerceAtLeast(0.001f)
        dx /= len
        dy /= len
        val w = mix(wa, wb, t) / 2
        xs[i] = p.x - dy * w
        ys[i] = p.y + dx * w
        xs[2 * n + 1 - i] = p.x + dy * w
        ys[2 * n + 1 - i] = p.y - dx * w
    }
    val path = Path().apply {
        moveTo(xs[0], ys[0])
        for (i in 1 until xs.size) lineTo(xs[i], ys[i])
        close()
    }
    drawPath(path, color)
    drawCircle(color, wa / 2, a)
    drawCircle(color, wb / 2, b)
}

/** Stride of Lora's walk (units from one footprint to the next of the same foot) and the part of it a foot is in the air. */
private const val STRIDE = 9f
private const val SWING = 0.4f

/**
 * Walking: where a foot is relative to Lora once she has come [q] strides (the other foot is half a stride ahead).
 * It stays where it was put down while she moves on, then swings forward to its next place – so feet never slide.
 * In strides; [stepLift] is how high it is on the way (0..1).
 */
private fun stepOffset(q: Float): Float {
    val frac = q - floor(q)
    val stance = 1 - SWING
    return if (frac < stance) stance / 2 - frac else stance / 2 + ease((frac - stance) / SWING) - frac
}

private fun stepLift(q: Float): Float {
    val frac = q - floor(q)
    val stance = 1 - SWING
    return if (frac < stance) 0f else sin(PI * (frac - stance) / SWING).toFloat()
}

/**
 * A little boot at the ankle [at]: its toe to the side [toe] (-1 = to the viewer's left, 1 = right, 0 = towards the
 * viewer), tipped by [pitch] degrees (positive: toe down).
 */
private fun DrawScope.boot(at: Offset, toe: Float, pitch: Float, u: Float, c: LoraColors) {
    val t = abs(toe).coerceAtMost(1f)
    val hl = (2.3f - 0.45f * t) * u
    val hr = (2.3f + 0.95f * t) * u
    val ht = (1.3f - 0.4f * t) * u
    val sole = 1.2f * u
    withTransform({
        rotate(pitch * (if (toe < 0f) -1f else 1f), at)
        scale(if (toe < 0f) -1f else 1f, 1f, at)
    }) {
        val x = at.x
        val y = at.y
        val shape = Path().apply {
            moveTo(x - hl + 1f * u, y + sole)
            lineTo(x + hr - 1f * u, y + sole)
            cubicTo(x + hr + 0.2f * u, y + sole, x + hr + 0.2f * u, y - ht, x + hr - 1.3f * u, y - ht)
            cubicTo(x + hr - 2.1f * u, y - ht - 0.1f * u, x - hl + 2f * u, y - 1.4f * u, x - hl + 1.2f * u, y - 1.3f * u)
            cubicTo(x - hl - 0.2f * u, y - 1.3f * u, x - hl - 0.2f * u, y + sole, x - hl + 1f * u, y + sole)
            close()
        }
        drawPath(shape, Brush.verticalGradient(listOf(c.body.shade(-0.06f), c.body.shade(-0.24f)), y - 1.4f * u, y + sole))
        drawLine(c.body.shade(-0.45f).fade(0.55f), Offset(x - hl + 1f * u, y + sole - 0.25f * u), Offset(x + hr - 1f * u, y + sole - 0.25f * u), 0.5f * u, StrokeCap.Round)
        drawCircle(Color.White.copy(alpha = 0.22f * c.body.alpha), 0.5f * u, Offset(x + hr - 1.45f * u, y - ht + 0.75f * u))
    }
}

/** How far the eyes are open at [s] seconds (1 = open): a quick blink now and then, sometimes two in a row. */
private fun blink(s: Float): Float {
    fun lid(t: Float) = when {
        t < 0.06f -> 1 - ease(t / 0.06f)
        t < 0.2f -> ease((t - 0.06f) / 0.14f)
        else -> 1f
    }
    return min(lid(s % 3.7f), lid((s + 1.9f) % 9.1f))
}

/**
 * The shape of an eye in units: half its width, the height of its corners (down is positive), how far the upper lid
 * rises above the middle and the lower one drops below it (negative: the lid bows the other way, as in a smile), how
 * square the lids are (1 = a pill, less = a soft arc), the light in the eye, how much of it is a sparkle and how far
 * the eye on the viewer's left is shut in a wink.
 */
private const val EYE_W = 0
private const val EYE_CORNER = 1
private const val EYE_UP = 2
private const val EYE_DOWN = 3
private const val EYE_BOX = 4
private const val EYE_SHINE = 5
private const val EYE_STAR = 6
private const val EYE_WINK = 7

private val EYE_SHAPES: List<FloatArray> = LoraEyes.entries.map {
    when (it) {
        LoraEyes.OPEN -> floatArrayOf(1.4f, 0f, 1.9f, 1.9f, 1f, 1f, 0f, 0f)
        LoraEyes.WIDE -> floatArrayOf(1.65f, 0f, 2.3f, 2.3f, 1f, 1.15f, 0f, 0f)
        LoraEyes.SPARKLE -> floatArrayOf(1.75f, 0f, 2.35f, 2.35f, 1f, 1.2f, 1f, 0f)
        LoraEyes.FOCUS -> floatArrayOf(1.55f, 0.1f, 0.5f, 1.15f, 0.85f, 0.5f, 0f, 0f)
        LoraEyes.SOFT -> floatArrayOf(1.45f, 0.1f, 0.75f, 1.85f, 0.95f, 0.7f, 0f, 0f)
        LoraEyes.HAPPY -> floatArrayOf(1.6f, 0.75f, 1.5f, -0.95f, 0.62f, 0f, 0f, 0f)
        LoraEyes.CLOSED -> floatArrayOf(1.6f, -0.2f, -0.75f, 1.15f, 0.62f, 0f, 0f, 0f)
        LoraEyes.WINK -> floatArrayOf(1.4f, 0f, 1.9f, 1.9f, 1f, 1f, 0f, 1f)
    }
}

/**
 * The shape of the mouth in units: half its width, the height of its corners, where the upper and the lower lip are in
 * the middle (down is positive), how square it is, how visible, how much of the tongue shows inside and whether its
 * tip peeks out at the side (while she concentrates).
 */
private const val MOUTH_W = 0
private const val MOUTH_CORNER = 1
private const val MOUTH_TOP = 2
private const val MOUTH_BOTTOM = 3
private const val MOUTH_BOX = 4
private const val MOUTH_ALPHA = 5
private const val MOUTH_TONGUE = 6
private const val MOUTH_LICK = 7

private val MOUTH_SHAPES: List<FloatArray> = LoraMouth.entries.map {
    when (it) {
        LoraMouth.SMILE -> floatArrayOf(1.4f, -0.35f, 0.6f, 0.6f, 0.6f, 1f, 0f, 0f)
        LoraMouth.GRIN -> floatArrayOf(1.75f, -0.55f, -0.3f, 1.25f, 0.62f, 1f, 1f, 0f)
        LoraMouth.OPEN -> floatArrayOf(0.9f, 0.2f, -0.8f, 1.2f, 1f, 1f, 0.7f, 0f)
        LoraMouth.OOH -> floatArrayOf(0.62f, 0.2f, -0.45f, 0.85f, 1f, 1f, 0f, 0f)
        LoraMouth.FLAT -> floatArrayOf(1.05f, 0.2f, 0.2f, 0.2f, 0.6f, 1f, 0f, 0f)
        LoraMouth.NONE -> floatArrayOf(0.5f, 0.1f, 0.25f, 0.25f, 0.6f, 0f, 0f, 0f)
        LoraMouth.TONGUE -> floatArrayOf(1.3f, -0.25f, 0.5f, 0.5f, 0.6f, 1f, 0f, 1f)
    }
}

/** The face of one moment: the shapes of eyes and mouth ([EYE_SHAPES], [MOUTH_SHAPES]), moving from one look into the next. */
private class LoraFaceShape(val eye: FloatArray, val mouth: FloatArray)

/** The face of the last moments blended, so a new look grows out of the old one within a blink. */
private fun faceOf(trail: Array<LoraPose>): LoraFaceShape {
    val n = 4
    val eye = FloatArray(8)
    val mouth = FloatArray(8)
    for (k in 0 until n) {
        val e = EYE_SHAPES[trail[k].eyes.ordinal]
        val m = MOUTH_SHAPES[trail[k].mouth.ordinal]
        for (i in eye.indices) eye[i] += e[i] / n
        for (i in mouth.indices) mouth[i] += m[i] / n
    }
    return LoraFaceShape(eye, mouth)
}

/** Lora's pose of the last moments, the newest first ([TRAIL] steps of [TRAIL_MS] back). */
private fun trailOf(frame: LoraFrame, s: Float, now: LoraPose): Array<LoraPose> =
    Array(TRAIL + 1) { k -> if (k == 0) now else posed(earlier(frame, k * TRAIL_MS), s - k * TRAIL_MS / 1000f) }

/** Draws Lora at [timeMs] (seconds drive the small motions, [frame] the activity); runs without an animation clock. */
internal fun DrawScope.drawLora(frame: LoraFrame, timeMs: Long, c: LoraColors, gaze: Offset? = null) {
    val s = timeMs / 1000f
    val prev = frame.previous
    val same = prev == frame.action
    val b = ease(frame.blend)
    val base = posed(frame, s)
    // The motion of the last moments: Lora leans into it and settles after a stop, stretches in the air and gives in
    // the knees on landing, her arms and the antenna swing behind. Calmer with "Ruhigere Bewegungen".
    val trail = trailOf(frame, s, base)
    val lively = if (LoraMotion.calm) 0.35f else 1f
    val dt = TRAIL_MS / 1000f
    val vx = (trail[0].dx + trail[0].sway - trail[2].dx - trail[2].sway) / (2 * dt)
    val vy = (trail[0].hop - trail[2].hop) / (2 * dt)
    val vt = (trail[0].tilt - trail[2].tilt) / (2 * dt)
    val zx = jelly { trail[it].dx + trail[it].sway } * lively
    val zy = jelly { trail[it].hop } * lively
    val zt = jelly { trail[it].tilt } * lively
    // Walking: fast enough over the ground, her feet take steps.
    val walkV = (trail[0].dx - trail[TRAIL].dx) / (TRAIL * dt)
    val walk = ramp(abs(walkV), 2.5f, 8f)
    val strides = base.dx / STRIDE
    val bob = 0.55f * abs(sin(2 * PI * strides).toFloat()) * walk
    val stretch = (1 + (abs(vy) * 0.0035f).coerceAtMost(0.05f) * lively + (zy * 0.06f).coerceIn(-0.09f, 0.07f)) * (1 + 0.014f * wave(s, 0.3f))
    val armDrag = (vx * 0.35f - zx * 7f).coerceIn(-12f, 12f) * lively
    val armFloat = (-vy * 0.4f + zy * 7f).coerceIn(-12f, 16f) * lively
    fun swung(angle: Float, side: Int, weight: Float): Float {
        val rad = Math.toRadians(angle.toDouble())
        // Arms held in front are busy (a book, the keys); only those hanging or raised to the side swing behind.
        val free = ((angle + 10f) / 20f).coerceIn(0f, 1f) * (1 - weight)
        return angle + free * (-side * armDrag * cos(rad).toFloat() + armFloat * sin(rad).toFloat())
    }
    val moved = base.copy(
        tilt = base.tilt + (vx * 0.13f + zx * 2.4f).coerceIn(-7f, 7f) * lively,
        hop = base.hop + bob,
        squash = base.squash * stretch,
        armL = swung(base.armL, -1, base.reachLW),
        armR = swung(base.armR, 1, base.reachRW),
    )
    // While she is just there, her eyes follow the pointer (AP-36).
    val pose = if (gaze != null && frame.action in GAZE_ACTIONS) moved.copy(
        lookX = mix(moved.lookX, gaze.x, 0.75f), lookY = mix(moved.lookY, gaze.y * 0.8f, 0.75f), turn = moved.turn + 8 * gaze.x,
    ) else moved
    val lagX = (-vx * 0.045f + zx * 1.5f - vt * 0.02f + zt * 0.4f).coerceIn(-3f, 3f) * lively
    val lagY = (vy * 0.04f - zy * 0.9f).coerceIn(-1.6f, 2.2f) * lively
    val face = faceOf(trail)
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
    val cx = cx0 + (pose.dx + pose.sway) * u
    // Squashed, she gives in the knees: the body comes down while the feet stay where they are.
    val sink = if (pose.hop < 1.2f) ((1 - pose.squash) * 9f).coerceIn(-0.6f, 2.4f) * u * (1 - pose.hop / 1.2f).coerceIn(0f, 1f) else 0f
    val bottom = ground - 5 * u - hop + sink
    val top = bottom - bh
    // The turn of the head: the face moves sideways and narrows; past 90 degrees the back shows.
    val turnRad = Math.toRadians(pose.turn.toDouble())
    val facing = cos(turnRad).toFloat()
    val sideways = sin(turnRad).toFloat()
    val spread = 0.6f + 0.4f * abs(facing)
    val g = LoraGeo(u, cx0, oy, ground, cx, top, bottom, bw, bh, sideways * 7 * u)

    if (prev != null && outA > 0f) drawScene(prev, 1f, s, outA, g, c)
    drawScene(frame.action, frame.phase, s, inA, g, c)

    // Shadow on the ground, soft at the rim, smaller while hopping.
    val shadowW = bw * (1f - hop / (14 * u)).coerceAtLeast(0.4f) * 1.06f
    drawOval(c.shadow.fade(0.5f), Offset(cx - shadowW / 2, ground - 1.1f * u), Size(shadowW, 2.2f * u))
    drawOval(c.shadow, Offset(cx - shadowW * 0.39f, ground - 0.8f * u), Size(shadowW * 0.78f, 1.6f * u))

    // Legs with little boots. On the ground the feet stay put (the knees give, she rises on her toes), while walking
    // they take steps, in the air the legs hang.
    val tiltRad = Math.toRadians(pose.tilt.toDouble())
    val feetX = cx0 + pose.dx * u
    val heading = (walkV / 8f).coerceIn(-1f, 1f)
    for (side in listOf(-1, 1)) {
        val hx = side * 5.5f * u * spread
        val hip = Offset(cx + (hx * cos(tiltRad) + u * sin(tiltRad)).toFloat(), bottom + (hx * sin(tiltRad) - u * cos(tiltRad)).toFloat())
        val swing = if (side < 0) pose.legs else -pose.legs
        val q = strides + (if (side < 0) 0f else 0.5f)
        val ahead = stepOffset(q) * walk
        val lift = maxOf(0f, swing) * 0.9f * u + stepLift(q) * walk * 1.5f * u
        val stand = ground - 1.2f * u - lift
        val reach = hip.y + 5.9f * u
        val grounded = reach >= stand
        val toes = if (grounded) ((stand - hip.y - 4.8f * u) / (1.1f * u)).coerceIn(0f, 1f) else 0f
        val hang = if (grounded) 0f else maxOf(4.3f * u + (abs(vy) * 0.04f).coerceAtMost(1.3f) * u, 5.9f * u - (stand - reach) * 1.2f)
        val air = if (grounded) 0f else ((stand - reach) / (2 * u)).coerceIn(0f, 1f)
        val ankle = Offset(
            mix(feetX + side * 5.5f * u * spread + ahead * STRIDE * u + swing * 2.6f * u, hip.x + swing * 2.2f * u - side * 0.5f * u, air),
            if (grounded) stand - toes * 0.9f * u else hip.y + hang,
        )
        val dxl = ankle.x - hip.x
        val dyl = ankle.y - hip.y
        val slack = (4.8f * u) * (4.8f * u) - (dxl * dxl + dyl * dyl)
        val knee = if (slack > 0f) 0.45f * sqrt(slack) else 0f
        val bend = Offset((hip.x + ankle.x) / 2 + side * knee, (hip.y + ankle.y) / 2)
        limb(hip, bend, Offset(ankle.x, ankle.y - 0.5f * u), 3.2f * u, 2.7f * u, c.body.shade(-0.1f))
        val toe = (side * 0.45f * (1 - walk) + heading * walk + sideways * 1.2f).coerceIn(-1f, 1f)
        val pitch = 26f * toes + 16f * air - 12f * maxOf(0f, swing) - 14f * walk * (stepOffset(q) / 0.3f) * (if (heading < 0f) -1f else 1f)
        boot(ankle, toe, pitch, u, c)
    }

    rotate(pose.tilt, Offset(cx, bottom)) {
        // Antenna on a spring with a small star that pulses (dim while sleeping, bright with an idea).
        val pulse = 0.5f + 0.5f * wave(s, 0.9f)
        val tip = Offset(cx + 0.7f * u * wave(s, 0.6f) + lagX * u, top - 5.2f * u + lagY * u)
        val antenna = Path().apply { moveTo(cx, top + 0.6f * u); quadraticTo(cx, top - 2.4f * u, tip.x, tip.y) }
        drawPath(antenna, c.line, style = Stroke(1.25f * u, cap = StrokeCap.Round))
        val lit = min(1f, pose.glow)
        val halo = (0.16f + 0.1f * pulse) * lit + (if (pose.glow > 1f) 0.3f * (pose.glow - 1f) / 0.4f else 0f)
        drawCircle(Brush.radialGradient(listOf(c.spark.fade(halo), c.spark.fade(0f)), tip, 5 * u), 5 * u, tip)
        rotate(8f * wave(s, 0.45f) + lagX * 9f, tip) {
            star(tip, 2.4f * u * (0.88f + 0.12f * pulse) * (if (pose.glow > 1f) 1.15f else 1f), c.spark.fade((0.75f + 0.25f * pulse) * lit))
        }
        drawCircle(Color.White.copy(alpha = 0.55f * lit * c.spark.alpha), 0.55f * u, Offset(tip.x - 0.35f * u, tip.y - 0.35f * u))

        val bodyShape = bodyPath(cx, top, bw, bh, u)
        drawBody(bodyShape, cx, top, bw, bh, u, c, sideways)
        // Where the antenna sits.
        drawOval(c.body.shade(-0.3f).fade(0.6f), Offset(cx - 1.5f * u, top + 0.15f * u), Size(3 * u, 1.1f * u))

        val visorTop = top + 3.2f * u * pose.squash
        // The face gives a little with her motion, like something soft behind the glass.
        val slosh = Offset((zx * 0.25f).coerceIn(-0.5f, 0.5f) * u, (-zy * 0.3f).coerceIn(-0.5f, 0.6f) * u)
        if (facing > 0.04f) {
            // The little star on the chest and the face, both turned with the head.
            withTransform({ translate(left = sideways * 6 * u); scale(facing, 1f, Offset(cx, top + bh * 0.84f)) }) {
                star(Offset(cx, top + bh * 0.84f), 1.5f * u, Color.White.copy(alpha = 0.38f * c.body.alpha))
            }
            clipPath(bodyShape) {
                withTransform({ translate(left = g.faceDx); scale(facing, 1f, Offset(cx, visorTop + 5.5f * u)) }) {
                    drawFace(pose, face, s, cx, visorTop, u, c, slosh)
                }
            }
        } else if (facing < -0.04f) {
            // The back: a small hatch with two screws.
            withTransform({ translate(left = -g.faceDx); scale(-facing, 1f, Offset(cx, visorTop + 5.5f * u)) }) {
                drawRoundRect(c.body.shade(-0.22f).fade(0.7f), Offset(cx - 5 * u, visorTop + 2 * u), Size(10 * u, 7 * u), CornerRadius(1.6f * u))
                drawRoundRect(Color.White.copy(alpha = 0.16f * c.body.alpha), Offset(cx - 5 * u, visorTop + 2.35f * u), Size(10 * u, 7 * u), CornerRadius(1.6f * u), style = Stroke(0.35f * u))
                for (side in listOf(-1, 1)) drawCircle(Color.White.copy(alpha = 0.4f * c.body.alpha), 0.6f * u, Offset(cx + side * 3.4f * u, visorTop + 3.4f * u))
            }
        }

        // Arms in front of the body with a soft elbow; a shade under them keeps them visible on it. Hands come after the held things.
        val shoulderY = top + bh * 0.69f
        val armColor = c.body.shade(0.07f)
        val armShade = c.body.shade(-0.42f).fade(0.42f)
        fun shoulder(side: Int) = Offset(cx + side * (bw / 2 - 0.9f * u) * spread, shoulderY)
        /** The elbow and the hand of an arm. */
        fun arm(side: Int, degrees: Float, reach: Offset, weight: Float): Pair<Offset, Offset> {
            val rad = Math.toRadians(degrees.toDouble())
            val start = shoulder(side)
            var ex = side * (sin(rad) * 8 * u).toFloat() * spread
            var ey = (cos(rad) * 8 * u).toFloat()
            if (weight > 0f) {
                // The hand goes to a point (as far as the arm reaches).
                var tx = cx + reach.x * u - start.x
                var ty = bottom + reach.y * u - start.y
                val d = sqrt(tx * tx + ty * ty)
                val max = 9.5f * u
                if (d > max) {
                    tx *= max / d
                    ty *= max / d
                }
                ex = mix(ex, tx, weight)
                ey = mix(ey, ty, weight)
            }
            val end = Offset(start.x + ex, start.y + ey)
            val len = sqrt(ex * ex + ey * ey).coerceAtLeast(0.01f)
            val bend = 1.7f * u * (1 + 0.2f * wave(s + side * 0.4f, 0.7f))
            return Offset((start.x + end.x) / 2 + side * ey / len * bend, (start.y + end.y) / 2 - side * ex / len * bend) to end
        }
        val (elbowL, handL) = arm(-1, pose.armL, pose.reachL, pose.reachLW)
        val (elbowR, handR) = arm(1, pose.armR, pose.reachR, pose.reachRW)
        val shoulderL = shoulder(-1)
        val shoulderR = shoulder(1)
        // The shade of the arms falls on the body only.
        val low = Offset(0f, 0.45f * u)
        clipPath(bodyShape) {
            limb(shoulderL + low, elbowL + low, handL + low, 4.1f * u, 3.5f * u, armShade)
            limb(shoulderR + low, elbowR + low, handR + low, 4.1f * u, 3.5f * u, armShade)
        }
        limb(shoulderL, elbowL, handL, 3.3f * u, 2.6f * u, armColor)
        limb(shoulderR, elbowR, handR, 3.3f * u, 2.6f * u, armColor)

        if (prev != null && outA > 0f) drawHeld(prev, 1f, s, outA, g, c, shoulderL, shoulderR, handL, handR, front = false)
        drawHeld(frame.action, frame.phase, s, inA, g, c, shoulderL, shoulderR, handL, handR, front = false)
        clipPath(bodyShape) { for (hand in listOf(handL, handR)) drawCircle(armShade, 2.2f * u, Offset(hand.x, hand.y + 0.4f * u)) }
        for (hand in listOf(handL, handR)) {
            drawCircle(armColor, 1.75f * u, hand)
            drawCircle(Color.White.copy(alpha = 0.24f * c.body.alpha), 0.55f * u, Offset(hand.x - 0.55f * u, hand.y - 0.6f * u))
        }
        if (prev != null && outA > 0f) drawHeld(prev, 1f, s, outA, g, c, shoulderL, shoulderR, handL, handR, front = true)
        drawHeld(frame.action, frame.phase, s, inA, g, c, shoulderL, shoulderR, handL, handR, front = true)
    }
}

/** Width and height of the close-up's square space (units of the full figure). */
private const val FACE_SPACE = 30f

/**
 * Lower edge of the head in the close-up's space. With the star of the antenna about 27 units above it, the face is
 * centred in [FACE_SPACE] (earlier the chin lay at 30.5 – just outside the square, so round buttons cut it off).
 */
private const val FACE_CHIN = 28.6f

/**
 * Lora as a close-up (the head with antenna and face, no legs, arms or scene) in a square: for small places like the
 * button in the top bar or the sleeping Lora in a corner, where the whole figure would be tiny. Eyes and mouth follow
 * the activity of [frame] (happy while waving, closed while sleeping), she breathes, blinks and tilts her head a little;
 * while waving a hand waves at the side, while sleeping small z's rise. Everything stays inside the canvas: the face from
 * the antenna's star to the chin is centred in the square ([FACE_CHIN]), so in round buttons and pills she sits in the
 * middle instead of on the lower edge.
 */
internal fun DrawScope.drawLoraFace(frame: LoraFrame, timeMs: Long, c: LoraColors, gaze: Offset? = null) {
    val s = timeMs / 1000f
    val base = posed(frame, s)
    val trail = trailOf(frame, s, base)
    val lively = if (LoraMotion.calm) 0.35f else 1f
    val pose = if (gaze != null && frame.action in GAZE_ACTIONS) base.copy(
        lookX = mix(base.lookX, gaze.x, 0.75f), lookY = mix(base.lookY, gaze.y * 0.8f, 0.75f),
    ) else base
    val u = min(size.width, size.height) / FACE_SPACE
    val ox = (size.width - FACE_SPACE * u) / 2
    val oy = (size.height - FACE_SPACE * u) / 2
    val cx = ox + 15 * u
    // The head breathes and squashes a little with the pose (never as much as the whole figure hops).
    val zy = jelly { trail[it].hop } * lively
    val squash = 1 + (pose.squash - 1) * 0.5f + 0.014f * wave(s, 0.3f) + (zy * 0.03f).coerceIn(-0.04f, 0.04f)
    val bw = 23 * u
    val bh = 20 * u * squash
    val bottom = oy + FACE_CHIN * u
    val top = bottom - bh
    // A gentle tilt of the head: half of the figure's, at most six degrees.
    val tilt = (pose.tilt * 0.5f).coerceIn(-6f, 6f)
    val zt = jelly { trail[it].tilt } * lively
    rotate(tilt, Offset(cx, bottom)) {
        val pulse = 0.5f + 0.5f * wave(s, 0.9f)
        val tip = Offset(cx + 0.7f * u * wave(s, 0.6f) + (zt * 0.35f).coerceIn(-1.6f, 1.6f) * u, top - 4.6f * u + (-zy * 0.5f).coerceIn(-0.8f, 0.8f) * u)
        val antenna = Path().apply { moveTo(cx, top + 0.6f * u); quadraticTo(cx, top - 2.2f * u, tip.x, tip.y) }
        drawPath(antenna, c.line, style = Stroke(1.25f * u, cap = StrokeCap.Round))
        val lit = min(1f, pose.glow)
        val halo = (0.16f + 0.1f * pulse) * lit + (if (pose.glow > 1f) 0.3f * (pose.glow - 1f) / 0.4f else 0f)
        drawCircle(Brush.radialGradient(listOf(c.spark.fade(halo), c.spark.fade(0f)), tip, 4.2f * u), 4.2f * u, tip)
        rotate(8f * wave(s, 0.45f), tip) { star(tip, 2.3f * u * (0.88f + 0.12f * pulse), c.spark.fade((0.75f + 0.25f * pulse) * lit)) }
        drawCircle(Color.White.copy(alpha = 0.55f * lit * c.spark.alpha), 0.5f * u, Offset(tip.x - 0.35f * u, tip.y - 0.35f * u))
        val head = bodyPath(cx, top, bw, bh, u)
        drawBody(head, cx, top, bw, bh, u, c, 0f)
        drawOval(c.body.shade(-0.3f).fade(0.6f), Offset(cx - 1.5f * u, top + 0.15f * u), Size(3 * u, 1.1f * u))
        drawFace(pose, faceOf(trail), s, cx, top + 3.4f * u, u, c, Offset.Zero)
    }
    when (frame.action) {
        LoraAction.WAVE -> {
            // A hand waving at the lower right, with two small lines.
            val swing = wave(s, 1.8f)
            val hand = Offset(ox + 25.6f * u + swing * 0.8f * u, oy + (FACE_CHIN - 9.5f) * u - abs(swing) * 0.6f * u)
            drawCircle(c.body.shade(0.07f), 1.95f * u, hand)
            drawCircle(Color.White.copy(alpha = 0.24f * c.body.alpha), 0.6f * u, Offset(hand.x - 0.6f * u, hand.y - 0.65f * u))
            for (i in 0..1) {
                val r = (3.1f + i * 1.2f) * u
                drawArc(
                    c.line.copy(alpha = 0.6f), -115f, 60f, useCenter = false, topLeft = Offset(hand.x - r, hand.y - r),
                    size = Size(2 * r, 2 * r), style = Stroke(0.6f * u, cap = StrokeCap.Round),
                )
            }
        }
        LoraAction.SLEEP -> {
            for (i in 0..2) {
                val k = (s * 0.35f + i / 3f) % 1f
                // They rise beside the antenna and stay inside a round button (not out over its edge).
                val z = (0.8f + k * 0.6f) * u
                val center = Offset(ox + 21.5f * u + k * 3.5f * u, oy + 7.5f * u - k * 4.5f * u)
                val path = Path().apply {
                    moveTo(center.x - z, center.y - z); lineTo(center.x + z, center.y - z)
                    lineTo(center.x - z, center.y + z); lineTo(center.x + z, center.y + z)
                }
                drawPath(path, c.line.fade(sin(PI * k).toFloat()), style = Stroke(0.6f * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
        else -> Unit
    }
}

/**
 * Lora's visor with eyes, cheeks and mouth; she blinks now and then, sometimes twice. The glass sits a little inside
 * the body (a shade above, a light edge below), [face] gives the look of eyes and mouth, [slosh] moves them with her
 * motion.
 */
private fun DrawScope.drawFace(pose: LoraPose, face: LoraFaceShape, s: Float, cx: Float, visorTop: Float, u: Float, c: LoraColors, slosh: Offset) {
    val tl = Offset(cx - 9 * u, visorTop)
    val glass = Size(18 * u, 11 * u)
    val round = CornerRadius(5.5f * u)
    drawRoundRect(c.body.shade(-0.38f).fade(0.55f), Offset(tl.x - 0.25f * u, tl.y - 0.5f * u), Size(glass.width + 0.5f * u, glass.height + 0.6f * u), CornerRadius(5.7f * u))
    drawRoundRect(Color.White.copy(alpha = 0.3f * c.body.alpha), Offset(tl.x - 0.1f * u, tl.y + 0.4f * u), Size(glass.width + 0.2f * u, glass.height), round)
    drawRoundRect(Brush.verticalGradient(listOf(c.visor, c.visor.tint(c.body, 0.1f)), tl.y + 3 * u, tl.y + glass.height), tl, glass, round)
    val dark = c.visor.brightness() < 0.4f
    val visor = Path().apply { addRoundRect(RoundRect(tl.x, tl.y, tl.x + glass.width, tl.y + glass.height, round)) }
    clipPath(visor) {
        // The shade of the body on the upper rim of the glass, and a reflection across it.
        drawRoundRect(c.eye.fade(if (dark) 0.05f else 0.07f), Offset(tl.x, tl.y - glass.height + 1.3f * u), glass, round)
        val sheen = Path().apply {
            moveTo(tl.x + 3.2f * u, tl.y); lineTo(tl.x + 6.4f * u, tl.y); lineTo(tl.x + 2.2f * u, tl.y + glass.height); lineTo(tl.x - 1f * u, tl.y + glass.height); close()
        }
        drawPath(sheen, Color.White.copy(alpha = (if (dark) 0.05f else 0.5f) * c.visor.alpha))
    }

    val open = blink(s)
    val smiling = EYE_SHAPES[LoraEyes.HAPPY.ordinal]
    val eyeY = visorTop + 4.75f * u + pose.lookY * 1.05f * u + slosh.y
    for (side in listOf(-1, 1)) {
        // In a wink the eye on the viewer's left smiles shut.
        val wink = if (side < 0) face.eye[EYE_WINK] else 0f
        fun e(i: Int) = mix(face.eye[i], smiling[i], wink)
        // A blink closes the lids to a thin line just below the middle; smiling and closed eyes do not blink.
        val shut = (1 - open) * (e(EYE_SHINE) * 2).coerceIn(0f, 1f)
        val w = e(EYE_W) * u
        val corner = mix(e(EYE_CORNER), 0.1f, shut) * u
        val up = mix(e(EYE_UP), -0.3f, shut) * u
        val down = mix(e(EYE_DOWN), 0.55f, shut) * u
        val box = e(EYE_BOX)
        val shine = e(EYE_SHINE) * (1 - shut)
        val ex = cx + side * 3.95f * u + pose.lookX * 2 * u + slosh.x
        val shape = Path().apply {
            moveTo(ex - w, eyeY + corner)
            cubicTo(ex - w * box, eyeY - (4 * up + corner) / 3, ex + w * box, eyeY - (4 * up + corner) / 3, ex + w, eyeY + corner)
            cubicTo(ex + w * box, eyeY + (4 * down - corner) / 3, ex - w * box, eyeY + (4 * down - corner) / 3, ex - w, eyeY + corner)
            close()
        }
        // On a dark glass the eyes glow a little.
        if (dark) drawPath(shape, c.eye.fade(0.16f), style = Stroke(2f * u, join = StrokeJoin.Round))
        drawPath(shape, c.eye)
        drawPath(shape, c.eye, style = Stroke(0.6f * u, join = StrokeJoin.Round))
        if (shine > 0.05f) {
            val k = shine.coerceAtMost(1.2f)
            val light = Offset(ex - 0.5f * u - pose.lookX * 0.2f * u, eyeY - 0.8f * u * (up / (1.9f * u)).coerceIn(0.3f, 1.3f))
            val star = e(EYE_STAR)
            if (star > 0.05f) star(light, 1.25f * u * star * (0.85f + 0.15f * wave(s, 2.2f)), c.visor)
            drawCircle(c.visor, 0.62f * u * k * (1 - 0.6f * star), light)
            drawCircle(c.visor.fade(0.75f), 0.3f * u * k, Offset(ex + 0.55f * u, eyeY + 0.75f * u * (down / (1.9f * u)).coerceIn(0.2f, 1.2f)))
        }
        // Cheeks: a soft blush, a little rosier with a broad smile.
        val cheek = Offset(cx + side * 6.1f * u + pose.lookX * 0.8f * u + slosh.x, visorTop + (8.05f + pose.lookY * 0.3f) * u + slosh.y)
        val rosy = 0.42f + 0.16f * face.mouth[MOUTH_TONGUE]
        drawOval(c.blush.copy(alpha = rosy * 0.5f * c.blush.alpha), Offset(cheek.x - 1.75f * u, cheek.y - 1f * u), Size(3.5f * u, 2f * u))
        drawOval(c.blush.copy(alpha = rosy * c.blush.alpha), Offset(cheek.x - 1.3f * u, cheek.y - 0.7f * u), Size(2.6f * u, 1.4f * u))
    }

    val m = face.mouth
    val alpha = m[MOUTH_ALPHA]
    if (alpha > 0.02f) {
        val mx = cx + pose.lookX * 1.4f * u + slosh.x
        val my = visorTop + 8.35f * u + pose.lookY * 0.6f * u + slosh.y
        val mw = m[MOUTH_W] * u
        val mc = m[MOUTH_CORNER] * u
        val mt = m[MOUTH_TOP] * u
        val mb = m[MOUTH_BOTTOM] * u
        val mbox = m[MOUTH_BOX]
        val ink = c.eye.fade(alpha)
        val shape = Path().apply {
            moveTo(mx - mw, my + mc)
            cubicTo(mx - mw * mbox, my + (4 * mt - mc) / 3, mx + mw * mbox, my + (4 * mt - mc) / 3, mx + mw, my + mc)
            cubicTo(mx + mw * mbox, my + (4 * mb - mc) / 3, mx - mw * mbox, my + (4 * mb - mc) / 3, mx - mw, my + mc)
            close()
        }
        drawPath(shape, ink)
        val tongue = m[MOUTH_TONGUE]
        if (tongue > 0.05f && mb - mt > 0.5f * u) {
            clipPath(shape) { drawOval(c.blush.fade(alpha * tongue), Offset(mx - mw * 0.62f, my + mb - 0.75f * u), Size(mw * 1.24f, 1.6f * u)) }
        }
        drawPath(shape, ink, style = Stroke(0.8f * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
        val lick = m[MOUTH_LICK]
        if (lick > 0.05f) {
            val tip = Offset(mx + mw * 0.72f, my + mc + 0.75f * u)
            drawCircle(ink, 0.95f * u * lick, tip)
            drawCircle(c.blush.fade(alpha), 0.62f * u * lick, tip)
        }
    }
}

/** Height of the side desk's top (units above the ground). */
private const val DESK_H = 9.3f

private val Silver = Color(0xFFB0BEC5)
private val Envelope = Color(0xFFFFF4DA)
private val EnvelopeShade = Color(0xFFF2E2B8)
private val Marker = Color(0xFFFFE066)
private val Palette = Color(0xFFE8C9A0)
private val Wood = Color(0xFFB5835A)

/** A pop-in: grows a little past full size and settles. */
private fun pop(k: Float): Float = if (k <= 0f) 0f else overshoot(k)

/** The small side desk to Lora's right (stamping, stapling): its top seen slightly from above, two legs. */
private fun DrawScope.sideDesk(g: LoraGeo, c: LoraColors, a: Float) {
    val u = g.u
    val l = g.cx0 + 13 * u
    val r = g.cx0 + 31 * u
    val back = g.ground - 10f * u
    val front = g.ground - 8.6f * u
    val top = Path().apply { moveTo(l + 0.8f * u, back); lineTo(r - 0.8f * u, back); lineTo(r, front); lineTo(l, front); close() }
    drawPath(top, c.line.fade(a * 0.3f))
    drawRoundRect(c.line.fade(a * 0.85f), Offset(l, front), Size(r - l, 1.1f * u), CornerRadius(0.3f * u))
    for (x in listOf(l + 1.4f * u, r - 1.4f * u)) drawLine(c.line.fade(a * 0.85f), Offset(x, front + 1f * u), Offset(x, g.ground - 0.3f * u), 0.9f * u, StrokeCap.Round)
}

/** A thin strip of paper (papers under the stapler's jaw). */
private fun DrawScope.paperStrip(at: Offset, w: Float, h: Float, u: Float, c: LoraColors, a: Float) {
    drawRoundRect(Ivory.fade(a), at, Size(w, h), CornerRadius(0.15f * u))
    drawRoundRect(c.line.fade(a * 0.5f), at, Size(w, h), CornerRadius(0.15f * u), style = Stroke(0.15f * u))
}

/** A rubber stamp held at [hand]: knob and handle above, wooden block and red rubber below (its bottom 2.6 units lower). */
private fun DrawScope.stampObject(hand: Offset, u: Float, c: LoraColors, a: Float) {
    drawCircle(Wood.fade(a), 1.0f * u, Offset(hand.x, hand.y - 2.4f * u))
    drawRoundRect(Wood.fade(a), Offset(hand.x - 0.6f * u, hand.y - 2.2f * u), Size(1.2f * u, 3.4f * u), CornerRadius(0.4f * u))
    drawRoundRect(Wood.fade(a), Offset(hand.x - 2.1f * u, hand.y + 0.9f * u), Size(4.2f * u, 1.3f * u), CornerRadius(0.3f * u))
    drawRoundRect(c.line.fade(a * 0.5f), Offset(hand.x - 2.1f * u, hand.y + 0.9f * u), Size(4.2f * u, 1.3f * u), CornerRadius(0.3f * u), style = Stroke(0.15f * u))
    drawRoundRect(StampRed.fade(a), Offset(hand.x - 1.9f * u, hand.y + 2.1f * u), Size(3.8f * u, 0.5f * u), CornerRadius(0.15f * u))
}

/**
 * A page held up in [hand] to show it ([rise] 0 = still flat on the desk, 1 = upright): with the red mark of the
 * stamp ([stamped]) or two sheets with a staple.
 */
private fun DrawScope.shownSheet(hand: Offset, rise: Float, u: Float, c: LoraColors, a: Float, stamped: Boolean) {
    val w = 5.4f * u
    val h = mix(0.9f, 6.8f, rise) * u
    val center = Offset(hand.x + 1.4f * u, hand.y - (0.4f + 2.8f * rise) * u)
    rotate(-6f * rise, center) {
        if (!stamped) {
            drawRoundRect(Ivory.fade(a), Offset(center.x - w / 2 + 0.6f * u, center.y - h / 2 + 0.5f * u), Size(w, h), CornerRadius(0.25f * u))
            drawRoundRect(c.line.fade(a * 0.5f), Offset(center.x - w / 2 + 0.6f * u, center.y - h / 2 + 0.5f * u), Size(w, h), CornerRadius(0.25f * u), style = Stroke(0.15f * u))
        }
        val tl = Offset(center.x - w / 2, center.y - h / 2)
        drawRoundRect(Ivory.fade(a), tl, Size(w, h), CornerRadius(0.25f * u))
        drawRoundRect(c.line.fade(a * 0.6f), tl, Size(w, h), CornerRadius(0.25f * u), style = Stroke(0.18f * u))
        if (rise > 0.6f) {
            val ra = a * ramp(rise, 0.6f, 0.9f)
            for (k in 0..2) drawLine(c.line.fade(ra * 0.45f), Offset(tl.x + 0.7f * u, tl.y + (1.2f + k * 0.9f) * u), Offset(tl.x + w - (0.8f + k * 0.6f) * u, tl.y + (1.2f + k * 0.9f) * u), 0.25f * u, StrokeCap.Round)
            if (stamped) {
                val m = Offset(center.x, tl.y + 4.9f * u)
                rotate(-10f, m) {
                    drawRoundRect(StampRed.fade(ra * 0.9f), Offset(m.x - 1.8f * u, m.y - 0.9f * u), Size(3.6f * u, 1.8f * u), CornerRadius(0.4f * u), style = Stroke(0.3f * u))
                    val tick = Path().apply { moveTo(m.x - 0.8f * u, m.y); lineTo(m.x - 0.2f * u, m.y + 0.5f * u); lineTo(m.x + 0.9f * u, m.y - 0.5f * u) }
                    drawPath(tick, StampRed.fade(ra * 0.9f), style = Stroke(0.3f * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            } else {
                drawLine(Silver.fade(ra), Offset(tl.x + 0.5f * u, tl.y + 0.9f * u), Offset(tl.x + 1.5f * u, tl.y + 0.4f * u), 0.35f * u, StrokeCap.Round)
            }
        }
    }
}

/** A sheet from [tl] to [br] with written [lines] (their y). */
private fun DrawScope.sheet(tl: Offset, br: Offset, u: Float, c: LoraColors, a: Float, lines: List<Float>) {
    if (a <= 0f) return
    val size = Size(br.x - tl.x, br.y - tl.y)
    drawRoundRect(Ivory.fade(a), tl, size, CornerRadius(0.3f * u))
    drawRoundRect(c.line.fade(a * 0.6f), tl, size, CornerRadius(0.3f * u), style = Stroke(0.2f * u))
    lines.forEachIndexed { i, y -> drawLine(c.line.fade(a * 0.45f), Offset(tl.x + 0.8f * u, y), Offset(br.x - (1f + (i % 2) * 1.6f) * u, y), 0.28f * u, StrokeCap.Round) }
}

/** Scissors whose blades meet at [tip], pointing along [angle] (degrees; 180 = to the left), [open] 0..1. */
private fun DrawScope.scissors(tip: Offset, angle: Float, open: Float, u: Float, c: LoraColors, a: Float) {
    if (a <= 0f) return
    rotate(angle - 180f, tip) {
        val pivot = Offset(tip.x + 3.2f * u, tip.y)
        for (sgn in listOf(-1f, 1f)) {
            val bladeTip = Offset(tip.x, tip.y + sgn * open * 1.3f * u)
            val blade = Path().apply {
                moveTo(pivot.x + 0.3f * u, pivot.y - 0.6f * u); lineTo(bladeTip.x, bladeTip.y); lineTo(pivot.x + 0.3f * u, pivot.y + 0.6f * u); close()
            }
            drawPath(blade, Silver.fade(a))
            drawPath(blade, c.line.fade(a * 0.9f), style = Stroke(0.25f * u, join = StrokeJoin.Round))
            val ring = Offset(pivot.x + 1.9f * u, pivot.y - sgn * (0.6f + open * 0.8f) * u)
            drawLine(c.prop.fade(a), pivot, ring, 0.5f * u, StrokeCap.Round)
            drawCircle(c.prop.fade(a), 0.9f * u, ring, style = Stroke(0.5f * u))
        }
        drawCircle(c.line.fade(a), 0.35f * u, pivot)
    }
}

/** A page of the wall calendar ([fold] 1 = hanging, 0 = flipped over the top), its header in [header]. */
private fun DrawScope.calendarPage(g: LoraGeo, c: LoraColors, a: Float, x0: Float, x1: Float, top: Float, bot: Float, fold: Float, header: Color, grid: Boolean) {
    if (fold <= 0f) return
    val u = g.u
    val h = (top - bot) * fold
    val tl = g.at2(x0, top)
    drawRoundRect(Ivory.fade(a), tl, Size((x1 - x0) * u, h * u), CornerRadius(0.5f * u))
    drawRoundRect(c.line.fade(a * 0.6f), tl, Size((x1 - x0) * u, h * u), CornerRadius(0.5f * u), style = Stroke(0.25f * u))
    drawRoundRect(header.fade(a), tl, Size((x1 - x0) * u, minOf(2.2f, h) * u), CornerRadius(0.5f * u))
    if (!grid || fold < 0.95f) return
    for (row in 0..3) for (col in 0..3) {
        val center = g.at2(-24.75f + col * 2.5f, 19.25f - row * 2f)
        drawRoundRect(c.line.fade(a * 0.3f), Offset(center.x - 0.7f * u, center.y - 0.55f * u), Size(1.4f * u, 1.1f * u), CornerRadius(0.25f * u))
    }
}

/** A speech bubble above Lora at [x] (units from the middle): two lines of text and a small flag (German or English). */
private fun DrawScope.speechBubble(g: LoraGeo, c: LoraColors, a: Float, x: Float, scale: Float, german: Boolean) {
    if (a <= 0f || scale <= 0f) return
    val u = g.u
    val center = g.at2(x, 33.5f)
    withTransform({ scale(scale, scale, center) }) {
        val w = 13f * u
        val h = 7f * u
        val tl = Offset(center.x - w / 2, center.y - h / 2)
        val tailX = center.x - (if (x > 0) 3f else -3f) * u
        val tail = Path().apply { moveTo(tailX - 1f * u, tl.y + h - 0.2f * u); lineTo(g.cx0 + (if (x > 0) 7f else -7f) * u, g.ground - 27.6f * u); lineTo(tailX + 1f * u, tl.y + h - 0.2f * u); close() }
        drawPath(tail, c.visor.fade(a))
        drawRoundRect(c.visor.fade(a), tl, Size(w, h), CornerRadius(2f * u))
        drawRoundRect(c.line.fade(a * 0.7f), tl, Size(w, h), CornerRadius(2f * u), style = Stroke(0.25f * u))
        val flag = Offset(tl.x + 1.3f * u, tl.y + 1.3f * u)
        if (german) {
            listOf(Color(0xFF1B1B1B), StampRed, Color(0xFFFFCC00)).forEachIndexed { i, col -> drawRect(col.fade(a), Offset(flag.x, flag.y + i * 1.2f * u), Size(5f * u, 1.2f * u)) }
        } else {
            drawRect(Color(0xFF1F3A93).fade(a), flag, Size(5f * u, 3.6f * u))
            drawLine(Color.White.fade(a), Offset(flag.x + 2.5f * u, flag.y), Offset(flag.x + 2.5f * u, flag.y + 3.6f * u), 0.9f * u)
            drawLine(Color.White.fade(a), Offset(flag.x, flag.y + 1.8f * u), Offset(flag.x + 5f * u, flag.y + 1.8f * u), 0.9f * u)
            drawLine(StampRed.fade(a), Offset(flag.x + 2.5f * u, flag.y), Offset(flag.x + 2.5f * u, flag.y + 3.6f * u), 0.45f * u)
            drawLine(StampRed.fade(a), Offset(flag.x, flag.y + 1.8f * u), Offset(flag.x + 5f * u, flag.y + 1.8f * u), 0.45f * u)
        }
        for (k in 0..1) drawLine(c.line.fade(a * 0.5f), Offset(tl.x + 7.2f * u, tl.y + (2.2f + k * 1.6f) * u), Offset(tl.x + w - (1.2f + k * 1.4f) * u, tl.y + (2.2f + k * 1.6f) * u), 0.35f * u, StrokeCap.Round)
    }
}

/** Lora's colors faded to [a] (e.g. at the end of her flight). */
internal fun LoraColors.faded(a: Float): LoraColors = if (a >= 1f) this else LoraColors(
    body = body.fade(a), visor = visor.fade(a), eye = eye.fade(a), line = line.fade(a), prop = prop.fade(a),
    spark = spark.fade(a), blush = blush.fade(a), shadow = shadow.fade(a),
)
