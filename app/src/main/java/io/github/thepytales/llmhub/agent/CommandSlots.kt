package io.github.thepytales.llmhub.agent

import io.github.thepytales.llmhub.data.AppMap
import java.time.LocalDate

/**
 * Werte aus dem Wunsch (AP-71): once the command model has said which action is meant, the values come from the wish by
 * rules – on/off, a size, pages, a column, a folder, a file name, a date. A required value that cannot be read makes
 * [args] return null: the wish then goes to the language model, which may ask back. Measured on the Messkorpus
 * (CommandSlotsTest).
 */
object CommandSlots {
    private fun has(n: String, pattern: String) = Regex(pattern).containsMatchIn(n)

    private const val OFF = "\\b(aus|ab|off|nicht|kein|keine|keinen|ohne|weg|raus)\\b|ausschalt|ausmach|abschalt|deaktiv|disable|verbiet|aufhor|hor auf|weglass|skip|ausklapp|entfern"
    private val SIZES = listOf("0.9", "1.0", "1.15", "1.3")

    /**
     * The value of [setting] the wish asks for, or null when it names none („Schriftgröße“ – then Lora shows the option).
     * [current] is the value now: „größer“ and „kleiner“ go one step from there.
     */
    fun settingValue(setting: LoraSetting, wish: String, current: String? = null): String? {
        val n = AppMap.norm(wish)
        val off = has(n, OFF)
        return when (setting.id) {
            "theme" -> when {
                has(n, "system|windows|automatisch") -> "SYSTEM"
                has(n, "dunk|dark|nacht|schwarz") -> if (off) "LIGHT" else "DARK"
                has(n, "\\bhell(e|es|er|en|em)?\\b|light|weiss") -> if (off) "DARK" else "LIGHT"
                else -> null
            }
            "text_size" -> {
                val at = SIZES.indexOf(current).takeIf { it >= 0 } ?: 1
                when {
                    has(n, "sehr gross|riesig|maximal|grosste|ganz gross|very large|huge|so gross wie") -> "1.3"
                    has(n, "normal|standard|zurucksetz|default|reset") -> "1.0"
                    has(n, "kleiner|verkleiner|smaller|zu gross") -> SIZES[maxOf(0, at - 1)]
                    has(n, "grosser|groesser|vergrosser|bigger|larger|zu klein|nicht lesen") -> SIZES[minOf(SIZES.lastIndex, at + 1)]
                    has(n, "klein|small") -> "0.9"
                    has(n, "gross|big|large") -> "1.15"
                    else -> null
                }
            }
            "language" -> when {
                has(n, "englisch|english") -> "en"
                has(n, "deutsch|german") -> "de"
                else -> null
            }
            "lora" -> when {
                has(n, "nur (noch )?im chat|only in chat") -> "CHAT"
                has(n, "ausblend|versteck|verschwind|nicht (mehr )?sehen|hide|$OFF") -> "OFF"
                has(n, "uberall|everywhere|einblend|anzeig|zuruck|wieder|\\b(an|on)\\b|einschalt|aktivier|show") -> "EVERYWHERE"
                else -> null
            }
            "lora_place" -> when {
                has(n, "gemischt|misch|mixed|je anfrage|automatisch|selbst entscheid") -> "MIXED"
                has(n, "server|uni|cloud|online") && !has(n, "ohne server|kein server|nicht (mehr )?(an|uber) (den|einen) server|nichts (mehr )?an") -> "SERVER"
                has(n, "lokal|local|gerat|offline|ohne server|kein server|nicht|nichts") -> "LOCAL"
                else -> null
            }
            "lora_calm" -> when {
                has(n, "ruhig|calm") -> if (off) "false" else "true"
                has(n, "wieder|darf|lebendig|mehr bewegung") -> "false"
                has(n, "weniger|nicht mehr|aufhor|zappel|hupf|tanz|herumlauf") -> "true"
                else -> null
            }
            "pseudonymize" -> when {
                has(n, "nicht an den server|schutz") -> "true"
                else -> if (off || has(n, "lass die namen")) "false" else "true"
            }
            "collapse_steps" -> if (off || has(n, "offen|sehen\\b")) "false" else "true"
            "auto_model" -> if (off || has(n, "selbst$|ich wahle")) "false" else "true"
            else -> if (setting.values.any { it.first == "true" }) (if (off) "false" else "true") else setting.resolve(wish)
        }
    }

