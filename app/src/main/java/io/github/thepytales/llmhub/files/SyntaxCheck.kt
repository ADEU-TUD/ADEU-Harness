package io.github.thepytales.llmhub.files

import kotlinx.serialization.json.Json
import org.xml.sax.SAXParseException
import java.io.ByteArrayInputStream
import java.io.File
import java.util.concurrent.TimeUnit
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Quick syntax check after the agent wrote a file: JSON, XML (also SVG, XAML, project files), YAML (indentation),
 * Jupyter notebooks, CSV (number of columns) and Python (only if a Python interpreter is installed). Returns a short
 * German description of the first problem, or null.
 */
object SyntaxCheck {
    private val XML = setOf("xml", "svg", "xaml", "csproj", "vbproj", "fsproj", "props", "targets", "resx", "xhtml", "plist", "xsd", "xsl", "xslt", "kml", "gpx")

    fun check(file: File, text: String): String? = runCatching {
        when (file.extension.lowercase()) {
            "json", "geojson" -> json(text)
            "ipynb" -> json(text) ?: Notebook.problem(text)
            in XML -> xml(text)
            "yaml", "yml" -> yaml(text)
            "csv" -> csv(text, null)
            "tsv" -> csv(text, '\t')
            "py" -> python(file)
            else -> null
        }
    }.getOrNull()

    fun json(text: String): String? = try {
        Json.parseToJsonElement(text)
        null
    } catch (e: Exception) {
        "Ungültiges JSON: " + (e.message ?: e.javaClass.simpleName).lines().first().take(300)
    }

    fun xml(text: String): String? = try {
        val f = DocumentBuilderFactory.newInstance()
        f.isNamespaceAware = true
        runCatching { f.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) }
        runCatching { f.isExpandEntityReferences = false }
        val builder = f.newDocumentBuilder()
        builder.setErrorHandler(null)
        builder.setEntityResolver { _, _ -> org.xml.sax.InputSource(java.io.StringReader("")) }
        builder.parse(ByteArrayInputStream(text.removePrefix("﻿").toByteArray(Charsets.UTF_8)))
        null
    } catch (e: SAXParseException) {
        "Ungültiges XML in Zeile ${e.lineNumber}, Spalte ${e.columnNumber}: ${e.message}"
    } catch (e: Exception) {
        "Ungültiges XML: ${e.message}"
    }

    /**
     * Basic YAML check without a parser: tabs in the indentation and lines that are indented deeper than the line
     * before although it does not open a block (key ending with ":", list item, block scalar, open bracket).
     */
    fun yaml(text: String): String? {
        var prevIndent = 0
        var prevOpens = true
        var scalarIndent = -1
        var flow = 0
        for ((i, raw) in text.lines().withIndex()) {
            val line = raw.trimEnd()
            val content = line.trimStart()
            if (content.isEmpty() || content.startsWith("#") || content == "---" || content == "...") continue
            val indent = line.length - content.length
            if (scalarIndent >= 0) {
                if (indent > scalarIndent) continue
                scalarIndent = -1
            }
            if (raw.substring(0, indent).contains('\t')) return "YAML: Tabulator in der Einrückung in Zeile ${i + 1} – YAML erlaubt nur Leerzeichen."
            if (flow == 0 && indent > prevIndent && !prevOpens) return "YAML: Zeile ${i + 1} ist vermutlich falsch eingerückt – tiefer als die Zeile davor, die einen einfachen Wert hat und keinen Block öffnet."
            val noComment = content.replace(Regex("\\s+#.*$"), "")
            flow += noComment.count { it == '[' || it == '{' } - noComment.count { it == ']' || it == '}' }
            if (flow < 0) flow = 0
            val blockScalar = Regex("(:|^-)\\s*[|>][+-]?\\d*$").containsMatchIn(noComment)
            if (blockScalar) scalarIndent = indent
            // Only "key: value" (a scalar value) closes the line; keys, list items, block scalars and plain text open one.
            prevOpens = blockScalar || flow > 0 || !Regex("^[^:#\\-\\[{][^:#]*:\\s+[^&!\\s|>]").containsMatchIn(noComment)
            prevIndent = indent
        }
        return null
    }

    fun csv(text: String, separator: Char?): String? {
        val lines = text.removePrefix("﻿").lines().filter { it.isNotBlank() }
        if (lines.size < 2) return null
        val sep = separator ?: listOf(';', ',', '\t').maxByOrNull { s -> lines.first().count { it == s } } ?: ';'
        // Fields per record; quoted fields may contain separators and line breaks.
        val counts = mutableListOf<Pair<Int, Int>>()
        var quoted = false
        var fields = 1
        var startLine = 1
        text.removePrefix("﻿").lines().forEachIndexed { i, line ->
            if (!quoted && line.isBlank()) return@forEachIndexed
            if (!quoted) { fields = 1; startLine = i + 1 }
            for (ch in line) {
                if (ch == '"') quoted = !quoted else if (ch == sep && !quoted) fields++
            }
            if (!quoted) counts += startLine to fields
        }
        if (quoted) return "CSV: Ein Anführungszeichen ab Zeile $startLine wird nicht geschlossen."
        val expected = counts.first().second
        val wrong = counts.firstOrNull { it.second != expected } ?: return null
        val sepName = when (sep) { ';' -> "Semikolon"; ',' -> "Komma"; else -> "Tabulator" }
        return "CSV: Zeile ${wrong.first} hat ${wrong.second} Felder, die Kopfzeile $expected (Trennzeichen $sepName)."
    }

    @Volatile
    private var pythonExe: String? = null

    @Volatile
    private var pythonChecked = false

    /** A working Python on the PATH (not the Microsoft Store placeholder), or null. */
    private fun python(): String? {
        if (pythonChecked) return pythonExe
        val names = if (File.separatorChar == '\\') listOf("python.exe", "python3.exe", "py.exe") else listOf("python3", "python")
        val dirs = System.getenv("PATH").orEmpty().split(File.pathSeparatorChar).filter { it.isNotBlank() }
        pythonExe = names.asSequence().flatMap { n -> dirs.asSequence().map { File(it, n) } }.filter { it.isFile }.firstOrNull { exe ->
            runCatching {
                val p = ProcessBuilder(exe.path, "-c", "print('ok')").redirectErrorStream(true).start()
                val out = p.inputStream.bufferedReader().readText()
                p.waitFor(10, TimeUnit.SECONDS) && p.exitValue() == 0 && out.trim() == "ok"
            }.getOrDefault(false)
        }?.path
        pythonChecked = true
        return pythonExe
    }

    private fun python(file: File): String? {
        val exe = python() ?: return null
        val p = ProcessBuilder(exe, "-c", "import sys\ncompile(open(sys.argv[1], 'rb').read(), sys.argv[1], 'exec')", file.absolutePath)
            .redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText()
        if (!p.waitFor(20, TimeUnit.SECONDS)) { p.destroyForcibly(); return null }
        if (p.exitValue() == 0) return null
        val lines = out.lines().filter { it.isNotBlank() }
        val where = lines.lastOrNull { it.trimStart().startsWith("File ") }?.let { Regex("line (\\d+)").find(it)?.groupValues?.get(1) }
        return "Python-Syntaxfehler" + (where?.let { " in Zeile $it" } ?: "") + ": " + (lines.lastOrNull() ?: "unbekannt").take(300)
    }
}
