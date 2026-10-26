package io.github.thepytales.llmhub

import io.github.thepytales.llmhub.agent.FieldSuggestions
import io.github.thepytales.llmhub.agent.LocalFolderTools
import io.github.thepytales.llmhub.agent.RepoContext
import io.github.thepytales.llmhub.agent.Risk
import io.github.thepytales.llmhub.agent.WorkFolder
import android.content.Context
import io.github.thepytales.llmhub.data.Folder
import io.github.thepytales.llmhub.data.Template
import io.github.thepytales.llmhub.data.TemplateKind
import io.github.thepytales.llmhub.local.Hardware
import io.github.thepytales.llmhub.local.LocalCatalog
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import java.nio.file.AccessDeniedException
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Suggestions in template forms, working folders of projects and the hardware check without JNA. */
class ProjectsAndSuggestionsTest {
    private val letter = Template(
        name = "Brief", kind = TemplateKind.PROMPT, direct = "letter",
        content = "{{Absender|Ihr Name}}\n{{Empfänger|Je Zeile}}\n{{Betreff|Worum es geht}}\n{{Text|Ihr Text}}",
    )
    private val seminar = Template(name = "Seminarplan", kind = TemplateKind.PROMPT, content = "{{Veranstaltung}} {{Semester}} {{Lernziele}}")

    @Test
    fun modelSuggestionsAreReadFromJsonAroundOtherText() {
        val answer = "Gern:\n```json\n{\"veranstaltung\": [\"Allgemeine Didaktik (Master)\", \"Allgemeine Didaktik (Master)\", \"Classroom Management\"], " +
            "\"Semester\": \"WS 2026/27\", \"Lernziele\": [\"a\", \"b\", \"c\", \"d\"], \"Unbekannt\": [\"x\"]}\n```"
        val s = FieldSuggestions.parse(answer, seminar)
        assertEquals(listOf("Allgemeine Didaktik (Master)", "Classroom Management"), s["Veranstaltung"]?.map { it.text })
        assertEquals(listOf("WS 2026/27"), s["Semester"]?.map { it.text })
        assertEquals(3, s["Lernziele"]?.size)
        assertFalse("Unbekannt" in s)
        assertTrue(FieldSuggestions.parse("Dazu weiß ich nichts.", seminar).isEmpty())
    }

    @Test
    fun theTextOfALetterAndTheSenderAreNotSuggestedByTheModel() {
        val f = letter.fields.associateBy { it.label }
        assertFalse(FieldSuggestions.wanted(letter, f.getValue("Text")))
        assertFalse(FieldSuggestions.wanted(letter, f.getValue("Absender")))
        assertTrue(FieldSuggestions.wanted(letter, f.getValue("Empfänger")))
        assertTrue(FieldSuggestions.wanted(letter, f.getValue("Betreff")))
        assertEquals("Anna Müller\nTU Dresden", FieldSuggestions.parse("{\"Empfänger\": [\"Anna Müller\\nTU Dresden\"]}", letter)["Empfänger"]?.single()?.text)
    }

