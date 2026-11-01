package io.github.thepytales.llmhub.audio

import io.github.thepytales.llmhub.ui.tr
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.Channel
import android.content.Context
import io.github.thepytales.llmhub.data.SettingsStore
import io.github.thepytales.llmhub.net.LlmClient
import kotlinx.coroutines.CancellationException
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

/** A voice of the Windows speech engine for the settings. */
data class VoiceOption(val name: String, val label: String)

/**
 * Reads answers aloud – either with the speech model of the API (natural AI voices; the text goes to the
 * same server that wrote it) or fully offline with the voices of Windows.
 */
class Speaker(context: Context, private val settings: SettingsStore, private val llm: LlmClient) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val player = AudioPlayer(appContext)
    private var job: Job? = null
    private val voice = WindowsSpeech(appContext)

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
                _errors.tryEmit(tr("Vorlesen fehlgeschlagen: {0}", e.message ?: e.javaClass.simpleName))
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
                _errors.tryEmit(tr("Vorlesen fehlgeschlagen: {0}", e.message ?: e.javaClass.simpleName))
            } finally {
                if (_playingId.value == id) _playingId.value = null
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        player.stop()
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

    /** Offline voices of Windows (German first); nothing leaves the PC with these. */
    suspend fun deviceVoices(): List<VoiceOption> = voice.voices()

    @Suppress("UNUSED_PARAMETER")
    private suspend fun speakDevice(id: String, text: String) {
        val s = settings.current
        voice.speak(text, s.deviceVoice, s.speechRate)
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
