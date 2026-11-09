# Erkennungsmodell der Pseudonymisierung (AP-67) – nur die Modelldateien

Dieser Branch wird **nicht** gemergt. Er hält die trainierte Modelldatei, die für das Repo zu groß ist (110 MB, gepackt
72 MB). Code, Messkorpus, Vorlagen und Skripte liegen auf dem normalen Branch unter `tools/pii/`.

- `pii-modell.int8.onnx.gz` – gbert-base (MIT), feingetunt am 09.11.2026, ONNX int8; entpacken mit `gunzip`
- `vocab.txt`, `labels.json`, `schwelle.json`, `MODELLKARTE.md` – gehören dazu
- `SHA256SUMS` – Prüfsummen der gepackten und der entpackten Datei

Neue Fassungen kommen als neuer Commit auf diesen Branch.
