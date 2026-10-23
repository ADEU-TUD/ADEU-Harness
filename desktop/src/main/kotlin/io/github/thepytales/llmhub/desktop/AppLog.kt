package io.github.thepytales.llmhub

import io.github.thepytales.llmhub.desktop.Paths
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Small log file in the data folder (log.txt), useful when something goes wrong on a colleague's PC. */
object AppLog {
    private val file by lazy { File(Paths.data, "log.txt") }
    private val stamp = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    @Synchronized
    fun write(level: String, tag: String, msg: String, error: Throwable?): Int {
        val line = "${LocalDateTime.now().format(stamp)} $level/$tag: $msg" + (error?.let { "\n" + it.stackTraceToString() } ?: "")
        System.err.println(line)
        runCatching {
            if (file.length() > 1_000_000) file.renameTo(File(file.parentFile, "log.old.txt"))
            file.appendText(line + "\n")
        }
        return 0
    }

    fun i(msg: String) = write("I", "App", msg, null)
    fun e(msg: String, error: Throwable? = null) = write("E", "App", msg, error)
}
