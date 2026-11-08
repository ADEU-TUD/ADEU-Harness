package io.github.thepytales.llmhub

import androidx.compose.ui.graphics.toAwtImage
import io.github.thepytales.llmhub.video.AudioClip
import io.github.thepytales.llmhub.video.ClipFx
import io.github.thepytales.llmhub.video.Face
import io.github.thepytales.llmhub.video.FaceDetector
import io.github.thepytales.llmhub.video.Faces
import io.github.thepytales.llmhub.video.Key
import io.github.thepytales.llmhub.video.Media
import io.github.thepytales.llmhub.video.MediaInfo
import io.github.thepytales.llmhub.video.Region
import io.github.thepytales.llmhub.video.VideoClip
import io.github.thepytales.llmhub.video.VideoEdit
import io.github.thepytales.llmhub.video.VideoFx
import io.github.thepytales.llmhub.video.VideoProject
import io.github.thepytales.llmhub.video.VideoRender
import io.github.thepytales.llmhub.video.VideoStore
import kotlinx.coroutines.runBlocking
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * AP-54 Video: einfache Effekte – speed, freeze, reverse, colour, zoom keyframes, vertical reframe, regions and voice
 * in the model and in the one ffmpeg graph of export and preview; with ffmpeg a short synthetic film is rendered and
 * looked at (pictures in build/video-effects).
 */
class VideoEffectsTest {
    private val base = Files.createTempDirectory("adeu-video-fx").toFile()
    private val out = File("build/video-effects").apply { mkdirs() }

    private fun ffmpeg(): String? = Media.ffmpeg()?.takeIf { runCatching { ProcessBuilder(it, "-version").start().waitFor(10, TimeUnit.SECONDS) }.getOrNull() == true }

    private fun make(name: String, vararg args: String): File {
        val f = File(base, name)
        val p = ProcessBuilder(listOf(ffmpeg()!!, "-nostdin", "-hide_banner", "-loglevel", "error", "-y") + args.toList() + f.absolutePath).redirectErrorStream(true).start()
        val log = p.inputStream.bufferedReader().readText()
        p.waitFor(120, TimeUnit.SECONDS)
        assertTrue(f.isFile && f.length() > 0, "Testdatei $name: $log")
        return f
    }

    private val info: (File) -> MediaInfo? = { MediaInfo(20.0, 1280, 720, 25.0, hasVideo = true, hasAudio = true) }

    private fun project(vararg clips: VideoClip) = VideoProject(title = "Effekte", width = 1280, height = 720, clips = clips.toList(), fadeEnds = false, sound = io.github.thepytales.llmhub.video.AudioSettings(normalize = false))

