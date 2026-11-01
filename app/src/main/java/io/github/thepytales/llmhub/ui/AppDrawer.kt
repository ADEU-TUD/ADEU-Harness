package io.github.thepytales.llmhub.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.platform.LocalContext
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
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.FolderSpecial
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.AutoAwesomeMosaic
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.PictureAsPdf
import io.github.thepytales.llmhub.data.withTool
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
import androidx.compose.ui.graphics.Brush
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
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

private data class SearchHit(val conv: Conversation, val snippet: String?)

/** Day groups of the chat list, like in the ChatGPT and Claude apps. */
private fun dayGroup(time: Long, today: LocalDate): String {
    val day = Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()).toLocalDate()
    val days = ChronoUnit.DAYS.between(day, today)
    return when {
        days <= 0 -> tr("Heute")
        days == 1L -> tr("Gestern")
        days < 7 -> tr("Letzte 7 Tage")
        days < 30 -> tr("Letzte 30 Tage")
        else -> tr("Älter")
    }
}

/**
 * Side menu: the main features on top, rarely used ones under "Mehr", then projects, pinned chats and the chats by
 * day. At the bottom the profile (settings) and "Neuer Chat". Shared by Android (drawer) and Windows (side panel).
 */
@Composable
fun AppDrawer(
    c: AppContainer,
    currentChatId: String?,
    onOpenChat: (String) -> Unit,
    onNewChat: () -> Unit,
    onNavigate: (String) -> Unit,
    width: androidx.compose.ui.unit.Dp = 318.dp,
) {
    val allConversations by c.chats.conversations.collectAsStateWithLifecycle()
    // Archived chats stay out of the list (section „Archiv“ at the end).
    val conversations = remember(allConversations) { allConversations.filter { !it.archived } }
    val archived = remember(allConversations) { allConversations.filter { it.archived } }
    val chatDrafts by c.chatDrafts.items.collectAsStateWithLifecycle()
    val drafts = remember(chatDrafts) { chatDrafts.map { it.chatId }.toSet() }
    /** Chats chosen for an action on several at once (long press › Auswählen). */
    var selection by remember { mutableStateOf(setOf<String>()) }
    var showArchive by rememberSaveable { mutableStateOf(false) }
    var moveSelection by remember { mutableStateOf(false) }
    val folders by c.folders.items.collectAsStateWithLifecycle()
    val runs by c.runs.runs.collectAsStateWithLifecycle()
    val settings by c.settings.settings.collectAsStateWithLifecycle()
    val look = LocalChatLook.current
    val transcribing by c.transcription.activeJobs.collectAsStateWithLifecycle()
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var expanded by remember { mutableStateOf(setOf<String>()) }
    var renameChat by remember { mutableStateOf<Conversation?>(null) }
    var moveChat by remember { mutableStateOf<Conversation?>(null) }
    var deleteChat by remember { mutableStateOf<Conversation?>(null) }
    var newFolder by remember { mutableStateOf(false) }
    var renameFolder by remember { mutableStateOf<Folder?>(null) }
    var deleteFolder by remember { mutableStateOf<Folder?>(null) }

    // Chat search with filters (AP-16): period, project, model, attachments, files of the AI, archive.
    var filter by remember { mutableStateOf(io.github.thepytales.llmhub.data.ChatFilter()) }
    val hits by produceState(emptyList<SearchHit>(), query, conversations, filter) {
        value = withContext(Dispatchers.Default) {
            io.github.thepytales.llmhub.data.ChatFilter.search(allConversations, query, filter).map { (conv, snippet) -> SearchHit(conv, snippet) }
        }
    }

    ModalDrawerSheet(
        drawerContainerColor = MaterialTheme.colorScheme.background,
        drawerShape = RoundedCornerShape(topEnd = 0.dp, bottomEnd = 0.dp),
        modifier = Modifier.width(width),
    ) {
        Column(Modifier.fillMaxHeight().statusBarsPadding().navigationBarsPadding()) {
            // Header
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (selection.isNotEmpty()) {
                    IconButton(onClick = { selection = emptySet() }) { Icon(Icons.Outlined.Close, contentDescription = tr("Auswahl beenden")) }
                    Text(tr("{0} ausgewählt", selection.size), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    IconButton(onClick = {
                        val ids = selection
                        val toArchive = allConversations.none { it.id in ids && !it.archived }.not()
                        ids.forEach { id -> c.chats.modify(id) { it.copy(archived = toArchive) } }
                        selection = emptySet()
                        Notices.undo(if (toArchive) tr("{0} Chats archiviert", ids.size) else tr("{0} Chats aus dem Archiv geholt", ids.size), restore = { ids.forEach { id -> c.chats.modify(id) { it.copy(archived = !toArchive) } } })
                    }) { Icon(Icons.Outlined.Archive, contentDescription = tr("Archivieren")) }
                    IconButton(onClick = { moveSelection = true }) { Icon(Icons.Outlined.DriveFileMove, contentDescription = tr("In Projekt verschieben")) }
                    IconButton(onClick = {
                        val ids = selection
                        ids.forEach { c.chats.delete(it) }
                        selection = emptySet()
                        Notices.undo(tr("{0} Chats gelöscht", ids.size), restore = { ids.forEach { c.chats.restore(it) } })
                    }) { Icon(Icons.Outlined.Delete, contentDescription = tr("Löschen")) }
                } else if (searching) {
                    IconButton(onClick = { searching = false; query = "" }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("Suche schließen"))
                    }
                    TextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text(tr("Chats durchsuchen")) },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                        trailingIcon = {
                            if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Outlined.Close, contentDescription = tr("Leeren")) }
                        },
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    AppLogo(Modifier.size(38.dp))
                    Spacer(Modifier.size(11.dp))
                    Text(
                        io.github.thepytales.llmhub.Brand.NAME,
                        style = MaterialTheme.typography.headlineSmall.copy(fontFamily = look.titleFont),
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { searching = true }) {
                        Icon(Icons.Outlined.Search, contentDescription = tr("Chats durchsuchen"), modifier = Modifier.size(26.dp))
                    }
                }
            }

            if (searching) ChatFilterRow(filter, folders, allConversations) { filter = it }
            LazyColumn(Modifier.weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp)) {
                if (searching) {
                    if ((query.isNotBlank() || filter.active) && hits.isEmpty()) {
                        item { Hint(tr("Keine Chats gefunden.")) }
                    }
                    if (query.isBlank() && !filter.active) item { Hint(tr("Suchwort eingeben oder oben filtern – nach Zeitraum, Projekt, Modell, Anhängen oder erstellten Dateien.")) }
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
                            Text(
                                listOfNotNull(
                                    java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(java.util.Date(hit.conv.updatedAt)),
                                    hit.conv.folderId?.let { id -> folders.firstOrNull { it.id == id }?.name },
                                    if (hit.conv.archived) tr("Archiv") else null,
                                ).joinToString(" · "),
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline,
                            )
                            hit.snippet?.let {
                                Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    return@LazyColumn
                }

                // AP-02: „Neuer Chat“ and up to four areas on top, everything else (and switched-on tools) under „Mehr“.
                // Right click or long press moves an area up or down; the folded state of „Mehr“ is kept.
                val newResults = conversations.count { it.unread && it.taskId != null }
                val (topAreas, moreAreas) = drawerLayout(settings.drawerTop, settings.enabledToolIds, settings.drawerHiddenIds)
                fun badgeOf(a: DrawerArea) = when (a) {
                    DrawerArea.TASKS -> newResults
                    DrawerArea.TRANSCRIPTS -> transcribing
                    else -> 0
                }
                /** Tools are switched off, other areas hidden („Erstellen“ and „Dateien“ always stay). */
                fun hideAction(a: DrawerArea): (() -> Unit)? = when {
                    a.tool != null -> { { c.settings.update { it.withTool(a.tool, false) } } }
                    a in DrawerArea.ALWAYS -> null
                    else -> { {
                        c.settings.update { it.copy(drawerHidden = hideDrawerArea(it.drawerHidden, a, true)) }
                        Notices.undo("„${tr(a.label)}“ " + tr("ausgeblendet – über Strg+K und Lora weiter erreichbar"), restore = {
                            c.settings.update { it.copy(drawerHidden = hideDrawerArea(it.drawerHidden, a, false)) }
                        })
                    } }
                }
                fun move(a: DrawerArea, toTop: Boolean) {
                    val next = moveDrawerArea(settings.drawerTop, a, toTop)
                    if (next == null) Notices.info(tr("Oben ist Platz für") + " $MAX_TOP_AREAS " + tr("Bereiche – zuerst einen unter „Mehr“ schieben."))
                    else c.settings.update { it.copy(drawerTop = next) }
                }
                item(key = "nav_new") { NavRow(Icons.Outlined.EditNote, tr("Neuer Chat"), accent = true, onClick = onNewChat) }
                items(topAreas, key = { "nav_" + it.id }) { a ->
                    AreaRow(a, onTop = true, badge = badgeOf(a), onOpen = { onNavigate(a.route) }, onMove = { move(a, false) },
                        onHide = hideAction(a))
                }
                if (moreAreas.isNotEmpty()) {
                    item(key = "nav_more") {
                        val more = moreAreas.sumOf { badgeOf(it) }
                        NavRow(
                            if (settings.drawerMoreOpen) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, tr("Mehr"),
                            badge = if (settings.drawerMoreOpen) 0 else more, muted = true,
                        ) { c.settings.update { it.copy(drawerMoreOpen = !it.drawerMoreOpen) } }
                    }
                    if (settings.drawerMoreOpen) {
                        items(moreAreas, key = { "nav_" + it.id }) { a ->
                            AreaRow(a, onTop = false, badge = badgeOf(a), onOpen = { onNavigate(a.route) }, onMove = { move(a, true) },
                                onHide = hideAction(a))
                        }
                    }
                }
                item { HorizontalDivider(Modifier.padding(horizontal = 20.dp, vertical = 10.dp), color = MaterialTheme.colorScheme.outlineVariant) }

                item {
                    Row(Modifier.padding(start = 20.dp, end = 8.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        SectionTitle(tr("Projekte"), Modifier.weight(1f))
                        IconButton(onClick = { newFolder = true }) { Icon(Icons.Outlined.CreateNewFolder, contentDescription = tr("Neues Projekt")) }
                    }
                }
                if (folders.isEmpty()) {
                    item {
                        Hint(tr("Ein Projekt bündelt Chats zu einem Thema – mit gemeinsamen Anweisungen und Dateien, z.B. „Seminar Allgemeine Didaktik“."))
                    }
                }
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
                            onNewChat = { NewChatFolder.id = folder.id; onNewChat() },
                        )
                    }
                    if (open) {
                        if (inFolder.isEmpty()) item(key = "fe_" + folder.id) { Hint(tr("Noch keine Chats in diesem Projekt."), indent = true) }
                        items(inFolder, key = { "fc_" + it.id }) { conv ->
                            ChatRow(conv, conv.id == currentChatId, indent = true, running = conv.id in runs,
                                draft = conv.id in drafts, selecting = selection.isNotEmpty(), checked = conv.id in selection,
                                onSelect = { selection = if (conv.id in selection) selection - conv.id else selection + conv.id },
                                onArchive = { c.chats.modify(conv.id) { it.copy(archived = !it.archived) } },
                                onOpen = { if (selection.isNotEmpty()) selection = if (conv.id in selection) selection - conv.id else selection + conv.id else onOpenChat(conv.id) },
                                onPin = { c.chats.modify(conv.id) { it.copy(pinned = !it.pinned) } },
                                onMove = { moveChat = conv }, onRename = { renameChat = conv }, onDelete = { deleteChat = conv })
                        }
                    }
                }

                val pinned = conversations.filter { it.pinned }
                if (pinned.isNotEmpty()) {
                    item { SectionTitle(tr("Angeheftet"), Modifier.padding(start = 20.dp, top = 18.dp, bottom = 4.dp)) }
                    chatList(pinned, "p_", currentChatId, runs.keys, onOpenChat, c, { moveChat = it }, { renameChat = it }, { deleteChat = it }, showPin = true,
                        drafts = drafts, selection = selection, onSelection = { selection = it })
                }

                val recent = conversations.filter { !it.pinned && it.folderId == null }
                if (recent.isEmpty()) {
                    item { SectionTitle(tr("Chats"), Modifier.padding(start = 20.dp, top = 18.dp, bottom = 4.dp)) }
                    item { Hint(tr("Noch keine Chats. Starte unten mit „Neuer Chat“ oder wähle eine Vorlage.")) }
                }
                val today = LocalDate.now()
                recent.groupBy { dayGroup(it.updatedAt, today) }.forEach { (group, list) ->
                    item(key = "g_$group") { SectionTitle(group, Modifier.padding(start = 20.dp, top = 18.dp, bottom = 4.dp)) }
                    chatList(list, "r_", currentChatId, runs.keys, onOpenChat, c, { moveChat = it }, { renameChat = it }, { deleteChat = it }, showPin = false,
                        drafts = drafts, selection = selection, onSelection = { selection = it })
                }
                if (archived.isNotEmpty()) {
                    item(key = "archive") {
                        NavRow(Icons.Outlined.Archive, tr("Archiv") + " (${archived.size})", muted = true) { showArchive = !showArchive }
                    }
                    if (showArchive) {
                        chatList(archived.sortedByDescending { it.updatedAt }, "a_", currentChatId, runs.keys, onOpenChat, c, { moveChat = it }, { renameChat = it }, { deleteChat = it }, showPin = false,
                            drafts = drafts, selection = selection, onSelection = { selection = it })
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            // Bottom: who is signed in (opens the settings); „Neuer Chat“ is the first entry on top (AP-02).
            Row(Modifier.fillMaxWidth().padding(start = 6.dp, end = 12.dp, top = 10.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onNavigate("settings") }
                        .padding(horizontal = 6.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(shape = CircleShape, color = look.brand, contentColor = Color.White, modifier = Modifier.size(36.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            val initial = settings.userName.trim().firstOrNull()?.uppercaseChar()
                            if (initial != null) Text(initial.toString(), style = MaterialTheme.typography.titleMedium)
                            else Icon(Icons.Outlined.Settings, contentDescription = null, modifier = Modifier.size(20.dp))
                        }
                    }
                    Spacer(Modifier.size(8.dp))
                    val name = settings.userName.trim()
                    Column(Modifier.weight(1f)) {
                        if (name.isNotEmpty()) Text(name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            tr("Einstellungen"),
                            style = if (name.isEmpty()) MaterialTheme.typography.bodySmall else MaterialTheme.typography.labelSmall,
                            color = if (name.isEmpty()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
            }
        }
    }

    renameChat?.let { conv ->
        TextInputDialog(tr("Chat umbenennen"), conv.title, tr("Titel"), onConfirm = { t -> c.chats.modify(conv.id) { it.copy(title = t) } }, onDismiss = { renameChat = null })
    }
    moveChat?.let { conv ->
        FolderPickerDialog(c, conv.folderId, onPick = { f -> c.chats.modify(conv.id) { it.copy(folderId = f) } }, onDismiss = { moveChat = null })
    }
    deleteChat?.let { conv ->
        ConfirmDialog(tr("Chat löschen?"), tr("„{0}“ kommt in den Papierkorb (30 Tage, Einstellungen › Daten).", conv.title), tr("Löschen"), onConfirm = {
            c.chats.delete(conv.id)
            Notices.undo(tr("Chat gelöscht"), restore = { c.chats.restore(conv.id) })
        }, onDismiss = { deleteChat = null })
    }
    if (moveSelection) {
        FolderPickerDialog(c, null, onPick = { folder ->
            selection.forEach { id -> c.chats.modify(id) { it.copy(folderId = folder) } }
            selection = emptySet()
        }, onDismiss = { moveSelection = false })
    }
    if (newFolder) {
        ProjectDialog(c, null, onDismiss = { newFolder = false })
    }
    renameFolder?.let { f -> ProjectDialog(c, f, onDismiss = { renameFolder = null }) }
    deleteFolder?.let { f ->
        ConfirmDialog(
            tr("Projekt löschen?"), tr("Die Chats in „{0}“ bleiben erhalten und erscheinen wieder in der Chat-Liste. Projektdateien werden entfernt.", f.name), tr("Löschen"),
            onConfirm = {
                c.chats.clearFolder(f.id)
                c.folders.delete(f.id)
                deleteProjectKnowledge(f.id)
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
    drafts: Set<String> = emptySet(),
    selection: Set<String> = emptySet(),
    onSelection: (Set<String>) -> Unit = {},
) {
    items(list, key = { prefix + it.id }) { conv ->
        fun toggle() = onSelection(if (conv.id in selection) selection - conv.id else selection + conv.id)
        ChatRow(
            conv, conv.id == currentChatId, indent = false, showIcon = showPin, running = conv.id in running,
            draft = conv.id in drafts, selecting = selection.isNotEmpty(), checked = conv.id in selection,
            onSelect = ::toggle,
            onArchive = { c.chats.modify(conv.id) { it.copy(archived = !it.archived) } },
            onOpen = { if (selection.isNotEmpty()) toggle() else onOpenChat(conv.id) },
            onPin = { c.chats.modify(conv.id) { it.copy(pinned = !it.pinned) } },
            onMove = { onMove(conv) }, onRename = { onRename(conv) }, onDelete = { onDelete(conv) },
        )
    }
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(tr(text), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
}

@Composable
private fun Hint(text: String, indent: Boolean = false) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = if (indent) 52.dp else 20.dp, end = 20.dp, top = 6.dp, bottom = 6.dp),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NavRow(
    icon: ImageVector,
    label: String,
    badge: Int = 0,
    badgeText: String = "neu",
    indent: Boolean = false,
    muted: Boolean = false,
    accent: Boolean = false,
    onMenu: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val color = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .then(if (onMenu != null) Modifier.combinedClickable(onClick = onClick, onLongClick = onMenu).secondaryClick(onMenu) else Modifier.clickable(onClick = onClick))
            .padding(start = if (indent) 24.dp else 12.dp, end = 12.dp, top = if (indent) 10.dp else 12.dp, bottom = if (indent) 10.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = if (accent) LocalChatLook.current.brand else color, modifier = Modifier.size(if (indent) 22.dp else 24.dp))
        Spacer(Modifier.size(18.dp))
        Text(
            tr(label), color = color, style = MaterialTheme.typography.bodyLarge.copy(fontSize = if (indent) 16.sp else 17.sp),
            fontWeight = if (accent) FontWeight.SemiBold else null, modifier = Modifier.weight(1f),
        )
        if (badge > 0) {
            Surface(shape = RoundedCornerShape(50), color = LocalChatLook.current.brand, contentColor = Color.White) {
                Text(
                    if (badge == 1) "1 $badgeText" else "$badge $badgeText",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        }
    }
}

/** Icon of an area of the side menu. */
internal fun DrawerArea.icon(): ImageVector = when (this) {
    DrawerArea.TEMPLATES -> Icons.Outlined.AutoAwesomeMosaic
    DrawerArea.LITERATURE -> Icons.Outlined.MenuBook
    DrawerArea.TRANSCRIPTS -> Icons.Outlined.GraphicEq
    DrawerArea.FILES -> Icons.Outlined.FolderOpen
    DrawerArea.IMAGES -> Icons.Outlined.Image
    DrawerArea.TASKS -> Icons.Outlined.Schedule
    DrawerArea.PDF -> Icons.Outlined.PictureAsPdf
    DrawerArea.VAULT -> Icons.Outlined.Lock
    DrawerArea.GAMES -> Icons.Outlined.SportsEsports
    DrawerArea.MAIL -> Icons.Outlined.Mail
    DrawerArea.VIDEO -> Icons.Outlined.Movie
    DrawerArea.BATCH -> Icons.Outlined.Inventory2
    DrawerArea.PROPOSALS -> Icons.AutoMirrored.Outlined.Assignment
}

/** An area of the side menu; right click or long press: move it on top or under „Mehr“ (tools: also switch off). */
@Composable
private fun AreaRow(area: DrawerArea, onTop: Boolean, badge: Int, onOpen: () -> Unit, onMove: () -> Unit, onHide: (() -> Unit)?) {
    var menu by remember { mutableStateOf(false) }
    Box {
        // Lora flies here and lets the entry glow when she leads to this area (AP-36).
        Spot("drawer.${area.route}") {
            NavRow(
                area.icon(), area.label, badge = badge, badgeText = if (area == DrawerArea.TRANSCRIPTS) tr("läuft") else "neu",
                indent = !onTop, onMenu = { menu = true }, onClick = onOpen,
            )
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text(tr(if (onTop) "Unter „Mehr“ verschieben" else "Nach oben holen")) },
                leadingIcon = { Icon(if (onTop) Icons.Outlined.ExpandMore else Icons.Outlined.ExpandLess, null) },
                onClick = { menu = false; onMove() },
            )
            if (onHide != null) {
                DropdownMenuItem(
                    text = { Text(tr(if (area.tool != null) "Werkzeug ausschalten" else "Ausblenden")) },
                    leadingIcon = { Icon(Icons.Outlined.Close, null) },
                    onClick = { menu = false; onHide() },
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderRow(folder: Folder, count: Int, open: Boolean, onToggle: () -> Unit, onRename: () -> Unit, onDelete: () -> Unit, onNewChat: () -> Unit) {
    val context = LocalContext.current.applicationContext
    var menu by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .clip(RoundedCornerShape(12.dp))
                .combinedClickable(onClick = onToggle, onLongClick = { menu = true })
                .secondaryClick { menu = true }
                .padding(start = 12.dp, end = 0.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(if (folder.instructions.isNotBlank()) Icons.Outlined.FolderSpecial else Icons.Outlined.Folder, contentDescription = null)
            Spacer(Modifier.size(16.dp))
            Text(folder.name, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text("$count", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Icon(if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, contentDescription = null, modifier = Modifier.padding(start = 4.dp).size(18.dp))
            // The menu is also reachable without knowing about long press / right click.
            IconButton(onClick = { menu = true }) {
                Icon(Icons.Outlined.MoreVert, contentDescription = tr("Projekt-Menü"), modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text(tr("Neuer Chat im Projekt")) }, leadingIcon = { Icon(Icons.Outlined.Add, null) }, onClick = { menu = false; onNewChat() })
            DropdownMenuItem(text = { Text(tr("Projekt bearbeiten …")) }, leadingIcon = { Icon(Icons.Outlined.Edit, null) }, onClick = { menu = false; onRename() })
            if (folder.workDir.isNotBlank() && PROJECT_FOLDERS) {
                DropdownMenuItem(
                    text = { Text(tr("Projektordner öffnen")) },
                    leadingIcon = { Icon(Icons.Outlined.FolderOpen, null) },
                    onClick = { menu = false; openProjectFolder(context, folder.workDir) },
                )
            }
            DropdownMenuItem(text = { Text(tr("Löschen")) }, leadingIcon = { Icon(Icons.Outlined.Delete, null) }, onClick = { menu = false; onDelete() })
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
    draft: Boolean = false,
    selecting: Boolean = false,
    checked: Boolean = false,
    onSelect: () -> Unit = {},
    onArchive: () -> Unit = {},
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
                .combinedClickable(onClick = onOpen, onLongClick = { if (selecting) onSelect() else menu = true })
                .secondaryClick { menu = true }
                .padding(start = if (indent) 44.dp else 12.dp, end = if (selected) 0.dp else 12.dp, top = if (selected) 0.dp else 10.dp, bottom = if (selected) 0.dp else 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selecting) {
                androidx.compose.material3.Checkbox(checked, { onSelect() }, modifier = Modifier.size(32.dp))
                Spacer(Modifier.size(8.dp))
            } else if (showIcon) {
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
            if (draft && !running) {
                Text(tr("Entwurf"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 6.dp))
            }
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
            if (selected) {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Outlined.MoreVert, contentDescription = tr("Chat-Menü"), modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text(if (conv.pinned) tr("Lösen") else tr("Anheften")) },
                leadingIcon = { Icon(Icons.Outlined.PushPin, null) },
                onClick = { menu = false; onPin() },
            )
            DropdownMenuItem(text = { Text(tr("In Projekt verschieben")) }, leadingIcon = { Icon(Icons.Outlined.DriveFileMove, null) }, onClick = { menu = false; onMove() })
            DropdownMenuItem(text = { Text(tr("Umbenennen")) }, leadingIcon = { Icon(Icons.Outlined.Edit, null) }, onClick = { menu = false; onRename() })
            DropdownMenuItem(text = { Text(if (conv.archived) tr("Aus dem Archiv holen") else tr("Archivieren")) }, leadingIcon = { Icon(Icons.Outlined.Archive, null) }, onClick = { menu = false; onArchive() })
            DropdownMenuItem(text = { Text(tr("Auswählen")) }, leadingIcon = { Icon(Icons.Outlined.CheckBox, null) }, onClick = { menu = false; onSelect() })
            DropdownMenuItem(text = { Text(tr("Löschen")) }, leadingIcon = { Icon(Icons.Outlined.Delete, null) }, onClick = { menu = false; onDelete() })
        }
    }
}


/** Filters of the chat search (AP-16) as a row of chips with menus. */
@Composable
private fun ChatFilterRow(filter: io.github.thepytales.llmhub.data.ChatFilter, folders: List<Folder>, chats: List<Conversation>, onChange: (io.github.thepytales.llmhub.data.ChatFilter) -> Unit) {
    var period by remember { mutableStateOf(false) }
    var project by remember { mutableStateOf(false) }
    var model by remember { mutableStateOf(false) }
    androidx.compose.foundation.layout.Row(
        Modifier.fillMaxWidth().horizontalScroll(androidx.compose.foundation.rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
    ) {
        Box {
            androidx.compose.material3.FilterChip(filter.days > 0, { period = true }, label = { Text(when (filter.days) { 7 -> tr("7 Tage"); 30 -> tr("30 Tage"); 365 -> tr("1 Jahr"); else -> tr("Zeitraum") }) })
            androidx.compose.material3.DropdownMenu(period, { period = false }) {
                listOf(0 to tr("Immer"), 7 to tr("Letzte 7 Tage"), 30 to tr("Letzte 30 Tage"), 365 to tr("Letztes Jahr")).forEach { (d, label) ->
                    androidx.compose.material3.DropdownMenuItem(text = { Text(label) }, onClick = { period = false; onChange(filter.copy(days = d)) })
                }
            }
        }
        Box {
            androidx.compose.material3.FilterChip(filter.project != null, { project = true }, label = {
                Text(filter.project?.let { id -> if (id.isEmpty()) tr("Ohne Projekt") else folders.firstOrNull { it.id == id }?.name ?: tr("Projekt") } ?: tr("Projekt"), maxLines = 1)
            })
            androidx.compose.material3.DropdownMenu(project, { project = false }) {
                androidx.compose.material3.DropdownMenuItem(text = { Text(tr("Alle")) }, onClick = { project = false; onChange(filter.copy(project = null)) })
                androidx.compose.material3.DropdownMenuItem(text = { Text(tr("Ohne Projekt")) }, onClick = { project = false; onChange(filter.copy(project = "")) })
                folders.forEach { f -> androidx.compose.material3.DropdownMenuItem(text = { Text(f.name) }, onClick = { project = false; onChange(filter.copy(project = f.id)) }) }
            }
        }
        Box {
            val models = remember(chats) { chats.map { it.model }.filter { it.isNotBlank() }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.map { it.key }.take(15) }
            androidx.compose.material3.FilterChip(filter.model != null, { model = true }, label = {
                Text(filter.model?.let { io.github.thepytales.llmhub.data.ModelCatalog.info(it).displayName } ?: tr("Modell"), maxLines = 1)
            })
            androidx.compose.material3.DropdownMenu(model, { model = false }) {
                androidx.compose.material3.DropdownMenuItem(text = { Text(tr("Alle")) }, onClick = { model = false; onChange(filter.copy(model = null)) })
                models.forEach { m -> androidx.compose.material3.DropdownMenuItem(text = { Text(io.github.thepytales.llmhub.data.ModelCatalog.info(m).displayName) }, onClick = { model = false; onChange(filter.copy(model = m)) }) }
            }
        }
        androidx.compose.material3.FilterChip(filter.withAttachments, { onChange(filter.copy(withAttachments = !filter.withAttachments)) }, label = { Text(tr("Mit Anhängen")) })
        androidx.compose.material3.FilterChip(filter.withFiles, { onChange(filter.copy(withFiles = !filter.withFiles)) }, label = { Text(tr("Mit erstellten Dateien")) })
        androidx.compose.material3.FilterChip(filter.archive, { onChange(filter.copy(archive = !filter.archive)) }, label = { Text(tr("Archiv")) })
        if (filter.active) androidx.compose.material3.TextButton(onClick = { onChange(io.github.thepytales.llmhub.data.ChatFilter()) }) { Text(tr("Zurücksetzen")) }
    }
}
