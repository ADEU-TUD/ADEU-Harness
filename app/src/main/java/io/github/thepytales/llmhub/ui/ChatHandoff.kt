package io.github.thepytales.llmhub.ui

import io.github.thepytales.llmhub.data.FileAttachment
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Content for the next new chat from another screen (e.g. "Transkript im Chat verwenden"): text for the input field,
 * attached files and the project. Taken by the chat screen when it opens empty.
 */
object ChatHandoff {
    data class Content(val text: String, val files: List<FileAttachment>, val folderId: String? = null)

    val pending = MutableStateFlow<Content?>(null)

    fun take(): Content? = pending.value.also { pending.value = null }
}
