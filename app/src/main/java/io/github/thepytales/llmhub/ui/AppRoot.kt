package io.github.thepytales.llmhub.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.key
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.navigation.NavBackStackEntry
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.CrashLog
import io.github.thepytales.llmhub.tasks.Notifier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.net.URLEncoder

private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

/** Route of a chat: an existing one, or a new one (optionally with model or persona template). */
fun chatRoute(id: String? = null, model: String? = null, persona: String? = null): String {
    val args = listOfNotNull(id?.let { "id=${enc(it)}" }, model?.let { "model=${enc(it)}" }, persona?.let { "persona=${enc(it)}" })
    return if (args.isEmpty()) "chat" else "chat?" + args.joinToString("&")
}

/** Opens a chat as the new root of the back stack (like switching chats in ChatGPT). */
private fun NavHostController.openChat(route: String) = navigate(route) {
    popUpTo(graph.id) { inclusive = true }
    launchSingleTop = true
}

/**
 * "Zurück" from the screen of [from]. Ignored if that screen is no longer on top: a second tap while it slides away
 * (the burger menu of the chat sits exactly where the back arrow was) would otherwise also remove the chat and
 * leave an empty, dark window. The chat at the bottom is never removed.
 */
private fun NavHostController.back(from: NavBackStackEntry) {
    if (!isOnTop(currentBackStackEntry?.id, from.id)) return
    if (previousBackStackEntry == null) openChat(chatRoute()) else popBackStack()
}

/** Opens [route] from the screen of [from]; double taps and taps on a screen that is leaving are ignored. */
private fun NavHostController.go(from: NavBackStackEntry, route: String) {
    if (isOnTop(currentBackStackEntry?.id, from.id)) navigate(route) { launchSingleTop = true }
}

/** Wide screens (tablets in landscape, foldables): the side menu stays open next to the content. */
private const val WIDE_DP = 840

