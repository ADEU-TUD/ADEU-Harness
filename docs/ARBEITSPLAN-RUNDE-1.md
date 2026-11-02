# Arbeitsplan Lora – Runde 1 (AP-01 bis AP-37, Archiv)

Abgeschlossen am 02.11.2026. Diese Datei bewahrt die ausführlichen Beschreibungen, Entscheidungen und Ergebnisse der
ersten Runde. Der aktuelle Plan steht in [`ARBEITSPLAN.md`](ARBEITSPLAN.md); AP-32 Website ist dort in die *Ideen*
gewandert.

---

## Übersicht

| Abschnitt | Pakete |
| --- | --- |
| **Jetzt** | ~~AP-01 Chat-Design und Logo · AP-02 Übersicht und einschaltbare Werkzeuge · AP-03 Pseudonymisierung · AP-04 Weniger Arbeitsspeicher · AP-05 Modus-Rahmen · AP-06 Rückfragen mit Antwortknöpfen · AP-07 Lora-Navigator · AP-08 Belegpflicht und Quellenmodus · AP-09 Exzerpt-Agent · AP-10 Tresor · AP-11 PDF Stufe 1 · AP-33 Umbenennung in Lora · AP-34 Lora-Panel · AP-35 Lora hilft im Chat · AP-36 Animationen · AP-37 Lora leichter finden, Startseite mit Chips~~ (☑) – als Nächstes rücken die Pakete aus *Danach* auf |
| **Danach** | ~~AP-12 Einfacher Sync · AP-13 Mail und Kalender über Outlook · AP-14 Browser neben dem Chat · AP-15 PDF Stufe 2~~ (☑) · ~~AP-16 Office-Ausbau · AP-17 Lernspiele · AP-18 Dateiverwaltung · AP-19 KI-Nutzungsnachweis~~ (☑) – alles erledigt, als Nächstes rückt *Später* auf |
| **Später** | ~~AP-20 Stapel und Massenkontrolle · AP-21 Antrags-Werkstatt · AP-22 Nextcloud · AP-23 MCP-Client · AP-24 Kreativbereich (jetzt „Erstellen“ › Gestalten) · AP-25 Videoeditor · AP-26 PDF Stufe 3 · AP-27 Mail über den Server · AP-28 Sync für Android · AP-29 Englisch · AP-30 Barrierefreiheit · AP-31 macOS und Linux~~ (☑) · *ganz am Ende:* AP-32 Website |

Abhängigkeiten: AP-05 vor AP-06 (Planungsmodus), AP-07 und AP-08 · AP-06 vor AP-07 · AP-08 vor AP-09 und AP-14 (Fragen
zur Seite mit Belegen) · AP-03 vor AP-10 · AP-11 vor AP-15 vor AP-26 · AP-12 vor AP-28 · AP-20 vor großen Exzerpt-Stapeln.

---

## Jetzt

### AP-01 ☑ Chat-Design und Logo · M
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
- **Ergebnis (31.10.2026)**: umgesetzt; Logo = Lora liest (violett, weißes Gesicht, helle Kachel). Bilder aller Stile
  erzeugt `ChatLookTest` (`desktop/build/chat-look`), Symbole `LogoTest`. Rest: Vorher-Bilder für den Pull Request.

### AP-02 ☑ Übersicht: Seitenleiste, Startseite, einschaltbare Werkzeuge · M (bisher W2-01, W2-02, Teil von W8-00)
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
- **Ergebnis (31.10.2026)**: umgesetzt (`data/AppTools.kt`, `ui/ToolsSettings.kt`, `ui/NavRules.kt`). Kacheln mit dem
  Vorschlags-Wortlaut. PDF-Werkzeugkasten und Tresor stehen als „geplant“ im Register und werden mit AP-10/AP-11
  einschaltbar; Loras Vorschlag über dem Eingabefeld ist dafür schon da.

### AP-03 ☑ Pseudonymisierung: treffsicherer · M
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
- **Ergebnis (31.10.2026)**: umgesetzt, Testsammlung `PseudonymCorpusTest` (12/12 private Namen ersetzt, 7/7 öffentliche
  erhalten). Gründliche Erkennung nutzt ein installiertes lokales Sprachmodell; „im Tresor standardmäßig an“ kommt mit AP-10.

### AP-04 ☑ Weniger Arbeitsspeicher (W) · S–M (bisher Teil von Q-05)
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
- **Ergebnis (31.10.2026)**: umgesetzt (Obergrenze 1,5 GB und Rückgabe im Leerlauf, schlanke Chat-Liste, Entladen der
  Spracherkennung, kleine Vorschaubilder, *Einstellungen › Arbeitsspeicher* mit Messpunkten). Rest: Messwerte
  vorher/nachher auf einem Windows-PC eintragen (Messpunkte stehen in der App und im Log).

### AP-05 ☑ Einheitlicher Modus-Rahmen · M (bisher W2-03)
- Gemeinsames Modell `ChatMode` (Name, Symbol, Erklärzeile, Beispielanfrage, Datenhinweis, Werkzeuge, Prompt-Zusatz,
  Standardmodell). Bestehende Modi (Literatursuche, Sprachmodus …) darauf umstellen.
- Anzeige: Chip über dem Eingabefeld mit ✕, darunter eine Zeile „Was passiert hier · Daten: …“, im leeren Chat ein
  Beispiel zum Antippen.
- Modi über `/`-Befehl, Befehlspalette, Startseite und Lora aufrufbar.
- **Fertig, wenn**: Jeder Modus sieht gleich aus und lässt sich mit ✕ verlassen; neue Modi brauchen nur eine Definition.
- Betrifft: neu `agent/ChatModes.kt`, `ui/ChatComposer.kt`, `ui/ChatViewModel.kt`.
- **Ergebnis (31.10.2026)**: umgesetzt (`agent/ChatModes.kt`, `ui/ModeUi.kt`): Literatursuche, Planungsmodus, Sprachmodus;
  `/`, Strg+K, Startseite und Loras Ideen. Aufruf über Lora kommt mit AP-07.

### AP-06 ☑ Rückfragen mit Antwortknöpfen · M (neu)
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
- **Ergebnis (31.10.2026)**: Punkte 1, 3 und 4 umgesetzt (`ask_user`, `ui/QuestionCard.kt`, Planungsmodus,
  „Lieber mit Lora Schritt für Schritt“ in TU Vorlagen ab drei Feldern). Punkt 2 (Lora fragt bei Unklarem) kommt mit AP-07.

### AP-07 ☑ Lora als Navigator · L (bisher W2-04, W2-05, W2-06)
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
- **Ergebnis (31.10.2026)**: umgesetzt wie beschrieben (`data/AppMap.kt`, `agent/LoraAgent.kt`, `ui/LoraHelper.kt`,
  `ui/Spotlight.kt`). Leuchtrahmen an den wichtigsten Optionen (Darstellung, Chat, Lora, Datenschutz, Stimme); weitere
  Optionen öffnen ihre Seite ohne Rahmen. Damit ist auch AP-06 Punkt 2 erledigt (Lora fragt mit Antwortknöpfen nach).

### AP-08 ☑ Belegpflicht und Quellenmodus · M (bisher W3-03, W5-02)
- **Belegpflicht**: Antworten enthalten zu jeder Aussage eine Fundstelle (Datei + Seite, DOI, URL, Transkriptzeit).
- Prüfschritt nach der Antwort: Belege gegen die Quelle abgleichen (Zitat vorhanden?), unbelegte Sätze markieren oder
  entfernen; Belegprotokoll am Ende, exportierbar.
- Klick auf einen Beleg öffnet die Stelle (PDF-Seite, Wissenssammlung, Webseite); Darstellung mit den Quellenmarken aus AP-01.
- **Quellenmodus** (nach AP-05) für jeden Chat; Quellen: Wissenssammlung, Projektordner, Anhänge, Web, Literatur.
- **Fertig, wenn**: Im Quellenmodus hat jede Aussage einen Beleg, falsche Zitate werden markiert, ein Klick öffnet die Stelle.
- Betrifft: neu `agent/Evidence.kt`; `ui/ChatMessages.kt`, `data/Knowledge.kt`.
- **Ergebnis (31.10.2026)**: `agent/Evidence.kt` (Prüfung auf dem Gerät, Seiten über die Seitenmarken der PDFs),
  `ui/EvidenceCard.kt`. Klick öffnet PDF-Seite (PDF-Werkzeugkasten) oder Webseite; Stellen in der Wissenssammlung werden
  geprüft, aber noch nicht in einer eigenen Ansicht geöffnet.

### AP-09 ☑ Exzerpt-Agent · M (bisher W5-01)
- Standardfelder: Bibliografie, Fragestellung, Theorie, Methode, Stichprobe, zentrale Befunde, wörtliche Zitate mit Seite,
  Begriffe, eigene Bewertung, Anschlussfragen.
- Schemata je Texttyp (empirisch, theoretisch, Review, Buchkapitel); eigene Schemata als TU Vorlage.
- Mit Belegpflicht (AP-08); Ergebnis am Literatureintrag und als Dokument/Tabelle. Mehrere Texte nacheinander zunächst
  einfach, große Stapel mit Pause/Fortsetzen über AP-20.
- Baut auf der vorhandenen Vorlage *Forschung › Exzerpt* auf.
- **Fertig, wenn**: Ein PDF ergibt ein Exzerpt mit geprüften Seitenangaben, das am Literatureintrag hängt.
- **Ergebnis (31.10.2026)**: `research/Excerpts.kt`, `agent/ExcerptTools.kt` (`save_excerpt` nimmt das Exzerpt erst
  mit stimmenden Seiten an), Exzerpt-Modus, Knopf am Literatureintrag, *Exzerpte als Tabelle*. Große Stapel folgen mit AP-20.

