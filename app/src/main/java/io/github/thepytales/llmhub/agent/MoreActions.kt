package io.github.thepytales.llmhub.agent

import io.github.thepytales.llmhub.data.ChatMessage
import io.github.thepytales.llmhub.data.LoraDrive
import io.github.thepytales.llmhub.files.FileText
import io.github.thepytales.llmhub.files.TextFormats
import io.github.thepytales.llmhub.net.ThinkingLevel
import io.github.thepytales.llmhub.net.s
import io.github.thepytales.llmhub.ui.splitThinking
import io.github.thepytales.llmhub.ui.tr
import io.github.thepytales.llmhub.ui.trLater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Actions that join tools (AP-40 Abläufe): transcribe a recording, write a handout as Word, put a file into the
 * Nextcloud. They also stand alone in the Dateien tab. Long ones (transcription) keep running when the panel closes.
 */
internal object MoreActions {
    private val SERVER_AUDIO = setOf("mp3", "m4a", "aac", "wav", "ogg", "oga", "opus", "flac", "webm", "mp4", "mpeg", "mpg")

    private fun mime(ext: String) = when (ext) {
        "mp3", "mpeg", "mpg" -> "audio/mpeg"
        "m4a", "aac" -> "audio/mp4"
        "wav" -> "audio/wav"
        "ogg", "oga", "opus" -> "audio/ogg"
        "flac" -> "audio/flac"
        "webm" -> "audio/webm"
        else -> "video/mp4"
    }

    /** Readable text of a file (transcript, document, PDF), at most [max] characters. */
    internal suspend fun text(f: File, max: Int = 60_000): String = runCatching {
        if (f.length() > 60_000_000) "" else FileText.read(f.name, f.readBytes()).file?.text.orEmpty()
    }.getOrDefault("").take(max)

    /**
     * A transcription with times by the engine of the Transkription area (server or device after the project's level);
     * the transcript appears there too, titled like the file, so subtitles can use it. Null when the engine is not ready.
     */
    internal suspend fun timed(c: io.github.thepytales.llmhub.AppContainer, f: File): io.github.thepytales.llmhub.transcribe.Transcript? = kotlinx.coroutines.coroutineScope {
        val name = f.nameWithoutExtension
        val waiter = async(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
            kotlinx.coroutines.withTimeoutOrNull(4L * 3600 * 1000) { c.transcription.finished.first { it.name == name } }
        }
        val started = runCatching {
            withContext(Dispatchers.IO) { f.inputStream().use { c.transcription.import(f.name, f.length(), it) } }
            val inc = c.transcription.incoming.value.lastOrNull { it.name == f.name } ?: return@runCatching false
            c.transcription.start(inc.token, c.transcription.defaultOptions(io.github.thepytales.llmhub.ui.LoraHelper.folderId).copy(title = name))
            true
        }.getOrDefault(false)
        if (!started) { waiter.cancel(); return@coroutineScope null }
        val done = waiter.await() ?: return@coroutineScope null
        done.error?.let { throw ActionFailed(it) }
        done.transcriptId?.let { c.transcription.load(it) }
    }

