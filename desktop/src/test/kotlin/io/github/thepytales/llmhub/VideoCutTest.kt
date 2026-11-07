package io.github.thepytales.llmhub

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import io.github.thepytales.llmhub.data.AppSettings
import io.github.thepytales.llmhub.ui.LlmHubTheme
import io.github.thepytales.llmhub.ui.VideoHub
import io.github.thepytales.llmhub.ui.VideoScreen
import io.github.thepytales.llmhub.video.AudioClip
import io.github.thepytales.llmhub.video.AudioSync
import io.github.thepytales.llmhub.video.LayerClip
import io.github.thepytales.llmhub.video.Marker
import io.github.thepytales.llmhub.video.Media
import io.github.thepytales.llmhub.video.PoolItem
import io.github.thepytales.llmhub.video.SubtitleCue
import io.github.thepytales.llmhub.video.TextOverlay
import io.github.thepytales.llmhub.video.TrackState
import io.github.thepytales.llmhub.video.VideoClip
import io.github.thepytales.llmhub.video.VideoEdit
import io.github.thepytales.llmhub.video.VideoProject
import io.github.thepytales.llmhub.video.VideoRender
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * AP-53 Video: Schnittwerkzeuge – blade, insert/overwrite from the source monitor, ripple, roll and slip trimming, the
 * magnet timeline, linked picture and sound, synchronising a second sound source over the waveform, markers and the
 * keys of the editor. Pictures in build/video-cut.
 */
@OptIn(ExperimentalTestApi::class)
class VideoCutTest {
    private val base = Files.createTempDirectory("adeu-video-cut").toFile()
    private val out = File("build/video-cut").apply { mkdirs() }

    private fun ffmpeg(): String? = Media.ffmpeg()?.takeIf { runCatching { ProcessBuilder(it, "-version").start().waitFor(10, TimeUnit.SECONDS) }.getOrNull() == true }

    private fun clip(len: Double, name: String = "a") = VideoClip(path = "/x/$name.mp4", inSec = 0.0, outSec = len)

    private fun lecture() = VideoProject(
        title = "Vortrag",
        clips = listOf(clip(10.0, "a"), clip(10.0, "b"), clip(10.0, "c")),
        layers = listOf(LayerClip(path = "/x/webcam.mp4", startSec = 22.0, outSec = 5.0)),
        audio = listOf(AudioClip(path = "/x/musik.mp3", startSec = 25.0, outSec = 4.0)),
        overlays = listOf(TextOverlay(text = "Kapitel 3", startSec = 21.0)),
        subtitles = listOf(SubtitleCue(23.0, 24.0, "Hallo")),
        markers = listOf(Marker(sec = 5.0, note = "Anfang"), Marker(sec = 24.0, note = "Ende")),
    )

    @Test
    fun bladeSplitsEveryKindOfPiece() {
        val p = lecture()
        val (a, right) = assertNotNull(VideoEdit.split(p, VideoEdit.Clip(p.clips[1].id), 14.0))
        assertEquals(listOf(10.0, 4.0, 6.0, 10.0), a.clips.map { it.length })
        assertEquals(right, a.clips[2].id)
        assertEquals(4.0, a.clips[2].inSec, 1e-9)
        val (b, _) = assertNotNull(VideoEdit.split(a, VideoEdit.Layer(p.layers[0].id), 24.0))
        assertEquals(listOf(22.0 to 2.0, 24.0 to 3.0), b.layers.map { it.startSec to it.length })
        assertEquals(2.0, b.layers[1].inSec, 1e-9)
        val (c, _) = assertNotNull(VideoEdit.split(b, VideoEdit.Sound(p.audio[0].id), 27.0))
        assertEquals(2, c.audio.size)
        // A cut right at an existing one does nothing; a locked track neither.
        assertNull(VideoEdit.split(c, VideoEdit.Clip(c.clips[0].id), 10.0))
        assertNull(VideoEdit.split(VideoEdit.setTrack(p, "V1") { it.copy(locked = true) }, VideoEdit.Clip(p.clips[0].id), 5.0))
    }

