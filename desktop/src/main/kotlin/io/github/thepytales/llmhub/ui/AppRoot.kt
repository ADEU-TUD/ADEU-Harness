package io.github.thepytales.llmhub.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.foundation.layout.width
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.CrashLog
import io.github.thepytales.llmhub.desktop.DesktopActions
import io.github.thepytales.llmhub.desktop.Toasts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.net.URLDecoder
import java.net.URLEncoder

private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

/** Route of a chat: an existing one, or a new one (optionally with model or persona template). */
fun chatRoute(id: String? = null, model: String? = null, persona: String? = null): String {
    val args = listOfNotNull(id?.let { "id=${enc(it)}" }, model?.let { "model=${enc(it)}" }, persona?.let { "persona=${enc(it)}" })
    return if (args.isEmpty()) "chat" else "chat?" + args.joinToString("&")
}

/** A screen on the back stack; owns the view models of that screen (like a NavBackStackEntry). */
private class Entry(val id: Long, val route: String) : ViewModelStoreOwner {
    override val viewModelStore = ViewModelStore()

    val name: String = route.substringBefore('?')

    /** "chat?id=…" → query arguments, "task/42" → {id: 42}, "settings/api" → {section: api}. */
    val args: Map<String, String> = buildMap {
        route.substringAfter('?', "").split('&').filter { '=' in it }.forEach {
            put(it.substringBefore('='), URLDecoder.decode(it.substringAfter('='), "UTF-8"))
        }
        val parts = name.split('/', limit = 2)
        if (parts.size == 2) put(if (parts[0] == "settings") "section" else "id", parts[1])
    }

    val base: String = name.substringBefore('/')
}

/** A simple back stack (Android uses navigation-compose); screens keep their state when you go back. */
private class Navigator(private val holder: SaveableStateHolder) {
    private var nextId = 0L
    val stack = mutableStateListOf(Entry(nextId++, "chat"))
    val current: Entry get() = stack.last()

    fun navigate(route: String) {
        stack.add(Entry(nextId++, route))
    }

    /** Opens a chat as the new root of the back stack (like switching chats in ChatGPT). */
    fun openChat(route: String) {
        val old = stack.toList()
        stack.clear()
        stack.add(Entry(nextId++, route))
        old.forEach(::dispose)
    }

    fun popBackStack(): Boolean {
        if (stack.size <= 1) return false
        dispose(stack.removeAt(stack.lastIndex))
        return true
    }

    private fun dispose(e: Entry) {
        e.viewModelStore.clear()
        holder.removeState(e.id)
    }
}

/** Screen and back requests from outside the UI (notifications, tray menu, keyboard shortcuts). */
object Navigation {
    val openChat = MutableStateFlow<String?>(null)
    /** "chat" (new chat), "drawer", "back" or a route such as "settings". */
    val openRoute = MutableStateFlow<String?>(null)

    /**
     * A screen that needs the whole width (the video editor, AP-70) folds the side menu in while it is open; [wideMenu]
     * is whether the menu button (or Strg+B) shows it there anyway. The screen sets both.
     */
    val wide = MutableStateFlow(false)
    val wideMenu = MutableStateFlow(false)
}

