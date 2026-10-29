# Arbeitsplan ADEU LLM

Stand: 28.10.2026 · Lebendes Dokument: Status hier pflegen, erledigte Pakete abhaken und in der README beschreiben.

**Legende**

- **Priorität**: P1 = als Nächstes, P2 = danach, P3 = später
- **Größe**: S (bis 1 Tag) · M (einige Tage) · L (1–2 Wochen) · XL (mehrere Wochen, in Meilensteine teilen)
- **Status**: ☐ offen · ◐ in Arbeit · ☑ erledigt
- **Plattform**: A = Android, W = Windows, beide wenn nicht angegeben

---

## Getroffene Entscheidungen

| Thema | Entscheidung |
| --- | --- |
| Web-Version | **Wird nicht gebaut.** |
| Betreuungs-Cockpit | Vorerst nicht. |
| Sprachen | Englisch vervollständigen (niedrige Priorität), keine weiteren Sprachen. |
| Seitenleiste | Grundfunktionen oben, fortgeschrittene Bereiche (Dokumente usw.) unter ausklappbarem **„Mehr“**. *Vorlagen* heißt künftig **„TU Vorlagen“**. Keine Einfach-/Expertenansicht. |
| Startseite | Aufgeräumter, nach Tätigkeiten statt nach Rollen (kein „Lehren/Betreuen“ als Oberbegriff). |
| Modus-Konzept | Einheitlich: Chip über dem Eingabefeld, eine Zeile „was passiert hier“, Datenhinweis, ✕ zum Verlassen. |
| Sprachmodus | Wird beschleunigt (Vorlesen ab dem ersten Satz, kurze TTS-Abschnitte, schnelles Modell ohne Denkphase). |
| Lora | Eigene Kommunikationsschicht mit **GLM-5.3 Flash**, überall in der App, navigiert zu Optionen, übergibt an Chat-Modelle. Beim ersten Start fragen, jederzeit abschaltbar. |
| E-Mail/Kalender | Das **neue Outlook** ist Standard → keine COM-Anbindung, sondern direkt an den Exchange-Server (siehe W7-01). |
| PDF-Werkzeugkasten | Funktionsumfang wie **pdf24 + Xodo**. |
| Kreativbereich | Ziel **so nah wie möglich an Canva**, Schwerpunkt Corporate Design und Uni-Kontext. |
| MCP/Plugins | Vollständig: App als MCP-Client (Fern- und lokale Server) **und** als MCP-Server. |
| Aus der Ideenliste übernommen | Profilpflege (ORCID, FIS, Publikationsliste, Lebenslauf), Assistent für Bewerbungsverfahren, MCP-/Plugin-Schnittstelle. |

---

## Übersicht der Wellen

| Welle | Inhalt | Ziel |
| --- | --- | --- |
| **W1** | Schnelle Verbesserungen | Alltagsärger beseitigen |
| **W2** | Bedienung, Navigation, Lora als Helfer | App wird verständlich, Lora führt durch |
| **W3** | Agent-Bausteine (Stapel, Mehr-Agenten, Belegpflicht, Modi-Rahmen) | Grundlage für alle neuen Modi |
| **W4** | Daten: Index-Datenbank, Synchronisierung, Professur-Datenbank, Sicherheit | Mehrere Geräte und Teamarbeit |
| **W5** | Modi Forschung | Systematic Review, Exzerpte, Quellenmodus, Panel, Anträge, Forschungsdaten, Profil, KI-Nachweis |
| **W6** | Modi Lehre & Prüfung | Massenkontrolle, Klausuranalyse, Lernbaukasten, Live-Untertitel |
| **W7** | Kommunikation & Verwaltung | Mail/Kalender, Podcast, Bewerbungsverfahren |
| **W8** | Dokumente-Tab | PDF-Werkzeugkasten, Kreativbereich, Diagramme, Formate, Dateiverwaltung |
| **W9** | Plattformen & Erweiterbarkeit | MCP, macOS/Linux, Englisch |
| **Q** | Querschnitt | Qualität, Datenschutz, Recht, Tests |

Reihenfolge innerhalb einer Welle = vorgeschlagene Bearbeitungsreihenfolge. W3 vor W5–W7, W4-01 vor W4-02/03.

---

## W1 – Schnelle Verbesserungen

### W1-01 ☑ Screenshots aus der Zwischenablage einfügen (A) · P1 · S
- Bilder aus der Zwischenablage im Chat-Eingabefeld annehmen: langes Drücken › *Einfügen* und die Zwischenablage-Leiste
  der Tastatur (Gboard) über `contentReceiver` des Textfelds; zusätzlich *Bild aus Zwischenablage* im „+“-Menü.
- Gleiche Behandlung wie angehängte Bilder (Verkleinern, Vision-Hinweis bei Modellen ohne Bildverständnis).
- **Fertig, wenn**: Screenshot machen → im Chat einfügen → Bild hängt als Anhang am Entwurf.
- Betrifft: `ui/ChatComposer.kt`, `ui/ChatScreen.kt` (Android), `ui/ImageEncoding.kt`.
- Umgesetzt: *Aus der Zwischenablage* im „+“-Menü (beide Plattformen) und Strg+V mit Tastatur. Offen: Einfügen über
  Gboard-Leiste/langes Drücken (braucht Umstellung des Eingabefelds auf `TextFieldState` für `contentReceiver`).

### W1-02 ☑ Absätze im Chat auf Windows (W) · P1 · S
- Umschalt+Enter fügt derzeit keinen Zeilenumbruch ein (die Taste wird an das Textfeld durchgereicht, Compose Desktop
  setzt bei gedrückter Umschalttaste aber keinen Umbruch). Umbruch an der Cursorposition selbst einfügen.
- Gleiche Prüfung für alle Eingabefelder mit „Enter sendet“ (`AskAiPopup.kt`, `DocsAssistant.kt`).
- **Fertig, wenn**: Umschalt+Enter erzeugt in Chat, *KI fragen* und Dokument-Assistent eine neue Zeile, Enter sendet.
- Betrifft: `ui/ChatComposer.kt`, `ui/AskAiPopup.kt`, `ui/DocsAssistant.kt`.

