package io.github.thepytales.llmhub.agent

import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.data.Privacy
import io.github.thepytales.llmhub.data.AgentEnvironment
import io.github.thepytales.llmhub.data.ChatMessage
import io.github.thepytales.llmhub.data.Conversation
import io.github.thepytales.llmhub.data.ModelCatalog
import io.github.thepytales.llmhub.data.NetworkAccess
import io.github.thepytales.llmhub.data.PiiDetector
import io.github.thepytales.llmhub.data.PrivacyScope
import io.github.thepytales.llmhub.data.ToolCall
import io.github.thepytales.llmhub.data.appendToThread
import io.github.thepytales.llmhub.data.dropFromThread
import io.github.thepytales.llmhub.data.thread
import io.github.thepytales.llmhub.data.updateMessage
import io.github.thepytales.llmhub.docs.DocsLive
import io.github.thepytales.llmhub.docs.DocumentWriter
import io.github.thepytales.llmhub.net.ApiException
import io.github.thepytales.llmhub.net.NetErrors
import io.github.thepytales.llmhub.net.ThinkingLevel
import io.github.thepytales.llmhub.ui.splitThinking
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.asContextElement
import kotlin.coroutines.coroutineContext
import java.io.IOException
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/** Whoever runs the agent: the chat screen or a background task. */
interface AgentHost {
    val conversation: Conversation?
    fun update(transform: (Conversation) -> Conversation)
    fun persist()
    fun notice(message: String)
    /** Whether a write action may run: chats apply the approval mode or ask, background hosts decide on their own. */
    suspend fun confirm(call: ToolCall, risk: Risk): Boolean
}

/** Tool groups for a run; without it the chat's and app's settings apply. */
data class ToolSelection(val github: Boolean, val documents: Boolean, val web: Boolean)

/** The agent loop: model → tools → model … until the model answers without tool calls. */
class AgentRunner(private val c: AppContainer) {
    private val askUser = AskUserTool()
    private val excerptTools = ExcerptTools(c)

    fun environment(conv: Conversation): AgentEnvironment? = conv.envId?.let { c.environments.get(it) }

    /** Tool groups offered to the model in this conversation (fewer in projects that keep data on the device). */
    fun providers(conv: Conversation, tools: ToolSelection? = null): List<ToolProvider> {
        val settings = c.settings.current
        // Models known not to support function calling only get a plain chat.
        if (ModelCatalog.info(conv.model).tools == false) return emptyList()
        val sel = tools ?: ToolSelection(conv.githubEnabled, settings.documentTools, settings.webTools)
        val env = environment(conv)
        // In the vault (AP-10) nothing goes to the internet: no web search, no GitHub.
        val network = Privacy.level(c, conv).allowsNetworkTools && !conv.vault
        // A mode (AP-05) brings the tool groups it needs.
        val mode = ChatModes.byId(conv.mode)?.tools.orEmpty()
        return listOfNotNull(
            c.githubTools.takeIf { sel.github && settings.hasGitHub && network },
            c.documentTools.takeIf { sel.documents || ModeTool.DOCUMENTS in mode },
            c.webTools.takeIf { (sel.web || ModeTool.WEB in mode) && env?.network != NetworkAccess.NONE && network },
            c.researchTools.takeIf { sel.web || ModeTool.RESEARCH in mode },
            c.localFolderTools.takeIf {
                io.github.thepytales.llmhub.files.LocalText.SUPPORTED && (c.localFolders.current.isNotEmpty() || WorkFolder.of(c, conv.folderId) != null)
            },
            c.projectFolderTools.takeIf { it.active(conv.folderId) },
            c.assistantTools,
            // Rückfragen mit Antwortknöpfen (AP-06): only in chats, where someone can answer.
            askUser.takeIf { conv.taskId == null },
            // Exzerpt-Agent (AP-09): save_excerpt checks the pages and attaches the excerpt to the literature entry.
            excerptTools.takeIf { conv.mode == ChatModes.EXCERPT.id },
        )
    }

