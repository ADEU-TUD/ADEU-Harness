# Design-Arbeitsplan Lora (APD)

Stand: 13.11.2026 (APD-01 *Umfassendes UI-Review* durchgeführt: Review mit Noten, Stilrichtung „Klar, ruhig – mit Lora als Farbe“ und Pakete APD-02 bis APD-14 unter *Danach*, wartet auf Freigabe; Anspruch des Hauptnutzers: super aussehen, gut bedienbar, rundes Gesamtprodukt mit einheitlichem Stil auf dem Niveau der neuen Lora, ohne wie Lora auszusehen; TU-Dresden-CD-Vorlagen unveränderlich) · Lebendes Dokument: Status hier pflegen,
erledigte Pakete abhaken und in der README beschreiben.

**Was hier steht**: alle Arbeitspakete zu **Aussehen, Bedienbarkeit und Nutzerfreundlichkeit** der App – Oberfläche,
Interaktion, Texte auf dem Bildschirm, Barrierefreiheit. Sie heißen **APD-xx** (*Arbeitspaket Design*). Neue Funktionen
bleiben im allgemeinen Arbeitsplan [`ARBEITSPLAN.md`](ARBEITSPLAN.md) (AP-xx).

**Wenn der Hauptnutzer vom „Design-Arbeitsplan“ spricht** („arbeite Pakete aus dem Design-Arbeitsplan ab“, „APD-…“),
ist **diese Datei** gemeint. Als Nächstes kommt das erste offene Paket unter *Jetzt*. Neue Design-Pakete bekommen die
nächste freie APD-Nummer und werden nur hier eingetragen.

**So ist der Plan aufgebaut** (wie der allgemeine Arbeitsplan)

- **Jetzt** – in fester Reihenfolge; ist ein Paket fertig, rückt das nächste nach.
- **Danach** – rückt nach „Jetzt“ auf, sobald dort Platz ist.
- **Später** – gewollt, aber erst nach „Jetzt“ und „Danach“.
- **Ideen** – ohne Auftrag, je eine Zeile.
- **Erledigt** – Kurzfassung mit Datum und Version.

**Legende**: Größe S (bis 1 Tag) · M (einige Tage) · L (1–2 Wochen) · XL (mehrere Wochen, in Stufen teilen) ·
Status ☐ offen · ◐ in Arbeit · ☑ erledigt · Plattform A = Android, W = Windows, D = nur Desktop, beide wenn nicht
angegeben · Schweregrad eines Befunds: **hoch** (stört oder verwirrt im Alltag, Fehler, unlesbar), **mittel** (umständlich,
uneinheitlich, wirkt unfertig), **gering** (Feinschliff).

---

## Grundsätze (Design)

