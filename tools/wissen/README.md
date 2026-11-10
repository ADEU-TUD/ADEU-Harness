# Wissenspakete (AP-78)

Datenbanken zum Nachladen für Lora: Abkürzungen, Einrichtungen, Studiengänge, Abläufe – **jede Angabe mit Quelle und
Stand**, nur aus öffentlichen, offiziellen Seiten (TU Dresden, Freistaat Sachsen). Nichts Erfundenes; was sich auf den
Seiten nicht belegen ließ, bleibt draußen. Keine Namen von Personen.

| Datei | Inhalt |
| --- | --- |
| `lehramt-tud.json` | „Lehramt an der TU Dresden“ – Quellen: Seiten des ZLSB (v. a. *Lehramtsstudium von A–Z*), LaSuB, Studienangebot der TU Dresden; abgerufen am 10.11.2026. |

**Format** (`format: 1`): `id`, `titel`, `beschreibung`, `version`, `stand`, `eintraege` mit `begriff`, `art`,
`erklaerung`, `synonyme`, `quelle` (https-Adresse), `stand` (JJJJ-MM-TT). Dasselbe Format haben geteilte Lora-Pakete
(AP-83), dort zusätzlich `befehle`, `woerter`, `vorlagen` und `hausstil`.

**Auslieferung** wie die Modelle: die App lädt die Datei von diesem Repository an einem festen Commit
(`KnowledgePacks.CATALOG` in `data/KnowledgePacks.kt`) und prüft sie mit SHA-256. Für ein neues Paket oder eine neue
Fassung: Datei hier ändern, `version` erhöhen, committen, dann Commit, Größe und SHA-256 im Katalog eintragen
(`sha256sum tools/wissen/lehramt-tud.json`). `KnowledgePackTest` prüft, dass jede Angabe Quelle und Stand hat und der
Katalog zur Datei passt.