@Composable
fun AppRoot(c: AppContainer, openChat: MutableStateFlow<String?>, openRoute: MutableStateFlow<String?>) {
    val holder = rememberSaveableStateHolder()
    val nav = remember { Navigator(holder) }
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val entry = nav.current
    val onChat = entry.base == "chat"
    var currentChatId by remember { mutableStateOf<String?>(null) }
    /** Wide windows show the side menu next to the chat; it can be hidden with the menu button. */
    var sidebar by remember { mutableStateOf(true) }
    val layout = remember { object { var wide = true } }

    LaunchedEffect(Unit) {
        if (c.settings.current.hasAnyApiKey && !io.github.thepytales.llmhub.data.Places.allLocal(c)) c.models.refresh()
    }

    val requested by openChat.collectAsStateWithLifecycle()
    LaunchedEffect(requested) {
        requested?.let { id ->
            nav.openChat(chatRoute(id))
            openChat.value = null
        }
    }

    fun closeDrawer() = scope.launch { drawer.close() }
    fun openDrawer() {
        if (!layout.wide) scope.launch { drawer.open() }
        else if (Navigation.wide.value) Navigation.wideMenu.value = !Navigation.wideMenu.value
        else sidebar = !sidebar
    }
    val wideScreen by Navigation.wide.collectAsStateWithLifecycle()
    val wideMenu by Navigation.wideMenu.collectAsStateWithLifecycle()

    var palette by remember { mutableStateOf(false) }
    var shortcuts by remember { mutableStateOf(false) }
    val route by openRoute.collectAsStateWithLifecycle()
    LaunchedEffect(route) {
        route?.let { r ->
            when (r) {
                "drawer" -> openDrawer()
                "palette" -> palette = true
                "lora" -> LoraHelper.toggle()
                "shortcuts" -> shortcuts = true
                "chat" -> nav.openChat(chatRoute())
                // Escape: close the menu, otherwise go back.
                "back" -> if (drawer.isOpen) closeDrawer() else nav.popBackStack()
                else -> nav.navigate(r)
            }
            openRoute.value = null
        }
    }

    // A PDF to open in the PDF-Werkzeugkasten (evidence of the Quellenmodus, a document card); the screen takes it.
    val pdfRequest by PdfHandoff.request.collectAsStateWithLifecycle()
    LaunchedEffect(pdfRequest) {
        if (pdfRequest != null && nav.current.base != "pdf") nav.navigate("pdf")
    }

    // A design to open in the editor (Erstellen, a card under a chat answer, AP-24).
    val designRequest by DesignHandoff.request.collectAsStateWithLifecycle()
    LaunchedEffect(designRequest) {
        designRequest?.let { id ->
            if (nav.current.route != "design/$id") nav.navigate("design/$id")
            DesignHandoff.request.value = null
        }
    }

    // "In Chat senden" from the document editor, the browser beside the chat (AP-14) …: open that chat (or a new one).
    val chatRequest by ChatHandoff.open.collectAsStateWithLifecycle()
    LaunchedEffect(chatRequest) {
        chatRequest?.let { id ->
            nav.openChat(chatRoute(id.ifBlank { null }))
            ChatHandoff.open.value = null
        }
    }

    // Documents opened from a card, the literature screen or a chat answer (the chat shows them beside it when wide).
    val docRequest by DocsHandoff.request.collectAsStateWithLifecycle()
    LaunchedEffect(docRequest) {
        docRequest?.let { id ->
            nav.navigate("doc/$id")
            DocsHandoff.request.value = null
        }
    }

    // Shows what went wrong if the app crashed last time.
    val context = LocalContext.current
    var crash by remember { mutableStateOf(CrashLog.take(context)) }
    crash?.let { report ->
        CrashDialog(report, onDismiss = { crash = null })
    }
    // A new version: asks at the start whether to install it (see Updates).
    if (crash == null) UpdatePrompt(c)
    // The recognition model of the pseudonymization, offered once (AP-67).
    if (crash == null) PiiModelPrompt(c)
    // AP-71: the command model for Lora and Strg+K, offered the same way.
    if (crash == null) CommandModelPrompt(c)
    if (crash == null) ModelsIntroDialog(c)

    // Results that arrive while the app is open appear as a banner at the top.
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        c.events.collect { e ->
            snackbar.currentSnackbarData?.dismiss()
            val result = snackbar.showSnackbar(
                NoticeVisuals("${e.title}: ${e.message}", actionLabel = tr("Ansehen"), duration = SnackbarDuration.Long, tone = NoticeTone.DONE),
            )
            if (result == SnackbarResult.ActionPerformed) nav.openChat(chatRoute(e.chatId))
        }
    }
    // Short hints (Android: Toast) at the bottom.
    val toasts = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        // A new hint replaces the previous one, also one still waiting: cancelling its showSnackbar takes it out of
        // the queue (before, quick hints such as the zoom level queued up and were shown one after another).
        var last: kotlinx.coroutines.Job? = null
        Toasts.messages.collect { text ->
            last?.cancel()
            toasts.currentSnackbarData?.dismiss()
            last = launch { toasts.showSnackbar(text, duration = SnackbarDuration.Short) }
        }
    }
    // Errors with "Erneut versuchen" and deletions with "Rückgängig" (see Notices); they queue up.
    LaunchedEffect(Unit) {
        Notices.flow.collect { n ->
            launch {
                val result = toasts.showSnackbar(n.visuals())
                if (result == SnackbarResult.ActionPerformed) n.onAction?.invoke() else n.onTimeout?.invoke()
            }
        }
    }

    if (palette) {
        CommandPalette(
            c,
            onNavigate = { r -> if (r == "chat") nav.openChat(chatRoute()) else nav.navigate(r) },
            onOpenChat = { id -> nav.openChat(chatRoute(id)) },
            onDismiss = { palette = false },
        )
    }
    if (shortcuts) ShortcutsDialog(onDismiss = { shortcuts = false })
    // Lora (AP-07, AP-34): answers wishes, leads to places, and the two questions of the first start. She knows where
    // the user is, so her suggestions fit the screen.
    LoraHelperHost(c) { r -> if (r == "chat" || r.startsWith("chat?")) nav.openChat(r) else nav.navigate(r) }
    LaunchedEffect(entry.route) { LoraHelper.route.value = entry.route }

    /** Widths of the side menu and the panels at the right; the lines beside them can be dragged (PanelResizer). */
    val widths = remember { PanelWidths.of(c) }
    val menu: @Composable () -> Unit = {
        AppDrawer(
            width = widths.menu.value,
            c = c,
            currentChatId = currentChatId,
            onOpenChat = { id -> nav.openChat(chatRoute(id)); closeDrawer() },
            onNewChat = { nav.openChat(chatRoute()); closeDrawer() },
            onNavigate = { r -> nav.navigate(r); closeDrawer() },
        )
    }

    val screen: @Composable () -> Unit = {
        holder.SaveableStateProvider(entry.id) {
            CompositionLocalProvider(LocalViewModelStoreOwner provides entry) {
                Screen(
                    c, entry, nav,
                    onOpenDrawer = ::openDrawer,
                    onChatShown = { currentChatId = it },
                )
            }
        }
    }

    // Browser beside the chat (AP-14): web links open as tabs in a panel at the right instead of the system browser.
    val settingsNow by c.settings.settings.collectAsStateWithLifecycle()
    val systemUri = LocalUriHandler.current
    val uriHandler = remember(systemUri) { PanelUriHandler(systemUri) { c.settings.current.browserPanel } }
    SideEffect { Browser.chatId = currentChatId?.takeIf { entry.route.startsWith("chat") } }
    LaunchedEffect(settingsNow.browserPanel) { if (!settingsNow.browserPanel) Browser.open = false }
    CompositionLocalProvider(LocalUriHandler provides uriHandler) {
    BoxWithConstraints(Modifier.fillMaxSize().trackLoraGaze()) {
        val wide = maxWidth >= 1000.dp
        layout.wide = wide
        val windowWidth = maxWidth
        SideEffect { widths.fitTo(windowWidth) }
        if (wide) {
            Row(Modifier.fillMaxSize()) {
                AnimatedVisibility(if (wideScreen) wideMenu else sidebar, enter = expandHorizontally(), exit = shrinkHorizontally()) {
                    Row(Modifier.fillMaxHeight()) {
                        menu()
                        // The line beside the side menu: drag for wider or narrower (240–520 dp), double-click resets.
                        PanelResizer(widths.menu, panelLeft = true, label = tr("Breite der Seitenleiste ändern"))
                    }
                }
                Box(Modifier.weight(1f)) { screen() }
                // The browser beside the chat (AP-14) and Lora's panel; the app gets narrower instead of covered.
                BrowserSidePanel(c)
                LoraSidePanel(c)
            }
        } else {
            ModalNavigationDrawer(drawerState = drawer, gesturesEnabled = onChat, drawerContent = menu) { screen() }
            BrowserOverlay(c)
            LoraSheet(c)
        }
        // Lora flying from her panel to the place she leads to.
        LoraFlightLayer()
        // Touren (AP-41): the offer on the first visit of a tool and the step card.
        LoraTourLayer(c)
        SnackbarHost(
            snackbar,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 56.dp)
                .widthIn(max = 640.dp),
        ) { data -> NoticeBar(data) }
        SnackbarHost(
            toasts,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 120.dp)
                .widthIn(max = 640.dp),
        ) { data -> if (data.visuals is NoticeVisuals) NoticeBar(data) else Snackbar(data, shape = RoundedCornerShape(14.dp)) }
    }
    }
}