| Thema | Entscheidung |
| --- | --- |
| Anspruch | **Es muss super aussehen und gut bedienbar sein** (Hauptnutzer, 13.11.2026). Die App sieht **seriös und modern** aus – klar, aufgeräumt, hochwertig, passend für Forschung, Lehre und Verwaltung an der TU Dresden. „Geht so“ reicht nicht: Viele Bereiche wirken noch **unübersichtlich oder langweilig** – genau das wird gesucht und behoben. Kein Effekt um seiner selbst willen. |
| Ein Gesamtprodukt | Ein **rundes Gesamtprodukt mit einheitlichem Stil**: dieselbe Formensprache (Ecken, Karten, Schatten, Abstände), dieselbe Bildsprache (Symbole, Illustrationen), dieselben Muster für Seitenköpfe, Listen, Karten, leere Zustände und Hinweise – in jedem Bereich, vom Chat bis zum Videoeditor. Wer einen Bereich kennt, kennt alle. |
| Lora als Maßstab | Lora wurde mit AP-122 neu gezeichnet (Volumen, weiches Licht, lebendige Bewegung). **Die Figur selbst ist bewusst poppig und soll es bleiben.** Die Oberfläche wird auf **dasselbe Qualitätsniveau** gehoben, soll aber **nicht so aussehen wie Lora**: kein Comic- oder Pop-Stil für die App, sondern eine ruhige, seriöse, moderne Oberfläche, vor der Lora als farbige Figur zur Geltung kommt. Lora erscheint dort, wo sie hilft (Einrichtung, leere Zustände, Erfolg, Hilfe), und nicht überall. |
| **TU Dresden: Corporate Design unveränderlich** | Die **Vorlagen im Corporate Design der TU Dresden sind zu jeder Zeit unveränderlich** und gehören nicht zum Design-Arbeitsplan: was die App im TUD-Stil **erzeugt** – TU Vorlagen, Briefe mit Anschriftfeld, Dokumente, Folien, Poster und Designs im Stil „TU Dresden“ –, mit Farben, Hausschrift Noto Sans, Logo, Satzspiegel und Aufbau aus dem offiziellen CD-Paket. Im Code: `docs/TudBrand.kt`, `DocStyle.TUD` in `docs/DocTheme.kt`, `creative/TudAssets.kt`, die eingebauten Vorlagen (`data/BuiltInTemplates.kt`), Logos und Schrift in `resources/tud/`. **Veränderbar** ist nur die App drumherum: die Oberfläche, mit der man Vorlagen auswählt, bearbeitet und erstellt (Listen, Editoren, Leisten, Dialoge, Vorschauen). Der App-Stil „TU Dresden“ (`AppStyle.TUD`) darf als Oberfläche verbessert werden, nimmt die CD-Farben aber unverändert. Ein Design-Paket, das eine dieser Vorlagen oder ihre Ausgabe ändern würde, ist falsch geschnitten. |
| Priorität | **Nutzerfreundlichkeit vor Schmuck.** Eine Änderung zählt, wenn etwas schneller gefunden, klarer verstanden oder mit weniger Schritten erledigt wird; Verschönerung folgt daraus. |
| Ein Baukasten | Bildschirme bauen aus gemeinsamen Bausteinen: `ui/Controls.kt` (Knöpfe, Symbol-Knöpfe mit Tooltip, Felder, Schalter, Menüs), `ui/Dialogs.kt` (Dialoge mit Hauptknopf, Enter/Esc), `ui/NoticeBar.kt` (Hinweise), `ui/Scrollbars.kt` (Bildlaufleisten), `ui/Design.kt` (Karten, Gruppenüberschriften, Marken, Segmente), `ui/Theme.kt` (`ChatLook` je Stil), `ui/Chips.kt`, `ui/TextI18n.kt`. Fehlt ein Baustein, kommt er dorthin – keine Einzellösung in einem Bildschirm. |
| Alle Stile | Jede Änderung gilt für alle 8 Stile (ADEU, TU Dresden, Office, Hoher Kontrast, Claude, Material You, ChatGPT, Gemini), hell und dunkel; Hoher Kontrast bleibt WCAG AAA, sonst mindestens AA (Text 4,5 : 1, Bedienelemente 3 : 1). |
| Plattformen | Windows-PC ist die Hauptplattform (1366 × 768 bei 150 % muss gehen); Android zieht mit (schmale Bildschirme, große Schrift, Touch). |
| Wege bleiben | Keine Einfach-/Expertenansicht (Grundsatz *Übersichtlichkeit* im allgemeinen Arbeitsplan). Strg+K, Loras Orte (Spots) und Leuchtrahmen (AP-41) finden alles weiter; Tests dafür bleiben grün. |
| Lora | Animationen respektieren *Ruhigere Bewegungen*; Lora bleibt seriös (Grundsatz *Lora*). |
| Sprache | Klartext aus Sicht der Nutzer:innen, einheitliche Begriffe; jede neue Beschriftung auch in `i18n/en.tsv`. |
| Freigabe | Jede Stufe zeigt **Vorher/Nachher-Bilder** aus Tests (Muster: `ui/ControlsLookTest`, `ui/BedienungReviewTest`; „vorher“ auf dem Stand von `main` in einem eigenen Arbeitsverzeichnis rendern) auf einer Vergleichsseite (Artefakt). Erst nach der Freigabe des Hauptnutzers PR und Merge. |

## Prüfmaßstab

Gilt für das Review (APD-01) und als Abnahme jedes Pakets. Je Bildschirm:

1. **Orientierung** – Wo bin ich, was ist das Wichtigste hier, wie komme ich zurück? Seitenkopf, Titel, Hauptaktion.
2. **Hierarchie** – Eine klare Hauptaktion; Nebenaktionen zurückhaltend; Wichtiges oben; nichts Gleichrangiges, was
   nicht gleich wichtig ist.
3. **Konsistenz** – Gleiche Dinge sehen gleich aus und heißen gleich (Karten, Listenzeilen, Kopfzeilen, Abstände,
   Symbole, Begriffe) – über alle Bereiche.
4. **Typografie und Raum** – Feste Schriftgrößen-Skala, lesbare Zeilenlänge, Abstände aus einer Skala, keine
   gequetschten oder verlorenen Bereiche.
5. **Farbe und Kontrast** – Akzent nur für Handlung und Auswahl, Zustandsfarben (Fehler, Warnung, Erfolg) einheitlich,
   Kontrast nach WCAG, dunkles Design gleichwertig.
6. **Zustände** – Leer, Laden, Fehler, Erfolg, nichts gefunden, offline: jeweils ein Satz, was los ist, und der nächste
   Schritt als Knopf.
7. **Rückmeldung und Interaktion** – Hover, Fokus, Drücken sichtbar; Tooltips; Rückgängig statt Nachfrage, wo möglich;
   Tastatur (Tab-Reihenfolge, Kürzel sichtbar, Enter/Esc); Rechtsklick; Ziehen und Ablegen.
8. **Formulare und Eingaben** – Beschriftung, Vorbelegung, Fehler direkt am Feld, Pflichtfelder erkennbar, Einheiten,
   Auswahl statt Freitext, wo sinnvoll.
9. **Texte** – Klartext statt Technik, kurz, aktiv, einheitliche Begriffe, keine doppelten oder mehrdeutigen
   Beschriftungen.
10. **Platz und Größen** – 1366 × 768 bei 150 %, breite Fenster (Lesebreite), Handy, große Schrift (bis 200 %): kein
    Abschneiden, kein ungewollter Umbruch, nichts außerhalb des Bildschirms.
11. **Barrierefreiheit** – Screenreader-Beschriftungen, Überschriften, Fokus sichtbar, nicht nur Farbe als Information,
    Touch-Ziele mindestens 48 dp (Android).
