# Lora – Der freundliche Helfer für alle Wünsche in Forschung, Lehre und Verwaltung an der TU Dresden

Eigene Android-App (und portable Windows-Version) für die OpenAI-kompatible LLM-Schnittstelle unter `https://llm.scads.ai/v1`
(API-Key aus dem TU-Dresden-Self-Service). Inoffiziell, nicht mit der Universität verbunden.

**Name**: Die App heißt seit Version 1.0.44 **Lora** – wie ihre Helferin (bis 1.0.43 „ADEU LLM“). Sichtbar heißt alles
Lora (Fenster, Taskleiste, Android-Symbol, Startanimation mit Slogan, Updates, Dokument-Metadaten). **Technische Namen
bleiben bewusst**: `ADEU LLM.exe`, die Datenordner `%APPDATA%\ADEU LLM` und `%LOCALAPPDATA%\ADEU LLM`, die Dateien der
Releases (`ADEU-LLM-x.y.z-…`) und der Autostart-Eintrag – ältere Versionen suchen beim Update genau diese Namen, und
vorhandene Daten bleiben so, wo sie sind. Ein Umzug auf neue technische Namen wäre ein eigener Schritt mit Migration.

## Funktionen

- **Gemeinsames Design mit wählbaren Stilen**: Standard ist **ADEU** (die Optik der früheren Whisper-App: helle Flächen,
  weiße Karten mit feinem Rand, Gruppenüberschriften). Daneben seriöse Stile für den Dienst: **TU Dresden** (Farben des
  Corporate Designs), **Office** (nüchtern wie Microsoft 365) und **Hoher Kontrast** (barrierearm, WCAG AAA). Unter
  *Weitere Stile* bleiben Claude, ChatGPT, Gemini und Material You. *Einstellungen › Darstellung* zeigt jeden Stil als
  kleine Vorschau; dort lässt sich auch die Startanimation ausschalten.
- **Logo und Startanimation**: Das Logo zeigt Lora, das Maskottchen, beim Lesen – im App-Violett mit weißem Gesicht auf
  einer hellen Kachel. Beim Start erscheint die Kachel, Lora hüpft hinein und blättert eine Seite um (abschaltbar unter
  *Einstellungen › Darstellung*). App-Symbole für Windows (`.ico`, Infobereich) und Android (Launcher, Benachrichtigung)
  entstehen aus derselben Zeichnung (siehe *Logo ändern* unten).
- **Chat im ADEU-Stil**: Antworten sind ruhiger Text; Kästen für Code, Tabellen und Hinweise (`> [!NOTE]` oder
  `> **Hinweis:**`) haben einen feinen Rand und einen Farbakzent statt grauer Flächen. Links zeigen ihre Domain als kleine
  Marke, bloße Adressen erscheinen nur als Marke, Quellen wie `[1]` werden zu nummerierten Marken, die auf die
  Quellenliste am Ende der Antwort verlinken. Anhänge, Dokumentkarten, die Literaturliste und Fehler sehen einheitlich
  aus; Lora signiert jede Antwort klein neben dem Modellnamen. Gilt für alle Stile, Hoher Kontrast bleibt WCAG AAA.
- **Aufgeräumte Seitenleiste**: oben *Neuer Chat* und bis zu vier Bereiche (Standard: *Erstellen*, *Literatur*,
  *Transkription*, *Dateien*), darunter ausklappbar **„Mehr“** mit *Bilder erzeugen*, *Geplante Aufgaben* und allen
  eingeschalteten Werkzeugen (auf-/zugeklappt bleibt gespeichert). Rechtsklick bzw. langes Drücken holt einen Bereich
  nach oben, schiebt ihn unter „Mehr“ oder **blendet ihn aus** (alle außer *Erstellen* und *Dateien*; auch unter
  *Einstellungen › Werkzeuge › Seitenleiste*) – Ausgeblendetes bleibt über Strg+K und Lora erreichbar. Darunter Projekte,
  angeheftete Chats und die Chats nach Tagen. Modellübersicht, Modellvergleich, Verbrauch und die lokalen Modelle stehen
  in den Einstellungen unter *Modelle* bzw. *Daten*.
- **Einschaltbare Werkzeuge** (*Einstellungen › Werkzeuge*): große Werkzeuge haben je einen Schalter und sind anfangs
  aus; eingeschaltete erscheinen unter „Mehr“, zugehörige Modi (z. B. *Lernspiel*) erst dann in `/`, Strg+K und Loras
  Landkarte. Passt eine Anfrage zu einem ausgeschalteten Werkzeug, schlägt Lora es über dem Eingabefeld vor („Quiz“ →
  *Lernspiele*). Einschaltbar sind der *PDF-Werkzeugkasten*, der *Tresor*, *Lernspiele*, *Stapel & Massenkontrolle*, die
  *Antrags-Werkstatt* und (Windows) *Mail & Kalender* und der *Videoeditor*. Gestalten (Poster, Flyer …) ist kein Werkzeug mehr, sondern Teil von *Erstellen*. Der Browser
  neben dem Chat ist ein Schalter unter *Einstellungen › Windows*.
- **Nachladbare Erweiterungen** (Windows, AP-25, *Einstellungen › Werkzeuge › Erweiterungen*): große Teile, die nicht
  alle brauchen, kommen nicht mit jedem Update, sondern werden beim ersten Gebrauch einmalig eingerichtet – aus derselben
  Quelle wie die Updates (GitHub oder der Ordner auf dem Netzlaufwerk), mit Prüfsumme, nach
  `%LOCALAPPDATA%\ADEU LLM\tools\<id>`. Eine neuere Fassung im Release aktualisiert sie mit der Update-Prüfung; *Entfernen*
  gibt den Platz wieder frei. Erste Erweiterung ist das **Videoeditor-Paket** (ca. 40 MB: ffprobe für genaue Angaben zu
  Videos, Sprachmodell RNNoise für die Rauschminderung, seit Paket 2 die Gesichtserkennung YuNet); der Videoeditor bietet es beim ersten Öffnen an und funktioniert
  auch ohne. Die **Browser-Engine** (Chromium, AP-14) steht daneben und lässt sich ebenso entfernen.
- **Lora, die Helferin im Kern der App** (AP-33 bis AP-36):
  - **Lora-Panel** statt Popup: am PC (breite Fenster) eine Seitenleiste rechts, die beim Navigieren offen bleibt – die
    App wird schmaler statt verdeckt –, auf dem Handy ein Blatt von unten. Das Gespräch bleibt bis *Neu beginnen*,
    Antworten erscheinen schrittweise und formatiert, *Abbrechen* stoppt, Fehler sind freundlich formuliert.
  - **Kontext**: Lora weiß, wo du bist, und zeigt passende Vorschläge („Das passt hier“): im Chat *Chat zusammenfassen*
    und *Als Word-Dokument* (landen direkt im offenen Chat), in den Einstellungen *Hell oder dunkel*, in der Literatur
    *Exzerpt erstellen* und *Zitierstil* usw. Feste Ziele öffnen sofort, ohne Anfrage.
  - **Ergebnis-Karten**: Orte mit Pfad und *Öffnen*, Einstellungen mit Vorher → Nachher, *Umstellen* und **Rückgängig**,
    Werkzeuge mit Beschreibung, Fristen als Kalenderkarte, frühere Chats zum Öffnen.
  - **Neue Fähigkeiten**: frühere Chats durchsuchen („Wo hatte ich das mit der Wübben-Stiftung?“, „meine letzten Chats“ –
    auf dem Gerät), **Fristen und Erinnerungen in den Kalender** (ganztägiger Termin mit Erinnerung, öffnet in Outlook
    bzw. der Kalender-App), mehrere Einstellungen auf einmal.
  - **Saubere Übergabe an den Chat**: Über der Frage steht eine Karte *Von Lora übergeben · Modell · Grund*; was Lora
    schon geklärt hat, geht als versteckte Anweisung mit (aufklappbar) statt sichtbar in deinem Text. **Standardmodell
    für Übergaben ist DeepSeek V4.1 Flash** (*Einstellungen › Lora*); nur in Sonderfällen – Programmieren, sehr
    schwierige Analysen, riesige Textmengen – wählt Lora selbst ein anderes und nennt den Grund. Der Chat behält das
    Modell (kein automatischer Wechsel danach). *Übergaben erst zeigen* legt die Frage nur ins Eingabefeld.
  - **Lora im Chat**: *Nächste Schritte* unter der letzten Antwort, auf dem Gerät erkannt – *Frist 30.11. in den
    Kalender* (auch aus Tabellen mit Spalte „Frist“), *Tabelle als Excel*, *In Literatur übernehmen* (DOIs über
    Crossref), *Als Word-Dokument*. Beim Tippen zeigt Lora über dem Eingabefeld den passenden Ort der App („dunkles
    Design“ → *Darstellung › Helligkeit*), nie bei echten Fragen. Die Arbeitskarte beschreibt den Schritt verständlich
    („Sucht im Web: „…““, „Liest dfg.de“, „Schreibt „Antrag““); ist eine Antwort fertig, jubelt Loras Signatur kurz.
  - **Überall erreichbar**: **Strg+L** (Windows; Literatur jetzt Strg+Umschalt+L), der Knopf **„Frag Lora“** oben im
    Chat (Loras Gesicht in einer violetten Pille, auf schmalen Fenstern mit Chat-Titel nur das runde Gesicht), ein Tipp
    auf Lora auf der Startseite, Strg+K › *Lora fragen*, die schlafende Lora unten rechts (verdeckt keine Einträge), Android:
    langes Drücken aufs App-Symbol › *Lora fragen*.
  - **Nahaufnahme statt Miniatur**: Wo wenig Platz ist – Knopf in der Kopfleiste, schlafende Lora unten rechts, kleine
    Karten und Chips –, zeigt die App Loras **Gesicht in Nahaufnahme** (Kopf mit Antenne und Visier) statt der ganzen,
    dort winzigen Figur. Sie blinzelt, atmet, folgt dem Mauszeiger mit den Augen, winkt (Hand am Rand) oder schläft
    (geschlossene Augen, aufsteigende „z“); mit der Maus über der schlafenden Lora wacht sie auf und winkt
    (`drawLoraFace` in `ui/LoraFigure.kt`, `LoraFace`/`LoraFaceStill` in `ui/LoraUi.kt`).
  - **Wege mit Animation**: Führt Lora irgendwohin, fliegt sie aus ihrem Panel dorthin – ins Eingabefeld des Chats, zum
    Eintrag der Seitenleiste oder zur Einstellung, die dann leuchtet – mit einer Spur kleiner Sterne und einem Ring bei
    der Landung.
- **Lora handelt in Werkzeugen** (AP-38, Runde 2): Lora führt nicht nur hin, sie **bedient die Werkzeuge selbst** –
  im Lora-Panel, mit sichtbaren Schritten („Fügt 2 PDFs zusammen …“), einer Ergebniskarte mit **Rückgängig** und *Öffnen*.
  Alles, was verschiebt, löscht (Papierkorb) oder überschreibt, zeigt vorher eine **Vorschau vorher → nachher** und wartet
  auf *Ausführen*. Jedes Werkzeug meldet seine Aktionen in einem Register (`agent/ToolActions.kt`); Lora bekommt nur die
  des offenen Bereichs und weiß, was ausgewählt ist. Jede Aktion steht im **Protokoll der Lora-Aktionen**
  (*Einstellungen › Lora*, Datei `lora-aktionen.log`). **Datenschutz** gilt auch für Aktionen: Was Inhalte an ein Modell
  gibt (nach Inhalt umbenennen, Handout, schreiben, kürzen), nimmt nur ein Modell, das die Datenschutzstufe des Projekts
  erlaubt – im Tresor nur den Uni-Server, bei *Nur auf diesem Gerät* nur ein lokales Modell (sonst ohne Modell bzw. gar
  nicht); Dateien des Tresors erreichen keine Aktion, und *Nur auf diesem Gerät* lädt nichts in die Nextcloud.
  - **Dateien**: aufräumen (Unterordner nach Dateiart, Semester „WiSe 2026-27“ oder Monat), nach Inhalt umbenennen,
    umbenennen, verschieben, Schlagwörter vergeben, Duplikate finden und in den Papierkorb legen.
  - **PDF-Werkzeugkasten**: öffnen, zusammenfügen, teilen, Seiten löschen/drehen/sortieren, Texterkennung, verkleinern,
    schwärzen (immer mit Vorschau) und umwandeln (Word, Markdown, Text) – Änderungen am offenen PDF mit dessen eigenem
    *Rückgängig*.
  - **Dokument**: schreiben an der Cursorstelle, Abschnitte als Vorschläge überarbeiten, Tabelle einfügen, Absatz als
    Überschrift (siehe AP-47).
  - **Tabelle**: Zellen füllen (Werte und Formeln), Spalte mit Formel für jede Zeile (`=B{r}*0,5+C{r}*0,5`), nach einer
    Spalte sortieren, Daten bereinigen (Leerzeichen, leere und doppelte Zeilen – mit Vorschau), Summe und Mittelwert
    unter die Zahlenspalten. „Sortiere nach Klausur absteigend“ erkennt Lora ohne Modell.
  - **Präsentation**: Folie anlegen (Titel, Stichpunkte, Layout), Folie ändern (auch Sprechernotizen), Folie löschen
    (mit Vorschau), Folien aus einer Gliederung, volle Folien teilen.
  - **Video** (PC): Videoprojekt aus einer Aufnahme anlegen, schneiden (an einer Zeit oder der Abspielposition), Clip
    entfernen, Titel/Bauchbinde/Einblendung/Abspann im TUD-Design, Untertitel aus der Transkription (transkribiert vorher,
    wenn nötig), Pausen und Füllwörter herausschneiden, *Mit Lora kürzen* („auf 10 Minuten, Beispiele behalten“, mit
    Vorschau der neuen Länge) und Export als MP4 in *Erstellt* (OPAL, YouTube, wie das Projekt). Bei offenem Editor
    laufen die Aktionen über dessen Verlauf – Strg+Z nimmt sie ebenfalls zurück.
  - **Werkzeugübergreifend**: Aufnahme transkribieren (mit Zeitmarken wie in *Transkription*, das Transkript steht dort
    und als Textdatei daneben), Handout als Word aus einer Datei, Dateien in die Nextcloud.
  - Klare Wünsche erkennt Lora **auf dem Gerät** ohne Modell: „PDF bearbeiten“ (öffnet die ausgewählte PDF im
    Werkzeugkasten), „Füge die offenen PDFs zusammen“, „Finde Duplikate“, „Räum diesen Ordner auf“, „verkleinern“,
    „Texterkennung“. In *Dateien* hat jede PDF zudem *⋮ › Im PDF-Werkzeugkasten bearbeiten*.
- **Ein Gedächtnis für Lora** (AP-39): *Einstellungen › Lora › Gedächtnis – was Lora über dich weiß* zeigt **Vorlieben je
  Werkzeug** (Dateien, PDF, Dokumente, Tabellen, Folien, Video, Literatur, Chat) – jede änderbar und löschbar, dazu
  *Alles vergessen* (mit Rückgängig). Lora **fragt immer vorher**: Hat man selbst „nach Semester“ aufgeräumt oder „für
  OPAL“ exportiert, erscheint *Soll ich mir merken, dass …? · Merken · Nein*; „Merk dir, dass ich Handouts zweispaltig
  möchte“ geht genauso. Gemerktes ist danach der **Standard ihrer Aktionen** (aufräumen ohne Angabe → nach Semester,
  „(wie gemerkt)“; Export-Dialog mit der gemerkten Vorgabe) und steht in Loras Anweisungen und – als Kategorie
  *Vorlieben* – in jedem Chat. Aus dem Tresor wird nie etwas gemerkt (auch das Dazulernen nach dem Chat und *remember*
  bleiben dort aus). Die Einträge liegen im normalen Gedächtnis und gehen mit dem Sync mit.
- **Abläufe über Werkzeuge** (AP-40): Mehrschrittige Aufträge – „Aus dieser Aufnahme: transkribieren, Untertitel, auf 10
  Minuten kürzen, Handout als Word“ oder „Alle Abgaben zusammenfügen, schwärzen, in die Nextcloud“ – **plant Lora zuerst**
  (`agent/LoraPlan.kt`): Die Karte zeigt die Schritte mit Werkzeug, geschätzter Dauer und Datenschutzhinweis, darunter
  *Los · Ändern · Abbrechen* (*Ändern*: Schritte abwählen oder in Worten sagen, was anders sein soll). Danach laufen die
  Schritte nacheinander mit den Aktionen von AP-38; jeder Schritt arbeitet mit den Dateien des vorigen weiter.
  Fortschritt im Panel, *Pause/Fortsetzen*; riskante Schritte warten mit ihrer Vorschau auf *Ausführen*, ein
  fehlgeschlagener lässt sich *wiederholen* oder *überspringen*. Lange Schritte laufen im Hintergrund weiter (auch bei
  geschlossenem Panel) und melden sich am Ende per Benachrichtigung; Zwischenergebnisse stehen verknüpft an ihrem
  Schritt. Abläufe liegen in `lora-ablaeufe.json` – ein laufender **setzt nach einem Neustart der App fort**.
- **Leuchtrahmen überall und Touren** (AP-41): Jede Einstellung mit Schalter und die wichtigen Knöpfe der Werkzeuge (Suche,
  „+“, Orte und Sortieren in *Dateien*; Öffnen, Modi, Zusammenfügen, Speichern im PDF-Werkzeugkasten; Lora-Feld,
  TU Vorlagen und *Neue Vorlage* in *Erstellen*; Projekt, Titel, Untertitel, Rohschnitt und Export im Videoeditor) haben
  einen Leuchtrahmen, zu dem Lora führen kann; ein Test prüft, dass jedes Ziel der Landkarte seinen Rahmen hat. Beim
  ersten Öffnen von *Dateien*, *Erstellen*, PDF-Werkzeugkasten und Videoeditor bietet Lora eine **kurze Tour** an (3–6
  Schritte: Leuchtrahmen + eine Zeile, *Zurück · Überspringen · Weiter*). Touren sind Daten (`ui/LoraTour.kt`), englisch
  über `en.tsv`; abschaltbar unter *Einstellungen › Lora › Touren*, wieder aufrufbar über Strg+K („Tour“) oder „Zeig mir,
  wie das geht“. Bei *Ruhigere Bewegungen* fliegt Lora nicht mit.
- **Lora-Dateidialog** (AP-42, PC): Öffnen, Speichern unter und Ordnerwahl laufen überall über einen eigenen Dialog im
  Lora-Stil statt der alten Windows-/Java-Dialoge – Seitenspalte wie im Tab *Dateien* (*Zuletzt · Markiert · Lora-Ordner ·
  Hochgeladen · Erstellt · Nextcloud · Ablageorte*, darunter *Dieser PC* mit Laufwerken, Desktop, Dokumente, Downloads,
  Bilder und Cloud-Ordnern wie OneDrive), Pfadleiste (auch zum Eintippen), Suche, Filter nach Dateityp, Liste oder Raster
  mit Vorschaubildern (Bilder, erste PDF-Seite), Mehrfachauswahl mit Strg, neuer Ordner, Dateien aus dem Explorer
  hineinziehen. Der zuletzt benutzte Ort je Zweck (Bilder, Videos, Dokumente, Speichern je Dateityp) wird gemerkt.
  Tastatur: Pfeile, Enter, Rücktaste, Strg+A, Tippen springt; Screenreader-Beschriftungen. Aus der Nextcloud geöffnete
  Dateien werden geladen, *Speichern unter* in die Nextcloud lädt die Datei nach dem Schreiben hoch. Auch die Frage nach
  einem verschobenen Lora-Ordner beim Start ist jetzt ein Fenster im Lora-Stil. Dateien und Ordner lassen sich im Dialog
  **umbenennen** (F2, Rechtsklick, Knopf) und **löschen** (Entf, Rechtsklick, Knopf; im Lora-Ordner in dessen Papierkorb,
  sonst in den Windows-Papierkorb).