    @Test
    fun rippleTrimAndDeleteMoveEverythingAfterWithTheMagnet() {
        val p = lecture()
        // Clip b 3 s shorter at its end: c and everything on the other tracks after 20 s move 3 s to the left.
        val r = VideoEdit.rippleTrim(p, p.clips[1].id, start = false, delta = -3.0)
        assertEquals(listOf(0.0, 10.0, 17.0), r.starts())
        assertEquals(19.0, r.layers[0].startSec, 1e-9)
        assertEquals(22.0, r.audio[0].startSec, 1e-9)
        assertEquals(18.0, r.overlays[0].startSec, 1e-9)
        assertEquals(20.0 to 21.0, r.subtitles[0].startSec to r.subtitles[0].endSec)
        assertEquals(listOf(5.0, 21.0), r.markers.map { it.sec }, "der Marker vor dem Schnitt bleibt")
        // Without the magnet the other tracks stay.
        val off = VideoEdit.rippleTrim(p.copy(magnet = false), p.clips[1].id, start = false, delta = -3.0)
        assertEquals(22.0, off.layers[0].startSec, 1e-9)
        // Start edge: In moves, the clip gets shorter, the rest moves on.
        val s = VideoEdit.rippleTrim(p, p.clips[2].id, start = true, delta = 2.0)
        assertEquals(2.0, s.clips[2].inSec, 1e-9)
        assertEquals(28.0, s.duration, 1e-9)
        // Out never past the end of the file.
        assertEquals(12.0, VideoEdit.rippleTrim(p, p.clips[0].id, start = false, delta = 5.0, sourceLength = 12.0).clips[0].outSec, 1e-9)
        // Delete clip b: c closes up, the rest follows; a locked track stays.
        val d = VideoEdit.delete(p.copy(trackStates = mapOf("A1" to TrackState(locked = true))), VideoEdit.Clip(p.clips[1].id))
        assertEquals(listOf(0.0, 10.0), d.starts())
        assertEquals(12.0, d.layers[0].startSec, 1e-9)
        assertEquals(25.0, d.audio[0].startSec, 1e-9, "A1 gesperrt")
        // On a free track the magnet closes the gap there.
        val two = p.copy(layers = p.layers + LayerClip(path = "/x/logo.png", startSec = 27.0, outSec = 2.0))
        val dl = VideoEdit.delete(two, VideoEdit.Layer(p.layers[0].id))
        assertEquals(22.0, dl.layers.single().startSec, 1e-9)
        assertEquals(27.0, VideoEdit.delete(two.copy(magnet = false), VideoEdit.Layer(p.layers[0].id)).layers.single().startSec, 1e-9)
        assertEquals(listOf(0.0, 5.0), VideoEdit.closeGaps(two, "V2").layers.map { it.startSec })
    }

    @Test
    fun rollAndSlipKeepTheTimeline() {
        val p = VideoProject(title = "T", clips = listOf(VideoClip(path = "/x/a.mp4", inSec = 5.0, outSec = 15.0), VideoClip(path = "/x/b.mp4", inSec = 5.0, outSec = 15.0)))
        val r = VideoEdit.roll(p, p.clips[0].id, 2.0)
        assertEquals(15.0 to 17.0, r.clips[0].inSec + 10.0 to r.clips[0].outSec)
        assertEquals(7.0, r.clips[1].inSec, 1e-9)
        assertEquals(p.duration, r.duration, 1e-9)
        // Not further than the second clip's start in its file.
        assertEquals(0.0, VideoEdit.roll(p, p.clips[0].id, -8.0).clips[1].inSec, 1e-9)
        val s = VideoEdit.slip(p, VideoEdit.Clip(p.clips[1].id), -7.0)
        assertEquals(0.0 to 10.0, s.clips[1].inSec to s.clips[1].outSec, "nicht vor den Anfang der Datei")
        assertEquals(p.starts(), s.starts())
    }

