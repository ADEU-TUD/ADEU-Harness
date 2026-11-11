package io.github.thepytales.llmhub.ssh

import io.github.thepytales.llmhub.ui.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.io.File

/**
 * State and rules of the Server-Terminal (AP-118), without UI: the saved sessions, the connection, Lora's steps and the
 * protocol. The rule that matters: a step runs only through [run] for exactly that step – after the user's click –,
 * never several at once and never on its own.
 */
class ServerTerminal(val dir: File, private val assistant: ServerAssistant? = null) {
    val sessions = ServerSessions(dir)
    val knownHosts = KnownHosts(File(dir, "known_hosts"))

    data class State(
        val profile: SshProfile? = null,
        val connected: Boolean = false,
        val busy: String? = null,
        val error: String? = null,
        /** The server's key needs a decision (new: confirm; changed: explicit replace). */
        val hostKey: HostKeyProblem? = null,
        /** Password or passphrase is needed for the next try. */
        val needsSecret: Boolean = false,
        val steps: List<ServerStep> = emptyList(),
        val flowTitle: String? = null,
        val note: String = "",
        /** What the last request to a model on a server contained (after masking) – shown on request. */
        val sent: String? = null,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    var connection: SshConnection? = null
        private set
    private var log: ServerLog? = null
    /** Password/passphrase of this connection, in memory only (unless saved encrypted in [sessions]). */
    private var secret: String? = null

    private fun secrets(): Collection<String> = (sessions.secrets.all() + listOfNotNull(secret)).distinct()

    fun logOf(profile: SshProfile) = ServerLog(File(dir, "protokoll"), profile, ::secrets)

    /** Connects with [profile]; [typed] is a password/passphrase typed now, kept encrypted when [keep]. */
    suspend fun connect(profile: SshProfile, typed: String? = null, keep: Boolean = false) = withContext(Dispatchers.IO) {
        disconnect()
        val pass = typed?.takeIf { it.isNotEmpty() } ?: sessions.secrets.get(profile.id)
        _state.update { it.copy(profile = profile, busy = tr("Verbinde mit {0} …", profile.label), error = null, hostKey = null, needsSecret = false) }
        val l = logOf(profile).also { log = it }
        try {
            val conn = SshConnection.connect(profile, pass, knownHosts)
            connection = conn
            secret = pass
            if (keep && !typed.isNullOrEmpty()) sessions.secrets.put(profile.id, typed)
            l.write("Verbunden mit ${profile.user}@${profile.host}:${profile.port}")
            _state.update { it.copy(connected = true, busy = null) }
        } catch (e: HostKeyProblem) {
            l.write(if (e.result is KnownHosts.Result.Changed) "Verbindung blockiert: Server-Schlüssel geändert (${e.result.fingerprint})" else "Neuer Server-Schlüssel ${e.result.fingerprint} – wartet auf Bestätigung")
            secret = pass
            _state.update { it.copy(busy = null, hostKey = e) }
        } catch (e: SecretNeeded) {
            _state.update { it.copy(busy = null, needsSecret = true, error = e.message) }
        } catch (e: Exception) {
            l.write("Verbindung fehlgeschlagen", e.message.orEmpty())
            _state.update { it.copy(busy = null, error = e.message ?: e.javaClass.simpleName) }
        }
    }

    /** The user confirmed the key shown in [State.hostKey]; a changed key needs [replace]. Then connects again. */
    suspend fun acceptHostKey(replace: Boolean) {
        val p = _state.value.hostKey ?: return
        val profile = _state.value.profile ?: return
        if (p.result is KnownHosts.Result.Changed && !replace) return
        knownHosts.accept(p.host, p.port, p.key, replace = replace)
        logOf(profile).write(if (replace) "Geänderten Server-Schlüssel ausdrücklich ersetzt: ${p.result.fingerprint}" else "Server-Schlüssel bestätigt: ${p.result.fingerprint}")
        connect(profile, secret)
    }

    fun rejectHostKey() {
        _state.update { it.copy(hostKey = null, error = tr("Verbindung abgebrochen – der Server-Schlüssel wurde nicht bestätigt.")) }
    }

    fun disconnect() {
        connection?.let { c -> runCatching { c.close() }; log?.write("Verbindung getrennt") }
        connection = null
        _state.update { it.copy(connected = false) }
    }

    /** Puts the steps of [flow] up for approval – nothing runs yet. */
    fun load(flow: ServerFlow) {
        val p = _state.value.profile ?: return
        val steps = flow.steps(p) { tr(it) }
        _state.update { it.copy(steps = steps, flowTitle = tr(flow.title), note = tr(flow.description)) }
        log?.write("Ablauf vorgeschlagen: ${flow.title}", steps.joinToString("\n") { "${it.risk.name.lowercase()}: ${it.command}" })
    }

    /** Lora proposes steps for [wish] (model on this device preferred, see [ServerAssistant]). */
    suspend fun propose(wish: String, recent: String = "") {
        val p = _state.value.profile ?: return
        val a = assistant ?: return
        _state.update { it.copy(busy = tr("Lora überlegt …"), error = null) }
        try {
            val done = _state.value.steps.filter { it.state != StepState.OPEN }
            // Without an output of its own: what the last step printed (a failed step is what Lora should look at).
            val seen = recent.ifBlank { done.lastOrNull { it.state == StepState.FAILED }?.output ?: done.lastOrNull()?.output.orEmpty() }
            val r = a.propose(wish, p, seen, done, secrets())
            _state.update { it.copy(busy = null, steps = r.steps, flowTitle = wish.trim(), note = r.note, sent = r.sent.takeUnless { a.where().onDevice }) }
            log?.write("Lora schlägt vor ($wish)", r.steps.joinToString("\n") { "${it.risk.name.lowercase()}: ${it.command}" })
        } catch (e: Exception) {
            _state.update { it.copy(busy = null, error = e.message ?: e.javaClass.simpleName) }
        }
    }

    private fun set(index: Int, f: (ServerStep) -> ServerStep) =
        _state.update { s -> s.copy(steps = s.steps.mapIndexed { i, st -> if (i == index) f(st) else st }) }

    /** Runs exactly step [index] after the user's click; [onOutput] streams its output into the screen. */
    suspend fun run(index: Int, onOutput: (String) -> Unit = {}): ExecResult? = withContext(Dispatchers.IO) {
        val conn = connection ?: return@withContext null
        val step = _state.value.steps.getOrNull(index)?.takeIf { it.state == StepState.OPEN || it.state == StepState.FAILED } ?: return@withContext null
        if (_state.value.steps.any { it.state == StepState.RUNNING }) return@withContext null
        set(index) { it.copy(state = StepState.RUNNING, output = "") }
        log?.write("Zugestimmt, ausgeführt: ${step.command}")
        val r = runCatching { conn.exec(step.command) { chunk -> set(index) { it.copy(output = (it.output + chunk).takeLast(20_000)) }; onOutput(chunk) } }
            .getOrElse { ExecResult(null, it.message ?: it.javaClass.simpleName) }
        set(index) { it.copy(state = if (r.ok) StepState.DONE else StepState.FAILED, exitCode = r.exitCode, output = r.output.takeLast(20_000)) }
        log?.write(if (r.timedOut) "Zeitüberschreitung: ${step.command}" else "Ende mit Exit ${r.exitCode ?: "?"}: ${step.command}", r.output)
        r
    }

    fun skip(index: Int) {
        val step = _state.value.steps.getOrNull(index) ?: return
        set(index) { it.copy(state = StepState.SKIPPED) }
        log?.write("Übersprungen: ${step.command}")
    }

    /** The user changed the command; the app assesses it again (risk and notes). */
    fun edit(index: Int, command: String) {
        val step = _state.value.steps.getOrNull(index) ?: return
        val c = command.trim().takeIf { it.isNotEmpty() && '\n' !in it } ?: return
        set(index) { ServerStep(c, it.what, Risks.notes(c).joinToString(" ") { n -> tr(n) }) }
        log?.write("Bearbeitet: ${step.command} → $c")
    }

    /** The step went to the terminal (interactive, e.g. sudo with password) – the user answers there. */
    fun sentToTerminal(index: Int) {
        val step = _state.value.steps.getOrNull(index) ?: return
        set(index) { it.copy(state = StepState.DONE, output = tr("Im Terminal ausgeführt – Ausgabe dort.")) }
        log?.write("Zugestimmt, im Terminal ausgeführt: ${step.command}")
    }

    /** Ends the proposal; open steps are dropped. */
    fun cancel() {
        val open = _state.value.steps.count { it.state == StepState.OPEN }
        if (_state.value.steps.isNotEmpty()) log?.write("Abgebrochen", if (open > 0) "$open Schritte nicht ausgeführt" else "")
        _state.update { it.copy(steps = emptyList(), flowTitle = null, note = "", sent = null) }
    }

    fun clearError() = _state.update { it.copy(error = null) }

    fun readLog(): String = log?.read().orEmpty()
}
