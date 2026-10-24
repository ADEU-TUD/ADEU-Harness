package io.github.thepytales.llmhub.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.agent.ReferenceCheck
import io.github.thepytales.llmhub.docs.AccessibilityCheck
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private val OK_GREEN = Color(0xFF15803D)
private val WARN_AMBER = Color(0xFFB45309)

@Composable
private fun statusColor(status: ReferenceCheck.Status) = when (status) {
    ReferenceCheck.Status.VERIFIED -> OK_GREEN
    ReferenceCheck.Status.DIFFERS -> WARN_AMBER
    ReferenceCheck.Status.NOT_FOUND -> MaterialTheme.colorScheme.error
    ReferenceCheck.Status.UNCHECKED -> MaterialTheme.colorScheme.onSurfaceVariant
}

/** Checks the literature references of an answer against Crossref & Co. – without the model. */
@Composable
fun ReferenceCheckDialog(c: AppContainer, text: String, onDismiss: () -> Unit) {
    val refs = remember(text) { ReferenceCheck.extract(text) }
    var results by remember(text) { mutableStateOf<List<ReferenceCheck.Result>?>(null) }
    var error by remember(text) { mutableStateOf<String?>(null) }
    LaunchedEffect(text) {
        if (refs.isEmpty()) return@LaunchedEffect
        runCatching { withContext(Dispatchers.IO) { ReferenceCheck.check(c.llm.http, refs) } }
            .onSuccess { results = it }
            .onFailure { error = it.message ?: it.toString() }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Quellen prüfen") },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when {
                    refs.isEmpty() -> Text(
                        "In dieser Antwort stehen keine vollständigen Literaturangaben (Autor:in, Jahr, Titel). Bitte das Modell um " +
                            "ein Literaturverzeichnis oder nutze die Vorlage „Literaturangaben prüfen“."
                    )
                    error != null -> Text("Die Prüfung ist fehlgeschlagen: $error", color = MaterialTheme.colorScheme.error)
                    results == null -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text("${refs.size} Angabe(n) werden bei Crossref, OpenAlex und Google Books gesucht …")
                    }
                    else -> {
                        val list = results!!
                        val counts = list.groupingBy { it.status }.eachCount()
                        SettingsHint(ReferenceCheck.Status.entries.filter { (counts[it] ?: 0) > 0 }.joinToString(" · ") { "${counts[it]} ${it.label}" })
                        list.forEach { r ->
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    "${r.status.symbol} ${r.status.label}",
                                    color = statusColor(r.status),
                                    fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.labelLarge,
                                )
                                Text(r.input, style = MaterialTheme.typography.bodySmall)
                                r.work?.let { w -> SettingsHint("Gefunden (${w.source}): ${w.cite()}") }
                                if (r.note.isNotBlank()) SettingsHint(r.note)
                            }
                        }
                        SettingsHint("Bücher ohne DOI und graue Literatur sind nicht immer verzeichnet – „nicht gefunden“ heißt dann: von Hand prüfen.")
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Schließen") } },
    )
}

/** Accessibility check of a document created by the app or attached to the chat. */
@Composable
fun AccessibilityDialog(c: AppContainer, file: File, onDismiss: () -> Unit) {
    var findings by remember(file) { mutableStateOf<List<AccessibilityCheck.Finding>?>(null) }
    var error by remember(file) { mutableStateOf<String?>(null) }
    LaunchedEffect(file) {
        runCatching { withContext(Dispatchers.IO) { AccessibilityCheck.check(c.appContext, file) } }
            .onSuccess { findings = it }
            .onFailure { error = it.message ?: it.toString() }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Barrierefreiheit") },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(file.name, style = MaterialTheme.typography.labelLarge)
                when {
                    error != null -> Text("Prüfung nicht möglich: $error", color = MaterialTheme.colorScheme.error)
                    findings == null -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else -> findings!!.forEach { f ->
                        Row(Modifier.fillMaxWidth()) {
                            Text(if (f.ok) "✓" else "✗", color = if (f.ok) OK_GREEN else MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold, modifier = Modifier.width(22.dp))
                            Column {
                                Text(f.text, style = MaterialTheme.typography.bodyMedium)
                                if (!f.ok && f.fix.isNotBlank()) SettingsHint(f.fix)
                            }
                        }
                    }
                }
                SettingsHint("Geprüft werden die häufigsten Hürden. Eine vollständige Prüfung nach PDF/UA oder WCAG (z.B. mit PAC) ersetzt das nicht.")
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Schließen") } },
        modifier = Modifier.padding(8.dp),
    )
}
