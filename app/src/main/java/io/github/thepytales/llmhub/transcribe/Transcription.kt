package io.github.thepytales.llmhub.transcribe

import de.lokal.whisper.Engine
import de.lokal.whisper.Models
import de.lokal.whisper.Platform
import de.lokal.whisper.Replacements
import de.lokal.whisper.Speech
import de.lokal.whisper.Util
import de.lokal.whisper.Wav
import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.data.PrivacyLevel
import io.github.thepytales.llmhub.net.ApiException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.RandomAccessFile

/** Where speech is turned into text. */
enum class TranscriptionMode(val label: String, val description: String) {
    LOCAL("Auf diesem Gerät", "Nichts verlässt das Gerät. Braucht ein geladenes Erkennungsmodell; dauert auf schwachen Rechnern länger."),
    SERVER("Uni-Server", "Whisper Large v3 Turbo auf dem Server der TU Dresden – schnell auch auf alten Rechnern. Sprecher und Pausen werden trotzdem auf dem Gerät erkannt."),
}

@Serializable
data class TranscriptionSettings(
    /** Enum name of [TranscriptionMode]. */
    val mode: String = "LOCAL",
    /** Recognition model on the device (empty = the best one that is loaded). */
    val model: String = "",
    /** ISO code or "auto". */
    val language: String = "de",
    /** "off", "auto" or a fixed number ("2" … "8"). */
    val speakers: String = "auto",
    /** "fewer", "normal" or "more" speakers in automatic mode. */
    val sensitivity: String = "normal",
    /** Recognize while recording (only on the device). */
    val live: Boolean = true,
    /** Cores for the recognition, 0 = automatic. */
    val threads: Int = 0,
    /** Android: "speech" (microphone tuned for speech recognition) or "standard". */
    val micMode: String = "speech",
    /** Windows: microphone name, empty = default. */
    val micDevice: String = "",
    /** Windows: compute with lower priority so other programs stay smooth. */
    val gentle: Boolean = true,
)

/** A transcription that has finished (for the banner and the notification). */
data class TranscriptFinished(val transcriptId: String?, val name: String, val error: String?)

/**
 * The speech recognition of the app (formerly the Whisper app): one engine per process, started on first use.
 * Keeps the state for the screens up to date from the events of the engine. Shared by Android and Windows; the
 * platform specifics come from [SpeechHost].
 */
