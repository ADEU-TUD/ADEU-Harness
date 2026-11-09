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
class Pseudonymizer(
    private val file: File,
    /** Names that are public and never replaced, e.g. the authors of the literature collection (AP-03). */
    private val publicNames: () -> Collection<String> = { emptyList() },
    private val userSettings: () -> AppSettings,
) {
    /** The settings of the user – or the strict ones inside the vault (see [STRICT]). */
    private fun settings(): AppSettings = userSettings().let {
        when {
            STRICT.get() == true -> strict(it)
            // Stapel (AP-20): pseudonymized by default, even when it is switched off for chats.
            FORCE.get() == true -> it.copy(pseudonymize = true)
            else -> it
        }
    }

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
        entries.firstOrNull { it.value == value }?.let { hit ->
            entries = entries.filterNot { it.kind == hit.kind && it.n == hit.n }
            save()
        }
        // The chats' own lists too, so a correction („nicht ersetzen“) holds everywhere.
        var changed = false
        for ((k, list) in scoped.entries.toList()) {
            val hit = list.firstOrNull { it.value == value } ?: continue
            scoped[k] = list.filterNot { it.kind == hit.kind && it.n == hit.n }
            changed = true
        }
        if (changed) saveScoped()
        patterns.clear()
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

    /**
     * Whether the rules guess names from frequent first names ([PiiDetector.find], chains). Off once the recognition
     * model (AP-67) finds the names – it tells „Käthe Kollwitz“ in an exhibition from a pupil.
     */
    @Volatile
    var nameChains: Boolean = true

    /**
     * The recognition model (AP-67, [PiiModel.find]): the names, institutions and places it finds in a text, or null while
     * it is not there (not downloaded, switched off, still loading) – then the rules guess names as before. With the
     * model the rules keep their clear clues and the fixed patterns, the model replaces their guesses from first names.
     */
    @Volatile
    var model: ((String) -> List<PiiHit>?)? = null

    /** Called whenever messages are pseudonymized – the app offers the download of the model then (once). */
    @Volatile
    var onUse: (() -> Unit)? = null

    /** What [text] contains, with the switches of the settings; public names (authors, „Nie ersetzen“) are left out. */
    fun detect(text: String): List<PiiHit> {
        val ex = exemptions(text)
        val never = ignored().map { it.lowercase() }.toSet()
        val auto = settings().pseudonymAuto
        val found = if (auto) model?.invoke(text)?.filter { it.kind == PiiKind.NAME || on(PiiFeature.INSTITUTIONS) } else null
        val rules = PiiDetector.find(text, ownTerms(), auto, off(), ignored() + ex.names, nameChains && found == null)
        val hits = if (found.isNullOrEmpty()) rules else rules + found.filter { m -> rules.none { it.value == m.value } }
        return hits.filterNot { h ->
            exempt(h.value, ex) ||
                // "Jolina Kretzschmann" with „Kretzschmann“ on the „Nie ersetzen“ list: only „Jolina“ is replaced.
                h.kind.person && ' ' in h.value && h.value.lowercase() !in ex.own && h.value.lowercase().split(' ').any { it in never }
        }
    }

    /**
     * What stays as it is in [text] (AP-03): the „Nie ersetzen“ list, and with „Öffentliche Quellen“ the authors of the
     * literature collection and the authors cited in the text. The own list always wins.
     */
    class Exemptions(val names: Set<String>, val surnames: Set<String>, val own: Set<String>)

    private fun exemptions(text: String): Exemptions {
        val s = settings()
        val names = LinkedHashSet<String>()
        if (s.pseudonymPublic) {
            names += PiiDetector.citedAuthors(text)
            publicNames().forEach { n -> n.trim().takeIf { it.length >= 2 }?.let { names += it } }
        }
        val surnames = names.map { it.substringAfterLast(' ') }.filter { it.length >= 3 }.toSet()
        val own = ownTerms().flatMap { t -> listOf(t) + t.split(Regex("\\s+")) }.map { it.lowercase() }.toSet()
        return Exemptions(names.map { it.lowercase() }.toSet(), surnames.map { it.lowercase() }.toSet(), own)
    }

    /**
     * Whether [value] is public: on the „Nie ersetzen“ list, a known author, or a surname of one („Müller“ for
     * „Müller (2020)“); a full name only if first name or initial fit („A. Müller“, „Anna Müller“ with „Müller, A.“).
     */
    private fun exempt(value: String, ex: Exemptions): Boolean {
        val v = value.lowercase()
        if (v in ex.own) return false
        if (ignored().any { it.lowercase() == v }) return true
        if (v in ex.names) return true
        val words = v.split(' ').filter { it.isNotEmpty() }
        if (words.size == 1) return v in ex.surnames
        val last = words.last()
        if (last !in ex.surnames) return false
        val initial = words.first().first()
        return ex.names.any { n -> n.endsWith(" $last") && n.first() == initial }
    }

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
        // A detail stored earlier ("Müller" as a parent) stays where it is public in this text ("Müller (2020)").
        val ex = exemptions(text)
        return r.replace(text) { m ->
            val v = m.groupValues[1]
            if (exempt(v, ex)) m.value else (byValue[v]?.placeholder ?: v) + m.groupValues[2]
        }
    }

    /** The details [mask] would replace in [text] with their placeholders (without storing new ones) – for corrections. */
    fun replacements(text: String, scope: String? = null): List<Pair<String, String>> {
        if (text.isBlank() || !enabled) return emptyList()
        val key = key(scope)
        val known = synchronized(this) { list(key).associateBy { it.value } }
        val ex = exemptions(text)
        val found = LinkedHashMap<String, String>()
        for (h in detect(text)) found[h.canonical ?: h.value] = known[h.canonical ?: h.value]?.placeholder ?: "[${h.kind.label}]"
        regex(key)?.findAll(text)?.forEach { m ->
            val v = m.groupValues[1]
            val p = known[v] ?: return@forEach
            if (!exempt(v, ex) && !p.alias) found.putIfAbsent(v, p.placeholder)
        }
        return found.entries.map { it.key to it.value }
    }

    /** Words in [text] that might be names but are not replaced – „auch ersetzen“ in the corrections. */
    fun missed(text: String): List<String> {
        if (text.isBlank() || !enabled) return emptyList()
        val known = detect(text).map { it.value } + ownTerms().flatMap { it.split(' ') }
        return PiiDetector.suspects(text, known, ignored())
    }

    /** One click in the preview or the agent protocol: [value] is never replaced again (AP-03). */
    fun neverReplace(value: String, update: ((AppSettings) -> AppSettings) -> Unit) {
        update { it.copy(pseudonymIgnore = NameList.merge(it.pseudonymIgnore, listOf(value)), pseudonymTerms = NameList.remove(it.pseudonymTerms, value)) }
        remove(value)
    }

    /** One click: [value] is always replaced from now on. */
    fun alwaysReplace(value: String, update: ((AppSettings) -> AppSettings) -> Unit) {
        update { it.copy(pseudonymTerms = NameList.merge(it.pseudonymTerms, listOf(value)), pseudonymIgnore = NameList.remove(it.pseudonymIgnore, value)) }
        patterns.clear()
    }

    /**
     * Thorough recognition (Einstellungen › Datenschutz › Gründlich, AP-03): a language model on this device lists the
     * names, schools and places of a text that the rules missed. Set by the app; null when no local model is installed.
     */
    var thoroughFinder: (suspend (String) -> List<String>)? = null

    /** Texts the local model has already looked at (hash → found names), so a long chat is not read again and again. */
    private val thoroughSeen = object : LinkedHashMap<Int, List<String>>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, List<String>>?) = size > 400
    }

    /**
     * Before [maskMessages]: with „Gründlich“ the local model reads the texts of [messages] once and the names it finds
     * are registered like found ones. Does nothing when it is off or no local model is there.
     */
    suspend fun prepareThorough(messages: List<ChatMessage>, scope: String? = null) {
        if (!enabled || !settings().pseudonymThorough) return
        val finder = thoroughFinder ?: return
        val public = settings().pseudonymPublic
        val texts = messages.flatMap { m ->
            if (m.role == "system" || public && m.role == "tool" && m.toolName in PUBLIC_TOOLS) emptyList()
            else listOf(m.content) + m.files.filter { !(public && isPublic(it)) }.map { it.text }
        }.filter { it.isNotBlank() }
        for (text in texts) {
            val h = text.hashCode()
            val found = synchronized(this) { thoroughSeen[h] } ?: runCatching { finder(text.take(12_000)) }.getOrDefault(emptyList()).also {
                synchronized(this) { thoroughSeen[h] = it }
            }
            val ex = exemptions(text)
            val hits = found.map { it.trim() }.filter { it.length >= 2 && PiiDetector.containsWord(text, it, false) && !exempt(it, ex) }
                .map { PiiHit(it, if (PiiDetector.looksLikeSchool(it)) PiiKind.SCHOOL else PiiKind.NAME) }
            if (hits.isNotEmpty()) register(hits, key(scope))
        }
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
        onUse?.invoke()
        val public = settings().pseudonymPublic
        val masked = messages.map { m ->
            // Results of the web and literature search are public (AP-03): authors and people in the news stay as they are.
            if (public && m.role == "tool" && m.toolName in PUBLIC_TOOLS) return@map m
            m.copy(
                content = mask(m.content, scope),
                reasoning = m.reasoning?.let { mask(it, scope) },
                instructions = m.instructions?.let { mask(it, scope) },
                files = m.files.map { f -> if (public && isPublic(f)) f else f.copy(text = mask(f.text, scope), name = mask(f.name, scope)) },
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
        /**
         * Strenge Pseudonymisierung (AP-10): set for the requests of a vault chat (as coroutine context element, see
         * AgentRunner): always on, every part on, no exemption for public sources, thorough recognition with the local model.
         */
        val STRICT: ThreadLocal<Boolean> = ThreadLocal()

        /** Pseudonymization on for these requests whatever the settings say (a batch with „pseudonymisiert“, AP-20). */
        val FORCE: ThreadLocal<Boolean> = ThreadLocal()

        fun strict(s: AppSettings): AppSettings = s.copy(pseudonymize = true, pseudonymOff = "", pseudonymPublic = false, pseudonymThorough = true)
        private const val MAX_CHATS = 400

        /** Tools whose results are public sources (web, news, literature databases) and are sent as they are. */
        val PUBLIC_TOOLS = setOf(
            "web_search", "news_search", "fetch_url", "search_literature", "literature_details", "snowball_literature",
            "journal_new_articles", "read_feed", "zotero_search", "check_references",
        )

        /** Task of the local model in the thorough recognition. */
        const val THOROUGH_PROMPT = "Du findest personenbezogene Angaben in deutschen Texten. Liste alle Namen privater Personen " +
            "(Vor- und Nachnamen, auch ohne Anrede), Namen von Schulen, Kitas und Einrichtungen sowie Orte, die eine Person " +
            "erkennbar machen. Nicht aufnehmen: Autorinnen und Autoren zitierter Literatur, bekannte öffentliche Personen, " +
            "Platzhalter in eckigen Klammern. Antworte nur mit einem JSON-Array von Zeichenketten, genau so geschrieben wie im " +
            "Text, z.B. [\"Jolina Kretzschmann\", \"Grundschule am Park\"]. Ohne Funde: []."

        /** The names of the local model's answer (a JSON array, also inside a code block or with text around it). */
        fun parseThorough(answer: String): List<String> {
            val start = answer.indexOf('[')
            val end = answer.lastIndexOf(']')
            if (start < 0 || end <= start) return emptyList()
            return runCatching {
                Json.parseToJsonElement(answer.substring(start, end + 1)).let { it as kotlinx.serialization.json.JsonArray }
                    .mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.content?.trim() }
                    .filter { it.length in 2..80 && !it.startsWith("[") }
            }.getOrDefault(emptyList())
        }

        /** An attachment marked „öffentlich“, or – unmarked – a book or paper (DOI, ISBN, ISSN). */
        fun isPublic(f: FileAttachment): Boolean = f.public ?: PiiDetector.looksPublic(f.text)
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

    /** [current] without [value] (ignoring case). */
    fun remove(current: String, value: String): String =
        current.lines().filter { it.trim().isNotEmpty() && !it.trim().equals(value.trim(), ignoreCase = true) }.joinToString("\n")

    /** [current] with [added] at the end, without duplicates (ignoring case). */
    fun merge(current: String, added: List<String>): String {
        val lines = current.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val known = lines.map { it.lowercase() }.toMutableSet()
        return (lines + added.filter { known.add(it.lowercase()) }).joinToString("\n")
    }
}
