package io.github.thepytales.llmhub.ui

import io.github.thepytales.llmhub.data.FileAttachment
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Content for the next new chat from another screen (e.g. "Transkript im Chat verwenden"): text for the input field,
 * attached files and the project. Taken by the chat screen when it opens empty – or, with [Content.chatId], by that
 * chat ("In Chat senden" in the document editor).
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
        /** Existing chat the content is for; null = a new chat. */
        val chatId: String? = null,
        /** Sent to the model in front of the text, not shown (e.g. what Lora already found out). */
        val instructions: String? = null,
        /** Handed over by Lora: the line on the card above the message (see [io.github.thepytales.llmhub.data.ChatMessage.handoff]). */
        val handoff: String? = null,
        /** Model the chat should use (Lora's choice); set before sending. */
        val model: String? = null,
    )

    val pending = MutableStateFlow<Content?>(null)

    /** Chat the app should open now ("" = a new chat), set by screens without their own navigation. */
    val open = MutableStateFlow<String?>(null)

    /** Content for a new chat. */
    fun take(): Content? = pending.value?.takeIf { it.chatId == null }.also { if (it != null) pending.value = null }

    /** Content for the existing chat [chatId]. */
    fun takeFor(chatId: String): Content? = pending.value?.takeIf { it.chatId == chatId }.also { if (it != null) pending.value = null }

    /** Attaches [file] in the input field of chat [chatId] (null = new chat) and opens it. */
    fun send(chatId: String?, file: FileAttachment, text: String = "") {
        pending.value = Content(text, listOf(file), chatId = chatId)
        open.value = chatId.orEmpty()
    }
}
