package io.github.thepytales.llmhub.data

import java.io.File
import java.nio.ByteBuffer
import java.security.MessageDigest
import kotlin.math.sqrt

/**
 * Suche nach Bedeutung auf dem Gerät for everything besides the Wissenssammlung (AP-107, Rest): „Frag diesen Ordner“,
 * the chat search, Wissenspakete and large attachments (AP-85) find passages by what they mean, not only by their
 * words – with the model on this device (multilingual-e5-small), so nothing leaves it. Vectors are kept on disk by the
 * hash of their text (a folder is read once); without the model everything falls back to the word search as before.
 * Mixing as in the Wissenssammlung: cosines stretched to 0..1 per query, 0.8 meaning : 0.2 words.
 */
object SemanticSearch {
    /** Encoder of [LocalEmbedding] (texts, passage?) → vectors; set by the app when the model is there. */
    @Volatile private var encoder: ((List<String>, Boolean) -> List<FloatArray>?)? = null
    @Volatile private var isActive: () -> Boolean = { false }
    @Volatile private var dir: File? = null
    private val memory = object : LinkedHashMap<String, FloatArray>(512, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, FloatArray>?) = size > 4000
    }

    const val WEIGHT = KnowledgeStore.SEMANTIC_WEIGHT

    /** Connects the model on this device ([embed]), whether it may be used now ([active]) and the cache folder. */
    fun attach(cache: File, active: () -> Boolean, embed: (List<String>, Boolean) -> List<FloatArray>?) {
        dir = cache
        isActive = active
        encoder = embed
    }

    /** For tests and when the model is deleted. */
    fun detach() {
        encoder = null
        isActive = { false }
        synchronized(memory) { memory.clear() }
    }

    val active: Boolean get() = encoder != null && runCatching { isActive() }.getOrDefault(false)

    private fun key(text: String, passage: Boolean): String {
        val md = MessageDigest.getInstance("SHA-1")
        md.update(if (passage) 1 else 0)
        md.update(text.toByteArray(Charsets.UTF_8))
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private fun file(k: String): File? = dir?.let { File(File(it, k.take(2)), k) }

    private fun cached(k: String): FloatArray? {
        synchronized(memory) { memory[k] }?.let { return it }
        val f = file(k)?.takeIf { it.isFile } ?: return null
        val b = ByteBuffer.wrap(runCatching { f.readBytes() }.getOrNull() ?: return null)
        val v = FloatArray(b.remaining() / 4) { b.float }
        synchronized(memory) { memory[k] = v }
        return v
    }

    private fun store(k: String, v: FloatArray) {
        synchronized(memory) { memory[k] = v }
        file(k)?.let { f ->
            runCatching {
                f.parentFile?.mkdirs()
                val b = ByteBuffer.allocate(v.size * 4)
                v.forEach { b.putFloat(it) }
                f.writeBytes(b.array())
            }
        }
    }

    /**
     * Vectors of [texts] ([passage]: documents, else a query): from the cache, missing ones computed – at most [limit]
     * new ones (a long folder is prepared ahead with [prepare]); null where none is there, null overall without model.
     */
    fun vectors(texts: List<String>, passage: Boolean, limit: Int = Int.MAX_VALUE): List<FloatArray?>? {
        val enc = encoder ?: return null
        if (!active) return null
        val keys = texts.map { key(it.take(4_000), passage) }
        val out = keys.map { cached(it) }.toMutableList()
        val missing = out.indices.filter { out[it] == null }.take(limit)
        for (batch in missing.chunked(16)) {
            val made = runCatching { enc(batch.map { texts[it].take(4_000) }, passage) }.getOrNull() ?: return out
            batch.zip(made).forEach { (i, v) -> out[i] = v; store(keys[i], v) }
        }
        return out
    }

    /** Computes the vectors of [texts] ahead (e.g. when a folder is opened); [progress] gets (done, total). */
    fun prepare(texts: List<String>, progress: (Int, Int) -> Unit = { _, _ -> }) {
        if (!active) return
        texts.chunked(16).forEachIndexed { i, chunk ->
            progress(i * 16, texts.size)
            vectors(chunk, passage = true) ?: return
        }
        progress(texts.size, texts.size)
    }

    fun cosine(a: FloatArray, b: FloatArray): Double {
        if (a.size != b.size) return 0.0
        var dot = 0.0
        var na = 0.0
        var nb = 0.0
        for (i in a.indices) { dot += a[i] * b[i]; na += a[i] * a[i]; nb += b[i] * b[i] }
        return if (na == 0.0 || nb == 0.0) 0.0 else dot / (sqrt(na) * sqrt(nb))
    }

    /** Cosine of [query] to each of [texts]; null overall without model, null for texts without vector. */
    fun similarity(query: String, texts: List<String>, limit: Int = Int.MAX_VALUE): List<Double?>? {
        if (texts.isEmpty() || !active) return null
        val q = vectors(listOf(query), passage = false)?.firstOrNull() ?: return null
        val vs = vectors(texts, passage = true, limit) ?: return null
        return vs.map { v -> v?.let { cosine(q, it) } }
    }

    /**
     * Mixed scores: word scores normalised by their maximum, cosines stretched to 0..1 over the texts that have one;
     * [WEIGHT] for the meaning. Texts without vector keep their word score.
     */
    fun mix(words: List<Double>, cos: List<Double?>): List<Double> {
        val maxWord = words.maxOrNull()?.takeIf { it > 0 } ?: 1.0
        val known = cos.filterNotNull()
        val lo = known.minOrNull() ?: 0.0
        val span = ((known.maxOrNull() ?: 1.0) - lo).takeIf { it > 1e-9 } ?: 1.0
        return words.indices.map { i ->
            val w = words[i] / maxWord
            val c = cos.getOrNull(i)
            if (c == null) w else WEIGHT * (c - lo) / span + (1 - WEIGHT) * w
        }
    }
}
