# Befehlsmodell für Lora (AP-71)

Dieser Branch enthält nur die Modelldatei des Befehlsmodells – er wird **nicht gemergt**. Die App lädt
`befehle-modell.int8.onnx.gz` von hier an einem festen Commit und prüft sie mit SHA-256
(`data/CommandModel.kt` › `CommandModelInfo`).

- gepackt 40 900 860 Bytes, SHA-256 `ee13766aeeda7983470cdc3ff0da6989de28bc20091d3f3b329d6d9242883e37`
- entpackt 67 956 881 Bytes, SHA-256 `3251a9ef4dd4104164bf861c0ac0d6778f0d8ef1c5dc8c286e02e017ab803f65`
- Basismodell `distilbert/distilbert-base-german-cased` (Apache-2.0), Feintuning auf erfundenen Wünschen
- Herkunft, Kennzahlen und Grenzen: `MODELLKARTE.md`; Werkzeuge und Messkorpus: `tools/befehle/` auf `main`
