package io.github.thepytales.llmhub.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * First start without an API key: what to do, step by step, and what already works without one (transcription and
 * models on this device).
 */
@Composable
fun WelcomeCard(onApiKey: () -> Unit, onNavigate: (String) -> Unit) {
    AppCard(Modifier.widthIn(max = 520.dp).fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(tr("Willkommen! So geht es los:"), style = MaterialTheme.typography.titleMedium)
            // AP-120: the whole setup step by step.
            Button(onClick = { onNavigate("setup") }, modifier = Modifier.fillMaxWidth()) { Text(tr("Lora Schritt für Schritt einrichten")) }
            Step(1, tr("API-Key holen"), tr("Im Self-Service-Portal der TU Dresden unter „ScaDS.AI LLM“ einen Schlüssel erzeugen (beginnt mit „sk-“)."))
            Step(2, tr("Eintragen"), tr("Er wird verschlüsselt nur auf diesem Gerät gespeichert."))
            Step(3, tr("Loslegen"), tr("Frage stellen, Datei anhängen oder eine Vorlage wählen."))
            Button(onClick = onApiKey, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.VpnKey, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text(tr("API-Key eintragen"))
            }
            // AP-112: the second way – everything on this device, without a key and without the Uni-Server.
            Text(tr("Oder ganz ohne Schlüssel: alles auf diesem Gerät"), style = MaterialTheme.typography.titleSmall)
            HintText(tr("Lora lädt Schritt für Schritt Modelle, die zu diesem Gerät passen – für Chat, Lora, Postfach, Suche, Diktat und Vorlesen. Nichts verlässt das Gerät; nur Bilder erzeugen geht ohne Schlüssel nicht."))
            androidx.compose.material3.OutlinedButton(onClick = { onNavigate("local-setup") }, modifier = Modifier.fillMaxWidth()) {
                Text(tr("Alles auf diesem Gerät einrichten"))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { onNavigate("transcripts") }) { Text(tr("Transkription")) }
                TextButton(onClick = { onNavigate("settings/places") }) { Text(tr("Wo Lora arbeitet")) }
            }
        }
    }
}

@Composable
private fun Step(n: Int, title: String, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(24.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text(n.toString(), style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
            }
        }
        Spacer(Modifier.size(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
