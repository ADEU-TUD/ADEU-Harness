package io.github.thepytales.llmhub.data

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class ToolCall(
    val id: String,
    val name: String,
    val arguments: String,
)

/** Text extracted from a file the user attached (PDF, Word, text …). */
@Serializable
data class FileAttachment(
    val name: String,
    val mime: String,
    val text: String,
    val pages: Int? = null,
    /** The text was cut to the maximum length. */
    val truncated: Boolean = false,
    /** Copy of an attached PDF, kept so the agent can fill it in (see docs/PdfForms). */
    val path: String? = null,
    /** Number of fillable form fields of that PDF. */
    val formFields: Int = 0,
)

@Serializable
data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: String,
    val content: String = "",
    val reasoning: String? = null,
    /** Attached images as data URLs (data:image/jpeg;base64,...). */
    val images: List<String> = emptyList(),
    /** Attached documents whose text is sent along with the message. */
    val files: List<FileAttachment> = emptyList(),
    val toolCalls: List<ToolCall> = emptyList(),
    val toolCallId: String? = null,
    val toolName: String? = null,
    val model: String? = null,
    val error: Boolean = false,
    /** Absolute path of a document created by a tool call (tool messages only). */
    val file: String? = null,
    /** Name of the template the user message was written with (shown above the bubble). */
    val template: String? = null,
    /** Instructions of that template: sent to the model in front of the text, not shown in the chat. */
    val instructions: String? = null,
    /** Previous message in the thread; messages with the same parent are alternative versions. */
    val parentId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

@Serializable
data class Conversation(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "Neuer Chat",
    val model: String,
    /** All messages of all versions; see ChatTree.kt for the active thread. */
    val messages: List<ChatMessage> = emptyList(),
    /** Chosen version per parent message (key "" = first message). */
    val selected: Map<String, String> = emptyMap(),
    val systemPrompt: String? = null,
    /** Name of the template the system prompt came from. */
    val persona: String? = null,
    /** Whether the GitHub tools are offered to the model. */
    val githubEnabled: Boolean = false,
    /** Selected repository as owner/name. */
    val repo: String? = null,
    /** Working branch inside [repo]. */
    val branch: String? = null,
    /** Agent environment (variables, network access). */
    val envId: String? = null,
    /** Write actions in this chat run without asking ("In diesem Chat nicht mehr fragen"). */
    val autoApprove: Boolean = false,
    val pinned: Boolean = false,
    val folderId: String? = null,
    /** Scheduled task that created this chat. */
    val taskId: String? = null,
    /** Has a result the user has not looked at yet (scheduled task, answer finished in the background). */
    val unread: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis(),
)
