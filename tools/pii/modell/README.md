# Lora Namensschutz – das trainierte Erkennungsmodell (AP-67)

Was hier liegt, gehört zum Modell `lora-namensschutz-pytales.int8.onnx` (gbert-base, Nachtraining 10.11.2026, 3 Epochen;
bis 1.0.65 hieß es „Erkennungsmodell“, Datei `pii-modell.int8.onnx`):

- `MODELLKARTE.md` – Herkunft, Lizenz, Kennzahlen, Grenzen
- `vocab.txt`, `labels.json`, `schwelle.json` – was die App neben dem Modell braucht (WordPiece-Wortschatz, Etiketten,
  Schwelle; Wortschatz und Etiketten sind dieselben wie beim Modell bis 1.0.65)
- `bericht-lora-namensschutz.json` (10.11.2026), `bericht-gbert-base.json`, `bericht-distilbert.json` (09.11.2026) – Verlauf
  und Zahlen der Trainingsläufe (Fehlerlisten nur für den Entwicklungsteil)
- `LIZENZ-gbert-base.txt` – Urheber und MIT-Lizenztext des Basismodells (deepset GmbH), von uns feinabgestimmt und verändert
- `lora-namensschutz-pytales.int8.onnx.sha256` – Prüfsumme der Modelldatei

Die Modelldatei selbst (111 MB) ist nicht auf diesem Branch – GitHub nimmt keine Dateien über 100 MB. Sie liegt gepackt
(93 MB) auf dem eigenen Branch **`pii-modell`** (Commit `43ad93492aaa3e89742bccd6994d04eef9b89bb4`, wird nicht gemergt),
zusammen mit Modellkarte, Lizenztext und `License.md`; die App lädt sie von dort (Offene Frage 9, entschieden 09.11.2026):
`https://raw.githubusercontent.com/ADEU-TUD/ADEU-Harness/43ad93492aaa3e89742bccd6994d04eef9b89bb4/lora-namensschutz-pytales.int8.onnx.gz`
(SHA-256 gepackt `c3d6d9bb3f1fe74a4bdc9488ce4f4895f657d5b1a2de97199bbbfb8d53d1a1fc`). Die Datei bis 1.0.65 bleibt am Commit
`806e33ca70948b03030a149f5f0255d145e69a5e` erreichbar; wer sie hat, arbeitet mit ihr weiter (Schwelle 0,9) und bekommt
Lora Namensschutz einmal als Update angeboten. Neu erzeugen lässt sich das Modell jederzeit mit `gen.py make -n 36000`,
`train.py --epochen 3` und `export.py` (siehe `../README.md`). Für Lora Namensschutz gilt `License.md`.
