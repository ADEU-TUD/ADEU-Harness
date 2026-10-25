package io.github.thepytales.llmhub

import io.github.thepytales.llmhub.transcribe.ExportOptions
import io.github.thepytales.llmhub.transcribe.Transcript
import io.github.thepytales.llmhub.transcribe.TranscriptChapters
import io.github.thepytales.llmhub.transcribe.TranscriptEdit
import io.github.thepytales.llmhub.transcribe.TranscriptEdit.Stamps
import io.github.thepytales.llmhub.transcribe.TranscriptExport
import io.github.thepytales.llmhub.transcribe.TranscriptStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Editing with measured word times, time stamps, segmentation, speakers, statistics, chapters and the original version. */
class TranscriptEditTest {
    /** Two speakers; the first two segments have measured word times (one word only placed between its neighbours). */
    private val sample = Transcript.parse(
        """{"id":"t2","title":"Unterrichtsgespräch","created":"2026-10-25T10:15:00","duration":30.0,"language":"de","model_name":"Whisper Turbo",
        |"diarization":true,"speakers":{"0":"LK","1":"S1"},"word_timing":{"source":"Parakeet v3","measured":11,"respelled":1,"interpolated":1},
        |"segments":[
        |{"start":0.5,"end":6.0,"text":"Wie lange planst du? Etwa 45 Minuten.","speaker":0,
        | "words":[[0.6,0.9,"Wie"],[1.0,1.4,"lange"],[1.5,1.9,"planst"],[2.0,2.4,"du?"],[3.1,3.5,"Etwa"],[3.6,4.6,"45","s"],[4.7,5.6,"Minuten."]]},
        |{"start":7.0,"end":11.0,"text":"Ich denke ähm danach ist gut.","speaker":1,
        | "words":[[7.1,7.3,"Ich"],[7.4,7.8,"denke"],[7.8,8.2,"ähm","i"],[8.3,8.9,"danach"],[9.0,9.2,"ist"],[9.3,9.8,"gut."]]},
        |{"start":15.0,"end":20.0,"text":"Okay. Dann machen wir weiter.","speaker":0,"comment":"Lachen im Hintergrund"}
        |],"audio":null,"version":3}""".trimMargin(),
        "t2",
    )!!

    @Test
    fun wordsKeepTheirQualityAndFitTheText() {
        val tw = TranscriptEdit.textWords(sample.segments[0])
        assertEquals(7, tw.size)
        assertEquals("45", sample.segments[0].text.substring(tw[5].from, tw[5].to))
        assertEquals('s', tw[5].word!!.quality)
        assertTrue(tw[5].word!!.measured)
        assertFalse(TranscriptEdit.textWords(sample.segments[1])[2].word!!.measured)
        assertTrue(TranscriptEdit.textWords(sample.segments[2]).all { it.word == null }, "ohne Wortzeiten keine erfundenen Zeiten")
        assertEquals((12.0 / 18), sample.measuredShare, 1e-9)
        val back = Transcript.parse(sample.toJson().toString(), "t2")!!
        assertEquals('s', back.segments[0].words[5].quality)
        assertEquals("Lachen im Hintergrund", back.segments[2].comment)
    }

    @Test
    fun editingTheTextKeepsMeasuredTimes() {
        val s = TranscriptEdit.withText(sample.segments[1], "Ich denke, danach ist es gut.")
        val tw = TranscriptEdit.textWords(s)
        assertEquals(6, tw.size)
        assertEquals(7.4, tw[1].word!!.start, 1e-9, "„denke,“ bleibt gemessen")
        assertEquals(8.3, tw[2].word!!.start, 1e-9)
        assertEquals('i', tw[4].word!!.quality, "„es“ ist neu und liegt zwischen den Nachbarn")
        assertTrue(tw[4].word!!.start >= 9.2 - 1e-9 && tw[4].word!!.end <= 9.3 + 1e-9)
    }

    @Test
    fun splitUsesTheMeasuredStartOfTheWord() {
        val offset = sample.segments[0].text.indexOf("Etwa")
        val t = TranscriptEdit.split(sample, 0, offset)!!
        assertEquals(4, t.segments.size)
        assertEquals("Wie lange planst du?", t.segments[0].text)
        assertEquals(2.4, t.segments[0].end, 1e-9)
        assertEquals(3.1, t.segments[1].start, 1e-9)
        assertEquals(listOf(0, 1, 2, 3), t.segments.map { it.id })
        // Without measured times only with an explicit time (playback position)
        val o2 = sample.segments[2].text.indexOf("Dann")
        assertNull(TranscriptEdit.split(sample, 2, o2))
        val t2 = TranscriptEdit.split(sample, 2, o2, at = 16.2)!!
        assertEquals(16.2, t2.segments[3].start, 1e-9)
        assertEquals("Lachen im Hintergrund", t2.segments[2].comment)
        assertNull(t2.segments[3].comment)
    }