    private val QUOTED = Regex("[„\"“»]([^„\"“”«»]{1,80})[“\"”«]")
    private val FILE = Regex("[\\p{L}\\d_.-]*[\\p{L}\\d]\\.(?i:pdf|docx?|xlsx?|pptx?|mp3|m4a|wav|txt|md|csv)\\b")
    private val RANGE = Regex("(\\d{1,4})(?:\\s*(?:-|–|bis)\\s*(\\d{1,4}))?")
    private val CELL = Regex("\\b([A-Za-z]{1,2}\\d{1,5})\\b")
    private val CELLS = Regex("\\b([A-Za-z]{1,2}\\d{1,5}:[A-Za-z]{1,2}\\d{1,5})\\b")
    private val TAIL = Regex("(?i)[\\s,.!?]*(bitte|danke|mal|pls|jetzt|kurz)*[\\s,.!?]*$")

    private fun clean(s: String) = s.replace(TAIL, "").trim().trim('„', '“', '"', '”', '»', '«', ':', ' ')

    /** Page numbers and ranges („2 und 5-7“ → "2,5-7"); numbers that are degrees are left out. */
    internal fun pages(wish: String): String {
        val text = wish.replace(Regex("(?i)\\d+\\s*(grad|°|degrees?)"), " ")
        return RANGE.findAll(text).joinToString(",") { m -> m.groupValues[1] + (m.groupValues[2].takeIf { it.isNotEmpty() }?.let { "-$it" } ?: "") }
    }

    /** What follows the last of the [markers] in the wish, without polite endings. */
    private fun after(wish: String, markers: String): String? =
        Regex("(?i)(?:^|\\s)(?:$markers)\\s+(.+)$").findAll(wish).lastOrNull()?.groupValues?.get(1)?.let(::clean)?.takeIf { it.isNotEmpty() }

