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
    /** A tool that must be switched on for the place to exist (pdf, video); null = always there. */
    val tool: String? = null,
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
        AppTarget(
            "area.scan", MapKind.AREA, trLater("Scannen"), trLater("Dateien › + › Scannen"),
            trLater("Papier scannen: mehrere Seiten fotografieren (Handy) oder mit dem Scanner einlesen (Windows), Kanten finden, gerade rücken, Kontrast – als durchsuchbares PDF mit Texterkennung in Dateien › Scans."),
            "scannen scan scanner einscannen dokument scannen foto vom dokument abfotografieren kamera seite pdf aus fotos beleg quittung rechnung kassenbon zettel ocr", "scan",
        ),
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
        // AP-41: every switch of the settings has its glowing frame.
        option("github.auto", "github", trLater("GitHub in neuen Chats aktiv"), trLater("GitHub › In neuen Chats automatisch aktiv"), trLater("Das Modell kann sofort auf GitHub zugreifen."), "github automatisch aktiv werkzeuge"),
        option("chat.autoModelAsk", "chat", trLater("Vor dem Modellwechsel fragen"), trLater("Chat & Werkzeuge › Vor dem Wechsel fragen"), trLater("Fragen, bevor ein anderes Modell genommen wird."), "modellwechsel fragen wechsel nachfragen"),
        option("chat.documents", "chat", trLater("Dokumente erstellen"), trLater("Chat & Werkzeuge › Dokumente erstellen"), trLater("PDF, Word, PowerPoint, Briefe und Termine im Chat erstellen."), "dokumente erstellen pdf word powerpoint werkzeug"),
        option("chat.autoContinue", "chat", trLater("Automatisch weitermachen"), trLater("Chat & Werkzeuge › Danach automatisch weitermachen"), trLater("Der Agent arbeitet nach dem Schrittlimit selbst weiter."), "weitermachen limit schritte automatisch fortsetzen"),
        option("memory.learn", "memory", trLater("Nach dem Chat dazulernen"), trLater("Gedächtnis › Nach dem Chat dazulernen"), trLater("Dauerhafte Angaben aus Chats vorschlagen oder merken."), "dazulernen lernen gedächtnis automatisch merken"),
        option("memory.chats", "memory", trLater("Frühere Chats einbeziehen"), trLater("Gedächtnis › Frühere Chats einbeziehen"), trLater("Das Modell durchsucht frühere Chats, wenn du dich darauf beziehst."), "frühere chats einbeziehen letztes mal verlauf"),
        option("memory.suggestions", "memory", trLater("Vorschläge aus meinem Wissen"), trLater("Gedächtnis › Vorschläge aus meinem Wissen"), trLater("Vorlagen schlagen unter jedem Feld passende Angaben vor."), "vorschläge felder ausfüllen vorlagen wissen"),
        option("lora.handoffReview", "lora", trLater("Übergaben erst zeigen"), trLater("Lora › Übergaben erst zeigen"), trLater("Übergebene Fragen landen zuerst im Eingabefeld."), "übergabe zeigen selbst senden"),
        option("lora.nextSteps", "lora", trLater("Nächste Schritte unter Antworten"), trLater("Lora › Nächste Schritte unter Antworten"), trLater("Fristen, Excel, Literatur unter der letzten Antwort anbieten."), "nächste schritte vorschläge antwort"),
        option("lora.aiUse", "lora", trLater("KI-Nutzungsnachweis vorschlagen"), trLater("Lora › KI-Nutzungsnachweis vorschlagen"), trLater("Bei Arbeiten einen KI-Nutzungsnachweis anbieten."), "ki nutzungsnachweis erklärung hausarbeit"),
        option("lora.typing", "lora", trLater("Hinweise beim Tippen"), trLater("Lora › Hinweise beim Tippen"), trLater("Beim Tippen im Chat den passenden Ort zeigen."), "hinweise tippen eingabe"),
        option("lora.hints", "lora", trLater("Hinweise erlaubt"), trLater("Lora › Hinweise erlaubt"), trLater("Lora darf einmal je Bereich auf etwas hinweisen."), "hinweise tipps lora"),
        option("lora.tours", "lora", trLater("Touren anbieten"), trLater("Lora › Touren anbieten"), trLater("Beim ersten Öffnen eines Werkzeugs eine kurze Tour anbieten."), "tour touren rundgang einführung zeig mir wie das geht erklärung"),
        option("lora.memory", "lora", trLater("Was Lora über dich weiß"), trLater("Lora › Gedächtnis"), trLater("Vorlieben je Werkzeug ansehen, ändern, löschen."), "vorlieben gedächtnis lora merken vergessen was weißt du"),
        option("lora.log", "lora", trLater("Protokoll der Lora-Aktionen"), trLater("Lora › Protokoll"), trLater("Was Lora in den Werkzeugen getan hat."), "protokoll aktionen verlauf was hat lora gemacht"),
        option("connections.mailServer", "connections", trLater("Mailserver statt Outlook"), trLater("Verbindungen › Statt des klassischen Outlook verwenden"), trLater("Mit dem Mailserver statt mit Outlook arbeiten."), "mailserver imap outlook mail", android = false),
        option("desktop.tray", "windows", trLater("Im Infobereich weiterlaufen"), trLater("Windows › Beim Schließen im Infobereich weiterlaufen"), trLater("Antworten und Aufgaben laufen weiter, wenn das Fenster zu ist."), "infobereich tray hintergrund schließen weiterlaufen", android = false),
        option("desktop.autostart", "windows", trLater("Mit Windows starten"), trLater("Windows › Mit Windows starten"), trLater("Lora startet nach der Anmeldung im Infobereich."), "autostart windows starten anmeldung", android = false),
        option("desktop.contextmenu", "windows", trLater("Lora im Explorer-Kontextmenü"), trLater("Windows › „Mit Lora öffnen“ und „Lora fragen“ im Explorer"), trLater("Rechtsklick auf eine Datei im Explorer öffnet sie in Lora oder fragt Lora."), "kontextmenü explorer rechtsklick mit lora öffnen lora fragen", android = false),
        option("desktop.trash", "windows", trLater("Windows-Papierkorb"), trLater("Windows › Gelöschte Dateien in den Windows-Papierkorb"), trLater("Gelöschte Dateien landen im Papierkorb von Windows statt im Lora-Papierkorb."), "papierkorb windows löschen", android = false),
        option("desktop.browser", "windows", trLater("Links neben dem Chat öffnen"), trLater("Windows › Links neben dem Chat öffnen"), trLater("Links öffnen als Tabs rechts neben der App."), "browser links tabs neben chat", android = false),
    )

    /** AP-41: important buttons in the tools (also the steps of the tours, ui/LoraTour). */
    private val PLACES = listOf(
        place("files.search", "files", trLater("Dateien durchsuchen"), trLater("Dateien › Suchfeld"), trLater("Namen, Schlagwörter und Inhalt durchsuchen."), "suchen durchsuchen volltext datei finden"),
        place("files.new", "files", trLater("Neu in Dateien"), trLater("Dateien › +"), trLater("Ordner, Dokument, Tabelle, Präsentation, aus Vorlage oder hochladen."), "neu neuer ordner neues dokument hochladen upload plus"),
        place("files.places", "files", trLater("Orte in Dateien"), trLater("Dateien › Seitenspalte"), trLater("Lora-Ordner, Hochgeladen, Erstellt, Markiert, Papierkorb."), "orte hochgeladen erstellt papierkorb markiert"),
        place("files.sort", "files", trLater("Dateien sortieren"), trLater("Dateien › Sortieren"), trLater("Nach Name, Datum oder Größe sortieren."), "sortieren reihenfolge name datum größe"),
        place("scan.capture", "scan", trLater("Seite fotografieren oder einscannen"), trLater("Scannen › Foto aufnehmen / Vom Scanner"), trLater("Eine Seite fotografieren (Handy) oder mit dem Scanner einlesen (Windows)."), "foto aufnehmen kamera scanner einscannen seite hinzufügen"),
        place("scan.save", "scan", trLater("Scan als PDF speichern"), trLater("Scannen › Als PDF speichern"), trLater("Alle Seiten als durchsuchbares PDF in Dateien › Scans."), "als pdf speichern scan fertig texterkennung ocr"),
        place("pdf.open", "pdf", trLater("PDF öffnen"), trLater("PDF-Werkzeugkasten › Öffnen"), trLater("Ein PDF zum Bearbeiten öffnen."), "pdf öffnen laden", tool = "pdf"),
        place("pdf.modes", "pdf", trLater("Modi des PDF-Werkzeugkastens"), trLater("PDF-Werkzeugkasten › Ansehen, Seiten, Markieren …"), trLater("Ansehen, Seiten, Markieren, Formular, Unterschreiben, Umwandeln, KI, Bearbeiten."), "seiten markieren unterschreiben umwandeln formular modus", tool = "pdf"),
        place("pdf.merge", "pdf", trLater("PDFs zusammenfügen"), trLater("PDF-Werkzeugkasten › Seiten › Zusammenfügen"), trLater("Mehrere PDFs zu einem zusammenfügen."), "zusammenfügen zusammenführen merge pdfs verbinden", tool = "pdf"),
        place("pdf.save", "pdf", trLater("PDF speichern unter"), trLater("PDF-Werkzeugkasten › Speichern unter / Teilen"), trLater("Das bearbeitete PDF speichern oder teilen."), "speichern unter teilen exportieren", tool = "pdf"),
        place("create.lora", "templates", trLater("Mit Lora erstellen"), trLater("Erstellen › Feld oben"), trLater("Lora sagen, was entstehen soll."), "erstellen lora wunsch neu entstehen"),
        place("create.templates", "templates", trLater("TU Vorlagen"), trLater("Erstellen › TU Vorlagen"), trLater("Briefe, Protokolle, Bescheinigungen im Corporate Design."), "vorlagen tu vorlagen brief protokoll"),
        place("create.diagrams", "templates", trLater("Diagramme & Whiteboard"), trLater("Erstellen › Diagramme & Whiteboard"), trLater("Flussdiagramm, Mindmap, Organigramm, Zeitstrahl oder eine freie Tafel – Lora zeichnet aus einer Beschreibung, Mermaid geht hin und zurück."), "diagramm flussdiagramm mindmap organigramm zeitstrahl whiteboard tafel mermaid ablauf", android = false),
        place("create.new", "templates", trLater("Neue Vorlage"), trLater("Erstellen › Neue Vorlage"), trLater("Eine eigene Vorlage anlegen."), "neue vorlage eigene vorlage anlegen"),
        place("video.new", "video", trLater("Neues Videoprojekt"), trLater("Video › Neues Projekt"), trLater("Ein neues Videoprojekt anlegen."), "neues projekt video anlegen", android = false, tool = "video"),
        place("video.titles", "video", trLater("Titel und Bauchbinde"), trLater("Video › Titel & Bauchbinde"), trLater("Titel, Bauchbinde, Einblendung, Abspann im TUD-Design."), "titel bauchbinde einblendung abspann", android = false, tool = "video"),
        place("video.subtitles", "video", trLater("Untertitel"), trLater("Video › Untertitel"), trLater("Untertitel aus der Transkription oder SRT."), "untertitel srt vtt", android = false, tool = "video"),
        place("video.roughcut", "video", trLater("Rohschnitt"), trLater("Video › Rohschnitt"), trLater("Pausen und Füllwörter herausschneiden, mit Lora kürzen."), "rohschnitt kürzen pausen füllwörter", android = false, tool = "video"),
        place("video.record", "video", trLater("Bildschirm aufnehmen"), trLater("Video › Bildschirm aufnehmen"), trLater("Screencast mit Webcam, Mikrofon und Systemton aufnehmen – danach als Projekt mit getrennten Spuren im Videoeditor."), "bildschirmaufnahme screencast aufnehmen webcam mikrofon systemton bildschirm aufzeichnen", android = false, tool = "video"),
        place("video.transcript", "video", trLater("Im Transkript schneiden"), trLater("Video › Transkript"), trLater("Das Video über den Text schneiden: Wörter löschen schneidet, Absätze verschieben verschiebt Clips, Lora schlägt Schnitte vor."), "transkript text schneiden descript füllwörter pausen absatz", android = false, tool = "video"),
        place("video.pip", "video", trLater("Bild-in-Bild"), trLater("Video › Bild-in-Bild"), trLater("Webcam oder zweites Video als Ecke über das Bild legen, auf eigenen Videospuren."), "bild in bild webcam ecke overlay spur ebene", android = false, tool = "video"),
        place("video.tools", "video", trLater("Schnittwerkzeuge"), trLater("Video › Werkzeuge"), trLater("Auswahl (A), Klinge (B), Trimmen (T) und Magnet über der Zeitleiste."), "werkzeug auswahl klinge trimmen magnet schneiden roll slip", android = false, tool = "video"),
        place("video.layout", "video", trLater("Anordnung des Videoeditors"), trLater("Video › Anordnung"), trLater("Bereiche ziehen, Medienpool und Eigenschaften einklappen, Vorschau groß oder im Vollbild, Spurhöhe – und die Anordnung zurücksetzen."), "anordnung zurücksetzen layout medienpool eigenschaften einklappen ausblenden vorschau groß vollbild spurhöhe spuren einklappen seitenleiste", android = false, tool = "video"),
        place("video.export", "video", trLater("Video exportieren"), trLater("Video › Exportieren"), trLater("MP4 für OPAL oder YouTube."), "exportieren mp4 opal youtube rendern", android = false, tool = "video"),
    )

    private fun place(id: String, route: String, title: String, path: String, description: String, synonyms: String, android: Boolean = true, tool: String? = null) =
        AppTarget("place.$id", MapKind.OPTION, title, path, description, synonyms, route, spot = id, android = android, tool = tool)

    private fun settings(section: String, title: String, description: String, synonyms: String, android: Boolean = true) =
        AppTarget("settings.$section", MapKind.SETTINGS, title, "Einstellungen › $title", description, synonyms, "settings/$section", android = android)

    private fun option(id: String, section: String, title: String, path: String, description: String, synonyms: String, android: Boolean = true) =
        AppTarget("option.$id", MapKind.OPTION, title, "Einstellungen › $path", description, synonyms, "settings/$section", spot = id, android = android)

    /** Every spot id the map leads to (a test checks that each has its frame, AP-41). */
    val spotIds: Set<String> get() = (OPTIONS + PLACES).mapNotNull { it.spot }.toSet()

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
        return (AREAS + SETTINGS + OPTIONS + PLACES + modes + tools + tpl).filter { if (android) it.android else it.desktop }
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
