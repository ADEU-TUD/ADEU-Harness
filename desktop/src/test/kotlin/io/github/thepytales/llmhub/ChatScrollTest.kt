package io.github.thepytales.llmhub

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import io.github.thepytales.llmhub.ui.ChatAutoScroll
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The chat follows a streaming answer, stops when the user scrolls up and follows again at the bottom. */
@OptIn(ExperimentalTestApi::class)
class ChatScrollTest {
    @Test
    fun followsStopsAndResumes() = runComposeUiTest {
        val messages = mutableStateOf((1..30).map { "Nachricht $it" })
        val lines = mutableIntStateOf(1)
        val follow = mutableStateOf(true)
        lateinit var state: androidx.compose.foundation.lazy.LazyListState
        setContent {
            state = rememberLazyListState()
            val reversed = remember(messages.value) { messages.value.asReversed() }
            ChatAutoScroll(state, follow, remember(messages.value, lines.intValue) { Any() })
            LazyColumn(state = state, reverseLayout = true, modifier = Modifier.size(300.dp, 400.dp).testTag("list")) {
                item(key = "answer") { Text((1..lines.intValue).joinToString("\n") { "Zeile $it" }) }
                items(reversed, key = { it }) { Text(it, Modifier.height(40.dp)) }
            }
        }
        waitForIdle()
        // A new message and a growing answer: the bottom stays in view.
        messages.value = messages.value + "Neue Frage"
        lines.intValue = 40
        waitForIdle()
        assertTrue(follow.value)
        assertEquals(0, state.firstVisibleItemIndex)
        assertEquals(0, state.firstVisibleItemScrollOffset)

        // The user scrolls up: following stops and the text being read stays put while the answer grows.
        onNodeWithTag("list").performTouchInput { swipeDown() }
        waitForIdle()
        assertFalse(follow.value)
        val index = state.firstVisibleItemIndex
        val offset = state.firstVisibleItemScrollOffset
        lines.intValue = 60
        waitForIdle()
        assertFalse(follow.value)
        if (index == 0) assertTrue(state.firstVisibleItemScrollOffset > offset, "Text bleibt stehen") else assertEquals(index, state.firstVisibleItemIndex)

        // Back to the very bottom: following starts again.
        repeat(8) { onNodeWithTag("list").performTouchInput { swipeUp() } }
        waitForIdle()
        assertTrue(follow.value, "unten angekommen → folgen")
        lines.intValue = 80
        messages.value = messages.value + "Noch eine Frage"
        waitForIdle()
        assertEquals(0, state.firstVisibleItemIndex)
        assertEquals(0, state.firstVisibleItemScrollOffset)
    }
}
