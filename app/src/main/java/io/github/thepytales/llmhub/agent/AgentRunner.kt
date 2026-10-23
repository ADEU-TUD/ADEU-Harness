package io.github.thepytales.llmhub.agent

import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.data.AgentEnvironment
import io.github.thepytales.llmhub.data.ChatMessage
import io.github.thepytales.llmhub.data.Conversation
import io.github.thepytales.llmhub.data.ModelCatalog
import io.github.thepytales.llmhub.data.NetworkAccess
import io.github.thepytales.llmhub.data.ToolCall
import io.github.thepytales.llmhub.data.appendToThread
import io.github.thepytales.llmhub.data.dropFromThread
import io.github.thepytales.llmhub.data.thread
import io.github.thepytales.llmhub.data.updateMessage
import io.github.thepytales.llmhub.net.ApiException
import io.github.thepytales.llmhub.ui.splitThinking
import kotlinx.coroutines.CancellationException
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/** Whoever runs the agent: the chat screen or a background task. */
interface AgentHost {
    val conversation: Conversation?
    fun update(transform: (Conversation) -> Conversation)
    fun persist()
    fun notice(message: String)
    /** Asks whether a write action may run; background hosts decide on their own. */
    suspend fun confirm(call: ToolCall): Boolean
}

/** Tool groups for a run; without it the chat's and app's settings apply. */
data class ToolSelection(val github: Boolean, val documents: Boolean, val web: Boolean)

/** The agent loop: model → tools → model … until the model answers without tool calls. */
class AgentRunner(private val c: AppContainer) {

    fun environment(conv: Conversation): AgentEnvironment? = conv.envId?.let { c.environments.get(it) }

    /** Tool groups offered to the model in this conversation. */
    fun providers(conv: Conversation, tools: ToolSelection? = null): List<ToolProvider> {
        val settings = c.settings.current
        // Models known not to support function calling only get a plain chat.
        if (ModelCatalog.info(conv.model).tools == false) return emptyList()
        val sel = tools ?: ToolSelection(conv.githubEnabled, settings.documentTools, settings.webTools)
        val env = environment(conv)
        return listOfNotNull(
            c.githubTools.takeIf { sel.github && settings.hasGitHub },
            c.documentTools.takeIf { sel.documents },
            c.webTools.takeIf { sel.web && env?.network != NetworkAccess.NONE },
        )
    }

    suspend fun run(host: AgentHost, maxSteps: Int = c.settings.current.maxAgentSteps, tools: ToolSelection? = null) {
        val settings = c.settings.current
        val start = host.conversation ?: return
        val env = environment(start)
        val lastUser = start.thread().lastOrNull { it.role == "user" }
        val ctx = RepoContext(start.repo, start.branch, env, lastUser?.images.orEmpty())
        if ((tools?.github ?: start.githubEnabled) && !settings.hasGitHub) {
            host.notice("GitHub-Token fehlt – GitHub-Werkzeuge deaktiviert.")
        }
        if (ctx.repo != null && ctx.branch == null && settings.hasGitHub) {
            ctx.branch = runCatching { c.github.defaultBranch(ctx.repo!!) }.getOrNull()
            if (ctx.branch != null) {
                host.update { it.copy(branch = ctx.branch) }
                host.persist()
            }
        }
        var providers = providers(start, tools)
        var steps = 0
        while (true) {
            val conv = host.conversation ?: return
            val apiMessages = buildApiMessages(conv, ctx, providers)
            val placeholder = ChatMessage(role = "assistant", model = conv.model)
            host.update { it.appendToThread(placeholder) }
            val result = try {
                c.llm.streamChat(
                    model = conv.model,
                    messages = apiMessages,
                    tools = providers.flatMap { it.defs }.toSchema().takeIf { providers.isNotEmpty() },
                ) { upd ->
                    host.update { cv ->
                        cv.updateMessage(placeholder.id) {
                            it.copy(content = upd.content, reasoning = upd.reasoning.ifEmpty { null }, toolCalls = upd.toolCalls)
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val partial = host.conversation?.messages?.firstOrNull { it.id == placeholder.id }
                if (e is ApiException && e.code in 400..422 && e.message.orEmpty().contains("tool", ignoreCase = true) &&
                    providers.isNotEmpty() && partial?.content.isNullOrBlank()
                ) {
                    // The model (or its server configuration) rejects function calling: answer without tools.
                    host.update { it.dropFromThread(placeholder.id) }
                    host.notice("${ModelCatalog.info(conv.model).displayName} unterstützt hier keine Werkzeuge – Antwort ohne Werkzeuge.")
                    providers = emptyList()
                    continue
                }
                val message = e.message ?: e.toString()
                host.update { cv ->
                    if (partial == null || partial.content.isBlank()) {
                        cv.updateMessage(placeholder.id) { it.copy(content = message, reasoning = null, toolCalls = emptyList(), error = true) }
                    } else {
                        cv.appendToThread(ChatMessage(role = "assistant", content = message, error = true))
                    }
                }
                return
            }
            host.persist()
            if (result.toolCalls.isEmpty() || providers.isEmpty()) return

            for (call in result.toolCalls) {
                val provider = providers.firstOrNull { it.handles(call.name) }
                val needsConfirm = provider != null && settings.confirmWrites &&
                    provider.needsConfirmation(call.name, parseArgs(call.arguments))
                val output = when {
                    provider == null -> ToolOutput("FEHLER: Unbekanntes Werkzeug '${call.name}'.")
                    !needsConfirm || host.confirm(call) -> provider.execute(call.name, call.arguments, ctx)
                    else -> ToolOutput("Der Nutzer hat diese Aktion abgelehnt. Frage nach, wie du fortfahren sollst.")
                }
                val text = env?.mask(output.text) ?: output.text
                host.update {
                    it.appendToThread(
                        ChatMessage(role = "tool", content = text, toolCallId = call.id, toolName = call.name, file = output.file)
                    ).copy(repo = ctx.repo, branch = ctx.branch)
                }
            }
            host.persist()
            steps++
            if (steps >= maxSteps) {
                host.update {
                    it.appendToThread(
                        ChatMessage(
                            role = "assistant", error = true,
                            content = "Maximale Anzahl an Werkzeugschritten ($steps) erreicht. Schreibe \"weiter\", um fortzufahren.",
                        )
                    )
                }
                return
            }
        }
    }

    private fun buildApiMessages(conv: Conversation, ctx: RepoContext, providers: List<ToolProvider>): List<ChatMessage> {
        val settings = c.settings.current
        val useTools = providers.isNotEmpty()
        val now = DateFormat.getDateTimeInstance(DateFormat.FULL, DateFormat.SHORT, Locale.GERMANY).format(Date())
        val system = listOfNotNull(
            settings.systemPrompt.ifBlank { null },
            conv.systemPrompt?.ifBlank { null },
            ctx.env?.systemPrompt(),
            if (c.githubTools in providers) GitHubTools.systemPrompt(ctx) else null,
            if (c.documentTools in providers) c.documentTools.prompt() else null,
            if (c.webTools in providers) WebTools.PROMPT else null,
            "Aktuelles Datum: $now.",
        ).joinToString("\n\n")
        val out = mutableListOf<ChatMessage>()
        if (system.isNotBlank()) out += ChatMessage(role = "system", content = system)
        val thread = conv.thread()
        // Only keep tool calls that have a matching result, otherwise the API rejects the history.
        val answered = thread.filter { it.role == "tool" }.mapNotNull { it.toolCallId }.toSet()
        val sent = mutableSetOf<String>()
        for (m in thread) {
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
}
