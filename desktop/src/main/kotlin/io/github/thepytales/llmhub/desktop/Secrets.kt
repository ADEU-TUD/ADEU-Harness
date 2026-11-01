package io.github.thepytales.llmhub.desktop

import com.sun.jna.platform.win32.Crypt32Util
import java.io.File
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Encrypts secrets (API key, tokens, environment variables). On Windows with DPAPI: only the same Windows
 * user can decrypt them, no admin rights or password needed. On macOS and Linux (AP-31) with AES, whose key lies in the
 * system's key store – the macOS Keychain or the Secret Service of the Linux desktop (GNOME Keyring, KWallet) –, or,
 * without one, in a file only the user may read.
 */
object Secrets {
    private val windows = System.getProperty("os.name").orEmpty().startsWith("Windows")

    fun encrypt(plain: ByteArray): ByteArray =
        if (windows) Crypt32Util.cryptProtectData(plain) else aes(Cipher.ENCRYPT_MODE, plain)

    fun decrypt(data: ByteArray): ByteArray =
        if (windows) Crypt32Util.cryptUnprotectData(data) else aes(Cipher.DECRYPT_MODE, data)

    /** Read once: the Keychain asks a separate program. */
    private val cachedKey: ByteArray by lazy { loadKey() }

    private fun key(): ByteArray = cachedKey

    private fun loadKey(): ByteArray {
        val file = File(Paths.machine, "key.bin")
        // Until 1.0.44 in the data folder.
        File(Paths.data, "key.bin").takeIf { !file.exists() && it.isFile && it != file }?.let { runCatching { it.copyTo(file) } }
        if (file.exists()) return file.readBytes()
        KeyStore.load()?.let { return it }
        val fresh = ByteArray(32).also { SecureRandom().nextBytes(it) }
        if (KeyStore.store(fresh)) return fresh
        file.parentFile.mkdirs()
        file.writeBytes(fresh)
        // Only the owner may read it.
        runCatching {
            java.nio.file.Files.setPosixFilePermissions(file.toPath(), java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"))
        }
        return fresh
    }

    /** The system's key store for the AES key (macOS Keychain via `security`, Linux Secret Service via `secret-tool`). */
    private object KeyStore {
        private const val SERVICE = "ADEU LLM"
        private const val ACCOUNT = "lora-secrets"

        fun load(): ByteArray? = runCatching {
            val out = when {
                Os.mac -> run(listOf("security", "find-generic-password", "-a", ACCOUNT, "-s", SERVICE, "-w"))
                Os.linux -> run(listOf("secret-tool", "lookup", "service", SERVICE, "account", ACCOUNT))
                else -> null
            }
            out?.trim()?.takeIf { it.isNotEmpty() }?.let { java.util.Base64.getDecoder().decode(it) }?.takeIf { it.size == 32 }
        }.getOrNull()

        fun store(key: ByteArray): Boolean = runCatching {
            val b64 = java.util.Base64.getEncoder().encodeToString(key)
            when {
                Os.mac -> run(listOf("security", "add-generic-password", "-a", ACCOUNT, "-s", SERVICE, "-l", "Lora", "-U", "-w", b64)) != null
                Os.linux -> run(listOf("secret-tool", "store", "--label=Lora", "service", SERVICE, "account", ACCOUNT), input = b64) != null
                else -> false
            } && load()?.contentEquals(key) == true
        }.getOrDefault(false)

        /** Output of a command that ended with 0 within ten seconds, else null. */
        private fun run(cmd: List<String>, input: String? = null): String? {
            val p = ProcessBuilder(cmd).redirectError(ProcessBuilder.Redirect.DISCARD).start()
            if (input != null) p.outputStream.use { it.write(input.toByteArray()) } else p.outputStream.close()
            // Read beside, so a key store that waits for the user cannot block the start of the app.
            var out = ""
            val reader = Thread { out = runCatching { p.inputStream.bufferedReader().readText() }.getOrDefault("") }.apply { isDaemon = true; start() }
            if (!p.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)) { p.destroyForcibly(); return null }
            reader.join(2_000)
            return out.takeIf { p.exitValue() == 0 }
        }
    }

    private fun aes(mode: Int, input: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val key = SecretKeySpec(key(), "AES")
        return if (mode == Cipher.ENCRYPT_MODE) {
            val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
            cipher.init(mode, key, GCMParameterSpec(128, iv))
            iv + cipher.doFinal(input)
        } else {
            cipher.init(mode, key, GCMParameterSpec(128, input.copyOfRange(0, 12)))
            cipher.doFinal(input, 12, input.size - 12)
        }
    }
}
