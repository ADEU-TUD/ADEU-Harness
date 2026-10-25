package io.github.thepytales.llmhub.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.transcribe.SpeechJob
import io.github.thepytales.llmhub.transcribe.TranscriptExport
import io.github.thepytales.llmhub.transcribe.TranscriptStatus
import io.github.thepytales.llmhub.transcribe.TranscriptSummary
import io.github.thepytales.llmhub.transcribe.TranscriptionMode
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Day groups of the transcript list. */
private fun group(created: String, today: LocalDate): String {
    val day = runCatching { LocalDateTime.parse(created.take(19)).toLocalDate() }.getOrNull() ?: return "Älter"
    val days = ChronoUnit.DAYS.between(day, today)
    return when {
        days <= 0 -> "Heute"
        days == 1L -> "Gestern"
        days < 7 -> "Letzte 7 Tage"
        day.month == today.month && day.year == today.year -> "Diesen Monat"
        else -> "Älter"
    }
}

/** "Heute, 14:03" / "16. Okt. 2026" */
fun transcriptDate(created: String): String {
    val dt = runCatching { LocalDateTime.parse(created.take(19)) }.getOrNull() ?: return ""
    val days = ChronoUnit.DAYS.between(dt.toLocalDate(), LocalDate.now())
    val time = dt.format(DateTimeFormatter.ofPattern("HH:mm"))
    return when (days) {
        0L -> "Heute, $time"
        1L -> "Gestern, $time"
        else -> dt.format(DateTimeFormatter.ofPattern("d. MMM yyyy", Locale.GERMAN))
    }
}

