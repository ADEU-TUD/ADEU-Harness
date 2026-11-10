# Messkorpus des Befehlsmodells (AP-71, Stufe b)

Erfundene, alltagsnahe Wünsche an Lora und Strg+K, an denen jede Erkennung gemessen wird: die Regeln der App heute
(`LoraAgent.local`), das Befehlsmodell in Python und das Befehlsmodell in der App. **Alles ist erfunden** – keine echten
Eingaben, keine echten Namen.

## Aufbau

Eine Datei je Art (`bereiche.txt`, `optionen.txt`, `umstellen.txt`, `aktionen.txt`, `lora.txt`, `frei.txt`,
`alltag.txt` mit Tippfehlern, Umgangssprache und Englisch). Jede Datei hat zwei Abschnitte:

- `== prüfteil ==` – **nie** zum Training, zur Wahl von Einstellungen (Schwelle, Basismodell) oder als Vorbild für
  Trainingsvorlagen benutzen. Nur für die Abnahme. Fehlerlisten des Prüfteils gibt kein Skript aus.
- `== entwicklung ==` – für Vergleiche, Schwelle und Fehlersuche, aber ebenfalls nicht als Trainingsdaten.

Eine Zeile je Wunsch, `#` leitet einen Kommentar ein:

```
mach mal dunkel => stell:theme ; wert=DARK
nach punkten absteigend sortieren => tu:sheet.sort @tabelle ; spalte=punkten ; absteigend=ja
dark mode einstellung => ort:option.appearance.theme|stell:theme
schreibe einen text über datenschutz an schulen => frei
```

| Teil | Bedeutung |
| --- | --- |
| `=> etikett` | die richtige Aktion (Etiketten: `../etiketten.json`, aus dem Code erzeugt) |
| `a\|b` | beide gelten als richtig (wo ein Wunsch wirklich zwei Lesarten hat); das erste ist die Hauptlesart |
| `frei` | freie Anfrage, einfach **oder** komplex; `frei.einfach` / `frei.komplex`, wo die Art klar ist |
| `@bereich` | was gerade offen ist: `dateien`, `pdf`, `dokument`, `tabelle`, `folien`, `video`, `mail` (ohne Angabe: sonst etwas) |
| ` ; name=wert` | Werte, die die App aus dem Wunsch lesen soll (Trenner: Leerzeichen, Semikolon, Leerzeichen) |

## Kennzahlen

Eine Erkennung liefert je Wunsch ein Etikett und eine Sicherheit. Unter der **Schwelle** – oder mit einem
`frei`-Etikett – wird der Wunsch an ein Sprachmodell **weitergegeben**.

- **Trefferquote**: Anteil der Befehle, die erkannt *und* ausgeführt würden (Etikett richtig, über der Schwelle).
- **Falsch ausgeführt**: Anteil *aller* Eingaben, bei denen eine andere Aktion ausgeführt würde, als gemeint war – das
  Schlimmste, darum die wichtigste Zahl. Ein weitergegebener Befehl ist nur eine Rückfrage.
- **Freie Anfragen weitergegeben**: Anteil der freien Anfragen, die nicht als Befehl ausgeführt würden.
- **Art richtig**: bei freien Anfragen mit klarer Art, ob einfach/komplex stimmt (für den Mischmodus).
- **Werte**: bei richtigem Etikett, ob die gelesenen Werte stimmen (misst die App, `CommandCorpusTest`).
- **Zeit** je Eingabe.

`python3 tools/befehle/corpus.py check` prüft Format und Etiketten und zählt.
