package io.github.thepytales.llmhub.docs

/** Ready-made sheets for school and projects, offered under „Neu ›“ Tabelle (Markdown with formats). */
object SheetTemplates {
    private val RED = CondRule(range = "", op = "between", a = "5", b = "6", fill = "#FFC7CE", color = "#9C0006")

    /**
     * Grade calculator: points of the written test → grade by the grading key (SVERWEIS), weighted with the oral
     * grade, average per column.
     */
    val GRADES: String by lazy {
        val n = 10
        val last = n + 1
        val head = listOf("Name", "Punkte Klassenarbeit", "Note schriftlich", "Note mündlich", "Gesamt", "Zeugnisnote", "", "Einstellung", "Wert")
        val settings = listOf(
            "Höchstpunktzahl" to "50", "Gewicht schriftlich" to "60 %", "Gewicht mündlich" to "40 %", "Notenschlüssel: ab Anteil" to "Note",
            "0" to "6", "20 %" to "5", "45 %" to "4", "60 %" to "3", "75 %" to "2", "90 %" to "1",
        )
        val sample = listOf(Triple("Anna", "46", "2"), Triple("Ben", "31", "3"), Triple("Cleo", "18", "4"))
        val rows = mutableListOf(head)
        for (r in 2..last) {
            val s = sample.getOrNull(r - 2)
            rows += listOf(
                s?.first ?: "", s?.second ?: "",
                "=WENN(B$r=\"\";\"\";SVERWEIS(B$r/\$I\$2;\$H\$6:\$I\$11;2;WAHR))",
                s?.third ?: "",
                "=WENN(ANZAHL(C$r:D$r)<2;\"\";C$r*\$I\$3+D$r*\$I\$4)",
                "=WENN(E$r=\"\";\"\";RUNDEN(E$r;0))",
                "", settings.getOrNull(r - 2)?.first ?: "", settings.getOrNull(r - 2)?.second ?: "",
            )
        }
        rows += listOf("Durchschnitt") + listOf("B", "C", "D", "E", "F").map { c -> "=WENNFEHLER(MITTELWERT(${c}2:$c$last);\"\")" } + listOf("", "", "")
        val meta = SheetMeta(
            cells = buildMap {
                for (r in 2..last + 1) put("E$r", CellStyle(nf = "0.00"))
                listOf("B", "C", "D", "F").forEach { put("${it}${last + 1}", CellStyle(b = true, nf = "0.00")) }
                put("A${last + 1}", CellStyle(b = true))
                put("E${last + 1}", CellStyle(b = true, nf = "0.00"))
                put("H5", CellStyle(b = true))
                put("I5", CellStyle(b = true))
                for (r in 2..last) put("F$r", CellStyle(b = true, align = "c"))
            },
            rules = listOf(RED.copy(range = "C2:C$last"), RED.copy(range = "F2:F$last")),
        )
        SheetMeta.compose(rows, meta)
    }

    /** Attendance list: one column per school day (working days follow with ARBEITSTAG), absences counted per pupil. */
    val ATTENDANCE: String by lazy {
        val days = 10
        val n = 12
        val last = n + 1
        val dayCols = (1..days).map { SheetEngine.colName(it) }
        val absent = SheetEngine.colName(days + 1)
        val excused = SheetEngine.colName(days + 2)
        val head = listOf("Name", "02.11.2026") + (2..days).map { "=ARBEITSTAG(${SheetEngine.colName(it - 1)}1;1)" } + listOf("Fehltage", "davon entschuldigt")
        val range = { r: Int -> "${dayCols.first()}$r:${dayCols.last()}$r" }
        val rows = mutableListOf(head)
        for (r in 2..last) {
            rows += listOf("") + List(days) { "" } +
                listOf("=ZÄHLENWENN(${range(r)};\"f\")+ZÄHLENWENN(${range(r)};\"e\")", "=ZÄHLENWENN(${range(r)};\"e\")")
        }
        rows += listOf("Fehlend am Tag") + dayCols.map { c -> "=ZÄHLENWENN(${c}2:$c$last;\"f\")+ZÄHLENWENN(${c}2:$c$last;\"e\")" } +
            listOf("=SUMME(${absent}2:$absent$last)", "=SUMME(${excused}2:$excused$last)")
        rows += listOf("Legende: f = fehlt, e = entschuldigt, v = verspätet") + List(days + 2) { "" }
        val area = "B2:${dayCols.last()}$last"
        val meta = SheetMeta(
            cells = buildMap {
                (listOf("B") + dayCols.drop(1)).forEach { put("${it}1", CellStyle(nf = "date", align = "c")) }
                for (r in 2..last) dayCols.forEach { put("$it$r", CellStyle(align = "c")) }
                put("A${last + 1}", CellStyle(b = true))
                put("A${last + 2}", CellStyle(i = true, color = "#555555"))
            },
            rules = listOf(
                CondRule(area, "eq", "f", fill = "#FFC7CE", color = "#9C0006"),
                CondRule(area, "eq", "e", fill = "#FFEB9C", color = "#9C5700"),
                CondRule(area, "eq", "v", fill = "#DDEBF7", color = "#1F4E79"),
                CondRule("${absent}2:$absent$last", "gt", "5", fill = "#FFC7CE", color = "#9C0006"),
            ),
        )
        SheetMeta.compose(rows, meta)
    }

    /** Budget: income and expenses with running balance, sums and the balance in euros. */
    val BUDGET: String by lazy {
        val n = 10
        val last = n + 1
        val sample = listOf(
            listOf("01.11.2026", "Zuschuss Förderverein", "Einnahme", "500", ""),
            listOf("03.11.2026", "Material Werkraum", "Ausgabe", "", "86,40"),
            listOf("10.11.2026", "Eintritt Museum", "Ausgabe", "", "120"),
        )
        val rows = mutableListOf(listOf("Datum", "Posten", "Kategorie", "Einnahmen", "Ausgaben", "Saldo"))
        for (r in 2..last) {
            val s = sample.getOrNull(r - 2) ?: List(5) { "" }
            rows += s + (if (r == 2) "=D2-E2" else "=F${r - 1}+D$r-E$r")
        }
        rows += listOf("Summe", "", "", "=SUMME(D2:D$last)", "=SUMME(E2:E$last)", "=D${last + 1}-E${last + 1}")
        val meta = SheetMeta(
            cells = buildMap {
                for (r in 2..last + 1) listOf("D", "E", "F").forEach { put("$it$r", CellStyle(nf = "eur", b = r == last + 1)) }
                put("A${last + 1}", CellStyle(b = true))
                for (r in 2..last) put("A$r", CellStyle(nf = "date"))
            },
            rules = listOf(CondRule("F2:F${last + 1}", "lt", "0", fill = "#FFC7CE", color = "#9C0006")),
            charts = emptyList(),
        )
        SheetMeta.compose(rows, meta)
    }

    /** Name → Markdown of all templates. */
    val ALL: List<Pair<String, String>> get() = listOf("Notenrechner" to GRADES, "Anwesenheitsliste" to ATTENDANCE, "Budget" to BUDGET)
}
