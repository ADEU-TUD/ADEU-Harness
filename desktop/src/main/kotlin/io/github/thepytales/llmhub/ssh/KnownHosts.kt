package io.github.thepytales.llmhub.ssh

import org.apache.sshd.common.config.keys.KeyUtils
import org.apache.sshd.common.config.keys.PublicKeyEntry
import org.apache.sshd.common.config.keys.PublicKeyEntryResolver
import java.io.File
import java.security.PublicKey

/**
 * Server keys the user confirmed (AP-118), in a `known_hosts` of the data folder in OpenSSH format (`[host]:port` for
 * ports other than 22, no hashed names – the file stays readable). The terminal never accepts a key silently:
 * [check] says whether the key is known, new (ask, showing the SHA-256 fingerprint) or **changed** (block – maybe an
 * attack; continue only after the user explicitly replaces it).
 */
class KnownHosts(val file: File) {
    sealed interface Result {
        val fingerprint: String
        data class Known(override val fingerprint: String) : Result
        data class New(override val fingerprint: String, val keyType: String) : Result
        data class Changed(override val fingerprint: String, val keyType: String, val previous: List<String>) : Result
    }

    /** `host` or `[host]:port`, like OpenSSH. */
    fun name(host: String, port: Int): String = host.lowercase().let { if (port == 22) it else "[$it]:$port" }

    private data class Entry(val names: List<String>, val key: PublicKey?, val line: String)

    private fun entries(): List<Entry> = if (!file.isFile) emptyList() else file.readLines().mapNotNull { raw ->
        val line = raw.trim()
        if (line.isEmpty() || line.startsWith("#") || line.startsWith("@")) return@mapNotNull null
        val sp = line.indexOf(' ')
        if (sp <= 0) return@mapNotNull null
        val key = runCatching { PublicKeyEntry.parsePublicKeyEntry(line.substring(sp + 1).trim()).resolvePublicKey(null, emptyMap(), PublicKeyEntryResolver.IGNORING) }.getOrNull()
        Entry(line.substring(0, sp).split(',').map { it.lowercase() }, key, line)
    }

    @Synchronized
    fun check(host: String, port: Int, key: PublicKey): Result {
        val fp = fingerprint(key)
        val type = KeyUtils.getKeyType(key)
        val mine = entries().filter { name(host, port) in it.names }
        if (mine.any { it.key != null && KeyUtils.compareKeys(it.key, key) }) return Result.Known(fp)
        // Another key of the same type for this host: the server changed it – or someone sits in between.
        val sameType = mine.filter { it.key != null && KeyUtils.getKeyType(it.key) == type }
        return if (sameType.isEmpty()) Result.New(fp, type) else Result.Changed(fp, type, sameType.map { fingerprint(it.key!!) })
    }

    /** Remembers [key] for the host; with [replace] keys of the same type for it go (after a confirmed change). */
    @Synchronized
    fun accept(host: String, port: Int, key: PublicKey, replace: Boolean = false) {
        val n = name(host, port)
        val type = KeyUtils.getKeyType(key)
        val keep = entries().filterNot { replace && n in it.names && it.key != null && KeyUtils.getKeyType(it.key) == type }
        val lines = keep.map { it.line } + "$n ${PublicKeyEntry.toString(key)}"
        file.parentFile?.mkdirs()
        file.writeText(lines.joinToString("\n", postfix = "\n"))
    }

    /** All hosts with their fingerprints (Einstellungen). */
    fun list(): List<Pair<String, String>> = entries().mapNotNull { e -> e.key?.let { e.names.joinToString(", ") to fingerprint(it) } }

    @Synchronized
    fun forget(host: String, port: Int) {
        val n = name(host, port)
        val rest = entries().filterNot { n in it.names }.map { it.line }
        file.writeText(rest.joinToString("\n", postfix = if (rest.isEmpty()) "" else "\n"))
    }

    companion object {
        /** `SHA256:…` like `ssh-keygen -lf` and PuTTY show it. */
        fun fingerprint(key: PublicKey): String = KeyUtils.getFingerPrint(key)
    }
}
