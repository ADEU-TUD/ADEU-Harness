package io.github.thepytales.llmhub.video

import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.agent.ActionArea
import io.github.thepytales.llmhub.agent.ActionFailed
import io.github.thepytales.llmhub.agent.ActionParam
import io.github.thepytales.llmhub.agent.ActionPlan
import io.github.thepytales.llmhub.agent.ActionResult
import io.github.thepytales.llmhub.agent.LoraFocus
import io.github.thepytales.llmhub.agent.ToolAction
import io.github.thepytales.llmhub.agent.ToolActions
import io.github.thepytales.llmhub.data.LoraDrive
import io.github.thepytales.llmhub.files.TextFormats
import io.github.thepytales.llmhub.net.s
import io.github.thepytales.llmhub.transcribe.Transcript
import io.github.thepytales.llmhub.ui.VideoHub
import io.github.thepytales.llmhub.ui.clock
import io.github.thepytales.llmhub.ui.projectFrom
import io.github.thepytales.llmhub.ui.tr
import io.github.thepytales.llmhub.ui.trLater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Lora im Videoeditor (AP-38 Stufe d): her actions work on the project model of today – cut, titles and lower thirds,
 * subtitles from the transcription, rough cut, shorten with Lora, export. With the editor open they go through its
 * history (Strg+Z works as well); in an Ablauf (AP-40) without the editor, on the stored project. AP-52 ff. take the
 * actions over.
 */
object VideoLora {
    /** The editor of the open project: its id, the project as it is now, and a change through its history. */
    class Host(val id: String, val project: () -> VideoProject, val edit: ((VideoProject) -> VideoProject) -> Unit, val playhead: () -> Double)

    @Volatile
    var host: Host? = null

    /** The project Lora made or worked on last (for the next step of an Ablauf). */
    @Volatile
    var lastId: String? = null

    /** Opens a project in the editor (the video screen listens). */
    val openRequest = MutableStateFlow<String?>(null)

    private fun current(): VideoProject? = host?.project?.invoke() ?: lastId?.let { VideoHub.store.get(it) }

    private fun need(): VideoProject = current() ?: throw ActionFailed(tr("Bitte ein Videoprojekt öffnen – oder sag „Mach aus der Aufnahme ein Videoprojekt“."))

    /** Changes the project: through the editor when it is open, else stored directly. */
    private suspend fun edit(id: String, f: (VideoProject) -> VideoProject) {
        val h = host
        if (h != null && h.id == id) withContext(Dispatchers.Main) { h.edit(f) }
        else withContext(Dispatchers.IO) { VideoHub.store.get(id)?.let { VideoHub.store.save(f(it)) } }
        lastId = id
    }

    /** A change with Rückgängig: the project before comes back. */
    private suspend fun change(p: VideoProject, text: String, f: (VideoProject) -> VideoProject): ActionResult {
        val before = p
        edit(p.id, f)
        return ActionResult(text, undo = { edit(before.id) { _ -> before.copy(updatedAt = System.currentTimeMillis()) }; tr("Projekt wie vorher.") })
    }

    /** „1:30“, „90“, „1:02:03“ → seconds; null for blank. */
    internal fun seconds(text: String?): Double? {
        val t = text?.trim()?.replace(',', '.').orEmpty()
        if (t.isEmpty()) return null
        val parts = t.split(':').map { it.trim().toDoubleOrNull() ?: return null }
        return parts.fold(0.0) { acc, v -> acc * 60 + v }
    }

    private suspend fun transcripts(c: AppContainer, p: VideoProject): Map<String, Transcript> = withContext(Dispatchers.IO) {
        // Linked ones, else the transcript titled like the source file (as Lora's transcription names it).
        val list = c.transcription.transcripts.value
        p.clips.map { it.path }.distinct().mapNotNull { path ->
            val id = p.transcripts[path] ?: list.firstOrNull { it.title.equals(File(path).nameWithoutExtension, true) }?.id ?: return@mapNotNull null
            c.transcription.load(id)?.let { path to it }
        }.toMap()
    }

