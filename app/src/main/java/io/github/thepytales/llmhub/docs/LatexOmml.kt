package io.github.thepytales.llmhub.docs

/**
 * Formulas in LaTeX as Word formulas (Office Math, OMML) – AP-16 Stufe 2: fractions, roots, sub- and superscripts,
 * sums/integrals with limits, Greek letters and operators, \text, \bar/\hat/\vec, brackets. Word shows and edits them
 * like formulas typed there. What it does not know stays as plain text inside the formula.
 */
object LatexOmml {
    const val NS = "http://schemas.openxmlformats.org/officeDocument/2006/math"

    private val INLINE = Regex("(?<![\\\\$])\\$(?!\\$)([^$\\n]+?)(?<!\\\\)\\$(?!\\$)|\\\\\\((.+?)\\\\\\)")
    private val DISPLAY = Regex("^\\s*(?:\\$\\$([\\s\\S]+?)\\$\\$|\\\\\\[([\\s\\S]+?)\\\\])\\s*$")

    /** Pieces of a text: plain text (false) and formulas (true) in their order. */
    fun split(text: String): List<Pair<String, Boolean>> {
        val out = mutableListOf<Pair<String, Boolean>>()
        var at = 0
        for (m in INLINE.findAll(text)) {
            val tex = m.groupValues[1].ifEmpty { m.groupValues[2] }
            // „5 $ und 6 $“ is money, not a formula: formulas do not start or end with a space.
            if (tex.isBlank() || tex.first() == ' ' || tex.last() == ' ') continue
            if (m.range.first > at) out += text.substring(at, m.range.first) to false
            out += tex to true
            at = m.range.last + 1
        }
        if (at < text.length) out += text.substring(at) to false
        return out
    }

    /** The formula of a paragraph that is only a display formula ($$…$$ or \[…\]), else null. */
    fun display(text: String): String? = DISPLAY.matchEntire(text)?.let { it.groupValues[1].ifEmpty { it.groupValues[2] }.trim() }

    /** An inline formula. */
    fun inline(tex: String): String = "<m:oMath>${convert(tex)}</m:oMath>"

    /** A formula paragraph (centered, own line). */
    fun paragraph(tex: String): String = "<w:p><m:oMathPara><m:oMath>${convert(tex)}</m:oMath></m:oMathPara></w:p>"

    // ── LaTeX → OMML ──────────────────────────────────────────────────────────────────────────────────────

    private val GREEK = mapOf(
        "alpha" to "α", "beta" to "β", "gamma" to "γ", "delta" to "δ", "epsilon" to "ϵ", "varepsilon" to "ε", "zeta" to "ζ", "eta" to "η",
        "theta" to "θ", "vartheta" to "ϑ", "iota" to "ι", "kappa" to "κ", "lambda" to "λ", "mu" to "μ", "nu" to "ν", "xi" to "ξ", "pi" to "π",
        "rho" to "ρ", "sigma" to "σ", "tau" to "τ", "upsilon" to "υ", "phi" to "ϕ", "varphi" to "φ", "chi" to "χ", "psi" to "ψ", "omega" to "ω",
        "Gamma" to "Γ", "Delta" to "Δ", "Theta" to "Θ", "Lambda" to "Λ", "Xi" to "Ξ", "Pi" to "Π", "Sigma" to "Σ", "Phi" to "Φ", "Psi" to "Ψ", "Omega" to "Ω",
    )
    private val SYMBOL = mapOf(
        "cdot" to "·", "times" to "×", "div" to "÷", "pm" to "±", "mp" to "∓", "leq" to "≤", "le" to "≤", "geq" to "≥", "ge" to "≥", "neq" to "≠",
        "ne" to "≠", "approx" to "≈", "sim" to "∼", "equiv" to "≡", "propto" to "∝", "infty" to "∞", "partial" to "∂", "nabla" to "∇",
        "rightarrow" to "→", "to" to "→", "Rightarrow" to "⇒", "leftarrow" to "←", "Leftarrow" to "⇐", "leftrightarrow" to "↔", "Leftrightarrow" to "⇔",
        "in" to "∈", "notin" to "∉", "subset" to "⊂", "subseteq" to "⊆", "cup" to "∪", "cap" to "∩", "forall" to "∀", "exists" to "∃",
        "ldots" to "…", "cdots" to "⋯", "dots" to "…", "circ" to "∘", "emptyset" to "∅", "neg" to "¬", "land" to "∧", "lor" to "∨",
        "langle" to "⟨", "rangle" to "⟩", "%" to "%", "{" to "{", "}" to "}", "," to " ", ";" to " ", "quad" to " ", "qquad" to "  ", "!" to "",
        "degree" to "°", "prime" to "′", "mid" to "∣", "perp" to "⊥", "parallel" to "∥", "ell" to "ℓ", "hbar" to "ℏ",
    )
    private val NARY = mapOf("sum" to "∑", "prod" to "∏", "int" to "∫", "iint" to "∬", "oint" to "∮", "bigcup" to "⋃", "bigcap" to "⋂")
    private val FUNCTIONS = setOf("sin", "cos", "tan", "cot", "log", "ln", "exp", "lim", "max", "min", "sup", "inf", "det", "arg", "deg", "Pr", "var", "cov")
    private val ACCENTS = mapOf("bar" to "‾", "overline" to "‾", "hat" to "^", "widehat" to "^", "tilde" to "~", "widetilde" to "~", "vec" to "→", "dot" to "˙", "ddot" to "¨")

