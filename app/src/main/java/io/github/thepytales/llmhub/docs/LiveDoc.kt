package io.github.thepytales.llmhub.docs

import io.github.thepytales.llmhub.data.AtomicFiles
import io.github.thepytales.llmhub.logFailure
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/** A tab of a document, like a section of a notebook; exported one after another or on its own. */
@Serializable
data class DocTab(val id: String = UUID.randomUUID().toString(), val title: String = "Tab 1", val markdown: String = "")

@Serializable
data class DocReply(val author: String, val text: String, val createdAt: Long = System.currentTimeMillis(), val ai: Boolean = false)

/** A comment on a passage ([quote]) of a tab; with [toAi] the model answers in the thread and makes the change. */
@Serializable
data class DocComment(
    val id: String = UUID.randomUUID().toString().take(8),
    val tabId: String,
    /** The commented text (a block or a selection), to find the place again after edits. */
    val quote: String,
    val text: String,
    val author: String = "Sie",
    val toAi: Boolean = false,
    val resolved: Boolean = false,
    val replies: List<DocReply> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
)

/** A saved state of the whole document; every change is attributed to the person or the model that made it. */
@Serializable
data class DocVersion(
    val id: String = UUID.randomUUID().toString(),
    val createdAt: Long = System.currentTimeMillis(),
    /** "Sie" or "KI · Modellname". */
    val author: String,
    val note: String = "",
    val title: String,
    val tabs: List<DocTab>,
) {
    val byAi: Boolean get() = author.startsWith("KI")
}

/**
 * A living document (like Claude Docs): written by the model in the chat, edited directly by the user, commented
 * and exported as PDF, Word, Markdown, HTML or PowerPoint. The tabs hold Markdown, which the app renders for
 * editing and for the documents.
 */
@Serializable
data class LiveDoc(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val tabs: List<DocTab> = listOf(DocTab()),
    /** Look of exported documents: enum name of [DocStyle]; null = settings. */
    val style: String? = null,
    /** Chat the document belongs to; comments for the model go there. */
    val chatId: String? = null,
    /** Last file the model or the user exported (PDF, Word …), shown in the chat. */
    val file: String? = null,
    val versions: List<DocVersion> = emptyList(),
    val comments: List<DocComment> = emptyList(),
    /** Comments go to the model by default (remembered per document, like the checkbox in Claude Docs). */
    val sendToAi: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    /** Who changed it last: "Sie" or "KI · …". */
    val lastAuthor: String = "Sie",
) {
    fun tab(id: String?): DocTab = tabs.firstOrNull { it.id == id } ?: tabs.first()

    /** All tabs as one Markdown text (for export and for the model); tabs from the second on start with their title. */
    fun markdown(tabId: String? = null): String = if (tabId != null) tab(tabId).markdown
    else if (tabs.size == 1) tabs.first().markdown
    else tabs.joinToString("\n\n") { t -> if (t == tabs.first()) t.markdown else "## ${t.title}\n\n${t.markdown}" }

    val openComments: Int get() = comments.count { !it.resolved }

    val words: Int get() = tabs.sumOf { t -> t.markdown.split(Regex("\\s+")).count { w -> w.any(Char::isLetterOrDigit) } }

    /**
     * New content with a version entry. Changes of the same person within [MERGE_MS] become one version, so typing
     * does not fill the history.
     */
    fun change(author: String, note: String = "", transform: (LiveDoc) -> LiveDoc): LiveDoc {
        val next = transform(this)
        if (next.tabs == tabs && next.title == title) return next
        val now = System.currentTimeMillis()
        val last = versions.lastOrNull()
        val version = DocVersion(author = author, note = note, title = next.title, tabs = next.tabs)
        val list = if (last != null && last.author == author && !last.byAi && now - last.createdAt < MERGE_MS && note.isBlank()) {
            versions.dropLast(1) + version.copy(id = last.id, createdAt = now)
        } else versions + version
        return next.copy(versions = list.takeLast(MAX_VERSIONS), updatedAt = now, lastAuthor = author)
    }

    companion object {
        const val MERGE_MS = 3 * 60_000L
        const val MAX_VERSIONS = 60

        fun create(title: String, markdown: String, author: String, chatId: String? = null, style: String? = null): LiveDoc {
            val tab = DocTab(title = "Tab 1", markdown = markdown)
            return LiveDoc(
                title = title.ifBlank { DocBlocks.titleOf(markdown) ?: "Unbenanntes Dokument" },
                tabs = listOf(tab), chatId = chatId, style = style, lastAuthor = author,
                versions = listOf(DocVersion(author = author, note = "Erstellt", title = title, tabs = listOf(tab))),
            )
        }
    }
}

