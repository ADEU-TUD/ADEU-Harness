# Lora Befehle – das Befehlsmodell für Lora (AP-71)

Werkzeuge, um **Lora Befehle** (bis 1.0.65 „Befehlsmodell“) zu bauen, das kleine Modell, das Eingaben in Lora und Strg+K **auf dem Gerät** einer Aktion der App zuordnet
– oder als freie Anfrage erkennt, die an ein Sprachmodell geht. Nicht Teil der App; läuft in der Cloud-Sitzung bzw. auf
einem Trainings-PC. Plan und Stufen: `docs/ARBEITSPLAN.md` › AP-71. Der Aufbau folgt `tools/pii/` (AP-67); Tokenizer-Laden
und die Batches nach Länge kommen von dort.

| Stufe | Was | Stand |
| --- | --- | --- |
| a | Etiketten aus dem Code (`etiketten.json`, `CommandLabelsTest`) | ☑ 178 Etiketten |
| b | Messkorpus (`messkorpus/`, `corpus.py`, `CommandCorpusTest`) | ☑ 844 Wünsche, Prüfteil 386 |
| c | Trainingsdaten (`vorlagen/`, `gen.py`) | ☑ 3 132 Zeilen, 33 640 Wünsche (zweite Runde 10.11.2026) |
| d | Entscheidung: eigenes kleines Modell statt zweiter Kopf | ☑ distilbert-base-german-cased |
| e | Training und Export (`train.py`, `modell.py`, `evaluate.py`, `export.py`) | ☑ ONNX int8 pro Kanal, 68 MB (gepackt 55 MB) |
| f–h | Einbau, Mischmodus, Tests (App) | ☑, auf Android getestet |
| AP-73 | Rückfrage mit zwei Knöpfen (`rueckfrage.py`, `modell/rueckfrage.json`) | ☑ Untergrenze 0,45 (vorher 0,3) |
| AP-72 | Korrekturen aus der App als Trainingsdaten (Export unter *Einstellungen › Lora*) | ☑ |

## Etiketten (Stufe a)

Die Etiketten sind die Aktionen der App und kommen aus ihren Registern: `ort:<id>` (jeder Ort der `AppMap` – Bereiche,
Einstellungsseiten, Optionen, Knöpfe, Modi, Werkzeuge; deckt die Strg+K-Einträge ab), `stell:<id>`
(`LoraAgent.SETTINGS`), `tu:<id>` (Aktionen der Werkzeuge, deren Werte die App aus dem Wunsch lesen kann),
`lora:<id>` (Tour, frühere Chats, Frist, Hilfe, Merken, Postfach), `vorlage` sowie `frei.einfach` und `frei.komplex`.
`CommandLabelsTest` schreibt sie nach `desktop/build/befehle/etiketten.json`; die Kopie hier ist die Grundlage für Korpus
und Training. Kommt im Code etwas dazu, meldet `CommandModelTest` es – bis zum nächsten Training geht es an das
Sprachmodell.

## Messkorpus (Stufe b)

Format, Kennzahlen und Regeln: [`messkorpus/README.md`](messkorpus/README.md).

```bash
python3 tools/befehle/corpus.py check
./gradlew -p desktop test --tests 'io.github.thepytales.llmhub.CommandCorpusTest'   # Regeln messen → desktop/build/befehle/regeln.txt
```

### Ausgangswert (10.11.2026, Regeln von 1.0.64: `LoraAgent.local`, ohne Modell)

| | Prüfteil | Entwicklung |
| --- | --- | --- |
| Befehle getroffen | 38,1 % | 35,4 % |
| falsch ausgeführt (alle Eingaben) | **19,4 %** | 15,1 % |
| freie Anfragen weitergegeben | 86,9 % | 91,5 % |

(Die Regeln kennen weder den offenen Bereich noch die Aktionen der Werkzeuge; das zählt hier mit.)

## Trainingsdaten (Stufe c)

Claude hat als Lehrer-Modell je Etikett Bezeichnungen (`o:`), ganze Sätze (`s:`) und Sätze je Wert (`v:`) geschrieben,
dazu Satzmuster je Art (`vorlagen/traeger.txt`) und Füllungen für Platzhalter (`vorlagen/listen.txt`). `gen.py`
kombiniert sie und ergänzt, was Menschen wirklich tippen: Tippfehler, fehlende Umlaute, Füllwörter, Satzzeichen.

