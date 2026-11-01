package io.github.thepytales.llmhub.desktop

import io.github.thepytales.llmhub.docs.LocalOcr
import io.github.thepytales.llmhub.docs.OcrPage
import io.github.thepytales.llmhub.docs.WindowsOcrOutput
import io.github.thepytales.llmhub.ui.tr
import java.io.File
import java.util.Base64
import java.util.concurrent.TimeUnit

/**
 * Text recognition with the OCR built into Windows 10/11 (Windows.Media.Ocr) – on the PC, nothing leaves it, nothing
 * to install. Reached through Windows PowerShell 5.1 (the only built-in way to the WinRT API without native code); the
 * script goes as -EncodedCommand, which the execution policy does not block. Languages: German, else English, else the
 * languages of the user profile – Windows brings the recognizer with its language packs.
 */
object WindowsOcr : LocalOcr {
    override val name: String get() = "Texterkennung von Windows" + (language?.let { " ($it)" } ?: "")

    /** The language of the last run (e.g. de-DE). */
    @Volatile
    var language: String? = null
        private set

    private const val BATCH = 12

    private val SCRIPT = """
${'$'}ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
try {
  Add-Type -AssemblyName System.Runtime.WindowsRuntime
  ${'$'}null = [Windows.Storage.StorageFile, Windows.Storage, ContentType = WindowsRuntime]
  ${'$'}null = [Windows.Media.Ocr.OcrEngine, Windows.Foundation, ContentType = WindowsRuntime]
  ${'$'}null = [Windows.Graphics.Imaging.BitmapDecoder, Windows.Foundation, ContentType = WindowsRuntime]
  ${'$'}null = [Windows.Globalization.Language, Windows.Foundation, ContentType = WindowsRuntime]
} catch { Write-Output ('#ERROR WINRT ' + ${'$'}_.Exception.Message); exit 0 }
${'$'}asTask = ([System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object { ${'$'}_.Name -eq 'AsTask' -and ${'$'}_.GetParameters().Count -eq 1 -and ${'$'}_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1' })[0]
function Await(${'$'}op, [Type]${'$'}type) { ${'$'}t = ${'$'}asTask.MakeGenericMethod(${'$'}type).Invoke(${'$'}null, @(${'$'}op)); ${'$'}t.Wait(-1) | Out-Null; ${'$'}t.Result }
${'$'}engine = ${'$'}null
foreach (${'$'}tag in @('de-DE', 'de', 'en-US', 'en')) {
  if (-not ${'$'}engine) { try { ${'$'}engine = [Windows.Media.Ocr.OcrEngine]::TryCreateFromLanguage([Windows.Globalization.Language]::new(${'$'}tag)) } catch {} }
}
if (-not ${'$'}engine) { ${'$'}engine = [Windows.Media.Ocr.OcrEngine]::TryCreateFromUserProfileLanguages() }
if (-not ${'$'}engine) { Write-Output '#ERROR NOENGINE'; exit 0 }
Write-Output ('#ENGINE ' + ${'$'}engine.RecognizerLanguage.LanguageTag)
${'$'}paths = @(__PATHS__)
for (${'$'}i = 0; ${'$'}i -lt ${'$'}paths.Count; ${'$'}i++) {
  try {
    ${'$'}file = Await ([Windows.Storage.StorageFile]::GetFileFromPathAsync(${'$'}paths[${'$'}i])) ([Windows.Storage.StorageFile])
    ${'$'}stream = Await (${'$'}file.OpenAsync([Windows.Storage.FileAccessMode]::Read)) ([Windows.Storage.Streams.IRandomAccessStream])
    ${'$'}decoder = Await ([Windows.Graphics.Imaging.BitmapDecoder]::CreateAsync(${'$'}stream)) ([Windows.Graphics.Imaging.BitmapDecoder])
    ${'$'}bitmap = Await (${'$'}decoder.GetSoftwareBitmapAsync()) ([Windows.Graphics.Imaging.SoftwareBitmap])
    ${'$'}result = Await (${'$'}engine.RecognizeAsync(${'$'}bitmap)) ([Windows.Media.Ocr.OcrResult])
    Write-Output ('#PAGE ' + ${'$'}i + ' ' + ${'$'}bitmap.PixelWidth + ' ' + ${'$'}bitmap.PixelHeight)
    foreach (${'$'}line in ${'$'}result.Lines) {
      foreach (${'$'}w in ${'$'}line.Words) {
        ${'$'}r = ${'$'}w.BoundingRect
        Write-Output ([string][int]${'$'}r.X + "`t" + [string][int]${'$'}r.Y + "`t" + [string][int]${'$'}r.Width + "`t" + [string][int]${'$'}r.Height + "`t" + ${'$'}w.Text)
      }
      Write-Output '#LINE'
    }
    ${'$'}stream.Dispose()
  } catch { Write-Output ('#FAIL ' + ${'$'}i + ' ' + ${'$'}_.Exception.Message) }
}
""".trimIndent()

    /** The script for these files (single quotes in paths doubled, as PowerShell wants). */
    internal fun script(images: List<File>): String =
        SCRIPT.replace("__PATHS__", images.joinToString(", ") { "'" + it.absolutePath.replace("'", "''") + "'" })

    override fun recognize(images: List<File>): List<OcrPage?> {
        if (!com.sun.jna.Platform.isWindows()) throw IllegalStateException("Nur unter Windows.")
        val out = arrayOfNulls<OcrPage>(images.size)
        images.chunked(BATCH).forEachIndexed { b, batch ->
            val parsed = WindowsOcrOutput.parse(run(script(batch), 60L + 20L * batch.size))
            parsed.error?.let { e ->
                throw IllegalStateException(
                    when {
                        e.startsWith("NOENGINE") -> tr("Windows hat keine Texterkennung für Deutsch oder Englisch (Einstellungen › Zeit und Sprache › Sprache: Sprachpaket mit „Optische Zeichenerkennung“ hinzufügen).")
                        e.startsWith("WINRT") -> tr("Die Texterkennung von Windows ist hier nicht erreichbar (PowerShell eingeschränkt?): {0}", e.removePrefix("WINRT").trim())
                        else -> e
                    },
                )
            }
            language = parsed.engine ?: language
            parsed.pages.forEach { (i, page) -> out[b * BATCH + i] = page }
        }
        return out.toList()
    }

    private fun run(script: String, timeoutS: Long): String {
        val encoded = Base64.getEncoder().encodeToString(script.toByteArray(Charsets.UTF_16LE))
        val powershell = File(System.getenv("SystemRoot") ?: "C:\\Windows", "System32\\WindowsPowerShell\\v1.0\\powershell.exe").takeIf { it.isFile }?.path ?: "powershell.exe"
        val process = ProcessBuilder(powershell, "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-EncodedCommand", encoded)
            .redirectErrorStream(true)
            .start()
        process.outputStream.close()
        val text = StringBuilder()
        val reader = Thread {
            runCatching { process.inputStream.bufferedReader(Charsets.UTF_8).forEachLine { text.appendLine(it) } }
        }.apply { isDaemon = true; start() }
        if (!process.waitFor(timeoutS, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            throw IllegalStateException("Die Texterkennung von Windows hat zu lange gebraucht.")
        }
        reader.join(5000)
        return text.toString()
    }
}