/**
 * Start page of the transcription: running jobs, transcripts by day (filterable by project), search, and at the
 * bottom "Datei" and the red "Aufnehmen" button – like the former Whisper app.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranscriptsScreen(c: AppContainer, onBack: () -> Unit, onOpen: (String) -> Unit, onNavigate: (String) -> Unit) {
    val t = c.transcription
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by t.state.collectAsStateWithLifecycle()
    val list by t.transcripts.collectAsStateWithLifecycle()
    val incoming by t.incoming.collectAsStateWithLifecycle()
    val available by t.available.collectAsStateWithLifecycle()
    val jobText by t.jobText.collectAsStateWithLifecycle()
    val folders by c.folders.items.collectAsStateWithLifecycle()
    val settings by t.settings.collectAsStateWithLifecycle()
    var folder by rememberSaveable { mutableStateOf<String?>(null) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var showRecording by remember { mutableStateOf(true) }
    /** Start dialogs closed without a decision: the file waits as a card until it is started or discarded. */
    var postponed by remember { mutableStateOf(setOf<String>()) }

    LaunchedEffect(Unit) {
        t.checkAvailable()
        t.refreshList()
    }
    LaunchedEffect(Unit) { t.messages.collect { Notices.info(it) } }

    val pickFile = rememberMediaImport { name, size, input ->
        runCatching { t.import(name, size, input) }.onFailure { Notices.error(it.message ?: "Fehler beim Einlesen") }
    }
    val record = rememberMicPermission {
        runCatching { t.startRecording(t.defaultOptions(folder)); showRecording = true }
            .onFailure { Notices.error("Aufnahme nicht möglich: ${it.message}") }
    }

    val hits by produceState(emptyList<Pair<String, String>>(), query, list) { value = if (query.isBlank()) emptyList() else t.search(query) }

    if (state.recording.active && showRecording) {
        RecordingScreen(c, folder, onMinimize = { showRecording = false })
        return
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                navigationIcon = { BackButton(onBack) },
                title = {
                    if (searching) {
                        OutlinedTextField(
                            query, { query = it }, placeholder = { Text("Transkripte durchsuchen") }, singleLine = true,
                            shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth().padding(end = 8.dp),
                        )
                    } else Text("Transkription")
                },
                actions = {
                    IconButton(onClick = { searching = !searching; if (!searching) query = "" }) {
                        Icon(if (searching) Icons.Outlined.Close else Icons.Outlined.Search, contentDescription = if (searching) "Suche schließen" else "Suchen")
                    }
                    if (!searching) IconButton(onClick = { onNavigate("settings/transcription") }) { Icon(Icons.Outlined.Settings, contentDescription = "Einstellungen der Transkription") }
                },
            )
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 140.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val wide = Modifier.widthIn(max = 760.dp).fillMaxWidth()
                if (searching && query.isNotBlank()) {
                    if (hits.isEmpty()) item { HintText("Keine Treffer.", wide.padding(8.dp)) }
                    items(hits, key = { "s" + it.first }) { (id, snippet) ->
                        val s = list.firstOrNull { it.id == id }
                        AppCard(wide, onClick = { onOpen(id) }) {
                            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                                Text(s?.title ?: "Transkript", style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                HintText(snippet)
                            }
                        }
                    }
                    return@LazyColumn
                }
                if (available == false) item {
                    AppCard(wide) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.width(12.dp))
                            TitleBlock("Transkription auf diesem Gerät nicht verfügbar", t.unavailableReason)
                        }
                    }
                }
                if (available != false && state.models.none { it.installed } && settings.mode == "LOCAL") item {
                    Onboarding(c, wide, onNavigate)
                }
                items(incoming.filter { it.token in postponed }, key = { "i" + it.token }) { f ->
                    AppCard(wide, onClick = { postponed = postponed - f.token }) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(12.dp))
                            TitleBlock(f.name, "Wartet auf den Start – tippen zum Einstellen", Modifier.weight(1f))
                        }
                    }
                }
                val jobs = state.jobs.sortedWith(compareByDescending<SpeechJob> { it.active }.thenBy { it.id })
                items(jobs, key = { "j" + it.id }) { j -> JobCard(c, j, jobText[j.id]?.lastOrNull(), wide, onOpen) }

                if (list.isNotEmpty() && folders.any { f -> list.any { it.folder == f.id } }) item {
                    Row(wide.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = folder == null, onClick = { folder = null }, label = { Text("Alle  ${list.size}") })
                        folders.filter { f -> list.any { it.folder == f.id } }.sortedBy { it.name.lowercase() }.forEach { f ->
                            FilterChip(
                                selected = folder == f.id, onClick = { folder = if (folder == f.id) null else f.id },
                                leadingIcon = { Icon(Icons.Outlined.Folder, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                label = { Text("${f.name}  ${list.count { it.folder == f.id }}") },
                            )
                        }
                    }
                }
                val shown = list.filter { folder == null || it.folder == folder }
                if (shown.isEmpty() && state.jobs.isEmpty()) item {
                    Column(wide.padding(top = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.11f), modifier = Modifier.size(72.dp)) {
                            Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.GraphicEq, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(34.dp)) }
                        }
                        Text("Noch keine Transkripte", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 14.dp))
                        HintText("Nimm ein Gespräch auf oder öffne eine Audio- oder Videodatei – Interviews, Sitzungen, Vorlesungen, Sprachnachrichten.", Modifier.padding(top = 4.dp).widthIn(max = 360.dp))
                    }
                }
                val today = LocalDate.now()
                shown.groupBy { group(it.created, today) }.forEach { (g, items) ->
                    item(key = "g$g") { GroupLabel(g, wide) }
                    items(items, key = { it.id }) { s -> TranscriptCard(c, s, wide) { onOpen(s.id) } }
                }
            }
            // Bottom: "Datei" and "Aufnehmen".
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, MaterialTheme.colorScheme.background, MaterialTheme.colorScheme.background)))
                    .navigationBarsPadding()
                    .padding(top = 28.dp, bottom = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(22.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(onClick = pickFile, shape = RoundedCornerShape(18.dp), color = LocalChatLook.current.card, border = LocalChatLook.current.cardBorder, shadowElevation = 3.dp, modifier = Modifier.size(56.dp)) {
                        Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.FolderOpen, contentDescription = "Datei öffnen") }
                    }
                    Text("Datei", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                }
                if (state.recording.active) {
                    RecordPill("Aufnahme läuft · ${TranscriptExport.clock(state.recording.seconds)}") { showRecording = true }
                } else {
                    RecordPill("Aufnehmen", enabled = available != false, onClick = record)
                }
            }
        }
    }

    incoming.firstOrNull { it.token !in postponed }?.let { f -> StartSheet(c, f, folder, onDone = { postponed = postponed + f.token }) }
}

@Composable
private fun RecordPill(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(32.dp),
        color = Color(0xFFE5484D).copy(alpha = if (enabled) 1f else 0.4f),
        contentColor = Color.White,
        shadowElevation = 8.dp,
    ) {
        Row(Modifier.height(64.dp).padding(start = 22.dp, end = 26.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(14.dp).clip(CircleShape).background(Color.White))
            Spacer(Modifier.width(10.dp))
            Text(label, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
        }
    }
}

