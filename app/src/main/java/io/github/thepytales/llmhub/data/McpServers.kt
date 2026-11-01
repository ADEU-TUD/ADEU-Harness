package io.github.thepytales.llmhub.data

import io.github.thepytales.llmhub.net.McpOAuthState
import kotlinx.serialization.Serializable
import java.util.UUID
import io.github.thepytales.llmhub.ui.trLater

/** What happens when the model wants to use a tool of a MCP server (Einstellungen › Verbindungen). */
enum class McpPolicy(val label: String, val description: String) {
    ALLOW(trLater("Erlauben"), trLater("ohne Rückfrage")),
    ASK(trLater("Fragen"), trLater("über die Freigaben")),
    BLOCK(trLater("Sperren"), trLater("das Modell sieht es nicht")),
    ;

    companion object {
        fun of(name: String?) = entries.firstOrNull { it.name == name } ?: ASK
    }
}

/** A tool of a MCP server as last listed (the model sees it without asking the server first). */
@Serializable
data class McpToolInfo(
    val name: String,
    val description: String = "",
    /** JSON schema of the arguments, as text. */
    val schema: String = "{\"type\":\"object\"}",
    val readOnly: Boolean = false,
)

/**
 * A MCP server the user added (AP-23): a remote server (Streamable HTTP, with a token or OAuth) on both platforms or a
 * local program (stdio) on Windows. Kept in the encrypted settings (tokens), on Windows only on this PC.
 */
@Serializable
data class McpServer(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    /** "http" or "stdio". */
    val transport: String = HTTP,
    val url: String = "",
    /** stdio: the program and its arguments as one line, e.g. `npx -y @modelcontextprotocol/server-filesystem C:\Daten`. */
    val command: String = "",
    /** stdio: environment variables, one `NAME=Wert` per line. */
    val env: String = "",
    /** Fixed token (Authorization: Bearer …); empty when the server uses OAuth or needs nothing. */
    val token: String = "",
    /** Further header for servers that take their key in it, e.g. `X-API-Key`; empty = Authorization: Bearer. */
    val tokenHeader: String = "",
    /** Client id given by the operator (for OAuth servers without dynamic registration). */
    val clientId: String = "",
    val oauth: McpOAuthState? = null,
    val enabled: Boolean = true,
    /**
     * The server runs at the TU Dresden or on this PC: its tools may also be used in projects with the level
     * „Nur TU Dresden“. Other servers only in projects with the standard level.
     */
    val internal: Boolean = false,
    val tools: List<McpToolInfo> = emptyList(),
    /** Tool name → McpPolicy name; tools without an entry get [defaultPolicy]. */
    val policies: Map<String, String> = emptyMap(),
    val defaultPolicy: String = McpPolicy.ASK.name,
    val lastError: String? = null,
    val checkedAt: Long = 0,
    /** Short description of what the server says about itself (instructions of initialize). */
    val instructions: String = "",
) {
    fun policy(tool: String): McpPolicy = McpPolicy.of(policies[tool] ?: defaultPolicy)

    val local: Boolean get() = transport == STDIO

    /** Where it is: the address or the program. */
    val where: String get() = if (local) command else url

    fun envMap(): Map<String, String> = env.lines().map { it.trim() }.filter { '=' in it && !it.startsWith("#") }
        .associate { it.substringBefore('=').trim() to it.substringAfter('=').trim() }

    /** Whether this server's tools may run in a chat with [level] (see [internal]). */
    fun allowedAt(level: PrivacyLevel): Boolean = when (level) {
        PrivacyLevel.OPEN -> true
        PrivacyLevel.TU -> internal
        PrivacyLevel.LOCAL -> false
    }

    companion object {
        const val HTTP = "http"
        const val STDIO = "stdio"
    }
}