### W1-03 ☑ Zoom mit Strg + / Strg − / Strg 0 (W) · P1 · S
- Oberfläche in Stufen von 80 % bis 200 % skalieren (Dichte/Schrift über `LocalDensity`), Strg+0 setzt zurück,
  Strg+Mausrad ebenfalls. Stufe wird gespeichert; kurze Anzeige „110 %“.
- Auch unter *Einstellungen › Darstellung* als Regler; in der Tastenkürzel-Übersicht (Strg+/) ergänzen.
- **Fertig, wenn**: Zoomen wirkt auf Chat, Seitenleiste, Einstellungen und Editoren, bleibt nach Neustart erhalten.
- Betrifft: `desktop/…/Main.kt`, `ui/AppearanceSettings.kt`.

### W1-04 ☐ Projektordner auf Android · P1 · M
- Beim Anlegen/Bearbeiten eines Projekts Ordner über den Systemdialog wählen (dauerhafte Berechtigung), auch Nextcloud-
  oder Drive-Ordner, soweit der Dateianbieter sie anbietet.
- Alles, was im Projekt entsteht, landet automatisch in `Ausgabe` (wie unter Windows).
- Nach der Ordnerwahl fragt die App (auf **beiden** Plattformen): *„Soll die KI die Dateien in diesem Ordner als Kontext
  nutzen?“* – **Ja, alles lesen** / **Nur Ausgabe** / **Später entscheiden**. Chip im Chat: *Projektordner · liest mit*.
- **Fertig, wenn**: Projekt mit Ordner auf Android anlegen → Dokument erzeugen → Datei liegt in `Ausgabe`; Frage
  erscheint einmal und ist unter *Projekt bearbeiten* änderbar.
- Betrifft: `agent/WorkFolder.kt`, `ui/FolderSettings.kt`, `ui/FolderAccess.kt`, `ui/KnowledgeUi.kt`.

### W1-05 ☐ Sprachmodus beschleunigen · P1 · M
Heute läuft alles nacheinander: 1,3 s Pause abwarten → ganze Aufnahme erkennen → **komplette** Antwort abwarten →
Vorlesen mit einem ersten TTS-Abschnitt von bis zu 3500 Zeichen (`audio/Speaker.kt`). Maßnahmen nach Wirkung:
1. **Vorlesen ab dem ersten fertigen Satz**, während die Antwort noch gestreamt wird (Satz-Warteschlange im Speaker).
2. Erster TTS-Abschnitt = ein Satz, folgende Abschnitte ca. 300–600 Zeichen, parallel vorbereitet.
3. Sprachmodus-Profil: schnelles Modell (GLM-5.3 Flash), Denkphase aus, Anweisung „kurz, gesprochen, ohne Markdown“,
   Werkzeuge nur bei Bedarf (einstellbar: *Sprachmodus › Modell*).
4. Satzende-Erkennung schneller: Pause ca. 0,8 s statt 1,3 s, weiterhin anpassend (`SpeechEndDetector`).
5. Kurzer Ton bzw. Lora-Geste beim Übergang „verstanden → denkt“, damit die Wartezeit sichtbar ist.
6. Messpunkte im Agent-Protokoll: Erkennung, erstes Token, erster Ton.
- **Fertig, wenn**: Zeit von Sprechende bis erstem Ton im Agent-Protokoll gemessen und gegenüber heute deutlich kürzer;
  Unterbrechen und Pause funktionieren weiter.
- Betrifft: `ui/VoiceMode.kt`, `audio/Speaker.kt` (beide Plattformen), `ui/ChatViewModel.kt`.

### W1-06 ☐ Fehlerbericht mit einem Klick und „Was ist neu“ · P2 · S
- *Hilfe › Problem melden*: Beschreibung + anonymisiertes Fehlerprotokoll (ohne Inhalte, Schlüssel, Namen) als E-Mail-
  Entwurf oder GitHub-Issue.
- Nach jedem Update einmal eine kurze Seite „Was ist neu“ (aus den Release-Notizen).
- Betrifft: `ErrorLog.kt`, `update/`, neue `ui/HelpScreen.kt`.

---

## W2 – Bedienung, Navigation, Lora als Helfer

### W2-01 ☐ Seitenleiste neu ordnen · P1 · S
- Oben die Grundfunktionen: *Neuer Chat*, *TU Vorlagen* (umbenannt), *Literatur*, *Transkription*.
- Darunter ausklappbar **„Mehr“**: *Dokumente*, *Bilder erzeugen*, *Geplante Aufgaben* und alle neuen Bereiche (PDF,
  Kreativ …). Auf-/Zugeklappt wird gespeichert; Bereiche lassen sich per Rechtsklick/langem Drücken nach oben holen.
- Umbenennung *Vorlagen → TU Vorlagen* überall (Seitenleiste, Befehlspalette, Startseite, README, Lora-Texte).
- **Fertig, wenn**: Seitenleiste zeigt oben max. 5 Einträge, „Mehr“ enthält den Rest, Tastenkürzel funktionieren weiter.
- Betrifft: `ui/AppDrawer.kt`, `ui/CommandPalette.kt`, `ui/TemplatesScreen.kt`, `ui/I18n.kt`.

### W2-02 ☐ Startseite aufräumen · P1 · M
- Oben eine Zeile Kacheln nach **Tätigkeiten**, Arbeitsvorschlag: *Fragen & schreiben* · *TU Vorlagen* · *Dokument
  erstellen* · *Dateien auswerten* · *Literatur finden* · *Mail & Termine* (Wortlaut vor Umsetzung abstimmen).