    @Test
    fun speedFreezeAndReverseInTheModel() {
        val a = VideoClip(id = "a", path = "/x/a.mp4", inSec = 0.0, outSec = 10.0)
        val b = VideoClip(id = "b", path = "/x/b.mp4", inSec = 0.0, outSec = 6.0)
        val p = project(a, b).copy(audio = listOf(AudioClip(id = "m", path = "/x/musik.mp3", startSec = 12.0, outSec = 3.0)))
        // Double speed: the first clip lasts 5 s, the rest closes up (magnet), the music moves along.
        val fast = VideoFx.speed(p, "a", 2.0)
        assertEquals(5.0, fast.clips[0].length, 1e-9)
        assertEquals(listOf(0.0, 5.0), fast.starts())
        assertEquals(7.0, fast.audio.single().startSec, 1e-9)
        assertEquals(4.0, fast.at(2.0)!!.second, 1e-9, "2 s der Zeitleiste = 4 s der Quelle")
        // The blade at 2 s of the timeline cuts the source at 4 s; both parts keep the speed.
        val (cut, right) = VideoEdit.splitV1(fast, 2.0)!!
        assertEquals(4.0, cut.clips[0].outSec, 1e-9)
        assertEquals(4.0, cut.clips.first { it.id == right }.inSec, 1e-9)
        assertTrue(cut.clips.all { it.id == "b" || it.fx.speed == 2.0 })
        assertEquals(fast.duration, cut.duration, 1e-9)
        // Ripple trim by 1 s of the timeline takes 2 s of the source.
        val trimmed = VideoEdit.rippleTrim(fast, "a", false, -1.0)
        assertEquals(8.0, trimmed.clips[0].outSec, 1e-9)
        // Reverse: the timeline shows Out first; too long a clip is refused with a reason.
        val (tooLong, why) = VideoFx.reverse(p, "a")
        assertNull(tooLong)
        assertTrue(why!!.contains("5 s"), why)
        val short = p.copy(clips = p.clips.map { if (it.id == "b") it.copy(outSec = 4.0) else it })
        val rev = VideoFx.reverse(short, "b").first!!
        val rb = rev.clips[1]
        assertTrue(rb.fx.reverse)
        assertEquals(4.0, rev.at(10.0)!!.second, 1e-9, "Anfang des rückwärts laufenden Clips = Out")
        assertEquals(1.0, rev.at(13.0)!!.second, 1e-9)
        val (rcut, rid) = VideoEdit.splitV1(rev, 11.0)!!
        assertEquals(3.0, rcut.clips[1].inSec, 1e-9, "linker Teil zeigt 4…3 s")
        assertEquals(3.0, rcut.clips.first { it.id == rid }.outSec, 1e-9, "rechter Teil zeigt 3…0 s")
        // Freeze: 2 s of the frame at 3 s, the clip split around it, everything after moved on.
        val (frozen, fid) = VideoFx.freeze(p, 3.0, 2.0)!!
        assertEquals(listOf(3.0, 2.0, 7.0, 6.0), frozen.clips.map { it.length })
        val f = frozen.clips.first { it.id == fid }
        assertTrue(f.fx.freeze && f.muted)
        assertEquals(3.0, f.inSec, 1e-9)
        assertEquals(3.0, frozen.at(4.5)!!.second, 1e-9, "Standbild zeigt immer dasselbe Bild")
        assertEquals(18.0, frozen.duration, 1e-9)
        assertEquals(14.0, frozen.audio.single().startSec, 1e-9)
    }

    @Test
    fun linkedSoundFollowsSpeedAndVoice() {
        val a = VideoClip(id = "a", path = "/x/a.mp4", inSec = 2.0, outSec = 10.0, muted = true)
        val p = project(a).copy(audio = listOf(AudioClip(id = "s", path = "/x/mikro.wav", startSec = 0.0, inSec = 3.0, outSec = 11.0, volume = 1f, linkedTo = "a", linkOffset = 1.0)))
        val q = VideoFx.speed(p, "a", 2.0)
        val s = q.audio.single()
        assertEquals(2.0, s.speed, 1e-9)
        assertEquals(4.0, s.length, 1e-9, "Ton so lang wie der schnelle Clip")
        val g = VideoRender.graph(VideoFx.voice(q, -4f), 640, 360, 25, File(base, "g1"), info, null).lines.joinToString("\n")
        assertTrue("asetrate=38098,aresample=48000,atempo=2.0,atempo=1.2599" in g, "Ton schneller, Stimme 4 Halbtöne tiefer:\n$g")
        // Old projects: no fx in the file, and none written back.
        val store = VideoStore(File(base, "files"))
        store.save(project(VideoClip(id = "x", path = "/x/a.mp4", inSec = 0.0, outSec = 5.0)).copy(id = "alt"))
        assertTrue("\"fx\"" !in File(base, "files/videos/alt.json").readText())
        store.save(VideoFx.speed(store.get("alt")!!, "x", 0.5))
        assertEquals(0.5, VideoStore(File(base, "files")).get("alt")!!.clips.single().fx.speed, 1e-9)
    }

