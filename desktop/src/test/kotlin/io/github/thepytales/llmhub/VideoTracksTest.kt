package io.github.thepytales.llmhub

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toAwtImage
import io.github.thepytales.llmhub.creative.pngBytes
import io.github.thepytales.llmhub.video.AudioClip
import io.github.thepytales.llmhub.video.Frame
import io.github.thepytales.llmhub.video.LayerClip
import io.github.thepytales.llmhub.video.Marker
import io.github.thepytales.llmhub.video.Media
import io.github.thepytales.llmhub.video.TextOverlay
import io.github.thepytales.llmhub.video.TrackState
import io.github.thepytales.llmhub.video.VideoClip
import io.github.thepytales.llmhub.video.VideoEdit
import io.github.thepytales.llmhub.video.VideoPlayer
import io.github.thepytales.llmhub.video.VideoProject
import io.github.thepytales.llmhub.video.VideoRender
import io.github.thepytales.llmhub.video.VideoStore
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * AP-52 Video: mehrere Spuren und echte Vorschau – the project with video tracks V2 … (picture-in-picture, position,
 * crop, opacity) and audio tracks A1 …, old projects, the export graph, and the preview that runs through the same
 * graph as the export (needs ffmpeg). Pictures in build/video-tracks.
 */
class VideoTracksTest {
    private val base = Files.createTempDirectory("adeu-video-tracks").toFile()
    private val out = File("build/video-tracks").apply { mkdirs() }

    private fun ffmpeg(): String? = Media.ffmpeg()?.takeIf { runCatching { ProcessBuilder(it, "-version").start().waitFor(10, TimeUnit.SECONDS) }.getOrNull() == true }

    private fun make(name: String, vararg args: String): File {
        val f = File(base, name)
        val p = ProcessBuilder(listOf(ffmpeg()!!, "-nostdin", "-hide_banner", "-loglevel", "error", "-y") + args.toList() + f.absolutePath).redirectErrorStream(true).start()
        val log = p.inputStream.bufferedReader().readText()
        p.waitFor(120, TimeUnit.SECONDS)
        assertTrue(f.isFile && f.length() > 0, "Testdatei $name: $log")
        return f
    }

    /** A plain coloured video with a tone. */
    private fun colour(name: String, colour: String, sec: Int, size: String = "640x360", tone: Int = 440) = make(
        name, "-f", "lavfi", "-i", "color=c=$colour:s=$size:r=25:d=$sec", "-f", "lavfi", "-i", "sine=frequency=$tone:duration=$sec:sample_rate=48000",
        "-c:v", "libx264", "-preset", "ultrafast", "-pix_fmt", "yuv420p", "-c:a", "aac", "-shortest",
    )

    private fun rgb(img: BufferedImage, fx: Double, fy: Double): Triple<Int, Int, Int> {
        val c = img.getRGB((img.width * fx).toInt().coerceIn(0, img.width - 1), (img.height * fy).toInt().coerceIn(0, img.height - 1))
        return Triple(c shr 16 and 0xff, c shr 8 and 0xff, c and 0xff)
    }

    private fun ImageBitmap.awt(): BufferedImage = toAwtImage()

    private fun green(c: Triple<Int, Int, Int>) = c.second > 150 && c.first < 90 && c.third < 90
    private fun red(c: Triple<Int, Int, Int>) = c.first > 150 && c.second < 90 && c.third < 90

