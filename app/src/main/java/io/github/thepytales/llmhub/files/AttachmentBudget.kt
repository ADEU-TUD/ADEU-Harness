package io.github.thepytales.llmhub.files

import io.github.thepytales.llmhub.data.ChatMessage
import io.github.thepytales.llmhub.data.FileAttachment
import io.github.thepytales.llmhub.data.ModelCatalog
import io.github.thepytales.llmhub.local.LocalCatalog
import io.github.thepytales.llmhub.ui.tr

/**
 * Große Anhänge vollständig nutzen (AP-85). Früher wurde jede Datei nach 120 000 Zeichen still abgeschnitten. Jetzt
 * richtet sich die Grenze nach dem Modell (ein fester Anteil seines Kontextfensters, bei mehreren Anhängen geteilt).
 * Passt eine Datei nicht, wählt man: **ganz** (mit einem Modell, das sie fasst), **nur die passenden Stellen** (Suche im
 * Anhang nach der Frage) oder **abschnittsweise zusammengefasst**. Lokale Modelle mit kleinem Kontext bekommen von selbst
 * die passenden Stellen. Gekürzt wird nie still: die Karte am Anhang sagt, wie viel mitgeht, und das Modell liest es im
 * Kopf der Datei.
 */
object AttachmentBudget {
    /** How a file goes along when it does not fit (stored on the attachment, [FileAttachment.send]). */
    enum class Mode(val id: String) {
        FULL("ganz"), PASSAGES("stellen"), SUMMARY("zusammenfassung");

        companion object {
            fun of(id: String?): Mode? = entries.firstOrNull { it.id == id }
        }
    }

    /** Characters per token for German text (estimate, like [io.github.thepytales.llmhub.agent.ContextManager]). */
    const val CHARS_PER_TOKEN = 3.5

    /** Share of the context window all attachments of a request may use; the rest is for the chat and the answer. */
    const val SHARE = 0.7

    /** Reading stops here (about 2000 pages) – only so a broken file cannot fill the memory. */
    const val READ_MAX_CHARS = 6_000_000

    /** Context window of [model] in tokens: from the catalog, local models from theirs, else a careful 32 768. */
    fun contextTokens(model: String): Int =
        LocalCatalog.get(model)?.let { LocalCatalog.context(it) } // AP-108: local models after the memory of this device
            ?: ModelCatalog.effective(ModelCatalog.info(model)).contextTokens ?: 32_768

    /**
     * Characters all attachments of one request may have for [model]. Local models (AP-108): no more than this device
     * reads in about a minute and a half ([LocalCatalog.quickChars]) – the whole text only when chosen ([wholeChars]).
     */
    fun budgetChars(model: String): Int {
        val memory = wholeChars(model)
        val quick = LocalCatalog.get(model)?.takeIf { local(model) }?.let { LocalCatalog.quickChars(it) }
        return if (quick != null) minOf(memory, quick.coerceAtLeast(8_000)) else memory
    }

    /** Characters that fit the context of [model] at all (for local models after the memory of this device). */
    fun wholeChars(model: String): Int = ((contextTokens(model) * SHARE - 2_000.0).coerceAtLeast(2_000.0) * CHARS_PER_TOKEN).toInt()

    /** A file sent whole: always for server models (chosen to fit), for local ones while it fits their context. */
    private fun whole(f: FileAttachment, model: String, total: Int) = Mode.of(f.send) == Mode.FULL && (!local(model) || total <= wholeChars(model))

    /** Whether [model] runs on this device (small context: passages by themselves). */
    private fun local(model: String) = model.startsWith(io.github.thepytales.llmhub.data.Privacy.LOCAL_PREFIX)

    /** A model of the catalog that takes [chars] whole: GLM-5.3 Flash first (fast, 1 M tokens), else the largest. */
    fun suggestModel(chars: Int): String? {
        val wanted = listOf(ModelCatalog.FAST_CHAT, ModelCatalog.DEFAULT_CHAT, ModelCatalog.HARD_CHAT)
        return wanted.firstOrNull { budgetChars(it) >= chars }
    }

    /** What the card at the attachment says (AP-85): how much really goes along with [model]. */
    data class Shown(val mode: Mode?, val share: Double, val fits: Boolean, val text: String)

