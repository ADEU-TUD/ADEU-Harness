package io.github.thepytales.llmhub.agent

import io.github.thepytales.llmhub.data.BatchGrading
import io.github.thepytales.llmhub.data.BatchItem
import io.github.thepytales.llmhub.data.BatchJob
import io.github.thepytales.llmhub.data.BatchStore
import io.github.thepytales.llmhub.data.ChatMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * Runs the batches (AP-20) one file after the other in the background: interactive chats come first (the batch waits
 * while one answers), requests are spread out so the API key's limit stays free for chats, failed files are tried
 * three times, every result is stored at once. A job that was running when the app closed carries on after the start.
 *
 * [ask] sends one request (model, messages) and returns the answer; [chatsBusy] says whether a chat is answering;
 * [perMinute] is the most requests per minute the batch may use (null = no limit of its own).
 */
class BatchRunner(
    private val store: BatchStore,
    private val ask: suspend (job: BatchJob, messages: List<ChatMessage>) -> String,
    private val chatsBusy: () -> Boolean = { false },
    private val perMinute: () -> Int? = { null },
    private val clock: () -> Long = System::currentTimeMillis,
    private val onFinished: (BatchJob) -> Unit = {},
    /** How long to wait after a failed file before the next try (tests: 0). */
    private val retryDelayMs: Long = 15_000,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    @Volatile private var worker: Job? = null

    /** The job and file being worked on now. */
    data class Current(val jobId: String, val itemId: String, val name: String)

    private val _current = MutableStateFlow<Current?>(null)
    val current: StateFlow<Current?> = _current.asStateFlow()

    /** Why the batch waits right now („Ein Chat hat Vorrang“, „Wartet auf die Nacht“), null while it works. */
    private val _waiting = MutableStateFlow<String?>(null)
    val waiting: StateFlow<String?> = _waiting.asStateFlow()

    /** Something is running (Android keeps the app alive meanwhile, see AgentService). */
    private val _active = MutableStateFlow(false)
    val active: StateFlow<Boolean> = _active.asStateFlow()

    private val _finished = MutableSharedFlow<BatchJob>(extraBufferCapacity = 4)
    val finished: SharedFlow<BatchJob> = _finished

    private val sent = ArrayDeque<Long>()

    /** After the app starts: files that were in progress go back to the queue, running jobs carry on. */
    fun resume() {
        for (j in store.jobs.value) {
            if (j.items.any { it.status == BatchItem.RUNNING }) {
                store.update(j.id) { job -> job.copy(items = job.items.map { if (it.status == BatchItem.RUNNING) it.copy(status = BatchItem.PENDING) else it }) }
            }
        }
        if (store.jobs.value.any { it.state == BatchJob.RUNNING }) kick()
    }

    fun start(id: String) {
        store.update(id) { it.copy(state = BatchJob.RUNNING) }
        kick()
    }

    /** Stops after the current request; the file being worked on goes back to the queue (nothing is lost). */
    fun pause(id: String) {
        store.update(id) { it.copy(state = BatchJob.PAUSED) }
        if (_current.value?.jobId == id) {
            val w = worker
            scope.launch {
                w?.cancelAndJoin()
                kick()
            }
        }
    }

    /** Files that failed three times get another chance. */
    fun retryFailed(id: String) {
        store.update(id) { j -> j.copy(items = j.items.map { if (it.status == BatchItem.FAILED) it.copy(status = BatchItem.PENDING, attempts = 0, error = null) else it }) }
        start(id)
    }

    /** One file again (e.g. after changing the raster). */
    fun redo(id: String, item: String) {
        store.updateItem(id, item) { it.copy(status = BatchItem.PENDING, attempts = 0, error = null, result = "", grade = null) }
        start(id)
    }

    @Synchronized
    private fun kick() {
        if (worker?.isActive == true) return
        worker = scope.launch { loop() }
    }

    private suspend fun loop() {
        _active.value = true
        try {
            while (true) {
                val job = store.jobs.value.filter { it.state == BatchJob.RUNNING }.minByOrNull { it.createdAt } ?: break
                val item = job.items.firstOrNull { it.status == BatchItem.PENDING }
                if (item == null) {
                    val done = store.update(job.id) { it.copy(state = BatchJob.DONE) } ?: job
                    _finished.tryEmit(done)
                    onFinished(done)
                    continue
                }
                if (job.night && !isNight()) {
                    _waiting.value = "Wartet auf die Nacht (22–6 Uhr)"
                    delay(60_000)
                    continue
                }
                if (chatsBusy()) {
                    _waiting.value = "Ein Chat antwortet gerade – der Stapel wartet"
                    delay(1500)
                    continue
                }
                throttle()
                _waiting.value = null
                process(job, item)
            }
        } finally {
            _current.value = null
            _waiting.value = null
            _active.value = false
        }
    }

    /** Keeps the batch within [perMinute] requests per minute. */
    private suspend fun throttle() {
        val limit = perMinute()?.takeIf { it > 0 } ?: return
        while (true) {
            val now = clock()
            while (sent.isNotEmpty() && now - sent.first() >= 60_000) sent.removeFirst()
            if (sent.size < limit) {
                sent.addLast(now)
                return
            }
            _waiting.value = "Kurze Pause, damit Chats schnell bleiben"
            delay((sent.first() + 60_000 - now).coerceIn(250, 60_000))
        }
    }

    private fun isNight(): Boolean {
        val h = Calendar.getInstance().apply { timeInMillis = clock() }.get(Calendar.HOUR_OF_DAY)
        return h >= 22 || h < 6
    }

    private suspend fun process(job: BatchJob, item: BatchItem) {
        _current.value = Current(job.id, item.id, item.name)
        val text = store.text(job.id, item.id)
        if (text.isNullOrBlank()) {
            store.updateItem(job.id, item.id) { it.copy(status = BatchItem.FAILED, error = "Die Datei enthält keinen lesbaren Text.") }
            return
        }
        store.updateItem(job.id, item.id) { it.copy(status = BatchItem.RUNNING, startedAt = clock(), attempts = it.attempts + 1) }
        try {
            val answer = ask(job, messages(job, item, text))
            if (job.grading) {
                val grade = BatchGrading.parse(answer, job.rubric) ?: throw IllegalStateException("Die Antwort enthielt keine lesbare Bewertung.")
                store.updateItem(job.id, item.id) { it.copy(status = BatchItem.DONE, result = answer, grade = grade, error = null, finishedAt = clock()) }
            } else {
                if (answer.isBlank()) throw IllegalStateException("Leere Antwort.")
                store.updateItem(job.id, item.id) { it.copy(status = BatchItem.DONE, result = answer.trim(), error = null, finishedAt = clock()) }
            }
        } catch (e: CancellationException) {
            // Paused or the app closes: the file goes back to the queue.
            store.updateItem(job.id, item.id) { it.copy(status = BatchItem.PENDING, attempts = (it.attempts - 1).coerceAtLeast(0)) }
            throw e
        } catch (e: Exception) {
            val attempts = store.get(job.id)?.items?.firstOrNull { it.id == item.id }?.attempts ?: MAX_ATTEMPTS
            val message = e.message ?: e.javaClass.simpleName
            store.updateItem(job.id, item.id) { it.copy(status = if (attempts >= MAX_ATTEMPTS) BatchItem.FAILED else BatchItem.PENDING, error = message) }
            if (attempts < MAX_ATTEMPTS && retryDelayMs > 0) {
                _waiting.value = "Fehler bei „${item.name}“ – neuer Versuch gleich"
                delay(retryDelayMs * attempts)
            }
        }
    }

    /** The request for one file: the job's system prompt (the same for every file) and the file. */
    fun messages(job: BatchJob, item: BatchItem, text: String, maxChars: Int = MAX_CHARS): List<ChatMessage> {
        val system = if (job.grading) {
            val examples = job.items.filter { it.decision?.calibration == true }.mapNotNull { i -> store.text(job.id, i.id)?.let { it to i.decision!! } }
            BatchGrading.gradingSystem(job, examples)
        } else BatchGrading.taskSystem(job)
        return listOf(
            ChatMessage(role = "system", content = system),
            ChatMessage(role = "user", content = BatchGrading.fileMessage(item.name, item.pages, text, maxChars)),
        )
    }

    companion object {
        const val MAX_ATTEMPTS = 3
        /** About 50,000 tokens of a file per request. */
        const val MAX_CHARS = 180_000
    }
}
