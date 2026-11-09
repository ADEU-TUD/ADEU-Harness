package io.github.thepytales.llmhub.agent

import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.data.AppMap
import io.github.thepytales.llmhub.data.AppTool
import io.github.thepytales.llmhub.data.ModelCatalog
import io.github.thepytales.llmhub.data.Outlook
import io.github.thepytales.llmhub.local.LocalCatalog
import io.github.thepytales.llmhub.ui.ChatHandoff
import io.github.thepytales.llmhub.ui.tr

/**
 * Lora und das Postfach (AP-68): Lora answers with a model on the Uni server, so she never reads mails. She leads to the
 * Postfach and takes on mail tasks (summarize, sort, draft an answer, deadlines) – the model on this device carries them
 * out in a Postfach chat, and the step line says „läuft auf diesem Gerät“. Her handoff from a Postfach chat takes only
 * what the local model wrote, on the card to check first ([MailHandoff]).
 */
object LoraMail {
    /** Lora's tool for mail tasks: hands the wording of the user (never mail content) to the model on this device. */
    val TOOL = ToolDef(
        "postfach_auftrag",
        "Gibt einen Auftrag zu Mails, Kalender oder Aufgaben (zusammenfassen, sortieren, Antwort entwerfen, Fristen finden) an das " +
            "Modell auf diesem Gerät, das im Postfach arbeitet. Du selbst liest keine Mails und bekommst kein Ergebnis.",
        listOf(Param("auftrag", "Der Auftrag in den Worten des Nutzers, z. B. „Was ist seit gestern wichtig?“")),
    )

    /** One line for Lora's system prompt while the mail tool is on. */
    const val PROMPT = "Mails, Kalender und Aufgaben liest du nie selbst: Aufträge dazu gibst du mit postfach_auftrag an das Modell auf diesem Gerät " +
        "(Datenschutz: ins Postfach darf nur ein lokales Modell)."

    /** Whether Lora offers mail tasks: the tool „Mail & Kalender“ is on and there is a mailbox. */
    fun available(c: AppContainer): Boolean = c.settings.current.toolOn(AppTool.MAIL.id) && Outlook.supported

    /** What Lora's model hears after a mail task or mail action: no title, no content, no result. */
    const val TOOL_OUTPUT = "Läuft im Postfach mit dem Modell auf diesem Gerät; das Ergebnis sieht nur der Nutzer. Sag kurz, dass es dort weitergeht."

    private val MAIL = Regex("\\b(e ?mails?|mails?|postfach|posteingang|outlook|inbox)\\b")
    private val TASK = Regex("(zusammenfass|fass\\w* .*zusammen|wichtig|sortier|ordne|einordn|entwurf|entwirf|antwort|beantwort|frist|termine? aus|was steht|was gibt es neu|ungelesen)")
    private val SORT = Regex("(sortier|ordne|einordn)")
    private val HANDOFF = Regex("(ubergib|ubergeb|weitergeb|gib\\w* (das|es|die\\w*)? ?(an|weiter)|an (einen|den|ein) (chat|anderes modell)|an deepseek|deepseek)")

    /**
     * Clear mail wishes on the device, without any model: from a Postfach chat „gib das weiter“ opens the handoff card
     * for the last answer of the local model; a mail task starts a Postfach chat. Null for other wishes.
     */
    fun fromWish(c: AppContainer, wish: String, chatId: String?): LoraReply? {
        val n = AppMap.norm(wish)
        val chat = chatId?.let { c.chats.get(it) }
        if (chat != null && MailAccess.locked(chat) && HANDOFF.containsMatchIn(n)) return handoff(c, chat.id, "")?.let { LoraReply(tr("Prüf den Text, dann gebe ich ihn weiter."), listOf(it)) }
            ?: LoraReply(tr("In diesem Chat gibt es noch keine Antwort des Modells auf diesem Gerät, die ich weitergeben könnte."))
        if (!available(c) || !MAIL.containsMatchIn(n) || !TASK.containsMatchIn(n)) return null
        // Sorting belongs to the mail.sort action once it is registered (AP-69).
        if (SORT.containsMatchIn(n) && ToolActions.byId("mail.sort") != null) return null
        val step = task(c, wish)
        return LoraReply(tr("Das übernimmt das Modell auf diesem Gerät im Postfach – ich lese keine Mails."), listOf(step))
    }

    /**
     * The step that hands [task] to the model on this device: a new Postfach chat that starts at once. Without a local
     * model the chat opens with the task in the input and offers one to download.
     */
    fun task(c: AppContainer, task: String): LoraStep.Action {
        val text = task.trim()
        val plan = ActionPlan("mail.task", tr("Postfach: {0}", text.take(60) + if (text.length > 60) "…" else ""), risky = false) {
            val model = MailAccess.model(c)
            ChatHandoff.pending.value = ChatHandoff.Content(text, emptyList(), mode = ChatModes.MAIL.id, send = model != null, model = model)
            ChatHandoff.open.value = ""
            ActionResult(
                if (model != null) tr("Läuft im Postfach mit {0}.", LocalCatalog.get(model)?.name?.let { tr(it) } ?: ModelCatalog.info(model).displayName)
                else tr(MailAccess.NEEDS_MODEL),
            )
        }
        return LoraStep.Action(plan)
    }

    /**
     * Lora's handoff from the Postfach chat [chatId]: the last answer of the model on this device (with [request] of
     * Lora's model as the question) on the card to check. Null when the local model has not answered yet.
     */
    fun handoff(c: AppContainer, chatId: String, request: String): LoraStep.Action? {
        val chat = c.chats.get(chatId) ?: return null
        val answer = MailHandoff.lastLocalAnswer(chat) ?: return null
        val plan = ActionPlan("lora.handoff", tr("An Chat übergeben – erst prüfen"), risky = false) {
            MailHandoff.offer(c, answer, chatId, request)
            ActionResult(tr("Die Karte zum Prüfen ist offen: Nur dieser Text geht an den Chat."))
        }
        return LoraStep.Action(plan)
    }
}
