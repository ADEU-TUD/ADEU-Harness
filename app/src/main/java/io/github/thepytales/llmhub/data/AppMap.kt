package io.github.thepytales.llmhub.data

import io.github.thepytales.llmhub.agent.ChatModes
import java.text.Normalizer
import io.github.thepytales.llmhub.ui.trLater
import io.github.thepytales.llmhub.ui.tr

/** What a place of the [AppMap] is. */
enum class MapKind(val label: String) {
    AREA(trLater("Bereich")), SETTINGS(trLater("Einstellungen")), OPTION(trLater("Einstellung")), MODE(trLater("Modus")), TEMPLATE(trLater("TU Vorlage")), TOOL(trLater("Werkzeug"))
}

/**
 * A place in the app Lora can lead to (AP-07): an area, a settings page, a single option on it (with [spot], the id the
 * screen highlights), a mode, a TU Vorlage or a big tool. [path] is how a person finds it by hand.
 */
data class AppTarget(
    val id: String,
    val kind: MapKind,
    val title: String,
    val path: String,
    val description: String,
    /** Words people use for it (lower case). */
    val synonyms: String,
    /** Screen to open (see ui/AppRoot); "mode:<id>" starts a chat in a mode, "template:<id>" a TU Vorlage. */
    val route: String,
    /** Id of the option on the screen that gets the glowing frame (ui/Spotlight). */
    val spot: String? = null,
    val android: Boolean = true,
    val desktop: Boolean = true,
)

/**
 * Landkarte der App (AP-07): every area, settings page, important option, mode, TU Vorlage and tool with id, path,
 * description and synonyms. Built from the registers that exist anyway (modes, templates, tools) plus the settings
 * pages, so nothing is kept twice. [search] answers simple wishes on the device without any request.
 */
object AppMap {
    data class Hit(val target: AppTarget, val score: Int)

    private val AREAS = listOf(
        AppTarget("area.chat", MapKind.AREA, trLater("Neuer Chat"), trLater("Seitenleiste › Neuer Chat"), trLater("Ein neues Gespräch mit dem gewählten Modell."), "neu chat gespräch frage stellen", "chat"),
        AppTarget(
            "area.templates", MapKind.AREA, trLater("Erstellen"), trLater("Seitenleiste › Erstellen"),
            trLater("Neues erstellen: TU Vorlagen für Briefe, Mails, Protokolle, Bescheinigungen, Lehre und Forschung; unter Windows außerdem Gestalten (Poster, Flyer, Aushänge, Social Media, Kursbanner, Titelbilder) und Meine Designs. Oder Lora sagen, was entstehen soll."),
            "erstellen tu vorlagen vorlagen vorlage prompt brief formular lehre forschung verwaltung gestalten design poster tagungsposter flyer aushang plakat einladungskarte social media instagram kursbanner titelbild kreativ kreativbereich canva meine designs", "templates",
        ),
        AppTarget("area.literature", MapKind.AREA, trLater("Literatur"), trLater("Seitenleiste › Literatur"), trLater("Literatursammlung: Einträge importieren, zitieren, Literaturverzeichnis, Zotero, Exzerpte."), "literatur quellen bibliografie literaturverzeichnis zitieren bibtex ris zotero exzerpt", "literature"),
        AppTarget("area.transcripts", MapKind.AREA, trLater("Transkription"), trLater("Seitenleiste › Transkription"), trLater("Audio und Interviews verschriften, Sprecher erkennen, codieren."), "transkription transkribieren audio interview aufnahme diktat verschriften", "transcripts"),
        AppTarget("area.files", MapKind.AREA, trLater("Dateien"), trLater("Seitenleiste › Dateien"), trLater("Alle Dateien wie in Google Drive: Hochgeladen, Erstellt, eigene Ordner, Dokumente, Präsentationen und Tabellen als Word-, PowerPoint- und Excel-Dateien – Raster und Liste, neue Dokumente über das große Plus, verschieben, Papierkorb, Volltextsuche, Lora-Ordner."),
            "dateien dokumente dokument editor entwurf word tabelle excel präsentation folien powerpoint dateiverwaltung datei finden ordner hochgeladen erstellt papierkorb verschieben schlagwort schlagwörter tag tags volltextsuche suchen durchsuchen zuletzt bearbeitet versionen vergleichen wo ist lora-ordner speicherort", "files"),
        AppTarget("area.images", MapKind.AREA, trLater("Bilder erzeugen"), trLater("Seitenleiste › Mehr › Bilder erzeugen"), trLater("Bilder und Grafiken mit einem Bildmodell erzeugen und bearbeiten."), "bild bilder grafik erzeugen malen zeichnen illustration galerie", "images"),
        AppTarget("area.tasks", MapKind.AREA, trLater("Geplante Aufgaben"), trLater("Seitenleiste › Mehr › Geplante Aufgaben"), trLater("Aufgaben, die der Agent zu festen Zeiten ausführt."), "geplant aufgabe zeitplan wiederholen täglich wöchentlich erinnerung automatisch", "tasks"),
        AppTarget("area.models", MapKind.AREA, trLater("Modellübersicht"), trLater("Einstellungen › Modellübersicht"), trLater("Alle Modelle des Uni-Servers mit Stärken und Einsatzzweck."), "modelle modell liste welches modell", "models"),
        AppTarget("area.compare", MapKind.AREA, trLater("Modelle vergleichen"), trLater("Einstellungen › Modelle vergleichen"), trLater("Zwei Modelle beantworten dieselbe Frage nebeneinander."), "vergleichen vergleich nebeneinander zwei modelle", "compare"),
        AppTarget("area.local", MapKind.AREA, trLater("Modelle auf diesem Gerät"), trLater("Einstellungen › Modelle auf diesem Gerät"), trLater("Sprachmodelle offline auf dem Gerät – für vertrauliche Daten."), "lokal offline gerät herunterladen lokales modell", "local-models"),
        AppTarget("area.usage", MapKind.AREA, trLater("Verbrauch"), trLater("Einstellungen › Verbrauch"), trLater("Anfragen und Tokens pro Tag, Bewertungen."), "verbrauch kosten token statistik anfragen", "usage"),
        AppTarget("area.environments", MapKind.AREA, trLater("Umgebungen"), trLater("Einstellungen › Umgebungen"), trLater("Variablen und Netzwerkzugriff für Agenten."), "umgebung variablen netzwerk agent", "environments"),
    )