    @Test
    fun insertAndOverwriteFromTheSourceMonitor() {
        val p = lecture()
        val piece = VideoClip(path = "/x/folie.png", inSec = 0.0, outSec = 4.0)
        // Insert at 15 s: b is split, everything after moves 4 s on.
        val i = VideoEdit.insert(p, piece, 15.0)
        assertEquals(listOf(10.0, 5.0, 4.0, 5.0, 10.0), i.clips.map { it.length })
        assertEquals("/x/folie.png", i.clips[2].path)
        assertEquals(26.0, i.layers[0].startSec, 1e-9)
        assertEquals(34.0, i.duration, 1e-9)
        // Overwrite at 8 s: 8–12 s become the new piece, nothing after moves.
        val o = VideoEdit.overwrite(p, piece, 8.0)
        assertEquals(listOf(0.0, 8.0, 12.0, 20.0), o.starts())
        assertEquals(listOf(8.0, 4.0, 8.0, 10.0), o.clips.map { it.length })
        assertEquals(2.0, o.clips[2].inSec, 1e-9)
        assertEquals(p.duration, o.duration, 1e-9)
        assertEquals(22.0, o.layers[0].startSec, 1e-9)
        // Past the end it gets longer.
        assertEquals(32.0, VideoEdit.overwrite(p, piece, 28.0).duration, 1e-9)
    }

    @Test
    fun linkedSoundFollowsItsClip() {
        val p = VideoProject(title = "T", clips = listOf(clip(10.0, "a"), clip(10.0, "kamera")))
        val (d, sid) = assertNotNull(VideoEdit.detachSound(p, p.clips[1].id))
        assertTrue(d.clips[1].muted)
        val s = d.audio.single { it.id == sid }
        assertEquals(10.0 to 1, s.startSec to s.track)
        // The first clip gets shorter: the camera clip and its sound move together.
        val t = VideoEdit.rippleTrim(d, d.clips[0].id, start = false, delta = -4.0)
        assertEquals(6.0, t.audio.single().startSec, 1e-9)
        // The camera clip is trimmed at its start: the sound too.
        val u = VideoEdit.rippleTrim(t, t.clips[1].id, start = true, delta = 3.0)
        assertEquals(3.0 to 10.0, u.audio.single().inSec to u.audio.single().outSec)
        // Reordered: the sound goes along.
        val r = u.copy(clips = u.clips.reversed()).linked()
        assertEquals(0.0, r.audio.single().startSec, 1e-9)
        // Split: each part keeps its sound; deleting a part takes its sound.
        val (sp, _) = assertNotNull(VideoEdit.splitV1(r, 3.0))
        assertEquals(2, sp.audio.size)
        assertEquals(listOf(0.0, 3.0), sp.audio.map { it.startSec })
        val del = VideoEdit.delete(sp, VideoEdit.Clip(sp.clips[0].id))
        assertEquals(1, del.audio.size)
        assertEquals(0.0 to 6.0, del.audio.single().startSec to del.audio.single().inSec)
        // A lapel microphone 1.7 s behind the camera: linked with its offset.
        val mic = AudioClip(path = "/x/mikro.wav", startSec = 0.0, outSec = 60.0, track = 2)
        val m = VideoEdit.link(p.copy(audio = listOf(mic)), p.clips[1].id, mic.id, offset = 1.7, muteClip = true)
        val linked = m.audio.single()
        assertEquals(10.0 to 1.7, linked.startSec to linked.inSec)
        assertEquals(11.7, linked.outSec, 1e-9)
        assertTrue(m.clips[1].muted)
        // A microphone started after the camera (negative offset): it begins later on the timeline.
        val late = VideoEdit.link(p.copy(audio = listOf(mic)), p.clips[1].id, mic.id, offset = -2.0).audio.single()
        assertEquals(12.0 to 0.0, late.startSec to late.inSec)
        // Unlinked it stays where it is; a link to a deleted clip is dropped.
        assertEquals(10.0, VideoEdit.unlink(m, linked.id).audio.single().startSec, 1e-9)
        assertNull(m.copy(clips = m.clips.take(1)).linked().audio.single().linkedTo)
    }

