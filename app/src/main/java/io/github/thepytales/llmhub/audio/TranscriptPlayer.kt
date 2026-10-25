package io.github.thepytales.llmhub.audio

import android.media.MediaPlayer
import android.media.PlaybackParams
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

/** Plays the audio of a transcript (Android: MediaPlayer; Windows has its own version of this file). */
class TranscriptPlayer(file: File) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val player = MediaPlayer()
    private var prepared = false
    private var speed = 1f

    private val _position = MutableStateFlow(0.0)
    /** Seconds. */
    val position: StateFlow<Double> = _position.asStateFlow()
    private val _playing = MutableStateFlow(false)
    val playing: StateFlow<Boolean> = _playing.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        runCatching {
            player.setDataSource(file.absolutePath)
            player.setOnPreparedListener { prepared = true }
            player.setOnCompletionListener { _playing.value = false }
            player.prepareAsync()
        }.onFailure { _error.value = "Die Aufnahme lässt sich nicht abspielen." }
        scope.launch {
            while (isActive) {
                if (prepared && _playing.value) runCatching { _position.value = player.currentPosition / 1000.0 }
                delay(100)
            }
        }
    }

    fun play() {
        if (!prepared) { scope.launch { repeat(30) { if (!prepared) delay(100) }; if (prepared) play() }; return }
        runCatching {
            player.playbackParams = PlaybackParams().setSpeed(speed)
            player.start()
            _playing.value = true
        }
    }

    fun pause() {
        runCatching { if (player.isPlaying) player.pause() }
        _playing.value = false
    }

    fun toggle() = if (_playing.value) pause() else play()

    fun seek(seconds: Double) {
        _position.value = seconds.coerceAtLeast(0.0)
        if (prepared) runCatching { player.seekTo((seconds * 1000).toInt().coerceAtLeast(0)) }
    }

    fun setSpeed(value: Float) {
        speed = value
        if (prepared && _playing.value) runCatching { player.playbackParams = PlaybackParams().setSpeed(value) }
    }

    fun release() {
        scope.cancel()
        runCatching { player.release() }
    }
}
