package io.github.thepytales.llmhub.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID
import kotlin.math.ln
import kotlin.math.sqrt

/** A document in the knowledge collection: global ([collection] empty) or of one project (folder id). */
@Serializable
data class KnowledgeDoc(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val collection: String = "",
    val chars: Int = 0,
    val chunks: Int = 0,
    /** Embedding model the chunks were embedded with; null = only keyword search. */
    val embeddedWith: String? = null,
    val addedAt: Long = System.currentTimeMillis(),
)

@Serializable
data class KnowledgeChunk(val text: String, val vector: List<Float>? = null)

/** A search hit: the passage, its document and the position in it. */
data class KnowledgeHit(val doc: KnowledgeDoc, val index: Int, val text: String, val score: Double)

/**
 * Own documents the model can search (module handbook, examination regulations, literature …). Text is split
 * into overlapping passages; search combines keyword ranking (BM25) with embeddings of the university server
 * when they are available, so it also works offline and without an embedding model.
 */
class KnowledgeStore(root: File) {
    private val dir = File(root, "knowledge")
    private val index = ListStore<KnowledgeDoc>(FileBackend(File(dir, "index.json")), KnowledgeDoc.serializer(), { it.id })
    val docs: StateFlow<List<KnowledgeDoc>> = index.items
    private val cache = HashMap<String, List<KnowledgeChunk>>()
    private val json = Json { ignoreUnknownKeys = true }

    private val _busy = MutableStateFlow(0)
    /** Documents being embedded right now. */
    val busy: StateFlow<Int> = _busy.asStateFlow()

    fun of(collection: String) = index.current.filter { it.collection == collection }
    fun hasAny(collections: Collection<String>) = index.current.any { it.collection in collections }

    @Synchronized
    fun chunks(doc: KnowledgeDoc): List<KnowledgeChunk> = cache.getOrPut(doc.id) {
        runCatching { json.decodeFromString(ListSerializer(KnowledgeChunk.serializer()), File(dir, "${doc.id}.json").readText()) }.getOrDefault(emptyList())
    }

    @Synchronized
    private fun saveChunks(id: String, chunks: List<KnowledgeChunk>) {
        dir.mkdirs()
        File(dir, "$id.json").writeText(json.encodeToString(ListSerializer(KnowledgeChunk.serializer()), chunks))
        cache[id] = chunks
    }

    /** Adds a document (replacing one with the same name in the same collection); embeddings follow with [embed]. */
    fun add(name: String, text: String, collection: String): KnowledgeDoc {
        index.current.filter { it.collection == collection && it.name == name }.forEach { remove(it) }
        val parts = split(text)
        val doc = KnowledgeDoc(name = name, collection = collection, chars = text.length, chunks = parts.size)
        saveChunks(doc.id, parts.map { KnowledgeChunk(it) })
        index.upsert(doc)
        return doc
    }

    /** Computes the embeddings of [doc]; on failure the document stays searchable by keywords. */
    suspend fun embed(doc: KnowledgeDoc, model: String, embedder: suspend (List<String>) -> List<FloatArray>): Boolean {
        _busy.value++
        try {
            val chunks = chunks(doc)
            val vectors = chunks.chunked(16).flatMap { batch -> embedder(batch.map { it.text }) }
            if (vectors.size != chunks.size) return false
            saveChunks(doc.id, chunks.zip(vectors) { c, v -> c.copy(vector = v.toList()) })
            index.update(doc.id) { it.copy(embeddedWith = model) }
            return true
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            return false
        } finally {
            _busy.value--
        }
    }

    @Synchronized
    fun remove(doc: KnowledgeDoc) {
        index.delete(doc.id)
        cache.remove(doc.id)
        File(dir, "${doc.id}.json").delete()
    }

    fun removeCollection(collection: String) = of(collection).forEach { remove(it) }

