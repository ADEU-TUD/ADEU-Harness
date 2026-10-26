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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.width
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
        if (c.settings.current.hasAnyApiKey) c.models.refresh()
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
        if (layout.wide) sidebar = !sidebar else scope.launch { drawer.open() }
    }

    var palette by remember { mutableStateOf(false) }
    var shortcuts by remember { mutableStateOf(false) }
    val route by openRoute.collectAsStateWithLifecycle()
    LaunchedEffect(route) {
        route?.let { r ->
            when (r) {
                "drawer" -> openDrawer()
                "palette" -> palette = true
                "shortcuts" -> shortcuts = true
                "chat" -> nav.openChat(chatRoute())
                // Escape: close the menu, otherwise go back.
                "back" -> if (drawer.isOpen) closeDrawer() else nav.popBackStack()
                else -> nav.navigate(r)
            }
            openRoute.value = null
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

    // Results that arrive while the app is open appear as a banner at the top.
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        c.events.collect { e ->
            snackbar.currentSnackbarData?.dismiss()
            val result = snackbar.showSnackbar(
                message = "${e.title}: ${e.message}",
                actionLabel = "Ansehen",
                withDismissAction = true,
                duration = SnackbarDuration.Long,
            )
            if (result == SnackbarResult.ActionPerformed) nav.openChat(chatRoute(e.chatId))
        }
    }
    // Short hints (Android: Toast) at the bottom.
    val toasts = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        Toasts.messages.collect { text ->
            toasts.currentSnackbarData?.dismiss()
            launch { toasts.showSnackbar(text, duration = SnackbarDuration.Short) }
        }
    }
    // Errors with "Erneut versuchen" and deletions with "Rückgängig" (see Notices); they queue up.
    LaunchedEffect(Unit) {
        Notices.flow.collect { n ->
            launch {
                val result = toasts.showSnackbar(
                    message = n.text,
                    actionLabel = n.action,
                    withDismissAction = n.error || n.action != null,
                    duration = if (n.error || n.action != null) SnackbarDuration.Long else SnackbarDuration.Short,
                )
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

    /** Width of the side menu; the line beside it can be dragged. */
    var menuWidth by remember { mutableStateOf(318.dp) }
    val density = LocalDensity.current
    val menu: @Composable () -> Unit = {
        AppDrawer(
            width = menuWidth,
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

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 1000.dp
        layout.wide = wide
        if (wide) {
            Row(Modifier.fillMaxSize()) {
                AnimatedVisibility(sidebar, enter = expandHorizontally(), exit = shrinkHorizontally()) {
                    Row(Modifier.fillMaxHeight()) {
                        menu()
                        // Drag handle: makes the side menu wider or narrower (240–520 dp).
                        Box(
                            Modifier
                                .fillMaxHeight()
                                .width(6.dp)
                                .pointerHoverIcon(PointerIcon(java.awt.Cursor(java.awt.Cursor.E_RESIZE_CURSOR)))
                                .draggable(
                                    orientation = Orientation.Horizontal,
                                    state = rememberDraggableState { delta ->
                                        menuWidth = (menuWidth + with(density) { delta.toDp() }).coerceIn(240.dp, 520.dp)
                                    },
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        }
                    }
                }
                Box(Modifier.weight(1f)) { screen() }
            }
        } else {
            ModalNavigationDrawer(drawerState = drawer, gesturesEnabled = onChat, drawerContent = menu) { screen() }
        }
        SnackbarHost(
            snackbar,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 56.dp)
                .widthIn(max = 640.dp),
        ) { data ->
            Snackbar(
                data,
                shape = RoundedCornerShape(16.dp),
                containerColor = LocalChatLook.current.card,
                contentColor = MaterialTheme.colorScheme.onSurface,
                actionColor = MaterialTheme.colorScheme.primary,
                dismissActionContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        SnackbarHost(
            toasts,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 120.dp)
                .widthIn(max = 640.dp),
        ) { data -> Snackbar(data, shape = RoundedCornerShape(14.dp)) }
    }
}

@Composable
private fun Screen(c: AppContainer, entry: Entry, nav: Navigator, onOpenDrawer: () -> Unit, onChatShown: (String?) -> Unit) {
    val args = entry.args
    val back: () -> Unit = { nav.popBackStack() }
    when (entry.base) {
        "chat" -> {
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
        "local-models" -> LocalModelsScreen(c, onBack = back, onTry = { model -> nav.openChat(chatRoute(model = model)) })
        "transcripts" -> TranscriptsScreen(c, onBack = back, onOpen = { id -> nav.navigate("transcript/$id") }, onNavigate = { nav.navigate(it) })
        "transcript" -> TranscriptScreen(c, args["id"] ?: "", onBack = back, onOpenChat = { nav.openChat(chatRoute()) })
        "environments" -> EnvironmentsScreen(c, onBack = back, onEdit = { id -> nav.navigate("environment/$id") })
        "environment" -> EnvironmentEditScreen(c, args["id"] ?: "new", onBack = back)
        "settings" -> SettingsScreen(c, section = args["section"], onBack = back, onNavigate = { nav.navigate(it) })
        "literature" -> LiteratureScreen(c, onBack = back, onOpenChat = { nav.openChat(chatRoute()) }, onSettings = { nav.navigate("settings/literature") })
        "docs" -> DocsListScreen(c, onBack = back, onOpen = { id -> nav.navigate("doc/$id") })
        "doc" -> DocScreen(c, args["id"] ?: "", onBack = back, onOpenChat = { id -> nav.openChat(chatRoute(id)) })
        else -> LaunchedEffect(entry.id) { nav.popBackStack() }
    }
}

/** Crash report of the last start with buttons to copy it or open the data folder. */
@Composable
private fun CrashDialog(report: String, onDismiss: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ADEU LLM ist abgestürzt") },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                Text(
                    "Beim letzten Mal ist die App abgestürzt. Schick den Bericht gern an den Entwickler, damit der Fehler behoben werden kann.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(report.take(6000), fontFamily = FontFamily.Monospace, fontSize = 10.sp, modifier = Modifier.padding(top = 8.dp))
            }
        },
        confirmButton = {
            TextButton(onClick = {
                DesktopActions.share(report)
                onDismiss()
            }) { Text("Kopieren & schließen") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { clipboard.setText(AnnotatedString(report)) }) { Text("Kopieren") }
                TextButton(onClick = onDismiss) { Text("Schließen") }
            }
        },
    )
}
