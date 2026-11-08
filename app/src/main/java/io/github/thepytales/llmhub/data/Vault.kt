package io.github.thepytales.llmhub.data

import io.github.thepytales.llmhub.ui.tr
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** What is stored in the clear about the vault: salts, the wrapped data key, failed attempts. Never the contents. */
@Serializable
data class VaultMeta(
    val version: Int = 1,
    val salt: String,
    val iterations: Int,
    /** The data key, encrypted with the key from the PIN (and then with the device key, see [deviceSealed]). */
    val pinWrapped: String,
    val deviceSealed: Boolean,
    val recoverySalt: String,
    /** The data key, encrypted with the key from the recovery key. */
    val recoveryWrapped: String,
    val failures: Int = 0,
    val blockedUntil: Long = 0,
    val lockMinutes: Int = VaultStore.DEFAULT_LOCK_MINUTES,
    /**
     * The PIN boxes of further devices (AP-63): a vault synced to another PC or the phone gets its own box there, sealed
     * with that device's key – the box of the first device stays, so both keep working with their PIN.
     */
    val boxes: List<VaultBox> = emptyList(),
)

/** The data key encrypted with a PIN (and sealed with the key of one device): one per device that opens the vault. */
@Serializable
data class VaultBox(val salt: String, val iterations: Int, val wrapped: String, val sealed: Boolean, val createdAt: Long = 0)

/**
 * Tresor für sensible Forschung (AP-10): chats and projects with interviews or data of students, locked with an app
 * PIN. A random data key encrypts every vault chat (AES-256-GCM); the key itself is stored only wrapped – with the key
 * derived from the PIN (PBKDF2, then additionally sealed with the device key: Android Keystore / Windows DPAPI) and with
 * the key derived from the recovery key shown once when setting up. Without the PIN the files cannot be read; vault
 * chats never appear in the chat list, the search, the memory or suggestions outside the vault. The vault locks itself
 * after [VaultMeta.lockMinutes] without activity.
 */