@Composable
fun AppRoot(c: AppContainer, openChat: MutableStateFlow<String?>, openRoute: MutableStateFlow<String?>) {
    val nav = rememberNavController()
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    var currentChatId by remember { mutableStateOf<String?>(null) }
    val wide = LocalConfiguration.current.screenWidthDp >= WIDE_DP

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

    val requestedRoute by openRoute.collectAsStateWithLifecycle()
    LaunchedEffect(requestedRoute) {
        requestedRoute?.let { r ->
            when (r) {
                "drawer" -> if (!wide) drawer.open()
                "lora" -> LoraHelper.open()
                "chat" -> nav.openChat(chatRoute())
                // Shared from another app: an empty new chat on screen takes it right away (ChatPlatform); if it is
                // still waiting after a moment, a new chat is opened for it. Before, a new chat was always opened –
                // and when the start chat had already taken the files, they disappeared with it.
                // (Its own coroutine: resetting openRoute restarts this effect, which would cancel the wait.)
                "share" -> scope.launch {
                    kotlinx.coroutines.delay(400)
                    if (io.github.thepytales.llmhub.MainActivity.shared.value != null) runCatching { nav.openChat(chatRoute()) }
                }
                else -> runCatching { nav.navigate(r) { launchSingleTop = true } }
            }
            openRoute.value = null
        }
    }

    // Documents opened from a card, the literature screen or a chat answer (the chat shows them beside it when wide).
    val docRequest by DocsHandoff.request.collectAsStateWithLifecycle()
    LaunchedEffect(docRequest) {
        docRequest?.let { id ->
            runCatching { nav.navigate("doc/$id") { launchSingleTop = true } }
            DocsHandoff.request.value = null
        }
    }

    // A PDF to open in the PDF-Werkzeugkasten (evidence of the Quellenmodus, a document card); the screen takes it.
    val pdfRequest by PdfHandoff.request.collectAsStateWithLifecycle()
    LaunchedEffect(pdfRequest) {
        if (pdfRequest != null && route != "pdf") runCatching { nav.navigate("pdf") { launchSingleTop = true } }
    }

    // "In Chat senden" from the document editor: open that chat (or a new one).
    val chatRequest by ChatHandoff.open.collectAsStateWithLifecycle()
    LaunchedEffect(chatRequest) {
        chatRequest?.let { id ->
            runCatching { nav.openChat(chatRoute(id.ifBlank { null })) }
            ChatHandoff.open.value = null
        }
    }

    // Safety net: should the back stack ever run empty, a new chat appears instead of an empty window.
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(entry) {
        if (entry != null) shown = true else if (shown) nav.openChat(chatRoute())
    }
    // The menu never stays open (or half open) on a screen without it.
    LaunchedEffect(route, wide) {
        if (wide || !isTopLevel(route)) drawer.snapTo(DrawerValue.Closed)
    }

    // Shows what went wrong if the app crashed last time.
    val context = LocalContext.current
    var crash by remember { mutableStateOf(CrashLog.take(context)) }
    crash?.let { report ->
        CrashDialog(report, onDismiss = { crash = null })
    }
    // A new version: asks at the start whether to install it (see Updates).
    if (crash == null) UpdatePrompt(c)
    // Lora (AP-07, AP-34): answers wishes, leads to places, and the two questions of the first start.
    LoraHelperHost(c) { r ->
        runCatching { if (r == "chat" || r.startsWith("chat?")) nav.openChat(r) else nav.navigate(r) { launchSingleTop = true } }
    }
    // Where the user is, so Lora's suggestions fit the screen ("settings/{section}" with its section filled in).
    LaunchedEffect(entry) {
        val e = entry ?: return@LaunchedEffect
        LoraHelper.route.value = (e.destination.route ?: "chat").replace(Regex("\\{(\\w+)\\}")) { m -> e.arguments?.getString(m.groupValues[1]).orEmpty() }
    }
    BackHandler(enabled = LoraHelper.isOpen.collectAsStateWithLifecycle().value) { LoraHelper.close() }

    BackHandler(enabled = drawer.isOpen) { closeDrawer() }

    // Ask once for notifications (Android 13+): finished answers and task results arrive as notifications.
    val notifyPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 && !Notifier.canNotify(context)) {
            val prefs = context.getSharedPreferences("ui_state", Context.MODE_PRIVATE)
            if (!prefs.getBoolean("askedNotifications", false)) {
                prefs.edit().putBoolean("askedNotifications", true).apply()
                notifyPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // Results that arrive while the app is open appear as a banner at the top.
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        c.events.collect { e ->
            snackbar.currentSnackbarData?.dismiss()
            val result = snackbar.showSnackbar(
                message = "${e.title}: ${e.message}",
                actionLabel = tr("Ansehen"),
                withDismissAction = true,
                duration = SnackbarDuration.Long,
            )
            if (result == SnackbarResult.ActionPerformed) nav.openChat(chatRoute(e.chatId))
        }
    }
    // Errors and "Rückgängig" use the same banner; they wait for each other instead of replacing one another.
    LaunchedEffect(Unit) {
        Notices.flow.collect { n ->
            launch {
                val result = snackbar.showSnackbar(
                    message = n.text,
                    actionLabel = n.action,
                    withDismissAction = true,
                    duration = if (n.error || n.action != null) SnackbarDuration.Long else SnackbarDuration.Short,
                )
                if (result == SnackbarResult.ActionPerformed) n.onAction?.invoke() else n.onTimeout?.invoke()
            }
        }
    }

    val menu: @Composable () -> Unit = {
        AppDrawer(
            c = c,
            currentChatId = currentChatId,
            onOpenChat = { id -> nav.openChat(chatRoute(id)); closeDrawer() },
            onNewChat = { nav.openChat(chatRoute()); closeDrawer() },
            onNavigate = { r ->
                // Closed at once: the next screen may not have a menu to close it from.
                scope.launch { drawer.snapTo(DrawerValue.Closed) }
                nav.navigate(r) { launchSingleTop = true }
            },
        )
    }

    val screens: @Composable () -> Unit = {
        NavHost(
            navController = nav,
            startDestination = "chat?id={id}&model={model}&persona={persona}",
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        ) {
            composable(
                "chat?id={id}&model={model}&persona={persona}",
                arguments = listOf(
                    navArgument("id") { type = NavType.StringType; nullable = true; defaultValue = null },
                    navArgument("model") { type = NavType.StringType; nullable = true; defaultValue = null },
                    navArgument("persona") { type = NavType.StringType; nullable = true; defaultValue = null },
                ),
            ) { back ->
                val args = back.arguments
                val vm: ChatViewModel = viewModel {
                    ChatViewModel(c, args?.getString("id"), args?.getString("model"), args?.getString("persona"))
                }
                val conv by vm.state.collectAsStateWithLifecycle()
                LaunchedEffect(conv?.id) { currentChatId = conv?.id }
                ChatScreen(
                    c = c,
                    vm = vm,
                    onOpenDrawer = if (wide) null else ({ scope.launch { drawer.open() } }),
                    onNewChat = { nav.openChat(chatRoute()) },
                    onNavigate = { nav.go(back, it) },
                )
            }
            composable("models") { back ->
                ModelsScreen(c = c, onBack = { nav.back(back) }, onStartChat = { model -> nav.openChat(chatRoute(model = model)) })
            }
            composable("images") { back -> ImagesScreen(c, onBack = { nav.back(back) }, onOpenChat = { nav.openChat(chatRoute()) }) }
            composable("compare") { back ->
                CompareScreen(c, onBack = { nav.back(back) }, onOpenChat = { id -> nav.openChat(chatRoute(id)) })
            }
            composable("templates") { back ->
                TemplatesScreen(
                    c, onBack = { nav.back(back) },
                    onUse = { t -> nav.openChat(chatRoute(persona = t.id)) },
                )
            }
            composable("tasks") { back ->
                TasksScreen(c, onBack = { nav.back(back) }, onEdit = { id -> nav.go(back, "task/$id") }, onOpenChat = { id -> nav.openChat(chatRoute(id)) })
            }
            composable("task/{id}") { back ->
                TaskEditScreen(c, back.arguments?.getString("id") ?: "new", onBack = { nav.back(back) })
            }
            composable("usage") { back -> UsageScreen(c, onBack = { nav.back(back) }) }
            composable("local-models") { back ->
                LocalModelsScreen(c, onBack = { nav.back(back) }, onTry = { model -> nav.openChat(chatRoute(model = model)) })
            }
            composable("transcripts") { back ->
                TranscriptsPane(
                    c,
                    onBack = { nav.back(back) },
                    onOpen = { id -> nav.go(back, "transcript/$id") },
                    onNavigate = { nav.go(back, it) },
                    onOpenChat = { nav.openChat(chatRoute()) },
                )
            }
            composable("transcript/{id}") { back ->
                TranscriptScreen(c, back.arguments?.getString("id").orEmpty(), onBack = { nav.back(back) }, onOpenChat = { nav.openChat(chatRoute()) })
            }
            composable("environments") { back ->
                EnvironmentsScreen(c, onBack = { nav.back(back) }, onEdit = { id -> nav.go(back, "environment/$id") })
            }
            composable("environment/{id}") { back ->
                EnvironmentEditScreen(c, back.arguments?.getString("id") ?: "new", onBack = { nav.back(back) })
            }
            composable("settings") { back ->
                SettingsScreen(c, section = null, onBack = { nav.back(back) }, onNavigate = { nav.go(back, it) })
            }
            composable("settings/{section}") { back ->
                SettingsScreen(c, section = back.arguments?.getString("section"), onBack = { nav.back(back) }, onNavigate = { nav.go(back, it) })
            }
            composable("literature") { back ->
                LiteratureScreen(c, onBack = { nav.back(back) }, onOpenChat = { nav.openChat(chatRoute()) }, onSettings = { nav.go(back, "settings/literature") })
            }
            composable("docs") { back ->
                // „Dokumente“ became the Dateien tab (AP-16).
                DriveScreen(c, onBack = { nav.back(back) }, onOpenDoc = { id -> nav.go(back, "doc/$id") }, onOpenChat = { id -> nav.openChat(chatRoute(id)) }, onNavigate = { nav.go(back, it) })
            }
            composable("pdf") { back ->
                PdfToolScreen(c, onBack = { nav.back(back) }, onOpenChat = { nav.openChat(chatRoute()) })
            }
            composable("games") { back ->
                GamesScreen(c, onBack = { nav.back(back) })
            }
            composable("mail") { back ->
                MailScreen(c, onBack = { nav.back(back) })
            }
            composable("batches") { back ->
                BatchScreen(c, onBack = { nav.back(back) })
            }
            composable("proposals") { back ->
                ProposalScreen(c, onBack = { nav.back(back) })
            }
            composable("files") { back ->
                DriveScreen(c, onBack = { nav.back(back) }, onOpenDoc = { id -> nav.go(back, "doc/$id") }, onOpenChat = { id -> nav.openChat(chatRoute(id)) }, onNavigate = { nav.go(back, it) })
            }
            composable("vault") { back ->
                VaultScreen(c, onBack = { nav.back(back) }, onOpenChat = { id -> nav.openChat(chatRoute(id)) })
            }
            composable("doc/{id}") { back ->
                DocScreen(c, back.arguments?.getString("id").orEmpty(), onBack = { nav.back(back) }, onOpenChat = { id -> nav.openChat(chatRoute(id)) })
            }
        }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).trackLoraGaze()) {
        if (wide) {
            androidx.compose.foundation.layout.Row(Modifier.fillMaxSize()) {
                PermanentNavigationDrawer(drawerContent = menu, modifier = Modifier.weight(1f)) { screens() }
                // Lora's panel beside the app on tablets.
                LoraSidePanel(c)
            }
        } else {
            ModalNavigationDrawer(
                drawerState = drawer,
                gesturesEnabled = isTopLevel(route) || drawer.isOpen,
                drawerContent = menu,
                content = screens,
            )
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
                .statusBarsPadding()
                .padding(top = 56.dp),
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
    }
}

