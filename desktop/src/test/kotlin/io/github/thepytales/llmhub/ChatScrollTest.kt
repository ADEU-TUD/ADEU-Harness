package io.github.thepytales.llmhub

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import io.github.thepytales.llmhub.ui.ChatAutoScroll
import io.github.thepytales.llmhub.ui.END
import io.github.thepytales.llmhub.ui.atEnd
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The chat follows a streaming answer, stops when the user scrolls up and follows again at the end. */
@OptIn(ExperimentalTestApi::class)
class ChatScrollTest {
    @Test
    fun followsStopsAndResumes() = runComposeUiTest {
        val messages = mutableStateOf((1..30).map { "Nachricht $it" })
        val lines = mutableIntStateOf(1)
        val follow = mutableStateOf(true)
        lateinit var state: LazyListState
        setContent {
            state = remember { LazyListState(30, END) }
            val count = messages.value.size + 1
            ChatAutoScroll(state, follow, remember(messages.value, lines.intValue) { Any() }, count)
            LazyColumn(state = state, modifier = Modifier.size(300.dp, 400.dp).testTag("list")) {
                items(messages.value, key = { it }) { Text(it, Modifier.height(40.dp)) }
                item(key = "answer") { Text((1..lines.intValue).joinToString("\n") { "Zeile $it" }) }
            }
        }
        waitForIdle()
        // A new message and a growing answer: the end stays in view.
        messages.value = messages.value + "Neue Frage"
        lines.intValue = 40
        waitForIdle()
        assertTrue(follow.value)
        assertTrue(atEnd(state), "Ende sichtbar")
        assertFalse(state.canScrollForward)

        // The user scrolls up into the answer: following stops.
        onNodeWithTag("list").performTouchInput {
            down(center)
            repeat(10) { moveBy(Offset(0f, 15f), delayMillis = 50) }
            up()
        }
        waitForIdle()
        assertFalse(follow.value)
        assertEquals(31, state.firstVisibleItemIndex, "liest in der Antwort")

        // The answer grows line by line – frame by frame, the text being read must not move at all (no jitter).
        val firstIndex = state.firstVisibleItemIndex
        val firstOffset = state.firstVisibleItemScrollOffset
        val firstPlaced = state.layoutInfo.visibleItemsInfo.first().offset
        mainClock.autoAdvance = false
        repeat(30) { i ->
            lines.intValue = 41 + i
            mainClock.advanceTimeByFrame()
            waitForIdle()
            assertEquals(firstIndex, state.firstVisibleItemIndex, "Bild $i: dieselbe Nachricht oben")
            assertEquals(firstOffset, state.firstVisibleItemScrollOffset, "Bild $i: Text bleibt stehen")
            assertEquals(firstPlaced, state.layoutInfo.visibleItemsInfo.first().offset, "Bild $i: gezeichnet an derselben Stelle")
        }
        mainClock.autoAdvance = true
        assertFalse(follow.value)

        // Back to the very end: following starts again, also for the next question.
        repeat(8) { onNodeWithTag("list").performTouchInput { swipeUp() } }
        waitForIdle()
        assertTrue(follow.value, "unten angekommen → folgen")
        mainClock.autoAdvance = false
        repeat(10) { i ->
            lines.intValue = 80 + i
            mainClock.advanceTimeByFrame()
            waitForIdle()
            assertTrue(atEnd(state), "Bild $i: Ende bleibt im gleichen Bild sichtbar")
        }
        mainClock.autoAdvance = true
        messages.value = messages.value + "Noch eine Frage"
        waitForIdle()
        assertTrue(atEnd(state))
    }
}
