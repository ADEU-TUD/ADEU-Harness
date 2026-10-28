# ADEU LLM – Android- und Windows-App für die Uni-LLM-API

Eigene Android-App (und portable Windows-Version) für die OpenAI-kompatible LLM-Schnittstelle unter `https://llm.scads.ai/v1`
(API-Key aus dem TU-Dresden-Self-Service). Inoffiziell, nicht mit der Universität verbunden.

## Funktionen

- **Gemeinsames Design mit wählbaren Stilen**: Standard ist **ADEU** (die Optik der früheren Whisper-App: helle Flächen,
  weiße Karten mit feinem Rand, Gruppenüberschriften). Daneben seriöse Stile für den Dienst: **TU Dresden** (Farben des
  Corporate Designs), **Office** (nüchtern wie Microsoft 365) und **Hoher Kontrast** (barrierearm, WCAG AAA). Unter
  *Weitere Stile* bleiben Claude, ChatGPT, Gemini und Material You. *Einstellungen › Darstellung* zeigt jeden Stil als
  kleine Vorschau; dort lässt sich auch die Startanimation ausschalten.
- **Logo und Startanimation**: Das Logo zeigt das didaktische Dreieck (Lehrende, Lernende, Inhalt) im TU-Blau, in der
  Mitte eine Klangwelle für das Gespräch. Beim Start baut es sich in gut einer Sekunde auf (abschaltbar unter
  *Einstellungen › Darstellung*).
- **Aufgeräumte Seitenleiste**: oben nur das Tägliche (*Vorlagen*, *Literatur*, *Dokumente*, *Geplante Aufgaben*,
  *Bilder erzeugen*, *Transkription*), darunter Projekte, angeheftete Chats und die Chats nach Tagen. Modellübersicht, Modellvergleich,
  Verbrauch und die lokalen Modelle stehen in den Einstellungen unter *Modelle* bzw. *Daten*.
- **Einstellungen in Gruppen**: Profil, *Zugang* (API, GitHub, Umgebungen), *Assistent* (Chat & Werkzeuge, Datenschutz,
  Gedächtnis, Wissenssammlung), *Modelle* (Übersicht, Vergleich, Modelle auf diesem Gerät, Transkription),
  *Dokumente & Darstellung* und *Daten* (Verbrauch, Sicherung). *Chat & Werkzeuge* ist
  aufgeteilt in Neue Chats, Werkzeuge, Agent, Freigaben, Zuverlässigkeit und Erweitert. Auf großen Bildschirmen bleibt die
  Lesebreite angenehm.
- **Modellauswahl wie bei Claude**: oben die empfohlenen Modelle mit kurzem Einsatzzweck, darunter „Weitere Modelle“
  für besondere Zwecke. Veraltete Modelle und Aliase stehen nicht zur Auswahl (in der Modellübersicht als „nicht in der
  Auswahl“ markiert). Standardmodell ist **DeepSeek V4.1 Flash** – schnell, versteht Bilder, nutzt Websuche und Werkzeuge
  und hat 1 Mio. Token Kontext.

  | Empfohlen | Wofür |
  | --- | --- |
  | DeepSeek V4.1 Flash | Für fast alles: Fragen, Texte, Bilder, Websuche, Dokumente (Standard) |
  | GLM-5.3 | Die schwierigsten Aufgaben – gründlich, aber langsamer, keine Bilder |
  | GLM-5.3 Flash | Am schnellsten, auch für sehr lange Dokumente |
  | Qwen3.8 27B | Programmieren und GitHub-Aufgaben |
- **Antworten laufen im Hintergrund weiter**: Chat verlassen oder App schließen (aus den letzten Apps wischen) – die
  Antwort wird trotzdem fertig geschrieben. Solange sie entsteht, zeigt Android eine stille Benachrichtigung mit
  „Stoppen“; ist sie fertig, kommt eine Benachrichtigung, die direkt in den Chat führt. Braucht der Agent eine
  Bestätigung (z.B. für einen Commit), meldet er sich ebenfalls per Benachrichtigung. Anhalten lässt sich die Antwort
  im Chat oder über „Stoppen“ in der Benachrichtigung; „Beenden erzwingen“ in den Android-Einstellungen bricht sie ab.
- **Chat** mit allen Chat-Modellen (Qwen, GLM, DeepSeek, MiniMax, gpt-oss, Gemma, Llama …), Streaming,
  Markdown/Code-Darstellung, Gedankengang von Reasoning-Modellen, Bilder anhängen (Vision-Modelle), Verlauf lokal.
- **Chats verwalten**: Volltextsuche über Titel und Nachrichten, Anheften, Projekte, Umbenennen (⋮ am offenen Chat,
  lange drücken oder Rechtsklick).
- **Bearbeiten & Neu generieren mit Versionen**: Eigene Nachrichten bearbeiten oder Antworten neu erzeugen (auch mit
  einem anderen Modell) – frühere Versionen bleiben über „‹ 1/2 ›“ erreichbar, wie bei ChatGPT.
- **Dateien als Anhang**: PDF (Text; Scans werden als Bilder angehängt), Word, PowerPoint, Excel, OpenDocument,
  Text- und Code-Dateien werden gelesen und mitgeschickt, z.B. zum Zusammenfassen. Word und PowerPoint kommen mit
  Überschriften, Listen und Tabellen beim Modell an – „Mach daraus ein Dokument im TUD-Design“ funktioniert so direkt.
  Beliebig viele Dateien (je bis 25 MB, Text bis 120 000 Zeichen) und bis zu 10 Bilder auf einmal.
- **Vorlagen-Bibliothek für die Professur**: rund 55 fertige Vorlagen, gegliedert in *TUD-Dokumente*, *Lehre*
  (Seminarplan, Sitzung planen, Lernziele, Prüfungsaufgaben, Bewertungsraster, Feedback, Unterrichtsentwurf,
  Unterrichtsvignette), *Betreuung & Prüfung* (Exposé-Feedback, Gutachten, Themen für Abschlussarbeiten, mündliche Prüfung),
  *Forschung* (Forschungsfrage, Fragebogen-Items, Interviewleitfaden, Kategoriensystem, Ergebnisse nach APA 7, Abstract,
  Exzerpt, Projektskizze, Einwilligungserklärung, Peer Review), *Kommunikation*, *Allgemein* und *Personas*
  (Didaktik-Beratung, Methoden- & Statistikberatung, Lektorat, kritische:r Gutachter:in …). Vorlage wählen, die Felder in
  einem Formular ausfüllen, senden – die Anweisungen an das Modell schickt die App unsichtbar mit, im Chat steht nur
  „Vorlage: …“ und Ihre Angaben. Suche, Filter nach Bereich, Favoriten (★, Lora bietet sie auf dem Startbildschirm an), eigene
  Vorlagen mit Platzhaltern `{{Feld}}` bzw. `{{Feld|Beispiel}}`, eingebaute Vorlagen lassen sich kopieren und anpassen.
  Eigene Vorlagen per *Teilen* als Text an Kolleg:innen schicken und dort über *Importieren* übernehmen. Die eingebauten
  Vorlagen kommen mit jedem Update aktuell mit. *Einstellungen › Dokumente & Logos › Meine Absenderangaben* füllt die
  Absender-Felder automatisch aus.
- **Brief ohne Umwege**: Die Vorlage *TUD-Dokumente › Brief* übernimmt Ihren Text wörtlich – die App setzt daraus
  direkt das PDF nach DIN 5008 (Anrede passend zum Empfänger, Grußformel und Unterschrift bleiben zusammen, kein Modell
  dazwischen). Danach lässt er sich im Chat ändern („Formuliere den zweiten Absatz freundlicher“).
- **✨ in jedem Vorlagenfeld**: Unter jedem Feld steht, was hineingehört; der ✨-Knopf glättet, formuliert aus Stichpunkten
  aus, kürzt, macht förmlicher/freundlicher, ordnet Anschriften – oder folgt einer eigenen Anweisung. „↶ Rückgängig“
  stellt den vorherigen Text wieder her. Was Sie eintippen, bleibt als Entwurf gespeichert, bis Sie senden.
- **Adressbuch**: Empfänger aus früheren Briefen stehen im Feld „Empfänger“ zur Auswahl.
- **Vorschläge aus meinem Wissen**: Beim Öffnen einer Vorlage (Brief, Präsentation, Seminarplan …) stehen unter den
  Feldern passende Vorschläge zum Antippen – sofort aus früheren Eingaben in gleichnamige Felder, den Absenderangaben und
  dem Adressbuch, nach wenigen Sekunden auch aus *Gedächtnis* und allgemeiner *Wissenssammlung* (das Modell wählt nur
  aus diesen Angaben aus, erfindet nichts; der Brieftext selbst bleibt Ihrer). „Vorschlag schreiben“ (✨) kennt das
  Gedächtnis ebenfalls. Abschaltbar unter *Einstellungen › Gedächtnis › Vorschläge in Formularen*.
- **Dokumente nachträglich ändern**: Das Modell ändert erstellte Dokumente gezielt (`edit_document`) statt alles neu zu
  schreiben; ältere Versionen bleiben erhalten. Lässt es Lücken wie „[Datum]“, zeigt das Dokument „2 Angaben offen“ –
  „Ausfüllen“ trägt sie ohne Modell ein. Fehlen wesentliche Angaben, fragt das Modell vorher nach.
- **PowerPoint im TUD-Design**: Präsentationen (.pptx, 16:9) mit Titelfolie in TUD-Blau, Abschnitts-, Inhalts-,
  Tabellen-, Zweispalten- und Zitatfolien, Logo, Fußzeile mit Einrichtung und Foliennummer sowie Sprechernotizen. Vorlagen
  *Präsentation (PowerPoint)* und *Lehre › Seminarsitzung als Folien* – Stichpunkte eingeben oder Dateien anhängen.
- **Projekte**: Ein Projekt bündelt Chats zu einem Thema. Beim Anlegen (📁+ in der Seitenleiste) und unter ⋮ › *Projekt
  bearbeiten* gibt es Name, Projektordner, Anweisungen, Datenschutzstufe und Dokumente zum Durchsuchen; sie gelten für
  alle Chats darin. „Neuer Chat im Projekt“ startet direkt dort.
- **Projektordner** (Windows): ein Ordner auf dem Rechner – Google Drive für Desktop, OneDrive, Nextcloud, ein
  Netzlaufwerk (auch `\\server\freigabe`) oder ein lokaler Ordner. Alles, was im Projekt entsteht – Dokumente der KI
  (PDF, Word, PowerPoint, Excel …), Briefe aus dem Brief-Formular und Dokumente aus dem Editor (als Markdown) –, legt die
  App ohne Rückfrage im Unterordner **`Ausgabe`** ab; „Öffnen“ und „Im Ordner zeigen“ führen zur Datei dort, auch
  „Ausfüllen“ offener Angaben aktualisiert sie dort. Eigene Dateien mit gleichem Namen werden nie überschrieben
  („Name (2).pdf“). Mit **„KI darf die Dateien im Ordner lesen“** (Standard: an) sieht die KI, was im Ordner liegt, liest
  die passenden Dateien und bezieht sie ein („Schreib den Antrag anhand meiner Notizen“); ausgeschaltet sieht sie nur
  `Ausgabe`. Dateien außerhalb von `Ausgabe` ändert sie nur nach Rückfrage. Im Chat zeigt der Chip *Projektordner* die
  Verbindung (ein Klick öffnet den Ordner), ⋮ › *Projektordner öffnen* in der Seitenleiste ebenso. Beim Wählen macht
  die App einen Schreibtest und erklärt, falls Windows das Schreiben verweigert (siehe *Dienst-PCs mit IT-Sperre*).
