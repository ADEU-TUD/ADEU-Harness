package io.github.thepytales.llmhub.data

import io.github.thepytales.llmhub.logFailure
import io.github.thepytales.llmhub.net.ChatResult
import io.github.thepytales.llmhub.net.StreamUpdate
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * The chat a request belongs to: with „Platzhalter je Chat“ every chat has its own numbering, so the server cannot
 * link a person across chats. Requests without it (tasks, ✨ helpers) use the shared list.
 */
class PrivacyScope(val id: String) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<PrivacyScope>
}

/** One stored pair of a real detail and its number, e.g. "Müller" ↔ [Name 4]; [alias]: another spelling ("MÜLLER"). */
@Serializable
data class Pseudonym(val value: String, val kind: PiiKind, val n: Int, val alias: Boolean = false) {
    val placeholder: String get() = "[${kind.label} $n]"
}

/**
 * Replaces personal details by placeholders before a request leaves the device and puts the real details back
 * into the answer (also into tool arguments, so documents and e-mails contain the real names – except for tools that
 * send their arguments to the internet). The pairs are kept on the device, so the same person has the same
 * placeholder in every request (and, with „Platzhalter je Chat“, within a chat).
 */
class Pseudonymizer(private val file: File, private val settings: () -> AppSettings) {
    private val json = Json { ignoreUnknownKeys = true }
    private val chatsFile = File(file.parentFile, file.nameWithoutExtension + "-chats.json")
    private var entries: List<Pseudonym> = load()
    private val scoped: LinkedHashMap<String, List<Pseudonym>> = loadScoped()
    private val patterns = HashMap<String, Regex>()

    val enabled: Boolean get() = settings().pseudonymize

    private fun off(): Set<PiiFeature> = settings().pseudonymOffFeatures
    fun on(f: PiiFeature): Boolean = f !in off()

    @Synchronized
    fun all(): List<Pseudonym> = entries

    /** Number of chats with their own placeholders. */
    @Synchronized
    fun chats(): Int = scoped.size

    @Synchronized
    fun clear() {
        entries = emptyList()
        scoped.clear()
        patterns.clear()
        save()
        saveScoped()
    }

    /** Forgets [value] and its other spellings (Einstellungen › Datenschutz › Zuordnungen). */
    @Synchronized
    fun remove(value: String) {
        val hit = entries.firstOrNull { it.value == value } ?: return
        entries = entries.filterNot { it.kind == hit.kind && it.n == hit.n }
        patterns.clear()
        save()
    }

    private fun load(): List<Pseudonym> = runCatching {
        AtomicFiles.read(file)?.let { json.decodeFromString(ListSerializer(Pseudonym.serializer()), it) } ?: emptyList()
    }.getOrElse { e ->
        AtomicFiles.quarantine(file, e)
        emptyList()
    }

    private fun loadScoped(): LinkedHashMap<String, List<Pseudonym>> = runCatching {
        AtomicFiles.read(chatsFile)?.let { LinkedHashMap(json.decodeFromString(SCOPED, it)) } ?: LinkedHashMap()
    }.getOrElse { e ->
        AtomicFiles.quarantine(chatsFile, e)
        LinkedHashMap()
    }

    private fun save() {
        runCatching { AtomicFiles.write(file, json.encodeToString(ListSerializer(Pseudonym.serializer()), entries)) }
            .logFailure("Pseudonyme speichern")
    }

    private fun saveScoped() {
        runCatching { AtomicFiles.write(chatsFile, json.encodeToString(SCOPED, scoped)) }.logFailure("Pseudonyme der Chats speichern")
    }

    private fun ownTerms(): List<String> = settings().pseudonymTerms.lines().map { it.trim() }.filter { it.length >= 2 }
    private fun ignored(): List<String> = settings().pseudonymIgnore.lines().map { it.trim() }.filter { it.isNotEmpty() }

    /** What [text] contains, with the switches of the settings. */
    fun detect(text: String): List<PiiHit> = PiiDetector.find(text, ownTerms(), settings().pseudonymAuto, off(), ignored())

    /** Key of the list for [scope]: the chat's own with „Platzhalter je Chat“, else the shared one (""). */
    private fun key(scope: String?): String = if (!scope.isNullOrBlank() && on(PiiFeature.PER_CHAT)) scope else ""

    private fun list(key: String): List<Pseudonym> = if (key.isEmpty()) entries else scoped[key].orEmpty()

