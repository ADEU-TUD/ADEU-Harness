# Arbeitsplan ADEU LLM

Stand: 30.10.2026 · Lebendes Dokument: Status hier pflegen, erledigte Pakete abhaken und in der README beschreiben.

**So ist der Plan aufgebaut**

- **Jetzt** – die nächsten Wochen, in fester Reihenfolge. Ein Paket nach dem anderen; ist eines fertig, rückt das nächste
  nach.
- **Danach** – rückt nach „Jetzt“ auf, sobald dort Platz ist.
- **Später** – gewollt, aber erst, wenn „Jetzt“ und „Danach“ erledigt sind. *Ganz am Ende* kommen Website, macOS und Linux.
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
| Umfang der Werkzeuge | **Alltagsumfang**: die Funktionen, die den Großteil der Arbeit abdecken. Die Vorbilder (pdf24 + Xodo, Word/Excel/PowerPoint, Canva, DaVinci Resolve) geben die Richtung vor, nicht den vollen Umfang. Ausbau nur bei konkretem Bedarf. |
| Übersichtlichkeit | Die App darf durch neue Funktionen nicht unübersichtlich werden: große Werkzeuge sind **einschaltbar** (anfangs aus, Lora schlägt sie passend vor), eingeschaltete Fortgeschrittene stehen in der Seitenleiste unter **„Mehr“**. Jedes neue Werkzeug fügt sich in AP-02 ein. Keine Einfach-/Expertenansicht. |
| Plattformen | **Windows-PC ist die Hauptplattform.** Android zieht nur Funktionen nach, die dort sinnvoll sind – **kein** Videoeditor, **kein** Kreativbereich. Website, macOS und Linux erst ganz am Ende. |
| Lora | Navigator und Helfer: findet Einstellungen, öffnet Modi und Vorlagen, übergibt an Chat-Modelle (GLM-5.3 Flash). Lora reagiert, unterbricht nicht. Neue **Animationen kommen mit dem jeweiligen Werkzeug**, nicht als eigenes Paket. |
| Modus-Konzept | Einheitlich: Chip über dem Eingabefeld, eine Zeile „was passiert hier“, Datenhinweis, ✕ zum Verlassen. |
| Datenschutz | Pseudonymisierung wird treffsicherer (weniger Fehlalarme, weniger Übersehenes). Für sensible Forschung gibt es den **Tresor** statt einer allgemeinen App-Sperre. |
| KI entscheidet nicht | Die KI schlägt vor, ein Mensch entscheidet (Bewertungen, Mails). Mails werden **nie gelöscht und nie ohne Aufforderung gesendet**. KI-Verordnung: Prüfungsbewertung ist Hochrisiko → nur Assistenz, Protokoll, Hinweis in der App. Datenschutzhinweise und Löschfristen für Mails und Abgaben laufend mitpflegen (bisher Q-04). |
| E-Mail/Kalender | Die Kolleg:innen nutzen das **klassische Outlook** → zuerst lokal über COM (ohne IT-Freigabe). Der Server-Weg (Exchange/IMAP) kommt erst, wenn jemand das neue Outlook nutzt. |
| Synchronisierung | **Einfach wie bei Obsidian**: Datenordner in einen Cloud-Ordner (Nextcloud, Google Drive, OneDrive), der auf beiden PCs eingerichtet ist. Mehrere PCs zuerst, Android ist nice to have. |
| Gemeinsame Daten | Keine Professur-Datenbank. Höchstens irgendwann ein gemeinsamer Ordner für TU Vorlagen (Ideen). |
| Anbindungen | Wichtige Dienste **fest eingebaut** (zuerst Nextcloud) statt zuerst allgemeines MCP. |
| Eigene Werkzeuge statt Fremd-Apps | Selbst bauen, gehört vollständig uns. Die Craft-Apps (getartcraft.com) werden **nicht** per MCP angebunden; je Werkzeug wird geprüft, ob Code übernommen oder selbst geschrieben wird (*Anhang: Craft-Apps*). |
| Eine App | Kein Launcher mit mehreren Apps. Werkzeuge werden ein- und ausgeblendet (AP-02); **nachladbare Erweiterungen** erst mit dem Videoeditor (AP-25). |
| Sprachen | Englisch vervollständigen (eigenes Paket, später), keine weiteren Sprachen. |
| Gestrichen | Qualitätstests für Modi (bisher Q-01), Usability-Tests (Q-03), Professur-Datenbank mit Rollen/Richtlinien/Audit-Log (W4-03), Betreuungs-Cockpit, Lora-Animationen als eigenes Paket (W2-07), weitere Sprachen. |

---

## Übersicht

| Abschnitt | Pakete |
| --- | --- |
| **Jetzt** | AP-01 Chat-Design und Logo · AP-02 Übersicht und einschaltbare Werkzeuge · AP-03 Pseudonymisierung · AP-04 Weniger Arbeitsspeicher · AP-05 Modus-Rahmen · AP-06 Rückfragen mit Antwortknöpfen · AP-07 Lora-Navigator · AP-08 Belegpflicht und Quellenmodus · AP-09 Exzerpt-Agent · AP-10 Tresor · AP-11 PDF Stufe 1 |
| **Danach** | AP-12 Einfacher Sync · AP-13 Mail und Kalender über Outlook · AP-14 Browser neben dem Chat · AP-15 PDF Stufe 2 · AP-16 Office-Ausbau · AP-17 Lernspiele · AP-18 Dateiverwaltung · AP-19 KI-Nutzungsnachweis |
| **Später** | AP-20 Stapel und Massenkontrolle · AP-21 Antrags-Werkstatt · AP-22 Nextcloud · AP-23 MCP-Client · AP-24 Kreativbereich · AP-25 Videoeditor · AP-26 PDF Stufe 3 · AP-27 Mail über den Server · AP-28 Sync für Android · AP-29 Englisch · AP-30 Barrierefreiheit · *ganz am Ende:* AP-31 macOS und Linux · AP-32 Website |

