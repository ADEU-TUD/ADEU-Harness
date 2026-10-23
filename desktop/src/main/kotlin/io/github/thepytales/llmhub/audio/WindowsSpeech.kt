package io.github.thepytales.llmhub.audio

import android.content.Context
import io.github.thepytales.llmhub.desktop.Paths
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.math.ln
import kotlin.math.roundToInt

/** The offline voices of Windows (System.Speech via PowerShell – part of every Windows, no admin rights). */
class WindowsSpeech(private val context: Context) {
    private fun ps(s: String) = "'" + s.replace("'", "''") + "'"

    private fun process(script: String): Process {
        if (!Paths.windows) throw IllegalStateException("Die Offline-Stimmen gibt es nur unter Windows.")
        val full = "[Console]::OutputEncoding=[Text.Encoding]::UTF8; Add-Type -AssemblyName System.Speech; $script"
        return ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-Command", full)
            .redirectErrorStream(true).start()
    }

    suspend fun voices(): List<VoiceOption> = withContext(Dispatchers.IO) {
        runCatching {
            val p = process(
                "(New-Object System.Speech.Synthesis.SpeechSynthesizer).GetInstalledVoices() | Where-Object { \$_.Enabled } | " +
                    "ForEach-Object { \$_.VoiceInfo.Name + '|' + \$_.VoiceInfo.Culture.Name + '|' + \$_.VoiceInfo.Gender }"
            )
            val out = runInterruptible { p.inputStream.bufferedReader(Charsets.UTF_8).readText().also { p.waitFor() } }
            out.lines().mapNotNull { line ->
                val parts = line.trim().split('|')
                if (parts.size < 3) return@mapNotNull null
                val locale = Locale.forLanguageTag(parts[1])
                val gender = when (parts[2]) {
                    "Female" -> "weiblich"
                    "Male" -> "männlich"
                    else -> null
                }
                Triple(locale, parts[0], gender)
            }.sortedWith(compareBy({ it.first.language != "de" }, { it.second }))
                .map { (locale, name, gender) ->
                    VoiceOption(name, listOfNotNull(name.removePrefix("Microsoft ").removeSuffix(" Desktop"), locale.getDisplayName(Locale.GERMAN), gender).joinToString(" · "))
                }
        }.getOrDefault(emptyList())
    }

    /** Speaks [text] and returns when done; cancelling stops the voice. */
    suspend fun speak(text: String, voice: String, rate: Float) {
        val file = File(context.cacheDir, "speech_${System.nanoTime()}.txt").apply { writeText(text, Charsets.UTF_8) }
        // Windows speed goes from -10 to 10; 10 is about three times as fast.
        val sapiRate = (ln(rate.coerceIn(0.3f, 3f).toDouble()) / ln(3.0) * 10).roundToInt().coerceIn(-10, 10)
        val select = if (voice.isNotBlank()) "try { \$s.SelectVoice(${ps(voice)}) } catch {}; "
        else "\$de = \$s.GetInstalledVoices() | Where-Object { \$_.Enabled -and \$_.VoiceInfo.Culture.TwoLetterISOLanguageName -eq 'de' } | Select-Object -First 1; " +
            "if (\$de) { \$s.SelectVoice(\$de.VoiceInfo.Name) }; "
        val p = process(
            "\$s = New-Object System.Speech.Synthesis.SpeechSynthesizer; \$s.SetOutputToDefaultAudioDevice(); \$s.Rate = $sapiRate; " +
                select + "\$s.Speak([IO.File]::ReadAllText(${ps(file.absolutePath)}, [Text.Encoding]::UTF8))"
        )
        try {
            val output = suspendCancellableCoroutine { cont ->
                cont.invokeOnCancellation { p.destroyForcibly() }
                Thread({
                    val out = runCatching { p.inputStream.bufferedReader().readText() }.getOrDefault("")
                    p.waitFor()
                    if (cont.isActive) cont.resume(out)
                }, "windows-speech").apply { isDaemon = true }.start()
            }
            if (p.exitValue() != 0) {
                throw IllegalStateException(
                    "Die Windows-Sprachausgabe ist nicht verfügbar" +
                        (output.lineSequence().firstOrNull { it.isNotBlank() }?.let { ": ${it.take(160)}" } ?: ".")
                )
            }
        } finally {
            file.delete()
        }
    }
}
