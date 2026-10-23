package io.github.thepytales.llmhub.docs

import android.util.Base64
import io.github.thepytales.llmhub.data.Template
import io.github.thepytales.llmhub.data.TemplateKind

/**
 * Corporate design of TU Dresden for generated documents: the blue, both logo versions (blue for white
 * paper, white for blue areas) and ready-made form templates. Built in, so they work offline and need no setup.
 */
object TudBrand {
    /** TUD blue of the logo. */
    const val BLUE = "#00008C"
    const val LOGO_NAME = "TU Dresden"
    const val LOGO_WHITE_NAME = "TU Dresden weiß"
    /** Return line above the address field of letters. */
    const val POSTAL = "Technische Universität Dresden · 01062 Dresden"

    val logoBlue: ByteArray by lazy { Base64.decode(LOGO_BLUE_PNG, Base64.DEFAULT) }
    val logoWhite: ByteArray by lazy { Base64.decode(LOGO_WHITE_PNG, Base64.DEFAULT) }

    /** The built-in logo for a name like "tud", "TU Dresden" or "TU Dresden weiß"; null for other names. */
    fun logo(name: String?): ByteArray? {
        val n = name?.trim()?.lowercase()?.removePrefix("logo:")?.trim()?.replace("-", " ")?.replace("_", " ") ?: return null
        val white = listOf("weiß", "weiss", "white", "negativ", "invers").any { it in n }
        val base = n.replace(Regex("weiß|weiss|white|negativ|invers"), "").trim()
        if (base !in setOf("tud", "tu dresden", "technische universität dresden", "tu")) return null
        return if (white) logoWhite else logoBlue
    }

    /** Applies the design: TUD blue as accent and the blue logo unless another logo was chosen. */
    fun apply(theme: DocTheme): DocTheme =
        if (theme.style != DocStyle.TUD) theme else theme.copy(accent = BLUE, logo = theme.logo ?: logoBlue)

    private fun form(id: String, name: String, description: String, content: String) =
        Template(id = "tud_$id", name = name, kind = TemplateKind.PROMPT, description = description, content = content)

    private const val MAKE = "Erstelle mit create_document ein PDF im Corporate Design der TU Dresden (style=tud). "

    /** Form templates shown at the top of the template picker; they fill the input field. */
    val templates: List<Template> = listOf(
        form(
            "letter", "Brief", "Geschäftsbrief mit Absender, Anschriftfeld und Betreff",
            MAKE + "Layout Brief: Absender in letter_sender, Anschrift in letter_recipient, Betreff als title, " +
                "Brieftext als content (Anrede, Text, Grußformel, Name).\n\n" +
                "Absender (Struktureinheit, Name, Telefon, E-Mail): \nEmpfänger (Name, Anschrift): \nBetreff: \n" +
                "Inhalt (Stichpunkte reichen): \n",
        ),
        form(
            "minutes", "Protokoll", "Sitzungs- oder Besprechungsprotokoll mit TOPs, Beschlüssen und Aufgaben",
            MAKE + "Aufbau: Tabelle mit Datum, Zeit, Ort, Sitzungsleitung, Protokoll, Teilnehmende und Abwesende; " +
                "danach je Tagesordnungspunkt eine Überschrift „TOP n: …“ mit Diskussion und Ergebnis, Beschlüsse als " +
                "Hinweisbox (> [!IMPORTANT]), am Ende eine Aufgabentabelle (Was | Wer | Bis wann).\n\n" +
                "Gremium/Anlass: \nDatum, Zeit, Ort: \nTeilnehmende: \nTagesordnung und Notizen: \n",
        ),
        form(
            "memo", "Aktennotiz", "Kurze Gesprächs- oder Telefonnotiz",
            MAKE + "Aufbau: Tabelle mit Datum, Verfasser:in, Beteiligte, Anlass und Aktenzeichen; danach Abschnitte " +
                "„Sachverhalt“, „Ergebnis“ und „Weiteres Vorgehen“. Knapp und sachlich.\n\n" +
                "Anlass/Gespräch mit: \nDatum: \nWas wurde besprochen: \n",
        ),
        form(
            "form", "Formular zum Ausfüllen", "Antrag, Anmeldung oder Abfrage mit Feldern und Ankreuzkästchen",
            MAKE + "Gestalte ein Formular zum Ausdrucken und Ausfüllen: Abschnitte als Überschriften (## 1. …), " +
                "Eingabefelder als zweispaltige Tabelle mit Bezeichnung links und leerer Zelle rechts, Auswahlmöglichkeiten " +
                "als Liste mit „- [ ] Option“, Freitextfelder als Tabelle mit einer leeren Zelle, am Ende Ort/Datum und " +
                "Unterschrift als Tabelle. Kurze Ausfüllhinweise als > [!NOTE].\n\n" +
                "Art des Formulars (z.B. Antrag auf …, Anmeldung zu …): \nBenötigte Angaben: \nEinreichen bei: \n",
        ),
        form(
            "info", "Merkblatt", "Informationsblatt oder Aushang mit Hinweisen",
            MAKE + "Aufbau: kurzer Einleitungssatz, Abschnitte mit Überschriften und Stichpunkten, wichtige Fristen " +
                "als > [!WARNING], Kontakt am Ende als Tabelle. Höchstens zwei Seiten.\n\n" +
                "Thema: \nZielgruppe: \nWichtige Inhalte, Fristen, Kontakt: \n",
        ),
        form(
            "report", "Bericht", "Projekt-, Tätigkeits- oder Abschlussbericht",
            MAKE + "Aufbau: Zusammenfassung als > [!NOTE], dann Ausgangslage, Vorgehen, Ergebnisse (gern mit Tabellen), " +
                "Fazit und Ausblick.\n\nProjekt/Thema: \nZeitraum: \nInhalte und Ergebnisse: \n",
        ),
        form(
            "agenda", "Einladung mit Tagesordnung", "Einladung zu Sitzung, Workshop oder Veranstaltung",
            MAKE + "Aufbau: kurzer Einladungstext, Tabelle mit Datum, Uhrzeit, Ort bzw. Link, danach die Tagesordnung " +
                "als Tabelle (TOP | Thema | Verantwortlich | Zeit) und Hinweise zur Anmeldung.\n\n" +
                "Anlass: \nDatum, Uhrzeit, Ort: \nTagesordnungspunkte: \nAnmeldung bis/bei: \n",
        ),
    )

    private const val LOGO_BLUE_PNG =
        "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"

    private const val LOGO_WHITE_PNG =
        "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" +
        "Yxvb2E7c/gCe2//cD+LsbAAAAABJRU5ErkJggg=="
}
