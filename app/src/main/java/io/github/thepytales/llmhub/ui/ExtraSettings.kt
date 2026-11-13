package io.github.thepytales.llmhub.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.data.ModelCatalog
import io.github.thepytales.llmhub.data.ModelType

/** A settings card like the other sections (shared by Android and Windows). */
@Composable
fun SettingsCard(title: String, content: @Composable () -> Unit) {
    // Corners and frame of the style like every other card (AP-123).
    val look = LocalChatLook.current
    Card(Modifier.fillMaxWidth(), shape = look.cardShape, colors = CardDefaults.cardColors(containerColor = look.card), border = look.cardBorder) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
fun SettingsHint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Fallback model and request pacing (Einstellungen › Chat & Werkzeuge). */
@Composable
fun ReliabilitySection(c: AppContainer) {
    val s by c.settings.settings.collectAsStateWithLifecycle()
    val models by c.models.state.collectAsStateWithLifecycle()
    SettingsCard(tr("Zuverlässigkeit")) {
        val auto = tr("Automatisch (ähnliches Modell)")
        val off = tr("Aus")
        val chat = models.ofType(ModelType.CHAT).map { it.id }
        val current = when (s.fallbackModel.lowercase()) {
            "auto", "" -> auto
            "off" -> off
            else -> ModelCatalog.info(s.fallbackModel).displayName
        }
        DropdownField(
            label = tr("Ausweichmodell"),
            value = current,
            options = listOf(auto, off) + chat.map { ModelCatalog.info(it).displayName },
            onSelect = { v ->
                val id = when (v) {
                    auto -> "auto"
                    off -> "off"
                    else -> chat.firstOrNull { ModelCatalog.info(it).displayName == v } ?: "auto"
                }
                c.settings.update { it.copy(fallbackModel = id) }
            },
        )
        SettingsHint(
            tr("Erreicht der Uni-Server das gewählte Modell auch nach mehreren Versuchen nicht (HTTP 502/503/504), beantwortet " +
                "dieses Modell die Anfrage – mit Hinweis im Chat und im Agent-Protokoll.")
        )
        val learned = c.llm.limiter.limit()
        DropdownField(
            label = tr("Anfragen pro Minute"),
            value = if (s.requestsPerMinute <= 0) tr("Automatisch") else s.requestsPerMinute.toString(),
            options = listOf(tr("Automatisch"), "5", "10", "15", "20", "30", "60"),
            onSelect = { v -> c.settings.update { it.copy(requestsPerMinute = v.toIntOrNull() ?: 0) } },
        )
        SettingsHint(
            tr("Die App verteilt Anfragen so, dass das Rate-Limit des API-Keys nicht erreicht wird. „Automatisch“ lernt die " +
                "Grenze aus den Antworten des Servers") + (if (s.requestsPerMinute <= 0 && learned != null) tr(" (zurzeit {0} pro Minute).", learned) else ".")
        )
        // Stapel (AP-20): how much of the limit a batch may take, so chats stay quick.
        DropdownField(
            label = tr("Stapel: Anfragen pro Minute"),
            value = if (s.batchPerMinute <= 0) tr("Automatisch") else s.batchPerMinute.toString(),
            options = listOf(tr("Automatisch"), "2", "4", "6", "10", "20"),
            onSelect = { v -> c.settings.update { it.copy(batchPerMinute = v.toIntOrNull() ?: 0) } },
        )
        SettingsHint(
            tr("Stapel und Massenkontrolle warten, solange ein Chat antwortet. „Automatisch“ lässt zwei Anfragen pro Minute für " +
                "Chats frei, wenn das Limit bekannt ist.")
        )
    }
}
