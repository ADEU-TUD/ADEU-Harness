package io.github.thepytales.llmhub.transcribe

import io.github.thepytales.llmhub.data.FileBackend
import io.github.thepytales.llmhub.data.ListStore
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.io.File
import java.time.Instant
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** A category of a coding scheme (qualitative content analysis after Kuckartz or Mayring). */
@Serializable
data class Code(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    /** #RRGGBB */
    val color: String = "#1E40AF",
    /** Definition: when the code applies. */
    val description: String = "",
    /** Anchor example. */
    val example: String = "",
    /** Main category this subcategory belongs to. */
    val parentId: String? = null,
)

@Serializable
data class Codebook(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val codes: List<Code> = emptyList(),
) {
    fun code(id: String) = codes.firstOrNull { it.id == id }

    /** "Haupt › Sub" */
    fun path(code: Code): String = code.parentId?.let { code(it) }?.let { "${it.name} › ${code.name}" } ?: code.name

    companion object {
        val COLORS = listOf("#1E40AF", "#B91C1C", "#047857", "#B45309", "#7C3AED", "#0E7490", "#BE185D", "#4D7C0F", "#9A3412", "#334155")

        /**
         * A coding scheme from text: one code per line, "Code: Definition"; lines indented with spaces or starting
         * with "-" under a main code are its subcodes. Also reads the tables of the template „Kategoriensystem“
         * („| Kategorie | Definition | Ankerbeispiel |“).
         */
        fun parse(name: String, text: String): Codebook {
            val codes = mutableListOf<Code>()
            var parent: Code? = null
            text.lines().forEach { raw ->
                if (raw.isBlank()) return@forEach
                val line = raw.trimEnd()
                if (line.trim().startsWith("|")) {
                    val cells = line.trim().trim('|').split('|').map { it.trim() }
                    if (cells.isEmpty() || cells.all { it.matches(Regex(":?-{2,}:?")) }) return@forEach
                    if (cells[0].lowercase() in setOf("kategorie", "code", "hauptkategorie", "name")) return@forEach
                    val c = Code(name = cells[0].trim('*'), color = COLORS[codes.size % COLORS.size], description = cells.getOrElse(1) { "" }, example = cells.getOrElse(2) { "" })
                    codes += c
                    return@forEach
                }
                if (line.trim().startsWith("#")) return@forEach
                val indented = raw.startsWith("  ") || raw.startsWith("\t") || raw.trimStart().startsWith("- ") && parent != null && raw.startsWith(" ")
                val body = line.trim().removePrefix("- ").removePrefix("* ").replace(Regex("^\\d+(\\.\\d+)*[.)]?\\s+"), "")
                val label = body.substringBefore(":").trim().trim('*')
                val def = if (':' in body) body.substringAfter(':').trim() else ""
                if (label.isEmpty()) return@forEach
                val c = Code(name = label, color = COLORS[codes.size % COLORS.size], description = def, parentId = if (indented) parent?.id else null)
                codes += c
                if (!indented) parent = c
            }
            return Codebook(name = name, codes = codes)
        }
    }
}

/** One code on one passage of a transcript, by one coder. */
data class Coding(val segmentStart: Double, val codeId: String, val coder: String, val at: Long = System.currentTimeMillis())

/** The coding of a transcript, kept in its JSON ("coding"), so it survives edits, backups and moving devices. */
data class TranscriptCoding(val codebookId: String?, val codings: List<Coding>) {
    fun coders(): List<String> = codings.map { it.coder }.distinct().sorted()

    fun at(segment: Segment, coder: String? = null): List<Coding> =
        codings.filter { kotlin.math.abs(it.segmentStart - segment.start) < 0.05 && (coder == null || it.coder == coder) }

    fun toggle(segment: Segment, codeId: String, coder: String): TranscriptCoding {
        val existing = at(segment, coder).firstOrNull { it.codeId == codeId }
        return copy(codings = if (existing != null) codings - existing else codings + Coding(segment.start, codeId, coder))
    }

