package io.github.thepytales.llmhub.files

import io.github.thepytales.llmhub.ui.tr
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Passwortschutz für Word, Excel und PowerPoint (AP-98) as Office does it (MS-OFFCRYPTO): the file is encrypted
 * whole and packed with its key information into an OLE container. Writing uses „Agile Encryption“ (AES-256, SHA-512,
 * 100 000 rounds, with an HMAC over the data), which Office 2010 and newer, LibreOffice and others open with the
 * password. Reading also takes the older „Standard Encryption“ of Office 2007 (AES-128, SHA-1). Nothing is cracked: a
 * wrong password only says so.
 */
object OfficeCrypto {
    class WrongPassword : Exception(tr("Das Passwort stimmt nicht."))

    private val random = SecureRandom()
    private const val SPIN = 100_000
    private const val SEGMENT = 4096

    private val BLOCK_VERIFIER_INPUT = byteArrayOf(0xfe.toByte(), 0xa7.toByte(), 0xd2.toByte(), 0x76, 0x3b, 0x4b, 0x9e.toByte(), 0x79)
    private val BLOCK_VERIFIER_VALUE = byteArrayOf(0xd7.toByte(), 0xaa.toByte(), 0x0f, 0x6d, 0x30, 0x61, 0x34, 0x4e)
    private val BLOCK_KEY = byteArrayOf(0x14, 0x6e, 0x0b, 0xe7.toByte(), 0xab.toByte(), 0xac.toByte(), 0xd0.toByte(), 0xd6.toByte())
    private val BLOCK_HMAC_KEY = byteArrayOf(0x5f, 0xb2.toByte(), 0xad.toByte(), 0x01, 0x0c, 0xb9.toByte(), 0xe1.toByte(), 0xf6.toByte())
    private val BLOCK_HMAC_VALUE = byteArrayOf(0xa0.toByte(), 0x67, 0x7f, 0x02, 0xb2.toByte(), 0x2c, 0x84.toByte(), 0x33)

    /** Whether [bytes] are an encrypted Office file (OLE container with „EncryptionInfo“ and „EncryptedPackage“). */
    fun isEncrypted(bytes: ByteArray): Boolean = Cfb.isCfb(bytes) &&
        runCatching { Cfb.open(bytes).let { it.stream("EncryptionInfo") != null && it.stream("EncryptedPackage") != null } }.getOrDefault(false)

    // ── Helpers ──

    private fun sha(alg: String, vararg parts: ByteArray): ByteArray = MessageDigest.getInstance(alg).run { parts.forEach(::update); digest() }

    private fun le32(i: Int) = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(i).array()

    /** The size [n] grown or cut to [size] bytes (filled with 0x36 as the format says). */
    private fun fit(b: ByteArray, size: Int): ByteArray = if (b.size >= size) b.copyOf(size) else b + ByteArray(size - b.size) { 0x36 }

    private fun pad(b: ByteArray, block: Int): ByteArray = if (b.size % block == 0) b else b + ByteArray(block - b.size % block)

