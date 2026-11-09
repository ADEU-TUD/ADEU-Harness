# Modellkarte: Erkennungsmodell der Pseudonymisierung (AP-67)

Stand: 09.11.2026

## Was es tut
Findet in deutschen Texten private **Personen** (`PER`), **Einrichtungen** wie Schulen, Kitas, Praxen (`EINR`) und
**Orte mit Personenbezug** (`ORT`), damit die App sie vor dem Senden durch Platzhalter ersetzt. Feste Muster (Mail,
Telefon, IBAN, Matrikelnummer …) und die eigene Liste bleiben bei den Regeln von `PiiDetector`.

## Herkunft
- **Basismodell**: `deepset/gbert-base` (Version `d50cb1df9660ff2de1af8a5362d322b3d5a1a28a`), Lizenz **MIT**.
- **Feintuning**: Token-Klassifikation (BIO), 27000 erfundene Trainingstexte, beste Epoche
  3 von 3.
- **Daten**: ausschließlich erfundene Texte. Claude (Anthropic) hat als Lehrer-Modell Vorlagen mit Platzhaltern
  geschrieben (`tools/pii/vorlagen`), ein Skript hat erfundene Namen, Einrichtungen und Orte eingesetzt
  (`tools/pii/gen.py`). Keine echten Interviews, Abgaben, Mails oder Schülerdaten.
- **Format**: ONNX, int8 (dynamische Quantisierung), 110 MB; Tokenizer `vocab.txt`/`tokenizer.json`.

## Kennzahlen (Messkorpus `tools/pii/messkorpus`, int8)
| | Entwicklung | Prüfteil |
| --- | --- | --- |
| Modell allein | 97.1 % gefunden, 1.7 % fälschlich ersetzt | 99.2 % gefunden, 5.8 % fälschlich ersetzt |
| wie in der App geplant (Regeln ohne Vornamen-Raten + Modell) | 97.1 % gefunden, 2.2 % fälschlich ersetzt | 99.2 % gefunden, 5.8 % fälschlich ersetzt |

Schwelle 0.9 (auf dem Entwicklungsteil gewählt, `schwelle.json`). Zeit je Absatz (CPU, ONNX
Runtime, 4 Kerne): Median 7.7 ms, 95 % unter 12.2 ms.
In der App gemessen (`PiiModelTest`, Regeln + Modell über die ONNX Runtime der App): Prüfteil 99,2 % gefunden,
7,4 % fälschlich ersetzt; Entwicklung 97,1 % und 2,2 %; etwa 11 ms je Absatz.
Ziel AP-67 (Prüfteil, wie geplant): mindestens 98 % gefunden, höchstens 2 % fälschlich ersetzt – das zweite ist noch
nicht erreicht (vor allem öffentliche Personen, die das Modell nicht kennt).

## Grenzen
- Kennt nur, was in den Vorlagen vorkommt: Deutsch, Schul- und Hochschulalltag. Andere Sprachen und Fachgebiete sind
  nicht geprüft.
- Öffentliche Personen erkennt es am Zusammenhang (Amt, Werk, Zitat), nicht an einem Weltwissen; unbekannte
  öffentliche Namen ohne solchen Zusammenhang werden eher ersetzt (im Zweifel für den Datenschutz).
- Indirekte Merkmale („die Schulleiterin der einzigen Schule im Ort“) erkennt es nicht.
- Ein Mensch prüft im Zweifel die Vorschau; „nicht ersetzen“/„auch ersetzen“ gehen vor.

## Lizenz des Basismodells (deepset/gbert-base, MIT)

Copyright 2022 deepset GmbH

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
