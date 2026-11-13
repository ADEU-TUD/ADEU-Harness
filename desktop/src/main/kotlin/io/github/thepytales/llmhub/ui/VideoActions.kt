package io.github.thepytales.llmhub.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.desktop.DesktopActions
import io.github.thepytales.llmhub.video.AudioClip
import io.github.thepytales.llmhub.video.Media
import io.github.thepytales.llmhub.video.RoughCut
import io.github.thepytales.llmhub.video.TextOverlay
import io.github.thepytales.llmhub.video.VideoClip
import io.github.thepytales.llmhub.video.VideoEdit
import io.github.thepytales.llmhub.video.VideoPlayer
import io.github.thepytales.llmhub.video.VideoRender
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

internal val VIDEO_MEDIA = listOf("mp4", "mov", "mkv", "avi", "wmv", "m4v", "webm", "mpg", "mpeg", "3gp", "png", "jpg", "jpeg")
internal val AUDIO_MEDIA = listOf("mp3", "m4a", "wav", "ogg", "opus", "flac", "aac", "wma", "mp4", "mov", "webm")

/** Sound only: these go onto a sound track, not onto V1 (AP-121, files dragged in from the Explorer). */
private val SOUND_ONLY = setOf("mp3", "m4a", "wav", "ogg", "opus", "flac", "aac", "wma")

/**
 * What the video editor does on a button, a menu or a step of „So entsteht dein Video“ (AP-121): the toolbar, the
 * right-click menus of the timeline, the step card and the files dragged in from the Explorer use the same functions,
 * so each thing works the same way wherever it is started. Every change goes through the history (Strg+Z).
 */
