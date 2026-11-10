# Arbeitsplan Lora (bis 1.0.43 „ADEU LLM“) – Runde 2

Stand: 10.11.2026 (nach 1.0.65; AP-72 bis AP-85 erledigt, Veröffentlichung als 1.0.66 steht aus) · Lebendes Dokument: Status hier pflegen, erledigte Pakete abhaken und in der README beschreiben.
Runde 1 (AP-01 bis AP-37) ist erledigt und steht ausführlich in [`ARBEITSPLAN-RUNDE-1.md`](ARBEITSPLAN-RUNDE-1.md).

**So ist der Plan aufgebaut**

- **Jetzt** – die nächsten Wochen, in fester Reihenfolge. Ein Paket nach dem anderen; ist eines fertig, rückt das nächste
  nach.
- **Danach** – rückt nach „Jetzt“ auf, sobald dort Platz ist.
- **Später** – gewollt, aber erst, wenn „Jetzt“ und „Danach“ erledigt sind.
- **Ideen** – ohne Auftrag, je eine Zeile. Bei Bedarf wieder aufnehmen (Details der alten Fassung stehen in der
  Git-Historie dieser Datei).

**Legende**

- **Nummer**: `AP-xx` bleibt gleich, auch wenn ein Paket den Abschnitt wechselt. „bisher W…“ verweist auf die alte Fassung.
- **Größe**: S (bis 1 Tag) · M (einige Tage) · L (1–2 Wochen) · XL (mehrere Wochen, in Stufen teilen)
- **Status**: ☐ offen · ◐ in Arbeit · ☑ erledigt
- **Plattform**: A = Android, W = Windows, D = nur Desktop (Windows, später macOS/Linux), beide (A + W) wenn nicht
  angegeben.

---

## Grundsätze

| Thema | Entscheidung |
| --- | --- |
| Für wen | Ich bin Hauptnutzer und entscheide über die Funktionen. Das Team ist gleichberechtigt, bekommt die App kostenlos und gibt Feedback (über *Problem melden*). Keine Umfragen, keine Usability-Tests, keine Teamverwaltung. |
| Priorität | **Täglicher Nutzen** entscheidet. |
| Umfang der Werkzeuge | **Alltagsumfang**: die Funktionen, die den Großteil der Arbeit abdecken. Die Vorbilder (pdf24 + Xodo, Word/Excel/PowerPoint, Canva, DaVinci Resolve) geben die Richtung vor, nicht den vollen Umfang. Ausbau nur bei konkretem Bedarf. **Ausnahme seit Runde 2**: Dokumente, Tabellen und Folien sollen so nah wie möglich an Google Docs/Sheets/Slides (und Word-/PowerPoint-/Excel-Treue) heranreichen, der Videoeditor so nah wie möglich an DaVinci Resolve für den Alltag – ohne Fusion, Color Grading und Fairlight, eigenständig im Lora-Stil in **einer** Ansicht. Dokument, Folien und Gestalten teilen sich einen Editor-Kern (Text-Kern AP-43, Leinwand AP-50). |
| Übersichtlichkeit | Die App darf durch neue Funktionen nicht unübersichtlich werden: große Werkzeuge sind **einschaltbar** (anfangs aus, Lora schlägt sie passend vor), eingeschaltete Fortgeschrittene stehen in der Seitenleiste unter **„Mehr“**. Jedes neue Werkzeug fügt sich in AP-02 ein. Keine Einfach-/Expertenansicht. „Mehr“ bleibt eine flache Liste; **jeder Bereich lässt sich ausblenden** (außer *Erstellen* und *Dateien*), erreichbar bleibt er über Strg+K und Lora (seit AP-24). |
| Inhalte | Zwei feste Orte für Inhalte: **Dateien** (was es gibt) und **Erstellen** (was neu entsteht: TU Vorlagen und Gestalten mit „Meine Designs“). Jederzeit kann man stattdessen Lora sagen, was entstehen soll (seit AP-24). |
| Plattformen | **Windows-PC ist die Hauptplattform.** Android zieht nur Funktionen nach, die dort sinnvoll sind – **kein** Videoeditor, **kein** Kreativbereich. macOS und Linux sind da (AP-31); die Website steht in den Ideen. |
| Lora | **Kern der App** und Namensgeberin (seit 1.0.44 heißt die App „Lora“, Slogan: *Der freundliche Helfer für alle Wünsche in Forschung, Lehre und Verwaltung an der TU Dresden*; technische Namen wie `ADEU LLM.exe` und die Datenordner bleiben). Navigator und Helfer: findet Einstellungen, öffnet Modi und Vorlagen, übergibt an Chat-Modelle – **Standard für Übergaben ist DeepSeek V4.1 Flash**, andere nur in Sonderfällen, die Lora selbst entscheidet und begründet. Lora antwortet selbst mit GLM-5.3 Flash. Lora reagiert, unterbricht nicht (darum **kein Tagesbriefing**). Seit Runde 2 **handelt Lora selbst in den Werkzeugen** (AP-38) – sichtbar im Panel rechts, mit Vorschau und Rückgängig, ohne eigenen Cursor im Werkzeug – und merkt sich Vorlieben nur nach Rückfrage (AP-39). Animationen dürfen auch ohne neues Werkzeug kommen, wenn sie Lora lebendiger machen (AP-36); sie bleiben seriös und respektieren *Ruhigere Bewegungen*. |
| Modus-Konzept | Einheitlich: Chip über dem Eingabefeld, eine Zeile „was passiert hier“, Datenhinweis, ✕ zum Verlassen. |
| Datenschutz | Pseudonymisierung wird treffsicherer (weniger Fehlalarme, weniger Übersehenes). Für sensible Forschung gibt es den **Tresor** statt einer allgemeinen App-Sperre. |
| KI entscheidet nicht | Die KI schlägt vor, ein Mensch entscheidet (Bewertungen, Mails). Mails werden **nie gelöscht und nie ohne Aufforderung gesendet**. KI-Verordnung: Prüfungsbewertung ist Hochrisiko → nur Assistenz, Protokoll, Hinweis in der App. Datenschutzhinweise und Löschfristen für Mails und Abgaben laufend mitpflegen (bisher Q-04). |
| E-Mail/Kalender | Die Kolleg:innen nutzen das **klassische Outlook** → zuerst lokal über COM (ohne IT-Freigabe). Der Server-Weg (Exchange/IMAP) kommt erst, wenn jemand das neue Outlook nutzt. **Ins Postfach darf nur ein Modell auf diesem Gerät** (seit 09.11.2026, AP-68): Uni-Server und externe Anbieter (API) sind dort fest gesperrt, ohne Schalter. Das lokale Modell darf seine Ergebnisse an Chats mit API-Modellen übergeben; diese Chats greifen selbst nie ins Postfach. |
| Synchronisierung | **Einfach wie bei Obsidian**: Datenordner in einen Cloud-Ordner (Nextcloud, Google Drive, OneDrive), der auf beiden PCs eingerichtet ist. Mehrere PCs zuerst, Android ist nice to have. |
| Gemeinsame Daten | Keine Professur-Datenbank. Höchstens irgendwann ein gemeinsamer Ordner für TU Vorlagen (Ideen). |
| Anbindungen | Wichtige Dienste **fest eingebaut** (zuerst Nextcloud) statt zuerst allgemeines MCP. |
| Eigene Werkzeuge statt Fremd-Apps | Selbst bauen, gehört vollständig uns. Die Craft-Apps (getartcraft.com) werden **nicht** per MCP angebunden; je Werkzeug wird geprüft, ob Code übernommen oder selbst geschrieben wird (*Anhang: Craft-Apps*). |
| Eine App | Kein Launcher mit mehreren Apps. Werkzeuge werden ein- und ausgeblendet (AP-02); große Teile, die nicht alle brauchen, sind **nachladbare Erweiterungen** über den Updater (seit AP-25: Videoeditor-Paket; die Browser-Engine wird mit aufgeführt). |
| Sprachen | Englisch ist vollständig (AP-29), keine weiteren Sprachen. |
| Gestrichen | Qualitätstests für Modi (bisher Q-01), Usability-Tests (Q-03), Professur-Datenbank mit Rollen/Richtlinien/Audit-Log (W4-03), Betreuungs-Cockpit, weitere Sprachen, Tagesbriefing (Runde 2), die frühere Ideenliste (10.11.2026), Sprachbefehle für die App (Chat-Sprachmodus und Diktat reichen). (Lora-Animationen als eigenes Paket, bisher W2-07, kamen auf Wunsch mit AP-36 doch.) |

---

## Übersicht

Runde 2 (ab 02.11.2026). Die erste Runde (AP-01 bis AP-37) ist erledigt; ihre ausführlichen Beschreibungen stehen in
[`ARBEITSPLAN-RUNDE-1.md`](ARBEITSPLAN-RUNDE-1.md), die Kurzfassung unter *Erledigt*. AP-32 Website ist in die *Ideen*
gewandert.

Schwerpunkte: **Lora** (handelt selbst in den Werkzeugen), **Dokumente, Tabellen, Folien** (so nah wie möglich an Google
Docs/Sheets/Slides, gemeinsamer Editor-Kern) und **Video** (DaVinci Resolve für den Alltag, ohne Fusion, Color und
Fairlight).

| Abschnitt | Pakete |
| --- | --- |
| ~~Lora zuerst~~ ☑ | AP-38 Lora handelt in Werkzeugen · AP-39 Ein Gedächtnis für Lora · AP-40 Abläufe über Werkzeuge · AP-41 Leuchtrahmen überall und Touren · AP-42 Lora-Dateidialog (erledigt, siehe *Erledigt*) |
| ~~Editor-Kern und Dokument~~ ☑ | AP-43 Gemeinsamer Text-Kern · AP-44 Fließtext-Editor · AP-45 Word-Treue · AP-46 Gliederung und Formatvorlagen · AP-47 Lora schreibt im Dokument (erledigt, siehe *Erledigt*) |
| ~~Video-Grundlage~~ ☑ | AP-52 Mehrere Spuren und echte Vorschau · AP-53 Schnittwerkzeuge (erledigt, siehe *Erledigt*) |
| ~~Office~~ ☑ | AP-48 Tabellen wie Excel · AP-49 Diagramme und Lora rechnet · AP-50 Gemeinsame Leinwand für Folien und Gestalten · AP-51 Folien wie PowerPoint (erledigt, siehe *Erledigt*) |
| ~~Video und Dateien~~ ☑ | AP-54 Einfache Effekte · AP-55 Textbasierter Schnitt · AP-56 Bildschirmaufnahme · AP-57 Export mit GPU · AP-58 Frag diesen Ordner · AP-59 Windows-Integration (erledigt, siehe *Erledigt*) |
| ~~PDF und Diagramme~~ ☑ | AP-60 PDF-Reste · AP-61 Diagramme und Whiteboard (erledigt, siehe *Erledigt*) |
| ~~Scanner, Sync, Android, Diktat~~ ☑ | AP-62 Dokumenten-Scanner · AP-63 Sync- und Android-Reste · AP-64 Android zieht nach · AP-65 Diktat · AP-66 Schnellansicht in Dateien (erledigt, siehe *Erledigt*) |
| ~~Postfach und Videoeditor~~ ☑ | AP-68 Postfach nur mit lokalem Modell · AP-69 Echte Mailansicht und Lora sortiert selbst · AP-70 Videoeditor: ruhiges Bild, anpassbare und übersichtliche Oberfläche (erledigt, siehe *Erledigt*) |
| **Jetzt** | **AP-103 Datenschutzseite aufräumen (dringend)** · AP-67 Eigenes Erkennungsmodell für die Pseudonymisierung · AP-71 Lora versteht Befehle auf dem Gerät und arbeitet im Mischmodus |
| ~~Lora lernt nach~~ ☑ | AP-72 Lora lernt aus Korrekturen · AP-73 Rückfrage mit zwei Knöpfen (erledigt, siehe *Erledigt*) |
| ~~Lora im Alltag~~ ☑ | AP-74 Fehlermeldungen in Klartext · AP-75 Lora-Verlauf mit „Nochmal“ · AP-76 Sprachmodell-Empfehlung nach Messung · AP-77 Probelauf und „Alles rückgängig“ · AP-78 Eigene Wörter und Wissenspakete · AP-79 Hausstil- und Barrierefreiheits-Check · AP-80 Rollenauswahl · AP-81 Videoexport-Warteschlange · AP-82 Vorschau für Video und Ton · AP-83 Lora-Pakete teilen · AP-84 Lora in mehreren Fenstern · AP-85 Große Anhänge vollständig nutzen (erledigt 10.11.2026, siehe *Erledigt*) |
| **Danach** | AP-86 Lokale Modelle auf dem Mac laden |
| **Später** | AP-87 Datei-Erste-Hilfe · AP-88 Zeitreise für Dateien · AP-89 Vorlage aus eigener Datei · AP-90 Formatwandler mit Verlustanzeige · AP-91 Schriften-Notfall · AP-92 Formatierung aufräumen · AP-93 CSV-Lotse · AP-94 Formular aus Scan · AP-95 PDF-Tabellen nach Excel · AP-96 Diagramm zu Daten · AP-97 Druck-Lotse · AP-98 Passwortschutz für Dateien · AP-99 Archiv-Werkzeug · AP-100 Fotos vom Handy aufbereiten · AP-101 Bild-Werkstatt · AP-102 Umzug auf einen neuen PC |

Abhängigkeiten: AP-38 vor AP-40 und vor allen Lora-Teilen der späteren Pakete (jedes neue Werkzeug liefert seine
Lora-Aktionen über dieselbe Schnittstelle) · AP-39 vor AP-40 · AP-43 vor AP-44 vor AP-45, AP-46, AP-47 · AP-43 vor AP-48
(Zelltext) und AP-50 (Textfelder) · AP-48 vor AP-49 · AP-50 vor AP-51 und AP-61 · AP-52 vor AP-53 vor AP-54, AP-55 ·
AP-52 vor AP-57 · AP-42 vor AP-59.

Reihenfolge: Editor-Kern und Dokument (AP-43 bis AP-47), die Video-Grundlage (AP-52, AP-53), Office (AP-48 bis AP-51),
Video und Dateien (AP-54 bis AP-59), PDF-Reste und Diagramme (AP-60, AP-61) sowie Scanner, Sync-Reste, Android, Diktat und
die Schnellansicht (AP-62 bis AP-66, 1.0.61) sind erledigt. Jetzt das eigene Erkennungsmodell für die Pseudonymisierung
(AP-67); innerhalb von AP-67 kommt der Messkorpus (Stufe a) vor allem anderen.

**Dringend (10.11.2026)**: AP-103 – die Datenschutzseite aufräumen und übersichtlicher darstellen.

**Dringend (09.11.2026)**: AP-68 bis AP-70 sind erledigt (09.11.2026). AP-67 läuft weiter (das Training der
Pseudonymisierung geschieht in einem eigenen Chat). Neu am 09.11.2026: **AP-71** (Befehlsmodell und Mischmodus für Lora),
ebenfalls in einem eigenen Chat; auch dort kommt der Messkorpus vor jedem Training.

---

## Jetzt

Die dringenden Pakete vom 09.11.2026 (AP-68 bis AP-70) sind erledigt (siehe *Erledigt*). Jetzt laufen AP-67 und AP-71
nebeneinander, je in einem eigenen Chat.

**Dringend: AP-103** (10.11.2026, vor den übrigen Paketen)

### AP-103 ☐ Datenschutzseite aufräumen · S–M (neu, dringend)
**Befund (10.11.2026)**: *Einstellungen › Datenschutz* (`PrivacySettings.kt`) ist durch viele Überarbeitungen
unübersichtlich geworden: eine lange Seite mit rund zehn Karten untereinander – *Pseudonymisierung* (Hauptschalter,
Erkennungsmodell, „Namen automatisch erkennen“, „Öffentliche Quellen“, „Gründlich“), *Postfach*, dann vier Karten mit
21 Einzelschaltern (*Kontaktdaten*, *Nummern und Daten*, *Namen*, *Platzhalter* – darin auch Fremdes wie „Warnung bei
Bildern“ und „Namensvorschläge beim Schreiben“), *Eigene Namen und Begriffe*, *Ausprobieren*, *Zuordnungen*, *Gut zu
wissen*. Die Schalter haben lange Erklärtexte, Wichtiges (ist der Schutz an? was sieht der Server?) und selten
Gebrauchtes stehen gleichrangig, der Hinweis „Pseudonymisierung ist aus – die Schalter unten wirken erst …“ steht
mitten in der Seite, und die Postfach-Regel ist eine Karte ohne Bedienung.
**Ziel**: Eine ruhige Seite, die auf einen Blick sagt, wie geschützt man ist, und Feinheiten erst auf Wunsch zeigt.
- **Oben eine Statuskarte**: Schutz an/aus (ein Schalter), darunter in einer Zeile, was aktiv ist („Regeln ·
  Erkennungsmodell · Gründlich aus“) und die festen Regeln als kurze Marken (*Postfach nur auf diesem Gerät*, *Tresor*).
  Ist der Schutz aus, sind die übrigen Bereiche sichtbar ausgegraut statt nur mit Hinweistext.
- **Drei Hauptbereiche** in fester Reihenfolge: *Erkennung* (was gefunden wird: Namen automatisch, öffentliche Quellen,
  Gründlich, Erkennungsmodell), *Meine Listen* (immer / nie ersetzen, Import/Export, Zuordnungen mit Suche),
  *Ausprobieren*.
- **Die 21 Einzelschalter** unter *Feineinstellungen* (zugeklappt, Zustand gespeichert), je Gruppe eine Zeile mit „7 von
  7 an“; Erklärtexte kurz, das Beispiel als Unterzeile. Schalter, die nicht zur Pseudonymisierung gehören („Warnung bei
  Bildern“, „Namensvorschläge beim Schreiben“), wandern an ihren Ort bzw. in einen eigenen kleinen Bereich *Hinweise
  beim Senden*.
- **„Gut zu wissen“ und die Postfach-Regel** als einklappbares *So schützt Lora deine Daten* am Ende, kurz und in
  Klartext; dazu der Verweis auf das Agent-Protokoll.
- Nichts geht verloren: alle Einstellungen behalten ihre Werte; Strg+K, Lora (Spots wie `privacy.pseudonymize`) und die
  Leuchtrahmen (AP-41) finden jede Einstellung weiterhin. Gleich auf Android und Desktop, alle Stile (Hoher Kontrast).
  en.tsv mitpflegen.
**Fertig, wenn**: die Seite ohne Aufklappen auf einen Bildschirm passt, der Schutzstatus oben sofort erkennbar ist,
jede bisherige Einstellung erreichbar bleibt (Test über alle `PiiFeature`-Werte und Spots) und Screenshots vorher/nachher
in der README stehen.

**Parallel: AP-67 und AP-71**

Am 09.11.2026 aufgenommen (Wunsch des Hauptnutzers, Weg A der Abwägung „eigenes Modell“). Weitere Vorschläge aus den
*Ideen*, wenn AP-67 fertig ist: **Fehlermeldungen in Klartext mit Lösungsknopf** (W2-09), **eigene Lora-Befehle**
(gespeicherte Abläufe per Name/Strg+K), **Exchange über EWS/Graph** (sobald Offene Frage 3 geklärt ist), **Tabellen beim
Taggen und LTV der Signatur** (Rest AP-60).

### AP-67 ◐ Eigenes Erkennungsmodell für die Pseudonymisierung · M–L (neu, Nachfolger von „Gründlich“ aus AP-03)
**Ziel**: Ein kleines, eigenes Erkennungsmodell (NER, deutsch, etwa 70–120 MB als ONNX int8) findet private Namen,
Einrichtungen (Schulen, Kitas, Praxen …) und Orte mit Personenbezug **in Millisekunden auf dem Gerät** – auf Android und
Windows, **immer an**, ohne installiertes Sprachmodell. Es ersetzt den Schritt „Gründlich“, in dem heute das kleinste
installierte lokale Sprachmodell (2B/4B, mehrere Sekunden je Text, Namen als JSON statt Fundstellen) nachliest. Die Regeln
von `PiiDetector` bleiben für feste Muster (Mail, IBAN, Telefon, Matrikelnummer …) und die eigene Liste; die Erlaubt-Liste
(zitierte Autor:innen, öffentliche Personen, Literatur) hat weiter Vorrang. Die Gewichte gehören uns und werden fest mit der
App ausgeliefert (Grundsatz „Eigene Werkzeuge statt Fremd-Apps“).

Weg: **Feintuning eines vortrainierten Encoders mit Destillation** – ein großes „Lehrer“-Modell erzeugt und beschriftet
erfundene Texte, das kleine Modell lernt daraus. Kein eigenes Modell von Grund auf, kein 4B-Sprachmodell (zu groß und zu
langsam für eine Aufgabe, die Fundstellen im Text braucht).

**Stufen**
- **a) Messkorpus zuerst** (vor jedem Training): `PseudonymCorpusTest` von 12 auf mindestens 500 Sätze aus dem Alltag der
  Professur – Interview-Transkripte, Elterngespräch, Gutachten, Mail, Protokoll, Notentabelle, Chat –, je Fundstelle mit Art
  (Person, Einrichtung, Ort). Dazu Gegenbeispiele, die **nicht** ersetzt werden dürfen: zitierte Autor:innen („Klafki
  (1996)“, „nach Helmke“), öffentliche Personen, Literaturangaben, Orte ohne Personenbezug, Wörter, die wie Namen aussehen
  („Rose“, „Winter“, „Becker-Studie“). Kennzahlen je Art: **Trefferquote** privater Angaben und **Fehlalarme** bei
  öffentlichen Namen; Ausgangswert der heutigen Regeln (mit und ohne „Gründlich“) festhalten. Ein **Prüfteil**
  (mindestens 150 Sätze) wird nie zum Training benutzt.
  ☑ *Erledigt (09.11.2026)*: 517 Beispiele in `tools/pii/messkorpus/` (Prüfteil 174 mit 261 privaten und 121
  öffentlichen Angaben), `PseudonymCorpusTest` misst die Regeln. Ausgangswert der Regeln auf dem Prüfteil: **78,2 %**
  gefunden (Personen 83,9 %, Einrichtungen 90,3 %, Orte 16 %), **9,9 %** der öffentlichen Angaben fälschlich ersetzt
  (öffentliche Personen 30 %); Einzelheiten in `tools/pii/README.md`. Offen: der Wert *mit* „Gründlich“ – in der
  Cloud ließ sich kein lokales Sprachmodell starten; am PC misst ihn der Test mit `PII_GRUENDLICH_URL` (llama-server).
- **b) Trainingsdaten ohne echte Daten**: Generator in `tools/pii/` (Python, nicht Teil der App). Erfundene Texte nach
  Vorlagen der Textarten aus a), mit erfundenen Namen (Listen aus `Names` und offenen Namenslisten, auch Namen von Familien,
  die in Deutschland leben), erfundenen Schulen/Kitas und sächsischen Orten. Das **Lehrer-Modell** ist Claude in der
  Cloud-Sitzung (Entscheidung 09.11.2026, kein Schlüssel des Uni-Servers): Es schreibt abwechslungsreiche Vorlagen mit
  Platzhaltern, der Generator setzt erfundene Namen ein – so stimmen die Beschriftungen genau; die Regeln von
  `PiiDetector` sind die zweite Meinung, Abweichungen werden geprüft. Ziel 20 000–50 000 Sätze, Beschriftung
  im BIO-Schema (`PER`, `EINR`, `ORT`). **Nie echte Interviews, Abgaben oder Mails** im Training.
  ☑ *Erledigt (09.11.2026)*: 823 Vorlagen in zehn Textarten (`tools/pii/vorlagen/`), `gen.py` erzeugt 30 000 Texte mit
  zeichengenauen Fundstellen; Wörter aus dem Prüfteil werden nie gezogen, Überschneidungen mit dem Messkorpus meldet
  `gen.py check`.
- **c) Training auf dem eigenen PC** (RTX 5080 mit 16 GB Grafikspeicher, 32 GB DDR5): Feintuning eines Encoders mit
  Hugging Face Transformers in bf16, wenige Epochen – Stunden, nicht Tage. Kandidaten werden auf dem Messkorpus verglichen
  und nur genommen, wenn ihre Lizenz MIT oder Apache-2.0 ist (vor dem Start einzeln prüfen): ein deutsches BERT (z. B.
  gbert-base), XLM-RoBERTa-base, GLiNER (multilingual, für personenbezogene Angaben vortrainiert). Hinweis: Grafikkarten der
  RTX-50-Reihe (Blackwell) brauchen PyTorch mit CUDA 12.8 oder neuer. Reicht das Tempo auf dem Handy nicht, wird das Modell
  in ein kleineres mit 6 Schichten destilliert.
  ◐ *Vorbereitet (09.11.2026)*: `tools/pii/pc.ps1` richtet auf dem PC alles ein und trainiert gbert-base,
  gelectra-base (beide MIT) und distilbert-base-german-cased (Apache-2.0, 6 Schichten); XLM-RoBERTa fällt wegen der
  Größe (int8 ≈ 280 MB) heraus, GLiNER wegen des aufwendigen Einbaus. Probelauf in der Cloud auf CPU (gbert-base,
  1000 Texte, eine Epoche): Prüfteil 96,9 % gefunden (Regeln 78,2 %), 22 ms je Absatz. Für die Kombination gibt es
  `Pseudonymizer.nameChains`: ohne das Raten von Namen aus häufigen Vornamen, das öffentliche Personen trifft.
  ◐ *Trainiert in der Cloud (09.11.2026, CPU, 3 Epochen)*: **gbert-base** gewählt (Entwicklung 97,4 % / 1,7 %,
  distilbert 96,8 % / 2,8 %). Prüfteil, wie geplant kombiniert: **99,2 %** der privaten Angaben gefunden, **5,8 %** der
  öffentlichen fälschlich ersetzt (Ziel ≤ 2 % noch nicht erreicht – vor allem ungesehene öffentliche Personen).
- **d) Export**: ONNX, int8-Quantisierung; das quantisierte Modell darf auf dem Prüfteil höchstens 0,5 Prozentpunkte
  verlieren. Eine **Modellkarte** (Basismodell und Lizenz, Herkunft der Daten, Kennzahlen, Grenzen) kommt mit in die App
  und in die Drittanbieter-Hinweise.
  ◐ *Exportiert*: int8 110 MB, 0,0 Prozentpunkte Verlust, 7,7 ms je Absatz auf der CPU, Modellkarte und Schwelle
  (`schwelle.json`) liegen bei. Offen: wo die Datei dauerhaft liegt (Offene Frage 9) – im Repo geht es nicht (über 100 MB).
- **e) Einbau**: neu `data/PiiModel.kt` (gemeinsam) über die ONNX Runtime, die die App mit sherpa-onnx schon mitbringt;
  Tokenizer (WordPiece bzw. SentencePiece) in Kotlin, Fenster von 512 Token mit Überlappung, Fundstellen als `PiiHit`.
  `Pseudonymizer` nimmt Regeln, Modell und eigene Liste zusammen; der Schalter *Gründlich* unter *Einstellungen ›
  Datenschutz* wird zu *Erkennungsmodell* (Standard an). Das lokale Sprachmodell bleibt nur optional für indirekte Merkmale
  („die Schulleiterin in Pirna“). Auslieferung als **Download** wie die Sprachmodelle (Offene Frage 9, entschieden):
  ein Popup bietet ihn beim ersten Nutzen der Pseudonymisierung oder beim Öffnen von *Einstellungen › Datenschutz* an
  („Laden“, „Später“, „Nicht mehr fragen“); Quelle ist der Branch `pii-modell` an festem Commit, geprüft mit SHA-256.
  ◐ *Eingebaut (09.11.2026)*: `data/PiiModel.kt` (WordPiece in Kotlin, ONNX Runtime von sherpa-onnx über ihre C-API mit
  JNA – auf Android neu `net.java.dev.jna:jna` –, Fenster 512/64, Schwelle 0,9, bekannte Namen der App bleiben),
  `data/PiiRuntime.kt` je Plattform (Ablage: Android App-Speicher, PC `%LOCALAPPDATA%\ADEU LLM\pii`), Download über das
  gemeinsame `FileDownload` (auch für die lokalen Sprachmodelle), Popup `PiiModelPrompt` und Schalter *Erkennungsmodell
  (empfohlen)* unter *Einstellungen › Datenschutz* mit Modellkarte; mit Modell gilt `nameChains = false`. Am PC getestet,
  **auf Android noch ungetestet** (JNA und der Pfad zur ONNX Runtime von sherpa-onnx).
- **f) Tests**: der Messkorpus als `PseudonymCorpusTest` mit Schwellen, Laufzeit je Absatz, ein Test, dass Autor:innen und
  öffentliche Namen bleiben, und dass ohne Modell (Datei fehlt) die Regeln allein weiterlaufen.
  ◐ *Erledigt bis auf das Ziel*: `PiiModelTest` – Tokenizer gleich dem des Trainings (520 Texte), Auswertung der Etiketten,
  ohne Modell wie bisher, und mit `PII_MODELL`/`PII_ORT` das echte Modell auf dem Messkorpus. In der App gemessen: Prüfteil
  **99,2 %** gefunden, **7,4 %** fälschlich ersetzt (Ziel ≤ 2 % offen), Entwicklung 97,1 % / 2,2 %, ≈ 11 ms je Absatz.

*Stand 10.11.2026 (nach AP-74 bis AP-85)*: unverändert offen – das Ziel „höchstens 2 % öffentliche Namen ersetzt“ braucht
ein Nachtraining (mehr öffentliche Personen und Literaturangaben in den Vorlagen, dann neu exportieren und auf dem Branch
`pii-modell` veröffentlichen) und den Gerätetest auf Android.

**Fertig, wenn**: Auf dem Prüfteil des Messkorpus findet die Kombination aus Regeln und Modell mindestens 98 % der privaten
Namen und ersetzt höchstens 2 % der öffentlichen; ein Absatz braucht auf einem Dienst-PC ohne Grafikkarte unter 50 ms, auf
dem Handy unter 150 ms; das Modell liegt mit Lizenz und Modellkarte in der App.

**Betrifft**: `data/PiiDetector.kt`, `data/Pseudonymizer.kt`, `AppContainer` (`thoroughFinder`), `ui/PrivacySettings.kt`,
neu `data/PiiModel.kt`, `tools/pii/` (Generator, Training, Export – Python, nur auf dem Trainings-PC).

