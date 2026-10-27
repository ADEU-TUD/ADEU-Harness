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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlin.math.min

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

/** Draws Lora at [timeMs] (seconds drive the small motions, [frame] the activity); runs without an animation clock. */
internal fun DrawScope.drawLora(frame: LoraFrame, timeMs: Long, c: LoraColors) {
    // Placeholder until the drawing of the activities is in: body and visor.
    val u = min(size.height / 56f, size.width / 80f)
    val cx = size.width / 2
    val ground = size.height - 3.5f * u
    val top = ground - 26 * u
    drawOval(c.shadow, Offset(cx - 12 * u, ground - 0.9f * u), Size(24 * u, 1.8f * u))
    drawRoundRect(c.body, Offset(cx - 12 * u, top), Size(24 * u, 21 * u), CornerRadius(9 * u))
    drawRoundRect(c.visor, Offset(cx - 9 * u, top + 3.2f * u), Size(18 * u, 11 * u), CornerRadius(5.5f * u))
}
