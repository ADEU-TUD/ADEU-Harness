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
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
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

    /** [INTERRUPTED]: was waiting or running when the app ended (AP-81) – „Fortsetzen“ starts it again. */
    enum class Status { WAITING, RUNNING, DONE, FAILED, CANCELLED, INTERRUPTED }

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

    /** What survives a restart (AP-81): the entry as queued, without progress and results. */
    @Serializable
    internal data class Saved(
        val id: String, val project: VideoProject, val kind: Kind, val preset: String, val out: String,
        val useHardware: Boolean = true, val upload: String? = null,
    )

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    /** Where waiting and running entries are kept (set by the app at start, [restore]); null = not kept (tests). */
    @Volatile
    var store: File? = null

    /** Writes the entries that are not finished yet (and the interrupted ones), so they survive a restart. */
    private fun save() {
        val f = store ?: return
        val open = _entries.value.filter { it.status == Status.WAITING || it.status == Status.RUNNING || it.status == Status.INTERRUPTED }
        runCatching {
            if (open.isEmpty()) f.delete()
            else {
                f.parentFile?.mkdirs()
                val tmp = File(f.parentFile, f.name + ".tmp")
                tmp.writeText(json.encodeToString(ListSerializer(Saved.serializer()), open.map { Saved(it.id, it.project, it.kind, it.preset, it.out.absolutePath, it.useHardware, it.upload) }))
                if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
            }
        }.onFailure { AppLog.i("Export-Warteschlange nicht gespeichert: ${it.message}") }
    }

    /**
     * After a restart (AP-81): the entries that were waiting or running come back as [Status.INTERRUPTED] – a half
     * written file is removed –; with [resume] they start again at once, else on „Fortsetzen“. Returns how many.
     */
    fun restore(file: File, resume: Boolean): Int {
        store = file
        val saved = runCatching { json.decodeFromString(ListSerializer(Saved.serializer()), file.readText()) }.getOrNull().orEmpty()
        val known = _entries.value.map { it.id }.toSet()
        val back = saved.filter { it.id !in known }.map { s ->
            File(s.out).let { out -> if (out.isFile && out.length() >= 0) out.delete() }
            Entry(id = s.id, project = s.project, kind = s.kind, preset = s.preset, out = File(s.out), useHardware = s.useHardware, upload = s.upload,
                status = Status.INTERRUPTED, note = tr("Beim letzten Beenden nicht fertig geworden"))
        }
        if (back.isEmpty()) return 0
        _entries.value = _entries.value + back
        if (resume) resumeAll() else save()
        return back.size
    }

    /** Starts an interrupted (or cancelled, failed) entry again. */
    fun resume(id: String) {
        val e = _entries.value.firstOrNull { it.id == id } ?: return
        if (e.status == Status.WAITING || e.status == Status.RUNNING || e.status == Status.DONE) return
        update(id) { it.copy(status = Status.WAITING, progress = 0f, error = null, note = null) }
        start(id)
    }

    /** Registers the job before it starts: a very short entry cannot finish (and sign off) before it is known. */
    private fun start(id: String) {
        val job = scope.launch(start = kotlinx.coroutines.CoroutineStart.LAZY) { lock.withLock { run(id) } }
        running[id] = job
        // Also a job cancelled before it ever ran signs off (its finally in run() is never reached then).
        job.invokeOnCompletion { running.remove(id, job) }
        job.start()
    }

    fun resumeAll() = _entries.value.filter { it.status == Status.INTERRUPTED }.forEach { resume(it.id) }

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
    val busy: Int get() = _entries.value.count { it.status == Status.WAITING || it.status == Status.RUNNING }

    private fun update(id: String, f: (Entry) -> Entry) {
        val before = _entries.value.firstOrNull { it.id == id }?.status
        _entries.value = _entries.value.map { if (it.id == id) f(it) else it }
        // Only a change of status is written (not every percent of progress).
        if (_entries.value.firstOrNull { it.id == id }?.status != before) save()
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
        save()
        list.forEach { e -> start(e.id) }
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
        if (e.status == Status.WAITING || e.status == Status.INTERRUPTED) update(id) { it.copy(status = Status.CANCELLED) }
        running[id]?.cancel()
    }

    /** Removes the finished entries from the list (the files stay). */
    fun clearFinished() { _entries.value = _entries.value.filter { !it.finished }; save() }

    /** Waits until the queue is empty (tests). */
    suspend fun idle() { lock.withLock { } ; while (running.isNotEmpty()) kotlinx.coroutines.delay(50) }
}
