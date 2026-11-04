# Arbeitsplan Lora (bis 1.0.43 „ADEU LLM“) – Runde 2

Stand: 04.11.2026 (neu geordnet in **Leuchttürmen**) · Lebendes Dokument: Status hier pflegen, erledigte Pakete abhaken
und in der README beschreiben. Runde 1 (AP-01 bis AP-37) ist erledigt und steht ausführlich in
[`ARBEITSPLAN-RUNDE-1.md`](ARBEITSPLAN-RUNDE-1.md). Die Fassung vom 03.11.2026 (Office- und Video-Nachbau als
Funktionspakete) steht in der Git-Historie dieser Datei.

**So ist der Plan aufgebaut**

- **Leuchttürme** – wenige große Ziele mit einem Satz „Fertig, wenn“. Jeder Leuchtturm besteht aus Arbeitspaketen
  (`AP-xx`), die Pakete aus Stufen.
- **Jetzt** – der Leuchtturm, an dem gerade gearbeitet wird. Ein Paket nach dem anderen.
- **Danach** – die nächsten Leuchttürme in fester Reihenfolge; rücken nach „Jetzt“ auf, sobald dort Platz ist.
- **Später** – gewollt, kleinere Pakete ohne eigenen Leuchtturm; erst, wenn „Jetzt“ und „Danach“ erledigt sind.
- **Ideen** – passen zu den Schwerpunkten (Lora, Mail, Fristen und Radar, Video, Office), aber ohne Auftrag; je eine
  Zeile.
- **Irgendwann mal** – passt nicht zu den Schwerpunkten; ruht ohne Auftrag (Details in der Git-Historie bzw. in
  `ARBEITSPLAN-RUNDE-1.md`).

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
| Priorität | **Täglicher Nutzen** entscheidet. Seit 04.11.2026 ist der Plan in **Leuchttürmen** geordnet: Lora, Mail datenschutzsicher, Nichts verpassen (Fristen und Radar), Video, Office. Lehre hat keinen eigenen Leuchtturm; sie profitiert über Video, Folien, Mail und Fristen. |
| Umfang der Werkzeuge | **Alltagsumfang**: die Funktionen, die den Großteil der Arbeit abdecken. Die Vorbilder (pdf24 + Xodo, Word/Excel/PowerPoint, Canva, DaVinci Resolve) geben die Richtung vor, nicht den vollen Umfang. Ausbau nur bei konkretem Bedarf. **Ausnahme Video**: Der Videoeditor kommt so nah wie möglich an DaVinci Resolve für den Alltag – ohne Fusion, Color Grading und Fairlight, eigenständig im Lora-Stil in **einer** Ansicht, mit Lora als Schnitt-Assistentin. **Office abgespeckt (seit 04.11.2026)**: kein voller Nachbau von Google Docs/Sheets/Slides mehr, sondern **Word-/PowerPoint-/Excel-Treue** plus Lora schreibt, rechnet und baut Folien; der Text-Kern (AP-43) bleibt für das Dokument. |
| Übersichtlichkeit | Die App darf durch neue Funktionen nicht unübersichtlich werden: große Werkzeuge sind **einschaltbar** (anfangs aus, Lora schlägt sie passend vor), eingeschaltete Fortgeschrittene stehen in der Seitenleiste unter **„Mehr“**. Jedes neue Werkzeug fügt sich in AP-02 ein. Keine Einfach-/Expertenansicht. „Mehr“ bleibt eine flache Liste; **jeder Bereich lässt sich ausblenden** (außer *Erstellen* und *Dateien*), erreichbar bleibt er über Strg+K und Lora (seit AP-24). |
| Inhalte | Zwei feste Orte für Inhalte: **Dateien** (was es gibt) und **Erstellen** (was neu entsteht: TU Vorlagen und Gestalten mit „Meine Designs“). Jederzeit kann man stattdessen Lora sagen, was entstehen soll (seit AP-24). |
| Plattformen | **Windows-PC ist die Hauptplattform.** Android zieht nur Funktionen nach, die dort sinnvoll sind – **kein** Videoeditor, **kein** Kreativbereich. macOS und Linux sind da (AP-31); die Website steht unter *Irgendwann mal*. |
| Lora | **Kern der App** und Namensgeberin (seit 1.0.44 heißt die App „Lora“, Slogan: *Der freundliche Helfer für alle Wünsche in Forschung, Lehre und Verwaltung an der TU Dresden*; technische Namen wie `ADEU LLM.exe` und die Datenordner bleiben). Navigator und Helfer: findet Einstellungen, öffnet Modi und Vorlagen, übergibt an Chat-Modelle – **Standard für Übergaben ist DeepSeek V4.1 Flash**, andere nur in Sonderfällen, die Lora selbst entscheidet und begründet. Lora antwortet selbst mit GLM-5.3 Flash. Lora reagiert, unterbricht nicht (darum **kein Tagesbriefing**); Fristen und Radar (AP-67–69) zeigen Neues als **Liste beim Öffnen**, ohne Pop-up und ohne Ton. Seit Runde 2 **handelt Lora selbst in den Werkzeugen** (AP-38) – sichtbar im Panel rechts, mit Vorschau und Rückgängig, ohne eigenen Cursor im Werkzeug – und merkt sich Vorlieben, Kontext und Schreibstil nur nach Rückfrage (AP-39). Wie selbstständig sie bei langen Aufträgen arbeitet, ist **je Auftrag wählbar** (AP-40). Animationen dürfen auch ohne neues Werkzeug kommen, wenn sie Lora lebendiger machen (AP-36); sie bleiben seriös und respektieren *Ruhigere Bewegungen*. |
| Modus-Konzept | Einheitlich: Chip über dem Eingabefeld, eine Zeile „was passiert hier“, Datenhinweis, ✕ zum Verlassen. |
| Datenschutz | Pseudonymisierung wird treffsicherer (weniger Fehlalarme, weniger Übersehenes). Für sensible Forschung gibt es den **Tresor** statt einer allgemeinen App-Sperre. **Mails** gehen immer pseudonymisiert und nur an Server der TU Dresden (AP-66); weil die Dienst-PCs kein starkes lokales Modell schaffen, stützt sich der Schutz auf Regeln, das Adressbuch und Kopfzeilen statt auf ein großes Modell. |
| KI entscheidet nicht | Die KI schlägt vor, ein Mensch entscheidet (Bewertungen, Mails). Mails werden **nie gelöscht und nie ohne Aufforderung gesendet**. KI-Verordnung: Prüfungsbewertung ist Hochrisiko → nur Assistenz, Protokoll, Hinweis in der App. Datenschutzhinweise und Löschfristen für Mails und Abgaben laufend mitpflegen (bisher Q-04). |
| E-Mail/Kalender | Die Kolleg:innen nutzen das **klassische Outlook** → zuerst lokal über COM (ohne IT-Freigabe). Der Server-Weg (Exchange/IMAP) kommt erst, wenn jemand das neue Outlook nutzt. |
| Synchronisierung | **Einfach wie bei Obsidian**: Datenordner in einen Cloud-Ordner (Nextcloud, Google Drive, OneDrive), der auf beiden PCs eingerichtet ist. Mehrere PCs zuerst, Android ist nice to have. |
| Gemeinsame Daten | Keine Professur-Datenbank. Höchstens irgendwann ein gemeinsamer Ordner für TU Vorlagen (*Irgendwann mal*). |
| Anbindungen | Wichtige Dienste **fest eingebaut** (zuerst Nextcloud) statt zuerst allgemeines MCP. |
| Eigene Werkzeuge statt Fremd-Apps | Selbst bauen, gehört vollständig uns. Die Craft-Apps (getartcraft.com) werden **nicht** per MCP angebunden; je Werkzeug wird geprüft, ob Code übernommen oder selbst geschrieben wird (*Anhang: Craft-Apps*). |
| Eine App | Kein Launcher mit mehreren Apps. Werkzeuge werden ein- und ausgeblendet (AP-02); große Teile, die nicht alle brauchen, sind **nachladbare Erweiterungen** über den Updater (seit AP-25: Videoeditor-Paket; die Browser-Engine wird mit aufgeführt). |
| Sprachen | Englisch ist vollständig (AP-29), keine weiteren Sprachen. |
| Gestrichen | Qualitätstests für Modi (bisher Q-01), Usability-Tests (Q-03), Professur-Datenbank mit Rollen/Richtlinien/Audit-Log (W4-03), Betreuungs-Cockpit, weitere Sprachen, Tagesbriefing (Runde 2), Sprachbefehle für die App (Chat-Sprachmodus und Diktat reichen), **digitale Signatur für PDFs** (04.11.2026: die TU hat dafür ein eigenes Werkzeug). (Lora-Animationen als eigenes Paket, bisher W2-07, kamen auf Wunsch mit AP-36 doch.) |

