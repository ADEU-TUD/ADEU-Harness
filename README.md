# ADEU LLM – Android- und Windows-App für die Uni-LLM-API

Eigene Android-App (und portable Windows-Version) für die OpenAI-kompatible LLM-Schnittstelle unter `https://llm.scads.ai/v1`
(API-Key aus dem TU-Dresden-Self-Service). Inoffiziell, nicht mit der Universität verbunden.

## Funktionen

- **Oberfläche wie die Claude-App** (Standard): ruhige Creme-Töne, weiße Karten, schwarze Knöpfe. Seitenleiste mit Suche,
  geplanten Aufgaben, Ordnern, angehefteten und letzten Chats, Profilbild und „Neuer Chat“. Das Eingabefeld enthält „+“
  (Kamera, Fotos, Dateien, Vorlagen, Persona, Repository, Umgebung), die Modellwahl, Mikrofon und Senden. Das Chat-Menü
  (⋮ oder Tipp auf den Titel) bietet Teilen, Umbenennen, Anheften, Ordner, Verknüpfung auf dem Startbildschirm, Export
  und Löschen. Andere Stile (Material You, ChatGPT, Gemini) unter *Einstellungen › Darstellung*.
- **Modellauswahl wie bei Claude**: oben die empfohlenen Modelle mit kurzem Einsatzzweck, darunter „Weitere Modelle“
  für besondere Zwecke. Veraltete Modelle und Aliase stehen nicht zur Auswahl (in der Modellübersicht als „nicht in der
  Auswahl“ markiert). Standardmodell ist **DeepSeek V4.1 Flash** – schnell, versteht Bilder, nutzt Websuche und Werkzeuge
  und hat 1 Mio. Token Kontext.

  | Empfohlen | Wofür |
  | --- | --- |
  | DeepSeek V4.1 Flash | Für fast alles: Fragen, Texte, Bilder, Websuche, Dokumente (Standard) |
  | GLM-5.3 | Die schwierigsten Aufgaben – gründlich, aber langsamer, keine Bilder |
  | GLM-5.3 Flash | Am schnellsten, auch für sehr lange Dokumente |
  | Qwen3.8 27B | Programmieren und GitHub-Aufgaben |
- **Antworten laufen im Hintergrund weiter**: Chat verlassen oder App schließen (aus den letzten Apps wischen) – die
  Antwort wird trotzdem fertig geschrieben. Solange sie entsteht, zeigt Android eine stille Benachrichtigung mit
  „Stoppen“; ist sie fertig, kommt eine Benachrichtigung, die direkt in den Chat führt. Braucht der Agent eine
  Bestätigung (z.B. für einen Commit), meldet er sich ebenfalls per Benachrichtigung. Anhalten lässt sich die Antwort
  im Chat oder über „Stoppen“ in der Benachrichtigung; „Beenden erzwingen“ in den Android-Einstellungen bricht sie ab.
- **Chat** mit allen Chat-Modellen (Qwen, GLM, DeepSeek, MiniMax, gpt-oss, Gemma, Llama …), Streaming,
  Markdown/Code-Darstellung, Gedankengang von Reasoning-Modellen, Bilder anhängen (Vision-Modelle), Verlauf lokal.
- **Chats verwalten**: Volltextsuche über Titel und Nachrichten, Anheften, Ordner, Umbenennen (lange auf einen Chat
  in der Seitenleiste drücken).
- **Bearbeiten & Neu generieren mit Versionen**: Eigene Nachrichten bearbeiten oder Antworten neu erzeugen (auch mit
  einem anderen Modell) – frühere Versionen bleiben über „‹ 1/2 ›“ erreichbar, wie bei ChatGPT.
- **Dateien als Anhang**: PDF (Text; Scans werden als Bilder angehängt), Word, PowerPoint, Excel, OpenDocument,
  Text- und Code-Dateien werden gelesen und mitgeschickt, z.B. zum Zusammenfassen.
- **Vorlagen**: Personas (System-Prompts wie „Übersetzer“, „Code-Reviewer“, „Lektor“) und Prompt-Vorlagen – eigene
  anlegen, im Chat über „+“ › Vorlagen nutzen.
- **Modelle vergleichen**: Zwei Modelle beantworten dieselbe Frage nebeneinander, mit Antwortzeit, Tokens und
  Tokens/s; eine Seite lässt sich als Chat fortsetzen.