/** First start: load a recognition model or use the Uni server. */
@Composable
private fun Onboarding(c: AppContainer, modifier: Modifier, onNavigate: (String) -> Unit) {
    val t = c.transcription
    AppCard(modifier) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("So geht's los", style = MaterialTheme.typography.titleLarge)
            HintText(
                "Für die Transkription auf diesem Gerät wird einmalig ein Erkennungsmodell geladen. Danach bleibt alles " +
                    "offline – auch vertrauliche Interviews. Alternativ übernimmt der Uni-Server der TU Dresden.",
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { t.download("parakeet"); onNavigate("settings/transcription") }) { Text("Modell laden (670 MB)") }
                if (c.settings.current.hasApiKey) OutlinedButton(onClick = { t.updateSettings { it.copy(mode = TranscriptionMode.SERVER.name) } }) { Text("Uni-Server nutzen") }
            }
        }
    }
}

@Composable
private fun JobCard(c: AppContainer, j: SpeechJob, lastText: String?, modifier: Modifier, onOpen: (String) -> Unit) {
    val t = c.transcription
    val look = LocalChatLook.current
    val (icon, tint) = when (j.status) {
        "done" -> Icons.Outlined.TaskAlt to look.success
        "error" -> Icons.Outlined.ErrorOutline to MaterialTheme.colorScheme.error
        "queued", "cancelled" -> Icons.Outlined.Schedule to MaterialTheme.colorScheme.onSurfaceVariant
        else -> null to MaterialTheme.colorScheme.primary
    }
    AppCard(modifier, onClick = j.transcriptId?.takeIf { j.status == "done" }?.let { id -> { onOpen(id) } }) {
        Column(Modifier.padding(start = 16.dp, end = 6.dp, top = 12.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(12.dp), color = tint.copy(alpha = 0.12f), modifier = Modifier.size(40.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        if (icon == null) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.5.dp, color = tint)
                        else Icon(icon, contentDescription = null, tint = tint)
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(j.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val pct = (j.progress * 100).toInt()
                    Text(
                        when (j.status) {
                            "running" -> j.stage + (if (pct > 0) " · $pct %" else "") + (j.eta?.let { " · noch ca. ${etaText(it)}" } ?: "")
                            "done" -> "Fertig – tippen zum Öffnen"
                            "error" -> j.error ?: "Fehler"
                            else -> j.stage
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (j.status == "error") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { if (j.active) t.cancel(j.id) else t.dismiss(j.id) }) {
                    Icon(Icons.Outlined.Close, contentDescription = if (j.active) "Abbrechen" else "Ausblenden", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            j.notice?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = look.warning, modifier = Modifier.padding(top = 6.dp, end = 10.dp)) }
            if (j.status == "running") {
                LinearProgressIndicator(
                    progress = { j.progress.toFloat().coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp, end = 10.dp).height(6.dp).clip(RoundedCornerShape(3.dp)),
                )
                lastText?.let { Text("„$it“", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp, end = 10.dp)) }
            }
        }
    }
}

private fun etaText(sec: Double): String = when {
    sec < 60 -> "${sec.toInt().coerceAtLeast(5)} s"
    sec < 3600 -> "${(sec / 60).toInt() + 1} min"
    else -> String.format(Locale.GERMANY, "%.1f h", sec / 3600)
}

@Composable
private fun TranscriptCard(c: AppContainer, s: TranscriptSummary, modifier: Modifier, onClick: () -> Unit) {
    val folders by c.folders.items.collectAsStateWithLifecycle()
    AppCard(modifier, onClick = onClick) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(s.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (s.status != TranscriptStatus.RAW) StatusPill(s.status.label, if (s.status == TranscriptStatus.CHECKED) PillTone.SUCCESS else PillTone.ACCENT)
            }
            Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                MetaText(null, transcriptDate(s.created))
                MetaText(Icons.Outlined.Schedule, TranscriptExport.clock(s.duration))
                if (s.speakers > 1) MetaText(Icons.Outlined.People, s.speakers.toString())
                s.folder?.let { id -> folders.firstOrNull { it.id == id } }?.let { MetaText(Icons.Outlined.Folder, it.name) }
                if (s.wordTimes) MetaText(Icons.Outlined.Timer, "Wortzeiten")
            }
            if (s.preview.isNotBlank()) {
                Text(s.preview, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}

@Composable
fun MetaText(icon: ImageVector?, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(3.dp))
        }
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline, maxLines = 1)
    }
}