### AP-10 ☑ Tresor für sensible Forschung · M (neu, ersetzt W4-04)
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
- **Ergebnis (31.10.2026)**: `data/Vault.kt`, `data/DeviceKey.kt` (Keystore/DPAPI), `ui/VaultScreen.kt`; strenge
  Pseudonymisierung über `Pseudonymizer.STRICT`. Tresor-Projekte (Ordner im Tresor) und Sync je Tresor-Chat kommen mit
  AP-12.

### AP-11 ☑ PDF-Werkzeugkasten Stufe 1 · L (bisher W8-02, Stufen 1–3 und Teil von 5)
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
- **Ergebnis (31.10.2026)**: `docs/PdfTools.kt` (je Plattform), `docs/PdfToolModel.kt` (Felderkennung),
  `ui/PdfRender.kt`, `ui/PdfToolScreen.kt`, Agent-Werkzeug `make_pdf_fillable`; Lora schneidet, heftet und stempelt.
  Markieren per Rechteck (noch ohne Einrasten am Text).

### AP-33 ☑ Umbenennung in Lora und sichtbare Fehler · S–M (neu, 31.10.2026)
- App-Name **Lora** mit Slogan *Der freundliche Helfer für alle Wünsche in Forschung, Lehre und Verwaltung an der TU
  Dresden*: Fenstertitel, Infobereich, Android-App-Name, Startanimation, Seitenleiste, Über/Hilfe, Update-Dialoge,
  Benachrichtigungen, Dokument-Metadaten, Loras Prompts, Release-Titel, README und Updater-README.
- **Technische Namen bleiben** (`ADEU LLM.exe`, `%APPDATA%\ADEU LLM`, `%LOCALAPPDATA%\ADEU LLM`, `ADEU-LLM-…`-Dateien,
  Autostart): ältere Versionen suchen beim Update genau diese Namen. Umzug später nur mit Migration (Ideen).
- **Übergabe an den Chat**: Karte *Von Lora übergeben · Modell · Grund* über der Frage; Loras Zusammenfassung geht als
  versteckte Anweisung mit statt sichtbar im Text; das gewählte Modell bleibt (vorher sprang die automatische Wahl auf
  das Standardmodell bzw. ein Förderprogramm landete wegen „programm“ beim Programmiermodell); Standard für Übergaben
  **DeepSeek V4.1 Flash**, andere nur in Sonderfällen, die Lora entscheidet und begründet; *Übergaben erst zeigen*.
- Schlafende Lora verdeckt keine Einträge mehr; kurze Chat-Titel aus der ersten Nachricht (`data/ChatTitles.kt`);
  breite Tabellen zeigen, dass sie seitlich weitergehen.
- Betrifft: `Brand.kt`, `ui/Logo.kt`, `ui/AppDrawer.kt`, `desktop/…/Main.kt`, `desktop/launcher/Launcher.cs`,
  `agent/LoraAgent.kt`, `agent/ModelRouter.kt`, `ui/ChatScreen.kt`, `ui/ChatMessages.kt`, `ui/Markdown.kt`.

### AP-34 ☑ Lora-Panel mit Kontext · M (neu, 31.10.2026)
- Panel statt Popup: Seitenleiste rechts bei breiten Fenstern (bleibt beim Navigieren offen), Blatt von unten auf dem
  Handy; Gespräch bleibt bis *Neu beginnen*; Antworten schrittweise und formatiert; *Abbrechen*; freundliche Fehler.
- **Kontext**: Lora kennt den Bereich und zeigt passende Vorschläge („Das passt hier“), feste Ziele öffnen ohne Anfrage,
  Wünsche zum offenen Chat landen in diesem Chat.
- **Ergebnis-Karten** für Orte, Einstellungen (Vorher → Nachher, *Rückgängig*), Werkzeuge, Fristen und frühere Chats.
- Einstiege: **Strg+L** (Literatur: Strg+Umschalt+L), Lora-Knopf im Chat, *Frag Lora …* auf der Startseite, Android-
  Verknüpfung am App-Symbol, Strg+K, schlafende Lora.
- Betrifft: `ui/LoraHelper.kt`, `ui/AppRoot.kt` (beide), `ui/ChatScreenParts.kt`, `res/xml/shortcuts.xml`.

### AP-35 ☑ Lora hilft im Chat · M (neu, 31.10.2026)
- **Nächste Schritte** unter der letzten Antwort, auf dem Gerät erkannt: Fristen in den Kalender (.ics mit Erinnerung),
  Tabelle als Excel, DOIs in die Literatur, langer Text als Word.
- Neue Fähigkeiten: `search_chats` (frühere Chats, auch lokal „Wo hatte ich …?“), `add_reminder` (Frist/Erinnerung).
- **Hinweis beim Tippen**: klarer App-Wunsch im Chatfeld → Lora zeigt den Ort darüber (nie bei echten Fragen).
- **Arbeitskarte**: verständliche Schrittzeilen („Sucht im Web: „…““, „Liest dfg.de“), „Schreibt die Antwort …“.
- Betrifft: `agent/LoraSense.kt`, `ui/LoraChat.kt`, `ui/ChatScreen.kt`, `ui/ChatMessages.kt`, `data/SettingsStore.kt`.

### AP-36 ☑ Animationen · M (neu, 31.10.2026, auf Wunsch statt „nur mit dem Werkzeug“)
- **Korrigiert**: Schere schneidet das Blatt entlang der Linie (vorher schnitt sie in die Luft), Stempel trifft das Blatt
  auf einem Beistelltisch und zeigt das Siegel (vorher landete er unter dem Blatt, das mitsprang), Tacker steht auf dem
  Tisch (vorher hing er vor Loras Bauch). Grundlage: Hände können gezielt nach einem Punkt greifen (`reachL/reachR`).
- **Neu**: Brief, Kalender, Staffelei, Schloss, Präsentation, Tabelle, Übersetzen, Textmarker, Ordner; Stimmungen Nicken,
  Jubel mit Konfetti, Schulterzucken, Kopfkratzen, Zeigen, Fliegen, Ruhe; Augen folgen dem Zeiger; Startseite nach
  Tageszeit; im Sprachmodus nickt Lora beim Zuhören.
- **Vom Wunsch zum Ort**: Lora fliegt aus dem Panel zum Ziel (Chat-Eingabe, Seitenleiste, Einstellung) und landet mit
  einem Ring; der Ort leuchtet.
- Kontaktbogen aller 51 Aktionen: `LoraFigureTest` (`desktop/build/lora.png`), Bilder des Panels: `LoraPanelLookTest`.
- Betrifft: `ui/LoraFigure.kt`, `ui/LoraFlight.kt`, `ui/LoraUi.kt`, `ui/VoiceMode.kt`, `ui/Spotlight.kt`.

### AP-37 ☑ Lora leichter finden, Startseite mit Chips · S–M (neu, 31.10.2026, auf Wunsch)
- **Anlass**: Das Lora-Symbol oben im Chat war winzig (die ganze Figur in 30 × 21 dp), das Panel dadurch schwer zu
  entdecken; die Kacheln der Startseite wirkten unschön; das zweite Eingabefeld *Frag Lora …* in der Mitte war überflüssig;
  die schlafende Lora unten rechts (Android) war klein.
- **Nahaufnahme**: Loras Gesicht (Kopf mit Antenne und Visier) als eigene Zeichnung (`drawLoraFace`), mit Mimik aus
  derselben Steuerung, Blinzeln, Atmen, Blick zum Zeiger, winkender Hand oder „z“ im Schlaf. Überall, wo wenig Platz
  ist: Kopfleiste, schlafende Lora, Karten und Chips.
- **Kopfleiste**: Pille **„Frag Lora“** mit Loras Gesicht (36 dp) und Markenfarbe, aktiv hervorgehoben; auf schmalen
  Fenstern mit Chat-Titel nur das runde Gesicht. **Schlafende Lora**: runder Knopf (62 dp) mit dem schlafenden Gesicht,
  wacht unter der Maus auf und winkt.
- **Startseite**: Chips statt Kacheln (*Schreiben*, *Dokument erstellen*, *Dateien auswerten*, *Literatur finden*,
  *TU Vorlagen*); ein Chip öffnet an seiner Stelle konkrete Starts, die das Eingabefeld füllen oder den Ort öffnen.
  *Frag Lora …* auf der Startseite entfällt; ein Tipp auf Lora öffnet ihr Panel.
- Bilder: `LoraPanelLookTest` (`desktop/build/lora-entries.png`), `LoraUiTest` (`lora-home-*.png`, `lora-home-starts.png`).
- Betrifft: `ui/LoraFigure.kt`, `ui/LoraUi.kt`, `ui/LoraHelper.kt`, `ui/ChatScreenParts.kt`, `ui/ChatScreen.kt`,
  `ui/LoraChat.kt`, `ui/TemplateUi.kt`, `ui/ToolsSettings.kt`.

---

## Danach

