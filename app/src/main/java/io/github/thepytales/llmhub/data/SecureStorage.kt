package io.github.thepytales.llmhub.data

/** Whether API keys and tokens are stored encrypted on this device (see createSecurePrefs). */
object SecureStorage {
    /** false if the device's key store is broken and the settings fell back to unencrypted storage. */
    @Volatile
    var encrypted: Boolean = true
}