- Darunter *Zuletzt* (Chats, Dokumente, Vorlagen gemischt) und *Favoriten*.
- Lora mit Sprechblase bleibt, aber ruhiger (eine Idee statt Liste).
- **Fertig, wenn**: Häufigste Aufgaben mit einem Klick erreichbar; Usability-Test (Q-03) bestätigt Verständlichkeit.
- Betrifft: `ui/LoraUi.kt`, `ui/ChatScreen.kt`, `ui/ChatScreenParts.kt`.

### W2-03 ☐ Einheitlicher Modus-Rahmen · P1 · M
- Gemeinsames Modell `ChatMode` (Name, Symbol, Erklärzeile, Beispielanfrage, Datenhinweis, Werkzeuge, Prompt-Zusatz,
  Standardmodell). Bestehende Modi (Literatursuche, Sprachmodus …) darauf umstellen.
- Anzeige: Chip über dem Eingabefeld mit ✕, darunter eine Zeile „Was passiert hier · Daten: …“, im leeren Chat ein
  Beispiel zum Antippen.
- Modi über `/`-Befehl, Befehlspalette, Startseite und Lora aufrufbar.
- **Fertig, wenn**: Jeder Modus sieht gleich aus und lässt sich mit ✕ verlassen; neue Modi brauchen nur eine Definition.
- Betrifft: neu `agent/ChatModes.kt`, `ui/ChatComposer.kt`, `ui/ChatViewModel.kt`.

### W2-04 ☐ Lora als Kommunikationsschicht (Kern) · P1 · L
- Eigene, schlanke Agent-Schleife **LoraAgent** mit **GLM-5.3 Flash**, Denkphase aus, kurzer System-Prompt.
- **Landkarte der App** (`AppMap`): alle Bereiche, Einstellungen, Modi, TU Vorlagen mit ID, Pfad, Beschreibung,
  Synonymen und Plattform. Wird aus vorhandenen Registern erzeugt (Einstellungssuche, Vorlagen, Modi), nicht doppelt gepflegt.
- Werkzeuge für Lora:
  - `navigate(ziel)` – öffnet Bereich/Einstellung und **hebt die Option mit Leuchtrahmen hervor** (Spotlight).
  - `open_mode(modus, felder)` – öffnet einen Modus/eine Vorlage vorausgefüllt.
  - `change_setting(id, wert)` – nur nach Bestätigung.
  - `handoff_chat(modell, anfrage, zusammenfassung)` – übergibt an ein Chat-Modell (Auswahl über `ModelRouter`).
  - `explain(thema)` – beantwortet „Wie geht …?“ aus der eingebauten Hilfe (README-Abschnitte).
- **Lokal zuerst**: einfache Wünsche („Wo stelle ich die Stimme um?“) löst die lokale Suche sofort ohne Anfrage; das
  Modell nur bei echten Gesprächen. Lora fragt höchstens einmal nach, wenn unklar.
- Datenschutz: in Projekten „Nur auf diesem Gerät“ nur lokale Suche bzw. lokales Modell; Lora-Anfragen erscheinen im
  Agent-Protokoll und im Verbrauch.
- Rate-Limit: Lora läuft nur auf Abruf, nie im Hintergrund mitlesend.
- **Fertig, wenn**: „Ich will 80 Hausarbeiten bewerten“ → Lora öffnet die Stapelbewertung; „Dunkles Design“ → Lora
  springt zu *Darstellung* und hebt den Schalter hervor; „Erklär mir Exzerpte“ → Übergabe an einen Chat.
- Betrifft: neu `agent/LoraAgent.kt`, `data/AppMap.kt`, `ui/LoraHelper.kt`; `ui/AppRoot.kt`, `ui/NavRules.kt`,
  `ui/SettingsOverview.kt`, `agent/ModelRouter.kt`.

### W2-05 ☐ Lora überall: Einstiegspunkte und Popup · P1 · M
- **Schlafend unten rechts** in den Einstellungen (und weiteren passenden Bereichen); Klick weckt Lora, Popup fragt
  „Was möchtest du machen oder einstellen?“ (Text oder Sprache).
- Weitere Einstiege: Startseite, Befehlspalette (Strg+K → „Lora fragen“), langes Drücken auf Lora im Chat.
- Grundregel: **Lora reagiert, Lora unterbricht nicht** – höchstens ein einmaliger Hinweis je Bereich, keine ungefragten Popups.
- Kontextvorschläge: z. B. Ordner mit vielen PDFs angehängt → kleine Lora-Zeile „Stück für Stück auswerten?“.
- **Fertig, wenn**: Lora ist auf Start, Chat, Einstellungen, Dokumente, Literatur, Transkription erreichbar und stört nicht.

### W2-06 ☐ Lora beim ersten Start und Einstellungen · P1 · S
- Erster Start: „Möchtest du Lora als Helfer in der App? – **Ja, überall** / **Nur im Chat** / **Nein**“.
- *Einstellungen › Lora*: Modus (überall/nur Chat/aus), Modell (Standard GLM-5.3 Flash), Animationen reduziert,
  Hinweise erlaubt. Bestehender Schalter *Lora ausblenden* geht darin auf.

### W2-07 ☐ Neue Lora-Animationen je Einsatzzweck · P2 · M
- Einstellungen: Schraubenschlüssel, Zahnräder justieren · PDF: Schere, Hefter, Stempel · Kreativ: Pinsel, Farbpalette,
  Staffelei · Mail: Briefe sortieren · Kalender: Seiten umblättern · Stapel: Akten abarbeiten mit Fortschrittszähler ·
  Panel: Richterhammer/Brille · Sync: Pakete hin und her · Schlafen mit „Zzz“ und Aufwachen.
- „Weniger Bewegung“ des Systems respektieren.
- Betrifft: `ui/LoraFigure.kt`.

### W2-08 ☐ Erster Start nach Rolle · P2 · S
- Auswahl Professur / wiss. Mitarbeit / Sekretariat / Hilfskraft / Sonstige → passende Favoriten, Startkacheln und
  TU Vorlagen; jederzeit änderbar. Kombinierbar mit W2-06 in einem kurzen Einrichtungsablauf.

