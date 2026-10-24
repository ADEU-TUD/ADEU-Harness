package io.github.thepytales.llmhub.docs

import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Tests for OPAL (ONYX) and other learning platforms as IMS QTI 2.1 content package (.zip), written without
 * libraries. The model writes simple Markdown:
 *
 * ```
 * # Titel des Tests
 * ## Frage 1 (2 Punkte)
 * Fragetext
 * - [x] richtige Antwort
 * - [ ] falsche Antwort
 * Feedback: Erklärung nach der Antwort (optional)
 *
 * ## Kurzantwort
 * Wie heißt …?
 * Antwort: Kohärenz | Kohaerenz
 *
 * ## Freitext (4 Punkte)
 * Erläutern Sie …
 * Erwartung: Musterlösung für die Bewertung
 * ```
 * One "[x]" makes a single choice, several a multiple choice (points per correct option, wrong ones subtract),
 * "Antwort:" a text entry (case does not matter) and a question without options an essay that is scored by hand.
 */
object QtiWriter {
    enum class Kind { SINGLE, MULTIPLE, TEXT, ESSAY }

    data class Option(val text: String, val correct: Boolean)

    data class Question(
        val title: String,
        val prompt: String,
        val kind: Kind,
        val points: Double,
        val options: List<Option> = emptyList(),
        val answers: List<String> = emptyList(),
        val expectation: String = "",
        val feedback: String = "",
    )

    data class Test(val title: String, val questions: List<Question>)

    private val QUESTION_HEAD = Regex("^#{2,3}\\s+(.+)$")
    private val POINTS = Regex("\\(\\s*(\\d+(?:[.,]\\d+)?)\\s*(?:Punkte?|P\\.?|Pkt\\.?|points?)\\s*\\)", RegexOption.IGNORE_CASE)
    private val OPTION = Regex("^\\s*(?:[-*]|\\d+[.)]|[a-z][.)])\\s*\\[([ xX✓])]\\s*(.+)$")
    private val ANSWER = Regex("^\\s*(?:Antwort|Antworten|Lösung|Loesung|Answer)\\s*:\\s*(.+)$", RegexOption.IGNORE_CASE)
    private val EXPECT = Regex("^\\s*(?:Erwartung|Erwartungshorizont|Musterlösung|Musterloesung|Bewertung)\\s*:\\s*(.*)$", RegexOption.IGNORE_CASE)
    private val FEEDBACK = Regex("^\\s*(?:Feedback|Erklärung|Erklaerung|Begründung)\\s*:\\s*(.*)$", RegexOption.IGNORE_CASE)
    private val POINT_LINE = Regex("^\\s*Punkte\\s*:\\s*(\\d+(?:[.,]\\d+)?)\\s*$", RegexOption.IGNORE_CASE)

    fun parse(title: String, markdown: String): Test {
        var testTitle = title
        val blocks = mutableListOf<Pair<String, MutableList<String>>>()
        for (line in markdown.replace("\r\n", "\n").lines()) {
            val head = QUESTION_HEAD.find(line)
            when {
                line.startsWith("# ") && blocks.isEmpty() -> testTitle = line.removePrefix("# ").trim().ifEmpty { title }
                head != null -> blocks += head.groupValues[1].trim() to mutableListOf()
                blocks.isNotEmpty() -> blocks.last().second += line
            }
        }
        val questions = blocks.mapIndexedNotNull { i, (head, lines) -> question(i + 1, head, lines) }
        return Test(testTitle.ifBlank { "Test" }, questions)
    }

