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

/**
 * „Lora Befehle“, the command model (AP-71; up to 1.0.65 „Befehlsmodell“): a small German encoder
 * (distilbert-base-german-cased, Apache-2.0), fine-tuned and changed by us on invented wishes, maps what is typed into Lora or Strg+K to one action of the app in milliseconds on the device – or says that
 * it is a free request for a language model. Built with tools/befehle (model card: resources/befehle/MODELLKARTE.md);
 * the labels come from the code ([io.github.thepytales.llmhub.agent.LoraCommands]).
 *
 * A download like the recognition model of the pseudonymization (AP-67): offered once in a popup, from the branch
 * `befehle-modell` of the public repository at a fixed commit, checked by SHA-256. Without it – or without the ONNX
 * Runtime the app ships with sherpa-onnx – Lora works as before (rules on the device, then a language model). The
 * installed version is recognized by its SHA-256 ([VERSIONS], AP-117): an older known one keeps working with its own
 * threshold, bound and labels until the newest is downloaded (offered once as an update); then the old file is deleted.
 */
object CommandModelInfo {
    /** The name shown in the app. */
    const val NAME = "Lora Befehle"
    const val COMMIT = "6f996b1c45609fdc9e565a113cfb11bfa28e14ee"
    const val URL = "https://raw.githubusercontent.com/ADEU-TUD/Lora/$COMMIT/lora-befehle-pytales.int8.onnx.gz"
    const val GZ_SIZE = 54_943_807L
    const val GZ_SHA256 = "99480c32e19c5a9f5a52a6b2da097e38ac5991b4f7961fb3820328794d7a883c"
    const val FILE = "lora-befehle-pytales.int8.onnx"
    const val SIZE = 68_169_161L
    const val SHA256 = "f445c6b49e67f81177f6b0cb60f8e950ec24bcd8fbe6b367cd69ae9beba231b9"
    /** Download size shown to the user. */
    const val MEGABYTES = 55

    /** Below this probability of the likeliest label nothing is carried out (chosen on the Messkorpus, schwelle.json). */
    const val THRESHOLD = 0.5f
    /**
     * Rückfrage mit zwei Knöpfen (AP-73): below [THRESHOLD], Lora asks „Meinst du … oder …?“ when the two likeliest
     * commands together reach this probability (chosen on the development part only, tools/befehle/rueckfrage.py and
     * modell/rueckfrage.json); otherwise the wish goes on to a language model as before.
     */
    const val CHOICE_BOUND = 0.45f

    /**
     * Every version the app knows, newest first (AP-117): SHA-256 → threshold, bound, labels, vocabulary. A new version
     * goes on top (tools/modelle/veroeffentlichen.py writes the values into fassung.json); one with new labels brings its
     * own labels file (resources/befehle/labels-<version>.json), so the older ones keep working with theirs (AP-114).
     */
    val VERSIONS = listOf(
        ModelVersion(
            "1.0.66 (Nachtraining 10.11.2026)", FILE, SIZE, SHA256, THRESHOLD, CHOICE_BOUND, labels = "/befehle/labels.json", vocab = "/befehle/vocab.txt",
            download = ModelDownload(COMMIT, listOf(ModelPart(URL, GZ_SIZE, GZ_SHA256)), GZ_SIZE, GZ_SHA256),
        ),
        // Up to 1.0.65 („Befehlsmodell“), same labels and vocabulary.
        ModelVersion(
            "1.0.65 (Befehlsmodell)", "befehle-modell.int8.onnx", 67_956_881L, "3251a9ef4dd4104164bf861c0ac0d6778f0d8ef1c5dc8c286e02e017ab803f65",
            0.8f, 0.3f, labels = "/befehle/labels.json", vocab = "/befehle/vocab.txt",
        ),
    )

    /** The version whose update was declined before AP-117 (only a switch was kept, see [OwnModels.updateDeclined]). */
    const val LEGACY_DECLINED = SHA256
    /** Inputs of the ONNX file (DistilBERT has no token_type_ids). */
    val INPUTS = listOf("input_ids", "attention_mask")

    /** The labels in the order of the model's output (resources/befehle/labels.json, written by tools/befehle/export.py). */
    val labels: List<String> by lazy { OwnModels.labels(VERSIONS.first().labels) }

    /** The WordPiece vocabulary of the model (resources/befehle/vocab.txt). */
    val vocab: Map<String, Int> by lazy { OwnModels.vocab(VERSIONS.first().vocab) }
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
class CommandModel(private val c: AppContainer, versions: List<ModelVersion> = CommandModelInfo.VERSIONS, private val dir: File? = null) {
    data class State(
        val installed: Boolean = false,
        /** An older known version is there (AP-117): it keeps working, the newest one is offered as an update. */
        val old: Boolean = false,
        /** Loaded and ready; false while loading or when the runtime is missing. */
        val ready: Boolean = false,
        /** 0..1 while the download runs. */
        val progress: Double? = null,
        val message: String? = null,
        /** The version on this device (AP-117), null without one. */
        val version: ModelVersion? = null,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val root get() = dir ?: File(PiiRuntime.root(c).parentFile, "befehle")
    /** The versions on this device, recognized by SHA-256 (AP-117). */
    val files = OwnModels({ root }, versions)
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    /** The popup offering the download is shown while this is true (AppRoot, CommandModelPrompt). */
    val offer = MutableStateFlow(false)
    @Volatile private var asked = false
    @Volatile private var cancelled = false
    @Volatile private var recognizer: CommandRecognizer? = null
    @Volatile private var classifier: OrtClassifier? = null
    @Volatile private var found: OwnModels.Found? = null
    @Volatile private var detected = false
    @Volatile private var offerWanted = false

    private val cache = object : LinkedHashMap<String, CommandRecognizer.Result>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CommandRecognizer.Result>?) = size > 300
    }

