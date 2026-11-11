package io.github.thepytales.llmhub.ssh

import io.github.thepytales.llmhub.data.DeviceKey
import io.github.thepytales.llmhub.ui.trLater
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.File
import java.util.Base64
import java.util.UUID

/** How a saved session logs in (AP-118). */
enum class SshAuth { KEY, PASSWORD }

/**
 * A saved session like in PuTTY (AP-118): where and as whom – never a password or passphrase (those are in
 * [ServerSecrets], encrypted with the key of this device, and only when the user asked to keep them).
 */
@Serializable
data class SshProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val host: String = "",
    val port: Int = 22,
    val user: String = "",
    val auth: SshAuth = SshAuth.KEY,
    /** Private key file (OpenSSH or PuTTY .ppk) for [SshAuth.KEY]. */
    val keyFile: String = "",
    /** Folder of the application on the server, e.g. /var/www/app – the flows run there. */
    val workDir: String = "",
    /** User of the web server (Debian/Ubuntu: www-data) for ACLs and the cache. */
    val webUser: String = "www-data",
) {
    val label: String get() = name.ifBlank { "$user@$host" + if (port != 22) ":$port" else "" }

    /** What is missing before a connection can be tried; empty when complete. */
    fun problems(): List<String> = buildList {
        if (host.isBlank()) add(trLater("Host fehlt"))
        if (port !in 1..65535) add(trLater("Port muss zwischen 1 und 65535 liegen"))
        if (user.isBlank()) add(trLater("Benutzer fehlt"))
        if (auth == SshAuth.KEY && keyFile.isBlank()) add(trLater("Schlüsseldatei fehlt"))
    }
}

/** The saved sessions in `ssh/sessions.json` of the data folder – plain text, without secrets. */
class ServerSessions(private val dir: File) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }
    private val file get() = File(dir, "sessions.json")
    val secrets = ServerSecrets(File(dir, "secrets.bin"))

    fun all(): List<SshProfile> = runCatching { json.decodeFromString(ListSerializer(SshProfile.serializer()), file.readText()) }.getOrDefault(emptyList())

    fun get(id: String): SshProfile? = all().firstOrNull { it.id == id }

    @Synchronized
    fun save(p: SshProfile) {
        val list = all().filter { it.id != p.id } + p
        write(list.sortedBy { it.label.lowercase() })
    }

    @Synchronized
    fun delete(id: String) {
        write(all().filter { it.id != id })
        secrets.forget(id)
    }

    private fun write(list: List<SshProfile>) {
        dir.mkdirs()
        val tmp = File(dir, "sessions.json.tmp")
        tmp.writeText(json.encodeToString(ListSerializer(SshProfile.serializer()), list))
        if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
    }
}

/**
 * Passwords and passphrases of saved sessions, only when the user ticked „Speichern“: one file, encrypted with the key
 * of this device (Windows DPAPI, macOS Keychain, Linux Secret Service – like the API key). Nothing here ever goes into a
 * model request or the protocol; [all] lets the redaction remove them from every text that leaves the terminal.
 */
class ServerSecrets(private val file: File) {
    private val json = Json { ignoreUnknownKeys = true }
    private val map = MapSerializer(String.serializer(), String.serializer())

    private fun load(): Map<String, String> = runCatching {
        val sealed = Base64.getDecoder().decode(file.readText().trim())
        val plain = DeviceKey.open(sealed) ?: return emptyMap()
        json.decodeFromString(map, plain.toString(Charsets.UTF_8))
    }.getOrDefault(emptyMap())

    @Synchronized
    private fun store(values: Map<String, String>) {
        if (values.isEmpty()) { file.delete(); return }
        val sealed = DeviceKey.seal(json.encodeToString(map, values).toByteArray()) ?: error(io.github.thepytales.llmhub.ui.tr("Verschlüsseln nicht möglich"))
        file.parentFile?.mkdirs()
        file.writeText(Base64.getEncoder().encodeToString(sealed))
    }

    fun get(profileId: String): String? = load()[profileId]

    fun put(profileId: String, secret: String) = store(load() + (profileId to secret))

    fun forget(profileId: String) = store(load() - profileId)

    /** All saved secrets – for the redaction only. */
    fun all(): Collection<String> = load().values
}
