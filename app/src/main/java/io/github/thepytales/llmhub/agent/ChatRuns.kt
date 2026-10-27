package io.github.thepytales.llmhub.agent

import android.content.Context
import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.AppState
import io.github.thepytales.llmhub.ResultEvent
import io.github.thepytales.llmhub.data.Conversation
import io.github.thepytales.llmhub.data.NO_CHILD
import io.github.thepytales.llmhub.data.ToolCall
import io.github.thepytales.llmhub.data.thread
import io.github.thepytales.llmhub.data.updateMessage
import io.github.thepytales.llmhub.tasks.Notifier
import io.github.thepytales.llmhub.ui.splitThinking
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A write action the agent wants to run; [result] receives the user's answer. */
data class PendingAction(val call: ToolCall, val result: CompletableDeferred<Boolean>)

/** An answer that is being generated for a chat. */
class ChatRun(initial: Conversation) {
    val chatId = initial.id
    val conversation = MutableStateFlow(initial)
    val pending = MutableStateFlow<PendingAction?>(null)
    internal lateinit var job: Job

    suspend fun join() = job.join()
}

/**
 * Runs the agent for chats independently of the chat screen, so an answer keeps being generated when
 * the user leaves the chat or closes the app. While something runs, [AgentService] keeps the app alive
 * and shows a notification; the result arrives as notification if nobody is looking at the chat.
 */
class ChatRuns(private val context: Context, private val c: AppContainer) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _runs = MutableStateFlow<Map<String, ChatRun>>(emptyMap())
    val runs: StateFlow<Map<String, ChatRun>> = _runs.asStateFlow()

    /** Hints for the chat screen (chat id to message). */
    private val _notices = MutableSharedFlow<Pair<String, String>>(extraBufferCapacity = 16)
    val notices: SharedFlow<Pair<String, String>> = _notices

    /** Chat that is open on screen; it gets no notification. */
    @Volatile
    var visibleChatId: String? = null

    private fun watched(chatId: String) = AppState.foreground && visibleChatId == chatId

    fun get(chatId: String): ChatRun? = _runs.value[chatId]

    /** Starts the agent for [conv] (which must already be saved), or returns the run in progress. */
    fun start(conv: Conversation): ChatRun {
        get(conv.id)?.let { return it }
        val run = ChatRun(conv)
        run.job = scope.launch(start = CoroutineStart.LAZY) { execute(run) }
        _runs.update { it + (run.chatId to run) }
        AgentService.start(context)
        run.job.start()
        return run
    }

    /** Changes chat settings (model, title …) while the agent runs. Returns false if nothing runs. */
    fun edit(chatId: String, transform: (Conversation) -> Conversation): Boolean {
        val run = get(chatId) ?: return false
        run.conversation.update(transform)
        if (c.chats.get(chatId) != null) c.chats.save(run.conversation.value)
        return true
    }

    fun stop(chatId: String) {
        val run = get(chatId) ?: return
        run.pending.value?.result?.complete(false)
        run.job.cancel()
    }

    fun stopAll() = _runs.value.keys.forEach(::stop)

    /** Answers the pending action; with [always] the chat runs all further actions without asking. */
    fun answer(chatId: String, allow: Boolean, always: Boolean = false) {
        if (allow && always) edit(chatId) { it.copy(autoApprove = true) }
        get(chatId)?.pending?.value?.result?.complete(allow)
    }

    /**
     * Saves the state of [run]. Renaming, pinning or moving the chat in the side menu only changes the
     * stored copy, so those fields are taken from there. A chat deleted meanwhile is not brought back.
     */
    private fun save(run: ChatRun) {
        val stored = c.chats.get(run.chatId) ?: return
        run.conversation.update { it.copy(title = stored.title, pinned = stored.pinned, folderId = stored.folderId) }
        c.chats.save(run.conversation.value.copy(updatedAt = System.currentTimeMillis()))
    }

    private suspend fun execute(run: ChatRun) {
        val host = object : AgentHost {
            override val conversation: Conversation get() = run.conversation.value
            override fun update(transform: (Conversation) -> Conversation) = run.conversation.update(transform)
            override fun persist() = save(run)
            override fun notice(message: String) {
                _notices.tryEmit(run.chatId to message)
            }

            override suspend fun confirm(call: ToolCall, risk: Risk): Boolean {
                // Read on every call, so switching the mode or "nicht mehr fragen" applies to the next action already.
                if (run.conversation.value.autoApprove || !ApprovalMode.of(c.settings.current.approvalMode).asks(risk)) return true
                val answer = CompletableDeferred<Boolean>()
                run.pending.value = PendingAction(call, answer)
                if (!watched(run.chatId)) Notifier.confirmationNeeded(context, run.conversation.value.title, run.chatId, call.name)
                return try {
                    answer.await()
                } finally {
                    run.pending.value = null
                    Notifier.cancel(context, run.chatId)
                }
            }
        }
        var cancelled = false
        try {
            c.agent.run(host)
        } catch (e: CancellationException) {
            cancelled = true
            host.update { conv ->
                val last = conv.thread().lastOrNull()
                if (last != null && last.role == "assistant" && last.content.isBlank() && last.toolCalls.isEmpty()) {
                    conv.updateMessage(last.id) { it.copy(content = "_(abgebrochen)_", error = true) }
                } else conv
            }
        } catch (e: Exception) {
            // AgentRunner turns API errors into messages; anything else must not crash the app.
            host.notice("Fehler: ${e.message ?: e.javaClass.simpleName}")
        } finally {
            // Stopped before a new version started: show the previous answer again.
            host.update { conv ->
                val last = conv.thread().lastOrNull()
                if (last != null && conv.selected[last.id] == NO_CHILD) conv.copy(selected = conv.selected - last.id) else conv
            }
            val seen = watched(run.chatId)
            if (!seen && !cancelled) host.update { it.copy(unread = true) }
            save(run)
            _runs.update { it - run.chatId }
            if (!seen && !cancelled && c.chats.get(run.chatId) != null) announce(run.conversation.value)
            if (!cancelled && c.chats.get(run.chatId) != null) c.memoryLearner.afterAnswer(run.conversation.value)
        }
    }

    /** Tells the user that an answer is ready: in the app as banner, otherwise as notification. */
    private fun announce(conv: Conversation) {
        val last = conv.thread().lastOrNull { it.role == "assistant" }
        val error = last?.takeIf { it.error }?.content?.take(200)
        if (AppState.foreground) {
            c.events.tryEmit(ResultEvent(conv.title, conv.id, if (error != null) "Fehler bei der Antwort" else "Antwort fertig"))
        } else {
            val answer = last?.takeIf { !it.error }?.let { splitThinking(it.content).second }
            Notifier.chatFinished(context, conv.title, conv.id, answer, error)
        }
    }
}
