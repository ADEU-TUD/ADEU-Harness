package io.github.thepytales.llmhub

import io.github.thepytales.llmhub.docs.text.AutoCorrect
import io.github.thepytales.llmhub.docs.text.AutoCorrect.Rule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Autokorrektur beim Tippen (AP-125): each rule, its exceptions, and that a rule switched off stays quiet. */
class AutoCorrectTest {
    /** Types [typed] at the end of [before] and returns the text after the correction (or as typed). */
    private fun type(before: String, typed: Char, off: Set<Rule> = emptySet()): String {
        val text = before + typed
        val fix = AutoCorrect.after(text, text.length, typed, off) ?: return text
        return text.replaceRange(fix.range, fix.text)
    }

    @Test
    fun quotesAndApostrophe() {
        assertEquals("Er sagte „", type("Er sagte ", '"'))
        assertEquals("„Hallo“", type("„Hallo", '"'))
        assertEquals("geht’", type("geht", '\''))
        assertEquals("Er sagte \"", type("Er sagte ", '"', setOf(Rule.QUOTES)))
    }

    @Test
    fun dashesAndEllipsis() {
        assertEquals("Lehre – ", type("Lehre --", ' '))
        assertEquals("und dann…", type("und dann..", '.'))
        assertEquals("x--y", type("x--", 'y'))
    }

    @Test
    fun symbols() {
        assertEquals("©", type("(c", ')'))
        assertEquals("A →", type("A -", '>'))
        assertEquals("Marke™", type("Marke(tm", ')'))
    }

    @Test
    fun capitalAtTheStartOfASentence() {
        assertEquals("Das ist gut. Dann ", type("Das ist gut. dann", ' '))
        assertEquals("Heute ", type("heute", ' '))
        assertEquals("Erster Absatz.\nZweiter ", type("Erster Absatz.\nzweiter", ' '))
        // Not after abbreviations, numbers or initials.
        assertEquals("Das ist z. B. eine ", type("Das ist z. B. eine", ' '))
        assertEquals("siehe z.B. die ", type("siehe z.B. die", ' '))
        assertEquals("Am 3. januar ", type("Am 3. januar", ' '))
        assertEquals("vgl. dazu ", type("vgl. dazu", ' ').let { if (it.startsWith("V")) it else it })
        assertEquals("A. weber ", type("A. weber", ' '))
        assertEquals("www.example.de ", type("www.example.de", ' '))
    }

    @Test
    fun twoCapitalsAndTypos() {
        assertEquals("Zwei ", type("ZWei", ' '))
        assertEquals("TUD ", type("TUD", ' '))
        assertEquals("Lehre und ", type("Lehre udn", ' '))
        assertEquals("Und ", type("udn", ' '))
        assertEquals("Und,", type("Udn", ','))
        assertEquals("Lehre udn ", type("Lehre udn", ' ', setOf(Rule.TYPOS)))
    }

    @Test
    fun nothingInCodeOrMidWord() {
        assertNull(AutoCorrect.after("a\"", 2, '"', code = true))
        assertNull(AutoCorrect.after("Wort", 4, 't'))
    }

    @Test
    fun settingRoundTrip() {
        val off = setOf(Rule.SENTENCE, Rule.TYPOS)
        assertEquals(off, AutoCorrect.rulesOff(AutoCorrect.setting(off)))
        assertEquals(emptySet(), AutoCorrect.rulesOff(""))
    }
}
