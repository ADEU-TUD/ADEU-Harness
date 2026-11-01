package io.github.thepytales.llmhub.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.data.AppSettings
import io.github.thepytales.llmhub.data.AppTool
import io.github.thepytales.llmhub.data.withTool

/**
 * Einstellungen › Werkzeuge (AP-02): a switch per big tool. New tools start switched off; switched-on ones appear in
 * the side menu under „Mehr“ and on the start page. Planned tools are listed greyed out. Below: how the side menu can
 * be arranged, with a button back to the original order.
 */
@Composable
fun ToolsSection(c: AppContainer) {
    val s by c.settings.settings.collectAsStateWithLifecycle()
    Section(tr("Große Werkzeuge")) {
        HintText(
            tr("Große Werkzeuge sind anfangs aus, damit die App übersichtlich bleibt. Eingeschaltete stehen in der Seitenleiste unter „Mehr“ und auf der Startseite; Lora schlägt passende Werkzeuge vor."),
        )
        val available = AppTool.available(IS_ANDROID)
        if (available.isEmpty()) HintText(tr("Im Moment gibt es noch kein einschaltbares Werkzeug – die ersten folgen mit den nächsten Updates."))
        available.forEach { tool ->
            SwitchRow(tool.label, tool.description, s.toolOn(tool.id)) { on -> c.settings.update { it.withTool(tool, on) } }
        }
    }
    // Nachladbare Erweiterungen (AP-25, Windows): Videoeditor-Paket, Browser-Engine.
    ExtensionsSettings(c)
    val planned = AppTool.planned(IS_ANDROID)
    if (planned.isNotEmpty()) {
        Section(tr("Geplant")) {
            planned.forEach { tool ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(tool.label, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        HintText(tool.description)
                    }
                    Spacer(Modifier.size(8.dp))
                    StatusPill(tr("geplant"), PillTone.NEUTRAL)
                    Switch(checked = false, onCheckedChange = null, enabled = false)
                }
            }
        }
    }
    Section(tr("Seitenleiste")) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            HintText(
                tr("Oben stehen „Neuer Chat“ und bis zu vier Bereiche, alles andere unter „Mehr“.") + " " +
                    tr("Mit") + " " + CONTEXT_GESTURE + " " + tr("auf einen Bereich holst du ihn nach oben, schiebst ihn unter „Mehr“ oder blendest ihn aus."),
            )
            // Bereiche ausblenden, damit die Liste kurz bleibt (Werkzeuge oben ausschalten); „Erstellen“ und „Dateien“ bleiben.
            DrawerArea.entries.filter { it.tool == null && it !in DrawerArea.ALWAYS }.forEach { area ->
                SwitchRow(tr(area.label), tr("In der Seitenleiste zeigen – sonst über Strg+K und Lora erreichbar."), area.id !in s.drawerHiddenIds) { on ->
                    c.settings.update { it.copy(drawerHidden = hideDrawerArea(it.drawerHidden, area, !on)) }
                }
            }
            OutlinedButton(
                onClick = { c.settings.update { it.copy(drawerTop = AppSettings.DEFAULT_DRAWER_TOP, drawerHidden = "") } },
                enabled = s.drawerTop != AppSettings.DEFAULT_DRAWER_TOP || s.drawerHidden.isNotBlank(),
            ) { Text(tr("Ursprüngliche Reihenfolge")) }
        }
    }
}

/** Tools whose suggestion was closed in this session (asked once, not on every word). */
private val dismissedTools = androidx.compose.runtime.mutableStateListOf<String>()

/**
 * Lora's hint above the input when the request (or an attached file) fits a tool that is switched off: „Für PDFs gibt
 * es den PDF-Werkzeugkasten – einschalten?“ (AP-02). Only with Einstellungen › Lora › Hinweise and Lora not off.
 */
@Composable
fun ToolSuggestion(c: AppContainer, input: String, fileNames: List<String>) {
    val s by c.settings.settings.collectAsStateWithLifecycle()
    if (!s.loraHints || s.hideLora) return
    val words = input.substringBeforeLast(' ', "") + " " + fileNames.joinToString(" ")
    val tool = androidx.compose.runtime.remember(words, s.enabledTools) {
        AppTool.suggestFor(words, s.enabledToolIds, IS_ANDROID)?.takeIf { it.id !in dismissedTools }
    } ?: return
    Row(
        Modifier.padding(bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        LoraFaceStill(Modifier.size(28.dp))
        Text(
            tr("Dafür gibt es") + " „${tool.label}“ – " + tr("einschalten?"),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f, fill = false),
        )
        androidx.compose.material3.TextButton(onClick = {
            c.settings.update { it.withTool(tool, true) }
            Notices.info("„${tool.label}“ " + tr("ist eingeschaltet – in der Seitenleiste unter „Mehr“."))
        }) { Text(tr("Einschalten")) }
        androidx.compose.material3.IconButton(onClick = { dismissedTools += tool.id }) {
            androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Outlined.Close, contentDescription = tr("Nicht jetzt"))
        }
    }
}
