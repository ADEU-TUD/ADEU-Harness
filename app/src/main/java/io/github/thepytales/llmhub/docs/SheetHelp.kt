package io.github.thepytales.llmhub.docs

/**
 * Help while typing a formula (like the tooltip of Excel and Google Sheets): the function at the cursor with its
 * arguments, the argument being typed, and functions that start with the typed letters.
 */
object SheetHelp {
    /** Arguments of the functions (German names as typed in the editor; [optional] in brackets). */
    val SIGNATURES: Map<String, String> = mapOf(
        "SUMME" to "Zahl1; [Zahl2]; …", "MITTELWERT" to "Zahl1; [Zahl2]; …", "MIN" to "Zahl1; [Zahl2]; …", "MAX" to "Zahl1; [Zahl2]; …",
        "ANZAHL" to "Wert1; [Wert2]; …", "ANZAHL2" to "Wert1; [Wert2]; …", "MEDIAN" to "Zahl1; [Zahl2]; …", "STABW" to "Zahl1; [Zahl2]; …",
        "RUNDEN" to "Zahl; Anzahl_Stellen", "ABRUNDEN" to "Zahl; Anzahl_Stellen", "AUFRUNDEN" to "Zahl; Anzahl_Stellen", "KÜRZEN" to "Zahl; [Anzahl_Stellen]",
        "VRUNDEN" to "Zahl; Vielfaches", "OBERGRENZE" to "Zahl; [Schritt]", "UNTERGRENZE" to "Zahl; [Schritt]",
        "WENN" to "Prüfung; [Dann_Wert]; [Sonst_Wert]", "WENNS" to "Prüfung1; Wert1; [Prüfung2; Wert2]; …",
        "WENNFEHLER" to "Wert; Wert_falls_Fehler", "WENNNV" to "Wert; Wert_bei_NV", "UND" to "Wahrheitswert1; [Wahrheitswert2]; …",
        "ODER" to "Wahrheitswert1; [Wahrheitswert2]; …", "NICHT" to "Wahrheitswert", "XODER" to "Wahrheitswert1; [Wahrheitswert2]; …",
        "ZÄHLENWENN" to "Bereich; Suchkriterien", "ZÄHLENWENNS" to "Kriterienbereich1; Kriterien1; [Kriterienbereich2; Kriterien2]; …",
        "SUMMEWENN" to "Bereich; Suchkriterien; [Summe_Bereich]", "SUMMEWENNS" to "Summe_Bereich; Kriterien_Bereich1; Kriterien1; …",
        "MITTELWERTWENN" to "Bereich; Kriterien; [Mittelwert_Bereich]", "MITTELWERTWENNS" to "Mittelwert_Bereich; Kriterien_Bereich1; Kriterien1; …",
        "MAXWENNS" to "Max_Bereich; Kriterien_Bereich1; Kriterien1; …", "MINWENNS" to "Min_Bereich; Kriterien_Bereich1; Kriterien1; …",
        "SVERWEIS" to "Suchkriterium; Matrix; Spaltenindex; [Bereich_Verweis]", "WVERWEIS" to "Suchkriterium; Matrix; Zeilenindex; [Bereich_Verweis]",
        "XVERWEIS" to "Suchkriterium; Suchmatrix; Rückgabematrix; [wenn_nicht_gefunden]; [Vergleichsmodus]; [Suchmodus]",
        "XVERGLEICH" to "Suchkriterium; Suchmatrix; [Vergleichsmodus]; [Suchmodus]", "VERWEIS" to "Suchkriterium; Suchvektor; [Ergebnisvektor]",
        "INDEX" to "Matrix; Zeile; [Spalte]", "VERGLEICH" to "Suchkriterium; Suchmatrix; [Vergleichstyp]", "WAHL" to "Index; Wert1; [Wert2]; …",
        "INDIREKT" to "Bezug", "ADRESSE" to "Zeile; Spalte; [Abs]; [A1]; [Tabellenname]", "ZEILE" to "[Bezug]", "SPALTE" to "[Bezug]",
        "ZEILEN" to "Matrix", "SPALTEN" to "Matrix",
        "VERKETTEN" to "Text1; [Text2]; …", "TEXTVERKETTEN" to "Trennzeichen; Leer_ignorieren; Text1; [Text2]; …", "LÄNGE" to "Text",
        "LINKS" to "Text; [Anzahl_Zeichen]", "RECHTS" to "Text; [Anzahl_Zeichen]", "TEIL" to "Text; Erstes_Zeichen; Anzahl_Zeichen",
        "GLÄTTEN" to "Text", "SÄUBERN" to "Text", "WECHSELN" to "Text; Alter_Text; Neuer_Text", "ERSETZEN" to "Alter_Text; Erstes_Zeichen; Anzahl_Zeichen; Neuer_Text",
        "FINDEN" to "Suchtext; Text; [Erstes_Zeichen]", "SUCHEN" to "Suchtext; Text; [Erstes_Zeichen]", "GROSS" to "Text", "KLEIN" to "Text",
        "GROSS2" to "Text", "WIEDERHOLEN" to "Text; Multiplikator", "WERT" to "Text", "TEXT" to "Wert; Textformat", "IDENTISCH" to "Text1; Text2",
        "TEXTVOR" to "Text; Trennzeichen; [Instanznummer]", "TEXTNACH" to "Text; Trennzeichen; [Instanznummer]",
        "TEXTTEILEN" to "Text; Spaltentrennzeichen; [Zeilentrennzeichen]", "ZEICHEN" to "Zahl", "CODE" to "Text",
        "PRODUKT" to "Zahl1; [Zahl2]; …", "SUMMENPRODUKT" to "Array1; [Array2]; …", "RANG" to "Zahl; Bezug; [Reihenfolge]",
        "RANG.MITTELW" to "Zahl; Bezug; [Reihenfolge]", "KGRÖSSTE" to "Matrix; k", "KKLEINSTE" to "Matrix; k", "VARIANZ" to "Zahl1; [Zahl2]; …",
        "STABW.N" to "Zahl1; [Zahl2]; …", "MODALWERT" to "Zahl1; [Zahl2]; …", "QUANTIL" to "Matrix; Alpha", "QUARTILE" to "Matrix; Quartil",
        "KORREL" to "Matrix1; Matrix2", "HÄUFIGKEIT" to "Daten; Klassen", "TEILERGEBNIS" to "Funktion; Bezug1; [Bezug2]; …",
        "ANZAHLLEEREZELLEN" to "Bereich", "ISTLEER" to "Wert", "ISTZAHL" to "Wert", "ISTTEXT" to "Wert", "ISTFEHLER" to "Wert", "ISTNV" to "Wert",
        "WURZEL" to "Zahl", "POTENZ" to "Zahl; Potenz", "ABS" to "Zahl", "GANZZAHL" to "Zahl", "REST" to "Zahl; Divisor", "VORZEICHEN" to "Zahl",
        "LN" to "Zahl", "LOG" to "Zahl; [Basis]", "EXP" to "Zahl", "ZUFALLSBEREICH" to "Untere_Zahl; Obere_Zahl",
        "HEUTE" to "", "JETZT" to "", "DATUM" to "Jahr; Monat; Tag", "TAG" to "Zahl", "MONAT" to "Zahl", "JAHR" to "Zahl", "ZEIT" to "Stunde; Minute; Sekunde",
        "STUNDE" to "Zahl", "MINUTE" to "Zahl", "SEKUNDE" to "Zahl", "DATWERT" to "Datumstext", "WOCHENTAG" to "Zahl; [Typ]",
        "KALENDERWOCHE" to "Zahl; [Zahl_Typ]", "DATEDIF" to "Ausgangsdatum; Enddatum; Einheit", "NETTOARBEITSTAGE" to "Ausgangsdatum; Enddatum; [Freie_Tage]",
        "ARBEITSTAG" to "Ausgangsdatum; Tage; [Freie_Tage]", "EDATUM" to "Ausgangsdatum; Monate", "MONATSENDE" to "Ausgangsdatum; Monate", "TAGE" to "Zieldatum; Ausgangsdatum",
        "EINDEUTIG" to "Matrix; [nach_Spalte]; [genau_einmal]", "FILTER" to "Matrix; einschließen; [wenn_leer]",
        "SORTIEREN" to "Matrix; [Sortierindex]; [Sortierreihenfolge]; [nach_Spalte]", "SEQUENZ" to "Zeilen; [Spalten]; [Anfang]; [Schritt]",
        "MTRANS" to "Matrix", "VSTAPELN" to "Matrix1; [Matrix2]; …", "HSTAPELN" to "Matrix1; [Matrix2]; …", "SPALTENWAHL" to "Matrix; Spalte1; [Spalte2]; …",
        "ZEILENWAHL" to "Matrix; Zeile1; [Zeile2]; …",
    )