- **Verbrauch**: Anfragen und Tokens pro Tag (Diagramm), nach Modell und Art; nur lokal gezählt.
- **Geplante Aufgaben**: Der Agent führt Aufträge zeitgesteuert aus (täglich/Wochentage um eine Uhrzeit oder alle
  N Stunden), z.B. jeden Morgen News zu einem Thema. Ist eine Aufgabe fertig, erscheint eine Benachrichtigung mit dem
  Anfang des Ergebnisses (Tippen öffnet den Chat) – auch wenn die App gerade offen ist; dann zusätzlich ein Banner oben
  in der App. Neue Ergebnisse sind in der Seitenleiste markiert. Oben im Bereich *Geplante Aufgaben* zeigt die App, ob
  Benachrichtigungen erlaubt sind und ob Android die App im Hintergrund arbeiten lässt, mit Knöpfen zum Einschalten
  und einer Test-Benachrichtigung.
- **Umgebungen für Agenten**: Name, Netzwerkzugriff (vollständig, nur erlaubte Domains, keiner),
  Umgebungsvariablen (auch als `.env` einfügbar) und Hinweise für das Modell. Geheime Variablen sieht das Modell nie;
  es nutzt sie als `${NAME}` in Web-Anfragen (`fetch_url` mit Methode, Headern und Body). Verschlüsselt gespeichert.
- **Modellübersicht** mit Beschreibung, Stärken, Leistungs-/Tempo-Einschätzung, Kontextlänge und Fähigkeiten,
  Suche, Filter und Sortierung.
- **GitHub-Agent**: Mit einem GitHub-Token arbeitet das Modell in deinen Repositories – Dateien lesen, durchsuchen,
  bearbeiten und committen, Branches und Pull Requests erstellen, Issues lesen/kommentieren. Schreibaktionen müssen
  standardmäßig bestätigt werden. Ein GitHub-Link in einer Nachricht wählt das Repository direkt aus.
- **Web**: Websuche (DuckDuckGo), Nachrichtensuche (Bing News/Google News) und Webseiten lesen.
- **Dokumente erstellen**: PDF, Word (.docx), HTML, Markdown und Text. PDFs werden über HTML/CSS gerendert:
  Stile *Modern*, *Kräftig*, *Elegant*, *Klassisch*, *Schlicht*, frei wählbare Akzentfarbe, Fußzeile, Hinweisboxen
  (`> [!NOTE]`, `> [!TIP]`, `> [!WARNING]`). Für besondere Gestaltung (Flyer, Rechnung, Lebenslauf …) schreibt das
  Modell eigenes HTML mit Farben, Formen und Schriften. Klappt das gestaltete Rendern einmal nicht, entsteht trotzdem
  ein PDF mit demselben Text (schlichtes Layout); der automatische Test im Emulator prüft das Rendern vor jedem Release,
  auch bei App im Hintergrund.
- **Logos**: Unter *Einstellungen › Dokumente & Logos* gespeicherte Logos bindet das Modell auf Befehl ein („mit
  meinem Logo“) oder automatisch in jedes Dokument. Ein Bild im Chat kann mit „Speichere das als Logo“ übernommen werden.
- **Darstellung**: Stile *Claude* (Standard), *Material You*, *ChatGPT* und *Gemini*, Farbton, Hell/Dunkel/System,
  reines Schwarz (AMOLED) und Schriftgröße.
- **Bildgenerierung** mit FLUX.2 (Speichern in der Galerie).
- **Spracheingabe** (Whisper) und **Vorlesen**: wahlweise mit der KI-Stimme des Uni-Servers (der die Antwort ohnehin
  geschrieben hat – keine Daten an Dritte) oder komplett offline mit einer Stimme des Geräts; lange Antworten werden in
  Abschnitten gelesen, Code und Links übersprungen.

## Installation

1. Unter **Releases** die neueste `ADEU-LLM-x.y.z.apk` herunterladen.
2. Auf dem Handy öffnen und die Installation aus unbekannten Quellen erlauben.
3. Beim ersten Start Benachrichtigungen erlauben (sonst erfährst du nicht, wann Aufgaben fertig sind).
4. In der App unter **Einstellungen** den API-Key (`sk-…`) eintragen und „Verbindung testen“.
5. Optional: Vorname eintragen (Begrüßung) und GitHub-Token (siehe unten).
6. Für zuverlässige geplante Aufgaben unter *Geplante Aufgaben* „Im Hintergrund erlauben“ tippen. Manche Hersteller
   (z.B. Xiaomi, Oppo/OnePlus/Realme, Huawei) beenden Apps zusätzlich – dort in den Akku-Einstellungen der App
   „Hintergrundaktivität erlauben“ bzw. „Nicht optimieren“ wählen.

Jeder Push auf den Standardbranch baut automatisch eine neue signierte APK und die Windows-Version und veröffentlicht
beide als Release (`.github/workflows/android.yml`); beide werden vorher automatisch gestartet und getestet (Android im
Emulator, Windows auf einem Windows-Rechner von GitHub). Builds anderer Branches erscheinen ebenfalls unter Releases, gekennzeichnet als
*Vorabversion*. Updates lassen sich direkt über die alte Version installieren – API-Key,
GitHub-Token, Einstellungen und Chats bleiben dabei erhalten. Nur beim Deinstallieren gehen sie verloren.