/** The living documents, one JSON file each (versions make them large; the list stays fast). */
class DocsStore(dir: File) {
    private val folder = File(dir, "livedocs").apply { mkdirs() }
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val _items = MutableStateFlow(load())
    val items: StateFlow<List<LiveDoc>> = _items.asStateFlow()
    val current: List<LiveDoc> get() = _items.value

    private fun load(): List<LiveDoc> = folder.listFiles { f -> f.extension == "json" }.orEmpty().mapNotNull { f ->
        runCatching { json.decodeFromString(LiveDoc.serializer(), AtomicFiles.read(f) ?: return@mapNotNull null) }
            .getOrElse { e -> AtomicFiles.quarantine(f, e); null }
    }.sortedByDescending { it.updatedAt }

    fun get(id: String?): LiveDoc? = id?.let { i -> _items.value.firstOrNull { it.id == i } }

    /** The document whose last export is [path]. */
    fun forFile(path: String?): LiveDoc? = path?.let { p -> _items.value.firstOrNull { it.file == p } }

    fun forChat(chatId: String): List<LiveDoc> = _items.value.filter { it.chatId == chatId }

    @Synchronized
    fun save(doc: LiveDoc) {
        _items.update { list -> (listOf(doc) + list.filterNot { it.id == doc.id }).sortedByDescending { it.updatedAt } }
        runCatching { AtomicFiles.write(File(folder, "${doc.id}.json"), json.encodeToString(LiveDoc.serializer(), doc)) }.logFailure("Dokument speichern")
    }

    fun update(id: String, transform: (LiveDoc) -> LiveDoc): LiveDoc? {
        val doc = get(id) ?: return null
        return transform(doc).also { save(it) }
    }

    @Synchronized
    fun delete(id: String) {
        _items.update { list -> list.filterNot { it.id == id } }
        File(folder, "$id.json").delete()
    }
}

/**
 * Markdown split into the blocks the editor shows and edits one at a time: paragraphs, headings, list groups,
 * tables, code, quotes and callouts. Joining the blocks with an empty line gives the Markdown back.
 */
object DocBlocks {
    private val HEADING = Regex("^#{1,6}\\s")
    private val LIST = Regex("^\\s*([-*+]|\\d+[.)])\\s")

