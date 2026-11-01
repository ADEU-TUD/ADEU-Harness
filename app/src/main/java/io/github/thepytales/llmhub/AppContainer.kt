package io.github.thepytales.llmhub

import android.content.Context
import de.lokal.whisper.Speech
import io.github.thepytales.llmhub.agent.AgentRunner
import io.github.thepytales.llmhub.agent.AssistantTools
import io.github.thepytales.llmhub.agent.ChatRuns
import io.github.thepytales.llmhub.agent.DocumentTools
import io.github.thepytales.llmhub.agent.GitHubTools
import io.github.thepytales.llmhub.agent.ResearchTools
import io.github.thepytales.llmhub.agent.WebTools
import io.github.thepytales.llmhub.audio.Speaker
import io.github.thepytales.llmhub.data.AgentEnvironment
import io.github.thepytales.llmhub.data.ChatRepository
import io.github.thepytales.llmhub.data.ChatDraft
import io.github.thepytales.llmhub.data.Contact
import io.github.thepytales.llmhub.data.Snippet
import io.github.thepytales.llmhub.data.FileBackend
import io.github.thepytales.llmhub.data.Folder
import io.github.thepytales.llmhub.data.KnowledgeStore
import io.github.thepytales.llmhub.data.ListStore
import io.github.thepytales.llmhub.data.MemoryEntry
import io.github.thepytales.llmhub.data.Pseudonymizer
import io.github.thepytales.llmhub.data.LogoStore
import io.github.thepytales.llmhub.data.ModelsRepository
import io.github.thepytales.llmhub.data.PrefsBackend
import io.github.thepytales.llmhub.data.ScheduledTask
import io.github.thepytales.llmhub.data.SettingsStore
import io.github.thepytales.llmhub.data.Template
import io.github.thepytales.llmhub.data.TemplateDraft
import io.github.thepytales.llmhub.data.TemplateLibrary
import io.github.thepytales.llmhub.data.UsageStore
import io.github.thepytales.llmhub.docs.DocsStore
import io.github.thepytales.llmhub.net.GitHubClient
import io.github.thepytales.llmhub.net.LlmClient
import io.github.thepytales.llmhub.tasks.Notifier
import io.github.thepytales.llmhub.tasks.TaskScheduler
import io.github.thepytales.llmhub.local.LocalLlm
import io.github.thepytales.llmhub.research.LiteratureStore
import io.github.thepytales.llmhub.transcribe.SpeechHost
import io.github.thepytales.llmhub.transcribe.Transcription
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import java.io.File