### AP-12 ☑ Einfacher Sync zwischen PCs (W) · M (bisher W4-02, stark vereinfacht)
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
- **Ergebnis (31.10.2026)**: *Einstellungen › Synchronisation* mit Knöpfen für gefundene Cloud-Ordner (OneDrive,
  Google Drive für Desktop, Nextcloud, Dropbox), *Vorhandenen Ordner öffnen*, Liste der PCs, *Synchronisation beenden*.
  Je PC eine Notiz in `sync/` (offen seit, lebt noch, geschlossen, geänderte Dateien mit Größe); **vor dem Laden** wartet
  ein kleines Fenster, solange Lora anderswo offen ist oder dessen Änderungen fehlen, und geht von selbst weiter. Ein PC,
  der nur im Infobereich wartet, beendet sich, wenn ein anderer öffnet. Konfliktkopien (OneDrive, Google Drive,
  Nextcloud, Dropbox, Syncthing): Chats als „zweite Fassung“, Rest in `sync/konflikte/`. **Wichtiger Fund**: Bis 1.0.44
  lagen alle Einstellungen DPAPI-verschlüsselt im Datenordner – ein zweiter PC konnte sie nicht lesen und überschrieb
  sie. Jetzt liegen Schlüssel/Tokens/Konten/Umgebungen verschlüsselt auf dem PC (`schluessel.dat`), der Rest lesbar in
  `einstellungen.json` (einmalige Aufteilung beim Start). Fensterposition, Update-Stand und Protokoll bleiben auf dem PC.
  Tests: `SyncAndBrowserTest`. Betrifft: `data/SyncGuard.kt`, `desktop/SyncSession.kt`, `ui/SyncUi.kt`,
  `data/SecurePrefs.kt` (Windows), `desktop/Paths.kt`, `Main.kt`.

### AP-13 ☑ Mail und Kalender über das klassische Outlook (W) · L (bisher W7-01)
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
- **Ergebnis (01.11.2026)**: Werkzeug *Mail & Kalender* (nur Windows, Standard aus) mit Modus *Postfach*. **Brücke:
  COM über JNA** (`desktop/Com.kt`, `desktop/ComOutlook.kt`; Late Binding, eigener COM-Thread, alle Objekte werden
  freigegeben) – keine PowerShell, keine Zusatzbibliothek, keine Admin-Rechte (beantwortet Offene Frage 2). Agent:
  `outlook_list_folders`, `outlook_list_mails` (seit N Stunden, ungelesen, Suche; Kürzel M1 … statt langer IDs),
  `outlook_read_mail`, `outlook_move_mails` (fragt immer), `outlook_create_folder`, `outlook_save_draft` (Antwort über
  dem zitierten Verlauf), `outlook_list_events`, `outlook_create_event` (Fristen ganztägig mit Erinnerung; mit
  Teilnehmenden eine **vorbereitete** Einladung, die sich in Outlook öffnet), `outlook_create_task`,
  `outlook_find_free_times` (Frei/Gebucht, Mo–Fr 8–18 Uhr), `outlook_save_rule`, `outlook_apply_rules`. **Kein Löschen,
  kein Senden** – die Brücke hat dafür keine Funktionen (Test prüft das). Bereich *Mehr › Mail & Kalender*: Verbindung,
  Starts für Lora, Termine heute/morgen, Sortierregeln mit Vorschau vor dem Anwenden, Protokoll jeder Änderung
  (`outlook_log.json`). Lora sortiert Briefe, blättert, trägt Termine ein. Datumsfilter im Format der
  Windows-Regionseinstellung. Tests: `OutlookTest` (mit nachgebautem Outlook); Selbsttest meldet den Stand als Info.
  Grenzen: nur klassisches Outlook; je nach Virenscanner einmalige Rückfrage von Outlook beim Lesen von Adressen;
  echte Outlook-Regeln (serverseitig) legt die App nicht an, ihre Regeln wirken beim Anwenden.

### AP-14 ☑ Browser neben dem Chat (W) · L (bisher W9-08)
- Wie bei Google Antigravity: Links aus Chat, Literatur und Dokumenten öffnen sich **in Tabs neben dem Chatfenster**.
- Kleine Zusatzfunktionen: Fragen zur Seite (mit Belegen, AP-08), Seite zusammenfassen, in Literatur oder
  Wissenssammlung übernehmen, Paper-PDFs im PDF-Werkzeugkasten öffnen.
- Technik: keine eigene Engine. Windows: Chromium über JCEF, fest in der App (nachladbar erst ab AP-25); Android später
  einfache Fassung mit System-WebView. Anmeldung über die TU (Shibboleth) bleibt erhalten.
- Browser-Automatisierung (Agent füllt Formulare aus) → Ideen.
- **Fertig, wenn**: Link im Chat öffnet sich als Tab daneben, eine Frage zur Seite wird mit Beleg beantwortet.
- **Ergebnis (31.10.2026)**: Panel rechts neben der App (ziehbar; auf schmalen Fenstern darüber) mit Tabs, Zurück/Vor,
  Adresszeile; alle Web-Links der App (Chat, Literatur, Dokumente) gehen dorthin, Download-Seiten der App extern. Unter der
  Seite: *Frage zur Seite* (Seite als Anhang, neuer Chat im Quellenmodus), *Zusammenfassen*, *In die Literatur* (DOI →
  Crossref, sonst `citation_*`-Meta-Tags, sonst Webquelle), *In die Wissenssammlung*, *PDF im Werkzeugkasten* (mit den
  Cookies des Browsers, also auch hinter der TU-Anmeldung). Profil mit Cookies in `%LOCALAPPDATA%`, Anmeldung bleibt.
  **Entscheidung (Abweichung)**: Chromium (~170 MB) steckt **nicht fest in der App**, sondern wird beim ersten Link nach
  Rückfrage einmalig eingerichtet (`jcefmaven`) – sonst wäre jedes Update 170 MB größer. Das ist kein allgemeines
  Erweiterungssystem (das bleibt bei AP-25). Bekannte Grenze: App-Dialoge über dem Browser werden von der Chromium-Fläche
  teilweise verdeckt. Android später (WebView). Tests: `SyncAndBrowserTest`, mit echtem Chromium `BrowserLiveTest`
  (`ADEU_BROWSER_TEST=1`, `xvfb-run`). Betrifft: `desktop/WebEngine.kt`, `desktop/WebPage.kt`, `ui/BrowserPanel.kt`,
  `ui/AppRoot.kt` (Windows), `desktop/build.gradle.kts`, `release-local.sh`.

### AP-15 ☑ PDF-Werkzeugkasten Stufe 2 · L (bisher W8-02, Stufen 5–7)
- **OCR**: Scans durchsuchbar machen (lokal Tesseract, alternativ Vision-Modell je Datenschutzstufe).
- **Umwandeln**: Word/Excel/PowerPoint/Bilder/HTML ↔ PDF, PDF → Text/Markdown.
- **Unterschreiben**: gezeichnet oder als Bild einfügen.
- **KI**: „Frag das PDF“ mit Belegpflicht, Tabellen herausziehen, Formulare erklären.
- **Ergebnis (01.11.2026)**: Neue Bereiche *Unterschreiben*, *Umwandeln*, *KI* im Werkzeugkasten. **OCR**: statt
  Tesseract (müsste mitgeliefert werden) die **Texterkennung von Windows** (`Windows.Media.Ocr`, über PowerShell 5.1 mit
  `-EncodedCommand`; Wortpositionen) – nichts verlässt den PC; alternativ nach Rückfrage ein Bildmodell, das die
  Datenschutzstufe erlaubt (Android immer so). Unsichtbare Textebene je Wort, auch auf gedrehten Seiten. **Umwandeln**:
  Office → PDF mit installiertem Office über COM (exakt; ein offenes Office bleibt unberührt), sonst mit dem Leser der
  App; Bilder, HTML, Text → PDF; PDF → Word/Markdown/Text (`PdfMarkdown`: Überschriften nach Schriftgröße, Absätze über
  den üblichen Zeilenabstand, Listen auch ohne Aufzählungszeichen, Kopf-/Fußzeilen weg), Tabellen → Excel
  (`PdfTables`), Seiten → PNG-ZIP. **Unterschreiben**: zeichnen oder Bild, Größe, „Ort, Datum“, antippen; gespeichert in
  `signatures.json`, auch auf Android. **KI**: Frag das PDF (Quellenmodus mit Seitenbelegen), Zusammenfassen, Formular
  erklären, Tabellen mit KI oder auf dem Gerät. Tests: `PdfStage2Test`, Bilder `NewToolsLookTest`; Selbsttest prüft
  Textebene, Unterschrift und die Texterkennung von Windows. Grenzen: Unterschrift als Bild bzw. Vektor (keine digitale
  Signatur), OCR von Windows liest keine Handschrift.