    fun split(markdown: String): List<String> {
        val out = mutableListOf<String>()
        val cur = StringBuilder()
        var kind = ""
        fun flush() {
            val s = cur.toString().trimEnd()
            if (s.isNotBlank()) out += s.trimStart('\n')
            cur.clear()
            kind = ""
        }
        val lines = markdown.replace("\r\n", "\n").lines()
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            val t = line.trim()
            when {
                t.startsWith("```") -> {
                    flush()
                    cur.append(line)
                    i++
                    while (i < lines.size) {
                        cur.append('\n').append(lines[i])
                        if (lines[i].trim().startsWith("```")) break
                        i++
                    }
                    flush()
                }
                t.isEmpty() -> if (kind != "list") flush() else {
                    // A list continues after an empty line only if the next line is another item.
                    if (i + 1 < lines.size && LIST.containsMatchIn(lines[i + 1])) cur.append('\n') else flush()
                }
                HEADING.containsMatchIn(t) -> { flush(); out += t }
                t.startsWith("|") -> {
                    if (kind != "table") flush()
                    kind = "table"
                    if (cur.isNotEmpty()) cur.append('\n')
                    cur.append(line)
                }
                t.startsWith(">") -> {
                    if (kind != "quote") flush()
                    kind = "quote"
                    if (cur.isNotEmpty()) cur.append('\n')
                    cur.append(line)
                }
                LIST.containsMatchIn(line) || (kind == "list" && line.startsWith("  ")) -> {
                    if (kind != "list") flush()
                    kind = "list"
                    if (cur.isNotEmpty()) cur.append('\n')
                    cur.append(line)
                }
                t.matches(Regex("^([-*_])\\1{2,}$")) -> { flush(); out += t }
                else -> {
                    if (kind != "" && kind != "para") flush()
                    kind = "para"
                    if (cur.isNotEmpty()) cur.append('\n')
                    cur.append(line)
                }
            }
            i++
        }
        flush()
        return out
    }

    fun join(blocks: List<String>): String = blocks.filter { it.isNotBlank() }.joinToString("\n\n") { it.trimEnd() } + "\n"

    fun titleOf(markdown: String): String? = markdown.lineSequence().firstOrNull { it.startsWith("# ") }?.removePrefix("# ")?.trim()

    enum class Kind(val label: String) { PARAGRAPH("Absatz"), H1("Überschrift 1"), H2("Überschrift 2"), H3("Überschrift 3"), BULLETS("Aufzählung"), NUMBERS("Nummerierte Liste"), QUOTE("Zitat"), NOTE("Hinweisbox"), TABLE("Tabelle"), CODE("Code"), RULE("Trennlinie") }

    fun kindOf(block: String): Kind {
        val t = block.trimStart()
        return when {
            t.startsWith("### ") || t.startsWith("#### ") -> Kind.H3
            t.startsWith("## ") -> Kind.H2
            t.startsWith("# ") -> Kind.H1
            t.startsWith("```") -> Kind.CODE
            t.startsWith("|") -> Kind.TABLE
            t.startsWith("> [!") -> Kind.NOTE
            t.startsWith(">") -> Kind.QUOTE
            Regex("^\\d+[.)]\\s").containsMatchIn(t) -> Kind.NUMBERS
            Regex("^[-*+]\\s").containsMatchIn(t) -> Kind.BULLETS
            Regex("^([-*_])\\1{2,}$").matches(t) -> Kind.RULE
            else -> Kind.PARAGRAPH
        }
    }

    /** The text of a block without its Markdown marks of the block type. */
    fun plainOf(block: String): String = block.lines().joinToString("\n") { l ->
        l.replace(Regex("^#{1,6}\\s+"), "").replace(Regex("^>\\s?(\\[![A-Z]+]\\s*)?"), "").replace(Regex("^\\s*([-*+]|\\d+[.)])\\s+"), "")
    }.trim()

    /** Changes the type of a block, keeping its text. */
    fun convert(block: String, to: Kind): String {
        val text = plainOf(block)
        val lines = text.lines().filter { it.isNotBlank() }
        return when (to) {
            Kind.PARAGRAPH -> lines.joinToString(" ")
            Kind.H1 -> "# " + lines.joinToString(" ")
            Kind.H2 -> "## " + lines.joinToString(" ")
            Kind.H3 -> "### " + lines.joinToString(" ")
            Kind.BULLETS -> lines.joinToString("\n") { "- $it" }
            Kind.NUMBERS -> lines.mapIndexed { i, l -> "${i + 1}. $l" }.joinToString("\n")
            Kind.QUOTE -> lines.joinToString("\n") { "> $it" }
            Kind.NOTE -> "> [!NOTE]\n" + lines.joinToString("\n") { "> $it" }
            Kind.TABLE -> if (block.trimStart().startsWith("|")) block else "| Spalte 1 | Spalte 2 |\n| --- | --- |\n| ${lines.firstOrNull().orEmpty()} | |"
            Kind.CODE -> "```\n$text\n```"
            Kind.RULE -> "---"
        }
    }

    /** Rows of a Markdown table (without the separator line). */
    fun tableRows(block: String): List<List<String>> = block.lines().map { it.trim() }.filter { it.startsWith("|") }
        .map { it.trim('|').split('|').map(String::trim) }
        .filterNot { row -> row.all { it.matches(Regex(":?-{2,}:?")) } }

    fun table(rows: List<List<String>>): String {
        if (rows.isEmpty()) return ""
        val cols = rows.maxOf { it.size }
        fun line(r: List<String>) = "| " + (0 until cols).joinToString(" | ") { r.getOrElse(it) { "" }.replace("|", "/").replace("\n", " ") } + " |"
        return (listOf(line(rows[0]), "| " + List(cols) { "---" }.joinToString(" | ") + " |") + rows.drop(1).map(::line)).joinToString("\n")
    }
}

/** Word-level differences between two texts, for the version history and Word with tracked changes. */
object TextDiff {
    enum class Op { SAME, ADDED, REMOVED }

    data class Piece(val op: Op, val text: String)

    private fun tokens(s: String): List<String> = Regex("\\s+|[^\\s]+").findAll(s).map { it.value }.toList()

