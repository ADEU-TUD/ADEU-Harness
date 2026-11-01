package io.github.thepytales.llmhub.research

import kotlinx.serialization.Serializable
import java.text.Normalizer
import java.util.UUID
import io.github.thepytales.llmhub.ui.trLater

/** Kind of a work; decides which details a reference needs and how the styles write it. */
@Serializable
enum class RefType(val label: String) {
    ARTICLE(trLater("Zeitschriftenartikel")),
    BOOK(trLater("Buch (Monografie)")),
    EDITED_BOOK(trLater("Sammelband (Herausgeberwerk)")),
    CHAPTER(trLater("Beitrag im Sammelband")),
    CONFERENCE(trLater("Tagungsbeitrag")),
    THESIS(trLater("Hochschulschrift")),
    REPORT(trLater("Bericht / graue Literatur")),
    WEB(trLater("Webseite")),
    OTHER(trLater("Sonstiges")),
}

/** A person (family and given name) or an organization ([literal]: written as is, never shortened). */
@Serializable
data class Person(val family: String, val given: String = "", val literal: Boolean = false) {
    /** "H.-P. M." style initials of the given names. */
    fun initials(withSpace: Boolean = true): String = given.split(Regex("\\s+")).filter { it.isNotBlank() }.joinToString(if (withSpace) " " else "") { part ->
        part.split('-').filter { it.isNotEmpty() }.joinToString("-") { p -> if (p.endsWith(".") && p.length <= 3) p else p.first().uppercaseChar() + "." }
    }

    val display: String get() = if (literal || given.isBlank()) family else "$given $family"

    companion object {
        /** "Müller, Hans", "Hans Müller", "{Kultusministerkonferenz}" or "van der Berg, Jan". */
        fun parse(text: String): Person? {
            val t = text.trim().trim(',').trim()
            if (t.isEmpty()) return null
            if (t.startsWith("{") && t.endsWith("}")) return Person(t.removeSurrounding("{", "}").trim(), literal = true)
            if (',' in t) {
                val family = t.substringBefore(',').trim()
                val given = t.substringAfter(',').trim()
                return Person(family, given)
            }
            val parts = t.split(Regex("\\s+"))
            if (parts.size == 1) return Person(parts[0])
            // Particles like "von", "van der" belong to the family name.
            val particle = parts.indexOfFirst { it.lowercase() in PARTICLES }
            return if (particle in 1 until parts.lastIndex) {
                Person(parts.drop(particle).joinToString(" "), parts.take(particle).joinToString(" "))
            } else {
                Person(parts.last(), parts.dropLast(1).joinToString(" "))
            }
        }

        private val PARTICLES = setOf("von", "van", "de", "der", "den", "zu", "da", "di", "du", "le", "la")
    }
}

/**
 * One work in a reference list or the literature collection, with the details the citation styles need
 * (similar to CSL-JSON, the format of Zotero and Citavi).
 */
@Serializable
data class Reference(
    val id: String = UUID.randomUUID().toString(),
    val type: RefType = RefType.OTHER,
    val authors: List<Person> = emptyList(),
    val editors: List<Person> = emptyList(),
    /** Year of publication as written ("2021"); empty = no year known. */
    val year: String = "",
    val title: String = "",
    /** Journal, book of a chapter, proceedings or website name. */
    val container: String = "",
    val volume: String = "",
    val issue: String = "",
    val pages: String = "",
    val edition: String = "",
    val publisher: String = "",
    val place: String = "",
    val doi: String = "",
    val url: String = "",
    val isbn: String = "",
    val issn: String = "",
    /** Date the web page was read, ISO (2026-03-03). */
    val accessed: String = "",
    /** University (theses) or institution that issued a report. */
    val institution: String = "",
    /** "Dissertation", "Masterarbeit", report number … */
    val genre: String = "",
    val abstract: String = "",
    val language: String = "",
    /** The reference as it was given (free text), when it could not be read into fields. */
    val raw: String = "",
    /** Times cited (OpenAlex, Crossref) – shown in search results. */
    val citations: Int? = null,
    /** Freely readable full text, if known. */
    val openAccessUrl: String = "",
    /** Where the details come from (OpenAlex, Crossref, ERIC, DNB, BibTeX …). */
    val source: String = "",
    /** Own note, e.g. why the work is relevant. */
    val note: String = "",
    /** Lists of the collection the work belongs to ("Classroom Management", "Antrag 2027" …). */
    val lists: List<String> = emptyList(),
    /** Project the work was collected in. */
    val folderId: String? = null,
    val addedAt: Long = System.currentTimeMillis(),
    /** Excerpt of the work (AP-09, Markdown with checked page numbers). */
    val excerpt: String = "",
    /** Document of the excerpt in „Dokumente“. */
    val excerptDocId: String? = null,
    val excerptAt: Long? = null,
) {
    /** Persons named first in the reference: authors, otherwise the editors. */
    val creators: List<Person> get() = authors.ifEmpty { editors }

    /** Only free text, nothing read into fields. */
    val unparsed: Boolean get() = raw.isNotBlank() && title.isBlank()

    val doiUrl: String get() = if (doi.isNotBlank()) "https://doi.org/$doi" else ""

    /** Key for finding the same work twice: DOI or title plus year. */
    val dedupeKey: String
        get() = if (doi.isNotBlank()) "doi:" + doi.lowercase() else "t:" + normalize(title).take(80) + "|" + year

    /** Short line for lists: "Müller & Schmidt (2020): Titel". */
    fun short(): String {
        val who = when (creators.size) {
            0 -> ""
            1 -> creators[0].family
            2 -> creators[0].family + " & " + creators[1].family
            else -> creators[0].family + " et al."
        }
        val y = year.ifBlank { "o. J." }
        val t = title.ifBlank { raw }
        return if (who.isEmpty()) "$t ($y)" else "$who ($y): $t"
    }

    companion object {
        fun normalize(s: String): String = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}"), "").replace("ß", "ss").replace(Regex("[^a-z0-9]+"), " ").trim()

        /** DOI without "https://doi.org/" or "doi:" in front. */
        fun cleanDoi(s: String): String = s.trim()
            .replace(Regex("^(https?://)?(dx\\.)?doi\\.org/", RegexOption.IGNORE_CASE), "")
            .removePrefix("doi:").removePrefix("DOI:").trim().trimEnd('.', ',', ';')
    }
}