    @Test
    fun markersSnapAndChapters() {
        val p = lecture()
        val (q, id) = VideoEdit.addMarker(p, 12.0, "Versuch")
        assertEquals(listOf(5.0, 12.0, 24.0), q.markers.map { it.sec })
        assertEquals(id, VideoEdit.addMarker(q, 12.02).second, "kein zweiter Marker an derselben Stelle")
        assertEquals(10.0, VideoEdit.snap(10.3, VideoEdit.snapPoints(p), 0.5), 1e-9)
        assertEquals(10.8, VideoEdit.snap(10.8, VideoEdit.snapPoints(p), 0.5), 1e-9)
        assertEquals("0:00 Beginn\n0:05 Anfang\n0:12 Versuch\n0:24 Ende", VideoEdit.youtubeChapters(q))
    }

    @Test
    fun crossCorrelationFindsTheOffset() {
        // Envelopes with bursts at irregular times; the second recording 1.73 s later, quieter and with noise.
        val rate = AudioSync.RATE
        val n = 60 * rate
        val rnd = java.util.Random(7)
        val bursts = (0 until 40).map { rnd.nextInt(n - 300) }
        val cam = FloatArray(n) { 0.02f }
        bursts.forEach { b -> for (k in 0 until 12) cam[b + k] = 0.8f }
        val lag = 173
        val mic = FloatArray(n) { i -> val j = i - lag; (if (j in 0 until n) cam[j] * 0.4f else 0.01f) + 0.02f * rnd.nextFloat() }
        assertEquals(1.73, assertNotNull(AudioSync.offset(cam, mic)), 0.02)
        assertEquals(-1.73, assertNotNull(AudioSync.offset(mic, cam)), 0.02)
        // Nothing in common: no offset.
        val other = FloatArray(n) { i -> 0.3f + 0.3f * sin(i * 0.37f) * rnd.nextFloat() }
        assertNull(AudioSync.offset(cam, other))
    }