```bash
python3 tools/befehle/gen.py check                       # Etiketten, Platzhalter, Überschneidung mit dem Messkorpus
python3 tools/befehle/gen.py make -n 110 --frei 3500     # → tools/befehle/daten/ (nicht im Repo)
```

- Eine Zeile: `{"text": …, "bereich": "pdf", "label": "tu:pdf.merge"}`; der Text ist klein geschrieben – die App macht
  dasselbe, bevor sie das Modell fragt.
- **Sperre**: Ein Wunsch, der (ohne Großschreibung, Akzente, Satzzeichen) einem Wunsch des Messkorpus gleicht, wird nie
  erzeugt; `check` meldet Sätze, die einen Wunsch des Messkorpus enthalten. Kurze Befehle ähneln sich von Natur aus
  („wo ist die option für …“) – gleiche Wörter sind kein Fehler, gleiche Sätze schon.
- Nach dem ersten Lauf wurden Formulierungen **nur anhand der Fehler im Entwicklungsteil** ergänzt. Der Entwicklungsteil
  sieht seitdem besser aus als ungesehene Wünsche; maßgeblich ist der Prüfteil.
- **Zweite Runde (10.11.2026)**: `vorlagen/nachtrag.txt` und in `gen.py` weitere Grüße (`PREFIX`) und gesprochene Formen
  (`COLLOQUIAL`: „wo is“, „ich hab“, „ich brauch“ …) – nur nach den Arten der Fehler im Entwicklungsteil des ersten Modells
  (`modell/bericht-distilbert.json` › `fehler_entwicklung`): Wert ohne Verb vorn, Umgangssprache und Dialekt, Englisch,
  Bedarf statt Ziel („ich muss was mit einem pdf machen“), knappe Stichwörter. Den Prüfteil hat dafür niemand angesehen.
- Keine echten Eingaben von Nutzer:innen, keine echten Namen.

## Ein Modell mit zwei Köpfen oder ein eigenes? (Stufe d)

Geprüft am 10.11.2026 auf denselben Daten (26 360 Wünsche, 3 Epochen, Entwicklungsteil):

| Variante | Befehle getroffen | falsch ausgeführt | Bemerkung |
| --- | --- | --- | --- |
| **distilbert-base-german-cased**, voll feinabgestimmt | 86,0 % | 1,5 % | eigener Download, 41 MB gepackt |
| gbert-base, Encoder **eingefroren**, nur Satz-Kopf | 7,1 % | 0,4 % | der einzige Weg, das Modell von AP-67 unverändert mitzunutzen |

**Entscheidung: ein eigenes kleines Modell.** Ein zweiter Kopf auf dem unveränderten Encoder der Pseudonymisierung lernt
die 178 Befehle nicht (7 % gegenüber 86 %). Ein gemeinsames Modell wäre nur mit gemeinsamem Training beider Köpfe
möglich – dann müsste jedes Nachtraining der Befehle das Erkennungsmodell von AP-67 neu ausliefern und neu abnehmen, und
AP-67 ist selbst noch nicht abgeschlossen. Dazu ist distilbert halb so groß und doppelt so schnell (3,5 ms je Eingabe),
was für Strg+K bei jedem Tastendruck zählt, und beide Modelle bleiben einzeln ladbar. Nicht gemessen: gbert-base voll
feinabgestimmt (der Lauf wurde aus Zeitgründen abgebrochen); er hätte die Entscheidung nicht geändert, weil er dieselbe
Kopplung an AP-67 bräuchte. Lizenz: distilbert-base-german-cased steht unter Apache-2.0 (am 10.11.2026 auf Hugging Face
geprüft, Version `6f96562de9886779ce7d48810dff8f5c4afb6d0b`).

## Training und Export (Stufe e)

```bash
python tools/befehle/train.py --basis distilbert/distilbert-base-german-cased --epochen 5 --nur 1 --fortsetzen   # je Aufruf eine Epoche
python tools/befehle/evaluate.py tools/befehle/modelle/<name>/best --zeit                                       # Entwicklung: Zahlen und Fehlerliste
python tools/befehle/export.py tools/befehle/modelle/<name>/best                                               # → …/onnx/
```