12. **Eindruck** – Wirkt der Bildschirm seriös, modern, hochwertig und fertig? Ist er **übersichtlich** (Gruppierung,
    wenig gleichzeitig, klarer Einstieg) oder überladen? Ist er **ansprechend** oder **langweilig** (graue Listen,
    Textwüsten, keine visuellen Anker, alles gleich laut)? Passt er zum Rest der App, und steht Lora davor gut da (ohne dass die App selbst poppig wird)? Was würde eine
    Designerin als Erstes ändern?

---

## Übersicht

| Abschnitt | Pakete |
| --- | --- |
| **Jetzt** | **APD-01 Umfassendes UI-Review der ganzen App ◐** (fertig bis auf die Freigabe des Hauptnutzers) |
| **Danach** | APD-02 Sofort-Korrekturen ☐ · APD-03 Stil-Grundlagen ☐ · APD-04 Baukasten für Seiten ☐ · APD-05 Chat-Startseite und Eingabefeld ☐ · APD-06 Seitenleiste, Rahmen und Lora im Kopf ☐ · APD-07 Dateien ☐ · APD-08 Einstellungen aus einem Guss ☐ · APD-09 Werkzeug-Startseiten und leere Zustände ☐ · APD-10 Erstellen und Editoren ☐ · APD-11 Literatur und Modelle ☐ · APD-12 Chat-Antworten und Nebenansichten ☐ · APD-13 Handy, kleine Fenster, große Schrift ☐ · APD-14 Kontrast, dunkles Design und Tastatur ☐ |
| **Später** | – |
| **Erledigt** | Grundlage: AP-123 *Bedienelemente aus einem Guss* (allgemeiner Arbeitsplan, 1.0.71) |

---

## Jetzt

### APD-01 ◐ Umfassendes UI-Review der ganzen App · L (Wunsch Hauptnutzer 13.11.2026)
**Ziel**: Die ganze Oberfläche einmal vollständig und ehrlich prüfen – mit dem Blick einer erfahrenen UI/UX-Designerin –
und daraus einen geordneten Plan machen, mit dem wir das Design Stück für Stück verbessern, bis die App ein **rundes
Gesamtprodukt mit einheitlichem Stil** ist, das **super aussieht und sich leicht bedienen lässt** (Grundsätze
*Anspruch*, *Ein Gesamtprodukt*, *Lora als Maßstab*). Ausdrücklich gesucht: was **unübersichtlich** und was
**langweilig** wirkt. Die Vorlagen im Corporate Design der
TU Dresden und alles, was die App im TUD-Stil erzeugt, sind **nicht Gegenstand** des Reviews (Grundsatz *TU Dresden*);
geprüft wird die Oberfläche, mit der man sie auswählt, bearbeitet und erstellt. **In diesem Paket wird nichts
an der Oberfläche umgebaut** (höchstens Werkzeuge für Bilder und Tests); offensichtliche Fehler werden als Befund mit
Schweregrad *hoch* erfasst.

**Vorgehen**
1. **Bilder von allem**: ein Test (z. B. `ui/UiReviewTest`, Bilder nach `build/review/`), der jeden Bereich unten
   rendert – PC-Fenster 1366 × 768 (bei 150 % also rund 910 × 512 dp) und ein breites Fenster, Handy-Breite (rund
   400 dp) für die Bereiche, die es auf Android gibt; ADEU hell und dunkel, dazu TU Dresden und Hoher Kontrast
   stichprobenartig; leere und gefüllte Zustände mit Beispieldaten; wichtige Dialoge, Menüs und Hinweise. Was sich im
   Test nicht rendern lässt (z. B. Browser, natives Fenster), im Bericht nennen.
2. **Prüfen** nach dem *Prüfmaßstab* oben (12 Punkte), Bereich für Bereich – dazu je Bereich eine Note von 1 bis 5 für
   **Übersicht**, **Bedienung** und **Ausstrahlung** (sieht es gut aus?), damit die schwächsten Bereiche sichtbar werden.
3. **Befunde erfassen** in `docs/design/review-2026-11.md` (neu), je Befund: Bereich, Ort im Code (Datei/Composable),
   was ist, warum es stört (Prüfpunkt), Schweregrad, **Vorschlag** (konkret, mit Skizze in Worten oder Bild), Aufwand
   (S/M/L), betroffene Plattformen und Stile. Bereichsübergreifende Muster (z. B. „Seitenköpfe uneinheitlich“) als
   eigener Befund, nicht zwanzigmal einzeln.
4. **Pakete bilden**: Befunde zu Paketen APD-02 ff. bündeln (eher nach Thema oder Bildschirm als nach Einzelbefund),
   unter *Danach* eintragen – je Paket Befund, Ziel, Vorschläge, *Fertig, wenn*, Größe – und nach Nutzen ordnen:
   zuerst, was im Alltag am meisten stört oder am häufigsten gesehen wird (Chat, Seitenleiste, Dateien, Einstellungen).
5. **Stilrichtung für das Gesamtprodukt**: aus dem Review eine einheitliche Gestaltung ableiten, die das Niveau der
   neuen Lora erreicht, ohne wie sie auszusehen (seriös und modern, Lora als farbige Figur davor) – Formensprache, Farbeinsatz, Schrift und Abstände, Symbole und Illustrationen, Muster für Seitenköpfe, Listen,
   Karten, leere Zustände – und für **2–3 Schlüsselbildschirme** (z. B. Chat-Startseite, Seitenleiste, Dateien) zeigen,
   wie sie damit aussehen würden (Entwurf als Bild oder HTML, noch nicht in der App). Die Richtung wird die Grundlage der
   Pakete APD-02 ff. (Baukasten zuerst, dann die Bildschirme).
