package io.github.thepytales.llmhub.agent

import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.data.AppMap
import io.github.thepytales.llmhub.data.AppSettings
import io.github.thepytales.llmhub.data.AppTarget
import io.github.thepytales.llmhub.data.AppTool
import io.github.thepytales.llmhub.data.LoraCorrections
import io.github.thepytales.llmhub.data.LoraWays
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
import io.github.thepytales.llmhub.ui.trLater
import io.github.thepytales.llmhub.ui.tr

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

    /**
     * Lora handelt im Werkzeug (AP-38): a planned action. Safe ones run at once (step line, then the result with
     * Rückgängig); risky ones show their preview and wait for „Ausführen“.
     */
    data class Action(val plan: ActionPlan, val dry: Boolean = false) : LoraStep

    /** „Alles rückgängig seit heute Morgen“ (AP-77): today's actions, one by one or all at once. */
    data object UndoDay : LoraStep

    /**
     * „Soll ich mir merken, dass …?“ (AP-39): saved only after „Merken“. [pref]/[value] make it the default of an
     * action („files.tidy:nach“ = „semester“).
     */
    data class Remember(val text: String, val tool: String = "", val pref: String? = null, val value: String? = null) : LoraStep

    /** A planned Ablauf over several tools (AP-40); the card shows it with Los · Ändern · Abbrechen. */
    data class Plan(val flowId: String, val dropped: List<String> = emptyList()) : LoraStep

    /** A tour through a tool (AP-41). */
    data class Tour(val tourId: String) : LoraStep

    /** Lora sortiert Mails (AP-69): a run on the model of this device; its card shows progress, preview and Rückgängig. */
    data class MailSort(val runId: String) : LoraStep

    /**
     * Rückfrage mit zwei Knöpfen (AP-73): the command model is not sure, but the wish is one of two commands. The card
     * shows [options] and „Etwas anderes“ (then the rules and a language model, as before); [area] is what was open.
     */
    data class Choose(val wish: String, val options: List<Choice>, val area: String = "app", val correcting: String? = null) : LoraStep {
        data class Choice(val label: String, val title: String)
    }
}

/**
 * Lora's answer: a short [text] and what to do; [local] = found on the device without a request. [understood]: the
 * command Lora carried out on the device for this wish (AP-71/72) – „Rückgängig“ right after it counts as a correction.
 */
data class LoraReply(
    val text: String, val steps: List<LoraStep> = emptyList(), val local: Boolean = true, val hits: List<AppTarget> = emptyList(),
    val understood: Understood? = null,
    /** After an error (AP-74): the step that helps, as a button under the text. */
    val fix: io.github.thepytales.llmhub.ui.ErrorHelp.Fix? = null,
)

