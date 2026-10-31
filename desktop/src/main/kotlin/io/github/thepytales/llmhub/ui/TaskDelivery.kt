package io.github.thepytales.llmhub.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.desktop.Autostart
import io.github.thepytales.llmhub.tasks.Notifier

/**
 * Shows whether results can reach the user (notifications) and whether tasks also run while the window
 * is closed or the app was not started, with buttons to fix both.
 */
@Composable
fun DeliveryStatus(c: AppContainer) {
    val context = LocalContext.current
    val settings by c.settings.settings.collectAsStateWithLifecycle()
    val notify = remember { Notifier.canNotify(context) }
    var autostart by remember { mutableStateOf(Autostart.enabled) }
    val background = settings.closeToTray
    Surface(color = LocalChatLook.current.card, shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            StatusLine(
                notify,
                if (notify) "Benachrichtigungen sind an – Windows meldet sich, sobald ein Ergebnis da ist."
                else "Dieser PC zeigt keine Benachrichtigungen – Ergebnisse siehst du hier in der App.",
            )
            if (notify) TextButton(onClick = { Notifier.test(context) }) { Text("Test-Benachrichtigung senden") }
            StatusLine(
                background,
                if (background) "Aufgaben laufen weiter, wenn du das Fenster schließt (Symbol im Infobereich)."
                else "Aufgaben laufen nur, solange das Fenster offen ist.",
            )
            if (!background) {
                OutlinedButton(onClick = { c.settings.update { it.copy(closeToTray = true) } }) { Text("Im Infobereich weiterlaufen") }
            }
            if (Autostart.supported) {
                StatusLine(
                    autostart,
                    if (autostart) "Lora startet mit Windows – Aufgaben laufen auch, wenn du die App nicht geöffnet hast."
                    else "Aufgaben laufen nur, wenn Lora gestartet wurde. Verpasste Termine werden beim nächsten Start nachgeholt.",
                )
                if (!autostart) {
                    OutlinedButton(onClick = {
                        Autostart.set(true)
                        autostart = Autostart.enabled
                    }) { Text("Mit Windows starten") }
                }
            }
        }
    }
}

/** Windows asks for nothing: notifications either work or not. */
@Composable
fun rememberAskNotifications(): () -> Unit = {}