- **Fließtext-Editor für Dokumente** (AP-44, mit dem Text-Kern aus AP-43): Text-Dokumente bearbeitest du wie in Google
  Docs als **durchgehenden Text** – ein Cursor, Markieren über Absätze hinweg, Formatieren mit Symbolleiste, Menüs
  (*Datei · Bearbeiten · Ansicht · Einfügen · Format · Extras*) und Tastenkürzeln (Strg+B/I/U, Strg+Alt+0…4 für Normaltext
  und Überschriften, Strg+Umschalt+7/8/9 für Listen, Tab/Umschalt+Tab für Einzüge, Strg+\ entfernt Formatierung).
  Formatvorlagen (Normaler Text, Überschrift 1–4, Zitat, Listen, Checkliste), Schriftart und -größe, fett, kursiv,
  unterstrichen, durchgestrichen, hoch-/tiefgestellt, **Textfarbe, Hervorhebung**, Links, **Ausrichtung** (links,
  zentriert, rechts, Blocksatz). Markdown-Kürzel beim Tippen (`# `, `- `, `1. `, `[] `, `> `). *Seitenansicht* mit echten
  A4-Seiten (seit 1.0.56, Kopf-/Fußzeile und Seitenzahl je Blatt) oder *Ohne Seiten*, Zoom 75–150 %. Tabellen, Bilder, Formeln, Inhaltsverzeichnis, Fußnoten und
  Seitenumbrüche stehen als eigene Blöcke zwischen dem Text (*Einfügen*-Menü, Tabelle über ein Raster). Gespeichert wird
  weiter Markdown – was Markdown nicht kann, als kleine HTML-Marken (`<u>`, `<mark>`, `<span style="color:…">`,
  `<!--a:c-->`), die alle Exporte (Word, PDF, Chat) verstehen. *Änderungen nachverfolgen* zeigt der Fließtext selbst
  (seit 1.0.56); nur wenn sich dabei Tabellen, Bilder oder Formeln geändert haben, oder solange Änderungen der KI zur
  Prüfung anstehen, erscheint die bisherige Blockansicht mit Annehmen/Ablehnen.
- **Word-Treue** (AP-45): Beim Öffnen einer .docx kommen jetzt auch Unterstreichung, Textfarbe, Hervorhebung,
  hoch-/tiefgestellt, abweichende Schriftart und -größe und die Ausrichtung mit. Beim Speichern schreibt die App neue,
  geänderte oder gelöschte Absätze **in das Original** (`docs/text/DocxWriter.kt`): Unveränderte Absätze und Tabellen
  bleiben Zeichen für Zeichen erhalten – mit Formatvorlagen, Lesezeichen, Feldern, Inhaltssteuerelementen –, ebenso
  Kopf- und Fußzeilen, Nummerierungen, Bilder und alle anderen Teile der Datei; neue Überschriften nehmen die
  Formatvorlagen des Originals. Nur wenn das nicht sicher geht (z. B. ein neues Bild), entsteht die Datei wie bisher neu.
- **Lora schreibt im Dokument** (AP-47): **„Hilf mir schreiben“** (Strg+J, ✨ in der Symbolleiste, *Extras* oder im
  leeren Dokument) – Lora schreibt sichtbar an der Cursorstelle, farbig hinterlegt, mit Überschriften als Formatvorlagen;
  danach *Übernehmen · Neu · Verwerfen*. **Überarbeiten als Vorschlag** (*Extras › Markierung mit Lora überarbeiten* oder
  Lora: „Überarbeite Abschnitt Methode kürzer“): die Änderungen erscheinen wie bei *Änderungen nachverfolgen* rot/grün im
  Text, eine Leiste führt von Vorschlag zu Vorschlag mit *Annehmen*, *Ablehnen* und *Alle …*; Loras Begründung steht als
  Kommentar am Rand. Die Formatierung des übrigen Textes bleibt.
- **Gliederung und Formatvorlagen** (AP-46, seit 1.0.54): Links im Editor steht die **Gliederung** (wie in Docs,
  *Ansicht › Gliederung*): Klick springt zur Überschrift, der Griff ⠿ verschiebt per Ziehen den **ganzen Abschnitt** samt
  Unterabschnitten (oder ⋮ › *nach oben/unten*), Pfeile klappen Ebenen ein. Neue **Formatvorlagen**: Titel, Untertitel,
  Bildunterschrift, Code und Literatur (hängender Einzug) zu Normaltext, Überschrift 1–4 und Zitat. **Vorlagensätze**
  *TU Dresden* (Noto Sans, TUD-Blau), *Wissenschaftlich* (Times New Roman, 1,5 Zeilen), *Schlicht* und *Standard*
  (Formatvorlagen-Menü oder *Format*): der Wechsel formatiert das ganze Dokument um, Word- und PDF-Export folgen.
  „*Überschrift 1* an Markierung anpassen“ übernimmt Schrift, Größe und Farbe des markierten Textes in die Formatvorlage,
  *Als Standard für neue Dokumente speichern* merkt sich den Satz. **Format übertragen** (Pinsel) und `**fett**`/`*kursiv*`
  beim Tippen. Gespeichert wird als Kommentarzeilen (`<!-- vorlagen: wissenschaftlich -->`, `<!-- vorlage h1: … -->`); Word
  bekommt echte Formatvorlagen (Titel, Untertitel, Überschrift 1–4, Literatur).
- **Mehr Docs im Fließtext-Editor** (AP-43/44, seit 1.0.54): **Zeilenabstand** (einfach, 1,15, 1,5, doppelt),
  **Lineal** über der Seite – Seitenränder per Ziehen (beliebige Breite in mm), Erstzeilen- und linker Einzug des Absatzes –,
  **Suchen und Ersetzen im Text** (Strg+F/Strg+H: Treffer markiert, einzeln oder alle ersetzen, Groß-/Kleinschreibung, ganze
  Wörter, alle Treffer formatieren), **Kommentare am Rand** neben der Textstelle (Klick markiert sie, ✓ erledigt), ein
  **Seitenumbruch** beginnt in der Seitenansicht wirklich eine neue Seite, Silbentrennung im Blocksatz, und die
  **Zwischenablage mit Formatierung**: Kopieren gibt Word, Browser und Mail formatierten Text (HTML), Einfügen aus Word,
  Google Docs und Webseiten übernimmt Überschriften, Listen, Ausrichtung, fett/kursiv/unterstrichen, Farben und Links
  (`docs/text/RichHtml.kt`). Über einer Markierung erscheint **Loras kleines Menü** (AP-47): *Überarbeiten …*, *Kürzer*,
  *Formeller*, *Einfacher* (als einzeln annehmbare Vorschläge) und *Kommentar*.
- **Word-Treue, Teil 2** (AP-45, seit 1.0.54): Aus .docx kommen jetzt auch **Kopf- und Fußzeile** (Text ohne Seitenzahlen;
  nicht bei Dateien, die Lora selbst geschrieben hat), die **Seitenränder**, **Textfelder** (nur lesen, beim Zurückschreiben
  bleiben sie unverändert), der Inhalt von **Inhaltssteuerelementen** (Felder in Vorlagen, bisher verloren), Untertitel,
  Bildunterschrift und Literatur sowie **Zeilenabstand und Einzüge**. Test `EditorCoreTest` (Vorlage mit Kopf-/Fußzeile,
  Textfeld, Feldern; öffnen → bearbeiten → ins Original zurückschreiben).
- **Echte Seiten, Kopf- und Fußzeilen, Änderungen im Text** (AP-43 bis AP-45, seit 1.0.56): Die **Seitenansicht** zeigt
  das Dokument als einzelne A4-Blätter. Eine eigene Seitenaufteilung (`docs/text/PageLayout.kt`) bricht den Text wie Word
  und Google Docs um: **keine einzelne Zeile** eines Absatzes am Seitenende oder -anfang (Witwen und Waisen),
  **Überschriften bleiben beim folgenden Absatz**, Tabellen und Bilder wandern als Ganzes, ein Seitenumbruch beginnt eine
  neue Seite. Jedes Blatt zeigt **Kopf- und Fußzeile** mit Datum und „Seite x von y“. In *Seite einrichten* gibt es
  **Erste Seite anders** (Deckblatt ohne Kopf-/Fußzeile und Seitenzahl, oder mit eigenen) und **Gerade Seiten anders**;
  `{seite}` und `{seiten}` in Kopf- oder Fußzeile werden zur Seitenzahl. PDF-Export (dieselben Regeln: Witwen/Waisen,
  erste und gerade Seiten, eigene Ränder) und Word-Export (echte Kopf-/Fußzeilen für erste und gerade Seiten mit
  Seitenfeldern) folgen; aus Word kommen erste und gerade Kopf-/Fußzeilen zurück. **Änderungen nachverfolgen** läuft jetzt
  im Fließtext: Gelöschtes steht rot durchgestrichen, Neues grün unterstrichen an seiner Stelle, eine Leiste führt von
  Änderung zu Änderung mit *Annehmen*, *Ablehnen* und *Alle …* (Wortvergleich `docs/text/TrackDiff.kt`). Die
  **Zwischenablage** gibt zusätzlich **RTF** mit und liest RTF, wenn ein Programm kein HTML liefert
  (`docs/text/RichRtf.kt`). Tests `PageLayoutTest`, `FlowTrackingTest`, `EditorCoreTest`, `FlowEditorUiTest`.
- **Tabellen über Seiten, Abschnitte, Tabstopps, große Dokumente** (AP-43 bis AP-45 abgeschlossen, seit 1.0.57):
  - **Tabellen brechen zeilenweise** über Seiten um; die **Kopfzeile wiederholt sich** oben auf jeder weiteren Seite
    (nur zur Ansicht – bearbeitet wird sie an ihrem Platz). Eine Kopfzeile bleibt nie allein am Seitenende. Im
    PDF steht die Kopfzeile in `<thead>` und wird ebenfalls wiederholt, Word bekommt sie als „Überschriftenzeile
    wiederholen“.
  - **Abschnittsumbruch** (*Einfügen › Abschnittsumbruch …*): ab hier eine neue Seite, wahlweise mit **eigener Kopf- und
    Fußzeile** (leer = keine, nicht gesetzt = wie davor; Felder `{seite}`/`{seiten}`). Gespeichert als
    `<!-- abschnitt: kopfzeile=Kapitel 2; fusszeile=Entwurf -->`. Die Seitenansicht zeigt je Blatt die Kopf-/Fußzeile
    seines Abschnitts, das PDF setzt jeden Abschnitt als eigene Seitenart, Word bekommt echte Abschnitte mit eigenen
    Kopf-/Fußzeilen – und Abschnitte aus Word kommen so zurück (der erste Abschnitt ist der des Dokuments).
  - **Tabstopps** wie in Word: Klick ins **Lineal** setzt einen Tabstopp (Art über das Zeichen links im Lineal wählen:
    links └, zentriert ┴, rechts ┘), Ziehen verschiebt ihn, Wegziehen nach unten entfernt ihn; *Format › Tabstopps
    entfernen*. Die Tab-Taste fügt einen Tab ein (in Listen rückt sie weiter ein). Ohne eigene Tabstopps gilt alle 12,5 mm.
    Word liest und schreibt Tabs und Tabstopps, im PDF stehen die Stücke an ihren Stopps. Gespeichert im Absatzmarker
    (`<!--a:;t=40l120r-->`).
  - **Markieren über Tabellen hinweg**: Strg+A, Umschalt+Pfeile und Umschalt+Klick markieren über Textfelder, Tabellen,
    Bilder und Formeln hinweg; Kopieren (mit Tabellen als HTML), Ausschneiden, Löschen, Überschreiben und Formatieren
    (fett, Farbe, Ausrichtung …) wirken auf alles Markierte. Die Pfeiltasten gehen am Rand eines Feldes ins nächste.
  - **Große Dokumente**: Fließtext ab etwa 12 Seiten wird intern in Stücke je Kapitel geteilt (unsichtbar: Cursor,
    Backspace/Entf, Markieren und Speichern gehen über die Stücke hinweg), und der Seitenplan kommt zur Ruhe statt bei
    jedem Tastendruck alle Seiten neu zu setzen. Gemessen mit 205 Seiten: **Tippen ~80 ms je Zeichen** (vorher 2,7–3,5 s),
    Öffnen bis zum fertigen Seitenplan ~3 s (Test `bigDocumentStaysResponsive`, Werte in `build/flow-look/big-document.txt`).
  - **Prüfsammlung** (`PruefsammlungTest`): nachgebaute TUD-Brief-, Abschlussarbeits-, Antrags- und Protokoll-Vorlagen,
    wie Word sie schreibt (Formatvorlagen, Abschnitte, Tabstopps, Fußnoten, Inhaltssteuerelemente, Listen, Tabellen). Jede
    wird geöffnet, im Editor angezeigt, an einer Stelle bearbeitet und **ins Original zurückgeschrieben** – alle anderen
    Teile der Datei bleiben byteweise gleich. Dabei behoben: Absätze mit ☒/☐ am Anfang werden wiedergefunden, und ein
    bearbeiteter Absatz mit Fußnote verweist auf die Fußnote des Originals, statt die ganze Datei neu zu schreiben.
  - Das **PDF** folgt denselben Umbruchregeln wie die Seitenansicht, wird aber weiter über HTML gesetzt (Offene Frage 8:
    so belassen) – Seitenumbrüche können je nach Schrift um einige Zeilen abweichen.
  Tests `EditorRestTest`, `PruefsammlungTest`, `FlowEditorUiTest`.
- **Lora versteht Befehle auf dem Gerät und arbeitet im Mischmodus** (seit 1.0.65, AP-71):
  - **Befehlsmodell**: Ein kleines eigenes Modell (distilbert-base-german-cased, Apache-2.0, auf erfundenen Wünschen
    trainiert) ordnet, was du Lora oder in **Strg+K** schreibst, in etwa 3 Millisekunden einer von 178 Aktionen der App zu
    – Bereich oder Werkzeug öffnen, Modus starten, Einstellung zeigen oder umstellen („mach dunkel“, „Schrift größer“),
    TU Vorlage, Aktion im offenen Werkzeug („füge die PDFs zusammen“, „sortiere nach Nachname absteigend“, „lösch Seite 3“),
    Tour, frühere Chats, Frist. Offline, auch mit Tippfehlern, Umgangssprache und einfachem Englisch. Werte (an/aus,
    Seiten, Spalte, Ordner, Dateiname, Datum) liest die App danach mit Regeln aus dem Wunsch. **Ist das Modell unsicher,
    führt es nichts aus**, sondern Lora fragt wie bisher ein Sprachmodell – eine falsch ausgeführte Aktion ist schlimmer
    als eine Rückfrage. Einstellungen ändert Lora weiter erst nach „Ja“, riskante Aktionen zeigen zuerst die Vorschau.
    Gemessen auf 386 ungesehenen Wünschen: 84 % der Befehle sofort richtig, **0 % falsch ausgeführt** (die bisherigen
    Regeln: 38 % und 19 %), alle freien Anfragen weitergegeben. In Strg+K steht der erkannte Befehl als erster Eintrag (⚡).
    Das Modell kommt als Download (41 MB) – angeboten im Dialog vom ersten Start oder unter *Einstellungen › Lora*, dort
    auch Schalter, Modellkarte und *Löschen*. Ohne Modell arbeitet Lora wie bisher.
  - **Mischmodus** (*Einstellungen › Lora › Wo Lora arbeitet*): **Gemischt (empfohlen)** – Befehle sofort auf dem Gerät;
    kurze, einfache Aufgaben (umformulieren, kurz erklären, eine kurze Nachricht) mit einem installierten Sprachmodell
    auf diesem Gerät, wenn die Hardware reicht; alles andere (lange Dokumente, Analysen, Bilder, Websuche, mehrere
    Schritte) über den Server wie bisher. **Nur lokal** – nichts geht an einen Server; die App sagt klar, was das kostet
    (schlichtere Antworten, kurze Texte, oft eine halbe Minute je Antwort, keine Websuche). **Nur Server** – Sprachmodelle
    nur auf dem Server. Unverändert: Ins Postfach schaut nur ein Modell auf diesem Gerät, Tresor und Projekte „Nur auf
    diesem Gerät“ behalten ihre Regeln, vor jeder Server-Anfrage wird pseudonymisiert, Übergaben gehen an DeepSeek V4.1
    Flash, Lora selbst nutzt GLM-5.3 Flash. Mit *Modell automatisch wählen* gilt dasselbe im Chat. Unter der Einstellung
    steht, **wie oft und wie schnell** Lora ohne Server geantwortet hat (nur Zahlen, auf dem Gerät).
  - **Welche Modelle sollen auf dieses Gerät?** Der Dialog vom ersten Start fragt jetzt auch danach – Befehle verstehen
    (41 MB), Namen schützen (72 MB), ein Sprachmodell, das zur Hardware passt, und wo Lora arbeiten soll, je mit einem
    Satz Erklärung. Wer Lora schon nutzt, bekommt die Frage einmalig als „Neu: Modelle für dieses Gerät“ („Übernehmen“,
    „Später“, „Nicht mehr fragen“).
  - Werkzeuge, Messkorpus und Zahlen: [`tools/befehle/README.md`](tools/befehle/README.md). **Auf Android noch
    ungetestet** (JNA und die ONNX Runtime von sherpa-onnx, wie beim Erkennungsmodell).
- **Lora als Navigatorin**: Lora findet Einstellungen, Bereiche, Modi, TU Vorlagen und Werkzeuge. „Dunkles Design“ springt
  zu *Darstellung* und hebt die Helligkeit mit einem **Leuchtrahmen** hervor, „Ich will ein PDF zusammenfügen“ schlägt den
  PDF-Werkzeugkasten vor, „Erklär mir Exzerpte“ gibt die Frage an einen Chat weiter. Einfache Wünsche löst Lora **auf dem
  Gerät** über eine Landkarte der App (`data/AppMap.kt`, mit Synonymen) – ohne Anfrage; nur für echte Gespräche fragt sie
  GLM-5.3 Flash (Denkphase aus) mit den Werkzeugen *navigate*, *open_mode*, *change_setting* (nur nach Bestätigung),
  *enable_tool*, *handoff_chat*, *explain* (eingebaute Hilfe) und Rückfragen mit Antwortknöpfen. Einstiege: schlafende Lora
  unten rechts in den Einstellungen, TU Vorlagen und Literatur (Tipp weckt sie, Text oder Sprache), ein Tipp auf Lora auf
  der Startseite, *Frag Lora* oben im Chat, Strg+K › *Lora fragen*, langes Drücken auf Lora im Chat. Beim ersten Start höchstens zwei Fragen (wo Lora
  erscheint, welche Werkzeuge sichtbar sind). *Einstellungen › Lora*: überall / nur im Chat / aus (ersetzt „Lora
  ausblenden“), Modell, ruhigere Bewegungen, Hinweise. Lora läuft nur auf Abruf; in Projekten „Nur auf diesem Gerät“ sucht
  sie nur lokal.
- **Startseite**: unter der Begrüßung die Tätigkeiten als schlichte **Chips** – *Schreiben*, *Dokument erstellen*,
  *Dateien auswerten*, *Literatur finden*, *TU Vorlagen*. Ein Tipp öffnet an ihrer Stelle **konkrete Starts**, damit
  niemand einen ersten Satz erfinden muss: z. B. *E-Mail formulieren*, *Text kürzen und verständlicher machen*,
  *Protokoll aus Notizen*, *Präsentation*, *Zusammenfassen* (wählt Dateien und legt den Auftrag ins Eingabefeld),
  *Aktuelle Literatur zu einem Thema* (Literatursuchmodus), *Antwort nur mit Belegen* (Quellenmodus) oder die eigenen
  TU Vorlagen; ✕ führt zurück, Tippen schließt sie. Darunter *Zuletzt* (die letzten Chats) und *Favoriten*
  (★-Vorlagen). Lora bietet in der Sprechblase jeweils eine Idee an; ein Tipp auf Lora öffnet ihr Panel. Das frühere
  zweite Eingabefeld *Frag Lora …* ist weggefallen – dafür ist der Knopf oben deutlich größer.
- **Einstellungen in Gruppen**: Profil, *Zugang* (API, GitHub, Umgebungen), *Assistent* (Chat & Werkzeuge, Werkzeuge,
  Datenschutz, Gedächtnis, Wissenssammlung), *Modelle* (Übersicht, Vergleich, Modelle auf diesem Gerät, Transkription),
  *Dokumente & Darstellung* und *Daten* (Verbrauch, Sicherung, Arbeitsspeicher). *Chat & Werkzeuge* ist
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
- **Erstellen** (Seitenleiste, AP-24) – neben *Dateien* der zweite Ort für Inhalte: oben das Feld **„Was möchtest du
  erstellen?“** (Lora erledigt es im Chat, auch mit angehängter Datei – „Tagungsposter aus meinem Paper“, „Aushang für die
  Sprechstunde“, „Protokoll aus meinen Notizen“), darunter unter Windows **Gestalten** und **Meine Designs**, dann die
  **TU Vorlagen**.
