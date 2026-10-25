package io.github.thepytales.llmhub.data

import io.github.thepytales.llmhub.logFailure
import io.github.thepytales.llmhub.net.ChatResult
import io.github.thepytales.llmhub.net.StreamUpdate
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

/** Kind of a personal detail; [label] is the word in the placeholder, e.g. „[Name 3]“. */
enum class PiiKind(val label: String, val title: String) {
    NAME("Name", "Namen"),
    TERM("Begriff", "eigene Begriffe"),
    EMAIL("E-Mail", "E-Mail-Adressen"),
    PHONE("Telefon", "Telefonnummern"),
    MATRIKEL("Matrikelnr.", "Matrikelnummern"),
    IBAN("IBAN", "Kontonummern"),
    BIRTHDATE("Geburtsdatum", "Geburtsdaten"),
    ADDRESS("Adresse", "Anschriften"),
}

/** A personal detail found in a text: [value] is exactly the text that gets replaced. */
data class PiiHit(val value: String, val kind: PiiKind)

/**
 * Finds personal details in a text, only with fixed rules on the device (no model involved): e-mail addresses,
 * phone numbers, matriculation numbers, IBANs, birth dates, street addresses, names after a form of address
 * (Frau, Herr, Dr., Prof.), frequent first names (with a following frequent surname) and every name or term of
 * the user's own list. Names without such a clue are not found – that is what the own list is for.
 */
object PiiDetector {
    private const val UPPER = "A-ZÄÖÜ"
    private const val LOWER = "a-zäöüßéèáàíóúçñ"
    private const val WORD = "[$UPPER][$LOWER]+(?:-[$UPPER][$LOWER]+)?"

    private val EMAIL = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")
    private val PHONE = Regex("(?<![\\w+/.,])(?:\\+\\d{1,3}[ /-]?(?:\\(0\\)[ ]?)?|0)\\(?\\d{2,5}\\)?(?:[ /-]?\\d{2,}){1,4}(?![\\w.,]\\d)")
    private val MATRIKEL = Regex("(?i)(?:matrikel(?:nummer|-?nr\\.?)?|matr\\.?-?nr\\.?|mat\\.?-?nr\\.?|immatrikulationsnummer)\\s*[:.]?\\s*(\\d{5,9})\\b")
    private val IBAN = Regex("\\b[A-Z]{2}\\d{2}(?: ?[0-9A-Z]{4}){3,7}(?: ?[0-9A-Z]{1,3})?\\b")
    private val BIRTHDATE = Regex("(?i)(?:geb(?:oren|\\.)?(?:\\s+am)?|geburtsdatum|geburtstag)\\s*[:.]?\\s*(\\d{1,2}\\.\\s?\\d{1,2}\\.\\s?\\d{2,4})")
    private val STREET = Regex(
        "\\b(?:[$UPPER][$LOWER-]*(?:straße|strasse|str\\.|weg|gasse|allee|platz|ring|damm|ufer|chaussee|steig|pfad)" +
            "|[$UPPER][$LOWER]+(?:er|e|es)? (?:Straße|Str\\.|Weg|Gasse|Allee|Platz|Ring|Damm|Ufer|Chaussee|Markt))" +
            "\\s+\\d{1,4}(?:\\s?[a-z](?![$LOWER]))?\\b"
    )

    /** Form of address with academic titles, then up to three words of the name. */
    private val ADDRESSED = Regex(
        "\\b(?:Frau|Herrn?|Hr\\.|Fr\\.|Prof\\.|Dr\\.|Professorin|Professor)\\s+" +
            "(?:(?:Prof\\.|Dr\\.|Dipl\\.-[A-Za-z]+\\.?|med\\.|phil\\.|rer\\.\\s?nat\\.|paed\\.|habil\\.|PD|apl\\.|h\\.\\s?c\\.|mult\\.)\\s*)*" +
            "($WORD(?:\\s+(?:von|van|de|zu|vom)\\s+$WORD|\\s+$WORD){0,2})"
    )
    private val NAME_WORD = Regex("(?<![\\wäöüÄÖÜß-])$WORD(?![\\wäöüÄÖÜß-])")
    private val PARTICLES = setOf("von", "van", "de", "zu", "vom")