Abhängigkeiten: AP-05 vor AP-06 (Planungsmodus), AP-07 und AP-08 · AP-06 vor AP-07 · AP-08 vor AP-09 und AP-14 (Fragen
zur Seite mit Belegen) · AP-03 vor AP-10 · AP-11 vor AP-15 vor AP-26 · AP-12 vor AP-28 · AP-20 vor großen Exzerpt-Stapeln.

---

## Jetzt

### AP-01 ☐ Chat-Design und Logo · M
- **Eigener ADEU-Stil statt grauer Kästen mit runden Ecken**: Antworten als ruhiger Text, Kästen (Code, Tabellen,
  Hinweise, Dokumentkarten) mit feinem Rand und TUD-Farbakzent statt grauer Fläche, klare Abstände und Typografie.
  Erkennbar eigenständig, Lora eingebunden.
- **Links, Quellen, Anhänge** schöner gestalten: Links als Marken mit Domain, Quellenangaben als nummerierte Marken (werden
  in AP-08 wiederverwendet), Dateikarten und Dokument-Vorschauen einheitlich.
- **Lora-Arbeitskarte** (das Kästchen, in dem Lora während der Antwort arbeitet) neu gestalten.
- **Logo**: Lora in lesender Pose ersetzt das bisherige Logo; Startanimation und App-Symbole (Android, Windows `.ico`)
  passend dazu.
- Werkzeugschritte des Agenten bleiben, wie sie sind.
- Gilt für alle Stile (ADEU, TU Dresden, Office, Hoher Kontrast …); Hoher Kontrast bleibt WCAG AAA.
- **Fertig, wenn**: Chat ohne graue Kästen, Links/Quellen/Anhänge und Lora-Karte im neuen Stil, neues Logo mit
  Startanimation auf beiden Plattformen; Screenshots vorher/nachher im Pull Request.
- Betrifft: `ui/ChatMessages.kt`, `ui/Markdown.kt`, `ui/ChatExtras.kt`, `ui/Design.kt`, `ui/Theme.kt`, `ui/Logo.kt`,
  `ui/LoraFigure.kt`, `ui/LoraUi.kt`, `desktop/icons/`.

### AP-02 ☐ Übersicht: Seitenleiste, Startseite, einschaltbare Werkzeuge · M (bisher W2-01, W2-02, Teil von W8-00)
- **Seitenleiste**: oben die Grundfunktionen *Neuer Chat*, *TU Vorlagen* (umbenannt aus *Vorlagen*), *Literatur*,
  *Transkription*. Darunter ausklappbar **„Mehr“**: *Dokumente*, *Bilder erzeugen*, *Geplante Aufgaben* und alle
  eingeschalteten Werkzeuge. Auf-/Zugeklappt wird gespeichert; Bereiche lassen sich per Rechtsklick/langem Drücken nach
  oben holen.
- **Einstellungen › Werkzeuge**: Schalter je großem Werkzeug (PDF, Kreativ, Video, Browser, Lernspiele …). Neue Werkzeuge
  sind anfangs aus; Seitenleiste und Startseite zeigen nur Eingeschaltetes. Lora schlägt passende Werkzeuge vor („Für
  PDFs gibt es den PDF-Werkzeugkasten – einschalten?“).
- **Startseite**: oben eine Zeile Kacheln nach Tätigkeiten (Vorschlag: *Fragen & schreiben* · *TU Vorlagen* · *Dokument
  erstellen* · *Dateien auswerten* · *Literatur finden*; Wortlaut vor Umsetzung abstimmen), darunter *Zuletzt* und
  *Favoriten*. Lora bleibt, aber ruhiger (eine Idee statt Liste).
- Umbenennung *Vorlagen → TU Vorlagen* überall (Seitenleiste, Befehlspalette, Startseite, README, Lora-Texte).
- **Fertig, wenn**: Seitenleiste zeigt oben höchstens 5 Einträge, „Mehr“ enthält den Rest, Werkzeuge lassen sich aus- und
  einschalten, Tastenkürzel funktionieren weiter.
- Betrifft: `ui/AppDrawer.kt`, `ui/CommandPalette.kt`, `ui/TemplatesScreen.kt`, `ui/LoraUi.kt`, `ui/ChatScreenParts.kt`,
  `ui/SettingsOverview.kt`, `ui/I18n.kt`.

### AP-03 ☐ Pseudonymisierung: treffsicherer · M
Heute rutschen Namen durch, und gleichzeitig werden Namen ersetzt, die nicht ersetzt werden sollen (Autor:innen in
Büchern, Namen in Ergebnissen der Websuche) – das führt zu falschen Antworten.
1. **Öffentliche Quellen ausnehmen**: Ergebnisse der Websuche, Bücher und Paper (erkennbar an ISBN/DOI) und
   Literatureinträge gelten als öffentlich und werden nicht pseudonymisiert. Anhänge lassen sich als *öffentlich* oder
   *vertraulich* markieren (Vorschlag der App, änderbar).
2. **Erlaubt-Liste automatisch**: Autor:innen aus *Literatur* und aus Literaturangaben im Text, bekannte öffentliche
   Personen und eine eigene „Nie ersetzen“-Liste werden nie ersetzt.
3. **Ein-Klick-Korrektur**: In der Vorschau und im Agent-Protokoll „nicht ersetzen“ bzw. „auch ersetzen“ antippen; die App
   merkt sich das für künftige Chats.
4. **Mehr Treffer**: Namen ohne Anrede/Titel und seltene Namen, zusätzlich Orte, Schulen, Institutionen und indirekte
   Merkmale („die Schulleiterin in X“).
