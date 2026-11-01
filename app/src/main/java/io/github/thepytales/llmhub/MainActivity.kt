package io.github.thepytales.llmhub

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.lifecycle.lifecycleScope
import io.github.thepytales.llmhub.ui.tr
import kotlinx.coroutines.launch
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.thepytales.llmhub.docs.PdfSelfTest
import io.github.thepytales.llmhub.tasks.Notifier
import io.github.thepytales.llmhub.ui.AppRoot
import io.github.thepytales.llmhub.ui.LlmHubTheme
import io.github.thepytales.llmhub.ui.Notices
import io.github.thepytales.llmhub.ui.StartAnimation
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
            intent?.let(::selfTest)
            intent?.let(::receiveShare)
        }
        setContent {
            val settings by container.settings.settings.collectAsStateWithLifecycle()
            LlmHubTheme(settings) {
                StartAnimation(enabled = settings.startAnimation && intent?.getStringExtra(EXTRA_ROUTE) == null) {
                    AppRoot(container, openChat, openRoute)
                }
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
        selfTest(intent)
        receiveShare(intent)
    }

    /** Something shared from another app: kept for the new chat, which puts it into the input field. */
    private fun receiveShare(intent: Intent) {
        if (intent.action != Intent.ACTION_SEND && intent.action != Intent.ACTION_SEND_MULTIPLE) return
        val text = listOfNotNull(
            intent.getStringExtra(Intent.EXTRA_SUBJECT)?.takeIf { it.isNotBlank() },
            intent.getStringExtra(Intent.EXTRA_TEXT)?.takeIf { it.isNotBlank() },
        ).distinct().joinToString("\n\n")
        @Suppress("DEPRECATION")
        val uris = if (intent.action == Intent.ACTION_SEND_MULTIPLE) {
            intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()
        } else {
            listOfNotNull(intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
        }
        if (text.isEmpty() && uris.isEmpty()) return
        // Audio and video (voice messages, recordings, lecture videos) go to the transcription.
        val media = uris.filter { (contentResolver.getType(it) ?: intent.type.orEmpty()).let { m -> m.startsWith("audio/") || m.startsWith("video/") } }
        if (media.isNotEmpty() && media.size == uris.size) {
            val container = (application as LlmHubApp).container
            lifecycleScope.launch {
                media.forEach { uri ->
                    runCatching {
                        var name = "Audio"
                        var size = -1L
                        contentResolver.query(uri, null, null, null, null)?.use { c ->
                            if (c.moveToFirst()) {
                                c.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let { i -> c.getString(i)?.let { name = it } }
                                c.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 }?.let { i -> if (!c.isNull(i)) size = c.getLong(i) }
                            }
                        }
                        contentResolver.openInputStream(uri)?.let { container.transcription.import(name, size, it) }
                    }.onFailure { Notices.error(it.message ?: tr("Die Datei konnte nicht übernommen werden.")) }
                }
            }
            openRoute.value = "transcripts"
            return
        }
        shared.value = Shared(text, uris)
        openRoute.value = "chat"
    }

    /** "foreground" or "background" (waits a few seconds so the test can leave the app first). */
    private fun selfTest(intent: Intent) {
        when (intent.getStringExtra(EXTRA_PDF_TEST)) {
            "foreground" -> PdfSelfTest.run(this, 0)
            "background" -> PdfSelfTest.run(this, 8_000)
        }
    }

    /** Text and files shared from another app. */
    data class Shared(val text: String, val uris: List<Uri>)

    companion object {
        /** Waiting for the next new chat (see ui/ChatScreen.kt). */
        val shared = MutableStateFlow<Shared?>(null)

        const val EXTRA_ROUTE = "route"
        const val EXTRA_PDF_TEST = "pdftest"
    }
}
