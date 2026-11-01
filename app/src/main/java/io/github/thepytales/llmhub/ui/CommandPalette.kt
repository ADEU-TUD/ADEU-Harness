package io.github.thepytales.llmhub.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.thepytales.llmhub.AppContainer

/** Route of a mode in the command palette: starts a new chat in it (AP-05). */
const val MODE_ROUTE = "mode:"

/** Something the command palette and the settings search can jump to. */
data class Command(val title: String, val route: String, val group: String, val keywords: String = "")

/** Areas of the app and settings pages with words people search for (Strg+K and the settings search). */
val APP_COMMANDS: List<Command> get() = listOf(
    Command("Neuer Chat", "chat", "Aktionen", "neu chat gespräch"),
    Command("Literatur", "literature", "Bereiche", "literatursuche quellen literaturverzeichnis zitieren bibtex ris zotero"),
    Command("Dateien", "files", "Bereiche", "dokumente editor entwurf dokument bearbeiten kommentare versionen präsentation folien tabelle excel powerpoint word ordner hochgeladen erstellt papierkorb lora-ordner"),
    Command("Transkription", "transcripts", "Bereiche", "audio interview aufnahme diktat codieren"),
    Command("Erstellen", "templates", "Bereiche", "tu vorlagen vorlagen prompt vorlage lehre forschung gestalten design poster flyer aushang social media kreativ"),
    Command("Bilder erzeugen", "images", "Bereiche", "bild grafik galerie"),
    Command("Geplante Aufgaben", "tasks", "Bereiche", "aufgabe zeitplan"),
    Command("Modelle vergleichen", "compare", "Bereiche", "vergleich antwort"),
    Command("Modelle", "models", "Bereiche", "modell liste"),
    Command("Verbrauch", "usage", "Bereiche", "kosten token bewertungen statistik"),
    Command("Einstellungen", "settings", "Einstellungen", "optionen"),
) + io.github.thepytales.llmhub.agent.ChatModes.visible().map { m ->
    // Modes (AP-05) start a new chat in that mode.
    Command(m.name, MODE_ROUTE + m.id, "Modi", m.keywords + " modus " + m.command.lowercase())
} + SETTINGS_INDEX

/** Settings pages with what can be set there, for the search in the settings overview. */
val SETTINGS_INDEX = listOf(
    Command("API-Zugang", "settings/api", "Einstellungen", "schlüssel api key server konto anmelden"),
    Command("GitHub", "settings/github", "Einstellungen", "repository token code"),
    Command("Chat & Werkzeuge", "settings/chat", "Einstellungen", "standardmodell werkzeuge freigaben websuche denken"),
    Command("Werkzeuge", "settings/tools", "Einstellungen", "werkzeuge einschalten ausschalten pdf tresor seitenleiste mehr reihenfolge"),
    Command("Lora", "settings/lora", "Einstellungen", "lora maskottchen ausblenden navigator helfer animation hinweise"),
    Command("Datenschutz", "settings/privacy", "Einstellungen", "pseudonymisierung namen eigene begriffe"),
    Command("Gedächtnis", "settings/memory", "Einstellungen", "erinnerungen merken"),
    Command("Wissenssammlung", "settings/knowledge", "Einstellungen", "pdf wissen dokumente durchsuchen"),
    Command("Literatur & Zotero", "settings/literature", "Einstellungen", "zitierstil apa din harvard openalex zotero quellen"),
    Command("Transkription", "settings/transcription", "Einstellungen", "whisper modell sprecher diarisierung"),
    Command("Dokumente & Logos", "settings/documents", "Einstellungen", "logo briefkopf stil schrift corporate design"),
    Command("Vorlesen & Spracheingabe", "settings/voice", "Einstellungen", "stimme tts sprechen diktieren"),
    Command("Darstellung", "settings/appearance", "Einstellungen", "dunkel hell farbe theme schriftgröße kontrast"),
    Command("Sicherung & Umzug", "settings/backup", "Einstellungen", "backup export import umzug"),
    Command("Papierkorb", "settings/trash", "Einstellungen", "gelöscht wiederherstellen"),
    Command("Arbeitsspeicher", "settings/ram", "Einstellungen", "speicher ram arbeitsspeicher langsam messen freigeben"),
    Command("Lokale Ordner", "settings/folders", "Einstellungen", "ordner pc dateien agent explorer freigeben windows"),
    Command("Synchronisation", "settings/sync", "Einstellungen", "sync cloud onedrive google drive nextcloud zweiter pc laptop mehrere rechner datenordner"),
    Command("Speicherorte", "settings/storage", "Einstellungen", "ablageort vault obsidian google drive onedrive synchronisieren ordner"),
    Command("Updates", "settings/updates", "Einstellungen", "update aktualisieren neue version installieren netzlaufwerk"),
)