    /** The function at the cursor: its name, the arguments and which one is being typed (0-based). */
    data class Call(val name: String, val signature: String, val argument: Int, val hint: String?) {
        /** The arguments, the current one marked (for the line under the formula bar). */
        val parts: List<String> get() = signature.split(';').map { it.trim() }.filter { it.isNotEmpty() }
    }

    private val hints: Map<String, String> by lazy { SheetEngine.FUNCTIONS.toMap() }

    /** German name of a function typed in English (SUM → SUMME), so the help shows the editor's names. */
    private val german: Map<String, String> by lazy {
        SIGNATURES.keys.associateBy { SheetEngine.excelName(it) }
    }

    private fun known(word: String): String? {
        val up = word.uppercase()
        return when {
            up in SIGNATURES -> up
            else -> german[SheetEngine.excelName(up)]
        }
    }

    /**
     * The innermost function call around [cursor] in [formula] (e.g. "=WENN(SVERWEIS(A2;" → SVERWEIS, argument 1);
     * null outside of a call or if the text is no formula.
     */
    fun at(formula: String, cursor: Int = formula.length): Call? {
        if (!formula.startsWith("=")) return null
        val end = cursor.coerceIn(0, formula.length)
        // Open calls: position of the name and the number of separators seen so far.
        data class Open(val name: String, var args: Int)
        val stack = ArrayDeque<Open?>()
        var i = 1
        var inString = false
        while (i < end) {
            val ch = formula[i]
            when {
                ch == '"' -> inString = !inString
                inString -> {}
                ch == '(' -> {
                    var j = i - 1
                    while (j >= 1 && (formula[j].isLetterOrDigit() || formula[j] == '.' || formula[j] == '_')) j--
                    val word = formula.substring(j + 1, i)
                    stack.addLast(if (word.isNotEmpty() && word.first().isLetter()) Open(word, 0) else null)
                }
                ch == ')' -> stack.removeLastOrNull()
                ch == ';' || (ch == ',' && !(formula.getOrNull(i - 1)?.isDigit() == true && formula.getOrNull(i + 1)?.isDigit() == true)) -> stack.lastOrNull()?.let { it.args++ }
            }
            i++
        }
        if (inString && stack.isEmpty()) return null
        val open = stack.lastOrNull { it != null } ?: return null
        val name = known(open.name) ?: return Call(open.name.uppercase(), "", open.args, null)
        return Call(name, SIGNATURES[name].orEmpty(), open.args, hints[name])
    }

