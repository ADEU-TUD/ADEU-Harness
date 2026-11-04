# Arbeitsplan Lora (bis 1.0.43 „ADEU LLM“) – Runde 2

Stand: 03.11.2026 · Lebendes Dokument: Status hier pflegen, erledigte Pakete abhaken und in der README beschreiben.
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
| E-Mail/Kalender | Die Kolleg:innen nutzen das **klassische Outlook** → zuerst lokal über COM (ohne IT-Freigabe). Der Server-Weg (Exchange/IMAP) kommt erst, wenn jemand das neue Outlook nutzt. |
| Synchronisierung | **Einfach wie bei Obsidian**: Datenordner in einen Cloud-Ordner (Nextcloud, Google Drive, OneDrive), der auf beiden PCs eingerichtet ist. Mehrere PCs zuerst, Android ist nice to have. |
| Gemeinsame Daten | Keine Professur-Datenbank. Höchstens irgendwann ein gemeinsamer Ordner für TU Vorlagen (Ideen). |
| Anbindungen | Wichtige Dienste **fest eingebaut** (zuerst Nextcloud) statt zuerst allgemeines MCP. |
| Eigene Werkzeuge statt Fremd-Apps | Selbst bauen, gehört vollständig uns. Die Craft-Apps (getartcraft.com) werden **nicht** per MCP angebunden; je Werkzeug wird geprüft, ob Code übernommen oder selbst geschrieben wird (*Anhang: Craft-Apps*). |
| Eine App | Kein Launcher mit mehreren Apps. Werkzeuge werden ein- und ausgeblendet (AP-02); große Teile, die nicht alle brauchen, sind **nachladbare Erweiterungen** über den Updater (seit AP-25: Videoeditor-Paket; die Browser-Engine wird mit aufgeführt). |
| Sprachen | Englisch ist vollständig (AP-29), keine weiteren Sprachen. |
| Gestrichen | Qualitätstests für Modi (bisher Q-01), Usability-Tests (Q-03), Professur-Datenbank mit Rollen/Richtlinien/Audit-Log (W4-03), Betreuungs-Cockpit, weitere Sprachen, Tagesbriefing (Runde 2), Sprachbefehle für die App (Chat-Sprachmodus und Diktat reichen). (Lora-Animationen als eigenes Paket, bisher W2-07, kamen auf Wunsch mit AP-36 doch.) |

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
| **Jetzt** – Lora zuerst | AP-38 Lora handelt in Werkzeugen · AP-39 Ein Gedächtnis für Lora · AP-40 Abläufe über Werkzeuge · AP-41 Leuchtrahmen überall und Touren · AP-42 Lora-Dateidialog |
| **Danach** – Editor-Kern und Office | AP-43 Gemeinsamer Text-Kern · AP-44 Fließtext-Editor · AP-45 Word-Treue · AP-46 Gliederung und Formatvorlagen · AP-47 Lora schreibt im Dokument · AP-48 Tabellen wie Excel · AP-49 Diagramme und Lora rechnet · AP-50 Gemeinsame Leinwand für Folien und Gestalten · AP-51 Folien wie PowerPoint |
| **Danach** – Video | AP-52 Mehrere Spuren und echte Vorschau · AP-53 Schnittwerkzeuge · AP-54 Einfache Effekte · AP-55 Textbasierter Schnitt · AP-56 Bildschirmaufnahme · AP-57 Export mit GPU |
| **Danach** – Dateien | AP-58 Frag diesen Ordner · AP-59 Windows-Integration |
| **Später** | AP-60 PDF-Reste · AP-61 Diagramme und Whiteboard · AP-62 Dokumenten-Scanner · AP-63 Sync- und Android-Reste · AP-64 Android zieht nach · AP-65 Diktat |

