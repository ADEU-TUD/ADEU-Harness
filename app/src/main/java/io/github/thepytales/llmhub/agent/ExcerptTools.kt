package io.github.thepytales.llmhub.agent

import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.docs.LiveDoc
import io.github.thepytales.llmhub.net.s
import io.github.thepytales.llmhub.research.ExcerptSchema
import io.github.thepytales.llmhub.research.Excerpts
import io.github.thepytales.llmhub.research.Reference
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

/**
 * save_excerpt (AP-09): checks the page numbers of the excerpt against the attached PDF, then attaches it to the
 * literature entry (found by id, DOI or title, otherwise created) and saves it as a document.
 */
class ExcerptTools(private val c: AppContainer) : ToolProvider {
    override val defs = listOf(
        ToolDef(
            NAME,
            "Speichert ein fertiges Exzerpt: prüft jede Seitenangabe der Zitate gegen das angehängte PDF, hängt das Exzerpt an den " +
                "Literatureintrag (neu angelegt, falls es ihn nicht gibt) und legt es unter Dokumente ab. Meldet falsche Seiten – dann " +
                "korrigieren und erneut aufrufen.",
            listOf(
                Param("title", "Titel des Werks"),
                Param("markdown", "Das Exzerpt in Markdown mit Zitaten wie „…“ (S. 12)"),
                Param("schema", "Textart: " + ExcerptSchema.entries.joinToString(", ") { it.id }, required = false),
                Param("doi", "DOI des Werks, wenn bekannt", required = false),
                Param("reference_id", "Id des Literatureintrags, wenn im Auftrag genannt", required = false),
                Param("force", "true: trotz gemeldeter Fehler speichern (die Fehler werden im Exzerpt vermerkt)", required = false, type = "boolean"),
            ),
            writes = true,
        ),
    )

    override suspend fun run(name: String, args: JsonObject, ctx: RepoContext): ToolOutput {
        val title = args.req("title").trim()
        val markdown = args.req("markdown").trim()
        if (markdown.length < 80) return ToolOutput("FEHLER: Das Exzerpt ist zu kurz. Übergib den vollständigen Text in markdown.")
        val force = (args["force"] as? JsonPrimitive)?.let { it.booleanOrNull ?: it.content.equals("true", true) } ?: false
        val sources = ctx.files.map { EvidenceSource(it.name, it.text, EvidenceSource.Kind.FILE, path = it.path) }
        val check = Excerpts.check(markdown, sources)
        if (!check.ok && !force) {
            return ToolOutput(
                "FEHLER: Seitenangaben stimmen nicht (${check.summary()}):\n" + check.problems.joinToString("\n") { "- $it" } +
                    "\nKorrigiere Zitat oder Seite (die Seiten stehen im Text als [Seite N]) und rufe save_excerpt erneut auf.",
            )
        }
        val note = Excerpts.withCheckNote(markdown, check)
        val doi = args.s("doi")?.trim()?.removePrefix("https://doi.org/")?.removePrefix("doi:")?.trim().orEmpty()
        val existing = Excerpts.findReference(c.literature.current, args.s("reference_id"), doi, title)
        val ref = existing ?: c.literature.addAll(listOf(Reference(title = title, doi = doi, source = "Exzerpt", folderId = ctx.folderId))).first()
        val schema = ExcerptSchema.parse(args.s("schema"))
        val doc = LiveDoc.create("Exzerpt: $title", note, "KI · " + (ctx.model ?: "Agent"), ctx.chatId).copy(vault = c.docs.defaultVault)
        c.docs.save(doc)
        c.literature.update(ref.id) { it.copy(excerpt = note, excerptDocId = doc.id, excerptAt = System.currentTimeMillis()) }
        return ToolOutput(
            "Exzerpt gespeichert" + (schema?.let { " (${it.label})" } ?: "") + ": am Literatureintrag „${ref.title.ifBlank { title }}“" +
                (if (existing == null) " (neu angelegt)" else "") + " und als Dokument „${doc.title}“. Seitenangaben: ${check.summary()}.",
        )
    }

    companion object {
        const val NAME = "save_excerpt"
    }
}