### W2-09 ☐ Fehlermeldungen in Klartext · P2 · S
- Alle Fehlertexte prüfen: Was ist passiert, was kann ich tun, ein Knopf zur Lösung (z. B. „API-Schlüssel prüfen“ springt
  in die Einstellung). Lora kann Fehler erklären.
- Betrifft: `net/NetErrors.kt`, `ui/Notices.kt`.

### W2-10 ☐ Gleicher Funktionsumfang Android/Windows · P2 · S (laufend)
- Tabelle aller Funktionen mit Plattform pflegen (Anhang dieses Plans), Lücken als eigene Pakete anlegen.

### W2-11 ☐ Barrierefreiheit der App · P2 · M
- Screenreader (TalkBack/Narrator): Beschriftungen aller Knöpfe, sinnvolle Reihenfolge; große Schrift ohne
  abgeschnittene Texte; komplette Bedienung per Tastatur unter Windows.

---

## W3 – Agent-Bausteine

### W3-01 ☐ Stapel-Engine · P1 · L
- Warteschlange für viele Dateien (Ordner, ZIP, Mehrfachauswahl): Datei für Datei mit demselben Auftrag/Raster.
- Fortschritt mit Lora-Karte („23 von 80“), Pause/Fortsetzen, übersteht App-Neustart (Zustand auf Platte), läuft im
  Hintergrund (Vordergrunddienst bzw. Infobereich), optional nachts über *Geplante Aufgaben*.
- Drosselung über den vorhandenen `RateLimiter`, Wiederholung bei Fehlern, fehlgeschlagene Dateien gesondert.
- Ergebnis: Tabelle (eine Zeile je Datei, Spalten je Kriterium) im Tabellen-Editor + Einzeldokumente; Export Excel/CSV.
- Datenschutzstufe und Pseudonymisierung gelten je Datei.
- **Fertig, wenn**: 80 PDFs laufen ohne Eingriff durch, Abbruch und Fortsetzen verlieren nichts.
- Betrifft: neu `agent/BatchRunner.kt`, `ui/BatchScreen.kt`; `agent/AgentService.kt`, `net/RateLimiter.kt`.

### W3-02 ☐ Mehr-Agenten-Engine (Elephant-Goldfish) · P1 · L
- Rollen mit eigenem Prompt, Modell und Werkzeugen; jede Rolle startet **ohne Chatverlauf** und bekommt nur, was ihr
  übergeben wird (Text, Auftrag, Raster).
- Ablauf: unabhängige Gutachten → Zusammenführung (Konsens, Dissens, offene Fragen) → optional Entscheidung go/no-go.
- Verschiedene Modelle je Rolle möglich (weniger gemeinsame blinde Flecken).
- Anzeige: je Rolle eine einklappbare Karte, Lora moderiert.
- **Fertig, wenn**: Panel aus 3–6 Rollen liefert getrennte Gutachten und ein Meta-Gutachten; Rate-Limit wird eingehalten.
- Betrifft: neu `agent/Panel.kt`, `ui/PanelUi.kt`; `agent/AgentRunner.kt`.

### W3-03 ☐ Belegpflicht · P1 · M
- Antworten im Belegmodus enthalten zu jeder Aussage eine Fundstelle (Datei + Seite, DOI, URL, Transkriptzeit).
- Prüfschritt nach der Antwort: Belege gegen Quelle abgleichen (Zitat vorhanden?), unbelegte Sätze markieren oder
  entfernen; Belegprotokoll am Ende.
- Klick auf einen Beleg öffnet die Stelle (PDF-Seite, Wissenssammlung, Webseite).
- Betrifft: neu `agent/Evidence.kt`; `ui/ChatMessages.kt`, `data/Knowledge.kt`.

---

## W4 – Daten

### W4-01 ☐ Lokale Index-Datenbank · P1 · L
- SQLite (Android: Room/SQLite, Windows: SQLite-JDBC) für Chats, Projekte, Literatur, Dokument-Metadaten, Adressbuch,
  Wissenssammlung; Volltextindex (FTS).
- Sanfte Migration aus den bisherigen JSON-Dateien; Sicherung & Umzug bleiben kompatibel.
- Voraussetzung für W4-02, W4-03, große Stapel und sehr große Chats (virtuelles Scrollen).
- **Fertig, wenn**: Bestandsdaten migriert, Suche schneller, Sicherung/Import funktioniert in beide Richtungen.

### W4-02 ☐ Synchronisierung zwischen Geräten · P1 · XL
**Einstellungsbereich *Einstellungen › Synchronisation*** (eigener, klar benannter Bereich, ähnlich einem Obsidian-Tresor):
- „**Synchronisationsordner wählen**“: ein Ordner (Nextcloud, OneDrive, Google Drive für Desktop, Netzlaufwerk; auf
  Android über Systemdialog oder direkt per WebDAV/Nextcloud-Zugang). Aus diesem Ordner liest und schreibt die App alles.
- Kurze Erklärung im Bereich: *Was wird synchronisiert* (Auswahl: Chats, Projekte, Dokumente, Literatur, TU Vorlagen,
  Gedächtnis, Einstellungen; API-Schlüssel nie), *wie* (verschlüsselt), *Status* (zuletzt abgeglichen, Konflikte).
- „Vorhandenen Synchronisationsordner öffnen“ auf einem zweiten Gerät → Daten erscheinen.
- Technik: Änderungsprotokoll pro Objekt (statt ganzer Dateien), Zusammenführen bei Konflikten (letzte Änderung je
  Feld, bei Texten Zusammenführen bzw. beide Fassungen behalten), Ende-zu-Ende-Verschlüsselung mit Passphrase.
- Speicherorte (Windows) und Datenordner-Verlegung gehen darin auf.
- Meilensteine: (a) Ordner + Chats/Projekte · (b) Dokumente/Literatur · (c) Gedächtnis/Einstellungen · (d) Android per WebDAV.
- **Fertig, wenn**: Chat am PC beginnen, am Handy weiterführen, ohne ZIP-Umzug; Konflikte gehen nicht verloren.

