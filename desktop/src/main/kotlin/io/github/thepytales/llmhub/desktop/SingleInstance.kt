package io.github.thepytales.llmhub.desktop

import io.github.thepytales.llmhub.AppLog
import java.io.File
import java.io.RandomAccessFile
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.channels.FileLock
import kotlin.concurrent.thread

/**
 * Only one ADEU LLM per Windows user: a second start (e.g. double-click while the app sits in the
 * notification area) brings the running window to the front instead of opening a second copy. The lock lives in
 * the local folder, so a data folder synced to other computers (Google Drive) does not block them.
 */
object SingleInstance {
    private var lock: FileLock? = null
    private var server: ServerSocket? = null

    /** Returns true if this is the first instance; [onShow] runs when another start asks to show the window. */
    fun acquire(onShow: () -> Unit): Boolean {
        val lockFile = File(Paths.local, "instance.lock")
        val portFile = File(Paths.local, "instance.port")
        val channel = RandomAccessFile(lockFile, "rw").channel
        val l = runCatching { channel.tryLock() }.getOrNull()
        if (l == null) {
            // Another instance runs: ask it to show its window.
            runCatching {
                val port = portFile.readText().trim().toInt()
                Socket(InetAddress.getLoopbackAddress(), port).use { it.getOutputStream().write("show\n".toByteArray()) }
            }.onFailure { AppLog.e("Laufende Instanz nicht erreichbar", it) }
            channel.close()
            return false
        }
        lock = l
        runCatching {
            val s = ServerSocket(0, 5, InetAddress.getLoopbackAddress())
            server = s
            portFile.writeText(s.localPort.toString())
            thread(isDaemon = true, name = "single-instance") {
                while (!s.isClosed) {
                    runCatching {
                        s.accept().use { client ->
                            val line = client.getInputStream().bufferedReader().readLine()
                            if (line == "show") onShow()
                        }
                    }
                }
            }
        }.onFailure { AppLog.e("Einzelinstanz-Server", it) }
        return true
    }
}