    private fun question(n: Int, head: String, lines: List<String>): Question? {
        var points = POINTS.find(head)?.groupValues?.get(1)?.replace(',', '.')?.toDoubleOrNull()
        val title = head.replace(POINTS, "").trim().trimEnd(':').ifEmpty { "Frage $n" }
        val prompt = StringBuilder()
        val options = mutableListOf<Option>()
        val answers = mutableListOf<String>()
        val expectation = StringBuilder()
        val feedback = StringBuilder()
        var section = 0 // 0 prompt, 1 expectation, 2 feedback
        for (raw in lines) {
            val option = OPTION.find(raw)
            val answer = ANSWER.find(raw)
            val expect = EXPECT.find(raw)
            val fb = FEEDBACK.find(raw)
            val pts = POINT_LINE.find(raw)
            when {
                pts != null -> points = pts.groupValues[1].replace(',', '.').toDoubleOrNull()
                option != null -> options += Option(option.groupValues[2].trim(), option.groupValues[1].isNotBlank())
                answer != null -> answers += answer.groupValues[1].split('|', ';').map { it.trim() }.filter { it.isNotEmpty() }
                expect != null -> { section = 1; expectation.append(expect.groupValues[1]).append('\n') }
                fb != null -> { section = 2; feedback.append(fb.groupValues[1]).append('\n') }
                section == 1 -> expectation.append(raw).append('\n')
                section == 2 -> feedback.append(raw).append('\n')
                else -> prompt.append(raw).append('\n')
            }
        }
        val text = prompt.toString().trim().ifEmpty { title }
        val kind = when {
            options.isNotEmpty() && options.count { it.correct } > 1 -> Kind.MULTIPLE
            options.isNotEmpty() -> Kind.SINGLE
            answers.isNotEmpty() -> Kind.TEXT
            else -> Kind.ESSAY
        }
        // A choice without a marked answer cannot be scored automatically.
        if (options.isNotEmpty() && options.none { it.correct }) return null
        val defaultPoints = when (kind) {
            Kind.MULTIPLE -> options.count { it.correct }.toDouble()
            Kind.ESSAY -> 2.0
            else -> 1.0
        }
        return Question(title, text, kind, (points ?: defaultPoints).coerceAtLeast(0.5), options, answers, expectation.toString().trim(), feedback.toString().trim())
    }

    /** Short description for the tool result. */
    fun summary(test: Test): String {
        val byKind = test.questions.groupingBy { it.kind }.eachCount()
        val labels = mapOf(Kind.SINGLE to "Einfachauswahl", Kind.MULTIPLE to "Mehrfachauswahl", Kind.TEXT to "Kurzantwort", Kind.ESSAY to "Freitext")
        val points = test.questions.sumOf { it.points }
        return "${test.questions.size} Fragen (" + byKind.entries.joinToString(", ") { "${it.value} ${labels[it.key]}" } +
            "), ${fmt(points)} Punkte"
    }

