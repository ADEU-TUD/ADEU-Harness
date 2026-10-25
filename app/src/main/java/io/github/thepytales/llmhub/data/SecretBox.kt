package io.github.thepytales.llmhub.data

import kotlinx.serialization.Serializable
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Text encrypted with a password (AES-256-GCM, key from PBKDF2-HMAC-SHA256). Used for the API keys and tokens in a
 * backup: without the password they cannot be read, and a wrong password is detected (GCM tag).
 */
@Serializable
data class SecretBox(
    val salt: String,
    val iv: String,
    val data: String,
    val iterations: Int = ITERATIONS,
) {
    companion object {
        const val ITERATIONS = 210_000
        const val MIN_PASSWORD = 8

        fun seal(plain: String, password: String): SecretBox {
            require(password.length >= MIN_PASSWORD) { "Das Passwort braucht mindestens $MIN_PASSWORD Zeichen." }
            val random = SecureRandom()
            val salt = ByteArray(16).also(random::nextBytes)
            val iv = ByteArray(12).also(random::nextBytes)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key(password, salt, ITERATIONS), GCMParameterSpec(128, iv))
            val enc = Base64.getEncoder()
            return SecretBox(enc.encodeToString(salt), enc.encodeToString(iv), enc.encodeToString(cipher.doFinal(plain.toByteArray())))
        }

        private fun key(password: String, salt: ByteArray, iterations: Int): SecretKeySpec {
            val spec = PBEKeySpec(password.toCharArray(), salt, iterations, 256)
            val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            spec.clearPassword()
            return SecretKeySpec(bytes, "AES")
        }
    }

    /** The plain text, or null if the password is wrong. */
    fun open(password: String): String? {
        val dec = Base64.getDecoder()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        return try {
            cipher.init(Cipher.DECRYPT_MODE, key(password, dec.decode(salt), iterations), GCMParameterSpec(128, dec.decode(iv)))
            cipher.doFinal(dec.decode(data)).decodeToString()
        } catch (e: AEADBadTagException) {
            null
        }
    }
}