Abhängigkeiten: AP-38 vor AP-40 und vor allen Lora-Teilen der späteren Pakete (jedes neue Werkzeug liefert seine
Lora-Aktionen über dieselbe Schnittstelle) · AP-39 vor AP-40 · AP-43 vor AP-44 vor AP-45, AP-46, AP-47 · AP-43 vor AP-48
(Zelltext) und AP-50 (Textfelder) · AP-48 vor AP-49 · AP-50 vor AP-51 und AP-61 · AP-52 vor AP-53 vor AP-54, AP-55 ·
AP-52 vor AP-57 · AP-42 vor AP-59.

Reihenfolge in *Danach*: erst Editor-Kern und Dokument (AP-43 bis AP-47), dann Video (AP-52, AP-53), dann Tabellen und
Folien, dann die übrigen Video- und Dateipakete. Die Video- und Office-Stränge sind unabhängig voneinander und dürfen sich
abwechseln, wenn ein Strang wartet.

---

## Jetzt

### AP-38 ◐ Lora handelt in Werkzeugen · XL (neu, in Stufen)
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
  **Rest**: Stufe d (Video) mit AP-52 ff.; Tabellen/Folien-Aktionen; Datenschutzstufe/Tresor für Aktionen prüfen.

### AP-39 ☐ Ein Gedächtnis für Lora · M (neu)
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

### AP-40 ☐ Abläufe über Werkzeuge · L (neu)
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

### AP-41 ☐ Leuchtrahmen überall und Touren · M (Rest von AP-07, neu)
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
  Android behält die Systemauswahl (Speicherzugriff nur über sie). Rest: Ordner und Dateien im Dialog umbenennen/löschen.

---

## Danach

### AP-43 ◐ Gemeinsamer Text-Kern · XL (neu, Grundlage)
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

### AP-44 ◐ Fließtext-Editor für Dokumente · XL (neu, ersetzt den Block-Editor)
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

### AP-45 ◐ Word-Treue · L (neu, ersetzt „Schreiben ins Original“ für Dokumente)
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

### AP-46 ☐ Gliederung und Formatvorlagen · M (neu)
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

### AP-48 ☐ Tabellen wie Excel · XL (neu, in Stufen)
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

### AP-49 ☐ Diagramme und Lora rechnet · M–L (neu)
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

### AP-50 ☐ Gemeinsame Leinwand für Folien und Gestalten · L (neu, mit Gestalten-Resten)
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

### AP-51 ☐ Folien wie PowerPoint · L (neu)
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

### AP-52 ☐ Video: mehrere Spuren und echte Vorschau (D) · XL (neu, Grundlage)
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

### AP-53 ☐ Video: Schnittwerkzeuge (D) · L (neu)
- **Medienpool** mit Ordnern (Bins), Vorschaubildern, Dauer, Suche; **Quellmonitor** mit In/Out-Punkten, *Einfügen* und
  *Überschreiben* (wie Resolve), Ziehen in die Zeitleiste.
- **Schnitt**: Klinge (B), Auswahl (A), Ripple-, Roll- und Slip-Trimmen, Magnet-Zeitleiste (Lücken schließen sich auf
  Wunsch), Verknüpfen von Bild und Ton, Spuren sperren/stumm/ausblenden, Marker mit Notiz (auch als Kapitel nutzbar).
- **Mehrere Tonquellen** synchronisieren (über die Wellenform), z. B. Kamera + Ansteckmikrofon bei Vorträgen/Interviews.
- Tastenkürzel nah an Resolve, Übersicht unter `?`.
- **Fertig, wenn**: Ein Vortrag mit zwei Tonquellen lässt sich über Quellmonitor und Ripple-Trimmen schneiden, ohne Lücken
  von Hand zu schließen.
- Betrifft: Windows `ui/VideoScreen.kt`, `video/*`.

### AP-54 ☐ Video: einfache Effekte (D) · M–L (neu)
- **Zoom und Schwenk mit Keyframes** (z. B. auf einen Bildschirmbereich im Screencast), **Hochformat-Reframe** für
  kurze Clips.
