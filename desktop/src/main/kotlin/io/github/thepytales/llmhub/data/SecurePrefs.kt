package io.github.thepytales.llmhub.data

import android.content.Context
import android.content.SharedPreferences
import io.github.thepytales.llmhub.AppLog
import io.github.thepytales.llmhub.desktop.Paths
import io.github.thepytales.llmhub.desktop.Secrets
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import java.io.File

/**
 * Settings of the Windows app, split in two (AP-12): API keys, tokens, accounts and environments are encrypted with
 * DPAPI (only this Windows user on this PC can read them) and stay on this PC ([Paths.machine]); every other setting
 * is a plain JSON file in the data folder, so it syncs with it to other PCs. Until 1.0.44 everything was in one
 * encrypted file in the data folder – which another PC could not read and overwrote; [migrate] splits it once.
 */
@Suppress("UNUSED_PARAMETER")
fun createSecurePrefs(context: Context): SharedPreferences {
    val secret = File(Paths.machine, SECRET_FILE)
    val plain = File(Paths.data, PLAIN_FILE)
    runCatching { migrate(File(Paths.data, LEGACY_FILE), secret, plain) }.onFailure { AppLog.e("Einstellungen aufteilen", it) }
    return SplitPrefs(FilePrefs.encrypted(secret), FilePrefs.plain(plain), SECRET_KEYS)
}

/** Settings that never leave this PC: they hold keys, tokens or secrets of environments. */
val SECRET_KEYS = setOf("apiKey", "githubToken", "githubAccounts", "providers", "environments", "nextcloudPassword", "mcpServers", "mailPassword")

/** The encrypted keys of this PC (in [Paths.machine]). */
const val SECRET_FILE = "schluessel.dat"

/** Every other setting, readable on every PC (in the data folder). */
const val PLAIN_FILE = "einstellungen.json"

/** Until 1.0.44: all settings encrypted in the data folder. */
const val LEGACY_FILE = "secure_settings.dat"

/**
 * Splits the old encrypted settings ([legacy]) into [secret] and [plain], each only if it does not exist yet (so the
 * settings another PC already wrote stay). If [legacy] lies in a synced folder, it is removed afterwards – keys never
 * stay there. A [legacy] file of another PC (DPAPI cannot open it here) is left for that PC.
 */
internal fun migrate(legacy: File, secret: File, plain: File) {
    if (!legacy.isFile) return
    val old = runCatching { Json.parseToJsonElement(Secrets.decrypt(legacy.readBytes()).toString(Charsets.UTF_8)) as JsonObject }.getOrNull() ?: return
    fun write(file: File, values: Map<String, JsonElement>, encrypted: Boolean) {
        val bytes = JsonObject(values).toString().toByteArray(Charsets.UTF_8)
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeBytes(if (encrypted) Secrets.encrypt(bytes) else bytes)
        if (!tmp.renameTo(file)) {
            file.writeBytes(tmp.readBytes())
            tmp.delete()
        }
    }
    if (!secret.exists()) write(secret, old.filterKeys { it in SECRET_KEYS }, encrypted = true)
    if (!plain.exists()) write(plain, old.filterKeys { it !in SECRET_KEYS }, encrypted = false)
    if (legacy.parentFile.canonicalFile != secret.parentFile.canonicalFile) {
        legacy.delete()
        AppLog.i("Einstellungen aufgeteilt: Schlüssel bleiben auf diesem PC, der Rest synchronisiert mit dem Datenordner")
    }
}

/** SharedPreferences that keep [secretKeys] in [secret] and everything else in [plain]. */
class SplitPrefs(private val secret: SharedPreferences, private val plain: SharedPreferences, private val secretKeys: Set<String>) : SharedPreferences {
    private fun of(key: String) = if (key in secretKeys) secret else plain

    override fun getString(key: String, defValue: String?) = of(key).getString(key, defValue)
    override fun getInt(key: String, defValue: Int) = of(key).getInt(key, defValue)
    override fun getLong(key: String, defValue: Long) = of(key).getLong(key, defValue)
    override fun getFloat(key: String, defValue: Float) = of(key).getFloat(key, defValue)
    override fun getBoolean(key: String, defValue: Boolean) = of(key).getBoolean(key, defValue)
    override fun contains(key: String) = of(key).contains(key)

    override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
        private val s = secret.edit()
        private val p = plain.edit()
        private fun ed(key: String) = if (key in secretKeys) s else p
        override fun putString(key: String, value: String?) = apply { ed(key).putString(key, value) }
        override fun putInt(key: String, value: Int) = apply { ed(key).putInt(key, value) }
        override fun putLong(key: String, value: Long) = apply { ed(key).putLong(key, value) }
        override fun putFloat(key: String, value: Float) = apply { ed(key).putFloat(key, value) }
        override fun putBoolean(key: String, value: Boolean) = apply { ed(key).putBoolean(key, value) }
        override fun remove(key: String) = apply { ed(key).remove(key) }
        override fun apply() {
            commit()
        }

        override fun commit(): Boolean = s.commit() and p.commit()
    }
}
