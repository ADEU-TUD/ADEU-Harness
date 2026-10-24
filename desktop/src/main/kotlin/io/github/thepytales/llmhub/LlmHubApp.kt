package io.github.thepytales.llmhub

import android.content.Context
import io.github.thepytales.llmhub.agent.AgentRunner
import io.github.thepytales.llmhub.agent.AssistantTools
import io.github.thepytales.llmhub.agent.ChatRuns
import io.github.thepytales.llmhub.agent.DocumentTools
import io.github.thepytales.llmhub.agent.GitHubTools
import io.github.thepytales.llmhub.agent.WebTools
import io.github.thepytales.llmhub.audio.Speaker
import io.github.thepytales.llmhub.data.AgentEnvironment
import io.github.thepytales.llmhub.data.ChatRepository
import io.github.thepytales.llmhub.data.Contact
import io.github.thepytales.llmhub.data.FileBackend
import io.github.thepytales.llmhub.data.Folder
import io.github.thepytales.llmhub.data.ListStore
import io.github.thepytales.llmhub.data.LogoStore
import io.github.thepytales.llmhub.data.ModelsRepository
import io.github.thepytales.llmhub.data.PrefsBackend
import io.github.thepytales.llmhub.data.ScheduledTask
import io.github.thepytales.llmhub.data.SettingsStore
import io.github.thepytales.llmhub.data.Template
import io.github.thepytales.llmhub.data.TemplateDraft
import io.github.thepytales.llmhub.data.TemplateLibrary
import io.github.thepytales.llmhub.data.UsageStore
import io.github.thepytales.llmhub.desktop.Paths
import io.github.thepytales.llmhub.net.GitHubClient
import io.github.thepytales.llmhub.net.LlmClient
import io.github.thepytales.llmhub.tasks.TaskScheduler
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
    val github = GitHubClient(settings, llm.http)
    val githubTools = GitHubTools(github)
    val logos = LogoStore(files)
    /** Address book for letters. */
    val contacts = ListStore<Contact>(FileBackend(File(files, "contacts.json")), Contact.serializer(), { it.id })
    /** Template forms typed but not sent yet. */
    val drafts = ListStore<TemplateDraft>(FileBackend(File(files, "drafts.json")), TemplateDraft.serializer(), { it.templateId })
    val documentTools = DocumentTools(context.applicationContext, settings, logos, contacts)
    val webTools = WebTools(llm.http)
    val assistantTools = AssistantTools(this)
    val chats = ChatRepository(context)
    val models = ModelsRepository(llm)
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

    init {
        App.container = this
    }
}

/** A finished answer or task result that the user should know about. */
data class ResultEvent(val title: String, val chatId: String, val message: String)

/** Whether the window is visible and focused; set by Main.kt. */
object AppState {
    @Volatile
    var foreground = false
}

/** Keeps the stack trace of the last crash so the app can show it on the next start. */
object CrashLog {
    private const val FILE = "last_crash.txt"

    fun install(context: Context) {
        val file = File(context.filesDir, FILE)
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                file.writeText(
                    "ADEU LLM ${BuildConfig.VERSION_NAME} · ${System.getProperty("os.name")} ${System.getProperty("os.version")} · " +
                        "Java ${System.getProperty("java.version")}\n\n" + error.stackTraceToString()
                )
            }
            AppLog.e("Absturz in ${thread.name}", error)
            previous?.uncaughtException(thread, error)
        }
    }

    /** Returns and forgets the last crash report. */
    fun take(context: Context): String? {
        val file = File(context.filesDir, FILE)
        if (!file.exists()) return null
        return runCatching { file.readText() }.getOrNull().also { file.delete() }
    }
}

/** The running app (Android: the Application object). */
object LlmHubApp {
    val context = Context(Paths.data, Paths.cache)

    lateinit var container: AppContainer
        private set

    fun start() {
        CrashLog.install(context)
        container = AppContainer(context)
        runCatching { TaskScheduler.scheduleAll(context) }.onFailure { AppLog.e("Aufgaben planen", it) }
        // Re-checks the GitHub token, so a revoked one hides the GitHub options again.
        if (container.settings.current.githubToken.isNotBlank()) {
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { runCatching { container.github.verify() } }
        }
    }
}