5. **Anhänge und Dateien** (PDF, Word, Projektordner) genauso gründlich wie der Chattext.
6. **Lokales Erkennungsmodell** als Angebot, nicht als Pflicht: findet mehr, kostet Rechenzeit. Einstellung mit klarer
   Abwägung „schneller ↔ gründlicher“; im Tresor (AP-10) standardmäßig an.
- Testsammlung mit Beispieltexten (bekannte Namen, öffentliche Autor:innen, Literaturangaben) als Unit-Tests, damit
  Verbesserungen messbar sind.
- **Fertig, wenn**: Zusammenfassen eines Buchs und Websuche ersetzen keine Autor:innen mehr; die Testsammlung findet
  deutlich mehr private Namen als heute; Korrekturen wirken im nächsten Chat.
- Betrifft: `data/Pseudonymizer.kt`, `data/PiiDetector.kt`, `data/Privacy.kt`, `ui/PrivacySettings.kt`, `ui/AgentProtocol.kt`.

### AP-04 ☐ Weniger Arbeitsspeicher (W) · S–M (bisher Teil von Q-05)
Die Windows-App braucht ohne lokale Modelle ca. 500 MB bis 1 GB. Hauptursache: Der Java-Speicher hat keine Obergrenze
(Standard: ein Viertel des Arbeitsspeichers) und gibt freien Speicher kaum an Windows zurück. Maßnahmen nach Wirkung:
1. **Messen**: Anzeige unter *Einstellungen › Daten* (Java-Speicher, gesamt) und Messpunkte für Start, eine Stunde Chat,
   großer PDF-Anhang, Transkription – vorher und nachher.
2. **Speicher begrenzen und zurückgeben**: Obergrenze (`-Xmx`, mit großen PDFs testen), freien Speicher im Leerlauf
   zurückgeben (`-XX:G1PeriodicGCInterval`, `MinHeapFreeRatio`/`MaxHeapFreeRatio`), `-XX:+UseStringDeduplication`;
   in `desktop/build.gradle.kts` und `ADEU LLM.cfg` (`release-local.sh`).
3. **Chats nicht alle im Speicher**: Heute liegen alle Chats mit allen Nachrichten (auch angehängte Texte bis 120 000
   Zeichen) im Speicher. Für die Liste reichen Titel, Datum und Projekt; die Volltextsuche liest von der Platte bzw.
   aus einem Index.
4. **Schwere Teile erst bei Bedarf** laden und danach wieder freigeben: Spracherkennung (sherpa-onnx), PDFBox,
   openhtmltopdf, Rhino.
5. **Bilder**: Vorschaubilder verkleinert halten, Zwischenspeicher begrenzen.
- **Fertig, wenn**: Messwerte vorher/nachher im Pull Request; im Leerlauf deutlich weniger als heute, und nach großen
  Aufgaben sinkt der Verbrauch wieder.
- Betrifft: `desktop/build.gradle.kts`, `.github/scripts/release-local.sh`, `data/ChatRepository.kt`, `desktop/…/Main.kt`.

### AP-05 ☐ Einheitlicher Modus-Rahmen · M (bisher W2-03)
- Gemeinsames Modell `ChatMode` (Name, Symbol, Erklärzeile, Beispielanfrage, Datenhinweis, Werkzeuge, Prompt-Zusatz,
  Standardmodell). Bestehende Modi (Literatursuche, Sprachmodus …) darauf umstellen.
- Anzeige: Chip über dem Eingabefeld mit ✕, darunter eine Zeile „Was passiert hier · Daten: …“, im leeren Chat ein
  Beispiel zum Antippen.
- Modi über `/`-Befehl, Befehlspalette, Startseite und Lora aufrufbar.
- **Fertig, wenn**: Jeder Modus sieht gleich aus und lässt sich mit ✕ verlassen; neue Modi brauchen nur eine Definition.
- Betrifft: neu `agent/ChatModes.kt`, `ui/ChatComposer.kt`, `ui/ChatViewModel.kt`.

### AP-06 ☐ Rückfragen mit Antwortknöpfen · M (neu)
Wie die Fragerunden, mit denen dieser Plan entstanden ist: Statt zu raten, stellt die KI 1–4 kurze Fragen mit
Antwortknöpfen (Einfach- oder Mehrfachauswahl, immer auch freie Antwort).
1. **Werkzeug für den Chat** (`ask_user`): alle Chat-Modelle können vor großen Aufgaben (Dokument, Antrag, Stapel)
   strukturiert nachfragen. Die Fragen erscheinen als Karte im Chat, die Antworten gehen als eine Nachricht zurück.
2. **Lora fragt bei Unklarem**: mehrdeutige Wünsche klärt Lora mit einer Fragekarte, bevor sie navigiert oder übergibt
   (nutzt AP-07).
3. **Planungsmodus** (Modus nach AP-05): Lora führt Fragerunden zu einem Vorhaben (Seminar, Antrag, Projekt, Paper,
   Arbeitsplan) und erstellt daraus einen Plan als Dokument.
4. **TU Vorlagen als Fragerunde**: Vorlagen mit vielen Feldern wahlweise Schritt für Schritt mit Lora statt im Formular
   ausfüllen.
- **Fertig, wenn**: Ein Modell stellt eine Rückfrage mit Knöpfen, die Antwort fließt in die Aufgabe ein; Planungsmodus
  erzeugt aus mehreren Runden ein Plan-Dokument; eine TU Vorlage lässt sich als Fragerunde ausfüllen.
- Betrifft: neu `agent/AskUserTool.kt`, `ui/ChatMessages.kt`, `ui/TemplateUi.kt`, `agent/AgentRunner.kt`.

