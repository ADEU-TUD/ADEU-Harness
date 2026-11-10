package io.github.thepytales.llmhub.data

import com.sun.jna.Function
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer
import com.sun.jna.StringArray
import com.sun.jna.WString
import com.sun.jna.ptr.PointerByReference
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
import java.util.zip.GZIPInputStream

/**
 * The recognition model of the pseudonymization (AP-67): a German BERT (gbert-base, MIT licence), fine-tuned on
 * invented texts, labels private persons, institutions (schools, day-care centres, practices …) and places with a
 * personal reference in milliseconds on the device. Built with tools/pii (model card: tools/pii/modell/MODELLKARTE.md).
 *
 * It is a download like the language models (Offene Frage 9): offered once in a popup when the pseudonymization is first
 * used or Einstellungen › Datenschutz is opened. The file comes from the branch `pii-modell` of the public repository at a
 * fixed commit and is checked by SHA-256. Without it – or without the ONNX Runtime the app ships with sherpa-onnx – the
 * rules of [PiiDetector] work alone.
 */
object PiiModelInfo {
    const val COMMIT = "806e33ca70948b03030a149f5f0255d145e69a5e"
    const val URL = "https://raw.githubusercontent.com/ADEU-TUD/ADEU-Harness/$COMMIT/pii-modell.int8.onnx.gz"
    const val GZ_SIZE = 72_057_318L
    const val GZ_SHA256 = "afd48b91fee421875325c06862c29806a417da2dfee3acd1aa631d74247ea4b3"
    const val FILE = "pii-modell.int8.onnx"
    const val SIZE = 110_099_426L
    const val SHA256 = "0e42df90ad38c060c7160a08de9d0e70ec242d519eaf7b49dc3131562ff89931"
    /** Download size shown to the user. */
    const val MEGABYTES = 72

    /** A token belongs to a detail when the model gives "not O" at least this probability (chosen on the Messkorpus). */
    const val THRESHOLD = 0.9f
    val LABELS = listOf("O", "B-PER", "I-PER", "B-EINR", "I-EINR", "B-ORT", "I-ORT")

    /** The WordPiece vocabulary of the model (resources/pii/vocab.txt, the same as tools/pii/modell/vocab.txt). */
    val vocab: Map<String, Int> by lazy {
        val stream = PiiModelInfo::class.java.getResourceAsStream("/pii/vocab.txt") ?: error("pii/vocab.txt fehlt")
        stream.bufferedReader(Charsets.UTF_8).useLines { lines -> lines.withIndex().associate { (i, t) -> t to i } }
    }
}

/**
 * BERT WordPiece tokenizer of the model (cased, no accent stripping), with the character offsets of every token – the
 * same as the fast tokenizer of Hugging Face that the model was trained with (checked by PiiModelTest).
 */
class WordPiece(private val vocab: Map<String, Int>) {
    data class Token(val id: Int, val start: Int, val end: Int)

    val cls = vocab.getValue("[CLS]")
    val sep = vocab.getValue("[SEP]")
    private val unk = vocab.getValue("[UNK]")

    fun tokenize(text: String): List<Token> {
        val out = ArrayList<Token>()
        var start = -1
        fun word(end: Int) {
            if (start >= 0) pieces(text, start, end, out)
            start = -1
        }
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            val n = Character.charCount(cp)
            when {
                cp == 0 || cp == 0xFFFD || isControl(cp) || isWhitespace(cp) -> word(i)
                isPunctuation(cp) || isCjk(cp) -> {
                    word(i)
                    pieces(text, i, i + n, out)
                }
                start < 0 -> start = i
            }
            i += n
        }
        word(text.length)
        return out
    }

    /** Greedy longest-match-first, the rest of a word with "##"; a word without a match is one [UNK]. */
    private fun pieces(text: String, from: Int, to: Int, out: MutableList<Token>) {
        if (text.codePointCount(from, to) > 100) {
            out += Token(unk, from, to)
            return
        }
        val found = ArrayList<Token>()
        var s = from
        while (s < to) {
            var e = to
            var id: Int? = null
            while (s < e) {
                val piece = (if (s > from) "##" else "") + text.substring(s, e)
                id = vocab[piece]
                if (id != null) break
                e = text.offsetByCodePoints(e, -1)
            }
            if (id == null) {
                out += Token(unk, from, to)
                return
            }
            found += Token(id, s, e)
            s = e
        }
        out += found
    }

    private fun isWhitespace(cp: Int) = cp == ' '.code || cp == '\t'.code || cp == '\n'.code || cp == '\r'.code ||
        Character.getType(cp) == Character.SPACE_SEPARATOR.toInt()

    private fun isControl(cp: Int): Boolean {
        if (cp == '\t'.code || cp == '\n'.code || cp == '\r'.code) return false
        val t = Character.getType(cp)
        return t == Character.CONTROL.toInt() || t == Character.FORMAT.toInt()
    }

    private fun isPunctuation(cp: Int): Boolean {
        if (cp in 33..47 || cp in 58..64 || cp in 91..96 || cp in 123..126) return true
        return when (Character.getType(cp).toByte()) {
            Character.CONNECTOR_PUNCTUATION, Character.DASH_PUNCTUATION, Character.START_PUNCTUATION, Character.END_PUNCTUATION,
            Character.INITIAL_QUOTE_PUNCTUATION, Character.FINAL_QUOTE_PUNCTUATION, Character.OTHER_PUNCTUATION -> true
            else -> false
        }
    }

    private fun isCjk(cp: Int) = cp in 0x4E00..0x9FFF || cp in 0x3400..0x4DBF || cp in 0x20000..0x2A6DF || cp in 0x2A700..0x2B73F ||
        cp in 0x2B740..0x2B81F || cp in 0x2B820..0x2CEAF || cp in 0xF900..0xFAFF || cp in 0x2F800..0x2FA1F
}