    @Test
    fun keyframesInterpolateLinearlyOrEased() {
        val pts = listOf(0.0 to 0.0, 2.0 to 10.0, 4.0 to 10.0)
        assertEquals(0.0, VideoFx.interpolate(pts, -1.0, false), 1e-9)
        assertEquals(5.0, VideoFx.interpolate(pts, 1.0, false), 1e-9)
        assertEquals(5.0, VideoFx.interpolate(pts, 1.0, true), 1e-9, "Mitte bleibt Mitte")
        assertEquals(2.5, VideoFx.interpolate(pts, 0.5, false), 1e-9)
        assertTrue(VideoFx.interpolate(pts, 0.5, true) < 2.0, "sanft: langsamer am Anfang")
        assertEquals(10.0, VideoFx.interpolate(pts, 9.0, true), 1e-9)
        val k = VideoFx.keyAt(listOf(Key(10.0, 0.2f, 0.2f, 1f), Key(12.0, 0.6f, 0.4f, 0.5f)), 11.0, false)!!
        assertEquals(0.4f, k.x, 1e-6f); assertEquals(0.3f, k.y, 1e-6f); assertEquals(0.75f, k.w, 1e-6f)
        // As an ffmpeg expression: a balanced tree, not 300 nested ifs.
        val many = (0 until 300).map { it * 0.5 to (it % 7).toDouble() }
        val e = VideoFx.expr(many, "t", false)
        var depth = 0; var max = 0
        e.forEach { ch -> if (ch == '(') { depth++; max = maxOf(max, depth) } else if (ch == ')') depth-- }
        assertTrue(max < 40, "Schachtelung $max")
        assertEquals("4.000", VideoFx.expr(listOf(1.0 to 4.0), "t", true))
        assertEquals(listOf("atempo=2.0", "atempo=2.0"), VideoFx.atempo(4.0))
        assertEquals(listOf("atempo=0.5", "atempo=0.5"), VideoFx.atempo(0.25))
        assertEquals(listOf("atempo=0.7500"), VideoFx.atempo(0.75))
    }

    @Test
    fun graphHasTheEffects() {
        val fx = ClipFx(
            speed = 2.0, brightness = 0.1f, contrast = 1.2f, saturation = 0.8f, gainR = 1.1f, gainB = 0.9f,
            zoom = listOf(Key(0.0), Key(2.0, 0.75f, 0.25f, 0.5f)),
            regions = listOf(Region(keys = listOf(Key(0.0, 0.2f, 0.2f, 0.1f, 0.1f), Key(4.0, 0.4f, 0.3f, 0.1f, 0.1f)), mode = "blur")),
        )
        val p = project(
            VideoClip(path = "/x/screen.mp4", inSec = 0.0, outSec = 8.0, fx = fx),
            VideoClip(path = "/x/b.mp4", inSec = 1.0, outSec = 3.0, fx = ClipFx(reverse = true)),
            VideoClip(path = "/x/b.mp4", inSec = 5.0, outSec = 6.5, fx = ClipFx(freeze = true, regions = listOf(Region(keys = listOf(Key(5.0, 0.5f, 0.5f, 0.2f, 0.2f)))))),
            VideoClip(path = "/x/c.mp4", inSec = 0.0, outSec = 4.0, fx = ClipFx(vertical = true, zoom = listOf(Key(0.0, 0.3f), Key(4.0, 0.7f)))),
        )
        assertEquals(4.0 + 2.0 + 1.5 + 4.0, p.duration, 1e-9)
        val work = File(base, "plan")
        VideoRender.plan(p, VideoRender.preset("opal"), File(base, "out.mp4"), work, info, rnnoise = null)
        val g = File(work, "filter.txt").readText()
        val args = VideoRender.plan(p, VideoRender.preset("opal"), File(base, "out.mp4"), work, info, rnnoise = null).args.joinToString(" ")
        assertTrue("-ss 0.000 -t 8.000 -i /x/screen.mp4" in args, "doppelte Geschwindigkeit liest 8 s für 4 s: $args")
        assertTrue("setpts=(PTS-STARTPTS)/2.0000,fps=25,scale=1280:720" in g, g)
        assertTrue("atempo=2.0" in g, g)
        assertTrue("eq=brightness=0.100:contrast=1.200:saturation=0.800" in g && "colorchannelmixer=rr=1.100:gg=1.000:bb=0.900" in g, g)
        assertTrue(Regex("perspective=x0='[^']+':y0=").containsMatchIn(g) && "eval=frame" in g, "Zoom mit Keyframes:\n$g")
        assertTrue(Regex("crop=128:72:'clip\\(").containsMatchIn(g) && "boxblur=luma_radius=14" in g && Regex("overlay=x='clip\\(").containsMatchIn(g), "Bereich folgt:\n$g")
        assertTrue("-ss 1.000 -t 2.000 -i /x/b.mp4" in args && ",reverse," in g && "areverse" in g, "rückwärts:\n$g")
        assertTrue("-ss 5.000 -t 0.500 -i /x/b.mp4" in args && "trim=end_frame=1" in g, "Standbild:\n$g")
        assertTrue("flags=neighbor" in g, "verpixelt:\n$g")
        assertTrue("scale=404:720,setsar=1" in g, "Hochformat 9:16:\n$g")
        // Without effects the graph is the one of before AP-54.
        val plain = project(VideoClip(path = "/x/a.mp4", inSec = 0.0, outSec = 5.0))
        val pg = VideoRender.graph(plain, 1280, 720, 25, File(base, "plain"), info, null).lines.joinToString("\n")
        assertTrue("[0:v]scale=1280:720:force_original_aspect_ratio=decrease" in pg && "[e0]" !in pg, pg)
        // Grey world: a bluish picture gets less blue and more red.
        val pixels = IntArray(400) { (0xff shl 24) or (100 shl 16) or (127 shl 8) or 154 }
        val (r, gg, b) = VideoFx.grayWorld(pixels)
        assertTrue(r > 1.1f && b < 0.9f && abs(gg - 1f) < 0.05f, "$r $gg $b")
    }