### AP-07 ☐ Lora als Navigator · L (bisher W2-04, W2-05, W2-06)
- Eigene, schlanke Agent-Schleife **LoraAgent** mit **GLM-5.3 Flash**, Denkphase aus, kurzer System-Prompt.
- **Landkarte der App** (`AppMap`): alle Bereiche, Einstellungen, Modi, TU Vorlagen und Werkzeuge mit ID, Pfad,
  Beschreibung, Synonymen und Plattform. Wird aus vorhandenen Registern erzeugt (Einstellungssuche, Vorlagen, Modi),
  nicht doppelt gepflegt.
- Werkzeuge für Lora:
  - `navigate(ziel)` – öffnet Bereich/Einstellung und **hebt die Option mit Leuchtrahmen hervor** (Spotlight).
  - `open_mode(modus, felder)` – öffnet einen Modus/eine Vorlage vorausgefüllt.
  - `change_setting(id, wert)` – nur nach Bestätigung.
  - `enable_tool(werkzeug)` – schlägt ein ausgeschaltetes Werkzeug vor (AP-02), einschalten nach Bestätigung.
  - `handoff_chat(modell, anfrage, zusammenfassung)` – übergibt an ein Chat-Modell (Auswahl über `ModelRouter`).
  - `explain(thema)` – beantwortet „Wie geht …?“ aus der eingebauten Hilfe (README-Abschnitte).
  - Rückfragen mit Antwortknöpfen aus AP-06.
- **Lokal zuerst**: einfache Wünsche („Wo stelle ich die Stimme um?“) löst die lokale Suche sofort ohne Anfrage; das
  Modell nur bei echten Gesprächen.
- **Einstiege**: schlafend unten rechts in den Einstellungen und weiteren passenden Bereichen (Klick weckt Lora: „Was
  möchtest du machen oder einstellen?“, Text oder Sprache), Startseite, Befehlspalette (Strg+K → „Lora fragen“), langes
  Drücken auf Lora im Chat. Lora reagiert, unterbricht nicht – höchstens ein einmaliger Hinweis je Bereich.
- **Erster Start, kurz**: höchstens zwei Fragen – Lora *überall / nur im Chat / aus* und welche Werkzeuge sichtbar sein
  sollen. *Einstellungen › Lora*: Modus, Modell, reduzierte Animationen, Hinweise erlaubt; der Schalter *Lora
  ausblenden* geht darin auf.
- Datenschutz: in Projekten „Nur auf diesem Gerät“ nur lokale Suche bzw. lokales Modell; Lora-Anfragen erscheinen im
  Agent-Protokoll und im Verbrauch. Lora läuft nur auf Abruf, nie im Hintergrund mitlesend.
- **Fertig, wenn**: „Dunkles Design“ → Lora springt zu *Darstellung* und hebt den Schalter hervor; „Ich will ein PDF
  zusammenfügen“ → Lora schlägt den PDF-Werkzeugkasten vor; „Erklär mir Exzerpte“ → Übergabe an einen Chat.
- Betrifft: neu `agent/LoraAgent.kt`, `data/AppMap.kt`, `ui/LoraHelper.kt`; `ui/AppRoot.kt`, `ui/NavRules.kt`,
  `ui/SettingsOverview.kt`, `ui/Welcome.kt`, `agent/ModelRouter.kt`.

### AP-08 ☐ Belegpflicht und Quellenmodus · M (bisher W3-03, W5-02)
- **Belegpflicht**: Antworten enthalten zu jeder Aussage eine Fundstelle (Datei + Seite, DOI, URL, Transkriptzeit).
- Prüfschritt nach der Antwort: Belege gegen die Quelle abgleichen (Zitat vorhanden?), unbelegte Sätze markieren oder
  entfernen; Belegprotokoll am Ende, exportierbar.
- Klick auf einen Beleg öffnet die Stelle (PDF-Seite, Wissenssammlung, Webseite); Darstellung mit den Quellenmarken aus AP-01.
- **Quellenmodus** (nach AP-05) für jeden Chat; Quellen: Wissenssammlung, Projektordner, Anhänge, Web, Literatur.
- **Fertig, wenn**: Im Quellenmodus hat jede Aussage einen Beleg, falsche Zitate werden markiert, ein Klick öffnet die Stelle.
- Betrifft: neu `agent/Evidence.kt`; `ui/ChatMessages.kt`, `data/Knowledge.kt`.

### AP-09 ☐ Exzerpt-Agent · M (bisher W5-01)
- Standardfelder: Bibliografie, Fragestellung, Theorie, Methode, Stichprobe, zentrale Befunde, wörtliche Zitate mit Seite,
  Begriffe, eigene Bewertung, Anschlussfragen.
- Schemata je Texttyp (empirisch, theoretisch, Review, Buchkapitel); eigene Schemata als TU Vorlage.
- Mit Belegpflicht (AP-08); Ergebnis am Literatureintrag und als Dokument/Tabelle. Mehrere Texte nacheinander zunächst
  einfach, große Stapel mit Pause/Fortsetzen über AP-20.
- Baut auf der vorhandenen Vorlage *Forschung › Exzerpt* auf.
- **Fertig, wenn**: Ein PDF ergibt ein Exzerpt mit geprüften Seitenangaben, das am Literatureintrag hängt.

### AP-10 ☐ Tresor für sensible Forschung · M (neu, ersetzt W4-04)
- Eigener Bereich für sensible Chats und Projekte, z. B. Interviews oder Daten von Studierenden.
- **App-interne PIN**, festgelegt beim ersten Öffnen des Tresors; sperrt nach Inaktivität. Beim Einrichten wird ein
  **Wiederherstellungsschlüssel** zum Ausdrucken/Aufbewahren angezeigt, mit dem sich die PIN neu setzen lässt.
