package io.github.thepytales.llmhub.agent

import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.data.BudgetRow
import io.github.thepytales.llmhub.data.Funders
import io.github.thepytales.llmhub.data.Proposal
import io.github.thepytales.llmhub.data.ProposalCheck
import io.github.thepytales.llmhub.data.ProposalChecks
import io.github.thepytales.llmhub.data.ProposalDeadline
import io.github.thepytales.llmhub.data.ProposalReview
import io.github.thepytales.llmhub.data.ProposalSection
import io.github.thepytales.llmhub.data.WorkPackage
import io.github.thepytales.llmhub.net.s
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * Antrags-Werkstatt (AP-21) for the agent: from the call it saves checklist, deadlines, outline with limits, Arbeitspakete
 * and budget; it writes sections (the watcher counts them), reads the state with the Abgabe-Check and notes trial reviews.
 * Offered while the tool is switched on and always in the mode „Antrag“.
 */
class ProposalTools(private val c: AppContainer) : ToolProvider {
    private fun arr(items: Map<String, String>, required: List<String> = emptyList()) = buildJsonObject {
        put("type", "array")
        putJsonObject("items") {
            put("type", "object")
            putJsonObject("properties") {
                items.forEach { (k, type) ->
                    putJsonObject(k) {
                        if (type == "number[]") {
                            put("type", "array")
                            putJsonObject("items") { put("type", "number") }
                        } else put("type", type)
                    }
                }
            }
            put("required", JsonArray(required.map { JsonPrimitive(it) }))
        }
    }

    override val defs = listOf(
        ToolDef(
            SAVE,
            "Legt einen Antrag in der Antrags-Werkstatt an (ohne id) oder ändert ihn. Übergib nur, was sich ändert; eine übergebene Liste ersetzt " +
                "die bisherige (bei sections bleibt der Text gleichnamiger Abschnitte erhalten). Ohne sections bekommt ein neuer Antrag die Gliederung des Förderers.",
            listOf(
                Param("id", "Antrags-ID (leer: neuer Antrag)", required = false),
                Param("title", "Titel bzw. Akronym des Vorhabens", required = false),
                Param("funder", "Förderer: dfg, bmbf, eu, sab, tud oder other", required = false),
                Param("program", "Programm bzw. Ausschreibung, z. B. „Sachbeihilfe“, „Horizon Europe RIA“", required = false),
                Param("call_summary", "Was die Ausschreibung verlangt, kurz (Ziel, Förderfähiges, Bewertungskriterien, Formalien)", required = false),
                Param("call_source", "Datei oder URL der Ausschreibung", required = false),
                Param("duration_months", "Laufzeit in Monaten", required = false, type = "integer"),
                Param("page_limit", "Seitenlimit des ganzen Antrags", required = false, type = "number"),
                Param("chars_per_page", "Zeichen je Seite für den Seitenwächter (Standard 3000)", required = false, type = "integer"),
                Param("checklist", "Checkliste aus der Ausschreibung (Liste von Texten)", required = false, schema = buildJsonObject { put("type", "array"); putJsonObject("items") { put("type", "string") } }),
                Param("deadlines", "Fristen: title, date (JJJJ-MM-TT, optional mit Uhrzeit HH:MM), note", required = false, schema = arr(mapOf("title" to "string", "date" to "string", "note" to "string"), listOf("title", "date"))),
                Param("sections", "Gliederung: title, guidance (was hinein gehört), limit_chars, limit_words, limit_pages", required = false,
                    schema = arr(mapOf("title" to "string", "guidance" to "string", "limit_chars" to "integer", "limit_words" to "integer", "limit_pages" to "number"), listOf("title"))),
                Param("work_packages", "Arbeitspakete: title, lead, start_month (1 = erster Monat), months, deliverables", required = false,
                    schema = arr(mapOf("title" to "string", "lead" to "string", "start_month" to "integer", "months" to "integer", "deliverables" to "string"), listOf("title", "start_month", "months"))),
                Param("budget", "Finanzplan: category, item, amounts (Euro je Projektjahr), note", required = false,
                    schema = arr(mapOf("category" to "string", "item" to "string", "amounts" to "number[]", "note" to "string"), listOf("category", "item", "amounts"))),
                Param("overhead_percent", "Pauschale auf die direkten Kosten in Prozent (DFG 22, BMBF 20, EU 25; 0 = keine)", required = false, type = "number"),
                Param("dmp", "Umgang mit Forschungsdaten (DMP), kurz", required = false),
                Param("ethics", "Ethische und rechtliche Aspekte, kurz", required = false),
            ),
            writes = true,
        ),
        ToolDef(
            WRITE,
            "Schreibt den Text eines Abschnitts des Antrags (ersetzt ihn oder hängt an). Der Zeichen-/Seitenwächter zählt danach nach.",
            listOf(
                Param("id", "Antrags-ID"),
                Param("section", "Titel oder Nummer des Abschnitts (1 = erster)"),
                Param("text", "Der Text (Markdown)"),
                Param("append", "\"true\": anhängen statt ersetzen", required = false),
            ),
            writes = true,
        ),
        ToolDef(
            READ,
            "Liest einen Antrag: Gliederung mit Umfang und Grenzen, Arbeitspakete, Budget, Fristen und das Ergebnis des Abgabe-Checks. Ohne id: Liste der Anträge.",
            listOf(Param("id", "Antrags-ID (leer: Liste)", required = false), Param("full", "\"true\": auch die Texte der Abschnitte", required = false)),
        ),
        ToolDef(
            REVIEW,
            "Hält das Ergebnis eines Probelaufs mit einer Gutachter-Rolle am Antrag fest (Stärken, Schwächen, Note, wichtigste Änderungen).",
            listOf(Param("id", "Antrags-ID"), Param("summary", "Zusammenfassung des Gutachtens")),
            writes = true,
        ),
    )