    @Synchronized
    private fun register(hits: List<PiiHit>, key: String) {
        var list = list(key)
        val start = list.size
        val roles = on(PiiFeature.ROLE_PLACEHOLDERS)
        fun next(kind: PiiKind) = (list.filter { it.kind == kind }.maxOfOrNull { it.n } ?: 0) + 1
        for (h in hits) {
            if (h.value.isBlank() || list.any { it.value == h.value }) continue
            val kind = if (!roles && h.kind in PiiKind.ROLES) PiiKind.NAME else h.kind
            val canonical = h.canonical?.let { c ->
                list.firstOrNull { it.value == c } ?: Pseudonym(c, kind, next(kind)).also { list = list + it }
            }
            list = list + (canonical?.let { Pseudonym(h.value, it.kind, it.n, alias = true) } ?: Pseudonym(h.value, kind, next(kind)))
        }
        if (list.size == start) return
        patterns.keys.removeAll { it.startsWith("$key|") }
        if (key.isEmpty()) {
            entries = list
            save()
        } else {
            scoped.remove(key)
            scoped[key] = list
            while (scoped.size > MAX_CHATS) scoped.remove(scoped.keys.first())
            saveScoped()
        }
    }

    @Synchronized
    private fun regex(key: String): Regex? {
        val list = list(key)
        if (list.isEmpty()) return null
        val fileNames = on(PiiFeature.FILE_NAMES)
        val inflect = on(PiiFeature.INFLECTION)
        return patterns.getOrPut("$key|$fileNames|$inflect") { patternFor(list, fileNames, inflect) }
    }

    /**
     * Longest first, so "Anna Müller" wins over "Anna"; never inside a word or an existing placeholder. Group 1 is the
     * detail, group 2 the ending of an inflected name ("Annas" → „[Name 1]s“).
     */
    private fun patternFor(list: List<Pseudonym>, fileNames: Boolean, inflect: Boolean): Regex {
        val alternatives = list.map { it.value }.distinct().sortedByDescending { it.length }.joinToString("|") { Regex.escape(it) }
        // In file names an underscore separates words: "Elterngespräch_Mueller.docx".
        val b = if (fileNames) "A-Za-z0-9äöüÄÖÜßéèáàíóúçñ" else PiiDetector.LETTERS
        val suffix = if (inflect) "(${PiiDetector.SUFFIX})?" else "()"
        return Regex("(?<![$b@.\\[])($alternatives)$suffix(?![$b@])")
    }

    /** Replaces every known and newly found detail in [text]. */
    fun mask(text: String, scope: String? = null): String {
        if (text.isBlank()) return text
        val key = key(scope)
        register(detect(text), key)
        val r = regex(key) ?: return text
        val byValue = synchronized(this) { list(key).associateBy { it.value } }
        return r.replace(text) { m -> (byValue[m.groupValues[1]]?.placeholder ?: m.groupValues[1]) + m.groupValues[2] }
    }

    /** Text for the semantic search of the knowledge base (embeddings, reranking): with placeholders if that is on. */
    fun maskForSearch(text: String): String = if (enabled && on(PiiFeature.KNOWLEDGE)) mask(text) else text

    /** What the server would see for [text], without storing new pairs – for trying it out in the settings. */
    fun preview(text: String): String {
        if (text.isBlank()) return text
        val tentative = all().toMutableList()
        val roles = on(PiiFeature.ROLE_PLACEHOLDERS)
        fun next(kind: PiiKind) = (tentative.filter { it.kind == kind }.maxOfOrNull { it.n } ?: 0) + 1
        for (h in detect(text)) {
            if (tentative.any { it.value == h.value }) continue
            val kind = if (!roles && h.kind in PiiKind.ROLES) PiiKind.NAME else h.kind
            val canonical = h.canonical?.let { c -> tentative.firstOrNull { it.value == c } ?: Pseudonym(c, kind, next(kind)).also { tentative += it } }
            tentative += canonical?.let { Pseudonym(h.value, it.kind, it.n, alias = true) } ?: Pseudonym(h.value, kind, next(kind))
        }
        if (tentative.isEmpty()) return text
        val byValue = tentative.associateBy { it.value }
        return patternFor(tentative, on(PiiFeature.FILE_NAMES), on(PiiFeature.INFLECTION))
            .replace(text) { m -> (byValue[m.groupValues[1]]?.placeholder ?: m.groupValues[1]) + m.groupValues[2] }
    }

    /** Puts the real details back; placeholders the app did not make stay as they are. */
    fun unmask(text: String, scope: String? = null): String {
        if (text.isEmpty()) return text
        val byKey = synchronized(this) { list(key(scope)).filter { !it.alias }.associateBy { it.kind.name + ":" + it.n } }
        if (byKey.isEmpty()) return text
        fun real(label: String, number: String): String? {
            val kind = LABELS[label.lowercase()] ?: return null
            return byKey[kind.name + ":" + number.toIntOrNull()]?.value
        }
        val bracketed = if ('[' in text || '［' in text || '【' in text) PLACEHOLDER.replace(text) { m -> real(m.groupValues[1], m.groupValues[2]) ?: m.value } else text
        // Without brackets ("Name 3") only for labels that are no ordinary words with a number.
        return BARE.replace(bracketed) { m -> real(m.groupValues[1], m.groupValues[2]) ?: m.value }
    }

