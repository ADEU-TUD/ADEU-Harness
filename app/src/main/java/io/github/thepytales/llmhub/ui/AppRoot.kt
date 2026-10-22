package io.github.thepytales.llmhub.ui

import androidx.activity.compose.BackHandler
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
fun AppRoot(c: AppContainer, openChat: MutableStateFlow<String?>) {
    val nav = rememberNavController()
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val entry by nav.currentBackStackEntryAsState()
    val onChat = entry?.destination?.route?.startsWith("chat") != false
    var currentChatId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        if (c.settings.current.hasApiKey) c.models.refresh()
    }

    val requested by openChat.collectAsStateWithLifecycle()
    LaunchedEffect(requested) {
        requested?.let { id ->
            nav.openChat(chatRoute(id))
            openChat.value = null
        }
    }

    fun closeDrawer() = scope.launch { drawer.close() }

    BackHandler(enabled = drawer.isOpen) { closeDrawer() }

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
}
