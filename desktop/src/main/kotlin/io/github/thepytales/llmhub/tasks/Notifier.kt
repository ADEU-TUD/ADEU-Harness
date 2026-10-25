package io.github.thepytales.llmhub.tasks

import android.content.Context
import io.github.thepytales.llmhub.data.ScheduledTask
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import java.awt.SystemTray

/** A Windows notification (shown by the tray icon in Main.kt); clicking it opens [chatId]. */
data class DesktopNotification(val title: String, val text: String, val chatId: String?, val route: String? = null)

/** System notifications: finished answers, results of scheduled tasks and questions of the agent. */
object Notifier {
    const val CHANNEL_RUNNING = "running"
    const val EXTRA_CHAT = "chatId"

    private val _notifications = MutableSharedFlow<DesktopNotification>(extraBufferCapacity = 16)
    val notifications: SharedFlow<DesktopNotification> = _notifications

    @Suppress("UNUSED_PARAMETER")
    fun ensureChannel(context: Context) {}

    /** Windows shows the notifications of the tray icon (unless "Nicht stören" is on). */
    @Suppress("UNUSED_PARAMETER")
    fun canNotify(context: Context): Boolean = runCatching { SystemTray.isSupported() }.getOrDefault(false)

    private fun preview(answer: String?): String? =
        answer?.replace(Regex("[#*_`>|]"), "")?.replace(Regex("\\s+"), " ")?.trim()?.take(250)?.ifBlank { null }

    private fun post(title: String, text: String, chatId: String?) {
        _notifications.tryEmit(DesktopNotification(title, text, chatId))
    }

    @Suppress("UNUSED_PARAMETER")
    fun taskFinished(context: Context, task: ScheduledTask, chatId: String, answer: String?, error: String?) {
        post(task.name, error ?: preview(answer) ?: "Fertig – klicken, um das Ergebnis zu sehen.", chatId)
    }

    /** An answer that was generated in the background is ready. */
    @Suppress("UNUSED_PARAMETER")
    fun chatFinished(context: Context, title: String, chatId: String, answer: String?, error: String?) {
        post(title, error?.let { "Fehler: $it" } ?: preview(answer) ?: "Antwort fertig.", chatId)
    }

    /** The agent waits for permission to run a write action. */
    @Suppress("UNUSED_PARAMETER")
    fun confirmationNeeded(context: Context, title: String, chatId: String, action: String) {
        post(title, "Der Agent wartet auf deine Bestätigung für „$action“. Klicken zum Öffnen.", chatId)
    }

    /** A transcription is finished (or failed); clicking opens [route] (the transcript). */
    fun transcriptionFinished(title: String, text: String, route: String) {
        _notifications.tryEmit(DesktopNotification(title, text, null, route))
    }

    /** Windows removes notifications by itself. */
    @Suppress("UNUSED_PARAMETER")
    fun cancel(context: Context, chatId: String) {}

    /** Shows a sample notification so the user can check that results will reach them. */
    @Suppress("UNUSED_PARAMETER")
    fun test(context: Context) {
        post("ADEU LLM", "So sieht es aus, wenn eine Aufgabe fertig ist – Benachrichtigungen funktionieren.", null)
    }
}
