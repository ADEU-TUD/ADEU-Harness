package io.github.thepytales.llmhub

import com.sun.jna.NativeLibrary
import com.sun.jna.Structure
import com.sun.jna.ptr.IntByReference
import java.io.File
import java.lang.reflect.Modifier
import java.util.regex.Pattern
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Android compiles regular expressions with ICU, which is stricter than Java: an unescaped "}" outside a
 * quantifier, for example, makes the class fail to load on the phone while every desktop test passes. This
 * test loads all classes of the app, collects every regular expression kept in a field (also behind `lazy`)
 * and compiles it with the ICU library of the system. Skipped where no ICU library is installed.
 */
class AndroidRegexTest {
    private companion object {
        /** `Regex(…)`/`Pattern.compile(…)` whose first argument is literal: group 1 = raw string, group 2 = "…" + "…" chain. */
        val LITERAL = Regex("(?:Regex|Pattern\\.compile)\\(\\s*(?:\"\"\"(.*?)\"\"\"|((?:\"(?:[^\"\\\\\\n]|\\\\.)*\"(?:\\s*\\+\\s*)?)+)\\s*(?=[,)]))", RegexOption.DOT_MATCHES_ALL)
        val PIECE = Regex("\"((?:[^\"\\\\\\n]|\\\\.)*)\"")
    }

    @Structure.FieldOrder("line", "offset", "preContext", "postContext")
    class ParseError : Structure() {
        @JvmField var line = 0
        @JvmField var offset = 0
        @JvmField var preContext = ShortArray(16)
        @JvmField var postContext = ShortArray(16)
    }

    private fun icu(): Pair<NativeLibrary, String>? {
        for (name in listOf("icui18n", "icuin")) {
            val lib = runCatching { NativeLibrary.getInstance(name) }.getOrNull() ?: continue
            for (v in (85 downTo 60).map { "_$it" } + "") {
                if (runCatching { lib.getFunction("uregex_open$v") }.isSuccess) return lib to v
            }
        }
        return null
    }

    @Test
    fun allRegexesCompileWithIcu() {
        val (lib, suffix) = icu() ?: return println("ICU nicht gefunden – Test übersprungen")
        val open = lib.getFunction("uregex_open$suffix")
        val close = lib.getFunction("uregex_close$suffix")
        val patterns = LinkedHashMap<String, String>()
        val roots = javaClass.classLoader.getResources("io/github/thepytales/llmhub").toList()
            .filter { it.protocol == "file" }.map { File(it.toURI()) }
        for (root in roots) {
            val base = root.parentFile.parentFile.parentFile.parentFile
            root.walkTopDown().filter { it.isFile && it.name.endsWith(".class") && !it.path.contains("/test/") }.forEach { f ->
                val name = f.relativeTo(base).path.removeSuffix(".class").replace(File.separatorChar, '.')
                val cls = runCatching { Class.forName(name, true, javaClass.classLoader) }.getOrNull() ?: return@forEach
                val holder = runCatching { cls.getDeclaredField("INSTANCE").apply { isAccessible = true }.get(null) }.getOrNull()
                for (field in runCatching { cls.declaredFields }.getOrDefault(emptyArray())) {
                    val static = Modifier.isStatic(field.modifiers)
                    if (!static && holder == null) continue
                    val value = runCatching { field.isAccessible = true; field.get(if (static) null else holder) }.getOrNull() ?: continue
                    val v = if (value is Lazy<*>) runCatching { value.value }.getOrNull() else value
                    val p = when (v) {
                        is Regex -> v.pattern
                        is Pattern -> v.pattern()
                        else -> null
                    } ?: continue
                    patterns.putIfAbsent(p, "$name.${field.name}")
                }
            }
        }
        val failures = patterns.mapNotNull { (p, where) -> icuError(open, close, p)?.let { "$where (ICU-Fehler $it): $p" } }
        println("${patterns.size} reguläre Ausdrücke mit ICU geprüft")
        assertTrue(patterns.size > 100, "zu wenige Ausdrücke gefunden: ${patterns.size}")
        assertTrue(failures.isEmpty(), "Auf Android ungültig:\n" + failures.joinToString("\n"))
    }

    private fun icuError(open: com.sun.jna.Function, close: com.sun.jna.Function, pattern: String): Int? {
        val units = pattern.map { it.code.toShort() }.toShortArray()
        val error = IntByReference(0)
        val handle = open.invokePointer(arrayOf(units, units.size, 0, ParseError(), error))
        if (handle != null) close.invokeVoid(arrayOf(handle))
        return error.value.takeIf { it > 0 }
    }

    /**
     * Expressions built inside functions (`Regex("…")` in a function body) are in no field, so the test above does not
     * see them – the IMAP client failed on the phone that way („Syntax error in regexp pattern near index 11“). This
     * test reads the literals from the Kotlin sources instead; literals with `$`-templates are left out.
     */
    @Test
    fun allRegexLiteralsInTheSourcesCompileWithIcu() {
        val (lib, suffix) = icu() ?: return println("ICU nicht gefunden – Test übersprungen")
        val open = lib.getFunction("uregex_open$suffix")
        val close = lib.getFunction("uregex_close$suffix")
        val literals = LinkedHashMap<String, String>()
        for (root in listOf(File("../app/src/main/java"), File("src/main/kotlin"))) {
            root.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { f ->
                val src = f.readText()
                for (m in LITERAL.findAll(src)) {
                    val pattern = (if (m.groups[1] != null) m.groupValues[1].takeIf { "\${" !in it } else PIECE.findAll(m.groupValues[2]).map { unescape(it.groupValues[1]) }.toList().takeIf { null !in it }?.joinToString("")) ?: continue
                    val line = src.substring(0, m.range.first).count { it == '\n' } + 1
                    literals.putIfAbsent(pattern, "${f.path}:$line")
                }
            }
        }
        val failures = literals.mapNotNull { (p, where) -> icuError(open, close, p)?.let { "$where (ICU-Fehler $it): $p" } }
        println("${literals.size} Regex-Literale aus den Quellen mit ICU geprüft")
        assertTrue(literals.size > 300, "zu wenige Literale gefunden: ${literals.size}")
        assertTrue(failures.isEmpty(), "Auf Android ungültig:\n" + failures.joinToString("\n"))
    }

    /** The value of a Kotlin "…" literal, or null if it contains a `$` template. */
    private fun unescape(body: String): String? {
        val out = StringBuilder()
        var i = 0
        while (i < body.length) {
            val c = body[i]
            if (c == '$' && i + 1 < body.length && (body[i + 1] == '{' || body[i + 1].isLetter())) return null
            if (c != '\\' || i + 1 >= body.length) { out.append(c); i++; continue }
            when (val e = body[i + 1]) {
                'n' -> out.append('\n')
                't' -> out.append('\t')
                'r' -> out.append('\r')
                'u' -> { out.append(body.substring(i + 2, i + 6).toInt(16).toChar()); i += 4 }
                else -> out.append(e)
            }
            i += 2
        }
        return out.toString()
    }

    @Test
    fun icuRejectsWhatAndroidRejects() {
        val (lib, suffix) = icu() ?: return
        val units = "(\\{[^}]*})".map { it.code.toShort() }.toShortArray()
        val error = IntByReference(0)
        lib.getFunction("uregex_open$suffix").invokePointer(arrayOf(units, units.size, 0, ParseError(), error))
        assertTrue(error.value > 0, "ICU müsste ein unmaskiertes } ablehnen")
    }
}
