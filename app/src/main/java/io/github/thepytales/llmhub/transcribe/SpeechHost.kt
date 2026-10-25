package io.github.thepytales.llmhub.transcribe

import android.util.Log
import de.lokal.whisper.AndroidSpeechPlatform
import de.lokal.whisper.Engine
import de.lokal.whisper.Platform
import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.AppState
import io.github.thepytales.llmhub.BuildConfig
import java.io.File

/** Android parts of the transcription (Windows has its own version of this file). */
internal object SpeechHost {
    /** Windows PC (microphone choice, priority) or phone. */
    const val IS_PC = false

    /** Folder of transcripts, recordings and recognition models. */
    fun root(c: AppContainer): File = File(c.appContext.filesDir, "speech")
    fun cache(c: AppContainer): File = File(c.appContext.cacheDir, "speech")

    @Suppress("UNUSED_PARAMETER")
    fun platform(c: AppContainer, t: Transcription, root: File, cache: File): Platform =
        AndroidSpeechPlatform(c.appContext, object : AndroidSpeechPlatform.Host {
            override fun emit(json: String) = t.onEvent(json)
            override fun onActivity(engine: Engine) = TranscriptionService.update(c.appContext, engine)
            override fun onJobFinished(engine: Engine, job: Engine.Job) {
                t.onJobFinished(job)
                if (!AppState.foreground) TranscriptionService.notifyFinished(c.appContext, job)
            }
            override fun threads(): Int = t.settings.value.threads
            override fun micMode(): String = t.settings.value.micMode
            override fun version(): String = BuildConfig.VERSION_NAME
        })

    fun log(msg: String, t: Throwable?) {
        if (t == null) Log.i("Transkription", msg) else Log.e("Transkription", msg, t)
    }

    /** Android chooses the microphone itself. */
    fun microphones(): List<String> = emptyList()
}
