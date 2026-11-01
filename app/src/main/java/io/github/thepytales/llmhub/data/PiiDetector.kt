package io.github.thepytales.llmhub.data
import io.github.thepytales.llmhub.ui.trLater

/** Kind of a personal detail; [label] is the word in the placeholder, e.g. „[Name 3]“. */
enum class PiiKind(val label: String, val title: String) {
    NAME("Name", trLater("Namen")),
    TERM("Begriff", trLater("eigene Begriffe")),
    EMAIL("E-Mail", "E-Mail-Adressen"),
    PHONE("Telefon", trLater("Telefonnummern")),
    MATRIKEL("Matrikelnr.", trLater("Matrikelnummern")),
    IBAN("IBAN", trLater("Kontonummern")),
    BIRTHDATE("Geburtsdatum", trLater("Geburtsdaten")),
    ADDRESS("Adresse", trLater("Anschriften")),
    POSTCODE("Ort", trLater("Postleitzahl und Ort")),
    ID("Kennung", trLater("Ausweis-, Steuer- und andere Nummern")),
    BIC("BIC", "BIC"),
    CARD("Karte", trLater("Kreditkartennummern")),
    PLATE("Kennzeichen", trLater("Kfz-Kennzeichen")),
    LINK("Link", trLater("Profil-Links und Webadressen mit Namen")),
    IP("IP-Adresse", "IP-Adressen"),
    HANDLE("Konto", trLater("@-Namen in sozialen Netzwerken")),
    STUDENT("Schüler/in", trLater("Schüler/innen")),
    UNI_STUDENT("Studierende/r", trLater("Studierende")),
    TEACHER("Lehrkraft", trLater("Lehrkräfte")),
    COLLEAGUE("Kollege/in", "Kolleg/innen"),
    PARENT("Elternteil", trLater("Eltern")),
    /** Schools, day-care centres and similar places that identify a person in research data (AP-03). */
    SCHOOL("Einrichtung", trLater("Schulen und Einrichtungen")),
    /** A town in a description of a person ("die Schulleiterin in Pirna"). */
    PLACE("Ortsangabe", trLater("Orte in Personenbeschreibungen")),
    ;

    /** Kinds that stand for a person (a name with or without a role). */
    val person: Boolean get() = this == NAME || this in ROLES

    companion object {
        val ROLES = setOf(STUDENT, UNI_STUDENT, TEACHER, COLLEAGUE, PARENT)
    }
}

/**
 * What the pseudonymization replaces, each with its own switch under Einstellungen › Datenschutz (all on by
 * default). [group] orders the switches in the settings.
 */
enum class PiiFeature(val title: String, val detail: String, val group: String) {
    EMAIL("E-Mail-Adressen", "anna.mueller@tu-dresden.de", "Kontaktdaten"),
    PHONE(trLater("Telefonnummern"), "0351 463-12345, +49 …", "Kontaktdaten"),
    ADDRESS(trLater("Anschriften"), trLater("Straße mit Hausnummer, Postleitzahl mit Ort"), "Kontaktdaten"),
    WEB(trLater("Profil-Links, IP-Adressen, @-Namen"), trLater("xing.com/profile/…, linkedin.com/in/…, 192.168.0.12, @anna_m"), "Kontaktdaten"),
    BIRTHDATE(trLater("Geburtsdaten"), trLater("nach „geb.“, „Geburtsdatum“ oder „*“, auch mit Monatsnamen"), "Nummern und Daten"),
    MATRIKEL(trLater("Matrikel-, Schüler- und Personalnummern"), trLater("Matrikelnummer, Schüler-Nr., Personalnummer, Kundennummer, Aktenzeichen …"), "Nummern und Daten"),
    BANK(trLater("Bankdaten"), trLater("IBAN, BIC und Kreditkartennummern (mit Prüfziffer)"), "Nummern und Daten"),
    IDS(trLater("Ausweis-, Steuer- und Sozialversicherungsnummern"), trLater("Steuer-ID, Steuernummer, Personalausweis, Reisepass, SV-Nummer"), "Nummern und Daten"),
    PLATE(trLater("Kfz-Kennzeichen"), "DD-AB 1234", "Nummern und Daten"),
    ROLES(trLater("Namen nach Rollen, Gruß und Unterschrift"), trLater("Schülerin Jolina, Kollege Brandt, „Liebe Anna,“, Grußformel mit Namen, Von:/An: in E-Mails"), "Namen"),
    EMAIL_NAMES(trLater("Namen aus E-Mail-Adressen"), trLater("steht anna.mueller@… im Text, gelten auch „Anna“ und „Müller“ als Namen"), "Namen"),
    TABLES(trLater("Spalten in Tabellen"), trLater("ganze Spalten wie Name, Vorname, Schüler/in, Geburtsdatum, Anschrift in Tabellen, CSV und Excel"), "Namen"),
    INSTITUTIONS(
        trLater("Schulen, Einrichtungen und Orte"),
        trLater("„Grundschule am Park“, „Goethe-Gymnasium“, „Kita Sonnenschein“ und der Ort in „die Schulleiterin in Pirna“"), "Namen",
    ),
    INFLECTION(trLater("Gebeugte Namen"), trLater("Annas, Müllers, Schmidt'sche werden mit ersetzt"), "Namen"),
    VARIANTS(trLater("Andere Schreibweisen"), trLater("MÜLLER, müller, Mueller zu „Müller“ aus der eigenen Liste; Mueller auch bei erkannten Namen"), "Namen"),
    FILE_NAMES(trLater("Datei- und Ordnernamen"), trLater("Elterngespräch_Mueller.docx – auch mit Unterstrich"), "Namen"),
    ROLE_PLACEHOLDERS("Rollen-Platzhalter", trLater("[Schüler/in 2] statt [Name 2], wenn die Rolle erkennbar ist – das Modell antwortet passender"), "Platzhalter"),
    PER_CHAT(trLater("Platzhalter je Chat"), trLater("jeder Chat beginnt bei [Name 1]; der Server kann Personen nicht über Chats hinweg verbinden"), "Platzhalter"),
    EXTERNAL_TOOLS(
        trLater("Platzhalter bei Suchen im Netz"),
        trLater("Websuche, Seitenabrufe, Literatursuche, Zotero und GitHub bekommen die Platzhalter statt der echten Angaben. Wer nach " +
            "öffentlichen Personen suchen will, schaltet das aus."),
        "Platzhalter",
    ),
    KNOWLEDGE(trLater("Wissensspeicher pseudonymisiert"), trLater("Texte für die semantische Suche (Embeddings, Reranking) gehen nur mit Platzhaltern an den Server"), "Platzhalter"),
    IMAGE_WARNING(trLater("Warnung bei Bildern"), trLater("fragt nach, bevor Bilder oder gescannte Seiten gesendet werden – darin lässt sich nichts ersetzen"), "Hinweise"),
    SUGGESTIONS(trLater("Namensvorschläge beim Schreiben"), trLater("zeigt unter der Eingabe Wörter, die Namen sein könnten, zum Aufnehmen in die eigene Liste"), "Hinweise"),
}

/**
 * A personal detail found in a text: [value] is exactly the text that gets replaced. [canonical] is set for another
 * spelling of an entry ("MÜLLER" for "Müller"): it gets the same placeholder and the answer gets [canonical] back.
 */
data class PiiHit(val value: String, val kind: PiiKind, val canonical: String? = null)

/**
 * Finds personal details in a text, only with fixed rules on the device (no model involved): e-mail addresses,
 * phone numbers, matriculation and other numbers, IBANs, birth dates, addresses, profile links, names after a form of
 * address, a role, a greeting or in a signature, frequent first names (with the surname after them), names in table
 * columns and every name or term of the user's own list. Names without such a clue are not found – that is what
 * the own list is for; [suspects] proposes candidates for it.
 */
object PiiDetector {
    private const val UPPER = "A-ZÄÖÜ"
    private const val LOWER = "a-zäöüßéèáàíóúçñğşıćčšžłńóśźżăâîșțëïø"
    private const val WORD = "[$UPPER][$LOWER]+(?:-[$UPPER][$LOWER]+)?"
    /** Characters that make a word go on (the boundaries of a replaced detail). */
    const val LETTERS = "\\wäöüÄÖÜßéèáàíóúçñ"

    /** Endings of inflected names: Annas, Müllers, Lukas', Schmidt'sche. */
    const val SUFFIX = "(?:'s|’s|'sche[nmrs]?|’sche[nmrs]?|sche[nmrs]?|s|'|’)"

