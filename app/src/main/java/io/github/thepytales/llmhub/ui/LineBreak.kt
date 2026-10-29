package io.github.thepytales.llmhub.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/**
 * Text field value with cursor for a field whose state is a plain String, so Umschalt+Enter can put the line break
 * at the cursor (Compose Desktop inserts none itself while Umschalt is held). Follows [text] when it changes elsewhere.
 */
@Composable
fun rememberCursorValue(text: String): MutableState<TextFieldValue> {
    val state = remember { mutableStateOf(TextFieldValue(text, TextRange(text.length))) }
    if (state.value.text != text) state.value = TextFieldValue(text, TextRange(text.length))
    return state
}

/** The value with a line break in place of the selection. */
fun TextFieldValue.withLineBreak(): TextFieldValue {
    val start = selection.min
    return TextFieldValue(text.substring(0, start) + "\n" + text.substring(selection.max), TextRange(start + 1))
}
