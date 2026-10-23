# LLM Hub – Android-App für die Uni-LLM-API

Eigene Android-App für die OpenAI-kompatible LLM-Schnittstelle unter `https://llm.scads.ai/v1`
(API-Key aus dem TU-Dresden-Self-Service). Inoffiziell, nicht mit der Universität verbunden.

## Funktionen

- **Oberfläche wie ChatGPT/Claude**: Seitenleiste mit Suche, Ordnern, angehefteten und letzten Chats; Modellauswahl
  oben in der Mitte, Eingabefeld mit „+“-Menü (Kamera, Fotos, Dateien, Vorlagen, Persona, Repository, Umgebung).
  Gewähltes Repository, Umgebung und Persona bleiben als Chips über dem Eingabefeld sichtbar.
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
  N Stunden), z.B. jeden Morgen News zu einem Thema. Ergebnis als Chat im Ordner der Aufgabe, mit Benachrichtigung.
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
  Modell eigenes HTML mit Farben, Formen und Schriften.
- **Logos**: Unter *Einstellungen › Dokumente & Logos* gespeicherte Logos bindet das Modell auf Befehl ein („mit
  meinem Logo“) oder automatisch in jedes Dokument. Ein Bild im Chat kann mit „Speichere das als Logo“ übernommen werden.
- **Darstellung**: Stile *Standard* (Material You), *Claude*, *ChatGPT* und *Gemini*, Farbton, Hell/Dunkel/System,
  reines Schwarz (AMOLED) und Schriftgröße.
- **Bildgenerierung** mit FLUX.2 (Speichern in der Galerie).
- **Spracheingabe** (Whisper) und **Vorlesen**: wahlweise mit der KI-Stimme des Uni-Servers (der die Antwort ohnehin
  geschrieben hat – keine Daten an Dritte) oder komplett offline mit einer Stimme des Geräts; lange Antworten werden in
  Abschnitten gelesen, Code und Links übersprungen.

## Installation

1. Unter **Releases** die neueste `LLMHub-x.y.z.apk` herunterladen.
2. Auf dem Handy öffnen und die Installation aus unbekannten Quellen erlauben.
3. In der App unter **Einstellungen** den API-Key (`sk-…`) eintragen und „Verbindung testen“.
4. Optional: GitHub-Token eintragen (siehe unten).

Jeder Push auf den Standardbranch baut automatisch eine neue signierte APK und veröffentlicht sie als Release
(`.github/workflows/android.yml`). Builds anderer Branches erscheinen ebenfalls unter Releases, gekennzeichnet als
*Vorabversion*. Updates lassen sich direkt über die alte Version installieren – API-Key,
GitHub-Token, Einstellungen und Chats bleiben dabei erhalten. Nur beim Deinstallieren gehen sie verloren.

## GitHub-Token

Unter <https://github.com/settings/personal-access-tokens/new> ein *Fine-grained token* erstellen:

- Repository access: die gewünschten Repositories (oder alle)
- Permissions: **Contents**, **Pull requests**, **Issues** → *Read and write*; **Metadata** → *Read*

Im Chat über „+“ › GitHub-Repository ein Repository wählen (oder einfach einen GitHub-Link schreiben). Für Agent-Aufgaben eignen sich Modelle mit Werkzeug-Unterstützung,
z.B. `Qwen/Qwen3.8-27B`, `zai-org/GLM-5.3`, `zai-org/GLM-5.3-Flash` oder `MiniMaxAI/MiniMax-M3`.

## Selbst bauen

Voraussetzung: JDK 17 und Android SDK.

```bash
./gradlew assembleRelease   # app/build/outputs/apk/release/app-release.apk
```

Signiert wird mit `app/release.keystore` (damit alle Releases dieselbe Signatur haben). Eigene Schlüssel lassen sich
über die Umgebungsvariablen `KEYSTORE_PATH`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` und `KEY_PASSWORD` setzen.

## Aufbau

| Pfad | Inhalt |
| --- | --- |
| `net/LlmClient.kt` | Chat-Streaming (SSE) mit Token-Zählung, Modelle, Bilder, TTS, STT |
| `net/GitHubClient.kt` | GitHub-REST-API |
| `agent/AgentRunner.kt` | Agent-Schleife (Modell → Werkzeuge → Modell …) für Chat und geplante Aufgaben |
| `agent/Tools.kt` | Gemeinsame Werkzeug-Infrastruktur (Function Calling) |
| `agent/GitHubTools.kt`, `agent/WebTools.kt`, `agent/DocumentTools.kt` | Werkzeuge: GitHub, Web/News-Suche, Dokumente & Logos |
| `data/ChatTree.kt` | Chats als Baum (Versionen beim Bearbeiten/Neu generieren) |
| `data/Library.kt`, `data/Stores.kt` | Ordner, Vorlagen, Umgebungen, Logos und deren Speicherung |
| `data/ScheduledTask.kt`, `tasks/TaskScheduler.kt` | Geplante Aufgaben (WorkManager) und Benachrichtigungen |
| `data/UsageStore.kt` | Verbrauch pro Tag und Modell |
| `files/AttachmentReader.kt` | Text aus PDF, Office- und Textdateien |
| `docs/` | Dokumente: HTML/CSS → PDF (WebView), Word (.docx), HTML, Text; Stile und Logos |
| `audio/` | Aufnahme, Wiedergabe und Vorlesen (Server oder offline) |
| `ui/AppRoot.kt`, `ui/AppDrawer.kt` | Navigation und Seitenleiste |
| `ui/ChatScreen.kt`, `ui/ChatMessages.kt`, `ui/ChatComposer.kt` | Chat-Oberfläche |
| `ui/Theme.kt` | Stile, Farbtöne, Hell/Dunkel |
