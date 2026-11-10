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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.util.zip.GZIPInputStream

/**
 * „Lora Befehle“, the command model (AP-71; up to 1.0.65 „Befehlsmodell“): a small German encoder
 * (distilbert-base-german-cased, Apache-2.0), fine-tuned and changed by us on invented wishes, maps what is typed into Lora or Strg+K to one action of the app in milliseconds on the device – or says that
 * it is a free request for a language model. Built with tools/befehle (model card: resources/befehle/MODELLKARTE.md);
 * the labels come from the code ([io.github.thepytales.llmhub.agent.LoraCommands]).
 *
 * A download like the recognition model of the pseudonymization (AP-67): offered once in a popup, from the branch
 * `befehle-modell` of the public repository at a fixed commit, checked by SHA-256. Without it – or without the ONNX
 * Runtime the app ships with sherpa-onnx – Lora works as before (rules on the device, then a language model). Who still
 * has the model of 1.0.65 ([OLD_FILE], same labels and vocabulary) keeps using it until the new one is downloaded
 * (offered once as an update); then the old file is deleted.
 */
object CommandModelInfo {
    /** The name shown in the app. */
    const val NAME = "Lora Befehle"
    const val COMMIT = "cc168774477e3ac8b439b42fd7b03ab05c9908fa"
    const val URL = "https://raw.githubusercontent.com/ADEU-TUD/ADEU-Harness/$COMMIT/lora-befehle-pytales.int8.onnx.gz"
    const val GZ_SIZE = 40_900_860L // TODO-NEU
    const val GZ_SHA256 = "ee13766aeeda7983470cdc3ff0da6989de28bc20091d3f3b329d6d9242883e37"
    const val FILE = "lora-befehle-pytales.int8.onnx"
    const val SIZE = 67_956_881L // TODO-NEU
    const val SHA256 = "3251a9ef4dd4104164bf861c0ac0d6778f0d8ef1c5dc8c286e02e017ab803f65"
    /** Download size shown to the user. */
    const val MEGABYTES = 41 // TODO-NEU

    /** Below this probability of the likeliest label nothing is carried out (chosen on the Messkorpus, schwelle.json). */
    const val THRESHOLD = 0.8f // TODO-NEU
    /**
     * Rückfrage mit zwei Knöpfen (AP-73): below [THRESHOLD], Lora asks „Meinst du … oder …?“ when the two likeliest
     * commands together reach this probability (chosen on the development part only, tools/befehle/rueckfrage.py and
     * modell/rueckfrage.json); otherwise the wish goes on to a language model as before.
     */
    const val CHOICE_BOUND = 0.3f // TODO-NEU

    /** The model up to 1.0.65 („Befehlsmodell“) with its threshold and bound: used until the new one is there. */
    const val OLD_FILE = "befehle-modell.int8.onnx"
    const val OLD_SIZE = 67_956_881L
    const val OLD_THRESHOLD = 0.8f
    const val OLD_CHOICE_BOUND = 0.3f
    /** Inputs of the ONNX file (DistilBERT has no token_type_ids). */
    val INPUTS = listOf("input_ids", "attention_mask")

    /** The labels in the order of the model's output (resources/befehle/labels.json, written by tools/befehle/export.py). */
    val labels: List<String> by lazy {
        val stream = CommandModelInfo::class.java.getResourceAsStream("/befehle/labels.json") ?: error("befehle/labels.json fehlt")
        Json.parseToJsonElement(stream.bufferedReader(Charsets.UTF_8).readText()).jsonArray.map { it.jsonPrimitive.content }
    }

    /** The WordPiece vocabulary of the model (resources/befehle/vocab.txt). */
    val vocab: Map<String, Int> by lazy {
        val stream = CommandModelInfo::class.java.getResourceAsStream("/befehle/vocab.txt") ?: error("befehle/vocab.txt fehlt")
        stream.bufferedReader(Charsets.UTF_8).useLines { lines -> lines.withIndex().associate { (i, t) -> t to i } }
    }
}

/** Runs a sentence classifier; the app uses [OrtClassifier], tests may use another one. */
fun interface SentenceClassifier {
    /** Probabilities of the labels for the token [ids] with their segment [types] (0 = area, 1 = wish). */
    fun probabilities(ids: LongArray, types: LongArray): FloatArray
}

/**
 * Recognizes the command in a wish with [classifier]: the input is what is open (one word) and the wish in lower case as
 * a pair `[CLS] area [SEP] wish [SEP]` – exactly as tools/befehle/modell.py encodes it for the training.
 */
