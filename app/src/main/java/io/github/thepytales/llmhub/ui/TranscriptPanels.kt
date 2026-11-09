package io.github.thepytales.llmhub.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.data.Privacy
import io.github.thepytales.llmhub.data.ModelCatalog
import io.github.thepytales.llmhub.docs.DocFormat
import io.github.thepytales.llmhub.docs.DocumentWriter
import io.github.thepytales.llmhub.net.ThinkingLevel
import io.github.thepytales.llmhub.transcribe.Chapter
import io.github.thepytales.llmhub.transcribe.ExportOptions
import io.github.thepytales.llmhub.transcribe.Transcript
import io.github.thepytales.llmhub.transcribe.TranscriptChapters
import io.github.thepytales.llmhub.transcribe.TranscriptEdit
import io.github.thepytales.llmhub.transcribe.TranscriptExport
import io.github.thepytales.llmhub.transcribe.TranscriptFormat
import io.github.thepytales.llmhub.transcribe.TranscriptStatus
import java.io.File
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Bottom sheet with a title and scrolling content, as used by all panels of the transcript. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PanelSheet(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = LocalChatLook.current.sheet) {
        Column(
            Modifier.fillMaxWidth().heightIn(max = 760.dp).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            content()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> Chips(label: String, options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEach { (v, l) -> FilterChip(selected = selected == v, onClick = { onSelect(v) }, label = { Text(l) }) }
        }
    }
}

/** ⋮ › Ansicht & Zeitstempel: the same for all transcripts. */
@Composable
fun ViewSheet(c: AppContainer, onDismiss: () -> Unit) {
    val t = c.transcription
    val s by t.settings.collectAsStateWithLifecycle()
    PanelSheet(tr("Ansicht & Zeitstempel"), onDismiss) {
        Chips(tr("Zeitstempel"), TranscriptEdit.Stamps.entries.map { it.key to it.label }, s.stamps, { v -> t.updateSettings { it.copy(stamps = v) } })
        HintText(
            tr("„Je Satz“ und „Alle … s“ setzen die Zeit vor das Wort, bei dem sie gemessen wurde. Ohne gemessene Wortzeiten gibt es nur die Zeiten " +
                "der Abschnittsanfänge – geschätzt wird nichts. Der Export übernimmt diese Einstellung."),
        )
        Chips(
            tr("Absätze"),
            listOf("auto" to tr("Bei Sprecherwechsel und Pausen"), "speaker" to tr("Nur bei Sprecherwechsel"), "segment" to tr("Jeder Abschnitt")),
            s.paragraphs, { v -> t.updateSettings { it.copy(paragraphs = v) } },
        )
        Chips(tr("Schriftgröße"), listOf("small" to tr("Klein"), "normal" to tr("Normal"), "large" to tr("Groß"), "xlarge" to tr("Sehr groß")), s.textSize, { v -> t.updateSettings { it.copy(textSize = v) } })
        ToggleRow(tr("Aktuelles Wort markieren"), tr("Beim Abspielen wird das gerade gesprochene Wort hervorgehoben (nur gemessene Wortzeiten)."), s.wordHighlight) { v -> t.updateSettings { it.copy(wordHighlight = v) } }
        ToggleRow(tr("Text mitlaufen lassen"), tr("Beim Abspielen scrollt der Text mit."), s.follow) { v -> t.updateSettings { it.copy(follow = v) } }
        ToggleRow(tr("Kommentare anzeigen"), tr("Kommentare stehen klein unter ihrem Abschnitt."), s.showComments) { v -> t.updateSettings { it.copy(showComments = v) } }
        ToggleRow(tr("Nicht gemessene Wörter unterstreichen"), tr("Zeigt, wo eine Wortzeit fehlt oder nur zwischen gemessenen Nachbarn liegt."), s.markUnmeasured) { v -> t.updateSettings { it.copy(markUnmeasured = v) } }
        HorizontalDivider()
        Text(tr("Abspielen zum Korrigieren"), style = MaterialTheme.typography.titleSmall)
        Chips(tr("Beim Anhalten zurückspringen"), listOf(0.0 to tr("Aus"), 1.0 to "1 s", 2.0 to "2 s", 3.0 to "3 s", 5.0 to "5 s"), s.rewindOnPause, { v -> t.updateSettings { it.copy(rewindOnPause = v) } })
        Chips(tr("Sprungweite (Knöpfe, F3/F5)"), listOf(5 to "5 s", 10 to "10 s", 30 to "30 s"), s.skipSeconds, { v -> t.updateSettings { it.copy(skipSeconds = v) } })
        ToggleRow(tr("Pausen überspringen"), tr("Stille zwischen den Abschnitten (über 1,5 s) wird beim Abspielen übersprungen."), s.skipSilence) { v -> t.updateSettings { it.copy(skipSilence = v) } }
        HintText(tr("Tastatur (Windows, Tablet mit Tastatur), auch beim Tippen im Abschnitt: F4 Abspielen/Anhalten · F3 zurück · F5 vor · F6 Abschnitt wiederholen · Strg+F Suchen."))
    }
}

