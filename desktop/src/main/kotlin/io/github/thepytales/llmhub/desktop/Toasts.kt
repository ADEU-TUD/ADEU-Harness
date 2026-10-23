package io.github.thepytales.llmhub.desktop

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/** Short messages for the user, shown at the bottom of the window by AppRoot. */
object Toasts {
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages

    fun show(text: String) {
        _messages.tryEmit(text)
    }
}
