package io.github.thepytales.llmhub.data

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * Templates that come with the app, made for the staff of the Professur für Allgemeine Didaktik und Empirische
 * Unterrichtsforschung: kept in code so every update brings the current versions to everybody. Own templates
 * are stored separately (templates.json) and can be shared as text.
 *
 * Prompt templates show only their fields in the chat; [Template.instructions] tell the model what to do.
 */
object TemplateLibrary {
    /** Id prefix of built-in templates. */
    const val BUILT_IN = "b_"

    private const val HONEST = "Erfinde keine Fakten, Namen, Zahlen, Termine, Zitate oder Literaturangaben. Fehlen Angaben, " +
        "ohne die das Ergebnis nicht brauchbar ist, frage zuerst kurz und gezielt nach (höchstens drei Fragen) und erstelle " +
        "das Dokument erst danach; nebensächliche Lücken lässt du weg. Nur wenn der Nutzer ausdrücklich einen Entwurf " +
        "möchte, setze für Fehlendes gut sichtbare Platzhalter in eckigen Klammern, z.B. [Datum]. "
    private const val ACADEMIC = "Schreibe sachlich und präzise, geschlechtergerecht (z.B. Studierende, Lehrkräfte, " +
        "Schüler:innen), ohne Emojis und ohne Werbesprache. "

    private const val TUD = "Erstelle mit create_document ein PDF mit style=tud (Corporate Design der TU Dresden). Logo, " +
        "Einrichtung, Datum und Fußzeile setzt die App – wiederhole sie nicht im Text. Sachlicher, förmlicher Ton wie in " +
        "amtlichen Schreiben der Universität, Sie-Form, keine Emojis, keine Ausrufezeichen. " + HONEST

    /** How form fields are written so the TUD style draws them as labelled boxes. */
    private const val FIELDS = "Formularfelder als zweispaltige Tabelle: Kopfzeile „| Angabe | Eintrag |“, darunter je " +
        "Feld eine Zeile „| Bezeichnung | |“ mit leerer rechter Zelle. Freitext als einspaltige Tabelle mit leerer Zeile. " +
        "Auswahl als „- [ ] Option“. Abschnitte als „## 1. …“, „## 2. …“. Am Ende eine Tabelle " +
        "„| Ort, Datum | Unterschrift |“ mit einer leeren Zeile. "

    private fun tud(id: String, name: String, description: String, content: String, instructions: String) =
        prompt(TemplateCategory.TUD, "tud_$id", name, description, content, TUD + instructions)

    private fun prompt(category: TemplateCategory, id: String, name: String, description: String, content: String, instructions: String) =
        Template(
            id = BUILT_IN + id, name = name, kind = TemplateKind.PROMPT, content = content.trimIndent(),
            description = description, category = category.name, instructions = instructions,
        )

    private fun persona(id: String, name: String, description: String, content: String) =
        Template(id = BUILT_IN + "p_" + id, name = name, kind = TemplateKind.PERSONA, content = content, description = description)

