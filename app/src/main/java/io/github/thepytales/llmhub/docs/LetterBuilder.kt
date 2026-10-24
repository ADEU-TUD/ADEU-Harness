package io.github.thepytales.llmhub.docs

/**
 * Builds a letter from the fields of the "Brief" template without the model: the user's text is taken over
 * as written, the app only adds salutation, closing and signature and arranges sender and address.
 */
object LetterBuilder {
    data class Input(
        val sender: String,
        val recipient: String,
        val subject: String,
        val reference: String = "",
        val salutation: String = "",
        val body: String,
        val closing: String = "",
    )

    data class Result(val title: String, val content: String, val letter: Letter)

    private val PHONE = Regex("^[+()\\d][\\d\\s()/+-]{5,}$")
    private val KEY_LINE = Regex("^([A-Za-zÄÖÜäöüß. -]{2,18}):\\s*(.*)$")
    private val GREETING_START = Regex("^(sehr geehrte|liebe|lieber|hallo|guten tag|moin)", RegexOption.IGNORE_CASE)
    private val CLOSING = Regex(
        "^(mit freundlichen grüßen|mit freundlichem gruß|freundliche grüße|mit besten grüßen|beste grüße|viele grüße|herzliche grüße|mit kollegialen grüßen)",
        RegexOption.IGNORE_CASE,
    )
    private val TITLES = setOf("prof.", "dr.", "dr.-ing.", "dr. phil.", "prof", "dr", "apl.", "jun.-prof.", "pd")

    fun lines(text: String): List<String> = text.lines().map { it.trim() }.filter { it.isNotEmpty() }

    /** An address typed in one line ("Anna Müller, Hauptstr. 1, 01069 Dresden") becomes one line per part. */
    fun addressLines(text: String): List<String> {
        val l = lines(text)
        return if (l.size == 1 && l[0].contains(',')) l[0].split(',').map { it.trim() }.filter { it.isNotEmpty() } else l
    }

    /**
     * Information block: name first, then function and contact details with labels. Lines without a label are
     * recognised as e-mail, phone or room; the rest (function, unit) stays as it is.
     */
    fun infoLines(sender: String, reference: String = ""): List<String> {
        val out = lines(sender).mapIndexed { i, l ->
            when {
                i == 0 -> l
                KEY_LINE.matches(l) -> l
                l.contains('@') && !l.contains(' ') -> "E-Mail: $l"
                PHONE.matches(l) -> "Telefon: $l"
                l.startsWith("Raum ", ignoreCase = true) || l.startsWith("Zi. ") -> "Raum: " + l.substringAfter(' ').trim()
                else -> l
            }
        }.toMutableList()
        if (reference.isNotBlank()) out += "Ihre Nachricht: ${reference.trim()}"
        return out
    }

    /** Name and function (the first unlabelled line after the name) under the closing. */
    fun signature(sender: String): List<String> {
        val l = lines(sender)
        val name = l.firstOrNull() ?: return emptyList()
        val function = l.drop(1).firstOrNull { !KEY_LINE.matches(it) && !it.contains('@') && !PHONE.matches(it) && !it.startsWith("Raum", ignoreCase = true) }
        return listOfNotNull(name, function)
    }

    /** "Sehr geehrte Frau Prof. Dr. Müller," from an address starting with "Frau …" / "Herrn …", else the neutral form. */
    fun salutationFor(recipient: String): String {
        for (line in addressLines(recipient).take(3)) {
            val words = line.split(Regex("\\s+")).filter { it.isNotEmpty() }
            val first = words.firstOrNull()?.lowercase() ?: continue
            val female = first == "frau"
            val male = first == "herr" || first == "herrn"
            if (!female && !male) continue
            val rest = words.drop(1)
            if (rest.isEmpty()) break
            val titles = rest.takeWhile { it.lowercase() in TITLES || it.endsWith(".") && it.length <= 6 }
            val surname = rest.drop(titles.size).lastOrNull() ?: break
            // In the salutation only "Prof." and "Dr." are kept.
            val kept = titles.filter { it.lowercase().startsWith("prof") || it.lowercase().startsWith("dr") }
            val name = (kept + surname).joinToString(" ")
            return if (female) "Sehr geehrte Frau $name," else "Sehr geehrter Herr $name,"
        }
        return "Sehr geehrte Damen und Herren,"
    }

    fun build(input: Input): Result {
        val body = input.body.trim().replace(Regex("\n{3,}"), "\n\n")
        val firstLine = body.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty().trim()
        val hasGreeting = GREETING_START.containsMatchIn(firstLine)
        val hasClosing = body.lines().any { CLOSING.containsMatchIn(it.trim()) }
        val salutation = input.salutation.trim().ifEmpty { salutationFor(input.recipient) }.let { if (it.endsWith(",")) it else "$it," }
        val content = buildString {
            if (!hasGreeting) append(salutation).append("\n\n")
            append(body)
            if (!hasClosing) {
                append("\n\n").append(input.closing.trim().ifEmpty { "Mit freundlichen Grüßen" })
                val sig = signature(input.sender)
                if (sig.isNotEmpty()) append("\n\n").append(sig.joinToString("  \n"))
            }
        }
        val letter = Letter(infoLines(input.sender, input.reference).joinToString("\n"), addressLines(input.recipient).joinToString("\n"))
        return Result(input.subject.trim(), content, letter)
    }

    /** Recognises the closing formula in a letter body (used by the layout to keep closing and signature together). */
    fun isClosing(text: String) = CLOSING.containsMatchIn(text.trim())
}