In der Cloud auf 4 CPU-Kernen: etwa 9–12 Minuten je Epoche. Die Eingabe ist ein Paar aus dem offenen Bereich (ein Wort)
und dem Wunsch: `[CLS] Tabelle [SEP] sortiere nach nachname [SEP]`. Die **Schwelle** wählt `modell.tune_threshold` auf
dem Entwicklungsteil: die niedrigste, die dort höchstens 0,5 % falsch ausführt – für die ausgelieferte int8-Datei
eigens, weil die Quantisierung die Sicherheiten leicht verschiebt. Seit dem 10.11.2026 quantisiert `export.py`
**pro Kanal** (`per_channel=True`): Die einfache dynamische Quantisierung verlor im Entwicklungsteil 1,7 Punkte Treffer, pro
Kanal keinen; verglichen wurden fünf Varianten nur auf dem Entwicklungsteil (Größe bleibt 68 MB).

### Ergebnis der zweiten Runde (10.11.2026): Lora Befehle

33 640 Wünsche, 5 Epochen in einem Lauf (63 min auf 4 CPU-Kernen), beste Epoche 4, Schwelle der int8-Datei **0,5**
(Entwicklungsteil). Der Prüfteil wurde einmal am Ende des Trainings (nur Zahlen) und einmal beim Export gemessen.

| Prüfteil | Befehle getroffen | **falsch ausgeführt** | freie Anfragen weitergegeben | Zeit je Eingabe |
| --- | --- | --- | --- | --- |
| Befehlsmodell bis 1.0.65 (int8, Schwelle 0,8) | 84,8 % | 0,0 % | 100 % | 3,5 ms |
| Lora Befehle fp32, Schwelle 0,75 | 92,7 % | 0,8 % | 98,8 % | 14 ms |
| **Lora Befehle int8 pro Kanal, Schwelle 0,5** | **95,4 %** | **1,3 %** | **98,8 %** | 3,8 ms |
| Ziel AP-71 | ≥ 90 % | ≤ 2 % | ≥ 95 % | < 30 ms |

Entwicklungsteil (int8): 98,9 % / 0,4 % / 98,9 % – nach der Nachschärfung erwartungsgemäß besser als der Prüfteil.
Gegenüber 1.0.65 trifft das Modell zehn Punkte mehr Befehle selbst und führt dafür 5 von 386 Eingaben falsch aus (vorher
keine) – innerhalb des Ziels von höchstens 2 %. In der App: siehe unten (`CommandModelTest`).

### Ergebnis der ersten Runde (10.11.2026, bis 1.0.65)

5 Epochen, beste Epoche 4:

| | Befehle getroffen | weitergegeben | **falsch ausgeführt** | freie Anfragen weitergegeben | Zeit je Eingabe |
| --- | --- | --- | --- | --- | --- |
| Regeln bisher, Prüfteil | 38,1 % | 40,7 % | 19,4 % | 86,9 % | – |
| Modell fp32, Schwelle 0,9, Prüfteil | 85,4 % | 14,6 % | 0,0 % | 100 % | 15 ms |
| **Modell int8, Schwelle 0,8, Prüfteil** | **84,8 %** | 15,2 % | **0,0 %** | **100 %** | 3,5 ms |
| Modell int8, Entwicklung | 89,8 % | 9,6 % | 0,4 % | 100 % | |
| **in der App** (`CommandModelTest`, ONNX Runtime über JNA), Prüfteil | **84,1 %** | 15,9 % | **0,0 %** | **100 %** | 3,3 ms |

Die Art der freien Anfrage (einfach/komplex, für den Mischmodus) stimmt im Prüfteil bei 100 % der eindeutig
ausgezeichneten Anfragen. Werte liest die App im Prüfteil bei 62 von 63 Wünschen richtig (`CommandSlotsTest`).

