package io.github.thepytales.llmhub.ui

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.audio.TranscriptPlayer
import io.github.thepytales.llmhub.video.Media
import io.github.thepytales.llmhub.video.VideoClip
import io.github.thepytales.llmhub.video.VideoPlayer
import io.github.thepytales.llmhub.video.VideoProject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/**
 * PC (AP-82): sound with the player of the transcription (ffmpeg), videos with the preview player of the video editor –
 * picture and sound in step, from a project of this one clip. Without ffmpeg the error says so.
 */
internal fun mediaControl(c: AppContainer, file: File, video: Boolean): MediaControl {
    val info = runCatching { Media.probe(file) }.getOrNull()
    return if (video && info != null && info.hasVideo) VideoControl(file, info.duration) else SoundControl(c, file, info?.duration)
}

private class SoundControl(c: AppContainer, file: File, length: Double?) : MediaControl {
    private val player = TranscriptPlayer(file, c.appContext)
    override val position: StateFlow<Double> = player.position
    override val playing: StateFlow<Boolean> = player.playing
    override val duration = MutableStateFlow(length?.takeIf { it > 0 })
    override val error: StateFlow<String?> = player.error
    override fun toggle() = player.toggle()
    override fun seek(seconds: Double) = player.seek(seconds)
    override fun release() = player.release()
}

internal class VideoControl(file: File, length: Double) : MediaControl {
    val player = VideoPlayer()
    private val project = VideoProject(title = file.nameWithoutExtension, clips = listOf(VideoClip(path = file.absolutePath, inSec = 0.0, outSec = length)))
    override val position: StateFlow<Double> = player.position
    override val playing: StateFlow<Boolean> = player.playing
    override val duration = MutableStateFlow<Double?>(length.takeIf { it > 0 })
    override val error = MutableStateFlow(if (Media.ffmpeg() == null) tr("ffmpeg fehlt – das Video lässt sich nicht abspielen.") else null)

    init { player.seek(project, 0.0) }

    override fun toggle() = player.toggle(project)
    override fun seek(seconds: Double) {
        val again = player.playing.value
        player.seek(project, seconds)
        if (again) player.play(project)
    }
    override fun release() = player.dispose()
}

@Composable
internal fun MediaPicture(control: MediaControl, modifier: Modifier) {
    val v = control as? VideoControl ?: return
    val frame by v.player.frame.collectAsState()
    frame?.let { Image(it, contentDescription = null, contentScale = ContentScale.Fit, modifier = modifier) }
}
