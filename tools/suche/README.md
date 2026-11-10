# Suche nach Bedeutung auf dem Gerät (AP-107)

Werkzeuge, um das Einbettungsmodell der Wissenssammlung auszuwählen und zu messen. Nicht Teil der App. Plan:
`docs/ARBEITSPLAN.md` › AP-107. **Kein Training** – ein fertiges Modell (MIT oder Apache-2.0) wird ausgewählt, gemessen
und ausgeliefert.

| Datei | Inhalt |
| --- | --- |
| `messkorpus/dokumente/` | 30 **erfundene** Dokumente aus dem Alltag einer Professur (Dienstreisen, Prüfungsordnung, Labor, VPN, Hiwis …), mit ähnlichen Ablenkern (Bachelor- neben Master-Prüfungsordnung, Exkursion neben Dienstreise) |
| `messkorpus/entwicklung.txt` | 60 Fragen `Frage \| Dokument \| Beleg` – **nur** hiermit werden Modell und Mischung gewählt |
| `messkorpus/pruefung.txt` | 90 Fragen – nur für die Kennzahlen am Ende, nie zum Wählen |
| `messen.py` | Misst Modelle wie die App (Abschnitte wie `KnowledgeStore.split`, BM25 wie `Knowledge.kt`) und strenger je Absatz |
| `probe.py` | Schreibt `modell/token-probe.json` (Ids des Hugging-Face-Tokenizers) für den Gleichstand mit dem Kotlin-Tokenizer |
| `modell/bericht-*.json` | Ergebnisse beider Teile |

Der **Beleg** ist ein wörtlicher Ausschnitt aus dem Absatz, der die Frage beantwortet; ein Treffer zählt, wenn der
gefundene Abschnitt aus dem richtigen Dokument ist und den Beleg enthält. Die Fragen sind umformuliert („Hiwi“ statt
„studentische Hilfskraft“, „gelber Schein“ statt „ärztliche Bescheinigung“), wie man sie tippt.

```bash
python3 -m venv /tmp/v && /tmp/v/bin/pip install onnxruntime tokenizers numpy huggingface_hub torch transformers
/tmp/v/bin/python tools/suche/messen.py                       # Entwicklungsteil
/tmp/v/bin/python tools/suche/messen.py --teil pruefung       # Prüfteil – nur am Ende
# In der App gemessen (KnowledgeStore, Kotlin-Tokenizer, ONNX Runtime über JNA):
SUCHE_MODELL=<Ordner mit multilingual-e5-small.int8.onnx und .spm> SUCHE_ORT=<libonnxruntime> \
  ./gradlew -p desktop test --tests '*LocalEmbeddingTest*'    # → desktop/build/suche/bericht.json
```

## Wahl (Entwicklungsteil, 10.11.2026)

Treffer unter den ersten fünf / MRR, Abschnitte wie in der App (40 Abschnitte):

| Modell (Lizenz) | allein | gemischt wie bis 1.0.66 | normiert 0,8 | ms je Abschnitt¹ | Größe |
| --- | --- | --- | --- | --- | --- |
| nur Wörter (BM25, App heute ohne Server) | – | 73,3 % / 0,642 | – | – | – |
| **multilingual-e5-small** (MIT) | 98,3 % / 0,865 | 75,0 % / 0,659 | **96,7 % / 0,895** | 32 | 118 MB |
| multilingual-e5-base (MIT) | 95,0 % / 0,847 | 75,0 % / 0,658 | 96,7 % / 0,862 | 56 | 279 MB |
| paraphrase-multilingual-MiniLM-L12-v2 (Apache-2.0) | 88,3 % / 0,692 | 90,0 % / 0,784 | 95,0 % / 0,815 | 35 | 118 MB |
| granite-embedding-107m-multilingual (Apache-2.0) | 90,0 % / 0,728 | 78,3 % / 0,720 | 91,7 % / 0,804 | 31 | 428 MB² |
| Qwen3-Embedding-4B (Apache-2.0, Uni-Server)³ | 98,3 % / 0,922 | 86,7 % / 0,772 | 98,3 % / 0,959 | 5 700 | – |

¹ ONNX Runtime, zwei Rechenkerne, Cloud-Rechner. ² nur fp32 im Original. ³ dasselbe Modell wie der Uni-Server, hier lokal
gerechnet; Fragen ohne Anweisung, wie die App sie schickt.

- **Mischung**: Bis 1.0.66 rechnete die App `0,65 × Kosinus + 0,35 × Wortsuche`. Die Kosinuswerte von e5 (und Qwen3)
  liegen eng beieinander (0,7–0,9), die Wortsuche ist auf 0..1 gestreckt – so entschied fast nur die Wortsuche. Neu:
  Kosinus je Anfrage auf 0..1 strecken, dann `0,8 × Bedeutung + 0,2 × Wörter` (Gewichte 0,5/0,65/0,8/0,9 verglichen,
  0,8 am besten für e5-small und Qwen3). Gilt für das Modell auf dem Gerät und das des Servers.
- **Modell**: e5-small – am besten mit der Mischung, schnell, klein, MIT; das Original-Repository hat eine int8-Datei, die
  unverändert ausgeliefert wird. e5-base ist nicht besser, aber doppelt so langsam und 2,4-mal so groß.
- **Tokenizer**: SentencePiece (Unigram) in Kotlin (`data/LocalEmbedding.kt`), liest `sentencepiece.bpe.model`;
  dieselben Ids wie Hugging Face bei allen 352 Texten der Probe (`LocalEmbeddingTest`).

## Ergebnis (Prüfteil, einmal gemessen am Ende)

| | Abschnitte (App) | einzelne Absätze |
| --- | --- | --- |
| nur Wörter | 83,3 % / 0,671 | 74,4 % / 0,594 |
| **e5-small auf dem Gerät, normiert 0,8** | **98,9 % / 0,894** | **91,1 % / 0,854** |
| Uni-Server (Qwen3-Embedding-4B), Mischung bis 1.0.66 | 94,4 % / 0,835 | 90,0 % / 0,821 |
| Uni-Server (Qwen3-Embedding-4B), neue Mischung | 100 % / 0,946 | 100 % / 0,948 |

In der App gemessen (`LocalEmbeddingTest`, Kotlin): Prüfteil 98,9 % / MRR 0,894, Entwicklungsteil 96,7 % / 0,895 –
gleich wie in Python; 40 ms je Abschnitt im Mittel (Median 41, höchstens 92; zwei Kerne, während parallel ein anderer
Lauf rechnete).

**Ziel von AP-107**: „im Prüfteil mindestens so gut wie mit Server-Einbettungen“ – erreicht gegenüber dem Server, wie die
App ihn bis 1.0.66 nutzte (98,9 % gegen 94,4 %); gegenüber dem Server mit der neuen Mischung knapp darunter (98,9 % gegen
100 %). „≤ 50 ms je Abschnitt auf einem Office-PC“ – hier 32–40 ms; auf einem echten Dienst-PC noch zu messen.

Grenzen des Korpus: 30 Dokumente, 40 Abschnitte – klein; die Absatz-Einteilung (150 Einheiten) ist die strengere Messung.