- **Gestalten** (Windows, AP-24, Vorbild Canva, Schwerpunkt Corporate Design): Vorlagen *Tagungsposter* (A1),
  *Digitales Poster* (16:9 für Bildschirme, nach dem Layout „3 Spalten“ des CD-Pakets), *Flyer* (A5), *Aushang* (A4),
  *Einladungskarte* (A5 quer), *Social Media: Beitrag* und *Story*, *OPAL-Kursbanner*, *Titelbild für Lehrvideos* – jede
  zum **Selbst gestalten** oder **Lora machen lassen**; dazu *Poster aus einem Paper* und *Leeres Design* in 18 Formaten
  (A0–A6, DIN lang, Bildschirmformate in Pixeln). Alle Vorlagen folgen dem **CD 2025**: Logo oben links (A4 wie in
  Briefen und Urkunden 25/18 mm), Überschriften in Noto Sans Medium (Brillantblau auf Weiß, Weiß auf Brillantblau),
  Fließtext Schwarz, nur TUD Farben, die **Shape** als Bildmotiv (randabfallend, gern mit Foto darin) und TUD-Icons. Briefe, Protokolle und
  Bescheinigungen bleiben TU Vorlagen (die Teilnahmebescheinigung gibt es dort nach der TUD-Vorlage).
  - **Editor**: Leinwand mit Seiten und Ebenen, Auswählen (auch mehrere, mit Rahmen), Verschieben mit **Einrasten** an
    Seitenrand, Seitenmitte und anderen Elementen, Größe und **Drehung** über Griffe, Ausrichten und Verteilen, nach
    vorne/hinten, Sperren, **Rückgängig/Wiederholen** (Strg+Z/Y), Pfeiltasten, Strg+D, Strg+C/V (auch Bilder und Text aus
    anderen Programmen), Zoom mit Strg+Mausrad. Seiten hinzufügen, verdoppeln, verschieben; Format ändern passt alles an.
    Seit AP-50: **Gruppieren** (Strg+G), **Text direkt auf der Leinwand tippen** (Doppelklick), **Farbverläufe** und
    **Bildfilter** (Helligkeit, Kontrast, Sättigung, Graustufen, Weichzeichnen). Ausrichten, Ebenen und Gruppen sind
    gemeinsamer Code mit den Folien (`creative/CanvasOps.kt`), dort wirken sie genauso.
    Seit 1.0.59: Text im Textfeld **mit Formatierung tippen** – markierten Text mit Strg+B/I/U oder der kleinen Leiste über
    dem Feld fett, kursiv, unterstrichen oder farbig machen (PNG, PDF und PowerPoint übernehmen das) – und **Tabellen auf
    der Leinwand** im TUD-Stil (blaue Kopfzeile, Zellen, Zeilen und Spalten im Panel; in PowerPoint eine echte Tabelle).
  - **Elemente**: Text (Noto Sans Normal, **Medium** für Überschriften und Fett, je kursiv; wächst beim Tippen mit oder
    wird mit *An die Box anpassen* kleiner), die **TUD-Shape** (Farbe, Spiegeln, Drehen in 45°-Schritten, nie verzerrt
    oder als Umriss), Formen (Rechteck, abgerundet, Kreis, Dreieck, Stern, Linie, Pfeil), **Symbole** – die rund 530
    **TUD-Icons** in 19 Themensets und 37 weitere –, **Bilder** mit Zuschnitt, Füllen/Ganz zeigen, Eckenradius,
    Alternativtext und *In die TUD-Shape setzen*, **Bild erzeugen** mit dem Bildmodell direkt auf die Leinwand,
    **QR-Codes**, alle **offiziellen Logos** (horizontal und vertikal, deutsch und englisch, Kurzform „TUD“, Bildmarke –
    je blau, weiß, schwarz; als Vektor gezeichnet) und eigene Logos aus *Einstellungen › Dokumente & Logos*.
  - **Brand-Kit** aus dem CD-Paket 2025: die 23 **TUD Farben** (Brillantblau, Dunkelblau, Schwarz, Grau, Weiß und die
    Sekundärfarben Blau, Violett, Magenta, Rot, Orange, Gelb, Oliv, Grün, Türkis in den Stufen 1 und 2), Noto Sans und
    Noto Sans Medium, Logos, Shape und Icons; eigene Farben als #RRGGBB. Die Logos und Icons bereitet
    `desktop/tools/tud_cd_assets.py` aus dem entpackten CD-Paket auf (`resources/tud/`).
  - **Export**: **PNG** (Bildschirmformate in ihrer Pixelgröße, Druckformate mit 150 oder 300 dpi; mehrere Seiten als ZIP),
    **PDF für den Druck** (vektoriell, Noto Sans eingebettet; auf Wunsch mit 3 mm Beschnitt und Schnittmarken für die
    Druckerei) und **PowerPoint** mit echten, bearbeitbaren Textfeldern und Formen.
  - **Lora gestaltet in jedem Chat** (Windows): `create_design`, `update_design`, `read_design` füllen die Platzhalter einer
    Vorlage (Texte passen sich der Box an), setzen QR-Codes und erzeugen auf Wunsch ein Bild für den Bildplatz; Modus
    *Gestalten* (`/Gestalten`) mit Vorgaben (knapp, nichts erfinden). Unter der Antwort eine Karte mit Vorschau und
    *Bearbeiten*; ist der Editor offen, lädt er eine im Chat geänderte Fassung nach.
- **TU Vorlagen – die Vorlagen-Bibliothek für die Professur** (unter *Erstellen*): rund 55 fertige Vorlagen, gegliedert in *TUD-Dokumente*, *Lehre*
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
- **PowerPoint im TUD-Design**: Präsentationen (.pptx, 16:9) nach der offiziellen Präsentationsvorlage des CD 2025 –
  Titelfolie in Brillantblau (weißes Logo, Untertitel über dem Titel, Einrichtung unten), Abschnitts- und Schlussfolien
  mit der Shape, Inhalts-, Tabellen-, Zweispalten- und Zitatfolien mit Titeln in Noto Sans Medium (32 pt), Fußzeile mit
  Kurzlogo „TUD“ und „Titel • Vortragende • Seite N“, Farbschema „TUD_2025“ sowie Sprechernotizen. Vorlagen
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
  **Treffsicherer**: Öffentliche Quellen bleiben, wie sie sind – Ergebnisse von Web- und Literatursuche, Bücher und Paper
  (DOI, ISBN, ISSN), die Autor:innen der Literatursammlung und im Text zitierte Autor:innen („Müller (2020)“, „Lipowsky &
  Rzejak, 2021“) sowie bekannte Personen aus Wissenschaft und Politik; ein früher als Elternteil gespeichertes „Müller“
  bleibt in einer Literaturangabe stehen. Anhänge zeigen „öffentlich“ oder „vertraulich“ (Vorschlag der App, ein Tipp
  schaltet um). Zusätzlich erkannt: seltene Nachnamen nach einem Vornamen, Schulen und Kitas („Grundschule am Park“,
  „Goethe-Gymnasium“) und Orte in Personenbeschreibungen („die Schulleiterin in Pirna“). In der Vorschau und im
  Agent-Protokoll korrigiert ein Tipp – „nicht ersetzen“ bzw. „auch ersetzen“ –, und das gilt ab dem nächsten Chat.
  **Eigenes Erkennungsmodell** (seit 1.0.64, AP-67): Ein kleines deutsches Modell (gbert-base, MIT-Lizenz, auf
  erfundenen Texten trainiert) findet Namen, Schulen, Kitas und Orte auch ohne Anrede oder bekannte Vornamen, in wenigen
  Millisekunden je Absatz auf dem Gerät. Es kommt als Download (72 MB) – angeboten in einem Popup beim ersten
  Pseudonymisieren oder beim Öffnen von *Einstellungen › Datenschutz* („Laden“, „Später“, „Nicht mehr fragen“), dort auch
  mit Schalter, Fortschritt, Modellkarte und *Löschen*. Mit dem Modell raten die Regeln keine Namen mehr aus häufigen
  Vornamen (die öffentliche Personen wie „Käthe Kollwitz“ trafen); feste Muster, Anrede, Rollen, Grüße, Tabellen und die
  eigene Liste bleiben. Auf dem Messkorpus (`tools/pii`, 517 erfundene Beispiele, Prüfteil nie im Training) findet die
  App so **99,2 %** statt 78,2 % der privaten Angaben und ersetzt 7,4 % statt 9,9 % der öffentlichen. Ohne Modell
  arbeiten die Regeln wie bisher. Zusätzlich kann wie bisher ein **lokales Sprachmodell** jeden Text einmal lesen
  (*Gründlich*, langsamer). `PseudonymCorpusTest` und `PiiModelTest` messen beides.
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
- **Lora, das Maskottchen und der Forschungsassistent**: ein kleiner, runder Roboter in der Farbe des Stils (benannt
  nach *Low-Rank Adaptation*). Auf „Wie heißt du?“ stellt sich der Assistent als Lora vor, der Forschungsassistent der
  App; nach dem Modell gefragt, nennt Lora das Sprachmodell, das gerade antwortet. Eine Persona oder ein eigener
  System-Prompt darf eine andere Rolle vorgeben.
  **Im Chat** bleiben Gedankengang und Werkzeugschritte eingeklappt (*Chat & Werkzeuge › Arbeitsschritte einklappen*,
  Standard an); solange der Agent arbeitet, begleitet Lora die Antwort in einer Karte mit fester Höhe (nichts springt) –
  Lora auf einer kleinen, hell beleuchteten Bühne, daneben „Lora ist dran“ mit der bisherigen Zeit, der aktuelle Schritt,
  die Zahl der Schritte, die alles aufklappt, und eine feine Fortschrittslinie am unteren Rand. Lora forscht dabei mit:
  mikroskopiert, experimentiert mit dem Erlenmeyerkolben, dreht den Globus, schaut durchs Teleskop und durchs Fernglas,
  funkt mit der Antenne, blättert im Karteikasten, holt ein Buch aus dem Regal, fängt umherflatternde Seiten mit dem
  Kescher, gräbt ein Fossil aus, schleppt Bücherstapel, puzzelt, rechnet am Abakus, faltet Papierflieger, hört mit
  Kopfhörern zu, notiert, präsentiert ein Diagramm, hat Geistesblitze, liest, tippt, jongliert, tanzt, dreht Pirouetten
  und trinkt zwischendurch Kaffee – und spielt oft nach, was der Agent gerade tut (Websuche: Lupe, Antenne, Teleskop,
  Fernglas; Literatur: Bücher, Karteikasten, Regal; Rechnen: Experiment und Abakus; E-Mail: Papierflieger;
  Transkription: Kopfhörer; Nachdenken: Denkerpose und Puzzle). Dabei geht es natürlich zu: Ein Buch hält Lora mit dem
  Einband nach außen, auf dem Klemmbrett schreibt Lora von hinten und dreht es zum Zeigen um. Lora dreht Kopf und Körper
  zu dem, was gerade wichtig ist, lehnt sich in Bewegungen, federt beim Hüpfen, die Antenne schwingt nach, die Arme
  haben weiche Ellbogen, und zwischen zwei Tätigkeiten hüpft Lora kurz hinüber. Ein Tipp auf Lora in der Arbeitskarte
  bringt ein Winken, eine Pirouette, einen Geistesblitz, einen Jubel oder ein Nicken. **Seit AP-36** arbeitet Lora auch
  mit Werkzeugen, die wirklich ansetzen – ihre Hände greifen dabei gezielt nach einem Punkt: Die **Schere** schneidet ein
  Blatt entlang der Linie durch (die untere Hälfte fällt), **Stempel** und **Tacker** stehen auf einem kleinen
  Beistelltisch – der Stempel trifft das Blatt und hinterlässt ein rotes Siegel, das Lora danach zeigt; dazu ein
  **Brief**, der gefaltet, eingesteckt und mit einem Herz versiegelt wird (E-Mail-Entwürfe), der **Wandkalender**, an dem
  sie umblättert und einen Tag rot einkreist (Termine, Fristen), die **Staffelei** (Logos), das **Vorhängeschloss** mit
  Schlüssel, eine **Präsentation** mit Zeigestock (Dokumente), eine **Tabelle**, die sich füllt (Excel), **Übersetzen** mit
  zwei Sprechblasen, der **Textmarker** (Dokumente und Quellen prüfen) und ein **Ordner**, in den ein Blatt fällt. Dazu
  Stimmungen: Lora **zeigt den Weg**, wenn sie irgendwohin führt, **nickt**, **zuckt mit den Schultern**, wenn sie nichts
  findet, **kratzt sich am Kopf** bei Fehlern, **jubelt** mit Konfetti und **fliegt** von ihrem Panel zum Ziel. Ihre
  Augen folgen dem Mauszeiger, solange sie nur da ist; morgens beginnt sie auf der Startseite mit einem Kaffee, spätabends
  ist sie ruhiger. *Ruhigere Bewegungen* (Einstellungen › Lora) gilt auch für das Fliegen. Fertige Antworten zeigen eine ruhige Zeile
  „Gedankengang und 4 Arbeitsschritte“. **Auf der Startseite** winkt Lora zur Begrüßung, wartet dann (schaut sich um,
  streckt sich, gießt die Pflanze, hört Musik, liest, dreht den Globus …) und bietet in einer Sprechblase eine Idee an –
  Favoriten bzw. empfohlene Vorlagen, Beispiele wie „Literatur zu einem Thema finden“ und alle Vorlagen; ↻ bringt mit
  einem Geistesblitz die nächste, ein Tipp auf Lora öffnet ihr Panel. Beim Tippen schreibt Lora mit; nach einer Weile ohne Eingabe nickt
  Lora ein. Wer Lora nicht sehen möchte, schaltet *Chat & Werkzeuge › Lora ausblenden* ein (Standard aus): Dann zeigen
  Startseite, Arbeitskarte und Sprachmodus das Symbol der App, die Ideen bleiben erreichbar.
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
- **Diktat** (AP-65, seit 1.0.61): 🎤 in der Symbolleiste des Dokument-Editors (und an den Sprechernotizen der Folien) –
  antippen, sprechen, wieder antippen: der Text steht am Cursor, mit Leerzeichen davor und Großbuchstaben am Satzanfang.
  Erkannt wird **auf dem Gerät** mit der Spracherkennung der Transkription (sherpa-onnx; Modell unter *Einstellungen ›
  Transkription* laden), den Uni-Server nimmt das Diktat nur, wenn kein Modell geladen ist und der Datenschutz es
  erlaubt. **Satzzeichen per Sprache**: „Punkt“, „Komma“, „Fragezeichen“, „Ausrufezeichen“, „Doppelpunkt“, „Strichpunkt“,
  „Bindestrich“, „Gedankenstrich“, „Anführungszeichen auf/zu“, „Klammer auf/zu“, „neuer Absatz“/„neue Zeile“ (englisch
  „period“, „comma“, „new paragraph“ …); die eigenen Satzzeichen des Modells daneben fallen weg, „der wichtigste Punkt“
  bleibt ein Wort. Keine Sprachbefehle für die App (dafür gibt es den Sprachmodus). Test `DictationTest`.

- **Literaturverzeichnis in 8 Zitierstilen**: APA 7 (deutsch und englisch), Zeitschrift für Pädagogik, DIN ISO 690,
  Harvard, Chicago (Autor-Jahr), MLA 9 und IEEE. Quellen als BibTeX, RIS, CSL-JSON oder einfach als Text einfügen
  (auch ein fertiges Verzeichnis aus Word) – die App sortiert alphabetisch (mit Namenszusätzen wie „von“ richtig),
  erkennt Dubletten und **ergänzt fehlende Angaben** (DOI, Verlag, Ort, Seiten, Heft, Mitautor:innen) über Crossref,
  OpenAlex, die Deutsche Nationalbibliothek und Google Books. Jeder Eintrag zeigt, was ergänzt wurde und was noch fehlt;
  unsichere Treffer werden nur vorgeschlagen. Export als Word, PDF, Markdown, BibTeX, RIS und CSL-JSON. Im Chat unter
  einer Antwort: *⋯ › Literaturverzeichnis erstellen*. Der Zitierstil ist unter *Einstellungen › Literatur & Zotero* wählbar.
- **Modi mit einheitlichem Rahmen**: Jeder Modus zeigt über dem Eingabefeld einen Chip mit ✕ zum Verlassen und eine Zeile
  „was passiert hier · Daten: …“; im leeren Chat steht ein Beispiel zum Antippen. Modi starten mit `/` (z. B. `/Planen`),
  über die Befehlspalette (Strg+K, Gruppe *Modi*), die Startseite und Loras Ideen: *Literatursuche*, *Quellenmodus*,
  *Exzerpt*, *Planungsmodus* und *Sprachmodus*. Neue Modi brauchen nur eine Definition in `agent/ChatModes.kt`.
- **Rückfragen mit Antwortknöpfen**: Statt zu raten, stellt das Modell vor größeren Aufgaben 1–4 kurze Fragen
  (`ask_user`) – als Karte im Chat mit Antwortknöpfen (eine oder mehrere Antworten) und einem Feld für eine eigene
  Antwort; *Antworten* schickt alles als eine Nachricht, *Überspringen* lässt das Modell mit Annahmen weitermachen. Im
  **Planungsmodus** klärt Lora ein Vorhaben (Seminar, Antrag, Projekt, Paper, Arbeitsplan) in mehreren solchen Runden und
  schreibt daraus einen Plan als Dokument. **TU Vorlagen mit vielen Feldern** lassen sich statt im Formular mit
  *Lieber mit Lora Schritt für Schritt* als Fragerunde ausfüllen.
- **Quellenmodus mit Belegpflicht** (`/Quellen`): Jede Aussage bekommt einen Marker [n], die Antwort endet mit
  „## Belege“ – Datei und Seite, DOI, URL oder Transkriptzeit, jeweils mit wörtlichem Zitat. Danach prüft die App **auf dem
  Gerät** jedes Zitat gegen die Quellen des Chats (Anhänge mit ihren Seitenmarken, gelesene Webseiten, Wissenssammlung,
  Transkripte): bestätigt, andere Seite, nicht gefunden oder nicht prüfbar. Aussagen ohne Beleg werden im Text mit ⚠
  markiert. Unter der Antwort steht das **Belegprotokoll** (aufklappbar, als Dokument exportierbar); ein Klick auf einen
  Beleg öffnet die PDF-Seite im PDF-Werkzeugkasten oder die Webseite.
- **Exzerpt-Agent** (`/Exzerpt`, *Literatur › Eintrag › Exzerpt erstellen* oder die TU Vorlage *Literatur exzerpieren*):
  Schemata je Textart – empirisch (Bibliografie, Fragestellung, Theorie, Methode, Stichprobe, zentrale Befunde, wörtliche
  Zitate mit Seite, Begriffe, eigene Bewertung, Anschlussfragen), theoretisch, Review und Buchkapitel. `save_excerpt`
  prüft jede Seitenangabe gegen das angehängte PDF und nimmt das Exzerpt erst an, wenn sie stimmen; dann hängt es am
  Literatureintrag (wird bei Bedarf angelegt) und liegt unter *Dokumente*. Mehrere Exzerpte: *Literatur › ⋯ › Exzerpte als
  Tabelle*.
