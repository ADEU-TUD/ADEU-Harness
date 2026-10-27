package io.github.thepytales.llmhub.data

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class ToolCall(
    val id: String,
    val name: String,
    val arguments: String,
    /** Provider data that has to be sent back with the call (Gemini's thought signature), as JSON. */
    val extra: String? = null,
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
    /** Agent protocol: how long the model request or the tool call took. */
    val durationMs: Long? = null,
    /** Agent protocol: tokens of the request (prompt) and the answer, as reported or estimated. */
    val tokensIn: Long? = null,
    val tokensOut: Long? = null,
    /** Agent protocol: what happened on the way (retries, fallback model, shortened history …). */
    val events: List<String> = emptyList(),
    /** The user's rating of an answer: 1 helpful, -1 not helpful (only on this device, see Verbrauch). */
    val rating: Int? = null,
    /** Why the answer was not helpful (optional). */
    val ratingNote: String? = null,
    /** The answer stopped at the model's length limit; "Weiterschreiben" continues it. */
    val truncated: Boolean = false,
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
    /**
     * First linked repository as owner/name – chats from before several repositories only have this one.
     * Kept in step with [repos] by [withRepos]; read the repositories through [linkedRepos].
     */
    val repo: String? = null,
    /** Working branch inside [repo]. */
    val branch: String? = null,
    /** Repositories linked to the chat (can belong to different accounts and organizations). */
    val repos: List<LinkedRepo> = emptyList(),
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
    /** Summary of the older part of a long chat, sent instead of those messages (see agent/ContextManager). */
    val summary: String? = null,
    /** Last message covered by [summary]; only used while it is part of the active thread. */
    val summaryUpTo: String? = null,
    /** Special mode of the chat: "literature" (literature search); null = normal chat. */
    val mode: String? = null,
    /** Temporary chat: never saved, gone when it is closed. */
    val temporary: Boolean = false,
    /** Archived: hidden from the chat list, kept under „Archiv“. */
    val archived: Boolean = false,
    /** Automatic model choice: before each request the app suggests the fitting model (see agent/ModelRouter). */
    val autoModel: Boolean = false,
    /** The memory is switched off for this chat (chip „Gedächtnis“ above the input field). */
    val memoryOff: Boolean = false,
    /** Last message the memory learner looked at (see agent/MemoryLearner), so nothing is suggested twice. */
    val learnedUpTo: String? = null,
    val updatedAt: Long = System.currentTimeMillis(),
) {
    /** The linked repositories, also for chats that only stored [repo]. */
    val linkedRepos: List<LinkedRepo>
        get() = repos.ifEmpty { listOfNotNull(repo?.let { LinkedRepo(it, branch) }) }

    companion object {
        const val MODE_LITERATURE = "literature"
    }

    fun withRepos(list: List<LinkedRepo>): Conversation {
        val distinct = list.distinctBy { it.name.lowercase() }
        return copy(repos = distinct, repo = distinct.firstOrNull()?.name, branch = distinct.firstOrNull()?.branch)
    }
}

/** A repository linked to a chat and the branch the agent works on there (null = not looked up yet). */
@Serializable
data class LinkedRepo(val name: String, val branch: String? = null)

/** "ADEU-TUD/Test · main" or "3 Repositorys" – for the chip above the input field. */
fun List<LinkedRepo>.label(): String? = when (size) {
    0 -> null
    1 -> first().name + (first().branch?.let { " · $it" } ?: "")
    else -> "$size Repositorys"
}