    @Test
    fun oldProjectsLoadAndTheirSoundIsOnA1() {
        // A project file from 1.0.56: no tracks, no layers, no pool.
        val dir = File(base, "files/videos").apply { mkdirs() }
        File(dir, "alt.json").writeText(
            """{"id":"alt","title":"Alt","clips":[{"id":"c1","path":"/x/a.mp4","inSec":0.0,"outSec":10.0}],""" +
                """"audio":[{"id":"m1","path":"/x/musik.mp3","startSec":1.0,"outSec":8.0}],"createdAt":1,"updatedAt":1}""",
        )
        val p = VideoStore(File(base, "files")).get("alt")
        assertNotNull(p)
        assertEquals(1, p.audio.single().track)
        assertEquals(Frame(), p.clips.single().frame)
        assertEquals(1, p.videoTrackCount)
        assertEquals(1, p.audioTrackCount)
        assertTrue(p.layers.isEmpty() && p.markers.isEmpty() && p.magnet)
        // Into the media pool when it is opened.
        assertEquals(listOf("/x/a.mp4", "/x/musik.mp3"), VideoEdit.withPool(p).media.map { it.path })
        // Saved again with the new parts and read back.
        val q = VideoEdit.addLayer(p, "/x/webcam.mp4", 2.0, 5.0).first
        val store = VideoStore(File(base, "files"))
        store.save(q)
        assertEquals(q.layers, VideoStore(File(base, "files")).get("alt")!!.layers)
    }

    @Test
    fun pictureInPictureGoesOnTheLowestFreeTrack() {
        val p = VideoProject(title = "PiP", clips = listOf(VideoClip(path = "/x/screen.mp4", inSec = 0.0, outSec = 60.0)))
        val (a, first) = VideoEdit.addLayer(p, "/x/webcam.mp4", 0.0, 30.0)
        assertEquals(2, a.layers.single().track)
        assertEquals(Frame.corner("unten-rechts"), a.layers.single().frame)
        val (b, _) = VideoEdit.addLayer(a, "/x/logo.png", 10.0, 5.0, corner = "oben-links", still = true)
        assertEquals(3, b.layers.last().track, "überlappt V2 → V3")
        assertEquals(3, b.videoTrackCount)
        val (c, _) = VideoEdit.addLayer(b, "/x/logo.png", 40.0, 5.0)
        assertEquals(2, c.layers.last().track, "nach dem ersten wieder auf V2")
        // Corners stay inside the frame.
        Frame.CORNERS.forEach { (k, _) ->
            val f = Frame.corner(k)
            assertTrue(f.x - f.scale / 2 >= 0f && f.x + f.scale / 2 <= 1f && f.y - f.scale / 2 >= 0f && f.y + f.scale / 2 <= 1f, k)
        }
        assertEquals(first, c.layers.first().id)
    }