    fun file(): File = File(root, files.current.file)

    /** There is a model to download at all (a build without a published model offers nothing). */
    val available: Boolean get() = files.current.download != null && files.current.size > 0

    /** The newest version is there. */
    fun installed(): Boolean = found?.outdated(files.current) == false

    /** Only an older known version is there (it keeps working until the update is downloaded). */
    fun hasOld(): Boolean = found?.outdated(files.current) == true

    init {
        // The SHA-256 of a file not seen before takes well under a second: in the background.
        scope.launch { refresh(); load() }
    }

    private fun refresh() {
        found = runCatching { files.detect() }.logFailure("Lora Befehle erkennen").getOrNull()
        detected = true
        _state.value = _state.value.copy(installed = installed(), old = hasOld(), version = found?.version)
        if (offerWanted) { offerWanted = false; offerIfNeeded() }
    }

    /**
     * Offers the download once per start of the app – unless it is there, switched off or „Nicht mehr fragen“. Who has
     * an older version gets the newest one offered as an update (with its own „Nicht mehr fragen“, per version).
     */
    fun offerIfNeeded() {
        if (!detected) { offerWanted = true; return }
        val s = c.settings.current
        if (asked || !available || installed() || _state.value.progress != null || !s.commandModel) return
        if (if (hasOld()) updateDeclined() else !s.commandModelAsk) return
        asked = true
        offer.value = true
    }

    /** „Nicht mehr fragen“ for the update to the newest version (AP-117: a later version is offered again). */
    fun updateDeclined(): Boolean = c.settings.current.let { s ->
        OwnModels.updateDeclined(s.commandModelUpdateAsk, s.commandModelUpdateDeclined, files.current, CommandModelInfo.LEGACY_DECLINED)
    }

    fun declineUpdate() = c.settings.update { it.copy(commandModelUpdateAsk = false, commandModelUpdateDeclined = files.current.sha256) }

    fun install() {
        if (_state.value.progress != null || !available) return
        cancelled = false
        _state.value = _state.value.copy(progress = 0.0, message = null)
        scope.launch {
            try {
                val now = files.install({ cancelled }, { p -> _state.value = _state.value.copy(progress = p) }) {
                    _state.value = _state.value.copy(progress = 1.0, message = tr("{0} wird geprüft …", CommandModelInfo.NAME))
                    unload()
                }
                found = now
                _state.value = State(installed = true, version = now.version, message = tr("{0} ist bereit.", CommandModelInfo.NAME))
                load()
            } catch (e: InterruptedException) {
                _state.value = _state.value.copy(progress = null, message = tr("Download abgebrochen – beim nächsten Mal wird fortgesetzt."))
            } catch (e: Exception) {
                _state.value = _state.value.copy(progress = null, message = if (hasOld()) tr("Download von {0} fehlgeschlagen: {1}. Bis dahin arbeitet die bisherige Version weiter.", CommandModelInfo.NAME, e.message)
                    else tr("Download von {0} fehlgeschlagen: {1}. Bis dahin arbeitet Lora wie bisher.", CommandModelInfo.NAME, e.message))
                load()
            } finally {
                if (_state.value.progress != null) _state.value = _state.value.copy(progress = null)
            }
        }
    }

    fun cancelInstall() { cancelled = true }

    fun delete() {
        scope.launch {
            unload()
            files.deleteAll()
            found = null
            _state.value = State(installed = false, message = tr("{0} gelöscht.", CommandModelInfo.NAME))
        }
    }

    /** Loads the version on this device in the background (well under a second); until then Lora works as before. */
    fun load() {
        if (recognizer != null) return
        val f = found ?: return
        scope.launch {
            val lib = PiiRuntime.library(c)
            if (lib == null) {
                _state.value = _state.value.copy(message = tr("Für {0} fehlt die ONNX Runtime der App (Spracherkennung) – Lora arbeitet wie bisher.", CommandModelInfo.NAME))
                return@launch
            }
            val v = f.version
            val labels = OwnModels.labels(v.labels)
            runCatching { OrtClassifier(lib, f.file, labels = labels.size, inputs = CommandModelInfo.INPUTS, perToken = false, name = "lora-befehle") }
                .logFailure("Lora Befehle laden").getOrNull()?.let { cl ->
                    classifier = cl
                    recognizer = CommandRecognizer({ ids, types -> cl.run(ids, types) }, WordPiece(OwnModels.vocab(v.vocab)), labels, threshold = v.threshold, bound = v.bound)
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
