package io.github.thepytales.llmhub.audio

/**
 * Cuts a growing answer into parts to read aloud while it is still being written: the first part is one sentence (so
 * the first sound comes quickly), later parts gather finished sentences up to [max] characters. Text inside a code
 * block that is still open is held back. Pure logic, so it can be tested.
 */
class SpeechStream(private val max: Int = 600) {
    private var offset = 0
    private var first = true

    /** Next part of [text] to speak, or null if no sentence is finished yet; [done]: the answer is complete. */
    fun next(text: String, done: Boolean): String? {
        if (offset >= text.length) return null
        val ends = SENTENCE_END.findAll(text, offset).map { it.range.last + 1 }
            .filter { text.substring(0, it).split("```").size % 2 == 1 }.toMutableList()
        if (done) ends += text.length
        val end = when {
            ends.isEmpty() -> return null
            first -> ends.first()
            else -> ends.lastOrNull { it - offset <= max } ?: ends.first()
        }
        val part = text.substring(offset, end)
        offset = end
        if (part.isBlank()) return next(text, done)
        first = false
        return part.trim()
    }

    /** All parts of a finished [text]. */
    fun all(text: String): List<String> = generateSequence { next(text, true) }.toList()

    private companion object {
        val SENTENCE_END = Regex("""[.!?:;](?=\s)|\n""")
    }
}