/** Runs a token classification model; the app uses [OrtClassifier], tests may use another one. */
fun interface TokenClassifier {
    /** Probabilities of the labels, [ids].size × labels, row by row. */
    fun probabilities(ids: LongArray): FloatArray
}

/**
 * The model on ONNX Runtime through its C API with JNA – the runtime is the one the app ships for sherpa-onnx (the face
 * detection of the video editor uses it the same way), so no second copy is loaded. Inputs input_ids, attention_mask,
 * token_type_ids (int64, 1 × n), output logits (float, 1 × n × labels).
 */
class OrtClassifier(
    lib: File, model: File, threads: Int = 2,
    /** Labels of the output: per token (the recognition model) or for the whole input ([perToken] = false, AP-71). */
    private val labels: Int = PiiModelInfo.LABELS.size,
    /** Names of the inputs the model has (DistilBERT has no token_type_ids). */
    inputs: List<String> = INPUTS,
    private val perToken: Boolean = true,
    name: String = "lora-pii",
) : TokenClassifier, AutoCloseable {
    private companion object {
        /** API version asked for: 11 = ONNX Runtime 1.11, all functions used here are older. */
        const val API_VERSION = 11
        // Indices into the OrtApi table (onnxruntime_c_api.h, stable across versions).
        const val GET_ERROR_MESSAGE = 2; const val CREATE_ENV = 3; const val CREATE_SESSION = 7; const val RUN = 9
        const val CREATE_SESSION_OPTIONS = 10; const val SET_INTRA_OP_THREADS = 24; const val CREATE_TENSOR_WITH_DATA = 49
        const val GET_TENSOR_DATA = 51; const val CREATE_CPU_MEMORY_INFO = 69; const val RELEASE_ENV = 92; const val RELEASE_STATUS = 93
        const val RELEASE_MEMORY_INFO = 94; const val RELEASE_SESSION = 95; const val RELEASE_VALUE = 96; const val RELEASE_SESSION_OPTIONS = 100
        const val INT64 = 7
        val INPUTS = listOf("input_ids", "attention_mask", "token_type_ids")
    }

    private val inputs = inputs.toList()
    private val native = NativeLibrary.getInstance(lib.absolutePath)
    private val api: Pointer = run {
        val base = native.getFunction("OrtGetApiBase").invokePointer(arrayOf<Any>()) ?: throw IllegalStateException("OrtGetApiBase")
        Function.getFunction(base.getPointer(0)).invokePointer(arrayOf(API_VERSION)) ?: throw IllegalStateException("ONNX Runtime zu alt")
    }

    private fun fn(i: Int): Function = Function.getFunction(api.getPointer(i.toLong() * Native.POINTER_SIZE))

    private fun call(i: Int, vararg args: Any?) {
        val status = fn(i).invokePointer(args) ?: return
        val msg = fn(GET_ERROR_MESSAGE).invokeString(arrayOf(status), false)
        fn(RELEASE_STATUS).invokeVoid(arrayOf(status))
        throw IllegalStateException("ONNX Runtime: $msg")
    }

    private fun out(i: Int, vararg args: Any?): Pointer {
        val ref = PointerByReference()
        call(i, *args, ref)
        return ref.value
    }

    private val env = out(CREATE_ENV, 3, name)
    private val options = out(CREATE_SESSION_OPTIONS).also { call(SET_INTRA_OP_THREADS, it, threads) }
    /** ORTCHAR_T is wchar_t on Windows and char elsewhere. */
    private val session = out(CREATE_SESSION, env, if (System.getProperty("os.name").orEmpty().startsWith("Windows")) WString(model.absolutePath) else model.absolutePath, options)
    private val memInfo = out(CREATE_CPU_MEMORY_INFO, 1, 0)
    private val inNames = StringArray(this.inputs.toTypedArray())
    private val outNames = StringArray(arrayOf("logits"))

    override fun probabilities(ids: LongArray): FloatArray = run(ids, null)

    /**
     * Runs the model on [ids] ([types]: the token_type_ids, zeros when null) and returns the probabilities of the labels:
     * row by row per token, or one row for the whole input.
     */
    @Synchronized
    fun run(ids: LongArray, types: LongArray?): FloatArray {
        val n = ids.size
        val rows = if (perToken) n else 1
        val shape = Memory(16).apply { setLong(0, 1); setLong(8, n.toLong()) }
        val buffers = inputs.map { input ->
            val values = when (input) { "input_ids" -> ids; "attention_mask" -> LongArray(n) { 1 }; else -> types ?: LongArray(n) }
            Memory(8L * n).apply { write(0, values, 0, n) }
        }
        val tensors = buffers.map { out(CREATE_TENSOR_WITH_DATA, memInfo, it, 8L * n, shape, 2L, INT64) }
        val inArr = Memory(tensors.size.toLong() * Native.POINTER_SIZE).apply { tensors.forEachIndexed { i, v -> setPointer(i.toLong() * Native.POINTER_SIZE, v) } }
        val outArr = Memory(Native.POINTER_SIZE.toLong()).apply { setPointer(0, Pointer.NULL) }
        try {
            call(RUN, session, Pointer.NULL, inNames, inArr, tensors.size.toLong(), outNames, 1L, outArr)
            val value = outArr.getPointer(0)
            try {
                val data = out(GET_TENSOR_DATA, value)
                val logits = data.getFloatArray(0, rows * labels)
                // Softmax per row.
                for (t in 0 until rows) {
                    val o = t * labels
                    var max = Float.NEGATIVE_INFINITY
                    for (k in 0 until labels) max = maxOf(max, logits[o + k])
                    var sum = 0f
                    for (k in 0 until labels) { logits[o + k] = kotlin.math.exp(logits[o + k] - max); sum += logits[o + k] }
                    for (k in 0 until labels) logits[o + k] /= sum
                }
                return logits
            } finally {
                fn(RELEASE_VALUE).invokeVoid(arrayOf(value))
            }
        } finally {
            tensors.forEach { fn(RELEASE_VALUE).invokeVoid(arrayOf(it)) }
        }
    }

    override fun close() {
        fn(RELEASE_MEMORY_INFO).invokeVoid(arrayOf(memInfo))
        fn(RELEASE_SESSION).invokeVoid(arrayOf(session))
        fn(RELEASE_SESSION_OPTIONS).invokeVoid(arrayOf(options))
        fn(RELEASE_ENV).invokeVoid(arrayOf(env))
    }
}

