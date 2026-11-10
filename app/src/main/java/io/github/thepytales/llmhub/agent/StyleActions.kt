package io.github.thepytales.llmhub.agent

import io.github.thepytales.llmhub.data.HouseStyle
import io.github.thepytales.llmhub.docs.StyleCheck
import io.github.thepytales.llmhub.ui.tr
import io.github.thepytales.llmhub.ui.trLater

/**
 * Hausstil-Prüfung und Barrierefreiheits-Check (AP-79) als Lora-Aktionen im offenen Dokument und in der offenen
 * Präsentation: „Prüfen“ zeigt die Funde mit Fundstelle, „Beheben“ ändert, was automatisch geht – mit Vorschau
 * (vorher → nachher) und Rückgängig.
 */
object StyleActions {
    private class Host(val markdown: () -> String, val replace: (String) -> Unit, val slides: Boolean)

    private fun host(area: ActionArea): Host? = when (area) {
        ActionArea.DOC -> ActionHosts.doc?.let { d -> d.markdown()?.let { Host({ d.markdown().orEmpty() }, d::restore, false) } }
        ActionArea.SLIDES -> ActionHosts.slides?.let { s -> Host(s::markdown, s::replace, true) }
        else -> null
    }

    private fun open(area: ActionArea): Host =
        host(area) ?: throw ActionFailed(if (area == ActionArea.SLIDES) tr("Bitte zuerst eine Präsentation öffnen.") else tr("Bitte zuerst ein Dokument öffnen."))

    /** The findings as lines for the card: „Absatz 4: „TU-Dresden“ → „TU Dresden““. */
    fun lines(issues: List<StyleCheck.Issue>, max: Int = 8): String = issues.take(max).joinToString("\n") { i ->
        "• " + i.where + ": " + i.text + (if (i.fix != null) " ✓" else i.advice.takeIf { it.isNotBlank() }?.let { " – $it" }.orEmpty())
    } + if (issues.size > max) "\n" + tr("… und {0} weitere", issues.size - max) else ""

    private fun check(id: String, area: ActionArea) = ToolAction(
        id, area, trLater("Hausstil und Barrierefreiheit prüfen"),
        "Prüft das offene ${if (area == ActionArea.SLIDES) "Präsentation" else "Dokument"} auf Hausstil (eigene Regeln, Schreibweise der TU Dresden) und Barrierefreiheit (Überschriften, Alternativtexte, Linktexte, Tabellenköpfe" +
            (if (area == ActionArea.SLIDES) ", Folientitel" else "") + ") und zeigt die Funde mit Fundstelle.",
        emptyList(), risky = false,
    ) { c, _ ->
        val h = open(area)
        ActionPlan(id, tr("Prüft Hausstil und Barrierefreiheit …"), false) {
            val issues = StyleCheck.check(h.markdown(), HouseStyle.all(c), h.slides)
            ActionResult(StyleCheck.summary(issues) + if (issues.isEmpty()) "" else "\n" + lines(issues) +
                if (issues.any { it.fix != null }) "\n" + tr("„Hausstil beheben“ ändert die mit ✓ markierten Stellen.") else "")
        }
    }

    private fun fix(id: String, area: ActionArea) = ToolAction(
        id, area, trLater("Hausstil und Barrierefreiheit beheben"),
        "Behebt im offenen ${if (area == ActionArea.SLIDES) "Präsentation" else "Dokument"} automatisch, was die Prüfung findet und sich sicher ändern lässt (Schreibweisen, Abkürzungen, Überschriftenebenen, fette Zeilen als Überschrift, nackte Adressen als Link).",
        emptyList(), risky = true,
    ) { c, _ ->
        val h = open(area)
        val before = h.markdown()
        val issues = StyleCheck.check(before, HouseStyle.all(c), h.slides).filter { it.fix != null }
        if (issues.isEmpty()) throw ActionFailed(tr("Nichts, das sich automatisch beheben ließe – die übrigen Funde zeigt „Prüfen“."))
        ActionPlan(
            id, tr("Behebt {0} Stelle(n) …", issues.size), true,
            issues.map { i -> (i.where + ": " + before.substring(i.fix!!.range.first, i.fix.range.last + 1).ifBlank { "␣" }) to i.fix.replacement.ifEmpty { tr("(entfernt)") } },
        ) {
            val after = StyleCheck.apply(before, issues)
            h.replace(after)
            ActionResult(tr("{0} Stelle(n) behoben.", issues.size), undo = { h.replace(before); tr("Wieder wie vorher.") })
        }
    }

    val ALL: List<ToolAction> = listOf(
        check("doc.check", ActionArea.DOC), fix("doc.fix_style", ActionArea.DOC),
        check("slides.check", ActionArea.SLIDES), fix("slides.fix_style", ActionArea.SLIDES),
    )

}
