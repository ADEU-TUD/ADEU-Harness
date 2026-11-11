package io.github.thepytales.llmhub.agent

import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.data.PrivacyLevel
import java.io.File
import java.text.Normalizer

/**
 * „Frag diesen Ordner“ (AP-58): a chat in the Quellenmodus over all files of a folder. The texts come from the full-text
 * cache of AP-18 (read once, large folders in the background with progress); the tool `search_folder` gives the model
 * the fitting passages with file and page, and the Belegprüfung checks every quote against exactly these passages.
 */
object FolderAsk {
    /** At most this many files of a folder (with its subfolders) are read. */
    const val MAX_FILES = 400

    private val READABLE = setOf(
        "pdf", "docx", "pptx", "xlsx", "xlsm", "odt", "ods", "odp", "rtf", "md", "markdown", "txt", "text", "csv", "tsv", "html", "htm",
        "tex", "eml", "msg", "json", "xml",
    )

    /** The readable files of [dir] and its subfolders (no hidden ones), sorted by path. */
    fun files(dir: File): List<File> = dir.walkTopDown()
        .onEnter { it == dir || !it.name.startsWith(".") }
        .filter { it.isFile && !it.name.startsWith(".") && !it.name.startsWith("~$") && it.extension.lowercase() in READABLE }
        .take(MAX_FILES).sortedBy { it.path.lowercase() }.toList()

    /**
     * Reads the texts of [dir]'s files into the cache (only what changed since last time); [progress] gets done and
     * total. Returns the files with text.
     */
    fun prepare(c: AppContainer, dir: File, progress: (Int, Int) -> Unit = { _, _ -> }): List<File> {
        val all = files(dir)
        val lib = c.fileLibrary
        val read = all.filterIndexed { i, f ->
            progress(i, all.size)
            !lib.textOf(f).isNullOrBlank()
        }
        // Suche nach Bedeutung (AP-107): the passages get their vectors now, once – later questions are fast.
        if (io.github.thepytales.llmhub.data.SemanticSearch.active) read.forEachIndexed { i, f ->
            progress(i, read.size)
            lib.textOf(f)?.let { t -> io.github.thepytales.llmhub.data.SemanticSearch.prepare(pages(t).map { it.second }) }
        }
        return read.also { progress(all.size, all.size) }
    }

    /**
     * The privacy level suggested for a folder: files or folders marked confidential („vertraulich“, „intern“,
     * „personenbezogen“, „Gutachten“ …) → Nur TU Dresden; null: no suggestion.
     */
    fun suggestedPrivacy(dir: File, files: List<File>): PrivacyLevel? {
        val words = Regex("(?i)(vertraulich|confidential|intern\\b|personenbezogen|gutachten|pr[üu]fungsakte|noten|bewerbung|personal)")
        return if (words.containsMatchIn(dir.name) || files.any { words.containsMatchIn(it.relativeToOrSelf(dir).path) }) PrivacyLevel.TU else null
    }

    /** Words of a question that count for the search (without filler words, folded to ASCII, lower case). */
    fun terms(query: String): List<String> = fold(query).split(Regex("[^a-z0-9]+")).filter { it.length >= 3 && it !in STOP }.distinct()

    private val STOP = setOf(
        "der", "die", "das", "und", "oder", "ein", "eine", "einer", "eines", "einem", "einen", "ist", "sind", "war", "wird", "werden", "mit", "von",
        "fur", "auf", "aus", "bei", "zum", "zur", "den", "dem", "des", "wie", "was", "wer", "wann", "welche", "welcher", "welches", "nicht",
        "auch", "noch", "nur", "sich", "dass", "als", "uber", "unter", "nach", "vor", "the", "and", "for", "with", "this", "that", "gibt", "steht",
        "sagen", "sagt", "texte", "text", "dateien", "datei", "ordner",
    )