/**
 * Finds details with [classifier]: windows of 512 tokens with an overlap of 64 (a token counts as a detail if one window
 * says so), labelled tokens joined to details and widened to whole words – as tools/pii/modell.py does.
 */
class PiiRecognizer(private val classifier: TokenClassifier, private val tokenizer: WordPiece = WordPiece(PiiModelInfo.vocab)) {
    data class Span(val start: Int, val end: Int, val kind: String)

    fun spans(text: String, threshold: Float = PiiModelInfo.THRESHOLD): List<Span> {
        val tokens = tokenizer.tokenize(text)
        if (tokens.isEmpty()) return emptyList()
        val labels = PiiModelInfo.LABELS.size
        val best = arrayOfNulls<FloatArray>(tokens.size)
        val width = 510
        var start = 0
        while (true) {
            val end = minOf(tokens.size, start + width)
            val ids = LongArray(end - start + 2)
            ids[0] = tokenizer.cls.toLong()
            for (i in start until end) ids[i - start + 1] = tokens[i].id.toLong()
            ids[ids.size - 1] = tokenizer.sep.toLong()
            val p = classifier.probabilities(ids)
            for (i in start until end) {
                val row = p.copyOfRange((i - start + 1) * labels, (i - start + 2) * labels)
                if (best[i] == null || row[0] < best[i]!![0]) best[i] = row
            }
            if (end == tokens.size) break
            start = end - 64
        }
        val tagged = tokens.indices.map { i ->
            val row = best[i]!!
            val tag = if (1 - row[0] >= threshold) PiiModelInfo.LABELS[(1 until labels).maxBy { row[it] }] else "O"
            Triple(tokens[i].start, tokens[i].end, tag)
        }
        return group(tagged, text)
    }

