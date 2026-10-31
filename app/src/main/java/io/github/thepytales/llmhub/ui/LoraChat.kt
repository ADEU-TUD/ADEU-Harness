package io.github.thepytales.llmhub.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.LibraryBooks
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.agent.LoraSense
import io.github.thepytales.llmhub.agent.RepoContext
import io.github.thepytales.llmhub.agent.loraCalendar
import io.github.thepytales.llmhub.data.AppMap
import io.github.thepytales.llmhub.data.ChatMessage
import io.github.thepytales.llmhub.data.TemplateLibrary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File

/**
 * Lora in the chat (AP-33, AP-35): the card above a question she handed over, next steps under the last answer, the
 * hint while typing, and her little signature that cheers when an answer is done.
 */

/** Above a message Lora handed over: „Von Lora übergeben · Modell · Grund“, lands with a soft glow. */
@Composable
fun LoraHandoffCard(m: ChatMessage) {
    val line = m.handoff ?: return
    val fresh = remember(m.id) { System.currentTimeMillis() - m.createdAt < 5_000 }
    val glow = remember(m.id) { Animatable(if (fresh) 1f else 0f) }
    LaunchedEffect(m.id) { if (fresh) glow.animateTo(0f, tween(1800)) }
    var open by remember(m.id) { mutableStateOf(false) }
    val brand = LocalChatLook.current.brand
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = brand.copy(alpha = 0.06f + 0.10f * glow.value),
        border = BorderStroke(1.dp, brand.copy(alpha = 0.25f + 0.5f * glow.value)),
        modifier = Modifier.padding(bottom = 6.dp).widthIn(max = 460.dp),
    ) {
        Column(Modifier.clip(RoundedCornerShape(14.dp)).clickable(enabled = !m.instructions.isNullOrBlank()) { open = !open }.padding(horizontal = 10.dp, vertical = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LoraStill(Modifier.size(26.dp, 18.dp), action = LoraAction.WAVE)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f, fill = false)) {
                    Text(tr("Von Lora übergeben"), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    Text(line, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (!m.instructions.isNullOrBlank()) {
                    Icon(if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, contentDescription = tr(if (open) "Einklappen" else "Was Lora schon weiß"), modifier = Modifier.padding(start = 6.dp).size(16.dp))
                }
            }
            if (open) {
                Text(
                    m.instructions.orEmpty().substringAfter("wiederholen): ").trim(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp, start = 34.dp),
                )
            }
        }
    }
}

/** Above the input while the user sends a question Lora prepared (Übergaben erst zeigen). */
@Composable
fun LoraPreparedBanner(line: String, onRemove: () -> Unit) {
    val brand = LocalChatLook.current.brand
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = brand.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, brand.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
    ) {
        Row(Modifier.padding(start = 10.dp, end = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            LoraStill(Modifier.size(24.dp, 17.dp), action = LoraAction.WAVE)
            Spacer(Modifier.width(8.dp))
            Text(
                tr("Von Lora vorbereitet – ihr Wissen geht mit") + if (line.isNotBlank()) " · $line" else "",
                style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onRemove, modifier = Modifier.size(36.dp)) { Icon(Icons.Outlined.Close, contentDescription = tr("Ohne Loras Wissen senden"), modifier = Modifier.size(16.dp)) }
        }
    }
}

/**
 * While typing (AP-35): when the text clearly means a place of the app („dunkles Design“), Lora shows it above the
 * input – a tap opens it, ✕ hides the hint for this text. Never for real questions; found on the device.
 */