@Composable
private fun Screen(c: AppContainer, entry: Entry, nav: Navigator, onOpenDrawer: () -> Unit, onChatShown: (String?) -> Unit) {
    val args = entry.args
    val back: () -> Unit = { nav.popBackStack() }
    when (entry.base) {
        "chat" -> {
            // Mehrere Fenster (AP-84): a chat open in a window of its own is written only there.
            val inWindow by ExtraWindows.open.collectAsStateWithLifecycle()
            val id = args["id"]
            if (id != null && inWindow.any { it.kind == ExtraWindows.Kind.CHAT && it.target == id }) {
                ChatInOtherWindow(id)
                return
            }
            val vm: ChatViewModel = viewModel { ChatViewModel(c, args["id"], args["model"], args["persona"]) }
            val conv by vm.state.collectAsStateWithLifecycle()
            LaunchedEffect(conv?.id) { onChatShown(conv?.id) }
            ChatScreen(
                c = c,
                vm = vm,
                onOpenDrawer = onOpenDrawer,
                onNewChat = { nav.openChat(chatRoute()) },
                onNavigate = { nav.navigate(it) },
            )
        }
        "models" -> ModelsScreen(c = c, onBack = back, onStartChat = { model -> nav.openChat(chatRoute(model = model)) })
        "images" -> ImagesScreen(c, onBack = back, onOpenChat = { nav.openChat(chatRoute()) })
        "compare" -> CompareScreen(c, onBack = back, onOpenChat = { id -> nav.openChat(chatRoute(id)) })
        "templates" -> TemplatesScreen(c, onBack = back, onUse = { t -> nav.openChat(chatRoute(persona = t.id)) })
        "tasks" -> TasksScreen(c, onBack = back, onEdit = { id -> nav.navigate("task/$id") }, onOpenChat = { id -> nav.openChat(chatRoute(id)) })
        "task" -> TaskEditScreen(c, args["id"] ?: "new", onBack = back)
        "usage" -> UsageScreen(c, onBack = back)
        "local-setup" -> LocalSetupScreen(c, onBack = back, onNavigate = { nav.navigate(it) })
        "local-models" -> LocalModelsScreen(c, onBack = back, onTry = { model -> nav.openChat(chatRoute(model = model)) })
        "transcripts" -> TranscriptsScreen(c, onBack = back, onOpen = { id -> nav.navigate("transcript/$id") }, onNavigate = { nav.navigate(it) })
        "transcript" -> TranscriptScreen(c, args["id"] ?: "", onBack = back, onOpenChat = { nav.openChat(chatRoute()) })
        "environments" -> EnvironmentsScreen(c, onBack = back, onEdit = { id -> nav.navigate("environment/$id") })
        "environment" -> EnvironmentEditScreen(c, args["id"] ?: "new", onBack = back)
        "settings" -> SettingsScreen(c, section = args["section"], onBack = back, onNavigate = { nav.navigate(it) })
        "literature" -> LiteratureScreen(c, onBack = back, onOpenChat = { nav.openChat(chatRoute()) }, onSettings = { nav.navigate("settings/literature") })
        "docs", "files" -> DriveScreen(c, onBack = back, onOpenDoc = { id -> nav.navigate("doc/$id") }, onOpenChat = { id -> nav.openChat(chatRoute(id)) }, onNavigate = { nav.navigate(it) })
        "doc" -> DocScreen(c, args["id"] ?: "", onBack = back, onOpenChat = { id -> nav.openChat(chatRoute(id)) })
        "vault" -> VaultScreen(c, onBack = back, onOpenChat = { id -> nav.openChat(chatRoute(id)) })
        "pdf" -> PdfToolScreen(c, onBack = back, onOpenChat = { nav.openChat(chatRoute()) })
        // Dokumenten-Scanner (AP-62).
        "scan" -> ScanScreen(c, onBack = back, onNavigate = { nav.navigate(it) })
        "games" -> GamesScreen(c, onBack = back)
        "mail" -> MailScreen(c, onBack = back)
        "batches" -> BatchScreen(c, onBack = back)
        "proposals" -> ProposalScreen(c, onBack = back)
        // The Kreativbereich became part of „Erstellen“ (AP-24).
        "creative" -> TemplatesScreen(c, onBack = back, onUse = { t -> nav.openChat(chatRoute(persona = t.id)) })
        "design" -> DesignEditor(c, args["id"] ?: "", onClose = back)
        // Diagramme und Whiteboard (AP-61).
        "whiteboard" -> WhiteboardScreen(c, args["id"] ?: "", onBack = back)
        "video" -> VideoScreen(c, onBack = back)
        // Server-Terminal (AP-118).
        "server" -> ServerTerminalScreen(c, onBack = back)
        else -> LaunchedEffect(entry.id) { nav.popBackStack() }
    }
}

