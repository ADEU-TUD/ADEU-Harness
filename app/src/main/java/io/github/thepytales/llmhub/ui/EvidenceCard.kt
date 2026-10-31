package io.github.thepytales.llmhub.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FactCheck
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.thepytales.llmhub.agent.CheckedCitation
import io.github.thepytales.llmhub.agent.CitationStatus
import io.github.thepytales.llmhub.agent.Evidence
import io.github.thepytales.llmhub.agent.EvidenceReport
import io.github.thepytales.llmhub.agent.EvidenceSource
import io.github.thepytales.llmhub.data.Conversation
import io.github.thepytales.llmhub.data.thread
import kotlinx.coroutines.flow.MutableStateFlow

/** Opens a PDF in the PDF-Werkzeugkasten at a page (AP-08 → AP-11); the app root navigates to it. */
object PdfHandoff {
    data class Request(val path: String, val page: Int = 1, val name: String? = null)

    val request = MutableStateFlow<Request?>(null)

    fun open(path: String, page: Int = 1, name: String? = null) {
        request.value = Request(path, page, name)
    }
}

/** Belegprotokolle of the finished answers in the Quellenmodus (AP-08), by the head message of each turn. */
internal fun evidenceReports(conv: Conversation, items: List<ChatItem>): Map<String, EvidenceReport> {
    if (conv.mode != io.github.thepytales.llmhub.agent.ChatModes.SOURCES.id) return emptyMap()
    val thread = conv.thread()
    val sources = Evidence.sourcesOf(thread)
    val out = HashMap<String, EvidenceReport>()
    items.filterIsInstance<ChatItem.Assistant>().filter { it.turnEnd }.forEach { item ->
        val report = Evidence.check(splitThinking(item.turnText).second, sources)
        if (!report.empty) item.turn.forEach { m -> out[m.id] = report }
    }
    return out
}

/** Where a click on marker [n] leads: the PDF page, the web page or the DOI. */
internal fun evidenceTargets(report: EvidenceReport): Map<Int, SourceTarget> = report.citations.associate { c ->
    val cit = c.citation
    val path = c.source?.path?.takeIf { it.endsWith(".pdf", ignoreCase = true) && java.io.File(it).isFile }
    val url = cit.url ?: c.source?.url ?: cit.doi?.let { "https://doi.org/$it" }
    val page = c.foundPages.firstOrNull()?.takeIf { c.status == CitationStatus.PAGE_MISMATCH } ?: cit.page ?: c.foundPages.firstOrNull() ?: 1
    cit.n to SourceTarget(
        label = (c.source?.label ?: cit.source) + (cit.page?.let { ", S. $it" } ?: ""),
        url = url,
        open = path?.let { p -> { PdfHandoff.open(p, page, c.source?.label) } },
    )
}

/**
 * Belegprotokoll under an answer in the Quellenmodus: one line with the result, unfolded every evidence with source,
 * page, quote and whether it was found; statements without evidence; „Exportieren“ makes a document of it.
 */
@Composable
internal fun EvidenceCard(report: EvidenceReport, onExport: (String) -> Unit) {
    var open by remember(report) { mutableStateOf(report.wrong > 0 || report.unsupported.isNotEmpty()) }
    val warn = report.wrong > 0 || report.unsupported.isNotEmpty() || report.dangling.isNotEmpty()
    val tone = if (warn) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    AccentBox {
        Column(Modifier.padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth().clickable { open = !open }, verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.FactCheck, contentDescription = null, tint = tone, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(tr("Belegprotokoll"), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = tone)
                    Text(report.summary(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, contentDescription = tr(if (open) "Zuklappen" else "Aufklappen"))
            }
            if (open) {
                report.citations.forEach { CitationRow(it) }
                if (report.unsupported.isNotEmpty()) {
                    Text(tr("Aussagen ohne Beleg"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
                    report.unsupported.forEach { s ->
                        Text("⚠ $s", style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (report.dangling.isNotEmpty()) {
                    Text(tr("Marker ohne Eintrag in der Liste") + ": " + report.dangling.joinToString { "[$it]" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                TextButton(onClick = { onExport(report.markdown()) }) {
                    Icon(Icons.Outlined.IosShare, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(6.dp))
                    Text(tr("Belegprotokoll exportieren"))
                }
            }
        }
    }
}

@Composable
private fun CitationRow(c: CheckedCitation) {
    val (icon, color) = when (c.status) {
        CitationStatus.VERIFIED -> Icons.Outlined.CheckCircle to Color(0xFF2E7D32)
        CitationStatus.NOT_FOUND, CitationStatus.PAGE_MISMATCH -> Icons.Outlined.ErrorOutline to MaterialTheme.colorScheme.error
        else -> Icons.Outlined.HelpOutline to MaterialTheme.colorScheme.onSurfaceVariant
    }
    val targets = remember(c) { evidenceTargets(EvidenceReport(listOf(c), emptyList(), emptyList(), 0)) }
    val target = targets[c.citation.n]
    val uri = androidx.compose.ui.platform.LocalUriHandler.current
    Row(
        Modifier.fillMaxWidth().clickable(enabled = target?.open != null || target?.url != null) {
            target?.open?.invoke() ?: target?.url?.let { runCatching { uri.openUri(it) } }
        },
        verticalAlignment = Alignment.Top,
    ) {
        Icon(icon, contentDescription = tr(c.status.label), tint = color, modifier = Modifier.padding(top = 2.dp).size(16.dp))
        Spacer(Modifier.size(8.dp))
        Column(Modifier.weight(1f)) {
            val where = listOfNotNull(
                c.source?.label ?: c.citation.source.ifBlank { null },
                c.citation.page?.let { "S. $it" },
                c.citation.time,
                c.citation.doi?.let { "doi:$it" },
            ).joinToString(", ")
            Text("[${c.citation.n}] $where", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            c.citation.quote?.let { Text("„$it“", style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis) }
            val detail = tr(c.status.label) + if (c.status == CitationStatus.PAGE_MISMATCH && c.foundPages.isNotEmpty()) " – S. ${c.foundPages.joinToString()}" else ""
            Text(detail, style = MaterialTheme.typography.labelSmall, color = color)
        }
    }
}

/** Kind of source as a word for the protocol. */
internal fun EvidenceSource.Kind.label(): String = when (this) {
    EvidenceSource.Kind.FILE -> "Datei"
    EvidenceSource.Kind.WEB -> "Web"
    EvidenceSource.Kind.KNOWLEDGE -> "Wissenssammlung"
    EvidenceSource.Kind.TRANSCRIPT -> "Transkript"
    EvidenceSource.Kind.LITERATURE -> "Literatur"
}
