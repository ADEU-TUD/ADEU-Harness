package io.github.thepytales.llmhub.ssh

import com.jediterm.core.util.TermSize
import com.jediterm.terminal.TtyConnector
import org.apache.sshd.client.channel.ChannelShell
import org.apache.sshd.client.channel.ClientChannelEvent
import java.io.InputStreamReader
import java.util.EnumSet

/** Connects JediTerm to the SSH shell (AP-118): what the server prints goes to the terminal, keys go to the server. */
class SshTty(private val channel: ChannelShell, private val title: String) : TtyConnector {
    private val reader = InputStreamReader(channel.invertedOut, Charsets.UTF_8)
    private val out = channel.invertedIn

    override fun read(buf: CharArray, offset: Int, length: Int): Int = reader.read(buf, offset, length)

    override fun write(bytes: ByteArray) {
        out.write(bytes)
        out.flush()
    }

    override fun write(string: String) = write(string.toByteArray(Charsets.UTF_8))

    override fun isConnected(): Boolean = channel.isOpen

    override fun resize(termSize: TermSize) {
        runCatching { channel.sendWindowChange(termSize.columns, termSize.rows) }
    }

    override fun waitFor(): Int {
        channel.waitFor(EnumSet.of(ClientChannelEvent.CLOSED), 0L)
        return channel.exitStatus ?: 0
    }

    override fun ready(): Boolean = reader.ready()

    override fun getName(): String = title

    override fun close() {
        runCatching { channel.close(false) }
    }
}
