package io.github.thepytales.llmhub.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Text
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
}

@Composable
fun AppRoot(c: AppContainer, openChat: MutableStateFlow<String?>, openRoute: MutableStateFlow<String?>) {
    val nav = rememberNavController()
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val entry by nav.currentBackStackEntryAsState()
    val onChat = entry?.destination?.route?.startsWith("chat") != false
    var currentChatId by remember { mutableStateOf<String?>(null) }

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

    val route by openRoute.collectAsStateWithLifecycle()
    LaunchedEffect(route) {
        route?.let { r ->
            when (r) {
                "drawer" -> drawer.open()
                "chat" -> nav.openChat(chatRoute())
                else -> runCatching { nav.navigate(r) }
            }
            openRoute.value = null
        }
    }

    // Shows what went wrong if the app crashed last time.
    val context = LocalContext.current
    var crash by remember { mutableStateOf(CrashLog.take(context)) }
    crash?.let { report ->
        CrashDialog(report, onDismiss = { crash = null })
    }

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
                actionLabel = "Ansehen",
                withDismissAction = true,
                duration = SnackbarDuration.Long,
            )
            if (result == SnackbarResult.ActionPerformed) nav.openChat(chatRoute(e.chatId))
        }
    }

    Box(Modifier.fillMaxSize()) {
        ModalNavigationDrawer(
            drawerState = drawer,
            gesturesEnabled = onChat,
            drawerContent = {
                AppDrawer(
                    c = c,
                    currentChatId = currentChatId,
                    onOpenChat = { id -> nav.openChat(chatRoute(id)); closeDrawer() },
                    onNewChat = { nav.openChat(chatRoute()); closeDrawer() },
                    onNavigate = { route -> nav.navigate(route); closeDrawer() },
                )
            },
        ) {
            NavHost(navController = nav, startDestination = "chat?id={id}&model={model}&persona={persona}") {
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
                        onOpenDrawer = { scope.launch { drawer.open() } },
                        onNewChat = { nav.openChat(chatRoute()) },
                        onNavigate = { nav.navigate(it) },
                    )
                }
                composable("models") {
                    ModelsScreen(c = c, onBack = { nav.popBackStack() }, onStartChat = { model -> nav.openChat(chatRoute(model = model)) })
                }
                composable("images") { ImagesScreen(c, onBack = { nav.popBackStack() }) }
                composable("compare") {
                    CompareScreen(c, onBack = { nav.popBackStack() }, onOpenChat = { id -> nav.openChat(chatRoute(id)) })
                }
                composable("templates") {
                    TemplatesScreen(
                        c, onBack = { nav.popBackStack() },
                        onUse = { t -> nav.openChat(chatRoute(persona = t.id)) },
                    )
                }
                composable("tasks") {
                    TasksScreen(c, onBack = { nav.popBackStack() }, onEdit = { id -> nav.navigate("task/$id") }, onOpenChat = { id -> nav.openChat(chatRoute(id)) })
                }
                composable("task/{id}") { back ->
                    TaskEditScreen(c, back.arguments?.getString("id") ?: "new", onBack = { nav.popBackStack() })
                }
                composable("usage") { UsageScreen(c, onBack = { nav.popBackStack() }) }
                composable("local-models") {
                    LocalModelsScreen(c, onBack = { nav.popBackStack() }, onTry = { model -> nav.openChat(chatRoute(model = model)) })
                }
                composable("transcripts") {
                    TranscriptsScreen(c, onBack = { nav.popBackStack() }, onOpen = { id -> nav.navigate("transcript/$id") }, onNavigate = { nav.navigate(it) })
                }
                composable("transcript/{id}") { back ->
                    TranscriptScreen(c, back.arguments?.getString("id").orEmpty(), onBack = { nav.popBackStack() }, onOpenChat = { nav.openChat(chatRoute()) })
                }
                composable("environments") {
                    EnvironmentsScreen(c, onBack = { nav.popBackStack() }, onEdit = { id -> nav.navigate("environment/$id") })
                }
                composable("environment/{id}") { back ->
                    EnvironmentEditScreen(c, back.arguments?.getString("id") ?: "new", onBack = { nav.popBackStack() })
                }
                composable("settings") {
                    SettingsScreen(c, section = null, onBack = { nav.popBackStack() }, onNavigate = { nav.navigate(it) })
                }
                composable("settings/{section}") { back ->
                    SettingsScreen(c, section = back.arguments?.getString("section"), onBack = { nav.popBackStack() }, onNavigate = { nav.navigate(it) })
                }
            }
        }
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

/** Crash report of the last start with buttons to copy or share it. */
@Composable
private fun CrashDialog(report: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
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
                val intent = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, report)
                context.startActivity(Intent.createChooser(intent, "Absturzbericht teilen").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                onDismiss()
            }) { Text("Teilen") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { clipboard.setText(AnnotatedString(report)) }) { Text("Kopieren") }
                TextButton(onClick = onDismiss) { Text("Schließen") }
            }
        },
    )
}
