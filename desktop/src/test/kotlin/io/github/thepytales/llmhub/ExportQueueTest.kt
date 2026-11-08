package io.github.thepytales.llmhub

import io.github.thepytales.llmhub.video.Encoders
import io.github.thepytales.llmhub.video.ExportQueue
import io.github.thepytales.llmhub.video.Marker
import io.github.thepytales.llmhub.video.Media
import io.github.thepytales.llmhub.video.SubtitleCue
import io.github.thepytales.llmhub.video.VideoClip
import io.github.thepytales.llmhub.video.VideoProject
import io.github.thepytales.llmhub.video.VideoRender
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Export mit GPU und Warteschlange (AP-57): encoders, their arguments, the queue with several targets, chapters, fallback. */
class ExportQueueTest {
    @Test
    fun hardwareEncodersAreFoundAndSpokenTo() {
        val list = """
            Encoders:
             V..... = Video
             ------
             V....D libx264              libx264 H.264 / AVC / MPEG-4 AVC / MPEG-4 part 10 (codec h264)
             V....D h264_amf             AMD AMF H.264 Encoder (codec h264)
             V....D h264_nvenc           NVIDIA NVENC H.264 encoder (codec h264)
             V..... h264_qsv             H.264 / AVC / MPEG-4 AVC / MPEG-4 part 10 (Intel Quick Sync Video acceleration) (codec h264)
             A....D aac                  AAC (Advanced Audio Coding)
        """.trimIndent()
        val known = Encoders.listed(list)
        assertTrue(known.containsAll(listOf("libx264", "h264_amf", "h264_nvenc", "h264_qsv")))
        assertTrue("aac" !in known)
        // Constant quality in each encoder's own words.
        assertEquals(listOf("-c:v", "libx264", "-preset", "medium", "-crf", "26", "-pix_fmt", "yuv420p"), Encoders.args(Encoders.SOFTWARE, 26, "medium"))
        val nv = Encoders.args(Encoders.byId("h264_nvenc"), 26, "medium").joinToString(" ")
        assertTrue("-c:v h264_nvenc" in nv && "-cq 27" in nv && "-b:v 0" in nv, nv)
        val qsv = Encoders.args(Encoders.byId("h264_qsv"), 20, "medium").joinToString(" ")
        assertTrue("-global_quality 21" in qsv && "-pix_fmt nv12" in qsv, qsv)
        assertTrue("-qp_i 20" in Encoders.args(Encoders.byId("h264_amf"), 20, "medium").joinToString(" "))
        // The export command takes the chosen encoder.
        val dir = Files.createTempDirectory("plan").toFile()
        try {
            val p = VideoProject(title = "Plan", clips = listOf(VideoClip(path = File(dir, "a.mp4").absolutePath, inSec = 0.0, outSec = 5.0)))
            val info = { _: File -> io.github.thepytales.llmhub.video.MediaInfo(5.0, 1280, 720, 25.0, hasVideo = true, hasAudio = true) }
            val plan = VideoRender.plan(p, VideoRender.preset("youtube"), File(dir, "out.mp4"), dir, info, rnnoise = null, encoder = Encoders.byId("h264_nvenc"))
            assertTrue("h264_nvenc" in plan.args && "libx264" !in plan.args, plan.args.toString())
            val sound = VideoRender.plan(p, VideoRender.preset("youtube"), File(dir, "out.m4a"), dir, info, rnnoise = null, audioOnly = true)
            assertTrue("-vn" in sound.args && "[vout]" !in sound.args && "-c:v" !in sound.args, sound.args.toString())
        } finally {
            dir.deleteRecursively()
        }
    }