- **Eigenes Material im Editor**: Im KI-Assistenten von Präsentationen, Dokumenten und Tabellen hängt 📎 eigene PDFs,
  PowerPoint-, Word- und Excel-Dateien an. Für Präsentationen gibt es dann „Folien aus den Dateien“, „Ins TUD-Design
  übertragen“ (Folie für Folie, Notizen bleiben Notizen), „Folien ergänzen“ und „Zusammenfassen“; die KI übernimmt Aussagen,
  Zahlen und Quellen aus dem Material und erfindet nichts hinzu. Ganze .pptx/.docx/.xlsx öffnet *Dokumente › Datei öffnen*
  weiterhin direkt im Editor.
- **Transkription** (die frühere Whisper-App, jetzt eingebaut; Startseite mit fester Leiste *Datei öffnen* / *Aufnehmen*,
  Transkripten je Tag in einer Karte und kompakt zusammengefassten fertigen Aufträgen): Aufnahmen, Sprachnachrichten, Interviews, Vorlesungen und
  Videos werden zu Text – wahlweise **auf diesem Gerät** (Whisper Turbo/Small/Base oder Parakeet v3, einmal laden, danach
  offline) oder auf dem **Uni-Server** (Whisper Large v3 Turbo, schnell auch auf alten Rechnern; Sprecher und Pausen
  erkennt trotzdem das Gerät). Aufnahme mit Pause, Markierungen und Live-Text, Warteschlange im Hintergrund (übersteht
  Neustarts), Ersetzungsliste für immer falsch erkannte Namen, Volltextsuche (auch in Kommentaren), Projekte,
  Bearbeitungsstatus *Rohfassung › Korrigiert › Geprüft* (in der Liste sichtbar). **Export**: Word, PDF,
  **Dresing/Pehl** (einfache Transkriptionsregeln, Kürzel, Pausen „(...)“, Zeitmarke je Beitrag), **MAXQDA/f4**
  (Zeitmarken `#00:01:23-4#`), **Untertitel SRT/WebVTT** für Lehrvideos (höchstens zwei Zeilen à 42 Zeichen, mit
  Sprechern, Zeiten aus den gemessenen Wortzeiten), Text und Markdown – Word, PDF, Markdown und Text wahlweise mit
  Zeitstempeln je Satz oder alle 10/30/60 s und mit Kommentaren. *Im Chat verwenden* übergibt das Transkript an einen
  neuen Chat (z. B. „Fasse zusammen“). Audio- und Videodateien, die man aus anderen Apps teilt (WhatsApp …), landen
  direkt in der Transkription.
- **Gemessene Wortzeiten**: Whisper und der Uni-Server liefern keine verlässlichen Zeiten pro Wort. Nach dem
  Transkribieren erkennt deshalb **Parakeet v3** jeden Abschnitt noch einmal auf dem Gerät, und seine Wortzeiten werden
  Wort für Wort mit dem Whisper-Text abgeglichen (auch Zahlen und getrennt/zusammen geschriebene Wörter). Geprüft an
  LibriSpeech *test-clean* (40 Äußerungen, 958 Wörter, Wortgrenzen aus Forced Alignment), Abweichung am Wortanfang:

  | Verfahren | Median | über 250 ms |
  | --- | --- | --- |
  | Zeit nach Wortlänge geschätzt | 200 ms | 37 % |
  | Whisper Turbo selbst (Cross-Attention/DTW) | 60 ms | 8,8 % |
  | **Whisper-Text + Parakeet-Zeiten (die App)** | **50 ms** | **0,3 %** |

  Jedes Wort trägt seine Güte (gemessen / an derselben Stelle gemessen, aber anders geschrieben / nicht gemessen und
  zwischen die gemessenen Nachbarn gelegt); geschätzt wird nichts. Das Transkript vermerkt Quelle und Anteil (für den
  Methodenteil, auch im Export). Für ältere Transkripte: *⋮ › Wortzeiten › Jetzt messen*. Braucht Parakeet v3
  (670 MB) und eine seiner 25 europäischen Sprachen; abschaltbar unter *Einstellungen › Transkription*.
- **Transkript lesen und korrigieren**: Beim Abspielen wird das gesprochene Wort markiert; Tippen auf ein Wort spielt
  genau ab dort. *⋮ › Ansicht & Zeitstempel* (gilt für alle Transkripte): Zeitstempel aus, je Absatz, **je Satz** (wie
  „(0:03)“ bei TurboScribe) oder alle 10/30/60 s – immer an gemessenen Zeiten –, Absätze bei Sprecherwechsel/Pausen,
  nur bei Sprecherwechsel oder je Abschnitt, Schriftgröße, Mitlaufen, Kommentare, nicht gemessene Wörter unterstreichen.
  **Korrekturmodus** wie bei f4transkript: beim Anhalten 1–5 s zurückspringen, Abschnitt in Schleife, Pausen überspringen,
  Tempo 0,5×–2×, Tastenkürzel auch beim Tippen (F4 Abspielen/Anhalten, F3/F5 zurück/vor, F6 wiederholen, Strg+F
  Suchen). **Suche im Transkript** mit markierten Treffern und Springen von Treffer zu Treffer.
- **Abschnitte bearbeiten** (lange drücken bzw. Rechtsklick): Text ändern (unveränderte Wörter behalten ihre gemessene
  Zeit), Sprecher zuordnen, **Kommentar** anfügen, **am Cursor teilen** (an der gemessenen Zeit des Worts, sonst an der
  Abspielposition), mit vorigem/nächstem Abschnitt verbinden, Anfang auf die Abspielposition setzen. *⋮ › Segmentierung*:
  ein Satz je Abschnitt oder Abschnitte bis ca. 10/20/45 s. *⋮ › Sprecher*: umbenennen, zusammenführen, von Hand anlegen
  (auch ohne Sprechererkennung) oder entfernen. *⋮ › Änderungen verwerfen* stellt die Fassung direkt nach dem
  Transkribieren wieder her; jede Änderung lässt sich sofort rückgängig machen.
- **Auswerten**: *⋮ › Redeanteile & Statistik* (Redezeit, Anteil, Wörter, Beiträge, Ø und längster Beitrag, Wörter pro
  Minute, Sprecherwechsel; als Tabelle für Excel/SPSS/R kopieren). *⋮ › Kapitel & Zusammenfassung*: das Sprachmodell
  schlägt Kapitel und eine Zusammenfassung vor (im Rahmen der Datenschutzstufe des Projekts; Kapitel beginnen nur an
  gemessenen Zeiten), Kapitel lassen sich von Hand an der Abspielposition einfügen, umbenennen und löschen.
- **Modelle auf diesem Gerät** (*Einstellungen › Modelle auf diesem Gerät*): kleine Sprachmodelle mit llama.cpp, die
  komplett offline laufen. Die App prüft die Hardware (Arbeitsspeicher, Kerne, Grafikkarte samt Grafikspeicher – ohne
  Admin-Rechte) und empfiehlt passende Modelle mit einem Knopf zum **Installieren**: vom alten ThinkPad (Qwen 3.5 2B,
  Gemma 4 E2B) über Büro-Laptops mit 16 GB (Gemma 4 E4B, Qwen 3.5 4B) bis zum Gaming-PC (Gemma 4 26B A4B, Qwen 3.6 35B A3B)
  und Handys wie dem OnePlus 15. Alle Modelle sind Apache-2.0-lizenziert, werden von einem festen Hugging-Face-Stand
  geladen und mit SHA-256 geprüft; ist der Download im Firmennetz gesperrt, lassen sich die Dateien von Hand einspielen.
  Lokale Modelle stehen in der Modellauswahl (Abzeichen „Lokal“), können Werkzeuge nutzen und beenden sich nach zehn
  Minuten ohne Anfrage, damit der Speicher wieder frei ist. Der Modell-Server lauscht nur auf 127.0.0.1 mit einem
  Zufallsschlüssel.
- **Datenschutzstufe pro Projekt** (⋮ am Projekt › *Projekt bearbeiten*): *Standard*, *Nur TU Dresden* (nur Uni-Server
  und lokale Modelle, externe Anbieter wie Gemini gesperrt) oder *Nur auf diesem Gerät* (nur lokale Modelle,
  Transkription und Spracheingabe auf dem Gerät, Websuche, GitHub und semantische Wissenssuche gesperrt). Die App setzt
  das durch: Modellauswahl, Agent, Ausweichmodell und Werkzeuge halten sich daran, über dem Eingabefeld steht die Stufe.
- **Interrater-Reliabilität**: In der Rechen-Sandbox stehen Cohens Kappa (auch linear/quadratisch gewichtet), Fleiss'
  Kappa und Krippendorffs Alpha (nominal, ordinal, Intervall, Verhältnis; mit fehlenden Werten) exakt zur Verfügung –
  z. B. „Berechne die Übereinstimmung der beiden Codierer in der angehängten Tabelle“.
- **Pseudonymisierung** (*Einstellungen › Datenschutz*): Vor jeder Anfrage ersetzt die App auf dem Gerät Namen, E-Mail-Adressen,
  Telefonnummern, Matrikelnummern, IBAN, Geburtsdaten und Anschriften durch Platzhalter wie `[Name 1]` – der Server sieht nur
  diese. In Antworten, Dokumenten, Briefen und E-Mail-Entwürfen setzt die App die echten Angaben wieder ein. Namen erkennt
  sie nach Anrede oder Titel (Frau, Herr, Dr., Prof.) und an häufigen Vor- und Nachnamen; vollständig zuverlässig ist nur die
  eigene Liste (Klassenlisten, Studierende, Schulen). Dieselbe Person behält in allen Chats denselben Platzhalter; die
  Zuordnung bleibt auf dem Gerät. Ein Vorschaufeld zeigt, was der Server sähe; das Agent-Protokoll nennt, was ersetzt wurde,
  und über dem Eingabefeld steht „Pseudonymisiert“. Ohne Pseudonymisierung warnt die App, wenn Namen oder Kontaktdaten an
  einen Anbieter außerhalb der TU Dresden (Gemini) gingen. Bilder und Spracheingaben lassen sich nicht pseudonymisieren.
- **Quellen-Check**: Das Modell prüft Literaturangaben mit `check_references` gegen Crossref, OpenAlex und Google Books,
  bevor es sie nennt (bestätigt, Angaben weichen ab, nicht gefunden). An jeder Antwort steht unter „⋯“ *Quellen prüfen* –
  ohne Modell, direkt bei Crossref. Vorlage *Forschung › Literaturangaben prüfen* für ganze Literaturverzeichnisse.
- **Tests für OPAL (ONYX)**: Vorlage *Lehre › Test für OPAL* bzw. `create_document` mit `format=qti` erzeugt ein IMS-QTI-2.1-
  Paket (.zip) mit Einfach- und Mehrfachauswahl, Kurzantwort und Freitext, Punkten und Feedback – in OPAL als
  Test-Lernressource importieren.
- **Lehrevaluation auswerten**: Vorlage *Lehre › Lehrevaluation auswerten* – Export (CSV/Excel/PDF) anhängen; Kennwerte je
  Item exakt in der Sandbox (`describe`, `freq`, `percent`), Themen der Freitexte mit Zitaten, Maßnahmen und Rückmeldung an
  die Studierenden als Bericht im TUD-Design.
- **Reisekosten aus Belegen**: Vorlage *Allgemein › Reisekosten aus Belegen* – Fotos von Fahrkarten, Hotel- und
  Tagungsrechnungen anhängen; die App liest Beträge aus, rechnet Summen und füllt ein angehängtes Abrechnungsformular aus
  (sonst Belegliste als PDF). Erstattungsregeln erfindet sie nicht.
