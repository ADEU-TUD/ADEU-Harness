package io.github.thepytales.llmhub.files

import io.github.thepytales.llmhub.docs.PdfStage3
import io.github.thepytales.llmhub.ui.tr
import java.io.File

/**
 * Passwortschutz für Dateien (AP-98): set, change or – with the known password – remove the password of Word, Excel,
 * PowerPoint ([OfficeCrypto], Agile Encryption with AES-256), PDF (AES-256, [PdfStage3]) and ZIP (AES-256,
 * [Archives]). By default the result is a new file next to the original („… (mit Passwort).docx“); replacing the
 * original is a choice. Passwords of others are never guessed.
 */
object FileProtect {
    enum class Kind { OFFICE, PDF, ZIP }

    fun kind(name: String): Kind? = when (name.substringAfterLast('.', "").lowercase()) {
        "docx", "docm", "xlsx", "xlsm", "pptx", "pptm" -> Kind.OFFICE
        "pdf" -> Kind.PDF
        "zip" -> Kind.ZIP
        else -> null
    }

    fun supported(name: String) = kind(name) != null

    /** Whether [file] is protected with a password (to open). */
    fun isProtected(file: File): Boolean = when (kind(file.name)) {
        Kind.OFFICE -> runCatching { ByteArray(8).also { h -> file.inputStream().use { it.read(h) } }.let { h -> h[0] == 0xD0.toByte() && h[1] == 0xCF.toByte() } && OfficeCrypto.isEncrypted(file.readBytes()) }.getOrDefault(false)
        Kind.PDF -> runCatching { PdfStage3.encrypted(file) }.getOrDefault(false)
        Kind.ZIP -> runCatching { Archives.needsPassword(file) }.getOrDefault(false)
        null -> false
    }

    /** Name of the result next to [file]: „Bericht (mit Passwort).docx“, numbered if taken. */
    fun outFile(file: File, suffix: String): File {
        val dir = file.parentFile ?: File(".")
        val base = file.nameWithoutExtension.replace(Regex(" \\((mit|ohne) Passwort\\)( \\(\\d+\\))?$"), "")
        val ext = file.extension
        var f = File(dir, "$base ($suffix).$ext")
        var n = 2
        while (f.exists()) f = File(dir, "$base ($suffix) ($n).$ext").also { n++ }
        return f
    }

    /** Writes [file] protected with [password] to [out] (the file must not be protected yet). */
    fun protect(file: File, password: String, out: File) {
        require(password.isNotEmpty()) { tr("Bitte ein Passwort eingeben.") }
        when (kind(file.name)) {
            Kind.OFFICE -> {
                val bytes = file.readBytes()
                if (OfficeCrypto.isEncrypted(bytes)) throw IllegalArgumentException(tr("Die Datei hat schon ein Passwort – zuerst „Passwort ändern“ wählen."))
                out.writeBytes(OfficeCrypto.encrypt(bytes, password))
            }
            Kind.PDF -> PdfStage3.protect(file, password, "", print = true, copy = true, out = out)
            Kind.ZIP -> Archives.protect(file, password.toCharArray(), out)
            null -> throw IllegalArgumentException(tr("Für „{0}“ gibt es keinen Passwortschutz.", file.name))
        }
    }

    /** Writes [file] without its password to [out]; [password] must be the right one. */
    fun unprotect(file: File, password: String, out: File) {
        when (kind(file.name)) {
            Kind.OFFICE -> out.writeBytes(OfficeCrypto.decrypt(file.readBytes(), password))
            Kind.PDF -> PdfStage3.unprotect(file, password, out)
            Kind.ZIP -> try {
                Archives.unprotect(file, password.toCharArray(), out)
            } catch (e: Archives.PasswordNeeded) {
                out.delete()
                throw IllegalArgumentException(e.message)
            }
            null -> throw IllegalArgumentException(tr("Für „{0}“ gibt es keinen Passwortschutz.", file.name))
        }
    }

    /** New password: opens with [old], protects with [new]. */
    fun change(file: File, old: String, new: String, out: File) {
        val tmp = File(out.parentFile, ".lora-pw-${System.nanoTime()}.${file.extension}")
        try {
            unprotect(file, old, tmp)
            protect(tmp, new, out)
        } finally {
            tmp.delete()
        }
    }

    /** The plain bytes of a protected Office file, to open it in Lora's editor (nothing is written to disk). */
    fun openOffice(file: File, password: String): ByteArray = OfficeCrypto.decrypt(file.readBytes(), password)

    /** Replaces [original] by [made] (after the work is done), keeping the original's name. */
    fun replace(original: File, made: File): File {
        val backup = File(original.parentFile, ".${original.name}.lora-alt")
        original.renameTo(backup)
        return if (made.renameTo(original)) original.also { backup.delete() } else {
            backup.renameTo(original)
            made
        }
    }
}
