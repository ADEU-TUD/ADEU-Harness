# Eigenes Erkennungsmodell für die Pseudonymisierung (AP-67)

Werkzeuge, um ein kleines deutsches Erkennungsmodell (NER: `PER`, `EINR`, `ORT`) für die Pseudonymisierung zu bauen.
Nicht Teil der App; läuft nur in der Cloud-Sitzung bzw. auf dem Trainings-PC. Plan und Stufen: `docs/ARBEITSPLAN.md`
› AP-67.

| Stufe | Was | Wo | Stand |
| --- | --- | --- | --- |
| a | Messkorpus (`messkorpus/`, `corpus.py`, `PseudonymCorpusTest`) | überall | ☑ |
| b | Trainingsdaten aus Vorlagen, erfundene Namen (`vorlagen/`, `namen.py`, `gen.py`) | überall | ☑ |
| c | Training (`train.py`, `modell.py`; Probelauf in der Cloud auf CPU) | PC mit RTX 5080 (`pc.ps1`) | ◐ |
| d | Export ONNX int8 + Modellkarte (`export.py`), Messung (`evaluate.py`) | PC | ◐ |

## Stufe a: Messkorpus

517 erfundene Beispiele aus neun Textarten (Interview, Elterngespräch, Gutachten, Mail, Protokoll, Tabelle, Chat,
Fachtext, Bericht), davon **174 im Prüfteil** (305 Sätze/Zeilen, 261 private und 121 öffentliche Angaben). Format und
Messung: [`messkorpus/README.md`](messkorpus/README.md).

```bash
python3 tools/pii/corpus.py check     # Format prüfen, zählen
./gradlew -p desktop test --tests 'io.github.thepytales.llmhub.PseudonymCorpusTest'   # Regeln messen → desktop/build/pii/regeln.txt
```

### Ausgangswert (09.11.2026, Regeln von 1.0.61)

| | Prüfteil | Entwicklung |
| --- | --- | --- |
| private Angaben gefunden (PER/EINR/ORT) | **78,2 %** (204/261) | 83,3 % (284/341) |
| · Personen | 83,9 % | 91,0 % |
| · Einrichtungen | 90,3 % | 76,9 % |
| · Orte mit Personenbezug | 16,0 % | 3,8 % |
| öffentliche Angaben fälschlich ersetzt | **9,9 %** (12/121) | 8,4 % (15/178) |
| · öffentliche Personen | 30,4 % | 24,3 % |
| · zitierte Autor:innen | 4,2 % | 6,1 % |
| sonstige Fehlalarme | 1,3 je 1000 Wörter | 0,3 je 1000 Wörter |

Typisch übersehen: Namen ohne Anrede und ohne bekannten Vornamen („Mykyta“, „Šimek“, „Hrytsenko“), kleingeschriebene
Namen im Chat, Aufzählungen von Nachnamen, fast alle Orte. Typisch fälschlich ersetzt: öffentliche Personen mit
bekanntem Vornamen („Käthe Kollwitz“, „Olaf Köller“), Buchtitel mit Vornamen („Emil und die Detektive“).

**Ziel AP-67** (Regeln + Modell, Prüfteil): mindestens 98 % gefunden, höchstens 2 % fälschlich ersetzt.

Mit „Gründlich“ (lokales Sprachmodell) misst der Test, wenn ein OpenAI-kompatibler Server läuft:

```bash
PII_GRUENDLICH_URL=http://localhost:8080/v1 PII_GRUENDLICH_MODELL=qwen3.5-2b ./gradlew -p desktop test --tests 'io.github.thepytales.llmhub.PseudonymCorpusTest'
```

## Stufe b: Trainingsdaten

Claude hat als Lehrer-Modell **985 Vorlagen** in zwölf Textarten geschrieben (`vorlagen/*.txt`), mit Platzhaltern statt
Namen: `{VN}` „Vorname Nachname“, `{Nw}` Nachname einer Frau, `{SCHULE}`, `{KITA}`, `{ORT}` (mit Personenbezug) – und
für Gegenbeispiele `{AUT}` (zitierte Autor:in), `{OEFF}` (öffentliche Person), `{OEINR}`, `{OORT}`, `{WERK}`. Alle
Platzhalter stehen am Anfang von `gen.py`. Der Generator setzt erfundene Namen ein (Listen in `namen.py`, die Listen der
App und erfundene sächsische Nachnamen) und schreibt die Fundstellen zeichengenau mit – die Beschriftung stimmt also
immer.