/** The command [label] Lora took [wish] for while [area] was open (AP-72). */
data class Understood(val wish: String, val area: String, val label: String)

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
        /** „Etwas anderes“ under the two buttons (AP-73): no second question, the wish goes on as before. */
        noChoice: Boolean = false,
        onText: (String) -> Unit = {},
    ): LoraReply {
        // Alles rückgängig (AP-77): today's actions as a list, nothing is planned.
        if (DryRun.asksUndoDay(wish)) return LoraReply(tr("Das habe ich heute erledigt:"), listOf(LoraStep.UndoDay))
        // Probelauf (AP-77): the action is planned as usual, but only the preview is shown – nothing runs by itself.
        // Without an action behind it („Was passiert, wenn ich auf Senden klicke?“) the question is answered as asked.
        DryRun.strip(wish)?.let { rest ->
            val r = ask(rest, history, folderId, android, noChoice, onText)
            if (r.steps.any { it is LoraStep.Action }) return r.copy(
                text = tr("Nur ein Probelauf – so sähe es aus:"),
                steps = r.steps.map { if (it is LoraStep.Action) it.copy(dry = true) else it },
            )
        }
        // AP-71: count where the answer came from and how long it took (numbers only, on this device).
        val t0 = System.nanoTime()
        val (reply, way) = corrected(wish, history, folderId, android, onText) ?: answer(wish, history, folderId, android, noChoice, onText).also {
            lastWish = wish to areaKey()
        }
        runCatching { LoraWays.record(c, way, (System.nanoTime() - t0) / 1_000_000) }
        return onDevice(reply)
    }

    /**
     * Wo Lora arbeitet (AP-112): where the chat works on this device, a question Lora hands over goes to the model here
     * (not to DeepSeek on the server); without one Lora says what is missing instead of handing over.
     */
    private fun onDevice(reply: LoraReply): LoraReply {
        val handoffs = reply.steps.filterIsInstance<LoraStep.Handoff>().filter { !Privacy.isLocal(it.model) }
        if (handoffs.isEmpty() || io.github.thepytales.llmhub.data.Places.effective(c, io.github.thepytales.llmhub.data.Area.CHAT) != io.github.thepytales.llmhub.data.Place.LOCAL) return reply
        val here = c.localLlm.preferredModel()
            ?: return reply.copy(
                text = io.github.thepytales.llmhub.data.Places.missingNote(c, io.github.thepytales.llmhub.data.Area.CHAT),
                steps = reply.steps - handoffs.toSet(),
                fix = io.github.thepytales.llmhub.ui.ErrorHelp.Fix.LOCAL_SETUP,
            )
        return reply.copy(steps = reply.steps.map { if (it is LoraStep.Handoff && it in handoffs) it.copy(model = here) else it })
    }

    /** The last wish Lora answered and what was open then – what „nein, ich meinte …“ corrects (AP-72). */
    @Volatile
    private var lastWish: Pair<String, String>? = null

    private fun areaKey(): String = ActionArea.of(io.github.thepytales.llmhub.ui.LoraHelper.route.value)?.key ?: "app"

    private fun vault(): Boolean = c.drive.isVaultChat(io.github.thepytales.llmhub.ui.LoraHelper.chatId)

    /**
     * „Nein, ich meinte …“ (AP-72): the rest is answered as a wish of its own; if that is a command, Lora remembers it for
     * the wish before. Null for every other wish.
     */
    private suspend fun corrected(
        wish: String, history: List<Pair<String, String>>, folderId: String?, android: Boolean, onText: (String) -> Unit,
    ): Pair<LoraReply, LoraWays.Way>? {
        val before = lastWish?.takeIf { history.isNotEmpty() } ?: return null
        val rest = correction(wish) ?: return null
        val (reply, way) = answer(rest, history, folderId, android, false, onText, correcting = before.first)
        lastWish = before
        val u = reply.understood ?: understoodFrom(reply, rest, before.second) ?: return reply to way
        return if (LoraCorrections.learn(c, before.first, before.second, u.label, vault())) {
            reply.copy(text = tr("Verstanden – das merke ich mir für „{0}“.", before.first.trim()) + "\n\n" + reply.text) to way
        } else reply to way
    }

    /**
     * The user tapped [label] under „Meinst du … oder …?“ (AP-73): Lora carries it out on the device like a command the
     * model was sure of. If that needs more than the wish gives (a value is missing), the wish goes on as before.
     */
    suspend fun chosen(
        step: LoraStep.Choose, label: String, history: List<Pair<String, String>> = emptyList(), folderId: String? = null, android: Boolean = false,
        onText: (String) -> Unit = {},
    ): LoraReply {
        val targets = AppMap.build(TemplateLibrary.all(c.templates.items.value), android)
        val area = ActionArea.of(io.github.thepytales.llmhub.ui.LoraHelper.route.value)
        // AP-72: the choice is a correction – next time the wish is carried out without asking.
        val vault = vault()
        LoraCorrections.learn(c, step.wish, step.area, label, vault)
        step.correcting?.let { LoraCorrections.learn(c, it, step.area, label, vault) }
        LoraCommands.reply(c, this, step.wish, label, targets, area, android)?.let { reply ->
            runCatching { LoraWays.record(c, LoraWays.Way.DEVICE, 0) }
            return reply.copy(understood = Understood(step.wish, step.area, label))
        }
        return ask(step.wish, history, folderId, android, noChoice = true, onText = onText)
    }

    /**
     * The command a reply found by the word rules stands for (AP-72): one place, mode or setting it leads to – if the
     * command model knows it as a label (only those can be carried out the same way next time).
     */
    private fun understoodFrom(reply: LoraReply, wish: String, area: String): Understood? {
        val label = when (val st = reply.steps.singleOrNull()) {
            is LoraStep.Navigate -> LoraCommands.PLACE + st.target.id
            is LoraStep.OpenMode -> LoraCommands.PLACE + st.target.id
            is LoraStep.ChangeSetting -> LoraCommands.SET + st.setting.id
            is LoraStep.EnableTool -> LoraCommands.PLACE + "tool." + st.tool.id
            else -> return null
        }
        return Understood(wish, area, label).takeIf { label in io.github.thepytales.llmhub.data.CommandModelInfo.labels }
    }

    /**
     * „Meinst du … oder …?“ (AP-73) for the two [labels] the model hesitates between, or null when one of them does not
     * exist here (then the wish goes on as before).
     */
    private fun choose(wish: String, labels: List<String>, area: String, targets: List<AppTarget>, android: Boolean, correcting: String?): LoraReply? {
        val options = labels.map { l -> LoraCommands.title(l, targets, android)?.let { LoraStep.Choose.Choice(l, it) } ?: return null }
        if (options.size != 2 || options[0].title == options[1].title) return null
        return LoraReply(tr("Meinst du „{0}“ oder „{1}“?", options[0].title, options[1].title), listOf(LoraStep.Choose(wish, options, area, correcting)))
    }

    /** The model handed-over questions go to while Lora works „Nur lokal“ (AP-71); null otherwise. */
    @Volatile
    private var localOnly: String? = null

    private suspend fun answer(
        typed: String, history: List<Pair<String, String>>, folderId: String?, android: Boolean, noChoice: Boolean, onText: (String) -> Unit,
        correcting: String? = null,
    ): Pair<LoraReply, LoraWays.Way> {
        // Eigene Wörter (AP-78): „öffne das MHB“ is understood as „öffne das Modulhandbuch“.
        val wish = runCatching { io.github.thepytales.llmhub.data.Vocabulary.canonical(typed, io.github.thepytales.llmhub.data.OwnWords.all(c)) }.getOrDefault(typed)
        // „Was ist das ZLSB?“: an own word or a pack entry answers on the device, with its source.
        termAnswer(typed)?.let { return it to LoraWays.Way.DEVICE }
        val device = LoraWays.Way.DEVICE
        val settings = c.settings.current
        val targets = AppMap.build(TemplateLibrary.all(c.templates.items.value), android)
        val area = ActionArea.of(io.github.thepytales.llmhub.ui.LoraHelper.route.value)
        val areaKey = area?.key ?: "app"
        // AP-72: a wish the user corrected before is carried out as meant, before anything else guesses.
        LoraCorrections.meant(c, wish, areaKey)?.let { label ->
            LoraCommands.reply(c, this, wish, label, targets, area, android)?.let { return it.copy(understood = Understood(wish, areaKey, label)) to device }
        }
        // AP-39: „Merk dir, dass …“ – Lora asks before she remembers.
        LoraPrefs.fromWish(c, wish)?.let { return it to device }
        // AP-41: „Zeig mir, wie das geht“ starts the tour of the open tool.
        localTour(wish, android)?.let { return it to device }
        // AP-69: „Ordne die Mails der letzten Woche in Lehre, Forschung und Verwaltung ein“ – the order is read here, the
        // mails only by the model on this device.
        MailSorter.fromWish(c, wish)?.let { return it to device }
        // AP-68: mail tasks go to the model on this device, the handoff from a Postfach chat to the card – Lora reads no mails.
        LoraMail.fromWish(c, wish, io.github.thepytales.llmhub.ui.LoraHelper.chatId)?.let { return it to device }
        // AP-71: the command model on this device – a clear command is carried out at once, without a language model.
        // Unsure, it changes nothing: the rules and the language model decide as before.
        c.commandModel.offerIfNeeded()
        val multi = LoraFlows.looksMultiStep(wish)
        val seen = if (multi) null else c.commandModel.recognize(wish, areaKey)
        // AP-72: an action undone right after Lora carried it out for this very wish is not carried out again unasked.
        val refused = seen != null && seen.sure && !seen.free && LoraCorrections.refused(c, wish, areaKey, seen.label)
        if (seen != null && seen.sure && !seen.free && !refused) LoraCommands.reply(c, this, wish, seen.label, targets, area, android)?.let {
            return it.copy(understood = Understood(wish, areaKey, seen.label)) to device
        }
        // AP-73: not sure, but one of two commands – Lora asks with two buttons instead of guessing or asking a model.
        // Before the word rules on purpose: a question costs a tap, a wrongly carried out action costs more.
        if (seen != null && !noChoice && seen.choices.size == 2) choose(wish, seen.choices, areaKey, targets, android, correcting)?.let { return it to device }
        // Lora handelt (AP-38): clear wishes for the open tool are carried out on the device, without a model.
        if (!multi) localAction(wish, area, android)?.let { return it to device }
        // The rules that guess a place from single words stay out when the model is sure that this is a free request.
        val free = seen != null && seen.sure && seen.free
        if (!free) local(wish, targets, settings.enabledToolIds, android, settings.loraHandoffModel)?.let { return it to device }
        localChats(wish)?.let { return it to device }

        // A language model answers. Mischmodus (AP-71): short, simple tasks on this device when a model is installed
        // that runs well here; everything else on the server as before. The project's privacy level comes first.
        val level = Privacy.level(c, folderId)
        val server = if (level == PrivacyLevel.LOCAL || !settings.hasAnyApiKey) null else Privacy.suitableModel(c, folderId, settings.loraModel.ifBlank { ModelCatalog.FAST_CHAT })
        val route = ModelRouter.place(
            io.github.thepytales.llmhub.data.Places.loraMode(c), simple = free && seen?.label == LoraCommands.FREE_SIMPLE, text = wish, images = 0, files = 0,
            localGood = LoraCommands.localModel(c, good = true, text = wish), localAny = LoraCommands.localModel(c, good = false, text = wish),
            serverOk = server != null, deviceOnly = level == PrivacyLevel.LOCAL,
        )
        return when (route.place) {
            ModelRouter.Place.NONE -> fallback(
                wish, targets,
                when {
                    !settings.hasAnyApiKey && level != PrivacyLevel.LOCAL ->
                        tr("Ohne API-Schlüssel arbeite ich auf diesem Gerät – dafür fehlt noch ein Sprachmodell (Einstellungen › Wo Lora arbeitet › Einrichtung). Befehle verstehe ich trotzdem.")
                    io.github.thepytales.llmhub.data.Places.loraMode(c) == ModelRouter.Mode.LOCAL && level != PrivacyLevel.LOCAL ->
                        tr("Lora arbeitet „Nur lokal“, aber auf diesem Gerät ist noch kein Sprachmodell installiert (Einstellungen › Modelle auf diesem Gerät). Befehle verstehe ich trotzdem.")
                    !settings.hasAnyApiKey -> tr("Ohne API-Schlüssel suche ich nur in der App.")
                    else -> tr("In diesem Projekt bleibt alles auf dem Gerät – ich suche nur in der App.")
                },
            ) to device
            ModelRouter.Place.LOCAL -> {
                val model = route.model!!
                // A short task in the mixed mode needs no detour over Lora's own model: it goes straight to a chat on this device.
                if (ModelRouter.Mode.of(settings.loraPlace) == ModelRouter.Mode.MIXED && level != PrivacyLevel.LOCAL && server != null) {
                    LoraReply(tr("Das beantwortet das Modell auf diesem Gerät – ich gebe es weiter."), listOf(LoraStep.Handoff(model, wish.trim(), reason = tr(route.reason)))) to LoraWays.Way.LOCAL
                } else {
                    localOnly = model.takeIf { level != PrivacyLevel.LOCAL }
                    try {
                        runCatching { remote(wish, history, model, targets, android, onText, area) }.getOrElse { e ->
                            if (e is kotlinx.coroutines.CancellationException) throw e
                            fallback(wish, targets, friendlyError(e)).copy(fix = io.github.thepytales.llmhub.ui.ErrorHelp.explain(e).fix?.takeIf { it.route != null })
                        } to LoraWays.Way.LOCAL
                    } finally {
                        localOnly = null
                    }
                }
            }
            ModelRouter.Place.SERVER -> runCatching { remote(wish, history, server!!, targets, android, onText, area) }
                .getOrElse { e ->
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    fallback(wish, targets, friendlyError(e)).copy(fix = io.github.thepytales.llmhub.ui.ErrorHelp.explain(e).fix?.takeIf { it.route != null })
                } to LoraWays.Way.SERVER
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
        if (chats.isEmpty()) return LoraReply(if (recent) tr("Es gibt noch keine Chats.") else tr("Dazu habe ich keinen Chat gefunden – versuch ein anderes Stichwort."))
        return LoraReply(if (recent) tr("Deine letzten Chats:") else if (chats.size == 1) tr("Das könnte er sein:") else tr("Diese Chats passen:"), chats)
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
        localOnly?.let { it to tr("Lora arbeitet nur lokal") } ?: handoffModel(c.settings.current.loraHandoffModel, request, chosen, why)

    /**
     * „Was ist das ZLSB?“, „Was heißt MHB?“, „Wofür steht LaSuB?“ (AP-78): the explanation of an own word or a pack entry,
     * on the device, with source and date for pack entries. Null when the wish asks something else.
     */
    internal fun termAnswer(wish: String): LoraReply? {
        val m = TERM_QUESTION.find(wish.trim()) ?: return null
        val asked = m.groupValues[1].trim().trimEnd('?', '.', '!').removePrefix("das ").removePrefix("die ").removePrefix("der ").trim()
        if (asked.isEmpty()) return null
        val hit = io.github.thepytales.llmhub.data.Vocabulary.find(asked, io.github.thepytales.llmhub.data.Vocabulary.all(c))
            .firstOrNull { h -> h.term.forms.any { it.equals(asked, ignoreCase = true) } } ?: return null
        val t = hit.term
        if (t.explanation.isBlank()) return null
        val text = buildString {
            append("**").append(t.term).append("** – ").append(t.explanation)
            if (t.synonyms.isNotEmpty()) append("\n\n").append(tr("Auch: {0}", t.synonyms.joinToString()))
            if (hit.pack != null) append("\n\n").append(tr("Aus dem Wissenspaket „{0}“ · Quelle: {1} · Stand {2}", hit.pack, t.source, t.date))
            else append("\n\n").append(tr("Aus deinen eigenen Wörtern."))
        }
        return LoraReply(text)
    }

    /** A short, kind sentence instead of a raw exception. */
    private fun friendlyError(e: Throwable): String {
        val reason = when (e) {
            is java.net.UnknownHostException, is java.net.ConnectException, is java.net.NoRouteToHostException -> tr("ohne Internetverbindung")
            is java.net.SocketTimeoutException -> tr("der Server antwortet gerade zu langsam")
            is io.github.thepytales.llmhub.net.ApiException -> when (e.code) {
                401, 403 -> tr("der API-Schlüssel wird nicht angenommen")
                429 -> tr("gerade sind zu viele Anfragen unterwegs")
                in 500..599 -> tr("der Uni-Server hat eine Störung")
                else -> tr("der Server meldet einen Fehler ({0})", e.code)
            }
            else -> null
        }
        return tr("Ich erreiche gerade kein Modell") + (reason?.let { " – $it" } ?: "") + tr(". In der App habe ich das gefunden:")
    }

    private fun fallback(wish: String, targets: List<AppTarget>, note: String): LoraReply {
        val hits = AppMap.search(wish, targets, 5).map { it.target }
        val text = if (hits.isEmpty()) tr("{0} Dazu habe ich nichts gefunden – versuch es mit anderen Worten.", note) else note
        return LoraReply(text, hits = hits)
    }

    /** „Zeig mir, wie das geht“, „Tour durch den PDF-Werkzeugkasten“: the tour (AP-41), or null. */
    internal fun localTour(wish: String, android: Boolean, sure: Boolean = false): LoraReply? {
        val n = AppMap.norm(wish)
        // [sure]: the command model recognized the wish for a tour (AP-71); the words need not match then.
        if (!sure && !TOUR.containsMatchIn(n)) return null
        val tour = io.github.thepytales.llmhub.ui.LoraTours.find(wish, io.github.thepytales.llmhub.ui.LoraHelper.route.value)?.takeIf { !android || it.android }
            ?: return LoraReply(tr("Touren gibt es für Dateien, Erstellen, den PDF-Werkzeugkasten und den Videoeditor – öffne einen davon oder nenne ihn."))
        return LoraReply(tr("Ich zeige dir „{0}“.", tr(tour.title)), listOf(LoraStep.Tour(tour.id)))
    }

    /**
     * Wishes for the open tool that are clear without a model (AP-38): „PDF bearbeiten“ with a PDF chosen in Dateien,
     * „zusammenfügen“, „Duplikate“, „aufräumen“, „verkleinern“, „Texterkennung“. Null for other wishes.
     */
    internal suspend fun localAction(wish: String, area: ActionArea?, android: Boolean): LoraReply? {
        if (area == null || area == ActionArea.MAIL) return null
        val n = AppMap.norm(wish)
        val pdfs = (LoraFocus.files.filter { it.extension.equals("pdf", true) } + listOfNotNull(LoraFocus.pdf)).distinct()
        val (id, args) = when {
            area == ActionArea.FILES && pdfs.isNotEmpty() && Regex("(bearbeit|werkzeugkasten|offne|oeffne|aufmach|ansehen|zeig)").containsMatchIn(n) && !Regex("(zusammen|merge|duplikat|aufraum)").containsMatchIn(n) -> "pdf.open" to emptyMap()
            Regex("(zusammenfu|zusammen ?fu|fug\\w* .*zusammen|fuhr\\w* .*zusammen|merge|vereinig)").containsMatchIn(n) && (area == ActionArea.PDF || pdfs.size >= 2) -> "pdf.merge" to emptyMap()
            area == ActionArea.FILES && Regex("(duplikat|doppelt)").containsMatchIn(n) -> "files.duplicates" to emptyMap()
            area == ActionArea.FILES && Regex("(aufraum|raum\\w* .*auf\\b|ordne|sortier)").containsMatchIn(n) ->
                // Without a word for the order the remembered one counts (AP-39), else by type.
                "files.tidy" to listOfNotNull(
                    when {
                        "semester" in n -> "semester"
                        "monat" in n -> "monat"
                        Regex("\\b(typ|art|dateiart|dateityp)").containsMatchIn(n) -> "typ"
                        else -> null
                    }?.let { "nach" to it },
                ).toMap()
            area == ActionArea.SHEET && Regex("^(sortier\\w*|ordne)\\s+(nach|die tabelle nach)\\s+").containsMatchIn(n) ->
                "sheet.sort" to mapOf("spalte" to wish.trim().replace(Regex("^(?i)(sortier\\w*|ordne)\\s+(die tabelle\\s+)?nach\\s+"), "").replace(Regex("(?i)\\s+(absteigend|aufsteigend)$"), "").trim(), "absteigend" to if ("absteigend" in n) "ja" else "")
            area == ActionArea.SHEET && Regex("(bereinig|aufraum|dubletten|doppelte zeilen)").containsMatchIn(n) -> "sheet.clean" to emptyMap()
            // Lora rechnet (AP-49): „Berechne die Notenverteilung“, „Mittelwert je Gruppe“, „Erkläre die Formel in D2“.
            area == ActionArea.SHEET && Regex("(verteilung|haufigkeit|wie oft)").containsMatchIn(n) ->
                "sheet.distribution" to listOfNotNull(Regex("(?i)(?:verteilung|häufigkeit|haeufigkeit)\\s+(?:der|von|in)\\s+(?:spalte\\s+)?[„\"]?([\\p{L}\\d .-]+?)[“\"]?\\s*$").find(wish.trim())?.groupValues?.get(1)?.let { "spalte" to it }).toMap()
            area == ActionArea.SHEET && Regex("(mittelwert|durchschnitt|schnitt|anzahl)\\b.*\\b(je|pro|nach)\\s+").containsMatchIn(n) -> {
                val m = Regex("(?i)(?:mittelwert|durchschnitt|schnitt|anzahl)\\s*(?:der|des|von)?\\s*([\\p{L}\\d.-]+)?\\s+(?:je|pro|nach)\\s+[„\"]?([\\p{L}\\d .-]+?)[“\"]?\\s*$").find(wish.trim())
                "sheet.group" to listOfNotNull(m?.groupValues?.get(2)?.let { "gruppe" to it }, m?.groupValues?.get(1)?.takeIf { it.isNotBlank() }?.let { "wert" to it }).toMap()
            }
            area == ActionArea.SHEET && Regex("erklar\\w*.*formel|formel.*erklar").containsMatchIn(n) ->
                "sheet.explain" to listOfNotNull(Regex("\\b([A-Za-z]{1,3}\\d{1,6})\\b").find(wish)?.groupValues?.get(1)?.let { "zelle" to it.uppercase() }).toMap()
            area == ActionArea.SHEET && Regex("(summe|mittelwert|durchschnitt)").containsMatchIn(n) && !Regex("(spalte|je |pro |nach )").containsMatchIn(n) -> "sheet.summary" to emptyMap()
            area == ActionArea.VIDEO && Regex("(exportier|export|rendern|als mp4)").containsMatchIn(n) && !android ->
                "video.export" to listOfNotNull(when { "opal" in n || "720" in n -> "opal"; "youtube" in n || "1080" in n -> "youtube"; else -> null }?.let { "vorgabe" to it }).toMap()
            area == ActionArea.VIDEO && Regex("untertitel").containsMatchIn(n) && !android -> "video.subtitles" to emptyMap()
            Regex("(verkleiner|komprimier|kleiner machen)").containsMatchIn(n) && (area == ActionArea.PDF || pdfs.isNotEmpty()) && !android -> "pdf.compress" to emptyMap()
            area == ActionArea.PDF && Regex("(texterkennung|\\bocr\\b|text erkennen)").containsMatchIn(n) -> "pdf.ocr" to emptyMap()
            // AP-79: „Prüfe Hausstil und Barrierefreiheit“, „Ist das barrierefrei?“, „Behebe die Hausstil-Fehler“.
            (area == ActionArea.DOC || area == ActionArea.SLIDES) && Regex("(barrierefrei|hausstil|corporate design|alternativtext|screenreader)").containsMatchIn(n) ->
                (if (area == ActionArea.SLIDES) "slides." else "doc.") + (if (Regex("(behebe|beheben|korrigier|reparier|fix)").containsMatchIn(n)) "fix_style" else "check") to emptyMap()
            else -> return null
        }
        val action = ToolActions.byId(id)?.takeIf { !android || it.android } ?: return null
        return planned(action, args)
    }

    /** Plans [action] with [args] on the device (AP-38, AP-71): the step for the panel, or the reason why it cannot be done. */
    internal suspend fun planned(action: ToolAction, args: Map<String, String>): LoraReply {
        val json = kotlinx.serialization.json.JsonObject(args.filterValues { it.isNotBlank() }.mapValues { kotlinx.serialization.json.JsonPrimitive(it.value) })
        return try {
            val (withPrefs, used) = LoraPrefs.withDefaults(c, action, json)
            val plan = action.plan(c, withPrefs)
            ToolActions.log("${action.id} geplant (auf dem Gerät): ${plan.title}")
            val remembered = if (used.isNotEmpty()) " " + tr("(wie gemerkt)") else ""
            val steps = listOfNotNull(LoraStep.Action(plan), LoraPrefs.offerFor(c, action, json))
            LoraReply((if (plan.risky) tr("Das würde ich so machen – passt es?") else plan.title) + remembered, steps)
        } catch (e: ActionFailed) {
            LoraReply(e.message.orEmpty())
        }
    }

    private suspend fun remote(
        wish: String, history: List<Pair<String, String>>, model: String, targets: List<AppTarget>, android: Boolean, onText: (String) -> Unit,
        area: ActionArea? = null,
    ): LoraReply {
        val actions = ToolActions.forArea(area, android)
        val multi = LoraFlows.looksMultiStep(wish)
        val mail = LoraMail.available(c)
        val tools = TOOLS + (if (mail) listOf(LoraMail.TOOL) else emptyList()) + actions.map { it.toolDef() } + if (multi) listOf(LoraFlows.TOOL) else emptyList()
        val flowPrompt = if (!multi) "" else "\nMehrschrittige Aufträge planst du mit plan_workflow (der Nutzer startet den Plan selbst). Aktionen (id (Bereich): was | Parameter, ? = optional):\n" +
            LoraFlows.catalog(android) + "\nJeder Schritt arbeitet mit den Dateien des vorigen weiter; lass Dateiparameter dann leer.\n"
        val mailPrompt = if (mail) "\n" + LoraMail.PROMPT + "\n" else ""
        val messages = mutableListOf(ChatMessage(role = "system", content = systemPrompt(wish, targets, android) + mailPrompt + actionPrompt(area, actions) + mailChoicePrompt() + flowPrompt))
        history.takeLast(4).forEach { (q, a) ->
            messages += ChatMessage(role = "user", content = q)
            messages += ChatMessage(role = "assistant", content = a)
        }
        messages += ChatMessage(role = "user", content = wish)
        val steps = mutableListOf<LoraStep>()
        // „Soll ich mir merken …?“ after actions with a choice of the user (AP-39), shown below them.
        val offers = mutableListOf<LoraStep>()
        var text = ""
        repeat(MAX_ROUNDS) { round ->
            // Wo Lora arbeitet (AP-112): Lora's own place, not the one of the chat.
            val result = kotlinx.coroutines.withContext(io.github.thepytales.llmhub.data.PlaceScope(io.github.thepytales.llmhub.data.Area.LORA)) {
                c.llm.streamChat(model, messages, tools.toSchema(), thinking = ThinkingLevel.OFF) { upd ->
                    splitThinking(upd.content).second.trim().takeIf { it.isNotEmpty() }?.let(onText)
                }
            }
            text = splitThinking(result.content).second.trim()
            if (result.toolCalls.isEmpty()) return LoraReply(text.ifBlank { if (steps.isEmpty()) tr("Wie kann ich helfen?") else defaultText(steps) }, steps + offers, local = false)
            messages += ChatMessage(role = "assistant", content = result.content, toolCalls = result.toolCalls)
            var needsAnswer = false
            for (call in result.toolCalls) {
                val args = parseArgs(call.arguments)
                val action = ToolActions.byToolName(call.name)
                val (output, step) = if (action?.id == MailSorter.ACTION_ID) MailSorter.loraStep(c, args, wish)
                else if (action != null) {
                    // An action of the open tool (AP-38): planned here, carried out by the panel.
                    try {
                        val plan = action.plan(c, LoraPrefs.withDefaults(c, action, args).first)
                        ToolActions.log("${action.id} geplant: ${plan.title}")
                        LoraPrefs.offerFor(c, action, args)?.let { offers += it }
                        // Mail actions (AP-68) run on this device: Lora's model hears nothing of the mailbox.
                        if (MailAccess.isMailAction(action.id)) LoraMail.TOOL_OUTPUT to LoraStep.Action(plan)
                        else (if (plan.risky) "Vorschau wird gezeigt, der Nutzer bestätigt: ${plan.title}" else "Wird ausgeführt: ${plan.title}") to LoraStep.Action(plan)
                    } catch (e: ActionFailed) {
                        needsAnswer = true
                        "FEHLER: ${e.message}" to null
                    } catch (e: Exception) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        needsAnswer = true
                        "FEHLER: ${e.message ?: e.javaClass.simpleName}" to null
                    }
                } else runTool(call.name, args, targets, wish, android)
                step?.let(steps::add)
                if (call.name == "search_chats") steps += searchChats(args.s("suche").orEmpty())
                if (call.name == "explain" || call.name == "search_chats") needsAnswer = true
                messages += ChatMessage(role = "tool", content = output, toolCallId = call.id, toolName = call.name)
            }
            // Actions are carried out by the screen; only an explanation needs the model once more for the answer.
            if (!needsAnswer && steps.isNotEmpty()) return LoraReply(text.ifBlank { defaultText(steps) }, steps + offers, local = false)
            if (round == MAX_ROUNDS - 1) return LoraReply(text.ifBlank { defaultText(steps) }, steps + offers, local = false)
        }
        return LoraReply(text.ifBlank { defaultText(steps) }, steps + offers, local = false)
    }

    /** Runs one of Lora's tools: the text for the model and the step for the screen. */
    internal fun runTool(name: String, args: JsonObject, targets: List<AppTarget>, wish: String = "", android: Boolean = false): Pair<String, LoraStep?> = when (name) {
        "navigate" -> {
            val t = args.s("ziel")?.let { AppMap.byId(it, targets) ?: AppMap.clearHit(it, targets) }
            val off = t?.tool?.let { AppTool.byId(it) }?.takeIf { it.id !in c.settings.current.enabledToolIds }
            if (t == null) "FEHLER: Ziel unbekannt. Nimm eine id aus der Liste." to null
            else if (off != null) "Das Werkzeug „${off.label}“ ist aus; der Nutzer wird gefragt, ob es eingeschaltet werden soll." to LoraStep.EnableTool(off)
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
        // AP-68: a mail task goes to the model on this device; Lora's model gets no mail content back.
        "postfach_auftrag" -> LoraMail.TOOL_OUTPUT to LoraMail.task(c, args.s("auftrag").orEmpty().ifBlank { wish })
        "handoff_chat" -> {
            val request = args.s("anfrage").orEmpty().ifBlank { args.s("zusammenfassung").orEmpty() }
            // From a Postfach chat only what the local model wrote goes on, on the card to check first (AP-68).
            val chatId = io.github.thepytales.llmhub.ui.LoraHelper.chatId
            val postfach = chatId?.let { c.chats.get(it) }?.takeIf { MailAccess.locked(it) }
            if (postfach != null) {
                LoraMail.handoff(c, postfach.id, request)?.let { "Der Nutzer prüft den Text auf einer Karte und gibt ihn selbst weiter." to it }
                    ?: ("FEHLER: Im Postfach-Chat gibt es noch keine Antwort des Modells auf diesem Gerät." to null)
            } else {
                val chosen = args.s("modell")?.takeIf { it.isNotBlank() && ModelCatalog.info(it).displayName.isNotBlank() }
                val (model, reason) = handoffModel(request, chosen, args.s("grund"))
                "Übergeben an einen Chat." to LoraStep.Handoff(model, request, args.s("zusammenfassung").orEmpty(), reason)
            }
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
        "remember_preference" -> {
            val text = args.s("text")?.trim().orEmpty()
            when {
                text.length < 4 -> "FEHLER: Was soll ich mir merken?" to null
                c.drive.isVaultChat(io.github.thepytales.llmhub.ui.LoraHelper.chatId) -> "FEHLER: Im Tresor merkt sich Lora nichts. Sag das dem Nutzer." to null
                Memory.known(c, text) -> "Steht schon im Gedächtnis." to null
                else -> "Der Nutzer wird gefragt, ob du dir „$text“ merken sollst." to LoraStep.Remember(text, args.s("werkzeug")?.lowercase()?.takeIf { w -> LoraPrefs.TOOLS.any { it.first == w } } ?: LoraPrefs.guessTool(text))
            }
        }
        "start_tour" -> {
            val tour = io.github.thepytales.llmhub.ui.LoraTours.find(args.s("werkzeug").orEmpty(), io.github.thepytales.llmhub.ui.LoraHelper.route.value)?.takeIf { !android || it.android }
            if (tour == null) "FEHLER: Touren gibt es für: ${io.github.thepytales.llmhub.ui.LoraTours.available(android).joinToString { it.id }}." to null
            else "Die Tour „${tour.title}“ startet." to LoraStep.Tour(tour.id)
        }
        "plan_workflow" -> {
            val steps = LoraFlows.parse(args)
            val made = if (steps.isEmpty()) null else LoraFlows.draft(c, wish, steps, android)
            if (made == null) "FEHLER: Keine gültigen Schritte. Nimm ids aus der Liste der Aktionen." to null
            else "Der Plan wird als Karte gezeigt (${made.first.steps.size} Schritte); der Nutzer startet ihn." + (if (made.second.isNotEmpty()) " Unbekannt und weggelassen: ${made.second.joinToString()}." else "") to
                LoraStep.Plan(made.first.id, made.second)
        }
        AskUserTool.NAME -> {
            val questions = AskUserTool.parse(args)
            if (questions.isEmpty()) "FEHLER: Keine Frage erkannt." to null else "Die Fragen werden angezeigt." to LoraStep.Ask(questions)
        }
        else -> "FEHLER: Unbekanntes Werkzeug." to null
    }

    /**
     * The mails chosen in Mail & Kalender (AP-69), so „diese Mails“ is understood – only how many: subjects, senders and
     * texts never go to a model off the device (AP-68).
     */
    private fun mailChoicePrompt(): String {
        val chosen = LoraFocus.mails.value?.count ?: 0
        if (chosen == 0 || !io.github.thepytales.llmhub.ui.LoraHelper.route.value.startsWith("mail")) return ""
        return "\nIn Mail & Kalender sind $chosen Mails ausgewählt; „diese Mails“ meint diese Auswahl. Was in den Mails steht, " +
            "bekommst du nicht – Mails liest nur ein Modell auf diesem Gerät.\n"
    }

    /** What Lora can do in the open tool (AP-38) and what the user has in front of them. */
    private fun actionPrompt(area: ActionArea?, actions: List<ToolAction>): String = if (area == null || actions.isEmpty()) "" else buildString {
        append("\nOffen ist: ").append(area.label).append(". Du kannst hier selbst handeln: ")
        append(actions.joinToString { it.toolName })
        append(". Handle, wenn der Wunsch klar ist; Riskantes (verschieben, löschen, überschreiben) zeigt der App vorher eine Vorschau. ")
        append("Erfinde keine Dateinamen – nimm die Auswahl, wenn nichts genannt ist.\n")
        if (LoraFocus.files.isNotEmpty()) append("Ausgewählt: ").append(LoraFocus.files.take(12).joinToString { it.name }).append('\n')
        LoraFocus.folder?.let { append("Offener Ordner: ").append(c.drive.rel(it)?.ifEmpty { "Lora-Ordner" } ?: it.name).append(" (").append(it.listFiles()?.count { f -> f.isFile } ?: 0).append(" Dateien)\n") }
        if (LoraFocus.pdfTabs.isNotEmpty()) append("Offene PDFs: ").append(LoraFocus.pdfTabs.joinToString { it.first }).append(" (das erste ist das aktive)\n")
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
        append("oder Erinnerung im Kalender an (der Nutzer bestätigt); remember_preference(text, werkzeug) nur, wenn der Nutzer eine ")
        append("dauerhafte Vorliebe äußert („immer“, „künftig“) – er bestätigt, nichts wird still gemerkt; start_tour(werkzeug) zeigt eine ")
        append("kurze Tour („Zeig mir, wie das geht“); ask_user nur bei echt mehrdeutigen Wünschen. Heute ist ")
        append(java.time.LocalDate.now()).append(".\n")
        LoraPrefs.promptLine(c)?.let { append(it).append('\n') }
        io.github.thepytales.llmhub.data.Vocabulary.context(c, wish)?.let { append(it).append('\n') }
        append("Änderbare Einstellungen: ").append(SETTINGS.joinToString { "${it.id} (${it.values.joinToString("/") { v -> v.first }})" }).append('\n')
        append("Werkzeuge der App: ").append(AppTool.entries.filter { !it.planned && (!android || it.android) }.joinToString { "${it.id} (${it.label})" }).append('\n')
        append("Orte (id | Art | Pfad | Beschreibung):\n").append(AppMap.candidatesText(wish, targets))
    }

    companion object {
        private const val MAX_ROUNDS = 3

        /** Settings Lora may change (always after asking). */
        val SETTINGS = listOf(
            LoraSetting(
                "theme", trLater("Helligkeit"),
                listOf("SYSTEM" to "Wie das System", "LIGHT" to "Hell", "DARK" to "Dunkel"),
                { it.themeMode }, { s, v -> s.copy(themeMode = v) },
            ),
            LoraSetting(
                "text_size", trLater("Schriftgröße"),
                listOf("0.9" to "Klein", "1.0" to "Normal", "1.15" to "Groß", "1.3" to "Sehr groß"),
                { it.textScale.toString() }, { s, v -> s.copy(textScale = v.toFloat()) },
            ),
            LoraSetting("language", trLater("Sprache der Oberfläche"), listOf("de" to "Deutsch", "en" to "Englisch"), { it.uiLanguage }, { s, v -> s.copy(uiLanguage = v) }),
            LoraSetting(
                "lora", "Lora",
                listOf("EVERYWHERE" to "Überall", "CHAT" to "Nur im Chat", "OFF" to "Aus"),
                { it.loraMode }, { s, v -> s.copy(loraMode = v, hideLora = v == "OFF") },
            ),
            LoraSetting(
                "lora_place", trLater("Wo Lora arbeitet"),
                listOf("MIXED" to "Gemischt", "LOCAL" to "Nur lokal", "SERVER" to "Nur Server"),
                { it.loraPlace }, { s, v -> s.copy(loraPlace = v) },
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
            "theme" to "appearance.theme", "text_size" to "appearance.textsize", "language" to "appearance.language", "lora" to "lora.mode", "lora_place" to "lora.place",
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
            ToolDef("remember_preference", "Fragt den Nutzer, ob Lora sich eine dauerhafte Vorliebe merken soll (z. B. „Handouts zweispaltig“); gespeichert wird erst nach seinem Ja.",
                listOf(Param("text", "Die Vorliebe als kurzer Satz"), Param("werkzeug", "files, pdf, doc, sheet, slides, video, literature oder chat", required = false))),
            ToolDef("start_tour", "Startet eine kurze Tour mit Leuchtrahmen durch ein Werkzeug (pdf, files, templates, video).",
                listOf(Param("werkzeug", "id der Tour, leer: das offene Werkzeug", required = false))),
        ) + AskUserTool().defs

        /** Built-in help (short sections of the README) for explain and the local answer to „Wie geht …?“. */
        val HELP: List<Pair<String, String>> = listOf(
            trLater("Chat und Modelle") to trLater("Neuer Chat oben in der Seitenleiste. Das Modell wechselst du über den Namen über dem Eingabefeld; „Automatisch“ schlägt je Anfrage das passende vor. Dateien und Bilder hängst du mit + an oder ziehst sie hinein."),
            trLater("Modi") to trLater("Modi (Literatursuche, Planungsmodus, Quellenmodus, Exzerpt, Sprachmodus) startest du mit „/“ im Eingabefeld, über Strg+K oder die Startseite. Der Chip über dem Eingabefeld zeigt den Modus, ✕ verlässt ihn."),
            trLater("TU Vorlagen") to trLater("Unter „Erstellen“ in der Seitenleiste: TU Vorlagen (Briefe, Mails, Lehre, Forschung) und unter Windows Gestalten (Poster, Flyer, Social Media) mit Meine Designs. Vorlagen mit vielen Feldern lassen sich auch Schritt für Schritt mit Lora ausfüllen. Mit dem Stern landen sie unter Favoriten auf der Startseite."),
            trLater("Literatur") to trLater("Literatur in der Seitenleiste: Einträge per DOI, ISBN, BibTeX/RIS oder Zotero, Zitierstil unter Einstellungen › Literatur & Zotero. Zu einem Eintrag lässt sich ein Exzerpt erstellen."),
            trLater("Exzerpt") to trLater("Exzerpt-Modus (/Exzerpt) oder Literatur › Eintrag › Exzerpt: PDF anhängen, der Agent füllt Bibliografie, Fragestellung, Theorie, Methode, Befunde und Zitate mit Seitenangaben aus, prüft die Zitate gegen das PDF und hängt das Exzerpt an den Literatureintrag."),
            trLater("Quellenmodus") to trLater("Quellenmodus (/Quellen): Jede Aussage bekommt einen Beleg (Datei und Seite, DOI, URL). Danach prüft die App die Zitate gegen die Quellen und zeigt ein Belegprotokoll, das sich exportieren lässt."),
            trLater("Tresor") to trLater("Der Tresor (Einstellungen › Werkzeuge einschalten) ist ein Bereich mit PIN für sensible Forschung. Inhalte sind verschlüsselt, nur der Uni-Server oder lokale Modelle sind erlaubt, Namen werden streng ersetzt. Der Wiederherstellungsschlüssel setzt eine vergessene PIN zurück."),
            trLater("PDF-Werkzeugkasten") to trLater("Der PDF-Werkzeugkasten (Einstellungen › Werkzeuge einschalten): PDFs in Tabs ansehen, zusammenfügen, teilen, Seiten drehen, löschen und sortieren, Seitenzahlen, markieren, Notizen und Stempel, Formulare ausfüllbar machen."),
            trLater("Datenschutz") to trLater("Einstellungen › Datenschutz: Pseudonymisierung ersetzt Namen vor dem Senden durch Platzhalter und setzt sie in der Antwort zurück. Öffentliche Quellen bleiben, wie sie sind; eine Nie-ersetzen-Liste und ein gründlicher lokaler Modus helfen."),
            trLater("Postfach") to trLater("Mail & Kalender und der Modus „Postfach“: Mails, Ordner, Termine und Aufgaben liest nur ein Modell auf diesem Gerät – der Uni-Server und externe Anbieter sind dort gesperrt, ohne Schalter. Fehlt ein lokales Modell, bietet das Postfach ein passendes zum Herunterladen an. Ergebnisse gibst du mit „An Chat übergeben“ weiter: erst prüfen, dann geht nur dieser Text hinaus."),
            trLater("Projekte") to trLater("Projekte bündeln Chats, Anweisungen, Dateien und eine Datenschutzstufe (Standard, Nur TU Dresden, Nur auf diesem Gerät)."),
            trLater("Transkription") to trLater("Transkription in der Seitenleiste: Audio wählen, auf dem Uni-Server oder dem Gerät verschriften, Sprecher benennen, codieren und exportieren."),
            trLater("Dokumente") to trLater("Der Agent erstellt PDF, Word, PowerPoint und mehr. Unter Dokumente bearbeitest du Entwürfe mit Versionen und Kommentaren."),
            trLater("Darstellung") to trLater("Einstellungen › Darstellung: Stil, Helligkeit (hell/dunkel), Farbton, Schriftgröße und Zoom."),
            trLater("Lora") to trLater("Lora hilft beim Finden: In den Einstellungen unten rechts antippen, auf der Startseite „Lora fragen“ oder Strg+K › Lora fragen. Einstellungen › Lora legt fest, wo sie erscheint."),
            trLater("Updates") to trLater("Neue Versionen meldet die App beim Start. Einstellungen › Updates sucht auf Knopfdruck."),
        )

        /** The help sections that fit [topic]; [shown] in the language of the interface (Lora's own answer), else German for the model. */
        fun explain(topic: String, shown: Boolean = false): String {
            val words = AppMap.words(topic)
            val hits = HELP.map { (title, text) ->
                val hay = AppMap.norm("$title $text")
                title to text to words.count { w -> w.length >= 3 && hay.contains(w.take(maxOf(4, w.length - 2))) }
            }.filter { it.second > 0 }.sortedByDescending { it.second }.take(2)
            if (hits.isEmpty()) return "Dazu steht nichts in der Hilfe. Themen: ${HELP.joinToString { it.first }}."
            return hits.joinToString("\n\n") { (p, _) -> if (shown) "${tr(p.first)}: ${tr(p.second)}" else "${p.first}: ${p.second}" }
        }

        private fun defaultText(steps: List<LoraStep>): String = when (val s = steps.firstOrNull()) {
            is LoraStep.Navigate -> tr("Hier ist es: {0}.", tr(s.target.path))
            is LoraStep.OpenMode -> tr("Ich öffne „{0}“.", tr(s.target.title))
            is LoraStep.ChangeSetting -> tr("Soll ich „{0}“ auf „{1}“ stellen?", tr(s.setting.label), tr(s.setting.labelOf(s.value)))
            is LoraStep.EnableTool -> tr("Dafür gibt es „{0}“ – einschalten?", tr(s.tool.label))
            is LoraStep.Handoff -> tr("Das klärt am besten ein Chat – ich gebe es weiter.")
            is LoraStep.Ask -> tr("Kurz nachgefragt:")
            is LoraStep.OpenChat -> tr("Das habe ich gefunden:")
            is LoraStep.Reminder -> tr("Soll ich „{0}“ in den Kalender eintragen?", s.title)
            is LoraStep.Action -> if (s.dry) tr("Nur ein Probelauf – so sähe es aus:") else if (s.plan.risky) tr("Das würde ich so machen – passt es?") else s.plan.title
            is LoraStep.UndoDay -> tr("Das habe ich heute erledigt:")
            is LoraStep.Remember -> tr("Soll ich mir das merken?")
            is LoraStep.Plan -> tr("So würde ich vorgehen – passt der Plan?")
            is LoraStep.Tour -> tr("Ich zeige es dir.")
            is LoraStep.MailSort -> tr("Ich lese die Mails auf diesem Gerät und zeige dir dann die Vorschau.")
            is LoraStep.Choose -> tr("Meinst du „{0}“ oder „{1}“?", s.options.getOrNull(0)?.title.orEmpty(), s.options.getOrNull(1)?.title.orEmpty())
            null -> tr("Wie kann ich helfen?")
        }

        /**
         * „Nein, ich meinte …“, „ich meinte eigentlich …“, "no, I meant …" (AP-72) on the normalized wish (lower case,
         * no accents): the rest is the corrected wish. A bare „nein“ or „ich meine, …“ in a sentence is no correction.
         */
        private val CORRECTION = Regex("^(?:(?:nein|ne|nee|noe|no|nicht das|falsch|quatsch)[\\s,.!]+(?:ich meine|ich meinte|i mean|i meant|gemeint war|ich wollte)|ich meinte|ich wollte eigentlich|gemeint war|i meant)(?:\\s+eigentlich|\\s+doch)?[\\s,:]+(.+)$")

        /** The corrected wish of „nein, ich meinte …“, or null (AP-72). */
        fun correction(wish: String): String? =
            CORRECTION.find(AppMap.norm(wish).trim())?.groupValues?.get(1)?.trim()?.trimEnd('.', '!')?.takeIf { it.length >= 2 }

        private val TERM_QUESTION = Regex("(?i)^(?:was (?:ist|sind|bedeutet|heißt|heisst|meint)|wofür steht|wofuer steht|wer ist|what is|what does)\\s+(.{2,60}?)(?:\\s+(?:eigentlich|nochmal|noch mal|genau))?(?:\\s+(?:mean|bedeuten))?\\s*\\??$")
        private val TOUR = Regex("(zeig mir,? wie (das|es|man) (geht|funktioniert)|\\btour\\b|rundgang|fuhr mich herum|fuehr mich herum|einfuhrung in|erklar mir diesen bereich|wie funktioniert (das|dieses werkzeug) hier)")
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
            if (chosen != null && chosen != standard) return chosen to (why?.takeIf { it.isNotBlank() } ?: tr("von Lora gewählt"))
            val route = ModelRouter.suggest(request, 0, emptyList(), false, standard)
            return if (route.model != standard) route.model to route.reason else standard to tr("Standard für Übergaben")
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
                return LoraReply(tr("Dafür gibt es „{0}“ – {1} Einschalten?", tr(tool.label), tr(tool.description)), listOf(LoraStep.EnableTool(tool)))
            }
            tools.firstOrNull { t -> t.id in enabledTools && t.keywords.any { k -> Regex("(^|[^a-z])" + Regex.escape(k)).containsMatchIn(n) } }?.let { tool ->
                targets.firstOrNull { it.id == "tool.${tool.id}" }?.let { return LoraReply(tr("Ich öffne „{0}“.", tr(tool.label)), listOf(LoraStep.Navigate(it))) }
            }
            // „Erklär mir …“ about something that is not the app: a chat answers that.
            if (EXPLAIN_START.containsMatchIn(n) && !APP_WORDS.containsMatchIn(n)) {
                val (model, reason) = handoffModel(handoff, wish, null)
                return LoraReply("Das klärt am besten ein Chat – ich gebe es weiter.", listOf(LoraStep.Handoff(model, wish.trim(), reason = reason)))
            }
            val hit = AppMap.clearHit(wish, targets)
            if (HOWTO.containsMatchIn(n) && (hit == null || hit.kind == MapKind.TEMPLATE)) {
                val help = explain(wish, shown = true)
                if (!help.startsWith("Dazu steht nichts")) return LoraReply(help, hits = AppMap.search(wish, targets, 3).map { it.target })
            }
            hit ?: return null
            hit.tool?.let { AppTool.byId(it) }?.takeIf { it.id !in enabledTools && !it.planned }?.let { tool ->
                return LoraReply(tr("Dafür gibt es „{0}“ – {1} Einschalten?", tr(tool.label), tr(tool.description)), listOf(LoraStep.EnableTool(tool)))
            }
            return when (hit.kind) {
                MapKind.MODE, MapKind.TEMPLATE -> LoraReply(tr("Ich öffne „{0}“.", tr(hit.title)), listOf(LoraStep.OpenMode(hit)))
                else -> LoraReply(tr("Hier ist es: {0}.", tr(hit.path)), listOf(LoraStep.Navigate(hit)))
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