    private fun fold(s: String): String = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}"), "").replace("ß", "ss")

    /** A passage of a file: its page (0 = no pages) and the text. */
    data class Passage(val file: String, val path: String, val page: Int, val text: String, val score: Double)

    /** Pages of a text with "[Seite N]" marks (PDF), otherwise pieces of about [size] characters (page 0). */
    fun pages(text: String, size: Int = 1_500): List<Pair<Int, String>> {
        val marks = Regex("\\[Seite (\\d+)]").findAll(text).toList()
        if (marks.isNotEmpty()) {
            return marks.mapIndexed { i, m ->
                val end = marks.getOrNull(i + 1)?.range?.first ?: text.length
                m.groupValues[1].toInt() to text.substring(m.range.last + 1, end).trim()
            }.filter { it.second.isNotBlank() }
        }
        val out = mutableListOf<Pair<Int, String>>()
        var i = 0
        while (i < text.length) {
            var end = (i + size).coerceAtMost(text.length)
            if (end < text.length) text.lastIndexOf("\n\n", end).takeIf { it > i + size / 2 }?.let { end = it }
            out += 0 to text.substring(i, end).trim()
            i = end
        }
        return out.filter { it.second.isNotBlank() }
    }

    /**
     * The [k] passages of [texts] (file → text) that fit [query] best: words of the question by how rare they are in
     * the folder (like tf-idf), at most [perFile] passages of one file; the passage is cut around its best part.
     */
    fun rank(texts: Map<File, String>, query: String, k: Int = 8, perFile: Int = 3, root: File? = null, meaning: Boolean = true): List<Passage> {
        val words = terms(query)
        val semantic = meaning && io.github.thepytales.llmhub.data.SemanticSearch.active
        if (words.isEmpty() && !semantic) return emptyList()
        val units = texts.flatMap { (f, t) -> pages(t).map { (p, s) -> Triple(f, p, s) } }
        val folded = units.map { fold(it.third) }
        val df = words.associateWith { w -> folded.count { it.contains(w) }.coerceAtLeast(1) }
        val n = units.size.coerceAtLeast(1)
        val wordScores = units.indices.map { i ->
            val t = folded[i]
            var score = 0.0
            var matched = 0
            words.forEach { w ->
                val hits = Regex(Regex.escape(w)).findAll(t).count()
                if (hits > 0) { matched++; score += (1 + kotlin.math.ln(hits.toDouble())) * kotlin.math.ln(1.0 + n.toDouble() / df.getValue(w)) }
            }
            if (matched == 0) 0.0 else score * (0.5 + matched.toDouble() / words.size)
        }
        // AP-107: with the model on this device, passages that say the same in other words are found too.
        val cos = if (semantic) io.github.thepytales.llmhub.data.SemanticSearch.similarity(query, units.map { it.third }, limit = 300) else null
        val mixed = if (cos != null) io.github.thepytales.llmhub.data.SemanticSearch.mix(wordScores, cos) else wordScores
        val scored = units.indices.filter { mixed[it] > if (cos != null) 0.05 else 0.0 }.map { it to mixed[it] }.sortedByDescending { it.second }
        val perCount = HashMap<File, Int>()
        val out = mutableListOf<Passage>()
        for ((i, score) in scored) {
            val (f, page, text) = units[i]
            if ((perCount[f] ?: 0) >= perFile) continue
            perCount[f] = (perCount[f] ?: 0) + 1
            out += Passage(root?.let { f.relativeToOrSelf(it).path } ?: f.name, f.absolutePath, page, excerpt(text, words), score)
            if (out.size >= k) break
        }
        return out
    }

    /** About 900 characters of [text] around the place where most of [words] meet. */
    private fun excerpt(text: String, words: List<String>, size: Int = 900): String {
        if (text.length <= size) return text
        val f = fold(text)
        val positions = words.flatMap { w -> Regex(Regex.escape(w)).findAll(f).map { it.range.first }.toList() }.sorted()
        if (positions.isEmpty()) return text.take(size)
        // The window with most hits.
        var best = positions.first()
        var bestCount = 0
        positions.forEach { p -> val c = positions.count { it in p until p + size }; if (c > bestCount) { bestCount = c; best = p } }
        val start = (best - 120).coerceIn(0, (text.length - size).coerceAtLeast(0))
        return (if (start > 0) "…" else "") + text.substring(start, (start + size).coerceAtMost(text.length)).trim() + if (start + size < text.length) "…" else ""
    }

    /** The passages as the tool returns them: one block per passage with file, path and page (for the Belegprüfung). */
    fun describe(passages: List<Passage>, folder: String): String {
        if (passages.isEmpty()) return "Im Ordner „$folder“ steht dazu nichts – andere Suchwörter versuchen oder sagen, dass die Dateien es nicht beantworten."
        return passages.joinToString("\n\n") { p ->
            "$FILE_HEAD${p.file} · ${p.path}\n" + (if (p.page > 0) "[Seite ${p.page}]\n" else "") + p.text
        }
    }

    /** Start of a passage in the tool's output. */
    const val FILE_HEAD = "### Datei: "

    /** The passages of a `search_folder` output as sources of the Belegprüfung (file name, text with page, path). */
    fun sources(output: String): List<EvidenceSource> = output.split(Regex("(?m)^(?=### Datei: )")).filter { it.startsWith(FILE_HEAD) }.map { block ->
        val head = block.lineSequence().first().removePrefix(FILE_HEAD)
        val name = head.substringBefore(" · ").substringAfterLast('/').substringAfterLast('\\')
        val path = head.substringAfter(" · ", "").trim().ifEmpty { null }
        EvidenceSource(name, block.substringAfter('\n'), EvidenceSource.Kind.FILE, path = path)
    }.groupBy { it.label to it.path }.map { (_, list) -> list.first().copy(text = list.joinToString("\n\n") { it.text }) }

    /** Searches the folder [dir] for [query] (texts from the cache; files not read yet are read now). */
    fun search(c: AppContainer, dir: File, query: String, k: Int = 8): String {
        if (!dir.isDirectory) return "FEHLER: Den Ordner „${dir.path}“ gibt es nicht."
        val texts = files(dir).mapNotNull { f -> c.fileLibrary.textOf(f)?.takeIf { it.isNotBlank() }?.let { f to it } }.toMap()
        return describe(rank(texts, query, k, root = dir), dir.name)
    }

    /** What the model is told at the start of a folder chat. */
    fun instructions(dir: File, files: List<File>): String = buildString {
        append("Dieser Chat gilt dem Ordner „").append(dir.name).append("“ (").append(dir.absolutePath).append(") mit ").append(files.size).append(" Dateien. ")
        append("Beantworte Fragen nur aus diesen Dateien: Suche vor jeder Antwort mit search_folder (folder = \"").append(dir.absolutePath)
        append("\") nach den passenden Stellen, gern mehrmals mit anderen Suchwörtern, und belege jede Aussage mit Datei und Seite wie im Quellenmodus. ")
        append("Steht etwas nicht in den Dateien, sag das. Dateien im Ordner:\n")
        files.take(120).forEach { append("- ").append(it.relativeToOrSelf(dir).path).append('\n') }
        if (files.size > 120) append("- … und ").append(files.size - 120).append(" weitere\n")
    }
}