- **Barrierefreie Dokumente**: Word-Dateien mit Überschriften-Formatvorlagen, Dokumentsprache, Titel, Alternativtext und
  wiederholter Tabellenkopfzeile; PowerPoint mit Folientiteln (auch ausgeblendet), sinnvoller Lesereihenfolge und
  dekorativ markierten Linien; PDFs mit Titel, Sprache und Fenstertitel (Windows zusätzlich getaggt mit Lesezeichen). Zu
  helle Akzentfarben werden auf einen Kontrast von 4,5:1 abgedunkelt. `check_accessibility` bzw. das Symbol an jeder
  Dokumentkarte prüft PDFs, Word, PowerPoint und HTML (Text statt Scan, Tags, Sprache, Titel, Alternativtexte …);
  Vorlage *Allgemein › Barrierefreiheit prüfen*.
- **Wissenssammlung**: Eigene Dokumente (Modulhandbuch, Prüfungsordnung, Literatur …) unter *Einstellungen ›
  Wissenssammlung* oder pro Projekt. Das Modell sucht die passenden Stellen (Stichworte und semantisch über die
  Embedding-Modelle des Uni-Servers), lässt die Treffer vom Reranker (Qwen3-Reranker) nach Relevanz ordnen und nennt die
  Quelle. „Speichere die Datei im Projektwissen“ übernimmt Anhänge.
- **Gedächtnis, das von selbst mitläuft**: Gemerkte Angaben (Funktion, Lehre, Schreibstil, Kontakte, laufende Vorhaben)
  gehen bei jeder Anfrage automatisch mit – auch bei Modellen ohne Werkzeuge, im Editor-Assistenten und beim
  Modellvergleich; niemand muss die KI darauf hinweisen. Über dem Eingabefeld zeigt **„Gedächtnis · 12“**, wie viele
  Angaben mitgehen; ein Tipp zeigt sie, ändert sie oder schaltet das Gedächtnis für diesen Chat aus. **Dazulernen**: Ruht
  ein Chat kurz, liest ein kleines Modell die neuen Nachrichten und schlägt dauerhafte Angaben vor („2 neue Angaben für
  das Gedächtnis – Übernehmen / Nein, danke“; im Modus *Automatisch* gespeichert mit „Rückgängig“). **„Merken …“** an
  jeder Nachricht (lange Texte verdichtet das Modell vorher zu einem Satz). **Frühere Chats**: Das Modell kennt die
  Titel der letzten Chats und durchsucht sie mit `search_chats`, wenn man sich auf Früheres bezieht („wie letztes Mal“) –
  Chats aus Projekten mit strengerer Datenschutzstufe bleiben außen vor. Einträge haben einen Bereich, gelten auf Wunsch
  nur in einem Projekt und können ablaufen („bis Semesterende“). Auf der Startseite richten drei kurze Fragen ein leeres
  Gedächtnis ein. Das Agent-Protokoll nennt, wie viele Angaben mitgingen. *Einstellungen › Gedächtnis*: aus, vorher
  fragen (Standard) oder automatisch, Dazulernen und frühere Chats einzeln abschaltbar.
- **Sprachmodus** (Wellen-Symbol statt Senden, solange nichts getippt ist): freihändig sprechen, die Antwort wird
  vorgelesen, danach hört die App wieder zu – bis „Beenden“. Das Ende eines Satzes erkennt sie an der Sprechpause (die
  Schwelle passt sich dem Raumgeräusch an); „Unterbrechen“ springt während des Vorlesens direkt zum Zuhören. Alles läuft
  über den normalen Chat (Gedächtnis, Werkzeuge, Datenschutzstufe) und steht danach dort; in Projekten „Nur auf diesem
  Gerät“ liest die Gerätestimme vor.
- **Automatische Modellwahl**: In der Modellauswahl ganz oben *Automatisch* – je Anfrage schlägt die App das passende
  Modell vor (Bilder und Alltag: Standardmodell, Programmieren und GitHub: Qwen, schwierige Analysen: GLM-5.3, sehr lange
  Dokumente: GLM-5.3 Flash) und **fragt vorher** („Anderes Modell verwenden? – Ja, GLM-5.3 / Nein, DeepSeek V4.1
  Flash“). Entschieden wird auf dem Gerät, ohne zusätzliche Anfrage. *Einstellungen › Chat & Werkzeuge*: neue Chats
  automatisch, Rückfrage abschaltbar.
- **Lora, das Maskottchen**: ein kleiner, runder Roboter in der Farbe des Stils (benannt nach *Low-Rank Adaptation*).
  **Im Chat** bleiben Gedankengang und Werkzeugschritte eingeklappt (*Chat & Werkzeuge › Arbeitsschritte einklappen*,
  Standard an); solange der Agent arbeitet, begleitet Lora die Antwort in einer Karte mit fester Höhe (nichts springt) –
  mit „Lora ist dran“, dem aktuellen Schritt und der Zahl der Schritte, die alles aufklappt. Lora forscht dabei mit:
  mikroskopiert, experimentiert mit dem Erlenmeyerkolben, dreht den Globus, schaut durchs Teleskop, gräbt ein Fossil
  aus, schleppt Bücherstapel, notiert, präsentiert ein Diagramm, hat Geistesblitze, liest, tippt, jongliert, tanzt und
  trinkt zwischendurch Kaffee – und spielt oft nach, was der Agent gerade tut (Websuche: Lupe und Teleskop, Literatur:
  Bücher, Rechnen: Experiment, Nachdenken: Denkerpose). Fertige Antworten zeigen eine ruhige Zeile „Gedankengang und
  4 Arbeitsschritte“. **Auf der Startseite** winkt Lora zur Begrüßung, wartet dann (schaut sich um, liest, dreht den
  Globus …) und bietet in einer Sprechblase eine Idee an – Favoriten bzw. empfohlene Vorlagen, Beispiele wie
  „Literatur zu einem Thema finden“ und alle Vorlagen; ↻ oder ein Tipp auf Lora bringt mit einem Geistesblitz die
  nächste. Beim Tippen schreibt Lora mit; nach einer Weile ohne Eingabe nickt Lora ein. Wer Lora nicht sehen möchte,
  schaltet *Chat & Werkzeuge › Lora ausblenden* ein (Standard aus): Dann zeigen Startseite, Arbeitskarte und Sprachmodus
  das Symbol der App, die Ideen bleiben erreichbar.
- **Ruhiges Lesen beim Mitlaufen**: Wer beim Entstehen einer Antwort nach oben scrollt, um etwas zu lesen, sieht den Text
  still stehen – die Chatliste ist oben verankert, die Antwort wächst darunter weiter. Beim Mitlaufen bleibt das Ende im
  selben Bild sichtbar (kein Ruckeln).
- **Rechnen und Daten auswerten**: Für exakte Zahlen rechnet das Modell in einer abgeschotteten JavaScript-Umgebung
  (ohne Internet- und Dateizugriff) – auch mit angehängten CSV- und Excel-Tabellen (Mittelwert, Median, SD, Tabellen).
- **E-Mail-Entwürfe und Termine**: „Schreib eine Mail an …“ legt einen Entwurf an, der sich in der Mail-App öffnet
  (gesendet wird dort); Termine entstehen als Kalendereintrag („Zum Kalender“ auf Android, Outlook auf Windows).
- **Teilen in die App** (Android): Text, Links, PDFs und Bilder aus anderen Apps über „Teilen › ADEU LLM“ – ein neuer
  Chat startet damit.
- **Agent-Protokoll**: Unter jeder Antwort zeigt das Verlaufssymbol, was passiert ist: Anfragen, Werkzeuge, Dauer,
  Tokens, Wartezeiten und ein eventuelles Ausweichmodell.
- **Sicherung & Umzug**: Alles als ZIP exportieren und auf einem anderen Gerät importieren – auch Android ↔ Windows.
  API-Key, Tokens und geheime Variablen gehen nur mit einem Passwort mit und sind damit verschlüsselt (AES-256-GCM).
- **Bilder erzeugen** als Verlauf wie bei Gemini, mit **mehreren Bilder-Chats** (Neuer Chat oben, *Verläufe* zum Wechseln,
  Umbenennen und Löschen): Beschreibung unten eingeben, Stil (Foto, Aquarell, Symbol, Folie …),
  Auflösung (512 × 512 bis 2048 × 2048 nach Seitenverhältnis oder eigene Breite × Höhe), 1–4 Bilder und Modell wählen;
  „✨“ verbessert die Beschreibung. **Überarbeiten**: bis zu vier Bereiche farbig markieren und je Bereich sagen, was dort
  anders werden soll. Die Wünsche werden vorher ins Englische übersetzt und mit genauer Position (z. B. „unten Mitte,
  40–60 % von links“) geschickt, die Maske ist weiß *und* transparent – so verstehen sie Server mit OpenAI- wie mit
  diffusers-Konvention, und Modelle ohne Maske (FLUX.2) wissen trotzdem, wo sie ändern sollen.
  Alle Bilder bleiben in der Galerie (Favoriten), Vollbild mit Zoom, Speichern, Teilen und „Im Chat verwenden“.
- **Papierkorb**: Gelöschte Chats, Transkripte und Bilder lassen sich sofort über „Rückgängig“ zurückholen, Chats und
  Transkripte 30 Tage lang unter *Einstellungen › Daten › Papierkorb*.
- **Sicher gespeichert**: Chats, Transkripte und Listen werden atomar geschrieben (erst Temp-Datei, dann Umbenennen) –
  ein Absturz oder leerer Akku hinterlässt keine halbe Datei. Unlesbare Dateien landen in `defekt/` statt verloren zu gehen;
  Transkripte behalten die vorige Fassung als `.bak`.
- **Statusseite des Uni-Servers**: Die Modellübersicht zeigt, welche Modelle gerade laufen; bei Serverfehlern sagt der
  Chat dazu, ob das Modell laut Statusseite gestört ist.
- **Tablets und Foldables**: Auf breiten Bildschirmen bleibt die Seitenleiste offen, Chat und Eingabe behalten eine
  lesbare Breite, Transkription zeigt Liste und Transkript nebeneinander.
- **PDF-Formulare ausfüllen**: Ein angehängtes PDF-Formular (z.B. Dienstreiseantrag, Anmeldung) füllt der Agent aus und
  gibt es als neue PDF zurück – Textfelder, Ankreuzfelder, Options- und Auswahlfelder; bei PDFs ohne Formularfelder setzt
  er den Text an die passenden Stellen neben die Beschriftungen. Optional „abgeflacht“ (Felder danach nicht mehr änderbar).
  Unterschriftsfelder bleiben leer. Vorlage *Allgemein › PDF-Formular ausfüllen* oder einfach „Fülle das Formular aus“.
- **Modelle vergleichen**: Zwei Modelle beantworten dieselbe Frage nebeneinander, mit Antwortzeit, Tokens und
  Tokens/s; eine Seite lässt sich als Chat fortsetzen.
- **Verbrauch**: Anfragen und Tokens pro Tag (Diagramm), nach Modell und Art; nur lokal gezählt.
- **Geplante Aufgaben**: Der Agent führt Aufträge zeitgesteuert aus (täglich/Wochentage um eine Uhrzeit oder alle
  N Stunden), z.B. jeden Morgen News zu einem Thema. Ist eine Aufgabe fertig, erscheint eine Benachrichtigung mit dem
  Anfang des Ergebnisses (Tippen öffnet den Chat) – auch wenn die App gerade offen ist; dann zusätzlich ein Banner oben
  in der App. Neue Ergebnisse sind in der Seitenleiste markiert. Oben im Bereich *Geplante Aufgaben* zeigt die App, ob
  Benachrichtigungen erlaubt sind und ob Android die App im Hintergrund arbeiten lässt, mit Knöpfen zum Einschalten
  und einer Test-Benachrichtigung.
