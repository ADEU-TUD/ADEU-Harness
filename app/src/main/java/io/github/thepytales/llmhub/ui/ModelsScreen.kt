package io.github.thepytales.llmhub.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.data.ModelCatalog
import io.github.thepytales.llmhub.data.ModelFilter
import io.github.thepytales.llmhub.data.ModelInfo
import io.github.thepytales.llmhub.data.ModelSort
import io.github.thepytales.llmhub.data.ModelTier
import io.github.thepytales.llmhub.data.ModelType

private val typeHelp = mapOf(
    ModelType.CHAT to "Zum Chatten, Programmieren und für die Werkzeuge. „Empfohlen“ = oben in der Modellauswahl; Modelle „nicht in der Auswahl“ sind veraltet oder nur ein anderer Name (Alias) für ein Modell.",
    ModelType.IMAGE to "Erzeugt Bilder aus Text – im Tab „Bilder“.",
    ModelType.TTS to "Liest Antworten vor (Lautsprecher-Symbol an einer Antwort).",
    ModelType.STT to "Wandelt Sprache in Text (Mikrofon-Symbol im Chat).",
    ModelType.EMBEDDING to "Erzeugt Vektoren für semantische Suche/RAG – nur über die API nutzbar.",
    ModelType.RERANK to "Sortiert Dokumente nach Relevanz – nur über die API nutzbar.",
    ModelType.EVALUATION to "Bewertet Texte – nur über die API nutzbar.",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelsScreen(c: AppContainer, onBack: () -> Unit, onStartChat: (String) -> Unit) {
    val state by c.models.state.collectAsStateWithLifecycle()
    val uri = LocalUriHandler.current
    var filter by remember { mutableStateOf(ModelFilter()) }
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { BackButton(onBack) },
                title = { Text("Modelle") },
                actions = {
                    IconButton(onClick = { uri.openUri("https://llm.scads.ai/status/") }) {
                        Icon(Icons.Outlined.Info, contentDescription = "Statusseite")
                    }
                    if (state.loading) {
                        CircularProgressIndicator(Modifier.size(24.dp).padding(end = 4.dp), strokeWidth = 2.dp)
                    } else {
                        IconButton(onClick = { c.models.refresh() }) {
                            Icon(Icons.Outlined.Refresh, contentDescription = "Aktualisieren")
                        }
                    }
                },
            )
        },
    ) { pad ->
        val shown = remember(state, filter) { filter.apply(state.models) }
        val grouped = shown.groupBy { it.type }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = pad.calculateTopPadding(), bottom = pad.calculateBottomPadding() + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "filter") {
                ModelFilterBar(
                    filter = filter,
                    onChange = { filter = it },
                    types = state.models.map { it.type }.distinct(),
                )
            }
            item(key = "info") {
                val text = when {
                    filter.isActive -> "${shown.size} von ${state.models.size} Modellen passen zum Filter."
                    state.error != null -> "Liste konnte nicht geladen werden (${state.error}). Angezeigt wird der eingebaute Katalog."
                    state.liveIds != null -> "${state.liveIds!!.size} Modelle live vom Server geladen."
                    else -> "Eingebauter Katalog – mit API-Key wird die aktuelle Liste vom Server geladen."
                }
                Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (filter.sort != ModelSort.DEFAULT) {
                // A ranking only makes sense as one list.
                items(shown, key = { it.id }) { m -> ModelCard(m, onStartChat) }
            } else ModelType.entries.forEach { type ->
                val list = grouped[type].orEmpty()
                if (list.isEmpty()) return@forEach
                item(key = "h_$type") {
                    Column(Modifier.padding(top = 8.dp)) {
                        Text(type.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            typeHelp[type].orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(list, key = { it.id }) { m -> ModelCard(m, onStartChat) }
            }
        }
    }
}

@Composable
private fun ModelCard(m: ModelInfo, onStartChat: (String) -> Unit) {
    val offered = m.type != ModelType.CHAT || m.tier != ModelTier.HIDDEN
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = LocalChatLook.current.card)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(m.displayName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                val tag = when {
                    m.type != ModelType.CHAT -> null
                    m.tier == ModelTier.FEATURED -> "Empfohlen"
                    m.tier == ModelTier.HIDDEN -> "Nicht in der Auswahl"
                    else -> m.badge
                }
                tag?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (m.tier == ModelTier.FEATURED) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (m.type == ModelType.CHAT && m.useCase.isNotBlank()) {
                Text(m.useCase, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            }
            val sub = listOfNotNull(m.provider, m.aliasOf?.let { "Alias für $it" }).joinToString(" · ")
            if (sub.isNotEmpty()) {
                Text(sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(m.summary, style = MaterialTheme.typography.bodyMedium)
            if (m.strengths.isNotBlank()) {
                Text(m.strengths, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            CapabilityChips(m)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    val e = ModelCatalog.effective(m)
                    e.power?.let { RatingRow("Leistung", it) }
                    e.speed?.let { RatingRow("Tempo   ", it) }
                }
                if (m.type == ModelType.CHAT && offered) {
                    FilledTonalButton(onClick = { onStartChat(m.id) }) {
                        Icon(Icons.AutoMirrored.Outlined.Chat, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("Chatten")
                    }
                }
            }
        }
    }
}