- **Tresor für sensible Forschung** (Werkzeug, *Einstellungen › Werkzeuge*): eigener Bereich mit App-PIN für Interviews oder
  Daten von Studierenden. Beim Einrichten erscheint einmal ein **Wiederherstellungsschlüssel** zum Ausdrucken, mit dem sich
  eine vergessene PIN neu setzen lässt. Tresor-Chats sind mit AES-256 verschlüsselt (Schlüssel aus PIN, zusätzlich mit
  Android Keystore bzw. Windows DPAPI gesichert) – ohne PIN auch als Datei nicht lesbar; sie erscheinen nicht in Chatliste,
  Suche, Gedächtnis, Vorschlägen oder Benachrichtigungstexten. Im Tresor gelten **nur Uni-Server oder lokale Modelle**,
  keine Websuche/GitHub und eine **strenge Pseudonymisierung** (gründliche Erkennung, keine Ausnahmen). Nach fünf falschen
  PINs wartet jeder weitere Versuch länger; der Tresor sperrt sich nach einstellbarer Inaktivität.
  **Auf mehreren Geräten** (AP-63, seit 1.0.61): Synchronisiert (PCs über den Cloud-Ordner, das Handy über den Handy-Sync)
  bekommt jedes Gerät seine **eigene PIN-Kiste**, versiegelt mit seinem Geräteschlüssel – einmal mit dem
  Wiederherstellungsschlüssel eine PIN für dieses Gerät setzen, die PIN der anderen Geräte bleibt; eine neue PIN ändert nur
  die Kiste des eigenen Geräts. Ein Tresor-Chat, der auf zwei Geräten geändert wurde, wird beim nächsten Öffnen zur
  verschlüsselten „zweiten Fassung“ (die Konfliktkopie bleibt bis dahin verschlüsselt im Tresor). Test `SyncRestsTest`.
- **PDF-Werkzeugkasten** (Werkzeug, Vorbild pdf24 + Xodo): mehrere PDFs in **Tabs** mit Miniaturen, Gliederung, Suche,
  Zoom und Nachtmodus; **Seiten** zusammenführen, teilen (alle n Seiten oder nach Bereichen), extrahieren, löschen, drehen,
  **sortieren durch Ziehen** der Miniaturen und **Seitenzahlen** setzen; **Markieren**: hervorheben, unterstreichen,
  durchstreichen, Freihand, Notizen und Stempel als echte PDF-Anmerkungen, Kommentarliste als Dokument exportieren.
  Jeder Schritt lässt sich rückgängig machen; *Speichern* bzw. *Speichern unter / Teilen*. Auf Windows zusätzlich
  **Formulare ausfüllbar machen**: *Felder erkennen* findet Eingabestellen (Linien „____“, „Name:“ mit Platz, Kästchen ☐,
  Datum, Unterschrift), die Felder lassen sich verschieben, in der Größe ändern, umbenennen und umtypisieren; oder der
  Agent macht es im Chat (`make_pdf_fillable`). Danach füllt man das PDF am PC aus (auch der Agent mit `fill_pdf`).
  Android: Betrachter, Seiten und Markieren. Lora schneidet, heftet und stempelt dazu.
  **Stufe 2** (AP-15) mit den Bereichen *Unterschreiben*, *Umwandeln* und *KI*:
  - **Texterkennung (OCR)**: Scans werden durchsuchbar – auf Windows mit der **Texterkennung von Windows** (Deutsch,
    sonst Englisch; nichts verlässt den PC, nichts zu installieren; über Windows PowerShell 5.1 als `-EncodedCommand`,
    das die Ausführungsrichtlinie nicht sperrt), sonst bzw. auf Wunsch nach Rückfrage mit einem **Bildmodell**, das die
    Datenschutzstufe erlaubt (besser bei Handschrift; die Seitenbilder gehen an den Server). Der Text liegt danach
    **unsichtbar** über den Seiten (auch gedrehten): Suchen, Kopieren, *Frag das PDF* und Anhänge im Chat finden ihn.
  - **Umwandeln**: Word, Excel, PowerPoint, Bilder, HTML und Text öffnen sich über *PDF öffnen* direkt als PDF – mit
    installiertem **Microsoft Office** exakt wie gedruckt (über COM, ein offenes Office bleibt unberührt), sonst mit dem
    Leser der App (vereinfachtes Layout). Ein PDF wird zu **Word**, **Markdown**, **Text** (Überschriften nach
    Schriftgröße, Absätze, Listen, Tabellen; Seitenzahlen und Kopfzeilen fallen weg), **Excel** (seine Tabellen) oder
    **Bildern** (PNG je Seite als ZIP).
  - **Unterschreiben**: mit Maus, Stift oder Finger zeichnen oder ein Bild (PNG mit durchsichtigem Hintergrund) wählen,
    Größe und *Ort, Datum darunter* einstellen, auf die Stelle tippen. Unterschriften bleiben für das nächste Formular
    (`signatures.json`), *Rückgängig* nimmt sie wieder weg. Auch auf Android.
  - **KI**: *Frag das PDF* (neuer Chat im **Quellenmodus**: jede Aussage mit Seite und Zitat, geprüft gegen das PDF, Klick
    öffnet die Stelle), *Zusammenfassen*, *Formular erklären* (Feld für Feld, Nachweise, Fristen – ohne auszufüllen),
    *Tabellen mit KI* und **Tabellen herausziehen auf dem Gerät** (Spalten aus den Textpositionen, ohne KI) als Excel
    oder als Tabelle im Editor.
  **Stufe 3** (AP-26) im Bereich *Schwärzen & mehr* (Windows; *Vergleichen* auch auf Android):
  - **Echtes Schwärzen**: Begriffe eingeben oder **Personenbezogenes vorschlagen** lassen (Namen, Kontaktdaten,
    eigene Begriffe der Pseudonymisierung), *Stellen suchen* zeigt die Treffer je Seite. Seiten mit Balken werden als
    Bild neu gezeichnet – der Text darunter ist aus der Datei **verschwunden**, nicht nur verdeckt; auf Wunsch gehen auch
    Titel, Autor und die versteckten XMP-Daten. Danach lässt sich der Rest mit der Texterkennung wieder durchsuchbar machen.
  - **Text ändern** (weiß überdecken, neuer Text in Helvetica darüber – der alte bleibt in der Datei) und **Bilder**:
    Vorschau, *Ersetzen …* durch ein eigenes Bild oder *Entfernen*.
  - **Verkleinern**: große Bilder kleiner rechnen und als JPEG speichern (*Mittel* bis 1.600 px, *Stark* bis 1.000 px),
    Datei komprimiert – mit Größe vorher/nachher.
  - **PDF/A-2b** für die Archivierung: XMP mit PDF/A-Kennung, sRGB-Farbprofil, Sprache, ohne Verschlüsselung, JavaScript
    und eingebettete Dateien; nennt, was noch fehlt (nicht eingebettete Schriften). Streng prüfen: veraPDF.
  - **Passwort** (AES-256): zum Öffnen und/oder für die Rechte (Drucken, Kopieren; Screenreader dürfen immer lesen),
    **Schutz entfernen** mit dem Passwort. **Metadaten** ansehen, ändern oder alle entfernen.
  - **Barrierefreiheit nach PDF/UA**: Tags, Sprache, Titel und Titelanzeige, eingebettete Schriften, Text statt Scan,
    Alternativtexte der Abbildungen, Formularfelder mit Beschreibung, Lesezeichen, Tab-Reihenfolge; *Einfache Mängel
    beheben* setzt Sprache, Titel, Titelanzeige und Tab-Reihenfolge.
  - **Zwei PDFs vergleichen** (zwei offene Tabs): Seite für Seite, Wort für Wort, was entfernt und was neu ist – auch
    als Word-Dokument.
  **Seit AP-60** (Windows): **Markieren am Text** – Hervorheben, Unterstreichen und Durchstreichen rasten an den Zeilen
  ein (vom ersten bis zum letzten Wort, ein Kasten je Zeile; Schalter *Am Text einrasten*). **Schwärzen mit der Maus**:
  Rechteck ziehen oder Wort anklicken, dann echt schwärzen. **Digitale Signatur** mit einem Zertifikat aus dem
  Windows-Zertifikatsspeicher oder einer .p12/.pfx-Datei (der private Schlüssel bleibt dort), optional mit sichtbarem
  Feld; **Signaturen prüfen** sagt, ob das Dokument unverändert ist, ob danach etwas hinzukam und ob dieser PC dem
  Zertifikat vertraut (Bouncy Castle). **Tags erzeugen** für PDF/UA: Überschriften nach Schriftgröße, Absätze, Listen,
  Abbildungen mit eingegebenem Alternativtext, Linien und Seitenzahlen als Dekoration – danach meldet die PDF/UA-Prüfung
  „Mit Struktur (Tags)“. Tabellen werden noch nicht erkannt. Test `PdfRestTest`.
- **Mail & Kalender** (AP-13, AP-27, Werkzeug *Mail & Kalender*, anfangs aus): Lora arbeitet unter Windows mit dem
  **klassischen Outlook** dieses PCs – lokal über COM (JNA, ohne PowerShell, ohne Admin-Rechte, ohne Freigabe der IT;
  Outlook startet bei Bedarf im Hintergrund). Für das neue Outlook, andere Mailprogramme und **das Handy** gibt es den
  **Weg über den Mailserver** (AP-27, *Einstellungen › Verbindungen › Mail über den Server*): IMAP mit TLS (993) oder
  STARTTLS (143), dieselben Funktionen ohne Kalender – Postfach zusammenfassen, Ordner, sortieren (nur mit MOVE oder
  UIDPLUS des Servers, damit keine andere Mail angefasst wird), Ordner anlegen, Antworten als **Entwurf** in „Entwürfe“
  (mit zitiertem Verlauf). Unter Windows wählbar statt des klassischen Outlook. Termine, Aufgaben und Frei/Gebucht
  brauchen Exchange (EWS/Graph mit Freigabe der IT) und gibt es auf diesem Weg noch nicht.
  - **Was ist wichtig?** Postfach zusammenfassen (z. B. seit gestern): *Handeln*, *Termine*, *Zur Kenntnis*.
  - **Nur ein Modell auf diesem Gerät** (AP-68): Mails, Ordner, Termine und Aufgaben liest weder der Uni-Server noch ein
    externer Anbieter – fest, ohne Schalter. Der Modus *Postfach* arbeitet *Nur auf diesem Gerät* und bietet im
    Modellwähler nur lokale Modelle an; Mail-Werkzeuge gibt es in keinem Chat mit einem Server-Modell, auch nicht über
    Lora, Abläufe, Stapel oder geplante Aufgaben. Fehlt ein lokales Modell, erklärt *Mail & Kalender* warum und schlägt
    ein passendes zum Herunterladen vor (je nachdem, ob der PC eine Grafikkarte hat). Lora nimmt Mail-Aufträge an, liest
    aber selbst keine Mails – das Modell auf dem Gerät erledigt sie („läuft auf diesem Gerät“). Lange Mails liest es
    gekürzt (Kopf und Anfang), damit es auch ohne Grafikkarte zügig geht. **An Chat übergeben**: Eine Zusammenfassung
    des lokalen Modells geht an DeepSeek – vorher als Karte zum Prüfen und Ändern, pseudonymisiert, ohne Mails, Anhänge
    und Mail-Kennungen; der neue Chat kann nicht ins Postfach greifen.
  - **Mailansicht wie ein Mailprogramm** (AP-69): links alle Ordner mit Unterordnern und Ungelesen-Zahl, in der Mitte
    die Mails nach Heute, Gestern, Diese Woche …, rechts die geöffnete Mail mit Anhängen (öffnen, in den Lora-Ordner
    speichern, *In Outlook öffnen*). Auch Postfächer mit vielen tausend Mails laufen flüssig (Nachladen beim Scrollen),
    die Spaltenbreiten lassen sich ziehen. **Suchen und filtern** nach Absender, Betreff, Zeitraum, ungelesen, mit
    Anhang, in einem oder allen Ordnern. Mehrere Mails auswählen, auf einen Ordner ziehen, als gelesen/ungelesen
    markieren, Ordner anlegen und umbenennen – alles mit Rückgängig und im Protokoll. Lora weiß, welche Mails ausgewählt
    sind. Auf dem Handy erst die Liste, dann die Mail. Über den Mailserver fragt die App lange Listen in kurzen Befehlen
    ab, weil Exchange Zeilen über 10 KB ablehnt (in 1.0.62 bei großen Posteingängen „Command Error. 10“).
  - **Lora sortiert selbst** (AP-69): „Geh die Mails der letzten Woche durch und ordne sie in Lehre, Forschung und
    Verwaltung ein“ – fehlende Ordner (auch Unterordner wie „Lehre/WS 2026-27“) legt Lora in Outlook an oder schlägt
    eine Ordnerstruktur vor. Das Modell auf dem Gerät liest Kopf und Anfang jeder Mail. Danach **eine Vorschau** für den
    ganzen Auftrag (einzelne Mails umhängen oder auslassen, Unsichere gesondert) und **ein Klick** *Einordnen*; mit
    „ohne Rückfrage“ ohne Vorschau, Unsichere bleiben dann liegen. Der Lauf geht im Hintergrund mit Fortschritt, Pause
    und Abbrechen weiter, auch nach einem Neustart, und lässt sich **als Ganzes rückgängig** machen (neue Ordner bleiben
    stehen). Aus einem Lauf werden auf Wunsch feste Regeln oder ein regelmäßiger Auftrag („jeden Montag“); Korrekturen
    merkt sich Lora als Beispiele, wenn man zustimmt. Auch als Lora-Aktion in Abläufen.
  - **Sortieren**: Lora schlägt Ordner und feste **Sortierregeln** vor (Absender/Betreff enthält … → Ordner);
    Verschieben fragt vorher, Regeln wirken nur über *Regeln anwenden …*, das zuerst zeigt, was wohin käme.
  - **Antworten im eigenen Stil** (Gedächtnis, gesendete Mails) landen als **Entwurf** in Outlook – mit zitiertem Verlauf.
  - **Fristen und Termine** aus Mails als Termin (Fristen ganztägig mit Erinnerung) oder **Aufgabe**; **freie Zeiten**
    mit Kolleg:innen aus Frei/Gebucht (Mo–Fr, 8–18 Uhr, einstellbar) und **Einladungen**, die vorbereitet in Outlook
    aufgehen – senden tut die Person selbst.
  - **Harte Regeln**: Die Brücke zu Outlook hat weder Löschen noch Senden. Jede Änderung steht im **Protokoll** unter
    *Mehr › Mail & Kalender* (Reiter *Übersicht*: Verbindung, Termine von heute und morgen, Starts für Lora, Regeln).
    Modus *Postfach* (`/Postfach`).
  - Je nach Virenscanner fragt Outlook einmal „Ein Programm versucht, auf E-Mail-Adressen zuzugreifen“ – dort zulassen.
- **Dateien** (AP-16 Stufe 0, Tab *Dateien* oben in der Seitenleiste, Strg+D) – wie Google Drive: der **Lora-Ordner**
  mit den festen Ordnern **Hochgeladen** (eine Kopie jeder Datei, die in einen Chat kommt – dieselbe Datei nur einmal,
  nie aus Tresor-Chats) und **Erstellt** (alles, was die KI im Chat macht) und eigenen Ordnern.
  - **Raster mit Vorschaubild** (erste Seite, Folie, Tabelle, PDF-Seite, Foto) oder **Liste**, sortiert nach Name,
    Geändert, Typ oder Größe; Pfadleiste; links (Handy: oben) *Zuletzt · Markiert · Meine Dateien · Hochgeladen ·
    Erstellt · Orte · Papierkorb*.
  - **Großes „+“** unten rechts: Ordner, Hochladen, Dokument, Präsentation, Tabelle, Markdown, aus Vorlage, mit KI – neue
    Dateien landen im geöffneten Ordner.
  - **Umbenennen, Verschieben** (Windows: auf einen Ordner, die Pfadleiste oder links ziehen; überall *Verschieben nach …*),
    Kopie, **Markieren** (Stern), Schlagwörter, Mehrfachauswahl (lange drücken), **Papierkorb** (30 Tage, Wiederherstellen).
    Aus dem Explorer auf das Fenster gezogene Dateien landen im geöffneten Ordner.
  - **Aus dem Chat**: Dateien zeigen „aus „Chat““ (Klick öffnet ihn), *Chat-Menü › Dateien dieses Chats* zeigt alles, was
    dort hochgeladen und erstellt wurde – auch nach dem Verschieben (die Chats behalten den alten Pfad, Lora findet die Datei).
  - **Orte**: Ablageorte und Projektordner bleiben verknüpft; ihre Dateien lassen sich hier jetzt auch umbenennen,
    verschieben und löschen.
  - **Lora-Ordner** (Windows): **ein Ordner für alles wie ein Obsidian-Vault** – standardmäßig `Dokumente\Lora`, versteckt
    darin `.lora` mit Chats und Einstellungen. *⋮ › Lora-Ordner …* oder *Einstellungen › Windows*: **Verschieben …**
    (alles wird kopiert, Lora startet dort neu) oder **Anderen Lora-Ordner wählen …**; selbst im Explorer verschoben, fragt
    Lora beim Start, wo er jetzt liegt. API- und Tresor-Schlüssel bleiben am PC. Beim ersten Start mit 1.0.x zieht Lora
    die Daten aus `%APPDATA%\ADEU LLM` dorthin um (ein selbst gewählter Datenordner wird selbst zum Lora-Ordner);
    erstellte Dateien kommen nach *Erstellt*, gemerkte PDFs und Bilder alter Chats nach *Hochgeladen* (Word, Excel und
    andere alte Uploads gab es nur als Text).
  - **Android**: Die Dateien liegen in der App; ein einmal im Systemdialog gewählter Ordner (z. B. `Dokumente/Lora`)
    bekommt eine Kopie und gibt Änderungen zurück (alle zwei Minuten und nach Änderungen). Chats bleiben in der App.
- **Dateiverwaltung** (AP-18): dazu alles, was die App als Datei kennt – Dateien in **Ablageorten** und
  **Projektordnern** (alle Formate) und die **Wissenssammlung**.
  - Die Suche im Tab *Dateien* findet alles davon.
  - **Schlagwörter** für jede Datei (`#Lehre`), als Filter oben; sie werden mit durchsucht.
  - **Volltextsuche über alle Formate**: PDF (mit Seite), Word, Excel, PowerPoint, OpenDocument, RTF, Mails, Text und
    Code – Name, Schlagwörter und Inhalt, Groß-/Kleinschreibung und Akzente egal, die Textstelle mit den Wörtern fett.
    Der Text jeder Datei wird einmal gelesen und im Cache (`volltext/`) gehalten, bis sie sich ändert; ohne Datenbank.
    Im Chat findet `search_files` dieselben Dateien („Wo hatte ich den Kooperationsvertrag?“).
  - **Frag diesen Ordner** (AP-58, ⋮ an einem Ordner): liest alle Dateien des Ordners (mit Unterordnern) mit Fortschritt
    und öffnet einen Chat im **Quellenmodus** über genau diese Dateien – das Werkzeug `search_folder` liefert die Stellen
    mit Datei und Seite, die Belegprüfung prüft jedes Zitat, ein Klick öffnet das PDF an der Seite. Wirkt der Ordner
    vertraulich, empfiehlt die App „Nur TU Dresden“.
  - **Windows-Integration** (AP-59): Dateien aus dem Explorer in den offenen Ordner ziehen (Kopie) und **aus Lora hinaus**
    über den Griff ⋮⋮ an jeder Zeile in den Explorer, eine Outlook-Mail oder ein anderes Programm ziehen (auch mehrere
    ausgewählte); *Als Datei kopieren* legt Dateien in die Zwischenablage (Strg+V im Explorer oder in einer Mail).
    **„Mit Lora öffnen“ / „Lora fragen“** im Kontextmenü des Explorers (und „Frag diesen Ordner“ auf Ordnern) – je
    Benutzer ohne Admin-Rechte, Schalter unter *Einstellungen › Windows › Explorer und Papierkorb*; eine laufende App
    übernimmt die Datei. Wahlweise landen gelöschte Dateien im **Windows-Papierkorb**. **In Word/Excel/PowerPoint öffnen
    und zurück**: im Dateimenü und im Dokument-Menü; solange Office die Datei offen hat, steht „in Word geöffnet“ an der
    Datei und über dem Dokument, und was dort gespeichert wird, kommt binnen einer Sekunde zurück. Test
    `WindowsIntegrationTest`.
  - **Versionen vergleichen**: zwei beliebige Versionen eines Dokuments; **zwei Dateien vergleichen** (auch in
    verschiedenen Formaten, z. B. Word gegen PDF) – grün nur rechts, rot nur links.
  - **Schnellansicht und Details-Spalte** (AP-66, seit 1.0.61): **Leertaste** zeigt die Datei unter der Maus (oder die
    ausgewählte) groß, ohne sie zu öffnen – PDF-Seiten, Bilder, Dokumente, Word- und Textdateien, Tabellen, Folien, den
    Inhalt eines Ordners; ←/→ blättert durch die Dateien des Ordners, Enter öffnet, Leertaste/Esc schließt. Auf Android
    über ⋮ › *Vorschau*. Das ⓘ oben blendet rechts die **Details** ein: Art, Größe, geändert, erstellt, Ort, je nach Datei
    Seiten, Wörter, Folien, Abmessungen, Versionen, offene Kommentare, Schlagwörter und der Chat, aus dem sie stammt.
    Test `QuickLookTest`, Bild `build/files-details.png` (`Round2EndLookTest`).
