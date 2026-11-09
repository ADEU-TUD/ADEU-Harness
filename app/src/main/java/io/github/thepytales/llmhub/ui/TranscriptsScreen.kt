package io.github.thepytales.llmhub.ui

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
    val day = runCatching { LocalDateTime.parse(created.take(19)).toLocalDate() }.getOrNull() ?: return tr("Älter")
    val days = ChronoUnit.DAYS.between(day, today)
    return when {
        days <= 0 -> tr("Heute")
        days == 1L -> tr("Gestern")
        days < 7 -> tr("Letzte 7 Tage")
        day.month == today.month && day.year == today.year -> tr("Diesen Monat")
        else -> tr("Älter")
    }
}

/** "Heute, 14:03" / "16. Okt. 2026" */
fun transcriptDate(created: String): String {
    val dt = runCatching { LocalDateTime.parse(created.take(19)) }.getOrNull() ?: return ""
    val days = ChronoUnit.DAYS.between(dt.toLocalDate(), LocalDate.now())
    val time = dt.format(DateTimeFormatter.ofPattern("HH:mm"))
    return when (days) {
        0L -> tr("Heute, {0}", time)
        1L -> tr("Gestern, {0}", time)
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
        runCatching { t.import(name, size, input) }.onFailure { Notices.error(it.message ?: tr("Fehler beim Einlesen")) }
    }
    val record = rememberMicPermission {
        runCatching { t.startRecording(t.defaultOptions(folder)); showRecording = true }
            .onFailure { Notices.error(tr("Aufnahme nicht möglich: {0}", it.message)) }
    }

    val hits by produceState(emptyList<Pair<String, String>>(), query, list) { value = if (query.isBlank()) emptyList() else t.search(query) }

    if (state.recording.active && showRecording) {
        RecordingScreen(c, folder, onMinimize = { showRecording = false })
        return
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            // "Datei öffnen" and "Aufnehmen" side by side in a bar of their own, so they never cover the list.
            Surface(color = MaterialTheme.colorScheme.background, shadowElevation = 6.dp) {
                Row(
                    Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Row(Modifier.widthIn(max = 560.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            onClick = pickFile, shape = RoundedCornerShape(28.dp), color = LocalChatLook.current.card,
                            border = LocalChatLook.current.cardBorder, modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                        ) {
                            Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.FolderOpen, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(tr("Datei öffnen"), style = MaterialTheme.typography.titleSmall)
                            }
                        }
                        Box(Modifier.weight(1.3f)) {
                            if (state.recording.active) {
                                RecordPill(tr("Läuft · {0}", TranscriptExport.clock(state.recording.seconds))) { showRecording = true }
                            } else {
                                RecordPill(tr("Aufnehmen"), enabled = available != false, onClick = record)
                            }
                        }
                    }
                }
            }
        },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                navigationIcon = { BackButton(onBack) },
                title = {
                    if (searching) {
                        OutlinedTextField(
                            query, { query = it }, placeholder = { Text(tr("Transkripte durchsuchen")) }, singleLine = true,
                            shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth().padding(end = 8.dp),
                        )
                    } else Text(tr("Transkription"))
                },
                actions = {
                    IconButton(onClick = { searching = !searching; if (!searching) query = "" }) {
                        Icon(if (searching) Icons.Outlined.Close else Icons.Outlined.Search, contentDescription = if (searching) tr("Suche schließen") else tr("Suchen"))
                    }
                    if (!searching) IconButton(onClick = { onNavigate("settings/transcription") }) { Icon(Icons.Outlined.Settings, contentDescription = tr("Einstellungen der Transkription")) }
                },
            )
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val wide = Modifier.widthIn(max = 760.dp).fillMaxWidth()
                if (searching && query.isNotBlank()) {
                    if (hits.isEmpty()) item { HintText(tr("Keine Treffer."), wide.padding(8.dp)) }
                    items(hits, key = { "s" + it.first }) { (id, snippet) ->
                        val s = list.firstOrNull { it.id == id }
                        AppCard(wide, onClick = { onOpen(id) }) {
                            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                                Text(s?.title ?: tr("Transkript"), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                            TitleBlock(tr("Transkription auf diesem Gerät nicht verfügbar"), t.unavailableReason)
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
                            TitleBlock(f.name, tr("Wartet auf den Start – tippen zum Einstellen"), Modifier.weight(1f))
                        }
                    }
                }
                val jobs = state.jobs.sortedWith(compareByDescending<SpeechJob> { it.active }.thenBy { it.id })
                items(jobs.filter { it.status != "done" }, key = { "j" + it.id }) { j -> JobCard(c, j, jobText[j.id]?.lastOrNull(), wide, onOpen) }
                jobs.filter { it.status == "done" }.takeIf { it.isNotEmpty() }?.let { done -> item(key = "done") { DoneJobs(c, done, wide, onOpen) } }

                if (list.isNotEmpty() && folders.any { f -> list.any { it.folder == f.id } }) item {
                    Row(wide.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = folder == null, onClick = { folder = null }, label = { Text(tr("Alle  {0}", list.size)) })
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
                        Text(tr("Noch keine Transkripte"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 14.dp))
                        HintText(tr("Nimm ein Gespräch auf oder öffne eine Audio- oder Videodatei – Interviews, Sitzungen, Vorlesungen, Sprachnachrichten."), Modifier.padding(top = 4.dp).widthIn(max = 360.dp))
                    }
                }
                val today = LocalDate.now()
                shown.groupBy { group(it.created, today) }.forEach { (g, items) ->
                    item(key = "g$g") { GroupLabel(g, wide.padding(top = 6.dp)) }
                    item(key = "c$g") {
                        AppCard(wide) {
                            items.forEachIndexed { n, s ->
                                if (n > 0) androidx.compose.material3.HorizontalDivider(Modifier.padding(start = 68.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                                TranscriptRow(c, s) { onOpen(s.id) }
                            }
                        }
                    }
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
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 18.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(14.dp).clip(CircleShape).background(Color.White))
            Spacer(Modifier.width(10.dp))
            Text(label, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), maxLines = 1)
        }
    }
}