    val ACTIONS: List<ToolAction> = listOf(
        ToolAction(
            "video.from_file", ActionArea.VIDEO, trLater("Videoprojekt anlegen"), "Legt aus einer oder mehreren Aufnahmen (Video) ein neues Videoprojekt an und öffnet es.",
            listOf(ActionParam("dateien", "Videos (leer: die ausgewählten)", required = false)), risky = false, android = false,
        ) { c, a ->
            val fs = ToolActions.files(c, a.s("dateien")).filter { it.extension.lowercase() in TextFormats.MEDIA }
            if (fs.isEmpty()) throw ActionFailed(tr("Welche Aufnahme? Bitte ein Video auswählen oder nennen."))
            ActionPlan("video.from_file", tr("Legt ein Videoprojekt aus {0} Datei(en) an …", fs.size), false) {
                val p = withContext(Dispatchers.IO) { projectFrom(fs) } ?: throw ActionFailed(tr("Die Dateien ließen sich nicht lesen – sind es Videos?"))
                val saved = VideoHub.store.save(p)
                lastId = saved.id
                openRequest.value = saved.id
                ActionResult(tr("Projekt „{0}“ angelegt ({1}).", saved.title, clock(saved.duration)), undo = {
                    VideoHub.store.delete(saved.id)
                    if (lastId == saved.id) lastId = null
                    tr("Projekt wieder entfernt.")
                })
            }
        },
        ToolAction(
            "video.split", ActionArea.VIDEO, trLater("Schneiden"), "Schneidet das Video an einer Stelle (Klinge), z. B. bei 1:30; leer: an der Abspielposition.",
            listOf(ActionParam("zeit", "Zeit auf der Zeitleiste, z. B. 1:30", required = false)), risky = false, android = false,
        ) { _, a ->
            val p = need()
            val at = seconds(a.s("zeit")) ?: host?.takeIf { it.id == p.id }?.playhead?.invoke() ?: throw ActionFailed(tr("Wo soll geschnitten werden?"))
            val (i, src) = p.at(at) ?: throw ActionFailed(tr("Das Projekt hat noch keine Clips."))
            val clip = p.clips[i]
            if (src - clip.inSec < 0.2 || clip.outSec - src < 0.2) throw ActionFailed(tr("Bei {0} ist schon ein Schnitt.", clock(at)))
            ActionPlan("video.split", tr("Schneidet bei {0} …", clock(at)), false) {
                change(p, tr("Bei {0} geschnitten.", clock(at))) { pp ->
                    val k = pp.clips.indexOfFirst { it.id == clip.id }.takeIf { it >= 0 } ?: return@change pp
                    pp.copy(clips = pp.clips.toMutableList().apply { set(k, clip.copy(outSec = src)); add(k + 1, clip.copy(id = newVideoId(), inSec = src, transition = "none")) })
                }
            }
        },
        ToolAction(
            "video.delete_clip", ActionArea.VIDEO, trLater("Clip entfernen"), "Entfernt einen Clip (Nummer in der Zeitleiste, ab 1).",
            listOf(ActionParam("clip", "Nummer des Clips")), risky = true, android = false,
        ) { _, a ->
            val p = need()
            val n = a.s("clip")?.filter { it.isDigit() }?.toIntOrNull()?.minus(1)?.takeIf { it in p.clips.indices } ?: throw ActionFailed(tr("Welcher Clip? Nummer 1 bis {0}.", p.clips.size))
            val clip = p.clips[n]
            ActionPlan("video.delete_clip", tr("Entfernt Clip {0} …", n + 1), true, listOf(tr("Clip {0}: {1} ({2})", n + 1, clip.name, clock(clip.length)) to tr("entfernt"))) {
                change(p, tr("Clip {0} entfernt.", n + 1)) { pp -> pp.copy(clips = pp.clips.filter { it.id != clip.id }) }
            }
        },
        ToolAction(
            "video.title", ActionArea.VIDEO, trLater("Titel oder Bauchbinde"),
            "Setzt einen Text im TUD-Design: titel (am Anfang), bauchbinde (Name und Rolle unten links), einblendung (Satz unten), abspann (am Ende).",
            listOf(
                ActionParam("text", "Haupttext, z. B. Name"), ActionParam("untertext", "Zweite Zeile, z. B. Rolle", required = false),
                ActionParam("stil", "titel, bauchbinde, einblendung oder abspann (Standard bauchbinde)", required = false),
                ActionParam("zeit", "Start, z. B. 0:05 (leer: passend zum Stil)", required = false), ActionParam("dauer", "Sekunden (Standard 5)", required = false),
            ), risky = false, android = false, remember = listOf("stil"),
        ) { _, a ->
            val p = need()
            val text = a.s("text")?.takeIf { it.isNotBlank() } ?: throw ActionFailed(tr("Welcher Text?"))
            val style = a.s("stil")?.lowercase()?.takeIf { s -> TextOverlay.STYLES.any { it.first == s } } ?: "bauchbinde"
            val dur = a.s("dauer")?.replace(',', '.')?.toDoubleOrNull()?.coerceIn(1.0, 60.0) ?: 5.0
            val start = seconds(a.s("zeit")) ?: when (style) {
                "titel" -> 0.0
                "abspann" -> (p.duration - dur).coerceAtLeast(0.0)
                else -> host?.takeIf { it.id == p.id }?.playhead?.invoke() ?: 2.0
            }
            ActionPlan("video.title", tr("Setzt „{0}“ als {1} …", text.take(40), tr(TextOverlay.label(style))), false) {
                change(p, tr("{0} „{1}“ bei {2}.", tr(TextOverlay.label(style)), text.take(40), clock(start))) { pp ->
                    pp.copy(overlays = pp.overlays + TextOverlay(style = style, text = text, subtext = a.s("untertext").orEmpty(), startSec = start, durationSec = dur))
                }
            }
        },
        ToolAction(
            "video.subtitles", ActionArea.VIDEO, trLater("Untertitel erzeugen"),
            "Erzeugt Untertitel aus der Transkription der Aufnahmen (transkribiert vorher, wenn es noch kein Transkript gibt).",
            emptyList(), risky = false, android = false,
        ) { c, _ ->
            val p = need()
            if (p.clips.isEmpty()) throw ActionFailed(tr("Das Projekt hat noch keine Clips."))
            ActionPlan("video.subtitles", tr("Erzeugt Untertitel …"), false) {
                var all = transcripts(c, p)
                // Sources without a transcript are transcribed now (with times, like in the Transkription area).
                p.clips.map { it.path }.distinct().filter { it !in all }.forEach { path ->
                    io.github.thepytales.llmhub.agent.MoreActions.timed(c, File(path))?.let { all = all + (path to it) }
                }
                if (all.isEmpty()) throw ActionFailed(tr("Für die Aufnahmen gibt es kein Transkript, und die Transkription ist nicht bereit (Einstellungen › Transkription)."))
                val cues = RoughCut.subtitles(p.copy(transcripts = p.transcripts + all.mapValues { it.value.id }), all)
                if (cues.isEmpty()) throw ActionFailed(tr("Im Transkript passt nichts zu den Clips."))
                change(p, tr("{0} Untertitel aus der Transkription.", cues.size)) { pp ->
                    pp.copy(subtitles = cues, burnSubtitles = true, transcripts = pp.transcripts + all.mapValues { it.value.id })
                }
            }
        },
        ToolAction(
            "video.rough_cut", ActionArea.VIDEO, trLater("Pausen herausschneiden"), "Schneidet Pausen und Füllwörter heraus (Rohschnitt aus dem Transkript).",
            emptyList(), risky = true, android = false,
        ) { c, _ ->
            val p = need()
            val all = transcripts(c, p)
            if (all.isEmpty()) throw ActionFailed(tr("Für den Rohschnitt braucht es ein Transkript – zuerst „Untertitel erzeugen“ oder transkribieren."))
            var next = p
            var saved = 0.0
            for (i in p.clips.indices.reversed()) {
                val clip = p.clips[i]
                val t = all[clip.path] ?: continue
                val parts = RoughCut.keep(t, clip.inSec, clip.outSec)
                if (parts.isEmpty()) continue
                saved += RoughCut.saved(clip.inSec, clip.outSec, parts)
                next = RoughCut.apply(next, i, parts)
            }
            if (saved < 0.5) throw ActionFailed(tr("Nichts zum Herausschneiden gefunden."))
            val result = next
            ActionPlan("video.rough_cut", tr("Schneidet Pausen und Füllwörter heraus …"), true, listOf(clock(p.duration) to clock(result.duration))) {
                change(p, tr("Rohschnitt fertig: {0} kürzer.", clock(saved))) { _ -> result }
            }
        },
        ToolAction(
            "video.shorten", ActionArea.VIDEO, trLater("Mit Lora kürzen"), "Kürzt das Video nach einem Wunsch (z. B. auf 10 Minuten, Beispiele behalten); vorher Vorschau.",
            listOf(ActionParam("wunsch", "Wie kürzen, z. B. auf 10 Minuten, Beispiele behalten")), risky = true, android = false, usesModel = true,
        ) { c, a ->
            val p = need()
            val wish = a.s("wunsch")?.takeIf { it.isNotBlank() } ?: throw ActionFailed(tr("Wie soll ich kürzen?"))
            if (ToolActions.model(c) == null) throw ActionFailed(tr("Zum Kürzen braucht es ein Modell – hier ist keines erlaubt oder kein API-Schlüssel da."))
            val all = transcripts(c, p)
            if (all.isEmpty()) throw ActionFailed(tr("Zum Kürzen braucht es ein Transkript – zuerst „Untertitel erzeugen“ oder transkribieren."))
            var next = p
            for (i in p.clips.indices.reversed()) {
                val clip = p.clips[i]
                val t = all[clip.path] ?: continue
                val parts = runCatching { RoughCut.withLora(c, t, clip.inSec, clip.outSec, wish) }.getOrNull() ?: continue
                next = RoughCut.apply(next, i, parts)
            }
            if (next == p) throw ActionFailed(tr("Lora hat nichts zum Kürzen gefunden."))
            val result = next
            ActionPlan("video.shorten", tr("Kürzt das Video: {0} …", wish.take(50)), true, listOf(clock(p.duration) to clock(result.duration))) {
                change(p, tr("Gekürzt auf {0}.", clock(result.duration))) { _ -> result }
            }
        },
        ToolAction(
            "video.export", ActionArea.VIDEO, trLater("Exportieren"), "Exportiert das Video als MP4 in Erstellt: Vorgabe opal (720p, klein), youtube (1080p) oder projekt.",
            listOf(ActionParam("vorgabe", "opal, youtube oder projekt", required = false)), risky = false, android = false, remember = listOf("vorgabe"),
        ) { c, a ->
            val p = need()
            if (p.clips.isEmpty()) throw ActionFailed(tr("Das Projekt hat noch keine Clips."))
            val preset = VideoRender.preset(a.s("vorgabe")?.lowercase()?.let { v -> VideoRender.PRESETS.firstOrNull { v.contains(it.id) }?.id })
            ActionPlan("video.export", tr("Exportiert „{0}“ ({1}) …", p.title, tr(preset.label)), false) {
                val out = LoraDrive.unique(c.drive.created, VideoHub.store.fileName(p, "mp4"))
                VideoRender.render(current()?.takeIf { it.id == p.id } ?: p, preset, out) { }
                if (p.subtitles.isNotEmpty()) withContext(Dispatchers.IO) { File(out.parentFile, out.nameWithoutExtension + ".srt").writeText(VideoRender.srt(p)) }
                c.drive.changed()
                LoraFocus.files = listOf(out)
                ActionResult(tr("„{0}“ in Erstellt ({1}).", out.name, clock(p.duration)), listOf(out), undo = { ToolActions.removeMade(c, listOfNotNull(out, File(out.parentFile, out.nameWithoutExtension + ".srt").takeIf { it.isFile })) })
            }
        },
    )

    /** Called once at the start of the Windows app. */
    fun register() = ToolActions.register(ACTIONS)
}
