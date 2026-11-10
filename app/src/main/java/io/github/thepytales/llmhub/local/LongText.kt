package io.github.thepytales.llmhub.local

import io.github.thepytales.llmhub.ui.tr
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.ceil

/**
 * Lange Texte lokal (AP-108): how long a long document takes with a model on this device – said honestly before the
 * start (from the forecast of AP-105) and shown while it runs. Three ways, as in the dialog for large attachments
 * (AP-85): the whole text in the context, only the matching passages, or section by section with a joining overview.
 * Pure arithmetic; the work itself is done by [io.github.thepytales.llmhub.ui.AttachmentDigest].
 */
object LongText {
    /** Characters per token for German text (as [io.github.thepytales.llmhub.files.AttachmentBudget.CHARS_PER_TOKEN]). */
    const val CHARS_PER_TOKEN = 3.5
    /** Tokens of the instruction around each request. */
    const val PROMPT_TOKENS = 150
    /** Tokens one section summary writes, and the joining overview. */
    const val SECTION_TOKENS = 350
    const val OVERVIEW_TOKENS = 700
    /** Tokens of an answer to a question. */
    const val ANSWER_TOKENS = 500
    /** Seconds per request for loading the request and starting (measured overhead is smaller; cautious). */
    const val REQUEST_OVERHEAD_S = 1.0

    /** The work in tokens: [requests] requests that read [readTokens] (the longest single one [longestRead]) and write [writeTokens]. */
    data class Plan(val requests: Int, val readTokens: Int, val longestRead: Int, val writeTokens: Int)

    /** The whole text in one request (it fits the context). */
    fun whole(chars: Int): Plan = (chars / CHARS_PER_TOKEN).toInt().let { Plan(1, it + PROMPT_TOKENS, it + PROMPT_TOKENS, ANSWER_TOKENS) }

    /** Only the passages for the question: at most [budgetChars] of the text are read. */
    fun passages(chars: Int, budgetChars: Int): Plan = whole(minOf(chars, budgetChars))

    /**
     * Section by section: pieces of [pieceChars], each summarized, then one overview of the summaries (if they do not fit
     * one request, first joined in groups).
     */
    fun sections(chars: Int, pieceChars: Int, contextTokens: Int): Plan {
        val n = ceil(chars.toDouble() / pieceChars.coerceAtLeast(1)).toInt().coerceAtLeast(1)
        val pieceTokens = (minOf(chars, pieceChars) / CHARS_PER_TOKEN).toInt() + PROMPT_TOKENS
        var requests = n
        var read = (chars / CHARS_PER_TOKEN).toInt() + n * PROMPT_TOKENS
        var write = n * SECTION_TOKENS
        // Joining: the summaries are read once more, in groups that fit the context.
        var level = n * SECTION_TOKENS
        while (level + PROMPT_TOKENS > contextTokens * 0.7) {
            val groups = ceil(level / (contextTokens * 0.6)).toInt()
            requests += groups; read += level + groups * PROMPT_TOKENS; write += groups * SECTION_TOKENS
            level = groups * SECTION_TOKENS
        }
        if (n > 1) { requests += 1; read += level + PROMPT_TOKENS; write += OVERVIEW_TOKENS }
        return Plan(requests, read, pieceTokens, write)
    }

    /**
     * Seconds for [plan] with [f]: reading slows down the longer one request is (attention over all earlier tokens; about
     * half again at 32 k), writing at the forecast speed.
     */
    fun seconds(plan: Plan, f: SpeedForecast.Forecast): Double {
        val slow = 1 + 0.5 * (plan.longestRead / 2.0) / 32_768
        return plan.readTokens / f.readTokS * slow + plan.writeTokens / f.writeTokS + plan.requests * REQUEST_OVERHEAD_S
    }

    /** „≈ 40 s“, „≈ 6 Min.“, „≈ 1 Std. 10 Min.“ */
    fun duration(seconds: Double): String = when {
        seconds < 60 -> tr("≈ {0} s", (ceil(seconds / 5) * 5).toInt().coerceAtLeast(5))
        seconds < 3600 -> tr("≈ {0} Min.", ceil(seconds / 60).toInt())
        else -> tr("≈ {0} Std. {1} Min.", (seconds / 3600).toInt(), ceil((seconds % 3600) / 60).toInt())
    }

    // ── Progress while it runs ─────────────────────────────────────────────────────────────────────────────

    data class Progress(val done: Int, val total: Int, val startedAt: Long, val estimateS: Double) {
        /** Remaining seconds: from the pace so far once a step is done, before that from the estimate. */
        fun remainingS(now: Long = System.currentTimeMillis()): Double {
            val elapsed = (now - startedAt) / 1000.0
            return if (done == 0) (estimateS - elapsed).coerceAtLeast(5.0) else elapsed / done * (total - done)
        }
        fun text(): String = tr("Abschnitt {0} von {1} · noch {2}", (done + 1).coerceAtMost(total), total, duration(remainingS()))
    }

    private val _progress = MutableStateFlow<Map<String, Progress>>(emptyMap())
    /** Running section summaries by file key (name and length). */
    val progress: StateFlow<Map<String, Progress>> = _progress.asStateFlow()

    fun key(name: String, length: Int) = "$name#$length"
    fun update(key: String, p: Progress?) { _progress.value = if (p == null) _progress.value - key else _progress.value + (key to p) }
}
