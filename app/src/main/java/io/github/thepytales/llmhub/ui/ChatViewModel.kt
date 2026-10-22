package io.github.thepytales.llmhub.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.agent.DocumentTools
import io.github.thepytales.llmhub.agent.GitHubTools
import io.github.thepytales.llmhub.agent.RepoContext
import io.github.thepytales.llmhub.agent.ToolOutput
import io.github.thepytales.llmhub.agent.ToolProvider
import io.github.thepytales.llmhub.agent.WebTools
import io.github.thepytales.llmhub.agent.execute
import io.github.thepytales.llmhub.agent.toSchema
import io.github.thepytales.llmhub.data.ChatMessage
import io.github.thepytales.llmhub.data.Conversation
import io.github.thepytales.llmhub.data.ModelCatalog
import io.github.thepytales.llmhub.data.ToolCall
import io.github.thepytales.llmhub.net.ApiException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

data class PendingAction(val call: ToolCall, val result: CompletableDeferred<Boolean>)

class ChatViewModel(private val c: AppContainer, private val id: String) : ViewModel() {
    private val _conversation = MutableStateFlow(c.chats.get(id))
    val conversation: StateFlow<Conversation?> = _conversation.asStateFlow()

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    private val _pending = MutableStateFlow<PendingAction?>(null)
    val pending: StateFlow<PendingAction?> = _pending.asStateFlow()

    private val _transcribing = MutableStateFlow(false)
    val transcribing: StateFlow<Boolean> = _transcribing.asStateFlow()

    private val _toasts = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val toasts: SharedFlow<String> = _toasts

    private var job: Job? = null

    init {
        if (_conversation.value == null) {
            viewModelScope.launch {
                // The repository loads asynchronously at app start.
                withTimeoutOrNull(5000) { c.chats.conversations.first { list -> list.any { it.id == id } } }
                _conversation.value = c.chats.get(id)
            }
        }
    }

    private fun edit(persist: Boolean = false, transform: (Conversation) -> Conversation) {
        val cur = _conversation.value ?: return
        val next = transform(cur)
        _conversation.value = next
        if (persist) c.chats.save(next)
    }

    private fun persist() {
        _conversation.value?.let { c.chats.save(it.copy(updatedAt = System.currentTimeMillis())) }
    }

    private fun editMessage(messageId: String, transform: (ChatMessage) -> ChatMessage) = edit { conv ->
        conv.copy(messages = conv.messages.map { if (it.id == messageId) transform(it) else it })
    }

    private fun append(message: ChatMessage) = edit { it.copy(messages = it.messages + message) }

    fun setModel(model: String) = edit(persist = true) { it.copy(model = model) }

    fun setSystemPrompt(prompt: String) = edit(persist = true) { it.copy(systemPrompt = prompt.ifBlank { null }) }

    fun setGitHub(enabled: Boolean, repo: String?, branch: String?) = edit(persist = true) {
        it.copy(githubEnabled = enabled, repo = repo, branch = branch)
    }

    fun deleteChat() {
        job?.cancel()
        c.chats.delete(id)
    }

    fun send(text: String, images: List<String>) {
        val conv = _conversation.value ?: return
        if (_running.value || (text.isBlank() && images.isEmpty())) return
        val isFirst = conv.messages.none { it.role == "user" }
        edit(persist = true) {
            it.copy(
                messages = it.messages + ChatMessage(role = "user", content = text.trim(), images = images),
                title = if (isFirst) makeTitle(text) else it.title,
                updatedAt = System.currentTimeMillis(),
            )
        }
        connectMentionedRepo(text)
        startAgent()
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
        edit(persist = true) { it.copy(githubEnabled = true, repo = it.repo ?: repo, branch = if (it.repo == null) null else it.branch) }
    }

