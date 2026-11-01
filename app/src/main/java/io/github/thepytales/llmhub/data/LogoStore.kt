package io.github.thepytales.llmhub.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import io.github.thepytales.llmhub.ui.tr
import java.io.ByteArrayOutputStream
import java.io.File

/** Logos for documents, stored as PNG files in the app's private storage. */
class LogoStore(filesDir: File) : ListStore<Logo>(
    FileBackend(File(filesDir, "logos.json")),
    Logo.serializer(),
    { it.id },
) {
    private val dir = File(filesDir, "logos").apply { mkdirs() }

    val default: Logo? get() = current.firstOrNull { it.isDefault } ?: current.singleOrNull()

    /** Saves an image (any format Android can decode) as logo; the first logo becomes the default. */
    fun add(name: String, bytes: ByteArray): Logo {
        val src = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: throw IllegalArgumentException(tr("Bild konnte nicht gelesen werden."))
        val scale = minOf(1f, 1024f / maxOf(src.width, src.height))
        val bmp = if (scale < 1f) Bitmap.createScaledBitmap(src, (src.width * scale).toInt(), (src.height * scale).toInt(), true) else src
        val logo = Logo(name = name.trim().ifBlank { "Logo" }, path = "", isDefault = current.isEmpty())
        val file = File(dir, "${logo.id}.png")
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return logo.copy(path = file.absolutePath).also { upsert(it) }
    }

    fun remove(id: String) {
        get(id)?.let { File(it.path).delete() }
        val wasDefault = get(id)?.isDefault == true
        delete(id)
        if (wasDefault) current.firstOrNull()?.let { first -> update(first.id) { it.copy(isDefault = true) } }
    }

    fun setDefault(id: String) = replaceAll(current.map { it.copy(isDefault = it.id == id) })

    /** Finds a logo by name; "standard", "default" or "logo" mean the default logo. */
    fun find(name: String?): Logo? {
        val n = name?.trim()?.lowercase()?.removePrefix("logo:")?.trim().orEmpty()
        if (n.isEmpty() || n in setOf("standard", "default", "logo", "true", "ja")) return default
        return current.firstOrNull { it.name.equals(n, ignoreCase = true) }
            ?: current.firstOrNull { it.name.lowercase().contains(n) }
    }

    fun dataUrl(logo: Logo): String? = runCatching {
        "data:image/png;base64," + Base64.encodeToString(File(logo.path).readBytes(), Base64.NO_WRAP)
    }.getOrNull()

    /** PNG bytes of a logo, re-encoded so other formats (Word) can embed it. */
    fun pngBytes(logo: Logo): ByteArray? = runCatching {
        val bmp = BitmapFactory.decodeFile(logo.path) ?: return null
        ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
    }.getOrNull()
}
