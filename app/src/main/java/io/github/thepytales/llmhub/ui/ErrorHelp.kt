package io.github.thepytales.llmhub.ui

import io.github.thepytales.llmhub.net.ApiException

/**
 * Fehlermeldungen in Klartext (AP-74): one catalog for the chat, Lora's panel and the banners. Every error becomes one
 * sentence a user can act on plus the matching step as a button ([Fix]); the technical message stays foldable. Works
 * on the device without a language model, also for the error texts already stored in older chats.
 */
object ErrorHelp {
    /** The step that helps, shown as a button. [route] opens a screen (null: the place handles it, e.g. retry). */
    enum class Fix(val label: String, val route: String?) {
        RETRY(trLater("Erneut versuchen"), null),
        CHECK_KEY(trLater("Schlüssel prüfen"), "settings/api"),
        CHANGE_MODEL(trLater("Modell wechseln"), "models"),
        NEW_CHAT(trLater("Neuen Chat beginnen"), "chat"),
        LOCAL_MODELS(trLater("Modelle auf diesem Gerät"), "local-models"),
        /** Wo Lora arbeitet (AP-112): the setup „Alles lokal“ loads what is missing. */
        LOCAL_SETUP(trLater("Einrichtung öffnen"), "local-setup"),
        ENABLE_TOOL(trLater("Werkzeuge einschalten"), "settings/tools"),
        CONNECTIONS(trLater("Verbindungen öffnen"), "settings/connections"),
        TRANSCRIPTION(trLater("Spracherkennung laden"), "settings/transcription"),
        FREE_SPACE(trLater("Papierkorb leeren"), "settings/trash"),
        MEMORY(trLater("Arbeitsspeicher ansehen"), "settings/ram"),
        GITHUB(trLater("GitHub-Token eintragen"), "settings/github"),
        PRIVACY(trLater("Datenschutz öffnen"), "settings/privacy"),
        UPDATES(trLater("Nach Updates sehen"), "settings/updates"),
        REPORT(trLater("Problem melden"), "settings/help"),
    }

    /** An error in plain words: [text] for everyone, [fix] as the button, [technical] to fold open (null = same text). */
    data class Plain(val id: String, val text: String, val fix: Fix?, val technical: String?)

    private class Rule(val id: String, val fix: Fix?, val text: () -> String, val match: (Throwable?, String) -> Boolean)

    private fun rule(id: String, fix: Fix?, text: () -> String, match: (Throwable?, String) -> Boolean) = Rule(id, fix, text, match)
    private fun has(raw: String, vararg words: String) = words.any { raw.contains(it, ignoreCase = true) }
    private fun http(raw: String, vararg codes: Int) = codes.any { Regex("""\bHTTP $it\b""").containsMatchIn(raw) }
    private fun code(e: Throwable?, raw: String, vararg codes: Int) = (e as? ApiException)?.code?.let { it in codes } ?: http(raw, *codes)
    private inline fun <reified T : Throwable> Throwable?.isA() = generateSequence(this) { it.cause }.take(6).any { it is T }