- **Umgebungen für Agenten**: Name, Netzwerkzugriff (vollständig, nur erlaubte Domains, keiner),
  Umgebungsvariablen (auch als `.env` einfügbar) und Hinweise für das Modell. Geheime Variablen sieht das Modell nie;
  es nutzt sie als `${NAME}` in Web-Anfragen (`fetch_url` mit Methode, Headern und Body). Verschlüsselt gespeichert.
- **Modellübersicht** mit Beschreibung, Stärken, Leistungs-/Tempo-Einschätzung, Kontextlänge und Fähigkeiten,
  Suche, Filter und Sortierung.
- **GitHub-Agent**: Mit einem GitHub-Token arbeitet das Modell in deinen Repositories – Dateien lesen, durchsuchen,
  bearbeiten und committen, Branches und Pull Requests erstellen, Issues lesen/kommentieren. Wann es vorher fragt, legen
  die *Freigaben* fest (siehe unten). Ein GitHub-Link in einer Nachricht verknüpft das Repository direkt.
- **Mehrere GitHub-Konten und Repositorys**: Neben dem Token des eigenen Kontos lassen sich weitere Tokens eintragen,
  z.B. ein Fine-grained Token pro Organisation. Die Repository-Auswahl zeigt die Repositorys aller Tokens zusammen, ein
  Chat kann mehrere gleichzeitig verknüpfen (auch aus verschiedenen Organisationen) – der Agent liest dann z.B. in einem
  und überträgt die Änderung in ein anderes. Für jede Aktion nimmt die App automatisch das passende Token.
- **Weitere Anbieter (Google Gemini)**: Unter *Einstellungen › API-Zugang › Weitere Anbieter* einen Gemini-API-Key aus
  Google AI Studio eintragen. Dann stehen **Gemini 3.8 Flash**, **3.6 Flash** und **3.5 Flash** (die besten Modelle des
  kostenlosen API-Plans) bei den empfohlenen Modellen – unter denen von ScaDS.AI, das der Standard bleibt. Weitere
  Gemini-Modelle stehen unter „Weitere Modelle“. Auch andere OpenAI-kompatible Anbieter lassen sich eintragen.
- **Web**: Websuche (DuckDuckGo), Nachrichtensuche (Bing News/Google News), Webseiten lesen und Literatur prüfen.
- **Dokumente erstellen**: PDF, Word (.docx), PowerPoint (.pptx), HTML, Markdown und Text. PDFs werden über HTML/CSS gerendert:
  Stile *Modern*, *Kräftig*, *Elegant*, *Klassisch*, *Schlicht*, frei wählbare Akzentfarbe, Fußzeile, Hinweisboxen
  (`> [!NOTE]`, `> [!TIP]`, `> [!WARNING]`). Für besondere Gestaltung (Flyer, Rechnung, Lebenslauf …) schreibt das
  Modell eigenes HTML mit Farben, Formen und Schriften. Klappt das gestaltete Rendern einmal nicht, entsteht trotzdem
  ein PDF mit demselben Text (schlichtes Layout); der automatische Test im Emulator prüft das Rendern vor jedem Release,
  auch bei App im Hintergrund.
- **TUD-Dokumente im Corporate Design** (nach dem offiziellen CD-Paket 2025): Brief nach DIN 5008 Form B (Anschriftfeld
  45 mm vom oberen Rand mit Rücksendezeile, Informationsblock ab 125 mm mit Einrichtung, Telefon, E-Mail, Zeichen und
  Datum, Betreff), Protokoll, Aktennotiz, Formular zum Ausfüllen (beschriftete Felder, Ankreuzkästchen,
  Unterschriftsfelder), Merkblatt/Aushang, Bericht, Einladung mit Tagesordnung und **Teilnahmebescheinigung/Urkunde**
  nach der TUD-Vorlage „Zertifikate & Urkunden“. Das Modell schreibt nur kurzes Markdown; die App setzt das Layout:
  offizielles Logo 17 mm hoch oben links (25 mm/18 mm vom Rand), darunter die Einrichtung, Hausschrift **Noto Sans**
  (eingebettet, also auf jedem Gerät gleich), Dunkelblau `#00008C` für Titel und Überschriften, Hellblau für
  Tabellenköpfe und Hinweisboxen, das Grau der TUD-Palette für Nebentexte, Fußzeile mit „Seite x von y“. Die Einrichtung
  ist voreingestellt auf Fakultät Erziehungswissenschaften › Institut für Erziehungswissenschaft › Professur für
  Allgemeine Didaktik und Empirische Unterrichtsforschung und lässt sich unter *Einstellungen › Dokumente & Logos*
  ändern. Das Logo ist eingebaut (blau und weiß für blaue Flächen).
- **Logos**: Unter *Einstellungen › Dokumente & Logos* gespeicherte Logos bindet das Modell auf Befehl ein („mit
  meinem Logo“) oder automatisch in jedes Dokument. Ein Bild im Chat kann mit „Speichere das als Logo“ übernommen werden.
- **Darstellung**: Stile *Claude* (Standard), *Material You*, *ChatGPT* und *Gemini*, Farbton, Hell/Dunkel/System,
  reines Schwarz (AMOLED) und Schriftgröße.
- **Bildgenerierung** mit FLUX.2 (Speichern in der Galerie).
- **Spracheingabe** (Whisper) und **Vorlesen**: wahlweise mit der KI-Stimme des Uni-Servers (der die Antwort ohnehin
  geschrieben hat – keine Daten an Dritte) oder komplett offline mit einer Stimme des Geräts; lange Antworten werden in
  Abschnitten gelesen, Code und Links übersprungen.

- **Literaturverzeichnis in 8 Zitierstilen**: APA 7 (deutsch und englisch), Zeitschrift für Pädagogik, DIN ISO 690,
  Harvard, Chicago (Autor-Jahr), MLA 9 und IEEE. Quellen als BibTeX, RIS, CSL-JSON oder einfach als Text einfügen
  (auch ein fertiges Verzeichnis aus Word) – die App sortiert alphabetisch (mit Namenszusätzen wie „von“ richtig),
  erkennt Dubletten und **ergänzt fehlende Angaben** (DOI, Verlag, Ort, Seiten, Heft, Mitautor:innen) über Crossref,
  OpenAlex, die Deutsche Nationalbibliothek und Google Books. Jeder Eintrag zeigt, was ergänzt wurde und was noch fehlt;
  unsichere Treffer werden nur vorgeschlagen. Export als Word, PDF, Markdown, BibTeX, RIS und CSL-JSON. Im Chat unter
  einer Antwort: *⋯ › Literaturverzeichnis erstellen*. Der Zitierstil ist unter *Einstellungen › Literatur & Zotero* wählbar.
- **Literatursuchmodus**: Thema und Kontext angeben (Chip *Literatursuche* oder `/Literatursuche`) – der Agent sucht
  systematisch in Crossref, OpenAlex, ERIC (Bildungsforschung), DNB, **SLUB-Katalog** (was an der TU Dresden ausleihbar
  oder lizenziert ist), **PubMed/Europe PMC**, **arXiv**, **DOAJ** und Google Books, verfolgt Zitationen in beide
  Richtungen (Schneeballsuche), prüft Zeitschriften auf neue Artikel und liefert eine gegliederte Übersicht mit
  Begründung je Quelle und Suchprotokoll. **Jedes Werk ist verlinkt** (Volltext, DOI oder Katalogeintrag). Treffer landen in einer Literaturliste (Bereich *Literatur*), auf Wunsch
  direkt in einer **Zotero**-Sammlung (Web-API-Schlüssel unter *Einstellungen › Literatur & Zotero*). Ein optionaler
  OpenAlex-Schlüssel erhöht das Abfragekontingent.
- **Bereich Literatur**: Suche, Sammlungen mit Notizen und Schlagwörtern, Einträge bearbeiten, Import aus Dateien, Übertragen nach
  Zotero, Verzeichnis im gewählten Stil – mit einem Tipp in den Chat oder den Dokument-Editor. Jedes Werk hat Knöpfe zum
  Öffnen (Volltext/DOI), *SLUB*, *Scholar* und *ResearchGate*. Portale ohne offene Schnittstelle (Google Scholar,
  ResearchGate, BASE, FIS Bildung, pedocs, Semantic Scholar, PubPsych) öffnet die Suchseite mit dem Thema im Browser.
  ResearchGate ist keine Literaturdatenbank, sondern ein Netzwerk, in dem Forschende ihre Arbeiten hochladen: gut für
  Volltexte, nicht für eine dokumentierbare systematische Suche.
