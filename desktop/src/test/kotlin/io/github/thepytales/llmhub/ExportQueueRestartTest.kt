package io.github.thepytales.llmhub

import io.github.thepytales.llmhub.video.ExportQueue
import io.github.thepytales.llmhub.video.VideoProject
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Videoexport-Warteschlange übersteht einen Neustart (AP-81). */
class ExportQueueRestartTest {
    @Test
    fun waitingAndRunningExportsComeBackAfterARestart() {
        val dir = Files.createTempDirectory("queue").toFile()
        try {
            val store = File(dir, "video-exporte.json")
            val half = File(dir, "Vorlesung 3.mp4").apply { writeText("halb geschrieben") }
            // What the last run wrote: one entry was running, one waiting.
            val saved = listOf(
                ExportQueue.Saved("a1", VideoProject(title = "Vorlesung 3"), ExportQueue.Kind.VIDEO, "opal", half.absolutePath),
                ExportQueue.Saved("a2", VideoProject(title = "Vorlesung 3"), ExportQueue.Kind.CHAPTERS, "opal", File(dir, "Kapitel.txt").absolutePath),
            )
            store.writeText(Json.encodeToString(ListSerializer(ExportQueue.Saved.serializer()), saved))
            assertEquals(2, ExportQueue.restore(store, resume = false))
            val back = ExportQueue.entries.value.filter { it.id in setOf("a1", "a2") }
            assertTrue(back.all { it.status == ExportQueue.Status.INTERRUPTED }, back.toString())
            assertFalse(half.exists(), "die halb geschriebene Datei wird entfernt")
            assertEquals(0, ExportQueue.busy, "unterbrochene Exporte halten das Beenden nicht auf")
            // Restoring twice does not double them.
            assertEquals(0, ExportQueue.restore(store, resume = false))
            // Cancelled ones are no longer kept for the next start.
            ExportQueue.cancel("a2")
            assertTrue("\"id\":\"a2\"" !in store.readText() && "\"id\":\"a1\"" in store.readText(), store.readText())
            ExportQueue.cancel("a1")
            assertFalse(store.exists(), "nichts mehr offen – die Datei verschwindet")
            ExportQueue.clearFinished()
        } finally {
            ExportQueue.store = null
            dir.deleteRecursively()
        }
    }
}
