package io.github.thepytales.llmhub.desktop

import com.sun.jna.platform.win32.Crypt32Util
import java.io.File
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Encrypts secrets (API key, tokens, environment variables). On Windows with DPAPI: only the same Windows
 * user can decrypt them, no admin rights or password needed. Elsewhere (development) with AES and a key file.
 */
object Secrets {
    private val windows = System.getProperty("os.name").orEmpty().startsWith("Windows")

    fun encrypt(plain: ByteArray): ByteArray =
        if (windows) Crypt32Util.cryptProtectData(plain) else aes(Cipher.ENCRYPT_MODE, plain)

    fun decrypt(data: ByteArray): ByteArray =
        if (windows) Crypt32Util.cryptUnprotectData(data) else aes(Cipher.DECRYPT_MODE, data)

    private fun key(): ByteArray {
        val file = File(Paths.machine, "key.bin")
        // Until 1.0.44 in the data folder.
        File(Paths.data, "key.bin").takeIf { !file.exists() && it.isFile && it != file }?.let { runCatching { it.copyTo(file) } }
        if (!file.exists()) {
            file.parentFile.mkdirs()
            file.writeBytes(ByteArray(32).also { SecureRandom().nextBytes(it) })
        }
        return file.readBytes()
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