/** ⋮ › Sprecher: rename, merge, add by hand, remove. */
@Composable
fun SpeakersSheet(t: Transcript, onEdit: (Int) -> Unit, onSave: (Transcript) -> Unit, onDismiss: () -> Unit, c: AppContainer? = null, hasAudio: Boolean = false) {
    var add by remember { mutableStateOf(false) }
    var remove by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var voiceBusy by remember { mutableStateOf(false) }
    var matches by remember { mutableStateOf<List<io.github.thepytales.llmhub.transcribe.Voices.Match>?>(null) }
    PanelSheet(tr("Sprecher"), onDismiss) {
        if (!t.diarized) {
            HintText(tr("In diesem Transkript sind keine Sprecher erkannt. Legen Sie sie von Hand an: Alle Abschnitte gehören zuerst zu „Sprecher 1“, " +
                "einzelne Abschnitte ordnen Sie per langem Drücken zu."))
        }
        val stats = TranscriptEdit.stats(t).associateBy { it.speaker }
        t.speakers.keys.sorted().forEach { k ->
            AppCard(onClick = { onEdit(k) }) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(speakerColor(k)))
                    Spacer(Modifier.width(10.dp))
                    val st = stats[k]
                    TitleBlock(t.speakerName(k), st?.let { tr("{0} % · {1} Beiträge · {2} Wörter", Math.round(it.share * 100), it.turns, it.words) } ?: tr("keine Abschnitte"), Modifier.weight(1f))
                    Icon(Icons.Outlined.Edit, contentDescription = tr("Umbenennen oder zusammenführen"))
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { add = true }) {
                Icon(Icons.Outlined.Add, contentDescription = null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (t.diarized) tr("Sprecher hinzufügen") else tr("Sprecher anlegen"))
            }
            if (t.diarized) TextButton(onClick = { remove = true }) { Text(tr("Sprecher entfernen")) }
        }
        HintText(tr("Tipp: Kurze Namen wie „I“, „B1“ oder „LK“ werden beim Export nach Dresing/Pehl und für MAXQDA direkt als Kürzel benutzt."))
        if (c != null && t.diarized) {
            val profiles by c.voices.items.collectAsStateWithLifecycle()
            Text(tr("Stimmprofile"), style = MaterialTheme.typography.titleSmall)
            HintText(
                tr("Benannte Sprecher lassen sich als Stimmprofil merken und in weiteren Aufnahmen (z.B. einer Interviewreihe) an der Stimme " +
                    "wiedererkennen. Die Stimmabdrücke entstehen und bleiben nur auf diesem Gerät.") + if (profiles.isNotEmpty()) tr(" Gespeichert: ") + profiles.joinToString { it.name } + "." else ""
            )
            if (!hasAudio) HintText(tr("Dafür braucht es die gespeicherte Aufnahme."))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(enabled = hasAudio && !voiceBusy && profiles.isNotEmpty(), onClick = {
                    voiceBusy = true
                    scope.launch {
                        runCatching { io.github.thepytales.llmhub.transcribe.Voices.match(c.transcription.speakerVoices(t.id), profiles) }
                            .onSuccess { m -> matches = m; if (m.isEmpty()) Notices.info(tr("Keine gespeicherte Stimme erkannt.")) }
                            .onFailure { Notices.error(tr("Stimmen erkennen nicht möglich: {0}", it.message)) }
                        voiceBusy = false
                    }
                }) { Text(tr("Stimmen erkennen")) }
                OutlinedButton(enabled = hasAudio && !voiceBusy, onClick = {
                    voiceBusy = true
                    scope.launch {
                        runCatching {
                            val voices = c.transcription.speakerVoices(t.id)
                            val named = t.speakers.filter { (_, n) -> n.isNotBlank() && !n.startsWith("Sprecher ") }
                            named.forEach { (k, n) -> voices[k]?.let { c.voices.learn(n, it) } }
                            named.size
                        }.onSuccess { n -> Notices.info(if (n == 0) tr("Bitte die Sprecher zuerst benennen.") else tr("{0} Stimmprofil(e) gespeichert.", n)) }
                            .onFailure { Notices.error(tr("Stimmprofile nicht gespeichert: {0}", it.message)) }
                        voiceBusy = false
                    }
                }) { Text(tr("Als Profile merken")) }
                if (voiceBusy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            }
            matches?.takeIf { it.isNotEmpty() }?.let { m ->
                m.forEach { x -> HintText(tr("{0} → {1} (Ähnlichkeit {2})", t.speakerName(x.speaker), x.profile.name, "%.2f".format(x.similarity))) }
                TextButton(onClick = {
                    var next = t
                    m.forEach { x -> next = next.copy(speakers = next.speakers + (x.speaker to x.profile.name)) }
                    onSave(next)
                    matches = null
                }) { Text(tr("Namen übernehmen")) }
            }
        }
    }
    if (add) TextInputDialog(tr("Sprecher hinzufügen"), "", tr("Name oder Kürzel"), onConfirm = { onSave(TranscriptEdit.addSpeaker(t, it.trim())) }, onDismiss = { add = false })
    if (remove) ConfirmDialog(tr("Sprecher entfernen?"), tr("Alle Zuordnungen werden gelöscht (für Aufnahmen mit nur einer Person)."), tr("Entfernen"),
        onConfirm = { onSave(TranscriptEdit.removeSpeakers(t)) }, onDismiss = { remove = false })
}

