package io.github.thepytales.llmhub.agent

import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.data.AppMap
import io.github.thepytales.llmhub.data.AppSettings
import io.github.thepytales.llmhub.data.AppTarget
import io.github.thepytales.llmhub.data.AppTool
import io.github.thepytales.llmhub.data.ChatMessage
import io.github.thepytales.llmhub.data.MapKind
import io.github.thepytales.llmhub.data.ModelCatalog
import io.github.thepytales.llmhub.data.Privacy
import io.github.thepytales.llmhub.data.PrivacyLevel
import io.github.thepytales.llmhub.data.TemplateLibrary
import io.github.thepytales.llmhub.data.withTool
import io.github.thepytales.llmhub.net.ThinkingLevel
import io.github.thepytales.llmhub.net.s
import io.github.thepytales.llmhub.ui.splitThinking
import kotlinx.serialization.json.JsonObject

/** One thing Lora does or offers after a wish (AP-07); the screen carries it out. */
sealed interface LoraStep {
    /** Opens [target] and highlights its option. */
    data class Navigate(val target: AppTarget) : LoraStep

    /** Starts a chat in a mode or opens a TU Vorlage, prefilled with [text]. */
    data class OpenMode(val target: AppTarget, val text: String = "") : LoraStep

    /** Changes a setting – only after the user confirms. */
    data class ChangeSetting(val setting: LoraSetting, val value: String) : LoraStep

    /** Suggests a tool that is switched off; switched on after the user confirms. */
    data class EnableTool(val tool: AppTool) : LoraStep

    /** Hands the wish to a chat model; [reason] says why this model (shown on the card in the chat). */
    data class Handoff(val model: String, val request: String, val summary: String = "", val reason: String = "") : LoraStep

    /** Opens an earlier chat (found by Lora's chat search, AP-35). */
    data class OpenChat(val id: String, val title: String, val updatedAt: Long) : LoraStep

    /** Puts a deadline or reminder into the calendar (.ics with a reminder) – after the user confirms (AP-35). */
    data class Reminder(val title: String, val date: java.time.LocalDate, val daysBefore: Int = 3) : LoraStep

    /** Questions with answer buttons when the wish is unclear (AP-06). */
    data class Ask(val questions: List<UserQuestion>) : LoraStep
}

/** Lora's answer: a short [text] and what to do; [local] = found on the device without a request. */
data class LoraReply(val text: String, val steps: List<LoraStep> = emptyList(), val local: Boolean = true, val hits: List<AppTarget> = emptyList())

/** A setting Lora may change (after asking): id, name, the allowed values with labels, how to read and set it. */
class LoraSetting(
    val id: String,
    val label: String,
    val values: List<Pair<String, String>>,
    val get: (AppSettings) -> String,
    val set: (AppSettings, String) -> AppSettings,
) {
    fun labelOf(value: String) = values.firstOrNull { it.first == value }?.second ?: value

    /** The value for what the model or user said („dunkel“, "DARK", „an“). */
    fun resolve(said: String): String? {
        val n = AppMap.norm(said)
        return values.firstOrNull { it.first.equals(said, ignoreCase = true) }?.first
            ?: values.firstOrNull { AppMap.norm(it.second) == n }?.first
            ?: values.firstOrNull { AppMap.norm(it.second).startsWith(n) && n.length >= 3 }?.first
            ?: when (n) {
                "an", "ein", "true", "ja", "on", "einschalten" -> values.firstOrNull { it.first == "true" }?.first
                "aus", "false", "nein", "off", "ausschalten" -> values.firstOrNull { it.first == "false" }?.first
                else -> null
            }
    }
}

/**
 * Lora als Navigator (AP-07): a slim agent loop with GLM-5.3 Flash (thinking off, short prompt) that finds settings,
 * opens modes and templates, suggests tools and hands real questions to a chat model. Simple wishes are answered on the
 * device first ([local]) without any request. Lora only runs when asked and never reads along in the background.
 */
class LoraAgent(private val c: AppContainer) {

