# Eigene Modelle veröffentlichen (AP-117)

Lora Namensschutz (`tools/pii`) und Lora Befehle (`tools/befehle`) sind von uns feinabgestimmte Modelle. Jede neue
Fassung kommt auf **GitHub**, nicht auf Hugging Face (Entscheidung 11.11.2026): gepackt auf einen eigenen Branch dieses
Repos (`pii-modell`, `befehle-modell`, wird nie gemergt), an einem festen Commit. Die App lädt sie über
`raw.githubusercontent.com` und erkennt die Fassung auf dem Gerät am **SHA-256**, nicht an Name und Größe.

## Ablauf

1. Training und Export wie bisher (`tools/pii/README.md`, `tools/befehle/README.md`); `export.py` schreibt in den Ordner
   `…/onnx` die int8-Datei, `vocab.txt`, `labels.json`, `schwelle.json`, `MODELLKARTE.md` und `messung.json`.
2. Veröffentlichen – in einer Claude-Sitzung in der Cloud oder am PC (`tools/pii/pc.ps1` ruft das Skript selbst auf):

   ```bash
   python tools/modelle/veroeffentlichen.py befehle tools/befehle/modelle/<name>/onnx --bericht tools/befehle/modelle/<name>/bericht.json
   python tools/modelle/veroeffentlichen.py namensschutz tools/pii/modelle/gbert-base/onnx --bericht tools/pii/modelle/gbert-base/bericht.json
   ```

   - **Ziele verfehlt** (Prüfteil, int8): nichts gepackt, nichts veröffentlicht, nur der Bericht (Rückgabewert 2).
     Lora Namensschutz: ≥ 98 % gefunden, ≤ 2 % fälschlich ersetzt (wie geplant, AP-67). Lora Befehle: ≥ 90 % getroffen,
     ≤ 2 % falsch ausgeführt, ≥ 95 % der freien Anfragen weitergegeben, < 30 ms (AP-71).
   - **Ziele erreicht**: packen (gleiches Ergebnis bei gleicher Datei), über 95 MB in Teile < 95 MB (GitHub nimmt keine
     Datei über 100 MB), Branch mit Datei bzw. Teilen, `MODELLKARTE.md` (muss „von uns feinabgestimmt und verändert“
     sagen), `License.md`, Lizenz des Basismodells, `messbericht.json`, Wortschatz, Etiketten, Schwelle, `SHA256SUMS`,
     `README.md`; Push, danach Größe und SHA-256 im gepushten Commit und die Adresse auf `raw.githubusercontent.com`
     prüfen; `tools/<pii|befehle>/modell/fassung.json` schreiben (Commit, Größe, SHA-256, Teile mit Adressen, Schwelle,
     Messwerte, Ziele).
3. Derselbe Chat trägt die Fassung **oben** in `PiiModelInfo.VERSIONS` bzw. `CommandModelInfo.VERSIONS` ein (das Skript
   gibt den Eintrag aus; `COMMIT`, `URL`, `GZ_*`, `SIZE`, `SHA256`, `THRESHOLD` auf die neue Fassung) – die bisherigen
   bleiben in der Liste, damit wer sie hat, bis zum Download mit ihnen weiterarbeitet. Neue Etiketten bekommen eine eigene
   Datei in `app/src/main/resources/<pii|befehle>/`. Dazu den Text des Update-Popups (`PiiModelPrompt`,
   `CommandModelPrompt`) an die neue Fassung anpassen. PR, grüner Check, Release.
4. Die Apps bieten das Update beim nächsten Start einmal an („Nicht mehr fragen“ gilt je Fassung).

Optionen: `--branch` (z. B. ein Test-Branch), `--fassung "<Name>"`, `--nur-pruefen`, `--kein-push`. Nur
Standardbibliothek von Python; braucht git mit Schreibrecht auf das Repo.

**Probelauf (11.11.2026)**: eine Test-Fassung (3 MB Zufallsdaten, künstlich in 4 Teile zu 1 MB) aus der Cloud-Sitzung auf
den Branch `test-fassung-probe` gepusht; das Skript hat sie im Commit und online geprüft, `OwnModelsTest
› aPublishedVersionLoadsFromGitHub` (mit `FASSUNG_JSON=<fassung.json>`) hat sie mit dem Code der App von GitHub geladen,
zusammengesetzt und geprüft. Ziele verfehlt (falsch ausgeführt 2,8 %): nichts geschrieben.