    private val SETTINGS = listOf(
        settings("api", trLater("API-Zugang"), trLater("API-Schlüssel des Uni-Servers und weiterer Anbieter."), "api schlüssel key zugang anmelden konto server token"),
        settings("github", trLater("GitHub"), trLater("GitHub-Token für die Arbeit in Repositorys."), "github repository token code programmieren"),
        settings("connections", trLater("Verbindungen"), trLater("Nextcloud (TU-Cloudstore) verbinden und MCP-Server mit fremden Werkzeugen hinzufügen, je Werkzeug erlauben, fragen oder sperren."),
            "verbindungen verbindung nextcloud cloudstore cloud webdav mcp model context protocol server werkzeuge anbinden integration schnittstelle connector"),
        settings("chat", trLater("Chat & Werkzeuge"), trLater("Standardmodell, Denkstufe, Werkzeuge, Freigaben und Agent."), "chat werkzeuge standardmodell freigaben agent websuche"),
        settings("tools", trLater("Werkzeuge"), trLater("Große Werkzeuge ein- und ausschalten, Seitenleiste ordnen."), "werkzeuge einschalten ausschalten seitenleiste mehr reihenfolge"),
        settings("lora", trLater("Lora"), trLater("Wo Lora erscheint, Modell, ruhigere Bewegungen, Hinweise."), "lora maskottchen helfer navigator assistentin"),
        settings("privacy", trLater("Datenschutz"), trLater("Pseudonymisierung, Nie-ersetzen-Liste, gründliche Erkennung."), "datenschutz pseudonymisierung anonym namen ersetzen dsgvo"),
        settings("memory", trLater("Gedächtnis"), trLater("Was sich die App über dich merkt."), "gedächtnis erinnerung merken memory"),
        settings("knowledge", trLater("Wissenssammlung"), trLater("Eigene Dokumente durchsuchbar machen."), "wissen wissenssammlung dokumente durchsuchen rag"),
        settings("literature", trLater("Literatur & Zotero"), trLater("Zitierstil, Literaturdatenbanken, Zotero."), "zitierstil apa din harvard zotero literaturdatenbank"),
        settings("transcription", trLater("Transkription"), trLater("Spracherkennung auf dem Server oder Gerät, Modelle."), "transkription whisper spracherkennung sprecher"),
        settings("documents", trLater("Dokumente & Logos"), trLater("Dokumentstil, Logos, Absender."), "dokumente logo briefkopf absender stil corporate design"),
        settings("voice", trLater("Vorlesen & Spracheingabe"), trLater("Stimme, Vorlesen, Diktieren."), "vorlesen stimme sprache tts diktieren spracheingabe mikrofon"),
        settings("appearance", trLater("Darstellung"), trLater("Stil, Helligkeit, Farbton, Schriftgröße, Zoom."), "darstellung aussehen design farbe theme"),
        settings("backup", trLater("Sicherung & Umzug"), trLater("Alles exportieren und auf einem anderen Gerät importieren."), "sicherung backup export import umzug neues gerät"),
        settings("trash", trLater("Papierkorb"), trLater("Gelöschte Chats und Transkripte wiederherstellen."), "papierkorb gelöscht wiederherstellen"),
        settings("ram", trLater("Arbeitsspeicher"), trLater("Speicherverbrauch der App messen und freigeben."), "arbeitsspeicher ram speicher langsam"),
        settings("folders", trLater("Lokale Ordner"), trLater("Den Agenten in Ordnern auf diesem PC arbeiten lassen."), "ordner lokale dateien pc explorer freigeben", android = false),
        settings("sync", trLater("Synchronisation"), trLater("Datenordner in OneDrive, Google Drive oder Nextcloud – am Büro-PC beginnen, am Laptop weiterarbeiten."), "sync synchronisieren synchronisation cloud onedrive google drive nextcloud zweiter pc laptop büro mehrere rechner", android = false),
        settings("storage", trLater("Speicherorte"), trLater("Eigene Ordner für Dokumente, z. B. in Google Drive."), "speicherort ablage ordner google drive onedrive", android = false),
        settings("updates", trLater("Updates"), trLater("Version, nach Updates suchen, automatische Suche."), "update aktualisieren neue version installieren"),
        settings("help", trLater("Problem melden"), trLater("Fehler beschreiben und anonymes Protokoll senden."), "problem fehler melden bug feedback absturz"),
    )