class VaultStore(
    root: File,
    /** The device key (Android Keystore / Windows DPAPI); tests give each simulated device its own. */
    private val seal: (ByteArray) -> ByteArray? = DeviceKey::seal,
    private val unseal: (ByteArray) -> ByteArray? = DeviceKey::open,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    enum class State { NOT_SET_UP, LOCKED, UNLOCKED }

    sealed interface Unlock {
        data object Ok : Unlock
        data class Wrong(val waitSeconds: Long) : Unlock
        data class Blocked(val waitSeconds: Long) : Unlock
    }

    private val dir = File(root, "vault")
    private val chatsDir = File(dir, "chats")
    /** Conflict copies of vault chats from the sync (SyncGuard puts them here); they become second chats on unlocking. */
    private val conflictsDir = File(dir, CONFLICTS)
    private val metaFile = File(dir, "vault.json")
    /** Which PIN box this device opened the vault with (-1: the first one in [VaultMeta]); a new PIN replaces only it. */
    @Volatile
    private var myBox = -1
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val random = SecureRandom()

    @Volatile
    private var key: ByteArray? = null
    @Volatile
    private var lastActivity = 0L

    private val _state = MutableStateFlow(if (metaFile.isFile) State.LOCKED else State.NOT_SET_UP)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _chats = MutableStateFlow<List<Conversation>>(emptyList())
    /** The vault chats, newest first – only while unlocked. */
    val chats: StateFlow<List<Conversation>> = _chats.asStateFlow()

    val unlocked: Boolean get() = _state.value == State.UNLOCKED

    private fun meta(): VaultMeta? {
        val text = AtomicFiles.read(metaFile) ?: return null
        return runCatching { json.decodeFromString(VaultMeta.serializer(), text) }.getOrNull()
    }

    private fun writeMeta(m: VaultMeta) {
        dir.mkdirs()
        AtomicFiles.write(metaFile, json.encodeToString(VaultMeta.serializer(), m))
    }

    val lockMinutes: Int get() = meta()?.lockMinutes ?: DEFAULT_LOCK_MINUTES

    fun setLockMinutes(minutes: Int) {
        meta()?.let { writeMeta(it.copy(lockMinutes = minutes.coerceIn(1, 120))) }
    }

    /** Sets the vault up with [pin]; returns the recovery key to print or keep (shown only now). */
    @Synchronized
    fun setUp(pin: String): String {
        require(validPin(pin)) { PIN_RULE }
        check(!metaFile.isFile) { tr("Der Tresor ist schon eingerichtet.") }
        val data = ByteArray(32).also(random::nextBytes)
        val recovery = newRecoveryKey()
        writeMeta(wrapAll(data, pin, recovery))
        chatsDir.mkdirs()
        key = data
        touch()
        _state.value = State.UNLOCKED
        _chats.value = emptyList()
        return recovery
    }

    private fun wrapAll(data: ByteArray, pin: String, recovery: String): VaultMeta {
        val salt = ByteArray(16).also(random::nextBytes)
        val rSalt = ByteArray(16).also(random::nextBytes)
        val pinBox = encrypt(derive(pin, salt, ITERATIONS), data, null)
        val sealed = seal(pinBox)
        return VaultMeta(
            salt = b64(salt),
            iterations = ITERATIONS,
            pinWrapped = b64(sealed ?: pinBox),
            deviceSealed = sealed != null,
            recoverySalt = b64(rSalt),
            recoveryWrapped = b64(encrypt(derive(normalizeRecovery(recovery), rSalt, RECOVERY_ITERATIONS), data, null)),
        )
    }

    /** Opens the vault with [pin]; after [FREE_ATTEMPTS] wrong PINs every further try waits longer. */
    @Synchronized
    fun unlock(pin: String): Unlock {
        val m = meta() ?: return Unlock.Wrong(0)
        val now = clock()
        if (m.blockedUntil > now) return Unlock.Blocked((m.blockedUntil - now + 999) / 1000)
        // Every box this device can open (its own, sealed with its key, or an unsealed one) is tried with the PIN.
        var data: ByteArray? = null
        for ((i, b) in boxesOf(m)) {
            val box = open(b) ?: continue
            data = decrypt(derive(pin, dec(b.salt), b.iterations), box, null)
            if (data != null) {
                myBox = i
                break
            }
        }
        if (data == null) {
            val failures = m.failures + 1
            val wait = if (failures < FREE_ATTEMPTS) 0L else minOf(3600L, 30L shl (failures - FREE_ATTEMPTS).coerceAtMost(7))
            writeMeta(m.copy(failures = failures, blockedUntil = if (wait > 0) now + wait * 1000 else 0))
            return Unlock.Wrong(wait)
        }
        if (m.failures > 0) writeMeta(m.copy(failures = 0, blockedUntil = 0))
        key = data
        touch()
        _state.value = State.UNLOCKED
        adoptConflicts()
        _chats.value = loadAll()
        return Unlock.Ok
    }

    /** The first box (fields of [VaultMeta]) as index -1, then the boxes of further devices. */
    private fun boxesOf(m: VaultMeta): List<Pair<Int, VaultBox>> =
        listOf(-1 to VaultBox(m.salt, m.iterations, m.pinWrapped, m.deviceSealed)) + m.boxes.mapIndexed { i, b -> i to b }

    private fun open(b: VaultBox): ByteArray? = dec(b.wrapped).let { if (b.sealed) unseal(it) else it }

    /**
     * Whether this device has a PIN box it can open (false after a reinstall or on another PC or phone the vault was
     * synced to: then the recovery key sets a PIN for this device, without touching the others').
     */
    fun deviceKeyWorks(): Boolean {
        val m = meta() ?: return true
        return boxesOf(m).any { (_, b) -> open(b) != null }
    }

    /** [m] with the PIN box of this device ([index] as in [boxesOf], null: a new box for this device) replaced. */
    private fun withBox(m: VaultMeta, index: Int?, salt: ByteArray, pinBox: ByteArray): VaultMeta {
        val sealedBox = seal(pinBox)
        val box = VaultBox(b64(salt), ITERATIONS, b64(sealedBox ?: pinBox), sealedBox != null, clock())
        return when (index) {
            -1 -> m.copy(salt = box.salt, iterations = box.iterations, pinWrapped = box.wrapped, deviceSealed = box.sealed)
            null -> m.copy(boxes = (m.boxes + box).takeLast(MAX_BOXES))
            else -> m.copy(boxes = m.boxes.mapIndexed { i, b -> if (i == index) box else b })
        }
    }

    /** The box this device can open (index as in [boxesOf]); null if it has none. */
    private fun ownBox(m: VaultMeta): Int? = boxesOf(m).firstOrNull { (_, b) -> b.sealed && open(b) != null }?.first

    /** Sets a new PIN with the recovery key; true if the recovery key was right. Also unlocks. */
    @Synchronized
    fun resetPin(recovery: String, newPin: String): Boolean {
        require(validPin(newPin)) { PIN_RULE }
        val m = meta() ?: return false
        val data = decrypt(derive(normalizeRecovery(recovery), dec(m.recoverySalt), RECOVERY_ITERATIONS), dec(m.recoveryWrapped), null) ?: return false
        val salt = ByteArray(16).also(random::nextBytes)
        val pinBox = encrypt(derive(newPin, salt, ITERATIONS), data, null)
        // This device's box gets the new PIN; a device without one (synced vault, reinstall) gets a box of its own, and
        // the boxes of the other devices stay as they are.
        val index = ownBox(m) ?: boxesOf(m).firstOrNull { (_, b) -> !b.sealed }?.first
        val next = withBox(m, index, salt, pinBox).copy(failures = 0, blockedUntil = 0)
        writeMeta(next)
        myBox = index ?: (next.boxes.size - 1)
        key = data
        touch()
        _state.value = State.UNLOCKED
        adoptConflicts()
        _chats.value = loadAll()
        return true
    }

    /** Changes the PIN while unlocked. */
    @Synchronized
    fun changePin(newPin: String): Boolean {
        require(validPin(newPin)) { PIN_RULE }
        val data = key ?: return false
        val m = meta() ?: return false
        val salt = ByteArray(16).also(random::nextBytes)
        val pinBox = encrypt(derive(newPin, salt, ITERATIONS), data, null)
        writeMeta(withBox(m, myBox.takeIf { it == -1 || it < m.boxes.size } ?: -1, salt, pinBox))
        return true
    }

    /** Locks the vault: the key is wiped from memory, the chats are gone from the app until the PIN is entered again. */
    @Synchronized
    fun lock() {
        key?.fill(0)
        key = null
        _chats.value = emptyList()
        if (metaFile.isFile) _state.value = State.LOCKED
    }

    /** Something happened in the vault: the idle time starts again. */
    fun touch() {
        lastActivity = clock()
    }

    /** Locks when the vault was idle for its lock time (called by a timer); true if it locked now. */
    fun lockIfIdle(): Boolean {
        if (!unlocked) return false
        if (clock() - lastActivity < lockMinutes * 60_000L) return false
        lock()
        return true
    }

    fun contains(id: String): Boolean = _chats.value.any { it.id == id } || File(chatsDir, "$id.bin").isFile

    /** The vault chat [id] while unlocked. */
    fun get(id: String): Conversation? = if (!unlocked) null else _chats.value.firstOrNull { it.id == id }

    /** Saves a vault chat encrypted; ignored while locked (nothing is ever written in the clear). */
    @Synchronized
    fun save(conversation: Conversation) {
        val k = key ?: return
        val c = conversation.copy(vault = true)
        val plain = json.encodeToString(Conversation.serializer(), c).toByteArray()
        chatsDir.mkdirs()
        AtomicFiles.writeBytes(File(chatsDir, "${c.id}.bin"), encrypt(k, plain, c.id.toByteArray()))
        _chats.value = (_chats.value.filterNot { it.id == c.id } + c).sortedByDescending { it.updatedAt }
        touch()
    }

    @Synchronized
    fun delete(id: String) {
        File(chatsDir, "$id.bin").delete()
        _chats.value = _chats.value.filterNot { it.id == id }
    }

    /**
     * Reads the vault again after a sync (AP-63): a vault set up on another device appears, the chats of an open vault
     * are read anew.
     */
    @Synchronized
    fun reload() {
        when {
            !metaFile.isFile -> if (_state.value != State.NOT_SET_UP) { lock(); _state.value = State.NOT_SET_UP }
            _state.value == State.NOT_SET_UP -> _state.value = State.LOCKED
            unlocked -> {
                adoptConflicts()
                _chats.value = loadAll()
            }
        }
    }

    /**
     * Conflict copies of vault chats (changed on two devices before a sync): each becomes a second vault chat „…
     * (zweite Fassung)“, encrypted again under its new id – as the sync does with ordinary chats. Only while unlocked.
     */
    @Synchronized
    fun adoptConflicts(): Int {
        val k = key ?: return 0
        val files = (conflictsDir.listFiles().orEmpty().toList() + chatsDir.listFiles().orEmpty().filter { SyncGuard.conflictOriginal(it, emptyList()) != null })
            .filter { it.isFile && it.extension == "bin" }
        var adopted = 0
        for (f in files) {
            // The chat's id is the encryption's associated data: the original name, without the cloud's or the sync's marks.
            val ids = listOfNotNull(SyncGuard.conflictOriginal(f, emptyList())?.nameWithoutExtension, f.nameWithoutExtension)
                .flatMap { listOf(it, it.replace(Regex("^\\d+_"), "")) }.distinct()
            val plain = ids.firstNotNullOfOrNull { id -> decrypt(k, f.readBytes(), id.toByteArray()) } ?: continue
            val conv = runCatching { json.decodeFromString(Conversation.serializer(), plain.decodeToString()) }.getOrNull() ?: continue
            save(conv.copy(id = java.util.UUID.randomUUID().toString(), title = conv.title.ifBlank { "Chat" } + " (zweite Fassung)"))
            f.delete()
            adopted++
        }
        return adopted
    }

    private fun loadAll(): List<Conversation> {
        val k = key ?: return emptyList()
        return chatsDir.listFiles { f -> f.extension == "bin" }.orEmpty().mapNotNull { f ->
            val id = f.nameWithoutExtension
            val plain = runCatching { decrypt(k, f.readBytes(), id.toByteArray()) }.getOrNull() ?: return@mapNotNull null
            runCatching { json.decodeFromString(Conversation.serializer(), plain.decodeToString()) }.getOrNull()
        }.sortedByDescending { it.updatedAt }
    }

    private fun encrypt(k: ByteArray, plain: ByteArray, aad: ByteArray?): ByteArray {
        val iv = ByteArray(12).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(k, "AES"), GCMParameterSpec(128, iv))
        aad?.let(cipher::updateAAD)
        return iv + cipher.doFinal(plain)
    }

    private fun decrypt(k: ByteArray, data: ByteArray, aad: ByteArray?): ByteArray? = if (data.size < 12 + 16) null else try {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(k, "AES"), GCMParameterSpec(128, data.copyOfRange(0, 12)))
        aad?.let(cipher::updateAAD)
        cipher.doFinal(data.copyOfRange(12, data.size))
    } catch (e: AEADBadTagException) {
        null
    } catch (e: java.security.GeneralSecurityException) {
        null
    } catch (e: IllegalArgumentException) {
        null
    }

    private fun derive(secret: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(secret.toCharArray(), salt, iterations, 256)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun newRecoveryKey(): String {
        val bytes = ByteArray(15).also(random::nextBytes)
        var bits = 0
        var value = 0
        val out = StringBuilder()
        for (b in bytes) {
            value = (value shl 8) or (b.toInt() and 0xFF)
            bits += 8
            while (bits >= 5) {
                out.append(ALPHABET[(value shr (bits - 5)) and 31])
                bits -= 5
            }
        }
        return out.chunked(4).joinToString("-")
    }

    private fun b64(b: ByteArray) = Base64.getEncoder().encodeToString(b)
    private fun dec(s: String) = Base64.getDecoder().decode(s)

    companion object {
        /** Folder of vault conflict copies inside the vault (see [adoptConflicts]). */
        const val CONFLICTS = "konflikte"
        /** At most this many boxes of further devices (old ones of reinstalled devices fall out). */
        private const val MAX_BOXES = 8
        const val DEFAULT_LOCK_MINUTES = 5
        const val FREE_ATTEMPTS = 5
        private const val ITERATIONS = 210_000
        private const val RECOVERY_ITERATIONS = 60_000
        /** Without 0/O and 1/I, so the printed key is easy to type. */
        private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        const val PIN_RULE = "Die PIN hat 4 bis 12 Ziffern."

        fun validPin(pin: String) = pin.length in 4..12 && pin.all { it.isDigit() }

        /** Recovery key as typed: case, spaces and dashes do not matter. */
        fun normalizeRecovery(s: String) = s.uppercase().filter { it.isLetterOrDigit() }
    }
}