/**
 * Transcription start page; on wide screens the list and the open transcript side by side (list – detail).
 */
@Composable
private fun TranscriptsPane(
    c: AppContainer,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    onNavigate: (String) -> Unit,
    onOpenChat: () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth < 720.dp) {
            TranscriptsScreen(c, onBack = onBack, onOpen = onOpen, onNavigate = onNavigate)
            return@BoxWithConstraints
        }
        var selected by rememberSaveable { mutableStateOf<String?>(null) }
        Row(Modifier.fillMaxSize()) {
            Box(Modifier.width(380.dp).fillMaxHeight()) {
                TranscriptsScreen(c, onBack = onBack, onOpen = { selected = it }, onNavigate = onNavigate)
            }
            VerticalDivider()
            Box(Modifier.weight(1f).fillMaxHeight()) {
                val id = selected
                if (id == null) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { HintText(tr("Transkript links auswählen.")) }
                } else {
                    key(id) { TranscriptScreen(c, id, onBack = { selected = null }, onOpenChat = onOpenChat) }
                }
            }
        }
    }
}

/** Crash report of the last start with buttons to copy or share it. */
@Composable
private fun CrashDialog(report: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
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
                val intent = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, report)
                context.startActivity(Intent.createChooser(intent, tr("Absturzbericht teilen")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                onDismiss()
            }) { Text(tr("Teilen")) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { clipboard.setText(AnnotatedString(report)) }) { Text(tr("Kopieren")) }
                TextButton(onClick = onDismiss) { Text(tr("Schließen")) }
            }
        },
    )
}