    fun find(text: String, ownTerms: List<String> = emptyList(), automatic: Boolean = true): List<PiiHit> {
        if (text.isBlank()) return emptyList()
        val hits = LinkedHashSet<PiiHit>()
        for (term in ownTerms) {
            val t = term.trim()
            if (t.length < 2 || !Regex("(?<![\\w])${Regex.escape(t)}(?![\\w])").containsMatchIn(text)) continue
            val words = personWords(t)
            if (words != null) {
                if (words.size > 1) hits += PiiHit(t, PiiKind.NAME)
                words.forEach { hits += PiiHit(it, PiiKind.NAME) }
            } else hits += PiiHit(t, PiiKind.TERM)
        }
        // Single words of own names also count on their own ("Lukas" after "Lukas Schmidt" is on the list).
        for (term in ownTerms) personWords(term.trim())?.forEach { w ->
            if (Regex("(?<![\\w])${Regex.escape(w)}(?![\\w])").containsMatchIn(text)) hits += PiiHit(w, PiiKind.NAME)
        }
        EMAIL.findAll(text).forEach { hits += PiiHit(it.value, PiiKind.EMAIL) }
        MATRIKEL.findAll(text).forEach { hits += PiiHit(it.groupValues[1], PiiKind.MATRIKEL) }
        IBAN.findAll(text).forEach { m -> if (m.value.count { it.isDigit() } >= 12) hits += PiiHit(m.value, PiiKind.IBAN) }
        BIRTHDATE.findAll(text).forEach { hits += PiiHit(it.groupValues[1], PiiKind.BIRTHDATE) }
        PHONE.findAll(text).forEach { m ->
            val digits = m.value.count { it.isDigit() }
            if (digits in 7..15 && hits.none { h -> m.value in h.value }) hits += PiiHit(m.value.trim(), PiiKind.PHONE)
        }
        STREET.findAll(text).forEach { hits += PiiHit(it.value, PiiKind.ADDRESS) }
        if (!automatic) return hits.toList()
        ADDRESSED.findAll(text).forEach { m ->
            val words = m.groupValues[1].split(Regex("\\s+")).filter { it !in PARTICLES }
            // "Frau Anna Müller": the second word belongs to the name if the first is a first name or the second a surname;
            // "Frau Müller Bescheid geben" keeps "Bescheid".
            val take = mutableListOf(words.first())
            for (i in 1 until words.size) {
                if (words[i - 1] in Names.first || words[i] in Names.last) take += words[i] else break
            }
            take.filter { it !in NOT_NAMES }.forEach { hits += PiiHit(it, PiiKind.NAME) }
        }
        // A frequent first name, and the names directly after it ("Anna Lena Müller").
        val words = NAME_WORD.findAll(text).toList()
        var chain = false
        for ((i, w) in words.withIndex()) {
            val adjacent = i > 0 && text.substring(words[i - 1].range.last + 1, w.range.first).let { it.isNotEmpty() && it.isBlank() && '\n' !in it }
            chain = when {
                w.value in Names.first -> true
                chain && adjacent && w.value in Names.last -> true
                else -> false
            }
            if (chain) hits += PiiHit(w.value, PiiKind.NAME)
        }
        return hits.toList()
    }

    /** The words of an own entry that looks like a person ("Lukas Schmidt"), else null (e.g. "Gymnasium Dresden-Plauen"). */
    private fun personWords(term: String): List<String>? {
        val words = term.split(Regex("\\s+")).filter { it.isNotEmpty() && it !in PARTICLES }
        if (words.isEmpty() || words.size > 4 || term.any { it.isDigit() }) return null
        if (words.any { !Regex(WORD).matches(it) || it in INSTITUTION_WORDS }) return null
        return words.filter { it.length >= 2 }
    }

    /** Words that look like names after "Frau"/"Herr" but are not ("Herr Präsident", "Frau Kollegin"). */
    private val NOT_NAMES = setOf(
        "Kollegin", "Kollege", "Kollegen", "Präsident", "Präsidentin", "Dekan", "Dekanin", "Rektor", "Rektorin", "Direktor",
        "Direktorin", "Professorin", "Professor", "Doktor", "Ministerin", "Minister", "Bürgermeister", "Bürgermeisterin",
        "Vorsitzende", "Vorsitzender", "Studiendekan", "Studiendekanin", "Prof", "Dr", "Lehrerin", "Lehrer",
    )