- **Verschlüsselt gespeichert** (Schlüssel aus PIN; Android Keystore bzw. Windows DPAPI zusätzlich); Tresor-Inhalte
  erscheinen nicht in Suche, Gedächtnis und Vorschlägen außerhalb des Tresors.
- **Strenge Pseudonymisierung** (aus AP-03, im Zweifel ersetzen; lokales Erkennungsmodell an) und **nur Uni-Server oder
  lokale Modelle** (Datenschutzstufe mindestens „Nur TU Dresden“).
- **Sync wählbar je Tresor-Chat** (sobald AP-12 da ist): „nur dieses Gerät“ oder „verschlüsselt synchronisieren“.
- **Fertig, wenn**: Tresor-Chat ist ohne PIN nicht lesbar (auch nicht als Datei), PIN lässt sich mit dem
  Wiederherstellungsschlüssel zurücksetzen, externe Modelle sind gesperrt.
- Betrifft: `data/SecretBox.kt`, `data/SecureStorage.kt`, `data/Privacy.kt`, `data/ChatRepository.kt`, `ui/PrivacySettings.kt`.

### AP-11 ☐ PDF-Werkzeugkasten Stufe 1 · L (bisher W8-02, Stufen 1–3 und Teil von 5)
Alltagsumfang nach dem Vorbild pdf24 + Xodo; als einschaltbares Werkzeug (AP-02).
1. **Betrachter**: mehrere PDFs in Tabs, Miniaturen, Gliederung/Lesezeichen, Suche, Nachtmodus.
2. **Seiten**: zusammenführen, teilen, extrahieren, löschen, drehen, sortieren (Ziehen in der Miniaturansicht),
   Seitenzahlen.
3. **Markieren & Notizen**: hervorheben, unterstreichen, durchstreichen, Freihand, Notizen, Stempel; Kommentarliste exportieren.
4. **PDF ausfüllbar machen**: Der Agent erkennt, wo Eingaben hingehören (Linien, Kästchen, „Name: ____“), und setzt die
  Formularfelder selbst; der Nutzer korrigiert bei Bedarf (verschieben, Größe, Feldtyp). Ausfüllen gibt es schon.
- Lora-Animation zum Werkzeug: Schere, Hefter, Stempel.
- Plattform: Windows voll; Android Betrachter, Seiten und Markieren.
- Technik: PDFBox (Android: pdfbox-android, Windows: PDFBox 3 – vorhanden), Rendering über PdfRenderer bzw. PDFBox.
  PdfCraft nur für Abläufe und Bedienung ansehen (*Anhang: Craft-Apps*).
- **Fertig, wenn**: Zwei PDFs in Tabs öffnen, zusammenfügen, Seiten umsortieren, markieren und speichern; ein
  Papierformular wird per Agent ausfüllbar und lässt sich danach am PC ausfüllen.

---

## Danach

### AP-12 ☐ Einfacher Sync zwischen PCs (W) · M (bisher W4-02, stark vereinfacht)
- *Einstellungen › Synchronisation*: „**Datenordner in Cloud-Ordner legen**“ – Nextcloud, Google Drive für Desktop oder
  OneDrive, der auf beiden PCs eingerichtet ist (wie ein Obsidian-Tresor). Auf dem zweiten PC „Vorhandenen Ordner
  öffnen“ → alles ist da.
- Synchronisiert wird alles (Chats, Projekte, Dokumente, Literatur, TU Vorlagen, Gedächtnis, Einstellungen), **API-Schlüssel
  nie**.
- Gleichzeitiges Arbeiten verhindern statt Konflikte auflösen: Sperrdatei „geöffnet auf PC X seit …“, Hinweis beim
  Start; Konfliktdateien des Cloud-Dienstes erkennen und beide Fassungen behalten.
- Speicherorte (Windows) und Datenordner-Verlegung gehen darin auf.
- **Fertig, wenn**: Chat am Büro-PC beginnen, am Laptop weiterführen, ohne ZIP-Umzug; nichts geht verloren, wenn beide
  PCs kurz nacheinander offen waren.

### AP-13 ☐ Mail und Kalender über das klassische Outlook (W) · L (bisher W7-01)
- **Anbindung**: lokal über COM an das klassische Outlook (Mails, Ordner, Kalender, Frei/Gebucht) – ohne Server-Zugang
  und ohne IT-Freigabe. Technik prüfen (z. B. JACOB/com4j oder kleine PowerShell-Brücke).
- Funktionen:
  - Postfach zusammenfassen („Was ist seit gestern wichtig?“).
  - **Mails in Ordner sortieren** (Regeln vorschlagen, Verschieben nach Bestätigung oder als feste Regel).
  - Antworten im eigenen Stil als **Entwurf** in Outlook (Gedächtnis).
  - Fristen und Termine aus Mails erkennen → Kalender/Aufgaben.
  - Freie Zeiten mit Kolleg:innen finden (Frei/Gebucht), Einladungen vorbereiten.
- **Harte Regeln**: nie löschen, nie ohne ausdrückliche Aufforderung senden; jede Aktion im Agent-Protokoll.
- Pseudonymisierung vor Modellanfragen (AP-03).
- Lora-Animation zum Werkzeug: Briefe sortieren, Seiten umblättern.
- **Fertig, wenn**: Zusammenfassung des Posteingangs, ein sortierter Ordner, ein Antwortentwurf und ein Kalendereintrag
  aus einer Mail – ohne dass etwas gesendet oder gelöscht wurde.

### AP-14 ☐ Browser neben dem Chat · L (bisher W9-08)
- Wie bei Google Antigravity: Links aus Chat, Literatur und Dokumenten öffnen sich **in Tabs neben dem Chatfenster**.
- Kleine Zusatzfunktionen: Fragen zur Seite (mit Belegen, AP-08), Seite zusammenfassen, in Literatur oder
  Wissenssammlung übernehmen, Paper-PDFs im PDF-Werkzeugkasten öffnen.
