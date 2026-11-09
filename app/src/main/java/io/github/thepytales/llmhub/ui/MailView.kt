package io.github.thepytales.llmhub.ui

import android.content.SharedPreferences
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Drafts
import androidx.compose.material.icons.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.MarkEmailRead
import androidx.compose.material.icons.outlined.MarkEmailUnread
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.PriorityHigh
import androidx.compose.material.icons.outlined.Report
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.thepytales.llmhub.data.CalendarEntry
import io.github.thepytales.llmhub.data.FolderNode
import io.github.thepytales.llmhub.data.MailBody
import io.github.thepytales.llmhub.data.MailFolder
import io.github.thepytales.llmhub.data.MailFolders
import io.github.thepytales.llmhub.data.MailGroups
import io.github.thepytales.llmhub.data.MailHeader
import io.github.thepytales.llmhub.data.MailViewState
import io.github.thepytales.llmhub.data.Outlook
import kotlinx.coroutines.flow.distinctUntilChanged
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/*
 * Die Teile der Mailansicht (AP-69 Stufe a): Ordnerbaum, Mail-Liste mit Suche und Filtern, Lesebereich, Auswahl, Ziehen
 * in einen Ordner und die kleinen Dialoge. The logic lives in data/MailBox (MailBoxModel); MailScreen puts the parts
 * together for wide windows (three columns), tablets (two) and phones (one after the other).
 */

/** Widths of the folder tree and the mail list (the lines between can be dragged), remembered like the side menu. */
internal class MailWidths private constructor(prefs: SharedPreferences) {
    val folders = PanelWidth(prefs, "mailFolderWidth", 250.dp, 180.dp, 440.dp)
    val list = PanelWidth(prefs, "mailListWidth", 430.dp, 300.dp, 780.dp)

    companion object {
        @Volatile
        private var instance: MailWidths? = null

        fun of(c: io.github.thepytales.llmhub.AppContainer): MailWidths = instance ?: synchronized(this) {
            instance ?: MailWidths(c.appContext.getSharedPreferences("ui_state", 0)).also { instance = it }
        }
    }
}

/** Mails dragged with the mouse onto a folder (Windows): the folders note where they are, the list where the pointer is. */
internal class MailDrag {
    var ids by mutableStateOf<List<String>>(emptyList())
    var at by mutableStateOf(Offset.Zero)
    val targets = mutableStateMapOf<String, Pair<Rect, MailFolder>>()
    val active: Boolean get() = ids.isNotEmpty()

    /** The folder under the pointer while mails are dragged. */
    fun over(): MailFolder? = if (!active) null else targets.values.firstOrNull { it.first.contains(at) }?.second
}

/** The language's locale for dates („Oktober 2026“, „Mo 14:03“). */
internal fun mailLocale(): Locale = if (I18n.language == "en") Locale.ENGLISH else Locale.GERMAN

/** Mail content (names, subjects, text) as it is – never looked up in the translations. */
@Composable
private fun Plain(
    text: String,
    modifier: Modifier = Modifier,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = Color.Unspecified,
    weight: FontWeight? = null,
    maxLines: Int = Int.MAX_VALUE,
) = androidx.compose.material3.Text(text, modifier, color = color, fontWeight = weight, maxLines = maxLines, overflow = TextOverflow.Ellipsis, style = style)

/** Time of a mail in the list: „14:03“ today, „Mo 14:03“ this week, „03.11.“ this year, else „03.11.25“. */
internal fun shortTime(millis: Long, today: LocalDate, zone: ZoneId = ZoneId.systemDefault(), locale: Locale = mailLocale()): String {
    if (millis <= 0) return ""
    val t = Instant.ofEpochMilli(millis).atZone(zone)
    val d = t.toLocalDate()
    val en = locale.language == "en"
    val pattern = when {
        d == today -> "HH:mm"
        d.isAfter(today.minusDays(7)) -> "EE HH:mm"
        d.year == today.year -> if (en) "MMM d" else "dd.MM."
        else -> if (en) "M/d/yy" else "dd.MM.yy"
    }
    return DateTimeFormatter.ofPattern(pattern, locale).format(t)
}

private fun folderIcon(f: MailFolder?): ImageVector {
    val n = f?.name?.lowercase().orEmpty()
    return when {
        f == null -> Icons.Outlined.Inbox
        MailFolders.isInbox(f) -> Icons.Outlined.Inbox
        n in setOf("entwürfe", "drafts") -> Icons.Outlined.Drafts
        n in setOf("gesendete elemente", "gesendet", "sent", "sent items") -> Icons.Outlined.DriveFileMove
        n in setOf("archiv", "archive") -> Icons.Outlined.Archive
        n in setOf("gelöschte elemente", "papierkorb", "trash", "deleted items") -> Icons.Outlined.DeleteOutline
        n in setOf("junk-e-mail", "spam", "junk") -> Icons.Outlined.Report
        else -> Icons.Outlined.Folder
    }
}

