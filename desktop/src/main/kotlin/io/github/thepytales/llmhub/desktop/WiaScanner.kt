package io.github.thepytales.llmhub.desktop

import com.sun.jna.platform.win32.Variant.VARIANT
import io.github.thepytales.llmhub.ui.tr
import java.io.File

/**
 * Scanner über WIA (AP-62): the scanning dialog of Windows (Windows Image Acquisition, in every Windows since XP, no
 * driver of our own and no admin rights). Over COM like Outlook and Office; the dialog lets the user choose the scanner,
 * colour and area, the page comes back as JPEG.
 */
object WiaScanner {
    private val thread = ComThread("wia-com")

    /** Whether there is WIA (only Windows). */
    val available: Boolean get() = Os.windows

    /** WiaFormatJPEG of the WIA Automation Layer. */
    private const val FORMAT_JPEG = "{B96B3CAE-0728-11D3-9D7B-0000F81EF32E}"

    /**
     * Shows the scanning dialog of Windows and saves the page in [dir]; null if the user cancelled. Throws with a
     * readable message when there is no scanner or WIA is missing.
     */
    fun scan(dir: File): File? {
        dir.mkdirs()
        val out = File(dir, "scanner-${System.currentTimeMillis()}.jpg")
        return try {
            thread.run(900, onTimeout = { IllegalStateException(tr("Der Scanner antwortet nicht.")) }) {
                val dialog = ComObj("WIA.CommonDialog")
                // ShowAcquireImage(DeviceType Scanner, Intent Colour, Bias MaximizeQuality, Format JPEG, AlwaysSelectDevice,
                // UseCommonUI, CancelError) – with CancelError false, Cancel gives no picture instead of an error.
                val image = dialog.get(
                    "ShowAcquireImage", VARIANT(1), VARIANT(1), VARIANT(131072), VARIANT(FORMAT_JPEG), VARIANT(false), VARIANT(true), VARIANT(false),
                ) as? ComObj ?: return@run null
                image.run("SaveFile", VARIANT(out.absolutePath))
                out.takeIf { it.isFile && it.length() > 0 }
            }
        } catch (e: UnsupportedOperationException) {
            throw IllegalStateException(tr("Scanner gibt es nur unter Windows (WIA)."))
        } catch (e: Exception) {
            val code = ComErrors.hresult(e)
            throw IllegalStateException(
                when (code) {
                    // WIA_S_NO_DEVICE_AVAILABLE / WIA_ERROR_OFFLINE
                    0x80210015.toInt(), 0x80210005.toInt() -> tr("Kein Scanner gefunden. Ist er eingeschaltet und angeschlossen?")
                    // REGDB_E_CLASSNOTREG: WIA is switched off on this PC.
                    0x80040154.toInt() -> tr("Die Scanner-Funktion von Windows (WIA) ist auf diesem PC nicht verfügbar.")
                    // WIA_ERROR_PAPER_EMPTY
                    0x80210003.toInt() -> tr("Im Einzug liegt kein Papier.")
                    else -> tr("Scannen hat nicht geklappt: {0}", e.message ?: e.javaClass.simpleName)
                },
                e,
            )
        }
    }
}
