# Lora Befehle – nur die Modelldateien

Dieser Branch wird **nicht** gemergt. Er hält die trainierte Modelldatei, die für das Repo zu groß ist. Code, Messkorpus
und Skripte liegen auf `main` unter `tools/`. Jede Fassung ist ein eigener Commit; die App lädt sie an genau diesem
Commit und prüft sie mit SHA-256 (AP-117). Ältere Fassungen bleiben an ihren Commits erreichbar.

- `lora-befehle-pytales.int8.onnx.gz.001` … `lora-befehle-pytales.int8.onnx.gz.004` – Test-Fassung 11.11.2026, ONNX int8, in 4 Teilen (GitHub nimmt keine Datei über 100 MB); zusammensetzen und entpacken: `cat lora-befehle-pytales.int8.onnx.gz.0* | gunzip > lora-befehle-pytales.int8.onnx`
- Basismodell `distilbert/distilbert-base-german-cased` (Apache-2.0), von uns feinabgestimmt und verändert. Die Urheber des
  Basismodells haben Lora Befehle weder geprüft noch gebilligt.
- Lizenz von Lora Befehle: `License.md` (PyTales)
- `MODELLKARTE.md`, `messbericht.json`, `vocab.txt`, `labels.json`, `schwelle.json` – gehören dazu
- `SHA256SUMS` – Prüfsummen der Teile, der gepackten und der entpackten Datei

Veröffentlicht mit `tools/modelle/veroeffentlichen.py`.