    override fun parallelSafe(name: String) = name == READ

    override suspend fun run(name: String, args: JsonObject, ctx: RepoContext): ToolOutput = when (name) {
        SAVE -> save(args, ctx)
        WRITE -> {
            val p = find(args) ?: return notFound(args)
            val key = args.req("section").trim()
            val idx = key.toIntOrNull()?.minus(1)?.takeIf { it in p.sections.indices }
                ?: p.sections.indexOfFirst { it.title.equals(key, ignoreCase = true) }.takeIf { it >= 0 }
                ?: p.sections.indexOfFirst { it.title.contains(key, ignoreCase = true) || key.contains(it.title, ignoreCase = true) }.takeIf { it >= 0 }
                ?: return ToolOutput("FEHLER: Kein Abschnitt „$key“. Abschnitte: " + p.sections.mapIndexed { i, s -> "${i + 1} ${s.title}" }.joinToString("; "))
            val append = args.s("append") == "true"
            val next = c.proposals.change(p.id) { q ->
                q.copy(sections = q.sections.mapIndexed { i, s -> if (i == idx) s.copy(text = if (append && s.text.isNotBlank()) s.text.trimEnd() + "\n\n" + args.req("text").trim() else args.req("text").trim()) else s })
            } ?: p
            val s = next.sections[idx]
            val count = ProposalChecks.count(s.text, next.charsPerPage)
            val over = ProposalChecks.overLimit(s, next.charsPerPage)
            ToolOutput(
                "Abschnitt „${s.title}“ gespeichert: ${ProposalChecks.num(count.chars)} Zeichen, ${ProposalChecks.num(count.words)} Wörter, etwa ${ProposalChecks.pages(count.pages)} Seiten" +
                    (ProposalChecks.limitLabel(s)?.let { " ($it)" } ?: "") + (over?.let { ". ZU LANG: $it – bitte kürzen." } ?: "."),
            )
        }
        REVIEW -> {
            val p = find(args) ?: return notFound(args)
            c.proposals.change(p.id) { it.copy(reviews = it.reviews + ProposalReview(chatId = ctx.chatId, summary = args.req("summary").trim())) }
            ToolOutput("Probelauf am Antrag „${p.title}“ festgehalten.")
        }
        else -> {
            val id = args.s("id")?.trim().orEmpty()
            if (id.isEmpty()) {
                val all = c.proposals.current
                ToolOutput(if (all.isEmpty()) "Noch keine Anträge." else all.joinToString("\n") { "- ${it.title} (${it.funderInfo.name} ${it.program}) – $ID_LABEL ${it.id}" })
            } else find(args)?.let { ToolOutput(describe(it, args.s("full") == "true")) } ?: notFound(args)
        }
    }

