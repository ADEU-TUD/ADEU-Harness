package io.github.thepytales.llmhub.net

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/**
 * Local MCP servers (AP-23, Windows): the app starts the program (e.g. `npx -y @modelcontextprotocol/server-filesystem C:\Daten`)
 * and talks to it over stdin/stdout, one JSON-RPC message per line. The program stops with the app or when the server is
 * switched off.
 */
object McpStdio {
    const val SUPPORTED = true

    fun start(command: String, args: List<String>, env: Map<String, String>, workDir: String?): McpTransport = StdioTransport(command, args, env, workDir)

    /** Splits „npx -y paket "C:\Mit Leerzeichen"“ into its parts (quotes keep spaces). */
    fun split(line: String): List<String> {
        val out = mutableListOf<String>()
        val cur = StringBuilder()
        var quote: Char? = null
        var any = false
        for (ch in line) {
            when {
                quote != null && ch == quote -> quote = null
                quote == null && (ch == '"' || ch == '\'') -> { quote = ch; any = true }
                quote == null && ch.isWhitespace() -> { if (cur.isNotEmpty() || any) out += cur.toString(); cur.clear(); any = false }
                else -> cur.append(ch)
            }
        }
        if (cur.isNotEmpty() || any) out += cur.toString()
        return out
    }

    private class StdioTransport(command: String, args: List<String>, env: Map<String, String>, workDir: String?) : McpTransport {
        private val process: Process
        private val pending = ConcurrentHashMap<String, CompletableDeferred<JsonObject>>()
        private val stderr = StringBuffer()

        init {
            val windows = System.getProperty("os.name").orEmpty().lowercase().contains("win")
            // npx, npm, uvx … are .cmd files on Windows and need the command interpreter to start.
            val cmd = if (windows && !command.lowercase().endsWith(".exe") && !File(command).isFile) listOf("cmd.exe", "/c", command) + args else listOf(command) + args
            process = try {
                ProcessBuilder(cmd).apply {
                    environment().putAll(env)
                    workDir?.takeIf { it.isNotBlank() }?.let { directory(File(it)) }
                }.start()
            } catch (e: IOException) {
                throw IOException("Das Programm „$command“ ließ sich nicht starten: ${e.message}")
            }
            Thread({
                process.inputStream.bufferedReader(Charsets.UTF_8).useLines { lines ->
                    lines.forEach { line ->
                        val o = runCatching { JSON.parseToJsonElement(line).jsonObject }.getOrNull() ?: return@forEach
                        val id = o["id"]?.toString() ?: return@forEach
                        if (o.containsKey("result") || o.containsKey("error")) pending.remove(id)?.complete(o)
                    }
                }
                pending.values.forEach { it.completeExceptionally(IOException("Der MCP-Server wurde beendet." + lastError())) }
            }, "mcp-stdout").apply { isDaemon = true }.start()
            Thread({
                runCatching { process.errorStream.bufferedReader().forEachLine { l -> if (stderr.length < 8000) stderr.append(l).append('\n') } }
            }, "mcp-stderr").apply { isDaemon = true }.start()
        }

        private fun lastError() = stderr.toString().trim().takeLast(400).let { if (it.isEmpty()) "" else " Meldung: $it" }

        override suspend fun send(message: JsonObject, expectResponse: Boolean): JsonObject? {
            if (!process.isAlive) throw IOException("Der MCP-Server läuft nicht mehr." + lastError())
            val id = message["id"]?.toString()
            val answer = if (expectResponse && id != null) CompletableDeferred<JsonObject>().also { pending[id] = it } else null
            withContext(Dispatchers.IO) {
                synchronized(process) {
                    process.outputStream.write((message.toString() + "\n").toByteArray(Charsets.UTF_8))
                    process.outputStream.flush()
                }
            }
            answer ?: return null
            return try {
                withTimeout(120_000) { answer.await() }
            } catch (e: TimeoutCancellationException) {
                pending.remove(id)
                throw IOException("Der MCP-Server hat nicht innerhalb von zwei Minuten geantwortet.")
            }
        }

        override fun close() {
            runCatching { process.outputStream.close() }
            process.destroy()
            if (!runCatching { process.waitFor(2, java.util.concurrent.TimeUnit.SECONDS) }.getOrDefault(false)) process.destroyForcibly()
        }
    }
}
