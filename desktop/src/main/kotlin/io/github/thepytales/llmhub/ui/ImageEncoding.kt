package io.github.thepytales.llmhub.ui

import io.github.thepytales.llmhub.desktop.Images
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Loads, downscales and encodes an image as JPEG data URL. */
suspend fun encodeImage(file: File): String? = withContext(Dispatchers.IO) {
    runCatching { Images.dataUrl(file.readBytes()) }.getOrNull()
}
