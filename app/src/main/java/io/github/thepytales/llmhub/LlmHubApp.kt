package io.github.thepytales.llmhub

import android.app.Application
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
import io.github.thepytales.llmhub.net.GitHubClient
import io.github.thepytales.llmhub.net.LlmClient
import io.github.thepytales.llmhub.tasks.Notifier
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

/** Whether the app is on screen; set by [MainActivity]. */
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
                    "ADEU LLM ${BuildConfig.VERSION_NAME} · Android ${android.os.Build.VERSION.RELEASE} · " +
                        "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}\n\n" + error.stackTraceToString() +
                        ErrorLog.recentText().takeIf { it.isNotBlank() }?.let { "\n\nZuvor abgefangene Fehler:\n$it" }.orEmpty()
                )
            }
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

class LlmHubApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
        container = AppContainer(this)
        Notifier.ensureChannel(this)
        runCatching { TaskScheduler.scheduleAll(this) }.logFailure("Geplante Aufgaben einplanen")
        // Re-checks the GitHub tokens, so revoked ones hide the GitHub options again.
        val s = container.settings.current
        if (s.githubToken.isNotBlank() || s.githubAccounts.isNotEmpty()) {
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { runCatching { container.github.verifyAll() }.logFailure("GitHub-Tokens prüfen") }
        }
    }
}
