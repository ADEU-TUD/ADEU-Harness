package io.github.thepytales.llmhub.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** Width of Lora in the card while the agent works. */
private val CARD_LORA_WIDTH = 110.dp

/**
 * Shown instead of the thinking and the tool steps while the agent works (Einstellungen › Chat & Werkzeuge ›
 * Arbeitsschritte einklappen): Lora, the mascot, acts out research things – matching the current step ([toolName],
 * [thinking]) now and then –, next to it „Lora ist dran“, the current [step] and a small pill with the number of steps
 * that opens them; a tap on Lora brings a little reaction. The card has a fixed height, so nothing jumps when the step
 * changes or the pill appears. With [hideLora] (Einstellungen › Chat & Werkzeuge › Lora ausblenden) a smaller card shows
 * the pulsing brand symbol instead.
 */
@Composable
fun WorkingCard(
    step: String?,
    toolName: String?,
    thinking: Boolean,
    stepsCount: Int,
    open: Boolean,
    hideLora: Boolean = false,
    onToggle: (() -> Unit)?,
) {
    val loraHeight = CARD_LORA_WIDTH / LORA_ASPECT
    Surface(
        color = LocalChatLook.current.card,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().height(if (hideLora) 64.dp else loraHeight + 12.dp),
    ) {
        Row(Modifier.padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (hideLora) {
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) { PulsingBrandSymbol(Modifier.size(24.dp)) }
            } else {
                val director = rememberLoraDirector()
                var taps by remember { mutableIntStateOf(0) }
                LoraFigure(
                    Modifier
                        .size(CARD_LORA_WIDTH, loraHeight)
                        // A tap: Lora waves, spins or has an idea – and then goes back to work.
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClickLabel = tr("Lora antippen"),
                        ) { director.play(LORA_TAP_REACTIONS[taps++ % LORA_TAP_REACTIONS.size]) },
                    label = tr("Lora bei der Arbeit"),
                    director = director,
                    hint = loraHint(toolName, thinking),
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Row(Modifier.fillMaxWidth().height(32.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        tr(if (hideLora) "Arbeitet an der Antwort" else "Lora ist dran"),
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (onToggle != null && stepsCount > 0) StepsPill(stepsCount, open, onToggle)
                }
                AnimatedContent(
                    targetState = step ?: tr("Bereitet die Antwort vor …"),
                    transitionSpec = { (fadeIn(tween(220)) + slideInVertically(tween(220)) { it / 3 }) togetherWith fadeOut(tween(150)) },
                    label = "step",
                ) { text ->
                    Text(
                        text,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}

/** The brand symbol, slowly pulsing (in place of Lora while Lora is hidden). */
@Composable
private fun PulsingBrandSymbol(modifier: Modifier) {
    val pulse by rememberInfiniteTransition(label = "symbol").animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "alpha",
    )
    Icon(
        Icons.Outlined.AutoAwesome, contentDescription = null, tint = LocalChatLook.current.brand,
        modifier = modifier.graphicsLayer { alpha = pulse },
    )
}

/** Number of steps with a chevron; opens and closes them. */
@Composable
private fun StepsPill(count: Int, open: Boolean, onToggle: () -> Unit) {
    val label = if (open) tr("Schritte ausblenden") else tr("Schritte anzeigen")
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .clickable(onClickLabel = label, onClick = onToggle)
            .semantics(mergeDescendants = true) { contentDescription = "$label ($count)" },
    ) {
        Row(Modifier.padding(start = 8.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Checklist, contentDescription = null, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text("$count", style = MaterialTheme.typography.labelMedium)
            Icon(if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, contentDescription = null, modifier = Modifier.size(16.dp))
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

/** An idea in Lora's speech bubble on the start page: [kind] is the small heading („Idee“, „Vorlage“). */
internal class LoraIdea(val label: String, val kind: String, val template: Boolean, val run: () -> Unit)

/** Counts the visits of the start page, so every new chat begins with another idea. */
private var ideaVisits = 0

/** Lora dozes off after this long without a tap or typing. */
internal const val LORA_SLEEP_AFTER_MS = 45_000L

/**
 * Lora on the start page: waves hello, then waits (looks around, sips coffee, reads, spins the globe …), offers one of
 * the [ideas] in a speech bubble (tap: use it; ↻ or a tap on Lora: the next one, with a flash of inspiration), takes
 * notes while the user is [typing] and dozes off after a while. Without ideas the bubble is left out.
 */
@Composable
internal fun LoraHome(ideas: List<LoraIdea>, typing: Boolean, loraHeight: Dp, modifier: Modifier = Modifier) {
    val director = rememberLoraDirector(HOME_SEQUENCE, start = LoraAction.WAVE, minDwellMs = 600)
    var index by remember { mutableIntStateOf(ideaVisits++) }
    var sleeping by remember { mutableStateOf(false) }
    var touches by remember { mutableIntStateOf(0) }
    LaunchedEffect(typing, touches) {
        if (typing && sleeping) {
            sleeping = false
            director.play(LoraAction.WAVE)
        }
        if (!typing) {
            delay(LORA_SLEEP_AFTER_MS)
            sleeping = true
        }
    }
    fun nextIdea() {
        touches++
        if (sleeping) {
            sleeping = false
            director.play(LoraAction.WAVE)
        } else {
            director.play(LoraAction.EUREKA)
            index++
        }
    }
    val hint = when {
        typing -> listOf(LoraAction.NOTES)
        sleeping -> listOf(LoraAction.SLEEP)
        else -> emptyList()
    }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        if (ideas.isNotEmpty()) {
            // The bubble keeps its place while hidden, so Lora and the greeting stay put when typing starts.
            val shown by animateFloatAsState(if (typing) 0f else 1f, tween(250), label = "bubble")
            Column(Modifier.alpha(shown), horizontalAlignment = Alignment.CenterHorizontally) {
                IdeaBubble(ideas, index, enabled = !typing, onUse = { touches++; ideas[index.mod(ideas.size)].run() }, onNext = ::nextIdea)
                BubbleTail()
            }
        }
        LoraFigure(
            Modifier
                // The sky above Lora stays free: the light bulb and the stars of the telescope rise up to the bubble.
                .size(loraHeight * LORA_ASPECT, loraHeight)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClickLabel = tr("Andere Idee"),
                    onClick = ::nextIdea,
                ),
            label = tr("Lora, das Maskottchen der App"),
            director = director,
            hint = hint,
            hintEvery = 1,
        )
    }
}

/**
 * The ideas without Lora (Lora ausblenden): the same bubble without the tail, e.g. below the greeting; it steps aside
 * while the user is [typing].
 */
@Composable
internal fun IdeaRow(ideas: List<LoraIdea>, typing: Boolean, modifier: Modifier = Modifier) {
    if (ideas.isEmpty()) return
    var index by remember { mutableIntStateOf(ideaVisits++) }
    val shown by animateFloatAsState(if (typing) 0f else 1f, tween(250), label = "ideas")
    Box(modifier.alpha(shown)) {
        IdeaBubble(ideas, index, enabled = !typing, onUse = { ideas[index.mod(ideas.size)].run() }, onNext = { index++ })
    }
}

@Composable
private fun IdeaBubble(ideas: List<LoraIdea>, index: Int, enabled: Boolean, onUse: () -> Unit, onNext: () -> Unit) {
    val look = LocalChatLook.current
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = look.card,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.widthIn(min = 200.dp, max = 340.dp),
    ) {
        Row(Modifier.height(54.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier
                    .weight(1f, fill = false)
                    .clip(RoundedCornerShape(18.dp))
                    .clickable(enabled = enabled, onClick = onUse)
                    .padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AnimatedContent(
                    targetState = index,
                    transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(180)) using SizeTransform(clip = false) },
                    modifier = Modifier.weight(1f, fill = false),
                    label = "idea",
                ) { i ->
                    val idea = ideas[i.mod(ideas.size)]
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (idea.template) Icons.Outlined.Bookmarks else Icons.Outlined.Lightbulb,
                            contentDescription = null,
                            tint = look.brand,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f, fill = false)) {
                            Text(
                                idea.kind,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                            Text(idea.label, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            IconButton(onClick = onNext, enabled = enabled, modifier = Modifier.padding(end = 4.dp).size(40.dp)) {
                Icon(Icons.Outlined.Autorenew, contentDescription = tr("Andere Idee"), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** The small tail under the speech bubble, pointing down at Lora. */
@Composable
private fun BubbleTail() {
    val fill = LocalChatLook.current.card
    val edge = MaterialTheme.colorScheme.outlineVariant
    // Overlaps the bubble's lower edge by a pixel row, so the border is open where the tail joins.
    Canvas(Modifier.size(18.dp, 9.dp).offset(y = (-1).dp)) {
        val tail = Path().apply {
            moveTo(0f, 0f)
            lineTo(size.width / 2, size.height)
            lineTo(size.width, 0f)
            close()
        }
        drawPath(tail, fill)
        val sides = Path().apply {
            moveTo(0f, 0f)
            lineTo(size.width / 2, size.height)
            lineTo(size.width, 0f)
        }
        drawPath(sides, edge, style = Stroke(1.dp.toPx()))
    }
}