    /**
     * [maxSteps] tool rounds make one block; with [autoContinue] the agent starts the next block on its own
     * (up to [HARD_STEP_LIMIT]) instead of waiting for "weiter". It stops when it repeats the same tool calls.
     */
    suspend fun run(
        host: AgentHost,
        maxSteps: Int = c.settings.current.maxAgentSteps,
        tools: ToolSelection? = null,
        autoContinue: Boolean = c.settings.current.autoContinue,
    ) {
        // All requests of this run belong to the chat (own placeholders with „Platzhalter je Chat“).
        if (coroutineContext[PrivacyScope] == null) {
            val id = host.conversation?.id ?: return
            // Vault chats (AP-10): strict pseudonymization for every request of this run.
            val strict = if (host.conversation?.vault == true) io.github.thepytales.llmhub.data.Pseudonymizer.STRICT.asContextElement(true) else kotlin.coroutines.EmptyCoroutineContext
            return withContext(PrivacyScope(id) + strict) { run(host, maxSteps, tools, autoContinue) }
        }
        val settings = c.settings.current
        val start = host.conversation ?: return
        val voice = start.thread().lastOrNull { it.role == "user" }?.voice == true
        /** Sprachmodus: the quick voice model, unless the project's privacy level does not allow it. */
        val startModel = settings.voiceModel.takeIf { voice && it.isNotBlank() && Privacy.blockReason(c, start, it) == null } ?: start.model
        Privacy.blockReason(c, start, startModel)?.let { reason ->
            stopWith(host, reason)
            return
        }
        val env = environment(start)
        val lastUser = start.thread().lastOrNull { it.role == "user" }
        val pdfs = start.thread().flatMap { it.files }.filter { it.path != null }.asReversed()
        val attached = start.thread().flatMap { it.files }.asReversed()
        val ctx = RepoContext(start.linkedRepos.toMutableList(), env, lastUser?.images.orEmpty(), pdfs, attached, start.folderId, start.id, startModel)
        if ((tools?.github ?: start.githubEnabled) && !settings.hasGitHub) {
            host.notice("GitHub-Token fehlt oder ist ungültig – GitHub-Werkzeuge deaktiviert.")
        }
        val withoutBranch = ctx.linked().filter { it.branch == null }
        if (withoutBranch.isNotEmpty() && settings.hasGitHub) {
            withoutBranch.forEach { r -> runCatching { c.github.defaultBranch(r.name) }.getOrNull()?.let { ctx.put(r.copy(branch = it)) } }
            if (ctx.linked() != start.linkedRepos) {
                host.update { it.withRepos(ctx.linked()) }
                host.persist()
            }
        }
        var privacyEvent = privacyNotice(host, start, lastUser)
        // Agent protocol: how much of the memory went along (once per answer).
        var memoryEvent = Memory.entriesFor(c, start).size.takeIf { it > 0 }?.let { n ->
            "Gedächtnis: " + (if (n == 1) "1 Angabe" else "$n Angaben") + if (Memory.chatsAllowed(c, start)) ", frühere Chats durchsuchbar" else ""
        }
        var providers = providers(start, tools)
        var steps = 0
        var totalSteps = 0
        val recentRounds = ArrayDeque<String>()
        /** Model of this run; changes once to a fallback model when the chosen one is unreachable. */
        var model = startModel
        var fallbackUsed = false
        var summarized = false
        while (true) {
            if (!summarized) summarized = compactIfNeeded(host, model, providers)
            val conv = host.conversation ?: return
            val apiMessages = buildApiMessages(conv, ctx, providers, model)
            val events = mutableListOf<String>()
            privacyEvent?.let { events += it }
            privacyEvent = null
            memoryEvent?.let { events += it }
            memoryEvent = null
            val placeholder = ChatMessage(role = "assistant", model = model)
            host.update { it.appendToThread(placeholder) }
            val started = System.currentTimeMillis()
            val result = try {
                c.llm.streamChat(
                    model = model,
                    messages = apiMessages,
                    thinking = if (voice) ThinkingLevel.OFF else null,
                    tools = providers.flatMap { it.defs }.toSchema().takeIf { providers.isNotEmpty() },
                    onRetry = { reason, wait ->
                        events += "$reason – $wait s gewartet"
                        host.notice("$reason – neuer Versuch in $wait s.")
                    },
                ) { upd ->
                    host.update { cv ->
                        cv.updateMessage(placeholder.id) {
                            it.copy(content = upd.content, reasoning = upd.reasoning.ifEmpty { null }, toolCalls = upd.toolCalls)
                        }
                    }
                    // A document being written shows up in the editor while it grows (as in Claude Docs).
                    upd.toolCalls.lastOrNull { it.name == "create_document" }?.let { call ->
                        val (title, content) = DocsLive.partialArgs(call.arguments)
                        if (content.isNotEmpty() && !DocumentWriter.isHtml(content)) DocsLive.update(start.id, DocsLive.Draft(title, content))
                    }
                }.also { DocsLive.update(start.id, null) }
            } catch (e: CancellationException) {
                DocsLive.update(start.id, null)
                throw e
            } catch (e: Exception) {
                DocsLive.update(start.id, null)
                val partial = host.conversation?.messages?.firstOrNull { it.id == placeholder.id }
                if (e is ApiException && e.code in 400..422 && e.message.orEmpty().contains("tool", ignoreCase = true) &&
                    providers.isNotEmpty() && partial?.content.isNullOrBlank()
                ) {
                    // The model (or its server configuration) rejects function calling: answer without tools.
                    host.update { it.dropFromThread(placeholder.id) }
                    host.notice("${ModelCatalog.info(model).displayName} unterstützt hier keine Werkzeuge – Antwort ohne Werkzeuge.")
                    providers = emptyList()
                    continue
                }
                val fallback = if (!fallbackUsed && unreachable(e) && partial?.content.isNullOrBlank()) {
                    fallbackModel(model, conv, providers.isNotEmpty())?.takeIf { Privacy.allows(c, Privacy.level(c, conv), it) }
                } else null
                if (fallback != null) {
                    // The gateway does not reach the model: the same request goes to a similar model.
                    fallbackUsed = true
                    host.update { it.dropFromThread(placeholder.id) }
                    val text = "${ModelCatalog.info(model).displayName} ist gerade nicht erreichbar – ${ModelCatalog.info(fallback).displayName} antwortet."
                    host.notice(text)
                    model = fallback
                    continue
                }
                // Server-side trouble: the status page tells whether the model itself is disrupted.
                val serverSide = e is ApiException && e.code >= 500 || e is java.net.SocketTimeoutException
                val message = NetErrors.describe(e) + (if (serverSide) c.llm.status.explain(model)?.let { "\n\n$it" }.orEmpty() else "")
                host.update { cv ->
                    if (partial == null || partial.content.isBlank()) {
                        cv.updateMessage(placeholder.id) { it.copy(content = message, reasoning = null, toolCalls = emptyList(), error = true) }
                    } else {
                        cv.appendToThread(ChatMessage(role = "assistant", content = message, error = true))
                    }
                }
                return
            }
            val elapsed = System.currentTimeMillis() - started
            host.update { cv ->
                cv.updateMessage(placeholder.id) {
                    it.copy(
                        durationMs = elapsed,
                        tokensIn = result.usage?.prompt ?: ContextManager.tokens(apiMessages).toLong(),
                        tokensOut = result.usage?.completion ?: ((result.content.length + result.reasoning.length + result.toolCalls.sumOf { tc -> tc.arguments.length }) / 4L),
                        events = (if (fallbackUsed && model != startModel) listOf("Ausweichmodell: ${ModelCatalog.info(model).displayName}") else emptyList()) + events,
                        truncated = result.finishReason == "length" && result.toolCalls.isEmpty(),
                    )
                }
            }
            host.persist()
            if (result.toolCalls.isEmpty() || providers.isEmpty()) return

            runTools(host, result.toolCalls, providers, ctx)
            host.persist()
            // After a question to the user the turn ends: the answers come as the next message (AP-06).
            if (result.toolCalls.any { it.name == AskUserTool.NAME }) return
            steps++
            totalSteps++
            // The same tool calls several rounds in a row mean the model is stuck; continuing only burns requests.
            recentRounds.addLast(result.toolCalls.joinToString("\n") { it.name + " " + it.arguments.trim() })
            if (recentRounds.size > REPEAT_LIMIT) recentRounds.removeFirst()
            if (recentRounds.size == REPEAT_LIMIT && recentRounds.distinct().size == 1) {
                stopWith(host, "Der Agent hat ${REPEAT_LIMIT}-mal hintereinander dieselbe Aktion ausgeführt und wurde angehalten. Schreibe \"weiter\" oder beschreibe, wie es weitergehen soll.")
                return
            }
            if (steps >= maxSteps) {
                if (autoContinue && totalSteps < HARD_STEP_LIMIT) {
                    host.notice("$totalSteps Werkzeugschritte – der Agent macht automatisch weiter.")
                    steps = 0
                    continue
                }
                stopWith(host, "Maximale Anzahl an Werkzeugschritten ($totalSteps) erreicht. Schreibe \"weiter\", um fortzufahren.")
                return
            }
        }
    }