    /**
     * The parameters of the action [id] read from [wish]; null when a required one is missing. Files are only named
     * when the wish names one – otherwise the action takes the selection, as with the language model.
     */
    fun args(id: String, wish: String): Map<String, String>? {
        val n = AppMap.norm(wish)
        val quoted = QUOTED.find(wish)?.groupValues?.get(1)?.trim()
        val file = FILE.find(wish)?.value
        val out = LinkedHashMap<String, String>()
        when (id) {
            "files.tidy" -> when {
                "semester" in n -> out["nach"] = "semester"
                "monat" in n -> out["nach"] = "monat"
                has(n, "\\b(typ|art|dateiart|dateityp|type)\\b") -> out["nach"] = "typ"
            }
            "files.move" -> out["ordner"] = quoted
                ?: after(wish, "in den ordner|in ordner|nach|unter|into|to")?.replace(Regex("(?i)\\s+(verschieben|schieben|ablegen|packen|ab)$"), "")?.let(::clean)
                ?: return null
            "files.rename" -> out["neuer_name"] = quoted ?: after(wish, "um in|umbenennen in|umbenennen zu|namen in|name:|rename (?:the file|this|it) to|auf|in|zu|to")
                ?: Regex("(?i)^nenn\\w*\\s+(?:die|das|den)\\s+\\S+\\s+(.+)$").find(wish.trim())?.groupValues?.get(1)?.let(::clean)
                ?: Regex("(?i)soll\\s+(.+?)\\s+hei(ß|ss)en").find(wish)?.groupValues?.get(1)?.let(::clean)
                ?: return null
            "files.tag" -> out["schlagwoerter"] = (quoted ?: after(wish, "schlagw[oö]rter|schlagw[oö]rtern|schlagwort|tags|tag|mit|with"))
                ?.replace(Regex("(?i)\\s+(vergeben|hinzuf[üu]gen|an|anh[äa]ngen)$"), "")?.let(::clean)?.takeIf { it.isNotEmpty() } ?: return null
            "pdf.split" -> out["bereiche"] = when {
                has(n, "einzel|jede seite|single|each page") -> "einzeln"
                else -> RANGE.findAll(wish).filter { it.groupValues[2].isNotEmpty() }.joinToString(";") { "${it.groupValues[1]}-${it.groupValues[2]}" }.ifEmpty { return null }
            }
            "pdf.delete_pages" -> out["seiten"] = pages(wish).ifEmpty { return null }
            "pdf.rotate" -> {
                val degrees = Regex("(?i)\\b(90|180|270)\\s*(?:grad|°|degrees?)").find(wish)?.groupValues?.get(1)
                    ?: if (has(n, "auf den kopf|auf dem kopf|upside")) "180" else if (has(n, "links|gegen den uhrzeiger|left")) "270" else null
                out["seiten"] = pages(wish).ifEmpty { if (has(n, "\\b(alle|alles|all|ganze|gesamte)\\b|das pdf")) "alle" else return null }
                degrees?.let { out["grad"] = it }
            }
            "pdf.convert" -> out["format"] = when {
                has(n, "word|docx") -> "docx"
                has(n, "markdown|\\bmd\\b") -> "md"
                has(n, "\\b(text|txt)\\b") -> "txt"
                else -> return null
            }
            "pdf.redact" -> out["begriffe"] = quoted ?: wish.trim()
                .replace(Regex("(?i)^(bitte\\s+)?(schw[äa]rzen?:?|redact|black out|mach|mache)\\s+"), "")
                .replace(Regex("(?i)^(im pdf\\s+|[üu]berall\\s+|in dem pdf\\s+)*(die begriffe\\s+|den begriff\\s+|den namen\\s+|die namen\\s+|alle stellen mit\\s+)?"), "")
                .replace(Regex("(?i)\\s+(schw[äa]rzen|unkenntlich( machen)?|im pdf.*|[üu]berall.*)$"), "").let(::clean).ifEmpty { return null }
            "doc.write" -> out["auftrag"] = wish.trim()
            "doc.diagram" -> out["beschreibung"] = wish.trim()
            "video.shorten" -> out["wunsch"] = wish.trim()
            "doc.insert_table" -> {
                out["spalten"] = (after(wish, "spalten|columns|tabelle mit|table with|tabelle einf[üu]gen mit|tabelle:|tabelle an mit") ?: return null)
                    .replace(Regex("(?i)\\s+(ein|einf[üu]gen|und \\d+ zeilen.*)$"), "").let(::clean).ifEmpty { return null }
                Regex("(\\d+)\\s*zeilen").find(n)?.let { out["zeilen"] = it.groupValues[1] }
            }
            "sheet.sort" -> {
                out["spalte"] = (after(wish, "nach spalte|nach|by|spalte") ?: return null)
                    .replace(Regex("(?i)[,\\s]+(absteigend|aufsteigend|sortieren|ordnen|descending|ascending|gr[öo](ß|ss)te zuerst|kleinste zuerst)\\b.*$"), "").let(::clean).ifEmpty { return null }
                if (has(n, "absteigend|descending|grosste zuerst|\\bdesc\\b")) out["absteigend"] = "ja"
            }
            "sheet.distribution" -> Regex("(?i)(?:verteilung|h[äa]ufigkeit(?:en)?|distribution|frequency)\\s+(?:der|von|in|of)\\s+(?:spalte\\s+)?(.+)$").find(wish)
                ?.groupValues?.get(1)?.replace(Regex("(?i)\\s+berechnen$"), "")?.let(::clean)?.takeIf { it.isNotEmpty() }?.let { out["spalte"] = it }
            "sheet.group" -> {
                out["gruppe"] = after(wish, "je|pro|per|nach|f[üu]r jede[nrs]?") ?.replace(Regex("(?i)\\s+(berechnen|auswerten.*)$"), "")?.let(::clean) ?: return null
                Regex("(?i)(?:mittelwert|durchschnitt|schnitt|average)\\s+(?:der|des|von|of)?\\s*([\\p{L}\\d.-]+)\\s+(?:je|pro|per|nach|f[üu]r)\\b").find(wish)?.let { out["wert"] = it.groupValues[1] }
            }
            "sheet.explain" -> CELL.find(wish)?.let { out["zelle"] = it.groupValues[1].uppercase() }
            "sheet.chart" -> {
                out["bereich"] = CELLS.find(wish)?.groupValues?.get(1)?.uppercase() ?: return null
                when {
                    has(n, "saule|column") -> "saeulen"
                    has(n, "balken|bar") -> "balken"
                    has(n, "linie|line") -> "linie"
                    has(n, "kreis|torte|pie") -> "kreis"
                    has(n, "punkt|scatter|streu") -> "punkt"
                    has(n, "histogramm") -> "histogramm"
                    has(n, "boxplot") -> "boxplot"
                    else -> null
                }?.let { out["typ"] = it }
            }
            "slides.delete" -> Regex("(?:folie|slide)\\s+(\\d+)").find(n)?.let { out["folie"] = it.groupValues[1] }
            "slides.beautify", "slides.notes" -> Regex("(?:folie|slide)\\s+(\\d+)").find(n)?.let { out["folie"] = it.groupValues[1] }
            "slides.animate" -> {
                when {
                    has(n, "uberblend|fade") -> "ueberblenden"
                    has(n, "schieb|push") -> "schieben"
                    has(n, "\\b(raus|keine|ohne|kein)\\b|entfern|remove") -> "keiner"
                    else -> null
                }?.let { out["uebergang"] = it }
                if ("nacheinander" in n) out["nacheinander"] = "ja"
                Regex("(?:folie|slide)\\s+(\\d+)").find(n)?.let { out["folie"] = it.groupValues[1] }
                if (out.isEmpty()) return null
            }
            "video.export" -> when {
                has(n, "opal|720") -> out["vorgabe"] = "opal"
                has(n, "youtube|1080") -> out["vorgabe"] = "youtube"
            }
            "video.split" -> Regex("\\b(\\d{1,2}:\\d{2})\\b").find(wish)?.let { out["zeit"] = it.groupValues[1] }
            "video.speed" -> out["faktor"] = Regex("(\\d+(?:[.,]\\d+)?)\\s*(?:-?fach|x\\b)").find(n.replace(' ', ' '))?.groupValues?.get(1)
                ?: Regex("(?:auf|to)\\s+(\\d+(?:[.,]\\d+)?)").find(wish)?.groupValues?.get(1)
                ?: when {
                    has(n, "doppelt|double|verdoppel|zeitraffer") -> "2"
                    has(n, "halb|half|zeitlupe") -> "0.5"
                    has(n, "schneller|speed up|erhoh") -> "1.5"
                    has(n, "langsamer|slow") -> "0.75"
                    else -> return null
                }
        }
        // A file named in the wish; without one the action takes what is selected or open.
        if (file != null) when (id) {
            "files.trash", "files.move", "files.tag", "files.to_nextcloud", "files.rename_by_content", "pdf.merge" -> if (id != "pdf.merge") out["dateien"] = file
            "files.transcribe", "files.handout", "pdf.open", "pdf.split", "pdf.compress", "pdf.convert", "pdf.redact", "slides.from_paper" -> out["datei"] = file
        }
        if (id == "files.rename" && file != null && out["neuer_name"] != file) out["datei"] = file
        return out
    }

