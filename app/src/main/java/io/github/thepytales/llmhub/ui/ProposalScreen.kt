package io.github.thepytales.llmhub.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.RateReview
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.agent.ChatModes
import io.github.thepytales.llmhub.agent.ProposalTools
import io.github.thepytales.llmhub.agent.change
import io.github.thepytales.llmhub.data.BudgetRow
import io.github.thepytales.llmhub.data.Funders
import io.github.thepytales.llmhub.data.Proposal
import io.github.thepytales.llmhub.data.ProposalCheck
import io.github.thepytales.llmhub.data.ProposalChecks
import io.github.thepytales.llmhub.data.ProposalDeadline
import io.github.thepytales.llmhub.data.ProposalSection
import io.github.thepytales.llmhub.data.WorkPackage
import io.github.thepytales.llmhub.docs.CalendarFiles
import io.github.thepytales.llmhub.docs.MarkdownDocx
import io.github.thepytales.llmhub.files.FileText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Starts a chat in the mode „Antrag“ (optionally about proposal [p]) with [text] in the input. */
internal fun proposalChat(text: String, p: Proposal? = null, send: Boolean = false) {
    val chat = p?.chatId?.takeIf { id -> io.github.thepytales.llmhub.App.container.chats.conversations.value.any { it.id == id } }
    ChatHandoff.pending.value = ChatHandoff.Content(text, emptyList(), mode = ChatModes.PROPOSAL.id.takeIf { chat == null }, chatId = chat, send = send)
    ChatHandoff.open.value = chat.orEmpty()
}

private val TABS = listOf(trLater("Übersicht"), trLater("Gliederung"), trLater("Arbeitspakete"), trLater("Budget"), trLater("Abgabe-Check"))

