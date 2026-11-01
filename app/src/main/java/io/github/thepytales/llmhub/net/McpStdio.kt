package io.github.thepytales.llmhub.net

import java.io.IOException

/** Local MCP servers (a program started by the app, stdio) exist on Windows only; Windows has its own version of this file. */
object McpStdio {
    const val SUPPORTED = false

    @Suppress("UNUSED_PARAMETER")
    fun start(command: String, args: List<String>, env: Map<String, String>, workDir: String?): McpTransport =
        throw IOException("Lokale MCP-Server gibt es nur in der Windows-Version.")

    /** Splits „npx -y paket "C:\\Mit Leerzeichen"“ into its parts (quotes keep spaces). */
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
}
