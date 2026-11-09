package io.github.thepytales.llmhub.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.data.ChatMessage
import io.github.thepytales.llmhub.data.FileAttachment
import io.github.thepytales.llmhub.data.ModelCatalog
import io.github.thepytales.llmhub.data.appendToThread
import io.github.thepytales.llmhub.data.thread
import io.github.thepytales.llmhub.docs.DocComment
import io.github.thepytales.llmhub.docs.DocKind
import io.github.thepytales.llmhub.docs.LiveDoc
import io.github.thepytales.llmhub.net.ThinkingLevel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Requests about a document to the model: in the document's chat (with tools), or directly for a passage. */
object DocsAi {
    /** Sends [text] to the model in the document's chat (creating one if needed); it changes the document with its tools. */
    fun send(c: AppContainer, doc: LiveDoc, label: String, text: String, instructions: String, files: List<FileAttachment> = emptyList()): Boolean {
        val existing = doc.chatId?.let { id -> c.runs.get(id)?.conversation?.value ?: c.chats.get(id) }
        if (existing != null && c.runs.get(existing.id) != null) {
            Notices.info(tr("Die KI arbeitet gerade an diesem Dokument – bitte kurz warten."))
            return false
        }
        val base = existing ?: newConversation(c).copy(title = "${doc.docKind.label}: ${doc.title}".take(60), folderId = null)
        if (existing == null) c.docs.save(doc.copy(chatId = base.id))
        val conv = base.appendToThread(ChatMessage(role = "user", content = text, template = label, instructions = instructions, files = files))
        c.chats.save(conv.copy(updatedAt = System.currentTimeMillis()))
        c.runs.start(conv)
        return true
    }

    /** How the model works on a document of this kind (sent invisibly with each request from the editor). */
    fun instructions(doc: LiveDoc, tabTitle: String? = null, selection: String? = null): String = buildString {
        append("Der Nutzer arbeitet im Editor der App gemeinsam mit dir an ")
        append(
            when (doc.docKind) {
                DocKind.DOC -> "dem Dokument"
                DocKind.MARKDOWN -> "der Markdown-Datei"
                DocKind.SLIDES -> "der Präsentation"
                DocKind.SHEET -> "der Tabelle"
            },
        )
        append(" „${doc.title}“ (file=\"${doc.id}\"). Lies zuerst den aktuellen Stand mit read_document – der Nutzer kann ihn selbst geändert haben. ")
        append("Ändere ihn mit edit_document (file=\"${doc.id}\"): kleine Änderungen als wörtliche Ersetzungen, größere Umbauten mit content")
        if (doc.tabs.size > 1) append(" und tab=Name des Tabs")
        append(". Erstelle dafür kein neues Dokument. Antworte im Chat nur kurz (ein, zwei Sätze), was du geändert hast; bei unklaren Wünschen frag nach. ")
        when (doc.docKind) {
            DocKind.DOC -> append(
                "Format: Markdown mit Überschriften, Listen, Tabellen, Checklisten (- [ ] …) und Hinweisboxen (> [!NOTE]); der Nutzer sieht " +
                    "das Dokument formatiert wie in Word, ohne die Markdown-Zeichen. " + DOC_MARKUP_HELP,
            )
            DocKind.MARKDOWN -> append("Format: eine Markdown-Datei (z. B. README, Notiz); der Nutzer sieht und bearbeitet den Quelltext mit allen Zeichen. ")
            DocKind.SLIDES -> append(
                "Die App zeigt und exportiert die Folien im Corporate Design der TU Dresden (Logo, Farben, Einrichtung, Fußzeile setzt sie " +
                    "selbst – schreib sie nicht auf die Folien). " +
                    "Format der Präsentation: Folien durch eine Zeile „---“ getrennt; erste Folie „# Titel“ und „## Untertitel“, darunter " +
                    "optional Zeilen wie „Vortrag · Oktober 2026“; eine Folie nur mit „## Titel“ und höchstens einem kurzen Satz ist eine " +
                    "Abschnittsfolie, eine Folie nur mit „> Zitat“ (letzte Zeile „> – Quelle“) eine Zitatfolie; jede weitere " +
                    "„## Folientitel“ mit wenigen kurzen Stichpunkten (höchstens 6, je eine Zeile); „|||“ beginnt die rechte Spalte, " +
                    "„Notizen: …“ am Ende einer Folie sind die Sprechernotizen. Details gehören in die Notizen, nicht auf die Folie. " +
                    SLIDE_LAYOUT_HELP,
            )
            DocKind.SHEET -> append(
                "Format der Tabelle: jeder Tab ist ein Tabellenblatt mit genau einer Markdown-Tabelle, die erste Zeile sind die " +
                    "Spaltenköpfe (Spalten A, B, C …, Zeile 1 = Kopfzeile). Formeln beginnen mit = und funktionieren wie in Excel mit " +
                    "deutschen Namen und Semikolon, z.B. =SUMME(B2:B9), =MITTELWERT(C2:C20), =WENN(D2>=50;\"ja\";\"nein\"), =RUNDEN(B2*1,19;2). " +
                    "Nutze Formeln statt fester Ergebnisse, wo sich Werte aus anderen Zellen ergeben. read_document zeigt auch die berechneten Werte. ",
            )
        }
        tabTitle?.let { if (doc.tabs.size > 1) append("Der Nutzer sieht gerade den Tab „$it“. ") }
        selection?.takeIf { it.isNotBlank() }?.let { append("Er hat diese Stelle markiert (darauf bezieht sich die Bitte): <<<${it.take(4000)}>>> ") }
    }