    /**
     * Runs the tool calls of one answer. Consecutive calls that only read (web searches, reading files …) run at
     * the same time; everything that changes something runs alone and in order, after the user's OK if needed.
     */
    private suspend fun runTools(host: AgentHost, calls: List<ToolCall>, providers: List<ToolProvider>, ctx: RepoContext) {
        fun providerOf(call: ToolCall) = providers.firstOrNull { it.handles(call.name) }
        fun parallel(call: ToolCall): Boolean {
            val p = providerOf(call) ?: return false
            return p.parallelSafe(call.name) && p.risk(call.name, parseArgs(call.arguments), ctx) == null
        }
        suspend fun one(call: ToolCall): Pair<ToolOutput, Long> {
            val started = System.currentTimeMillis()
            val provider = providerOf(call)
            val risk = provider?.risk(call.name, parseArgs(call.arguments), ctx)
            val output = when {
                provider == null -> {
                    val known = providers.flatMap { p -> p.defs.map { it.name } }
                    val hint = ArgRepair.closest(call.name, known)?.let { " Meintest du „$it“?" }.orEmpty()
                    ToolOutput("FEHLER: Unbekanntes Werkzeug '${call.name}'.$hint")
                }
                risk == null || host.confirm(call, risk) -> provider.execute(call.name, call.arguments, ctx)
                else -> ToolOutput("Der Nutzer hat diese Aktion abgelehnt. Frage nach, wie du fortfahren sollst.")
            }
            return toWorkFolder(output, ctx) to System.currentTimeMillis() - started
        }
        fun record(call: ToolCall, output: ToolOutput, ms: Long) {
            val text = ctx.env?.mask(output.text) ?: output.text
            host.update {
                it.appendToThread(
                    ChatMessage(role = "tool", content = text, toolCallId = call.id, toolName = call.name, file = output.file, durationMs = ms)
                ).withRepos(ctx.linked())
            }
        }
        var i = 0
        while (i < calls.size) {
            val batch = calls.drop(i).takeWhile { parallel(it) }
            if (batch.size > 1) {
                val gate = Semaphore(PARALLEL_TOOLS)
                val results = coroutineScope { batch.map { call -> async { gate.withPermit { one(call) } } }.awaitAll() }
                batch.zip(results).forEach { (call, r) -> record(call, r.first, r.second) }
                i += batch.size
            } else {
                val call = calls[i]
                val (output, ms) = one(call)
                record(call, output, ms)
                i++
            }
        }
    }