/** Reads and writes BibTeX (LaTeX, JabRef, Citavi, Zotero). */
object BibTeX {
    private val ENTRY_START = Regex("@\\s*([A-Za-z]+)\\s*[{(]")

    fun parse(text: String): List<Reference> {
        val out = mutableListOf<Reference>()
        var pos = 0
        while (true) {
            val m = ENTRY_START.find(text, pos) ?: break
            val kind = m.groupValues[1].lowercase()
            var i = m.range.last + 1
            // Find the end of the entry by counting braces.
            var depth = 1
            val start = i
            while (i < text.length && depth > 0) {
                when (text[i]) {
                    '{', '(' -> depth++
                    '}', ')' -> depth--
                }
                i++
            }
            pos = i
            if (kind in setOf("comment", "string", "preamble")) continue
            val body = text.substring(start, (i - 1).coerceAtLeast(start))
            val fields = fields(body.substringAfter(',', ""))
            out += toReference(kind, fields)
        }
        return out
    }

    /** field = {value} | "value" | number, separated by commas at depth 0. */
    private fun fields(body: String): Map<String, String> {
        val map = linkedMapOf<String, String>()
        var i = 0
        while (i < body.length) {
            val eq = body.indexOf('=', i)
            if (eq < 0) break
            val name = body.substring(i, eq).trim().trim(',').trim().lowercase()
            var j = eq + 1
            while (j < body.length && body[j].isWhitespace()) j++
            val value = StringBuilder()
            // Values can be joined with #.
            while (j < body.length) {
                when (body[j]) {
                    '{' -> {
                        var depth = 1
                        var k = j + 1
                        while (k < body.length && depth > 0) {
                            if (body[k] == '{') depth++ else if (body[k] == '}') depth--
                            k++
                        }
                        value.append(body.substring(j + 1, k - 1))
                        j = k
                    }
                    '"' -> {
                        var k = j + 1
                        var depth = 0
                        while (k < body.length && !(body[k] == '"' && depth == 0)) {
                            if (body[k] == '{') depth++ else if (body[k] == '}') depth--
                            k++
                        }
                        value.append(body.substring(j + 1, k.coerceAtMost(body.length)))
                        j = k + 1
                    }
                    else -> {
                        var k = j
                        while (k < body.length && body[k] != ',' && body[k] != '#') k++
                        value.append(body.substring(j, k).trim())
                        j = k
                    }
                }
                while (j < body.length && body[j].isWhitespace()) j++
                if (j < body.length && body[j] == '#') {
                    j++
                    while (j < body.length && body[j].isWhitespace()) j++
                } else break
            }
            if (name.isNotEmpty()) map[name] = value.toString()
            val comma = body.indexOf(',', j)
            i = if (comma < 0) body.length else comma + 1
        }
        return map
    }