    /**
     * Fertig, wenn: Ein Video exportiert im Hintergrund, mit Kapiteln – hier mehrere Ziele auf einmal (OPAL, YouTube, nur
     * Ton, Untertitel, Kapitel für YouTube), nacheinander, mit Ziel Nextcloud. Ohne Grafikkarte rechnet der Prozessor;
     * ein Hardware-Encoder, der nicht geht, fällt auf x264 zurück.
     */
    @Test
    fun severalTargetsRunInTheBackgroundWithChapters(): Unit = runBlocking {
        val exe = Media.ffmpeg()?.takeIf { runCatching { ProcessBuilder(it, "-version").start().waitFor(10, TimeUnit.SECONDS) }.getOrNull() == true } ?: return@runBlocking
        val dir = Files.createTempDirectory("exporte").toFile()
        try {
            val src = File(dir, "vortrag.mp4")
            ProcessBuilder(exe, "-nostdin", "-loglevel", "error", "-y", "-f", "lavfi", "-i", "testsrc2=size=640x360:rate=25:duration=6",
                "-f", "lavfi", "-i", "sine=f=440:d=6", "-c:v", "libx264", "-preset", "ultrafast", "-pix_fmt", "yuv420p", "-c:a", "aac", "-shortest", src.absolutePath)
                .redirectErrorStream(true).start().waitFor(60, TimeUnit.SECONDS)
            val p = VideoProject(
                title = "Vorlesung 5", fadeEnds = false,
                clips = listOf(VideoClip(path = src.absolutePath, inSec = 0.0, outSec = 6.0)),
                markers = listOf(Marker(sec = 2.0, note = "Versuch"), Marker(sec = 4.0, note = "Ergebnis")),
                subtitles = listOf(SubtitleCue(0.5, 2.0, "Willkommen.")),
            )
            val uploads = mutableListOf<Pair<String, String>>()
            ExportQueue.uploader = { f, folder -> uploads += f.name to folder; "$folder/${f.name}" }
            val out = File(dir, "Erstellt")
            val added = ExportQueue.add(p, listOf("opal", "youtube"), ExportQueue.Kind.entries.toSet(), out, useHardware = true, upload = "/Lora/Videos")
            assertEquals(5, added.size)
            // Queued, not done: the call returns at once and the work goes on in the background.
            assertTrue(ExportQueue.entries.value.any { !it.finished })
            // Only one runs at a time.
            var most = 0
            val watch = Thread { while (ExportQueue.busy > 0) { most = maxOf(most, ExportQueue.entries.value.count { it.status == ExportQueue.Status.RUNNING }); Thread.sleep(20) } }.apply { start() }
            ExportQueue.idle()
            watch.join(2000)
            assertEquals(1, most)
            val done = ExportQueue.entries.value.filter { it.id in added.map { a -> a.id } }
            assertTrue(done.all { it.status == ExportQueue.Status.DONE }, done.joinToString { "${it.label}: ${it.status} ${it.error}" })
            val names = done.flatMap { it.files }.map { it.name }.toSet()
            assertEquals(setOf("Vorlesung 5 – OPAL.mp4", "Vorlesung 5 – YOUTUBE.mp4", "Vorlesung 5.m4a", "Vorlesung 5.srt", "Vorlesung 5.vtt", "Vorlesung 5 – Kapitel.txt"), names)
            // Chapters in the MP4 and for the YouTube description.
            val opal = File(out, "Vorlesung 5 – OPAL.mp4")
            val probe = ProcessBuilder(exe, "-hide_banner", "-i", opal.absolutePath).redirectErrorStream(true).start().let { pr -> pr.inputStream.bufferedReader().readText().also { pr.waitFor() } }
            assertEquals(3, Regex("Chapter #").findAll(probe).count(), probe)
            assertEquals("0:00 Beginn\n0:02 Versuch\n0:04 Ergebnis\n", File(out, "Vorlesung 5 – Kapitel.txt").readText())
            // Only sound in the M4A, as long as the film.
            val m4a = Media.probe(File(out, "Vorlesung 5.m4a"))!!
            assertTrue(m4a.hasAudio && !m4a.hasVideo && abs(m4a.duration - 6.0) < 0.3, m4a.toString())
            // Everything went on to Nextcloud.
            assertEquals(6, uploads.size)
            assertTrue(uploads.all { it.second == "/Lora/Videos" })
            assertTrue(done.first().encoder != null)

            // A hardware encoder that does not work here falls back to x264 – the film is there all the same.
            val gpu = Encoders.available(exe)
            val forced = Encoders.byId("h264_nvenc")
            val film = File(dir, "nvenc.mp4")
            VideoRender.render(p, VideoRender.preset("opal"), film, forced) { }
            assertTrue(film.isFile && film.length() > 0)
            if (forced !in gpu) assertEquals("h264_nvenc", VideoRender.lastFallback)

            // Cancelling: the entry stops and leaves no file.
            val long = ExportQueue.add(p.copy(title = "Abbruch"), listOf("projekt"), setOf(ExportQueue.Kind.VIDEO), out, useHardware = false).single()
            ExportQueue.cancel(long.id)
            ExportQueue.idle()
            assertEquals(ExportQueue.Status.CANCELLED, ExportQueue.entries.value.first { it.id == long.id }.status)
            assertTrue(!long.out.exists())
            ExportQueue.clearFinished()
            assertTrue(ExportQueue.entries.value.isEmpty())
        } finally {
            ExportQueue.uploader = null
            dir.deleteRecursively()
        }
    }
}