    /** Differences of [old] and [new] word by word (the longest common subsequence). */
    fun words(old: String, new: String): List<Piece> {
        val a = tokens(old)
        val b = tokens(new)
        if (a.size * b.size > 6_000_000) {
            // Very long texts: compare blocks instead of words.
            return blocks(old, new)
        }
        val dp = Array(a.size + 1) { IntArray(b.size + 1) }
        for (i in a.indices.reversed()) for (j in b.indices.reversed()) {
            dp[i][j] = if (a[i] == b[j]) dp[i + 1][j + 1] + 1 else maxOf(dp[i + 1][j], dp[i][j + 1])
        }
        val out = mutableListOf<Piece>()
        fun add(op: Op, t: String) {
            if (out.isNotEmpty() && out.last().op == op) out[out.lastIndex] = Piece(op, out.last().text + t) else out += Piece(op, t)
        }
        var i = 0
        var j = 0
        while (i < a.size && j < b.size) {
            when {
                a[i] == b[j] -> { add(Op.SAME, a[i]); i++; j++ }
                dp[i + 1][j] >= dp[i][j + 1] -> { add(Op.REMOVED, a[i]); i++ }
                else -> { add(Op.ADDED, b[j]); j++ }
            }
        }
        while (i < a.size) add(Op.REMOVED, a[i++])
        while (j < b.size) add(Op.ADDED, b[j++])
        return out
    }

    /** Block by block: unchanged blocks stay together, changed ones are compared word by word. */
    fun blocks(old: String, new: String): List<Piece> {
        val a = DocBlocks.split(old)
        val b = DocBlocks.split(new)
        val dp = Array(a.size + 1) { IntArray(b.size + 1) }
        for (i in a.indices.reversed()) for (j in b.indices.reversed()) {
            dp[i][j] = if (a[i] == b[j]) dp[i + 1][j + 1] + 1 else maxOf(dp[i + 1][j], dp[i][j + 1])
        }
        val out = mutableListOf<Piece>()
        var i = 0
        var j = 0
        while (i < a.size || j < b.size) {
            when {
                i < a.size && j < b.size && a[i] == b[j] -> { out += Piece(Op.SAME, a[i] + "\n\n"); i++; j++ }
                j < b.size && (i >= a.size || dp[i][j + 1] >= dp[i + 1][j]) -> { out += Piece(Op.ADDED, b[j] + "\n\n"); j++ }
                else -> { out += Piece(Op.REMOVED, a[i] + "\n\n"); i++ }
            }
        }
        return out
    }

    /** Indexes of the blocks of [new] that differ from [old] (to highlight what the model just changed). */
    fun changedBlocks(old: String, new: String): Set<Int> {
        val before = DocBlocks.split(old).toSet()
        return DocBlocks.split(new).mapIndexedNotNull { i, b -> if (b !in before) i else null }.toSet()
    }

    fun summary(pieces: List<Piece>): String {
        val added = pieces.filter { it.op == Op.ADDED }.sumOf { p -> p.text.split(Regex("\\s+")).count { it.isNotBlank() } }
        val removed = pieces.filter { it.op == Op.REMOVED }.sumOf { p -> p.text.split(Regex("\\s+")).count { it.isNotBlank() } }
        return "+$added / −$removed Wörter"
    }
}

/**
 * What the model is writing right now (arguments of a create_document call still streaming), per chat: the editor
 * shows the document filling up as in Claude Docs.
 */
object DocsLive {
    data class Draft(val title: String, val markdown: String, val docId: String? = null)

    val drafts = MutableStateFlow<Map<String, Draft>>(emptyMap())

    /** Reads title and content from the beginning of a JSON object that is still being written. */
    fun partialArgs(json: String): Pair<String, String> = partialString(json, "title") to partialString(json, "content")

    private fun partialString(json: String, key: String): String {
        val m = Regex("\"$key\"\\s*:\\s*\"").find(json) ?: return ""
        val sb = StringBuilder()
        var i = m.range.last + 1
        while (i < json.length) {
            val ch = json[i]
            if (ch == '"') break
            if (ch == '\\') {
                if (i + 1 >= json.length) break
                when (val n = json[i + 1]) {
                    'n' -> sb.append('\n')
                    't' -> sb.append('\t')
                    'r' -> {}
                    'u' -> {
                        if (i + 5 < json.length) json.substring(i + 2, i + 6).toIntOrNull(16)?.let { sb.append(it.toChar()) } else return sb.toString()
                        i += 4
                    }
                    else -> sb.append(n)
                }
                i += 2
                continue
            }
            sb.append(ch)
            i++
        }
        return sb.toString()
    }

    fun update(chatId: String, draft: Draft?) {
        drafts.update { m -> if (draft == null) m - chatId else m + (chatId to draft) }
    }
}