    /**
     * Answers [wish]. [history] are the earlier turns of this conversation with Lora (wish, answer); [folderId] the
     * project of the chat Lora was called from (its privacy level decides whether a model may be asked).
     */
    suspend fun ask(
        wish: String, history: List<Pair<String, String>> = emptyList(), folderId: String? = null, android: Boolean = false,
        onText: (String) -> Unit = {},
    ): LoraReply {
        val settings = c.settings.current
        val targets = AppMap.build(TemplateLibrary.all(c.templates.items.value), android)
        local(wish, targets, settings.enabledToolIds, android, settings.loraHandoffModel)?.let { return it }
        localChats(wish)?.let { return it }
        val model = modelFor(folderId)
            ?: return fallback(wish, targets, if (!settings.hasAnyApiKey) "Ohne API-Schlüssel suche ich nur in der App." else "In diesem Projekt bleibt alles auf dem Gerät – ich suche nur in der App.")
        return runCatching { remote(wish, history, model, targets, android, onText) }
            .getOrElse { e ->
                if (e is kotlinx.coroutines.CancellationException) throw e
                fallback(wish, targets, friendlyError(e))
            }
    }

    /** „Wo hatte ich …“, „mein letzter Chat“: earlier chats, found on the device. Null for other wishes. */
    private fun localChats(wish: String): LoraReply? {
        val n = AppMap.norm(wish)
        val recent = RECENT.containsMatchIn(n)
        val find = FIND_CHAT.containsMatchIn(n)
        if (!recent && !find) return null
        val query = if (recent) "" else FIND_CHAT.replace(n, " ").trim()
        val chats = searchChats(query)
        if (chats.isEmpty()) return LoraReply(if (recent) "Es gibt noch keine Chats." else "Dazu habe ich keinen Chat gefunden – versuch ein anderes Stichwort.")
        return LoraReply(if (recent) "Deine letzten Chats:" else if (chats.size == 1) "Das könnte er sein:" else "Diese Chats passen:", chats)
    }

    /** Chats whose title or text fits [query] (all words), newest first; an empty query gives the latest chats. */
    internal fun searchChats(query: String, limit: Int = 5): List<LoraStep.OpenChat> {
        val words = AppMap.words(query)
        val all = c.chats.conversations.value.filter { !it.archived && !it.temporary }
        val hits = if (words.isEmpty()) all.sortedByDescending { it.updatedAt } else all.mapNotNull { conv ->
            val title = AppMap.norm(conv.title)
            val text = AppMap.norm(conv.messages.joinToString(" ") { it.content.take(4000) })
            val score = words.sumOf { w -> (if (title.contains(w)) 3 else 0) + (if (text.contains(w)) 1 else 0) }
            val every = words.all { w -> title.contains(w) || text.contains(w) }
            if (score > 0 && every) conv to score else null
        }.sortedWith(compareByDescending<Pair<io.github.thepytales.llmhub.data.Conversation, Int>> { it.second }.thenByDescending { it.first.updatedAt }).map { it.first }
        return hits.take(limit).map { LoraStep.OpenChat(it.id, it.title, it.updatedAt) }
    }

    /**
     * The model a handed-over question goes to (AP-33): the one set under Einstellungen › Lora (DeepSeek by default);
     * with „Automatisch“ Lora's own choice or the automatic one for the question.
     */
    internal fun handoffModel(request: String, chosen: String? = null, why: String? = null): Pair<String, String> =
        handoffModel(c.settings.current.loraHandoffModel, request, chosen, why)

    /** A short, kind sentence instead of a raw exception. */
    private fun friendlyError(e: Throwable): String {
        val reason = when (e) {
            is java.net.UnknownHostException, is java.net.ConnectException, is java.net.NoRouteToHostException -> "ohne Internetverbindung"
            is java.net.SocketTimeoutException -> "der Server antwortet gerade zu langsam"
            is io.github.thepytales.llmhub.net.ApiException -> when (e.code) {
                401, 403 -> "der API-Schlüssel wird nicht angenommen"
                429 -> "gerade sind zu viele Anfragen unterwegs"
                in 500..599 -> "der Uni-Server hat eine Störung"
                else -> "der Server meldet einen Fehler (${e.code})"
            }
            else -> null
        }
        return "Ich erreiche gerade kein Modell" + (reason?.let { " – $it" } ?: "") + ". In der App habe ich das gefunden:"
    }

    /** The model Lora may use here, or null (no key, or the project keeps everything on the device without a local model). */
    private fun modelFor(folderId: String?): String? {
        val settings = c.settings.current
        val level = Privacy.level(c, folderId)
        if (level == PrivacyLevel.LOCAL) return c.localLlm.preferredModel()
        if (!settings.hasAnyApiKey) return null
        val wanted = settings.loraModel.ifBlank { ModelCatalog.FAST_CHAT }
        return Privacy.suitableModel(c, folderId, wanted)
    }

