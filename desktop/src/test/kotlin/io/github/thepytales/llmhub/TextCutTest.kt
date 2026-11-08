package io.github.thepytales.llmhub

import io.github.thepytales.llmhub.transcribe.Segment
import io.github.thepytales.llmhub.transcribe.Transcript
import io.github.thepytales.llmhub.transcribe.Word
import io.github.thepytales.llmhub.video.AudioClip
import io.github.thepytales.llmhub.video.Media
import io.github.thepytales.llmhub.video.TextCut
import io.github.thepytales.llmhub.video.TextOverlay
import io.github.thepytales.llmhub.video.VideoClip
import io.github.thepytales.llmhub.video.VideoProject
import io.github.thepytales.llmhub.video.VideoRender
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import kotlin.test.assertTrue

/**
 * Textbasierter Schnitt (AP-55): the transcript as text on the timeline, deleting words cuts the video, paragraphs move
 * clips, fillers and pauses go with one click, Lora's proposals are parsed into passages.
 */
class TextCutTest {
    private val path = "/videos/vortrag.mp4"

    /** Three paragraphs of words 0.4 s long with 0.1 s between them; a filler in the second, a long pause before the third. */
    private fun transcript(): Transcript {
        fun seg(id: Int, start: Double, words: List<String>, speaker: Int? = null): Segment {
            var t = start
            val ws = words.map { w -> Word(t, t + 0.4, w).also { t += 0.5 } }
            return Segment(id, ws.first().start, ws.last().end, words.joinToString(" "), speaker, ws)
        }
        val segs = listOf(
            seg(0, 1.0, listOf("Heute", "geht", "es", "um", "Wasser."), 0),
            seg(1, 5.0, listOf("Zuerst", "äh", "das", "Organisatorische", "zur", "Prüfung."), 1),
            seg(2, 9.5, listOf("Ein", "Beispiel", "aus", "dem", "Labor."), 0),
        )
        return Transcript(
            id = "t1", title = "Vortrag", created = "2026-11-08T10:00:00", duration = 12.0, language = "de", modelName = "test", device = "pc",
            speakers = mapOf(0 to "Prof. Lehmann", 1 to "Frau Nowak"), segments = segs, audio = null, folder = null, marks = emptyList(), notice = null,
            raw = JsonObject(emptyMap()),
        )
    }

    private fun project(file: String = path) = VideoProject(
        title = "Vortrag", fadeEnds = false,
        clips = listOf(VideoClip(id = "c1", path = file, inSec = 0.0, outSec = 12.0)),
        audio = listOf(AudioClip(id = "s1", path = file, startSec = 0.0, outSec = 12.0, linkedTo = "c1", volume = 1f)),
        overlays = listOf(TextOverlay(id = "o1", text = "Labor", startSec = 10.0, durationSec = 1.5)),
    ).linked()

    @Test
    fun wordsSitOnTheTimelineWithPausesFillersAndSpeakers() {
        val p = project()
        val doc = TextCut.build(p, mapOf(path to transcript()))
        assertEquals(16, doc.words)
        assertEquals(3, doc.paragraphs.size)
        assertEquals("Prof. Lehmann", doc.paragraphs[0].speaker)
        assertEquals("Frau Nowak", doc.paragraphs[1].speaker)
        // Pauses longer than 0.8 s: before the second (1.6 s) and before the third paragraph (1.6 s).
        assertEquals(2, doc.pauses().size)
        assertEquals(listOf("äh"), doc.fillers().map { doc.tokens[it].text })
        // A click on "Beispiel" jumps to 10 s; the token at 10.1 s is "Beispiel".
        val k = doc.tokens.indexOfFirst { it.text == "Beispiel" }
        assertEquals(10.0, doc.tokens[k].start, 1e-9)
        assertEquals(k, doc.at(10.1))
        // A clip that starts later in its source and plays twice as fast places its words accordingly.
        val fast = p.copy(clips = listOf(p.clips[0].copy(inSec = 5.0, fx = p.clips[0].fx.copy(speed = 2.0))))
        val d2 = TextCut.build(fast, mapOf(path to transcript()))
        assertEquals("Zuerst", d2.tokens.first().text)
        assertEquals(0.0, d2.tokens.first().start, 1e-9)
        assertEquals(0.5 / 2, d2.tokens[1].start, 1e-9)
    }