    /** Single options with the id their screen highlights (see ui/Spotlight). */
    private val OPTIONS = listOf(
        option("appearance.theme", "appearance", trLater("Helligkeit (hell oder dunkel)"), trLater("Darstellung › Helligkeit"), trLater("Hell, dunkel oder wie das System."),
            "dunkel dunkles design dunkelmodus dark mode darkmode nachtmodus nacht hell helles design light theme schwarz augen"),
        option("appearance.style", "appearance", trLater("Stil der App"), trLater("Darstellung › Stil"), trLater("ADEU, TU Dresden, Office, Hoher Kontrast und weitere."),
            "stil aussehen tu dresden office hoher kontrast farben look"),
        option("appearance.textsize", "appearance", trLater("Schriftgröße"), trLater("Darstellung › Schriftgröße"), trLater("Text kleiner oder größer."),
            "schrift schriftgröße größer kleiner lesbar lesen text groß"),
        option("appearance.zoom", "appearance", trLater("Zoom"), trLater("Darstellung › Zoom"), trLater("Die ganze Oberfläche vergrößern."), "zoom vergrößern verkleinern skalierung", android = false),
        option("appearance.language", "appearance", trLater("Sprache der Oberfläche"), trLater("Darstellung › Sprache der Oberfläche"), trLater("Deutsch oder Englisch."),
            "sprache englisch english deutsch oberfläche"),
        option("appearance.startAnimation", "appearance", trLater("Startanimation"), trLater("Darstellung › Startanimation"), trLater("Kurze Animation beim Öffnen."), "startanimation animation start"),
        option("chat.defaultModel", "chat", trLater("Standardmodell"), trLater("Chat & Werkzeuge › Standardmodell"), trLater("Mit diesem Modell startet jeder neue Chat."),
            "standardmodell modell wechseln anderes modell voreinstellung"),
        option("chat.autoModel", "chat", trLater("Modell automatisch wählen"), trLater("Chat & Werkzeuge › Modell automatisch wählen"), trLater("Die App schlägt je Anfrage das passende Modell vor."),
            "automatisch modell wählen auto"),
        option("chat.thinking", "chat", trLater("Denkstufe"), trLater("Chat & Werkzeuge › Denkstufe"), trLater("Wie gründlich Modelle nachdenken."), "denken denkstufe reasoning gedankengang nachdenken"),
        option("chat.web", "chat", trLater("Websuche"), trLater("Chat & Werkzeuge › Websuche, Webseiten & Quellen-Check"), trLater("Im Web suchen und Links lesen."), "websuche internet suchen web"),
        option("chat.collapse", "chat", trLater("Arbeitsschritte einklappen"), trLater("Chat & Werkzeuge › Arbeitsschritte einklappen"), trLater("Werkzeugschritte eingeklappt lassen."), "arbeitsschritte einklappen schritte werkzeugschritte"),
        option("chat.approvals", "chat", trLater("Freigaben"), trLater("Chat & Werkzeuge › Freigaben"), trLater("Wann der Agent vor Änderungen fragt."), "freigabe fragen bestätigen genehmigen"),
        option("lora.mode", "lora", trLater("Lora ein- oder ausblenden"), trLater("Lora › Wo Lora erscheint"), trLater("Überall, nur im Chat oder aus."),
            "lora ausblenden ausschalten verstecken weg maskottchen figur einblenden"),
        option("lora.calm", "lora", trLater("Ruhigere Bewegungen"), trLater("Lora › Ruhigere Bewegungen"), trLater("Lora bewegt sich weniger."), "animation ruhig weniger bewegung reduziert"),
        option("privacy.pseudonymize", "privacy", trLater("Pseudonymisierung"), trLater("Datenschutz › Pseudonymisierung"), trLater("Namen und persönliche Angaben vor dem Senden ersetzen."),
            "pseudonymisierung pseudonym anonym anonymisieren namen ersetzen platzhalter"),
        option("privacy.never", "privacy", trLater("Nie ersetzen"), trLater("Datenschutz › Nie ersetzen"), trLater("Namen, die nie ersetzt werden."), "nie ersetzen ausnahme erlaubt liste"),
        option("voice.engine", "voice", trLater("Stimme"), trLater("Vorlesen & Spracheingabe › Stimme"), trLater("KI-Stimme des Uni-Servers oder offline."), "stimme vorlesen sprecher stimme ändern tts"),
        option("documents.sender", "documents", trLater("Absender"), trLater("Dokumente & Logos › Absender"), trLater("Name und Anschrift für Briefe."), "absender anschrift adresse briefkopf"),
        option("documents.logo", "documents", trLater("Logos"), trLater("Dokumente & Logos › Logos"), trLater("Eigene Logos für Dokumente."), "logo logos hochladen"),
        option("updates.auto", "updates", trLater("Automatisch nach Updates suchen"), trLater("Updates"), trLater("Beim Start nach neuen Versionen suchen."), "update automatisch suchen"),
    )

