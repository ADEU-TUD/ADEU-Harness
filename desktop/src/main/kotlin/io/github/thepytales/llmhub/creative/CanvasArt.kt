package io.github.thepytales.llmhub.creative

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.thepytales.llmhub.data.Design
import io.github.thepytales.llmhub.data.DesignElement

/**
 * Elements of the shared canvas (AP-50) drawn by the renderer of Gestalten – the Windows version of the shared file:
 * the TUD-Shape, symbols (Material and TUD icons), QR codes and tables look on a slide exactly as in a design, and the
 * PowerPoint export of the slides gets the same picture.
 */
object CanvasArt {
    const val FULL = true

    private val none = Design(id = "folie", title = "Folie")

    fun png(e: DesignElement, pxPerMm: Float): ByteArray? = runCatching { renderElement(none, e, pxPerMm).pngBytes() }.getOrNull()
}

@Composable
fun CanvasArtView(e: DesignElement, modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxSize()) {
        if (e.w <= 0f) return@Canvas
        drawDesignElement(Design(id = "folie", title = "Folie"), e.copy(x = 0f, y = 0f, rotation = 0f), size.width / e.w)
    }
}

@Composable
fun CanvasSymbolPicker(onDismiss: () -> Unit, onPick: (String) -> Unit) = io.github.thepytales.llmhub.ui.SymbolPicker(onDismiss, onPick)