    private val EMAIL = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")
    private val PHONE = Regex("(?<![\\w+/.,])(?:\\+\\d{1,3}[ /-]?(?:\\(0\\)[ ]?)?|0)\\(?\\d{2,5}\\)?(?:[ /-]?\\d{2,}){1,4}(?![\\w.,]\\d)")
    private val MATRIKEL = Regex("(?i)(?:matrikel(?:nummer|-?nr\\.?)?|matr\\.?-?nr\\.?|mat\\.?-?nr\\.?|immatrikulationsnummer)\\s*[:.]?\\s*(\\d{5,9})\\b")
    private val OTHER_NUMBER = Regex(
        "\\b(?i:schüler|schueler|personal|kunden|mitglieds|versicherten|patienten|akten|bewerber|bewerbungs|prüfungs|pruefungs|" +
            "teilnehmer|antrags|vorgangs|fall|klienten|mandanten|beschäftigten|mitarbeiter)" +
            "(?i:-?nummer|-?nr\\.?|-?id|-?kennung|zeichen)\\s*[:.]?\\s*([A-Z0-9][A-Za-z0-9/.-]{2,19}[A-Za-z0-9])"
    )
    private val IBAN = Regex("\\b[A-Z]{2}\\d{2}(?: ?[0-9A-Z]{4}){3,7}(?: ?[0-9A-Z]{1,3})?\\b")
    private val BIC = Regex("(?i:bic|swift(?:-?code)?)\\s*[:.]?\\s*([A-Z]{4}[A-Z]{2}[A-Z0-9]{2}(?:[A-Z0-9]{3})?)\\b")
    private val CARD = Regex("(?<![\\d-])[3-6]\\d{3}(?:[ -]?\\d{3,4}){2,4}(?![\\d-])")
    private const val MONTHS = "Januar|Jänner|Februar|März|Maerz|April|Mai|Juni|Juli|August|September|Oktober|November|Dezember|Jan\\.|Feb\\.|Mär\\.|Apr\\.|Aug\\.|Sept?\\.|Okt\\.|Nov\\.|Dez\\."
    private const val DATE = "\\d{1,2}\\.\\s?(?:\\d{1,2}\\.\\s?\\d{2,4}|(?:$MONTHS)\\s*\\d{4})|\\d{4}-\\d{2}-\\d{2}"
    private val BIRTHDATE = Regex(
        "(?:(?i:geb(?:oren|\\.)?(?:\\s+am)?|geburtsdatum|geburtstag|date of birth|born(?: on)?)|(?<=\\S[ \\t]?)\\*)\\s*[:.]?\\s*($DATE)"
    )
    private val ANY_DATE = Regex("(?<![\\d.])(?:$DATE)(?![\\d])")
    private val STREET = Regex(
        "\\b(?:[$UPPER][$LOWER-]*(?:straße|strasse|str\\.|weg|gasse|allee|platz|ring|damm|ufer|chaussee|steig|pfad)" +
            "|[$UPPER][$LOWER]+(?:er|e|es)? (?:Straße|Str\\.|Weg|Gasse|Allee|Platz|Ring|Damm|Ufer|Chaussee|Markt))" +
            "\\s+\\d{1,4}(?:\\s?[a-z](?![$LOWER]))?\\b"
    )
    /** Postal code and town where an address goes on: line start, after a comma, "in" or "D-". */
    private val POSTCODE = Regex(
        "(?m)(?:^[ \\t]*|(?<=[,;][ \\t]?)|(?<=[,;][ \\t]{2})|(?<=\\bin )|(?<=\\bD-)|(?<=\\bDE-))" +
            "((?:0[1-9]|[1-9]\\d)\\d{3})[ \\t]+([$UPPER][$LOWER]+(?:(?:[ -](?:an der |am |im |bei |in |ob der |a\\. ?d\\. ?)?)[$UPPER][$LOWER]+)?)"
    )
    private val STEUER_ID = Regex(
        "(?i:steuer-?id(?:entifikationsnummer)?|steuerliche\\s+identifikationsnummer|identifikationsnummer|idnr\\.?)\\s*[:.]?\\s*" +
            "(\\d{2}\\s?\\d{3}\\s?\\d{3}\\s?\\d{3})\\b"
    )
    private val STEUER_NR = Regex("(?i:steuer-?(?:nummer|nr\\.?))\\s*[:.]?\\s*(\\d{2,3}/\\d{3,4}/\\d{4,5}|\\d{10,13})\\b")
    private val SOCIAL = Regex("\\b(\\d{2}\\s?[0-3]\\d[01]\\d\\d{2}\\s?[A-Z]\\s?\\d{3})\\b")
    private val SOCIAL_KEY = Regex("(?i:sozialversicherungs-?(?:nummer|nr\\.?)|sv-?(?:nummer|nr\\.?)|rentenversicherungs-?(?:nummer|nr\\.?))\\s*[:.]?\\s*([0-9A-Z][0-9A-Z ]{9,14}[0-9])")
    private val PASSPORT = Regex("\\b(?i:personalausweis|ausweis|reisepass|pass)(?i:-?nummer|-?nr\\.?)?\\s*[:.]?\\s*([CFGHJKLMNPRTVWXYZ0-9]{9,10})\\b")
    private val PLATE = Regex("(?<![\\w-])([A-ZÄÖÜ]{1,3})-([A-Z]{1,2}) ?(\\d{1,4})([EH])?(?![\\w-])")
    private val NOT_PLATES = setOf("TV", "DIN", "ISO", "EN", "IEC", "VDE", "VDI", "EU", "UN", "IT", "KW", "NR", "ABS", "ART", "COVID", "SARS", "MP", "PS", "ICE", "IC", "RE", "RB")
    private val PROFILE = Regex(
        "(?i)\\b(?:https?://)?(?:[a-z]{2,3}\\.)?(?:xing\\.com/profile|linkedin\\.com/in|facebook\\.com|fb\\.com|instagram\\.com|twitter\\.com|x\\.com|" +
            "tiktok\\.com|threads\\.net|bsky\\.app/profile|researchgate\\.net/profile|youtube\\.com/@|t\\.me)/[^\\s)>\\]\"'„“]+"
    )
    private val URL = Regex("(?i)\\bhttps?://[^\\s)>\\]\"'„“]+")
    private val IP = Regex("(?<![\\d.])((?:25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)\\.(?:25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)\\.(?:25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)\\.(?:25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d))(?![\\d]|\\.\\d)")
    private val HANDLE = Regex("(?<![\\w@./])@([a-z0-9_][A-Za-z0-9_.]{1,28}[A-Za-z0-9_])(?![\\w(@])")
    /** "@param", "@media" … in code and documentation are no accounts. */
    private val CODE_AT = setOf(
        "param", "return", "returns", "throws", "throw", "see", "since", "author", "deprecated", "link", "inheritdoc", "property", "sample",
        "constructor", "suppress", "file", "brief", "media", "import", "keyframes", "apply", "tailwind", "layer", "mixin", "include", "extend",
        "use", "forward", "each", "if", "else", "for", "while", "function", "supports", "page", "charset", "namespace", "container", "override",
        "type", "typedef", "example", "version", "todo", "fixme", "click", "submit", "input", "change", "bind", "test", "before", "after",
        "here", "all", "channel", "everyone", "echo", "font", "layout", "tag", "name", "value", "class", "id", "jvmstatic", "jvmfield",
        "android", "material", "string", "drawable", "dimen", "color", "style", "app", "tools", "context", "pytest", "staticmethod", "classmethod",
        "dataclass", "property", "abstractmethod", "types", "babel", "angular", "vue", "nuxt", "testing-library", "mui", "emotion", "next",
    )

    /** Form of address with academic titles, then initials and up to three words of the name. */
    private val ADDRESSED = Regex(
        "\\b(?:Frau|Herrn?|Hr\\.|Fr\\.|Prof\\.|Dr\\.|Professorin|Professor)\\s+" +
            "(?:(?:Prof\\.|Dr\\.|Dipl\\.-[A-Za-z]+\\.?|med\\.|phil\\.|rer\\.\\s?nat\\.|paed\\.|habil\\.|PD|apl\\.|h\\.\\s?c\\.|mult\\.)\\s*)*" +
            "((?:[$UPPER]\\.\\s?){0,2}$WORD(?:\\s+(?:von|van|de|zu|vom)\\s+$WORD|\\s+$WORD){0,2})"
    )
    private val NAME_WORD = Regex("(?<![\\wäöüÄÖÜß-])$WORD(?![\\wäöüÄÖÜß-])")
    private val PARTICLES = setOf("von", "van", "de", "zu", "vom")

    /** Roles before a name; true: the word is only singular, so any name-like word after it counts. */
    private val ROLE_WORDS: Map<String, Pair<PiiKind, Boolean>> = buildMap {
        listOf("Schülerin", "Mitschülerin", "Grundschülerin", "Gymnasiastin", "Oberschülerin").forEach { put(it, PiiKind.STUDENT to true) }
        listOf("Schüler", "Mitschüler", "Grundschüler", "Gymnasiast", "Oberschüler").forEach { put(it, PiiKind.STUDENT to false) }
        listOf("Studentin", "Student", "Doktorandin", "Doktorand", "Kommilitonin", "Kommilitone", "Masterandin", "Masterand", "Hilfskraft")
            .forEach { put(it, PiiKind.UNI_STUDENT to true) }
        listOf("Lehrerin", "Klassenlehrerin", "Referendarin", "Referendar", "Lehramtsanwärterin", "Lehramtsanwärter", "Lehrkraft", "Fachlehrerin", "Schulleiterin")
            .forEach { put(it, PiiKind.TEACHER to true) }
        listOf("Lehrer", "Klassenlehrer", "Fachlehrer", "Schulleiter").forEach { put(it, PiiKind.TEACHER to false) }
        listOf("Kollegin", "Kollege").forEach { put(it, PiiKind.COLLEAGUE to true) }
        listOf("Mutter", "Vater", "Elternteil", "Erziehungsberechtigte", "Erziehungsberechtigter").forEach { put(it, PiiKind.PARENT to true) }
        listOf(
            "Tochter", "Sohn", "Kind", "Praktikantin", "Praktikant", "Auszubildende", "Azubi", "Tutorin", "Tutor", "Mentorin", "Mentor",
            "Erzieherin", "Betreuerin", "Teilnehmerin", "Bewerberin", "Patientin", "Patient", "Klientin", "Klient", "Mitarbeiterin",
            "Sekretärin", "Schwester", "Bruder", "Oma", "Opa", "Tante", "Onkel", "Nichte", "Neffe", "Cousine", "Cousin", "Freundin", "Freund",
        ).forEach { put(it, PiiKind.NAME to true) }
        listOf("Erzieher", "Betreuer", "Teilnehmer", "Bewerber", "Mitarbeiter", "Sekretär").forEach { put(it, PiiKind.NAME to false) }
    }
    private val ROLE = Regex("\\b(${ROLE_WORDS.keys.sortedByDescending { it.length }.joinToString("|")})\\s+((?:[$UPPER]\\.\\s?)?$WORD(?:\\s+$WORD)?)")
    private val GREETING = Regex(
        "(?m)(?:^|(?<=[.!?:]\\s))[ \\t]*(?:Liebe[rs]?|Hallo|Hi|Hey|Moin|Servus|Salut|Ciao|Dear|Hello|Guten (?:Morgen|Tag|Abend))[ \\t]+" +
            "($WORD)(?:[ \\t]+($WORD))?[ \\t]*[,!]"
    )
    private val CLOSING = Regex(
        "(?im)^[ \\t]*(?:(?:mit|viele|beste|liebe|herzliche|freundliche|schöne|sonnige|kollegiale)\\s+)?" +
            "(?:(?:freundlichen|besten|herzlichen|lieben|vielen|schönen|sonnigen|kollegialen)\\s+)?(?:grüßen|grüße|grüssen|grüsse|gruß|gruss)" +
            "(?:\\s+(?:aus|von)\\s+\\S+)?[ \\t]*[,!.]?[ \\t]*$|^[ \\t]*(?:mfg|lg|vg|bg|best regards|kind regards|regards|best|cheers|herzlichst|beste wünsche)[ \\t]*[,!.]?[ \\t]*$"
    )
    private val SIGNED = Regex("(?m)^[ \\t]*(?:Dein|Deine|Ihr|Ihre|Euer|Eure)[ \\t]+($WORD(?:[ \\t]+$WORD)?)[ \\t]*$")
    private val SIGNATURE_LINE = Regex(
        "^[ \\t]*(?:(?:Prof\\.|Dr\\.|Dipl\\.-\\S+|M\\.\\s?A\\.|B\\.\\s?A\\.|M\\.\\s?Ed\\.)\\s*)*" +
            "((?:[$UPPER]\\.\\s?)?$WORD(?:[ \\t]+(?:von|van|de|zu|vom)?[ \\t]*$WORD){0,3})[ \\t]*(?:,.*)?$"
    )
    private val MAIL_HEADER = Regex("(?im)^[ \\t]*(?:von|from|an|to|cc|bcc|absender|empfänger|gesendet von|antwort an|reply-to)[ \\t]*:[ \\t]*(.+)$")
    private val FAMOUS_PATTERN by lazy { Regex("(?<![$LETTERS])(?:" + FAMOUS.sortedByDescending { it.length }.joinToString("|") { Regex.escape(it) } + ")(?![$LETTERS])") }

