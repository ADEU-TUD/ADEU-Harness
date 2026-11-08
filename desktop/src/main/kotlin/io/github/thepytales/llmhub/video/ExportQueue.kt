package io.github.thepytales.llmhub.video

import io.github.thepytales.llmhub.AppLog
import io.github.thepytales.llmhub.ui.tr
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

/**
 * Export-Warteschlange (AP-57): exports run one after another in the background while the editing goes on – several
 * targets of one project at once (OPAL 720p, YouTube 1080p, only the sound, subtitle files, chapters for the YouTube
 * description), each with the best encoder of the PC (hardware with fallback, [Encoders]). The project is taken as it
 * is when queued. Finished files can go on to Nextcloud.
 */
object ExportQueue {
    /** What one entry makes. */
    enum class Kind { VIDEO, AUDIO, SUBTITLES, CHAPTERS }

    enum class Status { WAITING, RUNNING, DONE, FAILED, CANCELLED }

    /**
     * An entry: [project] as queued, [kind] and [preset] (for video and sound), into [out]. [upload]: a Nextcloud folder
     * the result goes to as well. [files]: what it made; [note]: e.g. „mit x264 statt NVENC“; [remote]: Nextcloud paths.
     */
    data class Entry(
        val id: String = newVideoId(),
        val project: VideoProject,
        val kind: Kind,
        val preset: String = "opal",
        val out: File,
        val useHardware: Boolean = true,
        val upload: String? = null,
        val status: Status = Status.WAITING,
        val progress: Float = 0f,
        val error: String? = null,
        val files: List<File> = emptyList(),
        val encoder: String? = null,
        val note: String? = null,
        val remote: List<String> = emptyList(),
        val seconds: Double? = null,
    ) {
        val label: String get() = when (kind) {
            Kind.VIDEO -> tr(VideoRender.preset(preset).label)
            Kind.AUDIO -> tr("Nur Ton (M4A)")
            Kind.SUBTITLES -> tr("Untertitel (SRT und VTT)")
            Kind.CHAPTERS -> tr("Kapitel für YouTube")
        }
        val finished: Boolean get() = status == Status.DONE || status == Status.FAILED || status == Status.CANCELLED
    }

    private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    val entries: StateFlow<List<Entry>> = _entries.asStateFlow()

    /** Uploads a finished file to a Nextcloud folder; returns the remote path (set by the app, tests leave it out). */
    @Volatile
    var uploader: (suspend (File, String) -> String)? = null

    /** Told when an entry is finished (a notice with „Öffnen“). */
    @Volatile
    var onFinished: ((Entry) -> Unit)? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()
    private val running = java.util.concurrent.ConcurrentHashMap<String, Job>()

    /** Waiting and running entries. */
    val busy: Int get() = _entries.value.count { !it.finished }

    private fun update(id: String, f: (Entry) -> Entry) {
        _entries.value = _entries.value.map { if (it.id == id) f(it) else it }
    }

    /**
     * Queues one entry per [kinds]/[presets] for [p] into [folder] (names from the project title, nothing overwritten).
     * Returns the new entries.
     */
    fun add(p: VideoProject, presets: List<String>, kinds: Set<Kind>, folder: File, useHardware: Boolean = true, upload: String? = null): List<Entry> {
        folder.mkdirs()
        val base = p.title.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().ifBlank { "Video" }.take(80)
        val taken = _entries.value.filter { !it.finished }.map { it.out.absolutePath }.toMutableSet()
        fun unique(name: String): File {
            var f = File(folder, name)
            var n = 2
            while (f.exists() || f.absolutePath in taken) { f = File(folder, name.substringBeforeLast('.') + " ($n)." + name.substringAfterLast('.')); n++ }
            taken += f.absolutePath
            return f
        }
        val list = mutableListOf<Entry>()
        if (Kind.VIDEO in kinds) presets.distinct().forEach { pr ->
            val suffix = if (presets.distinct().size > 1) " – " + pr.uppercase() else ""
            list += Entry(project = p, kind = Kind.VIDEO, preset = pr, out = unique("$base$suffix.mp4"), useHardware = useHardware, upload = upload)
        }
        if (Kind.AUDIO in kinds) list += Entry(project = p, kind = Kind.AUDIO, preset = presets.firstOrNull() ?: "youtube", out = unique("$base.m4a"), upload = upload)
        if (Kind.SUBTITLES in kinds && p.subtitles.isNotEmpty()) list += Entry(project = p, kind = Kind.SUBTITLES, out = unique("$base.srt"), upload = upload)
        if (Kind.CHAPTERS in kinds && VideoEdit.chapters(p).isNotEmpty()) list += Entry(project = p, kind = Kind.CHAPTERS, out = unique("$base – Kapitel.txt"), upload = upload)
        _entries.value = _entries.value + list
        list.forEach { e -> running[e.id] = scope.launch { lock.withLock { run(e.id) } } }
        return list
    }

