package io.github.thepytales.llmhub

import io.github.thepytales.llmhub.local.Fit
import io.github.thepytales.llmhub.local.Hardware
import io.github.thepytales.llmhub.local.LocalCatalog
import io.github.thepytales.llmhub.transcribe.Segment
import io.github.thepytales.llmhub.transcribe.Transcript
import io.github.thepytales.llmhub.transcribe.TranscriptExport
import io.github.thepytales.llmhub.transcribe.Word
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Transcript formats and the choice of local models for typical devices of the chair. */
class TranscriptionAndLocalTest {
    private val sample = Transcript.parse(
        """{"id":"t1","title":"Interview","created":"2026-10-25T10:15:00","duration":40.0,"language":"de","model_name":"Parakeet v3",
        |"diarization":true,"speakers":{"0":"I","1":"B1"},"extra_field":"bleibt",
        |"segments":[{"start":0.5,"end":4.0,"text":"Wie haben Sie die Stunde erlebt?","speaker":0},
        |{"start":5.0,"end":12.0,"text":"Sehr abwechslungsreich, vor allem die Gruppenarbeit hat mir gefallen, weil alle etwas beitragen konnten und niemand allein blieb.","speaker":1},
        |{"start":16.0,"end":20.0,"text":"Und am Ende war ich richtig zufrieden.","speaker":1},
        |{"start":21.0,"end":23.0,"text":"Danke.","speaker":0,"words":[[21.0,22.5,"Danke."]]}],"audio":null,"version":3}""".trimMargin(),
        "t1",
    )!!

    @Test
    fun f4TimeMarks() {
        assertEquals("#00:01:23-4#", TranscriptExport.f4Mark(83.4))
        assertEquals("#01:00:00-0#", TranscriptExport.f4Mark(3600.0))
        val maxqda = TranscriptExport.maxqda(sample)
        assertTrue(maxqda.startsWith("I: Wie haben Sie die Stunde erlebt? #00:00:04-0#"), maxqda)
        assertTrue("B1: Sehr abwechslungsreich" in maxqda)
    }

    @Test
    fun dresingPehlMarksLongPausesInsideAContribution() {
        val dp = TranscriptExport.dresingPehl(sample)
        // 4 s between the two segments of B1 → (...)
        assertTrue("niemand allein blieb. (...) Und am Ende" in dp, dp)
        assertTrue("**I:** Wie haben Sie" in dp)
        assertTrue("#00:00:20-0#" in dp)
    }

    @Test
    fun subtitlesFollowAccessibilityRules() {
        val cues = TranscriptExport.cues(sample)
        assertTrue(cues.all { it.lines.size <= 2 && it.lines.all { l -> l.length <= 42 } }, cues.toString())
        assertTrue(cues.zipWithNext().all { (a, b) -> a.end <= b.start + 1e-9 }, "keine Überlappung")
        val srt = TranscriptExport.srt(sample)
        assertTrue(srt.startsWith("1\n00:00:00,500 --> "), srt)
        val vtt = TranscriptExport.vtt(sample)
        assertTrue(vtt.startsWith("WEBVTT") && "<v B1>" in vtt, vtt)
    }

    @Test
    fun savingKeepsUnknownFields() {
        val edited = sample.copy(title = "Neu", segments = sample.segments.map { if (it.id == 0) it.copy(text = "Geändert?") else it })
        val back = Transcript.parse(edited.toJson().toString(), "t1")!!
        assertEquals("Neu", back.title)
        assertEquals("Geändert?", back.segments.first().text)
        assertEquals(JsonPrimitive("bleibt"), (edited.toJson() as JsonObject)["extra_field"])
        assertEquals(listOf(Word(21.0, 22.5, "Danke.")), back.segments.last().words)
        assertEquals("B1", back.speakerName(1))
    }

    private fun ids(hw: Hardware) = LocalCatalog.recommended(hw).map { it.id }

    @Test
    fun oldThinkPadGetsSmallModels() {
        val hw = Hardware(phone = false, ramGb = 8.0, cores = 2, cpuName = "Intel Core i5-7200U")
        val rec = ids(hw)
        assertTrue(rec.isNotEmpty(), "mindestens ein Vorschlag")
        assertTrue(rec.all { LocalCatalog.get(it)!!.needGb <= 4.2 }, rec.toString())
        assertEquals(Fit.TOO_BIG, LocalCatalog.fit(LocalCatalog.get("qwen3.6-35b-a3b")!!, hw))
    }

    @Test
    fun officeLaptopWith16GbGetsASmallFastAllrounderAndGemmaForTexts() {
        // Modellkatalog nach Zweck (AP-106): the allrounder first, sized by the speed forecast (AP-105).
        val hw = Hardware(phone = false, ramGb = 16.0, cores = 4)
        val rec = LocalCatalog.recommended(hw, setOf(io.github.thepytales.llmhub.local.Purpose.ALLROUND, io.github.thepytales.llmhub.local.Purpose.TEXT))
        assertTrue(io.github.thepytales.llmhub.local.Purpose.ALLROUND in rec.first().purposes && rec.first().needGb <= 4.2, rec.toString())
        assertTrue(rec.any { it.family == "Gemma" && it.needGb <= 6.0 }, rec.toString())
    }

    @Test
    fun gamingPcGetsTheLargeModel() {
        val rec = ids(Hardware(phone = false, ramGb = 32.0, cores = 8, gpuName = "NVIDIA GeForce RTX 4090", vramGb = 24.0))
        assertEquals("qwen3.6-27b", rec.first(), rec.toString())
    }

    @Test
    fun onePlusGetsAPhoneSizedModel() {
        val hw = Hardware(phone = true, ramGb = 16.0, cores = 8, cpuName = "Qualcomm SM8850")
        val rec = ids(hw)
        assertTrue(rec.all { LocalCatalog.get(it)!!.needGb <= 6.0 }, rec.toString())
        assertFalse("qwen3.6-35b-a3b" in rec)
        assertEquals(Fit.SLOW, LocalCatalog.fit(LocalCatalog.get("qwen3.5-9b")!!, hw))
    }

    @Test
    fun localModelIdsAreKnownToTheCatalog() {
        val info = io.github.thepytales.llmhub.data.ModelCatalog.info("local/gemma-4-e4b")
        assertEquals("Gemma 4 E4B – Lora Edition", info.displayName)
        assertTrue(io.github.thepytales.llmhub.data.Privacy.isLocal(info.id))
    }
}
