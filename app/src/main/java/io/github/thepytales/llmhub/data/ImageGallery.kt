package io.github.thepytales.llmhub.data

import io.github.thepytales.llmhub.logFailure
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/** One generated picture of the gallery (Bilder erzeugen). */
@Serializable
data class GalleryImage(
    val id: String = UUID.randomUUID().toString(),
    val prompt: String,
    val model: String,
    /** Size as sent to the model, e.g. "1024x768". */
    val size: String,
    /** Style chosen for it ("Foto", "Aquarell" …), empty for none. */
    val style: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val favorite: Boolean = false,
) {
    val fileName get() = "$id.png"
}

/**
 * Generated pictures, kept in the app's data folder (and with that in backups) instead of disappearing when the
 * screen is left: files in images/, the list in images/index.json.
 */
class ImageGallery(private val dir: File) {
    private val index = File(dir, "index.json")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val serializer = ListSerializer(GalleryImage.serializer())

    private val _items = MutableStateFlow<List<GalleryImage>>(emptyList())
    /** Newest first. */
    val items: StateFlow<List<GalleryImage>> = _items.asStateFlow()

    suspend fun load() = withContext(Dispatchers.IO) {
        val text = runCatching { AtomicFiles.read(index) }.logFailure("Bildergalerie lesen").getOrNull() ?: return@withContext
        val list = runCatching { json.decodeFromString(serializer, text) }.getOrElse { e -> AtomicFiles.quarantine(index, e); emptyList() }
        _items.value = list.filter { File(dir, it.fileName).isFile }.sortedByDescending { it.createdAt }
    }

    fun file(image: GalleryImage) = File(dir, image.fileName)

    suspend fun add(image: GalleryImage, bytes: ByteArray) = withContext(Dispatchers.IO) {
        AtomicFiles.writeBytes(File(dir, image.fileName), bytes)
        _items.update { listOf(image) + it }
        save()
    }

    suspend fun update(image: GalleryImage) = withContext(Dispatchers.IO) {
        _items.update { list -> list.map { if (it.id == image.id) image else it } }
        save()
    }

    /** Hides the picture at once; [commitDelete] removes the file (after "Rückgängig" was not used). */
    fun hide(id: String) = _items.update { list -> list.filterNot { it.id == id } }

    fun unhide(image: GalleryImage) = _items.update { list -> (list.filterNot { it.id == image.id } + image).sortedByDescending { it.createdAt } }

    suspend fun commitDelete(image: GalleryImage) = withContext(Dispatchers.IO) {
        if (_items.value.none { it.id == image.id }) File(dir, image.fileName).delete()
        save()
    }

    private fun save() {
        runCatching { AtomicFiles.write(index, json.encodeToString(serializer, _items.value)) }.logFailure("Bildergalerie speichern")
    }
}
