package io.github.thepytales.llmhub.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CompareArrows
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.data.Conversation
import io.github.thepytales.llmhub.data.Folder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class SearchHit(val conv: Conversation, val snippet: String?)

/** Finds chats whose title or messages contain [query]; with a short excerpt around the match. */
private fun search(list: List<Conversation>, query: String): List<SearchHit> {
    val q = query.trim()
    if (q.isEmpty()) return emptyList()
    return list.mapNotNull { conv ->
        val inTitle = conv.title.contains(q, ignoreCase = true)
        val msg = conv.messages.firstOrNull { (it.role == "user" || it.role == "assistant") && it.content.contains(q, ignoreCase = true) }
        if (!inTitle && msg == null) return@mapNotNull null
        val snippet = msg?.content?.let { text ->
            val i = text.indexOf(q, ignoreCase = true)
            val start = (i - 40).coerceAtLeast(0)
            (if (start > 0) "…" else "") + text.substring(start, minOf(text.length, i + q.length + 80)).replace(Regex("\\s+"), " ")
        }
        SearchHit(conv, snippet)
    }
}

/** ChatGPT-like side menu: search, features, folders, pinned and recent chats. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppDrawer(
    c: AppContainer,
    currentChatId: String?,
    onOpenChat: (String) -> Unit,
    onNewChat: () -> Unit,
    onNavigate: (String) -> Unit,
) {
    val conversations by c.chats.conversations.collectAsStateWithLifecycle()
    val folders by c.folders.items.collectAsStateWithLifecycle()
    val runs by c.runs.runs.collectAsStateWithLifecycle()
    val settings by c.settings.settings.collectAsStateWithLifecycle()
    val look = LocalChatLook.current
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var expanded by remember { mutableStateOf(setOf<String>()) }
    var renameChat by remember { mutableStateOf<Conversation?>(null) }
    var moveChat by remember { mutableStateOf<Conversation?>(null) }
    var deleteChat by remember { mutableStateOf<Conversation?>(null) }
    var newFolder by remember { mutableStateOf(false) }
    var renameFolder by remember { mutableStateOf<Folder?>(null) }
    var deleteFolder by remember { mutableStateOf<Folder?>(null) }

    val hits by produceState(emptyList<SearchHit>(), query, conversations) {
        value = withContext(Dispatchers.Default) { search(conversations, query) }
    }

    ModalDrawerSheet(
        drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        drawerShape = RoundedCornerShape(topEnd = 0.dp, bottomEnd = 0.dp),
        modifier = Modifier.width(318.dp),
    ) {
        Column(Modifier.fillMaxHeight().statusBarsPadding().navigationBarsPadding()) {
            // Header
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (searching) {
                    IconButton(onClick = { searching = false; query = "" }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Suche schließen")
                    }
                    TextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Chats durchsuchen") },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                        trailingIcon = {
                            if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Outlined.Close, contentDescription = "Leeren") }
                        },
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    Text(
                        "ADEU LLM",
                        style = MaterialTheme.typography.headlineMedium.copy(fontFamily = look.titleFont),
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { searching = true }) {
                        Icon(Icons.Outlined.Search, contentDescription = "Chats durchsuchen", modifier = Modifier.size(26.dp))
                    }
                }
            }

            LazyColumn(Modifier.weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp)) {
                if (searching) {
                    if (query.isNotBlank() && hits.isEmpty()) {
                        item { Hint("Keine Chats gefunden.") }
                    }
                    items(hits, key = { "s_" + it.conv.id }) { hit ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onOpenChat(hit.conv.id) }
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            Text(hit.conv.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
                            hit.snippet?.let {
                                Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    return@LazyColumn
                }

                val newResults = conversations.count { it.unread && it.taskId != null }
                item { NavRow(Icons.Outlined.Schedule, "Geplante Aufgaben", badge = newResults) { onNavigate("tasks") } }
                item { NavRow(Icons.Outlined.Image, "Bilder") { onNavigate("images") } }
                item { NavRow(Icons.Outlined.Bookmarks, "Vorlagen") { onNavigate("templates") } }
                item { NavRow(Icons.Outlined.AutoAwesome, "Modelle") { onNavigate("models") } }
                item { NavRow(Icons.Outlined.CompareArrows, "Modelle vergleichen") { onNavigate("compare") } }
                item { NavRow(Icons.Outlined.Settings, "Einstellungen") { onNavigate("settings") } }
                item { HorizontalDivider(Modifier.padding(horizontal = 20.dp, vertical = 10.dp), color = MaterialTheme.colorScheme.outlineVariant) }

                item {
                    Row(Modifier.padding(start = 20.dp, end = 8.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        SectionTitle("Ordner", Modifier.weight(1f))
                        IconButton(onClick = { newFolder = true }) { Icon(Icons.Outlined.CreateNewFolder, contentDescription = "Neuer Ordner") }
                    }
                }
                if (folders.isEmpty()) item { Hint("Lege Ordner an, um Chats zu sortieren.") }
                folders.sortedBy { it.name.lowercase() }.forEach { folder ->
                    val inFolder = conversations.filter { it.folderId == folder.id }
                    val open = folder.id in expanded
                    item(key = "f_" + folder.id) {
                        FolderRow(
                            folder = folder,
                            count = inFolder.size,
                            open = open,
                            onToggle = { expanded = if (open) expanded - folder.id else expanded + folder.id },
                            onRename = { renameFolder = folder },
                            onDelete = { deleteFolder = folder },
                        )
                    }
                    if (open) {
                        items(inFolder, key = { "fc_" + it.id }) { conv ->
                            ChatRow(conv, conv.id == currentChatId, indent = true, running = conv.id in runs, onOpen = { onOpenChat(conv.id) },
                                onPin = { c.chats.modify(conv.id) { it.copy(pinned = !it.pinned) } },
                                onMove = { moveChat = conv }, onRename = { renameChat = conv }, onDelete = { deleteChat = conv })
                        }
                    }
                }

                val pinned = conversations.filter { it.pinned }
                if (pinned.isNotEmpty()) {
                    item { SectionTitle("Angeheftet", Modifier.padding(start = 20.dp, top = 18.dp, bottom = 4.dp)) }
                    chatList(pinned, "p_", currentChatId, runs.keys, onOpenChat, c, { moveChat = it }, { renameChat = it }, { deleteChat = it }, showPin = true)
                }

                val recent = conversations.filter { !it.pinned && it.folderId == null }
                item { SectionTitle("Zuletzt", Modifier.padding(start = 20.dp, top = 18.dp, bottom = 4.dp)) }
                if (recent.isEmpty()) item { Hint("Noch keine Chats.") }
                chatList(recent, "r_", currentChatId, runs.keys, onOpenChat, c, { moveChat = it }, { renameChat = it }, { deleteChat = it }, showPin = false)
            }

            // Bottom like the Claude app: avatar (settings) and a black "Neuer Chat" button.
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = look.brand,
                    contentColor = Color.White,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .clickable { onNavigate("settings") },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        val initial = settings.userName.trim().firstOrNull()?.uppercaseChar()
                        if (initial != null) Text(initial.toString(), style = MaterialTheme.typography.titleMedium)
                        else Icon(Icons.Outlined.Person, contentDescription = "Einstellungen")
                    }
                }
                Spacer(Modifier.weight(1f))
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.onSurface,
                    contentColor = MaterialTheme.colorScheme.surface,
                    shadowElevation = 4.dp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable(onClick = onNewChat),
                ) {
                    Row(Modifier.padding(horizontal = 22.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Add, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text("Neuer Chat", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }

    renameChat?.let { conv ->
        TextInputDialog("Chat umbenennen", conv.title, "Titel", onConfirm = { t -> c.chats.modify(conv.id) { it.copy(title = t) } }, onDismiss = { renameChat = null })
    }
    moveChat?.let { conv ->
        FolderPickerDialog(c, conv.folderId, onPick = { f -> c.chats.modify(conv.id) { it.copy(folderId = f) } }, onDismiss = { moveChat = null })
    }
    deleteChat?.let { conv ->
        ConfirmDialog("Chat löschen?", "„${conv.title}“ wird dauerhaft gelöscht.", "Löschen", onConfirm = { c.chats.delete(conv.id) }, onDismiss = { deleteChat = null })
    }
    if (newFolder) {
        TextInputDialog("Neuer Ordner", "", "Name", confirm = "Erstellen", onConfirm = { c.folders.upsert(Folder(name = it)) }, onDismiss = { newFolder = false })
    }
    renameFolder?.let { f ->
        TextInputDialog("Ordner umbenennen", f.name, "Name", onConfirm = { n -> c.folders.update(f.id) { it.copy(name = n) } }, onDismiss = { renameFolder = null })
    }
    deleteFolder?.let { f ->
        ConfirmDialog(
            "Ordner löschen?", "Die Chats in „${f.name}“ bleiben erhalten und erscheinen wieder unter „Zuletzt“.", "Löschen",
            onConfirm = {
                c.chats.clearFolder(f.id)
                c.folders.delete(f.id)
            },
            onDismiss = { deleteFolder = null },
        )
    }
}

private fun LazyListScope.chatList(
    list: List<Conversation>,
    prefix: String,
    currentChatId: String?,
    running: Set<String>,
    onOpenChat: (String) -> Unit,
    c: AppContainer,
    onMove: (Conversation) -> Unit,
    onRename: (Conversation) -> Unit,
    onDelete: (Conversation) -> Unit,
    showPin: Boolean,
) {
    items(list, key = { prefix + it.id }) { conv ->
        ChatRow(
            conv, conv.id == currentChatId, indent = false, showIcon = showPin, running = conv.id in running,
            onOpen = { onOpenChat(conv.id) },
            onPin = { c.chats.modify(conv.id) { it.copy(pinned = !it.pinned) } },
            onMove = { onMove(conv) }, onRename = { onRename(conv) }, onDelete = { onDelete(conv) },
        )
    }
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
    )
}

@Composable
private fun NavRow(icon: ImageVector, label: String, badge: Int = 0, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(26.dp))
        Spacer(Modifier.size(18.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp), modifier = Modifier.weight(1f))
        if (badge > 0) {
            Surface(shape = RoundedCornerShape(50), color = LocalChatLook.current.brand, contentColor = Color.White) {
                Text(
                    if (badge == 1) "1 neu" else "$badge neu",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderRow(folder: Folder, count: Int, open: Boolean, onToggle: () -> Unit, onRename: () -> Unit, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .clip(RoundedCornerShape(12.dp))
                .combinedClickable(onClick = onToggle, onLongClick = { menu = true })
                .onRightClick { menu = true }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Folder, contentDescription = null)
            Spacer(Modifier.size(16.dp))
            Text(folder.name, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text("$count", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Icon(if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, contentDescription = null, modifier = Modifier.padding(start = 4.dp).size(18.dp))
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text("Umbenennen") }, leadingIcon = { Icon(Icons.Outlined.Edit, null) }, onClick = { menu = false; onRename() })
            DropdownMenuItem(text = { Text("Löschen") }, leadingIcon = { Icon(Icons.Outlined.Delete, null) }, onClick = { menu = false; onDelete() })
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatRow(
    conv: Conversation,
    selected: Boolean,
    indent: Boolean,
    showIcon: Boolean = false,
    running: Boolean = false,
    onOpen: () -> Unit,
    onPin: () -> Unit,
    onMove: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (selected) MaterialTheme.colorScheme.surfaceContainerHighest else Color.Transparent)
                .combinedClickable(onClick = onOpen, onLongClick = { menu = true })
                .onRightClick { menu = true }
                .padding(start = if (indent) 44.dp else 12.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showIcon) {
                Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.size(14.dp))
            }
            Text(
                conv.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected || conv.unread) FontWeight.SemiBold else null,
                modifier = Modifier.weight(1f),
            )
            when {
                // Answer is still being written (also in the background).
                running -> CircularProgressIndicator(Modifier.padding(start = 8.dp).size(14.dp), strokeWidth = 2.dp)
                conv.unread -> Box(
                    Modifier
                        .padding(start = 8.dp)
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(LocalChatLook.current.brand)
                )
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text(if (conv.pinned) "Lösen" else "Anheften") },
                leadingIcon = { Icon(Icons.Outlined.PushPin, null) },
                onClick = { menu = false; onPin() },
            )
            DropdownMenuItem(text = { Text("In Ordner verschieben") }, leadingIcon = { Icon(Icons.Outlined.DriveFileMove, null) }, onClick = { menu = false; onMove() })
            DropdownMenuItem(text = { Text("Umbenennen") }, leadingIcon = { Icon(Icons.Outlined.Edit, null) }, onClick = { menu = false; onRename() })
            DropdownMenuItem(text = { Text("Löschen") }, leadingIcon = { Icon(Icons.Outlined.Delete, null) }, onClick = { menu = false; onDelete() })
        }
    }
}
