package io.github.thepytales.llmhub.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.data.Conversation
import io.github.thepytales.llmhub.data.ModelCatalog
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationsScreen(c: AppContainer, onOpen: (String) -> Unit, onSettings: () -> Unit) {
    val conversations by c.chats.conversations.collectAsStateWithLifecycle()
    val settings by c.settings.settings.collectAsStateWithLifecycle()
    var toDelete by remember { mutableStateOf<Conversation?>(null) }
    val fmt = remember { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Chats") }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onOpen(newChat(c).id) },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Neuer Chat") },
            )
        },
    ) { pad ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = pad.calculateTopPadding(),
                bottom = pad.calculateBottomPadding() + 88.dp,
            ),
        ) {
            if (!settings.hasApiKey) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Willkommen!", style = MaterialTheme.typography.titleMedium)
                            Text("Trage zuerst deinen API-Key ein, um die Modelle nutzen zu können.")
                            Button(onClick = onSettings) {
                                Icon(Icons.Outlined.VpnKey, contentDescription = null)
                                Spacer(Modifier.padding(4.dp))
                                Text("API-Key eintragen")
                            }
                        }
                    }
                }
            }
            if (conversations.isEmpty()) {
                item {
                    Column(Modifier.fillMaxWidth().padding(32.dp)) {
                        Spacer(Modifier.height(48.dp))
                        Text(
                            "Noch keine Chats.\nTippe auf „Neuer Chat“, um loszulegen.",
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            items(conversations, key = { it.id }) { conv ->
                val leading: (@Composable () -> Unit)? = if (conv.githubEnabled) {
                    { Icon(Icons.Outlined.Code, contentDescription = "GitHub") }
                } else null
                ListItem(
                    modifier = Modifier.clickable { onOpen(conv.id) },
                    headlineContent = { Text(conv.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = {
                        val parts = listOfNotNull(
                            ModelCatalog.info(conv.model).displayName,
                            conv.repo,
                            fmt.format(Date(conv.updatedAt)),
                        )
                        Text(parts.joinToString(" · "), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    leadingContent = leading,
                    trailingContent = {
                        IconButton(onClick = { toDelete = conv }) {
                            Icon(Icons.Outlined.Delete, contentDescription = "Löschen")
                        }
                    },
                )
            }
        }
    }

    toDelete?.let { conv ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Chat löschen?") },
            text = { Text("„${conv.title}“ wird dauerhaft gelöscht.") },
            confirmButton = {
                TextButton(onClick = { c.chats.delete(conv.id); toDelete = null }) { Text("Löschen") }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Abbrechen") } },
        )
    }
}
