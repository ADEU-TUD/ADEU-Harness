# Lora Namensschutz (Erkennungsmodell der Pseudonymisierung, AP-67) – nur die Modelldateien

Dieser Branch wird **nicht** gemergt. Er hält die trainierte Modelldatei, die für das Repo zu groß ist (111 MB, gepackt
93 MB). Code, Messkorpus, Vorlagen und Skripte liegen auf `main` unter `tools/pii/`. Bis 1.0.65 hieß das Modell
„Erkennungsmodell“ (`pii-modell.int8.onnx.gz`, Commit `806e33ca70948b03030a149f5f0255d145e69a5e`); Apps mit der alten
Datei bekommen die neue als Update angeboten.

- `lora-namensschutz-pytales.int8.onnx.gz` – Nachtraining vom 10.11.2026, ONNX int8 (pro Kanal); entpacken mit `gunzip`
- Basismodell `deepset/gbert-base` von deepset GmbH (MIT, Lizenztext in `LIZENZ-gbert-base.txt`), von uns feinabgestimmt
  und verändert. Die Urheber des Basismodells haben Lora Namensschutz weder geprüft noch gebilligt.
- Lizenz von Lora Namensschutz: `License.md` (PyTales)
- `vocab.txt`, `labels.json`, `schwelle.json`, `MODELLKARTE.md` – gehören dazu
- `SHA256SUMS` – Prüfsummen der gepackten und der entpackten Datei

Neue Fassungen kommen als neuer Commit auf diesen Branch.