    private val CHAT_WORDS = Regex(
        "^(wo hatte ich|wo habe ich|wo war|in welchem chat|welcher chat|finde?|such\\w*|ich suche|where did i|find|show|den chat|zeig\\w*)\\b|" +
            "\\b(den|meinen|mein|der|das|ein|einen|altes|alten|my|the|a) (chat|gesprach)\\b|\\b(chat|chats|gesprach|talk about|geschrieben|besprochen|ging es|war das|offnen|von neulich)\\b",
    )
    private val CHAT_STOP = setOf("das", "den", "die", "der", "dem", "uber", "mit", "zu", "zum", "zur", "um", "about", "in", "es", "ich", "war", "von", "hatte", "habe", "wo", "on", "fur", "the", "my", "meine", "meinem", "of")

    /** The words to search earlier chats for („Wo hatte ich das mit dem DFG-Antrag?“ → "dfg antrag"); empty = the latest chats. */
    fun chatQuery(wish: String): String {
        val n = AppMap.norm(wish)
        if (Regex("letzte|zuletzt|recent|neueste").containsMatchIn(n)) return ""
        return CHAT_WORDS.replace(n, " ").split(' ').filter { it.isNotBlank() && it !in CHAT_STOP }.joinToString(" ")
    }

    /** The day of a deadline in the wish, from [today] on. */
    fun date(wish: String, today: LocalDate = LocalDate.now()): LocalDate? = LoraSense.dates(wish, today).firstOrNull()

    /** What the deadline is for: the wish without the date and the words around it. */
    fun reminderTitle(wish: String): String {
        val t = wish
            .replace(Regex("(?<!\\d)\\d{4}-\\d{2}-\\d{2}(?!\\d)|(?<!\\d)\\d{1,2}\\.\\s?\\d{1,2}\\.(\\s?\\d{2,4})?|\\d{1,2}\\.\\s+\\p{L}+(\\s+\\d{4})?"), " ")
            .replace(Regex("(?i)\\b(trag\\w*|eintragen|ein|erinner\\w*|mich|mir|leg\\w*|setz\\w*|anlegen|an|frist|deadline|termin|kalendereintrag|in den kalender|im kalender|kalender|add a reminder|remind me|" +
                "f[üu]r den|f[üu]r die|f[üu]r|am|bis|zum|on|about|for|die|den|das|eine|einen|vor|merk\\w*|bitte)\\b"), " ")
            .replace(Regex("[,:;.!?]+"), " ").replace(Regex("\\s+"), " ").trim()
        return t.ifBlank { "Erinnerung" }.replaceFirstChar { it.uppercase() }.take(80)
    }

    /** The preference in „Merk dir, dass …“. */
    fun preference(wish: String): String = wish.trim()
        .replace(Regex("(?i)^(bitte\\s+)?(merk\\w*|speicher\\w*|notier\\w*|behalte|remember|please remember)(\\s+(es|das|dir|bitte|im kopf|als vorliebe|that))*[\\s,:]*(dass\\s+|das\\s+)?"), "")
        .replace(Regex("(?i)[\\s,–-]+(merk\\w* (es |das )?dir( das)?|bitte merken|merken)[.!]?$"), "").trim().trimEnd('.', '!')
        .replaceFirstChar { it.uppercase() }
}
