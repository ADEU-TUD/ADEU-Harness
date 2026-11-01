package io.github.thepytales.llmhub.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.data.ModelCatalog
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Compact number: 950, 12,4k, 1,3 Mio. */
fun compact(n: Long): String = when {
    n >= 1_000_000 -> String.format(Locale.GERMANY, tr("%.1f Mio."), n / 1_000_000.0)
    n >= 10_000 -> String.format(Locale.GERMANY, "%.0fk", n / 1000.0)
    n >= 1_000 -> String.format(Locale.GERMANY, "%.1fk", n / 1000.0)
    else -> n.toString()
}

private data class DayUsage(val date: LocalDate, val requests: Int, val prompt: Long, val completion: Long) {
    val tokens get() = prompt + completion
}

private enum class Metric(val label: String) { TOKENS("Tokens"), REQUESTS(trLater("Anfragen")) }

private val dayFormat = DateTimeFormatter.ofPattern("EEE, d. MMM", Locale.GERMAN)
private val shortFormat = DateTimeFormatter.ofPattern("d.M.", Locale.GERMAN)

/** Requests and tokens per day, counted locally on the device. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsageScreen(c: AppContainer, onBack: () -> Unit) {
    val entries by c.usage.entries.collectAsStateWithLifecycle()
    var range by remember { mutableIntStateOf(14) }
    var metric by remember { mutableStateOf(Metric.TOKENS) }
    var confirmReset by remember { mutableStateOf(false) }
    val today = LocalDate.now()
    val days = remember(entries, range) {
        (range - 1 downTo 0).map { back ->
            val d = today.minusDays(back.toLong())
            val list = entries.filter { it.day == d.toString() }
            DayUsage(d, list.sumOf { it.requests }, list.sumOf { it.promptTokens }, list.sumOf { it.completionTokens })
        }
    }
    var selected by remember(range) { mutableIntStateOf(range - 1) }
    val first = today.minusDays((range - 1).toLong()).toString()
    val inRange = entries.filter { it.day >= first }
    val todayUsage = days.last()

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { BackButton(onBack) },
                title = { Text(tr("Verbrauch")) },
                actions = {
                    IconButton(onClick = { confirmReset = true }) { Icon(Icons.Outlined.DeleteOutline, contentDescription = tr("Zurücksetzen")) }
                },
            )
        },
    ) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(tr("Anfragen heute"), todayUsage.requests.toString(), Modifier.weight(1f))
                StatTile(tr("Tokens heute"), compact(todayUsage.tokens), Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(tr("Anfragen · {0} Tage", range), inRange.sumOf { it.requests }.toString(), Modifier.weight(1f))
                StatTile(tr("Tokens · {0} Tage", range), compact(inRange.sumOf { it.totalTokens }), Modifier.weight(1f))
            }

            Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(tr("{0} pro Tag", metric.label), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        listOf(7, 14, 30).forEach { r ->
                            FilterChip(
                                selected = range == r,
                                onClick = { range = r },
                                label = { Text("$r T") },
                                modifier = Modifier.padding(start = 4.dp),
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Metric.entries.forEach { m ->
                            FilterChip(selected = metric == m, onClick = { metric = m }, label = { Text(m.label) })
                        }
                    }
                    val sel = days.getOrNull(selected)
                    if (sel != null) {
                        Text(
                            tr("{0}: {1} Anfragen · {2} Tokens ", sel.date.format(dayFormat), sel.requests, compact(sel.tokens)) +
                                tr("(Eingabe {0}, Ausgabe {1})", compact(sel.prompt), compact(sel.completion)),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    BarChart(
                        values = days.map { if (metric == Metric.TOKENS) it.tokens else it.requests.toLong() },
                        selected = selected,
                        onSelect = { selected = it },
                    )
                    Row {
                        Text(days.first().date.format(shortFormat), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.weight(1f))
                        Text(tr("Heute"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            UsageTable(tr("Nach Modell"), inRange.groupBy { it.model }.map { (model, list) ->
                Triple(ModelCatalog.info(model).displayName, list.sumOf { it.requests }, list.sumOf { it.totalTokens })
            }.sortedByDescending { it.third + it.second })

            val kinds = mapOf("chat" to tr("Chat"), "image" to tr("Bilder"), "tts" to tr("Vorlesen"), "stt" to tr("Spracheingabe"))
            UsageTable(tr("Nach Art"), inRange.groupBy { it.kind }.map { (kind, list) ->
                Triple(kinds[kind] ?: kind, list.sumOf { it.requests }, list.sumOf { it.totalTokens })
            }.sortedByDescending { it.second })

            UsageTable(tr("Tage"), days.reversed().filter { it.requests > 0 }.map { Triple(it.date.format(dayFormat), it.requests, it.tokens) })

            // Ratings of answers (👍/👎 in the chat): which models have proven themselves for this user.
            val chats by c.chats.conversations.collectAsStateWithLifecycle()
            val rated = remember(chats) { chats.flatMap { cv -> cv.messages.filter { it.rating != null }.map { (it.model ?: cv.model) to it } } }
            if (rated.isNotEmpty()) {
                Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(tr("Bewertungen nach Modell"), style = MaterialTheme.typography.titleSmall)
                        rated.groupBy { it.first }.map { (model, list) ->
                            Triple(ModelCatalog.info(model).displayName, list.count { it.second.rating == 1 }, list.count { it.second.rating == -1 })
                        }.sortedByDescending { it.second - it.third }.forEach { (name, up, down) ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                Text("👍 $up   👎 $down", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        val reasons = rated.mapNotNull { it.second.ratingNote }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(4)
                        if (reasons.isNotEmpty()) Text(
                            tr("Häufigste Gründe für 👎: ") + reasons.joinToString(", ") { "${it.key} (${it.value})" },
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            val estimated = inRange.sumOf { it.estimated }
            Text(
                buildString {
                    append(tr("Gezählt wird nur lokal auf diesem Gerät – es werden keine Daten übertragen.")).append(" ")
                    if (estimated > 0) {
                        append(tr("Bei {0} Anfragen hat der Server keine Token-Zahlen geliefert; diese sind geschätzt (≈ 4 Zeichen pro Token).", estimated))
                    } else {
                        append(tr("Die Token-Zahlen stammen vom Server."))
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (confirmReset) {
        ConfirmDialog(tr("Verbrauch zurücksetzen?"), tr("Alle gezählten Anfragen und Tokens werden gelöscht."), tr("Zurücksetzen"),
            onConfirm = { c.usage.clear() }, onDismiss = { confirmReset = false })
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(16.dp), modifier = modifier) {
        Column(Modifier.padding(14.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun UsageTable(title: String, rows: List<Triple<String, Int, Long>>) {
    if (rows.isEmpty()) return
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(14.dp)) {
            Row {
                Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text(tr("Anfragen"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 16.dp))
                Text(tr("Tokens"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            rows.forEachIndexed { i, (name, requests, tokens) ->
                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text(requests.toString(), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(end = 16.dp))
                    Text(compact(tokens), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

/** Single-series column chart; tap a day to see its numbers. */
@Composable
private fun BarChart(values: List<Long>, selected: Int, onSelect: (Int) -> Unit) {
    val color = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val max = (values.maxOrNull() ?: 0L).coerceAtLeast(1L)
    Row(verticalAlignment = Alignment.Top) {
        Canvas(
            Modifier
                .weight(1f)
                .height(150.dp)
                .pointerInput(values.size) {
                    detectTapGestures { pos ->
                        val slot = size.width.toFloat() / values.size
                        onSelect((pos.x / slot).toInt().coerceIn(0, values.lastIndex))
                    }
                }
        ) {
            val slot = size.width / values.size
            val gap = 2.dp.toPx()
            val barW = (slot - gap).coerceIn(2f, 24.dp.toPx())
            val radius = minOf(4.dp.toPx(), barW / 2)
            // Recessive hairlines: baseline and the top (maximum).
            drawLine(grid, Offset(0f, size.height - 0.5f), Offset(size.width, size.height - 0.5f), strokeWidth = 1f)
            drawLine(grid.copy(alpha = 0.5f), Offset(0f, 0.5f), Offset(size.width, 0.5f), strokeWidth = 1f)
            values.forEachIndexed { i, v ->
                if (v <= 0) return@forEachIndexed
                val h = (v.toFloat() / max * (size.height - 4f)).coerceAtLeast(2f)
                val left = i * slot + (slot - barW) / 2
                val top = size.height - h
                val path = Path().apply {
                    addRoundRect(
                        RoundRect(
                            left = left, top = top, right = left + barW, bottom = size.height,
                            topLeftCornerRadius = CornerRadius(radius), topRightCornerRadius = CornerRadius(radius),
                            bottomRightCornerRadius = CornerRadius.Zero, bottomLeftCornerRadius = CornerRadius.Zero,
                        )
                    )
                }
                drawPath(path, if (i == selected) color else color.copy(alpha = 0.45f))
            }
            if (selected in values.indices) {
                // Marks the tapped day even when it has no usage.
                val x = selected * slot + slot / 2
                drawRect(color, topLeft = Offset(x - barW / 2, size.height - 2f), size = Size(barW, 2f))
            }
        }
        Text(
            compact(max),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}
