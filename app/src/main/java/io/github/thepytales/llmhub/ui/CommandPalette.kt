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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
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
    Command(trLater("Neuer Chat"), "chat", trLater("Aktionen"), "neu chat gespräch"),
    Command(trLater("Literatur"), "literature", trLater("Bereiche"), "literatursuche quellen literaturverzeichnis zitieren bibtex ris zotero"),
    Command(trLater("Dateien"), "files", trLater("Bereiche"), "dokumente editor entwurf dokument bearbeiten kommentare versionen präsentation folien tabelle excel powerpoint word ordner hochgeladen erstellt papierkorb lora-ordner"),
    Command(trLater("Transkription"), "transcripts", trLater("Bereiche"), "audio interview aufnahme diktat codieren"),
    Command(trLater("Scannen"), "scan", trLater("Bereiche"), "scanner einscannen dokument scannen foto kamera pdf texterkennung ocr beleg"),
    Command(trLater("Erstellen"), "templates", trLater("Bereiche"), "tu vorlagen vorlagen prompt vorlage lehre forschung gestalten design poster flyer aushang social media kreativ"),
    Command(trLater("Bilder erzeugen"), "images", trLater("Bereiche"), "bild grafik galerie"),
    Command(trLater("Geplante Aufgaben"), "tasks", trLater("Bereiche"), "aufgabe zeitplan"),
    Command(trLater("Modelle vergleichen"), "compare", trLater("Bereiche"), "vergleich antwort"),
    Command(trLater("Modelle"), "models", trLater("Bereiche"), "modell liste"),
    Command(trLater("Verbrauch"), "usage", trLater("Bereiche"), "kosten token bewertungen statistik"),
    Command(trLater("Einstellungen"), "settings", trLater("Einstellungen"), "optionen"),
    // AP-120: the whole setup step by step (first start, again at any time).
    Command(trLater("Lora einrichten"), "setup", trLater("Einstellungen"), "einrichtung einrichten assistent erster start schritt für schritt neu einrichten schlüssel werkzeuge aussehen"),
    // AP-112: the setup step by step is its own screen, not a settings page.
    Command(trLater("Alles lokal einrichten"), "local-setup", trLater("Einstellungen"), "offline einrichten assistent modelle laden ohne schlüssel alles lokal"),
) + io.github.thepytales.llmhub.agent.ChatModes.visible().map { m ->
    // Modes (AP-05) start a new chat in that mode.
    Command(m.name, MODE_ROUTE + m.id, trLater("Modi"), m.keywords + " modus " + m.command.lowercase())
} + SETTINGS_INDEX

/** Settings pages with what can be set there, for the search in the settings overview. */
val SETTINGS_INDEX = listOf(
    Command(trLater("API-Zugang"), "settings/api", trLater("Einstellungen"), "schlüssel api key server konto anmelden"),
    Command(trLater("GitHub"), "settings/github", trLater("Einstellungen"), "repository token code"),
    Command(trLater("Chat & Werkzeuge"), "settings/chat", trLater("Einstellungen"), "standardmodell werkzeuge freigaben websuche denken"),
    Command(trLater("Werkzeuge"), "settings/tools", trLater("Einstellungen"), "werkzeuge einschalten ausschalten pdf tresor seitenleiste mehr reihenfolge"),
    Command(trLater("Wo Lora arbeitet"), "settings/places", trLater("Einstellungen"), "lokal offline ohne internet ohne server ohne api schlüssel gerät alles lokal gemischt profil einrichtung"),
    Command(trLater("Lora"), "settings/lora", trLater("Einstellungen"), "lora maskottchen ausblenden navigator helfer animation hinweise"),
    Command(trLater("Datenschutz"), "settings/privacy", trLater("Einstellungen"), "pseudonymisierung namen eigene begriffe"),
    Command(trLater("Gedächtnis"), "settings/memory", trLater("Einstellungen"), "erinnerungen merken"),
    Command(trLater("Wissenssammlung"), "settings/knowledge", trLater("Einstellungen"), "pdf wissen dokumente durchsuchen"),
    Command(trLater("Literatur & Zotero"), "settings/literature", trLater("Einstellungen"), "zitierstil apa din harvard openalex zotero quellen"),
    Command(trLater("Transkription"), "settings/transcription", trLater("Einstellungen"), "whisper modell sprecher diarisierung"),
    Command(trLater("Dokumente & Logos"), "settings/documents", trLater("Einstellungen"), "logo briefkopf stil schrift corporate design"),
    Command(trLater("Vorlesen & Spracheingabe"), "settings/voice", trLater("Einstellungen"), "stimme tts sprechen diktieren"),
    Command(trLater("Darstellung"), "settings/appearance", trLater("Einstellungen"), "dunkel hell farbe theme schriftgröße kontrast"),
    Command(trLater("Sicherung & Umzug"), "settings/backup", trLater("Einstellungen"), "backup export import umzug"),
    Command(trLater("Papierkorb"), "settings/trash", trLater("Einstellungen"), "gelöscht wiederherstellen"),
    Command(trLater("Arbeitsspeicher"), "settings/ram", trLater("Einstellungen"), "speicher ram arbeitsspeicher langsam messen freigeben"),
    Command(trLater("Lokale Ordner"), "settings/folders", trLater("Einstellungen"), "ordner pc dateien agent explorer freigeben windows"),
    Command(trLater("Synchronisation"), "settings/sync", trLater("Einstellungen"), "sync cloud onedrive google drive nextcloud zweiter pc laptop mehrere rechner datenordner"),
    Command(trLater("Speicherorte"), "settings/storage", trLater("Einstellungen"), "ablageort vault obsidian google drive onedrive synchronisieren ordner"),
    Command(trLater("Inhalte zum Nachladen"), "settings/downloads", trLater("Einstellungen"), "nachladen herunterladen download modelle wissenspaket lehramt paket befehlsmodell erkennungsmodell löschen"),
    Command(trLater("Updates"), "settings/updates", trLater("Einstellungen"), "update aktualisieren neue version installieren netzlaufwerk"),
    Command(trLater("Verbindungen"), "settings/connections", trLater("Einstellungen"), "nextcloud cloudstore webdav mcp server werkzeuge anbinden integration token oauth"),
)