    fun write(file: File, title: String, markdown: String): Test {
        val test = parse(title, markdown)
        require(test.questions.isNotEmpty()) {
            "Keine Fragen erkannt. Jede Frage beginnt mit „## Fragetitel (Punkte)“, Antwortoptionen als „- [x] richtig“ / „- [ ] falsch“."
        }
        val ids = test.questions.indices.map { "item_${it + 1}" }
        ZipOutputStream(file.outputStream()).use { zip ->
            fun entry(name: String, content: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            entry("imsmanifest.xml", manifest(test, ids))
            entry("assessmentTest.xml", assessmentTest(test, ids))
            test.questions.forEachIndexed { i, q -> entry("${ids[i]}.xml", item(ids[i], q)) }
        }
        return test
    }

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
        .replace(Regex("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F]"), "")

    private fun fmt(d: Double) = if (d == d.toLong().toDouble()) d.toLong().toString() else d.toString()

    /** Paragraphs of the prompt as XHTML (bold/italic markers removed, line breaks kept). */
    private fun paragraphs(text: String): String = text.split(Regex("\\n\\s*\\n")).filter { it.isNotBlank() }.joinToString("") { p ->
        "<p>" + p.trim().lines().joinToString("<br/>") { esc(it.replace(Regex("(\\*\\*|__|\\*|`)"), "").trim()) } + "</p>"
    }

    private const val QTI_NS = "xmlns=\"http://www.imsglobal.org/xsd/imsqti_v2p1\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" " +
        "xsi:schemaLocation=\"http://www.imsglobal.org/xsd/imsqti_v2p1 http://www.imsglobal.org/xsd/qti/qtiv2p1/imsqti_v2p1p1.xsd\""

    private fun item(id: String, q: Question): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        append("<assessmentItem $QTI_NS identifier=\"$id\" title=\"${esc(q.title)}\" adaptive=\"false\" timeDependent=\"false\" xml:lang=\"de-DE\">\n")
        when (q.kind) {
            Kind.SINGLE, Kind.MULTIPLE -> {
                val cardinality = if (q.kind == Kind.SINGLE) "single" else "multiple"
                append("<responseDeclaration identifier=\"RESPONSE\" cardinality=\"$cardinality\" baseType=\"identifier\">\n<correctResponse>\n")
                q.options.forEachIndexed { i, o -> if (o.correct) append("<value>choice_${i + 1}</value>\n") }
                append("</correctResponse>\n")
                if (q.kind == Kind.MULTIPLE) {
                    // Points per correct option, the same amount off for a wrong one, never below 0.
                    val right = q.options.count { it.correct }.coerceAtLeast(1)
                    val each = q.points / right
                    append("<mapping lowerBound=\"0\" upperBound=\"${fmt(q.points)}\" defaultValue=\"0\">\n")
                    q.options.forEachIndexed { i, o ->
                        append("<mapEntry mapKey=\"choice_${i + 1}\" mappedValue=\"${fmt(if (o.correct) each else -each)}\"/>\n")
                    }
                    append("</mapping>\n")
                }
                append("</responseDeclaration>\n")
            }
            Kind.TEXT -> {
                append("<responseDeclaration identifier=\"RESPONSE\" cardinality=\"single\" baseType=\"string\">\n")
                append("<correctResponse><value>${esc(q.answers.first())}</value></correctResponse>\n")
                append("<mapping defaultValue=\"0\">\n")
                q.answers.distinct().forEach { a -> append("<mapEntry mapKey=\"${esc(a)}\" mappedValue=\"${fmt(q.points)}\" caseSensitive=\"false\"/>\n") }
                append("</mapping>\n</responseDeclaration>\n")
            }
            Kind.ESSAY -> append("<responseDeclaration identifier=\"RESPONSE\" cardinality=\"single\" baseType=\"string\"/>\n")
        }
        append("<outcomeDeclaration identifier=\"SCORE\" cardinality=\"single\" baseType=\"float\" normalMaximum=\"${fmt(q.points)}\">\n")
        append("<defaultValue><value>0</value></defaultValue>\n</outcomeDeclaration>\n")
        append("<outcomeDeclaration identifier=\"MAXSCORE\" cardinality=\"single\" baseType=\"float\">\n")
        append("<defaultValue><value>${fmt(q.points)}</value></defaultValue>\n</outcomeDeclaration>\n")
        if (q.feedback.isNotBlank()) append("<outcomeDeclaration identifier=\"FEEDBACKBASIC\" cardinality=\"single\" baseType=\"identifier\"/>\n")
        append("<itemBody>\n")
        append(paragraphs(q.prompt)).append('\n')
        when (q.kind) {
            Kind.SINGLE, Kind.MULTIPLE -> {
                val max = if (q.kind == Kind.SINGLE) 1 else 0
                append("<choiceInteraction responseIdentifier=\"RESPONSE\" shuffle=\"true\" maxChoices=\"$max\">\n")
                q.options.forEachIndexed { i, o -> append("<simpleChoice identifier=\"choice_${i + 1}\">${esc(o.text)}</simpleChoice>\n") }
                append("</choiceInteraction>\n")
            }
            Kind.TEXT -> append("<p><textEntryInteraction responseIdentifier=\"RESPONSE\" expectedLength=\"${q.answers.maxOf { it.length }.coerceIn(10, 60)}\"/></p>\n")
            Kind.ESSAY -> append("<extendedTextInteraction responseIdentifier=\"RESPONSE\" expectedLines=\"8\"/>\n")
        }
        append("</itemBody>\n")
        when (q.kind) {
            Kind.SINGLE -> append(processing("match_correct", q))
            Kind.MULTIPLE, Kind.TEXT -> append(processing("map_response", q))
            // Essays are scored by hand; the expectation is shown to the assessors as rubric.
            Kind.ESSAY -> {}
        }
        if (q.feedback.isNotBlank()) {
            append("<modalFeedback outcomeIdentifier=\"FEEDBACKBASIC\" identifier=\"shown\" showHide=\"show\">${paragraphs(q.feedback)}</modalFeedback>\n")
        }
        if (q.kind == Kind.ESSAY && q.expectation.isNotBlank()) {
            append("<!-- Erwartungshorizont: ${q.expectation.replace("--", "–")} -->\n")
        }
        append("</assessmentItem>\n")
    }

    /** Standard response processing, plus feedback after every answer if the question has one. */
    private fun processing(template: String, q: Question): String {
        if (q.feedback.isBlank()) return "<responseProcessing template=\"http://www.imsglobal.org/question/qti_v2p1/rptemplates/$template\"/>\n"
        val score = if (template == "match_correct") {
            "<responseCondition><responseIf><match><variable identifier=\"RESPONSE\"/><correct identifier=\"RESPONSE\"/></match>" +
                "<setOutcomeValue identifier=\"SCORE\"><variable identifier=\"MAXSCORE\"/></setOutcomeValue></responseIf>" +
                "<responseElse><setOutcomeValue identifier=\"SCORE\"><baseValue baseType=\"float\">0</baseValue></setOutcomeValue></responseElse></responseCondition>"
        } else {
            "<responseCondition><responseIf><isNull><variable identifier=\"RESPONSE\"/></isNull>" +
                "<setOutcomeValue identifier=\"SCORE\"><baseValue baseType=\"float\">0</baseValue></setOutcomeValue></responseIf>" +
                "<responseElse><setOutcomeValue identifier=\"SCORE\"><mapResponse identifier=\"RESPONSE\"/></setOutcomeValue></responseElse></responseCondition>"
        }
        return "<responseProcessing>$score<setOutcomeValue identifier=\"FEEDBACKBASIC\"><baseValue baseType=\"identifier\">shown</baseValue></setOutcomeValue></responseProcessing>\n"
    }

