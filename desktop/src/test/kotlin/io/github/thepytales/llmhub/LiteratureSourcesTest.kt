package io.github.thepytales.llmhub

import io.github.thepytales.llmhub.research.Atom
import io.github.thepytales.llmhub.research.LitLinks
import io.github.thepytales.llmhub.research.LitSource
import io.github.thepytales.llmhub.research.LiteratureSources
import io.github.thepytales.llmhub.research.Person
import io.github.thepytales.llmhub.research.RefType
import io.github.thepytales.llmhub.research.Reference
import io.github.thepytales.llmhub.research.SearchFilter
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The further databases (SLUB, Europe PMC, arXiv, DOAJ) and the links to open what was found. */
class LiteratureSourcesTest {
    @Test
    fun parsesSourceNames() {
        assertEquals(LitSource.SLUB, LitSource.parse("slub"))
        assertEquals(LitSource.SLUB, LitSource.parse("SLUB Dresden"))
        assertEquals(LitSource.EUROPEPMC, LitSource.parse("pubmed"))
        assertEquals(LitSource.ARXIV, LitSource.parse("arXiv"))
        assertEquals(LitSource.DOAJ, LitSource.parse("doaj"))
    }

    @Test
    fun everyWorkGetsALink() {
        val oa = Reference(title = "A", doi = "10.1/x", openAccessUrl = "https://example.org/a.pdf")
        assertEquals("https://example.org/a.pdf", LitLinks.best(oa))
        assertEquals("https://doi.org/10.1/x", LitLinks.landing(oa))
        assertEquals("https://doi.org/10.1/x", LitLinks.best(Reference(title = "B", doi = "10.1/x")))
        assertEquals("https://katalog.slub-dresden.de/id/0-1", LitLinks.best(Reference(title = "C", url = "https://katalog.slub-dresden.de/id/0-1")))
        // Nothing known but the title: a search finds it.
        val bare = Reference(title = "Die Wildbienen Deutschlands", authors = listOf(Person("Westrich", "Paul")))
        assertTrue(LitLinks.best(bare).startsWith("https://scholar.google.com/scholar?q="))
        assertTrue("Westrich" in LitLinks.slub(bare))
        assertTrue(LitLinks.PORTALS.any { it.label == "ResearchGate" })
    }

    @Test
    fun readsArxivFeed() {
        val xml = """<?xml version="1.0" encoding="UTF-8"?>
            <feed xmlns="http://www.w3.org/2005/Atom" xmlns:arxiv="http://arxiv.org/schemas/atom">
              <entry>
                <id>http://arxiv.org/abs/2101.00001v1</id>
                <published>2021-01-01T00:00:00Z</published>
                <title>Counting  bees
                  with deep learning</title>
                <summary>We count bees.</summary>
                <author><name>Ada Lovelace</name></author>
                <author><name>Alan Turing</name></author>
                <arxiv:doi>10.1234/bees</arxiv:doi>
                <link href="http://arxiv.org/pdf/2101.00001v1" rel="related" title="pdf" type="application/pdf"/>
              </entry>
            </feed>"""
        val r = Atom.arxiv(xml).single()
        assertEquals("Counting bees with deep learning", r.title)
        assertEquals("2021", r.year)
        assertEquals(listOf("Lovelace", "Turing"), r.authors.map { it.family })
        assertEquals("10.1234/bees", r.doi)
        assertEquals("https://arxiv.org/pdf/2101.00001v1", r.openAccessUrl)
    }

    /** Against the real catalogues: `LIVE_WEB=1 ./gradlew -p desktop test --tests '*LiteratureSourcesTest*'`. */
    @Test
    fun liveSearches() {
        if (System.getenv("LIVE_WEB") != "1") return
        val src = LiteratureSources(OkHttpClient())
        runBlocking {
            val slub = src.slubSearch("Wildbienen", 5)
            println(slub.joinToString("\n") { "SLUB: ${it.short()} – ${it.title} · ${it.publisher} · ${it.url}" })
            assertTrue(slub.isNotEmpty() && slub.all { it.url.startsWith("https://katalog.slub-dresden.de/id/") })
            assertTrue(slub.any { it.type == RefType.BOOK && it.publisher.isNotBlank() })
            assertTrue(slub.none { it.title.endsWith("Westrich") }, "Verfasserangabe aus dem Titel entfernt")
            val pmc = src.europePmcSearch("wild bees pollination", 5, SearchFilter(fromYear = 2018))
            println(pmc.joinToString("\n") { "PMC: ${it.short()} – ${it.title} · ${LitLinks.best(it)}" })
            assertTrue(pmc.isNotEmpty() && pmc.all { (it.year.toIntOrNull() ?: 0) >= 2018 })
            val arxiv = src.arxivSearch("bee detection", 3)
            println(arxiv.joinToString("\n") { "arXiv: ${it.short()} – ${it.title} · ${it.openAccessUrl}" })
            assertTrue(arxiv.isNotEmpty())
            val doaj = src.doajSearch("bienen", 3)
            println(doaj.joinToString("\n") { "DOAJ: ${it.short()} – ${it.title} · ${LitLinks.best(it)}" })
            assertTrue(doaj.isNotEmpty())
        }
    }
}