    /** Added to [instructions] when the user attached own material (PDFs, presentations …) to the request. */
    fun materialInstructions(doc: LiveDoc, files: List<FileAttachment>): String = buildString {
        append("Der Nutzer hat eigenes Material angehängt (").append(files.joinToString(", ") { it.name }).append("). ")
        append("Nutze es als inhaltliche Grundlage: übernimm Aussagen, Begriffe, Zahlen und Quellen daraus, erfinde nichts hinzu und ")
        append("nenne bei Zitaten die Quelle. ")
        if (doc.docKind == DocKind.SLIDES) {
            append("Bei angehängten Präsentationen sind die Folien mit ihren Titeln und Notizen gekennzeichnet – behalte deren Reihenfolge ")
            append("und Kernaussagen bei, wenn der Nutzer nichts anderes sagt, verteile lange Textstellen aus PDFs auf mehrere Folien ")
            append("mit kurzen Stichpunkten und schreibe die Details in die Sprechernotizen. ")
        }
        if (files.any { it.truncated }) append("Hinweis: Mindestens eine Datei war sehr lang und wurde gekürzt eingelesen. ")
    }

    fun commentInstructions(doc: LiveDoc, comment: DocComment): String =
        "Der Nutzer hat im Editor einen Kommentar zu „${doc.title}“ geschrieben (Kommentar-id ${comment.id}, " +
            "Tab „${doc.tab(comment.tabId).title}“). Lies das Dokument mit read_document (file=\"${doc.id}\"), ändere die kommentierte Stelle mit " +
            "edit_document (file=\"${doc.id}\", wörtliche Ersetzungen) – nur dort, alles andere bleibt – und antworte danach mit " +
            "reply_to_comment kurz, was du geändert hast. Ist der Wunsch unklar, frage mit reply_to_comment (resolve=false) nach."

    /**
     * Rewrites one passage directly (no chat, no tools) – the ✨ actions on a paragraph, a selection or a table cell.
     * [onText] gets the growing answer; returns the cleaned result.
     */
    suspend fun rewrite(c: AppContainer, doc: LiveDoc?, text: String, instruction: String, onText: (String) -> Unit = {}): String {
        val kind = doc?.docKind ?: DocKind.DOC
        val system = "Du hilfst in einem Schreibprogramm einer Professur der TU Dresden beim Bearbeiten " +
            (if (kind == DocKind.SLIDES) "einer Präsentationsfolie" else if (kind == DocKind.SHEET) "einer Tabelle" else "eines Dokuments") +
            ". Antworte ausschließlich mit dem neuen Text für die markierte Stelle – ohne Einleitung, ohne Erklärung, ohne " +
            "Anführungszeichen. Behalte Markdown-Auszeichnungen (Überschrift, Liste, **fett**) bei, wenn sie zur Stelle gehören. " +
            "Deutsch, sofern nicht anders verlangt; geschlechtergerecht; keine Emojis; erfinde keine Fakten – fehlende Angaben als [Platzhalter]." +
            // The memory (style, function …) also applies here, so texts sound like the user's own.
            (io.github.thepytales.llmhub.agent.Memory.promptFor(c, null)?.let { "\n\n$it" } ?: "")
        val context = doc?.markdown()?.take(6000).orEmpty()
        val user = buildString {
            if (context.isNotBlank()) append("Zusammenhang (das ganze Dokument „${doc?.title}“, gekürzt):\n<<<\n").append(context).append("\n>>>\n\n")
            if (text.isBlank()) append("Die Stelle ist noch leer.\n") else append("Markierte Stelle:\n<<<\n").append(text).append("\n>>>\n\n")
            append("Aufgabe: ").append(instruction)
        }
        val result = c.llm.streamChat(
            model = c.settings.current.defaultModel,
            messages = listOf(ChatMessage(role = "system", content = system), ChatMessage(role = "user", content = user)),
            tools = null,
            thinking = ThinkingLevel.OFF,
        ) { u -> onText(io.github.thepytales.llmhub.agent.FieldAssist.clean(u.content)) }
        return io.github.thepytales.llmhub.agent.FieldAssist.clean(result.content).ifEmpty { throw IllegalStateException(tr("Das Modell hat keinen Text geliefert.")) }
    }

