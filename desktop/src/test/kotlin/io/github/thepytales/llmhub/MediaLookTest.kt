package io.github.thepytales.llmhub

import io.github.thepytales.llmhub.ui.MediaFiles
import io.github.thepytales.llmhub.ui.VideoControl
import io.github.thepytales.llmhub.video.Media
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Vorschau für Video und Ton in der Schnellansicht (AP-82). */
class MediaLookTest {
    @Test
    fun videoAndSoundAreRecognizedAndTimesReadWell() {
        assertTrue(MediaFiles.isVideo(File("Vorlesung.MP4")) && MediaFiles.isMedia(File("Vorlesung.MP4")))
        assertTrue(MediaFiles.isMedia(File("Interview.m4a")) && !MediaFiles.isVideo(File("Interview.m4a")))
        assertTrue(!MediaFiles.isMedia(File("Bericht.pdf")))
        assertEquals("3:07", MediaFiles.clock(187.4))
        assertEquals("1:05:03", MediaFiles.clock(3903.0))
        assertEquals("0:00", MediaFiles.clock(-2.0))
        val quick = File("../app/src/main/java/io/github/thepytales/llmhub/ui/QuickLook.kt").readText()
        assertTrue("if (MediaFiles.isMedia(f)) return Look.Media(f)" in quick && "is Look.Media -> MediaLook(c, l.file, media)" in quick)
    }

    @Test
    fun aVideoPlaysWithPictureAndCanJump() {
        val ffmpeg = Media.ffmpeg()?.takeIf { runCatching { ProcessBuilder(it, "-version").start().waitFor(10, TimeUnit.SECONDS) }.getOrNull() == true } ?: return
        val dir = Files.createTempDirectory("look").toFile()
        try {
            val f = File(dir, "probe.mp4")
            ProcessBuilder(ffmpeg, "-nostdin", "-loglevel", "error", "-y", "-f", "lavfi", "-i", "testsrc=size=320x180:rate=25:duration=6",
                "-f", "lavfi", "-i", "sine=frequency=440:duration=6", "-shortest", "-c:v", "libx264", "-pix_fmt", "yuv420p", "-c:a", "aac", f.absolutePath)
                .redirectErrorStream(true).start().waitFor(60, TimeUnit.SECONDS)
            val info = Media.probe(f)!!
            assertTrue(info.hasVideo)
            val control = VideoControl(f, info.duration)
            assertEquals(6.0, control.duration.value!!, 0.3)
            // The first picture is there before playing.
            val deadline = System.currentTimeMillis() + 15_000
            while (control.player.frame.value == null && System.currentTimeMillis() < deadline) Thread.sleep(100)
            assertNotNull(control.player.frame.value, "erstes Bild")
            control.seek(3.0)
            assertEquals(3.0, control.position.value, 0.05)
            control.release()
        } finally {
            dir.deleteRecursively()
        }
    }
}