- **Dokumenten-Scanner** (AP-62, seit 1.0.61; *Dateien › + › Scannen*, Strg+K „Scannen“): Papier wird ein
  **durchsuchbares PDF**. Auf dem Handy die Seite von oben **fotografieren** (Kamera-App) oder aus den Fotos holen, unter
  Windows **vom Scanner** (Scan-Dialog von Windows über WIA, ohne Treiber oder Admin-Rechte) oder Bilder öffnen. Lora findet
  die **Kanten des Papiers** (größte helle Fläche, Viereck größter Fläche in ihrer Hülle – auch schräg fotografiert), die
  Ecken lassen sich mit Finger oder Maus verschieben; die Seite wird **entzerrt** (fast A4 wird genau A4) und aufbereitet:
  *Farbe* (Weißabgleich am Papier), *Graustufen* und *Schwarz-Weiß* (Schatten von Hand und Lampe werden ausgeglichen),
  *Original*. Mehrere Seiten drehen, umsortieren, entfernen, dann *Als PDF speichern*: eine A4-Seite je Scan in
  **Dateien › Scans**, mit **Texterkennung** (Windows: die eingebaute Texterkennung; Android: ein Bildmodell, das der
  Datenschutz erlaubt) als unsichtbare Textebene – suchen, kopieren, „Frag das PDF“. Über den Sync landet das PDF auch auf
  dem PC (**Handy als Scanner**). Tour *Scannen*. Tests `DocScannerTest` (drei schräg fotografierte Seiten → ein
  durchsuchbares PDF im Lora-Ordner), Bild `build/scan-screen.png`.
- **Office-Ausbau** (AP-16): Dokumente fühlen sich an wie Google Docs, mit **Lora rechts als Assistentin** (auf breiten
  Bildschirmen gleich offen) und dem **Lora-Popup** über markiertem Text („Was soll Lora mit der Markierung machen?“,
  dazu Verbessern, Kürzen, Einfacher, Rechtschreibung …).
  - **Echte Dateien**: Dokumente sind `.docx`, Präsentationen `.pptx`, Tabellen `.xlsx`, Markdown `.md` im Lora-Ordner –
    sie öffnen in Word, PowerPoint und Excel. Der Titel ist der Dateiname. In Word geänderte Dateien kommen als neue
    Version zurück. **Verlustfrei**: Kommt eine Datei von außen (oder wurde in Word geändert), schreibt der Editor reine
    Textänderungen Absatz für Absatz, Zelle für Zelle **in das Original** – Formatvorlagen, Kopfzeilen, alles Unbekannte
    bleibt. Ändert sich die Struktur (neue Absätze, andere Formatierung), wird die Datei neu geschrieben, das Original
    bleibt in `.lora/originale`.
  - **Änderungen nachverfolgen** (⋮): Änderungen erscheinen Wort für Wort (rot gestrichen, grün neu) zum Annehmen oder
    Ablehnen, einzeln oder alle – seit 1.0.56 direkt im Fließtext; in Word als echte Überarbeitungen. **Kommentare** werden Word-Kommentare und kommen aus
    Word zurück, ebenso Überarbeitungen aus Word.
  - **Seite einrichten**: Seitenzahlen, Datum, Kopf- und **Fußzeile**, **Seitenränder** (schmal, normal, breit, eigene
    per Lineal), seit 1.0.56 **Erste Seite anders** (Deckblatt), **Gerade Seiten anders** und die Felder `{seite}`/`{seiten}`
    – im Editor als echte Seiten, in PDF und Word ebenso. **Einfügen › Inhaltsverzeichnis** (live im Editor, in Word als Verzeichnisfeld) und
    **Formel (LaTeX)** mit Vorschau – in Word echte Formeln, die beim Zurücklesen wieder LaTeX werden.
  - **Rechtschreibung prüfen** auf dem Gerät (Wörterbücher von Windows bzw. Android, Deutsch und Englisch): Vorschläge
    antippen, Ignorieren, ins Wörterbuch; die KI-Prüfung bleibt daneben. **Zitieren** aus der Literatur mit automatischem
    Literaturverzeichnis gab es schon.
  - **Folien frei gestalten**: auf jeder Folie Textfelder, Bilder und Formen (Rechteck, abgerundet, Ellipse, Pfeil, Linie)
    einfügen, auf der Folie verschieben, an der Ecke in der Größe ändern, Farben, Schriftgröße, nach vorn/hinten;
    Layout *Frei* für leere Folien; **Masterfolie** mit Hintergrund, Akzentfarbe, Fußzeile und Logo. In PowerPoint sind
    es echte, bearbeitbare Formen.
- **Chatsuche mit Filtern** (Lupe in der Seitenleiste): Zeitraum, Projekt, Modell, *Mit Anhängen*, *Mit erstellten
  Dateien*, *Archiv* – auch ohne Suchwort; gefunden werden auch Namen von Anhängen.
- **Literatursuchmodus**: Thema und Kontext angeben (`/Literatursuche`, Kachel *Literatur finden* oder Strg+K) – der Agent sucht
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
  - **Präsentation** (wie PowerPoint): Folienleiste, Folie groß – **genau so, wie die PowerPoint-Datei sie zeigt** (Layout der
    CD-Präsentationsvorlage 2025: Titelfolie in Brillantblau mit Untertitel über dem Titel und Einrichtung, Abschnitte mit
    der Shape, Fußzeile mit Kurzlogo „TUD“ und „Titel • Vortragende • Seite N“, Schriftgröße je Textmenge, Tabellen). Layouts (Titel, Abschnitt, Inhalt, zwei Spalten, Zitat),
    Felder ohne Markdown-Zeichen mit Leiste für fett, kursiv, Stichpunkte, Einrücken; Sprechernotizen, **Folienübersicht**,
    Folien duplizieren/verschieben (Strg+M, Strg+D, Strg+Umschalt+↑/↓), **Vorführen** (F5) mit **Referentenansicht**
    (nächste Folie, Notizen, Timer), Export als PowerPoint im TUD-Design. Fehlt die Einrichtung, fragt der Editor danach.
    Seit AP-51: **Übergänge** (Überblenden, Schieben) und **Stichpunkte nacheinander** je Folie – beim Vorführen und in
    PowerPoint (auch aus fremden .pptx gelesen) –, **Zeichnen** beim Präsentieren (D, E löscht) und Lora baut aus einem
    Dokument ein ganzes Deck („Mach aus dem Dokument eine Präsentation“).
    Seit 1.0.59 (AP-50, AP-51):
    - **Freie Elemente wie in Gestalten**: am runden Griff drehen, beim Verschieben an Rändern, Mitte und anderen Elementen
      einrasten (Strg: frei), mit Umschalt+Klick oder einem Rahmen mehrere auswählen, ausrichten, verteilen, gruppieren;
      neu auf Folien sind TUD-Shape, Symbole, QR-Codes und Tabellen, Text wird direkt auf der Folie getippt und formatiert.
    - **Fremde PowerPoint-Dateien** (auch die TUD-Vorlage) öffnen mit ihren Layouts, Designfarben, Formen, Gruppen,
      Tabellen und Diagrammen; beim Speichern bleibt alles, was der Editor nicht kennt, in der Datei – geschrieben werden
      nur die geänderten Folien.
    - **Zwei Bildschirme**: beim Vorführen laufen die Folien auf dem Beamer, die Referentenansicht mit nächster Folie,
      Notizen und Timer bleibt auf dem Laptop (umschaltbar in der Vorführleiste).
    - **Lora baut Folien aus einem Paper**: „Mach aus diesem Paper eine Präsentation“ baut aus einem PDF ein Deck im
      TUD-Design mit Abschnitten, Tabellen, Abbildungen und Sprechertexten; ✨ › *Folie schöner machen* zeigt Vorschläge
      zum Antippen, Lora schreibt auf Wunsch die Sprechertexte.
  - **Tabelle** (wie Excel): Zellraster mit Spalten- und Zeilenköpfen, **Bearbeitungsleiste mit Formeln** in deutscher
    Schreibweise (`=SUMME(B2:B9)`, `=MITTELWERT`, `=WENN(…;…;…)`, `=RUNDEN`, `=ZÄHLENWENN`, `=SUMMEWENN`, `=STABW`, `&` …,
    auch englische Namen), dazu **SVERWEIS, XVERWEIS, INDEX/VERGLEICH, WENNFEHLER, WENNS, ZÄHLENWENNS, SUMMEWENNS,
    MITTELWERTWENN, LINKS/RECHTS/TEIL, WECHSELN, TEXTVERKETTEN, TEXT, SUMMENPRODUKT, RANG, KGRÖSSTE, VARIANZ, QUANTIL,
    KORREL** u. a.; WENN wertet wie Excel nur den gewählten Zweig aus. Tabellenblätter als Tabs, Zeilen/Spalten einfügen
    und löschen, **nach unten/rechts ausfüllen** (relative Bezüge wandern mit, `$`-Bezüge bleiben), Duplikate entfernen,
    Zeilen und Spalten tauschen, **Filter**, fixierte Kopfzeile, Pfeiltasten wie in Excel, Sortieren, Σ, Statuszeile mit
    Summe/Mittelwert/Anzahl. Der Excel-Export behält die Formeln (mit berechneten Werten).
    Seit AP-48 auch: ganze Spalten (`=SVERWEIS(A2;Tutorien!A:B;2;FALSCH)`), Platzhalter `*`/`?` in Kriterien, XVERWEIS mit
    Vergleichsmodus, rund 45 weitere Funktionen (MAXWENNS, XVERGLEICH, WAHL, INDIREKT, TEILERGEBNIS, HÄUFIGKEIT, TEXTVOR,
    TEXTTEILEN, ZEIT, MTRANS, VSTAPELN …), **Hilfe beim Tippen** (Argumente der Funktion am Cursor, Vervollständigen mit
    Tab), **Suchen und Ersetzen** (Strg+F/Strg+H), **Zellen verbinden**, **Zeilen und Spalten fixieren**, Spaltenbreite
    ziehen, **Auswahllisten** (▾ in der Zelle), **Ausfüllkästchen** zum Ziehen. Excel-Dateien bis 200 000 Zeilen öffnen
    zügig; geteilte Formeln, verbundene Zellen, bedingte Formatierung, Auswahllisten, fixierte Bereiche und Spaltenbreiten
    bleiben beim Öffnen und Speichern erhalten.
    **Formatierung innerhalb einer Zelle**: Einen Teil des Zelltexts in der Bearbeitungsleiste markieren und fett,
    kursiv, unterstrichen, durchgestrichen oder farbig machen (Werkzeugleiste, Strg+B/I/U) – Formeln rechnen mit dem
    reinen Text, Excel bekommt echten Rich Text (auch beim Öffnen fremder Dateien).
    **Diagramme** (AP-49) aus der Markierung in acht Arten (Säulen, Balken, gestapelt, Linie, Kreis, Punkt, Histogramm,
    Boxplot) in TUD-Farben, mit Titel und Achsentiteln, als SVG speicherbar und **verknüpft in Dokumente und Folien**
    einfügbar – dort aktualisieren sie sich beim Öffnen aus der Tabelle. **Lora rechnet** auf dem Gerät: „Berechne die
    Notenverteilung“, „Mittelwert der Note je Gruppe“ (wie eine Pivot-Tabelle) – als Formeln mit Diagramm neben den
    Daten – und „Erkläre die Formel in D2“.
    **Echte Office-Diagramme**: In der Excel-Datei sind die Diagramme echte Excel-Diagramme, die auf die Zellen zeigen
    (ändert man dort Zahlen, zeichnet Excel neu); Diagramme aus fremden Excel-Dateien erscheinen im Editor. In Word und
    PowerPoint werden Diagramme zu echten Diagrammen mit ihren Daten („Daten bearbeiten“ funktioniert dort);
    Boxplots bleiben gezeichnet.
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
  neue `.md`-Dateien erscheinen nach wenigen Sekunden. Den ganzen **Datenordner** synchronisiert *Einstellungen ›
  Synchronisation* (siehe unten).
- **Sync für Android** (AP-28, *Einstellungen › Verbindungen › Mit dem PC synchronisieren*): Liegt der Lora-Ordner des
  PCs in der Nextcloud (AP-12), holt sich das Handy über WebDAV **Chats, Projekte, Gedächtnis, Kurztexte, eigene TU
  Vorlagen, Kontakte und die Dateien** des Lora-Ordners und lädt eigene Änderungen hoch – beim Öffnen der App, alle
  fünf Minuten und beim Wechsel in den Hintergrund. Es gelten die Regeln von AP-12: Das Handy legt eine Notiz wie ein PC
  an; ist der Ordner am PC geöffnet oder sind dessen Änderungen noch unterwegs, wartet es. Haben beide Seiten etwas
  geändert, bleiben beide Fassungen (ein Chat wird zur „zweiten Fassung“, andere Dateien nach `sync/konflikte`).
  Löschungen gehen in beide Richtungen, ungewöhnlich viele halten den Sync an. Transkripte und Einstellungen bleiben je
  Gerät, API-Schlüssel nie im Ordner.
  **Seit AP-63** (1.0.61): auch **Literatur** (mit Exzerpten), die **Wissenssammlung** und der **Tresor** (verschlüsselt,
  eigene PIN je Gerät) – alles wird im Betrieb neu gelesen. **Sync im Hintergrund** ohne geöffnete App (Android
  WorkManager, etwa alle 30 Minuten, nur mit Netz und nicht bei schwachem Akku, auf Wunsch nur im WLAN): ein am PC
  angelegter Literatureintrag ist schon auf dem Handy, bevor die App geöffnet wird. Die Notiz des Handys sagt dann „im
  Hintergrund“, ein PC darf den Ordner öffnen. Test `SyncRestsTest`.
- **Einfacher Sync zwischen PCs** (Windows, *Einstellungen › Synchronisation*, AP-12): wie bei Obsidian liegt der
  Datenordner in einem Cloud-Ordner, der auf allen PCs eingerichtet ist – **Nextcloud**, **Google Drive für Desktop** oder
  **OneDrive** (gefundene Cloud-Ordner stehen als Knopf da: *In OneDrive legen* …). Erster PC: Daten werden kopiert, Lora
  startet neu. Zweiter PC: *Vorhandenen Ordner öffnen* – alles ist da. Am Büro-PC beginnen, am Laptop weiterarbeiten,
  ohne ZIP-Umzug.
  - **Was synchronisiert**: Chats, Projekte, Dokumente, Literatur, TU Vorlagen, Gedächtnis und Einstellungen
    (`einstellungen.json`). **API-Schlüssel, Tokens, Konten und Umgebungen nie**: Sie liegen mit DPAPI verschlüsselt in
    `%APPDATA%\ADEU LLM\schluessel.dat` auf jedem PC. Bis 1.0.44 lagen alle Einstellungen verschlüsselt im Datenordner,
    die ein zweiter PC nicht lesen konnte und überschrieb; beim ersten Start teilt Lora sie einmal auf. Fensterposition,
    Update-Stand und `log.txt` bleiben ebenfalls auf dem PC, große Dateien (Modelle, Aufnahmen) wie bisher in
    `%LOCALAPPDATA%`.
  - **Nie gleichzeitig**: Jeder PC hinterlegt in `sync/geoeffnet-<PC>-<id>.json` eine kleine Notiz (seit wann offen,
    alle zwei Minuten „lebt noch“, beim Beenden „geschlossen“, dazu die in der Sitzung geänderten Dateien mit Größe).
    **Vor dem Laden** prüft Lora: Ist sie auf einem anderen PC offen („Lora ist auf „BUERO-PC“ geöffnet – seit …“) oder
    sind dessen Änderungen noch nicht angekommen („3 Dateien fehlen noch, z. B. ein Chat“), wartet ein kleines Fenster und
    geht von selbst weiter, sobald alles da ist (*Trotzdem öffnen* / *Beenden*). Läuft Lora dort nur im Infobereich
    ohne Arbeit, beendet sie sich von selbst, sobald ein anderer PC den Ordner öffnet; mit offenem Fenster fragt sie.
    Notizen ohne Lebenszeichen seit 15 Minuten (Absturz, Standby) blockieren nicht.
  - **Konfliktkopien** des Cloud-Dienstes (OneDrive „name-PC.json“, Google Drive „name (1).json“, Nextcloud/Dropbox
    „conflicted copy“, Syncthing „sync-conflict“) erkennt Lora beim Start und **behält beide Fassungen**: ein Chat
    erscheint als zweiter Chat „… (zweite Fassung)“, alles andere liegt unter `sync/konflikte/<Datum>/`. Nichts wird
    gelöscht. *Synchronisation beenden* kopiert die aktuellen Daten zurück in den Standardordner.
- **Browser neben dem Chat auf Android** (AP-63, seit 1.0.61; Schalter *Einstellungen › Chat & Werkzeuge › Links in Lora
  öffnen*): die einfache Fassung des PC-Browsers – Links öffnen sich in einem Browser in der App (WebView; auf dem Handy
  über der App, auf Tablets rechts daneben), mit Zurück/Vor, Adresszeile und ↗ in den normalen Browser. Unter der Seite
  *Frage zur Seite*, *Zusammenfassen*, *In die Literatur* (Zitierangaben aus den Meta-Tags der Zeitschrift oder über die
  DOI), *In die Wissenssammlung* und bei PDFs *PDF im Werkzeugkasten* (mit den Anmelde-Cookies des Browsers).
- **Android zieht nach** (AP-64, seit 1.0.61): **Folien auf dem zweiten Bildschirm** – ein Beamer an HDMI/USB-C oder eine
  drahtlose Übertragung (Miracast) bekommt die Folien, die Referentenansicht mit Notizen, nächster Folie und Zeit bleibt
  auf Handy oder Tablet (Android-`Presentation`; ein später angesteckter Beamer wird erkannt); **Wischen** blättert, der
  Bildschirm bleibt beim Vorführen an. **Speichern** fragt zuerst nach einem Ordner im **Lora-Ordner** (der
  Android-Dialog erreicht ihn nicht – so landen Exporte auch per Sync am PC), *Anderswo …* öffnet den Systemdialog.
  **PDF verkleinern** jetzt auch auf Android (im Werkzeugkasten und als Lora-Aktion). Fließtext-Editor, Tabellen, Folien,
  Lora-Aktionen für Dateien und PDF, Gedächtnis und Touren laufen auf Android über den gemeinsamen Code; Videoeditor und
  Gestalten bleiben am PC. Test `AndroidCatchUpTest`.
