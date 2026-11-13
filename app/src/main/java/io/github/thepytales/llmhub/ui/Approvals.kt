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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
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
import io.github.thepytales.llmhub.agent.GitHubTools
import io.github.thepytales.llmhub.agent.PendingAction
import io.github.thepytales.llmhub.agent.parseArgs
import io.github.thepytales.llmhub.data.LinkedRepo
import io.github.thepytales.llmhub.net.s

/** What a write action does, in words for the approval dialog. */
fun actionLabel(tool: String): String = when (tool) {
    "write_file" -> tr("Datei schreiben")
    "edit_file" -> tr("Datei ändern")
    "delete_file" -> tr("Datei löschen")
    "create_branch" -> tr("Branch erstellen")
    "create_pull_request" -> tr("Pull Request erstellen")
    "create_issue" -> tr("Issue erstellen")
    "comment_issue" -> tr("Kommentar schreiben")
    "fetch_url" -> tr("Web-Anfrage senden")
    "remember" -> tr("Im Gedächtnis speichern")
    "forget" -> tr("Aus dem Gedächtnis löschen")
    else -> tool
}

/** Choice of the approval mode; shown under Chat & Werkzeuge and GitHub in the settings. */
@Composable
fun ApprovalModeOptions(c: AppContainer) {
    val s by c.settings.settings.collectAsStateWithLifecycle()
    val current = ApprovalMode.of(s.approvalMode)
    Text(
        tr("Wann der Agent nachfragt, bevor er etwas verändert. Gilt für alle Chats; einzelne Chats lassen sich im " +
            "Bestätigungsdialog mit „In diesem Chat nicht mehr fragen“ freischalten. Geplante Aufgaben haben eine eigene Einstellung."),
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
fun ApprovalDialog(action: PendingAction, repos: List<LinkedRepo>, onAnswer: (allow: Boolean, always: Boolean) -> Unit) {
    var always by remember(action) { mutableStateOf(false) }
    val call = action.call
    val where = when (call.name) {
        "fetch_url" -> tr("im Web")
        "remember", "forget" -> tr("Gilt für alle Chats · Einstellungen › Gedächtnis")
        else -> {
            // The repository named in the call, or the only linked one.
            val named = parseArgs(call.arguments).s("repo")?.let { GitHubTools.parseRepo(it) }
            val target = named?.let { n -> repos.firstOrNull { it.name.equals(n, ignoreCase = true) } ?: LinkedRepo(n) }
                ?: repos.singleOrNull()
            tr("in {0}", target?.name ?: tr("GitHub")) + (target?.branch?.let { tr(" · Branch {0}", it) } ?: "")
        }
    }
    AlertDialog(
        onDismissRequest = {},
        icon = { Icon(Icons.Outlined.Build, contentDescription = null) },
        title = { Text(tr("Aktion erlauben?")) },
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
                    Text(tr("In diesem Chat nicht mehr fragen"), style = MaterialTheme.typography.bodyMedium)
                }
                Text(
                    tr("Für alle Chats: Einstellungen › Chat & Werkzeuge › Freigaben."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onAnswer(true, always) }) { Text(if (always) tr("Immer erlauben") else tr("Erlauben")) } },
        dismissButton = { TextButton(onClick = { onAnswer(false, false) }) { Text(tr("Ablehnen")) } },
    )
}