    private fun find(args: JsonObject): Proposal? = args.s("id")?.trim()?.let { id -> c.proposals.get(id) ?: c.proposals.current.firstOrNull { it.id.startsWith(id) } }

    private fun notFound(args: JsonObject) = ToolOutput("FEHLER: Kein Antrag mit der ID ${args.s("id")}. Mit $READ ohne id die Liste holen.")

    private fun save(args: JsonObject, ctx: RepoContext): ToolOutput {
        val existing = find(args)
        if (existing == null && !args.s("id").isNullOrBlank()) return notFound(args)
        val funder = args.s("funder")?.let { Funders.byId(it) }
        val base = existing ?: Funders.newProposal(args.s("title") ?: "Neuer Antrag", funder ?: Funders.OTHER, ctx.chatId)
        var p = base
        args.s("title")?.let { p = p.copy(title = it.trim()) }
        if (funder != null && existing != null && funder.id != existing.funder) p = p.copy(funder = funder.id, overheadPercent = funder.overheadPercent, pageLimit = funder.pageLimit ?: p.pageLimit)
        args.s("program")?.let { p = p.copy(program = it.trim()) }
        args.s("call_summary")?.let { p = p.copy(callSummary = it.trim()) }
        args.s("call_source")?.let { p = p.copy(callSource = it.trim()) }
        args.num("duration_months")?.let { p = p.copy(durationMonths = it.toInt().coerceIn(1, 120)) }
        args.num("page_limit")?.let { p = p.copy(pageLimit = it.takeIf { v -> v > 0 }) }
        args.num("chars_per_page")?.let { p = p.copy(charsPerPage = it.toInt().coerceIn(500, 10_000)) }
        args.num("overhead_percent")?.let { p = p.copy(overheadPercent = it.takeIf { v -> v > 0 }) }
        args.s("dmp")?.let { p = p.copy(dmp = it.trim()) }
        args.s("ethics")?.let { p = p.copy(ethics = it.trim()) }
        list(args["checklist"])?.let { l ->
            val done = p.checklist.filter { it.done }.map { it.text.lowercase() }.toSet()
            p = p.copy(checklist = l.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { t -> t.isNotEmpty() } }.map { ProposalCheck(text = it, done = it.lowercase() in done) })
        }
        list(args["deadlines"])?.let { l ->
            p = p.copy(deadlines = l.mapNotNull { it as? JsonObject }.mapNotNull { o -> o.s("title")?.let { t -> ProposalDeadline(title = t, date = o.s("date").orEmpty(), note = o.s("note").orEmpty()) } })
        }
        list(args["sections"])?.let { l ->
            val old = p.sections
            p = p.copy(sections = l.mapNotNull { it as? JsonObject }.mapNotNull { o ->
                val t = o.s("title")?.trim() ?: return@mapNotNull null
                val prev = old.firstOrNull { it.title.equals(t, ignoreCase = true) }
                ProposalSection(
                    id = prev?.id ?: java.util.UUID.randomUUID().toString(), title = t, guidance = o.s("guidance") ?: prev?.guidance.orEmpty(),
                    limitChars = o.num("limit_chars")?.toInt() ?: prev?.limitChars, limitWords = o.num("limit_words")?.toInt() ?: prev?.limitWords,
                    limitPages = o.num("limit_pages") ?: prev?.limitPages, text = prev?.text.orEmpty(),
                )
            })
        }
        list(args["work_packages"])?.let { l ->
            p = p.copy(workPackages = l.mapNotNull { it as? JsonObject }.mapNotNull { o ->
                WorkPackage(
                    title = o.s("title") ?: return@mapNotNull null, lead = o.s("lead").orEmpty(),
                    start = (o.num("start_month") ?: 1.0).toInt().coerceAtLeast(1), months = (o.num("months") ?: 6.0).toInt().coerceAtLeast(1),
                    deliverables = o.s("deliverables").orEmpty(),
                )
            })
        }
        list(args["budget"])?.let { l ->
            p = p.copy(budget = l.mapNotNull { it as? JsonObject }.mapNotNull { o ->
                BudgetRow(
                    category = o.s("category") ?: return@mapNotNull null, item = o.s("item").orEmpty(),
                    amounts = (o["amounts"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.replace(',', '.')?.toDoubleOrNull() }.orEmpty(),
                    note = o.s("note").orEmpty(),
                )
            })
        }
        if (existing == null) c.proposals.upsert(p.copy(updatedAt = System.currentTimeMillis())) else c.proposals.change(p.id) { p }
        val saved = c.proposals.get(p.id) ?: p
        return ToolOutput((if (existing == null) "Antrag angelegt" else "Antrag gespeichert") + ". $ID_LABEL ${saved.id}\n\n" + describe(saved, false))
    }