    private val INSTITUTION_WORDS = setOf(
        "Schule", "Grundschule", "Oberschule", "Gymnasium", "Gesamtschule", "Förderschule", "Berufsschule", "Hochschule",
        "Universität", "Institut", "Fakultät", "Professur", "Klasse", "Kita", "Kindergarten", "Hort", "Amt", "Schulamt",
        "Landesamt", "Ministerium", "Verein", "Stiftung", "Gmbh", "GmbH", "Zentrum", "Seminar", "Projekt", "Firma",
    )
}

/** One stored pair of a real detail and its number, e.g. "Müller" ↔ [Name 4]. */
@Serializable
data class Pseudonym(val value: String, val kind: PiiKind, val n: Int) {
    val placeholder: String get() = "[${kind.label} $n]"
}

/**
 * Replaces personal details by placeholders before a request leaves the device and puts the real details back
 * into the answer (also into tool arguments, so documents and e-mails contain the real names). The pairs are
 * kept on the device, so the same person has the same placeholder in every request and chat.
 */
class Pseudonymizer(private val file: File, private val settings: () -> AppSettings) {
    private val json = Json { ignoreUnknownKeys = true }
    private var entries: List<Pseudonym> = load()
    private var pattern: Regex? = null

    val enabled: Boolean get() = settings().pseudonymize

    @Synchronized
    fun all(): List<Pseudonym> = entries

    @Synchronized
    fun clear() {
        entries = emptyList()
        pattern = null
        save()
    }

    private fun load(): List<Pseudonym> = runCatching {
        AtomicFiles.read(file)?.let { json.decodeFromString(ListSerializer(Pseudonym.serializer()), it) } ?: emptyList()
    }.getOrElse { e ->
        AtomicFiles.quarantine(file, e)
        emptyList()
    }

    private fun save() {
        runCatching { AtomicFiles.write(file, json.encodeToString(ListSerializer(Pseudonym.serializer()), entries)) }
            .logFailure("Pseudonyme speichern")
    }

    private fun ownTerms(): List<String> = settings().pseudonymTerms.lines().map { it.trim() }.filter { it.length >= 2 }

    @Synchronized
    private fun register(hits: List<PiiHit>) {
        var changed = false
        for (h in hits) {
            if (h.value.isBlank() || entries.any { it.value == h.value }) continue
            val n = entries.count { it.kind == h.kind } + 1
            entries = entries + Pseudonym(h.value, h.kind, n)
            changed = true
        }
        if (changed) {
            pattern = null
            save()
        }
    }

    @Synchronized
    private fun regex(): Regex? {
        if (entries.isEmpty()) return null
        pattern?.let { return it }
        return patternFor(entries).also { pattern = it }
    }

    /** Longest first, so "Anna Müller" wins over "Anna"; never inside a word or an existing placeholder. */
    private fun patternFor(list: List<Pseudonym>): Regex {
        val alternatives = list.map { it.value }.distinct().sortedByDescending { it.length }.joinToString("|") { Regex.escape(it) }
        return Regex("(?<![\\wäöüÄÖÜß@.\\[])(?:$alternatives)(?![\\wäöüÄÖÜß@])")
    }

    /** Replaces every known and newly found detail in [text]. */
    fun mask(text: String): String {
        if (text.isBlank()) return text
        register(PiiDetector.find(text, ownTerms(), settings().pseudonymAuto))
        val r = regex() ?: return text
        val byValue = synchronized(this) { entries.associateBy { it.value } }
        return r.replace(text) { m -> byValue[m.value]?.placeholder ?: m.value }
    }

    /** What the server would see for [text], without storing new pairs – for trying it out in the settings. */
    fun preview(text: String): String {
        if (text.isBlank()) return text
        val known = all()
        val tentative = known.toMutableList()
        for (h in PiiDetector.find(text, ownTerms(), settings().pseudonymAuto)) {
            if (tentative.none { it.value == h.value }) tentative += Pseudonym(h.value, h.kind, tentative.count { it.kind == h.kind } + 1)
        }
        if (tentative.isEmpty()) return text
        val byValue = tentative.associateBy { it.value }
        return patternFor(tentative).replace(text) { m -> byValue[m.value]?.placeholder ?: m.value }
    }