### AP-16 ☑ Office-Ausbau · XL (bisher W8-01)
Die Editoren (Dokument, Präsentation, Tabelle, Markdown) gibt es schon; Ausbau im Alltagsumfang. Stufen:
0. **Dateien wie bei Google Drive, mit einem Lora-Ordner** (baut auf AP-18 auf; entschieden am 01.11.2026, ☑):
   - Der Tab *Dokumente* und *Mehr › Dateien* werden zum Tab **Dateien** (oben in der Seitenleiste; Strg+D,
     Befehlspalette und Lora-Landkarte ziehen mit).
   - **Ein Lora-Ordner für alles, wie ein Obsidian-Vault**: Dateien, Chats und Einstellungen liegen in einem frei
     wählbaren Ordner (Standard `Dokumente\Lora`; ist der Sync aus AP-12 an, schlägt die App einen Ordner im Cloud-Ordner
     vor). **Ordner wechseln**: Inhalt in einen anderen Ordner verschieben, dort in der App auswählen, weiterarbeiten.
     API- und Tresor-Schlüssel bleiben am Gerät (DPAPI/Keystore); auf einem neuen Gerät gibt man sie einmal neu ein.
     Die App-Daten liegen versteckt in einem Unterordner, sichtbar sind nur die Dateien.
   - **Echte Dateien** im Ordner (.docx, .xlsx, .pptx, .md, .pdf), die sich mit Word & Co. öffnen lassen; Kommentare,
     Versionen und die Verbindung zum Chat liegen daneben im versteckten Ordner `.lora/`. Braucht Stufe 1
     (Speichern ohne Verlust).
   - **Feste Ordner**: *Hochgeladen* (jeder Upload als Kopie, gleiche Dateien nur einmal; frühere PDFs und Bilder aus
     alten Chats werden übernommen, von Word, Excel und anderen gibt es nur noch den Text) und *Erstellt* (alles, was im
     Chat entstanden ist; flach mit Spalte und Filter „aus Chat …“/Projekt). Beide lassen sich nicht umbenennen oder
     löschen, ihre Dateien lassen sich aber verschieben – die Verbindung zum Chat bleibt.
   - **Bedienung wie Google Drive**: Ordner anlegen, umbenennen, verschieben (Ziehen und Ablegen, auch mehrere, und
     *Verschieben nach …*), Mehrfachauswahl, Pfadleiste, **Raster mit Vorschaubild** (erste Seite/Folie, im Cache) und
     **Liste**, Sortieren nach Name, Geändert, Typ, Größe. **Großes Plus** unten rechts: Ordner, Dokument, Präsentation,
     Tabelle, Markdown, Hochladen; neue Dateien landen im geöffneten Ordner. Seitenspalte (Android oben): *Zuletzt ·
     Markiert · Hochgeladen · Erstellt · Meine Ordner · Orte · Papierkorb*. Keine Leiste unten und kein „Freigegeben“
     wie bei Drive.
   - **Papierkorb** statt sofort löschen. Bisherige Ablageorte und Projektordner bleiben als **Orte** verknüpft (kein
     Umzug); ihre Dateien lassen sich jetzt auch umbenennen, verschieben und löschen (Papierkorb von Lora, Rückgängig).
   - **Tresor** bleibt draußen: seine Dokumente landen nie unverschlüsselt im Lora-Ordner.
   - **Suche**: die Volltextsuche aus AP-18 im Tab *Dateien*. Chats bekommen eine eigene Suche mit Filtern in der
     Chatliste (Zeitraum, Projekt, Modell, mit Anhängen, mit erstellten Dateien); Dateien zeigen „aus Chat …“, Chats ihre
     Dateien.
   - **Android**: Ordner einmal über den Systemdialog wählen (z. B. `Dokumente/Lora`); dort liegen nur die Dateien,
     Chats und Einstellungen bleiben intern (alles in einem Ordner kommt mit AP-28). Scannen mit der Kamera → Ideen.
1. **Kommentare und Änderungen nachverfolgen**, verlustfreies Öffnen und Speichern von .docx, .xlsx und .pptx.
2. **Seitenlayout** im Dokument: Seitenansicht mit Rändern, Abschnitte, Kopf-/Fußzeilen, Inhaltsverzeichnis, Formeln
   (LaTeX). Dazu **Rechtschreibprüfung** (Deutsch und Englisch, auf dem Gerät) und **Zitieren** aus der Literatur mit
   automatischem Literaturverzeichnis (bisher Ideen).
3. **Folien frei gestalten**: Elemente frei platzieren, Bilder und Formen, Masterfolien.
- Android: ansehen, kleine Änderungen, KI-Assistent.
- Code aus WordCraft und DeckCraft ggf. ansehen (*Anhang: Craft-Apps*).
- Auf Wunsch dazu: Bearbeiten wie in Google Docs mit **Lora rechts als Assistentin** und einem **Lora-Popup** über
  markiertem Text.
- **Ergebnis (01.11.2026)**: Stufe 0: Tab *Dateien* (statt *Dokumente* und *Mehr › Dateien*, oben in der Seitenleiste)
  mit Raster/Liste, Vorschaubildern, Sortierung, Pfadleiste, großem „+“, Ziehen auf Ordner (Windows), *Verschieben
  nach …*, Mehrfachauswahl, Sternen, Papierkorb (`data/LoraDrive.kt`, `ui/DriveScreen.kt`). Lora-Ordner: Windows
  `Paths.root` mit Daten in `.lora` (`desktop/LoraLayout.kt` zieht `%APPDATA%` bzw. einen gewählten Datenordner einmal
  um), Verschieben/Wechseln mit Neustart, Abfrage beim Start, wenn er fehlt; Android: Kopie in einen Ordner des
  Systemdialogs (`files/LoraMirror.kt`, in beide Richtungen nach Änderungszeit). Uploads werden beim Lesen beiseitegelegt
  und erst beim Senden übernommen (Tresor-Chats nie), Bilder beim Senden; alte PDFs/Bilder und `documents/` einmal
  übernommen; Chats finden verschobene Dateien über `dateien.json` (`resolve`). Editor-Dokumente sind echte Dateien
  (`docs/DocFiles.kt`), Rückweg aus Word alle 4 s. Stufe 1: Änderungen nachverfolgen (`trackBase`, `Tracking`), Word-
  Überarbeitungen und Kommentare hin und zurück (`docs/DocxExtras.kt`), Schreiben ins Original (`OfficeEdit`) für docx,
  pptx, xlsx. Stufe 2: Fußzeile, Seitenränder, Seitenansicht, Inhaltsverzeichnis, Formeln als OMML (`docs/LatexOmml.kt`),
  Rechtschreibung auf dem Gerät (`docs/LocalSpelling.kt`: Windows ISpellChecker, Android Rechtschreibdienst); Zitieren
  war schon da. Stufe 3: freie Elemente und Masterfolie (`SlideElement`, `SlideMaster`, `docs/SlideExtras.kt`). Dazu
  Lora-Panel rechts und Lora-Popup, Chatsuche mit Filtern (`data/ChatFilter.kt`), *Dateien dieses Chats*. Tests:
  `LoraFilesTest`. Grenzen: Papierkorb ist der von Lora (nicht der von Windows); offene Überarbeitungen werden aus dem
  Editor geschrieben (nicht ins Original); Seitenansicht ohne echten Seitenumbruch; Android-Kopie erkennt
  Umbenennungen dort als neue Datei.

### AP-17 ☑ Lernspiele-Baukasten · L (bisher W6-03, neu gedacht)
- Die KI baut **eigenständige Browserspiele aus HTML, JavaScript und CSS** (Quiz, Zuordnen, Memory, Escape-Room …) aus
  Skript, Folien oder Beschreibung.
- **Direkt in der App testen** (Vorschau im eingebetteten Browser), ändern per Chat („mach Level 2 schwerer“), Versionen.
- **Sammlung in der App** mit Vorschau und Suche.
- **Export als HTML-Datei/ZIP für OPAL** (und Moodle). SCORM/H5P → Ideen.
- Sicherheit: Spiele laufen abgeschottet (kein Zugriff auf App-Daten, keine Netzwerkanfragen ohne Freigabe).
- **Fertig, wenn**: „Mach ein Zuordnungsspiel zu Kapitel 3“ ergibt ein spielbares Spiel in der App, das sich als
  HTML-Datei in OPAL nutzen lässt.
- **Ergebnis (31.10.2026)**: Werkzeug *Lernspiele* (auf Wunsch **standardmäßig aus**, unter *Einstellungen › Werkzeuge*
  einschaltbar; Lora schlägt es bei „Quiz“, „Memory“ … vor). Erst eingeschaltet erscheinen *Mehr › Lernspiele* und der
  Modus *Lernspiel* (`ChatMode.tool`, `ChatModes.visible()`), und der Agent bekommt `create_learning_game`,
  `read_learning_game`, `update_learning_game`. Karte unter der Antwort mit *Spielen* (Windows: Tab im Browser neben dem
  Chat, sonst normaler Browser; Android: WebView im Vollbild ohne Netz) und *Exportieren* (ZIP mit index.html oder
  HTML-Datei). Sammlung mit Suche, Versionen (spielen, wiederherstellen), *Im Chat ändern*. Abschottung über eine
  Content-Security-Policy (keine Anfragen, keine Frames), externe Skripte/Stylesheets werden beim Speichern abgelehnt.
  Tests: `GamesAndAiNoteTest`. Rest: SCORM/H5P (Ideen), Klärung OPAL-Einbindung (Offene Fragen).

### AP-18 ☑ Dateiverwaltung · L (bisher W8-06, mit W4-01)
- Ordner, Schlagwörter, Volltextsuche über alle Formate, Versionsvergleich, „Zuletzt bearbeitet“.
- Für die Volltextsuche bei Bedarf eine lokale Index-Datenbank (SQLite mit Volltextindex; bisher W4-01) – nur, wenn die
  Suche ohne Index zu langsam ist. Sanfte Migration, Sicherung & Umzug bleiben kompatibel.