@Composable
fun SpeakerDialog(t: Transcript, speaker: Int, onSave: (Transcript) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(t.speakerName(speaker)) }
    var mergeInto by remember { mutableStateOf<Int?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Sprecher")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text(tr("Name oder Kürzel")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                val others = t.speakers.keys.filter { it != speaker }.sorted()
                if (others.isNotEmpty()) {
                    ChoiceField(
                        tr("Zusammenführen mit (falls doppelt erkannt)"),
                        listOf<Pair<Int?, String>>(null to tr("Nicht zusammenführen")) + others.map { it to t.speakerName(it) },
                        mergeInto, { mergeInto = it },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val target = mergeInto
                onSave(if (target != null) TranscriptEdit.mergeSpeaker(t, speaker, target)
                else t.copy(speakers = t.speakers + (speaker to name.trim().ifBlank { "Sprecher ${speaker + 1}" })))
                onDismiss()
            }) { Text(tr("Speichern")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Abbrechen")) } },
    )
}

/** ⋮ › Segmentierung: split into sentences or join to longer passages. */
@Composable
fun SegmentationSheet(t: Transcript, onSave: (Transcript) -> Unit, onDismiss: () -> Unit) {
    var max by remember { mutableStateOf(20) }
    val preview = remember(t, max) { TranscriptEdit.resegment(t, max) }
    val untimed = t.segments.count { s -> TranscriptEdit.textWords(s).let { tw -> tw.isEmpty() || tw.any { it.word?.measured != true } } }
    PanelSheet(tr("Segmentierung"), onDismiss) {
        HintText(tr("Teilt Abschnitte an Satzenden – genau an der gemessenen Zeit des ersten Worts – und verbindet dann Sätze desselben Sprechers bis zur gewählten Länge."))
        Chips(tr("Abschnitte"), listOf(0 to tr("Ein Satz je Abschnitt"), 10 to tr("Bis ca. 10 s"), 20 to tr("Bis ca. 20 s"), 45 to tr("Bis ca. 45 s")), max, { max = it })
        Text(tr("Ergibt {0} Abschnitte (bisher {1}).", preview.segments.size, t.segments.size), style = MaterialTheme.typography.bodyMedium)
        if (untimed > 0) HintText(tr("{0} Abschnitte ohne vollständig gemessene Wortzeiten werden nicht geteilt (nur verbunden). Erst ⋮ › Wortzeiten messen, dann neu segmentieren.", untimed))
        HintText(tr("Einzelne Abschnitte teilen oder verbinden: Abschnitt lange drücken. Rückgängig ist möglich."))
        Row {
            Spacer(Modifier.weight(1f))
            Button(onClick = { onSave(preview); onDismiss() }) { Text(tr("Übernehmen")) }
        }
    }
}

private fun pct(v: Double) = "${Math.round(v * 100)} %"
private fun minutes(sec: Double) = TranscriptExport.clock(sec)

/** ⋮ › Redeanteile & Statistik, also as a table for Excel/SPSS. */
@Composable
fun StatsSheet(t: Transcript, onDismiss: () -> Unit) {
    val stats = remember(t) { TranscriptEdit.stats(t) }
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val speech = t.segments.sumOf { it.end - it.start }
    PanelSheet(tr("Redeanteile & Statistik"), onDismiss) {
        Text(
            tr("Gesprochen {0} von {1} ({2}) · {3} Wörter", minutes(speech), minutes(t.duration), pct(speech / t.duration.coerceAtLeast(0.001)), t.wordCount) +
                if (t.diarized) tr(" · {0} Sprecherwechsel", TranscriptEdit.speakerChanges(t)) else "",
            style = MaterialTheme.typography.bodyMedium,
        )
        stats.forEach { s ->
            AppCard {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (s.speaker != null) { Box(Modifier.size(10.dp).clip(CircleShape).background(speakerColor(s.speaker))); Spacer(Modifier.width(8.dp)) }
                        Text(s.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        Text(pct(s.share), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                    }
                    HintText(
                        tr("Redezeit {0} · {1} Wörter · {2} Beiträge · Ø {3} s je Beitrag · ", minutes(s.seconds), s.words, s.turns, String.format(Locale.GERMANY, "%.1f", s.averageTurn)) +
                            tr("längster {0} · {1} Wörter/min", minutes(s.longestTurn), Math.round(s.wordsPerMinute)),
                    )
                }
            }
        }
        HintText(tr("Redezeit = Summe der Abschnitte (Sprachaktivität), ohne Pausen. Beiträge = zusammenhängende Abschnitte desselben Sprechers."))
        OutlinedButton(onClick = {
            val rows = listOf("Sprecher;Redezeit (s);Anteil (%);Wörter;Beiträge;Ø Beitrag (s);Längster Beitrag (s);Wörter pro Minute") + stats.map { s ->
                listOf(s.name, s.seconds, s.share * 100, s.words, s.turns, s.averageTurn, s.longestTurn, s.wordsPerMinute)
                    .joinToString(";") { v -> if (v is Double) String.format(Locale.GERMANY, "%.1f", v) else v.toString() }
            }
            clipboard.setText(AnnotatedString(rows.joinToString("\n")))
            Notices.info(tr("Tabelle kopiert (für Excel, SPSS, R)."))
        }) { Text(tr("Als Tabelle kopieren")) }
    }
}

/** ⋮ › Kapitel & Zusammenfassung: by the language model (within the privacy level of the project) or by hand. */
@Composable
fun ChaptersSheet(c: AppContainer, t: Transcript, control: PlaybackControl, onSave: (Transcript) -> Unit, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<Chapter?>(null) }
    var adding by remember { mutableStateOf(false) }
    var summaryEdit by remember { mutableStateOf(false) }
    val model = Privacy.suitableModel(c, t.folder, c.settings.current.defaultModel)
    val block = Privacy.blockReason(c, t.folder, model)
    PanelSheet(tr("Kapitel & Zusammenfassung"), onDismiss) {
        t.summary?.let { sum ->
            AppCard(onClick = { summaryEdit = true }) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(tr("Zusammenfassung"), style = MaterialTheme.typography.titleSmall)
                    Text(sum, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        if (t.chapters.isEmpty()) HintText(tr("Noch keine Kapitel."))
        t.chapters.forEach { ch ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { control.play(ch.time) }) {
                Text(TranscriptExport.clock(ch.time), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.outline, modifier = Modifier.width(56.dp))
                Text(ch.title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                IconButton(onClick = { editing = ch }) { Icon(Icons.Outlined.Edit, contentDescription = tr("Umbenennen")) }
                IconButton(onClick = { onSave(t.copy(chapters = t.chapters - ch)) }) { Icon(Icons.Outlined.Delete, contentDescription = tr("Löschen")) }
            }
        }
        if (control.available) OutlinedButton(onClick = { adding = true }) { Text(tr("Kapitel bei {0} einfügen", TranscriptExport.clock(control.position()))) }
        HorizontalDivider()
        if (block != null) {
            HintText(block)
        } else {
            HintText(tr("Das Sprachmodell {0} liest das Transkript und schlägt Kapitel und eine Zusammenfassung vor. ", ModelCatalog.info(model).displayName) +
                tr("Kapitel beginnen nur an gemessenen Zeiten des Transkripts. Bisherige Kapitel und Zusammenfassung werden ersetzt."))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(enabled = !busy, onClick = {
                    busy = true; error = null
                    scope.launch {
                        runCatching {
                            val result = c.llm.streamChat(model = model, messages = TranscriptChapters.prompt(t), tools = null, thinking = ThinkingLevel.OFF) { }
                            TranscriptChapters.parse(splitThinking(result.content).second, t)
                        }.onSuccess { (summary, chapters) ->
                            if (chapters.isEmpty() && summary == null) error = tr("Das Modell hat keine brauchbare Gliederung geliefert. Bitte noch einmal versuchen.")
                            else onSave(t.copy(summary = summary ?: t.summary, chapters = chapters.ifEmpty { t.chapters }))
                        }.onFailure { error = it.message ?: tr("Fehler") }
                        busy = false
                    }
                }) { Text(tr("Mit dem Sprachmodell erstellen")) }
                if (busy) { Spacer(Modifier.width(12.dp)); CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) }
            }
        }
        error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
    }
    editing?.let { ch ->
        TextInputDialog(tr("Kapitel"), ch.title, tr("Titel"), onConfirm = { v ->
            onSave(t.copy(chapters = t.chapters.map { if (it == ch) it.copy(title = v.trim().ifBlank { ch.title }) else it }))
        }, onDismiss = { editing = null })
    }
    if (adding) {
        val at = control.position()
        TextInputDialog(tr("Kapitel bei {0}", TranscriptExport.clock(at)), "", tr("Titel"), onConfirm = { v ->
            if (v.isNotBlank()) onSave(t.copy(chapters = (t.chapters + Chapter(at, v.trim())).sortedBy { it.time }))
        }, onDismiss = { adding = false })
    }
    if (summaryEdit) {
        TextInputDialog(tr("Zusammenfassung"), t.summary.orEmpty(), tr("Text"), onConfirm = { v -> onSave(t.copy(summary = v.trim().ifBlank { null })) }, onDismiss = { summaryEdit = false })
    }
}