    private fun fallback(wish: String, targets: List<AppTarget>, note: String): LoraReply {
        val hits = AppMap.search(wish, targets, 5).map { it.target }
        val text = if (hits.isEmpty()) "$note Dazu habe ich nichts gefunden – versuch es mit anderen Worten." else note
        return LoraReply(text, hits = hits)
    }

    private suspend fun remote(
        wish: String, history: List<Pair<String, String>>, model: String, targets: List<AppTarget>, android: Boolean, onText: (String) -> Unit,
    ): LoraReply {
        val messages = mutableListOf(ChatMessage(role = "system", content = systemPrompt(wish, targets, android)))
        history.takeLast(4).forEach { (q, a) ->
            messages += ChatMessage(role = "user", content = q)
            messages += ChatMessage(role = "assistant", content = a)
        }
        messages += ChatMessage(role = "user", content = wish)
        val steps = mutableListOf<LoraStep>()
        var text = ""
        repeat(MAX_ROUNDS) { round ->
            val result = c.llm.streamChat(model, messages, TOOLS.toSchema(), thinking = ThinkingLevel.OFF) { upd ->
                splitThinking(upd.content).second.trim().takeIf { it.isNotEmpty() }?.let(onText)
            }
            text = splitThinking(result.content).second.trim()
            if (result.toolCalls.isEmpty()) return LoraReply(text.ifBlank { "Wie kann ich helfen?" }, steps, local = false)
            messages += ChatMessage(role = "assistant", content = result.content, toolCalls = result.toolCalls)
            var needsAnswer = false
            for (call in result.toolCalls) {
                val args = parseArgs(call.arguments)
                val (output, step) = runTool(call.name, args, targets)
                step?.let(steps::add)
                if (call.name == "search_chats") steps += searchChats(args.s("suche").orEmpty())
                if (call.name == "explain" || call.name == "search_chats") needsAnswer = true
                messages += ChatMessage(role = "tool", content = output, toolCallId = call.id, toolName = call.name)
            }
            // Actions are carried out by the screen; only an explanation needs the model once more for the answer.
            if (!needsAnswer && steps.isNotEmpty()) return LoraReply(text.ifBlank { defaultText(steps) }, steps, local = false)
            if (round == MAX_ROUNDS - 1) return LoraReply(text.ifBlank { defaultText(steps) }, steps, local = false)
        }
        return LoraReply(text.ifBlank { defaultText(steps) }, steps, local = false)
    }