    private fun toReference(kind: String, raw: Map<String, String>): Reference {
        fun f(vararg keys: String) = keys.firstNotNullOfOrNull { raw[it]?.let(::delatex)?.trim()?.ifBlank { null } }.orEmpty()
        fun people(key: String) = raw[key]?.let { names(it) }.orEmpty()
        val authors = people("author")
        val editors = people("editor")
        val type = when (kind) {
            "article" -> RefType.ARTICLE
            "book", "mvbook" -> if (authors.isEmpty() && editors.isNotEmpty()) RefType.EDITED_BOOK else RefType.BOOK
            "collection", "mvcollection", "proceedings" -> RefType.EDITED_BOOK
            "inbook", "incollection", "inreference", "bookinbook" -> RefType.CHAPTER
            "inproceedings", "conference" -> RefType.CONFERENCE
            "phdthesis", "mastersthesis", "thesis" -> RefType.THESIS
            "techreport", "report" -> RefType.REPORT
            "online", "electronic", "www" -> RefType.WEB
            else -> if (raw["url"] != null && raw["journal"] == null && raw["booktitle"] == null) RefType.WEB else RefType.OTHER
        }
        val date = f("year", "date")
        return Reference(
            type = type,
            authors = authors,
            editors = editors,
            year = Regex("\\d{4}").find(date)?.value ?: date,
            title = listOf(f("title"), f("subtitle")).filter { it.isNotEmpty() }.joinToString(": "),
            container = f("journal", "journaltitle", "booktitle", "maintitle", "howpublished", "organization").let {
                if (type == RefType.WEB && it.isEmpty()) f("organization") else it
            },
            volume = f("volume"),
            issue = f("number", "issue"),
            pages = f("pages").replace("--", "–").replace(Regex("(?<=\\d)-(?=\\d)"), "–"),
            edition = f("edition"),
            publisher = f("publisher"),
            place = f("address", "location"),
            doi = Reference.cleanDoi(f("doi")),
            url = f("url"),
            isbn = f("isbn"),
            issn = f("issn"),
            accessed = f("urldate"),
            institution = f("school", "institution"),
            genre = f("type").ifEmpty {
                when (kind) {
                    "phdthesis" -> "Dissertation"
                    "mastersthesis" -> "Masterarbeit"
                    else -> ""
                }
            },
            abstract = f("abstract"),
            language = f("language", "langid"),
            source = "BibTeX",
            note = f("note", "annote"),
        )
    }

    /** "Müller, Hans and {OECD} and Anna Schmidt". */
    fun names(text: String): List<Person> {
        val parts = mutableListOf<String>()
        var depth = 0
        var last = 0
        val lower = text
        var i = 0
        while (i < text.length) {
            when (text[i]) {
                '{' -> depth++
                '}' -> depth--
            }
            if (depth == 0 && lower.regionMatches(i, " and ", 0, 5, ignoreCase = true)) {
                parts += text.substring(last, i)
                last = i + 5
                i += 5
                continue
            }
            i++
        }
        parts += text.substring(last)
        return parts.mapNotNull { p ->
            val t = p.trim()
            if (t.equals("others", ignoreCase = true)) null
            else if (t.startsWith("{") && closingBrace(t, 0) == t.lastIndex) Person(delatex(t.substring(1, t.lastIndex)), literal = true)
            else Person.parse(delatex(t))
        }
    }

    /** Index of the brace closing the one at [open]. */
    private fun closingBrace(s: String, open: Int): Int {
        var depth = 0
        for (i in open until s.length) {
            if (s[i] == '{') depth++ else if (s[i] == '}') { depth--; if (depth == 0) return i }
        }
        return -1
    }

    private val ACCENTS = mapOf(
        "\"a" to "ä", "\"o" to "ö", "\"u" to "ü", "\"A" to "Ä", "\"O" to "Ö", "\"U" to "Ü", "\"e" to "ë", "\"i" to "ï",
        "'e" to "é", "'a" to "á", "'o" to "ó", "'i" to "í", "'u" to "ú", "'E" to "É", "`e" to "è", "`a" to "à",
        "^e" to "ê", "^a" to "â", "^o" to "ô", "~n" to "ñ", "c c" to "ç", "cc" to "ç", "v s" to "š", "vs" to "š",
        "v c" to "č", "vc" to "č", "v z" to "ž", "vz" to "ž",
    )

    /** LaTeX to plain text: umlauts, ß, dashes and braces. */
    fun delatex(s: String): String {
        var t = s
        t = t.replace(Regex("\\{\\\\([\"'`^~]|[cv] ?)\\{?([A-Za-z])\\}?\\}")) { m -> ACCENTS[m.groupValues[1] + m.groupValues[2]] ?: m.groupValues[2] }
        t = t.replace(Regex("\\\\([\"'`^~])\\{?([A-Za-z])\\}?")) { m -> ACCENTS[m.groupValues[1] + m.groupValues[2]] ?: m.groupValues[2] }
        t = t.replace("{\\ss}", "ß").replace("\\ss{}", "ß").replace("\\ss ", "ß").replace("\\ss", "ß")
            .replace("\\&", "&").replace("\\%", "%").replace("\\_", "_").replace("\\textendash", "–").replace("\\textemdash", "—")
            .replace("---", "—").replace("--", "–").replace("~", " ")
        t = t.replace(Regex("\\\\(emph|textit|textbf|mkbibquote|enquote)\\{([^{}]*)\\}"), "$2")
        return t.replace("{", "").replace("}", "").replace(Regex("\\s+"), " ").trim()
    }