/** ⋮ › Status: Rohfassung → Korrigiert → Geprüft (shown in the list). */
@Composable
fun StatusDialog(status: TranscriptStatus, onPick: (TranscriptStatus) -> Unit, onDismiss: () -> Unit) {
    val help = mapOf(
        TranscriptStatus.RAW to tr("So, wie es die Erkennung geliefert hat."),
        TranscriptStatus.CORRECTED to tr("Einmal vollständig gegengehört und korrigiert."),
        TranscriptStatus.CHECKED to tr("Von einer zweiten Person geprüft; bereit für die Auswertung."),
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Bearbeitungsstatus")) },
        text = {
            Column {
                TranscriptStatus.entries.forEach { s ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { onPick(s); onDismiss() }) {
                        RadioButton(selected = s == status, onClick = { onPick(s); onDismiss() })
                        TitleBlock(s.label, help[s], Modifier.weight(1f))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(tr("Schließen")) } },
    )
}

/** ⋮ › Wortzeiten: where the word times come from, how many are measured, measure (again). */
@Composable
fun WordTimesDialog(c: AppContainer, t: Transcript, hasAudio: Boolean, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val total = t.segments.sumOf { s -> TranscriptEdit.textWords(s).size }
    val words = t.segments.flatMap { it.words }
    val exact = words.count { it.quality == 'm' }
    val respelled = words.count { it.quality == 's' }
    val between = words.count { it.quality == 'i' }
    val own = t.modelName.startsWith("Parakeet")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Wortzeiten")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (words.isEmpty()) Text(tr("Noch keine Wortzeiten – es gibt nur die Zeiten der Abschnitte (aus der Spracherkennung der Pausen)."))
                else {
                    Text(tr("Gemessen: {0} von {1} Wörtern ({2})", exact + respelled, total, pct((exact + respelled).toDouble() / total.coerceAtLeast(1))))
                    if (respelled > 0) HintText(tr("Davon {0} an derselben Stelle gemessen, aber anders geschrieben erkannt (z. B. Zahlen).", respelled))
                    if (between > 0) HintText(tr("{0} Wörter ohne eigene Messung, zwischen ihre gemessenen Nachbarn gelegt.", between))
                    val missing = total - words.size
                    if (missing > 0) HintText(tr("{0} Wörter in Abschnitten ohne verlässlichen Abgleich – dort gibt es nur die Abschnittszeit.", missing))
                    t.wordTiming?.source?.takeIf { it.isNotBlank() }?.let { HintText(tr("Gemessen mit {0}.", it)) }
                }
                HintText(
                    tr("Whisper und der Uni-Server liefern keine verlässlichen Zeiten pro Wort. Deshalb erkennt Parakeet v3 jeden Abschnitt noch einmal auf diesem " +
                        "Gerät; seine Wortzeiten werden mit dem Text abgeglichen. Geprüft an LibriSpeech (958 Wörter mit Wortgrenzen aus Forced Alignment): Wortanfang " +
                        "im Median 50 ms genau, 0,3 % über 250 ms. Whispers eigene Zeiten lagen bei 8,8 % über 250 ms, Schätzungen bei 37 %."),
                )
            }
        },
        confirmButton = {
            if (hasAudio && !own) TextButton(onClick = {
                runCatching { c.transcription.measureWords(t.id) }
                    .onSuccess { Notices.info(tr("Wortzeiten werden gemessen – das Transkript aktualisiert sich danach.")); onDismiss() }
                    .onFailure { Notices.error(it.message ?: tr("Fehler")) }
            }) { Text(if (words.isEmpty()) tr("Jetzt messen") else tr("Neu messen")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Schließen")) } },
    )
}

@Composable
fun RetranscribeSheet(c: AppContainer, t: Transcript, onStarted: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var opts by remember { mutableStateOf(c.transcription.defaultOptions(t.folder).copy(title = t.title)) }
    PanelSheet(tr("Neu transkribieren"), onDismiss) {
        HintText(tr("Die Aufnahme wird mit den neuen Einstellungen noch einmal transkribiert; das bisherige Transkript bleibt erhalten."))
        StartOptionsForm(c, opts, { opts = it })
        Row {
            Spacer(Modifier.weight(1f))
            Button(onClick = {
                runCatching { c.transcription.retranscribe(t.id, opts) }
                    .onSuccess { onStarted() }
                    .onFailure { Notices.error(it.message ?: tr("Fehler")) }
            }) { Text(tr("Starten")) }
        }
    }
}

@Composable
fun FindReplaceDialog(t: Transcript, onApply: (Transcript, Int) -> Unit, onDismiss: () -> Unit) {
    var find by remember { mutableStateOf("") }
    var with by remember { mutableStateOf("") }
    val count = remember(find) { TranscriptEdit.search(t, find).size }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Suchen & Ersetzen")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(find, { find = it }, label = { Text(tr("Suchen")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(with, { with = it }, label = { Text(tr("Ersetzen durch")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                HintText(if (find.isBlank()) tr("Groß-/Kleinschreibung egal. Für Namen, die immer falsch erkannt werden: Einstellungen › Transkription › Ersetzungsliste.") else tr("{0} Treffer · Wortzeiten bleiben erhalten", count))
            }
        },
        confirmButton = {
            TextButton(enabled = count > 0, onClick = {
                val re = Regex(Regex.escape(find.trim()), RegexOption.IGNORE_CASE)
                onApply(t.copy(segments = t.segments.map { s -> if (re.containsMatchIn(s.text)) TranscriptEdit.withText(s, re.replace(s.text, Regex.escapeReplacement(with))) else s }), count)
                onDismiss()
            }) { Text(tr("Alle ersetzen")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Abbrechen")) } },
    )
}

/** Export formats; the file appears as a card with open, share and save. */
@Composable
fun ExportSheet(c: AppContainer, t: Transcript, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val vs by c.transcription.settings.collectAsStateWithLifecycle()
    var made by remember { mutableStateOf<File?>(null) }
    var busy by remember { mutableStateOf<TranscriptFormat?>(null) }
    var stamps by remember { mutableStateOf(TranscriptEdit.Stamps.of(vs.stamps)) }
    var anonymous by remember { mutableStateOf(false) }
    var mappingFile by remember { mutableStateOf<File?>(null) }
    val anon = remember(t, anonymous) {
        if (!anonymous) null else io.github.thepytales.llmhub.transcribe.TranscriptAnon.anonymize(
            t, c.settings.current.pseudonymTerms.lines().map { it.trim() }.filter { it.length >= 2 },
        )
    }
    PanelSheet(tr("Exportieren"), onDismiss) {
        made?.let { f -> DocumentCard(f.absolutePath) }
        mappingFile?.let { f -> DocumentCard(f.absolutePath) }
        ToggleRow(
            tr("Anonymisiert exportieren"),
            tr("Namen, Kontaktdaten, Anschriften und die eigene Liste (Einstellungen › Datenschutz) werden durchgehend ersetzt, Sprecher mit " +
                "echtem Namen heißen „Person A“ … Das Transkript selbst bleibt unverändert.") +
                (anon?.let { a -> tr(" – {0} Ersetzungen.", a.mapping.size) } ?: ""),
            anonymous,
        ) { anonymous = it }
        Chips(tr("Zeitstempel (Word, PDF, Markdown, Text)"), TranscriptEdit.Stamps.entries.map { it to it.label }, stamps, { stamps = it })
        ToggleRow(tr("Kommentare mitexportieren"), tr("Als [Kommentar: …] hinter dem Abschnitt. Dresing/Pehl, MAXQDA und Untertitel bleiben ohne."), vs.exportComments) { v ->
            c.transcription.updateSettings { it.copy(exportComments = v) }
        }
        val opts = ExportOptions(stamps, vs.exportComments)
        TranscriptFormat.entries.forEach { f ->
            AppCard(onClick = {
                busy = f
                scope.launch {
                    val source = anon?.transcript ?: t
                    made = runCatching { exportFile(context, source, f, opts) }
                        .onFailure { Notices.error(tr("Export fehlgeschlagen: {0}", it.message)) }.getOrNull()
                    // The mapping goes into a separate file (to be kept apart from the anonymized transcript).
                    if (anon != null && anon.mapping.isNotEmpty()) mappingFile = runCatching {
                        DocumentWriter.create(
                            context, File(io.github.thepytales.llmhub.data.LoraRoot.dir(context), "Erstellt/Transkripte"), "Zuordnungsliste – ${t.title}",
                            io.github.thepytales.llmhub.transcribe.TranscriptAnon.mappingTable(t.title, anon.mapping), DocFormat.PDF,
                            fileName = DocumentWriter.safeName(t.title) + " (Zuordnungsliste)", overwrite = true,
                        ).file
                    }.getOrNull()
                    busy = null
                }
            }) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    TitleBlock(f.label, f.description, Modifier.weight(1f))
                    if (busy == f) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else StatusPill(f.ext.uppercase(), PillTone.NEUTRAL)
                }
            }
        }
    }
}