    /** Puts the real details back; placeholders the app did not make stay as they are. */
    fun unmask(text: String): String {
        if ('[' !in text) return text
        val byKey = synchronized(this) { entries.associateBy { it.kind.name + ":" + it.n } }
        if (byKey.isEmpty()) return text
        return PLACEHOLDER.replace(text) { m ->
            val kind = PiiKind.entries.firstOrNull { it.label.equals(m.groupValues[1], ignoreCase = true) }
            val n = m.groupValues[2].toIntOrNull()
            kind?.let { k -> n?.let { byKey[k.name + ":" + it]?.value } } ?: m.value
        }
    }

    /** The messages as they go to the server; the note for the model is added to the first system message. */
    fun maskMessages(messages: List<ChatMessage>): List<ChatMessage> {
        val masked = messages.map { m ->
            m.copy(
                content = mask(m.content),
                reasoning = m.reasoning?.let { mask(it) },
                instructions = m.instructions?.let { mask(it) },
                files = m.files.map { f -> f.copy(text = mask(f.text), name = mask(f.name)) },
                toolCalls = m.toolCalls.map { tc -> tc.copy(arguments = mask(tc.arguments)) },
            )
        }
        val i = masked.indexOfFirst { it.role == "system" }
        return if (i >= 0) masked.mapIndexed { idx, m -> if (idx == i) m.copy(content = m.content + "\n\n" + MODEL_NOTE) else m }
        else listOf(ChatMessage(role = "system", content = MODEL_NOTE)) + masked
    }

    fun unmask(u: StreamUpdate) = StreamUpdate(unmask(u.content), unmask(u.reasoning), u.toolCalls.map { it.copy(arguments = unmask(it.arguments)) })

    fun unmask(r: ChatResult) = r.copy(
        content = unmask(r.content),
        reasoning = unmask(r.reasoning),
        toolCalls = r.toolCalls.map { it.copy(arguments = unmask(it.arguments)) },
    )

    /** How many placeholders of each kind a masked [text] contains – for the agent protocol. */
    fun summary(text: String): String? {
        val counts = PLACEHOLDER.findAll(text).mapNotNull { m -> PiiKind.entries.firstOrNull { it.label == m.groupValues[1] } }
            .groupingBy { it }.eachCount()
        if (counts.isEmpty()) return null
        return counts.entries.joinToString(", ") { "${it.value} × ${it.key.label}" }
    }

    companion object {
        private val PLACEHOLDER = Regex("\\[(Name|Begriff|E-Mail|Telefon|Matrikelnr\\.|IBAN|Geburtsdatum|Adresse) ?(\\d+)]", RegexOption.IGNORE_CASE)

        const val MODEL_NOTE = "Datenschutz: Personenbezogene Angaben in diesem Chat sind durch Platzhalter in eckigen Klammern " +
            "ersetzt, z.B. [Name 1], [E-Mail 2], [Telefon 1], [Matrikelnr. 1], [Adresse 1]. Übernimm diese Platzhalter genau so " +
            "(mit Klammern und Nummer) in Antworten, Dokumente und Werkzeugaufrufe – die App setzt auf dem Gerät die echten " +
            "Angaben wieder ein. Frage nicht nach den echten Daten und erfinde keine. Anreden wie „Frau [Name 3]“ bleiben erhalten."
    }
}