- Technik: keine eigene Engine. Windows: Chromium über JCEF, fest in der App (nachladbar erst ab AP-25); Android später
  einfache Fassung mit System-WebView. Anmeldung über die TU (Shibboleth) bleibt erhalten.
- Browser-Automatisierung (Agent füllt Formulare aus) → Ideen.
- **Fertig, wenn**: Link im Chat öffnet sich als Tab daneben, eine Frage zur Seite wird mit Beleg beantwortet.

### AP-15 ☐ PDF-Werkzeugkasten Stufe 2 · L (bisher W8-02, Stufen 5–7)
- **OCR**: Scans durchsuchbar machen (lokal Tesseract, alternativ Vision-Modell je Datenschutzstufe).
- **Umwandeln**: Word/Excel/PowerPoint/Bilder/HTML ↔ PDF, PDF → Text/Markdown.
- **Unterschreiben**: gezeichnet oder als Bild einfügen.
- **KI**: „Frag das PDF“ mit Belegpflicht, Tabellen herausziehen, Formulare erklären.

### AP-16 ☐ Office-Ausbau · XL (bisher W8-01)
Die Editoren (Dokument, Präsentation, Tabelle, Markdown) gibt es schon; Ausbau im Alltagsumfang. Stufen:
1. **Kommentare und Änderungen nachverfolgen**, verlustfreies Öffnen und Speichern von .docx und .xlsx.
2. **Seitenlayout** im Dokument: Seitenansicht mit Rändern, Abschnitte, Kopf-/Fußzeilen, Inhaltsverzeichnis, Formeln (LaTeX).
3. **Folien frei gestalten**: Elemente frei platzieren, Bilder und Formen, Masterfolien.
- Android: ansehen, kleine Änderungen, KI-Assistent.
- Code aus WordCraft und DeckCraft ggf. ansehen (*Anhang: Craft-Apps*).

### AP-17 ☐ Lernspiele-Baukasten · L (bisher W6-03, neu gedacht)
- Die KI baut **eigenständige Browserspiele aus HTML, JavaScript und CSS** (Quiz, Zuordnen, Memory, Escape-Room …) aus
  Skript, Folien oder Beschreibung.
- **Direkt in der App testen** (Vorschau im eingebetteten Browser), ändern per Chat („mach Level 2 schwerer“), Versionen.
- **Sammlung in der App** mit Vorschau und Suche.
- **Export als HTML-Datei/ZIP für OPAL** (und Moodle). SCORM/H5P → Ideen.
- Sicherheit: Spiele laufen abgeschottet (kein Zugriff auf App-Daten, keine Netzwerkanfragen ohne Freigabe).
- **Fertig, wenn**: „Mach ein Zuordnungsspiel zu Kapitel 3“ ergibt ein spielbares Spiel in der App, das sich als
  HTML-Datei in OPAL nutzen lässt.

### AP-18 ☐ Dateiverwaltung · L (bisher W8-06, mit W4-01)
- Ordner, Schlagwörter, Volltextsuche über alle Formate, Versionsvergleich, „Zuletzt bearbeitet“.
- Für die Volltextsuche bei Bedarf eine lokale Index-Datenbank (SQLite mit Volltextindex; bisher W4-01) – nur, wenn die
  Suche ohne Index zu langsam ist. Sanfte Migration, Sicherung & Umzug bleiben kompatibel.

### AP-19 ☐ KI-Nutzungsnachweis · M (bisher W5-07)
- Aus dem Agent-Protokoll eines Chats/Projekts: Modelle, Zweck, Zeitraum, betroffene Textteile, Werkzeuge.
- Formate: TUD-Vorgaben/Prüfungsordnung (für Studierende), Verlags-Policies, kurze Fußnote; als Dokument oder Text.

---

## Später

### AP-20 ☐ Stapel und Massenkontrolle · L (bisher W3-01, W6-01, Q-02)
- **Stapel**: Warteschlange für viele Dateien (Ordner, ZIP, Mehrfachauswahl) mit demselben Auftrag/Raster; Fortschritt
  mit Lora-Karte („23 von 80“, Animation: Akten abarbeiten), Pause/Fortsetzen, übersteht Neustart, läuft im Hintergrund,
  optional nachts über *Geplante Aufgaben*; Drosselung über `RateLimiter`, interaktive Anfragen haben Vorrang.
- **Massenkontrolle** (typisch 30–100 Abgaben je Semester): Ordner/ZIP (z. B. OPAL-Abgaben) + Raster (Tabelle oder TU
  Vorlage *Bewertungsraster*), Kalibrierung mit 2–3 von Hand bewerteten Arbeiten; je Arbeit Punkte je Kriterium,
  Begründung, Belegstellen; Ergebnistabelle mit Ausreißern, Feedback je Person (Serienbriefe vorhanden).
- **Die KI schlägt nur vor, ein Mensch entscheidet**; Protokoll der Entscheidung. Standard: pseudonymisiert, „Nur TU Dresden“.
- **Fertig, wenn**: 80 PDFs laufen ohne Eingriff durch, Abbruch und Fortsetzen verlieren nichts.

### AP-21 ☐ Antrags-Werkstatt · L (bisher W5-05)
- Ausschreibung einlesen (PDF/URL: DFG, BMBF, EU, SAB, TUD-intern) → Checkliste, Fristen in den Kalender.
- Gliederung nach Förderer-Vorgabe, Arbeitspakete mit Gantt, Budgettabelle, Zeichen-/Seitenwächter, DMP (Vorlage
  vorhanden), Ethik; Probelauf mit einer Gutachter-Rolle, Abgabe-Check. Gut geeignet für den Planungsmodus (AP-06).