class CommandRecognizer(
    private val classifier: SentenceClassifier,
    private val tokenizer: WordPiece = WordPiece(CommandModelInfo.vocab),
    private val labels: List<String> = CommandModelInfo.labels,
    /** Threshold and bound of the loaded model (the new one, or the old file of 1.0.65). */
    private val threshold: Float = CommandModelInfo.THRESHOLD,
    private val bound: Float = CommandModelInfo.CHOICE_BOUND,
) {
    /**
     * [sure]: confident enough to act; otherwise the wish is passed on to a language model – or, with two [choices]
     * (the two likeliest commands, AP-73), Lora asks which one is meant.
     */
    data class Result(val label: String, val confidence: Float, val sure: Boolean, val choices: List<String> = emptyList()) {
        val free: Boolean get() = label.startsWith("frei.")
    }

    /** Token ids and segment ids of the pair; the wish is cut so that the whole stays within [MAX_LEN] tokens. */
    fun encode(wish: String, area: String): Pair<LongArray, LongArray> {
        val first = tokenizer.tokenize(AREAS[area] ?: AREAS.getValue("app"))
        val second = tokenizer.tokenize(normalize(wish)).take(MAX_LEN - first.size - 3)
        val ids = LongArray(first.size + second.size + 3)
        val types = LongArray(ids.size)
        var i = 0
        ids[i++] = tokenizer.cls.toLong()
        first.forEach { ids[i++] = it.id.toLong() }
        ids[i++] = tokenizer.sep.toLong()
        second.forEach { types[i] = 1; ids[i++] = it.id.toLong() }
        types[i] = 1
        ids[i] = tokenizer.sep.toLong()
        return ids to types
    }

    fun recognize(wish: String, area: String = "app", threshold: Float = this.threshold, bound: Float = this.bound): Result {
        val (ids, types) = encode(wish, area)
        val p = classifier.probabilities(ids, types)
        var best = 0
        for (k in labels.indices) if (p[k] > p[best]) best = k
        val sure = p[best] >= threshold
        return Result(labels[best], p[best], sure, if (sure) emptyList() else choices(p, bound))
    }

    /**
     * The two likeliest labels that are not free, when together they reach [bound] – the same rule as `asks` in
     * tools/befehle/rueckfrage.py. Free requests carry their probability on `frei.*` and get no buttons.
     */
    private fun choices(p: FloatArray, bound: Float): List<String> {
        var first = -1
        var second = -1
        for (k in labels.indices) {
            if (labels[k].startsWith("frei.")) continue
            if (first < 0 || p[k] > p[first]) { second = first; first = k } else if (second < 0 || p[k] > p[second]) second = k
        }
        return if (first < 0 || second < 0 || p[first] + p[second] < bound) emptyList() else listOf(labels[first], labels[second])
    }

    companion object {
        const val MAX_LEN = 64

        /** What is open, as the word the model was trained with (keys: [io.github.thepytales.llmhub.agent.ActionArea.key], "app" = none). */
        val AREAS = mapOf(
            "app" to "App", "files" to "Dateien", "pdf" to "PDF", "doc" to "Dokument", "sheet" to "Tabelle", "slides" to "Folien",
            "video" to "Video", "mail" to "Mail",
            // The names of the Messkorpus (tools/befehle/messkorpus) for the tests.
            "dateien" to "Dateien", "dokument" to "Dokument", "tabelle" to "Tabelle", "folien" to "Folien",
        )

        /** Lower case and single spaces – the same as normalize in tools/befehle/modell.py. */
        fun normalize(text: String): String = text.lowercase().replace(Regex("\\s+"), " ").trim()
    }
}

/**
 * The model on this device: download (Einstellungen › Lora or the popup), loading in the background and the recognition
 * for Lora and Strg+K, cached per wish because Strg+K asks again with every key.
 */