    private fun escape(s: String) = s.replace("\\", "\\\\").replace("{", "\\{").replace("}", "\\}")
        .replace("&", "\\&").replace("%", "\\%").replace("_", "\\_")

    /** Citation key like "mueller2020unterricht", unique within [taken]. */
    fun key(r: Reference, taken: MutableSet<String>): String {
        val family = Reference.normalize(r.creators.firstOrNull()?.family ?: "anonym").replace(" ", "")
        val word = Reference.normalize(r.title).split(' ').firstOrNull { it.length > 3 }.orEmpty()
        val base = (family + r.year + word).ifBlank { "eintrag" }
        var k = base
        var n = 1
        while (!taken.add(k)) k = base + ('a' + n++)
        return k
    }

    fun write(refs: List<Reference>): String {
        val taken = mutableSetOf<String>()
        return refs.joinToString("\n\n") { r ->
            val kind = when (r.type) {
                RefType.ARTICLE -> "article"
                RefType.BOOK -> "book"
                RefType.EDITED_BOOK -> "collection"
                RefType.CHAPTER -> "incollection"
                RefType.CONFERENCE -> "inproceedings"
                RefType.THESIS -> if (r.genre.contains("master", true)) "mastersthesis" else "phdthesis"
                RefType.REPORT -> "techreport"
                RefType.WEB -> "online"
                RefType.OTHER -> "misc"
            }
            fun people(list: List<Person>) = list.joinToString(" and ") { p ->
                if (p.literal || p.given.isBlank()) "{${escape(p.family)}}" else "${escape(p.family)}, ${escape(p.given)}"
            }
            val fields = buildList {
                if (r.authors.isNotEmpty()) add("author" to people(r.authors))
                if (r.editors.isNotEmpty()) add("editor" to people(r.editors))
                add("title" to "{" + escape(r.title.ifBlank { r.raw }) + "}")
                if (r.container.isNotBlank()) add((if (r.type == RefType.ARTICLE) "journal" else if (r.type == RefType.WEB) "organization" else "booktitle") to escape(r.container))
                if (r.year.isNotBlank()) add("year" to r.year)
                if (r.volume.isNotBlank()) add("volume" to r.volume)
                if (r.issue.isNotBlank()) add("number" to r.issue)
                if (r.pages.isNotBlank()) add("pages" to r.pages.replace("–", "--"))
                if (r.edition.isNotBlank()) add("edition" to r.edition)
                if (r.publisher.isNotBlank()) add("publisher" to escape(r.publisher))
                if (r.place.isNotBlank()) add("address" to escape(r.place))
                if (r.institution.isNotBlank()) add((if (r.type == RefType.THESIS) "school" else "institution") to escape(r.institution))
                if (r.genre.isNotBlank() && r.type in setOf(RefType.THESIS, RefType.REPORT)) add("type" to escape(r.genre))
                if (r.doi.isNotBlank()) add("doi" to r.doi)
                if (r.url.isNotBlank()) add("url" to r.url)
                if (r.accessed.isNotBlank()) add("urldate" to r.accessed)
                if (r.isbn.isNotBlank()) add("isbn" to r.isbn)
                if (r.issn.isNotBlank()) add("issn" to r.issn)
                if (r.language.isNotBlank()) add("language" to r.language)
                if (r.abstract.isNotBlank()) add("abstract" to escape(r.abstract))
                if (r.note.isNotBlank()) add("note" to escape(r.note))
            }
            "@$kind{${key(r, taken)},\n" + fields.joinToString(",\n") { (k, v) -> "  $k = {$v}" } + "\n}"
        }
    }
}

/** Reads and writes RIS (EndNote, Citavi, Zotero, Mendeley, most library catalogues). */
object Ris {
    private val LINE = Regex("^([A-Z][A-Z0-9])  -(?: (.*))?$")

    fun parse(text: String): List<Reference> {
        val out = mutableListOf<Reference>()
        var tags = mutableListOf<Pair<String, String>>()
        for (line in text.lines()) {
            val m = LINE.find(line.trimEnd()) ?: continue
            val tag = m.groupValues[1]
            val value = m.groupValues[2].trim()
            if (tag == "TY") tags = mutableListOf()
            tags += tag to value
            if (tag == "ER") out += toReference(tags)
        }
        return out
    }

