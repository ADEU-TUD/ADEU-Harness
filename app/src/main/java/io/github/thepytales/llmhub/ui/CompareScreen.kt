package io.github.thepytales.llmhub.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.data.ChatMessage
import io.github.thepytales.llmhub.data.Conversation
import io.github.thepytales.llmhub.data.ModelCatalog
import io.github.thepytales.llmhub.data.ModelType
import io.github.thepytales.llmhub.data.appendToThread
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.Locale

/** One column of the comparison: a model with its own history. */
class CompareSide(model: String) {
    var model by mutableStateOf(model)
    val messages = mutableStateListOf<ChatMessage>()
    /** Timing and token numbers per answer id. */
    val stats = mutableStateListOf<Pair<String, String>>()
    var busy by mutableStateOf(false)
}

class CompareViewModel(private val c: AppContainer) : ViewModel() {
    private val chatModels get() = c.models.state.value.pickable.map { it.id }
    val left = CompareSide(c.settings.current.defaultModel)
    val right = CompareSide(chatModels.firstOrNull { it != left.model } ?: left.model)
    private val jobs = mutableListOf<Job>()

    val busy get() = left.busy || right.busy

    fun send(text: String) {
        if (text.isBlank() || busy) return
        listOf(left, right).forEach { side ->
            side.messages += ChatMessage(role = "user", content = text.trim())
            val history = side.messages.toList()
            val answer = ChatMessage(role = "assistant", model = side.model)
            side.messages += answer
            side.busy = true
            jobs += viewModelScope.launch {
                val start = System.currentTimeMillis()
                var first = 0L
                fun replace(transform: (ChatMessage) -> ChatMessage) {
                    val i = side.messages.indexOfFirst { it.id == answer.id }
                    if (i >= 0) side.messages[i] = transform(side.messages[i])
                }
                try {
                    // The memory goes along here too, so both models know the same about the user.
                    val memory = io.github.thepytales.llmhub.agent.Memory.promptFor(c, null)
                    val sent = if (memory != null) listOf(ChatMessage(role = "system", content = memory)) + history else history
                    val result = c.llm.streamChat(side.model, sent, null) { upd ->
                        if (first == 0L && (upd.content.isNotEmpty() || upd.reasoning.isNotEmpty())) first = System.currentTimeMillis()
                        replace { it.copy(content = upd.content, reasoning = upd.reasoning.ifEmpty { null }) }
                    }
                    val total = (System.currentTimeMillis() - start) / 1000.0
                    val ttft = if (first > 0) (first - start) / 1000.0 else total
                    val tokens = result.usage?.completion
                    val parts = mutableListOf(String.format(Locale.GERMANY, tr("%.1f s bis zur Antwort"), ttft), String.format(Locale.GERMANY, "%.1f s gesamt", total))
                    if (tokens != null && tokens > 0) {
                        parts += tr("{0} Tokens", tokens)
                        val genTime = (total - ttft).coerceAtLeast(0.1)
                        parts += String.format(Locale.GERMANY, tr("%.0f Tok/s"), tokens / genTime)
                    }
                    side.stats += answer.id to parts.joinToString(" · ")
                } catch (e: CancellationException) {
                    replace { it.copy(content = it.content.ifBlank { "_(abgebrochen)_" }) }
                    throw e
                } catch (e: Exception) {
                    replace { it.copy(content = io.github.thepytales.llmhub.net.NetErrors.describe(e), error = true) }
                } finally {
                    side.busy = false
                }
            }
        }
    }

    fun stop() {
        jobs.forEach { it.cancel() }
        jobs.clear()
    }

    fun clear() {
        stop()
        listOf(left, right).forEach { it.messages.clear(); it.stats.clear() }
    }

    /** Continues one side as a normal chat. */
    fun toChat(side: CompareSide): String? {
        val msgs = side.messages.filter { !it.error && (it.role == "user" || it.content.isNotBlank()) }
        if (msgs.isEmpty()) return null
        var conv = Conversation(model = side.model, title = msgs.first().content.lineSequence().first().take(48))
        msgs.forEach { conv = conv.appendToThread(it) }
        c.chats.save(conv)
        return conv.id
    }
}

