package io.github.thepytales.llmhub.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Rückfall, wenn der Uni-Server wegfällt (AP-112): the Uni-Server is a pilot of the TU Dresden. This notes how requests
 * to the main provider end – answered, refused (key no longer valid) or not reachable – and says when access is gone
 * for good: failures over at least [DAYS_MS] with at least [MIN_FAILURES] tries and no answer in between, and either
 * the key was refused or the Internet worked meanwhile (another address answered, e.g. the update check). Being
 * offline for a weekend is therefore no reason. Only counts, no contents; kept in a small file on this device.
 */
class UniWatch(private val file: File, private val now: () -> Long = System::currentTimeMillis) {
    @Serializable
    data class State(
        val firstFail: Long = 0,
        val lastFail: Long = 0,
        val failures: Int = 0,
        /** The server refused the key (401/403) at least once since [firstFail]. */
        val refused: Boolean = false,
        val lastOk: Long = 0,
        /** Another address answered after [firstFail]: the Internet worked, the server did not. */
        val internetOk: Long = 0,
    )

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Volatile var state: State = runCatching { json.decodeFromString<State>(file.readText()) }.getOrDefault(State())
        private set

    private fun save(next: State) {
        state = next
        runCatching { file.parentFile?.mkdirs(); file.writeText(json.encodeToString(State.serializer(), next)) }
    }

    /** The main provider answered. */
    fun ok() {
        if (state.failures == 0 && now() - state.lastOk < 3_600_000L) return
        save(State(lastOk = now()))
    }

    /** A request to the main provider failed: [refused] for 401/403, otherwise not reachable. */
    fun failed(refused: Boolean) {
        val s = state
        val t = now()
        save(s.copy(firstFail = if (s.failures == 0) t else s.firstFail, lastFail = t, failures = s.failures + 1, refused = s.refused || refused))
    }

    /** Some other address answered (update check, web search): the Internet works. */
    fun internet() {
        val s = state
        if (s.failures > 0 && s.internetOk < s.firstFail) save(s.copy(internetOk = now()))
    }

    /** Access is gone for good (see the class). */
    fun gone(): Boolean {
        val s = state
        return s.failures >= MIN_FAILURES && s.lastFail - s.firstFail >= DAYS_MS && (s.refused || s.internetOk >= s.firstFail)
    }

    /** Since when the server does not answer (for the message), 0 if it does. */
    val since: Long get() = if (state.failures == 0) 0 else state.firstFail

    companion object {
        const val DAYS_MS = 2 * 24 * 3_600_000L
        const val MIN_FAILURES = 4

        /** An error that says the server is not reachable (not: overloaded or a wrong request). */
        fun unreachable(e: Throwable): Boolean = generateSequence(e) { it.cause }.take(5).any {
            it is java.net.UnknownHostException || it is java.net.ConnectException || it is java.net.NoRouteToHostException
        }
    }
}