## Windows (portabel, ohne Admin-Rechte)

Dieselbe App gibt es als portable Windows-Version – gleiche Funktionen, gleicher Code (Kotlin/Compose Desktop) und die
Java-Laufzeit ist eingebaut. Nichts muss installiert werden, Admin-Rechte sind nicht nötig.

1. Unter **Releases** `ADEU-LLM-x.y.z-Windows.exe` herunterladen und doppelklicken.
   Beim ersten Start einer Version entpackt sie sich einmalig (wenige Sekunden) nach `%LOCALAPPDATA%\ADEU LLM\app`;
   danach startet sie sofort. Zeigt Windows „Der Computer wurde durch Windows geschützt“ (die Datei ist nicht signiert):
   *Weitere Informationen* › *Trotzdem ausführen*.
2. Alternativ `ADEU-LLM-x.y.z-Windows.zip` entpacken (z.B. auf den Desktop oder einen USB-Stick) und `ADEU LLM.exe`
   im Ordner starten – nützlich, wenn eine Firmenrichtlinie Programme in `AppData` sperrt.
3. In den **Einstellungen** den API-Key eintragen und „Verbindung testen“.

Unterschiede zu Android:

- **Daten** liegen pro Windows-Benutzer in `%APPDATA%\ADEU LLM` und bleiben bei neuen Versionen erhalten. API-Key,
  GitHub-Token und Umgebungen sind mit Windows (DPAPI) verschlüsselt – nur das eigene Windows-Konto kann sie lesen.
- **Im Hintergrund**: Schließt man das Fenster, läuft die App im Infobereich (unten rechts) weiter, Antworten werden
  fertig geschrieben und geplante Aufgaben laufen. Benachrichtigungen kommen als Windows-Benachrichtigung; ein Klick
  öffnet den Chat. Unter *Einstellungen › Windows* lässt sich „Mit Windows starten“ einschalten (ohne Admin-Rechte);
  verpasste Aufgaben werden beim nächsten Start nachgeholt.
- **Anhänge**: über „+“, per Drag & Drop ins Fenster oder mit Strg+V (Screenshot mit Win+Umschalt+S, kopierte Dateien).
  Statt der Kamera gibt es „Einfügen“.
- **PDFs** rendert Microsoft Edge (auf jedem Windows vorhanden) unsichtbar im Hintergrund – dieselbe Chromium-Engine wie
  auf Android. Ohne Edge/Chrome übernimmt ein eingebauter Renderer.
- **Vorlesen offline** nutzt die Stimmen von Windows; die KI-Stimme des Uni-Servers funktioniert wie auf Android.
- **Teilen** kopiert in die Zwischenablage; Dokumente lassen sich öffnen, im Ordner zeigen oder speichern unter.
- **Tastatur**: Enter sendet, Umschalt+Enter neue Zeile, Strg+N neuer Chat, Strg+B Seitenleiste, Strg+, Einstellungen,
  Esc zurück. Rechtsklick auf Chats und Nachrichten öffnet das Menü (wie langes Drücken auf Android).

## GitHub-Token

Unter <https://github.com/settings/personal-access-tokens/new> ein *Fine-grained token* erstellen:

- Repository access: die gewünschten Repositories (oder alle)
- Permissions: **Contents**, **Pull requests**, **Issues** → *Read and write*; **Metadata** → *Read*

Die App prüft das Token beim Eintragen und bei jedem Start. Erst mit einem gültigen Token erscheinen die
GitHub-Optionen (im Chat unter „+“, bei geplanten Aufgaben und in den GitHub-Einstellungen); ohne Token oder mit
einem abgelaufenen Token bleiben sie ausgeblendet.

Im Chat über „+“ › GitHub-Repository ein Repository wählen (oder einfach einen GitHub-Link schreiben). Für Agent-Aufgaben eignen sich Modelle mit Werkzeug-Unterstützung,
z.B. DeepSeek V4.1 Flash, Qwen3.8 27B, GLM-5.3, GLM-5.3 Flash oder MiniMax M3.

## Rate-Limit