    /**
     * The [k] best passages for [query] in [collections]. [queryVector] (from the same embedding model) adds
     * semantic similarity to the keyword score.
     */
    fun search(query: String, collections: Collection<String>, k: Int = 6, queryVector: FloatArray? = null, model: String? = null): List<KnowledgeHit> {
        val docs = index.current.filter { it.collection in collections }
        val all = docs.flatMap { d -> chunks(d).mapIndexed { i, c -> Triple(d, i, c) } }
        if (all.isEmpty()) return emptyList()
        val terms = tokens(query).distinct()
        val tokenized = all.map { tokens(it.third.text) }
        val avg = tokenized.sumOf { it.size }.toDouble() / tokenized.size
        val df = terms.associateWith { t -> tokenized.count { t in it } }
        val bm25 = tokenized.map { toks ->
            val tf = toks.groupingBy { it }.eachCount()
            terms.sumOf { t ->
                val f = tf[t] ?: 0
                if (f == 0) 0.0 else {
                    val idf = ln(1 + (all.size - df[t]!! + 0.5) / (df[t]!! + 0.5))
                    idf * f * 2.2 / (f + 1.2 * (0.25 + 0.75 * toks.size / avg))
                }
            }
        }
        val maxBm = bm25.maxOrNull()?.takeIf { it > 0 } ?: 1.0
        val scores = all.mapIndexed { i, (d, _, c) ->
            val keyword = bm25[i] / maxBm
            val semantic = if (queryVector != null && c.vector != null && d.embeddedWith == model) cosine(queryVector, c.vector) else null
            if (semantic != null) 0.65 * semantic + 0.35 * keyword else keyword
        }
        return all.indices.sortedByDescending { scores[it] }.take(k).filter { scores[it] > 0.05 }
            .map { i -> KnowledgeHit(all[i].first, all[i].second, all[i].third.text, scores[i]) }
    }

    companion object {
        const val CHUNK = 1_200
        const val OVERLAP = 200

        /** Passages of about [CHUNK] characters that end at paragraph or sentence borders and overlap a little. */
        fun split(text: String): List<String> {
            val clean = text.replace("\r\n", "\n").replace(Regex("[ \t]+"), " ").replace(Regex("\n{3,}"), "\n\n").trim()
            if (clean.length <= CHUNK) return listOf(clean).filter { it.isNotBlank() }
            val out = mutableListOf<String>()
            var start = 0
            while (start < clean.length) {
                var end = (start + CHUNK).coerceAtMost(clean.length)
                if (end < clean.length) {
                    val window = clean.substring(start, end)
                    val cut = listOf(window.lastIndexOf("\n\n"), window.lastIndexOf(". "), window.lastIndexOf('\n'))
                        .firstOrNull { it > CHUNK / 2 }
                    if (cut != null) end = start + cut + 1
                }
                out += clean.substring(start, end).trim()
                if (end >= clean.length) break
                start = (end - OVERLAP).coerceAtLeast(start + 1)
            }
            return out.filter { it.isNotBlank() }
        }

        private val WORD = Regex("[\\p{L}\\p{N}]{2,}")

        /** Lower-case words with a crude German stemming, so "Prüfungen" also finds "Prüfung". */
        fun tokens(text: String): List<String> = WORD.findAll(text.lowercase()).map { w ->
            val v = w.value
            when {
                v.length > 6 && v.endsWith("ungen") -> v.dropLast(2)
                v.length > 5 && (v.endsWith("en") || v.endsWith("er") || v.endsWith("es")) -> v.dropLast(2)
                v.length > 4 && (v.endsWith("e") || v.endsWith("n") || v.endsWith("s")) -> v.dropLast(1)
                else -> v
            }
        }.toList()

        fun cosine(a: FloatArray, b: List<Float>): Double {
            if (a.size != b.size) return 0.0
            var dot = 0.0
            var na = 0.0
            var nb = 0.0
            for (i in a.indices) {
                dot += a[i] * b[i]; na += a[i] * a[i]; nb += b[i] * b[i]
            }
            return if (na == 0.0 || nb == 0.0) 0.0 else dot / (sqrt(na) * sqrt(nb))
        }
    }
}

/** What the model may note about the user (Einstellungen › Gedächtnis). */
enum class MemoryMode(val label: String, val description: String) {
    OFF("Aus", "Das Modell merkt sich nichts über Chats hinweg."),
    ASK("Vorher fragen", "Das Modell schlägt vor, sich etwas zu merken – Sie bestätigen jeden Eintrag."),
    AUTO("Automatisch", "Das Modell merkt sich wichtige Angaben selbst; Sie sehen und löschen sie hier."),
    ;

    companion object {
        fun of(name: String?) = entries.firstOrNull { it.name == name } ?: ASK
    }
}

/** Something the model knows about the user in every chat, e.g. "Unterrichtet Allgemeine Didaktik im Master". */
@Serializable
data class MemoryEntry(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val createdAt: Long = System.currentTimeMillis(),
)
