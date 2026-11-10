package io.github.thepytales.llmhub.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.audio.LocalVoiceInfo

/**
 * Einstellungen › Vorlesen & Spracheingabe, „Natürliche Stimme auf diesem Gerät“ (AP-111): download with progress,
 * state, delete. Until the voice is loaded the voice of the system reads.
 */
@Composable
fun LocalVoiceSettings(c: AppContainer) {
    val st by c.localVoice.state.collectAsStateWithLifecycle()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        val progress = st.progress
        when {
            progress != null -> {
                LinearProgressIndicator(progress = { progress.toFloat() }, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("Wird geladen … {0} %", (progress * 100).toInt()), style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = c.localVoice::cancelInstall) { Text(tr("Abbrechen")) }
                }
            }
            !st.installed -> {
                OutlinedButton(onClick = c.localVoice::install) { Text(tr("{0} laden ({1} MB)", LocalVoiceInfo.NAME, LocalVoiceInfo.MEGABYTES)) }
                SettingsHint(tr("Bis dahin liest die Stimme des Systems vor."))
            }
            else -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(tr("Geladen."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = c.localVoice::delete) { Text(tr("Löschen")) }
            }
        }
        st.message?.let { SettingsHint(it) }
        SettingsHint(
            tr("Gesprochen von Thorsten Müller (Thorsten-Voice, frei als CC0), trainiert mit Piper (MIT); Aussprache nach espeak-ng " +
                "(GPL-3.0). Liest auch, wenn ein Text das Gerät nicht verlassen darf, und im Sprachmodus mit „Nur lokal“.")
        )
    }
}
