package io.github.thepytales.llmhub.data

import io.github.thepytales.llmhub.desktop.Secrets

/** Windows version of DeviceKey: DPAPI (only the same Windows user can open it), elsewhere the app's key file. */
object DeviceKey {
    fun seal(plain: ByteArray): ByteArray? = runCatching { Secrets.encrypt(plain) }.getOrNull()

    fun open(data: ByteArray): ByteArray? = runCatching { Secrets.decrypt(data) }.getOrNull()
}