6. **Übersichtsseite** (Artefakt) für den Hauptnutzer: Noten je Bereich, die wichtigsten Befunde mit Bild und Vorschlag,
   die Stilrichtung mit den Entwürfen, die Paketliste mit Reihenfolge – damit er Richtung und Prioritäten freigeben und
   streichen kann. Erst danach gilt APD-01 als fertig.

**Bereiche** (alle prüfen; Routen aus `ui/AppRoot.kt`, Einstellungsseiten aus `ui/SettingsScreen.kt`)
- *Rahmen*: Startanimation, Einrichtung beim ersten Start (`SetupWizard`), Seitenleiste mit „Mehr“, Projekten und Chats
  (`AppDrawer`), Strg+K (`CommandPalette`), Tastenkürzel-Übersicht, Hinweis-Banner, Fehler- und Update-Dialoge,
  Lora-Panel und schlafende Lora (`LoraHelper`, `LoraUi`), Touren (`LoraTour`).
- *Chat*: Startseite, Eingabefeld mit Anhängen und Modi (`ChatComposer`), Antworten (`ChatMessages`, `Markdown`),
  Übergabe-Karte, Arbeitskarte, Modellwahl, Persona/System-Prompt, Chat-Menüs, Browser neben dem Chat, Modellvergleich
  (`compare`).
- *Inhalte*: Dateien (`DriveScreen`, Schnellansicht, Lora-Dateidialog), Erstellen und TU Vorlagen (`TemplatesScreen`,
  `CreateUi`), Gestalten (`DesignEditor`, PC), Dokument-Editor (`DocScreen`, `FlowEditor`), Tabellen (`SheetEditor`),
  Folien (`SlidesEditor`), Whiteboard und Diagramme, Scanner, Bilder erzeugen.
- *Werkzeuge*: Literatur, Transkription (Liste und Transkript), PDF-Werkzeugkasten, Tresor, Lernspiele, Stapel &
  Massenkontrolle, Antrags-Werkstatt, Mail & Kalender (PC), Videoeditor (PC), Server-Terminal (PC), Geplante Aufgaben,
  Umgebungen.
- *Einstellungen*: Übersicht und alle Seiten (API-Zugang, Lora, Orte, GitHub, Chat & Werkzeuge, Werkzeuge,
  Arbeitsspeicher, Datenschutz, Darstellung, Transkription, Stimme, Dokumente und Hausstil, Gedächtnis, Wissen,
  Literatur, Sicherung, Papierkorb, Ordner, Speicher, Verbindungen, Updates, Problem melden, Inhalte zum Nachladen,
  Windows), Modelle, lokale Modelle und Einrichtung, Verbrauch.

**Schon vorgemerkt** (aus AP-123/AP-124 – im Review bestätigen, ergänzen oder verwerfen): Hauptbildschirme einzeln
überarbeiten (Chat, Seitenleiste, Dateien, Erstellen, Einstellungen, Literatur, Transkription, PDF, Lora-Panel) ·
einheitliche Seitenköpfe und eine feste Abstands- und Schriftgrößen-Skala · leere Zustände und Ladeanzeigen mit Satz und
nächstem Schritt · Fehler direkt am Eingabefeld · Tastenkürzel in Tooltips und Menüs · Bildlaufleisten auch waagerecht
und in Rastern (rund 65 Stellen mit `LazyRow`, `LazyVerticalGrid`, `horizontalScroll`) · dezente Übergänge · Android mit
schmalen Bildschirmen und großer Schrift · am Gerät offen aus AP-123: Tooltips, Enter/Esc in echten Dialogen,
Bildlaufleisten, Hoher Kontrast mit Screenreader · **einheitlicher Stil über alle Bereiche** (Wunsch Hauptnutzer) ·
**Lora (AP-122) als Figur vor einer ruhigen Oberfläche** – passende leere Zustände, ohne die App poppig zu machen · **visuelle Anker** statt
grauer Listen und Textwüsten (Symbole, Farbe, Bilder, Vorschauen).

**Fertig, wenn**: Bilder aller Bereiche vorliegen, `docs/design/review-2026-11.md` jeden Bereich mit Befunden und
Vorschlägen und Noten abdeckt, die Stilrichtung mit Entwürfen vorliegt, die Pakete APD-02 ff. hier unter *Danach*
stehen und der Hauptnutzer Richtung und Reihenfolge auf der Übersichtsseite freigegeben hat.

**Stand (13.11.2026)** – erledigt bis auf die Freigabe:
- ☑ **Bilder**: `ui/DesignReviewTest` (desktop) (nur mit `ADEU_DESIGN_REVIEW=1`, ca. 6 Minuten) rendert die echte App (`AppRoot`) durch alle Routen – PC 1366 × 768 @ 150 %
  (alle 54 Routen, ADEU hell und dunkel), breit 1600 × 900 dp, Handy-Breite 393 dp, TU Dresden und Hoher Kontrast
  (hell/dunkel), leer, erster Start, mit Beispieldaten und die Editoren (Dokument, Tabelle, Folien, Gestalten,
  Whiteboard): rund 300 Bilder in `desktop/build/design-review/`, Auswahl in `docs/design/bilder/`. Nicht gerendert
  (im Review genannt): Arbeits- und Übergabekarte, Browser neben dem Chat, Transkript-Ansicht, Android-eigene Teile.