    companion object {
        fun of(t: Transcript): TranscriptCoding {
            val o = t.raw["coding"] as? JsonObject ?: return TranscriptCoding(null, emptyList())
            val list = (o["codings"] as? JsonArray).orEmpty().mapNotNull { e ->
                val c = e as? JsonObject ?: return@mapNotNull null
                Coding(
                    (c["start"] as? JsonPrimitive)?.doubleOrNull ?: return@mapNotNull null,
                    (c["code"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null,
                    (c["coder"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
                    (c["at"] as? JsonPrimitive)?.longOrNull ?: 0L,
                )
            }
            return TranscriptCoding((o["codebook"] as? JsonPrimitive)?.contentOrNull, list)
        }
    }
}

fun Transcript.withCoding(coding: TranscriptCoding): Transcript = copy(
    raw = JsonObject(raw + ("coding" to buildJsonObject {
        coding.codebookId?.let { put("codebook", it) }
        put("codings", buildJsonArray {
            coding.codings.forEach { c ->
                add(buildJsonObject { put("start", c.segmentStart); put("code", c.codeId); put("coder", c.coder); put("at", c.at) })
            }
        })
    }))
)

class CodebookStore(dir: File) : ListStore<Codebook>(FileBackend(File(dir, "codebooks.json")), Codebook.serializer(), { it.id })

/** Agreement of two coders, per code (present/absent per passage) and overall. */
object Intercoder {
    data class CodeAgreement(val code: Code, val both: Int, val onlyA: Int, val onlyB: Int, val neither: Int) {
        val n get() = both + onlyA + onlyB + neither
        val percent get() = if (n == 0) 0.0 else (both + neither).toDouble() / n
        /** Cohen's kappa for this code (null if not computable, e.g. nobody used it). */
        val kappa: Double?
            get() {
                if (n == 0) return null
                val po = percent
                val pa = (both + onlyA).toDouble() / n
                val pb = (both + onlyB).toDouble() / n
                val pe = pa * pb + (1 - pa) * (1 - pb)
                return if (pe >= 1.0) null else (po - pe) / (1 - pe)
            }
    }

    data class Result(val coderA: String, val coderB: String, val passages: Int, val perCode: List<CodeAgreement>) {
        /** Share of passages where both chose exactly the same codes. */
        val exactAgreement: Double get() = if (passages == 0) 0.0 else perCodeExact / passages.toDouble()
        internal var perCodeExact = 0
        val meanKappa: Double? get() = perCode.mapNotNull { it.kappa }.takeIf { it.isNotEmpty() }?.average()
    }

    fun interpret(k: Double?): String = when {
        k == null -> "nicht berechenbar"
        k < 0 -> "keine Übereinstimmung"
        k < 0.21 -> "gering"
        k < 0.41 -> "ausreichend"
        k < 0.61 -> "mittelmäßig"
        k < 0.81 -> "beachtlich"
        else -> "(fast) vollkommen"
    }

    fun compare(t: Transcript, coding: TranscriptCoding, codebook: Codebook, a: String, b: String): Result {
        // Only passages that at least one of the two coded count as units (as in MAXQDA's segment comparison).
        val units = t.segments.filter { s -> coding.at(s, a).isNotEmpty() || coding.at(s, b).isNotEmpty() }
        var exact = 0
        val per = codebook.codes.map { code ->
            var both = 0; var onlyA = 0; var onlyB = 0; var neither = 0
            units.forEach { s ->
                val x = coding.at(s, a).any { it.codeId == code.id }
                val y = coding.at(s, b).any { it.codeId == code.id }
                when {
                    x && y -> both++
                    x -> onlyA++
                    y -> onlyB++
                    else -> neither++
                }
            }
            CodeAgreement(code, both, onlyA, onlyB, neither)
        }.filter { it.both + it.onlyA + it.onlyB > 0 }
        units.forEach { s -> if (coding.at(s, a).map { it.codeId }.toSet() == coding.at(s, b).map { it.codeId }.toSet()) exact++ }
        return Result(a, b, units.size, per).also { it.perCodeExact = exact }
    }

    fun report(r: Result): String = buildString {
        append("Intercoder-Übereinstimmung ${r.coderA} / ${r.coderB}: ${r.passages} codierte Abschnitte, ")
        append("identische Codierung in ${"%.0f".format(r.exactAgreement * 100)} %. ")
        r.meanKappa?.let { append("Mittleres Cohens Kappa über die Codes: ${"%.2f".format(it)} (${interpret(it)}).") }
        append("\n\n| Code | beide | nur ${r.coderA} | nur ${r.coderB} | Übereinstimmung | Kappa |\n| --- | --- | --- | --- | --- | --- |\n")
        r.perCode.forEach { c ->
            append("| ${c.code.name} | ${c.both} | ${c.onlyA} | ${c.onlyB} | ${"%.0f".format(c.percent * 100)} % | ${c.kappa?.let { "%.2f".format(it) } ?: "–"} |\n")
        }
    }
}

/**
 * REFI-QDA project exchange (.qdpx, standard 1.0): the transcript as text source with codebook, codings and comments.
 * MAXQDA, ATLAS.ti, NVivo and QDA Miner import it.
 */
object RefiQda {
    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
        .replace(Regex("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]"), "")
    private fun guid() = UUID.randomUUID().toString().uppercase()

    /** The text of the source: one paragraph per passage ("Sprecher: Text"), and where each passage lies in it. */
    fun sourceText(t: Transcript): Pair<String, List<IntRange>> {
        val sb = StringBuilder()
        val ranges = mutableListOf<IntRange>()
        t.segments.forEach { s ->
            val prefix = (if (t.diarized) t.speakerName(s.speaker) + ": " else "") + "#${TranscriptExport.clock(s.start)}# "
            sb.append(prefix)
            val start = sb.codePointCount(0, sb.length)
            sb.append(s.text.trim())
            val end = sb.codePointCount(0, sb.length)
            ranges += start until end
            sb.append("\n")
        }
        return sb.toString() to ranges
    }

    fun write(out: java.io.OutputStream, t: Transcript, coding: TranscriptCoding, codebook: Codebook?) {
        val now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS).toString()
        val users = (coding.coders().ifEmpty { listOf("Lora") }).associateWith { guid() }
        val defaultUser = users.values.first()
        val codeGuids = codebook?.codes?.associate { it.id to guid() }.orEmpty()
        val sourceGuid = guid()
        val (text, ranges) = sourceText(t)
        val xml = buildString {
            append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n")
            append("<Project name=\"${esc(t.title)}\" origin=\"Lora\" creatingUserGUID=\"$defaultUser\" creationDateTime=\"$now\" ")
            append("xmlns=\"urn:QDA-XML:project:1.0\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n")
            append("<Users>\n")
            users.forEach { (name, g) -> append("<User guid=\"$g\" name=\"${esc(name)}\"/>\n") }
            append("</Users>\n")
            if (codebook != null && codebook.codes.isNotEmpty()) {
                append("<CodeBook>\n<Codes>\n")
                fun codeXml(c: Code) {
                    append("<Code guid=\"${codeGuids[c.id]}\" name=\"${esc(c.name)}\" isCodable=\"true\" color=\"${c.color}\">")
                    val desc = listOf(c.description, c.example.takeIf { it.isNotBlank() }?.let { "Ankerbeispiel: $it" }).filterNotNull().filter { it.isNotBlank() }.joinToString("\n")
                    if (desc.isNotBlank()) append("<Description>${esc(desc)}</Description>")
                    codebook.codes.filter { it.parentId == c.id }.forEach { codeXml(it) }
                    append("</Code>\n")
                }
                codebook.codes.filter { it.parentId == null || codebook.code(it.parentId) == null }.forEach { codeXml(it) }
                append("</Codes>\n</CodeBook>\n")
            }
            append("<Sources>\n")
            append("<TextSource guid=\"$sourceGuid\" name=\"${esc(t.title)}\" plainTextPath=\"internal://$sourceGuid.txt\" creatingUser=\"$defaultUser\" creationDateTime=\"$now\">\n")
            t.segments.forEachIndexed { i, s ->
                val r = ranges[i]
                val here = coding.at(s).filter { codeGuids.containsKey(it.codeId) }
                if (here.isNotEmpty()) {
                    // One selection per coder, so each coder's codings stay apart (intercoder checks in MAXQDA).
                    here.groupBy { it.coder }.forEach { (coder, list) ->
                        val user = users[coder] ?: defaultUser
                        append("<PlainTextSelection guid=\"${guid()}\" name=\"${esc(s.text.take(40))}\" startPosition=\"${r.first}\" endPosition=\"${r.last + 1}\" creatingUser=\"$user\" creationDateTime=\"$now\">\n")
                        list.forEach { cd -> append("<Coding guid=\"${guid()}\" creatingUser=\"$user\" creationDateTime=\"$now\"><CodeRef targetGUID=\"${codeGuids[cd.codeId]}\"/></Coding>\n") }
                        append("</PlainTextSelection>\n")
                    }
                }
            }
            append("</TextSource>\n</Sources>\n")
            val comments = t.segments.withIndex().filter { !it.value.comment.isNullOrBlank() }
            if (comments.isNotEmpty()) {
                append("<Notes>\n")
                comments.forEach { (_, s) ->
                    append("<Note guid=\"${guid()}\" name=\"Kommentar ${TranscriptExport.clock(s.start)}\" creatingUser=\"$defaultUser\" creationDateTime=\"$now\">")
                    append("<PlainTextContent>${esc(s.comment!!)}</PlainTextContent></Note>\n")
                }
                append("</Notes>\n")
            }
            append("</Project>\n")
        }
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("project.qde"))
            zip.write(xml.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("Sources/$sourceGuid.txt"))
            zip.write(text.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
    }

    /** Coded passages as table (CSV with semicolons, for Excel, SPSS, R). */
    fun csv(t: Transcript, coding: TranscriptCoding, codebook: Codebook?): String = buildString {
        fun q(s: String) = "\"" + s.replace("\"", "\"\"") + "\""
        append("Zeit;Sprecher;Codierer:in;Code;Text\n")
        t.segments.forEach { s ->
            coding.at(s).forEach { c ->
                val name = codebook?.code(c.codeId)?.let { codebook.path(it) } ?: c.codeId
                append(listOf(TranscriptExport.clock(s.start), t.speakerName(s.speaker), c.coder, name, s.text.trim()).joinToString(";") { q(it) }).append("\n")
            }
        }
    }
}
