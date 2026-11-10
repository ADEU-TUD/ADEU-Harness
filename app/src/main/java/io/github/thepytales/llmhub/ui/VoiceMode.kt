package io.github.thepytales.llmhub.ui

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MicOff
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.audio.Recorder
import io.github.thepytales.llmhub.data.Privacy
import io.github.thepytales.llmhub.data.thread
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.PI
import kotlin.math.sin

/** Where the conversation is: listening, understanding what was said, the agent working, reading the answer aloud. */
enum class VoicePhase(val label: String) {
    LISTENING(trLater("Ich höre zu …")),
    PAUSED(trLater("Pausiert – tippen Sie auf „Weiter“")),
    UNDERSTANDING(trLater("Verstehe …")),
    WORKING(trLater("Lora ist dran …")),
    SPEAKING(trLater("Antwort wird vorgelesen …")),
}

/**
 * Decides from the microphone level when the user has finished a sentence: speech starts above a threshold that
 * adapts to the background noise, and a pause of [silenceMs] after speech ends the turn. Pure logic, so it can be tested.
 */
class SpeechEndDetector(private val silenceMs: Long = 800, private val maxMs: Long = 60_000) {
    private var noise = 0.02f
    private var heardMs = 0L
    private var silentMs = 0L
    private var totalMs = 0L

    /** Speech was heard in this turn. */
    val heard get() = heardMs >= 250

    /** Threshold above which a sample counts as speech. */
    val threshold get() = (noise * 3f).coerceIn(0.05f, 0.4f)

    /** Feeds one level sample covering [stepMs]; true when the turn is over (pause after speech, or too long). */
    fun feed(level: Float, stepMs: Long): Boolean {
        totalMs += stepMs
        if (level > threshold) {
            heardMs += stepMs
            silentMs = 0
        } else {
            // Background noise is learned only where nobody speaks.
            noise = noise * 0.95f + level * 0.05f
            if (heard) silentMs += stepMs
        }
        return (heard && silentMs >= silenceMs) || totalMs >= maxMs
    }
}

/** Sent along (hidden) with what was said in the Sprachmodus, so the answer suits being read aloud. */
private const val VOICE_INSTRUCTIONS =
    "Sprachmodus: Die Antwort wird vorgelesen. Antworte kurz und in gesprochener Sprache, ohne Markdown, Tabellen, " +
        "Listenzeichen oder Code. Nutze Werkzeuge nur, wenn die Frage es wirklich erfordert."

/**
 * Hands-free conversation (Sprachmodus): speak, the answer is read aloud, then the app listens again – until the user
 * ends it. Uses the chat's normal way (memory, tools, privacy level of the project): what was said appears in the chat
 * as a message. In projects that keep data on the device, the answer is read with the device's voice.
 */