/** Frequent first names and surnames; surnames only count next to a first name or a form of address. */
object Names {
    val first: Set<String> = """
        Anna Emma Mia Hanna Hannah Lea Leah Lena Lina Leni Mila Marie Maria Clara Klara Sophie Sophia Sofia Luisa Luise Frieda
        Greta Emilia Amelie Charlotte Johanna Lotta Nele Paula Maja Maya Mathilda Matilda Leonie Laura Lara Sarah Sara Julia
        Lisa Katharina Jana Nina Elena Helena Victoria Viktoria Ella Ida Pia Merle Thea Alina Annika Carla Carlotta Celina
        Emely Emily Finja Fiona Isabell Isabella Jasmin Josephine Kim Lia Lilly Lili Lilli Magdalena Marlene Mira Nora Olivia
        Rosalie Stella Tessa Valentina Vanessa Wilma Zoe Ronja Malia Elif Zeynep Ayse Ayşe Fatma Leyla Amira Aylin Mariam Lilith
        Sabine Petra Claudia Susanne Andrea Monika Birgit Kerstin Heike Anja Katrin Kathrin Nicole Stefanie Silke Ines Ulrike
        Christine Christina Barbara Gabriele Ute Doris Karin Renate Ursula Sandra Melanie Daniela Tanja Simone Jessica Jennifer
        Franziska Juliane Antje Annett Anett Peggy Mandy Romy Anke Steffi Martina Manuela Cornelia Elke Bettina Beate Dagmar
        Angelika Brigitte Elisabeth Eva Ingrid Irene Jutta Marion Regina Sigrid Sylvia Silvia Uta Veronika Yvonne Diana Janine
        Jacqueline Josefine Theresa Teresa Lucia Luzia Hedwig Margarete Gisela Erika Martha Marta Linda Anne Annette Constanze
        Noah Leon Paul Ben Finn Fynn Elias Felix Luca Luka Lukas Lucas Henry Henri Emil Theo Jonas Louis Luis Anton Max Liam
        Matteo Oskar Oscar Moritz Jakob Jacob Leo Tim Tom Niklas Nicklas Julian David Samuel Alexander Maximilian Philipp
        Simon Fabian Florian Sebastian Daniel Michael Thomas Andreas Stefan Stephan Steffen Christian Martin Peter Markus
        Marcus Matthias Mathias Tobias Johannes Jonathan Benjamin Erik Eric Ole Mats Milan Karl Carl Friedrich Konstantin
        Vincent Valentin Rafael Raphael Levi Ali Mohammed Muhammad Mohamed Yusuf Emir Ahmet Mehmet Mustafa Omar Hamza Ibrahim
        Jürgen Uwe Ralf Ralph Dirk Jörg Torsten Thorsten Holger Frank Bernd Wolfgang Klaus Claus Dieter Heinz Rainer Gerd Horst
        Manfred Werner Günter Günther Helmut Hans Lars Sven Kevin Dennis Marcel Patrick Robert René Rene Jens Mirko Ronny Rico
        Enrico Toni Gustav Arne Bastian Malte Hannes Till Nils Niels Lennart Lennard Leonard Linus Mattis Joshua Aaron Adam
        Elijah Jannik Yannick Kilian Malik Marlon Nico Niko Pascal Robin Ruben Fritz Hugo Wilhelm Otto Georg Heinrich Hermann
        Ludwig Ulrich Volker Detlef Hartmut Reinhard Norbert Joachim Gerhard Bernhard Rüdiger Axel Olaf Carsten Kai-Uwe Ingo
        Jan-Niklas Marco Mario Oliver Ricardo Sascha Timo Tino Tilo Thilo Udo Vladimir Wladimir Dmitri Igor Sergej Piotr
    """.trim().split(Regex("\\s+")).toSet()

    val last: Set<String> = """
        Müller Schmidt Schneider Fischer Weber Meyer Meier Mayer Maier Wagner Becker Schulz Schulze Hoffmann Schäfer Koch Bauer
        Richter Klein Wolf Schröder Neumann Schwarz Zimmermann Braun Krüger Hofmann Hartmann Lange Schmitt Werner Schmitz
        Krause Lehmann Schmid Köhler Herrmann König Walter Huber Kaiser Fuchs Peters Lang Scholz Möller Weiß Jung Hahn Schubert
        Vogel Friedrich Keller Günther Frank Berger Winkler Roth Beck Lorenz Baumann Franke Albrecht Schuster Simon Ludwig Böhm
        Winter Kraus Martin Schumacher Krämer Vogt Stein Jäger Otto Sommer Groß Seidel Heinrich Brandt Haas Schreiber Graf
        Schulte Dietrich Ziegler Kuhn Kühn Pohl Engel Horn Busch Bergmann Thomas Voigt Sauer Arnold Wolff Pfeiffer Yilmaz Kaya
        Demir Şahin Sahin Nguyen Kowalski Nowak Lindner Ernst Hübner Kretschmer Kretzschmar Ullrich Ulbrich Wendt Hennig Göbel
    """.trim().split(Regex("\\s+")).toSet()
}
