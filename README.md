# Lora Befehle (Befehlsmodell für Lora, AP-71)

Dieser Branch enthält nur die Modelldatei von **Lora Befehle** – er wird **nicht gemergt**. Die App lädt
`lora-befehle-pytales.int8.onnx.gz` von hier an einem festen Commit und prüft sie mit SHA-256
(`data/CommandModel.kt` › `CommandModelInfo`). Bis 1.0.65 hieß das Modell „Befehlsmodell“ (`befehle-modell.int8.onnx.gz`,
Commit `cc168774477e3ac8b439b42fd7b03ab05c9908fa`); Apps mit der alten Datei bekommen die neue als Update angeboten.

- gepackt 54 943 807 Bytes, SHA-256 `99480c32e19c5a9f5a52a6b2da097e38ac5991b4f7961fb3820328794d7a883c`
- entpackt 68 169 161 Bytes, SHA-256 `f445c6b49e67f81177f6b0cb60f8e950ec24bcd8fbe6b367cd69ae9beba231b9`
- Basismodell `distilbert/distilbert-base-german-cased` von Hugging Face (Apache-2.0, Lizenztext in
  `LIZENZ-distilbert-base-german-cased.txt`), von uns feinabgestimmt und verändert (Feintuning auf erfundenen Wünschen).
  Die Urheber des Basismodells haben Lora Befehle weder geprüft noch gebilligt.
- Lizenz von Lora Befehle: `License.md` (PyTales)
- Herkunft, Kennzahlen und Grenzen: `MODELLKARTE.md`; Werkzeuge und Messkorpus: `tools/befehle/` auf `main`