**Was offen ist** – ehrlich: Das Ziel „mindestens 90 % getroffen“ ist mit der vorsichtigen Schwelle nicht erreicht (84 %).
Eine erste, lockerere Wahl (Schwelle 0,3) traf im Prüfteil 95,4 %, führte aber 2,8 % falsch aus – über dem Ziel von 2 %;
darum die strengere Regel. Der Prüfteil wurde damit zweimal angesehen. Die 15 % weitergegebenen Befehle gehen wie bisher
an das Sprachmodell (eine Rückfrage, kein Schaden); AP-73 („Meinst du A oder B?“) holt sie ohne Server zurück. int8
verliert gegenüber fp32 0,6 Prozentpunkte Treffer (zwei Wünsche; erlaubt waren 0,5), führt aber nichts zusätzlich falsch
aus – bewusst so ausgeliefert.

## Rückfrage mit zwei Knöpfen (AP-73)

Ist das Modell unsicher (wahrscheinlichstes Etikett unter der Schwelle), führt die App nichts aus. Statt den Wunsch an ein
Sprachmodell weiterzugeben, fragt Lora **„Meinst du A oder B?“** mit den zwei wahrscheinlichsten **Befehlen** als Knöpfen
und „Etwas anderes“ (dann Regeln und Sprachmodell wie bisher) – wenn die beiden zusammen mindestens die **Untergrenze**
erreichen. Freie Anfragen tragen ihre Wahrscheinlichkeit auf `frei.*` und bleiben darunter; `frei.*` wird nie ein Knopf.
Dieselbe Regel steht in `rueckfrage.py` (`asks`) und in der App (`CommandRecognizer.choices`).

```bash
python tools/befehle/rueckfrage.py <ordner>/befehle-modell.int8.onnx                # Entwicklungsteil: wählt die Untergrenze
python tools/befehle/rueckfrage.py <ordner>/befehle-modell.int8.onnx --teil pruef   # Prüfteil: nur Zahlen, Wert aus modell/rueckfrage.json
```

Neben der `.onnx`-Datei müssen `vocab.txt`, `labels.json`, `schwelle.json` und eine `tokenizer_config.json`
(`{"tokenizer_class": "BertTokenizer", "do_lower_case": false}`) liegen.

**Wahl der Untergrenze nur auf dem Entwicklungsteil**: die niedrigste der Stufen 0,1 bis 0,75, bei der dort höchstens 2 %
der freien Anfragen Knöpfe bekämen (das Ziel im Prüfteil sind 5 %; streng, weil der Entwicklungsteil besser aussieht als
ungesehene Wünsche); bei gleichem Ergebnis die höhere. Gewählt am 10.11.2026: **0,3** (Entwicklung: 94,6 % der bisher
weitergegebenen Befehle richtig unter den Knöpfen, 1,1 % der freien mit Knöpfen; 0,1–0,25 trafen nicht mehr, gaben aber
bis 4,3 % der freien Anfragen Knöpfe). Der Prüfteil wurde danach **einmal** mit diesem Wert gemessen und für nichts
gewählt; eine Fehlerliste des Prüfteils gibt es nicht.

| 10.11.2026, Schwelle 0,8, Untergrenze 0,3 | richtig unter den Knöpfen (von den bisher weitergegebenen Befehlen) | freie Anfragen mit Knöpfen | sofort getroffen | **zusammen mit Rückfrage** | falsch ausgeführt |
| --- | --- | --- | --- | --- | --- |
| Python, Entwicklung | 94,6 % (35/37) | 1,1 % (1/94) | 89,3 % | 98,9 % | 0,4 % |
| Python, Prüfteil | 82,2 % (37/45) | 3,6 % (3/84) | 84,8 % | 97,0 % | 0,0 % |
| **in der App** (`CommandModelTest`), Prüfteil | **80,9 % (38/47)** | **3,6 % (3/84)** | 84,4 % | **97,0 %** | **0,0 %** |

Ziele von AP-73 (Prüfteil): mindestens 80 % richtig unter den Knöpfen, höchstens 5 % der freien Anfragen mit Knöpfen –
**erreicht**, in der App knapp (38 von 47; ein Wunsch weniger wäre 78,7 %). Die Python-Zahlen weichen um ein bis zwei
Wünsche von der App ab (Tokenizer-Einstellungen des Python-Laufs, ONNX Runtime 1.29); maßgeblich ist die App.
„Zusammen mit Rückfrage“ heißt: sofort richtig ausgeführt oder richtig unter den zwei Knöpfen – ein Tipp statt einer
Anfrage an ein Sprachmodell. Die Trefferquote des Modells selbst (AP-71, Ziel 90 %) ändert die Rückfrage nicht.

