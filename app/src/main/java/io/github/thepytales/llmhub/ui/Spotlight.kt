package io.github.thepytales.llmhub.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * The glowing frame Lora puts around an option after leading there (AP-07): [show] marks an option id (see
 * data/AppMap, e.g. "appearance.theme"); the screen that has a [Spot] with that id scrolls it into view and lets the
 * frame pulse for a few seconds.
 */
object Spotlight {
    val target = MutableStateFlow<String?>(null)

    fun show(id: String?) {
        target.value = id
    }

    /** How long the frame stays. */
    const val DURATION_MS = 4500L

    /** Where each spot is on screen (root coordinates), so Lora can fly there (ui/LoraFlight). */
    val bounds: MutableMap<String, androidx.compose.ui.geometry.Rect> = java.util.concurrent.ConcurrentHashMap()
}

/** Wraps an option that Lora can highlight ([id] as in data/AppMap). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Spot(id: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val target by Spotlight.target.collectAsStateWithLifecycle()
    val active = target == id
    val requester = remember { BringIntoViewRequester() }
    androidx.compose.runtime.DisposableEffect(id) { onDispose { Spotlight.bounds.remove(id) } }
    LaunchedEffect(active) {
        if (active) {
            delay(250)
            runCatching { requester.bringIntoView() }
            delay(Spotlight.DURATION_MS)
            if (Spotlight.target.value == id) Spotlight.target.value = null
        }
    }
    val shape = RoundedCornerShape(14.dp)
    val glow = if (active) {
        val pulse = rememberInfiniteTransition(label = "spot")
        pulse.animateFloat(0.35f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "spotAlpha").value
    } else 0f
    val color = MaterialTheme.colorScheme.primary
    Box(
        modifier
            .bringIntoViewRequester(requester)
            .onGloballyPositioned { Spotlight.bounds[id] = it.boundsInRoot() }
            .then(
                if (active) Modifier
                    .border(3.dp, color.copy(alpha = glow), shape)
                    .background(color.copy(alpha = 0.06f + 0.06f * glow), shape)
                    .padding(6.dp)
                else Modifier,
            ),
    ) { content() }
}
