package io.github.thepytales.llmhub.video

import io.github.thepytales.llmhub.data.AtomicFiles
import io.github.thepytales.llmhub.ui.trLater
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

fun newVideoId(): String = UUID.randomUUID().toString().substring(0, 8)

/**
 * Where a picture stands in the frame (AP-52): the source, after cutting [cropL]..[cropB] (shares of its width and
 * height) off its sides, is fitted into a box [scale] times the size of the frame (keeping its aspect ratio), centred
 * at [x], [y] (shares of the frame). [opacity] 0..1. The default is the whole frame, as before AP-52.
 */
@Serializable
data class Frame(
    val x: Float = 0.5f,
    val y: Float = 0.5f,
    val scale: Float = 1f,
    val cropL: Float = 0f,
    val cropT: Float = 0f,
    val cropR: Float = 0f,
    val cropB: Float = 0f,
    val opacity: Float = 1f,
) {
    val isFull: Boolean get() = this == Frame()
    val cropped: Boolean get() = cropL > 0f || cropT > 0f || cropR > 0f || cropB > 0f

    companion object {
        /** Picture-in-picture corners (webcam over the screencast): a third of the width, a small margin. */
        val CORNERS = listOf("unten-rechts" to trLater("Unten rechts"), "unten-links" to trLater("Unten links"), "oben-rechts" to trLater("Oben rechts"), "oben-links" to trLater("Oben links"), "voll" to trLater("Ganzes Bild"))

        fun corner(name: String, scale: Float = 0.3f): Frame {
            val mx = 0.03f + scale / 2
            val my = 0.04f + scale / 2
            return when (name) {
                "unten-links" -> Frame(x = mx, y = 1f - my, scale = scale)
                "oben-rechts" -> Frame(x = 1f - mx, y = my, scale = scale)
                "oben-links" -> Frame(x = mx, y = my, scale = scale)
                "voll" -> Frame()
                else -> Frame(x = 1f - mx, y = 1f - my, scale = scale)
            }
        }
    }
}

/**
 * A piece of a source file on the video track V1: [inSec]..[outSec] of [path]. [transition] blends from the previous clip
 * into this one ("none", "fade" = cross-dissolve, "black" = through black), [transitionSec] long. [frame]: position,
 * size, crop and opacity (AP-52). [fx]: speed, reverse, freeze, colour, zoom, regions and voice (AP-54); with a speed
 * the clip lasts [sourceLength] / speed on the timeline, a frozen clip shows the frame at [inSec] for outSec − inSec.
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
    val frame: Frame = Frame(),
    val fx: ClipFx = ClipFx(),
) {
    /** Seconds of the source it covers (a frozen clip: its length). */
    val sourceLength: Double get() = (outSec - inSec).coerceAtLeast(0.0)

    /** Source seconds per second of the timeline (1 for a frozen frame). */
    val speed: Double get() = if (fx.freeze) 1.0 else fx.speed.coerceIn(0.25, 4.0)

    /** Length on the timeline. */
    val length: Double get() = sourceLength / speed
    val name: String get() = File(path).name

    /** Source time shown [local] seconds of the timeline into the clip. */
    fun sourceAt(local: Double): Double {
        val l = local.coerceIn(0.0, length)
        return when {
            fx.freeze -> inSec
            fx.reverse -> outSec - l * speed
            else -> inSec + l * speed
        }
    }
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

/**
 * A picture on a video track above V1 (AP-52): track 2 = V2 and so on, free on the timeline from [startSec]
 * (picture-in-picture, a logo, a second camera). Higher tracks lie on top.
 */
@Serializable
data class LayerClip(
    val id: String = newVideoId(),
    val path: String,
    val track: Int = 2,
    val startSec: Double,
    val inSec: Double = 0.0,
    val outSec: Double,
    val volume: Float = 1f,
    val muted: Boolean = false,
    val frame: Frame = Frame.corner("unten-rechts"),
    val fadeSec: Double = 0.3,
) {
    val length: Double get() = (outSec - inSec).coerceAtLeast(0.0)
    val endSec: Double get() = startSec + length
    val name: String get() = File(path).name
}

/**
 * Music, a voice-over or a second sound source on audio track [track] (A1, A2 …), from [startSec] of the timeline.
 * [linkedTo] (AP-53): linked to a clip of V1 – it moves and is trimmed with it, its source time is the clip's plus
 * [linkOffset] (the clip's own sound laid out, or a lapel microphone synchronised over the waveform).
 */