    /** Spelling and grammar check of [text] by the model: the corrections it suggests (see [SpellCheck]). */
    suspend fun spellCheck(c: AppContainer, doc: LiveDoc?, text: String): List<io.github.thepytales.llmhub.docs.SpellCheck.Correction> {
        val system = "Du bist ein sorgfältiges Korrektorat in einem Schreibprogramm" + (doc?.title?.let { " (Dokument „$it“)" } ?: "") + ". " +
            io.github.thepytales.llmhub.docs.SpellCheck.INSTRUCTION
        val result = c.llm.streamChat(
            model = c.settings.current.defaultModel,
            messages = listOf(ChatMessage(role = "system", content = system), ChatMessage(role = "user", content = "Text:\n<<<\n${text.take(30_000)}\n>>>")),
            tools = null,
            thinking = ThinkingLevel.OFF,
        ) { }
        val answer = splitThinking(result.content).second
        if (answer.isBlank()) throw IllegalStateException(tr("Das Modell hat nicht geantwortet."))
        return io.github.thepytales.llmhub.docs.SpellCheck.parse(answer)
    }
}

/** ✨ actions on a passage: what the menu shows and what the model is asked to do. */
enum class InlineAi(val label: String, val instruction: String) {
    IMPROVE(trLater("Verbessern"), "Verbessere Stil, Verständlichkeit und Grammatik; Inhalt und Länge bleiben ungefähr gleich."),
    SPELLING(trLater("Rechtschreibung"), "Korrigiere nur Rechtschreibung, Grammatik und Zeichensetzung; sonst nichts ändern."),
    SHORTER(trLater("Kürzen"), "Kürze deutlich auf das Wesentliche; wichtige Inhalte, Zahlen und Termine bleiben."),
    LONGER(trLater("Ausführlicher"), "Formuliere ausführlicher und konkreter, mit Beispielen, wo sie helfen; erfinde keine Fakten."),
    CONTINUE(trLater("Weiterschreiben"), "Schreibe den Text sinnvoll weiter (ein bis zwei Absätze) und gib den bisherigen Text samt Fortsetzung zurück."),
    FORMAL(trLater("Förmlicher"), "Formuliere förmlicher und sachlicher (Sie-Form, wie in einem Schreiben der Universität)."),
    SIMPLE(trLater("Einfacher"), "Formuliere einfacher und leichter verständlich (kurze Sätze, wenig Fachwörter)."),
    BULLETS(trLater("Als Stichpunkte"), "Fasse als knappe Markdown-Aufzählung zusammen."),
    PROSE(trLater("Als Fließtext"), "Formuliere die Stichpunkte als zusammenhängenden Fließtext aus."),
    ENGLISH(trLater("Ins Englische"), "Übersetze ins Englische (akademischer Stil)."),
    GERMAN(trLater("Ins Deutsche"), "Übersetze ins Deutsche."),
    CUSTOM(trLater("Eigene Anweisung …"), ""),
}

/** What the model suggested for a passage, shown below it with Übernehmen / Verwerfen. */
data class AiSuggestion(val original: String, val text: String, val label: String, val done: Boolean, val error: String? = null)

/**
 * Card below a passage with the model's suggestion (growing while it writes): take it, discard it or try again.
 */
