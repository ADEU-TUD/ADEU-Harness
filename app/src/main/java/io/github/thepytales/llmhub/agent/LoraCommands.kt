package io.github.thepytales.llmhub.agent

import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.data.AppMap
import io.github.thepytales.llmhub.data.AppTarget
import io.github.thepytales.llmhub.data.AppTool
import io.github.thepytales.llmhub.data.MapKind
import io.github.thepytales.llmhub.local.Fit
import io.github.thepytales.llmhub.local.LocalCatalog
import io.github.thepytales.llmhub.ui.tr
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

    // ── Carrying out a recognized command ───────────────────────────────────────────────────────────────────

    /** The place for the label `ort:<id>` on this platform, or null. */
    fun target(label: String, targets: List<AppTarget>): AppTarget? {
        val id = label.removePrefix(PLACE)
        return if (id == SETTINGS_OVERVIEW.id) SETTINGS_OVERVIEW else targets.firstOrNull { it.id == id }
    }

    /** Lora leads to [t]: a tool that is switched off is offered first, a mode starts a chat, everything else opens. */
    fun open(t: AppTarget, enabledTools: Set<String>): LoraReply {
        val off = t.tool?.let { AppTool.byId(it) }?.takeIf { it.id !in enabledTools && !it.planned }
            ?: (if (t.kind == MapKind.TOOL) AppTool.byId(t.id.removePrefix("tool.")) else null)?.takeIf { it.id !in enabledTools && !it.planned }
        return when {
            off != null -> LoraReply(tr("Dafür gibt es „{0}“ – {1} Einschalten?", tr(off.label), tr(off.description)), listOf(LoraStep.EnableTool(off)))
            t.kind == MapKind.MODE || t.kind == MapKind.TEMPLATE -> LoraReply(tr("Ich öffne „{0}“.", tr(t.title)), listOf(LoraStep.OpenMode(t)))
            else -> LoraReply(tr("Hier ist es: {0}.", tr(t.path)), listOf(LoraStep.Navigate(t)))
        }
    }

    /** `stell:<id>`: the question whether to change the setting – or its place when the wish names no value or it is set already. */
    fun change(label: String, wish: String, current: io.github.thepytales.llmhub.data.AppSettings, targets: List<AppTarget>): LoraReply? {
        val setting = LoraAgent.SETTINGS.firstOrNull { it.id == label.removePrefix(SET) } ?: return null
        val now = setting.get(current)
        // The bare name of a setting („Pseudonymisierung“) asks to see it, not to change it.
        val bare = AppMap.norm(wish).let { n -> n == AppMap.norm(setting.label) || n == AppMap.norm(tr(setting.label)) }
        val value = if (bare) null else CommandSlots.settingValue(setting, wish, now)
        val place = LoraAgent.SETTING_SPOTS[setting.id]?.let { spot -> targets.firstOrNull { it.spot == spot } }
        return when {
            value == null -> place?.let { open(it, current.enabledToolIds) }
            value == now -> LoraReply(tr("„{0}“ steht schon auf „{1}“.", tr(setting.label), tr(setting.labelOf(value))), hits = listOfNotNull(place))
            else -> LoraReply(tr("Soll ich „{0}“ auf „{1}“ stellen?", tr(setting.label), tr(setting.labelOf(value))), listOf(LoraStep.ChangeSetting(setting, value)))
        }
    }

    /** `vorlage`: the TU Vorlage the wish clearly means, else the list of the templates with the likeliest ones. */
    fun template(wish: String, targets: List<AppTarget>): LoraReply? {
        val templates = targets.filter { it.kind == MapKind.TEMPLATE }
        val query = wish.replace(Regex("(?i)\\b(tu[- ]?)?(vorlagen?|templates?)\\b"), " ")
        val hits = AppMap.search(query, templates, 4)
        val best = hits.firstOrNull()
        if (best != null && best.score >= 6 && best.score >= (hits.getOrNull(1)?.score ?: 0) + 3) return open(best.target, emptySet())
        val list = targets.firstOrNull { it.id == "place.create.templates" } ?: targets.firstOrNull { it.id == "area.templates" } ?: return null
        return if (hits.isEmpty()) LoraReply(tr("Hier sind die TU Vorlagen: {0}.", tr(list.path)), listOf(LoraStep.Navigate(list)))
        else LoraReply(tr("Diese Vorlagen könnten passen:"), hits = hits.map { it.target } + list)
    }

    /** Where an action lives when its tool is not open („füge die PDFs zusammen“ typed in the chat). */
    private fun home(action: ToolAction, targets: List<AppTarget>): AppTarget? =
        targets.firstOrNull { it.id == "place.${action.id}" } ?: when (action.area) {
            ActionArea.PDF -> targets.firstOrNull { it.id == "tool.pdf" }
            ActionArea.VIDEO -> targets.firstOrNull { it.id == "tool.video" }
            ActionArea.MAIL -> targets.firstOrNull { it.id == "tool.mail" }
            else -> targets.firstOrNull { it.id == "area.files" }
        }

    /** An installed language model for this device: one that runs well here ([good]), or the strongest installed one. */
    fun localModel(c: AppContainer, good: Boolean): String? {
        val installed = c.localLlm.installedModels()
        if (!good) return installed.maxByOrNull { it.needGb }?.modelId
        val hw = c.localLlm.hardware.value ?: return null
        return installed.filter { LocalCatalog.fit(it, hw) == Fit.OK }.maxByOrNull { it.needGb }?.modelId
    }

    /**
     * What Lora does for the command [label] the model is sure of (AP-71) – on the device, without a language model.
     * Null hands the wish on: a value is missing, the place does not exist on this platform, or nothing fits.
     * Settings, tools and risky actions still wait for the user's yes, as with the language model.
     */
    suspend fun reply(c: AppContainer, agent: LoraAgent, wish: String, label: String, targets: List<AppTarget>, area: ActionArea?, android: Boolean): LoraReply? {
        val settings = c.settings.current
        return when {
            isFree(label) -> null
            label.startsWith(PLACE) -> target(label, targets)?.let { open(it, settings.enabledToolIds) }
            label.startsWith(SET) -> change(label, wish, settings, targets)
            label == TEMPLATE -> template(wish, targets)
            label.startsWith(ACT) -> {
                val action = ToolActions.byId(label.removePrefix(ACT))?.takeIf { !android || it.android } ?: return null
                if (action !in ToolActions.forArea(area, android)) {
                    // The tool is not open: Lora leads there instead of acting on files she cannot see.
                    val t = home(action, targets) ?: return null
                    return open(t, settings.enabledToolIds).let { r -> if (r.steps.singleOrNull() is LoraStep.Navigate) r.copy(text = tr("Das geht hier: {0}.", tr(t.path))) else r }
                }
                val args = CommandSlots.args(action.id, wish) ?: return null
                agent.planned(action, args)
            }
            label == LORA + "tour" -> agent.localTour(wish, android, sure = true)
            label == LORA + "chats" -> {
                val query = CommandSlots.chatQuery(wish)
                val chats = agent.searchChats(query)
                when {
                    chats.isNotEmpty() -> LoraReply(if (query.isBlank()) tr("Deine letzten Chats:") else if (chats.size == 1) tr("Das könnte er sein:") else tr("Diese Chats passen:"), chats)
                    query.isBlank() -> LoraReply(tr("Es gibt noch keine Chats."))
                    else -> LoraReply(tr("Dazu habe ich keinen Chat gefunden – versuch ein anderes Stichwort."))
                }
            }
            label == LORA + "frist" -> CommandSlots.date(wish)?.let { day ->
                val title = CommandSlots.reminderTitle(wish)
                LoraReply(tr("Soll ich „{0}“ in den Kalender eintragen?", title), listOf(LoraStep.Reminder(title, day)))
            }
            label == LORA + "hilfe" -> LoraAgent.explain(wish, shown = true).takeIf { !it.startsWith("Dazu steht nichts") }
                ?.let { LoraReply(it, hits = AppMap.search(wish, targets, 3).map { h -> h.target }) }
            label == LORA + "merken" -> {
                val text = CommandSlots.preference(wish)
                when {
                    text.length < 4 -> null
                    c.drive.isVaultChat(io.github.thepytales.llmhub.ui.LoraHelper.chatId) -> LoraReply(tr("Im Tresor merke ich mir nichts."))
                    Memory.known(c, text) -> LoraReply(tr("Das weiß ich schon."))
                    else -> LoraReply(tr("Soll ich mir das merken?"), listOf(LoraStep.Remember(text, LoraPrefs.guessTool(text))))
                }
            }
            label == LORA + "postfach" ->
                // AP-68: the mailbox is read by the model on this device only; Lora hands the task over and reads nothing.
                if (LoraMail.available(c)) LoraReply(tr("Das übernimmt das Modell auf diesem Gerät im Postfach – ich lese keine Mails."), listOf(LoraMail.task(c, wish)))
                else targets.firstOrNull { it.id == "tool.mail" }?.let { open(it, settings.enabledToolIds) }
            else -> null
        }
    }

    /**
     * A short name of the command [label] for a button („Meinst du … oder …?“, AP-73), or null when it does not exist on
     * this platform or is a free request.
     */
    fun title(label: String, targets: List<AppTarget>, android: Boolean): String? = when {
        isFree(label) -> null
        label.startsWith(PLACE) -> target(label, targets)?.let { tr(it.title) }
        label.startsWith(SET) -> LoraAgent.SETTINGS.firstOrNull { it.id == label.removePrefix(SET) }?.let { tr(it.label) }
        label.startsWith(ACT) -> ToolActions.byId(label.removePrefix(ACT))?.takeIf { !android || it.android }?.let { tr(it.title).removeSuffix(" …").removeSuffix("…") }
        label == TEMPLATE -> tr("TU Vorlage öffnen")
        label == LORA + "tour" -> tr("Tour starten")
        label == LORA + "chats" -> tr("Frühere Chats suchen")
        label == LORA + "frist" -> tr("Frist in den Kalender")
        label == LORA + "hilfe" -> tr("Hilfe zur App")
        label == LORA + "merken" -> tr("Vorliebe merken")
        label == LORA + "postfach" -> tr("Auftrag fürs Postfach")
        else -> null
    }

    /** One line for Strg+K: what the command [label] would do, or null for free requests and unknown labels. */
    fun describe(label: String, wish: String, current: io.github.thepytales.llmhub.data.AppSettings, targets: List<AppTarget>): String? = when {
        label.startsWith(PLACE) -> target(label, targets)?.let { tr("Öffnen: {0}", tr(it.title)) }
        label.startsWith(SET) -> LoraAgent.SETTINGS.firstOrNull { it.id == label.removePrefix(SET) }?.let { st ->
            CommandSlots.settingValue(st, wish, st.get(current))?.let { v -> tr("Einstellung: {0} → {1}", tr(st.label), tr(st.labelOf(v))) } ?: tr("Einstellung: {0}", tr(st.label))
        }
        label.startsWith(ACT) -> ToolActions.byId(label.removePrefix(ACT))?.let { tr("Lora macht das: {0}", tr(it.title)) }
        label == TEMPLATE -> tr("TU Vorlage suchen")
        label == LORA + "tour" -> tr("Tour starten")
        label == LORA + "chats" -> tr("Frühere Chats suchen")
        label == LORA + "frist" -> tr("Frist in den Kalender")
        label == LORA + "hilfe" -> tr("Hilfe zur App")
        label == LORA + "merken" -> tr("Vorliebe merken")
        label == LORA + "postfach" -> tr("Auftrag fürs Postfach (auf diesem Gerät)")
        else -> null
    }
}