### AP-22 ☐ Nextcloud fest eingebaut · M (bisher Teil von W9-03)
- Dateien aus Nextcloud direkt öffnen/anhängen und Ergebnisse dort speichern; auf Android per WebDAV (Grundlage für AP-28).

### AP-23 ☐ MCP-Client · L (bisher W9-01, W9-02)
- Fern-Server (Streamable HTTP mit OAuth/Token) auf beiden Plattformen, lokale Server (stdio) unter Windows.
- *Einstellungen › Verbindungen*: Server hinzufügen, Werkzeuge ansehen, je Werkzeug erlauben/fragen/sperren; fremde
  Werkzeuge laufen über die *Freigaben* und halten die Datenschutzstufe ein.

### AP-24 ☐ Kreativbereich (D) · XL (bisher W8-03)
Alltagsumfang nach dem Vorbild Canva, Schwerpunkt Corporate Design. Nur Windows (kein Android).
1. Leinwand mit Ebenen, Ausrichten, Rückgängig, mehrseitige Designs; Brand-Kit (TUD-Farben, Schriften, Logos – vorhanden).
2. Vorlagen Uni-Kontext: Tagungsposter, Flyer, Aushang, Einladung, Urkunde/Teilnahmebescheinigung, Social Media,
   OPAL-Kursbanner, Titelbild für Lehrvideos.
3. Text, Formen, Icons, QR-Codes, Bilder (zuschneiden, FLUX-Bilder direkt auf die Leinwand), KI: „Poster aus diesem Paper“.
4. Export PNG, PDF (Druck), PowerPoint.
- Lora-Animation: Pinsel, Farbpalette. Code aus DesignCraft/VectorCraft/PhotoCraft prüfen (*Anhang: Craft-Apps*).

### AP-25 ☐ Videoeditor und nachladbare Erweiterungen (D) · XL (bisher W8-07, Teil von W8-00)
Alltagsumfang nach dem Vorbild DaVinci Resolve, nur Desktop.
1. **Nachladbare Erweiterungen** einführen: der Videoeditor (und danach ggf. Browser-Engine, OCR-Daten) wird beim ersten
   Öffnen geladen („Videoeditor einrichten · ca. 200 MB“), liegt unter `%LOCALAPPDATA%\ADEU LLM\tools\…`, kommt über
   denselben Updater, lässt sich entfernen.
2. **Schnitt**: Zeitleiste mit Video- und Tonspuren, schneiden, trimmen, Übergänge, Titel und Bauchbinden im TUD-Design.
3. **Ton und Untertitel**: Pegel, Rauschminderung, Untertitel aus der vorhandenen Transkription einbrennen oder als SRT/VTT.
4. **Export** MP4 mit Vorgaben für OPAL und YouTube; KI: Rohschnitt aus dem Transkript (Füllwörter, Pausen).
- Technik: ffmpeg (in der Windows-Version enthalten), Proxy-Dateien für flüssigen Schnitt; Lizenz des ffmpeg-Builds prüfen.

### AP-26 ☐ PDF-Werkzeugkasten Stufe 3 · M (bisher W8-02, Stufen 4, 8, 9)
- Echtes Schwärzen, Text/Bilder ändern, verkleinern, PDF/A, Passwort, Metadaten; zwei PDFs vergleichen, Barrierefreiheit
  nach PDF/UA.

### AP-27 ☐ Mail über den Server · L (bisher W7-01, Server-Weg)
- Wenn jemand das neue Outlook nutzt (oder für Android): Exchange (EWS bzw. Microsoft Graph mit Freigabe der IT) oder
  IMAP/SMTP; Funktionen wie AP-13. Vorher klären, wie die TU Exchange betreibt (Offene Fragen).

### AP-28 ☐ Sync für Android · M (bisher W4-02, Stufe d)
- Den Cloud-Ordner aus AP-12 auf Android über Nextcloud/WebDAV oder den Systemdialog nutzen. Nice to have.

### AP-29 ☐ Englisch vervollständigen · M (bisher W9-07)
- Alle Texte in `I18n` überführen, englische Fassung vollständig (Einstellungen, Editoren, TU Vorlagen, Fehlermeldungen,
  Lora). Keine weiteren Sprachen.

### AP-30 ☐ Barrierefreiheit der App · M (bisher W2-11)
- Screenreader (TalkBack/Narrator): Beschriftungen aller Knöpfe, sinnvolle Reihenfolge; große Schrift ohne
  abgeschnittene Texte; komplette Bedienung per Tastatur unter Windows.

**Ganz am Ende** – erst, wenn alles andere fertig ist:

### AP-31 ☐ macOS und Linux · L (bisher W9-06)
- Compose Desktop baut bereits plattformunabhängig; zu tun: Pakete (DMG signiert/notarisiert, AppImage/DEB),
  PDF-Rendering ohne Edge, Pfade, Autostart, Updater, Tastenkürzel (⌘), nachladbare Erweiterungen je Plattform.

### AP-32 ☐ Website · XL (bisher W9-09)
- Möglichst viele Funktionen im Browser: Chat, TU Vorlagen, Lora, Literatur, Office-Editoren, PDF-Werkzeuge.
- Nicht im Web: lokale Modelle, Agent in lokalen Ordnern, Videoeditor, eigener Browser.
- Technik (zu prüfen): Compose Multiplatform für Web (Wasm); API-Schlüssel nicht im Klartext im Browser; CORS der Uni-API.

---

## Ideen

Ohne Auftrag; Details der bisherigen Pakete stehen in der Git-Historie dieser Datei (Fassung vom 30.10.2026).

