# LLM Hub – Android-App für die Uni-LLM-API

Eigene Android-App für die OpenAI-kompatible LLM-Schnittstelle unter `https://llm.scads.ai/v1`
(API-Key aus dem TU-Dresden-Self-Service). Inoffiziell, nicht mit der Universität verbunden.

## Funktionen

- **Chat** mit allen Chat-Modellen (Qwen, GLM, DeepSeek, MiniMax, gpt-oss, Gemma, Llama …), Streaming,
  Markdown/Code-Darstellung, Anzeige des Gedankengangs von Reasoning-Modellen, Bilder anhängen (Vision-Modelle),
  eigener System-Prompt pro Chat, Verlauf wird lokal gespeichert.
- **Modellübersicht** mit kurzer Beschreibung, Stärken, Leistungs-/Tempo-Einschätzung, Kontextlänge und
  Fähigkeiten (Denken, Bilder, Werkzeuge). Die Liste wird live über `/v1/models` geladen.
  **Suche und Filter** nach Typ, Fähigkeiten, Mindest-Leistung und -Tempo, Sortierung nach Leistung, Tempo,
  Kontext oder Name – auch in der Modellauswahl im Chat.
- **GitHub-Agent**: Mit einem GitHub-Token kann das Modell (wie ein Coding-Agent) in deinen Repositories arbeiten –
  Dateien auflisten, lesen, durchsuchen, bearbeiten und committen, Branches und Pull Requests erstellen,
  Issues lesen/kommentieren. Schreibaktionen müssen standardmäßig bestätigt werden. Die Werkzeuge sind in neuen
  Chats automatisch aktiv; ein GitHub-Link in einer Nachricht (z.B. `https://github.com/owner/repo`) wählt das
  Repository direkt aus.
- **Dokumente erstellen**: Das Modell kann PDF-, Word- (.docx), HTML-, Markdown- und Textdateien erzeugen
  („Erstelle mir ein PDF mit …“). Die Datei erscheint im Chat zum Öffnen, Teilen und Speichern. Jede Antwort und
  der ganze Chat lassen sich außerdem selbst als Dokument exportieren.
- **Webseiten lesen**: Links aus dem Chat kann das Modell abrufen und auswerten.
- **Darstellung**: Stile *Standard* (Material You), *Claude*, *ChatGPT* und *Gemini*, frei wählbarer Farbton,
  Hell/Dunkel/System, reines Schwarz (AMOLED) und Schriftgröße.
- **Bildgenerierung** mit FLUX.2 (Speichern in der Galerie).
- **Spracheingabe** (Whisper) und **Vorlesen** von Antworten (TTS).

## Installation

1. Unter **Releases** die neueste `LLMHub-x.y.z.apk` herunterladen.
2. Auf dem Handy öffnen und die Installation aus unbekannten Quellen erlauben.
3. In der App unter **Einstellungen** den API-Key (`sk-…`) eintragen und „Verbindung testen“.
4. Optional: GitHub-Token eintragen (siehe unten).

Jeder Push auf den Standardbranch baut automatisch eine neue signierte APK und veröffentlicht sie als Release
(`.github/workflows/android.yml`). Updates lassen sich direkt über die alte Version installieren – API-Key,
GitHub-Token, Einstellungen und Chats bleiben dabei erhalten. Nur beim Deinstallieren gehen sie verloren.

## GitHub-Token

Unter <https://github.com/settings/personal-access-tokens/new> ein *Fine-grained token* erstellen:

- Repository access: die gewünschten Repositories (oder alle)
- Permissions: **Contents**, **Pull requests**, **Issues** → *Read and write*; **Metadata** → *Read*

Im Chat über das `</>`-Symbol ein Repository wählen. Für Agent-Aufgaben eignen sich Modelle mit Werkzeug-Unterstützung,
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
| `net/LlmClient.kt` | Chat-Streaming (SSE), Modelle, Bilder, TTS, STT |
| `net/GitHubClient.kt` | GitHub-REST-API |
| `agent/Tools.kt` | Gemeinsame Werkzeug-Infrastruktur (Function Calling) |
| `agent/GitHubTools.kt` | GitHub-Werkzeuge und deren Ausführung |
| `agent/DocumentTools.kt`, `agent/WebTools.kt` | Dokumente erstellen, Webseiten lesen |
| `docs/` | Markdown → PDF, Word (.docx), HTML, Text |
| `ui/ChatViewModel.kt` | Agent-Schleife: Modell → Werkzeuge → Modell … |
| `data/ModelCatalog.kt` | Beschreibungen und Fähigkeiten der Modelle |
| `data/ModelFilter.kt` | Suche/Filter/Sortierung der Modelle |
| `ui/Theme.kt` | Stile, Farbtöne, Hell/Dunkel |
