package io.github.thepytales.llmhub.data

import android.content.SharedPreferences
import io.github.thepytales.llmhub.desktop.Secrets
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import java.io.File

/** SharedPreferences in a JSON file; [encrypted] files are protected with [Secrets] (Windows: DPAPI). */
class FilePrefs private constructor(private val file: File, private val encrypted: Boolean) : SharedPreferences {
    private val values: MutableMap<String, JsonElement> = load().toMutableMap()

    private fun load(): Map<String, JsonElement> = runCatching {
        if (!file.exists()) return emptyMap()
        val bytes = file.readBytes()
        val text = (if (encrypted) Secrets.decrypt(bytes) else bytes).toString(Charsets.UTF_8)
        Json.parseToJsonElement(text) as JsonObject
    }.getOrDefault(emptyMap())

    @Synchronized
    private fun save() {
        val bytes = JsonObject(values).toString().toByteArray(Charsets.UTF_8)
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeBytes(if (encrypted) Secrets.encrypt(bytes) else bytes)
        if (!tmp.renameTo(file)) {
            file.writeBytes(tmp.readBytes())
            tmp.delete()
        }
    }

    private fun prim(key: String) = synchronized(this) { values[key] as? JsonPrimitive }

    override fun getString(key: String, defValue: String?) = prim(key)?.contentOrNull ?: defValue
    override fun getInt(key: String, defValue: Int) = prim(key)?.intOrNull ?: defValue
    override fun getLong(key: String, defValue: Long) = prim(key)?.longOrNull ?: defValue
    override fun getFloat(key: String, defValue: Float) = prim(key)?.floatOrNull ?: defValue
    override fun getBoolean(key: String, defValue: Boolean) = prim(key)?.booleanOrNull ?: defValue
    override fun contains(key: String) = synchronized(this) { key in values }

    override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
        private val changes = mutableMapOf<String, JsonElement?>()
        override fun putString(key: String, value: String?) = apply { changes[key] = value?.let(::JsonPrimitive) }
        override fun putInt(key: String, value: Int) = apply { changes[key] = JsonPrimitive(value) }
        override fun putLong(key: String, value: Long) = apply { changes[key] = JsonPrimitive(value) }
        override fun putFloat(key: String, value: Float) = apply { changes[key] = JsonPrimitive(value) }
        override fun putBoolean(key: String, value: Boolean) = apply { changes[key] = JsonPrimitive(value) }
        override fun remove(key: String) = apply { changes[key] = null }
        override fun apply() {
            commit()
        }

        override fun commit(): Boolean = synchronized(this@FilePrefs) {
            changes.forEach { (k, v) -> if (v == null) values.remove(k) else values[k] = v }
            runCatching { save() }.isSuccess
        }
    }

    companion object {
        private val open = mutableMapOf<String, FilePrefs>()

        /** One instance per file, like Android. */
        private fun get(file: File, encrypted: Boolean) = synchronized(open) {
            open.getOrPut(file.absolutePath) { FilePrefs(file, encrypted) }
        }

        fun plain(file: File): SharedPreferences = get(file, false)
        fun encrypted(file: File): SharedPreferences = get(file, true)
    }
}
