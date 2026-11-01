# Lora – Downloads und automatische Updates

**Lora – Der freundliche Helfer für alle Wünsche in Forschung, Lehre und Verwaltung an der TU Dresden.**

Hier liegen **nur die fertigen Versionen** von Lora (bis Version 1.0.43 „ADEU LLM“) für Windows, macOS, Linux und
Android – kein Quellcode. Die Apps holen sich neue Versionen von hier selbst. Die Dateien und der Programmordner tragen
aus technischen Gründen weiter den alten Namen (`ADEU-LLM-…`, `ADEU LLM.exe`, `ADEU LLM.app`), damit Updates und
vorhandene Daten weiter funktionieren.

## Herunterladen (zum ersten Mal)

Unter **[Releases › neueste Version](https://github.com/ADEU-TUD/ADEU-Harness-Updater/releases/latest)**:

- **Windows:** `ADEU-LLM-x.y.z-Windows.exe` herunterladen und doppelklicken – keine Installation, keine
  Admin-Rechte. Falls Programme in `AppData` gesperrt sind: `ADEU-LLM-x.y.z-Windows.zip` entpacken (z. B. auf den
  Desktop) und `ADEU LLM.exe` im Ordner starten.
- **macOS:** siehe [unten](#macos) (nur Macs mit Apple-Chip, M1 oder neuer).
- **Linux:** siehe [unten](#linux).
- **Android:** `ADEU-LLM-x.y.z.apk` auf dem Handy herunterladen, öffnen und die Installation erlauben.

Ein GitHub-Konto ist nicht nötig.

### macOS

Für Macs mit **Apple-Chip** (M1, M2, M3, M4 …) ab macOS 12 – auf älteren Macs mit Intel-Prozessor läuft Lora nicht.
Welcher Chip drin ist, steht im Apple-Menü (oben links) unter *Über diesen Mac*.

1. `ADEU-LLM-x.y.z-macOS.dmg` herunterladen und doppelklicken.
2. Im Fenster, das sich öffnet, **ADEU LLM** auf den Ordner **Programme** ziehen. Danach das Fenster schließen und
   in der Seitenleiste des Finders das Laufwerk „ADEU LLM“ auswerfen.
   *Ohne Administratorrechte:* die App stattdessen in den Ordner „Programme“ im eigenen Benutzerordner ziehen (im
   Finder *Gehe zu › Benutzerordner*, dort den Ordner „Programme“ anlegen, falls es ihn noch nicht gibt).
3. Lora aus dem Ordner „Programme“ starten (nicht direkt aus dem DMG – sonst funktionieren die Updates nicht).

**Beim ersten Start warnt macOS**, solange die App nicht bei Apple hinterlegt (notarisiert) ist:

- **macOS 12 bis 14:** Die App im Ordner „Programme“ mit **Rechtsklick › Öffnen** starten und in der Rückfrage
  *Öffnen* wählen.
- **macOS 15 und neuer:** Die Warnung mit *Fertig* schließen, dann im Apple-Menü *Systemeinstellungen* › **Datenschutz &
  Sicherheit**, ganz nach unten scrollen und bei „ADEU LLM wurde blockiert“ auf **Dennoch öffnen** klicken; mit
  Passwort oder Touch ID bestätigen.

Das ist nur einmal nötig. Meldet macOS stattdessen, die App sei „beschädigt“, hilft im Programm *Terminal*:
`xattr -dr com.apple.quarantine "/Applications/ADEU LLM.app"` (bzw. der Pfad, in den die App gezogen wurde).

Beim ersten Diktat fragt macOS, ob Lora das **Mikrofon** benutzen darf – *Erlauben* wählen.
Für die **Transkription von Audio- und Videodateien** und den **Videoeditor** braucht Lora zusätzlich das freie
Programm ffmpeg. Am einfachsten über [Homebrew](https://brew.sh): im Terminal `brew install ffmpeg`.

### Linux

Für 64-Bit-Linux (x86-64), getestet unter Ubuntu 24.04 (ältere Systeme wie Ubuntu 22.04 werden nicht unterstützt). Zwei Wege:

- **Ohne Installation (empfohlen, aktualisiert sich selbst):** `ADEU-LLM-x.y.z-Linux.tar.gz` herunterladen und
  entpacken (z. B. nach `~/Programme`), dann im Ordner `ADEU LLM/bin` die Datei **ADEU LLM** starten. Im Terminal:
  ```bash
  mkdir -p ~/Programme
  tar -xzf ~/Downloads/ADEU-LLM-x.y.z-Linux.tar.gz -C ~/Programme
  ~/Programme/"ADEU LLM"/bin/"ADEU LLM"
  ```
  Nur so funktionieren die automatischen Updates – der Ordner muss dem eigenen Benutzer gehören.
- **Als Paket (Debian, Ubuntu, Mint …):** `ADEU-LLM-x.y.z-Linux.deb` herunterladen und mit
  `sudo apt install ./ADEU-LLM-x.y.z-Linux.deb` installieren. Lora steht danach im Startmenü unter *Büro*
  (als „ADEU LLM“). Weil das Paket dem System gehört, aktualisiert es sich nicht selbst: Für Updates die neue
  `.deb` genauso installieren. Entfernen mit `sudo apt remove adeu-llm`.

Für die **Transkription von Audio- und Videodateien** und den **Videoeditor** braucht Lora zusätzlich ffmpeg:
`sudo apt install ffmpeg`.

## Updates

Gibt es eine neue Version, fragt die App **beim Start**:

> **Neue Version verfügbar** – *Jetzt aktualisieren* / *Später*

- **Windows:** *Jetzt aktualisieren* lädt die neue Version, Lora startet kurz neu – fertig. Chats, Einstellungen
  und der API-Key bleiben erhalten, Verknüpfungen auf dem Desktop und in der Taskleiste funktionieren weiter.
- **macOS und Linux:** genauso – *Jetzt aktualisieren* lädt die neue Version, ersetzt die App an ihrem Platz und
  startet Lora neu; Chats und Einstellungen bleiben erhalten. Liegt die App in einem Ordner, in den der eigene
  Benutzer nicht schreiben darf (z. B. das Linux-Paket `.deb` oder ein von der IT verwalteter Mac), sagt die App das
  und verweist auf diese Seite.
- **Android:** Nach dem Herunterladen fragt Android noch einmal – einfach *Aktualisieren* tippen. Beim allerersten
  Update einmalig „Apps aus dieser Quelle zulassen“ einschalten (die App führt dorthin).
- *Später* fragt beim nächsten Start wieder. Von Hand: *Einstellungen › Updates › Jetzt nach Updates suchen*.

Versionen bis 1.0.36 kennen die Update-Suche noch nicht – dort die neue Version einmal wie oben beschrieben
installieren, danach geht es automatisch.

---

## Für die Pflege

Der Code liegt im (privaten) Repository `ADEU-TUD/ADEU-Harness`. **Hier nichts von Hand ändern** – Releases und diese
README kommen von dort (Quelle der README: `.github/updater/README.md`).

**Neue Version veröffentlichen** (ausführlich: README im Code-Repo, *Neue Version veröffentlichen*):
1. Änderungen per Pull Request in `main` des Code-Repos mergen.
2. Dort *Actions › Build & Release › Run workflow* auf `main` mit **version** = letzte Version hier + 1 (z. B. `38`,
   nie leer) und **notes** = „Was ist neu“ in 1–3 kurzen Sätzen.
3. Nach ca. 15 Minuten liegt die Version hier als Release; die Apps fragen beim nächsten Start.
4. Nur das Veröffentlichen schlug fehl (z. B. Token abgelaufen)? Secret `UPDATER_TOKEN` im Code-Repo erneuern, dann
   *Actions › Update veröffentlichen* mit derselben Version – ohne neu zu bauen.

Ein Release enthält:

| Datei | Zweck |
|---|---|
| `version.json` | Versionsnummer, „Was ist neu“, SHA-256 und Größe der Dateien – liest die App beim Start |
| `ADEU-LLM-x.y.z-Windows.zip` | App-Ordner für Windows; daraus aktualisiert sich die Windows-App |
| `ADEU-LLM-x.y.z-Windows.exe` | dasselbe als Ein-Datei-Programm zum ersten Installieren |
| `ADEU-LLM-x.y.z-macOS.tar.gz` | `ADEU LLM.app` für Macs mit Apple-Chip; daraus aktualisiert sich die macOS-App |
| `ADEU-LLM-x.y.z-macOS.dmg` | dasselbe als Disk-Image zum ersten Installieren (in „Programme“ ziehen) |
| `ADEU-LLM-x.y.z-Linux.tar.gz` | App-Ordner `ADEU LLM` für Linux (x86-64); daraus aktualisiert sich die Linux-App |
| `ADEU-LLM-x.y.z-Linux.deb` | Paket `adeu-llm` für Ubuntu 24.04 / Debian 13 und neuer (`/opt/adeu-llm`), Updates von Hand |
| `ADEU-LLM-x.y.z.apk` | Android-App (immer mit demselben Schlüssel signiert, sonst ist kein Update möglich) |
| `Lora-Videoeditor-n.zip` | Nachladbare Erweiterung für Windows: richtet die App beim ersten Öffnen des Videoeditors ein (ffprobe, Sprachmodell der Rauschminderung) |

Die Apps lesen `https://github.com/ADEU-TUD/ADEU-Harness-Updater/releases/latest/download/version.json` –
also immer das neueste Release, das **nicht** als Vorabversion markiert ist. Damit das ohne Konto funktioniert, muss
dieses Repository **öffentlich** bleiben.

```json
{
  "version": "1.0.37",
  "published": "2026-10-27",
  "notes": "Was ist neu – erscheint im Update-Hinweis",
  "windows": { "file": "ADEU-LLM-1.0.37-Windows.zip", "url": "https://github.com/…/ADEU-LLM-1.0.37-Windows.zip", "sha256": "…", "size": 190000000 },
  "android": { "file": "ADEU-LLM-1.0.37.apk", "url": "https://github.com/…/ADEU-LLM-1.0.37.apk", "sha256": "…", "size": 41000000 },
  "macos": { "file": "ADEU-LLM-1.0.37-macOS.tar.gz", "url": "https://github.com/…/ADEU-LLM-1.0.37-macOS.tar.gz", "sha256": "…", "size": 180000000 },
  "linux": { "file": "ADEU-LLM-1.0.37-Linux.tar.gz", "url": "https://github.com/…/ADEU-LLM-1.0.37-Linux.tar.gz", "sha256": "…", "size": 170000000 },
  "extensions": [{ "id": "videoeditor", "name": "Videoeditor-Paket", "version": "1", "file": "Lora-Videoeditor-1.zip", "url": "https://github.com/…/Lora-Videoeditor-1.zip", "sha256": "…", "size": 39000000 }]
}
```

Jede Plattform steht nur drin, wenn ihre Datei zum Release gehört (`macos` und `linux` seit Lora für macOS/Linux).
Erweiterungen (`extensions`) richtet die Windows-App unter `%LOCALAPPDATA%\ADEU LLM\tools\<id>` ein, sobald sie
gebraucht werden; eine höhere `version` aktualisiert sie mit der nächsten Update-Prüfung.

**Version zurückziehen:** Das fehlerhafte Release löschen (oder als Vorabversion markieren) – dann gilt wieder das
vorherige als neuestes. Bereits installierte Geräte gehen dadurch nicht zurück; dafür eine neue, höhere Version mit der
Korrektur veröffentlichen.

**Rechner ohne Zugang zu GitHub:** Die Dateien eines Releases (inkl. `version.json`) in einen Ordner auf dem
Netzlaufwerk legen und in der App unter *Einstellungen › Updates › Update-Quelle* eintragen – oder für alle Rechner
eine Datei `update-quelle.txt` mit dem Pfad neben `ADEU LLM.exe` legen.

**Sicherheit:** Schreibrechte auf dieses Repository hat nur, wer Releases veröffentlichen darf (für GitHub Actions ein
Token, das nur hier *Contents: Read and write* hat). Die Apps prüfen jede Datei mit der SHA-256-Prüfsumme aus
`version.json`; Android installiert Updates nur, wenn sie mit demselben Schlüssel signiert sind wie die installierte App.
Die macOS-App ist ohne Apple-Zertifikat nur „ad hoc“ signiert; mit den Secrets `MAC_CERT_P12_BASE64`,
`MAC_CERT_PASSWORD`, `MAC_SIGN_IDENTITY` (und `MAC_NOTARY_APPLE_ID`, `MAC_NOTARY_PASSWORD`, `MAC_NOTARY_TEAM_ID` für die
Notarisierung) im Code-Repo signiert der Workflow sie mit der Developer ID; ist sie auch notarisiert, entfällt die
Warnung beim ersten Start.
In den Dateien stehen keine Zugangsdaten – API-Keys und Tokens werden erst auf den Geräten eingetragen.
