package io.github.thepytales.llmhub.docs

/**
 * Slides of the presentation editor, stored as the Markdown [PptxWriter] reads: slides separated by "---", the
 * first heading is the title, "|||" starts the right column and "Notizen:" the speaker notes.
 */
object SlideDeck {
    enum class Layout(val label: String) { TITLE("Titelfolie"), SECTION("Abschnitt"), CONTENT("Inhalt"), TWO_COLUMNS("Zwei Spalten"), QUOTE("Zitat") }

    data class Slide(
        val title: String = "",
        /** Subtitle (title slide) or content in Markdown. */
        val body: String = "",
        /** Right column (two columns). */
        val right: String = "",
        val notes: String = "",
        val layout: Layout = Layout.CONTENT,
    )

    private val SEPARATOR = Regex("^\\s*-{3,}\\s*$")
    private val NOTES = Regex("^\\s*(Notizen|Notiz|Sprechernotizen|Sprechertext|Notes)\\s*:\\s*", RegexOption.IGNORE_CASE)
    private val COLUMNS = Regex("^\\s*(\\|\\|\\||::: *rechts|:::\\s*right)\\s*$", RegexOption.IGNORE_CASE)
    private val HEADING = Regex("^#{1,6}\\s+")

    fun parse(markdown: String): List<Slide> {
        val chunks = mutableListOf(StringBuilder())
        var inCode = false
        for (line in markdown.replace("\r\n", "\n").lines()) {
            if (line.trimStart().startsWith("```")) inCode = !inCode
            if (!inCode && SEPARATOR.matches(line)) chunks += StringBuilder() else chunks.last().append(line).append('\n')
        }
        return chunks.map { it.toString().trim() }.filter { it.isNotEmpty() }.mapIndexed { i, t -> slide(t, i == 0) }
    }

    private fun slide(text: String, first: Boolean): Slide {
        val lines = text.lines()
        val notesAt = lines.indexOfFirst { NOTES.containsMatchIn(it) }
        val notes = if (notesAt < 0) "" else (listOf(lines[notesAt].replace(NOTES, "")) + lines.drop(notesAt + 1)).joinToString("\n").trim()
        val main = if (notesAt < 0) lines else lines.take(notesAt)
        val split = main.indexOfFirst { COLUMNS.matches(it) }
        val left = (if (split < 0) main else main.take(split)).toMutableList()
        val right = if (split < 0) "" else main.drop(split + 1).joinToString("\n").trim()
        val headAt = left.indexOfFirst { HEADING.containsMatchIn(it) }
        val title = if (headAt < 0) "" else left.removeAt(headAt).replace(HEADING, "").trim()
        var body = left.joinToString("\n").trim()
        if (first) {
            body = body.lines().joinToString("\n") { it.replace(HEADING, "") }.trim()
            return Slide(title, body, notes = notes, layout = Layout.TITLE)
        }
        val layout = when {
            right.isNotBlank() -> Layout.TWO_COLUMNS
            body.lines().all { it.isBlank() || it.trimStart().startsWith(">") } && body.isNotBlank() -> Layout.QUOTE
            body.isBlank() || body.length <= 140 && '\n' !in body && !body.trimStart().startsWith("-") && !body.trimStart().startsWith("|") -> Layout.SECTION
            else -> Layout.CONTENT
        }
        return Slide(title, body, right, notes, layout)
    }

    fun markdown(slides: List<Slide>): String = slides.mapIndexed { i, s -> slideMarkdown(s, i == 0) }.joinToString("\n\n---\n\n") + "\n"

    private fun slideMarkdown(s: Slide, first: Boolean): String = buildString {
        if (first || s.layout == Layout.TITLE) {
            append("# ").append(s.title.ifBlank { "Titel" })
            // First line: subtitle; further lines (speaker, occasion, date) stay lines of their own below it.
            val lines = s.body.lines().map { it.trim() }.filter { it.isNotEmpty() }
            if (lines.isNotEmpty()) append("\n\n## ").append(lines.first())
            if (lines.size > 1) append("\n\n").append(lines.drop(1).joinToString("\n\n"))
        } else {
            append("## ").append(s.title.ifBlank { "Folie" })
            val body = when (s.layout) {
                Layout.QUOTE -> s.body.lines().filter { it.isNotBlank() }.joinToString("\n") { if (it.trimStart().startsWith(">")) it else "> $it" }
                else -> s.body.trim()
            }
            if (body.isNotBlank()) append("\n\n").append(body)
            if (s.layout == Layout.TWO_COLUMNS) append("\n\n|||\n\n").append(s.right.trim().ifBlank { "-" })
        }
        if (s.notes.isNotBlank()) append("\n\nNotizen: ").append(s.notes.trim())
    }

    /** A new presentation: title slide and one content slide. */
    fun blank(title: String): String = markdown(
        listOf(
            Slide(title, "Untertitel", layout = Layout.TITLE),
            Slide("Erste Folie", "- Erster Punkt\n- Zweiter Punkt", layout = Layout.CONTENT),
        ),
    )

    /** Template for a new slide of [layout]. */
    fun template(layout: Layout): Slide = when (layout) {
        Layout.TITLE -> Slide("Titel", "Untertitel", layout = layout)
        Layout.SECTION -> Slide("Neuer Abschnitt", "", layout = layout)
        Layout.CONTENT -> Slide("Neue Folie", "- Punkt 1\n- Punkt 2\n- Punkt 3", layout = layout)
        Layout.TWO_COLUMNS -> Slide("Vergleich", "- Links", "- Rechts", layout = layout)
        Layout.QUOTE -> Slide("Zitat", "> „Ein treffendes Zitat.“ – Quelle", layout = layout)
    }
}
