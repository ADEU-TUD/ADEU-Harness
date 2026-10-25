package io.github.thepytales.llmhub.audio

import android.media.MediaPlayer
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.PlaybackParams
import androidx.core.content.ContextCompat
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

/**
 * Plays the audio of a transcript (Android: MediaPlayer; Windows has its own version of this file). With a [context]
 * it behaves like a media app: pauses for calls and other players (audio focus) and when headphones are unplugged.
 */
class TranscriptPlayer(file: File, context: Context? = null) {
    private val appContext = context?.applicationContext
    private val audio = appContext?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
        .setOnAudioFocusChangeListener { change -> if (change < 0) pause() }
        .build()
    /** Headphones unplugged or Bluetooth gone: pause instead of playing aloud. */
    private val noisy = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = pause()
    }
    private var noisyRegistered = false

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
            player.setOnCompletionListener { _playing.value = false; stopListening() }
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
        if (audio != null && audio.requestAudioFocus(focus) == AudioManager.AUDIOFOCUS_REQUEST_FAILED) return
        runCatching {
            player.playbackParams = PlaybackParams().setSpeed(speed)
            player.start()
            _playing.value = true
            if (!noisyRegistered && appContext != null) {
                ContextCompat.registerReceiver(appContext, noisy, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY), ContextCompat.RECEIVER_NOT_EXPORTED)
                noisyRegistered = true
            }
        }
    }

    fun pause() {
        runCatching { if (player.isPlaying) player.pause() }
        _playing.value = false
        stopListening()
    }

    private fun stopListening() {
        if (noisyRegistered) runCatching { appContext?.unregisterReceiver(noisy) }
        noisyRegistered = false
        audio?.abandonAudioFocusRequest(focus)
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
        _playing.value = false
        stopListening()
        runCatching { player.release() }
    }
}
