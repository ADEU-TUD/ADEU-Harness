# Eigenes Erkennungsmodell für die Pseudonymisierung (AP-67)

Werkzeuge, um ein kleines deutsches Erkennungsmodell (NER: `PER`, `EINR`, `ORT`) für die Pseudonymisierung zu bauen.
Nicht Teil der App; läuft nur in der Cloud-Sitzung bzw. auf dem Trainings-PC. Plan und Stufen: `docs/ARBEITSPLAN.md`
› AP-67.

| Stufe | Was | Wo | Stand |
| --- | --- | --- | --- |
| a | Messkorpus (`messkorpus/`, `corpus.py`, `PseudonymCorpusTest`) | überall | ☑ |
| b | Trainingsdaten aus Vorlagen, erfundene Namen | Cloud / jeder PC | ☐ |
| c | Training (Hugging Face Transformers, bf16) | PC mit RTX 5080 | ☐ |
| d | Export ONNX int8 + Modellkarte | PC | ☐ |

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

## Grundsätze

- **Nie echte Daten**: keine echten Interviews, Abgaben, Mails, Namenslisten aus der Schule – weder im Messkorpus noch
  im Training.
- Der **Prüfteil** wird nie zum Training, zur Auswahl von Einstellungen oder als Vorbild für Vorlagen benutzt.
- Lehrer-Modell für die Trainingstexte ist Claude in der Cloud-Sitzung (Entscheidung 09.11.2026: kein Schlüssel des
  Uni-Servers); die Texte sind erfunden.