---

## Übersicht

Runde 2 (ab 02.11.2026), am 04.11.2026 in Leuchttürme umgeordnet. Die erste Runde (AP-01 bis AP-37) ist erledigt; ihre
ausführlichen Beschreibungen stehen in [`ARBEITSPLAN-RUNDE-1.md`](ARBEITSPLAN-RUNDE-1.md), die Kurzfassung unter
*Erledigt*.

| Abschnitt | Leuchtturm | Pakete |
| --- | --- | --- |
| **Jetzt** | **1 · Lora handelt und lernt** | AP-38 Lora handelt in Werkzeugen · AP-39 Was Lora über dich weiß · AP-40 Abläufe über Werkzeuge · AP-41 Leuchtrahmen überall und Touren · (AP-42 Lora-Dateidialog ☑) |
| **Danach** (1.) | **2 · Mail datenschutzsicher** | AP-66 Mail-Schutz |
| **Danach** (2.) | **3 · Nichts verpassen** | AP-67 Fristen und Aufgaben · AP-68 Förder- und CfP-Radar · AP-69 Zitations-Radar |
| **Danach** (3.) | **4 · Video wie Resolve, mit Lora** (D) | AP-52 Mehrere Spuren und echte Vorschau · AP-53 Schnittwerkzeuge · AP-55 Textbasierter Schnitt · AP-54 Verpixeln und Datenschutz · AP-70 Lora im Schnitt |
| **Danach** (4.) | **5 · Office fertig, abgespeckt** | AP-43 Text-Kern: Seiten · AP-44 Fließtext-Editor: Reste · AP-45 Word-Treue · AP-46 Gliederung und Formatvorlagen · AP-49 Lora rechnet in Tabellen · AP-51 Folien: Treue und Lora baut · (AP-47 ☑) |
| **Später** | – | AP-56 Bildschirmaufnahme · AP-57 Export mit GPU · AP-58 Frag diesen Ordner · AP-59 Windows-Integration · AP-60 PDF-Reste · AP-62 Dokumenten-Scanner |
| **Irgendwann mal** | – | AP-48 Tabellen wie Excel · AP-50 Gemeinsame Leinwand · AP-61 Whiteboard · AP-63 Sync-Reste · AP-64 Android zieht nach · AP-65 Diktat · ältere Ideen |

**Reihenfolge**: erst Leuchtturm 1 fertig, dann 2, dann 3. Die Leuchttürme 4 (Video) und 5 (Office) sind unabhängig
voneinander und dürfen sich abwechseln, wenn ein Strang wartet; begonnen wird mit Video (AP-52).

**Abhängigkeiten**: AP-38 vor AP-40 und vor allen Lora-Teilen der späteren Pakete (jedes Werkzeug liefert seine
Lora-Aktionen über dieselbe Schnittstelle) · AP-39 vor AP-40, AP-66 (Schreibstil) und AP-68/69 (Themen) · AP-41 vor AP-70
(Werkzeuge zeigen) · AP-66 vor AP-67 (Aufgaben aus Mails gehen pseudonymisiert) · AP-67 vor AP-68 und AP-69 (Fristen
landen in derselben Liste) · AP-52 vor AP-53 vor AP-54, AP-55, AP-70 · AP-52 vor AP-56 und AP-57 · AP-43 vor AP-45 (PDF über
die eigene Layout-Engine) und AP-46 · AP-42 vor AP-59.

---

## Jetzt

## Leuchtturm 1 · Lora handelt und lernt

**Ziel**: Lora bedient die Werkzeuge selbst, kennt dich und deine Vorlieben und erledigt auch mehrschrittige Aufträge –
sichtbar, umkehrbar und so selbstständig, wie du es je Auftrag erlaubst.
**Fertig, wenn**: Ein Auftrag wie „Aus dieser Aufnahme: Transkript, Untertitel, Handout als Word in meinem üblichen
Layout“ läuft über mehrere Werkzeuge durch, nutzt gemerkte Vorlieben ohne Nachfrage und lässt sich Schritt für Schritt
rückgängig machen.

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
5. **Stufe c – Dokument, Tabelle, Folien**: formatieren, umstrukturieren, Tabellen einfügen, Folien anlegen – auf den
   heutigen Editoren über ihre Datenmodelle; AP-49 und AP-51 ergänzen die Tabellen- und Folien-Aktionen.
6. **Stufe d – Video**: schneiden, Titel/Bauchbinde setzen, Untertitel erzeugen, Rohschnitt, Export – auf dem heutigen
   Projektmodell; AP-52 ff. und AP-70 übernehmen die Aktionen.
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
  überarbeiten, Tabelle einfügen, Überschrift setzen im Fließtext-Editor; Tabellen und Folien fehlen noch (mit AP-49/AP-51).
  **Rest**: Stufe d (Video) auf dem heutigen Projektmodell; Tabellen/Folien-Aktionen; Datenschutzstufe/Tresor für
  Aktionen prüfen.

### AP-39 ☐ Was Lora über dich weiß · M (neu, erweitert 04.11.2026)
Das Gedächtnis (`agent/Memory.kt`, heute: Funktion, Lehre, Schreibstil, Kontakte, laufende Vorhaben für die Chats) wird
zu **„Was Lora über dich weiß“** und gilt auch für Lora und ihre Aktionen. Vier Arten von Einträgen:
1. **Vorlieben je Werkzeug**: „Handouts zweispaltig“, „Videos für OPAL in 720p“, „Literaturverzeichnis in APA 7“,
   „Hochgeladenes nach Semester ordnen“ – Lora und die Aktionen aus AP-38 nutzen sie als Standardwerte.
2. **Kontext über dich**: Kurse, Projekte, Themen, Mitarbeitende, laufende Anträge – damit „mein Seminar“, „der
   DFG-Antrag“ oder „Frau X aus dem Projekt“ ohne Erklärung verstanden werden.
3. **Schreibstil**: wie du formulierst (Anrede, Ton, Länge, Grußformel), getrennt für Mails, Gutachten und Texte; gelernt
   aus Beispielen, die du freigibst (z. B. gesendete Mails, pseudonymisiert), gespeichert als kurze Beschreibung plus
   wenige Musterstellen. Grundlage für Antworten im eigenen Stil (AP-66).
4. **Themen für das Radar**: Forschungsthemen, Schlagwörter, eigene Publikationen (ORCID), beobachtete Autor:innen –
   Grundlage für AP-68 und AP-69.
- **Lora fragt vor dem Merken** („Soll ich mir merken, dass …?“ mit Antwortknöpfen aus AP-06); nichts wird still gemerkt,
  auch nicht im Modus *Automatisch* der Chats.
- Sichtbar und bearbeitbar unter *Einstellungen › Lora › Was Lora über dich weiß*: gruppiert nach den vier Arten und je
  Werkzeug, jeder Eintrag änderbar und löschbar, *Alles vergessen*. Tresor-Inhalte landen nie im Gedächtnis.
- Wird mit dem Sync wie bisher übertragen.
- **Fertig, wenn**: Eine bestätigte Vorliebe wirkt beim nächsten passenden Auftrag ohne Nachfrage; Lora versteht „mein
  Seminar“ aus dem gemerkten Kontext; alle vier Arten lassen sich in den Einstellungen sehen und löschen.
- Betrifft: `agent/Memory.kt`, `agent/MemoryLearner.kt`, `agent/LoraAgent.kt`, `ui/LoraHelper.kt`, Einstellungen › Lora.

### AP-40 ☐ Abläufe über Werkzeuge · L (neu)
- Mehrschrittige Aufträge: „Aus dieser Aufnahme: transkribieren, Untertitel, Video auf 10 Minuten kürzen, Handout als
  Word“ oder „Alle Abgaben zusammenfügen, schwärzen, als ein PDF in die Nextcloud“.
