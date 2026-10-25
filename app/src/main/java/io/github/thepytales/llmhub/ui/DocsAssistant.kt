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
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.AssistChip
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
    fun send(c: AppContainer, doc: LiveDoc, label: String, text: String, instructions: String): Boolean {
        val existing = doc.chatId?.let { id -> c.runs.get(id)?.conversation?.value ?: c.chats.get(id) }
        if (existing != null && c.runs.get(existing.id) != null) {
            Notices.info("Die KI arbeitet gerade an diesem Dokument – bitte kurz warten.")
            return false
        }
        val base = existing ?: newConversation(c).copy(title = "${doc.docKind.label}: ${doc.title}".take(60), folderId = null)
        if (existing == null) c.docs.save(doc.copy(chatId = base.id))
        val conv = base.appendToThread(ChatMessage(role = "user", content = text, template = label, instructions = instructions))
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
                DocKind.SLIDES -> "der Präsentation"
                DocKind.SHEET -> "der Tabelle"
            },
        )
        append(" „${doc.title}“ (file=\"${doc.id}\"). Lies zuerst den aktuellen Stand mit read_document – der Nutzer kann ihn selbst geändert haben. ")
        append("Ändere ihn mit edit_document (file=\"${doc.id}\"): kleine Änderungen als wörtliche Ersetzungen, größere Umbauten mit content")
        if (doc.tabs.size > 1) append(" und tab=Name des Tabs")
        append(". Erstelle dafür kein neues Dokument. Antworte im Chat nur kurz (ein, zwei Sätze), was du geändert hast; bei unklaren Wünschen frag nach. ")
        when (doc.docKind) {
            DocKind.DOC -> append("Format: Markdown mit Überschriften, Listen, Tabellen, Checklisten (- [ ] …) und Hinweisboxen (> [!NOTE]). ")
            DocKind.SLIDES -> append(
                "Format der Präsentation: Folien durch eine Zeile „---“ getrennt; erste Folie „# Titel“ und „## Untertitel“, jede weitere " +
                    "„## Folientitel“ mit wenigen kurzen Stichpunkten (höchstens 6, je eine Zeile); „|||“ beginnt die rechte Spalte, " +
                    "„Notizen: …“ am Ende einer Folie sind die Sprechernotizen. Details gehören in die Notizen, nicht auf die Folie. ",
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
            "Deutsch, sofern nicht anders verlangt; geschlechtergerecht; keine Emojis; erfinde keine Fakten – fehlende Angaben als [Platzhalter]."
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
        return io.github.thepytales.llmhub.agent.FieldAssist.clean(result.content).ifEmpty { throw IllegalStateException("Das Modell hat keinen Text geliefert.") }
    }
}