@Composable
fun AiSuggestionCard(s: AiSuggestion, onAccept: () -> Unit, onDiscard: () -> Unit, onRetry: () -> Unit, onInsertBelow: (() -> Unit)? = null) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.size(6.dp))
                Text(tr("KI-Vorschlag · {0}", s.label), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                if (!s.done) CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
            }
            when {
                s.error != null -> Text(s.error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                s.text.isBlank() -> Text(tr("KI schreibt …"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                else -> MarkdownText(s.text)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Button(onClick = onAccept, enabled = s.done && s.error == null && s.text.isNotBlank(), contentPadding = PaddingValues(horizontal = 14.dp)) { Text(tr("Übernehmen")) }
                if (onInsertBelow != null) OutlinedButton(onClick = onInsertBelow, enabled = s.done && s.error == null, contentPadding = PaddingValues(horizontal = 12.dp)) { Text(tr("Darunter einfügen")) }
                TextButton(onClick = onRetry, enabled = s.done) { Text(tr("Erneut")) }
                TextButton(onClick = onDiscard) { Text(tr("Verwerfen")) }
            }
        }
    }
}

/** Menu of ✨ actions; [onPick] gets the action and, for CUSTOM, the user's instruction. */
@Composable
fun InlineAiMenu(expanded: Boolean, onDismiss: () -> Unit, actions: List<InlineAi> = InlineAi.entries, onPick: (InlineAi, String) -> Unit) {
    var custom by remember { mutableStateOf(false) }
    androidx.compose.material3.DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        actions.forEach { a ->
            androidx.compose.material3.DropdownMenuItem(
                text = { Text(tr(a.label)) },
                leadingIcon = if (a == InlineAi.CUSTOM) null else ({ Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(16.dp)) }),
                onClick = {
                    onDismiss()
                    if (a == InlineAi.CUSTOM) custom = true else onPick(a, a.instruction)
                },
            )
        }
    }
    if (custom) TextPrompt(tr("Was soll die KI mit der Stelle tun?"), "", onDismiss = { custom = false }) { text ->
        custom = false
        onPick(InlineAi.CUSTOM, text)
    }
}

/** Runs a ✨ action and keeps its suggestion; [start] begins it, [state] is what the card shows. */
class InlineAiRunner(private val c: AppContainer, private val scope: kotlinx.coroutines.CoroutineScope) {
    var state by mutableStateOf<Pair<String, AiSuggestion>?>(null)
        private set
    private var job: Job? = null
    private var last: Triple<String, String, String>? = null

    /** [key] says where the suggestion belongs (block index, cell …). */
    fun start(key: String, doc: LiveDoc?, text: String, label: String, instruction: String) {
        job?.cancel()
        last = Triple(key, label, instruction)
        state = key to AiSuggestion(text, "", label, done = false)
        job = scope.launch {
            runCatching {
                DocsAi.rewrite(c, doc, text, instruction) { partial -> state = key to AiSuggestion(text, partial, label, done = false) }
            }.onSuccess { r -> state = key to AiSuggestion(text, r, label, done = true) }
                .onFailure { e -> if (e !is kotlinx.coroutines.CancellationException) state = key to AiSuggestion(text, "", label, done = true, error = e.message ?: tr("Fehler")) }
        }
    }

    fun retry(doc: LiveDoc?) {
        val (key, label, instruction) = last ?: return
        val original = state?.second?.original ?: return
        start(key, doc, original, label, "$instruction (Bitte einen anderen Vorschlag als vorher.)")
    }

    fun clear() {
        job?.cancel()
        state = null
    }
}

@Composable
fun rememberInlineAi(c: AppContainer): InlineAiRunner {
    val scope = rememberCoroutineScope()
    return remember { InlineAiRunner(c, scope) }
}