    val ALL: List<ToolAction> = listOf(
        ToolAction(
            "files.transcribe", ActionArea.FILES, trLater("Transkribieren"),
            "Verschriftet eine Aufnahme (Audio oder Video) und legt das Transkript als Textdatei daneben bzw. in Erstellt ab.",
            listOf(ActionParam("datei", "Aufnahme (leer: die ausgewählte)", required = false)), risky = false,
        ) { c, a ->
            val f = ToolActions.files(c, a.s("datei")).firstOrNull { it.extension.lowercase() in TextFormats.MEDIA }
                ?: throw ActionFailed(tr("Welche Aufnahme? Bitte eine Audio- oder Videodatei auswählen oder nennen."))
            val level = ToolActions.privacy(c)
            val server = level.allowsUniServer && c.settings.current.hasApiKey && f.length() <= 25L * 1024 * 1024 && f.extension.lowercase() in SERVER_AUDIO
            ActionPlan("files.transcribe", tr("Transkribiert „{0}“ …", f.name), false) {
                // With times like in the Transkription area (also for subtitles), else the quick recognition.
                val timed = runCatching { timed(c, f) }.getOrElse { e -> if (e is kotlinx.coroutines.CancellationException) throw e; null }
                val text = timed?.let { io.github.thepytales.llmhub.transcribe.TranscriptExport.plainText(it) }
                    ?: if (server) c.llm.transcribe(c.settings.current.sttModel, f, null, mime(f.extension.lowercase()))
                    else c.transcription.recognizeShort(f) ?: throw ActionFailed(
                        if (!level.allowsUniServer) tr("Hier bleibt Sprache auf dem Gerät – bitte unter Einstellungen › Transkription ein Erkennungsmodell laden.")
                        else tr("Die Aufnahme ist für den Uni-Server zu groß und auf diesem Gerät ist kein Erkennungsmodell geladen (Einstellungen › Transkription)."),
                    )
                if (text.isBlank()) throw ActionFailed(tr("In „{0}“ wurde keine Sprache erkannt.", f.name))
                val out = ToolActions.beside(c, f, f.nameWithoutExtension + " – " + tr("Transkript") + ".txt")
                withContext(Dispatchers.IO) { out.writeText(text.trim() + "\n") }
                c.drive.changed()
                val where = when {
                    timed != null -> tr("auch in der Transkription")
                    server -> tr("Uni-Server")
                    else -> tr("auf diesem Gerät")
                }
                ActionResult(tr("Transkript „{0}“ ({1} Wörter, {2}).", out.name, text.split(Regex("\\s+")).size, where), listOf(out), undo = { ToolActions.removeMade(c, listOf(out)) })
            }
        },
        ToolAction(
            "files.handout", ActionArea.FILES, trLater("Handout als Word"),
            "Schreibt aus einer Datei (Transkript, Dokument, PDF) ein Handout und speichert es als Word-Datei in Erstellt.",
            listOf(ActionParam("datei", "Grundlage (leer: die ausgewählte)", required = false), ActionParam("wunsch", "Was ins Handout soll, z. B. Kernaussagen und Literatur", required = false)),
            risky = false, usesModel = true,
        ) { c, a ->
            val f = ToolActions.files(c, a.s("datei")).firstOrNull() ?: throw ActionFailed(tr("Woraus soll das Handout entstehen? Bitte eine Datei auswählen."))
            val model = ToolActions.model(c, io.github.thepytales.llmhub.data.ModelCatalog.DEFAULT_CHAT)
                ?: throw ActionFailed(tr("Für ein Handout braucht es ein Modell – hier ist keines erlaubt oder kein API-Schlüssel da."))
            ActionPlan("files.handout", tr("Schreibt ein Handout aus „{0}“ …", f.name), false) {
                val source = withContext(Dispatchers.IO) { text(f) }
                if (source.isBlank()) throw ActionFailed(tr("In „{0}“ steht kein lesbarer Text.", f.name))
                val prefs = LoraPrefs.instructions(c, ActionArea.DOC)
                val prompt = buildString {
                    append("Schreibe aus dem folgenden Material ein Handout für Studierende auf Deutsch in Markdown: Titel (#), kurze Einleitung, ")
                    append("Kernaussagen als Abschnitte (##) mit Stichpunkten, am Ende offene Fragen bzw. Literatur, falls genannt. Höchstens zwei Seiten. ")
                    append("Nichts erfinden, was nicht im Material steht.")
                    a.s("wunsch")?.takeIf { it.isNotBlank() }?.let { append(" Wunsch: ").append(it) }
                    prefs?.let { append("\nVorlieben des Nutzers: ").append(it) }
                    append("\n\nMaterial („").append(f.name).append("“):\n").append(source)
                }
                val r = c.llm.streamChat(model, listOf(ChatMessage(role = "user", content = prompt)), null, thinking = ThinkingLevel.OFF) {}
                val md = splitThinking(r.content).second.trim().ifBlank { throw ActionFailed(tr("Das Modell hat nichts geschrieben.")) }
                val title = Regex("(?m)^#\\s+(.+)$").find(md)?.groupValues?.get(1)?.trim() ?: (f.nameWithoutExtension + " – Handout")
                val out = LoraDrive.unique(c.drive.created, FileText.safeName(title.take(70)) + ".docx")
                withContext(Dispatchers.IO) { io.github.thepytales.llmhub.docs.MarkdownDocx.write(out, title, md) }
                c.drive.changed()
                ActionResult(tr("Handout „{0}“ in Erstellt.", out.name), listOf(out), undo = { ToolActions.removeMade(c, listOf(out)) }, open = null)
            }
        },
        ToolAction(
            "files.to_nextcloud", ActionArea.FILES, trLater("In die Nextcloud"),
            "Lädt Dateien in die Nextcloud (Ordner aus Einstellungen › Verbindungen oder der genannte).",
            listOf(ActionParam("dateien", "Dateien (leer: die ausgewählten)", required = false), ActionParam("ordner", "Ordner in der Nextcloud", required = false)),
            risky = false,
        ) { c, a ->
            if (!c.nextcloud.configured) throw ActionFailed(tr("Die Nextcloud ist noch nicht verbunden (Einstellungen › Verbindungen)."))
            if (ToolActions.privacy(c) == io.github.thepytales.llmhub.data.PrivacyLevel.LOCAL) throw ActionFailed(tr("In diesem Projekt bleibt alles auf dem Gerät – nichts geht in die Nextcloud."))
            val fs = ToolActions.files(c, a.s("dateien"))
            if (fs.isEmpty()) throw ActionFailed(tr("Welche Dateien? Bitte auswählen oder nennen."))
            val folder = a.s("ordner")?.takeIf { it.isNotBlank() }?.let { io.github.thepytales.llmhub.net.WebDavClient.normalize(it) } ?: c.nextcloud.resultFolder
            ActionPlan("files.to_nextcloud", tr("Lädt {0} Dateien in die Nextcloud ({1}) …", fs.size, folder), false) {
                val paths = fs.map { c.nextcloud.save(it, folder) }
                ActionResult(tr("{0} Dateien in der Nextcloud unter {1}.", paths.size, folder))
            }
        },
    )
}
