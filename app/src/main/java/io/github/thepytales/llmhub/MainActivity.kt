package io.github.thepytales.llmhub

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.thepytales.llmhub.tasks.Notifier
import io.github.thepytales.llmhub.ui.AppRoot
import io.github.thepytales.llmhub.ui.LlmHubTheme
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {
    /** Chat to open, e.g. from the notification of a scheduled task. */
    private val openChat = MutableStateFlow<String?>(null)

    /** Screen to open (route); used by the automated smoke test. */
    private val openRoute = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as LlmHubApp).container
        if (savedInstanceState == null) {
            openChat.value = intent?.getStringExtra(Notifier.EXTRA_CHAT)
            openRoute.value = intent?.getStringExtra(EXTRA_ROUTE)
        }
        setContent {
            val settings by container.settings.settings.collectAsStateWithLifecycle()
            LlmHubTheme(settings) {
                AppRoot(container, openChat, openRoute)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        AppState.foreground = true
    }

    override fun onStop() {
        AppState.foreground = false
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(Notifier.EXTRA_CHAT)?.let { openChat.value = it }
        intent.getStringExtra(EXTRA_ROUTE)?.let { openRoute.value = it }
    }

    companion object {
        const val EXTRA_ROUTE = "route"
    }
}