internal class VideoActions(
    val c: AppContainer,
    val h: History,
    val player: VideoPlayer,
    private val scope: CoroutineScope,
    private val select: (Sel) -> Unit,
) {
    /** A transcript is chosen for: "subs" (subtitles) or "cut" (pauses and filler words), or null. */
    var pickTranscript by mutableStateOf<String?>(null)
    var loraCut by mutableStateOf(false)
    var export by mutableStateOf(false)

    /** The playhead when a button is used: read then (the bar is not composed again while the video plays). */
    fun pos() = player.position.value

    fun addClips() = scope.launch {
        val files = withContext(Dispatchers.IO) { DesktopActions.pickFiles(tr("Videos auswählen"), multiple = true, extensions = VIDEO_MEDIA) }
        addVideos(files)
    }

    /** Videos and pictures to the end of V1 (and into the pool); the first new clip is selected. ffprobe runs in the background. */
    suspend fun addVideos(files: List<File>): Int {
        val clips = withContext(Dispatchers.IO) {
            files.mapNotNull { f -> Media.probe(f)?.takeIf { it.hasVideo || it.duration > 0.05 }?.let { i -> VideoClip(path = f.absolutePath, inSec = 0.0, outSec = if (i.duration > 0.05 && !Media.isStill(f, i)) i.duration else 5.0) } }
        }
        if (clips.isEmpty()) return 0
        h.edit { VideoEdit.addToPool(it.copy(clips = it.clips + clips), clips.map { c -> c.path }) }
        select(Sel.Clip(clips.first().id))
        return clips.size
    }

    fun addPicture() = scope.launch {
        val f = withContext(Dispatchers.IO) { DesktopActions.pickFiles(tr("Bild-in-Bild: Video oder Bild auswählen"), multiple = false, extensions = VIDEO_MEDIA).firstOrNull() } ?: return@launch
        val info = withContext(Dispatchers.IO) { Media.probe(f) } ?: run { Notices.error(tr("Die Datei ließ sich nicht lesen.")); return@launch }
        val still = Media.isStill(f, info)
        val at = pos()
        val len = if (still) 5.0 else info.duration.takeIf { it > 0.05 } ?: 5.0
        var made: String? = null
        h.edit { pp -> VideoEdit.addLayer(VideoEdit.addToPool(pp, listOf(f.absolutePath)), f.absolutePath, at, len, still = still).let { (q, lid) -> made = lid; q } }
        made?.let { select(Sel.Layer(it)) }
    }

    fun addAudio(at: Double = pos()) = scope.launch {
        val f = withContext(Dispatchers.IO) { DesktopActions.pickFiles(tr("Musik oder Sprecherton auswählen"), multiple = false, extensions = AUDIO_MEDIA).firstOrNull() } ?: return@launch
        addSound(f, at)
    }

    /** A sound file at [at] on the first free sound track (music to the end of the video at most). */
    suspend fun addSound(f: File, at: Double = pos()): Boolean {
        val info = withContext(Dispatchers.IO) { Media.probe(f) }
        if (info?.hasAudio != true) { Notices.error(tr("In der Datei ist kein Ton.")); return false }
        val a = AudioClip(path = f.absolutePath, startSec = at, outSec = minOf(info.duration, maxOf(1.0, h.project.duration - at)))
        h.edit { pp ->
            val tr0 = VideoEdit.freeAudioTrack(pp, a.startSec, a.endSec)
            VideoEdit.addToPool(pp.copy(audio = pp.audio + a.copy(track = tr0), audioTracks = maxOf(pp.audioTracks, tr0)), listOf(a.path))
        }
        select(Sel.Sound(a.id))
        return true
    }

    fun importSubs() = scope.launch {
        val f = withContext(Dispatchers.IO) { DesktopActions.pickFiles(tr("Untertitel (SRT oder VTT)"), multiple = false, extensions = listOf("srt", "vtt")).firstOrNull() } ?: return@launch
        val cues = withContext(Dispatchers.IO) { runCatching { VideoRender.parseSubtitles(f.readText()) }.getOrDefault(emptyList()) }
        if (cues.isEmpty()) Notices.error(tr("In der Datei wurden keine Untertitel gefunden.")) else {
            h.edit { it.copy(subtitles = cues) }
            Notices.info("${cues.size} " + tr("Untertitel übernommen."))
        }
    }

    /** A title in the TUD design: the title at the start, the credits at the end, the others at [at]. */
    fun addTitle(style: String, at: Double = pos()) {
        val p = h.project
        val start = when (style) { "titel" -> 0.0; "abspann" -> maxOf(0.0, p.duration - 5.0); else -> at }
        val o = TextOverlay(
            style = style, startSec = start, durationSec = if (style == "bauchbinde") 6.0 else 4.0,
            text = when (style) { "titel" -> p.title; "abspann" -> tr("Vielen Dank"); "bauchbinde" -> tr("Vorname Nachname"); else -> tr("Wichtiger Hinweis") },
            subtext = when (style) { "titel" -> tr("Lehrvideo · TU Dresden"); "abspann" -> "tu-dresden.de"; "bauchbinde" -> tr("Professur … · TU Dresden"); else -> "" },
        )
        h.edit { it.copy(overlays = it.overlays + o) }
        select(Sel.Title(o.id))
    }

    /** „Anfang bis hier entfernen“ ([start]) or „Ende ab hier entfernen“ for the clip under [t] (AP-121). */
    fun trimTo(t: Double = pos(), start: Boolean) {
        val p = h.project
        val q = VideoEdit.trimToPlayhead(p, t, start)
        if (q == null) {
            Notices.info(tr(if (p.clips.isEmpty()) "Noch keine Clips." else "Hier lässt sich nichts kürzen – den Abspielkopf mitten in einen Clip setzen."))
            return
        }
        val i = p.at(t)?.first
        h.edit { q }
        i?.let { k -> h.project.clips.getOrNull(k)?.let { select(Sel.Clip(it.id)) } }
        // The picture after the cut: at the start of the trimmed clip, or at the new end.
        if (start && i != null) player.seek(h.project, h.project.starts()[i])
        Notices.info(tr(if (start) "Anfang entfernt – mit Strg+Z rückgängig." else "Ende entfernt – mit Strg+Z rückgängig."))
    }

    /** Marks the start (I) or the end (O) of the piece to cut out at the playhead. */
    fun mark(start: Boolean, at: Double = pos()) {
        if (start) h.edit { it.copy(markIn = at, markOut = it.markOut?.takeIf { o -> o > at + 0.2 }) }
        else h.edit { it.copy(markOut = at, markIn = it.markIn?.takeIf { i -> i < at - 0.2 }) }
    }

    /** Whether there is a piece between In and Out to cut out. */
    val canCutRange: Boolean
        get() = h.project.let { p -> p.markIn != null && p.markOut != null && p.markOut - p.markIn > 0.05 && p.clips.isNotEmpty() }

    /** „Markiertes Stück herausschneiden“: In to Out out of the video, everything after it moves along (AP-121). */
    fun cutRange() {
        val p = h.project
        val a = p.markIn
        val b = p.markOut
        if (a == null || b == null || b - a < 0.05) {
            Notices.info(tr("Zuerst Anfang (I) und Ende (O) des Stücks markieren, dann herausschneiden."))
            return
        }
        val q = VideoEdit.cutRange(p, a, b)
        if (q == p) { Notices.info(tr("Zwischen {0} und {1} lässt sich nichts herausschneiden.", clock(a), clock(b))); return }
        h.edit { q.copy(markIn = null, markOut = null) }
        select(Sel.None)
        player.seek(h.project, a)
        Notices.info(tr("{0} herausgeschnitten – mit Strg+Z rückgängig.", clock(b - a, true)))
    }

    /** Files dragged in from the Explorer (AP-121): into the pool; videos and pictures to the end, sound at the playhead. */
    fun dropFiles(files: List<File>, toTimeline: Boolean) {
        val usable = files.filter { it.isFile && it.extension.lowercase() in (VIDEO_MEDIA + AUDIO_MEDIA) }
        if (usable.isEmpty()) { Notices.error(tr("Diese Dateien kann der Videoeditor nicht verwenden.")); return }
        if (!toTimeline) {
            h.edit { VideoEdit.addToPool(it, usable.map { f -> f.absolutePath }) }
            Notices.info(tr("{0} Datei(en) im Medienpool.", usable.size))
            return
        }
        scope.launch {
            val (sound, video) = usable.partition { it.extension.lowercase() in SOUND_ONLY }
            val n = addVideos(video)
            var s = 0
            for (f in sound) if (addSound(f)) s++
            if (n + s > 0) Notices.info(tr("{0} Datei(en) eingefügt – mit Strg+Z rückgängig.", n + s))
            else Notices.error(tr("Die Dateien ließen sich nicht lesen – sind es Videos?"))
        }
    }

    /** Subtitles or the rough cut once the transcripts are chosen. */
    fun transcriptsChosen(purpose: String, map: Map<String, io.github.thepytales.llmhub.transcribe.TranscriptSummary>) {
        pickTranscript = null
        h.edit { it.copy(transcripts = it.transcripts + map.mapValues { e -> e.value.id }) }
        scope.launch {
            val all = withContext(Dispatchers.IO) { h.project.transcripts.mapNotNull { (path, tid) -> c.transcription.load(tid)?.let { path to it } }.toMap() }
            if (purpose == "subs") {
                val cues = RoughCut.subtitles(h.project, all)
                if (cues.isEmpty()) Notices.error(tr("Im Transkript passt nichts zu den Clips."))
                else { h.edit { it.copy(subtitles = cues, burnSubtitles = true) }; Notices.info("${cues.size} " + tr("Untertitel aus der Transkription.")) }
            } else {
                var saved = 0.0
                h.edit { pp ->
                    var next = pp
                    // From the back, so the indices of the clips before stay valid.
                    for (i in pp.clips.indices.reversed()) {
                        val clip = pp.clips[i]
                        val t = all[clip.path] ?: continue
                        val parts = RoughCut.keep(t, clip.inSec, clip.outSec)
                        if (parts.isEmpty()) continue
                        saved += RoughCut.saved(clip.inSec, clip.outSec, parts)
                        next = RoughCut.apply(next, i, parts)
                    }
                    next
                }
                Notices.info(if (saved > 0.5) tr("Rohschnitt fertig:") + " ${clock(saved)} " + tr("kürzer. Mit Strg+Z rückgängig.") else tr("Nichts zum Herausschneiden gefunden."))
            }
        }
    }
}

/** The dialogs the actions open: which transcript, „Mit Lora kürzen“. */
@Composable
internal fun VideoActionDialogs(a: VideoActions) {
    a.pickTranscript?.let { purpose ->
        TranscriptPicker(a.c, a.h.project, onDismiss = { a.pickTranscript = null }) { map -> a.transcriptsChosen(purpose, map) }
    }
    if (a.loraCut) LoraCutDialog(a.c, a.h, onDismiss = { a.loraCut = false })
}