    private fun toReference(tags: List<Pair<String, String>>): Reference {
        fun all(vararg t: String) = tags.filter { it.first in t }.map { it.second }.filter { it.isNotBlank() }
        fun one(vararg t: String) = t.firstNotNullOfOrNull { k -> tags.firstOrNull { it.first == k && it.second.isNotBlank() }?.second }.orEmpty()
        val ty = one("TY")
        val authors = all("AU", "A1").mapNotNull(Person::parse)
        val editors = all("ED", "A2", "A3").mapNotNull(Person::parse)
        val type = when (ty) {
            "JOUR", "JFULL", "MGZN", "EJOUR", "NEWS" -> RefType.ARTICLE
            "BOOK", "EBOOK" -> if (authors.isEmpty() && editors.isNotEmpty()) RefType.EDITED_BOOK else RefType.BOOK
            "EDBOOK" -> RefType.EDITED_BOOK
            "CHAP", "ECHAP" -> RefType.CHAPTER
            "CONF", "CPAPER" -> RefType.CONFERENCE
            "THES" -> RefType.THESIS
            "RPRT", "GOVDOC" -> RefType.REPORT
            "ELEC", "WEB", "BLOG" -> RefType.WEB
            else -> RefType.OTHER
        }
        val sp = one("SP")
        val ep = one("EP")
        val date = one("PY", "Y1", "DA")
        val container = if (type == RefType.CHAPTER || type == RefType.CONFERENCE) one("T2", "BT", "JO", "JF") else one("JO", "JF", "T2", "JA")
        return Reference(
            type = type,
            authors = authors,
            editors = if (type == RefType.CHAPTER || type == RefType.EDITED_BOOK || type == RefType.CONFERENCE) editors else editors.takeIf { authors.isEmpty() }.orEmpty(),
            year = Regex("\\d{4}").find(date)?.value.orEmpty(),
            title = one("TI", "T1", "CT"),
            container = container,
            volume = one("VL"),
            issue = one("IS"),
            pages = if (sp.isNotEmpty() && ep.isNotEmpty()) "$sp–$ep" else sp.replace("-", "–"),
            edition = one("ET"),
            publisher = one("PB"),
            place = one("CY", "PP"),
            doi = Reference.cleanDoi(one("DO")),
            url = one("UR", "L2"),
            isbn = if (type == RefType.ARTICLE) "" else one("SN"),
            issn = if (type == RefType.ARTICLE) one("SN") else "",
            accessed = one("Y2"),
            institution = if (type == RefType.THESIS || type == RefType.REPORT) one("PB") else "",
            genre = one("M3"),
            abstract = one("AB", "N2"),
            language = one("LA"),
            source = "RIS",
            note = one("N1"),
        )
    }

    fun write(refs: List<Reference>): String = refs.joinToString("\n") { r ->
        val ty = when (r.type) {
            RefType.ARTICLE -> "JOUR"
            RefType.BOOK -> "BOOK"
            RefType.EDITED_BOOK -> "EDBOOK"
            RefType.CHAPTER -> "CHAP"
            RefType.CONFERENCE -> "CPAPER"
            RefType.THESIS -> "THES"
            RefType.REPORT -> "RPRT"
            RefType.WEB -> "ELEC"
            RefType.OTHER -> "GEN"
        }
        buildString {
            fun t(tag: String, v: String) {
                if (v.isNotBlank()) append(tag).append("  - ").append(v.replace('\n', ' ')).append("\r\n")
            }
            t("TY", ty)
            r.authors.forEach { t("AU", if (it.literal || it.given.isBlank()) it.family else "${it.family}, ${it.given}") }
            r.editors.forEach { t("ED", if (it.literal || it.given.isBlank()) it.family else "${it.family}, ${it.given}") }
            t("TI", r.title.ifBlank { r.raw })
            t(if (r.type == RefType.ARTICLE) "JO" else "T2", r.container)
            t("PY", r.year)
            t("VL", r.volume)
            t("IS", r.issue)
            val pages = r.pages.split('–', '-')
            t("SP", pages.getOrElse(0) { "" })
            if (pages.size > 1) t("EP", pages[1])
            t("ET", r.edition)
            t("PB", r.publisher.ifBlank { r.institution })
            t("CY", r.place)
            t("DO", r.doi)
            t("UR", r.url.ifBlank { r.doiUrl })
            t("SN", r.isbn.ifBlank { r.issn })
            t("Y2", r.accessed)
            t("M3", r.genre)
            t("LA", r.language)
            t("AB", r.abstract)
            t("N1", r.note)
            append("ER  - \r\n")
        }
    }
}