    @Test
    fun deletingWordsCutsTheVideoAndTheTimelineFollows() {
        val p = project()
        val doc = TextCut.build(p, mapOf(path to transcript()))
        // Select "das Organisatorische zur Prüfung." and delete it.
        val from = doc.tokens.indexOfFirst { it.text == "das" }
        val to = doc.tokens.indexOfFirst { it.text == "Prüfung." }
        val ranges = TextCut.ranges(doc, from, to)
        assertEquals(1, ranges.size)
        val (_, a, b) = ranges.single()
        // From just after "äh" (5.9 s, half of the 0.1 s gap kept) to the end of the pause after it (9.5 s), keeping 0.15 s.
        assertEquals(5.95, a, 1e-9)
        assertEquals(9.35, b, 1e-9)
        val q = TextCut.cut(p, ranges)
        assertEquals(2, q.clips.size, "Der Clip wird in zwei Teile geschnitten")
        assertEquals(12.0 - (b - a), q.duration, 1e-6)
        // Linked sound goes with both parts and stays in sync.
        assertEquals(2, q.audio.size)
        assertTrue(q.audio.all { s -> q.clips.any { it.id == s.linkedTo } })
        assertEquals(q.clips[1].inSec, q.audio.first { it.linkedTo == q.clips[1].id }.inSec, 1e-9)
        // The title over "Labor" moves along with the magnet.
        assertEquals(10.0 - (b - a), q.overlays.single().startSec, 1e-6)
        // The text of the new timeline no longer has the words, and "Beispiel" is where the title says.
        val d2 = TextCut.build(q, mapOf(path to transcript()))
        assertTrue(d2.tokens.none { it.text == "Organisatorische" })
        assertEquals(10.0 - (b - a), d2.tokens.first { it.text == "Beispiel" }.start, 1e-6)
    }

    @Test
    fun fillersAndPausesGoWithOneClick() {
        val p = project()
        val t = mapOf(path to transcript())
        val doc = TextCut.build(p, t)
        val noFiller = TextCut.removeFillers(p, doc)
        assertTrue(TextCut.build(noFiller, t).tokens.none { it.filler })
        assertTrue(noFiller.duration < p.duration - 0.3)
        val shortPauses = TextCut.shortenPauses(p, doc)
        // Both pauses (1.6 s each) shrink to 2 × 0.15 s.
        assertEquals(12.0 - 2 * (1.6 - 0.3), shortPauses.duration, 1e-6)
        assertTrue(TextCut.build(shortPauses, t).pauses().isEmpty())
        // Pause selected alone: only the middle goes.
        val k = doc.pauses().first()
        val r = TextCut.ranges(doc, k, k).single()
        assertEquals(doc.tokens[k].srcStart + TextCut.PAD, r.second, 1e-9)
    }

    @Test
    fun movingAParagraphMovesItsClip() {
        val p = project()
        val t = mapOf(path to transcript())
        // The example (paragraph 3) to the front.
        val q = TextCut.moveParagraph(p, t, 2, 0)
        val d2 = TextCut.build(q, t)
        assertEquals("Ein", d2.tokens.first { it.kind == TextCut.Kind.WORD }.text)
        assertEquals(listOf("Ein", "Heute", "Zuerst"), d2.paragraphs.map { d2.tokens[it.first].text })
        assertEquals(p.duration, q.duration, 1e-6)
        assertEquals(2, q.clips.size)
        // Linked sound: one piece per clip, each at its clip.
        assertTrue(q.clips.all { c -> q.audio.count { it.linkedTo == c.id } == 1 })
        // Paragraph 1 to the end.
        val r = TextCut.moveParagraph(p, t, 0, 3)
        assertEquals(listOf("Zuerst", "Ein", "Heute"), TextCut.build(r, t).let { d -> d.paragraphs.map { d.tokens[it.first].text } })
    }

    @Test
    fun loraProposalsBecomePassages() {
        val p = project()
        val doc = TextCut.build(p, mapOf(path to transcript()))
        val prompt = TextCut.prompt(doc, p.duration, "Entferne den Teil über die Organisation")
        assertTrue("2 [0:05–0:09] Frau Nowak: Zuerst äh das Organisatorische zur Prüfung." in prompt, prompt)
        val props = TextCut.parse("2: Organisatorisches", doc)
        assertEquals(1, props.size)
        assertEquals("Organisatorisches", props.single().reason)
        assertEquals("Zuerst äh das Organisatorische zur Prüfung.", doc.text(props.single().from, props.single().to))
        // Ranges and neighbours: "1-2: A" is one passage, "keine" is none, numbers out of range are ignored.
        assertEquals(1, TextCut.parse("1-2: Einleitung", doc).size)
        assertTrue(TextCut.parse("keine", doc).isEmpty())
        assertTrue(TextCut.parse("7: zu viel", doc).isEmpty())
        val two = TextCut.parse("1: Begrüßung\n3: Beispiel", doc)
        assertEquals(2, two.size)
        val q = TextCut.accept(p, doc, two.take(1))
        assertEquals("Zuerst", TextCut.build(q, mapOf(path to transcript())).tokens.first { it.kind == TextCut.Kind.WORD }.text)
    }

