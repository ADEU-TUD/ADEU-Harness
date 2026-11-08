package io.github.thepytales.llmhub

import io.github.thepytales.llmhub.video.Media
import io.github.thepytales.llmhub.video.MediaInfo
import io.github.thepytales.llmhub.video.ScreenRecorder
import io.github.thepytales.llmhub.video.VideoRender
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Bildschirmaufnahme (AP-56): devices, the ffmpeg command, the project with separate tracks, a real recording with pause. */
class ScreenRecorderTest {
    @Test
    fun devicesOfDirectShowAndAvFoundation() {
        val newer = """
            [dshow @ 000001] "Integrated Camera" (video)
            [dshow @ 000001]   Alternative name "@device_pnp_\\?\usb#vid"
            [dshow @ 000001] "OBS Virtual Camera" (video)
            [dshow @ 000001] "Mikrofonarray (Realtek(R) Audio)" (audio)
            [dshow @ 000001]   Alternative name "@device_cm_{33D9A762}"
            [dshow @ 000001] "Stereomix (Realtek(R) Audio)" (audio)
            dummy: Immediate exit requested
        """.trimIndent()
        val d = ScreenRecorder.parseDshow(newer)
        assertEquals(listOf("Integrated Camera", "OBS Virtual Camera"), d.video)
        assertEquals(listOf("Mikrofonarray (Realtek(R) Audio)"), d.microphones)
        assertEquals(listOf("Stereomix (Realtek(R) Audio)"), d.loopback)
        val older = """
            [dshow @ 0x1] DirectShow video devices (some may be both video and audio devices)
            [dshow @ 0x1]  "HD Webcam"
            [dshow @ 0x1]     Alternative name "@device_pnp"
            [dshow @ 0x1] DirectShow audio devices
            [dshow @ 0x1]  "Mikrofon (USB Audio)"
        """.trimIndent()
        val o = ScreenRecorder.parseDshow(older)
        assertEquals(listOf("HD Webcam"), o.video)
        assertEquals(listOf("Mikrofon (USB Audio)"), o.audio)
        val mac = """
            [AVFoundation indev @ 0x1] AVFoundation video devices:
            [AVFoundation indev @ 0x1] [0] FaceTime HD Camera
            [AVFoundation indev @ 0x1] [1] Capture screen 0
            [AVFoundation indev @ 0x1] AVFoundation audio devices:
            [AVFoundation indev @ 0x1] [0] MacBook Pro Microphone
        """.trimIndent()
        val m = ScreenRecorder.parseAvfoundation(mac)
        assertEquals(listOf("FaceTime HD Camera"), m.video)
        assertEquals(listOf("MacBook Pro Microphone"), m.audio)
    }

    @Test
    fun oneProcessWritesEverySourceIntoItsOwnFile() {
        val dir = File("aufnahme")
        val part = ScreenRecorder.Part(File(dir, "bildschirm-1.mkv"), File(dir, "webcam-1.mkv"), File(dir, "mikrofon-1.wav"), File(dir, "systemton-1.wav"))
        val s = ScreenRecorder.Setup(
            source = ScreenRecorder.Source.AREA, area = ScreenRecorder.Area(100, 50, 1281, 721), webcam = "Integrated Camera",
            mic = "Mikrofonarray (Realtek(R) Audio)", systemAudio = "Stereomix (Realtek(R) Audio)", fps = 30,
        )
        val a = ScreenRecorder.args(s, part, ScreenRecorder.Platform.WINDOWS)
        val line = a.joinToString(" ")
        assertTrue("-f gdigrab -framerate 30 -draw_mouse 1 -offset_x 100 -offset_y 50 -video_size 1280x720 -i desktop" in line, line)
        assertTrue("-f dshow -rtbufsize 256M -i video=Integrated Camera" in line, line)
        assertTrue("-i audio=Mikrofonarray (Realtek(R) Audio)" in line && "-i audio=Stereomix (Realtek(R) Audio)" in line, line)
        // One output per input: 0 screen, 1 webcam, 2 microphone, 3 system sound.
        assertTrue("-map 0:v" in line && "-map 1:v" in line && "-map 2:a" in line && "-map 3:a" in line, line)
        assertTrue(a.indexOf(part.screen.absolutePath) < a.indexOf(part.webcam!!.absolutePath))
        assertTrue(a.indexOf("-map 3:a".split(" ")[1]) < a.indexOf(part.system!!.absolutePath))
        // A window by its title; without webcam and sound only the screen.
        val w = ScreenRecorder.args(ScreenRecorder.Setup(source = ScreenRecorder.Source.WINDOW, window = "Folien – PowerPoint"), ScreenRecorder.Part(part.screen, null, null, null), ScreenRecorder.Platform.WINDOWS)
        assertTrue("title=Folien – PowerPoint" in w)
        assertEquals(1, w.count { it == "-map" })
        // Linux: x11grab on the display, PulseAudio.
        val l = ScreenRecorder.args(s.copy(source = ScreenRecorder.Source.SCREEN, screen = ScreenRecorder.Area(0, 0, 1920, 1080), webcam = "/dev/video0", mic = "default", systemAudio = null), part.copy(system = null), ScreenRecorder.Platform.LINUX).joinToString(" ")
        assertTrue("-f x11grab" in l && "-video_size 1920x1080" in l && "-f v4l2 -i /dev/video0" in l && "-f pulse -i default" in l, l)
    }