    @Test
    fun exportGraphLaysTracksAndMixesTheirSound() {
        val p = VideoProject(
            title = "Graph", width = 1280, height = 720,
            clips = listOf(VideoClip(path = "/x/screen.mp4", inSec = 0.0, outSec = 20.0, frame = Frame(scale = 0.8f, cropL = 0.1f, opacity = 0.9f))),
            layers = listOf(
                LayerClip(path = "/x/webcam.mp4", track = 2, startSec = 2.0, outSec = 10.0, frame = Frame.corner("unten-rechts")),
                LayerClip(path = "/x/logo.png", track = 3, startSec = 0.0, outSec = 20.0, frame = Frame.corner("oben-links", 0.1f), muted = true),
            ),
            audio = listOf(AudioClip(path = "/x/musik.mp3", startSec = 0.0, outSec = 20.0), AudioClip(path = "/x/mikro.wav", startSec = 0.0, outSec = 20.0, track = 2, volume = 1f)),
            markers = listOf(Marker(sec = 0.0, note = "Einleitung"), Marker(sec = 8.0, note = "Versuch; Teil 1")),
        )
        val info: (File) -> io.github.thepytales.llmhub.video.MediaInfo? = { f ->
            if (f.extension == "png") io.github.thepytales.llmhub.video.MediaInfo(0.0, 200, 200, 0.0, hasVideo = true)
            else io.github.thepytales.llmhub.video.MediaInfo(20.0, 640, 360, 25.0, hasVideo = f.extension == "mp4", hasAudio = true)
        }
        val work = File(base, "plan")
        val plan = VideoRender.plan(p, VideoRender.preset("opal"), File(base, "out.mp4"), work, info, rnnoise = null)
        val graph = File(work, "filter.txt").readText()
        assertTrue("crop=iw*0.900" in graph && "colorchannelmixer=aa=0.900" in graph, "V1 zugeschnitten und durchscheinend:\n$graph")
        assertTrue("setpts=PTS+2.000/TB[l0]" in graph, "Webcam ab 2 s:\n$graph")
        assertTrue(Regex("overlay=x=[\\d.]+-w/2:y=[\\d.]+-h/2:eof_action=pass\\[vl1]").containsMatchIn(graph), "Logo über der Webcam:\n$graph")
        assertTrue("[la0]" in graph && "amix=inputs=4" in graph, "Ton von V1, Webcam, Musik und Mikrofon:\n$graph")
        assertTrue("-map_chapters" in plan.args, "Kapitel aus den Markern")
        assertTrue("title=Versuch\\; Teil 1" in File(work, "kapitel.txt").readText())
        // Muted A2 and hidden V2 leave their part out.
        val quiet = p.copy(trackStates = mapOf("A2" to TrackState(muted = true), "V2" to TrackState(hidden = true)))
        VideoRender.plan(quiet, VideoRender.preset("opal"), File(base, "out.mp4"), work, info, rnnoise = null)
        val g2 = File(work, "filter.txt").readText()
        assertTrue("amix=inputs=2" in g2 && "[l0]" in g2 && "[l1]" !in g2, "nur das Logo und die Musik:\n$g2")
        // A window of the timeline (the preview): from 5 s, no titles, no loudness filter.
        val withTitle = p.copy(overlays = listOf(TextOverlay(text = "T", startSec = 0.0, durationSec = 3.0)), sound = p.sound.copy(normalize = true))
        val g = VideoRender.graph(withTitle, 640, 360, 25, File(base, "win"), info, null, VideoRender.Window(5.0, 9.0))
        val lines = g.lines.joinToString("\n")
        assertEquals(4.0, g.duration, 1e-9)
        assertTrue("loudnorm" !in lines && "titel-" !in lines, lines)
        assertTrue(g.inputs.joinToString(" ").contains("-ss 5.000 -t 15.000 -i /x/screen.mp4"), g.inputs.toString())
        assertTrue("setpts=PTS+0.000/TB[l0]" in lines && g.inputs.joinToString(" ").contains("-ss 3.000"), "Webcam ab ihrer 3. Sekunde:\n$lines")
    }