    /** Fertig, wenn: Ein Lehrvideo wird nur über das Transkript geschnitten, und die Zeitleiste folgt (bis in den Export). */
    @Test
    fun lectureCutOnlyThroughTheTranscriptExportsAsTheTimelineSays(): Unit = runBlocking {
        val exe = Media.ffmpeg()?.takeIf { runCatching { ProcessBuilder(it, "-version").start().waitFor(10, TimeUnit.SECONDS) }.getOrNull() == true } ?: return@runBlocking
        val dir = Files.createTempDirectory("textcut").toFile()
        try {
            val video = File(dir, "vortrag.mp4")
            ProcessBuilder(
                exe, "-nostdin", "-hide_banner", "-loglevel", "error", "-y", "-f", "lavfi", "-i", "testsrc2=size=320x180:rate=25:duration=12",
                "-f", "lavfi", "-i", "sine=f=500:d=12", "-c:v", "libx264", "-preset", "ultrafast", "-pix_fmt", "yuv420p", "-c:a", "aac", "-shortest", video.absolutePath,
            ).redirectErrorStream(true).start().waitFor(120, TimeUnit.SECONDS)
            assertTrue(video.isFile)
            val t = mapOf(video.absolutePath to transcript())
            var p = project(video.absolutePath)
            // Only through the text: the organisational part out, fillers out, pauses shorter, the example to the front.
            var doc = TextCut.build(p, t)
            p = TextCut.delete(p, doc, doc.tokens.indexOfFirst { it.text == "das" }, doc.tokens.indexOfFirst { it.text == "Prüfung." })
            doc = TextCut.build(p, t); p = TextCut.removeFillers(p, doc)
            doc = TextCut.build(p, t); p = TextCut.shortenPauses(p, doc)
            p = TextCut.moveParagraph(p, t, 2, 0)
            val text = TextCut.build(p, t)
            assertEquals("Ein Beispiel aus dem Labor. Heute geht es um Wasser. Zuerst", text.text(0, text.tokens.lastIndex))
            assertTrue(p.duration < 8.0, "kürzer: ${p.duration}")
            val out = File(dir, "schnitt.mp4")
            VideoRender.render(p, VideoRender.preset("opal"), out) { }
            val exported = Media.probe(out)?.duration ?: error("kein Export")
            assertTrue(abs(exported - p.duration) < 0.2, "Export $exported s, Zeitleiste ${p.duration} s")
        } finally {
            dir.deleteRecursively()
        }
    }

    /** The panel beside the timeline: the words are there, a click selects, „Herausschneiden“ cuts and the timeline follows. */
    @OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
    @Test
    fun transcriptPanelCutsWhatIsSelected() {
        val base = Files.createTempDirectory("textcut-ui").toFile()
        try {
            androidx.compose.ui.test.runSkikoComposeUiTest(androidx.compose.ui.geometry.Size(420f, 900f), androidx.compose.ui.unit.Density(1f)) {
                val c = AppContainer(android.content.Context(File(base, "files").apply { mkdirs() }, File(base, "cache").apply { mkdirs() }))
                val h = io.github.thepytales.llmhub.ui.History(project().copy(transcripts = mapOf(path to "t1")))
                val state = io.github.thepytales.llmhub.ui.TextCutState()
                val player = io.github.thepytales.llmhub.video.VideoPlayer()
                setContent {
                    io.github.thepytales.llmhub.ui.LlmHubTheme(io.github.thepytales.llmhub.data.AppSettings()) {
                        io.github.thepytales.llmhub.ui.TranscriptCutPanel(c, h, player, 0.0, state, onAssign = {}, load = { mapOf(path to transcript()) })
                    }
                }
                waitUntil(timeoutMillis = 5000) { onAllNodesWithText("Füllwörter entfernen (1)").fetchSemanticsNodes().isNotEmpty() }
                assertTrue(onAllNodesWithText("Heute geht es um Wasser.", substring = true).fetchSemanticsNodes().isNotEmpty())
                // Select the second paragraph through the state (as Umschalt+Klick does) and cut it.
                val doc = assertNotNull(state.doc)
                runOnIdle { state.select(doc.paragraphs[1].first, false); state.select(doc.paragraphs[1].last, true) }
                onNodeWithText("Herausschneiden (Entf)").performClick()
                waitForIdle()
                assertTrue(h.project.duration < 12.0 - 3.0, "kürzer: ${h.project.duration}")
                assertTrue(h.canUndo)
                waitUntil(timeoutMillis = 5000) { onAllNodesWithText("Organisatorische", substring = true).fetchSemanticsNodes().isEmpty() }
                player.dispose()
            }
        } finally {
            base.deleteRecursively()
        }
    }
}