- **Verpixeln/Weichzeichnen** von Bereichen, die dem Objekt folgen; für Forschungsmaterial **Gesichter automatisch
  finden** (lokal) und verpixeln, Stimme verfremden – Ergebnis im Agent-Protokoll.
- **Geschwindigkeit** (0,25×–4×, Ton mit Tonhöhenkorrektur), **Standbild**, Rückwärts.
- **Einfache Bildkorrektur**: Helligkeit, Kontrast, Sättigung, Weißabgleich automatisch – kein Color Grading.
- **Fertig, wenn**: In einem Beobachtungsvideo werden Gesichter automatisch verpixelt; ein Screencast zoomt per Keyframes
  auf einen Bereich.
- Betrifft: Windows `video/*`, `ui/VideoScreen.kt`; lokales Erkennungsmodell im Videoeditor-Paket (AP-25).

### AP-55 ☐ Video: textbasierter Schnitt (D) · M (neu)
- Das **Transkript** liegt als Text neben der Zeitleiste; Wörter sind mit der Zeit verknüpft (Klick springt).
- **Text löschen = Video schneiden** (wie Descript), Absätze verschieben = Clips verschieben, Füllwörter und Pausen
  markiert zum Entfernen; Rückgängig wie überall.
- **Lora als Schnitt-Assistentin**: „Kürze auf 10 Minuten, behalte die Beispiele“, „Entferne den Teil über die
  Organisation“ – Vorschlag als markierte Stellen im Transkript, annehmen/ablehnen.
- Baut auf dem Rohschnitt aus AP-25 und den Aktionen aus AP-38 auf; Sprecher:innen unterscheidbar, wenn die Transkription
  das liefert.
- **Fertig, wenn**: Ein Lehrvideo wird nur über das Transkript geschnitten, und die Zeitleiste folgt.
- Betrifft: Windows `video/RoughCut.kt`, `ui/VideoScreen.kt`, Transkription.

### AP-56 ☐ Bildschirmaufnahme (D) · M (neu)
- **Aufnahme in der App**: ganzer Bildschirm, Fenster oder Bereich + Webcam + Mikrofon (+ Systemton), Countdown,
  Pause, Stopp über Infobereich/Tastenkürzel; Mauszeiger und Klicks hervorheben.
- Ergebnis landet als **Projekt im Videoeditor** mit getrennten Spuren (Bildschirm, Webcam als Bild-in-Bild, Ton) und
  optional sofortiger Transkription.
- Technik: ffmpeg (gdigrab/ddagrab, dshow/WASAPI) unter Windows; Folienaufnahme direkt aus der Referentenansicht (AP-51).
- **Fertig, wenn**: Ein Screencast mit Webcam-Ecke ist nach Stopp sofort als Projekt mit getrennten Spuren schnittbereit.
- Betrifft: neu Windows `video/ScreenRecorder.kt`, `ui/VideoScreen.kt`.

### AP-57 ☐ Video: Export mit GPU und Warteschlange (D) · M (neu)
- **Hardware-Encoder** (NVIDIA NVENC, Intel Quick Sync, AMD AMF) automatisch erkannt, Software als Rückfall.
- **Export-Warteschlange im Hintergrund** (weiterarbeiten während des Exports), mehrere Vorgaben auf einmal (OPAL 720p,
  YouTube 1080p, nur Ton, Untertitel-Datei).
- **Kapitelmarken** aus den Markern (MP4-Kapitel, YouTube-Beschreibung), Ziel direkt **Nextcloud** oder Lora-Ordner.
- Musik und Übergänge in der Vorschau (Rest aus AP-25) sind mit AP-52 erledigt.
- **Fertig, wenn**: Ein 60-Minuten-Video exportiert auf einem PC mit GPU deutlich schneller als heute, im Hintergrund,
  mit Kapiteln.
