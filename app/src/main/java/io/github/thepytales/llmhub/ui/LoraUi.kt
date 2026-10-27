package io.github.thepytales.llmhub.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Shown instead of the thinking and the tool steps while the agent works (Einstellungen › Chat & Werkzeuge ›
 * Arbeitsschritte einklappen): a small figure that walks, reads, types, juggles, searches, dances or thinks, the
 * sentence „Der Agent arbeitet an einer Antwort, bitte warte kurz.“, the current step and a button for the details.
 */
@Composable
fun WorkingCard(step: String?, stepsCount: Int, open: Boolean, onToggle: (() -> Unit)?) {
    Surface(
        color = LocalChatLook.current.card,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(start = 8.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            LoraFigure(Modifier.size(width = 76.dp, height = 52.dp), label = tr("Lora bei der Arbeit"))
            Spacer(Modifier.size(8.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(tr("Der Agent arbeitet an einer Antwort, bitte warte kurz."), style = MaterialTheme.typography.bodyMedium)
                if (step != null) {
                    Text(
                        step,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (onToggle != null && stepsCount > 0) {
                TextButton(onClick = onToggle) {
                    Text(if (open) tr("Ausblenden") else tr("Schritte") + " ($stepsCount)", style = MaterialTheme.typography.labelMedium)
                    Icon(if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, contentDescription = null, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

/** A finished turn with folded steps: one quiet line „Gedankengang und 4 Arbeitsschritte“ that opens them. */
@Composable
fun StepsLine(thinking: Boolean, tools: Int, open: Boolean, onToggle: () -> Unit) {
    val parts = buildList {
        if (thinking) add(tr("Gedankengang"))
        if (tools == 1) add(tr("1 Arbeitsschritt")) else if (tools > 1) add("$tools " + tr("Arbeitsschritte"))
    }
    if (parts.isEmpty()) return
    TextButton(onClick = onToggle, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 0.dp)) {
        Text(
            parts.joinToString(" " + tr("und") + " "),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Icon(
            if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, contentDescription = if (open) tr("Einklappen") else tr("Anzeigen"),
            modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
