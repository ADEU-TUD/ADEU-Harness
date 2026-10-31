package io.github.thepytales.llmhub.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.lang.management.ManagementFactory

/**
 * Working memory of the app (AP-04): what the Java heap uses, what it holds from the system, its upper limit and – where
 * the system tells – the whole process. Measuring points (start, after an hour, after a big PDF, after a transcription)
 * show under Einstellungen › Arbeitsspeicher whether the memory goes down again after big tasks.
 */
object MemoryMeter {
    /** One measurement; [label] says when it was taken. */
    data class Sample(val label: String, val time: Long, val heapUsed: Long, val heapCommitted: Long, val heapMax: Long, val nonHeap: Long, val process: Long?)

    /** Memory of the whole process in bytes (Windows: working set, see desktop/ProcessMemory); null when unknown. */
    var processBytes: () -> Long? = ::procStatus

    private val _samples = MutableStateFlow<List<Sample>>(emptyList())
    /** The latest measuring points, oldest first (at most [MAX_SAMPLES]). */
    val samples: StateFlow<List<Sample>> = _samples.asStateFlow()

    fun now(label: String = "Jetzt"): Sample {
        val rt = Runtime.getRuntime()
        val nonHeap = runCatching { ManagementFactory.getMemoryMXBean().nonHeapMemoryUsage.used }.getOrDefault(0L)
        return Sample(label, System.currentTimeMillis(), rt.totalMemory() - rt.freeMemory(), rt.totalMemory(), rt.maxMemory(), nonHeap, runCatching { processBytes() }.getOrNull())
    }

    /** Takes a measuring point and keeps it (also in the log, for reports). */
    fun mark(label: String): Sample {
        val s = now(label)
        _samples.value = (_samples.value + s).takeLast(MAX_SAMPLES)
        return s
    }

    /** Frees what is no longer used and gives free heap back to the system (the JVM flags of AP-04 allow that). */
    fun release(): Sample {
        System.gc()
        return mark("Nach „Speicher freigeben“")
    }

    /**
     * Background care while the app runs: the measuring point after one hour, and loaded speech recognition models that
     * nobody used for [IDLE_MODEL_MS] are released ([evictIdle], set by the app).
     */
    fun start(scope: CoroutineScope, evictIdle: (Long) -> Unit) {
        scope.launch {
            var minutes = 0
            while (true) {
                delay(60_000)
                minutes++
                runCatching { evictIdle(IDLE_MODEL_MS) }
                if (minutes == 60) mark("Nach einer Stunde")
            }
        }
    }

    /** Linux and Android: resident memory from /proc (VmRSS). */
    private fun procStatus(): Long? = runCatching {
        File("/proc/self/status").takeIf { it.canRead() }?.readLines()?.firstOrNull { it.startsWith("VmRSS:") }
            ?.substringAfter(':')?.trim()?.substringBefore(' ')?.toLongOrNull()?.times(1024)
    }.getOrNull()

    private const val MAX_SAMPLES = 30
    const val IDLE_MODEL_MS = 3 * 60_000L
}
