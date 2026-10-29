package io.github.thepytales.llmhub.audio

import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.Channel
import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import io.github.thepytales.llmhub.data.SettingsStore
import io.github.thepytales.llmhub.net.LlmClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/** A voice of the Android speech engine for the settings. */
data class VoiceOption(val name: String, val label: String)

/**
 * Reads answers aloud – either with the speech model of the API (natural AI voices; the text goes to the
 * same server that wrote it) or fully offline with Android's speech engine.
 */
class Speaker(context: Context, private val settings: SettingsStore, private val llm: LlmClient) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val player = AudioPlayer(appContext)
    private var job: Job? = null
    private var tts: TextToSpeech? = null
    private var ttsReady: CompletableDeferred<Boolean>? = null

    private val _playingId = MutableStateFlow<String?>(null)
    val playingId: StateFlow<String?> = _playingId.asStateFlow()

    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val errors: SharedFlow<String> = _errors

    /** Starts reading [markdown], or stops if [id] is already playing. */
    fun toggle(id: String, markdown: String) {
        if (_playingId.value == id) stop() else speak(id, markdown)
    }

    /** Reads [markdown] aloud; [deviceOnly]: with the device's voice even if the server's is chosen (data stays here). */
    fun speak(id: String, markdown: String, deviceOnly: Boolean = false) {
        stop()
        val text = speechText(markdown)
        if (text.isBlank()) return
        _playingId.value = id
        job = scope.launch {
            try {
                if (deviceOnly || settings.current.ttsEngine == "DEVICE") speakDevice(id, text) else speakServer(text)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _errors.tryEmit("Vorlesen fehlgeschlagen: ${e.message ?: e.javaClass.simpleName}")
            } finally {
                if (_playingId.value == id) _playingId.value = null
            }
        }
    }

    /**
     * Sprachmodus: reads an answer while it is still being written. [answer] gives the text so far and whether it is
     * complete; every finished sentence is spoken right away (the server voice prepares the next parts meanwhile).
     * [onFirstSound] runs when the first part starts playing.
     */
    fun speakStream(id: String, deviceOnly: Boolean, onFirstSound: () -> Unit = {}, answer: () -> Pair<String, Boolean>) {
        stop()
        _playingId.value = id
        job = scope.launch {
            try {
                coroutineScope {
                    val parts = Channel<String>(Channel.UNLIMITED)
                    launch {
                        val stream = SpeechStream()
                        while (true) {
                            val (text, done) = answer()
                            while (true) {
                                val part = stream.next(text, done) ?: break
                                speechText(part).takeIf { it.isNotBlank() }?.let { t -> chunk(t, 3500).forEach { parts.send(it) } }
                            }
                            if (done) break
                            delay(150)
                        }
                        parts.close()
                    }
                    var first = true
                    fun started() {
                        if (first) onFirstSound()
                        first = false
                    }
                    if (deviceOnly || settings.current.ttsEngine == "DEVICE") {
                        for (part in parts) {
                            started()
                            speakDevice(id, part)
                        }
                    } else {
                        val s = settings.current
                        val audio = Channel<Deferred<ByteArray>>(2)
                        launch {
                            for (part in parts) audio.send(async { llm.speech(s.ttsModel, s.ttsVoice, part) })
                            audio.close()
                        }
                        for (next in audio) {
                            val bytes = next.await()
                            started()
                            player.play(bytes)
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _errors.tryEmit("Vorlesen fehlgeschlagen: ${e.message ?: e.javaClass.simpleName}")
            } finally {
                if (_playingId.value == id) _playingId.value = null
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        player.stop()
        tts?.stop()
        _playingId.value = null
    }

    private suspend fun speakServer(text: String) = coroutineScope {
        val s = settings.current
        // A short first part, so the voice starts quickly.
        val chunks = SpeechStream().all(text).flatMap { chunk(it, 3500) }
        // Fetch the next part while the current one plays.
        var next: Deferred<ByteArray> = async { llm.speech(s.ttsModel, s.ttsVoice, chunks[0]) }
        for (i in chunks.indices) {
            val bytes = next.await()
            if (i + 1 < chunks.size) next = async { llm.speech(s.ttsModel, s.ttsVoice, chunks[i + 1]) }
            player.play(bytes)
        }
    }

    /** Starts Android's speech engine once. */
    private suspend fun engine(): TextToSpeech? {
        val ready = ttsReady ?: CompletableDeferred<Boolean>().also { d ->
            ttsReady = d
            tts = TextToSpeech(appContext) { status -> d.complete(status == TextToSpeech.SUCCESS) }
        }
        val ok = withTimeoutOrNull(6000) { ready.await() } == true
        if (!ok && ready.isCompleted) {
            // Engine failed: try again next time.
            tts?.shutdown()
            tts = null
            ttsReady = null
        }
        return if (ok) tts else null
    }

    private fun offlineVoices(engine: TextToSpeech): List<Voice> = runCatching {
        engine.voices.orEmpty()
            .filter { !it.isNetworkConnectionRequired && TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in it.features.orEmpty() }
            .sortedWith(compareBy<Voice>({ it.locale.language != "de" }, { -it.quality }, { it.name }))
    }.getOrDefault(emptyList())

    /** Offline voices of the device (German first); nothing leaves the phone with these. */
    suspend fun deviceVoices(): List<VoiceOption> {
        val engine = engine() ?: return emptyList()
        return offlineVoices(engine)
            .filter { it.locale.language in setOf("de", "en") }
            .map { v ->
                val variant = v.name.substringAfter("-x-", "").substringBefore('-').ifEmpty { v.name }
                val quality = if (v.quality >= Voice.QUALITY_HIGH) " · hohe Qualität" else ""
                VoiceOption(v.name, "${v.locale.getDisplayName(Locale.GERMAN)} · $variant$quality")
            }
    }

    private fun configure(engine: TextToSpeech) {
        val s = settings.current
        engine.setSpeechRate(s.speechRate)
        val voices = offlineVoices(engine)
        val voice = voices.firstOrNull { it.name == s.deviceVoice } ?: voices.firstOrNull { it.locale.language == "de" }
        if (voice != null) engine.setVoice(voice) else engine.setLanguage(Locale.GERMANY)
    }

    private suspend fun speakDevice(id: String, text: String) {
        val engine = engine() ?: throw IllegalStateException("Auf diesem Gerät ist keine Sprachausgabe verfügbar.")
        configure(engine)
        val max = (TextToSpeech.getMaxSpeechInputLength() - 100).coerceIn(500, 3500)
        val chunks = chunk(text, max)
        val last = "$id#${chunks.lastIndex}"
        suspendCancellableCoroutine<Unit> { cont ->
            val done = AtomicBoolean(false)
            fun finish() {
                if (done.compareAndSet(false, true)) cont.resume(Unit)
            }
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}

                override fun onDone(utteranceId: String?) {
                    if (utteranceId == last) finish()
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) = finish()

                override fun onStop(utteranceId: String?, interrupted: Boolean) = finish()
            })
            chunks.forEachIndexed { i, part ->
                engine.speak(part, if (i == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null, "$id#$i")
            }
            cont.invokeOnCancellation { engine.stop() }
        }
    }

    companion object {
        /** Splits text at sentence ends into parts of at most [max] characters. */
        fun chunk(text: String, max: Int): List<String> {
            val out = mutableListOf<String>()
            val sb = StringBuilder()
            for (raw in text.split(Regex("(?<=[.!?:;])\\s+|\\n+"))) {
                val s = raw.trim()
                if (s.isEmpty()) continue
                if (sb.isNotEmpty() && sb.length + s.length + 1 > max) {
                    out += sb.toString()
                    sb.clear()
                }
                if (s.length > max) {
                    out += s.chunked(max)
                    continue
                }
                if (sb.isNotEmpty()) sb.append(' ')
                sb.append(s)
            }
            if (sb.isNotEmpty()) out += sb.toString()
            return out.ifEmpty { listOf(text.take(max)) }
        }

        /** Turns Markdown into text that sounds natural when read aloud. */
        fun speechText(md: String): String {
            val t = md
                .replace(Regex("```[\\s\\S]*?```"), "\n(Codebeispiel ausgelassen.)\n")
                .replace(Regex("`([^`]+)`"), "$1")
                .replace(Regex("!\\[[^\\]]*]\\([^)]*\\)"), "")
                .replace(Regex("\\[([^\\]]+)]\\([^)]+\\)"), "$1")
                .replace(Regex("https?://\\S+"), "(Link)")
                .replace(Regex("<[^>]+>"), "")
            return t.lines().mapNotNull { raw ->
                var l = raw.trim()
                if (l.isEmpty() || Regex("^\\|?\\s*:?-{2,}").containsMatchIn(l)) return@mapNotNull null
                l = l.replace(Regex("^#{1,6}\\s+"), "")
                    .replace(Regex("^>\\s?"), "")
                    .replace(Regex("^\\[!\\w+]\\s*"), "")
                    .replace(Regex("^([-*+•]|\\d+[.)])\\s+"), "")
                if ('|' in l) l = l.trim('|').split('|').joinToString(", ") { it.trim() }
                l = l.replace(Regex("(\\*\\*|__|~~|\\*)"), "").trim()
                if (l.isEmpty()) return@mapNotNull null
                if (l.last() !in ".!?:;,)") l += "."
                l
            }.joinToString("\n")
        }
    }
}