    /** The state for the model: outline with counts, Arbeitspakete, budget, deadlines, Abgabe-Check. */
    private fun describe(p: Proposal, full: Boolean): String = buildString {
        append("„${p.title}“ – ${p.funderInfo.name} ${p.program}, ${p.durationMonths} Monate").append(p.pageLimit?.let { ", Seitenlimit ${ProposalChecks.pages(it)}" }.orEmpty()).append('\n')
        if (p.callSummary.isNotBlank()) append("Ausschreibung: ").append(p.callSummary.take(1500)).append('\n')
        append("\nGliederung:\n")
        p.sections.forEachIndexed { i, s ->
            val n = ProposalChecks.count(s.text, p.charsPerPage)
            append("${i + 1}. ${s.title} – ${ProposalChecks.num(n.chars)} Zeichen")
            ProposalChecks.limitLabel(s)?.let { append(" ($it)") }
            ProposalChecks.overLimit(s, p.charsPerPage)?.let { append(" ZU LANG") }
            if (s.guidance.isNotBlank()) append(" · ").append(s.guidance)
            append('\n')
            if (full && s.text.isNotBlank()) append(s.text.trim()).append("\n\n")
        }
        if (p.workPackages.isNotEmpty()) append("\nArbeitspakete: ").append(p.workPackages.mapIndexed { i, w -> "AP ${i + 1} ${w.title} (M${w.start}–M${w.start + w.months - 1})" }.joinToString("; ")).append('\n')
        if (p.budget.isNotEmpty()) append("Budget: ${ProposalChecks.euro(p.directCosts)} direkte Kosten").append(if (p.overhead > 0) " + ${ProposalChecks.euro(p.overhead)} Pauschale" else "").append('\n')
        if (p.deadlines.isNotEmpty()) append("Fristen: ").append(p.deadlines.joinToString("; ") { "${it.title} ${it.date}" }).append('\n')
        append("\nAbgabe-Check:\n")
        ProposalChecks.check(p).forEach { f -> append(when (f.level) { ProposalChecks.Level.OK -> "✓ "; ProposalChecks.Level.WARN -> "! "; ProposalChecks.Level.ERROR -> "✗ " }).append(f.text).append('\n') }
    }

