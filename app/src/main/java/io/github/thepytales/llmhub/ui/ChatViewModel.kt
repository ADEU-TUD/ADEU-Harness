package io.github.thepytales.llmhub.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.agent.AgentHost
import io.github.thepytales.llmhub.agent.GitHubTools
import io.github.thepytales.llmhub.data.ChatMessage
import io.github.thepytales.llmhub.data.Conversation
import io.github.thepytales.llmhub.data.FileAttachment
import io.github.thepytales.llmhub.data.ModelType
import io.github.thepytales.llmhub.data.NO_CHILD
import io.github.thepytales.llmhub.data.TemplateKind
import io.github.thepytales.llmhub.data.ToolCall
import io.github.thepytales.llmhub.data.addVersion
import io.github.thepytales.llmhub.data.appendToThread
import io.github.thepytales.llmhub.data.branchAfter
import io.github.thepytales.llmhub.data.removeSubtree
import io.github.thepytales.llmhub.data.select
import io.github.thepytales.llmhub.data.thread
import io.github.thepytales.llmhub.data.updateMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

data class PendingAction(val call: ToolCall, val result: CompletableDeferred<Boolean>)

/** Creates a new, not yet saved conversation with the configured defaults. */
fun newConversation(c: AppContainer, model: String? = null): Conversation {
    val s = c.settings.current
    val wanted = model ?: s.defaultModel
    val available = c.models.state.value.liveIds
    val chosen = if (available == null || wanted in available) wanted
    else c.models.state.value.ofType(ModelType.CHAT).firstOrNull()?.id ?: wanted
    val env = s.defaultEnvId.takeIf { id -> id.isNotEmpty() && c.environments.get(id) != null }
    return Conversation(model = chosen, githubEnabled = s.hasGitHub && s.githubAutoTools, envId = env)
}

/**
 * State of one chat. A new chat ([initialId] null) only lives in memory until the first message is sent.
 * [initialModel] / [initialPersona] preset a new chat (from the model list or a template).
 */