    /**
     * Fertig, wenn: Ein Vortrag mit zwei Tonquellen lässt sich über Quellmonitor und Ripple-Trimmen schneiden, ohne Lücken
     * von Hand zu schließen.
     */
    @Test
    fun lectureWithTwoSoundSourcesIsCutWithoutGaps(): Unit = runBlocking {
        val exe = ffmpeg() ?: return@runBlocking
        fun make(name: String, vararg args: String): File {
            val f = File(base, name)
            ProcessBuilder(listOf(exe, "-nostdin", "-hide_banner", "-loglevel", "error", "-y") + args.toList() + f.absolutePath).redirectErrorStream(true).start().waitFor(120, TimeUnit.SECONDS)
            assertTrue(f.isFile && f.length() > 0, name)
            return f
        }
        // Speech-like bursts at irregular times (the same on both recordings); the microphone was started 1.7 s earlier.
        val speech = "0.7*sin(2*PI*700*t)*gt(sin(2*PI*0.23*t)*sin(2*PI*0.71*t+1)*sin(2*PI*1.9*t+2),0.25)"
        val camera = make(
            "kamera.mp4", "-f", "lavfi", "-i", "testsrc2=size=640x360:rate=25:duration=24", "-f", "lavfi", "-i", "aevalsrc='$speech':s=48000:d=24",
            "-c:v", "libx264", "-preset", "ultrafast", "-pix_fmt", "yuv420p", "-c:a", "aac", "-shortest",
        )
        val mic = make(
            "mikro.wav", "-i", camera.absolutePath, "-f", "lavfi", "-i", "anoisesrc=a=0.01:d=27:r=48000",
            "-filter_complex", "[0:a]adelay=1700|1700,volume=0.5[s];[s][1:a]amix=inputs=2:duration=first[m]", "-map", "[m]", "-ac", "1",
        )
        val slide = make("folie.png", "-f", "lavfi", "-i", "color=c=white:s=1280x720", "-frames:v", "1")

        // The media pool, the camera whole on V1, the microphone on A2.
        var p = VideoEdit.addToPool(VideoProject(title = "Vortrag mit Mikrofon"), listOf(camera.absolutePath, mic.absolutePath, slide.absolutePath))
        p = VideoEdit.insert(p, VideoClip(path = camera.absolutePath, inSec = 0.0, outSec = 24.0), 0.0)
        val micClip = AudioClip(path = mic.absolutePath, startSec = 0.0, outSec = 24.0, volume = 1f, fadeSec = 0.0, track = 2)
        p = p.copy(audio = listOf(micClip), audioTracks = 2)
        // Synchronised over the waveform: 1.7 s.
        p = assertNotNull(AudioSync.synchronise(p, p.clips[0].id, micClip.id, muteClip = true))
        val synced = p.audio.single()
        assertEquals(1.7, synced.linkOffset, 0.05, "Versatz gefunden")
        assertTrue(p.clips[0].muted)

        // Cut out 11–14 s: blade twice, delete the middle – the magnet closes the gap, the microphone follows.
        p = assertNotNull(VideoEdit.splitV1(p, 11.0)).first
        p = assertNotNull(VideoEdit.splitV1(p, 14.0)).first
        assertEquals(3, p.audio.size, "jeder Teil hat seinen Mikrofonton")
        p = VideoEdit.delete(p, VideoEdit.Clip(p.clips[1].id))
        assertEquals(listOf(0.0, 11.0), p.starts())
        assertEquals(21.0, p.duration, 1e-9)
        // Ripple trim: the end of the first part 1 s earlier – the rest follows, no gap.
        p = VideoEdit.rippleTrim(p, p.clips[0].id, start = false, delta = -1.0)
        assertEquals(listOf(0.0, 10.0), p.starts())
        // A slide from the source monitor (In/Out 0–3 s) inserted at the cut.
        val pool = p.media.single { it.path == slide.absolutePath }.copy(inSec = 0.0, outSec = 3.0)
        p = VideoEdit.insert(p, VideoClip(path = pool.path, inSec = pool.inSec!!, outSec = pool.outSec!!), 10.0)
        assertEquals(listOf(0.0, 10.0, 13.0), p.starts())
        // Every microphone piece lies where its camera piece is and at the matching time of its file.
        p.audio.forEach { a ->
            val c = p.clips.single { it.id == a.linkedTo }
            assertEquals(p.clipStart(c.id)!!, a.startSec, 1e-9)
            assertEquals(c.inSec + synced.linkOffset, a.inSec, 1e-9)
        }
        // No gap anywhere on V1: each clip starts where the one before ends.
        p.starts().zipWithNext().forEachIndexed { i, (a, b) -> assertEquals(a + p.clips[i].length, b, 1e-9) }

        // Export: the length is right, and the sound of the first part lies on the camera's events (microphone in sync).
        val target = File(out, "vortrag-zwei-tonquellen.mp4")
        VideoRender.render(p.copy(fadeEnds = false), VideoRender.preset("opal"), target) { }
        val info = assertNotNull(Media.probe(target))
        assertEquals(p.duration, info.duration, 0.35)
        assertTrue(info.hasAudio)
        val exported = assertNotNull(AudioSync.envelope(target))
        val cam = assertNotNull(AudioSync.envelope(camera))
        val firstPart = exported.copyOf(10 * AudioSync.RATE)
        val camPart = cam.copyOf(10 * AudioSync.RATE)
        assertEquals(0.0, assertNotNull(AudioSync.offset(camPart, firstPart, maxLagSec = 3.0)), 0.06, "Mikrofon im Export lippensynchron")
    }

    private fun SkikoComposeUiTest.settle(rounds: Int = 8) {
        repeat(rounds) { Thread.sleep(80); mainClock.advanceTimeBy(300) }
        waitForIdle()
    }