```bash
python3 tools/pii/gen.py check            # Platzhalter, Überschneidung mit dem Messkorpus, wörtliche Namen
python3 tools/pii/gen.py make -n 30000    # → tools/pii/daten/train.jsonl, dev.jsonl, beispiele.txt (nicht im Repo)
```

- Eine Zeile in `train.jsonl`: `{"text": …, "spans": [[start, ende, "PER"], …], "public": […], "textart": …}`.
- Etwa ein Zehntel der Vorlagen kommt nur in `dev.jsonl` vor (Entwicklung, ungesehene Satzmuster).
- **Sperre**: Kein Wort aus den Angaben des Messkorpus (beide Teile) wird als Name, Einrichtung oder Ort gezogen (704 Wörter), und
  `check` meldet jede Vorlage, die fünf Wörter am Stück mit dem Messkorpus teilt. Für das endgültige Modell, das in die
  App kommt, kann die Sperre mit `--ohne-sperre` fallen.
- Vorlagen mit Orten und Einrichtungen werden öfter gezogen (2,5-fach), weil diese Angaben seltener sind.

## Stufen c und d: Training und Export

**Auf dem PC mit der RTX 5080** genügt ein Befehl in PowerShell im Ordner des Repos:

```powershell
powershell -ExecutionPolicy Bypass -File tools\pii\pc.ps1
```

Das Skript richtet `tools\pii\.venv` ein (PyTorch mit CUDA 12.8 – nötig für RTX 50xx –, Transformers, ONNX), erzeugt
die Trainingstexte, trainiert drei Kandidaten je 4 Epochen, vergleicht sie auf dem **Entwicklungsteil** und exportiert
den besten. Kandidaten (Lizenz am 09.11.2026 auf Hugging Face geprüft, Versionen fest):

| Basismodell | Lizenz | Größe int8 | Bemerkung |
| --- | --- | --- | --- |
| `deepset/gbert-base` | MIT | ≈ 110 MB | deutsches BERT, 12 Schichten |
| `deepset/gelectra-base` | MIT | ≈ 110 MB | deutsches ELECTRA, gleicher Wortschatz |
| `distilbert/distilbert-base-german-cased` | Apache-2.0 | ≈ 65 MB | 6 Schichten, doppelt so schnell |

XLM-RoBERTa-base (MIT) ist nicht dabei: mit seinem großen Wortschatz wäre es als int8 etwa 280 MB groß. GLiNER
(Apache-2.0) ist als Spannen-Modell in Kotlin deutlich aufwendiger einzubauen.

Einzeln geht es auch so (überall, auch ohne Grafikkarte – dann langsam):

```bash
python tools/pii/train.py --basis deepset/gbert-base --epochen 4      # → tools/pii/modelle/gbert-base/best, bericht.json
python tools/pii/evaluate.py tools/pii/modelle/gbert-base/best --zeit  # Entwicklung: Zahlen und Fehlerliste
python tools/pii/export.py tools/pii/modelle/gbert-base/best          # → …/onnx/pii-modell.int8.onnx, vocab.txt, MODELLKARTE.md
```

Gemessen wird in drei Varianten: **Modell allein**, **alle Regeln + Modell** und **wie geplant** – die Regeln ohne das
Raten von Namen aus häufigen Vornamen (`Pseudonymizer.nameChains = false`; Muster, Frau/Herr, Rollen, Grüße,
Unterschriften, Tabellen und Einrichtungen bleiben), das Modell für Namen, Einrichtungen und Orte; im Text zitierte
Autor:innen und die bekannten Namen der App (`PiiDetector.FAMOUS`) werden nie ersetzt. Das Modell ersetzt erst ab einer
**Schwelle** (Wahrscheinlichkeit „kein O“), die `train.py` auf dem Entwicklungsteil wählt: die höchste, die dort noch
98 % findet, sonst der beste Ausgleich; sie liegt als `schwelle.json` beim Modell. Die Ergebnisse der Regeln je Beispiel schreibt `PseudonymCorpusTest` nach
`desktop/build/pii/regeln.jsonl`; eine Kopie liegt in `messkorpus/regeln.jsonl` (für PCs ohne Kotlin-Build).