### AP-71 ◐ Lora versteht Befehle auf dem Gerät und arbeitet im Mischmodus · L (neu)
**Ziel 1 – eigenes Befehlsmodell**: Ein kleines Modell auf dem Gerät ordnet Eingaben in Lora und Strg+K **in
Millisekunden und offline** der richtigen Aktion zu – Bereich oder Werkzeug öffnen, Modus starten, Einstellung zeigen
oder umstellen, TU Vorlage öffnen, Lora-Aktion im offenen Werkzeug, Tour, frühere Chats, Frist – statt dafür jedes Mal
den Server zu fragen. Dazu die Klassen **„frei“** (einfach bzw. komplex): Solche Anfragen gehen wie heute an ein
Sprachmodell. Ist die Erkennung unsicher (Schwelle), wird ebenfalls weitergegeben: **Eine falsch ausgeführte Aktion ist
schlimmer als eine Rückfrage.** Werte (Schriftgröße, an/aus, Dateiname, Seiten, Spalte, Datum) zieht die App mit Regeln
aus der Eingabe, sobald die Aktion feststeht.

**Ziel 2 – Mischmodus (Standard)**: Lora entscheidet je Anfrage, wo sie läuft. Befehle über das Befehlsmodell sofort
lokal; einfache, kurze Aufgaben mit einem installierten lokalen Sprachmodell, wenn die Hardware reicht; komplexe Aufgaben
(lange Dokumente, Analysen, Bilder, Websuche, Werkzeugketten) über den Server wie bisher. Einstellung *Lora: Gemischt
(empfohlen) / Nur lokal / Nur Server* mit klarer Erklärung, was „Nur lokal“ kostet (Qualität, Kontextlänge, Tempo,
Funktionen). Unverändert bleiben: Postfach nur lokal (AP-68), Tresor, Pseudonymisierung vor jeder Server-Anfrage,
Standard für Übergaben DeepSeek V4.1 Flash, Lora selbst GLM-5.3 Flash.

Weg wie bei AP-67 (Feintuning eines vortrainierten Encoders, Claude als Lehrer-Modell, nur erfundene Daten); die
Werkzeuge aus `tools/pii/` werden wiederverwendet bzw. verallgemeinert (`tools/befehle/`).

**Stufen**
- **a) Etiketten aus dem Code**: die Aktionen kommen aus den Registern der App (`AppMap`: Bereiche,
  Einstellungsseiten, Optionen, Orte, Modi, Werkzeuge – deckt auch die Strg+K-Einträge ab; `LoraAgent.SETTINGS`;
  `ToolActions`/`OfficeActions`/`MoreActions`/Video; Lora-eigene Aktionen), dazu `frei.einfach` und `frei.komplex`. Ein
  Test hält die Liste des Modells und den Code zusammen (kein Etikett ohne Ziel im Code; neue Ziele ohne Etikett werden
  gemeldet und gehen bis zum nächsten Training an das Sprachmodell).
  ☑ *Erledigt (10.11.2026)*: 178 Etiketten aus `LoraCommands.catalog` (`tools/befehle/etiketten.json`); `CommandLabelsTest`
  und `CommandModelTest` halten Code und Modell zusammen.
- **b) Messkorpus zuerst** (vor jedem Training): erfundene, alltagsnahe Wünsche – auch mit Tippfehlern,
  Umgangssprache und auf Englisch –, je Wunsch die richtige Aktion (wo zwei passen: beide), bei Bedarf der offene
  Bereich und die erwarteten Werte; mindestens 600 Wünsche, davon ein **Prüfteil** (mindestens 250), der nie zum
  Training, zur Wahl von Einstellungen oder als Vorbild für Vorlagen dient. Fehlerlisten nur für den Entwicklungsteil.
  Kennzahlen: **Trefferquote** der Befehle, **Anteil falsch ausgeführter Aktionen**, **Anteil richtig weitergegebener
  freier Anfragen**, Zeit je Eingabe; Ausgangswert der heutigen Regeln (`LoraAgent.local`) festhalten.
  ☑ *Erledigt (10.11.2026)*: 844 Wünsche in `tools/befehle/messkorpus/` (Prüfteil 386, jedes Etikett in beiden Teilen).
  Ausgangswert der Regeln im Prüfteil: 38,1 % getroffen, **19,4 % falsch ausgeführt**, 86,9 % der freien Anfragen weitergegeben.
- **c) Trainingsdaten**: Claude schreibt als Lehrer-Modell Formulierungen und Satzmuster je Etikett (kein Schlüssel
  des Uni-Servers), ein Generator setzt sie zusammen (Tippfehler, Kleinschreibung, Füllwörter, Englisch); **Sperre**
  gegen Überschneidung mit dem Messkorpus; keine echten Daten.
  ☑ *Erledigt (10.11.2026)*: 2 866 Zeilen in `tools/befehle/vorlagen/`, `gen.py` erzeugt 26 360 Wünsche; gleiche Wünsche wie
  im Messkorpus werden nie erzeugt. Nachgeschärft nur anhand der Fehler im Entwicklungsteil.
- **d) Ein Modell mit zwei Köpfen oder ein eigenes kleines?**: prüfen, ob das Modell der Pseudonymisierung (AP-67,
  gbert-base) einen zweiten Kopf für Befehle bekommen kann – dann bliebe es bei einem Download –, oder ob ein kleineres
  Basismodell (z. B. distilbert-base-german-cased) besser ist. Auf dem Entwicklungsteil messen, begründet entscheiden,
  hier festhalten. Lizenz nur MIT oder Apache-2.0.
  ☑ *Entschieden (10.11.2026)*: **eigenes kleines Modell, distilbert-base-german-cased (Apache-2.0)**. Ein Satz-Kopf auf dem
  eingefrorenen gbert-base – der einzige Weg, das Modell von AP-67 unverändert mitzunutzen – trifft im Entwicklungsteil
  7,1 % der Befehle, distilbert auf denselben Daten 86,0 %. Ein gemeinsames Training beider Köpfe würde jedes Nachtraining
  der Befehle an eine neue Abnahme von AP-67 binden, das selbst noch offen ist; distilbert ist zudem halb so groß und
  doppelt so schnell. gbert-base voll feinabgestimmt wurde nicht gemessen. Einzelheiten: `tools/befehle/README.md`.
- **e) Training und Export**: in der Cloud auf CPU in Etappen (`train.py --nur/--fortsetzen`, Hintergrundläufe
  höchstens 2 h), Schwelle auf dem Entwicklungsteil, Export ONNX int8 mit **Modellkarte**; int8 darf auf dem Prüfteil
  höchstens 0,5 Prozentpunkte verlieren.
  ◐ *Trainiert und exportiert (10.11.2026)*: 5 Epochen auf CPU, ONNX int8 68 MB (gepackt 41 MB), Schwelle 0,8.
  Prüfteil: **84,8 % getroffen, 0,0 % falsch ausgeführt, 100 % der freien Anfragen weitergegeben**, 3,5 ms je Eingabe.
  Offen: das Trefferziel 90 % (eine lockerere Schwelle traf 95,4 %, führte aber 2,8 % falsch aus – darum die strenge);
  int8 verliert 0,6 statt höchstens 0,5 Prozentpunkte (zwei Wünsche, nichts zusätzlich falsch ausgeführt).
  *Mit AP-73 (10.11.2026)*: Die Rückfrage mit zwei Knöpfen holt die weitergegebenen Befehle ohne Server zurück –
  **zusammen 97,0 %** der Befehle im Prüfteil sofort richtig oder richtig unter den zwei Knöpfen, weiter 0,0 % falsch
  ausgeführt. Die Trefferquote des Modells selbst bleibt 84,4 %; das Ziel „mindestens 90 % ordnet das Modell richtig zu“
  ist damit **nicht** erreicht. Offen für den Hauptnutzer: das Ziel so lassen (dann braucht es ein Nachtraining, etwa mit
  den Korrekturen aus AP-72) oder „sofort oder mit einem Tipp“ als Treffer zählen.
- **f) Einbau**: neu `data/CommandModel.kt` wie `data/PiiModel.kt` – WordPiece in Kotlin, ONNX Runtime von
  sherpa-onnx über JNA, Download über `data/FileDownload.kt` mit SHA-256, große Datei auf eigenem Branch
  (`befehle-modell`, wird nicht gemergt), Angebot per Popup. `LoraAgent` fragt zuerst die Regeln, dann das Befehlsmodell,
  erst dann ein Sprachmodell; Strg+K zeigt den erkannten Befehl als ersten Eintrag. Android wird nach dem Gerätetest des
  Hauptnutzers bestätigt.
  ◐ *Eingebaut (10.11.2026)*: `data/CommandModel.kt` (Download vom Branch `befehle-modell`, SHA-256), `agent/LoraCommands.kt`
  (Ausführung), `agent/CommandSlots.kt` (Werte), `OrtClassifier` für Satz-Modelle erweitert; Strg+K zeigt den Befehl als
  ersten Eintrag; Angebot im Dialog vom ersten Start und einmalig als „Neu: Modelle für dieses Gerät“ für alle, die Lora
  schon nutzen. In der App gemessen: Prüfteil 84,1 % / 0,0 % / 100 %, 3,3 ms (am 10.11.2026 mit ONNX Runtime 1.29
  erneut: 84,4 % / 0,0 % / 100 %, 3,7 ms). **Android ungetestet.**
- **g) Mischmodus**: `agent/ModelRouter.kt` entscheidet je Anfrage den Ort (Befehl lokal · lokales Sprachmodell ·
  Server) aus Befehlsmodell, Länge, Anhängen, Bildern, Websuche/Werkzeugbedarf, installiertem Modell und Hardware
  (`local/LocalLlm.kt`, `LocalCatalog.kt`); Einstellung unter *Einstellungen › Lora*; Zähler „wie oft und wie schnell
  lokal statt Server“ auf dem Gerät, sichtbar in den Einstellungen.
  ☑ *Erledigt (10.11.2026)*: `ModelRouter.place`, Einstellung *Wo Lora arbeitet* mit Erklärung der Folgen, Zähler
  `data/LoraWays.kt`, im Chat mit *Modell automatisch wählen*. Offen: was „läuft gut“ heißt, misst erst AP-76.
- **h) Tests und Doku**: `CommandModelTest` wie `PiiModelTest` (Tokenizer gleich dem des Trainings, mit und ohne
  Modell, echtes Modell mit `BEFEHLE_MODELL`/`BEFEHLE_ORT` auf dem Messkorpus), Tests für Werte, Router und Etiketten;
  README, englische Texte (`i18n/en.tsv`), Release.
  ☑ *Erledigt (10.11.2026)*: `CommandModelTest`, `CommandSlotsTest` (Werte im Prüfteil 62 von 63), `CommandCorpusTest`,
  `CommandLabelsTest`; README, `tools/befehle/README.md`, englische Texte.

*Stand 10.11.2026 (nach AP-74 bis AP-85)*: unverändert offen – wartet auf die Entscheidung des Hauptnutzers zum Trefferziel
(so lassen und nachtrainieren, z. B. mit exportierten Korrekturen aus AP-72, oder „sofort oder mit einem Tipp“ = 97,0 %
als Treffer zählen) und auf den Gerätetest auf Android.

**Fertig, wenn**: Auf dem Prüfteil ordnet das Modell mindestens 90 % der Befehle richtig zu, führt höchstens 2 % der
Eingaben falsch aus und gibt mindestens 95 % der freien Anfragen weiter; eine Eingabe braucht auf einem Dienst-PC ohne
Grafikkarte unter 30 ms; ohne Modell (Datei fehlt) arbeitet Lora wie bisher; die Einstellung *Gemischt / Nur lokal / Nur
Server* ist da und erklärt die Folgen; die App zeigt, wie oft und wie schnell lokal geantwortet wurde; das Modell liegt
mit Lizenz und Modellkarte bei.

**Betrifft**: `agent/LoraAgent.kt`, `agent/ModelRouter.kt`, `ui/LoraHelper.kt`, `ui/CommandPalette.kt`,
`data/SettingsStore.kt`, `AppContainer`, neu `data/CommandModel.kt`, `agent/LoraCommands.kt`, `tools/befehle/`
(Messkorpus, Generator, Training, Export – Python, nicht Teil der App).

---

## Danach

AP-72 bis AP-85 sind erledigt (10.11.2026, siehe *Erledigt*). Als Nächstes nach AP-103: AP-86.
AP-67 und AP-71 (Modellziele) werden nachtrainiert und stehen weiter unter *Jetzt*.

### AP-86 ☐ Lokale Modelle auf dem Mac laden · S–M (neu, Fehler)
**Befund (10.11.2026)**: Auf einem Mac mit Apple Silicon (39 GB, Metal) scheitert jeder Download eines lokalen Modells
(Qwen 3.5 9B im Postfach, Qwen 3.6 35B A3B unter *Lokale Modelle*) mit „Kein Paket für macOS im llama.cpp-Release
gefunden.“ Es scheitert nicht das Modell, sondern das Programm llama.cpp davor: `LlamaHost.installRuntime` sucht im
festen Release `b11870` (und ersatzweise im neuesten) ein Paket, dessen Name auf `bin-macos-arm64.zip` endet – das gibt
es dort offenbar nicht (mehr) unter diesem Namen bzw. als ZIP. Die Meldung führt zudem in die Irre: „Im Firmennetz
gesperrt?“ passt nicht, „Datei einspielen“ erwartet eine Windows-ZIP, und am Satzende steht ein doppelter Punkt.
**Ziel**: Auf macOS (Apple Silicon und Intel) und Linux richtet sich llama.cpp beim ersten Modell von selbst ein.
- Paketsuche robust: tatsächliche Namen und Formate der Releases prüfen (ZIP und `.tar.gz`), nach Betriebssystem und
  Architektur wählen statt nach festem Namensende; das feste Release so wählen, dass es Pakete für alle Plattformen hat.
- Auf dem Mac: Programm nach dem Entpacken ausführbar machen und die Quarantäne-Markierung entfernen
  (`com.apple.quarantine`), damit Gatekeeper `llama-server` nicht blockiert; Metal wird genutzt.
- *Datei einspielen* nimmt auch das Mac-/Linux-Paket an und nennt den richtigen Dateinamen.
- Meldung nach AP-74: sagt, was fehlt (das Programm, nicht das Modell), mit Knopf *Erneut versuchen*; der Hinweis aufs
  Firmennetz nur, wenn der Download wirklich blockiert wurde. Doppelten Punkt beheben.
- Nebenbei: auf dem Mac nicht „PC“, sondern „Mac“ anzeigen.
**Fertig, wenn**: auf einem Mac mit Apple Silicon Qwen 3.5 9B ohne Handarbeit lädt, startet und im Postfach antwortet,
und ein Test die Paketwahl für Windows, macOS (arm64, x64) und Linux gegen eine echte Release-Liste prüft.

---

## Später

Aus den Ideen vom 10.11.2026 übernommen.

### AP-87 ☐ Datei-Erste-Hilfe · M (neu)
**Ziel**: Beschädigte Word-, Excel-, PowerPoint- und PDF-Dateien öffnen und so viel Inhalt wie möglich retten. Lora
erkennt beim Öffnen, dass eine Datei kaputt ist, und bietet *Retten* an: defekte ZIP-Container der Office-Formate
reparieren, lesbare Teile (Text, Tabellen, Bilder, Folien) herausholen, PDF-Querverweistabelle neu aufbauen. Das Ergebnis
ist eine neue Datei neben dem Original („… (gerettet)“), dazu eine Liste, was fehlt. Das Original bleibt unverändert.
**Fertig, wenn**: abgeschnittene und teilweise überschriebene Testdateien jedes Formats mindestens ihren Text und ihre
Bilder zurückgeben und Lora klar sagt, was verloren ist.

### AP-88 ☐ Zeitreise für Dateien · M (neu)
**Ziel**: Beim Speichern in Lora-Werkzeugen (Dokument, Tabelle, Folien, Gestalten, PDF) entsteht still ein Schnappschuss.
Unter *Frühere Stände* lassen sich Stände nebeneinander vergleichen (Text mit Wortvergleich, Tabellen nach Zellen,
Folien als Bilder) und zurückholen – als Ersatz oder als Kopie. Platz begrenzt (dichte Stände für die letzten Tage, danach
ausgedünnt; Obergrenze in *Einstellungen › Daten*); Tresor-Dateien bleiben verschlüsselt. Lora kennt die Stände („Wie
sah die Einleitung gestern aus?“).
**Fertig, wenn**: nach mehreren Speichervorgängen jeder Stand vergleichbar und wiederherstellbar ist, ohne dass der
Speicher unbegrenzt wächst.

### AP-89 ☐ Vorlage aus eigener Datei · S–M (neu)
**Ziel**: Aus einem fertigen Dokument (Word, Folien, Tabelle, Design) wird eine wiederverwendbare Vorlage: Lora schlägt
Platzhalter vor (Namen, Daten, Titel, Beträge, Bilder), man bestätigt oder ändert sie, die Vorlage landet unter *Erstellen
› Meine Vorlagen* und lässt sich wie die TU Vorlagen ausfüllen – auch per Lora („Mach mir wieder so ein Protokoll“).
Formatierung, Kopf- und Fußzeilen und Logos bleiben unverändert.
**Fertig, wenn**: aus einem echten Protokoll, einem Folienvortrag und einer Excel-Liste je eine Vorlage entsteht, die
sich mit neuen Angaben richtig füllt.

Aus der zweiten Ideensammlung vom 10.11.2026 übernommen (AP-90 bis AP-102). Jedes Werkzeug liefert seine Lora-Aktionen
über die gemeinsame Schnittstelle (AP-38) und fügt sich in AP-02 ein (Schalter bzw. Teil von *Dateien* oder
*PDF-Werkzeugkasten*). Platzbedarf für Modelle (Hintergrund entfernen, Tabellen- und Feldererkennung) über die
nachladbaren Erweiterungen (AP-25).

**Dateien umwandeln und reparieren**

### AP-90 ☐ Formatwandler mit Verlustanzeige · M (neu)
**Ziel**: Dateien zwischen gängigen Formaten umwandeln (DOCX ↔ ODT/RTF/PDF/Markdown, PPTX ↔ ODP/PDF, XLSX ↔ ODS/CSV,
Bilder untereinander), aus *Dateien* per Rechtsklick und per Lora („mach daraus eine ODT“). **Vorher** zeigt eine Karte,
was dabei verloren geht oder sich ändert – Formeln (nur Werte), Animationen und Übergänge, Kommentare und Änderungsspuren,
Makros, mehrere Tabellenblätter bei CSV, nicht eingebettete Schriften – mit *Trotzdem umwandeln* oder einer besseren
Alternative. Das Original bleibt immer erhalten.
**Fertig, wenn**: jede angebotene Richtung mit Testdateien richtig umwandelt und die Verlustanzeige für Formeln,
Animationen, Kommentare und Blätter stimmt.

### AP-91 ☐ Schriften-Notfall · S–M (neu)
**Ziel**: Beim Öffnen von Word-, PowerPoint- und PDF-Dateien erkennen, welche Schriften fehlen; Lora schlägt einen
passenden Ersatz vor (metrisch gleich, z. B. Carlito für Calibri, sonst ähnlich), zeigt die Auswirkung (Seitenzahl,
Umbrüche) und kann Schriften beim Speichern einbetten, damit die Datei auf anderen Rechnern gleich aussieht. Die
Schriften des TU-Designs werden erkannt.
**Fertig, wenn**: eine Datei mit fehlender Schrift den Hinweis zeigt, der Ersatz das Layout hält und die gespeicherte
Datei mit eingebetteter Schrift auf einem Rechner ohne diese Schrift gleich aussieht.

### AP-92 ☐ Formatierung aufräumen · M (neu)
**Ziel**: Word-Dateien voller direkter Formatierung (fett und 14 pt statt „Überschrift 1“, Leerzeilen statt Abstand,
getippte Nummern statt Listen) in saubere Formatvorlagen überführen. Lora erkennt die Muster, zeigt die Zuordnung („32
Absätze → Überschrift 2“), man bestätigt; danach funktionieren Gliederung, Inhaltsverzeichnis und Hausstil (AP-46,
AP-79). Vorschau und Rückgängig.
**Fertig, wenn**: eine typisch „verbastelte“ Abschlussarbeit danach eine richtige Gliederung hat, ohne dass sich das
Aussehen ungewollt ändert.

### AP-93 ☐ CSV-Lotse · S (neu)
**Ziel**: Beim Öffnen einer CSV- oder Textdatei erkennt die Tabelle Kodierung (UTF-8, Windows-1252, kaputte Umlaute wie
„Ã¤“), Trennzeichen, Dezimalzeichen, Tausenderpunkte und Datumsformate selbst und zeigt eine kleine Vorschau mit den
erkannten Einstellungen zum Anpassen. Beim Speichern als CSV wählbar: Excel-tauglich (Semikolon, mit BOM) oder
Standard (Komma, UTF-8). Gilt auch für Exporte aus OPAL, LimeSurvey und Selma.
**Fertig, wenn**: typische Problemdateien (Excel-Export, Umfrage-Export, falsch kodierte Datei) ohne Handarbeit richtig
in Spalten, Zahlen und Daten ankommen.

### AP-94 ☐ Formular aus Scan · M (neu)
**Ziel**: Ein gescanntes oder fotografiertes Papierformular (Scanner AP-62) wird zu einem ausfüllbaren PDF: Linien,
Kästchen und Tabellenzellen werden als Felder erkannt (Text, Ankreuzfeld, Datum, Unterschrift), mit Namen aus der
Beschriftung daneben; man prüft und korrigiert die Felder im PDF-Werkzeugkasten. Danach lässt es sich wie jedes Formular
ausfüllen – auch von Lora.
**Fertig, wenn**: drei echte Papierformulare der Verwaltung als ausfüllbare PDFs mit richtig platzierten und benannten
Feldern herauskommen.

### AP-95 ☐ PDF-Tabellen nach Excel · M (neu)
**Ziel**: Tabellen aus PDFs zuverlässig in die Tabelle (AP-48) bzw. nach Excel übernehmen – auch über mehrere Seiten
(wiederholte Kopfzeilen nur einmal), mit verbundenen Zellen, Zahlen als Zahlen (deutsches Format) und aus gescannten
PDFs über die Texterkennung. Prüfansicht: PDF-Seite links, erkannte Tabelle rechts, Zellen zum Nachbessern.
**Fertig, wenn**: Tabellen aus Statistik-Berichten, Haushaltsplänen und Papers (auch gescannt) mit richtigen Spalten und
Zahlen ankommen.

### AP-96 ☐ Diagramm zu Daten · M (neu)
**Ziel**: Aus dem Bild eines Diagramms (Ausschnitt aus einem Paper, Foto, Scan) die Werte als Tabelle zurückgewinnen:
Achsen und Skalen erkennen (auch logarithmisch), Datenreihen nach Farbe trennen, Punkte, Linien und Balken auslesen.
Halbautomatisch: Lora schlägt vor, man korrigiert Achsenpunkte und Reihen per Klick. Ergebnis in die Tabelle, auf Wunsch
gleich als neues Diagramm (AP-49); die Quelle wird vermerkt.
**Fertig, wenn**: Balken-, Linien- und Punktdiagramme aus Testbildern Werte mit höchstens wenigen Prozent Abweichung
liefern.

**Drucken**

### AP-97 ☐ Druck-Lotse · S–M (neu, D)
**Ziel**: Druckfertig machen aus Dokument, Folien, Gestalten und PDF: Broschüre mit Rückenheftung (Seiten ausschießen,
auf ein Vielfaches von 4 auffüllen), doppelseitig mit richtigen Rändern, mehrere Seiten pro Blatt, Handouts aus Folien,
Beschnittzugabe und Schnittmarken für die Druckerei. Prüft die Vorgaben vor dem Export (Auflösung der Bilder, Farbraum,
eingebettete Schriften – AP-91) und nennt Probleme.
**Fertig, wenn**: eine A5-Broschüre auf A4 gedruckt und gefaltet in der richtigen Reihenfolge liegt und ein Poster mit
Beschnitt die Prüfung einer Druckerei besteht.

**Schutz und Archive**

### AP-98 ☐ Passwortschutz für Dateien · S–M (neu)
**Ziel**: Für Word-, Excel-, PowerPoint-, PDF- und ZIP-Dateien ein Passwort setzen, ändern oder (mit bekanntem Passwort)
entfernen – aus *Dateien*, beim Speichern und beim Versand (Outlook). Moderne Verschlüsselung (Office: Agile Encryption,
PDF: AES-256, ZIP: AES); Hinweis, das Passwort auf anderem Weg weiterzugeben. Passwortgeschützte Dateien lassen sich nach
Eingabe in allen Werkzeugen öffnen. Kein Knacken fremder Passwörter.
**Fertig, wenn**: geschützte Dateien in Word, Excel, PowerPoint, Acrobat und 7-Zip/Windows mit dem Passwort aufgehen und
ohne nicht.

### AP-99 ☐ Archiv-Werkzeug · S–M (neu)
**Ziel**: ZIP-Dateien (dazu 7z, TAR/GZ zum Lesen) in *Dateien* wie Ordner öffnen, durchsuchen, einzelne Dateien in der
Schnellansicht (AP-66) oder im passenden Werkzeug öffnen, ohne alles zu entpacken. Packen mit Wahl der Stärke, Passwort
(AP-98) und **Aufteilen in Teile** (z. B. 20 MB für Mails); Lora kann „Frag diesen Ordner“ (AP-58) auch auf ein Archiv
anwenden.
**Fertig, wenn**: ein großes Archiv mit Unterordnern durchsucht und geöffnet werden kann und ein großer Ordner in Teile
gepackt und unter Windows/7-Zip wieder zusammengesetzt wird.

**Bilder**

### AP-100 ☐ Fotos vom Handy aufbereiten · S–M (neu)
**Ziel**: Fotos (auch vom iPhone) in einem Schritt aufbereiten: HEIC/HEIF → JPG, nach EXIF richtig drehen, nach
Aufnahmedatum benennen („2026-11-10_14-32_Exkursion.jpg“), Ortsdaten und andere Metadaten entfernen, auf Wunsch
verkleinern. Als Stapel in *Dateien* und per Lora („bereite die Exkursionsfotos auf“). Originale bleiben, bis man sie
selbst löscht.
**Fertig, wenn**: ein Ordner gemischter iPhone- und Android-Fotos richtig gedreht, benannt, als JPG und ohne Ortsdaten
herauskommt.

### AP-101 ☐ Bild-Werkstatt · M (neu)
**Ziel**: Einfache Bildbearbeitung ohne Fremdprogramm: **Hintergrund entfernen** (Modell auf dem Gerät, nachladbar),
zuschneiden (frei, Seitenverhältnisse, Vorlagen für Profilbild, Folie, Poster), drehen, auf Zielgröße bzw. Dateigröße
bringen, Format wählen (JPG, PNG mit Transparenz, WebP), Helligkeit/Kontrast. Aus *Dateien*, Gestalten und Folien
erreichbar; Lora kann es („Hintergrund weg, quadratisch, unter 1 MB“).
**Fertig, wenn**: Porträts und Produktfotos sauber freigestellt werden und Zuschnitt und Zielgröße stimmen.

**Umzug**

### AP-102 ☐ Umzug auf einen neuen PC · M (neu, D)
**Ziel**: Ein Assistent packt alles, was zu Lora gehört – Chats, Projekte, Einstellungen, Gedächtnis, Vorlagen und
Designs, Erweiterungen, auf Wunsch lokale Modelle und den Tresor (verschlüsselt) –, in ein Umzugspaket (Datei, USB oder
Netzlaufwerk) und spielt es auf dem neuen PC ein; vorher zeigt er Größe und Inhalt. API-Schlüssel gehen nur mit Passwort
mit. Ergänzt die bestehende Sicherung und die Synchronisierung über den Cloud-Ordner, ersetzt sie nicht.
**Fertig, wenn**: nach dem Einspielen auf einem frischen PC alles wie vorher dasteht, einschließlich lokaler Modelle, und
nichts doppelt angelegt wird.

---

## Ideen

Zurzeit leer. Aus den Sammlungen vom 10.11.2026 sind AP-87 bis AP-102 geworden; alles Übrige und die ältere Ideenliste
sind gestrichen (Git-Historie dieser Datei). Neue Ideen je eine Zeile, als Paket ab AP-103.

---

## Erledigt

Runde 1 in Kürze; Details in [`ARBEITSPLAN-RUNDE-1.md`](ARBEITSPLAN-RUNDE-1.md).