/** Quick requests when own files are attached, per kind of document. */
private fun materialActions(kind: DocKind): List<Pair<String, String>> = when (kind) {
    DocKind.SLIDES -> listOf(
        tr("Folien aus den Dateien") to tr("Erstelle aus den angehängten Dateien eine vollständige Präsentation im TUD-Design (ersetze die bisherigen " +
            "Platzhalterfolien): Titelfolie, Abschnittsfolien für die Teile, Inhaltsfolien mit höchstens 5 kurzen Stichpunkten, Tabellen " +
            "und Zitate, wo sie im Material stehen, eine Abschlussfolie; zu jeder Folie Sprechernotizen aus dem Material."),
        tr("Ins TUD-Design übertragen") to tr("Übertrage die angehängte Präsentation Folie für Folie in diese Präsentation – gleiche Reihenfolge, " +
            "gleiche Inhalte, aber in die Folientypen der App (Titel, Abschnitt, Inhalt, zwei Spalten, Zitat); Notizen bleiben Notizen."),
        tr("Folien ergänzen") to tr("Ergänze die vorhandenen Folien mit passenden Inhalten aus den angehängten Dateien (neue Folien, wo nötig); " +
            "Vorhandenes bleibt erhalten."),
        tr("Zusammenfassen") to tr("Fasse die angehängten Dateien auf 3–5 Folien zusammen (Kernaussagen, wichtigste Zahlen, Fazit)."),
    )
    DocKind.SHEET -> listOf(
        tr("Tabellen übernehmen") to tr("Übernimm die Tabellen und Zahlen aus den angehängten Dateien in diese Tabelle (ein Tab je Tabelle)."),
    )
    else -> listOf(
        tr("Aus den Dateien schreiben") to tr("Schreibe auf Grundlage der angehängten Dateien einen vollständigen Text passend zum Titel."),
        tr("Zusammenfassen") to tr("Fasse die angehängten Dateien zusammen und setze die Zusammenfassung ins Dokument."),
        tr("Einarbeiten") to tr("Arbeite die wichtigsten Inhalte der angehängten Dateien an passender Stelle ins Dokument ein."),
    )
}

/** Extras of the text documents the model may use (see docs/DocMarkup). */
const val DOC_MARKUP_HELP = "Fußnoten: [^1] im Text und eine Zeile „[^1]: Text“ am Ende. Bilder: eigene Zeile „![Bildunterschrift](Pfad){width=50%}“ " +
    "(vorhandene Bildzeilen unverändert lassen). Seitenumbruch: eine Zeile \\newpage. Querformat-Seiten zwischen den Zeilen " +
    "„<!-- quer -->“ und „<!-- hoch -->“. Zeilen mit <!-- … --> (Seiteneinrichtung, Literaturliste) nicht ändern. " +
    "Flussdiagramme, Mindmaps, Organigramme und Zeitstrahlen als ```mermaid-Block (flowchart TD, mindmap, timeline) – sie werden gezeichnet; ```diagram-Blöcke unverändert lassen. "

/** Slide layouts beyond the ones recognized from their content (see docs/SlideDeck). */
const val SLIDE_LAYOUT_HELP = "Weitere Folientypen über eine Zeile direkt unter dem Folientitel: „<!-- layout: bild-text -->“ " +
    "(bzw. bild-links; Bild als Zeile „![Beschreibung](Pfad)“), „<!-- layout: bild -->“ (Vollbild), „<!-- layout: vergleich -->“ " +
    "(zwei Spalten mit „### Überschrift“ oben, getrennt durch „|||“), „<!-- layout: tabelle -->“, „<!-- layout: diagramm -->“ " +
    "(mit ```chart-Block), „<!-- layout: agenda -->“ (leer = automatisch aus den Abschnittsfolien), „<!-- layout: abschluss -->“ " +
    "(Dank und Kontakt). "

/** Makes all slides look and read alike (also from the slide toolbar). */
const val STYLE_SLIDES = "Vereinheitliche den Stil aller Folien: gleiche Form der Titel (kurz, ohne Punkt), Stichpunkte in derselben " +
    "grammatischen Form (z. B. alle als Nominalphrase oder alle als kurzer Satz), einheitliche Schreibweisen, Zahlen und " +
    "Abkürzungen, gleiche Anrede, höchstens 6 Stichpunkte je Folie. Inhalte, Reihenfolge, Folientypen (<!-- layout: … -->), " +
    "Bilder und Notizen bleiben erhalten."

/** Speaker notes for every slide (also from the slide toolbar). */
const val NOTES_ALL_SLIDES = "Schreibe zu jeder Folie ausführliche Sprechernotizen (Notizen: … am Ende der Folie) – was ich dazu " +
    "sagen kann, mit Überleitung zur nächsten Folie. Vorhandene Notizen ergänzt du; die Folien selbst bleiben unverändert."

