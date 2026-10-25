package io.github.thepytales.llmhub.data

import io.github.thepytales.llmhub.logFailure
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalDate

/** Requests and tokens of one model on one day. */
@Serializable
data class UsageEntry(
    /** ISO date (yyyy-MM-dd). */
    val day: String,
    val model: String,
    /** chat, image, tts or stt. */
    val kind: String = "chat",
    val requests: Int = 0,
    val promptTokens: Long = 0,
    val completionTokens: Long = 0,
    /** Requests whose token numbers were estimated because the server sent none. */
    val estimated: Int = 0,
) {
    val totalTokens get() = promptTokens + completionTokens
}

/** Counts requests and tokens per day locally on the device. */
class UsageStore(dir: File) {
    private val file = File(dir, "usage.json")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var saveJob: Job? = null

    private val _entries = MutableStateFlow(load())
    val entries: StateFlow<List<UsageEntry>> = _entries.asStateFlow()

    private fun load(): List<UsageEntry> = runCatching {
        json.decodeFromString(ListSerializer(UsageEntry.serializer()), file.readText())
    }.logFailure("Verbrauch lesen").getOrDefault(emptyList())

    fun record(model: String, kind: String, promptTokens: Long, completionTokens: Long, estimated: Boolean) {
        val day = LocalDate.now().toString()
        val oldest = LocalDate.now().minusDays(400).toString()
        _entries.update { list ->
            val existing = list.firstOrNull { it.day == day && it.model == model && it.kind == kind }
            val next = (existing ?: UsageEntry(day, model, kind)).let {
                it.copy(
                    requests = it.requests + 1,
                    promptTokens = it.promptTokens + promptTokens,
                    completionTokens = it.completionTokens + completionTokens,
                    estimated = it.estimated + if (estimated) 1 else 0,
                )
            }
            (list.filterNot { it === existing } + next).filter { it.day >= oldest }
        }
        scheduleSave()
    }

    fun clear() {
        _entries.value = emptyList()
        scheduleSave()
    }

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(1000)
            runCatching { AtomicFiles.write(file, json.encodeToString(ListSerializer(UsageEntry.serializer()), _entries.value)) }.logFailure("Verbrauch speichern")
        }
    }
}
