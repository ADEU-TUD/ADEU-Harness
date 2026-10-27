package io.github.thepytales.llmhub.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.snapshotFlow

/**
 * Keeps the newest message in view while an answer streams in – like in the Claude app. The chat list runs from top
 * to bottom (oldest first), so the list keeps the topmost visible text where it is: while the user reads further up,
 * a growing answer only grows below and nothing moves. Following the answer means jumping to the end in the same
 * frame the new text is measured ([LazyListState.requestScrollToItem]), so the bottom does not jitter either.
 *
 * Scrolling up switches following off at once; scrolling back to the very end (or the arrow button, or sending)
 * switches it on again. [follow] is shared with the screen (sending turns it on); [content] changes with every new
 * message or streamed piece; [itemCount] is the number of items in the list.
 */
@Composable
fun ChatAutoScroll(listState: LazyListState, follow: MutableState<Boolean>, content: Any?, itemCount: Int) {
    // A scroll gesture (finger, mouse wheel, scroll bar) decides at its end: at the end → follow, else not.
    LaunchedEffect(listState) {
        var wasScrolling = false
        snapshotFlow { listState.isScrollInProgress }.collect { scrolling ->
            if (wasScrolling && !scrolling) follow.value = atEnd(listState)
            wasScrolling = scrolling
        }
    }
    // Moving away from the end during a gesture stops following right away (not only when the finger lifts).
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress && !atEnd(listState) }.collect { away -> if (away) follow.value = false }
    }
    // New text ([content] changed, so this composable runs again): stay at the end in the very frame it is measured.
    SideEffect {
        if (follow.value && itemCount > 0 && !listState.isScrollInProgress) listState.requestScrollToItem(itemCount - 1, END)
    }
    // Growth without new text (expanding boxes, pictures that load, the keyboard): follow on the next frame.
    LaunchedEffect(listState) {
        snapshotFlow { Triple(listState.layoutInfo.totalItemsCount, listState.canScrollForward, listState.layoutInfo.viewportSize) }
            .collect { (count, forward, _) ->
                if (follow.value && forward && count > 0 && !listState.isScrollInProgress) listState.requestScrollToItem(count - 1, END)
            }
    }
}

/** Offset beyond any item: the list stops at the end of the last item (the bottom edge of the chat). */
const val END = 1_000_000

/** The end of the newest message is visible (a few pixels of tolerance). */
fun atEnd(listState: LazyListState): Boolean {
    val info = listState.layoutInfo
    if (info.totalItemsCount == 0) return true
    val last = info.visibleItemsInfo.lastOrNull() ?: return true
    if (last.index != info.totalItemsCount - 1) return false
    return last.offset + last.size <= info.viewportEndOffset - info.afterContentPadding + 4
}

/** Scrolls to the end of the chat with an animation (arrow button). */
suspend fun scrollToEnd(listState: LazyListState) {
    val count = listState.layoutInfo.totalItemsCount
    if (count == 0) return
    listState.animateScrollToItem(count - 1)
    listState.scrollToItem(count - 1, END)
}
