package de.lokal.whisper;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class TextCleanTest {
    @Test void removesHallucinations() {
        assertEquals("", TextClean.clean("Untertitel im Auftrag des ZDF, 2021", 3));
        assertEquals("", TextClean.clean("[Musik]", 5));
        assertEquals("", TextClean.clean("  ", 5));
    }

    @Test void keepsNormalText() {
        assertEquals("Guten Morgen und willkommen.", TextClean.clean("  Guten   Morgen und willkommen. ", 4));
    }

    @Test void collapsesRepeats() {
        String t = TextClean.clean("ja ja ja ja ja ja ja ja ja ja", 5);
        assertEquals("ja ja ja", t);
        String s = TextClean.clean("Das ist gut. Das ist gut. Das ist gut. Das ist gut. Das ist gut.", 8);
        assertEquals("Das ist gut. Das ist gut. Das ist gut.", s);
    }
}