    @Test
    fun editorKeysToolsAndTrackHeads() = runSkikoComposeUiTest(Size(1500f, 980f), Density(1f)) {
        mainClock.autoAdvance = false
        val c = AppContainer(android.content.Context(File(base, "files").apply { mkdirs() }, File(base, "cache").apply { mkdirs() }))
        c.appContext.getSharedPreferences("ui_state", 0).edit().putBoolean("videoSetupSeen", true).apply()
        val p = VideoProject(
            title = "Schnitt",
            clips = listOf(VideoClip(path = File(base, "fehlt.mp4").absolutePath, inSec = 0.0, outSec = 30.0)),
            layers = listOf(LayerClip(path = File(base, "webcam.mp4").absolutePath, startSec = 2.0, outSec = 10.0)),
            audio = listOf(AudioClip(path = File(base, "mikro.wav").absolutePath, startSec = 0.0, outSec = 30.0, track = 1)),
            markers = listOf(Marker(sec = 20.0, note = "Fragen")),
            media = listOf(PoolItem(path = File(base, "fehlt.mp4").absolutePath), PoolItem(path = File(base, "webcam.mp4").absolutePath, bin = "Kamera")),
            bins = listOf("Kamera"),
        )
        VideoHub.store.save(p)
        val owner = object : androidx.lifecycle.LifecycleOwner {
            val registry = androidx.lifecycle.LifecycleRegistry.createUnsafe(this).apply { currentState = androidx.lifecycle.Lifecycle.State.RESUMED }
            override val lifecycle: androidx.lifecycle.Lifecycle get() = registry
        }
        setContent {
            androidx.compose.runtime.CompositionLocalProvider(androidx.lifecycle.compose.LocalLifecycleOwner provides owner) {
                LlmHubTheme(AppSettings()) {
                    androidx.compose.material3.Surface(color = androidx.compose.material3.MaterialTheme.colorScheme.background) { VideoScreen(c, onBack = {}) }
                }
            }
        }
        settle()
        onNodeWithText("Schnitt").performClick()
        settle(12)
        assertTrue(onAllNodesWithText("Medienpool").fetchSemanticsNodes().isNotEmpty(), "Medienpool da")
        ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(out, "editor-spuren.png"))
        fun stored() = VideoHub.store.get(p.id)!!
        // F1 (or ?) shows the keys.
        onRoot().performKeyInput { pressKey(Key.F1) }
        settle(3)
        assertTrue(onAllNodesWithText("Tastenkürzel im Videoeditor").fetchSemanticsNodes().isNotEmpty())
        onNodeWithText("Schließen").performClick()
        settle(3)
        // Blade (B), then a click into V1 at about 15 s: two clips.
        onRoot().performKeyInput { pressKey(Key.B) }
        settle(2)
        val zoom = SemanticsMatcher("Zoom-Regler") { it.config.getOrNull(SemanticsProperties.ProgressBarRangeInfo)?.range == 1f..24f }
        val rowBottom = onAllNodes(zoom)[0].fetchSemanticsNode().boundsInRoot.bottom
        // Ruler 22, titles 26 + 3, V2 40 + 3, then V1 (56).
        val v1 = rowBottom + 22f + 29f + 43f + 28f
        val width = onRoot().fetchSemanticsNode().boundsInRoot.width
        val x15 = 118f + (width - 118f - 20f) / 2f
        onRoot().performTouchInput { click(Offset(x15, v1)) }
        settle(6)
        assertEquals(2, stored().clips.size, "Klinge hat geschnitten")
        // M sets a marker at the playhead, N switches the magnet off.
        onRoot().performKeyInput { pressKey(Key.A) }
        onRoot().performKeyInput { pressKey(Key.M) }
        settle(2)
        onRoot().performKeyInput { pressKey(Key.N) }
        settle(6)
        assertEquals(2, stored().markers.size)
        assertTrue(!stored().magnet)
        // Lock V1: the blade does nothing there.
        onNodeWithContentDescription("Spur V1 sperren").performClick()
        settle(4)
        assertTrue(stored().track("V1").locked)
        onRoot().performKeyInput { pressKey(Key.B) }
        onRoot().performTouchInput { click(Offset(x15 - 150f, v1)) }
        settle(6)
        assertEquals(2, stored().clips.size, "gesperrt")
        ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(out, "editor-geschnitten.png"))
        base.deleteRecursively()
    }
}