    /** Runs one of Lora's tools: the text for the model and the step for the screen. */
    internal fun runTool(name: String, args: JsonObject, targets: List<AppTarget>): Pair<String, LoraStep?> = when (name) {
        "navigate" -> {
            val t = args.s("ziel")?.let { AppMap.byId(it, targets) ?: AppMap.clearHit(it, targets) }
            if (t == null) "FEHLER: Ziel unbekannt. Nimm eine id aus der Liste." to null
            else if (t.kind == MapKind.MODE || t.kind == MapKind.TEMPLATE) "Geöffnet: ${t.title}." to LoraStep.OpenMode(t)
            else "Geöffnet: ${t.path}${if (t.spot != null) " (Option hervorgehoben)" else ""}." to LoraStep.Navigate(t)
        }
        "open_mode" -> {
            val id = args.s("modus").orEmpty()
            val t = AppMap.byId(id, targets) ?: AppMap.byId("mode.$id", targets) ?: AppMap.byId("template.$id", targets)
                ?: AppMap.search(id, targets.filter { it.kind == MapKind.MODE || it.kind == MapKind.TEMPLATE }, 1).firstOrNull()?.target
            if (t == null) "FEHLER: Modus oder Vorlage unbekannt." to null
            else "Geöffnet: ${t.title}." to LoraStep.OpenMode(t, args.s("text").orEmpty())
        }
        "change_setting" -> {
            val setting = SETTINGS.firstOrNull { it.id == args.s("id") }
            val value = args.s("wert")?.let { setting?.resolve(it) }
            when {
                setting == null -> "FEHLER: Diese Einstellung kann ich nicht ändern. Änderbar: ${SETTINGS.joinToString { it.id }}. Sonst navigate." to null
                value == null -> "FEHLER: Erlaubte Werte: ${setting.values.joinToString { "${it.first} (${it.second})" }}." to null
                else -> "Der Nutzer wird gefragt, ob „${setting.label}“ auf „${setting.labelOf(value)}“ gestellt werden soll." to LoraStep.ChangeSetting(setting, value)
            }
        }
        "enable_tool" -> {
            val tool = args.s("werkzeug")?.let { w -> AppTool.byId(w) ?: AppTool.entries.firstOrNull { AppMap.norm(it.label).contains(AppMap.norm(w)) } }
            if (tool == null || tool.planned) "FEHLER: Dieses Werkzeug gibt es (noch) nicht." to null
            else "Der Nutzer wird gefragt, ob „${tool.label}“ eingeschaltet werden soll." to LoraStep.EnableTool(tool)
        }
        "handoff_chat" -> {
            val request = args.s("anfrage").orEmpty().ifBlank { args.s("zusammenfassung").orEmpty() }
            val chosen = args.s("modell")?.takeIf { it.isNotBlank() && ModelCatalog.info(it).displayName.isNotBlank() }
            val (model, reason) = handoffModel(request, chosen, args.s("grund"))
            "Übergeben an einen Chat." to LoraStep.Handoff(model, request, args.s("zusammenfassung").orEmpty(), reason)
        }
        "search_chats" -> {
            val found = searchChats(args.s("suche").orEmpty())
            (if (found.isEmpty()) "Keine passenden Chats." else "Gefunden (werden als Karten gezeigt): " + found.joinToString("; ") { it.title }) to null
        }
        "add_reminder" -> {
            val date = args.s("datum")?.let { io.github.thepytales.llmhub.docs.CalendarFiles.parse(it)?.first?.toLocalDate() }
            val title = args.s("titel").orEmpty().ifBlank { "Erinnerung" }
            if (date == null) "FEHLER: Datum nicht verstanden. Format: 2026-11-30." to null
            else "Der Nutzer wird gefragt, ob „$title“ am $date in den Kalender soll." to LoraStep.Reminder(title.take(80), date, args.s("tage_vorher")?.toIntOrNull()?.coerceIn(0, 30) ?: 3)
        }
        "explain" -> explain(args.s("thema").orEmpty()) to null
        AskUserTool.NAME -> {
            val questions = AskUserTool.parse(args)
            if (questions.isEmpty()) "FEHLER: Keine Frage erkannt." to null else "Die Fragen werden angezeigt." to LoraStep.Ask(questions)
        }
        else -> "FEHLER: Unbekanntes Werkzeug." to null
    }

    private fun systemPrompt(wish: String, targets: List<AppTarget>, android: Boolean): String = buildString {
        append("Du bist Lora, die freundliche Helferin und Navigatorin der App Lora (").append(if (android) "Android" else "Windows").append(") ")
        append("für Forschung, Lehre und Verwaltung an der TU Dresden. ")
        append("Du hilfst, Bereiche, Einstellungen, Modi, TU Vorlagen und Werkzeuge zu finden, und führst dorthin. ")
        append("Antworte auf Deutsch, freundlich und sehr kurz (1–2 Sätze). Erfinde keine Funktionen.\n")
        append("Werkzeuge: navigate(ziel) öffnet einen Ort und hebt die Option hervor; open_mode(modus, text) startet einen Modus oder ")
        append("eine TU Vorlage; change_setting(id, wert) nur, wenn der Nutzer ausdrücklich etwas umstellen will (er bestätigt); ")
        append("enable_tool(werkzeug) schlägt ein ausgeschaltetes Werkzeug vor; handoff_chat(anfrage, zusammenfassung) gibt Fach- und ")
        append("Inhaltsfragen (Erklärungen, Texte, Recherche) an einen Chat weiter – Standardmodell ist ")
        append(ModelCatalog.info(c.settings.current.loraHandoffModel.ifBlank { ModelCatalog.DEFAULT_CHAT }).displayName)
        append("; nur in Sonderfällen setzt du modell und grund selbst: Programmieren ").append(ModelCatalog.CODE_CHAT)
        append(", sehr schwierige, mehrstufige Analysen ").append(ModelCatalog.HARD_CHAT)
        append(", riesige Textmengen ").append(ModelCatalog.FAST_CHAT).append("; explain(thema) liest die Hilfe der App für ")
        append("„Wie geht …?“; search_chats(suche) findet frühere Chats („Wo hatte ich …?“); add_reminder(titel, datum) legt eine Frist ")
        append("oder Erinnerung im Kalender an (der Nutzer bestätigt); ask_user nur bei echt mehrdeutigen Wünschen. Heute ist ")
        append(java.time.LocalDate.now()).append(".\n")
        append("Änderbare Einstellungen: ").append(SETTINGS.joinToString { "${it.id} (${it.values.joinToString("/") { v -> v.first }})" }).append('\n')
        append("Werkzeuge der App: ").append(AppTool.entries.filter { !it.planned && (!android || it.android) }.joinToString { "${it.id} (${it.label})" }).append('\n')
        append("Orte (id | Art | Pfad | Beschreibung):\n").append(AppMap.candidatesText(wish, targets))
    }