    /** Kinds of schools and day-care centres that are followed by their name. */
    private const val SCHOOL_KINDS = "Grundschule|Oberschule|Mittelschule|Realschule|Hauptschule|Gesamtschule|Gemeinschaftsschule|Förderschule|" +
        "Berufsschule|Berufliches Schulzentrum|BSZ|Gymnasium|Abendgymnasium|Waldorfschule|Montessorischule|Freie Schule|Evangelische Schule|" +
        "Kita|Kindertagesstätte|Kindergarten|Hort|Krippe|Schulhort"

    /** „Grundschule am Park“, „Gymnasium Dresden-Plauen“, „Kita „Sonnenschein““, „32. Grundschule“. */
    private val SCHOOL = Regex(
        "(?:\\b\\d{1,3}\\.\\s?)?\\b(?:$SCHOOL_KINDS)\\s+(?:[„\"»][^“”\"«\\n]{2,40}[“”\"«]|" +
            "(?:am|an der|im|in der|zum|zur|auf dem|unterm)\\s+$WORD(?:\\s+$WORD)?|$WORD(?:[- ]$WORD){0,2})" +
            "|\\b\\d{1,3}\\.\\s?(?:$SCHOOL_KINDS)\\b"
    )

    /** „Goethe-Gymnasium“, „Pestalozzi-Oberschule“, „Lindenschule“. */
    private val NAMED_SCHOOL = Regex("\\b([$UPPER][$LOWER]+)-?(?:Schule|Grundschule|Oberschule|Gymnasium|Gesamtschule|Kita)\\b")
    private val NOT_SCHOOL_PREFIX = setOf(
        "Grund", "Ober", "Mittel", "Real", "Haupt", "Gesamt", "Förder", "Berufs", "Hoch", "Fach", "Volks", "Musik", "Sprach", "Kunst",
        "Tanz", "Fahr", "Abend", "Privat", "Partner", "Modell", "Gemeinschafts", "Waldorf", "Montessori", "Sommer", "Ganztags", "Halbtags",
        "Brennpunkt", "Schwerpunkt", "Projekt", "Pilot", "Stadt", "Land", "Dorf", "Regel", "Inklusions", "Sport", "Ausbildungs", "Universitäts",
    )

    /** A person's role followed by a town: „die Schulleiterin in Pirna“, „Klassenlehrer aus Meißen“. */
    private val ROLE_PLACE = Regex(
        "\\b(?:Schulleiter|Klassenlehrer|Lehrer|Fachlehrer|Rektor|Direktor|Leiter|Kitaleiter|Hortleiter|Erzieher|Sozialarbeiter|" +
            "Schulsozialarbeiter|Bürgermeister|Pfarrer|Trainer|Schüler|Referendar|Hausmeister|Sekretär|Elternsprecher|Elternvertreter)(?:in|innen)?" +
            "\\s+(?:der\\s+\\S+\\s+)?(?:in|aus|von|bei)\\s+($WORD(?:-$WORD)?)"
    )
    private val NOT_PLACES = setOf(
        "Deutschland", "Sachsen", "Bayern", "Europa", "Österreich", "Schweiz", "Thüringen", "Brandenburg", "Berlin", "Frankreich",
        "Polen", "Tschechien", "Ruhestand", "Elternzeit", "Teilzeit", "Vollzeit", "Ausbildung", "Rente", "Zukunft", "Mathematik", "Deutsch",
    )

    /** First name, then a capitalized word that ends the phrase (punctuation, line end or a lower-case word). */
    private val RARE_AFTER_FIRST = Regex("(?<![$LETTERS-])($WORD)[ \\t]+([$UPPER][$LOWER]+(?:-[$UPPER][$LOWER]+)?)(?=[ \\t]*(?:[.,;:!?)]|\\n|$|[ \\t]+[a-zäöü]))")

    /** Citations in the text: „Müller (2020)“, „(Müller & Schmidt, 2019)“, „Müller et al. 2018“, „Müller, A. (2021)“. */
    private val CITATION = Regex(
        "($WORD(?:\\s+(?:von|van|de|zu)\\s+$WORD)?)(?:,\\s*((?:[$UPPER]\\.\\s?){1,3}))?" +
            "(?:\\s*(?:&|und|and|/)\\s*($WORD)(?:,\\s*(?:[$UPPER]\\.\\s?){1,3})?)?(\\s+et\\s+al\\.)?,?\\s*\\(?((?:19|20)\\d{2}[a-z]?)\\b"
    )

    /** DOI or ISBN: the text is a published book or paper (AP-03: public sources stay as they are). */
    private val PUBLIC_MARK = Regex("(?i)\\b(?:doi:?\\s*|https?://(?:dx\\.)?doi\\.org/)?10\\.\\d{4,9}/[^\\s\"<>]+|\\bISBN(?:-1[03])?:?\\s*[\\d-]{10,17}[\\dX]\\b|\\bISSN:?\\s*\\d{4}-\\d{3}[\\dX]\\b")

    /**
     * Authors cited in [text] (surnames, and „A. Müller“ when the initial is given) – they are public and never
     * replaced, unless they are on the own list.
     */
    fun citedAuthors(text: String): Set<String> {
        val out = LinkedHashSet<String>()
        CITATION.findAll(text).forEach { m ->
            val first = m.groupValues[1]
            if (first in NOT_NAMES || first in INSTITUTION_WORDS || first in ROLE_WORDS || NOUN_ENDING.matches(first) && !isLast(first)) return@forEach
            out += first
            m.groupValues[2].trim().takeIf { it.isNotEmpty() }?.let { out += "$it $first".replace(Regex("\\s+"), " ") }
            m.groupValues[3].takeIf { it.isNotEmpty() && it !in NOT_NAMES && it !in INSTITUTION_WORDS }?.let { out += it }
        }
        return out
    }

    /** Whether [name] names a school or day-care centre (for names found by the local model). */
    fun looksLikeSchool(name: String): Boolean = SCHOOL.containsMatchIn(name) || NAMED_SCHOOL.containsMatchIn(name)

    /** Whether [text] is a published source (book, paper with DOI/ISBN/ISSN) – suggested as „öffentlich“ for attachments. */
    fun looksPublic(text: String): Boolean = PUBLIC_MARK.containsMatchIn(text.take(60_000))

    /** Well-known people (science, education, politics) whose names are no personal data of a third party. */
    fun isFamous(name: String): Boolean = name in FAMOUS

    /** Endings of nouns that are no names ("Didaktik", "Schulleitung", "Jugendamt"), unless the word is a known name. */
    private val NOUN_ENDING = Regex(".+(?:ung|heit|keit|schaft|ik|tät|ion|ismus|ium|ur|amt|team|rat|wesen|stelle|leitung|gruppe|ei|ität|ologie|ie)")

    /** Endings that mark a surname after a first name even if it is not on the list ("Anna Kaltenbach"). */
    private val SURNAME_ENDING = Regex(
        ".+(?:mann|bach|berg|burg|dorf|feld|hagen|hausen|heim|horst|kamp|stein|thal|wald|witz|ski|sky|ska|czyk|wicz|enko|oglu|oğlu|ović|ovic|" +
            "ić|ow|ova|ová|yan|sson|ssen|bauer|meier|maier|mayer|meyer|huber|hofer|inger|brecht|hardt|hold|ling|städt|stedt|reuther|müller|schmidt)"
    )

