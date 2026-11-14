# Design-Arbeitsplan Lora (APD)

Stand: 14.11.2026 (neu: **Editoren wie Google Docs und Word** – APD-15 Dokument-Editor, APD-16 Folien/Gestalten/Whiteboard,
APD-17 Videoeditor; Wunsch des Hauptnutzers, nur die Editoren; die Funktionen dazu stehen im allgemeinen Arbeitsplan als
AP-125 bis AP-130) · 13.11.2026 (APD-01 Review durchgeführt; die Pakete APD-02 bis APD-14 daraus wurden verworfen, ihre
Nummern bleiben unbenutzt) · Lebendes Dokument: Status hier pflegen,
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

Gilt als Abnahme jedes Pakets. Je Bildschirm:

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


---

## Übersicht

| Abschnitt | Pakete |
| --- | --- |
| **Jetzt** (14.11.2026, Editoren) | ~~APD-17a Videoeditor: Richtung wählen~~ ☑ („A mit B“) · **APD-15 Dokument-Editor aufgeräumt wie Google Docs ◐** · **APD-16 Folien, Gestalten, Whiteboard im selben Rahmen ☐** · **APD-17b Videoeditor übersichtlich ☐** |
| **Später** | – |
| **Erledigt** | APD-01 Umfassendes UI-Review (13.11.2026; Pakete daraus verworfen) · Grundlage: AP-123 *Bedienelemente aus einem Guss* (allgemeiner Arbeitsplan, 1.0.71) |

---

## Jetzt

**Editoren wie Google Docs und Word (Wunsch Hauptnutzer 14.11.2026)**: „Die Office-Funktionen müssen noch flüssiger und
intuitiver werden, wie in Google Docs und Word; die Oberfläche muss besser werden; dasselbe gilt für den Videoeditor.“
Antworten auf die Rückfragen:

- **Wichtigste Editoren**: Dokumente, dann Folien und Gestalten/Whiteboard (dort ohne eigene Erfahrung: „in Eigenregie
  optimieren“). Tabellen sind nicht im Fokus.
- **Was stört**: Der Dokument-Editor **verhält sich anders als Word** und hat **zu wenig Platz, wirkt unruhig**.
- **Vorbild der Leiste**: **Google Docs, aufgeräumt** (Menüs + schlanke Symbolleiste, kein Word-Menüband).
- **Umfang**: Nur die Oberfläche der **Editoren** (Dokument, Folien, Gestalten, Whiteboard, Video), nicht der Rest der App.
- **Bildschirme**: gemischt – vom kleinen Laptop (1366 × 768 bei 150 %) bis zum großen Monitor muss es gut gehen.
- **Umbau**: Ein **großer Umbau ist in Ordnung**, wenn es danach klar besser ist; Tastenkürzel bleiben.
- **Video**: Vorbild offen – **erst Entwürfe ansehen, dann entscheiden** (Clipchamp/CapCut-Richtung oder DaVinci, nur
  leichter). Die Seite wirkt unübersichtlich, die Zeitleiste umständlich, der Einstieg unklar, die Vorschau ruckelt.

Reihenfolge über beide Pläne: **APD-17a** (Entwürfe Video, damit die Entscheidung früh fällt) → **APD-15 + AP-125**
(Dokument: Rahmen und Word-Verhalten) → **AP-126** (Dokument: Funktionsumfang, in Stufen) → **APD-16 + AP-127** (Folien,
Gestalten, Whiteboard) → **APD-17b + AP-130** (Video: Oberfläche und flüssige Vorschau) → **AP-128** (Video: Datenschutz)
→ **AP-129** (Video: fehlende Funktionen).

### APD-17a ☑ Videoeditor: Richtung wählen · S (Wunsch Hauptnutzer 14.11.2026, D)
Zwei Entwürfe des ganzen Editors auf einer Vergleichsseite (Artefakt), gleiche Aufgabe (Vorlesung kürzen, Titel,
Export), damit der Hauptnutzer das Vorbild entscheidet:
- **A – Richtung Clipchamp/CapCut**: große Vorschau, links eine Leiste mit *Medien · Text · Übergänge · Ton · Untertitel ·
  Datenschutz* (Klick öffnet eine Galerie zum Hineinziehen), eine Zeitleiste ohne Spurnamen-Fachsprache („Video“,
  „Ton“, „Text“), Eigenschaften nur für das Gewählte, Export oben rechts.
- **B – DaVinci, nur leichter**: Aufbau wie heute (Medienpool, Vorschau, Eigenschaften, Zeitleiste mit V1/V2/A1), aber
  aufgeräumt: eine Leiste über der Zeitleiste, Werkzeuge mit Wort, Schrittkarte schmaler, klare Gruppen.