    @Test
    fun mergeAndStart() {
        val m = TranscriptEdit.mergeWithNext(sample, 0)!!
        assertEquals(2, m.segments.size)
        assertEquals(0.5, m.segments[0].start, 1e-9)
        assertEquals(11.0, m.segments[0].end, 1e-9)
        val tw = TranscriptEdit.textWords(m.segments[0])
        assertEquals(13, tw.size)
        assertEquals(7.1, tw[7].word!!.start, 1e-9, "Zeiten des zweiten Abschnitts bleiben")
        assertNotNull(TranscriptEdit.setStart(sample, 1, 6.5))
        assertNull(TranscriptEdit.setStart(sample, 1, 0.2), "nicht vor den vorigen Abschnitt")
    }

    @Test
    fun resegmentSplitsSentencesOnlyWhereMeasured() {
        val one = TranscriptEdit.resegment(sample, 0)
        assertEquals(listOf("Wie lange planst du?", "Etwa 45 Minuten.", "Ich denke ähm danach ist gut.", "Okay. Dann machen wir weiter."), one.segments.map { it.text })
        assertEquals(3.1, one.segments[1].start, 1e-9)
        val joined = TranscriptEdit.resegment(sample, 20)
        assertEquals(3, joined.segments.size, "gleiche Sprecher werden bis 20 s wieder verbunden, andere nicht")
    }

    @Test
    fun inlineStampsComeOnlyFromMeasurements() {
        val sentence = TranscriptEdit.inlineMarks(sample, Stamps.SENTENCE)
        assertEquals(listOf(0.5, 3.1), sentence[0].map { it.time })
        assertEquals(listOf(15.0), sentence[2].map { it.time }, "ohne Wortzeiten nur der Abschnittsanfang")
        val every = TranscriptEdit.inlineMarks(sample, Stamps.EVERY_10)
        assertEquals(listOf(0.5), every[0].map { it.time })
        assertEquals(listOf(15.0), every[2].map { it.time })
        val md = TranscriptExport.markdown(sample, ExportOptions(Stamps.SENTENCE))
        assertTrue("Wie lange planst du? (0:03) Etwa 45 Minuten." in md, md)
        assertTrue("[Kommentar: Lachen im Hintergrund]" in md)
        assertFalse("[Kommentar" in TranscriptExport.markdown(sample, ExportOptions(Stamps.PARAGRAPH, comments = false)))
        assertTrue("Wortzeiten gemessen mit Parakeet v3" in TranscriptExport.details(sample))
        // Text export without options stays as before: no stamps, no comments
        assertFalse("(0:" in TranscriptExport.plainText(sample) || "[" in TranscriptExport.plainText(sample))
    }

    @Test
    fun subtitlesUseMeasuredWordTimes() {
        val cues = TranscriptExport.cues(sample)
        assertEquals(0.6, cues.first().start, 1e-9)
    }

    @Test
    fun speakersByHandAndStatistics() {
        val solo = TranscriptEdit.removeSpeakers(sample)
        assertFalse(solo.diarized)
        val two = TranscriptEdit.addSpeaker(solo, "B1")
        assertTrue(two.diarized)
        assertEquals("B1", two.speakerName(1))
        assertTrue(two.segments.all { it.speaker == 0 })
        val merged = TranscriptEdit.mergeSpeaker(sample, 1, 0)
        assertEquals(setOf(0), merged.segments.map { it.speaker }.toSet())
        val stats = TranscriptEdit.stats(sample)
        assertEquals(2, stats.size)
        assertEquals(2, stats.first { it.speaker == 0 }.turns)
        assertEquals(1.0, stats.sumOf { it.share }, 1e-9)
        assertEquals(2, TranscriptEdit.speakerChanges(sample))
        assertEquals(2, TranscriptEdit.search(sample, "DANN").size + TranscriptEdit.search(sample, "planst").size)
    }

    @Test
    fun originalVersionCanBeRestored() {
        val edited0 = sample.copy(segments = sample.segments.drop(1))
        val edited = edited0.copy(raw = sample.withOriginal().raw, status = TranscriptStatus.CORRECTED)
        val back = Transcript.parse(edited.toJson().toString(), "t2")!!
        assertTrue(back.hasOriginal)
        assertEquals(2, back.segments.size)
        val orig = back.original()!!
        assertEquals(3, orig.segments.size)
        assertEquals(TranscriptStatus.CORRECTED, orig.status, "Status bleibt beim Verwerfen")
        assertEquals("S1", orig.speakerName(1))
    }

    @Test
    fun chaptersSnapToRealStarts() {
        val answer = """Hier die Gliederung: {"summary":"Die Lehrkraft fragt nach der Planung.","chapters":[{"start":"0:00","title":"Planung"},{"start":"0:15","title":"Weiterarbeit"},{"start":"9:99","title":"Unsinn"}]}"""
        val (summary, chapters) = TranscriptChapters.parse(answer, sample)
        assertEquals("Die Lehrkraft fragt nach der Planung.", summary)
        assertEquals(listOf(0.5, 15.0), chapters.map { it.time })
        assertEquals(listOf("Planung", "Weiterarbeit"), chapters.map { it.title })
        assertTrue(TranscriptChapters.prompt(sample)[1].content.contains("(0:03) Etwa"))
    }
}