- **Ergebnis (01.11.2026)**: Bereich *Mehr › Dateien* über alle Quellen: Dokumente des Editors, Dateien in Ablageorten
  und Projektordnern, Wissenssammlung. *Zuletzt bearbeitet*, *Orte* mit Ordnern (Dokumente: Ordner der App wie
  „Lehre/WiSe 2026“; Dateien: echte Ordner), **Schlagwörter** für alles (`file_meta.json`, wird synchronisiert),
  **Volltextsuche** über PDF (mit Seite), Word, Excel, PowerPoint, OpenDocument, RTF, Mails, Text – Akzente und
  Groß-/Kleinschreibung egal, Textstelle hervorgehoben. **Entscheidung**: keine Datenbank – der Text jeder Datei wird
  einmal gelesen und im Cache (`volltext/`, je Datei mit Änderungszeit) gehalten, die Suche läuft darüber; SQLite bleibt
  für später, falls es zu langsam wird. **Vergleich**: zwei beliebige Versionen eines Dokuments, zwei Dateien (auch
  verschiedener Formate). Agent: `search_files` („Wo hatte ich …?“, nutzt nur schon gelesene Texte und liest den Rest im
  Hintergrund). Tests: `FileLibraryTest`. Grenzen: Dateien auf der Platte lassen sich hier nicht verschieben oder
  umbenennen (dafür der Agent in lokalen Ordnern); Bilder nur über den Namen.

### AP-19 ☑ KI-Nutzungsnachweis · M (bisher W5-07)
- Aus dem Agent-Protokoll eines Chats/Projekts: Modelle, Zweck, Zeitraum, betroffene Textteile, Werkzeuge.
- Formate: TUD-Vorgaben/Prüfungsordnung (für Studierende), Verlags-Policies, kurze Fußnote; als Dokument oder Text.
- **Ergebnis (31.10.2026)**: Auf Wunsch nur dann als **Pop-up-Vorschlag** über dem Eingabefeld, wenn ein Chat um eine
  Arbeit geht (Hausarbeit, Abschlussarbeit, Dissertation, Paper, Förderantrag, Abstract …) **und** die KI dafür Text
  geschrieben hat (Dokument oder längere Antwort auf „formuliere/überarbeite/kürze …“) – einmal je Chat, abschaltbar
  unter *Einstellungen › Lora*; immer im Chat-Menü. Nachweis auf dem Gerät aus dem Chat bzw. allen Chats des Projekts:
  Modelle, Zweck, Zeitraum, Funktionen, Entwürfe; betroffene Teile als Platzhalter. Formate: Prüfungsleistung (Tabelle),
  Verlag/Zeitschrift (EN + DE), kurze Fußnote; kopieren oder als Dokument. Kein offizieller TUD-Wortlaut – der Dialog
  erinnert an den Abgleich mit Prüfungsordnung bzw. Verlag. Tests: `GamesAndAiNoteTest`. Betrifft: `agent/AiUseNote.kt`,
  `ui/AiUseNoteUi.kt`, `ui/ChatScreen.kt`, `ui/ChatScreenParts.kt`, `ui/LoraHelper.kt`.

---

## Später

### AP-20 ☑ Stapel und Massenkontrolle · L (bisher W3-01, W6-01, Q-02)
- **Stapel**: Warteschlange für viele Dateien (Ordner, ZIP, Mehrfachauswahl) mit demselben Auftrag/Raster; Fortschritt
  mit Lora-Karte („23 von 80“, Animation: Akten abarbeiten), Pause/Fortsetzen, übersteht Neustart, läuft im Hintergrund,
  optional nachts über *Geplante Aufgaben*; Drosselung über `RateLimiter`, interaktive Anfragen haben Vorrang.
- **Massenkontrolle** (typisch 30–100 Abgaben je Semester): Ordner/ZIP (z. B. OPAL-Abgaben) + Raster (Tabelle oder TU
  Vorlage *Bewertungsraster*), Kalibrierung mit 2–3 von Hand bewerteten Arbeiten; je Arbeit Punkte je Kriterium,
  Begründung, Belegstellen; Ergebnistabelle mit Ausreißern, Feedback je Person (Serienbriefe vorhanden).
- **Die KI schlägt nur vor, ein Mensch entscheidet**; Protokoll der Entscheidung. Standard: pseudonymisiert, „Nur TU Dresden“.
- **Fertig, wenn**: 80 PDFs laufen ohne Eingriff durch, Abbruch und Fortsetzen verlieren nichts.
- **Entscheidungen (01.11.2026)**: (a) **Eigenes Werkzeug** *Stapel & Massenkontrolle* (anfangs aus) mit einem Bildschirm
  für beides; die Massenkontrolle ist ein Stapel mit Raster. (b) Je Datei **eine Anfrage ohne Werkzeuge** (gleicher
  Systemprompt für alle Dateien, gut für den Cache des Servers) statt eines Agent-Laufs – berechenbar, schnell, ohne
  Rückfragen. (c) „Nachts“ ist ein Schalter am Stapel (22–6 Uhr) statt einer Geplanten Aufgabe: Der Stapel kennt seinen
  Stand selbst. (d) Pseudonymisiert und „Nur TU Dresden“ gelten für den Stapel auch dann, wenn sie für Chats aus sind.
  (e) Die Animation nutzt die vorhandenen Aktionen Ordner, Stempel, Tacker statt einer neuen Zeichnung.
- **Ergebnis (01.11.2026)**: Dateien aus Mehrfachauswahl, ZIP (auch ZIP je Person, ohne `__MACOSX`), Ordner (Windows) und
  Nextcloud, Texte auf dem Gerät gelesen. Vorlagen für den Auftrag (Zusammenfassen, Exzerpt, Steckbrief, Übersetzen).
  **Runner** im Hintergrund: eine Datei nach der anderen, jedes Ergebnis sofort gespeichert (`batches/<id>/job.json`),
  Pause bricht die laufende Anfrage ab und legt die Datei zurück, nach dem Start geht es weiter; drei Versuche je Datei;
  wartet, solange ein Chat antwortet; lässt bei bekanntem Limit zwei Anfragen pro Minute frei (eigene Grenze unter
  *Chat & Werkzeuge › Zuverlässigkeit*); Android hält die App mit dem Vordergrunddienst wach; Benachrichtigung am Ende.
  **Massenkontrolle**: Raster als Tabelle (*Kriterium; Punkte; Beschreibung*, auch aus Markdown/Excel), Kalibrierung mit
  von Hand bewerteten Arbeiten im Systemprompt, Antwort als JSON (Punkte je Kriterium, Begründung, Belege mit Seite,
  Rückmeldung), Belegprüfung auf dem Gerät (✓/✗), Ausreißer (|z| ≥ 1,8, nicht bewertbar, große Abweichung zur
  Entscheidung), Entscheidung je Arbeit mit Protokoll, Exporte Excel, Word-Rückmeldungen je Person (ZIP), Protokoll.
  Agent: `create_batch`, `batch_status`, `read_batch_results`. Tests: `BatchTest` (80 Dateien ohne Eingriff, Pause und
  Neustart ohne Verlust, Wiederholungen, Vorrang der Chats, Raster, Ausreißer, ZIP). Rest: große Exzerpt-Stapel direkt an
  die Literatureinträge hängen; OPAL-Exportformat prüfen (Offene Fragen 4); Bewertungsraster aus Excel per Datei laden.
  Betrifft: `data/Batches.kt`, `agent/BatchRunner.kt`, `agent/BatchTools.kt`, `ui/BatchScreen.kt`, `agent/AgentService.kt`
  (Android), `tasks/Notifier.kt` (beide).

### AP-21 ☑ Antrags-Werkstatt · L (bisher W5-05)
- Ausschreibung einlesen (PDF/URL: DFG, BMBF, EU, SAB, TUD-intern) → Checkliste, Fristen in den Kalender.
- Gliederung nach Förderer-Vorgabe, Arbeitspakete mit Gantt, Budgettabelle, Zeichen-/Seitenwächter, DMP (Vorlage
  vorhanden), Ethik; Probelauf mit einer Gutachter-Rolle, Abgabe-Check. Gut geeignet für den Planungsmodus (AP-06).
- **Entscheidungen (01.11.2026)**: (a) Werkzeug *Antrags-Werkstatt* (anfangs aus) mit Modus *Antrag*; der Antrag ist ein
  eigener Eintrag (nicht ein Dokument), die Texte der Abschnitte liegen am Antrag, damit der Wächter sie zählt; Export als
  Word. (b) Förderer-Vorlagen sind nur ein Start (Gliederung, Checkliste, Kategorien, Pauschale: DFG 22 %, BMBF 20 %,
  EU 25 %); Seiten- und Zeichengrenzen kommen aus der Ausschreibung, die Lora liest – erfunden wird nichts. (c) Fristen
  gehen als Kalenderdatei (.ics, Erinnerung eine Woche vorher) an Outlook bzw. die Kalender-App statt direkt über COM.
- **Ergebnis (01.11.2026)**: Tabs *Übersicht* (Ausschreibung, Fristen, Checkliste, DMP und Ethik), *Gliederung* (Text je
  Abschnitt, Grenzen in Zeichen/Wörtern/Seiten, Balken, Zeichen je Seite einstellbar), *Arbeitspakete* (Gantt über die
  Laufzeit), *Budget* (je Projektjahr, Kategorien des Förderers, Pauschale), *Abgabe-Check* (Checkliste, Fristen, leere und
  zu lange Abschnitte, Seitenlimit, Arbeitspakete außerhalb der Laufzeit, Budget, DMP, Ethik, Probelauf) und die
  Probeläufe. Agent: `save_proposal`, `write_proposal_section`, `read_proposal`, `save_proposal_review`; *Probelauf:
  Gutachter:in* startet den Chat mit der Rolle. Tests: `ProposalTest`. Rest: Vorlagen der Förderer (Word) direkt
  befüllen; Personalmittelsätze der DFG hinterlegen; Gantt als Bild in den Word-Export.
  Betrifft: `data/Proposals.kt`, `agent/ProposalTools.kt`, `ui/ProposalScreen.kt`, `agent/ChatModes.kt`.