/** Two models answer the same question side by side. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompareScreen(c: AppContainer, onBack: () -> Unit, onOpenChat: (String) -> Unit) {
    val vm: CompareViewModel = viewModel { CompareViewModel(c) }
    val models by c.models.state.collectAsStateWithLifecycle()
    var input by remember { mutableStateOf("") }
    var picking by remember { mutableStateOf<CompareSide?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { BackButton(onBack) },
                title = { Text(tr("Modelle vergleichen")) },
                actions = {
                    IconButton(onClick = { vm.clear() }) { Icon(Icons.Outlined.DeleteSweep, contentDescription = tr("Leeren")) }
                },
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.background) {
                Row(
                    Modifier
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(10.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(28.dp), modifier = Modifier.weight(1f)) {
                        TextField(
                            value = input,
                            onValueChange = { input = it },
                            placeholder = { Text(tr("Frage an beide Modelle")) },
                            maxLines = 5,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Spacer(Modifier.size(8.dp))
                    if (vm.busy) {
                        FilledIconButton(
                            onClick = { vm.stop() },
                            colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.onSurface),
                        ) { Icon(Icons.Outlined.Stop, contentDescription = tr("Stopp"), tint = MaterialTheme.colorScheme.surface) }
                    } else {
                        FilledIconButton(enabled = input.isNotBlank(), onClick = { vm.send(input); input = "" }) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = tr("Senden"))
                        }
                    }
                }
            }
        },
    ) { pad ->
        Row(Modifier.fillMaxSize().padding(pad)) {
            CompareColumn(vm.left, Modifier.weight(1f), onPick = { picking = vm.left }, onContinue = { vm.toChat(vm.left)?.let(onOpenChat) })
            VerticalDivider()
            CompareColumn(vm.right, Modifier.weight(1f), onPick = { picking = vm.right }, onContinue = { vm.toChat(vm.right)?.let(onOpenChat) })
        }
    }

    picking?.let { side ->
        ModelPickerSheet(
            models = models.ofType(ModelType.CHAT),
            selected = side.model,
            onSelect = { side.model = it },
            onDismiss = { picking = null },
        )
    }
}

@Composable
private fun CompareColumn(side: CompareSide, modifier: Modifier, onPick: () -> Unit, onContinue: () -> Unit) {
    Column(modifier.fillMaxHeight()) {
        Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier
                .padding(8.dp)
                .fillMaxWidth()
                .clickable(enabled = !side.busy, onClick = onPick),
        ) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    ModelCatalog.info(side.model).displayName,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(18.dp))
            }
        }
        if (side.messages.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                Text(
                    ModelCatalog.info(side.model).summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Column
        }
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            itemsIndexed(side.messages, key = { _, m -> m.id }) { i, m ->
                if (m.role == "user") {
                    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(14.dp)) {
                        Text(m.content, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(10.dp))
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        val (thinking, answer) = remember(m.content) { splitThinking(m.content) }
                        val reasoning = listOfNotNull(m.reasoning, thinking).joinToString("\n").ifBlank { null }
                        val streaming = side.busy && i == side.messages.lastIndex
                        if (reasoning != null) ThinkingBox(reasoning, streaming && answer.isBlank())
                        when {
                            m.error -> Text(m.content, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                            answer.isBlank() && streaming -> CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            else -> SelectionContainer { MarkdownText(answer) }
                        }
                        side.stats.firstOrNull { it.first == m.id }?.let { (_, s) ->
                            Text(s, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            if (!side.busy && side.messages.any { it.role == "assistant" && !it.error }) {
                item {
                    TextButton(onClick = onContinue) {
                        Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(6.dp))
                        Text(tr("Als Chat fortsetzen"))
                    }
                }
            }
        }
    }
}