- Beide behalten alle Tasten und Loras Aktionen; beide gehen bei 1366 × 768 und auf großen Monitoren.
**Fertig, wenn**: der Hauptnutzer eine Richtung (oder eine Mischung) gewählt hat; die Wahl steht hier und im
allgemeinen Arbeitsplan unter *Grundsätze › Umfang der Werkzeuge*.
☑ *14.11.2026*: Vergleichsseite als Artefakt („Videoeditor-Richtung wählen“). **Entscheidung des Hauptnutzers: „A mit B“**
– Aufbau wie A (Clipchamp/CapCut), dazu aus B: Spurnamen, Sperren und Stummschalten erscheinen beim Überfahren einer Reihe;
genaue Zahlen für Beginn und Dauer unter den Schiebereglern; eine Mehrkamera-Ansicht für Interviews; alle Tasten, Loras
Aktionen und der Schnitt im Transkript bleiben. Grundlage für APD-17b.

### APD-15 ◐ Dokument-Editor aufgeräumt wie Google Docs · M (Wunsch Hauptnutzer 14.11.2026)
**Befund** (Bild aus APD-01 bei 1366 × 768 und 150 %): Titelzeile, Reiterzeile („Tab 1“ auch bei nur einem Tab), Menüzeile,
Formatleiste und Lineal nehmen rund 190 dp ein – über ein Drittel der Höhe; Rückgängig/Wiederholen stehen doppelt (Kopf und
Formatleiste); die Formatleiste wird rechts abgeschnitten (Listen, Einzug, Formatierung entfernen sind nur durch
Waagerecht-Scrollen erreichbar); „Mit KI schreiben“ schwebt über dem Text; die Gliederung nimmt mit Erklärtext ein
Viertel der Breite.
**Ziel**: Kopf wie Google Docs: kompakt, ruhig, alles erreichbar, möglichst viel Seite.
- **Kopf in zwei Zeilen**: oben Zurück, Titel (Klick = umbenennen), *Gespeichert*-Zustand, rechts Kommentare, Lora und
  *Teilen/Exportieren* als Hauptknopf; darunter direkt am Titel die Menüs *Datei · Bearbeiten · Ansicht · Einfügen ·
  Format · Extras* in kleiner Schrift (wie Docs). Kein Rückgängig im Kopf, nur in der Formatleiste.
- **Formatleiste in einer Zeile**, Gruppen in fester Reihenfolge wie Docs; was nicht passt, wandert in ein „⋯“-Menü
  (von rechts nach links), statt abgeschnitten zu werden. Leiste ausblendbar (Strg+Umschalt+F wie Docs: „Menüs
  ausblenden“).
- **Reiter (Tabs)** nur zeigen, wenn es mehr als einen gibt; sonst in der Gliederung „Tab hinzufügen“ (wie Docs).
- **Gliederung** schmal, ohne Erklärtext (Tooltip statt Satz), per Symbol links oben ein-/ausklappbar; Zustand bleibt.
- **Lora/KI**: kein schwebendes Feld über dem Text; Lora über das ✨ im Kopf, Strg+J und im leeren Absatz ein dezenter
  Hinweis „Strg+J: Lora schreiben lassen“ (wie Docs „Hilf mir schreiben“).
- **Statusleiste unten** (wie Word): Seite x von y, Wörter (Klick: Wortzahl-Dialog mit Zeichen), Sprache,
  Nachverfolgen an/aus, Zoom-Regler mit *Seitenbreite*.
- **Gemischte Bildschirme**: kleine Fenster – Leiste rückt in „⋯“, Lineal schmal; große – Seite mittig mit
  Lesebreite, Gliederung und Kommentare daneben ohne Überlappen.
