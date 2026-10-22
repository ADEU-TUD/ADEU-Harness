package io.github.thepytales.llmhub.data

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class ToolCall(
    val id: String,
    val name: String,
    val arguments: String,
)

@Serializable
data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: String,
    val content: String = "",
    val reasoning: String? = null,
    /** Attached images as data URLs (data:image/jpeg;base64,...). */
    val images: List<String> = emptyList(),
    val toolCalls: List<ToolCall> = emptyList(),
    val toolCallId: String? = null,
    val toolName: String? = null,
    val model: String? = null,
    val error: Boolean = false,
    /** Absolute path of a document created by a tool call (tool messages only). */
    val file: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

@Serializable
data class Conversation(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "Neuer Chat",
    val model: String,
    val messages: List<ChatMessage> = emptyList(),
    val systemPrompt: String? = null,
    /** Whether the GitHub tools are offered to the model. */
    val githubEnabled: Boolean = false,
    /** Selected repository as owner/name. */
    val repo: String? = null,
    /** Working branch inside [repo]. */
    val branch: String? = null,
    val updatedAt: Long = System.currentTimeMillis(),
)
