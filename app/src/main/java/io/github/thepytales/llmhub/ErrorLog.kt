package io.github.thepytales.llmhub

import android.util.Log

/**
 * Errors the app catches and carries on from (a file that cannot be read, a failed refresh …). They used to vanish
 * without a trace; now they go to the system log and the last ones into the crash report, so a bug report shows
 * what went wrong before.
 */
object ErrorLog {
    private const val MAX = 40
    private val recent = ArrayDeque<String>()

    fun record(what: String, error: Throwable) {
        runCatching { Log.w("ADEU", what, error) }
        val line = "${java.time.LocalTime.now().withNano(0)} $what: ${error::class.simpleName}: ${error.message.orEmpty().take(300)}"
        synchronized(recent) {
            recent.addLast(line)
            while (recent.size > MAX) recent.removeFirst()
        }
    }

    /** The last caught errors, oldest first (for crash reports). */
    fun recentText(): String = synchronized(recent) { recent.joinToString("\n") }
}

/** Records a failure in [ErrorLog]; the result stays as it is (use like `runCatching { … }.logFailure("…")`). */
fun <T> Result<T>.logFailure(what: String): Result<T> = onFailure { ErrorLog.record(what, it) }