/** Quick requests offered in the assistant panel, per kind of document. */
private fun quickActions(kind: DocKind): List<Pair<String, String>> = when (kind) {
    DocKind.MARKDOWN -> listOf(
        tr("Entwurf schreiben") to tr("Schreibe einen ersten vollständigen Entwurf passend zum Titel und zu dem, was schon dasteht."),
        tr("Gliederung") to tr("Schlage eine sinnvolle Gliederung mit Überschriften vor und setze sie in die Datei."),
        tr("Inhaltsverzeichnis") to tr("Füge unter dem Titel ein Inhaltsverzeichnis mit Links auf die Überschriften ein."),
        tr("Sprache glätten") to tr("Glätte Sprache, Stil und Rechtschreibung; Inhalt, Aufbau und Markdown bleiben."),
        tr("Auf Englisch") to tr("Übersetze die ganze Datei ins Englische; Markdown und Code bleiben unverändert."),
    )
    DocKind.DOC -> listOf(
        tr("Entwurf schreiben") to tr("Schreibe einen ersten vollständigen Entwurf passend zum Titel und zu dem, was schon dasteht."),
        tr("Gliederung") to tr("Schlage eine sinnvolle Gliederung mit Überschriften vor und setze sie ins Dokument (vorhandener Text bleibt in den passenden Abschnitten)."),
        tr("Weiterschreiben") to tr("Schreibe am Ende des Dokuments sinnvoll weiter (ein bis drei Absätze)."),
        tr("Sprache glätten") to tr("Glätte Sprache, Stil und Rechtschreibung im ganzen Dokument; Inhalt und Aufbau bleiben."),
        tr("Kürzen") to tr("Kürze das Dokument um etwa ein Drittel, ohne wichtige Inhalte zu verlieren."),
        tr("Zusammenfassung oben") to tr("Füge direkt unter dem Titel eine kurze Zusammenfassung (3–5 Sätze) ein."),
        tr("Feedback") to tr("Gib mir kritisches, konkretes Feedback zum Dokument (Aufbau, Argumentation, Verständlichkeit) – ändere noch nichts."),
        tr("Leichte Sprache") to tr("Formuliere das ganze Dokument in einfacher, gut verständlicher Sprache."),
        tr("Auf Englisch") to tr("Übersetze das ganze Dokument ins Englische (Titel eingeschlossen)."),
        tr("TUD-Design") to tr("Prüfe Aufbau und Formulierungen für ein offizielles Dokument der TU Dresden und exportiere es danach mit create_document als Word (format=docx, style=tud)."),
    )
    DocKind.SLIDES -> listOf(
        tr("Folien entwerfen") to tr("Entwirf eine vollständige Präsentation passend zum Titel (8–12 Folien): Titelfolie mit Untertitel und einer " +
            "Zeile Anlass · Datum, Abschnittsfolien für die Teile, Inhaltsfolien mit höchstens 5 kurzen Stichpunkten, wo es passt auch " +
            "zwei Spalten, eine Tabelle oder ein Zitat, eine Abschlussfolie; zu jeder Folie Sprechernotizen."),
        tr("Sprechernotizen") to NOTES_ALL_SLIDES,
        tr("Straffen") to tr("Straffe alle Folien: höchstens 5 kurze Stichpunkte je Folie, Details in die Sprechernotizen."),
        tr("Stil vereinheitlichen") to STYLE_SLIDES,
        tr("Abschlussfolie") to tr("Füge am Ende eine Folie mit Zusammenfassung/Take-aways und eine Folie für Fragen hinzu."),
        tr("Interaktion") to tr("Ergänze an passenden Stellen Folien mit kurzen Aktivierungen für das Publikum (Frage, Murmelgruppe, Abstimmung)."),
        tr("Feedback") to tr("Gib mir Feedback zur Präsentation (Roter Faden, Textmenge, Verständlichkeit) – ändere noch nichts."),
        tr("Als PowerPoint") to tr("Exportiere die Präsentation mit create_document als PowerPoint (format=pptx) im TUD-Design."),
    )
    DocKind.SHEET -> listOf(
        tr("Auswerten") to tr("Werte die Daten aus: wichtigste Kennwerte (Mittelwert, Min, Max, Anzahl) und Auffälligkeiten – rechne exakt mit run_javascript und schreib das Ergebnis in den Chat."),
        tr("Summenzeile") to tr("Füge unter jeder Zahlenspalte eine Zeile mit Summe und Mittelwert als Formeln hinzu."),
        tr("Diagramm") to tr("Erstelle aus den Daten ein passendes Diagramm (create_chart) und erkläre kurz, was es zeigt."),
        tr("Bereinigen") to tr("Bereinige die Tabelle: einheitliche Schreibweisen, Zahlen als Zahlen, leere Zeilen entfernen, sinnvolle Spaltenköpfe."),
        tr("Spalte ergänzen") to tr("Schlage eine sinnvolle zusätzliche Spalte (mit Formel, wenn möglich) vor und füge sie ein."),
        tr("Als Excel") to tr("Exportiere die Tabelle mit create_document als Excel-Datei (format=xlsx)."),
    )
}