| Paket | Ergebnis | Rest |
| --- | --- | --- |
| ☑ Screenshots aus der Zwischenablage (W1-01) | *Aus der Zwischenablage* im „+“-Menü, Strg+V | Gboard-Leiste → Ideen |
| ☑ Absätze im Chat auf Windows (W1-02) | Umschalt+Enter in Chat, *KI fragen*, Dokument-Assistent | – |
| ☑ Zoom mit Strg + / − / 0 (W1-03) | 80–200 %, Strg+Mausrad, Regler unter *Darstellung* | – |
| ☑ Projektordner auf Android (W1-04) | Ordner aus dem Systemdialog, Lesen über `list_project_files`/`read_project_file` | Schreiben in den Ordner nur unter Windows |
| ☑ Sprachmodus beschleunigen (W1-05) | Vorlesen ab dem ersten Satz (`audio/SpeechStream.kt`), eigenes Modell, Pause 0,8 s, Messwerte im Agent-Protokoll | Messung auf Geräten |
| ☑ Problem melden und „Was ist neu“ (W1-06) | *Einstellungen › Problem melden*, Dialog nach Updates | – |
| ☑ AP-01 Chat-Design und Logo | ADEU-Stil ohne graue Kästen, Domain- und Quellenmarken, neue Lora-Arbeitskarte, Logo „Lora liest“ mit Startanimation und Symbolen | Vorher-Bilder für den PR |
| ☑ AP-02 Übersicht | Seitenleiste mit „Mehr“, *Einstellungen › Werkzeuge*, Kacheln, Zuletzt/Favoriten, „TU Vorlagen“ | erste Werkzeuge mit AP-10/11 |
| ☑ AP-03 Pseudonymisierung | öffentliche Quellen, Autor:innen, Ein-Klick-Korrektur, Schulen/Orte, lokales Modell, Testsammlung | Tresor-Standard mit AP-10 |
| ☑ AP-04 Arbeitsspeicher (W) | JVM-Grenzen, schlanke Chat-Liste, Entladen, Messpunkte unter *Einstellungen › Arbeitsspeicher* | Messung auf Windows |
| ☑ AP-05 Modus-Rahmen | `ChatModes` mit Chip, Erklärzeile, Beispiel; `/`, Strg+K, Startseite | Lora-Aufruf mit AP-07 |
| ☑ AP-06 Rückfragen | `ask_user` mit Antwortknöpfen, Planungsmodus, TU Vorlagen als Fragerunde | – (Lora fragt seit AP-07) |
| ☑ AP-07 Lora-Navigator | Landkarte der App, lokale Suche, LoraAgent mit Werkzeugen, Leuchtrahmen, Einstiege, *Einstellungen › Lora* | Leuchtrahmen für weitere Optionen |
| ☑ AP-08 Belegpflicht | Quellenmodus, Prüfung jedes Zitats und jeder Seite auf dem Gerät, Belegprotokoll, Klick öffnet die Stelle | eigene Ansicht für Stellen der Wissenssammlung |
| ☑ AP-09 Exzerpt-Agent | Schemata je Textart, `save_excerpt` mit Seitenprüfung, Exzerpt am Literatureintrag, Tabelle | große Stapel mit AP-20 |
| ☑ AP-10 Tresor | PIN, Wiederherstellungsschlüssel, AES-256 mit Keystore/DPAPI, nur Uni/lokal, strenge Pseudonymisierung | Tresor-Projekte, Sync mit AP-12 |
| ☑ AP-11 PDF Stufe 1 | Tabs, Seiten, Markieren, Kommentare, Formulare ausfüllbar (Erkennung, Editor, Agent) | Einrasten am Text beim Markieren |
| ☑ AP-33 Umbenennung in Lora | sichtbarer Name und Slogan, saubere Übergabe (DeepSeek als Standard), Chat-Titel, Tabellenhinweis | technische Namen mit Migration (Ideen) |
| ☑ AP-34 Lora-Panel | Seitenleiste/Blatt, Kontext-Vorschläge, Ergebnis-Karten mit Rückgängig, Strg+L, Android-Verknüpfung | – |
| ☑ AP-35 Lora im Chat | nächste Schritte (Kalender, Excel, Literatur, Word), Chat-Suche, Erinnerungen, Hinweis beim Tippen, Schrittzeilen | – |
| ☑ AP-36 Animationen | Schere/Stempel/Tacker korrigiert, 16 neue Aktionen, Flug zum Ziel, Augen folgen dem Zeiger | – |
| ☑ AP-37 Lora leichter finden | Gesicht in Nahaufnahme, „Frag Lora“-Pille, schlafende Lora als runder Knopf, Chips mit konkreten Starts, *Frag Lora …*-Feld entfernt | – |
| ☑ AP-12 Einfacher Sync (W) | Cloud-Ordner per Klick, Notiz je PC, Warten vor dem Laden, Konfliktkopien behalten, Schlüssel bleiben auf dem PC | Tresor-Projekte im Sync prüfen; Android mit AP-28 |
| ☑ AP-17 Lernspiele | Werkzeug (Standard aus) mit Modus, Agent-Werkzeugen, Sammlung, Versionen, Spielen, Export HTML/ZIP, CSP-Abschottung | SCORM/H5P; OPAL-Einbindung klären |
| ☑ AP-19 KI-Nutzungsnachweis | Pop-up nur bei Text für eine Arbeit, drei Formate, Chat oder Projekt, Chat-Menü | offizielle TUD-Vorlage einpflegen, sobald vorhanden |
| ☑ AP-14 Browser neben dem Chat (W) | JCEF-Panel mit Tabs, Frage zur Seite (Quellenmodus), Zusammenfassen, Literatur, Wissenssammlung, PDF | Android (WebView); Dialoge über dem Browser |
| ☑ AP-13 Mail und Kalender (W) | COM über JNA, Modus *Postfach*, 12 Werkzeuge, Regeln mit Vorschau, Protokoll, nie löschen/senden | echte Outlook-Regeln; Server-Weg mit AP-27 |
| ☑ AP-15 PDF Stufe 2 | Texterkennung von Windows bzw. Bildmodell, Umwandeln (Office über COM), Unterschreiben, Frag das PDF, Tabellen | digitale Signatur; Handschrift-OCR auf dem PC |
| ☑ AP-18 Dateiverwaltung | *Dateien*: Orte und Ordner, Schlagwörter, Volltextsuche mit Cache, Vergleich, `search_files` | SQLite-Index nur bei Bedarf; Dateien verschieben (mit AP-16 erledigt) |
| ☑ AP-24 Kreativbereich → „Erstellen“ | Erstellen mit Lora-Feld, Gestalten (9 Vorlagen nach dem CD-Paket 2025 mit Shape, TUD-Icons und allen Logos, Editor mit Ebenen, Einrasten, Drehen, Rückgängig), Meine Designs, Export PNG/PDF (Druck mit Beschnitt)/PowerPoint, KI in jedem Chat | Text auf der Leinwand tippen, PowerPoint-Import |
| ☑ AP-25 Videoeditor und Erweiterungen | Erweiterungen über den Updater (Videoeditor-Paket, Browser-Engine entfernbar), Zeitleiste, TUD-Titel, Ton, Untertitel aus der Transkription, Rohschnitt, Mit Lora kürzen, MP4 für OPAL/YouTube | Musik und Übergänge in der Vorschau, zweite Videospur, Farbkorrektur |
| ☑ Seitenleiste ausblendbar (Nachtrag AP-02) | jeder Bereich außer Erstellen und Dateien per Rechtsklick oder in den Einstellungen ausblendbar, über Strg+K und Lora erreichbar | – |
| ☑ AP-16 Office-Ausbau | Tab *Dateien* wie Google Drive, Lora-Ordner (`.lora`), echte docx/pptx/xlsx, ins Original schreiben, Änderungen nachverfolgen, Word-Kommentare, Fußzeile/Ränder/Inhaltsverzeichnis/Formeln, Rechtschreibung auf dem Gerät, freie Folien mit Masterfolie, Lora-Panel und -Popup, Chatfilter | echter Seitenumbruch in der Seitenansicht; Windows-Papierkorb; Scannen (Ideen) |
| ☑ AP-20 Stapel und Massenkontrolle | Warteschlange im Hintergrund (Ordner, ZIP, Mehrfachauswahl, Nextcloud) mit Pause/Fortsetzen über Neustarts, Vorrang für Chats, nachts; Massenkontrolle mit Raster, Kalibrierung, Belegprüfung, Ausreißern, Entscheidung mit Protokoll, Excel/Word-Exporten | Exzerpt-Stapel an Literatureinträge; OPAL-Format klären |
| ☑ AP-21 Antrags-Werkstatt | Modus *Antrag*, Förderer-Vorlagen (DFG, BMBF, EU, SAB, TUD), Checkliste, Fristen als Kalenderdatei, Gliederung mit Zeichen-/Seitenwächter, Gantt, Budget mit Pauschale, DMP, Ethik, Probelauf, Abgabe-Check, Word-Export | Förderer-Vorlagen befüllen, Personalmittelsätze |
| ☑ AP-22 Nextcloud | WebDAV auf beiden Plattformen, Anmeldung im Browser (Login Flow v2), Datei-Browser, Anhängen, Holen, Speichern, Agent-Werkzeuge | Android-Sync (AP-28), Freigabelinks |
| ☑ AP-26 PDF Stufe 3 | Schwärzen (Seiten als Bild), Text/Bilder ändern, Verkleinern, PDF/A-2b, Passwort, Metadaten, PDF/UA-Prüfung mit Behebung, Vergleichen | Inhaltsstrom bearbeiten, Tags erzeugen |
| ☑ AP-27 Mail über den Server | IMAP mit derselben Schnittstelle wie Outlook, Entwürfe statt Senden, Verschieben nur sicher, Android | Exchange mit Kalender (EWS/Graph) |
| ☑ AP-28 Sync für Android | Handy spiegelt den Lora-Ordner des PCs über Nextcloud/WebDAV mit den Regeln von AP-12 | Literatur/Wissen, Hintergrund-Sync |
| ☑ AP-23 MCP-Client | Streamable HTTP mit Token/OAuth (PKCE), stdio unter Windows, *Einstellungen › Verbindungen*, Erlauben/Fragen/Sperren je Werkzeug, Datenschutzstufe | Ressourcen, Prompts, Elicitation |
| ☑ AP-29 Englisch | ganze Oberfläche übersetzt (`en.tsv`), Pfade Teil für Teil, Lora versteht Englisch, Test für jeden Text | Inhalte der Dokumentvorlagen |
| ☑ AP-30 Barrierefreiheit | Beschriftungen, Überschriften, Schalter/Auswahl für Screenreader, sichtbarer Fokus, große Schrift, Access Bridge (NVDA/JAWS) | Test mit NVDA |
| ☑ AP-31 macOS und Linux | DMG/tar.gz (Apple Silicon), tar.gz/.deb (Linux), Updater, Keychain, Autostart, ⌘, ffmpeg vom System | Notarisierung, Intel-Macs |
| ☑ Lora-Knöpfe (Nachtrag AP-37) | Gesicht in „Frag Lora“ und der schlafenden Lora mittig statt am Rand, nichts abgeschnitten | – |

---

### Runde 2 – erledigt

| Paket | Ergebnis | Rest |
| --- | --- | --- |
| ☑ AP-38 Lora handelt in Werkzeugen | Register der Aktionen mit Vorschau und Rückgängig in Dateien, PDF, Dokument, Tabelle, Folien und Video, Datenschutz, Protokoll | neue Editoren (AP-44, AP-48, AP-51, AP-52) liefern ihre Aktionen über dieselbe Schnittstelle |
| ☑ AP-39 Ein Gedächtnis für Lora | Vorlieben je Werkzeug nur nach Rückfrage, als Standard der Aktionen, *Einstellungen › Lora › Gedächtnis* | – |
| ☑ AP-40 Abläufe über Werkzeuge | Plan-Karte, Ausführung im Hintergrund, Pause/Wiederholen, Fortsetzen nach Neustart | eigene gespeicherte Abläufe (Ideen) |
| ☑ AP-41 Leuchtrahmen überall und Touren | Rahmen für alle Schalter und wichtigen Knöpfe, Touren als Daten mit Angebot beim ersten Öffnen | Touren für die neuen Editoren mit ihnen |
| ☑ AP-42 Lora-Dateidialog | eigener Dialog für Öffnen/Speichern/Ordner, Umbenennen und Löschen | Android behält die Systemauswahl |
| ☑ AP-43 Gemeinsamer Text-Kern | Fließtext mit Zeichen-/Absatzformat, eigene Seitenaufteilung (Witwen/Waisen, Tabellen zeilenweise mit wiederholter Kopfzeile, Abschnitte), Zwischenablage HTML/RTF, 200+ Seiten flüssig | Bilder umflossen; PDF über HTML statt aus derselben Engine (Offene Frage 8: so belassen) |
| ☑ AP-44 Fließtext-Editor | Editor wie Docs mit Lineal (Ränder, Einzüge, Tabstopps), Suchen/Ersetzen, Kommentare am Rand, Änderungen im Text, Markieren über Tabellen hinweg | – |
| ☑ AP-45 Word-Treue | Lesen/Schreiben von Formaten, Formatvorlagen, Kopf-/Fußzeilen (erste/gerade Seite, Abschnitte), Tabstopps; ins Original schreiben; Prüfsammlung mit nachgebauten TUD-Dokumenten | Bildvergleich der Seiten mit Word |
| ☑ AP-46 Gliederung und Formatvorlagen | Gliederung zum Ziehen, Formatvorlagen und Vorlagensätze, Format übertragen | – |
| ☑ AP-47 Lora schreibt im Dokument | Hilf mir schreiben, Überarbeiten als Vorschläge, Menü über der Markierung | – |
| ☑ AP-48 Tabellen wie Excel | rund 45 neue Funktionen, ganze Spalten, Platzhalter, Hilfe beim Tippen, Suchen/Ersetzen, Zellen verbinden, Fixieren, Auswahllisten, Ausfüllkästchen, 100 000 Zeilen flüssig, treues .xlsx (geteilte Formeln, bedingte Formatierung), Formatierung innerhalb einer Zelle, echte Excel-Diagramme | – |
| ☑ AP-49 Diagramme und Lora rechnet | acht Diagrammarten in TUD-Farben, verknüpft in Dokumenten und Folien, Lora rechnet (Verteilung, Kennzahlen je Gruppe, Formeln erklären, Bereinigen), echte Diagramme mit Daten in Word und PowerPoint | Boxplot in Office nur gezeichnet bzw. als Tabelle |
| ☑ AP-50 Gemeinsame Leinwand | ein Leinwand-Kern für Folien und Gestalten (Drehen, Einrasten, Mehrfachauswahl, Gruppen, TUD-Shape, Symbole, QR), Text mit Formatierung auf der Leinwand tippen, Tabellen, Verläufe, Bildfilter | Android: Symbole/QR als Platzhalter, kein Gestalten-Editor; Listen in Textfeldern |
| ☑ AP-51 Folien wie PowerPoint | PptxReader (Master, Layouts, Platzhalter, Designfarben, Gruppen, Diagramme), Unbekanntes bleibt beim Speichern, Übergänge und Aufbau, Zeichnen, zweiter Bildschirm, Deck aus Dokument oder Paper, „Folie schöner machen“, Sprechertexte | Prüfung mit der echten TUD-Vorlage; Folien in fremden Decks im TUD-Design |
| ☑ AP-52 Video: mehrere Spuren und echte Vorschau | Video- und Tonspuren beliebig viele, Position/Größe/Zuschnitt/Deckkraft je Clip, Bild-in-Bild mit Ecken, Vorschau durch denselben ffmpeg-Graphen wie der Export (J/K/L, Schleife In–Out), Wellenformen, Lora-Aktion *Bild-in-Bild* | Lautheit angleichen nur im Export; Flüssigkeit auf den Dienst-PCs messen |
| ☑ AP-53 Video: Schnittwerkzeuge | Medienpool mit Ordnern und Suche, Quellmonitor mit In/Out, Einfügen/Überschreiben/Ziehen, Klinge, Ripple/Roll/Slip, Magnet, verknüpfter Ton, Synchronisieren über die Wellenform, Spuren sperren/stumm/ausblenden, Marker als Kapitel, Tasten wie Resolve | J spielt rückwärts ohne Ton; Synchronisieren vergleicht die ersten 15 Minuten |
| ☑ AP-54 Video: einfache Effekte | Geschwindigkeit, Standbild, Rückwärts, Bildkorrektur mit Weißabgleich, Zoom/Schwenk mit Keyframes, Hochformat, Bereiche verpixeln, Gesichter automatisch (YuNet), Stimme verfremden, Lora-Aktionen | Effekte auf V2 …; Bereiche mit der Maus ziehen; Prüfung auf Dienst-PCs |
| ☑ AP-58 Frag diesen Ordner | Ordnermenü in *Dateien*, Lesen mit Fortschritt aus dem Volltext-Cache, Chat im Quellenmodus mit `search_folder` (Datei + Seite), Belegprüfung je Datei, Datenschutz-Empfehlung | Nextcloud- und Projektordner direkt |
| ☑ AP-55 Video: textbasierter Schnitt | Transkript neben der Zeitleiste, Klick springt, Wörter löschen schneidet (Clip geteilt, Ton mit, Magnet), Absätze verschieben, Füllwörter/Pausen, Lora-Vorschläge zum Annehmen/Ablehnen | Lora schlägt absatzweise vor; Ziehen markiert innerhalb eines Absatzes |
| ☑ AP-56 Bildschirmaufnahme | Bildschirm/Fenster/Bereich + Webcam + Mikrofon + Systemton in einem ffmpeg-Prozess, Pause als Teile, Klicks als Ringe, Leiste außerhalb der Aufnahme, Strg+Umschalt+F9/F10, Projekt mit getrennten Spuren, sofort transkribieren, Knopf in der Referentenansicht | Klicks nur unter Windows; Systemton braucht ein Loopback-Gerät; Prüfung auf Dienst-PCs |
| ☑ AP-57 Video: Export mit GPU und Warteschlange | NVENC/Quick Sync/AMF/VideoToolbox mit Probe und Rückfall, Warteschlange im Hintergrund, mehrere Ziele (OPAL, YouTube, Projekt, Ton, Untertitel, Kapitel), Nextcloud gestreamt | Zeitmessung mit 60-Minuten-Video auf einem PC mit GPU; Warteschlange übersteht keinen Neustart |
| ☑ AP-59 Windows-Integration | Ziehen nach außen (Griff, Mehrfachauswahl), Als Datei kopieren, Kontextmenü je Benutzer (Datei, Ordner), Start mit Datei an die laufende App, Windows-Papierkorb, Office öffnen und zurück mit „in Word geöffnet“ | Offene Frage 7 (Dienst-PCs); neues Windows-11-Menü nur über „Weitere Optionen“ |
| ☑ AP-60 PDF-Reste | Markieren am Text, Schwärzen mit der Maus, digitale Signatur (Windows-Speicher, .p12/.pfx, sichtbares Feld) mit Prüfung, Tags für PDF/UA | Tabellen beim Taggen; Zeitstempel/LTV der Signatur; Android |
| ☑ AP-61 Diagramme und Whiteboard | Flussdiagramm, Mindmap, Organigramm, Zeitstrahl mit haftenden Verbindern und Anordnung, Mermaid hin und zurück, Lora zeichnet, Whiteboard (Notizen, Freihand), in Dokument, PDF, Word, Folien | Verbinder auf der Gestalten-Leinwand; Word-Bild nur unter Windows |
| ☑ AP-62 Dokumenten-Scanner | Kanten finden, Ecken ziehen, Entzerren (A4), Farbe/Graustufen/Schwarz-Weiß mit Schattenausgleich, mehrere Seiten, PDF mit Textebene in *Dateien › Scans*; Kamera und Fotos (Android), WIA-Scanner und Bilder (Windows), Handy als Scanner über den Sync, Tour | OCR auf Android nur über ein Bildmodell; Einzug (ADF) mehrseitig im WIA-Dialog einzeln |
| ☑ AP-63 Sync- und Android-Reste | Literatur, Wissenssammlung und Tresor im Handy-Sync mit Neuladen, Hintergrund-Sync (WorkManager, WLAN-Schalter), PIN-Kiste je Gerät im Tresor, Tresor-Konflikte als zweite Fassung, Browser neben dem Chat auf Android (WebView) | Browser ohne Tabs; Tresor-Fehlversuche zählen für alle Geräte gemeinsam |
| ☑ AP-64 Android zieht nach | Folien auf dem zweiten Bildschirm (Presentation), Wischen, Bildschirm bleibt an, Speichern in den Lora-Ordner, Verkleinern (Werkzeugkasten und Lora-Aktion), Tour für den Scanner | Rest der PDF-Stufe 3 (Schwärzen, PDF/A …) bleibt am PC; Touren für die Editoren selbst |
| ☑ AP-65 Diktat | 🎤 im Dokument-Editor und an den Sprechernotizen, Erkennung auf dem Gerät (sherpa-onnx), Satzzeichen und Absätze per Sprache (de/en), Abstand und Großschreibung am Cursor | Diktat in weiteren Textfeldern; Sprechen ohne Stopp-Knopf (Pausenerkennung) |
| ☑ AP-66 Schnellansicht in Dateien | Leertaste über der Datei unter der Maus bzw. der ausgewählten, ←/→ blättert, Enter öffnet; Details-Spalte (Art, Größe, Daten, Ort, Seiten/Wörter/Folien/Abmessungen, Schlagwörter, Chat); Android über ⋮ › Vorschau | Vorschau für Video/Ton; Details auch in den Suchergebnissen |
| ☑ AP-68 Postfach nur mit lokalem Modell | Mail-Werkzeuge und `mail.*`-Aktionen nur für Modelle auf dem Gerät (eine Sperre, letzte Prüfung im `LlmClient`), Postfach-Modus fest lokal, Lora gibt Mail-Aufträge weiter, Übergabe an API-Chats nur als geprüfte Karte, Modellvorschlag ohne lokales Modell | Modellvorgabe nach Messung (Offene Frage 10) |
| ☑ AP-69 Mailansicht und Lora sortiert selbst | drei Spalten mit Ordnerbaum, Liste seitenweise (5 000+ Mails), Lesebereich, Suche, Mehrfachauswahl, Ziehen, Rückgängig; Lora ordnet nach Satz ein (Ordner anlegen, eine Vorschau, ein Klick), Lauf im Hintergrund mit Rückgängig, `mail.sort`, Regeln und geplante Aufgabe | Probe mit echtem Outlook/IMAP; Tempo auf Dienst-PCs |
| ☑ AP-73 Rückfrage mit zwei Knöpfen | „Meinst du … oder …?“ mit den zwei wahrscheinlichsten Befehlen und „Etwas anderes“, Untergrenze nur auf dem Entwicklungsteil gewählt; Prüfteil 80,9 % richtig unter den Knöpfen, 3,6 % der freien mit Knöpfen | Android ungetestet |
| ☑ AP-72 Lora lernt aus Korrekturen | Paare *Wunsch → gemeinte Aktion* auf dem Gerät aus Knöpfen, „nein, ich meinte …“, Tipp auf die Karte und „Rückgängig“; Liste, Löschen, Export für das Training | Android ungetestet |
| ☑ AP-70 Videoeditor: ruhiges Bild, anpassbare Oberfläche | Vorschau ohne Zucken (gleicher Filter für Standbild und Wiedergabe, Scrubben ohne Leerbild, glatte Uhr), flache Chips ohne Hover-Schatten, ziehbare/einklappbare Bereiche, Anordnung gemerkt, Symbolleisten mit Tooltip, Tour-Karte neben dem Ziel | Prüfung unter Windows mit echter Skalierung |
| ☑ AP-74 Fehlermeldungen in Klartext | ein Katalog mit 30 Fehlern, je ein Satz und ein Knopf; Chat, Loras Panel und Hinweisleisten; Technik unter *Details* | – |
| ☑ AP-75 Lora-Verlauf mit „Nochmal“ | letzte 20 Wünsche, Pfeil hoch in Lora und Strg+K, „Zuletzt“ mit Nochmal/Anheften, eigene Befehle in Strg+K | – |
| ☑ AP-76 Sprachmodell-Empfehlung nach Messung | Wörter/s und erstes Wort je Modell, mit und ohne Grafikkarte; Empfehlung je Zweck; Mischmodus ab 6 Wörtern/s | Schwellen an Dienst-PCs prüfen |
| ☑ AP-77 Probelauf und „Alles rückgängig“ | „Was würde passieren, wenn …“ nur mit Vorschau; Hinweise zu Unumkehrbarem und Rat; Aktionen des Tages einzeln oder gesammelt zurücknehmen | Rückgängig hält nur bis zum Neustart |
| ☑ AP-78 Eigene Wörter und Wissenspakete | eigene Wörter in Befehlen, Suche, Strg+K und Chats; Paket „Lehramt an der TU Dresden“ (39 Einträge mit Quelle und Stand); *Inhalte zum Nachladen* | Paket am Commit `da9c3179` im Hauptzweig statt eigenem Branch |
| ☑ AP-79 Hausstil- und Barrierefreiheits-Check | Prüfen und Beheben im Dokument und in Folien mit Fundstellen; Dateibericht mit Schriften, Farben und Hausstil-Regeln | Logo und Ränder; Beheben in Word/PowerPoint-Dateien |
| ☑ AP-80 Rollenauswahl | Lehre/Forschung/Verwaltung/Studium beim ersten Start und in den Einstellungen: Werkzeuge, Vorlagen-Reihenfolge, Startvorschläge, Paket-Empfehlung | – |
| ☑ AP-81 Export-Warteschlange übersteht Neustart | unterbrochene Exporte kommen wieder, Fortsetzen per Knopf oder von selbst | – |
| ☑ AP-82 Video und Ton in der Schnellansicht | Abspielen mit Start/Pause, ±10 s, Regler, Dauer, K/J/L; PC und Android | Android ungetestet |
| ☑ AP-83 Lora-Pakete teilen | Befehle, Wörter, Vorlagen, Hausstil als eine Datei; Import mit Vorschau; Entfernen | – |
| ☑ AP-84 Lora in mehreren Fenstern (PC) | Chats, Dokumente und Lora in eigenen Fenstern, ein Chat nur in einem Fenster, eigene View-Models | Eintrag in Strg+K und der Chatsuche |
| ☑ AP-85 Große Anhänge vollständig nutzen | Grenze nach Modell, Wahl ganz/Stellen/Zusammenfassung, Anzeige am Anhang, nichts still gekürzt | – |

Die ausführlichen Beschreibungen von AP-38 bis AP-85 stehen unten.

### AP-38 ☑ Lora handelt in Werkzeugen · XL (neu, in Stufen)
Heute führt Lora zum richtigen Ort und übergibt an Chats. Neu: Lora **bedient die Werkzeuge selbst** – „Füge die beiden
PDFs zusammen“, „Räum meinen Hochgeladen-Ordner auf“, „Schneide die Pausen raus“, „Mach Abschnitt 2 zur Tabelle“.
1. **Grundlage: Werkzeug-Aktionen** (`ToolActions`): Jedes Werkzeug meldet seine Aktionen (Name, Beschreibung,
   Parameter, Risiko, Plattform) in einem Register, wie die Landkarte der App (`AppMap`). Lora bekommt daraus Werkzeuge
   nur für den Bereich, der gerade offen ist (kurze Prompts, wenige Werkzeuge).
2. **Sichtbar und umkehrbar**: Jede Aktion erscheint als Schrittzeile im Lora-Panel („Fügt 2 PDFs zusammen …“), das
   Ergebnis als Karte mit **Rückgängig**. Alles, was etwas verschiebt, löscht (Papierkorb) oder überschreibt, fragt
   vorher mit einer Vorschau (vorher → nachher). Lora bleibt im **Panel rechts**; kein eigener Lora-Cursor im Werkzeug.
3. **Stufe a – Dateien**: aufräumen (Vorschlag einer Ordnerstruktur), nach Inhalt umbenennen, verschieben, Schlagwörter
   vergeben, Duplikate finden; immer mit Vorschau und Rückgängig.
4. **Stufe b – PDF-Werkzeugkasten**: zusammenfügen, teilen, Seiten löschen/drehen/sortieren, OCR, verkleinern,
   schwärzen (nur mit Vorschau), umwandeln.
5. **Stufe c – Dokument, Tabelle, Folien**: formatieren, umstrukturieren, Tabellen einfügen, Folien anlegen – zuerst auf
   den heutigen Editoren über ihre Datenmodelle; die neuen Editoren (AP-44, AP-48, AP-51) erfüllen dieselbe Schnittstelle.
6. **Stufe d – Video**: schneiden, Titel/Bauchbinde setzen, Untertitel erzeugen, Rohschnitt, Export – auf dem heutigen
   Projektmodell; AP-52 ff. übernehmen die Aktionen.
- Datenschutz wie bisher: Datenschutzstufe, Tresor und „Nur auf diesem Gerät“ gelten auch für Aktionen; jede Aktion steht
  im Agent-Protokoll.
- **Fertig, wenn**: In jedem der vier Bereiche erledigt Lora einen Auftrag in natürlicher Sprache mit sichtbaren
  Schritten, Vorschau bei riskanten Aktionen und funktionierendem Rückgängig.
- Betrifft: neu `agent/ToolActions.kt`; `agent/LoraAgent.kt`, `data/AppMap.kt`, `ui/LoraHelper.kt`, `data/LoraDrive.kt`,
  `ui/DriveScreen.kt`, `ui/PdfToolScreen.kt`, `docs/LiveDoc.kt`, `docs/SheetEngine.kt`, `docs/SlideDeck.kt`, Windows
  `video/VideoProject.kt`, `ui/VideoScreen.kt`.
- **Umsetzung (03.11.2026, ◐)**: Register `agent/ToolActions.kt` (Aktion mit Bereich, Parametern, Risiko, Plattform;
  `LoraFocus` meldet Auswahl, offenen Ordner, offene PDFs und das offene Dokument; `ActionHosts` verbindet die Bildschirme).
  Lora bekommt die Aktionen des offenen Bereichs als Werkzeuge (`act_…`), klare Wünsche erkennt sie auf dem Gerät
  (`LoraAgent.localAction`). Panel: Schrittzeile, Vorschau vorher → nachher mit *Ausführen* bei Riskantem, Ergebnis mit
  *Rückgängig* und *Öffnen*; jede Aktion steht im App-Protokoll. **Stufe a (Dateien) ☑**: aufräumen (Typ/Semester/Monat),
  nach Inhalt umbenennen, umbenennen, verschieben, Schlagwörter, Duplikate. **Stufe b (PDF) ☑**: öffnen, zusammenfügen,
  teilen, Seiten löschen/drehen/sortieren, OCR, verkleinern, schwärzen, umwandeln. **Stufe c (Dokument) ◐**: schreiben,
  überarbeiten, Tabelle einfügen, Überschrift setzen im Fließtext-Editor; Tabellen und Folien fehlen noch (mit AP-48/AP-51).
  **Nachtrag (04.11.2026, ☑)**: **Stufe c** ergänzt um Tabellen (`agent/OfficeActions.kt`: Zellen füllen, Spalte mit
  Formel, sortieren, bereinigen mit Vorschau, Summe/Mittelwert) und Folien (anlegen, ändern inkl. Notizen, löschen mit
  Vorschau, aus Gliederung, volle Folien teilen) über `EditorDocHost` (`ActionHosts.sheet/slides`, Bereiche SHEET/SLIDES);
  **Stufe d (Video) ☑** auf dem heutigen Projektmodell (Windows `video/VideoLora.kt`: Projekt aus Aufnahme, schneiden,
  Clip entfernen, Titel/Bauchbinde, Untertitel aus der Transkription, Pausen herausschneiden, mit Lora kürzen, Export in
  *Erstellt*; bei offenem Editor über dessen Verlauf). Werkzeugübergreifend (`agent/MoreActions.kt`): transkribieren (mit
  Zeitmarken über die Transkription), Handout als Word, in die Nextcloud. **Datenschutz**: `ToolActions.privacy/model`
  – Aktionen mit Modell nur mit einem Modell, das die Stufe des Projekts erlaubt (Tresor-Chat ⇒ mindestens „Nur TU
  Dresden“, „Nur auf diesem Gerät“ ⇒ lokal), Tresor-Dateien erreichen keine Aktion, kein Nextcloud-Upload bei „Nur auf
  diesem Gerät“. **Protokoll der Lora-Aktionen** unter *Einstellungen › Lora* (`ActionLog`, `lora-aktionen.log`). Schwärzen
  geht auch ohne offenen Werkzeugkasten (neue Datei „(geschwärzt)“). Test `LoraRound2Test`. AP-44/48/51/52 übernehmen
  die Aktionen über dieselbe Schnittstelle.

