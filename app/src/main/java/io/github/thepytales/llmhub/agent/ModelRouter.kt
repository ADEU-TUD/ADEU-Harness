package io.github.thepytales.llmhub.agent

import io.github.thepytales.llmhub.data.FileAttachment
import io.github.thepytales.llmhub.data.ModelCatalog
import java.text.Normalizer

/**
 * Automatic model choice („Automatisch“ in the model picker): which of the recommended models fits a request. Decided
 * on the device from the request itself (no extra request, nothing leaves the device), so it is instant:
 * pictures → the standard model (sees images), programming → Qwen, very long documents → GLM-5.3 Flash, demanding
 * analyses → GLM-5.3, everything else → the standard model. The app asks before switching (see autoModelAsk).
 */
object ModelRouter {
    /** The suggested model and why, in a few words for the question to the user. */
    data class Choice(val model: String, val reason: String)

    /**
     * Signs of programming – whole words where a part would also match everyday words („Förderprogramm“,
     * „Vorlesungsskript“, „Notebook“ for the laptop, „Dresscode“).
     */
    private val code = Regex(
        "(programmier|quellcode|\\bcode\\b|\\bskript(e|s)?\\b|\\bscript\\b|python|javascript|typescript|kotlin|\\bjava\\b|c\\+\\+|\\bc#|" +
            "\\bhtml\\b|\\bcss\\b|\\bsql\\b|regex|regulärer ausdruck|funktion schreiben|\\bbugs?\\b|fehlermeldung|stacktrace|exception|" +
            "compil|kompilier|debugg|github|repository|pull request|\\bcommit|api-aufruf|\\bjson\\b|latex-code|excel-makro|\\bvba\\b|" +
            "r-skript|spss-syntax|jupyter)",
    )
    private val hardWords = listOf(
        "beweis", "herleit", "gründlich", "grundlich", "ausführlich analys", "ausfuhrlich analys", "schritt für schritt", "schritt fur schritt",
        "kritisch prüf", "kritisch pruf", "gutachten", "forschungsdesign", "methodisch", "systematisch vergleich", "abwägen",
        "abwagen", "argumentation prüf", "rätsel", "ratsel", "mathemat", "berechne exakt", "statistische auswertung",
        "widerspr", "begründe ausführlich", "konzept entwickeln",
    )

    private fun norm(s: String) = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFC)

    /** The request is about programming (AP-106: then a local code specialist is used if one is installed). */
    fun isCode(text: String): Boolean = "```" in text || code.containsMatchIn(norm(text))

    /**
     * The model for [text] with [images] and [files]; [github]: the chat works in a repository. Null if nothing
     * speaks for a particular model (then the standard model fits).
     */
    fun suggest(text: String, images: Int, files: List<FileAttachment>, github: Boolean, standard: String = ModelCatalog.DEFAULT_CHAT): Choice {
        val t = norm(text)
        val attached = files.sumOf { it.text.length }
        return when {
            images > 0 || files.any { it.mime.startsWith("image/") } -> Choice(standard, "versteht Bilder")
            github || "```" in text || code.containsMatchIn(t) -> Choice(ModelCatalog.CODE_CHAT, "Programmieren und GitHub")
            attached > 300_000 -> Choice(ModelCatalog.FAST_CHAT, "sehr lange Dokumente, am schnellsten")
            hardWords.any { it in t } || (text.length > 1500 && attached == 0) -> Choice(ModelCatalog.HARD_CHAT, "schwierige Aufgabe – gründlich, aber langsamer")
            else -> Choice(standard, "für fast alles, schnell")
        }
    }

    // ── Mischmodus (AP-71): where a request runs ─────────────────────────────────────────────────────────────

    /** Where language models may run for Lora (Einstellungen › Lora): per request, only on this device, only on the server. */
    enum class Mode {
        MIXED, LOCAL, SERVER;

        companion object {
            fun of(id: String): Mode = entries.firstOrNull { it.name == id } ?: MIXED
        }
    }

    enum class Place { LOCAL, SERVER, NONE }

    /** The place for a request, the local model if it runs there, and why (shown on the card in the chat). */
    data class Route(val place: Place, val model: String? = null, val reason: String = "")

    /** Longer requests go to the server in the mixed mode: small local models lose the thread and take long. */
    const val LOCAL_MAX_CHARS = 600

    /**
     * Signs that a request needs what a small model on the device does not have: the web and current facts, attached
     * or long documents, pictures, files to create, sources – on top of programming and demanding analyses above.
     */
    private val serverWork = Regex(
        "(im web|im internet|im netz|online|websuche|web search|search the web|google|aktuell|neueste|heute|dieses jahr|" +
            "recherch|quellen|studien|literatur|anhang|angehang|beigefug|hochgeladen|attached|upload|" +
            "\\bpdfs?\\b|\\bdatei(en)?\\b|\\bdokument\\b|prasentation|powerpoint|\\bfolien\\b|\\bexcel\\b|tabelle erstell|\\bword\\b|" +
            "\\bbild(er)?\\b|\\bfoto\\b|\\bgrafik\\b|image|diagramm|ausfuhrlich|detailliert|umfassend|vollstandig|in detail|detailed)",
    )

    private fun plain(s: String) = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}"), "").replace("ß", "ss")

    /** The request itself says that it is more than a short task for a small model. */
    fun needsServer(text: String): Boolean {
        val t = plain(text)
        return text.length > LOCAL_MAX_CHARS || "```" in text || code.containsMatchIn(norm(text)) || serverWork.containsMatchIn(t) ||
            hardWords.any { it in t }
    }

    /**
     * Where a request to a language model runs. [simple]: the command model is sure that it is a short, simple task;
     * [localGood]: an installed model that runs well on this hardware, [localAny]: any installed one; [serverOk]: a
     * server model may be asked (key there, the project allows it); [deviceOnly]: the project keeps everything on the
     * device. Commands never come here – they are carried out on the device before.
     */
    fun place(
        mode: Mode, simple: Boolean, text: String, images: Int, files: Int, localGood: String?, localAny: String?,
        serverOk: Boolean, deviceOnly: Boolean = false,
    ): Route = when {
        deviceOnly -> localAny?.let { Route(Place.LOCAL, it, "Projekt „Nur auf diesem Gerät“") } ?: Route(Place.NONE, reason = "kein Modell auf diesem Gerät")
        mode == Mode.LOCAL -> localAny?.let { Route(Place.LOCAL, it, "Lora arbeitet nur lokal") } ?: Route(Place.NONE, reason = "kein Modell auf diesem Gerät")
        mode == Mode.SERVER -> if (serverOk) Route(Place.SERVER, reason = "Lora arbeitet nur über den Server") else Route(Place.NONE, reason = "kein Server-Modell erreichbar")
        localGood != null && simple && images == 0 && files == 0 && !needsServer(text) -> Route(Place.LOCAL, localGood, "kurze Aufgabe – läuft auf diesem Gerät")
        serverOk -> Route(Place.SERVER, reason = if (localGood == null) "kein passendes Modell auf diesem Gerät" else "braucht mehr als das Modell auf diesem Gerät")
        localAny != null -> Route(Place.LOCAL, localAny, "ohne Server – läuft auf diesem Gerät")
        else -> Route(Place.NONE, reason = "kein Modell erreichbar")
    }
}