    @Test
    fun theProjectHasSeparateTracks() {
        val dir = Files.createTempDirectory("aufnahme").toFile()
        try {
            fun f(n: String) = File(dir, n).apply { writeText("x") }
            val parts = listOf(
                ScreenRecorder.Part(f("bildschirm-1.mkv"), f("webcam-1.mkv"), f("mikrofon-1.wav"), f("systemton-1.wav"), 10.0),
                ScreenRecorder.Part(f("bildschirm-2.mkv"), f("webcam-2.mkv"), f("mikrofon-2.wav"), null, 5.0),
            )
            val info = { file: File -> MediaInfo(duration = if (file.name.endsWith("-1.mkv") || file.name.endsWith("-1.wav")) 10.0 else 5.0, width = 2560, height = 1440, fps = 30.0, hasVideo = file.extension == "mkv", hasAudio = file.extension == "wav") }
            val ring = ScreenRecorder.ring(File(dir, "klick.png"))
            val clicks = listOf(ScreenRecorder.Click(2.0, 0.25f, 0.5f), ScreenRecorder.Click(2.1, 0.25f, 0.5f), ScreenRecorder.Click(12.0, 0.8f, 0.1f))
            val p = ScreenRecorder.project("Aufnahme", parts, clicks, ring, info)
            assertEquals(1920 to 1080, p.width to p.height)
            assertEquals(30, p.fps)
            assertEquals(listOf(0.0 to 10.0, 0.0 to 5.0), p.clips.map { it.inSec to it.outSec })
            assertTrue(p.clips.all { it.muted })
            assertEquals(15.0, p.duration, 1e-9)
            // Webcam on V2 as picture-in-picture, starting with its part.
            val cams = p.layers.filter { it.track == 2 }
            assertEquals(listOf(0.0, 10.0), cams.map { it.startSec })
            assertTrue(cams.all { it.frame.scale < 0.5f && it.frame.x > 0.5f && it.frame.y > 0.5f })
            // Clicks: a double click is one ring; rings on V3 where the click was.
            val rings = p.layers.filter { it.track == 3 }
            assertEquals(2, rings.size)
            assertEquals(0.25f, rings[0].frame.x)
            assertEquals(12.0 - 0.05, rings[1].startSec, 1e-9)
            // Microphone on A1, system sound on A2.
            assertEquals(listOf(0.0, 10.0), p.audio.filter { it.track == 1 }.map { it.startSec })
            assertEquals(listOf(0.0), p.audio.filter { it.track == 2 }.map { it.startSec })
            assertEquals(3 to 2, p.videoTrackCount to p.audioTrackCount)
            // All files in the media pool.
            assertEquals(7, p.media.size)
        } finally {
            dir.deleteRecursively()
        }
    }

    /** Fertig, wenn: Ein Screencast mit Webcam-Ecke ist nach Stopp sofort als Projekt mit getrennten Spuren schnittbereit. */
    @Test
    fun screencastWithWebcamIsReadyToCutAfterStop(): Unit = runBlocking {
        val exe = Media.ffmpeg()?.takeIf { runCatching { ProcessBuilder(it, "-version").start().waitFor(10, TimeUnit.SECONDS) }.getOrNull() == true } ?: return@runBlocking
        val dir = Files.createTempDirectory("screencast").toFile()
        try {
            // Test sources (lavfi) in place of the screen, the webcam and the microphone – the same command otherwise.
            val r = ScreenRecorder.start(ScreenRecorder.Setup(webcam = "Kamera", mic = "Mikrofon", fps = 25, clicks = true, test = true), File(dir, "a"), exe)
            Thread.sleep(2000)
            r.click(0.3f, 0.4f)
            r.pause()
            assertEquals(ScreenRecorder.State.PAUSED, r.state.value)
            Thread.sleep(600)
            r.resume()
            Thread.sleep(1500)
            val parts = r.stop()
            assertEquals(null, ScreenRecorder.active.value)
            assertEquals(2, parts.size)
            assertTrue(parts.all { it.screen.isFile && it.webcam!!.isFile && it.mic!!.isFile })
            val p = ScreenRecorder.project("Screencast", parts, r.clicks, ScreenRecorder.ring(File(dir, "klick.png")))
            assertEquals(2, p.clips.size)
            assertTrue(abs(p.clips[0].length - 2.0) < 0.6, "Teil 1: ${p.clips[0].length}")
            assertTrue(abs(p.clips[1].length - 1.5) < 0.6, "Teil 2: ${p.clips[1].length}")
            assertEquals(2, p.layers.count { it.track == 2 })
            assertEquals(1, p.layers.count { it.track == 3 })
            assertEquals(2, p.audio.count { it.track == 1 })
            // The pause is not in the recording: the second part starts where the first ends.
            assertEquals(p.clips[0].length, p.audio.filter { it.track == 1 }[1].startSec, 1e-6)
            // Ready to cut: it exports as the timeline says.
            val out = File(dir, "screencast.mp4")
            VideoRender.render(p, VideoRender.preset("opal"), out) { }
            val len = Media.probe(out)?.duration ?: error("kein Export")
            assertTrue(abs(len - p.duration) < 0.3, "Export $len s, Zeitleiste ${p.duration} s")
        } finally {
            dir.deleteRecursively()
        }
    }
}