    val builtIn: List<Template> = listOf(
        // ── TUD-Dokumente ──────────────────────────────────────────────────────────────────────────────────────
        tud(
            "letter", "Brief", "Schreiben nach DIN 5008 – Ihr Text wird genau so übernommen",
            """
            Absender: {{Absender|Ihr Name, darunter je Zeile: Funktion, Telefon, E-Mail, Raum}}
            Empfänger: {{Empfänger|Je Zeile: Name (z.B. Frau Dr. Anna Müller), Einrichtung, Straße Nr., PLZ Ort}}
            Betreff: {{Betreff|Worum es geht, in einer Zeile – steht fett über dem Brief}}
            Bezug: {{Bezug|optional, z.B. Ihr Schreiben vom 12. Oktober 2026}}
            Anrede: {{Anrede|optional – sonst passend zum Empfänger, z.B. „Sehr geehrte Frau Dr. Müller,“}}
            Brieftext: {{Brieftext|Der eigentliche Brief ohne Anrede und Gruß – wird wörtlich übernommen. Nur Stichpunkte? ✨ formuliert sie aus.}}
            """,
            "Der Brief wurde von der App direkt aus den Angaben erstellt (create_document). Bei Änderungswünschen " +
                "edit_document mit dem Dateinamen nutzen; den Text des Nutzers nur so weit ändern, wie er es verlangt.",
        ).copy(direct = "letter"),
        prompt(
            TemplateCategory.TUD, "tud_presentation", "Präsentation (PowerPoint)",
            "Folien im TUD-Design – aus Ihren Stichpunkten oder angehängten Dokumenten",
            """
            Thema / Titel: {{Thema|z.B. Classroom Management in der Sekundarstufe}}
            Anlass: {{Anlass|z.B. Vortrag im Kolloquium, Workshop, Tagung, Teamsitzung}}
            Vortragende und Datum: {{Vortragende|Name(n) und Datum für die Titelfolie}}
            Zielgruppe und Dauer: {{Zielgruppe und Dauer|z.B. Lehramtsstudierende, 20 Minuten}}
            Inhalte: {{Inhalte|Stichpunkte oder Gliederung – oder einfach Dateien anhängen (PDF, Word, Text …)}}
            Folienzahl: {{Folienzahl|optional, z.B. etwa 12}}
            """,
            "Erstelle mit create_document eine PowerPoint-Präsentation (format=pptx) im Corporate Design der TU Dresden. " +
                "Nutze alle Angaben und angehängten Dokumente gründlich; Aussagen, Zahlen und Quellen nur aus diesen Unterlagen. " +
                "Aufbau: Titelfolie, Agenda, Abschnittsfolien für die Hauptteile, Inhaltsfolien mit kurzen Stichpunkten statt " +
                "Fließtext, wo sinnvoll Tabelle oder Gegenüberstellung in zwei Spalten, Fazit bzw. Take-home-Message, bei Bedarf " +
                "Literaturfolie (APA 7) und eine Schlussfolie für Fragen. Zu jeder Inhaltsfolie Sprechernotizen mit 2–5 Sätzen. " +
                "Ohne Angabe etwa eine Folie pro zwei bis drei Minuten. " + ACADEMIC + HONEST +
                "Fasse danach in zwei Sätzen zusammen, was die Präsentation enthält.",
        ),
        tud(
            "minutes", "Protokoll", "Ergebnisprotokoll mit Tagesordnung, Beschlüssen und Aufgaben",
            """
            Gremium / Anlass: {{Gremium|z.B. Teamsitzung der Professur, Institutsrat}}
            Datum, Zeit, Ort: {{Datum, Zeit, Ort}}
            Sitzungsleitung: {{Sitzungsleitung}}
            Protokoll: {{Protokoll|Name}}
            Anwesend: {{Anwesend}}
            Entschuldigt: {{Entschuldigt|optional}}
            Notizen: {{Notizen|Stichpunkte je Tagesordnungspunkt, Beschlüsse, Aufgaben}}
            """,
            "title: „Protokoll der … am …“. Aufbau: Tabelle „| Angabe | Eintrag |“ mit Datum, Zeit, Ort, " +
                "Sitzungsleitung, Protokoll, Anwesend, Entschuldigt; dann „## Tagesordnung“ als nummerierte Liste; je Punkt " +
                "„## TOP n – Thema“ mit sachlicher Zusammenfassung im Präteritum; Beschlüsse als „> [!IMPORTANT]“ mit " +
                "Abstimmungsergebnis; am Ende „## Aufgaben“ als Tabelle (Nr. | Aufgabe | Verantwortlich | Termin) und " +
                "eine Tabelle „| Protokoll | Sitzungsleitung |“ mit leerer Zeile für die Unterschriften.",
        ),
        tud(
            "memo", "Aktennotiz", "Vermerk über ein Gespräch, Telefonat oder einen Vorgang",
            """
            Anlass / Gespräch mit: {{Anlass}}
            Datum: {{Datum}}
            Beteiligte: {{Beteiligte|optional}}
            Aktenzeichen: {{Aktenzeichen|optional}}
            Was wurde besprochen? {{Inhalt|Stichpunkte}}
            """,
            "title: „Aktennotiz“ bzw. „Vermerk“. Aufbau: Tabelle „| Angabe | Eintrag |“ mit Datum, Verfasser:in, " +
                "Beteiligte, Anlass, Aktenzeichen; dann „## Sachverhalt“, „## Ergebnis“, „## Weiteres Vorgehen“. Knapp, " +
                "im Präteritum, ohne Wertungen.",
        ),
        tud(
            "form", "Formular zum Ausfüllen", "Antrag, Anmeldung oder Erhebungsbogen mit Feldern und Ankreuzkästchen",
            """
            Art des Formulars: {{Art|z.B. Anmeldung zum Workshop, Antrag auf …, Rückmeldebogen}}
            Benötigte Angaben: {{Angaben|z.B. Name, Matrikelnummer, Studiengang, Auswahl}}
            Einreichen bei / bis: {{Abgabe|optional}}
            """,
            "Gestalte ein Formular zum Ausdrucken und handschriftlichen Ausfüllen. Unter dem Titel ein Satz zum Zweck und " +
                "ein „> [!NOTE]“ mit Ausfüll- und Abgabehinweisen (wo, bis wann). " + FIELDS +
                "Bei personenbezogenen Daten am Schluss einen kurzen Datenschutzhinweis (Zweck, Speicherdauer).",
        ),
        tud(
            "info", "Merkblatt / Aushang", "Informationsblatt für Studierende, Lehrende oder Schulen",
            """
            Thema: {{Thema}}
            Zielgruppe: {{Zielgruppe|z.B. Studierende im Modul EW-SEGY-BW 2}}
            Inhalte, Fristen, Kontakt: {{Inhalte}}
            """,
            "Aufbau: ein einleitender Satz, Abschnitte „## 1. …“ mit kurzen Absätzen und Stichpunkten, Fristen und " +
                "Pflichten als „> [!WARNING]“, am Ende „## Kontakt“ als Tabelle „| Angabe | Eintrag |“. Höchstens zwei Seiten.",
        ),
        tud(
            "report", "Bericht", "Projekt-, Tätigkeits- oder Abschlussbericht",
            """
            Projekt / Thema: {{Projekt}}
            Zeitraum: {{Zeitraum}}
            Verantwortlich / Förderung: {{Verantwortlich|optional}}
            Inhalte und Ergebnisse: {{Inhalte|Stichpunkte oder Datei anhängen}}
            """,
            "Aufbau: Tabelle „| Angabe | Eintrag |“ mit Projekt, Zeitraum, Verantwortlich, Förderung; „## Zusammenfassung“; " +
                "„## 1. Ausgangslage und Ziele“, „## 2. Vorgehen“, „## 3. Ergebnisse“ (gern mit Tabellen), " +
                "„## 4. Fazit und Ausblick“.",
        ),
        tud(
            "invitation", "Einladung mit Tagesordnung", "Einladung zu Sitzung, Kolloquium oder Workshop",
            """
            Anlass: {{Anlass|z.B. Forschungskolloquium, Teamsitzung}}
            Datum, Uhrzeit, Ort: {{Termin|Raum oder Link}}
            Tagesordnungspunkte: {{Tagesordnung}}
            Anmeldung bis / bei: {{Anmeldung|optional}}
            """,
            "title: „Einladung zur … am …“. Aufbau: kurzer förmlicher Einladungstext, Tabelle „| Angabe | Eintrag |“ mit " +
                "Datum, Uhrzeit, Ort bzw. Link, Leitung; „## Tagesordnung“ als Tabelle (TOP | Thema | Verantwortlich | Zeit); " +
                "Hinweise zu Unterlagen und Anmeldung.",
        ),
        tud(
            "certificate", "Teilnahmebescheinigung", "Bescheinigung, Zertifikat oder Urkunde nach der TUD-Vorlage",
            """
            Art: {{Art|Teilnahmebescheinigung, Zertifikat oder Urkunde}}
            Teilnehmer:in: {{Name|mit akademischem Grad}}
            Einrichtung der Person: {{Einrichtung|optional, z.B. Schule oder Universität}}
            Veranstaltung: {{Veranstaltung|Titel der Fortbildung, des Workshops oder Seminars}}
            Zeitraum und Umfang: {{Zeitraum|z.B. 3. bis 5. März 2026, 16 Unterrichtseinheiten}}
            Inhalte: {{Inhalte|optional, für den Text im Kleingedruckten}}
            Unterzeichnende: {{Unterzeichnende|Name und Funktion, eine oder zwei Personen}}
            """,
            "Nutze layout=urkunde. title = Art (z.B. „Teilnahmebescheinigung“). content als Markdown genau in dieser " +
                "Reihenfolge: „## Name der Person“; optional ein Absatz „von der …“; ein Absatz „hat erfolgreich an der " +
                "Veranstaltung“ bzw. „hat erfolgreich am Workshop“; „## Titel der Veranstaltung“; ein Absatz mit Einrichtung " +
                "(„an der Professur für … der Technischen Universität Dresden“), Zeitraum, Umfang und „teilgenommen.“; " +
                "optional Inhalte als Zitatblock „> …“ (Kleingedrucktes, 2–4 Sätze); am Ende eine Tabelle mit einer Spalte " +
                "je unterzeichnender Person: Kopfzeile Name mit Titel, zweite Zeile Funktion. Datum und Ort setzt die App.",
        ),

        // ── Lehre ──────────────────────────────────────────────────────────────────────────────────────────────
        prompt(
            TemplateCategory.TEACHING, "teach_syllabus", "Seminarplan", "Semesterplan mit Lernzielen, Sitzungen und Leistungen",
            """
            Lehrveranstaltung / Modul: {{Lehrveranstaltung|z.B. Seminar im Modul EW-SEGY-BW 2}}
            Zielgruppe: {{Zielgruppe|Studiengang, Semester, Vorwissen}}
            Umfang und Format: {{Format|z.B. 14 Sitzungen à 90 Minuten, Präsenz}}
            Thema und Schwerpunkte: {{Thema}}
            Prüfungs- bzw. Studienleistung: {{Leistung|optional}}
            Wünsche: {{Wünsche|optional, z.B. aktivierende Methoden, Bezug zu Inklusion oder Digitalisierung}}
            """,
            "Erstelle einen Seminarplan für eine Lehrveranstaltung im Lehramtsstudium. Beginne mit 3–5 überprüfbaren " +
                "Lernzielen („Die Studierenden können …“, Verben passend zur Taxonomie nach Anderson & Krathwohl). Dann eine " +
                "Tabelle | Nr. | Thema | Inhalte und Leitfrage | Methoden / Sozialform | Vorbereitung der Studierenden |. " +
                "Achte auf Constructive Alignment zwischen Zielen, Methoden und Leistung; plane Einführung, Erarbeitung, " +
                "Anwendung/Transfer, Abschluss und eine Puffer- oder Konsultationssitzung. Literatur nur nennen, wenn du sie " +
                "sicher kennst, sonst [Literatur]. Zum Schluss Hinweise zur Studienleistung und zu Bewertungskriterien. " +
                ACADEMIC + HONEST + "Biete am Ende an, den Plan als PDF im TUD-Design zu erstellen.",
        ),
        prompt(
            TemplateCategory.TEACHING, "teach_session", "Sitzung planen", "Verlaufsplan für eine Seminarsitzung oder Unterrichtsstunde",
            """
            Thema der Sitzung: {{Thema}}
            Lernziele: {{Lernziele|optional – sonst schlägt das Modell welche vor}}
            Dauer: {{Dauer|z.B. 90 Minuten}}
            Gruppe: {{Gruppe|Anzahl, Studiengang oder Klasse, Vorwissen}}
            Rahmen: {{Rahmen|optional: Raum, Technik, Präsenz oder online}}
            """,
            "Formuliere zuerst 2–3 Lernziele (falls nicht angegeben). Plane die Sitzung als Verlaufsplan-Tabelle | Zeit | " +
                "Phase | Inhalt / Aktivität | Methode & Sozialform | Medien | Funktion der Phase |: Einstieg/Aktivierung, " +
                "Erarbeitung, Sicherung, Transfer/Reflexion, Abschluss mit Ausblick. Wechsle Sozialformen, formuliere die " +
                "Arbeitsaufträge wörtlich aus (als Zitatblock) und plane Zeitpuffer ein. Ergänze kurz Differenzierung, " +
                "kognitive Aktivierung und mögliche Stolpersteine. " + ACADEMIC + HONEST +
                "Biete am Ende an, dazu passende Folien als PowerPoint im TUD-Design zu erstellen.",
        ),
        prompt(
            TemplateCategory.TEACHING, "teach_slides", "Seminarsitzung als Folien", "PowerPoint für eine Sitzung – mit Ablauf, Arbeitsaufträgen und Sprechernotizen",
            """
            Seminar / Modul: {{Seminar|z.B. Allgemeine Didaktik, EW-SEGY-BW 2}}
            Sitzung und Thema: {{Sitzung|z.B. Sitzung 4: Classroom Management}}
            Datum und Dozent:in: {{Datum und Dozent:in|für die Titelfolie}}
            Dauer: {{Dauer|z.B. 90 Minuten}}
            Lernziele: {{Lernziele|optional – sonst schlägt das Modell welche vor}}
            Inhalte und Material: {{Inhalte|Stichpunkte, Verlaufsplan oder Texte – oder Dateien anhängen (Literatur, Folien, Skript)}}
            """,
            "Erstelle mit create_document eine PowerPoint-Präsentation (format=pptx) im Corporate Design der TU Dresden für " +
                "diese Seminarsitzung. Folien: Titelfolie (Seminar, Sitzung, Datum, Dozent:in), Lernziele, Ablauf der Sitzung " +
                "mit Zeiten als Tabelle (| Zeit | Phase | Aktivität |), Einstieg mit Impuls oder Fallvignette, Abschnittsfolien " +
                "für die Inhaltsteile, Arbeitsaufträge als eigene Folien (Sozialform, Zeit, Auftrag, erwartetes Ergebnis), " +
                "Sicherung bzw. Zusammenfassung, Ausblick auf die nächste Sitzung und Literatur (APA 7, nur angegebene oder " +
                "angehängte Quellen). Kurze Stichpunkte statt Fließtext; zu jeder Folie Sprechernotizen mit Moderationshinweisen. " +
                ACADEMIC + HONEST + "Fasse danach kurz zusammen, was die Folien enthalten.",
        ),
        prompt(
            TemplateCategory.TEACHING, "teach_objectives", "Lernziele formulieren", "Operationalisierte Lernziele nach Taxonomiestufen",
            """
            Thema / Kompetenz: {{Thema}}
            Zielgruppe: {{Zielgruppe|z.B. Lehramtsstudierende im 3. Semester, Klasse 8}}
            Anzahl: {{Anzahl|z.B. 5}}
            """,
            "Formuliere operationalisierte Lernziele („Die Studierenden können …“ bzw. „Die Schüler:innen können …“) mit " +
                "beobachtbaren Verben. Ordne jedes Ziel einer Stufe der Taxonomie nach Anderson & Krathwohl (Erinnern, " +
                "Verstehen, Anwenden, Analysieren, Bewerten, Erschaffen) und einer Wissensart zu. Ausgabe als Tabelle | " +
                "Lernziel | Taxonomiestufe | Wissensart | Mögliche Überprüfung |, mit Progression von niedrigen zu höheren " +
                "Stufen. " + ACADEMIC,
        ),
        prompt(
            TemplateCategory.TEACHING, "teach_exam", "Prüfungsaufgaben", "Klausur- oder Prüfungsaufgaben mit Erwartungshorizont",
            """
            Inhalte: {{Inhalte|Themen, Texte, Lernziele – oder Datei anhängen}}
            Prüfungsform: {{Prüfungsform|z.B. Klausur 90 min, Single Choice, offene Aufgaben}}
            Umfang: {{Umfang|z.B. 6 Aufgaben, 60 Punkte}}
            Anforderungsniveau: {{Niveau|optional, z.B. Schwerpunkt Anwenden und Analysieren}}
            """,
            "Entwickle Prüfungsaufgaben, die zu den Inhalten passen und alle Anforderungsbereiche abdecken (I Reproduktion, " +
                "II Reorganisation und Transfer, III Reflexion und Problemlösung). Je Aufgabe: Aufgabenstellung mit Operator " +
                "(nennen, erläutern, analysieren, beurteilen …), Punkte, Anforderungsbereich und Erwartungshorizont mit " +
                "Bewertungshinweisen. Bei geschlossenen Formaten plausible Distraktoren und die richtige Lösung markieren. " +
                "Am Ende eine Übersicht | Aufgabe | Thema | Anforderungsbereich | Punkte |. " + ACADEMIC + HONEST,
        ),
        prompt(
            TemplateCategory.TEACHING, "teach_rubric", "Bewertungsraster", "Kriterien und Niveaustufen für Hausarbeit, Referat, Portfolio",
            """
            Leistung: {{Leistung|z.B. Hausarbeit, Referat, Portfolio, Unterrichtsentwurf}}
            Aufgabenstellung: {{Aufgabenstellung|optional}}
            Wichtige Kriterien: {{Kriterien|optional – sonst schlägt das Modell welche vor}}
            Skala: {{Skala|z.B. Noten 1,0–5,0, Punkte, bestanden/nicht bestanden}}
            """,
            "Erstelle ein analytisches Bewertungsraster als Tabelle: Zeilen = Kriterien (z.B. Fragestellung, Theoriebezug, " +
                "Methodik, Argumentation, Reflexion, Form und wissenschaftliches Arbeiten), Spalten = Gewichtung in Prozent " +
                "und vier Niveaustufen mit kurzen, beobachtbaren Beschreibungen. Darunter die Umrechnung in die Skala und " +
                "Hinweise zur Anwendung. " + ACADEMIC + "Biete an, es als ausfüllbares PDF im TUD-Design zu erstellen.",
        ),
        prompt(
            TemplateCategory.TEACHING, "teach_feedback", "Feedback zu einer Studienleistung", "Wertschätzend und konkret – Arbeit als Datei anhängen",
            """
            Art der Leistung: {{Art|z.B. Hausarbeit, Portfolio, Reflexionsbericht}}
            Aufgabenstellung / Kriterien: {{Kriterien|optional, oder Bewertungsraster anhängen}}
            Schwerpunkt: {{Schwerpunkt|optional, z.B. Argumentation, Theoriebezug, wissenschaftliches Schreiben}}
            Umfang: {{Umfang|z.B. kurz und motivierend, ausführlich}}
            """,
            "Gib Feedback zur angehängten Studienleistung: 1. Gesamteindruck (2–3 Sätze), 2. Stärken (konkret, mit " +
                "Textstellen), 3. Entwicklungsfelder (priorisiert, je mit konkretem Vorschlag), 4. Form und wissenschaftliches " +
                "Arbeiten, 5. Fazit. Sachbezogen, wertschätzend, mit Seiten- oder Abschnittsangaben. Schlage keine Note vor, " +
                "außer der Nutzer bittet ausdrücklich darum – die Bewertung trifft die prüfende Person. Ist keine Datei " +
                "angehängt, bitte darum. " + ACADEMIC + HONEST,
        ),
        prompt(
            TemplateCategory.TEACHING, "teach_lessonplan", "Unterrichtsentwurf besprechen", "Rückmeldung im Praktikum – Entwurf als Datei anhängen",
            """
            Fach und Klassenstufe: {{Fach und Klassenstufe}}
            Thema der Stunde: {{Thema}}
            Kontext: {{Kontext|z.B. Schulpraktikum, Blockpraktikum, Lehrprobe}}
            Fokus: {{Fokus|optional, z.B. Lernziele, Phasierung, Differenzierung, Inklusion}}
            """,
            "Analysiere den angehängten Unterrichtsentwurf aus allgemeindidaktischer Sicht: Bedingungsanalyse, Sachanalyse " +
                "und didaktische Reduktion, Lernziele (operationalisiert, lehrplanbezogen), Phasierung und Zeitplanung, " +
                "Methoden und Medien, kognitive Aktivierung, Differenzierung und Inklusion, Sicherung und Lernerfolgskontrolle. " +
                "Gliederung: Stärken – Fragen zum Entwurf – konkrete Überarbeitungsvorschläge (priorisiert) – Impulse für das " +
                "Nachgespräch. Wertschätzend und entwicklungsorientiert. Ist kein Entwurf angehängt, bitte darum. " + ACADEMIC,
        ),
        prompt(
            TemplateCategory.TEACHING, "teach_vignette", "Unterrichtsvignette", "Fallbeispiel aus dem Unterricht für die Analyse im Seminar",
            """
            Thema: {{Thema|z.B. Classroom Management, Heterogenität, Feedback, Fachlichkeit}}
            Schulform und Klassenstufe: {{Schulform}}
            Ziel im Seminar: {{Ziel|z.B. Analyse mit einem Theoriemodell, Handlungsalternativen entwickeln}}
            """,
            "Schreibe eine realistische Unterrichtsvignette (250–400 Wörter) mit kurzen Dialogen, die eine typische, " +
                "mehrdeutige Situation zum Thema zeigt, ohne die Lösung vorwegzunehmen. Danach 3–5 Analyseaufträge mit " +
                "steigendem Anspruch, passende theoretische Bezüge als Stichworte und Hinweise für die Seminarleitung. " +
                "Personen fiktiv, divers und ohne Stereotype. " + ACADEMIC,
        ),

        // ── Betreuung & Prüfung ───────────────────────────────────────────────────────────────────────────────
        prompt(
            TemplateCategory.THESES, "thesis_expose", "Exposé-Feedback", "Rückmeldung zu Fragestellung, Theorie, Methode und Zeitplan",
            """
            Art der Arbeit: {{Art|Bachelor-, Master- oder Wissenschaftliche Arbeit (Staatsexamen)}}
            Thema: {{Thema}}
            Fokus: {{Fokus|optional}}
            """,
            "Prüfe das angehängte Exposé: Relevanz und Eingrenzung des Themas, Präzision der Forschungsfrage, theoretische " +
                "Verortung und Forschungsstand, Passung von Fragestellung und Methode (Design, Stichprobe, Erhebung, " +
                "Auswertung), Machbarkeit im Bearbeitungszeitraum, Forschungsethik und Datenschutz (z.B. Erhebungen an " +
                "Schulen), Gliederung und Zeitplan. Antwort: Stärken – zentrale Rückfragen – konkrete Empfehlungen " +
                "(priorisiert) – nächste Schritte. Ermutigend und direkt umsetzbar. Ist kein Exposé angehängt, bitte darum. " +
                ACADEMIC + HONEST,
        ),
        prompt(
            TemplateCategory.THESES, "thesis_review", "Gutachten zur Abschlussarbeit", "Entwurf eines Gutachtens aus Ihren Notizen",
            """
            Art der Arbeit: {{Art|Bachelor-, Master- oder Wissenschaftliche Arbeit}}
            Titel: {{Titel}}
            Verfasser:in: {{Verfasser:in}}
            Gutachter:in: {{Gutachter:in|Name, Funktion}}
            Bewertungsnotizen: {{Notizen|Stärken, Schwächen, Eindrücke je Kapitel}}
            Notenvorschlag: {{Note}}
            """,
            "Formuliere aus den Notizen den Entwurf eines Gutachtens: 1. Thema und Fragestellung, 2. Aufbau, 3. Theoretische " +
                "Fundierung und Forschungsstand, 4. Methodisches Vorgehen, 5. Ergebnisse und Diskussion, 6. Form und " +
                "wissenschaftliches Arbeiten, 7. Gesamturteil mit dem angegebenen Notenvorschlag. Stütze dich nur auf die " +
                "Notizen und eine ggf. angehängte Arbeit; Bewertung und Note gibt die prüfende Person vor – ändere sie nicht. " +
                "Sachlich-abwägend wie in universitären Gutachten. " + ACADEMIC + HONEST +
                "Biete am Ende an, das Gutachten als PDF im TUD-Design zu erstellen.",
        ),
        prompt(
            TemplateCategory.THESES, "thesis_topics", "Themen für Abschlussarbeiten", "Themenvorschläge mit Forschungsfrage und Design",
            """
            Forschungsfeld: {{Forschungsfeld|z.B. Gamifiziertes Lernen, Fachlichkeit, Inklusion, Digitalisierung/VR, Lehrkräftebildung, Seiteneinstieg}}
            Abschluss: {{Abschluss|Bachelor, Master oder Staatsexamen}}
            Methodische Ausrichtung: {{Methode|qualitativ, quantitativ, Mixed Methods oder theoretisch}}
            Rahmen: {{Rahmen|optional, z.B. Daten aus einem Projekt, Feldzugang}}
            """,
            "Schlage 5 machbare Themen vor, passend zu den Schwerpunkten der Professur für Allgemeine Didaktik und Empirische " +
                "Unterrichtsforschung (empirische Unterrichts- und Lehrkräftebildungsforschung, Professionalisierung, " +
                "gamifiziertes Lernen, allgemeindidaktische Bedingungen fachlichen Lernens, Inklusion, Digitalisierung). " +
                "Je Thema: Arbeitstitel, Forschungsfrage, Relevanz in 2 Sätzen, mögliches Design (Stichprobe, Erhebung, " +
                "Auswertung), Machbarkeitshinweise und 2–3 Suchbegriffe für die Recherche (keine erfundenen Quellen). " + ACADEMIC,
        ),
        prompt(
            TemplateCategory.THESES, "thesis_reply", "Antwort auf Betreuungsanfrage", "Zusage, Absage oder Rückfragen per E-Mail",
            """
            Anfrage: {{Anfrage|E-Mail der/des Studierenden einfügen}}
            Entscheidung: {{Entscheidung|Zusage, Absage, Rückfragen oder Gesprächstermin}}
            Weitere Punkte: {{Punkte|optional, z.B. Exposé bis …, Sprechzeit, Formalia}}
            Absender: {{Absender}}
            """,
            "Schreibe eine Antwort-E-Mail an die/den Studierende:n: Betreff, Anrede, klare Aussage zur Entscheidung, nächste " +
                "Schritte mit Terminen und Fristen, freundlicher Abschluss, Signatur aus den Absenderangaben. Kurz und " +
                "verbindlich; bei einer Absage wertschätzend und möglichst mit Hinweis auf Alternativen. " + HONEST,
        ),
        prompt(
            TemplateCategory.THESES, "thesis_oral", "Mündliche Prüfung vorbereiten", "Ablauf, Leitfragen, Erwartungshorizont und Protokollbogen",
            """
            Prüfungsgebiet / Themen: {{Themen}}
            Dauer: {{Dauer|z.B. 30 Minuten}}
            Modul / Prüfungsordnung: {{Modul|optional}}
            """,
            "Erstelle: 1. einen Ablaufvorschlag mit Zeiten (Einstieg, Themenblöcke, Abschluss), 2. je Thema 3–4 Leitfragen " +
                "mit steigendem Anspruch und Stichpunkten zum Erwartungshorizont, 3. einen Protokollbogen als Tabelle | Zeit | " +
                "Frage | Antwort in Stichworten | Einschätzung | mit Angaben zu Prüfer:in, Beisitz, Kandidat:in, Datum und " +
                "Note. " + ACADEMIC + "Biete an, den Protokollbogen als ausfüllbares PDF im TUD-Design zu erstellen.",
        ),

        // ── Forschung ─────────────────────────────────────────────────────────────────────────────────────────
        prompt(
            TemplateCategory.RESEARCH, "res_question", "Forschungsfrage schärfen", "Fragestellung, Konstrukte, Hypothesen und Design",
            """
            Thema / Idee: {{Thema}}
            Hintergrund: {{Hintergrund|Was ist bekannt, was interessiert Sie?}}
            Methodische Präferenz: {{Methode|qualitativ, quantitativ, Mixed Methods oder offen}}
            Rahmen: {{Rahmen|optional: Projekt, Feldzugang, Zeit, Stichprobe}}
            """,
            "Hilf, die Idee zu präzisieren: 1. zwei bis drei alternative Forschungsfragen (deskriptiv, explorativ, erklärend) " +
                "mit Einschätzung von Relevanz und Beantwortbarkeit, 2. zentrale Konstrukte mit Arbeitsdefinition und " +
                "möglicher Operationalisierung, 3. bei quantitativer Ausrichtung gerichtete Hypothesen mit unabhängigen und " +
                "abhängigen Variablen, 4. passende Designs (z.B. Querschnitt, Längsschnitt, Intervention, Videostudie, " +
                "Fallstudie) mit Vor- und Nachteilen, 5. Hinweise zu Forschungsethik, Datenschutz und Genehmigungen bei " +
                "Erhebungen an Schulen in Sachsen. " + ACADEMIC + HONEST,
        ),
        prompt(
            TemplateCategory.RESEARCH, "res_items", "Fragebogen-Items", "Items mit Antwortformat für ein oder mehrere Konstrukte",
            """
            Konstrukt(e): {{Konstrukte|z.B. Selbstwirksamkeit von Lehrkräften im inklusiven Unterricht}}
            Zielgruppe: {{Zielgruppe}}
            Items je Konstrukt: {{Anzahl|z.B. 6}}
            Antwortformat: {{Antwortformat|z.B. 5-stufige Likert-Skala}}
            """,
            "Entwickle Items: kurz, eindeutig, eine Aussage je Item, keine doppelte Verneinung, zielgruppengerechte Sprache, " +
                "einige invertierte Items. Tabelle | Nr. | Item | Konstrukt / Facette | invertiert |, darunter die " +
                "Skalenbeschriftung. Weise darauf hin, ob etablierte, validierte Skalen vorzuziehen wären – nenne sie nur, " +
                "wenn du sie sicher kennst, sonst Suchhinweise. Ergänze Hinweise zu Pretest und Reliabilitätsprüfung. " +
                ACADEMIC + HONEST,
        ),
        prompt(
            TemplateCategory.RESEARCH, "res_interview", "Interviewleitfaden", "Leitfaden mit Einstieg, Themenblöcken und Nachfragen",
            """
            Forschungsfrage: {{Forschungsfrage}}
            Interviewte: {{Interviewte|z.B. Seiteneinsteiger:innen, Lehrkräfte, Schulleitungen}}
            Dauer: {{Dauer|z.B. 45 Minuten}}
            Interviewform: {{Form|optional: problemzentriert, Expert:inneninterview, narrativ}}
            """,
            "Erstelle einen Interviewleitfaden: Einleitung (Ablauf, Freiwilligkeit, Aufzeichnung, Datenschutz), " +
                "erzählgenerierende Einstiegsfrage, 3–5 Themenblöcke als Tabelle | Leitfrage | Aufrechterhaltung und " +
                "Nachfragen | inhaltlicher Check |, Abschlussfrage und Dank, kurzer Postskriptum-Bogen. Fragen offen, nicht " +
                "suggestiv, ohne Fachjargon. " + ACADEMIC,
        ),
        prompt(
            TemplateCategory.RESEARCH, "res_coding", "Kategoriensystem", "Für die qualitative Inhaltsanalyse – Material gern anhängen",
            """
            Forschungsfrage: {{Forschungsfrage}}
            Material: {{Material|z.B. Interviewtranskripte, Unterrichtsvideos, Dokumente}}
            Vorgehen: {{Vorgehen|deduktiv, induktiv oder deduktiv-induktiv}}
            Theoretische Grundlage: {{Theorie|optional}}
            """,
            "Entwickle ein Kategoriensystem für eine qualitative Inhaltsanalyse (inhaltlich strukturierend, z.B. nach Kuckartz " +
                "oder Mayring): Tabelle | Hauptkategorie | Subkategorie | Definition | Ankerbeispiel | Kodierregel / " +
                "Abgrenzung |. Ankerbeispiele nur wörtlich aus angehängtem Material, sonst [Ankerbeispiel]. Ergänze Hinweise " +
                "zu Probekodierung, Intercoder-Übereinstimmung, Memos und Umsetzung in MAXQDA. " + ACADEMIC + HONEST,
        ),
        prompt(
            TemplateCategory.RESEARCH, "res_results", "Ergebnisse berichten (APA 7)", "Statistik-Ausgabe als Ergebnisteil formulieren",
            """
            Fragestellung / Hypothese: {{Hypothese}}
            Ausgabe: {{Ausgabe|Output aus SPSS, R oder JASP einfügen oder anhängen}}
            Sprache: {{Sprache|Deutsch oder Englisch}}
            """,
            "Formuliere den Ergebnisteil nach APA 7: deskriptive Statistik, Voraussetzungsprüfung, inferenzstatistische " +
                "Ergebnisse mit Teststatistik, Freiheitsgraden, exaktem p-Wert (bzw. p < .001), Effektstärke und " +
                "Konfidenzintervall, statistische Symbole kursiv. Verwende ausschließlich Werte aus der Ausgabe; fehlende " +
                "Kennwerte als [fehlt] markieren, nicht berechnen oder schätzen. Danach kurz, ob die Hypothese gestützt wird, " +
                "und ggf. eine Tabelle im APA-Format. " + HONEST,
        ),
        prompt(
            TemplateCategory.RESEARCH, "res_abstract", "Abstract", "Strukturiertes Abstract für Tagung oder Zeitschrift",
            """
            Titel: {{Titel}}
            Anlass: {{Anlass|z.B. GEBF-Tagung, DGfE-Kongress, Zeitschrift}}
            Inhalt: {{Inhalt|Hintergrund, Ziel, Methode, Ergebnisse – Stichpunkte oder Text}}
            Wortlimit: {{Wortlimit|z.B. 250}}
            Sprache: {{Sprache|Deutsch, Englisch oder beides}}
            """,
            "Schreibe ein strukturiertes Abstract (Hintergrund – Fragestellung – Methode – Ergebnisse – Diskussion) im " +
                "Wortlimit und nenne am Ende die Wortzahl. Ergebnisse nur so stark formulieren, wie die Daten es tragen. " +
                "Schlage 3–5 Keywords vor. " + ACADEMIC + HONEST,
        ),
        prompt(
            TemplateCategory.RESEARCH, "res_excerpt", "Literatur exzerpieren", "Fragestellung, Methode, Befunde – Text oder PDF anhängen",
            """
            Eigene Fragestellung: {{Fragestellung|wofür ist der Text relevant?}}
            Schwerpunkt: {{Schwerpunkt|optional, z.B. Methode, Befunde, Begriffe}}
            """,
            "Exzerpiere den angehängten Text: bibliografische Angabe (nur aus dem Text), Fragestellung, theoretischer Rahmen " +
                "und zentrale Begriffe, Design, Methode und Stichprobe, Befunde mit Seitenangaben, Limitationen, Relevanz für " +
                "die eigene Fragestellung, zitierfähige Kernaussagen mit Seitenzahl. Trenne klar zwischen Aussagen des Textes " +
                "und eigener Einschätzung. Ist kein Text angehängt, bitte darum. " + ACADEMIC + HONEST,
        ),
        prompt(
            TemplateCategory.RESEARCH, "res_proposal", "Projektskizze / Antrag", "Gliederung und Entwurf für einen Förderantrag",
            """
            Förderprogramm: {{Förderprogramm|z.B. DFG-Sachbeihilfe, BMBF, SMK, DAAD, interne Förderung}}
            Projektidee: {{Idee}}
            Laufzeit und Umfang: {{Laufzeit|z.B. 3 Jahre, eine Stelle E13}}
            Partner: {{Partner|optional, z.B. Schulen, ZLSB}}
            Seitenlimit: {{Seitenlimit|optional}}
            """,
            "Entwirf eine Projektskizze: 1. Ausgangslage und Problemstellung, 2. Forschungsstand und Lücke (Quellen nur, " +
                "wenn sicher, sonst [Quelle]), 3. Ziele und Forschungsfragen, 4. Arbeitsprogramm mit Arbeitspaketen und " +
                "Methoden, 5. Zeitplan als Tabelle (Arbeitspakete × Halbjahre), 6. Transfer und Nachhaltigkeit, 7. " +
                "Forschungsethik und Datenmanagement, 8. Ressourcen. Richte Sprache und Gewichtung am Förderprogramm aus. " +
                ACADEMIC + HONEST,
        ),
        prompt(
            TemplateCategory.RESEARCH, "res_consent", "Einwilligungserklärung", "Information und Einwilligung zur Studienteilnahme (PDF)",
            """
            Studie: {{Studie|Titel und Ziel}}
            Erhebung: {{Erhebung|z.B. Videoaufzeichnung im Unterricht, Interview, Fragebogen}}
            Teilnehmende: {{Teilnehmende|z.B. Schüler:innen (Einwilligung der Sorgeberechtigten), Lehrkräfte}}
            Verantwortlich / Kontakt: {{Kontakt|Name, Professur, E-Mail}}
            Speicherung und Löschung: {{Speicherung|z.B. pseudonymisiert, Löschung nach 10 Jahren}}
            """,
            TUD + "Erstelle eine Information und Einwilligungserklärung zur Studienteilnahme nach DSGVO als Formular: " +
                "„## 1. Information zur Studie“ (Ziel, Ablauf, Dauer, Freiwilligkeit, keine Nachteile bei Nichtteilnahme), " +
                "„## 2. Datenschutz“ (Verantwortliche, Zweck, Rechtsgrundlage Art. 6 Abs. 1 lit. a DSGVO, Speicherung, " +
                "Pseudonymisierung, Weitergabe, Rechte auf Auskunft, Berichtigung, Löschung und Widerruf, Beschwerderecht, " +
                "Datenschutzbeauftragte:r der TU Dresden), „## 3. Einwilligung“ mit „- [ ] …“ je Erhebungsart. " + FIELDS +
                "Weise in deiner Antwort im Chat (nicht im Dokument) darauf hin, die Vorlage vor Verwendung mit dem " +
                "Datenschutz der TU Dresden und ggf. dem Landesamt für Schule und Bildung abzustimmen.",
        ),
        prompt(
            TemplateCategory.RESEARCH, "res_review", "Peer Review", "Gutachten zu einem Manuskript – Datei anhängen",
            """
            Zeitschrift / Tagung: {{Zeitschrift}}
            Art der Einreichung: {{Art|Zeitschriftenartikel, Tagungsbeitrag, Antrag}}
            Kriterien: {{Kriterien|optional, Vorgaben der Herausgeber:innen}}
            Eigene Notizen: {{Notizen|optional}}
            """,
            "Erstelle ein Gutachten zum angehängten Manuskript: Zusammenfassung (2–3 Sätze), Gesamteinschätzung, zentrale " +
                "Anmerkungen (Theorie, Fragestellung, Methode, Ergebnisse, Diskussion), kleinere Anmerkungen mit Seiten- oder " +
                "Zeilenangaben, Empfehlung (accept, minor revision, major revision, reject) mit Begründung – maßgeblich sind " +
                "die Notizen der begutachtenden Person. Konstruktiv, respektvoll, konkret. " + ACADEMIC + HONEST,
        ),

        // ── Kommunikation ─────────────────────────────────────────────────────────────────────────────────────
        prompt(
            TemplateCategory.COMMUNICATION, "mail", "E-Mail schreiben", "Klare, höfliche E-Mail mit Betreff und Signatur",
            """
            An: {{An|Person oder Personenkreis}}
            Anliegen: {{Anliegen|Stichpunkte reichen}}
            Ton: {{Ton|optional, z.B. förmlich, kollegial, kurz}}
            Absender: {{Absender}}
            """,
            "Schreibe eine E-Mail mit Betreffzeile, passender Anrede, Anliegen zuerst, dann Details und die erbetene " +
                "Handlung mit Frist, Grußformel und Signatur aus den Absenderangaben. Höflich, präzise, nicht länger als nötig. " +
                HONEST,
        ),
        prompt(
            TemplateCategory.COMMUNICATION, "mail_student", "Antwort an Studierende", "Anfrage einfügen, Antwort in Stichpunkten",
            """
            Anfrage: {{Anfrage|E-Mail einfügen}}
            Inhalt der Antwort: {{Antwort|Stichpunkte}}
            Absender: {{Absender}}
            """,
            "Beantworte die Anfrage freundlich, klar und verbindlich in Sie-Form: Bezug auf die Anfrage, Antwort bzw. Lösung, " +
                "ggf. Verweis auf OPAL, Sprechzeiten, Prüfungsamt oder Studienfachberatung, Grußformel mit Signatur. Keine " +
                "Zusagen, die nicht in den Stichpunkten stehen. " + HONEST,
        ),
        prompt(
            TemplateCategory.COMMUNICATION, "announcement", "Ankündigung für OPAL oder Rundmail", "Kurz, mit Terminen und klarer Handlungsaufforderung",
            """
            Kanal: {{Kanal|z.B. OPAL-Kurs, Rundmail an Studierende, Institutsverteiler}}
            Lehrveranstaltung / Anlass: {{Anlass}}
            Inhalt: {{Inhalt|Was, wann, wo, bis wann?}}
            Absender: {{Absender|optional}}
            """,
            "Schreibe eine Ankündigung: aussagekräftige Überschrift bzw. Betreff, wichtigste Information im ersten Satz, " +
                "Termine und Fristen fett, klare Handlungsaufforderung, ggf. Kontakt. Für OPAL ohne Anrede, für E-Mails mit " +
                "Anrede und Signatur. " + HONEST,
        ),
        prompt(
            TemplateCategory.COMMUNICATION, "mail_english", "E-Mail auf Englisch", "Für internationale Partner – mit deutscher Kurzfassung",
            """
            An: {{An|Person, Funktion, Einrichtung}}
            Anliegen: {{Anliegen|auf Deutsch oder Englisch, Stichpunkte reichen}}
            Ton: {{Ton|optional, z.B. formal, collegial}}
            Absender: {{Absender}}
            """,
            "Schreibe eine E-Mail auf Englisch (britisches Englisch, akademischer Ton) mit Subject, passender Anrede " +
                "(z.B. „Dear Professor …“), klarem Anliegen und Signatur aus den Absenderangaben. Danach eine kurze deutsche " +
                "Zusammenfassung zur Kontrolle. " + HONEST,
        ),

        // ── Allgemein ─────────────────────────────────────────────────────────────────────────────────────────
        prompt(
            TemplateCategory.GENERAL, "pdf_fill", "PDF-Formular ausfüllen", "Hochgeladenes Formular automatisch ausfüllen – PDF anhängen",
            """
            Angaben: {{Angaben|Was soll eingetragen werden? z.B. Reiseziel, Zeitraum, Kostenstelle}}
            Meine Angaben: {{Meine Angaben|Name, Anschrift, Personalnummer … – aus den Einstellungen}}
            Abflachen: {{Abflachen|optional „ja“, wenn die Felder danach nicht mehr änderbar sein sollen}}
            """,
            "Fülle das angehängte PDF-Formular aus: lies es mit read_pdf_form, ordne die Angaben den Feldern zu und fülle es " +
                "mit fill_pdf (flatten=true nur, wenn „Abflachen: ja“). Datumsangaben im Format TT.MM.JJJJ. Fehlen " +
                "Pflichtangaben, frage zuerst nach. Unterschriftsfelder bleiben leer. Danach eine kurze Tabelle | Feld | " +
                "eingetragen | und eine Liste offener Felder. Ist kein PDF angehängt, bitte darum. " + HONEST,
        ),
        prompt(
            TemplateCategory.GENERAL, "summary", "Zusammenfassen", "Kernaussagen eines Textes oder einer Datei",
            """
            Text: {{Text|einfügen oder Datei anhängen}}
            Länge: {{Länge|z.B. 7 Stichpunkte, eine halbe Seite}}
            Für wen: {{Zielgruppe|optional, z.B. Kolleg:innen, Studierende, Schulleitung}}
            """,
            "Fasse den Text bzw. die angehängte Datei zusammen: zuerst die Kernaussage in einem Satz, dann die wichtigsten " +
                "Punkte in der gewünschten Länge, am Ende offene Fragen oder Handlungsbedarf. Nur Inhalte aus der Vorlage. " + HONEST,
        ),
        prompt(
            TemplateCategory.GENERAL, "edit", "Text überarbeiten", "Korrigieren, kürzen, verständlicher oder wissenschaftlicher machen",
            """
            Text: {{Text}}
            Ziel: {{Ziel|z.B. Rechtschreibung prüfen, verständlicher, kürzer, wissenschaftlicher}}
            """,
            "Überarbeite den Text gemäß dem Ziel. Gib zuerst den überarbeiteten Text aus, danach die wichtigsten Änderungen " +
                "als Stichpunkte. Aussagen nicht verändern, Fachbegriffe beibehalten, geschlechtergerechte Sprache einheitlich.",
        ),
        prompt(
            TemplateCategory.GENERAL, "translate", "Übersetzen", "Fachgerecht, mit Liste der Fachbegriffe",
            """
            Text: {{Text}}
            Zielsprache: {{Zielsprache|z.B. Englisch}}
            Textsorte: {{Textsorte|optional, z.B. Abstract, E-Mail, Folien}}
            """,
            "Übersetze fachgerecht in die Zielsprache. Erziehungswissenschaftliche Fachbegriffe korrekt übertragen (z.B. " +
                "Lehrkräftebildung – teacher education, Unterrichtsqualität – instructional quality); bei mehrdeutigen Begriffen " +
                "die Wahl kurz begründen. Erst die Übersetzung, dann eine kurze Liste der Fachbegriffe.",
        ),
        prompt(
            TemplateCategory.GENERAL, "simple", "Einfache oder Leichte Sprache", "Für Eltern, Schüler:innen oder barrierearme Informationen",
            """
            Text: {{Text}}
            Zielgruppe: {{Zielgruppe|z.B. Eltern, Schüler:innen, Menschen mit Lernschwierigkeiten}}
            Stufe: {{Stufe|Einfache Sprache oder Leichte Sprache}}
            """,
            "Übertrage den Text in die gewünschte Stufe. Einfache Sprache: kurze Sätze, bekannte Wörter, Fachwörter erklärt, " +
                "aktive Formulierungen. Leichte Sprache: ein Gedanke pro Satz, keine Nebensätze, Mediopunkt bei langen " +
                "Wörtern, Beispiele. Inhalt vollständig und korrekt erhalten.",
        ),
        prompt(
            TemplateCategory.GENERAL, "slides", "Präsentation gliedern", "Folien für die TUD-Präsentationsvorlage mit Sprechernotizen",
            """
            Thema: {{Thema}}
            Anlass und Publikum: {{Anlass|z.B. Vorlesung, Tagungsvortrag, Elternabend}}
            Dauer: {{Dauer|z.B. 20 Minuten}}
            Inhalte: {{Inhalte|Stichpunkte oder Datei anhängen}}
            """,
            "Gliedere eine Präsentation für die TUD-Präsentationsvorlage (16:9, Noto Sans; Folien: Titel, Agenda, " +
                "Kapiteltrenner, Inhalt, Zitat, Abschluss mit Kontakt). Je Folie: Folientyp, Titel mit höchstens 8 Wörtern, " +
                "3–5 Stichpunkte, Vorschlag für Abbildung oder Icon, Sprechernotiz (2–3 Sätze). Etwa eine Folie pro 1–2 " +
                "Minuten. Biete an, die Gliederung als Word-Datei zu erstellen. " + HONEST,
        ),
        prompt(
            TemplateCategory.GENERAL, "explain", "Einfach erklären", "Wie für Einsteiger, mit Beispiel",
            "Erkläre mir: {{Thema}}",
            "Erkläre Schritt für Schritt und so einfach wie möglich, mit einem Beispiel aus Schule oder Hochschule, und " +
                "prüfe am Ende mit einer kurzen Frage das Verständnis.",
        ),
        prompt(
            TemplateCategory.GENERAL, "proscons", "Pro & Contra", "Abwägung mit Empfehlung",
            """
            Frage / Entscheidung: {{Frage}}
            Kontext: {{Kontext|optional}}
            """,
            "Stelle Vor- und Nachteile in einer Tabelle gegenüber, gewichte sie und gib eine begründete Empfehlung; nenne " +
                "getroffene Annahmen ausdrücklich.",
        ),

        // ── Personas ──────────────────────────────────────────────────────────────────────────────────────────
        persona(
            "didactics", "Didaktik-Beratung", "Plant Unterricht und Lehre theoriegeleitet mit",
            "Du bist eine erfahrene Beraterin für Allgemeine Didaktik und Hochschuldidaktik. Du unterstützt Lehrende bei der " +
                "Planung von Unterricht und Lehrveranstaltungen: Lernziele, Constructive Alignment, kognitive Aktivierung, " +
                "Methoden und Sozialformen, Differenzierung, Inklusion, digitale Medien und Prüfen. Du argumentierst " +
                "theoriegeleitet und empirisch fundiert, nennst Quellen nur, wenn du sie sicher kennst, und machst konkrete, " +
                "umsetzbare Vorschläge. Frage nach, wenn Zielgruppe, Rahmen oder Ziel unklar sind.",
        ),
        persona(
            "methods", "Methoden- & Statistikberatung", "Quantitative und qualitative Bildungsforschung",
            "Du bist Methodenberaterin für empirische Bildungsforschung – quantitativ (Design, Stichprobenplanung, " +
                "Skalenbildung, Regression, Mehrebenenmodelle, Strukturgleichungsmodelle), qualitativ (Interviews, " +
                "Videografie, qualitative Inhaltsanalyse, Dokumentarische Methode) und Mixed Methods. Erkläre Verfahren " +
                "verständlich, prüfe Voraussetzungen, schlage passende Analysen vor und zeige bei Bedarf R- oder SPSS-Syntax. " +
                "Rechne nie mit erfundenen Daten und weise auf Grenzen und Alternativen hin.",
        ),
        persona(
            "translator", "Übersetzer:in Wissenschaft", "Übersetzt jeden Text Deutsch ↔ Englisch, fachgerecht",
            "Du bist professionelle Fachübersetzerin für Erziehungswissenschaft und Bildungsforschung. Übersetze jeden Text " +
                "des Nutzers: deutsche Texte ins Englische, alle anderen ins Deutsche – außer der Nutzer nennt eine andere " +
                "Zielsprache. Behalte Ton, Formatierung, Zitationen und Fachbegriffe bei (z.B. Lehrkräftebildung – teacher " +
                "education). Gib nur die Übersetzung aus; nur bei mehrdeutigen Fachbegriffen eine kurze Anmerkung darunter.",
        ),
        persona(
            "editor", "Lektorat", "Korrigiert Rechtschreibung, Grammatik und Stil wissenschaftlicher Texte",
            "Du bist ein sorgfältiges wissenschaftliches Lektorat. Korrigiere Rechtschreibung, Grammatik und Zeichensetzung " +
                "nach der amtlichen Rechtschreibung, verbessere behutsam Stil, Verständlichkeit und Kohärenz, achte auf " +
                "einheitliche, geschlechtergerechte Sprache und auf die Zitierweise nach APA 7. Gib zuerst den korrigierten " +
                "Text aus, danach die wichtigsten Änderungen als Stichpunkte. Verändere keine inhaltlichen Aussagen.",
        ),
        persona(
            "reviewer", "Kritische:r Gutachter:in", "Prüft Texte auf Schwachstellen – wie ein strenges Review",
            "Du bist eine kritische, faire Gutachterin für erziehungswissenschaftliche Texte. Prüfe Argumentation, " +
                "Theoriebezug, Fragestellung, Methode, Belege und Schlussfolgerungen auf Schwachstellen, Lücken und " +
                "alternative Erklärungen. Gliedere in „Grundsätzliche Einwände“, „Weitere Anmerkungen“ und „Was überzeugt“. " +
                "Sei konkret, nenne Textstellen und schlage Verbesserungen vor.",
        ),
        persona(
            "coach", "Lerncoach für Studierende", "Führt mit Fragen zum eigenen Verständnis statt mit fertigen Lösungen",
            "Du bist Lerncoach für Lehramtsstudierende. Gib keine fertigen Lösungen, sondern führe mit gezielten Fragen, " +
                "Hinweisen und Beispielen zum eigenen Verständnis. Prüfe zwischendurch das Verständnis und fasse am Ende " +
                "zusammen, was erarbeitet wurde.",
        ),
        persona(
            "tutor", "Erklärer", "Erklärt Themen einfach und Schritt für Schritt",
            "Du bist ein geduldiger Tutor. Erkläre Themen verständlich und Schritt für Schritt, mit Beispielen aus dem " +
                "Alltag. Prüfe am Ende mit einer kurzen Frage, ob alles verstanden wurde.",
        ),
        persona(
            "code", "Code-Reviewer", "Prüft Code auf Fehler, Sicherheit und Lesbarkeit",
            "Du bist ein erfahrener Senior-Softwareentwickler und führst Code-Reviews durch. Prüfe den Code auf Fehler, " +
                "Randfälle, Sicherheitsprobleme, Performance und Lesbarkeit. Gliedere deine Antwort in „Kritisch“, " +
                "„Verbesserungen“ und „Kleinigkeiten“, nenne jeweils die Stelle und zeige konkrete Korrekturen als Codeblock. " +
                "Lobe kurz, was gut gelöst ist.",
        ),
    )