/** CSL-JSON, which Zotero, Citavi and Pandoc import. */
object CslJson {
    private fun esc(s: String) = buildString {
        s.forEach { ch ->
            when (ch) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> {}
                '\t' -> append("\\t")
                else -> if (ch < ' ') append("\\u%04x".format(ch.code)) else append(ch)
            }
        }
    }

    fun type(t: RefType) = when (t) {
        RefType.ARTICLE -> "article-journal"
        RefType.BOOK, RefType.EDITED_BOOK -> "book"
        RefType.CHAPTER -> "chapter"
        RefType.CONFERENCE -> "paper-conference"
        RefType.THESIS -> "thesis"
        RefType.REPORT -> "report"
        RefType.WEB -> "webpage"
        RefType.OTHER -> "document"
    }

    fun write(refs: List<Reference>): String {
        val taken = mutableSetOf<String>()
        return refs.joinToString(",\n", "[\n", "\n]") { r ->
            val fields = mutableListOf<String>()
            fun s(k: String, v: String) {
                if (v.isNotBlank()) fields += "\"$k\": \"${esc(v)}\""
            }
            fun people(k: String, list: List<Person>) {
                if (list.isEmpty()) return
                fields += "\"$k\": [" + list.joinToString(", ") { p ->
                    if (p.literal || p.given.isBlank()) "{\"literal\": \"${esc(p.family)}\"}" else "{\"family\": \"${esc(p.family)}\", \"given\": \"${esc(p.given)}\"}"
                } + "]"
            }
            s("id", BibTeX.key(r, taken))
            s("type", type(r.type))
            people("author", r.authors)
            people("editor", r.editors)
            s("title", r.title.ifBlank { r.raw })
            s("container-title", r.container)
            r.year.toIntOrNull()?.let { fields += "\"issued\": {\"date-parts\": [[$it]]}" }
            s("volume", r.volume)
            s("issue", r.issue)
            s("page", r.pages)
            s("edition", r.edition)
            s("publisher", r.publisher.ifBlank { r.institution })
            s("publisher-place", r.place)
            s("DOI", r.doi)
            s("URL", r.url)
            s("ISBN", r.isbn)
            s("ISSN", r.issn)
            s("genre", r.genre)
            s("abstract", r.abstract)
            s("language", r.language)
            s("note", r.note)
            if (r.accessed.length >= 4) {
                val parts = r.accessed.split('-').mapNotNull { it.toIntOrNull() }
                if (parts.isNotEmpty()) fields += "\"accessed\": {\"date-parts\": [[${parts.joinToString(", ")}]]}"
            }
            "  {" + fields.joinToString(", ") + "}"
        }
    }

    /** Reads CSL-JSON (an array of items, e.g. exported from Zotero). */
    fun parse(text: String): List<Reference> {
        val arr = runCatching { io.github.thepytales.llmhub.net.JSON.parseToJsonElement(text) }.getOrNull() ?: return emptyList()
        val items = (arr as? kotlinx.serialization.json.JsonArray) ?: kotlinx.serialization.json.JsonArray(listOf(arr))
        return items.mapNotNull { e ->
            val o = e as? kotlinx.serialization.json.JsonObject ?: return@mapNotNull null
            fun s(k: String) = (o[k] as? kotlinx.serialization.json.JsonPrimitive)?.content.orEmpty()
            fun people(k: String) = (o[k] as? kotlinx.serialization.json.JsonArray).orEmpty().mapNotNull { p ->
                val po = p as? kotlinx.serialization.json.JsonObject ?: return@mapNotNull null
                fun ps(key: String) = (po[key] as? kotlinx.serialization.json.JsonPrimitive)?.content.orEmpty()
                if (ps("literal").isNotBlank()) Person(ps("literal"), literal = true)
                else Person(listOf(ps("non-dropping-particle"), ps("family")).filter { it.isNotBlank() }.joinToString(" "), ps("given"))
                    .takeIf { it.family.isNotBlank() }
            }
            fun dateYear(k: String) = (((o[k] as? kotlinx.serialization.json.JsonObject)?.get("date-parts") as? kotlinx.serialization.json.JsonArray)
                ?.firstOrNull() as? kotlinx.serialization.json.JsonArray)?.firstOrNull()?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }.orEmpty()
            val authors = people("author")
            val editors = people("editor")
            val type = when (s("type")) {
                "article-journal", "article-magazine", "article-newspaper", "article" -> RefType.ARTICLE
                "book" -> if (authors.isEmpty() && editors.isNotEmpty()) RefType.EDITED_BOOK else RefType.BOOK
                "chapter", "entry-encyclopedia", "entry-dictionary" -> RefType.CHAPTER
                "paper-conference" -> RefType.CONFERENCE
                "thesis" -> RefType.THESIS
                "report" -> RefType.REPORT
                "webpage", "post-weblog", "post" -> RefType.WEB
                else -> RefType.OTHER
            }
            Reference(
                type = type, authors = authors, editors = editors, year = dateYear("issued"), title = s("title"),
                container = s("container-title"), volume = s("volume"), issue = s("issue"), pages = s("page").replace("-", "–"),
                edition = s("edition"), publisher = s("publisher"), place = s("publisher-place"), doi = Reference.cleanDoi(s("DOI")),
                url = s("URL"), isbn = s("ISBN"), issn = s("ISSN"), genre = s("genre"), abstract = s("abstract"),
                language = s("language"), source = "CSL-JSON", note = s("note"),
            )
        }
    }
}

/** Recognizes the format of pasted or imported text and reads the references. */
object ReferenceImport {
    enum class Kind { BIBTEX, RIS, CSL_JSON, TEXT }

