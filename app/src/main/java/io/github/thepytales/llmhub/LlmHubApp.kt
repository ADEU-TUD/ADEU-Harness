package io.github.thepytales.llmhub

import android.app.Application
import android.content.Context
import io.github.thepytales.llmhub.agent.AgentRunner
import io.github.thepytales.llmhub.agent.DocumentTools
import io.github.thepytales.llmhub.agent.GitHubTools
import io.github.thepytales.llmhub.agent.WebTools
import io.github.thepytales.llmhub.audio.Speaker
import io.github.thepytales.llmhub.data.AgentEnvironment
import io.github.thepytales.llmhub.data.ChatRepository
import io.github.thepytales.llmhub.data.DefaultTemplates
import io.github.thepytales.llmhub.data.FileBackend
import io.github.thepytales.llmhub.data.Folder
import io.github.thepytales.llmhub.data.ListStore
import io.github.thepytales.llmhub.data.LogoStore
import io.github.thepytales.llmhub.data.ModelsRepository
import io.github.thepytales.llmhub.data.PrefsBackend
import io.github.thepytales.llmhub.data.ScheduledTask
import io.github.thepytales.llmhub.data.SettingsStore
import io.github.thepytales.llmhub.data.Template
import io.github.thepytales.llmhub.data.UsageStore
import io.github.thepytales.llmhub.net.GitHubClient
import io.github.thepytales.llmhub.net.LlmClient
import io.github.thepytales.llmhub.tasks.Notifier
import io.github.thepytales.llmhub.tasks.TaskScheduler
import java.io.File

class AppContainer(context: Context) {
    private val files = context.filesDir
    val settings = SettingsStore(context)
    val usage = UsageStore(files)
    val llm = LlmClient(settings, usage)
    val github = GitHubClient(settings, llm.http)
    val githubTools = GitHubTools(github)
    val logos = LogoStore(files)
    val documentTools = DocumentTools(context.applicationContext, settings, logos)
    val webTools = WebTools(llm.http)
    val chats = ChatRepository(context)
    val models = ModelsRepository(llm)
    val speaker = Speaker(context, settings, llm)
    val folders = ListStore<Folder>(FileBackend(File(files, "folders.json")), Folder.serializer(), { it.id })
    val templates = ListStore<Template>(
        FileBackend(File(files, "templates.json")), Template.serializer(), { it.id }, { DefaultTemplates.all },
    )
    /** In the encrypted preferences because variables can hold API keys. */
    val environments = ListStore<AgentEnvironment>(
        PrefsBackend(settings.prefs, "environments"), AgentEnvironment.serializer(), { it.id },
    )
    val tasks = ListStore<ScheduledTask>(FileBackend(File(files, "tasks.json")), ScheduledTask.serializer(), { it.id })
    val agent = AgentRunner(this)
}

class LlmHubApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Notifier.ensureChannel(this)
        runCatching { TaskScheduler.scheduleAll(this) }
    }
}
