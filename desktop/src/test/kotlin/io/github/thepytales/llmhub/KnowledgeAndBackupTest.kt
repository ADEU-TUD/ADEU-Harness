package io.github.thepytales.llmhub

import io.github.thepytales.llmhub.data.Backup
import io.github.thepytales.llmhub.data.KnowledgeStore
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KnowledgeAndBackupTest {
    private val out = File("build/test-output/knowledge-${System.nanoTime()}").apply { mkdirs() }

    @Test
    fun longTextsAreSplitIntoOverlappingPassages() {
        val text = (1..60).joinToString(" ") { "Satz Nummer $it beschreibt einen Aspekt der Prüfungsordnung." }
        val parts = KnowledgeStore.split(text)
        assertTrue(parts.size >= 3)
        assertTrue(parts.all { it.length <= KnowledgeStore.CHUNK + 1 })
        assertTrue(parts.first().endsWith("."), "Abschnitte enden an Satzgrenzen")
        assertTrue(text.startsWith(parts.first()))
    }

    @Test
    fun keywordSearchFindsTheRightPassageAndKeepsProjectsApart() {
        val store = KnowledgeStore(out)
        store.add("Modulhandbuch.pdf", "Das Modul EW-SEGY-BW 2 schließt mit einer Hausarbeit von 15 Seiten ab.\n\n" +
            "Die Anwesenheit im Seminar wird empfohlen.", "")
        store.add("Projekt.txt", "Im Projekt Classroom Management werden Videovignetten genutzt.", "projekt-1")
        val hits = store.search("Welche Prüfungsleistung hat EW-SEGY-BW 2? Hausarbeiten", listOf(""))
        assertEquals("Modulhandbuch.pdf", hits.first().doc.name)
        assertTrue("Hausarbeit" in hits.first().text)
        assertTrue(store.search("Videovignetten", listOf("")).isEmpty(), "Projektdateien nur im Projekt")
        assertEquals("Projekt.txt", store.search("Videovignetten", listOf("", "projekt-1")).first().doc.name)
        // Same name in the same collection replaces the document.
        store.add("Modulhandbuch.pdf", "Neue Fassung", "")
        assertEquals(1, store.of("").size)
        // Semantic part: the query vector decides between equally good keyword matches.
        assertTrue(KnowledgeStore.cosine(floatArrayOf(1f, 0f), listOf(1f, 0f)) > 0.99)
    }

    @Test
    fun pathsMoveToTheNewDevice() {
        val json = """{"file":"C:\\Users\\anna\\AppData\\Roaming\\ADEU LLM\\documents\\Brief.pdf","other":"x"}"""
        val moved = Backup.movePaths(json, "C:\\Users\\anna\\AppData\\Roaming\\ADEU LLM", "/data/user/0/io.github.thepytales.llmhub/files")
        assertEquals("""{"file":"/data/user/0/io.github.thepytales.llmhub/files/documents/Brief.pdf","other":"x"}""", moved)
        val back = Backup.movePaths(
            """{"file":"/data/user/0/app/files/logos/a.png"}""", "/data/user/0/app/files", "C:\\Users\\b\\AppData\\Roaming\\ADEU LLM",
        )
        assertEquals("""{"file":"C:/Users/b/AppData/Roaming/ADEU LLM/logos/a.png"}""", back)
    }
}