### W4-03 ☐ Gemeinsame Datenbank der Professur · P1 · XL
**Einstellungsbereich *Einstellungen › Professur (gemeinsam)*** (eigener Bereich, klar getrennt von der persönlichen
Synchronisation):
- Erklärtext: „Hier verbinden Sie die App mit dem gemeinsamen Ordner Ihrer Professur. Projekte, TU Vorlagen,
  Wissenssammlung und Adressbuch liegen dort für alle Mitglieder.“
- „**Gemeinsamen Ordner verbinden**“ (Netzlaufwerk, Nextcloud-Gruppenordner) bzw. „**Neue Professur einrichten**“.
- **Rollen**: Leitung (alles inkl. Richtlinien), Mitarbeitende (lesen/schreiben), Hilfskräfte (nur freigegebene
  Projekte), Gäste (nur lesen). Rollen als signierte Datei im Ordner; Anzeige „Sie sind: Mitarbeitende“.
- **Vorlagen verteilen**: Versionen, „Pflichtvorlage“, Hinweis „3 neue TU Vorlagen der Professur“.
- Gemeinsame Projekte mit Sperre beim Bearbeiten, Wissenssammlung und Adressbuch für alle.
- **Zentrale Richtlinien** der Leitung: erlaubte Modelle, Mindest-Datenschutzstufe, erlaubte Werkzeuge/MCP-Server.
- Audit-Log (wer hat was wann geändert) und Löschfristen für personenbezogene Daten.
- **Fertig, wenn**: Zwei Personen sehen dieselben Projekte und Vorlagen, Rollen greifen, Richtlinien gelten auf allen Geräten.

### W4-04 ☐ App-Sperre und verschlüsselte lokale Daten · P1 · M
- PIN/Biometrie (Android), Windows Hello oder PIN (Windows); Sperre nach Inaktivität.
- Verschlüsselung der lokalen Daten (Android Keystore, Windows DPAPI – vorhanden für Schlüssel, ausweiten).
- Pflicht, sobald Mails, Bewerbungen oder Noten in der App liegen (W6, W7).

---

## W5 – Modi Forschung

### W5-01 ☐ Exzerpt-Agent · P1 · M
- Standardfelder: Bibliografie, Fragestellung, Theorie, Methode, Stichprobe, zentrale Befunde, wörtliche Zitate mit Seite,
  Begriffe, eigene Bewertung, Anschlussfragen.
- Schemata je Texttyp (empirisch, theoretisch, Review, Buchkapitel); eigene Schemata als TU Vorlage.
- Mit Belegpflicht (W3-03), im Stapel (W3-01), Ergebnis am Literatureintrag + als Dokument/Tabelle.
- Baut auf der vorhandenen Vorlage *Forschung › Exzerpt* auf.

### W5-02 ☐ Quellenmodus · P1 · S
- Modus nach W2-03 mit Belegpflicht (W3-03) für jeden Chat; Quellen: Wissenssammlung, Projektordner, Anhänge, Web,
  Literatur. Belegprotokoll exportierbar.

### W5-03 ☐ Gutachter-Panel · P1 · M
- Auf W3-02: Rollen Methodik, Fach, Statistik, Ethik/Datenschutz, Lesbarkeit, Advocatus Diaboli (Auswahl und eigene Rollen).
- Einsatz: Paper, Anträge, Abschlussarbeiten, eigene Texte; Ergebnis als Gutachten-Dokument im TUD-Design.
- Variante „Design-Review“: eine Rolle erklärt den Text zurück („Habe ich das richtig verstanden?“).

### W5-04 ☐ Systematic-Review-Modus (PRISMA) · P2 · XL
- Protokoll (PICO/SPIDER, Ein-/Ausschlusskriterien), Suchstrings je Datenbank aus der vorhandenen Literatursuche,
  Suchprotokoll.
- Dubletten entfernen, Screening Titel/Abstract und Volltext mit zwei Ratern (Mensch + KI), Übereinstimmung (κ vorhanden).
- Extraktionsbogen (Stapel), Qualitätsbewertung (RoB 2, CASP, MMAT), PRISMA-2020-Flussdiagramm, Export (Tabelle, Word, RIS).
- Meilensteine: Protokoll+Suche · Screening · Extraktion+Bewertung · PRISMA-Bericht.

### W5-05 ☐ Antrags-Werkstatt · P2 · L
- Ausschreibung einlesen (PDF/URL: DFG, BMBF, EU, SAB, TUD-intern) → Checkliste der Anforderungen, Fristen in Kalender.
- Gliederung nach Förderer-Vorgabe, Arbeitspakete mit Gantt, Budgettabelle (Personal nach Entgeltgruppen, Sachmittel),
  Zeichen-/Seitenwächter, DMP (Vorlage vorhanden), Ethik.
- Probelauf mit dem Panel (W5-03) in der Rolle der Gutachter:innen des Förderers, Abgabe-Check.

### W5-06 ☐ Forschungsdaten veröffentlichen (OPARA/Zenodo) · P2 · L
- Metadaten-Assistent nach DataCite (Titel, Personen mit ORCID, Beschreibung, Schlagwörter, Lizenz, Förderung, Bezug).
- README und Codebook erzeugen, Anonymisierung prüfen, offene Formate vorschlagen (CSV statt XLSX …).
- Zenodo: Upload per API (zuerst Sandbox); OPARA: Paket fertig vorbereiten bzw. Upload, falls Schnittstelle verfügbar (klären).

### W5-07 ☐ KI-Nutzungsnachweis · P2 · M
- Aus dem Agent-Protokoll eines Chats/Projekts: Modelle, Zweck, Zeitraum, betroffene Textteile, Werkzeuge.
- Formate: TUD-Vorgaben/Prüfungsordnung (für Studierende), Verlags-Policies, kurze Fußnote; als Dokument oder Text.