- Lora **plant zuerst** und zeigt den Plan als Karte (Schritte, Werkzeuge, geschätzte Dauer, Datenschutzhinweis);
  *Los*, *Ändern*, *Abbrechen*. Danach führt sie die Schritte mit den Aktionen aus AP-38 aus.
- **Selbstständigkeit je Auftrag wählbar** (Schalter auf der Plan-Karte):
  - *Schritt für Schritt* (Standard): jeder größere Schritt sichtbar, Bestätigung vor Riskantem.
  - *Allein arbeiten*: Lora arbeitet den ganzen Plan ohne Zwischenfragen ab (auch eine Stunde und länger) und legt am
    Ende Ergebnis und Protokoll vor; Riskantes (Überschreiben, Verschieben, Schwärzen, Versenden in die Cloud) wird
    gesammelt und erst am Ende mit Vorschau bestätigt, oder Lora fragt nur dann, wenn sie ohne Antwort nicht weiterkommt.
- Fortschritt im Panel, Pause/Fortsetzen; lange Schritte (Transkription, Export, Stapel) laufen im Hintergrund weiter und
  melden sich (Benachrichtigung wie beim Stapel).
- Zwischenergebnisse landen in *Erstellt*, verknüpft mit dem Ablauf; ein fehlgeschlagener Schritt lässt sich wiederholen.
- Gespeicherte Abläufe, Zeitsteuerung und Auslöser bleiben in den *Ideen* (am 04.11.2026 abgefragt: vorerst nicht).
- **Fertig, wenn**: Der Beispielauftrag mit der Aufnahme läuft vom Plan bis zum Handout durch, auch über einen Neustart
  der App hinweg, und einmal im Modus *Allein arbeiten* ohne Zwischenfrage.
- Betrifft: neu `agent/LoraPlan.kt`; `agent/LoraAgent.kt`, `agent/BatchRunner.kt` (Hintergrund), `ui/LoraHelper.kt`.

### AP-41 ☐ Leuchtrahmen überall und Touren · M (Rest von AP-07, neu)
Beides ist gleich wichtig: Lora kann jede Option zeigen, und neue Kolleg:innen finden sich ohne Erklärung zurecht.
- **Leuchtrahmen für alle Optionen**: Jede Einstellung und jeder wichtige Knopf in den Werkzeugen lässt sich von Lora
  hervorheben (heute nur die wichtigsten). Ein Test prüft, dass jedes Ziel der Landkarte einen Rahmen hat. Der
  Videoeditor ist eingeschlossen (Grundlage für „Lora erklärt Werkzeuge“ in AP-70).