### AP-22 ☑ Nextcloud fest eingebaut · M (bisher Teil von W9-03)
- Dateien aus Nextcloud direkt öffnen/anhängen und Ergebnisse dort speichern; auf Android per WebDAV (Grundlage für AP-28).
- **Entscheidungen (01.11.2026)**: WebDAV auf **beiden** Plattformen (nicht nur Android), Anmeldung über den
  **Login Flow v2** im Browser (funktioniert mit TU-Login und zweitem Faktor, die App bekommt ein eigenes App-Passwort),
  manuell mit App-Passwort als Ausweg; vorbelegt `cloudstore.zih.tu-dresden.de`. Das App-Passwort liegt verschlüsselt und
  unter Windows nur auf dem PC (nicht im Sync).
- **Ergebnis (01.11.2026)**: *Einstellungen › Verbindungen › Nextcloud* (Anmelden, Testen, Ordner für Ergebnisse,
  Abmelden), Datei-Browser (Ordner, Pfadleiste, Mehrfachauswahl, neuer Ordner), *Aus Nextcloud* im „+“ des Chats,
  *Aus Nextcloud holen* in den Lora-Ordner, *In Nextcloud speichern* an Dokumentkarten, Dateien, Stapel-Ergebnissen und
  Anträgen (gleichnamige Dateien bleiben, die neue bekommt „ (2)“), Agent: `nextcloud_list`, `nextcloud_read`,
  `nextcloud_save` (nicht im Tresor, nicht „Nur auf diesem Gerät“). Tests: `ConnectionsTest` gegen einen lokalen
  WebDAV-Testserver. Rest: Sync für Android darauf aufbauen (AP-28); Freigabelinks erzeugen.
  Betrifft: `net/WebDav.kt`, `data/Nextcloud.kt`, `agent/NextcloudTools.kt`, `ui/NextcloudUi.kt`, `files/FileText.kt` (beide).

### AP-23 ☑ MCP-Client · L (bisher W9-01, W9-02)
- Fern-Server (Streamable HTTP mit OAuth/Token) auf beiden Plattformen, lokale Server (stdio) unter Windows.
- *Einstellungen › Verbindungen*: Server hinzufügen, Werkzeuge ansehen, je Werkzeug erlauben/fragen/sperren; fremde
  Werkzeuge laufen über die *Freigaben* und halten die Datenschutzstufe ein.
- **Entscheidungen (01.11.2026)**: (a) Eigener schlanker Client (JSON-RPC über OkHttp, Protokoll 2025-06-18) statt einer
  SDK-Bibliothek. (b) Standard je Werkzeug **Fragen** (= Freigaben, Risiko „nach außen“); *Erlauben* ohne Rückfrage,
  *Sperren* versteckt es. (c) **Datenschutzstufe**: nie im Tresor, nie „Nur auf diesem Gerät“; „Nur TU Dresden“ nur für
  Server, die als *an der TU Dresden oder auf diesem PC* markiert sind. (d) OAuth mit PKCE und dynamischer Registrierung,
  Rückkehr aus dem Browser über 127.0.0.1 (Windows und Android); feste Tokens auch in eigenem Header.
- **Ergebnis (01.11.2026)**: Streamable HTTP (JSON- und SSE-Antworten, Sitzungs-ID, Protokoll-Header), stdio unter Windows
  (`npx`/`uvx` über `cmd /c`, Umgebungsvariablen, Fehlermeldung des Programms), Werkzeugliste mit Seiten, Ergebnisse als
  Text (Bilder und Ressourcen benannt, Fehler als „FEHLER:“), Werkzeuge im Chat als `mcp_<server>_<werkzeug>`, abgelaufene
  Anmeldungen werden erneuert. *Einstellungen › Verbindungen › MCP-Server*: hinzufügen, bearbeiten, ein/aus, Verbinden,
  Anmelden im Browser, Werkzeuge mit Erlauben/Fragen/Sperren (einzeln und für alle). Tests: `ConnectionsTest` (Sitzung,
  SSE, 401, OAuth mit PKCE gegen einen lokalen Testserver, Regeln, Datenschutzstufe). Rest: Ressourcen und Prompts der
  Server; Rückfragen des Servers (Elicitation); lokale Server unter Android gibt es nicht.
  Betrifft: `net/Mcp.kt`, `net/McpOAuth.kt`, `net/McpStdio.kt` (beide), `data/McpServers.kt`, `agent/McpTools.kt`,
  `ui/ConnectionsSettings.kt`, `data/SecurePrefs.kt` (Windows).

### AP-24 ☑ Kreativbereich → „Erstellen“ › Gestalten (D) · XL (bisher W8-03)
Alltagsumfang nach dem Vorbild Canva, Schwerpunkt Corporate Design. Nur Windows (kein Android).
1. Leinwand mit Ebenen, Ausrichten, Rückgängig, mehrseitige Designs; Brand-Kit (TUD-Farben, Schriften, Logos – vorhanden).
2. Vorlagen Uni-Kontext: Tagungsposter, Flyer, Aushang, Einladung, Urkunde/Teilnahmebescheinigung, Social Media,
   OPAL-Kursbanner, Titelbild für Lehrvideos.
3. Text, Formen, Icons, QR-Codes, Bilder (zuschneiden, FLUX-Bilder direkt auf die Leinwand), KI: „Poster aus diesem Paper“.
4. Export PNG, PDF (Druck), PowerPoint.
- Lora-Animation: Pinsel, Farbpalette. Code aus DesignCraft/VectorCraft/PhotoCraft prüfen (*Anhang: Craft-Apps*).
- **Entscheidungen (01.11.2026)**: (a) **Kein eigener Bereich und kein Werkzeug**: TU Vorlagen und Gestalten sind ein Ort,
  **„Erstellen“** (statt „TU Vorlagen“; erwogen: Gestalten, Werkstatt, Studio – „Erstellen“ passt neben „Dateien“ und
  trägt Brief wie Poster, wie *Erstellen* in Microsoft 365). Oben „Was möchtest du erstellen?“ für Lora, darunter
  Gestalten, Meine Designs, TU Vorlagen. (b) Die Teilnahmebescheinigung bleibt TU Vorlage (nach der TUD-Vorlage gebaut),
  keine zweite als Design; „Einladung“ heißt „Einladungskarte“. (c) Brand-Kit aus dem, was vom **CD-Paket 2025** im Repo
  liegt (Logos, Farben des Office-Themes „TUD_2025“, Noto Sans); Vorlagen, Paletten und die Icon-Schrift des CD-Portals
  liegen hinter dem ZIH-Login und sind nicht eingebaut. (d) Noto Sans als TTF (OFL) in der Windows-App, damit Editor, PDF
  und Videotitel dieselbe Schrift zeichnen (Skia liest das WOFF2 der App nicht). (e) Craft-Code nicht übernommen – alles
  selbst geschrieben.
- **Ergebnis (01.11.2026)**: *Erstellen* mit Lora-Feld (Wunsch, auch mit Datei; Design-Wünsche starten den Modus
  *Gestalten*), **Gestalten** mit acht Vorlagen (Tagungsposter A1, Flyer A5, Aushang A4, Einladungskarte A5 quer, Social
  Media Beitrag und Story, OPAL-Kursbanner, Titelbild für Lehrvideos) je *Selbst gestalten* oder *Lora machen lassen*,
  *Poster aus einem Paper*, *Leeres Design* in 17 Formaten, **Meine Designs**. **Editor**: Seiten, Ebenen, Mehrfachauswahl
  mit Rahmen, Verschieben mit Einrasten (Seite und Elemente), Griffe für Größe und Drehung, Ausrichten/Verteilen,
  Sperren, Rückgängig/Wiederholen, Tastenkürzel, Zwischenablage (auch Bilder/Text aus anderen Programmen), Format ändern.
  **Elemente**: Text (wächst mit oder passt sich an), sieben Formen, 37 Symbole, Bilder mit Zuschnitt/Eckenradius/
  Alternativtext, Bild vom Bildmodell, QR-Codes (`io.nayuki:qrcodegen`, MIT), TUD-Logo und eigene Logos. **Export**: PNG
  (Bildschirm in Pixeln, Druck 150/300 dpi), PDF vektoriell mit eingebetteter Schrift (auf Wunsch 3 mm Beschnitt und
  Schnittmarken), PowerPoint mit echten Formen und Textfeldern (A0 wird auf die PowerPoint-Höchstgröße verkleinert).
  **KI**: `create_design`, `update_design`, `read_design` in jedem Chat unter Windows, Modus *Gestalten*, Karte unter der
  Antwort, offene Designs laden Änderungen aus dem Chat nach; Lora malt dabei (PAINT). Tests: `CreativeTest`,
  `CreativeScreenTest` (Bilder in `desktop/build/creative-look`). Rest: Text direkt auf der Leinwand tippen, Verläufe,
  Bildfilter, Vorlagen und Icon-Schrift aus dem CD-Portal (wenn jemand sie hochlädt), PowerPoint-Vorlagen importieren.
  Betrifft: `data/Designs.kt`, `data/DesignTemplates.kt`, `agent/DesignTools.kt`, `ui/CreateUi.kt`, `ui/TemplatesScreen.kt`,
  Windows: `creative/*`, `ui/DesignEditor.kt`, `ui/CreateDesigns.kt`, `ui/DesignPlatform.kt`, `resources/fonts/`.