    /** Words that look like names after "Frau"/"Herr", a role or a greeting but are not ("Herr Präsident", "Liebe Eltern"). */
    private val NOT_NAMES = setOf(
        "Kollegin", "Kollege", "Kollegen", "Präsident", "Präsidentin", "Dekan", "Dekanin", "Rektor", "Rektorin", "Direktor",
        "Direktorin", "Professorin", "Professor", "Doktor", "Ministerin", "Minister", "Bürgermeister", "Bürgermeisterin",
        "Vorsitzende", "Vorsitzender", "Studiendekan", "Studiendekanin", "Prof", "Dr", "Lehrerin", "Lehrer", "Frau", "Herr", "Herrn",
        "Eltern", "Kolleginnen", "Kollegium", "Studierende", "Schülerinnen", "Schüler", "Leute", "Alle", "Freunde", "Team", "Teilnehmende",
        "Teilnehmer", "Teilnehmerinnen", "Mitglieder", "Damen", "Herren", "Familie", "Klasse", "Gemeinde", "Lehrkräfte", "Interessierte",
        "Mitarbeitende", "Erziehungsberechtigte", "Nachbarn", "Kinder", "Zusammen", "Allerseits", "Welt", "Mama", "Papa", "Sir", "Madam",
        "Kommilitoninnen", "Kommilitonen", "Fachschaft", "Gruppe", "Grüße", "Gruß", "Dank", "Danke", "Bitte", "Hallo", "Gott", "Gottes",
        "Erde", "Staat", "Natur", "Zufall", "Morgen", "Abend", "Tag", "Nacht", "Woche", "Sommer", "Winter", "Herbst", "Frühling",
        "Mathematik", "Deutsch", "Englisch", "Französisch", "Latein", "Spanisch", "Russisch", "Italienisch", "Polnisch", "Tschechisch",
        "Physik", "Chemie", "Biologie", "Geschichte", "Geographie", "Geografie", "Erdkunde", "Kunst", "Musik", "Sport", "Informatik",
        "Ethik", "Religion", "Sachunterricht", "Gemeinschaftskunde", "Wirtschaft", "Politik", "Philosophie", "Psychologie", "Pädagogik",
        "Technik", "Astronomie", "Aufgaben", "Fragen", "Hilfe", "Unterstützung", "Zeit", "Probleme", "Schwierigkeiten", "Noten",
        "Hausaufgaben", "Ergebnisse", "Mit", "Von", "In", "Und", "Der", "Die", "Das", "Im", "Am", "Beim", "Zum", "Zur", "Auf", "Aus",
        "Sekretariat", "Büro", "Verwaltung", "Prüfungsamt", "Dekanat", "Studienbüro", "Info", "Service", "Support", "Newsletter",
        "Redaktion", "Personalabteilung", "Absender", "Empfänger", "Betreff", "Anhang", "Datum", "Gesendet", "Montag", "Dienstag",
        "Mittwoch", "Donnerstag", "Freitag", "Samstag", "Sonntag", "Liebe", "Lieber", "Viele", "Beste", "Herzliche", "Freundliche",
        "Services", "Union", "Life", "Center", "Card", "Housing", "Didaktik", "Fachdidaktik", "Erziehungswissenschaft", "Kollegium",
        "Schulleitung", "Klassenleitung", "Elternrat", "Elternvertretung", "Förderverein", "Personalrat", "Arbeitsgruppe", "Arbeitsbereich",
        "Forschungsgruppe", "Orga", "Hausmeister", "Hausmeisterin", "Bescheid", "Geburtstag", "Feedback", "Termin", "Unterricht",
        "Dein", "Deine", "Ihr", "Ihre", "Euer", "Eure", "Unser", "Unsere", "Mein", "Meine",
    )

    /**
     * Well-known people of science, education, culture and politics: their names are no personal data of a third party
     * and the model needs them ("nach Max Weber"). The own list still wins.
     */
    private val FAMOUS = setOf(
        "Max Weber", "Maria Montessori", "Martin Luther", "Martin Luther King", "Karl Marx", "Wilhelm von Humboldt", "Alexander von Humboldt",
        "Johann Heinrich Pestalozzi", "Friedrich Fröbel", "Jean Piaget", "John Dewey", "Paulo Freire", "Hannah Arendt", "Theodor Adorno",
        "Jürgen Habermas", "Niklas Luhmann", "Pierre Bourdieu", "Wolfgang Klafki", "Hilbert Meyer", "John Hattie", "Albert Einstein",
        "Johann Wolfgang", "Friedrich Schiller", "Immanuel Kant", "Sigmund Freud", "Anna Freud", "Angela Merkel", "Olaf Scholz",
        "Friedrich Merz", "Lev Vygotsky", "Lew Wygotski", "Benjamin Bloom", "Albert Bandura", "Burrhus Skinner", "Erik Erikson",
        "Lawrence Kohlberg", "Howard Gardner", "Jerome Bruner", "Robert Gagné", "Klaus Hurrelmann", "Andreas Helmke", "Hans Aebli",
        "Heinrich Roth", "Hartmut von Hentig", "Ewald Terhart", "Mats Alvesson", "Thomas Kuhn", "Karl Popper", "Max Planck",
        "Marie Curie", "Charles Darwin", "Isaac Newton", "Ludwig van Beethoven", "Johann Sebastian Bach", "Wolfgang Amadeus Mozart",
        "Anne Frank", "Sophie Scholl", "Hans Scholl", "Willy Brandt", "Helmut Kohl", "Helmut Schmidt", "Konrad Adenauer",
        "Frank-Walter Steinmeier", "Ursula von der Leyen", "Emmanuel Macron", "Barack Obama", "Donald Trump", "Joe Biden",
        "Leonardo da Vinci", "Rosa Luxemburg", "Bertolt Brecht", "Thomas Mann", "Heinrich Heine", "Franz Kafka", "Hermann Hesse",
        "Erich Kästner", "Astrid Lindgren", "Michael Ende", "Otfried Preußler", "Cornelia Funke", "Jean-Jacques Rousseau", "John Locke",
        "Georg Wilhelm Friedrich Hegel", "Friedrich Nietzsche", "Martin Heidegger", "Ludwig Wittgenstein", "Simone de Beauvoir",
        "Judith Butler", "Michel Foucault", "Klaus Mollenhauer", "Dietrich Benner", "Wolfgang Schulz", "Paul Heimann", "Gunter Otto",
        "Herbert Gudjons", "Werner Jank", "Hilbert L. Meyer", "Eckhard Klieme", "Jürgen Baumert", "Mareike Kunter", "Frank Lipowsky",
        "Kurt Reusser", "Tina Seidel", "Manfred Prenzel", "Olaf Köller", "Heinz-Elmar Tenorth", "Horst Rumpf", "Wolfgang Edelstein",
        "Rainer Bromme", "Fritz Oser", "Jean-Pierre Changeux", "Daniel Kahneman", "Carol Dweck", "Richard Mayer", "John Sweller",
        "Robert Slavin", "Lee Shulman", "Linda Darling-Hammond", "Dylan Wiliam", "Paul Black", "Edward Deci", "Richard Ryan",
        "Urie Bronfenbrenner", "Kurt Lewin", "Max Horkheimer", "Walter Benjamin", "Erich Fromm", "Norbert Elias", "Georg Simmel",
        "Émile Durkheim", "Talcott Parsons", "Erving Goffman", "Anthony Giddens", "Ulrich Beck", "Hartmut Rosa", "Andreas Reckwitz",
        "Armin Nassehi", "Heinz Bude", "Steffen Mau", "Ralf Dahrendorf", "Robert Merton", "Alfred Schütz", "Peter Berger",
        "Thomas Luckmann", "Barney Glaser", "Anselm Strauss", "Philipp Mayring", "Udo Kuckartz", "Uwe Flick", "Ralf Bohnsack",
        "Fritz Schütze", "Ulrich Oevermann", "Winfried Marotzki", "Gabriele Rosenthal", "Christel Hopf", "Siegfried Lamnek",
        "Johann Friedrich Herbart", "Johann Amos Comenius", "Friedrich Schleiermacher", "Herman Nohl", "Eduard Spranger",
        "Theodor Litt", "Wilhelm Dilthey", "Peter Petersen", "Célestin Freinet", "Janusz Korczak", "Rudolf Steiner", "Alexander Neill",
        "Elisabeth Noelle-Neumann", "Svenja Schulze", "Robert Habeck", "Annalena Baerbock", "Christian Lindner", "Michael Kretschmer",
        "Bettina Stark-Watzinger", "Karin Prien", "Christian Piwarz", "Conrad Clemens",
    )

    private val INSTITUTION_WORDS = setOf(
        "Schule", "Grundschule", "Oberschule", "Gymnasium", "Gesamtschule", "Förderschule", "Berufsschule", "Hochschule",
        "Universität", "Institut", "Fakultät", "Professur", "Klasse", "Kita", "Kindergarten", "Hort", "Amt", "Schulamt",
        "Landesamt", "Ministerium", "Verein", "Stiftung", "Gmbh", "GmbH", "Zentrum", "Seminar", "Projekt", "Firma",
        "Lehrstuhl", "Bereich", "Abteilung", "Referat", "Dezernat", "Bibliothek", "Mensa", "Campus", "Gebäude", "Raum",
        "Technische", "Universitat", "Dresden", "Leipzig", "Chemnitz", "Sachsen", "Deutschland", "Studentenwerk",
    )

    /** Placeholders in a postal code line that are no town ("10000 Euro", "50000 Einwohner"). */
    private val NOT_TOWNS = setOf(
        "Euro", "Einwohner", "Menschen", "Personen", "Studierende", "Schüler", "Schülerinnen", "Mitglieder", "Stunden", "Minuten",
        "Exemplare", "Teilnehmer", "Teilnehmende", "Besucher", "Seiten", "Wörter", "Zeichen", "Punkte", "Jahre", "Tage", "Kilometer",
        "Meter", "Dollar", "Franken", "Pfund", "Stück", "Fälle", "Bücher", "Artikel", "Downloads", "Klicks", "Follower", "Mal",
    )

    /** E-mail local parts that are no person ("info@", "sekretariat.didaktik@"). */
    private val NOT_MAIL_NAMES = setOf(
        "info", "kontakt", "contact", "office", "sekretariat", "sekr", "mail", "email", "post", "postmaster", "admin", "noreply", "no",
        "reply", "service", "support", "team", "hello", "hallo", "studium", "verwaltung", "webmaster", "newsletter", "presse", "press",
        "buero", "büro", "institut", "fakultaet", "dekanat", "pruefungsamt", "bibliothek", "zih", "servicedesk", "helpdesk", "studienbuero",
        "studienberatung", "bewerbung", "jobs", "karriere", "personal", "hr", "rechnung", "invoice", "billing", "sales", "vertrieb",
        "marketing", "redaktion", "schule", "kita", "verwaltung", "leitung", "direktion", "rektorat", "tu", "dresden", "uni", "de", "org",
        "mailbox", "test", "user", "nutzer", "student", "studenten", "lehrer", "anmeldung", "bestellung", "order", "orders", "news",
    )

