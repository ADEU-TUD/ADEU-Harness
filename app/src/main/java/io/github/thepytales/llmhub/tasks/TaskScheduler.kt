package io.github.thepytales.llmhub.tasks

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
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
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.TimeUnit

/** Plans the runs of scheduled tasks with WorkManager (also after restarts of the phone). */
object TaskScheduler {
    private const val KEY_TASK = "taskId"
    private const val KEY_MANUAL = "manual"

    private fun workName(id: String) = "task_$id"

    /**
     * (Re)plans the next run of [task]; disabled tasks are cancelled. Returns the planned time.
     * Inside the running worker use APPEND_OR_REPLACE – REPLACE would cancel the worker itself.
     */
    fun schedule(context: Context, task: ScheduledTask, policy: ExistingWorkPolicy = ExistingWorkPolicy.REPLACE): Long? {
        val wm = WorkManager.getInstance(context)
        val next = if (task.enabled) task.nextRun() else null
        if (next == null) {
            wm.cancelUniqueWork(workName(task.id))
            return null
        }
        val request = OneTimeWorkRequestBuilder<TaskWorker>()
            .setInitialDelay((next - System.currentTimeMillis()).coerceAtLeast(0), TimeUnit.MILLISECONDS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInputData(workDataOf(KEY_TASK to task.id))
            .addTag("scheduled_task")
            .build()
        wm.enqueueUniqueWork(workName(task.id), policy, request)
        return next
    }

    fun cancel(context: Context, id: String) {
        WorkManager.getInstance(context).cancelUniqueWork(workName(id))
    }

    /** Runs a task once right now (in the background). */
    fun runNow(context: Context, id: String) {
        val request = OneTimeWorkRequestBuilder<TaskWorker>()
            .setInputData(workDataOf(KEY_TASK to id, KEY_MANUAL to true))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork("run_$id", ExistingWorkPolicy.KEEP, request)
    }

    /** Makes sure every enabled task has a planned run; planned or running work is kept. */
    fun scheduleAll(context: Context) {
        val c = (context.applicationContext as LlmHubApp).container
        c.tasks.current.forEach { t ->
            val next = schedule(context, t, ExistingWorkPolicy.KEEP)
            if (next != t.nextRunAt) c.tasks.update(t.id) { it.copy(nextRunAt = next) }
        }
    }

    internal const val TASK = KEY_TASK
    internal const val MANUAL = KEY_MANUAL
}

class TaskWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val id = inputData.getString(TaskScheduler.TASK) ?: return Result.success()
        val manual = inputData.getBoolean(TaskScheduler.MANUAL, false)
        val c = (applicationContext as LlmHubApp).container
        val task = c.tasks.get(id) ?: return Result.success()
        if (!manual && !task.enabled) return Result.success()
        try {
            TaskRunner.run(applicationContext, task)
        } finally {
            if (!manual) {
                c.tasks.get(id)?.let { t ->
                    val next = TaskScheduler.schedule(applicationContext, t, ExistingWorkPolicy.APPEND_OR_REPLACE)
                    c.tasks.update(id) { it.copy(nextRunAt = next) }
                }
            }
        }
        return Result.success()
    }
}

/** Executes a task headlessly: new chat in the task's folder, agent run, notification. */
object TaskRunner {
    suspend fun run(context: Context, task: ScheduledTask) {
        val c = (context.applicationContext as LlmHubApp).container
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
