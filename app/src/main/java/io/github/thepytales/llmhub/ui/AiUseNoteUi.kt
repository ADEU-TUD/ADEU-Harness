package io.github.thepytales.llmhub.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.agent.AiUseNote
import io.github.thepytales.llmhub.data.Conversation
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * KI-Nutzungsnachweis (AP-19) in the chat: once a chat is about a work and the AI wrote for it, a small pop-up above the
 * input offers the declaration (once per chat; „Nicht mehr vorschlagen“ switches it off under Einstellungen › Lora).
 * Always reachable in the chat menu.
 */
object AiUseNoteOffers {
    private const val KEY = "aiNoteDone"

    /** Chats where the offer was taken or declined (kept on this device). */
    val done = MutableStateFlow<Set<String>>(emptySet())
    private var loaded = false

    fun load(c: AppContainer) {
        if (loaded) return
        loaded = true
        done.value = c.appContext.getSharedPreferences("ui_state", 0).getString(KEY, null).orEmpty().lines().filter { it.isNotBlank() }.toSet()
    }

    fun close(c: AppContainer, chatId: String) {
        done.value = done.value + chatId
        c.appContext.getSharedPreferences("ui_state", 0).edit().putString(KEY, done.value.toList().takeLast(500).joinToString("\n")).apply()
    }
}

/** The pop-up above the input (see [AiUseNoteOffers]). */
@Composable
fun AiUseNoteSuggestion(c: AppContainer, conv: Conversation, running: Boolean, onOpen: () -> Unit) {
    val settings by c.settings.settings.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { AiUseNoteOffers.load(c) }
    val done by AiUseNoteOffers.done.collectAsStateWithLifecycle()
    val offer = remember(conv.messages.size, running) { !running && !conv.temporary && AiUseNote.shouldOffer(conv) }
    val work = remember(conv.messages.size) { AiUseNote.workIn(conv.messages.filter { it.role == "user" }.joinToString(" ") { it.content } + " " + conv.title) }
    AnimatedVisibility(settings.aiUseHint && offer && conv.id !in done, enter = fadeIn() + expandVertically()) {
        Surface(
            color = LocalChatLook.current.card,
            border = BorderStroke(1.dp, LocalChatLook.current.brand.copy(alpha = 0.4f)),
            shape = RoundedCornerShape(16.dp),
            shadowElevation = 4.dp,
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
        ) {
            Column(Modifier.padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LoraFaceStill(Modifier.size(28.dp), action = io.github.thepytales.llmhub.ui.LoraAction.EUREKA)
                    Spacer(Modifier.size(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            (work?.let { tr("Für deine") + " $it: " } ?: "") + tr("KI-Nutzung dokumentieren?"),
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Text(
                            tr("Lora erstellt aus diesem Chat einen KI-Nutzungsnachweis – Modelle, Zweck, Zeitraum – für die Prüfungsordnung, einen Verlag oder als Fußnote."),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { AiUseNoteOffers.close(c, conv.id) }) { Icon(Icons.Outlined.Close, contentDescription = tr("Schließen"), modifier = Modifier.size(18.dp)) }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { c.settings.update { it.copy(aiUseHint = false) }; Notices.info(tr("Kein Vorschlag mehr – der Nachweis bleibt im Chat-Menü.")) }) {
                        Text(tr("Nicht mehr vorschlagen"))
                    }
                    TextButton(onClick = { AiUseNoteOffers.close(c, conv.id); onOpen() }) { Text(tr("Nachweis erstellen")) }
                }
            }
        }
    }
}

/** The declaration in a format of choice, for this chat or – in a project – all its chats; copy it or open it as a document. */
@Composable
fun AiUseNoteDialog(c: AppContainer, conv: Conversation, onDismiss: () -> Unit) {
    var format by remember { mutableStateOf(AiUseNote.Format.EXAM) }
    var wholeProject by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val usage = remember(conv.id, conv.messages.size, wholeProject) {
        val chats = if (wholeProject && conv.folderId != null) {
            c.chats.conversations.value.filter { it.folderId == conv.folderId }.mapNotNull { c.chats.get(it.id) }.ifEmpty { listOf(conv) }
        } else listOf(conv)
        AiUseNote.usage(chats)
    }
    val text = remember(usage, format) { AiUseNote.text(usage, format) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("KI-Nutzungsnachweis")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Segmented(AiUseNote.Format.entries.map { it.name to tr(it.label) }, format.name, { f -> format = AiUseNote.Format.valueOf(f) })
                if (conv.folderId != null) SwitchRow(tr("Alle Chats des Projekts"), tr("Nachweis für die ganze Arbeit im Projekt statt nur für diesen Chat."), wholeProject) { wholeProject = it }
                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    SelectionContainer {
                        Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()).padding(12.dp)) { MarkdownText(text) }
                    }
                }
                HintText(tr("Aus diesem Chat auf dem Gerät erstellt. Betroffene Teile ergänzen und mit den Vorgaben deiner Prüfungsordnung, deines Lehrstuhls bzw. des Verlags abgleichen."))
            }
        },
        confirmButton = {
            TextButton(onClick = {
                DocsHandoff.open(c, tr("KI-Nutzungsnachweis") + (usage.work?.let { " – $it" } ?: ""), text, conv.id)
                onDismiss()
            }) { Text(tr("Als Dokument")) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { clipboard.setText(AnnotatedString(text)); Notices.info(tr("Nachweis kopiert.")) }) { Text(tr("Kopieren")) }
                TextButton(onClick = onDismiss) { Text(tr("Schließen")) }
            }
        },
    )
}