    fun detect(text: String): Kind {
        val t = text.trimStart()
        return when {
            Regex("@\\s*[A-Za-z]+\\s*[{(]").containsMatchIn(t) && "=" in t -> Kind.BIBTEX
            Regex("(?m)^TY  - ").containsMatchIn(t) -> Kind.RIS
            (t.startsWith("[") || t.startsWith("{")) && "\"type\"" in t -> Kind.CSL_JSON
            else -> Kind.TEXT
        }
    }

    /** Structured formats are read into fields; free text becomes one entry per reference (to be completed online). */
    fun read(text: String): List<Reference> = when (detect(text)) {
        Kind.BIBTEX -> BibTeX.parse(text)
        Kind.RIS -> Ris.parse(text)
        Kind.CSL_JSON -> CslJson.parse(text)
        Kind.TEXT -> splitFreeText(text).map { FreeText.parse(it) }
    }

    private val LIST_MARKER = Regex("^\\s*(?:[-*•]|\\d+[.)]|\\[\\d+])\\s+")

    /** One reference per paragraph or per line (lines that continue a reference are joined). */
    fun splitFreeText(text: String): List<String> {
        val lines = text.lines().map { it.replace(LIST_MARKER, "").replace(Regex("[*_]{1,2}"), "").trim() }
        val out = mutableListOf<String>()
        val current = StringBuilder()
        fun flush() {
            val s = current.toString().trim()
            if (s.length >= 15) out += s
            current.clear()
        }
        for (l in lines) {
            if (l.isEmpty()) { flush(); continue }
            if (l.equals("Literatur", true) || l.equals("Literaturverzeichnis", true) || l.equals("References", true) || l.startsWith("#")) { flush(); continue }
            // A new reference starts with a surname followed by a comma or with an organization in front of a year.
            val startsNew = current.isNotEmpty() && (Regex("^[\\p{Lu}][\\p{L}'’ -]{1,40},\\s*[\\p{Lu}]").containsMatchIn(l) ||
                Regex("^[\\p{Lu}][\\p{L}'’ .-]{1,60}\\s\\((\\d{4}|o\\. ?J\\.|n\\.d\\.)").containsMatchIn(l))
            if (startsNew) flush()
            if (current.isNotEmpty()) current.append(' ')
            current.append(l)
        }
        flush()
        return out
    }
}

/**
 * Reads the usual shapes of a written reference (APA, DIN, Harvard, ZfPäd) into fields as far as possible.
 * What cannot be read safely stays in [Reference.raw]; the online lookup fills in the rest.
 */
object FreeText {
    private val DOI = Regex("10\\.\\d{4,9}/[^\\s\"<>]+", RegexOption.IGNORE_CASE)
    private val URL = Regex("https?://\\S+")
    private val YEAR_PAREN = Regex("\\((\\d{4})[a-z]?(?:,[^)]*)?\\)|\\((o\\. ?J\\.|n\\.d\\.)\\)")
    private val YEAR = Regex("(?<![\\d/])(1[5-9]\\d{2}|20\\d{2})(?![\\d/])")
    private val EDITORS_MARK = Regex("\\((Hrsg|Hg|Eds?|ed)\\.?\\)", RegexOption.IGNORE_CASE)
    private val IN_MARK = Regex("(?:^|[.:]\\s)In(?::|\\s)\\s*", RegexOption.IGNORE_CASE)
    private val PAGES = Regex("(?:S\\.|pp?\\.)\\s*(\\d+\\s*[–-]\\s*\\d+|\\d+)")
    private val VOL_ISSUE = Regex("(\\d+)\\s*\\((\\d+[–/-]?\\d*)\\)")
    private val PLACE_PUBLISHER = Regex("([\\p{Lu}][\\p{L} ./-]{1,40}):\\s*([\\p{Lu}][^.,]{1,60})")
    private val EDITION = Regex("\\(?(\\d+)\\.\\s*(?:Aufl|Auflage|überarb|erw)[^)]*\\)?|\\((\\d+)(?:st|nd|rd|th) ed\\.\\)")