    private fun aes(mode: Int, key: ByteArray, iv: ByteArray?, data: ByteArray): ByteArray {
        val c = if (iv == null) Cipher.getInstance("AES/ECB/NoPadding") else Cipher.getInstance("AES/CBC/NoPadding")
        if (iv == null) c.init(mode, SecretKeySpec(key, "AES")) else c.init(mode, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        return c.doFinal(data)
    }

    /** The hash of the password after [spin] rounds (agile). */
    private fun passwordHash(alg: String, salt: ByteArray, password: String, spin: Int): ByteArray {
        val md = MessageDigest.getInstance(alg)
        var h = md.digest(salt + password.toByteArray(Charsets.UTF_16LE))
        for (i in 0 until spin) {
            md.update(le32(i))
            h = md.digest(h)
        }
        return h
    }

    private fun agileKey(alg: String, h: ByteArray, block: ByteArray, bits: Int) = fit(sha(alg, h, block), bits / 8)

    // ── Writing ──

    /** [plain] (a .docx/.xlsx/.pptx) encrypted with [password] as Office writes it. */
    fun encrypt(plain: ByteArray, password: String): ByteArray {
        require(password.isNotEmpty()) { tr("Bitte ein Passwort eingeben.") }
        val keySalt = ByteArray(16).also(random::nextBytes)
        val pwSalt = ByteArray(16).also(random::nextBytes)
        val secret = ByteArray(32).also(random::nextBytes)

        // The package: size, then 4096-byte segments, each with its own IV.
        val pkg = java.io.ByteArrayOutputStream()
        pkg.write(ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(plain.size.toLong()).array())
        var i = 0
        var seg = 0
        while (i < plain.size) {
            val chunk = plain.copyOfRange(i, minOf(plain.size, i + SEGMENT))
            val iv = sha("SHA-512", keySalt, le32(seg)).copyOf(16)
            pkg.write(aes(Cipher.ENCRYPT_MODE, secret, iv, pad(chunk, 16)))
            i += SEGMENT
            seg++
        }
        val encryptedPackage = pkg.toByteArray()

        // Data integrity: HMAC-SHA512 over the whole stream, key and value encrypted with the secret key.
        val hmacKey = ByteArray(64).also(random::nextBytes)
        val mac = Mac.getInstance("HmacSHA512").apply { init(SecretKeySpec(hmacKey, "HmacSHA512")) }.doFinal(encryptedPackage)
        val encHmacKey = aes(Cipher.ENCRYPT_MODE, secret, sha("SHA-512", keySalt, BLOCK_HMAC_KEY).copyOf(16), hmacKey)
        val encHmacValue = aes(Cipher.ENCRYPT_MODE, secret, sha("SHA-512", keySalt, BLOCK_HMAC_VALUE).copyOf(16), mac)

        // Password key encryptor.
        val h = passwordHash("SHA-512", pwSalt, password, SPIN)
        val verifierInput = ByteArray(16).also(random::nextBytes)
        val encVerifierInput = aes(Cipher.ENCRYPT_MODE, agileKey("SHA-512", h, BLOCK_VERIFIER_INPUT, 256), pwSalt, verifierInput)
        val encVerifierValue = aes(Cipher.ENCRYPT_MODE, agileKey("SHA-512", h, BLOCK_VERIFIER_VALUE, 256), pwSalt, sha("SHA-512", verifierInput))
        val encKey = aes(Cipher.ENCRYPT_MODE, agileKey("SHA-512", h, BLOCK_KEY, 256), pwSalt, secret)

        fun b64(b: ByteArray) = Base64.getEncoder().encodeToString(b)
        val xml = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\r\n" +
            "<encryption xmlns=\"http://schemas.microsoft.com/office/2006/encryption\" xmlns:p=\"http://schemas.microsoft.com/office/2006/keyEncryptor/password\" xmlns:c=\"http://schemas.microsoft.com/office/2006/keyEncryptor/certificate\">" +
            "<keyData saltSize=\"16\" blockSize=\"16\" keyBits=\"256\" hashSize=\"64\" cipherAlgorithm=\"AES\" cipherChaining=\"ChainingModeCBC\" hashAlgorithm=\"SHA512\" saltValue=\"${b64(keySalt)}\"/>" +
            "<dataIntegrity encryptedHmacKey=\"${b64(encHmacKey)}\" encryptedHmacValue=\"${b64(encHmacValue)}\"/>" +
            "<keyEncryptors><keyEncryptor uri=\"http://schemas.microsoft.com/office/2006/keyEncryptor/password\">" +
            "<p:encryptedKey spinCount=\"$SPIN\" saltSize=\"16\" blockSize=\"16\" keyBits=\"256\" hashSize=\"64\" cipherAlgorithm=\"AES\" cipherChaining=\"ChainingModeCBC\" hashAlgorithm=\"SHA512\" saltValue=\"${b64(pwSalt)}\" " +
            "encryptedVerifierHashInput=\"${b64(encVerifierInput)}\" encryptedVerifierHashValue=\"${b64(encVerifierValue)}\" encryptedKeyValue=\"${b64(encKey)}\"/>" +
            "</keyEncryptor></keyEncryptors></encryption>"
        val info = byteArrayOf(4, 0, 4, 0, 0x40, 0, 0, 0) + xml.toByteArray(Charsets.UTF_8)

        return CfbWriter()
            .add("\u0006DataSpaces/Version", version())
            .add("\u0006DataSpaces/DataSpaceMap", dataSpaceMap())
            .add("\u0006DataSpaces/DataSpaceInfo/StrongEncryptionDataSpace", dataSpaceDefinition())
            .add("\u0006DataSpaces/TransformInfo/StrongEncryptionTransform/\u0006Primary", transformInfo())
            .add("EncryptionInfo", info)
            .add("EncryptedPackage", encryptedPackage)
            .bytes()
    }

    // The „\u0006DataSpaces“ storage that says the package is encrypted (MS-OFFCRYPTO 2.1, 2.2).
    private fun lp4(s: String): ByteArray {
        val u = s.toByteArray(Charsets.UTF_16LE)
        return le32(u.size) + u + ByteArray((4 - u.size % 4) % 4)
    }

    private fun versions() = ByteArray(12).also { b -> for (k in 0 until 3) b[k * 4] = 1 }

    private fun version() = lp4("Microsoft.Container.DataSpaces") + versions()

    private fun dataSpaceMap(): ByteArray {
        val entry = le32(1) + le32(0) + lp4("EncryptedPackage") + lp4("StrongEncryptionDataSpace")
        return le32(8) + le32(1) + le32(entry.size + 4) + entry
    }

    private fun dataSpaceDefinition() = le32(8) + le32(1) + lp4("StrongEncryptionTransform")

    private fun transformInfo(): ByteArray {
        val id = lp4("{FF9A3F03-56EF-4613-BDD5-5A41C1D07246}")
        val header = le32(1) + id
        return le32(header.size + 4) + header + lp4("Microsoft.Container.EncryptionTransform") + versions() +
            le32(0) + le32(0) + le32(0) + le32(4)
    }

    // ── Reading ──

    /** The plain file inside the encrypted [bytes]; [WrongPassword] if [password] does not fit. */
    fun decrypt(bytes: ByteArray, password: String): ByteArray {
        val cfb = Cfb.open(bytes)
        val info = cfb.stream("EncryptionInfo") ?: error(tr("Keine verschlüsselte Office-Datei."))
        val pkg = cfb.stream("EncryptedPackage") ?: error(tr("Keine verschlüsselte Office-Datei."))
        val major = (info[0].toInt() and 0xFF) or ((info[1].toInt() and 0xFF) shl 8)
        val minor = (info[2].toInt() and 0xFF) or ((info[3].toInt() and 0xFF) shl 8)
        return when {
            major == 4 && minor == 4 -> agile(info, pkg, password)
            minor == 2 && major in 2..4 -> standard(info, pkg, password)
            else -> error(tr("Diese Verschlüsselung (Version {0}.{1}) kennt Lora nicht.", major, minor))
        }
    }

    private fun attr(xml: String, el: String, name: String): String? =
        Regex("<(?:\\w+:)?$el\\b[^>]*?\\s$name=\"([^\"]*)\"").find(xml)?.groupValues?.get(1)

    private fun hashName(x: String?) = when (x?.uppercase()) {
        "SHA1", "SHA-1" -> "SHA-1"; "SHA256" -> "SHA-256"; "SHA384" -> "SHA-384"; else -> "SHA-512"
    }

    private fun agile(info: ByteArray, pkg: ByteArray, password: String): ByteArray {
        val xml = String(info, 8, info.size - 8, Charsets.UTF_8)
        val d = Base64.getDecoder()
        val keySalt = d.decode(attr(xml, "keyData", "saltValue"))
        val keyBits = attr(xml, "keyData", "keyBits")?.toIntOrNull() ?: 256
        val keyHash = hashName(attr(xml, "keyData", "hashAlgorithm"))
        val pwHash = hashName(attr(xml, "encryptedKey", "hashAlgorithm"))
        val pwBits = attr(xml, "encryptedKey", "keyBits")?.toIntOrNull() ?: 256
        val pwSalt = d.decode(attr(xml, "encryptedKey", "saltValue"))
        val spin = attr(xml, "encryptedKey", "spinCount")?.toIntOrNull() ?: SPIN
        val h = passwordHash(pwHash, pwSalt, password, spin)
        val input = aes(Cipher.DECRYPT_MODE, agileKey(pwHash, h, BLOCK_VERIFIER_INPUT, pwBits), pwSalt, d.decode(attr(xml, "encryptedKey", "encryptedVerifierHashInput")))
        val value = aes(Cipher.DECRYPT_MODE, agileKey(pwHash, h, BLOCK_VERIFIER_VALUE, pwBits), pwSalt, d.decode(attr(xml, "encryptedKey", "encryptedVerifierHashValue")))
        val expected = sha(pwHash, input.copyOf(attr(xml, "encryptedKey", "saltSize")?.toIntOrNull() ?: 16))
        if (!MessageDigest.isEqual(expected, value.copyOf(expected.size))) throw WrongPassword()
        val secret = aes(Cipher.DECRYPT_MODE, agileKey(pwHash, h, BLOCK_KEY, pwBits), pwSalt, d.decode(attr(xml, "encryptedKey", "encryptedKeyValue"))).copyOf(keyBits / 8)
        val size = ByteBuffer.wrap(pkg, 0, 8).order(ByteOrder.LITTLE_ENDIAN).long.toInt()
        val out = java.io.ByteArrayOutputStream(size)
        var i = 8
        var seg = 0
        while (i < pkg.size && out.size() < size) {
            val chunk = pkg.copyOfRange(i, minOf(pkg.size, i + SEGMENT))
            val iv = sha(keyHash, keySalt, le32(seg)).copyOf(16)
            out.write(aes(Cipher.DECRYPT_MODE, secret, iv, pad(chunk, 16)))
            i += SEGMENT
            seg++
        }
        return out.toByteArray().copyOf(size)
    }

    private fun standard(info: ByteArray, pkg: ByteArray, password: String): ByteArray {
        val b = ByteBuffer.wrap(info).order(ByteOrder.LITTLE_ENDIAN)
        val headerSize = b.getInt(8)
        val header = 12
        val keyBits = b.getInt(header + 16)
        val verifier = header + headerSize
        val saltSize = b.getInt(verifier)
        val salt = info.copyOfRange(verifier + 4, verifier + 4 + saltSize)
        val encVerifier = info.copyOfRange(verifier + 20, verifier + 36)
        val hashSize = b.getInt(verifier + 36)
        val encHash = info.copyOfRange(verifier + 40, verifier + 40 + 32)
        // Key: SHA-1 of salt and password, 50 000 rounds, block 0, then the derivation with 0x36 / 0x5c.
        var h = passwordHash("SHA-1", salt, password, 50_000)
        h = sha("SHA-1", h, le32(0))
        val x1 = sha("SHA-1", ByteArray(64) { 0x36 }.also { a -> h.forEachIndexed { k, v -> a[k] = (a[k].toInt() xor v.toInt()).toByte() } })
        val x2 = sha("SHA-1", ByteArray(64) { 0x5c }.also { a -> h.forEachIndexed { k, v -> a[k] = (a[k].toInt() xor v.toInt()).toByte() } })
        val key = (x1 + x2).copyOf(keyBits / 8)
        val v = aes(Cipher.DECRYPT_MODE, key, null, encVerifier)
        val vh = aes(Cipher.DECRYPT_MODE, key, null, encHash)
        if (!MessageDigest.isEqual(sha("SHA-1", v), vh.copyOf(hashSize.coerceIn(1, 20)))) throw WrongPassword()
        val size = ByteBuffer.wrap(pkg, 0, 8).order(ByteOrder.LITTLE_ENDIAN).long.toInt()
        return aes(Cipher.DECRYPT_MODE, key, null, pad(pkg.copyOfRange(8, pkg.size), 16)).copyOf(size)
    }
}