### W5-08 ☐ Profilpflege: ORCID, FIS, Publikationsliste, Lebenslauf · P2 · M
- Publikationen aus ORCID (öffentliche API) und OpenAlex abgleichen, Dubletten zusammenführen, fehlende Werke vorschlagen.
- Publikationsliste im gewählten Zitierstil (Word/PDF/HTML für die Webseite), Lebenslauf im TUD-Design aus Profil + Gedächtnis.
- Export für ORCID-Import (BibTeX) und FIS der TUD (Format/Schnittstelle klären); optional geplante Aufgabe „monatlich prüfen“.

---

## W6 – Modi Lehre & Prüfung

### W6-01 ☐ Massenkontrolle mit Bewertungsraster · P1 · L
- Auf W3-01: Ordner/ZIP (z. B. OPAL-Abgaben) + Raster (Tabelle oder TU Vorlage *Bewertungsraster*).
- Kalibrierung: 2–3 von Hand bewertete Arbeiten als Maßstab.
- Je Arbeit: Punkte je Kriterium, Begründung, **Belegstellen**; Ergebnis-Tabelle mit Ausreißern; Feedback je Person
  (Serienbriefe vorhanden).
- **Die KI schlägt nur vor, ein Mensch entscheidet** (KI-Verordnung: Bewertung = Hochrisiko); Protokoll der Entscheidung.
  Standard: pseudonymisiert, Datenschutzstufe „Nur TU Dresden“.

### W6-02 ☐ Klausuren nachträglich analysieren · P2 · M
- Import OPAL/ONYX-Ergebnisse und Moodle-Export (CSV/XLSX).
- Itemanalyse: Schwierigkeit, Trennschärfe, Distraktoren, Cronbachs α, Notenverteilung, auffällige Items.
- Bericht im TUD-Design; Überarbeitungsvorschläge direkt zurück in QTI (`docs/QtiWriter.kt`).

### W6-03 ☐ Baukasten für interaktive Lernmaterialien und Lernspiele · P2 · XL
- Bausteine: Quiz, Lückentext, Zuordnung, Drag & Drop, Memory, Kreuzworträtsel, Escape-Room, verzweigte Fallvignetten.
- Editor mit Vorschau; Lora erzeugt Entwürfe aus Skript/Folien.
- Export: H5P, SCORM-/HTML-Paket für OPAL, Moodle-XML/GIFT (vorhanden).

### W6-04 ☐ Live-Untertitel und Übersetzung im Hörsaal · P2 · L
- Spracherkennung über den Uni-Server in kurzen Abschnitten (Ziel 2–5 s Verzögerung), Glossar der Fachbegriffe.
- Übersetzung in eine oder mehrere Sprachen, Anzeige im Vollbild / auf zweitem Bildschirm (Beamer).
- Danach: Transkript, Skript, Untertitel-Datei (SRT/VTT vorhanden).
- Mitlesen auf Studierenden-Geräten nur, wenn ohne eigenen Server machbar (lokaler Webserver im selben Netz) – klären.

---

## W7 – Kommunikation & Verwaltung

### W7-01 ☐ E-Mail- und Kalender-Assistent · P1 · XL
- **Anbindung**: Da das neue Outlook keine lokale Schnittstelle hat, spricht die App direkt mit dem Exchange-Server der TU
  (EWS: Mail, Kalender, Frei/Gebucht) mit dem TU-Login; Rückfall IMAP/SMTP für Mail. Entwürfe landen im
  Entwurfsordner und erscheinen damit automatisch im (neuen) Outlook. Android: zusätzlich Kalender des Systems.
- Funktionen: Postfach zusammenfassen, sortieren/kategorisieren, Antworten im eigenen Stil entwerfen (Gedächtnis),
  Fristen erkennen → Kalender/Aufgaben, Termine finden mit Frei/Gebucht der Kolleg:innen, Einladungen vorbereiten.
- **Senden nur nach Freigabe**; Pseudonymisierung vor Modellanfragen; App-Sperre (W4-04) Voraussetzung.
- Klären: Betreibt die TU Exchange selbst (EWS verfügbar) oder Exchange Online (dort wird EWS abgeschaltet → Graph mit
  Freigabe der IT nötig)?

### W7-02 ☐ Audio-Zusammenfassung als Podcast · P2 · M
- Dialog zweier Stimmen (TTS des Uni-Servers) aus Dokument, Literaturliste, Transkript oder Chat; Länge wählbar.
- MP3 mit Kapitelmarken, Skript als Text; Einsatz z. B. als Vorlesungs-Rückblick für Studierende.

### W7-03 ☐ Assistent für Bewerbungsverfahren · P2 · L
- Ausschreibung nach AGG (geschlechtsneutral, keine Altersangaben …), TV-L/WissZeitVG-Hinweise, Gleichstellungsformeln.
- Kriterienraster **vor** Sichtung festlegen (Muss/Soll), Bewerbungen über die Stapel-Engine sichten.
- **Nur Vorschläge** mit Begründung und Belegstelle, Entscheidung durch Menschen; Dokumentation für Personalrat und
  Gleichstellung; optional pseudonymisierte Sichtung.
- Löschfristen einstellbar (Standard 6 Monate nach Abschluss), automatische Erinnerung/Löschung; nur mit App-Sperre.
- KI-Verordnung: Personalauswahl ist Hochrisiko → Einsatz mit Datenschutz und Personalrat abstimmen (Hinweis in der App).

---

## W8 – Dokumente-Tab

Neue Gliederung des Bereichs: **Office · PDF · Kreativ · Diagramme · Dateien**

### W8-01 ☐ Office (Ausbau des Vorhandenen) · P2 · M
- Zitieren beim Schreiben (Zitat-Platzhalter aus *Literatur*, Verzeichnis aktualisiert sich), Formeleditor (LaTeX),
  Diktat im Editor, gemeinsames Bearbeiten über die Professur-Datenbank (Sperre/Zusammenführen).

### W8-02 ☐ PDF-Werkzeugkasten (Ziel: Umfang wie pdf24 + Xodo) · P1 · XL
Meilensteine:
1. **Betrachter wie Xodo**: Tabs für mehrere PDFs, Miniaturen, Gliederung/Lesezeichen, Suche, Nachtmodus,
   Lesemodus (Textfluss), geteilte Ansicht.