    /** Re-runs the model on the current history (e.g. after an error). */
    fun retry() {
        if (_running.value) return
        edit { conv ->
            val trimmed = conv.messages.dropLastWhile { it.role == "assistant" && (it.error || it.toolCalls.isEmpty()) }
            conv.copy(messages = trimmed)
        }
        _conversation.value?.messages?.lastOrNull { it.role == "user" }?.let { connectMentionedRepo(it.content) }
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
                runAgent()
            } catch (e: CancellationException) {
                edit { conv ->
                    val last = conv.messages.lastOrNull()
                    if (last != null && last.role == "assistant" && last.content.isBlank() && last.toolCalls.isEmpty()) {
                        conv.copy(messages = conv.messages.dropLast(1) + last.copy(content = "_(abgebrochen)_", error = true))
                    } else conv
                }
            } finally {
                _running.value = false
                persist()
            }
        }
    }

    /** Tool groups offered to the model in this conversation. */
    private fun toolProviders(conv: Conversation): List<ToolProvider> {
        val settings = c.settings.current
        // Models known not to support function calling only get a plain chat.
        if (ModelCatalog.info(conv.model).tools == false) return emptyList()
        return listOfNotNull(
            c.githubTools.takeIf { conv.githubEnabled && settings.hasGitHub },
            c.documentTools.takeIf { settings.documentTools },
            c.webTools.takeIf { settings.webTools },
        )
    }

    private suspend fun runAgent() {
        val settings = c.settings.current
        val start = _conversation.value ?: return
        val ctx = RepoContext(start.repo, start.branch)
        if (start.githubEnabled && !settings.hasGitHub) {
            _toasts.tryEmit("GitHub-Token fehlt – GitHub-Werkzeuge deaktiviert.")
        }
        if (ctx.repo != null && ctx.branch == null && settings.hasGitHub) {
            ctx.branch = runCatching { c.github.defaultBranch(ctx.repo!!) }.getOrNull()
            if (ctx.branch != null) edit(persist = true) { it.copy(branch = ctx.branch) }
        }
        var providers = toolProviders(start)
        var steps = 0
        while (true) {
            val conv = _conversation.value ?: return
            val apiMessages = buildApiMessages(conv, ctx, providers)
            val placeholder = ChatMessage(role = "assistant", model = conv.model)
            append(placeholder)
            val result = try {
                c.llm.streamChat(
                    model = conv.model,
                    messages = apiMessages,
                    tools = providers.flatMap { it.defs }.toSchema().takeIf { providers.isNotEmpty() },
                ) { upd ->
                    editMessage(placeholder.id) {
                        it.copy(content = upd.content, reasoning = upd.reasoning.ifEmpty { null }, toolCalls = upd.toolCalls)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val partial = _conversation.value?.messages?.firstOrNull { it.id == placeholder.id }
                if (e is ApiException && e.code in 400..422 && e.message.orEmpty().contains("tool", ignoreCase = true) &&
                    providers.isNotEmpty() && partial?.content.isNullOrBlank()
                ) {
                    // The model (or its server configuration) rejects function calling: answer without tools.
                    edit { cv -> cv.copy(messages = cv.messages.filterNot { it.id == placeholder.id }) }
                    _toasts.tryEmit("${ModelCatalog.info(conv.model).displayName} unterstützt hier keine Werkzeuge – Antwort ohne Werkzeuge.")
                    providers = emptyList()
                    continue
                }
                edit { cv ->
                    val msgs = cv.messages.filterNot { it.id == placeholder.id && it.content.isBlank() }
                    cv.copy(messages = msgs + ChatMessage(role = "assistant", content = e.message ?: e.toString(), error = true))
                }
                return
            }
            persist()
            if (result.toolCalls.isEmpty() || providers.isEmpty()) return

            for (call in result.toolCalls) {
                val provider = providers.firstOrNull { it.handles(call.name) }
                val needsConfirm = provider === c.githubTools && settings.confirmWrites && c.githubTools.isWriteTool(call.name)
                val output = when {
                    provider == null -> ToolOutput("FEHLER: Unbekanntes Werkzeug '${call.name}'.")
                    !needsConfirm || confirm(call) -> provider.execute(call.name, call.arguments, ctx)
                    else -> ToolOutput(
                        "Der Nutzer hat diese Aktion abgelehnt. Frage nach, wie du fortfahren sollst."
                    )
                }
                append(
                    ChatMessage(
                        role = "tool", content = output.text, toolCallId = call.id, toolName = call.name, file = output.file,
                    )
                )
                edit { it.copy(repo = ctx.repo, branch = ctx.branch) }
            }
            persist()
            steps++
            if (steps >= settings.maxAgentSteps) {
                append(
                    ChatMessage(
                        role = "assistant", error = true,
                        content = "Maximale Anzahl an Werkzeugschritten ($steps) erreicht. Schreibe \"weiter\", um fortzufahren.",
                    )
                )
                return
            }
        }
    }

    private suspend fun confirm(call: ToolCall): Boolean {
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

    private fun buildApiMessages(conv: Conversation, ctx: RepoContext, providers: List<ToolProvider>): List<ChatMessage> {
        val settings = c.settings.current
        val useTools = providers.isNotEmpty()
        val system = listOfNotNull(
            settings.systemPrompt.ifBlank { null },
            conv.systemPrompt?.ifBlank { null },
            if (c.githubTools in providers) GitHubTools.systemPrompt(ctx) else null,
            if (c.documentTools in providers) DocumentTools.PROMPT else null,
            if (c.webTools in providers) WebTools.PROMPT else null,
        ).joinToString("\n\n")
        val out = mutableListOf<ChatMessage>()
        if (system.isNotBlank()) out += ChatMessage(role = "system", content = system)
        // Only keep tool calls that have a matching result, otherwise the API rejects the history.
        val answered = conv.messages.filter { it.role == "tool" }.mapNotNull { it.toolCallId }.toSet()
        val sent = mutableSetOf<String>()
        for (m in conv.messages) {
            when (m.role) {
                "assistant" -> {
                    if (m.error) continue
                    val calls = if (useTools) m.toolCalls.filter { it.id in answered } else emptyList()
                    val content = splitThinking(m.content).second
                    if (content.isBlank() && calls.isEmpty()) continue
                    sent += calls.map { it.id }
                    out += m.copy(content = content, toolCalls = calls)
                }
                "tool" -> {
                    if (useTools && m.toolCallId in sent) out += m
                }
                else -> out += m
            }
        }
        return out
    }

    fun speak(message: ChatMessage) {
        if (c.player.playingId.value == message.id) {
            c.player.stop()
            return
        }
        viewModelScope.launch {
            try {
                val s = c.settings.current
                val text = plainText(splitThinking(message.content).second).take(4000)
                val audio = c.llm.speech(s.ttsModel, s.ttsVoice, text)
                c.player.play(message.id, audio)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _toasts.tryEmit("Vorlesen fehlgeschlagen: ${e.message}")
            }
        }
    }

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

    private fun makeTitle(text: String): String {
        val t = text.trim().lineSequence().firstOrNull().orEmpty().ifBlank { "Bild-Chat" }
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

/** Rough markdown stripping for speech output. */
fun plainText(md: String): String = md
    .replace(Regex("```[\\s\\S]*?```"), " Codeblock. ")
    .replace(Regex("[*_`#>|]"), "")
    .replace(Regex("\\[([^\\]]+)]\\([^)]+\\)"), "$1")
    .trim()