/** First start: load a recognition model or use the Uni server. */
@Composable
private fun Onboarding(c: AppContainer, modifier: Modifier, onNavigate: (String) -> Unit) {
    val t = c.transcription
    AppCard(modifier) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(tr("So geht's los"), style = MaterialTheme.typography.titleLarge)
            HintText(
                tr("Für die Transkription auf diesem Gerät wird einmalig ein Erkennungsmodell geladen. Danach bleibt alles " +
                    "offline – auch vertrauliche Interviews. Alternativ übernimmt der Uni-Server der TU Dresden."),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { t.download("parakeet"); onNavigate("settings/transcription") }) { Text(tr("Modell laden (670 MB)")) }
                if (c.settings.current.hasApiKey) OutlinedButton(onClick = { t.updateSettings { it.copy(mode = TranscriptionMode.SERVER.name) } }) { Text(tr("Uni-Server nutzen")) }
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
    var confirmCancel by remember { mutableStateOf(false) }
    // Own estimate when the engine gives none: time so far per progress, from the first progress seen.
    val start = remember(j.id) { mutableStateOf<Pair<Long, Double>?>(null) }
    if (j.status == "running" && j.progress > 0 && start.value == null) start.value = System.currentTimeMillis() to j.progress
    val eta = j.eta ?: start.value?.let { (t0, p0) ->
        val done = j.progress - p0
        if (done < 0.03) null else (System.currentTimeMillis() - t0) / 1000.0 / done * (1 - j.progress)
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
                            "running" -> j.stage + (if (pct > 0) " · $pct %" else "") + (eta?.let { tr(" · noch ca. {0}", etaText(it)) } ?: "")
                            "done" -> tr("Fertig – tippen zum Öffnen")
                            "error" -> j.error ?: tr("Fehler")
                            else -> j.stage
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (j.status == "error") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { if (j.status == "running") confirmCancel = true else if (j.active) t.cancel(j.id) else t.dismiss(j.id) }) {
                    Icon(Icons.Outlined.Close, contentDescription = if (j.active) tr("Abbrechen") else tr("Ausblenden"), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            j.notice?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = look.warning, modifier = Modifier.padding(top = 6.dp, end = 10.dp)) }
            if (confirmCancel) ConfirmDialog(
                tr("Transkription abbrechen?"), tr("„{0}“ ist zu {1} % fertig. Der bisherige Fortschritt geht verloren.", j.name, (j.progress * 100).toInt()),
                tr("Abbrechen"), onConfirm = { t.cancel(j.id) }, onDismiss = { confirmCancel = false },
            )
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

/** Finished transcriptions in one compact card: tap to open, × to hide. */
@Composable
private fun DoneJobs(c: AppContainer, done: List<SpeechJob>, modifier: Modifier, onOpen: (String) -> Unit) {
    val look = LocalChatLook.current
    AppCard(modifier) {
        done.forEachIndexed { n, j ->
            if (n > 0) androidx.compose.material3.HorizontalDivider(Modifier.padding(start = 48.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            Row(
                Modifier.fillMaxWidth().then(j.transcriptId?.let { id -> Modifier.clickable { onOpen(id) } } ?: Modifier).padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.TaskAlt, contentDescription = null, tint = look.success, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(j.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(tr("Fertig – tippen zum Öffnen"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { c.transcription.dismiss(j.id) }) {
                    Icon(Icons.Outlined.Close, contentDescription = tr("Ausblenden"), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

/** One transcript in the day's card: icon with duration, title, details and the first words. */
@Composable
private fun TranscriptRow(c: AppContainer, s: TranscriptSummary, onClick: () -> Unit) {
    val folders by c.folders.items.collectAsStateWithLifecycle()
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.Top) {
        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), modifier = Modifier.size(40.dp)) {
            Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.GraphicEq, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)) }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(s.title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (s.status != TranscriptStatus.RAW) StatusPill(s.status.label, if (s.status == TranscriptStatus.CHECKED) PillTone.SUCCESS else PillTone.ACCENT)
            }
            Row(Modifier.padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                MetaText(null, transcriptDate(s.created))
                MetaText(Icons.Outlined.Schedule, TranscriptExport.clock(s.duration))
                if (s.speakers > 1) MetaText(Icons.Outlined.People, s.speakers.toString())
                s.folder?.let { id -> folders.firstOrNull { it.id == id } }?.let { MetaText(Icons.Outlined.Folder, it.name) }
                if (s.wordTimes) MetaText(Icons.Outlined.Timer, tr("Wortzeiten"))
            }
            if (s.preview.isNotBlank()) {
                Text(s.preview, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
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