    private fun frameAt(file: File, sec: Double): BufferedImage {
        val png = File(base, "f-${file.nameWithoutExtension}-$sec.png")
        make(png.name, "-ss", sec.toString(), "-i", file.absolutePath, "-frames:v", "1")
        return ImageIO.read(png)
    }

    private fun rgb(img: BufferedImage, fx: Double, fy: Double): Triple<Int, Int, Int> {
        val c = img.getRGB((img.width * fx).toInt().coerceIn(0, img.width - 1), (img.height * fy).toInt().coerceIn(0, img.height - 1))
        return Triple(c shr 16 and 0xff, c shr 8 and 0xff, c and 0xff)
    }

    /** Mean absolute difference of neighbouring pixels in a box: low when blurred or pixelated. */
    private fun detail(img: BufferedImage, x0: Double, y0: Double, x1: Double, y1: Double): Double {
        var sum = 0.0; var n = 0
        for (y in (img.height * y0).toInt() until (img.height * y1).toInt()) for (x in (img.width * x0).toInt() until (img.width * x1).toInt() - 1) {
            val a = img.getRGB(x, y); val b = img.getRGB(x + 1, y)
            sum += abs((a shr 8 and 0xff) - (b shr 8 and 0xff)); n++
        }
        return sum / maxOf(1, n)
    }