- **Forschung**: Gutachter-Panel mit Mehr-Agenten-Engine (W3-02, W5-03), Systematic Review nach PRISMA (W5-04),
  Forschungsdaten veröffentlichen über OPARA/Zenodo (W5-06), Profilpflege mit ORCID/FIS/Publikationsliste/Lebenslauf (W5-08).
- **Lehre & Verwaltung**: Klausuren nachträglich analysieren (W6-02), Live-Untertitel im Hörsaal (W6-04, eher Spielerei),
  Podcast aus Dokumenten (W7-02), Assistent für Bewerbungsverfahren (W7-03), Lernspiele als SCORM/H5P.
- **Werkzeuge**: Diagramme und Whiteboard (W8-04), mehr Dateiformate (W8-05), Zitieren beim Schreiben im Dokument-Editor,
  Browser-Automatisierung für Portale ohne Schnittstelle.
- **Anbindungen**: Zotero, Obsidian, Moodle, LimeSurvey und weitere aus dem alten Katalog (W9-03); ADEU als MCP-Server
  (W9-04); Plugins (W9-05).
- **Bedienung**: Rollenauswahl beim ersten Start (W2-08), Fehlermeldungen in Klartext mit Lösungsknopf (W2-09),
  Einfügen über die Gboard-Leiste/langes Drücken auf Android (Rest von W1-01).
- **Team**: gemeinsamer Ordner für TU Vorlagen der Professur (abgespeckte W4-03, sehr niedrige Priorität).
- **Ältere Ideen**: Literatur-Radar, Statistik-Werkstatt, Zettelkasten/Wissensgraph, Förder-/Call-Radar,
  Fragebogen-Auswertung, KI als zweite Coder:in, Lehrveranstaltungs-Cockpit, Kurs-Assistent für Studierende, Folien zu
  Lernvideo, Feedback-Werkstatt, Prüfungsorganisation, Barrierefreiheits-Check für Lehrmaterial, Sitzungs-/
  Gremienassistent, zentrale Aufgaben und Fristen, Synopse-Werkzeug, Dokumenten-Scanner, Workflow-Baukasten,
  Schnellzugriff (Widget/globales Kürzel), Stift und Handschrift, Hausstil-Prüfung.

---

## Erledigt

| Paket | Ergebnis | Rest |
| --- | --- | --- |
| ☑ Screenshots aus der Zwischenablage (W1-01) | *Aus der Zwischenablage* im „+“-Menü, Strg+V | Gboard-Leiste → Ideen |
| ☑ Absätze im Chat auf Windows (W1-02) | Umschalt+Enter in Chat, *KI fragen*, Dokument-Assistent | – |
| ☑ Zoom mit Strg + / − / 0 (W1-03) | 80–200 %, Strg+Mausrad, Regler unter *Darstellung* | – |
| ☑ Projektordner auf Android (W1-04) | Ordner aus dem Systemdialog, Lesen über `list_project_files`/`read_project_file` | Schreiben in den Ordner nur unter Windows |
| ☑ Sprachmodus beschleunigen (W1-05) | Vorlesen ab dem ersten Satz (`audio/SpeechStream.kt`), eigenes Modell, Pause 0,8 s, Messwerte im Agent-Protokoll | Messung auf Geräten |
| ☑ Problem melden und „Was ist neu“ (W1-06) | *Einstellungen › Problem melden*, Dialog nach Updates | – |

---

## Offene Fragen

1. **Startseite** (AP-02): Wortlaut der Tätigkeits-Kacheln final abstimmen.
2. **Outlook über COM** (AP-13): Welche Brücke aus Kotlin/JVM (JACOB, com4j, PowerShell)? Läuft sie auf den Dienst-PCs
   ohne Admin-Rechte?
3. **Exchange der TU** (nur für AP-27): selbst betrieben (EWS) oder Exchange Online (Graph mit Freigabe der IT)? Wann
   stellt die TU auf das neue Outlook um?
4. **OPAL** (AP-17, AP-20): Lassen sich HTML-Dateien/ZIP-Pakete als Lernelement einbinden? In welchem Format kommen
   Abgaben aus OPAL?

---

## Anhang: Plattformen

Windows bekommt alles; Android nur, was dort sinnvoll ist.

| Werkzeug | Android | Windows |
| --- | --- | --- |
| Chat-Design, Übersicht, Lora, Modi, Rückfragen (AP-01, 02, 05–07) | ☐ | ☐ |
| Pseudonymisierung, Tresor (AP-03, AP-10) | ☐ | ☐ |
| PDF-Werkzeugkasten (AP-11, 15, 26) | Betrachter, Seiten, Markieren | voll |
| Sync (AP-12, AP-28) | später (AP-28) | ☐ |
| Mail und Kalender (AP-13, AP-27) | erst über den Server (AP-27) | ☐ (Outlook) |
| Browser neben dem Chat (AP-14) | später, einfach (WebView) | ☐ |
| Office (AP-16) | ansehen, kleine Änderungen | voll |
| Lernspiele (AP-17) | ansehen, spielen | voll |
| Kreativbereich (AP-24) | – (nicht geplant) | ☐ |
| Videoeditor (AP-25) | – (nicht geplant) | ☐ |
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
| Kreativbereich (AP-24) | DesignCraft, VectorCraft, PhotoCraft | In Entwicklung / Early alpha | Bester Kandidat für Übernahme: Leinwand-Datenmodell, Vektorpfade (Bézier, boolesche Operationen), Bildfilter und Mischmodi; Bedienung selbst |
| Videoeditor (AP-25) | FilmCraft, EffectCraft | In Entwicklung | Zeitleisten-Modell und Effekt-Graph ansehen; Grundlage bleibt ffmpeg |
| Nicht übernommen | LightCraft, SoundCraft, CADCraft, FigCraft | – | kein Bedarf an der Professur |