2. **Seiten**: zusammenführen, teilen, extrahieren, löschen, drehen, sortieren (Ziehen in der Miniaturansicht),
   zuschneiden, leere Seiten einfügen, Seitenzahlen, Kopf-/Fußzeilen, Wasserzeichen, Overlay.
3. **Anmerkungen**: markieren, unterstreichen, durchstreichen, Freihand/Stift, Notizen, Formen, Stempel, Textfelder,
   Messen; Kommentarliste exportieren.
4. **Bearbeiten**: Text und Bilder ändern, **echtes Schwärzen** (Inhalt wird entfernt, nicht nur überdeckt),
   Bilder extrahieren, Graustufen.
5. **Formulare & Signatur**: ausfüllen (vorhanden), Felder erstellen, Felder automatisch erkennen, Formular „flach“ machen,
   unterschreiben (gezeichnet/Bild), digitale Signatur prüfen.
6. **OCR**: Scans durchsuchbar machen (lokal Tesseract, alternativ Vision-Modell je Datenschutzstufe).
7. **Umwandeln**: Word/Excel/PowerPoint/Bilder/HTML ↔ PDF, PDF → Text/Markdown.
8. **Optimieren & Sichern**: verkleinern, für Web optimieren, reparieren, PDF/A, Passwort setzen/entfernen,
   Rechte, Metadaten bearbeiten.
9. **Vergleichen & Prüfen**: zwei PDFs vergleichen (Text und Bild), Barrierefreiheit nach PDF/UA (vorhanden ausbauen).
10. **KI**: „Frag das PDF“ mit Belegpflicht, zusammenfassen, Tabellen herausziehen, Formulare erklären.
- Technik: PDFBox (Android: pdfbox-android, Windows: PDFBox 3 – vorhanden), Rendering über PdfRenderer bzw. PDFBox.

### W8-03 ☐ Kreativbereich (Ziel: so nah wie möglich an Canva) · P2 · XL
Schwerpunkt Corporate Design und Uni-Kontext, trotzdem großer Funktionsumfang. Meilensteine:
1. **Leinwand und Datenmodell**: Seiten mit beliebigem Format, Ebenen, Gruppieren, Ausrichten/Verteilen, Raster und
   Hilfslinien, Rückgängig, mehrseitige Designs.
2. **Brand-Kit**: TUD-Farben, Schriften, Logos (vorhanden), Professur-Kit über die Professur-Datenbank; CD-Prüfung
   („Logo zu klein“, „Farbe nicht im CD“).
3. **Vorlagen Uni-Kontext**: Tagungsposter A0/A1, Flyer, Aushang, Programmheft, Einladung, Urkunde/Teilnahmebescheinigung,
   Namensschilder/Tischkarten, Roll-up, Infografik, Social Media (Instagram, LinkedIn, Mastodon), OPAL-Kursbanner,
   Titelbild für Lehrvideos, Titelseite Abschlussarbeit.
4. **Text & Typografie**: Textstile, Effekte (Schatten, Kontur), Kurventext, Listen.
5. **Elemente**: Formen, Linien, Rahmen, Icons, Sticker, Diagramme/Charts, Tabellen, QR-Codes; freie Bild- und Icon-
   Bibliotheken (Lizenz geprüft).
6. **Bilder**: zuschneiden, Filter, Anpassungen, Hintergrund entfernen, Masken, FLUX-Bilder direkt auf die Leinwand.
7. **Größe ändern** („Magic Resize“): ein Design auf andere Formate übertragen.
8. **KI**: Design aus Beschreibung, „Poster aus diesem Paper“, Texte umschreiben, Layout-Vorschläge.
9. **Export**: PNG, JPG, SVG, PDF (Druck mit Beschnitt und Schnittmarken), PowerPoint.
10. **Später (P3)**: Animation, kurze Videos, gemeinsames Bearbeiten in Echtzeit.

### W8-04 ☐ Diagramme und Whiteboard · P2 · L
- Flussdiagramme, Mindmaps, Konzeptkarten, Zeitstrahl, Gantt, Organigramm; Mermaid- und draw.io-Import/Export;
  Whiteboard mit Stift; KI zeichnet aus Beschreibung.

### W8-05 ☐ Mehr Dateiformate · P2 · L
- Text: LaTeX, RTF, EPUB, ODT/ODS/ODP auch als Export.
- Daten: SPSS (.sav), Stata (.dta), R (.rds), JSON/XML/YAML, Jupyter (.ipynb).
- Literatur: BibTeX, RIS, CSL-JSON (Import/Export überall).
- Bilder: HEIC, TIFF, SVG, WebP. Sonstiges: E-Mails (.eml/.msg), ZIP entpacken, H5P, SCORM, draw.io, XMind.
- Betrifft: `files/AttachmentReader.kt`, `docs/OfficeImport.kt`.

### W8-06 ☐ Dateiverwaltung · P2 · M
- Ordner, Schlagwörter, Volltextsuche über alle Formate (Index aus W4-01), Versionsvergleich, „Zuletzt bearbeitet“,
  Freigaben im Professur-Ordner.

---

## W9 – Plattformen & Erweiterbarkeit

### W9-01 ☐ MCP-Client: Fern-Server · P1 · L
- MCP über HTTP (Streamable HTTP) mit Anmeldung (OAuth/Token) auf **beiden** Plattformen.
- *Einstellungen › Verbindungen*: Server hinzufügen, Werkzeuge ansehen, je Werkzeug erlauben/fragen/sperren.
- Jedes fremde Werkzeug läuft über die *Freigaben* und hält die Datenschutzstufe ein; Professur-Richtlinie legt
  erlaubte Server fest (W4-03).

### W9-Zusatz: Ein eigener Broswer im Tool um direkt Linsk zu öffnen und diese mit KI untersuchen zu lassen usw.