    private fun settings(section: String, title: String, description: String, synonyms: String, android: Boolean = true) =
        AppTarget("settings.$section", MapKind.SETTINGS, title, "Einstellungen › $title", description, synonyms, "settings/$section", android = android)

    private fun option(id: String, section: String, title: String, path: String, description: String, synonyms: String, android: Boolean = true) =
        AppTarget("option.$id", MapKind.OPTION, title, "Einstellungen › $path", description, synonyms, "settings/$section", spot = id, android = android)

    /** All places of this platform; [templates] are the built-in and own TU Vorlagen. */
    fun build(templates: List<Template> = TemplateLibrary.builtIn, android: Boolean = false): List<AppTarget> {
        val modes = ChatModes.visible().map { m ->
            AppTarget("mode.${m.id}", MapKind.MODE, m.name, "Chat › /${m.command}", m.explain, m.keywords + " modus " + m.command.lowercase(), "mode:${m.id}")
        }
        val tools = AppTool.entries.filter { !it.planned }.map { t ->
            AppTarget("tool.${t.id}", MapKind.TOOL, t.label, "Seitenleiste › Mehr › ${t.label}", t.description, t.keywords.joinToString(" "), t.route, android = t.android)
        }
        val tpl = templates.map { t ->
            AppTarget("template.${t.id}", MapKind.TEMPLATE, t.name, "Erstellen › TU Vorlagen › ${t.section.label} › ${t.name}", t.description, t.name.lowercase(), "template:${t.id}")
        }
        return (AREAS + SETTINGS + OPTIONS + modes + tools + tpl).filter { if (android) it.android else it.desktop }
    }

