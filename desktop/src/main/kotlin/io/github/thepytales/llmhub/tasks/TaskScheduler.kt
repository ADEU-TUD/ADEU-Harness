package io.github.thepytales.llmhub.tasks

import android.content.Context
import io.github.thepytales.llmhub.AppLog
import io.github.thepytales.llmhub.AppState
import io.github.thepytales.llmhub.LlmHubApp
import io.github.thepytales.llmhub.ResultEvent
import io.github.thepytales.llmhub.agent.AgentHost
import io.github.thepytales.llmhub.agent.Risk
import io.github.thepytales.llmhub.agent.ToolSelection
import io.github.thepytales.llmhub.data.ChatMessage
import io.github.thepytales.llmhub.data.Conversation
import io.github.thepytales.llmhub.data.Folder
import io.github.thepytales.llmhub.data.ScheduledTask
import io.github.thepytales.llmhub.data.ToolCall
import io.github.thepytales.llmhub.data.appendToThread
import io.github.thepytales.llmhub.data.thread
import io.github.thepytales.llmhub.ui.splitThinking
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.ConcurrentHashMap

/**
 * Runs scheduled tasks while the app is running (also in the notification area). A run that was due while
 * the PC was off or the app closed is made up once on the next start – like WorkManager on Android.
 */
object TaskScheduler {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val running = ConcurrentHashMap<String, Job>()
    private var ticker: Job? = null

    /** (Re)plans the next run of [task]; returns the planned time (null if disabled). */
    @Suppress("UNUSED_PARAMETER")
    fun schedule(context: Context, task: ScheduledTask): Long? = if (task.enabled) task.nextRun() else null

    @Suppress("UNUSED_PARAMETER")
    fun cancel(context: Context, id: String) {
        running.remove(id)?.cancel()
    }

    /** Runs a task once right now (in the background). */
    @Suppress("UNUSED_PARAMETER")
    fun runNow(context: Context, id: String) {
        launch(id, manual = true)
    }

    /** Makes sure every enabled task has a planned run and starts the clock that runs due tasks. */
    @Synchronized
    fun scheduleAll(context: Context) {
        val c = LlmHubApp.container
        c.tasks.current.forEach { t ->
            // Missed runs are kept (in the past), so the clock makes them up right away.
            val next = when {
                !t.enabled -> null
                t.nextRunAt != null -> t.nextRunAt
                else -> schedule(context, t)
            }
            if (next != t.nextRunAt) c.tasks.update(t.id) { it.copy(nextRunAt = next) }
        }
        if (ticker == null) {
            ticker = scope.launch {
                while (isActive) {
                    runCatching { tick() }.onFailure { AppLog.e("Aufgaben prüfen", it) }
                    delay(20_000)
                }
            }
        }
    }

    private fun tick() {
        val c = LlmHubApp.container
        val now = System.currentTimeMillis()
        c.tasks.current
            .filter { it.enabled && !running.containsKey(it.id) && (it.nextRunAt ?: Long.MAX_VALUE) <= now }
            .forEach { launch(it.id, manual = false) }
    }

    private fun launch(id: String, manual: Boolean) {
        if (running.containsKey(id)) return
        val c = LlmHubApp.container
        val task = c.tasks.get(id) ?: return
        val job = scope.launch(start = kotlinx.coroutines.CoroutineStart.LAZY) {
            try {
                TaskRunner.run(LlmHubApp.context, task)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.e("Aufgabe ${task.name}", e)
            } finally {
                running.remove(id)
                if (!manual) {
                    c.tasks.get(id)?.let { t ->
                        val next = schedule(LlmHubApp.context, t)
                        c.tasks.update(id) { it.copy(nextRunAt = next) }
                    }
                }
            }
        }
        if (running.putIfAbsent(id, job) == null) job.start() else job.cancel()
    }
}

/** Executes a task headlessly: new chat in the task's folder, agent run, notification. */
object TaskRunner {
    suspend fun run(context: Context, task: ScheduledTask) {
        val c = LlmHubApp.container
        c.chats.awaitLoaded()
        val folderId = task.folderId?.takeIf { c.folders.get(it) != null }
            ?: Folder(name = task.name).also { c.folders.upsert(it) }.id
        if (folderId != task.folderId) c.tasks.update(task.id) { it.copy(folderId = folderId) }
        val stamp = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date())
        var conv = Conversation(
            title = "${task.name} · $stamp",
            model = task.model,
            systemPrompt = task.systemPrompt.ifBlank { null },
            githubEnabled = task.github,
            repo = task.repo,
            envId = task.envId,
            folderId = folderId,
            taskId = task.id,
        ).appendToThread(ChatMessage(role = "user", content = task.prompt))
        c.chats.save(conv)
        val host = object : AgentHost {
            override val conversation: Conversation get() = conv
            override fun update(transform: (Conversation) -> Conversation) {
                conv = transform(conv)
            }
            override fun persist() = c.chats.save(conv.copy(updatedAt = System.currentTimeMillis()))
            override fun notice(message: String) {}
            override suspend fun confirm(call: ToolCall, risk: Risk) = task.allowWrites
        }
        var status = "Erfolgreich"
        try {
            c.agent.run(host, maxSteps = 15, tools = ToolSelection(task.github, task.documentTools, task.webTools))
            val last = conv.thread().lastOrNull { it.role == "assistant" }
            status = if (last == null || last.error) "Fehler: ${last?.content?.take(120) ?: "keine Antwort"}" else "Erfolgreich"
        } catch (e: CancellationException) {
            status = "Abgebrochen"
            throw e
        } catch (e: Exception) {
            status = "Fehler: ${e.message}"
            conv = conv.appendToThread(ChatMessage(role = "assistant", content = e.message ?: e.toString(), error = true))
        } finally {
            // Marked as new until the user opens it (dot in the side menu).
            conv = conv.copy(unread = true)
            host.persist()
        }
        c.tasks.update(task.id) { it.copy(lastRunAt = System.currentTimeMillis(), lastStatus = status, lastChatId = conv.id) }
        if (task.notify) {
            val answer = conv.thread().lastOrNull { it.role == "assistant" && !it.error }?.let { splitThinking(it.content).second }
            Notifier.taskFinished(context, task, conv.id, answer, status.takeIf { it != "Erfolgreich" })
        }
        if (AppState.foreground) c.events.tryEmit(ResultEvent(task.name, conv.id, if (status == "Erfolgreich") "Ergebnis ist da" else status))
    }
}