    /** Header words of table columns that hold persons; the role of the person if one is clear. */
    private val NAME_COLUMN = Regex(
        "(?i)^(?:vor-?name|vornamen|nach-?name|nachnamen|familienname|rufname|name, vorname|nachname, vorname|vorname nachname|" +
            "vollständiger name|voller name|name|namen|person|personen|kind|kinder|teilnehmer(?:in)?|teilnehmende|ansprechpartner(?:in)?|" +
            "schüler(?:in)?|schüler/?innen|schüler:innen|schüler\\*innen|schülername|mitschüler(?:in)?|student(?:in)?|studierende|studierender|" +
            "doktorand(?:in)?|lehrkraft|lehrer(?:in)?|klassenlehrer(?:in)?|referendar(?:in)?|eltern|mutter|vater|erziehungsberechtigte[r]?|" +
            "elternteil|betreuer(?:in)?|mentor(?:in)?|tutor(?:in)?|kolleg(?:e|in)|mitarbeiter(?:in)?|mitarbeitende|bewerber(?:in)?|" +
            "patient(?:in)?|klient(?:in)?|empfänger(?:in)?|absender(?:in)?|verantwortlich|zuständig|betreuung|gutachter(?:in)?|prüfer(?:in)?)$"
    )
    private val GENERIC_NAME_COLUMN = Regex("(?i)^(?:name|namen|person|personen|verantwortlich|zuständig|empfänger(?:in)?|absender(?:in)?)$")
    private val BIRTH_COLUMN = Regex("(?i)^(?:geburtsdatum|geb\\.?|geb\\.?-?datum|geboren(?: am)?|geburtstag|date of birth|dob|birthday)$")
    private val ADDRESS_COLUMN = Regex("(?i)^(?:anschrift|adresse|wohnadresse|privatadresse|heimatadresse|straße|strasse|straße und hausnummer|straße, nr\\.?|hausnummer|plz|postleitzahl|plz/ort|plz und ort|plz, ort|wohnort)$")
    /** Companion columns that make a generic "Name" column a column of persons. */
    private val PERSON_COLUMNS = Regex("(?i)^(?:vor-?name|klasse|geburtsdatum|geb\\.?|e-?mail|telefon|tel\\.?|handy|mobil|note|noten|matrikel.*|anwesend|fehltage|entschuldigt|unterschrift|geschlecht|alter)$")

    fun find(
        text: String,
        ownTerms: List<String> = emptyList(),
        automatic: Boolean = true,
        off: Set<PiiFeature> = emptySet(),
        ignore: Collection<String> = emptyList(),
    ): List<PiiHit> {
        if (text.isBlank()) return emptyList()
        fun on(f: PiiFeature) = f !in off
        // File names: "Elterngespräch_Mueller.docx" – an underscore separates words like a space.
        val src = if (on(PiiFeature.FILE_NAMES)) text.replace('_', ' ') else text
        val inflect = on(PiiFeature.INFLECTION)
        val hits = LinkedHashSet<PiiHit>()
        val own = LinkedHashSet<String>()

        fun addOwn(value: String, kind: PiiKind) {
            val forms = if (on(PiiFeature.VARIANTS)) variants(value, lower = true) else listOf(value)
            val present = forms.filter { containsWord(src, it, inflect) }
            if (present.isEmpty()) return
            hits += PiiHit(value, kind)
            own += value
            present.filter { it != value }.forEach { hits += PiiHit(it, kind, canonical = value); own += it }
        }
        for (term in ownTerms) {
            val t = term.trim()
            if (t.length < 2) continue
            val words = personWords(t)
            if (words != null) {
                if (words.size > 1) addOwn(t, PiiKind.NAME)
                words.forEach { addOwn(it, PiiKind.NAME) }
            } else addOwn(t, PiiKind.TERM)
        }

        // E-mail addresses, links and @-names may contain underscores: found in the original text.
        if (on(PiiFeature.EMAIL)) EMAIL.findAll(text).forEach { hits += PiiHit(it.value, PiiKind.EMAIL) }
        if (on(PiiFeature.MATRIKEL)) {
            MATRIKEL.findAll(src).forEach { hits += PiiHit(it.groupValues[1], PiiKind.MATRIKEL) }
            OTHER_NUMBER.findAll(src).forEach { m -> m.groupValues[1].takeIf { v -> v.any { it.isDigit() } }?.let { hits += PiiHit(it, PiiKind.ID) } }
        }
        if (on(PiiFeature.BANK)) {
            IBAN.findAll(src).forEach { m -> if (m.value.count { it.isDigit() } >= 12) hits += PiiHit(m.value, PiiKind.IBAN) }
            BIC.findAll(src).forEach { hits += PiiHit(it.groupValues[1], PiiKind.BIC) }
            CARD.findAll(src).forEach { m -> if (luhn(m.value) && hits.none { h -> m.value in h.value }) hits += PiiHit(m.value, PiiKind.CARD) }
        }
        if (on(PiiFeature.IDS)) {
            STEUER_ID.findAll(src).forEach { hits += PiiHit(it.groupValues[1], PiiKind.ID) }
            STEUER_NR.findAll(src).forEach { hits += PiiHit(it.groupValues[1], PiiKind.ID) }
            SOCIAL.findAll(src).forEach { hits += PiiHit(it.groupValues[1], PiiKind.ID) }
            SOCIAL_KEY.findAll(src).forEach { hits += PiiHit(it.groupValues[1].trim(), PiiKind.ID) }
            PASSPORT.findAll(src).forEach { m -> m.groupValues[1].takeIf { v -> v.any { it.isDigit() } }?.let { hits += PiiHit(it, PiiKind.ID) } }
        }
        if (on(PiiFeature.BIRTHDATE)) BIRTHDATE.findAll(src).forEach { hits += PiiHit(it.groupValues[1].trim(), PiiKind.BIRTHDATE) }
        if (on(PiiFeature.PHONE)) PHONE.findAll(src).forEach { m ->
            val digits = m.value.count { it.isDigit() }
            if (digits in 7..15 && hits.none { h -> m.value in h.value || h.value in m.value }) hits += PiiHit(m.value.trim(), PiiKind.PHONE)
        }
        if (on(PiiFeature.ADDRESS)) {
            STREET.findAll(src).forEach { hits += PiiHit(it.value, PiiKind.ADDRESS) }
            POSTCODE.findAll(src).forEach { m ->
                val town = m.groupValues[2]
                if (town.split(' ', '-').first() !in NOT_TOWNS) hits += PiiHit("${m.groupValues[1]}${src.substring(m.groups[1]!!.range.last + 1, m.groups[2]!!.range.first)}$town", PiiKind.POSTCODE)
            }
        }
        if (on(PiiFeature.PLATE)) PLATE.findAll(src).forEach { m ->
            if (m.groupValues[1] !in NOT_PLATES) hits += PiiHit(m.value, PiiKind.PLATE)
        }
        if (on(PiiFeature.WEB)) {
            PROFILE.findAll(text).forEach { hits += PiiHit(it.value.trimEnd('.', ',', ';', ':'), PiiKind.LINK) }
            IP.findAll(src).forEach { m ->
                val parts = m.value.split('.').map { it.toInt() }
                // "2.4.10.1" is a chapter, "192.168.10.24" an address.
                if (parts.first() >= 10) hits += PiiHit(m.value, PiiKind.IP)
            }
            HANDLE.findAll(text).forEach { m -> if (m.groupValues[1].lowercase() !in CODE_AT) hits += PiiHit("@" + m.groupValues[1], PiiKind.HANDLE) }
        }

        val famous = if (automatic) FAMOUS_PATTERN.findAll(src).map { it.range }.toList() else emptyList()
        val names = LinkedHashMap<String, PiiKind>()
        fun name(value: String, kind: PiiKind = PiiKind.NAME) {
            val v = value.trim()
            if (v.length < 2 || v in NOT_NAMES || v in INSTITUTION_WORDS) return
            val current = names[v]
            if (current == null || current == PiiKind.NAME && kind != PiiKind.NAME) names[v] = kind
        }
        fun nameWords(words: List<String>, kind: PiiKind) {
            val parts = words.filter {
                it !in PARTICLES && it !in NOT_NAMES && it !in INSTITUTION_WORDS && !Regex("[$UPPER]\\.").matches(it) &&
                    (isFirst(it) || isLast(it) || !NOUN_ENDING.matches(it))
            }
            if (parts.isEmpty()) return
            parts.forEach { name(it, kind) }
            if (parts.size > 1) name(parts.joinToString(" "), kind)
        }

        if (on(PiiFeature.TABLES)) tableHits(src, ::name, hits, on(PiiFeature.BIRTHDATE), on(PiiFeature.ADDRESS))

        if (on(PiiFeature.EMAIL_NAMES)) EMAIL.findAll(text).forEach { m ->
            val local = m.value.substringBefore('@').lowercase()
            val parts = local.split('.', '_', '-').filter { p -> p.length >= 2 && p.all { it.isLetter() } }
            if (parts.isEmpty() || parts.any { it in NOT_MAIL_NAMES }) return@forEach
            val long = parts.filter { it.length >= 3 }
            val candidates = if (parts.size >= 2) long else long.filter { isFirst(cap(it)) || isLast(cap(it)) }
            // "mueller" is "Müller" if that is a known name or occurs in the text.
            fun spelled(p: String): String = deTranslit(cap(p)).takeIf { d -> d != cap(p) && (isFirst(d) || isLast(d) || containsWord(src, d, false)) } ?: cap(p)
            candidates.forEach { p ->
                name(cap(p))
                name(spelled(p))
            }
            if (candidates.size >= 2) name(candidates.joinToString(" ") { spelled(it) })
        }

        if (automatic) {
            ADDRESSED.findAll(src).forEach { m ->
                val words = m.groupValues[1].split(Regex("\\s+")).filter { it !in PARTICLES && !Regex("[$UPPER]\\.").matches(it) }
                if (words.isEmpty()) return@forEach
                // "Frau Anna Müller": the second word belongs to the name if the first is a first name or the second a surname;
                // "Frau Müller Bescheid geben" keeps "Bescheid".
                val take = mutableListOf(words.first())
                for (i in 1 until words.size) {
                    if (isFirst(words[i - 1]) || isLast(words[i])) take += words[i] else break
                }
                nameWords(take, PiiKind.NAME)
                // With initials: "Frau A. Müller" is one placeholder.
                Regex("((?:[$UPPER]\\.\\s?){1,2})${Regex.escape(take.first())}").find(m.groupValues[1])?.let { i ->
                    name(i.groupValues[1] + take.joinToString(" "))
                }
            }

            if (on(PiiFeature.ROLES)) {
                ROLE.findAll(src).forEach { m ->
                    val (kind, sure) = ROLE_WORDS.getValue(m.groupValues[1])
                    val words = m.groupValues[2].split(Regex("\\s+")).filter { !Regex("[$UPPER]\\.").matches(it) }
                    val first = words.firstOrNull() ?: return@forEach
                    if (first in NOT_NAMES || first in INSTITUTION_WORDS || first in ROLE_WORDS) return@forEach
                    if (!sure && !isFirst(first) && !isLast(first)) return@forEach
                    val take = mutableListOf(first)
                    words.getOrNull(1)?.let { w -> if (isLast(w) || isFirst(w) || SURNAME_ENDING.matches(w)) take += w }
                    nameWords(take, kind)
                }
                GREETING.findAll(src).forEach { m ->
                    val words = listOf(m.groupValues[1], m.groupValues[2]).filter { it.isNotEmpty() }
                    if (words.first() in NOT_NAMES) return@forEach
                    nameWords(words, PiiKind.NAME)
                }
                SIGNED.findAll(src).forEach { m -> nameWords(m.groupValues[1].split(Regex("\\s+")), PiiKind.NAME) }
                // The name below a closing formula ("Viele Grüße\nAnna Müller").
                val lines = src.lines()
                for ((i, line) in lines.withIndex()) {
                    if (!CLOSING.matches(line)) continue
                    val next = lines.drop(i + 1).take(3).firstOrNull { it.isNotBlank() } ?: continue
                    if (next.any { it.isDigit() } || next.length > 60) continue
                    SIGNATURE_LINE.matchEntire(next)?.let { s -> nameWords(s.groupValues[1].split(Regex("\\s+")), PiiKind.NAME) }
                }
                // "Von: Müller, Anna <anna.mueller@…>", "An: Lukas Schmidt; Mia Weber"
                MAIL_HEADER.findAll(src).forEach { m ->
                    val value = m.groupValues[1].replace(Regex("<[^>]*>"), " ").replace(EMAIL, " ").replace("\"", " ")
                    value.split(';').forEach { part ->
                        val words = NAME_WORD.findAll(part).map { it.value }.toList()
                        if (words.size in 1..4) nameWords(words, PiiKind.NAME)
                    }
                }
            }

            // A frequent first name, and the names directly after it ("Anna Lena Müller", "Anna Kaltenbach").
            val words = NAME_WORD.findAll(src).toList()
            var chain = mutableListOf<String>()
            fun flush() {
                if (chain.isNotEmpty()) nameWords(chain, PiiKind.NAME)
                chain = mutableListOf()
            }
            for ((i, w) in words.withIndex()) {
                if (famous.any { w.range.first in it }) {
                    flush()
                    continue
                }
                val adjacent = i > 0 && src.substring(words[i - 1].range.last + 1, w.range.first).let { it.isNotEmpty() && it.isBlank() && '\n' !in it }
                val value = w.value
                when {
                    isFirst(value) -> {
                        if (!(chain.isNotEmpty() && adjacent)) flush()
                        chain += value
                    }
                    chain.isNotEmpty() && adjacent && (isLast(value) || SURNAME_ENDING.matches(value) && value !in NOT_NAMES) -> chain += value
                    inflect && value.length > 3 && value.endsWith("s") && isFirst(value.dropLast(1)) -> {
                        flush()
                        name(value.dropLast(1))
                    }
                    else -> flush()
                }
            }
            flush()
        }

        if (on(PiiFeature.INSTITUTIONS)) {
            SCHOOL.findAll(src).forEach { m -> hits += PiiHit(m.value.trim().trimEnd('.', ',', ';'), PiiKind.SCHOOL) }
            NAMED_SCHOOL.findAll(src).forEach { m -> if (m.groupValues[1] !in NOT_SCHOOL_PREFIX) hits += PiiHit(m.value, PiiKind.SCHOOL) }
            ROLE_PLACE.findAll(src).forEach { m ->
                val place = m.groupValues[1]
                if (place.split(' ', '-').first() !in NOT_PLACES) hits += PiiHit(place, PiiKind.PLACE)
            }
        }

        // Rare surnames without a title: a known first name, then a capitalized word that is no usual noun and ends the
        // phrase ("Jolina Kretzschmann sagte …", "mit Anna Wojtyła.").
        if (automatic) RARE_AFTER_FIRST.findAll(src).forEach { m ->
            val first = m.groupValues[1]
            val last = m.groupValues[2]
            if (isFirst(first) && !isFirst(last) && last !in NOT_NAMES && last !in INSTITUTION_WORDS && last !in ROLE_WORDS &&
                !NOUN_ENDING.matches(last) && last.length in 3..24 && famous.none { m.range.first in it }
            ) {
                name(last)
                name("$first $last")
            }
        }

        // Spellings of found names: "MÜLLER", "Mueller" (not lower case – "frank" is a word).
        val variantsOn = on(PiiFeature.VARIANTS)
        for ((v, kind) in names) {
            if (v in own) continue
            hits += PiiHit(v, kind)
            // All spellings, also those not in this text: a later "MUELLER" has no clue of its own.
            if (variantsOn) variants(v, lower = false).filter { it != v && it !in names }.forEach { hits += PiiHit(it, kind, canonical = v) }
        }

        // Web addresses with a name in them ("https://tu-dresden.de/team/anna-mueller").
        if (on(PiiFeature.WEB)) {
            val parts = hits.filter { it.kind.person && it.value.length >= 4 && ' ' !in it.value }.map { translit(it.value).lowercase() }.toSet()
            if (parts.isNotEmpty()) URL.findAll(text).forEach { m ->
                val url = m.value.trimEnd('.', ',', ';', ':')
                val lower = translit(url).lowercase()
                if (parts.any { p -> Regex("(?<![a-z])${Regex.escape(p)}(?![a-z])").containsMatchIn(lower) }) hits += PiiHit(url, PiiKind.LINK)
            }
        }

        val skip = ignore.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()
        val seen = HashSet<String>()
        return hits.filter { h ->
            (h.value in own || h.value.lowercase() !in skip && (h.canonical == null || h.canonical.lowercase() !in skip)) && seen.add(h.value)
        }
    }

