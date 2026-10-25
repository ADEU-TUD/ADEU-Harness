package io.github.thepytales.llmhub.transcribe

import de.lokal.whisper.Engine
import de.lokal.whisper.Platform
import de.lokal.whisper.desktop.DesktopSpeechPlatform
import de.lokal.whisper.desktop.SpeechWin
import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.AppLog
import io.github.thepytales.llmhub.AppState
import io.github.thepytales.llmhub.BuildConfig
import io.github.thepytales.llmhub.desktop.Paths
import io.github.thepytales.llmhub.tasks.Notifier
import java.io.File

/** Windows parts of the transcription: folders, ffmpeg and native libraries, energy saving, notifications. */
internal object SpeechHost {
    /** Windows PC (microphone choice, priority) or phone. */
    const val IS_PC = true

    /** Large files stay out of the roaming profile (%LOCALAPPDATA%\ADEU LLM\speech). */
    @Suppress("UNUSED_PARAMETER")
    fun root(c: AppContainer): File = File(Paths.local, "speech")
    @Suppress("UNUSED_PARAMETER")
    fun cache(c: AppContainer): File = File(Paths.cache, "speech")

    /** Native libraries and ffmpeg: resources folder of the app (Compose Desktop sets this property). */
    private fun resources(): File? = System.getProperty("compose.application.resources.dir")?.let(::File)?.takeIf { it.isDirectory }

    @Suppress("UNUSED_PARAMETER")
    fun platform(c: AppContainer, t: Transcription, root: File, cache: File): Platform =
        DesktopSpeechPlatform(root, cache, resources(), object : DesktopSpeechPlatform.Host {
            override fun emit(json: String) = t.onEvent(json)
            override fun onActivity(engine: Engine) {
                val busy = engine.busy()
                SpeechWin.keepAwake(busy)
                SpeechWin.setGentle(busy && t.settings.value.gentle)
            }
            override fun onJobFinished(engine: Engine, job: Engine.Job) {
                t.onJobFinished(job)
                if (AppState.foreground || job.status == "cancelled") return
                val name = job.name.substringBeforeLast('.')
                if (job.status == "done") {
                    Notifier.transcriptionFinished("Transkription fertig – klicken zum Öffnen", name, job.transcriptId?.let { "transcript/$it" } ?: "transcripts")
                } else {
                    Notifier.transcriptionFinished("Transkription fehlgeschlagen", "$name: ${job.error}", "transcripts")
                }
            }
            override fun threads(): Int = t.settings.value.threads
            override fun version(): String = BuildConfig.VERSION_NAME
        })

    fun log(msg: String, t: Throwable?) {
        if (t == null) AppLog.i("Transkription: $msg") else AppLog.e("Transkription: $msg", t)
    }

    /** Recording devices for the settings (Windows). */
    fun microphones(): List<String> = runCatching {
        val a = DesktopSpeechPlatform.microphones()
        (0 until a.length()).map { a.getJSONObject(it).getString("id") }
    }.getOrDefault(emptyList())
}
