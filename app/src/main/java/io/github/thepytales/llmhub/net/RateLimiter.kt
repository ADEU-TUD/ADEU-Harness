package io.github.thepytales.llmhub.net

import kotlinx.coroutines.delay

/**
 * Keeps the requests per minute below the limit of the API key, so agent runs pause briefly instead of
 * running into HTTP 429. The limit is either set in the settings or learned from the server's 429 answers
 * (the number of requests of the last minute when it refused, minus one); a learned limit expires after
 * 15 minutes, so a temporarily stricter server does not slow the app down for good.
 */
class RateLimiter(private val configured: () -> Int, private val clock: () -> Long = System::currentTimeMillis) {
    private val times = ArrayDeque<Long>()
    private var learned: Int? = null
    private var learnedAt = 0L

    @Synchronized
    private fun prune(now: Long) {
        while (times.isNotEmpty() && now - times.first() >= WINDOW) times.removeFirst()
    }

    /** The limit in effect, or null while none is known. */
    @Synchronized
    fun limit(now: Long = clock()): Int? =
        configured().takeIf { it > 0 } ?: learned?.takeIf { now - learnedAt < LEARNED_FOR }

    /** Milliseconds to wait before the next request; 0 means it was counted and may start now. */
    @Synchronized
    fun reserve(now: Long = clock()): Long {
        prune(now)
        val limit = limit(now)
        if (limit == null || times.size < limit) {
            times.addLast(now)
            return 0
        }
        return (times.first() + WINDOW - now).coerceAtLeast(250)
    }

    /** Waits until a request fits into the window; [onWait] gets the waiting time in seconds (only for noticeable waits). */
    suspend fun acquire(onWait: (Long) -> Unit = {}) {
        while (true) {
            val wait = reserve()
            if (wait == 0L) return
            if (wait >= 2000) onWait((wait + 999) / 1000)
            delay(wait)
        }
    }

    /** The server answered 429: the requests of the last minute were one too many. */
    @Synchronized
    fun onRateLimited(now: Long = clock()) {
        prune(now)
        learned = (times.size - 1).coerceAtLeast(1).let { n -> learned?.let { minOf(it, n) } ?: n }
        learnedAt = now
    }

    private companion object {
        const val WINDOW = 60_000L
        const val LEARNED_FOR = 15 * 60_000L
    }
}