- **Browser neben dem Chat** (Windows, AP-14): Links aus Chat, Literatur und Dokumenten öffnen sich wie bei Google
  Antigravity **als Tabs rechts neben der App** – die App wird schmaler statt verdeckt, die Breite lässt sich ziehen; auf
  schmalen Fenstern liegt der Browser über der App. Oben Tabs, Zurück/Vor/Neu laden und eine Adresszeile (Suchbegriffe
  gehen an DuckDuckGo), ↗ öffnet die Seite im normalen Browser. Unter der Seite:
  - **Frage zur Seite**: hängt die Seite als Text an den gerade offenen Chat (sonst einen neuen im **Quellenmodus**, AP-08)
    – die Antwort belegt mit Zitaten aus der Seite.
  - **Zusammenfassen**, **In die Literatur** (DOI aus den Meta-Tags → Crossref, sonst Autor:innen, Jahr, Zeitschrift aus
    den `citation_*`-Tags oder die Seite als Webquelle mit Abrufdatum), **In die Wissenssammlung**.
  - **PDF im Werkzeugkasten**: lädt das PDF eines Papers (`citation_pdf_url` oder die PDF-Adresse selbst) mit den Cookies
    des Browsers – also auch hinter der TU-Anmeldung – und öffnet es im PDF-Werkzeugkasten. Heruntergeladene PDFs öffnen
    sich dort ebenfalls, andere Downloads landen in *Downloads*.
  - **Technik**: keine eigene Engine – Chromium über **JCEF** (`me.friwi:jcefmaven`). Chromium ist rund 170 MB groß und
    steckt deshalb **nicht in jedem Update**: Beim ersten Link fragt Lora einmal und richtet es dann ohne Admin-Rechte in
    `%LOCALAPPDATA%\ADEU LLM\browser` ein (Download von GitHub bzw. Maven Central). Das Profil dort behält Cookies, eine
    Anmeldung über die TU (Shibboleth) bleibt also erhalten. *Einstellungen › Windows › Links neben dem Chat öffnen*
    schaltet den Browser ab (dann wie bisher der normale Browser). Download-Seiten der App öffnen immer extern.
    Bekannte Grenze: Dialoge der App, die über dem Browser liegen, verdeckt die Browserfläche teilweise (Chromium ist
    ein eigenes Fenster).
  - Hängt Chromium auf einem PC beim Start, merkt Lora das (`browser/start.pending`) und startet ihn beim nächsten Mal nicht
    von selbst, sondern bietet *Erneut versuchen* oder den normalen Browser an.
  - Test mit echtem Chromium (für Windows gedacht; in Linux-Containern kann CEF beim Initialisieren hängen):
    `ADEU_BROWSER_TEST=1 ./gradlew -p desktop test --tests '*BrowserLiveTest*'`.
- **Videoeditor** (Windows, AP-25, Werkzeug *Videoeditor*, anfangs aus; Vorbild DaVinci Resolve im Alltagsumfang):
  Projekte aus Videos (auch Bildern), die bleiben, wo sie sind. **Zeitleiste** mit Video-, Titel-, Ton- und
  Untertitelspur: Clips verschieben, an den Rändern kürzen, am Abspielkopf **teilen** (S), Übergänge *Überblenden* und
  *Über Schwarz*, Lautstärke und stumm je Clip; Leertaste spielt, Pfeiltasten gehen bildweise. **Vorschau** aus kleinen
  Proxy-Dateien (360 Zeilen, im Hintergrund erstellt) mit Ton; Titel und Untertitel liegen genau so darüber wie im Export.
  - **Titel, Bauchbinde, Einblendung, Abspann** im TUD-Design (Noto Sans, TUD-Blau, weißes Logo), mit sanftem Ein- und
    Ausblenden – gezeichnet mit dem Renderer von *Erstellen*, ffmpeg legt sie nur noch darüber.
  - **Ton**: Musik oder Sprecherton unterlegen (Lautstärke, Ein-/Ausblenden), **Rauschminderung** leicht/stark (mit dem
    Videoeditor-Paket über das Sprachmodell RNNoise, sonst mit dem FFT-Filter von ffmpeg), **Lautheit angleichen**
    (−16 LUFS), Pegel.
  - **Untertitel** aus der vorhandenen **Transkription** (Transkript je Video zuordnen oder *Jetzt transkribieren*; die
    Untertitel folgen dem Schnitt) oder aus SRT/VTT; **einbrennen** oder als SRT/VTT neben dem Video speichern.
  - **Rohschnitt aus dem Transkript**: *Pausen und Füllwörter herausschneiden* (lange Pausen kurz, „äh“/„ähm“ heraus, wo
    Wortzeiten gemessen sind) und **Mit Lora kürzen** („nur die Erklärung des Versuchsaufbaus, höchstens 5 Minuten“ –
    Lora wählt die Abschnitte). Alles mit Strg+Z rückgängig.
  - **Export** als MP4 (H.264/AAC, für das Web optimiert) mit Vorgaben *OPAL* (720p, kleine Datei), *YouTube* (1080p) und
    *Wie das Projekt*; mit Fortschritt und Abbrechen. Ein einziger ffmpeg-Lauf (das ffmpeg der App) baut den Film.
- **Videoeditor: mehrere Spuren und echte Vorschau** (AP-52, seit 1.0.57):
  - **Beliebig viele Video- und Tonspuren**: über V1 liegen V2, V3 … (oben liegt vorn), darunter A1, A2 … (Musik,
    Sprecherton, zweites Mikrofon). Jeder Clip hat **Position, Größe, Zuschnitt und Deckkraft** (*Position und Größe …*
    in den Eigenschaften). Alte Projekte öffnen unverändert: ihre Tonspur ist A1.
  - **Bild-in-Bild** als Standardfall: *Bild-in-Bild* in der Leiste über der Zeitleiste legt eine Webcam (oder ein Bild,
    ein Logo) an den Abspielkopf auf die nächste freie Spur, mit Vorlagen für die Ecke (unten rechts, unten links, oben …,
    ganzes Bild) und sanftem Ein- und Ausblenden.
  - **Echte Vorschau**: Sie läuft durch **denselben ffmpeg-Filtergraphen wie der Export** (`VideoRender.graph`), nur in
    360 Zeilen aus den Proxys – mit allen Spuren, Bild-in-Bild, Übergängen, Ton aller Spuren und Musik; Titel und
    Untertitel zeichnet der Editor mit derselben Zeichnung wie der Export darüber (jetzt auch mit Ein- und Ausblenden).
    Nur das Angleichen der Lautheit (braucht Sekunden Vorlauf) bleibt dem Export. **J/K/L** (mehrmals: 2×, 4×),
    Einzelbild ←/→, **Schleife In–Out** (I, O, *Schleife*). Standbilder mit mehreren Spuren oder in einer Überblendung
    kommen ebenfalls aus dem Graphen. **Wellenformen** auf den Tonspuren und unter den Clips von V1.
  - Export: ein Filtergraph für alle Spuren; gesperrte, stumme und ausgeblendete Spuren werden beachtet.
  Tests `VideoTracksTest` (Vorschau und Export bei Bild-in-Bild und mitten in einer Überblendung Bild für Bild verglichen,
  Bilder in `desktop/build/video-tracks`).
- **Videoeditor: Schnittwerkzeuge** (AP-53, seit 1.0.57; Tasten nah an DaVinci Resolve, Übersicht mit `?` oder F1):
  - **Medienpool** links mit Ordnern, Vorschaubildern, Dauer und Suche; ein Klick öffnet die Datei im **Quellmonitor**
    (Q wechselt zwischen Quelle und Zeitleiste) mit eigenem Abspielkopf und In/Out, dann **Einfügen** (F9),
    **Überschreiben** (F10) oder **Bild-in-Bild** (F12) am Abspielkopf – oder die Datei aus dem Pool in die Zeitleiste
    **ziehen** (V1 fügt am nächsten Schnitt ein, V2 … als Bild-in-Bild, A … als Ton).
  - **Werkzeuge**: Auswahl (A), **Klinge** (B, schneidet jeden Clip und Ton), **Trimmen** (T: am Schnitt *Roll*, in der
    Mitte *Slip*); in der Auswahl kürzt Ziehen an den Rändern als **Ripple**. **Magnet-Zeitleiste** (N): Schnitte auf V1
    ziehen alle Spuren mit, Löschen schließt die Lücke (Umschalt+Entf immer), Ziehen rastet an Kanten, Markern und dem
    Abspielkopf ein.
  - **Bild und Ton verknüpfen**: *Ton auf eine Tonspur legen* (oder Strg+Alt+L) legt den Ton eines Clips verknüpft auf
    eine Tonspur – er bewegt, teilt und kürzt sich mit dem Clip.
  - **Mehrere Tonquellen synchronisieren**: Ansteckmikrofon oder Rekorder auf eine Tonspur, dann *Mit dem Bild
    synchronisieren › Über die Wellenform* – die App vergleicht, wann es lauter wird (Kreuzkorrelation, auf etwa 10 ms
    genau), verknüpft den Ton mit dem Kameraclip und schaltet den Kameraton auf Wunsch stumm.
  - **Spuren sperren, stumm, ausblenden** über die Köpfe links in der Zeitleiste; *+ Videospur*, *+ Tonspur* in den
    Projekteinstellungen.
  - **Marker mit Notiz** (M): in der Leiste über der Zeitleiste, verschiebbar; als **Kapitel** im MP4 und als Liste für
    die YouTube-Beschreibung (*Für YouTube kopieren*).
  Tests `VideoCutTest` (u. a. ein Vortrag mit Kamera und Ansteckmikrofon: synchronisieren, schneiden, Ripple-Trimmen,
  eine Folie aus dem Quellmonitor einfügen – ohne Lücken, im Export lippensynchron).
- **Videoeditor: einfache Effekte** (AP-54, seit 1.0.59; Abschnitt *Effekte* in den Clip-Eigenschaften):
  - **Tempo und Bild**: Clips doppelt so schnell oder in Zeitlupe (0,25×–4×, Ton ohne Mickymaus-Stimme), Standbild,
    kurze Stücke rückwärts, Helligkeit/Kontrast/Sättigung und **Weißabgleich auf Knopfdruck** – Vorschau und Export sehen
    gleich aus.
  - **Zoom und Schwenk mit Keyframes**: im Screencast sanft auf einen Bildschirmbereich zoomen und wieder heraus, auch als
    **Hochformat 9:16** – oder einfach „Zoome oben rechts hinein“ zu Lora sagen.
  - **Anonymisieren für Forschungsmaterial**: *Gesichter verpixeln* findet Gesichter auf dem eigenen PC (YuNet im
    Videoeditor-Paket, nichts verlässt den Rechner), die Verpixelung folgt den Personen; dazu Bereiche von Hand verpixeln
    oder weichzeichnen und **Stimmen verfremden** – jede Anonymisierung steht im Protokoll. Test `VideoEffectsTest`.
- **Videoeditor: textbasierter Schnitt** (AP-55, seit 1.0.60; links im Editor *Transkript* statt *Medien*): das
  Transkript der Clips auf V1 als Text neben der Zeitleiste – ein Klick auf ein Wort springt dorthin, das gesprochene Wort
  ist beim Abspielen hervorgehoben. **Wörter markieren und Entf = herausschneiden** (Umschalt+Klick oder Ziehen markiert;
  der Clip wird geteilt, verknüpfter Ton geht mit, mit dem Magneten rückt alles nach), Absätze mit ↑/↓ verschieben
  (= Clips verschieben), Sprecher:innen am Absatz, **Füllwörter** (orange) und **lange Pausen** markiert und mit einem Klick
  entfernt bzw. gekürzt. **Lora als Schnitt-Assistentin**: „Kürze auf 10 Minuten, behalte die Beispiele“ – die Vorschläge
  stehen durchgestrichen im Text, jeder einzeln annehmen oder ablehnen; auch als Lora-Aktion *Im Transkript schneiden*.
  Rückgängig wie überall. Test `TextCutTest` (u. a. ein Lehrvideo nur über den Text geschnitten, Export = Zeitleiste).
- **Videoeditor: ruhiges Bild und anpassbare Oberfläche** (AP-70):
  - **Ruhige Vorschau**: Standbild und Wiedergabe sind gleich groß und liegen genau übereinander, auch bei 4:3- und
    Hochformat-Aufnahmen. Beim Ziehen des Abspielkopfs folgt das Bild flüssig und wird nie schwarz; mit Ton läuft die
    Wiedergabe gleichmäßig. Titel und Untertitel blinken nicht mehr, wenn sich die Größe ändert oder man tippt.
  - **Anpassbar**: Medienpool, Vorschau, Eigenschaften und Zeitleiste lassen sich an ihren Rändern ziehen, Medienpool
    und Eigenschaften einklappen, die Vorschau gibt es groß (Alt+F) oder im Vollbild (Strg+F). Spuren lassen sich höher,
    niedriger und ganz einklappen. Im Editor ist die Seitenleiste der App eingeklappt (Knopf oder Strg+B). Die Anordnung
    bleibt nach dem Neustart erhalten; *Anordnung zurücksetzen* stellt sie wieder her.
  - **Übersichtlicher**: Werkzeuge und Aktionen als Symbole mit Tooltip und Taste, Eigenschaften nur passend zum
    gewählten Element, auch bei 1366×768 und 150 % ohne Umbruch. Tour-Karten stehen neben dem Ort, den sie erklären.
  - **Kein grauer Kasten** mehr beim Überfahren von Knöpfen und Chips (in der ganzen App) – nur eine leichte Tönung;
    der deutliche Tastaturfokus bleibt.
- **Bildschirmaufnahme** (AP-56, *Videoeditor › Bildschirm aufnehmen*): ganzer Bildschirm, ein Fenster oder ein
  gezogener Bereich, dazu **Webcam**, **Mikrofon** und **Systemton** (über ein Aufnahmegerät wie „Stereomix“), Countdown,
  Mauszeiger und **Klicks als Ringe**. Ein ffmpeg-Prozess nimmt alles mit derselben Uhr auf, jede Quelle in eine eigene
  Datei (`Lora-Ordner/Aufnahmen/<Datum>`). Während der Aufnahme eine kleine Leiste oben (sie selbst ist nicht im Bild),
  **Strg+Umschalt+F9** pausiert, **Strg+Umschalt+F10** stoppt – auch in anderen Programmen –, ebenso im Infobereich.
  Nach dem Stopp liegt sofort ein **Projekt mit getrennten Spuren** im Editor: Bildschirm auf V1, Webcam als Bild-in-Bild
  auf V2, Klicks auf V3, Mikrofon auf A1, Systemton auf A2; auf Wunsch **sofort transkribiert** (das Transkript wird
  zugeordnet, sobald es fertig ist). In der Referentenansicht der Folien nimmt ein Knopf die Präsentation mit Mikrofon
  auf. Test `ScreenRecorderTest`.
- **Export mit Grafikkarte und Warteschlange** (AP-57): der Export erkennt **NVIDIA NVENC, Intel Quick Sync, AMD AMF** bzw.
  Apple VideoToolbox (ffmpeg muss den Encoder kennen *und* eine Probe-Kodierung muss gehen) und rechnet damit deutlich
  schneller; geht die Grafikkarte mitten im Export nicht, rechnet der Prozessor (x264) weiter. **Mehrere Ziele auf einmal**
  (OPAL 720p, YouTube 1080p, wie das Projekt, **nur Ton** als M4A, **Untertitel** als SRT/VTT, **Kapitel** für die
  YouTube-Beschreibung; im MP4 sind die Kapitel immer), Ziel Lora-Ordner, eigener Ordner oder zusätzlich **Nextcloud**
  (gestreamt hochgeladen). Die Exporte laufen nacheinander **im Hintergrund** – weiterarbeiten, Fortschritt als Chip
  „Exporte 1/3 · 45 %“ in der Kopfzeile, Liste mit Abbrechen, Öffnen, Kapitel kopieren. Test `ExportQueueTest`.
- **Diagramme und Whiteboard** (AP-61, *Erstellen › Diagramme & Whiteboard*, im Dokument *Einfügen › Diagramm …*):
  **Flussdiagramme, Mindmaps, Organigramme und Zeitstrahlen**, deren **Verbinder an den Formen haften** (ziehen, die
  Pfeile gehen mit), **automatisch angeordnet** oder von Hand; Formen Rechteck, abgerundet, Raute, Kreis, Notiz. **Lora
  zeichnet**: „Mach ein Flussdiagramm aus diesem Ablauf“ – im Editor oder als Lora-Aktion *Diagramm zeichnen* direkt ins
  offene Dokument. **Mermaid-Text ↔ Diagramm** (flowchart/graph, mindmap, timeline) daneben, live in beide Richtungen.
  **Whiteboard**: freie Fläche (verschieben, Mausrad zoomt) mit Notizzetteln und Freihand. Im Dokument steht ein Diagramm
  als ` ```mermaid `- bzw. ` ```diagram `-Block, wird gezeichnet, ein Klick öffnet den Editor; im PDF als Vektorgrafik, in
  Word und PowerPoint als Bild mit den Verbindungen als Alternativtext; auf Folien mit dem Layout *Diagramm*. Tests
  `DiagramsTest`.
- **Lernspiele-Baukasten** (AP-17, Werkzeug *Lernspiele*, anfangs aus): Lora baut aus Skript, Folien oder Beschreibung
  **eigenständige Browserspiele** – Quiz, Zuordnen, Memory, Escape-Room, Lückentext … – als *eine* HTML-Datei mit CSS und
  JavaScript („Mach ein Zuordnungsspiel zu Kapitel 3 des angehängten Skripts“). Modus *Lernspiel* (`/Lernspiel`, nur bei
  eingeschaltetem Werkzeug) mit Vorgaben für gute Spiele (Anleitung, Rückmeldung je Antwort, Punktestand, Auswertung,
  barrierearm, Handy und PC); bei eingeschaltetem Werkzeug kann der Agent auch in normalen Chats Spiele bauen
  (`create_learning_game`, `read_learning_game`, `update_learning_game`).
  - **Direkt testen**: Unter der Antwort erscheint eine Karte mit *Spielen* (Windows: als Tab im Browser neben dem Chat,
    sonst im normalen Browser; Android: Vollbild in der App) und *Exportieren*. Ändern per Chat („mach Level 2 schwerer“) –
    jede Änderung ist eine neue **Version**, frühere lassen sich spielen und wiederherstellen.
  - **Sammlung** (*Mehr › Lernspiele*): Suche, Spielen, Versionen, *Im Chat ändern*, Löschen, *Neues Lernspiel*.
  - **Export für OPAL/Moodle**: *ZIP* (index.html und eine kurze Anleitung) oder *HTML-Datei*.
  - **Abgeschottet**: Jedes Spiel bekommt eine Content-Security-Policy, die jede Anfrage verbietet (kein Netzwerk, keine
    anderen Dateien, keine Frames) – auch nach dem Export. Externe Skripte und Stylesheets lehnt die App schon beim
    Speichern ab; auf Android läuft die WebView zusätzlich ohne Netzwerk- und Dateizugriff.
- **KI-Nutzungsnachweis** (AP-19): Geht es in einem Chat um eine Arbeit (Hausarbeit, Bachelor-/Masterarbeit, Dissertation,
  Paper, Förderantrag, Abstract …) **und** hat die KI dafür Text geschrieben (ein Dokument oder längere Texte nach
  „formuliere“, „überarbeite“, „kürze“ …), erscheint über dem Eingabefeld einmal ein **Pop-up-Vorschlag** *KI-Nutzung
  dokumentieren?* – nie bei Mails, Verwaltung oder bloßen Fragen. Der Nachweis entsteht auf dem Gerät aus dem Chat (im
  Projekt wahlweise aus allen Chats): Modelle, Zweck (Formulierungshilfe, Überarbeitung, Gliederung, Recherche …),
  Zeitraum, genutzte Funktionen, entstandene Entwürfe; betroffene Teile werden als „[bitte ergänzen …]“ markiert. Formate:
  **Prüfungsleistung** (Erklärung mit Tabelle), **Verlag/Zeitschrift** (übliche englische Erklärung mit deutscher
  Fassung), **kurze Fußnote** – kopieren oder als Dokument öffnen. Immer erreichbar im Chat-Menü (*KI-Nutzungsnachweis*);
  der Vorschlag lässt sich unter *Einstellungen › Lora* abschalten.