class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext
    private val files = context.filesDir
    val settings = SettingsStore(context)
    val usage = UsageStore(files)
    val llm = LlmClient(settings, usage)
    /** Pairs of personal details and placeholders; used by [llm] when pseudonymization is on. */
    val privacy = Pseudonymizer(File(files, "pseudonyms.json"), publicNames = { literatureAuthors() }) { settings.current }.also { llm.privacy = it }

    /** Authors of the literature collection: public, never replaced (AP-03); recomputed only when the collection changes. */
    private var authorCache: Pair<List<Any>, List<String>>? = null

    private fun literatureAuthors(): List<String> {
        val refs = literature.current
        authorCache?.let { (key, names) -> if (key === refs) return names }
        val names = refs.flatMap { r -> r.authors.filter { !it.literal }.flatMap { listOf(it.family, it.display) } }.distinct()
        authorCache = refs to names
        return names
    }
    val github = GitHubClient(settings, llm.http)
    val githubTools = GitHubTools(github)
    val logos = LogoStore(files)
    /** Address book for letters. */
    val contacts = ListStore<Contact>(FileBackend(File(files, "contacts.json")), Contact.serializer(), { it.id })
    /** Own documents the model can search (global and per project). */
    val knowledge = KnowledgeStore(files)
    /** What the model knows about the user in every chat. */
    val memory = ListStore<MemoryEntry>(FileBackend(File(files, "memory.json")), MemoryEntry.serializer(), { it.id })
    /** Text blocks for the input field (Kurztexte). */
    val snippets = ListStore<Snippet>(FileBackend(File(files, "snippets.json")), Snippet.serializer(), { it.id })
    /** Earlier values of template fields, for the suggestions in the forms. */
    val fieldHistory = ListStore<io.github.thepytales.llmhub.data.FieldHistory>(
        FileBackend(File(files, "field_history.json")), io.github.thepytales.llmhub.data.FieldHistory.serializer(), { it.label },
    )
    /** Unsent text per chat. */
    val chatDrafts = ListStore<ChatDraft>(FileBackend(File(files, "chat_drafts.json")), ChatDraft.serializer(), { it.chatId })
    /** Template forms typed but not sent yet. */
    val drafts = ListStore<TemplateDraft>(FileBackend(File(files, "drafts.json")), TemplateDraft.serializer(), { it.templateId })
    /** Coding schemes for transcripts (qualitative content analysis). */
    val codebooks = io.github.thepytales.llmhub.transcribe.CodebookStore(files)
    /** Voices of named speakers, to recognize them in further recordings (only on this device). */
    val voices = io.github.thepytales.llmhub.transcribe.VoiceProfileStore(files)
    /** Living documents of the editor (versions, comments, tabs). */
    val docs = DocsStore(files)
    val documentTools = DocumentTools(context.applicationContext, settings, logos, contacts, docs)
    /** Works the user or the literature search collected (see research/). */
    val literature = LiteratureStore(files)
    val researchTools = ResearchTools(this)
    /** Lernspiele (AP-17): the collection and the agent's tools for it (offered while the tool is switched on). */
    val games = io.github.thepytales.llmhub.data.LearningGameStore(files)
    val gameTools = io.github.thepytales.llmhub.agent.GameTools(this)
    /** Mail & Kalender (AP-13): fixed sorting rules, the protocol of every change in Outlook, the agent's tools. */
    val mailRules = ListStore<io.github.thepytales.llmhub.data.MailRule>(FileBackend(File(files, "mail_rules.json")), io.github.thepytales.llmhub.data.MailRule.serializer(), { it.id })
    val outlookLog = ListStore<io.github.thepytales.llmhub.data.OutlookAction>(FileBackend(File(files, "outlook_log.json")), io.github.thepytales.llmhub.data.OutlookAction.serializer(), { it.id })
    val outlookTools = io.github.thepytales.llmhub.agent.OutlookTools(this)
    val webTools = WebTools(llm.http)
    val assistantTools = AssistantTools(this)
    val chats = ChatRepository(context)
    /** Tresor (AP-10): encrypted chats behind an app PIN. */
    val vault = io.github.thepytales.llmhub.data.VaultStore(files).also { chats.vault = it }
    val models = ModelsRepository(llm, settings)
    val speaker = Speaker(context, settings, llm)
    val folders = ListStore<Folder>(FileBackend(File(files, "folders.json")), Folder.serializer(), { it.id })

    init {
        // Modes of big tools (AP-17 Lernspiel) appear only while their tool is switched on.
        io.github.thepytales.llmhub.agent.ChatModes.enabledTools = { settings.current.enabledToolIds }
    }

    init {
        // Editor documents of a project with a project folder live in its "Ausgabe" as Markdown (like an own storage location).
        documentTools.projectVault = { id ->
            io.github.thepytales.llmhub.agent.WorkFolder.of(this, id)
                ?.takeIf { (_, dir) -> io.github.thepytales.llmhub.agent.WorkFolder.problem(dir) == null }
                ?.let { (folder, dir) ->
                    val out = io.github.thepytales.llmhub.agent.WorkFolder.output(dir).apply { mkdirs() }
                    docs.addVault("Projekt ${folder.name}", out.path, makeDefault = false).id
                }
        }
    }
    /** Own templates; the built-in ones come from [TemplateLibrary]. */
    val templates = ListStore<Template>(FileBackend(File(files, "templates.json")), Template.serializer(), { it.id })
        .also(TemplateLibrary::removeLegacyDefaults)
    /** In the encrypted preferences because variables can hold API keys. */
    val environments = ListStore<AgentEnvironment>(
        PrefsBackend(settings.prefs, "environments"), AgentEnvironment.serializer(), { it.id },
    )
    val tasks = ListStore<ScheduledTask>(FileBackend(File(files, "tasks.json")), ScheduledTask.serializer(), { it.id })
    /** Folders on this PC the agent may work in (Windows). */
    val localFolders = ListStore<io.github.thepytales.llmhub.data.LocalFolder>(
        FileBackend(File(files, "local_folders.json")), io.github.thepytales.llmhub.data.LocalFolder.serializer(), { it.id },
    )
    val localFolderTools = io.github.thepytales.llmhub.agent.LocalFolderTools(this)
    val projectFolderTools = io.github.thepytales.llmhub.agent.ProjectFolderTools(this)
    val agent = AgentRunner(this)
    /** Finished results while the app is open (shown as banner). */
    val events = MutableSharedFlow<ResultEvent>(extraBufferCapacity = 8)
    val runs = ChatRuns(context.applicationContext, this)
    /** Suggests lasting details for the memory when a chat has been quiet for a moment. */
    val memoryLearner = io.github.thepytales.llmhub.agent.MemoryLearner(this)
    /** Speech to text on the device or the Uni server (formerly the Whisper app); starts on first use. */
    val transcription = Transcription(this, SpeechHost.root(this), SpeechHost.cache(this))
    /** Language models on this device (llama.cpp); the LLM client starts them on demand. */
    val localLlm = LocalLlm(this).also { local -> llm.localProvider = { model -> local.provider(model) } }

    init {
        // Gründliche Erkennung (AP-03): the smallest installed local model lists names the rules missed.
        privacy.thoroughFinder = finder@{ text ->
            val model = localLlm.installedModels().minByOrNull { it.needGb }?.modelId ?: return@finder emptyList()
            val result = llm.streamChat(
                model = model,
                messages = listOf(
                    io.github.thepytales.llmhub.data.ChatMessage(role = "system", content = io.github.thepytales.llmhub.data.Pseudonymizer.THOROUGH_PROMPT),
                    io.github.thepytales.llmhub.data.ChatMessage(role = "user", content = text),
                ),
                tools = null,
                thinking = io.github.thepytales.llmhub.net.ThinkingLevel.OFF,
            ) { }
            io.github.thepytales.llmhub.data.Pseudonymizer.parseThorough(result.content)
        }
    }

    /** Background work of the container itself (memory care, AP-04). */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        App.container = this
        // AP-04: measuring points and release of speech models that nobody used for a few minutes.
        io.github.thepytales.llmhub.data.MemoryMeter.start(scope) { ms -> Speech.evictUnusedFor(ms) }
        scope.launch { transcription.finished.collect { io.github.thepytales.llmhub.data.MemoryMeter.mark("Nach Transkription") } }
        // The vault locks itself after its idle time (AP-10).
        scope.launch {
            while (true) {
                kotlinx.coroutines.delay(20_000)
                vault.lockIfIdle()
            }
        }
    }
}

/** A finished answer or task result that the user should know about. */
data class ResultEvent(val title: String, val chatId: String, val message: String)