## Korrekturen als Trainingsdaten (AP-72)

*Einstellungen › Lora › Was Lora aus Korrekturen gelernt hat › Exportieren …* schreibt die Paare *Wunsch → gemeinte
Aktion*, die der Nutzer durchgesehen und angehakt hat, als `lora-korrekturen.jsonl` – eine Zeile wie `gen.py`
(`{"text": …, "bereich": "tabelle", "label": "tu:sheet.sort"}`). Das sind **echte Eingaben**: vor einem Training noch
einmal auf Namen prüfen, nur in den Trainingsteil aufnehmen (nie in den Messkorpus) und gegen den Messkorpus sperren wie
die Vorlagen (`gen.py check`).

## Was beim Modell liegt

`modell/`: Modellkarte, `vocab.txt`, `labels.json`, `schwelle.json`, `rueckfrage.json` (Untergrenze, AP-73), Berichte der Läufe
(Fehlerlisten nur für den Entwicklungsteil; `bericht-lora-befehle.json` ist die zweite Runde), Lizenz des Basismodells mit
vollem Lizenztext, Prüfsumme. Die Modelldatei selbst liegt gepackt (55 MB) auf dem Branch **`befehle-modell`** (Commit
`6f996b1c45609fdc9e565a113cfb11bfa28e14ee`, wird nicht gemergt) zusammen mit Modellkarte, Lizenztext des Basismodells und
`License.md`; die App lädt sie von dort:
`https://raw.githubusercontent.com/ADEU-TUD/Lora/6f996b1c45609fdc9e565a113cfb11bfa28e14ee/lora-befehle-pytales.int8.onnx.gz`.
Die Datei bis 1.0.65 (`befehle-modell.int8.onnx.gz`, Commit `cc168774477e3ac8b439b42fd7b03ab05c9908fa`) bleibt dort
erreichbar; wer sie hat, arbeitet mit ihr weiter (gleiche Etiketten, gleicher Wortschatz, eigene Schwelle 0,8 und
Untergrenze 0,3) und bekommt Lora Befehle einmal als Update angeboten. Nach dem Laden wird die alte Datei gelöscht.

**Neue Fassung veröffentlichen** (AP-117): `tools/modelle/veroeffentlichen.py befehle …` – nur bei erreichten Zielen, auf
den Branch `befehle-modell`, schreibt `modell/fassung.json`; Einzelheiten in [`tools/modelle/README.md`](../modelle/README.md).
Die App erkennt die Fassung am SHA-256 (`CommandModelInfo.VERSIONS`), auch bei gleichem Dateinamen und gleicher Größe.

**Lizenz**: Basismodell `distilbert/distilbert-base-german-cased` von Hugging Face unter Apache-2.0 (Lizenztext in
`modell/LIZENZ-distilbert-base-german-cased.txt`), von uns feinabgestimmt und verändert; die Urheber des Basismodells
haben Lora Befehle weder geprüft noch gebilligt. Für Lora Befehle selbst gilt `License.md`.

In der App messen:

```bash
BEFEHLE_MODELL=<Ordner mit lora-befehle-pytales.int8.onnx> BEFEHLE_ORT=<libonnxruntime> \
  ./gradlew -p desktop test --tests 'io.github.thepytales.llmhub.CommandModelTest'   # → desktop/build/befehle/app-mit-modell.txt
```

## Grundsätze

- **Nie echte Daten** im Messkorpus oder im Training. Einzige Ausnahme (AP-72, vom Hauptnutzer bestätigt): exportierte
  Korrekturen, die der Nutzer durchgesehen hat, ohne Namen und nur im Trainingsteil.
- Der **Prüfteil** dient nie zum Training, zur Wahl von Einstellungen oder als Vorbild für Vorlagen; Fehlerlisten gibt es
  nur für den Entwicklungsteil.
- **Eine falsch ausgeführte Aktion ist schlimmer als eine Rückfrage** – im Zweifel wird weitergegeben.