    /** The messages as they go to the server; the note for the model is added to the first system message. */
    fun maskMessages(messages: List<ChatMessage>, scope: String? = null): List<ChatMessage> {
        val masked = messages.map { m ->
            m.copy(
                content = mask(m.content, scope),
                reasoning = m.reasoning?.let { mask(it, scope) },
                instructions = m.instructions?.let { mask(it, scope) },
                files = m.files.map { f -> f.copy(text = mask(f.text, scope), name = mask(f.name, scope)) },
                toolCalls = m.toolCalls.map { tc -> tc.copy(arguments = mask(tc.arguments, scope)) },
            )
        }
        val note = modelNote()
        val i = masked.indexOfFirst { it.role == "system" }
        return if (i >= 0) masked.mapIndexed { idx, m -> if (idx == i) m.copy(content = m.content + "\n\n" + note) else m }
        else listOf(ChatMessage(role = "system", content = note)) + masked
    }

    private fun modelNote(): String = MODEL_NOTE + (if (on(PiiFeature.ROLE_PLACEHOLDERS)) " " + ROLE_NOTE else "") +
        (if (on(PiiFeature.EXTERNAL_TOOLS)) " " + EXTERNAL_NOTE else "")

    /** Tools whose arguments go to the internet keep the placeholders (if that is on). */
    private fun keepsPlaceholders(tool: String) = tool in EXTERNAL_TOOLS && on(PiiFeature.EXTERNAL_TOOLS)

    fun unmask(u: StreamUpdate, scope: String? = null) = StreamUpdate(
        unmask(u.content, scope), unmask(u.reasoning, scope),
        u.toolCalls.map { if (keepsPlaceholders(it.name)) it else it.copy(arguments = unmask(it.arguments, scope)) },
    )

    fun unmask(r: ChatResult, scope: String? = null) = r.copy(
        content = unmask(r.content, scope),
        reasoning = unmask(r.reasoning, scope),
        toolCalls = r.toolCalls.map { if (keepsPlaceholders(it.name)) it else it.copy(arguments = unmask(it.arguments, scope)) },
    )

    /** How many placeholders of each kind a masked [text] contains – for the agent protocol. */
    fun summary(text: String): String? {
        val counts = PLACEHOLDER.findAll(text).mapNotNull { m -> LABELS[m.groupValues[1].lowercase()] }.groupingBy { it }.eachCount()
        if (counts.isEmpty()) return null
        return counts.entries.joinToString(", ") { "${it.value} × ${it.key.label}" }
    }

