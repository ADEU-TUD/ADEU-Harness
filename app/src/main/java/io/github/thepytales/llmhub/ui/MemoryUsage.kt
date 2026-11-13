package io.github.thepytales.llmhub.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.data.MemoryMeter
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Subtitle of „Arbeitsspeicher“ in the settings overview. */
internal fun memorySummary(): String {
    val m = MemoryMeter.now()
    return tr("Java {0} von {1}", formatBytes(m.heapUsed), formatBytes(m.heapMax)) + (m.process?.let { tr(" · App gesamt {0}", formatBytes(it)) } ?: "")
}

/**
 * Einstellungen › Arbeitsspeicher (AP-04): what the app uses now (Java heap of its limit, the whole process), measuring
 * points with time and occasion, and buttons to take one or to free memory.
 */
@Composable
fun MemoryUsageSection(@Suppress("UNUSED_PARAMETER") c: AppContainer) {
    val now by produceState(MemoryMeter.now()) {
        while (true) {
            value = MemoryMeter.now()
            delay(2000)
        }
    }
    val samples by MemoryMeter.samples.collectAsStateWithLifecycle()
    Section(tr("Jetzt")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tr("Java-Speicher"), modifier = Modifier.weight(1f))
            Text("${formatBytes(now.heapUsed)} / ${formatBytes(now.heapMax)}", fontWeight = FontWeight.SemiBold)
        }
        LinearProgressIndicator(progress = { (now.heapUsed.toFloat() / now.heapMax.coerceAtLeast(1)).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
        HintText(tr("Vom System belegt:") + " ${formatBytes(now.heapCommitted)} · " + tr("dazu Programmcode und Zwischenspeicher:") + " ${formatBytes(now.nonHeap)}")
        now.process?.let {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tr("App gesamt (wie im Task-Manager)"), modifier = Modifier.weight(1f))
                Text(formatBytes(it), fontWeight = FontWeight.SemiBold)
            }
        }
        HintText(
            tr("Die App gibt freien Speicher nach etwa einer Minute ohne Arbeit an Windows zurück. Spracherkennung, die drei Minuten niemand nutzt, wird entladen. Lokale Sprachmodelle laufen als eigenes Programm und zählen hier nicht mit."),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { MemoryMeter.mark(tr("Von Hand")) }) { Text(tr("Messpunkt setzen")) }
            OutlinedButton(onClick = { MemoryMeter.release() }) { Text(tr("Speicher freigeben")) }
        }
    }
    Section(tr("Messpunkte")) {
        if (samples.isEmpty()) HintText(tr("Noch keine – sie entstehen beim Start, nach großen PDF-Anhängen, nach Transkriptionen und nach einer Stunde."))
        val time = SimpleDateFormat("HH:mm", Locale.GERMANY)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            samples.asReversed().forEach { s ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(time.format(Date(s.time)), style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(48.dp))
                    Text(s.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text(
                        "Java ${formatBytes(s.heapUsed)}" + (s.process?.let { " · ${formatBytes(it)}" } ?: ""),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
