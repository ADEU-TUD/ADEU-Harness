package io.github.thepytales.llmhub.agent

import io.github.thepytales.llmhub.data.AppMap
import io.github.thepytales.llmhub.data.AppTarget
import io.github.thepytales.llmhub.data.MapKind
import io.github.thepytales.llmhub.ui.trLater

/**
 * Befehle auf dem Gerät (AP-71): the labels of the command model and what each of them means in the app. Nothing is
 * kept twice – the labels come from the registers that exist anyway:
 *  - `ort:<id>`   a place of the [AppMap] (area, settings page, option, button, mode, tool; also every Strg+K entry),
 *  - `stell:<id>` a setting Lora may change ([LoraAgent.SETTINGS]); the value is read from the wish ([CommandSlots]),
 *  - `tu:<id>`    an action of the open tool ([ToolActions]) whose parameters the device can read from the wish,
 *  - `lora:<id>`  what Lora does herself (tour, earlier chats, deadline, help, remember, Postfach),
 *  - `vorlage`    a TU Vorlage (which one is found by the search in the templates – they change, the model stays),
 *  - `frei.einfach` / `frei.komplex`  no command: goes to a language model as before.
 * tools/befehle (Python, not part of the app) trains the model on these labels; CommandLabelsTest keeps both together.
 */
object LoraCommands {
    const val FREE_SIMPLE = "frei.einfach"
    const val FREE_COMPLEX = "frei.komplex"
    const val TEMPLATE = "vorlage"
    const val PLACE = "ort:"
    const val SET = "stell:"
    const val ACT = "tu:"
    const val LORA = "lora:"

    fun isFree(label: String) = label.startsWith("frei.")

    /** One label: [kind] ort/stell/tu/lora/vorlage/frei, [title] and [hint] say what it is (for people and the teacher). */
    data class Entry(val label: String, val kind: String, val title: String, val hint: String, val area: String? = null, val android: Boolean = true)

    /** The overview of the settings (Strg+K › Einstellungen); the map itself only has the single pages. */
    val SETTINGS_OVERVIEW = AppTarget(
        "area.settings", MapKind.AREA, trLater("Einstellungen"), trLater("Seitenleiste › Einstellungen"),
        trLater("Alle Einstellungen der App."), "einstellungen optionen", "settings",
    )

    /**
     * Actions of the tools the device plans without a language model: their parameters are optional or can be read
     * from the wish by rules (pages, column, folder, format …). Actions that need invented content (cells, slides from
     * an outline) stay with the language model.
     */
    val LOCAL_ACTIONS = listOf(
        "files.duplicates", "files.tidy", "files.trash", "files.move", "files.rename", "files.rename_by_content", "files.tag",
        "files.transcribe", "files.handout", "files.to_nextcloud",
        "pdf.open", "pdf.merge", "pdf.split", "pdf.delete_pages", "pdf.rotate", "pdf.compress", "pdf.ocr", "pdf.convert", "pdf.redact",
        "doc.write", "doc.diagram", "doc.insert_table", "slides.from_document", "slides.from_paper",
        "sheet.sort", "sheet.clean", "sheet.summary", "sheet.distribution", "sheet.group", "sheet.explain", "sheet.chart",
        "slides.split_full", "slides.delete", "slides.animate", "slides.beautify", "slides.notes",
        "video.export", "video.subtitles", "video.split", "video.rough_cut", "video.faces", "video.speed", "video.shorten",
    )

    /** What Lora does herself: id → what it is. */
    val LORA_ACTIONS = listOf(
        "tour" to "Tour durch ein Werkzeug („Zeig mir, wie das geht“)",
        "chats" to "Frühere Chats finden („Wo hatte ich …“, „meine letzten Chats“)",
        "frist" to "Frist oder Erinnerung in den Kalender eintragen",
        "hilfe" to "„Wie geht …?“ zur App: Antwort aus der eingebauten Hilfe",
        "merken" to "Eine Vorliebe merken („Merk dir, dass …“) – Lora fragt nach",
        "postfach" to "Auftrag fürs Postfach (Mails, Termine): läuft nur mit dem Modell auf diesem Gerät",
    )

    /** Every label the code offers on this platform. */
    fun catalog(android: Boolean = false): List<Entry> {
        val out = ArrayList<Entry>()
        out += Entry(FREE_SIMPLE, "frei", "Freie Anfrage, kurz und einfach", "Umformulieren, kurze Texte, einfache Erklärungen – reicht ein kleines Sprachmodell")
        out += Entry(FREE_COMPLEX, "frei", "Freie Anfrage, komplex", "Analysen, lange Dokumente, Recherche, Websuche, Bilder, Programmieren, Werkzeugketten")
        out += Entry(TEMPLATE, "vorlage", "TU Vorlage öffnen", "Brief, Mail, Protokoll, Bescheinigung … – welche, findet die Suche in den Vorlagen")
        for (t in AppMap.build(emptyList(), android) + SETTINGS_OVERVIEW) {
            if (t.kind == MapKind.TEMPLATE) continue
            out += Entry(PLACE + t.id, "ort", t.title, t.path + " · " + t.description + " · " + t.synonyms, android = t.android)
        }
        for (s in LoraAgent.SETTINGS) out += Entry(SET + s.id, "stell", s.label, s.values.joinToString(" / ") { "${it.first} = ${it.second}" })
        for (id in LOCAL_ACTIONS) {
            val a = ToolActions.byId(id) ?: continue
            if (android && !a.android) continue
            out += Entry(ACT + a.id, "tu", a.title, a.description + a.params.joinToString("") { " · ${it.name}: ${it.description}" }, a.area.key, a.android)
        }
        for ((id, what) in LORA_ACTIONS) out += Entry(LORA + id, "lora", id, what)
        return out
    }
}