@Serializable
data class AudioClip(
    val id: String = newVideoId(),
    val path: String,
    val startSec: Double,
    val inSec: Double = 0.0,
    val outSec: Double,
    val volume: Float = 0.3f,
    val fadeSec: Double = 1.0,
    val track: Int = 1,
    val linkedTo: String? = null,
    val linkOffset: Double = 0.0,
    /** Speed and direction (AP-54): linked sound takes them from its clip, so it stays in sync. */
    val speed: Double = 1.0,
    val reverse: Boolean = false,
    /** Voice change in semitones (AP-54, anonymisation); linked sound also follows its clip's. */
    val voice: Float = 0f,
) {
    val length: Double get() = (outSec - inSec).coerceAtLeast(0.0) / speed.coerceIn(0.25, 4.0)
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

/** Lock, mute and hide of one track ("V1", "V2" … for video, "A1" … for sound; AP-53). Mute on a video track mutes its sound. */
@Serializable
data class TrackState(val locked: Boolean = false, val muted: Boolean = false, val hidden: Boolean = false)

/** A marker with a note on the timeline (AP-53); [chapter] markers become chapters of the MP4 and of YouTube. */
@Serializable
data class Marker(val id: String = newVideoId(), val sec: Double, val note: String = "", val chapter: Boolean = true)

/** A file in the media pool (AP-53), in folder [bin] ("" = top), with the In/Out points of the source monitor. */
@Serializable
data class PoolItem(val id: String = newVideoId(), val path: String, val bin: String = "", val inSec: Double? = null, val outSec: Double? = null) {
    val name: String get() = File(path).name
}

/**
 * A video project of the Videoeditor (AP-25, AP-52, AP-53): the video track V1 with clips one after another, further
 * video tracks V2 … with free [layers] on top (picture-in-picture), text overlays in the TUD design, audio tracks A1 …
 * for music, voice-over or a second sound source, subtitles (from the transcription or an SRT file), markers, the media
 * pool and the sound settings. Source files stay where they are; the project only refers to them. Projects from before
 * AP-52 load unchanged: their sound track is A1, there are no layers.
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
    /** Pictures on the video tracks above V1 (AP-52). */
    val layers: List<LayerClip> = emptyList(),
    /** Number of video tracks (V1 …) and audio tracks (A1 …) shown, at least as many as used. */
    val videoTracks: Int = 1,
    val audioTracks: Int = 1,
    val trackStates: Map<String, TrackState> = emptyMap(),
    val markers: List<Marker> = emptyList(),
    /** In and Out of the timeline (I, O): the loop of the preview plays between them. */
    val markIn: Double? = null,
    val markOut: Double? = null,
    /** Media pool (AP-53): the files of the project in folders. */
    val media: List<PoolItem> = emptyList(),
    val bins: List<String> = emptyList(),
    /** Magnet timeline (AP-53): edits on V1 move everything after them along, deleting closes the gap. */
    val magnet: Boolean = true,
    /**
     * Transcripts still being made (AP-56, „sofort transkribieren“): source path → title of the transcript; the editor
     * assigns each as soon as it is there.
     */
    val pendingTranscripts: Map<String, String> = emptyMap(),
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
        // Clips under 0.2 s (a short picture, a cut from the transcript) leave less than the usual 0.1 s minimum –
        // coerceIn(0.1, smaller) used to throw, and with it every drawing of the timeline.
        val limit = minOf(c.length, clips[i - 1].length) / 2
        if (limit <= 0.0) return 0.0
        return c.transitionSec.coerceIn(minOf(0.1, limit), limit)
    }

    /**
     * Whether subtitle [cue] goes higher up: a lower third or a sentence at the bottom is on at the same time, and the
     * subtitle would cover it.
     */
    fun subtitleRaised(cue: SubtitleCue): Boolean =
        overlays.any { (it.style == "bauchbinde" || it.style == "einblendung") && it.startSec < cue.endSec && it.endSec > cue.startSec }

    fun track(key: String): TrackState = trackStates[key] ?: TrackState()

    /** Video tracks shown: V1 up to the highest used one. */
    val videoTrackCount: Int get() = maxOf(videoTracks, layers.maxOfOrNull { it.track } ?: 1, 1)
    val audioTrackCount: Int get() = maxOf(audioTracks, audio.maxOfOrNull { it.track } ?: 1, 1)

    /** Timeline start of the V1 clip [id], or null. */
    fun clipStart(id: String): Double? {
        val i = clips.indexOfFirst { it.id == id }
        return if (i < 0) null else starts()[i]
    }

    /**
     * Linked sound follows its clip (AP-53): start, In and Out from the clip of V1 (plus the offset of the source);
     * a link to a clip that is gone is dropped and the sound stays where it is.
     */
    fun linked(): VideoProject {
        if (audio.none { it.linkedTo != null }) return this
        val s = starts()
        val idx = clips.withIndex().associate { it.value.id to it.index }
        val next = audio.map { a ->
            val k = a.linkedTo?.let { idx[it] } ?: return@map if (a.linkedTo != null) a.copy(linkedTo = null) else a
            val c = clips[k]
            var start = s[k]
            var inS = c.inSec + a.linkOffset
            if (inS < 0) { if (!c.fx.reverse) start -= inS / c.speed; inS = 0.0 }
            // A frozen frame has no sound of its own: its linked sound gets no length.
            val outS = if (c.fx.freeze) inS else maxOf(inS, c.outSec + a.linkOffset)
            a.copy(startSec = start, inSec = inS, outSec = outS, speed = c.speed, reverse = c.fx.reverse)
        }
        return if (next == audio) this else copy(audio = next)
    }

    val duration: Double
        get() = if (clips.isEmpty()) maxOf(audio.maxOfOrNull { it.endSec } ?: 0.0, overlays.maxOfOrNull { it.endSec } ?: 0.0, layers.maxOfOrNull { it.endSec } ?: 0.0)
        else starts().last() + clips.last().length

    /** The clip at timeline time [t] and the time inside its source file. */
    fun at(t: Double): Pair<Int, Double>? {
        if (clips.isEmpty()) return null
        val s = starts()
        for (i in clips.indices.reversed()) {
            if (t >= s[i] - 1e-6) return i to clips[i].sourceAt(t - s[i])
        }
        return 0 to clips[0].sourceAt(0.0)
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