    /** The catalog, most specific first. Ids are stable (tests and the error log refer to them). */
    private val rules: List<Rule> = listOf(
        rule("abgebrochen", null, { tr("Abgebrochen.") }) { e, raw -> e is kotlinx.coroutines.CancellationException || raw.trim() == "Abgebrochen." || raw.trim() == tr("Abgebrochen.") },
        rule("postfach-lokal", Fix.LOCAL_MODELS, { tr("Ins Postfach darf nur ein Modell auf diesem Gerät. Bitte ein lokales Modell laden oder wählen.") }) { _, raw ->
            has(raw, "nur ein Modell auf diesem Gerät", "only a model on this device")
        },
        // Wo Lora arbeitet (AP-112): the request must stay on this device – the message itself says what is missing.
        rule("nur-auf-diesem-geraet", Fix.LOCAL_SETUP, { "" }) { e, raw ->
            e.isA<io.github.thepytales.llmhub.data.OnDeviceOnly>() || has(raw, "Wo Lora arbeitet › Einrichtung")
        },
        rule("kein-schluessel", Fix.CHECK_KEY, { tr("Es ist noch kein API-Schlüssel eingetragen. Ohne ihn erreicht die App den Uni-Server nicht.") }) { _, raw ->
            has(raw, "Kein API-Key hinterlegt", "No API key")
        },
        rule("github-token", Fix.GITHUB, { tr("Für GitHub fehlt ein Token. Er lässt sich unter Einstellungen › GitHub eintragen.") }) { _, raw ->
            has(raw, "Kein GitHub-Token", "No GitHub token")
        },
        rule("werkzeuge-nicht-unterstuetzt", Fix.CHANGE_MODEL, { tr("Dieses Modell kann hier keine Werkzeuge benutzen. Ein anderes Modell kann es.") }) { e, raw ->
            code(e, raw, 400, 404, 422) && has(raw, "tool_choice", "tool choice", "tools are not supported", "does not support tools", "function calling")
        },
        rule("chat-zu-lang", Fix.NEW_CHAT, { tr("Der Chat ist zu lang für dieses Modell. Ein neuer Chat oder ein Modell mit mehr Kontext hilft.") }) { e, raw ->
            code(e, raw, 400, 413) && has(raw, "context", "maximum context", "too many tokens", "zu lang für das Modell")
        },
        rule("schluessel-ungueltig", Fix.CHECK_KEY, { tr("Der API-Schlüssel wird nicht angenommen – vielleicht ist er abgelaufen. Im TU-Self-Service lässt sich ein neuer erzeugen.") }) { e, raw ->
            code(e, raw, 401) && !has(raw, "GitHub", "Nextcloud", "Zotero", "MCP", "Mailserver")
        },
        rule("kein-zugriff-modell", Fix.CHANGE_MODEL, { tr("Mit diesem Schlüssel gibt es keinen Zugriff auf dieses Modell. Bitte ein anderes wählen.") }) { e, raw ->
            code(e, raw, 403) && !has(raw, "GitHub", "Nextcloud", "Zotero", "MCP", "Mailserver")
        },
        rule("modell-weg", Fix.CHANGE_MODEL, { tr("Dieses Modell gibt es auf dem Server nicht mehr. Bitte ein anderes wählen.") }) { e, raw ->
            code(e, raw, 404) && has(raw, "Modell", "model")
        },
        rule("anfrage-zu-gross", null, { tr("Die Anfrage ist zu groß für den Server. Bitte den Anhang verkleinern oder weniger Bilder schicken.") }) { e, raw ->
            code(e, raw, 413)
        },
        rule("zu-viele-anfragen", Fix.RETRY, { tr("Gerade sind zu viele Anfragen unterwegs. Bitte kurz warten und erneut versuchen.") }) { e, raw ->
            code(e, raw, 429) || has(raw, "rate limit", "Rate-Limit", "zu viele Anfragen")
        },
        rule("server-ueberlastet", Fix.CHANGE_MODEL, { tr("Der Uni-Server erreicht das Modell gerade nicht (überlastet oder in Wartung). Später erneut versuchen oder ein anderes Modell wählen.") }) { e, raw ->
            code(e, raw, 502, 503, 504)
        },
        rule("server-fehler", Fix.RETRY, { tr("Der Server hatte einen Fehler. Meist hilft es, es gleich noch einmal zu versuchen.") }) { e, raw ->
            code(e, raw, 500)
        },
        rule("offline", Fix.LOCAL_MODELS, { NetErrorsText.offline() }) { e, raw ->
            e.isA<java.net.UnknownHostException>() || e.isA<java.net.ConnectException>() || e.isA<java.net.NoRouteToHostException>() ||
                has(raw, "Keine Internetverbindung", "UnknownHostException", "Unable to resolve host", "Failed to connect", "Connection refused", "No address associated")
        },
        rule("zeitueberschreitung", Fix.RETRY, { tr("Der Server hat zu lange nicht geantwortet. Bitte erneut versuchen – bei sehr langen Texten hilft ein schnelleres Modell.") }) { e, raw ->
            e.isA<java.net.SocketTimeoutException>() || has(raw, "Zeitüberschreitung", "timeout", "timed out")
        },
        rule("verbindung-abgebrochen", Fix.RETRY, { tr("Die Verbindung ist abgerissen. Bitte erneut versuchen.") }) { e, raw ->
            has(raw, "unexpected end of stream", "Connection reset", "stream was reset", "Broken pipe", "Software caused connection abort")
        },
        rule("ssl", Fix.RETRY, { tr("Die sichere Verbindung ist fehlgeschlagen. Stimmt die Uhrzeit des Geräts? In WLANs mit Anmeldeseite zuerst im Browser anmelden.") }) { e, raw ->
            e.isA<javax.net.ssl.SSLException>() || has(raw, "SSLHandshake", "PKIX path", "certificate")
        },
        rule("speicher-voll", Fix.FREE_SPACE, { tr("Auf dem Gerät ist kein Platz mehr frei. Bitte Platz schaffen, z. B. den Papierkorb leeren oder große Dateien löschen.") }) { _, raw ->
            has(raw, "No space left", "ENOSPC", "nicht genügend Speicherplatz", "disk full", "not enough space on the disk")
        },
        rule("arbeitsspeicher", Fix.MEMORY, { tr("Der Arbeitsspeicher hat nicht gereicht. Bitte andere Programme schließen oder die Datei in kleineren Teilen bearbeiten.") }) { e, raw ->
            e.isA<OutOfMemoryError>() || has(raw, "OutOfMemoryError", "Java heap space", "out of memory")
        },
        rule("datei-gesperrt", Fix.RETRY, { tr("Die Datei ist gerade in einem anderen Programm geöffnet. Bitte dort schließen und erneut versuchen.") }) { e, raw ->
            e.isA<java.nio.file.FileSystemException>() && has(raw, "used by another process", "anderen Prozess verwendet") ||
                has(raw, "being used by another process", "von einem anderen Prozess verwendet", "locked by another", "Datei ist gesperrt")
        },
        rule("kein-zugriff-datei", null, { tr("Die App darf hier nicht lesen oder schreiben. Bitte einen anderen Ort wählen oder die Berechtigung prüfen.") }) { e, raw ->
            e.isA<java.nio.file.AccessDeniedException>() || e.isA<SecurityException>() || has(raw, "Permission denied", "Access is denied", "Zugriff verweigert", "EACCES")
        },
        rule("datei-fehlt", null, { tr("Die Datei gibt es nicht mehr – sie wurde verschoben, umbenannt oder gelöscht.") }) { e, raw ->
            e.isA<java.io.FileNotFoundException>() || e.isA<java.nio.file.NoSuchFileException>() || has(raw, "No such file", "ENOENT", "Das System kann die angegebene Datei nicht finden")
        },
        rule("pdf-passwort", null, { tr("Das PDF ist mit einem Passwort geschützt. Bitte zuerst im PDF-Werkzeugkasten entsperren.") }) { e, raw ->
            e?.javaClass?.simpleName == "InvalidPasswordException" || has(raw, "InvalidPasswordException", "password is incorrect", "Cannot decrypt PDF")
        },
        rule("datei-kaputt", Fix.REPORT, { tr("Die Datei lässt sich nicht lesen – sie ist beschädigt oder hat ein anderes Format, als ihre Endung sagt.") }) { e, raw ->
            e.isA<java.util.zip.ZipException>() || has(raw, "ZipException", "Missing root object", "Header doesn't contain versioninfo", "End-of-File, expected line", "not a valid OOXML", "Invalid header signature")
        },
        rule("werkzeug-aus", Fix.ENABLE_TOOL, { tr("Dieses Werkzeug ist ausgeschaltet. Es lässt sich unter Einstellungen › Werkzeuge einschalten.") }) { _, raw ->
            has(raw, "Werkzeug ist ausgeschaltet", "ist ausgeschaltet – Einstellungen › Werkzeuge", "tool is switched off")
        },
        rule("lokales-modell-fehlt", Fix.LOCAL_MODELS, { tr("Auf diesem Gerät ist noch kein passendes Modell geladen. Unter Modelle auf diesem Gerät lässt sich eines laden.") }) { _, raw ->
            has(raw, "kein lokales Modell", "Kein Modell auf diesem Gerät", "no local model", "llama-server", "lokale Modell startet nicht", "lokales Modell ließ sich nicht starten")
        },
        rule("spracherkennung-fehlt", Fix.TRANSCRIPTION, { tr("Für Sprache auf dem Gerät fehlt noch ein Erkennungsmodell. Es lässt sich unter Einstellungen › Transkription laden.") }) { _, raw ->
            has(raw, "ein Erkennungsmodell laden")
        },
        rule("ffmpeg-fehlt", Fix.UPDATES, { tr("Für Video und Ton fehlt der App ein Baustein (ffmpeg). Ein Update der App bringt ihn wieder mit.") }) { _, raw ->
            has(raw, "ffmpeg fehlt", "Cannot run program \"ffmpeg", "ffmpeg not found")
        },
        rule("outlook", Fix.CONNECTIONS, { tr("Outlook antwortet nicht. Bitte prüfen, ob das klassische Outlook installiert und einmal gestartet ist.") }) { _, raw ->
            has(raw, "Outlook.Application", "Outlook ist nicht", "0x80080005", "CO_E_SERVER_EXEC_FAILURE")
        },
        rule("verbindung-einrichten", Fix.CONNECTIONS, { "" }) { _, raw ->
            has(raw, "Einstellungen › Verbindungen", "noch nicht eingerichtet (Einstellungen")
        },
        rule("mikrofon", null, { tr("Das Mikrofon ist nicht verfügbar. Bitte prüfen, ob es angeschlossen ist und die App es benutzen darf.") }) { e, raw ->
            e?.javaClass?.simpleName == "LineUnavailableException" || has(raw, "LineUnavailableException", "microphone", "RECORD_AUDIO")
        },
    )

