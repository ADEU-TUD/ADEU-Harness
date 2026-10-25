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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
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
    PanelSheet("Ansicht & Zeitstempel", onDismiss) {
        Chips("Zeitstempel", TranscriptEdit.Stamps.entries.map { it.key to it.label }, s.stamps, { v -> t.updateSettings { it.copy(stamps = v) } })
        HintText(
            "„Je Satz“ und „Alle … s“ setzen die Zeit vor das Wort, bei dem sie gemessen wurde. Ohne gemessene Wortzeiten gibt es nur die Zeiten " +
                "der Abschnittsanfänge – geschätzt wird nichts. Der Export übernimmt diese Einstellung.",
        )
        Chips(
            "Absätze",
            listOf("auto" to "Bei Sprecherwechsel und Pausen", "speaker" to "Nur bei Sprecherwechsel", "segment" to "Jeder Abschnitt"),
            s.paragraphs, { v -> t.updateSettings { it.copy(paragraphs = v) } },
        )
        Chips("Schriftgröße", listOf("small" to "Klein", "normal" to "Normal", "large" to "Groß", "xlarge" to "Sehr groß"), s.textSize, { v -> t.updateSettings { it.copy(textSize = v) } })
        ToggleRow("Aktuelles Wort markieren", "Beim Abspielen wird das gerade gesprochene Wort hervorgehoben (nur gemessene Wortzeiten).", s.wordHighlight) { v -> t.updateSettings { it.copy(wordHighlight = v) } }
        ToggleRow("Text mitlaufen lassen", "Beim Abspielen scrollt der Text mit.", s.follow) { v -> t.updateSettings { it.copy(follow = v) } }
        ToggleRow("Kommentare anzeigen", "Kommentare stehen klein unter ihrem Abschnitt.", s.showComments) { v -> t.updateSettings { it.copy(showComments = v) } }
        ToggleRow("Nicht gemessene Wörter unterstreichen", "Zeigt, wo eine Wortzeit fehlt oder nur zwischen gemessenen Nachbarn liegt.", s.markUnmeasured) { v -> t.updateSettings { it.copy(markUnmeasured = v) } }
        HorizontalDivider()
        Text("Abspielen zum Korrigieren", style = MaterialTheme.typography.titleSmall)
        Chips("Beim Anhalten zurückspringen", listOf(0.0 to "Aus", 1.0 to "1 s", 2.0 to "2 s", 3.0 to "3 s", 5.0 to "5 s"), s.rewindOnPause, { v -> t.updateSettings { it.copy(rewindOnPause = v) } })
        Chips("Sprungweite (Knöpfe, F3/F5)", listOf(5 to "5 s", 10 to "10 s", 30 to "30 s"), s.skipSeconds, { v -> t.updateSettings { it.copy(skipSeconds = v) } })
        ToggleRow("Pausen überspringen", "Stille zwischen den Abschnitten (über 1,5 s) wird beim Abspielen übersprungen.", s.skipSilence) { v -> t.updateSettings { it.copy(skipSilence = v) } }
        HintText("Tastatur (Windows, Tablet mit Tastatur), auch beim Tippen im Abschnitt: F4 Abspielen/Anhalten · F3 zurück · F5 vor · F6 Abschnitt wiederholen · Strg+F Suchen.")
    }
}

/** ⋮ › Sprecher: rename, merge, add by hand, remove. */
@Composable
fun SpeakersSheet(t: Transcript, onEdit: (Int) -> Unit, onSave: (Transcript) -> Unit, onDismiss: () -> Unit) {
    var add by remember { mutableStateOf(false) }
    var remove by remember { mutableStateOf(false) }
    PanelSheet("Sprecher", onDismiss) {
        if (!t.diarized) {
            HintText("In diesem Transkript sind keine Sprecher erkannt. Legen Sie sie von Hand an: Alle Abschnitte gehören zuerst zu „Sprecher 1“, " +
                "einzelne Abschnitte ordnen Sie per langem Drücken zu.")
        }
        val stats = TranscriptEdit.stats(t).associateBy { it.speaker }
        t.speakers.keys.sorted().forEach { k ->
            AppCard(onClick = { onEdit(k) }) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(speakerColor(k)))
                    Spacer(Modifier.width(10.dp))
                    val st = stats[k]
                    TitleBlock(t.speakerName(k), st?.let { "${Math.round(it.share * 100)} % · ${it.turns} Beiträge · ${it.words} Wörter" } ?: "keine Abschnitte", Modifier.weight(1f))
                    Icon(Icons.Outlined.Edit, contentDescription = "Umbenennen oder zusammenführen")
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { add = true }) {
                Icon(Icons.Outlined.Add, contentDescription = null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (t.diarized) "Sprecher hinzufügen" else "Sprecher anlegen")
            }
            if (t.diarized) TextButton(onClick = { remove = true }) { Text("Sprecher entfernen") }
        }
        HintText("Tipp: Kurze Namen wie „I“, „B1“ oder „LK“ werden beim Export nach Dresing/Pehl und für MAXQDA direkt als Kürzel benutzt.")
    }
    if (add) TextInputDialog("Sprecher hinzufügen", "", "Name oder Kürzel", onConfirm = { onSave(TranscriptEdit.addSpeaker(t, it.trim())) }, onDismiss = { add = false })
    if (remove) ConfirmDialog("Sprecher entfernen?", "Alle Zuordnungen werden gelöscht (für Aufnahmen mit nur einer Person).", "Entfernen",
        onConfirm = { onSave(TranscriptEdit.removeSpeakers(t)) }, onDismiss = { remove = false })
}