class CommandModel(private val c: AppContainer) {
    data class State(
        val installed: Boolean = false,
        /** Only the model of 1.0.65 is there: it keeps working, the new version is offered as an update. */
        val old: Boolean = false,
        /** Loaded and ready; false while loading or when the runtime is missing. */
        val ready: Boolean = false,
        /** 0..1 while the download runs. */
        val progress: Double? = null,
        val message: String? = null,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val root get() = File(PiiRuntime.root(c).parentFile, "befehle")
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    /** The popup offering the download is shown while this is true (AppRoot, CommandModelPrompt). */
    val offer = MutableStateFlow(false)
    @Volatile private var asked = false
    @Volatile private var cancelled = false
    @Volatile private var recognizer: CommandRecognizer? = null
    @Volatile private var classifier: OrtClassifier? = null

    private val cache = object : LinkedHashMap<String, CommandRecognizer.Result>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CommandRecognizer.Result>?) = size > 300
    }

    fun file(): File = File(root, CommandModelInfo.FILE)

    /** There is a model to download at all (a build without a published model offers nothing). */
    val available: Boolean get() = CommandModelInfo.SIZE > 0

    fun installed(): Boolean = available && file().let { it.isFile && it.length() == CommandModelInfo.SIZE }

    /** The model of 1.0.65 („Befehlsmodell“). */
    fun oldFile(): File = File(root, CommandModelInfo.OLD_FILE)

    /** Only the old model is there (it keeps working until the update is downloaded). */
    fun hasOld(): Boolean = !installed() && oldFile().let { it.isFile && it.length() == CommandModelInfo.OLD_SIZE }

    init {
        _state.value = State(installed = installed(), old = hasOld())
        load()
    }

    /**
     * Offers the download once per start of the app – unless it is there, switched off or „Nicht mehr fragen“. Who has
     * the old model gets the new one offered as an update (with its own „Nicht mehr fragen“).
     */
    fun offerIfNeeded() {
        val s = c.settings.current
        if (asked || !available || installed() || _state.value.progress != null || !s.commandModel) return
        if (if (hasOld()) !s.commandModelUpdateAsk else !s.commandModelAsk) return
        asked = true
        offer.value = true
    }

    fun install() {
        if (_state.value.progress != null || !available) return
        cancelled = false
        _state.value = _state.value.copy(progress = 0.0, message = null)
        scope.launch {
            val gz = File(root, CommandModelInfo.FILE + ".gz.part")
            val part = File(root, CommandModelInfo.FILE + ".part")
            try {
                FileDownload.fetch(CommandModelInfo.URL, gz, CommandModelInfo.GZ_SIZE, { cancelled }) { p -> _state.value = _state.value.copy(progress = p) }
                _state.value = _state.value.copy(progress = 1.0, message = tr("{0} wird geprüft …", CommandModelInfo.NAME))
                if (gz.length() != CommandModelInfo.GZ_SIZE || FileDownload.sha256(gz) != CommandModelInfo.GZ_SHA256) {
                    gz.delete()
                    error(tr("Prüfsumme stimmt nicht – die Datei ist beschädigt"))
                }
                GZIPInputStream(gz.inputStream().buffered(1 shl 20)).use { i -> part.outputStream().use { o -> i.copyTo(o, 1 shl 20) } }
                if (part.length() != CommandModelInfo.SIZE || FileDownload.sha256(part) != CommandModelInfo.SHA256) {
                    part.delete()
                    error(tr("Prüfsumme stimmt nicht – die Datei ist beschädigt"))
                }
                unload()
                file().delete()
                if (!part.renameTo(file())) error(tr("Datei konnte nicht gespeichert werden"))
                gz.delete()
                oldFile().delete()
                _state.value = State(installed = true, message = tr("{0} ist bereit.", CommandModelInfo.NAME))
                load()
            } catch (e: InterruptedException) {
                _state.value = _state.value.copy(progress = null, message = tr("Download abgebrochen – beim nächsten Mal wird fortgesetzt."))
            } catch (e: Exception) {
                _state.value = _state.value.copy(progress = null, message = if (hasOld()) tr("Download von {0} fehlgeschlagen: {1}. Bis dahin arbeitet die bisherige Version weiter.", CommandModelInfo.NAME, e.message)
                    else tr("Download von {0} fehlgeschlagen: {1}. Bis dahin arbeitet Lora wie bisher.", CommandModelInfo.NAME, e.message))
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
            oldFile().delete()
            File(root, CommandModelInfo.FILE + ".gz.part").delete()
            File(root, CommandModelInfo.FILE + ".part").delete()
            _state.value = State(installed = false, message = tr("{0} gelöscht.", CommandModelInfo.NAME))
        }
    }

    /** Loads the model in the background (well under a second); until then Lora works as before. */
    fun load() {
        if (recognizer != null) return
        val old = !installed()
        if (old && !hasOld()) return
        val f = if (old) oldFile() else file()
        scope.launch {
            val lib = PiiRuntime.library(c)
            if (lib == null) {
                _state.value = _state.value.copy(message = tr("Für {0} fehlt die ONNX Runtime der App (Spracherkennung) – Lora arbeitet wie bisher.", CommandModelInfo.NAME))
                return@launch
            }
            runCatching { OrtClassifier(lib, f, labels = CommandModelInfo.labels.size, inputs = CommandModelInfo.INPUTS, perToken = false, name = "lora-befehle") }
                .logFailure("Lora Befehle laden").getOrNull()?.let { cl ->
                    classifier = cl
                    recognizer = if (old) CommandRecognizer({ ids, types -> cl.run(ids, types) }, threshold = CommandModelInfo.OLD_THRESHOLD, bound = CommandModelInfo.OLD_CHOICE_BOUND)
                        else CommandRecognizer({ ids, types -> cl.run(ids, types) })
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

    /** What the model makes of [wish] while [area] is open, or null when it is not ready or switched off. */
    fun recognize(wish: String, area: String = "app"): CommandRecognizer.Result? {
        val r = recognizer ?: return null
        if (!c.settings.current.commandModel || wish.isBlank()) return null
        val key = "$area\n" + CommandRecognizer.normalize(wish)
        synchronized(cache) { cache[key] }?.let { return it }
        val result = runCatching { r.recognize(wish, area) }.logFailure("Lora Befehle").getOrNull() ?: return null
        synchronized(cache) { cache[key] = result }
        return result
    }
}