    /**
     * For the cards in the input field and in the chat: per file of [files] how much goes along with [model] – whole, the
     * matching passages (about x %), or the summary.
     */
    fun shown(files: List<FileAttachment>, model: String): List<Shown> {
        val budget = budgetChars(model)
        val total = files.sumOf { it.text.length }.coerceAtLeast(1)
        return files.map { f ->
            val len = f.text.length.coerceAtLeast(1)
            val share = if (total <= budget) 1.0 else (budget.toDouble() * len / total / len).coerceAtMost(1.0)
            val mode = Mode.of(f.send)
            val fits = total <= budget || share >= 1.0
            val text = when {
                mode == Mode.FULL -> tr("geht ganz mit")
                fits -> tr("geht ganz mit")
                mode == Mode.SUMMARY && f.digest != null -> tr("als Zusammenfassung ({0} % der Länge)", (f.digest.length * 100 / len).coerceIn(1, 100))
                mode == Mode.SUMMARY -> tr("Zusammenfassung wird erstellt …")
                else -> tr("nur passende Stellen (etwa {0} %)", (share * 100).toInt().coerceIn(1, 99))
            }
            Shown(mode, share, fits, text)
        }
    }

    /** Files of [files] that do not fit [model] and have no choice yet – for these the app asks. */
    fun needsChoice(files: List<FileAttachment>, model: String): List<FileAttachment> =
        // Local models ask too (AP-108): with the time each way takes on this device; until then they take the passages.
        files.filter { it.send == null }.takeIf { fs -> files.sumOf { it.text.length } > budgetChars(model) && fs.isNotEmpty() }.orEmpty()

    /**
     * The messages as they go to [model]: files that fit stay whole; the others become their passages for the question
     * of the last user message, or their summary – with a line at the top of the file that says so. Files the user sends
     * whole stay whole (the model was chosen to fit).
     */
    fun prepare(messages: List<ChatMessage>, model: String): List<ChatMessage> {
        val all = messages.flatMap { it.files }
        if (all.isEmpty()) return messages
        val budget = budgetChars(model)
        val total = all.sumOf { it.text.length }
        if (total <= budget) return messages
        val question = messages.lastOrNull { it.role == "user" }?.content.orEmpty()
        // Whole files take their place first; the rest is shared by length.
        val whole = all.filter { whole(it, model, total) }.sumOf { it.text.length }
        val rest = (budget - whole).coerceAtLeast(budget / 5)
        val others = all.filter { !whole(it, model, total) }.sumOf { it.text.length }.coerceAtLeast(1)
        return messages.map { m ->
            if (m.files.isEmpty()) m else m.copy(files = m.files.map { f ->
                if (whole(f, model, total)) return@map f
                val room = (rest.toLong() * f.text.length / others).toInt().coerceAtLeast(4_000)
                if (f.text.length <= room) return@map f
                val digest = f.digest?.takeIf { Mode.of(f.send) == Mode.SUMMARY && it.length <= room }
                if (digest != null) f.copy(text = tr("[Abschnittsweise Zusammenfassung des ganzen Dokuments – nicht der Wortlaut.]") + "\n\n" + digest, truncated = false)
                else {
                    val (text, share) = passages(f.text, question.ifBlank { f.name }, room)
                    f.copy(text = tr("[Nur die zur Frage passenden Stellen, etwa {0} % des Dokuments; Auslassungen sind mit […] markiert.]", (share * 100).toInt().coerceIn(1, 99)) + "\n\n" + text, truncated = false)
                }
            })
        }
    }

    /** Tokens the attachments of [messages] take (so they do not make the chat summarize its history). */
    fun tokens(messages: List<ChatMessage>): Int = (messages.sumOf { m -> m.files.sumOf { it.text.length } } / CHARS_PER_TOKEN).toInt()

    // ── Passende Stellen ──────────────────────────────────────────────────────────────────────────────────

    /** A piece of the text with where it starts. */
    private data class Chunk(val start: Int, val text: String, val page: Int?)