    private fun group(tokens: List<Triple<Int, Int, String>>, text: String): List<Span> {
        val spans = ArrayList<IntArray>()
        val kinds = ArrayList<String>()
        for ((s, e, tag) in tokens) {
            if (tag == "O") continue
            val kind = tag.substring(2)
            val last = spans.lastOrNull()
            val gap = if (last != null && s >= last[1]) text.substring(last[1], s) else null
            if (last != null && kinds.last() == kind && gap != null && (tag.startsWith("I-") || gap.isEmpty()) && gap.trim(' ', '-').isEmpty()) {
                last[1] = e
            } else {
                spans += intArrayOf(s, e)
                kinds += kind
            }
        }
        val words = WORD.findAll(text).map { it.range }.toList()
        return spans.mapIndexed { k, se ->
            var s = se[0]
            var e = se[1]
            for (w in words) if (w.first < e && w.last + 1 > s) { s = minOf(s, w.first); e = maxOf(e, w.last + 1) }
            Span(s, e, kinds[k])
        }
    }

    /** The details as hits of the pseudonymization; well-known people ([PiiDetector.famousRanges]) are left out. */
    fun hits(text: String): List<PiiHit> {
        val famous = PiiDetector.famousRanges(text)
        val out = LinkedHashSet<PiiHit>()
        for (sp in spans(text)) {
            if (famous.any { it.first <= sp.start && it.last + 1 >= sp.end }) continue
            val value = text.substring(sp.start, sp.end).trim()
            if (value.length < 2) continue
            when (sp.kind) {
                "PER" -> {
                    val base = baseForm(value, text)
                    out += PiiHit(base, PiiKind.NAME)
                    // The parts too, so "Frau Müller" later in the text gets the same treatment.
                    val parts = base.split(' ').filter { NAME_PART.matches(it) }
                    if (parts.size in 2..4) parts.forEach { out += PiiHit(it, PiiKind.NAME) }
                }
                "EINR" -> out += PiiHit(value, PiiKind.SCHOOL)
                else -> out += PiiHit(value, PiiKind.PLACE)
            }
        }
        return out.toList()
    }

    /** "Annas" → "Anna" when the bare form is a known first name or occurs in the text (the placeholder bends it back). */
    private fun baseForm(value: String, text: String): String {
        val whole = value.substringAfterLast(' ')
        if (whole in Names.first || whole in Names.last) return value  // "Jonas", "Klaus" are names of their own
        val m = Regex("^(.+?)(?:'s|’s|s|'|’)$").find(value) ?: return value
        val base = m.groupValues[1]
        val last = base.substringAfterLast(' ')
        return if (last.length >= 2 && (last in Names.first || last in Names.last || PiiDetector.containsWord(text, base, false))) base else value
    }

    private companion object {
        /** A word as the Messkorpus counts it (tools/pii/corpus.py). */
        val WORD = Regex("[\\p{L}\\p{N}]+(?:[-'’.@][\\p{L}\\p{N}]+)*")

        /** A part of a name that may stand alone ("Müller", "Anna-Lena"), no initial ("A.") and no particle ("von"). */
        val NAME_PART = Regex("\\p{Lu}[\\p{L}'’-]+")
    }
}

/**
 * The model on this device: download (Einstellungen › Datenschutz or the popup), loading in the background and the
 * hits for the [Pseudonymizer], cached per text because every request masks the whole chat again.
 */
