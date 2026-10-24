package io.github.thepytales.llmhub.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.agent.ApprovalMode
import io.github.thepytales.llmhub.agent.PendingAction

/** What a write action does, in words for the approval dialog. */
fun actionLabel(tool: String): String = when (tool) {
    "write_file" -> "Datei schreiben"
    "edit_file" -> "Datei ändern"
    "delete_file" -> "Datei löschen"
    "create_branch" -> "Branch erstellen"
    "create_pull_request" -> "Pull Request erstellen"
    "create_issue" -> "Issue erstellen"
    "comment_issue" -> "Kommentar schreiben"
    "fetch_url" -> "Web-Anfrage senden"
    else -> tool
}

/** Choice of the approval mode; shown under Chat & Werkzeuge and GitHub in the settings. */
@Composable
fun ApprovalModeOptions(c: AppContainer) {
    val s by c.settings.settings.collectAsStateWithLifecycle()
    val current = ApprovalMode.of(s.approvalMode)
    Text(
        "Wann der Agent nachfragt, bevor er etwas verändert. Gilt für alle Chats; einzelne Chats lassen sich im " +
            "Bestätigungsdialog mit „In diesem Chat nicht mehr fragen“ freischalten. Geplante Aufgaben haben eine eigene Einstellung.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    ApprovalMode.entries.forEach { mode ->
        Row(
            verticalAlignment = Alignment.Top,
            modifier = Modifier.fillMaxWidth().clickable { c.settings.update { it.copy(approvalMode = mode.name) } },
        ) {
            RadioButton(selected = current == mode, onClick = { c.settings.update { it.copy(approvalMode = mode.name) } })
            Column(Modifier.padding(top = 12.dp)) {
                Text(mode.label)
                Text(mode.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/**
 * Asks whether the agent may run [action]. [onAnswer] gets the decision and whether the chat should run all
 * further actions without asking.
 */
@Composable
fun ApprovalDialog(action: PendingAction, repo: String?, branch: String?, onAnswer: (allow: Boolean, always: Boolean) -> Unit) {
    var always by remember(action) { mutableStateOf(false) }
    val call = action.call
    val where = if (call.name == "fetch_url") "im Web" else "in ${repo ?: "GitHub"}" + (branch?.let { " · Branch $it" } ?: "")
    AlertDialog(
        onDismissRequest = {},
        icon = { Icon(Icons.Outlined.Build, contentDescription = null) },
        title = { Text("Aktion erlauben?") },
        text = {
            Column {
                Text(actionLabel(call.name), style = MaterialTheme.typography.titleMedium)
                Text(where, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.size(10.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState()).padding(10.dp)) {
                        Text(prettyArgs(call.arguments).take(4000), fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                    }
                }
                Spacer(Modifier.size(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { always = !always },
                ) {
                    Checkbox(checked = always, onCheckedChange = { always = it })
                    Text("In diesem Chat nicht mehr fragen", style = MaterialTheme.typography.bodyMedium)
                }
                Text(
                    "Für alle Chats: Einstellungen › Chat & Werkzeuge › Freigaben.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onAnswer(true, always) }) { Text(if (always) "Immer erlauben" else "Erlauben") } },
        dismissButton = { TextButton(onClick = { onAnswer(false, false) }) { Text("Ablehnen") } },
    )
}