- **Nachtrag CD-Paket (01.11.2026)**: Das vollständige CD-Paket 2025 (Logos, Shape mit Regeln, Icon-Themensets,
  Präsentationsvorlage, digitales Poster, Urkunden, Office-Themes) liegt jetzt vor und ist eingearbeitet: 23 **TUD Farben**,
  **Noto Sans Medium** für Überschriften (statt fett), alle **offiziellen Logos** als Vektor (horizontal/vertikal, de/en,
  „TUD“, Bildmarke; blau/weiß/schwarz), die **TUD-Shape** als Element (45°-Schritte, spiegeln, Foto in der Shape) und
  rund 530 **TUD-Icons** (aufbereitet mit `desktop/tools/tud_cd_assets.py`). Alle neun Vorlagen neu nach den Regeln
  (weiß mit Logo oben links oder Brillantblau mit weißem Logo, Shape als Bildmotiv, TUD-Icons), neu **Digitales Poster
  16:9**. Die TU Vorlage *Präsentation* (PowerPoint, Handout, Folien-Editor) folgt der offiziellen Präsentationsvorlage
  (Titelfolie, Abschnitt mit Shape, schwarze Titel 32 pt Medium, Fußzeile mit Kurzlogo „Titel • Vortragende • Seite N“),
  Urkunden setzen Name und Veranstaltung in Medium, Videotitel nutzen Medium und die Shape. Hinweis: Logos, Icons und
  Shape stecken damit in der Windows-App, die über das öffentliche Updater-Repo verteilt wird.

### AP-25 ☑ Videoeditor und nachladbare Erweiterungen (D) · XL (bisher W8-07, Teil von W8-00)
Alltagsumfang nach dem Vorbild DaVinci Resolve, nur Desktop.
1. **Nachladbare Erweiterungen** einführen: der Videoeditor (und danach ggf. Browser-Engine, OCR-Daten) wird beim ersten
   Öffnen geladen („Videoeditor einrichten · ca. 200 MB“), liegt unter `%LOCALAPPDATA%\ADEU LLM\tools\…`, kommt über
   denselben Updater, lässt sich entfernen.
2. **Schnitt**: Zeitleiste mit Video- und Tonspuren, schneiden, trimmen, Übergänge, Titel und Bauchbinden im TUD-Design.
3. **Ton und Untertitel**: Pegel, Rauschminderung, Untertitel aus der vorhandenen Transkription einbrennen oder als SRT/VTT.
4. **Export** MP4 mit Vorgaben für OPAL und YouTube; KI: Rohschnitt aus dem Transkript (Füllwörter, Pausen).
- Technik: ffmpeg (in der Windows-Version enthalten), Proxy-Dateien für flüssigen Schnitt; Lizenz des ffmpeg-Builds prüfen.
- **Entscheidungen (01.11.2026)**: Da ffmpeg schon in der App steckt, ist das **Videoeditor-Paket** klein (ca. 40 MB
  Download statt 200 MB): ffprobe aus demselben ffmpeg-Build und das Sprachmodell RNNoise („somnolent-hogwash“, laut
  Projekt nicht urheberrechtlich geschützt) für die Rauschminderung. Der Editor bietet es beim ersten Öffnen an und
  arbeitet ohne Paket mit dem ffmpeg der App weiter. Titel und Untertitel zeichnet die App selbst (Renderer von
  *Erstellen*) – ffmpeg braucht weder libass noch Schriften. Lizenz: der ffmpeg-Build ist GPL v3, seine LICENSE liegt bei
  App und Paket.
- **Ergebnis (01.11.2026)**: **Erweiterungen**: `version.json` listet sie unter `extensions`, die App richtet sie unter
  `%LOCALAPPDATA%\ADEU LLM\tools\<id>` ein (Prüfsumme, Entpacken ohne Pfade nach außen, Tausch beim nächsten Start, wenn
  Dateien belegt sind), aktualisiert sie mit der Update-Prüfung und entfernt sie unter *Einstellungen › Werkzeuge ›
  Erweiterungen*; dort steht auch die Browser-Engine (entfernbar). `publish-update.sh` baut das Paket mit
  `extension-videoeditor.sh` bei jedem Release. **Videoeditor** (Werkzeug, anfangs aus): Projekte, Zeitleiste mit
  Video-, Titel-, Ton- und Untertitelspur, Teilen (S), Trimmen an den Rändern, Verschieben, Übergänge Überblenden/über
  Schwarz, Lautstärke/stumm, Rückgängig; Vorschau aus Proxys (360 Zeilen) mit Ton als Uhr, Titel und Untertitel darüber.
  Titel, Bauchbinde, Einblendung, Abspann im TUD-Design; Musik/Sprecherton; Rauschminderung (RNNoise oder FFT), Lautheit
  −16 LUFS, Pegel; Untertitel aus der Transkription (folgen dem Schnitt, *Jetzt transkribieren*) oder SRT/VTT, einbrennen
  oder als Datei; **Rohschnitt** (Pausen und Füllwörter) und **Mit Lora kürzen**; Export MP4 für OPAL (720p), YouTube
  (1080p) oder Projektqualität. Lora mit der Schere (SCISSORS) auf der leeren Projektseite. Tests: `VideoTest` (echter Export mit ffmpeg),
  `VideoScreenTest`, `ExtensionsTest`. Rest: Vorschau zeigt Übergänge als Schnitt und spielt die Musikspur nicht mit;
  Bild-in-Bild und zweite Videospur, Farbkorrektur, Wellenform der Tonspur, Hardware-Encoder; Browser-Engine und
  OCR-Daten über denselben Weg ausliefern.
  Betrifft: Windows `video/*`, `ui/VideoScreen.kt`, `desktop/Extensions.kt`, `ui/ExtensionsUi.kt`, gemeinsam
  `update/UpdateInfo.kt`, `update/Updates.kt`, `.github/scripts/extension-videoeditor.sh`, `publish-update.sh`.

### AP-26 ☑ PDF-Werkzeugkasten Stufe 3 · M (bisher W8-02, Stufen 4, 8, 9)
- Echtes Schwärzen, Text/Bilder ändern, verkleinern, PDF/A, Passwort, Metadaten; zwei PDFs vergleichen, Barrierefreiheit
  nach PDF/UA.
- **Entscheidungen (01.11.2026)**: (a) **Schwärzen** zeichnet die betroffenen Seiten als Bild neu (wie die einfache
  Schwärzung von pdf24) – sicher, weil der Text darunter weg ist; andere Seiten bleiben Text. (b) **Text ändern** überdeckt
  und schreibt neu, statt den Inhaltsstrom umzuschreiben; der alte Text bleibt (Hinweis im Werkzeug). (c) PDF/A als
  **PDF/A-2b-Vorbereitung** mit klarer Liste, was fehlt; strenge Prüfung bleibt bei veraPDF. (d) Stufe 3 nur unter Windows
  (wie *Formular*); *Vergleichen* ist gemeinsamer Code und läuft auch auf Android.
- **Ergebnis (01.11.2026)**: Bereich *Schwärzen & mehr*: Schwärzen mit Suche und Vorschlägen aus der Pseudonymisierung
  (Namen, Kontaktdaten, eigene Begriffe), optional ohne Metadaten; Text ersetzen; Bilder mit Vorschau ersetzen oder
  entfernen; Verkleinern (Mittel/Stark); PDF/A-2b (XMP, sRGB, Sprache, ohne JavaScript/Anhänge/Verschlüsselung);
  Passwort AES-256 mit Rechten und Entfernen; Metadaten bearbeiten oder entfernen; PDF/UA-Prüfung (Tags, Sprache, Titel,
  Schriften, Text, Alternativtexte, Felder, Lesezeichen, Tab-Reihenfolge) mit *Einfache Mängel beheben*; Vergleichen
  Wort für Wort mit Word-Export. Tests: `PdfStage3Test`. Rest: echtes Bearbeiten im Inhaltsstrom, Schwärzen mit der Maus
  auf der Seite, Tags erzeugen, digitale Signatur.
  Betrifft: `docs/PdfStage3.kt` (beide), `docs/PdfCompare.kt`, `ui/PdfStage3Ui.kt`, `ui/PdfToolScreen.kt`.

### AP-27 ☑ Mail über den Server · L (bisher W7-01, Server-Weg)
- Wenn jemand das neue Outlook nutzt (oder für Android): Exchange (EWS bzw. Microsoft Graph mit Freigabe der IT) oder
  IMAP/SMTP; Funktionen wie AP-13. Vorher klären, wie die TU Exchange betreibt (Offene Fragen).
- **Entscheidungen (01.11.2026)**: (a) **IMAP zuerst** – geht ohne Freigabe der IT; EWS/Graph bleibt offen (Offene Fragen 3).
  (b) **Kein SMTP**: Antworten landen als Entwurf (APPEND in „Entwürfe“), gesendet wird nie. (c) Verschieben nur mit MOVE
  oder UIDPLUS (UID EXPUNGE der eigenen Mails) – nie EXPUNGE, das andere gelöschte Mails endgültig entfernen würde.
  (d) Eigener kleiner IMAP-Client statt Jakarta Mail (läuft unverändert auf Android und Windows). (e) Dieselbe
  Schnittstelle wie das klassische Outlook (`OutlookBridge`), damit Werkzeuge, Regeln, Protokoll und *Mehr › Mail &
  Kalender* gleich bleiben.
