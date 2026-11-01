package io.github.thepytales.llmhub.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.agent.ChatModes
import io.github.thepytales.llmhub.agent.GameTools
import io.github.thepytales.llmhub.data.LearningGame
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Lernspiele (AP-17): the collection of the games the agent built – search, play (walled off, see [GamePlayer]), earlier
 * versions, change in the chat, export as HTML file or ZIP for OPAL/Moodle. A new game starts a chat in the Lernspiel
 * mode.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GamesScreen(c: AppContainer, onBack: () -> Unit) {
    val games by c.games.items.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    val shown = remember(games, query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) games else games.filter { (it.title + " " + it.kind + " " + it.description).lowercase().contains(q) }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { BackButton(onBack) },
                title = { Text(tr("Lernspiele")) },
                actions = { TextButton(onClick = { newGameChat() }) { Icon(Icons.Outlined.Add, null); Spacer(Modifier.size(4.dp)); Text(tr("Neues Lernspiel")) } },
            )
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.TopCenter) {
            if (games.isEmpty()) {
                Column(Modifier.widthIn(max = 520.dp).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    LoraFace(Modifier.size(72.dp), label = "Lora", director = rememberLoraDirector(listOf(LoraAction.PUZZLE, LoraAction.CARDS), LoraAction.PUZZLE))
                    Text(tr("Noch keine Lernspiele"), style = MaterialTheme.typography.titleMedium)
                    Text(
                        tr("Beschreibe im Chat ein Spiel – z. B. „Mach ein Zuordnungsspiel zu Kapitel 3 des angehängten Skripts“. Lora baut daraus ein Browserspiel, das du hier testest und als HTML oder ZIP in OPAL oder Moodle hochlädst. Die Spiele laufen abgeschottet, ohne Internet."),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                    )
                    Button(onClick = { newGameChat() }) { Text(tr("Lernspiel bauen lassen")) }
                }
            } else {
                LazyColumn(
                    Modifier.widthIn(max = 860.dp).fillMaxWidth(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item {
                        OutlinedTextField(
                            value = query, onValueChange = { query = it }, singleLine = true,
                            leadingIcon = { Icon(Icons.Outlined.Search, null) }, placeholder = { Text(tr("Lernspiele durchsuchen")) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    items(shown, key = { it.id }) { g -> GameRow(c, g) }
                }
            }
        }
    }
}

/** Starts a new chat in the Lernspiel mode (optionally with [text] in the input). */
internal fun newGameChat(text: String = "", chatId: String? = null) {
    ChatHandoff.pending.value = ChatHandoff.Content(text, emptyList(), mode = ChatModes.GAMES.id.takeIf { chatId == null }, chatId = chatId)
    ChatHandoff.open.value = chatId.orEmpty()
}

private val DATE = SimpleDateFormat("dd.MM.yyyy, HH:mm", Locale.GERMANY)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GameRow(c: AppContainer, g: LearningGame) {
    var play by remember { mutableStateOf<Int?>(null) }
    var versions by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    Surface(shape = MaterialTheme.shapes.medium, color = LocalChatLook.current.card, border = boxBorder()) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconTile(Icons.Outlined.SportsEsports, size = 36.dp)
                Spacer(Modifier.size(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(g.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(g.kind.ifBlank { null }, tr("Version {0}", g.versions), DATE.format(Date(g.updatedAt))).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (g.description.isNotBlank()) Text(g.description, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(onClick = { play = g.versions }) { Icon(Icons.Outlined.PlayArrow, null, Modifier.size(18.dp)); Spacer(Modifier.size(4.dp)); Text(tr("Spielen")) }
                GameExportButtons(c, g)
                OutlinedButton(onClick = {
                    newGameChat(tr("Ändere das Lernspiel „{0}“ ({1} {2}): ", g.title, GameTools.ID_LABEL, g.id), g.chatId?.takeIf { id -> c.chats.conversations.value.any { it.id == id } })
                }) { Icon(Icons.Outlined.Edit, null, Modifier.size(18.dp)); Spacer(Modifier.size(4.dp)); Text(tr("Im Chat ändern")) }
                if (g.versions > 1) Box {
                    TextButton(onClick = { versions = true }) { Icon(Icons.Outlined.History, null, Modifier.size(18.dp)); Spacer(Modifier.size(4.dp)); Text(tr("Versionen")) }
                    DropdownMenu(expanded = versions, onDismissRequest = { versions = false }) {
                        (g.versions downTo 1).forEach { v ->
                            DropdownMenuItem(text = { Text(tr("Version") + " $v" + if (v == g.versions) " (" + tr("aktuell") + ")" else "") }, onClick = { versions = false; play = v })
                        }
                        (g.versions - 1 downTo 1).firstOrNull()?.let { prev ->
                            DropdownMenuItem(
                                text = { Text(tr("Version {0} wiederherstellen", prev)) },
                                onClick = {
                                    versions = false
                                    c.games.html(g.id, prev)?.let { c.games.update(g.id, it) }
                                    Notices.info(tr("Version {0} ist wieder die aktuelle (als neue Version gespeichert).", prev))
                                },
                            )
                        }
                    }
                }
                TextButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.Delete, null, Modifier.size(18.dp)); Spacer(Modifier.size(4.dp)); Text(tr("Löschen")) }
            }
        }
    }
    play?.let { v -> GamePlayer(c, g, v) { play = null } }
    if (confirmDelete) {
        ConfirmDialog(tr("Lernspiel löschen?"), tr("„{0}“ mit allen Versionen wird gelöscht.", g.title), tr("Löschen"), onConfirm = { c.games.delete(g.id) }, onDismiss = { confirmDelete = false })
    }
}

/** „HTML-Datei“ and „ZIP für OPAL“: the game walled off, saved where the user chooses. */
@Composable
internal fun GameExportButtons(c: AppContainer, g: LearningGame) {
    val saveHtml = rememberSaveAs("text/html")
    val saveZip = rememberSaveAs("application/zip")
    var menu by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { menu = true }) { Icon(Icons.Outlined.FileDownload, null, Modifier.size(18.dp)); Spacer(Modifier.size(4.dp)); Text(tr("Exportieren")) }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text(tr("ZIP für OPAL/Moodle")) }, onClick = {
                menu = false
                c.games.writeZip(g.id, File(File(c.appContext.cacheDir, "lernspiele"), c.games.fileName(g, "zip")))?.let(saveZip)
            })
            DropdownMenuItem(text = { Text(tr("HTML-Datei")) }, onClick = {
                menu = false
                c.games.writePlayable(g.id, File(File(c.appContext.cacheDir, "lernspiele"), c.games.fileName(g, "html")))?.let(saveHtml)
            })
        }
    }
}

/** The card under a chat answer that built or changed a game: play it, export it. */
@Composable
internal fun GameCard(id: String) {
    val c = io.github.thepytales.llmhub.App.container
    val games by c.games.items.collectAsStateWithLifecycle()
    val g = games.firstOrNull { it.id == id } ?: return
    var play by remember { mutableStateOf(false) }
    Surface(shape = MaterialTheme.shapes.medium, color = LocalChatLook.current.card, border = boxBorder(), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconTile(Icons.Outlined.SportsEsports, size = 32.dp)
                Spacer(Modifier.size(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(g.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(listOfNotNull(g.kind.ifBlank { null }, tr("Version {0}", g.versions), tr("in der Sammlung Lernspiele")).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { play = true }) { Icon(Icons.Outlined.PlayArrow, null, Modifier.size(18.dp)); Spacer(Modifier.size(4.dp)); Text(tr("Spielen")) }
                GameExportButtons(c, g)
            }
        }
    }
    if (play) GamePlayer(c, g, g.versions) { play = false }
}