class PiiModel(private val c: AppContainer) {
    data class State(
        val installed: Boolean = false,
        /** Loaded and ready; false while loading or when the runtime is missing. */
        val ready: Boolean = false,
        /** 0..1 while the download runs. */
        val progress: Double? = null,
        val message: String? = null,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val root get() = PiiRuntime.root(c)
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    /** The popup offering the download is shown while this is true (AppRoot, PiiModelPrompt). */
    val offer = MutableStateFlow(false)
    @Volatile private var asked = false
    @Volatile private var cancelled = false
    @Volatile private var recognizer: PiiRecognizer? = null
    @Volatile private var classifier: OrtClassifier? = null

    private val cache = object : LinkedHashMap<String, List<PiiHit>>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<PiiHit>>?) = size > 400
    }

    fun file(): File = File(root, PiiModelInfo.FILE)

    fun installed(): Boolean = file().let { it.isFile && it.length() == PiiModelInfo.SIZE }

    init {
        _state.value = State(installed = installed())
        load()
    }

    /** Offers the download once per start of the app – unless it is there, switched off or „Nicht mehr fragen“. */
    fun offerIfNeeded() {
        val s = c.settings.current
        if (asked || installed() || _state.value.progress != null || !s.pseudonymModel || !s.pseudonymModelAsk) return
        asked = true
        offer.value = true
    }

    fun install() {
        if (_state.value.progress != null) return
        cancelled = false
        _state.value = _state.value.copy(progress = 0.0, message = null)
        scope.launch {
            val gz = File(root, PiiModelInfo.FILE + ".gz.part")
            val part = File(root, PiiModelInfo.FILE + ".part")
            try {
                FileDownload.fetch(PiiModelInfo.URL, gz, PiiModelInfo.GZ_SIZE, { cancelled }) { p -> _state.value = _state.value.copy(progress = p) }
                _state.value = _state.value.copy(progress = 1.0, message = tr("Erkennungsmodell wird geprüft …"))
                if (gz.length() != PiiModelInfo.GZ_SIZE || FileDownload.sha256(gz) != PiiModelInfo.GZ_SHA256) {
                    gz.delete()
                    error(tr("Prüfsumme stimmt nicht – die Datei ist beschädigt"))
                }
                GZIPInputStream(gz.inputStream().buffered(1 shl 20)).use { i -> part.outputStream().use { o -> i.copyTo(o, 1 shl 20) } }
                if (part.length() != PiiModelInfo.SIZE || FileDownload.sha256(part) != PiiModelInfo.SHA256) {
                    part.delete()
                    error(tr("Prüfsumme stimmt nicht – die Datei ist beschädigt"))
                }
                file().delete()
                if (!part.renameTo(file())) error(tr("Datei konnte nicht gespeichert werden"))
                gz.delete()
                _state.value = State(installed = true, message = tr("Erkennungsmodell ist bereit."))
                load()
            } catch (e: InterruptedException) {
                _state.value = _state.value.copy(progress = null, message = tr("Download abgebrochen – beim nächsten Mal wird fortgesetzt."))
            } catch (e: Exception) {
                _state.value = _state.value.copy(progress = null, message = tr("Download des Erkennungsmodells fehlgeschlagen: {0}. Bis dahin arbeiten die Regeln allein.", e.message))
            } finally {
                if (_state.value.progress != null) _state.value = _state.value.copy(progress = null)
            }
        }
    }

    fun cancelInstall() { cancelled = true }

    fun delete() {
        scope.launch {
            unload()
            file().delete()
            File(root, PiiModelInfo.FILE + ".gz.part").delete()
            File(root, PiiModelInfo.FILE + ".part").delete()
            _state.value = State(installed = false, message = tr("Erkennungsmodell gelöscht."))
        }
    }

    /** Loads the model in the background (about a second); until then the rules work alone. */
    fun load() {
        if (!installed() || recognizer != null) return
        scope.launch {
            val lib = PiiRuntime.library(c)
            if (lib == null) {
                _state.value = _state.value.copy(message = tr("Für das Erkennungsmodell fehlt die ONNX Runtime der App (Spracherkennung) – die Regeln arbeiten allein."))
                return@launch
            }
            runCatching { OrtClassifier(lib, file()) }.logFailure("Erkennungsmodell laden").getOrNull()?.let { cl ->
                classifier = cl
                recognizer = PiiRecognizer(cl)
                _state.value = _state.value.copy(ready = true)
            }
        }
    }

    private fun unload() {
        recognizer = null
        _state.value = _state.value.copy(ready = false)
        classifier?.let { cl -> runCatching { cl.close() } }
        classifier = null
        synchronized(cache) { cache.clear() }
    }

    /** The details the model finds in [text], or null when it is not ready (the rules then guess names as before). */
    fun find(text: String): List<PiiHit>? {
        val r = recognizer ?: return null
        if (!c.settings.current.pseudonymModel) return null
        synchronized(cache) { cache[text] }?.let { return it }
        val hits = runCatching { r.hits(text) }.logFailure("Erkennungsmodell").getOrNull() ?: return null
        synchronized(cache) { cache[text] = hits }
        return hits
    }
}
