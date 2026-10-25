package io.github.thepytales.llmhub

import io.github.thepytales.llmhub.research.BibEntry
import io.github.thepytales.llmhub.research.BibTeX
import io.github.thepytales.llmhub.research.Bibliography
import io.github.thepytales.llmhub.research.CitationFormatter
import io.github.thepytales.llmhub.research.CitationStyle
import io.github.thepytales.llmhub.research.CslJson
import io.github.thepytales.llmhub.research.EntryStatus
import io.github.thepytales.llmhub.research.FreeText
import io.github.thepytales.llmhub.research.LiteratureSources
import io.github.thepytales.llmhub.research.Marc
import io.github.thepytales.llmhub.research.Person
import io.github.thepytales.llmhub.research.RefType
import io.github.thepytales.llmhub.research.Reference
import io.github.thepytales.llmhub.research.ReferenceImport
import io.github.thepytales.llmhub.research.Ris
import io.github.thepytales.llmhub.research.ZoteroClient
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BibliographyTest {
    private val article = Reference(
        type = RefType.ARTICLE,
        authors = listOf(Person("Praetorius", "Anna-Katharina"), Person("Klieme", "Eckhard"), Person("Herbert", "Benjamin"), Person("Pinger", "Petra")),
        year = "2018",
        title = "Generic dimensions of teaching quality: The German framework of Three Basic Dimensions",
        container = "ZDM",
        volume = "50", issue = "3", pages = "407-426",
        doi = "10.1007/s11858-018-0918-4",
    )
    private val book = Reference(
        type = RefType.BOOK, authors = listOf(Person("Helmke", "Andreas")), year = "2009",
        title = "Unterrichtsqualität und Lehrerprofessionalität", edition = "2", publisher = "Klett-Kallmeyer", place = "Seelze",
    )
    private val chapter = Reference(
        type = RefType.CHAPTER, authors = listOf(Person("Terhart", "Ewald")), year = "2011", title = "Lehrerberuf und Professionalität",
        editors = listOf(Person("Helsper", "Werner"), Person("Tippelt", "Rudolf")), container = "Pädagogische Professionalität",
        pages = "202–224", publisher = "Beltz", place = "Weinheim",
    )

    @Test
    fun apaGermanAndEnglish() {
        assertEquals(
            "Praetorius, A.-K., Klieme, E., Herbert, B., & Pinger, P. (2018). Generic dimensions of teaching quality: The German framework of Three Basic Dimensions. *ZDM*, *50*(3), 407–426. https://doi.org/10.1007/s11858-018-0918-4",
            CitationFormatter.format(article, CitationStyle.APA7_DE),
        )
        assertEquals("Helmke, A. (2009). *Unterrichtsqualität und Lehrerprofessionalität* (2. Aufl.). Klett-Kallmeyer.", CitationFormatter.format(book, CitationStyle.APA7_DE))
        assertEquals("Helmke, A. (2009). *Unterrichtsqualität und Lehrerprofessionalität* (2nd ed.). Klett-Kallmeyer.", CitationFormatter.format(book, CitationStyle.APA7_EN))
        assertEquals(
            "Terhart, E. (2011). Lehrerberuf und Professionalität. In W. Helsper & R. Tippelt (Hrsg.), *Pädagogische Professionalität* (S. 202–224). Beltz.",
            CitationFormatter.format(chapter, CitationStyle.APA7_DE),
        )
    }

    @Test
    fun germanStylesWithPlace() {
        assertEquals(
            "Terhart, E. (2011): Lehrerberuf und Professionalität. In: Helsper, W./Tippelt, R. (Hrsg.): Pädagogische Professionalität. Weinheim: Beltz, S. 202–224.",
            CitationFormatter.format(chapter, CitationStyle.ZFPAED),
        )
        assertEquals("Helmke, A. (2009): Unterrichtsqualität und Lehrerprofessionalität. 2. Aufl. Seelze: Klett-Kallmeyer.", CitationFormatter.format(book, CitationStyle.ZFPAED))
        assertEquals("HELMKE, Andreas, 2009. *Unterrichtsqualität und Lehrerprofessionalität*. 2. Auflage. Seelze: Klett-Kallmeyer.", CitationFormatter.format(book, CitationStyle.DIN_ISO_690))
        val din = CitationFormatter.format(article, CitationStyle.DIN_ISO_690)
        assertTrue(din.startsWith("PRAETORIUS, Anna-Katharina et al., 2018. Generic dimensions"), din)
        assertTrue("Jg. 50, Nr. 3, S. 407–426." in din, din)
    }

    @Test
    fun englishStyles() {
        assertEquals("Helmke, A. (2009) *Unterrichtsqualität und Lehrerprofessionalität*. 2nd edn. Seelze: Klett-Kallmeyer.", CitationFormatter.format(book, CitationStyle.HARVARD))
        assertEquals("Helmke, Andreas. *Unterrichtsqualität und Lehrerprofessionalität*. 2nd ed., Klett-Kallmeyer, 2009.", CitationFormatter.format(book, CitationStyle.MLA9))
        assertTrue(CitationFormatter.format(article, CitationStyle.CHICAGO).startsWith("Praetorius, Anna-Katharina, Eckhard Klieme, Benjamin Herbert, and Petra Pinger. 2018. “Generic"))
        assertEquals(
            "[3] A.-K. Praetorius, E. Klieme, B. Herbert, and P. Pinger, “Generic dimensions of teaching quality: The German framework of Three Basic Dimensions,” *ZDM*, vol. 50, no. 3, pp. 407–426, 2018, doi: 10.1007/s11858-018-0918-4.",
            CitationFormatter.format(article, CitationStyle.IEEE, number = 3),
        )
    }

    @Test
    fun alphabeticalWithYearLetters() {
        val a = book.copy(id = "a", title = "Zweites Werk")
        val b = book.copy(id = "b", title = "Anderes Werk")
        val result = Bibliography.build(listOf(article, a, chapter, b, Reference(raw = "von Aster, M. (2020). Ein freier Text ohne Felder")).map { BibEntry(it) }, CitationStyle.APA7_DE)
        assertEquals(listOf("Helmke", "Helmke", "Praetorius", "Terhart"), result.entries.filter { !it.ref.unparsed }.map { it.ref.creators.first().family })
        assertTrue(result.lines[1].contains("(2009a). *Anderes Werk*"), result.lines[1])
        assertTrue(result.lines[2].contains("(2009b). *Zweites Werk*"), result.lines[2])
        // "von Aster" sorts under A.
        assertTrue(result.lines.indexOfFirst { "von Aster" in it } < result.lines.indexOfFirst { "Helmke" in it }, result.lines.joinToString("\n"))
    }

    @Test
    fun missingFieldsDependOnStyle() {
        val noPlace = book.copy(place = "")
        assertEquals(emptyList(), Bibliography.missing(noPlace, CitationStyle.APA7_DE))
        assertEquals(listOf("Verlagsort"), Bibliography.missing(noPlace, CitationStyle.DIN_ISO_690))
        assertEquals(listOf("Seiten"), Bibliography.missing(article.copy(pages = ""), CitationStyle.APA7_DE))
    }

    @Test
    fun bibtexRisAndCslRoundTrip() {
        val bib = """
            @article{prae2018,
              author = {Praetorius, Anna-Katharina and Klieme, Eckhard and {Deutsche Gesellschaft f{\"u}r Erziehungswissenschaft}},
              title = {Generic dimensions of teaching {quality}},
              journal = "ZDM", year = 2018, volume = {50}, number = {3}, pages = {407--426},
              doi = {https://doi.org/10.1007/s11858-018-0918-4}
            }
            @comment{ignored}
            @incollection{terhart, author={Ewald Terhart}, editor={Helsper, W. and Tippelt, R.}, booktitle={P{\"a}dagogische Professionalit{\"a}t},
              title={Lehrerberuf}, year={2011}, publisher={Beltz}, address={Weinheim}, pages={202-224}}
        """.trimIndent()
        val refs = BibTeX.parse(bib)
        assertEquals(2, refs.size)
        assertEquals(RefType.ARTICLE, refs[0].type)
        assertEquals("Deutsche Gesellschaft für Erziehungswissenschaft", refs[0].authors[2].family)
        assertTrue(refs[0].authors[2].literal)
        assertEquals("407–426", refs[0].pages)
        assertEquals("10.1007/s11858-018-0918-4", refs[0].doi)
        assertEquals("Pädagogische Professionalität", refs[1].container)
        assertEquals(Person("Terhart", "Ewald"), refs[1].authors[0])
        assertEquals(refs.map { it.title }, BibTeX.parse(BibTeX.write(refs)).map { it.title })
        val ris = Ris.write(refs)
        assertEquals(refs.map { it.copy(source = "", id = "", raw = "") }.map { it.title to it.pages }, Ris.parse(ris).map { it.title to it.pages })
        assertEquals(ReferenceImport.Kind.RIS, ReferenceImport.detect(ris))
        val csl = CslJson.write(refs)
        assertEquals(ReferenceImport.Kind.CSL_JSON, ReferenceImport.detect(csl))
        assertEquals(refs.map { it.doi }, CslJson.parse(csl).map { it.doi })
    }

    @Test
    fun freeTextIsReadIntoFields() {
        val r = FreeText.parse("Terhart, E. (2011). Lehrerberuf und Professionalität. In W. Helsper & R. Tippelt (Hrsg.), Pädagogische Professionalität (S. 202–224). Beltz.")
        assertEquals(RefType.CHAPTER, r.type)
        assertEquals("Terhart", r.authors.first().family)
        assertEquals("2011", r.year)
        assertEquals("Lehrerberuf und Professionalität", r.title)
        assertEquals("202–224", r.pages)
        val zfp = FreeText.parse("Baumert, J./Kunter, M. (2006): Stichwort: Professionelle Kompetenz von Lehrkräften. In: Zeitschrift für Erziehungswissenschaft 9, H. 4, S. 469–520.")
        assertEquals(listOf("Baumert", "Kunter"), zfp.authors.map { it.family })
        val list = ReferenceImport.splitFreeText(
            """
            Literatur
            - Helmke, A. (2009). Unterrichtsqualität und Lehrerprofessionalität.
              Klett-Kallmeyer.
            - Kounin, J. S. (2006). Techniken der Klassenführung. Waxmann.
            """.trimIndent()
        )
        assertEquals(2, list.size)
        assertTrue(list[0].endsWith("Klett-Kallmeyer."))
    }

    @Test
    fun marcRecordsOfTheDnb() {
        val xml = """<?xml version="1.0"?><searchRetrieveResponse><records><record><recordData><record xmlns="http://www.loc.gov/MARC21/slim">
            <datafield tag="020" ind1=" " ind2=" "><subfield code="a">9783780049185 kart.</subfield></datafield>
            <datafield tag="100" ind1="1" ind2=" "><subfield code="a">Helmke, Andreas</subfield><subfield code="4">aut</subfield></datafield>
            <datafield tag="245" ind1="1" ind2="0"><subfield code="a">Unterrichtsqualität und Lehrerprofessionalität :</subfield><subfield code="b">Diagnose, Evaluation und Verbesserung des Unterrichts /</subfield></datafield>
            <datafield tag="250" ind1=" " ind2=" "><subfield code="a">2. Aufl.</subfield></datafield>
            <datafield tag="264" ind1=" " ind2="1"><subfield code="a">Seelze :</subfield><subfield code="b">Klett-Kallmeyer,</subfield><subfield code="c">2009</subfield></datafield>
            </record></recordData></record></records></searchRetrieveResponse>"""
        val r = Marc.parse(xml).single()
        assertEquals("Unterrichtsqualität und Lehrerprofessionalität: Diagnose, Evaluation und Verbesserung des Unterrichts", r.title)
        assertEquals("Seelze", r.place)
        assertEquals("Klett-Kallmeyer", r.publisher)
        assertEquals("2", r.edition)
        assertEquals("9783780049185", r.isbn)
    }

    @Test
    fun zoteroItemsKeepTheirFields() {
        val item = ZoteroClient.item(chapter, "ABC123")
        val back = ZoteroClient.fromItem(item)
        assertEquals(RefType.CHAPTER, back.type)
        assertEquals(chapter.container, back.container)
        assertEquals(chapter.editors, back.editors)
        assertEquals("bookSection", item["itemType"].toString().trim('"'))
    }

    /** Fills in a reference without DOI and pages from Crossref (only with LIVE_WEB=1). */
    @Test
    fun completesMissingDetailsOnline() {
        if (System.getenv("LIVE_WEB") != "1") return
        val sources = LiteratureSources(OkHttpClient())
        val entries = runBlocking {
            Bibliography.complete(
                sources,
                listOf(
                    Reference(type = RefType.ARTICLE, authors = listOf(Person("Praetorius", "A.-K.")), year = "2018", title = "Generic dimensions of teaching quality", container = "ZDM"),
                    Reference(raw = "Helmke, A. (2009). Unterrichtsqualität und Lehrerprofessionalität."),
                ),
            )
        }
        println(Bibliography.build(entries, CitationStyle.DIN_ISO_690).let { it.markdown() + "\n" + it.report() })
        assertEquals(EntryStatus.COMPLETED, entries[0].status)
        assertEquals("10.1007/s11858-018-0918-4", entries[0].ref.doi)
        assertTrue("Seiten" in entries[0].added)
        assertTrue(entries[1].status != EntryStatus.NOT_FOUND)
    }
}