- ☑ **Review**: [`docs/design/review-2026-11.md`](design/review-2026-11.md) – 89 Befunde (23 hoch, 40 mittel, 21 gering; 19 querschnittlich; je Bereich
  mit Ort im Code, Schweregrad, Vorschlag, Aufwand) und Noten für Übersicht, Bedienung, Ausstrahlung je Bereich
  (gesamt 3,1 · 2,8 · 3,2). Schwächster Fall ist die Hauptplattform 1366 × 768 @ 150 %.
- ☑ **Stilrichtung** „Klar, ruhig – mit Lora als Farbe“ mit Entwürfen für Chat-Startseite (hell/dunkel), Seitenleiste
  mit Chat, Dateien und einer Baukasten-Tafel: [`docs/design/entwuerfe/`](design/entwuerfe/).
- ☑ **Pakete** APD-02 bis APD-14 unter *Danach*, nach Nutzen geordnet.
- ◐ **Übersichtsseite** (Artefakt) liegt dem Hauptnutzer zur Freigabe von Richtung und Reihenfolge vor.
- ☑ **Nachtrag Fenstergrößen** (Rückfrage Hauptnutzer): Viele „abgeschnitten“-Befunde gibt es nur im Review-Fenster
  911 × 512 dp oder in Handy-Breite; US-Datum und Rohfehler waren zum Teil Testartefakte. Bestätigt bei üblichen
  Größen: Gestalten-Editor und die unten unsichtbar abgeschnittene Willkommenskarte. Abgleich in fünf Fenstergrößen
  (`DesignReviewTest.sizes`) im Review unter *Nachtrag*. **Offen**: welche Bildschirme und Skalierungen der Maßstab
  sind (Grundsatz *Plattformen*) – danach werden C-01, R-01, R-09 und I-11 eingestuft.

---

## Danach

Aus APD-01 (Befund-Nummern Q-, R-, C-, I-, W-, E-, D- siehe [`design/review-2026-11.md`](design/review-2026-11.md)).
Reihenfolge nach Nutzen: zuerst echte Fehler, die im Alltag stören (klein und ohne Umbau), dann **der Baukasten** (Tokens
und Bausteine, auf denen alles Weitere aufbaut), dann die Bildschirme in der Reihenfolge, in der man sie am häufigsten
sieht. Jede Stufe zeigt Vorher/Nachher-Bilder aus `DesignReviewTest` (gleiche Konfigurationen) auf einer Vergleichsseite.
Grundlage aller Pakete ist die **Stilrichtung „Klar, ruhig – mit Lora als Farbe“** (Review, Abschnitt *Stilrichtung*,
Entwürfe in `design/entwuerfe/`). TU-Dresden-Vorlagen und ihre Ausgabe bleiben in allen Paketen unberührt.

### APD-02 ☐ Sofort-Korrekturen · S–M
**Befund**: Fehler, die heute im Alltag stören und keinen Baukasten brauchen: Gestalten-Editor bricht bei 1366 × 768
(„Exportieren“ senkrecht, I-10), Erstellen am Handy zerbrochen (I-07), Wörter brechen mitten im Wort (Q-17),
Willkommenskarte ohne Schlüssel unter ≈ 950 dp Höhe unten unsichtbar abgeschnitten (C-02), rohe Fehlermeldung mit
Token-Hash bei ungültigem Schlüssel (Q-08), Datum und Uhrzeit nach Systemsprache statt App-Sprache (Q-09), „nicht verbunden“ in Mail als roter Absatz und Hinweis mit rotem Fehler-Symbol in
Aufgaben (Q-07, W-05).
**Ziel**: Nichts mehr, was kaputt oder erschreckend aussieht – auf der Hauptplattform und am Handy.
**Vorschläge**: Kopfleiste des Design-Editors umbrechen/Symbol-Knopf unter 1000 dp; `FlowRow`/Spalte unter 600 dp in
Erstellen; Mindestbreiten für Namen; Willkommenskarte scrollbar und kompakt; `ErrorHelp` in Modelle (Details
zugeklappt); `formatDate/formatTime` mit Locale der App-Sprache in `ui/I18n.kt`, relativ („heute, 18:02“); Rot nur für
Fehler.
**Fertig, wenn**: `DesignReviewTest` zeigt in allen Konfigurationen keinen senkrechten oder mitten im Wort gebrochenen
Text, keine Hex-Hashes und keine US-Daten (ein Test prüft die Bilder bzw. die Semantik auf diese Muster); Vorher/Nachher
freigegeben.

