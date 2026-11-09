# Das trainierte Erkennungsmodell (AP-67)

Was hier liegt, gehört zum Modell `pii-modell.int8.onnx` (gbert-base, 3 Epochen, 09.11.2026):

- `MODELLKARTE.md` – Herkunft, Lizenz, Kennzahlen, Grenzen
- `vocab.txt`, `labels.json`, `schwelle.json` – was die App neben dem Modell braucht (WordPiece-Wortschatz, Etiketten,
  Schwelle)
- `bericht-gbert-base.json`, `bericht-distilbert.json` – Verlauf und Zahlen der Trainingsläufe (Fehlerlisten nur für den
  Entwicklungsteil)
- `pii-modell.int8.onnx.sha256` – Prüfsumme der Modelldatei

Die Modelldatei selbst (110 MB) ist nicht auf diesem Branch – GitHub nimmt keine Dateien über 100 MB. Sie liegt gepackt
(72 MB) auf dem eigenen Branch **`pii-modell`** (Commit `806e33ca70948b03030a149f5f0255d145e69a5e`, wird nicht gemergt);
die App lädt sie von dort (Offene Frage 9, entschieden 09.11.2026):
`https://raw.githubusercontent.com/ADEU-TUD/ADEU-Harness/806e33ca70948b03030a149f5f0255d145e69a5e/pii-modell.int8.onnx.gz`
(SHA-256 gepackt `afd48b91fee421875325c06862c29806a417da2dfee3acd1aa631d74247ea4b3`). Neu erzeugen lässt sie sich jederzeit mit `gen.py make`, `train.py --epochen 3` und
`export.py` (siehe `../README.md`).