    companion object {
        private const val MAX_ROUNDS = 3

        /** Settings Lora may change (always after asking). */
        val SETTINGS = listOf(
            LoraSetting(
                "theme", "Helligkeit",
                listOf("SYSTEM" to "Wie das System", "LIGHT" to "Hell", "DARK" to "Dunkel"),
                { it.themeMode }, { s, v -> s.copy(themeMode = v) },
            ),
            LoraSetting(
                "text_size", "Schriftgröße",
                listOf("0.9" to "Klein", "1.0" to "Normal", "1.15" to "Groß", "1.3" to "Sehr groß"),
                { it.textScale.toString() }, { s, v -> s.copy(textScale = v.toFloat()) },
            ),
            LoraSetting("language", "Sprache der Oberfläche", listOf("de" to "Deutsch", "en" to "Englisch"), { it.uiLanguage }, { s, v -> s.copy(uiLanguage = v) }),
            LoraSetting(
                "lora", "Lora",
                listOf("EVERYWHERE" to "Überall", "CHAT" to "Nur im Chat", "OFF" to "Aus"),
                { it.loraMode }, { s, v -> s.copy(loraMode = v, hideLora = v == "OFF") },
            ),
            onOff("pseudonymize", "Pseudonymisierung", { it.pseudonymize }) { s, v -> s.copy(pseudonymize = v) },
            onOff("web", "Websuche", { it.webTools }) { s, v -> s.copy(webTools = v) },
            onOff("documents", "Dokumente erstellen", { it.documentTools }) { s, v -> s.copy(documentTools = v) },
            onOff("auto_model", "Modell automatisch wählen", { it.autoModel }) { s, v -> s.copy(autoModel = v) },
            onOff("collapse_steps", "Arbeitsschritte einklappen", { it.collapseSteps }) { s, v -> s.copy(collapseSteps = v) },
            onOff("start_animation", "Startanimation", { it.startAnimation }) { s, v -> s.copy(startAnimation = v) },
            onOff("lora_calm", "Ruhigere Bewegungen für Lora", { it.loraCalm }) { s, v -> s.copy(loraCalm = v) },
        )

        private fun onOff(id: String, label: String, get: (AppSettings) -> Boolean, set: (AppSettings, Boolean) -> AppSettings) =
            LoraSetting(id, label, listOf("true" to "An", "false" to "Aus"), { get(it).toString() }, { s, v -> set(s, v == "true") })

        /** The option each setting is shown at (for the highlight after changing it). */
        val SETTING_SPOTS = mapOf(
            "theme" to "appearance.theme", "text_size" to "appearance.textsize", "language" to "appearance.language", "lora" to "lora.mode",
            "pseudonymize" to "privacy.pseudonymize", "web" to "chat.web", "auto_model" to "chat.autoModel", "collapse_steps" to "chat.collapse",
            "start_animation" to "appearance.startAnimation", "lora_calm" to "lora.calm",
        )

        /** Lora's tools in the function-calling format. */
        val TOOLS: List<ToolDef> = listOf(
            ToolDef("navigate", "Öffnet einen Ort der App (Bereich, Einstellungsseite, Option) und hebt die Option mit einem Leuchtrahmen hervor.",
                listOf(Param("ziel", "id aus der Liste der Orte, z. B. option.appearance.theme"))),
            ToolDef("open_mode", "Startet einen neuen Chat in einem Modus oder öffnet eine TU Vorlage, optional mit Text im Eingabefeld.",
                listOf(Param("modus", "id des Modus oder der Vorlage, z. B. mode.literature oder template.b_mail"), Param("text", "Text für das Eingabefeld", required = false))),
            ToolDef("change_setting", "Stellt eine Einstellung um – der Nutzer bestätigt vorher. Nur auf ausdrücklichen Wunsch.",
                listOf(Param("id", "id der Einstellung, z. B. theme"), Param("wert", "neuer Wert, z. B. DARK"))),
            ToolDef("enable_tool", "Schlägt vor, ein großes Werkzeug einzuschalten (z. B. pdf, vault); der Nutzer bestätigt.",
                listOf(Param("werkzeug", "id des Werkzeugs"))),
            ToolDef("handoff_chat", "Gibt eine Fach- oder Inhaltsfrage an einen neuen Chat weiter (Erklärungen, Texte, Recherche).",
                listOf(
                    Param("anfrage", "Die Anfrage für den Chat, ausformuliert"),
                    Param("zusammenfassung", "Was im Gespräch mit Lora schon klar wurde", required = false),
                    Param("modell", "Nur in Sonderfällen setzen, sonst leer lassen (dann gilt der Standard, DeepSeek)", required = false),
                    Param("grund", "Warum ein anderes Modell, kurz (nur mit modell)", required = false),
                )),
            ToolDef("explain", "Liest die eingebaute Hilfe der App zu einem Thema („Wie geht …?“).", listOf(Param("thema", "Thema, z. B. Exzerpt, Tresor, PDF"))),
            ToolDef("search_chats", "Sucht in den früheren Chats des Nutzers (Titel und Text); die Treffer erscheinen als Karten zum Öffnen.",
                listOf(Param("suche", "Stichworte, leer für die letzten Chats"))),
            ToolDef("add_reminder", "Legt eine Frist oder Erinnerung als ganztägigen Kalendereintrag mit Erinnerung an (öffnet in Outlook bzw. dem Kalender); der Nutzer bestätigt.",
                listOf(Param("titel", "Wofür, z. B. Skizzenfrist BMFTR"), Param("datum", "Tag, z. B. 2026-11-30"), Param("tage_vorher", "Erinnerung so viele Tage vorher (Standard 3)", required = false))),
        ) + AskUserTool().defs

        /** Built-in help (short sections of the README) for explain and the local answer to „Wie geht …?“. */
        val HELP: List<Pair<String, String>> = listOf(
            "Chat und Modelle" to "Neuer Chat oben in der Seitenleiste. Das Modell wechselst du über den Namen über dem Eingabefeld; „Automatisch“ schlägt je Anfrage das passende vor. Dateien und Bilder hängst du mit + an oder ziehst sie hinein.",
            "Modi" to "Modi (Literatursuche, Planungsmodus, Quellenmodus, Exzerpt, Sprachmodus) startest du mit „/“ im Eingabefeld, über Strg+K oder die Startseite. Der Chip über dem Eingabefeld zeigt den Modus, ✕ verlässt ihn.",
            "TU Vorlagen" to "Unter „Erstellen“ in der Seitenleiste: TU Vorlagen (Briefe, Mails, Lehre, Forschung) und unter Windows Gestalten (Poster, Flyer, Social Media) mit Meine Designs. Vorlagen mit vielen Feldern lassen sich auch Schritt für Schritt mit Lora ausfüllen. Mit dem Stern landen sie unter Favoriten auf der Startseite.",
            "Literatur" to "Literatur in der Seitenleiste: Einträge per DOI, ISBN, BibTeX/RIS oder Zotero, Zitierstil unter Einstellungen › Literatur & Zotero. Zu einem Eintrag lässt sich ein Exzerpt erstellen.",
            "Exzerpt" to "Exzerpt-Modus (/Exzerpt) oder Literatur › Eintrag › Exzerpt: PDF anhängen, der Agent füllt Bibliografie, Fragestellung, Theorie, Methode, Befunde und Zitate mit Seitenangaben aus, prüft die Zitate gegen das PDF und hängt das Exzerpt an den Literatureintrag.",
            "Quellenmodus" to "Quellenmodus (/Quellen): Jede Aussage bekommt einen Beleg (Datei und Seite, DOI, URL). Danach prüft die App die Zitate gegen die Quellen und zeigt ein Belegprotokoll, das sich exportieren lässt.",
            "Tresor" to "Der Tresor (Einstellungen › Werkzeuge einschalten) ist ein Bereich mit PIN für sensible Forschung. Inhalte sind verschlüsselt, nur der Uni-Server oder lokale Modelle sind erlaubt, Namen werden streng ersetzt. Der Wiederherstellungsschlüssel setzt eine vergessene PIN zurück.",
            "PDF-Werkzeugkasten" to "Der PDF-Werkzeugkasten (Einstellungen › Werkzeuge einschalten): PDFs in Tabs ansehen, zusammenfügen, teilen, Seiten drehen, löschen und sortieren, Seitenzahlen, markieren, Notizen und Stempel, Formulare ausfüllbar machen.",
            "Datenschutz" to "Einstellungen › Datenschutz: Pseudonymisierung ersetzt Namen vor dem Senden durch Platzhalter und setzt sie in der Antwort zurück. Öffentliche Quellen bleiben, wie sie sind; eine Nie-ersetzen-Liste und ein gründlicher lokaler Modus helfen.",
            "Projekte" to "Projekte bündeln Chats, Anweisungen, Dateien und eine Datenschutzstufe (Standard, Nur TU Dresden, Nur auf diesem Gerät).",
            "Transkription" to "Transkription in der Seitenleiste: Audio wählen, auf dem Uni-Server oder dem Gerät verschriften, Sprecher benennen, codieren und exportieren.",
            "Dokumente" to "Der Agent erstellt PDF, Word, PowerPoint und mehr. Unter Dokumente bearbeitest du Entwürfe mit Versionen und Kommentaren.",
            "Darstellung" to "Einstellungen › Darstellung: Stil, Helligkeit (hell/dunkel), Farbton, Schriftgröße und Zoom.",
            "Lora" to "Lora hilft beim Finden: In den Einstellungen unten rechts antippen, auf der Startseite „Lora fragen“ oder Strg+K › Lora fragen. Einstellungen › Lora legt fest, wo sie erscheint.",
            "Updates" to "Neue Versionen meldet die App beim Start. Einstellungen › Updates sucht auf Knopfdruck.",
        )

        /** The help sections that fit [topic]. */
        fun explain(topic: String): String {
            val words = AppMap.words(topic)
            val hits = HELP.map { (title, text) ->
                val hay = AppMap.norm("$title $text")
                title to text to words.count { w -> w.length >= 3 && hay.contains(w.take(maxOf(4, w.length - 2))) }
            }.filter { it.second > 0 }.sortedByDescending { it.second }.take(2)
            if (hits.isEmpty()) return "Dazu steht nichts in der Hilfe. Themen: ${HELP.joinToString { it.first }}."
            return hits.joinToString("\n\n") { (p, _) -> "${p.first}: ${p.second}" }
        }

        private fun defaultText(steps: List<LoraStep>): String = when (val s = steps.firstOrNull()) {
            is LoraStep.Navigate -> "Hier ist es: ${s.target.path}."
            is LoraStep.OpenMode -> "Ich öffne „${s.target.title}“."
            is LoraStep.ChangeSetting -> "Soll ich „${s.setting.label}“ auf „${s.setting.labelOf(s.value)}“ stellen?"
            is LoraStep.EnableTool -> "Dafür gibt es „${s.tool.label}“ – einschalten?"
            is LoraStep.Handoff -> "Das klärt am besten ein Chat – ich gebe es weiter."
            is LoraStep.Ask -> "Kurz nachgefragt:"
            is LoraStep.OpenChat -> "Das habe ich gefunden:"
            is LoraStep.Reminder -> "Soll ich „${s.title}“ in den Kalender eintragen?"
            null -> "Wie kann ich helfen?"
        }

        private val EXPLAIN_START = Regex("^(erklar|erklaer|was (ist|sind|bedeutet|heisst|meint)|wer (ist|war)|warum|wieso|weshalb|schreib|formulier|ubersetz|uebersetz|fass|gib mir|nenne|hilf mir beim)")
        private val RECENT = Regex("(letzte[nrm]? chats?|zuletzt|mein(en)? letzte[nrm]? (chat|gesprach)|letzte gesprach)")
        private val FIND_CHAT = Regex("^(wo hatte ich|wo war (das|der chat)|finde? (den |meinen )?chat|such(e)? (den |meinen )?chat|welcher chat)( (das|den|die|der|uber|mit|zu|zum|zur))*")
        private val HOWTO = Regex("^(wie (geht|funktioniert|nutze|benutze|verwende|mache|kann ich|lege|richte))")
        private val APP_WORDS = Regex("(app|einstellung|lora|seitenleiste|modus|vorlage|tresor|werkzeug|pdf werkzeug|knopf|menu|menue)")

        /**
         * Lokal zuerst: answers the wish on the device when it is clear – a tool that is switched off, a mode, one clear
         * place, an explanation for a chat or „Wie geht …?“ from the help. Null when the model should help.
         */
        /**
         * The model for a handed-over question: the standard of Einstellungen › Lora (DeepSeek by default). Only in
         * special cases another one – when Lora chose one herself ([chosen], with her [why]), or when the question
         * clearly needs it (programming, a very demanding analysis; see ModelRouter).
         */
        fun handoffModel(setting: String, request: String, chosen: String?, why: String? = null): Pair<String, String> {
            val standard = setting.ifBlank { ModelCatalog.DEFAULT_CHAT }
            if (chosen != null && chosen != standard) return chosen to (why?.takeIf { it.isNotBlank() } ?: "von Lora gewählt")
            val route = ModelRouter.suggest(request, 0, emptyList(), false, standard)
            return if (route.model != standard) route.model to route.reason else standard to "Standard für Übergaben"
        }

        fun local(
            wish: String, targets: List<AppTarget>, enabledTools: Set<String>, android: Boolean,
            handoff: String = ModelCatalog.DEFAULT_CHAT,
        ): LoraReply? {
            val n = AppMap.norm(wish)
            if (n.isBlank()) return LoraReply("Was möchtest du machen oder einstellen?")
            // A big tool: suggest it when it is off, otherwise open it.
            val tools = AppTool.available(android)
            AppTool.suggestFor(wish, enabledTools, android, tools)?.let { tool ->
                return LoraReply("Dafür gibt es „${tool.label}“ – ${tool.description} Einschalten?", listOf(LoraStep.EnableTool(tool)))
            }
            tools.firstOrNull { t -> t.id in enabledTools && t.keywords.any { k -> Regex("(^|[^a-z])" + Regex.escape(k)).containsMatchIn(n) } }?.let { tool ->
                targets.firstOrNull { it.id == "tool.${tool.id}" }?.let { return LoraReply("Ich öffne „${tool.label}“.", listOf(LoraStep.Navigate(it))) }
            }
            // „Erklär mir …“ about something that is not the app: a chat answers that.
            if (EXPLAIN_START.containsMatchIn(n) && !APP_WORDS.containsMatchIn(n)) {
                val (model, reason) = handoffModel(handoff, wish, null)
                return LoraReply("Das klärt am besten ein Chat – ich gebe es weiter.", listOf(LoraStep.Handoff(model, wish.trim(), reason = reason)))
            }
            val hit = AppMap.clearHit(wish, targets)
            if (HOWTO.containsMatchIn(n) && (hit == null || hit.kind == MapKind.TEMPLATE)) {
                val help = explain(wish)
                if (!help.startsWith("Dazu steht nichts")) return LoraReply(help, hits = AppMap.search(wish, targets, 3).map { it.target })
            }
            hit ?: return null
            return when (hit.kind) {
                MapKind.MODE, MapKind.TEMPLATE -> LoraReply("Ich öffne „${hit.title}“.", listOf(LoraStep.OpenMode(hit)))
                else -> LoraReply("Hier ist es: ${hit.path}.", listOf(LoraStep.Navigate(hit)))
            }
        }
    }
}

