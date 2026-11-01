package io.github.thepytales.llmhub.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * The card under a chat answer that made or changed a design (AP-24). The Kreativbereich is part of the Windows app
 * only; Android shows where to find the design. Windows has its own version of this file (preview and „Öffnen“).
 */
@Composable
internal fun DesignCard(id: String) {
    val c = io.github.thepytales.llmhub.App.container
    val designs by c.designs.items.collectAsStateWithLifecycle()
    val d = designs.firstOrNull { it.id == id } ?: return
    Surface(shape = MaterialTheme.shapes.medium, color = LocalChatLook.current.card, border = boxBorder(), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(d.title, style = MaterialTheme.typography.titleSmall)
            Text(tr("Im Kreativbereich der Windows-App bearbeiten und exportieren."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Android has no design editor (AP-24): „Erstellen“ shows the TU Vorlagen only. */
@Suppress("UNUSED_PARAMETER")
internal fun androidx.compose.foundation.lazy.LazyListScope.designSection(c: io.github.thepytales.llmhub.AppContainer) = Unit
