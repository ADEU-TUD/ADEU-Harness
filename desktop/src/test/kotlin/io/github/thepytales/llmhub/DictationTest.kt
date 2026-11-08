package io.github.thepytales.llmhub

import io.github.thepytales.llmhub.audio.Dictation
import io.github.thepytales.llmhub.ui.FlowSeg
import io.github.thepytales.llmhub.ui.FlowState
import androidx.compose.ui.text.TextRange
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Diktat (AP-65): spoken punctuation, spaces and capitals at the cursor, a paragraph dictated into the document editor. */
class DictationTest {
    @Test
    fun spokenPunctuationBecomesMarks() {
        assertEquals("Das ist ein Test.", Dictation.punctuate("Das ist ein Test Punkt"))
        // Whisper writes its own marks: next to a spoken one they go.
        assertEquals("Sehr geehrte Damen und Herren,\nVielen Dank für Ihre Nachricht.", Dictation.punctuate("Sehr geehrte Damen und Herren, Komma. Neuer Absatz. Vielen Dank für Ihre Nachricht. Punkt."))
        assertEquals("Kommen Sie morgen? Ja!", Dictation.punctuate("kommen Sie morgen Fragezeichen ja Ausrufezeichen").replaceFirstChar { it.uppercaseChar() })
        assertEquals("Er sagte „hallo“ (leise): gut; schön – fertig.", Dictation.punctuate("Er sagte Anführungszeichen auf hallo Anführungszeichen zu Klammer auf leise Klammer zu Doppelpunkt gut Strichpunkt schön Gedankenstrich fertig Punkt"))
        assertEquals("E-Mail", Dictation.punctuate("E Bindestrich Mail"))
        // After the end of a sentence and a new paragraph the next word starts with a capital.
        assertEquals("erster Satz. Zweiter Satz.\nDritter", Dictation.punctuate("erster Satz Punkt zweiter Satz Punkt neue Zeile dritter"))
        // English.
        assertEquals("hello, world.\nNext one?", Dictation.punctuate("hello comma world period new paragraph next one question mark"))
    }

    @Test
    fun theNounStaysANoun() {
        assertEquals("Das ist der wichtigste Punkt.", Dictation.punctuate("Das ist der wichtigste Punkt Punkt"))
        assertEquals("Er kommt auf den Punkt.", Dictation.punctuate("Er kommt auf den Punkt Punkt"))
        assertEquals("Im dritten Absatz steht es.", Dictation.punctuate("Im dritten Absatz steht es Punkt"))
    }

    @Test
    fun spaceAndCapitalAtTheCursor() {
        assertEquals(" welt", Dictation.join("Hallo", "welt"))
        assertEquals("Neu", Dictation.join("Ende. ", "neu"))
        assertEquals(" Neu", Dictation.join("Ende.", "neu"))
        assertEquals("Test", Dictation.join("", "test"))
        assertEquals("Absatz", Dictation.join("Zeile\n", "absatz"))
        assertEquals("innen", Dictation.join("Wort (", "innen"))
        assertEquals(",", Dictation.join("Wort", ","))
    }

    /** Fertig, wenn: ein Absatz lässt sich im neuen Dokument-Editor diktieren. */
    @Test
    fun aParagraphIsDictatedIntoTheDocumentEditor() {
        val state = FlowState("# Bericht\n\nErster Absatz ist getippt.")
        // The cursor at the end of the typed paragraph.
        val text = state.segs.filterIsInstance<FlowSeg.Text>().last()
        state.active = text
        text.selection = TextRange(text.rich.text.length)
        // What the recognition on the device heard (Whisper style, with spoken commands).
        val heard = "Neuer Absatz. Dieser Absatz ist diktiert, Komma, mit Satzzeichen Punkt. Neuer Absatz. Und noch ein Satz Punkt"
        state.dictate(Dictation.punctuate(heard))
        val md = state.markdown()
        assertTrue(md.contains("Erster Absatz ist getippt.\n\nDieser Absatz ist diktiert, mit Satzzeichen.\n\nUnd noch ein Satz."), md)
        assertTrue(md.startsWith("# Bericht"), md)
    }
}