- **Touren**: Beim ersten Öffnen eines Werkzeugs (PDF, Dateien, Erstellen, Video, Mail, Fristen & Radar, später die neuen
  Editoren) bietet Lora eine kurze Tour an (3–6 Schritte, Leuchtrahmen + eine Zeile Text, *Weiter*/*Überspringen*).
  **Nach Updates** gibt es eine Mini-Tour zu neuen Funktionen (verknüpft mit „Was ist neu“). Abschaltbar unter
  *Einstellungen › Lora*, jederzeit über Strg+K bzw. „Zeig mir, wie das geht“ wiederholbar.
- Touren werden als Daten beschrieben (Ziel, Text), nicht je Bildschirm programmiert; englische Texte über `en.tsv`.
- Respektiert *Ruhigere Bewegungen*; Lora fliegt (AP-36) nur, wenn Bewegungen erlaubt sind.
- **Fertig, wenn**: Jede Option der Landkarte leuchtet beim Navigieren auf; das erste Öffnen des PDF-Werkzeugkastens bietet
  eine Tour an, die sich überspringen und später wieder aufrufen lässt; nach einem Update zeigt eine Mini-Tour das Neue.
- Betrifft: `ui/Spotlight.kt`, `data/AppMap.kt`, neu `ui/LoraTour.kt`, Einstellungen › Lora, „Was ist neu“.

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

## Leuchtturm 2 · Mail datenschutzsicher

**Ziel**: Lora hilft bei Mails so viel wie heute (Was ist wichtig, Sortieren, Antworten im eigenen Stil, Fristen) – aber
**so datenschutzsicher wie möglich**, auch auf Dienst-PCs ohne Leistung für starke lokale KI.
**Fertig, wenn**: Eine Studierenden-Mail mit Name, Matrikelnummer, Note und Anhang wird mit Lora beantwortet; das
Protokoll zeigt, dass beim Server nur Platzhalter ankamen und nur ein TU-Server beteiligt war; der Entwurf in Outlook
enthält die echten Angaben.

### AP-66 ☐ Mail-Schutz · M–L (neu, 04.11.2026)
Heute gehen Mails pseudonymisiert an das Modell, **wenn** die Pseudonymisierung an ist. Neu ist eine eigene Schutzschicht
für alles, was aus dem Postfach kommt (Outlook über COM und IMAP, beide Plattformen):
1. **Immer pseudonymisiert und „Nur TU Dresden“** – wie beim Stapel (AP-20), auch wenn die Pseudonymisierung für Chats aus
   ist; externe Anbieter sind für Mailinhalte gesperrt. Antworten, Entwürfe und Aufgaben bekommen die echten Angaben erst
   auf dem Gerät zurück.
2. **Treffsicher ohne großes Modell** (die Dienst-PCs schaffen kein starkes lokales Modell):
   - Namen und Adressen aus den **Kopfzeilen** (Von, An, Cc) und dem **Outlook-Adressbuch** bzw. den Kontakten gehen als
     exakte Liste in die Pseudonymisierung – damit werden genau die Personen ersetzt, die im Verlauf vorkommen, auch in
     Grußformeln, Spitznamen-Varianten und zitierten Mails.
   - **Signaturen** (Telefon, Raum, Adresse) und **zitierte Verläufe** werden erkannt; Lora schickt nur, was für die
     Aufgabe nötig ist (Datensparsamkeit: z. B. für eine Antwort die letzte Mail plus eine Kurzfassung des Verlaufs).
   - Regeln wie bisher (Matrikelnummern, IBAN, Telefon, Geburtsdaten, Anschriften, Noten in Studierenden-Mails); das
     optionale lokale Erkennungsmodell bleibt **freiwillig** für starke PCs.
3. **Anhänge** gehen nie automatisch mit; auf Wunsch einzeln, pseudonymisiert wie Dokumente. Mails mit Outlook-
   Vertraulichkeit *Vertraulich*/*Privat* oder aus Tresor-markierten Ordnern liest Lora nur nach Rückfrage.
4. **Sichtbar**: Vor dem ersten Senden einer Mail-Anfrage zeigt eine Vorschau, was der Server sähe; das Mail-Protokoll
   nennt je Anfrage Server, Platzhalter und weggelassene Teile. Ein Tipp korrigiert („nicht ersetzen“/„auch ersetzen“).
5. **Nichts bleibt liegen**: Mailinhalte landen nicht im Gedächtnis (außer bestätigtem Schreibstil, AP-39) und nicht im
   Sync; Zwischenstände werden nach Abschluss gelöscht. Datenschutzhinweis für das Team und Löschfristen in der App und
   der README (Grundsatz *KI entscheidet nicht*).
6. **Antworten im eigenen Stil** nutzen den Schreibstil aus AP-39 (statt jedes Mal gesendete Mails mitzuschicken);
   Wiedervorlage („Erinnere mich, wenn bis Freitag keine Antwort kommt“) als Eintrag in AP-67.
- Testsammlung `MailPseudonymCorpusTest` mit typischen Professur-Mails (Studierende, Prüfungsamt, Kolleg:innen,
  Verlage) misst Übersehenes und Fehlalarme.
- **Fertig, wenn**: wie beim Leuchtturm; dazu erkennt die Testsammlung mindestens so viel wie die Chat-Pseudonymisierung
  mit lokalem Modell, ohne dass ein lokales Modell läuft.
- Betrifft: `data/Pseudonymizer.kt`, `data/PiiDetector.kt`, `data/Privacy.kt`, `data/Outlook.kt`, `data/MailServer.kt`,
  `files/MailText.kt`, `agent/OutlookTools.kt`, `ui/MailScreen.kt`, `ui/PrivacySettings.kt`.

## Leuchtturm 3 · Nichts verpassen

**Ziel**: Fristen, Ausschreibungen, Calls for Papers und Zitationen landen von selbst an **einem** Ort – als Liste beim
Öffnen, ohne Pop-up und ohne Ton.
**Fertig, wenn**: Beim Öffnen der App zeigt *Fristen & Radar* eine Frist aus einer Mail, eine passende DFG-Ausschreibung,
einen CfP aus einer Mailingliste und eine neue Zitation einer eigenen Publikation – jeweils mit „warum“, Frist und einem
Knopf für den nächsten Schritt.

### AP-67 ☐ Fristen und Aufgaben · M (neu, aus „zentrale Aufgaben und Fristen“)
- **Ein Ort**: neues Werkzeug *Fristen & Radar* (einschaltbar, Lora schlägt es vor, sobald Fristen erkannt werden), oben
  die Fristen und Aufgaben, darunter das Radar (AP-68, AP-69). Kachel auf der Startseite und eine Zahl am Eintrag in der
  Seitenleiste – kein Pop-up, kein Ton.
- **Gesammelt aus Mails**: Bitten und Fristen werden erkannt (über den Mail-Schutz AP-66) und als **Vorschlag** angeboten
  („Gutachten für Frau [Name] bis 15.11. – übernehmen?“); nichts landet ungefragt in der Liste.
- **Gesammelt aus Dokumenten**: Ausschreibungen, Anträge (Antrags-Werkstatt AP-21), Protokolle, Abgabefristen aus Stapeln
  (AP-20) und Dateien im Lora-Ordner.
- Eintrag mit Titel, Frist, Herkunft (Klick öffnet Mail/Datei), Projekt, erledigt/verschieben; *Wiedervorlage* für Mails.
  Lora versteht „Was ist diese Woche fällig?“ und „Verschiebe das auf nächste Woche“ (Aktionen nach AP-38).
- Die Liste liegt in Lora und synchronisiert wie die anderen Daten; Termin/Aufgabe in Outlook bleibt wie heute als Knopf
  (Abgleich mit Outlook-Aufgaben steht in den *Ideen*).
- **Fertig, wenn**: Eine Frist aus einer Mail und eine aus einem Antrag stehen nach Bestätigung in der Liste, öffnen ihre
  Herkunft und erscheinen auf dem zweiten PC.
- Betrifft: neu `data/Deadlines.kt`, `ui/DeadlineScreen.kt`; `agent/OutlookTools.kt`, `agent/ProposalTools.kt`,
  `data/AppMap.kt`, Startseite.

### AP-68 ☐ Förder- und CfP-Radar · M–L (neu, aus „Förder-/Call-Radar“)
- **Themen** kommen aus AP-39 (Forschungsthemen, Schlagwörter, Fachgebiet) und lassen sich im Radar direkt bearbeiten.
- **Abruf** beim Öffnen der App im Hintergrund, höchstens einmal am Tag (kein eigener Zeitplan, keine Meldung von außen).
  Neues seit dem letzten Besuch steht oben; jeder Treffer mit **„Passt, weil …“** (Lora, über die TU-API – es geht nur um
  öffentliche Ausschreibungen), Frist, Förderer, Link.
- **Quellen** – Empfehlung: so viele wie möglich, beginnend mit den gut lesbaren:
  1. *Mit Feed oder Schnittstelle*: DFG-Ausschreibungen, BMBF-Bekanntmachungen, Förderdatenbank des Bundes, EU Funding &
     Tenders Portal, H-Soz-Kult (CfPs und Tagungen).
  2. *Webseiten beobachten*: TUD-interne Ausschreibungen (Forschungsförderung, Graduiertenakademie), Sächsische
     Aufbaubank, große Stiftungen (z. B. VolkswagenStiftung, Fritz Thyssen, Gerda Henkel), Fachgesellschaften;
     dazu **„Diese Seite beobachten“** im Browser neben dem Chat (AP-14) für jede weitere Quelle – Lora erkennt neue
     Einträge.
  3. *Postfach*: Ausschreibungs- und CfP-Mails aus Mailinglisten werden lokal erkannt (über AP-66) und erscheinen im
     Radar statt im Posteingang unterzugehen.
  Schnittstellen und Nutzungsbedingungen je Quelle beim Bau prüfen (*Offene Fragen*).
- **Nächster Schritt per Knopf**: *Frist übernehmen* (AP-67), *Antrag starten* (Antrags-Werkstatt AP-21 mit passender
  Förderer-Vorlage), *Merken*, *Nicht interessant* (Lora lernt daraus, nach Rückfrage).
- **Fertig, wenn**: Mit drei eingetragenen Themen zeigt das Radar passende Ausschreibungen aus mindestens drei Quellen und
  einen CfP aus dem Postfach; *Antrag starten* öffnet die Antrags-Werkstatt mit Förderer und Frist.
- Betrifft: neu `data/Radar.kt`, `ui/RadarScreen.kt` (Teil von *Fristen & Radar*); `agent/WebTools.kt`,
  `agent/ProposalTools.kt`, Windows-Browser (AP-14).

### AP-69 ☐ Zitations-Radar · M (neu)
- **Individuell einstellbar**, was beobachtet wird – je Eintrag an/aus:
  - **eigene Publikationen** (ORCID-ID oder Liste aus der Literatur): wer zitiert dich bzw. die Professur;
  - **markierte Literatur**: neue Arbeiten, die wichtige Paper zitieren;
  - **Autor:innen**: neue Veröffentlichungen bestimmter Personen.
- Quellen: OpenAlex (frei, „zitiert von“), Crossref und ORCID – dieselben Dienste wie der Quellen-Check (`check_references`).
- Treffer im Radar mit Kurzfassung (aus dem Abstract), *In Literatur übernehmen*, *Exzerpt* (AP-09), *Nicht interessant*.
- **Fertig, wenn**: Für eine eigene Publikation und ein markiertes Paper erscheinen neue zitierende Arbeiten im Radar und
  lassen sich per Klick in die Literatur übernehmen.
- Betrifft: `data/Radar.kt`, `agent/ReferenceCheck.kt`, Literatur, `ui/RadarScreen.kt`.

## Leuchtturm 4 · Video wie Resolve, mit Lora (D)

**Ziel**: Ein Schnittprogramm nah an DaVinci Resolve für den Alltag – mehrere Spuren, echte Vorschau, richtige
Schnittwerkzeuge, Schnitt über das Transkript – mit Lora als Assistentin und Datenschutz-Effekten, die mit einem Knopf
gehen.
**Fertig, wenn**: Ein Vortrag mit Folien-Screencast, Webcam und zwei Tonquellen wird geschnitten, Lora schlägt den
Rohschnitt und die Kapitel vor, alle Gesichter im Publikum sind mit einem Knopf verpixelt, und der Export sieht aus wie die
Vorschau.

### AP-52 ☐ Video: mehrere Spuren und echte Vorschau (D) · XL (neu, Grundlage)
So nah wie möglich an **DaVinci Resolve** für den Alltag, eigenständig im Lora-Stil, **eine Ansicht** statt Seiten.
- **Projektmodell mit beliebig vielen Video- und Tonspuren** (heute: eine Videospur), Ebenen von unten nach oben,
  Deckkraft, Position/Größe/Zuschnitt je Clip; altes Projektformat wird übernommen.
- **Echte Vorschau**: flüssige Wiedergabe mit Ton aller Spuren, Musik, Übergängen und Titeln **genau wie im Export**
  (eigene Wiedergabe-Engine: ffmpeg decodiert, die App mischt Bild und Ton, Proxys bei Bedarf); Wellenformen auf den
  Tonspuren; J/K/L, Einzelbild vor/zurück, Schleife In–Out.
- **Bild-in-Bild** als Standardfall (Webcam-Ecke über dem Screencast), mit Vorlagen für die Ecke.
- Export bleibt ein ffmpeg-Filtergraph, jetzt für mehrere Spuren.
- Die Lora-Aktionen aus AP-38 Stufe d wandern auf das neue Modell.
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

### AP-55 ☐ Video: textbasierter Schnitt (D) · M (neu)
- Das **Transkript** liegt als Text neben der Zeitleiste; Wörter sind mit der Zeit verknüpft (Klick springt).
- **Text löschen = Video schneiden** (wie Descript), Absätze verschieben = Clips verschieben, Füllwörter und Pausen
  markiert zum Entfernen; Rückgängig wie überall.
- Lora-Vorschläge aus AP-70 („Kürze auf 10 Minuten“) erscheinen hier als markierte Stellen im Transkript, annehmen/ablehnen.
- Baut auf dem Rohschnitt aus AP-25 und den Aktionen aus AP-38 auf; Sprecher:innen unterscheidbar, wenn die Transkription
  das liefert.
- **Fertig, wenn**: Ein Lehrvideo wird nur über das Transkript geschnitten, und die Zeitleiste folgt.
- Betrifft: Windows `video/RoughCut.kt`, `ui/VideoScreen.kt`, Transkription.

### AP-54 ☐ Video: Verpixeln und Datenschutz (D) · M–L (neu, am 04.11.2026 auf Datenschutz zugeschnitten)
Muss **leicht** gehen – für Forschungsmaterial, Lehraufnahmen mit Publikum und Beobachtungsvideos.
- **Ein Knopf: Alle Gesichter verpixeln**: Gesichter werden lokal gefunden und verfolgt; danach eine Liste der gefundenen
  Personen (Vorschaubild) zum Abhaken („diese Person nicht verpixeln“). Übersehenes lässt sich per Klick ins Bild
  nachtragen und folgt dann ebenfalls.
- **Lora-Auftrag**: „Verpixle alle Gesichter außer meinem“, „… und Namensschilder und Bildschirme“, „nur die
  Studierenden in der ersten Reihe“ – Ergebnis als Vorschau, annehmen/ändern.
- **Auch Ton**: Stimme verfremden (je Sprecher:in), **Namen im Ton überpiepen** – die Stellen findet die Pseudonymisierung
  im Transkript (dieselben Regeln und Listen wie im Chat), Untertitel werden mit Platzhaltern geschrieben.
- Weichzeichnen oder Verpixeln wählbar; Stärke einstellbar; alles steht im Agent-Protokoll (was, wo, wie).
- Weitere Effekte (Zoom/Schwenk mit Keyframes, Hochformat, Tempo, Bildkorrektur) stehen in den *Ideen*.
- **Fertig, wenn**: In einem Beobachtungsvideo werden mit einem Knopf alle Gesichter außer einer abgehakten Person
  verpixelt und ein genannter Name im Ton überpiept.
- Betrifft: Windows `video/*`, `ui/VideoScreen.kt`, `data/Pseudonymizer.kt`; lokales Erkennungsmodell im
  Videoeditor-Paket (AP-25).

### AP-70 ☐ Lora im Schnitt (D) · M (neu, 04.11.2026)
- **Rohschnitt auf Auftrag**: „Schneide Pausen und Versprecher raus“, „Kürze auf 10 Minuten, behalte die Beispiele“,
  „Entferne den Teil über die Organisation“ – als **Vorschlag in der Zeitleiste** (farbig markierte Bereiche) und im
  Transkript (AP-55), einzeln oder alle annehmbar, Rückgängig wie überall.
- **Lora erklärt Werkzeuge**: „Wie mache ich einen Ripple-Trim?“ – Lora zeigt den Weg mit Leuchtrahmen (AP-41) oder führt
  ihn auf Wunsch aus („Mach du“); Tour für den Videoeditor.
- **Auto-Kapitel, Titel und Bauchbinden**: aus dem Transkript vorgeschlagen (Kapitel als Marker, Bauchbinden für
  Sprecher:innen im TUD-Design, Titelkarte); beim Export als **MP4-Kapitel** und als Kapitel-Liste für die
  YouTube/OPAL-Beschreibung.
- Nutzt die Aktionen aus AP-38 (Stufe d) und das Gedächtnis (z. B. „Bauchbinden immer mit Professur“).
- **Fertig, wenn**: Lora kürzt einen 30-Minuten-Vortrag per Auftrag auf einen annehmbaren Vorschlag, setzt Kapitel und
  Bauchbinden, und der Export enthält die Kapitel.
- Betrifft: Windows `video/RoughCut.kt`, `video/VideoRender.kt`, `ui/VideoScreen.kt`, `agent/ToolActions.kt`.

## Leuchtturm 5 · Office fertig, abgespeckt

**Ziel**: Word-, PowerPoint- und Excel-Dateien kommen verlustfrei hin und zurück; das Dokument hat echte Seiten; Lora
schreibt, rechnet und baut Folien. Kein voller Nachbau von Google Docs/Sheets/Slides.
**Fertig, wenn**: Die TUD-Vorlagen (Brief, Abschlussarbeit, Präsentation) öffnen originalgetreu und kommen nach dem
Bearbeiten ohne Verlust in Word/PowerPoint an; aus einem Paper baut Lora ein Deck im TUD-Design.

### AP-43 ◐ Text-Kern: echte Seiten · L (neu, am 04.11.2026 abgespeckt)
Grundlage für das Dokument (nicht mehr für Tabellenzellen und Leinwand).
- **Layout-Engine**: Zeilenumbruch, Silbentrennung (Deutsch/Englisch), **echter Seitenumbruch** mit Witwen/Waisen,
  Tabellen über Seiten, Kopf-/Fußzeilen je Abschnitt (erste Seite, gerade/ungerade); dieselbe Engine zeichnet Bildschirm,
  PDF-Export und Vorschaubilder.
- **Zwischenablage mit Formatierung** aus Word und Browser (HTML/RTF).
- Flüssig bei 100 Seiten mit Tabellen und Bildern.
- **Fertig, wenn**: Ein 100-seitiges Dokument mit Tabellen und Bildern lässt sich flüssig bearbeiten, Seitenumbrüche
  entsprechen dem PDF-Export.
- Betrifft: `docs/text/*`; `docs/DocMarkup.kt`, `docs/LiveDoc.kt`.
- **Umsetzung (03.11.2026, ◐, für AP-44 vorgezogen)**: `docs/text/RichText.kt` – Fließtext mit Zeichenformat je Zeichen
  (fett, kursiv, unterstrichen, durchgestrichen, Code, hoch/tief, Farbe, Hervorhebung, Größe, Schrift, Link) und
  Absatzformat (Formatvorlage, Ausrichtung, Listenebene, Checkliste, weiche Zeilen); Bearbeiten (Diff, Formate wandern mit,
  Absätze teilen/verbinden), Markdown hin und zurück (Erweiterungen als HTML-Marken, die alle Exporte lesen), Vorschläge
  (Wortdiff) und Loras Schreibmarken. Markdown bleibt vorerst Speicherformat (Chat, Exporte, Word laufen unverändert).
  **Rest**: wie oben (Layout-Engine, Zwischenablage, Leistung).

### AP-44 ◐ Fließtext-Editor: Reste · M (neu, ersetzt den Block-Editor; am 04.11.2026 abgespeckt)
- **Rest**: Zeilenabstand wählbar, **Suchen und Ersetzen**, **Kommentare am Rand** im Text, **Änderungen nachverfolgen im
  Fließtext** (dann kann der Block-Editor ganz weg), Markieren über Tabellen hinweg.
- Lineal mit Tabstopps und Ränder per Ziehen stehen in den *Ideen*.
- **Fertig, wenn**: Der alte Block-Editor ist ersetzt; ein Hausarbeits-Dokument (Deckblatt, Inhaltsverzeichnis,
  Fußnoten, Tabellen, Literaturverzeichnis) lässt sich ohne Umweg über Word erstellen.
- Betrifft: `ui/FlowEditor.kt`, `ui/DocsEditor.kt`, `ui/DocsText.kt` (ersetzt), `ui/DocsAssistant.kt`, `docs/LiveDoc.kt`.
- **Umsetzung (03.11.2026, ◐)**: `ui/FlowEditor.kt` ist der Editor für Text-Dokumente: durchgehender Text mit Cursor und
  Markieren über Absätze, Symbolleiste und Menüs wie Docs (Formatvorlage, Schrift, Größe, B/I/U/S, Farbe, Hervorhebung,
  Link, Kommentar, Bild, Ausrichtung, Listen, Einzug, Formatierung entfernen, ✨), Tastenkürzel, Markdown-Kürzel,
  Checkboxen zum Anklicken, eigenes Rückgängig, Seitenansicht (A4-Breite, Ränder aus *Seite einrichten*, Linien an den
  Seitenenden) oder *Ohne Seiten*, Zoom. Einfügen: Tabelle (Raster), Bild, Link, Kommentar, Zitat, Seitenumbruch,
  Inhaltsverzeichnis, Fußnote, Formel, Linie, Datum, Sonderzeichen; Tabellen werden an Ort und Stelle bearbeitet. Bestehende
  Dokumente öffnen ohne Umstellung (Markdown bleibt). Der Block-Editor bleibt nur für die Prüfung von KI-Änderungen und
  *Änderungen nachverfolgen*.

### AP-45 ◐ Word-Treue · L (neu, ersetzt „Schreiben ins Original“ für Dokumente)
- **.docx direkt in das neue Modell lesen und zurückschreiben**: Formatvorlagen (auch eigene), Abschnitte,
  Kopf-/Fußzeilen (erste Seite, gerade/ungerade), Fußnoten, Felder, Nummerierungen, Tabellen, Bilder mit Umfluss,
  Textfelder (lesen), Kommentare, Überarbeitungen.
- **Unbekanntes bleibt erhalten**: Was das Modell nicht kennt, wird als Rohdaten mitgeführt und unverändert
  zurückgeschrieben (kein Umweg über `.lora/originale` mehr nötig).
- **Prüfsammlung** mit echten Dokumenten (TUD-Briefvorlage, Abschlussarbeit-Vorlage, Antrag, Protokoll): öffnen →
  speichern → in Word vergleichen; Bildvergleich der Seiten als Test.
- Export PDF über die eigene Layout-Engine (AP-43, gleich wie am Bildschirm), ODT bleibt.
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
- Betrifft: `ui/FlowEditor.kt`, `ui/DocsEditor.kt`, neu `docs/text/Styles.kt`, `docs/DocTheme.kt`, `docs/TudBrand.kt`.

### AP-47 ☑ Lora schreibt im Dokument · M (neu)
- **„Hilf mir schreiben“** (wie Gemini in Docs): Feld an der Cursorstelle bzw. über leeren Absätzen – „Schreib eine
  Einleitung zu …“, „Fasse Abschnitt 3 zusammen“. Lora schreibt **sichtbar an der Cursorstelle** (Text erscheint
  schrittweise, farbig markiert), danach *Übernehmen · Neu · Verwerfen*.
- **Abschnitte überarbeiten als Vorschlag**: Lora ändert nicht direkt, sondern über die Änderungsverfolgung (rot/grün),
  jede Änderung einzeln annehmbar; Kommentare von Lora am Rand mit Begründung.
- **Fertig, wenn**: Lora schreibt eine Einleitung an der Cursorstelle und überarbeitet einen Abschnitt als einzeln
  annehmbare Vorschläge, ohne Formatierung zu zerstören.
- **Umsetzung (03.11.2026, ☑)**: Im Fließtext-Editor: *Hilf mir schreiben* (Strg+J, ✨, *Extras*, leeres Dokument, Lora
  „schreib eine Einleitung …“) schreibt schrittweise und hinterlegt an der Cursorstelle, Markdown-Überschriften werden
  Formatvorlagen; *Übernehmen · Neu · Verwerfen*. *Markierung mit Lora überarbeiten* bzw. Lora „Überarbeite Abschnitt …“:
  Wortdiff als Vorschläge (rot/grün), Leiste mit *Annehmen/Ablehnen/Alle*, Begründung als Kommentar von Lora. Das
  Lora-Panel rechts schreibt weiter über den Chat; der Editor lädt das Ergebnis mit Formatierung. Rest: das kleine
  KI-Popup direkt über einer Markierung gibt es nur im Block-Editor (im Fließtext über Strg+J / *Extras*).

### AP-49 ☐ Lora rechnet in Tabellen · M (neu, am 04.11.2026 abgespeckt; ersetzt AP-48 als Tabellen-Paket)
Die Tabellen bleiben, wie sie sind; dazu:
- **Lora rechnet**: „Berechne die Notenverteilung“, „Mittelwert je Gruppe“, Pivot-Zusammenfassung, Daten bereinigen
  (Leerzeichen, Datumsformate, Dubletten), Formeln erklären – Ergebnisse als neue Spalten/Blätter mit Rückgängig,
  Erklärung in Worten im Panel. Rechnen passiert auf dem Gerät in der Tabellen-Engine bzw. der Rechen-Sandbox; das
  Modell sieht nur Spaltennamen und Stichproben, außer man erlaubt mehr.
- **Diagramme** aus markierten Bereichen: Säulen, Balken, Linie, Kreis, Punkt, Histogramm, Boxplot; Titel, Achsen,
  Farben im TUD-Design; als Bild in Dokument und Folien einfügen oder exportieren.
- **xlsx bleibt erhalten**: Beim Speichern einer fremden .xlsx bleiben Formeln, Formate, Blätter und verbundene Zellen,
  die Lora nicht anfasst, unverändert (wie bei der Word-Treue: Unverändertes als Original übernehmen).
- Tabellen-Aktionen für Lora (Rest AP-38 Stufe c).
- **Fertig, wenn**: Aus einer Notentabelle (.xlsx) entsteht per Auftrag eine Verteilung mit Diagramm im Dokument; die
  Datei kommt ohne Verlust zurück in Excel.
- Betrifft: `docs/Charts.kt`, `docs/SheetEngine.kt`, `ui/SheetEditor.kt`, `agent/ToolActions.kt`.

### AP-51 ☐ Folien: Treue und Lora baut · L (neu, am 04.11.2026 abgespeckt)
- **PowerPoint-Treue**: fremde .pptx (auch die TUD-Präsentationsvorlage) mit Masterfolien, Layouts, Platzhaltern,
  Designfarben, Gruppen, Tabellen und Diagrammen originalgetreu anzeigen und bearbeiten; Unbekanntes bleibt erhalten.
  Dazu der Gestalten-Rest *PowerPoint-Vorlagen importieren*. Ohne Zusammenlegung mit der Gestalten-Leinwand (AP-50 ruht).
- **Lora baut Folien**: aus Dokument, Paper (PDF) oder Gliederung ein ganzes Deck im TUD-Design (Titel, Abschnitte,
  Inhalt, Bilder/Icons, Sprechertexte); einzelne Folie „schöner machen“ (Vorschläge zum Antippen); Folien kürzen,
  Sprechertexte schreiben. Folien-Aktionen für Lora (Rest AP-38 Stufe c).
- Animationen, Übergänge und ein ausgebautes Präsentieren stehen in den *Ideen*.
- **Fertig, wenn**: Die TUD-Präsentationsvorlage öffnet originalgetreu und kommt verlustfrei zurück in PowerPoint; ein
  Deck aus einem Paper entsteht per Auftrag.
- Betrifft: `ui/SlidesEditor.kt`, `docs/SlideDeck.kt`, `docs/PptxWriter.kt`, `docs/SlideHandout.kt`, neu
  `docs/PptxReader.kt`.

---

## Später

Gewollt, ohne eigenen Leuchtturm.

### AP-56 ☐ Bildschirmaufnahme (D) · M (neu)
- **Aufnahme in der App**: ganzer Bildschirm, Fenster oder Bereich + Webcam + Mikrofon (+ Systemton), Countdown,
  Pause, Stopp über Infobereich/Tastenkürzel; Mauszeiger und Klicks hervorheben.
- Ergebnis landet als **Projekt im Videoeditor** mit getrennten Spuren (Bildschirm, Webcam als Bild-in-Bild, Ton) und
  optional sofortiger Transkription.
- Technik: ffmpeg (gdigrab/ddagrab, dshow/WASAPI) unter Windows; Folienaufnahme aus der Referentenansicht.
- **Fertig, wenn**: Ein Screencast mit Webcam-Ecke ist nach Stopp sofort als Projekt mit getrennten Spuren schnittbereit.
- Betrifft: neu Windows `video/ScreenRecorder.kt`, `ui/VideoScreen.kt`.

### AP-57 ☐ Video: Export mit GPU und Warteschlange (D) · M (neu)
- **Hardware-Encoder** (NVIDIA NVENC, Intel Quick Sync, AMD AMF) automatisch erkannt, Software als Rückfall.
- **Export-Warteschlange im Hintergrund** (weiterarbeiten während des Exports), mehrere Vorgaben auf einmal (OPAL 720p,
  YouTube 1080p, nur Ton, Untertitel-Datei); Ziel direkt **Nextcloud** oder Lora-Ordner. (MP4-Kapitel kommen mit AP-70.)
- **Fertig, wenn**: Ein 60-Minuten-Video exportiert auf einem PC mit GPU deutlich schneller als heute, im Hintergrund.
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

### AP-60 ☐ PDF-Reste · M (Reste von AP-11, AP-15, AP-26; ohne digitale Signatur)
- Markieren mit **Einrasten am Text** (Zeilen statt Rechtecke).
- **Schwärzen mit der Maus** auf der Seite (Bereich ziehen, Wort anklicken).
- **Tags erzeugen** für PDF/UA (Überschriften, Absätze, Listen, Tabellen, Bilder mit Alternativtext).
- Die digitale Signatur ist gestrichen (die TU hat ein eigenes Werkzeug).
- **Fertig, wenn**: Ein PDF lässt sich am Text markieren, per Maus schwärzen und mit Tags versehen.
- Betrifft: `docs/PdfStage3.kt`, `docs/PdfAccessibility.kt`, `ui/PdfToolScreen.kt`.

### AP-62 ☐ Dokumenten-Scanner · M (bisher Ideen)
- **Android**: Kamera mit Kantenerkennung, Zuschneiden, Entzerren, Kontrast (Schwarz-Weiß/Farbe), mehrere Seiten → PDF
  mit OCR in den Lora-Ordner (und per Sync an den PC).
- **Windows**: Scanner über WIA bzw. Handy als Scanner.
- **Fertig, wenn**: Drei fotografierte Seiten ergeben ein durchsuchbares PDF im Lora-Ordner.

---

## Ideen

Passen zu den Schwerpunkten, ohne Auftrag.

- **Lora**: eigene Lora-Befehle (gespeicherte Abläufe per Name/Strg+K), zeitgesteuerte Abläufe und Auslöser („neue
  Datei in Ordner X“) – am 04.11.2026 abgefragt, vorerst nicht; Fehlermeldungen in Klartext mit Lösungsknopf (W2-09);
  globales Kürzel für Lora in ganz Windows (mit markiertem Text aus anderen Programmen); technische Namen (`ADEU LLM.exe`,
  Datenordner, Release-Dateien) mit Migration auf „Lora“ umstellen (AP-33).
- **Mail, Fristen, Radar**: Exchange über EWS/Graph mit Kalender (Rest AP-27); Fristen mit Outlook-Aufgaben abgleichen;
  wöchentlicher Radar-Bericht; Literatur-Radar (neue Paper zu den eigenen Themen, nicht nur Zitationen).
- **Video**: Zoom und Schwenk mit Keyframes, Hochformat-Reframe, Geschwindigkeit/Standbild/Rückwärts, einfache
  Bildkorrektur (bisher AP-54); Bereich ziehen, der dem Objekt folgt; passende Clips aus dem Medienpool vorschlagen
  (B-Roll); Ton im Stil von Fairlight (Ducking, Kompressor/EQ-Vorlagen, Mischpult); Farbkorrektur über die einfache
  Korrektur hinaus.
- **Office**: Lineal mit Einzügen und Tabstopps, Ränder per Ziehen (bisher AP-44); Diagramme verknüpft einfügen (ändern
  sich mit den Daten, als echte Office-Diagramme); Folien-Animationen und -Übergänge, Referentenansicht mit Timer,
  Laserpointer, zweiter Bildschirm (bisher AP-51).
- **Prüfen auf Dienst-PCs**: Arbeitsspeicher vorher/nachher (AP-04), NVDA-Test (AP-30), Outlook-Rückfrage (AP-13),
  Vorher-Bilder (AP-01) – bei Gelegenheit, ohne eigenes Paket.

---

## Irgendwann mal

Passt nicht zu den Schwerpunkten; ruht ohne Auftrag. Details der Pakete in der Git-Historie (Fassung vom 03.11.2026).

- **Office-Nachbau**: AP-48 *Tabellen wie Excel* (viele Funktionen, mehrere Blätter, 100 000 Zeilen, xlsx-Treue für
  alles) · AP-50 *Gemeinsame Leinwand für Folien und Gestalten* (dazu Text auf der Leinwand tippen, Farbverläufe,
  Bildfilter) · AP-61 *Diagramme und Whiteboard* (Flussdiagramme, Mindmaps, Mermaid, unendliche Fläche).
- **Android und Diktat**: AP-63 *Sync- und Android-Reste* (Literatur/Wissen synchronisieren, Hintergrund-Sync, WebView-
  Browser, Tresor-Projekte im Sync prüfen) · AP-64 *Android zieht nach* (neuer Editor, Lora-Aktionen, Touren auf
  Android) · AP-65 *Diktat* in Dokumente.
- **Website** (bisher AP-32): möglichst viele Funktionen im Browser; Technik: Compose Multiplatform für Web (Wasm),
  API-Schlüssel nicht im Klartext, CORS der Uni-API.
- **Klammern** (am 04.11.2026 angeboten, nicht gewählt): Projekte als Klammer für Chats, Dateien, Literatur, Mails und
  Fristen; „Lora kennt alle meine Dateien“ (semantische Suche über alles); Eingangskorb beim Öffnen.
- **Forschung**: Paper-Werkstatt (Zielzeitschrift, Gutachten beantworten), qualitative Analyse (Codieren, KI als zweite
  Coder:in), historische Quellen (Handschrift-/Fraktur-OCR, Edition), Gutachter-Panel mit Mehr-Agenten-Engine (W3-02,
  W5-03), Systematic Review nach PRISMA (W5-04), Forschungsdaten über OPARA/Zenodo (W5-06), Profilpflege mit
  ORCID/FIS/Publikationsliste/Lebenslauf (W5-08), Statistik-Werkstatt, Zettelkasten/Wissensgraph, Fragebogen-Auswertung,
  Synopse-Werkzeug.
- **Lehre** (kein eigener Leuchtturm): Semesterwerkstatt, Feedback-Werkstatt für Hausarbeiten, Lehrvideo-Fabrik,
  Prüfungsorganisation, Klausuren nachträglich analysieren (W6-02), Live-Untertitel im Hörsaal (W6-04), Lernspiele als
  SCORM/H5P, Lehrveranstaltungs-Cockpit, Kurs-Assistent für Studierende, Folien zu Lernvideo, Barrierefreiheits-Check für
  Lehrmaterial.
- **Verwaltung**: Sitzungs-/Gremienassistent, Portale ohne Schnittstelle per Browser-Automatisierung, Podcast aus
  Dokumenten (W7-02), Assistent für Bewerbungsverfahren (W7-03), Hausstil-Prüfung.
- **Anbindungen und Werkzeuge**: Zotero, Obsidian, Moodle, LimeSurvey und weitere aus dem alten Katalog (W9-03); ADEU als
  MCP-Server (W9-04); Plugins (W9-05); MCP-Ressourcen, -Prompts und Elicitation (Rest AP-23); mehr Dateiformate (W8-05);
  Workflow-Baukasten.
- **Bedienung**: Rollenauswahl beim ersten Start (W2-08), Einfügen über die Gboard-Leiste/langes Drücken auf Android
  (Rest von W1-01), große Vorschau in *Dateien* ohne Öffnen (Leertaste) mit Details-Spalte, Schnellzugriff (Widget), Stift
  und Handschrift.
- **Team**: gemeinsamer Ordner für TU Vorlagen der Professur (abgespeckte W4-03, sehr niedrige Priorität).

---

## Erledigt

Runde 1 in Kürze; Details in [`ARBEITSPLAN-RUNDE-1.md`](ARBEITSPLAN-RUNDE-1.md). Aus Runde 2 erledigt: AP-42
Lora-Dateidialog, AP-47 Lora schreibt im Dokument (oben beschrieben).

| Paket | Ergebnis | Rest |
| --- | --- | --- |
| ☑ Screenshots aus der Zwischenablage (W1-01) | *Aus der Zwischenablage* im „+“-Menü, Strg+V | Gboard-Leiste → Irgendwann mal |
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
| ☑ AP-15 PDF Stufe 2 | Texterkennung von Windows bzw. Bildmodell, Umwandeln (Office über COM), Unterschreiben, Frag das PDF, Tabellen | Handschrift-OCR auf dem PC (digitale Signatur gestrichen) |
| ☑ AP-18 Dateiverwaltung | *Dateien*: Orte und Ordner, Schlagwörter, Volltextsuche mit Cache, Vergleich, `search_files` | SQLite-Index nur bei Bedarf; Dateien verschieben (mit AP-16 erledigt) |
| ☑ AP-24 Kreativbereich → „Erstellen“ | Erstellen mit Lora-Feld, Gestalten (9 Vorlagen nach dem CD-Paket 2025 mit Shape, TUD-Icons und allen Logos, Editor mit Ebenen, Einrasten, Drehen, Rückgängig), Meine Designs, Export PNG/PDF (Druck mit Beschnitt)/PowerPoint, KI in jedem Chat | Text auf der Leinwand tippen (Irgendwann mal), PowerPoint-Import (AP-51) |
| ☑ AP-25 Videoeditor und Erweiterungen | Erweiterungen über den Updater (Videoeditor-Paket, Browser-Engine entfernbar), Zeitleiste, TUD-Titel, Ton, Untertitel aus der Transkription, Rohschnitt, Mit Lora kürzen, MP4 für OPAL/YouTube | Musik und Übergänge in der Vorschau, zweite Videospur (AP-52), Farbkorrektur (Ideen) |
| ☑ Seitenleiste ausblendbar (Nachtrag AP-02) | jeder Bereich außer Erstellen und Dateien per Rechtsklick oder in den Einstellungen ausblendbar, über Strg+K und Lora erreichbar | – |
| ☑ AP-16 Office-Ausbau | Tab *Dateien* wie Google Drive, Lora-Ordner (`.lora`), echte docx/pptx/xlsx, ins Original schreiben, Änderungen nachverfolgen, Word-Kommentare, Fußzeile/Ränder/Inhaltsverzeichnis/Formeln, Rechtschreibung auf dem Gerät, freie Folien mit Masterfolie, Lora-Panel und -Popup, Chatfilter | echter Seitenumbruch in der Seitenansicht (AP-43); Windows-Papierkorb (AP-59); Scannen (AP-62) |
| ☑ AP-20 Stapel und Massenkontrolle | Warteschlange im Hintergrund (Ordner, ZIP, Mehrfachauswahl, Nextcloud) mit Pause/Fortsetzen über Neustarts, Vorrang für Chats, nachts; Massenkontrolle mit Raster, Kalibrierung, Belegprüfung, Ausreißern, Entscheidung mit Protokoll, Excel/Word-Exporten | Exzerpt-Stapel an Literatureinträge; OPAL-Format klären |
| ☑ AP-21 Antrags-Werkstatt | Modus *Antrag*, Förderer-Vorlagen (DFG, BMBF, EU, SAB, TUD), Checkliste, Fristen als Kalenderdatei, Gliederung mit Zeichen-/Seitenwächter, Gantt, Budget mit Pauschale, DMP, Ethik, Probelauf, Abgabe-Check, Word-Export | Förderer-Vorlagen befüllen, Personalmittelsätze; Anschluss an das Radar (AP-68) |
| ☑ AP-22 Nextcloud | WebDAV auf beiden Plattformen, Anmeldung im Browser (Login Flow v2), Datei-Browser, Anhängen, Holen, Speichern, Agent-Werkzeuge | Android-Sync (AP-28), Freigabelinks |
| ☑ AP-26 PDF Stufe 3 | Schwärzen (Seiten als Bild), Text/Bilder ändern, Verkleinern, PDF/A-2b, Passwort, Metadaten, PDF/UA-Prüfung mit Behebung, Vergleichen | Inhaltsstrom bearbeiten, Tags erzeugen (AP-60) |
| ☑ AP-27 Mail über den Server | IMAP mit derselben Schnittstelle wie Outlook, Entwürfe statt Senden, Verschieben nur sicher, Android | Exchange mit Kalender (EWS/Graph, Ideen) |
| ☑ AP-28 Sync für Android | Handy spiegelt den Lora-Ordner des PCs über Nextcloud/WebDAV mit den Regeln von AP-12 | Literatur/Wissen, Hintergrund-Sync (Irgendwann mal) |
| ☑ AP-23 MCP-Client | Streamable HTTP mit Token/OAuth (PKCE), stdio unter Windows, *Einstellungen › Verbindungen*, Erlauben/Fragen/Sperren je Werkzeug, Datenschutzstufe | Ressourcen, Prompts, Elicitation |
| ☑ AP-29 Englisch | ganze Oberfläche übersetzt (`en.tsv`), Pfade Teil für Teil, Lora versteht Englisch, Test für jeden Text | Inhalte der Dokumentvorlagen |
| ☑ AP-30 Barrierefreiheit | Beschriftungen, Überschriften, Schalter/Auswahl für Screenreader, sichtbarer Fokus, große Schrift, Access Bridge (NVDA/JAWS) | Test mit NVDA |
| ☑ AP-31 macOS und Linux | DMG/tar.gz (Apple Silicon), tar.gz/.deb (Linux), Updater, Keychain, Autostart, ⌘, ffmpeg vom System | Notarisierung, Intel-Macs |
| ☑ Lora-Knöpfe (Nachtrag AP-37) | Gesicht in „Frag Lora“ und der schlafenden Lora mittig statt am Rand, nichts abgeschnitten | – |

---

## Offene Fragen

1. **Startseite** (AP-02, AP-37): Wortlaut der Chips und ihrer Starts final abstimmen (umgesetzt ist der Vorschlag aus AP-37);
   dazu die Kachel *Fristen & Radar* (AP-67).
2. ~~**Outlook über COM** (AP-13)~~: entschieden – COM über JNA (schon in der App), ohne PowerShell und ohne
   Admin-Rechte. Offen nur: Läuft es auf den Dienst-PCs ohne Rückfrage von Outlook (Virenscanner aktuell)?
3. **Exchange der TU** (Rest von AP-27): selbst betrieben (EWS) oder Exchange Online (Graph mit Freigabe der IT)? Wann
   stellt die TU auf das neue Outlook um? Mail über IMAP ist seit AP-27 da; offen sind Kalender und Aufgaben über den
   Server sowie die Zugangsdaten der TU zum Vorbelegen.
4. **OPAL** (AP-17, AP-20): Lassen sich HTML-Dateien/ZIP-Pakete als Lernelement einbinden? In welchem Format kommen
   Abgaben aus OPAL?
5. **Word-Prüfsammlung** (AP-45): Welche echten Dokumente (TUD-Briefvorlage, Vorlage Abschlussarbeit, Antrag, Protokoll)
   dürfen als Testdateien ins Repo, oder nur anonymisiert?
6. **Gesichter verpixeln** (AP-54): Welches lokale Erkennungsmodell (Lizenz, Größe im Videoeditor-Paket), und läuft es auf
   den Dienst-PCs schnell genug (sonst langsamer im Hintergrund)?
7. **Explorer-Kontextmenü** (AP-59): Ist ein Eintrag je Benutzer (ohne Admin) auf den Dienst-PCs erlaubt?
8. **Mail-Schutz** (AP-66): Welche Modelle der Uni-API laufen nachweislich auf Servern der TU Dresden? Braucht die
   Verarbeitung dienstlicher Mails eine Abstimmung mit dem Datenschutzbeauftragten der TU (Hinweis für das Team)?
9. **Radar-Quellen** (AP-68, AP-69): Welche Quellen bieten Feeds/Schnittstellen, und was erlauben ihre Nutzungsbedingungen
   (automatischer Abruf, Häufigkeit)? Welche Fachgesellschaften und Mailinglisten gehören für die Professur dazu?

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
| Mail-Schutz (AP-66) | geplant (für IMAP) | geplant |
| Fristen & Radar (AP-67–69) | geplant (ansehen, abhaken) | geplant |
| Dateiverwaltung (AP-18) | ☑ (Dokumente, Wissenssammlung) | ☑ (dazu Ablageorte und Projektordner) |
| Browser neben dem Chat (AP-14) | später, einfach (WebView) | ☑ |
| Office (AP-16) | ☑ ansehen, bearbeiten, Lora; Dateien in der App mit Kopie in einen gewählten Ordner | ☑ voll, Lora-Ordner |
| Lernspiele (AP-17) | ansehen, spielen | voll |
| Erstellen (AP-24) | TU Vorlagen; Gestalten nicht geplant | ☑ (mit Gestalten) |
| Videoeditor (AP-25, AP-52–55, AP-70) | – (nicht geplant) | ☑ / geplant |
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