- Die Bausteine dafür kommen nach `ui/EditorChrome.kt` (neu) und werden in APD-16 und APD-17b wiederverwendet.
**Fertig, wenn**: bei 1366 × 768 und 150 % mindestens 60 dp mehr Höhe für die Seite und nichts abgeschnitten ist;
Vorher/Nachher-Bilder (klein, Full HD, groß; hell, dunkel, Hoher Kontrast) auf der Vergleichsseite; `FlowEditorUiTest`
und die Tests von Strg+K/Spots grün.
◐ *Stand 14.11.2026 (Stufe 1, wartet auf Freigabe)*: Kopf wie Docs (`FlowDocHeader` in `ui/DocsEditor.kt`: Zurück,
Dokument-Symbol, Titel zum Umbenennen mit Status, Menüs darunter, rechts Kommentare, Lora, *Exportieren*); *Datei* enthält
alles aus dem bisherigen „⋮“ (`FlowFileMenu`, `DocFileActions`); Reiterzeile erst ab zwei Tabs, *Einfügen › Tab*;
Formatleiste über die ganze Breite (`FlowHost.menus`/`toolbar`: der Editor gibt Menüs und Leiste an das Fenster), Gruppen
wie Docs mit „⋯“ statt Abschneiden (`OverflowBar`), Ausrichtung als ein Knopf, Symbole 34 dp (`LocalToolIconSize`);
Strg+Umschalt+F bzw. Pfeil am Ende der Leiste blendet Titel und Menüs aus; kein schwebendes „Mit KI schreiben“;
Statusleiste (`FlowStatusBar`: Seite x von y, Wörter auch der Markierung, Nachverfolgen, Zoom mit *Seitenbreite*);
Gliederung 208 dp ohne Erklärtext. **Gemessen**: Text der Seite beginnt bei 215 dp statt 320 dp (Laptop), 219 statt 321 dp
(Full HD). **Nebenbei behoben**: Strg+Z/Y/F im Fließtext erreichten zuerst das Fenster (Rückgängig ganzer gespeicherter
Stände, direkt nach dem Tippen wirkungslos) – jetzt das Rückgängig und die Suche des Editors; *Datei › Exportieren* nahm
den Stand beim Öffnen – jetzt den aktuellen. Tests `ui/DocEditorChromeTest` (scheitern auf dem alten Stand),
`ui/DocEditorLookTest` (Bilder in `build/apd15`, Messung in `messung.txt`). **Offen**: Bausteine nach `ui/EditorChrome.kt`
verschieben, wenn APD-16 sie braucht; Gliederung per Symbol wieder öffnen und Zustand merken.

### APD-16 ☐ Folien, Gestalten, Whiteboard im selben Rahmen · M (Wunsch Hauptnutzer 14.11.2026, Eigenregie)
Derselbe Kopf und dieselbe Leistenlogik wie APD-15 für Folien, Gestalten und Whiteboard.
- **Gestalten** bricht bei 1366 × 768 nicht mehr (Befund APD-01: „Exportieren“ senkrecht, Seite winzig, Titel als großes
  Feld).
- **Whiteboard**: Leiste geordnet nach *Werkzeug* (Auswählen, Stift, Notiz, Form, Verbinden) · *Art* (Flussdiagramm,
  Mindmap …) im Menü · *Aktion* (Anordnen, Mermaid-Text); Rückgängig als Knopf.
- **Eigenschaften nur für das Gewählte** (wie Canva/PowerPoint): ohne Auswahl Seite/Folie, mit Auswahl Text, Form oder
  Bild; Leiste über der Fläche statt zweiter Spalte, wo der Platz knapp ist.
- **Fläche zuerst**: Folien-/Seitenleiste schmal und einklappbar, Fläche füllt den Rest.
**Fertig, wenn**: alle drei Editoren bei 1366 × 768 und 150 % ohne Umbruch und Abschneiden; Vorher/Nachher-Bilder.

### APD-17b ☐ Videoeditor übersichtlich · L (Wunsch Hauptnutzer 14.11.2026, D, nach APD-17a)
Umbau nach der gewählten Richtung **„A mit B“** (siehe APD-17a): eine übersichtliche Seite (weniger gleichzeitig, klare Gruppen), klarer Einstieg (leeres
Projekt zeigt „Video hierher ziehen“ in der Zeitleiste), Zeitleiste wie selbstverständlich (siehe AP-130 für das Tempo):
Clips ziehen und kürzen mit Einrasten und sichtbarer Zeit, Strg+Mausrad zoomt um den Mauszeiger, Lücken schließen per
Rechtsklick, Spuren mit Klartext-Namen, gewählte Clips deutlich. Derselbe Kopf wie APD-15.
**Fertig, wenn**: jemand ohne Vorkenntnisse eine Vorlesung kürzt, einen Titel setzt und exportiert, ohne nach dem Weg zu
fragen (wie AP-121); Vorher/Nachher-Bilder; `VideoLayoutTest`, `VideoEaseTest`, `VideoReviewTest` grün.

---

## Später

–

## Ideen

–

## Erledigt

- **APD-01 Umfassendes UI-Review** (13.11.2026): Review mit Bildern und 89 Befunden; vom Hauptnutzer nicht umgesetzt,
  die Pakete APD-02 bis APD-14 verworfen. Befunde zu den Editoren (I-10 bis I-14, W-06) sind in APD-15 bis APD-17
  eingeflossen.