- **Office-Suite mit KI: Dokumente, Präsentationen, Tabellen, Markdown** (Bereich *Dokumente*): vier Editoren mit gleichem Aufbau
  (Kopfzeile, Tabs, Kommentare, Versionsverlauf, Export, KI-Assistent) – nur der Inhalt unterscheidet sich.
  - **Dokument** (wie Google Docs/Word, mit Obsidian-Funktionen): Seite wie auf Papier, feste Formatleiste (Absatzformat,
    fett, kursiv, durchgestrichen, Code, Link, Aufzählung, Nummerierung, **Checklisten zum Abhaken**, Zitat, Tabelle als
    Zellenraster, Trennlinie, Datum), `/`-Befehle am Absatzanfang, Enter setzt Listen fort, Tab rückt ein, Strg+Umschalt+↑/↓
    verschiebt Absätze. Getipptes wird nach einer kurzen Pause automatisch gespeichert. Lesemodus, **Markdown-Quelltext**
    wie in Obsidian, **Gliederung** zum Springen, Verknüpfungen mit `[[Titel]]` samt Rückverweisen, Suchen & Ersetzen,
    Wortzahl und Lesezeit. Ein leeres Dokument bietet Vorlagen (Protokoll, Seminarplan, Gliederung …) und „Mit KI entwerfen“.
    **Keine Markdown-Zeichen beim Bearbeiten**: Überschriften ohne „#“, fett statt „\*\*“, „•“ und „☐“ statt „- “ und
    „- [ ] “, Links ohne Adresse; Rücktaste am Anfang einer Überschrift oder eines Listenpunkts nimmt die Formatierung
    weg wie in Word. **KI fragen**: Text markieren öffnet am PC sofort ein Fenster „KI fragen“ zum Hineinschreiben und
    Absenden (am Handy über den Knopf *KI fragen*); der Vorschlag erscheint unter der Stelle. **TUD-Dokumente**: *Neu ›
    ⋮ › Im TUD-Design* (Ergebnisprotokoll, Aktennotiz, Einladung, Handout, Stellungnahme, Merkblatt, Projektskizze) –
    die Seite zeigt Logo und Einrichtung, PDF und Word entstehen im Corporate Design; *⋮ › Design* wechselt es je Dokument.
  - **Präsentation** (wie PowerPoint): Folienleiste, Folie groß – **genau so, wie die PowerPoint-Datei sie zeigt** (TUD-Logo,
    Titelfolie in TUD-Blau mit Untertitel, Angaben und Einrichtung, Fußzeile „TU Dresden · Professur · Titel“ mit
    Foliennummer, Schriftgröße je Textmenge, Tabellen). Layouts (Titel, Abschnitt, Inhalt, zwei Spalten, Zitat),
    Felder ohne Markdown-Zeichen mit Leiste für fett, kursiv, Stichpunkte, Einrücken; Sprechernotizen, **Folienübersicht**,
    Folien duplizieren/verschieben (Strg+M, Strg+D, Strg+Umschalt+↑/↓), **Vorführen** (F5) mit **Referentenansicht**
    (nächste Folie, Notizen, Timer), Export als PowerPoint im TUD-Design. Fehlt die Einrichtung, fragt der Editor danach.
  - **Tabelle** (wie Excel): Zellraster mit Spalten- und Zeilenköpfen, **Bearbeitungsleiste mit Formeln** in deutscher
    Schreibweise (`=SUMME(B2:B9)`, `=MITTELWERT`, `=WENN(…;…;…)`, `=RUNDEN`, `=ZÄHLENWENN`, `=SUMMEWENN`, `=STABW`, `&` …,
    auch englische Namen), dazu **SVERWEIS, XVERWEIS, INDEX/VERGLEICH, WENNFEHLER, WENNS, ZÄHLENWENNS, SUMMEWENNS,
    MITTELWERTWENN, LINKS/RECHTS/TEIL, WECHSELN, TEXTVERKETTEN, TEXT, SUMMENPRODUKT, RANG, KGRÖSSTE, VARIANZ, QUANTIL,
    KORREL** u. a.; WENN wertet wie Excel nur den gewählten Zweig aus. Tabellenblätter als Tabs, Zeilen/Spalten einfügen
    und löschen, **nach unten/rechts ausfüllen** (relative Bezüge wandern mit, `$`-Bezüge bleiben), Duplikate entfernen,
    Zeilen und Spalten tauschen, **Filter**, fixierte Kopfzeile, Pfeiltasten wie in Excel, Sortieren, Σ, Statuszeile mit
    Summe/Mittelwert/Anzahl. Der Excel-Export behält die Formeln (mit berechneten Werten).
  - **Markdown-Datei** (wie Obsidian oder eine README): Quelltext mit allen Zeichen, Werkzeugleiste (H1–H3, fett,
    kursiv, Code, Link, Listen, Checkliste, Zitat, Tabelle, Codeblock), Vorschau daneben (am Handy umschaltbar),
    Export als .md. .md-Dateien öffnen sich als Markdown-Datei.
  - **KI als Mitautorin**: ✨ an jeder Stelle (Verbessern, Rechtschreibung, Kürzen, Ausführlicher, Weiterschreiben, Förmlicher,
    Einfacher, Stichpunkte ↔ Fließtext, Übersetzen, eigene Anweisung) zeigt einen Vorschlag mit *Übernehmen / Verwerfen /
    Erneut / Darunter einfügen*; in Tabellen „Formel beschreiben“ und „Spalte mit KI füllen“, bei Folien „Folie verbessern“
    oder „Sprechernotizen“. Der **KI-Assistent** (✨ oben oder „Mit KI schreiben …“ unten, Strg+E) arbeitet direkt im Dokument
    – mit Schnellaktionen wie Entwurf, Gliederung, Feedback, Sprache glätten, Folien entwerfen, Auswerten, Summenzeile;
    seine Änderungen werden farbig markiert und stehen im Versionsverlauf. Kommentare mit *An KI senden* bleiben erhalten.
  - **Dateien aus dem Chat übernehmen**: Erzeugt die KI im Chat ein Word-, PowerPoint-, Excel- oder Markdown-Dokument, öffnet
    *Im Editor bearbeiten* es dort; *Im Editor öffnen* gibt es auch für andere Dokumentkarten und angehängte Dateien (Tippen
    auf den Anhang). *Datei öffnen* im Bereich *Dokumente* importiert .docx (Überschriften, Listen, Tabellen, fett/kursiv),
    .pptx (Folien mit Notizen), .xlsx (Blätter mit Formeln), .csv, .md und .txt.
  - **In Chat senden** (⋮ im Editor): das Dokument als Text oder als Word/PowerPoint/Excel-Datei in einen neuen oder
    vorhandenen Chat anhängen. Export als PDF, Word (auch **mit Änderungsverfolgung** gegenüber einer früheren Version),
    Markdown, HTML, PowerPoint, Excel, CSV.
- **Speicherorte wie Obsidian-Tresore** (Windows, *Einstellungen › Speicherorte*): Ordner als Ablageort für Dokumente
  einbinden – jedes Dokument liegt dort als lesbare `.md`-Datei (Versionen und Kommentare in `.adeu`). Liegt der Ordner in
  **Google Drive**, OneDrive oder Nextcloud, synchronisiert deren Programm alles auf andere Rechner; Änderungen von dort und
  neue `.md`-Dateien erscheinen nach wenigen Sekunden. Zusätzlich lässt sich unter *Einstellungen › Windows* der ganze
  **Datenordner** (Chats, Einstellungen …) in einen eigenen Ordner verlegen (kopiert, startet neu; große Modelle bleiben lokal).
- **Agent in lokalen Ordnern** (Windows, *Einstellungen › Lokale Ordner*): freigegebene Ordner kann der Assistent in jedem Chat
  auflisten, durchsuchen und lesen (auch Word, PowerPoint, Excel, PDF), Dateien anlegen und ändern, Dokumente direkt als
  .docx/.pdf/.pptx/.xlsx dort speichern (z. B. im TUD-Design), verschieben und löschen (in den Unterordner
  `.adeu-papierkorb`). Ordner lassen sich auf „nur lesen“ stellen; außerhalb kommt er nicht. Rückfragen vor Änderungen
  richten sich nach *Chat & Werkzeuge › Freigaben*.
- **Chat**: `/`-Befehle und `@`-Erwähnungen (Dateien, Literaturlisten, Dokumente, Kurztexte), *Anfrage verbessern*,
  gespeicherte Kurztexte, Entwürfe pro Chat, Zitieren eines Antwortteils, Antworten bewerten (👍/👎 mit Notiz;
  Auswertung je Modell unter *Verbrauch*), *Weiterschreiben* bei abgeschnittenen Antworten, *Abzweigen* in einen neuen
  Chat, Chat duplizieren, Suche im Chat, temporäre Chats (werden nicht gespeichert), Archiv, Mehrfachauswahl in der
  Seitenleiste, Export und Import als JSON. Mehrere Werkzeugaufrufe werden zu einer Zeile zusammengefasst
  („5 Werkzeuge genutzt“), auf der Startseite schlägt Lora Beispiele und Vorlagen vor.
- **Forschung**: Codieren von Transkripten mit eigenem Kategoriensystem (Codes, Farben, Ankerbeispiele) –
  *ausschließlich von Hand*, die KI codiert nicht. Intercoder-Übereinstimmung (Cohen's κ, prozentuale Übereinstimmung,
  Abweichungsliste) und Export nach **REFI-QDA** (`.qdpx`, für MAXQDA, ATLAS.ti, NVivo) sowie als CSV. Diagramme
  (Balken, Linie, Kreis, Streuung, Boxplot) direkt im Chat und in Dokumenten, Excel-Tabellen (`.xlsx`) und CSV,
  Formeln (LaTeX) werden lesbar gesetzt. Neue Vorlagen: Literaturverzeichnis, Literaturrecherche, Fragebogen,
  Datenmanagementplan, Verarbeitungsverzeichnis, Datenschutzinformation, Ethikantrag. Fundstellen aus der
  Wissenssammlung nennen die Seitenzahl.
- **Lehre**: Quizfragen als **Moodle-XML** und **GIFT**, Lernkarten für **Anki**, Fragebögen für **LimeSurvey**,
  **Semesterplan** mit den Vorlesungszeiten und Feiertagen in Sachsen (auf Wunsch als Kalenderdatei mit allen
  Terminen), Vorlagen für Moodle-Kurse, Lernkarten, Semesterkalender und Vorlesungsfolien aus einem Skript.
- **Serienbriefe**: Vorlage mit `{{Platzhaltern}}` plus Tabelle oder CSV – die App erzeugt je Zeile ein Dokument
  (z.B. Teilnahmebescheinigungen, Briefe) und packt alle in eine ZIP-Datei.
- **Transkripte**: anonymisierte Kopie (Namen, Kontaktdaten und eigene Begriffe werden einheitlich ersetzt, Sprecher
  zu „Person A/B“) mit getrennter **Zuordnungsliste** als PDF, **Übersetzung** Abschnitt für Abschnitt mit erhaltenen
  Zeiten (für Untertitel), **Stimmprofile**, die bekannte Sprecher:innen in neuen Aufnahmen wiedererkennen, und
  *Protokoll erstellen* aus dem Transkript.
