package io.github.thepytales.llmhub.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.thepytales.llmhub.AppContainer

/** Einstellungen › Datenschutz: pseudonymization with own list, a preview and the stored pairs. */
@Composable
fun PrivacySection(c: AppContainer) {
    val s by c.settings.settings.collectAsStateWithLifecycle()
    var sample by remember { mutableStateOf("Frau Dr. Anna Kaltenbach (a.kaltenbach@example.org, Tel. 0351 463-12345) bittet um ein Gutachten für Lukas Schmidt, Matrikelnummer 4712345.") }
    var stored by remember { mutableIntStateOf(c.privacy.all().size) }
    var confirmClear by remember { mutableStateOf(false) }

    SettingsCard("Pseudonymisierung") {
        ToggleRow(
            "Personenbezogene Daten ersetzen",
            "Vor jeder Anfrage ersetzt die App Namen, E-Mail-Adressen, Telefonnummern, Matrikelnummern, IBAN, Geburtsdaten " +
                "und Anschriften auf dem Gerät durch Platzhalter wie [Name 1]. Der Server sieht nur die Platzhalter; in der " +
                "Antwort, in Dokumenten und E-Mail-Entwürfen setzt die App die echten Angaben wieder ein.",
            s.pseudonymize,
        ) { v -> c.settings.update { it.copy(pseudonymize = v) } }
        ToggleRow(
            "Namen automatisch erkennen",
            "Namen nach Anrede oder Titel (Frau, Herr, Dr., Prof.) sowie häufige Vor- und Nachnamen. Ohne diesen Schalter nur die " +
                "Angaben mit festem Muster und die Namen aus der Liste unten.",
            s.pseudonymAuto,
        ) { v -> c.settings.update { it.copy(pseudonymAuto = v) } }
        OutlinedTextField(
            value = s.pseudonymTerms,
            onValueChange = { v -> c.settings.update { it.copy(pseudonymTerms = v) } },
            label = { Text("Eigene Namen und Begriffe") },
            placeholder = { Text("Je Zeile einer, z.B.\nJonte Vogelsang\nGymnasium Dresden-Plauen") },
            supportingText = {
                Text(
                    "Werden immer ersetzt – z.B. Klassenlisten, Studierende, Schulen. Bei Personen erkennt die App auch Vor- und " +
                        "Nachnamen einzeln. Nur mit dieser Liste ist die Erkennung von Namen vollständig zuverlässig."
                )
            },
            minLines = 3,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    SettingsCard("Ausprobieren") {
        SettingsHint("So sähe der Server diesen Text. Nichts wird gesendet oder gespeichert.")
        OutlinedTextField(value = sample, onValueChange = { sample = it }, label = { Text("Beispieltext") }, minLines = 2, modifier = Modifier.fillMaxWidth())
        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
            Text(
                c.privacy.preview(sample),
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.padding(12.dp),
            )
        }
    }

    SettingsCard("Gut zu wissen") {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SettingsHint("• Die Zuordnung (z.B. Müller ↔ [Name 4]) bleibt nur auf diesem Gerät, damit dieselbe Person in allen Chats denselben Platzhalter hat.")
            SettingsHint("• Bilder, Scans und Spracheingaben lassen sich nicht pseudonymisieren. Vorlesen mit der KI-Stimme schickt den Antworttext an den Uni-Server.")
            SettingsHint("• Ohne Pseudonymisierung warnt die App, wenn eine Nachricht mit Namen oder Kontaktdaten an einen Anbieter außerhalb der TU Dresden (z.B. Google Gemini) ginge.")
            SettingsHint("• Was ersetzt wurde, zeigt das Agent-Protokoll unter der Antwort (Verlaufssymbol).")
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("$stored Zuordnung(en) gespeichert", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = { confirmClear = true }, enabled = stored > 0) { Text("Löschen") }
        }
    }

    if (confirmClear) {
        ConfirmDialog(
            "Zuordnungen löschen?",
            "Die App vergisst, welcher Platzhalter zu welcher Angabe gehört. Platzhalter in älteren Chats lassen sich danach nicht " +
                "mehr zurückübersetzen.",
            "Löschen",
            onConfirm = {
                c.privacy.clear()
                stored = 0
            },
            onDismiss = { confirmClear = false },
        )
    }
}