    @Test
    fun workFolderWriteTest() {
        val dir = Files.createTempDirectory("arbeitsordner").toFile()
        try {
            assertNull(WorkFolder.problem(dir))
            assertTrue(dir.listFiles().orEmpty().isEmpty(), "Der Schreibtest räumt hinter sich auf")
            assertTrue(WorkFolder.problem(File(dir, "fehlt"))!!.contains("nicht erreichbar"))
            val denied = WorkFolder.explain(dir, AccessDeniedException(dir.path))
            assertTrue("Überwachten Ordnerzugriff" in denied, denied)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun filesOfAProjectLandInTheOutputOfItsFolder() {
        val base = Files.createTempDirectory("adeu-projekt").toFile()
        if (System.getProperty("adeu.data").isNullOrBlank()) System.setProperty("adeu.data", File(base, "daten").absolutePath)
        try {
            val c = AppContainer(Context(File(base, "files").apply { mkdirs() }, File(base, "cache").apply { mkdirs() }))
            val work = File(base, "Google Drive/Seminar").apply { mkdirs() }
            val project = Folder(name = "Seminar", workDir = work.path)
            c.folders.upsert(project)
            val made = File(c.documentTools.folder.apply { mkdirs() }, "Sitzungsplan.pdf").apply { writeText("v1") }

            val out = File(work, WorkFolder.OUTPUT)
            val (copy, note) = WorkFolder.deliver(c, project.id, made)!!
            assertEquals(File(out, "Sitzungsplan.pdf").absolutePath, copy!!.absolutePath)
            assertEquals("v1", copy.readText())
            assertTrue("Projektordner" in note, note)
            // A new version replaces the app's own copy …
            made.writeText("v2")
            assertEquals(copy.absolutePath, WorkFolder.deliver(c, project.id, made)!!.first!!.absolutePath)
            assertEquals("v2", copy.readText())
            // … but never a file of the user with the same name.
            val other = File(c.documentTools.folder, "Notizen.docx").apply { writeText("neu") }
            File(out, "Notizen.docx").writeText("vom Nutzer")
            assertEquals("Notizen (2).docx", WorkFolder.deliver(c, project.id, other)!!.first!!.name)
            assertEquals("vom Nutzer", File(out, "Notizen.docx").readText())
            // Files outside the app's documents (e.g. written into another shared folder) and chats without project stay as they are.
            assertNull(WorkFolder.deliver(c, project.id, File(base, "anderswo.txt").apply { writeText("x") }))
            assertNull(WorkFolder.deliver(c, null, made))
            // Folder gone (network drive disconnected): a note for the model, no copy.
            work.deleteRecursively()
            val (none, why) = WorkFolder.deliver(c, project.id, made)!!
            assertNull(none)
            assertTrue("nicht speichern" in why, why)
        } finally {
            base.deleteRecursively()
        }
    }

    @Test
    fun projectFolderAccessIsAnOption() = runBlocking {
        val base = Files.createTempDirectory("adeu-zugriff").toFile()
        if (System.getProperty("adeu.data").isNullOrBlank()) System.setProperty("adeu.data", File(base, "daten").absolutePath)
        try {
            val c = AppContainer(Context(File(base, "files").apply { mkdirs() }, File(base, "cache").apply { mkdirs() }))
            val work = File(base, "Projekt").apply { mkdirs() }
            File(work, "Notizen zum Antrag.txt").writeText("Frist: 30. November")
            val project = Folder(name = "Antrag", workDir = work.path)
            c.folders.upsert(project)
            val ctx = RepoContext(mutableListOf(), folderId = project.id)
            fun args(vararg pairs: Pair<String, String>) = JsonObject(pairs.associate { it.first to JsonPrimitive(it.second) })

            // With access the model sees the folder and its files and is told to use them …
            val prompt = c.localFolderTools.prompt(project.id)
            assertTrue("„${LocalFolderTools.PROJECT}“" in prompt && "Notizen zum Antrag.txt" in prompt, prompt)
            assertTrue("Frist" in c.localFolderTools.run("read_local_file", args("path" to "Notizen zum Antrag.txt"), ctx).text)
            // … new files in "Ausgabe" need no OK, the user's files still do.
            val newFile = args("path" to "${WorkFolder.OUTPUT}/Zusammenfassung.md", "content" to "x")
            assertNull(c.localFolderTools.risk("write_local_file", newFile, ctx))
            assertEquals(Risk.EDIT, c.localFolderTools.risk("write_local_file", args("path" to "Notizen zum Antrag.txt", "content" to "x"), ctx))
            c.localFolderTools.run("write_local_file", newFile, ctx)
            assertNull(c.localFolderTools.risk("write_local_file", newFile, ctx), "Eigene Dateien der App darf sie ohne Rückfrage ersetzen")
            File(work, "${WorkFolder.OUTPUT}/Eigene.md").writeText("vom Nutzer")
            assertEquals(Risk.EDIT, c.localFolderTools.risk("write_local_file", args("path" to "${WorkFolder.OUTPUT}/Eigene.md", "content" to "x"), ctx))

            // Without access only "Ausgabe" is there.
            c.folders.update(project.id) { it.copy(workDirAccess = false) }
            val closed = c.localFolderTools.prompt(project.id)
            assertTrue("Notizen zum Antrag.txt" !in closed && "„${LocalFolderTools.PROJECT_OUTPUT}“" in closed, closed)
            val listed = c.localFolderTools.run("list_local_files", JsonObject(emptyMap()), ctx).text
            assertTrue("Zusammenfassung.md" in listed && "Notizen zum Antrag" !in listed, listed)
            assertTrue(runCatching { c.localFolderTools.run("read_local_file", args("path" to "../Notizen zum Antrag.txt"), ctx) }.isFailure)
            assertNull(c.localFolderTools.risk("write_local_file", args("path" to "Neu.txt", "content" to "x"), ctx))
        } finally {
            base.deleteRecursively()
        }
    }

    @Test
    fun letterOfTheFormLandsInTheProjectFolder() = runBlocking {
        val base = Files.createTempDirectory("adeu-brief").toFile()
        if (System.getProperty("adeu.data").isNullOrBlank()) System.setProperty("adeu.data", File(base, "daten").absolutePath)
        try {
            val c = AppContainer(Context(File(base, "files").apply { mkdirs() }, File(base, "cache").apply { mkdirs() }))
            val work = File(base, "Projekt").apply { mkdirs() }
            val project = Folder(name = "Prüfung", workDir = work.path)
            c.folders.upsert(project)
            val values = mapOf(
                "Absender" to "Anna Muster", "Empfänger" to "Prüfungsamt\nHelmholtzstr. 10\n01069 Dresden",
                "Betreff" to "Betreff: Einsicht in Prüfungsunterlagen", "Brieftext" to "ich bitte um Einsicht am [Datum].",
            )
            val ctx = RepoContext(mutableListOf(), folderId = project.id)
            val (_, made) = c.documentTools.createDirect("letter", values, ctx)
            val placed = WorkFolder.place(c, project.id, made)
            val copy = File(placed.file!!)
            assertEquals(File(work, WorkFolder.OUTPUT).canonicalPath, copy.parentFile.canonicalPath)
            assertFalse(copy.name.contains("Betreff"), copy.name)
            // Open gaps and "Ausfüllen" work on the copy in the project folder too.
            assertEquals(listOf("Datum"), c.documentTools.openPlaceholders(copy))
            val before = copy.readBytes()
            c.documentTools.fillPlaceholders(copy, mapOf("Datum" to "3. November"))
            assertTrue(c.documentTools.openPlaceholders(copy).isEmpty())
            assertFalse(before.contentEquals(copy.readBytes()), "Die Kopie im Projektordner bekommt die ausgefüllte Fassung")
        } finally {
            base.deleteRecursively()
        }
    }

    @Test
    fun basicHardwareStillRecommendsModels() {
        // What the app knows when IT rules block the full check: memory from Java, no graphics card.
        val hw = Hardware(phone = false, ramGb = 8.0, cores = 2, limited = true)
        assertNotNull(LocalCatalog.recommended(hw).firstOrNull())
    }
}