- **Ergebnis (01.11.2026)**: *Einstellungen › Verbindungen › Mail über den Server* (Server, Port 993/143, Benutzer,
  Passwort verschlüsselt, eigene Adresse, unter Windows „statt des klassischen Outlook“, Testen); Werkzeug *Mail &
  Kalender* jetzt auch auf Android. Ordner (Modified UTF-7, Zähler), Mails mit Vorschau, eine Mail ganz (MIME über
  `MailText`), Verschieben, Ordner anlegen, Entwürfe mit Antwort-Kopfzeilen und zitiertem Verlauf. Kalender, Aufgaben und
  Frei/Gebucht melden verständlich, dass es sie auf diesem Weg nicht gibt. Tests: `MailServerTest` gegen einen
  IMAP-Testserver (auch: nie EXPUNGE/STORE ohne UIDPLUS, falsches Passwort). Rest: Exchange über EWS/Graph mit Kalender,
  OAuth-Anmeldung für Exchange Online, Zugangsdaten der TU im Formular vorbelegen (sobald geklärt).
  Betrifft: `net/Imap.kt`, `data/MailServer.kt`, `data/Outlook.kt`, `ui/ConnectionsSettings.kt`, `ui/MailScreen.kt`.

### AP-28 ☑ Sync für Android · M (bisher W4-02, Stufe d)
- Den Cloud-Ordner aus AP-12 auf Android über Nextcloud/WebDAV oder den Systemdialog nutzen. Nice to have.
- **Entscheidungen (01.11.2026)**: (a) Über **Nextcloud/WebDAV** (AP-22) statt des Systemdialogs: Android hat keinen
  Cloud-Client für den App-Ordner, der Systemdialog bleibt für die Kopie des Lora-Ordners (AP-16). (b) Das Handy ist ein
  „Gerät“ wie ein PC: Notiz in `.lora/sync/`, wartet, solange ein PC den Ordner offen hat oder dessen Änderungen
  unterwegs sind. (c) **Nur, was die App im Betrieb neu lesen kann**: Chats (je Datei), Projekte, Gedächtnis,
  Kurztexte, eigene TU Vorlagen, Kontakte und die sichtbaren Dateien – Literatur, Wissen, Transkripte und Einstellungen
  bleiben je Gerät (sonst könnte ein Speicher im Speicher alte Daten über neue schreiben). (d) Konflikte wie AP-12
  (zweite Fassung bzw. `sync/konflikte`), Schutz vor Massenlöschung.
- **Ergebnis (01.11.2026)**: *Einstellungen › Verbindungen › Mit dem PC synchronisieren* (Android): Lora-Ordner in der
  Nextcloud wählen (prüft `.lora`), *Jetzt synchronisieren*, Stand; automatisch beim Öffnen, alle fünf Minuten und beim
  Wechsel in den Hintergrund (Notiz dann „geschlossen“). Zweiwege-Abgleich über ETags mit Zustandsdatei, Löschungen in
  beide Richtungen, Chats und Listen werden danach neu gelesen. Tests: `PhoneSyncTest` (holen, hochladen, Konflikt,
  Löschen, wartet auf den PC, Massenlöschung). Rest: weitere Speicher (Literatur, Wissen) mit Neuladen; Sync im
  Hintergrund ohne geöffnete App.
  Betrifft: `data/PhoneSync.kt`, `net/WebDav.kt`, `data/Stores.kt` (`reload`), `data/ChatRepository.kt` (`rescan`),
  `ui/NextcloudUi.kt`.

### AP-29 ☑ Englisch vervollständigen · M (bisher W9-07)
- Alle Texte in `I18n` überführen, englische Fassung vollständig (Einstellungen, Editoren, TU Vorlagen, Fehlermeldungen,
  Lora). Keine weiteren Sprachen.
- **Entscheidungen (01.11.2026)**: (a) Deutsch bleibt die Quellsprache im Code; `tr("…")` mit Platzhaltern `{0}`,
  `trLater("…")` für Listen/Aufzählungen, ein eigenes `Text()` im Paket `ui` übersetzt Angezeigtes von selbst.
  (b) Die englischen Texte stehen in einer Datei (`i18n/en.tsv`), nicht im Code. (c) Prompts an die Modelle, Wörter zum
  Erkennen deutscher Eingaben, Dokumentvorlagen (Inhalt) und Protokolle bleiben deutsch; Antworten folgen der Sprache.
- **Ergebnis (01.11.2026)**: rund 4 300 Texte auf Englisch – Einstellungen, alle Editoren, TU Vorlagen und Personas,
  Modelle, Modi, Datenschutzstufen, Designs, Literatur, Transkription, Fehlermeldungen von Server, Updates und Anhängen,
  Loras Antworten; Pfade wie „Einstellungen › Darstellung“ Teil für Teil; Loras Suche versteht englische Wörter.
  Tests: `I18nCoverageTest` (jeder markierte Text hat eine englische Zeile, Platzhalter passen, `$`-Vorlagen in `tr()`
  verboten). Rest: Inhalte der Dokumentvorlagen (Protokoll, Seminarplan …) bleiben deutsch.
  Betrifft: `ui/I18n.kt`, `ui/TextI18n.kt`, `resources/i18n/en.tsv`, fast alle Bildschirme.

### AP-30 ☑ Barrierefreiheit der App · M (bisher W2-11)
- Screenreader (TalkBack/Narrator): Beschriftungen aller Knöpfe, sinnvolle Reihenfolge; große Schrift ohne
  abgeschnittene Texte; komplette Bedienung per Tastatur unter Windows.
- **Entscheidungen (01.11.2026)**: (a) Windows: Compose Desktop spricht über die **Java Access Bridge** – die App schaltet
  sie für sich selbst ein (ohne `jabswitch`, ohne Admin-Rechte), wenn die mitgelieferte Java-Laufzeit sie enthält.
  Damit lesen **NVDA** und JAWS; Narrator kennt diese Schnittstelle nicht. (b) Gemeinsame Bausteine statt jeder Bildschirm
  einzeln: Abschnitte und Gruppen sind Überschriften, Schalterzeilen ein Schalter, Umschalter eine Auswahl.
  (c) Fokus deutlich sichtbar über den Ripple-Zustand (26 % statt 10 %).
- **Ergebnis (01.11.2026)**: Beschriftungen für alle Knöpfe mit Symbol und Schalter ohne eigene Zeile, Farbfelder mit
  Namen und Auswahlzustand, Überschriften in den Einstellungen, große Schrift bricht um (Umschalter, feste Höhen
  entfernt), sichtbarer Tastaturfokus, Access Bridge. Tests: `AccessibilityTest` (Schalterzeile, Überschriften,
  Umschalter bei 200 % Schrift ohne Abschneiden, jeder Symbolknopf beschriftet). Rest: Test mit NVDA auf einem Dienst-PC.
  Betrifft: `ui/Design.kt`, `ui/SettingsScreen.kt`, `ui/Theme.kt`, `desktop/Accessibility.kt` u. a.

**Ganz am Ende** – erst, wenn alles andere fertig ist:

### AP-31 ☑ macOS und Linux · L (bisher W9-06)
- Compose Desktop baut bereits plattformunabhängig; zu tun: Pakete (DMG signiert/notarisiert, AppImage/DEB),
  PDF-Rendering ohne Edge, Pfade, Autostart, Updater, Tastenkürzel (⌘), nachladbare Erweiterungen je Plattform.
- **Entscheidungen (01.11.2026)**: (a) macOS nur Apple Silicon (arm64), Linux x64. (b) Pakete: DMG für die Erstinstallation,
  tar.gz für automatische Updates (behält Rechte und Links), Linux tar.gz und .deb. Signieren/Notarisieren erst, wenn ein
  Apple-Entwicklerkonto da ist (Workflow nutzt es dann über Secrets). (c) ffmpeg vom System statt mitgeliefert
  (Homebrew/apt). (d) Schlüssel im Keychain bzw. Secret Service. (e) Erweiterungen je System (`videoeditor-macos` …),
  das Windows-Paket wird dort nicht angeboten.
- **Ergebnis (01.11.2026)**: Ordner nach Systemstandard, Keychain/Secret Service, Autostart (LaunchAgent, XDG),
  Updater für .app-Bündel und App-Ordner, ⌘-Kürzel und angepasste Texte (⌘, Finder, Menüleiste), Öffnen/Zeigen mit
  `open`/`xdg-open`, Ordnerwahl im Finder-Dialog, PDF über Chrome/Edge des Systems oder den eingebauten Renderer mit den
  Mac-Schriften, llama.cpp für Apple Silicon/Linux, sherpa-onnx für macOS, Build & Release mit macOS- und Linux-Job und
  Selbsttest. Tests: `UpdatesTest` (tar.gz mit Rechten und Links). Rest: Notarisierung (Apple-Konto), Intel-Macs,
  Videoeditor-Paket für macOS/Linux, Outlook-Ersatz für den Kalender.
  Betrifft: `desktop/Os.kt`, `desktop/Paths.kt`, `desktop/Secrets.kt`, `desktop/Autostart.kt`, `desktop/Desktop.kt`,
  `update/*`, `local/LlamaHost.kt`, `docs/HtmlPdf.kt`, `desktop/build.gradle.kts`, `.github/workflows/android.yml`.

### AP-32 ☐ Website · XL (bisher W9-09)
- Möglichst viele Funktionen im Browser: Chat, TU Vorlagen, Lora, Literatur, Office-Editoren, PDF-Werkzeuge.
- Nicht im Web: lokale Modelle, Agent in lokalen Ordnern, Videoeditor, eigener Browser.
- Technik (zu prüfen): Compose Multiplatform für Web (Wasm); API-Schlüssel nicht im Klartext im Browser; CORS der Uni-API.