- Betrifft: Windows `video/VideoRender.kt`, `ui/VideoScreen.kt`, `data/Nextcloud.kt`.

### AP-58 ☐ Frag diesen Ordner · S–M (neu)
- Rechtsklick auf einen Ordner (Lora-Ordner, Ort, Projektordner, Nextcloud-Ordner) → **„Frag diesen Ordner“**: neuer Chat
  im **Quellenmodus** (AP-08) über alle Dateien darin, mit Belegen (Datei + Seite).
- Nutzt den Volltext-Cache aus AP-18; große Ordner werden im Hintergrund gelesen, Fortschritt sichtbar.
- Datenschutz: Vorschlag der Datenschutzstufe nach Ordner (vertraulich markierte Dateien → „Nur TU Dresden“).
- **Fertig, wenn**: Eine Frage an einen Ordner mit 50 PDFs wird mit klickbaren Belegen aus mehreren Dateien beantwortet.
- Betrifft: `ui/DriveScreen.kt`, `data/FileLibrary.kt`, `agent/Evidence.kt`.

### AP-59 ☐ Windows-Integration (W) · M (neu)
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

---

## Später

### AP-60 ☐ PDF-Reste · M (Reste von AP-11, AP-15, AP-26)
- Markieren mit **Einrasten am Text** (Zeilen statt Rechtecke).
- **Schwärzen mit der Maus** auf der Seite (Bereich ziehen, Wort anklicken).
- **Digitale Signatur** (Zertifikat aus dem Windows-Zertifikatsspeicher bzw. Datei, sichtbares Feld, Prüfung vorhandener
  Signaturen).
- **Tags erzeugen** für PDF/UA (Überschriften, Absätze, Listen, Tabellen, Bilder mit Alternativtext).
- **Fertig, wenn**: Ein PDF lässt sich am Text markieren, per Maus schwärzen, digital signieren und mit Tags versehen.
- Betrifft: `docs/PdfStage3.kt`, `docs/PdfAccessibility.kt`, `ui/PdfToolScreen.kt`.

### AP-61 ☐ Diagramme und Whiteboard · L (bisher Ideen, W8-04)
- Flussdiagramme, Mindmaps, Organigramme, Zeitstrahlen auf der gemeinsamen Leinwand (AP-50): Verbinder, die an Formen
  haften, automatische Anordnung.
- **Lora zeichnet**: „Mach ein Flussdiagramm aus diesem Ablauf“, Mermaid-Text ↔ Diagramm.
- Whiteboard: unendliche Fläche, Freihand, Notizzettel; einfügbar in Dokument und Folien.
- **Fertig, wenn**: Aus einer Beschreibung entsteht ein bearbeitbares Flussdiagramm, das in einem Dokument steht.

### AP-62 ☐ Dokumenten-Scanner · M (bisher Ideen)
- **Android**: Kamera mit Kantenerkennung, Zuschneiden, Entzerren, Kontrast (Schwarz-Weiß/Farbe), mehrere Seiten → PDF
  mit OCR in den Lora-Ordner (und per Sync an den PC).
- **Windows**: Scanner über WIA bzw. Handy als Scanner.
- **Fertig, wenn**: Drei fotografierte Seiten ergeben ein durchsuchbares PDF im Lora-Ordner.

### AP-63 ☐ Sync- und Android-Reste · M (Reste von AP-12, AP-14, AP-28)
- Literatur und Wissenssammlung auf Android synchronisieren (mit Neuladen im Betrieb).
- Sync im Hintergrund ohne geöffnete App (Android WorkManager).
- Browser neben dem Chat auf Android als einfache Fassung (WebView).
- Tresor-Projekte im Sync prüfen.
- **Fertig, wenn**: Ein am PC angelegter Literatureintrag erscheint ohne Öffnen der App auf dem Handy.