/** Crash report of the last start with buttons to copy it or open the data folder. */
@Composable
private fun CrashDialog(report: String, onDismiss: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Lora ist abgestürzt")) },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                Text(
                    tr("Beim letzten Mal ist die App abgestürzt. Schick den Bericht gern an den Entwickler, damit der Fehler behoben werden kann."),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(report.take(6000), fontFamily = FontFamily.Monospace, fontSize = 10.sp, modifier = Modifier.padding(top = 8.dp))
            }
        },
        confirmButton = {
            TextButton(onClick = {
                DesktopActions.share(report)
                onDismiss()
            }) { Text(tr("Kopieren & schließen")) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { clipboard.setText(AnnotatedString(report)) }) { Text(tr("Kopieren")) }
                TextButton(onClick = onDismiss) { Text(tr("Schließen")) }
            }
        },
    )
}

/** The chat is open in a window of its own (AP-84): a note with „Zum Fenster“ and „Hierher holen“ instead of a second input. */
@Composable
private fun ChatInOtherWindow(id: String) {
    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
        androidx.compose.foundation.layout.Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally, verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)) {
            Text(tr("Dieser Chat ist in einem eigenen Fenster offen."), style = MaterialTheme.typography.titleMedium)
            androidx.compose.foundation.layout.Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                Button(onClick = { WindowHooks.focusChat(id) }) { Text(tr("Zum Fenster")) }
                OutlinedButton(onClick = { WindowHooks.closeChat(id) }) { Text(tr("Hierher holen")) }
            }
        }
    }
}
