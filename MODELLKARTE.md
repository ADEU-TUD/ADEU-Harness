# Modellkarte: Lora Namensschutz (Erkennungsmodell der Pseudonymisierung, AP-67)

Stand: 10.11.2026 · Datei `lora-namensschutz-pytales.int8.onnx` (bis 1.0.65: „Erkennungsmodell“, `pii-modell.int8.onnx`)

## Was es tut
Findet in deutschen Texten private **Personen** (`PER`), **Einrichtungen** wie Schulen, Kitas, Praxen (`EINR`) und
**Orte mit Personenbezug** (`ORT`), damit die App sie vor dem Senden durch Platzhalter ersetzt. Feste Muster (Mail,
Telefon, IBAN, Matrikelnummer …) und die eigene Liste bleiben bei den Regeln von `PiiDetector`.

## Herkunft
- **Basismodell**: `deepset/gbert-base` (Version `d50cb1df9660ff2de1af8a5362d322b3d5a1a28a`) von deepset GmbH (Branden Chan, Stefan Schweter, Timo Möller), Lizenz **MIT**.
  Lora Namensschutz ist eine abgeleitete Arbeit: Das Basismodell wurde **von uns feinabgestimmt und verändert**. Lora Namensschutz ist kein
  Produkt der Urheber des Basismodells; sie haben es weder geprüft noch gebilligt.
- **Lizenz von Lora Namensschutz**: Bedingungen von PyTales in `License.md` im Repository ADEU-TUD/ADEU-Harness (kostenlos nutzbar;
  Änderungen und Weiterverbreitung nur mit Genehmigung). Das Basismodell bleibt unter seiner eigenen Lizenz (unten).
- **Feintuning**: Token-Klassifikation (BIO), 32400 erfundene Trainingstexte, beste Epoche
  3 von 3.
- **Daten**: ausschließlich erfundene Texte. Claude (Anthropic) hat als Lehrer-Modell Vorlagen mit Platzhaltern
  geschrieben (`tools/pii/vorlagen`), ein Skript hat erfundene Namen, Einrichtungen und Orte eingesetzt
  (`tools/pii/gen.py`). Keine echten Interviews, Abgaben, Mails oder Schülerdaten.
- **Format**: ONNX, int8 (dynamische Quantisierung pro Kanal), 111 MB; Tokenizer `vocab.txt`/`tokenizer.json`.

## Kennzahlen (Messkorpus `tools/pii/messkorpus`, int8)
| | Entwicklung | Prüfteil |
| --- | --- | --- |
| Modell allein | 95.9 % gefunden, 1.1 % fälschlich ersetzt | 98.9 % gefunden, 2.5 % fälschlich ersetzt |
| wie in der App geplant (Regeln ohne Vornamen-Raten + Modell) | 95.9 % gefunden, 1.7 % fälschlich ersetzt | 98.9 % gefunden, 3.3 % fälschlich ersetzt |

Schwelle 0.93 (auf dem Entwicklungsteil gewählt, `schwelle.json`). Zeit je Absatz (CPU, ONNX
Runtime, 4 Kerne): Median 8.4 ms, 95 % unter 13.7 ms.
Ziel AP-67 (Prüfteil, wie geplant): mindestens 98 % gefunden, höchstens 2 % fälschlich ersetzt.

## Grenzen
- Kennt nur, was in den Vorlagen vorkommt: Deutsch, Schul- und Hochschulalltag. Andere Sprachen und Fachgebiete sind
  nicht geprüft.
- Öffentliche Personen erkennt es am Zusammenhang (Amt, Werk, Zitat), nicht an einem Weltwissen; unbekannte
  öffentliche Namen ohne solchen Zusammenhang werden eher ersetzt (im Zweifel für den Datenschutz).
- Indirekte Merkmale („die Schulleiterin der einzigen Schule im Ort“) erkennt es nicht.
- Ein Mensch prüft im Zweifel die Vorschau; „nicht ersetzen“/„auch ersetzen“ gehen vor.

## Lizenz des Basismodells (deepset/gbert-base, MIT)

gbert-base (Hugging Face: deepset/gbert-base, Version d50cb1df9660ff2de1af8a5362d322b3d5a1a28a)
Urheber: deepset GmbH (Branden Chan, Stefan Schweter, Timo Möller). Lizenz: MIT.

Lora Namensschutz ist eine abgeleitete Arbeit: Das Basismodell wurde von uns feinabgestimmt und verändert (Feintuning
auf erfundenen Texten mit tools/pii, eigener Kopf für die Token-Klassifikation, Export als ONNX int8). Lora Namensschutz
ist kein Produkt von deepset; die Urheber des Basismodells haben es weder geprüft noch gebilligt.

MIT License

Copyright 2022 deepset GmbH

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
