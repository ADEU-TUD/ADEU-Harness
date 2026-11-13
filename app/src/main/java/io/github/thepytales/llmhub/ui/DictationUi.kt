package io.github.thepytales.llmhub.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.audio.Dictation
import io.github.thepytales.llmhub.audio.Recorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The state of one Diktat (AP-65): recording, recognizing, and the button's action. */
class DictationControl {
    var recording by mutableStateOf(false)
    var busy by mutableStateOf(false)
    var toggle: () -> Unit = {}
}

/**
 * Diktat for a text field or document (AP-65): the first tap starts recording (asking for the microphone on Android), the
 * second stops it; the recording is recognized on the device (Dictation.recognize) and [onText] gets the text with its
 * spoken punctuation turned into marks.
 */
@Composable
fun rememberDictation(c: AppContainer, onText: (String) -> Unit): DictationControl {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val recorder = remember { Recorder(context.applicationContext) }
    val control = remember { DictationControl() }
    val latest by rememberUpdatedState(onText)
    val start = rememberMicPermission {
        runCatching { recorder.start() }
            .onSuccess { control.recording = true }
            .onFailure { Notices.error(tr("Mikrofon nicht verfügbar: {0}", it.message ?: it.javaClass.simpleName)) }
    }
    DisposableEffect(recorder) { onDispose { if (control.recording) runCatching { recorder.stop() } } }
    control.toggle = {
        if (control.recording) {
            control.recording = false
            val file = runCatching { recorder.stop() }.getOrNull()
            if (file == null) Notices.error(tr("Die Aufnahme war leer."))
            else {
                control.busy = true
                scope.launch {
                    runCatching { withContext(Dispatchers.IO) { Dictation.recognize(c, file) } }
                        .onSuccess { (text, _) ->
                            val punctuated = Dictation.punctuate(text)
                            if (punctuated.isBlank()) Notices.info(tr("Keine Sprache erkannt.")) else latest(punctuated)
                        }
                        .onFailure { Notices.error(tr("Diktat: {0}", it.message ?: it.javaClass.simpleName)) }
                    control.busy = false
                }
            }
        } else if (!control.busy) start()
    }
    return control
}

/** The microphone of a toolbar: red stop while recording, a spinner while recognizing. */
@Composable
fun DictateIcon(d: DictationControl) {
    IconButton(
        onClick = d.toggle, enabled = !d.busy,
        modifier = Modifier.size(40.dp).then(if (d.recording) Modifier.background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(10.dp)) else Modifier),
    ) {
        Box(contentAlignment = Alignment.Center) {
            when {
                d.busy -> CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                d.recording -> Icon(Icons.Outlined.StopCircle, contentDescription = tr("Diktat beenden"), tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                else -> Icon(Icons.Outlined.Mic, contentDescription = tr("Diktieren („Punkt“, „Komma“, „neuer Absatz“ sprechen)"), modifier = Modifier.size(20.dp))
            }
        }
    }
}