    private fun list(e: JsonElement?): JsonArray? = when (e) {
        is JsonArray -> e
        // Some models send the list as a JSON string.
        is JsonPrimitive -> e.contentOrNull?.let { runCatching { io.github.thepytales.llmhub.net.JSON.parseToJsonElement(it) as? JsonArray }.getOrNull() }
        else -> null
    }

    private fun JsonObject.num(key: String): Double? = (this[key] as? JsonPrimitive)?.contentOrNull?.replace(',', '.')?.toDoubleOrNull()

    companion object {
        const val SAVE = "save_proposal"
        const val WRITE = "write_proposal_section"
        const val READ = "read_proposal"
        const val REVIEW = "save_proposal_review"
        const val ID_LABEL = "Antrags-ID:"

        fun idIn(result: String): String? = Regex(Regex.escape(ID_LABEL) + "\\s*([0-9a-f-]{8,})").find(result)?.groupValues?.get(1)

        const val MODE_PROMPT = "ANTRAGS-MODUS (AP-21): Du hilfst, einen Drittmittelantrag vorzubereiten, in der Antrags-Werkstatt der App.\n" +
            "1. Ausschreibung: Liest du eine angehängte Ausschreibung (PDF) oder eine URL (fetch_url), ziehe heraus: Förderer und Programm, Ziel, " +
            "was gefördert wird, Bewertungskriterien, Formalien (Sprache, Umfang, Seiten- und Zeichengrenzen je Teil), Fristen, Unterlagen. Lege den Antrag mit " +
            "save_proposal an: checklist (konkrete, abhakbare Punkte), deadlines (JJJJ-MM-TT), sections mit den Grenzen der Ausschreibung, duration_months. " +
            "Erfinde keine Grenzen oder Fristen – was nicht in der Ausschreibung steht, lässt du leer und sagst es.\n" +
            "2. Kläre mit ask_user in kurzen Runden, was fehlt (Idee, Ziele, Partner, Laufzeit, Personal). Plane Arbeitspakete (work_packages, " +
            "Monate innerhalb der Laufzeit) und den Finanzplan (budget, Euro je Projektjahr; Personal nach den Sätzen des Förderers, die du nicht kennst, " +
            "als Annahme kennzeichnen).\n" +
            "3. Schreibe Abschnitte mit write_proposal_section – sachlich, präzise, im Stil des Förderers, innerhalb der Grenzen; der Wächter meldet, wenn es zu lang ist.\n" +
            "4. Probelauf: Auf Wunsch begutachtest du den Antrag in der Rolle einer Gutachterin/eines Gutachters des Förderers nach dessen Kriterien " +
            "(read_proposal mit full=true): Stärken, Schwächen, Fragen, Gesamturteil, die fünf wichtigsten Änderungen – und hältst es mit save_proposal_review fest.\n" +
            "5. Nenne am Ende den nächsten Schritt; der Abgabe-Check steht unter Anträge in der App."

        /** The prompt of a trial review started from the screen. */
        fun reviewPrompt(p: Proposal) = "Mach einen Probelauf: Begutachte den Antrag „${p.title}“ ($ID_LABEL ${p.id}) in der Rolle einer erfahrenen Gutachterin " +
            "der ${p.funderInfo.name} (${p.program.ifBlank { "Programm laut Antrag" }}) nach deren Kriterien. Lies ihn mit read_proposal (full=true). Gliedere: Gesamteindruck, " +
            "Stärken, Schwächen, offene Fragen, Bewertung, die fünf wichtigsten Änderungen. Halte das Ergebnis mit save_proposal_review fest."
    }
}

/** Changes a proposal and notes when (the ListStore keeps the order). */
fun io.github.thepytales.llmhub.data.ListStore<Proposal>.change(id: String, transform: (Proposal) -> Proposal): Proposal? {
    if (get(id) == null) return null
    update(id) { transform(it).copy(updatedAt = System.currentTimeMillis()) }
    return get(id)
}