    /**
     * Fertig, wenn (Teil Screencast): a screencast zooms by keyframes onto a part – rendered with speed, colour, zoom
     * and a blurred region through the export graph, and the preview graph gives the same picture.
     */
    @Test
    fun rendersSpeedColourZoomAndRegion(): Unit = runBlocking {
        if (ffmpeg() == null) return@runBlocking
        // Left half red, right half blue, a fine pattern (testsrc) in the top left quarter; a tone.
        val src = make(
            "screencast.mp4", "-f", "lavfi", "-i", "color=c=red:s=640x360:r=25:d=8", "-f", "lavfi", "-i", "color=c=blue:s=320x360:r=25:d=8",
            "-f", "lavfi", "-i", "testsrc=s=320x180:r=25:d=8", "-f", "lavfi", "-i", "sine=frequency=440:duration=8:sample_rate=48000",
            "-filter_complex", "[0][1]overlay=320:0[a];[a][2]overlay=0:0[v]", "-map", "[v]", "-map", "3:a",
            "-c:v", "libx264", "-preset", "ultrafast", "-pix_fmt", "yuv420p", "-c:a", "aac", "-shortest",
        )
        val zoom = listOf(Key(0.0), Key(2.0, 0.75f, 0.5f, 0.4f), Key(8.0, 0.75f, 0.5f, 0.4f))
        val region = Region(keys = listOf(Key(0.0, 0.25f, 0.25f, 0.5f, 0.5f)), mode = "blur")
        val p = project(
            VideoClip(path = src.absolutePath, inSec = 0.0, outSec = 8.0, fx = ClipFx(speed = 2.0, zoom = zoom, ease = false, brightness = 0.05f)),
            VideoClip(path = src.absolutePath, inSec = 0.0, outSec = 4.0, fx = ClipFx(regions = listOf(region))),
        ).copy(width = 640, height = 360)
        val film = File(out, "effekte.mp4")
        VideoRender.render(p, VideoRender.preset("projekt"), film) { }
        val dur = Media.probe(film)!!.duration
        assertTrue(abs(dur - 8.0) < 0.2, "4 s schnell + 4 s = 8 s, ist $dur")
        // At 1 s of the film (2 s of the source) the zoom reached the blue right half: blue in the middle.
        val z = frameAt(film, 1.5)
        ImageIO.write(z, "png", File(out, "zoom.png"))
        val mid = rgb(z, 0.5, 0.5)
        assertTrue(mid.third > 150 && mid.first < 90, "Mitte blau nach dem Zoom: $mid")
        val start = frameAt(film, 0.0)
        assertTrue(rgb(start, 0.25, 0.75).first > 150, "am Anfang noch das ganze Bild (links rot)")
        // In the second clip the pattern in the top left is blurred.
        val blurred = frameAt(film, 6.0)
        ImageIO.write(blurred, "png", File(out, "bereich.png"))
        val original = frameAt(src, 2.0)
        val d1 = detail(blurred, 0.05, 0.05, 0.45, 0.45)
        val d0 = detail(original, 0.05, 0.05, 0.45, 0.45)
        assertTrue(d1 < d0 * 0.5, "Muster weichgezeichnet: $d1 gegen $d0")
        // The preview graph (same graph, other size) shows the zoom too.
        val pic = io.github.thepytales.llmhub.video.VideoPlayer().picture(p, 1.5)
        assertNotNull(pic)
        val awt = pic.toAwtImage()
        val pm = rgb(awt, 0.5, 0.5)
        assertTrue(pm.third > 150 && pm.first < 90, "Vorschau wie Export: $pm")
    }

    /** Stands in for YuNet: a "face" is the yellow square of the synthetic observation video. */
    private class YellowFaces : FaceDetector {
        override val name = "Testdetektor"
        override fun detect(img: BufferedImage): List<Face> {
            var x0 = Int.MAX_VALUE; var y0 = Int.MAX_VALUE; var x1 = -1; var y1 = -1
            for (y in 0 until img.height) for (x in 0 until img.width) {
                val c = img.getRGB(x, y)
                if ((c shr 16 and 0xff) > 170 && (c shr 8 and 0xff) > 170 && (c and 0xff) < 90) { x0 = minOf(x0, x); y0 = minOf(y0, y); x1 = maxOf(x1, x); y1 = maxOf(y1, y) }
            }
            if (x1 < 0) return emptyList()
            val w = img.width.toFloat(); val h = img.height.toFloat()
            return listOf(Face((x0 + x1) / 2f / w, (y0 + y1) / 2f / h, (x1 - x0 + 1) / w, (y1 - y0 + 1) / h, 0.95f))
        }
    }