    private fun assessmentTest(test: Test, ids: List<String>): String = buildString {
        val total = test.questions.sumOf { it.points }
        append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        append("<assessmentTest $QTI_NS identifier=\"test_${UUID.randomUUID().toString().take(8)}\" title=\"${esc(test.title)}\">\n")
        append("<outcomeDeclaration identifier=\"SCORE\" cardinality=\"single\" baseType=\"float\" normalMaximum=\"${fmt(total)}\"><defaultValue><value>0</value></defaultValue></outcomeDeclaration>\n")
        append("<outcomeDeclaration identifier=\"MAXSCORE\" cardinality=\"single\" baseType=\"float\"><defaultValue><value>${fmt(total)}</value></defaultValue></outcomeDeclaration>\n")
        append("<testPart identifier=\"part_1\" navigationMode=\"nonlinear\" submissionMode=\"simultaneous\">\n")
        append("<assessmentSection identifier=\"section_1\" title=\"${esc(test.title)}\" visible=\"true\">\n")
        ids.forEach { append("<assessmentItemRef identifier=\"$it\" href=\"$it.xml\"/>\n") }
        append("</assessmentSection>\n</testPart>\n")
        append("<outcomeProcessing><setOutcomeValue identifier=\"SCORE\"><sum><testVariables variableIdentifier=\"SCORE\"/></sum></setOutcomeValue></outcomeProcessing>\n")
        append("</assessmentTest>\n")
    }

    private fun manifest(test: Test, ids: List<String>): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        append("<manifest xmlns=\"http://www.imsglobal.org/xsd/imscp_v1p1\" xmlns:imsmd=\"http://ltsc.ieee.org/xsd/LOM\" ")
        append("xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" identifier=\"manifest_${UUID.randomUUID()}\" ")
        append("xsi:schemaLocation=\"http://www.imsglobal.org/xsd/imscp_v1p1 http://www.imsglobal.org/xsd/qti/qtiv2p1/qtiv2p1_imscpv1p2_v1p0.xsd\">\n")
        append("<metadata><schema>QTIv2.1 Package</schema><schemaversion>1.0.0</schemaversion>")
        append("<imsmd:lom><imsmd:general><imsmd:title><imsmd:string language=\"de\">${esc(test.title)}</imsmd:string></imsmd:title></imsmd:general></imsmd:lom></metadata>\n")
        append("<organizations/>\n<resources>\n")
        append("<resource identifier=\"res_test\" type=\"imsqti_test_xmlv2p1\" href=\"assessmentTest.xml\">\n<file href=\"assessmentTest.xml\"/>\n")
        ids.forEach { append("<dependency identifierref=\"res_$it\"/>\n") }
        append("</resource>\n")
        ids.forEach { append("<resource identifier=\"res_$it\" type=\"imsqti_item_xmlv2p1\" href=\"$it.xml\">\n<file href=\"$it.xml\"/>\n</resource>\n") }
        append("</resources>\n</manifest>\n")
    }

    /** How the model writes a test (for the document guide). */
    const val HELP = "OPAL-/ONYX-Tests (QTI 2.1): create_document mit format=qti; content ist Markdown: „# Titel des Tests“, " +
        "jede Frage beginnt mit „## Fragetitel (2 Punkte)“, darunter der Fragetext. Antwortoptionen als „- [x] richtig“ und " +
        "„- [ ] falsch“ (eine richtige = Einfachauswahl, mehrere = Mehrfachauswahl). Kurzantwort: Zeile „Antwort: Begriff | " +
        "Schreibvariante“. Ohne Optionen und ohne Antwort wird die Frage zur Freitextaufgabe; „Erwartung: …“ hält die " +
        "Musterlösung fest. Optional „Feedback: …“ (Erklärung nach dem Beantworten). Die ZIP-Datei lässt sich in OPAL über " +
        "„Importieren“ als Test-Lernressource (ONYX) hochladen. Auf Wunsch zusätzlich ein Lösungsblatt als PDF erstellen. "
}