### APD-03 ☐ Stil-Grundlagen (Tokens) · M
**Befund**: 18 Eckenradien, 15 Innenabstände, 28 feste Schriftgrößen, 467 feste Farben; Seitenleiste und Inhalt ohne
Zonen; Dialoge getönt; alles gleich laut; Lora ohne Regel (Q-05, Q-06, Q-11, Q-15, Q-16, Q-19).
**Ziel**: Eine gemeinsame Grundlage in `ui/Theme.kt`, die alle 8 Stile hell und dunkel bedient.
**Vorschläge**: `Space` (4/8/12/16/24/32/48), `Radius` S/M/L je Stil, Typo-Rollen (Begrüßung 32, Seitentitel 24,
Abschnitt 20, Kartentitel 16, Text 14, Hinweis 12, Abschnittslabel 11), semantische Farben in `ChatLook` (Zustände,
**Bereichsfarben** als gedämpfte Kacheln, Dateitypen, Ordner, Stern); Zonen (Seitenleiste eine Stufe dunkler, Inhalt
als Blatt); Dialoge auf `look.card`; Lora-Größen 32/96/160 dp und die Regel, wo sie erscheint. TUD-Stil nimmt die
CD-Farben unverändert. Ein Test meldet neue feste Farben, Radien und Schriftgrößen in Bildschirmen (Ausnahmen: Theme,
Lora, Vorlagen-Vorschauen, Editor-Inhalte); ein Kontrast-Test misst Text gegen Grund in allen Stilen (AA, Kontrast AAA).
**Fertig, wenn**: Tokens und Tests stehen, die Baukasten-Tafel (`design/entwuerfe/baukasten.html`) ist als Compose-Bild
aus einem Test nachgebaut, hell/dunkel und alle Stile grün.

### APD-04 ☐ Baukasten für Seiten · L (Stufen: a Bausteine + Look-Test, b Umstellung aller Bildschirme)
**Befund**: Kein gemeinsamer Seitenkopf (26 eigene `TopAppBar`, Hauptaktion an vier Orten), drei Muster für leere
Zustände, Textwüsten, Lesebreite uneinheitlich, Zustandsfarben falsch, Einzelwahl in fünf Formen, mehrere Hauptknöpfe,
Filter laufen ab, Laden ohne Satz (Q-01, Q-02, Q-03, Q-04, Q-07, Q-12, Q-13, Q-14, Q-18).
**Ziel**: Wer einen Bildschirm kennt, kennt alle.
**Vorschläge**: In `ui/Page.kt` bzw. `ui/Design.kt`: `PageScaffold(title, subtitle, primary, actions, width =
LESEN/LISTE/VOLL)` mit **einem** gefüllten Hauptknopf rechts im Kopf (Handy: erweiterter FAB) und „Frag Lora“ im Kopf;
`EmptyState(pose, title, text, primary, secondary)` mit Lora; `StatusBanner(INFO/WARNUNG/FEHLER/ERFOLG, text, action)`;
`LoadingState`; `ListRow` (Kachel, Titel, Meta, Aktionen bei Hover, Rechtsklick); `SettingRow`; `RadioCards`,
`ModelField`; Regeln für Einzelwahl und Knopf-Hierarchie; Erklärtexte als Untertitel + zuklappbare „So funktioniert
es“-Karte. Ein Test (wie `ControlsBehaviourTest`) prüft, dass Bildschirme keine eigene `TopAppBar` und keinen eigenen
leeren Zustand mehr bauen.
**Fertig, wenn**: Stufe a: Bausteine mit Look-Test (alle Stile, hell/dunkel, Handy) freigegeben; Stufe b: alle
Bildschirme umgestellt, Vorher/Nachher aller Routen freigegeben.

### APD-05 ☐ Chat-Startseite und Eingabefeld · M
**Befund**: PC-Startseite fast leer (Schwellen 380/420/600 dp), Modellzeile doppelt, graue Kontext-Chips, mächtiges
Eingabefeld mit schwarzem Knopf, Modellwahl gekürzt, Chat-Titel gekürzt, unverständliches Auge-Symbol (C-01, C-03 bis
C-06, C-09, R-10).
**Ziel**: Die meistgesehene Seite ist einladend und klar – Lora begrüßt, man sieht sofort, was geht und wo man war.
**Vorschläge**: Wie Entwurf `design/entwuerfe/chat-start.html`: Lora 96 dp neben Gruß und einem Satz, Eingabefeld in
der Mitte, Startaktionen als eine Reihe mit Bereichsfarben, „Weiter, wo du warst“ als drei Karten; kompaktes statt
ausgeblendetes Layout bei wenig Höhe; Status-Symbole (Schild, Gedächtnis) in der Eingabeleiste; Akzent für Senden;
ein Mikrofon mit Menü; Titel linksbündig; temporärer Chat ins Menü.
**Fertig, wenn**: Bei 1366 × 768 @ 150 % sind Lora, Aktionen und „Weiter“ sichtbar; Vorher/Nachher in allen Stilen,
hell/dunkel und am Handy freigegeben.

