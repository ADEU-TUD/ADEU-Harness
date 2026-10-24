package io.github.thepytales.llmhub.agent

import io.github.thepytales.llmhub.data.ChatMessage
import io.github.thepytales.llmhub.data.ModelCatalog
import io.github.thepytales.llmhub.net.textWithFiles

/**
 * Keeps what is sent to the model small enough to stay fast and below the context length:
 * tool results of finished turns are shortened (the answer already used them), and when a long chat still
 * does not fit, its older part is replaced by a summary (stored on the conversation, see AgentRunner).
 */
object ContextManager {
    /** Upper bound even for models with huge contexts: long prompts are slow and use up the token budget. */
    const val MAX_BUDGET = 100_000
    /** Tool results of earlier turns keep this many characters. */
    const val OLD_TOOL_RESULT = 1_500
    /** Turns (user message plus answer) that always stay complete. */
    const val KEEP_TURNS = 2

    fun tokens(m: ChatMessage): Int =
        ((m.textWithFiles().length + m.toolCalls.sumOf { it.arguments.length + it.name.length }) / 3.5).toInt() + m.images.size * 800 + 4

    fun tokens(messages: List<ChatMessage>): Int = messages.sumOf { tokens(it) }

    /** Tokens of history the request may use for [model]. */
    fun budget(model: String): Int {
        val ctx = ModelCatalog.effective(ModelCatalog.info(model)).contextTokens ?: 32_768
        return minOf((ctx * 0.6).toInt(), MAX_BUDGET)
    }

    /**
     * Shortens tool results that belong to turns before the last user message. The model saw them in full
     * when it answered; later it only needs to know what came out (and can call the tool again).
     */
    fun trimOldToolResults(messages: List<ChatMessage>): List<ChatMessage> {
        val lastUser = messages.indexOfLast { it.role == "user" }
        return messages.mapIndexed { i, m ->
            if (i < lastUser && m.role == "tool" && m.content.length > OLD_TOOL_RESULT) {
                m.copy(content = m.content.take(OLD_TOOL_RESULT) + "\n…[gekürzt, um Platz zu sparen – bei Bedarf das Werkzeug erneut aufrufen]")
            } else m
        }
    }

    /**
     * Where the history can be cut for a summary: the index of the user message that starts the turns kept in
     * full, or -1 if the chat is too short to summarize.
     */
    fun cutIndex(thread: List<ChatMessage>): Int {
        val users = thread.indices.filter { thread[it].role == "user" }
        if (users.size <= KEEP_TURNS) return -1
        return users[users.size - KEEP_TURNS]
    }

    /** Plain transcript of [messages] for the summary request, each part shortened. */
    fun transcript(messages: List<ChatMessage>, previousSummary: String?, maxChars: Int): String {
        val parts = messages.mapNotNull { m ->
            when (m.role) {
                "user" -> "Nutzer: " + m.textWithFiles().take(3_000)
                "assistant" -> {
                    val calls = m.toolCalls.joinToString("; ") { "${it.name}(${it.arguments.take(200)})" }
                    listOf(m.content.take(3_000).takeIf { it.isNotBlank() }, calls.takeIf { it.isNotBlank() }?.let { "[Werkzeuge: $it]" })
                        .filterNotNull().joinToString("\n").takeIf { it.isNotBlank() }?.let { "Assistent: $it" }
                }
                "tool" -> "Ergebnis ${m.toolName.orEmpty()}: " + m.content.take(600)
                else -> null
            }
        }
        val text = parts.joinToString("\n\n")
        val head = previousSummary?.let { "Bisherige Zusammenfassung:\n$it\n\n" }.orEmpty()
        // Very long histories: the most recent part matters most.
        return head + if (text.length > maxChars) "…\n" + text.takeLast(maxChars) else text
    }

    const val SUMMARY_PROMPT = "Fasse den folgenden Chatverlauf so zusammen, dass das Gespräch nahtlos fortgesetzt werden kann. " +
        "Behalte: Ziele und Wünsche des Nutzers, getroffene Entscheidungen, wichtige Fakten, Namen, Zahlen, Termine, erstellte " +
        "Dokumente (Dateinamen), offene Aufgaben und Fragen. Lass Höflichkeiten und Wiederholungen weg. Stichpunkte, höchstens " +
        "800 Wörter, auf Deutsch. Antworte nur mit der Zusammenfassung."
}