### AP-39 ☑ Ein Gedächtnis für Lora · M (neu)
- Das vorhandene Gedächtnis (`agent/Memory.kt`) wird zu **„Was Lora über dich weiß“**: neben Inhalten auch
  **Vorlieben je Werkzeug** („Handouts zweispaltig“, „Videos für OPAL in 720p“, „Literaturverzeichnis in APA 7“,
  „Hochgeladenes nach Semester ordnen“).
- **Lora fragt vor dem Merken** („Soll ich mir merken, dass …?“ mit Antwortknöpfen aus AP-06); nichts wird still gemerkt.
- Sichtbar und bearbeitbar unter *Einstellungen › Lora › Gedächtnis*: gruppiert nach Werkzeug, jeder Eintrag änderbar und
  löschbar, *Alles vergessen*. Tresor-Inhalte landen nie im Gedächtnis.
- Lora und die Aktionen aus AP-38 nutzen die Vorlieben als Standardwerte (z. B. Export-Vorgabe, Ordnerstruktur).
- Wird mit dem Sync wie bisher übertragen.
- **Fertig, wenn**: Eine bestätigte Vorliebe wirkt beim nächsten passenden Auftrag ohne Nachfrage und lässt sich in den
  Einstellungen sehen und löschen.
- Betrifft: `agent/Memory.kt`, `agent/LoraAgent.kt`, `ui/LoraHelper.kt`, Einstellungen › Lora.
- **Umsetzung (04.11.2026, ☑)**: `agent/LoraPrefs.kt` – Vorlieben sind Gedächtnis-Einträge der neuen Kategorie
  *Vorlieben* mit Werkzeug (`tool`) und optional einem Standardwert einer Aktion (`pref` = „files.tidy:nach“, `value`).
  Lora fragt mit einer Karte *Merken · Nein* (`LoraStep.Remember`): nach Aktionen mit eigener Wahl (Parameter in
  `ToolAction.remember`, z. B. aufräumen nach, Export-Vorgabe, Titelstil), bei „Merk dir, dass …“ und über das Werkzeug
  `remember_preference`. Gemerkte Werte füllen offene Parameter („(wie gemerkt)“), gehen in Loras Prompt und als Kategorie
  in jeden Chat; der Export-Dialog des Videos startet mit der gemerkten Vorgabe. *Einstellungen › Lora › Gedächtnis*:
  gruppiert nach Werkzeug, ändern, löschen (mit Rückgängig), hinzufügen, *Alles vergessen*. Tresor: keine Rückfrage, kein
  Dazulernen (`MemoryLearner`), kein `remember`. Sync wie das übrige Gedächtnis (`memory.json`).

### AP-40 ☑ Abläufe über Werkzeuge · L (neu)
- Mehrschrittige Aufträge: „Aus dieser Aufnahme: transkribieren, Untertitel, Video auf 10 Minuten kürzen, Handout als
  Word“ oder „Alle Abgaben zusammenfügen, schwärzen, als ein PDF in die Nextcloud“.
- Lora **plant zuerst** und zeigt den Plan als Karte (Schritte, Werkzeuge, geschätzte Dauer, Datenschutzhinweis);
  *Los*, *Ändern*, *Abbrechen*. Danach führt sie die Schritte mit den Aktionen aus AP-38 aus.
- Fortschritt im Panel, Pause/Fortsetzen; lange Schritte (Transkription, Export, Stapel) laufen im Hintergrund weiter und
  melden sich (Benachrichtigung wie beim Stapel).
- Zwischenergebnisse landen in *Erstellt*, verknüpft mit dem Ablauf; ein fehlgeschlagener Schritt lässt sich wiederholen.
- Eigene gespeicherte Abläufe („Wochenprotokoll“) bleiben in den Ideen (*Eigene Lora-Befehle*).
- **Fertig, wenn**: Der Beispielauftrag mit der Aufnahme läuft vom Plan bis zum Handout durch, auch über einen Neustart
  der App hinweg.
- Betrifft: neu `agent/LoraPlan.kt`; `agent/LoraAgent.kt`, `agent/BatchRunner.kt` (Hintergrund), `ui/LoraHelper.kt`.
- **Umsetzung (04.11.2026, ☑)**: `agent/LoraPlan.kt` (`LoraFlow`, `FlowStep`, `LoraFlows`). Bei mehrschrittigen Wünschen
  bekommt Lora das Werkzeug `plan_workflow` und den Katalog aller Aktionen; der Plan erscheint als Karte (`ui/LoraCards.kt`,
  `FlowCard`) mit Schritten, Dauer und Datenschutzhinweis, *Los · Ändern · Abbrechen* (Ändern: Schritte abwählen oder neu
  planen lassen). Ausführung im Hintergrund unabhängig vom Panel, Schritt für Schritt mit den Dateien des vorigen; riskante
  Schritte warten mit Vorschau auf *Ausführen*, Fehler mit *Wiederholen/Überspringen*, *Pause/Fortsetzen*, Rückgängig je
  Schritt (in derselben Sitzung), Benachrichtigung am Ende. Gespeichert in `lora-ablaeufe.json`; laufende Abläufe setzen
  nach dem Start fort (`AppContainer` → `LoraFlows.resume`), offene stehen oben im Panel. Statt `BatchRunner` ein eigener
  Hintergrund-Lauf (Aktionen statt Modellanfragen je Datei).

### AP-41 ☑ Leuchtrahmen überall und Touren · M (Rest von AP-07, neu)
- **Leuchtrahmen für alle Optionen**: Jede Einstellung und jeder wichtige Knopf in den Werkzeugen lässt sich von Lora
  hervorheben (heute nur die wichtigsten). Ein Test prüft, dass jedes Ziel der Landkarte einen Rahmen hat.
