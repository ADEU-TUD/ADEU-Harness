package io.github.thepytales.llmhub

import android.content.Context
import io.github.thepytales.llmhub.update.UpdateApply
import io.github.thepytales.llmhub.update.UpdateInfo
import io.github.thepytales.llmhub.update.UpdatePlatform
import io.github.thepytales.llmhub.update.Updates
import io.github.thepytales.llmhub.update.Versions
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Automatic updates: version.json, version numbers, unpacking the Windows ZIP and swapping the app folder. */
class UpdatesTest {
    @Test
    fun versionJsonOfTheUpdateRepository() {
        val info = UpdateInfo.parse(
            """{"version": "1.0.37", "published": "2026-10-26", "notes": "Projektordner mit „Ausgabe“",
               "windows": {"file": "ADEU-LLM-1.0.37-Windows.zip", "url": "https://example.org/w.zip", "sha256": "AB12", "size": 5},
               "android": {"file": "ADEU-LLM-1.0.37.apk", "sha256": "cd34", "size": 7}, "neu": true}""",
        )
        assertEquals("1.0.37", info.version)
        assertEquals("Projektordner mit „Ausgabe“", info.notes)
        assertEquals("ab12", info.files["windows"]!!.sha256)
        assertEquals("https://example.org/w.zip", info.files["windows"]!!.url)
        assertNull(info.files["android"]!!.url, "Im Ordner auf dem Netzlaufwerk gibt es keine Adresse")
        assertFailsWith<IllegalStateException> { UpdateInfo.parse("""{"notes": "ohne Version"}""") }
    }

    @Test
    fun versionNumbersAreComparedAsNumbers() {
        assertTrue(Versions.newer("1.0.10", "1.0.9"))
        assertTrue(Versions.newer("1.1.0", "1.0.99"))
        assertFalse(Versions.newer("1.0.36", "1.0.36"))
        assertFalse(Versions.newer("1.0.35", "1.0.36"))
        assertTrue(Versions.newer("v1.0.37", "1.0.36-dev"))
        assertEquals(0, Versions.compare("1.0", "1.0.0"))
    }

    @Test
    fun theZipIsUnpackedNextToTheAppAndChecked() {
        val base = Files.createTempDirectory("adeu-update").toFile()
        if (System.getProperty("adeu.data").isNullOrBlank()) System.setProperty("adeu.data", File(base, "daten").absolutePath)
        try {
            val context = Context(File(base, "files").apply { mkdirs() }, File(base, "cache").apply { mkdirs() })
            val info = UpdateInfo("1.0.99", "", null, emptyMap())
            val zip = File(base, "update.zip")
            zip(zip, "ADEU LLM/ADEU LLM.exe" to "exe", "ADEU LLM/app/ADEU-LLM-Desktop.jar" to "jar", "ADEU LLM/runtime/bin/jvm.dll" to "dll", "../ausserhalb.txt" to "böse")
            val app = UpdatePlatform.prepare(context, zip, info) {}
            assertEquals("jar", File(app, "app/ADEU-LLM-Desktop.jar").readText())
            assertFalse(File(app.parentFile.parentFile, "ausserhalb.txt").exists(), "Nichts außerhalb des Zielordners")
            assertFalse(zip.exists(), "Die ZIP wird nach dem Entpacken gelöscht")
            assertEquals(app.canonicalFile, UpdatePlatform.prepared(context, info)!!.canonicalFile, "Beim nächsten Start sofort bereit")

            val broken = File(base, "kaputt.zip").also { zip(it, "ADEU LLM/app/x.jar" to "x") }
            assertFailsWith<IllegalStateException> { UpdatePlatform.prepare(context, broken, info.copy(version = "1.0.98")) {} }
            assertNull(UpdatePlatform.prepared(context, info.copy(version = "1.0.98")))
            assertEquals(64, Updates.sha256(app.resolve("app/ADEU-LLM-Desktop.jar")).length)
        } finally {
            base.deleteRecursively()
        }
    }

    @Test
    fun swappingTheAppFolderKeepsUserFilesAndCanBeUndone() {
        val base = Files.createTempDirectory("adeu-tausch").toFile()
        try {
            val target = File(base, "ADEU LLM").apply { mkdirs() }
            File(target, "ADEU LLM.exe").writeText("alt-exe")
            File(target, "app").mkdirs(); File(target, "app/alt.jar").writeText("alt")
            File(target, "runtime").mkdirs(); File(target, "runtime/jvm.dll").writeText("alt-dll")
            File(target, "notiz.txt").writeText("bleibt")
            val source = File(base, "neu/ADEU LLM").apply { mkdirs() }
            File(source, "ADEU LLM.exe").writeText("neu-exe")
            File(source, "app").mkdirs(); File(source, "app/neu.jar").writeText("neu")
            File(source, "runtime").mkdirs(); File(source, "runtime/jvm.dll").writeText("neu-dll")
            File(source, ".fertig").writeText("1.0.99")

            // Copying fails half way: everything is as before.
            assertFailsWith<IOException> {
                UpdateApply.swap(source, target, copy = { from, to -> if (from.name == "runtime") throw IOException("Datenträger voll") else from.copyRecursively(to) })
            }
            assertEquals("alt-exe", File(target, "ADEU LLM.exe").readText())
            assertEquals(listOf("alt.jar"), File(target, "app").list()!!.toList())
            assertEquals("alt-dll", File(target, "runtime/jvm.dll").readText())

            UpdateApply.swap(source, target)
            assertEquals("neu-exe", File(target, "ADEU LLM.exe").readText())
            assertEquals(listOf("neu.jar"), File(target, "app").list()!!.toList(), "Alte Jars sind weg")
            assertEquals("neu-dll", File(target, "runtime/jvm.dll").readText())
            assertEquals("bleibt", File(target, "notiz.txt").readText())
            assertFalse(File(target, ".fertig").exists())
            assertTrue(File(target, "${UpdateApply.BACKUP}/app/alt.jar").exists(), "Die alte Version liegt bis zum nächsten Start daneben")
        } finally {
            base.deleteRecursively()
        }
    }

    private fun zip(file: File, vararg entries: Pair<String, String>) {
        ZipOutputStream(file.outputStream()).use { z ->
            for ((name, text) in entries) {
                z.putNextEntry(ZipEntry(name))
                z.write(text.toByteArray())
                z.closeEntry()
            }
        }
    }
}
