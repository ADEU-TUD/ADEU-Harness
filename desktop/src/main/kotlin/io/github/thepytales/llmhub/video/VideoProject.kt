package io.github.thepytales.llmhub.video

import io.github.thepytales.llmhub.data.AtomicFiles
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

fun newVideoId(): String = UUID.randomUUID().toString().substring(0, 8)

/**
 * A piece of a source file on the video track: [inSec]..[outSec] of [path]. [transition] blends from the previous clip
 * into this one ("none", "fade" = cross-dissolve, "black" = through black), [transitionSec] long.
 */
@Serializable
data class VideoClip(
    val id: String = newVideoId(),
    val path: String,
    val inSec: Double,
    val outSec: Double,
    val volume: Float = 1f,
    val muted: Boolean = false,
    val transition: String = "none",
    val transitionSec: Double = 0.6,
) {
    val length: Double get() = (outSec - inSec).coerceAtLeast(0.0)
    val name: String get() = File(path).name
}

/**
 * Text on the picture in the TUD design: "titel" (full picture at the start), "bauchbinde" (name and role at the
 * bottom left), "einblendung" (a sentence at the bottom), "abspann" (full picture at the end). Times on the timeline.
 */
@Serializable
data class TextOverlay(
    val id: String = newVideoId(),
    val style: String = "bauchbinde",
    val text: String,
    val subtext: String = "",
    val startSec: Double,
    val durationSec: Double = 5.0,
) {
    val endSec: Double get() = startSec + durationSec

    companion object {
        val STYLES = listOf("titel" to "Titel", "bauchbinde" to "Bauchbinde", "einblendung" to "Einblendung", "abspann" to "Abspann")
        fun label(style: String) = STYLES.firstOrNull { it.first == style }?.second ?: style
    }
}

/** Music or a voice-over on the sound track, from [startSec] of the timeline. */
@Serializable
data class AudioClip(
    val id: String = newVideoId(),
    val path: String,
    val startSec: Double,
    val inSec: Double = 0.0,
    val outSec: Double,
    val volume: Float = 0.3f,
    val fadeSec: Double = 1.0,
) {
    val length: Double get() = (outSec - inSec).coerceAtLeast(0.0)
    val endSec: Double get() = startSec + length
    val name: String get() = File(path).name
}

/** One subtitle on the timeline. */
@Serializable
data class SubtitleCue(val startSec: Double, val endSec: Double, val text: String)

/** Sound of the whole video. */
@Serializable
data class AudioSettings(
    /** Overall level in dB. */
    val gainDb: Float = 0f,
    /** "aus", "leicht", "stark" (RNNoise speech model with the Videoeditor-Paket, otherwise ffmpeg's FFT filter). */
    val denoise: String = "aus",
    /** Even loudness (EBU R128, -16 LUFS – usual for lecture videos and YouTube). */
    val normalize: Boolean = true,
)

/**
 * A video project of the Videoeditor (AP-25): one video track with clips one after another, text overlays in the TUD
 * design, a sound track for music or voice-over, subtitles (from the transcription or an SRT file) and the sound
 * settings. Source files stay where they are; the project only refers to them.
 */
@Serializable
data class VideoProject(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val width: Int = 1920,
    val height: Int = 1080,
    val fps: Int = 25,
    val clips: List<VideoClip> = emptyList(),
    val overlays: List<TextOverlay> = emptyList(),
    val audio: List<AudioClip> = emptyList(),
    val subtitles: List<SubtitleCue> = emptyList(),
    val burnSubtitles: Boolean = false,
    val sound: AudioSettings = AudioSettings(),
    /** Fade in from black at the start and out to black at the end. */
    val fadeEnds: Boolean = true,
    /** Transcript of a source file (path → transcript id): subtitles and the rough cut come from it. */
    val transcripts: Map<String, String> = emptyMap(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
) {
    /** Start of every clip on the timeline (transitions overlap the clips they join). */
    fun starts(): List<Double> {
        val out = ArrayList<Double>(clips.size)
        var t = 0.0
        clips.forEachIndexed { i, c ->
            val overlap = if (i > 0) transitionOf(i) else 0.0
            t -= overlap
            out += t
            t += c.length
        }
        return out
    }

    /** Length of the transition into clip [i] (0 for none; never longer than half of either clip). */
    fun transitionOf(i: Int): Double {
        if (i <= 0 || i >= clips.size) return 0.0
        val c = clips[i]
        if (c.transition == "none") return 0.0
        return c.transitionSec.coerceIn(0.1, minOf(c.length, clips[i - 1].length) / 2)
    }

    val duration: Double
        get() = if (clips.isEmpty()) maxOf(audio.maxOfOrNull { it.endSec } ?: 0.0, overlays.maxOfOrNull { it.endSec } ?: 0.0)
        else starts().last() + clips.last().length

    /** The clip at timeline time [t] and the time inside its source file. */
    fun at(t: Double): Pair<Int, Double>? {
        if (clips.isEmpty()) return null
        val s = starts()
        for (i in clips.indices.reversed()) {
            if (t >= s[i] - 1e-6) return i to (clips[i].inSec + (t - s[i]).coerceIn(0.0, clips[i].length))
        }
        return 0 to clips[0].inSec
    }
}

/** The video projects: `videos/<id>.json` in the data folder. */
class VideoStore(files: File) {
    private val dir = File(files, "videos").apply { mkdirs() }
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    private val _items = MutableStateFlow(load())
    val items: StateFlow<List<VideoProject>> = _items.asStateFlow()

    private fun load(): List<VideoProject> = dir.listFiles().orEmpty().filter { it.extension == "json" }.mapNotNull { f ->
        runCatching { json.decodeFromString(VideoProject.serializer(), AtomicFiles.read(f) ?: return@mapNotNull null) }.getOrNull()
    }.sortedByDescending { it.updatedAt }

    fun get(id: String) = _items.value.firstOrNull { it.id == id }

    @Synchronized
    fun save(p: VideoProject, touch: Boolean = true): VideoProject {
        val next = if (touch) p.copy(updatedAt = System.currentTimeMillis()) else p
        AtomicFiles.write(File(dir, "${next.id}.json"), json.encodeToString(VideoProject.serializer(), next))
        _items.value = (_items.value.filter { it.id != next.id } + next).sortedByDescending { it.updatedAt }
        return next
    }

    @Synchronized
    fun delete(id: String) {
        File(dir, "$id.json").delete()
        _items.value = _items.value.filter { it.id != id }
    }

    fun fileName(p: VideoProject, extension: String) =
        p.title.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().ifBlank { "Video" }.take(80) + "." + extension
}
