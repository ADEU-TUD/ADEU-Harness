package io.github.thepytales.llmhub.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.agent.ChatModes
import io.github.thepytales.llmhub.data.DesignSupport
import io.github.thepytales.llmhub.data.DesignTemplates
import io.github.thepytales.llmhub.data.FileAttachment

/**
 * „Erstellen“ (AP-24): at the top of the area one field for every wish – Lora makes it, from a letter to a poster.
 * Wishes about designs (poster, flyer, social media …) start the Gestalten mode on Windows; files can be attached
 * („Poster aus meinem Paper“).
 */
@Composable
fun CreateWithLora(c: AppContainer, modifier: Modifier = Modifier) {
    var text by rememberSaveable { mutableStateOf("") }
    val files = remember { mutableStateListOf<FileAttachment>() }
    val pick = rememberDocumentPicker { files += it }
    fun go() {
        val wish = text.trim()
        if (wish.isEmpty()) return
        val design = DesignSupport.EDITOR && DesignTemplates.fits(wish)
        ChatHandoff.pending.value = ChatHandoff.Content(wish, files.toList(), mode = if (design) ChatModes.DESIGN.id else null, send = true)
        ChatHandoff.open.value = ""
        text = ""
        files.clear()
    }
    Surface(shape = MaterialTheme.shapes.large, color = LocalChatLook.current.card, border = boxBorder(), modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LoraFaceStill(Modifier.size(30.dp))
                Spacer(Modifier.size(8.dp))
                Text(tr("Was möchtest du erstellen?"), style = MaterialTheme.typography.titleSmall)
            }
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                minLines = 1, maxLines = 4,
                placeholder = {
                    Text(
                        if (DesignSupport.EDITOR) tr("z. B. „Tagungsposter aus meinem Paper“, „Aushang für die Sprechstunde“ oder „Protokoll aus meinen Notizen“")
                        else tr("z. B. „Einladung zur Sitzung am Montag“ oder „Protokoll aus meinen Notizen“"),
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                },
            )
            if (files.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    files.forEach { f ->
                        InputChip(selected = false, onClick = { files.remove(f) }, label = { Text(f.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            trailingIcon = { Icon(Icons.Outlined.Close, contentDescription = tr("Entfernen"), modifier = Modifier.size(16.dp)) })
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = pick) { Icon(Icons.Outlined.AttachFile, contentDescription = tr("Datei anhängen")) }
                Text(
                    tr("Lora erledigt es im Chat – oder unten eine Vorlage wählen."),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f),
                )
                Button(onClick = ::go, enabled = text.isNotBlank()) {
                    Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(18.dp)); Spacer(Modifier.size(6.dp)); Text(tr("Lora machen lassen"))
                }
            }
        }
    }
}