    /** [e] in plain words (the same as [explain] with its message). */
    fun explain(e: Throwable): Plain {
        val raw = (if (e is ApiException) e.message else null) ?: rawOf(e)
        if (e !is OutOfMemoryError && e.javaClass.simpleName in bugs) {
            return Plain("unbekannt", unknown(), Fix.REPORT, "${e.javaClass.simpleName}: ${e.message.orEmpty()}".trimEnd(':', ' '))
        }
        return explain(raw, e)
    }

    private fun unknown() = tr("Da ist etwas schiefgelaufen, womit die App nicht gerechnet hat. Bitte erneut versuchen – hilft das nicht, gern über „Problem melden“ Bescheid geben.")

    /** A stored or caught error text in plain words; [e] when the exception itself is still there. */
    fun explain(raw: String, e: Throwable? = null): Plain {
        val r = rules.firstOrNull { runCatching { it.match(e, raw) }.getOrDefault(false) }
        if (r != null) {
            // Texts of the app that already say what to do keep their words; the rule only adds the button.
            val text = r.text().ifEmpty { raw.trim() }
            return Plain(r.id, text, r.fix, raw.trim().takeIf { it.isNotEmpty() && it != text })
        }
        val clean = raw.trim()
        if (readable(clean)) return Plain("app", clean, settingsFix(clean), null)
        // „„Bericht.pdf“ ließ sich nicht öffnen: java.io.IOException …“: the app's own part stays, the rest folds away.
        val cut = clean.indexOf(": ")
        if (cut > 0) {
            val head = clean.substring(0, cut).trim()
            if (readable(head)) return Plain("app", head.trimEnd('.', ':') + ".", Fix.REPORT, clean)
        }
        return Plain("unbekannt", unknown(), Fix.REPORT, clean.ifEmpty { null })
    }

