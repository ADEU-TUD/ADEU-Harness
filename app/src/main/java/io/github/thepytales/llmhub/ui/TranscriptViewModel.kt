package io.github.thepytales.llmhub.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.transcribe.Transcript
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * State of an open transcript: loading, changes with "Rückgängig", deleting. Survives turning the device and
 * switching to dark mode (the screen itself only keeps which dialog is open).
 */
class TranscriptViewModel(private val c: AppContainer, val id: String) : ViewModel() {
    private val t = c.transcription

    private val _transcript = MutableStateFlow<Transcript?>(null)
    val transcript: StateFlow<Transcript?> = _transcript.asStateFlow()

    private val _loaded = MutableStateFlow(false)
    /** false while loading; true and [transcript] null if it no longer exists. */
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    private val _undo = MutableStateFlow<Transcript?>(null)
    /** Version before the last change ("Rückgängig"). */
    val undo: StateFlow<Transcript?> = _undo.asStateFlow()

    init {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { t.engine() }
            _transcript.value = t.load(id)
            _loaded.value = true
        }
        // Word times measured in the background: show them as soon as they are saved
        viewModelScope.launch {
            t.finished.collect { f -> if (f.transcriptId == id) t.load(id)?.let { _transcript.value = it } }
        }
    }

    /** Saves a change; the first change keeps the version as transcribed ("Änderungen verwerfen"). */
    fun update(next: Transcript, undoable: Boolean = true) {
        val before = _transcript.value ?: return
        if (undoable) _undo.value = before
        val kept = if (before.hasOriginal || next.hasOriginal) next else next.copy(raw = before.withOriginal().raw)
        _transcript.value = kept
        viewModelScope.launch { t.save(kept) }
    }

    fun undoLast() {
        _undo.value?.let { update(it, undoable = false) }
        _undo.value = null
    }

    /** Moves the transcript into the trash; the banner offers "Rückgängig". */
    fun delete(onDone: () -> Unit) {
        val title = _transcript.value?.title ?: "Transkript"
        viewModelScope.launch {
            t.delete(id)
            Notices.undo("„$title“ gelöscht", restore = { t.restoreLater(id) })
            onDone()
        }
    }
}