    /**
     * Words that might be names but have no clear clue – for the suggestions under the input: after an ambiguous role
     * ("Schüler Jolin"), after a first name ("Anna Kowalczyk") and before a form of address in a signature.
     */
    fun suspects(text: String, known: Collection<String>, ignore: Collection<String> = emptyList()): List<String> {
        if (text.isBlank()) return emptyList()
        val skip = (known + ignore).map { it.lowercase() }.toSet()
        val found = LinkedHashSet<String>()
        fun add(w: String) {
            if (w.length >= 3 && w !in NOT_NAMES && w !in INSTITUTION_WORDS && w !in ROLE_WORDS && w.lowercase() !in skip && !isFirst(w) && !isLast(w)) found += w
        }
        ROLE.findAll(text).forEach { m ->
            m.groupValues[2].split(Regex("\\s+")).filter { !Regex("[$UPPER]\\.").matches(it) }.forEach(::add)
        }
        val words = NAME_WORD.findAll(text).toList()
        for (i in 1 until words.size) {
            val between = text.substring(words[i - 1].range.last + 1, words[i].range.first)
            if (between == " " && isFirst(words[i - 1].value)) add(words[i].value)
        }
        // Capitalized words in the middle of a sentence that end like a surname ("bei Kowalczyk").
        Regex("(?<=[a-zäöüß,] )[$UPPER][$LOWER]+").findAll(text).forEach { m -> if (SURNAME_ENDING.matches(m.value)) add(m.value) }
        return found.toList()
    }

    /** Whether [form] occurs in [text] as a word of its own (optionally with the ending of an inflected name). */
    fun containsWord(text: String, form: String, inflect: Boolean): Boolean =
        Regex("(?<![$LETTERS])${Regex.escape(form)}${if (inflect) "$SUFFIX?" else ""}(?![$LETTERS])").containsMatchIn(text)

    /** Spellings of [value]: upper case, ae/oe/ue/ss for umlauts and back, with [lower] also lower case. */
    fun variants(value: String, lower: Boolean): List<String> {
        val base = listOf(value, translit(value), deTranslit(value)).distinct()
        val out = LinkedHashSet<String>()
        base.forEach { b ->
            out += b
            if (b.length >= 3) out += b.uppercase()
            if (lower) {
                out += b.lowercase()
                out += b.lowercase().split(' ').joinToString(" ") { w -> w.split('-').joinToString("-") { it.replaceFirstChar { c -> c.uppercase() } } }
            }
        }
        return out.filter { it.length >= 2 }
    }

    fun translit(s: String): String = s.replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss")
        .replace("Ä", "Ae").replace("Ö", "Oe").replace("Ü", "Ue")

    /** "Mueller" → "Müller"; names that are spelled with "ae"/"ue" ("Michael", "Samuel") stay. */
    fun deTranslit(s: String): String {
        if (s in Names.first || s in Names.last) return s
        return s.replace("ae", "ä").replace("oe", "ö").replace("ue", "ü").replace("Ae", "Ä").replace("Oe", "Ö").replace("Ue", "Ü")
    }