- **Touren**: Beim ersten Öffnen eines Werkzeugs (PDF, Dateien, Erstellen, Video, später die neuen Editoren) bietet Lora
  eine kurze Tour an (3–6 Schritte, Leuchtrahmen + eine Zeile Text, *Weiter*/*Überspringen*). Abschaltbar unter
  *Einstellungen › Lora*, jederzeit über Strg+K bzw. „Zeig mir, wie das geht“ wiederholbar.
- Touren werden als Daten beschrieben (Ziel, Text), nicht je Bildschirm programmiert; englische Texte über `en.tsv`.
- Respektiert *Ruhigere Bewegungen*; Lora fliegt (AP-36) nur, wenn Bewegungen erlaubt sind.
- **Fertig, wenn**: Jede Option der Landkarte leuchtet beim Navigieren auf; das erste Öffnen des PDF-Werkzeugkastens bietet
  eine Tour an, die sich überspringen und später wieder aufrufen lässt.
- Betrifft: `ui/Spotlight.kt`, `data/AppMap.kt`, neu `ui/LoraTour.kt`, Einstellungen › Lora.
- **Umsetzung (04.11.2026, ☑)**: Alle Schalter der Einstellungen und die wichtigen Knöpfe in Dateien, PDF-Werkzeugkasten,
  Erstellen und Videoeditor haben `Spot`-Rahmen und stehen auf der Landkarte (`AppMap.PLACES`, 40+ Ziele, `spotIds`);
  Test: jedes Ziel hat seinen Rahmen. Orte eines ausgeschalteten Werkzeugs schlagen erst das Einschalten vor. Touren als
  Daten in `ui/LoraTour.kt` (Dateien, Erstellen, PDF, Video; 3–6 Schritte), Angebot beim ersten Öffnen (je Tour einmal,
  `loraToursSeen`), Karte unten mit *Zurück · Überspringen · Weiter*, der Rahmen bleibt je Schritt stehen (`Spotlight.hold`).
  *Einstellungen › Lora › Touren* (an/aus, starten, wieder anbieten), Strg+K „Tour …“, Lora „Zeig mir, wie das geht“
  bzw. `start_tour`. Lora fliegt nur ohne *Ruhigere Bewegungen*.

### AP-42 ☑ Lora-Dateidialog · M (neu, Wunsch)
Heute öffnet *Hochladen* den einfachen AWT-Dialog und die Ordnerwahl einen alten Java-Dialog (Swing) – beides wirkt
veraltet und passt nicht zu Windows 11.
- **Eigener Dialog im Lora-Design** für Öffnen, Speichern unter und Ordnerwahl, überall in der App (`Desktop.pickFiles`,
  `pickFolder`, `pickSaveFile`, Ordnerabfrage beim Start in `Main.kt`, Android-Auswahl wo sinnvoll).
- Seitenspalte wie im Tab *Dateien*: *Zuletzt · Markiert · Lora-Ordner · Hochgeladen · Erstellt · Nextcloud ·
  Dieser PC* (Laufwerke, Desktop, Downloads, Dokumente, OneDrive/Cloud-Ordner).
- Raster/Liste mit Vorschaubildern, Pfadleiste, Suche, Filter nach Dateityp, Mehrfachauswahl, neuer Ordner, Ziehen von
  Dateien aus dem Explorer in den Dialog; zuletzt benutzter Ort je Zweck wird gemerkt.
- Tastatur vollständig (Pfeile, Enter, Rücktaste, Strg+A, Tippen springt), Screenreader-Beschriftungen (AP-30), alle
  Stile inklusive Hoher Kontrast.
- **Fertig, wenn**: Kein Swing-/AWT-Dialog mehr in der App; Hochladen, Speichern unter und Ordnerwahl laufen über den
  Lora-Dialog und erreichen Lora-Ordner, Nextcloud und beliebige Ordner des PCs.
- Betrifft: `desktop/Desktop.kt`, `Main.kt`, `ui/FilePicker.kt` (beide), neu `ui/LoraFileDialog.kt`, `ui/DriveScreen.kt`
  (gemeinsame Bausteine).
- **Umsetzung (03.11.2026, ☑)**: `desktop/…/ui/LoraFileDialog.kt` – ein Fenster der App (liegt auch über dem Browser),
  in das `DesktopActions.pickFiles/pickFolder/pickSaveFile` warten; alle Aufrufer laufen unverändert darüber. Seitenspalte,
  Pfadleiste mit Eingabe, Suche, Typfilter, Liste/Raster mit Vorschaubildern (Bilder, PDF-Seite 1), Mehrfachauswahl,
  neuer Ordner, Ziehen aus dem Explorer, Ersetzen-Rückfrage, letzter Ort je Zweck, Tastatur und Screenreader. Nextcloud:
  Öffnen lädt, *Speichern unter* lädt nach dem Schreiben hoch. Die Lora-Ordner-Frage beim Start ist ein eigenes kleines
  Compose-Fenster (berührt keine App-Daten). Der Systemdialog bleibt nur als Rückfall, falls kein Fenster der App da ist.
  Android behält die Systemauswahl (Speicherzugriff nur über sie). **Nachtrag (04.11.2026)**: Ordner und Dateien im Dialog
  umbenennen (F2, Rechtsklick, Knopf; Prüfung auf verbotene Zeichen und vorhandene Namen) und löschen (Entf, Rechtsklick,
  Knopf; im Lora-Ordner in dessen Papierkorb, sonst in den Windows-Papierkorb, mit Rückfrage).

### AP-43 ☑ Gemeinsamer Text-Kern · XL (neu, Grundlage)
Der Dokument-Editor arbeitet heute **blockweise** (ein Eingabefeld je Absatz, Markdown darunter): Markieren über Absätze
hinweg geht nicht, der Cursor springt, Formatierung ist begrenzt. Grundlage für alle neuen Editoren:
- **Eigenes Dokumentmodell**: Absätze mit Formatvorlage, Läufe mit Zeichenformat (Schrift, Größe, fett/kursiv/
  unterstrichen, Farbe, Hervorhebung, hoch/tief), Absatzformat (Ausrichtung, Einzüge, Abstände, Zeilenabstand, Listen),
  Tabellen (verbundene Zellen, Rahmen, Schattierung), Bilder (im Text, umflossen), Abschnitte (Seitenformat, Ränder,
  Kopf-/Fußzeile, Spalten), Fußnoten, Felder (Seitenzahl, Inhaltsverzeichnis, Zitat), Formeln, Kommentare,
  Änderungsverfolgung.
- **Layout-Engine**: Zeilenumbruch, Silbentrennung (Deutsch/Englisch), **echter Seitenumbruch** mit Witwen/Waisen,
  Tabellen über Seiten, Kopf-/Fußzeilen je Abschnitt; dieselbe Engine zeichnet Bildschirm, PDF-Export und Vorschaubilder.
- **Bearbeiten**: ein durchgehender Cursor, Markieren über beliebige Grenzen (Maus, Umschalt+Pfeile, Doppel-/Dreifachklick),
  Kopieren/Einfügen mit Formatierung (aus Word und Browser: HTML/RTF), Rückgängig als Transaktionen, Eingabemethoden
  (IME, Akzente), große Dokumente (200+ Seiten) flüssig.
- Markdown bleibt als **Import/Export** erhalten (für Chat und `.md`-Dateien), nicht mehr als Speicherformat für Dokumente.
- Wird von Dokument (AP-44), Tabellenzellen (AP-48) und Textfeldern in Folien und Gestalten (AP-50) benutzt.
- Stufen: (a) Modell + Layout + Anzeige, (b) Bearbeiten und Rückgängig, (c) Zwischenablage, (d) Leistung bei großen
  Dokumenten.
- **Fertig, wenn**: Ein 100-seitiges Dokument mit Tabellen und Bildern lässt sich flüssig durchgehend bearbeiten,
  Seitenumbrüche entsprechen dem PDF-Export.
- Betrifft: neu `docs/text/*` (Modell, Layout, Bearbeiten); `docs/DocMarkup.kt`, `docs/LiveDoc.kt`.
- **Umsetzung (03.11.2026, ◐, für AP-44 vorgezogen)**: `docs/text/RichText.kt` – Fließtext mit Zeichenformat je Zeichen
  (fett, kursiv, unterstrichen, durchgestrichen, Code, hoch/tief, Farbe, Hervorhebung, Größe, Schrift, Link) und
  Absatzformat (Formatvorlage, Ausrichtung, Listenebene, Checkliste, weiche Zeilen); Bearbeiten (Diff, Formate wandern mit,
  Absätze teilen/verbinden), Markdown hin und zurück (Erweiterungen als HTML-Marken, die alle Exporte lesen), Vorschläge
  (Wortdiff) und Loras Schreibmarken. Markdown bleibt vorerst Speicherformat (Chat, Exporte, Word laufen unverändert).
  **Rest**: eigene Layout-Engine mit echtem Seitenumbruch (Witwen/Waisen, Kopf-/Fußzeilen je Abschnitt) für Bildschirm und
  PDF, Tabellen/Bilder im Modell statt als Blöcke, Zwischenablage mit Formatierung (HTML/RTF), Leistung bei 200+ Seiten.
- **Umsetzung Teil 2 (04.11.2026, 1.0.54, ◐)**: Absatzformat um Zeilenabstand, linken und Erstzeilen-Einzug (hängend)
  erweitert, Formatvorlagen Titel/Untertitel/Bildunterschrift/Code/Literatur (Absatzmarke `<!--a:…;s=…;z=…;l=…;f=…-->`,
  alle Exporte lesen sie); **Zwischenablage mit Formatierung** (`docs/text/RichHtml.kt`, `ui/RichClipboard.kt` je
  Plattform: HTML hin und zurück, Word-HTML mit Mso-Klassen und getippten Aufzählungszeichen, Google Docs); ein
  **Seitenumbruch** füllt in der Seitenansicht den Rest der Seite; Silbentrennung im Blocksatz; Enter am Ende einer
  Überschrift vor einer Überschrift verschiebt die Formate nicht mehr. Test `EditorCoreTest`. **Rest**: eigene
  Layout-Engine (echter Seitenumbruch mit Witwen/Waisen, Kopf-/Fußzeilen je Abschnitt, PDF aus derselben Engine),
  Tabellen/Bilder im Modell, RTF, Leistung bei 200+ Seiten.
- **Umsetzung Teil 3 (07.11.2026, 1.0.56, ◐)**: **Eigene Seitenaufteilung** `docs/text/PageLayout.kt` (Witwen/Waisen mit
  zwei Zeilen, Überschriften/Titel mit dem nächsten Absatz, Blöcke als Ganzes, erzwungene Umbrüche, überhohe Blöcke);
  die Seitenansicht zeigt damit **echte Blätter**: der Editor misst die Zeilen, die Aufteilung setzt unsichtbare Zeilen
  passender Höhe in den Text (`ui/FlowPaging.kt`, nicht Teil des Dokuments) bzw. Abstand vor Blöcken; Kopf-/Fußzeile mit
  Seitenzahl auf jedem Blatt. **RTF** in der Zwischenablage (`docs/text/RichRtf.kt`: schreiben und lesen). `paraAt` als
  binäre Suche; Seitenplan für 100 Seiten im Test unter 0,5 s. Tests `PageLayoutTest`, `EditorCoreTest`,
  `FlowEditorUiTest` (keine Zeile über einem Seitenende). **Rest**: PDF nicht aus derselben Engine, sondern mit denselben
  Regeln (CSS: Witwen/Waisen, erste/gerade Seiten) – Seitenumbrüche können je nach Schrift um Zeilen abweichen;
  Abschnitte mit eigener Kopfzeile; Tabellen/Bilder im Modell (Tabellen brechen nicht zeilenweise über Seiten);
  Leistung bei 200+ Seiten im Editor messen (ein Textfeld je Fließtextstück).
- **Umsetzung Teil 4 (07.11.2026, 1.0.57, ☑)**: **Tabellen über Seiten**: `PageLayout.Row` – Tabellen brechen zwischen
  Zeilen um, die Kopfzeile bleibt bei der ersten Zeile und wird auf jeder weiteren Seite wiederholt (`Plan.repeat`; Editor
  `TableGrid` mit `TablePaging`, PDF `<thead>`). **Abschnitte** mit eigener Kopf-/Fußzeile (`DocMarkup.Section`, Marker
  `<!-- abschnitt: kopfzeile=…; fusszeile=… -->`): Editor (je Blatt), PDF (benannte Seiten `@page absN`), Word (siehe AP-45).
  **Große Dokumente (Stufe d)** gemessen: 205 Seiten in einem Feld kosteten 2,7 s je Tastendruck. Ursachen: der Seitenplan
  kam nicht zur Ruhe (Abstände pendelten um einen Pixel, jedes Feld wurde je Taste mehrmals neu gesetzt) und vieles lief
  über den ganzen Text. Jetzt: Fließtext ab 30 000 Zeichen in Stücken je Kapitel (unsichtbar, siehe AP-44), 2,5 px
  Spielraum für Abstände, Zeilen je Feld, Markdown je Stück und Kopf-/Fußzeilen nur für sichtbare Blätter gemerkt bzw.
  gezeichnet – **~80 ms je Zeichen** bei 205 Seiten, Öffnen ~3 s. Tests `EditorRestTest`, `FlowEditorUiTest`
  (`bigDocumentStaysResponsive`). **Entscheidung (Offene Frage 8)**: Das PDF bleibt beim Satz über HTML mit denselben
  Regeln; kein eigener PDF-Textsatz.

### AP-44 ☑ Fließtext-Editor für Dokumente · XL (neu, ersetzt den Block-Editor)
So nah wie möglich an **Google Docs**:
- **Seitenansicht** mit echten Seiten (Standard) und *Ohne Seiten* (fortlaufend); **Lineal** mit Einzügen und Tabstopps;
  Zoom; Ränder per Ziehen.
- **Symbolleiste** wie Docs: Rückgängig, Formatvorlage, Schrift, Größe, fett/kursiv/unterstrichen, Farbe, Hervorhebung,
  Link, Kommentar, Bild, Ausrichtung, Zeilenabstand, Listen (Aufzählung, nummeriert, Checkliste), Einzug,
  Formatierung entfernen; Menüs *Datei · Bearbeiten · Ansicht · Einfügen · Format · Extras*.
- **Einfügen**: Tabelle (Raster-Auswahl, Zeilen/Spalten per Rechtsklick, verbinden), Bild (auch vom Bildmodell),
  Seitenumbruch/Abschnittsumbruch, Fußnote, Inhaltsverzeichnis, Formel, Sonderzeichen, Datum, Zitat aus der Literatur.
- **Suchen und Ersetzen** (auch Formatierung), Wortzahl, Rechtschreibung auf dem Gerät (aus AP-16), Kommentare am Rand,
  Änderungen nachverfolgen – übernommen in das neue Modell.
- Bestehende Dokumente werden beim ersten Öffnen umgestellt (Markdown → Modell); Versionen bleiben.
- **Fertig, wenn**: Der alte Block-Editor ist ersetzt; ein Hausarbeits-Dokument (Deckblatt, Inhaltsverzeichnis,
  Fußnoten, Tabellen, Literaturverzeichnis) lässt sich ohne Umweg über Word erstellen.
- Betrifft: `ui/DocsEditor.kt`, `ui/DocsText.kt` (ersetzt), `ui/DocsAssistant.kt`, `docs/LiveDoc.kt`.
- **Umsetzung (03.11.2026, ◐)**: `ui/FlowEditor.kt` ist der Editor für Text-Dokumente: durchgehender Text mit Cursor und
  Markieren über Absätze, Symbolleiste und Menüs wie Docs (Formatvorlage, Schrift, Größe, B/I/U/S, Farbe, Hervorhebung,
  Link, Kommentar, Bild, Ausrichtung, Listen, Einzug, Formatierung entfernen, ✨), Tastenkürzel, Markdown-Kürzel,
  Checkboxen zum Anklicken, eigenes Rückgängig, Seitenansicht (A4-Breite, Ränder aus *Seite einrichten*, Linien an den
  Seitenenden) oder *Ohne Seiten*, Zoom. Einfügen: Tabelle (Raster), Bild, Link, Kommentar, Zitat, Seitenumbruch,
  Inhaltsverzeichnis, Fußnote, Formel, Linie, Datum, Sonderzeichen; Tabellen werden an Ort und Stelle bearbeitet. Bestehende
  Dokumente öffnen ohne Umstellung (Markdown bleibt). Der Block-Editor bleibt nur für die Prüfung von KI-Änderungen und
  *Änderungen nachverfolgen*. **Rest**: Lineal mit Einzügen/Tabstopps, Ränder per Ziehen, Zeilenabstand wählbar, Kommentare
  am Rand im Text, Änderungen nachverfolgen im Fließtext, Suchen/Ersetzen mit Formatierung, Markieren über Tabellen hinweg.
- **Umsetzung Teil 2 (04.11.2026, 1.0.54, ◐)**: **Lineal** mit Seitenrändern zum Ziehen (beliebige Breite in mm, auch im
  Dialog *Seite einrichten*) und Erstzeilen-/linkem Einzug, **Zeilenabstand** (Symbolleiste, *Format*), **Suchen und
  Ersetzen im Fließtext** (Strg+F/H, Treffer markiert, einzeln/alle, Groß-/Kleinschreibung, ganze Wörter, alle Treffer
  formatieren), **Kommentare am Rand** neben der Textstelle (Text markiert, Klick springt, ✓ erledigt). **Rest**:
  Tabstopps, Änderungen nachverfolgen im Fließtext (bleibt in der Blockansicht), Markieren über Tabellen hinweg.
- **Umsetzung Teil 3 (07.11.2026, 1.0.56, ◐)**: **Seitenansicht mit echten Seiten** (siehe AP-43 Teil 3) samt Kopf-/Fußzeile
  je Blatt; **Änderungen nachverfolgen im Fließtext** (Wortvergleich `docs/text/TrackDiff.kt`, Myers): Gelöschtes rot
  durchgestrichen an seiner Stelle, Neues grün, Leiste *Änderung x von y* mit Annehmen/Ablehnen/Alle; die Blockansicht
  bleibt nur, wenn sich Tabellen, Bilder oder Formeln geändert haben. Test `FlowTrackingTest`. **Rest**: Tabstopps
  (Compose kennt keine eigenen Tabstopps), Markieren über Tabellen hinweg.
- **Umsetzung Teil 4 (07.11.2026, 1.0.57, ☑)**: **Tabstopps** (`TabStop` im Absatzformat, Marker `t=40l90c160r`):
  Klick ins Lineal setzt einen (Art links/zentriert/rechts über das Zeichen links im Lineal), Ziehen verschiebt, Wegziehen
  entfernt, *Format › Tabstopps entfernen*; die Tab-Taste fügt einen Tab ein. Compose kennt keine Tabstopps: der Editor
  misst jeden Tab und gibt ihm Buchstabenabstand bis zu seinem Stopp (`ui/FlowTabs.kt`), sonst alle 12,5 mm. **Markieren
  über Grenzen** (`ui/FlowSpan.kt`): eine Auswahl über mehrere Textfelder samt Tabellen/Bildern dazwischen (Strg+A,
  Umschalt+Pfeile, Umschalt+Klick), Kopieren mit Tabellen als HTML, Ausschneiden, Löschen, Überschreiben, Formatieren;
  Pfeile, Backspace und Entf gehen über die Feldgrenzen. Tests `FlowEditorUiTest` (`tabsReachTheirStops`,
  `selectionReachesOverTablesAndPieces`, `piecesOfALongTextJoinAndSplitInvisibly`), `EditorRestTest`.

### AP-45 ☑ Word-Treue · L (neu, ersetzt „Schreiben ins Original“ für Dokumente)
- **.docx direkt in das neue Modell lesen und zurückschreiben**: Formatvorlagen (auch eigene), Abschnitte,
  Kopf-/Fußzeilen (erste Seite, gerade/ungerade), Fußnoten, Felder, Nummerierungen, Tabellen, Bilder mit Umfluss,
  Textfelder (lesen), Kommentare, Überarbeitungen.
- **Unbekanntes bleibt erhalten**: Was das Modell nicht kennt, wird als Rohdaten mitgeführt und unverändert
  zurückgeschrieben (kein Umweg über `.lora/originale` mehr nötig).
- **Prüfsammlung** mit echten Dokumenten (TUD-Briefvorlage, Abschlussarbeit-Vorlage, Antrag, Protokoll): öffnen →
  speichern → in Word vergleichen; Bildvergleich der Seiten als Test.
- Export PDF über die eigene Layout-Engine (gleich wie am Bildschirm), ODT bleibt.
- **Fertig, wenn**: Die Prüfsammlung öffnet ohne sichtbare Abweichung und kommt nach dem Bearbeiten ohne Verlust in Word an.
- Betrifft: neu `docs/text/DocxReader.kt`, `docs/text/DocxWriter.kt`; `docs/DocxExtras.kt`, `files/OfficeEdit.kt`,
  `docs/MarkdownDocx.kt`.
- **Umsetzung (03.11.2026, ◐)**: Lesen (`docs/OfficeImport.kt`): Unterstreichung, Farbe, Hervorhebung/Schattierung,
  hoch/tief, abweichende Größe und Schrift (gegenüber der üblichen des Dokuments) und Ausrichtung. Schreiben
  (`docs/text/DocxWriter.kt`, von `DocFiles` genutzt, wenn das Ersetzen im Text nicht reicht): unveränderte Absätze und
  Tabellen werden als Original-XML übernommen, Unbekanntes ohne Text bleibt an seinem Platz, nur neue/geänderte Blöcke
  entstehen neu (mit den Überschriften-Formatvorlagen des Originals); alle anderen Teile der Datei bleiben unberührt. Ist
  das nicht sicher, wird wie bisher neu geschrieben. Test `WordFidelityTest`. Export Word schreibt Farbe, Hervorhebung,
  hoch/tief, Größe, Schrift und Ausrichtung. **Rest**: Prüfsammlung mit echten TUD-Dokumenten und Bildvergleich, Kopf-/
  Fußzeilen und Abschnitte im Modell, Textfelder, PDF über eigene Layout-Engine.
- **Umsetzung Teil 2 (04.11.2026, 1.0.54, ◐)**: Lesen von **Kopf-/Fußzeile** (Text ohne Seitenzahlen, nicht aus Loras
  eigenen Dateien), **Seitenrändern**, **Textfeldern** (nur lesen, Block `<!-- textfeld -->`; beim Zurückschreiben bleiben
  sie unverändert, auch die Kopie für altes Word), **Inhaltssteuerelementen** (w:sdt, bisher verloren), Untertitel,
  Bildunterschrift, Literatur, Code, Zeilenabstand und Einzügen. Export Word schreibt die Formatvorlagen des Dokuments als
  echte Word-Formatvorlagen (Titel, Untertitel, Überschrift 1–4, Literatur) samt Zeilenabstand und Einzügen. Prüfsammlung
  als Test mit einer nachgebauten Vorlage (`EditorCoreTest`). **Rest**: Prüfsammlung mit echten TUD-Dokumenten und
  Bildvergleich (Offene Frage 5), Kopf-/Fußzeilen erste/gerade Seite und Abschnitte im Modell, PDF über eigene Layout-Engine.
- **Umsetzung Teil 3 (07.11.2026, 1.0.56, ◐)**: **Erste Seite anders** und **gerade Seiten anders** im Modell
  (`<!-- erste seite anders: an -->`, `<!-- kopfzeile erste seite: … -->`, `<!-- fusszeile gerade seiten: … -->`), Felder
  `{seite}`/`{seiten}`; Word lesen (`w:titlePg`, `w:evenAndOddHeaders`, Kopf-/Fußzeilen `first`/`even`) und schreiben
  (eigene Kopf-/Fußzeilen-Teile mit PAGE/NUMPAGES-Feldern), PDF (`@page:first`, `@page:left`, eigene Ränder, Witwen/Waisen).
  „Seite“ in Kopf-/Fußzeilen wird beim Lesen nur noch mit Zahl als Seitenzahl entfernt. **Rest**: Prüfsammlung mit echten
  TUD-Dokumenten (Offene Frage 5), Abschnitte mit eigener Kopfzeile.
- **Umsetzung Teil 4 (07.11.2026, 1.0.57, ☑)**: **Abschnitte** aus Word lesen (`w:sectPr` im Absatz beendet einen
  Abschnitt; Kopf-/Fußzeile des nächsten Abschnitts als Marker, der erste Abschnitt ist der des Dokuments) und schreiben
  (eigene Kopf-/Fußzeilen-Teile ab `header4.xml`); geänderte Abschnittsumbrüche schreiben die Datei neu. **Tabs und
  Tabstopps** lesen/schreiben (`w:tab`, `w:tabs`). **Prüfsammlung** (Offene Frage 5 entschieden: nachgebaute Dateien,
  `PruefsammlungTest`): TUD-Brief (erste Seite anders, Bezugszeichen an Tabstopps), Abschlussarbeit (Titelblatt, drei
  Abschnitte, Fußnote, Tabelle mit 41 Zeilen, Literatur), Antrag (Inhaltssteuerelemente, Ankreuzfelder, Aufzählung),
  Protokoll (Tabstopps, Teilnehmertabelle, nummerierte Tagesordnung) – öffnen, im Editor zeigen, eine Stelle ändern, ins
  Original schreiben: alle anderen Teile byteweise gleich. Dabei behoben: Absätze mit ☒/☐ am Anfang werden im Original
  wiedergefunden; ein geänderter Absatz mit Fußnote verweist auf die Fußnote des Originals (vorher: ganze Datei neu).
  **Nicht umgesetzt**: Bildvergleich der Seiten (kein Word im Test; die Prüfsammlung vergleicht Text und Struktur).

### AP-46 ☑ Gliederung und Formatvorlagen · M (neu)
- **Gliederung links** (wie in Docs): Überschriften als Baum, Klick springt, Ziehen verschiebt ganze Abschnitte,
  ein-/ausklappbar.
- **Formatvorlagen**: Standard, Titel, Untertitel, Überschrift 1–4, Zitat, Bildunterschrift, Code, Literatur; ändern
  („Überschrift 1 an Markierung anpassen“), als Standard speichern.
- **TUD-Stil** als Vorlagensatz (Noto Sans, Farben aus dem CD-Paket 2025), dazu *Wissenschaftlich* (Times-artig,
  1,5 Zeilen) und *Schlicht*; Wechsel des Satzes formatiert das ganze Dokument um.
- **Format übertragen** (Pinsel), Strg+Alt+1…4 für Überschriften, Markdown-Kürzel beim Tippen (`#`, `-`, `1.`, `**`).
- **Fertig, wenn**: Ein Dokument lässt sich über die Gliederung umstellen und per Klick vom TUD-Stil in den
  wissenschaftlichen Stil umschalten.
- Betrifft: `ui/DocsEditor.kt`, neu `docs/text/Styles.kt`, `docs/DocTheme.kt`, `docs/TudBrand.kt`.
- **Umsetzung (04.11.2026, 1.0.54, ☑)**: **Gliederung links** im Fließtext-Editor (`ui/FlowExtras.kt`, Logik
  `docs/text/Outline.kt`): Baum der Überschriften, Klick springt, Ziehen am Griff ⠿ verschiebt den ganzen Abschnitt (auch
  ⋮ › nach oben/unten), Ebenen einklappbar. **Formatvorlagen** (`docs/text/Styles.kt`): Normaler Text, Titel, Untertitel,
  Überschrift 1–4, Zitat, Bildunterschrift, Code, Literatur; „… an Markierung anpassen“, zurücksetzen, *Als Standard für
  neue Dokumente speichern* (Einstellung `docStyleLines`). **Vorlagensätze** TU Dresden, Wissenschaftlich, Schlicht,
  Standard; der Wechsel formatiert das ganze Dokument um und setzt den Export-Stil. Gespeichert als Kommentarzeilen
  (`<!-- vorlagen: … -->`, `<!-- vorlage h1: … -->`). **Format übertragen** (Pinsel), Strg+Alt+0…4, Markdown-Kürzel inkl.
  `**fett**` und `*kursiv*`. Test `EditorCoreTest`.

### AP-47 ☑ Lora schreibt im Dokument · M (neu)
- **„Hilf mir schreiben“** (wie Gemini in Docs): Feld an der Cursorstelle bzw. über leeren Absätzen – „Schreib eine
  Einleitung zu …“, „Fasse Abschnitt 3 zusammen“. Lora schreibt **sichtbar an der Cursorstelle** (Text erscheint
  schrittweise, farbig markiert), danach *Übernehmen · Neu · Verwerfen*.
- **Abschnitte überarbeiten als Vorschlag**: Lora ändert nicht direkt, sondern über die Änderungsverfolgung (rot/grün),
  jede Änderung einzeln annehmbar; Kommentare von Lora am Rand mit Begründung.
- Lora-Popup über Markierung und Lora-Panel rechts bleiben (AP-16); beide schreiben jetzt in das neue Modell und behalten
  Formatierung.
- Nutzt Gliederung und Formatvorlagen (Lora setzt Überschriften als Vorlagen, nicht als fette Zeilen), Zitieren aus der
  Literatur und die Belegpflicht (AP-08) auf Wunsch.
- **Fertig, wenn**: Lora schreibt eine Einleitung an der Cursorstelle und überarbeitet einen Abschnitt als einzeln
  annehmbare Vorschläge, ohne Formatierung zu zerstören.
- Betrifft: `ui/DocsAssistant.kt`, `ui/LoraHelper.kt`, `docs/text/*`, `agent/ToolActions.kt`.
- **Umsetzung (03.11.2026, ☑)**: Im Fließtext-Editor: *Hilf mir schreiben* (Strg+J, ✨, *Extras*, leeres Dokument, Lora
  „schreib eine Einleitung …“) schreibt schrittweise und hinterlegt an der Cursorstelle, Markdown-Überschriften werden
  Formatvorlagen; *Übernehmen · Neu · Verwerfen*. *Markierung mit Lora überarbeiten* bzw. Lora „Überarbeite Abschnitt …“:
  Wortdiff als Vorschläge (rot/grün), Leiste mit *Annehmen/Ablehnen/Alle*, Begründung als Kommentar von Lora. Das
  Lora-Panel rechts schreibt weiter über den Chat; der Editor lädt das Ergebnis mit Formatierung. Rest: das kleine
  KI-Popup direkt über einer Markierung gibt es nur im Block-Editor (im Fließtext über Strg+J / *Extras*).
- **Nachtrag (04.11.2026, 1.0.54)**: Der Rest ist erledigt – über einer Markierung im Fließtext erscheint Loras kleines Menü
  (*Überarbeiten …*, *Kürzer*, *Formeller*, *Einfacher* als einzeln annehmbare Vorschläge, *Kommentar*).

### AP-48 ☑ Tabellen wie Excel · XL (neu, in Stufen)
So nah wie möglich an **Google Sheets/Excel**:
1. **Rechnen**: Formelleiste mit Hilfe zu Funktionen, deutlich mehr Funktionen (Text, Datum, Statistik, SVERWEIS/XVERWEIS,
   WENNS, SUMMEWENNS, EINDEUTIG, FILTER, SORTIEREN), absolute/relative Bezüge, Bezüge auf andere Blätter, Fehlerwerte
   wie in Excel.
2. **Bedienung**: mehrere Blätter (Tabs unten), Zellen verbinden, Zahlenformate, bedingte Formatierung, Filter und
   Sortieren, Zeilen/Spalten fixieren, Ausfüllen durch Ziehen, Suchen/Ersetzen, Datenüberprüfung (Auswahllisten).
3. **Große Daten**: CSV/Excel mit 100 000 Zeilen flüssig (virtuelles Raster, Berechnung nur für Sichtbares/Abhängiges,
   Lesen im Hintergrund).
4. **xlsx-Treue**: Formeln, Formate, Blätter, verbundene Zellen, bedingte Formatierung lesen und schreiben.
- Zelltext nutzt den Text-Kern (AP-43) für Formatierung innerhalb von Zellen.
- **Fertig, wenn**: Ein Notenexport aus OPAL (mehrere Tausend Zeilen) lässt sich filtern, mit SVERWEIS anreichern und
  verlustfrei als .xlsx speichern.
- Betrifft: `docs/SheetEngine.kt`, `docs/SheetMeta.kt`, `ui/SheetEditor.kt`, `docs/SheetTemplates.kt`.
- **Umsetzung (08.11.2026, ◐)**: **Stufe 1 Rechnen**: ganze Spalten (`A:A`, `Tabelle2!A:B`), Platzhalter `*`/`?`/`~` in
  ZÄHLENWENN, SUMMEWENNS & Co. und im genauen SVERWEIS/VERGLEICH, ausgelassene Argumente (`=XVERWEIS(70;D:D;A:A;;-1)`),
  XVERWEIS mit Vergleichs- und Suchmodus, rund 45 neue Funktionen (u. a. MAXWENNS/MINWENNS, XVERGLEICH, VERWEIS, WAHL,
  INDIREKT, ADRESSE, TEILERGEBNIS, HÄUFIGKEIT, QUARTILE, RANG.MITTELW, TEXTVOR/TEXTNACH/TEXTTEILEN, ERSETZEN, IDENTISCH,
  ZEIT/STUNDE/DATWERT, KÜRZEN, LOG, ISTNV, FEHLER.TYP, MTRANS, VSTAPELN/HSTAPELN, SPALTENWAHL/ZEILENWAHL; die neuen
  Matrixfunktionen laufen über), Excel-Namen mit `_xlfn.` im Export. **Hilfe beim Tippen** (`docs/SheetHelp.kt`): unter
  der Bearbeitungsleiste die Argumente der Funktion am Cursor (aktuelles fett) bzw. passende Funktionen zum Vervollständigen
  (Tab übernimmt). **Stufe 2 Bedienung**: Suchen/Ersetzen (Strg+F/Strg+H; Groß/klein, ganze Zelle, in Formeln oder Werten),
  **Zellen verbinden**, **Zeilen 1–n und Spalten A–x fixieren**, **Spaltenbreite** (Rand im Spaltenkopf ziehen, breiter/
  schmaler, an Inhalt anpassen), **Auswahllisten** (Werte oder Zellen, auch eines anderen Blatts; streng ablehnen oder nur
  rot markieren; ▾ in der Zelle), **Ausfüllkästchen** zum Ziehen (Reihen, Formeln wandern mit). **Stufe 3 Große Daten**:
  schneller Weg für Blätter (`SheetEngine.grid` Zeile für Zeile, Formate vom Ende her), Zellen ändern kopiert nur die
  geänderte Zeile, Berechnung weiter nur für Sichtbares/Abhängiges; Messung im Test: 100 000 Zeilen × 7 Spalten lesen
  0,4 s, erster Bildschirm 0,14 s, xlsx schreiben 2,8 s, lesen 1,5 s. **Stufe 4 xlsx-Treue** (`docs/XlsxReader.kt`): Zellen
  mit einem eigenen Scanner statt XML-Baum (bis 200 000 Zeilen, vorher 2 000), **geteilte Formeln** (gingen bisher
  verloren), verbundene Zellen, bedingte Formatierung (Wert, Text enthält, Farbskala), Auswahllisten, fixierte Zeilen/
  Spalten, eigene Spaltenbreiten und Zahlenformate wie „0.0“ lesen und schreiben. Test `SheetsLikeExcelTest`: u. a.
  **OPAL-Export mit 5 000 Zeilen** (geteilte Formeln, Formate) gelesen, mit SVERWEIS auf ein zweites Blatt angereichert,
  gefiltert, als .xlsx gespeichert und unverändert wieder gelesen (5 s).
- **Umsetzung Teil 2 (08.11.2026, ☑)**: **Formatierung innerhalb einer Zelle** (`docs/CellRich.kt`, `ui/SheetRichText.kt`):
  der Zelltext trägt das Inline-Markdown des Text-Kerns (`**fett**`, `*kursiv*`, `~~durch~~`, `<u>`, `<span style="color:…">`);
  ein Teil der Zelle wird in der Bearbeitungsleiste markiert und über die Werkzeugleiste bzw. Strg+B/I/U formatiert
  (ohne Markierung gilt das Format wie bisher für die ganze Zelle). Die Leiste und die Zelle zeigen die Formatierung statt
  der Zeichen, Formeln, Filter und Diagramme sehen den reinen Text (ein fettes „12“ bleibt eine Zahl), Excel bekommt
  echte Rich-Text-Läufe (`<is><r><rPr>…`) und gibt sie beim Lesen zurück (auch aus `sharedStrings.xml`). **Diagramme in
  .xlsx** (`docs/OfficeCharts.kt`): die Diagramme eines Blatts werden echte Excel-Diagramme (Zeichnung rechts neben den
  Daten), deren Reihen auf die Zellen zeigen (`'Noten 2026'!$B$2:$B$5`, `SheetTools.chartRefs`); Histogramm und Boxplot
  als Säulen ihrer Klassen bzw. Quartile mit Alternativtext „Histogramm von A1:A13“. Beim Öffnen werden Säulen-, Balken-,
  Linien-, Kreis- und Punktdiagramme fremder Dateien zu Diagrammen des Blatts, auf dem ihre Daten stehen (ID bleibt über
  den Namen „Lora-Diagramm …“). Test `OfficeChartsTest`.

### AP-49 ☑ Diagramme und Lora rechnet · M–L (neu)
- **Diagramme** aus markierten Bereichen: Säulen, Balken, Linie, Kreis, Punkt, Histogramm, Boxplot; Titel, Achsen,
  Farben im TUD-Design; als Bild exportieren.
- **Verknüpft einfügen** in Dokument und Folien: Ändern sich die Daten, aktualisiert sich das Diagramm (wie Sheets →
  Docs); in .docx/.pptx als echte Office-Diagramme, wo möglich.
- **Lora rechnet**: „Berechne die Notenverteilung“, „Mittelwert je Gruppe“, Pivot-Zusammenfassung, Daten bereinigen
  (Leerzeichen, Datumsformate, Dubletten), Formeln erklären – Ergebnisse als neue Spalten/Blätter mit Rückgängig, Erklärung
  in Worten im Panel.
- Rechnen passiert auf dem Gerät in der Tabellen-Engine; das Modell sieht nur Spaltennamen und Stichproben, außer man
  erlaubt mehr.
- **Fertig, wenn**: Aus einer Notentabelle entsteht per Auftrag eine Verteilung mit Diagramm, das in einem Dokument
  verknüpft steht und sich bei geänderten Noten aktualisiert.
- Betrifft: `docs/Charts.kt`, `ui/SheetEditor.kt`, `agent/ToolActions.kt`, `docs/PptxWriter.kt`.
- **Umsetzung (08.11.2026, ◐)**: **Diagramme aus markierten Bereichen** in acht Arten (Säulen, Balken, gestapelt, Linie,
  Kreis, Punkt – erste Spalte x –, Histogramm, Boxplot – je Spalte die Rohwerte), in den **Farben des TUD-Designs**
  (`ChartLayout.TUD`: Brillantblau, Orange, Türkis, Magenta, Grün …), mit Titel, Achsentiteln, Typ und Bereich zum
  Ändern (⋮ am Diagramm) und **als Bild (SVG)** im Ordner *Diagramme* neben dem Dokument. **Verknüpft einfügen**
  (`docs/ChartLinks.kt`): ⋮ › *Verknüpft in Dokument oder Folien einfügen* schreibt einen ```chart-Block mit `"link"` (Dokument,
  Blatt, Diagramm-ID) ans Ende des Dokuments bzw. als Diagrammfolie; beim Öffnen des Dokuments oder der Präsentation
  kommen die Daten frisch aus der Tabelle. **Lora rechnet** (`SheetCalc`, Aktionen `sheet.distribution`, `sheet.group`,
  `sheet.chart`, `sheet.explain`, auch ohne Modell erkannt: „Berechne die Notenverteilung“, „Mittelwert der Note je
  Gruppe“, „Erkläre die Formel in D2“): Verteilung (je Wert oder in Klassen, Anzahl und Anteil als ZÄHLENWENN-Formeln)
  bzw. Kennzahlen je Gruppe (Mittelwert, Anzahl, Min, Max – wie eine Pivot-Tabelle) rechts neben den Daten, mit
  Säulendiagramm und Rückgängig; Formeln erklärt in Worten mit den Spaltennamen der Bezüge. Gerechnet wird auf dem Gerät,
  das Modell bekommt nur den Auftrag. Bereinigen gab es schon (`sheet.clean`). Dazu begrenzt: Versionsverlauf und
  Rückgängig großer Tabellen nach Größe (24 bzw. 60 Mio. Zeichen) statt nur nach Anzahl. Test `ChartsAndLoraCalcTest`:
  Notenverteilung mit Diagramm verknüpft in Bericht und Präsentation, nach geänderter Note aktualisiert. Nachtrag:
  Daten bereinigen vereinheitlicht Datumsformate (TT.MM.JJJJ).
- **Umsetzung Teil 2 (08.11.2026, ☑)**: **Echte Office-Diagramme** in Word und PowerPoint (`OfficeCharts.embedded`):
  ```chart-Blöcke im Dokument und Diagrammfolien werden `c:chartSpace`-Teile mit **eingebetteter Arbeitsmappe**
  (`embeddings/Microsoft_Excel_WorksheetN.xlsx`), sodass „Daten bearbeiten“ in Word/PowerPoint funktioniert; Boxplots
  bleiben in PowerPoint gezeichnet und in Word Datentabelle, „Änderungen nachverfolgen“ schreibt Diagramme weiter als
  Tabelle. Word-Diagramme kommen beim Öffnen als ```chart-Block mit den Werten zurück; Diagramme aus .xlsx siehe AP-48.
  Test `OfficeChartsTest` (Word, PowerPoint, eingebettete Daten).

### AP-50 ☑ Gemeinsame Leinwand für Folien und Gestalten · L (neu, mit Gestalten-Resten)
- Der Folien-Editor bekommt die **Leinwand aus Erstellen › Gestalten** (Ebenen, Einrasten, Drehen, Ausrichten/Verteilen,
  Gruppieren, TUD-Shape, TUD-Icons, Logos, QR-Codes) – ein Kern für beide; die Leinwand kommt dafür in den gemeinsamen
  Code (Android zeigt sie an).
- **Text direkt auf der Leinwand tippen** mit dem Text-Kern (AP-43): Formatierung innerhalb eines Textfelds, Listen,
  automatische Größe.
- **Gestalten-Reste**: Farbverläufe, einfache Bildfilter (Helligkeit, Kontrast, Sättigung, Graustufen, Weichzeichnen),
  Gruppieren, Tabellen auf der Leinwand.
- Gestalten behält seine Vorlagen und Exporte; Folien behalten Layouts, Masterfolie und Referentenansicht.
- **Fertig, wenn**: Ein Element verhält sich in Folien und Gestalten gleich; Text wird auf der Leinwand getippt.
- Betrifft: Windows `creative/*`, `ui/DesignEditor.kt`, `ui/SlidesEditor.kt`, `docs/SlideDeck.kt`, `docs/SlideExtras.kt`.
- **Umsetzung (08.11.2026, ◐)**: Der Kern der Leinwand liegt jetzt im **gemeinsamen Code** (`creative/DesignEditorState.kt`,
  `creative/DesignViewport.kt` aus Windows verschoben, neu `creative/CanvasOps.kt`): Ausrichten, Verteilen, Ebenen und
  **Gruppen** als reine Funktionen, die Gestalten und die Folien beide nutzen (`SlideCanvas` übersetzt die freien
  Folienelemente in cm in Leinwand-Elemente in mm und zurück). **Gestalten**: *Gruppieren* (Strg+G, Strg+Umschalt+G; ein
  Klick wählt die ganze Gruppe, sie bewegt und richtet sich als ein Block aus), **Text direkt auf der Leinwand tippen**
  (Doppelklick auf ein Textfeld: Eingabe an Ort und Stelle in Schrift, Größe, Farbe und Drehung des Felds, Esc oder Klick
  daneben beendet), **Farbverläufe** (zweite Farbe und Richtung für Formen und Text-Hintergründe), **Bildfilter**
  (Helligkeit, Kontrast, Sättigung, Graustufen, Weichzeichnen) – im Editor, PNG, PDF und PowerPoint gleich (PDF und
  PowerPoint bekommen solche Elemente als scharfes Bild). **Folien**: Ausrichten auf der Folie und Ebenen über dieselben
  Funktionen, Gruppen (mit dem Element darunter gruppieren, Gruppe zieht sich gemeinsam), Drehung und Verlauf im
  Folienmodell. Test `SharedCanvasTest`. **Offen**: Folien-Editor ganz auf die Gestalten-Leinwand umstellen (Drehen,
  Einrasten, Mehrfachauswahl, TUD-Shape/Icons/QR auf Folien), Android zeigt die Leinwand (Zeichnen ist noch Windows-Code),
  Text-Kern mit Formatierung innerhalb eines Textfelds, Tabellen auf der Leinwand.
- **Umsetzung Teil 2 (08.11.2026, ☑)**: Der **Folien-Editor läuft auf der Gestalten-Leinwand**: `ui/SlideCanvasEditor.kt`
  (`SlideCanvasOverlay`) führt die Gesten der freien Folienelemente über `DesignEditorState` aus (`SlideCanvas.design`) –
  **Einrasten** an Rändern, Mitte und anderen Elementen mit Hilfslinien (Strg: frei), acht Griffe, **Drehgriff** (TUD-Shape in
  45°-Schritten), **Mehrfachauswahl** mit Umschalt+Klick und Rahmen, Gruppen, Pfeiltasten, Entf, Strg+G; beim Ziehen zeigt die
  Folie die Elemente live. Neu auf Folien: **TUD-Shape, Symbole, QR-Codes** (gezeichnet vom Renderer von Gestalten über die
  Plattformdatei `creative/CanvasArt.kt`; Android zeichnet die Shape selbst und Symbole/QR als Platzhalter) und **Tabellen**;
  Verlauf, Ausrichten/Verteilen mehrerer Elemente im Panel. **Formatierung innerhalb eines Textfelds** mit dem Text-Kern:
  `creative/CanvasText.kt` (`FieldRich` auf `CellRich`/`RichText.inline`, zeilenweise; Schalter `rich` an `SlideElement` und
  `DesignElement`, damit ein „*“ in alten Feldern ein Stern bleibt) und `CanvasTextEditor` – Doppelklick tippt auf der Folie
  und in Gestalten, Zeichen behalten beim Tippen ihr Format, Strg+B/I/U und eine kleine Leiste (fett, kursiv, unterstrichen,
  Farbe) formatieren die Markierung; gleich im Editor, PNG, PDF (Läufe mit eigener Schrift), PowerPoint (`<a:r>` mit
  `b`/`i`/`u`/Farbe) und Handout. **Tabellen auf der Leinwand** (`creative/CanvasTable.kt`, `ElementKind.TABLE` bzw.
  `tabelle`: Kopfzeile in TUD-Blau, Bänder, Zellen mit Inline-Formatierung, Zeilen/Spalten im Panel) als echte `a:tbl` in
  Folien und Gestalten-PowerPoint (`SlideExtras.tableFrame`); Folien-PowerPoint mit Drehung, Spiegeln, Verlauf und der
  TUD-Shape als eigener Geometrie. Tests `SharedCanvasTest` (Einrasten, Drehen, Seitenverhältnis, Gruppen, Rundlauf der
  Formatierung, Folien-Markdown, .pptx, Handout, Gestalten-Renderer, PDF). **Offen**: Android zeichnet Symbole und QR-Codes
  noch nicht (Platzhalter) und hat keinen Gestalten-Editor; Listen und automatische Größe in Textfeldern; Tabellen im
  Gestalten-PDF nur als Bild.

### AP-51 ☑ Folien wie PowerPoint · L (neu)
So nah wie möglich an **Google Slides/PowerPoint**:
- **PowerPoint-Treue**: fremde .pptx (auch die TUD-Präsentationsvorlage) mit Masterfolien, Layouts, Platzhaltern,
  Designfarben, Gruppen, Tabellen und Diagrammen originalgetreu anzeigen und bearbeiten; Unbekanntes bleibt erhalten.
  Dazu der Gestalten-Rest *PowerPoint-Vorlagen importieren*.
- **Animationen und Präsentieren**: Folienübergänge (Überblenden, Schieben), Aufzählungen nacheinander einblenden,
  Elemente ein-/ausblenden; Referentenansicht mit nächster Folie, Notizen, Timer; Laserpointer und Zeichnen beim
  Präsentieren; zweiter Bildschirm.
- **Lora baut Folien**: aus Dokument, Paper (PDF) oder Gliederung ein ganzes Deck im TUD-Design (Titel, Abschnitte,
  Inhalt, Bilder/Icons, Sprechertexte); einzelne Folie „schöner machen“ (Vorschläge zum Antippen); Folien kürzen,
  Sprechertexte schreiben.
- **Fertig, wenn**: Die TUD-Präsentationsvorlage öffnet originalgetreu, ein Deck aus einem Paper entsteht per Auftrag und
  lässt sich mit Animationen präsentieren.
- Betrifft: `ui/SlidesEditor.kt`, `docs/SlideDeck.kt`, `docs/PptxWriter.kt`, `docs/SlideHandout.kt`, neu
  `docs/PptxReader.kt`.
- **Umsetzung (08.11.2026, ◐)**: **Animationen**: je Folie *Übergang* (Überblenden, Schieben) und *Stichpunkte nacheinander
  einblenden* (Menü *Animation* an der Folie; als Kommentarzeile der Folie gespeichert, `SlideDeck.Animation`). **Vorführen**:
  Übergänge laufen beim Blättern, ein Klick/→ zeigt den nächsten Punkt (Unterpunkte mit ihm), zurück zeigt die Folie
  ganz; **Zeichnen** beim Präsentieren (D, rote Linien je Folie, E löscht) neben Laserpointer, Schwarz/Weiß, Notizen,
  Timer und Referentenansicht. **PowerPoint**: Übergänge (`p:fade`, `p:push`) und der Aufbau nach Absätzen
  („Erscheinen“, `p:timing`/`p:bldP`) werden geschrieben und beim Öffnen fremder .pptx gelesen. **Lora baut Folien**:
  Aktion `slides.from_document` (ganzes Deck im TUD-Design aus einem Dokument – Abschnitte, Inhalt, Tabellen, Bilder,
  Diagramme, lange Absätze als Sprechernotizen –, auf dem Gerät, mit Rückgängig) und `slides.animate` (Übergänge und
  Aufbau für alle oder eine Folie). Test `SlidesLikePowerPointTest`. **Offen**: `PptxReader` für fremde .pptx mit
  Masterfolien, Layouts, Platzhaltern, Designfarben und Gruppen (heute: Text, Bilder, Tabellen, Notizen, Animationen),
  zweiter Bildschirm, Deck aus einem Paper (PDF) und „Folie schöner machen“/Sprechertexte mit dem Modell.
- **Umsetzung Teil 2 (08.11.2026, ☑)**: **PowerPoint-Treue**: neuer `docs/PptxReader.kt` (`OfficeImport.pptx` gibt ab) liest
  fremde .pptx wie PowerPoint – **Platzhalter** erben Lage und Textstil von **Layout** und **Masterfolie**, **Designfarben**
  über Theme und Farbzuordnung (schemeClr mit lumMod/lumOff, tint, shade), **Gruppen** über chOff/chExt; Platzhalter füllen
  das Folienlayout (auch nach Layoutnamen), alles andere wird **freies Element** an seiner echten Stelle (Formen mit Füllung
  und Text, Linien, Bilder, Gruppen); **Diagramme** (`c:chart`, auch die echten Diagramme aus AP-49) über `OfficeCharts.read`
  als ```chart-Block, SmartArt als Stichpunkte, Hintergrund und Akzent des Masters als Masterfolie; eigene Decks behalten
  ihre freien Elemente. **Unbekanntes bleibt erhalten**: `docs/PptxMerge.kt` schreibt neue oder umgebaute Folien ins
  Original – unveränderte Folien, Master, Layouts, Theme, Abschnitte und unbekannte Teile bleiben Byte für Byte, reine
  Textänderungen werden je Folie ersetzt, gelöschte Folien gehen, die Reihenfolge folgt dem Editor (`DocFiles`).
  **Zweiter Bildschirm** (Windows, `ui/PresentScreens.kt`): Folien randlos auf dem zweiten Bildschirm, Referentenansicht
  bleibt, Zeichnen und Laserpointer an derselben Stelle, umschaltbar in der Vorführleiste. **Lora baut Folien**:
  `slides.from_paper` (`docs/PaperDeck.kt`) baut aus einem Paper (PDF) ein TUD-Deck auf dem Gerät – Titel und Autor:innen,
  „Worum es geht“, Gliederung, Abschnitte und Unterabschnitte mit Kernaussagen und Sprechernotizen, Tabellen, Abbildungen
  (Windows), Punkte nacheinander; **„Folie schöner machen“** mit Vorschlägen zum Antippen (`docs/SlideLook.kt`,
  `ui/SlideSuggestions.kt`, mit Modell eigene Varianten, `slides.beautify`) und **Sprechertexte** (`slides.notes`, mit Modell,
  sonst aus den Stichpunkten). Tests `PptxReaderTest`, `SlidesLikePowerPointTest`, `OfficeChartsTest` (echtes Diagramm
  zurück). **Offen**: Prüfung mit der echten TUD-Präsentationsvorlage (die Datei liegt nicht im Repo; getestet mit
  nachgebauten Paketen); neu geschriebene Folien in fremden Decks im TUD-Design statt im fremden Layout; Schriftart, Kursiv,
  Umrisse und eigene Formen der Elemente; Abbildungen aus PDFs auf Android.

### AP-52 ☑ Video: mehrere Spuren und echte Vorschau (D) · XL (neu, Grundlage)
So nah wie möglich an **DaVinci Resolve** für den Alltag, eigenständig im Lora-Stil, **eine Ansicht** statt Seiten.
- **Projektmodell mit beliebig vielen Video- und Tonspuren** (heute: eine Videospur), Ebenen von unten nach oben,
  Deckkraft, Position/Größe/Zuschnitt je Clip; altes Projektformat wird übernommen.
- **Echte Vorschau**: flüssige Wiedergabe mit Ton aller Spuren, Musik, Übergängen und Titeln **genau wie im Export**
  (eigene Wiedergabe-Engine: ffmpeg decodiert, die App mischt Bild und Ton, Proxys bei Bedarf); Wellenformen auf den
  Tonspuren; J/K/L, Einzelbild vor/zurück, Schleife In–Out.
- **Bild-in-Bild** als Standardfall (Webcam-Ecke über dem Screencast), mit Vorlagen für die Ecke.
- Export bleibt ein ffmpeg-Filtergraph, jetzt für mehrere Spuren.
- **Fertig, wenn**: Screencast + Webcam als Bild-in-Bild + Musik + Titel laufen in der Vorschau flüssig mit Ton und
  sehen im Export genauso aus.
- Betrifft: Windows `video/VideoProject.kt`, `video/VideoPlayer.kt`, `video/VideoRender.kt`, `video/Media.kt`,
  `ui/VideoScreen.kt`.
- **Umsetzung (07.11.2026, 1.0.57, ☑)**: Projektmodell mit **Spuren** (`VideoProject.layers` auf V2 …, `AudioClip.track`
  für A1 …, `trackStates` für sperren/stumm/ausblenden) und **`Frame`** je Clip (Position, Größe, Zuschnitt, Deckkraft);
  alte Projekte laden unverändert (ihre Tonspur ist A1). **Ein Graph für Export und Vorschau** (`VideoRender.graph`): V1
  geschnitten und überblendet, jeder Clip nach seinem `Frame` platziert, die Bilder der höheren Spuren der Reihe nach
  darüber (Zuschnitt, Deckkraft, Ein-/Ausblenden), Ton aller Spuren gemischt; ein **Fenster** davon (`Window`) ist die
  Vorschau – zwei ffmpeg-Läufe mit demselben Graphen (Bild/Ton) aus den Proxys, Java Sound als Uhr, Titel und Untertitel
  zeichnet der Editor wie bisher darüber. Beginnt ein Fenster in einer Überblendung, fängt der Graph davor an und schneidet
  den Vorlauf ab. **J/K/L** (2×/4× über `setpts`/`atempo`, rückwärts als Bildfolge), **Schleife In–Out**, Standbilder mit
  mehreren Spuren aus dem Graphen. **Wellenformen** aus `AudioSync.envelope` (10 ms, im Cache). **Bild-in-Bild** über
  *Bild-in-Bild* (Leiste), F12 aus der Quelle oder Ziehen auf V2 …; Lora-Aktion `video.pip`. Test `VideoTracksTest`:
  Vorschau und Export bei 2 s (Webcam-Ecke) und 7,5 s (mitten in der Überblendung) Bild für Bild verglichen (mittlere
  Abweichung unter 18 von 255), Vorschau läuft mit 25 Bildern/s, Kapitel im MP4. **Nicht umgesetzt**: Lautheit angleichen
  in der Vorschau (braucht Vorlauf), Messung der Flüssigkeit auf den Dienst-PCs.

### AP-53 ☑ Video: Schnittwerkzeuge (D) · L (neu)
- **Medienpool** mit Ordnern (Bins), Vorschaubildern, Dauer, Suche; **Quellmonitor** mit In/Out-Punkten, *Einfügen* und
  *Überschreiben* (wie Resolve), Ziehen in die Zeitleiste.
- **Schnitt**: Klinge (B), Auswahl (A), Ripple-, Roll- und Slip-Trimmen, Magnet-Zeitleiste (Lücken schließen sich auf
  Wunsch), Verknüpfen von Bild und Ton, Spuren sperren/stumm/ausblenden, Marker mit Notiz (auch als Kapitel nutzbar).
- **Mehrere Tonquellen** synchronisieren (über die Wellenform), z. B. Kamera + Ansteckmikrofon bei Vorträgen/Interviews.
- Tastenkürzel nah an Resolve, Übersicht unter `?`.
- **Fertig, wenn**: Ein Vortrag mit zwei Tonquellen lässt sich über Quellmonitor und Ripple-Trimmen schneiden, ohne Lücken
  von Hand zu schließen.
- Betrifft: Windows `ui/VideoScreen.kt`, `video/*`.
- **Umsetzung (07.11.2026, 1.0.57, ☑)**: Schnitt als reine Funktionen in `video/VideoEdit.kt` (Editor, Lora und Tests
  nutzen dieselben): `split` (Klinge auf V1, V2 …, A …; verknüpfter Ton teilt sich mit), `insert`/`overwrite` (F9/F10),
  `rippleTrim`, `roll`, `slip`, `delete` und `closeGaps`, Magnet (`rippleAfter` schiebt Bilder, Ton, Titel, Untertitel,
  Marker und In/Out; gesperrte Spuren bleiben), Einrasten (`snapPoints`), `detachSound`/`link`/`unlink` (verknüpfter Ton
  leitet Start, In und Out aus seinem Clip ab: `VideoProject.linked()`), Marker und Kapitel (`chapters`,
  `youtubeChapters`, im MP4 über FFMETADATA). **Synchronisieren** (`video/AudioSync.kt`): Einsätze der Lautstärke beider
  Aufnahmen, Kreuzkorrelation per FFT, Spitze per Parabel verfeinert. Oberfläche: Medienpool (`ui/VideoPanels.kt`),
  Quellmonitor mit In/Out (Q), Zeitleiste mit Spurköpfen und Werkzeugen (`ui/VideoTimeline.kt`), Tastenübersicht (`?`,
  F1); Lora-Aktion `video.marker`. Test `VideoCutTest`: u. a. Vortrag mit Kamera und Ansteckmikrofon (1,7 s versetzt) –
  synchronisiert, zweimal geschnitten, Mitte gelöscht, Ripple-Trim, Folie aus dem Quellmonitor eingefügt, ohne Lücke; im
  Export liegt der Mikrofonton auf den Ereignissen der Kamera (±60 ms). **Nicht umgesetzt**: Rückwärtslauf mit Ton;
  Synchronisieren vergleicht die ersten 15 Minuten jeder Aufnahme (reicht für übliche Versätze).

### AP-54 ☑ Video: einfache Effekte (D) · M–L (neu)
- **Zoom und Schwenk mit Keyframes** (z. B. auf einen Bildschirmbereich im Screencast), **Hochformat-Reframe** für
  kurze Clips.
- **Verpixeln/Weichzeichnen** von Bereichen, die dem Objekt folgen; für Forschungsmaterial **Gesichter automatisch
  finden** (lokal) und verpixeln, Stimme verfremden – Ergebnis im Agent-Protokoll.
- **Geschwindigkeit** (0,25×–4×, Ton mit Tonhöhenkorrektur), **Standbild**, Rückwärts.
- **Einfache Bildkorrektur**: Helligkeit, Kontrast, Sättigung, Weißabgleich automatisch – kein Color Grading.
- **Fertig, wenn**: In einem Beobachtungsvideo werden Gesichter automatisch verpixelt; ein Screencast zoomt per Keyframes
  auf einen Bereich.
- Betrifft: Windows `video/*`, `ui/VideoScreen.kt`; lokales Erkennungsmodell im Videoeditor-Paket (AP-25).
- **Umsetzung (08.11.2026, ☑)**: Effekte je Clip auf V1 als **`ClipFx`** (`video/VideoEffects.kt`), alle durch **denselben
  ffmpeg-Graphen** wie Vorschau und Export (`VideoRender.graph`; Clips ohne Effekt ergeben den Graphen wie bisher).
  **Geschwindigkeit** 0,25×–4× (`setpts`, Ton über `atempo`-Ketten mit gleicher Tonhöhe; verknüpfter Ton übernimmt Tempo
  und Richtung), **Standbild** (`VideoFx.freeze`: Clip geteilt, das Bild steht n s und behält Bereiche und Farbe),
  **Rückwärts** (`reverse`/`areverse`, bis 5 s je Clip, sonst klare Meldung); Klinge, Ripple-Trim, Roll und Untertitel aus
  dem Transkript rechnen mit Tempo und Richtung. **Bildkorrektur**: Helligkeit, Kontrast, Sättigung (`eq`),
  **Weißabgleich automatisch** (Grauwelt-Schätzung aus dem Bild am Abspielkopf → `colorchannelmixer`). **Zoom und Schwenk
  mit Keyframes** in Quellzeit (linear oder sanft, als ffmpeg-Ausdruck mit balanciertem Baum; gleitend über `perspective`
  mit Subpixel, fest über `crop`), **Hochformat 9:16** folgt denselben Keyframes. **Bereiche verpixeln/weichzeichnen** mit
  Keyframes (`crop` + `boxblur` bzw. Herunter-/Hochskalieren, `overlay` an derselben Stelle). **Gesichter automatisch
  finden** (`video/Faces.kt`): Bilder alle 0,4 s aus einem ffmpeg-Lauf (`select`, nicht `fps` – der lieferte zu spät),
  **`FaceDetector`** YuNet je Bild, Spuren über Überlappung (neue Spur bei Lücke > 1,6 s oder Größensprung), daraus Bereiche
  mit Rand und vereinfachten Keyframes. **Stimme verfremden** (Halbtöne über `asetrate`+`aresample`+`atempo`, Clips und
  Tonspuren). Oberfläche: Abschnitt *Effekte* in den Clip-Eigenschaften (`ui/VideoEffectsUi.kt`), Zusammenfassung am Clip
  in der Zeitleiste; ohne Paket erklärt der Editor das und bietet Bereiche von Hand. Lora-Aktionen **`video.speed`**,
  **`video.freeze`**, **`video.zoom`**, **`video.faces`**, **`video.voice`** (mit Rückgängig); Anonymisierungen stehen mit
  Projekt, Anzahl und Detektor im Protokoll der Lora-Aktionen. **Videoeditor-Paket 2** mit dem Modell
  (`extension-videoeditor.sh`). Test **`VideoEffectsTest`**: Modell und Schnitt mit Tempo, Rückwärts und Standbild,
  Keyframes, Graph-Zeichenketten, echter Export (2× schneller, Zoom per Keyframes auf einen Bildschirmbereich,
  Weichzeichnen; Vorschau = Export), Beobachtungsvideo mit wandernder Person – das ganze Gesicht zu drei Zeitpunkten
  verpixelt (Test-Detektor), echte YuNet-Erkennung über JNA, wenn Laufzeit und Modell vorhanden sind. **Offen**: Effekte
  auf V2 … (Bild-in-Bild), Bereiche und Zoom mit der Maus im Vorschaubild ziehen (heute Schieberegler am Abspielkopf),
  Gesichtserkennung auf den Dienst-PCs prüfen.

### AP-58 ☑ Frag diesen Ordner · S–M (neu)
- Rechtsklick auf einen Ordner (Lora-Ordner, Ort, Projektordner, Nextcloud-Ordner) → **„Frag diesen Ordner“**: neuer Chat
  im **Quellenmodus** (AP-08) über alle Dateien darin, mit Belegen (Datei + Seite).
- Nutzt den Volltext-Cache aus AP-18; große Ordner werden im Hintergrund gelesen, Fortschritt sichtbar.
- Datenschutz: Vorschlag der Datenschutzstufe nach Ordner (vertraulich markierte Dateien → „Nur TU Dresden“).
- **Fertig, wenn**: Eine Frage an einen Ordner mit 50 PDFs wird mit klickbaren Belegen aus mehreren Dateien beantwortet.
- Betrifft: `ui/DriveScreen.kt`, `data/FileLibrary.kt`, `agent/Evidence.kt`.
- **Umsetzung (08.11.2026, ☑)**: *Dateien* › ⋮ an einem Ordner (Lora-Ordner, Orte, auch feste Ordner) › **„Frag diesen
  Ordner“**: liest die Texte aller lesbaren Dateien (PDF, Office, OpenDocument, Markdown, Text, Mails …, auch in
  Unterordnern, bis 400) über den Volltext-Cache von AP-18 im Hintergrund mit Fortschritt und öffnet einen neuen Chat im
  **Quellenmodus**; der Auftrag an das Modell nennt Ordner und Dateien. Neues Werkzeug **`search_folder`**
  (`agent/FolderAsk.kt`): die passendsten Stellen nur aus diesem Ordner (Seiten aus den `[Seite N]`-Marken, Wörter
  gewichtet nach Seltenheit im Ordner, höchstens drei Stellen je Datei) mit Datei, Pfad und Seite. Die **Belegprüfung**
  nimmt jede Datei der Treffer als eigene Quelle (mit Pfad, Klick öffnet das PDF an der Seite). **Datenschutz**: wirkt
  ein Ordner vertraulich (Gutachten, Noten, Bewerbung, personenbezogen …), empfiehlt die App „Nur TU Dresden“. Test
  `FolderAskTest`: 50 PDFs, Frage mit Belegen aus drei Dateien, alle Zitate geprüft, falsche Seite erkannt. **Rest**:
  Nextcloud- und Projektordner direkt (heute über den synchronisierten Ordner), Datenschutzstufe automatisch setzen statt
  empfehlen.

### AP-55 ☑ Video: textbasierter Schnitt (D) · M (neu)
- Das **Transkript** liegt als Text neben der Zeitleiste; Wörter sind mit der Zeit verknüpft (Klick springt).
- **Text löschen = Video schneiden** (wie Descript), Absätze verschieben = Clips verschieben, Füllwörter und Pausen
  markiert zum Entfernen; Rückgängig wie überall.
- **Lora als Schnitt-Assistentin**: „Kürze auf 10 Minuten, behalte die Beispiele“, „Entferne den Teil über die
  Organisation“ – Vorschlag als markierte Stellen im Transkript, annehmen/ablehnen.
- Baut auf dem Rohschnitt aus AP-25 und den Aktionen aus AP-38 auf; Sprecher:innen unterscheidbar, wenn die Transkription
  das liefert.
- **Fertig, wenn**: Ein Lehrvideo wird nur über das Transkript geschnitten, und die Zeitleiste folgt.
- Betrifft: Windows `video/RoughCut.kt`, `ui/VideoScreen.kt`, Transkription.
- **Umsetzung (08.11.2026, 1.0.60, ☑)**: `video/TextCut.kt` legt die Wörter der Transkripte auf die Zeitleiste (In/Out und
  Tempo der Clips, Wörter ohne Zeitmessung über ihr Segment verteilt), Pausen über 0,8 s werden eigene Marken am Absatzende,
  Füllwörter markiert („um“/„er“ nur in englischen Transkripten – vorher schnitt der Rohschnitt sie auch im Deutschen).
  **Löschen = Schneiden**: Bereiche mit Rand bis zum Nachbarwort (0,15 s), der Clip wird geteilt (rechter Teil mit neuer
  Id, verknüpfter Ton für beide Teile), mit dem Magneten rücken Titel, Ebenen und Marker nach; Absätze verschieben schneidet
  an den Pausen und verschiebt den Clip. Panel *Transkript* (`ui/VideoTranscriptPanel.kt`) mit Klick-Sprung, Hervorhebung
  beim Abspielen, Umschalt+Klick/Ziehen, Entf, ↑/↓, Füllwörter/Pausen mit einem Klick, Lora-Vorschlägen (durchgestrichen,
  einzeln an/aus, *Angenommene übernehmen*); Lora-Aktion `video.text_cut` mit Vorschau. Test `TextCutTest`. **Offen**:
  Vorschläge absatzweise statt satzweise; Clips mit Standbild/Rückwärts fehlen im Text.

### AP-56 ☑ Bildschirmaufnahme (D) · M (neu)
- **Aufnahme in der App**: ganzer Bildschirm, Fenster oder Bereich + Webcam + Mikrofon (+ Systemton), Countdown,
  Pause, Stopp über Infobereich/Tastenkürzel; Mauszeiger und Klicks hervorheben.
- Ergebnis landet als **Projekt im Videoeditor** mit getrennten Spuren (Bildschirm, Webcam als Bild-in-Bild, Ton) und
  optional sofortiger Transkription.
- Technik: ffmpeg (gdigrab/ddagrab, dshow/WASAPI) unter Windows; Folienaufnahme direkt aus der Referentenansicht (AP-51).
- **Fertig, wenn**: Ein Screencast mit Webcam-Ecke ist nach Stopp sofort als Projekt mit getrennten Spuren schnittbereit.
- Betrifft: neu Windows `video/ScreenRecorder.kt`, `ui/VideoScreen.kt`.
- **Umsetzung (08.11.2026, 1.0.60, ☑)**: `video/ScreenRecorder.kt`: **ein** ffmpeg-Prozess je Teil mit allen Quellen
  (gdigrab mit Bereich/Fenstertitel und Mauszeiger, DirectShow für Webcam/Mikrofon/Stereomix; Linux x11grab/v4l2/Pulse,
  macOS AVFoundation), jede Quelle eine eigene Datei (MKV/WAV, „q“ beendet sauber), Pause beendet den Teil. Klicks über
  `GetAsyncKeyState` (50 Hz) als Ringe auf V3. Projekt: V1 Bildschirm (stumm), V2 Webcam unten rechts, A1 Mikrofon, A2
  Systemton, alles im Medienpool; „sofort transkribieren“ startet die Transkription je Teil, der Editor ordnet sie zu
  (`VideoProject.pendingTranscripts`). Dialog mit Geräten (`ui/ScreenRecordUi.kt`), Bereich ziehen, Countdown;
  Aufnahmeleiste mit `SetWindowDisplayAffinity` aus der Aufnahme genommen, `desktop/GlobalHotkeys.kt`
  (RegisterHotKey), Infobereich; Knopf in der Referentenansicht. Test `ScreenRecorderTest` (echte Aufnahme mit Testquellen,
  Pause, Export). **Offen**: Klicks auf macOS/Linux, Systemton ohne Loopback-Gerät (WASAPI-Loopback kann ffmpeg nicht),
  Prüfung auf den Dienst-PCs.

### AP-57 ☑ Video: Export mit GPU und Warteschlange (D) · M (neu)
- **Hardware-Encoder** (NVIDIA NVENC, Intel Quick Sync, AMD AMF) automatisch erkannt, Software als Rückfall.
- **Export-Warteschlange im Hintergrund** (weiterarbeiten während des Exports), mehrere Vorgaben auf einmal (OPAL 720p,
  YouTube 1080p, nur Ton, Untertitel-Datei).
- **Kapitelmarken** aus den Markern (MP4-Kapitel, YouTube-Beschreibung), Ziel direkt **Nextcloud** oder Lora-Ordner.
- Musik und Übergänge in der Vorschau (Rest aus AP-25) sind mit AP-52 erledigt.
- **Fertig, wenn**: Ein 60-Minuten-Video exportiert auf einem PC mit GPU deutlich schneller als heute, im Hintergrund,
  mit Kapiteln.
- Betrifft: Windows `video/VideoRender.kt`, `ui/VideoScreen.kt`, `data/Nextcloud.kt`.
- **Umsetzung (08.11.2026, 1.0.60, ☑)**: `video/Encoders.kt` liest `ffmpeg -encoders` und macht je Hardware-Encoder eine
  Probe-Kodierung; Qualität je Encoder (NVENC `-cq`, Quick Sync `-global_quality`, AMF `-qp`, VideoToolbox `-q:v`);
  `VideoRender.render` fällt bei einem Fehler auf x264 zurück. `video/ExportQueue.kt`: Einträge nacheinander (Mutex) im
  Hintergrund mit Fortschritt, Abbrechen, Hinweis „mit dem Prozessor gerechnet“; Ziele Video je Vorgabe, nur Ton (M4A aus
  demselben Graphen), SRT/VTT, Kapiteltext; Upload nach Nextcloud gestreamt (`WebDavClient.uploadFile`,
  `NextcloudService.saveLarge`). Neuer Exportdialog und Chip „Exporte 1/3“ (`ui/VideoExportUi.kt`); die Lora-Aktion
  `video.export` nutzt die Grafikkarte. Test `ExportQueueTest`. **Offen**: Zeitmessung mit einem 60-Minuten-Video auf einem
  PC mit GPU; Warteschlange über Neustarts; VAAPI unter Linux.

### AP-59 ☑ Windows-Integration (W) · M (neu)
- **Ziehen aus dem Explorer** in *Dateien* (kopieren in den geöffneten Ordner) und **aus Lora in den Explorer**, in Mails
  und andere Programme.
- **Windows-Papierkorb** als Option statt des Lora-Papierkorbs (Rest aus AP-16).
- **„Mit Lora öffnen“ / „Lora fragen“** im Kontextmenü des Explorers (ohne Admin-Rechte, je Benutzer), öffnet die Datei
  in Lora bzw. einen Chat mit der Datei.
- **In Word/Excel/PowerPoint öffnen und zurück**: sichtbarer Zustand „in Word geöffnet“, Änderungen kommen beim Speichern
  zurück (heute alle 4 s geprüft).
- **Fertig, wenn**: Eine Datei aus dem Explorer lässt sich per Kontextmenü in Lora fragen und per Ziehen in den Lora-Ordner
  legen.
- Betrifft: Windows `ui/AppRoot.kt`, `desktop/Desktop.kt`, `data/LoraDrive.kt`, Installer/`desktop/launcher/`.
- **Umsetzung (08.11.2026, 1.0.60, ☑)**: `desktop/WindowsShell.kt`: Kontextmenü unter `HKCU\Software\Classes`
  (`*\shell\Lora.Open|Lora.Ask`, `Directory\shell\Lora.Ask`), Schalter unter *Einstellungen › Windows › Explorer und
  Papierkorb*, nach Updates neu geschrieben, wenn die EXE woanders liegt; Start mit `--open/--ask/--ask-folder`, eine
  laufende App bekommt die Datei über die Einzelinstanz (`ui/ShellRequests.kt`: PDF → Werkzeugkasten, Office/Markdown →
  Editor, Video → Projekt, sonst Chat mit Anhang, Ordner → Frag diesen Ordner). Dateien: Griff zum Ziehen
  (`dragAndDropSource` mit Dateiliste), *Als Datei kopieren*. Windows-Papierkorb über `Desktop.moveToTrash`
  (`AppSettings.systemTrash`). `OfficeHandoff` beobachtet von Lora geöffnete Dateien und Office-Besitzerdateien
  (`~$…`), meldet „in Word geöffnet“ (`ui/ExternalOffice.kt`, Dateien und Editor) und lädt Gespeichertes binnen einer
  Sekunde. Test `WindowsIntegrationTest`. **Offen**: Offene Frage 7; das neue Kontextmenü von Windows 11 braucht eine
  verpackte App (heute „Weitere Optionen anzeigen“).

### AP-60 ☑ PDF-Reste · M (Reste von AP-11, AP-15, AP-26)
- Markieren mit **Einrasten am Text** (Zeilen statt Rechtecke).
- **Schwärzen mit der Maus** auf der Seite (Bereich ziehen, Wort anklicken).
- **Digitale Signatur** (Zertifikat aus dem Windows-Zertifikatsspeicher bzw. Datei, sichtbares Feld, Prüfung vorhandener
  Signaturen).
- **Tags erzeugen** für PDF/UA (Überschriften, Absätze, Listen, Tabellen, Bilder mit Alternativtext).
- **Fertig, wenn**: Ein PDF lässt sich am Text markieren, per Maus schwärzen, digital signieren und mit Tags versehen.
- Betrifft: `docs/PdfStage3.kt`, `docs/PdfAccessibility.kt`, `ui/PdfToolScreen.kt`.
- **Umsetzung (08.11.2026, 1.0.60, ☑)**: `PdfTools.words` und `PdfSnap` (gemeinsam): Wörter mit Kästen, Zeilen, Auswahl
  vom ersten bis zum letzten Wort, ein Kasten je Zeile – beim Markieren live; *Mit der Maus schwärzen* (Rechteck oder Wort,
  dann `PdfStage3.redact`). `docs/PdfSigning.kt` (Bouncy Castle, `bcpkix-jdk18on`): CMS (adbe.pkcs7.detached) mit
  Schlüssel aus `Windows-MY` oder PKCS#12 im Speicher, sichtbares Feld als Vorlage, `saveIncremental`; Prüfung: Signatur,
  ByteRange bis zum Dateiende, Kette gegen `Windows-ROOT` bzw. Java-cacerts. `docs/PdfAutoTag.kt`: Inhaltsströme mit
  `BDC`/`EMC` und MCID je Textblock bzw. Bild, Pfade/Formen/Seitenzahlen als `/Artifact`, Strukturbaum (Document, H1–H3,
  P, L/LI/LBody, Figure mit Alt), ParentTree, MarkInfo, Sprache, Titelanzeige, XMP mit `pdfuaid:part`. Karten *Digital
  signieren* und *Tags erzeugen* (mit Alternativtexten). Test `PdfRestTest`. **Offen**: Tabellen erkennen, Zeitstempel und
  Langzeitprüfung (LTV) der Signatur, Android.

### AP-61 ☑ Diagramme und Whiteboard · L (bisher Ideen, W8-04)
- Flussdiagramme, Mindmaps, Organigramme, Zeitstrahlen auf der gemeinsamen Leinwand (AP-50): Verbinder, die an Formen
  haften, automatische Anordnung.
- **Lora zeichnet**: „Mach ein Flussdiagramm aus diesem Ablauf“, Mermaid-Text ↔ Diagramm.
- Whiteboard: unendliche Fläche, Freihand, Notizzettel; einfügbar in Dokument und Folien.
- **Fertig, wenn**: Aus einer Beschreibung entsteht ein bearbeitbares Flussdiagramm, das in einem Dokument steht.
- **Umsetzung (08.11.2026, 1.0.60, ☑)**: `docs/Diagrams.kt` (gemeinsam): Modell mit Knoten, Verbindern nach Id und Freihand;
  Mermaid flowchart/graph (Ketten, `&`, Beschriftungen, Formen), mindmap, timeline lesen und schreiben; Anordnung in Ebenen
  (längster Pfad, Rückkanten ignoriert, Reihenfolge nach Eltern), radial und als Zeitstrahl; Verbinder vom Rand zum Rand
  (Rechteck, Raute, Ellipse); SVG mit Alternativtext. `ui/DiagramEditor.kt` (Editor = Whiteboard: Ziehen, Verbinden, Stift,
  Notizzettel, Formen, Mermaid daneben, Mausrad-Zoom, Lora zeichnet über `diagramFromDescription`), `ui/DiagramView.kt`.
  Dokumente: ` ```mermaid ` (automatisch angeordnet) bzw. ` ```diagram ` (JSON mit Positionen), gezeichnet in Markdown und
  Fließtext-Editor, *Einfügen › Diagramm/Whiteboard*, Klick öffnet den Editor; PDF als SVG, Word als PNG
  (`docs/DiagramRaster.kt`), Folien mit Layout *Diagramm* (PowerPoint als Bild). *Erstellen › Diagramme & Whiteboard*
  (`ui/WhiteboardScreen.kt`, gespeichert unter `whiteboards/`), Lora-Aktion `doc.diagram`. Test `DiagramsTest`.
  **Offen**: Verbinder auf der Gestalten-Leinwand (AP-50), Bild für Word auf Android.

### AP-62 ☑ Dokumenten-Scanner · M (bisher Ideen)
- **Android**: Kamera mit Kantenerkennung, Zuschneiden, Entzerren, Kontrast (Schwarz-Weiß/Farbe), mehrere Seiten → PDF
  mit OCR in den Lora-Ordner (und per Sync an den PC).
- **Windows**: Scanner über WIA bzw. Handy als Scanner.
- **Fertig, wenn**: Drei fotografierte Seiten ergeben ein durchsuchbares PDF im Lora-Ordner.
- **Umsetzung (08.11.2026, 1.0.61, ☑)**: `docs/DocScanner.kt` (gemeinsam, reines Kotlin auf ARGB-Pixeln): Kanten über die
  größte helle (sonst dunkle) Fläche nach Otsu, konvexe Hülle, Viereck größter Fläche aus der Hülle (je Diagonale der beste
  Punkt auf jeder Seite), Prüfung auf Festigkeit; Entzerren über eine Homografie (bilinear), fast A4 wird genau A4, eine
  Haaresbreite nach innen gegen Tischkanten; *Farbe* (Weißabgleich am hellsten Zehntel, Pegel), *Graustufen* und
  *Schwarz-Weiß* mit örtlicher Papierhelligkeit (zwei Durchgänge über ein Integralbild, Schrift ausgenommen), *Original*;
  `save` legt je Scan eine A4-Seite an (`PdfExtras.imagesToPdf`) und die erkannte Schrift unsichtbar darüber
  (`PdfExtras.addTextLayer`), ohne Erkennung bleibt das PDF. `docs/ScanImages.kt` je Plattform (Android mit EXIF-Drehung
  und verkleinertem Dekodieren, Windows ImageIO/Skia, Schwarz-Weiß als 1-Bit-PNG). `ui/ScanScreen.kt`: Seiten mit
  verschiebbaren Ecken, Modi, Drehen, Sortieren, Entfernen, Speichern nach *Dateien › Scans* mit Fortschritt;
  `ui/ScanPlatform.kt`: Kamera-App und Fotos (Android), WIA-Dialog über COM (`desktop/WiaScanner.kt`) und Bilder (Windows).
  Einstiege: *Dateien › + › Scannen*, Landkarte, Strg+K, Tour *Scannen*. Tests `DocScannerTest` (drei schräg auf einem
  Tisch „fotografierte“ Seiten mit Schatten → ein PDF mit drei durchsuchbaren Seiten im Lora-Ordner), `Round2EndLookTest`.
  **Rest**: Texterkennung auf Android nur über ein Bildmodell (keine eingebaute OCR); mehrere Seiten aus dem Einzug
  kommen im WIA-Dialog einzeln.

### AP-63 ☑ Sync- und Android-Reste · M (Reste von AP-12, AP-14, AP-28)
- Literatur und Wissenssammlung auf Android synchronisieren (mit Neuladen im Betrieb).
- Sync im Hintergrund ohne geöffnete App (Android WorkManager).
- Browser neben dem Chat auf Android als einfache Fassung (WebView).
- Tresor-Projekte im Sync prüfen.
- **Fertig, wenn**: Ein am PC angelegter Literatureintrag erscheint ohne Öffnen der App auf dem Handy.
- **Umsetzung (08.11.2026, 1.0.61, ☑)**: `PhoneSync` nimmt `literature.json`, `knowledge/` und `vault/` dazu;
  `PhoneSyncService.apply` liest Literatur (`ListStore.reload`), Wissenssammlung (`KnowledgeStore.reload`) und Tresor
  (`VaultStore.reload`) neu. `tasks/BackgroundSync.kt`: periodischer WorkManager-Auftrag (30 Min., Netz, Akku nicht
  schwach, auf Wunsch nur WLAN), neu eingeplant, wenn sich die Einstellungen ändern; er synchronisiert nur, wenn die App
  nicht vorn ist, mit der Notiz „im Hintergrund“ (ein PC darf öffnen). **Tresor im Sync geprüft**: Die PIN-Kiste war an
  einen Geräteschlüssel gebunden – ein zweites Gerät hätte mit dem Wiederherstellungsschlüssel die Kiste des ersten
  überschrieben. Jetzt `VaultMeta.boxes`: je Gerät eine eigene Kiste, eine neue PIN ändert nur die eigene; Konfliktkopien
  von Tresor-Chats legt `SyncGuard.keepBoth` verschlüsselt nach `vault/konflikte`, `VaultStore.adoptConflicts` macht beim
  Öffnen „zweite Fassungen“ daraus (der Ordner bleibt vom Handy-Sync ausgenommen). `ui/PhoneBrowser.kt` (nur Android):
  WebView über bzw. neben der App, Seite lesen per Skript (`research/WebMeta.kt`: Titel, Text, Meta-Tags →
  Literatureintrag), Frage/Zusammenfassen/Literatur/Wissen, PDFs mit den Cookies in den Werkzeugkasten; Schalter *Links in
  Lora öffnen*. Test `SyncRestsTest`: Literatureintrag und Wissensdokument vom PC kommen per Hintergrund-Sync aufs Handy
  und sind nach dem Neuladen da; Tresor auf zwei Geräten mit eigener PIN; Tresor-Konflikt → zweite Fassung; Tresor nur
  verschlüsselt in der Nextcloud; Meta-Tags → Eintrag. **Rest**: der Browser hat keine Tabs; Fehlversuche am Tresor
  zählen für alle Geräte gemeinsam.

### AP-64 ☑ Android zieht nach · M–L (neu)
Wie bisher nur, was dort sinnvoll ist:
- Neuer Fließtext-Editor (AP-43–AP-47): ansehen, bearbeiten, Lora schreibt; Tabellen (AP-48) ansehen und einfach
  bearbeiten; Folien (AP-51) ansehen und präsentieren.
- Lora-Aktionen (AP-38) für Dateien und PDF, Gedächtnis (AP-39), Touren (AP-41), Lora-Dateidialog (AP-42), wo der
  Systemdialog nicht reicht.
- Kein Videoeditor, kein Gestalten (unverändert).
- **Umsetzung (08.11.2026, 1.0.61, ☑)**: Editor, Tabellen, Folien, Lora-Aktionen, Gedächtnis und Touren laufen über den
  gemeinsamen Code schon auf Android; nachgezogen wurde, was dort fehlte. **Präsentieren**: `ui/PresentScreens.kt` für
  Android zeigt die Folien über `android.app.Presentation` auf einem Beamer an HDMI/USB-C oder einer drahtlosen Anzeige
  (ComposeView mit dem Lebenszyklus der Activity), die Referentenansicht bleibt auf dem Gerät; ein später angesteckter
  Bildschirm wird binnen zwei Sekunden erkannt (auch am PC); Wischen blättert, `KeepScreenOn`. **Lora-Dateidialog, wo der
  Systemdialog nicht reicht**: Android-*Speichern* fragt zuerst nach einem Ordner im Lora-Ordner (`ui/LoraFolderPicker.kt`),
  den der Android-Dialog nicht erreicht – so kommen Exporte per Sync an den PC; *Anderswo …* öffnet den Systemdialog.
  **Verkleinern** (PDF-Stufe 3) mit pdfbox-android im Werkzeugkasten und als Lora-Aktion `pdf.compress`. Tour *Scannen*
  (auch Android). Test `AndroidCatchUpTest`. **Rest**: Schwärzen, PDF/A, Passwort und PDF/UA bleiben am PC; Touren für die
  Editoren selbst (Leuchtrahmen in den Editoren fehlen noch).

### AP-65 ☑ Diktat · S–M (neu, optional)
- Diktieren in Dokumente und Textfelder mit der vorhandenen lokalen Spracherkennung (sherpa-onnx), Satzzeichen per
  Sprache („Punkt“, „neuer Absatz“); keine Sprachbefehle für die App (Chat-Sprachmodus reicht).
- **Fertig, wenn**: Ein Absatz lässt sich im neuen Dokument-Editor auf dem Gerät diktieren.
- **Umsetzung (08.11.2026, 1.0.61, ☑)**: `audio/Dictation.kt`: Aufnahme mit dem vorhandenen `Recorder`, Erkennung über
  `Transcription.recognizeShort` (sherpa-onnx auf dem Gerät), der Uni-Server nur ohne geladenes Modell und wenn der
  Datenschutz es erlaubt; `punctuate` macht aus gesprochenen Befehlen Satzzeichen (Zwei-Wort-Befehle zuerst, die eigenen
  Satzzeichen des Modells daneben fallen weg, nach einem Artikel bleibt „Punkt“ ein Wort), `join` setzt Leerzeichen und
  Großschreibung am Cursor. `ui/DictationUi.kt` (Mikrofon-Knopf mit Berechtigung), `FlowState.dictate` im
  Fließtext-Editor, dazu die Sprechernotizen der Folien. Test `DictationTest` (u. a. ein diktierter Absatz im
  Dokument-Editor). **Rest**: Diktat in weiteren Textfeldern; ohne Stopp-Knopf (Pausenerkennung).

### AP-66 ☑ Schnellansicht in Dateien · S–M (bisher Ideen)
- Große Vorschau ohne Öffnen (Leertaste) mit Details-Spalte.
- **Fertig, wenn**: Leertaste über einer Datei zeigt sie groß; die Details-Spalte beschreibt die Datei unter der Maus.
- **Umsetzung (08.11.2026, 1.0.61, ☑)**: `ui/QuickLook.kt`: Schnellansicht als Dialog (PDF-Seiten, Bilder, Dokumente und
  Text als Markdown, Tabellen, Folien, Ordnerinhalt), ←/→ blättert durch die Dateien, Enter öffnet, Leertaste/Esc schließt;
  die Leertaste fängt `DriveScreen` vor fokussierten Knöpfen und Einträgen ab, das Suchfeld behält seine Leerzeichen.
  Ziel ist die Datei unter der Maus (Pointer-Enter/Exit), sonst die ausgewählte bzw. zuletzt benutzte. Details-Spalte
  (ⓘ oben, breite Fenster) mit `files/FileDetails.kt`. Android: ⋮ › *Vorschau*. Tests `QuickLookTest`,
  `Round2EndLookTest` (Maus über einer PDF → Details mit Seiten und Ort, Leertaste → Schnellansicht). **Rest**: Vorschau
  für Video und Ton; Details auch für Treffer der Suche.

### AP-68 ☑ Postfach nur mit lokalem Modell · M (neu, dringend)
**Ziel**: Aus Datenschutzgründen kommt **nur ein Modell auf diesem Gerät** an Mails, Ordner, Termine und Aufgaben – für
das klassische Outlook (COM) und den Mailserver (IMAP) gleichermaßen. Uni-Server und externe Anbieter (API) sind im
Postfach **fest gesperrt**, ohne Schalter zum Aufheben. Das lokale Modell darf Ergebnisse an Chats mit API-Modellen
**übergeben** – aber nur, was es selbst geschrieben hat. Ins Postfach selbst kommt nur das lokale Modell.

Heute: Der Modus *Postfach* schickt Mails und Termine an das gewählte Modell (Datenhinweis „… gehen an das gewählte
Modell, Namen pseudonymisiert“), und die `outlook_*`-Werkzeuge stehen bei eingeschaltetem Werkzeug *Mail & Kalender* in
**jedem** Chat bereit, unabhängig vom Modell (`AgentRunner`, außer im Tresor).

- **Sperre an einer Stelle**: Die Mail-Werkzeuge (Outlook und IMAP) bekommt ein Chat nur, wenn sein Modell auf dem Gerät
  läuft (`local/LocalLlm.kt`, `LlamaHost`). Das gilt für alle Wege: Chats, Modus *Postfach*, Lora-Aktionen, Abläufe
  (AP-40), Stapel (AP-20), geplante Aufgaben. Der Modus *Postfach* setzt die Datenschutzstufe des Chats fest auf *Nur auf
  diesem Gerät*; im Modellwähler stehen dort nur lokale Modelle, ein Wechsel auf ein Modell außerhalb des Geräts ist
  nicht möglich.
- **Auch Lora**: Lora antwortet sonst mit GLM-5.3 Flash (Uni-Server). Sie darf zum Postfach führen und Aufträge annehmen,
  liest aber keine Mails; Mail-Aufträge (zusammenfassen, sortieren, Entwurf) führt das lokale Modell aus, die Schrittzeile
  sagt „läuft auf diesem Gerät“. Auch Zusammenfassungen in *Mail & Kalender* kommen nur aus dem lokalen Modell.
- **Übergabe an API-Chats**: *An Chat übergeben* (Knopf und Lora-Übergabe, Standard DeepSeek V4.1 Flash) nimmt nur den
  Text, den das lokale Modell erzeugt hat (Zusammenfassung, Stichpunkte, Frage), pseudonymisiert und **vorher als Karte
  zum Prüfen und Ändern** – genau dieser Text geht hinaus, nichts sonst. Der neue Chat bekommt keine Mail-Werkzeuge, keine
  Anhänge, keine Mail-Kennungen und kann nicht ins Postfach greifen. Zurück ins Postfach geht ein Ergebnis nur über die
  Person oder das lokale Modell (Offene Frage 10).
- **Ohne lokales Modell**: Das Postfach erklärt, warum ein lokales Modell nötig ist, und bietet ein passendes zum
  Herunterladen an (mit Werkzeugaufrufen; Vorgabe je PC mit bzw. ohne Grafikkarte, Offene Frage 10). Ein schon geladenes
  lokales Modell wird mitgenutzt.
- **Tempo**: Lokale Modelle sind auf Dienst-PCs ohne Grafikkarte langsam → kurze Prompts, wenige Werkzeuge, Mails gekürzt
  (Kopf und Anfang, ganze Mail nur bei Bedarf); lange Läufe im Hintergrund mit Fortschritt (mit AP-69).
- **Texte**: Datenhinweis des Modus („Mails verlassen den PC nicht – ein Modell auf diesem Gerät liest sie“), *Einstellungen
  › Datenschutz*, Erklärung in *Mail & Kalender*, Datenschutzhinweise (Grundsatz *KI entscheidet nicht*), englische Texte
  über `en.tsv`.
- **Tests**: Kein Mail-Werkzeug in einem Chat mit Uni-Server- oder API-Modell – auch nicht bei eingeschaltetem Werkzeug,
  über Lora, Abläufe oder Stapel; die Übergabe enthält nur den freigegebenen Text; der Postfach-Chat lässt kein Modell
  außerhalb des Geräts zu.

**Fertig, wenn**: Mit einem API- oder Uni-Server-Modell ist kein Mail-Werkzeug erreichbar; im Postfach arbeitet ein
lokales Modell; eine Zusammenfassung lässt sich nach Prüfung an DeepSeek übergeben, ohne dass der neue Chat die Mails
selbst lesen kann.

**Betrifft**: `agent/AgentRunner.kt` (Freigabe der `outlookTools`), `agent/ChatModes.kt` (`MAIL`), `agent/OutlookTools.kt`,
`data/Privacy.kt`, `local/LocalLlm.kt`, `agent/LoraAgent.kt`, `agent/ToolActions.kt`, `agent/LoraPlan.kt`,
`ui/MailScreen.kt`, Modellwähler, `en.tsv`.
- **Umsetzung (09.11.2026, ☑)**: Eine Sperre an einer Stelle (`agent/MailAccess.kt`): Mail-Werkzeuge (`outlook_*`,
  `mail_*`) und Lora-Aktionen `mail.*` gibt es nur für Modelle auf dem Gerät – in Chats, im Modus *Postfach*, über Lora,
  Abläufe, Stapel und geplante Aufgaben; `LlmClient` prüft als letzte Stelle (`MailAccess.guard`), dass keine Maildaten an
  einen Server gehen. Ein Chat, der das Postfach berührt hat, sperrt sich (nur Mail-Werkzeuge und Rückfragen, kein Web,
  keine Stapel). Modus *Postfach* fest *Nur auf diesem Gerät*, Modellwähler nur lokal. Lora nimmt Mail-Aufträge an und
  gibt sie an das lokale Modell weiter (`agent/LoraMail.kt`, Schrittzeile „läuft auf diesem Gerät“). *An Chat übergeben*
  (`MailHandoff`): Karte zum Prüfen und Ändern, pseudonymisiert, ohne Kennungen, Anhänge und Werkzeuge. Ohne lokales
  Modell erklärt `ui/MailLocalModel.kt` den Grund und schlägt je nach Grafikkarte ein Modell vor (ein geladenes wird
  mitgenutzt). Mails gehen gekürzt an das Modell (`read_mail` mit `full=true` für die ganze Mail). Gedächtnis und
  Vorlesen bleiben bei Postfach-Chats auf dem Gerät. Karte *Postfach* unter *Einstellungen › Datenschutz*. Test
  `MailLocalOnlyTest`. **Rest**: Vorgabe des Modells nach der Messung (Offene Frage 10); Probe mit echtem Outlook und
  llama.cpp auf einem Dienst-PC.

### AP-69 ☑ Postfach: echte Mailansicht und Lora sortiert selbst · L (neu, dringend, nach AP-68)
**Ziel**: Das Postfach zeigt **alle Mails** wie ein Mailprogramm, und Lora **ordnet Mails auf Zuruf selbständig ein** –
„Geh die Mails der letzten Woche durch und ordne sie in Lehre, Forschung und Verwaltung ein“ – und legt dafür auch **eigene
Ordner in Outlook** an. Alles mit dem lokalen Modell (AP-68); gelöscht und gesendet wird weiterhin nie.

Heute zeigt *Mail & Kalender* Verbindung, Termine von heute/morgen, Sortierregeln und Protokoll, aber **keine Mails**.
Sortieren geht über feste Regeln (Absender/Betreff → Ordner) oder im Chat mit höchstens 200 Mails je Abruf und Rückfrage
bei jedem Verschieben. `outlook_create_folder` gibt es als Werkzeug, ist aber nicht fester Teil eines Sortierauftrags.

**Stufen**
- **a) Mailansicht** (ohne Modell): drei Spalten wie in Outlook – **Ordnerbaum** (alle Ordner mit Unterordnern und
  Ungelesen-Zahl), **Mail-Liste** (Absender, Betreff, Vorschau, Datum, Anhang, gelesen/ungelesen; gruppiert nach Heute,
  Gestern, Diese Woche …; lädt seitenweise nach und bleibt auch bei vielen tausend Mails flüssig) und **Lesebereich**
  (Text, Anhänge öffnen oder in den Lora-Ordner speichern, *In Outlook öffnen*). Spaltenbreiten ziehbar. **Suchen und
  filtern** (Absender, Betreff, Zeitraum, ungelesen, mit Anhang, ein Ordner oder alle). **Mehrfachauswahl**, Ziehen in
  einen Ordner, als gelesen markieren, Ordner von Hand anlegen und umbenennen – mit Rückgängig. Termine, Regeln und
  Protokoll wandern in eine Seitenleiste bzw. einen eigenen Reiter. Das Lora-Panel kennt die Auswahl („diese 12 Mails
  …“). Outlook und IMAP über dieselbe Schnittstelle (`OutlookBridge`: seitenweises Laden, Ordnerbaum, gelesen markieren,
  Ordner umbenennen). Android: Liste und Lesebereich nacheinander.
- **b) Lora sortiert selbst**: Zeitraum, Quellordner (Standard Posteingang) und Zielordner kommen aus dem Satz; fehlende
  Zielordner legt Lora **in Outlook an** (auch Unterordner, z. B. „Lehre/WS 2026-27“), auf Wunsch schlägt sie eine
  Ordnerstruktur vor. Das lokale Modell liest Kopf und Anfang jeder Mail und ordnet zu (kurze Begründung, Sicherheit).
  Danach **eine Vorschau für den ganzen Auftrag** (Ordner → Anzahl, je Mail aufklappbar, einzelne umhängen oder
  auslassen, Unsichere gesondert) und **ein Klick** *Einordnen* statt einer Rückfrage je Mail. Auf Wunsch ohne Vorschau
  („ohne Rückfrage“ im Auftrag); Unsichere bleiben dann im Quellordner.
- **c) Lauf und Rückgängig**: Der Lauf geht im Hintergrund mit Fortschritt, Pause und Abbrechen und übersteht einen
  Neustart (wie AP-40). **Rückgängig für den ganzen Lauf** schiebt alle Mails zurück; neu angelegte Ordner bleiben stehen
  (nichts wird gelöscht). Jede Verschiebung steht im Protokoll. Lora-Aktion `mail.sort` im Register (AP-38), damit sie auch
  in Abläufen geht.
- **d) Wiederkehrend** (wenn einfach machbar): Aus einem Lauf wird auf Wunsch eine feste Regel oder ein gespeicherter
  Sortierauftrag („jeden Montag die letzte Woche einordnen“) über *Geplante Aufgaben*; Korrekturen der Person dienen
  dem nächsten Lauf als Beispiele (nur nach Rückfrage, AP-39).

**Fertig, wenn**: Die Mailansicht zeigt alle Ordner und Mails eines echten Postfachs (auch über 5 000 Mails) flüssig mit
Lesebereich und Suche; „Ordne die Mails der letzten Woche in X, Y und Z ein“ legt fehlende Ordner in Outlook an, zeigt
eine Vorschau, verschiebt nach einem Klick und lässt sich als Ganzes rückgängig machen – alles mit einem lokalen Modell.

**Betrifft**: `data/Outlook.kt` (`OutlookBridge`), Windows `desktop/ComOutlook.kt`, `data/MailServer.kt`, `net/Imap.kt`,
`ui/MailScreen.kt` (neu aufgebaut), `agent/OutlookTools.kt`, neu `agent/MailSorter.kt`, `agent/ToolActions.kt`,
`agent/LoraPlan.kt`.
- **Umsetzung (09.11.2026, ☑)**: a) `data/MailBox.kt` (Ordnerbaum, Tagesgruppen, Auswahl, Rückgängig) und `ui/MailView.kt`:
  drei Spalten mit ziehbaren Breiten, Liste seitenweise (`OutlookBridge.page` mit `MailQuery`, auch „von:“/„betreff:“),
  Lesebereich mit Anhängen (öffnen, in den Lora-Ordner, *In Outlook öffnen*), Suche und Filter, Mehrfachauswahl,
  Ziehen auf Ordner, gelesen/ungelesen, Ordner anlegen/umbenennen, alles mit Rückgängig und Protokoll; Termine, Regeln
  und Protokoll im Reiter *Übersicht*; Lora kennt die Auswahl (nur Anzahl). Outlook über `Items.Restrict` (Jet/DASL),
  IMAP mit gebündeltem `STATUS`, `STORE`, `RENAME`, `MOVE` mit `COPYUID`. b–d) `agent/MailSorter.kt`: Auftrag aus dem
  Satz (Zeitraum, Quelle, Ziele mit Unterordnern, „ohne Rückfrage“, „jeden Montag“), das lokale Modell liest Kopf und
  Anfang (höchstens 500 Mails je Lauf), Vorschau `ui/MailSortPreview.kt` (umhängen, auslassen, Unsichere gesondert, ein
  Klick *Einordnen*), fehlende Ordner erst beim Einordnen angelegt, Lauf im Hintergrund mit Pause/Abbrechen und
  Fortsetzen nach Neustart, Rückgängig für den ganzen Lauf über die neuen Kennungen (`moveTracked`), Aktion `mail.sort`
  im Register, Regeln oder geplante Aufgabe aus einem Lauf, Korrekturen als Beispiele nur nach Rückfrage (eigene Datei
  auf dem Gerät, nicht im allgemeinen Gedächtnis). Tests `MailViewTest`, `MailSorterTest`. **Rest**: Probe mit echtem
  Outlook (COM ist hier nicht ausführbar) und echtem IMAP-Server; „Alle Ordner“ zeigt bei Outlook höchstens 1 000
  Treffer; Tempo des lokalen Modells auf Dienst-PCs messen.

### AP-70 ☑ Videoeditor: ruhiges Bild, anpassbare und übersichtliche Oberfläche (D) · L (neu, dringend)
Rückmeldung zu 1.0.61: Das Bild der Vorschau **zuckt**, die Oberfläche ist **starr** (feste Spaltenbreiten, nichts
verschieb- oder ausblendbar) und **mäßig übersichtlich**, und beim Überfahren mit der Maus bekommen Knöpfe einen
**seltsamen Schatten** (grauer Kasten, die Beschriftung von einem hellen Balken verdeckt – gesehen an *Klinge (B)*).

**Stufen**
- **a) Zucken beheben** (zuerst): Ursache messen statt raten. Kandidaten: Wechsel zwischen dem Standbild direkt aus der
  Datei und dem Bild aus dem Graphen (andere Größe, anderes Seitenverhältnis, andere Schärfe – auffällig bei Quellen, die
  nicht 16:9 sind), ein leeres Bild zwischen altem und neuem Standbild beim Ziehen des Abspielkopfs, Neustart der
  ffmpeg-Läufe bei jeder Änderung, Titel und Untertitel, die beim Neuzeichnen kurz fehlen. Ziel: Das alte Bild bleibt
  stehen, bis das neue fertig ist; Standbild und Wiedergabe sind gleich groß und deckungsgleich; Scrubben ohne Flackern.
- **b) Hover-Schatten**: Ursache finden (Zustandsebene der Chips und Knöpfe, `FOCUS_VISIBLE` in `ui/Theme.kt` aus AP-30,
  Fokus nach dem Klick …) und zentral beheben: Überfahren tönt nur dezent, die Beschriftung bleibt lesbar. Prüfen, ob die
  ganze App betroffen ist (dieselben Chips gibt es überall). Der deutliche Tastaturfokus aus AP-30 bleibt.
- **c) Anpassbar** (eine Ansicht, keine Einfach-/Expertenansicht): Trenner zwischen Medienpool, Vorschau, Eigenschaften
  und Zeitleiste ziehbar (gemeinsamer `PanelResizer`), Medienpool und Eigenschaften einklappbar, Vorschau groß bzw. im
  Vollbild, die Seitenleiste der App im Editor eingeklappt (per Knopf wieder da), Spurhöhe einstellbar, Spuren
  einklappbar. Die Anordnung wird gemerkt; *Anordnung zurücksetzen*. Taugt auch für kleine Bildschirme (1366×768 bzw.
  1920×1080 bei 125–150 %).
- **d) Übersichtlicher**: Kopfzeile aufräumen (Werkzeuge als Symbolgruppe mit Tooltip und Taste statt langer
  Text-Chips; Rückgängig, Wiederholen und Exportieren rechts), die zweite Leiste (*Clip hinzufügen … Rohschnitt*) in
  Gruppen bzw. ein „+“-Menü, Transportleiste ohne umbrochene Beschriftungen (heute „Teil en (S)“ im runden Knopf),
  Eigenschaften zeigen nur, was zum gewählten Element passt, Hinweiszeile kürzer, die Tour-Karte verdeckt ihr Ziel nicht
  (heute liegt sie über den Clips). Richtung: die Schnitt-Seite von Resolve, eigenständig im Lora-Stil. Leuchtrahmen und
  Tour (AP-41) an die neue Anordnung anpassen.
- **e) Tests**: Bildfolge beim Abspielen, Scrubben und Wechsel Standbild → Wiedergabe → Standbild ohne Sprung in Größe
  und Lage und ohne leere Bilder; Anordnung übersteht einen Neustart; alle Knöpfe bei 1366×768 und 150 % ohne Umbruch.

**Fertig, wenn**: Abspielen und Scrubben eines 1080p-Projekts (auch mit einer Quelle, die nicht 16:9 ist) zucken nicht
mehr; Bereiche lassen sich ziehen, ein- und ausklappen, bleiben nach dem Neustart so und lassen sich zurücksetzen;
Überfahren zeigt keinen grauen Kasten mehr.

**Betrifft**: Windows `ui/VideoScreen.kt`, `ui/VideoTimeline.kt`, `ui/VideoPanels.kt`, `ui/VideoEffectsUi.kt`,
`video/VideoPlayer.kt`; gemeinsam `ui/PanelResizer.kt`, `ui/Theme.kt` (Hover), `ui/LoraTour.kt`.
- **Umsetzung (09.11.2026, ☑)**: a) gemessen mit Testvideos (16:9, 4:3, Hochformat): Standbild und Wiedergabe laufen
  jetzt durch denselben Filter (`VideoRender.fitInto`, vorher 1 Pixel Versatz bei Hochformat), beim Scrubben bleibt
  das letzte fertige Bild stehen statt verworfen zu werden, `PlaybackClock` glättet die Ton-Uhr (vorher Doppelbilder),
  Bearbeitungen ohne Bildänderung starten keinen ffmpeg-Lauf, Titel/Untertitel bleiben beim Neuzeichnen stehen.
  b) Ursache: Material 3 hebt nicht gewählte `FilterChip`s beim Überfahren an, der Schatten schien durch – flache Chips
  in `ui/Chips.kt` für die ganze App, Hover-Tönung 6 %, Tastaturfokus unverändert. c/d) `ui/VideoLayout.kt`:
  ziehbare Trenner (`PanelResizer` auch senkrecht), Medienpool und Eigenschaften einklappbar, Vorschau groß (Alt+F) und
  Vollbild (Strg+F), App-Seitenleiste im Editor eingeklappt (Strg+B), Spurhöhe und eingeklappte Spuren, Anordnung
  gemerkt mit *Anordnung zurücksetzen*, Werkzeuge als Symbole mit Tooltip (`ui/Tip.kt`), Transportleiste mit fester
  Höhe, Tour-Karte neben ihrem Ziel (alle Touren). Tests `VideoPreviewCalmTest`, `HoverLookTest`, `VideoLayoutTest`.
  **Rest**: Prüfung unter Windows mit echter Skalierung und Soundkarte; den „hellen Balken“ selbst nachsehen.

### AP-73 ☑ Rückfrage mit zwei Knöpfen statt Weitergabe · S–M (neu)
**Ziel**: Liegt das Befehlsmodell knapp unter der Schwelle, zeigt Lora die zwei wahrscheinlichsten Aktionen als Knöpfe
(„Meinst du … oder …?“) und dazu „Etwas anderes“ (dann erst das Sprachmodell) – schneller und offline. Die Wahl zählt als
Korrektur für AP-72. Die Untergrenze für die Rückfrage wird wie die Schwelle auf dem Entwicklungsteil des Messkorpus
gewählt; gemessen wird, wie oft die gemeinte Aktion unter den zwei Knöpfen ist.
**Fertig, wenn**: auf dem Prüfteil bei mindestens 80 % der bisher weitergegebenen Befehle die richtige Aktion unter den
zwei Knöpfen steht und freie Anfragen dabei weiter ohne Rückfrage durchgehen (höchstens 5 % mit Knöpfen).
☑ *Erledigt (10.11.2026)*: `CommandRecognizer.choices` – unter der Schwelle die zwei wahrscheinlichsten Befehle (nie
`frei.*`), wenn sie zusammen die Untergrenze erreichen; `LoraStep.Choose` mit zwei Knöpfen und „Etwas anderes“ (dann
Regeln und Sprachmodell wie bisher), vor den Wortregeln. Die Untergrenze **0,3** wählt `tools/befehle/rueckfrage.py`
**nur auf dem Entwicklungsteil** (niedrigste Stufe mit höchstens 2 % freien Anfragen mit Knöpfen; `modell/rueckfrage.json`,
ein Test hält App und Datei zusammen); der Prüfteil wurde danach einmal gemessen. Prüfteil in der App
(`CommandModelTest`): **80,9 %** der bisher weitergegebenen Befehle richtig unter den Knöpfen (38 von 47, knapp),
**3,6 %** der freien Anfragen mit Knöpfen (3 von 84), zusammen 97,0 % der Befehle sofort oder mit einem Tipp, 0,0 %
falsch ausgeführt. Strg+K zeigt weiter nur sichere Befehle; „Lora fragen“ führt zu den Knöpfen.

### AP-72 ☑ Lora lernt aus Korrekturen · M (neu)
**Ziel**: Versteht Lora einen Wunsch falsch und der Nutzer korrigiert („nein, ich meinte …“, ein Tipp auf die richtige
Karte, „Rückgängig“ direkt nach einer Aktion), merkt sie sich das Paar *Wunsch → gemeinte Aktion* **auf dem Gerät** und
trifft es beim nächsten Mal – vor dem Befehlsmodell, als eigene kleine Liste (wie die eigene Liste der
Pseudonymisierung). Die Liste ist unter *Einstellungen › Lora* einsehbar und löschbar; im Tresor wird nichts gemerkt.
Auf Wunsch lassen sich die Paare als Datei exportieren (ohne Namen, vom Nutzer durchgesehen) – Stoff für das nächste
Training des Befehlsmodells (`tools/befehle/`).
**Fertig, wenn**: eine korrigierte Eingabe beim zweiten Mal ohne Sprachmodell richtig ausgeführt wird; die Liste sichtbar,
löschbar und exportierbar ist; nichts davon das Gerät von selbst verlässt.
☑ *Erledigt (10.11.2026)*: `data/LoraCorrections.kt` – Paare auf dem Gerät (höchstens 300, gleiche Schreibweise ohne
Groß-/Kleinschreibung, Akzente und Satzzeichen), vor allem anderen in `LoraAgent` gefragt; eine Aktion eines Werkzeugs
gilt nur, wo es offen ist, Orte und Einstellungen überall. Korrekturen: Knopf bei „Meinst du … oder …?“ (AP-73),
„nein, ich meinte …“ (auch „ich meinte eigentlich …“, "no, I meant …"; gelernt wird, wenn der Rest ein Befehl, ein Ort
oder eine Einstellung ist), Tipp auf einen der aufgelisteten Orte in Loras Antwort, „Rückgängig“ direkt nach einer auf
dem Gerät erkannten Aktion oder einem eingeschalteten Werkzeug – das merkt sich *nicht ohne Rückfrage* für diesen Wunsch.
*Einstellungen › Lora › Was Lora aus Korrekturen gelernt hat*: Schalter (Standard an), Liste mit Löschen je Eintrag und
*Alle löschen*, *Exportieren …* mit Häkchen je Paar als `lora-korrekturen.jsonl` im Format von `tools/befehle/gen.py`.
Im Tresor nichts. Test `LoraCorrectionsTest` (u. a. „nein, ich meinte Dateien“ → beim zweiten Mal ohne Sprachmodell).
Rest: Android ungetestet; ein „Nein“ ohne neuen Wunsch lernt nichts; die Export-Datei enthält echte Eingaben – vor einem
Training noch einmal auf Namen prüfen (`tools/befehle/README.md`).

### AP-74 ☑ Fehlermeldungen in Klartext mit Lösungsknopf · M (bisher Ideen, W2-09)
**Ziel**: Jede Fehlermeldung der App (Server, Schlüssel, Modell nicht verfügbar, Datei gesperrt, Werkzeug aus, kein
Speicher, lokales Modell fehlt …) erscheint als ein verständlicher Satz mit dem passenden Schritt als Knopf – Schlüssel
prüfen, Modell wechseln, Werkzeug einschalten, erneut versuchen, *Problem melden*. Ein gemeinsamer Katalog statt
verstreuter Texte; die technische Meldung bleibt aufklappbar. Auf dem Gerät, ohne Sprachmodell.
**Fertig, wenn**: die häufigsten 30 Fehler (aus Protokollen und Tests gesammelt) einen Klartext und einen Knopf haben und
ein Test prüft, dass keine rohe Exception mehr im Chat oder in Loras Panel landet.
☑ *Erledigt (10.11.2026)*: `ui/ErrorHelp.kt` – ein Katalog mit 30 Regeln (Netz, Schlüssel, Modell weg/überlastet/
ohne Werkzeuge, Chat zu lang, Rate-Limit, Speicher voll, Arbeitsspeicher, Datei gesperrt/fehlt/kaputt/mit Passwort,
Werkzeug aus, lokales Modell und Spracherkennung fehlen, ffmpeg, Outlook, Mikrofon, Postfach nur lokal, GitHub-Token,
Verbindung einrichten …) und je einem Knopf (`ErrorHelp.Fix` → Einstellungsseite, *Modell wechseln* im Chat direkt).
Eigene Sätze der App bleiben, angehängte Technik wird abgetrennt; Programmierfehler (NullPointer, ClassCast …) werden nie
gezeigt, sondern „Da ist etwas schiefgelaufen …“ mit *Problem melden*. Eingebaut in die Fehlerkarte im Chat (auch für
gespeicherte Fehler älterer Chats, *Details* aufklappbar), Loras Panel (Antwort mit Knopf, fehlgeschlagene Aktionen,
Rückgängig, Abläufe) und `Notices.error` (Knopf statt „Erneut versuchen“, wo es keinen Neuversuch gibt). Test
`ErrorHelpTest` (30 Fehler, keine rohe Exception im Chat oder Panel).

### AP-75 ☑ Lora-Verlauf mit „Nochmal“ · S–M (neu)
**Ziel**: Die letzten Befehle an Lora und Strg+K als Liste (Strg+K › Pfeil hoch, im Lora-Panel „Zuletzt“): wiederholen,
vor dem Senden ändern, anheften. Angeheftete Befehle bekommen einen Namen und erscheinen in Strg+K – der erste Schritt zu
eigenen Lora-Befehlen (Ideen). Nur auf dem Gerät gespeichert, abschaltbar, im Tresor nichts.
**Fertig, wenn**: Pfeil hoch in Strg+K und im Lora-Eingabefeld die letzten 20 Befehle durchblättert, „Nochmal“ sie
ausführt und ein angehefteter Befehl unter seinem Namen in Strg+K gefunden wird.
☑ *Erledigt (10.11.2026)*: `data/LoraHistory.kt` (die letzten 20 Wünsche, gleiche nur einmal, angeheftete bleiben;
`lora-verlauf.json` im App-Speicher), Pfeil hoch/runter im Lora-Feld und in Strg+K (Enter schickt den geholten Wunsch
nochmal an Lora), „Zuletzt“ unter Loras Begrüßung mit *Nochmal* und *Anheften* (Name im Dialog), **Eigene Befehle** in
Strg+K (Suche nach Name und Text), *Einstellungen › Lora › Verlauf und eigene Befehle* (Schalter, Lösen, Verlauf/alles
löschen). Aus dem Tresor nichts. Test `LoraHistoryTest`.

### AP-76 ☑ Sprachmodell-Empfehlung nach Messung · M (neu, beantwortet Offene Frage 10)
**Ziel**: Lora misst einmal kurz (unter einer Minute, auf Wunsch), wie schnell die lokalen Sprachmodelle auf diesem
Gerät wirklich sind – Wörter je Sekunde, Zeit bis zum ersten Wort, mit und ohne Grafikkarte –, und empfiehlt danach das
passende Modell für Postfach (AP-68/69), kurze Aufgaben im Mischmodus (AP-71) und vertrauliche Projekte, statt nur nach
Arbeitsspeicher zu schätzen (`LocalCatalog.fit`). Das Ergebnis steht unter *Modelle auf diesem Gerät* und fließt in den
Mischmodus ein (was „läuft gut“ heißt).
**Fertig, wenn**: die Messung für installierte Modelle Zahlen liefert, die Empfehlung daraus begründet wird („schafft
etwa 12 Wörter je Sekunde – gut für kurze Aufgaben, zu langsam für lange Mails“) und der Mischmodus nur Modelle nimmt,
die eine Mindestgeschwindigkeit erreichen.
☑ *Erledigt (10.11.2026)*: `local/LocalSpeed.kt` – *Messen* lässt jedes installierte Modell eine kurze Mail schreiben
(Streaming direkt am llama-server, Laden nicht mitgezählt): Wörter je Sekunde und Zeit bis zum ersten Wort, mit
Grafikkarte und – wenn vorhanden – ohne (`LocalLlm.provider(cpuOnly)`). Gespeichert je Gerät (`hardware` muss passen);
Empfehlung je Zweck (Postfach ≥ 12 Wörter/s, kurze Aufgaben ≥ 6 Wörter/s und erstes Wort ≤ 6 s, Vertrauliches ≥ 3);
`LoraCommands.localModel(good = true)` nimmt gemessene Modelle nur ab 6 Wörtern/s, ungemessene wie bisher nach
`LocalCatalog.fit`. Test `LocalSpeedTest`. Rest: in der Cloud nicht an echter Hardware gemessen – die Schwellen sind
Startwerte und werden mit den ersten Messungen auf Dienst-PCs geprüft (Offene Frage 10).

### AP-77 ☑ Probelauf und „Alles rückgängig“ · M (neu)
**Ziel**: Lora weist auf gefährliche Aktionen hin und berät, bevor etwas geschieht. „Was würde passieren, wenn …?“ zeigt
nur die Vorschau (was verschoben, gelöscht, überschrieben würde, wie viele Dateien, was sich nicht zurückholen lässt) und
einen Rat („Das betrifft 214 Dateien – erst an einem Ordner ausprobieren?“). Dazu ein gemeinsames **„Alles rückgängig
seit heute Morgen“**: die Aktionen des Tages aus dem Protokoll (AP-38) als Liste, einzeln oder gesammelt zurücknehmbar.
**Fertig, wenn**: jede riskante Aktion einen Probelauf hat, die Vorschau sagt, was unumkehrbar wäre, und die Aktionen eines
Tages sich in einem Schritt zurücknehmen lassen (mit Hinweis, was nicht mehr geht).
☑ *Erledigt (10.11.2026)*: `agent/DryRun.kt` – „Was würde passieren, wenn …“, „Probelauf: …“, "what would happen if …"
plant die Aktion und zeigt nur die Vorschau (*Doch ausführen* / *Lassen*); jede riskante Vorschau zeigt `DryRun.notes`
(wie viele, was in den Papierkorb geht, was sich nicht über Lora zurücknehmen lässt – die Video-, Folien-, Tabellen- und
Seitenlösch-Aktionen nur im Werkzeug mit Strg+Z –, Rat ab 20 bzw. 50 Dateien; `ActionPlan.irreversible` für eigene
Hinweise). `DayJournal` sammelt die Aktionen des Tages mit ihrem Rückgängig (auch Schritte von Abläufen); „Mach alles
von heute rückgängig“ und *Einstellungen › Lora › Heute erledigt* zeigen die Liste – einzeln oder *Alle zurücknehmen*,
Zeilen von vor einem Neustart aus dem Protokoll mit Hinweis „lässt sich nicht mehr zurücknehmen“. Test `DryRunTest`.

### AP-78 ☑ Eigene Wörter und Wissenspakete · L (neu, in Stufen)
**Ziel**: Lora versteht die Sprache der Professur. **a) Eigene Wörter**: Abkürzungen und Namen (z. B. „ZLSB“, „MHB“,
Projektnamen) mit Erklärung und Synonymen – eigene Einträge unter *Einstellungen › Lora*; Befehlsmodell, Suche, Strg+K und
die Sprachmodelle (als kurzer Kontext) nutzen sie. **b) Wissenspaket „Lehramt an der TU Dresden“**: eine Datenbank zum
Nachladen – Abkürzungen, Einrichtungen, Studiengänge und Schularten, Module und Praktika, Fristen und Abläufe,
Ansprechstellen, Ordnungen – jeder Eintrag mit **Quelle und Stand** (nur aus öffentlichen, offiziellen Seiten der TU
Dresden und des Freistaats; nichts Erfundenes, Unsicheres bleibt draußen). Wer es nicht braucht, lässt es weg.
**c) „Inhalte zum Nachladen“**: eine leicht verständliche Übersicht in der App (und im Dialog vom ersten Start) über
alles, was sich nachladen lässt – Befehlsmodell, Erkennungsmodell, Sprachmodelle, Spracherkennung, Videoeditor,
Wissenspakete – je mit Zweck, Größe, Laden/Löschen. Auslieferung wie die Modelle (eigener Branch, fester Commit,
SHA-256); Pakete lassen sich aktualisieren.
**Fertig, wenn**: eigene Wörter in Befehlen und Suche wirken; das Lehramts-Paket sich laden, aktualisieren und löschen
lässt und jede Angabe eine Quelle hat; die Übersicht alle nachladbaren Inhalte zeigt.
☑ *Erledigt (10.11.2026)*: **a)** `data/Vocabulary.kt` – eigene Wörter (*Einstellungen › Lora › Eigene Wörter*):
Befehlsmodell und Regeln bekommen für eine andere Schreibweise das Wort selbst („öffne das MHB“ → „… Modulhandbuch“),
die Dateisuche findet über alle Schreibweisen, Chats und Lora bekommen passende Einträge als kurzen Kontext (nur die im
Chat vorkommenden, Paketeinträge mit Quelle und Stand), „Was ist …?“ beantwortet Lora auf dem Gerät. **b)** Wissenspaket
`tools/wissen/lehramt-tud.json`: 39 Einträge aus Seiten des ZLSB (v. a. *Lehramtsstudium von A–Z*), des LaSuB und der
TU Dresden, abgerufen am 10.11.2026, jeder mit https-Quelle und Stand, keine Personennamen; Einträge ohne Quelle fallen
beim Laden heraus. **c)** *Einstellungen › Inhalte zum Nachladen* (auch in Strg+K) mit Befehlsmodell,
Erkennungsmodell, Sprachmodellen, Spracherkennung, Videoeditor-Paket (PC) und Wissenspaketen (Laden, Aktualisieren bei
neuerer Version, Löschen); im Dialog vom ersten Start ankreuzbar. Test `VocabularyTest` (u. a. Katalog = Datei, SHA-256).
Abweichung: Das Paket liegt nicht auf einem eigenen Branch, sondern in `tools/wissen/` dieses Repos am festen Commit
`da9c3179` – der Commit muss erreichbar bleiben (Merge ohne Squash); sonst Datei auf einen Branch `wissen-pakete` legen
und `KnowledgePacks.COMMIT` anpassen.

### AP-79 ☑ Hausstil-Prüfung und Barrierefreiheits-Check · M–L (bisher Ideen)
**Ziel**: Dokumente, Folien und Lehrmaterial vor der Abgabe prüfen. **Hausstil**: Corporate Design der TU Dresden
(Schriften, Farben, Logo, Ränder), eigene Regeln der Professur (Gendern, Zitierstil, Schreibweisen aus AP-78).
**Barrierefreiheit**: Überschriftenstruktur, Alternativtexte, Kontraste, Lesereihenfolge, Tabellenköpfe, Sprache,
verständliche Linktexte, PDF/UA-Tags (mit AP-26/60). Ergebnis als Liste mit „Beheben“ je Punkt, wo es automatisch geht.
**Fertig, wenn**: Dokument, Präsentation und PDF einen Prüfbericht mit Fundstellen bekommen und die häufigsten Mängel
sich mit einem Klick beheben lassen.
☑ *Erledigt (10.11.2026)*: `docs/StyleCheck.kt` – Hausstil (eigene Regeln *Einstellungen › Dokumente & Logos ›
Hausstil* und aus geteilten Paketen, „TU Dresden“ ohne Bindestrich, Abkürzungen nach DIN 5008, doppelte Leerzeichen) und
Barrierefreiheit (übersprungene Überschriftenebenen, mehrere Hauptüberschriften, fette Zeilen statt Überschriften, leere
Überschriften, Bilder ohne Alternativtext, Linktexte wie „hier“, nackte Adressen, Tabellen ohne Kopfzeile, Zeilen in
Großbuchstaben, lange Dokumente ohne Überschriften, Folien ohne Titel), jede Fundstelle mit „Absatz n“/„Folie n“.
Lora-Aktionen `doc.check`/`doc.fix_style` und `slides.check`/`slides.fix_style` (Beheben mit Vorschau und Rückgängig),
auch als Regel auf dem Gerät („Prüfe Hausstil und Barrierefreiheit“, „Behebe …“). Der Dateibericht (*Barrierefreiheit
und Hausstil*) prüft bei Word/PowerPoint zusätzlich Schriften (Noto Sans) und Farben (TUD-Palette) und die
Hausstil-Regeln. PDF: wie bisher `AccessibilityCheck` (Tags, Sprache, Titel …). Test `StyleCheckTest`. Rest:
Corporate Design nur Schriften und Farben (Logo und Ränder nicht), Beheben in Word-/PowerPoint-Dateien nicht automatisch.

### AP-80 ☑ Rollenauswahl beim ersten Start · S (bisher Ideen, W2-08)
**Ziel**: Im Dialog vom ersten Start (mit der Modellauswahl aus AP-71) eine Frage „Wofür nutzt du Lora vor allem?“ –
Lehre, Forschung, Verwaltung, Studium (mehrere möglich). Daraus: welche Werkzeuge sichtbar sind, welche Vorschläge und
TU Vorlagen oben stehen, welche Inhalte zum Nachladen (AP-78) empfohlen werden. Später änderbar.
**Fertig, wenn**: die Auswahl Startseite, Werkzeuge und Empfehlungen sichtbar anpasst und sich in den Einstellungen ändern lässt.
☑ *Erledigt (10.11.2026)*: `data/Roles.kt` – Frage im Dialog vom ersten Start (Chips, mehrere möglich); eine Rolle hakt
ihre Werkzeuge an (Lehre: PDF, Lernspiele, Stapel, Video; Forschung: PDF, Tresor, Anträge; Verwaltung: PDF, Mail &
Kalender, Stapel; Studium: PDF) und das Lehramts-Paket (Lehre, Studium). Die TU Vorlagen zeigen die Bereiche der Rollen
zuerst, Loras Startvorschläge folgen den Rollen. *Einstellungen › Lora › Wofür nutzt du Lora?* (Werkzeuge nur ein, nie
aus; empfohlenes Paket mit *Laden*). Test `RolesTest`.

### AP-81 ☑ Videoexport-Warteschlange übersteht einen Neustart · S (Rest AP-57, D)
**Fertig, wenn**: wartende und abgebrochene Exporte nach einem Neustart der App wieder in der Warteschlange stehen und
auf Knopfdruck (oder von selbst) weiterlaufen.
☑ *Erledigt (10.11.2026)*: `ExportQueue` speichert wartende und laufende Einträge (Projekt, Ziel, Vorgabe) bei jedem
Statuswechsel in `video-exporte.json`; beim Start kommen sie als *unterbrochen* zurück (halbe Datei entfernt), mit
*Fortsetzen*, *Alle fortsetzen* und einem Hinweis – oder von selbst, wenn *Unterbrochene Exporte nach dem Start von
selbst fortsetzen* an ist. Unterbrochene halten das Beenden nicht auf. Test `ExportQueueRestartTest`.

### AP-82 ☑ Vorschau für Video und Ton in der Schnellansicht · S (Rest AP-66)
**Fertig, wenn**: die Leertaste in *Dateien* Videos und Tonaufnahmen abspielt (Start/Pause, Springen, Dauer) – am PC und,
wo sinnvoll, auf Android.
☑ *Erledigt (10.11.2026)*: `ui/MediaLook.kt` – Video und Ton in der Schnellansicht: Start beim Öffnen, Start/Pause,
±10 s, Regler, Zeit/Dauer, Tasten K/J/L (Leertaste schließt weiter, ←/→ blättert). PC: Bild und Ton über den
Vorschau-Player des Videoeditors (`VideoPlayer`, ein Clip), Ton über den Player der Transkription; Android: `VideoView`
bzw. derselbe Player der Transkription (`ui/MediaPreview.kt` je Plattform). Test `MediaLookTest` (mit ffmpeg: erstes
Bild, Springen). Rest: Android ungetestet.

### AP-83 ☑ Lora-Pakete teilen · M (neu, nach AP-78)
**Ziel**: Eigene Befehle (AP-75), eigene Wörter (AP-78), eigene TU Vorlagen und Hausstil-Regeln (AP-79) als **eine
Datei** exportieren und im Team importieren – mit Vorschau, was hinzukommt und was ersetzt würde, und ohne persönliche
Daten (Chats, Gedächtnis, Schlüssel bleiben draußen). Dasselbe Format wie die Wissenspakete aus AP-78, sodass ein
geteiltes Paket in der Übersicht „Inhalte zum Nachladen“ erscheint und sich wieder entfernen lässt.
**Fertig, wenn**: ein Paket von einem PC exportiert, auf einem zweiten importiert und dort wieder entfernt werden kann.
☑ *Erledigt (10.11.2026)*: `data/LoraPackages.kt` – *Einstellungen › Lora › Lora-Paket teilen*: Export mit Name,
Beschreibung und Häkchen je Teil (angeheftete Befehle ohne Zeitpunkt, eigene Wörter ohne Quellen, eigene TU Vorlagen,
Hausstil-Regeln) als `….lora-paket.json` im Format der Wissenspakete; Import zeigt *Kommt hinzu* und *Wird ersetzt*;
Wörter, Befehle (Strg+K) und Regeln wirken als Paket, Vorlagen kommen zu den eigenen; *Entfernen* (auch unter *Inhalte
zum Nachladen*) nimmt das Paket und seine unveränderten Vorlagen wieder heraus. Chats, Gedächtnis, Verlauf, Schlüssel
bleiben draußen. Test `VocabularyTest`.

### AP-84 ☑ Lora in mehreren Fenstern · M–L (neu, D)
**Ziel**: Mehrere Chats und Werkzeuge nebeneinander. Rechtsklick auf einen Chat (Seitenleiste, Strg+K, Suche) bietet
zusätzlich **„In neuem Fenster öffnen“**; ebenso für Dokumente und das Lora-Panel. Jedes Fenster hat seine eigene
Navigation, alle teilen dieselben Daten (ein Prozess, keine zweite Instanz); laufende Antworten, Entwürfe und Updates
bleiben je Fenster richtig, Fenstergröße und -ort werden gemerkt. Zu klären: Tabs innerhalb eines Fensters zusätzlich
(wie im PDF-Werkzeugkasten) oder nur Fenster.
**Fertig, wenn**: zwei Chats in zwei Fenstern gleichzeitig antworten können, ein Chat nie in zwei Fenstern
gegeneinander schreibt und das Schließen eines Fensters die anderen nicht beendet.
☑ *Erledigt (10.11.2026)*, PC: `ui/ExtraWindows.kt` – *In neuem Fenster öffnen* im Chat-Menü der Seitenleiste, im
Menü von Dokumenten in *Dateien* und oben in Loras Panel. Jedes Fenster hat eigene View-Models (zwei Chats antworten
gleichzeitig), alle teilen Daten und Läufe (ein Prozess); ein Chat ist nur in einem Fenster offen – das Hauptfenster
zeigt dafür *Zum Fenster* / *Hierher holen*; Schließen eines Fensters beendet nichts sonst; Größe und Ort je Art gemerkt.
**Entschieden: nur Fenster, keine Tabs** (zum Bestätigen durch den Hauptnutzer). Test `ExtraWindowsTest`. Rest: Strg+K
und die Chatsuche bieten den Eintrag noch nicht; neue Chats und andere Orte aus einem Zusatzfenster öffnen sich im
Hauptfenster.

### AP-85 ☑ Große Anhänge vollständig nutzen · M (neu)
**Befund (10.11.2026)**: `AttachmentReader.MAX_CHARS = 120_000` – jede angehängte Datei wird nach 120 000 Zeichen
(etwa 30 000 Tokens, rund 60–70 Seiten) abgeschnitten, der Rest geht nicht mit. Die Grenze stammt aus der Zeit kleiner
Kontextfenster und ist fest, unabhängig vom Modell; die heutigen Server-Modelle fassen 262 000 bis 1 000 000 Tokens.
Dazu kommt, dass der Text des Anhangs im Chat gespeichert und bei jeder Nachricht erneut pseudonymisiert und gesendet wird.
**Ziel**: Die Grenze richtet sich nach dem gewählten Modell (ein fester Anteil seines Kontextfensters, bei mehreren
Anhängen geteilt); ist eine Datei größer, fragt die App statt still zu kürzen: *ganz senden mit einem Modell, das es
fasst* (Vorschlag: GLM-5.3 Flash) · *nur die passenden Stellen* (Suche im Anhang wie bei „Frag diesen Ordner“, AP-58) ·
*abschnittsweise zusammenfassen*. Die Anzeige am Anhang sagt, wie viel wirklich mitgeht (Seiten, Anteil); lokale Modelle
mit kleinem Kontext bekommen von selbst nur die passenden Stellen. Speicher und Tempo beachten (lange Texte nicht
mehrfach im Chat halten, Pseudonymisierung je Anhang nur einmal).
**Fertig, wenn**: ein 300-Seiten-PDF mit einem großen Modell vollständig ankommt, bei kleineren Modellen eine klare Wahl
erscheint und nirgends mehr still gekürzt wird.
☑ *Erledigt (10.11.2026)*: `files/AttachmentBudget.kt` – gelesen wird ganz (Sicherheitsgrenze 6 Mio. Zeichen); je Anfrage
dürfen alle Anhänge 70 % des Kontextfensters des Modells nutzen (nach Länge geteilt). Passt es nicht, fragt
`LargeAttachmentDialog`: *Ganz senden mit GLM-5.3 Flash* (wechselt das Modell), *Nur die zur Frage passenden Stellen*
(Abschnitte nach Wörtern der Frage gewichtet, Anfang immer dabei, Seitenangaben, Auslassungen „[…]“) oder
*Abschnittsweise zusammenfassen* (mit dem Modell des Chats, gespeichert am Anhang). Die Karte am Anhang zeigt, wie viel
mitgeht; was gekürzt mitgeht, sagt es auch dem Modell in der ersten Zeile. Lokale Modelle bekommen von selbst die
passenden Stellen. Anhänge zählen nicht gegen den Verlauf (keine Zusammenfassung des Chats wegen einer großen Datei);
lange Anhänge werden je Chat nur einmal pseudonymisiert (Cache, neu bei neuen Platzhaltern). Test `AttachmentBudgetTest`
(300-Seiten-Text ganz mit GLM-5.3 Flash, Wahl bei kleineren Modellen, nichts still gekürzt).

---

## Offene Fragen

1. **Startseite** (AP-02, AP-37): Wortlaut der Chips und ihrer Starts final abstimmen (umgesetzt ist der Vorschlag aus AP-37).
2. ~~**Outlook über COM** (AP-13)~~: entschieden – COM über JNA (schon in der App), ohne PowerShell und ohne
   Admin-Rechte. Offen nur: Läuft es auf den Dienst-PCs ohne Rückfrage von Outlook (Virenscanner aktuell)?
3. **Exchange der TU** (Rest von AP-27): selbst betrieben (EWS) oder Exchange Online (Graph mit Freigabe der IT)? Wann
   stellt die TU auf das neue Outlook um? Mail über IMAP ist seit AP-27 da; offen sind Kalender und Aufgaben über den
   Server sowie die Zugangsdaten der TU zum Vorbelegen.
4. **OPAL** (AP-17, AP-20): Lassen sich HTML-Dateien/ZIP-Pakete als Lernelement einbinden? In welchem Format kommen
   Abgaben aus OPAL?
5. ~~**Word-Prüfsammlung** (AP-45)~~: entschieden (07.11.2026) – nachgebaute Dokumente reichen (`PruefsammlungTest`),
   echte Dateien kommen nicht ins Repo.
6. ~~**Gesichter verpixeln** (AP-54)~~: entschieden (08.11.2026) – **YuNet** (`face_detection_yunet_2023mar.onnx` aus OpenCV
   Zoo, MIT-Lizenz, 227 KB) im Videoeditor-Paket; es läuft auf der ONNX Runtime, die die App für die Spracherkennung
   (sherpa-onnx) schon mitbringt, das Paket wächst also nur um das Modell.
7. **Explorer-Kontextmenü** (AP-59): Ist ein Eintrag je Benutzer (ohne Admin) auf den Dienst-PCs erlaubt? Umgesetzt als
   Schalter (anfangs aus) unter *Einstellungen › Windows*; offen bleibt nur die Prüfung auf den Dienst-PCs.
8. ~~**PDF aus derselben Seitenaufteilung** (AP-43/45)~~: entschieden (07.11.2026) – reicht so. Das PDF folgt denselben
   Regeln wie die Seitenansicht (Witwen/Waisen, Überschriften beim Folgetext, erste/gerade Seiten, Tabellen mit
   wiederholter Kopfzeile, Abschnitte) und wird weiter über HTML gesetzt; kein eigener PDF-Textsatz.
9. ~~**Erkennungsmodell ausliefern** (AP-67)~~: entschieden (09.11.2026) – **Download wie die lokalen Sprachmodelle**,
   angeboten als Popup beim ersten Nutzen der Pseudonymisierung oder beim Öffnen von *Einstellungen › Datenschutz*.
   Quelle: Branch `pii-modell` dieses (öffentlichen) Repos an festem Commit, gepackt 72 MB, mit SHA-256 geprüft. Ohne
   Modell laufen die Regeln allein weiter. Basismodell: **gbert-base** (MIT), nach dem Vergleich auf dem Entwicklungsteil.

10. **Lokales Modell fürs Postfach** (AP-68, AP-69; Messung seit AP-76 in der App: *Modelle auf diesem Gerät › Messen*): Welches lokale Modell reicht auf den Dienst-PCs ohne Grafikkarte, um
    Mails in vertretbarer Zeit zusammenzufassen und einzuordnen – Qwen 3.5 4B, Gemma 4 E4B oder größer (9B mit
    Grafikkarte)? Messung mit 200 Mails vor der Vorgabe. Und: Darf ein Text aus einem API-Chat auf Klick der Person als
    Entwurf zurück ins Postfach (gespeichert von der App, nicht vom API-Modell), oder nur über Kopieren?

---

## Anhang: Plattformen

Windows bekommt alles; Android nur, was dort sinnvoll ist.

| Werkzeug | Android | Windows |
| --- | --- | --- |
| Chat-Design, Übersicht, Modi, Rückfragen (AP-01, 02, 05, 06) | ☑ | ☑ |
| Lora-Navigator (AP-07) | ☐ | ☐ |
| Pseudonymisierung (AP-03) | ☑ | ☑ |
| Tresor (AP-10) | ☑ (eigene PIN je Gerät, AP-63) | ☑ |
| PDF-Werkzeugkasten (AP-11, 15, 26) | Betrachter, Seiten, Markieren, Unterschreiben, KI, OCR per Bildmodell, Vergleichen | voll |
| Sync (AP-12, AP-28) | ☑ (über Nextcloud, mit Literatur, Wissen, Tresor, im Hintergrund – AP-63) | ☑ |
| Mail und Kalender (AP-13, AP-27) | ☑ Mail über den Server (ohne Kalender) | ☑ (Outlook oder Server) |
| Dateiverwaltung (AP-18) | ☑ (Dokumente, Wissenssammlung) | ☑ (dazu Ablageorte und Projektordner) |
| Browser neben dem Chat (AP-14) | ☑ einfach (WebView, AP-63) | ☑ |
| Office (AP-16) | ☑ ansehen, bearbeiten, Lora; Dateien in der App mit Kopie in einen gewählten Ordner; Folien auf dem zweiten Bildschirm (AP-64) | ☑ voll, Lora-Ordner |
| Dokumenten-Scanner (AP-62) | ☑ Kamera, OCR per Bildmodell | ☑ WIA-Scanner, Bilder, OCR von Windows |
| Diktat (AP-65) | ☑ | ☑ |
| Schnellansicht (AP-66) | ☑ über ⋮ › Vorschau | ☑ Leertaste, Details-Spalte |
| Lernspiele (AP-17) | ansehen, spielen | voll |
| Erstellen (AP-24) | TU Vorlagen; Gestalten nicht geplant | ☑ (mit Gestalten) |
| Videoeditor (AP-25) | – (nicht geplant) | ☑ |
| Nachladbare Erweiterungen (AP-25) | – | ☑ |
| Stapel & Massenkontrolle (AP-20) | ☑ (Dateien, ZIP, Nextcloud) | ☑ (dazu Ordner) |
| Antrags-Werkstatt (AP-21) | ☑ | ☑ |
| Nextcloud (AP-22) | ☑ | ☑ |
| MCP-Client (AP-23) | ☑ (Fern-Server) | ☑ (dazu lokale Server) |
| Agent in lokalen Ordnern | – (nur Lesen im Projektordner) | ☑ |

---

## Anhang: Craft-Apps (getartcraft.com)

Die Craft-Apps des ArtCraft Team sind Open Source unter **MIT oder Apache-2.0**, in Rust geschrieben und Stand 10/2026
noch Alpha bzw. in Entwicklung. Eine Anbindung per MCP ist entschieden: **nein**. Direkt einbauen lässt sich ihr Code
nicht (Rust, unsere App ist Kotlin), aber Algorithmen, Datenmodelle und Abläufe dürfen übernommen und nach Kotlin
übertragen werden.

Regeln bei Übernahme:
- Lizenztext und Copyright-Hinweis („ArtCraft Team and the ArtCraft contributors“) in die Drittanbieter-Hinweise der App
  aufnehmen und die Herkunft im Code-Kommentar nennen.
- Name, Logo und „A“-Zeichen von ArtCraft sind Marken und nicht freigegeben – nicht verwenden.
- Mitgelieferte Schriften, Icons und Assets haben eigene Lizenzen (ATTRIBUTION.md) – einzeln prüfen.
- Vor der Übernahme die Lizenz des jeweiligen App-Repos prüfen (bisher nur Haupt-Repo und Launcher geprüft).

| Werkzeug | Craft-App | Stand dort | Vorschlag |
| --- | --- | --- | --- |
| PDF (AP-11, 15, 26) | PdfCraft | Early alpha | Selbst schreiben auf PDFBox (vorhanden); PdfCraft nur für Abläufe und Bedienung ansehen |
| Dokument (AP-16) | WordCraft | In Entwicklung | Selbst schreiben (Editor vorhanden); Seitenumbruch- und Layoutlogik ggf. ansehen |
| Tabelle (AP-16) | GridCraft | In Entwicklung | Selbst schreiben (`SheetEngine` vorhanden); Formelfunktionen ggf. abgleichen |
| Präsentation (AP-16) | DeckCraft | In Entwicklung | Selbst schreiben (Editor vorhanden) |
| Kreativbereich (AP-24) | DesignCraft, VectorCraft, PhotoCraft | In Entwicklung / Early alpha | **Entschieden: selbst geschrieben** (Leinwand-Modell, Renderer, Export); für boolesche Operationen und Bildfilter später ansehen |
| Videoeditor (AP-25) | FilmCraft, EffectCraft | In Entwicklung | **Entschieden: selbst geschrieben** auf ffmpeg (ein Filtergraph je Export) |
| Nicht übernommen | LightCraft, SoundCraft, CADCraft, FigCraft | – | kein Bedarf an der Professur |