    private class Reader(val s: String) {
        var i = 0
        fun done() = i >= s.length
        fun peek(): Char = s[i]
        fun skipSpaces() { while (i < s.length && s[i] == ' ') i++ }
    }

    fun convert(tex: String): String = runCatching { seq(Reader(tex.trim()), stop = null) }.getOrElse { r(tex) }

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    /** A run of math text; [plain] for upright text (\text, function names). */
    private fun r(t: String, plain: Boolean = false): String =
        if (t.isEmpty()) "" else "<m:r>" + (if (plain) "<m:rPr><m:sty m:val=\"p\"/></m:rPr>" else "") + "<m:t xml:space=\"preserve\">${esc(t)}</m:t></m:r>"

    /** Elements up to [stop] (or the end). */
    private fun seq(rd: Reader, stop: Char?): String = buildString {
        while (!rd.done()) {
            val c = rd.peek()
            if (stop != null && c == stop) { rd.i++; return@buildString }
            if (c == '}' && stop == null) { rd.i++; continue }
            val base = atom(rd) ?: continue
            append(scripts(rd, base))
        }
    }

    /** An argument: {…} or a single atom. */
    private fun arg(rd: Reader): String {
        rd.skipSpaces()
        if (rd.done()) return ""
        if (rd.peek() == '{') { rd.i++; return seq(rd, '}') }
        return atom(rd).orEmpty()
    }

    private fun command(rd: Reader): String {
        val start = rd.i
        if (rd.i < rd.s.length && !rd.s[rd.i].isLetter()) { rd.i++; return rd.s.substring(start, rd.i) }
        while (rd.i < rd.s.length && rd.s[rd.i].isLetter()) rd.i++
        return rd.s.substring(start, rd.i)
    }

