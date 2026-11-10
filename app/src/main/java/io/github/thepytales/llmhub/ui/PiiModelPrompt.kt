package io.github.thepytales.llmhub.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.data.PiiModelInfo

/**
 * Offers Lora Namensschutz, the recognition model of the pseudonymization (AP-67), once per start of the app: when
 * messages are first pseudonymized or Einstellungen › Datenschutz is opened, while it is not downloaded. „Laden“,
 * „Später“ (asks again at the next start) or „Nicht mehr fragen“ (the download stays in the settings). Who still has the
 * model of 1.0.65 gets the new version offered as an update; the old one keeps working until then.
 */
@Composable
fun PiiModelPrompt(c: AppContainer) {
    val offer by c.piiModel.offer.collectAsState()
    if (!offer) return
    val update = remember { c.piiModel.hasOld() }
    fun close() { c.piiModel.offer.value = false }
    AlertDialog(
        onDismissRequest = ::close,
        icon = { Icon(Icons.Outlined.Shield, contentDescription = null) },
        title = { Text(if (update) tr("Neue Version von {0}", PiiModelInfo.NAME) else tr("Namen besser erkennen")) },
        text = {
            Text(
                if (update) tr("Es gibt eine bessere Version von {0} ({1} MB): Sie lässt zitierte Autor:innen, öffentliche Personen und " +
                    "Literaturangaben öfter stehen und findet private Namen weiter zuverlässig. Bis du sie lädst, arbeitet die bisherige " +
                    "Version weiter. Alles läuft nur auf diesem Gerät.", PiiModelInfo.NAME, PiiModelInfo.MEGABYTES)
                else tr("Lora kann für die Pseudonymisierung {0} laden ({1} MB), ein kleines Modell nur für diese Aufgabe. Es findet Namen, " +
                    "Schulen, Kitas und Orte auch ohne Anrede oder bekannte Vornamen und lässt zitierte Autor:innen und öffentliche " +
                    "Personen eher stehen – in unseren Tests 99 % statt 78 % der privaten Angaben. Es läuft nur auf diesem Gerät, " +
                    "nichts verlässt es dabei.", PiiModelInfo.NAME, PiiModelInfo.MEGABYTES),
            )
        },
        confirmButton = { Button(onClick = { c.piiModel.install(); close() }) { Text(tr("Laden")) } },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = {
                    c.settings.update { if (update) it.copy(pseudonymModelUpdateAsk = false) else it.copy(pseudonymModelAsk = false) }
                    close()
                }) { Text(tr("Nicht mehr fragen")) }
                TextButton(onClick = ::close) { Text(tr("Später")) }
            }
        },
    )
}

/** Einstellungen › Datenschutz: Lora Namensschutz – download (or the update), progress, switch, model card, delete. */
@Composable
fun PiiModelSettings(c: AppContainer) {
    val s by c.settings.settings.collectAsState()
    val st by c.piiModel.state.collectAsState()
    var card by remember { mutableStateOf(false) }
    ToggleRow(
        tr("{0} (empfohlen)", PiiModelInfo.NAME),
        tr("Ein kleines Modell auf diesem Gerät findet Namen, Schulen, Kitas und Orte – auch ohne Anrede oder bekannte Vornamen – " +
            "und lässt zitierte Autor:innen und öffentliche Personen eher stehen. In unseren Tests 99 % statt 78 % der privaten " +
            "Angaben, wenige Millisekunden je Absatz. Mit dem Modell raten die Regeln keine Namen mehr aus häufigen Vornamen; " +
            "die eigene Liste und die festen Muster (Mail, Telefon, Nummern …) bleiben."),
        s.pseudonymModel,
    ) { v -> c.settings.update { it.copy(pseudonymModel = v) } }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        val progress = st.progress
        when {
            progress != null -> {
                LinearProgressIndicator(progress = { progress.toFloat() }, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(tr("Wird geladen … {0} %", (progress * 100).toInt()), style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = c.piiModel::cancelInstall) { Text(tr("Abbrechen")) }
                }
            }
            st.old -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    tr("Die bisherige Version ist geladen und arbeitet weiter. Es gibt eine bessere."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = c.piiModel::install) { Text(tr("Neue Version laden ({0} MB)", PiiModelInfo.MEGABYTES)) }
                    TextButton(onClick = c.piiModel::delete) { Text(tr("Löschen")) }
                }
            }
            !st.installed -> OutlinedButton(onClick = c.piiModel::install) { Text(tr("{0} laden ({1} MB)", PiiModelInfo.NAME, PiiModelInfo.MEGABYTES)) }
            else -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (st.ready) tr("Geladen und bereit.") else tr("Geladen."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = { card = true }) { Text(tr("Modellkarte")) }
                TextButton(onClick = c.piiModel::delete) { Text(tr("Löschen")) }
            }
        }
        st.message?.let { SettingsHint(it) }
    }
    if (card) {
        val text = remember { PiiModelInfo::class.java.getResourceAsStream("/pii/MODELLKARTE.md")?.bufferedReader()?.readText().orEmpty() }
        AlertDialog(
            onDismissRequest = { card = false },
            title = { Text(tr("Modellkarte")) },
            text = { Text(text, modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), style = MaterialTheme.typography.bodySmall) },
            confirmButton = { TextButton(onClick = { card = false }) { Text("OK") } },
        )
    }
}
