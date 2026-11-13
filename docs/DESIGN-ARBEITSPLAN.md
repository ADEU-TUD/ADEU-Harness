# Design-Arbeitsplan Lora (APD)

Stand: 13.11.2026 (angelegt; APD-01 *Umfassendes UI-Review* als Erstes) · Lebendes Dokument: Status hier pflegen,
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
| Anspruch | Die App sieht **seriös und modern** aus – ruhig, klar, aufgeräumt, passend für Forschung, Lehre und Verwaltung an der TU Dresden. Kein Effekt um seiner selbst willen. |
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
12. **Eindruck** – Wirkt der Bildschirm seriös, modern und fertig? Was würde eine Designerin als Erstes ändern?

---

## Übersicht

| Abschnitt | Pakete |
| --- | --- |
| **Jetzt** | **APD-01 Umfassendes UI-Review der ganzen App ☐** |
| **Danach** | (entsteht aus APD-01: je Befundgruppe ein Paket APD-02 ff.) |
| **Später** | – |
| **Erledigt** | Grundlage: AP-123 *Bedienelemente aus einem Guss* (allgemeiner Arbeitsplan, 1.0.71) |

---

## Jetzt

### APD-01 ☐ Umfassendes UI-Review der ganzen App · L (Wunsch Hauptnutzer 13.11.2026)
**Ziel**: Die ganze Oberfläche einmal vollständig und ehrlich prüfen – mit dem Blick einer erfahrenen UI/UX-Designerin –
und daraus einen geordneten Plan machen, mit dem wir das Design Stück für Stück verbessern. **In diesem Paket wird nichts
an der Oberfläche umgebaut** (höchstens Werkzeuge für Bilder und Tests); offensichtliche Fehler werden als Befund mit
Schweregrad *hoch* erfasst.

**Vorgehen**
1. **Bilder von allem**: ein Test (z. B. `ui/UiReviewTest`, Bilder nach `build/review/`), der jeden Bereich unten
   rendert – PC-Fenster 1366 × 768 (bei 150 % also rund 910 × 512 dp) und ein breites Fenster, Handy-Breite (rund
   400 dp) für die Bereiche, die es auf Android gibt; ADEU hell und dunkel, dazu TU Dresden und Hoher Kontrast
   stichprobenartig; leere und gefüllte Zustände mit Beispieldaten; wichtige Dialoge, Menüs und Hinweise. Was sich im
   Test nicht rendern lässt (z. B. Browser, natives Fenster), im Bericht nennen.
2. **Prüfen** nach dem *Prüfmaßstab* oben (12 Punkte), Bereich für Bereich.
3. **Befunde erfassen** in `docs/design/review-2026-11.md` (neu), je Befund: Bereich, Ort im Code (Datei/Composable),
   was ist, warum es stört (Prüfpunkt), Schweregrad, **Vorschlag** (konkret, mit Skizze in Worten oder Bild), Aufwand
   (S/M/L), betroffene Plattformen und Stile. Bereichsübergreifende Muster (z. B. „Seitenköpfe uneinheitlich“) als
   eigener Befund, nicht zwanzigmal einzeln.
4. **Pakete bilden**: Befunde zu Paketen APD-02 ff. bündeln (eher nach Thema oder Bildschirm als nach Einzelbefund),
   unter *Danach* eintragen – je Paket Befund, Ziel, Vorschläge, *Fertig, wenn*, Größe – und nach Nutzen ordnen:
   zuerst, was im Alltag am meisten stört oder am häufigsten gesehen wird (Chat, Seitenleiste, Dateien, Einstellungen).
5. **Übersichtsseite** (Artefakt) für den Hauptnutzer: die wichtigsten Befunde mit Bild und Vorschlag, die Paketliste mit
   Reihenfolge – damit er Prioritäten setzen und streichen kann. Erst danach gilt APD-01 als fertig.

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
Bildlaufleisten, Hoher Kontrast mit Screenreader.

**Fertig, wenn**: Bilder aller Bereiche vorliegen, `docs/design/review-2026-11.md` jeden Bereich mit Befunden und
Vorschlägen abdeckt, die Pakete APD-02 ff. hier unter *Danach* stehen und der Hauptnutzer die Reihenfolge auf der
Übersichtsseite freigegeben hat.

---

## Danach

(leer – entsteht aus APD-01)

## Später

(leer)

## Ideen

- Design-System als eigene Übersichtsseite (Farben, Schriften, Abstände, Bausteine je Stil) zum Nachschlagen.

## Erledigt

- **Grundlage vor diesem Plan**: AP-123 *Bedienelemente aus einem Guss* (13.11.2026, für 1.0.71, PR #82) – eigene
  Bausteine für Knöpfe, Dialoge (Hauptknopf, Enter/Esc, rote Lösch-Knöpfe), Symbol-Knöpfe mit Tooltip, Felder,
  Schalter, Menüs, Hinweis-Banner und Bildlaufleisten; Beschreibung im allgemeinen Arbeitsplan unter AP-123.
