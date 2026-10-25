package io.github.thepytales.llmhub.ui

import io.github.thepytales.llmhub.data.FileAttachment
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Content for the next new chat from another screen (e.g. "Transkript im Chat verwenden"): text for the input field,
 * attached files and the project. Taken by the chat screen when it opens empty.
 */
object ChatHandoff {
    data class Content(
        val text: String,
        val files: List<FileAttachment>,
        val folderId: String? = null,
        /** Pictures as data URLs (e.g. a generated image to discuss or describe). */
        val images: List<String> = emptyList(),
        /** Mode of the new chat, e.g. [io.github.thepytales.llmhub.data.Conversation.MODE_LITERATURE]. */
        val mode: String? = null,
        /** Send [text] right away instead of putting it into the input field. */
        val send: Boolean = false,
    )

    val pending = MutableStateFlow<Content?>(null)

    fun take(): Content? = pending.value.also { pending.value = null }
}
