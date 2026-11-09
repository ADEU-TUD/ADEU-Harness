package io.github.thepytales.llmhub.data

import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Large downloads that go on where they stopped (HTTP range), for the language models on this device and the
 * recognition model of the pseudonymization (AP-67). The file grows in [part]; checking and renaming is up to the caller.
 */
object FileDownload {
    /** Loads [url] into [part] ([total] bytes expected); throws [InterruptedException] when [cancelled]. */
    fun fetch(url: String, part: File, total: Long, cancelled: () -> Boolean, progress: (Double) -> Unit) {
        var have = if (part.isFile) part.length() else 0L
        if (have >= total) return
        part.parentFile?.mkdirs()
        var u = url
        var con: HttpURLConnection
        var hops = 0
        while (true) {
            con = URL(u).openConnection() as HttpURLConnection
            con.instanceFollowRedirects = false
            con.connectTimeout = 20_000
            con.readTimeout = 60_000
            con.setRequestProperty("User-Agent", "ADEU-LLM")
            if (have > 0) con.setRequestProperty("Range", "bytes=$have-")
            val code = con.responseCode
            if (code in 300..399 && hops++ < 10) { u = URL(URL(u), con.getHeaderField("Location")).toString(); con.disconnect(); continue }
            if (code == 416) { part.delete(); have = 0; con.disconnect(); continue }
            if (code != 200 && code != 206) { con.disconnect(); error("HTTP $code") }
            if (code == 200) have = 0
            break
        }
        con.inputStream.use { input ->
            java.io.FileOutputStream(part, have > 0).use { out ->
                val buf = ByteArray(1 shl 16)
                var got = have
                var last = 0L
                while (true) {
                    val n = input.read(buf)
                    if (n <= 0) break
                    out.write(buf, 0, n)
                    got += n
                    if (cancelled()) throw InterruptedException()
                    val now = System.currentTimeMillis()
                    if (now - last > 400) { last = now; progress((got.toDouble() / total).coerceAtMost(0.999)) }
                }
            }
        }
    }

    /** SHA-256 of [f] in lower-case hex. */
    fun sha256(f: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        f.inputStream().use { i -> val b = ByteArray(1 shl 20); while (true) { val n = i.read(b); if (n <= 0) break; md.update(b, 0, n) } }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}