### AP-64 ☐ Android zieht nach · M–L (neu)
Wie bisher nur, was dort sinnvoll ist:
- Neuer Fließtext-Editor (AP-43–AP-47): ansehen, bearbeiten, Lora schreibt; Tabellen (AP-48) ansehen und einfach
  bearbeiten; Folien (AP-51) ansehen und präsentieren.
- Lora-Aktionen (AP-38) für Dateien und PDF, Gedächtnis (AP-39), Touren (AP-41), Lora-Dateidialog (AP-42), wo der
  Systemdialog nicht reicht.
- Kein Videoeditor, kein Gestalten (unverändert).

### AP-65 ☐ Diktat · S–M (neu, optional)
- Diktieren in Dokumente und Textfelder mit der vorhandenen lokalen Spracherkennung (sherpa-onnx), Satzzeichen per
  Sprache („Punkt“, „neuer Absatz“); keine Sprachbefehle für die App (Chat-Sprachmodus reicht).
- **Fertig, wenn**: Ein Absatz lässt sich im neuen Dokument-Editor auf dem Gerät diktieren.

---

## Ideen

Ohne Auftrag; Details der bisherigen Pakete stehen in [`ARBEITSPLAN-RUNDE-1.md`](ARBEITSPLAN-RUNDE-1.md) bzw. in der
Git-Historie (Fassung vom 30.10.2026).

- **Website** (bisher AP-32): möglichst viele Funktionen im Browser (Chat, TU Vorlagen, Lora, Literatur, Office, PDF);
  nicht im Web: lokale Modelle, Agent in lokalen Ordnern, Videoeditor, eigener Browser. Technik zu prüfen: Compose
  Multiplatform für Web (Wasm), API-Schlüssel nicht im Klartext, CORS der Uni-API.
- **Lora**: eigene Lora-Befehle (gespeicherte Abläufe per Name/Strg+K), Fehlermeldungen in Klartext mit Lösungsknopf
  (W2-09), globales Kürzel für Lora in ganz Windows (mit markiertem Text aus anderen Programmen), technische Namen
  (`ADEU LLM.exe`, Datenordner, Release-Dateien) mit Migration auf „Lora“ umstellen (AP-33).
- **Dateien**: große Vorschau ohne Öffnen (Leertaste) mit Details-Spalte.
- **Video**: Ton im Stil von Fairlight (Ducking, Kompressor/EQ-Vorlagen, Mischpult), Farbkorrektur über die einfache
  Korrektur hinaus.
- **Forschung**: Gutachter-Panel mit Mehr-Agenten-Engine (W3-02, W5-03), Systematic Review nach PRISMA (W5-04),
  Forschungsdaten veröffentlichen über OPARA/Zenodo (W5-06), Profilpflege mit ORCID/FIS/Publikationsliste/Lebenslauf (W5-08).
- **Lehre & Verwaltung**: Klausuren nachträglich analysieren (W6-02), Live-Untertitel im Hörsaal (W6-04, eher Spielerei),
  Podcast aus Dokumenten (W7-02), Assistent für Bewerbungsverfahren (W7-03), Lernspiele als SCORM/H5P.
- **Werkzeuge**: mehr Dateiformate (W8-05), Browser-Automatisierung für Portale ohne Schnittstelle.
- **Anbindungen**: Zotero, Obsidian, Moodle, LimeSurvey und weitere aus dem alten Katalog (W9-03); ADEU als MCP-Server
  (W9-04); Plugins (W9-05); MCP-Ressourcen, -Prompts und Elicitation (Rest AP-23); Exchange über EWS/Graph (Rest AP-27).
- **Bedienung**: Rollenauswahl beim ersten Start (W2-08), Einfügen über die Gboard-Leiste/langes Drücken auf Android
  (Rest von W1-01).