    /**
     * Fertig, wenn: Screencast + Webcam als Bild-in-Bild + Musik + Titel laufen in der Vorschau flüssig mit Ton und
     * sehen im Export genauso aus.
     */
    @Test
    fun previewShowsWhatTheExportMakes(): Unit = runBlocking {
        if (ffmpeg() == null) return@runBlocking
        val screen = colour("screencast.mp4", "red", 8)
        val screen2 = colour("screencast2.mp4", "blue", 4, tone = 550)
        val webcam = colour("webcam.mp4", "0x00FF00", 8, size = "320x240", tone = 880)
        val music = make("musik.m4a", "-f", "lavfi", "-i", "sine=frequency=220:duration=12:sample_rate=48000", "-c:a", "aac")
        val p = VideoProject(
            title = "Bild-in-Bild", width = 1280, height = 720,
            clips = listOf(VideoClip(path = screen.absolutePath, inSec = 0.0, outSec = 8.0), VideoClip(path = screen2.absolutePath, inSec = 0.0, outSec = 4.0, transition = "fade", transitionSec = 1.0)),
            layers = listOf(LayerClip(path = webcam.absolutePath, track = 2, startSec = 0.5, outSec = 6.5)),
            audio = listOf(AudioClip(path = music.absolutePath, startSec = 0.0, outSec = 11.0, volume = 0.2f)),
            overlays = listOf(TextOverlay(style = "bauchbinde", text = "Erika Muster", subtext = "TU Dresden", startSec = 0.2, durationSec = 1.5)),
        )
        assertEquals(11.0, p.duration, 1e-9)
        val player = VideoPlayer()
        try {
            // A still at 2 s: through the graph (a picture on V2), the webcam in the corner.
            assertTrue(player.needsGraph(p, 2.0))
            val still = assertNotNull(player.picture(p, 2.0)).awt()
            ImageIO.write(still, "png", File(out, "vorschau-bild-in-bild.png"))
            assertTrue(green(rgb(still, 0.82, 0.81)), "Webcam unten rechts: ${rgb(still, 0.82, 0.81)}")
            assertTrue(red(rgb(still, 0.2, 0.2)), "Screencast dahinter: ${rgb(still, 0.2, 0.2)}")
            // V2 hidden: the corner is the screencast again.
            val hidden = p.copy(trackStates = mapOf("V2" to TrackState(hidden = true)))
            assertTrue(red(rgb(assertNotNull(player.picture(hidden, 2.0)).awt(), 0.82, 0.81)))
            // Half transparent: green and red mixed.
            val half = assertNotNull(player.picture(p.copy(layers = p.layers.map { it.copy(frame = it.frame.copy(opacity = 0.5f)) }), 2.0)).awt()
            rgb(half, 0.82, 0.81).let { c -> assertTrue(abs(c.first - c.second) < 60 && c.first > 80, "halb durchscheinend: $c") }

            // Playing: pictures come in time (wall clock without a sound device), the playhead moves on.
            player.seek(p, 1.0)
            Thread.sleep(300)
            player.play(p)
            val began = System.currentTimeMillis()
            while (player.framesShown < 25 && System.currentTimeMillis() - began < 15_000) Thread.sleep(50)
            assertTrue(player.framesShown >= 25, "Vorschau läuft: ${player.framesShown} Bilder")
            assertTrue(player.position.value > 1.5, "Abspielkopf läuft: ${player.position.value}")
            player.frame.value?.let { ImageIO.write(it.awt(), "png", File(out, "vorschau-laufend.png")) }
            // L while playing: twice as fast.
            player.forward(p)
            assertEquals(2.0, player.speed.value)
            player.stop()
            assertEquals(0.0, player.speed.value)

            // Export, and the same moments in the preview: in the corner, and in the middle of the cross-dissolve.
            val target = File(out, "bild-in-bild.mp4")
            VideoRender.render(p, VideoRender.preset("opal"), target) { }
            val result = assertNotNull(Media.probe(target))
            assertEquals(p.duration, result.duration, 0.35)
            for (t in listOf(2.0, 7.5)) {
                val exported = assertNotNull(Media.frame(target, t, 360)).awt()
                val preview = assertNotNull(player.picture(p, t)).awt()
                ImageIO.write(exported, "png", File(out, "export-$t.png"))
                ImageIO.write(preview, "png", File(out, "vorschau-$t.png"))
                var diff = 0L; var n = 0
                for (y in 0 until minOf(exported.height, preview.height) step 6) for (x in 0 until minOf(exported.width, preview.width) step 6) {
                    val a = exported.getRGB(x, y); val b = preview.getRGB(x, y)
                    diff += abs((a shr 16 and 0xff) - (b shr 16 and 0xff)) + abs((a shr 8 and 0xff) - (b shr 8 and 0xff)) + abs((a and 0xff) - (b and 0xff))
                    n += 3
                }
                assertTrue(diff.toDouble() / n < 18, "Vorschau und Export bei $t s gleich (mittlere Abweichung ${diff.toDouble() / n})")
            }
            // Chapters from markers land in the MP4.
            val chaptered = p.copy(markers = listOf(Marker(sec = 0.0, note = "Begrüßung"), Marker(sec = 5.0, note = "Versuch")))
            val withChapters = File(out, "kapitel.mp4")
            VideoRender.render(chaptered, VideoRender.preset("opal"), withChapters) { }
            val probe = ProcessBuilder("/usr/bin/ffprobe", "-v", "error", "-show_chapters", "-of", "json", withChapters.absolutePath).redirectErrorStream(true).start()
            val json = JSONObject(probe.inputStream.bufferedReader().readText())
            val chapters = json.getJSONArray("chapters")
            assertEquals(2, chapters.length(), json.toString())
            assertEquals("Versuch", chapters.getJSONObject(1).getJSONObject("tags").getString("title"))
            assertEquals("0:00 Begrüßung\n0:05 Versuch", VideoEdit.youtubeChapters(chaptered))
            File(out, "vorschau-bild.png").writeBytes(assertNotNull(player.picture(p, 3.0)).pngBytes())
        } finally {
            player.dispose()
        }
    }
}
