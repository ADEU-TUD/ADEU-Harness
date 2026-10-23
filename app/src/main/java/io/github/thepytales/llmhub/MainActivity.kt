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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as LlmHubApp).container
        if (savedInstanceState == null) openChat.value = intent?.getStringExtra(Notifier.EXTRA_CHAT)
        setContent {
            val settings by container.settings.settings.collectAsStateWithLifecycle()
            LlmHubTheme(settings) {
                AppRoot(container, openChat)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(Notifier.EXTRA_CHAT)?.let { openChat.value = it }
    }
}
