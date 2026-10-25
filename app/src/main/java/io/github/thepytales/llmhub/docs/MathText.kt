package io.github.thepytales.llmhub.docs

/**
 * Formulas in LaTeX ($…$, $$…$$, \(…\), \[…\]) as readable Unicode text – for the chat and for documents, where
 * no formula renderer is available: x̄ = ∑xᵢ / n, α ≤ 0,05, √(s²).
 */
object MathText {
    private val SYMBOLS = linkedMapOf(
        "\\alpha" to "α", "\\beta" to "β", "\\gamma" to "γ", "\\delta" to "δ", "\\epsilon" to "ε", "\\varepsilon" to "ε", "\\zeta" to "ζ",
        "\\eta" to "η", "\\theta" to "θ", "\\vartheta" to "ϑ", "\\iota" to "ι", "\\kappa" to "κ", "\\lambda" to "λ", "\\mu" to "μ", "\\nu" to "ν",
        "\\xi" to "ξ", "\\pi" to "π", "\\rho" to "ρ", "\\sigma" to "σ", "\\tau" to "τ", "\\upsilon" to "υ", "\\phi" to "φ", "\\varphi" to "φ",
        "\\chi" to "χ", "\\psi" to "ψ", "\\omega" to "ω", "\\Gamma" to "Γ", "\\Delta" to "Δ", "\\Theta" to "Θ", "\\Lambda" to "Λ", "\\Xi" to "Ξ",
        "\\Pi" to "Π", "\\Sigma" to "Σ", "\\Phi" to "Φ", "\\Psi" to "Ψ", "\\Omega" to "Ω",
        "\\cdot" to "·", "\\times" to "×", "\\div" to "÷", "\\pm" to "±", "\\mp" to "∓", "\\leq" to "≤", "\\le" to "≤", "\\geq" to "≥", "\\ge" to "≥",
        "\\neq" to "≠", "\\ne" to "≠", "\\approx" to "≈", "\\sim" to "∼", "\\equiv" to "≡", "\\propto" to "∝", "\\infty" to "∞",
        "\\sum" to "∑", "\\prod" to "∏", "\\int" to "∫", "\\partial" to "∂", "\\nabla" to "∇", "\\rightarrow" to "→", "\\to" to "→",
        "\\Rightarrow" to "⇒", "\\leftarrow" to "←", "\\Leftarrow" to "⇐", "\\leftrightarrow" to "↔", "\\Leftrightarrow" to "⇔", "\\in" to "∈",
        "\\notin" to "∉", "\\subset" to "⊂", "\\subseteq" to "⊆", "\\cup" to "∪", "\\cap" to "∩", "\\forall" to "∀", "\\exists" to "∃",
        "\\ldots" to "…", "\\cdots" to "⋯", "\\dots" to "…", "\\degree" to "°", "\\circ" to "∘", "\\emptyset" to "∅", "\\neg" to "¬",
        "\\land" to "∧", "\\lor" to "∨", "\\langle" to "⟨", "\\rangle" to "⟩", "\\%" to "%", "\\{" to "{", "\\}" to "}",
        "\\," to " ", "\\;" to " ", "\\:" to " ", "\\!" to "", "\\quad" to "  ", "\\qquad" to "    ", "\\left" to "", "\\right" to "",
    )
    private val SUP = mapOf('0' to '⁰', '1' to '¹', '2' to '²', '3' to '³', '4' to '⁴', '5' to '⁵', '6' to '⁶', '7' to '⁷', '8' to '⁸', '9' to '⁹',
        '+' to '⁺', '-' to '⁻', '=' to '⁼', '(' to '⁽', ')' to '⁾', 'n' to 'ⁿ', 'i' to 'ⁱ', 'T' to 'ᵀ', 'a' to 'ᵃ', 'b' to 'ᵇ', 'c' to 'ᶜ',
        'd' to 'ᵈ', 'e' to 'ᵉ', 'k' to 'ᵏ', 'm' to 'ᵐ', 'o' to 'ᵒ', 'p' to 'ᵖ', 'r' to 'ʳ', 's' to 'ˢ', 't' to 'ᵗ', 'x' to 'ˣ', 'y' to 'ʸ', '*' to '*')
    private val SUB = mapOf('0' to '₀', '1' to '₁', '2' to '₂', '3' to '₃', '4' to '₄', '5' to '₅', '6' to '₆', '7' to '₇', '8' to '₈', '9' to '₉',
        '+' to '₊', '-' to '₋', '=' to '₌', '(' to '₍', ')' to '₎', 'a' to 'ₐ', 'e' to 'ₑ', 'h' to 'ₕ', 'i' to 'ᵢ', 'j' to 'ⱼ', 'k' to 'ₖ',
        'l' to 'ₗ', 'm' to 'ₘ', 'n' to 'ₙ', 'o' to 'ₒ', 'p' to 'ₚ', 'r' to 'ᵣ', 's' to 'ₛ', 't' to 'ₜ', 'u' to 'ᵤ', 'v' to 'ᵥ', 'x' to 'ₓ')

