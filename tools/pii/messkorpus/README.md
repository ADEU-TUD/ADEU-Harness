# Messkorpus der Pseudonymisierung (AP-67, Stufe a)

Erfundene, aber alltagsnahe Texte aus der Professur, an denen jede Erkennung gemessen wird: die Regeln von
`PiiDetector` heute, später Regeln + eigenes Erkennungsmodell. **Alles ist erfunden** – keine echten Interviews, Abgaben,
Mails oder Personen (außer öffentlichen Personen und zitierten Autor:innen, die gerade nicht ersetzt werden dürfen).

## Aufbau

Eine Datei je Textart (`interview.txt`, `elterngespraech.txt`, `gutachten.txt`, `mail.txt`, `protokoll.txt`,
`tabelle.txt`, `chat.txt`, `wissenschaft.txt`). Jede Datei hat zwei Abschnitte:

- `== prüfteil ==` – **nie** zum Training, zur Auswahl von Hyperparametern oder zum Schreiben von Vorlagen benutzen.
  Nur für die Abnahme (Ziel AP-67: ≥ 98 % private Namen gefunden, ≤ 2 % öffentliche ersetzt).
- `== entwicklung ==` – darf für Vergleiche, Schwellen und Fehlersuche benutzt werden, aber ebenfalls nicht als
  Trainingsdaten.

Beispiele sind durch eine **Leerzeile** getrennt; ein Beispiel darf mehrere Zeilen haben (Mail, Tabelle, Transkript).
Zeilen, die mit `#` beginnen, sind Kommentare.

## Auszeichnung

| Schreibweise | Bedeutung |
| --- | --- |
| `[[Jolina Kretzschmann\|PER]]` | private Person – muss ersetzt werden |
| `[[Grundschule am Park\|EINR]]` | Schule, Kita, Praxis, Verein … mit Personenbezug – muss ersetzt werden |
| `[[Pirna\|ORT]]` | Ort mit Personenbezug („die Schulleiterin in Pirna“) – muss ersetzt werden |
| `[[anna.k@web.de\|MUSTER]]` | feste Muster (Mail, Telefon, IBAN …) – Sache der Regeln, wird getrennt gezählt |
| `{{Klafki\|AUT}}` | zitierte Autor:in, Literatur – muss bleiben |
| `{{Angela Merkel\|OEFF}}` | öffentliche Person (auch Figuren aus Büchern) – muss bleiben |
| `{{TU Dresden\|EINR}}` | öffentliche Einrichtung – muss bleiben |
| `{{Leipzig\|ORT}}` | Ort ohne Personenbezug – muss bleiben |
| `{{Rose\|WORT}}` | Wort, das wie ein Name aussieht („Becker-Studie“, „Winter“) – muss bleiben |

Namen werden ohne Anrede ausgezeichnet („Frau `[[Wojciechowski|PER]]`“), aber mit Endung („`[[Annas|PER]]` Bruder“).
Eine Auszeichnung umfasst immer ganze Wörter.

## Messung

Gezählt wird auf **Wörtern** (`[\p{L}\p{N}]+` mit Bindestrich, Apostroph, Punkt oder @ dazwischen, z. B.
`Goethe-Gymnasium`, `e.V`). Welche Wörter eine Erkennung ersetzt hat, ergibt ein Wortabgleich (längste gemeinsame
Teilfolge) zwischen Original und pseudonymisiertem Text – so lässt sich jede Erkennung gleich messen.

- **Gefunden** ist eine private Angabe, wenn alle ihre *Kernwörter* ersetzt sind. Kernwörter sind alle Wörter außer
  einzelnen Buchstaben (Initialen) und allgemeinen Wörtern wie „Grundschule“, „Kita“, „am“, „Dr“ (Liste `GENERAL` in `corpus.py` und im Test); besteht eine
  Angabe nur aus solchen Wörtern, zählen alle.
- **Fälschlich ersetzt** ist eine öffentliche Angabe, wenn auch nur eines ihrer Wörter ersetzt ist.
- **Sonstige Fehlalarme**: ersetzte Wörter ohne Auszeichnung, je 1000 Wörter.

Die Kennzahlen gibt `PseudonymCorpusTest` (Regeln der App) aus; `tools/pii/corpus.py` rechnet für Python-Modelle genauso.