@Composable
fun SpeakerDialog(t: Transcript, speaker: Int, onSave: (Transcript) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(t.speakerName(speaker)) }
    var mergeInto by remember { mutableStateOf<Int?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sprecher") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name oder Kürzel") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                val others = t.speakers.keys.filter { it != speaker }.sorted()
                if (others.isNotEmpty()) {
                    ChoiceField(
                        "Zusammenführen mit (falls doppelt erkannt)",
                        listOf<Pair<Int?, String>>(null to "Nicht zusammenführen") + others.map { it to t.speakerName(it) },
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
            }) { Text("Speichern") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

/** ⋮ › Segmentierung: split into sentences or join to longer passages. */
@Composable
fun SegmentationSheet(t: Transcript, onSave: (Transcript) -> Unit, onDismiss: () -> Unit) {
    var max by remember { mutableStateOf(20) }
    val preview = remember(t, max) { TranscriptEdit.resegment(t, max) }
    val untimed = t.segments.count { s -> TranscriptEdit.textWords(s).let { tw -> tw.isEmpty() || tw.any { it.word?.measured != true } } }
    PanelSheet("Segmentierung", onDismiss) {
        HintText("Teilt Abschnitte an Satzenden – genau an der gemessenen Zeit des ersten Worts – und verbindet dann Sätze desselben Sprechers bis zur gewählten Länge.")
        Chips("Abschnitte", listOf(0 to "Ein Satz je Abschnitt", 10 to "Bis ca. 10 s", 20 to "Bis ca. 20 s", 45 to "Bis ca. 45 s"), max, { max = it })
        Text("Ergibt ${preview.segments.size} Abschnitte (bisher ${t.segments.size}).", style = MaterialTheme.typography.bodyMedium)
        if (untimed > 0) HintText("$untimed Abschnitte ohne vollständig gemessene Wortzeiten werden nicht geteilt (nur verbunden). Erst ⋮ › Wortzeiten messen, dann neu segmentieren.")
        HintText("Einzelne Abschnitte teilen oder verbinden: Abschnitt lange drücken. Rückgängig ist möglich.")
        Row {
            Spacer(Modifier.weight(1f))
            Button(onClick = { onSave(preview); onDismiss() }) { Text("Übernehmen") }
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
    PanelSheet("Redeanteile & Statistik", onDismiss) {
        Text(
            "Gesprochen ${minutes(speech)} von ${minutes(t.duration)} (${pct(speech / t.duration.coerceAtLeast(0.001))}) · ${t.wordCount} Wörter" +
                if (t.diarized) " · ${TranscriptEdit.speakerChanges(t)} Sprecherwechsel" else "",
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
                        "Redezeit ${minutes(s.seconds)} · ${s.words} Wörter · ${s.turns} Beiträge · Ø ${String.format(Locale.GERMANY, "%.1f", s.averageTurn)} s je Beitrag · " +
                            "längster ${minutes(s.longestTurn)} · ${Math.round(s.wordsPerMinute)} Wörter/min",
                    )
                }
            }
        }
        HintText("Redezeit = Summe der Abschnitte (Sprachaktivität), ohne Pausen. Beiträge = zusammenhängende Abschnitte desselben Sprechers.")
        OutlinedButton(onClick = {
            val rows = listOf("Sprecher;Redezeit (s);Anteil (%);Wörter;Beiträge;Ø Beitrag (s);Längster Beitrag (s);Wörter pro Minute") + stats.map { s ->
                listOf(s.name, s.seconds, s.share * 100, s.words, s.turns, s.averageTurn, s.longestTurn, s.wordsPerMinute)
                    .joinToString(";") { v -> if (v is Double) String.format(Locale.GERMANY, "%.1f", v) else v.toString() }
            }
            clipboard.setText(AnnotatedString(rows.joinToString("\n")))
            Notices.info("Tabelle kopiert (für Excel, SPSS, R).")
        }) { Text("Als Tabelle kopieren") }
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
    PanelSheet("Kapitel & Zusammenfassung", onDismiss) {
        t.summary?.let { sum ->
            AppCard(onClick = { summaryEdit = true }) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Zusammenfassung", style = MaterialTheme.typography.titleSmall)
                    Text(sum, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        if (t.chapters.isEmpty()) HintText("Noch keine Kapitel.")
        t.chapters.forEach { ch ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { control.play(ch.time) }) {
                Text(TranscriptExport.clock(ch.time), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.outline, modifier = Modifier.width(56.dp))
                Text(ch.title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                IconButton(onClick = { editing = ch }) { Icon(Icons.Outlined.Edit, contentDescription = "Umbenennen") }
                IconButton(onClick = { onSave(t.copy(chapters = t.chapters - ch)) }) { Icon(Icons.Outlined.Delete, contentDescription = "Löschen") }
            }
        }
        if (control.available) OutlinedButton(onClick = { adding = true }) { Text("Kapitel bei ${TranscriptExport.clock(control.position())} einfügen") }
        HorizontalDivider()
        if (block != null) {
            HintText(block)
        } else {
            HintText("Das Sprachmodell ${ModelCatalog.info(model).displayName} liest das Transkript und schlägt Kapitel und eine Zusammenfassung vor. " +
                "Kapitel beginnen nur an gemessenen Zeiten des Transkripts. Bisherige Kapitel und Zusammenfassung werden ersetzt.")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(enabled = !busy, onClick = {
                    busy = true; error = null
                    scope.launch {
                        runCatching {
                            val result = c.llm.streamChat(model = model, messages = TranscriptChapters.prompt(t), tools = null, thinking = ThinkingLevel.OFF) { }
                            TranscriptChapters.parse(splitThinking(result.content).second, t)
                        }.onSuccess { (summary, chapters) ->
                            if (chapters.isEmpty() && summary == null) error = "Das Modell hat keine brauchbare Gliederung geliefert. Bitte noch einmal versuchen."
                            else onSave(t.copy(summary = summary ?: t.summary, chapters = chapters.ifEmpty { t.chapters }))
                        }.onFailure { error = it.message ?: "Fehler" }
                        busy = false
                    }
                }) { Text("Mit dem Sprachmodell erstellen") }
                if (busy) { Spacer(Modifier.width(12.dp)); CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) }
            }
        }
        error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
    }
    editing?.let { ch ->
        TextInputDialog("Kapitel", ch.title, "Titel", onConfirm = { v ->
            onSave(t.copy(chapters = t.chapters.map { if (it == ch) it.copy(title = v.trim().ifBlank { ch.title }) else it }))
        }, onDismiss = { editing = null })
    }
    if (adding) {
        val at = control.position()
        TextInputDialog("Kapitel bei ${TranscriptExport.clock(at)}", "", "Titel", onConfirm = { v ->
            if (v.isNotBlank()) onSave(t.copy(chapters = (t.chapters + Chapter(at, v.trim())).sortedBy { it.time }))
        }, onDismiss = { adding = false })
    }
    if (summaryEdit) {
        TextInputDialog("Zusammenfassung", t.summary.orEmpty(), "Text", onConfirm = { v -> onSave(t.copy(summary = v.trim().ifBlank { null })) }, onDismiss = { summaryEdit = false })
    }
}

/** ⋮ › Status: Rohfassung → Korrigiert → Geprüft (shown in the list). */
@Composable
fun StatusDialog(status: TranscriptStatus, onPick: (TranscriptStatus) -> Unit, onDismiss: () -> Unit) {
    val help = mapOf(
        TranscriptStatus.RAW to "So, wie es die Erkennung geliefert hat.",
        TranscriptStatus.CORRECTED to "Einmal vollständig gegengehört und korrigiert.",
        TranscriptStatus.CHECKED to "Von einer zweiten Person geprüft; bereit für die Auswertung.",
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Bearbeitungsstatus") },
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
        confirmButton = { TextButton(onClick = onDismiss) { Text("Schließen") } },
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
        title = { Text("Wortzeiten") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (words.isEmpty()) Text("Noch keine Wortzeiten – es gibt nur die Zeiten der Abschnitte (aus der Spracherkennung der Pausen).")
                else {
                    Text("Gemessen: ${exact + respelled} von $total Wörtern (${pct((exact + respelled).toDouble() / total.coerceAtLeast(1))})")
                    if (respelled > 0) HintText("Davon $respelled an derselben Stelle gemessen, aber anders geschrieben erkannt (z. B. Zahlen).")
                    if (between > 0) HintText("$between Wörter ohne eigene Messung, zwischen ihre gemessenen Nachbarn gelegt.")
                    val missing = total - words.size
                    if (missing > 0) HintText("$missing Wörter in Abschnitten ohne verlässlichen Abgleich – dort gibt es nur die Abschnittszeit.")
                    t.wordTiming?.source?.takeIf { it.isNotBlank() }?.let { HintText("Gemessen mit $it.") }
                }
                HintText(
                    "Whisper und der Uni-Server liefern keine verlässlichen Zeiten pro Wort. Deshalb erkennt Parakeet v3 jeden Abschnitt noch einmal auf diesem " +
                        "Gerät; seine Wortzeiten werden mit dem Text abgeglichen. Geprüft an LibriSpeech (958 Wörter mit Wortgrenzen aus Forced Alignment): Wortanfang " +
                        "im Median 50 ms genau, 0,3 % über 250 ms. Whispers eigene Zeiten lagen bei 8,8 % über 250 ms, Schätzungen bei 37 %.",
                )
            }
        },
        confirmButton = {
            if (hasAudio && !own) TextButton(onClick = {
                runCatching { c.transcription.measureWords(t.id) }
                    .onSuccess { Notices.info("Wortzeiten werden gemessen – das Transkript aktualisiert sich danach."); onDismiss() }
                    .onFailure { Notices.error(it.message ?: "Fehler") }
            }) { Text(if (words.isEmpty()) "Jetzt messen" else "Neu messen") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Schließen") } },
    )
}

@Composable
fun RetranscribeSheet(c: AppContainer, t: Transcript, onStarted: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var opts by remember { mutableStateOf(c.transcription.defaultOptions(t.folder).copy(title = t.title)) }
    PanelSheet("Neu transkribieren", onDismiss) {
        HintText("Die Aufnahme wird mit den neuen Einstellungen noch einmal transkribiert; das bisherige Transkript bleibt erhalten.")
        StartOptionsForm(c, opts, { opts = it })
        Row {
            Spacer(Modifier.weight(1f))
            Button(onClick = {
                runCatching { c.transcription.retranscribe(t.id, opts) }
                    .onSuccess { onStarted() }
                    .onFailure { Notices.error(it.message ?: "Fehler") }
            }) { Text("Starten") }
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
        title = { Text("Suchen & Ersetzen") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(find, { find = it }, label = { Text("Suchen") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(with, { with = it }, label = { Text("Ersetzen durch") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                HintText(if (find.isBlank()) "Groß-/Kleinschreibung egal. Für Namen, die immer falsch erkannt werden: Einstellungen › Transkription › Ersetzungsliste." else "$count Treffer · Wortzeiten bleiben erhalten")
            }
        },
        confirmButton = {
            TextButton(enabled = count > 0, onClick = {
                val re = Regex(Regex.escape(find.trim()), RegexOption.IGNORE_CASE)
                onApply(t.copy(segments = t.segments.map { s -> if (re.containsMatchIn(s.text)) TranscriptEdit.withText(s, re.replace(s.text, Regex.escapeReplacement(with))) else s }), count)
                onDismiss()
            }) { Text("Alle ersetzen") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
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
    PanelSheet("Exportieren", onDismiss) {
        made?.let { f -> DocumentCard(f.absolutePath) }
        Chips("Zeitstempel (Word, PDF, Markdown, Text)", TranscriptEdit.Stamps.entries.map { it to it.label }, stamps, { stamps = it })
        ToggleRow("Kommentare mitexportieren", "Als [Kommentar: …] hinter dem Abschnitt. Dresing/Pehl, MAXQDA und Untertitel bleiben ohne.", vs.exportComments) { v ->
            c.transcription.updateSettings { it.copy(exportComments = v) }
        }
        val opts = ExportOptions(stamps, vs.exportComments)
        TranscriptFormat.entries.forEach { f ->
            AppCard(onClick = {
                busy = f
                scope.launch {
                    made = runCatching { exportFile(context, t, f, opts) }
                        .onFailure { Notices.error("Export fehlgeschlagen: ${it.message}") }.getOrNull()
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
    val folder = File(context.filesDir, "documents/Transkripte")
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