    /** The message of [e] for [explain]: own message, else the cause's, else the class name. */
    private fun rawOf(e: Throwable): String = generateSequence(e) { it.cause }.take(4)
        .firstNotNullOfOrNull { it.message?.takeIf(String::isNotBlank) }?.let { msg ->
            if (e.message.isNullOrBlank()) "${e.javaClass.simpleName}: $msg" else msg
        } ?: e.javaClass.name

    private val technical = Regex(
        """(Exception|Error\b|java\.|kotlinx?\.|javax\.|android\.|okhttp|\bnull\b|\bat [\w.]+\(|\bNaN\b|\bHTTP \d{3}\b|\$\w|\b[a-z]\w*\.[a-z]\w*\.[a-z]\w*\b|For input string|cannot be cast|out of bounds|Failed requirement|Required value was null|Collection is empty|List is empty|Stream closed|lateinit property)""",
    )

    /** Programming errors: their message is never meant for people, whatever it says. */
    private val bugs = setOf(
        "NullPointerException", "KotlinNullPointerException", "ClassCastException", "IndexOutOfBoundsException",
        "ArrayIndexOutOfBoundsException", "StringIndexOutOfBoundsException", "NumberFormatException", "ArithmeticException",
        "ConcurrentModificationException", "NoSuchElementException", "UnsupportedOperationException",
        "UninitializedPropertyAccessException", "StackOverflowError", "NoClassDefFoundError", "UnsatisfiedLinkError",
        "ExceptionInInitializerError", "NoSuchMethodError", "AbstractMethodError",
    )

    /**
     * Whether [text] is already a sentence for people (the app's own error texts, German or English): no class names,
     * stack lines or bare codes, and it reads like a sentence.
     */
    fun readable(text: String): Boolean {
        if (text.length < 8 || text.length > 600) return false
        if (technical.containsMatchIn(text)) return false
        val words = text.split(Regex("""\s+""")).filter { it.isNotBlank() }
        return words.size >= 3 && text.any { it.isLowerCase() }
    }

    /** „… (Einstellungen › Verbindungen)“ in an app text: the button to that page. */
    private fun settingsFix(text: String): Fix? = when {
        has(text, "Einstellungen › Verbindungen", "Settings › Connections") -> Fix.CONNECTIONS
        has(text, "Einstellungen › Transkription", "Settings › Transcription") -> Fix.TRANSCRIPTION
        has(text, "Einstellungen › Werkzeuge", "Settings › Tools") -> Fix.ENABLE_TOOL
        has(text, "Einstellungen › Datenschutz", "Settings › Privacy") -> Fix.PRIVACY
        has(text, "Einstellungen › GitHub", "Settings › GitHub") -> Fix.GITHUB
        has(text, "API-Zugang", "API access") -> Fix.CHECK_KEY
        has(text, "Modelle auf diesem Gerät", "Models on this device") -> Fix.LOCAL_MODELS
        else -> null
    }

    /** The ids of the catalog (for tests and „Problem melden“). */
    val ids: List<String> get() = rules.map { it.id }
}

/** The offline sentence of [io.github.thepytales.llmhub.net.NetErrors], without a dependency cycle at init. */
private object NetErrorsText {
    fun offline() = io.github.thepytales.llmhub.net.NetErrors.OFFLINE
}
