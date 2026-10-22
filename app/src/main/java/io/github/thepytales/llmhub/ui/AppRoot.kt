package io.github.thepytales.llmhub.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.data.Conversation
import io.github.thepytales.llmhub.data.ModelType

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("chats", "Chats", Icons.AutoMirrored.Outlined.Chat),
    Tab("models", "Modelle", Icons.Outlined.AutoAwesome),
    Tab("images", "Bilder", Icons.Outlined.Image),
    Tab("settings", "Einstellungen", Icons.Outlined.Settings),
)

/** Creates a new conversation, preferring [model] or the configured default. */
fun newChat(c: AppContainer, model: String? = null): Conversation {
    val wanted = model ?: c.settings.current.defaultModel
    val available = c.models.state.value.liveIds
    val chosen = if (available == null || wanted in available) wanted
    else c.models.state.value.ofType(ModelType.CHAT).firstOrNull()?.id ?: wanted
    return Conversation(model = chosen).also { c.chats.save(it) }
}

private fun NavHostController.switchTab(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

@Composable
fun AppRoot(c: AppContainer) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route

    LaunchedEffect(Unit) {
        if (c.settings.current.hasApiKey) c.models.refresh()
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (tabs.any { it.route == route }) {
                NavigationBar {
                    tabs.forEach { t ->
                        NavigationBarItem(
                            selected = route == t.route,
                            onClick = { nav.switchTab(t.route) },
                            icon = { Icon(t.icon, contentDescription = null) },
                            label = { Text(t.label) },
                        )
                    }
                }
            }
        },
    ) { pad ->
        NavHost(
            navController = nav,
            startDestination = "chats",
            modifier = Modifier.padding(pad).consumeWindowInsets(pad),
        ) {
            composable("chats") {
                ConversationsScreen(
                    c = c,
                    onOpen = { nav.navigate("chat/$it") },
                    onSettings = { nav.switchTab("settings") },
                )
            }
            composable("models") {
                ModelsScreen(c = c, onStartChat = { model -> nav.navigate("chat/${newChat(c, model).id}") })
            }
            composable("images") { ImagesScreen(c) }
            composable("settings") { SettingsScreen(c) }
            composable("chat/{id}") { back ->
                val id = back.arguments?.getString("id") ?: return@composable
                ChatScreen(c = c, id = id, onBack = { nav.popBackStack() })
            }
        }
    }
}