/** Writes the export into the documents folder of the app and returns the file. */
private suspend fun exportFile(context: android.content.Context, t: Transcript, f: TranscriptFormat, opts: ExportOptions): File {
    val folder = File(io.github.thepytales.llmhub.data.LoraRoot.dir(context), "Erstellt/Transkripte")
    val name = DocumentWriter.safeName(t.title) + when (f) {
        TranscriptFormat.DRESING_PEHL -> " (Dresing-Pehl)"
        TranscriptFormat.MAXQDA -> " (MAXQDA)"
        else -> ""
    }
    val content = TranscriptExport.content(t, f, opts)
    return when (f) {
        TranscriptFormat.DOCX, TranscriptFormat.DRESING_PEHL ->
            DocumentWriter.create(context, folder, t.title, content, DocFormat.DOCX, fileName = name, overwrite = true).file
        TranscriptFormat.PDF ->
            DocumentWriter.create(context, folder, t.title, content, DocFormat.PDF, fileName = name, overwrite = true).file
        else -> withContext(Dispatchers.IO) {
            folder.mkdirs()
            File(folder, "$name.${f.ext}").apply {
                // MAXQDA reads Windows text files best with a byte order mark.
                if (f == TranscriptFormat.MAXQDA) writeBytes(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + content.toByteArray())
                else writeText(content)
            }
        }
    }
}