- **Team**: gemeinsamer Ordner für TU Vorlagen der Professur (abgespeckte W4-03, sehr niedrige Priorität).
- **Prüfen auf Dienst-PCs**: Arbeitsspeicher vorher/nachher (AP-04), NVDA-Test (AP-30), Outlook-Rückfrage (AP-13),
  Vorher-Bilder (AP-01) – bei Gelegenheit, ohne eigenes Paket.
- **Ältere Ideen**: Literatur-Radar, Statistik-Werkstatt, Zettelkasten/Wissensgraph, Förder-/Call-Radar,
  Fragebogen-Auswertung, KI als zweite Coder:in, Lehrveranstaltungs-Cockpit, Kurs-Assistent für Studierende, Folien zu
  Lernvideo, Feedback-Werkstatt, Prüfungsorganisation, Barrierefreiheits-Check für Lehrmaterial, Sitzungs-/
  Gremienassistent, zentrale Aufgaben und Fristen, Synopse-Werkzeug, Workflow-Baukasten, Schnellzugriff (Widget),
  Stift und Handschrift, Hausstil-Prüfung.

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

## Offene Fragen

1. **Startseite** (AP-02, AP-37): Wortlaut der Chips und ihrer Starts final abstimmen (umgesetzt ist der Vorschlag aus AP-37).
2. ~~**Outlook über COM** (AP-13)~~: entschieden – COM über JNA (schon in der App), ohne PowerShell und ohne
   Admin-Rechte. Offen nur: Läuft es auf den Dienst-PCs ohne Rückfrage von Outlook (Virenscanner aktuell)?
3. **Exchange der TU** (Rest von AP-27): selbst betrieben (EWS) oder Exchange Online (Graph mit Freigabe der IT)? Wann
   stellt die TU auf das neue Outlook um? Mail über IMAP ist seit AP-27 da; offen sind Kalender und Aufgaben über den
   Server sowie die Zugangsdaten der TU zum Vorbelegen.
4. **OPAL** (AP-17, AP-20): Lassen sich HTML-Dateien/ZIP-Pakete als Lernelement einbinden? In welchem Format kommen
   Abgaben aus OPAL?
5. **Word-Prüfsammlung** (AP-45): Welche echten Dokumente (TUD-Briefvorlage, Vorlage Abschlussarbeit, Antrag, Protokoll)
   dürfen als Testdateien ins Repo, oder nur anonymisiert?
6. **Gesichter verpixeln** (AP-54): Welches lokale Erkennungsmodell (Lizenz, Größe im Videoeditor-Paket)?
7. **Explorer-Kontextmenü** (AP-59): Ist ein Eintrag je Benutzer (ohne Admin) auf den Dienst-PCs erlaubt?

---

## Anhang: Plattformen

Windows bekommt alles; Android nur, was dort sinnvoll ist.

| Werkzeug | Android | Windows |
| --- | --- | --- |
| Chat-Design, Übersicht, Modi, Rückfragen (AP-01, 02, 05, 06) | ☑ | ☑ |
| Lora-Navigator (AP-07) | ☐ | ☐ |
| Pseudonymisierung (AP-03) | ☑ | ☑ |
| Tresor (AP-10) | ☐ | ☐ |
| PDF-Werkzeugkasten (AP-11, 15, 26) | Betrachter, Seiten, Markieren, Unterschreiben, KI, OCR per Bildmodell, Vergleichen | voll |
| Sync (AP-12, AP-28) | ☑ (über Nextcloud) | ☑ |
| Mail und Kalender (AP-13, AP-27) | ☑ Mail über den Server (ohne Kalender) | ☑ (Outlook oder Server) |
| Dateiverwaltung (AP-18) | ☑ (Dokumente, Wissenssammlung) | ☑ (dazu Ablageorte und Projektordner) |
| Browser neben dem Chat (AP-14) | später, einfach (WebView) | ☑ |
| Office (AP-16) | ☑ ansehen, bearbeiten, Lora; Dateien in der App mit Kopie in einen gewählten Ordner | ☑ voll, Lora-Ordner |
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