    private fun cap(s: String) = s.replaceFirstChar { it.uppercase() }

    /** Also double names: "Anna-Lena", "Ben-Luca" (all parts first names). */
    private fun isFirst(w: String): Boolean = w in Names.first || deTranslit(w) in Names.first ||
        '-' in w && w.split('-').let { p -> p.size == 2 && p.all { it in Names.first || deTranslit(it) in Names.first } }

    /** Also double surnames: "Schulz-Wagner" (one part a surname). */
    private fun isLast(w: String): Boolean = w in Names.last || deTranslit(w) in Names.last ||
        '-' in w && w.split('-').let { p -> p.size == 2 && p.any { it in Names.last || deTranslit(it) in Names.last } }

    private fun luhn(number: String): Boolean {
        val digits = number.filter { it.isDigit() }
        if (digits.length !in 13..19) return false
        var sum = 0
        digits.reversed().forEachIndexed { i, c ->
            var d = c - '0'
            if (i % 2 == 1) { d *= 2; if (d > 9) d -= 9 }
            sum += d
        }
        return sum % 10 == 0
    }

    /** The words of an own entry that looks like a person ("Lukas Schmidt"), else null (e.g. "Gymnasium Dresden-Plauen"). */
    private fun personWords(term: String): List<String>? {
        val words = term.split(Regex("\\s+")).filter { it.isNotEmpty() && it !in PARTICLES }
        if (words.isEmpty() || words.size > 4 || term.any { it.isDigit() }) return null
        if (words.any { !Regex(WORD).matches(it) || it in INSTITUTION_WORDS }) return null
        return words.filter { it.length >= 2 }
    }

    /**
     * Columns of persons, birth dates and addresses in Markdown tables and CSV/TSV text: every cell of such a column
     * counts, with or without a clue in the cell itself.
     */
    private fun tableHits(text: String, name: (String, PiiKind) -> Unit, hits: MutableSet<PiiHit>, birth: Boolean, address: Boolean) {
        for (rows in tables(text)) {
            if (rows.size < 2) continue
            val head = rows.first().map { it.replace("*", "").replace(":", "").trim() }
            val companion = head.any { PERSON_COLUMNS.matches(it) }
            head.forEachIndexed { col, h ->
                val cells = rows.drop(1).mapNotNull { it.getOrNull(col)?.replace("**", "")?.trim() }.filter { it.isNotEmpty() && it != "-" && it != "–" }
                when {
                    NAME_COLUMN.matches(h) -> {
                        if (GENERIC_NAME_COLUMN.matches(h) && !companion && cells.none { c -> NAME_WORD.findAll(c).any { isFirst(it.value) || isLast(it.value) } }) return@forEachIndexed
                        val kind = roleOfColumn(h)
                        cells.filter { c -> c.none { it.isDigit() } && c.length <= 60 }.forEach { cell ->
                            val words = NAME_WORD.findAll(cell).map { it.value }.filter { it !in NOT_NAMES && it !in INSTITUTION_WORDS }.toList()
                            if (words.isEmpty() || words.size > 5) return@forEach
                            words.forEach { name(it, kind) }
                            if (words.size > 1) name(cell.replace(Regex("\\s+"), " "), kind)
                        }
                    }
                    birth && BIRTH_COLUMN.matches(h) -> cells.forEach { c -> ANY_DATE.findAll(c).forEach { hits += PiiHit(it.value, PiiKind.BIRTHDATE) } }
                    address && ADDRESS_COLUMN.matches(h) -> cells.filter { it.length >= 4 }.forEach { hits += PiiHit(it, PiiKind.ADDRESS) }
                }
            }
        }
    }

    private fun roleOfColumn(h: String): PiiKind {
        val l = h.lowercase()
        return when {
            l.contains("schüler") -> PiiKind.STUDENT
            l.startsWith("student") || l.startsWith("studierend") || l.startsWith("doktorand") -> PiiKind.UNI_STUDENT
            l.contains("lehr") || l.startsWith("referendar") -> PiiKind.TEACHER
            l.startsWith("eltern") || l == "mutter" || l == "vater" || l.startsWith("erziehungsberechtigt") -> PiiKind.PARENT
            l.startsWith("kolleg") -> PiiKind.COLLEAGUE
            else -> PiiKind.NAME
        }
    }

    /** Tables in [text]: Markdown tables and runs of lines with the same number of ";", tabs or commas. */
    fun tables(text: String): List<List<List<String>>> {
        val out = mutableListOf<List<List<String>>>()
        val lines = text.lines()
        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()
            if (line.startsWith("|") && line.count { it == '|' } >= 2) {
                val rows = mutableListOf<List<String>>()
                while (i < lines.size && lines[i].trim().startsWith("|")) {
                    val cells = lines[i].trim().trim('|').split(Regex("(?<!\\\\)\\|")).map { it.trim() }
                    if (!cells.all { it.matches(Regex(":?-{2,}:?")) }) rows += cells
                    i++
                }
                out += rows
                continue
            }
            val sep = listOf(';', '\t', ',').firstOrNull { s -> split(line, s).size >= 2 && (s != ',' || split(line, s).size >= 3) }
            if (sep != null) {
                val width = split(line, sep).size
                val rows = mutableListOf<List<String>>()
                var j = i
                while (j < lines.size && lines[j].isNotBlank() && split(lines[j].trim(), sep).size == width) {
                    rows += split(lines[j].trim(), sep)
                    j++
                }
                if (rows.size >= 2) {
                    out += rows
                    i = j
                    continue
                }
            }
            i++
        }
        return out
    }

    /** One CSV line, with "quoted; cells". */
    private fun split(line: String, sep: Char): List<String> {
        val cells = mutableListOf<String>()
        val cur = StringBuilder()
        var quoted = false
        for (ch in line) {
            when {
                ch == '"' -> quoted = !quoted
                ch == sep && !quoted -> { cells += cur.toString().trim(); cur.clear() }
                else -> cur.append(ch)
            }
        }
        cells += cur.toString().trim()
        return cells
    }
}

