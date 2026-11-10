package io.github.thepytales.llmhub.data

import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.logFailure
import io.github.thepytales.llmhub.ui.tr
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.text.Normalizer
import kotlin.math.sqrt

/**
 * Suche nach Bedeutung auf dem Gerät (AP-107): the embedding model the Wissenssammlung uses without the server.
 *
 * Chosen on the Entwicklungsteil of tools/suche/messkorpus (tools/suche/README.md): multilingual-e5-small by Microsoft
 * (intfloat, MIT licence), the int8 ONNX file of the original repository, unchanged – „Lora Edition“ only means chosen,
 * measured and set up for Lora by us. It comes from Hugging Face at a fixed commit and is checked by SHA-256, like Lora
 * Namensschutz; it runs on the ONNX Runtime the app ships for sherpa-onnx.
 */
object LocalEmbeddingInfo {
    const val NAME = "multilingual-e5-small – Lora Edition"
    /** What [KnowledgeDoc.embeddedWith] says for passages embedded on this device. */
    const val ID = "local/multilingual-e5-small"
    const val REPO = "intfloat/multilingual-e5-small"
    const val COMMIT = "614241f622f53c4eeff9890bdc4f31cfecc418b3"
    const val MODEL_URL = "https://huggingface.co/$REPO/resolve/$COMMIT/onnx/model_qint8_avx512_vnni.onnx"
    const val MODEL_FILE = "multilingual-e5-small.int8.onnx"
    const val MODEL_SIZE = 118_346_824L
    const val MODEL_SHA256 = "dd476dd0c2514e9b9be83aeb3853fac0763e0bdf4a71645407587d77c48a2d88"
    const val VOCAB_URL = "https://huggingface.co/$REPO/resolve/$COMMIT/sentencepiece.bpe.model"
    const val VOCAB_FILE = "multilingual-e5-small.spm"
    const val VOCAB_SIZE = 5_069_051L
    const val VOCAB_SHA256 = "cfc8146abe2a0488e9e2a0c56de7952f7c11ab059eca145a0a727afce0db2865"
    const val MEGABYTES = 118 + 5

    /** e5 wants these in front of the texts. */
    const val QUERY = "query: "
    const val PASSAGE = "passage: "
    const val MAX_TOKENS = 512

}

/**
 * The SentencePiece tokenizer of XLM-RoBERTa (Unigram), read from `sentencepiece.bpe.model`, with the ids of the
 * Hugging Face tokenizer: <s> 0, <pad> 1, </s> 2, <unk> 3, every other piece its SentencePiece id + 1. Normalization is
 * NFKC with collapsed whitespace and „▁“ for the spaces – the same tokens as the fast tokenizer the model was made with
 * on the Messkorpus (LocalEmbeddingTest).
 */
class SentencePiece(bytes: ByteArray) {
    private val ids = HashMap<String, Int>(300_000)
    private val scores: FloatArray
    private val maxLen: Int
    private val unkScore: Float

    init {
        val pieces = ArrayList<Triple<String, Float, Int>>(260_000)
        Proto(bytes).forEach { field, value ->
            if (field == 1 && value is ByteArray) {
                var piece = ""
                var score = 0f
                var type = 1
                Proto(value).forEach { f, v ->
                    when (f) {
                        1 -> piece = String(v as ByteArray, Charsets.UTF_8)
                        2 -> score = java.lang.Float.intBitsToFloat((v as Long).toInt())
                        3 -> type = (v as Long).toInt()
                    }
                }
                pieces += Triple(piece, score, type)
            }
        }
        require(pieces.size > 1000) { "SentencePiece-Modell leer" }
        scores = FloatArray(pieces.size)
        var longest = 1
        var min = Float.MAX_VALUE
        pieces.forEachIndexed { i, (p, sc, type) ->
            scores[i] = sc
            // 1 normal, 4 user defined; unknown, control and unused pieces are never matched in the text.
            if (type == 1 || type == 4) {
                ids[p] = i
                longest = maxOf(longest, p.length)
                min = minOf(min, sc)
            }
        }
        maxLen = longest
        unkScore = min - 10f
    }

    /** Text as SentencePiece sees it: NFKC, whitespace collapsed, „▁“ for spaces and in front. */
    fun normalize(text: String): String {
        val n = Normalizer.normalize(text, Normalizer.Form.NFKC)
        val sb = StringBuilder(n.length + 1)
        var space = true
        for (ch in n) {
            val blank = ch.isWhitespace() || ch == '\u0000'
            if (blank) {
                if (!space) sb.append(' ')
                space = true
            } else if (!Character.isISOControl(ch)) {
                sb.append(ch)
                space = false
            }
        }
        // Like the fast tokenizer the model was measured with: whitespace at the end stays as one „▁“.
        val body = sb.toString()
        val trimmed = body.trimEnd()
        return if (trimmed.isEmpty()) "" else "▁" + trimmed.replace(' ', '▁') + if (body.length > trimmed.length) "▁" else ""
    }

