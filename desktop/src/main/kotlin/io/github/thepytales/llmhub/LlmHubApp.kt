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
import io.github.thepytales.llmhub.desktop.Paths
import io.github.thepytales.llmhub.net.GitHubClient
import io.github.thepytales.llmhub.net.LlmClient
import io.github.thepytales.llmhub.tasks.TaskScheduler
import io.github.thepytales.llmhub.local.LocalLlm
import io.github.thepytales.llmhub.transcribe.SpeechHost
import io.github.thepytales.llmhub.transcribe.Transcription
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import java.io.File

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
                    "Lora ${BuildConfig.VERSION_NAME} · ${System.getProperty("os.name")} ${System.getProperty("os.version")} · " +
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
        // Lora im Videoeditor (AP-38 Stufe d): her actions are there before an Ablauf carries on after the start.
        io.github.thepytales.llmhub.video.VideoLora.register()
        container = AppContainer(context)
        runCatching { TaskScheduler.scheduleAll(context) }.onFailure { AppLog.e("Aufgaben planen", it) }
        // Re-checks the GitHub tokens, so revoked ones hide the GitHub options again.
        val s = container.settings.current
        if (s.githubToken.isNotBlank() || s.githubAccounts.isNotEmpty()) {
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { runCatching { container.github.verifyAll() } }
        }
    }
}