class ChatViewModel(
    private val c: AppContainer,
    initialId: String?,
    initialModel: String? = null,
    initialPersona: String? = null,
) : ViewModel(), AgentHost {
    private val template = initialPersona?.let { c.templates.get(it) }

    /** Text for the input field of a new chat started from a prompt template. */
    val prefill: String? = template?.takeIf { it.kind == TemplateKind.PROMPT }?.content

    private val _conversation = MutableStateFlow(
        if (initialId == null) {
            newConversation(c, initialModel).let { conv ->
                template?.takeIf { it.kind == TemplateKind.PERSONA }?.let { t -> conv.copy(systemPrompt = t.content, persona = t.name) } ?: conv
            }
        } else c.chats.get(initialId)
    )
    override val conversation: Conversation? get() = _conversation.value
    val state: StateFlow<Conversation?> = _conversation.asStateFlow()

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    private val _pending = MutableStateFlow<PendingAction?>(null)
    val pending: StateFlow<PendingAction?> = _pending.asStateFlow()

    private val _transcribing = MutableStateFlow(false)
    val transcribing: StateFlow<Boolean> = _transcribing.asStateFlow()

    private val _toasts = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val toasts: SharedFlow<String> = _toasts

    /** The chat was deleted elsewhere (drawer); nothing may be saved anymore. */
    private val _deleted = MutableStateFlow(false)
    val deleted: StateFlow<Boolean> = _deleted.asStateFlow()

    private var job: Job? = null

    /** Whether this chat has been written to the repository at least once. */
    private var saved = initialId != null

    init {
        viewModelScope.launch {
            if (_conversation.value == null && initialId != null) {
                // The repository loads asynchronously at app start.
                c.chats.awaitLoaded()
                _conversation.value = c.chats.get(initialId)
            }
        }
        viewModelScope.launch {
            // Renaming, pinning or moving the chat in the drawer changes the stored copy.
            c.chats.conversations.collect { list ->
                val cur = _conversation.value ?: return@collect
                val stored = list.firstOrNull { it.id == cur.id }
                if (stored == null) {
                    if (c.chats.loaded.value && cur.messages.isNotEmpty() && saved) _deleted.value = true
                    return@collect
                }
                if (stored.title != cur.title || stored.pinned != cur.pinned || stored.folderId != cur.folderId) {
                    _conversation.value = cur.copy(title = stored.title, pinned = stored.pinned, folderId = stored.folderId)
                }
            }
        }
    }

    override fun update(transform: (Conversation) -> Conversation) {
        val cur = _conversation.value ?: return
        _conversation.value = transform(cur)
    }

    override fun persist() {
        val conv = _conversation.value ?: return
        if (_deleted.value || conv.messages.isEmpty()) return
        saved = true
        c.chats.save(conv.copy(updatedAt = System.currentTimeMillis()))
    }

    /** Saves settings changes without moving the chat to the top of the list. */
    private fun edit(transform: (Conversation) -> Conversation) {
        update(transform)
        val conv = _conversation.value ?: return
        if (!_deleted.value && conv.messages.isNotEmpty()) {
            saved = true
            c.chats.save(conv)
        }
    }

    override fun notice(message: String) {
        _toasts.tryEmit(message)
    }

    override suspend fun confirm(call: ToolCall): Boolean {
        val deferred = CompletableDeferred<Boolean>()
        _pending.value = PendingAction(call, deferred)
        return try {
            deferred.await()
        } finally {
            _pending.value = null
        }
    }

    fun answerPending(allow: Boolean) {
        _pending.value?.result?.complete(allow)
    }

    fun setModel(model: String) = edit { it.copy(model = model) }

    fun setSystemPrompt(prompt: String, persona: String? = null) =
        edit { it.copy(systemPrompt = prompt.ifBlank { null }, persona = if (prompt.isBlank()) null else persona) }

    fun setGitHub(enabled: Boolean, repo: String?, branch: String?) =
        edit { it.copy(githubEnabled = enabled, repo = repo, branch = branch) }

    fun setEnvironment(envId: String?) = edit { it.copy(envId = envId) }

    fun rename(title: String) = edit { it.copy(title = title.trim().ifBlank { it.title }) }

    fun setPinned(pinned: Boolean) = edit { it.copy(pinned = pinned) }

    fun moveToFolder(folderId: String?) = edit { it.copy(folderId = folderId) }

    fun deleteChat() {
        job?.cancel()
        _deleted.value = true
        _conversation.value?.let { c.chats.delete(it.id) }
    }

    fun send(text: String, images: List<String>, files: List<FileAttachment>) {
        val conv = _conversation.value ?: return
        if (_running.value || (text.isBlank() && images.isEmpty() && files.isEmpty())) return
        val isFirst = conv.messages.none { it.role == "user" }
        update {
            it.appendToThread(ChatMessage(role = "user", content = text.trim(), images = images, files = files)).copy(
                title = if (isFirst) makeTitle(text, files) else it.title,
            )
        }
        persist()
        connectMentionedRepo(text)
        startAgent()
    }

    /** Sends an edited version of a user message as new branch (the old version stays reachable). */
    fun editMessage(message: ChatMessage, newText: String) {
        if (_running.value || newText.isBlank()) return
        update { it.addVersion(message, message.copy(id = java.util.UUID.randomUUID().toString(), content = newText.trim(), createdAt = System.currentTimeMillis())) }
        persist()
        connectMentionedRepo(newText)
        startAgent()
    }

    /** Generates a new version of the answer to [userMessage], optionally with another model. */
    fun regenerate(userMessage: ChatMessage, model: String? = null) {
        if (_running.value) return
        update { conv -> conv.branchAfter(userMessage).let { if (model != null) it.copy(model = model) else it } }
        startAgent()
    }

    /** Shows another version of a message. */
    fun showVersion(message: ChatMessage) {
        if (_running.value) return
        edit { it.select(message) }
    }

    /** A GitHub link in the message turns the GitHub tools on and selects that repository. */
    private fun connectMentionedRepo(text: String) {
        val repo = GitHubTools.findRepoUrl(text) ?: return
        val conv = _conversation.value ?: return
        if (!c.settings.current.hasGitHub) {
            _toasts.tryEmit("Für den Zugriff auf GitHub-Repositories bitte ein GitHub-Token in den Einstellungen eintragen.")
            return
        }
        if (conv.githubEnabled && conv.repo != null) return
        if (!conv.githubEnabled) _toasts.tryEmit("GitHub-Werkzeuge aktiviert: $repo")
        edit { it.copy(githubEnabled = true, repo = it.repo ?: repo, branch = if (it.repo == null) null else it.branch) }
    }

    /** Re-runs the model after an error (drops the failed answer). */
    fun retry() {
        if (_running.value) return
        update { conv ->
            var cv = conv
            while (true) {
                val last = cv.thread().lastOrNull() ?: break
                if (last.role == "assistant" && (last.error || (last.content.isBlank() && last.toolCalls.isEmpty()))) {
                    cv = cv.removeSubtree(last.id)
                    last.parentId?.let { pid -> cv.messages.firstOrNull { it.id == pid } }?.let { cv = cv.branchAfter(it) }
                } else break
            }
            cv
        }
        _conversation.value?.thread()?.lastOrNull { it.role == "user" }?.let { connectMentionedRepo(it.content) }
        startAgent()
    }

    fun stop() {
        job?.cancel()
        _pending.value?.result?.complete(false)
    }

    private fun startAgent() {
        job = viewModelScope.launch {
            _running.value = true
            try {
                c.agent.run(this@ChatViewModel)
            } catch (e: CancellationException) {
                update { conv ->
                    val last = conv.thread().lastOrNull()
                    if (last != null && last.role == "assistant" && last.content.isBlank() && last.toolCalls.isEmpty()) {
                        conv.updateMessage(last.id) { it.copy(content = "_(abgebrochen)_", error = true) }
                    } else conv
                }
            } finally {
                // Stopped before a new version started: show the previous answer again.
                update { conv ->
                    val last = conv.thread().lastOrNull()
                    if (last != null && conv.selected[last.id] == NO_CHILD) conv.copy(selected = conv.selected - last.id) else conv
                }
                _running.value = false
                persist()
            }
        }
    }

    fun speak(id: String, text: String) = c.speaker.toggle(id, text)

    fun transcribe(file: File, onText: (String) -> Unit) {
        viewModelScope.launch {
            _transcribing.value = true
            try {
                val text = c.llm.transcribe(c.settings.current.sttModel, file)
                if (text.isBlank()) _toasts.tryEmit("Keine Sprache erkannt.") else onText(text)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _toasts.tryEmit("Spracherkennung fehlgeschlagen: ${e.message}")
            } finally {
                _transcribing.value = false
            }
        }
    }

    override fun onCleared() {
        persist()
    }

    private fun makeTitle(text: String, files: List<FileAttachment>): String {
        val t = text.trim().lineSequence().firstOrNull().orEmpty()
            .ifBlank { files.firstOrNull()?.name ?: "Bild-Chat" }
        return if (t.length > 48) t.take(48).trimEnd() + "…" else t
    }
}

/** Splits `<think>…</think>` reasoning that some models put into the content. */
fun splitThinking(content: String): Pair<String?, String> {
    val t = content.trimStart()
    if (t.startsWith("<think>")) {
        val end = t.indexOf("</think>")
        return if (end < 0) t.removePrefix("<think>").trim() to ""
        else t.substring(7, end).trim() to t.substring(end + 8).trimStart()
    }
    val end = content.indexOf("</think>")
    if (end >= 0) return content.substring(0, end).trim() to content.substring(end + 8).trimStart()
    return null to content
}
