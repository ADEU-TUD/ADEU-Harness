multilingual-e5-small – Lora Edition

Wofür: Suche nach Bedeutung in der Wissenssammlung auf diesem Gerät (AP-107). Das Modell macht aus jedem Abschnitt und jeder Frage einen Vektor; ähnliche Bedeutung gibt ähnliche Vektoren. Nichts verlässt dabei das Gerät.

Was „Lora Edition“ heißt: von uns ausgewählt, gemessen und für Lora eingestellt (Abschnitte wie die Wissenssammlung, Mischung mit der Wortsuche). Die Gewichte sind unverändert, die Datei ist die des Originals. Keine Billigung durch die Urheber.

Basismodell: intfloat/multilingual-e5-small (Liang Wang u. a., Microsoft), Lizenz MIT. Datei: onnx/model_qint8_avx512_vnni.onnx (int8, 118 MB) und sentencepiece.bpe.model (5 MB) aus dem Original-Repository auf Hugging Face, fester Commit 614241f622f53c4eeff9890bdc4f31cfecc418b3, geprüft mit SHA-256.

Messung (erfundene Dokumente aus dem Alltag einer Professur, 30 Dokumente; Prüfteil mit 90 Fragen, nie zur Wahl benutzt): die gesuchte Stelle stand bei 98,9 % der Fragen unter den ersten fünf Treffern (nur Wörter: 83,3 %; Qwen3-Embedding-4B des Uni-Servers, wie die App es bis 1.0.66 mischte: 94,4 %). Bei einzelnen Absätzen statt Abschnitten: 91,1 % (Server-Modell bis 1.0.66: 90,0 %). Etwa 30–40 ms je Abschnitt mit zwei Rechenkernen.

Grenzen: Das Server-Modell (4 Mrd. Parameter) mit der neuen Mischung findet noch etwas mehr (100 %). Sehr kurze Fragen mit seltenen Fachwörtern findet die Wortsuche oft genauso gut; darum mischt die App beides. Gemessen auf Deutsch.
