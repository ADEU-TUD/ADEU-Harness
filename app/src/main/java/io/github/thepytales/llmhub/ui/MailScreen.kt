package io.github.thepytales.llmhub.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Rule
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.agent.ChatModes
import io.github.thepytales.llmhub.agent.LoraFocus
import io.github.thepytales.llmhub.agent.OutlookTools
import io.github.thepytales.llmhub.data.CalendarEntry
import io.github.thepytales.llmhub.data.MailBody
import io.github.thepytales.llmhub.data.MailBoxModel
import io.github.thepytales.llmhub.data.MailChange
import io.github.thepytales.llmhub.data.MailFolder
import io.github.thepytales.llmhub.data.MailHeader
import io.github.thepytales.llmhub.data.MailRule
import io.github.thepytales.llmhub.data.MailViewState
import io.github.thepytales.llmhub.data.Outlook
import io.github.thepytales.llmhub.data.OutlookAction
import io.github.thepytales.llmhub.data.OutlookBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/** Connection to Outlook as the screen shows it. */
private sealed interface OutlookState {
    data object Connecting : OutlookState
    data class Ready(val accounts: String) : OutlookState
    data class Problem(val message: String) : OutlookState
}

/** Starts a chat in the Postfach mode; [send] sends [text] at once, otherwise it waits in the input. */
internal fun newMailChat(text: String, send: Boolean) {
    ChatHandoff.pending.value = ChatHandoff.Content(text, emptyList(), mode = ChatModes.MAIL.id, send = send)
    ChatHandoff.open.value = ""
}

/** The two tabs: the mailbox like a mail program, and connection, Lora, appointments, rules and protocol. */
private enum class MailTab(val label: String) { MAILS(trLater("Mails")), OVERVIEW(trLater("Übersicht")) }

/** Rückgängig of the mail view works also after leaving the screen (the banner is the app's). */
private val undoScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

/** A change of the Mailansicht in the Outlook protocol („Sie“ did it by hand). */
private fun logByHand(c: AppContainer, what: String, detail: String) {
    c.outlookLog.upsert(OutlookAction(what = what, detail = detail, by = "Sie"))
    val all = c.outlookLog.current
    if (all.size > OutlookTools.MAX_LOG) c.outlookLog.replaceAll(all.sortedByDescending { it.at }.take(OutlookTools.MAX_LOG))
}