### W9-02 ☐ MCP-Client: lokale Server (W) · P2 · M
- Lokale Server (stdio) unter Windows; Hinweis/Installationshilfe, wenn Node.js oder Python fehlen.

### W9-03 ☐ Fertige Anbindungen (Katalog) · P2 · L
- **Nextcloud** (Dateien, Kalender, Deck, Talk), **Moodle** (Webservice-API), **LimeSurvey** (RemoteControl),
  **Confluence/Jira**, **GitLab/GitHub**, **Matrix**, **Overleaf** (über Git), **Obsidian**, **Notion**,
  **ORCID/OpenAlex/Wikidata**, Datenbanken (SQLite/Postgres), **R/Python** für Statistik, Browser-Automatisierung für
  Portale ohne Schnittstelle.
- **OPAL**: beim Betreiber klären, ob eine Schnittstelle freigegeben werden kann; bis dahin Import/Export von Dateien.
- **Microsoft 365/Teams/SharePoint**: nur mit Freigabe im TU-Tenant (klären).

### W9-04 ☐ ADEU als MCP-Server · P3 · M
- Die App stellt TU Vorlagen, TUD-Design-Dokumente, Literatur und Wissenssammlung als MCP-Werkzeuge bereit (Windows,
  lokal), damit z. B. Claude Desktop oder Claude Code sie nutzen können. Zugriff nur nach Freigabe.

### W9-05 ☐ Plugins · P3 · M
- Eigene Erweiterungen als Paket (Modus-Definition + Prompt + MCP-Server/Werkzeuge + TU Vorlagen), verteilbar über
  die Professur-Datenbank.

### W9-06 ☐ macOS und Linux · P2 · L
- Compose Desktop baut bereits plattformunabhängig; zu tun: Pakete (DMG signiert/notarisiert, AppImage/DEB),
  PDF-Rendering ohne Edge (Chromium headless oder openhtmltopdf), Pfade, Autostart, Updater, Tastenkürzel (⌘).

### W9-07 ☐ Englisch vervollständigen · P3 · M
- Alle Texte in `I18n` überführen, englische Fassung vollständig (Einstellungen, Editoren, TU Vorlagen, Fehlermeldungen,
  Lora). Keine weiteren Sprachen.

---

## Q – Querschnitt

### Q-01 ☐ Qualitätstests für Modi und TU Vorlagen · P2 · M
- Beispielaufgaben mit erwarteten Merkmalen je Modus/Vorlage; laufen bei Modellwechsel und vor Releases, warnen bei
  Verschlechterung.

### Q-02 ☐ Rate-Limit-Budget · P1 · S
- Stapel, Panel, Lora und Sprachmodus teilen sich das Kontingent: Vorrang für Interaktives (Lora, Chat), Stapel
  weichen aus bzw. laufen nachts.

### Q-03 ☐ Usability-Tests · P1 · S (je Welle)
- Fünf Personen der Professur, typische Aufgaben, Klickwege und Stolperstellen festhalten; vor und nach W2.

### Q-04 ☐ Recht und Datenschutz · P1 · laufend
- KI-Verordnung: Bewertung von Prüfungen und Personalauswahl = Hochrisiko → nur Assistenz, menschliche Entscheidung,
  Protokoll, Hinweise in der App.
- Verarbeitungsverzeichnis/Datenschutzhinweise für Mail, Abgaben, Bewerbungen; Löschfristen.

### Q-05 ☐ Leistung · P2 · M
- Virtuelles Scrollen für sehr lange Chats und große Listen, Startzeit messen, Speicherverbrauch bei Stapeln.

---

## Offene Fragen

1. **Exchange der TU**: selbst betrieben (EWS nutzbar) oder Exchange Online (Graph + Freigabe der IT)?
2. **OPAL**: gibt es eine freigebbare Schnittstelle (Abgaben, Ergebnisse, Kurse)?
3. **OPARA**: Upload-Schnittstelle vorhanden?
4. **FIS der TUD**: Import-/Exportformat oder Schnittstelle?
5. **Live-Untertitel**: Mitlesen auf Studierenden-Geräten gewünscht (bräuchte lokalen Webserver im selben Netz)?
6. **Startseite**: Wortlaut der Tätigkeits-Kacheln final abstimmen.
7. **Microsoft 365**: Ist eine Tenant-Freigabe für Teams/SharePoint realistisch?

## Zurückgestellt

- Web-Version (entschieden: nein), Betreuungs-Cockpit (vorerst nicht), weitere Sprachen außer Englisch.
- Ideen ohne Auftrag (bei Bedarf wieder aufnehmen): Literatur-Radar, Zitieren beim Schreiben als eigener Modus
  (teilweise in W8-01), Statistik-Werkstatt, Zettelkasten/Wissensgraph, Förder-/Call-Radar, Fragebogen-Auswertung,
  KI als zweite Coder:in, Lehrveranstaltungs-Cockpit, Kurs-Assistent für Studierende, Folien zu Lernvideo,
  Feedback-Werkstatt, Prüfungsorganisation, Barrierefreiheits-Check für Lehrmaterial, Sitzungs-/Gremienassistent,
  zentrale Aufgaben und Fristen, Synopse-Werkzeug, Dokumenten-Scanner, Workflow-Baukasten, Schnellzugriff (Widget/
  globales Kürzel), Stift und Handschrift, Hausstil-Prüfung.

---

## Anhang: Plattform-Lücken (W2-10)

| Funktion | Android | Windows | Paket |
| --- | --- | --- | --- |
| Screenshot aus Zwischenablage | ☑ | ☑ | W1-01 |
| Absatz im Chat (Umschalt+Enter) | ☑ | ☑ | W1-02 |
| Zoom der Oberfläche | System | ☑ | W1-03 |
| Projektordner mit `Ausgabe` | ☐ | ☑ | W1-04 |
| Agent in lokalen Ordnern | ☐ | ☑ | später |
| Speicherorte (Tresore) | ☐ | ☑ | W4-02 |