    private fun chunks(text: String, size: Int = 1_600): List<Chunk> {
        // Page marks („[Seite 12]“) once, then looked up by position (a 300-page text has thousands of chunks).
        val marks = Regex("\\[Seite (\\d+)]").findAll(text).map { it.range.first to it.groupValues[1].toInt() }.toList()
        fun pageAt(at: Int): Int? {
            var lo = 0; var hi = marks.size - 1; var found: Int? = null
            while (lo <= hi) { val mid = (lo + hi) / 2; if (marks[mid].first <= at) { found = marks[mid].second; lo = mid + 1 } else hi = mid - 1 }
            return found
        }
        val out = mutableListOf<Chunk>()
        val paragraphs = Regex("\n\\s*\n").split(text)
        var pos = 0
        val sb = StringBuilder()
        var start = 0
        for (p in paragraphs) {
            val at = text.indexOf(p, pos).coerceAtLeast(pos)
            if (sb.isEmpty()) start = at
            if (sb.length + p.length > size && sb.isNotEmpty()) {
                out += Chunk(start, sb.toString(), pageAt(start))
                sb.clear()
                start = at
            }
            // A single very long paragraph is split as well.
            var rest = p
            while (rest.length > size * 2) {
                out += Chunk(start, rest.take(size), pageAt(start))
                start += size
                rest = rest.drop(size)
            }
            if (sb.isNotEmpty()) sb.append("\n\n")
            sb.append(rest)
            pos = at + p.length
        }
        if (sb.isNotEmpty()) out += Chunk(start, sb.toString(), pageAt(start))
        return out
    }

    private fun words(s: String): List<String> = java.text.Normalizer.normalize(s.lowercase(), java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{M}"), "").split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length >= 4 && it !in STOP }

    private val STOP = setOf(
        "eine", "einer", "einen", "einem", "dass", "diese", "dieser", "dieses", "nicht", "auch", "oder", "aber", "wenn", "werden",
        "wird", "wurde", "sind", "sein", "haben", "hatte", "kann", "konnen", "bitte", "fasse", "zusammen", "welche", "welcher",
        "dokument", "datei", "text", "steht", "stehen", "darin", "dabei", "sowie", "ueber", "uber", "unter", "einen", "this", "that", "with",
    )

    /**
     * The passages of [text] that fit [question] best, in their order in the text, at most [maxChars]; the beginning (title,
     * abstract, table of contents) always comes along. Returns the text and the share of the whole.
     */
    fun passages(text: String, question: String, maxChars: Int): Pair<String, Double> {
        val cs = chunks(text)
        if (cs.isEmpty()) return text.take(maxChars) to 1.0
        val q = words(question).toSet()
        val df = HashMap<String, Int>()
        val tf = cs.map { c -> words(c.text).groupingBy { it }.eachCount().also { m -> m.keys.forEach { df[it] = (df[it] ?: 0) + 1 } } }
        val n = cs.size.toDouble()
        val score = cs.indices.map { i ->
            q.sumOf { w -> val f = tf[i][w] ?: 0; if (f == 0) 0.0 else (1 + kotlin.math.ln(f.toDouble())) * kotlin.math.ln(1 + n / (df[w] ?: 1)) }
        }
        val chosen = sortedSetOf(0)
        var used = cs[0].text.length
        for (i in cs.indices.sortedByDescending { score[it] }) {
            if (score[i] <= 0.0) break
            if (used + cs[i].text.length + 20 > maxChars) continue
            chosen += i
            used += cs[i].text.length + 20
        }
        // Nothing matched the words: an even selection over the whole text instead of only the start.
        if (chosen.size == 1 && cs.size > 1) {
            val step = (cs.size / ((maxChars / 1_600).coerceAtLeast(1))).coerceAtLeast(1)
            for (i in cs.indices step step) { if (used + cs[i].text.length + 20 > maxChars) break; if (chosen.add(i)) used += cs[i].text.length + 20 }
        }
        val out = StringBuilder()
        var last = -1
        for (i in chosen) {
            if (last >= 0 && i != last + 1) out.append("\n\n[…]\n\n") else if (last >= 0) out.append("\n\n")
            cs[i].page?.let { if ((i != last + 1 || last < 0) && !cs[i].text.trimStart().startsWith("[Seite")) out.append(tr("[Seite {0}]", it)).append('\n') }
            out.append(cs[i].text.trim())
            last = i
        }
        return out.toString().take(maxChars) to (used.toDouble() / text.length).coerceAtMost(1.0)
    }
}
