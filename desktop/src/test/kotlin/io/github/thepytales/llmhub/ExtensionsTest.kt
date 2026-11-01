package io.github.thepytales.llmhub

import android.content.Context
import io.github.thepytales.llmhub.desktop.Extensions
import io.github.thepytales.llmhub.update.UpdateInfo
import io.github.thepytales.llmhub.update.Updates
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Nachladbare Erweiterungen (AP-25): version.json lists them, the app sets them up, updates and removes them. */
class ExtensionsTest {
    private val base = Files.createTempDirectory("adeu-ext").toFile()
    private val context = Context(File(base, "files").apply { mkdirs() }, File(base, "cache").apply { mkdirs() })
    private val source = File(base, "quelle").apply { mkdirs() }
    private val oldRoot = Extensions.root

    @AfterTest
    fun cleanUp() {
        Extensions.root = oldRoot
        base.deleteRecursively()
    }

    private fun pack(name: String, files: Map<String, String>): File {
        val zip = File(source, name)
        ZipOutputStream(zip.outputStream()).use { z ->
            files.forEach { (path, text) -> z.putNextEntry(ZipEntry(path)); z.write(text.toByteArray()); z.closeEntry() }
        }
        return zip
    }

    private fun publish(version: String, zip: File) {
        File(source, "version.json").writeText(
            """{"version": "1.0.1", "notes": "", "windows": {"file": "x.zip", "sha256": "00", "size": 1},
              "extensions": [{"id": "videoeditor", "name": "Videoeditor-Paket", "version": "$version", "description": "Test",
                              "file": "${zip.name}", "sha256": "${Updates.sha256(zip)}", "size": ${zip.length()}}]}""",
        )
    }

    @Test
    fun versionJsonListsExtensions() {
        val info = UpdateInfo.parse(
            """{"version": "1.0.50", "extensions": [{"id": "videoeditor", "version": "2", "file": "Lora-Videoeditor-2.zip",
                "url": "https://example.org/Lora-Videoeditor-2.zip", "sha256": "ABC", "size": 42}, {"id": "kaputt"}]}""",
        )
        val e = info.extensions.single()
        assertEquals("videoeditor", e.id)
        assertEquals("2", e.version)
        assertEquals("abc", e.file.sha256)
        assertEquals(42, e.file.size)
        assertTrue(UpdateInfo.parse("""{"version": "1.0.1"}""").extensions.isEmpty(), "alte version.json ohne Erweiterungen")
    }

    @Test
    fun setsUpUpdatesAndRemovesFromAFolderSource() = runBlocking {
        Extensions.root = File(base, "tools")
        Updates.setSource(context, source.absolutePath)
        val v1 = pack("Lora-Videoeditor-1.zip", mapOf("ffprobe.exe" to "probe", "rnnoise/sh.rnnn" to "modell"))
        publish("1", v1)
        assertFalse(Extensions.installed("videoeditor"))
        assertNull(Extensions.install(context, "videoeditor"))
        assertEquals("1", Extensions.version("videoeditor"))
        assertEquals("modell", Extensions.file("videoeditor", "rnnoise/sh.rnnn")?.readText())
        assertTrue(Extensions.states.value["videoeditor"] is Extensions.State.Installed)

        // The update check brings it up to date.
        val v2 = pack("Lora-Videoeditor-2.zip", mapOf("ffprobe.exe" to "probe2", "rnnoise/sh.rnnn" to "modell2"))
        publish("2", v2)
        Extensions.onUpdateInfo(context, Updates.readInfo(context))
        assertEquals("2", Extensions.version("videoeditor"))
        assertEquals("probe2", Extensions.file("videoeditor", "ffprobe.exe")?.readText())

        // A changed file is refused (checksum), the installed version stays.
        File(source, "version.json").writeText(File(source, "version.json").readText().replace("\"2\"", "\"3\""))
        v2.appendText("verändert")
        assertNotNull(Extensions.install(context, "videoeditor"))
        assertEquals("2", Extensions.version("videoeditor"))

        Extensions.remove("videoeditor")
        assertFalse(Extensions.installed("videoeditor"))
        assertNull(Extensions.file("videoeditor", "ffprobe.exe"))
    }

    @Test
    fun refusesPathsOutsideItsFolder() {
        Extensions.root = File(base, "tools")
        val evil = pack("boese.zip", mapOf("../../ausserhalb.txt" to "x"))
        val failed = runCatching { Extensions.unpack(evil, "videoeditor", "1") }
        assertTrue(failed.isFailure)
        assertFalse(File(base, "ausserhalb.txt").exists())
    }
}