    /** Start screen of a new chat while the user has no favourites. */
    private val SUGGESTED = listOf("tud_letter", "mail", "summary", "teach_session", "teach_feedback", "res_abstract").map { BUILT_IN + it }

    fun all(own: List<Template>): List<Template> = builtIn + own

    fun find(id: String, own: List<Template>): Template? = own.firstOrNull { it.id == id } ?: builtIn.firstOrNull { it.id == id }

    /** Favourites in the order they were marked, otherwise a few common templates. */
    fun suggestions(favorites: List<String>, own: List<Template>): List<Template> =
        favorites.mapNotNull { find(it, own) }.ifEmpty { SUGGESTED.mapNotNull { find(it, own) } }.take(8)

    /** Copy of a built-in template that the user can change. */
    fun copyOf(t: Template): Template = t.copy(id = UUID.randomUUID().toString(), name = t.name + " (angepasst)")

    /**
     * Until version 2 the default templates were copied into templates.json. Copies the user did not change
     * (recognized by the hash of their text) are removed; the new built-in versions replace them.
     */
    fun removeLegacyDefaults(store: ListStore<Template>) {
        store.current.filter { it.id.startsWith("tpl_") && it.content.hashCode() in LEGACY }.forEach { store.delete(it.id) }
    }

    private val LEGACY = setOf(
        1241340825, -421716484, 1253114753, 453414931, 430831514, -1689628843, -498660521, 591226954,
    )

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    /** Text with [list] for colleagues (mail, chat); [import] reads it back. */
    fun export(list: List<Template>): String =
        "ADEU-LLM-Vorlagen – in der App unter Vorlagen › Importieren einfügen:\n" +
            json.encodeToString(ListSerializer(Template.serializer()), list.map { it.copy(id = "") })

    /** Templates from text made by [export] (or a single template as JSON); they get new ids. */
    fun import(text: String): List<Template> {
        val start = text.indexOfFirst { it == '[' || it == '{' }
        if (start < 0) return emptyList()
        val body = text.substring(start).trim()
        val list = runCatching {
            if (body.startsWith("[")) json.decodeFromString(ListSerializer(Template.serializer()), body)
            else listOf(json.decodeFromString(Template.serializer(), body))
        }.getOrDefault(emptyList())
        return list.filter { it.name.isNotBlank() && it.content.isNotBlank() }.map { it.copy(id = UUID.randomUUID().toString()) }
    }
}