    /**
     * Pseudonymization on: the protocol shows what was replaced. Off: a hint when personal details of the message
     * would leave the university, i.e. go to a further provider such as Google Gemini.
     */
    private fun privacyNotice(host: AgentHost, conv: Conversation, lastUser: ChatMessage?): String? {
        lastUser ?: return null
        val text = (listOf(lastUser.content) + lastUser.files.map { it.text }).joinToString("\n")
        val settings = c.settings.current
        if (settings.pseudonymize) {
            val masked = c.privacy.mask(lastUser.content, conv.id)
            val summary = c.privacy.summary(c.privacy.mask(text, conv.id)) ?: return null
            // The protocol shows what the server got, so everyone can check the replacements.
            return "Pseudonymisiert gesendet: $summary" + if (masked != lastUser.content) "\nSo sah der Server die Nachricht: " + masked.take(600).let { if (masked.length > 600) "$it …" else it } else ""
        }
        val provider = c.llm.providerFor(conv.model) ?: return null
        val terms = settings.pseudonymTerms.lines().map { it.trim() }.filter { it.length >= 2 }
        if (PiiDetector.find(text, terms).isEmpty()) return null
        host.notice(
            "Die Nachricht enthält personenbezogene Angaben (Namen, Kontaktdaten …) und geht an ${provider.name}, also außerhalb " +
                "der TU Dresden. Für solche Daten ein ScaDS.AI-Modell wählen oder unter Einstellungen › Datenschutz die " +
                "Pseudonymisierung einschalten."
        )
        return null
    }

    /** Errors after which another model may well answer: the gateway does not reach this one. */
    private fun unreachable(e: Exception): Boolean = when (e) {
        is ApiException -> e.code in setOf(502, 503, 504, 520, 521, 522, 523, 524, 529) ||
            e.code == 404 && e.message.orEmpty().contains("model", ignoreCase = true)
        is java.net.UnknownHostException, is javax.net.ssl.SSLException -> false
        is IOException -> true
        else -> false
    }