### APD-06 ☐ Seitenleiste, Rahmen und Lora im Kopf · M
**Befund**: Seitenleiste auf der Hauptplattform nur als Overlay (Schwelle 1000 dp), hohe Zeilen, „Neuer Chat“ kein
Knopf, Projektnamen gekürzt, uneinheitliche Abschnittstitel, Strg+K und Kürzel-Übersicht roh, Lora-Panel als Blatt über
dem Inhalt, schwebende Lora verdeckt Inhalte, keine Zonen (R-01 bis R-09, R-11, Q-10, Q-15).
**Ziel**: Navigation ist auf jedem PC-Fenster da, kompakt und klar; Lora steht im Kopf, nicht über dem Inhalt.
**Vorschläge**: Wie Entwurf `design/entwuerfe/seitenleiste.html`: Seitenleiste ab 840 dp dauerhaft (260 dp), darunter
Symbol-Leiste; Zeilen 32–40 dp mit Bereichskacheln; „Neuer Chat“ als Knopf mit Strg+N; Projekte mit Farbe und Zähler;
`GroupLabel`; Strg+K mit Symbolen und Kürzeln auf weißer Fläche; Kürzel-Übersicht in Gruppen; Lora-Panel ab 840 dp als
Seitenpanel; „Frag Lora“ im Kopf aller Seiten statt schlafender Lora unten rechts; Einrichtung auf weißer Fläche mit
Haken an erledigten Schritten.
**Fertig, wenn**: Wege bleiben (Strg+K, Spots, Leuchtrahmen – Tests grün); Vorher/Nachher bei 911, 1366 und 1600 dp
Breite und am Handy freigegeben.

### APD-07 ☐ Dateien · M
**Befund**: Graue Liste ohne Anker, feste Farben (FAB und Ordner im Dunkeln unsichtbar), roher Pfad, kein leerer
Zustand, Ziehgriff zufällig, „Erstellt“ doppelt, Handy mit zweizeiliger Suche und abgeschnittenen Filtern (I-01 bis
I-06).
**Ziel**: Dateien finden und erkennen auf einen Blick – wie in einem guten Dateimanager, ruhig und modern.
**Vorschläge**: Wie Entwurf `design/entwuerfe/dateien.html`: Seitenkopf mit Suche und Hauptknopf „Neu“, Ort statt
Pfad, Abschnitte als Reiter, „Zuletzt“ als Karten mit Vorschau, Liste mit Typ-Kacheln und Spalten (Name, Geändert,
Größe), Aktionen bei Hover, `EmptyState` mit Ablagefläche.
**Fertig, wenn**: Vorher/Nachher (leer und mit Daten; PC, breit, Handy; ADEU hell/dunkel, Kontrast) freigegeben;
Schnellansicht, Ziehen und Ablegen und Details-Spalte funktionieren wie vorher (`Round2EndLookTest`).

### APD-08 ☐ Einstellungen aus einem Guss · M
**Befund**: Drei Aufbau-Muster, viel Fließtext, Werkzeuge ohne Symbole, leerer Papierkorb ohne Zustand, rohes
Fehlerprotokoll, Mischung aus Links und Knöpfen, dichter Datenschutz, Ergebnisse nicht am Feld (E-01 bis E-11, Q-03).
**Ziel**: Jede Einstellungsseite gleich gebaut: Abschnitt, Karte, Zeilen mit Titel, einem Satz und Steuerelement.
**Vorschläge**: `GroupLabel` + Karte + `SettingRow` überall; Erklärungen in Untertitel und Hilfe; Werkzeuge als Kacheln
mit den Symbolen der Seitenleiste; Fehler und Testergebnisse unter dem Feld; Texte kürzen (+ `i18n/en.tsv`).
**Fertig, wenn**: Alle 24 Einstellungsseiten im Vorher/Nachher freigegeben; Suche in den Einstellungen und Leuchtrahmen
finden alles wie vorher (`PrivacyPageTest` u. a. grün).

### APD-09 ☐ Werkzeug-Startseiten und leere Zustände · M–L
**Befund**: PDF-Werkzeugkasten als Absatz statt Werkzeug-Übersicht, Mail „nicht verbunden“ ohne Knopf, Videoeditor mit
vier Mustern, Server-Terminal mit Hauptknopf unten, Aufgaben und Umgebungen mit Erklärtext und nacktem Leer-Text,
Verbrauch mit viermal „0“, Tresor mit Textpunkten, Kopfaktion doppelt in Lernspiele/Stapel/Anträge (W-01 bis W-10,
W-12, I-15).
**Ziel**: Jedes Werkzeug zeigt beim ersten Öffnen, was es kann, und den ersten Schritt als Knopf – mit Lora im leeren
Zustand.
**Vorschläge**: PDF: Raster der Werkzeuge + Ablagefläche; Mail: `EmptyState` mit „Outlook nutzen“ / „Mailserver
eintragen“; Videoeditor: zwei große Start-Kacheln + `StatusBanner` für das Paket; Aufgaben: Zeitplan als Satz, nächster
Lauf relativ; Umgebungen, Verbrauch, Papierkorb: `EmptyState`; Lernspiele/Stapel/Anträge: Kopfaktion erst bei Inhalten.
**Fertig, wenn**: Alle Werkzeuge leer und mit Daten im Vorher/Nachher freigegeben.