    /** One element: a letter/number run, a command, a group, a bracket. */
    private fun atom(rd: Reader): String? {
        val c = rd.peek()
        when {
            c == ' ' -> { rd.i++; return null }
            c == '{' -> { rd.i++; return seq(rd, '}') }
            c == '^' || c == '_' -> return ""
            c == '\\' -> {
                rd.i++
                val name = command(rd)
                return when {
                    name == "frac" || name == "dfrac" || name == "tfrac" -> { val n = arg(rd); val d = arg(rd); "<m:f><m:num>$n</m:num><m:den>$d</m:den></m:f>" }
                    name == "binom" -> { val n = arg(rd); val k = arg(rd); "<m:d><m:e><m:f><m:fPr><m:type m:val=\"noBar\"/></m:fPr><m:num>$n</m:num><m:den>$k</m:den></m:f></m:e></m:d>" }
                    name == "sqrt" -> {
                        rd.skipSpaces()
                        val deg = if (!rd.done() && rd.peek() == '[') { rd.i++; seq(rd, ']') } else ""
                        val e = arg(rd)
                        if (deg.isEmpty()) "<m:rad><m:radPr><m:degHide m:val=\"1\"/></m:radPr><m:deg/><m:e>$e</m:e></m:rad>" else "<m:rad><m:deg>$deg</m:deg><m:e>$e</m:e></m:rad>"
                    }
                    name in NARY -> nary(rd, NARY.getValue(name))
                    name in FUNCTIONS -> {
                        val fname = r(name, plain = true)
                        "<m:func><m:fName>${scripts(rd, fname, limits = name == "lim" || name == "max" || name == "min")}</m:fName><m:e>${arg(rd)}</m:e></m:func>"
                    }
                    name in ACCENTS -> {
                        val e = arg(rd)
                        if (name == "bar" || name == "overline") "<m:bar><m:barPr><m:pos m:val=\"top\"/></m:barPr><m:e>$e</m:e></m:bar>"
                        else "<m:acc><m:accPr><m:chr m:val=\"${ACCENTS.getValue(name)}\"/></m:accPr><m:e>$e</m:e></m:acc>"
                    }
                    name == "text" || name == "mathrm" || name == "textrm" || name == "operatorname" || name == "mathbf" || name == "textbf" -> {
                        rd.skipSpaces()
                        val raw = if (!rd.done() && rd.peek() == '{') { rd.i++; val st = rd.i; var depth = 1; while (!rd.done() && depth > 0) { if (rd.peek() == '{') depth++; if (rd.peek() == '}') depth--; rd.i++ }; rd.s.substring(st, rd.i - 1) } else ""
                        r(raw, plain = true)
                    }
                    name == "left" -> {
                        val open = delimiter(rd)
                        val inner = StringBuilder()
                        var close = ")"
                        while (!rd.done()) {
                            if (rd.s.startsWith("\\right", rd.i)) { rd.i += 6; close = delimiter(rd); break }
                            val a = atom(rd) ?: continue
                            inner.append(scripts(rd, a))
                        }
                        "<m:d><m:dPr><m:begChr m:val=\"${esc(open)}\"/><m:endChr m:val=\"${esc(close)}\"/></m:dPr><m:e>$inner</m:e></m:d>"
                    }
                    name == "right" -> { delimiter(rd); "" }
                    name in GREEK -> r(GREEK.getValue(name))
                    name in SYMBOL -> r(SYMBOL.getValue(name))
                    name == "\\" -> r(" ")
                    else -> r(name, plain = true)
                }
            }
            c.isDigit() || c == '.' || c == ',' && rd.i + 1 < rd.s.length && rd.s[rd.i + 1].isDigit() -> {
                val st = rd.i
                while (rd.i < rd.s.length && (rd.s[rd.i].isDigit() || rd.s[rd.i] == '.' || rd.s[rd.i] == ',' && rd.i + 1 < rd.s.length && rd.s[rd.i + 1].isDigit())) rd.i++
                return r(rd.s.substring(st, rd.i))
            }
            else -> { rd.i++; return r(c.toString()) }
        }
    }

    private fun delimiter(rd: Reader): String {
        rd.skipSpaces()
        if (rd.done()) return ""
        if (rd.peek() == '\\') {
            rd.i++
            val n = command(rd)
            return when (n) { "{" -> "{"; "}" -> "}"; "langle" -> "⟨"; "rangle" -> "⟩"; "|" -> "‖"; else -> n }
        }
        val ch = rd.peek()
        rd.i++
        return if (ch == '.') "" else ch.toString()
    }

    /** ∑, ∫ …: with the limits that follow (_ and ^) and the expression after it. */
    private fun nary(rd: Reader, chr: String): String {
        var sub = ""
        var sup = ""
        repeat(2) {
            rd.skipSpaces()
            if (!rd.done() && rd.peek() == '_') { rd.i++; sub = arg(rd) } else if (!rd.done() && rd.peek() == '^') { rd.i++; sup = arg(rd) }
        }
        rd.skipSpaces()
        val e = if (rd.done()) "" else atom(rd)?.let { scripts(rd, it) }.orEmpty()
        val hide = (if (sub.isEmpty()) "<m:subHide m:val=\"1\"/>" else "") + (if (sup.isEmpty()) "<m:supHide m:val=\"1\"/>" else "")
        return "<m:nary><m:naryPr><m:chr m:val=\"$chr\"/><m:limLoc m:val=\"undOvr\"/>$hide</m:naryPr><m:sub>$sub</m:sub><m:sup>$sup</m:sup><m:e>$e</m:e></m:nary>"
    }

    /** Sub- and superscripts after [base]. */
    private fun scripts(rd: Reader, base: String, limits: Boolean = false): String {
        var sub: String? = null
        var sup: String? = null
        while (!rd.done() && (rd.peek() == '_' || rd.peek() == '^')) {
            val c = rd.peek()
            rd.i++
            if (c == '_') sub = arg(rd) else sup = arg(rd)
        }
        return when {
            limits && sub != null -> "<m:limLow><m:e>$base</m:e><m:lim>$sub</m:lim></m:limLow>"
            sub != null && sup != null -> "<m:sSubSup><m:e>$base</m:e><m:sub>$sub</m:sub><m:sup>$sup</m:sup></m:sSubSup>"
            sub != null -> "<m:sSub><m:e>$base</m:e><m:sub>$sub</m:sub></m:sSub>"
            sup != null -> "<m:sSup><m:e>$base</m:e><m:sup>$sup</m:sup></m:sSup>"
            else -> base
        }
    }