/**
 * Mail & Kalender (AP-13, AP-27, AP-69): the mailbox like a mail program – folder tree, mail list in pages with search
 * and filters, reading pane – in three columns on wide windows, two on tablets and one after the other on phones;
 * mails are chosen, dragged into folders, marked read or unread, folders created and renamed, all with Rückgängig and
 * in the protocol. „Übersicht“ keeps the connection, starts for Lora, the appointments, the sorting rules and the
 * protocol. Nothing is deleted or sent.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MailScreen(c: AppContainer, onBack: () -> Unit) {
    val bridge = Outlook.bridge
    var state by remember { mutableStateOf<OutlookState>(OutlookState.Connecting) }
    var agenda by remember { mutableStateOf<List<CalendarEntry>?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var tab by remember { mutableStateOf(MailTab.MAILS) }
    val model = remember(bridge) { bridge?.let { b -> MailBoxModel(b, log = { what, detail -> logByHand(c, what, detail) }) } }

    LaunchedEffect(attempt) {
        if (bridge == null) {
            state = OutlookState.Problem(tr("Noch kein Postfach verbunden. Unter Windows arbeitet Lora mit dem klassischen Outlook; sonst – und auf dem Handy – unter Einstellungen › Verbindungen › „Mail über den Server“ den Mailserver eintragen."))
            return@LaunchedEffect
        }
        state = OutlookState.Connecting
        state = withContext(Dispatchers.IO) {
            runCatching { OutlookState.Ready(bridge.connect()) }.getOrElse { OutlookState.Problem(Outlook.explain(it)) }
        }
        if (state is OutlookState.Ready) {
            launch { model?.start() }
            val zone = ZoneId.systemDefault()
            val today = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
            agenda = withContext(Dispatchers.IO) { runCatching { bridge.events(today, today + 2 * 86_400_000L) }.getOrNull() }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { BackButton(onBack) },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(tr("Mail & Kalender"), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        Spacer(Modifier.width(14.dp))
                        Segmented(MailTab.entries.map { it to it.label }, tab, { tab = it }, Modifier.widthIn(max = 240.dp))
                    }
                },
                actions = {
                    IconButton(onClick = { if (state is OutlookState.Ready) model?.let { m -> undoScope.launch { m.refresh() } } else attempt++ }) {
                        Icon(Icons.Outlined.Refresh, contentDescription = tr("Neu laden"))
                    }
                },
            )
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            when {
                tab == MailTab.OVERVIEW -> MailOverview(c, bridge, state, agenda, onRetry = { attempt++ })
                model == null || state !is OutlookState.Ready -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    Column(Modifier.widthIn(max = 860.dp).padding(16.dp)) { StatusCard(state, onRetry = { attempt++ }) }
                }
                else -> MailBoxView(c, model, agenda, onAgenda = { tab = MailTab.OVERVIEW })
            }
        }
    }
}

/**
 * The mailbox itself: folder tree · list · reading pane (wide), list · reading pane with the folders in a drawer
 * (tablet), or list, then the mail (phone).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MailBoxView(c: AppContainer, model: MailBoxModel, agenda: List<CalendarEntry>?, onAgenda: () -> Unit) {
    val s by model.state.collectAsState()
    val context = LocalContext.current
    val widths = remember { MailWidths.of(c) }
    var filter by remember { mutableStateOf(MailFilter()) }
    var collapsed by remember { mutableStateOf(emptySet<String>()) }
    var moving by remember { mutableStateOf<List<String>?>(null) }
    var newFolderIn by remember { mutableStateOf<MailFolder?>(null) }
    var newFolder by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<MailFolder?>(null) }
    var reading by remember { mutableStateOf(false) }
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val listFocus = remember { FocusRequester() }
    val drag = remember { if (DRAG_TO_MOVE) MailDrag() else null }
    val llm by c.localLlm.state.collectAsStateWithLifecycle()
    val localModel = remember(llm.installed, llm.running) { io.github.thepytales.llmhub.agent.MailAccess.model(c) }
    var origin by remember { mutableStateOf(Offset.Zero) }

    /** Runs a change and shows its notice – with Rückgängig when it has one. */
    fun act(block: suspend () -> MailChange) {
        undoScope.launch {
            runCatching { block() }.onSuccess { ch ->
                val undo = ch.undo
                if (undo == null) Notices.info(ch.text)
                else Notices.undo(ch.text, restore = {
                    undoScope.launch { runCatching { undo() }.onSuccess { Notices.info(it) }.onFailure { Notices.error(Outlook.explain(it)) } }
                })
            }.onFailure { Notices.error(Outlook.explain(it)) }
        }
    }

    // The folder shown: taken from the model (the Posteingang after the start, a new id after renaming on the server)
    // and set by a click in the tree. Filter bar and folder search together, after a short pause in typing.
    var folderId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(s.query.folderId) { s.query.folderId?.let { folderId = it } }
    LaunchedEffect(filter, folderId) {
        val id = folderId ?: return@LaunchedEffect
        delay(if (filter.typed.isNotEmpty() || filter.from.isNotEmpty() || filter.subject.isNotEmpty()) 450 else 0)
        val q = filter.query(id)
        if (q != s.query) model.search(q)
    }
    // Lora's panel knows the choice („diese 12 Mails …“): ids, how many and subjects – never the text.
    LaunchedEffect(s.selected) { LoraFocus.mails.value = model.selection() }
    DisposableEffect(Unit) { onDispose { LoraFocus.mails.value = null } }
    // Like Outlook: a mail counts as read once the reading pane moves on from it.
    var shown by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(s.open) {
        val before = shown
        shown = s.open
        if (before != null && before != s.open) runCatching { model.markRead(listOf(before), true, quiet = true) }
    }

    val showInOutlook: ((String) -> Unit)? = if (Outlook.viaServer) null else { id ->
        undoScope.launch { runCatching { withContext(Dispatchers.IO) { Outlook.bridge?.show(id) } }.onFailure { Notices.error(Outlook.explain(it)) } }
    }
    val moveAsk: (List<String>) -> Unit = { ids ->
        if (!s.canMove) Notices.error(tr("Dieser Mailserver kann nicht verschieben, ohne dabei andere gelöschte Mails endgültig zu entfernen – deshalb lässt die App das."))
        else moving = ids
    }
    val listActions = MailListActions(
        click = { id, toggle, range -> model.click(id, toggle, range); if (!toggle && !range) reading = true },
        check = model::check,
        loadMore = { scope.launch { model.loadMore() } },
        step = model::step,
        selectAll = model::selectAll,
        clear = model::clearSelection,
        markRead = { ids, read -> act { model.markRead(ids, read) } },
        move = moveAsk,
        moveTo = { ids, f -> if (s.canMove) act { model.move(ids, f) } else moveAsk(ids) },
        showInOutlook = showInOutlook,
    )
    val readingActions = ReadingActions(
        markRead = listActions.markRead,
        move = moveAsk,
        showInOutlook = showInOutlook,
        answer = { m -> newMailChat(tr("Entwirf eine Antwort auf die Mail „{0}“ von {1}.", m.subject, m.from.ifBlank { m.fromAddress }), send = false) },
        openAttachment = { m, i, name ->
            undoScope.launch {
                runCatching { model.saveAttachment(m.id, i, File(c.appContext.cacheDir, "mail-anhaenge/" + UUID.randomUUID().toString().take(8))) }
                    .onSuccess { openDocument(context, it) }
                    .onFailure { Notices.error(tr("„{0}“ ließ sich nicht öffnen: {1}", name, Outlook.explain(it))) }
            }
        },
        keepAttachment = { m, i, name ->
            undoScope.launch {
                runCatching {
                    val f = model.saveAttachment(m.id, i, File(c.appContext.cacheDir, "mail-anhaenge/" + UUID.randomUUID().toString().take(8)))
                    withContext(Dispatchers.IO) { c.drive.keepUpload(f.name, f.readBytes()) } ?: error(tr("Speichern ging nicht."))
                }.onSuccess { Notices.info(tr("„{0}“ liegt jetzt im Lora-Ordner (Hochgeladen).", it.name)) }
                    .onFailure { Notices.error(tr("„{0}“ ließ sich nicht speichern: {1}", name, Outlook.explain(it))) }
            }
        },
    )
    val openFolder: (MailFolder) -> Unit = { f ->
        reading = false
        scope.launch { drawer.close() }
        filter = filter.copy(allFolders = false)
        folderId = f.id
    }

    @Composable
    fun folders(mod: Modifier) = FolderPane(
        s, collapsed, onToggle = { k -> collapsed = if (k in collapsed) collapsed - k else collapsed + k },
        onOpen = openFolder, onNew = { f -> newFolderIn = f; newFolder = true }, onRename = { renaming = it },
        drag = drag, agenda = agenda, onAgenda = onAgenda, modifier = mod,
    )

    @Composable
    fun list(mod: Modifier, withFolderButton: Boolean) = Column(mod.fillMaxHeight()) {
        Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 6.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (withFolderButton) IconButton(onClick = { scope.launch { drawer.open() } }) { Icon(Icons.Outlined.Folder, contentDescription = tr("Ordner")) }
            Column(Modifier.weight(1f)) {
                androidx.compose.material3.Text(
                    if (s.query.allFolders) tr("Alle Ordner") else s.folder?.name ?: tr("Posteingang"),
                    style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                // When the list could not be read, the folder's own count (not „0 Mails“).
                val total = if (s.error != null && s.mails.isEmpty() && !s.query.filtered) s.folder?.items ?: s.total else s.total
                HintText(if (s.loading && s.mails.isEmpty()) tr("Wird geladen …") else tr("{0} Mails", total) + (s.folder?.unread?.takeIf { it > 0 && !s.query.filtered }?.let { " · " + tr("{0} ungelesen", it) } ?: ""))
            }
        }
        MailFilterBar(filter, onChange = { filter = it })
        // Cards above the list: a local model is needed (AP-68), Lora's sort runs (AP-69b), problems of the mailbox.
        MailNotices {
            if (localModel == null) Box(Modifier.padding(horizontal = 10.dp, vertical = 4.dp)) { MailLocalModelCard(c) }
            Box(Modifier.padding(horizontal = 10.dp)) { OpenMailSorts(c) }
            s.error?.let { e -> MailNotice(e, error = true, action = tr("Erneut versuchen") to { scope.launch { model.refresh() } }) }
            if (!s.canMove) MailNotice(tr("Dieser Mailserver kann nicht verschieben, ohne dabei andere gelöschte Mails endgültig zu entfernen – deshalb lässt die App das."))
        }
        MailList(s, listActions, drag, listFocus, Modifier.weight(1f))
    }

    @Composable
    fun readingPane(mod: Modifier, onBack: (() -> Unit)?) {
        val chosen = s.selectedMails()
        val open = s.open?.let { id -> s.mails.firstOrNull { it.id == id } }
        when {
            chosen.size > 1 -> SelectionPane(chosen, listActions, mod)
            open != null -> OpenMail(model, open, readingActions, onBack, mod)
            else -> EmptyReading(mod)
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInWindow() }) {
        val w = maxWidth
        SideEffect {
            widths.folders.limit = w * 0.3f
            widths.list.limit = w * 0.5f
        }
        when {
            w >= 1000.dp -> Row(Modifier.fillMaxSize()) {
                folders(Modifier.width(widths.folders.value))
                PanelResizer(widths.folders, panelLeft = true, label = tr("Breite der Ordner ändern"))
                list(Modifier.width(widths.list.value), withFolderButton = false)
                PanelResizer(widths.list, panelLeft = true, label = tr("Breite der Mail-Liste ändern"))
                readingPane(Modifier.weight(1f), onBack = null)
            }
            else -> ModalNavigationDrawer(
                drawerState = drawer,
                gesturesEnabled = drawer.isOpen,
                drawerContent = { ModalDrawerSheet(Modifier.width(300.dp)) { folders(Modifier.fillMaxHeight()) } },
            ) {
                if (w >= 640.dp) Row(Modifier.fillMaxSize()) {
                    list(Modifier.width(widths.list.value), withFolderButton = true)
                    PanelResizer(widths.list, panelLeft = true, label = tr("Breite der Mail-Liste ändern"))
                    readingPane(Modifier.weight(1f), onBack = null)
                } else {
                    // Phone: the list, then the mail; back (or the system's back) returns to the list.
                    val showMail = reading && s.selected.isNotEmpty() && s.selected.size == 1
                    PlatformBackHandler(enabled = showMail) { reading = false }
                    if (showMail) readingPane(Modifier.fillMaxSize(), onBack = { reading = false })
                    else list(Modifier.fillMaxSize(), withFolderButton = true)
                }
            }
        }
        drag?.let { DragPill(it, origin) }
    }

    moving?.let { ids ->
        FolderPickerDialog(
            s.folders, ids.size,
            onPick = { f -> moving = null; act { model.move(ids, f) } },
            onNew = { moving = null; newFolderIn = s.folder; newFolder = true },
            onDismiss = { moving = null },
        )
    }
    if (newFolder) NewFolderDialog(
        s.folders, newFolderIn ?: s.folder,
        onCreate = { parent, path -> newFolder = false; act { model.createFolder(parent, path) } },
        onDismiss = { newFolder = false },
    )
    renaming?.let { f -> RenameFolderDialog(f, onRename = { name -> renaming = null; act { model.renameFolder(f, name) } }, onDismiss = { renaming = null }) }
}