/** All words of the query must occur in title, group or keywords; title matches come first. */
fun filterCommands(commands: List<Command>, query: String): List<Command> {
    val words = query.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
    if (words.isEmpty()) return commands
    return commands
        .filter { cmd -> val hay = "${cmd.title} ${cmd.group} ${cmd.keywords}".lowercase(); words.all { it in hay } }
        .sortedBy { cmd -> if (cmd.title.lowercase().startsWith(words.first())) 0 else if (words.first() in cmd.title.lowercase()) 1 else 2 }
}

/** Strg+K: jump to any area, settings page or chat by typing. */
@Composable
fun CommandPalette(c: AppContainer, onNavigate: (String) -> Unit, onOpenChat: (String) -> Unit, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val conversations by c.chats.conversations.collectAsStateWithLifecycle()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val commands = remember(query) { filterCommands(APP_COMMANDS, query).take(12) }
    val chats = remember(query, conversations) {
        if (query.isBlank()) conversations.filter { !it.archived }.sortedByDescending { it.updatedAt }.take(5)
        else conversations.filter { it.title.contains(query.trim(), ignoreCase = true) }.sortedByDescending { it.updatedAt }.take(8)
    }
    /** Areas and settings open as screens; a mode starts a new chat in it. */
    fun go(route: String) {
        if (route.startsWith(MODE_ROUTE)) {
            ChatHandoff.pending.value = ChatHandoff.Content("", emptyList(), mode = route.removePrefix(MODE_ROUTE))
            onNavigate("chat")
        } else onNavigate(route)
    }
    fun first() {
        commands.firstOrNull()?.let { go(it.route); onDismiss() } ?: chats.firstOrNull()?.let { onOpenChat(it.id); onDismiss() }
    }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(20.dp), tonalElevation = 6.dp, modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    placeholder = { Text(tr("Bereich, Einstellung oder Chat suchen …")) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { first() }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
                LazyColumn(Modifier.heightIn(max = 420.dp).padding(top = 6.dp)) {
                    // Lora fragen (AP-07): the typed wish goes to Lora, who finds the place or hands it to a chat.
                    item(key = "lora") {
                        PaletteRow(
                            if (query.isBlank()) tr("Lora fragen") else tr("Lora fragen") + ": „" + query.trim() + "“",
                            "Lora",
                        ) { LoraHelper.open(query.trim()); onDismiss() }
                    }
                    var lastGroup: String? = null
                    commands.forEach { cmd ->
                        if (cmd.group != lastGroup) {
                            lastGroup = cmd.group
                            item(key = "g_" + cmd.group) { PaletteHeading(cmd.group) }
                        }
                        item(key = "c_" + cmd.route) {
                            PaletteRow(cmd.title, cmd.route.takeIf { it.startsWith("settings/") }?.let { "Einstellungen" }) { go(cmd.route); onDismiss() }
                        }
                    }
                    if (chats.isNotEmpty()) {
                        item(key = "g_chats") { PaletteHeading(tr(if (query.isBlank()) "Zuletzt" else "Chats")) }
                        items(chats, key = { "chat_" + it.id }) { conv ->
                            PaletteRow(conv.title, null, chat = true) { onOpenChat(conv.id); onDismiss() }
                        }
                    }
                    if (commands.isEmpty() && chats.isEmpty()) item { Text(tr("Nichts gefunden."), Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
    }
}

@Composable
private fun PaletteHeading(text: String) {
    Text(tr(text), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 12.dp, top = 10.dp, bottom = 2.dp))
}

@Composable
private fun PaletteRow(title: String, hint: String?, chat: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (chat) {
            Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.size(10.dp))
        }
        Text(if (chat) title else tr(title), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        hint?.let { Text(tr(it), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

/** Strg+/: the keyboard shortcuts of the Windows version. */
@Composable
fun ShortcutsDialog(onDismiss: () -> Unit) {
    val shortcuts = listOf(
        "Strg+K" to "Befehlspalette: Bereiche, Einstellungen, Chats",
        "Strg+N" to "Neuer Chat",
        "Strg+B" to "Seitenleiste ein/aus",
        "Strg+L" to "Lora öffnen und schließen",
        "Strg+Umschalt+L" to "Literatur",
        "Strg+D" to "Dateien",
        "Strg+T" to "Transkription",
        "Strg+," to "Einstellungen",
        "Strg+/" to "Diese Übersicht",
        "Strg++ / Strg+−" to "Zoom (Strg+0: 100 %, auch Strg+Mausrad)",
        "Enter" to "Senden (Umschalt+Enter: neue Zeile)",
        "/" to "Befehle im Eingabefeld",
        "@" to "Dateien, Literatur und Dokumente erwähnen",
        "Esc" to "Menü schließen bzw. zurück",
    )
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(20.dp), tonalElevation = 6.dp, modifier = Modifier.widthIn(max = 480.dp)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(tr("Tastenkürzel"), style = MaterialTheme.typography.titleLarge)
                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                shortcuts.forEach { (key, what) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.widthIn(min = 72.dp)) {
                            Text(key, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                        }
                        Spacer(Modifier.size(12.dp))
                        Text(tr(what), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text(tr("Schließen")) }
            }
        }
    }
}