@Composable
fun LoraTypingHint(c: AppContainer, input: String, onUsed: () -> Unit) {
    val s by c.settings.settings.collectAsStateWithLifecycle()
    if (!s.loraTyping || !LoraHelper.available(s)) return
    var hint by remember { mutableStateOf<io.github.thepytales.llmhub.data.AppTarget?>(null) }
    var dismissed by remember { mutableStateOf("") }
    LaunchedEffect(input) {
        hint = null
        if (input.isBlank() || input.length > 60 || input == dismissed) return@LaunchedEffect
        delay(450)
        hint = withContext(Dispatchers.Default) {
            LoraSense.typingHint(input, AppMap.build(TemplateLibrary.all(c.templates.items.value), IS_ANDROID))
        }
    }
    AnimatedVisibility(hint != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
        val t = hint ?: return@AnimatedVisibility
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = LocalChatLook.current.card,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
        ) {
            Row(
                Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClickLabel = tr("Öffnen")) {
                    onUsed()
                    openTarget(t, LoraHost.go)
                }.padding(start = 10.dp, end = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LoraStill(Modifier.size(24.dp, 17.dp), action = LoraAction.LOOK)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
                    Text(tr("Lora: Meinst du") + " „${t.title}“?", style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(t.path, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = { dismissed = input; hint = null }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Outlined.Close, contentDescription = tr("Hinweis ausblenden"), modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

/**
 * Under the last answer (AP-35): at most three next steps Lora noticed on the device – deadlines for the calendar, a
 * table as Excel, literature for the collection, a long text as Word document. Each runs on the device in one tap.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LoraNextSteps(c: AppContainer, answer: String, chatId: String?) {
    val s by c.settings.settings.collectAsStateWithLifecycle()
    if (!s.loraNextSteps || !LoraHelper.available(s)) return
    val steps = remember(answer) { LoraSense.nextSteps(answer) }
    if (steps.isEmpty()) return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state = remember(answer) { mutableStateMapOf<Int, String>() }
    fun conv() = chatId?.let { c.chats.get(it) }
    fun ctx() = RepoContext(mutableListOf(), folderId = conv()?.folderId, chatId = chatId)
    fun run(i: Int, block: suspend () -> File?) {
        if (state[i] == "busy") return
        state[i] = "busy"
        scope.launch {
            runCatching { block() }
                .onSuccess { file -> state[i] = "done"; file?.let { openPrimary(context, it) } }
                .onFailure { e -> state.remove(i); Notices.error(tr("Das hat nicht geklappt:") + " " + (e.message ?: e.javaClass.simpleName)) }
        }
    }
    Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.Top) {
        LoraStill(Modifier.padding(top = 6.dp).size(24.dp, 17.dp), action = LoraAction.EUREKA)
        Spacer(Modifier.width(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            steps.forEachIndexed { i, step ->
                val icon: ImageVector = when (step) {
                    is LoraSense.NextStep.Calendar -> Icons.Outlined.CalendarMonth
                    is LoraSense.NextStep.Excel -> Icons.Outlined.TableChart
                    is LoraSense.NextStep.Literature -> Icons.AutoMirrored.Outlined.LibraryBooks
                    LoraSense.NextStep.Word -> Icons.Outlined.Description
                }
                NextStepPill(icon, tr(LoraSense.label(step)), state[i]) {
                    when (step) {
                        is LoraSense.NextStep.Calendar -> run(i) {
                            withContext(Dispatchers.IO) {
                                c.loraCalendar(step.deadlines.map { it.title to it.date }, note = tr("Aus dem Chat") + " „${conv()?.title ?: "Lora"}“ – " + tr("von Lora eingetragen."))
                            }
                        }
                        is LoraSense.NextStep.Excel -> run(i) {
                            val out = withContext(Dispatchers.IO) { c.documentTools.run("create_document", buildJsonObject {
                                put("title", conv()?.title?.take(60) ?: "Tabelle")
                                put("content", step.table)
                                put("format", "xlsx")
                            }, ctx()) }
                            out.file?.let(::File) ?: error(out.text.take(160))
                        }
                        LoraSense.NextStep.Word -> run(i) {
                            val out = withContext(Dispatchers.IO) { c.documentTools.run("create_document", buildJsonObject {
                                put("title", conv()?.title?.take(60) ?: "Antwort")
                                put("content", answer)
                                put("format", "docx")
                            }, ctx()) }
                            out.file?.let(::File) ?: error(out.text.take(160))
                        }
                        is LoraSense.NextStep.Literature -> run(i) {
                            val sources = c.researchTools.sources(null)
                            val refs = step.dois.mapNotNull { d -> runCatching { sources.crossrefDoi(d) }.getOrNull() }
                            if (refs.isEmpty()) error(tr("Zu den DOIs wurde nichts gefunden."))
                            c.literature.addAll(refs)
                            Notices.info(if (refs.size == 1) tr("1 Titel in der Literatur gespeichert.") else "${refs.size} " + tr("Titel in der Literatur gespeichert."))
                            null
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NextStepPill(icon: ImageVector, label: String, state: String?, onClick: () -> Unit) {
    val brand = LocalChatLook.current.brand
    Surface(
        shape = RoundedCornerShape(50),
        color = if (state == "done") brand.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, if (state == "done") brand.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.clip(RoundedCornerShape(50)).clickable(enabled = state != "busy", onClick = onClick),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(16.dp), contentAlignment = Alignment.Center) {
                if (state == "busy") CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                else Icon(if (state == "done") Icons.Outlined.Check else icon, contentDescription = null, tint = brand, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
    }
}

/**
 * Lora's signature next to the model under an answer: still, but for a moment she cheers when the answer has just
 * been finished ([justDone]).
 */
@Composable
fun LoraSignature(justDone: Boolean) {
    if (justDone && !LoraMotion.calm) {
        val director = rememberLoraDirector(sequence = listOf(LoraAction.CHEER), start = LoraAction.CHEER)
        LoraFigure(Modifier.padding(end = 4.dp).size(30.dp, 21.dp), label = tr("Lora freut sich"), director = director)
    } else {
        LoraStill(Modifier.padding(end = 4.dp).size(26.dp, 18.dp).alpha(0.95f))
    }
}

/** True for a few seconds after [working] turned from true to false (an answer was just finished). */
@Composable
fun rememberJustFinished(working: Boolean, forMs: Long = 3_200): Boolean {
    var seen by remember { mutableStateOf(working) }
    var done by remember { mutableStateOf(false) }
    LaunchedEffect(working) {
        if (working) {
            seen = true
            done = false
        } else if (seen) {
            seen = false
            done = true
            delay(forMs)
            done = false
        }
    }
    return done
}
