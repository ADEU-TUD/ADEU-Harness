package io.github.thepytales.llmhub.ssh

import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Protocol of the Server-Terminal (AP-118): one text file per session and day in `ssh/protokoll/` of the data folder –
 * time, what happened (connected, step proposed, approved, skipped, edited, ended with exit code) and the shortened
 * output. Passwords, passphrases, private keys and tokens are removed before writing ([ServerMask] without the
 * restorable placeholders: on this device host and user may stay readable).
 */
class ServerLog(private val dir: File, private val profile: SshProfile, private val secrets: () -> Collection<String>, private val now: () -> LocalDateTime = LocalDateTime::now) {
    val file: File get() = File(dir, "${now().toLocalDate()}-${safe(profile.label)}.log")

    private fun clean(text: String): String = ServerMask.secretsOnly(text, secrets())

    @Synchronized
    fun write(event: String, detail: String = "") {
        dir.mkdirs()
        val time = now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
        val body = clean(detail).lines().dropLastWhile { it.isBlank() }.let { lines ->
            if (lines.size > MAX_LINES) lines.take(MAX_LINES / 2) + "… (${lines.size - MAX_LINES} Zeilen gekürzt) …" + lines.takeLast(MAX_LINES / 2) else lines
        }
        val text = buildString {
            append(time).append("  ").append(clean(event)).append('\n')
            body.forEach { append("          ").append(it).append('\n') }
        }
        file.appendText(text)
    }

    fun read(): String = file.takeIf { it.isFile }?.readText().orEmpty()

    companion object {
        const val MAX_LINES = 60
        private fun safe(s: String) = s.replace(Regex("[^A-Za-z0-9._@-]"), "_").take(60)
    }
}