    fun parse(text: String): Reference {
        val raw = text.trim()
        val doi = DOI.find(raw)?.value?.trimEnd('.', ',', ';', ')', ']').orEmpty()
        val url = if (doi.isEmpty()) URL.find(raw)?.value?.trimEnd('.', ',', ';').orEmpty() else ""
        val yearMatch = YEAR_PAREN.find(raw)
        val year = yearMatch?.groupValues?.get(1)?.takeIf { it.isNotEmpty() } ?: YEAR.find(raw)?.value.orEmpty()
        val namesPart = when {
            yearMatch != null -> raw.substring(0, yearMatch.range.first)
            else -> raw.substringBefore(". ", "")
        }
        val editorsFirst = EDITORS_MARK.containsMatchIn(namesPart)
        val people = names(namesPart.replace(EDITORS_MARK, ""))
        var rest = if (yearMatch != null) raw.substring(yearMatch.range.last + 1) else raw.substringAfter(". ", raw)
        rest = rest.trimStart(':', '.', ' ', ',')
        rest = rest.replace(DOI, "").replace(URL, "").replace(Regex("https?://doi\\.org/"), "").trim()
        val inIdx = IN_MARK.find(rest)
        val title: String
        var container = ""
        var editors = emptyList<Person>()
        var type: RefType
        val pages = PAGES.find(rest)?.groupValues?.get(1)?.replace(Regex("\\s*[–-]\\s*"), "–").orEmpty()
        if (inIdx != null && inIdx.range.first > 3) {
            title = rest.substring(0, inIdx.range.first).trim().trimEnd('.')
            val after = rest.substring(inIdx.range.last + 1)
            val edMatch = EDITORS_MARK.find(after)
            if (edMatch != null) {
                editors = names(after.substring(0, edMatch.range.first))
                container = after.substring(edMatch.range.last + 1).trimStart(':', ',', ' ', '.').substringBefore(". ").substringBefore(" (S.").substringBefore(" (pp.").trim().trimEnd('.', ',')
            } else {
                container = after.substringBefore(",").substringBefore(". ").trim()
            }
            type = RefType.CHAPTER
        } else {
            val sentences = rest.split(Regex("(?<=[.?!])\\s+(?=[\\p{Lu}*])"))
            title = sentences.firstOrNull().orEmpty().trim().trimEnd('.')
            val second = sentences.getOrNull(1).orEmpty()
            type = when {
                VOL_ISSUE.containsMatchIn(rest) || Regex(",\\s*\\d+\\s*,\\s*\\d+").containsMatchIn(second) -> RefType.ARTICLE
                editorsFirst -> RefType.EDITED_BOOK
                PLACE_PUBLISHER.containsMatchIn(rest) -> RefType.BOOK
                url.isNotEmpty() -> RefType.WEB
                else -> RefType.OTHER
            }
            if (type == RefType.ARTICLE) container = second.substringBefore(",").trim().trimEnd('.')
        }
        val vi = VOL_ISSUE.find(rest)
        val pp = PLACE_PUBLISHER.find(rest.substringAfter(title))
        val edition = EDITION.find(rest)?.let { it.groupValues[1].ifEmpty { it.groupValues[2] } }.orEmpty()
        val fields = Reference(
            type = type,
            authors = if (editorsFirst) emptyList() else people,
            editors = if (editorsFirst) people else editors,
            year = year,
            title = title.removeSurrounding("*").removeSurrounding("„", "“").removeSurrounding("\"").trim(),
            container = container.removeSurrounding("*").trim(),
            volume = vi?.groupValues?.get(1).orEmpty(),
            issue = vi?.groupValues?.get(2).orEmpty(),
            pages = pages,
            edition = edition,
            place = if (type != RefType.ARTICLE) pp?.groupValues?.get(1)?.trim().orEmpty() else "",
            publisher = if (type != RefType.ARTICLE) pp?.groupValues?.get(2)?.trim()?.trimEnd('.').orEmpty() else "",
            doi = Reference.cleanDoi(doi),
            url = url,
            raw = raw,
            source = "Text",
        )
        // Without a recognizable title the entry stays free text.
        return if (fields.title.length < 4) Reference(raw = raw, year = year, doi = fields.doi, authors = fields.authors, source = "Text") else fields
    }

    /** "Müller, H., & Schmidt, A." / "Müller, H./Schmidt, A." / "MÜLLER, Hans und Anna SCHMIDT". */
    fun names(text: String): List<Person> {
        val t = text.trim().trimEnd('.', ':', ',').trim()
        if (t.isEmpty()) return emptyList()
        val parts = t.split(Regex("\\s*(?:/|;|&|\\bund\\b|\\band\\b|\\bu\\.)\\s*")).map { it.trim().trim(',') }.filter { it.isNotEmpty() }
        val out = mutableListOf<Person>()
        for (p in parts) {
            // "Müller, H., Schmidt, A." – pairs of surname and initials.
            val pairs = Regex("([\\p{Lu}][\\p{L}'’ -]+?),\\s*((?:[\\p{Lu}]\\.\\s*-?)+|[\\p{Lu}][\\p{Ll}]+(?:\\s[\\p{Lu}][\\p{Ll}]+)?)").findAll(p).toList()
            if (pairs.isNotEmpty()) {
                pairs.forEach { m -> out += Person(fixCase(m.groupValues[1].trim()), m.groupValues[2].trim()) }
            } else if (p.isNotBlank() && !p.equals("et al", true) && !p.equals("et al.", true)) {
                Person.parse(p)?.let { out += it.copy(family = fixCase(it.family)) }
            }
        }
        return out
    }

    /** "MÜLLER" (DIN style) to "Müller". */
    private fun fixCase(s: String) = if (s.length > 1 && s == s.uppercase() && s.any { it.isLetter() }) {
        s.lowercase().split(' ', '-').joinToString(" ") { w -> w.replaceFirstChar { it.uppercaseChar() } }
    } else s
}