- **Bedienung**: **Strg+K** Befehlspalette (Bereiche, Einstellungen, Chats), **Strg+/** Übersicht der Tastenkürzel,
  Strg+L/D/T für Literatur, Dokumente, Transkription; die Seitenleiste unter Windows lässt sich durch Ziehen breiter
  oder schmaler machen. Suche in den Einstellungen. **Sprache der Oberfläche** Deutsch oder Englisch
  (*Einstellungen › Darstellung*; bisher sind Menüs, Einstellungen und Startseite übersetzt, Antworten folgen der
  gewählten Sprache). Ein automatischer Test prüft den Textkontrast aller Stile nach WCAG.

## Installation

1. Unter **Releases** die neueste `ADEU-LLM-x.y.z.apk` herunterladen.
2. Auf dem Handy öffnen und die Installation aus unbekannten Quellen erlauben.
3. Beim ersten Start Benachrichtigungen erlauben (sonst erfährst du nicht, wann Aufgaben fertig sind).
4. In der App unter **Einstellungen** den API-Key (`sk-…`) eintragen und „Verbindung testen“.
5. Optional: Vorname eintragen (Begrüßung) und GitHub-Token (siehe unten).
6. Für zuverlässige geplante Aufgaben unter *Geplante Aufgaben* „Im Hintergrund erlauben“ tippen. Manche Hersteller
   (z.B. Xiaomi, Oppo/OnePlus/Realme, Huawei) beenden Apps zusätzlich – dort in den Akku-Einstellungen der App
   „Hintergrundaktivität erlauben“ bzw. „Nicht optimieren“ wählen.

Updates kommen von selbst: Gibt es eine neue Version, fragt die App beim Start („Jetzt aktualisieren“), lädt die APK und
öffnet den Installationsdialog von Android („Aktualisieren“ tippen; beim ersten Mal einmalig „Apps aus dieser Quelle
zulassen“). API-Key, GitHub-Token, Einstellungen und Chats bleiben dabei erhalten – nur beim Deinstallieren gehen sie
verloren. Siehe *Automatische Updates*.

### Release über ADEU-TUD/Test

Seit 1.0.26 erscheinen neue Versionen unter **https://github.com/ADEU-TUD/Test/releases** (APK, Windows-EXE und -ZIP).
Das Repository ist eine Kopie dieses Codes in der Organisation ADEU-TUD, deren Actions-Minuten den Build tragen. Für
eine neue Version wird der aktuelle Stand dorthin auf `main` übertragen und dort *Actions › Build & Release › Run
workflow* mit der nächsten **Versionsnummer** gestartet (z.B. `27` für 1.0.27). Die Nummer muss größer sein als die
installierte Version, sonst lässt sich die APK nicht als Update installieren. Der Workflow baut, testet (Emulator und
Windows) und veröffentlicht selbst.

### PR-Check

Jeder Pull Request startet `.github/workflows/pr-check.yml` (etwa 6–8 Minuten): Android-App kompilieren (ohne lokale
Modelle), Tests der Sprach-Engine und des gemeinsamen Codes. Der vollständige Build mit Emulator bleibt im Release-Workflow.

### Release ohne GitHub Actions

Das Actions-Kontingent ist aufgebraucht, deshalb baut der Workflow (`.github/workflows/android.yml`) nicht mehr bei jedem
Push, sondern nur noch, wenn man ihn unter *Actions › Build & Release › Run workflow* startet (Emulator- und Windows-Test,
Release auf GitHub – ein Durchlauf kostet etwa 25 Minuten). Stattdessen entsteht ein Release lokal:

```bash
.github/scripts/release-local.sh 26 ADEU-LLM-1.0.25-Windows.zip   # → dist/ADEU-LLM-1.0.26.apk und -Windows.zip
```

Die APK wird mit dem Release-Schlüssel signiert (siehe *Signaturschlüssel* unten) und installiert sich als Update. Die Windows-ZIP setzt
das Skript auf jedem Betriebssystem zusammen: die neuen Jars (mit den Windows-Bibliotheken) plus Starter, Java-Laufzeit
und Skia-DLL aus der Windows-ZIP einer früheren Version. Das geht, solange Compose, Skia und die Java-Module gleich
bleiben – sonst bricht das Skript mit einem Hinweis ab, und die Windows-Version muss einmal über den Workflow entstehen.
Die Ein-Datei-EXE braucht den C#-Compiler von Windows und entsteht nur im Workflow; die ZIP ist gleichwertig (entpacken,
`ADEU LLM.exe` starten). Die fertigen Dateien lassen sich dann von Hand unter *Releases* hochladen oder direkt
weitergeben.

### Neue Version veröffentlichen (Kurzanleitung, auch für KI-Assistenten)

1. Änderungen per Pull Request nach `main`; PR-Check grün, dann mergen.
2. *Actions › Build & Release (Android + Windows) › Run workflow* auf `main` starten mit
   - **version**: letzte Version + 1 – nachsehen unter
     [Updater-Releases](https://github.com/ADEU-TUD/ADEU-Harness-Updater/releases/latest), z. B. `38` für 1.0.38. Nie leer
     lassen: Dann nimmt der Workflow die Laufnummer, die kleiner ist, und Android installiert die APK nicht als Update.
   - **notes**: „Was ist neu“ in 1–3 kurzen Sätzen für Nutzer:innen ohne Technikkenntnisse (steht im Update-Popup).

   KI-Assistenten: GitHub-MCP `actions_run_trigger`, Methode `run_workflow`, `workflow_id: android.yml`, `ref: main`,
   `inputs: {"version": "38", "notes": "…"}`; Lauf mit `actions_list`/`actions_get` verfolgen.
3. Der Workflow (ca. 15 Min.) baut, testet (Emulator, Windows-Selbsttest), legt hier ein Release an und veröffentlicht
   APK, Windows-ZIP/-EXE und `version.json` im öffentlichen Repo `ADEU-TUD/ADEU-Harness-Updater` (Schritt „Für
   automatische Updates veröffentlichen“, Secret `UPDATER_TOKEN`).
4. Prüfen: `https://github.com/ADEU-TUD/ADEU-Harness-Updater/releases/latest/download/version.json` zeigt die neue
   Version – ab dann fragen die Apps beim Start.
5. Schlug nur das Veröffentlichen fehl (z. B. Token abgelaufen): Token erneuern, Secret `UPDATER_TOKEN` ersetzen,
   dann *Actions › Update veröffentlichen* mit derselben Version und denselben notes – baut nicht neu.

Der Token in `UPDATER_TOKEN` (Fine-grained, Resource owner ADEU-TUD, *Contents: Read and write* auf
`ADEU-Harness-Updater`) läuft am **24.01.2027** ab – vorher neu erstellen und das Secret ersetzen.

### Automatische Updates

Neue Versionen erscheinen im **öffentlichen** Repository
[ADEU-TUD/ADEU-Harness-Updater](https://github.com/ADEU-TUD/ADEU-Harness-Updater/releases) – dort liegen nur die
fertigen Dateien (`version.json`, Windows-ZIP und -EXE, APK), kein Code. Die Apps lesen bei jedem Start (und alle sechs
Stunden im Infobereich)
`https://github.com/ADEU-TUD/ADEU-Harness-Updater/releases/latest/download/version.json` – ein einfacher Download ohne
GitHub-Konto und ohne das API-Limit von 60 Anfragen pro Stunde, das sich ein ganzes Uni-Netz teilen würde. Ist die
Version neuer als die installierte, erscheint ein Popup mit „Was ist neu“, *Jetzt aktualisieren* und *Später*
(*Später* fragt beim nächsten Start wieder). Die Datei wird mit ihrer SHA-256-Prüfsumme aus `version.json` geprüft.
*Einstellungen › Updates* zeigt die Version, sucht auf Knopfdruck und lässt die automatische Suche abschalten.

- **Windows** (`desktop/…/update/`): Die ZIP wird in den Unterordner `.update` des App-Ordners geladen und dort entpackt –
  von Java geschrieben, also ohne „Mark of the Web“, an der SmartScreen heruntergeladene Programme blockiert. Danach
  startet die neue `ADEU LLM.exe` mit `--apply-update`, die App beendet sich, die neue Version wartet darauf, legt die
  alten Dateien nach `.update-alt`, kopiert die neuen an dieselbe Stelle und startet die App neu. Kein `cmd.exe`, keine
  PowerShell, keine Admin-Rechte; der Pfad bleibt gleich, also funktionieren Verknüpfungen, das Symbol in der Taskleiste
  und „Mit Windows starten“ weiter. Geht etwas schief, kommen die alten Dateien zurück und die bisherige Version startet
  (Protokoll: `%LOCALAPPDATA%\ADEU LLM\update.log`). Ist der App-Ordner schreibgeschützt (von der IT installiert), sagt
  das Popup das und verweist auf die Download-Seite.
- **Android**: APK in den Cache laden, prüfen, Installationsdialog öffnen (`REQUEST_INSTALL_PACKAGES`, über den
  vorhandenen FileProvider). Android fragt bei jedem Update einmal nach.
- **Ohne GitHub** (Firewall): Unter *Einstellungen › Updates › Update-Quelle* einen Ordner eintragen, z. B.
  `\\server\freigabe\ADEU-Update`, in dem die Dateien eines Releases liegen. Für alle PCs auf einmal: eine Datei
  `update-quelle.txt` mit diesem Pfad neben `ADEU LLM.exe` legen (bleibt bei Updates erhalten). Den Ordner füllt
  `NO_UPLOAD=1 .github/scripts/publish-update.sh 1.0.37 dist "Was ist neu"` (schreibt `version.json` in `dist/`).

**Einmalig einrichten:**
1. Das Repository `ADEU-TUD/ADEU-Harness-Updater` bleibt öffentlich (Releases privater Repositories lassen sich ohne
   Konto nicht herunterladen).
2. Einen *Fine-grained personal access token* anlegen (GitHub › Settings › Developer settings): *Resource owner*
   ADEU-TUD, *Repository access* nur `ADEU-Harness-Updater`, *Permissions › Contents: Read and write*. Ihn hier unter
   *Settings › Secrets and variables › Actions* als `UPDATER_TOKEN` speichern.
3. Ab dann veröffentlicht *Actions › Build & Release › Run workflow* (Feld „Was ist neu?“ ausfüllen) jede Version von
   `main` zusätzlich als Update. Eine schon gebaute Version (z. B. von vor dem Token) veröffentlicht *Actions › Update
   veröffentlichen* nachträglich, ohne neu zu bauen. Lokal: `PUBLISH=1 UPDATE_NOTES="Was ist neu"
   .github/scripts/release-local.sh 37 …` oder danach `.github/scripts/publish-update.sh 1.0.37 dist "Was ist neu"`
   (braucht `gh auth login`).

**Erste Verteilung:** Versionen bis 1.0.36 kennen die Update-Suche noch nicht. Die erste Version mit Updater muss also
noch einmal von Hand auf die Rechner (USB-Stick, wie bisher, oder die EXE/APK aus den Releases) – danach aktualisieren
sich die Apps selbst. Die Versionsnummer muss immer größer sein als die installierte.

## Windows (portabel, ohne Admin-Rechte)

Dieselbe App gibt es als portable Windows-Version – gleiche Funktionen, gleicher Code (Kotlin/Compose Desktop) und die
Java-Laufzeit ist eingebaut. Nichts muss installiert werden, Admin-Rechte sind nicht nötig.

1. Unter **[Releases](https://github.com/ADEU-TUD/ADEU-Harness-Updater/releases/latest)** `ADEU-LLM-x.y.z-Windows.exe`
   herunterladen und doppelklicken. Spätere Versionen installiert die App selbst (*Automatische Updates*).
   Beim ersten Start einer Version entpackt sie sich einmalig (wenige Sekunden) nach `%LOCALAPPDATA%\ADEU LLM\app`;
   danach startet sie sofort. Zeigt Windows „Der Computer wurde durch Windows geschützt“ (die Datei ist nicht signiert):
   *Weitere Informationen* › *Trotzdem ausführen*.
2. Alternativ `ADEU-LLM-x.y.z-Windows.zip` entpacken (z.B. auf den Desktop oder einen USB-Stick) und `ADEU LLM.exe`
   im Ordner starten – nützlich, wenn eine Firmenrichtlinie Programme in `AppData` sperrt.
3. In den **Einstellungen** den API-Key eintragen und „Verbindung testen“.

Unterschiede zu Android:

- **Daten** liegen pro Windows-Benutzer in `%APPDATA%\ADEU LLM` und bleiben bei neuen Versionen erhalten. API-Key,
  GitHub-Token und Umgebungen sind mit Windows (DPAPI) verschlüsselt – nur das eigene Windows-Konto kann sie lesen.
- **Im Hintergrund**: Schließt man das Fenster, läuft die App im Infobereich (unten rechts) weiter, Antworten werden
  fertig geschrieben und geplante Aufgaben laufen. Benachrichtigungen kommen als Windows-Benachrichtigung; ein Klick
  öffnet den Chat. Unter *Einstellungen › Windows* lässt sich „Mit Windows starten“ einschalten (ohne Admin-Rechte);
  verpasste Aufgaben werden beim nächsten Start nachgeholt.
- **Anhänge**: über „+“, per Drag & Drop ins Fenster oder mit Strg+V (Screenshot mit Win+Umschalt+S, kopierte Dateien).
  Statt der Kamera gibt es „Einfügen“.
- **PDFs** rendert Microsoft Edge (auf jedem Windows vorhanden) unsichtbar im Hintergrund – dieselbe Chromium-Engine wie
  auf Android. Ohne Edge/Chrome übernimmt ein eingebauter Renderer.
- **Vorlesen offline** nutzt die Stimmen von Windows; die KI-Stimme des Uni-Servers funktioniert wie auf Android.
- **Teilen** kopiert in die Zwischenablage; Dokumente lassen sich öffnen, im Ordner zeigen oder speichern unter.
- **Tastatur**: Enter sendet, Umschalt+Enter neue Zeile, Strg+N neuer Chat, Strg+B Seitenleiste, Strg+, Einstellungen,
  Esc zurück. Rechtsklick auf Chats und Nachrichten öffnet das Menü (wie langes Drücken auf Android).

### Dienst-PCs mit IT-Sperre

- **Geräteprüfung** (*Einstellungen › Modelle auf diesem Gerät*): Die genaue Prüfung liest Grafikkarte und Arbeitsspeicher
  über Windows-Systemfunktionen (JNA). Deren Hilfs-DLL entpackt die App jetzt in den eigenen Ordner
  `%LOCALAPPDATA%\ADEU LLM\jna` statt nach `%TEMP%` – DLLs in `%TEMP%` sperren AppLocker, WDAC und manche Virenscanner.
  Bleibt die Prüfung trotzdem gesperrt oder hängt sie, nutzt die App nach 8 Sekunden die Grundangaben von Java
  (Arbeitsspeicher, Kerne, Prozessor) und sagt das; die Empfehlungen sind dann vorsichtiger, installieren lässt sich
  alles. Vorher blieb die Seite bei „Gerät wird geprüft …“ stehen.
- **Projektordner und Ablageorte**: Die App schreibt als angemeldeter Benutzer – was im Explorer geht, geht auch in der
  App, auch auf Netzlaufwerken und in Google Drive. IT-Sperren betreffen das Ausführen von Programmen, nicht das
  Speichern. Ausnahme ist der **Überwachte Ordnerzugriff** von Microsoft Defender: Er blockiert unbekannte Programme in
  *Dokumente*, *Desktop* und *Bilder*. Dann meldet die App das beim Wählen des Ordners; Abhilfe ist ein Ordner außerhalb
  davon (z. B. `G:\Meine Ablage\…` oder ein Netzlaufwerk) oder die Freigabe von `ADEU LLM.exe` durch die IT.

## GitHub-Token

Unter <https://github.com/settings/personal-access-tokens/new> ein *Fine-grained token* erstellen:

- Repository access: die gewünschten Repositories (oder alle)
- Permissions: **Contents**, **Pull requests**, **Issues** → *Read and write*; **Metadata** → *Read*

Die App prüft das Token beim Eintragen und bei jedem Start. Erst mit einem gültigen Token erscheinen die
GitHub-Optionen (im Chat unter „+“, bei geplanten Aufgaben und in den GitHub-Einstellungen); ohne Token oder mit
einem abgelaufenen Token bleiben sie ausgeblendet.

Ein Fine-grained Token gilt nur für **einen** Besitzer (Resource owner): das eigene Konto *oder* eine Organisation.
Wer zusätzlich in Organisationen arbeitet, legt für jede ein eigenes Token an (beim Erstellen als *Resource owner* die
Organisation wählen; je nach Einstellung der Organisation muss ein Owner es bestätigen) und trägt es unter
*Einstellungen › GitHub › Weitere Tokens* ein. Die App zeigt bei jedem Token, welche Konten und Organisationen es
erreicht, listet die Repositorys aller Tokens zusammen und wählt pro Anfrage das passende.

Im Chat über „+“ › Repository ein oder mehrere Repositorys verknüpfen (oder einfach einen GitHub-Link schreiben). Sind
mehrere verknüpft, gibt der Agent bei jedem Werkzeug an, in welchem er arbeitet; jedes behält seinen eigenen
Arbeitsbranch. Für Agent-Aufgaben eignen sich Modelle mit Werkzeug-Unterstützung,
z.B. DeepSeek V4.1 Flash, Qwen3.8 27B, GLM-5.3, GLM-5.3 Flash oder MiniMax M3.

## Rate-Limit

Keys aus dem Self-Service-Portal haben laut [Doku](https://llm.scads.ai/docs/usage/api/) ein niedriges Limit an
Anfragen pro Minute (die genaue Zahl ist nicht veröffentlicht). Ein Agent mit vielen Werkzeugschritten erreicht es
schnell, weil jeder Schritt eine eigene Anfrage ist. Der Server antwortet dann mit HTTP 429 und `retry-after`; die App
wartet die angegebene Zeit ab und versucht es bis zu dreimal erneut (Hinweis im Chat).

**HTTP 503 „upstream connect error or disconnect/reset before headers“** kommt nicht von der App, sondern vom Gateway
des Uni-Servers: Es erreicht das Modell dahinter gerade nicht (Modell überlastet, startet neu oder wird gewartet). Die
App versucht es nach 5, 15 und 30 Sekunden automatisch erneut (ebenso bei 502/504 und abgebrochenen Verbindungen).
Hält der Fehler an, hilft nur warten oder ein anderes Modell wählen; der Status steht unter <https://llm.scads.ai/status>.

## Freigaben („Aktion erlauben?“)

Bevor der Agent etwas verändert, kann er nachfragen. *Einstellungen › Chat & Werkzeuge › Freigaben* (auch unter GitHub):

| Modus | Verhalten |
| --- | --- |
| Immer fragen (Standard) | Vor jedem Commit (Datei schreiben, ändern, löschen), Branch, Pull Request, Kommentar und schreibenden Web-Aufruf |
| Code-Änderungen automatisch | Dateien und Branches ohne Rückfrage (alles bleibt in der Git-Historie nachvollziehbar); gefragt wird nur bei Pull Requests, Issues, Kommentaren und schreibenden Web-Aufrufen |
| Alles automatisch | Nie nachfragen – für lange Coding-Sessions |

Für einen einzelnen Chat: im Dialog „Aktion erlauben?“ *In diesem Chat nicht mehr fragen* ankreuzen oder im Chat-Menü
(⋮) *Aktionen ohne Rückfrage* wählen. Solange das gilt, zeigt der Chat über dem Eingabefeld „Ohne Rückfrage“; ein Tipp
darauf schaltet die Rückfragen wieder ein. Geplante Aufgaben haben weiterhin ihre eigene Einstellung
„Schreibaktionen erlauben“.

## Wie der Agent arbeitet

- **Kontext**: Ergebnisse von Werkzeugen aus früheren Runden werden gekürzt mitgeschickt; passt ein langer Chat nicht mehr
  in das Budget des Modells (höchstens 100.000 Tokens), fasst das Modell den älteren Teil einmal zusammen – die letzten
  beiden Runden bleiben vollständig. Der Hinweis erscheint im Chat.
- **Schnell durch gleichbleibenden Anfang**: Der Systemprompt ändert sich innerhalb eines Chats nicht (nur das Datum
  steht darin, die Uhrzeit hängt an jeder Nachricht); der Uni-Server kann den Anfang der Anfrage so aus seinem Cache nehmen.
  Lange Anleitungen (Dokumente, Präsentationen, Formulare) kommen nur mit, wenn die Anfrage danach aussieht, sonst lädt
  das Modell sie bei Bedarf (`read_guide`).
- **Robuste Werkzeugaufrufe**: Kaputtes JSON (Codeblöcke, Zeilenumbrüche, abgeschnittene Antworten) wird repariert,
  fehlende Pflichtangaben und vertippte Werkzeugnamen werden dem Modell genau erklärt. Lesende Werkzeuge einer Antwort
  (Websuche, Dateien lesen, Wissenssuche) laufen gleichzeitig.
- **Ausweichmodell**: Erreicht der Server das gewählte Modell auch nach den Wiederholungen nicht (502/503/504), antwortet
  ein ähnliches Modell eines anderen Anbieters (*Einstellungen › Chat & Werkzeuge › Zuverlässigkeit*, abschaltbar).
- **Rate-Limit vorausschauend**: Die App merkt sich, ab wie vielen Anfragen pro Minute der Server ablehnt, und wartet
  vorher kurz – oder hält sich an einen eingestellten Wert.

## Agent-Schritte und Denkstufe

- Jeder Werkzeugaufruf ist ein Schritt. Nach *Max. Werkzeugschritte* (Standard 30) macht der Agent automatisch weiter
  (*Einstellungen › Chat › Danach automatisch weitermachen*), bis höchstens 500 Schritte. Führt er viermal hintereinander
  dieselbe Aktion aus, hält er an, damit er sich nicht im Kreis dreht.
- *Einstellungen › Chat › Denkstufe*: Modellstandard, Aus, Niedrig, Mittel, Hoch. Die App schickt dafür
  `reasoning_effort` (OpenAI-Parameter, z.B. gpt-oss) und `chat_template_kwargs` mit `enable_thinking`/`thinking`
  (Qwen, GLM, DeepSeek bei vLLM). Lehnt der Server die Parameter für ein Modell ab, antwortet es ohne sie mit seinem Standard. Ein Budget oder Kostenlimit
beschreibt die Doku nicht; ein höheres Limit vergibt das Team auf Anfrage an llm.scads.ai@tu-dresden.de.

## Selbst bauen

Voraussetzung: JDK 17, Android SDK mit NDK (Version in `app/build.gradle.kts`), git und CMake. Beim ersten Bauen werden
geladen bzw. gebaut: sherpa-onnx und die eingebauten Sprachmodelle (`speech/natives.gradle.kts`, feste Versionen mit
SHA-256), ffmpeg für Windows und `llama-server` für Android aus einem festen llama.cpp-Release (`app/llama.gradle.kts`,
zwei CPU-Varianten, ein paar Minuten, danach im Zwischenspeicher `build/llama-cache`). Ohne Internet: `-PskipNatives`
baut ohne Spracherkennung und lokale Modelle.

```bash
./gradlew assembleRelease   # app/build/outputs/apk/release/app-release.apk
./gradlew -p desktop test   # Brief, Vorlagen, Freigaben, PDF-Formulare, PowerPoint, Agent-Schleife, Wissen, Sandbox, Datenschutz, QTI,
                            # Transkript-Exporte, Interrater-Reliabilität, Geräteempfehlungen …
./gradlew :speech:test      # Spracherkennung: Warteschlange, Abschnitte, Ersetzungen, WAV …
```

Live-Tests gegen den Uni-Server (prüfen, ob die Modelle sich an die Vorgaben der App halten: ✨ schreibt nur den
Brieftext, Werkzeugaufrufe sind gültig, Folien haben das richtige Format) laufen nur mit einem API-Key:

```bash
LLM_API_KEY=sk-… ./gradlew -p desktop test --tests '*LiveApiTest*'   # optional LLM_MODEL=… für ein anderes Modell
```

Ein echter Chat mit einem lokalen Modell (inklusive Werkzeugaufruf und Datenschutzstufe) läuft mit einem gebauten
`llama-server` und dem Modell Qwen 3.5 2B:
`LLAMA_SERVER=…/llama-server LLAMA_MODELS=<Ordner mit Qwen3.5-2B-Q4_K_M.gguf> ./gradlew -p desktop test --tests '*LocalLlmLiveTest*'`.

Eine echte Transkription auf dem Rechner (lädt Whisper Base über die App, transkribiert die eingebaute Aufnahme mit
vier Sprechern): `TRANSCRIBE_LIVE=1 ./gradlew -p desktop test --tests '*TranscriptionLiveTest*'`.

Der Quellen-Check gegen Crossref lässt sich mit Internetzugang ebenso live prüfen:
`LIVE_WEB=1 ./gradlew -p desktop test --tests '*TeachingAndResearchTest*'`.

Der Starttest der Windows-Version läuft auch unter Linux mit virtuellem Bildschirm:
`xvfb-run -a ./gradlew -p desktop run --args="--selftest /tmp/selftest"` (öffnet alle Bildschirme, erstellt Brief,
PowerPoint und PDFs, Ergebnis in `selftest.txt`).

Windows-Version (JDK 17+; die Pakete für Windows entstehen auf Windows):

```bash
./gradlew -p desktop run                 # starten
./gradlew -p desktop createDistributable # desktop/build/compose/binaries/main/app/ADEU LLM/
```

`.github/scripts/windows-portable.ps1` macht daraus die Ein-Datei-EXE und die ZIP.

### Signaturschlüssel

Der Release-Schlüssel gehört **nicht** ins Repository: Wer ihn hat, kann „Updates“ bauen, die sich über die echte App
installieren und API-Keys und Chats lesen. Er liegt deshalb außerhalb des Codes:

- **Lokal:** `keystore.properties.example` nach `keystore.properties` kopieren (wird nicht eingecheckt) und Pfad,
  Alias und Passwörter eintragen. Alternativ die Umgebungsvariablen `KEYSTORE_PATH`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`
  und `KEY_PASSWORD` setzen. Ohne Schlüssel entsteht eine unsignierte APK, `release-local.sh` bricht mit einem Hinweis ab.
- **GitHub Actions:** Repository-Secrets `KEYSTORE_BASE64` (`base64 -w0 release.keystore`), `KEYSTORE_PASSWORD`,
  `KEY_ALIAS` und `KEY_PASSWORD` anlegen.

Bis Version 1.0.x lag der Schlüssel (`app/release.keystore`, Passwort `llmhub-release`) im Repository und steht damit
weiter in der Git-Historie. Wer ihn weiter nutzt, behält Updates ohne Neuinstallation, der Schlüssel gilt aber als
öffentlich. Sicherer ist ein neuer Schlüssel
(`keytool -genkeypair -v -keystore release.keystore -alias llmhub -keyalg RSA -keysize 4096 -validity 10000`); die App
muss dann einmal deinstalliert und neu installiert werden (vorher *Einstellungen › Sicherung & Umzug* nutzen, damit
Chats und Einstellungen erhalten bleiben).

## Aufbau

| Pfad | Inhalt |
| --- | --- |
| `net/LlmClient.kt` | Chat-Streaming (SSE) mit Token-Zählung, Modelle, Bilder, TTS, STT |
| `net/GitHubClient.kt` | GitHub-REST-API mit mehreren Tokens (Auswahl nach Besitzer des Repositorys) |
| `agent/AgentRunner.kt` | Agent-Schleife (Modell → Werkzeuge → Modell …) für Chat und geplante Aufgaben, Ausweichmodell, parallele Werkzeuge |
| `agent/ContextManager.kt`, `agent/ArgRepair.kt`, `net/RateLimiter.kt` | Kontext kürzen/zusammenfassen, Werkzeugargumente reparieren, Anfragen drosseln |
| `agent/AssistantTools.kt` | Anleitungen (`read_guide`), Gedächtnis, Wissenssuche, JavaScript-Sandbox (`agent/ScriptRunner.kt`) |
| `agent/FieldAssist.kt`, `agent/FieldSuggestions.kt` | ✨ in Vorlagenfeldern; Vorschläge aus Verlauf, Absender, Gedächtnis und Wissen |
| `agent/Memory.kt`, `agent/MemoryLearner.kt`, `ui/MemoryUi.kt` | Gedächtnis im System-Prompt jeder Anfrage, frühere Chats (`search_chats`), Dazulernen nach dem Chat, Knopf, Dialoge und Einstellungen |
| `agent/ModelRouter.kt` | Automatische Modellwahl (auf dem Gerät, ohne zusätzliche Anfrage) |
| `ui/VoiceMode.kt` | Sprachmodus: Zuhören bis zur Sprechpause, Antwort vorlesen, wieder zuhören |
| `ui/LoraFigure.kt` | Lora: Zeichnung, Aktionen und Ablauf (welche Aktion wann, passend zum aktuellen Schritt) |
| `ui/LoraUi.kt` | Arbeitskarte mit Lora und eingeklappte Arbeitsschritte im Chat, Lora mit Sprechblase auf der Startseite |
| `ui/ChatAutoScroll.kt` | Mitlaufen beim Streamen, ohne die Lesestelle zu verschieben |
| `update/` | Automatische Updates: `version.json` lesen, Download mit SHA-256, Popup (`ui/UpdatePrompt.kt`); Windows tauscht den App-Ordner (`desktop/…/update/UpdateApply.kt`), Android öffnet den Installer |
| `agent/WorkFolder.kt` | Projektordner: Schreibtest, Ablage erstellter Dateien in `Ausgabe`, verständliche Fehlermeldungen |
| `docs/LetterBuilder.kt` | Brief direkt aus den Feldern |
| `docs/PptxWriter.kt` | PowerPoint im TUD-Design (Office Open XML ohne Bibliothek) |
| `docs/DocumentSources.kt`, `docs/OfficeFiles.kt` | Quellen für `edit_document` und Platzhalter, E-Mail-Entwürfe (.eml), Termine (.ics) |
| `data/Knowledge.kt`, `data/Backup.kt` | Wissenssammlung und Gedächtnis, Sicherung & Umzug |
| `ui/KnowledgeUi.kt`, `ui/BackupUi.kt`, `ui/AgentProtocol.kt`, `ui/FilePicker.kt` | Gedächtnis, Wissen, Projekte, Sicherung, Agent-Protokoll, Dateiauswahl je Plattform |
| `agent/ChatRuns.kt`, `agent/AgentService.kt` | Laufende Chat-Antworten unabhängig vom Bildschirm; Vordergrunddienst, damit sie bei geschlossener App fertig werden |
| `agent/Tools.kt` | Gemeinsame Werkzeug-Infrastruktur (Function Calling) |
| `agent/GitHubTools.kt`, `agent/WebTools.kt`, `agent/DocumentTools.kt` | Werkzeuge: GitHub, Web/News-Suche, Dokumente & Logos |
| `agent/LocalFolderTools.kt`, `files/LocalText.kt` | Agent in freigegebenen Ordnern auf dem PC (lesen, suchen, schreiben, Papierkorb) |
| `docs/LiveDoc.kt` | Dokumente des Editors (Art, Tabs, Versionen, Kommentare), Speicherorte mit Markdown-Kopie und Abgleich |
| `docs/SheetEngine.kt`, `docs/SlideDeck.kt`, `docs/OfficeImport.kt` | Tabellen mit Formeln, Folienmodell, Import von .docx/.pptx/.xlsx/.csv |
| `ui/DocsEditor.kt`, `ui/DocsText.kt`, `ui/SlidesEditor.kt`, `ui/SheetEditor.kt`, `ui/DocsAssistant.kt`, `ui/DocsList.kt` | Editor-Rahmen, Text, Folien, Tabellen, KI-Mitarbeit, Startseite der Dokumente |
| `data/Pseudonymizer.kt`, `ui/PrivacySettings.kt` | Pseudonymisierung (Erkennung, Platzhalter, Rückersetzung) und Einstellungen dazu |
| `agent/ReferenceCheck.kt`, `ui/CheckDialogs.kt` | Quellen-Check (Crossref, OpenAlex, Google Books), Dialoge für Quellen und Barrierefreiheit |
| `docs/QtiWriter.kt` | Tests für OPAL/ONYX als IMS QTI 2.1 |
| `docs/AccessibilityCheck.kt`, `docs/PdfAccessibility.kt`, `docs/Contrast.kt` | Barrierefreiheit prüfen, PDF-Metadaten, Farbkontrast |
| `ui/SettingsOverview.kt`, `ui/PlatformUi.kt` | Gegliederte Einstellungsübersicht; Rechtsklick (Windows) bzw. langes Drücken (Android) |
| `data/ChatTree.kt` | Chats als Baum (Versionen beim Bearbeiten/Neu generieren) |
| `data/Library.kt`, `data/Stores.kt` | Ordner, Vorlagen (Bereiche, Platzhalter), Umgebungen, Logos und deren Speicherung |
| `data/TemplateLibrary.kt` | Eingebaute Vorlagen, Favoriten-Vorschläge, Teilen/Importieren |
| `ui/TemplateUi.kt`, `ui/TemplatesScreen.kt` | Vorlagen durchsuchen, ausfüllen, verwalten |
| `ui/Approvals.kt` | Freigabe-Modi und Dialog „Aktion erlauben?“ |
| `docs/PdfForms.kt`, `docs/PdfFormModel.kt` | PDF-Formulare lesen und ausfüllen (Android: pdfbox-android, Windows: PDFBox 3) |
| `resources/tud/` | Offizielles TUD-Logo (blau/weiß) und Noto Sans für den Dokumentstil „TU Dresden“ |
| `data/ModelCatalog.kt` | Modellbeschreibungen, Einsatzzwecke und Einstufung (empfohlen / weitere / ausgeblendet) |
| `data/ScheduledTask.kt`, `tasks/TaskScheduler.kt` | Geplante Aufgaben (WorkManager) |
| `tasks/Notifier.kt` | Benachrichtigungen: fertige Antworten, Aufgaben-Ergebnisse, Rückfragen |
| `data/UsageStore.kt` | Verbrauch pro Tag und Modell |
| `files/AttachmentReader.kt` | Text aus PDF, Office- und Textdateien |
| `docs/` | Dokumente: HTML/CSS → PDF (WebView), Word (.docx), HTML, Text; Stile und Logos |
| `audio/` | Aufnahme, Wiedergabe und Vorlesen (Server oder offline) |
| `ui/AppRoot.kt`, `ui/AppDrawer.kt` | Navigation und Seitenleiste |
| `ui/ChatScreen.kt`, `ui/ChatMessages.kt`, `ui/ChatComposer.kt` | Chat-Oberfläche |
| `ui/Theme.kt`, `ui/ThemePlatform.kt`, `ui/Design.kt`, `ui/AppearanceSettings.kt` | Stile (ADEU, TU Dresden, Office, Hoher Kontrast …), Bausteine des gemeinsamen Designs, Darstellung mit Vorschau |
| `speech/` | Spracherkennung (früher Whisper-App, Java): Warteschlange, Whisper/Parakeet über sherpa-onnx, Sprachabschnitte, Sprecher, Suche; `natives.gradle.kts` lädt die nativen Teile |
| `transcribe/` | Anbindung an die App: `Transcription` (Zustand, Uni-Server-Erkennung, Kurz-Erkennung für Diktate), `TranscriptExport` (Word/PDF, Dresing/Pehl, MAXQDA/f4, SRT/VTT), `SpeechHost` je Plattform |
| `ui/TranscriptsScreen.kt`, `ui/TranscriptScreen.kt`, `ui/TranscriptStart.kt`, `ui/TranscriptionSettings.kt` | Transkription: Übersicht, Aufnahme, Start-Dialog, Transkript mit Player, Einstellungen |
| `local/` | Lokale Sprachmodelle: `LocalCatalog` (Modelle, Hardware-Empfehlung), `LocalLlm` (Installieren, Server starten/stoppen), `LlamaHost` je Plattform; `app/llama.gradle.kts` baut llama-server für Android |
| `data/Privacy.kt` | Datenschutzstufen der Projekte durchsetzen |

### Windows-Version (`desktop/`)

`desktop/` ist ein eigener Gradle-Build mit Compose Desktop. Er kompiliert alle Dateien aus `app/src/main/java` mit,
außer denen, die unter `desktop/src/main/kotlin` im gleichen Pfad eine Windows-Fassung haben (z.B. `ui/ChatScreen.kt`,
`docs/HtmlPdf.kt`, `tasks/TaskScheduler.kt`). Änderungen an Logik, Werkzeugen und den meisten Bildschirmen gelten so
automatisch für beide Versionen. Kleine Stellvertreter für Android-Klassen (`android/content/Context.kt`, `Base64`, `Toast`,
`LocalContext`) sorgen dafür, dass gemeinsamer Code unverändert kompiliert.

| Pfad | Inhalt |
| --- | --- |
| `Main.kt` | Fenster, Symbol im Infobereich, Benachrichtigungen, Tastenkürzel, Drag & Drop |
| `SelfTest.kt` | Starttest für CI (`--selftest <Ordner>`): alle Bildschirme, Dokumente, Verschlüsselung |
| `desktop/Paths.kt`, `desktop/Secrets.kt`, `data/FilePrefs.kt` | Datenordner, DPAPI-Verschlüsselung, Einstellungen |
| `desktop/Autostart.kt`, `desktop/SingleInstance.kt` | „Mit Windows starten“, nur eine laufende App |
| `docs/HtmlPdf.kt` | HTML → PDF mit Edge/Chrome (headless), sonst openhtmltopdf |
| `launcher/Launcher.cs` | Ein-Datei-Starter: entpackt die App nach `%LOCALAPPDATA%` und startet sie |

