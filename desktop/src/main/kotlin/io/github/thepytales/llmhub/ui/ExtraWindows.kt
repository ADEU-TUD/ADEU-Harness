package io.github.thepytales.llmhub.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.thepytales.llmhub.AppContainer
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Lora in mehreren Fenstern (AP-84): chats, documents and Lora's panel in windows of their own beside the main window –
 * one process, the same data. Each window has its own view model, so two chats answer at the same time; a chat is only
 * ever open in one window (the main window shows a note for it instead of a second input); closing a window ends
 * nothing else. Size and place are remembered per kind of window.
 */
object ExtraWindows {
    enum class Kind { CHAT, DOC, LORA }

    data class Spec(val kind: Kind, val target: String, val title: String) {
        val key: String get() = "${kind.name}:$target"
    }

    private val _open = MutableStateFlow<List<Spec>>(emptyList())
    val open: StateFlow<List<Spec>> = _open.asStateFlow()

    /** Asks a window to come to the front (by key). */
    val focus = MutableSharedFlow<String>(extraBufferCapacity = 8)

    fun show(spec: Spec) {
        if (_open.value.any { it.key == spec.key }) { focus.tryEmit(spec.key); return }
        _open.value = _open.value + spec
    }

    fun close(key: String) { _open.value = _open.value.filterNot { it.key == key } }

    fun chatOpen(id: String): Boolean = _open.value.any { it.kind == Kind.CHAT && it.target == id }

    /** Connects the menus of the shared screens (drawer, files, Lora) with the windows. */
    fun wire() {
        WindowHooks.openChat = { id, title ->
            show(Spec(Kind.CHAT, id, title))
            // The main window does not keep the same chat open with a second input.
            if (LoraHelper.chatId == id) Navigation.openRoute.value = "chat"
        }
        WindowHooks.openDoc = { id, title -> show(Spec(Kind.DOC, id, title)) }
        WindowHooks.openLora = {
            LoraHelper.close()
            show(Spec(Kind.LORA, "lora", "Lora"))
        }
        WindowHooks.chatInWindow = ::chatOpen
        WindowHooks.focusChat = { id -> focus.tryEmit(Spec(Kind.CHAT, id, "").key) }
        WindowHooks.closeChat = { id -> close(Spec(Kind.CHAT, id, "").key) }
    }

    /** Size and place per kind of window (ui_state of this PC). */
    internal object Memory {
        private val prefs by lazy { io.github.thepytales.llmhub.App.container.appContext.getSharedPreferences("ui_state", 0) }
        fun size(kind: Kind): DpSize = DpSize(prefs.getInt("extra.${kind.name}.w", if (kind == Kind.LORA) 460 else 900).dp, prefs.getInt("extra.${kind.name}.h", 760).dp)
        fun position(kind: Kind): WindowPosition {
            val x = prefs.getInt("extra.${kind.name}.x", Int.MIN_VALUE)
            val y = prefs.getInt("extra.${kind.name}.y", Int.MIN_VALUE)
            return if (x == Int.MIN_VALUE) WindowPosition.PlatformDefault else WindowPosition(x.dp, y.dp)
        }
        fun save(kind: Kind, size: DpSize, position: WindowPosition) {
            val e = prefs.edit().putInt("extra.${kind.name}.w", size.width.value.toInt()).putInt("extra.${kind.name}.h", size.height.value.toInt())
            if (position is WindowPosition.Absolute) e.putInt("extra.${kind.name}.x", position.x.value.toInt()).putInt("extra.${kind.name}.y", position.y.value.toInt())
            e.apply()
        }
    }
}

/** The extra windows of the app (inside `application { }` beside the main window). */
@Composable
fun ApplicationScope.ExtraWindowsHost(c: AppContainer) {
    val windows by ExtraWindows.open.collectAsState()
    val settings by c.settings.settings.collectAsState()
    for (spec in windows) key(spec.key) {
        val state = rememberWindowState(size = ExtraWindows.Memory.size(spec.kind), position = ExtraWindows.Memory.position(spec.kind))
        fun close() {
            ExtraWindows.Memory.save(spec.kind, state.size, state.position)
            ExtraWindows.close(spec.key)
        }
        Window(onCloseRequest = ::close, state = state, title = spec.title.ifBlank { "Lora" } + " – Lora", icon = painterResource("icon.png")) {
            LaunchedEffect(spec.key) { ExtraWindows.focus.collect { k -> if (k == spec.key) { window.toFront(); window.requestFocus() } } }
            // Own view models per window: two chats answer at the same time, closing clears only this window's.
            val owner = remember { object : ViewModelStoreOwner { override val viewModelStore = ViewModelStore() } }
            DisposableEffect(owner) { onDispose { owner.viewModelStore.clear() } }
            LlmHubTheme(settings) {
                CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        when (spec.kind) {
                            ExtraWindows.Kind.CHAT -> {
                                val vm: ChatViewModel = viewModel { ChatViewModel(c, spec.target) }
                                ChatScreen(
                                    c = c, vm = vm, onOpenDrawer = null,
                                    // New chats and other places open in the main window.
                                    onNewChat = { Navigation.openRoute.value = "chat" },
                                    onNavigate = { r -> Navigation.openRoute.value = r },
                                )
                            }
                            ExtraWindows.Kind.DOC -> DocScreen(c, spec.target, onBack = ::close, onOpenChat = { id -> Navigation.openChat.value = id })
                            ExtraWindows.Kind.LORA -> LoraWindowPanel(c)
                        }
                    }
                }
            }
        }
    }
}
