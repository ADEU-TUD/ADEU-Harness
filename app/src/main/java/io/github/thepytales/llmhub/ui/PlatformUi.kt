package io.github.thepytales.llmhub.ui

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.Modifier

/** Android: context menus open with a long press only. The Windows version of this file adds the right click. */
@Suppress("UNUSED_PARAMETER")
fun Modifier.secondaryClick(action: () -> Unit): Modifier = this

/** How a context menu is opened – for hints in the interface. */
const val CONTEXT_GESTURE = "Lange drücken"

/** System back (gesture or button) handled by the screen itself, e.g. to close a search first. */
@Composable
fun PlatformBackHandler(enabled: Boolean = true, onBack: () -> Unit) = BackHandler(enabled, onBack)

/** Picture from PNG/JPEG bytes (Android: BitmapFactory; Windows: Skia). */
fun decodeImage(bytes: ByteArray): ImageBitmap? = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()

/** Opens the Android share sheet for [text]. */
fun shareText(context: Context, text: String, subject: String? = null) {
    val intent = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    subject?.let { intent.putExtra(Intent.EXTRA_SUBJECT, it) }
    runCatching { context.startActivity(Intent.createChooser(intent, "Teilen")) }
}

/** Verb for touching something, in hints ("tippen" / "klicken"). */
const val TAP = "tippen"

/** Enter in the chat input sends (Windows) or starts a new line (Android). */
const val ENTER_SENDS = false

/** First tile of the attach sheet: the camera on Android, pasting from the clipboard on Windows. */
object CameraTile {
    val icon: ImageVector get() = Icons.Outlined.CameraAlt
    const val label = "Kamera"
}

const val PHOTOS_LABEL = "Fotos"

/** Extra sentence for the attach sheet. */
const val ATTACH_HINT = ""