class Transcription(private val c: AppContainer, private val root: File, private val cache: File) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val settingsFile = File(root, "adeu-settings.json")
    private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val _settings = MutableStateFlow(
        runCatching { json.decodeFromString<TranscriptionSettings>(settingsFile.readText()) }.getOrDefault(TranscriptionSettings())
    )
    val settings: StateFlow<TranscriptionSettings> = _settings.asStateFlow()

    private val _state = MutableStateFlow(SpeechState())
    val state: StateFlow<SpeechState> = _state.asStateFlow()

    private val _transcripts = MutableStateFlow<List<TranscriptSummary>>(emptyList())
    val transcripts: StateFlow<List<TranscriptSummary>> = _transcripts.asStateFlow()

    /** Files waiting for the start dialog (shared, picked, rescued recordings). */
    private val _incoming = MutableStateFlow<List<IncomingFile>>(emptyList())
    val incoming: StateFlow<List<IncomingFile>> = _incoming.asStateFlow()

    /** Text recognized so far per running job (shown while it runs). */
    private val _jobText = MutableStateFlow<Map<String, List<String>>>(emptyMap())
    val jobText: StateFlow<Map<String, List<String>>> = _jobText.asStateFlow()

    /** Short messages for the screen ("Modell ist bereit", errors). */
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages

    private val _finished = MutableSharedFlow<TranscriptFinished>(extraBufferCapacity = 8)
    val finished: SharedFlow<TranscriptFinished> = _finished

    /** Number of transcriptions waiting or running (badge in the side menu). */
    val activeJobs: StateFlow<Int> = _state.map { s -> s.jobs.count { it.active } }.stateIn(scope, SharingStarted.Eagerly, 0)

    /** null while unchecked; false if the native libraries are missing on this device. */
    private val _available = MutableStateFlow<Boolean?>(null)
    val available: StateFlow<Boolean?> = _available.asStateFlow()
    var unavailableReason: String? = null
        private set

    @Volatile
    private var engine: Engine? = null

    init {
        // Resume a queue that was interrupted (app closed, phone restarted) and offer rescued recordings.
        val data = File(root, "data")
        val pending = File(data, "jobs.json").let { it.isFile && it.length() > 20 } ||
            File(data, "recordings").listFiles()?.isNotEmpty() == true
        if (pending) scope.launch { runCatching { engine() } }
    }

    fun updateSettings(transform: (TranscriptionSettings) -> TranscriptionSettings) {
        val next = transform(_settings.value)
        _settings.value = next
        runCatching { root.mkdirs(); settingsFile.writeText(json.encodeToString(next)) }
    }

    /** The engine, started on first use. */
    @Synchronized
    fun engine(): Engine {
        engine?.let { return it }
        Util.logger = object : Util.Logger {
            override fun log(msg: String) = SpeechHost.log(msg, null)
            override fun err(msg: String, t: Throwable?) = SpeechHost.log(msg, t)
        }
        Engine.remote = remote
        val e = Engine.start(SpeechHost.platform(c, this, root, cache))
        engine = e
        _state.value = SpeechState.parse(e.state(), _state.value)
        refreshList()
        refreshIncoming()
        return e
    }

    /** Checks once whether the recognition works on this device (native libraries and built-in models). */
    suspend fun checkAvailable(): Boolean = withContext(Dispatchers.IO) {
        _available.value?.let { return@withContext it }
        val ok = try {
            val e = engine()
            val vad = Speech.newVad(e.builtinFile("silero_vad.onnx"))
            vad.release()
            true
        } catch (t: Throwable) {
            SpeechHost.log("Spracherkennung nicht verfügbar", t)
            unavailableReason = when (t) {
                is UnsatisfiedLinkError, is NoClassDefFoundError ->
                    "Die Spracherkennung ist für diesen Prozessor nicht enthalten (nur 64-Bit-ARM-Handys und Windows-PCs)."
                else -> "Die Spracherkennung konnte nicht gestartet werden: ${t.message ?: t.javaClass.simpleName}"
            }
            false
        }
        _available.value = ok
        ok
    }

    // ------------------------------------------------------------------ events of the engine

    /** Called by the platform for every event of the engine (any thread). */
    internal fun onEvent(raw: String) {
        val ev = runCatching { SpeechJson.parseToJsonElement(raw).jsonObject }.getOrNull() ?: return
        when (ev.str("type")) {
            "state" -> ev.obj("state")?.let { _state.value = SpeechState.parse(it.toString(), _state.value) }
            "job" -> ev.obj("job")?.let { j ->
                val job = SpeechJob.parse(j)
                _state.value = _state.value.let { s ->
                    val jobs = s.jobs.toMutableList()
                    val i = jobs.indexOfFirst { it.id == job.id }
                    if (i >= 0) jobs[i] = job else jobs += job
                    s.copy(jobs = jobs)
                }
                if (!job.active) _jobText.value = _jobText.value - job.id
            }
            "segment" -> {
                val id = ev.str("job_id") ?: return
                val text = ev.obj("segment")?.str("text") ?: return
                _jobText.value = _jobText.value + (id to ((_jobText.value[id] ?: emptyList()) + text).takeLast(40))
            }
            "download" -> {
                val id = ev.str("id") ?: return
                val p = ev.num("progress")
                _state.value = _state.value.let { s -> s.copy(models = s.models.map { if (it.id == id) it.copy(downloading = p) else it }) }
            }
            "rec" -> _state.value = _state.value.let { s ->
                s.copy(recording = s.recording.copy(
                    level = (ev.num("level") ?: 0.0).toFloat(),
                    seconds = ev.num("seconds") ?: s.recording.seconds,
                    paused = ev.bool("paused") ?: s.recording.paused,
                ))
            }
            "mark" -> ev.obj("mark")?.let { m ->
                _state.value = _state.value.let { s -> s.copy(recording = s.recording.copy(marks = s.recording.marks + Mark(m.num("t") ?: 0.0, m.str("label").orEmpty()))) }
            }
            "live_segment" -> ev.obj("segment")?.str("text")?.let { t ->
                _state.value = _state.value.let { s -> s.copy(recording = s.recording.copy(liveText = s.recording.liveText + t)) }
            }
            "incoming" -> refreshIncoming()
            "transcript_saved" -> {
                refreshList()
                _finished.tryEmit(TranscriptFinished(ev.str("id"), ev.str("name").orEmpty().substringBeforeLast('.'), null))
            }
            "toast" -> ev.str("message")?.let { _messages.tryEmit(it) }
        }
    }

    internal fun onJobFinished(job: Engine.Job) {
        if (job.status == "error") _finished.tryEmit(TranscriptFinished(null, job.name.substringBeforeLast('.'), job.error))
    }

    fun refreshList() {
        val e = engine ?: return
        scope.launch(Dispatchers.IO) { _transcripts.value = TranscriptSummary.parseList(e.indexList()) }
    }

    private fun refreshIncoming() {
        val e = engine ?: return
        _incoming.value = runCatching {
            SpeechJson.parseToJsonElement(e.takePendingIncoming()).let { it as kotlinx.serialization.json.JsonArray }.mapNotNull { el ->
                val o = el as? JsonObject ?: return@mapNotNull null
                IncomingFile(o.str("token") ?: return@mapNotNull null, o.str("name").orEmpty(), o.long("size") ?: 0, o.num("duration"), o.bool("recovered") == true)
            }
        }.getOrDefault(emptyList())
    }

    // ------------------------------------------------------------------ actions

    /** Copies a picked or shared file into the app and offers it in the start dialog. */
    suspend fun import(name: String, size: Long, input: InputStream) = withContext(Dispatchers.IO) {
        input.use { engine().importStream(name, size, it) }
        refreshIncoming()
    }

    fun discardIncoming(token: String) {
        engine().discardIncoming(token)
        refreshIncoming()
    }

    /** Starts the transcription of an offered file. */
    fun start(token: String, opts: StartOptions) {
        engine().enqueueIncoming(token, opts.toJson())
        refreshIncoming()
    }

    fun retranscribe(transcriptId: String, opts: StartOptions): String = engine().retranscribe(transcriptId, opts.toJson())

    fun cancel(jobId: String) = engine().cancelJob(jobId)
    fun dismiss(jobId: String) = engine().dismissJob(jobId)

    fun startRecording(opts: StartOptions) {
        val s = _settings.value
        val live = JSONObject().put("live", opts.mode == TranscriptionMode.LOCAL && s.live).put("model", opts.model).put("language", opts.language)
        _state.value = _state.value.copy(recording = RecordingState(active = true))
        engine().startRecording(live, s.micDevice)
    }

    fun pauseRecording(paused: Boolean) = engine().pauseRecording(paused)
    fun addMark(label: String? = null) { engine().addMarker(label) }

    /** Stops the recording; [keep] = transcribe it with [opts], otherwise throw it away. */
    fun stopRecording(keep: Boolean, opts: StartOptions) {
        engine().stopRecording(keep, opts.toJson())
        _state.value = _state.value.copy(recording = RecordingState())
    }

    fun download(modelId: String) = engine().downloadModel(modelId)
    fun cancelDownload(modelId: String) = engine().cancelDownload(modelId)
    fun deleteModel(modelId: String) = engine().deleteModel(modelId)
    fun recalibrate() = engine().recalibrate()

    /** Model file loaded by hand (browser, USB stick), also as ZIP. */
    suspend fun importModelFile(name: String, input: InputStream): String = withContext(Dispatchers.IO) {
        input.use { engine().importModelFile(name, it) }
    }

    // ------------------------------------------------------------------ transcripts

    private fun dir(id: String): File {
        require(id.matches(Regex("[0-9a-zA-Z_-]{3,40}"))) { "id" }
        return File(engine().transcripts, id)
    }

    suspend fun load(id: String): Transcript? = withContext(Dispatchers.IO) {
        val f = File(dir(id), "transcript.json")
        if (!f.isFile) null else Transcript.parse(f.readText(), id)
    }

    suspend fun save(t: Transcript) = withContext(Dispatchers.IO) {
        val f = File(dir(t.id), "transcript.json")
        Util.writeText(f, SpeechJson.encodeToString(JsonObject.serializer(), t.toJson()))
        engine().indexInvalidate(t.id)
        refreshList()
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        Util.deleteRecursive(dir(id))
        engine().indexInvalidate(id)
        refreshList()
    }

    /** Audio of a transcript (for playing), null if none was kept. */
    fun audioFile(t: Transcript): File? = t.audio?.let { a -> File(dir(t.id), a).takeIf { it.isFile } }

    /** Full-text search: transcript id → snippet. */
    suspend fun search(query: String): List<Pair<String, String>> = withContext(Dispatchers.IO) {
        runCatching {
            (SpeechJson.parseToJsonElement(engine().indexSearch(query)) as kotlinx.serialization.json.JsonArray).mapNotNull { el ->
                val o = el as? JsonObject ?: return@mapNotNull null
                (o.str("id") ?: return@mapNotNull null) to o.str("snippet").orEmpty()
            }
        }.getOrDefault(emptyList())
    }

    /**
     * Recognizes a short recording (dictation in the chat) on the device, without saving a transcript; null if no
     * recognition model is loaded. Used where data must not leave the device.
     */
    suspend fun recognizeShort(file: File): String? = withContext(Dispatchers.IO) {
        val modelId = localModel() ?: return@withContext null
        val e = engine()
        val m = Models.get(modelId) ?: return@withContext null
        val pcm = File(cache, "dictation.pcm")
        try {
            val n = e.platform.decode(file, pcm, null, null)
            val all = Wav.readPcm(pcm, 0, n)
            val lang = if (m.isWhisper) _settings.value.language else "auto"
            val h = Speech.acquire(m, File(e.models, m.id), lang, "transcribe", e.platform.threads())
            try {
                val window = 16000 * 25
                (0 until all.size step window).joinToString(" ") { from ->
                    Speech.recognize(h, all.copyOfRange(from, minOf(all.size, from + window))).text.orEmpty().trim()
                }.trim()
            } finally {
                Speech.release(h)
            }
        } finally {
            pcm.delete()
        }
    }

    // ------------------------------------------------------------------ replacement list

    private fun replacementsFile() = File(File(root, "data"), "replacements.json")

    /** Names and terms that are always recognized wrongly: (wrong, right). */
    fun replacements(): List<Pair<String, String>> = runCatching {
        (SpeechJson.parseToJsonElement(replacementsFile().readText()) as kotlinx.serialization.json.JsonArray).mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            (o.str("from") ?: return@mapNotNull null) to o.str("to").orEmpty()
        }
    }.getOrDefault(emptyList())

    fun saveReplacements(list: List<Pair<String, String>>) {
        val a = org.json.JSONArray()
        list.filter { it.first.isNotBlank() }.forEach { (f, t) -> a.put(JSONObject().put("from", f.trim()).put("to", t.trim())) }
        File(root, "data").mkdirs()
        Util.writeText(replacementsFile(), a.toString())
    }

    /** Applies the replacement list to all existing transcripts; returns how many were changed. */
    suspend fun applyReplacementsToAll(): Int = withContext(Dispatchers.IO) {
        val rep = Replacements.load(File(root, "data"))
        if (rep.isEmpty) return@withContext 0
        var changed = 0
        for (s in TranscriptSummary.parseList(engine().indexList())) {
            val t = load(s.id) ?: continue
            val next = t.copy(segments = t.segments.map { seg ->
                val text = rep.apply(seg.text)
                if (text == seg.text) seg else seg.copy(text = text, words = emptyList())
            })
            if (next != t) { save(next); changed++ }
        }
        changed
    }

    // ------------------------------------------------------------------ privacy and options

    /** Transcription on the Uni server is allowed for this project (data protection level) and possible (API key). */
    fun serverAllowed(folderId: String?): Boolean = c.settings.current.hasApiKey && privacy(folderId).allowsUniServer

    fun privacy(folderId: String?): PrivacyLevel =
        folderId?.let { id -> c.folders.items.value.firstOrNull { it.id == id }?.privacy } ?: PrivacyLevel.OPEN

    /** The model to use on the device: the chosen one if loaded, else the best loaded one (null = none loaded). */
    fun localModel(): String? {
        val installed = _state.value.models.filter { it.installed }.map { it.id }
        val chosen = _settings.value.model
        return if (chosen in installed) chosen else listOf("parakeet", "turbo", "small", "base").firstOrNull { it in installed }
    }

    /** Defaults for the start dialog from the settings. */
    fun defaultOptions(folderId: String? = null): StartOptions {
        val s = _settings.value
        val mode = enumValues<TranscriptionMode>().firstOrNull { it.name == s.mode } ?: TranscriptionMode.LOCAL
        val useServer = mode == TranscriptionMode.SERVER && serverAllowed(folderId) || localModel() == null && serverAllowed(folderId)
        return StartOptions(
            mode = if (useServer) TranscriptionMode.SERVER else TranscriptionMode.LOCAL,
            model = localModel() ?: "turbo",
            language = s.language,
            speakers = s.speakers,
            sensitivity = s.sensitivity,
            folder = folderId,
        )
    }

    // ------------------------------------------------------------------ recognition on the Uni server

    /** Sends one piece of speech (≤ 28 s) to the Whisper model of the Uni server; waits and retries on 429/5xx. */
    private val remote = object : Engine.Remote {
        override fun name(): String = "Uni-Server"

        override fun recognize(samples: FloatArray, language: String?): String {
            val wav = File(cache, "remote-${Thread.currentThread().id}.wav")
            writeWav(samples, wav)
            try {
                var wait = 5_000L
                repeat(6) { attempt ->
                    try {
                        return runBlocking {
                            c.llm.limiter.acquire()
                            c.llm.transcribe(c.settings.current.sttModel, wav, language, "audio/wav")
                        }
                    } catch (e: ApiException) {
                        val retry = e.code == 429 || e.code in 500..599
                        if (!retry || attempt == 5) throw e
                        Thread.sleep(e.retryAfter?.let { it * 1000 } ?: wait)
                        wait = (wait * 2).coerceAtMost(60_000)
                    } catch (e: java.io.IOException) {
                        if (attempt == 5) throw e
                        Thread.sleep(wait)
                        wait = (wait * 2).coerceAtMost(60_000)
                    }
                }
                error("unreachable")
            } finally {
                wav.delete()
            }
        }
    }

    companion object {
        /** 16-bit mono WAV at 16 kHz. */
        fun writeWav(samples: FloatArray, file: File) {
            file.parentFile?.mkdirs()
            RandomAccessFile(file, "rw").use { raf ->
                raf.setLength(0)
                val dataLen = samples.size * 2
                val h = java.nio.ByteBuffer.allocate(44).order(java.nio.ByteOrder.LITTLE_ENDIAN)
                h.put("RIFF".toByteArray()).putInt(36 + dataLen).put("WAVE".toByteArray())
                h.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1).putInt(16000).putInt(32000).putShort(2).putShort(16)
                h.put("data".toByteArray()).putInt(dataLen)
                raf.write(h.array())
                val b = java.nio.ByteBuffer.allocate(dataLen).order(java.nio.ByteOrder.LITTLE_ENDIAN)
                for (s in samples) b.putShort((s.coerceIn(-1f, 1f) * 32767f).toInt().toShort())
                raf.write(b.array())
            }
        }
    }
}

/** What the start dialog asks: where, which model and language, speakers, title and project. */
data class StartOptions(
    val mode: TranscriptionMode = TranscriptionMode.LOCAL,
    val model: String = "turbo",
    val language: String = "de",
    val speakers: String = "auto",
    val sensitivity: String = "normal",
    val translate: Boolean = false,
    val title: String = "",
    val folder: String? = null,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("model", if (mode == TranscriptionMode.SERVER) Engine.CLOUD else model)
        .put("language", language)
        .put("speakers", speakers)
        .put("sensitivity", sensitivity)
        .put("task", if (translate && mode == TranscriptionMode.LOCAL) "translate" else "transcribe")
        .put("title", title.trim())
        .put("folder", folder ?: "")
}
