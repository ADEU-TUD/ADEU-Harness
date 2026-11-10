package io.github.thepytales.llmhub.audio

import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import io.github.thepytales.llmhub.data.FileDownload
import io.github.thepytales.llmhub.logFailure
import io.github.thepytales.llmhub.ui.tr
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Sprachausgabe lokal (AP-111): a natural German voice on this device – Piper „Thorsten“ (medium quality) through the
 * sherpa-onnx the app ships for speech recognition. The voice was recorded and given away by Thorsten Müller
 * (Thorsten-Voice, CC0), trained with Piper (MIT) and converted for sherpa-onnx by its authors; the pronunciation rules are
 * the German part of espeak-ng (GPL-3.0, its own files). Only the files German needs are loaded, from Hugging Face at a
 * fixed commit, each checked by SHA-256.
 */
object LocalVoiceInfo {
    const val NAME = "Piper Thorsten – Lora Edition"
    const val REPO = "csukuangfj/vits-piper-de_DE-thorsten-medium"
    const val COMMIT = "da8bed7888b27d17e1aa8fbd53e6703ab939583a"
    const val MODEL = "de_DE-thorsten-medium.onnx"

    data class Part(val path: String, val size: Long, val sha256: String)

    val FILES = listOf(
        Part(MODEL, 63_201_421L, "1c305abe13dda2f50a9783da3e7645396833cdf2d09f24fa9a823c91293d65a6"),
        Part("tokens.txt", 907L, "2619c1a9de1bcf928162f40c583caf39368cfd6b2340c7bcad51dc634411ec36"),
        Part("espeak-ng-data/phontab", 55_796L, "886f3fa402cb0ba73d483aa8ad000af47a6b7cc06293c75a97913fba68a530f6"),
        Part("espeak-ng-data/phonindex", 39_074L, "3ca7b8fa3b42624e4b0f152707e7a39245fce569aa99ea47c055d9e622fcf0c4"),
        Part("espeak-ng-data/phondata", 550_424L, "4e0288957874029a8c3c9f41a8f517ad4bf18127046decbdd4b9d1d6807ce3a3"),
        Part("espeak-ng-data/intonations", 2_040L, "3f8af65fd3eda9759a10f021d61361c120871f463515229c925995c7f90918cc"),
        Part("espeak-ng-data/de_dict", 68_276L, "e9ac048df5cde03b74907d591c7b29b45ee9ec5fcd4592b0b894f0083572fcfd"),
        Part("espeak-ng-data/lang/gmw/de", 42L, "f3cca92f94b70f8c25a29ee0a4c9ce4c7f1022241532e0647fa2b7f698bf104e"),
    )
    val MEGABYTES = (FILES.sumOf { it.size } / 1_000_000).toInt()

    fun url(p: Part) = "https://huggingface.co/$REPO/resolve/$COMMIT/${p.path}"

    /**
     * Which voice reads: „LOCAL“ (this voice) when it is chosen or the text must stay on the device and it is loaded,
     * „DEVICE“ (the system's voice) when the text must stay here or LOCAL is chosen but not loaded, else „SERVER“.
     */
    fun route(engine: String, deviceOnly: Boolean, localInstalled: Boolean): String = when {
        localInstalled && (engine == "LOCAL" || deviceOnly) -> "LOCAL"
        deviceOnly || engine != "SERVER" -> "DEVICE"
        else -> "SERVER"
    }

    /** 16-bit mono WAV of [samples] (−1..1). */
    fun wav(samples: FloatArray, rate: Int): ByteArray {
        val data = samples.size * 2
        val out = ByteArrayOutputStream(44 + data)
        fun int(v: Int) { out.write(v and 0xFF); out.write((v ushr 8) and 0xFF); out.write((v ushr 16) and 0xFF); out.write((v ushr 24) and 0xFF) }
        fun short(v: Int) { out.write(v and 0xFF); out.write((v ushr 8) and 0xFF) }
        out.write("RIFF".toByteArray()); int(36 + data); out.write("WAVE".toByteArray())
        out.write("fmt ".toByteArray()); int(16); short(1); short(1); int(rate); int(rate * 2); short(2); short(16)
        out.write("data".toByteArray()); int(data)
        for (s in samples) short((s.coerceIn(-1f, 1f) * 32767f).toInt())
        return out.toByteArray()
    }
}