@Composable
fun VoiceModeDialog(c: AppContainer, vm: ChatViewModel, platform: ChatPlatform, onClose: () -> Unit) {
    val context = LocalContext.current
    val recorder = remember { Recorder(context.applicationContext) }
    var phase by remember { mutableStateOf(VoicePhase.LISTENING) }
    var paused by remember { mutableStateOf(false) }
    var heardText by remember { mutableStateOf("") }
    var answerText by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var level by remember { mutableFloatStateOf(0f) }
    /** Skips the rest of the reading and listens again. */
    var interrupt by remember { mutableStateOf(0) }
    val conv by vm.state.collectAsStateWithLifecycle()
    val hideLora = c.settings.settings.collectAsStateWithLifecycle().value.hideLora
    val haptics = LocalHapticFeedback.current

    DisposableEffect(Unit) {
        platform.keepScreenOn(true)
        onDispose {
            platform.keepScreenOn(false)
            runCatching { recorder.stop() }
            c.speaker.stop()
        }
    }

    LaunchedEffect(paused, interrupt) {
        if (paused) {
            phase = VoicePhase.PAUSED
            return@LaunchedEffect
        }
        // The voice on this device (AP-111) loads while the person speaks, so the answer starts without waiting.
        c.localVoice.preload()
        try {
        while (true) {
            // 1. Listen until a pause after speech.
            phase = VoicePhase.LISTENING
            error = null
            try {
                recorder.start()
            } catch (e: Exception) {
                error = tr("Mikrofon nicht verfügbar: {0}", e.message ?: e.javaClass.simpleName)
                paused = true
                return@LaunchedEffect
            }
            val detector = SpeechEndDetector()
            while (true) {
                delay(100)
                val l = recorder.level()
                level = l
                if (detector.feed(l, 100)) break
            }
            val file = recorder.stop()
            val spokeAt = System.currentTimeMillis()
            level = 0f
            if (!detector.heard || file == null) continue

            // 2. Speech to text.
            phase = VoicePhase.UNDERSTANDING
            val text = try {
                vm.recognize(file).trim()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = tr("Spracherkennung fehlgeschlagen: {0}", e.message)
                delay(1500)
                continue
            }
            if (text.isBlank()) continue
            heardText = text
            answerText = ""

            val recognizedAt = System.currentTimeMillis()

            // 3. The agent answers in the chat as usual; reading starts with its first finished sentence.
            phase = VoicePhase.WORKING
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            vm.send(text, emptyList(), emptyList(), instructions = VOICE_INSTRUCTIONS, voice = true)
            withTimeoutOrNull(5_000) { vm.running.first { it } }
            var firstTokenAt = 0L
            var firstSoundAt = 0L
            fun answerSoFar(): String {
                val thread = vm.state.value?.thread().orEmpty()
                val lastUser = thread.indexOfLast { it.role == "user" }
                return thread.drop(lastUser + 1).filter { it.role == "assistant" && !it.error }
                    .joinToString("\n\n") { splitThinking(it.content).second.trim() }.trim()
            }
            val id = "voice-" + System.nanoTime()
            // Data that stays here, or „Nur lokal“ (AP-71/AP-111): read with a voice on this device.
            val deviceOnly = !(vm.state.value?.let { Privacy.level(c, it) } ?: Privacy.level(c, null as String?)).allowsUniServer ||
                c.settings.current.loraPlace == "LOCAL"
            c.speaker.speakStream(id, deviceOnly, onFirstSound = {
                firstSoundAt = System.currentTimeMillis()
                phase = VoicePhase.SPEAKING
            }) {
                val answer = answerSoFar()
                if (answer.isNotEmpty() && firstTokenAt == 0L) firstTokenAt = System.currentTimeMillis()
                answerText = answer
                answer to !vm.running.value
            }
            withTimeoutOrNull(5_000) { c.speaker.playingId.first { it == id } }
            c.speaker.playingId.first { it != id }
            vm.running.first { !it }

            // 4. Waiting times in the agent protocol; without an answer, say why.
            fun s(from: Long, to: Long) = if (to == 0L) "–" else String.format(java.util.Locale.GERMANY, "%.1f s", (to - from) / 1000.0)
            if (answerSoFar().isBlank()) {
                val thread = vm.state.value?.thread().orEmpty()
                error = thread.lastOrNull()?.takeIf { it.role == "assistant" && it.error }?.content?.take(200) ?: tr("Keine Antwort erhalten.")
                delay(1500)
                continue
            }
            vm.noteOnLastAnswer(
                "Sprachmodus ab Sprechende: erkannt nach ${s(spokeAt, recognizedAt)}, erstes Wort der Antwort nach " +
                    "${s(spokeAt, firstTokenAt)}, erster Ton nach ${s(spokeAt, firstSoundAt)}",
            )
        }
        } finally {
            runCatching { recorder.stop() }
        }
    }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(tr("Sprachmodus"), style = MaterialTheme.typography.titleLarge)
                Text(
                    conv?.title?.takeIf { (conv?.messages?.isNotEmpty() == true) } ?: tr("Sprechen Sie einfach los."),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(8.dp))
                Box(Modifier.size(220.dp), contentAlignment = Alignment.Center) {
                    if (phase == VoicePhase.WORKING && !hideLora) LoraFigure(Modifier.size(width = 190.dp, height = 133.dp), label = tr("Lora bei der Arbeit"))
                    else VoiceOrb(phase, level, Modifier.fillMaxSize())
                    // Listening: Lora nods along at the edge of the orb (AP-36).
                    if (phase == VoicePhase.LISTENING && !hideLora) {
                        LoraFigure(
                            Modifier.align(Alignment.BottomEnd).size(64.dp, 45.dp), label = tr("Lora hört zu"),
                            director = rememberLoraDirector(sequence = listOf(LoraAction.NOD, LoraAction.IDLE), start = LoraAction.NOD),
                        )
                    }
                }
                Text(
                    tr(if (phase == VoicePhase.WORKING && hideLora) "Arbeitet an der Antwort …" else phase.label),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center) }
                if (heardText.isNotBlank()) {
                    Text(
                        "„$heardText“",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (answerText.isNotBlank() && phase == VoicePhase.SPEAKING) {
                    Text(
                        speechPreview(answerText),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (phase == VoicePhase.SPEAKING) {
                        FilledTonalButton(onClick = { c.speaker.stop(); interrupt++ }) {
                            Icon(Icons.Outlined.SkipNext, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.size(6.dp))
                            Text(tr("Unterbrechen"))
                        }
                    } else {
                        FilledTonalButton(
                            enabled = phase != VoicePhase.WORKING && phase != VoicePhase.UNDERSTANDING,
                            onClick = { paused = !paused },
                        ) {
                            Icon(if (paused) Icons.Outlined.Mic else Icons.Outlined.MicOff, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.size(6.dp))
                            Text(if (paused) tr("Weiter") else tr("Pause"))
                        }
                    }
                    OutlinedButton(onClick = { if (phase == VoicePhase.WORKING) vm.stop(); onClose() }) {
                        Icon(Icons.Outlined.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text(tr("Beenden"))
                    }
                }
                Text(
                    tr("Alles Gesagte steht danach auch im Chat."),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** The first sentences of the answer as plain text (without Markdown marks). */
private fun speechPreview(markdown: String): String =
    markdown.lines().map { it.trim().trimStart('#', '-', '*', '>', ' ') }.filter { it.isNotBlank() && !it.startsWith("```") }
        .joinToString(" ").replace("**", "").take(320)

/** A calm circle: breathes with the voice while listening, slow waves while reading aloud. */
@Composable
private fun VoiceOrb(phase: VoicePhase, level: Float, modifier: Modifier) {
    val brand = LocalChatLook.current.brand
    val soft = MaterialTheme.colorScheme.primaryContainer
    val ring = MaterialTheme.colorScheme.outlineVariant
    val time by produceState(0L) {
        val start = withFrameMillis { it }
        while (true) withFrameMillis { value = it - start }
    }
    val loud by animateFloatAsState((level * 4f).coerceIn(0f, 1f), tween(120), label = "level")
    Canvas(modifier) {
        val t = time / 1000f
        val center = Offset(size.width / 2, size.height / 2)
        val base = size.minDimension * 0.28f
        when (phase) {
            VoicePhase.LISTENING -> {
                drawCircle(soft, base * (1.25f + 0.35f * loud), center)
                drawCircle(brand, base * (1f + 0.12f * loud + 0.03f * sin(t * 2.4f)), center)
            }
            VoicePhase.SPEAKING -> {
                for (i in 0..2) {
                    val k = ((t * 0.6f + i / 3f) % 1f)
                    drawCircle(brand.copy(alpha = 0.35f * (1 - k)), base * (1f + 0.7f * k), center, style = Stroke(3.dp.toPx()))
                }
                drawCircle(brand, base * (1f + 0.05f * sin(t * 5f)), center)
            }
            VoicePhase.UNDERSTANDING -> {
                drawCircle(ring, base * 1.2f, center, style = Stroke(2.dp.toPx()))
                for (i in 0..2) {
                    val a = t * 2.2f + i * (2 * PI / 3).toFloat()
                    drawCircle(brand, base * 0.16f, Offset(center.x + base * 0.7f * kotlin.math.cos(a), center.y + base * 0.7f * sin(a)))
                }
            }
            else -> {
                drawCircle(ring, base, center, style = Stroke(2.dp.toPx()))
                drawCircle(soft, base * 0.85f, center)
            }
        }
    }
}