    private val MATH = Regex("\\$\\$([\\s\\S]+?)\\$\\$|\\\\\\[([\\s\\S]+?)\\\\]|\\\\\\(([\\s\\S]+?)\\\\\\)|(?<![\\\\\\w$])\\$(?!\\s)([^$\\n]{1,200}?)(?<!\\s)\\$(?![\\w$])")

    /** Replaces the formulas in [text]; text without formulas stays unchanged (also prices like „5 $“). */
    fun inText(text: String): String {
        if ('$' !in text && "\\(" !in text && "\\[" !in text) return text
        return MATH.replace(text) { m ->
            val f = m.groupValues.drop(1).firstOrNull { it.isNotEmpty() } ?: return@replace m.value
            // Only replace what looks like a formula, not „zwischen $5 und $10“.
            if (m.groupValues[4].isNotEmpty() && !Regex("[\\\\^_=+<>]|[a-zA-Z]\\d|\\d[a-zA-Z]").containsMatchIn(f)) return@replace m.value
            toUnicode(f.trim())
        }
    }

    private fun group(s: String, start: Int): Pair<String, Int> {
        if (start >= s.length) return "" to start
        if (s[start] != '{') {
            // A command (\alpha) or a single character.
            if (s[start] == '\\') {
                val m = Regex("\\\\[a-zA-Z]+").find(s, start)
                if (m != null && m.range.first == start) return m.value to m.range.last + 1
            }
            return s[start].toString() to start + 1
        }
        var depth = 0
        for (i in start until s.length) {
            if (s[i] == '{') depth++ else if (s[i] == '}') { depth--; if (depth == 0) return s.substring(start + 1, i) to i + 1 }
        }
        return s.substring(start + 1) to s.length
    }

    private fun script(text: String, map: Map<Char, Char>, mark: String): String {
        val converted = text.map { map[it] }
        return if (converted.all { it != null }) converted.joinToString("") else "$mark(${text})"
    }

    fun toUnicode(latex: String): String {
        var s = latex
        // Structures with arguments first: \frac, \sqrt, \text, accents.
        val out = StringBuilder()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            when {
                s.startsWith("\\frac", i) || s.startsWith("\\dfrac", i) || s.startsWith("\\tfrac", i) -> {
                    val (a, j) = group(s, s.indexOf('{', i).takeIf { it >= 0 } ?: (i + 5))
                    val (b, k) = group(s, j)
                    val na = toUnicode(a); val nb = toUnicode(b)
                    fun wrap(x: String) = if (x.length == 1 || Regex("^[\\w.,]+$").matches(x)) x else "($x)"
                    out.append(wrap(na)).append("/").append(wrap(nb))
                    i = k
                }
                s.startsWith("\\sqrt", i) -> {
                    val (a, j) = group(s, i + 5)
                    val inner = toUnicode(a)
                    out.append("√").append(if (inner.length == 1) inner else "($inner)")
                    i = j
                }
                Regex("^\\\\(text|mathrm|mathit|mathbf|operatorname|textrm|textit)\\{").containsMatchIn(s.substring(i)) -> {
                    val (a, j) = group(s, s.indexOf('{', i))
                    out.append(a)
                    i = j
                }
                Regex("^\\\\(bar|overline|hat|tilde|vec|dot)\\{?").containsMatchIn(s.substring(i)) -> {
                    val name = Regex("^\\\\([a-z]+)").find(s.substring(i))!!.groupValues[1]
                    val (a, j) = group(s, i + name.length + 1)
                    val mark = when (name) { "bar", "overline" -> '̄'; "hat" -> '̂'; "tilde" -> '̃'; "vec" -> '⃗'; else -> '̇' }
                    val inner = toUnicode(a)
                    out.append(inner.map { "$it$mark" }.joinToString(""))
                    i = j
                }
                c == '^' || c == '_' -> {
                    val (a, j) = group(s, i + 1)
                    val inner = toUnicode(a)
                    out.append(script(inner, if (c == '^') SUP else SUB, if (c == '^') "^" else "_"))
                    i = j
                }
                c == '\\' -> {
                    val m = Regex("\\\\([a-zA-Z]+|[,;:!%{}])").find(s, i)
                    if (m != null && m.range.first == i) {
                        out.append(SYMBOLS[m.value] ?: m.groupValues[1])
                        i = m.range.last + 1
                    } else { out.append(c); i++ }
                }
                c == '{' || c == '}' -> i++
                else -> { out.append(c); i++ }
            }
        }
        s = out.toString()
        return s.replace(Regex(" {2,}"), " ").trim()
    }
}