/** Frequent first names and surnames (German and the most frequent of families who live in Germany); surnames only count next to a first name or a form of address. */
object Names {
    val first: Set<String> = """
        Anna Emma Mia Hanna Hannah Lea Leah Lena Lina Leni Mila Marie Maria Clara Klara Sophie Sophia Sofia Luisa Luise Frieda
        Greta Emilia Amelie Charlotte Johanna Lotta Nele Paula Maja Maya Mathilda Matilda Leonie Laura Lara Sarah Sara Julia
        Lisa Katharina Jana Nina Elena Helena Victoria Viktoria Ella Ida Pia Merle Thea Alina Annika Carla Carlotta Celina
        Emely Emily Finja Fiona Isabell Isabella Jasmin Josephine Kim Lia Lilly Lili Lilli Magdalena Marlene Mira Nora Olivia
        Rosalie Stella Tessa Valentina Vanessa Wilma Zoe Ronja Malia Elif Zeynep Ayse Ayşe Fatma Leyla Amira Aylin Mariam Lilith
        Sabine Petra Claudia Susanne Andrea Monika Birgit Kerstin Heike Anja Katrin Kathrin Nicole Stefanie Silke Ines Ulrike
        Christine Christina Barbara Gabriele Ute Doris Karin Renate Ursula Sandra Melanie Daniela Tanja Simone Jessica Jennifer
        Franziska Juliane Antje Annett Anett Peggy Mandy Romy Anke Steffi Martina Manuela Cornelia Elke Bettina Beate Dagmar
        Angelika Brigitte Elisabeth Eva Ingrid Irene Jutta Marion Regina Sigrid Sylvia Silvia Uta Veronika Yvonne Diana Janine
        Jacqueline Josefine Theresa Teresa Lucia Luzia Hedwig Margarete Gisela Erika Martha Marta Linda Anne Annette Constanze
        Noah Leon Paul Ben Finn Fynn Elias Felix Luca Luka Lukas Lucas Henry Henri Emil Theo Jonas Louis Luis Anton Max Liam
        Matteo Oskar Oscar Moritz Jakob Jacob Leo Tim Tom Niklas Nicklas Julian David Samuel Alexander Maximilian Philipp
        Simon Fabian Florian Sebastian Daniel Michael Thomas Andreas Stefan Stephan Steffen Christian Martin Peter Markus
        Marcus Matthias Mathias Tobias Johannes Jonathan Benjamin Erik Eric Ole Mats Milan Karl Carl Friedrich Konstantin
        Vincent Valentin Rafael Raphael Levi Ali Mohammed Muhammad Mohamed Yusuf Emir Ahmet Mehmet Mustafa Omar Hamza Ibrahim
        Jürgen Uwe Ralf Ralph Dirk Jörg Torsten Thorsten Holger Frank Bernd Wolfgang Klaus Claus Dieter Heinz Rainer Gerd Horst
        Manfred Werner Günter Günther Helmut Hans Lars Sven Kevin Dennis Marcel Patrick Robert René Rene Jens Mirko Ronny Rico
        Enrico Toni Gustav Arne Bastian Malte Hannes Till Nils Niels Lennart Lennard Leonard Linus Mattis Joshua Aaron Adam
        Elijah Jannik Yannick Kilian Malik Marlon Nico Niko Pascal Robin Ruben Fritz Hugo Wilhelm Otto Georg Heinrich Hermann
        Ludwig Ulrich Volker Detlef Hartmut Reinhard Norbert Joachim Gerhard Bernhard Rüdiger Axel Olaf Carsten Kai-Uwe Ingo
        Jan-Niklas Marco Mario Oliver Ricardo Sascha Timo Tino Tilo Thilo Udo Vladimir Wladimir Dmitri Igor Sergej Piotr
        Jan Kai Leni Luna Nala Alma Juna Aurora Mina Mila Lene Isabel Milena Jolina Joline Liana Elina Aleyna Hira Ecrin Defne
        Azra Eslem Medina Sila Melek Esra Emine Hatice Meryem Sevgi Derya Gül Gülsüm Hülya Nurcan Selin Buse Büşra Merve
        Amina Aya Nour Noor Rania Yasmin Yasmina Dalia Lina Hiba Malak Salma Sana Inaya Aisha Aischa Fatima Zahra Layla Habiba
        Olga Tatjana Natalia Natalja Natalie Nathalie Svetlana Swetlana Irina Jelena Ljudmila Galina Oksana Alena Anastasia
        Daria Darja Polina Sofija Ksenia Xenia Kateryna Katerina Olena Iryna Yulia Julija Mariya Tetiana Halyna Zofia Agnieszka
        Katarzyna Małgorzata Malgorzata Joanna Magdalena Ewa Aleksandra Monika Dorota Beata Justyna Karolina Weronika Wiktoria
        Kinga Natalia Marta Paulina Linh Huong Hoa Ngoc Trang Thao Anh Minh Duc Tuan Quang Thanh
        Jasper Joris Jonte Mika Mikkel Bjarne Fiete Jarne Keno Lasse Tjark Hauke Piet Lio Elian Noel Nael Malo Jona Joel
        Mats Bennet Bent Juri Yuri Henrik Hendrik Frederik Friedrik Dominik Patrik Arthur Artur Timur Theodor Konrad Leopold
        Ludwig Albert Emilio Enzo Gabriel Michel Laurin Lorenz Benedikt Korbinian Quirin Vitus Severin Kaspar Caspar
        Damian Dorian Adrian Fabio Marius Moritz Vincent Kian Tyler Liam Elyas Ilyas Yasin Yassin Emre Kerem Deniz Eren
        Efe Mert Berat Baran Burak Furkan Kaan Onur Serkan Tarik Tarek Karim Kareem Khalid Hassan Hussein Hasan Hüseyin Osman
        Bilal Amir Samir Sami Rami Ahmad Ahmed Mahmoud Mahmud Youssef Jusuf Abdullah Abdul Idris Ilias Elias Ilja Ilya Artjom
        Artem Maxim Maksim Nikita Daniil Danylo Bohdan Taras Andrij Oleksandr Dmytro Mykola Wojciech Krzysztof Tomasz Paweł
        Pawel Marek Grzegorz Łukasz Lukasz Jakub Mateusz Kacper Szymon Bartosz Filip Dawid Kamil Michał Michal Radek Jiří
        Petr Pavel Tomáš Ondřej Dang Trung Phuc Khanh Bao Tien Vinh Giang Peter Paula Pauline Ronja Thilo
        Wiebke Frauke Gesa Imke Inken Silja Tomke Hilke Ilka Birte Britta Doreen Grit Kati Kathleen Madlen Manja Nadine
        Nadja Nancy Ramona Sindy Cindy Susann Sylke Yvette Jenny Jana Jeannette Josi Heidi Helga Hannelore Ilse Inge Ingeborg
        Edith Elfriede Erna Gerda Gertrud Herta Hertha Irmgard Käthe Lieselotte Liselotte Lotte Marianne Rosemarie Waltraud
        Christa Christel Gudrun Ruth Rita Helene Henriette Hildegard Annemarie Bärbel Carola Dörte Dorothea Elsa Else Emmi
        Gabi Gaby Heike Karola Katja Kerstin Larissa Leonore Lydia Margot Mareike Maike Meike Miriam Myriam Nadia Paulina
        Rebecca Rebekka Ricarda Sabrina Saskia Selina Sina Svenja Tamara Tina Verena Viola Wanda Xenia Yasmin Zora Alicia
        Alexandra Amelia Antonia Aurelia Bianca Caroline Carolin Cecilia Chiara Denise Dana Elisa Eleni Emilie Estelle Eva-Maria
        Felicitas Femke Fenja Frida Gloria Grete Hedi Henni Jette Josefa Jule Kira Klea Lara-Sophie Leila Lenja Liv Lou
        Luzie Mara Marit Marla Mathea Mieke Milla Nelly Nika Noemi Paulina Philippa Rieke Rosa Ruby Smilla Tilda Vivien Yara
        Achim Alfred Alois Andre André Armin Arno Benno Björn Bodo Burkhard Christoph Clemens Conrad Constantin Cornelius
        Dietmar Dietrich Eberhard Edgar Egon Erwin Eugen Ewald Ferdinand Franz Gerald Gernot Gottfried Gregor Guido
        Gunnar Harald Heiko Helge Herbert Hubert Jochen Josef Joseph Kurt Lothar Lutz Marc Mark Mike Nikolai Nikolaus
        Norman Oswald Ottmar Rainer Reiner Richard Roland Rolf Rudolf Rupert Siegfried Sigmund Silvio Steffen Thoralf
        Tilman Tillmann Tobi Ulf Ullrich Veit Walter Willi Willy Winfried Wolfram Yves Dustin Justin Jeremy Jeremias
        Dominic Kevin Steven Marvin Mario Maik Meik Danny Denny Sandro Silvio Benny Ricky Ronald Rocco Ronja Siegmar
""".trim().split(Regex("\\s+")).toSet()

    val last: Set<String> = """
        Müller Schmidt Schneider Fischer Weber Meyer Meier Mayer Maier Wagner Becker Schulz Schulze Hoffmann Schäfer Koch Bauer
        Richter Klein Wolf Schröder Neumann Schwarz Zimmermann Braun Krüger Hofmann Hartmann Lange Schmitt Werner Schmitz
        Krause Lehmann Schmid Köhler Herrmann König Walter Huber Kaiser Fuchs Peters Lang Scholz Möller Weiß Jung Hahn Schubert
        Vogel Friedrich Keller Günther Frank Berger Winkler Roth Beck Lorenz Baumann Franke Albrecht Schuster Simon Ludwig Böhm
        Winter Kraus Martin Schumacher Krämer Vogt Stein Jäger Otto Sommer Groß Seidel Heinrich Brandt Haas Schreiber Graf
        Schulte Dietrich Ziegler Kuhn Kühn Pohl Engel Horn Busch Bergmann Thomas Voigt Sauer Arnold Wolff Pfeiffer Yilmaz Kaya
        Demir Şahin Sahin Nguyen Kowalski Nowak Lindner Ernst Hübner Kretschmer Kretzschmar Ullrich Ulbrich Wendt Hennig Göbel
        Schütz Schütze Ackermann Böttcher Brückner Busse Dittrich Ebert Ehrlich Fiedler Förster Forster Frenzel Fritzsche
        Geißler Gerlach Glaser Götz Hanke Heinze Herzog Hesse Hildebrandt Hoppe Jakob Jahn Kaufmann Kirchner Kluge Knoll
        Köhler Kolb Kraft Kunze Lenz Lindemann Löffler Ludwig Marx Mertens Michel Mohr Morgenstern Nagel Naumann Nitsche
        Oswald Paul Petzold Pietsch Preuß Rau Reichel Reimann Reinhardt Richter Riedel Rösler Rudolph Sachse Schade Scheffler
        Schilling Schindler Schlegel Schmiedel Schneider Schönfeld Schramm Seifert Seiler Sommer Stark Steinbach Stephan Strauß
        Thiele Thiel Trautmann Unger Vetter Walther Weigel Weise Weiß Wenzel Wiesner Wilhelm Wolter Zander Zeidler Ziegler
        Lehmann Pohl Funke Bach Barth Baum Beyer Bock Dörr Eckert Fink Franz Gärtner Gebhardt Gruber Haase Hauser Heil Henke
        Hertel Heuer Hinz Hofmeister Hummel Jansen Janssen Kemper Kern Kessler Keßler Kirsch Klose Knapp Krebs Kroll Kurz Lorenz
        Maurer Menzel Metzger Möbius Nickel Oppermann Pape Peter Raab Ritter Rose Ruf Sander Schott Schulze Schwab Siebert
        Stahl Stoll Thomsen Vollmer Voß Wagner Weidner Wirth Witt Wittig Zimmer Arslan Aydın Aydin Çelik Celik Doğan Dogan
        Kılıç Kilic Koç Koc Kurt Öztürk Ozturk Özdemir Ozdemir Polat Yıldız Yildiz Yıldırım Yildirim Aksoy Erdoğan Erdogan
        Güneş Gunes Keskin Bozkurt Çetin Cetin Kaplan Özkan Ozkan Tekin Avcı Avci Ivanov Ivanova Petrov Petrova Smirnov
        Smirnova Popov Popova Kuznetsov Kuznetsova Sokolov Volkov Volkova Morozov Novikov Fedorov Schewtschenko Shevchenko
        Kovalenko Bondarenko Tkachenko Kravchenko Boyko Melnyk Wiśniewski Wisniewski Wójcik Wojcik Kowalczyk Kamiński Kaminski
        Lewandowski Zieliński Zielinski Szymański Szymanski Woźniak Wozniak Dąbrowski Dabrowski Kozłowski Kozlowski Jankowski
        Mazur Krawczyk Piotrowski Grabowski Pawłowski Pawlowski Michalski Nowakowski Novák Svoboda Novotný Dvořák Černý
        Tran Le Pham Hoang Huynh Phan Vu Vo Dang Bui Do Ho Ngo Duong Ly Rossi Russo Ferrari Esposito Bianchi Romano Colombo
        Ricci Marino Greco Bruno Gallo Conti Costa Giordano Mancini Rizzo Lombardi Moretti Garcia García Fernandez Fernández
        Lopez López Martinez Martínez Gonzalez González Rodriguez Rodríguez Sanchez Sánchez Perez Pérez Gomez Gómez Silva
        Santos Oliveira Pereira Ferreira Alves Rodrigues Almeida Haddad Khalil Hassan Hussein Ahmadi Mohammadi Hosseini Rahimi
        Karimi Rezaei Mansour Saleh Nasser Ibrahim Abdullah Popescu Ionescu Popa Stan Dumitru Horvat Kovačević Kovacevic
        Petrović Petrovic Jovanović Jovanovic Nikolić Nikolic Hoxha Krasniqi Berisha Gashi Papadopoulos Georgiou Dimitriou
    """.trim().split(Regex("\\s+")).toSet()
}