    @Test
    fun facesBecomeRegionsThatFollow() {
        // Two faces over 4 s: one moves right, one appears at 2 s and stays; a gap ends a track.
        val samples = (0..10).map { k ->
            val t = k * 0.4
            t to listOfNotNull(Face(0.2f + 0.05f * k, 0.4f, 0.1f, 0.15f), Face(0.8f, 0.3f, 0.08f, 0.12f).takeIf { t >= 2.0 })
        }
        val tracks = Faces.tracks(samples)
        assertEquals(2, tracks.size, tracks.toString())
        val regions = Faces.regions(samples)
        assertTrue(regions.all { it.face && !it.hold })
        val moving = regions.first { it.keys.size > 1 && it.keys.first().x < 0.5f }
        assertEquals(2, moving.keys.size, "gerade Bewegung: zwei Keyframes genügen")
        assertEquals(0.45f, VideoFx.keyAt(moving.keys, 2.0, false)!!.x, 1e-3f)
        assertTrue(moving.keys.first().w > 0.1f, "mit Rand")
        // A face that suddenly is much larger starts a new region; a gap of 2 s as well.
        val jump = listOf(0.0 to listOf(Face(0.5f, 0.5f, 0.1f, 0.1f)), 0.4 to listOf(Face(0.5f, 0.5f, 0.3f, 0.3f)), 3.0 to listOf(Face(0.5f, 0.5f, 0.3f, 0.3f)))
        assertEquals(3, Faces.tracks(jump).size)
        // Applying replaces earlier found faces and keeps hand-drawn regions.
        val p = project(VideoClip(id = "c", path = "/x/a.mp4", inSec = 0.0, outSec = 4.0))
        val (withHand, _) = VideoFx.addRegion(p, "c", Key(1.0, 0.1f, 0.1f, 0.1f, 0.1f))
        val once = Faces.apply(withHand, "c", regions)
        val twice = Faces.apply(once, "c", regions.take(1))
        assertEquals(3, once.clips[0].fx.regions.size)
        assertEquals(2, twice.clips[0].fx.regions.size)
    }

    /** Fertig, wenn (Teil Beobachtungsvideo): faces are found automatically and pixelated – here with a test detector. */
    @Test
    fun observationVideoGetsItsFacesPixelated(): Unit = runBlocking {
        if (ffmpeg() == null) return@runBlocking
        val video = make(
            "beobachtung.mp4", "-f", "lavfi", "-i", "color=c=gray:s=640x360:r=25:d=4", "-f", "lavfi", "-i", "color=c=yellow:s=64x64:r=25:d=4",
            "-filter_complex", "[1]drawgrid=w=8:h=8:t=2:c=black[f];[0][f]overlay=x='40+120*t':y=140[v]", "-map", "[v]",
            "-c:v", "libx264", "-preset", "ultrafast", "-pix_fmt", "yuv420p",
        )
        Faces.override = YellowFaces()
        try {
            val clip = VideoClip(id = "c", path = video.absolutePath, inSec = 0.0, outSec = 4.0)
            val found = Faces.find(clip)
            assertEquals(1, found.size, found.toString())
            val p = Faces.apply(project(clip).copy(width = 640, height = 360), "c", found)
            val film = File(out, "beobachtung-verpixelt.mp4")
            VideoRender.render(p, VideoRender.preset("projekt"), film) { }
            for (t in listOf(0.5, 2.0, 3.5)) {
                val before = frameAt(video, t)
                val after = frameAt(film, t)
                if (t == 2.0) ImageIO.write(after, "png", File(out, "beobachtung-2s.png"))
                val cx = (40 + 120 * t + 32) / 640
                val box = doubleArrayOf(cx - 0.04, 0.42, cx + 0.04, 0.55)
                val d0 = detail(before, box[0], box[1], box[2], box[3])
                val d1 = detail(after, box[0], box[1], box[2], box[3])
                assertTrue(d1 < d0 * 0.5, "Gesicht bei $t s verpixelt: $d1 gegen $d0")
            }
        } finally {
            Faces.override = null
        }
    }

    /**
     * The real detector (YuNet on ONNX Runtime through JNA) – runs only where a runtime, the model and a picture with a
     * face are named: LORA_ORT_LIB, LORA_YUNET_MODEL, LORA_FACE_IMAGE (the build machine has none of them).
     */
    @Test
    fun yunetFindsARealFace() {
        val lib = System.getenv("LORA_ORT_LIB")?.let(::File)?.takeIf { it.isFile } ?: return
        val model = System.getenv("LORA_YUNET_MODEL")?.let(::File)?.takeIf { it.isFile } ?: return
        val img = System.getenv("LORA_FACE_IMAGE")?.let(::File)?.takeIf { it.isFile }?.let { ImageIO.read(it) } ?: return
        val yunet = io.github.thepytales.llmhub.video.YuNet(lib, model)
        val faces = yunet.detect(img)
        yunet.close()
        assertEquals(1, faces.size, faces.toString())
        val f = faces.single()
        assertTrue(f.score > 0.8f && f.x in 0.4f..0.65f && f.y in 0.25f..0.5f && f.w in 0.2f..0.5f, f.toString())
    }
}
