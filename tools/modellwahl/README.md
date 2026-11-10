# Modellwahl – Messkorpus für den Modellkatalog (AP-106)

Womit der Katalog der lokalen Modelle (`app/…/local/LocalCatalog.kt`) je Stufe und Zweck gewählt wird. Nicht Teil der
App; läuft auf dem Gerät, auf dem gemessen wird (Office-PC, Gaming-PC, Mac, Android über Termux o. ä.).

## Messkorpus

`messkorpus.jsonl` – 48 **erfundene** Aufgaben (keine echten Mails, Namen oder Arbeiten), je Zweck 8 im
**Entwicklungsteil** und 8 im **Prüfteil**:

| Zweck | Aufgaben | geprüft wird |
| --- | --- | --- |
| `TEXT` | Absagen, Einladungen, einfache Sprache, Zusammenfassen, Übersetzen, Feedback, Korrektur, Betreff | Pflichtangaben, Länge, Satz- und Stichpunktzahl, deutsche Sprache, verbotene Fremdwörter |
| `CODE` | kleine Python-Funktionen aus dem Alltag der Professur (Noten, Daten, CSV, Texte) | die Tests der Aufgabe laufen gegen den geschriebenen Code |
| `MAIL` | Einordnen (Lehre/Forschung/Verwaltung/Privat/Werbung), Termin als JSON, Zusammenfassen, Antwort | genaue Kategorie, JSON-Felder, Pflichtangaben, Länge |

Der **Allrounder** wird auf allen 48 Aufgaben gemessen. Regeln wie bei AP-67/AP-71: nur erfundene Daten; der Prüfteil
wird erst gemessen, wenn die Wahl feststeht, und nie zur Wahl von Modellen, Quantisierungen oder Einstellungen benutzt.

## Messen

```bash
# Programm und Modelle liegen im Datenordner der App: …/llm/runtime/llama-server, …/llm/models/*.gguf
python3 -I tools/modellwahl/messen.py --llama-server <…/llm/runtime/llama-server> --gguf <…/llm/models/Qwen3.5-4B-Q4_K_M.gguf> --ausgabe qwen4b.jsonl
python3 -I tools/modellwahl/messen.py --server http://127.0.0.1:8080 --modell gemma-4-e4b --zweck TEXT
```

Ausgabe je Zweck: richtig gelöst, Zeit je Aufgabe, Token/s. Code-Aufgaben führen den Code des Modells in einem eigenen
Python-Prozess (isoliert, leerer Ordner, 10 s) aus.

## Stand (10.11.2026)

- Korpus und Messskript fertig; die Prüfungen sind mit Musterantworten geprüft (alle 16 Code-Aufgaben mit einer
  Referenzlösung grün; die Referenzlösungen stehen bewusst nicht im Repository).
- **Noch nicht gemessen**: In der Cloud-Sitzung darf llama-server nicht gestartet werden; die Wahl in `LocalCatalog`
  ist deshalb vorläufig – nach Lizenz, Größe, Ruf der Modellfamilie und der Tempo-Vorhersage (AP-105). Je Stufe soll
  auf einem Gerät der Stufe gemessen werden: zuerst Entwicklungsteil für die Kandidaten der Stufe, Wahl begründet in
  `docs/MODELLKATALOG.md` festhalten, dann einmal der Prüfteil.