    /** Piece ids (SentencePiece numbering, -1 = unknown) of the best segmentation (Viterbi over the piece scores). */
    fun pieces(text: String): IntArray {
        val s = normalize(text)
        if (s.isEmpty()) return IntArray(0)
        val n = s.length
        val best = DoubleArray(n + 1) { Double.NEGATIVE_INFINITY }
        val from = IntArray(n + 1)
        val piece = IntArray(n + 1) { -1 }
        best[0] = 0.0
        for (end in 1..n) {
            // Longest piece first: on equal scores the earlier start wins, as in SentencePiece and Hugging Face.
            for (len in minOf(maxLen, end) downTo 1) {
                val start = end - len
                if (best[start] == Double.NEGATIVE_INFINITY) continue
                // Never cut a surrogate pair.
                if (Character.isLowSurrogate(s[start]) && start > 0) continue
                val id = ids[s.substring(start, end)] ?: continue
                val score = best[start] + scores[id]
                if (score > best[end]) { best[end] = score; from[end] = start; piece[end] = id }
            }
            if (best[end] == Double.NEGATIVE_INFINITY || piece[end] < 0) {
                // One unknown character (a whole code point).
                val start = if (end >= 2 && Character.isLowSurrogate(s[end - 1]) && Character.isHighSurrogate(s[end - 2])) end - 2 else end - 1
                if (best[start] != Double.NEGATIVE_INFINITY && best[start] + unkScore > best[end]) {
                    best[end] = best[start] + unkScore; from[end] = start; piece[end] = -1
                }
            }
        }
        val out = ArrayList<Int>()
        var at = n
        while (at > 0) {
            out += piece[at]
            at = from[at]
        }
        out.reverse()
        // Unknown characters next to each other become one unknown token, as in SentencePiece.
        val merged = ArrayList<Int>(out.size)
        for (id in out) if (!(id < 0 && merged.lastOrNull() == -1)) merged += id
        return merged.toIntArray()
    }

    /** Ids for XLM-RoBERTa models with <s> … </s>, at most [max] in all. */
    fun encode(text: String, max: Int = LocalEmbeddingInfo.MAX_TOKENS): LongArray {
        val p = pieces(text)
        val body = minOf(p.size, max - 2)
        val out = LongArray(body + 2)
        out[0] = 0
        for (i in 0 until body) out[i + 1] = if (p[i] < 0) 3L else p[i] + 1L
        out[body + 1] = 2
        return out
    }

    /** A minimal protobuf reader: fields of one message (varint → Long, 32/64 bit → Long, length → ByteArray). */
    private class Proto(private val b: ByteArray) {
        private var i = 0

        private fun varint(): Long {
            var shift = 0
            var r = 0L
            while (true) {
                val x = b[i++].toInt() and 0xFF
                r = r or ((x and 0x7F).toLong() shl shift)
                if (x and 0x80 == 0) return r
                shift += 7
            }
        }

        fun forEach(f: (Int, Any) -> Unit) {
            while (i < b.size) {
                val key = varint()
                val field = (key ushr 3).toInt()
                when ((key and 7).toInt()) {
                    0 -> f(field, varint())
                    1 -> { var v = 0L; for (k in 0 until 8) v = v or ((b[i + k].toLong() and 0xFF) shl (8 * k)); i += 8; f(field, v) }
                    2 -> { val len = varint().toInt(); f(field, b.copyOfRange(i, i + len)); i += len }
                    5 -> { var v = 0L; for (k in 0 until 4) v = v or ((b[i + k].toLong() and 0xFF) shl (8 * k)); i += 4; f(field, v) }
                    else -> error("Protobuf: Typ ${key and 7}")
                }
            }
        }
    }
}

/** Turns texts into vectors; the app uses [OrtClassifier] with e5-small, tests may use another one. */
fun interface TextEncoder {
    /** Hidden states, ids.size × dim, row by row. */
    fun hidden(ids: LongArray): FloatArray
}

/** Mean of the token vectors, normalized to length 1 – how e5 makes one vector of a text. */
fun meanPool(hidden: FloatArray, tokens: Int): FloatArray {
    val dim = hidden.size / tokens
    val v = FloatArray(dim)
    for (t in 0 until tokens) for (k in 0 until dim) v[k] += hidden[t * dim + k]
    var norm = 0.0
    for (k in 0 until dim) { v[k] /= tokens; norm += v[k] * v[k] }
    val n = sqrt(norm).toFloat().takeIf { it > 0f } ?: 1f
    for (k in 0 until dim) v[k] /= n
    return v
}

/**
 * The model on this device: download (Einstellungen › Wissen), loading in the background, vectors for queries and
 * passages – and, once it is ready, the documents of the Wissenssammlung that were not embedded with it yet.
 */