/**
 * Antrags-Werkstatt (AP-21): the proposals; each with checklist and deadlines (into the calendar), the outline with the
 * Zeichen-/Seitenwächter, Arbeitspakete as Gantt, the budget with the funder's flat rate and the Abgabe-Check. Lora reads
 * the call and writes in the mode „Antrag“, a trial review takes the role of a reviewer.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProposalScreen(c: AppContainer, onBack: () -> Unit) {
    val all by c.proposals.items.collectAsStateWithLifecycle()
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    var creating by remember { mutableStateOf(false) }
    val open = openId?.let { id -> all.firstOrNull { it.id == id } }
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { if (open != null) IconButton(onClick = { openId = null }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, tr("Zurück")) } else BackButton(onBack) },
                title = { Text(open?.title ?: tr("Antrags-Werkstatt"), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                actions = { if (open == null) TextButton(onClick = { creating = true }) { Icon(Icons.Outlined.Add, null); Spacer(Modifier.size(4.dp)); Text(tr("Neuer Antrag")) } },
            )
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.TopCenter) {
            when {
                open != null -> ProposalDetail(c, open)
                all.isEmpty() -> Column(Modifier.widthIn(max = 560.dp).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    LoraFace(Modifier.size(72.dp), label = "Lora", director = rememberLoraDirector(listOf(LoraAction.NOTES, LoraAction.CALENDAR, LoraAction.CHART), LoraAction.NOTES))
                    Text(tr("Noch keine Anträge"), style = MaterialTheme.typography.titleMedium)
                    Text(
                        tr("Gib Lora die Ausschreibung (PDF oder Link): Sie legt Checkliste, Fristen und die Gliederung mit Seiten- und Zeichengrenzen an. Danach planst du Arbeitspakete und Budget, schreibst die Abschnitte – der Wächter zählt mit – und machst einen Probelauf mit einer Gutachter-Rolle."),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { proposalChat(tr("Hier ist die Ausschreibung: ")) }) { Text(tr("Ausschreibung einlesen")) }
                        OutlinedButton(onClick = { creating = true }) { Text(tr("Selbst anlegen")) }
                    }
                }
                else -> LazyColumn(Modifier.widthIn(max = 860.dp).fillMaxWidth(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    item {
                        OutlinedButton(onClick = { proposalChat(tr("Hier ist die Ausschreibung: ")) }) { Text(tr("Neue Ausschreibung mit Lora einlesen")) }
                    }
                    items(all.sortedByDescending { it.updatedAt }, key = { it.id }) { p -> ProposalRow(p) { openId = p.id } }
                }
            }
        }
    }
    if (creating) NewProposalDialog(onDismiss = { creating = false }) { p ->
        c.proposals.upsert(p)
        creating = false
        openId = p.id
    }
}

@Composable
private fun ProposalRow(p: Proposal, onOpen: () -> Unit) {
    val next = p.deadlines.mapNotNull { d -> ProposalChecks.daysUntil(d.date)?.takeIf { it >= 0 }?.let { d to it } }.minByOrNull { it.second }
    val errors = remember(p) { ProposalChecks.check(p).count { it.level == ProposalChecks.Level.ERROR } }
    Surface(shape = MaterialTheme.shapes.medium, color = LocalChatLook.current.card, border = boxBorder(), modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            IconTile(Icons.AutoMirrored.Outlined.Assignment, size = 34.dp)
            Spacer(Modifier.size(10.dp))
            Column(Modifier.weight(1f)) {
                Text(p.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(
                        p.funderInfo.name + if (p.program.isNotBlank()) " · ${p.program}" else "",
                        next?.let { (d, n) -> "${d.title} " + tr("in") + " $n " + tr("Tagen") },
                        "${p.checklist.count { it.done }}/${p.checklist.size} " + tr("erledigt"),
                        if (errors > 0) "$errors " + tr("offene Punkte") else tr("Abgabe-Check ok"),
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NewProposalDialog(onDismiss: () -> Unit, onCreate: (Proposal) -> Unit) {
    var title by remember { mutableStateOf("") }
    var funder by remember { mutableStateOf(Funders.DFG) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Neuer Antrag")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it }, singleLine = true, label = { Text(tr("Titel oder Akronym")) }, modifier = Modifier.fillMaxWidth())
                Text(tr("Förderer"), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Funders.all.forEach { f -> FilterChip(selected = funder == f, onClick = { funder = f }, label = { Text(f.name) }) }
                }
                Text(
                    tr("Gliederung, Checkliste und Pauschale kommen aus der Vorlage für") + " ${funder.name}" + (if (funder.program.isNotBlank()) " (${funder.program})" else "") + ". " +
                        tr("Mit der Ausschreibung passt Lora sie an.") + if (funder.note.isNotBlank()) " " + funder.note else "",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { Button(enabled = title.isNotBlank(), onClick = { onCreate(Funders.newProposal(title.trim(), funder)) }) { Text(tr("Anlegen")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Abbrechen")) } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProposalDetail(c: AppContainer, p: Proposal) {
    var tab by rememberSaveable(p.id) { mutableStateOf(0) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val saveDocx = rememberSaveAs("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
    val toNextcloud = rememberNextcloudSave(c)
    var confirmDelete by remember { mutableStateOf(false) }
    val change: ((Proposal) -> Proposal) -> Unit = { t -> c.proposals.change(p.id, t) }
    fun docx(): File = File(File(c.appContext.cacheDir, "antraege"), FileText.safeName("${p.title}.docx")).apply { parentFile?.mkdirs() }
        .also { MarkdownDocx.write(it, p.title, ProposalChecks.markdown(p)) }
    Column(Modifier.widthIn(max = 920.dp).fillMaxSize()) {
        FlowRow(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(onClick = { proposalChat(tr("Weiter am Antrag") + " „${p.title}“ (${ProposalTools.ID_LABEL} ${p.id}): ", p) }) { Text(tr("Mit Lora weiterarbeiten")) }
            OutlinedButton(onClick = { proposalChat(ProposalTools.reviewPrompt(p), p, send = true) }) {
                Icon(Icons.Outlined.RateReview, null, Modifier.size(18.dp)); Spacer(Modifier.size(4.dp)); Text(tr("Probelauf: Gutachter:in"))
            }
            OutlinedButton(onClick = {
                scope.launch { runCatching { withContext(Dispatchers.IO) { docx() } }.onSuccess(saveDocx).onFailure { Notices.error(it.message ?: tr("Export fehlgeschlagen")) } }
            }) { Icon(Icons.Outlined.FileDownload, null, Modifier.size(18.dp)); Spacer(Modifier.size(4.dp)); Text(tr("Als Word")) }
            if (toNextcloud != null) TextButton(onClick = { scope.launch { runCatching { withContext(Dispatchers.IO) { docx() } }.onSuccess(toNextcloud) } }) { Text(tr("In Nextcloud")) }
            TextButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.Delete, null, Modifier.size(18.dp)); Spacer(Modifier.size(4.dp)); Text(tr("Löschen")) }
        }
        ScrollableTabRow(selectedTabIndex = tab, edgePadding = 16.dp) {
            TABS.forEachIndexed { i, t -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(tr(t)) }) }
        }
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (tab) {
                    0 -> OverviewTab(p, change) {
                        val events = ProposalChecks.events(p)
                        if (events.isEmpty()) Notices.info(tr("Keine Frist mit lesbarem Datum."))
                        else scope.launch {
                            val f = withContext(Dispatchers.IO) {
                                File(File(c.appContext.cacheDir, "antraege"), FileText.safeName("${p.title} – Fristen.ics")).apply { parentFile?.mkdirs(); CalendarFiles.writeAll(this, events) }
                            }
                            openDocument(context, f)
                        }
                    }
                    1 -> SectionsTab(p, change)
                    2 -> WorkPackagesTab(p, change)
                    3 -> BudgetTab(p, change)
                    else -> CheckTab(p)
                }
            }
        }
    }
    if (confirmDelete) ConfirmDialog(tr("Antrag löschen?"), "„${p.title}“ " + tr("wird gelöscht."), tr("Löschen"), onConfirm = { c.proposals.delete(p.id) }, onDismiss = { confirmDelete = false })
}

@Composable
private fun OverviewTab(p: Proposal, change: ((Proposal) -> Proposal) -> Unit, onCalendar: () -> Unit) {
    var newCheck by remember { mutableStateOf("") }
    var editDeadline by remember { mutableStateOf<ProposalDeadline?>(null) }
    Section(tr("Ausschreibung")) {
        Text(p.funderInfo.name + (if (p.program.isNotBlank()) " – ${p.program}" else "") + " · ${p.durationMonths} " + tr("Monate"), style = MaterialTheme.typography.bodyMedium)
        if (p.callSource.isNotBlank()) Text(tr("Quelle:") + " " + p.callSource, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (p.callSummary.isNotBlank()) MarkdownText(p.callSummary)
        else HintText(tr("Noch keine Ausschreibung eingelesen – „Mit Lora weiterarbeiten“ und das PDF oder den Link anhängen."))
    }
    Section(tr("Fristen")) {
        p.deadlines.sortedBy { it.date }.forEach { d ->
            val days = ProposalChecks.daysUntil(d.date)
            Row(Modifier.fillMaxWidth().clickable { editDeadline = d }, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(d.title)
                    Text(
                        d.date + when {
                            days == null -> " · " + tr("Datum nicht lesbar")
                            days < 0 -> " · " + tr("vorbei")
                            days == 0L -> " · " + tr("heute")
                            else -> " · " + tr("in") + " $days " + tr("Tagen")
                        } + if (d.note.isNotBlank()) " · ${d.note}" else "",
                        style = MaterialTheme.typography.bodySmall, color = if (days != null && days in 0..13) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (p.deadlines.isEmpty()) HintText(tr("Noch keine Fristen."))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { editDeadline = ProposalDeadline(title = "", date = "") }) { Text(tr("Frist hinzufügen")) }
            if (p.deadlines.isNotEmpty()) OutlinedButton(onClick = onCalendar) { Icon(Icons.Outlined.CalendarMonth, null, Modifier.size(18.dp)); Spacer(Modifier.size(4.dp)); Text(tr("In den Kalender")) }
        }
    }
    Section(tr("Checkliste") + " (${p.checklist.count { it.done }}/${p.checklist.size})") {
        p.checklist.forEach { item ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = item.done, onCheckedChange = { on -> change { q -> q.copy(checklist = q.checklist.map { if (it.id == item.id) it.copy(done = on) else it }) } })
                Text(item.text, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                IconButton(onClick = { change { q -> q.copy(checklist = q.checklist.filter { it.id != item.id }) } }) { Icon(Icons.Outlined.Delete, tr("Entfernen"), Modifier.size(18.dp)) }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = newCheck, onValueChange = { newCheck = it }, singleLine = true, label = { Text(tr("Neuer Punkt")) }, modifier = Modifier.weight(1f))
            TextButton(enabled = newCheck.isNotBlank(), onClick = { val t = newCheck.trim(); change { it.copy(checklist = it.checklist + ProposalCheck(text = t)) }; newCheck = "" }) { Text(tr("Hinzufügen")) }
        }
    }
    Section(tr("Forschungsdaten und Ethik")) {
        var dmp by remember(p.id) { mutableStateOf(p.dmp) }
        var ethics by remember(p.id) { mutableStateOf(p.ethics) }
        LaunchedEffect(dmp, ethics) {
            delay(600)
            if (dmp != p.dmp || ethics != p.ethics) change { it.copy(dmp = dmp, ethics = ethics) }
        }
        OutlinedTextField(value = dmp, onValueChange = { dmp = it }, minLines = 2, label = { Text(tr("Umgang mit Forschungsdaten (DMP)")) }, modifier = Modifier.fillMaxWidth(),
            supportingText = { Text(tr("Ausführlich mit der TU Vorlage „Datenmanagementplan“.")) })
        OutlinedTextField(value = ethics, onValueChange = { ethics = it }, minLines = 2, label = { Text(tr("Ethische und rechtliche Aspekte")) }, modifier = Modifier.fillMaxWidth(),
            supportingText = { Text(tr("Ethikvotum, Einwilligungen, Datenschutz, Tierversuche …")) })
    }
    editDeadline?.let { d ->
        var title by remember(d.id) { mutableStateOf(d.title) }
        var date by remember(d.id) { mutableStateOf(d.date) }
        var note by remember(d.id) { mutableStateOf(d.note) }
        val known = p.deadlines.any { it.id == d.id }
        AlertDialog(
            onDismissRequest = { editDeadline = null },
            title = { Text(tr(if (known) "Frist bearbeiten" else "Neue Frist")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = title, onValueChange = { title = it }, singleLine = true, label = { Text(tr("Was")) }, placeholder = { Text(tr("z. B. Einreichung Skizze")) })
                    OutlinedTextField(value = date, onValueChange = { date = it }, singleLine = true, label = { Text(tr("Datum")) }, placeholder = { Text("2027-02-15 " + tr("oder") + " 15.02.2027 14:00") },
                        isError = date.isNotBlank() && CalendarFiles.parse(date) == null)
                    OutlinedTextField(value = note, onValueChange = { note = it }, singleLine = true, label = { Text(tr("Notiz (optional)")) })
                }
            },
            confirmButton = {
                Button(enabled = title.isNotBlank() && CalendarFiles.parse(date) != null, onClick = {
                    val next = d.copy(title = title.trim(), date = date.trim(), note = note.trim())
                    change { q -> q.copy(deadlines = if (known) q.deadlines.map { if (it.id == d.id) next else it } else q.deadlines + next) }
                    editDeadline = null
                }) { Text(tr("Speichern")) }
            },
            dismissButton = {
                Row {
                    if (known) TextButton(onClick = { change { q -> q.copy(deadlines = q.deadlines.filter { it.id != d.id }) }; editDeadline = null }) { Text(tr("Löschen")) }
                    TextButton(onClick = { editDeadline = null }) { Text(tr("Abbrechen")) }
                }
            },
        )
    }
}

@Composable
private fun SectionsTab(p: Proposal, change: ((Proposal) -> Proposal) -> Unit) {
    var newTitle by remember { mutableStateOf("") }
    var cpp by remember(p.id) { mutableStateOf(p.charsPerPage.toString()) }
    val total = p.sections.sumOf { ProposalChecks.count(it.text, p.charsPerPage).pages }
    Text(
        tr("Insgesamt etwa") + " ${ProposalChecks.pages(total)} " + tr("Seiten") + (p.pageLimit?.let { " " + tr("von") + " ${ProposalChecks.pages(it)}" } ?: "") +
            " · " + tr("Seiten geschätzt mit") + " ${ProposalChecks.num(p.charsPerPage)} " + tr("Zeichen je Seite (≈ 11 pt, einzeilig)"),
        style = MaterialTheme.typography.bodySmall, color = if (p.pageLimit != null && total > p.pageLimit) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(value = cpp, onValueChange = { v -> cpp = v.filter { it.isDigit() }.take(5) }, singleLine = true, label = { Text(tr("Zeichen je Seite")) }, modifier = Modifier.width(160.dp))
        TextButton(enabled = cpp.toIntOrNull()?.let { it in 500..10_000 && it != p.charsPerPage } == true, onClick = { change { it.copy(charsPerPage = cpp.toInt()) } }) { Text(tr("Übernehmen")) }
    }
    p.sections.forEachIndexed { i, s -> SectionCard(p, i, s, change) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(value = newTitle, onValueChange = { newTitle = it }, singleLine = true, label = { Text(tr("Neuer Abschnitt")) }, modifier = Modifier.weight(1f))
        TextButton(enabled = newTitle.isNotBlank(), onClick = { val t = newTitle.trim(); change { it.copy(sections = it.sections + ProposalSection(title = t)) }; newTitle = "" }) { Text(tr("Hinzufügen")) }
    }
}

@Composable
private fun SectionCard(p: Proposal, index: Int, s: ProposalSection, change: ((Proposal) -> Proposal) -> Unit) {
    var text by remember(s.id) { mutableStateOf(s.text) }
    var limits by remember { mutableStateOf(false) }
    // Typed text is saved after a short pause; text the agent wrote comes in when nothing is being typed.
    LaunchedEffect(s.text) { if (s.text != text) text = s.text }
    LaunchedEffect(text) {
        delay(700)
        if (text != s.text) change { q -> q.copy(sections = q.sections.map { if (it.id == s.id) it.copy(text = text) else it }) }
    }
    val live = s.copy(text = text)
    val n = ProposalChecks.count(text, p.charsPerPage)
    val over = ProposalChecks.overLimit(live, p.charsPerPage)
    val usage = ProposalChecks.usage(live, p.charsPerPage)
    Surface(shape = MaterialTheme.shapes.medium, color = LocalChatLook.current.card, border = boxBorder(), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(s.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                TextButton(onClick = { proposalChat(tr("Schreibe im Antrag „{0}“ ({1}) den Abschnitt „{2}“: ", p.title, "${ProposalTools.ID_LABEL} ${p.id}", s.title), p) }) { Text(tr("Mit Lora")) }
                TextButton(onClick = { limits = true }) { Text(tr("Grenze")) }
            }
            if (s.guidance.isNotBlank()) Text(s.guidance, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(value = text, onValueChange = { text = it }, minLines = 4, modifier = Modifier.fillMaxWidth())
            if (usage != null) LinearProgressIndicator(
                progress = { usage.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth(),
                color = if (over != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )
            Text(
                "${ProposalChecks.num(n.chars)} " + tr("Zeichen") + " · ${ProposalChecks.num(n.words)} " + tr("Wörter") + " · ≈ ${ProposalChecks.pages(n.pages)} " + tr("Seiten") +
                    (ProposalChecks.limitLabel(s)?.let { " · $it" } ?: "") + (over?.let { " · " + tr("zu lang:") + " $it" } ?: ""),
                style = MaterialTheme.typography.labelSmall, color = if (over != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (limits) {
        var chars by remember { mutableStateOf(s.limitChars?.toString().orEmpty()) }
        var words by remember { mutableStateOf(s.limitWords?.toString().orEmpty()) }
        var pages by remember { mutableStateOf(s.limitPages?.let(ProposalChecks::pages).orEmpty()) }
        var title by remember { mutableStateOf(s.title) }
        AlertDialog(
            onDismissRequest = { limits = false },
            title = { Text(tr("Abschnitt") + " ${index + 1}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = title, onValueChange = { title = it }, singleLine = true, label = { Text(tr("Titel")) })
                    OutlinedTextField(value = chars, onValueChange = { v -> chars = v.filter(Char::isDigit) }, singleLine = true, label = { Text(tr("Höchstens Zeichen (mit Leerzeichen)")) })
                    OutlinedTextField(value = words, onValueChange = { v -> words = v.filter(Char::isDigit) }, singleLine = true, label = { Text(tr("Höchstens Wörter")) })
                    OutlinedTextField(value = pages, onValueChange = { v -> pages = v.filter { it.isDigit() || it == ',' || it == '.' } }, singleLine = true, label = { Text(tr("Höchstens Seiten")) })
                }
            },
            confirmButton = {
                Button(enabled = title.isNotBlank(), onClick = {
                    change { q -> q.copy(sections = q.sections.map {
                        if (it.id == s.id) it.copy(title = title.trim(), limitChars = chars.toIntOrNull(), limitWords = words.toIntOrNull(), limitPages = pages.replace(',', '.').toDoubleOrNull()) else it
                    }) }
                    limits = false
                }) { Text(tr("Speichern")) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { change { q -> q.copy(sections = q.sections.filter { it.id != s.id }) }; limits = false }) { Text(tr("Abschnitt löschen")) }
                    TextButton(onClick = { limits = false }) { Text(tr("Abbrechen")) }
                }
            },
        )
    }
}

@Composable
private fun WorkPackagesTab(p: Proposal, change: ((Proposal) -> Proposal) -> Unit) {
    var edit by remember { mutableStateOf<WorkPackage?>(null) }
    var duration by remember(p.id) { mutableStateOf(p.durationMonths.toString()) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(value = duration, onValueChange = { v -> duration = v.filter(Char::isDigit).take(3) }, singleLine = true, label = { Text(tr("Laufzeit in Monaten")) }, modifier = Modifier.width(180.dp))
        TextButton(enabled = duration.toIntOrNull()?.let { it in 1..120 && it != p.durationMonths } == true, onClick = { change { it.copy(durationMonths = duration.toInt()) } }) { Text(tr("Übernehmen")) }
    }
    if (p.workPackages.isEmpty()) HintText(tr("Noch keine Arbeitspakete. Lora plant sie mit dir, oder du legst sie hier an."))
    else Gantt(p) { edit = it }
    OutlinedButton(onClick = { edit = WorkPackage(title = "", start = 1, months = minOf(6, p.durationMonths)) }) { Icon(Icons.Outlined.Add, null, Modifier.size(18.dp)); Spacer(Modifier.size(4.dp)); Text(tr("Arbeitspaket")) }
    edit?.let { w ->
        val known = p.workPackages.any { it.id == w.id }
        var title by remember(w.id) { mutableStateOf(w.title) }
        var lead by remember(w.id) { mutableStateOf(w.lead) }
        var start by remember(w.id) { mutableStateOf(w.start.toString()) }
        var months by remember(w.id) { mutableStateOf(w.months.toString()) }
        var deliverables by remember(w.id) { mutableStateOf(w.deliverables) }
        AlertDialog(
            onDismissRequest = { edit = null },
            title = { Text(tr(if (known) "Arbeitspaket bearbeiten" else "Neues Arbeitspaket")) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = title, onValueChange = { title = it }, singleLine = true, label = { Text(tr("Titel")) })
                    OutlinedTextField(value = lead, onValueChange = { lead = it }, singleLine = true, label = { Text(tr("Leitung")) })
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = start, onValueChange = { v -> start = v.filter(Char::isDigit) }, singleLine = true, label = { Text(tr("Ab Monat")) }, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = months, onValueChange = { v -> months = v.filter(Char::isDigit) }, singleLine = true, label = { Text(tr("Dauer (Monate)")) }, modifier = Modifier.weight(1f))
                    }
                    OutlinedTextField(value = deliverables, onValueChange = { deliverables = it }, minLines = 2, label = { Text(tr("Ergebnisse / Meilensteine")) })
                }
            },
            confirmButton = {
                Button(enabled = title.isNotBlank() && (start.toIntOrNull() ?: 0) >= 1 && (months.toIntOrNull() ?: 0) >= 1, onClick = {
                    val next = w.copy(title = title.trim(), lead = lead.trim(), start = start.toInt(), months = months.toInt(), deliverables = deliverables.trim())
                    change { q -> q.copy(workPackages = if (known) q.workPackages.map { if (it.id == w.id) next else it } else q.workPackages + next) }
                    edit = null
                }) { Text(tr("Speichern")) }
            },
            dismissButton = {
                Row {
                    if (known) TextButton(onClick = { change { q -> q.copy(workPackages = q.workPackages.filter { it.id != w.id }) }; edit = null }) { Text(tr("Löschen")) }
                    TextButton(onClick = { edit = null }) { Text(tr("Abbrechen")) }
                }
            },
        )
    }
}

/** The Arbeitspakete as bars over the months of the project (a line per year). */
@Composable
private fun Gantt(p: Proposal, onEdit: (WorkPackage) -> Unit) {
    val months = p.durationMonths.coerceAtLeast(1)
    val brand = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    Surface(shape = MaterialTheme.shapes.medium, color = LocalChatLook.current.card, border = boxBorder(), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row {
                Spacer(Modifier.width(150.dp))
                BoxWithConstraints(Modifier.weight(1f)) {
                    val w = maxWidth
                    (0 until p.years).forEach { y ->
                        Text(tr("Jahr") + " ${y + 1}", style = MaterialTheme.typography.labelSmall, modifier = Modifier.offset(x = w * (y * 12f / months)))
                    }
                }
            }
            p.workPackages.forEachIndexed { i, wp ->
                val outside = wp.start + wp.months - 1 > months
                Row(Modifier.fillMaxWidth().clickable { onEdit(wp) }, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.width(150.dp).padding(end = 8.dp)) {
                        Text(tr("AP {0} ", i + 1) + wp.title, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text("M${wp.start}–M${wp.start + wp.months - 1}" + if (wp.lead.isNotBlank()) " · ${wp.lead}" else "", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                    BoxWithConstraints(Modifier.weight(1f).height(22.dp).background(grid.copy(alpha = 0.25f), RoundedCornerShape(4.dp))) {
                        val w = maxWidth
                        val from = ((wp.start - 1).coerceIn(0, months)).toFloat() / months
                        val len = (wp.months.toFloat() / months).coerceAtMost(1f - from)
                        Box(Modifier.offset(x = w * from).width(w * len.coerceAtLeast(0.01f)).height(22.dp).background(if (outside) MaterialTheme.colorScheme.error else brand, RoundedCornerShape(4.dp)))
                        (1 until p.years).forEach { y -> Box(Modifier.offset(x = w * (y * 12f / months)).width(1.dp).height(22.dp).background(grid)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun BudgetTab(p: Proposal, change: ((Proposal) -> Proposal) -> Unit) {
    var edit by remember { mutableStateOf<BudgetRow?>(null) }
    var overhead by remember(p.id) { mutableStateOf(p.overheadPercent?.let(ProposalChecks::pages).orEmpty()) }
    val cats = (p.funderInfo.budgetCategories + p.budget.map { it.category }).distinct()
    Section(tr("Finanzplan") + " · ${p.years} " + tr("Projektjahre")) {
        cats.forEach { cat ->
            val rows = p.budget.filter { it.category == cat }
            if (rows.isEmpty()) return@forEach
            Text(cat + " · " + ProposalChecks.euro(rows.sumOf { it.total }), style = MaterialTheme.typography.titleSmall)
            rows.forEach { r ->
                Row(Modifier.fillMaxWidth().clickable { edit = r }.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(r.item.ifBlank { cat }, style = MaterialTheme.typography.bodyMedium)
                        Text(r.amounts.mapIndexed { y, a -> tr("J") + "${y + 1}: " + ProposalChecks.euro(a) }.joinToString(" · ") + if (r.note.isNotBlank()) " · ${r.note}" else "",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(ProposalChecks.euro(r.total), style = MaterialTheme.typography.bodyMedium)
                }
            }
            HorizontalDivider()
        }
        if (p.budget.isEmpty()) HintText(tr("Noch keine Posten."))
        Text(tr("Direkte Kosten:") + " " + ProposalChecks.euro(p.directCosts), style = MaterialTheme.typography.titleSmall)
        if (p.overhead > 0) Text(p.funderInfo.overheadLabel + ": " + ProposalChecks.euro(p.overhead) + " · " + tr("gesamt") + " " + ProposalChecks.euro(p.directCosts + p.overhead), style = MaterialTheme.typography.bodySmall)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = overhead, onValueChange = { v -> overhead = v.filter { it.isDigit() || it == ',' || it == '.' }.take(5) }, singleLine = true, label = { Text(tr("Pauschale in %")) }, modifier = Modifier.width(150.dp))
            TextButton(onClick = { change { it.copy(overheadPercent = overhead.replace(',', '.').toDoubleOrNull()?.takeIf { v -> v > 0 }) } }) { Text(tr("Übernehmen")) }
        }
        OutlinedButton(onClick = { edit = BudgetRow(category = cats.firstOrNull() ?: "Personal", item = "", amounts = List(p.years) { 0.0 }) }) { Icon(Icons.Outlined.Add, null, Modifier.size(18.dp)); Spacer(Modifier.size(4.dp)); Text(tr("Posten")) }
        HintText(tr("Personalmittel nach den aktuellen Sätzen des Förderers (z. B. DFG-Personalmittelsätze) – Lora kennzeichnet Annahmen."))
    }
    edit?.let { r -> BudgetDialog(p, r, cats, change) { edit = null } }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BudgetDialog(p: Proposal, r: BudgetRow, cats: List<String>, change: ((Proposal) -> Proposal) -> Unit, onDone: () -> Unit) {
    val known = p.budget.any { it.id == r.id }
    var category by remember(r.id) { mutableStateOf(r.category) }
    var item by remember(r.id) { mutableStateOf(r.item) }
    var note by remember(r.id) { mutableStateOf(r.note) }
    val amounts = remember(r.id) { androidx.compose.runtime.mutableStateListOf(*List(p.years) { y -> r.amounts.getOrNull(y)?.let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() }.orEmpty() }.toTypedArray()) }
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(tr(if (known) "Posten bearbeiten" else "Neuer Posten")) },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { cats.forEach { cat -> FilterChip(selected = category == cat, onClick = { category = cat }, label = { Text(cat) }) } }
                OutlinedTextField(value = category, onValueChange = { category = it }, singleLine = true, label = { Text(tr("Kategorie")) })
                OutlinedTextField(value = item, onValueChange = { item = it }, singleLine = true, label = { Text(tr("Posten")) }, placeholder = { Text(tr("z. B. Doktorand:in 65 %")) })
                amounts.indices.forEach { y ->
                    OutlinedTextField(value = amounts[y], onValueChange = { v -> amounts[y] = v.filter { it.isDigit() || it == ',' || it == '.' } }, singleLine = true, label = { Text(tr("Jahr") + " ${y + 1} (€)") })
                }
                OutlinedTextField(value = note, onValueChange = { note = it }, singleLine = true, label = { Text(tr("Begründung (optional)")) })
            }
        },
        confirmButton = {
            Button(enabled = category.isNotBlank(), onClick = {
                val next = r.copy(category = category.trim(), item = item.trim(), note = note.trim(), amounts = amounts.map { it.replace(',', '.').toDoubleOrNull() ?: 0.0 })
                change { q -> q.copy(budget = if (known) q.budget.map { if (it.id == r.id) next else it } else q.budget + next) }
                onDone()
            }) { Text(tr("Speichern")) }
        },
        dismissButton = {
            Row {
                if (known) TextButton(onClick = { change { q -> q.copy(budget = q.budget.filter { it.id != r.id }) }; onDone() }) { Text(tr("Löschen")) }
                TextButton(onClick = onDone) { Text(tr("Abbrechen")) }
            }
        },
    )
}

@Composable
private fun CheckTab(p: Proposal) {
    val findings = remember(p) { ProposalChecks.check(p) }
    Section(tr("Abgabe-Check")) {
        findings.forEach { f ->
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    when (f.level) { ProposalChecks.Level.OK -> "✓"; ProposalChecks.Level.WARN -> "!"; ProposalChecks.Level.ERROR -> "✗" },
                    color = when (f.level) {
                        ProposalChecks.Level.OK -> MaterialTheme.colorScheme.primary
                        ProposalChecks.Level.WARN -> MaterialTheme.colorScheme.tertiary
                        ProposalChecks.Level.ERROR -> MaterialTheme.colorScheme.error
                    },
                    modifier = Modifier.width(22.dp),
                )
                Text(tr(f.text), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
    if (p.reviews.isNotEmpty()) Section(tr("Probeläufe")) {
        val df = java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.GERMANY)
        p.reviews.asReversed().forEach { r ->
            Text(df.format(java.util.Date(r.at)), style = MaterialTheme.typography.labelLarge)
            MarkdownText(r.summary)
            HorizontalDivider()
        }
    }
}