- **Stapel & Massenkontrolle** (AP-20, Werkzeug *Stapel & Massenkontrolle*, anfangs aus): viele Dateien mit **demselben
  Auftrag** – nacheinander im Hintergrund statt in einem endlosen Chat. Dateien kommen aus der Mehrfachauswahl, einem
  **ZIP** (z. B. die Abgaben aus OPAL; ZIPs je Person werden mit geöffnet), einem **Ordner** (Windows) oder der Nextcloud;
  der Text wird auf dem Gerät gelesen. Vorlagen für den Auftrag: Zusammenfassen, Exzerpt, Steckbrief, Übersetzen – oder
  ein eigener. Lora sitzt dabei auf der Fortschrittskarte („23 von 80“) und arbeitet Akten ab.
  - **Pause und Fortsetzen**, übersteht einen Neustart (jedes Ergebnis wird sofort gespeichert; eine Datei, die beim
    Schließen lief, kommt zurück in die Warteschlange), läuft im Hintergrund (Windows im Infobereich, Android mit
    Benachrichtigung) und auf Wunsch **nur nachts** (22–6 Uhr). Fehlgeschlagene Dateien werden dreimal versucht, danach
    geht es mit der nächsten weiter; *Fehler erneut versuchen* holt sie nach. Eine Benachrichtigung meldet das Ende.
  - **Chats haben Vorrang**: Solange ein Chat antwortet, wartet der Stapel; kennt die App das Limit des API-Keys, lässt er
    zwei Anfragen pro Minute für Chats frei (eigene Grenze möglich).
  - **Standard: pseudonymisiert und „Nur TU Dresden“** – auch wenn die Pseudonymisierung für Chats aus ist; externe
    Anbieter sind dann gesperrt.
  - **Massenkontrolle** (typisch 30–100 Abgaben): Bewertungsraster als Tabelle (je Zeile *Kriterium; Punkte;
    Beschreibung*, aus Excel oder der TU Vorlage *Bewertungsraster* einfügbar) plus Hinweise (Erwartungshorizont).
    **Kalibrierung**: 2–3 Arbeiten vor dem Start von Hand bewerten – sie gehen als Maßstab an die KI. Je Arbeit kommen
    Punkte je Kriterium mit Begründung und **Belegstellen** (wörtliche Zitate mit Seite); die App prüft auf dem Gerät, ob
    jedes Zitat wirklich in der Arbeit steht (✓/✗). **Die KI schlägt nur vor, ein Mensch entscheidet**: *Vorschlag
    übernehmen* oder Punkte anpassen, mit Notiz – jede Entscheidung steht mit Zeit im **Protokoll** (KI-Verordnung).
    **Ausreißer** (weit weg von den anderen, nicht bewertbar oder stark von der Entscheidung abweichend) sind markiert.
  - **Exporte**: Ergebnistabelle als Excel, **Rückmeldung je Person** als Word-Dateien in einem ZIP, Protokoll als Word;
    beim normalen Stapel alle Ergebnisse als ein Word-Dokument (auch direkt in die Nextcloud).
  - Lora kann einen Stapel auch aus den Anhängen eines Chats anlegen (`create_batch`, `batch_status`, `read_batch_results`).
- **Antrags-Werkstatt** (AP-21, Werkzeug *Antrags-Werkstatt*, anfangs aus; Modus *Antrag*): von der Ausschreibung bis zur
  Abgabe. Lora liest die **Ausschreibung** (PDF oder Link) und legt den Antrag an: Förderer und Programm, Zusammenfassung,
  **Checkliste** zum Abhaken, **Fristen** (mit *In den Kalender* als Kalenderdatei, Erinnerung eine Woche vorher) und die
  **Gliederung** mit den Grenzen der Ausschreibung. Vorlagen für **DFG** (Sachbeihilfe, Programmpauschale 22 %),
  **BMBF** (Projektskizze, Projektpauschale 20 %), **EU** (Horizon Europe Part B, 25 % indirekte Kosten, 45 Seiten),
  **SAB**, **TUD-intern** und andere – immer nur ein Start, die Ausschreibung gewinnt.
  - **Gliederung mit Zeichen-/Seitenwächter**: Text je Abschnitt hier oder mit Lora schreiben; die App zählt Zeichen,
    Wörter und Seiten (einstellbar, Standard 3.000 Zeichen je Seite) und färbt, was zu lang ist.
  - **Arbeitspakete mit Gantt** (Balken über die Monate, Jahre markiert), **Budget** je Projektjahr nach den Kategorien
    des Förderers mit Pauschale, **DMP** und **Ethik** (die TU Vorlagen *Datenmanagementplan* und *Ethikantrag* helfen).
  - **Probelauf mit Gutachter-Rolle**: Lora begutachtet den Antrag nach den Kriterien des Förderers (Stärken, Schwächen,
    Fragen, die fünf wichtigsten Änderungen); das Ergebnis bleibt am Antrag.
  - **Abgabe-Check**: offene Punkte der Checkliste, nahe oder verpasste Fristen, leere oder zu lange Abschnitte,
    Seitenlimit, Arbeitspakete außerhalb der Laufzeit, Budget, DMP, Ethik, Probelauf. Export des ganzen Antrags als Word.
  - Werkzeuge für Lora: `save_proposal`, `write_proposal_section`, `read_proposal`, `save_proposal_review`; gut
    kombinierbar mit Rückfragen (Planungsmodus, AP-06).
- **Nextcloud** (AP-22, *Einstellungen › Verbindungen*, Android und Windows): **Im Browser anmelden** (Nextcloud-Login,
  auch mit TU-Login und zweitem Faktor; die App bekommt ein eigenes App-Passwort, kein Passwort wird in die App getippt)
  oder Anmeldename und App-Passwort eintragen. Vorbelegt ist der Cloudstore der TU Dresden. Danach:
  - **Anhängen** im Chat („+“ › *Aus Nextcloud*), **holen** in den Lora-Ordner (*Dateien* › ⋮ › *Aus Nextcloud holen*),
    **speichern** an jeder Dokumentkarte im Chat und an jeder Datei im Tab *Dateien* (*In Nextcloud speichern*, Ordner
    wählbar, Standard *Ordner für Ergebnisse*; eine gleichnamige Datei bleibt erhalten, die neue bekommt „ (2)“).
  - Lora kann selbst nachsehen, lesen und Ergebnisse ablegen (`nextcloud_list`, `nextcloud_read`, `nextcloud_save`; das
    Speichern läuft über die Freigaben) – nicht im Tresor und nicht in Projekten „Nur auf diesem Gerät“.
  - Technik: WebDAV über OkHttp auf beiden Plattformen – die Grundlage für den Sync unter Android (AP-28).
- **MCP-Client** (AP-23, *Einstellungen › Verbindungen › MCP-Server*): Werkzeuge anderer Dienste über das **Model Context
  Protocol**. **Fern-Server** (Streamable HTTP, Android und Windows) mit festem Token (auch in eigenem Header) oder
  **OAuth** (Anmeldung im Browser mit PKCE, die App meldet sich beim Server selbst an; Token werden erneuert), **lokale
  Server** unter Windows als Programm über stdio (z. B. `npx -y @modelcontextprotocol/server-filesystem "C:\Daten"`, mit
  Umgebungsvariablen).
  - *Verbinden* listet die Werkzeuge; **je Werkzeug Erlauben, Fragen oder Sperren** (Standard: Fragen). *Fragen* läuft
    über die **Freigaben** wie andere Aktionen nach außen, *Sperren* versteckt das Werkzeug vor dem Modell.
  - **Datenschutzstufe**: Fremde Werkzeuge gibt es nie im Tresor und nie in Projekten „Nur auf diesem Gerät“; in Projekten
    „Nur TU Dresden“ nur von Servern, die als *läuft an der TU Dresden oder auf diesem PC* markiert sind. Tokens liegen
    verschlüsselt (Windows: nur auf diesem PC, nicht im Sync).
- **Agent in lokalen Ordnern** (Windows, *Einstellungen › Lokale Ordner*): freigegebene Ordner kann der Assistent in jedem Chat
  auflisten, durchsuchen und lesen (auch Word, PowerPoint, Excel, PDF), Dateien anlegen und ändern, Dokumente direkt als
  .docx/.pdf/.pptx/.xlsx dort speichern (z. B. im TUD-Design), verschieben und löschen (in den Unterordner
  `.adeu-papierkorb`). Ordner lassen sich auf „nur lesen“ stellen; außerhalb kommt er nicht. Rückfragen vor Änderungen
  richten sich nach *Chat & Werkzeuge › Freigaben*.
- **Ordner an einen Chat anhängen** (Windows): *„+“ › Ordner* hängt einen ganzen Ordner an den laufenden Chat – vorher wählst
  du, ob Lora dort **nur lesen** oder **lesen und schreiben** darf. Ein Chip über dem Eingabefeld zeigt Ordner und Recht
  („Seminar · nur lesen“); ein Klick ändert das Recht im laufenden Chat, öffnet den Ordner im Explorer oder löst ihn vom
  Chat. Das Recht gilt nur für diesen Chat und geht vor einer gleichnamigen Freigabe unter *Lokale Ordner*; angehängte
  Ordner sind der Standard für die Ordner-Werkzeuge (lesen, durchsuchen, schreiben, Dokumente speichern). Nicht im Tresor.
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
  **Strg+L** Lora, Strg+Umschalt+L Literatur, Strg+D/T Dokumente, Transkription. **Breiten ziehen**: Die Linie neben der
  Seitenleiste (links) und neben Loras Bereich und dem Browser (rechts) hat einen kleinen Griff; Ziehen macht die Leiste
  breiter oder schmaler, Doppelklick stellt die Standardbreite wieder her. Die Breiten bleiben über Neustarts erhalten
  und werden in kleinen Fenstern begrenzt, damit der Chat Platz behält (PC und Tablet, `ui/PanelResizer.kt`). Suche in
  den Einstellungen. **Sprache der Oberfläche** Deutsch oder Englisch
  (*Einstellungen › Darstellung*): seit AP-29 ist die ganze Oberfläche übersetzt – Einstellungen, Editoren, TU Vorlagen,
  Modelle, Modi, Fehlermeldungen und Loras eigene Antworten; Antworten der Modelle folgen der gewählten Sprache. Loras
  Suche findet Bereiche auch mit englischen Wörtern. Ein automatischer Test prüft den Textkontrast aller Stile nach WCAG.
- **Barrierefreiheit** (AP-30): Screenreader (TalkBack, unter Windows NVDA/JAWS über die Java Access Bridge, die die App
  für sich selbst einschaltet; macOS VoiceOver) lesen Knöpfe mit Beschriftung, Schalterzeilen als *ein* Schalter mit
  Titel und Zustand, Umschalter als Auswahl, Farbfelder mit Namen und Abschnitte als Überschriften (zum Springen). Die
  Tastatur erreicht alles mit Tab, das gewählte Element ist deutlich markiert; große Schrift (Android bis 200 %,
  *Darstellung › Schriftgröße*, Zoom) bricht um statt abzuschneiden. Der Windows-Sprachausgabe *Narrator* fehlt die
  Schnittstelle für Java-Programme – dafür das kostenlose NVDA nutzen.

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
   - **macos_linux** (Standard an): auch macOS und Linux bauen und testen (AP-31). Aus, wenn nur Windows/Android
     erscheinen sollen – macOS-Minuten zählen in privaten Repositorys zehnfach.

   KI-Assistenten: GitHub-MCP `actions_run_trigger`, Methode `run_workflow`, `workflow_id: android.yml`, `ref: main`,
   `inputs: {"version": "38", "notes": "…"}`; Lauf mit `actions_list`/`actions_get` verfolgen.

   macOS signieren und notarisieren (optional, sobald ein Apple-Entwicklerkonto da ist): Secrets `MAC_CERT_P12_BASE64`,
   `MAC_CERT_PASSWORD`, `MAC_SIGN_IDENTITY` (Signieren) und `MAC_NOTARY_APPLE_ID`, `MAC_NOTARY_PASSWORD`,
   `MAC_NOTARY_TEAM_ID` (Notarisieren). Ohne sie ist die Mac-App nur ad hoc signiert (Hinweis beim ersten Start).
3. Der Workflow (ca. 15–20 Min.) baut, testet (Emulator, Selbsttest unter Windows, macOS und Linux), legt hier ein Release
   an und veröffentlicht APK, Windows-ZIP/-EXE, macOS-DMG/-tar.gz, Linux-tar.gz/-deb und `version.json` im öffentlichen
   Repo `ADEU-TUD/ADEU-Harness-Updater` (Schritt „Für automatische Updates veröffentlichen“, Secret `UPDATER_TOKEN`).
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

- **Daten** liegen im **Lora-Ordner** (Standard `Dokumente\Lora`, Daten versteckt in `.lora`, siehe *Dateien*) und
  bleiben bei neuen Versionen erhalten; `%APPDATA%\ADEU LLM` behält nur, was zu diesem PC gehört. API-Key,
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
- **Arbeitsspeicher**: Der Java-Speicher hat eine feste Obergrenze (1,5 GB) und gibt freien Speicher nach etwa einer
  Minute ohne Arbeit an Windows zurück (G1 mit `G1PeriodicGCInterval`, `Min/MaxHeapFreeRatio`, String-Deduplizierung;
  Liste in `desktop/build.gradle.kts` › `MEMORY_JVM_ARGS` und in `release-local.sh`). Die Chat-Liste hält keine langen
  Texte mehr: Anhänge, Werkzeugergebnisse und Denkwege liegen dort nur gekürzt, ganz erst im geöffneten Chat.
  Spracherkennungsmodelle werden nach drei Minuten ohne Nutzung entladen, Vorschaubilder verkleinert gehalten.
  *Einstellungen › Arbeitsspeicher* zeigt Java-Speicher und App gesamt (Working Set wie im Task-Manager) sowie
  Messpunkte (Start, nach großem PDF, nach Transkription, nach einer Stunde, von Hand) und hat *Speicher freigeben*.

## macOS und Linux (AP-31)

Dieselbe App gibt es für **Macs mit Apple-Chip** (M1 und neuer) und **Linux (x64, Ubuntu 24.04 / Debian 13 und neuer)** –
gleicher Code wie unter Windows,
Java-Laufzeit eingebaut, ohne Admin-Rechte.

- **macOS**: `ADEU-LLM-x.y.z-macOS.dmg` öffnen und *ADEU LLM* in *Programme* ziehen. Die App ist (noch) nicht bei Apple
  notarisiert: beim ersten Start Rechtsklick › *Öffnen* bzw. *Systemeinstellungen › Datenschutz & Sicherheit ›
  Dennoch öffnen*. Updates installiert die App danach selbst (aus `ADEU-LLM-x.y.z-macOS.tar.gz`, ohne erneute Rückfrage).
- **Linux**: `ADEU-LLM-x.y.z-Linux.tar.gz` entpacken (z. B. nach `~/Programme`) und `ADEU LLM/bin/ADEU LLM` starten –
  so aktualisiert sich die App selbst. Alternativ `sudo apt install ./ADEU-LLM-x.y.z-Linux.deb` (Startmenü-Eintrag,
  Updates dann über die nächste .deb, da `/opt` schreibgeschützt ist).
- **Daten**: der Lora-Ordner wie unter Windows (`Dokumente/Lora`, unter Linux der Dokumente-Ordner des Desktops, z. B.
  `~/Dokumente`); was zum Rechner gehört in `~/Library/Application Support/ADEU LLM` bzw. `~/.config/adeu-llm` und
  `~/.local/share/adeu-llm`. API-Key und Tokens sind mit AES verschlüsselt, dessen Schlüssel im **Schlüsselbund**
  (macOS) bzw. im **Secret Service** des Desktops (GNOME Schlüsselbund, KWallet) liegt; ohne beides in einer Datei, die
  nur das eigene Konto lesen darf.
- **Bedienung**: Kürzel mit **⌘** statt Strg (Strg geht auch), Hinweise in der App zeigen ⌘, Finder statt Explorer,
  Menüleiste statt Infobereich. *Einstellungen › macOS/Linux* hat „Beim Anmelden starten“ (LaunchAgent bzw.
  `~/.config/autostart`).
- **Klicks auf dem Mac** (`desktop/MacClickFix.kt`): Compose bricht einen Klick ab, wenn zwischen Drücken und Loslassen
  ein Scroll-Ereignis kommt und eine Liste darunter es nimmt – das passiert auf dem Mac ständig mit Magic Mouse, aufgelegtem
  Daumen auf dem Trackpad, Schwung nach dem Wischen oder Logitech-Rädern; man musste dann 2–4-mal klicken. Solange eine
  Taste gedrückt ist und der Zeiger stehen bleibt (höchstens 1,5 s), hält die App Scrollen zurück; Ziehen und Scrollen
  zugleich geht weiter. Den Tastenzustand setzt sie aus den Ereignisarten statt aus der verspäteten Abfrage von macOS.
  *Problem melden* schickt auf dem Mac mit, wie oft das griff, Modell, Bildwiederholrate, Tippen zum Klicken,
  Zeigegeräte und bekannte Maus-Programme (nur feste Bezeichnungen) sowie die Ereignisse vor einem erneuten Klick an
  derselben Stelle.
- **ffmpeg** (Audio/Video öffnen, Transkription von Dateien, Videoeditor) kommt vom System: `brew install ffmpeg` bzw.
  `sudo apt install ffmpeg`; die App findet es auch ohne PATH (Homebrew, MacPorts, Snap). Aufnahmen gehen ohne.
- **Was es dort nicht gibt**: Outlook über COM (dafür *Mail über den Server*), Office → PDF über Microsoft Office,
  Texterkennung von Windows (dafür *Mit KI-Modell erkennen*), das Videoeditor-Paket (der Editor läuft mit dem ffmpeg des
  Systems) und die Ein-Datei-EXE. Lokale Modelle laden llama.cpp für Apple Silicon (Metal) bzw. Ubuntu x64;
  Spracherkennung auf dem Gerät (sherpa-onnx) und der Browser neben dem Chat (Chromium über JCEF) laufen auf beiden.
  Macs mit Intel-Chip werden nicht unterstützt.

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
                            # Transkript-Exporte, Interrater-Reliabilität, Geräteempfehlungen, Gestalten (Bilder in
                            # desktop/build/creative-look), Videoeditor mit echtem ffmpeg-Export (desktop/build/video-look),
                            # Stapel (80 Dateien, Pause, Neustart), Anträge, Nextcloud/MCP/IMAP/Handy-Sync gegen lokale
                            # Testserver, PDF Stufe 3 (Schwärzen, PDF/A, Passwort, PDF/UA) …
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

macOS und Linux (AP-31) bauen auf dem jeweiligen System: `./gradlew -p desktop createDistributable packageDmg` (Mac) bzw.
`createDistributable packageDeb` (Linux). Der Workflow *Build & Release* macht daraus DMG und tar.gz (macOS) bzw.
tar.gz und .deb (Linux) und testet beide mit `--selftest`. Linux baut bewusst mit dem OpenJDK von Ubuntu
(`apt install openjdk-21-jdk`), nicht mit Temurin: Mit der Temurin-Laufzeit stürzt die Spracherkennung (sherpa-onnx)
beim Laden ab.

### Englisch (Texte übersetzen)

Neue Texte der Oberfläche schreibt man auf Deutsch in `tr("…")` (mit `{0}`, `{1}` statt `$`-Vorlagen), in Listen und
Aufzählungen in `trLater("…")`; im Paket `ui` übersetzt `Text("…")` von selbst. Die englische Fassung kommt als Zeile
`Deutsch<TAB>Englisch` nach `app/src/main/resources/i18n/en.tsv` (sortiert, `\n` für Zeilenumbrüche).
`./gradlew -p desktop test --tests '*I18nCoverageTest*'` meldet jeden markierten Text ohne englische Zeile mit Datei und
Zeile und prüft, dass die Platzhalter in beiden Sprachen gleich sind. Prompts an die Modelle und Wörter, mit denen die App
deutsche Eingaben erkennt, bleiben deutsch.

### Logo ändern