    /** The letters being typed at [cursor] if they could start a function name ("=SV" → "SV"). */
    fun prefix(formula: String, cursor: Int = formula.length): String? {
        if (!formula.startsWith("=")) return null
        val end = cursor.coerceIn(0, formula.length)
        if (formula.substring(0, end).count { it == '"' } % 2 == 1) return null
        var j = end - 1
        while (j >= 1 && (formula[j].isLetterOrDigit() || formula[j] == '.' || formula[j] == '_' || formula[j] in "ÄÖÜäöü")) j--
        val word = formula.substring(j + 1, end)
        if (word.isEmpty() || !word.first().isLetter()) return null
        // A cell reference like "B2" is no function name.
        if (SheetEngine.parseRef(word) != null) return null
        if (formula.getOrNull(j) == '!' || formula.getOrNull(j) == '\'') return null
        return word
    }

    /** Functions starting with the letters at the cursor (German names first, at most [limit]). */
    fun suggestions(formula: String, cursor: Int = formula.length, limit: Int = 8): List<Pair<String, String>> {
        val p = prefix(formula, cursor)?.uppercase() ?: return emptyList()
        val names = (SheetEngine.FUNCTIONS.map { it.first } + SIGNATURES.keys).distinct()
        return names.filter { it.startsWith(p) && it != p }.sortedWith(compareBy({ it.length }, { it }))
            .take(limit).map { it to (hints[it] ?: SIGNATURES[it]?.let { s -> "$it($s)" }.orEmpty()) }
    }

    /** [formula] with the name at the cursor completed to [name] and "(" after it; returns text and new cursor. */
    fun complete(formula: String, cursor: Int, name: String): Pair<String, Int> {
        val p = prefix(formula, cursor) ?: return formula to cursor
        val start = cursor - p.length
        val hasParen = formula.getOrNull(cursor) == '('
        val text = formula.substring(0, start) + name + (if (hasParen) "" else "(") + formula.substring(cursor)
        return text to start + name.length + 1
    }
}