    private fun fallbackModel(model: String, conv: Conversation, needsTools: Boolean): String? {
        val setting = c.settings.current.fallbackModel.trim()
        if (setting.equals("off", ignoreCase = true)) return null
        if (setting.isNotEmpty() && !setting.equals("auto", ignoreCase = true)) return setting.takeIf { it != model }
        val vision = conv.thread().any { it.images.isNotEmpty() }
        return ModelCatalog.fallbackFor(model, vision, needsTools, c.models.state.value.liveIds?.toSet())
    }

    private fun stopWith(host: AgentHost, message: String) {
        host.update { it.appendToThread(ChatMessage(role = "assistant", error = true, content = message)) }
        host.persist()
    }

    /**
     * When the history no longer fits the budget of [model], its older part is summarized once by the model and
     * the summary is stored on the chat; the last turns stay complete. Returns whether a summary was made.
     */
    private suspend fun compactIfNeeded(host: AgentHost, model: String, providers: List<ToolProvider>): Boolean {
        val conv = host.conversation ?: return false
        val budget = ContextManager.budget(model)
        val ctx = RepoContext(conv.linkedRepos.toMutableList(), environment(conv))
        if (ContextManager.tokens(buildApiMessages(conv, ctx, providers, model)) <= budget) return false
        val thread = conv.thread()
        val cut = ContextManager.cutIndex(thread)
        if (cut <= 0) return false
        val coveredFrom = conv.summaryUpTo?.let { id -> thread.indexOfFirst { it.id == id } + 1 }?.coerceAtLeast(0) ?: 0
        if (cut <= coveredFrom) return false
        val older = thread.subList(coveredFrom, cut)
        val previous = conv.summary?.takeIf { coveredFrom > 0 }
        val transcript = ContextManager.transcript(older, previous, maxChars = budget * 3)
        val summary = try {
            c.llm.streamChat(
                model = model,
                messages = listOf(
                    ChatMessage(role = "system", content = ContextManager.SUMMARY_PROMPT),
                    ChatMessage(role = "user", content = transcript),
                ),
                tools = null,
                thinking = ThinkingLevel.OFF,
            ) { }.content.let { splitThinking(it).second.trim() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return false
        }
        if (summary.isBlank()) return false
        host.update { it.copy(summary = summary, summaryUpTo = thread[cut - 1].id) }
        host.persist()
        host.notice("Der ältere Teil des Chats wurde zusammengefasst, damit die Antwort schnell bleibt.")
        return true
    }

    /**
     * The request: a system prompt that stays the same during a chat (the server can reuse its cache for it),
     * the summary of older messages if there is one, then the history with shortened old tool results. [model] answers
     * the request (Lora names it when asked).
     */
    internal fun buildApiMessages(
        conv: Conversation,
        ctx: RepoContext,
        providers: List<ToolProvider>,
        model: String = conv.model,
    ): List<ChatMessage> {
        val settings = c.settings.current
        val useTools = providers.isNotEmpty()
        // Only the date: a time that changes every minute would make the whole request new for the server's cache.
        val today = DateFormat.getDateInstance(DateFormat.FULL, Locale.GERMANY).format(Date())
        var thread = conv.thread()
        val summaryAt = conv.summaryUpTo?.let { id -> thread.indexOfFirst { it.id == id } } ?: -1
        val summary = conv.summary?.takeIf { summaryAt >= 0 }
        if (summary != null) thread = thread.drop(summaryAt + 1)
        val documentsFull = c.documentTools in providers && needsGuide(conv, Guides.documents)
        val system = listOfNotNull(
            // Who answers: Lora; an own system prompt or a persona below may give another role.
            identityPrompt(ModelCatalog.info(model).displayName),
            settings.systemPrompt.ifBlank { null },
            // The memory goes along with every request, also without tools (see Memory).
            Memory.prompt(c, conv),
            conv.systemPrompt?.ifBlank { null },
            ctx.env?.systemPrompt(),
            if (c.githubTools in providers) GitHubTools.systemPrompt(ctx) else null,
            when {
                c.documentTools !in providers -> null
                documentsFull -> c.documentTools.prompt()
                else -> "Mit create_document erstellst du PDF-, Word-, PowerPoint- und andere Dateien, mit edit_document änderst du sie. " +
                    "Lies vor dem ersten Dokument die Anleitung mit read_guide(\"dokumente\")."
            },
            if (c.webTools in providers) WebTools.PROMPT else null,
            if (c.localFolderTools in providers) c.localFolderTools.prompt(conv.folderId) else null,
            if (c.projectFolderTools in providers) c.projectFolderTools.prompt(conv.folderId) else null,
            if (c.researchTools in providers) c.researchTools.prompt() else null,
            ChatModes.byId(conv.mode)?.prompt?.ifBlank { null },
            io.github.thepytales.llmhub.ui.I18n.answerHint(c.settings.current.uiLanguage),
            if (c.assistantTools in providers) c.assistantTools.prompt(conv) else null,
            if (askUser in providers) AskUserTool.PROMPT else null,
            projectPrompt(conv),
            "Heute ist $today. Hinter jeder Nachricht des Nutzers steht, wann er sie geschrieben hat.",
        ).joinToString("\n\n")
        val out = mutableListOf<ChatMessage>()
        if (system.isNotBlank()) out += ChatMessage(role = "system", content = system)
        if (summary != null) {
            out += ChatMessage(role = "system", content = "Zusammenfassung des bisherigen Chatverlaufs (ältere Nachrichten):\n$summary")
        }
        // Only keep tool calls that have a matching result, otherwise the API rejects the history.
        val answered = thread.filter { it.role == "tool" }.mapNotNull { it.toolCallId }.toSet()
        val sent = mutableSetOf<String>()
        val time = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, Locale.GERMANY)
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
                // The time comes from the message itself, so it never changes afterwards.
                "user" -> out += m.copy(content = m.content + "\n\n(geschrieben: ${time.format(Date(m.createdAt))})")
                else -> out += m
            }
        }
        return ContextManager.trimOldToolResults(out)
    }

    /** Files the tools created in a project with a project folder land in its "Ausgabe" (see [WorkFolder.place]). */
    private suspend fun toWorkFolder(output: ToolOutput, ctx: RepoContext): ToolOutput =
        withContext(Dispatchers.IO) { WorkFolder.place(c, ctx.folderId, output) }

    /** Instructions of the chat's project (folder) – also without tools. */
    private fun projectPrompt(conv: Conversation): String? {
        val folder = conv.folderId?.let { c.folders.get(it) } ?: return null
        if (folder.instructions.isBlank()) return null
        return "Dieser Chat gehört zum Projekt „${folder.name}“. Anweisungen für das Projekt:\n${folder.instructions.trim()}"
    }

    /** Whether the long instructions of [guide] go directly into the system prompt for this chat. */
    private fun needsGuide(conv: Conversation, guide: Guides.Guide): Boolean {
        val thread = conv.thread()
        if (thread.any { m -> m.toolCalls.any { it.name in setOf("create_document", "edit_document", "fill_pdf", "read_pdf_form") } }) return true
        val user = thread.lastOrNull { it.role == "user" } ?: return false
        if (user.files.any { it.path != null }) return true
        return guide.needed.containsMatchIn(user.content) || guide.needed.containsMatchIn(user.instructions.orEmpty()) ||
            guide.needed.containsMatchIn(conv.systemPrompt.orEmpty())
    }

    companion object {
        /**
         * Lora, the research assistant of the app (and its mascot), introduces itself by that name; asked for the model,
         * it names [modelName], the one answering right now.
         */
        internal fun identityPrompt(modelName: String) =
            "Du bist Lora, der KI-Forschungsassistent der App „ADEU LLM“ (TU Dresden) – benannt nach Low-Rank " +
                "Adaptation, in der App ein kleiner runder Roboter. Du unterstützt bei Forschung, Lehre und Verwaltung: " +
                "Literatur finden und prüfen, Texte und Dokumente schreiben, Daten auswerten, Lehrveranstaltungen planen. " +
                "Fragt jemand, wie du heißt oder wer du bist, stellst du dich als Lora vor, der Forschungsassistent dieser " +
                "App. Nach dem Sprachmodell gefragt, sagst du, dass im Hintergrund gerade $modelName antwortet (in der App " +
                "wählbar). Weist dir eine Anweisung weiter unten eine andere Rolle oder einen anderen Namen zu, gilt diese."

        /** Upper bound for one request even with automatic continuation. */
        const val HARD_STEP_LIMIT = 500
        /** Identical tool rounds in a row after which the agent stops. */
        const val REPEAT_LIMIT = 4
        /** Read-only tool calls of one answer that run at the same time. */
        const val PARALLEL_TOOLS = 4
    }
}