**Fehlerlisten nur für den Entwicklungsteil.** Für den Prüfteil geben die Skripte nur Zahlen aus. (Beim allerersten
Probelauf am 09.11.2026 stand die Fehlerliste des Prüfteils einmal im Protokoll; seitdem nicht mehr. Vorlagen wurden nur
nach Fehlern im Entwicklungsteil ergänzt, `vorlagen/oeffentlich.txt`.)

### Probelauf in der Cloud (CPU, 09.11.2026)

`gbert-base`, nur 1000 Trainingstexte, eine Epoche (3 Minuten auf 4 CPU-Kernen):

| | Prüfteil gefunden | Prüfteil fälschlich ersetzt | Zeit je Absatz (CPU) |
| --- | --- | --- | --- |
| Regeln heute | 78,2 % | 9,9 % | – |
| Modell allein (int8) | 96,9 % | 9,9 % | 22 ms |
| wie geplant (int8) | 96,9 % | 8,3 % | – |

int8 verliert gegenüber fp32 auf dem Prüfteil 0,0 Prozentpunkte; die Datei ist 110 MB groß.

### Training in der Cloud (CPU, 09.11.2026)

`gbert-base`, 30 000 Texte aus 985 Vorlagen, 3 Epochen in Etappen (je ≈ 37 min auf 4 CPU-Kernen), Sperre der Wörter
aus beiden Teilen des Messkorpus (704), beste Epoche 3, Schwelle 0,9:

| | Prüfteil gefunden | Prüfteil fälschlich ersetzt | Entwicklung gefunden | Entwicklung fälschlich ersetzt |
| --- | --- | --- | --- | --- |
| Regeln heute | 78,2 % | 9,9 % | 83,3 % | 8,4 % |
| Modell allein (int8) | 99,2 % | 5,8 % | 97,1 % | 1,7 % |
| **wie geplant** (int8) | **99,2 %** | **5,8 %** | 97,1 % | 2,2 % |
| alle Regeln + Modell (int8) | 99,2 % | 14,9 % | 97,9 % | 9,6 % |

int8: 110 MB, 0,0 Prozentpunkte Verlust gegenüber fp32, **7,7 ms je Absatz** (Median, CPU, 4 Kerne; 95 % unter 12 ms).

**Vergleich auf dem Entwicklungsteil** (wie geplant, nach dem dort die Wahl fällt): `gbert-base` 97,4 % gefunden, 1,7 %
fälschlich ersetzt; `distilbert-base-german-cased` (3 Epochen, je ≈ 18 min) 96,8 % und 2,8 %. **Gewählt: gbert-base** –
distilbert wäre halb so groß und doppelt so schnell, aber gbert ist mit 7,7 ms je Absatz schnell genug.
Das Ziel „98 % gefunden“ ist erreicht, „höchstens 2 % fälschlich ersetzt“ noch nicht: Im Prüfteil sind es vor allem
öffentliche Personen, die das Modell nie gesehen hat. Ein erster Lauf mit nur einer Epoche und ohne Sperre des
Entwicklungsteils zeigte, warum die Sperre beider Teile nötig ist: Fehlalarme 2,2 % im Entwicklungsteil, aber 7,4 % im
Prüfteil – die öffentlichen Namen des Entwicklungsteils kannte das Modell aus dem Training.

## Grundsätze

- **Nie echte Daten**: keine echten Interviews, Abgaben, Mails, Namenslisten aus der Schule – weder im Messkorpus noch
  im Training.
- Der **Prüfteil** wird nie zum Training, zur Auswahl von Einstellungen oder als Vorbild für Vorlagen benutzt.
- Lehrer-Modell für die Trainingstexte ist Claude in der Cloud-Sitzung (Entscheidung 09.11.2026: kein Schlüssel des
  Uni-Servers); die Texte sind erfunden.
