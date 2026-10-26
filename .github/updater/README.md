# ADEU LLM – Downloads und automatische Updates

Hier liegen **nur die fertigen Versionen** von ADEU LLM für Windows und Android – kein Quellcode.
Die Apps holen sich neue Versionen von hier selbst.

## Herunterladen (zum ersten Mal)

Unter **[Releases › neueste Version](https://github.com/ADEU-TUD/ADEU-Harness-Updater/releases/latest)**:

- **Windows:** `ADEU-LLM-x.y.z-Windows.exe` herunterladen und doppelklicken – keine Installation, keine
  Admin-Rechte. Falls Programme in `AppData` gesperrt sind: `ADEU-LLM-x.y.z-Windows.zip` entpacken (z. B. auf den
  Desktop) und `ADEU LLM.exe` im Ordner starten.
- **Android:** `ADEU-LLM-x.y.z.apk` auf dem Handy herunterladen, öffnen und die Installation erlauben.

Ein GitHub-Konto ist nicht nötig.

## Updates

Gibt es eine neue Version, fragt die App **beim Start**:

> **Neue Version verfügbar** – *Jetzt aktualisieren* / *Später*

- **Windows:** *Jetzt aktualisieren* lädt die neue Version, ADEU LLM startet kurz neu – fertig. Chats, Einstellungen
  und der API-Key bleiben erhalten, Verknüpfungen auf dem Desktop und in der Taskleiste funktionieren weiter.
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
| `ADEU-LLM-x.y.z.apk` | Android-App (immer mit demselben Schlüssel signiert, sonst ist kein Update möglich) |

Die Apps lesen `https://github.com/ADEU-TUD/ADEU-Harness-Updater/releases/latest/download/version.json` –
also immer das neueste Release, das **nicht** als Vorabversion markiert ist. Damit das ohne Konto funktioniert, muss
dieses Repository **öffentlich** bleiben.

```json
{
  "version": "1.0.37",
  "published": "2026-10-27",
  "notes": "Was ist neu – erscheint im Update-Hinweis",
  "windows": { "file": "ADEU-LLM-1.0.37-Windows.zip", "url": "https://github.com/…/ADEU-LLM-1.0.37-Windows.zip", "sha256": "…", "size": 190000000 },
  "android": { "file": "ADEU-LLM-1.0.37.apk", "url": "https://github.com/…/ADEU-LLM-1.0.37.apk", "sha256": "…", "size": 41000000 }
}
```

**Version zurückziehen:** Das fehlerhafte Release löschen (oder als Vorabversion markieren) – dann gilt wieder das
vorherige als neuestes. Bereits installierte Geräte gehen dadurch nicht zurück; dafür eine neue, höhere Version mit der
Korrektur veröffentlichen.

**Rechner ohne Zugang zu GitHub:** Die Dateien eines Releases (inkl. `version.json`) in einen Ordner auf dem
Netzlaufwerk legen und in der App unter *Einstellungen › Updates › Update-Quelle* eintragen – oder für alle Rechner
eine Datei `update-quelle.txt` mit dem Pfad neben `ADEU LLM.exe` legen.

**Sicherheit:** Schreibrechte auf dieses Repository hat nur, wer Releases veröffentlichen darf (für GitHub Actions ein
Token, das nur hier *Contents: Read and write* hat). Die Apps prüfen jede Datei mit der SHA-256-Prüfsumme aus
`version.json`; Android installiert Updates nur, wenn sie mit demselben Schlüssel signiert sind wie die installierte App.
In den Dateien stehen keine Zugangsdaten – API-Keys und Tokens werden erst auf den Geräten eingetragen.