/** All words of the query must occur in title, group or keywords; title matches come first. */
fun filterCommands(commands: List<Command>, query: String): List<Command> {
    val words = query.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
    if (words.isEmpty()) return commands
    return commands
        .filter { cmd -> val hay = "${cmd.title} ${tr(cmd.title)} ${cmd.group} ${tr(cmd.group)} ${cmd.keywords}".lowercase(); words.all { it in hay } }
        .sortedBy { cmd -> val t = tr(cmd.title).lowercase(); if (t.startsWith(words.first()) || cmd.title.lowercase().startsWith(words.first())) 0 else if (words.first() in t || words.first() in cmd.title.lowercase()) 1 else 2 }
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
    // Befehle auf dem Gerät (AP-71): what the command model makes of the typed words – shown as the first entry when it
    // is sure, carried out by Lora on the device (settings and risky actions still ask first).
    val targets = remember { io.github.thepytales.llmhub.data.AppMap.build(io.github.thepytales.llmhub.data.TemplateLibrary.all(c.templates.items.value), IS_ANDROID) }
    val command = remember(query) {
        val q = query.trim()
        if (q.length < 3) null else c.commandModel.recognize(q, io.github.thepytales.llmhub.agent.ActionArea.of(LoraHelper.route.value)?.key ?: "app")
            ?.takeIf { it.sure && !it.free }
            ?.let { io.github.thepytales.llmhub.agent.LoraCommands.describe(it.label, q, c.settings.current, targets) }
    }
    // Lora-Verlauf (AP-75): Pfeil hoch holt die letzten Wünsche, angeheftete Befehle stehen unter ihrem Namen.
    val historyVersion by io.github.thepytales.llmhub.data.LoraHistory.version.collectAsStateWithLifecycle()
    val history = remember(historyVersion) { if (c.settings.current.loraHistory) io.github.thepytales.llmhub.data.LoraHistory.recent(c) else emptyList() }
    val pinned = remember(query, historyVersion) { io.github.thepytales.llmhub.data.LoraHistory.search(c, query).take(8) }
    var historyAt by remember { mutableStateOf(-1) }
    fun browse(up: Boolean): Boolean {
        if (historyAt < 0 && query.isNotEmpty()) return false
        val next = io.github.thepytales.llmhub.data.LoraHistory.browse(history.size, historyAt, up) ?: return historyAt >= 0
        historyAt = next
        query = if (next < 0) "" else history[next].text
        return true
    }
    fun first() {
        // A wish fetched with Pfeil hoch goes to Lora again („Nochmal“), a pinned command by its name too.
        if (historyAt >= 0) { LoraHelper.open(query.trim()); onDismiss(); return }
        pinned.firstOrNull()?.takeIf { query.isNotBlank() && commands.none { tr(it.title).equals(query.trim(), true) } }?.let { LoraHelper.open(it.text); onDismiss(); return }
        commands.firstOrNull()?.let { go(it.route); onDismiss() }
            ?: command?.let { LoraHelper.open(query.trim()); onDismiss() }
            ?: chats.firstOrNull()?.let { onOpenChat(it.id); onDismiss() }
    }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(20.dp), tonalElevation = 6.dp, modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it; historyAt = -1 },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    placeholder = { Text(tr("Bereich, Einstellung, Chat – oder sag, was du tun willst …")) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { first() }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus).onPreviewKeyEvent { e ->
                        when {
                            e.type == KeyEventType.KeyDown && e.key == Key.DirectionUp -> browse(up = true)
                            e.type == KeyEventType.KeyDown && e.key == Key.DirectionDown -> browse(up = false)
                            else -> false
                        }
                    },
                )
                if (historyAt >= 0) {
                    Text(
                        tr("Zuletzt {0} von {1} · Enter: nochmal an Lora · Pfeil runter: weiter", historyAt + 1, history.size),
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 12.dp, top = 4.dp),
                    )
                }
                LazyColumn(Modifier.heightIn(max = 420.dp).padding(top = 6.dp)) {
                    command?.let { line ->
                        item(key = "command") { PaletteRow("⚡ $line", tr("Befehl")) { LoraHelper.open(query.trim()); onDismiss() } }
                    }
                    if (pinned.isNotEmpty()) {
                        item(key = "g_pinned") { PaletteHeading(tr("Eigene Befehle")) }
                        pinned.forEach { e ->
                            item(key = "pin_" + e.text) { PaletteRow(e.name!!, tr("Lora")) { LoraHelper.open(e.text); onDismiss() } }
                        }
                    }
                    // Lora fragen (AP-07): the typed wish goes to Lora, who finds the place or hands it to a chat.
                    item(key = "lora") {
                        PaletteRow(
                            if (query.isBlank()) tr("Lora fragen") else tr("Lora fragen") + ": „" + query.trim() + "“",
                            "Lora",
                        ) { LoraHelper.open(query.trim()); onDismiss() }
                    }
                    // Touren (AP-41): „Tour …“, „Zeig mir, wie …“ – or the whole list when nothing is typed yet.
                    val tours = LoraTours.available(IS_ANDROID).filter { t ->
                        val q = io.github.thepytales.llmhub.data.AppMap.norm(query)
                        q.isBlank() || "tour".startsWith(q.take(4)) || q.contains("zeig") || io.github.thepytales.llmhub.data.AppMap.norm(tr(t.title)).contains(q) && q.length >= 3
                    }.takeIf { query.isNotBlank() }.orEmpty()
                    if (tours.isNotEmpty()) {
                        item(key = "g_tours") { PaletteHeading(tr("Touren")) }
                        tours.forEach { t ->
                            item(key = "tour_" + t.id) { PaletteRow(tr("Tour: {0}", tr(t.title)), tr("Lora zeigt es")) { onDismiss(); LoraTours.start(t, onNavigate) } }
                        }
                    }
                    var lastGroup: String? = null
                    commands.forEach { cmd ->
                        if (cmd.group != lastGroup) {
                            lastGroup = cmd.group
                            item(key = "g_" + cmd.group) { PaletteHeading(cmd.group) }
                        }
                        item(key = "c_" + cmd.route) {
                            PaletteRow(cmd.title, cmd.route.takeIf { it.startsWith("settings/") }?.let { tr("Einstellungen") }) { go(cmd.route); onDismiss() }
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
        tr("Strg+K") to tr("Befehlspalette: Bereiche, Einstellungen, Chats"),
        tr("Strg+N") to tr("Neuer Chat"),
        tr("Strg+B") to tr("Seitenleiste ein/aus"),
        tr("Strg+L") to tr("Lora öffnen und schließen"),
        tr("Strg+Umschalt+L") to tr("Literatur"),
        tr("Strg+D") to tr("Dateien"),
        tr("Strg+T") to tr("Transkription"),
        tr("Strg+,") to tr("Einstellungen"),
        tr("Strg+/") to tr("Diese Übersicht"),
        tr("Strg++ / Strg+−") to tr("Zoom (Strg+0: 100 %, auch Strg+Mausrad)"),
        tr("Enter") to tr("Senden (Umschalt+Enter: neue Zeile)"),
        "/" to tr("Befehle im Eingabefeld"),
        "@" to tr("Dateien, Literatur und Dokumente erwähnen"),
        tr("Esc") to tr("Menü schließen bzw. zurück"),
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