    private suspend fun run(id: String) {
        val e = _entries.value.firstOrNull { it.id == id } ?: return
        if (e.status != Status.WAITING) return
        update(id) { it.copy(status = Status.RUNNING, progress = 0f) }
        val started = System.nanoTime()
        try {
            val made = mutableListOf<File>()
            var encoderUsed: String? = null
            var note: String? = null
            when (e.kind) {
                Kind.VIDEO -> {
                    val enc = Encoders.best(e.useHardware)
                    VideoRender.lastFallback = null
                    VideoRender.render(e.project, VideoRender.preset(e.preset), e.out, enc) { v -> update(id) { it.copy(progress = v) } }
                    encoderUsed = if (VideoRender.lastFallback == enc.id) Encoders.SOFTWARE.id else enc.id
                    if (VideoRender.lastFallback == enc.id) note = tr("{0} ging nicht – mit dem Prozessor gerechnet.", enc.label)
                    made += e.out
                }
                Kind.AUDIO -> {
                    VideoRender.render(e.project, VideoRender.preset(e.preset), e.out, audioOnly = true) { v -> update(id) { it.copy(progress = v) } }
                    made += e.out
                }
                Kind.SUBTITLES -> {
                    e.out.writeText(VideoRender.srt(e.project))
                    val vtt = File(e.out.parentFile, e.out.nameWithoutExtension + ".vtt").apply { writeText(VideoRender.vtt(e.project)) }
                    made += listOf(e.out, vtt)
                }
                Kind.CHAPTERS -> {
                    e.out.writeText(VideoEdit.youtubeChapters(e.project) + "\n")
                    made += e.out
                }
            }
            val remote = mutableListOf<String>()
            val up = uploader
            if (e.upload != null && up != null) {
                update(id) { it.copy(note = tr("Lädt nach Nextcloud …")) }
                made.forEach { f -> remote += up(f, e.upload) }
            }
            update(id) { it.copy(status = Status.DONE, progress = 1f, files = made, encoder = encoderUsed, note = note, remote = remote, seconds = (System.nanoTime() - started) / 1e9) }
        } catch (c: CancellationException) {
            update(id) { it.copy(status = Status.CANCELLED, note = null) }
            e.out.delete()
        } catch (t: Throwable) {
            AppLog.i("Export „${e.out.name}“ fehlgeschlagen: ${t.message}")
            update(id) { it.copy(status = Status.FAILED, error = t.message ?: t.javaClass.simpleName, note = null) }
        } finally {
            running.remove(id)
            _entries.value.firstOrNull { it.id == id }?.let { done -> runCatching { onFinished?.invoke(done) } }
        }
    }

    /** Stops a waiting or running entry. */
    fun cancel(id: String) {
        val e = _entries.value.firstOrNull { it.id == id } ?: return
        if (e.status == Status.WAITING) update(id) { it.copy(status = Status.CANCELLED) }
        running[id]?.cancel()
    }

    /** Removes the finished entries from the list (the files stay). */
    fun clearFinished() { _entries.value = _entries.value.filter { !it.finished } }

    /** Waits until the queue is empty (tests). */
    suspend fun idle() { lock.withLock { } ; while (running.isNotEmpty()) kotlinx.coroutines.delay(50) }
}