    fun byId(id: String, targets: List<AppTarget> = build()): AppTarget? =
        targets.firstOrNull { it.id == id } ?: targets.firstOrNull { it.spot == id || it.id.substringAfter('.') == id }

    internal fun norm(s: String): String = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}"), "")
        .replace("ß", "ss").replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()

    /** Words that say nothing about the place („Wo stelle ich … um?“). */
    private val STOP = setOf(
        "wo", "wie", "was", "ich", "mich", "mir", "man", "kann", "konnen", "will", "mochte", "mochten", "wurde", "gerne", "gern", "bitte",
        "der", "die", "das", "den", "dem", "des", "ein", "eine", "einen", "einem", "einer", "und", "oder", "zu", "zum", "zur", "in", "im",
        "an", "am", "auf", "aus", "fur", "mit", "von", "vom", "ist", "sind", "gibt", "es", "um", "stelle", "stellen", "einstellen",
        "andern", "aendern", "finde", "finden", "find", "machen", "mache", "hier", "da", "dass", "mal", "doch", "noch", "app", "so",
        "meine", "mein", "meinen", "nach", "wieder", "jetzt", "lora", "hallo", "hi", "geht", "brauche", "suche", "zeig", "zeige", "offne", "offnen",
        "the", "how", "where", "do", "i", "to", "my",
    )

    /** Words of [text] that count for the search. */
    internal fun words(text: String): List<String> = norm(text).split(' ').filter { it.length > 1 && it !in STOP }

    /**
     * Places that fit [query], best first. Title words count most, then synonyms, then the description; a whole phrase
     * of the synonyms („dunkles design“) counts extra, so it beats places that only share single words.
     */
    fun search(query: String, targets: List<AppTarget> = build(), limit: Int = 5): List<Hit> {
        val q = words(query)
        if (q.isEmpty()) return emptyList()
        val phrase = q.joinToString(" ")
        return targets.mapNotNull { t ->
            // English interface (AP-29): the English title and description count as well.
            val title = norm(t.title + " " + tr(t.title)).split(' ')
            val syn = norm(t.synonyms)
            val synWords = syn.split(' ')
            val desc = norm(t.description + " " + tr(t.description)).split(' ')
            var score = 0
            var matched = 0
            for (w in q) {
                val s = when {
                    title.any { it == w } -> 10
                    synWords.any { it == w } -> 8
                    title.any { it.startsWith(w) && w.length >= 4 } -> 7
                    synWords.any { it.startsWith(w) && w.length >= 4 } -> 6
                    w.length >= 5 && synWords.any { it.length >= 5 && w.startsWith(it.take(5)) } -> 4
                    desc.any { it.startsWith(w) && w.length >= 4 } -> 2
                    else -> 0
                }
                if (s > 0) matched++
                score += s
            }
            if (matched == 0) return@mapNotNull null
            if (q.size > 1 && " $syn ".contains(" $phrase ")) score += 12
            if (norm(t.title) == phrase || norm(tr(t.title)) == phrase) score += 15
            // Options are more precise than their page; templates only count with a strong match.
            score += when (t.kind) {
                MapKind.OPTION -> 2
                MapKind.TEMPLATE -> -4
                else -> 0
            }
            // Places where only a part of the words fit come later.
            score = score * matched / q.size
            Hit(t, score).takeIf { score > 0 }
        }.sortedByDescending { it.score }.take(limit)
    }

    /**
     * The one place the query clearly means – for answering on the device without the model: a good score and a clear
     * lead over the next place. Null when it is not clear (then the model or a list of hits helps).
     */
    fun clearHit(query: String, targets: List<AppTarget> = build()): AppTarget? {
        val hits = search(query, targets, 2)
        val best = hits.firstOrNull() ?: return null
        val second = hits.getOrNull(1)?.score ?: 0
        return best.target.takeIf { best.score >= 8 && best.score >= second + 4 }
    }

    /** Short list for the model: the places that may fit [query] (keeps Lora's prompt small). */
    fun candidatesText(query: String, targets: List<AppTarget>, limit: Int = 14): String {
        val hits = search(query, targets, limit).map { it.target }
        val base = targets.filter { it.kind == MapKind.SETTINGS || it.kind == MapKind.AREA || it.kind == MapKind.TOOL }
        return (hits + base).distinctBy { it.id }.take(limit + 24).joinToString("\n") { t ->
            "- ${t.id} | ${t.kind.label} | ${t.path} | ${t.description}"
        }
    }
}
