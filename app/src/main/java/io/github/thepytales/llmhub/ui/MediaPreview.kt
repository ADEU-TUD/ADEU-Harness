package io.github.thepytales.llmhub.ui

import android.widget.VideoView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.audio.TranscriptPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

/**
 * Android (AP-82): sound with the player of the transcription, videos with the system's [VideoView] (picture and sound,
 * hardware decoding); the position is read four times a second.
 */
internal fun mediaControl(c: AppContainer, file: File, video: Boolean): MediaControl =
    if (video) VideoControl(c, file) else SoundControl(c, file)

private class SoundControl(c: AppContainer, file: File) : MediaControl {
    private val player = TranscriptPlayer(file, c.appContext)
    override val position: StateFlow<Double> = player.position
    override val playing: StateFlow<Boolean> = player.playing
    override val duration = MutableStateFlow(runCatching {
        android.media.MediaMetadataRetriever().run {
            setDataSource(file.absolutePath)
            (extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.div(1000.0)).also { release() }
        }
    }.getOrNull())
    override val error: StateFlow<String?> = player.error
    override fun toggle() = player.toggle()
    override fun seek(seconds: Double) = player.seek(seconds)
    override fun release() = player.release()
}

internal class VideoControl(c: AppContainer, file: File) : MediaControl {
    val view = VideoView(c.appContext).apply { setVideoPath(file.absolutePath) }
    override val position = MutableStateFlow(0.0)
    override val playing = MutableStateFlow(false)
    override val duration = MutableStateFlow<Double?>(null)
    override val error = MutableStateFlow<String?>(null)
    private val poll: Job = CoroutineScope(Dispatchers.Main).launch {
        while (isActive) {
            runCatching {
                position.value = view.currentPosition / 1000.0
                playing.value = view.isPlaying
                view.duration.takeIf { it > 0 }?.let { duration.value = it / 1000.0 }
            }
            delay(250)
        }
    }

    init {
        view.setOnErrorListener { _, _, _ -> error.value = tr("Dieses Video lässt sich auf dem Gerät nicht abspielen."); true }
        view.setOnCompletionListener { playing.value = false }
    }

    override fun toggle() { if (view.isPlaying) view.pause() else view.start(); playing.value = view.isPlaying }
    override fun seek(seconds: Double) { view.seekTo((seconds * 1000).toInt()); position.value = seconds }
    override fun release() { poll.cancel(); runCatching { view.stopPlayback() } }
}

@Composable
internal fun MediaPicture(control: MediaControl, modifier: Modifier) {
    val v = (control as? VideoControl)?.view ?: return
    AndroidView(factory = { v }, modifier = modifier)
}
