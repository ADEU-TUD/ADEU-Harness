# Das trainierte Erkennungsmodell (AP-67)

Was hier liegt, gehört zum Modell `pii-modell.int8.onnx` (gbert-base, 3 Epochen, 09.11.2026):

- `MODELLKARTE.md` – Herkunft, Lizenz, Kennzahlen, Grenzen
- `vocab.txt`, `labels.json`, `schwelle.json` – was die App neben dem Modell braucht (WordPiece-Wortschatz, Etiketten,
  Schwelle)
- `bericht-gbert-base.json`, `bericht-distilbert.json` – Verlauf und Zahlen der Trainingsläufe (Fehlerlisten nur für den
  Entwicklungsteil)
- `pii-modell.int8.onnx.sha256` – Prüfsumme der Modelldatei

Die Modelldatei selbst (110 MB) ist **nicht** im Repo – GitHub nimmt keine Dateien über 100 MB. Wo sie dauerhaft liegt,
ist Offene Frage 9 im Arbeitsplan. Neu erzeugen lässt sie sich jederzeit mit `gen.py make`, `train.py --epochen 3` und
`export.py` (siehe `../README.md`).
