package io.github.thepytales.llmhub

import android.content.Context
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
    val privacy = Pseudonymizer(File(files, "pseudonyms.json")) { settings.current }.also { llm.privacy = it }
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
    /** Unsent text per chat. */
    val chatDrafts = ListStore<ChatDraft>(FileBackend(File(files, "chat_drafts.json")), ChatDraft.serializer(), { it.chatId })
    /** Template forms typed but not sent yet. */
    val drafts = ListStore<TemplateDraft>(FileBackend(File(files, "drafts.json")), TemplateDraft.serializer(), { it.templateId })
    /** Coding schemes for transcripts (qualitative content analysis). */
    val codebooks = io.github.thepytales.llmhub.transcribe.CodebookStore(files)
    /** Living documents of the editor (versions, comments, tabs). */
    val docs = DocsStore(files)
    val documentTools = DocumentTools(context.applicationContext, settings, logos, contacts, docs)
    /** Works the user or the literature search collected (see research/). */
    val literature = LiteratureStore(files)
    val researchTools = ResearchTools(this)
    val webTools = WebTools(llm.http)
    val assistantTools = AssistantTools(this)
    val chats = ChatRepository(context)
    val models = ModelsRepository(llm, settings)
    val speaker = Speaker(context, settings, llm)
    val folders = ListStore<Folder>(FileBackend(File(files, "folders.json")), Folder.serializer(), { it.id })
    /** Own templates; the built-in ones come from [TemplateLibrary]. */
    val templates = ListStore<Template>(FileBackend(File(files, "templates.json")), Template.serializer(), { it.id })
        .also(TemplateLibrary::removeLegacyDefaults)
    /** In the encrypted preferences because variables can hold API keys. */
    val environments = ListStore<AgentEnvironment>(
        PrefsBackend(settings.prefs, "environments"), AgentEnvironment.serializer(), { it.id },
    )
    val tasks = ListStore<ScheduledTask>(FileBackend(File(files, "tasks.json")), ScheduledTask.serializer(), { it.id })
    val agent = AgentRunner(this)
    /** Finished results while the app is open (shown as banner). */
    val events = MutableSharedFlow<ResultEvent>(extraBufferCapacity = 8)
    val runs = ChatRuns(context.applicationContext, this)
    /** Speech to text on the device or the Uni server (formerly the Whisper app); starts on first use. */
    val transcription = Transcription(this, SpeechHost.root(this), SpeechHost.cache(this))
    /** Language models on this device (llama.cpp); the LLM client starts them on demand. */
    val localLlm = LocalLlm(this).also { local -> llm.localProvider = { model -> local.provider(model) } }

    init {
        App.container = this
    }
}

/** A finished answer or task result that the user should know about. */
data class ResultEvent(val title: String, val chatId: String, val message: String)

