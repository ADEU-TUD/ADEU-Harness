package io.github.thepytales.llmhub

import io.github.thepytales.llmhub.data.HouseRule
import io.github.thepytales.llmhub.docs.StyleCheck
import io.github.thepytales.llmhub.docs.StyleCheck.Kind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Hausstil-Prüfung und Barrierefreiheits-Check (AP-79). */
class StyleCheckTest {
    private val doc = """
        # Handreichung Praktikum

        Die TU-Dresden bietet z.B. Beratung für Lehrer an.

        #### Ablauf

        **Anmeldung**

        Mehr dazu [hier](https://tu-dresden.de/zlsb) oder unter https://tu-dresden.de/zlsb/lehramtsstudium.

        ![](bilder/schule.png)

        # Zweiter Titel
    """.trimIndent()

    @Test
    fun findsHouseStyleWithPlaceAndFixesIt() {
        val issues = StyleCheck.check(doc, listOf(HouseRule("Lehrer", "Lehrkräfte", "Gendern")))
        val house = issues.filter { it.kind == Kind.HOUSE }
        assertTrue(house.any { "„TU-Dresden“ → „TU Dresden“" in it.text && it.where == "Absatz 2" }, house.toString())
        assertTrue(house.any { "„z.B.“ → „z. B.“" in it.text }, house.toString())
        assertTrue(house.any { "„Lehrer“ → „Lehrkräfte“ – Gendern" in it.text }, house.toString())
        val fixed = StyleCheck.apply(doc, house)
        assertTrue("Die TU Dresden bietet z. B. Beratung für Lehrkräfte an." in fixed, fixed)
        // Words that only contain the rule stay („Lehrerbildung“ is not „Lehrer“).
        assertTrue(StyleCheck.check("Die Lehrerbildung", listOf(HouseRule("Lehrer", "Lehrkräfte"))).none { it.kind == Kind.HOUSE })
    }

    @Test
    fun findsWhatKeepsScreenReaderUsersOut() {
        val access = StyleCheck.check(doc).filter { it.kind == Kind.ACCESS }.map { it.text }
        assertTrue(access.any { "springt von Ebene 1 auf Ebene 4" in it }, access.toString())
        assertTrue("Fett gesetzte Zeile statt Überschrift" in access, access.toString())
        assertTrue(access.any { "Linktext „hier“" in it }, access.toString())
        assertTrue("Nackte Adresse als Link" in access, access.toString())
        assertTrue("Bild ohne Alternativtext" in access, access.toString())
        assertTrue(access.any { "2 Hauptüberschriften" in it }, access.toString())
    }

    @Test
    fun theAutomaticFixesMakeAStructureScreenReadersCanUse() {
        val issues = StyleCheck.check(doc)
        val fixed = StyleCheck.apply(doc, issues)
        assertTrue("\n## Ablauf\n" in fixed, fixed)
        assertTrue("\n### Anmeldung\n" in fixed, fixed)
        assertTrue("[tu-dresden.de](https://tu-dresden.de/zlsb/lehramtsstudium)" in fixed, fixed)
        val again = StyleCheck.check(fixed).filter { it.fix != null }
        assertTrue(again.isEmpty(), "nach dem Beheben bleibt nichts automatisch Behebbares: $again")
    }

    @Test
    fun slidesNeedATitleEach() {
        val deck = "# Einführung\nText\n---\nNur Stichpunkte ohne Titel\n---\n# Fazit\n"
        val issues = StyleCheck.check(deck, slides = true)
        assertEquals(listOf("Folie 2"), issues.filter { it.text == "Folie ohne Titel" }.map { it.where })
    }

    @Test
    fun corporateDesignOfOfficeFiles() {
        val xml = """<w:rFonts w:ascii="Comic Sans MS"/><w:rFonts w:ascii="Noto Sans"/><w:color w:val="FF00FF"/><w:color w:val="000000"/><a:srgbClr val="00305D"/>"""
        val (fonts, colors) = StyleCheck.corporateDesign(xml, setOf("#00305D"), "Noto Sans")
        assertEquals(setOf("Comic Sans MS"), fonts)
        assertEquals(setOf("#FF00FF"), colors)
        assertFalse(StyleCheck.summary(emptyList()).contains("Funde:"))
    }
}