Das Logo wird in `ui/Logo.kt` gezeichnet (Lora aus `ui/LoraFigure.kt`, eigene Farben in `LogoColors`). Die App-Symbole
erzeugt `./gradlew -p desktop test --tests '*LogoTest*'` nach `desktop/build/icons`: `app.ico` und `app.png` gehören nach
`desktop/icons/`, `app.png` als `icon.png` und `tray.png` nach `desktop/src/main/resources/`, die Ordner unter `android/`
nach `app/src/main/res/`. Das Mac-Symbol `desktop/icons/app.icns` erzeugt `python3 .github/scripts/make-icns.py` aus
`app.png`. `ChatLookTest` rendert den Chat in allen Stilen nach `desktop/build/chat-look` (für
Vorher/Nachher-Bilder).

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
| `docs/SheetHelp.kt`, `docs/XlsxReader.kt` | Hilfe beim Tippen von Formeln; schnelles, treues Lesen von .xlsx (AP-48) |
| `docs/ChartLinks.kt` | Verknüpfte Diagramme in Dokumenten und Folien, „Lora rechnet“ (`SheetCalc`, AP-49) |
| `docs/PptxReader.kt`, `docs/PptxMerge.kt`, `docs/PaperDeck.kt`, `docs/SlideLook.kt`, `ui/PresentScreens.kt` (beide), `ui/SlideSuggestions.kt`, `ui/SlideCanvasEditor.kt`, `creative/CanvasText.kt`, `creative/CanvasTable.kt`, `creative/CanvasArt.kt` (beide) | Fremde .pptx lesen und ins Original schreiben, zweiter Bildschirm, Deck aus einem Paper, Folien-Vorschläge (AP-51); Folien auf der Gestalten-Leinwand, Text mit Formatierung und Tabellen auf der Leinwand (AP-50) |
| `docs/OfficeCharts.kt`, `docs/CellRich.kt`, `ui/SheetRichText.kt` | Echte Office-Diagramme (DrawingML) für .xlsx/.docx/.pptx schreiben und lesen; Formatierung innerhalb einer Zelle (AP-48, AP-49) |
| `agent/FolderAsk.kt` | Frag diesen Ordner: Dateien, Stellen mit Seite, Quellen der Belegprüfung (AP-58) |
| `ui/DocsEditor.kt`, `ui/DocsText.kt`, `ui/SlidesEditor.kt`, `ui/SheetEditor.kt`, `ui/DocsAssistant.kt`, `ui/DocsList.kt` | Editor-Rahmen, Text, Folien, Tabellen, KI-Mitarbeit, Startseite der Dokumente |
| `data/Pseudonymizer.kt`, `data/PiiDetector.kt`, `ui/PrivacySettings.kt` | Pseudonymisierung (Erkennung, öffentliche Quellen, Platzhalter, Rückersetzung, Ein-Klick-Korrektur) und Einstellungen dazu |
| `data/CommandModel.kt`, `agent/LoraCommands.kt`, `agent/CommandSlots.kt`, `agent/ModelRouter.kt`, `data/LoraWays.kt`, `ui/CommandModelPrompt.kt`, `tools/befehle/` | Befehlsmodell und Mischmodus (AP-71): Etiketten aus dem Code, Erkennung über die ONNX Runtime, Werte aus dem Wunsch, Ausführung, Ort je Anfrage, Zähler, Einstellungen und Dialoge; Messkorpus, Trainingsdaten, Training und Export |
| `data/PiiModel.kt`, `data/PiiRuntime.kt`, `ui/PiiModelPrompt.kt`, `tools/pii/` | Erkennungsmodell der Pseudonymisierung (AP-67): Tokenizer, ONNX Runtime über JNA, Download und Popup; Messkorpus, Trainingsdaten, Training und Export |
| `agent/ReferenceCheck.kt`, `ui/CheckDialogs.kt` | Quellen-Check (Crossref, OpenAlex, Google Books), Dialoge für Quellen und Barrierefreiheit |
| `docs/QtiWriter.kt` | Tests für OPAL/ONYX als IMS QTI 2.1 |
| `docs/AccessibilityCheck.kt`, `docs/PdfAccessibility.kt`, `docs/Contrast.kt` | Barrierefreiheit prüfen, PDF-Metadaten, Farbkontrast |
| `ui/SettingsOverview.kt`, `ui/PlatformUi.kt` | Gegliederte Einstellungsübersicht; Rechtsklick (Windows) bzw. langes Drücken (Android) |
| `data/ChatTree.kt` | Chats als Baum (Versionen beim Bearbeiten/Neu generieren) |
| `data/Library.kt`, `data/Stores.kt` | Ordner, Vorlagen (Bereiche, Platzhalter), Umgebungen, Logos und deren Speicherung |
| `data/TemplateLibrary.kt` | Eingebaute Vorlagen, Favoriten-Vorschläge, Teilen/Importieren |
| `ui/TemplateUi.kt`, `ui/TemplatesScreen.kt` | TU Vorlagen durchsuchen, ausfüllen (auch als Fragerunde), verwalten |
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
| `data/AppTools.kt`, `ui/ToolsSettings.kt`, `ui/NavRules.kt` | Einschaltbare Werkzeuge, Seitenleiste mit „Mehr“, Loras Werkzeug-Vorschlag |
| `agent/ChatModes.kt`, `ui/ModeUi.kt` | Modi (Literatursuche, Quellenmodus, Exzerpt, Planungsmodus, Sprachmodus) mit einheitlichem Rahmen |
| `agent/AskUserTool.kt`, `ui/QuestionCard.kt` | Rückfragen mit Antwortknöpfen (`ask_user`), TU Vorlagen als Fragerunde |
| `data/MemoryMeter.kt`, `ui/MemoryUsage.kt` | Arbeitsspeicher messen und anzeigen, Modelle im Leerlauf entladen |
| `data/AppMap.kt`, `agent/LoraAgent.kt`, `ui/LoraHelper.kt`, `ui/Spotlight.kt` | Lora als Navigatorin: Landkarte der App, lokale Suche, Agent-Schleife, Panel (Seitenleiste/Blatt) mit Ergebnis-Karten, Leuchtrahmen |
| `agent/ToolActions.kt`, `agent/OfficeActions.kt`, `agent/MoreActions.kt`, Windows `video/VideoLora.kt` | Lora handelt in Werkzeugen (AP-38): Register der Aktionen mit Vorschau und Rückgängig, Datenschutz, Protokoll; Dateien, PDF, Dokument, Tabelle, Folien, Video, Transkript/Handout/Nextcloud |
| `agent/LoraPrefs.kt`, `agent/LoraPlan.kt`, `ui/LoraCards.kt`, `ui/LoraTour.kt` | Vorlieben mit Rückfrage (AP-39), Abläufe mit Plan-Karte und Fortsetzen nach Neustart (AP-40), Touren mit Leuchtrahmen (AP-41), *Einstellungen › Lora › Gedächtnis* |
| `agent/LoraSense.kt`, `ui/LoraChat.kt`, `ui/LoraFlight.kt` | Was Lora auf dem Gerät erkennt (Vorschläge je Bereich, Fristen, Tabellen, DOIs, Ort beim Tippen, Schrittzeilen), Übergabe-Karte und nächste Schritte im Chat, Flug zum Ziel und Augen, die dem Zeiger folgen |
| `Brand.kt`, `data/ChatTitles.kt` | Name und Slogan der App; kurze Chat-Titel aus der ersten Nachricht |
| `agent/Evidence.kt`, `ui/EvidenceCard.kt` | Quellenmodus: Belege lesen, gegen die Quellen prüfen, Belegprotokoll |
| `research/Excerpts.kt`, `agent/ExcerptTools.kt` | Exzerpt-Agent: Schemata, Seitenprüfung, `save_excerpt` |
| `data/Vault.kt`, `data/DeviceKey.kt`, `ui/VaultScreen.kt` | Tresor: PIN, Wiederherstellungsschlüssel, Verschlüsselung (Keystore bzw. DPAPI) |
| `docs/PdfTools.kt`, `docs/PdfToolModel.kt`, `ui/PdfRender.kt`, `ui/PdfToolScreen.kt` | PDF-Werkzeugkasten: Seiten, Anmerkungen, Formularfelder (erkennen und setzen), Darstellung je Plattform |
| `data/Batches.kt`, `agent/BatchRunner.kt`, `agent/BatchTools.kt`, `ui/BatchScreen.kt` | Stapel & Massenkontrolle (AP-20): Aufträge und Raster, Warteschlange im Hintergrund mit Pause/Fortsetzen, Kalibrierung, Belegprüfung, Ausreißer, Entscheidungen mit Protokoll, Exporte |
| `data/Proposals.kt`, `agent/ProposalTools.kt`, `ui/ProposalScreen.kt` | Antrags-Werkstatt (AP-21): Förderer-Vorlagen, Checkliste, Fristen (Kalender), Gliederung mit Zeichen-/Seitenwächter, Gantt, Budget, Abgabe-Check, Probelauf |
| `net/WebDav.kt`, `data/Nextcloud.kt`, `agent/NextcloudTools.kt`, `ui/NextcloudUi.kt`, `files/FileText.kt` (beide) | Nextcloud (AP-22): WebDAV, Login Flow v2, Datei-Browser, Anhängen/Holen/Speichern, Agent-Werkzeuge |
| `net/Mcp.kt`, `net/McpOAuth.kt`, `net/McpStdio.kt` (beide), `data/McpServers.kt`, `agent/McpTools.kt`, `ui/ConnectionsSettings.kt` | MCP-Client (AP-23): Streamable HTTP, OAuth mit PKCE, lokale Server (Windows), Werkzeuge je Server mit Erlauben/Fragen/Sperren, *Einstellungen › Verbindungen* |
| `docs/PdfStage3.kt` (beide), `docs/PdfCompare.kt`, `ui/PdfStage3Ui.kt` | PDF Stufe 3 (AP-26): Schwärzen, Text/Bilder ändern, Verkleinern, PDF/A-2b, Passwort, Metadaten, PDF/UA-Prüfung, Vergleichen |
| `net/Imap.kt`, `data/MailServer.kt` | Mail über den Server (AP-27): IMAP-Client und ImapBridge mit der Schnittstelle des klassischen Outlook |
| `data/PhoneSync.kt` | Sync für Android (AP-28): Lora-Ordner des PCs über WebDAV spiegeln, Notiz, Konflikte, Schutz vor Massenlöschung |

### Windows-Version (`desktop/`)

`desktop/` ist ein eigener Gradle-Build mit Compose Desktop. Er kompiliert alle Dateien aus `app/src/main/java` mit,
außer denen, die unter `desktop/src/main/kotlin` im gleichen Pfad eine Windows-Fassung haben (z.B. `ui/ChatScreen.kt`,
`docs/HtmlPdf.kt`, `tasks/TaskScheduler.kt`). Änderungen an Logik, Werkzeugen und den meisten Bildschirmen gelten so
automatisch für beide Versionen. Kleine Stellvertreter für Android-Klassen (`android/content/Context.kt`, `Base64`, `Toast`,
`LocalContext`) sorgen dafür, dass gemeinsamer Code unverändert kompiliert.

| Pfad | Inhalt |
| --- | --- |
| `Main.kt` | Fenster, Symbol im Infobereich, Benachrichtigungen, Tastenkürzel, Drag & Drop |
| `desktop/ProcessMemory.kt` | Arbeitsspeicher des ganzen Prozesses (Working Set) für *Einstellungen › Arbeitsspeicher* |
| `SelfTest.kt` | Starttest für CI (`--selftest <Ordner>`): alle Bildschirme, Dokumente, Verschlüsselung |
| `desktop/Paths.kt`, `desktop/Secrets.kt`, `data/FilePrefs.kt`, `data/SecurePrefs.kt` | Datenordner, DPAPI-Verschlüsselung, Einstellungen (Schlüssel auf dem PC, Rest im Datenordner) |
| `desktop/SyncSession.kt`, `ui/SyncUi.kt` (gemeinsam: `data/SyncGuard.kt`) | Einfacher Sync (AP-12): Notiz je PC, Prüfung vor dem Laden, Konfliktkopien, *Einstellungen › Synchronisation*, Cloud-Ordner finden |
| `data/LearningGames.kt`, `agent/GameTools.kt`, `ui/GamesScreen.kt`, `ui/GamePlatform.kt` (beide) | Lernspiele (AP-17): Sammlung mit Versionen, Abschottung (CSP), Export als HTML/ZIP, Agent-Werkzeuge, Spielen |
| `agent/AiUseNote.kt`, `ui/AiUseNoteUi.kt` | KI-Nutzungsnachweis (AP-19): Erkennung „Text für eine Arbeit“, Nachweis in drei Formaten, Pop-up-Vorschlag |
| `desktop/WebEngine.kt`, `desktop/WebPage.kt`, `ui/BrowserPanel.kt` | Browser neben dem Chat (AP-14): Chromium über JCEF (einmalig eingerichtet), Tabs, Seitenaktionen, Meta-Tags → Literatur |
| `desktop/Com.kt`, `desktop/ComOutlook.kt`, `data/OutlookPlatform.kt` (gemeinsam: `data/Outlook.kt`, `data/MailBox.kt`, `agent/OutlookTools.kt`, `agent/MailAccess.kt`, `agent/LoraMail.kt`, `agent/MailSorter.kt`, `ui/MailScreen.kt`, `ui/MailView.kt`, `ui/MailSortPreview.kt`, `ui/MailLocalModel.kt`) | Mail & Kalender (AP-13, AP-68, AP-69): COM über JNA, Outlook ohne Löschen und Senden, nur lokales Modell (eine Sperre), Mailansicht, Lora sortiert, Agent-Werkzeuge, Regeln, Protokoll |
| `desktop/WindowsOcr.kt`, `desktop/OfficePdf.kt`, `docs/PdfPlatform.kt`, `docs/PdfExtras.kt` (beide; gemeinsam: `docs/PdfStage2.kt`, `docs/PdfConvert.kt`, `ui/PdfToolsMore.kt`) | PDF Stufe 2 (AP-15): Texterkennung von Windows, Office → PDF, Textebene, Unterschrift, PDF → Markdown/Word/Excel, Tabellen |
| `data/FileLibrary.kt`, `ui/FilesScreen.kt` | Dateien (AP-18): Katalog, Ordner, Schlagwörter, Volltext-Cache und Suche, Vergleich |
| `data/LoraDrive.kt`, `data/LoraRoot.kt` (beide), `desktop/Paths.kt`, `desktop/LoraLayout.kt`, `files/LoraMirror.kt` (beide), `ui/DriveScreen.kt`, `ui/LoraFolderUi.kt` (beide), `ui/DrivePlatform.kt` (beide) | Lora-Ordner und Tab *Dateien* (AP-16): Hochgeladen, Erstellt, Ordner, Papierkorb, Umzug der Daten, Kopie auf Android |
| `docs/DocFiles.kt`, `docs/DocxExtras.kt`, `docs/LatexOmml.kt`, `docs/SlideExtras.kt`, `docs/LocalSpelling.kt` (beide), `data/ChatFilter.kt` | Office-Ausbau (AP-16): echte Dateien und Schreiben ins Original, Überarbeitungen und Kommentare in Word, Formeln, freie Folien, Rechtschreibung, Chatfilter |
| `creative/DesignRender.kt`, `creative/DesignExport.kt`, `creative/DesignEditorState.kt` und `creative/CanvasOps.kt` (gemeinsam, AP-50), `ui/DesignEditor.kt`, `ui/CreateDesigns.kt`, `ui/DesignPlatform.kt` (beide), `data/DesignSupport.kt` (beide), `creative/TudAssets.kt`, `resources/fonts/`, `resources/tud/` (Logos, Icons; aus `tools/tud_cd_assets.py`) (gemeinsam: `data/Designs.kt`, `data/DesignTemplates.kt`, `agent/DesignTools.kt`, `ui/CreateUi.kt`) | Erstellen › Gestalten (AP-24): Renderer mit Noto Sans, Export PNG/PDF/PowerPoint, Editor, Vorlagen, Agent-Werkzeuge |
| `video/VideoProject.kt`, `video/Media.kt`, `video/VideoRender.kt`, `video/VideoTitles.kt`, `video/RoughCut.kt`, `video/VideoPlayer.kt`, `ui/VideoScreen.kt`, `ui/VideoLayout.kt` | Videoeditor (AP-25, Anordnung AP-70): Projekte, ffprobe/ffmpeg, Proxys, Vorschau, Titel im TUD-Design, Rohschnitt, Export |
| `video/VideoEdit.kt`, `video/AudioSync.kt`, `ui/VideoTimeline.kt`, `ui/VideoPanels.kt` | Videoeditor (AP-52, AP-53): Spuren, Bild-in-Bild, Schnitt (Klinge, Ripple, Roll, Slip, Einfügen/Überschreiben, Magnet), verknüpfter Ton, Synchronisieren über die Wellenform, Medienpool, Quellmonitor, Marker, Tastenkürzel |
| `video/TextCut.kt`, `ui/VideoTranscriptPanel.kt` | Textbasierter Schnitt (AP-55): Wörter auf der Zeitleiste, Löschen = Schneiden, Absätze verschieben, Füllwörter/Pausen, Lora-Vorschläge |
| `video/ScreenRecorder.kt`, `ui/ScreenRecordUi.kt`, `desktop/GlobalHotkeys.kt` | Bildschirmaufnahme (AP-56): ein ffmpeg-Prozess für Bildschirm, Webcam und Ton, Teile bei Pause, Klicks, Aufnahmeleiste, Tasten in ganz Windows |
| `video/Encoders.kt`, `video/ExportQueue.kt`, `ui/VideoExportUi.kt` | Export (AP-57): Hardware-Encoder mit Rückfall, Warteschlange im Hintergrund, mehrere Ziele, Kapitel, Nextcloud |
| `desktop/WindowsShell.kt`, `ui/ShellRequests.kt`, `ui/ExternalOffice.kt` (gemeinsam) | Windows-Integration (AP-59): Kontextmenü je Benutzer, Start mit Datei, Dateien nach außen ziehen/kopieren, Windows-Papierkorb, Office mit Rückweg |
| `docs/PdfSigning.kt` (beide), `docs/PdfAutoTag.kt`, `PdfSnap` in `docs/PdfToolModel.kt` | PDF-Reste (AP-60): digitale Signatur mit Prüfung (Bouncy Castle), Tags für PDF/UA, Markieren und Schwärzen am Text |
| `docs/Diagrams.kt`, `ui/DiagramView.kt`, `ui/DiagramEditor.kt`, `docs/DiagramRaster.kt`, `ui/WhiteboardScreen.kt` | Diagramme und Whiteboard (AP-61): Modell, Mermaid, Anordnung, Zeichnen (Compose, SVG, PNG), Editor, gespeicherte Tafeln |
| `docs/DocScanner.kt`, `ui/ScanScreen.kt` (gemeinsam), `docs/ScanImages.kt`, `ui/ScanPlatform.kt` (beide), `desktop/WiaScanner.kt` | Dokumenten-Scanner (AP-62): Kanten, Entzerren, Kontrast, PDF mit Textebene; Kamera/Fotos bzw. WIA/Bilder |
| `data/PhoneSync.kt`, `data/Vault.kt`, `tasks/BackgroundSync.kt` (beide), `research/WebMeta.kt`, `ui/PhoneBrowser.kt` (nur Android) | Sync- und Android-Reste (AP-63): Literatur, Wissen und Tresor im Handy-Sync, Hintergrund-Sync, PIN-Kiste je Gerät, Browser mit WebView |
| `ui/PresentScreens.kt` (beide), `ui/LoraFolderPicker.kt`, `docs/PdfStage3.kt` (Android: Verkleinern) | Android zieht nach (AP-64): zweiter Bildschirm über `Presentation`, Speichern in den Lora-Ordner |
| `audio/Dictation.kt`, `ui/DictationUi.kt` | Diktat (AP-65): Satzzeichen per Sprache, Einfügen am Cursor, Erkennung auf dem Gerät |
| `files/FileDetails.kt`, `ui/QuickLook.kt` | Schnellansicht und Details-Spalte in Dateien (AP-66) |
| `desktop/Extensions.kt`, `ui/ExtensionsUi.kt` (gemeinsam: `update/UpdateInfo.kt`, `update/Updates.kt`), `.github/scripts/extension-videoeditor.sh` | Nachladbare Erweiterungen (AP-25): Katalog in version.json, Einrichten, Aktualisieren, Entfernen; Bau des Videoeditor-Pakets |
| `desktop/Autostart.kt`, `desktop/SingleInstance.kt` | „Mit Windows starten“ (macOS: LaunchAgent, Linux: `~/.config/autostart`), nur eine laufende App |
| `desktop/Os.kt`, `update/AppLayout.kt`, `ui/Keys.kt` (gemeinsam) | macOS und Linux (AP-31): System, App-Ordner je System, tar.gz mit Rechten und Links, ⌘/Strg, Texte (⌘, Finder) |
| `desktop/Accessibility.kt`, `ui/Design.kt` (`a11yLabel`, `Segmented`, `GroupLabel`), `ui/Theme.kt` (Fokus) | Barrierefreiheit (AP-30): Java Access Bridge für NVDA/JAWS, Beschriftungen, Überschriften, sichtbarer Fokus |
| `ui/I18n.kt`, `ui/TextI18n.kt`, `resources/i18n/en.tsv` (gemeinsam) | Englisch (AP-29): `tr`, `trLater`, übersetzendes `Text`, englische Texte |
| `docs/HtmlPdf.kt` | HTML → PDF mit Edge/Chrome (headless), sonst openhtmltopdf |
| `launcher/Launcher.cs` | Ein-Datei-Starter: entpackt die App nach `%LOCALAPPDATA%` und startet sie |