/** Carries out a confirmed [LoraStep.ChangeSetting] / [LoraStep.EnableTool]. */
fun AppContainer.applyLoraStep(step: LoraStep) {
    when (step) {
        is LoraStep.ChangeSetting -> settings.update { step.setting.set(it, step.value) }
        is LoraStep.EnableTool -> settings.update { it.withTool(step.tool, true) }
        else -> Unit
    }
}

/**
 * Writes deadlines as all-day calendar entries with a reminder [daysBefore] days ahead (AP-35) and returns the .ics,
 * which opens in Outlook or the calendar app. Several deadlines go into one file.
 */
fun AppContainer.loraCalendar(deadlines: List<Pair<String, java.time.LocalDate>>, daysBefore: Int = 3, note: String = ""): java.io.File {
    val events = deadlines.map { (title, day) ->
        io.github.thepytales.llmhub.docs.CalendarEvent(
            title = if (title.startsWith("Frist", ignoreCase = true)) title else "Frist: $title",
            start = day.atStartOfDay(), end = day.atStartOfDay(), allDay = true,
            description = note.ifBlank { "Von Lora eingetragen." },
            reminderMinutes = daysBefore.coerceAtLeast(0) * 24 * 60,
        )
    }
    val name = if (deadlines.size == 1) "Frist – " + deadlines[0].first.take(50) else "Fristen (${deadlines.size})"
    val file = documentTools.newFile(name, "ics")
    io.github.thepytales.llmhub.docs.CalendarFiles.writeAll(file, events)
    return file
}