/** ✨ actions on a passage: what the menu shows and what the model is asked to do. */
enum class InlineAi(val label: String, val instruction: String) {
    IMPROVE("Verbessern", "Verbessere Stil, Verständlichkeit und Grammatik; Inhalt und Länge bleiben ungefähr gleich."),
    SPELLING("Rechtschreibung", "Korrigiere nur Rechtschreibung, Grammatik und Zeichensetzung; sonst nichts ändern."),
    SHORTER("Kürzen", "Kürze deutlich auf das Wesentliche; wichtige Inhalte, Zahlen und Termine bleiben."),
    LONGER("Ausführlicher", "Formuliere ausführlicher und konkreter, mit Beispielen, wo sie helfen; erfinde keine Fakten."),
    CONTINUE("Weiterschreiben", "Schreibe den Text sinnvoll weiter (ein bis zwei Absätze) und gib den bisherigen Text samt Fortsetzung zurück."),
    FORMAL("Förmlicher", "Formuliere förmlicher und sachlicher (Sie-Form, wie in einem Schreiben der Universität)."),
    SIMPLE("Einfacher", "Formuliere einfacher und leichter verständlich (kurze Sätze, wenig Fachwörter)."),
    BULLETS("Als Stichpunkte", "Fasse als knappe Markdown-Aufzählung zusammen."),
    PROSE("Als Fließtext", "Formuliere die Stichpunkte als zusammenhängenden Fließtext aus."),
    ENGLISH("Ins Englische", "Übersetze ins Englische (akademischer Stil)."),
    GERMAN("Ins Deutsche", "Übersetze ins Deutsche."),
    CUSTOM("Eigene Anweisung …", ""),
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
                Text("KI-Vorschlag · ${s.label}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                if (!s.done) CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
            }
            when {
                s.error != null -> Text(s.error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                s.text.isBlank() -> Text("KI schreibt …", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                else -> MarkdownText(s.text)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Button(onClick = onAccept, enabled = s.done && s.error == null && s.text.isNotBlank(), contentPadding = PaddingValues(horizontal = 14.dp)) { Text("Übernehmen") }
                if (onInsertBelow != null) OutlinedButton(onClick = onInsertBelow, enabled = s.done && s.error == null, contentPadding = PaddingValues(horizontal = 12.dp)) { Text("Darunter einfügen") }
                TextButton(onClick = onRetry, enabled = s.done) { Text("Erneut") }
                TextButton(onClick = onDiscard) { Text("Verwerfen") }
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
                text = { Text(a.label) },
                leadingIcon = if (a == InlineAi.CUSTOM) null else ({ Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(16.dp)) }),
                onClick = {
                    onDismiss()
                    if (a == InlineAi.CUSTOM) custom = true else onPick(a, a.instruction)
                },
            )
        }
    }
    if (custom) TextPrompt("Was soll die KI mit der Stelle tun?", "", onDismiss = { custom = false }) { text ->
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
                .onFailure { e -> if (e !is kotlinx.coroutines.CancellationException) state = key to AiSuggestion(text, "", label, done = true, error = e.message ?: "Fehler") }
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

/** Quick requests offered in the assistant panel, per kind of document. */
private fun quickActions(kind: DocKind): List<Pair<String, String>> = when (kind) {
    DocKind.DOC -> listOf(
        "Entwurf schreiben" to "Schreibe einen ersten vollständigen Entwurf passend zum Titel und zu dem, was schon dasteht.",
        "Gliederung" to "Schlage eine sinnvolle Gliederung mit Überschriften vor und setze sie ins Dokument (vorhandener Text bleibt in den passenden Abschnitten).",
        "Weiterschreiben" to "Schreibe am Ende des Dokuments sinnvoll weiter (ein bis drei Absätze).",
        "Sprache glätten" to "Glätte Sprache, Stil und Rechtschreibung im ganzen Dokument; Inhalt und Aufbau bleiben.",
        "Kürzen" to "Kürze das Dokument um etwa ein Drittel, ohne wichtige Inhalte zu verlieren.",
        "Zusammenfassung oben" to "Füge direkt unter dem Titel eine kurze Zusammenfassung (3–5 Sätze) ein.",
        "Feedback" to "Gib mir kritisches, konkretes Feedback zum Dokument (Aufbau, Argumentation, Verständlichkeit) – ändere noch nichts.",
        "Leichte Sprache" to "Formuliere das ganze Dokument in einfacher, gut verständlicher Sprache.",
        "Auf Englisch" to "Übersetze das ganze Dokument ins Englische (Titel eingeschlossen).",
        "TUD-Design" to "Prüfe Aufbau und Formulierungen für ein offizielles Dokument der TU Dresden und exportiere es danach mit create_document als Word (format=docx, style=tud).",
    )
    DocKind.SLIDES -> listOf(
        "Folien entwerfen" to "Entwirf eine vollständige Präsentation passend zum Titel (8–12 Folien, Stichpunkte, Sprechernotizen).",
        "Sprechernotizen" to "Ergänze zu jeder Folie ausführliche Sprechernotizen (was ich dazu sagen kann).",
        "Straffen" to "Straffe alle Folien: höchstens 5 kurze Stichpunkte je Folie, Details in die Sprechernotizen.",
        "Abschlussfolie" to "Füge am Ende eine Folie mit Zusammenfassung/Take-aways und eine Folie für Fragen hinzu.",
        "Interaktion" to "Ergänze an passenden Stellen Folien mit kurzen Aktivierungen für das Publikum (Frage, Murmelgruppe, Abstimmung).",
        "Feedback" to "Gib mir Feedback zur Präsentation (Roter Faden, Textmenge, Verständlichkeit) – ändere noch nichts.",
        "Als PowerPoint" to "Exportiere die Präsentation mit create_document als PowerPoint (format=pptx) im TUD-Design.",
    )
    DocKind.SHEET -> listOf(
        "Auswerten" to "Werte die Daten aus: wichtigste Kennwerte (Mittelwert, Min, Max, Anzahl) und Auffälligkeiten – rechne exakt mit run_javascript und schreib das Ergebnis in den Chat.",
        "Summenzeile" to "Füge unter jeder Zahlenspalte eine Zeile mit Summe und Mittelwert als Formeln hinzu.",
        "Diagramm" to "Erstelle aus den Daten ein passendes Diagramm (create_chart) und erkläre kurz, was es zeigt.",
        "Bereinigen" to "Bereinige die Tabelle: einheitliche Schreibweisen, Zahlen als Zahlen, leere Zeilen entfernen, sinnvolle Spaltenköpfe.",
        "Spalte ergänzen" to "Schlage eine sinnvolle zusätzliche Spalte (mit Formel, wenn möglich) vor und füge sie ein.",
        "Als Excel" to "Exportiere die Tabelle mit create_document als Excel-Datei (format=xlsx).",
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
    val messages = conv?.thread().orEmpty().filter { (it.role == "user" || it.role == "assistant") && splitThinking(it.content).second.isNotBlank() }
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size, messages.lastOrNull()?.content?.length) { if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex) }

    fun ask(text: String, label: String = "Im Editor") {
        if (text.isBlank()) return
        val sel = selection()
        if (DocsAi.send(c, doc, label, text, DocsAi.instructions(doc, tabTitle, sel))) input = ""
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.size(8.dp))
            Column(Modifier.weight(1f)) {
                Text("KI-Assistent", style = MaterialTheme.typography.titleSmall)
                Text(ModelCatalog.info(conv?.model ?: c.settings.current.defaultModel).displayName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (doc.chatId != null && onOpenChat != null) TextButton(onClick = { onOpenChat(doc.chatId) }) { Text("Im Chat") }
            IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, contentDescription = "Assistent schließen") }
        }
        HorizontalDivider()
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState, contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (messages.isEmpty()) item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        when (doc.docKind) {
                            DocKind.DOC -> "Schreib gemeinsam mit der KI: Sag ihr, was sie schreiben oder ändern soll – sie arbeitet direkt im Dokument, Änderungen werden farbig markiert und lassen sich im Versionsverlauf zurücknehmen."
                            DocKind.SLIDES -> "Die KI entwirft und überarbeitet Folien direkt in der Präsentation – sag einfach, was du brauchst."
                            DocKind.SHEET -> "Die KI rechnet, ergänzt Formeln, bereinigt Daten und wertet aus – direkt in der Tabelle."
                        },
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    HintText("Tipp: Text markieren und ✨ wählen, um nur eine Stelle zu ändern.")
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
                    Text("KI arbeitet am Dokument …", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
            if (pending != null) item {
                Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = RoundedCornerShape(12.dp)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Die KI möchte: ${pending.call.name}", style = MaterialTheme.typography.labelLarge)
                        Row {
                            Button(onClick = { c.runs.answer(doc.chatId!!, allow = true) }) { Text("Erlauben") }
                            Spacer(Modifier.size(8.dp))
                            TextButton(onClick = { c.runs.answer(doc.chatId!!, allow = false) }) { Text("Ablehnen") }
                        }
                    }
                }
            }
        }
        // Quick requests.
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            quickActions(doc.docKind).forEach { (label, text) ->
                AssistChip(
                    onClick = { ask(text, label) }, enabled = !working,
                    label = { Text(label) },
                    leadingIcon = { Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(14.dp)) },
                    colors = AssistChipDefaults.assistChipColors(leadingIconContentColor = MaterialTheme.colorScheme.primary),
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.Bottom) {
            OutlinedTextField(
                input, { input = it },
                placeholder = { Text(if (selection().isNullOrBlank()) "Was soll die KI schreiben oder ändern?" else "Was soll mit der Markierung passieren?") },
                modifier = Modifier.weight(1f).heightIn(max = 160.dp).onPreviewKeyEvent { e ->
                    if (e.type == KeyEventType.KeyDown && e.key == Key.Enter && !e.isShiftPressed) { ask(input); true } else false
                },
                shape = RoundedCornerShape(20.dp),
                maxLines = 5,
            )
            Spacer(Modifier.size(6.dp))
            if (working) IconButton(onClick = { c.runs.stop(doc.chatId!!) }) { Icon(Icons.Outlined.Stop, contentDescription = "Anhalten") }
            else IconButton(onClick = { ask(input) }, enabled = input.isNotBlank()) {
                Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = "Senden", tint = if (input.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
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
                if (working) "KI arbeitet …" else when (kind) {
                    DocKind.DOC -> "Mit KI schreiben …"
                    DocKind.SLIDES -> "Folien mit KI erstellen …"
                    DocKind.SHEET -> "KI fragen: rechnen, auswerten, ergänzen …"
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
