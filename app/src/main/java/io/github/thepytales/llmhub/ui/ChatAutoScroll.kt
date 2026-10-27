package io.github.thepytales.llmhub.ui

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow

/**
 * Keeps the newest message in view while an answer streams in – like in the Claude app. The chat list is reversed
 * (item 0 at the bottom). Scrolling up switches following off at once; scrolling back to the very bottom (or the
 * arrow button, or sending) switches it on again. While it is off, a growing answer does not push the text away
 * that is being read.
 *
 * [follow] is shared with the screen (sending turns it on); [content] changes with every new message or streamed piece.
 */
@Composable
fun ChatAutoScroll(listState: LazyListState, follow: MutableState<Boolean>, content: Any?) {
    // Scrolls of this helper itself are no gesture of the user.
    val own = remember(listState) { BooleanArray(1) }
    suspend fun ownScroll(block: suspend () -> Unit) {
        own[0] = true
        try { block() } finally { own[0] = false }
    }
    // A scroll gesture (finger, mouse wheel, scroll bar) decides at its end: at the bottom → follow, else not.
    LaunchedEffect(listState) {
        var wasScrolling = false
        snapshotFlow { listState.isScrollInProgress }.collect { scrolling ->
            if (wasScrolling && !scrolling && !own[0]) follow.value = atBottom(listState)
            wasScrolling = scrolling
        }
    }
    // Moving up during a gesture stops following right away (not only when the finger lifts).
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress && !atBottom(listState) }.collect { up -> if (up && !own[0]) follow.value = false }
    }
    LaunchedEffect(content) {
        if (follow.value && !listState.isScrollInProgress && !atBottom(listState)) ownScroll { listState.scrollToItem(0) }
    }
    // Not following and reading inside the growing answer: keep the visible text where it is.
    LaunchedEffect(listState) {
        var lastKey: Any? = null
        var lastSize = 0
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == 0 }?.let { it.key to it.size } }.collect { info ->
            val (key, size) = info ?: return@collect
            val grew = size - lastSize
            if (key == lastKey && grew > 0 && !follow.value && listState.firstVisibleItemIndex == 0 && !listState.isScrollInProgress) {
                ownScroll { listState.scrollBy(grew.toFloat()) }
            }
            lastKey = key
            lastSize = size
        }
    }
}

/** The bottom of the newest message is visible (a few pixels of tolerance). */
fun atBottom(listState: LazyListState): Boolean = listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset <= 4