/**
 * The voice on this device: download (Einstellungen › Vorlesen & Spracheingabe), loading on first use (about a second),
 * then each sentence as WAV – fast enough that the Sprachmodus starts speaking well within half a second.
 */
class LocalVoice(private val root: File) {
    data class State(val installed: Boolean = false, val progress: Double? = null, val message: String? = null)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(State(installed = installed()))
    val state: StateFlow<State> = _state.asStateFlow()
    @Volatile private var cancelled = false
    @Volatile private var tts: OfflineTts? = null

    fun installed(): Boolean = LocalVoiceInfo.FILES.all { p -> File(root, p.path).let { it.isFile && it.length() == p.size } }

    fun install() {
        if (_state.value.progress != null) return
        cancelled = false
        _state.value = _state.value.copy(progress = 0.0, message = null)
        scope.launch {
            try {
                val total = LocalVoiceInfo.FILES.sumOf { it.size }.toDouble()
                var done = 0L
                for (p in LocalVoiceInfo.FILES) {
                    val target = File(root, p.path)
                    if (!(target.isFile && target.length() == p.size)) {
                        val part = File(target.path + ".part")
                        val start = done
                        FileDownload.fetch(LocalVoiceInfo.url(p), part, p.size, { cancelled }) { f -> _state.value = _state.value.copy(progress = (start + f * p.size) / total) }
                        if (part.length() != p.size || FileDownload.sha256(part) != p.sha256) {
                            part.delete()
                            error(tr("Prüfsumme stimmt nicht – die Datei ist beschädigt"))
                        }
                        target.delete()
                        if (!part.renameTo(target)) error(tr("Datei konnte nicht gespeichert werden"))
                    }
                    done += p.size
                }
                _state.value = State(installed = true, message = tr("{0} ist bereit.", LocalVoiceInfo.NAME))
                preload()
            } catch (e: InterruptedException) {
                _state.value = _state.value.copy(progress = null, message = tr("Download abgebrochen – beim nächsten Mal wird fortgesetzt."))
            } catch (e: Exception) {
                _state.value = _state.value.copy(progress = null, message = tr("Download von {0} fehlgeschlagen: {1}", LocalVoiceInfo.NAME, e.message))
            } finally {
                if (_state.value.progress != null) _state.value = _state.value.copy(progress = null)
            }
        }
    }

    fun cancelInstall() { cancelled = true }

    fun delete() {
        scope.launch {
            synchronized(this@LocalVoice) { tts?.let { runCatching { it.release() } }; tts = null }
            root.deleteRecursively()
            _state.value = State(installed = false, message = tr("{0} gelöscht.", LocalVoiceInfo.NAME))
        }
    }

    /** Loads the voice in the background, so the first sentence does not wait for it (Sprachmodus, Vorlesen). */
    fun preload() {
        if (tts == null && installed()) scope.launch { runCatching { engine() }.logFailure("Lokale Stimme laden") }
    }

    @Synchronized
    private fun engine(): OfflineTts = tts ?: OfflineTts(
        OfflineTtsConfig.builder().setModel(
            OfflineTtsModelConfig.builder()
                .setVits(
                    OfflineTtsVitsModelConfig.builder()
                        .setModel(File(root, LocalVoiceInfo.MODEL).absolutePath)
                        .setTokens(File(root, "tokens.txt").absolutePath)
                        .setDataDir(File(root, "espeak-ng-data").absolutePath)
                        .build(),
                )
                .setNumThreads(2)
                .setDebug(false)
                .build(),
        ).build(),
    ).also { tts = it }

    /** [text] spoken as WAV ([rate] like the speech rate of the settings, 1 = normal); throws without the voice. */
    fun synth(text: String, rate: Float = 1f): ByteArray {
        check(installed()) { tr("{0} ist nicht geladen.", LocalVoiceInfo.NAME) }
        val audio = synchronized(this) { engine().generate(text, 0, rate.coerceIn(0.5f, 2f)) }
        check(audio.samples.isNotEmpty()) { tr("Die Stimme hat für diesen Text nichts erzeugt.") }
        return LocalVoiceInfo.wav(audio.samples, audio.sampleRate)
    }
}