// ── Ordnerbaum ──────────────────────────────────────────────────────────────────────────────────────────────

/**
 * The folder tree with unread counts; a click opens a folder, the arrow opens or closes its subfolders, a right click
 * (long press) offers „Unterordner anlegen“ and „Umbenennen“. While mails are dragged, the folder under the pointer
 * lights up. Below: today's appointments, if the mailbox has a calendar.
 */
@Composable
internal fun FolderPane(
    state: MailViewState,
    collapsed: Set<String>,
    onToggle: (String) -> Unit,
    onOpen: (MailFolder) -> Unit,
    onNew: (MailFolder?) -> Unit,
    onRename: (MailFolder) -> Unit,
    drag: MailDrag?,
    agenda: List<CalendarEntry>?,
    onAgenda: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val nodes = remember(state.folders, collapsed) { MailFolders.tree(state.folders, collapsed) }
    val current = state.folder?.id
    Column(modifier.fillMaxHeight()) {
        Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(tr("Ordner"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            IconButton(onClick = { onNew(state.folder) }) { Icon(Icons.Outlined.CreateNewFolder, contentDescription = tr("Neuer Ordner …")) }
        }
        if (nodes.isEmpty()) HintText(if (state.error != null) state.error else tr("Ordner werden geladen …"), Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(bottom = 8.dp)) {
            items(nodes, key = { it.key }) { n -> FolderRow(n, n.folder != null && n.folder.id == current, onToggle, onOpen, onNew, onRename, drag) }
        }
        if (!agenda.isNullOrEmpty()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            AgendaPeek(agenda, onAgenda)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderRow(
    n: FolderNode,
    selected: Boolean,
    onToggle: (String) -> Unit,
    onOpen: (MailFolder) -> Unit,
    onNew: (MailFolder?) -> Unit,
    onRename: (MailFolder) -> Unit,
    drag: MailDrag?,
) {
    val f = n.folder
    var menu by remember { mutableStateOf(false) }
    val dropHere = drag != null && f != null && drag.over()?.id == f.id
    val brand = LocalChatLook.current.brand
    // A folder scrolled out of the tree is no target any more.
    androidx.compose.runtime.DisposableEffect(f?.id) { onDispose { f?.let { drag?.targets?.remove(it.id) } } }
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 1.dp)
                .clip8()
                .background(
                    when {
                        dropHere -> brand.copy(alpha = 0.16f)
                        selected -> MaterialTheme.colorScheme.secondaryContainer
                        else -> Color.Transparent
                    },
                )
                .then(if (dropHere) Modifier.border(2.dp, brand, RoundedCornerShape(8.dp)) else Modifier)
                .then(
                    if (f == null || drag == null) Modifier
                    else Modifier.onGloballyPositioned { drag.targets[f.id] = it.boundsInWindow() to f },
                )
                .then(if (f == null) Modifier else Modifier.combinedClickable(onClick = { onOpen(f) }, onLongClick = { menu = true }).secondaryClick { menu = true })
                .heightIn(min = 34.dp)
                .padding(start = (n.depth * 14).dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (n.hasChildren) {
                Icon(
                    if (n.expanded) Icons.Outlined.ExpandMore else Icons.Outlined.ChevronRight,
                    contentDescription = if (n.expanded) tr("Unterordner ausblenden") else tr("Unterordner einblenden"),
                    modifier = Modifier.size(22.dp).clip8().clickable { onToggle(n.key) }.padding(2.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else Spacer(Modifier.width(22.dp))
            if (f == null) {
                Plain(n.label, Modifier.weight(1f).padding(start = 4.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            } else {
                Icon(folderIcon(f), contentDescription = null, modifier = Modifier.size(18.dp), tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
                Plain(n.label, Modifier.weight(1f), weight = if (n.unread > 0) FontWeight.SemiBold else null, maxLines = 1)
                if (n.unread > 0) Plain(n.unread.toString(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, weight = FontWeight.Bold)
            }
        }
        if (f != null) DropdownMenu(menu, { menu = false }) {
            DropdownMenuItem(text = { Text(tr("Unterordner anlegen …")) }, leadingIcon = { Icon(Icons.Outlined.CreateNewFolder, null) }, onClick = { menu = false; onNew(f) })
            DropdownMenuItem(text = { Text(tr("Umbenennen …")) }, leadingIcon = { Icon(Icons.Outlined.Edit, null) }, onClick = { menu = false; onRename(f) })
        }
    }
}

private fun Modifier.clip8(): Modifier = clip(RoundedCornerShape(8.dp))

/** Today's appointments below the folders (the whole calendar is in „Übersicht“). */
@Composable
private fun AgendaPeek(agenda: List<CalendarEntry>, onAll: () -> Unit) {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val todays = agenda.filter { Instant.ofEpochMilli(it.start).atZone(zone).toLocalDate() == today || it.allDay && Instant.ofEpochMilli(it.start).atZone(zone).toLocalDate() <= today }
    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Event, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(tr("Heute"), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            Text(tr("Alle Termine"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.clip8().clickable(onClick = onAll).padding(4.dp))
        }
        if (todays.isEmpty()) HintText(tr("Keine Termine."))
        todays.take(4).forEach { e ->
            val time = if (e.allDay) tr("ganztägig") else DateTimeFormatter.ofPattern("HH:mm").format(Instant.ofEpochMilli(e.start).atZone(zone).toLocalTime() ?: LocalTime.MIDNIGHT)
            Row {
                Plain(time, Modifier.width(62.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Plain(e.subject.ifBlank { tr("(ohne Titel)") }, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, maxLines = 1)
            }
        }
    }
}

// ── Suche und Filter ────────────────────────────────────────────────────────────────────────────────────────

/** Time spans of the filter. */
internal enum class MailSpan(val label: String) {
    ALL(trLater("Jederzeit")), TODAY(trLater("Heute")), WEEK(trLater("Letzte 7 Tage")), MONTH(trLater("Letzte 30 Tage")),
    YEAR(trLater("Dieses Jahr")), CUSTOM(trLater("Eigener Zeitraum …"));

    /** Start and end (exclusive) in epoch millis; [from] and [to] („3.11.2026“) for the own span. */
    fun range(from: String, to: String, zone: ZoneId = ZoneId.systemDefault(), today: LocalDate = LocalDate.now(zone)): Pair<Long?, Long?> {
        fun start(d: LocalDate) = d.atStartOfDay(zone).toInstant().toEpochMilli()
        return when (this) {
            ALL -> null to null
            TODAY -> start(today) to null
            WEEK -> start(today.minusDays(6)) to null
            MONTH -> start(today.minusDays(29)) to null
            YEAR -> start(today.withDayOfYear(1)) to null
            CUSTOM -> {
                val a = Outlook.parseTime(from, zone, LocalTime.MIDNIGHT, today)
                // „bis 5.11.“ includes that whole day.
                val b = Outlook.parseTime(to, zone, LocalTime.MIDNIGHT, today)?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate().plusDays(1) }?.let(::start)
                a to b
            }
        }
    }
}

/** What the filter bar holds (the typed search and the switches); [MailScreen] makes a query of it. */
internal data class MailFilter(
    val typed: String = "",
    val from: String = "",
    val subject: String = "",
    val span: MailSpan = MailSpan.ALL,
    val spanFrom: String = "",
    val spanTo: String = "",
    val unread: Boolean = false,
    val attachments: Boolean = false,
    val allFolders: Boolean = false,
) {
    val active: Boolean get() = typed.isNotBlank() || from.isNotBlank() || subject.isNotBlank() || span != MailSpan.ALL || unread || attachments || allFolders

    fun query(folderId: String?): io.github.thepytales.llmhub.data.MailQuery {
        val (since, until) = span.range(spanFrom, spanTo)
        val parsed = io.github.thepytales.llmhub.data.MailQuery.parse(typed)
        return parsed.copy(
            folderId = folderId, allFolders = allFolders,
            from = from.trim().ifEmpty { parsed.from }, subject = subject.trim().ifEmpty { parsed.subject },
            since = since, until = until, unreadOnly = unread, withAttachments = attachments,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MailFilterBar(filter: MailFilter, onChange: (MailFilter) -> Unit, onSearchKeys: (androidx.compose.ui.input.key.KeyEvent) -> Boolean = { false }) {
    var more by remember { mutableStateOf(false) }
    var spanMenu by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(
            value = filter.typed,
            onValueChange = { onChange(filter.copy(typed = it)) },
            placeholder = { Text(tr("Mails durchsuchen (auch von:… betreff:…)"), maxLines = 1, overflow = TextOverflow.Ellipsis) },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            trailingIcon = {
                Row {
                    if (filter.typed.isNotEmpty()) IconButton(onClick = { onChange(filter.copy(typed = "")) }) { Icon(Icons.Outlined.Close, contentDescription = tr("Suche leeren")) }
                    IconButton(onClick = { more = !more }) {
                        Icon(Icons.Outlined.Tune, contentDescription = tr("Weitere Filter"), tint = if (more || filter.from.isNotBlank() || filter.subject.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(22.dp),
            textStyle = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.fillMaxWidth().onPreviewKeyEvent(onSearchKeys),
        )
        if (more) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(filter.from, { onChange(filter.copy(from = it)) }, label = { Text(tr("Absender")) }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(filter.subject, { onChange(filter.copy(subject = it)) }, label = { Text(tr("Betreff")) }, singleLine = true, modifier = Modifier.weight(1f))
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(filter.unread, { onChange(filter.copy(unread = !filter.unread)) }, label = { Text(tr("Ungelesen")) })
            FilterChip(filter.attachments, { onChange(filter.copy(attachments = !filter.attachments)) }, label = { Text(tr("Mit Anhang")) }, leadingIcon = { Icon(Icons.Outlined.AttachFile, null, Modifier.size(16.dp)) })
            Box {
                FilterChip(
                    filter.span != MailSpan.ALL, { spanMenu = true },
                    label = { Text(if (filter.span == MailSpan.ALL) tr("Zeitraum") else if (filter.span == MailSpan.CUSTOM) tr("Eigener Zeitraum") else tr(filter.span.label)) },
                    trailingIcon = { Icon(Icons.Outlined.ExpandMore, null, Modifier.size(16.dp)) },
                )
                DropdownMenu(spanMenu, { spanMenu = false }) {
                    MailSpan.entries.forEach { s -> DropdownMenuItem(text = { Text(tr(s.label)) }, onClick = { spanMenu = false; onChange(filter.copy(span = s)) }) }
                }
            }
            FilterChip(filter.allFolders, { onChange(filter.copy(allFolders = !filter.allFolders)) }, label = { Text(tr("Alle Ordner")) })
            if (filter.active) TextButton(onClick = { onChange(MailFilter()) }) { Text(tr("Filter zurücksetzen")) }
        }
        if (filter.span == MailSpan.CUSTOM) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(filter.spanFrom, { onChange(filter.copy(spanFrom = it)) }, label = { Text(tr("von (TT.MM.JJJJ)")) }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(filter.spanTo, { onChange(filter.copy(spanTo = it)) }, label = { Text(tr("bis (TT.MM.JJJJ)")) }, singleLine = true, modifier = Modifier.weight(1f))
            }
        }
    }
}

// ── Mail-Liste ──────────────────────────────────────────────────────────────────────────────────────────────

/** What the list can do; MailScreen fills it from the model. */
internal class MailListActions(
    val click: (id: String, toggle: Boolean, range: Boolean) -> Unit,
    val check: (String) -> Unit,
    val loadMore: () -> Unit,
    val step: (delta: Int, extend: Boolean) -> Unit,
    val selectAll: () -> Unit,
    val clear: () -> Unit,
    val markRead: (List<String>, Boolean) -> Unit,
    val move: (List<String>) -> Unit,
    val moveTo: (List<String>, MailFolder) -> Unit,
    val showInOutlook: ((String) -> Unit)?,
)

/**
 * The mails of the folder (or the search), grouped by Heute, Gestern, Diese Woche …, newest first; pages load while
 * scrolling. Click, Strg/Umschalt-click, the check boxes, arrow keys, Strg+A, Strg+Q (gelesen) and Strg+U (ungelesen)
 * choose and mark; on Windows the mails can be dragged onto a folder.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun MailList(state: MailViewState, actions: MailListActions, drag: MailDrag?, focus: FocusRequester, modifier: Modifier = Modifier) {
    val zone = ZoneId.systemDefault()
    val locale = mailLocale()
    val today = LocalDate.now(zone)
    val groups = remember(state.mails, today, locale) { MailGroups.groups(state.mails, today, zone, locale) }
    // Index of each mail among the lines of the list (group headers count), for scrolling to the arrow keys' mail.
    val lineOf = remember(groups) {
        val map = HashMap<String, Int>(state.mails.size * 2)
        var line = 0
        groups.forEach { (_, list) -> line++; list.forEach { m -> map[m.id] = line++ } }
        map
    }
    val listState = rememberLazyListState()
    val checking = state.selected.size > 1 || IS_ANDROID && state.selected.isNotEmpty()

    // A new search starts at the top.
    LaunchedEffect(state.query) { listState.scrollToItem(0) }
    // Near the end the next page loads (also when a page brought nothing new).
    LaunchedEffect(listState) {
        snapshotFlow { Triple(listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0, listState.layoutInfo.totalItemsCount, state.next) }
            .distinctUntilChanged()
            .collect { (last, count, _) -> if (last >= count - 15) actions.loadMore() }
    }
    LaunchedEffect(state.lead) {
        val line = state.lead?.let { lineOf[it] } ?: return@LaunchedEffect
        val visible = listState.layoutInfo.visibleItemsInfo
        if (visible.none { it.index == line } || visible.lastOrNull()?.index == line) listState.animateScrollToItem((line - 2).coerceAtLeast(0))
    }

    Box(
        modifier
            .focusRequester(focus)
            .focusable()
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val cmd = e.isCtrlPressed || e.isMetaPressed
                val chosen = state.selected.toList()
                when {
                    e.key == Key.DirectionDown -> { actions.step(1, e.isShiftPressed); true }
                    e.key == Key.DirectionUp -> { actions.step(-1, e.isShiftPressed); true }
                    cmd && e.key == Key.A -> { actions.selectAll(); true }
                    cmd && e.key == Key.Q && chosen.isNotEmpty() -> { actions.markRead(chosen, true); true }
                    cmd && e.key == Key.U && chosen.isNotEmpty() -> { actions.markRead(chosen, false); true }
                    e.key == Key.Escape && chosen.isNotEmpty() -> { actions.clear(); true }
                    else -> false
                }
            },
    ) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = LORA_BUTTON_ROOM)) {
            groups.forEachIndexed { gi, (label, list) ->
                stickyHeader(key = "g$gi:$label", contentType = "head") { GroupHeader(tr(label), list.size) }
                items(list, key = { it.id }, contentType = { "mail" }) { m ->
                    MailRow(
                        m, selected = m.id in state.selected, checking = checking, showFolder = state.query.allFolders, today = today,
                        onClick = { toggle, range -> focus.requestFocusSafely(); actions.click(m.id, toggle, range) },
                        onCheck = { actions.check(m.id) },
                        actions = actions, chosen = { if (m.id in state.selected) state.selected.toList() else listOf(m.id) },
                        drag = drag,
                    )
                }
            }
            when {
                state.loading -> item(key = "loading") {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp) }
                }
                state.hasMore -> item(key = "more") {
                    Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) { TextButton(onClick = actions.loadMore) { Text(tr("Weitere Mails laden")) } }
                }
                state.mails.isNotEmpty() -> item(key = "end") {
                    HintText(tr("{0} Mails", state.mails.size), Modifier.fillMaxWidth().padding(16.dp))
                }
            }
            if (state.mails.isEmpty() && !state.loading) item(key = "empty") {
                Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Outlined.Inbox, contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(36.dp))
                    HintText(if (state.query.filtered) tr("Keine Mails passen zur Suche.") else tr("Keine Mails in diesem Ordner."))
                }
            }
        }
    }
}

private fun FocusRequester.requestFocusSafely() {
    runCatching { requestFocus() }
}


@Composable
private fun GroupHeader(label: String, count: Int) {
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(start = 14.dp, end = 12.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Plain(label, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, weight = FontWeight.SemiBold)
        Plain(count.toString(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** One mail of the list: sender, time, subject, the start of the text; a bar and bold type for unread ones. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MailRow(
    m: MailHeader,
    selected: Boolean,
    checking: Boolean,
    showFolder: Boolean,
    today: LocalDate,
    onClick: (toggle: Boolean, range: Boolean) -> Unit,
    onCheck: () -> Unit,
    actions: MailListActions,
    chosen: () -> List<String>,
    drag: MailDrag?,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    // Strg/Umschalt at the moment of the press (pointer events know them on every platform).
    val keys = remember { booleanArrayOf(false, false) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    var menu by remember { mutableStateOf(false) }
    val brand = LocalChatLook.current.brand
    val unread = m.unread
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .background(
                    when {
                        selected -> MaterialTheme.colorScheme.secondaryContainer
                        hovered -> MaterialTheme.colorScheme.surfaceContainerHigh
                        else -> Color.Transparent
                    },
                )
                .hoverable(interaction)
                .onGloballyPositioned { origin = it.positionInWindow() }
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val e = awaitPointerEvent(PointerEventPass.Initial)
                            if (e.type == PointerEventType.Press) {
                                keys[0] = e.keyboardModifiers.isCmd
                                keys[1] = e.keyboardModifiers.isShiftPressed
                            }
                        }
                    }
                }
                .then(
                    if (drag == null) Modifier else Modifier.pointerInput(m.id) {
                        detectDragGestures(
                            onDragStart = { p -> drag.ids = chosen(); drag.at = origin + p },
                            onDrag = { change, _ -> drag.at = origin + change.position },
                            onDragEnd = {
                                val target = drag.over()
                                val ids = drag.ids
                                drag.ids = emptyList()
                                if (target != null) actions.moveTo(ids, target)
                            },
                            onDragCancel = { drag.ids = emptyList() },
                        )
                    },
                )
                .combinedClickable(interactionSource = interaction, indication = null, onClick = { onClick(keys[0], keys[1]) }, onLongClick = onCheck)
                .secondaryClick { menu = true }
                .padding(end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(4.dp).height(58.dp).background(if (unread) brand else Color.Transparent))
            if (checking || hovered) {
                CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                    Checkbox(selected, { onCheck() }, Modifier.padding(start = 6.dp).size(20.dp))
                }
                Spacer(Modifier.width(6.dp))
            } else Spacer(Modifier.width(32.dp))
            Column(Modifier.weight(1f).padding(vertical = 7.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Plain(m.from.ifBlank { m.fromAddress }, Modifier.weight(1f), weight = if (unread) FontWeight.Bold else null, maxLines = 1)
                    Spacer(Modifier.width(8.dp))
                    Plain(shortTime(m.received, today), style = MaterialTheme.typography.labelSmall, color = if (unread) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Plain(m.subject.ifBlank { tr("(ohne Betreff)") }, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, weight = if (unread) FontWeight.SemiBold else null, color = if (unread) MaterialTheme.colorScheme.primary else Color.Unspecified, maxLines = 1)
                    if (m.important) Icon(Icons.Outlined.PriorityHigh, contentDescription = tr("Wichtig"), tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(14.dp))
                    if (m.attachments > 0) Icon(Icons.Outlined.AttachFile, contentDescription = tr("Mit Anhang"), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                    if (m.flagged) Icon(Icons.Outlined.Flag, contentDescription = tr("Zur Nachverfolgung"), tint = Color(0xFFD9534F), modifier = Modifier.size(14.dp))
                }
                if (m.preview.isNotBlank()) Plain(m.preview, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                if (showFolder) Plain(m.folder.split('\\').filter { it.isNotEmpty() }.lastOrNull().orEmpty(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, maxLines = 1)
            }
        }
        DropdownMenu(menu, { menu = false }) {
            val ids = chosen()
            if (m.unread) DropdownMenuItem(text = { Text(tr("Als gelesen markieren")) }, leadingIcon = { Icon(Icons.Outlined.MarkEmailRead, null) }, onClick = { menu = false; actions.markRead(ids, true) })
            else DropdownMenuItem(text = { Text(tr("Als ungelesen markieren")) }, leadingIcon = { Icon(Icons.Outlined.MarkEmailUnread, null) }, onClick = { menu = false; actions.markRead(ids, false) })
            DropdownMenuItem(text = { Text(tr("Verschieben nach …")) }, leadingIcon = { Icon(Icons.Outlined.DriveFileMove, null) }, onClick = { menu = false; actions.move(ids) })
            actions.showInOutlook?.let { show -> DropdownMenuItem(text = { Text(tr("In Outlook öffnen")) }, leadingIcon = { Icon(Icons.Outlined.OpenInNew, null) }, onClick = { menu = false; show(m.id) }) }
        }
    }
}

/** The pill that follows the pointer while mails are dragged; [origin] is where the screen lies in the window. */
@Composable
internal fun DragPill(drag: MailDrag, origin: Offset) {
    if (!drag.active) return
    val over = drag.over()
    val p = drag.at - origin
    Surface(
        shape = RoundedCornerShape(50),
        color = LocalChatLook.current.brand,
        contentColor = Color.White,
        shadowElevation = 4.dp,
        modifier = Modifier.offset { androidx.compose.ui.unit.IntOffset(p.x.toInt() + 14, p.y.toInt() + 10) },
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.DriveFileMove, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Plain(
                if (over != null) tr("{0} Mail(s) nach „{1}“", drag.ids.size, over.name) else tr("{0} Mail(s) – auf einen Ordner ziehen", drag.ids.size),
                style = MaterialTheme.typography.labelLarge, color = Color.White,
            )
        }
    }
}


// ── Lesebereich ─────────────────────────────────────────────────────────────────────────────────────────────

/** What the reading pane can do with the open mail. */
internal class ReadingActions(
    val markRead: (List<String>, Boolean) -> Unit,
    val move: (List<String>) -> Unit,
    val showInOutlook: ((String) -> Unit)?,
    val answer: (MailHeader) -> Unit,
    val openAttachment: (MailHeader, Int, String) -> Unit,
    val keepAttachment: (MailHeader, Int, String) -> Unit,
)

/**
 * The open mail: subject, sender, recipients, time, attachments (open, or keep in the Lora-Ordner), the text; above
 * it gelesen/ungelesen, verschieben, *In Outlook öffnen* and an answer with Lora (a draft, never sent).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ReadingPane(header: MailHeader, body: MailBody?, problem: String?, actions: ReadingActions, onBack: (() -> Unit)?, modifier: Modifier = Modifier) {
    val zone = ZoneId.systemDefault()
    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("Zurück zur Liste")) }
            Spacer(Modifier.weight(1f))
            if (header.unread) IconButton(onClick = { actions.markRead(listOf(header.id), true) }) { Icon(Icons.Outlined.MarkEmailRead, contentDescription = tr("Als gelesen markieren")) }
            else IconButton(onClick = { actions.markRead(listOf(header.id), false) }) { Icon(Icons.Outlined.MarkEmailUnread, contentDescription = tr("Als ungelesen markieren")) }
            IconButton(onClick = { actions.move(listOf(header.id)) }) { Icon(Icons.Outlined.DriveFileMove, contentDescription = tr("Verschieben nach …")) }
            actions.showInOutlook?.let { show -> IconButton(onClick = { show(header.id) }) { Icon(Icons.Outlined.OpenInNew, contentDescription = tr("In Outlook öffnen")) } }
            TextButton(onClick = { actions.answer(header) }) {
                Icon(Icons.Outlined.EditNote, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(tr("Antwort mit Lora"))
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
        SelectionContainer(Modifier.weight(1f)) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Plain(header.subject.ifBlank { tr("(ohne Betreff)") }, style = MaterialTheme.typography.titleLarge)
                Row(verticalAlignment = Alignment.Top) {
                    val name = header.from.ifBlank { header.fromAddress }
                    Box(Modifier.size(38.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                        Plain(name.split(Regex("[\\s,.]+")).filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1).uppercase() }.ifEmpty { "?" }, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Plain(name + if (header.fromAddress.isNotBlank() && header.fromAddress != name) " <${header.fromAddress}>" else "", weight = FontWeight.SemiBold)
                        if (header.to.isNotBlank()) Plain(tr("An: {0}", header.to), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3)
                        body?.cc?.takeIf { it.isNotBlank() }?.let { Plain(tr("Cc: {0}", it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3) }
                    }
                    if (header.received > 0) Plain(Outlook.show(header.received, zone), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                val names = body?.attachmentNames.orEmpty()
                if (names.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    names.forEachIndexed { i, n -> AttachmentChip(n.ifBlank { tr("Anhang") }, onOpen = { actions.openAttachment(header, i, n) }, onKeep = { actions.keepAttachment(header, i, n) }) }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                when {
                    problem != null -> Text(problem, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    body == null -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        HintText(tr("Mail wird geladen …"))
                    }
                    else -> Plain(body.body.take(200_000).trim().ifEmpty { tr("(kein Text)") }, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun AttachmentChip(name: String, onOpen: () -> Unit, onKeep: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Box {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.widthIn(max = 280.dp),
        ) {
            Row(Modifier.clickable(onClick = onOpen).secondaryClick { menu = true }.padding(start = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.AttachFile, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(6.dp))
                Plain(name, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.labelLarge, maxLines = 1)
                IconButton(onClick = { menu = true }, modifier = Modifier.size(32.dp)) { Icon(Icons.Outlined.MoreVert, contentDescription = tr("Mehr"), modifier = Modifier.size(16.dp)) }
            }
        }
        DropdownMenu(menu, { menu = false }) {
            DropdownMenuItem(text = { Text(tr("Öffnen")) }, leadingIcon = { Icon(Icons.Outlined.OpenInNew, null) }, onClick = { menu = false; onOpen() })
            DropdownMenuItem(text = { Text(tr("In den Lora-Ordner speichern")) }, leadingIcon = { Icon(Icons.Outlined.Folder, null) }, onClick = { menu = false; onKeep() })
        }
    }
}

/** Several mails chosen: how many, which, and what can be done with all of them. */
@Composable
internal fun SelectionPane(chosen: List<MailHeader>, actions: MailListActions, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.SelectAll, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(10.dp))
            Text(tr("{0} Mails ausgewählt", chosen.size), style = MaterialTheme.typography.titleLarge)
        }
        chosen.take(8).forEach { m -> Plain("• " + m.subject.ifBlank { tr("(ohne Betreff)") } + " – " + m.from, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1) }
        if (chosen.size > 8) HintText(tr("… und {0} weitere", chosen.size - 8))
        val ids = chosen.map { it.id }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { actions.markRead(ids, true) }) { Text(tr("Als gelesen markieren")) }
            OutlinedButton(onClick = { actions.markRead(ids, false) }) { Text(tr("Als ungelesen markieren")) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { actions.move(ids) }) {
                Icon(Icons.Outlined.DriveFileMove, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(tr("Verschieben nach …"))
            }
            TextButton(onClick = actions.clear) { Text(tr("Auswahl aufheben")) }
        }
        HintText(tr("Lora kennt diese Auswahl – im Lora-Panel genügt „diese Mails“. Was in den Mails steht, liest nur ein Modell auf diesem Gerät."))
    }
}

/** Nothing chosen: what the reading pane shows. */
@Composable
internal fun EmptyReading(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)) {
        Icon(Icons.Outlined.Inbox, contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(44.dp))
        Text(tr("Wähle eine Mail aus, um sie hier zu lesen."), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        HintText(if (DRAG_TO_MOVE) tr("Strg- oder Umschalt-Klick wählt mehrere; zum Verschieben auf einen Ordner ziehen.") else tr("Lange drücken wählt mehrere Mails."))
    }
}

// ── Dialoge ─────────────────────────────────────────────────────────────────────────────────────────────────

/** Choose the folder to move [count] mails into (with a search over the folder names). */
@Composable
internal fun FolderPickerDialog(folders: List<MailFolder>, count: Int, onPick: (MailFolder) -> Unit, onNew: () -> Unit, onDismiss: () -> Unit) {
    var find by remember { mutableStateOf("") }
    val nodes = remember(folders) { MailFolders.tree(folders) }
    val shown = if (find.isBlank()) nodes.filter { it.folder != null } else folders.filter { it.path.contains(find.trim(), ignoreCase = true) }
        .map { FolderNode(it.path, MailFolders.shortPath(it), 0, it, false, true, it.unread) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("{0} Mail(s) verschieben nach …", count)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(find, { find = it }, placeholder = { Text(tr("Ordner suchen")) }, singleLine = true, leadingIcon = { Icon(Icons.Outlined.Search, null) }, modifier = Modifier.fillMaxWidth())
                LazyColumn(Modifier.heightIn(max = 380.dp)) {
                    items(shown, key = { it.key }) { n ->
                        val f = n.folder ?: return@items
                        Row(
                            Modifier.fillMaxWidth().clip8().clickable { onPick(f) }.padding(start = (8 + n.depth * 14).dp, top = 8.dp, bottom = 8.dp, end = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(folderIcon(f), contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(8.dp))
                            Plain(n.label, maxLines = 1)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onNew) { Text(tr("Neuer Ordner …")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Abbrechen")) } },
    )
}

/** A new folder (also several levels: „Lehre/WS 2026-27“) below a chosen one. */
@Composable
internal fun NewFolderDialog(folders: List<MailFolder>, start: MailFolder?, onCreate: (MailFolder?, String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var parent by remember { mutableStateOf(start) }
    var menu by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Neuer Ordner")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text(tr("Name")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                HintText(tr("Unterordner mit / trennen, z. B. „Lehre/WS 2026-27“. Fehlende Ebenen werden mit angelegt."))
                Box {
                    OutlinedButton(onClick = { menu = true }, modifier = Modifier.fillMaxWidth()) {
                        Plain(tr("In: {0}", parent?.let(MailFolders::shortPath) ?: tr("Posteingang")), maxLines = 1)
                    }
                    DropdownMenu(menu, { menu = false }) {
                        folders.take(400).forEach { f -> DropdownMenuItem(text = { Plain(MailFolders.shortPath(f), maxLines = 1) }, onClick = { parent = f; menu = false }) }
                    }
                }
            }
        },
        confirmButton = { Button(enabled = MailFolders.levels(name).isNotEmpty(), onClick = { onCreate(parent, name) }) { Text(tr("Anlegen")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Abbrechen")) } },
    )
}

@Composable
internal fun RenameFolderDialog(folder: MailFolder, onRename: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(folder.name) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Ordner umbenennen")) },
        text = { OutlinedTextField(name, { name = it }, label = { Text(tr("Name")) }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
        confirmButton = { Button(enabled = name.isNotBlank() && name.trim() != folder.name, onClick = { onRename(name) }) { Text(tr("Umbenennen")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Abbrechen")) } },
    )
}

/** A card above the list: problems with the mailbox and, from other parts of the app, their notes (see MailScreen). */
@Composable
internal fun MailNotice(text: String, error: Boolean = false, action: Pair<String, () -> Unit>? = null) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (error) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, style = MaterialTheme.typography.bodySmall, color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            action?.let { (label, run) -> TextButton(onClick = run) { Text(label) } }
        }
    }
}

/** A column with the given contents (for the notices of other parts). */
@Composable
internal fun MailNotices(content: @Composable ColumnScope.() -> Unit) = Column(Modifier.fillMaxWidth(), content = content)