/** The reading pane with the mail loaded in full (text, recipients, attachments). */
@Composable
private fun OpenMail(model: MailBoxModel, header: MailHeader, actions: ReadingActions, onBack: (() -> Unit)?, modifier: Modifier) {
    var body by remember(header.id) { mutableStateOf<MailBody?>(null) }
    var problem by remember(header.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(header.id) {
        runCatching { model.body(header.id) }
            .onSuccess { b -> if (b == null) problem = tr("Diese Mail gibt es nicht mehr (verschoben?).") else body = b }
            .onFailure { problem = Outlook.explain(it) }
    }
    ReadingPane(header, body, problem, actions, onBack, modifier)
}

/**
 * Übersicht: the connection, starts for Lora (what is important, sort, deadlines, free times, answer), today's and
 * tomorrow's appointments, the fixed sorting rules with „Anwenden“ (shows first what would move) and the protocol of
 * every change the app made in Outlook.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MailOverview(c: AppContainer, bridge: OutlookBridge?, state: OutlookState, agenda: List<CalendarEntry>?, onRetry: () -> Unit) {
    val scope = rememberCoroutineScope()
    val rules by c.mailRules.items.collectAsStateWithLifecycle()
    val log by c.outlookLog.items.collectAsStateWithLifecycle()
    var editRule by remember { mutableStateOf<MailRule?>(null) }
    var applyPlan by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var allLog by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            Modifier.widthIn(max = 860.dp).fillMaxWidth(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = LORA_BUTTON_ROOM),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { StatusCard(state, onRetry = onRetry) }
            // AP-68: only a model on this device reads the mails (data note, model, download offer).
            item { MailLocalModelCard(c) }
            item {
                AppCard {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(tr("Mit Lora"), style = MaterialTheme.typography.titleSmall)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            MAIL_STARTS.forEach { s ->
                                AssistChip(
                                    onClick = { newMailChat(tr(s.text), s.send) },
                                    label = { Text(tr(s.label)) },
                                    leadingIcon = { Icon(s.icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                    enabled = bridge != null,
                                )
                            }
                        }
                        HintText(tr("Lora löscht nie etwas und sendet nie: Antworten landen als Entwurf in Outlook, Einladungen öffnen sich dort zum Prüfen und Senden. Vor dem Verschieben fragt die App nach."))
                    }
                }
            }
            agenda?.let { list ->
                item {
                    AppCard {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.CalendarMonth, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(tr("Heute und morgen"), style = MaterialTheme.typography.titleSmall)
                            }
                            if (list.isEmpty()) HintText(tr("Keine Termine."))
                            list.take(10).forEach { e ->
                                Text(
                                    Outlook.span(e.start, e.end, e.allDay) + " · " + e.subject.ifBlank { tr("(ohne Titel)") } + if (e.location.isNotBlank()) " · ${e.location}" else "",
                                    style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
            item {
                AppCard {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Rule, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(tr("Sortierregeln"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            TextButton(onClick = { editRule = MailRule(name = "", folder = "") }, enabled = bridge != null) {
                                Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(tr("Regel"))
                            }
                        }
                        if (rules.isEmpty()) {
                            HintText(tr("Noch keine Regeln. Lege eine an oder bitte Lora: „Schlag mir Regeln zum Sortieren vor“. Regeln wirken nur, wenn du sie hier anwendest."))
                        }
                        rules.forEach { r ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(r.name, style = MaterialTheme.typography.bodyMedium)
                                    HintText(OutlookTools.ruleText(r) + if (r.moved > 0) " · " + tr("bisher") + " ${r.moved}" else "")
                                }
                                Switch(modifier = Modifier.a11yLabel(r.name), checked = r.enabled, onCheckedChange = { on -> c.mailRules.update(r.id) { it.copy(enabled = on) } })
                                IconButton(onClick = {
                                    c.mailRules.delete(r.id)
                                    Notices.undo(tr("Regel gelöscht"), restore = { c.mailRules.upsert(r) })
                                }) { Icon(Icons.Outlined.Delete, contentDescription = tr("Regel löschen")) }
                            }
                        }
                        if (rules.any { it.enabled } && bridge != null) {
                            OutlinedButton(enabled = !busy, onClick = {
                                busy = true
                                scope.launch {
                                    applyPlan = withContext(Dispatchers.IO) {
                                        runCatching { OutlookTools.applyRules(c, bridge, 200, dry = true, by = "Sie") }.getOrElse { Outlook.explain(it) }
                                    }
                                    busy = false
                                }
                            }) {
                                if (busy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                else Icon(Icons.Outlined.DriveFileMove, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(tr("Regeln anwenden …"))
                            }
                        }
                    }
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("Protokoll"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    if (log.size > 8) TextButton(onClick = { allLog = !allLog }) { Text(tr(if (allLog) "Weniger" else "Alle anzeigen")) }
                }
                HintText(tr("Alles, was die App in Outlook geändert hat – gelöscht oder gesendet wird nie etwas."))
            }
            val shownLog = log.sortedByDescending { it.at }.let { if (allLog) it else it.take(8) }
            if (shownLog.isEmpty()) item { HintText(tr("Noch nichts.")) }
            items(shownLog, key = { it.id }) { a -> LogRow(a) }
        }
    }

    editRule?.let { r -> RuleDialog(bridge, r, onDismiss = { editRule = null }, onSave = { rule -> c.mailRules.upsert(rule); editRule = null }) }

    applyPlan?.let { plan ->
        val canMove = plan.startsWith("Würde verschieben")
        AlertDialog(
            onDismissRequest = { applyPlan = null },
            title = { Text(tr("Regeln anwenden")) },
            text = { Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) { Text(plan, style = MaterialTheme.typography.bodySmall) } },
            confirmButton = {
                if (canMove && bridge != null) TextButton(onClick = {
                    applyPlan = null
                    busy = true
                    scope.launch {
                        val result = withContext(Dispatchers.IO) { runCatching { OutlookTools.applyRules(c, bridge, 200, dry = false, by = "Sie") }.getOrElse { Outlook.explain(it) } }
                        busy = false
                        Notices.info(result.lines().first())
                    }
                }) { Text(tr("Verschieben")) } else TextButton(onClick = { applyPlan = null }) { Text(tr("OK")) }
            },
            dismissButton = { if (canMove) TextButton(onClick = { applyPlan = null }) { Text(tr("Abbrechen")) } },
        )
    }
}

private data class MailStart(val label: String, val text: String, val send: Boolean, val icon: androidx.compose.ui.graphics.vector.ImageVector)

private val MAIL_STARTS = listOf(
    MailStart(trLater("Was ist seit gestern wichtig?"), trLater("Was ist seit gestern im Posteingang wichtig?"), true, Icons.Outlined.Inbox),
    MailStart(trLater("Posteingang sortieren"), trLater("Schau dir die letzten 100 Mails im Posteingang an und schlag mir Ordner und feste Regeln zum Sortieren vor. Noch nichts verschieben."), true, Icons.Outlined.DriveFileMove),
    MailStart(trLater("Fristen und Termine aus Mails"), trLater("Finde in den Mails der letzten 7 Tage Fristen und Termine und schlag vor, was ich als Termin oder Aufgabe übernehmen soll."), true, Icons.Outlined.TaskAlt),
    MailStart(trLater("Antwort entwerfen …"), trLater("Entwirf eine Antwort auf die Mail von "), false, Icons.Outlined.EditNote),
    MailStart(trLater("Freie Zeit finden …"), trLater("Finde einen gemeinsamen Termin (60 Minuten) in den nächsten zwei Wochen mit "), false, Icons.Outlined.EventAvailable),
)

@Composable
private fun StatusCard(state: OutlookState, onRetry: () -> Unit) {
    AppCard {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            LoraFigure(
                Modifier.size(104.dp, 104.dp / LORA_ASPECT), label = tr("Lora sortiert Briefe"),
                director = rememberLoraDirector(listOf(LoraAction.MAIL, LoraAction.FOLDER, LoraAction.CALENDAR), LoraAction.MAIL),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(if (Outlook.viaServer) tr("Mail über den Server") else tr("Klassisches Outlook"), style = MaterialTheme.typography.titleMedium)
                when (state) {
                    OutlookState.Connecting -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        HintText(tr("Verbinde mit Outlook … (startet es bei Bedarf im Hintergrund)"))
                    }
                    is OutlookState.Ready -> {
                        StatusPill(tr("Verbunden"), PillTone.SUCCESS)
                        HintText(state.accounts)
                    }
                    is OutlookState.Problem -> {
                        Text(state.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        if (Outlook.supported) TextButton(onClick = onRetry) { Text(tr("Erneut versuchen")) }
                    }
                }
            }
        }
    }
}

@Composable
private fun LogRow(a: OutlookAction) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.Top) {
        Text(Outlook.show(a.at), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(150.dp))
        Column(Modifier.weight(1f)) {
            Text(a.what + " · " + a.detail, style = MaterialTheme.typography.bodySmall)
            HintText(a.by)
        }
    }
}

/** A new rule: name, sender and/or subject part, target folder (from Outlook's folders). */
@Composable
private fun RuleDialog(bridge: OutlookBridge?, start: MailRule, onDismiss: () -> Unit, onSave: (MailRule) -> Unit) {
    var name by remember { mutableStateOf(start.name) }
    var from by remember { mutableStateOf(start.from) }
    var subject by remember { mutableStateOf(start.subject) }
    var folder by remember { mutableStateOf<MailFolder?>(null) }
    var folders by remember { mutableStateOf<List<MailFolder>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var menu by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (bridge == null) return@LaunchedEffect
        withContext(Dispatchers.IO) { runCatching { bridge.folders() } }.onSuccess { folders = it }.onFailure { error = Outlook.explain(it) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Sortierregel")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text(tr("Name")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(from, { from = it }, label = { Text(tr("Absender enthält")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(subject, { subject = it }, label = { Text(tr("Betreff enthält")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Box {
                    OutlinedButton(onClick = { menu = true }, enabled = folders != null, modifier = Modifier.fillMaxWidth()) {
                        Text(folder?.path?.trim('\\') ?: if (folders == null && error == null) tr("Ordner werden geladen …") else tr("Zielordner wählen"), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        folders.orEmpty().take(300).forEach { f ->
                            DropdownMenuItem(text = { Text(f.path.trim('\\'), maxLines = 1, overflow = TextOverflow.Ellipsis) }, onClick = { folder = f; menu = false })
                        }
                    }
                }
                error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                HintText(tr("Die Regel verschiebt nur, wenn du unter „Regeln anwenden“ zustimmst."))
            }
        },
        confirmButton = {
            val f = folder
            Button(
                enabled = f != null && (from.isNotBlank() || subject.isNotBlank()),
                onClick = { f?.let { onSave(start.copy(name = name.ifBlank { from.ifBlank { subject } }.trim(), from = from.trim(), subject = subject.trim(), folder = it.id, folderName = it.path.trim('\\'))) } },
            ) { Text(tr("Speichern")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Abbrechen")) } },
    )
}
