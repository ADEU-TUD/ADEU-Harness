package io.github.thepytales.llmhub.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.research.CitationStyle
import io.github.thepytales.llmhub.research.LitSource
import kotlinx.coroutines.launch

/** Einstellungen › Literatur & Zotero: citation style, databases, OpenAlex key, Zotero access. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LiteratureSettings(c: AppContainer) {
    val s by c.settings.settings.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val uri = LocalUriHandler.current
    var status by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }
    Section(tr("Literaturverzeichnis")) {
        ChoiceField(tr("Standard-Zitierstil"), CitationStyle.entries.map { it.name to it.label }, s.citationStyle, { v -> c.settings.update { it.copy(citationStyle = v) } })
        HintText(CitationStyle.parse(s.citationStyle)?.description.orEmpty())
        HintText(tr("Alle Stile sortieren alphabetisch nach Erstautor:in; gleiche Autor:innen und Jahr bekommen a/b. Fehlende Angaben ergänzt die App aus Crossref, der Deutschen Nationalbibliothek, Google Books und OpenAlex."))
    }
    Section(tr("Literatursuche")) {
        Text(tr("Durchsuchte Datenbanken"), style = MaterialTheme.typography.labelLarge)
        val chosen = s.literatureSources.split(',').mapNotNull { LitSource.parse(it) }.toSet()
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            LitSource.entries.forEach { src ->
                FilterChip(src in chosen, {
                    val next = if (src in chosen) chosen - src else chosen + src
                    c.settings.update { it.copy(literatureSources = LitSource.entries.filter { e -> e in next }.joinToString(",") { e -> e.name }) }
                }, label = { Text(src.label) })
            }
        }
        LitSource.entries.forEach { HintText("${it.label}: ${it.description}") }
        SecretField(tr("OpenAlex-API-Schlüssel (kostenlos, empfohlen)"), s.openAlexKey) { v -> c.settings.update { it.copy(openAlexKey = v.trim()) } }
        HintText(tr("Ohne eigenen Schlüssel teilen sich alle Nutzer:innen hinter derselben IP-Adresse (z.B. im Uni-Netz) ein kleines Tageskontingent. Den Schlüssel gibt es kostenlos mit einer E-Mail-Adresse."))
        TextButton(onClick = { uri.openUri("https://openalex.org/settings/api") }) { Text(tr("Schlüssel bei OpenAlex holen")) }
    }
    Section("Zotero") {
        HintText(tr("Werke aus der Sammlung oder der Literatursuche direkt in die eigene Zotero-Bibliothek übertragen. Die User-ID und einen Schlüssel mit Schreibrechten gibt es unter zotero.org › Einstellungen › Feeds/API."))
        OutlinedTextField(
            s.zoteroUserId, { v -> c.settings.update { it.copy(zoteroUserId = v.filter(Char::isDigit)) } },
            label = { Text(tr("User-ID (Zahl)")) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        SecretField(tr("API-Schlüssel"), s.zoteroKey) { v -> c.settings.update { it.copy(zoteroKey = v.trim()) } }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(enabled = !testing && s.zoteroUserId.isNotBlank() && s.zoteroKey.isNotBlank(), onClick = {
                testing = true
                scope.launch {
                    status = runCatching { c.researchTools.zotero.collections() }
                        .fold({ tr("✓ Verbunden – {0} Sammlungen", it.size) }, { "✗ ${it.message}" })
                    testing = false
                }
            }) { Text(tr("Verbindung testen")) }
            if (testing) CircularProgressIndicator(Modifier.padding(start = 12.dp).size(20.dp), strokeWidth = 2.dp)
        }
        status?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        TextButton(onClick = { uri.openUri("https://www.zotero.org/settings/keys/new") }) { Text(tr("Schlüssel bei Zotero anlegen")) }
    }
}
