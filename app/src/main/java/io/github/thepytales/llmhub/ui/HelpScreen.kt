package io.github.thepytales.llmhub.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import io.github.thepytales.llmhub.BuildConfig
import io.github.thepytales.llmhub.ErrorLog
import io.github.thepytales.llmhub.update.UpdatePlatform
import io.github.thepytales.llmhub.update.Updates
import java.net.URLEncoder

/** Einstellungen › Problem melden: a description plus the anonymized error log, as e-mail (share) or GitHub issue. */
@Composable
fun HelpSection() {
    val context = LocalContext.current
    val uri = LocalUriHandler.current
    var text by rememberSaveable { mutableStateOf("") }
    val log = remember { ErrorLog.anonymizedText() }
    fun report() = buildString {
        append(text.trim().ifBlank { "(keine Beschreibung)" })
        append("\n\n---\nADEU LLM ${BuildConfig.VERSION_NAME} · ${UpdatePlatform.KEY} · ${System.getProperty("os.name").orEmpty()}")
        append("\n\nFehlerprotokoll (anonymisiert):\n").append(log.ifBlank { "(leer)" })
    }
    Section("Was ist passiert?") {
        OutlinedTextField(
            text, { text = it },
            placeholder = { Text("Was hast du gemacht, was ist passiert, was hättest du erwartet?") },
            modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
        )
        SettingsHint("Mitgeschickt werden nur Version, System und die letzten Fehlermeldungen – ohne Chat-Inhalte, Schlüssel, Namen, Pfade und Adressen.")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { shareText(context, report(), "Problem mit ADEU LLM ${BuildConfig.VERSION_NAME}") }) { Text("Per E-Mail senden …") }
            OutlinedButton(onClick = {
                val title = text.trim().lineSequence().firstOrNull().orEmpty().take(80).ifBlank { "Problem melden" }
                fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
                runCatching { uri.openUri("https://github.com/${Updates.REPOSITORY}/issues/new?title=${enc(title)}&body=${enc(report().take(6000))}") }
            }) { Text("Als GitHub-Issue") }
        }
    }
    Section("Fehlerprotokoll") {
        Text(log.ifBlank { "Keine Fehler seit dem Start." }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