    // ── OMML → LaTeX (reading Word formulas back) ─────────────────────────────────────────────────────────

    private val NARY_BACK = NARY.entries.associate { (k, v) -> v to k }
    private val ACCENT_BACK = mapOf("^" to "hat", "~" to "tilde", "→" to "vec", "⃗" to "vec", "˙" to "dot", "¨" to "ddot", "‾" to "bar", "̅" to "bar", "̂" to "hat", "̃" to "tilde")

    private fun kids(e: org.w3c.dom.Element): List<org.w3c.dom.Element> = (0 until e.childNodes.length).mapNotNull { e.childNodes.item(it) as? org.w3c.dom.Element }
    private fun kid(e: org.w3c.dom.Element, name: String) = kids(e).firstOrNull { it.localName == name }
    private fun valOf(e: org.w3c.dom.Element?): String? = e?.let { el -> (0 until el.attributes.length).map { el.attributes.item(it) }.firstOrNull { it.localName == "val" }?.nodeValue }

    /** A Word formula (m:oMath) as LaTeX. */
    fun toLatex(e: org.w3c.dom.Element): String = kids(e).joinToString("") { node(it) }.trim()

    private fun part(e: org.w3c.dom.Element?, name: String): String = e?.let { kid(it, name) }?.let { toLatex(it) }.orEmpty()

    private fun group(s: String) = if (s.length == 1) s else "{$s}"

    private fun node(e: org.w3c.dom.Element): String = when (e.localName) {
        "r" -> kids(e).filter { it.localName == "t" }.joinToString("") { it.textContent }.let { t ->
            val plain = kid(e, "rPr")?.let { valOf(kid(it, "sty")) } == "p"
            if (plain && t.length > 1 && t.all { it.isLetter() }) (if (t in FUNCTIONS) "\\$t" else "\\text{$t}") else t
        }
        "f" -> if (kid(e, "fPr")?.let { valOf(kid(it, "type")) } == "noBar") "\\binom{${part(e, "num")}}{${part(e, "den")}}" else "\\frac{${part(e, "num")}}{${part(e, "den")}}"
        "rad" -> part(e, "deg").let { d -> if (d.isEmpty()) "\\sqrt{${part(e, "e")}}" else "\\sqrt[$d]{${part(e, "e")}}" }
        "sSup" -> group(part(e, "e")) + "^" + group(part(e, "sup"))
        "sSub" -> group(part(e, "e")) + "_" + group(part(e, "sub"))
        "sSubSup" -> group(part(e, "e")) + "_" + group(part(e, "sub")) + "^" + group(part(e, "sup"))
        "nary" -> {
            val chr = kid(e, "naryPr")?.let { valOf(kid(it, "chr")) } ?: "∫"
            val name = NARY_BACK[chr]?.let { "\\$it" } ?: chr
            val sub = part(e, "sub")
            val sup = part(e, "sup")
            name + (if (sub.isNotEmpty()) "_" + group(sub) else "") + (if (sup.isNotEmpty()) "^" + group(sup) else "") + " " + part(e, "e")
        }
        "func" -> part(e, "fName").let { n -> (if (n.startsWith("\\")) n else "\\operatorname{$n}") + " " + part(e, "e") }
        "d" -> {
            val pr = kid(e, "dPr")
            val open = valOf(pr?.let { kid(it, "begChr") }) ?: "("
            val close = valOf(pr?.let { kid(it, "endChr") }) ?: ")"
            fun lx(c: String) = when (c) { "{" -> "\\{"; "}" -> "\\}"; "" -> "."; "⟨" -> "\\langle"; "⟩" -> "\\rangle"; else -> c }
            "\\left${lx(open)}" + kids(e).filter { it.localName == "e" }.joinToString(",") { toLatex(it) } + "\\right${lx(close)}"
        }
        "acc" -> "\\" + (ACCENT_BACK[kid(e, "accPr")?.let { valOf(kid(it, "chr")) } ?: "^"] ?: "hat") + "{${part(e, "e")}}"
        "bar" -> "\\bar{${part(e, "e")}}"
        "limLow" -> group(part(e, "e")) + "_" + group(part(e, "lim"))
        "limUpp" -> group(part(e, "e")) + "^" + group(part(e, "lim"))
        "box", "borderBox", "groupChr", "e", "oMath" -> toLatex(e)
        else -> if (e.localName.endsWith("Pr")) "" else toLatex(e)
    }
}