Keys aus dem Self-Service-Portal haben laut [Doku](https://llm.scads.ai/docs/usage/api/) ein niedriges Limit an
Anfragen pro Minute (die genaue Zahl ist nicht veröffentlicht). Ein Agent mit vielen Werkzeugschritten erreicht es
schnell, weil jeder Schritt eine eigene Anfrage ist. Der Server antwortet dann mit HTTP 429 und `retry-after`; die App
wartet die angegebene Zeit ab und versucht es bis zu dreimal erneut (Hinweis im Chat). Ein Budget oder Kostenlimit
beschreibt die Doku nicht; ein höheres Limit vergibt das Team auf Anfrage an llm.scads.ai@tu-dresden.de.

## Selbst bauen

Voraussetzung: JDK 17 und Android SDK.

```bash
./gradlew assembleRelease   # app/build/outputs/apk/release/app-release.apk
```

Windows-Version (JDK 17+; die Pakete für Windows entstehen auf Windows):

```bash
./gradlew -p desktop run                 # starten
./gradlew -p desktop createDistributable # desktop/build/compose/binaries/main/app/ADEU LLM/
```

`.github/scripts/windows-portable.ps1` macht daraus die Ein-Datei-EXE und die ZIP.

Signiert wird mit `app/release.keystore` (damit alle Releases dieselbe Signatur haben). Eigene Schlüssel lassen sich
über die Umgebungsvariablen `KEYSTORE_PATH`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` und `KEY_PASSWORD` setzen.

## Aufbau

| Pfad | Inhalt |
| --- | --- |
| `net/LlmClient.kt` | Chat-Streaming (SSE) mit Token-Zählung, Modelle, Bilder, TTS, STT |
| `net/GitHubClient.kt` | GitHub-REST-API |
| `agent/AgentRunner.kt` | Agent-Schleife (Modell → Werkzeuge → Modell …) für Chat und geplante Aufgaben |
| `agent/ChatRuns.kt`, `agent/AgentService.kt` | Laufende Chat-Antworten unabhängig vom Bildschirm; Vordergrunddienst, damit sie bei geschlossener App fertig werden |
| `agent/Tools.kt` | Gemeinsame Werkzeug-Infrastruktur (Function Calling) |
| `agent/GitHubTools.kt`, `agent/WebTools.kt`, `agent/DocumentTools.kt` | Werkzeuge: GitHub, Web/News-Suche, Dokumente & Logos |
| `data/ChatTree.kt` | Chats als Baum (Versionen beim Bearbeiten/Neu generieren) |
| `data/Library.kt`, `data/Stores.kt` | Ordner, Vorlagen, Umgebungen, Logos und deren Speicherung |
| `data/ModelCatalog.kt` | Modellbeschreibungen, Einsatzzwecke und Einstufung (empfohlen / weitere / ausgeblendet) |
| `data/ScheduledTask.kt`, `tasks/TaskScheduler.kt` | Geplante Aufgaben (WorkManager) |
| `tasks/Notifier.kt` | Benachrichtigungen: fertige Antworten, Aufgaben-Ergebnisse, Rückfragen |
| `data/UsageStore.kt` | Verbrauch pro Tag und Modell |
| `files/AttachmentReader.kt` | Text aus PDF, Office- und Textdateien |
| `docs/` | Dokumente: HTML/CSS → PDF (WebView), Word (.docx), HTML, Text; Stile und Logos |
| `audio/` | Aufnahme, Wiedergabe und Vorlesen (Server oder offline) |
| `ui/AppRoot.kt`, `ui/AppDrawer.kt` | Navigation und Seitenleiste |
| `ui/ChatScreen.kt`, `ui/ChatMessages.kt`, `ui/ChatComposer.kt` | Chat-Oberfläche |
| `ui/Theme.kt` | Stile, Farbtöne, Hell/Dunkel |

### Windows-Version (`desktop/`)

`desktop/` ist ein eigener Gradle-Build mit Compose Desktop. Er kompiliert alle Dateien aus `app/src/main/java` mit,
außer denen, die unter `desktop/src/main/kotlin` im gleichen Pfad eine Windows-Fassung haben (z.B. `ui/ChatScreen.kt`,
`docs/HtmlPdf.kt`, `tasks/TaskScheduler.kt`). Änderungen an Logik, Werkzeugen und den meisten Bildschirmen gelten so
automatisch für beide Versionen. Kleine Stellvertreter für Android-Klassen (`android/content/Context.kt`, `Base64`, `Toast`,
`LocalContext`) sorgen dafür, dass gemeinsamer Code unverändert kompiliert.

| Pfad | Inhalt |
| --- | --- |
| `Main.kt` | Fenster, Symbol im Infobereich, Benachrichtigungen, Tastenkürzel, Drag & Drop |
| `SelfTest.kt` | Starttest für CI (`--selftest <Ordner>`): alle Bildschirme, Dokumente, Verschlüsselung |
| `desktop/Paths.kt`, `desktop/Secrets.kt`, `data/FilePrefs.kt` | Datenordner, DPAPI-Verschlüsselung, Einstellungen |
| `desktop/Autostart.kt`, `desktop/SingleInstance.kt` | „Mit Windows starten“, nur eine laufende App |
| `docs/HtmlPdf.kt` | HTML → PDF mit Edge/Chrome (headless), sonst openhtmltopdf |
| `launcher/Launcher.cs` | Ein-Datei-Starter: entpackt die App nach `%LOCALAPPDATA%` und startet sie |

