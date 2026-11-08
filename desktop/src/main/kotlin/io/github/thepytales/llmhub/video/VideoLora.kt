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
 * history (Strg+Z works as well); in an Ablauf (AP-40) without the editor, on the stored project. Since AP-52/53
 * also picture-in-picture and markers; since AP-54 speed, freeze, zoom, pixelated faces and a changed voice (the last two
 * are anonymisation and go into the Protokoll der Lora-Aktionen with what was done).
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

    /**
     * The clips an effect is for: the numbers in [text] („2“, „1-3“, „alle“), else the clip under the playhead, else
     * all (when [allByDefault]) or the first.
     */
    internal fun clipsFor(p: VideoProject, text: String?, allByDefault: Boolean = false): List<VideoClip> {
        val t = text?.trim().orEmpty().lowercase()
        if (t == "alle" || t == "all") return p.clips
        if (t.any { it.isDigit() }) return RoughCut.numbers(t, p.clips.size).sorted().map { p.clips[it - 1] }.ifEmpty { throw ActionFailed(tr("Welcher Clip? Nummer 1 bis {0}.", p.clips.size)) }
        if (allByDefault) return p.clips
        val at = host?.takeIf { it.id == p.id }?.playhead?.invoke()
        return listOfNotNull(at?.let { p.at(it)?.first }?.let { p.clips[it] } ?: p.clips.firstOrNull())
    }

    /** „oben links“, „rechts“, „Mitte“ … → the rectangle to zoom onto ([zoom] times larger). */
    internal fun zoomTarget(where: String?, zoom: Double): Key {
        val w = where.orEmpty().lowercase()
        val share = (1.0 / zoom.coerceIn(1.1, 6.0)).toFloat()
        val half = share / 2
        val x = when { "links" in w || "left" in w -> half; "rechts" in w || "right" in w -> 1f - half; else -> 0.5f }
        val y = when { "oben" in w || "top" in w -> half; "unten" in w || "bottom" in w -> 1f - half; else -> 0.5f }
        return Key(0.0, x, y, share)
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
            if (p.clips.isEmpty()) throw ActionFailed(tr("Das Projekt hat noch keine Clips."))
            // The same blade as the editor (speed, reverse and freeze of AP-54 map the cut into the source).
            if (VideoEdit.splitV1(p, at) == null) throw ActionFailed(tr("Bei {0} ist schon ein Schnitt.", clock(at)))
            ActionPlan("video.split", tr("Schneidet bei {0} …", clock(at)), false) {
                change(p, tr("Bei {0} geschnitten.", clock(at))) { pp -> VideoEdit.splitV1(pp, at)?.first ?: pp }
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
            "video.pip", ActionArea.VIDEO, trLater("Bild-in-Bild"),
            "Legt ein Video oder Bild (z. B. die Webcam) als Bild-in-Bild über das Video, auf die nächste freie Videospur (AP-52).",
            listOf(
                ActionParam("datei", "Video oder Bild (leer: das ausgewählte)", required = false),
                ActionParam("ecke", "unten-rechts, unten-links, oben-rechts, oben-links oder voll (Standard unten-rechts)", required = false),
                ActionParam("zeit", "Start auf der Zeitleiste, z. B. 0:05 (leer: Abspielposition)", required = false),
            ), risky = false, android = false, remember = listOf("ecke"),
        ) { c, a ->
            val p = need()
            val f = ToolActions.files(c, a.s("datei")).firstOrNull { it.extension.lowercase() in TextFormats.MEDIA || Media.isStill(it) }
                ?: throw ActionFailed(tr("Welches Video oder Bild? Bitte eine Datei auswählen oder nennen."))
            val corner = a.s("ecke")?.lowercase()?.replace(' ', '-')?.takeIf { e -> Frame.CORNERS.any { it.first == e } } ?: "unten-rechts"
            val at = seconds(a.s("zeit")) ?: host?.takeIf { it.id == p.id }?.playhead?.invoke() ?: 0.0
            ActionPlan("video.pip", tr("Legt „{0}“ als Bild-in-Bild bei {1} …", f.name, clock(at)), false) {
                val info = withContext(Dispatchers.IO) { Media.probe(f) } ?: throw ActionFailed(tr("Die Datei ließ sich nicht lesen."))
                val still = Media.isStill(f, info)
                val len = if (still) 5.0 else info.duration.takeIf { it > 0.05 } ?: 5.0
                change(p, tr("„{0}“ als Bild-in-Bild bei {1}.", f.name, clock(at))) { pp ->
                    VideoEdit.addLayer(VideoEdit.addToPool(pp, listOf(f.absolutePath)), f.absolutePath, at, len, corner, still).first
                }
            }
        },
        ToolAction(
            "video.marker", ActionArea.VIDEO, trLater("Marker setzen"),
            "Setzt einen Marker mit Notiz auf die Zeitleiste; Marker werden zu Kapiteln im MP4 und für YouTube (AP-53).",
            listOf(ActionParam("notiz", "Notiz, z. B. Kapitelname"), ActionParam("zeit", "Zeit, z. B. 1:30 (leer: Abspielposition)", required = false)),
            risky = false, android = false,
        ) { _, a ->
            val p = need()
            val note = a.s("notiz").orEmpty().trim()
            val at = seconds(a.s("zeit")) ?: host?.takeIf { it.id == p.id }?.playhead?.invoke() ?: throw ActionFailed(tr("Wo soll der Marker hin?"))
            ActionPlan("video.marker", tr("Setzt einen Marker bei {0} …", clock(at)), false) {
                change(p, tr("Marker „{0}“ bei {1}.", note.ifBlank { tr("ohne Notiz") }, clock(at))) { pp ->
                    val (q, mid) = VideoEdit.addMarker(pp, at, note)
                    if (note.isBlank()) q else q.copy(markers = q.markers.map { if (it.id == mid) it.copy(note = note) else it })
                }
            }
        },
        ToolAction(
            "video.speed", ActionArea.VIDEO, trLater("Geschwindigkeit"),
            "Ändert die Geschwindigkeit von Clips (0,25 bis 4, z. B. 2 = doppelte Geschwindigkeit); der Ton läuft mit, die Tonhöhe bleibt (AP-54).",
            listOf(ActionParam("faktor", "z. B. 2 (doppelt), 0,5 (halb), 1 (normal)"), ActionParam("clip", "Nummer(n) oder „alle“ (leer: Clip am Abspielkopf)", required = false)),
            risky = false, android = false,
        ) { _, a ->
            val p = need()
            val factor = a.s("faktor")?.replace(',', '.')?.replace("×", "")?.replace("x", "")?.trim()?.toDoubleOrNull()?.takeIf { it in 0.25..4.0 }
                ?: throw ActionFailed(tr("Welche Geschwindigkeit? Zwischen 0,25 und 4."))
            val clips = clipsFor(p, a.s("clip"))
            if (clips.isEmpty()) throw ActionFailed(tr("Das Projekt hat noch keine Clips."))
            ActionPlan("video.speed", tr("Setzt {0} Clip(s) auf {1} …", clips.size, io.github.thepytales.llmhub.ui.speedLabel(factor)), false) {
                change(p, tr("{0} Clip(s) laufen mit {1}.", clips.size, io.github.thepytales.llmhub.ui.speedLabel(factor))) { pp -> clips.fold(pp) { q, c -> VideoFx.speed(q, c.id, factor) } }
            }
        },
        ToolAction(
            "video.freeze", ActionArea.VIDEO, trLater("Standbild"), "Hält das Bild an einer Stelle für einige Sekunden an (Standbild), der Rest rückt nach (AP-54).",
            listOf(ActionParam("zeit", "Zeit auf der Zeitleiste (leer: Abspielposition)", required = false), ActionParam("dauer", "Sekunden (Standard 2)", required = false)),
            risky = false, android = false,
        ) { _, a ->
            val p = need()
            val at = seconds(a.s("zeit")) ?: host?.takeIf { it.id == p.id }?.playhead?.invoke() ?: throw ActionFailed(tr("Wo soll das Standbild hin?"))
            val dur = a.s("dauer")?.replace(',', '.')?.toDoubleOrNull()?.coerceIn(0.2, 60.0) ?: 2.0
            if (VideoFx.freeze(p, at, dur) == null) throw ActionFailed(tr("Das Projekt hat noch keine Clips."))
            ActionPlan("video.freeze", tr("Standbild bei {0} …", clock(at)), false) {
                change(p, tr("Standbild bei {0} für {1} s.", clock(at), dur.toInt().coerceAtLeast(1))) { pp -> VideoFx.freeze(pp, at, dur)?.first ?: pp }
            }
        },
        ToolAction(
            "video.zoom", ActionArea.VIDEO, trLater("Zoomen"),
            "Zoomt in einem Clip sanft auf einen Bereich des Bildes und am Ende wieder heraus, z. B. auf einen Teil des Bildschirms im Screencast (Keyframes, AP-54).",
            listOf(
                ActionParam("bereich", "oben links, oben rechts, unten links, unten rechts, links, rechts, oben, unten oder Mitte"),
                ActionParam("stärke", "Vergrößerung, z. B. 2 (Standard)", required = false),
                ActionParam("clip", "Nummer (leer: Clip am Abspielkopf)", required = false),
            ), risky = false, android = false,
        ) { _, a ->
            val p = need()
            val zoom = a.s("stärke")?.replace(',', '.')?.replace("×", "")?.trim()?.toDoubleOrNull() ?: 2.0
            val target = zoomTarget(a.s("bereich"), zoom)
            val clips = clipsFor(p, a.s("clip"))
            if (clips.isEmpty()) throw ActionFailed(tr("Das Projekt hat noch keine Clips."))
            ActionPlan("video.zoom", tr("Zoomt auf „{0}“ …", a.s("bereich").orEmpty().ifBlank { tr("Mitte") }), false) {
                change(p, tr("Zoom auf „{0}“ in {1} Clip(s) – Keyframes in den Effekten des Clips.", a.s("bereich").orEmpty().ifBlank { tr("Mitte") }, clips.size)) { pp -> clips.fold(pp) { q, c -> VideoFx.zoomTo(q, c.id, target) } }
            }
        },
        ToolAction(
            "video.faces", ActionArea.VIDEO, trLater("Gesichter verpixeln"),
            "Findet Gesichter im Video auf diesem Computer und verpixelt sie; die Bereiche folgen den Personen (Forschungsmaterial, Datenschutz, AP-54).",
            listOf(ActionParam("clip", "Nummer(n) (leer: alle Clips)", required = false), ActionParam("art", "verpixeln (Standard) oder weichzeichnen", required = false)),
            risky = false, android = false,
        ) { _, a ->
            val p = need()
            Faces.missing()?.let { throw ActionFailed(it) }
            val clips = clipsFor(p, a.s("clip"), allByDefault = true)
            if (clips.isEmpty()) throw ActionFailed(tr("Das Projekt hat noch keine Clips."))
            val mode = if (a.s("art").orEmpty().lowercase().let { "weich" in it || "blur" in it }) "blur" else "pixel"
            ActionPlan("video.faces", tr("Sucht Gesichter in {0} Clip(s) …", clips.size), false) {
                val found = clips.associate { c -> c.id to runCatching { Faces.find(c, mode) }.getOrElse { throw ActionFailed(it.message ?: tr("Die Gesichtserkennung ist fehlgeschlagen.")) } }
                val n = found.values.sumOf { it.size }
                val text = tr("Anonymisierung im Video „{0}“: {1} Gesicht(er) in {2} Clip(s) verpixelt ({3}).", p.title, n, clips.size, Faces.detector()?.name ?: "")
                ToolActions.log(text)
                change(p, if (n == 0) tr("Keine Gesichter gefunden – bitte in der Vorschau prüfen.") else "$text " + tr("Bitte in der Vorschau prüfen.")) { pp -> found.entries.fold(pp) { q, (id, r) -> Faces.apply(q, id, r) } }
            }
        },
        ToolAction(
            "video.voice", ActionArea.VIDEO, trLater("Stimme verfremden"),
            "Verfremdet die Stimme (Tonhöhe in Halbtönen, Tempo bleibt) in Clips und auf den Tonspuren – z. B. für Interviews im Forschungsmaterial (AP-54).",
            listOf(ActionParam("halbtöne", "z. B. -4 (tiefer, Standard) oder 3 (höher); 0 = aus", required = false), ActionParam("clip", "Nummer(n) (leer: alle Clips und Tonspuren)", required = false)),
            risky = false, android = false,
        ) { _, a ->
            val p = need()
            val semi = a.s("halbtöne")?.replace(',', '.')?.toFloatOrNull()?.coerceIn(-12f, 12f) ?: -4f
            val ids = a.s("clip")?.takeIf { it.any(Char::isDigit) }?.let { clipsFor(p, it).map { c -> c.id }.toSet() }
            ActionPlan("video.voice", tr("Verfremdet die Stimme ({0} Halbtöne) …", String.format(java.util.Locale.GERMANY, "%+.0f", semi)), false) {
                val text = tr("Anonymisierung im Video „{0}“: Stimme um {1} Halbtöne verschoben ({2}).", p.title, String.format(java.util.Locale.GERMANY, "%+.0f", semi), if (ids == null) tr("alle Clips und Tonspuren") else tr("{0} Clip(s)", ids.size))
                ToolActions.log(text)
                change(p, text) { pp -> VideoFx.voice(pp, semi, ids) }
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
            "video.text_cut", ActionArea.VIDEO, trLater("Im Transkript schneiden"),
            "Schlägt nach einem Wunsch Stellen im Transkript zum Herausschneiden vor (z. B. „Entferne den Teil über die Organisation“, „Kürze auf 10 Minuten, behalte die Beispiele“) und schneidet sie nach der Vorschau heraus; die Zeitleiste folgt (AP-55).",
            listOf(ActionParam("wunsch", "Was herausgeschnitten werden soll")), risky = true, android = false, usesModel = true,
        ) { c, a ->
            val p = need()
            val wish = a.s("wunsch")?.takeIf { it.isNotBlank() } ?: throw ActionFailed(tr("Was soll herausgeschnitten werden?"))
            if (ToolActions.model(c) == null) throw ActionFailed(tr("Dafür braucht es ein Modell – hier ist keines erlaubt oder kein API-Schlüssel da."))
            val all = transcripts(c, p)
            if (all.isEmpty()) throw ActionFailed(tr("Dafür braucht es ein Transkript – zuerst „Untertitel erzeugen“ oder transkribieren."))
            val doc = TextCut.build(p, all)
            val props = runCatching { TextCut.propose(c, doc, p.duration, wish) }.getOrElse { throw ActionFailed(it.message ?: it.javaClass.simpleName) }
            if (props.isEmpty()) throw ActionFailed(tr("Lora hat nichts zum Herausschneiden gefunden."))
            val result = TextCut.accept(p, doc, props)
            val preview = props.map { pr -> (clock(doc.tokens[pr.from].start) + " „" + doc.text(pr.from, pr.to).take(60) + "…“") to pr.reason.ifBlank { tr("herausschneiden") } } +
                (clock(p.duration) to clock(result.duration))
            ActionPlan("video.text_cut", tr("Schneidet {0} Stelle(n) aus dem Transkript …", props.size), true, preview) {
                change(p, tr("{0} Stelle(n) herausgeschnitten, jetzt {1}.", props.size, clock(result.duration))) { _ -> result }
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