### APD-10 ☐ Erstellen und Editoren · L
**Befund**: Erstellen überladen (Prompt, Galerie, Diagramme, TU-Vorlagen-Suche, Filter, Liste; zwei Text-Links je
Karte), unklare Kopf-Symbole, Dokument-Editor mit drei Leisten und Lineal (wenig Schreibfläche bei 1366 × 768),
Tabelle im Dokument als Einzelkästen, Whiteboard mit zwei gemischten Chip-Reihen (I-08, I-09, I-11 bis I-14).
**Ziel**: Erstellen ist eine aufgeräumte Galerie; die Editoren haben eine gemeinsame, kompakte Leisten-Logik wie
Tabellen- und Folien-Editor.
**Vorschläge**: Erstellen mit Abschnitts-Reitern (Gestalten · Diagramme · TU Vorlagen · Eigene), Karten mit einer
Hauptaktion; Dokument-Editor: Menü und Format in einer Zeile, Lineal einklappbar, Tab-Leiste nur bei mehreren Tabs,
KI-Leiste angedockt; Whiteboard: Werkzeugleiste links, Art als Auswahl, Aktionen im Kopf. **Die TU-Vorlagen selbst,
ihre Vorschauen und alles, was im TUD-Stil erzeugt wird, bleiben unverändert** – nur die Oberfläche drumherum.
**Fertig, wenn**: Bei 1366 × 768 @ 150 % hat der Dokument-Editor mindestens die Hälfte der Höhe für Text; Vorher/Nachher
aller Editoren freigegeben; `FlowEditorUiTest`, `CreativeScreenTest`, `PruefsammlungTest` grün.

### APD-11 ☐ Literatur und Modelle · M
**Befund**: Literatur startet mit langem Formular, Reiter über volle Breite (I-16); Modelle mit 19 Filter-Chips in zwei
Reihen und textlastigen Karten (W-11).
**Ziel**: Die Sammlung bzw. die passenden Modelle stehen vorn; Suchen und Filtern sind einfach.
**Vorschläge**: Literatur startet auf „Sammlung“, sobald es Einträge gibt; Suche als eine Zeile + „Erweitert“;
Modelle: Aufgabe als `Segmented`, Fähigkeiten als Filter-Menü, Karten mit Name, einem Satz, drei Kennzahlen, „Chatten“.
**Fertig, wenn**: Vorher/Nachher (leer und mit Daten, PC und Handy) freigegeben.

### APD-12 ☐ Chat-Antworten und Nebenansichten · M
**Befund**: Lora als Gesicht am Modellnamen (C-07), Code am Handy abgeschnitten (C-08), Modellvergleich mit leeren
Spalten und eigenem Eingabefeld (C-11), Bilder erzeugen mit überlappenden Vorschlägen (C-12); Arbeitskarte,
Übergabe-Karte, Persona, Chat-Menüs noch nicht bebildert.
**Ziel**: Antworten und Nebenansichten sprechen dieselbe Sprache wie der Chat.
**Vorschläge**: Neutrales Modell-Symbol, Lora nur bei ihren Vorschlägen (eigene Zeile wie im Entwurf); Code und
Tabellen mit Rand-Verlauf und Bildlaufleiste; Vergleich mit Chat-Eingabefeld und Modellkarten; Bild-Optionen in einem
Menü; `DesignReviewTest` um Arbeitskarte, Übergabe und Menüs erweitern.
**Fertig, wenn**: Vorher/Nachher freigegeben; `ChatLookTest`, `LoraPanelLookTest` grün.

### APD-13 ☐ Handy, kleine Fenster, große Schrift · M · A
**Befund**: Brüche und Kürzungen am Handy (D-04), kleine Fenster wie Handy (D-05); Android-eigene Teile noch nicht
bebildert.
**Ziel**: Jede Seite funktioniert bei 360 dp Breite und Schrift 200 % ohne Abschneiden; Touch-Ziele ≥ 48 dp.
**Vorschläge**: `DesignReviewTest` um 360 dp und Schrift 200 % erweitern; ein Test erkennt abgeschnittene Texte
(Semantik-Grenzen außerhalb des Bildschirms, Ellipse bei Titeln); Prüfung am Gerät.
**Fertig, wenn**: Keine Funde im Test; Prüfung auf einem Android-Gerät dokumentiert.

### APD-14 ☐ Kontrast, dunkles Design und Tastatur · M
**Befund**: Hoher Kontrast unter AAA an mehreren Stellen (D-02), dunkles Design mit festen Farben (D-01), Kürzel kaum in
Tooltips und Menüs (D-06), Strg+K und Kürzel-Übersicht (R-07, R-08 – falls nicht schon in APD-06).
**Ziel**: Hoher Kontrast hält AAA, dunkles Design ist gleichwertig, alles ist mit der Tastatur auffindbar.
**Vorschläge**: Kontrast-Test aus APD-03 auf alle Review-Bilder; Kürzel im Tooltip-Text und rechts in Menüs;
Prüfung am Gerät aus AP-123 (Tooltips, Enter/Esc in echten Fenstern, Bildlaufleisten, Screenreader).
**Fertig, wenn**: Kontrast-Test grün; Prüfprotokoll am Gerät im Review ergänzt.

## Später

(leer)

## Ideen

- Design-System als eigene Übersichtsseite (Farben, Schriften, Abstände, Bausteine je Stil) zum Nachschlagen.

## Erledigt

- **Grundlage vor diesem Plan**: AP-123 *Bedienelemente aus einem Guss* (13.11.2026, für 1.0.71, PR #82) – eigene
  Bausteine für Knöpfe, Dialoge (Hauptknopf, Enter/Esc, rote Lösch-Knöpfe), Symbol-Knöpfe mit Tooltip, Felder,
  Schalter, Menüs, Hinweis-Banner und Bildlaufleisten; Beschreibung im allgemeinen Arbeitsplan unter AP-123.
