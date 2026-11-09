package io.github.thepytales.llmhub.ui

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerHoverIcon
import io.github.thepytales.llmhub.desktop.DesktopActions
import org.jetbrains.skia.Image as SkiaImage
import androidx.compose.ui.Modifier

/**
 * Windows: the right click opens the same menu as a long press on Android – and so do the menu key and Shift+F10 on
 * the focused element (keyboard, AP-30; every caller puts this after its clickable).
 */
fun Modifier.secondaryClick(action: () -> Unit): Modifier = onRightClick(action).onKeyEvent { e ->
    val menuKey = e.key == Key.Menu || e.isShiftPressed && e.key == Key.F10
    if (menuKey && e.type == KeyEventType.KeyUp) action()
    menuKey
}

/** The resize cursor ⇔ over the line between side menu, screen and panels (PanelResizer); ⇕ over a [vertical] one. */
fun Modifier.resizeCursor(vertical: Boolean = false): Modifier =
    pointerHoverIcon(androidx.compose.ui.input.pointer.PointerIcon(java.awt.Cursor(if (vertical) java.awt.Cursor.N_RESIZE_CURSOR else java.awt.Cursor.E_RESIZE_CURSOR)))

/** The app runs on Android (Windows: false); e.g. for tools that only Windows offers (data/AppTool). */
const val IS_ANDROID = false

/** How a context menu is opened – for hints in the interface. */
val CONTEXT_GESTURE: String get() = tr("Rechtsklick")

/** Windows has no system back; Escape is handled per screen. */
@Suppress("UNUSED_PARAMETER")
@Composable
fun PlatformBackHandler(enabled: Boolean = true, onBack: () -> Unit) {}

/** Picture from PNG/JPEG bytes (Android: BitmapFactory; Windows: Skia). */
fun decodeImage(bytes: ByteArray): ImageBitmap? = SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap()

/** Windows has no share sheet: the text goes to the clipboard, ready to paste into mail, Teams … */
@Suppress("UNUSED_PARAMETER")
fun shareText(context: Context, text: String, subject: String? = null) {
    DesktopActions.share(if (subject != null) "$subject\n\n$text" else text)
}

/** Verb for touching something, in hints ("tippen" / "klicken"). */
val TAP: String get() = tr("klicken")

/** Windows (mouse and keyboard first): selecting text in a document opens the „KI fragen“ window right away. */
const val DESKTOP = true

/** Enter in the chat input sends (Windows) or starts a new line (Android). */
const val ENTER_SENDS = true

/** First tile of the attach sheet: the camera on Android, pasting from the clipboard on Windows. */
object CameraTile {
    val icon: ImageVector get() = Icons.Outlined.ContentPaste
    val label: String get() = tr("Einfügen")
}

val PHOTOS_LABEL: String get() = tr("Bilder")

/** Extra sentence for the attach sheet. */
val ATTACH_HINT: String get() = tr(" Dateien lassen sich auch ins Fenster ziehen; „Einfügen“ übernimmt einen Screenshot (Win+Umschalt+S) aus der Zwischenablage.")

/** PNG bytes of a picture (masks and reference images for "Überarbeiten"). */
fun encodePng(image: ImageBitmap): ByteArray =
    SkiaImage.makeFromBitmap(image.asSkiaBitmap()).encodeToData(org.jetbrains.skia.EncodedImageFormat.PNG)!!.bytes
