# Modellkarte: Befehlsmodell für Lora (AP-71)

Stand: 10.11.2026

## Was es tut
Ordnet einen kurzen Wunsch an Lora oder Strg+K einer von 178 Aktionen der App zu – Bereich oder Werkzeug öffnen,
Modus starten, Einstellung zeigen oder umstellen, TU Vorlage, Aktion im offenen Werkzeug, Tour, frühere Chats, Frist –
oder erkennt eine **freie Anfrage** (einfach bzw. komplex), die wie bisher an ein Sprachmodell geht. Es läuft nur auf dem
Gerät. Werte (an/aus, Seiten, Spalte, Dateiname …) liest die App danach mit Regeln aus dem Wunsch.
Ist die Sicherheit kleiner als die Schwelle, führt die App nichts aus, sondern gibt den Wunsch weiter.

## Herkunft
- **Basismodell**: `distilbert/distilbert-base-german-cased` (Version `neueste`), Lizenz **Apache-2.0**.
- **Feintuning**: Satz-Klassifikation, 26360 erfundene Trainingswünsche, beste Epoche
  4 von 5. Eingabe: was offen ist (ein Wort) und der Wunsch in Kleinbuchstaben.
- **Etiketten**: aus dem Code der App erzeugt (`tools/befehle/etiketten.json`, `CommandLabelsTest`).
- **Daten**: ausschließlich erfundene Wünsche. Claude (Anthropic) hat als Lehrer-Modell Bezeichnungen, Satzmuster und
  Sätze je Etikett geschrieben (`tools/befehle/vorlagen`), ein Skript hat sie kombiniert und Tippfehler, fehlende
  Umlaute und Füllwörter ergänzt (`tools/befehle/gen.py`). Keine echten Eingaben von Nutzer:innen.
- **Format**: ONNX, int8 (dynamische Quantisierung), 68 MB; Tokenizer `vocab.txt` (WordPiece).

## Kennzahlen (Messkorpus `tools/befehle/messkorpus`, int8, Schwelle 0.8)
| | Befehle getroffen | falsch ausgeführt (alle Eingaben) | freie Anfragen weitergegeben | Art einfach/komplex richtig |
| --- | --- | --- | --- | --- |
| Entwicklung | 89.8 % | 0.4 % | 100.0 % | 98.7 % |
| Prüfteil | 84.8 % | 0.0 % | 100.0 % | 100.0 % |

Zeit je Wunsch (CPU, ONNX Runtime, 2 Kerne): Median 3.5 ms, 95 % unter 5.1 ms.
Ziel AP-71 (Prüfteil): mindestens 90 % getroffen, höchstens 2 % falsch ausgeführt, mindestens 95 % der freien Anfragen
weitergegeben. Die Schwelle wurde auf dem Entwicklungsteil gewählt (`schwelle.json`).

## Grenzen
- Kennt nur die Aktionen, die es beim Training gab. Neue Bereiche, Optionen und Aktionen der App gehen bis zum nächsten
  Training an das Sprachmodell (`CommandLabelsTest` meldet sie).
- Deutsch und einfaches Englisch, kurze Wünsche. Lange oder verschachtelte Aufträge („erst …, dann …“) sind freie Anfragen.
- Welche TU Vorlage, welcher Chat, welche Datei gemeint ist, entscheidet nicht das Modell, sondern die Suche der App.
- Riskante Aktionen (löschen, verschieben, überschreiben) zeigen weiter zuerst eine Vorschau; Einstellungen ändert die
  App erst nach „Ja“.

## Lizenz des Basismodells (distilbert/distilbert-base-german-cased, Apache-2.0)

distilbert-base-german-cased (Hugging Face: distilbert/distilbert-base-german-cased) steht unter der Apache License, Version 2.0.

Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the
License. You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific
language governing permissions and limitations under the License.

Das Befehlsmodell ist eine abgeleitete Arbeit: Feintuning auf erfundenen Wünschen (tools/befehle), als ONNX int8 exportiert.