/**
 * The assistant beside the document (side panel or sheet): quick requests, a conversation with the model about the
 * document and its answers. The model changes the document itself (edit_document); changes appear highlighted.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DocAssistantPanel(c: AppContainer, doc: LiveDoc, tabTitle: String?, selection: () -> String?, onClose: () -> Unit, onOpenChat: ((String) -> Unit)?) {
    val runs by c.runs.runs.collectAsState()
    val chats by c.chats.conversations.collectAsState()
    val run = doc.chatId?.let { runs[it] }
    val live = run?.conversation?.collectAsState()?.value
    val conv = live ?: doc.chatId?.let { id -> chats.firstOrNull { it.id == id }?.let { c.chats.get(id) } }
    val pending = run?.pending?.collectAsState()?.value
    val working = run != null
    var input by remember { mutableStateOf("") }
    /** Own material for the next request (PDF, PowerPoint, Word …), read like a chat attachment. */
    val material = remember(doc.id) { mutableStateListOf<FileAttachment>() }
    val pickMaterial = rememberDocumentPicker { f -> if (material.none { it.name == f.name }) material += f }
    val messages = conv?.thread().orEmpty().filter { (it.role == "user" || it.role == "assistant") && splitThinking(it.content).second.isNotBlank() }
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size, messages.lastOrNull()?.content?.length) { if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex) }

    fun ask(text: String, label: String = tr("Im Editor")) {
        if (text.isBlank()) return
        val sel = selection()
        val files = material.toList()
        val instructions = DocsAi.instructions(doc, tabTitle, sel) + if (files.isNotEmpty()) DocsAi.materialInstructions(doc, files) else ""
        if (DocsAi.send(c, doc, label, text, instructions, files)) {
            input = ""
            material.clear()
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            // Lora as assistant beside the document (AP-16, like the side panel of Google Docs).
            LoraFaceStill(Modifier.size(26.dp))
            Spacer(Modifier.size(8.dp))
            Column(Modifier.weight(1f)) {
                Text("Lora", style = MaterialTheme.typography.titleSmall)
                Text(ModelCatalog.info(conv?.model ?: c.settings.current.defaultModel).displayName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (doc.chatId != null && onOpenChat != null) TextButton(onClick = { onOpenChat(doc.chatId) }) { Text(tr("Im Chat")) }
            IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, contentDescription = tr("Lora schließen")) }
        }
        HorizontalDivider()
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState, contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (messages.isEmpty()) item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        when (doc.docKind) {
                            DocKind.DOC, DocKind.MARKDOWN -> tr("Schreib gemeinsam mit der KI: Sag ihr, was sie schreiben oder ändern soll – sie arbeitet direkt im Dokument, Änderungen werden farbig markiert und lassen sich im Versionsverlauf zurücknehmen.")
                            DocKind.SLIDES -> tr("Die KI entwirft und überarbeitet Folien direkt in der Präsentation – sag einfach, was du brauchst.")
                            DocKind.SHEET -> tr("Die KI rechnet, ergänzt Formeln, bereinigt Daten und wertet aus – direkt in der Tabelle.")
                        },
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    HintText(tr("Tipp: Text markieren und ✨ wählen, um nur eine Stelle zu ändern."))
                    HintText(
                        if (doc.docKind == DocKind.SLIDES) tr("Eigene PDFs, Präsentationen oder Word-Dateien mit 📎 anhängen – die KI macht daraus Folien oder überträgt sie ins TUD-Design.")
                        else tr("Eigene PDFs, Präsentationen oder Word-Dateien mit 📎 anhängen – die KI arbeitet sie ins Dokument ein."),
                    )
                }
            }
            items(messages, key = { it.id }) { m ->
                val mine = m.role == "user"
                Box(Modifier.fillMaxWidth(), contentAlignment = if (mine) Alignment.CenterEnd else Alignment.CenterStart) {
                    Surface(
                        color = if (mine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.widthIn(max = 300.dp),
                    ) {
                        Box(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                            if (mine) Text(m.content, style = MaterialTheme.typography.bodyMedium, maxLines = 8, overflow = TextOverflow.Ellipsis)
                            else MarkdownText(splitThinking(m.content).second)
                        }
                    }
                }
            }
            if (working) item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.size(8.dp))
                    Text(tr("KI arbeitet am Dokument …"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
            if (pending != null) item {
                Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = RoundedCornerShape(12.dp)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(tr("Die KI möchte: {0}", pending.call.name), style = MaterialTheme.typography.labelLarge)
                        Row {
                            Button(onClick = { c.runs.answer(doc.chatId!!, allow = true) }) { Text(tr("Erlauben")) }
                            Spacer(Modifier.size(8.dp))
                            TextButton(onClick = { c.runs.answer(doc.chatId!!, allow = false) }) { Text(tr("Ablehnen")) }
                        }
                    }
                }
            }
        }
        if (material.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                material.forEach { f ->
                    InputChip(
                        selected = false,
                        onClick = { material.remove(f) },
                        label = { Text(f.name, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 180.dp)) },
                        leadingIcon = { Icon(Icons.Outlined.AttachFile, null, Modifier.size(14.dp)) },
                        trailingIcon = { Icon(Icons.Outlined.Close, contentDescription = tr("Entfernen"), modifier = Modifier.size(14.dp)) },
                    )
                }
            }
        }
        // Quick requests (with attached material first those that use it).
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (if (material.isNotEmpty()) materialActions(doc.docKind) + quickActions(doc.docKind) else quickActions(doc.docKind)).forEach { (label, text) ->
                AssistChip(
                    onClick = { ask(text, label) }, enabled = !working,
                    label = { Text(label) },
                    leadingIcon = { Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(14.dp)) },
                    colors = AssistChipDefaults.assistChipColors(leadingIconContentColor = MaterialTheme.colorScheme.primary),
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.Bottom) {
            IconButton(onClick = pickMaterial, enabled = !working) {
                Icon(Icons.Outlined.AttachFile, contentDescription = tr("Eigene Dateien anhängen (PDF, PowerPoint, Word …)"))
            }
            var field by rememberCursorValue(input)
            OutlinedTextField(
                field, { field = it; input = it.text },
                placeholder = { Text(if (selection().isNullOrBlank()) tr("Was soll die KI schreiben oder ändern?") else tr("Was soll mit der Markierung passieren?")) },
                modifier = Modifier.weight(1f).heightIn(max = 160.dp).onPreviewKeyEvent { e ->
                    when {
                        e.type != KeyEventType.KeyDown || e.key != Key.Enter -> false
                        e.isShiftPressed -> { field = field.withLineBreak(); input = field.text; true }
                        else -> { ask(input); true }
                    }
                },
                shape = RoundedCornerShape(20.dp),
                maxLines = 5,
            )
            Spacer(Modifier.size(6.dp))
            if (working) IconButton(onClick = { c.runs.stop(doc.chatId!!) }) { Icon(Icons.Outlined.Stop, contentDescription = tr("Anhalten")) }
            else IconButton(onClick = { ask(input.ifBlank { tr("Arbeite die angehängten Dateien hier ein.") }) }, enabled = input.isNotBlank() || material.isNotEmpty()) {
                Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = tr("Senden"), tint = if (input.isNotBlank() || material.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
            }
        }
    }
}

/**
 * Slim bar at the bottom of the editor (like "Help me write" in Google Docs): one tap opens the assistant, quick
 * requests start right away.
 */
@Composable
fun AiPromptBar(kind: DocKind, working: Boolean, onOpen: () -> Unit) {
    Surface(
        onClick = onOpen,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shadowElevation = 2.dp,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).widthIn(max = 640.dp).fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (working) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            else Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(10.dp))
            Text(
                if (working) tr("KI arbeitet …") else when (kind) {
                    DocKind.DOC, DocKind.MARKDOWN -> tr("Mit KI schreiben …")
                    DocKind.SLIDES -> tr("Folien mit KI erstellen …")
                    DocKind.SHEET -> tr("KI fragen: rechnen, auswerten, ergänzen …")
                },
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Small round tool button for the formatting bars. */
@Composable
fun ToolIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, active: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(40.dp).then(if (active) Modifier.background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(10.dp)) else Modifier)) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(20.dp))
    }
}

/** Clickable text label in toolbars (e.g. "Absatz ▾"). */
@Composable
fun ToolLabel(text: String, onClick: () -> Unit) {
    Text(
        text, style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 10.dp),
    )
}