    companion object {
        private const val MAX_CHATS = 400
        private val SCOPED = MapSerializer(String.serializer(), ListSerializer(Pseudonym.serializer()))

        /** Tools that send their arguments to servers on the internet (search engines, literature databases, GitHub). */
        val EXTERNAL_TOOLS = setOf(
            "web_search", "news_search", "fetch_url", "check_references", "search_literature", "literature_details", "snowball_literature",
            "journal_new_articles", "read_feed", "zotero_search", "zotero_add", "search_code", "write_file", "edit_file", "create_issue",
            "comment_issue", "create_pull_request", "create_branch",
        )

        /** Words the model may use for a placeholder label (also plural or spelled out), to the kind. */
        private val LABELS: Map<String, PiiKind> = buildMap {
            PiiKind.entries.forEach { put(it.label.lowercase(), it) }
            mapOf(
                PiiKind.NAME to listOf("Namen", "Person"),
                PiiKind.EMAIL to listOf("Email", "E-Mail-Adresse", "Mail", "E-mail"),
                PiiKind.PHONE to listOf("Tel.", "Telefonnummer", "Tel"),
                PiiKind.MATRIKEL to listOf("Matrikelnummer", "Matrikel-Nr.", "Matrikelnr", "Matr.-Nr."),
                PiiKind.ADDRESS to listOf("Anschrift", "Straße"),
                PiiKind.TERM to listOf("Begriffe"),
                PiiKind.IP to listOf("IP"),
                PiiKind.STUDENT to listOf("Schülerin", "Schüler", "Schüler:in", "Schüler*in", "SchülerIn"),
                PiiKind.UNI_STUDENT to listOf("Studierende", "Studierender", "Student/in", "Studentin", "Student"),
                PiiKind.COLLEAGUE to listOf("Kollegin", "Kollege", "Kolleg/in", "Kolleg:in"),
                PiiKind.TEACHER to listOf("Lehrerin", "Lehrer"),
                PiiKind.PARENT to listOf("Eltern", "Mutter", "Vater"),
            ).forEach { (kind, words) -> words.forEach { put(it.lowercase(), kind) } }
        }
        private val LABEL_PATTERN = LABELS.keys.sortedByDescending { it.length }.joinToString("|") { Regex.escape(it) }
        private val PLACEHOLDER = Regex("[\\[［【]\\s*($LABEL_PATTERN)\\s?(\\d+)\\s*[]］】]", RegexOption.IGNORE_CASE)
        private val BARE_LABELS = listOf(
            "Name", "E-Mail", "Telefon", "Matrikelnr.", "IBAN", "Geburtsdatum", "Adresse", "Kennzeichen", "IP-Adresse", "Schüler/in",
            "Studierende/r", "Lehrkraft", "Kollege/in", "Elternteil",
        ).joinToString("|") { Regex.escape(it) }
        private val BARE = Regex("(?<![\\p{L}\\[［【/-])($BARE_LABELS) (\\d+)(?![\\d\\]］】\\p{L}]|[.,]\\d)")

        const val MODEL_NOTE = "Datenschutz: Personenbezogene Angaben in diesem Chat sind durch Platzhalter in eckigen Klammern " +
            "ersetzt, z.B. [Name 1], [E-Mail 2], [Telefon 1], [Matrikelnr. 1], [Adresse 1]. Übernimm diese Platzhalter genau so " +
            "(mit Klammern und Nummer) in Antworten, Dokumente und Werkzeugaufrufe – die App setzt auf dem Gerät die echten " +
            "Angaben wieder ein. Frage nicht nach den echten Daten und erfinde keine. Anreden wie „Frau [Name 3]“ bleiben erhalten."
        private const val ROLE_NOTE = "Platzhalter wie [Schüler/in 2], [Lehrkraft 1] oder [Elternteil 1] stehen für eine Person mit dieser " +
            "Rolle; schreibe sie ebenso unverändert. Ein angehängtes „s“ ([Name 1]s) ist der Genitiv."
        private const val EXTERNAL_NOTE = "Websuche, Literatursuche und GitHub erhalten die Platzhalter, nicht die echten Angaben – " +
            "suche deshalb nicht nach Platzhaltern."
    }
}

/** Own lists of names for the pseudonymization: import from class lists, export for colleagues. */
object NameList {
    private val FIRST = Regex("(?i)^(?:vor-?name|vornamen|rufname|first name)$")
    private val LAST = Regex("(?i)^(?:nach-?name|nachnamen|familienname|last name|surname)$")
    private val FULL = Regex("(?i)^(?:name|namen|vollständiger name|schüler(?:in)?|schüler/?innen|schüler:innen|teilnehmer(?:in)?|teilnehmende|student(?:in)?|studierende|person|kind)$")
    private val BULLET = Regex("^\\s*(?:[-*•–]|\\d+[.)])\\s*")

    /**
     * Names in [text]: from a table with columns like Vorname/Nachname or Name ("Müller, Anna" becomes "Anna Müller"),
     * otherwise one name per line. Lines with numbers or long sentences are skipped.
     */
    fun parse(text: String): List<String> {
        val out = LinkedHashSet<String>()
        for (rows in PiiDetector.tables(text)) {
            val head = rows.firstOrNull()?.map { it.replace("*", "").trim() } ?: continue
            val first = head.indexOfFirst { FIRST.matches(it) }
            val last = head.indexOfFirst { LAST.matches(it) }
            val full = head.indexOfFirst { FULL.matches(it) }
            if (first < 0 && last < 0 && full < 0) continue
            rows.drop(1).forEach { r ->
                val name = when {
                    first >= 0 && last >= 0 -> "${r.getOrNull(first).orEmpty()} ${r.getOrNull(last).orEmpty()}"
                    full >= 0 -> r.getOrNull(full).orEmpty().let { c -> if (',' in c) c.substringAfter(',').trim() + " " + c.substringBefore(',').trim() else c }
                    else -> r.getOrNull(maxOf(first, last)).orEmpty()
                }
                clean(name)?.let { out += it }
            }
        }
        if (out.isNotEmpty()) return out.toList()
        text.lines().forEach { l -> clean(BULLET.replace(l, ""))?.let { out += it } }
        return out.toList()
    }

    private fun clean(s: String): String? {
        val t = s.replace(Regex("\\s+"), " ").trim().trim('"', ';', ',')
        if (t.length < 2 || t.length > 60 || t.any { it.isDigit() } || t.split(' ').size > 5 || !t.first().isLetter()) return null
        return t
    }

    /** [current] with [added] at the end, without duplicates (ignoring case). */
    fun merge(current: String, added: List<String>): String {
        val lines = current.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val known = lines.map { it.lowercase() }.toMutableSet()
        return (lines + added.filter { known.add(it.lowercase()) }).joinToString("\n")
    }
}