class LocalEmbedding(private val c: AppContainer) {
    data class State(
        val installed: Boolean = false,
        val ready: Boolean = false,
        /** 0..1 while the download runs. */
        val progress: Double? = null,
        val message: String? = null,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val root get() = File(PiiRuntime.root(c).parentFile, "suche")
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()
    @Volatile private var cancelled = false
    @Volatile private var tokenizer: SentencePiece? = null
    @Volatile private var encoder: OrtClassifier? = null

    fun modelFile() = File(root, LocalEmbeddingInfo.MODEL_FILE)
    fun vocabFile() = File(root, LocalEmbeddingInfo.VOCAB_FILE)

    fun installed(): Boolean = modelFile().let { it.isFile && it.length() == LocalEmbeddingInfo.MODEL_SIZE } &&
        vocabFile().let { it.isFile && it.length() == LocalEmbeddingInfo.VOCAB_SIZE }

    /** Ready and switched on: the Wissenssammlung searches with it. */
    val active: Boolean get() = _state.value.ready && c.settings.current.semanticLocal

    init {
        _state.value = State(installed = installed())
        load()
    }

    fun install() {
        if (_state.value.progress != null) return
        cancelled = false
        _state.value = _state.value.copy(progress = 0.0, message = null)
        scope.launch {
            try {
                fetch(LocalEmbeddingInfo.VOCAB_URL, vocabFile(), LocalEmbeddingInfo.VOCAB_SIZE, LocalEmbeddingInfo.VOCAB_SHA256, 0.0, 0.04)
                fetch(LocalEmbeddingInfo.MODEL_URL, modelFile(), LocalEmbeddingInfo.MODEL_SIZE, LocalEmbeddingInfo.MODEL_SHA256, 0.04, 0.96)
                _state.value = State(installed = true, message = tr("{0} ist bereit.", LocalEmbeddingInfo.NAME))
                load()
            } catch (e: InterruptedException) {
                _state.value = _state.value.copy(progress = null, message = tr("Download abgebrochen – beim nächsten Mal wird fortgesetzt."))
            } catch (e: Exception) {
                _state.value = _state.value.copy(progress = null, message = tr("Download von {0} fehlgeschlagen: {1}. Bis dahin sucht die App nach Wörtern (oder mit dem Uni-Server).", LocalEmbeddingInfo.NAME, e.message))
            } finally {
                if (_state.value.progress != null) _state.value = _state.value.copy(progress = null)
            }
        }
    }

    private fun fetch(url: String, target: File, size: Long, sha: String, from: Double, share: Double) {
        if (target.isFile && target.length() == size) return
        val part = File(target.path + ".part")
        FileDownload.fetch(url, part, size, { cancelled }) { p -> _state.value = _state.value.copy(progress = from + p * share) }
        if (part.length() != size || FileDownload.sha256(part) != sha) {
            part.delete()
            error(tr("Prüfsumme stimmt nicht – die Datei ist beschädigt"))
        }
        target.delete()
        if (!part.renameTo(target)) error(tr("Datei konnte nicht gespeichert werden"))
    }

    fun cancelInstall() { cancelled = true }

    fun delete() {
        scope.launch {
            unload()
            listOf(modelFile(), vocabFile()).forEach { f -> f.delete(); File(f.path + ".part").delete() }
            _state.value = State(installed = false, message = tr("{0} gelöscht.", LocalEmbeddingInfo.NAME))
        }
    }

    /** Loads tokenizer and model in the background (about a second); then documents are embedded that miss it. */
    fun load() {
        if (encoder != null || !installed()) return
        scope.launch {
            val lib = PiiRuntime.library(c)
            if (lib == null) {
                _state.value = _state.value.copy(message = tr("Für {0} fehlt die ONNX Runtime der App (Spracherkennung) – die App sucht nach Wörtern.", LocalEmbeddingInfo.NAME))
                return@launch
            }
            runCatching {
                tokenizer = SentencePiece(vocabFile().readBytes())
                encoder = OrtClassifier(lib, modelFile(), labels = 384, perToken = true, name = "lora-suche", output = "last_hidden_state", softmax = false)
            }.logFailure("Suche nach Bedeutung laden").onSuccess {
                _state.value = _state.value.copy(ready = true)
                catchUp()
            }
        }
    }

    private fun unload() {
        _state.value = _state.value.copy(ready = false)
        encoder?.let { runCatching { it.close() } }
        encoder = null
        tokenizer = null
    }

    /** Vectors of [texts] ([passage]: documents, else search queries); null when the model is not ready. */
    fun embed(texts: List<String>, passage: Boolean): List<FloatArray>? {
        val tok = tokenizer ?: return null
        val enc = encoder ?: return null
        val prefix = if (passage) LocalEmbeddingInfo.PASSAGE else LocalEmbeddingInfo.QUERY
        return texts.map { t ->
            val ids = tok.encode(prefix + t)
            meanPool(enc.run(ids, LongArray(ids.size)), ids.size)
        }
    }

    /** The documents of the Wissenssammlung that have no vectors of this model yet (after the download or a switch). */
    fun catchUp() {
        if (!active) return
        scope.launch {
            c.knowledge.docs.value.filter { it.embeddedWith != LocalEmbeddingInfo.ID }.forEach { d ->
                if (!active) return@launch
                c.knowledge.embed(d, LocalEmbeddingInfo.ID) { texts -> embed(texts, passage = true) ?: error("nicht bereit") }
            }
        }
    }
}
